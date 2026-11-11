// Amber/orange recolour for amber_club: icon (from diamond_club.png) + head/rim textures used by amber_club.json.
//   node dev/art/make-amber-club.mjs
import fs from "node:fs";
import path from "node:path";
import zlib from "node:zlib";
import { fileURLToPath } from "node:url";
import { encodePNG } from "../pipi-pets/lib/bbmodel.mjs";

const here = path.dirname(fileURLToPath(import.meta.url));
const tex = path.resolve(here, "../pipi-facilities/assets/pipifacilities/textures/item");
const models = path.resolve(here, "../pipi-facilities/assets/pipifacilities/models/item");

function decodePNG(buf) {
  let p = 8, w = 0, h = 0, ct = 0; const idat = [];
  while (p < buf.length) { const len = buf.readUInt32BE(p), t = buf.toString("ascii", p + 4, p + 8), d = buf.subarray(p + 8, p + 8 + len); p += 12 + len;
    if (t === "IHDR") { w = d.readUInt32BE(0); h = d.readUInt32BE(4); ct = d[9]; } else if (t === "IDAT") idat.push(d); }
  const ch = ct === 6 ? 4 : ct === 2 ? 3 : 0; if (!ch) throw new Error("unsupported png " + ct);
  const raw = zlib.inflateSync(Buffer.concat(idat)), stride = w * ch, out = Buffer.alloc(h * stride); let pos = 0;
  for (let y = 0; y < h; y++) { const f = raw[pos++], line = raw.subarray(pos, pos + stride); pos += stride;
    const prev = y ? out.subarray((y - 1) * stride, y * stride) : null, cur = out.subarray(y * stride, (y + 1) * stride);
    for (let i = 0; i < stride; i++) { const a = i >= ch ? cur[i - ch] : 0, b = prev ? prev[i] : 0, c = prev && i >= ch ? prev[i - ch] : 0; let v = line[i];
      if (f === 1) v += a; else if (f === 2) v += b; else if (f === 3) v += (a + b) >> 1;
      else if (f === 4) { const pp = a + b - c, pa = Math.abs(pp - a), pb = Math.abs(pp - b), pc = Math.abs(pp - c); v += pa <= pb && pa <= pc ? a : pb <= pc ? b : c; }
      cur[i] = v & 255; } }
  const px = []; for (let i = 0; i < w * h; i++) px.push([out[i * ch], out[i * ch + 1], out[i * ch + 2], ch === 4 ? out[i * 4 + 3] : 255]);
  return { w, h, px };
}
function rgb2hsl([r, g, b]) { r /= 255; g /= 255; b /= 255; const mx = Math.max(r, g, b), mn = Math.min(r, g, b); let h = 0, s = 0; const l = (mx + mn) / 2;
  if (mx !== mn) { const d = mx - mn; s = l > .5 ? d / (2 - mx - mn) : d / (mx + mn); h = mx === r ? (g - b) / d + (g < b ? 6 : 0) : mx === g ? (b - r) / d + 2 : (r - g) / d + 4; h /= 6; } return [h, s, l]; }
function hsl2rgb([h, s, l]) { const f = (p, q, t) => { t = (t + 1) % 1; return t < 1/6 ? p + (q - p) * 6 * t : t < .5 ? q : t < 2/3 ? p + (q - p) * (2/3 - t) * 6 : p; };
  if (!s) return [l, l, l].map(v => Math.round(v * 255)); const q = l < .5 ? l * (1 + s) : l + s - l * s, p = 2 * l - q;
  return [f(p, q, h + 1/3), f(p, q, h), f(p, q, h - 1/3)].map(v => Math.round(v * 255)); }
// shift hue to amber (~33 deg), boost saturation of the cyan-ish pixels
const amber = ([r, g, b, a]) => { const [h, s, l] = rgb2hsl([r, g, b]); const isCyan = s > 0.15 && h > 0.4 && h < 0.7;
  const [nr, ng, nb] = hsl2rgb([isCyan ? 0.09 : h, isCyan ? Math.min(1, s * 1.1) : s, l]); return [nr, ng, nb, a]; };
function write(name, w, h, px) { const rgba = new Uint8Array(w * h * 4); px.forEach((p, i) => rgba.set(p, i * 4));
  fs.writeFileSync(path.join(tex, name + ".png"), encodePNG({ width: w, height: h, data: rgba })); console.log("wrote", name); }

const icon = decodePNG(fs.readFileSync(path.join(tex, "diamond_club.png")));
write("amber_club", icon.w, icon.h, icon.px.map(p => p[3] ? amber(p) : p));
// model textures: diamond_block-like head and cyan rim, recoloured (generated amber pattern, 16x16)
const mk = (base, dark, lite) => Array.from({ length: 256 }, (_, i) => { const x = i % 16, y = (i / 16) | 0; const n = ((x * 7 + y * 13) % 5);
  const e = x === 0 || y === 0 || x === 15 || y === 15; return e ? dark : n === 0 ? lite : n === 1 ? dark : base; });
write("amber_club_head", 16, 16, mk([242, 162, 58, 255], [184, 98, 26, 255], [255, 200, 110, 255]));
write("amber_club_rim", 16, 16, mk([214, 120, 30, 255], [140, 72, 16, 255], [235, 150, 60, 255]));
const m = JSON.parse(fs.readFileSync(path.join(models, "diamond_club.json"), "utf8"));
m.textures.head = "pipifacilities:item/amber_club_head"; m.textures.rim = "pipifacilities:item/amber_club_rim";
fs.writeFileSync(path.join(models, "amber_club.json"), JSON.stringify(m, null, 2) + "\n"); console.log("wrote amber_club.json");
