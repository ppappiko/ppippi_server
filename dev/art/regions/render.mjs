// Preview renderers (no dependencies): top-down map and isometric view of a region Vox.
import { writePng } from "../village2/lib.mjs";
import { Y0, SEA, hash } from "./lib.mjs";

const RULES = [
  [/^light$/, null], [/^water/, [52, 110, 200]], [/^lava/, [255, 120, 20]],
  [/sea_lantern/, [205, 238, 232]], [/pearlescent_froglight/, [236, 214, 236]], [/ochre_froglight/, [246, 226, 150]], [/verdant_froglight/, [210, 240, 190]],
  [/glowstone|shroomlight/, [240, 200, 110]], [/redstone_lamp/, [236, 170, 90]], [/soul_lantern|soul_torch|soul_campfire|soul_fire/, [110, 220, 230]], [/lantern|torch|campfire/, [255, 214, 120]], [/end_rod/, [250, 245, 235]],
  [/beacon/, [140, 240, 240]], [/copper_bulb/, [240, 170, 110]],
  [/sculk_catalyst/, [40, 60, 70]], [/sculk_shrieker/, [190, 200, 170]], [/sculk_sensor/, [30, 120, 140]], [/sculk_vein/, [20, 70, 90]], [/sculk/, [14, 48, 62]],
  [/reinforced_deepslate/, [80, 90, 86]], [/amethyst/, [166, 110, 220]], [/purpur/, [170, 120, 170]], [/end_stone/, [222, 224, 168]],
  [/purple_stained/, [150, 70, 200]], [/magenta_stained/, [200, 90, 200]], [/pink_stained/, [240, 170, 200]], [/light_blue_stained/, [150, 200, 240]], [/cyan_stained/, [80, 150, 170]], [/yellow_stained/, [240, 220, 110]], [/orange_stained/, [230, 140, 60]], [/white_stained/, [230, 235, 240]], [/tinted_glass/, [60, 50, 70]], [/glass/, [180, 215, 235]],
  [/red_wool|red_banner|red_carpet/, [176, 46, 46]], [/blue_wool|blue_banner|blue_carpet/, [52, 88, 190]], [/yellow_wool|yellow_banner/, [235, 200, 50]], [/light_gray_wool|light_gray_carpet/, [160, 160, 156]], [/white_wool|white_carpet|white_banner/, [236, 236, 232]], [/green_wool|green_carpet/, [80, 110, 40]], [/orange_wool|orange_carpet/, [230, 130, 40]], [/purple_wool|purple_carpet/, [120, 50, 170]], [/brown_wool|brown_carpet/, [110, 74, 44]],
  [/white_concrete/, [230, 232, 234]], [/white_terracotta/, [212, 188, 170]], [/red_terracotta/, [150, 66, 50]], [/orange_terracotta/, [170, 90, 46]], [/brown_terracotta/, [96, 64, 46]], [/light_gray_terracotta/, [140, 112, 104]], [/gray_terracotta|cyan_terracotta/, [78, 70, 66]], [/terracotta/, [160, 100, 74]],
  [/mud_brick/, [140, 106, 80]], [/nether_brick/, [60, 30, 36]], [/^bricks|^brick_/, [150, 80, 64]],
  [/oxidized_.*copper|oxidized_copper/, [82, 162, 132]], [/weathered_.*copper|weathered_copper/, [104, 150, 108]], [/exposed_.*copper|exposed_copper/, [160, 124, 100]], [/copper|lightning_rod/, [196, 108, 76]],
  [/polished_deepslate|deepslate_tile|deepslate_brick/, [64, 64, 70]], [/deepslate/, [80, 80, 86]], [/polished_blackstone|blackstone/, [44, 40, 48]], [/basalt/, [80, 80, 84]], [/tuff/, [108, 108, 100]],
  [/smooth_quartz|quartz/, [236, 232, 226]], [/calcite/, [226, 228, 224]], [/diorite/, [200, 200, 200]], [/snow/, [244, 248, 250]],
  [/iron_block|iron_bars|iron_door|iron_trapdoor|anvil|cauldron|hopper/, [182, 182, 186]], [/chain/, [70, 74, 84]], [/gold_block/, [245, 205, 60]], [/emerald_block/, [60, 190, 90]], [/diamond_block/, [110, 230, 225]], [/coal_block/, [30, 30, 32]], [/redstone_block|redstone_wire/, [180, 20, 10]], [/observer|piston|dispenser|dropper/, [110, 110, 110]],
  [/rail/, [140, 120, 100]],
  [/cherry_leaves/, [242, 170, 200]], [/flowering_azalea/, [110, 150, 70]], [/azalea_leaves/, [90, 140, 60]], [/birch_leaves/, [130, 170, 80]], [/spruce_leaves/, [52, 92, 58]], [/dark_oak_leaves/, [56, 104, 38]], [/leaves/, [72, 140, 46]],
  [/cherry_log|cherry_wood/, [80, 50, 60]], [/cherry/, [228, 180, 176]], [/birch_log|birch_wood/, [220, 218, 205]], [/birch/, [200, 182, 128]],
  [/stripped_dark_oak|dark_oak/, [70, 48, 28]], [/spruce_log|spruce_wood/, [64, 46, 30]], [/spruce|barrel|composter/, [114, 84, 52]], [/mangrove/, [118, 54, 50]],
  [/oak_log|oak_wood/, [110, 86, 52]], [/oak|planks|fence|bookshelf|lectern|crafting|table|scaffolding|bed/, [170, 134, 82]], [/hay/, [210, 180, 60]], [/wheat|carrots|potatoes|beetroots/, [150, 180, 60]], [/farmland/, [92, 60, 36]], [/melon/, [110, 160, 50]], [/pumpkin/, [220, 130, 30]],
  [/moss_block|moss_carpet/, [90, 120, 46]], [/mossy/, [102, 128, 92]], [/cobble/, [122, 122, 120]], [/polished_andesite|andesite/, [148, 148, 148]], [/cracked_stone_brick/, [120, 120, 120]], [/stone_brick|chiseled_stone/, [130, 130, 130]], [/smooth_stone/, [168, 168, 168]], [/furnace|blast|smoker|grindstone|stonecutter/, [100, 100, 100]], [/^stone$|stone/, [122, 122, 122]], [/gravel/, [140, 134, 130]],
  [/red_sand/, [190, 100, 40]], [/sandstone/, [214, 200, 150]], [/sand/, [226, 212, 150]], [/clay/, [160, 166, 178]], [/packed_mud/, [140, 104, 76]], [/mud/, [60, 56, 60]], [/coal_ore/, [100, 100, 100]], [/ladder/, [150, 120, 70]], [/glow_lichen/, [120, 160, 130]],
  [/grass_block/, [96, 160, 62]], [/short_grass|tall_grass|fern/, [86, 150, 56]], [/podzol/, [110, 78, 40]], [/dirt_path/, [170, 142, 90]], [/coarse|rooted|dirt/, [120, 86, 56]],
  [/pink_petals/, [246, 182, 210]], [/poppy|red_tulip|rose_bush/, [210, 40, 40]], [/dandelion|sunflower/, [250, 220, 40]], [/cornflower|blue_orchid/, [90, 110, 230]], [/allium|lilac|peony/, [190, 120, 220]], [/oxeye|azure|white_tulip|lily_of/, [236, 236, 230]], [/sugar_cane|bamboo/, [130, 190, 90]], [/lily_pad/, [60, 130, 50]], [/vine/, [60, 110, 40]],
  [/bell/, [240, 200, 60]], [/banner/, [200, 200, 200]], [/flower_pot|pot/, [140, 80, 60]],
];
const memo = new Map();
export function colorOf(s) {
  if (memo.has(s)) return memo.get(s);
  const n = s.replace(/\[.*/, ""); let v = [150, 120, 160];
  for (const [re, c] of RULES) if (re.test(n)) { v = c; break; }
  memo.set(s, v); return v;
}
const cl = v => Math.max(0, Math.min(255, Math.round(v)));
const shade = (c, f) => [cl(c[0] * f), cl(c[1] * f), cl(c[2] * f)];
const mix = (a, b, t) => [cl(a[0] + (b[0] - a[0]) * t), cl(a[1] + (b[1] - a[1]) * t), cl(a[2] + (b[2] - a[2]) * t)];
const THIN = /torch|lantern|end_rod|chain|rail|vein|carpet|petals|flower|poppy|dandelion|tulip|grass$|fern|button|pressure|sign|banner|lily|vine|cornflower|allium|daisy|orchid|rod|candle|cluster|bud|pot|sapling|bars|pane/;
const SEE = /water|glass|leaves|bars|pane|fence|wall$|chain|rail|vein|carpet|petals|lantern|torch|rod|flower|grass$|fern|slab|stairs|door|trapdoor|^light|cluster|bud|campfire|wheat|carrots|potatoes|beetroots|candle|pot|sapling|button/;

/** Top-down map: S px per block, height shading, water depth tint. */
export function topdown(V, file, S = 2) {
  const W = V.W * S, H = V.D * S, rgb = new Uint8Array(W * H * 3);
  const tops = new Int16Array(V.W * V.D), ids = new Uint16Array(V.W * V.D);
  for (let z = 0; z < V.D; z++) for (let x = 0; x < V.W; x++) {
    let y = V.Y1;
    for (; y >= Y0; y--) { const id = V.get(x, y, z); if (id && V.pal[id] !== "light") break; }
    tops[z * V.W + x] = y; ids[z * V.W + x] = V.get(x, y, z);
  }
  for (let z = 0; z < V.D; z++) for (let x = 0; x < V.W; x++) {
    const i = z * V.W + x, y = tops[i], s = V.pal[ids[i]];
    let c = colorOf(s) || [100, 100, 100];
    if (/^water/.test(s)) { let d = 0; for (let yy = y; yy >= Y0 && /^water/.test(V.name(x, yy, z)); yy--) d++; c = mix([90, 190, 210], [28, 70, 150], Math.min(1, d / 12)); }
    else {
      const nw = x > 0 && z > 0 ? tops[i - V.W - 1] : y;
      c = shade(c, 1 + clampf((y - nw) * 0.07, -0.3, 0.25) + (y - 66) * 0.004);
    }
    c = shade(c, 0.97 + hash(x, z, 3) * 0.06);
    for (let dy = 0; dy < S; dy++) for (let dx = 0; dx < S; dx++) { const p = ((z * S + dy) * W + x * S + dx) * 3; rgb[p] = c[0]; rgb[p + 1] = c[1]; rgb[p + 2] = c[2]; }
  }
  writePng(file, W, H, rgb);
}
const clampf = (v, a, b) => Math.max(a, Math.min(b, v));

/**
 * Isometric view from the south-east (looking north-west) of local box [x0,x1]x[z0,z1], a = half tile width in px.
 * rot: 0 = from SE, 1 = from SW, 2 = from NW, 3 = from NE.
 */
export function iso(V, file, { x0 = 0, z0 = 0, x1 = V.W - 1, z1 = V.D - 1, a = 2, rot = 0, ymin = Y0 + 4, sky = [168, 206, 236] } = {}) {
  const nx = x1 - x0 + 1, nz = z1 - z0 + 1, N = Math.max(nx, nz);
  // rotated local coords (u, v): u grows to screen-right-down, v to screen-left-down
  const map = (u, v) => rot === 0 ? [x0 + u, z0 + v] : rot === 1 ? [x1 - v, z0 + u] : rot === 2 ? [x1 - u, z1 - v] : [x0 + v, z1 - u];
  const U = rot % 2 ? nz : nx, Vv = rot % 2 ? nx : nz;
  let ymax = ymin; for (let z = z0; z <= z1; z++) for (let x = x0; x <= x1; x++) ymax = Math.max(ymax, V.top(x, z));
  const W = (U + Vv) * a + 20, H = Math.round((U + Vv) * a / 2 + (ymax - ymin + 2) * a + 20);
  const buf = new Uint8Array(W * H * 3);
  for (let i = 0; i < W * H; i++) { const t = Math.floor(i / W) / H; const c = mix(sky, [236, 240, 246], t); buf[i * 3] = c[0]; buf[i * 3 + 1] = c[1]; buf[i * 3 + 2] = c[2]; }
  const sx = (u, v) => (u - v) * a + Vv * a + 10, sy = (u, v, y) => (u + v) * a / 2 - (y - ymax - 1) * a + 10;
  const getUV = (u, v, y) => { if (u < 0 || v < 0 || u >= U || v >= Vv) return 0; const [x, z] = map(u, v); return V.get(x, y, z); };
  const opaque = id => id !== 0 && !SEE.test(V.pal[id]);
  for (let s = 0; s < U + Vv - 1; s++) for (let u = Math.max(0, s - Vv + 1); u <= Math.min(U - 1, s); u++) {
    const v = s - u;
    for (let y = ymin; y <= ymax; y++) {
      const id = getUV(u, v, y); if (!id) continue;
      const name = V.pal[id]; const base = colorOf(name); if (!base) continue;
      const up = getUV(u, v, y + 1), e = getUV(u + 1, v, y), so = getUV(u, v + 1, y);
      if (opaque(up) && opaque(e) && opaque(so)) continue;
      const water = /^water/.test(name);
      if (water && /^water/.test(V.pal[up] || "")) continue;
      const thin = THIN.test(name.replace(/\[.*/, ""));
      const k = thin ? 0.32 : 0;
      const P = (du, dv, dy) => [sx(u + du, v + dv), sy(u + du, v + dv, y + dy)];
      const lo = k, hi = 1 - k, top = thin ? 0.75 : /slab\[type=bottom|carpet|petals|rail|snow\[/.test(name) ? 0.5 : 1;
      const c = shade(base, 0.96 + hash3i(u, y, v) * 0.08);
      if (water) { poly(buf, W, H, [P(0, 0, 0.9), P(1, 0, 0.9), P(1, 1, 0.9), P(0, 1, 0.9)], c, 0.75); continue; }
      poly(buf, W, H, [P(lo, lo, top), P(hi, lo, top), P(hi, hi, top), P(lo, hi, top)], shade(c, 1.1));
      if (!opaque(so) || thin) poly(buf, W, H, [P(lo, hi, top), P(hi, hi, top), P(hi, hi, 0), P(lo, hi, 0)], shade(c, 0.8));
      if (!opaque(e) || thin) poly(buf, W, H, [P(hi, lo, top), P(hi, hi, top), P(hi, hi, 0), P(hi, lo, 0)], shade(c, 0.62));
    }
  }
  writePng(file, W, H, buf);
}
const hash3i = (x, y, z) => hash(x * 31 + y * 977, z * 13 - y * 71, 9);
function poly(buf, W, H, pts, c, alpha = 1) {
  let ymin = Infinity, ymax = -Infinity; for (const p of pts) { ymin = Math.min(ymin, p[1]); ymax = Math.max(ymax, p[1]); }
  for (let y = Math.max(0, Math.ceil(ymin - 0.5)); y <= Math.min(H - 1, Math.floor(ymax - 0.5)); y++) {
    const yc = y + 0.5, xs = [];
    for (let i = 0; i < pts.length; i++) { const a = pts[i], b = pts[(i + 1) % pts.length]; if ((a[1] <= yc && b[1] > yc) || (b[1] <= yc && a[1] > yc)) xs.push(a[0] + (yc - a[1]) / (b[1] - a[1]) * (b[0] - a[0])); }
    xs.sort((p, q) => p - q);
    for (let k = 0; k + 1 < xs.length; k += 2) for (let x = Math.max(0, Math.round(xs[k])); x <= Math.min(W - 1, Math.round(xs[k + 1]) - 1); x++) {
      const i = (y * W + x) * 3;
      if (alpha >= 1) { buf[i] = c[0]; buf[i + 1] = c[1]; buf[i + 2] = c[2]; }
      else { buf[i] = cl(buf[i] * (1 - alpha) + c[0] * alpha); buf[i + 1] = cl(buf[i + 1] * (1 - alpha) + c[1] * alpha); buf[i + 2] = cl(buf[i + 2] * (1 - alpha) + c[2] * alpha); }
    }
  }
}
