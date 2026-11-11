// Downscales the user's wheat side art to a 16x16 texture and renders an isometric box wrapped with it on 4 sides.
//   node dev/art/station/wheat-box-preview.mjs
import fs from "node:fs"; import zlib from "node:zlib"; import path from "node:path"; import { fileURLToPath } from "node:url";
const here = path.dirname(fileURLToPath(import.meta.url));

function decodePNG(buf) {
  let p = 8, w = 0, h = 0, ct = 0; const idat = [];
  while (p < buf.length) { const len = buf.readUInt32BE(p), type = buf.toString("ascii", p + 4, p + 8), data = buf.subarray(p + 8, p + 8 + len); p += 12 + len;
    if (type === "IHDR") { w = data.readUInt32BE(0); h = data.readUInt32BE(4); ct = data[9]; } else if (type === "IDAT") idat.push(data); }
  const ch = ct === 6 ? 4 : 3, raw = zlib.inflateSync(Buffer.concat(idat)), stride = w * ch, out = Buffer.alloc(h * stride); let pos = 0;
  for (let y = 0; y < h; y++) { const f = raw[pos++], line = raw.subarray(pos, pos + stride); pos += stride;
    for (let i = 0; i < stride; i++) { const a = i >= ch ? out[y * stride + i - ch] : 0, b = y ? out[(y - 1) * stride + i] : 0, c = y && i >= ch ? out[(y - 1) * stride + i - ch] : 0; let v = line[i];
      if (f === 1) v += a; else if (f === 2) v += b; else if (f === 3) v += (a + b) >> 1; else if (f === 4) { const pp = a + b - c, pa = Math.abs(pp - a), pb = Math.abs(pp - b), pc = Math.abs(pp - c); v += pa <= pb && pa <= pc ? a : pb <= pc ? b : c; }
      out[y * stride + i] = v & 255; } }
  return { w, h, ch, px: (x, y) => { const o = (y * w + x) * ch; return [out[o], out[o + 1], out[o + 2]]; } };
}
const crcT = new Uint32Array(256).map((_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const crc = b => { let c = ~0; for (const v of b) c = crcT[(c ^ v) & 255] ^ (c >>> 8); return ~c >>> 0; };
function encodePNG(w, h, rgba) {
  const raw = Buffer.alloc((w * 4 + 1) * h); for (let y = 0; y < h; y++) { raw[y * (w * 4 + 1)] = 0; rgba.copy(raw, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4); }
  const chunk = (t, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length); const td = Buffer.concat([Buffer.from(t), d]); const c = Buffer.alloc(4); c.writeUInt32BE(crc(td)); return Buffer.concat([l, td, c]); };
  const hd = Buffer.alloc(13); hd.writeUInt32BE(w, 0); hd.writeUInt32BE(h, 4); hd[8] = 8; hd[9] = 6;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk("IHDR", hd), chunk("IDAT", zlib.deflateSync(raw)), chunk("IEND", Buffer.alloc(0))]);
}

const src = decodePNG(fs.readFileSync(path.join(here, "wheat_side_src.png")));
const N = 16, side = [];
for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) side.push(src.px(Math.floor((x + .5) * src.w / N), Math.floor((y + .5) * src.h / N)));
const sideTex = Buffer.alloc(N * N * 4); side.forEach((c, i) => { sideTex[i * 4] = c[0]; sideTex[i * 4 + 1] = c[1]; sideTex[i * 4 + 2] = c[2]; sideTex[i * 4 + 3] = 255; });
fs.writeFileSync(path.join(here, "wheat_side.png"), encodePNG(N, N, sideTex));
// top face: wheat colours from the top 6 rows of the side art, shuffled per pixel
const topPool = side.slice(0, N * 6);
const top = []; for (let i = 0; i < N * N; i++) top.push(topPool[(i * 2654435761 >>> 0) % topPool.length]);

// isometric render
const S = 22, W = 760, H = 760, img = Buffer.alloc(W * H * 4);
for (let i = 0; i < W * H; i++) { img[i * 4] = 120; img[i * 4 + 1] = 158; img[i * 4 + 2] = 92; img[i * 4 + 3] = 255; }
const ox = W / 2, oy = H / 2 + 20;
const P = (x, y, z) => [ox + (x - z) * S * .866, oy + (x + z) * S * .5 - y * S]; // x,z in 0..16, y up
function quad(a, b, c, d, col, shade) {
  const pts = [a, b, c, d].map(v => P(...v)), xs = pts.map(p => p[0]), ys = pts.map(p => p[1]);
  for (let y = Math.floor(Math.min(...ys)); y <= Math.ceil(Math.max(...ys)); y++) for (let x = Math.floor(Math.min(...xs)); x <= Math.ceil(Math.max(...xs)); x++) {
    let inside = true; for (let i = 0; i < 4; i++) { const [x1, y1] = pts[i], [x2, y2] = pts[(i + 1) % 4]; if ((x2 - x1) * (y - y1) - (y2 - y1) * (x - x1) < 0) { inside = false; break; } }
    if (!inside || x < 0 || y < 0 || x >= W || y >= H) continue; const o = (y * W + x) * 4; img[o] = col[0] * shade; img[o + 1] = col[1] * shade; img[o + 2] = col[2] * shade;
  }
}
for (let u = 0; u < N; u++) for (let v = 0; v < N; v++) {
  // front-left face (z = 16), u along x, v down from the top
  quad([u, N - v, N], [u + 1, N - v, N], [u + 1, N - v - 1, N], [u, N - v - 1, N], side[v * N + u], .82);
  // front-right face (x = 16), u along z (mirrored so the art reads left→right)
  quad([N, N - v, N - u], [N, N - v, N - u - 1], [N, N - v - 1, N - u - 1], [N, N - v - 1, N - u], side[v * N + u], .64);
  // top face
  quad([u, N, v], [u + 1, N, v], [u + 1, N, v + 1], [u, N, v + 1], top[v * N + u], 1.0);
}
fs.mkdirSync(path.join(here, "..", "preview"), { recursive: true });
fs.writeFileSync(path.join(here, "..", "preview", "wheat_box.png"), encodePNG(W, H, img));
console.log("ok");
