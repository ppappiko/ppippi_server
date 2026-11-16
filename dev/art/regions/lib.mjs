// Shared voxel model, noise, island terrain, block helpers and the plugin export format for the three wild-world regions.
// Local coordinates: x, z in [0, W) / [0, D) (origin = region min corner), y = real world y (Y0 .. Y1).
import fs from "node:fs";
import path from "node:path";
import zlib from "node:zlib";

export const Y0 = 40;          // lowest y the plugin writes
export const SEA = 62;         // water surface block (vanilla sea level 63 = first air)

// ---------------------------------------------------------------- hashing / noise
export function hash(x, z, s = 0) {
  let h = (x * 374761393 + z * 668265263 + s * 2147483647) | 0;
  h = Math.imul(h ^ (h >>> 13), 1274126177); h ^= h >>> 16;
  return (h >>> 0) / 4294967296;
}
export const hash3 = (x, y, z, s = 0) => hash(x * 31 + y * 7919, z * 17 - y * 104729, s);
const fade = t => t * t * (3 - 2 * t);
export function vnoise(x, z, s = 0) {
  const xi = Math.floor(x), zi = Math.floor(z), xf = fade(x - xi), zf = fade(z - zi);
  const a = hash(xi, zi, s), b = hash(xi + 1, zi, s), c = hash(xi, zi + 1, s), d = hash(xi + 1, zi + 1, s);
  return a + (b - a) * xf + (c - a) * zf + (a - b - c + d) * xf * zf;
}
export function fbm(x, z, oct = 4, s = 0) {
  let v = 0, amp = 0.5, f = 1, n = 0;
  for (let i = 0; i < oct; i++) { v += amp * vnoise(x * f, z * f, s + i * 101); n += amp; amp *= 0.5; f *= 2.03; }
  return v / n;
}
export const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
export const lerp = (a, b, t) => a + (b - a) * t;
export const smooth = (e0, e1, v) => { const t = clamp((v - e0) / (e1 - e0), 0, 1); return t * t * (3 - 2 * t); };
export const pick = (arr, r) => arr[Math.min(arr.length - 1, Math.floor(r * arr.length))];

// ---------------------------------------------------------------- block-state helpers
export const B = (n, p) => p ? n + "[" + Object.entries(p).map(([k, v]) => k + "=" + v).join(",") + "]" : n;
export const stairs = (n, facing, half = "bottom", shape) => B(n, shape ? { facing, half, shape } : { facing, half });
export const slab = (n, type = "bottom") => B(n, { type });
export const axis = (n, a = "y") => B(n, { axis: a });
export const OPP = { north: "south", south: "north", east: "west", west: "east" };
export const DIR = { north: [0, -1], south: [0, 1], east: [1, 0], west: [-1, 0] };
export const LEFT = { north: "west", west: "south", south: "east", east: "north" };
export const RIGHT = { north: "east", east: "south", south: "west", west: "north" };
export const leaves = n => B(n, { persistent: "true" });
export const lantern = (soul = false, hanging = false) => B(soul ? "soul_lantern" : "lantern", { hanging });
export const door = (n, facing, half, hinge = "left") => B(n, { facing, half, hinge, open: "false" });

