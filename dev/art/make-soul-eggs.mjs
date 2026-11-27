// Boss soul stones drawn like the 1.21.5 spawn eggs (egg silhouette with the mob's face painted on, features may poke out):
//   node dev/art/make-soul-eggs.mjs [--preview <png>]
// warden_soul_stone (1보스 워든) / sorrow_soul_stone (2보스 통곡의 왕좌 · 비탄의 군주), 32 x 32 -> pipi-facilities item textures.
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { Canvas, encodePNG } from "../pipi-pets/lib/bbmodel.mjs";

const here = path.dirname(fileURLToPath(import.meta.url));
const modDst = path.resolve(here, "../pipi-facilities/assets/pipifacilities/textures/item");
const S = 32, CX = 15.5, CY = 16.8, RY = 14.2;

/** egg half-width at row y (narrower top, fuller bottom) */
const halfW = y => { const t = (y + .5 - CY) / RY; return Math.abs(t) >= 1 ? 0 : 9.8 * Math.sqrt(1 - t * t) * (1 + .13 * t); };
const inEgg = (x, y) => y >= 0 && y < S && Math.abs(x + .5 - CX) < halfW(y);
const hash = (x, y, k = 0) => { let h = x * 374761393 + y * 668265263 + k * 1442695041; h = (h ^ (h >>> 13)) * 1274126177; return ((h ^ (h >>> 16)) >>> 0) / 4294967296; };

/** left-half (16 col) pixel map mirrored to 32 columns */
const mirror = rows => rows.map(r => r + [...r].reverse().join(""));

/** free = overlay chars allowed outside / over the rim (antlers, crown); face chars stay inside so the egg keeps a clean outline */
function egg(ramp, outline, overlay, pal, texture, free = "") {
  const c = new Canvas(S, S), map = mirror(overlay);
  for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) {
    const ch = map[y]?.[x] ?? ".";
    const inside = inEgg(x, y), rim = inside && (!inEgg(x - 1, y) || !inEgg(x + 1, y) || !inEgg(x, y - 1) || !inEgg(x, y + 1));
    if (ch !== "." && pal[ch] && (free.includes(ch) || inside && !rim)) { c.pixel(x, y, pal[ch]); continue; }
    if (!inside) continue;
    if (rim) { c.pixel(x, y, outline); continue; }
    // inner bevel like the vanilla eggs: lit edge on the upper left, shadow edge on the lower right
    const edgeL = !inEgg(x - 2, y) || !inEgg(x, y - 2), edgeR = !inEgg(x + 2, y) || !inEgg(x, y + 2);
    if (ch === "." && edgeR && x + .5 >= CX - 2 && y > 8) { c.pixel(x, y, ramp[0]); continue; }
    if (ch === "." && edgeL && x + .5 < CX && y < 22) { c.pixel(x, y, ramp[ramp.length - 1]); continue; }
    const hw = halfW(y), nx = (x + .5 - CX) / hw, ny = (y + .5 - CY) / RY, nz = Math.sqrt(Math.max(0, 1 - nx * nx * .7 - ny * ny * .7));
    let l = -.5 * nx - .55 * ny + .65 * nz + (hash(x, y) - .5) * .22;   // light from the upper left + speckle noise
    if (texture) l += texture(x, y);
    const i = Math.max(0, Math.min(ramp.length - 1, Math.floor((l + .15) * ramp.length / 1.05)));
    c.pixel(x, y, ramp[i]);
  }
  return c;
}

