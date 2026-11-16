// Sparse-ish voxel world for the town: ground surface grid + structure blocks above/inside ground.
export const BASE = 63;          // surface block y
export const Y0 = 58, Y1 = 118;  // structure layer range (inclusive)
export const HASH = (x, z, s = 0) => { let h = (x * 73856093) ^ (z * 19349663) ^ (s * 83492791); h ^= h >>> 13; h = Math.imul(h, 0x5bd1e995); h ^= h >>> 15; return ((h >>> 0) % 10000) / 10000; };
const DIRS = { n: [0, -1], e: [1, 0], s: [0, 1], w: [-1, 0] };
export const rotVec = (x, z, k) => { for (let i = 0; i < k; i++) { const nx = -z, nz = x; x = nx; z = nz; } return [x, z]; };
export function rotFacing(f, k) { if (!DIRS[f ? f[0] : ""] || f.length !== (f === "north" || f === "south" || f === "east" || f === "west" ? f.length : 0)) return f; const short = { north: "n", south: "s", east: "e", west: "w" }; const long = { n: "north", s: "south", e: "east", w: "west" }; const d = DIRS[short[f]]; if (!d) return f; const [x, z] = rotVec(d[0], d[1], k); return long[Object.keys(DIRS).find(c => DIRS[c][0] === x && DIRS[c][1] === z)]; }
export function norm(b) { if (typeof b === "string") { const i = b.indexOf("["); if (i < 0) return { n: b, p: {} }; const p = {}; b.slice(i + 1, -1).split(",").forEach(kv => { const [k, v] = kv.split("="); p[k] = v; }); return { n: b.slice(0, i), p }; } if (Array.isArray(b)) return { n: b[0], p: { ...(b[1] || {}) } }; return b; }
export const key = ({ n, p }) => (n.startsWith("minecraft:") ? n : "minecraft:" + n) + (Object.keys(p).length ? "[" + Object.keys(p).sort().map(k => k + "=" + p[k]).join(",") + "]" : "");

export class World {
  constructor(I) {
    this.I = I; this.W = I.W; this.H = I.H; this.x0 = I.x0; this.z0 = I.z0; this.NY = Y1 - Y0 + 1;
    this.g = new Uint16Array(this.W * this.H * this.NY);
    this.pal = ["", "minecraft:air"]; this.pid = new Map([["minecraft:air", 1]]);
    this.surf = new Uint8Array(this.W * this.H); // surface codes, 0 = water
    this.entities = []; this.warps = []; this.npcs = [];
  }
  id(b) { const k = key(norm(b)); let i = this.pid.get(k); if (i === undefined) { i = this.pal.length; this.pal.push(k); this.pid.set(k, i); } return i; }
  idx(x, y, z) { const gx = x - this.x0, gz = z - this.z0; if (gx < 0 || gz < 0 || gx >= this.W || gz >= this.H || y < Y0 || y > Y1) return -1; return ((y - Y0) * this.H + gz) * this.W + gx; }
  set(x, y, z, b) { const i = this.idx(x, y, z); if (i >= 0) this.g[i] = typeof b === "number" ? b : this.id(b); }
  get(x, y, z) { const i = this.idx(x, y, z); return i < 0 ? 0 : this.g[i]; }
  name(x, y, z) { const v = this.get(x, y, z); return v ? this.pal[v] : ""; }
  fill(x0, y0, z0, x1, y1, z1, b) { const id = this.id(b); for (let y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) for (let z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) for (let x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) this.set(x, y, z, id); }
  land(x, z) { const gx = x - this.x0, gz = z - this.z0; return gx >= 0 && gz >= 0 && gx < this.W && gz < this.H && this.I.land[gz * this.W + gx] === 1; }
  setSurf(x, z, c) { const gx = x - this.x0, gz = z - this.z0; if (gx >= 0 && gz >= 0 && gx < this.W && gz < this.H && this.I.land[gz * this.W + gx]) this.surf[gz * this.W + gx] = c; }
  getSurf(x, z) { const gx = x - this.x0, gz = z - this.z0; return gx >= 0 && gz >= 0 && gx < this.W && gz < this.H ? this.surf[gz * this.W + gx] : 0; }
}
// Building-local drawing context. local +z = direction of the front door (F).
export class Ctx {
  constructor(w, cx, cz, F) { this.w = w; this.cx = cx; this.cz = cz; this.F = F; this.k = { S: 0, W: 1, N: 2, E: 3 }[F]; }
  pos(lx, lz) { const [x, z] = rotVec(lx, lz, this.k); return [this.cx + x, this.cz + z]; }
  rb(b) { const o = norm(b); const p = { ...o.p }; if (p.facing) p.facing = rotFacing(p.facing, this.k); if (p.axis && this.k % 2) p.axis = p.axis === "x" ? "z" : p.axis === "z" ? "x" : p.axis; return { n: o.n, p }; }
  s(lx, ly, lz, b) { const [x, z] = this.pos(lx, lz); this.w.set(x, BASE + ly, z, this.rb(b)); }
  f(x0, y0, z0, x1, y1, z1, b) { const id = this.w.id(this.rb(b)); for (let y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) for (let z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) for (let x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) { const [wx, wz] = this.pos(x, z); this.w.set(wx, BASE + y, wz, id); } }
  g(lx, ly, lz) { const [x, z] = this.pos(lx, lz); return this.w.name(x, BASE + ly, z); }
  /** world position of a local block (ground level y = BASE) */
  wpos(lx, ly, lz) { const [x, z] = this.pos(lx, lz); return [x, BASE + ly, z]; }
  /** local yaw (degrees, MC convention: 0 = south, 90 = west, 180 = north, 270 = east) for an entity looking along local dir */
  yaw(localDir) { const d = { s: 0, w: 90, n: 180, e: 270 }[localDir]; const rot = (this.k * 90) ; // local->world rotation: k * 90 deg of matrix, maps s->w for k=1 => yaw +90
    return (d + rot) % 360; }
  frame(lx, ly, lz, facing, item) { const [x, y, z] = this.wpos(lx, ly, lz); this.w.entities.push({ t: "frame", x, y, z, f: rotFacing(facing, this.k), item }); }
  stand(lx, ly, lz, localDir, gear = {}) { const [x, y, z] = this.wpos(lx, ly, lz); this.w.entities.push({ t: "stand", x, y, z, yaw: this.yaw(localDir), ...gear }); }
  npc(role, lx, ly, lz, localDir) { const [x, y, z] = this.wpos(lx, ly, lz); this.w.npcs.push({ role, x, y, z, yaw: this.yaw(localDir) }); }
  warp(id, name, lx, ly, lz, localDir) { const [x, y, z] = this.wpos(lx, ly, lz); this.w.warps.push({ id, name, x, y, z, yaw: this.yaw(localDir) }); }
}
