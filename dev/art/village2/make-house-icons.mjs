// house_owned_src.png / house_empty_src.png -> Minecraft-style 16x16 pixel-art map icons (transparent background).
//   node make-house-icons.mjs
//   -> dev/pipi-travel/assets/pipitravel/textures/gui/house_owned.png, house_empty.png   (drawn by WarpScreen.house)
//   -> dev/art/village2/preview_houses.png (x12 preview on a grass backdrop)
// The baked checkerboard is removed by flood-filling the light background from the image border; the silhouette
// (roof / wall) is area-sampled down to the pixel grid, then repainted with a vanilla-like 3-shade palette and a dark outline.
import fs from "node:fs";
import path from "node:path"; import { fileURLToPath } from "node:url";
import { readPng, writePng } from "../class-icons/pngio.mjs";
const here = path.dirname(fileURLToPath(import.meta.url));
const OUT = path.resolve(here, "../../pipi-travel/assets/pipitravel/textures/gui");
const S = 16; // icon size

/** Source -> per-pixel class: 0 background, 1 outline, 2 roof, 3 wall. */
function classify(file) {
  const { w, h, px } = readPng(fs.readFileSync(path.join(here, file)));
  const lum = i => (px[i * 4] * 299 + px[i * 4 + 1] * 587 + px[i * 4 + 2] * 114) / 1000;
  const sat = i => Math.max(px[i * 4], px[i * 4 + 1], px[i * 4 + 2]) - Math.min(px[i * 4], px[i * 4 + 1], px[i * 4 + 2]);
  const cls = new Uint8Array(w * h);
  // background: light, unsaturated pixels connected to the border (checkerboard + white glow)
  const bgLike = i => px[i * 4 + 3] < 128 || (lum(i) > 170 && sat(i) < 40);
  const stack = [];
  for (let x = 0; x < w; x++) { stack.push(x, (h - 1) * w + x); }
  for (let y = 0; y < h; y++) { stack.push(y * w, y * w + w - 1); }
  const seen = new Uint8Array(w * h);
  while (stack.length) {
    const i = stack.pop(); if (seen[i] || !bgLike(i)) continue; seen[i] = 1;
    const x = i % w, y = (i / w) | 0;
    if (x > 0) stack.push(i - 1); if (x < w - 1) stack.push(i + 1); if (y > 0) stack.push(i - w); if (y < h - 1) stack.push(i + w);
  }
  // wall: grey component containing the lower middle of the house
  const wallSeed = Math.round(h * .7) * w + (w >> 1);
  const greyLike = i => !seen[i] && lum(i) >= 70 && sat(i) < 40;
  const wall = new Uint8Array(w * h); stack.push(wallSeed);
  while (stack.length) {
    const i = stack.pop(); if (wall[i] || !greyLike(i)) continue; wall[i] = 1;
    const x = i % w, y = (i / w) | 0;
    if (x > 0) stack.push(i - 1); if (x < w - 1) stack.push(i + 1); if (y > 0) stack.push(i - w); if (y < h - 1) stack.push(i + w);
  }
  let x0 = w, y0 = h, x1 = 0, y1 = 0;
  for (let i = 0; i < w * h; i++) {
    if (seen[i]) continue;
    cls[i] = wall[i] ? 3 : lum(i) < 70 ? 1 : 2;
    const x = i % w, y = (i / w) | 0;
    x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y);
  }
  return { w, h, cls, box: [x0, y0, x1, y1] };
}

/** Area-sample the silhouette onto a gw x gh grid: 0 empty, 2 roof, 3 wall (outline pixels count as object only). */
function grid(src, gw, gh) {
  const { w, cls, box: [x0, y0, x1, y1] } = src, cw = (x1 - x0 + 1) / gw, ch = (y1 - y0 + 1) / gh;
  const g = new Uint8Array(gw * gh);
  for (let gy = 0; gy < gh; gy++) for (let gx = 0; gx < gw; gx++) {
    const n = [0, 0, 0, 0];
    for (let y = Math.floor(y0 + gy * ch); y < Math.floor(y0 + (gy + 1) * ch); y++)
      for (let x = Math.floor(x0 + gx * cw); x < Math.floor(x0 + (gx + 1) * cw); x++) n[cls[y * w + x]]++;
    const tot = n[0] + n[1] + n[2] + n[3];
    if ((n[1] + n[2] + n[3]) / tot < .42) continue;
    g[gy * gw + gx] = n[2] >= n[3] * .6 || (n[3] === 0) ? 2 : 3;
  }
  return g;
}

const hex = v => [(v >> 16) & 255, (v >> 8) & 255, v & 255];
const PAL = {
  owned: { roof: [0xf0533f, 0xc42a22, 0x861812].map(hex) },   // light, mid, dark (vanilla red-wool-ish ramp)
  empty: { roof: [0x8d939a, 0x666c73, 0x45494f].map(hex) },   // slate grey, darker than the wall so the roof still reads
};
const WALL = [0xb5b5b5, 0x979797, 0x767676].map(hex); // stone-ish grey ramp
const OUTLINE = hex(0x1b1714), DOOR = [0x7a5230, 0x5a3a20].map(hex), WIN = [0x9fd8f0, 0x5aa6c8].map(hex);

