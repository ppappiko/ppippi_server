// Class icons for the client (party list, armory tabs, upgrade/smelt rows): node dev/art/class-icons/make.mjs
// Reads warrior_src.png / archer_src.png / mage_src.png (opaque, with a baked grey/white checkerboard "transparent" background),
// removes the checkerboard (flood fill from the edges + enclosed checker pockets), keeps the black outline anti-aliasing as alpha,
// then downsamples to 64x64 RGBA into dev/pipi-travel/assets/pipitravel/textures/gui/class/<name>.png (+ preview copies here).
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { readPng, writePng } from "./pngio.mjs";

const here = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(here, "../../..");
const outDir = path.join(repo, "dev/pipi-travel/assets/pipitravel/textures/gui/class");
const prevDir = path.join(here, "out");
for (const d of [outDir, prevDir]) fs.mkdirSync(d, { recursive: true });
const SIZE = 64, PAD = 2;

function process(name) {
  const { w, h, px } = readPng(fs.readFileSync(path.join(here, name + "_src.png")));
  const lum = new Float32Array(w * h), light = new Uint8Array(w * h);
  for (let i = 0; i < w * h; i++) {
    const r = px[i * 4], g = px[i * 4 + 1], b = px[i * 4 + 2];
    lum[i] = .299 * r + .587 * g + .114 * b;
    light[i] = lum[i] >= 205 && Math.max(r, g, b) - Math.min(r, g, b) < 24 ? 1 : 0;
  }
  // connected components of "light" pixels
  const comp = new Int32Array(w * h).fill(-1), comps = [];
  const stack = [];
  for (let s = 0; s < w * h; s++) {
    if (!light[s] || comp[s] >= 0) continue;
    const id = comps.length, info = { edge: false, n: 0, hi: 0, lo: 0 };
    comps.push(info);
    comp[s] = id; stack.push(s);
    while (stack.length) {
      const i = stack.pop(), x = i % w, y = (i / w) | 0;
      info.n++;
      if (x === 0 || y === 0 || x === w - 1 || y === h - 1) info.edge = true;
      if (lum[i] >= 247) info.hi++; else if (lum[i] >= 217 && lum[i] <= 233) info.lo++;
      for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
        const nx = x + dx, ny = y + dy;
        if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
        const j = ny * w + nx;
        if (light[j] && comp[j] < 0) { comp[j] = id; stack.push(j); }
      }
    }
  }
  // background = touches the edge, or an enclosed pocket made of the two checker tones only
  const bgComp = comps.map(c => c.edge || (c.n > 30 && (c.hi + c.lo) / c.n > .9 && c.hi / c.n > .15 && c.lo / c.n > .15));
  comps.forEach((c, i) => { if (!c.edge && c.n > 200) console.log(`  ${name} pocket ${i}: n=${c.n} hi=${(c.hi / c.n).toFixed(2)} lo=${(c.lo / c.n).toFixed(2)} -> ${bgComp[i] ? "removed" : "kept"}`); });
  const bg = new Uint8Array(w * h);
  for (let i = 0; i < w * h; i++) if (comp[i] >= 0 && bgComp[comp[i]]) bg[i] = 1;
  // distance (in px, up to 3) from the background, for the anti-aliased outline rim
  const near = new Uint8Array(w * h);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    if (bg[y * w + x]) continue;
    outer: for (let d = 1; d <= 3; d++) for (let oy = -d; oy <= d; oy++) for (let ox = -d; ox <= d; ox++) {
      const nx = x + ox, ny = y + oy;
      if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
      if (bg[ny * w + nx]) { near[y * w + x] = d; break outer; }
    }
  }
  const img = new Float32Array(w * h * 4); // straight RGBA 0..1
  let minX = w, minY = h, maxX = 0, maxY = 0;
  for (let i = 0; i < w * h; i++) {
    if (bg[i]) continue;
    let r = px[i * 4] / 255, g = px[i * 4 + 1] / 255, b = px[i * 4 + 2] / 255, a = 1;
    if (near[i] && lum[i] > 40) { // outline blended into the checkerboard: black with partial alpha
      a = Math.max(0, Math.min(1, 1 - lum[i] / 240));
      r = g = b = 0.04;
    }
    if (a <= 0.02) continue;
    img[i * 4] = r; img[i * 4 + 1] = g; img[i * 4 + 2] = b; img[i * 4 + 3] = a;
    const x = i % w, y = (i / w) | 0;
    minX = Math.min(minX, x); maxX = Math.max(maxX, x); minY = Math.min(minY, y); maxY = Math.max(maxY, y);
  }
  // fit the bounding box into SIZE-2*PAD, area-average downsample (premultiplied)
  const bw = maxX - minX + 1, bh = maxY - minY + 1, k = (SIZE - 2 * PAD) / Math.max(bw, bh);
  const ow = bw * k, oh = bh * k, ox0 = (SIZE - ow) / 2, oy0 = (SIZE - oh) / 2;
  const acc = new Float64Array(SIZE * SIZE * 4), cnt = new Float64Array(SIZE * SIZE);
  for (let y = minY; y <= maxY; y++) for (let x = minX; x <= maxX; x++) {
    const tx = Math.floor(ox0 + (x - minX + .5) * k), ty = Math.floor(oy0 + (y - minY + .5) * k);
    if (tx < 0 || ty < 0 || tx >= SIZE || ty >= SIZE) continue;
    const i = y * w + x, o = ty * SIZE + tx, a = img[i * 4 + 3];
    acc[o * 4] += img[i * 4] * a; acc[o * 4 + 1] += img[i * 4 + 1] * a; acc[o * 4 + 2] += img[i * 4 + 2] * a; acc[o * 4 + 3] += a;
    cnt[o]++;
  }
  const out = Buffer.alloc(SIZE * SIZE * 4);
  for (let o = 0; o < SIZE * SIZE; o++) {
    if (!cnt[o]) continue;
    const a = acc[o * 4 + 3] / cnt[o];
    if (a < .03) continue;
    for (let c = 0; c < 3; c++) out[o * 4 + c] = Math.round(Math.min(1, acc[o * 4 + c] / acc[o * 4 + 3]) * 255);
    out[o * 4 + 3] = Math.round(Math.min(1, a) * 255);
  }
  const png = writePng(out, SIZE, SIZE);
  fs.writeFileSync(path.join(outDir, name + ".png"), png);
  fs.writeFileSync(path.join(prevDir, name + ".png"), png);
  // big preview on a dark background to eyeball the cut-out
  const Z = 6, big = Buffer.alloc(SIZE * Z * SIZE * Z * 4);
  for (let y = 0; y < SIZE * Z; y++) for (let x = 0; x < SIZE * Z; x++) {
    const s = ((y / Z | 0) * SIZE + (x / Z | 0)) * 4, d = (y * SIZE * Z + x) * 4, a = out[s + 3] / 255;
    const bgc = ((x >> 4) + (y >> 4)) & 1 ? 60 : 40;
    for (let c = 0; c < 3; c++) big[d + c] = Math.round(out[s + c] * a + bgc * (1 - a));
    big[d + 3] = 255;
  }
  fs.writeFileSync(path.join(prevDir, name + "_preview.png"), writePng(big, SIZE * Z, SIZE * Z));
  console.log(`${name}: ${w}x${h} -> ${SIZE}x${SIZE}`);
}

for (const n of ["warrior", "archer", "mage"]) process(n);
