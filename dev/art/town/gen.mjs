// Builds the pipi_town model from sketch.png and writes:
//   dev/pipi-rpg/res/town_layout.json (terrain surface rows + structure runs + entities/warps/npcs)
//   dev/pipi-travel/assets/pipitravel/textures/gui/town_map.png, dev/art/town/preview*.png
//   node gen.mjs
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { loadIsland, SK, toWorld } from "./island.mjs";
import { World, BASE, HASH, Y0, Y1, norm, key } from "./world.mjs";
import { tree, bench, lampPost, pick, AIR, lant } from "./bcommon.mjs";
import { alchemist, smith } from "./b1.mjs";
import { shop, armory } from "./b2.mjs";
import { renderAll } from "./render.mjs";
const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "../../..");

const I = loadIsland(), w = new World(I);
export const SURF = ["", "sand", "grass_block", "cobblestone", "stone_bricks", "gravel", "mossy_cobblestone", "polished_andesite", "mossy_stone_bricks", "coarse_dirt", "podzol", "cracked_stone_bricks", "smooth_stone", "dirt_path"];
const C = { sand: 1, grass: 2, cobble: 3, brick: 4, gravel: 5, mossy: 6, andesite: 7, mbrick: 8, coarse: 9, podzol: 10, cbrick: 11, smooth: 12, path: 13 };
const L = (x, z) => w.land(x, z);

// water distance (chebyshev, capped) like the Java side
const wd = new Uint8Array(I.W * I.H).fill(9);
{ const q = []; for (let i = 0; i < I.W * I.H; i++) if (!I.land[i]) { wd[i] = 0; q.push(i); }
  for (let h = 0; h < q.length; h++) { const i = q[h], x = i % I.W, z = (i / I.W) | 0, d = wd[i]; if (d >= 5) continue;
    for (let dz = -1; dz <= 1; dz++) for (let dx = -1; dx <= 1; dx++) { const nx = x + dx, nz = z + dz; if (nx < 0 || nz < 0 || nx >= I.W || nz >= I.H) continue; const j = nz * I.W + nx; if (wd[j] > d + 1) { wd[j] = d + 1; q.push(j); } } } }
export const wdist = (x, z) => { const gx = x - I.x0, gz = z - I.z0; return gx < 0 || gz < 0 || gx >= I.W || gz >= I.H ? 0 : wd[gz * I.W + gx]; };

// ---- base surface: sand beach, grass inland
for (let z = I.z0; z < I.z0 + I.H; z++) for (let x = I.x0; x < I.x0 + I.W; x++) if (L(x, z)) w.setSurf(x, z, wdist(x, z) <= 2 ? C.sand : C.grass);

// ---- buildings (centres derived from the sketch marks; warp stone sits on the mark)
// compact layout: marks pulled in close to the plaza (sketch order kept: alchemist NW, smith SW, armory S, shop E)
const m = { alchemist: [-38, -8], smith: [-40, 38], armory: [-8, 44], shop: [66, 8] };
const B = {
  alchemist: [m.alchemist[0] - 13, m.alchemist[1]],
  smith: [m.smith[0] - 14, m.smith[1]],
  shop: [m.shop[0] + 13, m.shop[1]],
  armory: [m.armory[0], m.armory[1] + 14],
};
const stoneAt = { alchemist: m.alchemist, smith: m.smith, shop: m.shop, armory: m.armory };
const apron = [[B.alchemist, 14], [B.smith, 15], [B.shop, 15], [B.armory, 17]];

