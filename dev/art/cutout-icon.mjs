// Screenshot icon -> transparent game icon: node dev/art/cutout-icon.mjs <in.png> <out.png> [size=32]
// The background (sampled from the border) is keyed out, the subject is cropped, padded square and box-resampled.
import fs from "node:fs";
import { readPng, writePng } from "./class-icons/pngio.mjs";
const [, , inPath, outPath, sizeArg] = process.argv;
const N = +(sizeArg || 32);
const { w, h, px } = readPng(fs.readFileSync(inPath));
const at = (x, y) => [px[(y * w + x) * 4], px[(y * w + x) * 4 + 1], px[(y * w + x) * 4 + 2]];
// background colour: median of the border pixels
const border = [];
for (let x = 0; x < w; x++) border.push(at(x, 0), at(x, h - 1));
for (let y = 0; y < h; y++) border.push(at(0, y), at(w - 1, y));
const med = i => border.map(c => c[i]).sort((a, b) => a - b)[border.length >> 1];
const bg = [med(0), med(1), med(2)];
const dist = c => Math.hypot(c[0] - bg[0], c[1] - bg[1], c[2] - bg[2]);
// flood fill from the border through background-like pixels (keeps dark pixels inside the subject)
const isBg = new Uint8Array(w * h), stack = [];
for (let x = 0; x < w; x++) stack.push([x, 0], [x, h - 1]);
for (let y = 0; y < h; y++) stack.push([0, y], [w - 1, y]);
while (stack.length) {
  const [x, y] = stack.pop();
  if (x < 0 || y < 0 || x >= w || y >= h || isBg[y * w + x] || dist(at(x, y)) > 34) continue;
  isBg[y * w + x] = 1;
  stack.push([x + 1, y], [x - 1, y], [x, y + 1], [x, y - 1]);
}
let x0 = w, y0 = h, x1 = -1, y1 = -1;
for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) if (!isBg[y * w + x]) { x0 = Math.min(x0, x); y0 = Math.min(y0, y); x1 = Math.max(x1, x); y1 = Math.max(y1, y); }
const side = Math.max(x1 - x0 + 1, y1 - y0 + 1), ox = x0 - (side - (x1 - x0 + 1)) / 2, oy = y0 - (side - (y1 - y0 + 1)) / 2;
const out = Buffer.alloc(N * N * 4);
for (let j = 0; j < N; j++) for (let i = 0; i < N; i++) {
  let r = 0, g = 0, b = 0, a = 0, n = 0;
  const sx0 = ox + i * side / N, sx1 = ox + (i + 1) * side / N, sy0 = oy + j * side / N, sy1 = oy + (j + 1) * side / N;
  for (let sy = Math.floor(sy0); sy < Math.ceil(sy1); sy++) for (let sx = Math.floor(sx0); sx < Math.ceil(sx1); sx++) {
    n++;
    if (sx < 0 || sy < 0 || sx >= w || sy >= h || isBg[sy * w + sx]) continue;
    const c = at(sx, sy); r += c[0]; g += c[1]; b += c[2]; a++;
  }
  const o = (j * N + i) * 4;
  if (a * 2 >= n) { out[o] = r / a; out[o + 1] = g / a; out[o + 2] = b / a; out[o + 3] = 255; }
}
fs.writeFileSync(outPath, writePng(out, N, N));
console.log(outPath, `${N}x${N}`, "bg", bg.join(","));
