// 촌장의 집: a timber-framed elder's house on the north side of the pipi_town plaza, front door facing the plaza (south).
//   node dev/art/town/chief_house.mjs  -> dev/pipi-rpg/res/chief_house.json (pasted into the live town by PipiLife ChiefHouse)
//                                         + dev/art/town/iso_chief.png (preview)
// The town world already exists, so this is not part of gen.mjs: PipiLife clears the "clear" boxes, then places "blocks"
// (once, data flag town-chief-house; /pipilife maps chiefhouse rebuilds). "chief" is where the 촌장 stands inside.
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { loadIsland } from "./island.mjs";
import { World, Ctx, BASE } from "./world.mjs";
import { st, lant, logY } from "./bcommon.mjs";
import { iso } from "./render.mjs";
const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "../../..");

const CX = 0, CZ = -38;                       // house centre; the plaza (centre 0,0, radius ~25) is to the south
const I = loadIsland(), w = new World(I);
for (let z = CZ - 12; z <= CZ + 16; z++) for (let x = CX - 12; x <= CX + 12; x++) w.setSurf(x, z, 2);   // grass for the preview
const c = new Ctx(w, CX, CZ, "S");            // local +z = front door side (south, toward the plaza)
const ROOF = "deepslate_tile_stairs", ROOF_SLAB = "deepslate_tile_slab", WALL = "smooth_quartz", PLINTH = "stone_bricks", BEAM = "dark_oak_log", POST = "stripped_dark_oak_log";

