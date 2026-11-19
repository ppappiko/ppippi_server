// 15x15 ranch station model (one item model shown by an ItemDisplay scaled 15x, so 16 model px = 15 blocks).
// node dev/art/make-ranch-model.mjs -> pipi-facilities/assets/pipifacilities/models/item/station_ranch.json
//                                    + prints RANCH_SOLID for Facility.java (cells blocked by invisible barriers)
// Design (user's sketch): a round ring of thin fence (16-sided, extra mid posts), one entrance with a dirt path, two hay stacks.
// The ItemDisplay renders the model turned 180 degrees about Y, so world (x, z) = (15 - mx, 15 - mz) in blocks.
// Entrance: model +z side -> world north (-z).
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
const here = path.dirname(fileURLToPath(import.meta.url));
const out = path.resolve(here, "../pipi-facilities/assets/pipifacilities/models/item/station_ranch.json");
const N = 15, B = 16 / N, r4 = v => Math.round(v * 10000) / 10000;
const C = N / 2, R = 6.2;
const POSTS = 16, angOf = k => (11.25 + 22.5 * k) * Math.PI / 180;   // angle from +x toward +z (model)
const GAP_CHORD = 3;                                                   // chord 3 -> 4 spans 78.75..101.25 deg = model +z
const elements = [];
function box(from, to, tex, { top = tex, rot, name } = {}) {
  const f = from.map(v => r4(v * B)), t = to.map(v => r4(v * B)), sz = [0, 1, 2].map(i => Math.min(16, (to[i] - from[i]) * 16));
  const uv = (w, h) => [0, 0, r4(Math.max(w, .01)), r4(Math.max(h, .01))];
  const el = { name, from: f, to: t, faces: {
    north: { uv: uv(sz[0], sz[1]), texture: tex }, south: { uv: uv(sz[0], sz[1]), texture: tex },
    east: { uv: uv(sz[2], sz[1]), texture: tex }, west: { uv: uv(sz[2], sz[1]), texture: tex },
    up: { uv: uv(sz[0], sz[2]), texture: top }, down: { uv: uv(sz[0], sz[2]), texture: top } } };
  if (rot) el.rotation = { angle: rot.angle, axis: "y", origin: rot.origin.map(v => r4(v * B)) };
  elements.push(el);
}
const pt = k => [C + R * Math.cos(angOf(k)), C + R * Math.sin(angOf(k))];
const post = (x, z, P, H, name) => box([x - P, 0, z - P], [x + P, H, z + P], "#plank", { name });
// posts: one at every corner of the 16-gon (gate posts thicker/taller) + one in the middle of every fenced chord
for (let k = 0; k < POSTS; k++) {
  const gate = k === GAP_CHORD || k === (GAP_CHORD + 1) % POSTS;
  post(...pt(k), gate ? .13 : .09, gate ? 1.45 : 1.15, gate ? "gate_post" : "post");
}
for (let k = 0; k < POSTS; k++) {
  if (k === GAP_CHORD) continue;
  const [x1, z1] = pt(k), [x2, z2] = pt((k + 1) % POSTS);
  post((x1 + x2) / 2, (z1 + z2) / 2, .08, 1.1, "mid_post");
}
// rails: each chord is one axis-aligned box turned by a legal model angle about its centre (chord directions are multiples of 22.5).
// Minecraft turns +x toward -z for a positive y angle.
for (let k = 0; k < POSTS; k++) {
  if (k === GAP_CHORD) continue;
  const [x1, z1] = pt(k), [x2, z2] = pt((k + 1) % POSTS), mx = (x1 + x2) / 2, mz = (z1 + z2) / 2, len = Math.hypot(x2 - x1, z2 - z1) + .05;
  let phi = Math.atan2(z2 - z1, x2 - x1) * 180 / Math.PI;
  phi = ((Math.round(phi / 22.5) * 22.5) % 180 + 180) % 180;
  let alongX = true, a = phi;
  if (a > 45 && a < 135) { alongX = false; a -= 90; } else if (a >= 135) a -= 180;
  for (const y of [.4, .82]) {
    const t = .05, h = .12;
    const from = alongX ? [mx - len / 2, y, mz - t] : [mx - t, y, mz - len / 2], to = alongX ? [mx + len / 2, y + h, mz + t] : [mx + t, y + h, mz + len / 2];
    box(from, to, "#plank", { rot: -a ? { angle: -a, origin: [mx, y, mz] } : null, name: "rail" });
  }
}
// dirt path from the gate out to the footprint edge (model +z), slightly into the ring
const [gx1] = pt(GAP_CHORD), [gx2] = pt(GAP_CHORD + 1);
const pathL = Math.min(gx1, gx2) + .25, pathR = Math.max(gx1, gx2) - .25;
box([pathL, 0, C + R - 1.2], [pathR, .035, N], "#path", { top: "#path_top", name: "path" });
// two hay stacks, left and right of the gate's axis (each 2 bales long + 1 on top)
const HAY = [[3.2, 6, 2.6], [10.2, 6.4, 2.6]];   // [x, z, length along z] in model blocks
for (const [x, z, l] of HAY) {
  box([x, 0, z], [x + 1.4, .9, z + l], "#hay", { top: "#hay_top", name: "hay" });
  box([x + .2, .9, z + .4], [x + 1.2, 1.6, z + l - .5], "#hay", { top: "#hay_top", name: "hay_top" });
}
fs.writeFileSync(out, JSON.stringify({
  textures: { plank: "minecraft:block/oak_planks", hay: "minecraft:block/hay_block_side", hay_top: "minecraft:block/hay_block_top",
    path: "minecraft:block/dirt_path_side", path_top: "minecraft:block/dirt_path_top", particle: "minecraft:block/hay_block_side" },
  elements,
  display: { fixed: { rotation: [0, 0, 0], translation: [0, 0, 0], scale: [1, 1, 1] }, gui: { rotation: [30, 45, 0], translation: [0, 0, 0], scale: [.6, .6, .6] } },
}));
// blocked WORLD cells: every cell the fence line passes through (4-connected, so nothing slips through a corner) + hay cells
const solid = Array.from({ length: N }, () => Array(N).fill("."));
const W = (mx, mz) => [N - 1 - Math.floor(mx), N - 1 - Math.floor(mz)];
let prev = null;
const mark = (mx, mz) => {
  const [x, z] = W(mx, mz);
  if (x < 0 || z < 0 || x >= N || z >= N) { prev = null; return; }
  if (prev && prev[0] !== x && prev[1] !== z) solid[prev[1]][x] = "#";
  solid[z][x] = "#"; prev = [x, z];
};
for (let k = 0; k < POSTS; k++) {
  if (k === GAP_CHORD) { prev = null; continue; }
  const [x1, z1] = pt(k), [x2, z2] = pt((k + 1) % POSTS);
  for (let s = 0; s <= 60; s++) mark(x1 + (x2 - x1) * s / 60, z1 + (z2 - z1) * s / 60);
}
for (const [x, z, l] of HAY) for (let mx = Math.floor(x); mx < Math.ceil(x + 1.4); mx++) for (let mz = Math.floor(z); mz < Math.ceil(z + l); mz++) {
  const [wx, wz] = W(mx + .5, mz + .5); solid[wz][wx] = "#";
}
solid[0][0] = ".";   // the anchor block itself
const rows = solid.map(r => r.join(""));
console.log("station_ranch.json", elements.length, "elements\n" + rows.join("\n"));
console.log("RANCH_SOLID = " + rows.map(r => `"${r}"`).join(" + "));
