import { Ctx } from "./world.mjs";
import { st, sti, lant, AIR, pick, logY, gable, floorFill, lampPost } from "./bcommon.mjs";

// ---------------------------------------------------------------- MARKET HOUSE (door faces west)
export function shop(w, cx, cz) {
  const c = new Ctx(w, cx, cz, "W"), HW = 10, HD = 8;
  floorFill(c, (x, z) => Math.abs(x) <= HW + 3 && Math.abs(z) <= HD + 5, 0, (x, z) => pick(x, z, 13, "stone_bricks", "cobblestone", "polished_andesite", "mossy_stone_bricks", "dirt_path"), 14);
  floorFill(c, (x, z) => Math.abs(x) <= HW && z <= -1 && z >= -HD, 0, (x, z) => pick(x, z, 14, "spruce_planks", "oak_planks"), 12);
  const wallB = (x, y) => y === 1 ? "stone_bricks" : "white_terracotta";
  for (let y = 1; y <= 5; y++) {
    for (let x = -HW; x <= HW; x++) { c.s(x, y, -HD, wallB(x, y)); c.s(x, y, -1, wallB(x, y)); }
    for (let z = -HD; z <= -1; z++) { c.s(-HW, y, z, wallB(-HW, y)); c.s(HW, y, z, wallB(HW, y)); }
  }
  for (const x of [-HW, -5, 0, 5, HW]) for (let y = 1; y <= 5; y++) { c.s(x, y, -HD, logY("stripped_oak_log")); c.s(x, y, -1, logY("stripped_oak_log")); }
  for (const z of [-HD, -4, -1]) for (let y = 1; y <= 5; y++) { c.s(-HW, y, z, logY("stripped_oak_log")); c.s(HW, y, z, logY("stripped_oak_log")); }
  for (let x = -HW; x <= HW; x++) for (const z of [-HD, -1]) c.s(x, 5, z, ["stripped_oak_log", { axis: "x" }]);
  for (let z = -HD; z <= -1; z++) for (const x of [-HW, HW]) c.s(x, 5, z, ["stripped_oak_log", { axis: "z" }]);
  for (const x of [-7, -3, 3, 7]) for (let y = 3; y <= 4; y++) c.s(x, y, -HD, "yellow_stained_glass");
  for (const z of [-6, -3]) for (let y = 3; y <= 4; y++) { c.s(-HW, y, z, "yellow_stained_glass"); c.s(HW, y, z, "yellow_stained_glass"); }
  for (let y = 1; y <= 3; y++) for (let x = -1; x <= 1; x++) c.s(x, y, -1, AIR);
  for (let x = -2; x <= 2; x++) c.s(x, 4, -1, x === 0 ? "chiseled_stone_bricks" : "stone_bricks");
  gable(c, -HW - 1, HW + 1, -HD - 1, 0, 6, "brick_stairs", "bricks");
  for (const x of [-HW, HW]) for (let z = -HD; z <= -1; z++) { const d = Math.min(z + HD, -1 - z), h = 6 + Math.floor(d / 2); for (let y = 6; y < h; y++) c.s(x, y, z, "white_terracotta"); }
  // house interior: shelves, goods, rug, hanging lanterns
  for (let x = -9; x <= 9; x++) { if (Math.abs(x) <= 1) continue; c.s(x, 1, -7, "barrel"); c.s(x, 2, -7, ["spruce_slab", { type: "bottom" }]); c.s(x, 3, -7, x % 2 ? "chiseled_bookshelf" : "barrel"); }
  c.f(-9, 1, -5, -8, 1, -3, "hay_block"); c.s(-9, 2, -5, "red_carpet"); c.s(9, 1, -5, "barrel"); c.s(9, 2, -5, "barrel"); c.s(8, 1, -5, "composter");
  for (let x = -3; x <= 3; x++) c.s(x, 0, -4, x % 2 ? "red_wool" : "white_wool");
  for (const x of [-4, 0, 4]) { c.s(x, 5, -4, "chain"); c.s(x, 4, -4, lant(true)); }
  c.s(0, 1, -6, "emerald_block"); c.s(-1, 1, -6, "barrel"); c.s(1, 1, -6, "barrel");
  // striped awnings over the stalls
  const awn = x => (x + HW) % 2 === 0 ? ["red_wool", "blue_wool", "orange_wool"][Math.floor((x + HW) / 7) % 3] : "white_wool";
  for (let x = -HW; x <= HW; x++) for (let z = 0; z <= 6; z++) c.s(x, z === 0 ? 5 : 4, z, awn(x));
  for (let x = -HW; x <= HW; x++) c.s(x, 4, 7, awn(x));
  for (let x = -HW; x <= HW; x += 2) c.s(x, 3, 7, [["red", "blue", "yellow", "lime", "orange"][((x + HW) / 2) % 5] + "_wall_banner", { facing: "south" }]);
  for (const x of [-HW, -5, 0, 5, HW]) for (let y = 1; y <= 3; y++) c.s(x, y, 7, "oak_fence");
  for (const x of [-HW, HW]) for (let z = 1; z <= 6; z++) c.s(x, 1, z, "oak_fence");
  // stalls
  for (const sx of [-6, 0, 6]) for (let x = sx - 2; x <= sx + 2; x++) {
    if (sx === 0 && Math.abs(x) <= 1) { if (x === 0) c.s(x, 1, 3, ["oak_slab", { type: "top" }]); continue; }
    c.s(x, 1, 3, x === sx ? "barrel" : "oak_slab");
  }
  c.s(-1, 1, 3, "barrel"); c.s(1, 1, 3, "barrel");
  for (const sx of [-6, 6]) { c.s(sx - 2, 1, 5, "barrel"); c.s(sx - 2, 2, 5, "barrel"); c.s(sx + 2, 1, 5, "hay_block"); c.s(sx + 2, 2, 5, "melon"); c.s(sx, 1, 5, "composter"); }
  for (const x of [-8, -4, 0, 4, 8]) c.s(x, 3, 3, lant(true));
  const goods = ["APPLE", "BREAD", "EMERALD", "DIAMOND", "CARROT", "GOLDEN_APPLE", "COOKED_BEEF", "POTION"];
  [-9, -7, -5, -3, 3, 5, 7, 9].forEach((x, i) => { c.frame(x, 3, 0, "south", goods[i]); c.frame(x, 2, 0, "south", goods[(i + 3) % goods.length]); });
  for (const x of [-HW - 1, HW + 1]) { c.s(x, 1, 0, "oak_fence"); c.s(x, 2, 0, lant(false)); }
  for (const z of [3, 6, 9]) lampPost(w, ...c.pos(HW + 3, z), 3);
  c.s(HW + 2, 1, 4, "barrel"); c.s(HW + 2, 2, 4, "hay_block"); c.s(-HW - 2, 1, 3, "hay_block"); c.s(-HW - 2, 1, 5, "barrel");
  c.warp("warp_shop", "상점", 0, 1, HD + 5, "s");
  c.npc("merchant", 0, 1, 2, "s");
  c.s(0, 0, HD + 5, "gold_block");
  for (const [x, z] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) c.s(x, 0, HD + 5 + z, "polished_andesite");
  for (const [x, z] of [[1, 1], [-1, 1], [1, -1], [-1, -1]]) c.s(x, 0, HD + 5 + z, "red_terracotta");
}

