// 마을 NPC 모델 (촌장 · 대장장이 · 상인 · 무기 관리인 · 연금술사) in Minecraft style: player-proportioned cubes (head 8, body 8x12x4,
// arms / legs 4x12x4 "skin pixels") with pixel-art faces painted from small character grids, plus a few blocky props.
//   node dev/art/make-npc-models.mjs [id ...] -> pipi-facilities/assets/pipifacilities/{models,textures}/item/fx_<id>.* (128x128 texture)
//   previews: dev/art/preview/npc/<id>.png (3/4 view) and <id>_ortho.png (front)
// Space: one skin pixel = K model units; the character faces model -Z (world +Z at yaw 0), feet at y 0, centred on x = z = 8.
// Shown by PipiLife as ItemDisplays: VillageChief (fx_village_chief) and TownNpcModels (fx_npc_*, over the invisible villagers).
import fs from "node:fs";
import path from "node:path";
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { writePng } from "./class-icons/pngio.mjs";
const here = path.dirname(fileURLToPath(import.meta.url));
const A = path.resolve(here, "../pipi-facilities/assets/pipifacilities");
const PV = path.join(here, "preview/npc"); fs.mkdirSync(PV, { recursive: true });
const r3 = v => Math.round(v * 1000) / 1000;
const ONLY = process.argv.slice(2);
const K = .875, TEX = 128;
const MX = x => 8 + (x - 4) * K, MY = y => y * K, MZ = z => 8 + (z - 2) * K;
const rgb = h => [1, 3, 5].map(i => parseInt(h.slice(i, i + 2), 16));
const hash = (a, b, c) => { let h = (a * 374761393 + b * 668265263 + c * 2147483647) >>> 0; h = ((h ^ (h >>> 13)) * 1274126177) >>> 0; return (h & 0xffff) / 0xffff; };

/**
 * Builds one NPC. part(from, to, faces, opts) takes skin-pixel corners; faces = { n, s, e, w, u, d, all } where each value is a
 * legend char (solid), an array of strings (a pixel grid, '.' = transparent, scaled to the face) or a function (i, j, w, h) => char.
 * n = front (-Z). opts: { rot: { axis, angle, origin: [x, y, z] in skin px }, glow: true }.
 */
function npc(id, legend, build) {
  if (ONLY.length && !ONLY.includes(id)) return;
  const px = Buffer.alloc(TEX * TEX * 4), els = [];
  let sx = 0, sy = 0, rowH = 0, faceNo = 0;
  const alloc = (w, h) => { if (sx + w > TEX) { sx = 0; sy += rowH; rowH = 0; } if (sy + h > TEX) throw new Error(id + ": texture full"); const at = [sx, sy]; sx += w; rowH = Math.max(rowH, h); return at; };
  const paint = (spec, w, h) => {
    const [u, v] = alloc(w, h), seed = ++faceNo;
    for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) {
      let ch = typeof spec === "string" ? spec : typeof spec === "function" ? spec(i, j, w, h) : spec[Math.min(spec.length - 1, Math.floor(j * spec.length / h))][Math.min(spec[0].length - 1, Math.floor(i * spec[0].length / w))];
      if (!ch || ch === ".") continue;
      const hex = legend[ch]; if (!hex) throw new Error(id + ": no colour '" + ch + "'");
      const jit = Math.round((hash(i + u, j + v, seed) - .5) * 14), c = rgb(hex), o = ((v + j) * TEX + u + i) * 4;
      px[o] = Math.max(0, Math.min(255, c[0] + jit)); px[o + 1] = Math.max(0, Math.min(255, c[1] + jit)); px[o + 2] = Math.max(0, Math.min(255, c[2] + jit)); px[o + 3] = 255;
    }
    return [u / 8, v / 8, (u + w) / 8, (v + h) / 8].map(r3);
  };
  const part = (from, to, faces, opts = {}) => {
    const f = from.map((v, i) => Math.min(v, to[i])), t = from.map((v, i) => Math.max(v, to[i]));
    const d = [t[0] - f[0], t[1] - f[1], t[2] - f[2]].map(v => Math.max(1, Math.round(v)));
    const dims = { n: [d[0], d[1]], s: [d[0], d[1]], e: [d[2], d[1]], w: [d[2], d[1]], u: [d[0], d[2]], d: [d[0], d[2]] };
    const name = { n: "north", s: "south", e: "east", w: "west", u: "up", d: "down" };
    const el = { from: [MX(f[0]), MY(f[1]), MZ(f[2])].map(r3), to: [MX(t[0]), MY(t[1]), MZ(t[2])].map(r3), faces: {} };
    for (const v of [...el.from, ...el.to]) if (v < -16 || v > 32) throw new Error(id + ": out of range " + el.from + " " + el.to);
    for (const k of Object.keys(dims)) { const spec = faces[k] ?? faces.all; if (spec == null) continue; el.faces[name[k]] = { uv: paint(spec, ...dims[k]), texture: "#0" }; }
    if (opts.glow) el.shade = false;
    if (opts.rot) { const o = opts.rot.origin; el.rotation = { angle: opts.rot.angle, axis: opts.rot.axis, origin: [MX(o[0]), MY(o[1]), MZ(o[2])].map(r3) }; }
    els.push(el);
  };
  build(part);
  const file = path.join(A, `models/item/fx_${id}.json`);
  fs.writeFileSync(path.join(A, `textures/item/fx_${id}.png`), writePng(px, TEX, TEX));
  fs.writeFileSync(file, JSON.stringify({ textures: { "0": `pipifacilities:item/fx_${id}`, particle: `pipifacilities:item/fx_${id}` }, elements: els,
    display: { fixed: { rotation: [0, 0, 0], translation: [0, 0, 0], scale: [1, 1, 1] }, gui: { rotation: [0, 180, 0], translation: [0, -6, 0], scale: [.45, .45, .45] } } }));
  console.log(`fx_${id}`, els.length, "elements");
  const run = (out, env = {}) => execFileSync("node", [path.join(here, "preview-station.mjs"), "--model", file, "--out", path.join(PV, out), "--size", "420"], { env: { ...process.env, ...env } });
  run(id + ".png", { FRONT: "1" }); run(id + "_ortho.png", { ORTHO: "1" });
}

