// Shared builder for Blockbench pet models (format "free", per-face UV, one painted texture), as used by PetModels.
// model = Model(name, T); model.cube(name, from, to, paint); model.G(name, origin, children, rot); model.write(outliner, file)
// paint(x, y, z, face, i, j, w, h) -> "#rrggbb" ; x,y,z = model position of the texel centre (wraps patterns across faces)
import fs from "node:fs";
import crypto from "node:crypto";
import { writePng } from "./class-icons/pngio.mjs";

export const hash = (a, b, c) => { let h = Math.imul(a | 0, 374761393) ^ Math.imul(b | 0, 668265263) ^ Math.imul(c | 0, 2147483647); h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) >>> 0) / 4294967296; };
const hx = s => [parseInt(s.slice(1, 3), 16), parseInt(s.slice(3, 5), 16), parseInt(s.slice(5, 7), 16)];
const toHex = c => "#" + c.map(v => Math.max(0, Math.min(255, Math.round(v))).toString(16).padStart(2, "0")).join("");
export const mix = (a, b, k) => { const x = hx(a), y = hx(b); return toHex(x.map((v, i) => v + (y[i] - v) * k)); };
export const shade = (c, k) => mix(c, k > 0 ? "#ffffff" : "#000000", Math.abs(k));

/** Soft per-face shading + a little texel noise: top lighter, bottom darker, east/west a touch darker. */
export function fur(base, { noise = .05, light = .1, dark = .22, dither = 2 } = {}) {
  return (x, y, z, f) => {
    const n = hash(Math.floor(x * dither), Math.floor(y * dither), Math.floor(z * dither));
    let c = f === "up" ? shade(base, light) : f === "down" ? shade(base, -dark) : f === "east" || f === "west" ? shade(base, -.06) : base;
    if (n < noise) c = shade(c, -.08); else if (n > 1 - noise) c = shade(c, .07);
    return c;
  };
}
export const flat = c => () => c;
export const edge = (c, e) => (x, y, z, f, i, j, w, h) => (i === 0 || j === 0 || i === w - 1 || j === h - 1) ? e : c;
/** Face painter: a different painter for the given face names, falling back to 'rest'. */
export const faces = (map, rest) => (...a) => (map[a[3]] || rest)(...a);
/** Blob patches in model space (spots, stripes): blobs [[x,y,z,r]], 'on' painter inside, 'off' outside. */
export const blobs = (list, on, off) => (x, y, z, ...r) => list.some(([bx, by, bz, rr]) => (x - bx) ** 2 + (y - by) ** 2 + (z - bz) ** 2 < rr * rr) ? on(x, y, z, ...r) : off(x, y, z, ...r);

/** Face details that never get the staircase rounding (they sit on the face; only the outer silhouette is rounded). */
const DETAIL = /muzzle|nose|snout|beak|cheek|mask|patch|spot|eye|brow|mouth|blush|tongue|whisker|goggle/i;
const cdist = (a, b) => { const p = hx(a), q = hx(b); return Math.abs(p[0] - q[0]) + Math.abs(p[1] - q[1]) + Math.abs(p[2] - q[2]); };
/** Texels that differ clearly from their face's main colour (eyes, patterns), with their world-space rectangle. */
function featureTexels(from, to, paint) {
  const [w, h, d] = [0, 1, 2].map(i => Math.max(1, Math.ceil(to[i] - from[i] - 1e-6))), [fx, fy, fz] = from, [tx, ty, tz] = to;
  const sx = (tx - fx) / w, sy = (ty - fy) / h, sz = (tz - fz) / d, out = [];
  const grid = { north: [w, h], south: [w, h], east: [d, h], west: [d, h], up: [w, d], down: [w, d] };
  for (const [f, [fw, fh]] of Object.entries(grid)) {
    const tex = [];
    for (let j = 0; j < fh; j++) for (let i = 0; i < fw; i++) {
      let lo, hi; // texel rectangle (min / max corner) on the face plane
      switch (f) {
        case "north": lo = [tx - (i + 1) * sx, ty - (j + 1) * sy, fz]; hi = [tx - i * sx, ty - j * sy, fz]; break;
        case "south": lo = [fx + i * sx, ty - (j + 1) * sy, tz]; hi = [fx + (i + 1) * sx, ty - j * sy, tz]; break;
        case "east": lo = [tx, ty - (j + 1) * sy, tz - (i + 1) * sz]; hi = [tx, ty - j * sy, tz - i * sz]; break;
        case "west": lo = [fx, ty - (j + 1) * sy, fz + i * sz]; hi = [fx, ty - j * sy, fz + (i + 1) * sz]; break;
        case "up": lo = [fx + i * sx, ty, fz + j * sz]; hi = [fx + (i + 1) * sx, ty, fz + (j + 1) * sz]; break;
        default: lo = [fx + i * sx, fy, tz - (j + 1) * sz]; hi = [fx + (i + 1) * sx, fy, tz - j * sz];
      }
      const c = lo.map((v, k) => (v + hi[k]) / 2);
      tex.push({ f, lo, hi, col: paint(c[0], c[1], c[2], f, i, j, fw, fh) });
    }
    let best = tex[0].col, bestN = -1; // main colour = the one most texels are close to (fur noise stays close)
    for (const a of tex) { const n = tex.reduce((k, b) => k + (cdist(a.col, b.col) < 100), 0); if (n > bestN) { bestN = n; best = a.col; } }
    for (const t of tex) if (cdist(t.col, best) >= 100) out.push(t);
  }
  return out;
}

