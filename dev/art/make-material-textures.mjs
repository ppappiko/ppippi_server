// Converts dev/art/materials/*.png (decoded from the .webp sources, smooth art on WHITE) into 32x32 item textures:
//   node dev/art/make-material-textures.mjs [--out <dir>]
// Near-white background connected to the border becomes transparent (interior highlights kept), bbox is fit
// into 32x32 keeping aspect, area-averaged (box filter), alpha quantized to 0/255.
// webp -> png decode: WPF BitmapDecoder via PowerShell (see git history of this task / PresentationCore).
import fs from "node:fs";
import path from "node:path";
import zlib from "node:zlib";
import { fileURLToPath } from "node:url";
import { Canvas, encodePNG } from "../pipi-pets/lib/bbmodel.mjs";

const here = path.dirname(fileURLToPath(import.meta.url));
const src = path.join(here, "materials");
const outArg = process.argv.indexOf("--out");
const dst = outArg > 0 ? path.resolve(process.argv[outArg + 1])
  : path.resolve(here, "../../plugins/ItemsAdder/contents/pipi_materials/textures/item");
fs.mkdirSync(dst, { recursive: true });
const SIZE = 32, WHITE = 228, CHROMA = 36;

function decodePNG(buf) {
  let p = 8, width = 0, height = 0, bitDepth = 0, colorType = 0, interlace = 0;
  const idat = [];
  while (p < buf.length) {
    const len = buf.readUInt32BE(p), type = buf.toString("ascii", p + 4, p + 8);
    const data = buf.subarray(p + 8, p + 8 + len);
    p += 12 + len;
    if (type === "IHDR") { width = data.readUInt32BE(0); height = data.readUInt32BE(4); bitDepth = data[8]; colorType = data[9]; interlace = data[12]; }
    else if (type === "IDAT") idat.push(data);
  }
  const ch = colorType === 6 ? 4 : colorType === 2 ? 3 : 0;
  if (!ch || bitDepth !== 8 || interlace) throw new Error(`unsupported PNG ${colorType}/${bitDepth}/${interlace}`);
  const raw = zlib.inflateSync(Buffer.concat(idat));
  const stride = width * ch, out = Buffer.alloc(height * stride);
  let pos = 0;
  for (let y = 0; y < height; y++) {
    const f = raw[pos++], line = raw.subarray(pos, pos + stride); pos += stride;
    const prev = y ? out.subarray((y - 1) * stride, y * stride) : null, cur = out.subarray(y * stride, (y + 1) * stride);
    for (let i = 0; i < stride; i++) {
      const a = i >= ch ? cur[i - ch] : 0, b = prev ? prev[i] : 0, c = prev && i >= ch ? prev[i - ch] : 0;
      let v = line[i];
      if (f === 1) v += a; else if (f === 2) v += b; else if (f === 3) v += (a + b) >> 1;
      else if (f === 4) { const pp = a + b - c, pa = Math.abs(pp - a), pb = Math.abs(pp - b), pc = Math.abs(pp - c); v += pa <= pb && pa <= pc ? a : pb <= pc ? b : c; }
      cur[i] = v & 255;
    }
  }
  const rgba = new Uint8Array(width * height * 4);
  for (let i = 0; i < width * height; i++) {
    rgba[i * 4] = out[i * ch]; rgba[i * 4 + 1] = out[i * ch + 1]; rgba[i * 4 + 2] = out[i * ch + 2];
    rgba[i * 4 + 3] = ch === 4 ? out[i * 4 + 3] : 255;
  }
  return { width, height, data: rgba };
}