// ---------------------------------------------------------------- grid helpers
/** vertical stripes / bands: rows top->bottom of [char, count] (counts in pixels; the last fills the rest) */
const bands = (...rows) => (i, j, w, h) => { let y = 0; for (const [c, n] of rows) { y += n; if (j < y) return c; } return rows[rows.length - 1][0]; };
/** side of a sleeve: cloth on top, cuff, hand at the bottom */
const sleeve = (cloth, cuff, hand, handRows = 3) => (i, j, w, h) => j >= h - handRows ? hand : j === h - handRows - 1 ? cuff : cloth;
const solid = c => c;
/** 8 x 8 head sides: hair rows on top, skin below with an ear */
const headSide = (hair, skin, ear, hairRows = 2, back = false) => (i, j) => j < hairRows || back ? hair : (i === 3 || i === 4) && (j === 4 || j === 5) ? ear : skin;

// ================================================================ 촌장: green robe with gold trim, brown mantle, silver circlet, long white beard, staff with a glowing orb
npc("village_chief", { s: "#e8b896", S: "#c98f6e", W: "#f4f2ec", w: "#cfcbc0", g: "#3f6a4a", G: "#2c4d36", y: "#c9a43a", b: "#6a4a30", B: "#4e3522",
  k: "#3a2a22", e: "#5ae6a0", i: "#d8dde6", o: "#6a4a2c", O: "#8a6a44", L: "#d8ffb0", l: "#ffffe8", f: "#5aa84a" }, part => {
  // robe skirt over the legs
  part([-.3, 0, -.3], [8.3, 12, 4.3], {
    n: ["gggyyggg", "gggyyggg", "GggyyggG", "gggyyggg", "gggyyggg", "GggyyggG", "gggyyggg", "gggyyggg", "GggyyggG", "gggyyggg", "GGGGGGGG", "yyyyyyyy"],
    s: bands(["g", 10], ["G", 1], ["y", 1]), e: bands(["g", 10], ["G", 1], ["y", 1]), w: bands(["g", 10], ["G", 1], ["y", 1]), u: "g", d: "G" });
  // body
  part([0, 12, 0], [8, 24, 4], {
    n: ["gggyyggg", "gggyyggg", "gggyyggg", "gggyyggg", "BBByyBBB", "BBBkkBBB", "ggeyyggg", "gggyyggg", "gggyyggg", "gggyyggg", "gggyyggg", "gggyyggg"],
    s: "b", e: bands(["g", 7], ["B", 2], ["g", 3]), w: bands(["g", 7], ["B", 2], ["g", 3]), u: "g", d: "g" });
  // mantle down the back + rolled hood on the shoulders
  part([-.5, 5, 4], [8.5, 24, 5], { all: "b", s: (i, j, w, h) => j > h - 2 ? "B" : i === 0 || i === w - 1 ? "B" : "b" });
  part([-.6, 22.6, -.5], [8.6, 24.6, 5.2], { all: (i, j) => j === 1 ? "B" : "b", u: "b", d: "B" });
  // arms: left hangs, right forward on the staff
  part([-4, 12, 0], [0, 24, 4], { all: sleeve("g", "y", "s"), u: "g", d: "s" });
  part([8, 12, 0], [12, 24, 4], { all: sleeve("g", "y", "s"), u: "g", d: "s" }, { rot: { axis: "x", angle: 45, origin: [10, 23, 2] } });
  // staff + glowing orb + leaves
  part([9.4, 0, -7.4], [10.6, 32, -6.2], { all: (i, j) => j % 7 === 3 ? "O" : "o" });
  part([8.4, 31.6, -8.4], [11.6, 34.8, -5.2], { all: ["lLL", "LLL", "LLL"] }, { glow: true });
  part([8, 31, -6.4], [9, 32, -5.4], { all: "f" }); part([11, 30.4, -8.2], [12, 31.4, -7.2], { all: "f" }); part([7.8, 33.6, -7.6], [8.6, 34.4, -6.8], { all: "f" });
  // head: white hair, bushy brows, smiling closed eyes, moustache; beard hanging over the chest
  part([0, 24, -2], [8, 32, 6], {
    n: ["WWWWWWWW", "WssssssW", "WssssssW", "sWWssWWs", "skkssKKs".replace(/K/g, "k"), "sssSSsss", "sWWWWWWs", "WWWWWWWW"],
    e: headSide("W", "s", "S", 3), w: headSide("W", "s", "S", 3), s: "W", u: "W", d: "W" });
  part([1, 14, -2.8], [7, 24.4, -1.6], { n: ["WWWWWW", "WWWWWW", "wWWWWw", "wWWWWw", ".WWWW.", ".wWWw.", "..WW..", "..Ww..", "..ww..", "..w..."], all: "W", s: "W" });
  // silver circlet with a green gem
  part([-.3, 29, -2.3], [8.3, 30, 6.3], { n: "iiieeiii".split("").length ? ["iiieeiii"] : "i", all: "i" });
  // ---- depth: hair layer, nose, brows, moustache, layered beard, shoes, hem, belt, collar, cuffs, clasp, staff prongs
  part([-.4, 25.2, -.6], [8.4, 32.4, 6.4], { e: "W", w: "W", s: "W", u: "W" });
  part([3.5, 25.8, -2.7], [4.5, 27, -2], { all: "S" });
  for (const x of [.8, 4.9]) part([x, 28, -2.5], [x + 2.3, 28.8, -2], { all: "W" });
  part([1, 25, -2.7], [7, 25.9, -2], { all: "W" });
  part([2, 16.5, -3.6], [6, 23, -2.7], { n: ["WWWW", "wWWw", "WWWW", ".WW.", ".Ww."], all: "W" });
  for (const x of [0, 4.4]) part([x, 0, -1], [x + 3.6, 1.2, 0], { all: "B" });
  part([-.8, 0, -.8], [8.8, 1.4, 4.8], { all: "y", u: "y", d: "G" });
  part([-.4, 16, -.5], [8.4, 17.2, 4.4], { all: "B", n: "BBBByyBBB".slice(0, 8).split("").length ? ["BBByyBBB"] : "B" });
  part([3.2, 15.8, -.9], [4.8, 17.4, -.4], { all: "y" });
  part([3.3, 22.9, -1], [4.7, 24.1, -.5], { all: "e" });
  part([-4.4, 14.6, -.4], [.4, 15.8, 4.4], { all: "y" });
  part([7.6, 14.6, -.4], [12.4, 15.8, 4.4], { all: "y" }, { rot: { axis: "x", angle: 45, origin: [10, 23, 2] } });
  for (const [x, z] of [[8.2, -8.6], [11, -8.6], [8.2, -5.8], [11, -5.8]]) part([x, 30.6, z], [x + .8, 33.4, z + .8], { all: "o" });
  part([9.2, 34.8, -7.4], [10.8, 35.6, -6.2], { all: "l" }, { glow: true });
});

