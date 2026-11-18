// Restyle the two waypoint map textures as an old-parchment ("옛날 지도") map.
//   node dev/art/oldmap/make-oldmap.mjs
//
// The waypoint screen (dev/pipi-travel/src/WarpScreen.java) maps world -> texture with a fixed
// MapDef(size, origin, ppb), so the images MUST keep exactly the same size and pixel geometry.
// This script classifies every source pixel (water / sand / grass / tree / path / stone / building),
// then repaints the same pixels in a sepia parchment style: tan gridded sea, light parchment land,
// dark coast outline + offshore ring, tan paths/plazas, sepia buildings and tree blobs, plus sea-only
// clouds / compass and a soft vignette. It never moves the island, paths or buildings.
// The town map additionally erases every building/plaza footprint (drawn as ordinary mottled land),
// leaving only the coloured facility icons where the buildings were.
//
// The current textures are backed up to dev/art/oldmap/orig_*.png on first run and all later runs read
// those backups, so re-running is stable. Outputs: the two textures + side-by-side previews.
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { readPng, writePng } from "../class-icons/pngio.mjs";

const here = path.dirname(fileURLToPath(import.meta.url));
const gui = path.resolve(here, "../../pipi-travel/assets/pipitravel/textures/gui");

const clamp = (v) => (v < 0 ? 0 : v > 255 ? 255 : v | 0);
const hash = (x, y, s = 0) => {
  let h = (x * 374761393 + y * 668265263 + s * 1442695041) | 0;
  h = (h ^ (h >>> 13)) * 1274126177 | 0;
  return ((h ^ (h >>> 16)) >>> 0) / 4294967296;
};
const mulberry32 = (a) => () => {
  a |= 0; a = (a + 0x6d2b79f5) | 0;
  let t = Math.imul(a ^ (a >>> 15), 1 | a);
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
};

// ---- categories ----
const WATER = 0, SAND = 1, GRASS = 2, TREE = 3, PATH_C = 4, STONE = 5, BLD = 6;
const CAT = { water: WATER, sand: SAND, grass: GRASS, tree: TREE, path: PATH_C, stone: STONE, building: BLD };
const NAMES = ["water", "sand", "grass", "tree", "path", "stone", "building"];

// ---- palette (base colours + multiplicative shade variants) -> flat [r,g,b,cat, ...] ----
const FACTORS = [0.82, 0.9, 1.0, 1.08, 1.2, 1.35];
function buildPalette(spec) {
  const pal = [];
  for (const [name, cols] of spec) {
    const cat = CAT[name];
    for (const c of cols) for (const f of FACTORS) {
      pal.push(clamp(c[0] * f), clamp(c[1] * f), clamp(c[2] * f), cat);
    }
  }
  return pal;
}
const hex = (v) => [(v >> 16) & 255, (v >> 8) & 255, v & 255];
// village2 palette (from dev/art/village2/make-map.mjs)
const VILLAGE_PAL = buildPalette([
  ["water", [0x23489e, 0x2b55b4, 0x3463c6, 0x3f72d4, 0x5287de, 0x6f9fe6, 0x8fbdf0, 0xa9cff5].map(hex)],
  ["sand", [0xbfb07a, 0xcdbf88, 0xd8cb95, 0xe1d6a2, 0xe9dfb0].map(hex)],
  ["grass", [0x4a7f2c, 0x568f33, 0x5f9b3a, 0x6aa842, 0x76b54b, 0x84c055,
    0x6a9e3e, 0x78ad48, 0x84ba52, 0x90c55c, 0x9ccf67, 0xa9d873,
    0x3c6324, 0x55862f, 0xbfe48a].map(hex)],
  ["path", [0x7b5e34, 0x8c6c3d, 0x9b7a47, 0xa98752, 0xb6955e, 0xc2a36b,
    0x6b4f2a, 0x86653a, 0x9c7a48, 0xb08b54].map(hex)],
  ["building", [0x3a2a20, 0xebe5dc, 0xd2ccc2, 0xa8282e, 0xc23238].map(hex)],
]);
// town palette (from dev/art/town/render.mjs SURFC + RULES)
const TOWN_PAL = buildPalette([
  ["water", [[52, 110, 200], [190, 232, 238], [110, 190, 220], [58, 130, 195], [56, 126, 190], [64, 142, 210], [100, 180, 212]]],
  ["sand", [[226, 212, 150], [238, 228, 176]]],
  ["grass", [[102, 168, 66], [90, 160, 60], [70, 120, 50]]],
  ["tree", [[80, 150, 50], [130, 175, 80], [60, 110, 40], [240, 160, 190]]],
  ["path", [[120, 86, 56], [110, 78, 40], [176, 150, 98]]],
  ["stone", [[128, 128, 126], [150, 150, 150], [136, 136, 136], [140, 134, 130], [104, 134, 94],
    [158, 158, 158], [112, 140, 104], [200, 200, 198], [125, 125, 125], [175, 175, 180],
    [226, 222, 214], [90, 160, 150]]],
  ["building", [[170, 134, 82], [70, 48, 28], [110, 80, 48], [150, 86, 70], [186, 48, 48], [52, 88, 190],
    [230, 130, 40], [235, 200, 50], [236, 236, 232], [214, 190, 170], [150, 70, 54], [74, 90, 130],
    [60, 60, 66], [76, 76, 82], [48, 44, 52], [166, 110, 220], [170, 120, 170], [245, 205, 60],
    [60, 190, 90], [30, 30, 32], [200, 150, 110], [200, 120, 80], [210, 180, 60], [110, 160, 50],
    [220, 130, 30], [100, 100, 100], [255, 214, 120], [240, 200, 110], [200, 235, 225]]],
]);

