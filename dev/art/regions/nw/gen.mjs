// NW continent (centre x=-3000, z=-5000): medieval-fantasy GATHERING LAND — temperate forests and meadows, terraced hills with
// cliffs, caves and ore, streams and ponds, winding dirt trails with a few lone timber/stone cottages, a ruined watchtower, trail
// props (benches, lanterns, signposts, wells, campfires, bridges) and one sculk-infected zone around the boss-room entrance.
//   node dev/art/regions/nw/gen.mjs
import { Vox, paintTerrain, hash, hash3, fbm, pick, B, lantern, SEA, DIR } from "../lib.mjs";
import { tree, bush, groundCover, house, roundTower, well } from "../parts.mjs";
import { Roads, output } from "../common.mjs";
import { terrain, flatten, downhill, planTrails, easeTrails, veinOres, exposeOres, streams, autoPonds, caves, outcrops, stampTrails, placeHouses, placeProps, props, entranceSpec, reserveEntrance, sculkZone } from "../wild.mjs";

const W = 330, D = 330, V = new Vox(W, D, 160), SEED = 11;
const HUB = { x: 160, z: 172 };
const SC = { x: 236, z: 214, r: 32, y: 70 };          // sculk zone / boss entrance
const E = entranceSpec(SC.x, SC.z, SC.y, [1, 0]);       // stairs descend eastwards (approach from the hub side)

const H = terrain(W, D, {
  R: 132, seed: SEED, hills: 18, base: 4,
  bumps: [{ x: 120, z: 92, r: 52, h: 26, step: 6 }, { x: 214, z: 104, r: 40, h: 18, step: 5 }, { x: 84, z: 214, r: 38, h: 16, step: 5 }, { x: 180, z: 262, r: 30, h: 10 }],
});
flatten(H, W, D, { x: SC.x, z: SC.z, r: 16, y: SC.y, blend: 14 });
flatten(H, W, D, { x: HUB.x, z: HUB.z, r: 7, y: Math.round(H[HUB.z * W + HUB.x]), blend: 8 });
const lines = planTrails(H, W, D, {
  seed: SEED, hub: HUB, loop: { rx: 82, rz: 76 }, spokes: [-100, 20, 140, 200], r: 1.3,
  targets: [{ x: E.approach[0], z: E.approach[1], kind: "entrance", keep: true }, { x: 40, z: 150 }, { x: 290, z: 150 }, { x: 165, z: 300 }],
});
const corridor = easeTrails(H, W, D, lines);
const coast = paintTerrain(V, H, (x, z, h, c) => {
  if (h <= SEA) return { top: hash(x, z, 1) < 0.15 ? "gravel" : hash(x, z, 2) < 0.1 ? "clay" : "sand", sub: "sand", deep: "stone" };
  if (c <= 2 && h <= SEA + 2) return { top: "sand", sub: "sand", deep: "sandstone" };
  const sl = Math.max(Math.abs(H[z * W + x + 1] - H[z * W + x - 1]), Math.abs(H[(z + 1) * W + x] - H[(z - 1) * W + x]));
  if (sl >= 3.2) return { top: pick(["stone", "andesite", "cobblestone", "mossy_cobblestone", "stone", "gravel"], hash(x, z, 4)), sub: "stone", deep: "stone" };
  const n = fbm(x / 26, z / 26, 2, 5);
  return { top: n > 0.62 && hash(x, z, 6) < 0.5 ? "podzol" : hash(x, z, 5) < 0.04 ? "coarse_dirt" : "grass_block", sub: "dirt", deep: hash3(x, 0, z, 7) < 0.15 ? "andesite" : "stone" };
});
const ORE = [["coal_ore", 10, 40, 140], ["iron_ore", 8, 40, 120], ["copper_ore", 4, 40, 110], ["gold_ore", 1.5, 40, 64], ["redstone_ore", 1.5, 40, 58], ["lapis_ore", 1.2, 40, 60], ["diamond_ore", 0.4, 40, 50], ["emerald_ore", 0.4, 74, 140]];
const SURF_ORE = [["coal_ore", 10], ["iron_ore", 7], ["copper_ore", 4], ["gold_ore", 1], ["lapis_ore", 0.6], ["redstone_ore", 0.6]];
console.log("ore blocks", veinOres(V, SEED, ORE));
exposeOres(V, SEED + 1, SURF_ORE, 0.08);
reserveEntrance(V, E);
V.mark(HUB.x - 8, HUB.z - 8, HUB.x + 8, HUB.z + 8);

