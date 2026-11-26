// Wailing stele (통곡의 비석, boss gate of 통곡의 왕좌 · 비탄의 망령 군주):  node dev/art/make-wailing-stele.mjs
// Designed for its boss (2026-11-26), not a recolour of the sealed stele: a pale ash-violet arched gravestone with a weeping
// face relief on both sides whose tears glow green (emissive), a broken gold crown resting on the arch (the lord's crown),
// a torn mourning veil hanging down both sides, and two tear chalices with glowing green tears on the plinth (the raid's
// 눈물 성배). Original design. Every boss gate gets its own stele design.
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { writePng } from "./class-icons/pngio.mjs";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "../pipi-facilities/assets/pipifacilities");
const tex = path.join(root, "textures/block");
fs.mkdirSync(tex, { recursive: true });

let seed = 9331;
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

// ---- plinth: ash-violet stone bricks ----
{
  const t = img(16, 16), stone = ["#4a4358", "#4f485e", "#453f52", "#544c63"], grout = hex("#2a2533"), hi = hex("#625a73");
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const row = y >> 2, off = row % 2 ? 4 : 0;
    const g = y % 4 === 3 || (x + off) % 8 === 7;
    t.set(x, y, g ? grout : y % 4 === 0 && rnd() < .5 ? hi : pick(stone));
  }
  t.save("wstele_base");
}

// ---- plinth top: ashen ground strewn with pale petals and a few glowing tear drops ----
{
  const t = img(16, 16), ground = ["#3a3446", "#3f3850", "#352f40", "#433c55"], petal = hex("#e9e2f2"), petal2 = hex("#c9bfdc"), tear = hex("#7dff9a");
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const r = rnd();
    t.set(x, y, r < .06 ? petal : r < .1 ? petal2 : r < .12 ? tear : pick(ground));
  }
  t.save("wstele_sculk");
}

// ---- tablet (16x32): pale stone with a weeping hooded face relief on rows 0..21; rows 22..31 plain stone for sides/back ----
const FX0 = 2, FX1 = 14, FH = 22;
{
  const t = img(16, 32), stone = ["#6f6880", "#746c86", "#69627a", "#7a7290"], lip = hex("#8d85a3"), recess = hex("#4b4459"), hood = hex("#5a5368"),
    eye = hex("#120e18"), mouth = hex("#2a2333"), carve = hex("#554e63");
  for (let y = 0; y < 32; y++) for (let x = 0; x < 16; x++) {
    let c = pick(stone);
    if (y < FH && x >= FX0 && x < FX1) {
      const fx = x - FX0, fy = y;
      if (fx === 0 || fx === 11 || fy === 0 || fy === FH - 1) c = lip;
      else if (fx === 1 || fx === 10 || fy === 1 || fy === FH - 2) c = recess;
      else {
        // hood: an arch around the face (rows 3..14)
        const dx = fx - 5.5, inHood = fy >= 3 && fy <= 14 && Math.abs(dx) <= 4.2 - (fy < 6 ? (6 - fy) * .8 : 0);
        if (inHood) c = hood;
        // eyes: hollow, slightly droopy (sorrow)
        if ((fy === 7 || fy === 8) && (fx === 3 || fx === 4 || fx === 7 || fx === 8)) c = eye;
        if (fy === 6 && (fx === 3 || fx === 8)) c = carve;         // brows sloping down outward
        if (fy === 5 && (fx === 4 || fx === 7)) c = carve;
        // sad mouth: corners drop
        if (fy === 11 && fx >= 4 && fx <= 7) c = mouth;
        if (fy === 12 && (fx === 3 || fx === 8)) c = mouth;
        // epitaph strokes under the face
        if (fy >= 16 && fy <= 19 && fy % 2 === 0 && fx >= 2 && fx <= 9 && (fx + fy) % 3 !== 0) c = carve;
      }
    }
    t.set(x, y, c);
  }
  t.save("wstele_body");
}

