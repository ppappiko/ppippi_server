// SE continent (centre x=+3000, z=+5000): celestial GATHERING LAND — cherry groves and flower meadows, calcite/quartz bluffs with
// amethyst and ore, caves, streams and ponds, winding trails with a few lone calcite/quartz huts, a couple of small floating islets,
// trail props (froglight lamps, benches, signposts, crystal springs, campfires, bridges) and one sculk-infected zone around the
// boss-room entrance. Invisible light blocks keep it bright.   node dev/art/regions/se/gen.mjs
import { Vox, paintTerrain, hash, hash3, fbm, pick, B, stairs, lantern, SEA, DIR, clamp } from "../lib.mjs";
import { tree, bush, groundCover, house, roundTower, spire } from "../parts.mjs";
import { Roads, output } from "../common.mjs";
import { terrain, flatten, downhill, planTrails, easeTrails, veinOres, exposeOres, streams, autoPonds, caves, outcrops, stampTrails, placeHouses, placeProps, props, entranceSpec, reserveEntrance, sculkZone } from "../wild.mjs";

const W = 330, D = 330, V = new Vox(W, D, 165), SEED = 37;
const HUB = { x: 165, z: 170 };
const SC = { x: 236, z: 238, r: 30, y: 70 };
const E = entranceSpec(SC.x, SC.z, SC.y, [0, 1]);       // stairs descend southwards (approach from the north / hub side)
const LIGHT = "light[level=15,waterlogged=false]", ROD = "end_rod[facing=up]";

const H = terrain(W, D, {
  R: 134, seed: SEED, hills: 16, base: 4,
  bumps: [{ x: 110, z: 104, r: 48, h: 22, step: 5 }, { x: 236, z: 112, r: 40, h: 18, step: 6 }, { x: 96, z: 236, r: 36, h: 14, step: 4 }, { x: 170, z: 250, r: 26, h: 10 }],
});
flatten(H, W, D, { x: SC.x, z: SC.z, r: 15, y: SC.y, blend: 14 });
flatten(H, W, D, { x: HUB.x, z: HUB.z, r: 7, y: Math.round(H[HUB.z * W + HUB.x]), blend: 8 });
const lines = planTrails(H, W, D, {
  seed: SEED, hub: HUB, loop: { rx: 80, rz: 78 }, spokes: [-80, 30, 130, 210], r: 1.3,
  targets: [{ x: E.approach[0], z: E.approach[1], kind: "entrance", keep: true }, { x: 30, z: 170 }, { x: 165, z: 30 }, { x: 300, z: 160 }],
});
const corridor = easeTrails(H, W, D, lines);
const coast = paintTerrain(V, H, (x, z, h, c) => {
  if (h <= SEA) return { top: hash(x, z, 1) < 0.2 ? "clay" : "sand", sub: "sand", deep: "calcite" };
  if (c <= 2 && h <= SEA + 2) return { top: "sand", sub: "sand", deep: "calcite" };
  const sl = Math.max(Math.abs(H[z * W + x + 1] - H[z * W + x - 1]), Math.abs(H[(z + 1) * W + x] - H[(z - 1) * W + x]));
  if (sl >= 3) return { top: pick(["calcite", "diorite", "calcite", "stone", "smooth_basalt", "calcite"], hash(x, z, 4)), sub: pick(["calcite", "stone", "diorite"], hash(x, z, 8)), deep: "stone" };
  return { top: fbm(x / 20, z / 20, 2, 7) > 0.7 ? "moss_block" : "grass_block", sub: "dirt", deep: hash3(x, 0, z, 9) < 0.4 ? "calcite" : "stone" };
});
const ORE = [["coal_ore", 8, 40, 140], ["iron_ore", 8, 40, 120], ["copper_ore", 4, 40, 110], ["gold_ore", 2, 40, 70], ["redstone_ore", 1.4, 40, 58], ["lapis_ore", 2, 40, 64], ["diamond_ore", 0.5, 40, 50], ["emerald_ore", 0.6, 74, 140], ["amethyst_block", 1.5, 40, 80]];
const SURF_ORE = [["coal_ore", 6], ["iron_ore", 6], ["copper_ore", 3], ["gold_ore", 1.2], ["lapis_ore", 1.4], ["amethyst_block", 2]];
console.log("ore blocks", veinOres(V, SEED, ORE));
exposeOres(V, SEED + 1, SURF_ORE, 0.08);
reserveEntrance(V, E);
V.mark(HUB.x - 8, HUB.z - 8, HUB.x + 8, HUB.z + 8);