// ---- 1보스 워든: deep teal shell, glowing cyan antler-tendrils, eyeless face plate, toothy mouth, cyan cheek spots + soul heart
const warden = egg(
  [[8, 30, 36, 255], [12, 44, 52, 255], [18, 60, 70, 255], [26, 80, 92, 255], [38, 104, 116, 255]],
  [4, 16, 20, 255],
  [
    "................",
    ".oo.............",
    ".oCo............",
    ".ocCo...........",
    "..ocCo..........",
    "..odcCo.........",
    "...odcco........",
    "...oddcco.......",
    "....oddcoo......",
    ".....oddco......",
    "......oddo......",
    ".......oo.......",
    "................",
    ".......bFFFFFFFF",
    "......bFFFFFFFFF",
    "......FFccFFFFFF",
    "......FcCcFFFFFF",
    "......FFccFmmmmm",
    "......FFFmmtmmtm",
    ".......FFmmmmmmm",
    ".......FFFmmtmmm",
    "........bFFFFFFF",
    "................",
    "..........d.....",
    "..........d...dc",
    ".........d...dcC",
    "..............dc",
    "................",
  ],
  { o: [4, 16, 20, 255], c: [41, 223, 235, 255], C: [200, 255, 255, 255], d: [16, 132, 142, 255], F: [34, 66, 74, 255], b: [22, 46, 52, 255],
    m: [3, 9, 12, 255], t: [126, 160, 150, 255] },
  (x, y) => (hash(x, y, 7) > .93 && y > 20 ? .5 : 0), "ocCd");   // a few glowing sculk flecks low on the shell

// ---- 2보스 비탄의 군주: pale lilac shaggy shell, gold crown with a violet gem, bone mask with glowing violet eyes and tear trails
const sorrow = egg(
  [[66, 52, 88, 255], [92, 78, 118, 255], [120, 106, 148, 255], [150, 138, 178, 255], [178, 168, 202, 255]],
  [58, 44, 74, 255],
  [
    "...............Y",
    ".........Y....YG",
    "........YGg..YGG",
    "........YGGgYGGG",
    ".......gGGGGGGpP",
    ".......gGGGGGGpp",
    "........gggggggg",
    "................",
    "..........wwwwww",
    "........wwWWWWWW",
    ".......wWWWWWWWW",
    "......wWWWWWWWWW",
    "......wWWWWWkkWW",
    "......wWWWkkeEkW",
    "......wWkkeeekWW",
    "......wWkkkkkWWW",
    "......wWWtWWWWWW",
    "......wWWtWWWWWW",
    ".......wWtWWWmmm",
    ".......wWtWWmmmm",
    "........wtWWmmmm",
    "........wwWWWmmm",
    ".........wwwwwww",
  ],
  { Y: [255, 236, 140, 255], G: [228, 182, 62, 255], g: [150, 100, 28, 255], p: [150, 70, 210, 255], P: [230, 190, 255, 255],
    w: [150, 138, 168, 255], W: [240, 236, 246, 255], k: [62, 30, 82, 255], e: [190, 110, 255, 255], E: [248, 228, 255, 255],
    t: [168, 120, 230, 255], m: [36, 16, 46, 255], h: [210, 200, 220, 255] },
  (x, y) => (hash(x >> 1, y, 3) > .7 ? -.28 : 0) + (y > 22 && hash(x, y >> 1, 5) > .8 ? -.2 : 0), "YGgpP");   // shaggy fur streaks

fs.writeFileSync(path.join(modDst, "warden_soul_stone.png"), encodePNG(warden));
fs.writeFileSync(path.join(modDst, "sorrow_soul_stone.png"), encodePNG(sorrow));
console.log("warden_soul_stone, sorrow_soul_stone (face spawn eggs)");

const pv = process.argv.indexOf("--preview");
if (pv > 0) {   // both eggs side by side at 10x on a checkerboard
  const Z = 10, out = new Canvas(S * Z * 2 + 30, S * Z + 20);
  for (let y = 0; y < out.height; y++) for (let x = 0; x < out.width; x++) out.pixel(x, y, ((x >> 4) + (y >> 4)) % 2 ? [70, 70, 78, 255] : [86, 86, 94, 255]);
  [warden, sorrow].forEach((cv, k) => {
    for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) {
      const i = (y * S + x) * 4, p = [...cv.data.slice(i, i + 4)];
      if (!p || p[3] === 0) continue;
      for (let dy = 0; dy < Z; dy++) for (let dx = 0; dx < Z; dx++) out.pixel(10 + k * (S * Z + 10) + x * Z + dx, 10 + y * Z + dy, p);
    }
  });
  fs.writeFileSync(path.resolve(process.argv[pv + 1]), encodePNG(out));
}