// ------------------------------------------------------------------ water: streams off the hills, ponds in the meadows
streams(V, [
  { ctrl: downhill(V, 112, 96, 1), r: 1.6 },
  { ctrl: downhill(V, 214, 100, 2), r: 1.4 },
  { ctrl: [[150, 120], [176, 140], [196, 170], [206, 196], [190, 236], [200, 270], [214, 312]], r: 1.6 },
  { ctrl: downhill(V, 84, 214, 3), r: 1.3 },
], SEED);
const ponds = autoPonds(V, 4, SEED + 2, corridor, (x, z) => Math.hypot(x - SC.x, z - SC.z) < SC.r * 1.5 || Math.hypot(x - HUB.x, z - HUB.z) < 20);
console.log("ponds", ponds.length);
const CAVES = caves(V, 5, SEED + 3, corridor, [["coal_ore", 6], ["iron_ore", 6], ["copper_ore", 3], ["gold_ore", 1.4], ["redstone_ore", 1.2], ["lapis_ore", 1], ["diamond_ore", 0.4]], { post: "spruce_fence", lamp: lantern() });
console.log("caves", CAVES.length);
outcrops(V, 26, SEED + 4, corridor, ["stone", "andesite", "cobblestone", "mossy_cobblestone", "stone"], [["coal_ore", 8], ["iron_ore", 6], ["copper_ore", 3], ["gold_ore", 0.8]], { avoid: (x, z) => Math.hypot(x - SC.x, z - SC.z) < SC.r * 1.4, cap: (x, z) => pick(["moss_carpet", "short_grass", "fern"], hash(x, z, 9)) });

// ------------------------------------------------------------------ trails
const R = new Roads(V);
const dirtMix = (x, z, t, d) => d > 1.1 && hash(x, z, 40) < 0.4 ? pick(["coarse_dirt", "grass_block", "gravel"], hash(x, z, 41)) : pick(["dirt_path", "dirt_path", "dirt_path", "coarse_dirt", "gravel"], hash(x, z, 42));
console.log("bridges", stampTrails(V, R, lines, dirtMix, { deck: "spruce_planks", rail: "spruce_fence" }));

// ------------------------------------------------------------------ hub: crossroads well, signpost, benches
{
  const y = V.h(HUB.x, HUB.z);
  for (let dz = -5; dz <= 5; dz++) for (let dx = -5; dx <= 5; dx++) if (Math.hypot(dx, dz) <= 5.2) { const x = HUB.x + dx, z = HUB.z + dz; V.set(x, y, z, pick(["dirt_path", "gravel", "cobblestone", "dirt_path", "mossy_cobblestone"], hash(x, z, 50))); for (let k = 1; k <= 6; k++) V.set(x, y + k, z, 0); V.surf[z * W + x] = y; R.m[z * W + x] = 1; }
  well(V, HUB.x, HUB.z, y, "cobblestone", "spruce");
  props.sign(V, HUB.x + 4, HUB.z - 4, "south", y, "spruce_fence", "spruce_sign");
  props.bench(V, HUB.x - 4, HUB.z + 4, "north", y, "spruce_stairs");
  props.post(V, HUB.x + 4, HUB.z + 4, y, "spruce_fence", lantern());
  props.post(V, HUB.x - 4, HUB.z - 4, y, "spruce_fence", lantern());
  V.mark(HUB.x - 6, HUB.z - 6, HUB.x + 6, HUB.z + 6);
}

