// NE continent (centre x=+3000, z=-5000): mechanised GATHERING LAND — rocky terraced hills and quarry cliffs full of exposed ore,
// gravel flats and scrub, caves, streams and ponds, winding gravel trails with a few lone copper/brick workshop sheds, a couple of
// wind turbines, rusty rail remnants and pipe runs, trail props, and one sculk-infected zone around the boss-room entrance.
//   node dev/art/regions/ne/gen.mjs
import { Vox, paintTerrain, hash, hash3, fbm, pick, B, stairs, lantern, SEA, DIR } from "../lib.mjs";
import { tree, bush, groundCover, house, foundation } from "../parts.mjs";
import { Roads, output } from "../common.mjs";
import { terrain, flatten, downhill, planTrails, easeTrails, veinOres, exposeOres, streams, autoPonds, caves, outcrops, stampTrails, placeHouses, placeProps, props, entranceSpec, reserveEntrance, sculkZone, FACE } from "../wild.mjs";

const W = 330, D = 330, V = new Vox(W, D, 160), SEED = 23;
const HUB = { x: 172, z: 178 };
const SC = { x: 88, z: 96, r: 32, y: 72 };
const E = entranceSpec(SC.x, SC.z, SC.y, [-1, 0]);      // stairs descend westwards (approach from the east / hub side)

const CU = { block: "waxed_copper_block", cut: "waxed_cut_copper", cutSt: "waxed_cut_copper_stairs", exp: "waxed_exposed_cut_copper", expSt: "waxed_exposed_cut_copper_stairs", wea: "waxed_weathered_cut_copper", weaSt: "waxed_weathered_cut_copper_stairs", oxi: "waxed_oxidized_cut_copper", grate: "waxed_copper_grate", bulb: "waxed_copper_bulb[lit=true,powered=false]" };
const H = terrain(W, D, {
  R: 136, seed: SEED, hills: 16, base: 4,
  bumps: [{ x: 230, z: 104, r: 56, h: 30, step: 5 }, { x: 110, z: 214, r: 46, h: 22, step: 4 }, { x: 250, z: 238, r: 36, h: 18, step: 4 }, { x: 160, z: 100, r: 30, h: 14, step: 5 }, { x: 60, z: 160, r: 26, h: 12, step: 4 }],
});
flatten(H, W, D, { x: SC.x, z: SC.z, r: 16, y: SC.y, blend: 14 });
flatten(H, W, D, { x: HUB.x, z: HUB.z, r: 7, y: Math.round(H[HUB.z * W + HUB.x]), blend: 8 });
const lines = planTrails(H, W, D, {
  seed: SEED, hub: HUB, loop: { rx: 84, rz: 80 }, spokes: [-60, 60, 160, 250], r: 1.4,
  targets: [{ x: E.approach[0], z: E.approach[1], kind: "entrance", keep: true }, { x: 300, z: 180 }, { x: 170, z: 306 }, { x: 40, z: 240 }],
});
const corridor = easeTrails(H, W, D, lines);
const coast = paintTerrain(V, H, (x, z, h, c) => {
  if (h <= SEA) return { top: hash(x, z, 1) < 0.35 ? "gravel" : "sand", sub: "sand", deep: "stone" };
  if (c <= 2 && h <= SEA + 2) return { top: hash(x, z, 6) < 0.4 ? "gravel" : "sand", sub: "gravel", deep: "stone" };
  const sl = Math.max(Math.abs(H[z * W + x + 1] - H[z * W + x - 1]), Math.abs(H[(z + 1) * W + x] - H[(z - 1) * W + x]));
  if (sl >= 2.8) return { top: pick(["stone", "andesite", "tuff", "stone", "cobblestone", "gravel"], hash(x, z, 4)), sub: pick(["stone", "andesite", "tuff"], hash(x, z, 8)), deep: "stone" };
  const n = fbm(x / 22, z / 22, 3, 3), r = hash(x, z, 7);
  if (n > 0.56) return { top: pick(["gravel", "andesite", "coarse_dirt", "stone", "tuff", "gravel"], r), sub: "stone", deep: "stone" };
  if (n > 0.48) return { top: r < 0.35 ? "coarse_dirt" : r < 0.45 ? "gravel" : "grass_block", sub: "dirt", deep: "stone" };
  return { top: r < 0.06 ? "coarse_dirt" : "grass_block", sub: "dirt", deep: hash3(x, 0, z, 9) < 0.25 ? "tuff" : "stone" };
});
const ORE = [["coal_ore", 12, 40, 150], ["iron_ore", 11, 40, 130], ["copper_ore", 10, 40, 130], ["gold_ore", 2, 40, 66], ["redstone_ore", 2.4, 40, 60], ["lapis_ore", 1.5, 40, 62], ["diamond_ore", 0.5, 40, 50], ["emerald_ore", 0.5, 76, 150]];
const SURF_ORE = [["coal_ore", 10], ["iron_ore", 9], ["copper_ore", 9], ["gold_ore", 1.4], ["redstone_ore", 1], ["lapis_ore", 0.7]];
console.log("ore blocks", veinOres(V, SEED, ORE, 0.02));
exposeOres(V, SEED + 1, SURF_ORE, 0.15);
reserveEntrance(V, E);
V.mark(HUB.x - 8, HUB.z - 8, HUB.x + 8, HUB.z + 8);