// ---- paths: quadratic bezier stamped as cobble/brick/gravel mix
const pathMask = new Uint8Array(I.W * I.H);
function stamp(x, z, r, wide) {
  for (let dz = -Math.ceil(r); dz <= Math.ceil(r); dz++) for (let dx = -Math.ceil(r); dx <= Math.ceil(r); dx++) {
    if (dx * dx + dz * dz > r * r) continue; const X = x + dx, Z = z + dz; if (!L(X, Z) || wdist(X, Z) <= 2) continue;
    const h = HASH(X, Z, 21);
    w.setSurf(X, Z, h < 0.34 ? C.cobble : h < 0.56 ? C.brick : h < 0.76 ? C.gravel : h < 0.88 ? C.mossy : C.path);
    pathMask[(Z - I.z0) * I.W + X - I.x0] = 1;
  }
}
function road(a, b, bend, r = 1.7) {
  const mx = (a[0] + b[0]) / 2, mz = (a[1] + b[1]) / 2, dx = b[0] - a[0], dz = b[1] - a[1], len = Math.hypot(dx, dz) || 1;
  const cx = mx - dz / len * bend, cz = mz + dx / len * bend, pts = [];
  for (let t = 0; t <= 1.0001; t += 1 / (len * 1.5)) { const u = 1 - t; const x = u * u * a[0] + 2 * u * t * cx + t * t * b[0], z = u * u * a[1] + 2 * u * t * cz + t * t * b[1]; stamp(Math.round(x), Math.round(z), r); pts.push([x, z]); }
  return pts;
}
const roads = [
  road([0, 0], [m.alchemist[0] + 2, m.alchemist[1]], 18),
  road([0, 0], [m.smith[0] + 2, m.smith[1]], -16),
  road([0, 0], [m.armory[0], m.armory[1] - 3], 14),
  road([0, 0], [m.shop[0] - 3, m.shop[1]], -12),
  road([m.alchemist[0] + 2, m.alchemist[1] + 6], [m.smith[0] + 2, m.smith[1] - 6], 10, 1.3),
  road([m.smith[0] + 3, m.smith[1] + 6], [m.armory[0] - 6, m.armory[1] - 4], 12, 1.3),
];

