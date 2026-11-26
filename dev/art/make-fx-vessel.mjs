// 눈물 성배 (통곡의 왕좌 raid vessel) as an ItemDisplay item model: a player-sized hive cauldron with a glowing sphere sitting in it.
//   node dev/art/make-fx-vessel.mjs            -> pipi-facilities/assets/pipifacilities/{models,textures}/item/fx_tear_vessel.{json,png}
//   PREVIEW_DIR=<dir> node dev/art/make-fx-vessel.mjs   also writes <dir>/fx_tear_vessel.bbmodel for preview-pets.mjs
// 16 units = 1 block, feet at model y = 0 (the ItemDisplay stands 0.5 block above the floor, since the model centre is (8, 8, 8)).
// Vanilla item-model rules: coordinates -16..32, rotations only 0/±22.5/±45 about one axis, so round parts are unions of
// squares turned 0 / 22.5 / 45 / -22.5 about y (a 16-sided outline).
import fs from "node:fs";
import path from "node:path";
import zlib from "node:zlib";
import crypto from "node:crypto";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "../pipi-facilities/assets/pipifacilities");
const NAME = "tear_vessel";

// ---------------------------------------------------------------- palette texture (16 cells of 4x4 px)
const crcT = new Uint32Array(256).map((_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const crc = b => { let c = 0xffffffff; for (const x of b) c = crcT[(c ^ x) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (type, data) => { const t = Buffer.from(type, "ascii"), l = Buffer.alloc(4), c = Buffer.alloc(4); l.writeUInt32BE(data.length); c.writeUInt32BE(crc(Buffer.concat([t, data]))); return Buffer.concat([l, t, data, c]); };
function png(px, w = 16, h = 16) {
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) { raw[y * (w * 4 + 1)] = 0; for (let x = 0; x < w * 4; x++) raw[y * (w * 4 + 1) + 1 + x] = px[y * w * 4 + x]; }
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk("IHDR", ihdr), chunk("IDAT", zlib.deflateSync(raw)), chunk("IEND", Buffer.alloc(0))]);
}
let seed = 77;
const rnd = () => (seed = (seed * 1664525 + 1013904223) >>> 0) / 4294967296;
const hex = h => [1, 3, 5].map(i => parseInt(h.slice(i, i + 2), 16));
function paletteTexture(pal) {
  const px = new Uint8Array(16 * 16 * 4);
  pal.forEach((e, i) => {
    const o = typeof e === "string" ? { c: e } : e, base = hex(o.c), a = o.a ?? 255, cx = (i % 4) * 4, cy = Math.floor(i / 4) * 4;
    for (let y = 0; y < 4; y++) for (let x = 0; x < 4; x++) {
      const j = (rnd() - .5) * 2 * (o.noise ?? 0);
      let c = base.map(v => Math.max(0, Math.min(255, Math.round(v + j))));
      if (o.speck && rnd() < o.speck[1]) c = hex(o.speck[0]);
      const k = ((cy + y) * 16 + cx + x) * 4; px[k] = c[0]; px[k + 1] = c[1]; px[k + 2] = c[2]; px[k + 3] = a;
    }
  });
  return png(px);
}

// ---------------------------------------------------------------- palette
const PAL = [
  { c: "#2b2230", noise: 10 },                          // 0 chitin dark
  { c: "#45374a", noise: 12, speck: ["#5a6a3a", .12] },  // 1 chitin (mossy)
  { c: "#9a9384", noise: 12, speck: ["#6a6354", .15] },  // 2 bone
  { c: "#22202a", noise: 6 },                           // 3 dark iron band
  { c: "#0d3436", noise: 8 },                           // 4 tear liquid
  "#3fe0d0",                                            // 5 teal glow
  "#ffffff",                                            // 6 sphere core
  "#c4fff5",                                            // 7 sphere inner
  { c: "#6fe8dc", a: 175 },                             // 8 sphere shell (translucent)
  { c: "#5f574a", noise: 10 },                          // 9 bone shadow / claws
  "#a6fff4",                                            // 10 bright rune
  { c: "#3a2e3e", noise: 8 },                           // 11 chitin rim
];

// ---------------------------------------------------------------- model
const els = [];
function box(from, to, ci, rot) {
  const f = from.map((v, i) => Math.min(v, to[i])), t = from.map((v, i) => Math.max(v, to[i]));
  for (let i = 0; i < 3; i++) if (t[i] - f[i] < .3) t[i] = f[i] + .3;
  const col = ci % 4 * 4, row = Math.floor(ci / 4) * 4, face = { uv: [col, row, col + 4, row + 4], texture: "#0" };
  const el = { from: f.map(v => +v.toFixed(3)), to: t.map(v => +v.toFixed(3)), shade: true, faces: { north: face, east: face, south: face, west: face, up: face, down: face } };
  if (rot && rot.angle) {
    if (![-45, -22.5, 22.5, 45].includes(rot.angle)) throw new Error("bad angle " + rot.angle);
    el.rotation = { angle: rot.angle, axis: rot.axis, origin: rot.origin ?? f.map((v, i) => (v + t[i]) / 2) };
  }
  els.push(el);
}
const C = 8, D = Math.PI / 180;
/** round slice: squares of half-size r turned 0 / 22.5 / 45 / -22.5 about the vertical axis through (cx, cz) */
function disc(y0, y1, r, ci, n = 4, cx = C, cz = C) {
  [0, 45, 22.5, -22.5].slice(0, n).forEach(a => box([cx - r, y0, cz - r], [cx + r, y1, cz + r], ci, a ? { axis: "y", angle: a, origin: [cx, (y0 + y1) / 2, cz] } : null));
}
/** truly round slice: 8 narrow bars through the centre at every 22.5 deg (outline = 16-gon, no star points) */
function round(y0, y1, r, ci, cx = C, cz = C) {
  const w = r * .2 + .2, o = [cx, (y0 + y1) / 2, cz];
  for (const a of [0, 22.5, 45, -22.5, -45]) box([cx - r, y0, cz - w], [cx + r, y1, cz + w], ci, a ? { axis: "y", angle: a, origin: o } : null);
  for (const a of [0, 22.5, -22.5]) box([cx - w, y0, cz - r], [cx + w, y1, cz + r], ci, a ? { axis: "y", angle: a, origin: o } : null);
}
/** small box on the bowl surface at compass angle th (deg, 0 = -z / front), turned to face outward */
function onSurface(th, y0, y1, R, w, d, ci) {
  const x = C + Math.sin(th * D) * R, z = C - Math.cos(th * D) * R;
  let a = ((-th % 90) + 90) % 90; if (a > 45) a -= 90; a = Math.round(a / 22.5) * 22.5;
  box([x - w / 2, y0, z - d / 2], [x + w / 2, y1, z + d / 2], ci, a ? { axis: "y", angle: a, origin: [x, (y0 + y1) / 2, z] } : null);
}

// bowl profile: small foot, wide round belly, pinched neck under the rim (a squat cauldron about one block tall)
const prof = [[1.4, 6.2], [2.8, 9.2], [4.2, 11], [5.6, 12], [7, 12.5], [8.4, 12.6], [9.8, 12.4], [11.2, 11.9], [12.6, 11.2], [14, 10.5], [15.4, 10]];
const rAt = y => { if (y <= prof[0][0]) return prof[0][1]; for (let i = 1; i < prof.length; i++) if (y <= prof[i][0]) { const [y0, r0] = prof[i - 1], [y1, r1] = prof[i]; return r0 + (r1 - r0) * (y - y0) / (y1 - y0); } return prof[prof.length - 1][1]; };
const TOP = prof[prof.length - 1][0], RIM0 = TOP - .4, RIM1 = RIM0 + 3, LIQ = RIM0 + 1.7;

// foot ring and body
round(0, 1.6, 5.6, 3);
for (let i = 0; i < prof.length - 1; i++) {
  const [y0] = prof[i], y1 = prof[i + 1][0], r = (prof[i][1] + prof[i + 1][1]) / 2;
  round(y0, y1 + .05, r, 1);
}
// dark iron bands
for (const y of [4.4, 11.6]) round(y, y + 1, rAt(y + .5) + .45, 3);
// bone ribs on 8 sides, following the belly
for (let k = 0; k < 8; k++) for (let i = 1 + k % 2; i < prof.length - 1; i += 2) {
  const [y0] = prof[i], y1 = prof[i + 1][0];
  onSurface(k * 45 + 22.5, y0 - .3, y1 + .35, rAt((y0 + y1) / 2) + .3, 1.3, 1.2, i % 4 === 3 ? 9 : 2);
}
// rim: a ring of 16 chitin panels with a bone cap, tear liquid inside
for (let k = 0; k < 16; k++) {
  const th = k * 22.5, R = 10.3, L = 2 * R * Math.tan(Math.PI / 16) + .45;
  const x = C + Math.sin(th * D) * R, z = C - Math.cos(th * D) * R;
  let a = ((-th % 90) + 90) % 90; if (a > 45) a -= 90;
  const swap = Math.abs(((th % 180) + 180) % 180 - 90) < 44.9 || ((th % 180) + 180) % 180 === 45;
  const w = swap ? 2 : L, d = swap ? L : 2;
  box([x - w / 2, RIM0, z - d / 2], [x + w / 2, RIM1, z + d / 2], 11, a ? { axis: "y", angle: a, origin: [x, (RIM0 + RIM1) / 2, z] } : null);
  box([x - w / 2 - .1, RIM1 - .2, z - d / 2 - .1], [x + w / 2 + .1, RIM1 + .7, z + d / 2 + .1], k % 2 ? 2 : 9, a ? { axis: "y", angle: a, origin: [x, RIM1 + .25, z] } : null);
}
round(RIM0, LIQ, 9.6, 4);
// four clawed bone legs (left, right, front, back), leaning outward from under the belly
for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
  const ax = dx ? "z" : "x", sgn = dx ? dx : -dz;
  const cx = C + dx * 11.6, cz = C + dz * 11.6;
  box([cx - 1.2, 1.4, cz - 1.2], [cx + 1.2, 6.6, cz + 1.2], 2, { axis: ax, angle: 22.5 * sgn, origin: [cx, 6.2, cz] });
  const fx = C + dx * 13, fz = C + dz * 13;
  box([fx - 1.6 - Math.abs(dx) * .6, 0, fz - 1.6 - Math.abs(dz) * .6], [fx + 1.6 + Math.abs(dx) * .6, 1.4, fz + 1.6 + Math.abs(dz) * .6], 9);
  for (const o of [-1, 0, 1]) { // three claws splayed outward
    const tx = fx + dx * 1.9 + (dz ? o * 1.1 : 0), tz = fz + dz * 1.9 + (dx ? o * 1.1 : 0);
    box([tx - .35, 0, tz - .35], [tx + .35, .9, tz + .35], 2);
  }
  // curved horn rising from the rim above each leg
  const hx = C + dx * 10.4, hz = C + dz * 10.4;
  box([hx - 1, RIM1 - .4, hz - 1], [hx + 1, RIM1 + 4, hz + 1], 0, { axis: ax, angle: 22.5 * sgn, origin: [hx, RIM1, hz] });
  const hx2 = hx + dx * 1.8, hz2 = hz + dz * 1.8;
  box([hx2 - .6, RIM1 + 3.2, hz2 - .6], [hx2 + .6, RIM1 + 6.6, hz2 + .6], 11, { axis: ax, angle: 45 * sgn, origin: [hx2, RIM1 + 3.4, hz2] });
}
// short bone spikes on the diagonals of the rim
for (let k = 0; k < 4; k++) { const th = 45 + k * 90, x = C + Math.sin(th * D) * 10.3, z = C - Math.cos(th * D) * 10.3;
  box([x - .75, RIM1, z - .75], [x + .75, RIM1 + 2.2, z + .75], 2, { axis: "y", angle: 45 }); box([x - .38, RIM1 + 2, z - .38], [x + .38, RIM1 + 3.2, z + .38], 9, { axis: "y", angle: 45 }); }