// ------------------------------------------------------------------ water, caves, rock
streams(V, [{ ctrl: downhill(V, 222, 112, 4), r: 1.5 }, { ctrl: downhill(V, 112, 206, 5), r: 1.4 }, { ctrl: downhill(V, 250, 232, 6), r: 1.3 }], SEED);
console.log("ponds", autoPonds(V, 3, SEED + 2, corridor, (x, z) => Math.hypot(x - SC.x, z - SC.z) < SC.r * 1.5 || Math.hypot(x - HUB.x, z - HUB.z) < 20, ["gravel", "clay", "sand"]).length);
const CAVE_ORE = [["coal_ore", 6], ["iron_ore", 6], ["copper_ore", 6], ["gold_ore", 1.6], ["redstone_ore", 1.6], ["lapis_ore", 1], ["diamond_ore", 0.5]];
console.log("caves", caves(V, 7, SEED + 3, corridor, CAVE_ORE, { post: "polished_blackstone_wall", lamp: CU.bulb, light: "torch" }).length);
const avoidSc = (x, z) => Math.hypot(x - SC.x, z - SC.z) < SC.r * 1.4;
outcrops(V, 48, SEED + 4, corridor, ["stone", "andesite", "tuff", "cobblestone", "stone", "gravel"], [["coal_ore", 8], ["iron_ore", 8], ["copper_ore", 9], ["gold_ore", 1.2], ["redstone_ore", 1]], { avoid: avoidSc, ore: 0.3, rvar: 3 });

// ------------------------------------------------------------------ trails
const R = new Roads(V);
const gravelMix = (x, z, t, d) => d > 1.2 && hash(x, z, 40) < 0.5 ? pick(["gravel", "andesite", "coarse_dirt"], hash(x, z, 41)) : pick(["dirt_path", "dirt_path", "gravel", "dirt_path", "coarse_dirt", "packed_mud"], hash(x, z, 42));
console.log("bridges", stampTrails(V, R, lines, gravelMix, { deck: "spruce_planks", rail: "iron_bars", sea: "spruce_planks" }));