// ---------------------------------------------------------------- voxel model
export class Vox {
  constructor(W, D, Y1) {
    this.W = W; this.D = D; this.Y1 = Y1; this.NY = Y1 - Y0 + 1;
    this.g = new Uint16Array(W * D * this.NY);
    this.pal = ["air"]; this.pid = new Map([["air", 0]]);
    this.occ = new Uint8Array(W * D);     // 1 = something built here (no trees / decor)
    this.surf = new Int16Array(W * D);    // terrain surface y per column
  }
  id(s) { let i = this.pid.get(s); if (i === undefined) { i = this.pal.length; this.pal.push(s); this.pid.set(s, i); } return i; }
  in(x, y, z) { return x >= 0 && z >= 0 && x < this.W && z < this.D && y >= Y0 && y <= this.Y1; }
  set(x, y, z, s) { if (!this.in(x, y, z)) return; this.g[((y - Y0) * this.D + z) * this.W + x] = typeof s === "number" ? s : this.id(s); }
  get(x, y, z) { return this.in(x, y, z) ? this.g[((y - Y0) * this.D + z) * this.W + x] : 0; }
  name(x, y, z) { return this.pal[this.get(x, y, z)]; }
  air(x, y, z) { return this.get(x, y, z) === 0; }
  fill(x0, y0, z0, x1, y1, z1, s) {
    const id = typeof s === "number" ? s : this.id(s);
    for (let y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) for (let z = Math.min(z0, z1); z <= Math.max(z0, z1); z++)
      for (let x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) this.set(x, y, z, id);
  }
  /** set only where currently air */
  put(x, y, z, s) { if (this.air(x, y, z)) this.set(x, y, z, s); }
  top(x, z) { for (let y = this.Y1; y >= Y0; y--) if (this.get(x, y, z)) return y; return Y0 - 1; }
  h(x, z) { return x < 0 || z < 0 || x >= this.W || z >= this.D ? SEA - 8 : this.surf[z * this.W + x]; }
  occupied(x, z) { return x < 0 || z < 0 || x >= this.W || z >= this.D || this.occ[z * this.W + x] === 1; }
  mark(x0, z0, x1, z1) { for (let z = Math.max(0, Math.min(z0, z1)); z <= Math.min(this.D - 1, Math.max(z0, z1)); z++) for (let x = Math.max(0, Math.min(x0, x1)); x <= Math.min(this.W - 1, Math.max(x0, x1)); x++) this.occ[z * this.W + x] = 1; }
  land(x, z) { return this.h(x, z) >= SEA; }
}

// ---------------------------------------------------------------- island terrain
/**
 * Heightmap for a 330x330 "continent": a noisy island (radius R) with sand beaches and a sea that deepens to the square's edge.
 * flats: [{x, z, r, y, blend}] circles flattened to height y (blend = soft edge width); rects: [{x0,z0,x1,z1,y,blend}].
 * Returns Float32 heights (surface block y); land where h >= SEA + 1.
 */
export function heightmap(W, D, o) {
  const H = new Float32Array(W * D), cx = W / 2, cz = D / 2, R = o.R ?? 138, seed = o.seed ?? 1;
  for (let z = 0; z < D; z++) for (let x = 0; x < W; x++) {
    const dx = x - cx, dz = z - cz, ang = Math.atan2(dz, dx);
    const warp = (fbm(x / 70, z / 70, 4, seed) - 0.5) * 0.55 + (fbm(x / 22, z / 22, 3, seed + 9) - 0.5) * 0.12;
    const lobe = 1 + 0.08 * Math.sin(ang * 3 + seed) + 0.05 * Math.sin(ang * 5 + seed * 2);
    const d = Math.hypot(dx, dz) / (R * lobe) + warp;          // < 1 = land
    let h;
    if (d < 1) {
      const inland = smooth(1.0, 0.82, d);                    // 0 at the coast .. 1 inland
      const hills = (fbm(x / 48, z / 48, 5, seed + 3) - 0.35) * (o.hills ?? 14) * smooth(0.95, 0.55, d);
      h = SEA + 1 + inland * ((o.base ?? 3) + Math.max(0, hills)) + (1 - inland) * 0.4;
    } else {
      const edge = Math.min(x, z, W - 1 - x, D - 1 - z);     // keep the outer ring deep
      h = SEA - 1.5 - Math.min(13, (d - 1) * 38) - (edge < 12 ? (12 - edge) * 0.3 : 0) + (fbm(x / 14, z / 14, 2, seed + 5) - 0.5) * 2;
    }
    H[z * W + x] = h;
  }
  const flat = (x, z, f) => {
    let t;
    if (f.r !== undefined) t = smooth(f.r + (f.blend ?? 10), f.r, Math.hypot(x - f.x, z - f.z));
    else { const ex = Math.max(f.x0 - x, 0, x - f.x1), ez = Math.max(f.z0 - z, 0, z - f.z1); t = smooth(f.blend ?? 10, 0, Math.hypot(ex, ez)); }
    return t;
  };
  for (const f of o.flats || []) for (let z = 0; z < D; z++) for (let x = 0; x < W; x++) {
    const i = z * W + x, t = flat(x, z, f); if (t <= 0) continue;
    if (H[i] < SEA + 1 && !f.force) continue;               // keep the coastline unless forced
    H[i] = lerp(H[i], f.y, t);
  }
  return H;
}