function classify(px, off, pal) {
  const r = px[off], g = px[off + 1], b = px[off + 2];
  let best = Infinity, cat = BLD;
  for (let i = 0; i < pal.length; i += 4) {
    const dr = r - pal[i], dg = g - pal[i + 1], db = b - pal[i + 2];
    const d = dr * dr + dg * dg + db * db;
    if (d < best) { best = d; cat = pal[i + 3]; }
  }
  return cat;
}

// ---- sepia output colours ----
const CO = {
  sea: [201, 180, 138], seaGrid: [187, 166, 125], seaBlotch: [178, 157, 116],
  land: [234, 215, 171], land1: [224, 203, 156], land2: [243, 226, 188],
  path: [197, 168, 116], pathD: [185, 154, 102], pathEdge: [158, 124, 78],
  plaza: [211, 187, 143],
  coast: [88, 60, 34], coast2: [122, 92, 58], ring: [170, 144, 104],
  bld: [124, 88, 54], roof: [96, 66, 38], roofDark: [74, 50, 28],
  tree: [104, 74, 44], treeHl: [152, 114, 66],
  cloud: [242, 229, 199], cloudSh: [198, 178, 142],
  compass: [92, 66, 40], compassLt: [214, 190, 150],
};

// ---- pixel helpers ----
const put = (out, W, H, x, y, c) => { if (x < 0 || y < 0 || x >= W || y >= H) return; const i = (y * W + x) * 3; out[i] = c[0]; out[i + 1] = c[1]; out[i + 2] = c[2]; };
function fillEllipse(out, W, H, cx, cy, rx, ry, c) {
  for (let dy = -ry; dy <= ry; dy++) {
    const half = Math.round(rx * Math.sqrt(Math.max(0, 1 - (dy * dy) / ((ry * ry) + 1))));
    for (let dx = -half; dx <= half; dx++) put(out, W, H, cx + dx, cy + dy, c);
  }
}
function fillTriangle(out, W, H, x1, y1, x2, y2, x3, y3, c) {
  const minx = Math.min(x1, x2, x3), maxx = Math.max(x1, x2, x3), miny = Math.min(y1, y2, y3), maxy = Math.max(y1, y2, y3);
  const sign = (ax, ay, bx, by, cx, cy) => (ax - cx) * (by - cy) - (bx - cx) * (ay - cy);
  for (let y = miny; y <= maxy; y++) for (let x = minx; x <= maxx; x++) {
    const d1 = sign(x, y, x1, y1, x2, y2), d2 = sign(x, y, x2, y2, x3, y3), d3 = sign(x, y, x3, y3, x1, y1);
    const neg = d1 < 0 || d2 < 0 || d3 < 0, pos = d1 > 0 || d2 > 0 || d3 > 0;
    if (!(neg && pos)) put(out, W, H, x, y, c);
  }
}
function blob(out, W, H, cx, cy, r) {
  for (let dy = -r; dy <= r; dy++) for (let dx = -r; dx <= r; dx++) {
    if (dx * dx + dy * dy > r * r + 1) continue;
    put(out, W, H, cx + dx, cy + dy, CO.tree);
  }
  for (let dy = -r; dy <= 0; dy++) for (let dx = -r; dx <= 0; dx++) {
    if (dx * dx + dy * dy > r * r + 1) continue;
    if (dx + dy > -Math.max(1, Math.round(r * 0.5))) continue;
    put(out, W, H, cx + dx, cy + dy, CO.treeHl);
  }
}
function bfs(seed, W, H, cap, eight) {
  const d = new Int32Array(W * H).fill(cap);
  const q = new Int32Array(W * H); let head = 0, tail = 0;
  for (let i = 0; i < W * H; i++) if (seed[i]) { d[i] = 0; q[tail++] = i; }
  const off4 = [-1, 1, -W, W];
  const off8 = [-W - 1, -W, -W + 1, -1, 1, W - 1, W, W + 1];
  while (head < tail) {
    const i = q[head++], nd = d[i] + 1; if (nd > cap) continue;
    const x = i % W;
    const offs = eight ? off8 : off4;
    for (const o of offs) {
      if (eight && (o === -W - 1 || o === -W + 1 || o === W - 1 || o === W + 1) && (x === 0 || x === W - 1)) continue;
      const j = i + o; if (j < 0 || j >= W * H) continue;
      if (d[j] > nd) { d[j] = nd; q[tail++] = j; }
    }
  }
  return d;
}
function components(mask, W, H) {
  const comp = new Int32Array(W * H).fill(-1), comps = [];
  const q = new Int32Array(W * H);
  for (let i = 0; i < W * H; i++) {
    if (!mask[i] || comp[i] >= 0) continue;
    const id = comps.length; let head = 0, tail = 0; q[tail++] = i; comp[i] = id;
    const info = { area: 0, minx: W, miny: H, maxx: 0, maxy: 0, sx: 0, sy: 0, id };
    while (head < tail) {
      const j = q[head++], x = j % W, y = (j / W) | 0;
      info.area++; info.sx += x; info.sy += y;
      if (x < info.minx) info.minx = x; if (x > info.maxx) info.maxx = x;
      if (y < info.miny) info.miny = y; if (y > info.maxy) info.maxy = y;
      if (x > 0 && mask[j - 1] && comp[j - 1] < 0) { comp[j - 1] = id; q[tail++] = j - 1; }
      if (x < W - 1 && mask[j + 1] && comp[j + 1] < 0) { comp[j + 1] = id; q[tail++] = j + 1; }
      if (y > 0 && mask[j - W] && comp[j - W] < 0) { comp[j - W] = id; q[tail++] = j - W; }
      if (y < H - 1 && mask[j + W] && comp[j + W] < 0) { comp[j + W] = id; q[tail++] = j + W; }
    }
    comps.push(info);
  }
  return { comp, comps };
}

