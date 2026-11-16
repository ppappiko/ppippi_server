// Reusable structures: trees, roofs, houses, towers, walls, lamps, fields and the sculk corruption pass.
import { Y0, SEA, B, stairs, slab, axis, leaves, lantern, door, hash, hash3, fbm, clamp, pick, DIR, OPP, LEFT, RIGHT, smooth } from "./lib.mjs";

// ---------------------------------------------------------------- vegetation
export function tree(V, x, z, kind = "oak", size = 1, y) {
  y = y ?? V.h(x, z); if (y <= SEA) return false;
  const r = hash(x, z, 77);
  const log = { oak: "oak_log", birch: "birch_log", spruce: "spruce_log", dark: "dark_oak_log", cherry: "cherry_log", dead: "dark_oak_log", azalea: "oak_log" }[kind];
  const lf = leaves({ oak: "oak_leaves", birch: "birch_leaves", spruce: "spruce_leaves", dark: "dark_oak_leaves", cherry: "cherry_leaves", azalea: "flowering_azalea_leaves", dead: "oak_leaves" }[kind]);
  if (kind === "spruce") {
    const h = Math.round(7 + size * 4 + r * 3);
    for (let k = 1; k <= h; k++) V.set(x, y + k, z, axis(log));
    for (let k = 3; k <= h + 1; k++) {
      const rr = Math.max(0, Math.round((h + 1 - k) * 0.42 * (k % 2 ? 1 : 0.7)));
      for (let dz = -rr; dz <= rr; dz++) for (let dx = -rr; dx <= rr; dx++) if (Math.abs(dx) + Math.abs(dz) <= rr + 0.5 && (dx || dz)) V.put(x + dx, y + k, z + dz, lf);
    }
    V.set(x, y + h + 1, z, lf); V.set(x, y + h + 2, z, lf);
    return true;
  }
  if (kind === "dead") {
    const h = Math.round(4 + size * 3 + r * 3);
    for (let k = 1; k <= h; k++) V.set(x, y + k, z, axis(log));
    for (let b = 0; b < 3; b++) {
      const d = pick(["north", "south", "east", "west"], hash(x, z, 80 + b)), [ddx, ddz] = DIR[d], by = y + h - 1 - b * 2, L = 2 + Math.floor(hash(x, z, 90 + b) * 3);
      for (let k = 1; k <= L; k++) V.set(x + ddx * k, by + (k > 1 ? 1 : 0), z + ddz * k, axis(log, ddx ? "x" : "z"));
    }
    return true;
  }
  if (kind === "cherry") {
    const h = Math.round(4 + size * 2 + r * 2);
    for (let k = 1; k <= h; k++) V.set(x, y + k, z, axis(log));
    // two leaning arms + wide flat canopy
    const arms = [[1, 0], [-1, 0], [0, 1], [0, -1]].sort((a, b) => hash(x + a[0], z + a[1], 3) - hash(x + b[0], z + b[1], 3)).slice(0, 2);
    const tops = [[x, y + h, z]];
    for (const [ax, az] of arms) { let cx = x, cy = y + h - 1, cz = z; for (let k = 0; k < 3; k++) { cx += ax; cz += az; cy += k ? 1 : 0; V.set(cx, cy, cz, axis(log, ax ? "x" : "z")); } tops.push([cx, cy, cz]); }
    for (const [tx, ty, tz] of tops) {
      const R = Math.round(2.6 + size);
      for (let dy = -1; dy <= 2; dy++) { const rr = dy === 2 ? R - 2 : dy === -1 ? R - 1 : R; for (let dz = -rr; dz <= rr; dz++) for (let dx = -rr; dx <= rr; dx++) { const dd = dx * dx + dz * dz; if (dd > rr * rr + 1 || (dd > (rr - 1) * (rr - 1) && hash3(tx + dx, ty + dy, tz + dz, 5) < 0.35)) continue; V.put(tx + dx, ty + dy + 1, tz + dz, lf); } }
      for (let k = 0; k < 4; k++) { const hx = tx + Math.round((hash(tx, tz, 40 + k) - 0.5) * 2 * R), hz = tz + Math.round((hash(tx, tz, 50 + k) - 0.5) * 2 * R); if (V.air(hx, ty - 1, hz) && V.name(hx, ty, hz).startsWith("cherry_leaves")) V.set(hx, ty - 1, hz, lf); }
    }
    return true;
  }
  const h = Math.round((kind === "dark" ? 5 : 4) + size * 2 + r * 2);
  const thick = kind === "dark" && size >= 1.5;
  for (let k = 1; k <= h; k++) { V.set(x, y + k, z, axis(log)); if (thick) { V.set(x + 1, y + k, z, axis(log)); V.set(x, y + k, z + 1, axis(log)); V.set(x + 1, y + k, z + 1, axis(log)); } }
  const R = Math.round(2 + size + (thick ? 1 : 0)), top = y + h;
  for (let dy = -2; dy <= 2; dy++) {
    const rr = dy === 2 ? Math.max(1, R - 2) : dy === 1 ? R - 1 : R;
    for (let dz = -rr; dz <= rr + (thick ? 1 : 0); dz++) for (let dx = -rr; dx <= rr + (thick ? 1 : 0); dx++) {
      const ex = dx > 0 && thick ? dx - 1 : dx, ez = dz > 0 && thick ? dz - 1 : dz, dd = ex * ex + ez * ez;
      if (dd > rr * rr + 0.5) continue;
      if (dd >= (rr - 0.5) * (rr - 0.5) && hash3(x + dx, top + dy, z + dz, 7) < 0.4) continue;
      V.put(x + dx, top + dy, z + dz, lf);
    }
  }
  return true;
}
export function bush(V, x, z, lf = "oak_leaves", y) {
  y = y ?? V.h(x, z); if (y <= SEA) return;
  V.put(x, y + 1, z, leaves(lf));
  for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) if (hash(x + dx, z + dz, 12) < 0.6) V.put(x + dx, y + 1, z + dz, leaves(lf));
  if (hash(x, z, 13) < 0.5) V.put(x, y + 2, z, leaves(lf));
}
const FLOWERS = ["poppy", "dandelion", "cornflower", "oxeye_daisy", "azure_bluet", "allium", "red_tulip", "white_tulip", "pink_tulip"];
export function groundCover(V, x, z, dens = 0.25, flowers = FLOWERS, fdens = 0.04) {
  const y = V.h(x, z); if (y <= SEA || !V.air(x, y + 1, z) || !/grass_block/.test(V.name(x, y, z))) return;
  const r = hash(x, z, 600);
  if (r < fdens) V.set(x, y + 1, z, pick(flowers, hash(Math.floor(x / 6), Math.floor(z / 6), 601)));
  else if (r < fdens + dens) V.set(x, y + 1, z, r < fdens + dens * 0.12 ? "tall_grass[half=lower]" : "short_grass");
  if (V.name(x, y + 1, z) === "tall_grass[half=lower]") V.set(x, y + 2, z, "tall_grass[half=upper]");
}