/** Coast distance (Chebyshev BFS from water columns, capped at 12). */
export function coastDist(V) {
  const { W, D } = V, d = new Uint8Array(W * D).fill(12), q = [];
  for (let i = 0; i < W * D; i++) if (V.surf[i] < SEA + 1) { d[i] = 0; q.push(i); }
  for (let k = 0; k < q.length; k++) {
    const i = q[k], x = i % W, z = (i / W) | 0; if (d[i] >= 11) continue;
    for (let dz = -1; dz <= 1; dz++) for (let dx = -1; dx <= 1; dx++) {
      const nx = x + dx, nz = z + dz; if (nx < 0 || nz < 0 || nx >= W || nz >= D) continue;
      const j = nz * W + nx; if (d[j] > d[i] + 1) { d[j] = d[i] + 1; q.push(j); }
    }
  }
  return (x, z) => x < 0 || z < 0 || x >= W || z >= D ? 0 : d[z * W + x];
}

/**
 * Writes the terrain columns: deep stone, sub-surface, surface block from surfFn(x, z, h, coast) -> {top, sub, deep}, water to SEA.
 */
export function paintTerrain(V, H, surfFn) {
  for (let z = 0; z < V.D; z++) for (let x = 0; x < V.W; x++) V.surf[z * V.W + x] = Math.round(H[z * V.W + x]);
  const coast = coastDist(V);
  for (let z = 0; z < V.D; z++) for (let x = 0; x < V.W; x++) {
    const h = V.surf[z * V.W + x], c = coast(x, z), m = surfFn(x, z, h, c);
    for (let y = Y0; y <= h; y++) V.set(x, y, z, y === h ? m.top : y >= h - 3 ? m.sub : m.deep);
    for (let y = h + 1; y <= SEA; y++) V.set(x, y, z, "water");
  }
  return coast;
}

/** Re-levels one column to height y (keeps the column's surface materials). */
export function setColumn(V, x, z, y, top, sub, deep = "stone") {
  if (x < 0 || z < 0 || x >= V.W || z >= V.D) return;
  const old = V.surf[z * V.W + x];
  for (let yy = Math.min(old, y) - 4; yy <= Math.max(old, y) + 1; yy++) {
    if (yy < Y0) continue;
    if (yy > y) V.set(x, yy, z, yy <= SEA ? "water" : 0);
    else V.set(x, yy, z, yy === y ? top : yy >= y - 3 ? sub : deep);
  }
  V.surf[z * V.W + x] = y;
}

