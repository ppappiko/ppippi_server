// Converts dev/art/cores/*.png (big pixel art on WHITE) into 32x32 RGBA item textures: node dev/art/make-core-textures.mjs
//   node dev/art/make-blueprint-textures.mjs
// Black background connected to the border becomes transparent; art bbox is fit into 32x32.
import fs from "node:fs";
import path from "node:path";
import zlib from "node:zlib";
import { fileURLToPath } from "node:url";
import { Canvas, encodePNG } from "../pipi-pets/lib/bbmodel.mjs";

const here = path.dirname(fileURLToPath(import.meta.url));
const src = path.join(here, "cores");
const dst = path.resolve(here, "../../plugins/ItemsAdder/contents/pipi_cores/textures/item");
fs.mkdirSync(dst, { recursive: true });
const SIZE = 32, DARK = 40;

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

function convert(file, name) {
  const { width: w, height: h, data } = decodePNG(fs.readFileSync(path.join(src, file)));
  const dark = i => data[i * 4 + 3] < 128 || (data[i * 4] > 235 && data[i * 4 + 1] > 235 && data[i * 4 + 2] > 235);
  const bg = new Uint8Array(w * h), stack = [];
  const push = i => { if (!bg[i] && dark(i)) { bg[i] = 1; stack.push(i); } };
  for (let x = 0; x < w; x++) { push(x); push((h - 1) * w + x); }
  for (let y = 0; y < h; y++) { push(y * w); push(y * w + w - 1); }
  while (stack.length) {
    const i = stack.pop(), x = i % w, y = (i / w) | 0;
    if (x > 0) push(i - 1); if (x < w - 1) push(i + 1); if (y > 0) push(i - w); if (y < h - 1) push(i + w);
  }
  let x0 = w, y0 = h, x1 = -1, y1 = -1;
  for (let i = 0; i < w * h; i++) if (!bg[i]) { const x = i % w, y = (i / w) | 0; x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y); }
  const bw = x1 - x0 + 1, bh = y1 - y0 + 1, s = Math.max(bw, bh) / SIZE;
  const ow = Math.min(SIZE, Math.round(bw / s)), oh = Math.min(SIZE, Math.round(bh / s));
  const ox = (SIZE - ow) >> 1, oy = (SIZE - oh) >> 1;
  const canvas = new Canvas(SIZE, SIZE);
  for (let ty = 0; ty < oh; ty++) for (let tx = 0; tx < ow; tx++) {
    // average the central half of the source block (nearest-neighbour style, noise tolerant)
    const sx0 = x0 + tx * s, sy0 = y0 + ty * s;
    let r = 0, g = 0, b = 0, n = 0, total = 0;
    for (let y = Math.floor(sy0 + s * .4); y <= Math.floor(sy0 + s * .6); y++)
      for (let x = Math.floor(sx0 + s * .4); x <= Math.floor(sx0 + s * .6); x++) {
        if (x < 0 || y < 0 || x >= w || y >= h) continue;
        total++;
        const i = y * w + x;
        if (bg[i]) continue;
        r += data[i * 4]; g += data[i * 4 + 1]; b += data[i * 4 + 2]; n++;
      }
    if (n * 2 > total && n > 0) canvas.pixel(ox + tx, oy + ty, [Math.round(r / n), Math.round(g / n), Math.round(b / n), 255]);
  }
  fs.writeFileSync(path.join(dst, name + ".png"), encodePNG(canvas));
  console.log(name, `${SIZE}x${SIZE}`, `art ${ow}x${oh}`, `src bbox ${bw}x${bh}`);
}

for (const [file, name] of [["iron_core.png", "iron_core"], ["diamond_core.png", "diamond_core"]]) convert(file, name);
