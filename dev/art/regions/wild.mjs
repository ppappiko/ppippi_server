// Shared builder for the three wild "gathering land" islands: natural terrain (hills with terraced cliffs, ore veins in the rock,
// exposed ore on cliffs and outcrops, caves into hillsides, streams, ponds), a winding trail network, sparse single houses along
// the trails, small trail props, and the sculk-infected boss-room ENTRANCE (marker block recorded in the layout metadata).
import { Y0, SEA, heightmap, spline, hash, hash3, fbm, clamp, lerp, smooth, pick, B, stairs, lantern, DIR, OPP } from "./lib.mjs";
import { corrupt } from "./parts.mjs";

export const FACE = (dx, dz) => Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? "east" : "west") : (dz > 0 ? "south" : "north");
const ROCK = /^(stone|andesite|diorite|granite|tuff|calcite|deepslate|cobblestone|mossy_cobblestone|cobbled_deepslate|smooth_basalt|dripstone_block)$/;

// ================================================================== heights
/** Island heightmap + hills (bumps: {x, z, r, h, step}) — step > 0 terraces the hill into ledges with short cliffs. */
export function terrain(W, D, o) {
  const H = heightmap(W, D, { R: o.R, seed: o.seed, hills: o.hills, base: o.base });
  for (const b of o.bumps || []) for (let z = 0; z < D; z++) for (let x = 0; x < W; x++) {
    const i = z * W + x; if (H[i] < SEA + 1) continue;
    const d = Math.hypot(x - b.x, z - b.z) / (b.r * (0.78 + 0.44 * fbm(x / 22, z / 22, 2, o.seed + b.x)));
    if (d >= 1) continue;
    let v = b.h * (1 - d * d) ** 1.5 * smooth(0, 3, H[i] - SEA);
    if (b.step) { const q = Math.floor(v / b.step), f = v / b.step - q; v = b.step * (q + smooth(0.6, 0.9, f)); }
    H[i] += v;
  }
  return H;
}
/** Blends a circle to height y (soft edge `blend`), land only. */
export function flatten(H, W, D, f) {
  for (let z = 0; z < D; z++) for (let x = 0; x < W; x++) {
    const i = z * W + x; if (H[i] < SEA + 1) continue;
    const t = smooth(f.r + f.blend, f.r, Math.hypot(x - f.x, z - f.z)); if (t > 0) H[i] = lerp(H[i], f.y, t);
  }
}

// ================================================================== trails
/**
 * Trail network: a wobbly loop around the hub, spokes from the hub to the loop, branches from the loop to targets
 * (sculk entrance approach, coves...). Control points are pulled inland until they stand on dry ground.
 */
export function planTrails(H, W, D, o) {
  const land = (x, z) => x >= 2 && z >= 2 && x < W - 2 && z < D - 2 && H[Math.round(z) * W + Math.round(x)] >= SEA + 3;
  const inland = (x, z) => { let k = 0; while (!land(x, z) && k++ < 60) { x = lerp(x, o.hub.x, 0.04); z = lerp(z, o.hub.z, 0.04); } return [x, z]; };
  const lines = [];
  const loop = [];
  for (let a = 0; a < 360; a += 18) {
    const t = a * Math.PI / 180, w = 1 + (fbm(Math.cos(t) * 2 + 9, Math.sin(t) * 2 + 9, 2, o.seed) - 0.5) * 0.5;
    loop.push(inland(o.hub.x + Math.cos(t) * o.loop.rx * w, o.hub.z + Math.sin(t) * o.loop.rz * w));
  }
  lines.push({ pts: spline([...loop, loop[0], loop[1]].slice(0, loop.length + 1), 2), r: o.r ?? 1.3, kind: "loop" });
  const nearestLoop = (x, z) => loop.reduce((b, p) => Math.hypot(p[0] - x, p[1] - z) < Math.hypot(b[0] - x, b[1] - z) ? p : b, loop[0]);
  const wob = (a, b, s, amt) => { const mx = (a[0] + b[0]) / 2, mz = (a[1] + b[1]) / 2, dx = b[0] - a[0], dz = b[1] - a[1], L = Math.hypot(dx, dz) || 1, k = (hash(Math.round(mx), Math.round(mz), s) - 0.5) * 2 * amt * L; return inland(mx - dz / L * k, mz + dx / L * k); };
  for (const ang of o.spokes || []) {
    const t = ang * Math.PI / 180, end = nearestLoop(o.hub.x + Math.cos(t) * 400, o.hub.z + Math.sin(t) * 400);
    const a = [o.hub.x, o.hub.z], m = wob(a, end, ang, 0.22);
    lines.push({ pts: spline([a, wob(a, m, ang + 1, 0.2), m, wob(m, end, ang + 2, 0.2), end], 2), r: o.r ?? 1.3, kind: "spoke" });
  }
  for (const tg of o.targets || []) {
    const end = tg.keep ? [tg.x, tg.z] : inland(tg.x, tg.z), st = tg.from ? tg.from : nearestLoop(tg.x, tg.z), m = wob(st, end, tg.x + tg.z, 0.25);
    lines.push({ pts: spline([st, m, end], 2), r: tg.r ?? (o.r ?? 1.3) * 0.9, kind: tg.kind ?? "branch" });
  }
  return lines;
}
/** Smooths the ground along the trails (walkable slopes) and returns the trail corridor mask (trail radius + 3). */
export function easeTrails(H, W, D, lines) {
  const best = new Float32Array(W * D).fill(1e9), hv = new Float32Array(W * D), corridor = new Uint8Array(W * D);
  for (const { pts, r } of lines) {
    let hp = pts.map(([x, z]) => H[clamp(Math.round(z), 0, D - 1) * W + clamp(Math.round(x), 0, W - 1)]);
    for (let pass = 0; pass < 3; pass++) hp = hp.map((_, i) => { let s = 0, n = 0; for (let k = -4; k <= 4; k++) { const j = clamp(i + k, 0, hp.length - 1); s += hp[j]; n++; } return s / n; });
    for (let i = 1; i < hp.length; i++) hp[i] = clamp(hp[i], hp[i - 1] - 0.9, hp[i - 1] + 0.9);
    for (let i = hp.length - 2; i >= 0; i--) hp[i] = clamp(hp[i], hp[i + 1] - 0.9, hp[i + 1] + 0.9);
    const R = r + 4;
    pts.forEach(([px, pz], i) => {
      for (let dz = -Math.ceil(R); dz <= Math.ceil(R); dz++) for (let dx = -Math.ceil(R); dx <= Math.ceil(R); dx++) {
        const x = Math.round(px) + dx, z = Math.round(pz) + dz; if (x < 0 || z < 0 || x >= W || z >= D) continue;
        const d = Math.hypot(x - px, z - pz), j = z * W + x; if (d > R) continue;
        if (d <= r + 3) corridor[j] = 1;
        if (d < best[j]) { best[j] = d; hv[j] = hp[i]; }
      }
    });
  }
  for (let j = 0; j < W * D; j++) {
    if (best[j] > 1e8 || H[j] < SEA + 1) continue;
    const r = 1.3, t = smooth(r + 4, r + 0.8, best[j]);
    if (hv[j] >= SEA + 1) H[j] = lerp(H[j], hv[j], t);
  }
  return (x, z) => x >= 0 && z >= 0 && x < W && z < D && corridor[z * W + x] === 1;
}