// ------------------------------------------------------------------ water, caves, crystal outcrops
streams(V, [{ ctrl: downhill(V, 112, 112, 7), r: 1.5 }, { ctrl: downhill(V, 232, 118, 8), r: 1.4 }, { ctrl: downhill(V, 96, 232, 9), r: 1.3 }], SEED);
const avoidSc = (x, z) => Math.hypot(x - SC.x, z - SC.z) < SC.r * 1.4;
console.log("ponds", autoPonds(V, 5, SEED + 2, corridor, (x, z) => avoidSc(x, z) || Math.hypot(x - HUB.x, z - HUB.z) < 20, ["sand", "clay", "sand", "sea_lantern"]).length);
console.log("caves", caves(V, 5, SEED + 3, corridor, [["coal_ore", 5], ["iron_ore", 5], ["lapis_ore", 2], ["gold_ore", 1.5], ["amethyst_block", 2.5], ["diamond_ore", 0.5]], { post: "end_stone_brick_wall", lamp: "pearlescent_froglight[axis=y]", light: ROD }).length);
outcrops(V, 18, SEED + 4, corridor, ["calcite", "calcite", "diorite", "smooth_basalt", "stone"], [["amethyst_block", 6], ["iron_ore", 4], ["coal_ore", 4], ["lapis_ore", 2], ["gold_ore", 1]], { avoid: avoidSc, ore: 0.25, cap: (x, z) => hash(x, z, 3) < 0.6 ? "amethyst_cluster[facing=up,waterlogged=false]" : "large_amethyst_bud[facing=up,waterlogged=false]" });

// ------------------------------------------------------------------ trails
const R = new Roads(V);
const paveMix = (x, z, t, d) => d > 1.1 && hash(x, z, 40) < 0.45 ? pick(["moss_block", "grass_block", "calcite"], hash(x, z, 41)) : pick(["dirt_path", "dirt_path", "calcite", "dirt_path", "smooth_quartz"], hash(x, z, 42));
console.log("bridges", stampTrails(V, R, lines, paveMix, { deck: "cherry_planks", rail: "cherry_fence", sea: "cherry_planks" }));
const lamp = (x, z, y) => { V.set(x, y + 1, z, "end_stone_brick_wall"); V.set(x, y + 2, z, "end_stone_brick_wall"); V.set(x, y + 3, z, "pearlescent_froglight[axis=y]"); V.set(x, y + 4, z, ROD); };
function spring(x, z, y) {   // tiny crystal-lined pool with an amethyst cluster
  for (let dz = -1; dz <= 1; dz++) for (let dx = -1; dx <= 1; dx++) V.set(x + dx, y, z + dz, dx || dz ? "smooth_quartz" : "water");
  V.set(x, y - 1, z, "sea_lantern");
  for (const [dx, dz] of [[1, 1], [-1, -1]]) V.set(x + dx, y + 1, z + dz, "amethyst_cluster[facing=up,waterlogged=false]");
}
// hub: quartz plaza with a crystal spring, signpost, bench, lamps
{
  const y = V.h(HUB.x, HUB.z);
  for (let dz = -5; dz <= 5; dz++) for (let dx = -5; dx <= 5; dx++) if (Math.hypot(dx, dz) <= 5.2) { const x = HUB.x + dx, z = HUB.z + dz; V.set(x, y, z, Math.round(Math.hypot(dx, dz)) === 4 ? "smooth_quartz" : pick(["calcite", "dirt_path", "quartz_bricks", "dirt_path"], hash(x, z, 50))); for (let k = 1; k <= 6; k++) V.set(x, y + k, z, 0); V.surf[z * W + x] = y; R.m[z * W + x] = 1; }
  spring(HUB.x, HUB.z, y);
  props.sign(V, HUB.x + 4, HUB.z - 3, "south", y, "cherry_fence", "cherry_sign");
  props.bench(V, HUB.x - 4, HUB.z + 3, "north", y, "quartz_stairs");
  lamp(HUB.x + 3, HUB.z + 4, y); lamp(HUB.x - 3, HUB.z - 4, y);
  V.mark(HUB.x - 6, HUB.z - 6, HUB.x + 6, HUB.z + 6);
}