// ---------------------------------------------------------------- roofs
/**
 * Gable roof over wall box [x0..x1]x[z0..z1]; first stair row at y (eave). ridge = 'x' (slopes face N/S) or 'z'.
 * gable(x|z, y) is called for gable-end wall cells. Returns ridge y.
 */
export function gableRoof(V, x0, z0, x1, z1, y, ridge, st, full, gable, over = 1) {
  if (ridge === "x") {
    let a = z0 - over, b = z1 + over, k = 0;
    while (a <= b) {
      if (a === b) { for (let x = x0 - over; x <= x1 + over; x++) V.set(x, y + k, a, full); return y + k; }
      for (let x = x0 - over; x <= x1 + over; x++) { V.set(x, y + k, a, stairs(st, "south")); V.set(x, y + k, b, stairs(st, "north")); }
      for (let z = a + 1; z <= b - 1; z++) if (z >= z0 && z <= z1) { gable(x0, y + k, z); gable(x1, y + k, z); }
      if (b - a === 1) { for (let x = x0 - over; x <= x1 + over; x++) { V.set(x, y + k + 1, a, slab(slabOf(st), "bottom")); V.set(x, y + k + 1, b, slab(slabOf(st), "bottom")); } return y + k + 1; }
      a++; b--; k++;
    }
    return y + k;
  } else {
    let a = x0 - over, b = x1 + over, k = 0;
    while (a <= b) {
      if (a === b) { for (let z = z0 - over; z <= z1 + over; z++) V.set(a, y + k, z, full); return y + k; }
      for (let z = z0 - over; z <= z1 + over; z++) { V.set(a, y + k, z, stairs(st, "east")); V.set(b, y + k, z, stairs(st, "west")); }
      for (let x = a + 1; x <= b - 1; x++) if (x >= x0 && x <= x1) { gable(x, y + k, z0); gable(x, y + k, z1); }
      if (b - a === 1) { for (let z = z0 - over; z <= z1 + over; z++) { V.set(a, y + k + 1, z, slab(slabOf(st), "bottom")); V.set(b, y + k + 1, z, slab(slabOf(st), "bottom")); } return y + k + 1; }
      a++; b--; k++;
    }
    return y + k;
  }
}
export const slabOf = st => st.replace(/_stairs$/, "_slab").replace(/^(stone_brick|brick|deepslate_tile|deepslate_brick|purpur|quartz|end_stone_brick|mud_brick|polished_blackstone_brick|cobbled_deepslate|polished_deepslate|blackstone|cobblestone|mossy_cobblestone|mossy_stone_brick|prismarine|smooth_quartz|cut_copper|waxed_cut_copper|waxed_exposed_cut_copper|waxed_weathered_cut_copper|waxed_oxidized_cut_copper|sandstone)_slab$/, "$1_slab");
/** Cone/pyramid roof (square, stepped) for towers: radius r at y, shrinking by 1 per layer. */
export function spire(V, cx, cz, r, y, st, full, round = true) {
  let k = 0;
  for (let rr = r; rr >= 0; rr--, k++) {
    for (let dz = -rr; dz <= rr; dz++) for (let dx = -rr; dx <= rr; dx++) {
      const d = round ? Math.hypot(dx, dz) : Math.max(Math.abs(dx), Math.abs(dz));
      if (d > rr + 0.45) continue;
      const edge = d > rr - 0.6;
      if (!edge && rr > 0) continue;
      const f = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? "west" : "east") : (dz > 0 ? "north" : "south");
      V.set(cx + dx, y + k, cz + dz, rr === 0 ? full : stairs(st, f));
    }
    // a second layer for steeper look
    if (rr > 0) for (let dz = -rr; dz <= rr; dz++) for (let dx = -rr; dx <= rr; dx++) { const d = round ? Math.hypot(dx, dz) : Math.max(Math.abs(dx), Math.abs(dz)); if (d <= rr - 0.55 && d > rr - 1.6) V.set(cx + dx, y + k, cz + dz, full); }
  }
  return y + k;
}