// ================================================================== ores
const DEEP = { coal_ore: "deepslate_coal_ore", iron_ore: "deepslate_iron_ore", copper_ore: "deepslate_copper_ore", gold_ore: "deepslate_gold_ore", redstone_ore: "deepslate_redstone_ore", lapis_ore: "deepslate_lapis_ore", diamond_ore: "deepslate_diamond_ore", emerald_ore: "deepslate_emerald_ore" };
const pickW = (list, r) => { const tot = list.reduce((s, e) => s + e[1], 0); let a = r * tot; for (const e of list) { a -= e[1]; if (a <= 0) return e[0]; } return list[list.length - 1][0]; };
const oreFor = (base, ore) => /deepslate/.test(base) ? DEEP[ore.replace(/\[.*/, "")] ?? ore : ore;
/** Ore clumps inside the rock (2x2x2 cells). weights: [[ore, weight, ymin, ymax]]. */
export function veinOres(V, seed, weights, density = 0.014) {
  let n = 0;
  for (let z = 0; z < V.D; z++) for (let x = 0; x < V.W; x++) {
    const h = V.h(x, z);
    for (let y = Y0; y < h - 1; y++) {
      const nm = V.name(x, y, z); if (!ROCK.test(nm)) continue;
      const cx = x >> 1, cy = y >> 1, cz = z >> 1;
      if (hash3(cx, cy, cz, seed) >= density || hash3(x, y, z, seed + 1) > 0.6) continue;
      const ok = weights.filter(w => y >= (w[2] ?? -99) && y <= (w[3] ?? 999)); if (!ok.length) continue;
      V.set(x, y, z, oreFor(nm, pickW(ok, hash3(cx, cy, cz, seed + 2)))); n++;
    }
  }
  return n;
}
/** Ore showing on cliff faces / rocky ground (top + 2 blocks below on rock columns). */
export function exposeOres(V, seed, weights, p = 0.09) {
  for (let z = 1; z < V.D - 1; z++) for (let x = 1; x < V.W - 1; x++) {
    const h = V.h(x, z); if (h <= SEA + 1) continue;
    for (let y = h - 3; y <= h; y++) {
      const nm = V.name(x, y, z); if (!ROCK.test(nm)) continue;
      const exposed = y === h || V.air(x + 1, y, z) || V.air(x - 1, y, z) || V.air(x, y, z + 1) || V.air(x, y, z - 1);
      if (!exposed) continue;
      if (hash(x >> 1, (z >> 1) + y * 131, seed) >= p || hash3(x, y, z, seed + 3) > 0.65) continue;
      V.set(x, y, z, oreFor(nm, pickW(weights, hash(x >> 1, z >> 1, seed + 4))));
    }
  }
}

// ================================================================== water
/** Stream course from a source: downhill with momentum and a pull away from the island centre, until the sea. */
export function downhill(V, x, z, seed = 1) {
  const cx = V.W / 2, cz = V.D / 2, out = [[x, z]];
  let dx = x - cx, dz = z - cz, L = Math.hypot(dx, dz) || 1; dx /= L; dz /= L;
  for (let i = 0; i < 400; i++) {
    const h = q => V.h(Math.round(q[0]), Math.round(q[1]));
    const gx = h([x + 3, z]) - h([x - 3, z]), gz = h([x, z + 3]) - h([x, z - 3]);
    let ox = x - cx, oz = z - cz; const ol = Math.hypot(ox, oz) || 1; ox /= ol; oz /= ol;
    const w = (hash(i, 1, seed) - 0.5) * 0.5;
    let nx = dx * 0.7 - gx * 0.08 + ox * 0.18 - dz * w, nz = dz * 0.7 - gz * 0.08 + oz * 0.18 + dx * w;
    const nl = Math.hypot(nx, nz) || 1; dx = nx / nl; dz = nz / nl; x += dx; z += dz;
    if (i % 6 === 5) out.push([x, z]);
    if (V.h(Math.round(x), Math.round(z)) <= SEA) { out.push([x + dx * 4, z + dz * 4]); break; }
  }
  return out;
}
/** Streams: water level never rises downstream (monotonic), banks raised where needed; mask V.water. */
export function streams(V, list, seed = 1) {
  V.wt = V.wt ?? new Int16Array(V.W * V.D).fill(-1);
  for (const s of list) {
    const pts = spline(s.ctrl, 1); let wl = 999;
    for (const [px, pz] of pts) {
      const cx = Math.round(px), cz = Math.round(pz), hc = V.h(cx, cz);
      if (hc <= SEA + 1) break;
      wl = Math.min(wl, hc - 1);
      const r = s.r ?? 1.5;
      for (let dz = -2; dz <= 2; dz++) for (let dx = -2; dx <= 2; dx++) {
        const x = cx + dx, z = cz + dz, d = Math.hypot(x - px, z - pz); if (d > r || x < 1 || z < 1 || x >= V.W - 1 || z >= V.D - 1) continue;
        const h = V.h(x, z), j = z * V.W + x; if (V.wt[j] >= 0 && V.wt[j] <= wl) continue;
        const bed = Math.min(h, wl) - (d < r * 0.55 ? 2 : 1);
        for (let y = bed + 1; y <= h + 2; y++) V.set(x, y, z, y <= wl ? "water" : 0);
        V.set(x, bed, z, pick(["gravel", "sand", "gravel", "clay"], hash(x, z, seed)));
        V.wt[j] = wl; V.mark(x, z, x, z);
      }
    }
  }
  // levees: dry neighbours lower than the water get raised
  for (let z = 1; z < V.D - 1; z++) for (let x = 1; x < V.W - 1; x++) {
    const w = V.wt[z * V.W + x]; if (w < 0) continue;
    for (let dz = -1; dz <= 1; dz++) for (let dx = -1; dx <= 1; dx++) {
      const X = x + dx, Z = z + dz, j = Z * V.W + X; if (V.wt[j] >= 0) continue;
      const h = V.h(X, Z); if (h <= SEA || h >= w) continue;
      for (let y = h; y < w; y++) V.set(X, y, Z, "dirt"); V.set(X, w, Z, "grass_block"); V.surf[j] = w;
    }
  }
}
/** Pond in a basin: water top 1 below the lowest rim block. */
export function pond(V, cx, cz, r, seed = 3, bed = ["sand", "clay", "gravel"]) {
  let g = 999;
  for (let a = 0; a < 48; a++) { const t = a / 48 * Math.PI * 2; for (const rr of [r + 1, r + 2]) g = Math.min(g, V.h(Math.round(cx + Math.cos(t) * rr), Math.round(cz + Math.sin(t) * rr))); }
  const wt = g - 1; if (wt <= SEA) return false;
  V.wt = V.wt ?? new Int16Array(V.W * V.D).fill(-1);
  for (let dz = -r - 1; dz <= r + 1; dz++) for (let dx = -r - 1; dx <= r + 1; dx++) {
    const x = cx + dx, z = cz + dz, d = Math.hypot(dx, dz) + (fbm(x / 5, z / 5, 2, seed) - 0.5) * 2.2; if (d > r) continue;
    const h = V.h(x, z), depth = Math.round(clamp((r - d) * 0.8, 1, 3)), b = Math.min(h, wt) - depth;
    for (let y = b + 1; y <= h + 3; y++) V.set(x, y, z, y <= wt ? "water" : 0);
    V.set(x, b, z, pick(bed, hash(x, z, seed + 1)));
    if (hash(x, z, seed + 2) < 0.05 && depth <= 2) V.set(x, wt + 1, z, "lily_pad");
    V.wt[z * V.W + x] = wt; V.surf[z * V.W + x] = b; V.mark(x, z, x, z);
  }
  // reeds / sugar cane on the shore
  for (let a = 0; a < 36; a++) {
    const t = a / 36 * Math.PI * 2, x = Math.round(cx + Math.cos(t) * (r + 1)), z = Math.round(cz + Math.sin(t) * (r + 1)), h = V.h(x, z);
    if (V.occupied(x, z) || hash(x, z, seed + 5) > 0.3 || !/grass_block|sand|dirt/.test(V.name(x, h, z))) continue;
    const n = 1 + Math.floor(hash(x, z, seed + 6) * 3); for (let k = 1; k <= n; k++) V.set(x, h + k, z, "sugar_cane[age=0]");
  }
  return true;
}
/** Automatic pond spots: flat low ground away from the trails / builds. */
export function autoPonds(V, n, seed, corridor, avoid = () => false) {
  const out = [];
  for (let z = 20; z < V.D - 20; z += 7) for (let x = 20; x < V.W - 20; x += 7) {
    const r = 4 + Math.floor(hash(x, z, seed) * 4), h = V.h(x, z); if (h < SEA + 3 || h > SEA + 16) continue;
    let lo = 999, hi = 0, bad = false;
    for (let dz = -r - 3; dz <= r + 3 && !bad; dz += 2) for (let dx = -r - 3; dx <= r + 3; dx += 2) { const X = x + dx, Z = z + dz, hh = V.h(X, Z); lo = Math.min(lo, hh); hi = Math.max(hi, hh); if (corridor(X, Z) || V.occupied(X, Z) || avoid(X, Z)) { bad = true; break; } }
    if (bad || hi - lo > 3) continue;
    out.push({ x, z, r, s: hash(x, z, seed + 1) });
  }
  out.sort((a, b) => a.s - b.s);
  const got = [];
  for (const c of out) { if (got.length >= n) break; if (got.some(g => Math.hypot(g.x - c.x, g.z - c.z) < 55)) continue; if (pond(V, c.x, c.z, c.r, seed + got.length)) got.push(c); }
  return got;
}

// ================================================================== caves
/** Tunnels into hillsides (from the foot of a cliff), ending in a small chamber; ore shows in the walls. */
export function caves(V, n, seed, corridor, weights, st = {}) {
  const cand = [];
  for (let z = 12; z < V.D - 12; z += 3) for (let x = 12; x < V.W - 12; x += 3) {
    const h = V.h(x, z); if (h <= SEA + 1 || V.occupied(x, z) || corridor(x, z)) continue;
    for (const [f, [fx, fz]] of Object.entries(DIR)) if (V.h(x + fx * 4, z + fz * 4) >= h + 6 && V.h(x + fx * 10, z + fz * 10) >= h + 8) cand.push({ x, z, f, s: hash(x, z, seed) });
  }
  cand.sort((a, b) => a.s - b.s);
  const done = [];
  for (const c of cand) {
    if (done.length >= n) break;
    if (done.some(d => Math.hypot(d.x - c.x, d.z - c.z) < 45)) continue;
    const [fx, fz] = DIR[c.f], h0 = V.h(c.x, c.z), L = 18 + Math.floor(hash(c.x, c.z, seed + 1) * 14);
    let ok = true; for (let i = 2; i <= L; i += 2) { const x = c.x + fx * i, z = c.z + fz * i; if (corridor(x, z) || V.occupied(x, z) && i > 3 || x < 4 || z < 4 || x >= V.W - 4 || z >= V.D - 4) { ok = false; break; } }
    if (!ok) continue;
    const carved = new Set();
    let px = c.x, pz = c.z, lat = 0;
    for (let i = 0; i <= L; i++) {
      lat += (hash(c.x + i, c.z, seed + 2) - 0.5) * 0.5; lat = clamp(lat, -3, 3);
      px = c.x + fx * i + (fz ? lat : 0); pz = c.z + fz * i + (fx ? lat : 0);
      const fy = h0 - Math.floor(i / 5), end = i === L, rh = end ? 3.6 : 1.7 + hash(i, c.x, seed + 3) * 0.7, rv = end ? 2.6 : 1.6;
      for (let dz = -4; dz <= 4; dz++) for (let dx = -4; dx <= 4; dx++) for (let dy = -1; dy <= 5; dy++) {
        const x = Math.round(px) + dx, z = Math.round(pz) + dz, y = fy + dy;
        const e = (dx * dx + dz * dz) / (rh * rh) + ((y - fy - 1.6) / rv) ** 2; if (e > 1 || y <= fy || y < Y0 + 3) continue;
        if (/water/.test(V.name(x, y, z))) continue;
        V.set(x, y, z, 0); carved.add(x * 4096 + z);
      }
      const fx0 = Math.round(px), fz0 = Math.round(pz);
      if (!V.air(fx0, fy, fz0)) V.set(fx0, fy, fz0, pick(["gravel", "stone", "coarse_dirt", "cobblestone"], hash(fx0, fz0, seed + 4)));
      if (i > 3 && i % 7 === 0) V.set(fx0, fy + 1, fz0, st.light ?? "torch");
    }
    // ore in the walls + surface heights of the carved columns
    for (const k of carved) {
      const x = Math.floor(k / 4096), z = k % 4096, old = V.h(x, z);
      let y = old; while (y > Y0 && V.air(x, y, z)) y--; V.surf[z * V.W + x] = y;
      for (let yy = y - 8; yy <= old; yy++) for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
        const X = x + dx, Z = z + dz, nm = V.name(X, yy, Z); if (!ROCK.test(nm) && !/dirt/.test(nm)) continue;
        if (!V.air(x, yy, z)) continue;
        const r = hash3(X, yy, Z, seed + 5);
        if (r < 0.12) V.set(X, yy, Z, oreFor(nm, pickW(weights, hash3(X >> 1, yy >> 1, Z >> 1, seed + 6))));
        else if (/dirt/.test(nm)) V.set(X, yy, Z, "stone");
      }
    }
    // mouth: two posts with lights
    for (const s of [-1, 1]) { const x = c.x - fx * 1 + (fz ? s * 3 : 0), z = c.z - fz * 1 + (fx ? s * 3 : 0), g = V.h(x, z); if (g > SEA && V.air(x, g + 1, z)) { V.set(x, g + 1, z, st.post ?? "spruce_fence"); V.set(x, g + 2, z, st.lamp ?? lantern()); } }
    V.mark(c.x - 3, c.z - 3, c.x + 3, c.z + 3);
    done.push({ x: c.x, z: c.z, f: c.f });
  }
  return done;
}

