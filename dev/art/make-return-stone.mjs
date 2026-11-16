// 귀환석 (return stone) item texture: dev/art/return_stone_src.webp (1024x1024, baked checkerboard bg)
//   -> dev/pipi-travel/assets/pipitravel/textures/item/return_stone.png (32x32)
//   -> dev/art/return_stone_preview.png (8x upscale preview)
// Run: node dev/art/make-return-stone.mjs
// The .webp is decoded with PowerShell WPF (BitmapDecoder) to a temp PNG, then:
//  1) flood-fill from the image border over every non-outline pixel (checker greys/whites and
//     the faint glow outside the outline), stopping at the dark outline -> background mask;
//  2) majority-sample each 32x32 source cell down to one pixel (cell is transparent if mostly
//     background; dark outline wins when it covers a good share of the cell so it stays readable).
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { readPng, writePng } from "./class-icons/pngio.mjs";

const here = path.dirname(fileURLToPath(import.meta.url));
const SRC = path.join(here, "return_stone_src.webp");
const OUT = path.join(here, "..", "pipi-travel", "assets", "pipitravel", "textures", "item", "return_stone.png");
const PREVIEW = path.join(here, "return_stone_preview.png");
const N = 32;

// --- decode webp -> png via WPF ---
const tmp = path.join(os.tmpdir(), "return_stone_src_" + process.pid + ".png");
const ps = `Add-Type -AssemblyName PresentationCore
$s=[IO.File]::OpenRead('${SRC.replace(/'/g, "''")}')
$d=[System.Windows.Media.Imaging.BitmapDecoder]::Create($s,'None','OnLoad')
$c=New-Object System.Windows.Media.Imaging.FormatConvertedBitmap($d.Frames[0],[System.Windows.Media.PixelFormats]::Bgra32,$null,0)
$e=New-Object System.Windows.Media.Imaging.PngBitmapEncoder
$e.Frames.Add([System.Windows.Media.Imaging.BitmapFrame]::Create($c))
$o=[IO.File]::Create('${tmp.replace(/'/g, "''")}'); $e.Save($o); $o.Close(); $s.Close()`;
execFileSync("powershell.exe", ["-NoProfile", "-NonInteractive", "-Command", ps], { stdio: "inherit" });
const { w, h, px } = readPng(fs.readFileSync(tmp));
fs.unlinkSync(tmp);

// --- background mask by flood fill from the border ---
// outline = dark by luminance (the top-left outline is dark blue, e.g. 16/61/146, under the glow)
const isOutline = i => 0.3 * px[i * 4] + 0.59 * px[i * 4 + 1] + 0.11 * px[i * 4 + 2] < 95;
const bg = new Uint8Array(w * h);
const stack = [];
for (let x = 0; x < w; x++) stack.push(x, (h - 1) * w + x);
for (let y = 0; y < h; y++) stack.push(y * w, y * w + w - 1);
while (stack.length) {
  const i = stack.pop();
  if (bg[i] || isOutline(i)) continue;
  bg[i] = 1;
  const x = i % w, y = (i / w) | 0;
  if (x > 0) stack.push(i - 1);
  if (x < w - 1) stack.push(i + 1);
  if (y > 0) stack.push(i - w);
  if (y < h - 1) stack.push(i + w);
}
let bgCount = 0; for (const b of bg) bgCount += b;
console.log(`background ${(100 * bgCount / (w * h)).toFixed(1)}% of source`);

// --- majority downscale ---
const cs = w / N;
const out = Buffer.alloc(N * N * 4);
for (let cy = 0; cy < N; cy++) for (let cx = 0; cx < N; cx++) {
  let total = 0, bgN = 0, dark = [0, 0, 0, 0];
  const bins = new Map();
  for (let y = Math.floor(cy * cs); y < Math.floor((cy + 1) * cs); y++) for (let x = Math.floor(cx * cs); x < Math.floor((cx + 1) * cs); x++) {
    const i = y * w + x; total++;
    if (bg[i]) { bgN++; continue; }
    const r = px[i * 4], g = px[i * 4 + 1], b = px[i * 4 + 2];
    if (isOutline(i)) { dark[0] += r; dark[1] += g; dark[2] += b; dark[3]++; continue; }
    const key = (r >> 5) << 6 | (g >> 5) << 3 | (b >> 5);
    const e = bins.get(key) || [0, 0, 0, 0];
    e[0] += r; e[1] += g; e[2] += b; e[3]++; bins.set(key, e);
  }
  const o = (cy * N + cx) * 4;
  const fg = total - bgN;
  if (bgN > total * 0.5) continue; // transparent
  let pick;
  if (dark[3] >= fg * 0.3) pick = dark;
  else { pick = [0, 0, 0, 0]; for (const e of bins.values()) if (e[3] > pick[3]) pick = e; }
  out[o] = Math.round(pick[0] / pick[3]); out[o + 1] = Math.round(pick[1] / pick[3]); out[o + 2] = Math.round(pick[2] / pick[3]); out[o + 3] = 255;
}
// keep the silhouette edge readable: any opaque pixel touching transparency/border becomes outline
const solid = (x, y) => x >= 0 && y >= 0 && x < N && y < N && out[(y * N + x) * 4 + 3] > 0;
const edge = [];
for (let y = 0; y < N; y++) for (let x = 0; x < N; x++)
  if (solid(x, y) && (!solid(x - 1, y) || !solid(x + 1, y) || !solid(x, y - 1) || !solid(x, y + 1))) edge.push((y * N + x) * 4);
for (const o of edge) { out[o] = 20; out[o + 1] = 22; out[o + 2] = 28; }

fs.writeFileSync(OUT, writePng(out, N, N));
const S = 8, prev = Buffer.alloc(N * S * N * S * 4);
for (let y = 0; y < N * S; y++) for (let x = 0; x < N * S; x++) {
  const so = (((y / S) | 0) * N + ((x / S) | 0)) * 4, d = (y * N * S + x) * 4;
  if (out[so + 3]) out.copy(prev, d, so, so + 4);
  else { const c = ((x >> 3) + (y >> 3)) & 1 ? 200 : 235; prev[d] = prev[d + 1] = prev[d + 2] = c; prev[d + 3] = 255; }
}
fs.writeFileSync(PREVIEW, writePng(prev, N * S, N * S));
console.log("wrote", OUT, "and", PREVIEW);