// ---- facility icons (coloured pixel-art sprites) ----
// Small hand-drawn sprites at ~18x16, scaled up so they read as crisp pixel art. Each facility
// on the town map is painted over its footprint so the map shows *which* facility is where,
// instead of a flat sepia silhouette; everything else stays sepia.
function makeSprite(iw, ih, paint) {
  const rgb = new Uint8Array(iw * ih * 3), mask = new Uint8Array(iw * ih);
  const set = (x, y, c) => { x = Math.round(x); y = Math.round(y); if (x < 0 || y < 0 || x >= iw || y >= ih) return; const i = y * iw + x; rgb[i * 3] = c[0]; rgb[i * 3 + 1] = c[1]; rgb[i * 3 + 2] = c[2]; mask[i] = 1; };
  const rect = (x0, y0, x1, y1, c) => { const ax = Math.min(x0, x1), bx = Math.max(x0, x1), ay = Math.min(y0, y1), by = Math.max(y0, y1); for (let y = ay; y <= by; y++) for (let x = ax; x <= bx; x++) set(x, y, c); };
  const tri = (x1, y1, x2, y2, x3, y3, c) => {
    const sg = (ax, ay, bx, by, cx, cy) => (ax - cx) * (by - cy) - (bx - cx) * (ay - cy);
    const minx = Math.min(x1, x2, x3), maxx = Math.max(x1, x2, x3), miny = Math.min(y1, y2, y3), maxy = Math.max(y1, y2, y3);
    for (let y = miny; y <= maxy; y++) for (let x = minx; x <= maxx; x++) { const d1 = sg(x, y, x1, y1, x2, y2), d2 = sg(x, y, x2, y2, x3, y3), d3 = sg(x, y, x3, y3, x1, y1); const neg = d1 < 0 || d2 < 0 || d3 < 0, pos = d1 > 0 || d2 > 0 || d3 > 0; if (!(neg && pos)) set(x, y, c); }
  };
  const ell = (cx, cy, rx, ry, c) => { for (let y = -ry; y <= ry; y++) { const hw = Math.round(rx * Math.sqrt(Math.max(0, 1 - (y * y) / (ry * ry + 1)))); rect(cx - hw, cy + y, cx + hw, cy + y, c); } };
  const line = (x0, y0, x1, y1, c) => { const n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) || 1; for (let i = 0; i <= n; i++) set(x0 + (x1 - x0) * i / n, y0 + (y1 - y0) * i / n, c); };
  paint({ set, rect, tri, ell, line });
  return { iw, ih, rgb, mask };
}