// ================================================================== outcrops
/** Rock mounds with ore on the surface. mats: rock names; weights: surface ore weights. */
export function outcrops(V, n, seed, corridor, mats, weights, st = {}) {
  let got = 0;
  for (let i = 0; i < n * 30 && got < n; i++) {
    const x = 10 + Math.floor(hash(i, 1, seed) * (V.W - 20)), z = 10 + Math.floor(hash(i, 2, seed) * (V.D - 20)), h = V.h(x, z);
    if (h <= SEA + 1 || corridor(x, z) || V.occupied(x, z) || (st.avoid && st.avoid(x, z))) continue;
    const r = (st.rmin ?? 1.6) + hash(i, 3, seed) * (st.rvar ?? 2.4), hh = r * (st.tall ?? 1.1) + hash(i, 4, seed) * 2;
    let bad = false; for (let dz = -4; dz <= 4 && !bad; dz++) for (let dx = -4; dx <= 4; dx++) if (corridor(x + dx, z + dz) || V.occupied(x + dx, z + dz) || V.h(x + dx, z + dz) <= SEA + 1) { bad = true; break; }
    if (bad) continue;
    for (let dz = -4; dz <= 4; dz++) for (let dx = -4; dx <= 4; dx++) {
      const X = x + dx, Z = z + dz, d = Math.hypot(dx, dz) + (hash(X, Z, seed + 5) - 0.5) * 0.9; if (d > r) continue;
      const g = V.h(X, Z), top = g + Math.round((1 - (d / r) ** 2) * hh);
      for (let y = g; y <= top; y++) {
        const ore = hash3(X, y, Z, seed + 6) < (st.ore ?? 0.22);
        V.set(X, y, Z, ore ? pickW(weights, hash3(X, y, Z, seed + 7)) : pick(mats, hash3(X, y, Z, seed + 8)));
      }
      V.surf[Z * V.W + X] = top;
      if (st.cap && hash(X, Z, seed + 9) < 0.3 && d < r - 0.5) V.put(X, top + 1, Z, st.cap(X, Z));
    }
    V.mark(x - 3, z - 3, x + 3, z + 3); got++;
  }
  return got;
}