// ---------------------------------------------------------------- foundations
/** Solid foundation under a footprint down to the terrain (and 2 below), plus clears terrain above the floor. */
export function foundation(V, x0, z0, x1, z1, y, mat, clearTo) {
  for (let z = z0; z <= z1; z++) for (let x = x0; x <= x1; x++) {
    const h = V.h(x, z);
    if (V.occupied(x, z) && (x === x0 || x === x1 || z === z0 || z === z1)) continue; // a neighbour's apron / eave: leave it
    for (let yy = Math.min(h, y) - 2; yy <= y; yy++) V.set(x, yy, z, mat);
    for (let yy = y + 1; yy <= (clearTo ?? y + 3); yy++) if (yy <= h + 3) V.set(x, yy, z, 0);
  }
  V.mark(x0, z0, x1, z1);
}
/** Ground level for a footprint: rounded average of the terrain (at least SEA+1). */
export function groundY(V, x0, z0, x1, z1) {
  let s = 0, n = 0; for (let z = z0; z <= z1; z++) for (let x = x0; x <= x1; x++) { s += V.h(x, z); n++; }
  return Math.max(SEA + 1, Math.round(s / n));
}

// ---------------------------------------------------------------- medieval house
/**
 * Timber-frame / stone house. box [x0..x1]x[z0..z1] (walls inclusive), ground y, door on side `face`.
 * st: {base, frame, fill, fill2, roofSt, roofFull, floor, win, floors}
 */