// ------------------------------------------------------------------ lone huts (round calcite towers + small gabled quartz huts)
function roundHut(cx, cz, r, face) {
  let s = 0, n = 0; for (let dz = -r; dz <= r; dz++) for (let dx = -r; dx <= r; dx++) { s += V.h(cx + dx, cz + dz); n++; }
  const y = Math.max(SEA + 1, Math.round(s / n));
  const top = roundTower(V, cx, cz, r, y, 5, { wall: "calcite", wall2: "white_concrete", win: "white_stained_glass_pane", floor: "birch_planks", spire: "purpur_stairs", spireFull: "purpur_block", base: "quartz_bricks" });
  V.set(cx, top, cz, "amethyst_block"); V.set(cx, top + 1, cz, ROD);
  const [fx, fz] = DIR[face];
  for (let k = 1; k <= 2; k++) V.set(cx + fx * r, y + k, cz + fz * r, B("cherry_door", { facing: face, half: k === 1 ? "lower" : "upper", hinge: "left", open: "false" }));
  V.set(cx + fx * (r + 1), y, cz + fz * (r + 1), "quartz_bricks");
  V.set(cx, y + 4, cz, "pearlescent_froglight[axis=y]");
  V.set(cx - fz, y + 1, cz + fx, "bookshelf"); V.set(cx + fz, y + 1, cz - fx, "brewing_stand[has_bottle_0=false,has_bottle_1=false,has_bottle_2=false]");
  V.mark(cx - r - 1, cz - r - 1, cx + r + 1, cz + r + 1);
  return { door: [cx + fx * r, cz + fz * r], y };
}
const HST = [
  { base: "quartz_bricks", frame: "quartz_pillar", fill: "calcite", fill2: "white_concrete", roofSt: "purpur_stairs", roofFull: "purpur_block", floor: "birch_planks", win: "white_stained_glass_pane", door: "birch_door", apron: "calcite", posts: false, furn: ["bookshelf", "amethyst_block", "lectern[facing=north]", "crafting_table", "flower_pot"] },
  { base: "end_stone_bricks", frame: "quartz_pillar", fill: "white_concrete", fill2: "calcite", roofSt: "quartz_stairs", roofFull: "quartz_block", floor: "cherry_planks", win: "light_blue_stained_glass_pane", door: "cherry_door", apron: "calcite", posts: false, furn: ["bookshelf", "flower_pot", "cherry_planks", "crafting_table"] },
  { base: "quartz_bricks", frame: "stripped_cherry_log", fill: "calcite", fill2: "cherry_planks", roofSt: "cherry_stairs", roofFull: "cherry_planks", floor: "cherry_planks", win: "pink_stained_glass_pane", door: "cherry_door", apron: "calcite", posts: true },
];
const HOUSES = placeHouses(V, R, lines, {
  seed: SEED, max: 10, spacing: 36, slope: 4, sizes: [[7, 7], [7, 8], [8, 7], [7, 7]],
  avoid: (x, z) => Math.hypot(x - SC.x, z - SC.z) < SC.r * 1.6 || Math.hypot(x - HUB.x, z - HUB.z) < 22,
  path: (x, z) => pick(["dirt_path", "calcite", "dirt_path"], hash(x, z, 8)),
  build: (x0, z0, x1, z1, face, r) => {
    if (r < 0.4) return roundHut((x0 + x1) >> 1, (z0 + z1) >> 1, 3, face);
    const hb = house(V, x0, z0, x1, z1, face, { ...HST[Math.floor(r * 997) % HST.length], floors: 1, lamp: false });
    V.set((x0 + x1) >> 1, hb.top + 1, (z0 + z1) >> 1, ROD);
    const [fx, fz] = DIR[face]; V.set(hb.door[0] + fx + (fz ? 2 : 0), hb.y + 1, hb.door[1] + fz + (fx ? 2 : 0), "flowering_azalea");
    return hb;
  },
});
console.log("huts", HOUSES.length);