// ================================================================== trail stamping + bridges
export function stampTrails(V, R, lines, mat, st = {}) {
  for (const l of lines) R.add(l.pts, l.r, (x, z, t, d) => V.wt && V.wt[z * V.W + x] >= 0 ? null : mat(x, z, t, d), { bridge: st.sea ?? "spruce_planks" });
  // bridges over streams / ponds
  let bridges = 0;
  for (const l of lines) {
    const cells = [];
    const seen = new Set();
    for (const [px, pz] of l.pts) for (let dz = -3; dz <= 3; dz++) for (let dx = -3; dx <= 3; dx++) {
      const x = Math.round(px) + dx, z = Math.round(pz) + dz, k = x * 4096 + z, d = Math.hypot(x - px, z - pz);
      if (d > l.r + 0.6 || x < 0 || z < 0 || x >= V.W || z >= V.D || !V.wt || V.wt[z * V.W + x] < 0) continue;
      if (seen.has(k)) continue; seen.add(k); cells.push([x, z]);
    }
    if (!cells.length) continue;
    // deck height: the trail ground next to the water
    let deck = 0; for (const [x, z] of cells) deck = Math.max(deck, V.wt[z * V.W + x] + 1);
    for (const [px, pz] of l.pts) { const x = Math.round(px), z = Math.round(pz); if (R.on(x, z) && (!V.wt || V.wt[z * V.W + x] < 0) && cells.some(([a, b]) => Math.abs(a - x) + Math.abs(b - z) <= 2)) deck = Math.max(deck, V.h(x, z)); }
    for (const [x, z] of cells) {
      V.set(x, deck, z, st.deck ?? "spruce_planks"); for (let y = deck + 1; y <= deck + 3; y++) V.set(x, y, z, 0);
      const edge = ![[1, 0], [-1, 0], [0, 1], [0, -1]].every(([dx, dz]) => R.on(x + dx, z + dz) || cells.some(([a, b]) => a === x + dx && b === z + dz));
      if (edge) V.set(x, deck + 1, z, st.rail ?? "spruce_fence");
      R.m[z * V.W + x] = 1;
    }
    bridges++;
  }
  return bridges;
}

