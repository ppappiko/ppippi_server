// Sealed stele (봉인된 비석, boss gate of 깊은 어둠) textures + block/item models:  node dev/art/make-sealed-stele.mjs
// Warden palette: near-black slate, deep teal sculk, glowing cyan runes / seal (emissive via neoforge_data).
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { writePng } from "./class-icons/pngio.mjs";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "../pipi-facilities/assets/pipifacilities");
const tex = path.join(root, "textures/block");
fs.mkdirSync(tex, { recursive: true });

let seed = 4242;
const rnd = () => (seed = (seed * 1664525 + 1013904223) >>> 0) / 4294967296;
const hex = h => [1, 3, 5].map(i => parseInt(h.slice(i, i + 2), 16));
function img(w, h) {
  const px = Buffer.alloc(w * h * 4);
  return {
    w, h, px,
    set(x, y, c, a = 255) { if (x < 0 || y < 0 || x >= w || y >= h) return; const i = (y * w + x) * 4; px[i] = c[0]; px[i + 1] = c[1]; px[i + 2] = c[2]; px[i + 3] = a; },
    save(name) { fs.writeFileSync(path.join(tex, name + ".png"), writePng(px, w, h)); },
  };
}
const pick = list => hex(list[Math.floor(rnd() * list.length)]);

// ---- base: reinforced-deepslate-like bricks ----
{
  const t = img(16, 16), stone = ["#20272d", "#232b32", "#1c2228", "#262f36"], grout = hex("#0d1114"), hi = hex("#2f3a42");
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const row = y >> 2, off = row % 2 ? 4 : 0;
    const g = y % 4 === 3 || (x + off) % 8 === 7;
    t.set(x, y, g ? grout : y % 4 === 0 && rnd() < .5 ? hi : pick(stone));
  }
  t.save("stele_base");
}

// ---- sculk top: deep teal with faint cyan specks ----
{
  const t = img(16, 16), base = ["#0b2a31", "#0d333b", "#082127", "#0f3a43"], speck = hex("#2fd8e2"), dark = hex("#051418");
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const r = rnd();
    t.set(x, y, r < .05 ? speck : r < .15 ? dark : pick(base));
  }
  t.save("stele_sculk");
}

// ---- body (16x32): carved near-black slate with a recessed frame; runes live in the overlay ----
const BX0 = 3, BX1 = 13, BH = 22; // face window: columns 3..12, rows 0..21
{
  const t = img(16, 32), stone = ["#141b20", "#161f25", "#11171b", "#19232a"], edge = hex("#0a0e11"), lip = hex("#24313a");
  for (let y = 0; y < 32; y++) for (let x = 0; x < 16; x++) {
    let c = pick(stone);
    const fx = x - BX0, fy = y;
    if (fx === 0 || fx === 9 || fy === 0 || fy === BH - 1) c = lip;           // outer lip
    else if (fx === 1 || fx === 8 || fy === 1 || fy === BH - 2) c = edge;     // recess shadow
    t.set(x, y, c);
  }
  t.save("stele_body");
}

// ---- rune overlay (16x32, transparent): glowing glyph column + cracks, emissive ----
{
  const t = img(16, 32), glow = hex("#5ff6ee"), mid = hex("#22c3cf"), dim = hex("#137d88");
  const cx = 8; // centre column of the face window (3..12)
  // vertical seal line with glyph nodes
  for (let y = 3; y < BH - 3; y++) { if (y >= 9 && y <= 12) continue; t.set(cx - 1, y, y % 3 === 0 ? glow : mid); }
  const glyphs = [
    [[-2, 0], [-1, 0], [0, 0], [-2, 1], [0, 1]],            // ㅠ-ish
    [[-3, 0], [-2, -1], [-1, 0], [0, -1], [1, 0]],           // zigzag
    [[-2, -1], [-2, 0], [-2, 1], [0, -1], [0, 0], [0, 1]],   // ||
    [[-3, 0], [-2, 0], [0, 0], [1, 0], [-1, -1], [-1, 1]],   // cross
  ];
  [[4, 0], [7, 1], [15, 2], [18, 3]].forEach(([y, g]) => glyphs[g].forEach(([dx, dy]) => t.set(cx + dx, y + dy, glow)));
  // cracks spreading from the seal band (rows 9..12 are covered by the seal)
  [[5, 8], [4, 7], [4, 6], [11, 8], [12, 7], [5, 13], [4, 14], [11, 13], [12, 14], [12, 15]].forEach(([x, y]) => t.set(x, y, dim));
  // frame corner studs
  [[4, 2], [11, 2], [4, BH - 3], [11, BH - 3]].forEach(([x, y]) => t.set(x, y, mid));
  t.save("stele_rune");
}

// ---- seal band: bright cyan core with darker rim, emissive ----
{
  const t = img(16, 16);
  const ramp = ["#0f6f7a", "#1aa7b8", "#2fd3df", "#7ffcf2", "#c8fff9"].map(hex);
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const d = Math.min(y, 15 - y);                        // brighter in the middle rows
    const n = Math.sin((x + y * .7) * 1.3) * .5 + .5;     // flowing streaks
    const lvl = Math.max(0, Math.min(4, Math.round(d / 2 + n * 1.2)));
    t.set(x, y, ramp[lvl]);
  }
  t.save("stele_seal");
}