// ---- emissive overlay (16x32, transparent): eye glints and green tear streaks running down the face ----
{
  const t = img(16, 32), glow = hex("#9dffb5"), mid = hex("#4fe07a"), dim = hex("#2aa552");
  for (const ex of [FX0 + 3, FX0 + 8]) {                     // inner eye columns 5 and 10
    t.set(ex + (ex < 8 ? 1 : -1), 7, glow);                  // glint
    for (let y = 9; y < FH - 2; y++) {
      const wob = Math.round(Math.sin(y * .9 + ex) * .6);
      t.set(ex + wob, y, y < 13 ? glow : y < 17 ? mid : dim);
    }
    t.set(ex, FH - 2, mid);                                  // drop at the bottom
  }
  t.save("wstele_rune");
}

// ---- tears / chalice liquid: bright green, flowing ----
{
  const t = img(16, 16);
  const ramp = ["#1a6b35", "#2aa552", "#46d872", "#9dffb5", "#dcffe5"].map(hex);
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const n = Math.sin((x * .8 + y) * 1.1) * .5 + .5, d = Math.min(x, 15 - x, y, 15 - y);
    t.set(x, y, ramp[Math.max(0, Math.min(4, Math.round(1 + n * 2 + (d > 4 ? 1 : 0))))]);
  }
  t.save("wstele_seal");
}

// ---- torn mourning veil: dusk-purple cloth with a ragged (transparent) hem ----
{
  const t = img(16, 16), cloth = ["#3d2f5a", "#45366a", "#352a4f"], fold = hex("#2a2140");
  const hem = Array.from({ length: 16 }, () => 11 + Math.floor(rnd() * 5));
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    if (y > hem[x]) { t.set(x, y, [0, 0, 0], 0); continue; }
    t.set(x, y, x % 4 === 1 ? fold : pick(cloth));
  }
  t.save("wstele_veil");
}

// ---- gold crown ----
{
  const t = img(16, 16), gold = ["#e8b83a", "#f2c84e", "#d9a52c"], dark = hex("#9a6f1a"), shine = hex("#fff1a8");
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) t.set(x, y, y === 0 || x === 0 ? shine : y === 15 || x === 15 ? dark : pick(gold));
  t.save("wstele_crown");
}
for (const old of ["wstele_tendril"]) { const p = path.join(tex, old + ".png"); if (fs.existsSync(p)) fs.unlinkSync(p); }