// ---- foundation + floors
c.f(-6, 0, -5, 6, 0, 5, "stone_bricks");
c.f(-4, 0, -3, 4, 0, 3, "dark_oak_planks");
for (let x = -4; x <= 4; x++) for (let z = -3; z <= 3; z++) if ((x + z) % 2 === 0) c.s(x, 0, z, "spruce_planks");
// ---- walls: stone plinth, quartz plaster, dark oak frame
for (let y = 1; y <= 4; y++) for (let x = -5; x <= 5; x++) for (const z of [-4, 4]) c.s(x, y, z, y === 1 ? PLINTH : WALL);
for (let y = 1; y <= 4; y++) for (let z = -3; z <= 3; z++) for (const x of [-5, 5]) c.s(x, y, z, y === 1 ? PLINTH : WALL);
for (const x of [-5, 5]) for (const z of [-4, 0, 4]) for (let y = 1; y <= 4; y++) c.s(x, y, z, logY(POST));
for (const x of [-2, 2]) for (const z of [-4, 4]) for (let y = 1; y <= 4; y++) c.s(x, y, z, logY(POST));
for (let x = -5; x <= 5; x++) for (const z of [-4, 4]) c.s(x, 5, z, [BEAM, { axis: "x" }]);
for (let z = -3; z <= 3; z++) for (const x of [-5, 5]) c.s(x, 5, z, [BEAM, { axis: "z" }]);
c.f(-4, 5, -3, 4, 5, 3, "dark_oak_planks");                                     // ceiling / attic floor
// windows (front, back, sides) with flower boxes under the front ones
for (const x of [-4, -3, 3, 4]) for (const y of [2, 3]) c.s(x, y, 4, "glass_pane");
for (const x of [-1, 0, 1]) for (const y of [2, 3]) c.s(x, y, -4, "glass_pane");
for (const z of [-2, -1, 1, 2]) for (const y of [2, 3]) { c.s(-5, y, z, "glass_pane"); c.s(5, y, z, "glass_pane"); }
for (const x of [-4, -3, 3, 4]) { c.s(x, 1, 5, ["spruce_trapdoor", { facing: "south", half: "top", open: "false" }]); c.s(x, 2, 5, x % 2 ? "potted_red_tulip" : "potted_cornflower"); }
// door
c.s(0, 1, 4, ["dark_oak_door", { facing: "south", half: "lower", hinge: "left", open: "false" }]);
c.s(0, 2, 4, ["dark_oak_door", { facing: "south", half: "upper", hinge: "left", open: "false" }]);
c.s(0, 3, 4, ["dark_oak_planks"]); c.s(0, 4, 4, "chiseled_quartz_block");
// ---- gable roof (ridge along x), deepslate tiles with overhang
for (let k = 0; k <= 5; k++) for (let x = -7; x <= 7; x++) {
  c.s(x, 5 + k, -6 + k, st(ROOF, "south"));
  c.s(x, 5 + k, 6 - k, st(ROOF, "north"));
}
for (let x = -7; x <= 7; x++) c.s(x, 11, 0, [ROOF_SLAB, { type: "bottom" }]);
for (const x of [-7, 7]) for (let k = 0; k <= 5; k++) { c.s(x, 4 + k, -6 + k, ["deepslate_tile_wall", {}]); c.s(x, 4 + k, 6 - k, ["deepslate_tile_wall", {}]); }
// gable ends: plaster with a dark oak cross beam and a round window
for (const x of [-5, 5]) for (let k = 0; k <= 5; k++) for (let z = -5 + k; z <= 5 - k; z++) c.s(x, 6 + k, z, WALL);
for (const x of [-5, 5]) { for (let y = 6; y <= 10; y++) c.s(x, y, 0, logY(POST)); c.s(x, 7, -1, "glass_pane"); c.s(x, 7, 1, "glass_pane"); c.s(x, 8, -1, "glass_pane"); c.s(x, 8, 1, "glass_pane"); }
// chimney with a lit campfire (smoke)
for (let y = 6; y <= 12; y++) c.s(3, y, -2, y === 12 ? "polished_deepslate" : "bricks");
c.s(3, 13, -2, ["campfire", { lit: "true", signal_fire: "false" }]);
// ---- porch: andesite step, two posts, tiled canopy, lanterns, purple banners
c.f(-2, 0, 5, 2, 0, 6, "polished_andesite");
for (const x of [-2, 2]) { for (let y = 1; y <= 3; y++) c.s(x, y, 6, "dark_oak_fence"); c.s(x, 4, 6, "dark_oak_log"); }
for (let x = -3; x <= 3; x++) { c.s(x, 4, 5, st(ROOF, "north")); c.s(x, 4, 6, [ROOF_SLAB, { type: "top" }]); }
c.s(-1, 3, 6, lant(true)); c.s(1, 3, 6, lant(true));
for (const x of [-1, 1]) c.s(x, 3, 5, ["purple_wall_banner", { facing: "south" }]);
for (const x of [-2, 2]) c.s(x, 1, 7, st("stone_brick_stairs", "south"));
c.s(-1, 0, 7, "polished_andesite"); c.s(0, 0, 7, "polished_andesite"); c.s(1, 0, 7, "polished_andesite");
// ---- interior: back wall bookshelves, lectern desk, carpet, chandelier, storage, plants
for (const x of [-4, -3, 3, 4]) for (let y = 1; y <= 3; y++) c.s(x, y, -3, "bookshelf");
c.s(0, 1, -3, ["lectern", { facing: "south", has_book: "true" }]);
c.s(-1, 1, -3, ["candle", { candles: "3", lit: "true" }]); c.s(1, 1, -3, "potted_fern");
c.s(-2, 1, -3, "barrel"); c.s(2, 1, -3, "barrel");
for (const x of [-1, 1]) c.s(x, 3, -3, ["purple_wall_banner", { facing: "south" }]);
c.s(0, 3, -3, ["amethyst_cluster", { facing: "south" }]);
for (let x = -2; x <= 2; x++) for (let z = 0; z <= 2; z++) c.s(x, 1, z, (x + z) % 2 ? "red_carpet" : "purple_carpet");
c.s(0, 4, 0, lant(true)); c.s(-3, 4, 1, lant(true)); c.s(3, 4, 1, lant(true));
c.s(-4, 1, 2, ["chest", { facing: "east" }]); c.s(-4, 1, 1, "cartography_table"); c.s(-4, 1, -1, "potted_azalea_bush");
c.s(4, 1, 2, "flower_pot"); c.s(4, 1, 1, ["spruce_stairs", { facing: "west", half: "bottom" }]); c.s(4, 1, -1, "decorated_pot");
// ---- garden beds beside the house and a path to the plaza
for (const sx of [-1, 1]) for (let z = -3; z <= 3; z++) { c.s(sx * 7, 0, z, "grass_block"); c.s(sx * 7, 1, z, ["allium", "oxeye_daisy", "cornflower", "pink_tulip"][(z + 4) % 4]); }
for (const sx of [-1, 1]) for (let z = -4; z <= 4; z++) c.s(sx * 8, 1, z, "spruce_fence");
const PATH = ["dirt_path", "cobblestone", "gravel", "dirt_path", "stone_bricks"];
for (let z = 8; z <= 13; z++) for (let x = -1; x <= 1; x++) c.s(x, 0, z, PATH[(x + 2 * z + 9) % PATH.length]);
for (const z of [9, 12]) for (const x of [-2, 2]) { c.s(x, 1, z, "cobblestone_wall"); c.s(x, 2, z, lant(false)); }