function make(file, pal) {
  const gw = S, gh = 13;
  const g = grid(classify(file), gw - 2, gh - 2); // inner silhouette, a 1px outline goes around it
  const W = gw, H = gh, m = new Uint8Array(W * H);
  for (let y = 0; y < gh - 2; y++) for (let x = 0; x < gw - 2; x++) m[(y + 1) * W + x + 1] = g[y * (gw - 2) + x];
  // the roof band reads at 16 px only if it is at least 2 px thick: grow it one pixel down into the wall
  for (let y = H - 1; y > 0; y--) for (let x = 0; x < W; x++) if (m[y * W + x] === 3 && m[(y - 1) * W + x] === 2 && (y < 2 || m[(y - 2) * W + x] !== 2)) m[y * W + x] = 4;
  for (let i = 0; i < W * H; i++) if (m[i] === 4) m[i] = 2;
  const at = (x, y) => x < 0 || y < 0 || x >= W || y >= H ? 0 : m[y * W + x];
  const img = Buffer.alloc(S * S * 4), oy = S - H; // bottom-aligned in the 16x16 canvas
  const put = (x, y, c, a = 255) => { const i = ((y + oy) * S + x) * 4; img[i] = c[0]; img[i + 1] = c[1]; img[i + 2] = c[2]; img[i + 3] = a; };
  // wall extent (for door / window placement)
  let wx0 = W, wx1 = 0, wy0 = H, wy1 = 0;
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) if (m[y * W + x] === 3) { wx0 = Math.min(wx0, x); wx1 = Math.max(wx1, x); wy0 = Math.min(wy0, y); wy1 = Math.max(wy1, y); }
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    const t = at(x, y);
    if (!t) { // outline: any empty pixel touching the object (8-neighbourhood)
      let touch = false;
      for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) if (at(x + dx, y + dy)) touch = true;
      if (touch) put(x, y, OUTLINE);
      continue;
    }
    if (t === 2) {
      const above = at(x, y - 1) === 2, below = at(x, y + 1);
      put(x, y, !above ? pal.roof[0] : below !== 2 ? pal.roof[2] : pal.roof[1]); // lit top edge, shaded underside
      if (above && below === 2 && (x + y) % 4 === 0) put(x, y, pal.roof[0]); // shingle speckle
      continue;
    }
    // wall: lit left column, shaded right column / under the eaves, a few stone specks (vanilla stone-like)
    let c = WALL[1];
    if (x === wx0) c = WALL[0];
    if (x === wx1 || at(x, y - 1) === 2) c = WALL[2];
    else if (y === wy1) c = WALL[2];
    else if ((x * 5 + y * 3) % 11 === 0) c = WALL[0];
    else if ((x * 3 + y * 7) % 13 === 0) c = WALL[2];
    put(x, y, c);
  }
  // door (2x3, centred at the bottom) with a gold knob, one window on each side
  const dx = Math.round((wx0 + wx1) / 2) - 1;
  for (let y = wy1 - 2; y <= wy1; y++) for (let x = dx; x < dx + 2; x++) put(x, y, x === dx ? DOOR[0] : DOOR[1]);
  put(dx + 1, wy1 - 1, hex(0xe0b84a));
  const win = x0 => { for (let y = wy1 - 3; y <= wy1 - 2; y++) for (let x = x0; x < x0 + 2; x++) put(x, y, y === wy1 - 3 && x === x0 ? WIN[0] : WIN[1]); };
  if (wx0 + 2 < dx) win(wx0 + 1);
  if (wx1 - 2 > dx + 2) win(wx1 - 2);
  return img;
}

const icons = { house_owned: make("house_owned_src.png", PAL.owned), house_empty: make("house_empty_src.png", PAL.empty) };
for (const [name, img] of Object.entries(icons)) fs.writeFileSync(path.join(OUT, name + ".png"), writePng(img, S, S));

// preview: both icons x12 on a grass backdrop
const Z = 12, PW = (S * 2 + 3) * Z, PH = (S + 2) * Z, prev = Buffer.alloc(PW * PH * 4);
for (let y = 0; y < PH; y++) for (let x = 0; x < PW; x++) { const i = (y * PW + x) * 4, k = ((x / Z | 0) + (y / Z | 0)) % 3; prev[i] = 96 + k * 6; prev[i + 1] = 158 + k * 6; prev[i + 2] = 64; prev[i + 3] = 255; }
let ox = Z;
for (const img of Object.values(icons)) {
  for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) {
    const s = (y * S + x) * 4; if (!img[s + 3]) continue;
    for (let yy = 0; yy < Z; yy++) for (let xx = 0; xx < Z; xx++) { const d = (((y + 1) * Z + yy) * PW + ox + x * Z + xx) * 4; prev[d] = img[s]; prev[d + 1] = img[s + 1]; prev[d + 2] = img[s + 2]; prev[d + 3] = 255; }
  }
  ox += (S + 1) * Z;
}
fs.writeFileSync(path.join(here, "preview_houses.png"), writePng(prev, PW, PH));
console.log("wrote", Object.keys(icons).map(n => path.join(OUT, n + ".png")).join(", "));