// ------------------------------------------------------------------ lone cottages along the trails
const STYLES = [
  { base: "cobblestone", frame: "spruce_log", fill: "cobblestone", fill2: "white_terracotta", roofSt: "dark_oak_stairs", roofFull: "dark_oak_planks", floor: "spruce_planks", chimney: "cobblestone", apron: "coarse_dirt" },
  { base: "stone_bricks", frame: "stripped_spruce_log", fill: "stone_bricks", fill2: "spruce_planks", roofSt: "deepslate_tile_stairs", roofFull: "deepslate_tiles", floor: "oak_planks", apron: "gravel" },
  { base: "cobblestone", frame: "dark_oak_log", fill: "white_terracotta", fill2: "white_terracotta", roofSt: "spruce_stairs", roofFull: "spruce_planks", floor: "spruce_planks", chimney: "cobblestone", apron: "coarse_dirt" },
  { base: "mossy_cobblestone", frame: "spruce_log", fill: "spruce_planks", fill2: "spruce_planks", roofSt: "spruce_stairs", roofFull: "spruce_planks", floor: "spruce_planks", chimney: "cobblestone", apron: "gravel", furn: ["barrel[facing=up,open=false]", "crafting_table", "smoker[facing=north,lit=false]", "composter[level=0]"] },
  { base: "stone_bricks", frame: "oak_log", fill: "cobblestone", fill2: "birch_planks", roofSt: "dark_oak_stairs", roofFull: "dark_oak_planks", floor: "oak_planks", chimney: "stone_bricks", apron: "coarse_dirt" },
];
const HOUSES = placeHouses(V, R, lines, {
  seed: SEED, max: 11, spacing: 34, slope: 4, sizes: [[7, 7], [8, 7], [7, 8], [9, 7], [8, 8]],
  avoid: (x, z) => Math.hypot(x - SC.x, z - SC.z) < SC.r * 1.6 || Math.hypot(x - HUB.x, z - HUB.z) < 22,
  build: (x0, z0, x1, z1, face, r) => {
    const st = STYLES[Math.floor(r * 997) % STYLES.length], hb = house(V, x0, z0, x1, z1, face, { ...st, floors: r < 0.8 ? 1 : 2 });
    // little yard: woodpile or flower box beside the house
    const [fx, fz] = DIR[face], sx = face === "north" || face === "south" ? x1 + 2 : hb.door[0] + 2, sz = face === "north" || face === "south" ? hb.door[1] : z1 + 2;
    const g = V.h(sx, sz); if (!V.occupied(sx, sz) && g > SEA) { V.set(sx, g + 1, sz, r < 0.5 ? "spruce_log[axis=x]" : "composter[level=0]"); if (r < 0.5) V.set(sx, g + 2, sz, "spruce_log[axis=x]"); V.mark(sx, sz, sx, sz); }
    return hb;
  },
});
console.log("houses", HOUSES.length);

