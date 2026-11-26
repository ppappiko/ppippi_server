// 만월 (fx_full_moon): a round full moon for the mage ult 만월의 추억 — a layered voxel sphere (radius 7.5 px, centred in the
// 16 px cube, so 1 block across at display scale 1) in pale moonlight colours with a few darker craters on the front.
//   node dev/art/make-fx-moon.mjs  -> pipi-facilities/assets/pipifacilities/models/item/fx_full_moon.json + textures/item/fx_full_moon.png
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { writePng } from "./class-icons/pngio.mjs";
const here = path.dirname(fileURLToPath(import.meta.url));
const A = path.resolve(here, "../pipi-facilities/assets/pipifacilities");
const r3 = v => Math.round(v * 1000) / 1000;

// texture: 4 swatches (bright rim, moon face, crater, deep crater)
const px = Buffer.alloc(16 * 16 * 4);
const sw = ["#fffbe6", "#fff0b8", "#e3cf8c", "#c4ad6a"].map(h => [1, 3, 5].map(i => parseInt(h.slice(i, i + 2), 16)));
for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) { const c = sw[Math.floor(x / 4)], o = (y * 16 + x) * 4; px[o] = c[0]; px[o + 1] = c[1]; px[o + 2] = c[2]; px[o + 3] = 255; }
fs.writeFileSync(path.join(A, "textures/item/fx_full_moon.png"), writePng(px, 16, 16));
const UV = { rim: [0, 0, 4, 4], face: [4, 0, 8, 4], crater: [8, 0, 12, 4], deep: [12, 0, 16, 4] };

const elements = [];
const box = (from, to, swatch) => {
  const f = { uv: UV[swatch], texture: "#0" };
  elements.push({ from: from.map(r3), to: to.map(r3), shade: false, faces: { north: f, east: f, south: f, west: f, up: f, down: f } });
};
const R = 7.5, C = 8;
for (let y0 = .5; y0 < 15.5; y0 += 1.5) {
  const yc = y0 + .75, r = Math.sqrt(Math.max(0, R * R - (yc - C) ** 2));
  if (r < .8) continue;
  box([C - r, y0, C - r * .42], [C + r, y0 + 1.5, C + r * .42], "face");     // x-long slab
  box([C - r * .42, y0, C - r], [C + r * .42, y0 + 1.5, C + r], "face");     // z-long slab
  box([C - r * .74, y0, C - r * .74], [C + r * .74, y0 + 1.5, C + r * .74], "rim"); // diagonal fill
}
// craters on the front (-z) face
for (const [x, y, s, k] of [[5.5, 9.5, 2.2, "crater"], [9.8, 6, 1.6, "deep"], [9, 11, 1.2, "crater"], [6.2, 5.2, 1.1, "deep"]]) {
  const zf = C - Math.sqrt(Math.max(0, R * R - (x - C) ** 2 - (y - C) ** 2)) - .15;
  box([x - s / 2, y - s / 2, zf], [x + s / 2, y + s / 2, zf + .6], k);
}
const model = { textures: { 0: "pipifacilities:item/fx_full_moon", particle: "pipifacilities:item/fx_full_moon" }, elements,
  display: { fixed: { rotation: [0, 0, 0], translation: [0, 0, 0], scale: [1, 1, 1] }, gui: { rotation: [0, 0, 0], translation: [0, 0, 0], scale: [.6, .6, .6] } } };
fs.writeFileSync(path.join(A, "models/item/fx_full_moon.json"), JSON.stringify(model));
console.log("fx_full_moon", elements.length, "elements");