const lamp = (x, z, y) => { V.set(x, y + 1, z, "polished_blackstone_wall"); V.set(x, y + 2, z, "polished_blackstone_wall"); V.set(x, y + 3, z, "chain[axis=y]"); V.set(x, y + 4, z, CU.bulb); V.set(x, y + 5, z, "waxed_cut_copper_slab[type=bottom,waterlogged=false]"); };
// hub: crossroads with a hand pump (cauldron + lever), signpost, bench, lamps
{
  const y = V.h(HUB.x, HUB.z);
  for (let dz = -5; dz <= 5; dz++) for (let dx = -5; dx <= 5; dx++) if (Math.hypot(dx, dz) <= 5.2) { const x = HUB.x + dx, z = HUB.z + dz; V.set(x, y, z, pick(["gravel", "andesite", "polished_andesite", "gravel", "tuff"], hash(x, z, 50))); for (let k = 1; k <= 6; k++) V.set(x, y + k, z, 0); V.surf[z * W + x] = y; R.m[z * W + x] = 1; }
  for (let dz = -1; dz <= 1; dz++) for (let dx = -1; dx <= 1; dx++) V.set(HUB.x + dx, y, HUB.z + dz, "polished_blackstone_bricks");
  V.set(HUB.x, y + 1, HUB.z, "water_cauldron[level=3]"); V.set(HUB.x, y + 2, HUB.z + 1, CU.grate);
  for (let k = 1; k <= 3; k++) V.set(HUB.x, y + k, HUB.z + 1, k < 3 ? CU.cut : CU.grate);
  V.set(HUB.x, y + 3, HUB.z, "waxed_cut_copper_stairs[facing=south,half=top,shape=straight,waterlogged=false]");
  props.sign(V, HUB.x + 4, HUB.z - 4, "south", y, "polished_blackstone_wall", "dark_oak_sign");
  props.bench(V, HUB.x - 4, HUB.z + 4, "north", y, "polished_blackstone_brick_stairs");
  lamp(HUB.x + 4, HUB.z + 4, y); lamp(HUB.x - 4, HUB.z - 4, y);
  V.mark(HUB.x - 6, HUB.z - 6, HUB.x + 6, HUB.z + 6);
}

// ------------------------------------------------------------------ lone workshop sheds
const SHED = [
  { base: "polished_blackstone_bricks", frame: "polished_basalt", fill: "bricks", fill2: "bricks", roofSt: CU.expSt, roofFull: CU.exp, floor: "spruce_planks", chimney: "bricks", apron: "gravel", door: "spruce_door", lamp: false, furn: ["blast_furnace[facing=north,lit=false]", "barrel[facing=up,open=false]", "anvil[facing=north]", "crafting_table", "smithing_table"] },
  { base: "polished_blackstone_bricks", frame: "stripped_dark_oak_log", fill: "mud_bricks", fill2: "bricks", roofSt: CU.weaSt, roofFull: CU.wea, floor: "dark_oak_planks", chimney: "deepslate_bricks", apron: "andesite", door: "spruce_door", lamp: false, furn: ["furnace[facing=north,lit=false]", "barrel[facing=up,open=false]", "grindstone[face=floor,facing=north]", "stonecutter[facing=north]"] },
  { base: "deepslate_bricks", frame: "polished_basalt", fill: "deepslate_bricks", fill2: "bricks", roofSt: "waxed_oxidized_cut_copper_stairs", roofFull: CU.oxi, floor: "spruce_planks", chimney: "bricks", apron: "gravel", door: "spruce_door", lamp: false, furn: ["blast_furnace[facing=north,lit=false]", "chest[facing=north,type=single,waterlogged=false]", "crafting_table", "cauldron"] },
  { base: "polished_blackstone_bricks", frame: "dark_oak_log", fill: "bricks", fill2: "waxed_cut_copper", roofSt: "blackstone_stairs", roofFull: "blackstone", floor: "spruce_planks", chimney: "bricks", apron: "gravel", door: "spruce_door", lamp: false, furn: ["smoker[facing=north,lit=false]", "barrel[facing=up,open=false]", "anvil[facing=north]", "crafting_table"] },
];
const HOUSES = placeHouses(V, R, lines, {
  seed: SEED, max: 10, spacing: 36, slope: 4, sizes: [[7, 7], [8, 7], [7, 8], [9, 7]],
  avoid: (x, z) => Math.hypot(x - SC.x, z - SC.z) < SC.r * 1.6 || Math.hypot(x - HUB.x, z - HUB.z) < 22,
  path: (x, z) => pick(["gravel", "andesite", "gravel"], hash(x, z, 8)),
  build: (x0, z0, x1, z1, face, r) => {
    const hb = house(V, x0, z0, x1, z1, face, { ...SHED[Math.floor(r * 997) % SHED.length], floors: 1 });
    const [fx, fz] = DIR[face];
    // copper lamp by the door, a pipe from the wall into the ground, a coal / ore pile
    const lx = hb.door[0] + fx + (fz ? 2 : 0), lz = hb.door[1] + fz + (fx ? 2 : 0); V.set(lx, hb.y + 3, lz, CU.bulb);
    const sx = face === "north" || face === "south" ? x1 + 1 : x0 + 2, sz = face === "north" || face === "south" ? z0 + 2 : z1 + 1;
    for (let k = 1; k <= 3; k++) V.set(sx, hb.y + k, sz, k === 3 ? CU.grate : CU.cut);
    const px = face === "north" || face === "south" ? x0 - 2 : x1 - 1, pz = face === "north" || face === "south" ? z1 - 1 : z0 - 2, g = V.h(px, pz);
    if (g > SEA) { V.set(px, g + 1, pz, "coal_block"); V.set(px + 1, g + 1, pz, "barrel[facing=up,open=false]"); V.mark(px, pz, px + 1, pz); }
    return hb;
  },
});
console.log("sheds", HOUSES.length);