const ICONS = {
  // alchemist: stone hall under a bent orange witch hat
  alchemist: makeSprite(18, 16, ({ set, rect, tri }) => {
    const wall = [198, 162, 106], wallD = [168, 132, 82], door = [78, 52, 30], glass = [250, 210, 96];
    const hat = [228, 124, 42], hatL = [250, 168, 74], hatD = [178, 82, 24], band = [102, 64, 34];
    rect(4, 9, 13, 14, wall); rect(12, 9, 13, 14, wallD);
    rect(7, 11, 10, 14, door);
    set(5, 11, glass); set(6, 11, glass); set(11, 11, glass); set(12, 11, glass);
    tri(9, 0, 1, 8, 17, 8, hat); tri(9, 0, 9, 8, 17, 8, hatD); tri(9, 0, 1, 8, 9, 8, hatL);
    rect(1, 8, 17, 9, hatD); rect(1, 8, 17, 8, band);
    set(9, 0, [40, 26, 16]); set(10, 1, [40, 26, 16]);
  }),
  // smith: dark forge with a glowing mouth and an anvil
  smith: makeSprite(18, 16, ({ set, rect }) => {
    const stone = [86, 80, 76], stoneD = [62, 58, 56], iron = [66, 64, 74], ironL = [104, 102, 112], ironD = [44, 42, 52];
    const fire = [236, 124, 32], fireL = [255, 216, 120];
    rect(2, 9, 16, 14, stone); rect(13, 9, 16, 14, stoneD);
    rect(6, 10, 12, 14, fire); rect(7, 11, 11, 13, fireL);
    rect(3, 5, 5, 9, stone); rect(3, 5, 5, 5, stoneD);
    rect(4, 4, 14, 5, iron); rect(6, 6, 12, 7, iron); rect(8, 8, 10, 8, iron);
    set(3, 4, ironL); set(3, 5, ironD); set(14, 4, ironD);
    set(15, 3, fireL); set(16, 5, fireL); set(2, 2, fireL);
  }),
  // shop: striped market canopy on poles over a stall
  shop: makeSprite(18, 16, ({ set, rect }) => {
    const red = [198, 58, 54], wht = [238, 232, 220], pole = [150, 110, 60], wood = [164, 124, 72];
    const goods = [236, 196, 60], green = [72, 152, 62];
    for (let y = 1; y <= 8; y++) for (let x = 0; x < 18; x++) { if (x < 8 - (y - 1) || x > 9 + (y - 1)) continue; set(x, y, ((x >> 1) % 2 === 0) ? red : wht); }
    rect(0, 8, 17, 8, red); for (let x = 0; x < 18; x++) if ((x >> 1) % 2 === 1) set(x, 8, wht);
    rect(1, 9, 2, 14, pole); rect(15, 9, 16, 14, pole);
    rect(3, 11, 14, 12, wood); rect(3, 11, 14, 11, wht);
    set(6, 10, goods); set(9, 10, green); set(11, 10, red);
  }),
  // armory: stone barracks with a red roof and crossed swords over the door
  armory: makeSprite(18, 16, ({ set, rect, tri, line }) => {
    const wall = [188, 184, 176], wallD = [152, 148, 140], door = [92, 62, 36];
    const roof = [176, 50, 46], roofL = [212, 78, 68], roofD = [132, 32, 30];
    const steel = [206, 210, 218], handle = [120, 80, 44];
    rect(2, 8, 15, 15, wall); rect(13, 8, 15, 15, wallD);
    tri(8, 1, 0, 7, 17, 7, roof); tri(8, 1, 8, 7, 17, 7, roofD); tri(8, 1, 0, 7, 8, 7, roofL);
    rect(0, 7, 17, 8, roofD);
    line(5, 12, 12, 9, steel); line(12, 12, 5, 9, steel);
    rect(4, 12, 5, 13, handle); rect(12, 12, 13, 13, handle);
    rect(7, 13, 10, 15, door);
  }),
  // plaza: round fountain with an amethyst crystal in the centre
  plaza: makeSprite(18, 16, ({ set, rect, ell, tri }) => {
    const stone = [178, 178, 186], stoneD = [142, 142, 150];
    const water = [78, 150, 206], waterL = [156, 210, 238];
    const cry = [172, 112, 224], cryL = [220, 184, 255], cryD = [124, 72, 176];
    ell(9, 10, 8, 4, stoneD); ell(9, 10, 7, 3, stone);
    ell(9, 10, 6, 3, water);
    set(5, 9, waterL); set(6, 9, waterL); set(11, 10, waterL); set(12, 10, waterL);
    rect(8, 7, 10, 10, stone); rect(10, 7, 10, 10, stoneD);
    tri(9, 0, 5, 6, 13, 6, cry); tri(9, 12, 5, 6, 13, 6, cry);
    tri(9, 0, 7, 6, 9, 6, cryL); tri(9, 12, 9, 6, 13, 6, cryD);
    set(2, 4, [255, 255, 255]); set(15, 3, [255, 255, 255]);
  }),
};

