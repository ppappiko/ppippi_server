// Crisp 16x16 pixel-art icons for the ranch: node dev/art/make-ranch-icons.mjs
//  task_ranch.png (방목 적성, orange cow from the user's icon), wool.png (썬더쉽 털), hide.png (해달수 가죽)
import fs from "node:fs";
import { writePng } from "./class-icons/pngio.mjs";
function draw(art, pal, file) {
  const N = art.length, px = Buffer.alloc(N * N * 4);
  art.forEach((row, y) => [...row].forEach((c, x) => {
    if (c === "." || c === " ") return;
    const h = pal[c]; if (!h) throw new Error(`${file}: no colour for ${c}`);
    const o = (y * N + x) * 4;
    px[o] = parseInt(h.slice(1, 3), 16); px[o + 1] = parseInt(h.slice(3, 5), 16); px[o + 2] = parseInt(h.slice(5, 7), 16); px[o + 3] = 255;
  }));
  fs.writeFileSync(new URL(file, import.meta.url), writePng(px, N, N));
  console.log(file);
}
// 방목 적성: side view cow (head left), warm orange like the original icon
draw([
  "................",
  "................",
  "..o.o...........",
  ".oHoHo..........",
  "oLLLLLoooooooo..",
  "oLwLLLLLLLLLLLo.",
  "oLLLLLLLLLLLLLLo",
  ".oNNLLLLLLLLLLLt",
  "..oNoLLLLLLLLLot",
  "...o.oDLLLLLLDo.",
  ".....oDooDDooDo.",
  ".....oDo.oDo.oDo",
  ".....oko.oko.oko",
  "......o...o...o.",
  "................",
  "................",
], { o: "#5a3210", L: "#f0a53a", H: "#fff1c4", w: "#2a1a0a", N: "#f6c9a0", D: "#c97a22", k: "#3a2410", t: "#7a4614" }, "../pipi-travel/assets/pipitravel/textures/gui/task_ranch.png");
// 썬더쉽 털: fluffy wool bundle (shape after the reference), cream with a small yellow spark
draw([
  "................",
  "................",
  "......oooo......",
  "....ooWWWWoo....",
  "...oWWHHWWWWo...",
  "..oWWHHWWWWCWo..",
  "..oWHWWWCWWWWo..",
  ".oWWWWWWWWWYWWo.",
  ".oWWCWWWWWYYCWo.",
  ".oCWWWWCWWWYWWo.",
  "..oCWWWWWWWWCo..",
  "..oCCWCWWWCCCo..",
  "...ooCCCCCCoo...",
  ".....oooooo.....",
  "................",
  "................",
], { o: "#6b5a46", W: "#f3ead7", H: "#ffffff", C: "#cdbb9c", Y: "#f6d24a" }, "../pipi-facilities/assets/pipifacilities/textures/item/wool.png");
// 해달수 가죽: stretched leather hide with stitched edge
draw([
  "................",
  "..oo........oo..",
  ".oBBo......oBBo.",
  ".oBLBoooooooBLBo",
  "..oLLLLLLLLLLLo.",
  "..oLsLLLLLLLsLo.",
  "...oLLLHHLLLLo..",
  "...oLLHHLLLLLo..",
  "...oLsLLLLLsLo..",
  "...oLLLLLLLLLo..",
  "..oLLLLLLLLLLLo.",
  "..oLsLLLLLLLsLo.",
  ".oBLBoooooooBLBo",
  ".oBBo......oBBo.",
  "..oo........oo..",
  "................",
], { o: "#3d2412", B: "#7a4a26", L: "#a8693a", H: "#c98d57", s: "#e8c9a0" }, "../pipi-facilities/assets/pipifacilities/textures/item/hide.png");