// ================================================================== houses along the trails
/** Single houses along the trails (door facing the trail), spaced out; o.build(x0,z0,x1,z1,face,r) -> {door:[x,z]} */
export function placeHouses(V, R, lines, o) {
  const cand = [];
  for (const { pts, r } of lines) for (let i = 4; i < pts.length - 4; i += 1) {
    const [ax, az] = pts[i - 1], [bx, bz] = pts[i + 1], len = Math.hypot(bx - ax, bz - az) || 1, tx = (bx - ax) / len, tz = (bz - az) / len, [px, pz] = pts[i];
    for (const side of [1, -1]) cand.push({ px, pz, tx, tz, r, side, s: hash(Math.round(px), Math.round(pz), (o.seed ?? 1) + side) });
  }
  cand.sort((a, b) => a.s - b.s);
  const got = [];
  for (const c of cand) {
    if (got.length >= o.max) break;
    const [front, deep] = pick(o.sizes, c.s * 7 % 1);
    const nx = -c.tz * c.side, nz = c.tx * c.side, horiz = Math.abs(nx) > Math.abs(nz), w = horiz ? deep : front, d = horiz ? front : deep;
    const face = horiz ? (nx > 0 ? "west" : "east") : (nz > 0 ? "north" : "south");
    const off = c.r + 3.5 + (horiz ? w : d) / 2;
    const cx = Math.round(c.px + nx * off), cz = Math.round(c.pz + nz * off);
    if (got.some(g => Math.hypot(g.x - cx, g.z - cz) < o.spacing)) continue;
    if (o.avoid && o.avoid(cx, cz)) continue;
    const x0 = cx - Math.floor(w / 2), z0 = cz - Math.floor(d / 2), x1 = x0 + w - 1, z1 = z0 + d - 1;
    let ok = true, lo = 999, hi = -999;
    for (let z = z0 - 2; z <= z1 + 2 && ok; z++) for (let x = x0 - 2; x <= x1 + 2; x++) {
      if (x < 2 || z < 2 || x >= V.W - 2 || z >= V.D - 2 || V.occupied(x, z) || R.on(x, z) || V.h(x, z) <= SEA + 1 || (V.wt && V.wt[z * V.W + x] >= 0)) { ok = false; break; }
      if (x >= x0 && x <= x1 && z >= z0 && z <= z1) { lo = Math.min(lo, V.h(x, z)); hi = Math.max(hi, V.h(x, z)); }
    }
    if (!ok || hi - lo > (o.slope ?? 3)) continue;
    const hb = o.build(x0, z0, x1, z1, face, c.s);
    // short path from the door step to the trail
    const [fx, fz] = DIR[face]; let px = hb.door[0] + fx * 2, pz = hb.door[1] + fz * 2;
    for (let k = 0; k < 8 && !R.on(px, pz); k++, px += fx, pz += fz) { const g = V.h(px, pz); V.set(px, g, pz, o.path ? o.path(px, pz) : "dirt_path"); for (let y = g + 1; y <= g + 3; y++) V.set(px, y, pz, 0); V.mark(px, pz, px, pz); }
    got.push({ x: cx, z: cz, face, x0, z0, x1, z1 });
  }
  return got;
}