// ------------------------------------------------------------------ a couple of small floating islets
function islet(cx, cz, y, r, seed) {
  for (let dz = -r - 3; dz <= r + 3; dz++) for (let dx = -r - 3; dx <= r + 3; dx++) {
    const x = cx + dx, z = cz + dz, d = Math.hypot(dx, dz) / (r * (0.85 + fbm(x / 9, z / 9, 2, seed) * 0.3)); if (d > 1) continue;
    const depth = Math.round((1 - d * d) * r * 1.1 + fbm(x / 4, z / 4, 2, seed + 1) * 4);
    for (let k = 0; k <= depth; k++) { const yy = y - k; V.set(x, yy, z, k === 0 ? (d > 0.9 ? "moss_block" : "grass_block") : k < 3 ? "dirt" : k > depth - 2 ? (hash3(x, yy, z, 6) < 0.5 ? "amethyst_block" : "calcite") : pick(["calcite", "end_stone", "calcite", "diorite", "iron_ore", "calcite", "lapis_ore"], hash3(x, yy, z, 7))); }
    if (depth > 4 && hash(x, z, 8) < 0.06) V.set(x, y - depth - 1, z, "amethyst_cluster[facing=down,waterlogged=false]");
    const rr = hash(x, z, 12); if (d < 0.85 && rr < 0.08) V.set(x, y + 1, z, "pink_petals[facing=north,flower_amount=4]"); else if (d < 0.9 && rr < 0.16) V.set(x, y + 1, z, "short_grass");
  }
  tree(V, cx, cz, "cherry", 0.9, y); V.set(cx + 2, y + 1, cz + 2, LIGHT);
}
const ISLETS = [{ x: 118, z: 150, y: 116, r: 9 }, { x: 214, z: 176, y: 124, r: 7 }, { x: 150, z: 236, y: 110, r: 6 }];
ISLETS.forEach((o, i) => islet(o.x, o.z, o.y, o.r, 40 + i));

// ------------------------------------------------------------------ trail props
const P = placeProps(V, R, lines, {
  seed: SEED, every: 26, spacing: 16,
  avoid: (x, z) => Math.hypot(x - SC.x, z - SC.z) < SC.r || HOUSES.some(o => Math.hypot(o.x - x, o.z - z) < 9),
  kinds: [
    { name: "lamp", w: 5, build: (x, z, f, y) => lamp(x, z, y) },
    { name: "bench", w: 2, cap: 8, build: (x, z, f, y) => props.bench(V, x, z, f, y, "quartz_stairs", "flowering_azalea") },
    { name: "sign", w: 1.5, cap: 6, build: (x, z, f, y) => props.sign(V, x, z, f, y, "cherry_fence", "cherry_sign") },
    { name: "campfire", w: 1, cap: 3, need: 2, off: 2, build: (x, z, f, y) => props.campfire(V, x, z, y, "smooth_quartz_slab[type=bottom,waterlogged=false]", "calcite") },
    { name: "spring", w: 1, cap: 3, need: 1, build: (x, z, f, y) => spring(x, z, y) },
    { name: "crystal", w: 1, cap: 4, build: (x, z, f, y) => { V.set(x, y + 1, z, "amethyst_block"); V.set(x, y + 2, z, "amethyst_cluster[facing=up,waterlogged=false]"); V.set(x + DIR[f][1], y + 1, z - DIR[f][0], "small_amethyst_bud[facing=up,waterlogged=false]"); } },
  ],
});
console.log("props", JSON.stringify(P.count));

