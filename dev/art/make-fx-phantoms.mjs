// 분신 무기 (basic attack phantoms): small glowing bow / sword / staff models shown beside the player while basic-attacking.
//   node dev/art/make-fx-phantoms.mjs
//   -> pipi-facilities/assets/pipifacilities/models/item/fx_phantom_{bow,sword,staff}.json + textures/item/fx_phantom_*.png
// Model space: 16 px = 1 block at display scale 1, centred on (8, 8, 8); forward = +Z (ClassSkills.facing), up = +Y.
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { writePng } from "./class-icons/pngio.mjs";
const here = path.dirname(fileURLToPath(import.meta.url));
const A = path.resolve(here, "../pipi-facilities/assets/pipifacilities");
const r3 = v => Math.round(v * 1000) / 1000;
const rgb = h => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];

function model(id, colours, build) {
  // texture: 4 swatches in a row (pale highlight, main glow, deep, dark grip)
  const px = Buffer.alloc(16 * 16 * 4), sw = colours.map(rgb);
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) { const c = sw[Math.floor(x / 4)], o = (y * 16 + x) * 4; px[o] = c[0]; px[o + 1] = c[1]; px[o + 2] = c[2]; px[o + 3] = 255; }
  fs.writeFileSync(path.join(A, `textures/item/fx_${id}.png`), writePng(px, 16, 16));
  const UV = { pale: [0, 0, 4, 4], main: [4, 0, 8, 4], deep: [8, 0, 12, 4], dark: [12, 0, 16, 4] }, elements = [];
  const box = (from, to, sw, rot) => {
    const f = { uv: UV[sw], texture: "#0" };
    const el = { from: from.map(r3), to: to.map(r3), shade: false, faces: { north: f, east: f, south: f, west: f, up: f, down: f } };
    if (rot) el.rotation = rot;
    elements.push(el);
  };
  build(box);
  fs.writeFileSync(path.join(A, `models/item/fx_${id}.json`), JSON.stringify({
    textures: { "0": `pipifacilities:item/fx_${id}`, particle: `pipifacilities:item/fx_${id}` }, elements,
    display: { fixed: { rotation: [0, 0, 0], translation: [0, 0, 0], scale: [1, 1, 1] }, gui: { rotation: [0, 0, 0], translation: [0, 0, 0], scale: [.6, .6, .6] } },
  }));
  console.log(`fx_${id}`, elements.length, "elements");
}

// 초록 분신 활: recurve bow, grip in the middle, limbs bend back toward the string (string side = -Z, toward the archer)
model("phantom_bow", ["#e6ffe9", "#5ae66e", "#2a9a45", "#1d4a2a"], box => {
  box([7.2, 6, 7.2], [8.8, 10, 8.8], "dark");                                   // grip
  box([7.4, 5.6, 7.4], [8.6, 6.2, 8.6], "pale"); box([7.4, 9.8, 7.4], [8.6, 10.4, 8.6], "pale"); // grip bands
  box([7.4, 10, 8.2], [8.6, 16.5, 9.4], "main", { angle: -22.5, axis: "x", origin: [8, 10, 8.8] });   // upper limb
  box([7.4, -.5, 8.2], [8.6, 6, 9.4], "main", { angle: 22.5, axis: "x", origin: [8, 6, 8.8] });       // lower limb
  box([7.6, 15.6, 6.0], [8.4, 17.0, 7.0], "pale"); box([7.6, -1.0, 6.0], [8.4, .4, 7.0], "pale");     // glowing tips
  box([7.85, .2, 6.25], [8.15, 15.8, 6.55], "pale");                             // string
  box([7.7, 7.6, 9.4], [8.3, 8.4, 10.2], "deep");                                // arrow rest
});

// 빨간 분신 검 (장검): long slender blade with a fuller, straight cross guard, long two-hand grip with wraps, round pommel
model("phantom_sword", ["#ffe6e2", "#ff5a50", "#b8262a", "#4a1416"], box => {
  box([7.4, 6, 7.75], [8.6, 24, 8.25], "main");                                 // blade (long, narrow)
  box([7.8, 6, 7.65], [8.2, 23.4, 8.35], "pale");                               // fuller glow
  box([7.55, 23.6, 7.8], [8.45, 24.5, 8.2], "main", { angle: 45, axis: "z", origin: [8, 24, 8] }); // tip
  box([7.7, 24.3, 7.85], [8.3, 25.4, 8.15], "pale");
  box([4.6, 5.1, 7.4], [11.4, 6, 8.6], "deep");                                 // straight cross guard
  box([4.3, 5.0, 7.6], [4.9, 6.1, 8.4], "pale"); box([11.1, 5.0, 7.6], [11.7, 6.1, 8.4], "pale");
  box([7.5, .2, 7.5], [8.5, 5.1, 8.5], "dark");                                 // long grip
  for (const y of [1, 2.4, 3.8]) box([7.4, y, 7.4], [8.6, y + .5, 8.6], "deep"); // wraps
  box([7.15, -.9, 7.15], [8.85, .2, 8.85], "pale", { angle: 45, axis: "y", origin: [8, 0, 8] }); // pommel
});

// 보라 분신 지팡이: dark shaft, two crescent prongs holding a glowing orb
model("phantom_staff", ["#f3e6ff", "#b05aff", "#6a2ab8", "#2a1640"], box => {
  box([7.4, -2.5, 7.4], [8.6, 12, 8.6], "dark");                                // shaft
  box([7.1, 11, 7.1], [8.9, 12.2, 8.9], "deep"); box([7.2, 3, 7.2], [8.8, 3.8, 8.8], "deep"); // rings
  box([7.6, -3.2, 7.6], [8.4, -2.5, 8.4], "pale");                              // butt cap
  box([5.4, 12, 7.5], [6.4, 16.5, 8.5], "deep", { angle: -22.5, axis: "z", origin: [6.4, 12, 8] });  // prongs
  box([9.6, 12, 7.5], [10.6, 16.5, 8.5], "deep", { angle: 22.5, axis: "z", origin: [9.6, 12, 8] });
  box([6.4, 12.9, 6.6], [9.6, 16.1, 9.4], "main"); box([6.6, 12.7, 6.4], [9.4, 16.3, 9.6], "main"); box([6.6, 12.6, 6.6], [9.4, 16.4, 9.4], "main"); // orb
  box([7.3, 13.8, 6.3], [8.5, 15.0, 6.5], "pale");                              // orb glint
});