export function house(V, x0, z0, x1, z1, face, st) {
  const y = st.y ?? groundY(V, x0, z0, x1, z1), floors = st.floors ?? 2, FH = 4;
  const ringFree = []; for (let z = z0 - 1; z <= z1 + 1; z++) for (let x = x0 - 1; x <= x1 + 1; x++) if ((x < x0 || x > x1 || z < z0 || z > z1) && !V.occupied(x, z)) ringFree.push([x, z]);
  foundation(V, x0 - 1, z0 - 1, x1 + 1, z1 + 1, y, st.base, y + floors * FH + 12);
  for (const [x, z] of ringFree) V.set(x, y, z, st.apron ?? st.base);
  // ground ring (path apron)
  const H = floors * FH;
  const corner = (x, z) => (x === x0 || x === x1) && (z === z0 || z === z1);
  const wall = (x, z) => x === x0 || x === x1 || z === z0 || z === z1;
  for (let k = 1; k <= H; k++) {
    const fl = Math.floor((k - 1) / FH), lk = (k - 1) % FH + 1; // lk 1..4, 4 = floor beam level of the next storey
    for (let z = z0; z <= z1; z++) for (let x = x0; x <= x1; x++) {
      if (!wall(x, z)) { V.set(x, y + k, z, lk === FH && k < H ? st.floor : 0); continue; }
      let m;
      if (corner(x, z)) m = axis(st.frame);
      else if (lk === FH) m = axis(st.frame, x === x0 || x === x1 ? "z" : "x");
      else m = fl === 0 ? st.fill : (st.fill2 ?? st.fill);
      // posts every 4 blocks on upper floors
      if (!corner(x, z) && lk !== FH && fl > 0 && st.posts !== false && ((x === x0 || x === x1) ? (z - z0) % 4 === 0 : (x - x0) % 4 === 0)) m = axis(st.frame);
      V.set(x, y + k, z, m);
    }
  }
  V.fill(x0 + 1, y, z0 + 1, x1 - 1, y, z1 - 1, st.floor);
  // windows (every wall cell at lk 2..3 that is 2+ from corners, alternating)
  for (let fl = 0; fl < floors; fl++) for (const side of ["north", "south", "east", "west"]) {
    const cells = sideCells(x0, z0, x1, z1, side);
    cells.forEach(([x, z], i) => {
      if (i < 1 || i > cells.length - 2) return;
      if ((i + (fl % 2)) % 3 !== 1) return;
      for (const lk of [2, 3]) if (!(fl === 0 && lk === 3 && st.lowWin)) V.set(x, y + fl * FH + lk, z, st.win ?? "glass_pane");
    });
  }
  // door
  const dc = sideCells(x0, z0, x1, z1, face), [dx, dz] = dc[Math.floor(dc.length / 2)];
  V.set(dx, y + 1, dz, door(st.door ?? "spruce_door", face, "lower")); V.set(dx, y + 2, dz, door(st.door ?? "spruce_door", face, "upper"));
  V.set(dx, y + 3, dz, axis(st.frame, face === "north" || face === "south" ? "x" : "z"));
  const [fx, fz] = DIR[face];
  V.set(dx + fx, y, dz + fz, st.step ?? st.apron ?? st.base);
  if (st.lamp !== false) V.set(dx + fx + (fz ? 1 : 0), y + 3, dz + fz + (fx ? 1 : 0), lantern(st.soul));
  // interior
  const ix = Math.floor((x0 + x1) / 2), iz = Math.floor((z0 + z1) / 2);
  for (let fl = 0; fl < floors; fl++) {
    V.set(ix, y + fl * FH + 3, iz, lantern(st.soul, true));
    const cy = y + fl * FH + 1;
    const spots = [[x0 + 1, z0 + 1], [x1 - 1, z0 + 1], [x0 + 1, z1 - 1], [x1 - 1, z1 - 1]];
    spots.forEach(([sx, sz], i) => { if (Math.abs(sx - dx) + Math.abs(sz - dz) <= 2) return; V.set(sx, cy, sz, pick(st.furn ?? ["barrel[facing=up,open=false]", "crafting_table", "bookshelf", "composter[level=0]", "smoker[facing=north,lit=false]"], hash(sx, sz, fl * 7 + i))); });
  }
  // stairs/ladder between floors
  if (floors > 1) for (let k = 1; k <= H - FH; k++) V.set(x0 + 1, y + k, iz, "ladder[facing=east]");
  for (let fl = 1; fl < floors; fl++) V.set(x0 + 1, y + fl * FH, iz, "ladder[facing=east]");
  // roof
  const ridge = st.ridge ?? (x1 - x0 >= z1 - z0 ? "x" : "z");
  const gableMat = st.gable ?? st.fill2 ?? st.fill;
  const ry = gableRoof(V, x0, z0, x1, z1, y + H + 1, ridge, st.roofSt, st.roofFull, (gx, gy, gz) => V.set(gx, gy, gz, gableMat), 1);
  // chimney
  if (st.chimney) {
    const cx = ridge === "x" ? x1 - 1 : ix, cz = ridge === "x" ? iz : z1 - 1;
    for (let yy = y + 1; yy <= ry + 2; yy++) V.set(cx, yy, cz, st.chimney);
    V.set(cx, ry + 3, cz, st.smoke ?? "campfire[lit=true,signal_fire=false,facing=north,waterlogged=false]");
  }
  return { y, top: ry, door: [dx, dz] };
}
export function sideCells(x0, z0, x1, z1, side) {
  const out = [];
  if (side === "north") for (let x = x0; x <= x1; x++) out.push([x, z0]);
  if (side === "south") for (let x = x0; x <= x1; x++) out.push([x, z1]);
  if (side === "west") for (let z = z0; z <= z1; z++) out.push([x0, z]);
  if (side === "east") for (let z = z0; z <= z1; z++) out.push([x1, z]);
  return out;
}