// ------------------------------------------------------------------ wind turbines (lone, on hill shoulders beside trails)
function turbine(cx, cz) {
  const y = V.h(cx, cz), Hh = 24;
  foundation(V, cx - 2, cz - 2, cx + 2, cz + 2, y, "polished_blackstone_bricks", y + 3);
  for (let k = 1; k <= Hh; k++) for (const [dx, dz] of [[-1, -1], [1, -1], [-1, 1], [1, 1]]) V.set(cx + dx, y + k, cz + dz, k % 4 === 0 ? CU.cut : "iron_bars");
  for (let k = 2; k <= Hh; k += 4) for (const [dx, dz] of [[0, -1], [0, 1], [-1, 0], [1, 0]]) V.set(cx + dx, y + k, cz + dz, "iron_bars");
  for (let k = 1; k <= Hh; k++) V.set(cx, y + k, cz, k % 2 ? "chain[axis=y]" : "ladder[facing=north]");
  const hy = y + Hh + 2;
  for (let dz = -1; dz <= 3; dz++) for (let dx = -1; dx <= 1; dx++) for (let k = -1; k <= 1; k++) V.set(cx + dx, hy + k, cz + dz, Math.abs(dx) + Math.abs(k) === 2 ? 0 : dz === 3 ? CU.block : CU.exp);
  V.set(cx, hy + 2, cz - 1, "lightning_rod[facing=up,powered=false,waterlogged=false]");
  const rz = cz + 5; V.set(cx, hy, rz - 1, CU.cut); V.set(cx, hy, rz, "iron_block");
  for (let b = 0; b < 3; b++) { const a = b * Math.PI * 2 / 3 + 0.3; for (let r = 1; r <= 9; r++) V.set(cx + Math.round(Math.cos(a) * r), hy + Math.round(Math.sin(a) * r), rz, r > 2 ? (r > 6 ? "smooth_stone_slab[type=bottom,waterlogged=false]" : "waxed_cut_copper_slab[type=bottom,waterlogged=false]") : "iron_bars"); }
  V.mark(cx - 3, cz - 3, cx + 3, cz + 6);
}
const TURB = [];
for (const l of lines) for (let i = 10; i < l.pts.length - 1 && TURB.length < 2; i += 5) {
  const [px, pz] = l.pts[i], [qx, qz] = l.pts[i + 1] ?? l.pts[i - 1], L = Math.hypot(qx - px, qz - pz) || 1, s = hash(i, l.pts.length, 3) < 0.5 ? 1 : -1;
  const x = Math.round(px - (qz - pz) / L * 7 * s), z = Math.round(pz + (qx - px) / L * 7 * s), h = V.h(x, z);
  if (h < SEA + 8 || avoidSc(x, z) || TURB.some(t => Math.hypot(t.x - x, t.z - z) < 90) || HOUSES.some(o => Math.hypot(o.x - x, o.z - z) < 20)) continue;
  let ok = true; for (let dz = -3; dz <= 6 && ok; dz++) for (let dx = -3; dx <= 3; dx++) if (V.occupied(x + dx, z + dz) || R.on(x + dx, z + dz) || Math.abs(V.h(x + dx, z + dz) - h) > 3) { ok = false; break; }
  if (!ok) continue;
  turbine(x, z); TURB.push({ x, z });
}
console.log("turbines", TURB);

