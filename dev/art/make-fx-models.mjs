// Generates 3D skill-effect item models + palette textures:  node dev/art/make-fx-models.mjs
// Writes pipi-facilities/assets/pipifacilities/{models,textures}/item/fx_<name>.{json,png}
// Conventions: model centre = (8,8,8); "forward" = model -Z (ItemDisplay renders it as world +Z, see ClassSkills).
// Element rotations are limited to -45..45 in 22.5 steps, so curves are built from many small rotated boxes.
import fs from "node:fs";
import path from "node:path";
import zlib from "node:zlib";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "../pipi-facilities/assets/pipifacilities");
const texDir = path.join(root, "textures/item"), modDir = path.join(root, "models/item");
fs.mkdirSync(texDir, { recursive: true }); fs.mkdirSync(modDir, { recursive: true });

const crcT = new Uint32Array(256).map((_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const crc = b => { let c = 0xffffffff; for (const x of b) c = crcT[(c ^ x) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
function chunk(type, data) {
  const t = Buffer.from(type, "ascii"), l = Buffer.alloc(4), c = Buffer.alloc(4);
  l.writeUInt32BE(data.length); c.writeUInt32BE(crc(Buffer.concat([t, data])));
  return Buffer.concat([l, t, data, c]);
}
function png(px, w = 16, h = 16) {
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) { raw[y * (w * 4 + 1)] = 0; for (let x = 0; x < w * 4; x++) raw[y * (w * 4 + 1) + 1 + x] = px[y * w * 4 + x]; }
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk("IHDR", ihdr), chunk("IDAT", zlib.deflateSync(raw)), chunk("IEND", Buffer.alloc(0))]);
}
let seed = 12345;
const rnd = () => (seed = (seed * 1664525 + 1013904223) >>> 0) / 4294967296;
const hex = h => [1, 3, 5].map(i => parseInt(h.slice(i, i + 2), 16));

/** palette entry: "#rrggbb" | "#rrggbbaa" | {c, a, noise, speck:[hex, prob]} ; 16 cells of 4x4 px. */
function paletteTexture(pal) {
  const px = new Uint8Array(16 * 16 * 4);
  pal.forEach((e, i) => {
    const o = typeof e === "string" ? { c: e } : e;
    const base = hex(o.c); const a = o.a ?? (o.c.length > 7 ? parseInt(o.c.slice(7, 9), 16) : 255);
    const cx = (i % 4) * 4, cy = Math.floor(i / 4) * 4;
    for (let y = 0; y < 4; y++) for (let x = 0; x < 4; x++) {
      let c = base.slice();
      const n = o.noise ?? 0; const j = (rnd() - .5) * 2 * n;
      c = c.map(v => Math.max(0, Math.min(255, Math.round(v + j))));
      if (o.speck && rnd() < o.speck[1]) c = hex(o.speck[0]);
      const k = ((cy + y) * 16 + cx + x) * 4; px[k] = c[0]; px[k + 1] = c[1]; px[k + 2] = c[2]; px[k + 3] = a;
    }
  });
  return png(px);
}

class Model {
  constructor(name, pal) { this.name = name; this.pal = pal; this.els = []; }
  /** axis-aligned box from..to (model units) in palette colour ci, optional {axis,angle,origin}. */
  box(from, to, ci, rot) {
    const f = from.map((v, i) => Math.min(v, to[i])), t = from.map((v, i) => Math.max(v, to[i]));
    for (let i = 0; i < 3; i++) { if (t[i] - f[i] < .3) t[i] = f[i] + .3; if (f[i] < -16 || t[i] > 32) throw new Error(this.name + ": out of range " + f + " " + t); }
    const col = ci % 4 * 4, row = Math.floor(ci / 4) * 4;
    const face = { uv: [col, row, col + 4, row + 4], texture: "#0" };
    const el = { from: f.map(v => +v.toFixed(3)), to: t.map(v => +v.toFixed(3)), shade: false,
      faces: { north: face, east: face, south: face, west: face, up: face, down: face } };
    if (rot && rot.angle) {
      if (![-45, -22.5, 0, 22.5, 45].includes(rot.angle)) throw new Error("bad angle " + rot.angle);
      el.rotation = { angle: rot.angle, axis: rot.axis, origin: rot.origin ?? f.map((v, i) => (v + t[i]) / 2) };
    }
    this.els.push(el);
  }
  /** Box centred at c with size s, long axis along tangent angle a (deg) inside plane "xz" or "xy". s = [along, across-in-plane, third]. */
  tan(plane, c, s, a, ci) {
    let ang = ((a % 180) + 180) % 180; if (ang > 90) ang -= 180; // (-90,90]
    let swap = false;
    if (Math.abs(ang) > 45) { swap = true; ang -= 90 * Math.sign(ang); }
    ang = Math.round(ang / 22.5) * 22.5;
    const [L, W, T] = s;
    if (plane === "xz") {
      const d = swap ? [W, T, L] : [L, T, W];
      this.box([c[0] - d[0] / 2, c[1] - d[1] / 2, c[2] - d[2] / 2], [c[0] + d[0] / 2, c[1] + d[1] / 2, c[2] + d[2] / 2], ci, { axis: "y", angle: -ang });
    } else { // xy, rotation about z; T is the z thickness
      const d = swap ? [W, L, T] : [L, W, T];
      this.box([c[0] - d[0] / 2, c[1] - d[1] / 2, c[2] - d[2] / 2], [c[0] + d[0] / 2, c[1] + d[1] / 2, c[2] + d[2] / 2], ci, { axis: "z", angle: ang });
    }
  }
  write() {
    if (this.els.length > 400) throw new Error(this.name + " too many elements");
    const tex = "pipifacilities:item/fx_" + this.name;
    const id = { rotation: [0, 0, 0], translation: [0, 0, 0], scale: [1, 1, 1] };
    const json = { textures: { "0": tex, particle: tex }, elements: this.els,
      display: { none: id, fixed: id, gui: { rotation: [30, 45, 0], scale: [.4, .4, .4] } } };
    fs.writeFileSync(path.join(modDir, `fx_${this.name}.json`), JSON.stringify(json));
    fs.writeFileSync(path.join(texDir, `fx_${this.name}.png`), paletteTexture(this.pal));
    console.log(`fx_${this.name}: ${this.els.length} elements`);
  }
}
const D = Math.PI / 180;
const sq = (c, h, cc) => [[c[0] - h, c[1] - h, c[2] - h], [c[0] + h, c[1] + h, c[2] + h]];

// ------------------------------------------------------------------ slash arc (cyan)
function crescent(m, o) {
  const R = 20, amax = o.amax, N = o.n;
  for (let k = 0; k <= N; k++) {
    const a = -amax + (2 * amax * k) / N, ar = a * D, t = k / N;
    const f = Math.pow(Math.cos((a / amax) * Math.PI / 2), o.pow);
    const T = o.minT + (o.maxT - o.minT) * f;
    const hgt = o.minH + (o.maxH - o.minH) * f + (o.jitter ? (rnd() - .5) * o.jitter : 0);
    const yoff = o.jitter ? (rnd() - .5) * o.jitter : 0;
    const seg = t < .34 ? 0 : t < .67 ? 1 : 2;
    // bands, outer edge first: [width fraction, colour]
    const bands = o.bands(seg);
    let r1 = R;
    for (const [fr, ci] of bands) {
      const w = Math.max(.6, T * fr), rc = r1 - w / 2; r1 -= w;
      const c = [8 + rc * Math.sin(ar), 8 + yoff, 8 - (rc * Math.cos(ar) - 14)];
      m.tan("xz", c, [o.L, w, hgt], a, ci);
    }
  }
}
{
  const m = new Model("slash_arc", ["#ffffff", "#d8fbff", "#8eeaff", "#4cc9f0", { c: "#2aa3d8", a: 200 }, "#eaffff"]);
  crescent(m, { amax: 80, n: 16, L: 5.4, minT: 1, maxT: 9, minH: .8, maxH: 2.4, pow: .8, jitter: 0,
    bands: () => [[.22, 3], [.2, 2], [.36, 0], [.22, 2]] });
  // thin trailing sparks
  for (const a of [-70, -40, 40, 70]) m.tan("xz", [8 + 23 * Math.sin(a * D), 8, 8 - (23 * Math.cos(a * D) - 14)], [3, .8, .8], a, 5);
  m.write();
}
// ------------------------------------------------------------------ slash arc (red chunky, like block ribbon)
{
  const noisy = (c, n = 18) => ({ c, noise: n });
  const m = new Model("slash_red", [noisy("#8a1c14"), noisy("#d62f1e"), noisy("#ff7a1f"), noisy("#ffb23a"), noisy("#7a7a82", 22), noisy("#b8b8c0", 22),
    noisy("#4a1410"), noisy("#ffe08a")]);
  seed = 99;
  crescent(m, { amax: 84, n: 12, L: 7.5, minT: 2.5, maxT: 12, minH: 1.8, maxH: 4.4, pow: .7, jitter: 1.2,
    bands: seg => seg === 0 ? [[.3, 2], [.35, 1], [.35, 0]] : seg === 1 ? [[.3, 3], [.4, 2], [.3, 1]] : [[.3, 5], [.4, 4], [.3, 2]] });
  // loose chunks flying off the outer edge
  for (let i = 0; i < 7; i++) {
    const a = -70 + i * 23, ar = a * D, r = 21.5 + rnd() * 1.5, s = 1.2 + rnd() * 1.6;
    m.box(...sq([8 + r * Math.sin(ar), 8 + (rnd() - .5) * 5, 8 - (r * Math.cos(ar) - 14)], s / 2), [3, 4, 2, 5, 1, 7, 0][i], { axis: "y", angle: [22.5, -22.5, 45][i % 3] });
  }
  m.write();
}
// ------------------------------------------------------------------ whirl ring
{
  const m = new Model("whirl_ring", ["#ffffff", "#c9f7ff", "#6fdcff", "#2f9de0", "#eaffff"]);
  const R = 18;
  for (let k = 0; k < 16; k++) {
    const phi = k * 22.5, pr = phi * D, y = 8 + (k % 2 ? 1.6 : -1.6);
    const cx = 8 + R * Math.cos(pr), cz = 8 + R * Math.sin(pr);
    m.tan("xz", [cx + Math.cos(pr) * .8, y, cz + Math.sin(pr) * .8], [10, 1.5, 1.1], phi + 90 + 12, 3);
    m.tan("xz", [cx, y, cz], [9, 1.5, 1.4], phi + 90 + 12, 2);
    m.tan("xz", [cx - Math.cos(pr) * .8, y, cz - Math.sin(pr) * .8], [8, 1.1, 1.2], phi + 90 + 12, 0);
    // blade tip pointing outward
    m.tan("xz", [8 + (R + 2.6) * Math.cos(pr), y, 8 + (R + 2.6) * Math.sin(pr)], [3.6, 1.1, .9], phi, 4);
  }
  m.write();
}
// ------------------------------------------------------------------ shockwave spikes
{
  const noisy = (c, n = 14) => ({ c, noise: n });
  const m = new Model("shockwave", [noisy("#5a5650"), noisy("#8d887e"), noisy("#c9c2b2"), "#fff3c0", "#ffd36a", noisy("#3c3935")]);
  seed = 7;
  const xs = [-11, -6, -1, 4, 8, 12, 17, 22, 27];
  xs.forEach((x, i) => {
    const mid = 1 - Math.abs(x - 8) / 22;           // taller in the middle
    const h = 9 + mid * 14 + rnd() * 3, z = 8 + (rnd() - .5) * 6, base = 6;
    const lean = [-22.5, 22.5, 0, -22.5, 0, 22.5, 0, -22.5, 22.5][i];
    const o = [x, base, z], rot = { axis: "z", angle: lean, origin: o };
    const wd = 3.2 + mid * 2.2;
    m.box([x - wd, base, z - wd * .8], [x + wd, base + h * .38, z + wd * .8], i % 2 ? 0 : 5, rot);
    m.box([x - wd * .72, base + h * .38, z - wd * .6], [x + wd * .72, base + h * .66, z + wd * .6], 1, rot);
    m.box([x - wd * .45, base + h * .66, z - wd * .4], [x + wd * .45, base + h * .88, z + wd * .4], 2, rot);
    m.box([x - wd * .2, base + h * .88, z - wd * .2], [x + wd * .2, base + h, z + wd * .2], 3, rot);
  });
  // glowing crack line at the base
  m.box([-14, 5.4, 7], [30, 6.4, 9], 4);
  m.write();
}
// ------------------------------------------------------------------ giant sword (tip points down)
{
  const m = new Model("giant_sword", ["#ffffff", "#bff4ff", "#4fd0ff", "#e8b84a", "#7a5030", "#4a3020", { c: "#6fe0ff", a: 90 }, "#fff6c8"]);
  m.box([7.3, -16, 7.3], [8.7, -12, 8.7], 0);
  m.box([6.4, -12, 7], [9.6, -7, 9], 1); m.box([7.2, -12, 7.3], [8.8, -7, 8.7], 0);
  m.box([5.4, -7, 6.8], [10.6, -2, 9.2], 1); m.box([7, -7, 7.2], [9, -2, 8.8], 0);
  m.box([4, -2, 6.5], [12, 21, 9.5], 1);
  m.box([4, -2, 6.4], [5.3, 21, 9.6], 2); m.box([10.7, -2, 6.4], [12, 21, 9.6], 2);   // glowing edges
  m.box([6.4, -3, 7], [9.6, 22, 9], 0);                                                  // white core
  m.box([7.4, -6, 6.6], [8.6, 21, 9.4], 7);                                              // fuller line
  m.box([2.6, -2, 5.5], [13.4, 21.5, 10.5], 6);                                          // translucent aura
  for (const [y, w] of [[3, 1.4], [8, 1.6], [13, 1.4]]) { m.box([4 - w, y, 7], [4, y + 3, 9], 2, { axis: "z", angle: 22.5, origin: [4, y, 8] }); m.box([12, y, 7], [12 + w, y + 3, 9], 2, { axis: "z", angle: -22.5, origin: [12, y, 8] }); }
  m.box([-2, 21, 6], [18, 23.5, 10], 3);                                                 // guard
  m.box([-3.6, 20.4, 6.3], [-1, 24.6, 9.7], 7, { axis: "z", angle: 22.5 }); m.box([17, 20.4, 6.3], [19.6, 24.6, 9.7], 7, { axis: "z", angle: -22.5 });
  m.box([6.8, 23.5, 6.8], [9.2, 29.5, 9.2], 4);
  for (const y of [24.5, 26.5, 28.5]) m.box([6.5, y, 6.5], [9.5, y + .9, 9.5], 5);
  m.box([5.6, 29.5, 5.6], [10.4, 32, 10.4], 3, { axis: "y", angle: 45 });
  m.write();
}
// ------------------------------------------------------------------ big arrow (points to model -Z)
{
  const m = new Model("arrow_big", ["#ffffff", "#c9f7ff", "#5ad8ff", "#fff0b0", "#d9a050", "#ff5a4a", { c: "#2aa3d8", a: 230 }]);
  m.box([7.5, 7.5, -16], [8.5, 8.5, -12], 0);
  m.box([6.8, 6.8, -12], [9.2, 9.2, -8], 2);
  m.box([6, 6, -8], [10, 10, -4], 1); m.box([6, 6, -8], [10, 10, -4], 1, { axis: "z", angle: 45 });
  m.box([5.2, 5.2, -4.4], [10.8, 10.8, -2.4], 6, { axis: "z", angle: 45 });
  m.box([5.2, 5.2, -4.4], [10.8, 10.8, -2.4], 6);
  m.box([7.1, 7.1, -3], [8.9, 8.9, 26], 4);
  m.box([7.5, 7.5, -3], [8.5, 8.5, 26], 3);
  for (const [ang, ci] of [[0, 5], [45, 0], [-45, 5], [90, 0]]) {   // 4 fins at the tail
    const a = ang === 90 ? 0 : ang;
    if (ang === 90) m.box([2.5, 7.6, 21], [13.5, 8.4, 30], 5);
    else m.box([7.6, 2.5, 21], [8.4, 13.5, 30], ci, { axis: "z", angle: a });
  }
  m.box([6.9, 6.9, 29.4], [9.1, 9.1, 30.6], 3);
  m.write();
}
// ------------------------------------------------------------------ wind spiral (ribbon along -Z)
{
  const m = new Model("wind_spiral", ["#ffffff", "#d8fbff", "#8eeaff", "#4cc9f0", "#bff7e0"]);
  for (let rib = 0; rib < 2; rib++) {
    const N = 26;
    for (let k = 0; k < N; k++) {
      const t = k / (N - 1), z = -12 + t * 40, phi = (t * 2.1 * 360 + rib * 180) * D;
      const r = 6.5 + 1.5 * Math.sin(t * Math.PI), wz = 1.2 + 3.2 * Math.sin(t * Math.PI);
      const c = [8 + r * Math.cos(phi), 8 + r * Math.sin(phi), z];
      const tang = phi / D + 90;
      m.tan("xy", c, [6, 1.1, wz + 1.6], tang, rib ? 3 : 2);
      m.tan("xy", [c[0] - Math.cos(phi) * .1, c[1] - Math.sin(phi) * .1, z], [5.4, 1.4, wz], tang, rib ? 4 : 0);
    }
  }
  m.write();
}
// ------------------------------------------------------------------ leaf (tip at -Z)
{
  const m = new Model("leaf", ["#2f8f3a", "#58c34f", "#9be86a", "#d7ff9a", "#7a5a30", "#1f6a2c"]);
  const rows = 12;
  for (let k = 0; k < rows; k++) {
    const t = (k + .5) / rows, z = -14 + t * 36, w = 1 + 7 * Math.pow(Math.sin(Math.PI * Math.pow(t, .8)), .85);
    const zc = z, dz = 3.4;
    for (const s of [-1, 1]) {
      const rot = { axis: "z", angle: -s * 22.5, origin: [8, 8, zc] };
      const x0 = 8, mid = 8 + s * w * .55, x1 = 8 + s * w;
      m.box([x0, 7.6, zc - dz / 2], [mid, 8.4, zc + dz / 2], k % 2 ? 2 : 1, rot);
      m.box([mid, 7.6, zc - dz / 2], [x1, 8.4, zc + dz / 2], k % 3 ? 0 : 5, rot);
    }
  }
  m.box([7.4, 7.2, -14], [8.6, 9, 24], 3);
  m.box([7.5, 6.6, 22], [8.5, 7.6, 31], 4, { axis: "x", angle: 22.5 });
  m.write();
}
// ------------------------------------------------------------------ magic orb (purple, faceted)
{
  const m = new Model("orb", ["#ffffff", "#e9c8ff", "#b86bff", "#7a2fd6", { c: "#4b1a8c", a: 235 }, "#ff9cf0"]);
  m.box(...sq([8, 8, 8], 6.2), 4, { axis: "y", angle: 45 });
  m.box(...sq([8, 8, 8], 5.9), 3, { axis: "x", angle: 45 });
  m.box(...sq([8, 8, 8], 5.6), 3, { axis: "z", angle: 45 });
  m.box(...sq([8, 8, 8], 5.2), 2);
  m.box(...sq([8, 8, 8], 5.0), 2, { axis: "y", angle: 22.5 });
  m.box(...sq([8, 8, 8], 3.2), 1, { axis: "y", angle: 45 });
  m.box(...sq([8, 8, 8], 2.1), 0, { axis: "x", angle: 45 });
  for (const [x, y, z] of [[8, 8, 0], [8, 8, 16], [0, 8, 8], [16, 8, 8], [8, 0.5, 8], [8, 15.5, 8]]) m.box(...sq([x, y, z], 1.2), 5, { axis: "y", angle: 45 });
  m.write();
}
// ------------------------------------------------------------------ ice lance (points to model -Z)
{
  const m = new Model("ice_lance", [{ c: "#c8f0ff", a: 215 }, { c: "#eefcff", a: 235 }, "#ffffff", { c: "#7cc8f0", a: 205 }, { c: "#4a98d0", a: 200 }]);
  const oct = (z0, z1, s, ci) => { m.box([8 - s / 2, 8 - s / 2, z0], [8 + s / 2, 8 + s / 2, z1], ci); m.box([8 - s / 2, 8 - s / 2, z0], [8 + s / 2, 8 + s / 2, z1], ci, { axis: "z", angle: 45 }); };
  oct(-4, 27, 5, 0); oct(-8, -4, 3.8, 1); oct(-12, -8, 2.6, 0); oct(-16, -12, 1.2, 2);
  m.box([7.1, 7.1, -9], [8.9, 8.9, 28], 2);
  oct(24, 30, 6.4, 3);
  for (const [z, a, side] of [[5, 22.5, 1], [10, -22.5, -1], [15, 22.5, -1], [20, -22.5, 1]]) {
    m.box([8 + side * 2.5, 7.4, z], [8 + side * 7, 8.6, z + 4], 3, { axis: "z", angle: a * side, origin: [8 + side * 2.5, 8, z + 2] });
    m.box([7.4, 8 + side * 2.5, z], [8.6, 8 + side * 7, z + 4], 4, { axis: "z", angle: a * side, origin: [8, 8 + side * 2.5, z + 2] });
  }
  m.box([7, 2, 27], [9, 14, 31], 1); m.box([2, 7, 27], [14, 9, 31], 1);
  m.write();
}
// ------------------------------------------------------------------ meteor
{
  const m = new Model("meteor", [{ c: "#3a2c28", noise: 10, speck: ["#ff8a2a", .12] }, { c: "#574036", noise: 12, speck: ["#ff6a1a", .1] }, { c: "#7a5a46", noise: 12 },
    "#ff3b14", "#ff8a1f", "#ffd23a", "#fff4b0", { c: "#ff8a1f", a: 210 }, { c: "#ffd23a", a: 220 }]);
  seed = 5;
  m.box(...sq([8, 6, 8], 6), 0, { axis: "y", angle: 22.5 });
  m.box(...sq([8, 6, 8], 5.8), 1, { axis: "x", angle: 45 });
  m.box(...sq([8, 6, 8], 5.4), 2, { axis: "z", angle: 22.5 });
  m.box(...sq([8, 6, 8], 4.2), 1, { axis: "y", angle: 45 });
  m.box(...sq([8, 6, 8], 2.5), 3, { axis: "y", angle: 45 });
  for (const [x, y, z, s, a] of [[1, 4, 5, 2.2, 22.5], [15, 3, 10, 2.4, -22.5], [7, -2, 12, 2, 45], [10, 1, 2, 1.8, 22.5]]) m.box(...sq([x, y, z], s / 2), 0, { axis: "y", angle: a });
  // flame fins trail upward (+Y)
  const col = [[3, 12, 18, 9, 1.6], [4, 18, 24, 6, 1.5], [5, 24, 29, 3.6, 1.2], [6, 29, 32, 1.6, 1]];
  for (const [axis, ang, off] of [["z", 0, 0], ["z", 22.5, 0], ["z", -22.5, 0], ["x", 22.5, 0], ["x", -22.5, 0]]) {
    for (const [ci, y0, y1, w, th] of col) {
      const rot = { axis, angle: ang, origin: [8, 11, 8] };
      if (axis === "z") m.box([8 - w / 2, y0, 8 - th / 2], [8 + w / 2, y1, 8 + th / 2], ci, rot);
      else m.box([8 - th / 2, y0, 8 - w / 2], [8 + th / 2, y1, 8 + w / 2], ci, rot);
    }
  }
  m.box([5, 11, 5], [11, 20, 11], 7, { axis: "y", angle: 45 });
  m.write();
}
// ------------------------------------------------------------------ heal cross
{
  const m = new Model("heal_cross", ["#ffffff", "#fff6c8", "#b8fff0", { c: "#7cf0d8", a: 210 }, "#ffe27a", "#e8fffb"]);
  const A = 2.2;
  m.box([8 - A, -4, 8 - A], [8 + A, 20, 8 + A], 3); m.box([-4, 8 - A, 8 - A], [20, 8 + A, 8 + A], 3); m.box([8 - A, 8 - A, -4], [8 + A, 8 + A, 20], 3);
  m.box([7, -4.5, 7], [9, 20.5, 9], 0); m.box([-4.5, 7, 7], [20.5, 9, 9], 0); m.box([7, 7, -4.5], [9, 9, 20.5], 0);
  m.box(...sq([8, 8, 8], 3.6), 1, { axis: "y", angle: 45 }); m.box(...sq([8, 8, 8], 3.4), 2, { axis: "x", angle: 45 }); m.box(...sq([8, 8, 8], 3.2), 5, { axis: "z", angle: 45 });
  for (const [x, y, z] of [[8, 22, 8], [8, -6, 8], [22, 8, 8], [-6, 8, 8], [8, 8, 22], [8, 8, -6]]) {
    m.box(...sq([x, y, z], 2), 4, { axis: "y", angle: 45 }); m.box(...sq([x, y, z], 1.7), 0, { axis: "x", angle: 45 });
  }
  m.write();
}
// ------------------------------------------------------------------ crystal shard
{
  const m = new Model("crystal", [{ c: "#bfe6ff", a: 190 }, { c: "#e6f6ff", a: 215 }, "#ffffff", { c: "#8cc8f4", a: 185 }, { c: "#5a9ce0", a: 190 }]);
  const shard = (cx, cz, by, sc, lean, ox) => {
    const layers = [[-12, -6, 3, 3], [-6, 0, 8, 0], [0, 8, 12, 1], [8, 16, 10, 0], [16, 22, 7, 1], [22, 27, 4, 0], [27, 30, 1.6, 2]];
    const rot = (r) => lean ? { axis: "z", angle: lean, origin: [cx, by, cz] } : r;
    for (const [y0, y1, w, ci] of layers) {
      const h = w * sc / 2, a = by + y0 * sc, b = by + y1 * sc;
      const R = lean ? { axis: "z", angle: lean, origin: [cx, by, cz] } : null;
      m.box([cx - h, a, cz - h], [cx + h, b, cz + h], ci, R);
      if (!lean) m.box([cx - h, a, cz - h], [cx + h, b, cz + h], ci, { axis: "y", angle: 45 });
      else m.box([cx - h * .8, a, cz - h * .8], [cx + h * .8, b, cz + h * .8], 1, R);
      m.box([cx - h * .4, a, cz - h * .4], [cx + h * .4, b, cz + h * .4], 2, R);
    }
  };
  shard(8, 8, 2, 1, 0);
  shard(1, 10, -10, .5, 22.5); shard(15, 6, -10, .45, -22.5);
  m.write();
}
