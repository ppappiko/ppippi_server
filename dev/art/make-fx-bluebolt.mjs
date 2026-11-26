// 청룡의 진노 (궁수 보스 궁극기) blue lightning bolt effect model (original design: glowing orb on top, a jagged bolt with small forks,
// a spark splash at the foot). node dev/art/make-fx-bluebolt.mjs
//  -> pipi-facilities/assets/pipifacilities/models/item/fx_blue_bolt.json + textures/item/fx_blue_bolt.png
// Model space: 16 px = 1 block at display scale 1; the bolt spans y -8 .. 31 (the display is spawned 1 block x scale above the ground).
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { writePng } from "./class-icons/pngio.mjs";
const here = path.dirname(fileURLToPath(import.meta.url));
const A = path.resolve(here, "../pipi-facilities/assets/pipifacilities");
const r3 = v => Math.round(v * 1000) / 1000;

// texture: 4 swatches in the top row (white core, pale cyan, cyan, deep blue)
const px = Buffer.alloc(16 * 16 * 4);
const sw = ["#ffffff", "#c8f6ff", "#62d6ff", "#2f7dff"].map(h => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)]);
for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) { const c = sw[Math.min(3, Math.floor(x / 4))], o = (y * 16 + x) * 4; px[o] = c[0]; px[o + 1] = c[1]; px[o + 2] = c[2]; px[o + 3] = 255; }
fs.writeFileSync(path.join(A, "textures/item/fx_blue_bolt.png"), writePng(px, 16, 16));
const UV = { white: [0, 0, 4, 4], pale: [4, 0, 8, 4], cyan: [8, 0, 12, 4], blue: [12, 0, 16, 4] };

const elements = [];
const box = (from, to, swatch, rot) => {
  const f = { uv: UV[swatch], texture: "#0" };
  const el = { from: from.map(r3), to: to.map(r3), shade: false, faces: { north: f, east: f, south: f, west: f, up: f, down: f } };
  if (rot) el.rotation = rot;
  elements.push(el);
};
const ANG = [-45, -22.5, 0, 22.5, 45];
/** A bolt segment from P to Q (x, y in px, z = 8): a cyan sheath with a white core, as a vertical box turned about z, plus its twin in the z-y plane. */
const seg = (P, Q, w = 2.2) => {
  const dx = Q[0] - P[0], dy = Q[1] - P[1], len = Math.hypot(dx, dy) + .6;
  let a = -Math.atan2(dx, dy) * 180 / Math.PI;                    // angle from vertical
  a = ANG.reduce((b, v) => Math.abs(v - a) < Math.abs(b - a) ? v : b, 0);
  const cx = (P[0] + Q[0]) / 2, cy = (P[1] + Q[1]) / 2, rot = a ? { angle: a, axis: "z", origin: [cx, cy, 8] } : null;
  box([cx - w / 2, cy - len / 2, 8 - w / 2], [cx + w / 2, cy + len / 2, 8 + w / 2], "cyan", rot);
  box([cx - w / 4, cy - len / 2 - .2, 8 - w / 4 - .3], [cx + w / 4, cy + len / 2 + .2, 8 + w / 4 + .3], "white", rot);
  // the same segment again in the z-y plane (turned about x): two crossed zigzags, so the bolt has the same thickness from every side
  const cz = 16 - cx, rx = a ? { angle: XSIGN * a, axis: "x", origin: [8, cy, cz] } : null;
  box([8 - w / 2, cy - len / 2, cz - w / 2], [8 + w / 2, cy + len / 2, cz + w / 2], "cyan", rx);
  box([8 - w / 4 - .3, cy - len / 2 - .2, cz - w / 4], [8 + w / 4 + .3, cy + len / 2 + .2, cz + w / 4], "white", rx);
};
const XSIGN = +(process.env.XSIGN ?? 1); // right-handed x rotation: the twin path z = 16 - x needs the same angle as the z-plane segment
// main bolt (top -> bottom zigzag)
const path1 = [[8, 27], [10.5, 21], [6.5, 15.5], [10.5, 9.5], [6.5, 3.5], [9.5, -2.5], [8, -8]];
for (let i = 0; i < path1.length - 1; i++) seg(path1[i], path1[i + 1], i === 0 ? 2.6 : 2.2);
// forks
seg([10.5, 21], [14, 17], 1.4); seg([6.5, 15.5], [3, 12], 1.4); seg([10.5, 9.5], [13.5, 6.5], 1.2); seg([6.5, 3.5], [3.5, 0], 1.2);
// orb on top: blue shell, cyan body, white heart (kept inside the model limit of y <= 32: beyond it the model fails to load)
box([4.5, 24, 4.5], [11.5, 31, 11.5], "blue");
box([5, 24.5, 4], [11, 30.5, 12], "cyan"); box([4, 24.5, 5], [12, 30.5, 11], "cyan"); box([5, 23.5, 5], [11, 31.5, 11], "cyan");
box([6, 26, 5.6], [10, 30, 10.4], "pale"); box([6.8, 26.8, 5.3], [8.8, 28.8, 6], "white");
// spark splash at the foot
for (const [x, a] of [[4, 45], [12, -45], [6, 22.5], [10, -22.5]]) box([x - .6, -8, 7.4], [x + .6, -4.5, 8.6], "pale", { angle: a, axis: "z", origin: [x, -8, 8] });
box([3, -8.6, 3], [13, -8, 13], "cyan");

fs.writeFileSync(path.join(A, "models/item/fx_blue_bolt.json"), JSON.stringify({
  textures: { "0": "pipifacilities:item/fx_blue_bolt", particle: "pipifacilities:item/fx_blue_bolt" },
  elements,
  display: { fixed: { rotation: [0, 0, 0], translation: [0, 0, 0], scale: [1, 1, 1] }, gui: { rotation: [0, 0, 0], translation: [0, 0, 0], scale: [.4, .4, .4] } },
}));
console.log("fx_blue_bolt", elements.length, "elements");
