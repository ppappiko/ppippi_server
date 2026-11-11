// Preview-only: one standalone wheat plant model (NOT registered in the mod).  node dev/art/make-wheat-single.mjs
// then: node dev/art/preview-station.mjs --model dev/art/preview/wheat_single.json --out dev/art/preview/wheat_single.png
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
const here = path.dirname(fileURLToPath(import.meta.url));
// palette cell order is read from make-station-models.mjs so the colours stay identical
const src = fs.readFileSync(path.join(here, "make-station-models.mjs"), "utf8");
const palBlock = src.slice(src.indexOf("const PAL = {"), src.indexOf("};", src.indexOf("const PAL = {")));
const names = [...palBlock.matchAll(/(\w+):\s*"#[0-9a-fA-F]{6}"/g)].map(m => m[1]);
const uvOf = n => { const i = names.indexOf(n); if (i < 0) throw new Error(n); const x = (i % 8) * 2, y = Math.floor(i / 8) * 2; return [x + .3, y + .3, x + 1.7, y + 1.7]; };
const el = [];
const box = (f, t, col, rot) => {
  const c = typeof col === "string" ? { all: col } : col, faces = {};
  for (const [k, v] of Object.entries({ north: c.n, south: c.s, east: c.e, west: c.w, up: c.top, down: c.bottom })) faces[k] = { uv: uvOf(v ?? c.all), texture: "#0" };
  const e = { from: f.map(v => +v.toFixed(3)), to: t.map(v => +v.toFixed(3)), faces };
  if (rot) e.rotation = { axis: rot[0], angle: rot[1], origin: rot[2] };
  el.push(e);
};
const X = 8, Z = 8, w = .25;
// stem: green at the base, fading to straw yellow
box([X - w, 0, Z - w], [X + w, 3, Z + w], "strawGreen");
box([X - w, 3, Z - w], [X + w, 6, Z + w], "wheatGreen");
box([X - w, 6, Z - w], [X + w, 9, Z + w], "strawPale");
// two slim leaves from the lower stem, +-22.5 deg (one along x, one along z so both read from the 3/4 view)
box([X + w, 1.2, Z - .2], [X + 3.4, 1.6, Z + .2], { all: "leaf", top: "leafLight" }, ["z", 22.5, [X + w, 1.2, Z]]);
box([X - .2, 2.6, Z - 3.2], [X + .2, 3.0, Z - w], { all: "leaf", top: "leafLight" }, ["x", 22.5, [X, 2.6, Z - w]]);
// seed head: slim tapered stack of 5 small boxes
const head = [[9, 10, .4, "strawGold"], [10, 11, .37, "strawGold"], [11, 12, .33, "strawPale"], [12, 13, .27, "strawGold"], [13, 14, .2, "strawPale"]];
for (const [y0, y1, hw, c] of head) box([X - hw, y0, Z - hw], [X + hw, y1, Z + hw], { all: c, top: "strawGold", bottom: "strawDark" });
// tiny kernels alternating sides like a real ear (flush against the head, smaller toward the tip)
head.slice(0, 4).forEach(([y0, y1, hw], i) => {
  const k = .17, o = .3, c = i % 2 ? "strawPale" : "strawGold";
  if (i % 2 === 0) { box([X + hw - .05, y0 + .1, Z - k], [X + hw + o, y1 - .1, Z + k], c); box([X - hw - o, y0 + .1, Z - k], [X - hw + .05, y1 - .1, Z + k], c); }
  else { box([X - k, y0 + .1, Z + hw - .05], [X + k, y1 - .1, Z + hw + o], c); box([X - k, y0 + .1, Z - hw - o], [X + k, y1 - .1, Z - hw + .05], c); }
});
fs.writeFileSync(path.join(here, "preview/wheat_single.json"), JSON.stringify({
  textures: { "0": "pipifacilities:item/station_palette", particle: "pipifacilities:item/station_palette" }, elements: el,
}));
console.log(el.length, "elements");