// ------------------------------------------------------------------ ruined watchtower on a hilltop beside a trail
function ruinTower() {
  let best = null;
  for (const l of lines) for (let i = 0; i < l.pts.length; i += 4) for (const s of [-1, 1]) {
    const [px, pz] = l.pts[i], [qx, qz] = l.pts[Math.min(l.pts.length - 1, i + 1)], L = Math.hypot(qx - px, qz - pz) || 1;
    const x = Math.round(px - (qz - pz) / L * 9 * s), z = Math.round(pz + (qx - px) / L * 9 * s), h = V.h(x, z);
    if (Math.hypot(x - SC.x, z - SC.z) < 60 || HOUSES.some(o => Math.hypot(o.x - x, o.z - z) < 25)) continue;
    let ok = true; for (let dz = -5; dz <= 5 && ok; dz++) for (let dx = -5; dx <= 5; dx++) if (V.occupied(x + dx, z + dz) || R.on(x + dx, z + dz) || Math.abs(V.h(x + dx, z + dz) - h) > 2) { ok = false; break; }
    if (ok && (!best || h > best.h)) best = { x, z, h };
  }
  if (!best) return null;
  const { x: cx, z: cz, h: y } = best;
  roundTower(V, cx, cz, 3, y, 15, { wall: "stone_bricks", wall2: "mossy_stone_bricks", floor: "spruce_planks" });
  // ruin it: jagged broken top, a breach on one side, rubble around
  for (let dz = -5; dz <= 5; dz++) for (let dx = -5; dx <= 5; dx++) {
    const x = cx + dx, z = cz + dz, cut = y + 8 + Math.floor(fbm(x / 2, z / 2, 2, 9) * 9);
    for (let yy = cut; yy <= y + 18; yy++) V.set(x, yy, z, 0);
    for (let yy = y + 1; yy < cut; yy++) { const n = V.name(x, yy, z); if (/stone_bricks/.test(n) && hash3(x, yy, z, 3) < 0.18) V.set(x, yy, z, pick(["cracked_stone_bricks", "mossy_stone_bricks", "cobblestone", "air"], hash3(x, yy, z, 4))); }
  }
  for (let k = 1; k <= 3; k++) for (let t = -1; t <= 1; t++) V.set(cx + 3, y + k, cz + t, 0);   // breach / doorway
  for (let i = 0; i < 26; i++) { const a = hash(i, 1, 5) * Math.PI * 2, r = 4 + hash(i, 2, 5) * 3, x = Math.round(cx + Math.cos(a) * r), z = Math.round(cz + Math.sin(a) * r), g = V.h(x, z); if (!V.occupied(x, z) && !R.on(x, z)) V.set(x, g + 1, z, pick(["cobblestone", "mossy_cobblestone", "stone_brick_slab[type=bottom]", "mossy_stone_brick_slab[type=bottom]", "cobblestone_wall"], hash(i, 3, 5))); }
  for (let yy = y + 1; yy <= y + 14; yy++) if (V.air(cx - 2, yy, cz)) V.set(cx - 2, yy, cz, "ladder[facing=east]");
  V.mark(cx - 6, cz - 6, cx + 6, cz + 6);
  return best;
}
const TOWER = ruinTower();
console.log("tower", TOWER);

// ------------------------------------------------------------------ trail props
const P = placeProps(V, R, lines, {
  seed: SEED, every: 26, spacing: 16,
  avoid: (x, z) => Math.hypot(x - SC.x, z - SC.z) < SC.r || HOUSES.some(o => Math.hypot(o.x - x, o.z - z) < 9),
  kinds: [
    { name: "lantern", w: 5, build: (x, z, f, y) => props.post(V, x, z, y, "spruce_fence", lantern()) },
    { name: "bench", w: 2, cap: 8, build: (x, z, f, y) => props.bench(V, x, z, f, y, "spruce_stairs", "spruce_trapdoor[facing=north,half=bottom,open=true,waterlogged=false,powered=false]".replace("facing=north", "facing=" + f)) },
    { name: "sign", w: 1.5, cap: 6, build: (x, z, f, y) => props.sign(V, x, z, f, y, "spruce_fence", "spruce_sign") },
    { name: "campfire", w: 1, cap: 3, need: 2, off: 2, build: (x, z, f, y) => props.campfire(V, x, z, y, a => "spruce_log[axis=" + a + "]") },
    { name: "well", w: 0.6, cap: 1, need: 3, off: 3, build: (x, z, f, y) => well(V, x, z, y, "cobblestone", "oak") },
    { name: "woodpile", w: 1, cap: 4, need: 1, build: (x, z, f, y) => { const ax = f === "north" || f === "south" ? "x" : "z", [lx, lz] = ax === "x" ? [1, 0] : [0, 1]; for (const s of [-1, 0, 1]) for (let k = 1; k <= (s ? 1 : 2); k++) V.set(x + lx * s, y + k, z + lz * s, "oak_log[axis=" + ax + "]"); } },
  ],
});
console.log("props", JSON.stringify(P.count));

