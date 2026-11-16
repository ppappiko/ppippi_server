import { Ctx } from "./world.mjs";
import { st, sti, lant, AIR, pick, logY, gable, octa, ring, floorFill, lampPost } from "./bcommon.mjs";

// ---------------------------------------------------------------- ALCOHEMIST TOWER (door faces east)
export function alchemist(w, cx, cz) {
  const c = new Ctx(w, cx, cz, "E"), R = 8;
  const lowP = octa(8, 12), upP = (x, z) => Math.abs(x) <= 6 && Math.abs(z) <= 6;
  const stoneB = (x, y, z) => pick(x + y, z, 1, "stone_bricks", "stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks");
  floorFill(c, (x, z) => Math.abs(x) <= 12 && Math.abs(z) <= 11 && Math.abs(x) + Math.abs(z) <= 19, 0,
    (x, z) => pick(x, z, 2, "stone_bricks", "polished_andesite", "mossy_stone_bricks", "cobblestone"), 13);
  floorFill(c, lowP, 0, (x, z) => (Math.abs(x) + Math.abs(z)) % 4 === 0 ? "polished_deepslate" : pick(x, z, 3, "dark_oak_planks", "spruce_planks"), R);
  ring(c, lowP, 1, 6, (x, y, z) => y === 1 ? "polished_deepslate" : stoneB(x, y, z), R);
  for (const [px, pz] of [[8, 4], [8, -4], [-8, 4], [-8, -4], [4, 8], [-4, 8], [4, -8], [-4, -8], [6, 6], [-6, 6], [6, -6], [-6, -6]]) {
    if (!lowP(px, pz)) continue;
    for (let y = 1; y <= 6; y++) c.s(px, y, pz, y === 1 || y === 6 ? "chiseled_stone_bricks" : logY("stripped_dark_oak_log"));
  }
  for (const [wx, wz] of [[-8, 0], [-8, 2], [-8, -2], [0, -8], [2, -8], [-2, -8], [2, 8], [-2, 8], [8, 2], [8, -2]]) {
    if (!lowP(wx, wz)) continue; if (wz === 8 && Math.abs(wx) < 2) continue;
    for (let y = 3; y <= 4; y++) c.s(wx, y, wz, "purple_stained_glass");
  }
  // door arch on the +z (front) wall
  for (let x = -1; x <= 1; x++) for (let y = 1; y <= 4; y++) c.s(x, y, R, AIR);
  for (let y = 1; y <= 5; y++) { c.s(-2, y, R, y === 1 ? "chiseled_stone_bricks" : "stone_bricks"); c.s(2, y, R, y === 1 ? "chiseled_stone_bricks" : "stone_bricks"); }
  for (let x = -2; x <= 2; x++) c.s(x, 5, R, x === 0 ? "chiseled_stone_bricks" : "polished_deepslate");
  c.s(-2, 3, R + 1, lant(false)); c.s(2, 3, R + 1, lant(false));
  floorFill(c, lowP, 7, (x, z) => pick(x, z, 4, "dark_oak_planks", "dark_oak_planks", "spruce_planks"), R);
  // stair flight along the back wall to the upper floor
  for (let k = 0; k < 6; k++) { c.s(-5 + k, 1 + k, -6, st("dark_oak_stairs", "east")); for (let y = 1; y < 1 + k; y++) c.s(-5 + k, y, -6, "dark_oak_planks"); }
  for (let x = -5; x <= 1; x++) for (let z = -6; z <= -5; z++) c.s(x, 7, z, AIR);
  // upper storey
  for (let z = -6; z <= 6; z++) for (let x = -6; x <= 6; x++) {
    if (Math.abs(x) < 6 && Math.abs(z) < 6) continue;
    const corner = Math.abs(x) === 6 && Math.abs(z) === 6, e = Math.abs(x) === 6 ? z : x;
    for (let y = 8; y <= 17; y++) c.s(x, y, z, corner ? logY("stripped_dark_oak_log")
      : (e % 3 === 0 && (y >= 9 && y <= 11 || y >= 14 && y <= 16)) ? "purple_stained_glass" : y === 8 || y === 17 || y === 12 ? "dark_oak_planks" : pick(x, z + y, 5, "dark_oak_planks", "spruce_planks"));
  }
  for (let y = 8; y <= 10; y++) for (let x = -1; x <= 1; x++) c.s(x, y, 6, AIR);
  // balcony rail + lanterns on the lower roof ring
  for (let z = -8; z <= 8; z++) for (let x = -8; x <= 8; x++) {
    if (!lowP(x, z) || upP(x, z)) continue;
    if (!(lowP(x + 1, z) && lowP(x - 1, z) && lowP(x, z + 1) && lowP(x, z - 1))) { c.s(x, 8, z, "dark_oak_fence"); if ((x + z) % 4 === 0) c.s(x, 9, z, lant(false)); }
  }
  for (let z = -6; z <= 6; z++) for (let x = -6; x <= 6; x++) c.s(x, 18, z, "dark_oak_planks");
  // purple pointed roof
  for (let r = 7, y = 18; r >= 1; r--, y++) for (let z = -r; z <= r; z++) for (let x = -r; x <= r; x++) {
    if (Math.max(Math.abs(x), Math.abs(z)) !== r && r > 1) continue;
    const corner = Math.abs(x) === r && Math.abs(z) === r;
    if (r === 1 || corner) c.s(x, y, z, y % 2 ? "purpur_block" : "purpur_pillar");
    else if (Math.abs(x) === r) c.s(x, y, z, st("purpur_stairs", x > 0 ? "west" : "east"));
    else c.s(x, y, z, st("purpur_stairs", z > 0 ? "north" : "south"));
  }
  for (let y = 25; y <= 26; y++) c.s(0, y, 0, "purpur_pillar");
  c.s(0, 27, 0, "amethyst_block"); c.s(0, 28, 0, ["amethyst_cluster", { facing: "up" }]); c.s(0, 29, 0, "end_rod");
  for (const [x, z] of [[7, 7], [-7, 7], [7, -7], [-7, -7]]) { c.s(x, 18, z, "purpur_pillar"); c.s(x, 19, z, ["amethyst_cluster", { facing: "up" }]); }
  for (const [x, z] of [[0, 4], [0, -4], [4, 0], [-4, 0]]) c.s(x, 21, z, "purple_stained_glass");
  // lower hall
  for (let x = -3; x <= 7; x++) for (let y = 1; y <= 4; y++) if (lowP(x, -7)) c.s(x, y, -7, "bookshelf");
  for (let z = -5; z <= 5; z++) for (let y = 1; y <= 4; y++) if (Math.abs(z) >= 2) c.s(-7, y, z, "bookshelf");
  for (let z = 1; z <= 5; z++) { c.s(7, 1, z, "polished_andesite"); c.s(7, 2, z, "brewing_stand"); c.s(6, 1, z, z % 2 ? "cauldron" : ["water_cauldron", { level: "3" }]); }
  for (let x = -6; x <= -3; x++) { c.s(x, 1, 6, "barrel"); if (x % 2) c.s(x, 2, 6, "brewing_stand"); else c.s(x, 2, 6, "flower_pot"); }
  for (let z = 2; z <= 6; z++) c.s(-6, 1, z, ["spruce_slab", { type: "top" }]);
  c.s(-6, 2, 3, ["amethyst_cluster", { facing: "up" }]); c.s(-6, 2, 5, ["amethyst_cluster", { facing: "up" }]);
  for (let x = -2; x <= 2; x++) c.s(x, 1, 3, x === 0 ? "cauldron" : ["dark_oak_slab", { type: "top" }]);
  c.s(0, 1, 0, "brewing_stand"); c.s(2, 1, 0, "enchanting_table"); c.s(-2, 1, 0, "lectern");
  for (const x of [3, -3]) { c.s(x, 1, -2, "amethyst_block"); c.s(x, 2, -2, ["amethyst_cluster", { facing: "up" }]); }
  for (const [x, z] of [[0, -3], [4, 4], [-4, 3]]) { c.s(x, 6, z, "chain"); c.s(x, 5, z, lant(true)); }
  for (const [x, z] of [[0, 0], [5, -5], [-5, 5], [5, 5]]) c.s(x, 6, z, "end_rod");
  for (const [x, z] of [[3, 3], [-3, 3], [3, -3], [-3, -3]]) c.s(x, 0, z, "sea_lantern");
  // upper study
  for (let x = -5; x <= 5; x++) for (let y = 8; y <= 10; y++) if (!(x <= 1 && y >= 8 && x >= -5 && false)) c.s(x, y, -5, "bookshelf");
  for (let x = -5; x <= 1; x++) { c.s(x, 8, -5, "dark_oak_fence"); c.s(x, 9, -5, AIR); c.s(x, 10, -5, AIR); }
  for (let z = -3; z <= 5; z++) for (let y = 8; y <= 10; y++) c.s(-5, y, z, "bookshelf");
  c.s(0, 8, 0, "enchanting_table");
  for (const [x, z] of [[2, 2], [-2, 2], [2, -2], [-2, -2]]) { c.s(x, 8, z, "amethyst_block"); c.s(x, 9, z, ["amethyst_cluster", { facing: "up" }]); }
  c.s(4, 8, 3, "brewing_stand"); c.s(4, 8, 4, "cauldron"); c.s(-3, 8, 4, "lectern"); c.s(3, 8, -3, "cauldron"); c.s(-1, 8, 3, "brewing_stand");
  for (const z of [2, -2]) { c.s(0, 17, z, "chain"); c.s(0, 16, z, lant(true)); }
  for (const [x, z] of [[5, 5], [-4, -4], [5, -4]]) c.s(x, 12, z, "end_rod");
  // herb gardens either side
  for (const side of [-1, 1]) {
    for (let x = -10; x <= -5; x++) for (let k = 0; k <= 4; k++) {
      const z = side * (9 + k), edge = x === -10 || x === -5 || k === 0 || k === 4;
      if (!w.land(...c.pos(x, z))) continue;
      if (edge) { c.s(x, 1, z, "oak_fence"); continue; }
      c.s(x, 0, z, "farmland"); c.s(x, 1, z, pick(x, z, 7, "allium", "lily_of_the_valley", "red_tulip", "blue_orchid", "fern", "sweet_berry_bush", "cornflower", "azure_bluet"));
    }
    c.s(-7, 1, side * 11, "cauldron"); c.s(-8, 1, side * 11, lant(false));
  }
  for (const z of [-4, 4]) lampPost(w, ...c.pos(z, R + 3), 3);
  c.warp("warp_alchemist", "연금술사의 탑", 0, 1, R + 5, "s");
  c.npc("alchemist", 0, 1, 5, "s");
  c.s(0, 0, R + 5, "amethyst_block");
  for (const [x, z] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) c.s(x, 0, R + 5 + z, "polished_andesite");
  for (const [x, z] of [[1, 1], [-1, 1], [1, -1], [-1, -1]]) c.s(x, 0, R + 5 + z, "purpur_block");
}