// ---------------------------------------------------------------- round tower
export function roundTower(V, cx, cz, r, y, h, st) {
  for (let dz = -r - 1; dz <= r + 1; dz++) for (let dx = -r - 1; dx <= r + 1; dx++) {
    const d = Math.hypot(dx, dz); if (d > r + 0.5) continue;
    const hh = V.h(cx + dx, cz + dz);
    for (let yy = Math.min(hh, y) - 2; yy <= y; yy++) V.set(cx + dx, yy, cz + dz, st.base ?? st.wall);
    for (let k = 1; k <= h; k++) {
      const wallCell = d > r - 1.0;
      if (wallCell) {
        const win = k % 6 === 3 && (Math.abs(dx) <= 0 || Math.abs(dz) <= 0) && k < h - 1;
        V.set(cx + dx, y + k, cz + dz, win ? (st.win ?? "glass_pane") : (st.wall2 && hash3(cx + dx, y + k, cz + dz, 4) < 0.25 ? st.wall2 : st.wall));
      } else V.set(cx + dx, y + k, cz + dz, k % 6 === 0 ? st.floor ?? "spruce_planks" : 0);
    }
  }
  V.mark(cx - r, cz - r, cx + r, cz + r);
  // crenellated top or spire
  const ty = y + h;
  if (st.spire) {
    for (let dz = -r - 1; dz <= r + 1; dz++) for (let dx = -r - 1; dx <= r + 1; dx++) { const d = Math.hypot(dx, dz); if (d <= r + 0.5) V.set(cx + dx, ty + 1, cz + dz, st.wall); }
    return spire(V, cx, cz, r + 1, ty + 2, st.spire, st.spireFull ?? st.spire.replace("_stairs", "_planks"));
  }
  for (let dz = -r - 1; dz <= r + 1; dz++) for (let dx = -r - 1; dx <= r + 1; dx++) {
    const d = Math.hypot(dx, dz); if (d > r + 1.5) continue;
    if (d <= r - 0.5) V.set(cx + dx, ty, cz + dz, st.floor ?? "spruce_planks");
    else { V.set(cx + dx, ty, cz + dz, st.wall); if (d > r + 0.5 && (Math.round(Math.atan2(dz, dx) * r) % 2 === 0)) V.set(cx + dx, ty + 1, cz + dz, st.wall); else if (d > r + 0.5) V.set(cx + dx, ty + 1, cz + dz, 0); }
  }
  return ty + 1;
}