// glowing runes on the four sides + tear streaks running down from the rim
for (let k = 0; k < 4; k++) {
  const th = k * 90, R = rAt(8) + .3;
  onSurface(th, 6, 10.6, R, .7, .5, 10);
  onSurface(th, 7.9, 8.5, R, 3.2, .5, 5);
  onSurface(th, 10.1, 10.8, R - .05, 1.6, .5, 5);
}
for (const [th, len] of [[12, 5], [-30, 7], [140, 6], [255, 8]]) {
  for (let y = RIM0; y > RIM0 - len; y -= 1.5) onSurface(th, y - 1.55, y, rAt(y - .7) + .3, .45, .4, 5);
}
// the big sphere resting in the vessel: translucent shell over a bright inner ball and a white core
const R0 = 7.4, S = [C, LIQ + R0 * .62, C];
for (let y = -R0 + .7; y < R0; y += 1.4) { const r = Math.sqrt(Math.max(0, R0 * R0 - y * y)); if (S[1] + y + .7 > LIQ) round(S[1] + y - .7, S[1] + y + .7, r, 8); }
for (let y = -4.2; y < 4.6; y += 1.8) { const r = Math.sqrt(Math.max(0, 5.2 * 5.2 - y * y)) * .78; disc(S[1] + y - .9, S[1] + y + .9, r, 7, 2); }
disc(S[1] - 2.2, S[1] + 2.2, 2.1, 6, 2);
box([C - 2.5, S[1] - 2.5, C - 2.5], [C + 2.5, S[1] + 2.5, C + 2.5], 6, { axis: "x", angle: 45, origin: S });
console.log("rim top", RIM1.toFixed(1), "sphere top", (S[1] + R0).toFixed(1), "centre", S[1].toFixed(1));

