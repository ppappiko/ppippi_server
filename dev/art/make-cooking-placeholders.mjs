// 32x32 placeholder textures for the cooking items (seasonings + dishes), zlib PNG without dependencies:
//   node dev/art/make-cooking-placeholders.mjs [--out <dir>]
import fs from "node:fs";
import path from "node:path";
import zlib from "node:zlib";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const outArg = process.argv.indexOf("--out");
const dst = outArg > 0 ? path.resolve(process.argv[outArg + 1])
  : path.resolve(here, "../pipi-facilities/assets/pipifacilities/textures/item");
fs.mkdirSync(dst, { recursive: true });
const S = 32;

function crc32(buf) {
  let c, crc = 0xffffffff;
  for (let n = 0; n < buf.length; n++) {
    c = (crc ^ buf[n]) & 0xff;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    crc = (crc >>> 8) ^ c;
  }
  return (crc ^ 0xffffffff) >>> 0;
}
function chunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const body = Buffer.concat([Buffer.from(type, "ascii"), data]);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(body));
  return Buffer.concat([len, body, crc]);
}
function png(px) {
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(S, 0); ihdr.writeUInt32BE(S, 4); ihdr[8] = 8; ihdr[9] = 6;
  const raw = Buffer.alloc(S * (S * 4 + 1));
  for (let y = 0; y < S; y++) { raw[y * (S * 4 + 1)] = 0; px.copy(raw, y * (S * 4 + 1) + 1, y * S * 4, (y + 1) * S * 4); }
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk("IHDR", ihdr), chunk("IDAT", zlib.deflateSync(raw)), chunk("IEND", Buffer.alloc(0))]);
}
function canvas() { return Buffer.alloc(S * S * 4); }
function set(px, x, y, c) { if (x < 0 || y < 0 || x >= S || y >= S) return; const i = (y * S + x) * 4; px[i] = c[0]; px[i + 1] = c[1]; px[i + 2] = c[2]; px[i + 3] = 255; }
function disc(px, cx, cy, r, c) { for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) if ((x - cx) ** 2 + (y - cy) ** 2 <= r * r) set(px, x, y, c); }
function rect(px, x0, y0, x1, y1, c) { for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) set(px, x, y, c); }
const dark = [40, 30, 24];

// seasoning jar: lid, glass body, coloured contents
function jar(body, lid) {
  const px = canvas();
  rect(px, 9, 8, 22, 26, dark); rect(px, 10, 9, 21, 25, [214, 226, 232]); rect(px, 10, 15, 21, 25, body);
  rect(px, 8, 4, 23, 8, dark); rect(px, 9, 5, 22, 7, lid);
  rect(px, 12, 10, 13, 14, [255, 255, 255]);
  return px;
}
// dish: plate + coloured food mound
function dish(food, accent) {
  const px = canvas();
  disc(px, 16, 19, 13, dark); disc(px, 16, 19, 12, [236, 238, 240]); disc(px, 16, 19, 9, [214, 218, 222]);
  disc(px, 16, 16, 7, food); disc(px, 13, 14, 3, accent); disc(px, 19, 17, 2, accent);
  rect(px, 14, 9, 17, 10, [255, 255, 255]);
  return px;
}
const items = {
  salt: jar([245, 245, 245], [200, 200, 205]),
  sugar: jar([255, 244, 214], [240, 150, 190]),
  pepper: jar([70, 62, 58], [90, 90, 96]),
  soy_sauce: jar([74, 38, 24], [190, 50, 40]),
  hearty_salmon_stew: dish([232, 142, 110], [120, 190, 90]),
  hearty_carrot_soup: dish([238, 140, 40], [250, 200, 120]),
  hearty_veggie_salad: dish([110, 190, 80], [235, 130, 50]),
  failed_dish: dish([90, 78, 64], [50, 44, 40]),
};
for (const [name, px] of Object.entries(items)) fs.writeFileSync(path.join(dst, name + ".png"), png(px));
console.log("wrote", Object.keys(items).length, "textures to", dst);