// ---- central plaza: tiled floor, rings and spokes
for (let z = -26; z <= 26; z++) for (let x = -26; x <= 26; x++) {
  const d = Math.hypot(x, z); if (d > 24.5 || !L(x, z)) continue;
  const ang = Math.atan2(z, x), spoke = Math.abs(Math.sin(ang * 4)) < 0.08 || Math.abs(Math.cos(ang * 4)) < 0.08;
  let c = HASH(x, z, 22) < 0.5 ? C.brick : HASH(x, z, 23) < 0.6 ? C.andesite : C.mbrick;
  if (d > 14.5 && d < 16.2) c = C.smooth; else if (d > 20.2 && d < 21.5) c = C.cbrick; else if (spoke && d > 9) c = C.cobble; else if (d > 22.5) c = HASH(x, z, 24) < 0.5 ? C.cobble : C.gravel;
  w.setSurf(x, z, c);
}
// plaza structures: dais, basin, bridges, pillars
const Q = "quartz_block", SQ = "smooth_quartz";
for (let z = -9; z <= 9; z++) for (let x = -9; x <= 9; x++) {
  const d = Math.hypot(x, z), axis = Math.abs(x) <= 1 || Math.abs(z) <= 1;
  if (d <= 3.4) { w.set(x, BASE, z, SQ); w.set(x, BASE + 1, z, d < 1.5 ? "chiseled_quartz_block" : (x + z) % 2 ? SQ : "quartz_bricks"); }
  else if (d <= 5.6) {
    w.set(x, BASE - 1, z, HASH(x, z, 25) < 0.4 ? "sea_lantern" : "prismarine_bricks"); w.set(x, BASE, z, "water");
    if (axis) { w.set(x, BASE + 1, z, Math.abs(x) <= 1 && Math.abs(z) <= 1 ? SQ : "polished_andesite"); }
  } else if (d <= 7.4) { w.set(x, BASE, z, "stone_bricks"); w.set(x, BASE + 1, z, d > 6.7 ? "polished_andesite" : (x + z) % 2 ? "stone_bricks" : "mossy_stone_bricks"); }
  else if (d <= 8.7) {
    const f = Math.abs(x) > Math.abs(z) ? (x > 0 ? "west" : "east") : (z > 0 ? "north" : "south");
    w.set(x, BASE, z, "stone_bricks"); w.set(x, BASE + 1, z, ["stone_brick_stairs", { facing: f, half: "bottom" }]);
    if (axis && d <= 8.7) w.set(x, BASE + 1, z, ["stone_brick_stairs", { facing: f, half: "bottom" }]);
  }
}
for (const [sx, sz] of [[1, 1], [-1, 1], [1, -1], [-1, -1]]) {
  const px = sx * 12, pz = sz * 12;
  for (let y = 1; y <= 9; y++) for (let dx = 0; dx <= 1; dx++) for (let dz = 0; dz <= 1; dz++) w.set(px + dx, BASE + y, pz + dz, y <= 1 || y === 9 ? "chiseled_quartz_block" : ["quartz_pillar", { axis: "y" }]);
  w.set(px, BASE + 10, pz, "amethyst_block"); w.set(px + 1, BASE + 10, pz, "amethyst_block"); w.set(px, BASE + 10, pz + 1, "amethyst_block"); w.set(px + 1, BASE + 10, pz + 1, "amethyst_block");
  w.set(px, BASE + 11, pz, ["amethyst_cluster", { facing: "up" }]); w.set(px + 1, BASE + 11, pz + 1, ["amethyst_cluster", { facing: "up" }]);
  w.set(px, BASE + 11, pz + 1, "end_rod"); w.set(px + 1, BASE + 11, pz, "end_rod");
  for (const [qx, qz] of [[-1, -1], [2, -1], [-1, 2], [2, 2]]) { w.set(px + qx, BASE + 1, pz + qz, "amethyst_block"); w.set(px + qx, BASE + 2, pz + qz, ["amethyst_cluster", { facing: "up" }]); }
}
// central warp stone socket
w.set(0, BASE + 2, 0, "air");
for (const [x, z] of [[2, 2], [-2, 2], [2, -2], [-2, -2]]) { w.set(x, BASE + 2, z, ["amethyst_cluster", { facing: "up" }]); }
for (const [x, z] of [[2, 0], [-2, 0], [0, 2], [0, -2]]) w.set(x, BASE + 2, z, "end_rod");
w.warps.push({ id: "town_plaza", name: "마을 광장", x: 0, y: BASE + 2, z: 0, yaw: 0 });
// banners + lamp posts around the plaza ring
for (let k = 0; k < 16; k++) {
  const a = (k / 16) * Math.PI * 2 + 0.2, x = Math.round(Math.cos(a) * 18), z = Math.round(Math.sin(a) * 18);
  if (!L(x, z)) continue;
  if (k % 2 === 0) lampPost(w, x, z, 3);
  else { for (let y = 1; y <= 3; y++) w.set(x, BASE + y, z, "oak_fence"); w.set(x, BASE + 4, z, [["red", "blue", "purple", "yellow"][(k >> 1) % 4] + "_banner", { rotation: String((k * 4) % 16) }]); }
}
for (const [x, z, d] of [[-14, 0, "e"], [14, 0, "w"], [0, -14, "s"], [0, 14, "n"], [10, 10, "w"], [-10, -10, "e"]]) { bench(w, x, z, d); }
// flower beds between dais and pillars
for (const [cx2, cz2] of [[0, 16], [0, -16], [16, 0], [-16, 0]]) for (let dz = -2; dz <= 2; dz++) for (let dx = -2; dx <= 2; dx++) {
  const X = cx2 + dx, Z = cz2 + dz; if (Math.abs(dx) === 2 || Math.abs(dz) === 2) { w.set(X, BASE + 1, Z, "oak_fence"); continue; }
  w.set(X, BASE, Z, "grass_block"); w.set(X, BASE + 1, Z, pick(X, Z, 26, "allium", "peony".replace("peony", "pink_tulip"), "poppy", "cornflower", "azure_bluet", "red_tulip", "oxeye_daisy", "lily_of_the_valley"));
}

// ---- buildings
alchemist(w, ...B.alchemist); smith(w, ...B.smith); shop(w, ...B.shop); armory(w, ...B.armory);

// lamp posts along the roads
for (const pts of roads.slice(0, 4)) for (let i = 30; i < pts.length - 10; i += 14) {
  const [x, z] = pts[i], [x2, z2] = pts[Math.min(pts.length - 1, i + 2)], dx = x2 - x, dz = z2 - z, l = Math.hypot(dx, dz) || 1;
  const lx = Math.round(x - dz / l * 3), lz = Math.round(z + dx / l * 3);
  if (L(lx, lz) && wdist(lx, lz) > 2 && !w.get(lx, BASE + 1, lz) && w.getSurf(lx, lz) === C.grass) lampPost(w, lx, lz, 3);
}