// ---------------------------------------------------------------- 2D stamping
/** Calls fn(x, z, t, dist) for cells within r of the polyline pts ([[x,z],...]); t = 0..1 along it. */
export function alongPath(pts, r, fn) {
  const seen = new Set();
  let total = 0; for (let i = 1; i < pts.length; i++) total += Math.hypot(pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1]);
  let acc = 0;
  for (let i = 1; i < pts.length; i++) {
    const [ax, az] = pts[i - 1], [bx, bz] = pts[i], len = Math.hypot(bx - ax, bz - az) || 1;
    for (let s = 0; s <= len; s += 0.5) {
      const px = ax + (bx - ax) * s / len, pz = az + (bz - az) * s / len;
      for (let dz = -Math.ceil(r); dz <= Math.ceil(r); dz++) for (let dx = -Math.ceil(r); dx <= Math.ceil(r); dx++) {
        const d = Math.hypot(dx, dz); if (d > r) continue;
        const X = Math.round(px + dx), Z = Math.round(pz + dz), k = X * 4096 + Z; if (seen.has(k)) continue; seen.add(k);
        fn(X, Z, (acc + s) / total, d);
      }
    }
    acc += len;
  }
}
/** Smooth curve through control points (Catmull-Rom), returns dense points. */
export function spline(ctrl, step = 2) {
  const out = [];
  for (let i = 0; i < ctrl.length - 1; i++) {
    const p0 = ctrl[Math.max(0, i - 1)], p1 = ctrl[i], p2 = ctrl[i + 1], p3 = ctrl[Math.min(ctrl.length - 1, i + 2)];
    const len = Math.hypot(p2[0] - p1[0], p2[1] - p1[1]), n = Math.max(2, Math.ceil(len / step));
    for (let k = 0; k < n; k++) {
      const t = k / n, t2 = t * t, t3 = t2 * t;
      const f = (a, b, c, d) => 0.5 * (2 * b + (-a + c) * t + (2 * a - 5 * b + 4 * c - d) * t2 + (-a + 3 * b - 3 * c + d) * t3);
      out.push([f(p0[0], p1[0], p2[0], p3[0]), f(p0[1], p1[1], p2[1], p3[1])]);
    }
  }
  out.push(ctrl[ctrl.length - 1]);
  return out;
}
/** Sets the surface block of a column (keeps height). */
export function surface(V, x, z, s) { if (x < 0 || z < 0 || x >= V.W || z >= V.D) return; const h = V.surf[z * V.W + x]; if (h > SEA) V.set(x, h, z, s); }

// ---------------------------------------------------------------- export (plugin format)
function varint(arr, v) { while (v >= 0x80) { arr.push((v & 0x7f) | 0x80); v >>>= 7; } arr.push(v); }
/**
 * Binary layout read by WildRegions.java (gzip):
 *   "PRG2", int W, int D, int Y0, short palCount, palCount x UTF(block state "minecraft:..."),
 *   int spawnX, int spawnY, int spawnZ, float spawnYaw (local x/z),
 *   int entranceX, int entranceY, int entranceZ (local x/z, real y of the boss-room entrance marker block; y = -9999 when none),
 *   then W*D columns (z-major, x inner):
 *   varint runs, runs x (varint palIndex, varint length) from Y0 upward; above the last run = air (cleared to the old surface).
 */
export function exportRegion(V, file, spawn, entrance) {
  const bytes = [];
  const head = Buffer.alloc(16); head.write("PRG2", 0, "latin1"); head.writeInt32BE(V.W, 4); head.writeInt32BE(V.D, 8); head.writeInt32BE(Y0, 12);
  const pal = V.pal.map(s => "minecraft:" + s), parts = [head];
  const pc = Buffer.alloc(2); pc.writeUInt16BE(pal.length); parts.push(pc);
  for (const s of pal) { const b = Buffer.from(s, "utf8"), l = Buffer.alloc(2); l.writeUInt16BE(b.length); parts.push(l, b); }
  const sp = Buffer.alloc(16); sp.writeInt32BE(spawn.x, 0); sp.writeInt32BE(spawn.y, 4); sp.writeInt32BE(spawn.z, 8); sp.writeFloatBE(spawn.yaw ?? 0, 12); parts.push(sp);
  const en = Buffer.alloc(12); en.writeInt32BE(entrance ? entrance.x : 0, 0); en.writeInt32BE(entrance ? entrance.y : -9999, 4); en.writeInt32BE(entrance ? entrance.z : 0, 8); parts.push(en);
  let blocks = 0;
  for (let z = 0; z < V.D; z++) for (let x = 0; x < V.W; x++) {
    let top = V.top(x, z); const runs = [];
    for (let y = Y0; y <= top; ) { const id = V.get(x, y, z); let n = 1; while (y + n <= top && V.get(x, y + n, z) === id) n++; runs.push(id, n); y += n; blocks += n; }
    varint(bytes, runs.length / 2); for (const v of runs) varint(bytes, v);
  }
  parts.push(Buffer.from(bytes));
  const gz = zlib.gzipSync(Buffer.concat(parts), { level: 9 });
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, gz);
  console.log("export", file, (gz.length / 1024).toFixed(0) + " KB", "palette", pal.length, "blocks", blocks);
}
