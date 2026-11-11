// Generates the 마력포 charge magic-circle textures (256x256 RGBA): node dev/art/make-charge-circle.mjs
import fs from "node:fs";
import path from "node:path";
import zlib from "node:zlib";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const out = path.resolve(here, "../pipi-travel/assets/pipitravel/textures/gui");
fs.mkdirSync(out, { recursive: true });
const crcT = new Uint32Array(256).map((_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const crc = b => { let c = 0xffffffff; for (const x of b) c = crcT[(c ^ x) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
function chunk(type, data) {
  const t = Buffer.from(type, "ascii"), l = Buffer.alloc(4), c = Buffer.alloc(4);
  l.writeUInt32BE(data.length); c.writeUInt32BE(crc(Buffer.concat([t, data])));
  return Buffer.concat([l, t, data, c]);
}
const N = 256, C = 127.5;
function png(px) {
  const raw = Buffer.alloc((N * 4 + 1) * N);
  for (let y = 0; y < N; y++) { raw[y * (N * 4 + 1)] = 0; for (let x = 0; x < N * 4; x++) raw[y * (N * 4 + 1) + 1 + x] = px[y * N * 4 + x]; }
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(N, 0); ihdr.writeUInt32BE(N, 4); ihdr[8] = 8; ihdr[9] = 6;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk("IHDR", ihdr), chunk("IDAT", zlib.deflateSync(raw)), chunk("IEND", Buffer.alloc(0))]);
}
const hex = h => [1, 3, 5].map(i => parseInt(h.slice(i, i + 2), 16));
const VIO = hex("#b57cff"), DV = hex("#7a45d6"), LT = hex("#efe0ff");
let P;
const set = (x, y, c, a = 255) => {
  x = Math.round(x); y = Math.round(y); if (x < 0 || y < 0 || x >= N || y >= N) return;
  const i = (y * N + x) * 4;
  if (a <= P[i + 3]) return;
  P[i] = c[0]; P[i + 1] = c[1]; P[i + 2] = c[2]; P[i + 3] = a;
};
const disc = (cx, cy, r, c, a = 255) => { for (let y = -r; y <= r; y++) for (let x = -r; x <= r; x++) if (x * x + y * y <= r * r) set(cx + x, cy + y, c, a); };
function line(x0, y0, x1, y1, t, c, a = 255) {
  const n = Math.ceil(Math.hypot(x1 - x0, y1 - y0) * 2) + 1;
  for (let i = 0; i <= n; i++) { const u = i / n; disc(Math.round(x0 + (x1 - x0) * u), Math.round(y0 + (y1 - y0) * u), Math.max(0, Math.round((t - 1) / 2)), c, a); }
}
function ring(r, t, c, a = 255, a0 = 0, a1 = 360) {
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    const d = Math.hypot(x - C, y - C); if (d > r + t / 2 || d < r - t / 2) continue;
    let ang = Math.atan2(y - C, x - C) * 180 / Math.PI; if (ang < 0) ang += 360;
    if (ang >= a0 && ang <= a1) set(x, y, c, a);
  }
}
const pt = (r, deg) => [C + Math.cos(deg * Math.PI / 180) * r, C + Math.sin(deg * Math.PI / 180) * r];
// original rune glyphs: a few strokes in a 10x14 cell, placed along a radius and rotated tangentially
const GLYPHS = [
  [[0, 0, 0, 14], [0, 7, 8, 0], [0, 7, 8, 14]],
  [[0, 0, 8, 0], [8, 0, 8, 14], [0, 14, 8, 14], [0, 7, 8, 7]],
  [[4, 0, 4, 14], [0, 4, 8, 4], [0, 10, 8, 10]],
  [[0, 0, 8, 7], [8, 7, 0, 14], [0, 7, 8, 7]],
  [[0, 14, 4, 0], [4, 0, 8, 14], [1, 9, 7, 9]],
  [[0, 0, 8, 14], [8, 0, 0, 14], [4, 0, 4, 14]],
];
function glyph(g, r, deg, s, c, a = 255) {
  const rad = deg * Math.PI / 180, ux = -Math.sin(rad), uy = Math.cos(rad), vx = Math.cos(rad), vy = Math.sin(rad); // u tangent, v outward
  const base = pt(r, deg);
  for (const [x0, y0, x1, y1] of GLYPHS[g]) {
    const f = (x, y) => [base[0] + ux * (x - 4) * s + vx * -(y - 7) * s, base[1] + uy * (x - 4) * s + vy * -(y - 7) * s];
    const A = f(x0, y0), B = f(x1, y1); line(A[0], A[1], B[0], B[1], 3, c, a);
  }
}
function star(r, k, rot, t, c, a) { // hexagram / polygon outline
  for (let tri = 0; tri < 2; tri++) for (let i = 0; i < 3; i++) {
    const A = pt(r, rot + tri * 60 + i * 120), B = pt(r, rot + tri * 60 + (i + 1) * 120); line(A[0], A[1], B[0], B[1], t, c, a);
  }
}
function glow(r0, r1, c, amax) { // soft radial falloff band
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    const d = Math.hypot(x - C, y - C); if (d < r0 || d > r1) continue;
    const u = (d - r0) / (r1 - r0); set(x, y, c, Math.round(amax * (1 - u) * (1 - u)));
  }
}

// outer ring
P = new Uint8Array(N * N * 4);
ring(124, 5, VIO); ring(124, 1, LT); ring(114, 3, DV); ring(104, 2, VIO); ring(88, 2, DV, 255, 0, 360);
for (let i = 0; i < 24; i++) { const a = pt(124, i * 15), b = pt(114, i * 15); line(a[0], a[1], b[0], b[1], i % 2 ? 2 : 3, VIO); }
for (let i = 0; i < 12; i++) glyph(i % 6, 96, i * 30 + 15, 1.1, LT);
for (let i = 0; i < 12; i++) { const a = pt(88, i * 30), b = pt(104, i * 30); line(a[0], a[1], b[0], b[1], 2, DV); }
for (let i = 0; i < 6; i++) { const p = pt(124, i * 60 + 30); disc(Math.round(p[0]), Math.round(p[1]), 4, LT); }
fs.writeFileSync(path.join(out, "charge_circle_outer.png"), png(P));

// inner: hexagram + runes + glow core
P = new Uint8Array(N * N * 4);
glow(0, 70, DV, 90);
ring(72, 3, VIO); ring(60, 2, DV);
star(70, 3, -90, 3, LT, 255); star(46, 3, -90, 2, VIO, 255);
ring(30, 2, VIO);
for (let i = 0; i < 6; i++) glyph((i + 2) % 6, 54, i * 60 + 30, 0.8, LT);
for (let i = 0; i < 6; i++) { const p = pt(70, i * 60 - 90); disc(Math.round(p[0]), Math.round(p[1]), 5, LT); }
disc(128, 128, 9, VIO); disc(128, 128, 5, LT);
fs.writeFileSync(path.join(out, "charge_circle_inner.png"), png(P));
console.log("wrote charge circle textures to", out);