// ---- nature: trees, flowers, tufts, bushes
const near = (x, z, r) => { for (const [c, rr] of apron) if (Math.abs(x - c[0]) < rr + r && Math.abs(z - c[1]) < rr + r - 3) return true; return false; };
const trees = [];
for (let z = I.z0 + 4; z < I.z0 + I.H - 4; z++) for (let x = I.x0 + 4; x < I.x0 + I.W - 4; x++) {
  if (!L(x, z) || wdist(x, z) < 4 || w.getSurf(x, z) !== C.grass) continue;
  const r2 = x * x + z * z; if (r2 < 32 * 32 || near(x, z, 4)) continue;
  const gx = x - I.x0, gz = z - I.z0; if (pathMask[gz * I.W + gx]) continue;
  let bad = false; for (let dz = -3; dz <= 3 && !bad; dz++) for (let dx = -3; dx <= 3; dx++) { if (pathMask[(gz + dz) * I.W + gx + dx] || w.get(x + dx, BASE + 1, z + dz) > 1 || w.get(x + dx, BASE, z + dz) > 1) { bad = true; break; } }
  if (bad) continue;
  const clump = 0.5 + 0.5 * Math.sin(x * 0.09) * Math.sin(z * 0.1 + 1);
  if (HASH(x, z, 31) > 0.0015 + 0.004 * clump + 0.02 * Math.max(0, 1 - Math.abs(wdist(x, z) - 6) / 5) * (r2 > 40 * 40 ? 1 : 0)) continue;
  if (trees.some(t => Math.abs(t[0] - x) < 5 && Math.abs(t[1] - z) < 5)) continue;
  trees.push([x, z]);
  const h = HASH(x, z, 32), kind = h < 0.42 ? "oak" : h < 0.68 ? "birch" : h < 0.82 ? "cherry" : "dark";
  tree(w, x, z, kind, 4 + Math.floor(HASH(x, z, 33) * 3));
}
const tuft = "short_grass";
for (let z = I.z0; z < I.z0 + I.H; z++) for (let x = I.x0; x < I.x0 + I.W; x++) {
  if (!L(x, z) || w.getSurf(x, z) !== C.grass || w.get(x, BASE + 1, z)) continue;
  const gx = x - I.x0, gz = z - I.z0; if (pathMask[gz * I.W + gx] || Math.hypot(x, z) < 10 || near(x, z, -2)) continue;
  const h = HASH(x, z, 41), patch = Math.sin(x * 0.11 + 1) * Math.sin(z * 0.13) > 0.35;
  const f = patch ? ["poppy", "dandelion", "cornflower", "oxeye_daisy", "azure_bluet", "allium", "pink_tulip", "lily_of_the_valley"][Math.floor(HASH(x, z, 42) * 8)] : ["dandelion", "poppy", "cornflower", "oxeye_daisy"][Math.floor(HASH(x, z, 43) * 4)];
  const edge = pathMask[gz * I.W + gx + 2] || pathMask[gz * I.W + gx - 2] || pathMask[(gz + 2) * I.W + gx] || pathMask[(gz - 2) * I.W + gx];
  if (h < (edge ? 0.22 : patch ? 0.06 : 0.003)) w.set(x, BASE + 1, z, f);
  else if (h < (edge ? 0.35 : patch ? 0.12 : 0.05)) w.set(x, BASE + 1, z, tuft);
  else if (h > 0.998) { w.set(x, BASE + 1, z, ["sweet_berry_bush", { age: "3" }]); }
}
// grass-path fringe
for (let z = I.z0 + 1; z < I.z0 + I.H - 1; z++) for (let x = I.x0 + 1; x < I.x0 + I.W - 1; x++) {
  if (w.getSurf(x, z) !== C.grass) continue; const gx = x - I.x0, gz = z - I.z0;
  if (pathMask[gz * I.W + gx + 1] || pathMask[gz * I.W + gx - 1] || pathMask[(gz + 1) * I.W + gx] || pathMask[(gz - 1) * I.W + gx]) { const h = HASH(x, z, 51); if (h < 0.35) w.setSurf(x, z, h < 0.2 ? C.coarse : C.podzol); }
}
// cairn lanterns on the beach rim: skip; coastal reeds
for (let z = I.z0; z < I.z0 + I.H; z++) for (let x = I.x0; x < I.x0 + I.W; x++) {
  if (w.getSurf(x, z) === C.sand && wdist(x, z) === 3 && HASH(x, z, 61) < 0.02) { w.set(x, BASE + 1, z, "sugar_cane"); }
}