// ---- model ----
const T = { base: "#base", sculk: "#sculk", body: "#body", rune: "#rune", seal: "#seal", veil: "#veil", crown: "#crown" };
const GLOW = { block_light: 15, sky_light: 15 };
function faceUv(dir, f, t) {
  const sx = t[0] - f[0], sy = t[1] - f[1], sz = t[2] - f[2];
  const [w, h] = dir === "up" || dir === "down" ? [sx, sz] : dir === "north" || dir === "south" ? [sx, sy] : [sz, sy];
  return [0, 0, Math.min(16, w), Math.min(16, h)];
}
function box(from, to, texture, opts = {}) {
  const faces = {};
  for (const dir of opts.only || ["north", "east", "south", "west", "up", "down"]) faces[dir] = { uv: opts.uv?.[dir] || opts.uvAll || faceUv(dir, from, to), texture: opts.tex?.[dir] || texture };
  const el = { from, to, faces };
  if (opts.glow) { el.shade = false; el.neoforge_data = GLOW; }
  return el;
}
// tablet: front and back show the weeping face (uv in 16ths: the 32 px texture is 16 uv tall), sides / top the plain stone rows
const faceUv16 = [FX0, 0, FX1, FH / 2], plain = [0, 11, 7, 16];
const chalice = (x, z) => [
  box([x, 3.5, z], [x + 2.6, 4, z + 2.6], T.crown),
  box([x + .95, 4, z + .95], [x + 1.65, 5.4, z + 1.65], T.crown),
  box([x + .2, 5.4, z + .2], [x + 2.4, 6.9, z + 2.4], T.crown),
  box([x + .45, 6.9, z + .45], [x + 2.15, 7.05, z + 2.15], T.seal, { glow: true, only: ["up"] }),
];
const elements = [
  // plinth (two steps) with the ash-and-petals top
  box([-2, 0, -2], [18, 2, 18], T.base, { tex: { up: T.sculk } }),
  box([0, 2, 0], [16, 3.5, 16], T.base, { tex: { up: T.sculk } }),
  // the gravestone tablet + arched top
  box([2, 3.5, 4.5], [14, 25, 11.5], T.body, { uv: { north: faceUv16, south: faceUv16, east: plain, west: plain, up: plain, down: plain } }),
  box([1.95, 3.5, 4.45], [14.05, 25, 4.5], T.rune, { only: ["north"], uvAll: faceUv16, glow: true }),
  box([1.95, 3.5, 11.5], [14.05, 25, 11.55], T.rune, { only: ["south"], uvAll: faceUv16, glow: true }),
  box([3, 25, 4.5], [13, 27, 11.5], T.body, { uvAll: plain }),
  box([4.5, 27, 4.5], [11.5, 28.5, 11.5], T.body, { uvAll: plain }),
  box([6, 28.5, 5], [10, 29.5, 11], T.body, { uvAll: plain }),
  // the lord's broken crown resting on the arch (front-right point snapped off), green tear gem in front
  box([5.6, 29.5, 6], [10.4, 30.3, 10], T.crown),
  box([5.6, 30.3, 6], [6.6, 31.9, 7], T.crown),
  box([9.4, 30.3, 6], [10.4, 30.9, 7], T.crown),
  box([5.6, 30.3, 9], [6.6, 31.9, 10], T.crown),
  box([9.4, 30.3, 9], [10.4, 31.9, 10], T.crown),
  box([7.5, 30.3, 7.5], [8.5, 31.5, 8.5], T.crown),
  box([7.4, 29.7, 5.85], [8.6, 30.6, 6.05], T.seal, { glow: true }),
  // torn mourning veil draped over both shoulders
  box([1.5, 9, 5], [2, 25.2, 11], T.veil, { only: ["east", "west"], uvAll: [0, 0, 16, 16] }),
  box([14, 9, 5], [14.5, 25.2, 11], T.veil, { only: ["east", "west"], uvAll: [0, 0, 16, 16] }),
  box([1.5, 25.2, 5], [14.5, 25.7, 11], T.veil, { only: ["up", "north", "south"], uvAll: [0, 0, 16, 2] }),
  // the tear chalices in front of the stone
  ...chalice(.2, .2), ...chalice(13.2, .2),
];
const ns = n => "pipifacilities:block/" + n;
const model = {
  textures: { base: ns("wstele_base"), sculk: ns("wstele_sculk"), body: ns("wstele_body"), rune: ns("wstele_rune"), seal: ns("wstele_seal"), veil: ns("wstele_veil"), crown: ns("wstele_crown"), particle: ns("wstele_body") },
  render_type: "minecraft:cutout",
  ambientocclusion: false,
  elements,
};
fs.writeFileSync(path.join(root, "models/block/wailing_stele.json"), JSON.stringify(model));
fs.writeFileSync(path.join(root, "blockstates/wailing_stele.json"), JSON.stringify({ variants: { "": { model: "pipifacilities:block/wailing_stele" } } }));
fs.writeFileSync(path.join(root, "models/item/wailing_stele.json"), JSON.stringify({
  parent: "pipifacilities:block/wailing_stele",
  display: {
    gui: { rotation: [30, 225, 0], translation: [0, -3.5, 0], scale: [0.36, 0.36, 0.36] },
    ground: { rotation: [0, 0, 0], translation: [0, 3, 0], scale: [0.25, 0.25, 0.25] },
    fixed: { rotation: [0, 0, 0], translation: [0, -3, 0], scale: [0.4, 0.4, 0.4] },
    thirdperson_righthand: { rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: [0.25, 0.25, 0.25] },
    firstperson_righthand: { rotation: [0, 45, 0], translation: [0, 0, 0], scale: [0.25, 0.25, 0.25] },
  },
}));
console.log("wailing_stele: " + elements.length + " elements");
