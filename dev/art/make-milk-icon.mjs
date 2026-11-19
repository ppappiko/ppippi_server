// Pixel-art milk bottle (32x32) for the ranch product: node dev/art/make-milk-icon.mjs
import fs from "node:fs";
import { writePng } from "./class-icons/pngio.mjs";
const N = 32, px = Buffer.alloc(N * N * 4);
const hex = s => [parseInt(s.slice(1, 3), 16), parseInt(s.slice(3, 5), 16), parseInt(s.slice(5, 7), 16)];
const put = (x, y, c, a = 255) => { if (x < 0 || y < 0 || x >= N || y >= N) return; const o = (y * N + x) * 4; [px[o], px[o + 1], px[o + 2]] = hex(c); px[o + 3] = a; };
const OUT = "#3a3440", GLASS = "#cfe6f0", GLASS_D = "#9cc0d2", MILK = "#fbf8f0", MILK_S = "#e4ddd0", MILK_D = "#cfc5b4", CORK = "#b07a48", CORK_D = "#7e5230", CLOTH = "#5a9be0", CLOTH_D = "#3c74b8";
// body half-widths per row (bottle silhouette, rows 5..29)
const half = { 5: 3, 6: 3, 7: 3, 8: 3, 9: 3, 10: 4, 11: 5, 12: 7, 13: 8, 14: 8, 15: 8, 16: 8, 17: 8, 18: 8, 19: 8, 20: 8, 21: 8, 22: 8, 23: 8, 24: 8, 25: 8, 26: 8, 27: 8, 28: 7 };
const cx = 16;
for (const [ys, hw] of Object.entries(half)) {
  const y = +ys;
  for (let x = cx - hw; x < cx + hw; x++) {
    const edge = x === cx - hw || x === cx + hw - 1;
    let c;
    if (y >= 13) c = x < cx - hw + 3 ? MILK : x > cx + hw - 4 ? MILK_D : MILK_S; // milk fill with soft shading
    else c = GLASS;
    if (y >= 13 && y <= 14) c = y === 13 ? "#ffffff" : c;                       // milk surface highlight
    put(x, y, edge ? OUT : c);
  }
}
for (let x = cx - 7; x < cx + 7; x++) put(x, 29, OUT);                             // base
for (let y = 15; y <= 26; y++) put(cx - 5, y, "#ffffff", 200);                      // glass glint
for (let y = 7; y <= 10; y++) put(cx - 2, y, GLASS_D);
// cork
for (let y = 1; y <= 4; y++) for (let x = cx - 3; x < cx + 3; x++) put(x, y, x === cx - 3 || x === cx + 2 || y === 1 ? OUT : x > cx ? CORK_D : CORK);
// blue cloth band on the neck (ranch label)
for (let x = cx - 4; x < cx + 4; x++) { put(x, 5, x === cx - 4 || x === cx + 3 ? OUT : CLOTH); put(x, 6, x === cx - 4 || x === cx + 3 ? OUT : CLOTH_D); }
// small blue label with a white drop
for (let y = 18; y <= 23; y++) for (let x = cx - 3; x < cx + 4; x++) put(x, y, y === 18 || y === 23 || x === cx - 3 || x === cx + 3 ? CLOTH_D : CLOTH);
for (const [x, y] of [[cx, 19], [cx - 1, 20], [cx, 20], [cx + 1, 20], [cx - 1, 21], [cx, 21], [cx + 1, 21], [cx, 22]]) put(x, y, "#ffffff");
fs.writeFileSync(new URL("../pipi-facilities/assets/pipifacilities/textures/item/milk.png", import.meta.url), writePng(px, N, N));
console.log("milk.png");
