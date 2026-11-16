import { HASH, BASE } from "./world.mjs";
export const st = (n, f, extra = {}) => [n, { facing: f, half: "bottom", ...extra }];
export const sti = (n, f) => [n, { facing: f, half: "top" }];
export const lant = (hang = false) => ["lantern", { hanging: hang ? "true" : "false" }];
export const AIR = "air";
export const pick = (x, z, s, ...opts) => opts[Math.floor(HASH(x, z, s) * opts.length) % opts.length];
export const logY = n => [n, { axis: "y" }];
export const L2 = { n: "north", s: "south", e: "east", w: "west" };

/** Roof with ridge along local x: slope 1/2, stairs on even rows, blocks on odd rows (rows counted from the eave). */
export function gable(c, x0, x1, z0, z1, y0, stairN, block) {
  const zc = (z0 + z1) / 2, even = (z1 - z0) % 2 === 0;
  for (let z = z0; z <= z1; z++) {
    const d = Math.min(z - z0, z1 - z), h = y0 + Math.floor(d / 2);
    for (let x = x0; x <= x1; x++) {
      if (even && z === zc) c.s(x, h, z, block);
      else if (d % 2 === 0) c.s(x, h, z, st(stairN, z < zc ? "south" : "north"));
      else c.s(x, h, z, block);
    }
  }
}
export const octa = (r, cut) => (x, z) => Math.abs(x) <= r && Math.abs(z) <= r && Math.abs(x) + Math.abs(z) <= cut;
export function ring(c, pred, y0, y1, bfn, R) {
  for (let z = -R; z <= R; z++) for (let x = -R; x <= R; x++) {
    if (!pred(x, z)) continue;
    if (pred(x + 1, z) && pred(x - 1, z) && pred(x, z + 1) && pred(x, z - 1)) continue;
    for (let y = y0; y <= y1; y++) c.s(x, y, z, bfn(x, y, z));
  }
}
export function floorFill(c, pred, y, bfn, R) { for (let z = -R; z <= R; z++) for (let x = -R; x <= R; x++) if (pred(x, z)) c.s(x, y, z, bfn(x, z)); }
export function lampPost(w, x, z, h = 3) {
  for (let y = 1; y <= h; y++) w.set(x, BASE + y, z, "cobblestone_wall");
  w.set(x, BASE + h + 1, z, "oak_fence"); w.set(x, BASE + h + 2, z, lant(false));
}
export function bench(w, x, z, dir) {
  const f = L2[dir], side = dir === "n" || dir === "s" ? [[-1, 0], [0, 0], [1, 0]] : [[0, -1], [0, 0], [0, 1]];
  for (const [dx, dz] of side) w.set(x + dx, BASE + 1, z + dz, ["spruce_slab", { type: "bottom" }]);
  for (const i of [0, 2]) w.set(x + side[i][0], BASE + 1, z + side[i][1], ["spruce_stairs", { facing: f, half: "bottom" }]);
}
export function tree(w, x, z, kind, h) {
  const log = { cherry: "cherry_log", birch: "birch_log", dark: "dark_oak_log" }[kind] || "oak_log";
  const leaf = { cherry: "cherry_leaves", birch: "birch_leaves", dark: "dark_oak_leaves" }[kind] || "oak_leaves";
  const lb = [leaf, { persistent: "true" }];
  for (let y = 1; y <= h; y++) w.set(x, BASE + y, z, logY(log));
  const top = BASE + h;
  for (let dy = -2; dy <= 1; dy++) {
    const r = dy >= 1 ? 1 : 2;
    for (let dz = -r; dz <= r; dz++) for (let dx = -r; dx <= r; dx++) {
      if (Math.abs(dx) === r && Math.abs(dz) === r && (dy === 1 || HASH(x + dx, z + dz, dy) < 0.5)) continue;
      if (dx === 0 && dz === 0 && dy < 1) continue;
      if (!w.get(x + dx, top + dy, z + dz)) w.set(x + dx, top + dy, z + dz, lb);
    }
  }
  w.set(x, top + 2, z, lb);
}
export function dais(c, cx, ly, wl, ring0, ring1) { /* unused helper placeholder */ }