// ------------------------------------------------------------------ rusty rail remnants + pipe runs beside the trails
function straightBeside(l, i, off, len) {
  const [px, pz] = l.pts[i], [qx, qz] = l.pts[Math.min(l.pts.length - 1, i + 3)], tx = qx - px, tz = qz - pz;
  const ax = Math.abs(tx) > Math.abs(tz) ? [Math.sign(tx), 0] : [0, Math.sign(tz)], n = [-ax[1], ax[0]];
  const cells = []; for (let k = 0; k < len; k++) cells.push([Math.round(px + n[0] * off + ax[0] * k), Math.round(pz + n[1] * off + ax[1] * k)]);
  return { cells, ax };
}
let rails = 0, pipes = 0;
for (const l of lines) for (let i = 20; i < l.pts.length - 20; i += 37) {
  const kind = hash(i, l.pts.length, 11) < 0.5 ? "rail" : "pipe";
  if (kind === "rail" ? rails >= 4 : pipes >= 4) continue;
  const len = 10 + Math.floor(hash(i, 2, 11) * 10), { cells, ax } = straightBeside(l, i, l.r + 2.5, len);
  if (cells.some(([x, z]) => V.occupied(x, z) || R.on(x, z) || V.h(x, z) <= SEA + 1 || avoidSc(x, z))) continue;
  const shape = ax[0] ? "east_west" : "north_south";
  if (kind === "rail") {
    cells.forEach(([x, z], k) => {
      const g = V.h(x, z); V.set(x, g, z, "gravel");
      if (hash(x, z, 12) < 0.75) V.set(x, g + 1, z, B("rail", { shape, waterlogged: "false" }));
      if (k % 3 === 0) { const sx = x + (ax[1] ? 1 : 0), sz = z + (ax[0] ? 1 : 0); if (!R.on(sx, sz) && !V.occupied(sx, sz)) V.set(sx, V.h(sx, sz), sz, "stripped_spruce_log[axis=" + (ax[0] ? "z" : "x") + "]"); }
      V.mark(x, z, x, z);
    });
    const [ex, ez] = cells[cells.length - 1], g = V.h(ex, ez); V.set(ex, g + 1, ez, "barrel[facing=up,open=false]"); V.set(ex + ax[0], V.h(ex + ax[0], ez + ax[1]) + 1, ez + ax[1], "iron_ore");
    rails++;
  } else {
    const y = Math.max(...cells.map(([x, z]) => V.h(x, z))) + 3;
    cells.forEach(([x, z], k) => {
      V.set(x, y, z, k % 6 === 3 ? CU.grate : (k > len * 0.6 ? CU.wea : CU.cut));
      if (k % 5 === 0) for (let yy = V.h(x, z) + 1; yy < y; yy++) V.set(x, yy, z, "iron_bars");
      V.mark(x, z, x, z);
    });
    const [ex, ez] = cells[cells.length - 1]; for (let yy = V.h(ex, ez) + 1; yy < y; yy++) V.set(ex, yy, ez, CU.cut);
    const [sx, sz] = cells[0]; for (let yy = V.h(sx, sz) + 1; yy < y; yy++) V.set(sx, yy, sz, CU.exp); V.set(sx, y + 1, sz, "lever[face=floor,facing=north,powered=false]");
    pipes++;
  }
}
console.log("rail remnants", rails, "pipes", pipes);

