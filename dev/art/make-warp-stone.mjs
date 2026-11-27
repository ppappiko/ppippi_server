// 워프석 (warp_stone block) in Minecraft style: a big upright cyan crystal floating over a stepped stone platform with a
// glowing basin, flanked by two broken rune-carved pillars leaning outward and some fallen stones. Few chunky cubes; all the
// detail is in painted 16x16 pixel textures with vanilla-like shading (see memory: minecraft-style-models).
//   node dev/art/make-warp-stone.mjs
//   -> pipi-facilities/assets/pipifacilities/textures/block/ws_*.png, models/block/warp_stone.json, preview dev/art/preview/warp_stone*.png
import fs from "node:fs";
import path from "node:path";
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { writePng } from "./class-icons/pngio.mjs";
const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "../pipi-facilities/assets/pipifacilities");
const TEX = path.join(root, "textures/block");
fs.mkdirSync(TEX, { recursive: true });

let seed = 4242;
const rnd = () => (seed = (seed * 1664525 + 1013904223) >>> 0) / 4294967296;
const hex = h => [1, 3, 5].map(i => parseInt(h.slice(i, i + 2), 16));
/** paints a 16x16 texture: fn(x, y) -> "#rrggbb" (or null = transparent); every pixel gets a little noise like vanilla blocks */
function tex(name, fn, noise = 7) {
  const px = Buffer.alloc(16 * 16 * 4);
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const c = fn(x, y); if (!c) continue;
    const v = hex(c), j = Math.round((rnd() - .5) * 2 * noise), o = (y * 16 + x) * 4;
    px[o] = Math.max(0, Math.min(255, v[0] + j)); px[o + 1] = Math.max(0, Math.min(255, v[1] + j)); px[o + 2] = Math.max(0, Math.min(255, v[2] + j)); px[o + 3] = 255;
  }
  fs.writeFileSync(path.join(TEX, name + ".png"), writePng(px, 16, 16));
}

// ---- crystal: bright cyan facets with diagonal stripes, light top-left edge, dark bottom-right edge, glints
const CRY = ["#eafeff", "#9ff4ff", "#5fdcf2", "#33b6da", "#1f88b4", "#155f86"];
const crystal = shift => (x, y) => {
  if (x === 0 || y === 0) return CRY[Math.min(5, 1 + shift)];
  if (x === 15 || y === 15) return CRY[Math.min(5, 4 + shift)];
  if ((x - y + 32) % 11 === 0 && x > 1 && y > 1) return CRY[shift > 0 ? 1 : 0];   // bright diagonal stripe
  if ((x - y + 32) % 11 === 1) return CRY[Math.min(5, 1 + shift)];
  const band = Math.floor((x + y * .9) / 5) % 3;
  return CRY[Math.min(5, 2 + band + shift)];
};
tex("ws_crystal", crystal(0), 5);
tex("ws_crystal_dark", crystal(1), 5);
tex("ws_crystal_light", (x, y) => (x + y) % 7 === 0 ? CRY[0] : x < 2 || y < 2 ? CRY[0] : CRY[1 + ((x * 3 + y) % 2)], 4);

// ---- rune-carved stone: worn grey stone with a carved glyph column, chipped light edges, moss at the foot
const ST = ["#c3c7cb", "#a3a8ad", "#8a8f94", "#73787d", "#5b6065", "#43474b"];
const GLYPH = [  // carved grooves (1) in a 6x14 strip, drawn at x 5..10
  "011110", "010010", "011110", "000000", "110011", "011110", "001100", "000000", "101101", "111111", "000000", "011100", "010100", "111110"];
tex("ws_rune", (x, y) => {
  if (x === 0 || x === 15) return ST[4];
  if (y === 0) return ST[1];
  const g = y >= 1 && y <= 14 && x >= 5 && x <= 10 ? GLYPH[y - 1][x - 5] === "1" : false;
  if (g) return ST[5];
  if (y >= 13 && rnd() < .35) return "#5f7f3f";        // moss
  if (rnd() < .08) return ST[1];                       // chips
  return x < 3 ? ST[2] : x > 12 ? ST[3] : ST[2 + ((x + y) % 5 === 0 ? 1 : 0)];
});
tex("ws_rune_top", (x, y) => {
  if (x === 0 || y === 0 || x === 15 || y === 15) return ST[3];
  if (Math.abs(x - y) === 0 && x > 3 && x < 12) return ST[4];   // crack
  if (x === 11 && y > 2 && y < 8) return ST[4];
  return rnd() < .1 ? ST[1] : ST[2];
});