// ================================================================== trail props
/**
 * Props beside the trails every ~o.every blocks. kinds: [{name, w, cap, need, off, build(x, z, face, y)}]; face = towards the trail.
 */
export function placeProps(V, R, lines, o) {
  const got = [], count = {};
  for (const { pts, r } of lines) {
    let acc = 0, next = o.every * (0.5 + hash(pts.length, 3, o.seed));
    for (let i = 1; i < pts.length - 1; i++) {
      acc += Math.hypot(pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1]); if (acc < next) continue;
      const [ax, az] = pts[i - 1], [bx, bz] = pts[i + 1], len = Math.hypot(bx - ax, bz - az) || 1, tx = (bx - ax) / len, tz = (bz - az) / len;
      const s = hash(Math.round(pts[i][0]), Math.round(pts[i][1]), o.seed + 7), side = s < 0.5 ? 1 : -1;
      const kinds = o.kinds.filter(k => (count[k.name] ?? 0) < (k.cap ?? 999));
      const kind = pickW(kinds.map(k => [k, k.w]), hash(i, pts.length, o.seed + 8));
      const nx = -tz * side, nz = tx * side, off = r + 1.5 + (kind.off ?? 0);
      const x = Math.round(pts[i][0] + nx * off), z = Math.round(pts[i][1] + nz * off), need = kind.need ?? 1;
      let ok = !got.some(g => Math.hypot(g.x - x, g.z - z) < (o.spacing ?? 14)) && !(o.avoid && o.avoid(x, z));
      const h = V.h(x, z);
      for (let dz = -need; dz <= need && ok; dz++) for (let dx = -need; dx <= need; dx++) { const X = x + dx, Z = z + dz; if (V.occupied(X, Z) || R.on(X, Z) || Math.abs(V.h(X, Z) - h) > 1 || V.h(X, Z) <= SEA + 1) { ok = false; break; } }
      if (!ok) { next = acc + 4; continue; }
      const face = FACE(-nx, -nz);
      for (let dz = -need; dz <= need; dz++) for (let dx = -need; dx <= need; dx++) { const X = x + dx, Z = z + dz, g = V.h(X, Z); for (let y = g + 1; y <= g + 4; y++) if (!/log|leaves/.test(V.name(X, y, Z))) V.set(X, y, Z, 0); }
      kind.build(x, z, face, h);
      V.mark(x - need, z - need, x + need, z + need);
      count[kind.name] = (count[kind.name] ?? 0) + 1; got.push({ x, z, kind: kind.name });
      acc = 0; next = o.every * (0.7 + hash(i, 9, o.seed) * 0.6);
    }
  }
  return { got, count };
}