// ------------------------------------------------------------------ cherry groves, birch, azalea, petals and flowers
for (let z = 2; z < D - 2; z++) for (let x = 2; x < W - 2; x++) {
  const h = V.h(x, z);
  if (h <= SEA + 1 || V.occupied(x, z) || R.on(x, z, 2) || coast(x, z) <= 3 || Math.hypot(x - SC.x, z - SC.z) < SC.r * 1.05) continue;
  if (!/grass_block|moss_block/.test(V.name(x, h, z))) continue;
  const n = fbm(x / 34, z / 34, 3, 51), r = hash(x, z, 300);
  const p = n > 0.6 ? 0.022 : n > 0.5 ? 0.007 : 0.0015;
  if (r < p && ![[2, 0], [-2, 0], [0, 2], [0, -2], [3, 0], [0, 3]].some(([dx, dz]) => V.occupied(x + dx, z + dz) || R.on(x + dx, z + dz))) { tree(V, x, z, hash(x, z, 301) < 0.75 ? "cherry" : hash(x, z, 302) < 0.6 ? "birch" : "azalea", 0.5 + hash(x, z, 303)); V.mark(x - 2, z - 2, x + 2, z + 2); }
  else if (r < p * 1.8) bush(V, x, z, "flowering_azalea_leaves");
}
for (let z = 0; z < D; z++) for (let x = 0; x < W; x++) {
  if (R.on(x, z) || V.occupied(x, z)) continue;
  const y = V.h(x, z); if (y <= SEA || !V.air(x, y + 1, z) || !/grass_block|moss_block/.test(V.name(x, y, z))) continue;
  const r = hash(x, z, 600), n = fbm(x / 16, z / 16, 2, 61);
  if (n > 0.55 && r < 0.4) V.set(x, y + 1, z, B("pink_petals", { facing: pick(["north", "east", "south", "west"], hash(x, z, 602)), flower_amount: 1 + Math.floor(hash(x, z, 603) * 4) }));
  else groundCover(V, x, z, 0.2, ["allium", "azure_bluet", "lily_of_the_valley", "white_tulip", "oxeye_daisy", "cornflower"], 0.05);
}

// ------------------------------------------------------------------ sculk zone + boss entrance, then light
const marker = sculkZone(V, E, SC.r, 41);
console.log("entrance marker", marker);
for (let z = 5; z < D; z += 10) for (let x = 5; x < W; x += 10) {
  if (Math.hypot(x - SC.x, z - SC.z) < SC.r) continue;          // the infected zone stays dark
  const y = V.h(x, z); if (y > SEA && V.air(x, y + 1, z)) V.set(x, y + 1, z, LIGHT); else if (y > SEA && V.air(x, y + 2, z)) V.set(x, y + 2, z, LIGHT);
}

output(V, "se", { x: HUB.x, y: V.h(HUB.x, HUB.z + 3) + 1, z: HUB.z + 3, yaw: 180 }, [
  { name: "sculk", x: SC.x, z: SC.z, r: 26, a: 6 },
  { name: "entrance", x: E.x - 12, z: E.z, z1: E.z + 12, r: 12, a: 12, rot: 0 },
  { name: "hub", x: HUB.x, z: HUB.z, r: 40, a: 5 },
  { name: "islet", x: ISLETS[0].x, z: ISLETS[0].z, r: 18, a: 6 },
  ...(HOUSES[0] ? [{ name: "hut", x: HOUSES[0].x, z: HOUSES[0].z, r: 20, a: 7 }] : []),
], marker);