// ---- platform: stone-brick sides (two courses) and a polished top with a lighter rim and cracks
tex("ws_platform", (x, y) => {
  const row = y < 8 ? 0 : 1, joint = row === 0 ? x === 0 || x === 8 : x === 4 || x === 12;
  if (y === 7 || y === 15 || joint) return ST[4];
  if (y === 0 || y === 8) return ST[1];
  return rnd() < .07 ? "#6f8f4f" : ST[2 + (rnd() < .3 ? 1 : 0)];
});
tex("ws_platform_top", (x, y) => {
  if (x === 0 || y === 0 || x === 15 || y === 15) return ST[1];
  if (x === 1 || y === 1 || x === 14 || y === 14) return ST[3];
  if ((x === 5 && y > 3 && y < 9) || (y === 9 && x > 4 && x < 10) || (x === 10 && y > 9 && y < 13)) return ST[4];
  return ST[2];
});

// ---- glowing water in the basin
tex("ws_water", (x, y) => (x * 2 + y * 3) % 9 === 0 ? "#d8fdff" : ((x + y * 2) % 5 === 0 ? "#7ff0ff" : (x + y) % 3 === 0 ? "#3fd0ec" : "#56def4"), 6);

// ---- model (block space: 16 = 1 block; the crystal floats up to y 32)
const T = { cry: "#cry", dark: "#dark", light: "#light", rune: "#rune", rtop: "#rtop", plat: "#plat", ptop: "#ptop", water: "#water" };
const els = [];
/** cube with per-face textures: faces = { all, top, bottom, side } (texture keys) */
const cube = (from, to, f, rot) => {
  const side = f.side ?? f.all, top = f.top ?? f.all ?? side, bottom = f.bottom ?? f.all ?? side;   // every face needs a texture or the client drops the whole model
  const uv = (a, b) => [0, 0, Math.min(16, Math.max(1, Math.abs(b[0] - a[0]))), Math.min(16, Math.max(1, Math.abs(b[1] - a[1])))];
  const dx = to[0] - from[0], dy = to[1] - from[1], dz = to[2] - from[2];
  // stone bricks / floor / water tile by size; carved runes and crystal facets stretch the whole picture over the face
  const face = (t, w, h) => ({ texture: t, uv: (t === T.plat || t === T.ptop || t === T.water ? [0, 0, Math.min(16, Math.max(1, w)), Math.min(16, Math.max(1, h))] : [0, 0, 16, 16]).map(v => Math.round(v * 100) / 100) });
  const el = { from, to, faces: { north: face(side, dx, dy), south: face(side, dx, dy), east: face(side, dz, dy), west: face(side, dz, dy), up: face(top, dx, dz), down: face(bottom, dx, dz) } };
  if (rot) el.rotation = rot;
  for (const v of [...from, ...to]) if (v < -16 || v > 32) throw new Error("out of range " + from + " " + to);
  void uv;
  els.push(el);
};
// platform: wide lower step, upper step, glowing basin, rim stones
cube([-4, 0, -4], [20, 2, 20], { side: T.plat, top: T.ptop, bottom: T.plat });
cube([-1, 2, -1], [17, 4, 17], { side: T.plat, top: T.ptop, bottom: T.plat });
cube([2, 4, 2], [14, 4.4, 14], { all: T.water });
for (const [x, z] of [[-1, -1], [15, -1], [-1, 15], [15, 15]]) cube([x, 4, z], [x + 2, 5.5, z + 2], { side: T.rune, top: T.rtop });
// broken rune pillars leaning outward, with broken caps
const lean = (o, a) => ({ angle: a, axis: "z", origin: o });
cube([-3.5, 1, 5.5], [1.5, 21, 10.5], { side: T.rune, top: T.rtop }, lean([-1, 1, 8], 22.5));
cube([-3, 21, 5.5], [0, 23.5, 9], { side: T.rune, top: T.rtop }, lean([-1, 1, 8], 22.5));
cube([14.5, 1, 4], [19.5, 14, 9], { side: T.rune, top: T.rtop }, lean([17, 1, 6.5], -22.5));
cube([16.5, 14, 5], [19.5, 15.5, 8], { side: T.rune, top: T.rtop }, lean([17, 1, 6.5], -22.5));
// fallen stones and a toppled slab
cube([4, 2, 16], [12, 4.5, 22], { side: T.rune, top: T.rtop }, { angle: 22.5, axis: "y", origin: [8, 3, 19] });
cube([18, 0, 13], [21, 3, 16], { side: T.plat, top: T.rtop }, { angle: 45, axis: "y", origin: [19.5, 1.5, 14.5] });
cube([-6, 0, 12], [-3, 2.5, 15], { side: T.plat, top: T.rtop }, { angle: 22.5, axis: "y", origin: [-4.5, 1.2, 13.5] });
// floating crystal, upright: body, bevel plate, lower and upper points, tips
const tilt = undefined;   // the crystal stands straight up (the facet stripes in the texture give it the slant)
cube([3.5, 14.5, 4.5], [12.5, 26.5, 11.5], { side: T.cry, top: T.light, bottom: T.dark }, tilt);
cube([2.7, 16.5, 6], [3.5, 24.5, 10], { all: T.light }, tilt);
cube([4.8, 10.8, 5.6], [11.2, 14.6, 10.4], { side: T.dark, top: T.cry, bottom: T.dark }, tilt);
cube([6.4, 8.2, 6.8], [9.6, 10.9, 9.2], { all: T.dark }, tilt);
cube([4.8, 26.4, 5.6], [11.2, 29.8, 10.4], { side: T.cry, top: T.light }, tilt);
cube([6.4, 29.7, 6.8], [9.6, 32, 9.2], { all: T.light }, tilt);
// two small shards drifting beside it
cube([0, 17, 1.5], [2, 21, 3.5], { side: T.cry, top: T.light, bottom: T.dark }, { angle: 45, axis: "y", origin: [1, 19, 2.5] });
cube([14, 13, 12], [15.6, 16.5, 13.6], { side: T.cry, top: T.light, bottom: T.dark }, { angle: 45, axis: "y", origin: [14.8, 14.7, 12.8] });