// ================================================================ 대장장이: bald, brass goggles, braided ginger beard, leather apron, hammer raised
npc("npc_smith", { s: "#d49a74", S: "#b07a58", r: "#b8572a", R: "#8a3e1e", d: "#4a4650", D: "#34313a", p: "#3b3a40", a: "#8a5a34", A: "#6a4426", b: "#4a3020",
  i: "#5c6068", I: "#9aa0a8", y: "#c9a43a", c: "#7fd0ff", h: "#ff8a2a", H: "#ffd27a", k: "#2a2628" }, part => {
  for (const x of [0, 4]) part([x, 0, 0], [x + 4, 12, 4], { all: bands(["p", 9], ["b", 3]), u: "p", d: "b" });
  part([.4, 3, -.5], [7.6, 12, 0], { n: (i, j, w, h) => j === h - 1 ? "A" : i === 0 || i === w - 1 ? "A" : "a", all: "A" });     // apron over the legs
  part([0, 12, 0], [8, 24, 4], {
    n: ["dAddddAd", "dAddddAd", "daaaaaad", "daaaaaad", "daIIiaad", "daIiiaad", "daaaaaad", "AAAyyAAA", "daaaaaad", "daaaaaad", "daaaaaad", "daaaaaad"],
    s: ["dAddddAd", "ddAddAdd", "dddAAddd", "dddAAddd", "ddddddddd", "dddddddd", "dddddddd", "AAAAAAAA", "dddddddd", "dddddddd", "dddddddd", "dddddddd"],
    e: bands(["d", 7], ["A", 1], ["d", 4]), w: bands(["d", 7], ["A", 1], ["d", 4]), u: "d", d: "d" });
  const arm = (i, j, w, h) => j < 3 ? "d" : j >= 6 && j <= 8 ? "A" : j === 9 ? "y" : j >= h - 2 ? "S" : "s";
  part([-4, 12, 0], [0, 24, 4], { all: arm, u: "d", d: "S" });
  part([8, 12, 0], [12, 24, 4], { all: arm, u: "d", d: "S" }, { rot: { axis: "x", angle: 45, origin: [10, 23, 2] } });
  // hammer held up in front: handle + iron head with glowing faces
  part([9.5, 12, -7.2], [10.5, 26, -6.2], { all: (i, j) => j > 10 ? "A" : j % 3 === 0 ? "y" : "b" });
  part([7.2, 25, -8.6], [12.8, 29, -4.8], { n: ["IIIIIIII", "iiyyyyii", "iiiiiiii", "iiiiiiii"], s: ["IIIIIIII", "iiyyyyii", "iiiiiiii", "iiiiiiii"], u: "I", d: "i", e: ["hhhh", "hHHh", "hHHh", "hhhh"], w: ["hhhh", "hHHh", "hHHh", "hhhh"] });
  // head: bald, goggles band, ginger brows, beard; braided beard hangs below
  part([0, 24, -2], [8, 32, 6], {
    n: ["ssssssss", "ssssssss", "yccyyccy", "srrssrrs", "sksSSsks", "rrrSSrrr", "rRrrrrRr", "rrrrrrrr"],
    e: (i, j) => j === 2 ? "y" : j >= 5 ? "r" : (i === 3 || i === 4) && j === 4 ? "S" : "s", w: (i, j) => j === 2 ? "y" : j >= 5 ? "r" : (i === 3 || i === 4) && j === 4 ? "S" : "s",
    s: (i, j) => j === 2 ? "y" : "s", u: "s", d: "r" });
  part([1, 18.5, -2.8], [7, 24.2, -1.6], { n: ["rrrrrr", "rRrrRr", "rr..rr", "Ir..rI", "rr..rr", "R....R"], all: "r" });
  // ---- depth: nose, brows, 3D goggles + strap, big beard and braids with rings, pocket with tongs, belt, bracers, pauldron, boot tops
  part([3.5, 25.8, -2.7], [4.5, 27.2, -2], { all: "S" });
  for (const x of [.7, 4.9]) part([x, 28, -2.5], [x + 2.4, 28.8, -2], { all: "r" });
  part([-.3, 29.8, -2.3], [8.3, 30.8, 6.3], { all: "D", n: "y" });
  for (const x of [.6, 4.6]) part([x, 29.4, -2.9], [x + 2.8, 31.2, -2.2], { n: ["yyy", "ycy", "yyy"], all: "y" });
  part([.4, 22.4, -2.9], [7.6, 26, -2], { n: ["rrrrrrr", "rRrrrRr", "rrrrrrr"], all: "r" });
  for (const x of [1.2, 5.2]) { part([x, 16.4, -3], [x + 1.6, 22.6, -2], { all: (i, j) => j % 3 === 2 ? "R" : "r" }); part([x - .2, 18.6, -3.2], [x + 1.8, 19.4, -1.8], { all: "I" }); }
  part([2.2, 13.2, -.9], [5.6, 16.4, -.4], { all: "A" });
  part([4.2, 15.4, -1], [4.6, 18.6, -.6], { all: "i" }); part([4.8, 15.4, -1], [5.2, 18.2, -.6], { all: "i" });
  part([-.4, 18.6, -.5], [8.4, 19.6, 4.4], { all: "A" }); part([3.2, 18.4, -.8], [4.8, 19.8, -.4], { all: "y" });
  part([-4.4, 15, -.4], [.4, 18, 4.4], { all: bands(["A", 1], ["y", 1], ["A", 1]) });
  part([7.6, 15, -.4], [12.4, 18, 4.4], { all: bands(["A", 1], ["y", 1], ["A", 1]) }, { rot: { axis: "x", angle: 45, origin: [10, 23, 2] } });
  part([-4.8, 21.4, -.8], [.8, 24.8, 4.8], { all: bands(["a", 2], ["y", 1], ["a", 1]), u: "a" });
  for (const x of [-.4, 3.6]) part([x, 2.6, -.4], [x + 4.8, 3.8, 4.4], { all: "b" });
  part([7, 24.6, -9], [13, 25.2, -4.4], { all: "y" });
});

