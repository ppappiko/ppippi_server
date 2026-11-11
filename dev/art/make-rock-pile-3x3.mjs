// Preview-only 3x3-block stone heap (model space -16..32 = 48 px). Reuses station_rock_* textures from make-rock-pile.mjs.
import fs from "node:fs"; import zlib from "node:zlib"; import path from "node:path"; import { fileURLToPath } from "node:url"; import { execFileSync } from "node:child_process";
const here = path.dirname(fileURLToPath(import.meta.url)), prev = path.join(here, "preview");
let seed = 11; const rnd = () => (seed = (seed * 1664525 + 1013904223) >>> 0) / 4294967296;
const el = [], T = { s: "#1", d: "#3" };
// c = [cx, cz], s = [w, h, d] (each <= 16 so uv stays inside the 16px texture, 1 texel per px), y = base height
function box(c, s, y, side = "s", rot = 0) {
  const [w, h, d] = s, f = [c[0] - w / 2, y, c[1] - d / 2], t = [c[0] + w / 2, y + h, c[1] + d / 2];
  const ou = rnd() * Math.max(0, 16 - Math.max(w, d)), ov = rnd() * Math.max(0, 16 - Math.max(h, d)), S = T[side];
  const fc = (tx, a, b) => ({ uv: [ou, ov, ou + a, ov + b], texture: tx });
  const e = { from: f, to: t, faces: { north: fc(S, w, h), south: fc(S, w, h), east: fc(S, d, h), west: fc(S, d, h), up: fc("#2", w, d), down: fc(S, w, d) } };
  if (rot) e.rotation = { axis: "y", angle: rot, origin: [c[0], y, c[1]] };
  el.push(e);
}
// level 0: big central blocks
box([4, 3], [15, 12, 14], 0, "s", 22.5);
box([15, 2], [14, 10, 13], 0, "d", -22.5);
box([2, 15], [13, 11, 15], 0, "d", 0);
box([14, 14], [15, 13, 15], 0, "s", 22.5);
box([8, 8], [12, 8, 12], 0, "s", -22.5);       // fills the core
// level 1
box([8, 8], [14, 11, 14], 12.5, "s", -22.5);
box([-1, 6], [10, 7, 9], 11.5, "d", 22.5);
box([14, 1], [11, 7, 10], 10, "s", 0);
box([4, 18], [10, 6, 10], 11, "s", -22.5);
// level 2 cap
box([8, 8], [8, 6, 8], 23.5, "d", 22.5);
// medium blocks (6-10 px)
for (const [c, s, y, sd, r] of [
  [[-9, -3], [9, 6, 8], 0, "s", -22.5], [[-8, 11], [8, 5, 9], 0, "d", 0], [[-4, 22], [9, 7, 8], 0, "s", 22.5],
  [[9, -9], [8, 6, 9], 0, "d", 22.5], [[24, -7], [9, 5, 8], 0, "s", 0], [[24, 7], [8, 6, 9], 0, "d", -22.5],
  [[26, 20], [9, 5, 8], 0, "s", 22.5], [[12, 25], [10, 7, 8], 0, "s", 0], [[22, 28], [7, 4, 6], 0, "d", -22.5],
  [[-12, 4], [7, 4, 7], 0, "s", 22.5], [[-2, -10], [7, 4, 6], 0, "s", 0], [[27, 31 - 12], [6, 3, 6], 0, "d", 0],
]) box(c, s, y, sd, r);
// medium blocks stacked on the inner ring for a layered look
box([-6, -2], [6, 4, 6], 6, "d", 22.5);
box([22, 5], [6, 4, 6], 6, "s", 0);
box([11, 24], [6, 4, 6], 7, "d", -22.5);
// pebbles (2-4 px)
for (const [c, s, sd, r] of [
  [[-13, -11], [3, 2, 3], "d", 22.5], [[-12, -6], [2, 2, 2], "s", 0], [[-14, 18], [3, 2, 3], "s", -22.5], [[-12, 26], [2, 2, 2], "d", 0],
  [[0, 29], [4, 3, 3], "s", 22.5], [[6, 30], [2, 2, 2], "d", 0], [[17, 30], [3, 2, 3], "s", 0], [[29, 27], [3, 2, 3], "d", -22.5],
  [[30, 12], [4, 3, 3], "s", 0], [[29, 0], [2, 2, 2], "d", 22.5], [[30, -12], [3, 2, 3], "s", 0], [[16, -14], [3, 2, 3], "d", 22.5],
  [[3, -14], [2, 2, 2], "s", 0], [[-8, -14], [3, 2, 3], "s", -22.5], [[-15, 8], [2, 2, 2], "d", 0], [[-8, 16], [4, 3, 3], "s", 22.5],
  [[19, 17], [3, 2, 3], "d", 0], [[-3, 6], [3, 2, 3], "s", 0],
]) box(c, s, 0, sd, r);
// ---- gems poking out (cyan crystals). textures: station_gem_side (4px facet bands) / station_gem_top (4px tiles)
{
  const texDir = path.resolve(here, "../pipi-facilities/assets/pipifacilities/textures/item");
  const crcT = new Uint32Array(256).map((_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
  const crc = b => { let c = 0xffffffff; for (const x of b) c = crcT[(c ^ x) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
  const chunk = (t, d) => { const tb = Buffer.from(t), l = Buffer.alloc(4), c = Buffer.alloc(4); l.writeUInt32BE(d.length); c.writeUInt32BE(crc(Buffer.concat([tb, d]))); return Buffer.concat([l, tb, d, c]); };
  const png = f => { const raw = Buffer.alloc(16 * 65); for (let y = 0; y < 16; y++) { raw[y * 65] = 0; for (let x = 0; x < 16; x++) { const c = f(x, y); raw.set([c >> 16 & 255, c >> 8 & 255, c & 255, 255], y * 65 + 1 + x * 4); } }
    const ih = Buffer.alloc(13); ih.writeUInt32BE(16, 0); ih.writeUInt32BE(16, 4); ih[8] = 8; ih[9] = 6;
    return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk("IHDR", ih), chunk("IDAT", zlib.deflateSync(raw)), chunk("IEND", Buffer.alloc(0))]); };
  const ORES = { diamond: { hi: 0xeafffb, li: 0x9cf2ea, ba: 0x4fd6cf, mi: 0x2eb0ad, dk: 0x1b7d86 },
    gold: { hi: 0xfffbd0, li: 0xfde46a, ba: 0xf3c330, mi: 0xd59a1c, dk: 0x9c6512 },
    amber: { hi: 0xffe6b0, li: 0xffb24a, ba: 0xf08a1c, mi: 0xc9630f, dk: 0x8a3e0a },
    iron: { hi: 0xf4e6d6, li: 0xe2c3a6, ba: 0xd0a382, mi: 0xaf7f5f, dk: 0x7c5440 } };
  for (const [ore, G] of Object.entries(ORES)) {
    fs.writeFileSync(path.join(texDir, `station_gem_${ore}_side.png`), png((x, y) => { const c = x % 4; if (c === 0 && y % 8 < 2) return G.hi; if (y % 8 === 5 && c === 1) return G.hi; return [G.li, G.ba, G.mi, G.dk][c]; }));
    fs.writeFileSync(path.join(texDir, `station_gem_${ore}_top.png`), png((x, y) => { const a = x % 4, b = y % 4; if (a === 0 && b === 0) return G.hi; if (a === 0 || b === 0) return G.li; if (a === 3 || b === 3) return G.mi; return G.ba; }));
  }
  for (const f of ["station_gem_side.png", "station_gem_top.png"]) fs.rmSync(path.join(texDir, f), { force: true });
}
// crystal = body box + tapered tip, both rotated together about the base. axis "x"/"z", angle in {±22.5, ±45}
function gem(c, w, h, y, axis, ang) {
  const tip = w >= 3 ? w - 2 : 1, th = Math.max(1, Math.round(w * .7));
  const part = (ww, hh, yy) => { const f = [c[0] - ww / 2, yy, c[1] - ww / 2], t = [c[0] + ww / 2, yy + hh, c[1] + ww / 2], fc = (a, b) => ({ uv: [0, 0, a, b], texture: "#4" });
    const e = { from: f, to: t, faces: { north: fc(ww, hh), south: fc(ww, hh), east: fc(ww, hh), west: fc(ww, hh), up: { uv: [0, 0, ww, ww], texture: "#5" }, down: fc(ww, ww) } };
    if (ang) e.rotation = { axis, angle: ang, origin: [c[0], y, c[1]] }; el.push(e); };
  part(w, h, y); part(tip, th, y + h);
}
// top cluster beside the cap (front-right shoulder)
gem([13, 13], 5, 5, 21, "x", 0);
gem([10, 15], 4, 4, 21, "x", 22.5);
gem([15.5, 10.5], 4, 4, 21, "z", -22.5);
gem([8, 8], 3, 3, 27, "x", 0);
// south face cluster (front-left)
gem([4, 21], 5, 5, 5, "x", 45);
gem([1, 21], 3, 4, 4, "x", 22.5);
gem([7.5, 22], 3, 3, 3, "x", 45);
// east face cluster (front-right)
gem([22, 2], 5, 5, 5, "z", -45);
gem([22, -1], 3, 4, 4, "z", -22.5);
gem([23, 6], 3, 3, 3, "z", -45);
// front corner
gem([22, 22], 4, 5, 6, "x", 22.5);
gem([24, 20], 3, 3, 4, "z", -22.5);
// little gem bits near the edges
gem([-10, 24], 3, 3, 0, "z", 22.5);
gem([28, -6], 3, 3, 0, "x", -22.5);
gem([16, 29], 3, 4, 0, "x", 0);
gem([29, 15], 2, 3, 0, "x", 0);
gem([-12, -8], 2, 2, 0, "x", 0);
// validate
for (const e of el) { for (const v of [...e.from, ...e.to]) if (v < -16 || v > 32) throw new Error("range " + v); if (e.rotation && ![22.5, -22.5, 45, -45].includes(e.rotation.angle)) throw new Error("angle"); }
const ps = path.join(here, "preview-station.mjs");
for (const ore of ["diamond", "gold", "amber", "iron"]) {
  const tex = { 1: "pipifacilities:item/station_rock_side", 2: "pipifacilities:item/station_rock_top", 3: "pipifacilities:item/station_rock_dark", 4: `pipifacilities:item/station_gem_${ore}_side`, 5: `pipifacilities:item/station_gem_${ore}_top`, particle: "pipifacilities:item/station_rock_side" };
  const m = path.join(prev, `rock_pile_3x3_${ore}.json`);
  fs.writeFileSync(m, JSON.stringify({ textures: tex, elements: el }));
  // live station model (shown by PipiLife.showStationModel as a FIXED ItemDisplay, scale = footprint/3)
  const live = { iron: "station_mine", diamond: "station_diamond_mine", amber: "station_amber_quarry", gold: "station_gold_mine" }[ore];
  fs.writeFileSync(path.resolve(here, "../pipi-facilities/assets/pipifacilities/models/item", live + ".json"), JSON.stringify({ textures: tex, elements: el,
    display: { fixed: { rotation: [0, 0, 0], translation: [0, 0, 0], scale: [1, 1, 1] }, gui: { rotation: [30, 45, 0], translation: [0, -2, 0], scale: [0.3, 0.3, 0.3] } } }));
  execFileSync("node", [ps, "--model", m, "--grid", "--out", path.join(prev, `rock_pile_3x3_${ore}.png`)], { stdio: "inherit" });
}
console.log(el.length, "elements");