// ---- export: every block in the house box, plus the boxes to clear first (trees, flowers) and the chief's spot
const X0 = CX - 9, X1 = CX + 9, Z0 = CZ - 7, Z1 = CZ + 13, Y0 = BASE, Y1 = BASE + 14;
const palette = [], pid = new Map(), blocks = [];
for (let y = Y0; y <= Y1; y++) for (let z = Z0; z <= Z1; z++) for (let x = X0; x <= X1; x++) {
  const n = w.name(x, y, z); if (!n) continue;
  let i = pid.get(n); if (i === undefined) { i = palette.length; palette.push(n); pid.set(n, i); }
  blocks.push([x, y, z, i]);
}
const out = {
  name: "촌장의 집", origin: [CX, BASE, CZ],
  clear: [[CX - 9, BASE + 1, CZ - 7, CX + 9, BASE + 14, CZ + 7], [CX - 2, BASE + 1, CZ + 8, CX + 2, BASE + 3, CZ + 13]],
  palette, blocks,
  chief: { x: CX + .5, y: BASE + 1, z: CZ - .5, yaw: 0 },
};
fs.writeFileSync(path.join(root, "dev/pipi-rpg/res/chief_house.json"), JSON.stringify(out));
console.log("chief_house.json:", blocks.length, "blocks,", palette.length, "block types");
iso(w, CX, CZ + 2, "S", 11, 16, path.join(here, "iso_chief.png"), 14);

// ---- map icon for the town map (WarpScreen draws it at the house; the parchment map texture itself is left alone)
import { readPng, writePng } from "../class-icons/pngio.mjs";
// ---- 16x16 map icon (same style as house_owned.png): slate roof, cream walls, chimney, a gold crown over the ridge
{
  const S = 16, cls = Array.from({ length: S }, () => Array(S).fill("."));
  const put = (x, y, ch) => { if (x >= 0 && y >= 0 && x < S && y < S) cls[y][x] = ch; };
  for (let y = 2; y <= 6; y++) { put(11, y, "b"); put(12, y, "b"); }                     // chimney
  for (let y = 3; y <= 8; y++) { const hw = (y - 3) + 1.5; for (let x = 0; x < S; x++) if (Math.abs(x + .5 - 8) <= hw) put(x, y, y === 8 ? "q" : x + .5 < 8 - hw + 1.2 ? "R" : "r"); }   // roof
  for (let y = 9; y <= 14; y++) for (let x = 3; x <= 12; x++) put(x, y, y === 9 || y === 14 ? "v" : x === 12 ? "w" : "W");   // walls
  for (const [x0, y0] of [[4, 10], [10, 10]]) for (let y = y0; y <= y0 + 1; y++) for (let x = x0; x <= x0 + 1; x++) put(x, y, "g");   // windows
  for (let y = 11; y <= 14; y++) { put(7, y, "D"); put(8, y, "D"); } put(8, 13, "d");   // door
  for (let x = 6; x <= 9; x++) put(x, 1, "G"); put(6, 0, "G"); put(9, 0, "G"); put(7, 0, "P"); put(8, 0, "P");   // crown
  const filled = (x, y) => x >= 0 && y >= 0 && x < S && y < S && cls[y][x] !== "." && cls[y][x] !== "k";
  for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) if (cls[y][x] === "." && (filled(x - 1, y) || filled(x + 1, y) || filled(x, y - 1) || filled(x, y + 1))) cls[y][x] = "k";
  const COL = { k: "#1c1c22", R: "#6a768c", r: "#4a5366", q: "#2f3542", W: "#efe9dc", w: "#d6cdb8", v: "#b5ab94", g: "#8ec8ec", D: "#5a3a24", d: "#e6b43a", G: "#f2c94c", P: "#9a5ae0", b: "#9a4a3a" };
  const px = Buffer.alloc(S * S * 4);
  for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) { const ch = cls[y][x]; if (ch === ".") continue; const h = COL[ch], o = (y * S + x) * 4; px[o] = parseInt(h.slice(1, 3), 16); px[o + 1] = parseInt(h.slice(3, 5), 16); px[o + 2] = parseInt(h.slice(5, 7), 16); px[o + 3] = 255; }
  fs.writeFileSync(path.join(root, "dev/pipi-travel/assets/pipitravel/textures/gui/chief_house.png"), writePng(px, S, S));
  const Z = 14, pv = Buffer.alloc(S * Z * S * Z * 4);
  for (let y = 0; y < S * Z; y++) for (let x = 0; x < S * Z; x++) { const o = (y * S * Z + x) * 4, i = (Math.floor(y / Z) * S + Math.floor(x / Z)) * 4; const a = px[i + 3]; pv[o] = a ? px[i] : 104; pv[o + 1] = a ? px[i + 1] : 160; pv[o + 2] = a ? px[i + 2] : 72; pv[o + 3] = 255; }
  fs.writeFileSync(path.join(here, "icon_chief_house.png"), writePng(pv, S * Z, S * Z));
  console.log("chief_house.png icon written");
}
