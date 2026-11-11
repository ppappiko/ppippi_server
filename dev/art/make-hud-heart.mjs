// Generates the HUD heart textures (28x28): node dev/art/make-hud-heart.mjs
//   heart_empty.png  dark outline heart with an empty (dark) interior
//   heart_full.png   same silhouette filled red with highlights (clipped bottom-up by HP in the client)
import fs from "node:fs";
import path from "node:path";
import zlib from "node:zlib";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const out = path.resolve(here, "../pipi-travel/assets/pipitravel/textures/gui/hud");
fs.mkdirSync(out, { recursive: true });

const crcT = new Uint32Array(256).map((_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const crc = b => { let c = 0xffffffff; for (const x of b) c = crcT[(c ^ x) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
function chunk(type, data) {
  const t = Buffer.from(type, "ascii"), l = Buffer.alloc(4), c = Buffer.alloc(4);
  l.writeUInt32BE(data.length); c.writeUInt32BE(crc(Buffer.concat([t, data])));
  return Buffer.concat([l, t, data, c]);
}
const N = 28;
function png(px) {
  const raw = Buffer.alloc((N * 4 + 1) * N);
  for (let y = 0; y < N; y++) { raw[y * (N * 4 + 1)] = 0; for (let x = 0; x < N * 4; x++) raw[y * (N * 4 + 1) + 1 + x] = px[y * N * 4 + x]; }
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(N, 0); ihdr.writeUInt32BE(N, 4); ihdr[8] = 8; ihdr[9] = 6;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk("IHDR", ihdr), chunk("IDAT", zlib.deflateSync(raw)), chunk("IEND", Buffer.alloc(0))]);
}

// heart silhouette: (x^2+y^2-1)^3 - x^2*y^3 <= 0
const S = 11.6;
const inside = (px, py) => {
  const x = (px + .5 - N / 2) / S, y = (N / 2 - (py + .5)) / S + .1;
  const a = x * x + y * y - 1;
  return a * a * a - x * x * y * y * y <= 0;
};
const mask = [];
for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) mask[y * N + x] = inside(x, y);
const m = (x, y) => x >= 0 && y >= 0 && x < N && y < N && mask[y * N + x];
// distance (chebyshev-ish) to the nearest outside pixel
function edgeDist(x, y) {
  for (let d = 1; d <= 3; d++)
    for (let dy = -d; dy <= d; dy++) for (let dx = -d; dx <= d; dx++)
      if (Math.max(Math.abs(dx), Math.abs(dy)) === d && !m(x + dx, y + dy)) return d;
  return 4;
}
const mix = (a, b, t) => a.map((v, i) => Math.round(v + (b[i] - v) * t));

function render(full) {
  const px = new Uint8Array(N * N * 4);
  const put = (x, y, c) => { const i = (y * N + x) * 4; px[i] = c[0]; px[i + 1] = c[1]; px[i + 2] = c[2]; px[i + 3] = 255; };
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    if (!m(x, y)) continue;
    const d = edgeDist(x, y);
    const t = y / (N - 1);
    if (d === 1) { put(x, y, [18, 6, 10]); continue; }
    if (full) {
      if (d === 2) { put(x, y, mix([226, 52, 58], [120, 12, 28], t)); continue; }
      let c = mix([245, 74, 78], [176, 20, 36], t);
      if (x > N / 2 + 2 && y > 12) c = mix(c, [130, 14, 30], .35); // shaded lower-right
      put(x, y, c);
    } else {
      if (d === 2) { put(x, y, [52, 26, 32]); continue; }
      put(x, y, mix([44, 22, 28], [30, 14, 20], t));
    }
  }
  if (full) {
    // highlights on the upper-left lobe
    for (const [x, y] of [[5, 6], [6, 5], [7, 5], [5, 7], [8, 5]]) if (m(x, y)) put(x, y, [255, 214, 214]);
    for (const [x, y] of [[6, 6], [7, 6], [5, 8]]) if (m(x, y)) put(x, y, [255, 150, 154]);
  }
  return png(px);
}

fs.writeFileSync(path.join(out, "heart_empty.png"), render(false));
fs.writeFileSync(path.join(out, "heart_full.png"), render(true));
console.log("wrote", out);