// ================================================================ 상인: feathered wide-brim hat, monocle, curled moustache, open teal coat, big pack with a bedroll and a lantern, coin purse
npc("npc_merchant", { s: "#e6b08a", S: "#c48a68", t: "#2f7a7a", T: "#1f5a5a", r: "#b83a32", R: "#8a2a24", y: "#d9a83a", p: "#6a4a30", b: "#3a2818",
  h: "#5a3a6a", H: "#3e2850", f: "#e8c8ff", F: "#b89ad8", l: "#8a6238", L: "#6a4426", o: "#ffd86a", k: "#3a2a22" }, part => {
  for (const x of [0, 4]) part([x, 0, 0], [x + 4, 12, 4], { all: bands(["p", 9], ["b", 3]), u: "p", d: "b" });
  part([-.3, 4, -.3], [8.3, 12, 4.3], { n: ["tT....Tt", "tT....Tt", "tT....Tt", "tT....Tt", "tT....Tt", "tT....Tt", "tT....Tt", "TT....TT"], all: (i, j, w, h) => j === h - 1 ? "T" : "t", u: ".", d: "T" });
  part([0, 12, 0], [8, 24, 4], {
    n: ["rrrrrrrr", "RrrrrrrR", "tTryrTt".padEnd(8, "t"), "tTtyttTt", "tTttttTt", "tTtyttTt", "LLLyLLLL", "tTtyttTt", "tTttttTt", "tTtyttTt", "tTttttTt", "tTttttTt"],
    s: bands(["r", 2], ["t", 4], ["L", 1], ["t", 5]), e: bands(["r", 2], ["t", 4], ["L", 1], ["t", 5]), w: bands(["r", 2], ["t", 4], ["L", 1], ["t", 5]), u: "r", d: "t" });
  part([-4, 12, 0], [0, 24, 4], { all: sleeve("t", "y", "s"), u: "t", d: "s" });
  part([8, 12, 0], [12, 24, 4], { all: sleeve("t", "y", "s"), u: "t", d: "s" }, { rot: { axis: "x", angle: 22.5, origin: [10, 23, 2] } });
  part([8.4, 11.4, -3.6], [11.6, 14.6, -.4], { all: ["lyl", "lll", "LlL"], u: "y" });                                          // coin purse
  // pack on the back with straps, a bedroll on top and a lantern hanging at the side
  part([.5, 12.5, 4], [7.5, 23, 7.6], { s: ["LLLLLLL", "lllllll", "lLlllLl", "lllllll", "LLLLLLL", "lllllll", "llLLLll", "llLyLll", "lllllll", "LLLLLLL"], all: "l", u: "L" });
  part([-1, 23, 4.4], [9, 25.6, 7.4], { all: (i, j, w, h) => i % 3 === 2 ? "R" : "r", e: "R", w: "R" });
  part([8, 14.6, 4.8], [10, 17.4, 6.8], { all: ["yyy", "ooo", "ooo", "yyy"] }, { glow: true });
  // head: smiling, monocle, curled moustache
  part([0, 24, -2], [8, 32, 6], {
    n: ["kkkkkkkk", "kssssssk", "sssssyys", "skkssyky", "ssssSyys", "kkkSSkkk", "ksssssssk".slice(0, 8), "ssskksss"],
    e: headSide("k", "s", "S", 2), w: headSide("k", "s", "S", 2), s: "k", u: "k", d: "s" });
  // hat: wide brim, crown with a gold band, a big feather
  part([-2, 31.2, -4], [10, 32.2, 8], { all: (i, j, w, h) => i === 0 || j === 0 || i === w - 1 || j === h - 1 ? "H" : "h" });
  part([.5, 32.2, -1.5], [7.5, 35.4, 5.5], { all: bands(["h", 2], ["y", 1]), u: "h" });
  part([6.6, 32.4, 1], [7.6, 36, 4.4], { all: (i, j) => j === 0 ? "F" : "f", e: [".ff", "fff", "fFf", "fF."], w: ["ff.", "fff", "fFf", ".Ff"] });
  // ---- depth: nose, brows, 3D moustache curls, monocle ring + chain, scarf, lapels, buttons, belt, pack straps + pockets + pot, boot tops, hat band, feather
  part([3.5, 25.8, -2.7], [4.5, 27.2, -2], { all: "S" });
  for (const x of [.9, 4.9]) part([x, 28.2, -2.5], [x + 2.2, 28.9, -2], { all: "k" });
  part([1.2, 25, -2.7], [6.8, 25.8, -2], { all: "k" });
  part([.6, 25.6, -2.7], [1.4, 26.6, -2.1], { all: "k" }); part([6.6, 25.6, -2.7], [7.4, 26.6, -2.1], { all: "k" });
  part([4.6, 26.6, -2.7], [6.8, 28.8, -2.3], { n: ["yyy", "y.y", "yyy"], all: "y" });
  part([6.5, 21, -2.5], [6.8, 26.6, -2.2], { all: "y" });
  part([-.5, 22.3, -.7], [8.5, 24.5, 4.6], { all: "r", u: "r" });
  part([1.2, 17.6, -1], [2.8, 22.4, -.6], { all: (i, j) => j % 2 ? "R" : "r" });
  part([-.2, 18.4, -.6], [1.8, 22.3, -.2], { all: "T" }); part([6.2, 18.4, -.6], [8.2, 22.3, -.2], { all: "T" });
  for (const y of [13.4, 16, 20.4]) part([3.4, y, -.6], [4.6, y + 1, -.2], { all: "y" });
  part([-.4, 17.6, -.5], [8.4, 18.6, 4.4], { all: "L" }); part([3.3, 17.4, -.8], [4.7, 18.8, -.4], { all: "y" });
  for (const x of [1, 5.8]) part([x, 14, -.4], [x + 1.2, 24.4, 4.4], { n: "L", u: "L", e: "L", w: "L" });
  part([1.4, 13.6, 7.6], [6.6, 17, 8.4], { all: "L", s: ["LyyL".slice(0, 4), "LLLL"] });
  part([-1.8, 13.6, 4.8], [.4, 15.8, 7], { all: "k", u: "b" });
  for (const x of [-.4, 3.6]) part([x, 2.6, -.4], [x + 4.8, 3.8, 4.4], { all: "b" });
  part([.2, 32.2, -1.8], [7.8, 33.2, 5.8], { all: "y" });
  part([7.4, 34.6, 2.2], [8.4, 36, 5.2], { all: "f" }); part([5.8, 33.4, 3.6], [6.8, 35.4, 5.6], { all: "F" });
  part([8.2, 18, 4.6], [9.8, 18.6, 6.8], { all: "y" });
});