// ---------------------------------------------------------------- ARMORY / BARRACKS (gate faces north)
export function armory(w, cx, cz) {
  const c = new Ctx(w, cx, cz, "N"), HW = 12, HD = 9;
  const gear = (m, hand) => ({ head: m + "_HELMET", chest: m + "_CHESTPLATE", legs: m + "_LEGGINGS", feet: m + "_BOOTS", hand });
  floorFill(c, (x, z) => Math.abs(x) <= HW + 2 && Math.abs(z) <= HD + 5, 0, (x, z) => pick(x, z, 15, "stone_bricks", "polished_andesite", "cobblestone", "gravel"), 16);
  floorFill(c, (x, z) => Math.abs(x) <= HW && Math.abs(z) <= HD, 0, (x, z) => z < 0 ? pick(x, z, 16, "polished_andesite", "stone_bricks", "smooth_stone") : pick(x, z, 16, "coarse_dirt", "dirt_path", "gravel", "coarse_dirt"), 14);
  const wallB = (x, y, z) => y === 1 ? "polished_andesite" : pick(x + y, z, 17, "stone_bricks", "stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks");
  for (let y = 1; y <= 7; y++) {
    for (let x = -HW; x <= HW; x++) { c.s(x, y, -HD, wallB(x, y, -HD)); c.s(x, y, HD, wallB(x, y, HD)); }
    for (let z = -HD; z <= HD; z++) { c.s(-HW, y, z, wallB(-HW, y, z)); c.s(HW, y, z, wallB(HW, y, z)); }
  }
  for (let x = -HW; x <= HW; x += 2) for (const z of [-HD, HD]) { c.s(x, 8, z, "stone_bricks"); c.s(x, 9, z, "stone_brick_slab"); }
  for (let z = -HD; z <= HD; z += 2) for (const x of [-HW, HW]) { c.s(x, 8, z, "stone_bricks"); c.s(x, 9, z, "stone_brick_slab"); }
  // corner towers with banners
  for (const [tx, tz] of [[-HW, -HD], [HW, -HD], [-HW, HD], [HW, HD]]) {
    for (let y = 1; y <= 11; y++) for (let dx = -2; dx <= 2; dx++) for (let dz = -2; dz <= 2; dz++) {
      if (Math.abs(dx) === 2 && Math.abs(dz) === 2 && y > 1) continue;
      if (Math.abs(dx) < 2 && Math.abs(dz) < 2 && y < 11) { c.s(tx + dx, y, tz + dz, y === 1 ? "stone_bricks" : AIR); continue; }
      c.s(tx + dx, y, tz + dz, wallB(tx + dx, y, tz + dz));
    }
    for (let dx = -2; dx <= 2; dx++) for (let dz = -2; dz <= 2; dz++) {
      if (Math.abs(dx) === 2 && Math.abs(dz) === 2) continue;
      c.s(tx + dx, 11, tz + dz, "stone_bricks");
      if ((Math.abs(dx) === 2 || Math.abs(dz) === 2) && (dx + dz) % 2 === 0) c.s(tx + dx, 12, tz + dz, "stone_bricks");
    }
    for (let y = 12; y <= 14; y++) c.s(tx, y, tz, "oak_fence");
    c.s(tx, 15, tz, [(tx > 0 ? "blue" : "red") + "_banner", { rotation: "0" }]);
    for (let y = 4; y <= 5; y++) c.s(tx, y, tz + (tz > 0 ? 2 : -2), "iron_bars");
  }
  // gate with portcullis bars
  for (let x = -2; x <= 2; x++) for (let y = 1; y <= 5; y++) c.s(x, y, HD, y === 5 ? "iron_bars" : AIR);
  for (let x = -3; x <= 3; x++) c.s(x, 6, HD, "chiseled_stone_bricks");
  for (const x of [-3, 3]) for (let y = 1; y <= 5; y++) c.s(x, y, HD, "chiseled_stone_bricks");
  for (const x of [-4, 4]) { c.s(x, 1, HD + 1, "stone_brick_wall"); c.s(x, 2, HD + 1, "stone_brick_wall"); c.s(x, 3, HD + 1, lant(false)); }
  for (const x of [-5, 5]) { c.s(x, 4, HD + 1, ["red_wall_banner", { facing: "south" }]); c.s(x, 3, HD + 1, ["blue_wall_banner", { facing: "south" }]); }
  // barracks hall (back) with flat roof, yard in front
  for (let x = -HW + 1; x <= HW - 1; x++) for (let z = -HD + 1; z <= -1; z++) c.s(x, 6, z, "stone_bricks");
  for (let x = -HW + 1; x <= HW - 1; x++) for (let y = 1; y <= 5; y++) c.s(x, y, 0, Math.abs(x) <= 2 ? AIR : (y === 5 || y === 1 ? "polished_andesite" : "stone_bricks"));
  for (let x = -3; x <= 3; x++) c.s(x, 6, 0, "chiseled_stone_bricks");
  for (const x of [-3, 3]) for (let y = 1; y <= 5; y++) c.s(x, y, 0, "stone_brick_wall");
  for (let x = -HW + 1; x <= HW - 1; x++) c.s(x, 7, -1, x % 2 ? "stone_brick_slab" : "stone_bricks");
  for (const x of [-HW + 1, HW - 1]) for (let z = -HD + 1; z <= -1; z++) c.s(x, 7, z, z % 2 ? "stone_brick_slab" : "stone_bricks");
  const racks = ["IRON_SWORD", "DIAMOND_SWORD", "BOW", "CROSSBOW", "TRIDENT", "IRON_AXE", "SHIELD", "NETHERITE_SWORD", "STONE_SWORD", "GOLDEN_SWORD"];
  [-10, -8, -6, -4, -2, 2, 4, 6, 8, 10].forEach((x, i) => { c.frame(x, 3, -HD + 1, "south", racks[i]); c.s(x, 2, -HD + 1, "dark_oak_slab"); c.s(x, 1, -HD + 1, x % 4 === 0 ? "barrel" : "dark_oak_fence"); });
  for (const x of [-10, -8, -6, 6, 8, 10]) c.stand(x, 1, -HD + 2, "s", gear("IRON", x % 4 === 0 ? "IRON_SWORD" : "SHIELD"));
  c.stand(-3, 1, -HD + 2, "s", gear("DIAMOND", "DIAMOND_SWORD")); c.stand(3, 1, -HD + 2, "s", gear("NETHERITE", "NETHERITE_SWORD"));
  for (let x = -4; x <= 4; x++) c.s(x, 1, -4, x % 2 ? "dark_oak_slab" : "dark_oak_planks");
  c.s(-4, 2, -4, "dark_oak_fence"); c.s(4, 2, -4, lant(false)); c.s(0, 2, -4, "red_carpet");
  for (const x of [-9, -5, 5, 9]) { c.s(x, 1, -6, "hay_block"); c.s(x, 2, -6, "red_carpet"); }
  for (const x of [-8, 0, 8]) { c.s(x, 6, -3, "stone_bricks"); c.s(x, 5, -3, lant(true)); }
  c.s(-HW + 1, 1, -HD + 1, ["blast_furnace", { facing: "east", lit: "true" }]); c.s(HW - 1, 1, -HD + 1, ["blast_furnace", { facing: "west", lit: "true" }]);
  c.s(-HW + 1, 1, -2, ["anvil", { facing: "east" }]); c.s(HW - 1, 1, -2, ["grindstone", { face: "floor", facing: "west" }]);
  // yard: training dummies, racks, banners
  for (const [x, z] of [[-8, 4], [-5, 6], [5, 6], [8, 4]]) { c.s(x, 1, z, "hay_block"); c.s(x, 2, z, "oak_fence"); c.s(x, 3, z, "hay_block"); c.s(x, 4, z, "carved_pumpkin"); }
  for (const x of [-9, 9]) { c.s(x, 1, 2, "fletching_table"); c.s(x, 1, 1, "barrel"); }
  for (const x of [-HW + 1, HW - 1]) for (let z = 2; z <= 6; z += 4) c.s(x, 4, z, [(x < 0 ? "red" : "blue") + "_wall_banner", { facing: x < 0 ? "east" : "west" }]);
  for (const [x, z] of [[-6, 2], [6, 2], [-6, 7], [6, 7]]) { c.s(x, 1, z, "stone_brick_wall"); c.s(x, 2, z, "stone_brick_wall"); c.s(x, 3, z, lant(false)); }
  for (const x of [-7, 7]) lampPost(w, ...c.pos(x, HD + 3), 3);
  for (const x of [-9, 9]) { c.s(x, 1, HD + 3, "barrel"); c.s(x, 1, HD + 4, "hay_block"); }
  c.stand(-6, 1, HD + 2, "s", gear("IRON", "IRON_SWORD")); c.stand(6, 1, HD + 2, "s", gear("IRON", "SHIELD"));
  c.warp("warp_armory", "무기고", 0, 1, HD + 5, "s");
  c.npc("armory", 0, 1, 2, "s");
  c.s(0, 0, HD + 5, "iron_block");
  for (const [x, z] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) c.s(x, 0, HD + 5 + z, "polished_andesite");
  for (const [x, z] of [[1, 1], [-1, 1], [1, -1], [-1, -1]]) c.s(x, 0, HD + 5 + z, "blue_terracotta");
}
