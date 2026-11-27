// High-detail skill effects: rebuilds the lighter skill FX models with many more pieces (rounder octagons / 16-gons, bevels, glow
// layers, small ornaments), keeping each effect's size, facing and centre so the skills place them exactly as before.
//   node dev/art/make-fx-hq.mjs        (also run at the end of make-fx-models.mjs, so these always win)
//   -> pipi-facilities/assets/pipifacilities/{models,textures}/item/fx_<name>.{json,png}, previews in dev/art/preview/fx-hq/
// Conventions (same as make-fx-models): centre (8,8,8); "forward" = model -Z; rotations 0/±22.5/±45 on one axis; coords -16..32.
import fs from "node:fs";
import path from "node:path";
import zlib from "node:zlib";
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "../pipi-facilities/assets/pipifacilities");
const texDir = path.join(root, "textures/item"), modDir = path.join(root, "models/item");
const PV = path.join(here, "preview/fx-hq"); fs.mkdirSync(PV, { recursive: true });
const ONLY = process.argv.slice(2);

const crcT = new Uint32Array(256).map((_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const crc = b => { let c = 0xffffffff; for (const x of b) c = crcT[(c ^ x) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
function chunk(type, data) { const l = Buffer.alloc(4); l.writeUInt32BE(data.length); const td = Buffer.concat([Buffer.from(type), data]); const c = Buffer.alloc(4); c.writeUInt32BE(crc(td)); return Buffer.concat([l, td, c]); }
function png(px, w = 16, h = 16) {
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) { raw[y * (w * 4 + 1)] = 0; Buffer.from(px.buffer, y * w * 4, w * 4).copy(raw, y * (w * 4 + 1) + 1); }
  const ih = Buffer.alloc(13); ih.writeUInt32BE(w, 0); ih.writeUInt32BE(h, 4); ih[8] = 8; ih[9] = 6;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk("IHDR", ih), chunk("IDAT", zlib.deflateSync(raw)), chunk("IEND", Buffer.alloc(0))]);
}
let seed = 12345;
const rnd = () => (seed = (seed * 1664525 + 1013904223) >>> 0) / 4294967296;
const hex = h => [1, 3, 5].map(i => parseInt(h.slice(i, i + 2), 16));
/** palette entry: "#rrggbb" | {c, a, noise, speck:[hex, prob]} ; 16 cells of 4x4 px. */
function paletteTexture(pal) {
  const px = new Uint8Array(16 * 16 * 4);
  pal.forEach((e, i) => {
    const o = typeof e === "string" ? { c: e } : e, base = hex(o.c), a = o.a ?? 255, cx = (i % 4) * 4, cy = Math.floor(i / 4) * 4;
    for (let y = 0; y < 4; y++) for (let x = 0; x < 4; x++) {
      let c = base.map(v => Math.max(0, Math.min(255, Math.round(v + (rnd() - .5) * 2 * (o.noise ?? 0)))));
      if (o.speck && rnd() < o.speck[1]) c = hex(o.speck[0]);
      const k = ((cy + y) * 16 + cx + x) * 4; px[k] = c[0]; px[k + 1] = c[1]; px[k + 2] = c[2]; px[k + 3] = a;
    }
  });
  return png(px);
}
const ANG = [-45, -22.5, 0, 22.5, 45];
const snap = a => ANG.reduce((b, v) => Math.abs(v - a) < Math.abs(b - a) ? v : b, 0);
class Model {
  constructor(name, pal, display) { this.name = name; this.pal = pal; this.els = []; this.display = display; }
  box(from, to, ci, rot) {
    if (ci >= this.pal.length) throw new Error(this.name + ": no colour " + ci);
    const cl = v => v < -16 && v > -16.1 ? -16 : v > 32 && v < 32.1 ? 32 : v;   // overlap slivers at the limits
    const f = from.map((v, i) => cl(Math.min(v, to[i]))), t = from.map((v, i) => cl(Math.max(v, to[i])));
    for (let i = 0; i < 3; i++) { if (t[i] - f[i] < .25) { const m = (f[i] + t[i]) / 2; f[i] = m - .125; t[i] = m + .125; } }
    for (const v of [...f, ...t]) if (!(v >= -16 && v <= 32)) throw new Error(this.name + ": out of range " + f + " / " + t);
    const col = ci % 4 * 4, row = Math.floor(ci / 4) * 4, face = { uv: [col, row, col + 4, row + 4], texture: "#0" };
    const el = { from: f.map(v => +v.toFixed(3)), to: t.map(v => +v.toFixed(3)), shade: false, faces: { north: face, east: face, south: face, west: face, up: face, down: face } };
    if (rot && rot.angle) {
      if (!ANG.includes(rot.angle)) throw new Error(this.name + ": bad angle " + rot.angle);
      el.rotation = { angle: rot.angle, axis: rot.axis, origin: (rot.origin ?? f.map((v, i) => (v + t[i]) / 2)).map(v => +v.toFixed(3)) };
    }
    this.els.push(el);
  }
  /** box centred at c, long axis along angle a (deg) inside plane "xz" (about y) or "xy" (about z); s = [along, across, third] */
  tan(plane, c, s, a, ci) {
    let ang = ((a % 180) + 180) % 180; if (ang > 90) ang -= 180;
    let swap = false;
    if (Math.abs(ang) > 45) { swap = true; ang -= 90 * Math.sign(ang); }
    ang = Math.round(ang / 22.5) * 22.5;
    const [L, W, T] = s;
    if (plane === "xz") { const d = swap ? [W, T, L] : [L, T, W]; this.box([c[0] - d[0] / 2, c[1] - d[1] / 2, c[2] - d[2] / 2], [c[0] + d[0] / 2, c[1] + d[1] / 2, c[2] + d[2] / 2], ci, { axis: "y", angle: -ang }); }
    else { const d = swap ? [W, L, T] : [L, W, T]; this.box([c[0] - d[0] / 2, c[1] - d[1] / 2, c[2] - d[2] / 2], [c[0] + d[0] / 2, c[1] + d[1] / 2, c[2] + d[2] / 2], ci, { axis: "z", angle: ang }); }
  }
  write() {
    if (ONLY.length && !ONLY.includes(this.name)) return;
    if (this.els.length > 400) throw new Error(this.name + " too many elements " + this.els.length);
    const tex = "pipifacilities:item/fx_" + this.name, id = { rotation: [0, 0, 0], translation: [0, 0, 0], scale: [1, 1, 1] };
    const file = path.join(modDir, `fx_${this.name}.json`);
    fs.writeFileSync(file, JSON.stringify({ textures: { "0": tex, particle: tex }, elements: this.els,
      display: this.display ?? { none: id, fixed: id, gui: { rotation: [30, 45, 0], scale: [.4, .4, .4] } } }));
    fs.writeFileSync(path.join(texDir, `fx_${this.name}.png`), paletteTexture(this.pal));
    execFileSync("node", [path.join(here, "preview-station.mjs"), "--model", file, "--out", path.join(PV, this.name + ".png"), "--size", "300"]);
    console.log(`fx_${this.name}: ${this.els.length} elements`);
  }
}
const D = Math.PI / 180;
const sq = (c, h) => [[c[0] - h, c[1] - h, c[2] - h], [c[0] + h, c[1] + h, c[2] + h]];
const DISP_FLAT = (gui) => ({ fixed: { rotation: [0, 0, 0], translation: [0, 0, 0], scale: [1, 1, 1] }, gui: { rotation: [0, 0, 0], translation: [0, 0, 0], scale: [gui, gui, gui] } });

// ---------------------------------------------------------------- HQ shapes
const TURNS = { 2: [0, 45], 4: [0, 45, 22.5, -22.5] };
/** prism along an axis: n = 2 (octagon) or 4 (16-gon). y: c = [x, z]; z: c = [x, y]; x: c = [y, z]. cols cycle over the turned copies. */
function prism(m, axis, c, lo, hi, r, cols, n = 2) {
  const cs = Array.isArray(cols) ? cols : [cols];
  TURNS[n].forEach((a, k) => {
    const ci = cs[k % cs.length];
    if (axis === "y") m.box([c[0] - r, lo, c[1] - r], [c[0] + r, hi, c[1] + r], ci, { axis: "y", angle: a, origin: [c[0], (lo + hi) / 2, c[1]] });
    else if (axis === "z") m.box([c[0] - r, c[1] - r, lo], [c[0] + r, c[1] + r, hi], ci, { axis: "z", angle: a, origin: [c[0], c[1], (lo + hi) / 2] });
    else m.box([lo, c[0] - r, c[1] - r], [hi, c[0] + r, c[1] + r], ci, { axis: "x", angle: a, origin: [(lo + hi) / 2, c[0], c[1]] });
  });
}
/** round ball: `layers` slices along y, each a prism; cols cycle per slice */
function ball(m, c, r, cols, layers = 6, n = 4) {
  const cs = Array.isArray(cols) ? cols : [cols];
  for (let i = 0; i < layers; i++) {
    const t0 = -1 + 2 * i / layers, t1 = -1 + 2 * (i + 1) / layers, tm = (t0 + t1) / 2;
    prism(m, "y", [c[0], c[2]], c[1] + r * t0 - .02, c[1] + r * t1 + .02, Math.max(.15, r * Math.sqrt(1 - tm * tm)), [cs[i % cs.length], cs[(i + 1) % cs.length]], n);
  }
}
/** a left/right mirrored pair about x = 8 (z-rotations flip) */
function pair(m, from, to, ci, rot) {
  m.box(from, to, ci, rot);
  m.box([16 - to[0], from[1], from[2]], [16 - from[0], to[1], to[2]], ci,
    rot && { axis: rot.axis, angle: rot.axis === "x" ? rot.angle : -rot.angle, origin: rot.origin && [16 - rot.origin[0], rot.origin[1], rot.origin[2]] });
}
/** gem facing +-z: diamond + square */
function gem(m, x, y, z0, z1, s, a, b) {
  m.box([x - s, y - s, z0], [x + s, y + s, z1], a, { axis: "z", angle: 45, origin: [x, y, (z0 + z1) / 2] });
  m.box([x - s * .55, y - s * .55, z0 - .08], [x + s * .55, y + s * .55, z1 + .08], b);
}
/** curved chain in the YZ plane (rotation about X), pts = [[y, z], ...] (as make-fx-models' limb) */
const SGN = 1;
function limb(m, pts, th, wd, ci, x = 8) {
  for (let i = 0; i < pts.length - 1; i++) {
    const [y0, z0] = pts[i], [y1, z1] = pts[i + 1], dy = y1 - y0, dz = z1 - z0;
    const len = Math.hypot(dy, dz) * 1.2, q = Math.max(-45, Math.min(45, Math.round(Math.atan2(dz, dy) / D / 22.5) * 22.5));
    const cy = (y0 + y1) / 2, cz = (z0 + z1) / 2;
    m.box([x - wd / 2, cy - len / 2, cz - th / 2], [x + wd / 2, cy + len / 2, cz + th / 2], ci, q ? { axis: "x", angle: SGN * q, origin: [x, cy, cz] } : null);
  }
}
/** a straight segment P->Q in the XY plane (z = 8) as a vertical box turned about z, and (twin) its copy in the ZY plane at z = 16 - x */
function bolt(m, P, Q, w, ci, twin = true, d = .0) {
  const dx = Q[0] - P[0], dy = Q[1] - P[1], len = Math.hypot(dx, dy) + .5, a = snap(-Math.atan2(dx, dy) / D);
  const cx = (P[0] + Q[0]) / 2, cy = (P[1] + Q[1]) / 2;
  m.box([cx - w / 2, cy - len / 2, 8 - w / 2 - d], [cx + w / 2, cy + len / 2, 8 + w / 2 + d], ci, a ? { axis: "z", angle: a, origin: [cx, cy, 8] } : null);
  if (twin) { const cz = 16 - cx; m.box([8 - w / 2 - d, cy - len / 2, cz - w / 2], [8 + w / 2 + d, cy + len / 2, cz + w / 2], ci, a ? { axis: "x", angle: a, origin: [8, cy, cz] } : null); }
}

// ================================================================ 대검 소환 (giant sword, tip down, grip up to y 32)
{
  const m = new Model("giant_sword", ["#ffffff", "#bff4ff", "#4fd0ff", "#e8b84a", "#7a5030", "#4a3020", { c: "#6fe0ff", a: 90 }, "#fff6c8", "#2a86c8", "#9a6a1c", { c: "#bff4ff", a: 55 }, "#fff0a8"]);
  const H = 1.3;
  m.box([8 - H, -11, 7.25], [8 + H, 21, 8.75], 1);                                        // blade body
  pair(m, [8 + H - .75, -11, 7.35], [8 + H - .3, 21, 8.65], 2);                            // glowing bevels
  pair(m, [8 + H - .32, -11, 7.5], [8 + H + .12, 21, 8.5], 0);                             // white edges
  m.box([7.6, -9, 7.15], [8.4, 19.5, 8.85], 8);                                            // fuller
  m.box([7.85, -9.5, 7.1], [8.15, 20, 8.9], 0);
  for (const y of [-4, 2, 8, 14]) gem(m, 8, y, 7.05, 8.95, .55, 0, 2);                    // runes
  for (let i = 0; i < 6; i++) {                                                            // tapering point (down)
    const t = (i + 1) / 6, h = Math.max(.22, H * Math.pow(1 - t * .97, .8)), ya = -11 - 5 * t, yb = -11 - 5 * i / 6 + .02;
    m.box([8 - h, ya, 7.3], [8 + h, yb, 8.7], 1);
    if (h > .45) pair(m, [8 + h - .32, ya, 7.45], [8 + h + .1, yb, 8.55], 0);
  }
  m.box([7.65, -16, 7.55], [8.35, -15.3, 8.45], 0, { axis: "z", angle: 45 });
  m.box([5.9, -12, 6.6], [10.1, 21, 9.4], 6); m.box([5.2, -14, 6.1], [10.8, 20.5, 9.9], 10);   // auras
  m.box([6.5, 19.5, 7.1], [9.5, 21, 8.9], 9);                                              // ricasso
  m.box([1.8, 21, 7], [14.2, 22.6, 9], 3);                                                  // guard
  m.box([2, 22.3, 7.2], [14, 22.9, 8.8], 7); m.box([2.2, 20.7, 7.3], [13.8, 21.1, 8.7], 9);
  pair(m, [13.6, 18.6, 7.25], [14.8, 21.4, 8.75], 3, { axis: "z", angle: 22.5, origin: [14.2, 21, 8] });   // quillons curl down
  for (const x of [15.1, .9]) prism(m, "y", [x, 8], 18.1, 19.3, .6, [11, 3]);
  m.box([6.3, 20.6, 6.6], [9.7, 23, 9.4], 3);
  gem(m, 8, 21.8, 6.4, 9.6, .75, 2, 0);
  prism(m, "y", [8, 8], 23, 29.6, .85, [4, 5]);                                           // grip
  for (let i = 0; i < 5; i++) { const y = 23.4 + i * 1.25; prism(m, "y", [8, 8], y, y + .38, .98, 5); }
  for (const y of [22.9, 29.3]) prism(m, "y", [8, 8], y, y + .45, 1.08, [3, 9]);
  prism(m, "y", [8, 8], 29.7, 31, 1.3, [3, 9, 3, 9], 4);                                  // pommel
  prism(m, "y", [8, 8], 30.9, 32, .8, 11);
  gem(m, 8, 30.35, 6.6, 9.4, .5, 2, 0);
  m.write();
}

// ================================================================ big arrow (points to -Z)
{
  const m = new Model("arrow_big", ["#ffffff", "#c9f7ff", "#5ad8ff", "#fff0b0", "#d9a050", "#ff5a4a", "#2a8fd0", { c: "#8ff0ff", a: 80 }, "#a06a30", "#c03a30", "#e8fbff"]);
  for (const [z0, z1, r, ci] of [[-16, -14.6, .45, 0], [-14.6, -13, 1, 1], [-13, -11.2, 1.6, 2], [-11.2, -9, 2.2, 1], [-9, -6.8, 2.7, 2], [-6.8, -5, 2.3, 6]])
    prism(m, "z", [8, 8], z0, z1 + .02, r, [ci, ci === 1 ? 2 : 1], 4);
  for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {                                  // white ridges + barbs
    if (dx) { m.box([8 + dx * 2.2 - .2, 7.8, -12], [8 + dx * 2.2 + .2, 8.2, -5.5], 10); m.box([8 + dx * 2.9 - .35, 7.7, -6.6], [8 + dx * 2.9 + .35, 8.3, -2.4], 2, { axis: "y", angle: dx * 22.5, origin: [8 + dx * 2.9, 8, -6.6] }); }
    else { m.box([7.8, 8 + dy * 2.2 - .2, -12], [8.2, 8 + dy * 2.2 + .2, -5.5], 10); m.box([7.7, 8 + dy * 2.9 - .35, -6.6], [8.3, 8 + dy * 2.9 + .35, -2.4], 2, { axis: "x", angle: -dy * 22.5, origin: [8, 8 + dy * 2.9, -6.6] }); }
  }
  prism(m, "z", [8, 8], -5, -2.6, 1.2, [8, 4]); prism(m, "z", [8, 8], -3.2, -2.6, 1.38, 3);
  prism(m, "z", [8, 8], -16, -2, 3.4, 7);                                                     // head glow
  prism(m, "z", [8, 8], -2.6, 27, .85, [4, 8]);                                               // shaft
  m.box([7.7, 8.55, -2.6], [8.3, 8.95, 27], 3);
  for (const z of [2, 9, 16]) prism(m, "z", [8, 8], z, z + .9, 1.05, 3);
  for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {                                  // fletching: three feather layers per vane
    const v = (a, b, z0, z1, ci) => dx ? m.box([Math.min(8 + dx * a, 8 + dx * b), 7.65, z0], [Math.max(8 + dx * a, 8 + dx * b), 8.35, z1], ci)
      : m.box([7.65, Math.min(8 + dy * a, 8 + dy * b), z0], [8.35, Math.max(8 + dy * a, 8 + dy * b), z1], ci);
    v(.8, 1.5, 19, 29.4, 9); v(1.4, 3.4, 19.5, 29, 5); v(3.3, 4.6, 20.5, 27.6, 5); v(4.5, 5.3, 21.5, 25.5, 0);
  }
  for (const a of [45, -45]) m.box([7.75, 3.6, 20.5], [8.25, 12.4, 28], 3, { axis: "z", angle: a, origin: [8, 8, 24] });   // thin diagonal vanes
  prism(m, "z", [8, 8], 28.6, 30.6, 1.1, 3); m.box([7.75, 6.8, 30.1], [8.25, 9.2, 30.9], 8);
  m.write();
}

// ================================================================ magic orb (purple)
{
  const m = new Model("orb", ["#ffffff", "#e9c8ff", "#b86bff", "#7a2fd6", { c: "#4b1a8c", a: 225 }, "#ff9cf0", { c: "#c58bff", a: 70 }, "#f6e4ff"]);
  ball(m, [8, 8, 8], 6.2, [4], 7, 4);
  ball(m, [8, 8, 8], 4.7, [3, 2], 5, 4);
  ball(m, [8, 8, 8], 2.6, [1, 0], 3, 2);
  for (let k = 0; k < 16; k++) { const a = k * 22.5 * D; m.tan("xz", [8 + 7.6 * Math.cos(a), 8 + (k % 2 ? .4 : -.4), 8 + 7.6 * Math.sin(a)], [2.6, .5, .5], k * 22.5 + 90, k % 4 ? 5 : 0); }
  for (let k = 0; k < 12; k++) { const a = k * 30 * D; m.tan("xy", [8 + 8.4 * Math.cos(a), 8 + 8.4 * Math.sin(a), 8], [2.4, .45, .45], k * 30 + 90, k % 3 ? 1 : 7); }
  for (const [x, y, z] of [[8, 8, -.5], [8, 8, 16.5], [-.5, 8, 8], [16.5, 8, 8], [8, -.5, 8], [8, 16.5, 8]]) { m.box(...sq([x, y, z], 1.1), 5, { axis: "y", angle: 45 }); m.box(...sq([x, y, z], .7), 0, { axis: "x", angle: 45 }); }
  ball(m, [8, 8, 8], 8.8, [6], 3, 2);
  m.write();
}

// ================================================================ ice lance / mana lance (point at -Z)
function lance(name, pal) {
  const m = new Model(name, pal);
  const zs = [-16, -14.5, -12.5, -10, -7, -3, 2, 8, 14, 20, 24, 27], rad = z => z < -3 ? .35 + 2.35 * Math.pow((z + 16) / 13, .8) : z < 20 ? 2.75 : 2.75 - .25 * (z - 20) / 2.3;
  for (let i = 0; i < zs.length - 1; i++) prism(m, "z", [8, 8], zs[i] - .02, zs[i + 1] + .02, rad((zs[i] + zs[i + 1]) / 2), [0, 3, 0, 3], 4);
  prism(m, "z", [8, 8], -13.5, 26, .6, 2);                                                      // bright core
  for (const z of [-3, 8, 20]) prism(m, "z", [8, 8], z, z + .6, 2.95, 6);                      // frost bands
  [[1, 0], [0, 1], [-1, 0], [0, -1]].forEach(([dx, dy], k) => {                                 // swept fins, spiralling
    const z = 3 + k * 5;
    for (const [r0, r1, l, ci] of [[2.3, 4.4, 4.6, 3], [4.3, 6.1, 3.4, 4], [6, 7.3, 2, 1]]) {
      const zc = z + (r0 - 2.3) * .8;
      if (dx) m.box([Math.min(8 + dx * r0, 8 + dx * r1), 7.6, zc], [Math.max(8 + dx * r0, 8 + dx * r1), 8.4, zc + l], ci, { axis: "y", angle: dx * 22.5, origin: [8 + dx * r0, 8, zc] });
      else m.box([7.6, Math.min(8 + dy * r0, 8 + dy * r1), zc], [8.4, Math.max(8 + dy * r0, 8 + dy * r1), zc + l], ci, { axis: "x", angle: -dy * 22.5, origin: [8, 8 + dy * r0, zc] });
    }
  });
  for (let i = 0; i < 16; i++) { const t = i / 15, z = -7 + t * 29, a = t * 4 * Math.PI, r = 3.6; m.box(...sq([8 + r * Math.cos(a), 8 + r * Math.sin(a), z], .3), 6, { axis: "z", angle: 45 }); }   // frost helix
  prism(m, "z", [8, 8], 23.6, 26.4, 3.4, [3, 4]); prism(m, "z", [8, 8], 26.4, 30, 2.2, [1, 0]);
  for (const [dx, dy] of [[1, 1], [-1, 1], [1, -1], [-1, -1]]) {                                 // tail shards splaying back
    const x = 8 + dx * 2.2, y = 8 + dy * 2.2;
    m.box([x - .5, y - .5, 26], [x + .5, y + .5, 31], 1, { axis: "y", angle: dx * 22.5, origin: [x, y, 26] });
    m.box([x - .25, y - .25, 29.5], [x + .25, y + .25, 31.6], 2, { axis: "y", angle: dx * 22.5, origin: [x, y, 26] });
  }
  prism(m, "z", [8, 8], -14, 27, 3.7, 5);                                                       // aura
  m.write();
}
lance("ice_lance", [{ c: "#c8f0ff", a: 215 }, { c: "#eefcff", a: 235 }, "#ffffff", { c: "#7cc8f0", a: 205 }, { c: "#4a98d0", a: 200 }, { c: "#bfeaff", a: 60 }, "#e8fbff"]);
lance("mana_lance", [{ c: "#d2b0ff", a: 215 }, { c: "#f1e4ff", a: 235 }, "#ffffff", { c: "#a674f0", a: 205 }, { c: "#7442cc", a: 200 }, { c: "#d9b8ff", a: 60 }, "#f6edff"]);

// ================================================================ meteor (rock at (8,6,8), flames trailing up +Y)
{
  const m = new Model("meteor", [{ c: "#3a2c28", noise: 10, speck: ["#ff8a2a", .12] }, { c: "#574036", noise: 12, speck: ["#ff6a1a", .1] }, { c: "#7a5a46", noise: 12 },
    "#ff3b14", "#ff8a1f", "#ffd23a", "#fff4b0", { c: "#ff8a1f", a: 200 }, { c: "#ffd23a", a: 170 }, { c: "#ff5a14", a: 90 }, "#2a1e1a"]);
  seed = 5;
  ball(m, [8, 6, 8], 6, [0, 1, 2, 1], 8, 4);
  for (let i = 0; i < 12; i++) {                                                                 // lumps + lava cracks on the surface
    const th = rnd() * 2 * Math.PI, ph = (rnd() - .35) * Math.PI * .8, r = 5.6;
    const p = [8 + r * Math.cos(ph) * Math.cos(th), 6 + r * Math.sin(ph), 8 + r * Math.cos(ph) * Math.sin(th)];
    m.box(...sq(p, .7 + rnd() * .5), [0, 1, 10][i % 3], { axis: ["x", "y", "z"][i % 3], angle: [22.5, 45, -22.5][i % 3] });
    const q = [8 + 6.05 * Math.cos(ph) * Math.cos(th + .4), 6 + 6.05 * Math.sin(ph + .2), 8 + 6.05 * Math.cos(ph) * Math.sin(th + .4)];
    m.box([q[0] - 1.3, q[1] - .18, q[2] - .18], [q[0] + 1.3, q[1] + .18, q[2] + .18], i % 2 ? 3 : 4, { axis: "y", angle: [22.5, -45, 45, -22.5][i % 4] });
  }
  for (const [y0, y1, r, ci] of [[9.5, 14, 4.8, 7], [14, 19, 4, 7], [19, 24, 3, 8], [24, 29, 2, 8], [29, 32, 1, 6]]) prism(m, "y", [8, 8], y0, y1, r, ci, 4);
  for (const [y0, y1, r, ci] of [[10, 17, 2.4, 5], [17, 23, 1.7, 5], [23, 28, 1.1, 6]]) prism(m, "y", [8, 8], y0, y1, r, ci);
  for (let k = 0; k < 8; k++) {                                                                  // flame tongues around the plume
    const a = k * 45, ar = a * D, x = 8 + 4.3 * Math.cos(ar), z = 8 + 4.3 * Math.sin(ar), off = (k % 2) * 2;
    for (const [y0, y1, w, ci] of [[9 + off, 15 + off, 2, 3], [15 + off, 21 + off, 1.5, 4], [21 + off, 25.5 + off, .9, 5]]) {
      const rot = k % 2 ? { axis: "y", angle: 45, origin: [x, (y0 + y1) / 2, z] }
        : Math.abs(Math.cos(ar)) > .5 ? { axis: "z", angle: -Math.sign(Math.cos(ar)) * 22.5, origin: [x, 9, z] } : { axis: "x", angle: Math.sign(Math.sin(ar)) * 22.5, origin: [x, 9, z] };
      m.box([x - w / 2, y0, z - w / 2], [x + w / 2, Math.min(32, y1), z + w / 2], ci, rot);
    }
  }
  for (let i = 0; i < 12; i++) { const a = rnd() * 2 * Math.PI, r = 3 + rnd() * 4.5, y = 12 + rnd() * 19; m.box(...sq([8 + r * Math.cos(a), y, 8 + r * Math.sin(a)], .25 + rnd() * .25), i % 2 ? 5 : 6, { axis: "y", angle: 45 }); }   // embers
  ball(m, [8, 6, 8], 7.4, [9], 3, 2);                                                            // heat glow
  m.write();
}

// ================================================================ heal cross (3D, arms -4..20)
{
  const m = new Model("heal_cross", ["#ffffff", "#fff6c8", "#b8fff0", { c: "#7cf0d8", a: 200 }, "#ffe27a", "#e8fffb", "#d4a93a", { c: "#b8fff0", a: 70 }]);
  for (const ax of ["x", "y", "z"]) {
    const c = [8, 8];
    prism(m, ax, c, -4, 20, 2.4, 3);
    prism(m, ax, c, -3.2, 19.2, 1.7, 2);
    prism(m, ax, c, -4.4, 20.4, 1, [0, 5]);
    for (const p of [-1.5, 17.5]) prism(m, ax, c, p - .4, p + .4, 2.65, 4);                    // gold rings near the ends
  }
  for (const p of [[8, 21.6, 8], [8, -5.6, 8], [21.6, 8, 8], [-5.6, 8, 8], [8, 8, 21.6], [8, 8, -5.6]]) { ball(m, p, 1.8, [4, 6], 3, 2); m.box(...sq(p, .8), 0, { axis: "y", angle: 45 }); }
  ball(m, [8, 8, 8], 3.8, [1, 2, 5], 5, 4);
  ball(m, [8, 8, 8], 2.1, [0], 3, 2);
  for (let k = 0; k < 12; k++) { const a = k * 30 * D; m.box(...sq([8 + 12 * Math.cos(a), 8 + (k % 2 ? 1.2 : -1.2), 8 + 12 * Math.sin(a)], k % 3 ? .45 : .7), k % 3 ? 4 : 0, { axis: "y", angle: 45 }); }
  ball(m, [8, 8, 8], 6, [7], 3, 2);
  m.write();
}

// ================================================================ shockwave spikes (row along x, base y 6)
{
  const noisy = (c, n = 14) => ({ c, noise: n });
  const m = new Model("shockwave", [noisy("#5a5650"), noisy("#8d887e"), noisy("#c9c2b2"), "#fff3c0", "#ffd36a", noisy("#3c3935"), { c: "#ffd36a", a: 70 }, "#ff9a3a"]);
  seed = 7;
  const spike = (x, z, h, wd, lean, cols) => {
    const o = [x, 6, z], tiers = [[0, .32, 1], [.32, .56, .74], [.56, .76, .52], [.76, .9, .32], [.9, 1, .14]];
    tiers.forEach(([a, b, k], i) => {
      const w = wd * k, y0 = 6 + h * a, y1 = 6 + h * b + .05;
      if (lean) m.box([x - w, y0, z - w * .8], [x + w, y1, z + w * .8], cols[i], { axis: "z", angle: lean, origin: o });
      else prism(m, "y", [x, z], y0, y1, w, [cols[i], cols[Math.min(i + 1, 4)]]);
    });
  };
  for (let i = 0; i < 15; i++) {
    const x = -13 + i * 3, mid = 1 - Math.abs(x - 8) / 22, h = 8 + mid * 15 + rnd() * 3, z = 8 + (rnd() - .5) * 5;
    spike(x, z, h, 2.4 + mid * 2, [-22.5, 0, 22.5, 0, -22.5][i % 5], i % 2 ? [5, 0, 1, 2, 3] : [0, 1, 1, 2, 3]);
  }
  for (let i = 0; i < 10; i++) { const x = -12 + i * 4.4, z = 8 + (i % 2 ? 5.5 : -5.5); spike(x, z, 4 + rnd() * 3, 1.3, 0, [5, 0, 1, 2, 2]); }
  for (let i = 0; i < 12; i++) { const x = -12 + rnd() * 40, y = 17 + rnd() * 11, z = 8 + (rnd() - .5) * 9; m.box(...sq([x, y, z], .45 + rnd() * .6), [0, 1, 5, 2][i % 4], { axis: ["x", "y", "z"][i % 3], angle: [22.5, 45, -22.5][i % 3] }); }
  m.box([-15, 5.4, 7.2], [31, 6.4, 8.8], 4); m.box([-15, 5.6, 7.6], [31, 6.6, 8.4], 3);           // glowing crack
  for (let i = 0; i < 12; i++) { const x = -12 + i * 3.7, s = i % 2 ? 1 : -1; m.tan("xz", [x, 5.9, 8 + s * 2.2], [4.2, .45, .7], s * (i % 3 ? 45 : 22.5), i % 2 ? 4 : 7); }
  m.box([-15, 5.2, 4], [31, 9, 12], 6);                                                          // dust glow
  m.write();
}

// ================================================================ mana hammer (handle +Y, head at the bottom faces along ±X)
{
  const m = new Model("mana_hammer", ["#ffffff", "#f0d8ff", "#b86bff", "#7a2fd6", { c: "#c58bff", a: 90 }, "#ff9cf0", "#3a2060", "#ffe27a", "#5a3a96", "#b88a2a"]);
  const hy = -1;
  m.box([-1, hy - 3, 1], [17, hy + 9, 15], 3);
  m.box([-1, hy - 3, 1], [17, hy - 1.5, 15], 8); m.box([-1, hy + 7.5, 1], [17, hy + 9, 15], 8);
  for (const s of [-1, 1]) {
    const lo = s < 0, ex = lo ? -1 : 17, bx = len => lo ? [ex - len, ex] : [ex, ex + len];
    m.box([bx(3.5)[0], hy - 3.6, .2], [bx(3.5)[1], hy + 9.6, 15.8], 6);
    m.box([bx(4.4)[0], hy - 1, 3], [bx(4.4)[1], hy + 7, 13], 2);
    m.box([bx(5)[0], hy + 1, 5], [bx(5)[1], hy + 5, 11], 1);
    m.box([bx(5.4)[0], hy + 2, 6.5], [bx(5.4)[1], hy + 4, 9.5], 0);
    for (const y of [hy - 3.6, hy + 9.1]) for (const z of [.2, 15.3]) m.box([bx(3.7)[0], y, z], [bx(3.7)[1], y + .5, z + .5], 7);   // cap rims
    m.box([bx(.8)[0] + s * 3.5, hy + 2.4, 7.4], [bx(.8)[1] + s * 3.5, hy + 3.6, 8.6], 7, { axis: "x", angle: 45 });                   // face stud
  }
  m.box([2.5, hy - 2.5, 0], [13.5, hy + 8.5, 16], 2);
  for (const x of [2.5, 13.5]) m.box([x - .3, hy - 2.7, -.2], [x + .3, hy + 8.7, 16.2], 7);
  for (const z of [-.4, 15.2]) for (const x of [4.5, 8, 11.5]) gem(m, x, hy + 3, z, z + 1.2, x === 8 ? 1.4 : .8, 5, 0);   // runes on both faces
  for (const [x, y, z] of [[-1, hy - 3, 1], [17, hy - 3, 1], [-1, hy + 9, 1], [17, hy + 9, 1], [-1, hy - 3, 15], [17, hy - 3, 15], [-1, hy + 9, 15], [17, hy + 9, 15]]) m.box(...sq([x, y, z], .6), 7, { axis: "y", angle: 45 });
  prism(m, "y", [8, 8], 8, 29, 1.3, [8, 6]);
  for (let y = 10; y < 28; y += 3.4) prism(m, "y", [8, 8], y, y + 1.2, 1.7, [6, 9]);
  prism(m, "y", [8, 8], 17, 22, 2, [7, 9]);
  for (let y = 11.6; y < 27; y += 3.4) gem(m, 8, y + .1, 6.6, 9.4, .45, 5, 0);
  ball(m, [8, 30.2, 8], 1.8, [7, 5], 4, 4);
  prism(m, "y", [8, 8], 7.6, 8.6, 2.3, [9, 7]);
  for (const [x, y, z] of [[-2, 16, 8], [18, 20, 8], [8, 14, 0], [8, 12, 16], [-3, 6, 3], [19, 2, 13], [4, -7, 17], [12, 13, -1]]) { m.box(...sq([x, y, z], 1.2), 5, { axis: "y", angle: 45 }); m.box(...sq([x, y, z], .8), 0, { axis: "x", angle: 45 }); }
  for (const [x, y, a] of [[-3, 10, 45], [19, 0, -45], [-2.5, -2, -22.5], [18.5, 12, 22.5]]) m.box([x - .25, y - 1.6, 7.75], [x + .25, y + 1.6, 8.25], 1, { axis: "z", angle: a });   // crackle
  m.box([-4, hy - 4.5, -1.5], [20, hy + 10.5, 17.5], 4);
  m.write();
}

// ================================================================ spirit wolf (head at -Z, running)
{
  const m = new Model("spirit_wolf", [{ c: "#d8f4ff", a: 205 }, { c: "#9fdcff", a: 195 }, { c: "#5cb4f5", a: 190 }, { c: "#3a86e0", a: 185 }, { c: "#e8f8ff", a: 90 }, "#ffffff", { c: "#bfeaff", a: 140 }, "#7af0ff"]);
  const B = (a, b, ci, r) => m.box(a, b, ci, r);
  B([6.2, 5.5, 8], [9.8, 11.5, 19], 1); B([6.6, 6, 6], [9.4, 11.5, 11], 0); B([6.4, 11, 7.5], [9.6, 12.8, 17], 2); B([6.2, 5.5, 17], [9.8, 11, 21], 1);
  B([5.9, 6.2, 9], [10.1, 10.8, 16], 0);                                                       // rounder flanks
  B([6.6, 9, 3], [9.4, 14, 8.5], 2, { axis: "x", angle: -22.5 * SGN });
  for (let i = 0; i < 6; i++) B([5.4 + (i % 2) * .4, 9 + i * .35, 3 + i * 1.2], [10.6 - (i % 2) * .4, 13.9 + i * .35, 5 + i * 1.2], i % 2 ? 0 : 1, { axis: "y", angle: i % 2 ? 22.5 : -22.5 });   // mane
  for (let z = 8; z <= 18; z += 2) B([7.2, 12.4, z], [8.8, 13.8, z + 1.8], z % 4 ? 1 : 0, { axis: "x", angle: -22.5 * SGN });     // back tufts
  for (let i = 0; i < 3; i++) B([6.8, 4.6, 5 + i * 1.8], [9.2, 6.4, 7 + i * 1.8], 0, { axis: "x", angle: 22.5 * SGN });           // chest fluff
  B([6, 8.5, -3], [10, 14, 3.5], 0);
  pair(m, [9.6, 8.6, -.5], [10.6, 11.6, 3], 0, { axis: "y", angle: 22.5 });                    // cheek fluff
  B([6.8, 8.7, -9], [9.2, 11.8, -3], 6); B([7.1, 11.4, -7.5], [8.9, 12.4, -3], 1);             // snout + bridge
  B([7, 7.6, -8.4], [9, 8.9, -3.6], 3); B([7.4, 11.4, -10], [8.6, 12.4, -8.6], 3);
  for (const s of [-1, 1]) {
    B([8 + s * 2.4 - .8, 11.2, -2.2], [8 + s * 2.4 + .8, 12.4, -1], 7); B([8 + s * 2.4 - .35, 11.5, -2.35], [8 + s * 2.4 + .35, 12.1, -2.1], 5);
    B([8 + s * 2.4 - 1.2, 14, -1.5], [8 + s * 2.4 + 1.2, 17.6, .5], 2, { axis: "z", angle: s * 22.5 });
    B([8 + s * 2.4 - .5, 14.2, -.6], [8 + s * 2.4 + .5, 16.2, -.1], 5, { axis: "z", angle: s * 22.5 });
    B([8 + s * 1.2 - .3, 7.2, -8], [8 + s * 1.2 + .3, 8.4, -7.2], 5);
    B([8 + s * 1.9 - .5, 13.4, -2.8], [8 + s * 1.9 + .5, 14, -.4], 3);                         // brow
  }
  for (const [x, z, a] of [[6.1, 6.5, 22.5], [8.9, 7.5, -22.5], [6.1, 18.5, -22.5], [8.9, 19, 22.5]]) {
    const r = { axis: "x", angle: a * SGN, origin: [x + .8, 6, z + 1] };
    B([x, 0, z], [x + 1.8, 6, z + 2], 2, r); B([x - .1, 3, z - .2], [x + 1.9, 6.2, z + 2.2], 1, r);
    B([x - .1, -1, z - .6], [x + 1.9, .6, z + 2.4], 3, r); B([x + .1, -1.3, z - .9], [x + 1.7, -.6, z + .2], 5, r);   // paw + claws
  }
  B([7.2, 9, 20], [8.8, 11.5, 25], 2, { axis: "x", angle: -22.5 * SGN }); B([7, 11.5, 24], [9, 14.5, 29], 1, { axis: "x", angle: -45 * SGN }); B([7.3, 14.5, 28], [8.7, 17, 31.5], 0, { axis: "x", angle: -45 * SGN });
  for (const [y, z, s] of [[10.5, 23, 1], [13, 27, -1], [15.5, 30, 1]]) B([8 + s * .9 - .5, y - .8, z - 1.5], [8 + s * .9 + .5, y + .8, z + 1.5], 6, { axis: "x", angle: -45 * SGN });
  for (const [x, y, z, w, h, l] of [[8, 3, 14, 7, 1.4, 10], [8, 4.5, 24, 5, 1.2, 7], [4.5, 7, 22, 1.4, 3, 8], [11.5, 8, 24, 1.4, 3, 7], [8, 12.5, 26, 3, 1, 8], [3.5, 10, 18, 1, 2, 6], [12.5, 4, 18, 1, 2, 6], [8, 1.5, 28, 4, 1, 6]])
    B([x - w / 2, y - h / 2, z - l / 2], [x + w / 2, y + h / 2, Math.min(32, z + l / 2)], 4);
  B([5, 4.5, 6], [11, 13, 22], 4);
  m.write();
}

// ================================================================ lightning bolt (y 0..32, zig-zag; now thick from every side)
{
  const m = new Model("lightning_bolt", ["#ffffff", "#fffbd0", "#ffe94a", { c: "#fff27a", a: 140 }, { c: "#9ff4ff", a: 110 }, { c: "#c8fbff", a: 60 }, "#e8ffff"]);
  const P = [[9, 31], [12.5, 25], [5.5, 19.5], [11, 13], [4.5, 6.5], [8, 0.8]];
  for (let i = 0; i < P.length - 1; i++) {
    const [x0, y0] = P[i], [x1, y1] = P[i + 1], c = [(x0 + x1) / 2, (y0 + y1) / 2], a = Math.atan2(y1 - y0, x1 - x0) / D, len = Math.hypot(x1 - x0, y1 - y0) * (i === 0 ? 1.05 : 1.2);
    m.tan("xy", [c[0], c[1], 8], [len, 3.6, 2.4], a, 5); m.tan("xy", [c[0], c[1], 8], [len, 2.3, 1.8], a, 3);
    m.tan("xy", [c[0], c[1], 8], [len, 1.4, 1.3], a, 2); m.tan("xy", [c[0], c[1], 8], [len, .8, 1], a, 0);
    bolt(m, P[i], P[i + 1], 1.3, 2); bolt(m, P[i], P[i + 1], .7, 0);                              // crossed twin
    const mx = c[0] + (i % 2 ? .9 : -.9), my = c[1];                                             // a thin secondary crackle beside it
    bolt(m, [x0 + (i % 2 ? .8 : -.8), y0 - .5], [mx, my], .35, 1, false); bolt(m, [mx, my], [x1 + (i % 2 ? -.6 : .6), y1 + .5], .35, 1, false);
  }
  for (const [x, y] of P.slice(1, -1)) { m.box(...sq([x, y, 8], 1.05), 1, { axis: "z", angle: 45 }); m.box(...sq([x, y, 8], .6), 0, { axis: "z", angle: 22.5 }); m.box(...sq([x, y, 8], 1.8), 5, { axis: "y", angle: 45 }); }
  for (const [x, y, s] of [[12.5, 25, 1], [5.5, 19.5, -1], [11, 13, 1], [4.5, 6.5, -1], [9, 28, -1], [8.4, 16, 1]]) {           // forks with sub-forks
    const q = [x + s * 3.2, y - 3], r = [q[0] + s * 1.8, q[1] - 2.4], u = [q[0] - s * .4, q[1] - 2.8];
    bolt(m, [x, y], q, .9, 3, false); bolt(m, [x, y], q, .45, 0, false); bolt(m, q, r, .5, 4, false); bolt(m, q, u, .4, 1, false);
  }
  m.box([4.5, 0, 6], [11.5, 1, 10], 3); m.box([6, 0, 4.5], [10, 1, 11.5], 4);
  for (let k = 0; k < 8; k++) { const a = k * 45 * D; m.box([8 + 4.8 * Math.cos(a) - .3, 0, 8 + 4.8 * Math.sin(a) - .3], [8 + 4.8 * Math.cos(a) + .3, 2.2, 8 + 4.8 * Math.sin(a) + .3], k % 2 ? 4 : 1, { axis: Math.abs(Math.cos(a)) > .5 ? "z" : "x", angle: (Math.cos(a) + Math.sin(a) > 0 ? -1 : 1) * 22.5, origin: [8 + 4.8 * Math.cos(a), 0, 8 + 4.8 * Math.sin(a)] }); }
  for (const [x, y, z] of [[2.5, 4, 8], [13.5, 9, 8], [3, 24, 8.5], [14, 19, 7.5], [8, 31, 8], [1.5, 14, 6], [14.5, 28, 10], [5, 29, 5]]) m.box(...sq([x, y, z], .55), 6, { axis: "z", angle: 45 });
  m.write();
}

// ================================================================ rock arrow (points to -Z)
{
  const nz = (c, n = 12) => ({ c, noise: n });
  const m = new Model("rock_arrow", [nz("#8a5a30"), nz("#5e3c20"), nz("#85848a", 14), nz("#55534f", 12), nz("#6e5238"), nz("#4f7a35", 14), nz("#7fa84a", 12), nz("#b09a78", 12), nz("#a07040"), { c: "#d8f0b0", a: 80 }, nz("#6a6870", 12)]);
  seed = 21;
  prism(m, "z", [8, 8], -3, 26, .9, [0, 4]);
  for (const z of [3, 9, 15]) { prism(m, "z", [8, 8], z, z + 2.2, 1.45, [3, 10]); m.box([6.9, 9.2, z + .4], [9.1, 9.6, z + 1.8], 5); }
  for (const [z0, z1, r, cs] of [[-16, -14.4, .5, [7, 2]], [-14.4, -12, 1.4, [2, 10]], [-12, -9, 2.2, [2, 3]], [-9, -6, 2.6, [10, 2]], [-6, -4, 2, [3, 10]]]) prism(m, "z", [8, 8], z0, z1, r, cs, 4);
  const chunks = [[4.2, 9.2, -7, 3, 2], [11.8, 6.6, -6, 3.2, 3], [7.6, 11.6, -5, 2.8, 2], [8.4, 4.4, -8, 2.8, 3], [11.2, 10.8, -9.5, 2.2, 2], [4.8, 5.6, -10, 2.2, 3], [8, 8, -3, 4.6, 2], [5, 7.4, -2, 2.6, 7], [11.4, 8.6, -1.5, 2.6, 7], [3.6, 8, -4.5, 2, 10], [12.6, 8.2, -4, 2, 10], [8, 12.4, -8.5, 1.8, 3], [8, 3.6, -4, 1.8, 10]];
  chunks.forEach(([x, y, z, s, ci], i) => { m.box(...sq([x, y, z], s / 2), ci, { axis: ["y", "x", "z"][i % 3], angle: [22.5, 45, -22.5][i % 3] }); m.box(...sq([x, y, z], s / 2 - .35), i % 2 ? 3 : 2, { axis: "y", angle: 45 }); });
  for (const [x, y, z, w] of [[8, 11.4, -4, 2.6], [5, 10.4, -7.5, 1.8], [11.6, 8, -7, 1.8], [8.6, 3.3, -8, 1.6], [8, 8, -0.4, 2.2], [3.8, 6, -6, 1.4], [12.2, 11, -3, 1.4], [9.5, 12.6, -1, 1.2]]) { m.box([x - w / 2, y - .5, z - w / 2], [x + w / 2, y + .5, z + w / 2], 5); m.box([x - w / 4, y - .2, z - w / 4], [x + w / 4, y + .8, z + w / 4], 6); }
  m.box([4, 4, -10], [12, 12, -1], 9);
  for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {                                   // leaf fletching: blade + midrib
    if (dx) { m.box([Math.min(8 + dx, 8 + dx * 5), 7.6, 21.5], [Math.max(8 + dx, 8 + dx * 5), 8.4, 29.5], 6); m.box([Math.min(8 + dx, 8 + dx * 3.4), 7.55, 22.5], [Math.max(8 + dx, 8 + dx * 3.4), 8.45, 28.5], 5); m.box([Math.min(8 + dx * 4.8, 8 + dx * 5.8), 7.65, 23], [Math.max(8 + dx * 4.8, 8 + dx * 5.8), 8.35, 27], 5); }
    else { m.box([7.6, Math.min(8 + dy, 8 + dy * 5), 21.5], [8.4, Math.max(8 + dy, 8 + dy * 5), 29.5], 6); m.box([7.55, Math.min(8 + dy, 8 + dy * 3.4), 22.5], [8.45, Math.max(8 + dy, 8 + dy * 3.4), 28.5], 5); m.box([7.65, Math.min(8 + dy * 4.8, 8 + dy * 5.8), 23], [8.35, Math.max(8 + dy * 4.8, 8 + dy * 5.8), 27], 5); }
  }
  prism(m, "z", [8, 8], 28.6, 30.4, 1.1, [7, 8]);
  for (const [x, y, z, s, ci, a] of [[2.5, 12.5, -3, 1.6, 2, 22.5], [13.5, 3.8, -4.5, 1.4, 3, 45], [3.2, 3.5, 2, 1.2, 7, -22.5], [13, 12.6, 3.5, 1.5, 2, 22.5], [8, 14.8, -10, 1.1, 3, 45], [1.8, 8, -12, 1, 7, 22.5], [14.4, 8, 8, 1.1, 3, -22.5], [2, 13.5, 6, .9, 10, 45], [14.5, 13, -8, .8, 2, 22.5], [5, 1.5, -9, .9, 3, -22.5], [11, 14.6, 10, .8, 7, 45]])
    m.box(...sq([x, y, z], s / 2), ci, { axis: "y", angle: a });
  m.write();
}

// ================================================================ wind bow (arrow direction -Z)
{
  const m = new Model("wind_bow", ["#ffffff", "#e0fff4", "#8ef0c8", { c: "#6fd0ff", a: 205 }, { c: "#b8fff0", a: 80 }, { c: "#9fffe0", a: 150 }, "#4fd0a8"]);
  const cy = 8, half = 11, N = 22, pts = [];
  for (let i = 0; i <= N; i++) { const u = -1 + 2 * i / N; pts.push([cy + half * u, 4 + 8 * u * u]); }
  limb(m, pts, 3.4, 3.4, 4);
  limb(m, pts, 1.9, 1.9, 2); limb(m, pts.map(([y, z]) => [y, z - .5]), .9, 1.2, 0); limb(m, pts.map(([y, z]) => [y, z + .7]), .7, 1.4, 6);
  m.box([6.6, cy - 2.2, .8], [9.4, cy + 2.2, 4.6], 3); m.box([6.9, cy - 2.2, .5], [9.1, cy + 2.2, 1.3], 1);
  for (const dy of [-1.6, 0, 1.6]) m.box([6.4, cy + dy - .3, .9], [9.6, cy + dy + .3, 4.8], 1);
  gem(m, 8, cy, 4.6, 5.2, .7, 0, 1);
  for (const s of [-1, 1]) {
    const ty = cy + s * (half - 1.5);
    m.box([6.6, ty - 1.2, 11], [9.4, ty + 1.2, 14.5], 3, { axis: "x", angle: s * -22.5 * SGN });
    m.box([7.2, ty + s * 1.8 - .8, 13], [8.8, ty + s * 1.8 + .8, 17], 0, { axis: "x", angle: s * -45 * SGN });
    m.box([7.5, ty + s * 3.2 - .5, 15.5], [8.5, ty + s * 3.2 + .5, 18.5], 1, { axis: "x", angle: s * -45 * SGN });
    for (const k of [1, 2]) { const dy = k * 3.6; m.box([6.4, cy + s * dy - .25, 2 + 8 * (dy / half) ** 2], [9.6, cy + s * dy + .25, 3.2 + 8 * (dy / half) ** 2], 1); }
  }
  limb(m, [[cy - half, 12], [cy - half / 2, 12.5], [cy, 13], [cy + half / 2, 12.5], [cy + half, 12]], .7, .7, 0);
  limb(m, [[cy - half, 12], [cy - half / 2, 12.5], [cy, 13], [cy + half / 2, 12.5], [cy + half, 12]], 1.3, 1.3, 4);
  for (const [x, y, z, a, ci] of [[3, cy + 7, 8, 22.5, 3], [13, cy - 6, 10, -22.5, 2], [2.5, cy - 3, 4, 45, 5], [13.5, cy + 3, 6, -45, 5], [5, cy + 12, 6, 22.5, 2], [11, cy - 12, 6, -22.5, 3], [1.5, cy + 1, 10, -22.5, 1], [14.5, cy - 1, 2, 22.5, 1], [4, cy - 9, 12, 45, 5], [12, cy + 9, 12, -45, 5], [8, cy + 14, 9, 22.5, 4], [8, cy - 14, 9, -22.5, 4]])
    m.box([x - .4, y - 1, z - 2.2], [x + .4, y + 1, z + 2.2], ci, { axis: "y", angle: a });
  m.write();
}

// ================================================================ leaf (tip at -Z)
{
  const m = new Model("leaf", ["#2f8f3a", "#58c34f", "#9be86a", "#d7ff9a", "#7a5a30", "#1f6a2c", "#4aa84a", "#b8f080"]);
  const rows = 22;
  for (let k = 0; k < rows; k++) {
    const t = (k + .5) / rows, zc = -14 + t * 36, w = 1 + 7 * Math.pow(Math.sin(Math.PI * Math.pow(t, .8)), .85), dz = 36 / rows + .3;
    for (const s of [-1, 1]) {
      const rot = { axis: "z", angle: -s * 22.5, origin: [8, 8, zc] }, mid = 8 + s * w * .55, x1 = 8 + s * w, ser = k % 2 ? .4 : 0;
      m.box([8, 7.6, zc - dz / 2], [mid, 8.4, zc + dz / 2], k % 2 ? 2 : 1, rot);
      m.box([mid, 7.6, zc - dz / 2], [x1 - s * ser, 8.4, zc + dz / 2], k % 4 === 1 ? 5 : k % 3 ? 0 : 6, rot);
    }
  }
  for (const z of [-8, -2, 4, 10, 16]) for (const s of [-1, 1]) m.box([Math.min(8, 8 + s * 4.5), 8.35, z], [Math.max(8, 8 + s * 4.5), 8.6, z + .35], 7, { axis: "z", angle: -s * 22.5, origin: [8, 8, z] });   // side veins
  m.box([7.4, 7.2, -14], [8.6, 9, 24], 3); m.box([7.6, 8.9, -12], [8.4, 9.2, 22], 7);
  m.box([7.5, 6.6, 22], [8.5, 7.6, 31], 4, { axis: "x", angle: 22.5 });
  m.write();
}

// ================================================================ slashes and rings: same shapes, finer steps
function crescent(m, o) {
  const R = 20, amax = o.amax, N = o.n;
  for (let k = 0; k <= N; k++) {
    const a = -amax + (2 * amax * k) / N, ar = a * D, t = k / N, f = Math.pow(Math.cos((a / amax) * Math.PI / 2), o.pow);
    const T = o.minT + (o.maxT - o.minT) * f, hgt = o.minH + (o.maxH - o.minH) * f + (o.jitter ? (rnd() - .5) * o.jitter : 0), yoff = o.jitter ? (rnd() - .5) * o.jitter : 0;
    const seg = t < .34 ? 0 : t < .67 ? 1 : 2;
    let r1 = R;
    for (const [fr, ci] of o.bands(seg)) { const w = Math.max(.6, T * fr), rc = r1 - w / 2; r1 -= w; m.tan("xz", [8 + rc * Math.sin(ar), 8 + yoff, 8 - (rc * Math.cos(ar) - 14)], [o.L, w, hgt], a, ci); }
  }
}
{
  const m = new Model("slash_arc", ["#ffffff", "#d8fbff", "#8eeaff", "#4cc9f0", { c: "#2aa3d8", a: 200 }, "#eaffff", { c: "#8eeaff", a: 70 }]);
  crescent(m, { amax: 80, n: 30, L: 3.4, minT: 1, maxT: 9, minH: .8, maxH: 2.4, pow: .8, jitter: 0, bands: () => [[.22, 3], [.2, 2], [.36, 0], [.22, 2]] });
  crescent(m, { amax: 74, n: 18, L: 4.4, minT: .8, maxT: 12, minH: .6, maxH: 3.4, pow: .9, jitter: 0, bands: () => [[1, 6]] });   // soft glow behind
  for (const a of [-76, -62, -48, -34, 34, 48, 62, 76]) m.tan("xz", [8 + (22.5 + Math.abs(a) / 40) * Math.sin(a * D), 8 + (a % 3 ? .6 : -.6), 8 - ((22.5 + Math.abs(a) / 40) * Math.cos(a * D) - 14)], [2.6, .7, .7], a, 5);
  m.write();
}
{
  const noisy = (c, n = 18) => ({ c, noise: n });
  const m = new Model("slash_red", [noisy("#8a1c14"), noisy("#d62f1e"), noisy("#ff7a1f"), noisy("#ffb23a"), noisy("#7a7a82", 22), noisy("#b8b8c0", 22), noisy("#4a1410"), noisy("#ffe08a"), { c: "#ff7a1f", a: 70 }]);
  seed = 99;
  crescent(m, { amax: 84, n: 24, L: 4.4, minT: 2.5, maxT: 12, minH: 1.8, maxH: 4.4, pow: .7, jitter: 1.2,
    bands: seg => seg === 0 ? [[.3, 2], [.35, 1], [.35, 0]] : seg === 1 ? [[.3, 3], [.4, 2], [.3, 1]] : [[.3, 5], [.4, 4], [.3, 2]] });
  crescent(m, { amax: 78, n: 14, L: 6, minT: 2, maxT: 15, minH: 1.4, maxH: 5.5, pow: .8, jitter: 0, bands: () => [[1, 8]] });
  for (let i = 0; i < 14; i++) {
    const a = -74 + i * 11.4, ar = a * D, r = 21.5 + rnd() * 2, s = .9 + rnd() * 1.6;
    m.box(...sq([8 + r * Math.sin(ar), 8 + (rnd() - .5) * 6, 8 - (r * Math.cos(ar) - 14)], s / 2), [3, 4, 2, 5, 1, 7, 0][i % 7], { axis: ["y", "x", "z"][i % 3], angle: [22.5, -22.5, 45][i % 3] });
  }
  m.write();
}
{
  const m = new Model("whirl_ring", ["#ffffff", "#c9f7ff", "#6fdcff", "#2f9de0", "#eaffff", { c: "#6fdcff", a: 70 }]);
  const R = 18;
  for (let k = 0; k < 24; k++) {
    const phi = k * 15, pr = phi * D, y = 8 + Math.sin(k * Math.PI / 6) * 1.8, cx = 8 + R * Math.cos(pr), cz = 8 + R * Math.sin(pr);
    m.tan("xz", [cx + Math.cos(pr) * .8, y, cz + Math.sin(pr) * .8], [7.4, 1.5, 1.1], phi + 102, 3);
    m.tan("xz", [cx, y, cz], [6.8, 1.5, 1.4], phi + 102, 2);
    m.tan("xz", [cx - Math.cos(pr) * .8, y, cz - Math.sin(pr) * .8], [6, 1.1, 1.2], phi + 102, 0);
    m.tan("xz", [cx - Math.cos(pr) * 1.8, y, cz - Math.sin(pr) * 1.8], [5.6, 1.6, 2.4], phi + 102, 5);
    if (k % 2 === 0) m.tan("xz", [8 + (R + 2.6) * Math.cos(pr), y, 8 + (R + 2.6) * Math.sin(pr)], [3.6, 1.1, .9], phi, 4);
  }
  m.write();
}
{
  const m = new Model("wind_spiral", ["#ffffff", "#d8fbff", "#8eeaff", "#4cc9f0", "#bff7e0", { c: "#bff7ff", a: 70 }]);
  for (let rib = 0; rib < 2; rib++) {
    const N = 40;
    for (let k = 0; k < N; k++) {
      const t = k / (N - 1), z = -12 + t * 40, phi = (t * 2.1 * 360 + rib * 180) * D, r = 6.5 + 1.5 * Math.sin(t * Math.PI), wz = 1 + 2.2 * Math.sin(t * Math.PI);
      const c = [8 + r * Math.cos(phi), 8 + r * Math.sin(phi), z], tang = phi / D + 90;
      m.tan("xy", c, [4.2, 1.1, wz + 1.2], tang, rib ? 3 : 2);
      m.tan("xy", [c[0] - Math.cos(phi) * .1, c[1] - Math.sin(phi) * .1, z], [3.8, 1.4, wz], tang, rib ? 4 : 0);
      if (k % 3 === 0) m.tan("xy", [8 + (r + 1.4) * Math.cos(phi), 8 + (r + 1.4) * Math.sin(phi), z], [3.4, .9, wz + 2], tang, 5);
    }
  }
  m.write();
}

// ================================================================ 만월 (full moon, radius 7.5 centred in the cube)
function moonModel(name, pal) {
  const m = new Model(name, pal, DISP_FLAT(.6));
  ball(m, [8, 8, 8], 7.5, [1, 0, 4], 14, 4);
  const front = (x, y) => 8 - Math.sqrt(Math.max(0, 7.5 * 7.5 - (x - 8) ** 2 - (y - 8) ** 2));
  for (const [x, y, s] of [[5.5, 9.5, 2.4], [9.8, 6, 1.8], [9.2, 11.2, 1.3], [6.2, 5.2, 1.2], [11.5, 9, .9], [4.4, 7, .8], [8, 13, .9], [7.6, 3.4, .8]]) {   // craters with rims
    const z = front(x, y);
    prism(m, "z", [x, y], z - .25, z + .4, s / 2 + .3, 3);
    prism(m, "z", [x, y], z - .35, z + .45, s / 2, 2);
    m.box([x - s / 4, y - s / 4, z - .4], [x + s / 4, y + s / 4, z + .5], 3);
  }
  for (const [x, y, w, h] of [[7, 8.5, 3, 2], [10, 9.5, 2, 1.4], [6, 11.5, 1.6, 1]]) { const z = front(x, y); m.box([x - w / 2, y - h / 2, z - .2], [x + w / 2, y + h / 2, z + .4], 5); }   // maria
  ball(m, [8, 8, 8], 8.4, [6], 3, 2);
  m.write();
}
moonModel("full_moon", ["#fffbe6", "#fff0b8", "#e3cf8c", "#c4ad6a", "#f6e6a6", "#d8c27e", { c: "#fff6cc", a: 60 }]);
// 푸른 달 (만월의 추억 rework): the same moon in cold moonlight blues
moonModel("moon_blue", ["#e6f4ff", "#b8dcff", "#7fb0f0", "#4f7ad8", "#cfe6ff", "#6a92e0", { c: "#a8d4ff", a: 70 }]);

// ================================================================ 분신 무기 (basic-attack phantoms: glowing bow / sword / staff beside the player)
function phantom(name, cols, build) {
  // 0 pale, 1 main glow, 2 deep, 3 dark grip, 4 white, 5 aura
  const m = new Model(name, [cols[0], cols[1], cols[2], cols[3], "#ffffff", { c: cols[1], a: 70 }], DISP_FLAT(.6));
  build(m); m.write();
}
phantom("phantom_sword", ["#ffe6e2", "#ff5a50", "#b8262a", "#4a1416"], m => {         // long slender blade y 6..24, guard y 5..6, grip, pommel
  m.box([7.4, 6, 7.75], [8.6, 23.6, 8.25], 1);
  pair(m, [8.45, 6, 7.82], [8.72, 23.6, 8.18], 0);
  m.box([7.85, 6.4, 7.7], [8.15, 22.8, 8.3], 4);
  for (let i = 0; i < 5; i++) { const t = (i + 1) / 5, h = Math.max(.12, .6 * Math.pow(1 - t * .95, .8)); m.box([8 - h, 23.6 + 1.8 * i / 5, 7.78], [8 + h, 23.6 + 1.8 * t + .02, 8.22], i > 2 ? 0 : 1); }
  m.box([7.4, 5.7, 7.5], [8.6, 6.6, 8.5], 2);
  m.box([4.6, 5.1, 7.4], [11.4, 6, 8.6], 2); m.box([4.8, 5.8, 7.55], [11.2, 6.2, 8.45], 0);
  pair(m, [10.9, 4.6, 7.6], [11.9, 6.4, 8.4], 1, { axis: "z", angle: -22.5, origin: [11.2, 5.5, 8] });
  gem(m, 8, 5.55, 7.3, 8.7, .45, 4, 1);
  prism(m, "y", [8, 8], .2, 5.1, .5, [3, 2]);
  for (const y of [1, 2.2, 3.4, 4.5]) prism(m, "y", [8, 8], y, y + .35, .62, 2);
  ball(m, [8, -.3, 8], .85, [0, 1], 3, 4);
  m.box([7, 4.8, 7.3], [9, 25.6, 8.7], 5);
});
phantom("phantom_bow", ["#e6ffe9", "#5ae66e", "#2a9a45", "#1d4a2a"], m => {           // recurve: grip y 6..10, limbs bend back to the string at z 6.2
  const pts = []; for (let i = 0; i <= 16; i++) { const u = -1 + 2 * i / 16; pts.push([8 + 8.6 * u, 8.9 - 2.3 * u * u - (Math.abs(u) > .85 ? (Math.abs(u) - .85) * 3 : 0)]); }
  limb(m, pts, 1.2, 1.2, 1); limb(m, pts.map(([y, z]) => [y, z + .45]), .5, .7, 0); limb(m, pts, 2.2, 2, 5);
  m.box([7.2, 6, 7.9], [8.8, 10, 9.7], 3);
  for (const y of [6.4, 7.4, 8.4, 9.4]) m.box([7.1, y, 7.8], [8.9, y + .35, 9.8], 2);
  m.box([7.4, 5.6, 7.9], [8.6, 6.2, 9.7], 0); m.box([7.4, 9.8, 7.9], [8.6, 10.4, 9.7], 0);
  for (const y of [-1, 17]) ball(m, [8, y, 6.4], .6, [0, 4], 3, 2);
  m.box([7.85, -.6, 6.05], [8.15, 16.6, 6.35], 0);
  m.box([7.6, 7.3, 6], [8.4, 8.7, 6.6], 4);
  m.box([7.7, 7.6, 9.6], [8.3, 8.4, 10.4], 2);
  gem(m, 8, 12.5, 8.5, 9.6, .35, 4, 0); gem(m, 8, 3.5, 8.5, 9.6, .35, 4, 0);
});
phantom("phantom_staff", ["#f3e6ff", "#b05aff", "#6a2ab8", "#2a1640"], m => {         // shaft y -2.5..12, crescent prongs, orb at y ~14.5
  prism(m, "y", [8, 8], -2.5, 12, .6, [3, 2]);
  for (const y of [-1, 3, 7.5, 11]) prism(m, "y", [8, 8], y, y + .5, .78, y === 11 ? 1 : 2);
  ball(m, [8, -3, 8], .55, [0, 1], 2, 2);
  prism(m, "y", [8, 8], 11.4, 12.4, 1, [2, 1]);
  for (const s of [-1, 1]) {
    m.box([8 + s * 1.4 - .4, 12, 7.6], [8 + s * 1.4 + .4, 15.2, 8.4], 2, { axis: "z", angle: -s * 22.5, origin: [8 + s * 1.4, 12, 8] });
    m.box([8 + s * 2.4 - .3, 14.8, 7.65], [8 + s * 2.4 + .3, 17, 8.35], 1, { axis: "z", angle: s * 22.5, origin: [8 + s * 2.4, 14.8, 8] });
    m.box([8 + s * 1.7 - .2, 16.6, 7.7], [8 + s * 1.7 + .2, 17.6, 8.3], 0, { axis: "z", angle: s * 45, origin: [8 + s * 1.7, 16.6, 8] });
  }
  ball(m, [8, 14.6, 8], 1.7, [1, 0, 1], 5, 4);
  ball(m, [8, 14.6, 8], .8, [4], 2, 2);
  ball(m, [8, 14.6, 8], 2.6, [5], 3, 2);
});