// common prop builders (themed through materials)
export const props = {
  post(V, x, z, y, post, head, h = 2) { for (let k = 1; k <= h; k++) V.set(x, y + k, z, post); V.set(x, y + h + 1, z, head); },
  bench(V, x, z, face, y, st, arm) {
    const [fx, fz] = DIR[face], lx = fz ? 1 : 0, lz = fx ? 1 : 0;
    for (const s of [0, 1]) V.set(x + lx * s, y + 1, z + lz * s, stairs(st, OPP[face]));
    if (arm) { V.set(x - lx, y + 1, z - lz, arm); V.set(x + lx * 2, y + 1, z + lz * 2, arm); }
  },
  sign(V, x, z, face, y, post, sign) {
    const rot = { south: 0, west: 4, north: 8, east: 12 }[face];
    V.set(x, y + 1, z, post); V.set(x, y + 2, z, post); V.set(x, y + 3, z, B(sign, { rotation: rot, waterlogged: "false" }));
  },
  campfire(V, x, z, y, seat, ring = "gravel", fire = "campfire[lit=true,signal_fire=false,facing=north,waterlogged=false]") {
    for (let dz = -2; dz <= 2; dz++) for (let dx = -2; dx <= 2; dx++) if (Math.hypot(dx, dz) <= 2.3 && hash(x + dx, z + dz, 5) < 0.75) V.set(x + dx, y, z + dz, ring);
    V.set(x, y + 1, z, fire);
    for (const [dx, dz, a] of [[2, 0, "z"], [-2, 0, "z"], [0, 2, "x"], [0, -2, "x"]]) if (hash(x + dx, z + dz, 6) < 0.8) V.set(x + dx, y + 1, z + dz, typeof seat === "function" ? seat(a) : seat);
  },
};

// ================================================================== sculk zone + boss-room entrance
/**
 * Entrance geometry. dir = direction the stairs DESCEND (cardinal [dx,dz]); local a = along dir (rim at -8, stairs -7..-2,
 * landing -1..2, doorway frame at +3, sealed alcove +4..+5, back wall +6), b = lateral.
 */
