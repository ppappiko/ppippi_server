// Generates 16x16 block textures for the tier-4 region blocks:  node dev/art/make-region-blocks.mjs
import fs from "node:fs";
import path from "node:path";
import zlib from "node:zlib";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const dst = path.resolve(here, "../pipi-facilities/assets/pipifacilities/textures/block");
fs.mkdirSync(dst, { recursive: true });

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
const mk = () => new Uint8Array(16 * 16 * 4);
const set = (p, x, y, c) => { if (x < 0 || y < 0 || x > 15 || y > 15) return; const i = (y * 16 + x) * 4; p[i] = c[0]; p[i + 1] = c[1]; p[i + 2] = c[2]; p[i + 3] = c[3] ?? 255; };
const shade = (c, d) => [c[0] + d, c[1] + d, c[2] + d].map(v => Math.max(0, Math.min(255, v | 0)));
function fillNoise(p, base, amp) { for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) set(p, x, y, shade(base, (rnd() - 0.5) * amp)); }
const save = (n, p) => fs.writeFileSync(path.join(dst, n + ".png"), png(p));

// stone rock pile
function rock(withAmber) {
  const p = mk(); fillNoise(p, [120, 118, 114], 40);
  for (let i = 0; i < 9; i++) { // pebble outlines
    const cx = (rnd() * 16) | 0, cy = (rnd() * 16) | 0;
    for (let k = 0; k < 4; k++) set(p, cx + k, cy, [80, 78, 76]);
    for (let k = 0; k < 3; k++) set(p, cx + k, cy + 1, [150, 148, 142]);
  }
  if (withAmber) {
    for (const [cx, cy] of [[4, 4], [11, 6], [6, 11], [12, 12]]) {
      for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) set(p, cx + dx, cy + dy, (dx + dy) % 2 ? [235, 150, 25] : [255, 190, 50]);
      set(p, cx, cy, [255, 225, 120]); set(p, cx + 1, cy + 1, [190, 105, 10]);
    }
  }
  return p;
}
save("rock_pile", rock(false));
save("amber_rock", rock(true));

// redwood log
{
  const bark = mk();
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const stripe = (x % 4 === 0) ? -28 : (x % 4 === 3 ? 10 : 0);
    set(bark, x, y, shade([140, 40, 32], stripe + (rnd() - 0.5) * 24));
  }
  for (let i = 0; i < 6; i++) { const x = (rnd() * 16) | 0, y = (rnd() * 14) | 0; set(bark, x, y, [70, 18, 16]); set(bark, x, y + 1, [70, 18, 16]); }
  save("redwood_tree_log", bark);
  const top = mk();
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const d = Math.hypot(x - 7.5, y - 7.5);
    if (d > 7.6) { set(top, x, y, shade([120, 32, 26], (rnd() - 0.5) * 16)); continue; }
    set(top, x, y, Math.floor(d) % 2 ? [190, 85, 60] : [160, 62, 44]);
  }
  save("redwood_tree_log_top", top);
}
// redwood leaves (cutout: holes)
{
  const p = mk();
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    if (rnd() < 0.14) { set(p, x, y, [0, 0, 0, 0]); continue; }
    set(p, x, y, shade([150, 25, 45], (rnd() - 0.5) * 70));
  }
  save("redwood_tree_leaves", p);
}
// cabbage crop (cross plant)
{
  const p = mk();
  const G = [[98, 160, 60], [120, 190, 80], [78, 135, 48], [150, 215, 110]];
  for (let y = 5; y < 15; y++) for (let x = 0; x < 16; x++) {
    const dx = (x - 7.5) / 7.2, dy = (y - 10) / 5.2;
    if (dx * dx + dy * dy > 1) continue;
    const r = Math.hypot(dx, dy);
    set(p, x, y, r < 0.35 ? G[3] : r < 0.7 ? G[1] : G[0]);
    if ((x + y) % 5 === 0 && r > 0.35) set(p, x, y, G[2]);
  }
  for (let x = 4; x < 12; x++) set(p, x, 14, [60, 105, 38]);
  save("cabbage_crop", p);
}
// seed item
{
  const p = mk();
  for (const [x, y] of [[5, 6], [8, 5], [10, 8], [7, 9], [6, 11], [10, 11], [4, 8]]) {
    set(p, x, y, [120, 85, 40]); set(p, x + 1, y, [160, 120, 60]); set(p, x, y + 1, [90, 60, 28]);
  }
  fs.mkdirSync(path.resolve(dst, "../item"), { recursive: true });
  fs.writeFileSync(path.resolve(dst, "../item/cabbage_seed.png"), png(p));
}
console.log("done ->", dst);