function convert(file, name, from = src, to = dst) {
  const { width: w, height: h, data } = decodePNG(fs.readFileSync(path.join(from, file)));
  const light = i => { const r = data[i*4], g = data[i*4+1], b = data[i*4+2];
    return data[i*4+3] < 128 || (Math.min(r, g, b) > WHITE && Math.max(r, g, b) - Math.min(r, g, b) < CHROMA); };
  const bg = new Uint8Array(w * h), stack = [];
  const push = i => { if (!bg[i] && light(i)) { bg[i] = 1; stack.push(i); } };
  for (let x = 0; x < w; x++) { push(x); push((h - 1) * w + x); }
  for (let y = 0; y < h; y++) { push(y * w); push(y * w + w - 1); }
  while (stack.length) {
    const i = stack.pop(), x = i % w, y = (i / w) | 0;
    if (x > 0) push(i - 1); if (x < w - 1) push(i + 1); if (y > 0) push(i - w); if (y < h - 1) push(i + w);
  }
  // keep only the largest foreground component (drops stray specks)
  const lab = new Int32Array(w * h); let best = 0, bestN = 0, cur = 0;
  for (let s = 0; s < w * h; s++) {
    if (bg[s] || lab[s]) continue;
    cur++; let n = 0; const st = [s]; lab[s] = cur;
    while (st.length) { const i = st.pop(); n++; const x = i % w, y = (i / w) | 0;
      for (const j of [x > 0 ? i - 1 : -1, x < w - 1 ? i + 1 : -1, y > 0 ? i - w : -1, y < h - 1 ? i + w : -1])
        if (j >= 0 && !bg[j] && !lab[j]) { lab[j] = cur; st.push(j); } }
    if (n > bestN) { bestN = n; best = cur; }
  }
  for (let i = 0; i < w * h; i++) if (lab[i] !== best) bg[i] = 1;
  let x0 = w, y0 = h, x1 = -1, y1 = -1;
  for (let i = 0; i < w * h; i++) if (!bg[i]) { const x = i % w, y = (i / w) | 0; x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y); }
  const bw = x1 - x0 + 1, bh = y1 - y0 + 1, s = Math.max(bw, bh) / SIZE;
  const ow = Math.min(SIZE, Math.round(bw / s)), oh = Math.min(SIZE, Math.round(bh / s));
  const ox = (SIZE - ow) >> 1, oy = (SIZE - oh) >> 1;
  const canvas = new Canvas(SIZE, SIZE);
  const sx = bw / ow, sy = bh / oh;
  for (let ty = 0; ty < oh; ty++) for (let tx = 0; tx < ow; tx++) {
    let r = 0, g = 0, b = 0, n = 0, total = 0;
    for (let y = Math.floor(y0 + ty * sy); y < Math.floor(y0 + (ty + 1) * sy); y++)
      for (let x = Math.floor(x0 + tx * sx); x < Math.floor(x0 + (tx + 1) * sx); x++) {
        total++; const i = y * w + x;
        if (bg[i]) continue;
        r += data[i*4]; g += data[i*4+1]; b += data[i*4+2]; n++;
      }
    if (n * 2 >= total && n > 0) canvas.pixel(ox + tx, oy + ty, [Math.round(r / n), Math.round(g / n), Math.round(b / n), 255]);
  }
  fs.mkdirSync(to, { recursive: true });
  fs.writeFileSync(path.join(to, name + ".png"), encodePNG(canvas));
  fs.mkdirSync(path.join(from, "32"), { recursive: true });
  fs.writeFileSync(path.join(from, "32", name + ".png"), encodePNG(canvas));
  console.log(name, `art ${ow}x${oh}`, `src bbox ${bw}x${bh}`);
}

if (process.argv.includes("--food")) {
  // pet food (mod items): dev/art/food/*.png -> mod texture dir
  const food = path.join(here, "food"), modDst = path.resolve(here, "../pipi-facilities/assets/pipifacilities/textures/item");
  for (const n of ["straw", "pet_carrot"]) convert(n + ".png", n, food, modDst);
} else if (process.argv.includes("--tier4")) {
  // tier 4 items: dev/art/tier4/*.png -> mod texture dir, plus a drawn placeholder for warden_soul_stone
  const t4 = path.join(here, "tier4"), modDst = path.resolve(here, "../pipi-facilities/assets/pipifacilities/textures/item");
  for (const n of ["amber", "amber_ingot", "redwood_log", "redwood_plank", "cabbage", "ranch_blueprint", "vitality_crystal"]) convert(n + ".png", n, t4, modDst);
  const c = new Canvas(SIZE, SIZE), O = [6, 24, 30, 255], B = [11, 61, 71, 255], L = [24, 110, 124, 255], G = [41, 223, 235, 255], W = [190, 255, 255, 255];
  // gem silhouette (hexagon-ish): rows of [x0,x1]
  const rows = { 4: [12, 19], 5: [10, 21], 6: [8, 23], 7: [7, 24], 8: [6, 25], 9: [6, 25], 10: [6, 25], 11: [6, 25], 12: [6, 25], 13: [6, 25], 14: [6, 25], 15: [6, 25], 16: [6, 25], 17: [6, 25], 18: [7, 24], 19: [7, 24], 20: [8, 23], 21: [9, 22], 22: [10, 21], 23: [11, 20], 24: [13, 18], 25: [14, 17] };
  for (const [y, [a, b]] of Object.entries(rows)) for (let x = a; x <= b; x++) {
    const edge = x === a || x === b || +y === 4 || +y === 25;
    c.pixel(x, +y, edge ? O : (x < 10 || +y > 20) ? B : L);
  }
  // fill interior shading: dark body with lighter upper-left facets
  for (const [y, [a, b]] of Object.entries(rows)) for (let x = a + 1; x < b; x++) if (+y > 4 && +y < 25) c.pixel(x, +y, x - a < 3 && +y < 16 ? L : B);
  // glowing core
  for (let y = 11; y <= 18; y++) for (let x = 12; x <= 19; x++) { const d = Math.abs(x - 15.5) + Math.abs(y - 14.5); if (d < 5) c.pixel(x, y, d < 2 ? W : G); }
  for (const [x, y] of [[15, 9], [16, 9], [15, 20], [16, 20], [10, 14], [10, 15], [21, 14], [21, 15]]) c.pixel(x, y, G);
  fs.writeFileSync(path.join(modDst, "warden_soul_stone.png"), encodePNG(c));
  console.log("warden_soul_stone placeholder");
} else for (const n of ["plank", "plate", "reinforced_plank", "coated_plank", "repair_kit"]) convert(n + ".png", n);