// ================================================================ 무기 관리인: steel plate, blue tabard with a gold sword, pauldrons, helmet with a red crest, halberd
npc("npc_armory", { m: "#c8ccd4", M: "#8a9098", n: "#5c6068", y: "#d9a83a", u: "#2c4a8a", U: "#1f3466", r: "#b83a32", s: "#e2aa86", S: "#c08664",
  k: "#2a2026", w: "#5a3a24", c: "#6a4426" }, part => {
  for (const x of [0, 4]) part([x, 0, 0], [x + 4, 12, 4], { all: bands(["m", 2], ["M", 1], ["m", 4], ["M", 1], ["m", 2], ["n", 2]), u: "m", d: "n" });
  part([0, 12, 0], [8, 24, 4], {
    n: ["mmmmmmmm", "mMuuuuMm", "mMuyyuMm".replace("yy", "uy").replace("uyu", "uyu"), "mMuyyuMm", "mMyyyyMm", "mMuyyuMm", "nnnyynnn", "mMuyyuMm", "mMuuuuMm", "mMuuuuMm", "mMuuuuMm", "mMUUUUMm"],
    s: ["mmmmmmmm", "mMuuuuMm", "mMuuuuMm", "mMuuuuMm", "mMuuuuMm", "mMuuuuMm", "nnnnnnnn", "mMuuuuMm", "mMuuuuMm", "mMuuuuMm", "mMuuuuMm", "mMUUUUMm"],
    e: bands(["m", 6], ["n", 1], ["m", 5]), w: bands(["m", 6], ["n", 1], ["m", 5]), u: "m", d: "m" });
  const arm = (i, j, w, h) => j >= h - 3 ? "M" : j === h - 4 ? "y" : j % 4 === 3 ? "M" : "m";
  part([-4, 12, 0], [0, 24, 4], { all: arm, u: "m", d: "M" });
  part([8, 12, 0], [12, 24, 4], { all: arm, u: "m", d: "M" }, { rot: { axis: "x", angle: 22.5, origin: [10, 23, 2] } });
  for (const x of [-4.6, 7.6]) part([x, 21, -.6], [x + 5, 25, 4.6], { all: bands(["m", 2], ["M", 1], ["y", 1]), u: (i, j, w, h) => i === 0 || i === w - 1 || j === 0 || j === h - 1 ? "y" : "m" });
  // halberd: shaft, axe blade, back hook, top spike, red tassel
  part([9.5, 0, -5.4], [10.5, 33, -4.4], { all: (i, j) => j % 8 === 0 ? "n" : "w" });
  part([10.5, 26.5, -5.6], [14, 32, -4.2], { all: ["mmmmM", "mmmmM", "mmmmM", "mmmmM", "Mmmmm", ".MMMM"] });
  part([7.4, 29, -5.3], [9.5, 30, -4.5], { all: "M" });
  part([9.6, 33, -5.3], [10.4, 36, -4.5], { all: "m" });
  part([9.1, 23.4, -5.8], [10.9, 26.4, -4], { all: (i, j) => j % 2 ? "r" : "r" });
  // head: stern face with a moustache, helmet (open face) with cheek guards and a red crest
  part([0, 24, -2], [8, 32, 6], {
    n: ["ssssssss", "ssssssss", "skkssKKs".replace(/K/g, "k"), "sksSSsks", "ssssSsss", "scccccss".slice(0, 8), "sssssssS", "ssssssss"],
    e: "s", w: "s", s: "s", u: "s", d: "s" });
  part([-.4, 29, -2.4], [8.4, 32.6, 6.4], { n: ["mmmmmmmm", "mmmmmmmm", "yyyMMyyy", "...MM..."], all: (i, j, w, h) => j === h - 1 ? "y" : "m", u: "m", d: "M" });
  for (const x of [-.4, 7]) part([x, 25, -2.4], [x + 1.4, 29, 2.6], { all: "M" });
  part([3.4, 32.6, -1.6], [4.6, 35.6, 5.6], { all: (i, j) => j === 0 ? "r" : "r", n: "r", e: ["rrrrrrr", "rrrrrrr", ".rrrrr."], w: ["rrrrrrr", "rrrrrrr", ".rrrrr."] });
  // ---- depth: nose, brows, moustache, visor brim, nose guard, rivets, tabard panel, fauld, knee cops, gauntlet cuffs, sword at the hip, layered pauldrons
  part([3.5, 25.8, -2.7], [4.5, 27.2, -2], { all: "S" });
  for (const x of [.8, 4.9]) part([x, 28, -2.5], [x + 2.3, 28.8, -2], { all: "k" });
  part([1, 25, -2.7], [7, 25.8, -2], { all: "c" });
  part([-.6, 28.6, -3.2], [8.6, 29.4, 0], { all: "m", n: "y" });
  part([3.5, 25.4, -3.2], [4.5, 29, -2.5], { all: "M" });
  for (const x of [.4, 7]) part([x, 30.6, -2.6], [x + .6, 31.2, -2.3], { all: "y" });
  part([1.8, 7, -.7], [6.2, 23.4, -.2], { n: ["uuuuuu", "uuyuuu".slice(0, 6), "uyyyyu", "uuyuuu", "uuyuuu", "uuyuuu", "uuuuuu", "uuuuuu", "UUUUUU", "yyyyyy"], all: "u" });
  part([-.4, 10, -.5], [8.4, 12.6, 4.5], { all: bands(["m", 1], ["M", 1], ["y", 1]) });
  part([-.4, 17.4, -.5], [8.4, 18.4, 4.5], { all: "n", n: "n" }); part([3.3, 17.2, -.8], [4.7, 18.6, -.4], { all: "y" });
  for (const x of [0, 4]) part([x, 4.6, -.7], [x + 4, 6.8, -.1], { all: "m", n: ["mMm", "MyM"] });
  part([-4.5, 13.6, -.5], [.5, 15.4, 4.5], { all: "y" });
  part([7.5, 13.6, -.5], [12.5, 15.4, 4.5], { all: "y" }, { rot: { axis: "x", angle: 22.5, origin: [10, 23, 2] } });
  part([-1.4, 3.6, .9], [-.4, 13.6, 2.1], { all: "c" }); part([-1.5, 4.4, .8], [-.3, 5.2, 2.2], { all: "y" });
  part([-2, 13.6, .5], [.4, 14.4, 2.5], { all: "y" }); part([-1.3, 14.4, 1.1], [-.5, 16.6, 1.9], { all: "w" }); part([-1.5, 16.6, .9], [-.3, 17.4, 2.1], { all: "y" });
  for (const x of [-4, 8]) part([x, 24.6, 0], [x + 4, 25.6, 4], { all: "M", u: "m" });
});

