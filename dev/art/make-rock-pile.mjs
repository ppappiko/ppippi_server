// Preview-only stone pile: textures -> dev/pipi-facilities/assets/.../textures/item/station_rock_*.png, model -> dev/art/preview/rock_pile.json, then renders via preview-station.mjs.
import fs from "node:fs"; import path from "node:path"; import zlib from "node:zlib"; import { fileURLToPath } from "node:url"; import { execFileSync } from "node:child_process";
const here = path.dirname(fileURLToPath(import.meta.url));
const texDir = path.resolve(here, "../pipi-facilities/assets/pipifacilities/textures/item");
const crcT = new Uint32Array(256).map((_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const crc = b => { let c = 0xffffffff; for (const x of b) c = crcT[(c ^ x) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (t, d) => { const tb = Buffer.from(t), l = Buffer.alloc(4), c = Buffer.alloc(4); l.writeUInt32BE(d.length); c.writeUInt32BE(crc(Buffer.concat([tb, d]))); return Buffer.concat([l, tb, d, c]); };
function png(px) { const raw = Buffer.alloc(16 * 65); for (let y = 0; y < 16; y++) { raw[y * 65] = 0; for (let x = 0; x < 16; x++) { const c = px[y][x]; raw.set([c >> 16 & 255, c >> 8 & 255, c & 255, 255], y * 65 + 1 + x * 4); } }
  const ih = Buffer.alloc(13); ih.writeUInt32BE(16, 0); ih.writeUInt32BE(16, 4); ih[8] = 8; ih[9] = 6;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk("IHDR", ih), chunk("IDAT", zlib.deflateSync(raw)), chunk("IEND", Buffer.alloc(0))]); }
let seed = 7; const rnd = () => (seed = (seed * 1664525 + 1013904223) >>> 0) / 4294967296;
// palette (own greys): light, base, base2, shade, crack
function stone(P, speck, cracks, hl) {
  const g = Array.from({ length: 16 }, () => Array(16).fill(P.base));
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) { const r = rnd(); g[y][x] = r < .22 ? P.base2 : r < .30 ? P.shade : r < .36 ? P.light : P.base; }
  for (let i = 0; i < speck; i++) g[rnd() * 16 | 0][rnd() * 16 | 0] = P.shade;
  for (const c of cracks) for (const [x, y] of c) g[y][x] = P.crack;           // short dark cracks
  for (const [x, y] of hl) { g[y][x] = P.light; }                                // top-left highlights
  return g;
}
const P1 = { light: 0xa9a9a9, base: 0x8e8e8e, base2: 0x838383, shade: 0x717171, crack: 0x555555 };
const P2 = { light: 0xbdbdbd, base: 0xa4a4a4, base2: 0x9a9a9a, shade: 0x888888, crack: 0x6e6e6e };
const P3 = { light: 0x8c8c8c, base: 0x777777, base2: 0x6e6e6e, shade: 0x626262, crack: 0x4c4c4c };
const crk = [[[3, 3], [4, 3], [4, 4], [5, 4], [5, 5]], [[10, 9], [11, 9], [11, 10], [12, 10]], [[6, 12], [7, 12], [7, 13]], [[13, 2], [13, 3]], [[1, 8], [2, 8]]];
const hlA = [[0, 0], [1, 0], [0, 1], [8, 0], [9, 0], [8, 1], [0, 8], [0, 9], [1, 8], [8, 8], [9, 8], [8, 9]];
const side = stone(P1, 6, crk, hlA), top = stone(P2, 4, [[[5, 6], [6, 6]], [[11, 11], [12, 11], [12, 12]]], hlA), dark = stone(P3, 6, crk, [[0, 0], [1, 0], [0, 1], [8, 8], [9, 8], [8, 9]]);
for (const [n, t] of [["station_rock_side", side], ["station_rock_top", top], ["station_rock_dark", dark]]) fs.writeFileSync(path.join(texDir, n + ".png"), png(t));
// model: every box has per-face UVs equal to the face size in px (square texels)
const el = [];
const T = { s: "#1", t: "#2", d: "#3" };
function box(f, t, side = "s", rot, off = [0, 0]) {
  const w = t[0] - f[0], h = t[1] - f[1], d = t[2] - f[2], [ou, ov] = off, S = T[side], tp = side === "d" ? "#2" : "#2";
  const fc = (tx, a, b) => ({ uv: [ou, ov, ou + a, ov + b], texture: tx });
  const e = { from: f, to: t, faces: { north: fc(S, w, h), south: fc(S, w, h), east: fc(S, d, h), west: fc(S, d, h), up: fc(tp, w, d), down: fc(S, w, d) } };
  if (rot) e.rotation = { axis: "y", angle: rot, origin: [(f[0] + t[0]) / 2, f[1], (f[2] + t[2]) / 2] };
  el.push(e);
}
box([2, 0, 2], [10.5, 8, 10.5], "s", 22.5, [0, 0]);          // big boulder, back
box([3.5, 8, 3.5], [8.5, 11, 8.5], "s", -22.5, [7, 5]);      // cap stone on top
box([9.5, 0, 3.5], [15, 5, 9], "d", -22.5, [2, 6]);          // medium right
box([3, 0, 10], [8.5, 4.5, 15], "s", 0, [6, 7]);             // medium front-left
box([9, 0, 10], [12, 2.5, 13], "d", 22.5, [3, 3]);           // pebbles front
box([12.5, 0, 11], [14.5, 2, 13], "s", 0, [8, 8]);
box([1, 0, 6], [3, 2, 8.5], "d", 22.5, [5, 9]);
const tex = { 1: "pipifacilities:item/station_rock_side", 2: "pipifacilities:item/station_rock_top", 3: "pipifacilities:item/station_rock_dark", particle: "pipifacilities:item/station_rock_side" };
const prev = path.join(here, "preview"); fs.mkdirSync(prev, { recursive: true });
fs.writeFileSync(path.join(prev, "rock_pile.json"), JSON.stringify({ textures: tex, elements: el }));
const ps = path.join(here, "preview-station.mjs"), m = path.join(prev, "rock_pile.json");
execFileSync("node", [ps, "--model", m, "--out", path.join(prev, "rock_pile.png")], { stdio: "inherit" });
execFileSync("node", [ps, "--model", m, "--out", path.join(prev, "rock_pile_top.png")], { stdio: "inherit", env: { ...process.env, TOP: "1" } });