const model = {
  render_type: "minecraft:cutout",
  textures: {
    particle: "pipifacilities:block/ws_crystal", cry: "pipifacilities:block/ws_crystal", dark: "pipifacilities:block/ws_crystal_dark", light: "pipifacilities:block/ws_crystal_light",
    rune: "pipifacilities:block/ws_rune", rtop: "pipifacilities:block/ws_rune_top", plat: "pipifacilities:block/ws_platform", ptop: "pipifacilities:block/ws_platform_top", water: "pipifacilities:block/ws_water",
  },
  elements: els,
  display: {
    gui: { rotation: [30, 225, 0], translation: [0, -3, 0], scale: [.36, .36, .36] },
    ground: { rotation: [0, 0, 0], translation: [0, 2, 0], scale: [.25, .25, .25] },
    fixed: { rotation: [0, 0, 0], translation: [0, -2, 0], scale: [.4, .4, .4] },
    thirdperson_righthand: { rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: [.3, .3, .3] },
    firstperson_righthand: { rotation: [0, 45, 0], translation: [0, 0, 0], scale: [.3, .3, .3] },
  },
};
const file = path.join(root, "models/block/warp_stone.json");
fs.writeFileSync(file, JSON.stringify(model));
console.log("warp_stone:", els.length, "cubes");
const pv = path.join(here, "preview");
execFileSync("node", [path.join(here, "preview-station.mjs"), "--model", file, "--out", path.join(pv, "warp_stone.png"), "--size", "480"], { env: { ...process.env, FRONT: "1" } });
execFileSync("node", [path.join(here, "preview-station.mjs"), "--model", file, "--out", path.join(pv, "warp_stone_side.png"), "--size", "480"]);