// ================================================================ 연금술사: green hood with goggles, round glasses, grey goatee, purple robe with vial straps, glowing flask and a book
npc("npc_alchemist", { s: "#e6b896", S: "#c49474", p: "#4a3a6a", P: "#352850", y: "#c9a43a", h: "#2e5a4a", H: "#1f4033", g: "#9a9aa2", l: "#7a5434",
  r: "#ff6a5a", b: "#6ab0ff", v: "#7fffb0", w: "#efe6cc", k: "#352850", c: "#7fffb0" }, part => {
  part([-.3, 0, -.3], [8.3, 12, 4.3], { n: ["ppPyyPpp", "pppyyppp", "pppyyppp", "pppyyppp", "pppyyppp", "pppyyppp", "pppyyppp", "pppyyppp", "pppyyppp", "pppyyppp", "PPPPPPPP", "yyyyyyyy"],
    all: bands(["p", 10], ["P", 1], ["y", 1]), u: "p", d: "P" });
  part([0, 12, 0], [8, 24, 4], {
    n: ["pplppvpp", "ppplrppp", "pppllppp", "ppllpbpp", "plpprlpp", "lppppplp", "llllllll", "pplyylpp", "ppPyyPpp", "pppyyppp", "pppyyppp", "pppyyppp"],
    s: bands(["p", 6], ["l", 1], ["p", 5]), e: bands(["p", 6], ["l", 1], ["p", 5]), w: bands(["p", 6], ["l", 1], ["p", 5]), u: "p", d: "p" });
  part([-4, 12, 0], [0, 24, 4], { all: sleeve("p", "y", "s") }, { rot: { axis: "x", angle: 22.5, origin: [-2, 23, 2] } });
  part([8, 12, 0], [12, 24, 4], { all: sleeve("p", "y", "s") }, { rot: { axis: "x", angle: 45, origin: [10, 23, 2] } });
  part([9, 14, -7.2], [11, 17, -5.2], { all: ["vvv", "vcv", "vvv"] }, { glow: true });                       // flask
  part([9.5, 17, -6.7], [10.5, 18.4, -5.7], { all: "w", u: "l" });
  part([-3.4, 12.6, -5.2], [-.6, 16.4, -4.2], { n: ["lwwl", "lwwl", "lwwl", "lllll".slice(0, 4)], all: "l" });   // book
  // head: round glasses, smiling, grey goatee
  part([0, 24, -2], [8, 32, 6], {
    n: ["gggggggg", "gssssssg", "syyyyyys", "sykysyky".slice(0, 8), "syyySyyy".slice(0, 8), "ssssSsss", "sssgggss", "ssssgsss"],
    e: headSide("g", "s", "S", 2), w: headSide("g", "s", "S", 2), s: "g", u: "g", d: "s" });
  // hood: shell around the head with a face opening (transparent pixels), goggles on the brow, a point at the back
  part([-.5, 23.5, -2.5], [8.5, 32.6, 6.5], {
    n: ["hhhhhhhhh", "hyccyccyh", "hhhhhhhhh", "h.......h", "h.......h", "h.......h", "h.......h", "h.......h", "hh.....hh"],
    all: (i, j, w, h) => j === h - 1 ? "H" : "h", u: "h", d: (i, j, w, h) => i === 0 || i === w - 1 || j === h - 1 ? "h" : null });
  part([3, 26, 6.5], [5, 30.5, 8], { all: "H" });
  // ---- depth: nose, 3D round glasses, goatee, hood fold on the shoulders, goggles on the hood, straps with glowing vials, belt with pouches, hem, bubbles, book cover
  part([3.5, 25.8, -2.7], [4.5, 27.2, -2], { all: "S" });
  for (const x of [.6, 4.6]) part([x, 26.6, -2.7], [x + 2.8, 29, -2.3], { n: ["yyyy", "y..y", "y..y", "yyyy"], all: "y" });
  part([3.4, 27.8, -2.7], [4.6, 28.2, -2.4], { all: "y" });
  part([3, 22.2, -2.7], [5, 25, -2], { n: ["ggg", "ggg", ".g."], all: "g" });
  part([-.9, 22.4, -1.2], [8.9, 24.2, 5.6], { all: "h", u: "h", d: "H" });
  for (const x of [.8, 4.6]) part([x, 30.4, -3.2], [x + 2.6, 32.2, -2.5], { n: ["yyy", "ycy", "yyy"], all: "y" }, { glow: false });
  part([-.6, 30.8, -2.6], [8.6, 31.8, 6.6], { all: "l" });
  part([1.4, 13, -.7], [2.2, 23.4, -.3], { all: "l" }, { rot: { axis: "z", angle: -22.5, origin: [4, 18, -.5] } });
  part([5.8, 13, -.7], [6.6, 23.4, -.3], { all: "l" }, { rot: { axis: "z", angle: 22.5, origin: [4, 18, -.5] } });
  for (const [x, y, c] of [[2.2, 20.6, "r"], [5.2, 20.6, "b"], [1.6, 16.2, "v"], [5.8, 16.2, "r"]]) part([x, y, -1.1], [x + 1, y + 1.6, -.4], { all: c }, { glow: true });
  part([-.4, 17.6, -.5], [8.4, 18.6, 4.4], { all: "l" }); part([3.3, 17.4, -.8], [4.7, 18.8, -.4], { all: "y" });
  part([-1.4, 13.6, .2], [.4, 16.6, 2.8], { all: "l", u: "l" }); part([7.6, 13.6, .2], [9.4, 16.6, 2.8], { all: "l", u: "l" });
  part([-.8, 0, -.8], [8.8, 1.4, 4.8], { all: "y", d: "P" });
  part([3.4, 0, -.7], [4.6, 12, -.4], { all: "y" });
  for (const [x, y, z, sz] of [[9.8, 18.8, -6.6, .7], [10.4, 19.8, -6.2, .5], [9.6, 20.6, -6.4, .4]]) part([x, y, z], [x + sz, y + sz, z + sz], { all: "c" }, { glow: true });
  part([-3.6, 12.4, -5.4], [-.4, 16.6, -5], { all: "P" }, { rot: { axis: "x", angle: 22.5, origin: [-2, 23, 2] } });
});