// ---- connect fences / walls / panes (shape updates are skipped when blocks are placed without physics)
const isFence = n => /_fence$/.test(n), isWall = n => /_wall$/.test(n), isPane = n => /_pane$|^iron_bars$/.test(n);
const solidName = n => n && !/^(air|water|lava|lantern|torch|short_grass|poppy|dandelion|.*banner|.*carpet|.*stairs|.*slab|end_rod|chain|campfire|sugar_cane|.*bush|.*tulip|allium|cornflower|oxeye_daisy|azure_bluet|lily_of_the_valley|fern|flower_pot|brewing_stand|amethyst_cluster|.*_cauldron|cauldron|anvil|grindstone|.*leaves|.*orchid|.*pumpkin)$/.test(n.replace(/^minecraft:/, "").replace(/\[.*/, ""));
const bn = id => id > 1 ? w.pal[id].replace(/^minecraft:/, "").replace(/\[.*/, "") : "";
const D4 = [["north", 0, -1], ["east", 1, 0], ["south", 0, 1], ["west", -1, 0]];
for (let y = Y0; y <= Y1; y++) for (let z = I.z0; z < I.z0 + I.H; z++) for (let x = I.x0; x < I.x0 + I.W; x++) {
  const id = w.get(x, y, z); if (id < 2) continue; const n = bn(id);
  if (!isFence(n) && !isWall(n) && !isPane(n)) continue;
  const o = norm(w.pal[id]); const p = { ...o.p };
  for (const [nm, dx, dz] of D4) {
    const n2 = bn(w.get(x + dx, y, z + dz)); let c = false;
    if (isFence(n)) c = (isFence(n2) && n2.includes("nether") === n.includes("nether")) || (solidName(n2) && !isFence(n2) && !isWall(n2) && !isPane(n2));
    else if (isWall(n)) c = isWall(n2) || (solidName(n2) && !isFence(n2) && !isPane(n2));
    else c = isPane(n2) || (solidName(n2) && !isFence(n2) && !isWall(n2));
    p[nm] = c ? (isWall(n) ? "low" : "true") : (isWall(n) ? "none" : "false");
  }
  if (isWall(n)) p.up = (bn(w.get(x, y + 1, z)) ? "true" : "true");
  w.set(x, y, z, { n: o.n, p });
}

// ---- ensure airy interiors stay clear: nothing else; export
const spawn = { x: 0.5, y: BASE + 2, z: 4.5, yaw: 180 };
const rows = []; const surfPal = SURF;
for (let z = 0; z < I.H; z++) { let s = "", prev = "", n = 0; for (let x = 0; x < I.W; x++) { const v = w.surf[z * I.W + x], ch = v ? String.fromCharCode(96 + v) : "w"; if (ch === prev) n++; else { if (n) s += prev + n; prev = ch; n = 1; } } s += prev + n; rows.push(s); }
const runs = [];
for (let y = Y0; y <= Y1; y++) for (let z = I.z0; z < I.z0 + I.H; z++) { let x = I.x0; while (x < I.x0 + I.W) { const id = w.get(x, y, z); if (!id) { x++; continue; } let e = x; while (e + 1 < I.x0 + I.W && w.get(e + 1, y, z) === id) e++; runs.push(x, y, z, e - x + 1, id); x = e + 1; } }
const out = { width: I.W, height: I.H, originX: I.x0, originZ: I.z0, baseY: BASE, spawn, surfPal, rows, pal: w.pal, runs, entities: w.entities, warps: w.warps, npcs: w.npcs };
const outFile = path.join(root, "dev/pipi-rpg/res/town_layout.json");
fs.writeFileSync(outFile, JSON.stringify(out));
console.log("layout", outFile, (fs.statSync(outFile).size / 1e6).toFixed(2) + "MB", "runs", runs.length / 5, "pal", w.pal.length, "entities", w.entities.length, "warps", w.warps.length);
console.log(JSON.stringify(w.warps), JSON.stringify(w.npcs));
renderAll(w, { wdist, SURF, root, here, B });