export function entranceSpec(x, z, y, dir) {
  const [dx, dz] = dir, lx = -dz, lz = dx;
  const at = (a, b) => [x + dx * a + lx * b, z + dz * a + lz * b];
  return { x, z, y, dir, at, approach: at(-13, 0), up: FACE(-dx, -dz), marker: (() => { const [mx, mz] = at(3, 0); return { x: mx, y: y - 6, z: mz }; })() };
}
export function reserveEntrance(V, E) {
  for (let a = -10; a <= 7; a++) for (let b = -5; b <= 5; b++) { const [x, z] = E.at(a, b); V.mark(x, z, x, z); }
}
/** Sculk infection (corrupt) then the entrance itself on top: open stairwell, landing, reinforced-deepslate doorway, lodestone marker. */
export function sculkZone(V, E, R, seed) {
  const I = corrupt(V, E.x, E.z, R, seed);
  const G = E.y, sk = (x, y, z, m) => V.set(x, y, z, m);
  const wall = (x, y, z) => pick(["deepslate_bricks", "cracked_deepslate_bricks", "deepslate_tiles", "sculk", "cracked_deepslate_tiles", "sculk"], hash3(x, y, z, seed + 1));
  // clear the footprint above ground (trees / collapse leftovers); dead trees / logs a bit wider so nothing hangs over the arch
  for (let a = -15; a <= 10; a++) for (let b = -8; b <= 8; b++) { const [x, z] = E.at(a, b); for (let y = G + 1; y <= G + 24; y++) if (/log|wood|leaves/.test(V.name(x, y, z))) sk(x, y, z, 0); }
  for (let a = -10; a <= 7; a++) for (let b = -5; b <= 5; b++) { const [x, z] = E.at(a, b); for (let y = G + 1; y <= G + 24; y++) sk(x, y, z, 0); if (Math.abs(b) >= 4 || a <= -9 || a >= 7) { for (let y = G - 3; y <= G; y++) sk(x, y, z, y === G ? pick(["sculk", "deepslate", "cracked_deepslate_tiles", "sculk", "tuff"], hash(x, z, seed + 2)) : "deepslate"); V.surf[z * V.W + x] = G; } }
  // pit: walls |b| = 3, and the end wall a = 3..6
  for (let a = -8; a <= 6; a++) for (let b = -3; b <= 3; b++) {
    const [x, z] = E.at(a, b), edge = Math.abs(b) === 3 || a >= 3 || a === -8;
    for (let y = G - 8; y <= G; y++) sk(x, y, z, edge ? wall(x, y, z) : "deepslate");
    V.surf[z * V.W + x] = G;
  }
  // stairs (a = -7 .. -2, y = G .. G-5), landing (a = -1 .. 2, floor G-6)
  for (let i = 0; i < 6; i++) for (let b = -2; b <= 2; b++) {
    const [x, z] = E.at(-7 + i, b), y = G - i;
    sk(x, y, z, stairs("deepslate_brick_stairs", E.up)); for (let yy = y + 1; yy <= G + 6; yy++) sk(x, yy, z, 0);
    V.surf[z * V.W + x] = y;
  }
  for (let a = -1; a <= 2; a++) for (let b = -2; b <= 2; b++) {
    const [x, z] = E.at(a, b);
    sk(x, G - 6, z, (a + b) % 2 ? "polished_deepslate" : pick(["sculk", "deepslate_tiles", "sculk"], hash(x, z, seed + 3)));
    for (let y = G - 5; y <= G + 6; y++) sk(x, y, z, 0);
    V.surf[z * V.W + x] = G - 6;
  }
  // doorway frame at a = 3: 5 wide, rows G-6 .. G-1; opening |b| <= 1, G-5 .. G-2
  for (let b = -2; b <= 2; b++) for (let y = G - 6; y <= G - 1; y++) {
    const [x, z] = E.at(3, b), open = Math.abs(b) <= 1 && y >= G - 5 && y <= G - 2;
    sk(x, y, z, open ? 0 : "reinforced_deepslate");
  }
  { const [x, z] = E.at(3, 0); sk(x, G - 6, z, "lodestone"); sk(x, G, z, "chiseled_deepslate"); }
  // sealed alcove behind the doorway (a = 4..5), back wall a = 6
  for (let a = 4; a <= 5; a++) for (let b = -1; b <= 1; b++) { const [x, z] = E.at(a, b); sk(x, G - 6, z, "sculk"); for (let y = G - 5; y <= G - 2; y++) sk(x, y, z, 0); }
  for (let b = -1; b <= 1; b++) for (let y = G - 5; y <= G - 2; y++) { const [x, z] = E.at(6, b); sk(x, y, z, (b + y) % 2 ? "crying_obsidian" : "sculk_catalyst[bloom=false]"); }
  // lights on the landing, soul fire bowls
  for (const b of [-2, 2]) { const [x, z] = E.at(2, b); sk(x, G - 5, z, "soul_lantern[hanging=false,waterlogged=false]"); const [x2, z2] = E.at(-1, b); sk(x2, G - 5, z2, "soul_campfire[lit=true,signal_fire=false,facing=north,waterlogged=false]"); }
  // rim railing + arch over the top of the stairs
  for (let a = -6; a <= 3; a++) for (const b of [-3, 3]) { const [x, z] = E.at(a, b); sk(x, G + 1, z, "cobbled_deepslate_wall"); }
  for (let b = -3; b <= 3; b++) { const [x, z] = E.at(3, b); sk(x, G + 1, z, "cobbled_deepslate_wall"); }
  for (const b of [-3, 3]) { const [x, z] = E.at(-8, b); for (let y = G + 1; y <= G + 6; y++) sk(x, y, z, y === G + 6 ? "chiseled_deepslate" : "reinforced_deepslate"); sk(x, G + 7, z, "soul_lantern[hanging=false,waterlogged=false]"); }
  for (let b = -2; b <= 2; b++) { const [x, z] = E.at(-8, b); sk(x, G + 6, z, Math.abs(b) === 0 ? "chiseled_deepslate" : "reinforced_deepslate"); if (Math.abs(b) === 1) sk(x, G + 5, z, "soul_lantern[hanging=true,waterlogged=false]"); }
  for (let b = -2; b <= 2; b++) { const [x, z] = E.at(-8, b); sk(x, G, z, "polished_deepslate"); }
  // sculk veins on the pit walls
  for (let a = -7; a <= 2; a++) for (const b of [-2, 2]) for (let y = G - 5; y <= G; y++) {
    const [x, z] = E.at(a, b); if (!V.air(x, y, z) || hash3(x, y, z, seed + 4) > 0.3) continue;
    const [wx, wz] = E.at(a, b * 1.5), side = FACE(wx - x, wz - z), p = { down: "false", east: "false", north: "false", south: "false", up: "false", west: "false", waterlogged: "false" }; p[side] = "true";
    if (/stairs/.test(V.name(x, y - 1, z)) || /stairs/.test(V.name(x, y, z))) continue;
    sk(x, y, z, B("sculk_vein", p));
  }
  // cracks radiating out of the zone
  for (let k = 0; k < 9; k++) {
    const t = k / 9 * Math.PI * 2 + hash(k, 1, seed) * 0.5, L = R * (0.55 + hash(k, 2, seed) * 0.4);
    for (let s = 8; s < L; s += 0.5) {
      const x = Math.round(E.x + Math.cos(t + Math.sin(s / 6) * 0.25) * s), z = Math.round(E.z + Math.sin(t + Math.sin(s / 6) * 0.25) * s);
      if (V.occupied(x, z) && Math.hypot(x - E.x, z - E.z) < 12) continue;
      const g = V.h(x, z); if (g <= SEA) continue;
      if (!/water|log|planks|stairs/.test(V.name(x, g, z))) V.set(x, g, z, s < L * 0.6 ? "cracked_deepslate_tiles" : pick(["sculk", "cracked_deepslate_bricks", "tuff"], hash(x, z, seed + 5)));
    }
  }
  return E.marker;
}