// ---------------------------------------------------------------- write
for (const e of els) for (const v of [...e.from, ...e.to]) if (!(v >= -16 && v <= 32)) throw new Error("coord out of -16..32: " + v);
if (els.length > 400) throw new Error("too many elements " + els.length);
const tex = "pipifacilities:item/fx_" + NAME, id = { rotation: [0, 0, 0], translation: [0, 0, 0], scale: [1, 1, 1] };
const texPng = paletteTexture(PAL);
fs.writeFileSync(path.join(root, "models/item", `fx_${NAME}.json`), JSON.stringify({ textures: { "0": tex, particle: tex }, elements: els,
  display: { none: id, fixed: id, gui: { rotation: [30, 45, 0], translation: [0, -3, 0], scale: [.4, .4, .4] } } }));
fs.writeFileSync(path.join(root, "textures/item", `fx_${NAME}.png`), texPng);
console.log(`fx_${NAME}: ${els.length} elements`);

// preview: the same elements as a bbmodel (feet at y 0) so preview-pets.mjs can draw it
if (process.env.PREVIEW_DIR) {
  const elements = els.map(e => {
    const rot = [0, 0, 0]; if (e.rotation) rot["xyz".indexOf(e.rotation.axis)] = e.rotation.angle;
    const faces = {}; for (const [k, f] of Object.entries(e.faces)) faces[k] = { uv: f.uv, texture: 0 };
    return { name: "c", uuid: crypto.randomUUID(), type: "cube", from: e.from.map((v, i) => i === 1 ? v : v - 8), to: e.to.map((v, i) => i === 1 ? v : v - 8),
      origin: e.rotation ? e.rotation.origin.map((v, i) => i === 1 ? v : v - 8) : [0, 0, 0], rotation: rot, faces };
  });
  const bb = { meta: { format_version: "4.5" }, resolution: { width: 16, height: 16 }, elements,
    outliner: [{ name: "root", uuid: crypto.randomUUID(), origin: [0, 0, 0], rotation: [0, 0, 0], children: elements.map(e => e.uuid) }],
    textures: [{ source: "data:image/png;base64," + texPng.toString("base64") }], animations: [{ name: "idle", loop: "loop", length: 1, animators: {} }] };
  fs.mkdirSync(process.env.PREVIEW_DIR, { recursive: true });
  fs.writeFileSync(path.join(process.env.PREVIEW_DIR, `fx_${NAME}.bbmodel`), JSON.stringify(bb));
}