// ------------------------------------------------------------------ trail props
const P = placeProps(V, R, lines, {
  seed: SEED, every: 26, spacing: 16,
  avoid: (x, z) => Math.hypot(x - SC.x, z - SC.z) < SC.r || HOUSES.some(o => Math.hypot(o.x - x, o.z - z) < 9),
  kinds: [
    { name: "lamp", w: 5, build: (x, z, f, y) => lamp(x, z, y) },
    { name: "bench", w: 2, cap: 7, build: (x, z, f, y) => props.bench(V, x, z, f, y, "polished_blackstone_brick_stairs", "waxed_cut_copper_slab[type=top,waterlogged=false]") },
    { name: "sign", w: 1.5, cap: 6, build: (x, z, f, y) => props.sign(V, x, z, f, y, "polished_blackstone_wall", "dark_oak_sign") },
    { name: "campfire", w: 1, cap: 3, need: 2, off: 2, build: (x, z, f, y) => props.campfire(V, x, z, y, "polished_blackstone_brick_slab[type=bottom,waterlogged=false]", "gravel") },
    { name: "orecart", w: 1, cap: 4, build: (x, z, f, y) => { V.set(x, y + 1, z, "barrel[facing=up,open=false]"); const [dx, dz] = DIR[f]; V.set(x - dz, y + 1, z + dx, pick(["coal_block", "raw_iron_block", "raw_copper_block"], hash(x, z, 13))); V.set(x + dz, y + 1, z - dx, "chain[axis=y]"); } },
    { name: "pump", w: 0.6, cap: 2, build: (x, z, f, y) => { V.set(x, y + 1, z, "water_cauldron[level=3]"); for (let k = 1; k <= 3; k++) V.set(x + DIR[f][1], y + k, z - DIR[f][0], k === 3 ? CU.grate : CU.cut); } },
  ],
});
console.log("props", JSON.stringify(P.count));

// ------------------------------------------------------------------ sparse scrub: spruce stands, dark oak, dead trees, ferns
for (let z = 2; z < D - 2; z++) for (let x = 2; x < W - 2; x++) {
  const h = V.h(x, z);
  if (h <= SEA + 1 || V.occupied(x, z) || R.on(x, z, 2) || coast(x, z) <= 3 || Math.hypot(x - SC.x, z - SC.z) < SC.r * 1.05) continue;
  const top = V.name(x, h, z); if (!/grass_block|coarse_dirt|podzol/.test(top)) continue;
  const n = fbm(x / 34, z / 34, 3, 31), r = hash(x, z, 300);
  const p = n > 0.6 ? 0.05 : n > 0.5 ? 0.014 : 0.003;
  if (r < p && ![[2, 0], [-2, 0], [0, 2], [0, -2]].some(([dx, dz]) => V.occupied(x + dx, z + dz) || R.on(x + dx, z + dz))) {
    tree(V, x, z, hash(x, z, 305) < 0.12 ? "dead" : pick(["spruce", "spruce", "dark", "birch", "spruce"], hash(x, z, 301)), 0.5 + hash(x, z, 302)); V.mark(x - 1, z - 1, x + 1, z + 1);
  } else if (r < p * 2) bush(V, x, z, "spruce_leaves");
}
for (let z = 0; z < D; z++) for (let x = 0; x < W; x++) {
  if (R.on(x, z) || V.occupied(x, z)) continue;
  groundCover(V, x, z, 0.22, ["dandelion", "poppy", "azure_bluet", "oxeye_daisy"], 0.018);
  const y = V.h(x, z), n = V.name(x, y, z);
  if (/coarse_dirt|gravel/.test(n) && V.air(x, y + 1, z) && hash(x, z, 610) < 0.05) V.set(x, y + 1, z, pick(["dead_bush", "fern", "brown_mushroom"], hash(x, z, 611)));
}

// ------------------------------------------------------------------ sculk zone + boss entrance
const marker = sculkZone(V, E, SC.r, 29);
console.log("entrance marker", marker);

output(V, "ne", { x: HUB.x, y: V.h(HUB.x, HUB.z + 3) + 1, z: HUB.z + 3, yaw: 180 }, [
  { name: "sculk", x: SC.x, z: SC.z, r: 26, a: 6 },
  { name: "entrance", x: E.x, z: E.z, z1: E.z, r: 12, a: 12 },
  { name: "hub", x: HUB.x, z: HUB.z, r: 40, a: 5 },
  ...(TURB[0] ? [{ name: "turbine", x: TURB[0].x, z: TURB[0].z, r: 20, a: 6 }] : []),
  ...(HOUSES[0] ? [{ name: "shed", x: HOUSES[0].x, z: HOUSES[0].z, r: 20, a: 7 }] : []),
], marker);
