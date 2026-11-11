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
    for (const e of this.els) {   // vanilla model rules: any violation => magenta missing cube
      if (e.rotation && (![-45, -22.5, 0, 22.5, 45].includes(e.rotation.angle) || !["x", "y", "z"].includes(e.rotation.axis))) throw new Error(this.name + ": bad rotation");
      for (const v of [...e.from, ...e.to]) if (!(v >= -16 && v <= 32)) throw new Error(this.name + ": coord out of -16..32");
      for (const f of Object.values(e.faces)) for (const v of f.uv) if (!(v >= 0 && v <= 16)) throw new Error(this.name + ": uv out of 0..16");
    }
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

// ================================================================== skill FX batch 2 (forward = model -Z)
const SGN = 1;  // rotation-about-x direction for limb()/swept parts (verified in previews)
/** curved chain of boxes in the YZ plane (rotation about X). pts = [[y,z],...]; th = z thickness, wd = x width. */
function limb(m, pts, th, wd, ci, x = 8) {
  for (let i = 0; i < pts.length - 1; i++) {
    const [y0, z0] = pts[i], [y1, z1] = pts[i + 1], dy = y1 - y0, dz = z1 - z0;
    const len = Math.hypot(dy, dz) * 1.2, ang = Math.atan2(dz, dy) / D;
    const q = Math.max(-45, Math.min(45, Math.round(ang / 22.5) * 22.5));
    const cy = (y0 + y1) / 2, cz = (z0 + z1) / 2;
    m.box([x - wd / 2, cy - len / 2, cz - th / 2], [x + wd / 2, cy + len / 2, cz + th / 2], ci, q ? { axis: "x", angle: SGN * q, origin: [x, cy, cz] } : null);
  }
}
const noisy2 = (c, n = 10, a) => ({ c, noise: n, a });