// ---------------------------------------------------------------- wall along a polyline (thick, walkway, battlements)
export function wallLine(V, pts, y, h, st, skip = () => false) {
  const done = new Set();
  for (let i = 1; i < pts.length; i++) {
    const [ax, az] = pts[i - 1], [bx, bz] = pts[i], len = Math.hypot(bx - ax, bz - az) || 1;
    const nx = -(bz - az) / len, nz = (bx - ax) / len;
    for (let s = 0; s <= len; s += 0.4) for (let t = -1.5; t <= 1.5; t += 0.5) {
      const x = Math.round(ax + (bx - ax) * s / len + nx * t), z = Math.round(az + (bz - az) * s / len + nz * t), k = x * 4096 + z;
      if (done.has(k) || skip(x, z)) continue; done.add(k);
      const outer = Math.abs(t) >= 1.25, hh = V.h(x, z), base = Math.min(hh, y);
      for (let yy = base - 2; yy <= y + h; yy++) V.set(x, yy, z, hash3(x, yy, z, 3) < 0.2 ? (st.wall2 ?? st.wall) : st.wall);
      for (let yy = y + h + 1; yy <= y + h + 3; yy++) V.set(x, yy, z, 0);
      if (outer && t > 0 && ((x + z) % 2 === 0)) V.set(x, y + h + 1, z, st.wall);
      if (!outer) V.set(x, y + h, z, st.walk ?? st.wall);
      V.mark(x, z, x, z);
    }
  }
}

// ---------------------------------------------------------------- small props
export function lampPost(V, x, z, st = {}) {
  const y = st.y ?? V.h(x, z), h = st.h ?? 3;
  for (let k = 1; k <= h; k++) V.set(x, y + k, z, st.post ?? "spruce_fence");
  V.set(x, y + h + 1, z, st.head ?? lantern(st.soul));
  V.mark(x, z, x, z);
}
export function well(V, cx, cz, y, mat = "cobblestone", roof = "spruce") {
  for (let dz = -2; dz <= 2; dz++) for (let dx = -2; dx <= 2; dx++) {
    const ring = Math.max(Math.abs(dx), Math.abs(dz)) === 2;
    V.set(cx + dx, y, cz + dz, mat);
    if (ring) V.set(cx + dx, y + 1, cz + dz, mat); else { V.set(cx + dx, y, cz + dz, "water"); V.set(cx + dx, y - 1, cz + dz, "water"); V.set(cx + dx, y + 1, cz + dz, 0); }
  }
  for (const [dx, dz] of [[-2, -2], [2, -2], [-2, 2], [2, 2]]) for (let k = 2; k <= 4; k++) V.set(cx + dx, y + k, cz + dz, roof + "_fence");
  gableRoof(V, cx - 2, cz - 2, cx + 2, cz + 2, y + 5, "x", roof + "_stairs", roof + "_planks", () => {}, 0);
  V.set(cx, y + 4, cz, "chain[axis=y]"); V.set(cx, y + 3, cz, lantern(false, true));
  V.mark(cx - 2, cz - 2, cx + 2, cz + 2);
}
export function field(V, x0, z0, x1, z1, crop) {
  const y = groundY(V, x0, z0, x1, z1);
  for (let z = z0; z <= z1; z++) for (let x = x0; x <= x1; x++) {
    const edge = x === x0 || x === x1 || z === z0 || z === z1;
    const h = V.h(x, z);
    for (let yy = h + 1; yy <= y + 3; yy++) V.set(x, yy, z, 0);
    for (let yy = Math.min(h, y) - 1; yy < y; yy++) V.set(x, yy, z, "dirt");
    if (edge) { V.set(x, y, z, "oak_log[axis=" + (x === x0 || x === x1 ? "z" : "x") + "]"); continue; }
    if ((x - x0) % 5 === 0) { V.set(x, y, z, "water"); V.set(x, y + 1, z, 0); continue; }
    V.set(x, y, z, "farmland[moisture=7]"); V.set(x, y + 1, z, crop);
    V.surf[z * V.W + x] = y;
  }
  V.mark(x0, z0, x1, z1);
}