// Composite a sprite onto `out`: soft sepia drop shadow, then a 1px dark outline, then the body.
function drawIcon(out, W, H, cx, cy, sprite, k = 2) {
  const sw = sprite.iw * k, sh = sprite.ih * k, ow = sw + 2, oh = sh + 2;
  const ox = Math.round(cx - ow / 2), oy = Math.round(cy - oh / 2);
  const sb = new Uint8Array(ow * oh * 3), sm = new Uint8Array(ow * oh);
  for (let y = 0; y < sprite.ih; y++) for (let x = 0; x < sprite.iw; x++) {
    const si = y * sprite.iw + x; if (!sprite.mask[si]) continue;
    const c0 = sprite.rgb[si * 3], c1 = sprite.rgb[si * 3 + 1], c2 = sprite.rgb[si * 3 + 2];
    for (let dy = 0; dy < k; dy++) for (let dx = 0; dx < k; dx++) { const X = 1 + x * k + dx, Y = 1 + y * k + dy, i = Y * ow + X; sb[i * 3] = c0; sb[i * 3 + 1] = c1; sb[i * 3 + 2] = c2; sm[i] = 1; }
  }
  const px = (x, y) => (x >= 0 && y >= 0 && x < ow && y < oh && sm[y * ow + x]);
  for (let y = 0; y < oh; y++) for (let x = 0; x < ow; x++) {
    if (!sm[y * ow + x]) continue;
    const sx = x + 2, sy = y + 3; if (px(sx, sy)) continue;
    const X = ox + sx, Y = oy + sy; if (X < 0 || Y < 0 || X >= W || Y >= H) continue;
    const i = (Y * W + X) * 3;
    out[i] = Math.round(out[i] * 0.55 + 92 * 0.45); out[i + 1] = Math.round(out[i + 1] * 0.55 + 64 * 0.45); out[i + 2] = Math.round(out[i + 2] * 0.55 + 40 * 0.45);
  }
  for (let y = 0; y < oh; y++) for (let x = 0; x < ow; x++) {
    const i = y * ow + x; if (sm[i]) continue;
    if (!(px(x - 1, y) || px(x + 1, y) || px(x, y - 1) || px(x, y + 1))) continue;
    const X = ox + x, Y = oy + y; if (X < 0 || Y < 0 || X >= W || Y >= H) continue;
    const j = (Y * W + X) * 3; out[j] = 38; out[j + 1] = 26; out[j + 2] = 16;
  }
  for (let y = 0; y < oh; y++) for (let x = 0; x < ow; x++) {
    const i = y * ow + x; if (!sm[i]) continue;
    const X = ox + x, Y = oy + y; if (X < 0 || Y < 0 || X >= W || Y >= H) continue;
    const j = (Y * W + X) * 3; out[j] = sb[i * 3]; out[j + 1] = sb[i * 3 + 1]; out[j + 2] = sb[i * 3 + 2];
  }
}

