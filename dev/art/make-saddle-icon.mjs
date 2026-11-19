// 16x16 saddle icon for 탑승 적성: node dev/art/make-saddle-icon.mjs
import fs from "node:fs";
import { writePng } from "./class-icons/pngio.mjs";
const N = 16, px = Buffer.alloc(N * N * 4);
const pal = { o: "#3a2414", b: "#9a5b2e", l: "#c47c3e", h: "#e3a564", d: "#6e3d1d", g: "#f2c14e", G: "#b8861e", s: "#8f949c" };
const art = [
  "................",
  "................",
  ".oo..........oo.",
  ".ohoo......ooho.",
  ".ohlloooooolllo.",
  "..olllhhhhllllo.",
  "..obllllllllbo..",
  "...obbbbbbbbo...",
  "....oddGgddo....",
  ".....od..do.....",
  ".....od..do.....",
  ".....os..so.....",
  "....ossssssso...",
  "....o......o....",
  "................",
  "................",
];
art.forEach((row, y) => [...row].forEach((c, x) => {
  if (c === ".") return;
  const h = pal[c], o = (y * N + x) * 4;
  px[o] = parseInt(h.slice(1, 3), 16); px[o + 1] = parseInt(h.slice(3, 5), 16); px[o + 2] = parseInt(h.slice(5, 7), 16); px[o + 3] = 255;
}));
fs.writeFileSync(new URL("../pipi-travel/assets/pipitravel/textures/gui/aptitude_mount.png", import.meta.url), writePng(px, N, N));
console.log("aptitude_mount.png");