// ------------------------------------------------------------------ wind bow (mint/sky translucent, arrow direction -Z)
{
  const m = new Model("wind_bow", ["#ffffff", "#e0fff4", "#8ef0c8", { c: "#6fd0ff", a: 205 }, { c: "#b8fff0", a: 90 }, { c: "#9fffe0", a: 150 }]);
  const cy = 8, half = 11, N = 12, pts = [];
  for (let i = 0; i <= N; i++) { const u = -1 + 2 * i / N; pts.push([cy + half * u, 4 + 8 * u * u]); }
  limb(m, pts, 3.4, 3.4, 4);                                                // translucent aura
  limb(m, pts, 1.8, 1.8, 2); limb(m, pts.map(([y, z]) => [y, z - .5]), .9, 1.2, 0);
  m.box([6.6, cy - 2.2, 0.8], [9.4, cy + 2.2, 4.6], 3);                      // grip wrap
  m.box([6.9, cy - 2.2, 0.5], [9.1, cy + 2.2, 1.3], 1);
  for (const s of [-1, 1]) {                                                 // curled wind tips
    const ty = cy + s * (half - 1.5);
    m.box([6.6, ty - 1.2, 11], [9.4, ty + 1.2, 14.5], 3, { axis: "x", angle: s * -22.5 * SGN });
    m.box([7.2, ty + s * 1.8 - .8, 13], [8.8, ty + s * 1.8 + .8, 17], 0, { axis: "x", angle: s * -45 * SGN });
  }
  limb(m, [[cy - half, 12], [cy - half / 2, 12.5], [cy, 13], [cy + half / 2, 12.5], [cy + half, 12]], .7, .7, 0);   // string
  for (const [x, y, z, a, ci] of [[3, cy + 7, 8, 22.5, 3], [13, cy - 6, 10, -22.5, 2], [2.5, cy - 3, 4, 45, 5], [13.5, cy + 3, 6, -45, 5], [5, cy + 12, 6, 22.5, 2], [11, cy - 12, 6, -22.5, 3]])
    m.box([x - .4, y - 1, z - 2.2], [x + .4, y + 1, z + 2.2], ci, { axis: "y", angle: a });   // drifting wisps
  m.write();
}
// ------------------------------------------------------------------ mana bow (huge purple bow, drawn with a glowing arrow)
{
  const m = new Model("mana_bow", ["#ffffff", "#f0d8ff", "#b86bff", "#7a2fd6", { c: "#c58bff", a: 90 }, "#ff9cf0", "#3a2060", "#ffe27a"]);
  const N = 16, half = 20, cy = 8, pts = [];
  for (let i = 0; i <= N; i++) { const u = -1 + 2 * i / N; pts.push([cy + half * u, -3 + 14 * u * u]); }
  limb(m, pts, 5, 5, 4);
  limb(m, pts, 3.2, 3, 3); limb(m, pts.map(([y, z]) => [y, z - 1]), 1.6, 1.6, 2); limb(m, pts.map(([y, z]) => [y, z - 1.4]), .8, 1, 1);
  m.box([6.4, cy - 4, -6], [9.6, cy + 4, 1.5], 6); m.box([6.9, cy - 4.2, -6.4], [9.1, cy + 4.2, -5], 7);   // grip + gold trim
  for (const dy of [-2.5, 0, 2.5]) m.box([6, cy + dy - .5, -2], [10, cy + dy + .5, 0], 3);
  for (const s of [-1, 1]) {                       // tip crystals + curved horns
    const ty = cy + s * (half - 3);
    m.box([6, ty - 1.6 + s * 1.6, 8], [10, ty + 1.6 + s * 1.6, 14], 5, { axis: "x", angle: s * -22.5 * SGN });
    m.box([6.8, ty + s * 3 - 1, 12], [9.2, ty + s * 3 + 1, 17], 1, { axis: "x", angle: s * -45 * SGN });
    for (const k of [1, 2, 3]) { const dy = k * 6.2, zc = -4 + 14 * Math.pow(dy / half, 2); m.box([7, cy + s * dy - 1, zc - 2.5], [9, cy + s * dy + 1, zc + 2.5], 7); }
  }
  const ty0 = cy - half, ty1 = cy + half, pull = 24;
  limb(m, [[ty0, 11], [(ty0 + cy) / 2, 17.5], [cy, pull], [(ty1 + cy) / 2, 17.5], [ty1, 11]], 1.1, 1.1, 0);   // drawn string
  m.box([7.2, cy - .8, -11], [8.8, cy + .8, pull], 2); m.box([7.6, cy - .4, -11], [8.4, cy + .4, pull], 0);    // arrow
  m.box([6.2, cy - 2.2, -14], [9.8, cy + 2.2, -10], 5, { axis: "z", angle: 45 }); m.box([6.2, cy - 2.2, -14], [9.8, cy + 2.2, -10], 1);
  m.box([6.8, cy - 1, -16], [9.2, cy + 1, -13.5], 0);
  m.box([7.3, cy - 3, 19], [8.7, cy + 3, 27], 5); m.box([2, cy - .5, 20], [14, cy + .5, 27], 2);               // fletching
  m.box([6.2, cy - 2.2, -14], [9.8, cy + 2.2, 26], 4);                                                         // glow aura
  m.write();
}
// ------------------------------------------------------------------ spirit wolf (translucent sky-blue, running, head at -Z)
{
  const m = new Model("spirit_wolf", [{ c: "#d8f4ff", a: 205 }, { c: "#9fdcff", a: 195 }, { c: "#5cb4f5", a: 190 }, { c: "#3a86e0", a: 185 }, { c: "#e8f8ff", a: 100 }, "#ffffff", { c: "#bfeaff", a: 150 }, "#7af0ff"]);
  const B = (a, b, ci, r) => m.box(a, b, ci, r);
  B([6.2, 5.5, 8], [9.8, 11.5, 19], 1);                 // torso
  B([6.6, 6, 6], [9.4, 11.5, 11], 0);                   // chest
  B([6.4, 11, 7.5], [9.6, 12.8, 17], 2);                // back fur ridge
  B([6.2, 5.5, 17], [9.8, 11, 21], 1);                  // haunch
  B([6.6, 9, 3], [9.4, 14, 8.5], 2, { axis: "x", angle: -22.5 * SGN });    // neck
  for (let i = 0; i < 4; i++) B([5.6 + (i % 2) * .4, 9.2 + i * .4, 3.5 + i * 1.5], [10.4 - (i % 2) * .4, 13.8 + i * .4, 5.5 + i * 1.5], 0, { axis: "y", angle: i % 2 ? 22.5 : -22.5 });  // mane fluff
  B([6, 8.5, -3], [10, 14, 3.5], 0);                    // head
  B([6.8, 8.7, -9], [9.2, 11.8, -3], 6);                // snout
  B([7, 7.6, -8.4], [9, 8.9, -3.6], 3);                 // lower jaw
  B([7.4, 11.4, -10], [8.6, 12.4, -8.6], 3);            // nose
  for (const s of [-1, 1]) {
    B([8 + s * 2.4 - .8, 11.2, -2.2], [8 + s * 2.4 + .8, 12.4, -1], 7);   // eyes
    B([8 + s * 2.4 - 1.2, 14, -1.5], [8 + s * 2.4 + 1.2, 17.6, .5], 2, { axis: "z", angle: s * 22.5 });  // ears
    B([8 + s * 2.4 - .5, 14.2, -.6], [8 + s * 2.4 + .5, 16.2, -.1], 5, { axis: "z", angle: s * 22.5 });
    B([8 + s * 1.2 - .3, 7.2, -8], [8 + s * 1.2 + .3, 8.4, -7.2], 5);   // fangs
  }
  // running legs (front pair stretched forward, rear pair back)
  for (const [x, z, a] of [[6.1, 6.5, 22.5], [8.9, 7.5, -22.5], [6.1, 18.5, -22.5], [8.9, 19, 22.5]]) {
    B([x, 0, z], [x + 1.8, 6, z + 2], 2, { axis: "x", angle: a * SGN, origin: [x + .8, 6, z + 1] });
    B([x - .1, -1, z - .6], [x + 1.9, .6, z + 2.4], 3, { axis: "x", angle: a * SGN, origin: [x + .8, 6, z + 1] });
  }
  // tail streaming up and back
  B([7.2, 9, 20], [8.8, 11.5, 25], 2, { axis: "x", angle: -22.5 * SGN }); B([7, 11.5, 24], [9, 14.5, 29], 1, { axis: "x", angle: -45 * SGN }); B([7.3, 14.5, 28], [8.7, 17, 31.5], 0, { axis: "x", angle: -45 * SGN });
  for (const [x, y, z, w, h, l] of [[8, 3, 14, 7, 1.4, 10], [8, 4.5, 24, 5, 1.2, 7], [4.5, 7, 22, 1.4, 3, 8], [11.5, 8, 24, 1.4, 3, 7]])
    B([x - w / 2, y - h / 2, z - l / 2], [x + w / 2, y + h / 2, z + l / 2], 4);   // spirit mist
  B([5, 4.5, 6], [11, 13, 22], 4);                      // aura
  m.write();
}
// ------------------------------------------------------------------ mana hammer (handle +Y, head at the bottom, faces along +-X)
{
  const m = new Model("mana_hammer", ["#ffffff", "#f0d8ff", "#b86bff", "#7a2fd6", { c: "#c58bff", a: 100 }, "#ff9cf0", "#3a2060", "#ffe27a", "#5a3a96"]);
  const hy = -1;
  m.box([-1, hy - 3, 1], [17, hy + 9, 15], 3);                         // head core
  m.box([-1, hy - 3, 1], [17, hy - 1.5, 15], 8); m.box([-1, hy + 7.5, 1], [17, hy + 9, 15], 8);
  for (const s of [-1, 1]) {
    const lo = s < 0, ex = lo ? -1 : 17, d = lo ? -1 : 1;
    const bx = (len) => lo ? [ex - len, ex] : [ex, ex + len];
    m.box([bx(3.5)[0], hy - 3.6, .2], [bx(3.5)[1], hy + 9.6, 15.8], 6);         // metal end caps
    m.box([bx(4.4)[0], hy - 1, 3], [bx(4.4)[1], hy + 7, 13], 2);                // striking faces
    m.box([bx(5)[0], hy + 1, 5], [bx(5)[1], hy + 5, 11], 1);
    m.box([bx(5.4)[0], hy + 2, 6.5], [bx(5.4)[1], hy + 4, 9.5], 0);
  }
  m.box([2.5, hy - 2.5, 0], [13.5, hy + 8.5, 16], 2);                  // mid band
  m.box([6, hy + 1, -.4], [10, hy + 5, 1.2], 5, { axis: "z", angle: 45 }); m.box([6, hy + 1, 14.8], [10, hy + 5, 16.4], 5, { axis: "z", angle: 45 });
  m.box([7, hy + 2, -.8], [9, hy + 4, .4], 0, { axis: "z", angle: 45 }); m.box([7, hy + 2, 15.6], [9, hy + 4, 16.8], 0, { axis: "z", angle: 45 });
  m.box([6.7, 8, 6.7], [9.3, 29, 9.3], 8);                             // handle (+Y)
  for (let y = 10; y < 28; y += 3.4) m.box([6.3, y, 6.3], [9.7, y + 1.2, 9.7], 6);
  m.box([6, 17, 6], [10, 22, 10], 7);
  m.box([5.8, 28, 5.8], [10.2, 31, 10.2], 7, { axis: "y", angle: 45 }); m.box([6.6, 30, 6.6], [9.4, 32, 9.4], 5, { axis: "y", angle: 45 });
  for (const [x, y, z] of [[-2, 16, 8], [18, 20, 8], [8, 14, 0], [8, 12, 16]]) { m.box(...sq([x, y, z], 1.4), 5, { axis: "y", angle: 45 }); m.box(...sq([x, y, z], 1), 0, { axis: "x", angle: 45 }); }
  m.box([-4, hy - 4.5, -1.5], [20, hy + 10.5, 17.5], 4);               // aura
  m.write();
}
// ------------------------------------------------------------------ blue dragon / 청룡 (blue-teal scales, cyan glow, mouth open toward -Z)
{
  const m = new Model("dragon_head", [noisy2("#1f6fd0"), noisy2("#2fb4d8"), noisy2("#12408f"), "#2fe0ff", "#bff8ff", "#ffffff", "#efe6c8", { c: "#4fe6ff", a: 150 }, noisy2("#0a1f4a"), { c: "#9ff4ff", a: 120 }, "#8e0f1c", "#d9323f", noisy2("#0d5aa0"), "#c9b98a"]);
  seed = 33;
  const B = (a, b, ci, r) => m.box(a, b, ci, r);
  const XS = -1, YS = 1;   // rotation direction checks (verified in previews)
  // box along +z from p, elevation el (deg, up = +), cross-section w (x) by h (y); returns end point
  const dseg = (p, el, L, w, h, ci) => {
    B([p[0] - w / 2, p[1] - h / 2, p[2]], [p[0] + w / 2, p[1] + h / 2, p[2] + L], ci, el ? { axis: "x", angle: XS * el, origin: [...p] } : null);
    const r = el * D; return [p[0], p[1] + L * Math.sin(r), p[2] + L * Math.cos(r)];
  };
  // box along +y from p, leaning back (toward +z) by a deg; returns end point
  const hseg = (p, a, L, w, d, ci) => {
    B([p[0] - w / 2, p[1], p[2] - d / 2], [p[0] + w / 2, p[1] + L, p[2] + d / 2], ci, a ? { axis: "x", angle: -XS * a, origin: [...p] } : null);
    const r = a * D; return [p[0], p[1] + L * Math.cos(r), p[2] + L * Math.sin(r)];
  };
  // horizontal sweep outward+back (side s), yaw out deg; returns end
  const yseg = (p, s, out, L, w, h, ci) => {
    B([p[0] - w / 2, p[1] - h / 2, p[2]], [p[0] + w / 2, p[1] + h / 2, p[2] + L], ci, out ? { axis: "y", angle: YS * s * out, origin: [...p] } : null);
    const r = out * D; return [p[0] + s * L * Math.sin(r), p[1], p[2] + L * Math.cos(r)];
  };
  const JA = 45, J = [8, 9, 9];
  const jaw = { axis: "x", angle: XS * JA, origin: J };
  // ---- skull + brow
  B([4.6, 9, 8], [11.4, 14, 18.5], 0);                // skull
  B([5, 10, 2.5], [11, 13.6, 9.5], 0);                // mid-head / brow block
  B([5.4, 13.4, 5], [10.6, 14.6, 17], 12);            // dark crown
  B([6.6, 14, 8], [9.4, 15, 16], 2);                  // crown ridge
  // ---- long narrow snout, flared nose
  B([6, 9.8, -1], [10, 12.6, 9], 0);
  B([6.4, 10, -7.5], [9.6, 12.4, -.8], 0);
  B([6.2, 12.2, -6], [9.8, 12.9, 2], 12);             // snout scale plate
  for (const z of [-5.5, -3, -.5, 2, 4.5]) B([6.5, 12.7, z], [9.5, 13.3, z + .8], 2);
  B([5.4, 10.3, -9.4], [10.6, 12.9, -6.8], 1);        // flared nose pad
  B([7, 12.8, -9.2], [9, 14, -4.5], 2);               // nose bump
  B([6.9, 13.8, -8.6], [9.1, 14.8, -5.5], 1);
  for (const s of [-1, 1]) {
    B([8 + s * 1.9 - .55, 11, -9.6], [8 + s * 1.9 + .55, 12.1, -8.8], 8);        // nostrils
    B([8 + s * 3 - .5, 10.6, -8.4], [8 + s * 3 + .5, 12.2, -6.8], 1, { axis: "z", angle: s * 22.5 });   // nose flares
  }
  B([6.1, 9.4, -7.5], [9.9, 10.1, 9], 8);             // gum line
  B([6.3, 9, -5], [9.7, 9.6, 8.5], 10);               // mouth roof (red)
  // ---- eyes with angry brows
  for (const s of [-1, 1]) {
    B([8 + s * 3.3 - 1.9, 12.6, 2.6], [8 + s * 3.3 + 1.9, 13.9, 9.4], 2, { axis: "z", angle: s * -22.5 });    // heavy brow ridge
    B([8 + s * 3.3 - 1.4, 13.6, 3.4], [8 + s * 3.3 + 1.4, 14.6, 7.5], 12, { axis: "z", angle: s * -22.5 });
    B([8 + s * 3.2 - 1.4, 10.8, 1.9], [8 + s * 3.2 + 1.4, 12.6, 3.6], 9);       // glow halo
    B([8 + s * 3.2 - 1, 11.2, 1.5], [8 + s * 3.2 + 1, 12.2, 2.9], 3, { axis: "z", angle: s * 22.5 });         // eye
    B([8 + s * 3.2 - .45, 11.5, 1.3], [8 + s * 3.2 + .45, 12, 2.2], 5);          // hot core
    B([8 + s * 4.2 - .5, 11.2, 2.4], [8 + s * 4.2 + .5, 12.4, 4.4], 3);          // side glint
  }
  // ---- fangs on upper jaw
  for (const s of [-1, 1]) {
    B([8 + s * 1.9 - .55, 6.6, -6.2], [8 + s * 1.9 + .55, 9.8, -5.1], 6);        // big canine
    B([8 + s * 1.9 - .3, 6.2, -6], [8 + s * 1.9 + .3, 7.2, -5.4], 5);
    B([8 + s * 2.3 - .5, 6.8, .6], [8 + s * 2.3 + .5, 9.8, 1.6], 6);
    B([8 + s * 2.3 - .3, 6.4, .8], [8 + s * 2.3 + .3, 7.4, 1.4], 5);
    for (const z of [-3.6, -1.6, 3.2, 5.4, 7.2]) B([8 + s * 2.4 - .35, 8.2, z], [8 + s * 2.4 + .35, 9.8, z + .8], 5);
    B([8 + s * 1.0 - .3, 8.4, -6.8], [8 + s * 1.0 + .3, 9.8, -6.1], 5);
  }
  // ---- lower jaw (dropped wide), all rotated about the hinge
  B([5.9, 6.6, -5], [10.1, 8.8, 9.5], 0, jaw);
  B([6.2, 5.9, -4.4], [9.8, 6.9, 8.5], 1, jaw);       // belly plates
  B([6.4, 6.4, -5.6], [9.6, 8.8, -4.4], 0, jaw);      // chin
  B([6.3, 8.7, -5.2], [9.7, 9.2, 8], 10, jaw);        // inner mouth red
  B([7, 8.9, -3.5], [9, 9.7, 6], 11, jaw);            // tongue
  B([7.4, 9.2, -5], [8.6, 9.9, -2.4], 11, jaw);
  for (const s of [-1, 1]) {
    B([8 + s * 1.8 - .5, 8.8, -5.6], [8 + s * 1.8 + .5, 11.8, -4.6], 6, jaw);    // lower tusks
    B([8 + s * 1.8 - .3, 11, -5.4], [8 + s * 1.8 + .3, 12.2, -4.8], 5, jaw);
    B([8 + s * 2.2 - .45, 8.8, 1], [8 + s * 2.2 + .45, 10.8, 1.9], 6, jaw);
    for (const z of [-2.6, 3.4, 5.8]) B([8 + s * 2.4 - .35, 8.8, z], [8 + s * 2.4 + .35, 10, z + .8], 5, jaw);
  }
  // ---- spiky beard under the jaw (in jaw frame)
  for (const [x, z, len] of [[0, -6, 3.6], [-1.6, -3.5, 3], [1.6, -3.5, 3], [0, -1.5, 3.8], [-2.2, 1, 2.6], [2.2, 1, 2.6], [0, 3.5, 3.2], [-1.8, 5.5, 2.4], [1.8, 5.5, 2.4]])
    B([8 + x - .45, 6 - len, z], [8 + x + .45, 6.6, z + .9], x === 0 ? 6 : 4, jaw);
  // ---- throat glow (breath source)
  B([6.4, 7.4, 4], [9.6, 9.6, 8.8], 7); B([6.9, 7.9, 5], [9.1, 9.1, 7.6], 3);
  // ---- whisker barbels from the upper lip
  for (const s of [-1, 1]) {
    let p = [8 + s * 3.4, 10.2, -5.5];
    p = yseg(p, s, 45, 6, .6, .6, 4); p[1] -= .6; p = yseg(p, s, 22.5, 6, .5, .5, 3); p[1] -= .8; p = yseg(p, s, 22.5, 5, .4, .4, 9);
    let q = [8 + s * 3.2, 11.2, -2]; q = yseg(q, s, 22.5, 6, .5, .5, 9); q[1] += .6; q = yseg(q, s, 45, 5, .4, .4, 3);
  }
  // ---- cheek mane fins (sweep back and out)
  for (const s of [-1, 1]) {
    B([8 + s * 4.9 - .3, 8.4, 9], [8 + s * 4.9 + .3, 13.2, 13.6], 12);
    yseg([8 + s * 5.2, 12.2, 10], s, 22.5, 7.5, .5, 2.4, 9);
    yseg([8 + s * 5.2, 10.4, 11], s, 45, 6.5, .5, 1.8, 3);
    yseg([8 + s * 5.2, 8.6, 11.5], s, 45, 5, .5, 1.6, 7);
    yseg([8 + s * 5.2, 13.6, 12], s, 22.5, 6, .5, 1.6, 9);
    yseg([8 + s * 5.2, 11.4, 10.5], s, 22.5, 5.5, .5, 1.4, 3); yseg([8 + s * 5.2, 9.6, 11], s, 45, 4, .5, 1.2, 9);
    B([8 + s * 5.4 - .3, 7.2, 5], [8 + s * 5.4 + .3, 10, 9], 1, { axis: "z", angle: s * 22.5 });                  // jaw fin
  }
  // ---- two long swept-back antler horns with tines
  for (const s of [-1, 1]) {
    let p = [8 + s * 2.8, 13.8, 12.5];
    p = hseg(p, 22.5, 3.6, 2.2, 2.2, 6);
    hseg([p[0], p[1] - 1.4, p[2] - .2], 22.5, 3.2, .7, .7, 5);
    p = hseg(p, 45, 3.8, 1.9, 1.9, 6); p[0] += s * .5;
    hseg([p[0], p[1] - 1.2, p[2]], 22.5, 3, .6, .6, 5);
    p = dseg(p, 22.5, 4.6, 1.6, 1.6, 6); p[0] += s * .6;
    hseg([p[0], p[1] - .6, p[2] - 3], 22.5, 2.6, .6, .6, 5);
    p = dseg(p, 22.5, 4.4, 1.2, 1.2, 13); p[0] += s * .4;
    p = dseg(p, 0, 3.4, .8, .8, 13);
  }
  // ---- flame-like mane crest spikes along the back of the head and nape
  for (let i = 0; i < 8; i++) {
    const z = 8.5 + i * 1.8, el = i < 3 ? 45 : 22.5, hgt = 2.6 - i * .12;
    dseg([8, 15 - (i > 4 ? (i - 4) * .5 : 0), z], el, 4.8 + (i % 3), .8, hgt, i % 2 ? 9 : 3);
    for (const s of [-1, 1]) if (i % 2 === 0) dseg([8 + s * 2.3, 14.2, z + .6], el, 4, .6, 1.8, 7);
  }
  // ---- S-curved scaled neck going back/down
  let np = [8, 11.4, 16.4];
  const neck = [[-22.5, 4.5, 8.2, 7.6], [-45, 4.5, 8, 7.4], [-22.5, 3.8, 7.6, 7], [0, 2, 7.2, 6.8]];
  for (const [el, L, w, h] of neck) {
    const r = el * D;
    dseg(np, el, L + 1.2, w, h, 0);
    dseg([np[0], np[1] - h * .46 * Math.cos(r), np[2] + h * .46 * Math.sin(r)], el, L + .6, w * .55, h * .3, 1);       // belly plates
    for (let k = 0.5; k < L; k += 1.7) {
      const c = [np[0], np[1] + k * Math.sin(r), np[2] + k * Math.cos(r)];
      dseg(c, el, .7, w + .5, h + .5, 12);                                                                       // scale bands
      dseg([c[0], c[1] + (h / 2 + .2) * Math.cos(r), c[2] - (h / 2 + .2) * Math.sin(r)], el, .8, .6, 1.6, k % 3.4 < 1.7 ? 3 : 9);   // dorsal fin
    }
    np = [np[0], np[1] + L * Math.sin(r), np[2] + L * Math.cos(r)];
  }
  m.write();
}
// ------------------------------------------------------------------ white tiger / 백호 claw (white-grey striped tiger head, icy-blue claw glow at the back, three claw rakes in front)
{
  const m = new Model("tiger_claw", [noisy2("#e6eaf0", 6), noisy2("#ffffff", 4), "#f4f8ff", "#14141a", { c: "#7fd8ff", a: 150 }, { c: "#c8f2ff", a: 130 }, "#6fd0ff", "#ffffff", noisy2("#b4bfcf", 6), "#d9566c", "#9c2f45", "#c8707e", "#d8f6ff"]);
  const B = (a, b, ci, r) => m.box(a, b, ci, r);
  const hz = 16;
  const q22 = a => Math.max(-45, Math.min(45, Math.round(a / 22.5) * 22.5));
  const wAt = (i, n) => Math.max(.32, .78 - .5 * (i / Math.max(1, n - 1)));   // taper along the stripe
  // stripe on the x=2.5 / x=13.5 skull side, pts = [y, z]
  function sideStripe(s, pts) {
    const surf = s > 0 ? 13.5 : 2.5, x0 = s > 0 ? surf - .1 : surf - .3, x1 = x0 + .4;
    for (let i = 0; i < pts.length - 1; i++) {
      const [y0, z0] = pts[i], [y1, z1] = pts[i + 1], c = [(y0 + y1) / 2, (z0 + z1) / 2], L = Math.hypot(y1 - y0, z1 - z0) * 1.15 + .3, w = wAt(i, pts.length - 1);
      let a = Math.atan2(y1 - y0, z1 - z0) / D; if (a > 90) a -= 180; if (a <= -90) a += 180;
      if (Math.abs(a) <= 45) { const th = q22(-a); B([x0, c[0] - w / 2, c[1] - L / 2], [x1, c[0] + w / 2, c[1] + L / 2], 3, th ? { axis: "x", angle: th } : null); }
      else { let b = Math.atan2(z1 - z0, y1 - y0) / D; if (b > 90) b -= 180; if (b <= -90) b += 180; const th = q22(b); B([x0, c[0] - L / 2, c[1] - w / 2], [x1, c[0] + L / 2, c[1] + w / 2], 3, th ? { axis: "x", angle: th } : null); }
    }
  }
  // stripe in the xy plane on a z-surface, pts = [x, y]
  function xyStripe(pts, zs) {
    for (let i = 0; i < pts.length - 1; i++) {
      const [x0, y0] = pts[i], [x1, y1] = pts[i + 1], c = [(x0 + x1) / 2, (y0 + y1) / 2, zs - .1];
      m.tan("xy", c, [Math.hypot(x1 - x0, y1 - y0) * 1.15 + .3, wAt(i, pts.length - 1), .5], Math.atan2(y1 - y0, x1 - x0) / D, 3);
    }
  }
  const frontStripe = (s, pts) => xyStripe(pts.map(([d, y]) => [8 + s * d, y]), hz - 2);   // [dx, y] on the front of the skull (z = hz-2)
  const frontPlate = pts => xyStripe(pts, hz - 2.6);                                       // [x, y] on the forehead plate
  // stripe on top of the brow (y = 14), pts = [x, z]
  function topStripe(pts) {
    for (let i = 0; i < pts.length - 1; i++) {
      const [x0, z0] = pts[i], [x1, z1] = pts[i + 1], c = [(x0 + x1) / 2, (z0 + z1) / 2], L = Math.hypot(x1 - x0, z1 - z0) * 1.15 + .3, w = wAt(i, pts.length - 1), th = q22(Math.atan2(x1 - x0, z1 - z0) / D);
      B([c[0] - w / 2, 13.85, c[1] - L / 2], [c[0] + w / 2, 14.3, c[1] + L / 2], 3, th ? { axis: "y", angle: th } : null);
    }
  }
  // skull + heavy brow
  B([2.5, 3, hz - 2], [13.5, 13, hz + 8], 0);
  B([3, 12.6, hz - 1], [13, 14, hz + 7], 8);
  B([5.2, 11.2, hz - 2.6], [10.8, 13.6, hz - 1.8], 1);            // forehead
  // muzzle, nose, snarling lips
  B([4.8, 3.4, hz - 5.8], [11.2, 8, hz - 1.5], 1);
  B([5.6, 5, hz - 6.3], [10.4, 7.6, hz - 5.4], 2);                // whisker pads
  B([6.9, 7, hz - 6.6], [9.1, 8.5, hz - 5.4], 11);                // nose
  B([7.2, 7.3, hz - 6.9], [8.8, 8, hz - 6.4], 10);
  B([6, 8.4, hz - 3.2], [7.2, 9.6, hz - 2.2], 3, { axis: "z", angle: 22.5 }); B([8.8, 8.4, hz - 3.2], [10, 9.6, hz - 2.2], 3, { axis: "z", angle: -22.5 });   // snarl wrinkles
  B([4.6, 2.6, hz - 5.9], [11.4, 3.8, hz - 4.9], 2);              // upper lip line
  // open mouth: pink inside, long white fangs, lower jaw dropped
  B([5.2, .6, hz - 5.2], [10.8, 3.4, hz - .8], 9);                // mouth interior
  B([6.6, 1.2, hz - 4.6], [9.4, 2.2, hz - 2.4], 10);              // tongue
  const jaw = { axis: "x", angle: -22.5, origin: [8, 3.6, hz + 1] };
  B([4.8, -.8, hz - 5.6], [11.2, 1.8, hz - .6], 1, jaw); B([5.4, -1.2, hz - 5.2], [10.6, -.4, hz - 1.4], 8, jaw);
  for (const s of [-1, 1]) {
    B([8 + s * 2.4 - .55, 1.2, hz - 5.4], [8 + s * 2.4 + .55, 3.6, hz - 4.3], 7);                                // upper fangs
    B([8 + s * 2.4 - .3, .4, hz - 5.2], [8 + s * 2.4 + .3, 1.6, hz - 4.6], 7);
    B([8 + s * 1.4 - .3, 2, hz - 5.6], [8 + s * 1.4 + .3, 3.2, hz - 5], 7);
    B([8 + s * 2 - .4, -.6, hz - 5.6], [8 + s * 2 + .4, 1.4, hz - 4.8], 7, jaw);                                // lower fangs
    B([8 + s * 4.6 - .35, 1.4, hz - 5], [8 + s * 4.6 + .35, 3.2, hz - 4.3], 7);
    // eyes: glowing icy-blue iris, dark outline, white highlight, angry slanted brow, faint blue glow
    const ex = 8 + s * 3.9, ey = 8.3, ez = hz - 2.2, er = { axis: "z", angle: s * 22.5, origin: [ex, ey, ez] };
    B([ex - 2.2, ey - 1.5, ez - .38], [ex + 2.2, ey + 1.5, ez - .1], 4);                                        // glow layer
    B([ex - 1.75, ey - .95, ez - .4], [ex + 1.75, ey + .95, ez + .4], 3, er);                                    // dark outline
    B([ex - 1.35, ey - .65, ez - .65], [ex + 1.35, ey + .65, ez - .35], 6, er);                                  // iris
    B([ex - .55, ey - .6, ez - .85], [ex + .55, ey + .6, ez - .6], 12, er);                                       // bright inner iris
    B([ex - .17, ey - .62, ez - .98], [ex + .17, ey + .62, ez - .8], 3, er);                                     // slit pupil
    B([ex + s * -.75 - .24, ey + .3 - .24, ez - 1.12], [ex + s * -.75 + .24, ey + .3 + .24, ez - .92], 7);        // highlight pixel
    B([ex - 2.6, ey + 1.1, ez - .7], [ex + 2.6, ey + 2.3, ez - .15], 3, { axis: "z", angle: s * 22.5, origin: [ex, ey + 1.7, ez] });   // angry brow
    B([ex - 1.7, ey + 1.1, ez - 1.0], [ex + 1.7, ey + 1.6, ez - .6], 3, { axis: "z", angle: s * 22.5, origin: [ex, ey + 1.4, ez] });
    // ears
    B([8 + s * 5 - 1.8, 13, hz + 1], [8 + s * 5 + 1.8, 17, hz + 3.5], 0, { axis: "z", angle: s * 22.5 });
    B([8 + s * 5 - .8, 13.4, hz + .8], [8 + s * 5 + .8, 16, hz + 1.1], 9, { axis: "z", angle: s * 22.5 });
    B([8 + s * 5 - 1.8, 13.4, hz + 3.3], [8 + s * 5 + 1.8, 16.6, hz + 3.8], 3, { axis: "z", angle: s * 22.5 });
    // cheek ruff (spiky fur) + side stripes
    B([8 + s * 6.4 - .8, 3, hz + .4], [8 + s * 6.4 + .8, 9, hz + 5], 2, { axis: "y", angle: s * -22.5 });
    B([8 + s * 7.4 - .7, 3.4, hz + 2], [8 + s * 7.4 + .7, 7.4, hz + 6.4], 8, { axis: "y", angle: s * -45 });
    // ---- tiger stripes: thin tapered wavy segments hugging the fur
    const sx = s * 5.5 + 8;
    sideStripe(s, [[9.8, 14.4], [9.3, 16], [9.8, 17.6], [9.1, 19.2], [9.4, 20.8], [8.6, 22]]);
    sideStripe(s, [[7.9, 14.4], [7.3, 16], [7.6, 17.6], [6.7, 19], [6.9, 20.4]]);
    sideStripe(s, [[5.9, 14.4], [5.4, 16], [5.6, 17.4], [4.8, 18.6]]);
    sideStripe(s, [[11.6, 15.4], [11.2, 17], [11.7, 18.8], [11, 20.6], [11.2, 22]]);
    sideStripe(s, [[4, 15], [3.6, 16.2]]);
    frontStripe(s, [[3.2, 6.9], [4.2, 6.6], [5, 5.9], [5.7, 5.2]]);
    frontStripe(s, [[3.2, 5.3], [4.2, 5], [4.9, 4.2]]);
    frontStripe(s, [[3.4, 3.9], [4.2, 3.5]]);
    frontStripe(s, [[3.7, 10.8], [4.4, 11.6], [4.8, 12.6]]);
    for (const [pts] of [[[[8 + s * 2.2, 15.2], [8 + s * 2.5, 17], [8 + s * 2, 19], [8 + s * 2.4, 21]]], [[[8 + s * 4.2, 15.2], [8 + s * 4.6, 17], [8 + s * 4, 18.8]]]]) topStripe(pts);
    // forehead: classic 王 mark above the brow (centre line + three bars narrowing downward, bent like real markings)
    frontPlate([[8 + s * .5, 13], [8 + s * 1.9, 13.1], [8 + s * 3.1, 12.7], [8 + s * 3.9, 12.1]]);
    frontPlate([[8 + s * .5, 12.1], [8 + s * 1.6, 12.1], [8 + s * 2.5, 11.7]]);
    frontPlate([[8 + s * .5, 11.4], [8 + s * 1.2, 11.3]]);
  }
  frontPlate([[8, 13.5], [8, 12.6], [8, 11.9], [8, 11.3]]);
  topStripe([[8, 15.2], [8.2, 17], [7.9, 19], [8.1, 21], [8, 22.4]]);
  B([-1, 1, hz + 6], [17, 12, hz + 12], 8);               // mane toward the back
  B([1, 3, hz + 10], [15, 11, hz + 15], 4);
  B([-.4, 0, hz + 4], [16.4, 3.5, hz + 9], 2);            // chin ruff

  // three claw slashes: very thin curved scratch marks (white core, faint icy-blue edge), tapering to a point
  for (let k = 0; k < 3; k++) {
    const x0 = 1 + k * 6.5, z0 = -11 + k * 1.2, top = 29 - k * .6, N = 10, Lh = 22 - k;
    let px = x0, py = top;
    for (let i = 0; i < N; i++) {
      const t0 = i / N, t1 = (i + 1) / N, nx = x0 + 11 * t1 + 4 * t1 * t1, ny = top - Lh * t1 + 1.5 * Math.sin(t1 * 3);
      const ang = Math.atan2(ny - py, nx - px) / D, len = Math.hypot(nx - px, ny - py) * 1.2 + .2, c = [(px + nx) / 2, (py + ny) / 2, z0];
      const w = Math.max(.3, 1.1 * (1 - t0) * (1 - .3 * t0));
      m.tan("xy", c, [len, w + 1, .3], ang, 4);             // faint icy-blue edge
      m.tan("xy", [c[0], c[1], z0 + .05], [len, w, .45], ang, 5);   // pale body
      m.tan("xy", [c[0], c[1], z0 + .1], [len, Math.max(.3, w * .5), .6], ang, 7);   // bright white core
      px = nx; py = ny;
    }
  }
  m.write();
}

