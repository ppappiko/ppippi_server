// Generates warp_stone textures + block/item models:  node dev/art/make-warp-stone.mjs
import fs from "node:fs";
import path from "node:path";
import zlib from "node:zlib";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "../pipi-facilities/assets/pipifacilities");
const tex = path.join(root, "textures/block");
fs.mkdirSync(tex, { recursive: true });

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
const hex = h => [1, 3, 5].map(i => parseInt(h.slice(i, i + 2), 16));
const mk = () => new Uint8Array(16 * 16 * 4);
const set = (p, x, y, c) => { if (x < 0 || y < 0 || x > 15 || y > 15) return; const i = (y * 16 + x) * 4; p[i] = c[0]; p[i + 1] = c[1]; p[i + 2] = c[2]; p[i + 3] = 255; };
let seed = 777;
const rnd = () => (seed = (seed * 1664525 + 1013904223) >>> 0) / 4294967296;

// crystal: diagonal bands
{
  const p = mk();
  const pal = [hex("#bff6ff"), hex("#5fd6e6"), hex("#2a9fb5"), hex("#17707f")];
  const seq = [1, 0, 1, 2, 3, 2];
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) set(p, x, y, pal[seq[Math.floor(((x + y) % 12) / 2)]]);
  fs.writeFileSync(path.join(tex, "crystal_cyan.png"), png(p));
}
// pedestal stone with cracks
{
  const p = mk();
  const a = hex("#2e3440"), b = hex("#3b4252"), crack = hex("#1e222b");
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) set(p, x, y, rnd() < 0.45 ? b : a);
  for (let i = 0; i < 3; i++) { let x = (rnd() * 16) | 0, y = (rnd() * 6) | 0; for (let k = 0; k < 7; k++) { set(p, x, y, crack); y++; if (rnd() < 0.5) x += rnd() < 0.5 ? -1 : 1; } }
  fs.writeFileSync(path.join(tex, "pedestal_stone.png"), png(p));
}

// models
const face = t => ({ uv: [0, 0, 16, 16], texture: t });
const box = (from, to, t, rot45) => {
  const e = { from, to, faces: Object.fromEntries(["north", "east", "south", "west", "up", "down"].map(d => [d, face(t)])) };
  if (rot45) e.rotation = { angle: 45, axis: "y", origin: [8, (from[1] + to[1]) / 2, 8] };
  return e;
};
const sq = (y0, y1, w) => [[8 - w / 2, y0, 8 - w / 2], [8 + w / 2, y1, 8 + w / 2]];
const els = [
  box(...sq(0, 3, 12), "#stone"), box(...sq(3, 7, 8), "#stone"), box(...sq(7, 9, 10), "#stone"),
];
const layers = [[12, 14, 2], [14, 17, 4], [17, 20, 6], [20, 24, 8], [24, 27, 6], [27, 30, 4], [30, 32, 2]];
layers.forEach(([y0, y1, w], i) => els.push(box(...sq(y0, y1, w), "#crystal", i === 2 || i === 4)));
const model = { textures: { stone: "pipifacilities:block/pedestal_stone", crystal: "pipifacilities:block/crystal_cyan", particle: "pipifacilities:block/pedestal_stone" }, render_type: "minecraft:cutout", elements: els };
const w = (f, o) => { fs.mkdirSync(path.dirname(f), { recursive: true }); fs.writeFileSync(f, JSON.stringify(o)); };
w(path.join(root, "models/block/warp_stone.json"), model);
w(path.join(root, "models/item/warp_stone.json"), { parent: "pipifacilities:block/warp_stone" });
w(path.join(root, "blockstates/warp_stone.json"), { variants: { "": { model: "pipifacilities:block/warp_stone" } } });
console.log("warp_stone assets written");