// ---------------------------------------------------------------- BLACKSMITH FORGE (door faces east)
export function smith(w, cx, cz) {
  const c = new Ctx(w, cx, cz, "E"), HW = 10, HD = 8;
  floorFill(c, (x, z) => Math.abs(x) <= HW + 2 && Math.abs(z) <= HD + 4, 0, (x, z) => pick(x, z, 8, "cobblestone", "stone", "andesite", "gravel", "polished_andesite"), 14);
  floorFill(c, (x, z) => Math.abs(x) <= HW && Math.abs(z) <= HD, 0, (x, z) => z < 0 ? pick(x, z, 9, "deepslate_bricks", "polished_deepslate", "deepslate_tiles") : pick(x, z, 9, "stone_bricks", "cobblestone", "andesite"), 12);
  const wallB = (x, y, z) => y <= 1 ? "deepslate_bricks" : pick(x + y, z, 10, "cobblestone", "cobblestone", "stone_bricks", "mossy_cobblestone", "andesite");
  for (let y = 1; y <= 7; y++) {
    for (let x = -HW; x <= HW; x++) c.s(x, y, -HD, wallB(x, y, -HD));
    for (let z = -HD; z <= 2; z++) { c.s(-HW, y, z, wallB(-HW, y, z)); c.s(HW, y, z, z <= -1 ? wallB(HW, y, z) : AIR); }
    for (let z = 3; z <= HD; z++) c.s(-HW, y, z, y <= 2 ? wallB(-HW, y, z) : AIR);
  }
  for (const [x, z] of [[-HW, HD], [-HW, 3], [HW, HD], [HW, 3], [-5, HD], [5, HD], [0, HD], [HW, -1], [HW, -HD], [-HW, -HD]])
    for (let y = 1; y <= 7; y++) c.s(x, y, z, logY("stripped_dark_oak_log"));
  for (let x = -HW; x <= HW; x++) for (const z of [-HD, 0, HD]) c.s(x, 7, z, ["dark_oak_log", { axis: "x" }]);
  for (let z = -HD; z <= HD; z++) for (const x of [-HW, 0, HW]) c.s(x, 7, z, ["dark_oak_log", { axis: "z" }]);
  gable(c, -HW - 1, HW + 1, -HD - 1, HD + 1, 8, "deepslate_tile_stairs", "deepslate_tiles");
  for (const x of [-HW, HW]) for (let z = -HD; z <= HD; z++) { const d = Math.min(z + HD, HD - z), h = 8 + Math.floor(d / 2); for (let y = 8; y < h; y++) if (c.g(x, y, z) === "") c.s(x, y, z, "dark_oak_planks"); }
  // chimney mass behind the hearth
  for (let x = -3; x <= 3; x++) for (let y = 1; y <= 9; y++) for (let z = -HD + 1; z <= -HD + 2; z++) c.s(x, y, z, y <= 1 ? "deepslate_bricks" : pick(x, z + y, 11, "cobblestone", "stone_bricks", "bricks"));
  for (let y = 10; y <= 14; y++) for (let x = -1; x <= 1; x++) for (let z = -HD + 1; z <= -HD + 2; z++) c.s(x, y, z, pick(x + y, z, 12, "cobblestone", "stone_bricks", "bricks"));
  c.s(0, 15, -HD + 1, ["campfire", { lit: "true", signal_fire: "true", facing: "south" }]);
  // hearth: lava pool behind iron bars, campfire embers
  for (let x = -2; x <= 2; x++) { c.s(x, 0, -HD + 3, "deepslate_bricks"); c.s(x, 1, -HD + 3, Math.abs(x) === 2 ? "lava" : "magma_block"); c.s(x, 1, -HD + 4, "iron_bars"); c.s(x, 2, -HD + 4, "iron_bars"); }
  for (let x = -1; x <= 1; x++) c.s(x, 2, -HD + 3, ["campfire", { lit: "true", facing: "south" }]);
  // furnaces, blast furnaces and ore storage along the back wall
  for (const x of [-9, -8, -7, 7, 8, 9]) { c.s(x, 1, -HD + 1, [x % 2 ? "blast_furnace" : "furnace", { facing: "south", lit: "true" }]); c.s(x, 2, -HD + 1, ["furnace", { facing: "south", lit: "true" }]); }
  for (const x of [-6, -5, 5, 6]) { c.s(x, 1, -HD + 1, "barrel"); c.s(x, 2, -HD + 1, x < 0 ? "raw_iron_block" : "raw_copper_block"); c.s(x, 3, -HD + 1, x < 0 ? "coal_block" : "iron_block"); }
  // stations
  c.s(-6, 1, -3, ["anvil", { facing: "north" }]); c.s(6, 1, -3, ["anvil", { facing: "north" }]);
  c.s(-8, 1, -3, ["grindstone", { face: "floor", facing: "east" }]); c.s(8, 1, -3, "smithing_table");
  c.s(-9, 1, 2, "smithing_table"); c.s(-9, 1, 4, ["water_cauldron", { level: "3" }]); c.s(-9, 1, 6, "cauldron");
  for (const x of [-6, 6]) { c.s(x, 1, 5, "stone"); c.s(x, 2, 5, ["anvil", { facing: "north" }]); c.s(x, 1, 6, "barrel"); }
  for (let x = -2; x <= 2; x++) c.s(x, 1, 1, x === 0 ? ["grindstone", { face: "floor", facing: "north" }] : "stone_brick_slab");
  for (const x of [-9, -8]) for (const z of [-6, -5]) c.s(x, 1, z, "barrel");
  c.s(-9, 2, -6, "iron_block"); c.s(-8, 2, -6, "gold_block"); c.s(-9, 2, -5, "raw_iron_block"); c.s(-8, 2, -5, "coal_block");
  c.frame(-HW + 1, 3, 1, "east", "IRON_SWORD"); c.frame(-HW + 1, 3, 3, "east", "IRON_PICKAXE"); c.frame(-HW + 1, 4, 2, "east", "IRON_AXE");
  for (const x of [-5, 5]) for (let y = 3; y <= 4; y++) c.s(x, y, -HD, "iron_bars");
  for (const x of [-7, 0, 7]) for (const z of [1, 5]) { c.s(x, 6, z, "chain"); c.s(x, 5, z, lant(true)); }
  // yard
  c.s(HW + 2, 1, 6, ["water_cauldron", { level: "3" }]); c.s(HW + 2, 1, -5, "barrel"); c.s(HW + 2, 2, -5, "raw_iron_block"); c.s(HW + 3, 1, -5, "coal_block");
  for (const x of [-7, -3, 3, 7]) lampPost(w, ...c.pos(x, HD + 8), 3);
  for (let z = -HD; z <= -2; z++) c.s(-HW - 2, 1, z, "cobblestone_wall");
  c.warp("warp_smith", "대장간", 0, 1, 14, "s");
  c.npc("npc", 4, 1, 4, "s");
  c.s(0, 0, 14, "polished_blackstone");
  for (const [x, z] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) c.s(x, 0, 14 + z, "deepslate_bricks");
  for (const [x, z] of [[1, 1], [-1, 1], [1, -1], [-1, -1]]) c.s(x, 0, 14 + z, "magma_block");
}