// ------------------------------------------------------------------ dragon breath: blue fire plume, origin (8,8,4) widening toward -Z to z=-16
{
  const m = new Model("dragon_breath", ["#f0ffff", { c: "#5ae6ff", a: 205 }, { c: "#2a9cff", a: 175 }, { c: "#1a52e0", a: 150 }, "#ffffff", { c: "#1030b0", a: 110 }, { c: "#9ff4ff", a: 150 }, "#c8ffff"]);
  seed = 77;
  const Z0 = 4, ZL = -16, R = [-45, -22.5, 0, 22.5, 45];
  const seg = 3.2, n = 7;
  for (let i = 0; i < n; i++) {
    const zf = Z0 - i * seg, zt = zf - seg * 1.35, d = (i + .5) / n, r = .7 + 7 * Math.pow(d, 1.05);
    const rot = ang => ang ? { axis: "z", angle: ang } : null;
    m.box([8 - r * .8, 8 - r * .8, Math.max(ZL, zt)], [8 + r * .8, 8 + r * .8, zf], 5, rot(R[(i * 2) % 5 === 2 ? 3 : 1 + (i % 2) * 2 - 1 + 1]));
    m.box([8 - r * .6, 8 - r * .6, Math.max(ZL, zt)], [8 + r * .6, 8 + r * .6, zf], 3, rot(i % 2 ? 45 : 22.5));
    m.box([8 - r * .42, 8 - r * .42, Math.max(ZL, zt + .3)], [8 + r * .42, 8 + r * .42, zf], 2, rot(i % 2 ? 22.5 : 45));
    if (i < 6) m.box([8 - r * .27, 8 - r * .27, Math.max(ZL, zt + .5)], [8 + r * .27, 8 + r * .27, zf], 1, rot(i % 2 ? 45 : 0));
    if (i < 4) m.box([8 - r * .15, 8 - r * .15, zt + .8], [8 + r * .15, 8 + r * .15, zf], 0);
  }
  // flame tongues: licking, elongated, outward-offset chunks
  for (let j = 0; j < 44; j++) {
    const z = Z0 - 2 - rnd() * 17, d = (Z0 - z) / 20, rad = (.5 + 7.5 * d) * (.4 + rnd() * .8), th = rnd() * Math.PI * 2;
    const cx = 8 + rad * Math.cos(th), cy = 8 + rad * Math.sin(th), L = 2.5 + rnd() * 4, w = .6 + rnd() * 1.4 + d * 1.2;
    const ci = d < .3 ? [6, 1, 1][j % 3] : d < .6 ? [1, 2, 6][j % 3] : [2, 3, 3, 5][j % 4];
    const zz0 = Math.max(ZL, z - L / 2), zz1 = Math.min(32, z + L / 2);
    m.box([cx - w / 2, cy - w / 2, zz0], [cx + w / 2, cy + w / 2, zz1], ci, { axis: ["x", "y", "z"][j % 3], angle: R[(j * 3 + 1) % 5] || 22.5 });
  }
  // sparks
  for (let j = 0; j < 9; j++) {
    const z = -2 - rnd() * 14, d = (Z0 - z) / 20, rad = (2 + 7 * d) * (.4 + rnd() * .8), th = rnd() * Math.PI * 2, s2 = .3 + rnd() * .35;
    const cx = 8 + rad * Math.cos(th), cy = 8 + rad * Math.sin(th);
    m.box([cx - s2, cy - s2, z - s2], [cx + s2, cy + s2, z + s2], j % 2 ? 4 : 7, { axis: "z", angle: 45 });
  }
  m.write();
}
// ================================================================== skill FX batch 3
// ------------------------------------------------------------------ lightning bolt (vertical, zig-zag in the XY plane, y 0..32)
{
  const m = new Model("lightning_bolt", ["#ffffff", "#fffbd0", "#ffe94a", { c: "#fff27a", a: 140 }, { c: "#9ff4ff", a: 110 }, { c: "#c8fbff", a: 70 }, "#e8ffff"]);
  const P = [[9, 31], [12.5, 25], [5.5, 19.5], [11, 13], [4.5, 6.5], [8, 0.8]];
  for (let i = 0; i < P.length - 1; i++) {
    const [x0, y0] = P[i], [x1, y1] = P[i + 1], c = [(x0 + x1) / 2, (y0 + y1) / 2], a = Math.atan2(y1 - y0, x1 - x0) / D, len = Math.hypot(x1 - x0, y1 - y0) * (i === 0 ? 1.05 : 1.2);
    m.tan("xy", [c[0], c[1], 8], [len, 3.6, 2.4], a, 5);       // pale cyan outer glow
    m.tan("xy", [c[0], c[1], 8], [len, 2.3, 1.8], a, 3);       // yellow glow
    m.tan("xy", [c[0], c[1], 8], [len, 1.4, 1.3], a, 2);       // yellow rim
    m.tan("xy", [c[0], c[1], 8], [len, .8, 1], a, 0);       // white core
  }
  for (const [x, y] of P.slice(1, -1)) { m.box(...sq([x, y, 8], 1.05), 1, { axis: "z", angle: 45 }); m.box(...sq([x, y, 8], .6), 0, { axis: "z", angle: 22.5 }); }
  for (const [x, y, a] of [[12.2, 24, 22.5], [5.2, 17.5, -22.5], [11, 11.5, 22.5], [5, 5, -22.5]]) {   // small side forks
    m.tan("xy", [x + (a > 0 ? 2.2 : -2.2), y - 2.4, 8], [4.4, .9, 1.2], a > 0 ? -50 : 50, 4);
    m.tan("xy", [x + (a > 0 ? 2.2 : -2.2), y - 2.4, 8], [3.6, .5, .7], a > 0 ? -50 : 50, 0);
  }
  m.box([4.5, 0, 6], [11.5, 1, 10], 3); m.box([6, 0, 4.5], [10, 1, 11.5], 4);                             // impact flash
  for (const [x, y, z] of [[2.5, 4, 8], [13.5, 9, 8], [3, 24, 8.5], [14, 19, 7.5], [8, 31, 8]]) m.box(...sq([x, y, z], .55), 6, { axis: "z", angle: 45 });
  m.write();
}
// ------------------------------------------------------------------ rock arrow (earth spirit, points to model -Z)
{
  const nz = (c, n = 12) => ({ c, noise: n });
  const m = new Model("rock_arrow", [nz("#8a5a30"), nz("#5e3c20"), nz("#85848a", 14), nz("#55534f", 12), nz("#6e5238"), nz("#4f7a35", 14), nz("#7fa84a", 12), nz("#b09a78", 12), nz("#a07040"), { c: "#d8f0b0", a: 90 }]);
  seed = 21;
  m.box([7.1, 7.1, -3], [8.9, 8.9, 26], 0); m.box([7.4, 7.4, -3], [8.6, 8.6, 26], 4);                    // wooden shaft
  for (const z of [3, 9, 15]) m.box([6.5, 6.5, z], [9.5, 9.5, z + 2.2], 3, { axis: "z", angle: 45 });      // stone bands
  m.box([7, 7, -16], [9, 9, -11], 7); m.box([6.2, 6.2, -12], [9.8, 9.8, -7], 2, { axis: "z", angle: 45 }); // stone tip
  m.box([6, 6, -9], [10, 10, -4], 2); m.box([6, 6, -9], [10, 10, -4], 3, { axis: "z", angle: 45 });
  const chunks = [[4.2, 9.2, -7, 3, 2], [11.8, 6.6, -6, 3.2, 3], [7.6, 11.6, -5, 2.8, 2], [8.4, 4.4, -8, 2.8, 3], [11.2, 10.8, -9.5, 2.2, 2], [4.8, 5.6, -10, 2.2, 3], [8, 8, -3, 4.6, 2], [5, 7.4, -2, 2.6, 7], [11.4, 8.6, -1.5, 2.6, 7]];
  chunks.forEach(([x, y, z, s, ci], i) => { m.box(...sq([x, y, z], s / 2), ci, { axis: ["y", "x", "z"][i % 3], angle: [22.5, 45, -22.5][i % 3] }); m.box(...sq([x, y, z], s / 2 - .35), i % 2 ? 3 : 2, { axis: "y", angle: 45 }); });
  for (const [x, y, z, w] of [[8, 11.4, -4, 2.6], [5, 10.4, -7.5, 1.8], [11.6, 8, -7, 1.8], [8.6, 3.3, -8, 1.6], [8, 8, -0.4, 2.2]]) { m.box([x - w / 2, y - .5, z - w / 2], [x + w / 2, y + .5, z + w / 2], 5); m.box([x - w / 4, y - .2, z - w / 4], [x + w / 4, y + .8, z + w / 4], 6); }   // moss
  m.box([4, 4, -10], [12, 12, -1], 9);                                                                   // spirit aura
  for (const [ang, ci] of [[0, 6], [45, 5], [-45, 5]]) m.box([7.6, 3, 22], [8.4, 13, 29], ci, { axis: "z", angle: ang });   // leaf fletching
  m.box([3, 7.6, 22], [13, 8.4, 29], 6);
  m.box([6.9, 6.9, 28.6], [9.1, 9.1, 30.4], 7);
  for (const [x, y, z, s, ci, a] of [[2.5, 12.5, -3, 1.6, 2, 22.5], [13.5, 3.8, -4.5, 1.4, 3, 45], [3.2, 3.5, 2, 1.2, 7, -22.5], [13, 12.6, 3.5, 1.5, 2, 22.5], [8, 14.8, -10, 1.1, 3, 45], [1.8, 8, -12, 1, 7, 22.5], [14.4, 8, 8, 1.1, 3, -22.5]])
    m.box(...sq([x, y, z], s / 2), ci, { axis: "y", angle: a });                                         // floating pebbles
  m.write();
}
// ------------------------------------------------------------------ water arrow (thin high-pressure spear, points to model -Z)
{
  const m = new Model("water_arrow", ["#ffffff", { c: "#d8fbff", a: 220 }, { c: "#4fd8ff", a: 200 }, { c: "#2a8cf0", a: 190 }, { c: "#1a5ccc", a: 210 }, "#eaffff", { c: "#7fe8ff", a: 90 }, { c: "#bff4ff", a: 150 }]);
  const oct = (z0, z1, s, ci) => { m.box([8 - s / 2, 8 - s / 2, z0], [8 + s / 2, 8 + s / 2, z1], ci); m.box([8 - s / 2, 8 - s / 2, z0], [8 + s / 2, 8 + s / 2, z1], ci, { axis: "z", angle: 45 }); };
  oct(-16, -12, .7, 0); oct(-12, -6, 1.5, 1); oct(-6, 2, 2.4, 2); oct(2, 14, 2.6, 3);
  m.box([7.55, 7.55, -15], [8.45, 8.45, 22], 0);
  m.box([7.3, 7.3, 14], [8.7, 8.7, 26], 4);
  for (const [z, r] of [[-3, 1.9], [3, 2.5], [9.5, 2.9], [16, 2.5]]) {      // foam rings
    for (const rot of [null, { axis: "z", angle: 45 }]) {
      m.box([8 - r, 8 - r, z], [8 + r, 8 - r + .9, z + 1.4], 5, rot); m.box([8 - r, 8 + r - .9, z], [8 + r, 8 + r, z + 1.4], 5, rot);
      m.box([8 - r, 8 - r, z], [8 - r + .9, 8 + r, z + 1.4], 5, rot); m.box([8 + r - .9, 8 - r, z], [8 + r, 8 + r, z + 1.4], 5, rot);
    }
  }
  for (let rib = 0; rib < 2; rib++) for (let k = 0; k < 22; k++) {          // spiral wake
    const t = k / 21, z = 12 + t * 19, ph = (t * 2.4 * 360 + rib * 180) * D, r = 1.8 + 4 * t;
    const c = [8 + r * Math.cos(ph), 8 + r * Math.sin(ph), z], s = 1.2 - .6 * t;
    m.box(...sq(c, s / 2 + .25), rib ? 7 : 2, { axis: "z", angle: [0, 22.5, 45, -22.5][k % 4] });
  }
  m.box([6.4, 6.4, 12], [9.6, 9.6, 31], 6);
  for (const [x, y, z] of [[4.4, 11, 20], [11.8, 4.8, 24], [12, 11.2, 29], [4, 5, 28]]) m.box(...sq([x, y, z], .5), 5, { axis: "y", angle: 45 });
  m.write();
}
////// ------------------------------------------------------------------ phoenix (upright regal firebird, wings raised in a big V, head toward model -Z, long tail curling below)
{
  const nz = (c, n = 8) => ({ c, noise: n });
  const m = new Model("phoenix", [nz("#9c0f1c"), nz("#e8321c"), nz("#ff7a1f"), nz("#ffb02e"), nz("#ffe14a"), "#fff6b0", "#ffffff", "#2a0c10", { c: "#ff8a2a", a: 100 }, { c: "#ffd23a", a: 140 }, "#b9bfd0", { c: "#fff2a8", a: 120 }]);
  seed = 61;
  const B = (a, b, ci, r) => m.box(a, b, ci, r);
  const qa = a => Math.max(-45, Math.min(45, Math.round(a / 22.5) * 22.5));
  // one ribbon segment between two 3D points (yz plane when mostly vertical/longitudinal, xz plane when mostly horizontal)
  function seg3(p0, p1, w, t, ci) {
    const dx = p1[0] - p0[0], dy = p1[1] - p0[1], dz = p1[2] - p0[2];
    const c = [(p0[0] + p1[0]) / 2, (p0[1] + p1[1]) / 2, (p0[2] + p1[2]) / 2], len = Math.hypot(dx, dy, dz) * 1.2 + .4;
    if (Math.abs(dz) < Math.hypot(dx, dy) * .6) { m.tan("xy", c, [Math.hypot(dx, dy) * 1.2 + .4, w, t], Math.atan2(dy, dx) / D, ci); return; }
    if (Math.abs(dy) < Math.hypot(dx, dz) * .5) { m.tan("xz", c, [len, w * .8, Math.max(t, w * .6)], Math.atan2(dz, dx) / D, ci); return; }
    let ang = (((Math.atan2(dy, dz) / D) % 180) + 180) % 180; if (ang > 90) ang -= 180;
    const swap = Math.abs(ang) > 45, q = qa(swap ? ang - 90 * Math.sign(ang) : ang);
    const d = swap ? [w, len, t] : [w, t, len];
    B([c[0] - d[0] / 2, c[1] - d[1] / 2, c[2] - d[2] / 2], [c[0] + d[0] / 2, c[1] + d[1] / 2, c[2] + d[2] / 2], ci, q ? { axis: "x", angle: -q, origin: c } : null);
  }
  function ribbon(pts, w0, th, cols, glow = 8) {
    const n = pts.length - 1;
    for (let i = 0; i < n; i++) {
      const t = i / n, w = Math.max(.5, w0 * (1 - .8 * Math.pow(t, 1.4)));
      seg3(pts[i], pts[i + 1], w, th, cols[Math.min(cols.length - 1, Math.floor(t * cols.length))]);
      if (glow >= 0) seg3(pts[i], pts[i + 1], w * 1.9, th * .45, glow);
    }
    const e = pts[n]; B([e[0] - .3, e[1] - .3, e[2] - .3], [e[0] + .3, e[1] + .3, e[2] + .3], 5, { axis: "y", angle: 45 });
  }
  // ---- wings: a fan of flame-shaped feathers rising in a V; tips point up and out
  const FE = [[10, 15, 22, 0.0], [22, 17, 24, 0.5], [34, 20, 24, 0.0], [46, 22, 22, 0.5], [57, 22, 20, 0.0], [68, 20, 16, 0.5], [78, 17, 12, 0.0], [88, 13, 8, 0.5], [100, 9, 4, 0.0]];
  for (const s of [-1, 1]) {
    FE.forEach(([p0, len, bend, zo], i) => {
      const n = 6, step = len / n; let x = 3 + i * .15, y = 10 + i * .25; const z0 = 1.4 + zo;
      for (let k = 0; k < n; k++) {
        const t = (k + .5) / n, phi = p0 + bend * t, r = phi * D, cx = x + Math.cos(r) * step / 2, cy = y + Math.sin(r) * step / 2;
        const w = 4.2 * (1 - .8 * Math.pow(t, 1.4)), z = z0 + t * 2.2, ci = k === n - 1 ? 5 : t < .25 ? (i % 2 ? 0 : 1) : t < .55 ? (i % 2 ? 1 : 2) : t < .82 ? (i % 2 ? 2 : 3) : 4;
        m.tan("xy", [8 + s * cx, cy, z], [step * 1.28, w, .9], s > 0 ? phi : 180 - phi, ci);
        x += Math.cos(r) * step; y += Math.sin(r) * step;
      }
    });
    // bright covert layer in front of the flight feathers
    [[30, 11, 22], [52, 12, 20], [68, 11, 16], [88, 9, 10]].forEach(([p0, len, bend]) => {
      const n = 4, step = len / n; let x = 3.2, y = 10.6;
      for (let k = 0; k < n; k++) {
        const t = (k + .5) / n, phi = p0 + bend * t, r = phi * D, cx = x + Math.cos(r) * step / 2, cy = y + Math.sin(r) * step / 2;
        m.tan("xy", [8 + s * cx, cy, .6 + t * 1.2], [step * 1.3, 2.1 * (1 - .6 * t), .8], s > 0 ? phi : 180 - phi, [3, 4, 4, 5][k]);
        x += Math.cos(r) * step; y += Math.sin(r) * step;
      }
    });
    for (const [phi, d] of [[26, 9], [50, 13], [74, 12]]) m.tan("xy", [8 + s * (3 + Math.cos(phi * D) * d), 10.5 + Math.sin(phi * D) * d, 3.6], [9, 5, .5], s > 0 ? phi : 180 - phi, 8);   // glow behind
    B([8 + s * 3 - 1.6, 9, -.4], [8 + s * 3 + 1.6, 12.4, 3.2], 0);   // shoulder
  }
  // ---- body: upright chest, proud
  B([5.9, 9.5, -2.6], [10.1, 17.5, 3], 1);
  B([6.4, 10, -3.6], [9.6, 16.5, -2.4], 3);
  for (let j = 0; j < 5; j++) B([6.3, 10.2 + j * 1.35, -4.4], [9.7, 11.4 + j * 1.35, -2.7], j % 2 ? 3 : 4, { axis: "x", angle: 22.5 });
  B([6.6, 6.4, .6], [9.4, 10.6, 5.6], 0); B([7, 5.4, 3], [9, 8.4, 7], 1);
  B([6.8, 16.8, -3.4], [9.2, 21, .2], 2); B([7.2, 17, -4], [8.8, 20.4, -2.8], 4);   // neck
  // ---- head: small, fierce, hooked beak, tall flame crest
  B([6.9, 20.4, -5.4], [9.1, 23.2, -1.6], 1); B([7.2, 22.8, -5], [8.8, 23.5, -2], 2);
  B([7.4, 20.8, -8.2], [8.6, 22.2, -5.2], 4); B([7.5, 19.6, -8.5], [8.5, 21.4, -7.4], 5); B([7.7, 19, -8.2], [8.3, 20, -7.5], 6);   // beak, hook
  for (const s of [-1, 1]) {
    // sharp bird-of-prey eye: narrow almond slanted down toward the beak, dark outline, gold sliver, slit pupil, angular brow ridge
    const ex = 8 + s * .78, ey = 21.95, ez = -5.62, er = { axis: "z", angle: s * 22.5, origin: [ex, ey, ez] };
    B([ex - .56, ey - .17, ez - .12], [ex + .56, ey + .17, ez + .2], 7, er);          // dark almond outline
    B([ex - .4, ey - .09, ez - .22], [ex + .4, ey + .11, ez + .1], 4, er);         // gold iris sliver
    B([ex - .46, ey - .04, ez - .3], [ex - .2, ey + .07, ez - .08], 6, er);         // bright inner corner
    B([ex - .07, ey - .12, ez - .32], [ex + .07, ey + .14, ez - .1], 7, er);        // slit pupil
    B([ex - .7, ey + .3, ez - .12], [ex + .7, ey + .52, ez + .2], 7, er);        // angular brow ridge
    B([ex + s * .42 - .25, ey + .42, ez - .1], [ex + s * .42 + .25, ey + .72, ez + .2], 7, { axis: "z", angle: s * 45, origin: [ex + s * .42, ey + .57, ez] });   // brow tip
    ribbon([[8 + s * 1.2, 21, -2], [8 + s * 2.4, 20.4, 0], [8 + s * 3, 18.6, 2.2], [8 + s * 3.4, 16.2, 3.6]], 1.1, .6, [2, 3, 4], 8);   // cheek wisps
  }
  ribbon([[8, 23, -4], [8, 26.5, -4.6], [8, 29.5, -3.6], [8, 31.2, -1.2], [8, 31, 1.8]], 2.2, 1.2, [2, 3, 4, 5], 9);   // tall flowing crest
  ribbon([[8.9, 23, -3.4], [9.8, 26, -3.4], [10.6, 28.6, -1.8], [10.8, 29.6, 1]], 1.2, .8, [2, 3, 4], 9);
  ribbon([[7.1, 23, -3.4], [6.2, 26, -3.4], [5.4, 28.6, -1.8], [5.2, 29.6, 1]], 1.2, .8, [2, 3, 4], 9);
  ribbon([[8, 23, -2.4], [8, 25, .8], [8, 26.2, 4.2], [8, 25.8, 7]], 1.4, .8, [1, 2, 3], 8);
  // ---- talons forward (-Z)
  for (const s of [-1, 1]) {
    const x = 8 + s * 1.6;
    B([x - .6, 6.6, -.5], [x + .6, 9.4, 3.5], 1); B([x - .55, 4.8, -3.8], [x + .55, 6.8, .6], 3, { axis: "x", angle: 22.5 });
    B([x - .5, 4.2, -4.4], [x + .5, 5.2, -1.6], 10);
    for (const d of [-.8, 0, .8]) B([x + d - .22, 3.6, -6.4], [x + d + .22, 4.6, -3.4], 10, { axis: "x", angle: 22.5 });
  }
  B([6.4, 5.8, -4], [9.6, 11, 6], 8);   // body glow
  // ---- long flowing tail: plumes sweep down and curl in an S / spiral below the body (spread in the XY plane so they read from the front)
  const TL = (arr, z0, z1) => arr.map(([x, y], i) => [8 + x, y, z0 + (z1 - z0) * i / (arr.length - 1)]);
  ribbon(TL([[0, 8], [-.6, 3.4], [-1.8, -1.6], [-3.6, -6.4], [-6.6, -10.4], [-10.6, -12.4], [-14, -10.6], [-14.6, -7]], 5, 9), 4.9, 1.4, [1, 2, 3, 4, 5], 8);
  ribbon(TL([[0, 8], [1, 3], [2.6, -2.4], [5, -7.4], [8.8, -11], [13, -11.6], [16.4, -8.6], [17, -4.6]], 6, 11), 4.6, 1.4, [0, 1, 2, 3, 4], 8);
  ribbon(TL([[1, 8], [5.4, 6.4], [10, 4.2], [14.6, 2.6], [18.4, 3.6], [20.4, 7], [19.4, 10.6]], 4.6, 9), 3.5, 1.1, [1, 2, 3, 4], 8);
  ribbon(TL([[-1, 8], [-5.4, 6.4], [-10, 4.2], [-14.6, 2.6], [-18.4, 3.6], [-20.4, 7], [-19.4, 10.6]], 4.6, 9), 3.5, 1.1, [1, 2, 3, 4], 8);
  ribbon(TL([[0, 7], [-.4, 2], [-.6, -3.4], [-.2, -8.6], [1.6, -13]], 4, 8), 2.9, 1, [2, 3, 4], 8);
  ribbon(TL([[.4, 7.6], [4, 3], [7.6, -1.6], [10.4, -6.4], [13, -9.6]], 5, 9), 1.6, .9, [0, 2, 3], 8);
  ribbon(TL([[-.4, 7.6], [-4, 3], [-7.6, -1.6], [-11, -5.4], [-14.6, -7.6]], 5, 9), 1.6, .9, [0, 2, 3], 8);
  // ---- floating embers
  for (const [x, y, z, a] of [[-4, 15, 4, 45], [20, 17, 3, 22.5], [-6, 26, 8, 22.5], [22, 27, 6, 45], [2, 5, 6, 45], [14, 4, 8, -22.5], [4, -4, 16, 22.5], [13, -6, 17, 45], [8, 27, -8, 45], [-2, 8, 12, 22.5], [19, 9, 10, 45], [12, 30, -2, 22.5]])
    B(...sq([x, y, z], .6), a > 30 ? 5 : 4, { axis: "y", angle: a });
  m.write();
}
// ------------------------------------------------------------------ mana cannon beam segment: HEXAGONAL prism along Z (z=-16..16, seamless)
{
  const m = new Model("mana_beam", ["#f6eeff", "#e9c8ff", "#b86bff", "#7a2fd6", { c: "#8a3fe8", a: 105 }, "#ff9cf0", { c: "#b86bff", a: 160 }, "#4a1f9a"]);
  // pixel-art flat-top hexagon (across-corners width d) from stacked rows; rows are exactly adjacent
  const hexRows = (d, n) => { const H = d * .866, rows = []; for (let i = 0; i < n; i++) { const y0 = -H / 2 + H * i / n, y1 = y0 + H / n, u = Math.abs((y0 + y1) / 2); rows.push([y0, y1, d * (1 - .5 * u / (H / 2))]); } return rows; };
  const hex = (d, z0, z1, ci, n = 6) => { for (const [y0, y1, w] of hexRows(d, n)) m.box([8 - w / 2, 8 + y0, z0], [8 + w / 2, 8 + y1, z1], ci); };
  const hexRing = (d, z0, z1, ci, t = .9) => { const rows = hexRows(d, 6);
    rows.forEach(([y0, y1, w], i) => { if (i === 0 || i === 5) m.box([8 - w / 2, 8 + y0, z0], [8 + w / 2, 8 + y1, z1], ci);
      else for (const s of [-1, 1]) m.box([8 + s * w / 2 - (s > 0 ? t : 0), 8 + y0, z0], [8 + s * w / 2 + (s < 0 ? t : 0), 8 + y1, z1], ci); }); };
  hex(13, -16, 16, 4);       // translucent outer glow hex (~13px)
  hex(8, -16, 16, 2);        // violet hex (~8px)
  hex(4, -16, 16, 0, 4);     // white-lavender core hex (~4px)
  // hexagonal rune rings spaced along the length
  for (const z of [-12, -4, 4, 12]) { hexRing(11, z - .5, z + .5, 1, .8); hexRing(13.6, z - .35, z + .35, 6, .7); }
  for (const z of [-8, 0, 8]) hexRing(9, z - .4, z + .4, 5, .6);
  // rune dots on hex corners
  for (let i = 0; i < 8; i++) { const z = -14 + i * 4, s = i % 2 ? -1 : 1; m.box(...sq([8 + s * 4.6, 8, z], .5), 7, { axis: "z", angle: 45 }); }
  // sparkles
  for (const [x, y, z, a] of [[13.4, 9, -12, 45], [3.2, 6.5, -6, 22.5], [11.6, 3.6, -1, 45], [2.8, 11.5, 4, 22.5], [13, 11.4, 9, 45], [5.5, 2.6, 13, 22.5], [10.6, 13.4, -15, 22.5], [4.6, 13.2, 0, 45]])
    m.box(...sq([x, y, z], .5), 5, { axis: "z", angle: a });
  m.write();
}
// ------------------------------------------------------------------ mana shell: compressed cannonball (centre 8,8,8, ~10px, comet trail toward +Z)
{
  const m = new Model("mana_shell", ["#ffffff", "#e9c8ff", "#b86bff", "#4a1f9a", { c: "#8a3fe8", a: 90 }, "#ff9cf0", { c: "#b86bff", a: 130 }, "#2a1166", "#7a2fd6"]);
  seed = 5;
  const sphere = (R, ci, step, rot45 = true) => { for (let y = -R + step / 2; y < R; y += step) { const r = Math.sqrt(Math.max(0, R * R - y * y)) * .97, h = step / 2;
    m.box([8 - r, 8 + y - h, 8 - r], [8 + r, 8 + y + h, 8 + r], ci);
    if (rot45) m.box([8 - r, 8 + y - h, 8 - r], [8 + r, 8 + y + h, 8 + r], ci, { axis: "y", angle: 45 }); } };
  m.box(...sq([8, 8, 8], 2.4), 0); m.box(...sq([8, 8, 8], 3.2), 5, { axis: "y", angle: 45 });  // hot centre
  sphere(4.4, 7, 1.4, false);          // darkest inner shell
  sphere(5, 3, 1.25);                  // dense dark-violet outer shell
  // glowing cracks: thin bright slivers on the surface
  for (const [x, y, z, w, hh, d, ci, a, ax] of [[8, 11.6, 4.1, 2.4, .5, .5, 0, 22.5, "z"], [10.4, 7, 3.1, .5, 3, .5, 1, -22.5, "z"], [5.1, 9, 5.2, .5, 3.2, .6, 0, 22.5, "z"], [8, 4.6, 6.2, 3, .5, .6, 1, -22.5, "z"],
    [12.9, 9, 8, .5, 2.6, .6, 0, 22.5, "x"], [3, 7, 9, .5, 2.8, .6, 1, -22.5, "x"], [8, 12.8, 9, 2.6, .5, .6, 0, 22.5, "y"], [8, 3.3, 8.5, 2.4, .5, .5, 1, 22.5, "y"],
    [9.5, 8, 12.9, 3, .6, .5, 0, 22.5, "z"], [6, 9, 12.5, .5, 2.8, .5, 1, -22.5, "z"]])
    m.box([x - w / 2, y - hh / 2, z - d / 2], [x + w / 2, y + hh / 2, z + d / 2], ci, { axis: ax, angle: a });
  // two crossing glowing rune bands
  const band = (plane, r, W, T, ci, ci2) => { for (let k = 0; k < 16; k++) { const th = k * 22.5, L = 2 * r * Math.tan(Math.PI / 16) + .5;
    const c = plane === "xy" ? [8 + r * Math.cos(th * D), 8 + r * Math.sin(th * D), 8] : [8 + r * Math.cos(th * D), 8, 8 + r * Math.sin(th * D)];
    m.tan(plane, c, [L, W, T], th + 90, k % 2 ? ci : ci2); } };
  band("xy", 5.7, .9, 1.1, 1, 8); band("xz", 5.7, .9, 1.1, 5, 8);
  band("xy", 5.9, .35, 1.4, 0, 0); band("xz", 5.9, .35, 1.4, 0, 0);
  // orbiting shards
  for (let i = 0; i < 8; i++) { const th = i * 45 * D, r = 7.4, c = [8 + r * Math.cos(th), 8 + r * Math.sin(th) * .6 + (i % 2 ? 1.5 : -1.5), 8 + r * Math.sin(th) * .8];
    m.box(...sq(c, .55 + (i % 3) * .1), i % 2 ? 1 : 5, { axis: ["x", "y", "z"][i % 3], angle: [22.5, 45, -22.5][i % 3] }); }
  sphere(7, 4, 2.2, false);            // translucent outer aura
  // short comet trail toward +Z
  for (let i = 0; i < 7; i++) { const z0 = 12.5 + i * 2.3, r = 3.2 - i * .38; m.box([8 - r, 8 - r, z0], [8 + r, 8 + r, z0 + 2.3], i < 3 ? 6 : 4, { axis: "z", angle: i % 2 ? 45 : 22.5 }); m.box([8 - r * .5, 8 - r * .5, z0], [8 + r * .5, 8 + r * .5, z0 + 2.3], 2, { axis: "z", angle: i % 2 ? 22.5 : 45 }); }
  for (const [x, y, z] of [[5.5, 9.5, 18], [10.8, 6.6, 21], [9.4, 10.8, 25], [6.2, 6.2, 27]]) m.box(...sq([x, y, z], .45), 1, { axis: "z", angle: 45 });
  m.write();
}
// ------------------------------------------------------------------ mana blast: explosion burst (centre 8,8,8), scaled up + tweened by the plugin
{
  const m = new Model("mana_blast", ["#ffffff", "#e9c8ff", "#b86bff", "#7a2fd6", { c: "#8a3fe8", a: 95 }, "#ff9cf0", { c: "#b86bff", a: 110 }, "#4a1f9a"]);
  const dirPlane = (plane, k, r0, r1, w, t, ci) => { const th = k * 22.5, rc = (r0 + r1) / 2, L = (r1 - r0) * 1.04 + .4;
    const c = plane === "xy" ? [8 + rc * Math.cos(th * D), 8 + rc * Math.sin(th * D), 8] : [8 + rc * Math.cos(th * D), 8, 8 + rc * Math.sin(th * D)];
    m.tan(plane, c, [L, w, t], th, ci); };
  // spiky star: long/short alternating spikes in the XY and XZ planes, each = thick base + thin tip
  for (const plane of ["xy", "xz"]) for (let k = 0; k < 16; k++) {
    const long = k % 2 === 0, len = long ? 14 : 8.5, big = k % 4 === 0;
    dirPlane(plane, k, 1.5, len * .55, long ? 2.6 : 1.8, 1.8, long ? 3 : 2);
    dirPlane(plane, k, len * .45, len, long ? 1.4 : 1, 1, big ? 1 : 2);
    if (long) dirPlane(plane, k, len * .8, len + 1.5, .6, .6, 0);
  }
  // third plane (yz): spikes tilted about X, long axis along Z (|ang|<=45) or along Y
  for (let k = 0; k < 16; k++) { const th = k * 22.5 * D, len = k % 2 ? 7.5 : 11, rc = len / 2 + 1.2;
    const dy = Math.sin(th), dz = Math.cos(th); let ang = (k * 22.5) % 180; if (ang > 90) ang -= 180;
    let a = ang, swap = false; if (Math.abs(a) > 45) { swap = true; a -= 90 * Math.sign(a); }
    const c = [8, 8 + rc * dy, 8 + rc * dz], W = k % 2 ? 1.1 : 1.6;
    const d = swap ? [W, len, W] : [W, W, len];
    m.box([c[0] - d[0] / 2, c[1] - d[1] / 2, c[2] - d[2] / 2], [c[0] + d[0] / 2, c[1] + d[1] / 2, c[2] + d[2] / 2], k % 2 ? 2 : 1, a ? { axis: "x", angle: -a } : null);
  }
  // hot core
  m.box(...sq([8, 8, 8], 2.2), 0); m.box(...sq([8, 8, 8], 2.9), 1, { axis: "y", angle: 45 }); m.box(...sq([8, 8, 8], 2.9), 5, { axis: "z", angle: 45 });
  // translucent shock shell
  for (let y = -8.5 + .85; y < 8.5; y += 1.7) { const r = Math.sqrt(Math.max(0, 8.5 * 8.5 - y * y)) * .97, h = .85;
    m.box([8 - r, 8 + y - h, 8 - r], [8 + r, 8 + y + h, 8 + r], 6); m.box([8 - r, 8 + y - h, 8 - r], [8 + r, 8 + y + h, 8 + r], 4, { axis: "y", angle: 45 }); }
  // debris shards
  for (const [x, y, z, a] of [[1.5, 12, 9, 22.5], [14.5, 4, 6, 45], [12, 14.5, 4, 22.5], [3, 2.5, 12, 45], [8, 15, 13, 22.5], [13.5, 11, 13.5, 45], [2.5, 8, 1.5, 22.5], [10, 1.5, 2.5, 45]])
    m.box(...sq([x, y, z], .5), a > 30 ? 5 : 0, { axis: "y", angle: a });
  m.write();
}
// ------------------------------------------------------------------ mana cannon magic circle (XY plane)
{
  const m = new Model("mana_circle", ["#ffffff", "#e9c8ff", "#b86bff", "#7a2fd6", { c: "#7a2fd6", a: 110 }, "#ff9cf0", { c: "#b86bff", a: 150 }]);
  const Z0 = 7.75, T = .5, C = [8, 8];
  m.box([-2.5, -2.5, 7.3], [18.5, 18.5, 7.6], 4); // translucent glow plate behind
  for (const a of [0, 22.5, 45, -22.5]) m.box([-2.5, -2.5, 7.3], [18.5, 18.5, 7.6], 4, { axis: "z", angle: a });
  const ring = (r, w, ci) => { for (let k = 0; k < 16; k++) { const th = k * 22.5; const L = 2 * r * Math.tan(Math.PI / 16) + .5;
    m.tan("xy", [C[0] + r * Math.cos(th * D), C[1] + r * Math.sin(th * D), 8], [L, w, T], th + 90, ci); } };
  ring(14, .9, 2); ring(11.4, .8, 3);
  // octagram: axis-aligned square + diagonal square
  const h = 6.4;
  for (const [dx, dy, ang] of [[0, h, 0], [0, -h, 0], [h, 0, 90], [-h, 0, 90], [h * .7071, h * .7071, 135], [-h * .7071, -h * .7071, 135], [h * .7071, -h * .7071, 45], [-h * .7071, h * .7071, 45]])
    m.tan("xy", [C[0] + dx, C[1] + dy, 8], [2 * h + .6, .7, T], ang, 1);
  // inner small ring-ish diamond
  m.box([5.4, 5.4, Z0 + .05], [10.6, 10.6, Z0 + T + .05], 6, { axis: "z", angle: 45 });
  // rune blocks between the rings and at the rim
  for (let k = 0; k < 16; k++) { const th = (k * 22.5 + 11.25) * D, r = 12.7;
    m.box([C[0] + r * Math.cos(th) - .6, C[1] + r * Math.sin(th) - (k % 2 ? .35 : .6), Z0], [C[0] + r * Math.cos(th) + .6, C[1] + r * Math.sin(th) + (k % 2 ? .35 : .6), Z0 + T], k % 2 ? 5 : 1, { axis: "z", angle: [0, 22.5, 45, -22.5][k % 4] }); }
  for (let k = 0; k < 8; k++) { const th = (k * 45) * D, r = 15.3;
    m.box(...[[C[0] + r * Math.cos(th) - .5, C[1] + r * Math.sin(th) - .5, Z0], [C[0] + r * Math.cos(th) + .5, C[1] + r * Math.sin(th) + .5, Z0 + T]], 5, { axis: "z", angle: 45 }); }
  // glowing centre
  m.box([6.3, 6.3, 7.6], [9.7, 9.7, 8.4], 2, { axis: "z", angle: 45 });
  m.box([6.9, 6.9, 7.55], [9.1, 9.1, 8.45], 1);
  m.box([7.3, 7.3, 7.5], [8.7, 8.7, 8.5], 0, { axis: "z", angle: 45 });
  m.write();
}