export class Model {
  /** round: rounded big boxes (corner = voxelised sphere in 0.65 px steps) and top-light / bottom-shadow shading, for a softer blocky look. */
  constructor(name, T = 128, { round = false } = {}) { this.name = name; this.T = T; this.round = round; this.px = Buffer.alloc(T * T * 4); this.u = 0; this.v = 0; this.row = 0; this.elements = []; }
  set(u, v, c) { const o = (v * this.T + u) * 4, [r, g, b] = hx(c); this.px[o] = r; this.px[o + 1] = g; this.px[o + 2] = b; this.px[o + 3] = 255; }
  alloc(w, h) { if (this.u + w > this.T) { this.u = 0; this.v += this.row; this.row = 0; } const at = [this.u, this.v]; this.u += w; this.row = Math.max(this.row, h); if (this.v + h > this.T) throw new Error(this.name + ": texture full"); return at; }
  cube(name, from, to, paint, extra = {}) {
    if (this.round && !extra.raw) {
      const d = [0, 1, 2].map(i => to[i] - from[i]), min = Math.min(...d);
      if (min >= 2) {
        // Rounded box from overlapping axis-aligned boxes: the corners are a voxelised sphere of radius R in 0.5 px steps
        // (edges become a fine pixel staircase instead of one big notch). All sub-boxes share ONE texture block painted for the
        // full box, each face mapping to its own part of it, so patterns / eyes stay continuous and the texture does not grow.
        // Only the outer silhouette gets the fine staircase: the radius shrinks until no eye / pattern texel (a texel far from the
        // face's main colour) falls on a stepped-back part; face details (nose, muzzle, cheeks ...) and faces whose pattern reaches
        // the edge keep the old simple chamfer (one 0.5 / 1 px notch).
        const S = .65, Rmax = (min < 4 ? 2 : Math.max(2, Math.min(Math.floor(3 / S), Math.round(min * .3 / S)))) * S; // step 0.65 px (was 0.5)
        const sphere = R => {
          const n = Math.round(R / S), cells = [];
          for (let i = 0; i < n; i++) for (let j = 0; j < n; j++) for (let k = 0; k < n; k++) if (((i + .5) ** 2 + (j + .5) ** 2 + (k + .5) ** 2) * S * S <= R * R + 1e-6) cells.push([i, j, k]);
          return cells.filter(c => !cells.some(o => o !== c && o[0] >= c[0] && o[1] >= c[1] && o[2] >= c[2])).map(c => c.map(v => R - (v + 1) * S));
        };
        const feats = featureTexels(from, to, paint);
        const AX = { north: [2, 0, 1], south: [2, 0, 1], east: [0, 2, 1], west: [0, 2, 1], up: [1, 0, 2], down: [1, 0, 2] };
        const onFlat = (boxes, { f, lo, hi }) => { const [a, u, v] = AX[f]; return boxes.some(b => b[a] < 1e-6 && b[u] <= Math.min(lo[u] - from[u], to[u] - hi[u]) + 1e-6 && b[v] <= Math.min(lo[v] - from[v], to[v] - hi[v]) + 1e-6); };
        let top = null;
        if (!DETAIL.test(name)) for (let R = Rmax; R >= 2 * S - 1e-6 && !top; R -= S) { const b = sphere(R); if (feats.every(t => onFlat(b, t))) top = b; }
        if (!top) { const r = min >= 5 ? 1 : .5; top = [[0, r, r], [r, 0, r], [r, r, 0]]; }
        const shaded = (x, y, z, f, ...r) => { const c = paint(x, y, z, f, ...r); if (d[1] < 3) return c; const k = f === "up" ? .07 : f === "down" ? -.2 : -.13 + .18 * (y - from[1]) / d[1]; return shade(c, k); };
        const tex = this.cube("__tex", from, to, shaded, { ...extra, raw: true, texOnly: true });
        const [fx, fy, fz] = from, [tx, ty, tz] = to, [sx, sy, sz] = [0, 1, 2].map(i => d[i] / tex.W[i]);
        const ex = { ...extra, raw: true, origin: extra.origin || from.map((v, i) => (v + to[i]) / 2) };
        return top.map((c, q) => {
          const ins = c, A = from.map((v, i) => v + ins[i]), B = to.map((v, i) => v - ins[i]);
          const r3 = v => Math.round(v * 1000) / 1000, uv = (f, u1, v1, u2, v2) => { const [u0, v0] = tex.fc[f]; return { uv: [u0 + u1, v0 + v1, u0 + u2, v0 + v2].map(r3), texture: 0 }; };
          const faces = {
            north: uv("north", (tx - B[0]) / sx, (ty - B[1]) / sy, (tx - A[0]) / sx, (ty - A[1]) / sy),
            south: uv("south", (A[0] - fx) / sx, (ty - B[1]) / sy, (B[0] - fx) / sx, (ty - A[1]) / sy),
            east: uv("east", (tz - B[2]) / sz, (ty - B[1]) / sy, (tz - A[2]) / sz, (ty - A[1]) / sy),
            west: uv("west", (A[2] - fz) / sz, (ty - B[1]) / sy, (B[2] - fz) / sz, (ty - A[1]) / sy),
            up: uv("up", (A[0] - fx) / sx, (A[2] - fz) / sz, (B[0] - fx) / sx, (B[2] - fz) / sz),
            down: uv("down", (A[0] - fx) / sx, (tz - B[2]) / sz, (B[0] - fx) / sx, (tz - A[2]) / sz),
          };
          const el = { name: q ? name + "_r" + q : name, uuid: crypto.randomUUID(), type: "cube", from: A.map(r3), to: B.map(r3), origin: ex.origin, faces };
          if (extra.rotation) el.rotation = extra.rotation;
          this.elements.push(el);
          return el.uuid;
        });
      }
    }
    const [w, h, d] = [0, 1, 2].map(i => Math.max(1, Math.ceil(to[i] - from[i] - 1e-6)));
    const [u0, v0] = this.alloc(2 * d + 2 * w, d + h);
    const fc = { up: [u0 + d, v0, w, d], down: [u0 + d + w, v0, w, d], east: [u0, v0 + d, d, h], north: [u0 + d, v0 + d, w, h], west: [u0 + d + w, v0 + d, d, h], south: [u0 + 2 * d + w, v0 + d, w, h] };
    const pos = (f, i, j) => {
      const a = i + .5, b = j + .5, [fx, fy, fz] = from, [tx, ty, tz] = to, sx = (tx - fx) / w, sy = (ty - fy) / h, sz = (tz - fz) / d;
      switch (f) {
        case "north": return [tx - a * sx, ty - b * sy, fz];
        case "south": return [fx + a * sx, ty - b * sy, tz];
        case "east": return [tx, ty - b * sy, tz - a * sz];
        case "west": return [fx, ty - b * sy, fz + a * sz];
        case "up": return [fx + a * sx, ty, fz + b * sz];
        default: return [fx + a * sx, fy, tz - b * sz];
      }
    };
    const out = {};
    for (const [f, [u, v, fw, fh]] of Object.entries(fc)) {
      for (let j = 0; j < fh; j++) for (let i = 0; i < fw; i++) this.set(u + i, v + j, paint(...pos(f, i, j), f, i, j, fw, fh));
      out[f] = { uv: [u, v, u + fw, v + fh], texture: 0 };
    }
    if (extra.texOnly) return { fc, W: [w, h, d] };
    const el = { name, uuid: crypto.randomUUID(), type: "cube", from, to, origin: extra.origin || from.map((v, i) => (v + to[i]) / 2), faces: out };
    if (extra.rotation) el.rotation = extra.rotation;
    this.elements.push(el);
    return el.uuid;
  }
  G(name, origin, children, rotation = [0, 0, 0]) { return { name, uuid: crypto.randomUUID(), origin, rotation, visibility: true, children: children.flat(Infinity) }; }
  write(outliner, file) {
    fs.writeFileSync(file, JSON.stringify({
      meta: { format_version: "4.10", model_format: "free" }, name: this.name, box_uv: false, resolution: { width: this.T, height: this.T },
      elements: this.elements, outliner,
      textures: [{ path: "", name: this.name, folder: "", namespace: "", id: "0", particle: false, render_mode: "default", source: "data:image/png;base64," + writePng(this.px, this.T, this.T).toString("base64") }],
      animations: [],
    }));
    return this.elements.length;
  }
}