// ------------------------------------------------------------------ nature: forests, meadows, flowers
for (let z = 2; z < D - 2; z++) for (let x = 2; x < W - 2; x++) {
  const h = V.h(x, z);
  if (h <= SEA + 1 || V.occupied(x, z) || R.on(x, z, 2) || coast(x, z) <= 3 || Math.hypot(x - SC.x, z - SC.z) < SC.r * 1.05) continue;
  if (!/grass_block|podzol|coarse_dirt/.test(V.name(x, h, z))) continue;
  const forest = fbm(x / 42, z / 42, 3, 21), r = hash(x, z, 300);
  const p = forest > 0.58 ? 0.07 : forest > 0.5 ? 0.03 : forest > 0.42 ? 0.009 : 0.002;
  if (r < p) {
    if ([[2, 0], [-2, 0], [0, 2], [0, -2]].some(([dx, dz]) => V.occupied(x + dx, z + dz) || R.on(x + dx, z + dz))) continue;
    const kind = forest > 0.62 ? pick(["spruce", "dark", "spruce", "oak"], hash(x, z, 301)) : h > SEA + 18 ? pick(["spruce", "spruce", "birch"], hash(x, z, 304)) : pick(["oak", "oak", "birch", "oak", "spruce"], hash(x, z, 302));
    tree(V, x, z, kind, 0.6 + hash(x, z, 303) * 1.2);
    V.mark(x - 1, z - 1, x + 1, z + 1);
  } else if (r < p * 2.4) bush(V, x, z, forest > 0.55 ? "spruce_leaves" : "oak_leaves");
}
for (let z = 0; z < D; z++) for (let x = 0; x < W; x++) {
  if (R.on(x, z) || V.occupied(x, z)) continue;
  const meadow = fbm(x / 30, z / 30, 2, 33);
  groundCover(V, x, z, meadow > 0.55 ? 0.4 : 0.24, undefined, meadow > 0.6 ? 0.09 : 0.03);
  const y = V.h(x, z);
  if (/podzol/.test(V.name(x, y, z)) && V.air(x, y + 1, z) && hash(x, z, 610) < 0.12) V.set(x, y + 1, z, pick(["fern", "brown_mushroom", "sweet_berry_bush[age=3]", "fern"], hash(x, z, 611)));
}
// pumpkins / berry bushes here and there in the meadows
for (let i = 0; i < 400; i++) { const x = Math.floor(hash(i, 1, 77) * W), z = Math.floor(hash(i, 2, 77) * D), y = V.h(x, z); if (y > SEA + 1 && !V.occupied(x, z) && !R.on(x, z, 1) && /grass_block/.test(V.name(x, y, z)) && /air|short_grass/.test(V.name(x, y + 1, z))) V.set(x, y + 1, z, pick(["sweet_berry_bush[age=3]", "pumpkin", "sweet_berry_bush[age=2]", "melon"], hash(i, 3, 77))); }

// ------------------------------------------------------------------ sculk zone + boss entrance
const marker = sculkZone(V, E, SC.r, 17);
console.log("entrance marker", marker);

output(V, "nw", { x: HUB.x, y: V.h(HUB.x, HUB.z + 3) + 1, z: HUB.z + 3, yaw: 180 }, [
  { name: "sculk", x: SC.x, z: SC.z, r: 26, a: 6 },
  { name: "entrance", x: E.x, z: E.z, z1: E.z, r: 12, a: 12 },   // cut-away through the stairwell centre line
  { name: "hub", x: HUB.x, z: HUB.z, r: 40, a: 5 },
  ...(TOWER ? [{ name: "tower", x: TOWER.x, z: TOWER.z, r: 24, a: 6 }] : []),
  ...(HOUSES[0] ? [{ name: "house", x: HOUSES[0].x, z: HOUSES[0].z, r: 22, a: 7 }] : []),
], marker);
