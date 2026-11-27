// Preview renderers: top-down map texture + isometric close-ups (no dependencies).
import fs from "node:fs";
import path from "node:path";
import { writePng } from "../village2/lib.mjs";
import { BASE, Y0, Y1, HASH, rotVec } from "./world.mjs";

const RULES = [
  [/^water/, [52, 110, 200]], [/^lava/, [255, 120, 20]], [/sea_lantern/, [200, 235, 225]], [/glowstone|shroomlight/, [240, 200, 110]], [/lantern|end_rod|campfire|torch/, [255, 214, 120]],
  [/amethyst/, [166, 110, 220]], [/purpur/, [170, 120, 170]], [/purple_stained/, [150, 70, 200]], [/yellow_stained/, [240, 220, 110]], [/_stained_glass|glass/, [170, 210, 235]],
  [/red_wool|red_banner|red_wall_banner|red_carpet/, [186, 48, 48]], [/blue_wool|blue_banner|blue_wall_banner/, [52, 88, 190]], [/orange_wool|orange_banner|orange_wall_banner/, [230, 130, 40]], [/yellow_wool|yellow_wall_banner|yellow_banner/, [235, 200, 50]],
  [/lime_wool|lime_wall_banner/, [120, 200, 40]], [/purple_banner/, [130, 60, 170]], [/white_wool|white_carpet/, [236, 236, 232]], [/white_terracotta/, [214, 190, 170]], [/red_terracotta/, [150, 70, 54]], [/blue_terracotta/, [74, 90, 130]],
  [/^bricks|brick_stairs|brick_slab$/, [150, 86, 70]], [/deepslate_tile/, [60, 60, 66]], [/deepslate/, [76, 76, 82]], [/blackstone/, [48, 44, 52]], [/magma/, [190, 80, 30]],
  [/quartz|smooth_stone|^stone_brick_slab/, [226, 222, 214]], [/prismarine/, [90, 160, 150]], [/iron_block|iron_bars|anvil|grindstone|cauldron|chain/, [175, 175, 180]], [/gold_block/, [245, 205, 60]], [/emerald/, [60, 190, 90]], [/coal_block/, [30, 30, 32]], [/raw_iron/, [200, 150, 110]], [/raw_copper/, [200, 120, 80]],
  [/cherry_leaves/, [240, 160, 190]], [/birch_leaves/, [130, 175, 80]], [/dark_oak_leaves/, [60, 110, 40]], [/leaves/, [80, 150, 50]], [/cherry_log/, [200, 150, 160]], [/birch_log/, [220, 218, 205]],
  [/dark_oak|stripped_dark/, [70, 48, 28]], [/spruce|barrel|composter/, [110, 80, 48]], [/oak|planks|log|fence|bookshelf|lectern|table|chiseled_bookshelf/, [170, 134, 82]], [/hay/, [210, 180, 60]], [/melon/, [110, 160, 50]], [/pumpkin/, [220, 130, 30]],
  [/mossy/, [100, 130, 90]], [/cobble/, [128, 128, 126]], [/andesite/, [150, 150, 150]], [/stone_brick|chiseled_stone|cracked_stone/, [136, 136, 136]], [/furnace/, [100, 100, 100]], [/^stone$|stone/, [125, 125, 125]], [/gravel/, [140, 134, 130]],
  [/sand/, [226, 212, 150]], [/grass_block|^grass$/, [102, 168, 66]], [/short_grass|fern|sugar_cane/, [90, 160, 60]], [/podzol/, [110, 78, 40]], [/coarse|dirt|farmland/, [120, 86, 56]], [/poppy|red_tulip/, [220, 40, 40]], [/dandelion/, [250, 220, 40]], [/cornflower|blue_orchid|azure/, [90, 110, 230]], [/allium|lily/, [190, 120, 220]], [/pink_tulip|oxeye|daisy/, [235, 200, 215]], [/bush/, [70, 120, 50]],
];
const nameOf = pal => pal.replace(/^minecraft:/, "").replace(/\[.*/, "");
const memo = new Map();
export function colorOf(pal) {
  let v = memo.get(pal); if (v) return v; const n = nameOf(pal); v = [150, 120, 160];
  for (const [re, c] of RULES) if (re.test(n)) { v = c; break; }
  memo.set(pal, v); return v;
}
const SURFC = { 1: [226, 212, 150], 2: [102, 168, 66], 3: [130, 130, 128], 4: [140, 140, 140], 5: [146, 140, 134], 6: [104, 134, 94], 7: [158, 158, 158], 8: [112, 140, 104], 9: [122, 88, 58], 10: [112, 80, 42], 11: [128, 128, 128], 12: [200, 200, 198], 13: [176, 150, 98] };
const clamp = v => Math.max(0, Math.min(255, Math.round(v)));
const shade = (c, f) => [clamp(c[0] * f), clamp(c[1] * f), clamp(c[2] * f)];

function top(w, x, z) { for (let y = Y1; y >= Y0; y--) { const id = w.get(x, y, z); if (id > 1) return [id, y]; } return null; }

export function renderAll(w, { wdist, root, here, B }) {
  // ---- top-down
  let minx = 1e9, maxx = -1e9, minz = 1e9, maxz = -1e9;
  for (let z = w.z0; z < w.z0 + w.H; z++) for (let x = w.x0; x < w.x0 + w.W; x++) if (w.land(x, z)) { minx = Math.min(minx, x); maxx = Math.max(maxx, x); minz = Math.min(minz, z); maxz = Math.max(maxz, z); }
  minx -= 8; minz -= 8; maxx += 8; maxz += 8;
  const S = 2, PW = (maxx - minx + 1) * S, PH = (maxz - minz + 1) * S, rgb = new Uint8Array(PW * PH * 3);
  const hAt = (x, z) => { const t = top(w, x, z); return t ? t[1] : BASE; };
  for (let z = minz; z <= maxz; z++) for (let x = minx; x <= maxx; x++) {
    let c;
    const t = top(w, x, z);
    if (t) { c = colorOf(w.pal[t[0]]); const hh = t[1] - BASE; c = shade(c, 1 + Math.max(-0.1, Math.min(0.35, hh * 0.018))); if (t[1] > BASE + 2 && hAt(x - 1, z - 1) < t[1] - 1) c = shade(c, 1.1); }
    else if (w.land(x, z)) { const s = w.getSurf(x, z); c = SURFC[s] || [100, 100, 100]; const d = wdist(x, z); if (s === 1 && d <= 1) c = [238, 228, 176]; c = shade(c, 0.94 + HASH(x, z, 71) * 0.1); }
    else { const d = nearLand(w, x, z); c = d <= 1 ? [190, 232, 238] : d === 2 ? [110, 190, 220] : [Math.round(64 - Math.min(d, 9) * 2), Math.round(142 - Math.min(d, 9) * 4), Math.round(210 - Math.min(d, 9) * 5)]; }
    // shadow to the south-east of tall things
    if (!t || t[1] <= BASE + 1) { const a = hAt(x - 1, z - 1); if (a > BASE + 3 && w.land(x, z)) c = shade(c, 0.78); }
    for (let dy = 0; dy < S; dy++) for (let dx = 0; dx < S; dx++) { const i = (((z - minz) * S + dy) * PW + (x - minx) * S + dx) * 3; rgb[i] = c[0]; rgb[i + 1] = c[1]; rgb[i + 2] = c[2]; }
  }
  const mapOut = path.join(root, "dev/pipi-travel/assets/pipitravel/textures/gui/town_map.png");
  writePng(mapOut, PW, PH, rgb);
  console.log("MAP", PW, PH, "orgX", minx, "orgZ", minz, "pxPerBlock", S);
  // preview with markers
  const pv = Buffer.from(rgb);
  const mark = (x, z, col) => { for (let dz = -9; dz <= 9; dz++) for (let dx = -9; dx <= 9; dx++) { const m = Math.abs(dx) + Math.abs(dz); if (m > 9) continue; const px = (x - minx) * S + dx, pz = (z - minz) * S + dz; if (px < 0 || pz < 0 || px >= PW || pz >= PH) continue; const i = (pz * PW + px) * 3; const cc = m >= 8 ? [255, 255, 255] : col; pv[i] = cc[0]; pv[i + 1] = cc[1]; pv[i + 2] = cc[2]; } };
  for (const wp of w.warps) mark(wp.x, wp.z, wp.id === "town_plaza" ? [214, 48, 64] : [255, 200, 40]);
  fs.mkdirSync(path.join(here), { recursive: true });
  writePng(path.join(here, "preview.png"), PW, PH, new Uint8Array(pv));
  // ---- isometric close-ups
  const items = [["alchemist", B.alchemist, "E", 24, 34], ["smith", B.smith, "E", 24, 16], ["shop", B.shop, "W", 22, 12], ["armory", B.armory, "N", 24, 16]];
  for (const [name, [cx, cz], F, half, hgt] of items) iso(w, cx, cz, F, half, hgt, path.join(here, `iso_${name}.png`));
  iso(w, 0, 0, "E", 26, 14, path.join(here, "iso_plaza.png"), 7);
}
function nearLand(w, x, z) { for (let d = 1; d <= 9; d++) for (let dz = -d; dz <= d; dz++) for (let dx = -d; dx <= d; dx++) { if (Math.max(Math.abs(dx), Math.abs(dz)) !== d) continue; if (w.land(x + dx, z + dz)) return d; } return 10; }

function poly(buf, W, H, pts, c) {
  let ymin = Infinity, ymax = -Infinity; for (const p of pts) { ymin = Math.min(ymin, p[1]); ymax = Math.max(ymax, p[1]); }
  for (let y = Math.max(0, Math.ceil(ymin)); y <= Math.min(H - 1, Math.floor(ymax)); y++) {
    let xs = []; for (let i = 0; i < pts.length; i++) { const a = pts[i], b = pts[(i + 1) % pts.length]; if ((a[1] <= y && b[1] > y) || (b[1] <= y && a[1] > y)) xs.push(a[0] + (y - a[1]) / (b[1] - a[1]) * (b[0] - a[0])); }
    xs.sort((p, q) => p - q);
    for (let k = 0; k + 1 < xs.length; k += 2) for (let x = Math.max(0, Math.round(xs[k])); x <= Math.min(W - 1, Math.round(xs[k + 1]) - 1); x++) { const i = (y * W + x) * 3; buf[i] = c[0]; buf[i + 1] = c[1]; buf[i + 2] = c[2]; }
  }
}
export function iso(w, cx, cz, F, half, hgt, file, a = 9) {
  const r = { E: 0, W: 2, N: 1, S: 3 }[F], inv = (4 - r) % 4;
  const get = (u, v, y) => { const [dx, dz] = rotVec(u, v, inv); return w.get(cx + dx, y, cz + dz); };
  const gnd = (u, v) => { const [dx, dz] = rotVec(u, v, inv); return { land: w.land(cx + dx, cz + dz), s: w.getSurf(cx + dx, cz + dz) }; };
  const N = half * 2 + 1, ys = hgt + 3;
  const W = Math.round(N * a * 2 + 40), H = Math.round(N * a + ys * a + 60);
  const buf = new Uint8Array(W * H * 3); for (let i = 0; i < W * H; i++) { buf[i * 3] = 150; buf[i * 3 + 1] = 200; buf[i * 3 + 2] = 240; }
  const sx = (u, v) => (u - v) * a + W / 2, sy = (u, v, y) => (u + v) * a / 2 - (y - BASE) * a + H - 40 - N * a / 2 - 0;
  const cell = (u, v, y, id, solidTop) => {
    const c = id === 0 ? null : colorOf(w.pal[id]); if (!c) return;
    const x0 = sx(u, v), y0 = sy(u, v, y);
    // block occupies [u,u+1]x[v,v+1], height y..y+1 ; top at y+1
    const P = (du, dv, dy) => [sx(u + du, v + dv), sy(u + du, v + dv, y + dy)];
    poly(buf, W, H, [P(0, 0, 1), P(1, 0, 1), P(1, 1, 1), P(0, 1, 1)], shade(c, 1.08));
    poly(buf, W, H, [P(0, 1, 1), P(1, 1, 1), P(1, 1, 0), P(0, 1, 0)], shade(c, 0.82));
    poly(buf, W, H, [P(1, 0, 1), P(1, 1, 1), P(1, 1, 0), P(1, 0, 0)], shade(c, 0.64));
  };
  for (let s = -N; s <= N; s++) for (let u = -half; u <= half; u++) {
    const v = s - u; if (v < -half || v > half) continue;
    // ground
    const g = gnd(u, v); { const c = g.land ? (SURFC[g.s] || [100, 100, 100]) : [60, 130, 200]; const P = (du, dv) => [sx(u + du, v + dv), sy(u + du, v + dv, BASE)]; poly(buf, W, H, [P(0, 0), P(1, 0), P(1, 1), P(0, 1)], shade(c, 0.96 + HASH(u, v, 5) * 0.08)); }
    for (let y = Y0; y <= BASE + hgt; y++) {
      const id = get(u, v, y); if (id < 2) continue;
      if (y < BASE - 1) continue;
      if (get(u + 1, v, y) > 1 && get(u, v + 1, y) > 1 && get(u, v, y + 1) > 1 && !/water|glass|leaves/.test(w.pal[id])) { if (!/glass|leaves/.test(w.pal[get(u + 1, v, y)] || "")) continue; }
      cell(u, v, y, id);
    }
  }
  writePng(file, W, H, buf);
}