// ---- main restyle ----
function restyle(def) {
  const backup = path.join(here, def.backup);
  const srcPath = path.join(gui, def.src);
  if (!fs.existsSync(backup)) fs.copyFileSync(srcPath, backup);
  const img = readPng(fs.readFileSync(backup));
  const W = img.w, H = img.h, px = img.px;
  const pal = def.pal;

  // classify
  const lab = new Uint8Array(W * H), counts = new Array(NAMES.length).fill(0);
  for (let i = 0; i < W * H; i++) { const c = classify(px, i * 4, pal); lab[i] = c; counts[c]++; }

  const water = new Uint8Array(W * H), land = new Uint8Array(W * H), pathM = new Uint8Array(W * H), treeM = new Uint8Array(W * H), bldM = new Uint8Array(W * H);
  for (let i = 0; i < W * H; i++) {
    if (lab[i] === WATER) water[i] = 1; else land[i] = 1;
    if (lab[i] === PATH_C) pathM[i] = 1;
    if (lab[i] === TREE) treeM[i] = 1;
    if (lab[i] === BLD) bldM[i] = 1;
  }
  const dWater = bfs(water, W, H, 16, true);   // land pixel: distance to sea
  const dLand = bfs(land, W, H, 16, true);     // sea pixel: distance to land
  const dPath = def.map === "village2" ? bfs(pathM, W, H, 8, true) : null;

  // sea blotches
  const rng = mulberry32(def.seed);
  const blotches = [];
  for (let k = 0; k < 9; k++) blotches.push({ x: rng() * W, y: rng() * H, r: 16 + rng() * 34, a: 0.05 + rng() * 0.05 });

  const out = new Uint8Array(W * H * 3);
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    const i = y * W + x;
    let c;
    if (lab[i] === WATER) {
      c = (x % 24 === 0 || y % 24 === 0) ? CO.seaGrid : CO.sea;
      let dark = 0;
      for (const b of blotches) { const dx = x - b.x, dy = y - b.y, d = Math.sqrt(dx * dx + dy * dy); if (d < b.r) { const t = 1 - d / b.r; dark = Math.max(dark, b.a * t * t); } }
      if (dark > 0) c = [clamp(c[0] * (1 - dark)), clamp(c[1] * (1 - dark)), clamp(c[2] * (1 - dark))];
    } else {
      const h = hash(x, y, def.seed);
      const ground = h < 0.5 ? CO.land : h < 0.8 ? CO.land1 : CO.land2;
      switch (lab[i]) {
        case PATH_C: c = hash(x, y, def.seed + 5) < 0.25 ? CO.pathD : CO.path; break;
        case STONE: c = def.eraseBuildings ? ground : CO.plaza; break;
        case BLD: c = def.eraseBuildings ? ground : CO.bld; break;
        default: c = ground;
      }
    }
    put(out, W, H, x, y, c);
  }

  // soft path edge: land not path adjacent to a path gets a brown rim
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    const i = y * W + x; if (!land[i] || pathM[i]) continue;
    if ((x > 0 && pathM[i - 1]) || (x < W - 1 && pathM[i + 1]) || (y > 0 && pathM[i - W]) || (y < H - 1 && pathM[i + W]))
      put(out, W, H, x, y, CO.pathEdge);
  }

  // coastline: 2 px dark outline just inside land; faint ring 4-6 px offshore
  for (let i = 0; i < W * H; i++) {
    if (land[i]) {
      const d = dWater[i];
      if (d === 1) put(out, W, H, i % W, (i / W) | 0, CO.coast);
      else if (d === 2) put(out, W, H, i % W, (i / W) | 0, CO.coast2);
    } else {
      const d = dLand[i];
      if (d >= 4 && d <= 6) put(out, W, H, i % W, (i / W) | 0, CO.ring);
    }
  }

  // trees: one round blob per tree blob
  {
    const { comp, comps } = components(treeM, W, H);
    for (const t of comps) {
      if (t.area < 3) continue;
      const cx = Math.round(t.sx / t.area), cy = Math.round(t.sy / t.area);
      const r = Math.min(5, Math.max(2, Math.round(Math.sqrt(t.area) / 2.2)));
      blob(out, W, H, cx, cy, r);
    }
  }
  // village2 has no trees in the source: scatter small dots over grass away from paths/coast
  if (def.map === "village2") {
    const placed = [];
    for (let y = 10; y < H - 10; y += 3) for (let x = 10; x < W - 10; x += 3) {
      const i = y * W + x;
      if (lab[i] !== GRASS || dPath[i] <= 2 || dWater[i] <= 4) continue;
      if (hash(x, y, 777) > 0.03) continue;
      if (placed.some((p) => Math.abs(p[0] - x) < 8 && Math.abs(p[1] - y) < 8)) continue;
      placed.push([x, y]);
      blob(out, W, H, x, y, hash(x, y, 778) < 0.45 ? 2 : 1);
    }
  }

  // buildings
  const { comps } = components(bldM, W, H);
  const claimed = new Set();
  // facilities: a coloured pixel-art icon over each footprint, so the map shows which
  // convenience facility is where while the rest of the map stays sepia.
  if (def.facilities) {
    const texX = (wx) => Math.round((wx - def.orgX) * def.ppb);
    const texY = (wz) => Math.round((wz - def.orgZ) * def.ppb);
    for (const f of def.facilities) {
      let cx = texX(f.x), cy = texY(f.z);
      if (f.building) {
        let best = null, bd = Infinity;
        for (const t of comps) {
          if (t.area < 50 || claimed.has(t.id)) continue;
          const d = Math.hypot(t.sx / t.area - cx, t.sy / t.area - cy);
          if (d < bd) { bd = d; best = t; }
        }
        if (best && bd < 34) { cx = Math.round(best.sx / best.area); cy = Math.round(best.sy / best.area); claimed.add(best.id); }
      }
      drawIcon(out, W, H, cx, cy, ICONS[f.icon], f.scale || 2);
    }
  }
  // any remaining building blobs become a plain sepia roof (town erases them to land instead)
  if (!def.eraseBuildings) {
    for (const t of comps) {
      if (t.area < 50 || claimed.has(t.id)) continue;
      const bw = t.maxx - t.minx + 1, bh = t.maxy - t.miny + 1;
      if (bw < 6 || bh < 6 || bw / bh > 3 || bh / bw > 3) continue;
      const cx = (t.minx + t.maxx) / 2;
      const roofH = Math.max(3, Math.round(bh * 0.42));
      fillTriangle(out, W, H, Math.round(cx), t.miny, t.minx, t.miny + roofH, t.maxx, t.miny + roofH, CO.roof);
      for (let x = t.minx; x <= t.maxx; x++) put(out, W, H, x, t.miny + roofH, CO.roofDark);
    }
  }

  // ---- sea-only decorations (never over land) ----
  const isSeaRect = (x0, y0, x1, y1) => {
    for (let y = Math.max(0, y0); y <= Math.min(H - 1, y1); y++) for (let x = Math.max(0, x0); x <= Math.min(W - 1, x1); x++) if (!water[y * W + x]) return false;
    return true;
  };
  const cloud = (x, y, s) => {
    const sh = (X, Y, r1, r2) => fillEllipse(out, W, H, X, Y, r1, r2, CO.cloudSh);
    const body = (X, Y, r1, r2) => fillEllipse(out, W, H, X, Y, r1, r2, CO.cloud);
    // sepia shadow first, cream body on top so the shadow peeks out below
    sh(x, y + 3, s, s >> 1); sh(x + ((s * 2) / 3 | 0), y + 3 - (s / 3 | 0), (s * 2) / 3 | 0, s >> 1); sh(x - (s / 2 | 0), y + 3 - (s / 6 | 0), s >> 1, (s / 3 | 0));
    body(x, y, s, s >> 1); body(x + ((s * 2) / 3 | 0), y - (s / 3 | 0), (s * 2) / 3 | 0, s >> 1); body(x - (s / 2 | 0), y - (s / 6 | 0), s >> 1, (s / 3 | 0));
  };
  // free sea centres: far enough from every land pixel that a small ornament fits
  const freeSea = [];
  for (let y = 3; y < H - 3; y += 3) for (let x = 3; x < W - 3; x += 3) { const i = y * W + x; if (water[i] && dLand[i] >= 8) freeSea.push([x, y, dLand[i]]); }
  freeSea.sort((a, b) => b[2] - a[2]);

  // compass rose in the roomiest free sea corner (prefer lower-left like the reference)
  const used = [];
  let compassInfo = "none";
  {
    let pick = null;
    for (const [left, top] of [[0, H - 130], [W - 130, H - 130], [0, 0], [W - 130, 0]]) {
      let bx = 0, by = 0, room = -1;
      for (let y = Math.max(0, top); y < Math.min(H, top + 130); y++) for (let x = Math.max(0, left); x < Math.min(W, left + 130); x++) {
        const i = y * W + x; if (!water[i]) continue;
        const r = Math.min(dLand[i], x, y, W - 1 - x, H - 1 - y);   // fits land and the image border
        if (r > room) { room = r; bx = x; by = y; }
      }
      if (room > (pick ? pick[2] : -1)) pick = [bx, by, room];
    }
    if (pick && pick[2] >= 9) {
      const cx = pick[0], cy = pick[1], R = Math.max(7, Math.min(26, pick[2] - 3));
      const long = R, short = Math.round(R * 0.6), hw = Math.max(3, Math.round(R / 5));
      fillEllipse(out, W, H, cx, cy, R, R, [214, 195, 158]);
      const tri = (dx, dy, len, col) => { const px2 = -dy, py2 = dx; fillTriangle(out, W, H, cx + dx * len, cy + dy * len, cx + px2 * hw, cy + py2 * hw, cx - px2 * hw, cy - py2 * hw, col); };
      tri(0, -1, long, CO.compass); tri(0, 1, long, CO.compassLt);
      tri(1, 0, long, CO.compassLt); tri(-1, 0, long, CO.compass);
      const D = Math.SQRT1_2;
      tri(D, -D, short, CO.compass); tri(-D, D, short, CO.compassLt);
      tri(D, D, short, CO.compassLt); tri(-D, -D, short, CO.compass);
      fillEllipse(out, W, H, cx, cy, Math.max(2, Math.round(R / 8)), Math.max(2, Math.round(R / 8)), CO.compass);
      fillEllipse(out, W, H, cx, cy, 1, 1, CO.compassLt);
      used.push([cx, cy]);
      compassInfo = `(${cx},${cy}) r=${R}`;
    }
  }
  // clouds: preferred spots first, then the roomiest free sea, spread apart and clear of the compass
  let cloudN = 0;
  const tryCloud = (x, y, s) => {
    if (cloudN >= 4) return;
    if (x - s - 2 < 0 || y - s - 3 < 0 || x + ((s * 2) / 3 | 0) + 2 >= W || y + s + 5 >= H) return; // keep fully inside
    if (!isSeaRect(x - s - 2, y - s - 3, x + ((s * 2) / 3 | 0) + 2, y + s + 5)) return;
    if (used.some((u) => Math.hypot(u[0] - x, u[1] - y) < s + 52)) return;
    cloud(x, y, s); used.push([x, y]); cloudN++;
  };
  for (const [x, y, s] of def.clouds) tryCloud(x, y, s);
  for (const [x, y, d] of freeSea) { if (cloudN >= 4) break; tryCloud(x, y, Math.min(13, Math.max(8, d - 3))); }

  // vignette
  {
    const edge = 15;
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
      const d = Math.min(x, y, W - 1 - x, H - 1 - y);
      if (d >= edge) continue;
      const f = 0.82 + 0.18 * (d / edge);
      const i = (y * W + x) * 3;
      out[i] = clamp(out[i] * f); out[i + 1] = clamp(out[i + 1] * f); out[i + 2] = clamp(out[i + 2] * f);
    }
  }

  // write texture (same size)
  const rgba = Buffer.alloc(W * H * 4);
  for (let i = 0; i < W * H; i++) { rgba[i * 4] = out[i * 3]; rgba[i * 4 + 1] = out[i * 3 + 1]; rgba[i * 4 + 2] = out[i * 3 + 2]; rgba[i * 4 + 3] = 255; }
  fs.writeFileSync(srcPath, writePng(rgba, W, H));

  // side-by-side preview (original | new)
  const gap = 8, PW = W * 2 + gap, PH = H;
  const prev = Buffer.alloc(PW * PH * 4, 0);
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    const si = (y * W + x) * 4, di = (y * PW + x) * 4;
    prev[di] = px[si]; prev[di + 1] = px[si + 1]; prev[di + 2] = px[si + 2]; prev[di + 3] = 255;
    const di2 = (y * PW + W + gap + x) * 4, ri = (y * W + x) * 3;
    prev[di2] = out[ri]; prev[di2 + 1] = out[ri + 1]; prev[di2 + 2] = out[ri + 2]; prev[di2 + 3] = 255;
  }
  fs.writeFileSync(path.join(here, def.preview), writePng(prev, PW, PH));

  console.log(`[${def.map}] ${W}x${H}  classes ` + NAMES.map((n, k) => `${n}=${counts[k]}`).join(" ") + `  clouds=${cloudN} compass=${compassInfo}`);
  console.log("  wrote", srcPath);
  console.log("  wrote", path.join(here, def.preview));
}

