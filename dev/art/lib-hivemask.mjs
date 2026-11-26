// The crowned sorrow mask shared by the 통곡의 왕좌 lord (make-boss2-concepts.mjs) and its 문지기 (make-hive-mobs.mjs):
// a pale, faintly glowing bone face plate curved round the head, vertical glowing slats, a heavy brow, two bright eyes,
// a lower jaw plate and a crown of jagged light shards fanning up from the forehead.
// Only rotations of 0 / ±22.5 / ±45 deg about one axis are used, so the lord's item-model export keeps every piece.
//   hiveCrownMask(m, { cx, cy, cz, s })  cy = eye height, (cx, cz) = head axis, the mask front sits at about cz - 3.4 s.
import { mix, shade } from "./lib-petmodel.mjs";

export const MASK = "#d6e2bd", MASK_D = "#97a67f", MASK_G = "#aaff7e", MASK_W = "#f0ffe2";
const R = { raw: true };
const n3 = (x, y, z, k) => { let h = Math.imul(Math.floor(x * k) | 0, 374761393) ^ Math.imul(Math.floor(y * k) | 0, 668265263) ^ Math.imul(Math.floor(z * k) | 0, 2147483647); h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) >>> 0) / 4294967296; };
const side = (c, f) => f === "up" ? shade(c, .08) : f === "down" ? shade(c, -.2) : f === "east" || f === "west" ? shade(c, -.08) : c;
/** bone plate with a few dark pits */
const plate = (x, y, z, f) => { const c = side(MASK, f), n = n3(x, y, z, 2); return n < .1 ? mix(c, MASK_D, .6) : n > .9 ? shade(c, .06) : c; };
/** plate whose front carries a glowing vertical slat every ~1.4 units */
const slats = s => (x, y, z, f) => f === "north" && ((Math.floor(x / (.7 * s) + 50) % 2) === 0) && n3(x, 0, z, 1) > .15 ? mix(MASK, MASK_G, .45) : plate(x, y, z, f);

export function hiveCrownMask(m, { cx = 0, cy, cz, s = 1, name = "mask" }) {
  const out = [];
  const arc = (nm, y0, y1, Rr, angs, paint, { w = 1.5, d = .7, dy = () => 0 } = {}) => angs.forEach(a => {
    const rad = a * Math.PI / 180, x = cx + Math.sin(rad) * Rr * s, z = cz - Math.cos(rad) * Rr * s, o = dy(a) * s;
    out.push(m.cube(`${name}_${nm}${a + 90}`, [x - w * s / 2, cy + y0 * s + o, z - d * s / 2], [x + w * s / 2, cy + y1 * s + o, z + d * s / 2], paint,
      a ? { raw: true, rotation: [0, -a, 0], origin: [x, cy + (y0 + y1) / 2 * s + o, z] } : R));
  });
  const A5 = [-45, -22.5, 0, 22.5, 45];
  arc("plate", -2.2, 3.4, 3.4, A5, slats(s), { w: 1.55, dy: a => -Math.abs(a) / 45 * .6 });
  arc("brow", 1.3, 2.4, 3.75, A5, plate, { w: 1.65, d: .8, dy: a => Math.abs(a) / 45 * .5 - .1 });
  arc("jaw", -3.6, -2.1, 3.05, [-22.5, 0, 22.5], plate, { w: 1.7, d: .8 });
  out.push(m.cube(`${name}_chin`, [cx - .9 * s, cy - 4.6 * s, cz - 3.3 * s], [cx + .9 * s, cy - 3.5 * s, cz - 2.5 * s], plate, R));
  out.push(m.cube(`${name}_ridge`, [cx - .35 * s, cy - 2 * s, cz - 3.95 * s], [cx + .35 * s, cy + 3.9 * s, cz - 3.35 * s], (x, y, z, f) => f === "north" ? MASK_W : plate(x, y, z, f), R));
  // eyes: two large slanted glowing slots (inner corners low = a glare) with a dark tear line running down from each
  const eyeP = (x, y, z, f, i, j, w, h) => f === "north" && i > 0 && j > 0 && i < w - 1 && j < h - 1 ? MASK_W : MASK_G;
  for (const o of [-1, 1]) {
    const ex = cx + o * 1.55 * s, ey = cy + .55 * s;
    out.push(m.cube(`${name}_eye${o}`, [ex - 1 * s, ey - .6 * s, cz - 4.15 * s], [ex + 1 * s, ey + .6 * s, cz - 3.6 * s], eyeP, { raw: true, rotation: [0, 0, o * 22.5], origin: [ex, ey, cz - 3.9 * s] }));
    out.push(m.cube(`${name}_tear${o}`, [ex + o * .3 * s - .18 * s, cy - 2 * s, cz - 3.85 * s], [ex + o * .3 * s + .18 * s, cy - .1 * s, cz - 3.6 * s], () => "#3b4a32", R));
  }
  // crown of light shards fanning up from the forehead (front row) and a lower back row
  const shard = (nm, x, z, a, h, w, col) => {
    const y0 = cy + 3.1 * s;
    out.push(m.cube(`${name}_${nm}`, [x - w * s / 2, y0, z - .3 * s], [x + w * s / 2, y0 + h * s, z + .3 * s], (px, py, pz, f) => py > y0 + h * s * .65 ? MASK_W : col,
      a ? { raw: true, rotation: [0, 0, a], origin: [x, y0, z] } : R));
  };
  [[-45, 4.4, 1.2], [-22.5, 6.2, 1.3], [0, 8, 1.5], [22.5, 6.2, 1.3], [45, 4.4, 1.2]].forEach(([a, h, w], k) =>
    shard("crown" + k, cx - Math.sin(a * Math.PI / 180) * 1.6 * s, cz - 3.1 * s, a, h, w, MASK_G));
  [[-22.5, 4.6], [0, 5.6], [22.5, 4.6]].forEach(([a, h], k) => shard("crown_b" + k, cx - Math.sin(a * Math.PI / 180) * 1.2 * s, cz - 1.6 * s, a, h, 1, mix(MASK_G, MASK, .5)));
  out.push(m.cube(`${name}_crown_band`, [cx - 2.6 * s, cy + 2.8 * s, cz - 3.6 * s], [cx + 2.6 * s, cy + 3.5 * s, cz - 1.2 * s], plate, R));
  return out;
}