// ---- tendril (warden horn): dark teal ridged ----
{
  const t = img(16, 16), body = ["#1e4e57", "#245a64", "#1a454d"], ridge = hex("#0e2a30");
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) t.set(x, y, y % 4 === 0 ? ridge : pick(body));
  t.save("stele_tendril");
}

// ---- model ----
const T = { base: "#base", sculk: "#sculk", body: "#body", rune: "#rune", seal: "#seal", tendril: "#tendril" };
const GLOW = { block_light: 15, sky_light: 15 };
function faceUv(dir, f, t) {
  const sx = t[0] - f[0], sy = t[1] - f[1], sz = t[2] - f[2];
  const [w, h] = dir === "up" || dir === "down" ? [sx, sz] : dir === "north" || dir === "south" ? [sx, sy] : [sz, sy];
  return [0, 0, Math.min(16, w), Math.min(16, h)];
}
function box(from, to, texture, opts = {}) {
  const faces = {};
  for (const dir of opts.only || ["north", "east", "south", "west", "up", "down"]) {
    const face = { uv: opts.uv?.[dir] || opts.uvAll || faceUv(dir, from, to), texture: opts.tex?.[dir] || texture };
    faces[dir] = face;
  }
  const el = { from, to, faces };
  if (opts.glow) { el.shade = false; el.neoforge_data = GLOW; }
  return el;
}
const SIDES = ["north", "east", "south", "west"];
const bodyUv = [BX0, 0, BX1, BH / 2]; // 16x32 texture: 16 uv units = 32 px tall
const elements = [
  // plinth + sculk-topped step
  box([-3, 0, -3], [19, 3, 19], T.base),
  box([-1, 3, -1], [17, 5, 17], T.base, { tex: { up: T.sculk } }),
  // corner vents with cyan tips
  ...[[-2.5, -2.5], [16, -2.5], [-2.5, 16], [16, 16]].flatMap(([x, z]) => [
    box([x, 3, z], [x + 2.5, 7, z + 2.5], T.body, { uvAll: [3, 0, 8, 8] }),
    box([x + .5, 7, z + .5], [x + 2, 7.6, z + 2], T.seal, { glow: true }),
  ]),
  // obelisk body + emissive rune overlay
  box([3, 5, 3], [13, 27, 13], T.body, { uv: Object.fromEntries([...SIDES.map(d => [d, bodyUv]), ["up", [3, 0, 13, 5]], ["down", [3, 0, 13, 5]]]) }),
  box([2.95, 5, 2.95], [13.05, 27, 13.05], T.rune, { only: SIDES, uvAll: bodyUv, glow: true }),
  // seal band (the boss is bound here)
  box([2.4, 14, 2.4], [13.6, 17, 13.6], T.seal, { glow: true }),
  box([2.1, 13.5, 2.1], [13.9, 14, 13.9], T.base),
  box([2.1, 17, 2.1], [13.9, 17.5, 13.9], T.base),
  // cap + stepped tip with a glowing eye
  box([2, 27, 2], [14, 29, 14], T.base),
  box([4, 29, 4], [12, 30.5, 12], T.body, { uvAll: [3, 0, 11, 3] }),
  box([6, 30.5, 6], [10, 32, 10], T.seal, { glow: true }),
  // warden tendrils on both sides
  box([-1, 21, 6.5], [3, 23, 9.5], T.tendril),
  box([-2.5, 22, 6.5], [0, 29, 9.5], T.tendril),
  box([-2.2, 29, 6.8], [-0.3, 31, 9.2], T.seal, { glow: true }),
  box([13, 21, 6.5], [17, 23, 9.5], T.tendril),
  box([16, 22, 6.5], [18.5, 29, 9.5], T.tendril),
  box([16.3, 29, 6.8], [18.2, 31, 9.2], T.seal, { glow: true }),
];
const ns = n => "pipifacilities:block/" + n;
const model = {
  textures: { base: ns("stele_base"), sculk: ns("stele_sculk"), body: ns("stele_body"), rune: ns("stele_rune"), seal: ns("stele_seal"), tendril: ns("stele_tendril"), particle: ns("stele_body") },
  render_type: "minecraft:cutout",
  ambientocclusion: false,
  elements,
};
fs.writeFileSync(path.join(root, "models/block/sealed_stele.json"), JSON.stringify(model));
fs.writeFileSync(path.join(root, "blockstates/sealed_stele.json"), JSON.stringify({ variants: { "": { model: "pipifacilities:block/sealed_stele" } } }));
fs.writeFileSync(path.join(root, "models/item/sealed_stele.json"), JSON.stringify({
  parent: "pipifacilities:block/sealed_stele",
  display: {
    gui: { rotation: [30, 225, 0], translation: [0, -3.5, 0], scale: [0.36, 0.36, 0.36] },
    ground: { rotation: [0, 0, 0], translation: [0, 3, 0], scale: [0.25, 0.25, 0.25] },
    fixed: { rotation: [0, 0, 0], translation: [0, -3, 0], scale: [0.4, 0.4, 0.4] },
    thirdperson_righthand: { rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: [0.25, 0.25, 0.25] },
    firstperson_righthand: { rotation: [0, 45, 0], translation: [0, 0, 0], scale: [0.25, 0.25, 0.25] },
  },
}));
console.log("sealed_stele: " + elements.length + " elements");