const MAPS = [
  {
    map: "village2", pal: VILLAGE_PAL, seed: 1337,
    src: "village2_map.png", backup: "orig_village2_map.png", preview: "preview_village2.png",
    clouds: [[130, 96, 15], [430, 92, 13], [110, 380, 16], [470, 350, 12], [300, 60, 12]],
  },
  {
    map: "town", pal: TOWN_PAL, seed: 4242,
    src: "town_map.png", backup: "orig_town_map.png", preview: "preview_town.png",
    // world -> texture mapping (must match WarpScreen.MapDef: ppb=2, origin (-88,-84))
    orgX: -88, orgZ: -84, ppb: 2,
    // town draws no building/plaza footprints: those pixels become ordinary mottled land
    // so only the coloured facility icons remain where the buildings were.
    eraseBuildings: true,
    // facility centres from dev/art/town/gen.mjs (B.* building centres; plaza = warp 0,0)
    facilities: [
      { icon: "alchemist", x: -51, z: -8, building: true },
      { icon: "smith", x: -54, z: 38, building: true },
      { icon: "shop", x: 79, z: 8, building: true },
      { icon: "armory", x: -8, z: 58, building: true },
      { icon: "plaza", x: 0, z: 0, building: false },
    ],
    clouds: [[66, 70, 12], [366, 62, 12], [70, 292, 12], [368, 298, 11], [216, 52, 11]],
  },
];

for (const def of MAPS) restyle(def);