// ---------------------------------------------------------------- sculk corruption
/**
 * Infects everything around `centre` (local x,z) within radius R: ground -> sculk/deepslate, walls crack and crumble, roofs
 * get holes, veins creep over faces, sensors/shriekers (can_summon=false: never summon a warden) / catalysts / soul lights
 * scattered. Intensity falls off with distance plus noise so it blends into the healthy town.
 */
export function corrupt(V, cx, cz, R, seed = 7, y0 = SEA - 4, y1 = 140) {
  const I = (x, z) => { const d = Math.hypot(x - cx, z - cz) / R + (fbm(x / 18, z / 18, 3, seed) - 0.5) * 0.55; return clamp(1.35 - d * 1.35, 0, 1); };
  const CRACK = [[/^stone_bricks$/, ["cracked_stone_bricks", "deepslate_bricks", "cracked_deepslate_bricks", "mossy_stone_bricks"]],
    [/^cobblestone$/, ["cobbled_deepslate", "cobblestone", "tuff"]], [/^stone_brick_(stairs|slab|wall)/, null], [/_planks$/, ["air", "dark_oak_planks", "stripped_dark_oak_wood[axis=y]"]],
    [/terracotta$|calcite|white_concrete|smooth_sandstone|mud_bricks|bricks$/, ["sculk", "cracked_deepslate_tiles", "deepslate_tiles", "air"]],
    [/^(oak|spruce|birch|dark_oak)_log/, ["dark_oak_log[axis=y]", "stripped_dark_oak_log[axis=y]"]], [/glass_pane/, ["air", "iron_bars"]], [/^glass$/, ["air", "tinted_glass"]],
    [/^lantern/, null], [/copper/, ["waxed_oxidized_cut_copper", "sculk", "waxed_weathered_copper"]], [/deepslate_bricks$|polished_deepslate$/, ["cracked_deepslate_bricks", "sculk"]],
    [/^grass_block|^dirt|^coarse_dirt|^podzol|^dirt_path|^gravel|^sand$|^moss_block/, ["sculk", "sculk", "deepslate", "tuff", "cobbled_deepslate"]],
    [/leaves/, ["air"]], [/short_grass|tall_grass|poppy|dandelion|tulip|cornflower|daisy|bluet|allium|wheat|carrots|potatoes|flower|petals/, ["air"]], [/farmland/, ["sculk"]],
    [/campfire\[/, ["soul_campfire[lit=true,signal_fire=false,facing=north,waterlogged=false]"]], [/stairs\[/, ["air"]]];
  const lanternRe = /^lantern/;
  const x0 = Math.max(0, Math.floor(cx - R * 1.4)), x1 = Math.min(V.W - 1, Math.ceil(cx + R * 1.4)), z0 = Math.max(0, Math.floor(cz - R * 1.4)), z1 = Math.min(V.D - 1, Math.ceil(cz + R * 1.4));
  for (let z = z0; z <= z1; z++) for (let x = x0; x <= x1; x++) {
    const it = I(x, z); if (it <= 0) continue;
    const h = V.h(x, z), top = Math.min(V.top(x, z), y1);
    for (let y = Math.max(y0, h - 3); y <= top; y++) {
      const id = V.get(x, y, z); if (!id) continue; const n = V.pal[id];
      if (/^water/.test(n)) continue;
      const r = hash3(x, y, z, seed);
      if (lanternRe.test(n)) { V.set(x, y, z, n.replace(/^lantern/, "soul_lantern")); continue; }
      for (const [re, opts] of CRACK) {
        if (!re.test(n)) continue;
        if (opts === null) {
          if (n.startsWith("stone_brick_")) { if (r < it * 0.5) V.set(x, y, z, n.replace("stone_brick", it > 0.6 ? "deepslate_brick" : "mossy_stone_brick")); }
          break;
        }
        const ground = y <= h && y >= h - 1;
        const p = ground ? smooth(0.1, 0.42, it) * 1.05 : /leaves|grass|poppy|wheat|flower|petals|tulip/.test(n) ? it * 1.3 : /stairs/.test(n) ? it * 0.45 : /glass/.test(n) ? it * 0.9 : it * 0.62;
        if (r < p) {
          let rep = opts[Math.floor(hash3(x, y, z, seed + 1) * opts.length)];
          if (rep === "air" && (y <= h || /_planks$/.test(n) && y < h + 2)) rep = "sculk";
          if (rep === "air" && it < 0.35) break;   // only the core really crumbles
          V.set(x, y, z, rep);
        }
        break;
      }
    }
  }
  // collapse: upper parts of walls fall where the intensity is high (keep the bottom 2 levels)
  for (let z = z0; z <= z1; z++) for (let x = x0; x <= x1; x++) {
    const it = I(x, z); if (it < 0.5) continue;
    const h = V.h(x, z), top = V.top(x, z);
    const cut = h + 3 + Math.floor((1 - it) * 14 + fbm(x / 6, z / 6, 2, seed + 4) * 8);
    for (let y = cut; y <= top; y++) if (!/^water/.test(V.name(x, y, z))) V.set(x, y, z, 0);
  }
  // veins on exposed faces + sensors / shriekers / catalysts / soul lights on the ground
  for (let z = z0; z <= z1; z++) for (let x = x0; x <= x1; x++) {
    const it = I(x, z); if (it <= 0.05) continue;
    const h = V.h(x, z), top = Math.min(V.top(x, z) + 1, y1);
    for (let y = h + 1; y <= top; y++) {
      if (!V.air(x, y, z)) continue;
      const onGround = y === h + 1;
      if (hash3(x, y, z, seed + 2) > it * (onGround ? 0.22 : 0.55)) continue;
      const faces = {};
      const S = (dx, dy, dz) => { const nn = V.name(x + dx, y + dy, z + dz); return nn !== "air" && !/water|vein|lantern|door|pane|bars|fence|torch|rod|stairs|slab|chain|campfire|sensor|shrieker|grass|flower|petal|ladder|rail|carpet|leaves/.test(nn); };
      if (S(0, -1, 0)) faces.down = true; if (S(0, 1, 0)) faces.up = true; if (S(0, 0, -1)) faces.north = true; if (S(0, 0, 1)) faces.south = true; if (S(1, 0, 0)) faces.east = true; if (S(-1, 0, 0)) faces.west = true;
      if (!Object.keys(faces).length) continue;
      if (faces.down && !(y === h + 1)) delete faces.down;
      if (!Object.keys(faces).length) continue;
      if (y === h + 1 && /^sculk$/.test(V.name(x, h, z))) continue; // sculk ground stays clean on top (props below)
      const p = {}; for (const f of ["down", "east", "north", "south", "up", "west"]) p[f] = faces[f] ? "true" : "false"; p.waterlogged = "false";
      V.set(x, y, z, B("sculk_vein", p));
    }
    // ground props
    const g = V.name(x, h, z);
    if (!/^sculk$|deepslate|tuff/.test(g) || !V.air(x, h + 1, z) || V.occupied(x, z) && it < 0.5) continue;
    const r = hash(x, z, seed + 30);
    if (r < it * 0.012) V.set(x, h + 1, z, "sculk_shrieker[can_summon=false,shrieking=false,waterlogged=false]");
    else if (r < it * 0.035) V.set(x, h + 1, z, "sculk_sensor[power=0,sculk_sensor_phase=inactive,waterlogged=false]");
    else if (r < it * 0.04) V.set(x, h, z, "sculk_catalyst[bloom=false]");
    else if (r < it * 0.05) { V.set(x, h + 1, z, "cobbled_deepslate_wall"); V.set(x, h + 2, z, lantern(true)); }
    else if (r < it * 0.056) V.set(x, h + 1, z, "soul_campfire[lit=true,signal_fire=false,facing=north,waterlogged=false]");
    else if (r < it * 0.075) { V.set(x, h + 1, z, "sculk_vein[down=true,east=false,north=false,south=false,up=false,west=false,waterlogged=false]"); }
    else if (r < it * 0.08 && it > 0.4) tree(V, x, z, "dead", 0.5 + it, h);
  }
  return I;
}
