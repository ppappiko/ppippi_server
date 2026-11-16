// PipiLife armour inventory icons, v5 flat bold style: node dev/art/make-armor-icons-v5.mjs
// 3 classes x tiers T0..T5 x helmet/chest/legs/boots = 72 icons, 32x32 RGBA.
// Writes previews to dev/art/preview/armor_icons_v5/ (+ sheet.png x6) and the client assets
// (dev/pipi-travel/assets/pipitravel/textures/item/armor/*.png and models/item/armor/*.json).
import fs from "node:fs";
import path from "node:path";
import zlib from "node:zlib";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(here, "../..");
const prev = path.join(here, "preview/armor_icons_v5");
const texDir = path.join(repo, "dev/pipi-travel/assets/pipitravel/textures/item/armor");
const modDir = path.join(repo, "dev/pipi-travel/assets/pipitravel/models/item/armor");
for (const d of [prev, texDir, modDir]) fs.mkdirSync(d, { recursive: true });

// ---------- PNG ----------
const crcT = new Uint32Array(256).map((_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const crc = b => { let c = 0xffffffff; for (const x of b) c = crcT[(c ^ x) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
function chunk(type, data) {
  const t = Buffer.from(type, "ascii"), l = Buffer.alloc(4), c = Buffer.alloc(4);
  l.writeUInt32BE(data.length); c.writeUInt32BE(crc(Buffer.concat([t, data])));
  return Buffer.concat([l, t, data, c]);
}
function png(px, w, h) {
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) { raw[y * (w * 4 + 1)] = 0; px.copy(raw, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4); }
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk("IHDR", ihdr), chunk("IDAT", zlib.deflateSync(raw)), chunk("IEND", Buffer.alloc(0))]);
}

// ---------- palettes (same as the approved v5 worn design): ramp = [highlight, base, shadow, outline] ----------
const hex = h => [1, 3, 5].map(i => parseInt(h.slice(i, i + 2), 16));
const ramp = (...a) => a.map(hex);
const mixc = (a, b, k) => a.map((v, i) => Math.round(v + (b[i] - v) * k));
const lighten = (c, n) => c.map(v => Math.min(255, v + n));
const BASEC = [["#c98a52", "#a8683a", "#7a4a28", "#4a2812"], ["#c4cad4", "#9aa1ac", "#727a88", "#363b46"], ["#5a78dc", "#3552b8", "#263d90", "#141c54"],
  ["#9adcff", "#4fb0f0", "#2f85cc", "#174a82"], ["#ff8458", "#e8502e", "#b43218", "#66160a"], ["#a870f4", "#7a35d4", "#5522a8", "#2a1066"]].map(r => ramp(...r));
BASEC.forEach(r => { r[3] = mixc(r[2], r[3], .62); });
const TRIM = [["#f6b070", "#e08a48", "#a85a26", "#5a2e10"], ["#ffffff", "#dfe3ea", "#a0a8b6", "#4c5260"], ["#fff3a8", "#ffd447", "#d49a14", "#7a5208"],
  ["#ffffff", "#f4fbff", "#b8d6ea", "#5a86a8"], ["#fff3a8", "#ffd447", "#d49a14", "#7a5208"], ["#fff3a8", "#ffd447", "#d49a14", "#7a5208"]].map(r => ramp(...r));
const GEM = [null, null, null, ramp("#e8ffff", "#7ef0ff", "#25b8e8", "#1070b0"), ramp("#fff6b0", "#ffb52e", "#e07a10", "#8a4208"), ramp("#ffffff", "#d8fff4", "#7affd8", "#35c0a8")];
const BANDA = BASEC.map(r => [mixc(r[2], r[3], .2), mixc(r[2], r[3], .45), mixc(r[2], r[3], .75), r[3]]);
const FEATHER = [hex("#d8c8a8"), hex("#e6eaee"), hex("#f4f4f4"), hex("#9ef4ff"), hex("#ff8a4a"), hex("#ffffff")];
const DK = [hex("#5a4d66"), hex("#3d3345"), hex("#2a2230"), hex("#1a1420")];
const BLADE = [[255, 255, 255], [226, 220, 246], [190, 184, 224], [104, 98, 150]];
const TIERS = 6, CLASSES = ["warrior", "archer", "mage"], SLOTS = ["helmet", "chest", "legs", "boots"];

// ---------- mask toolkit ----------
const N = 32;
const mk = () => new Uint8Array(N * N);
const at = (m, x, y) => x >= 0 && y >= 0 && x < N && y < N && m[y * N + x] === 1;
const or = (...ms) => { const o = mk(); for (const m of ms) for (let i = 0; i < N * N; i++) if (m[i]) o[i] = 1; return o; };
const and = (a, b) => { const o = mk(); for (let i = 0; i < N * N; i++) if (a[i] && b[i]) o[i] = 1; return o; };
const sub = (a, b) => { const o = mk(); for (let i = 0; i < N * N; i++) if (a[i] && !b[i]) o[i] = 1; return o; };
const mir = m => { const o = mk(); for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) if (m[y * N + x]) o[y * N + (N - 1 - x)] = 1; return o; };
const sym = m => or(m, mir(m));
const inPoly = (pts, x, y) => {
  let inside = false;
  for (let i = 0, j = pts.length - 1; i < pts.length; j = i++) {
    const [xi, yi] = pts[i], [xj, yj] = pts[j];
    if ((yi > y) !== (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside;
  }
  return inside;
};
const poly = pts => { const m = mk(); for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) if (inPoly(pts, x + .5, y + .5)) m[y * N + x] = 1; return m; };
const rect = (x0, y0, x1, y1) => { const m = mk(); for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) if (x >= 0 && y >= 0 && x < N && y < N) m[y * N + x] = 1; return m; };
const ell = (cx, cy, rx, ry) => { const m = mk(); for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) { const dx = (x + .5 - cx) / rx, dy = (y + .5 - cy) / ry; if (dx * dx + dy * dy <= 1) m[y * N + x] = 1; } return m; };
// thick polyline; rf(t) = radius at t in 0..1
function stroke(path, rf) {
  const m = mk(); const segs = path.length - 1; const steps = 90;
  for (let i = 0; i <= steps; i++) {
    const t = i / steps, s = t * segs, k = Math.min(segs - 1, Math.floor(s)), u = s - k;
    const cx = path[k][0] + (path[k + 1][0] - path[k][0]) * u, cy = path[k][1] + (path[k + 1][1] - path[k][1]) * u, r = rf(t);
    for (let y = Math.floor(cy - r - 1); y <= Math.ceil(cy + r + 1); y++) for (let x = Math.floor(cx - r - 1); x <= Math.ceil(cx + r + 1); x++)
      if (x >= 0 && y >= 0 && x < N && y < N && (x + .5 - cx) ** 2 + (y + .5 - cy) ** 2 <= r * r) m[y * N + x] = 1;
  }
  return m;
}
function line(x0, y0, x1, y1) {
  const m = mk(); const n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0), 1);
  for (let i = 0; i <= n; i++) { const x = Math.round(x0 + (x1 - x0) * i / n), y = Math.round(y0 + (y1 - y0) * i / n); if (x >= 0 && y >= 0 && x < N && y < N) m[y * N + x] = 1; }
  return m;
}
const dots = list => { const m = mk(); list.forEach(([x, y]) => { if (x >= 0 && y >= 0 && x < N && y < N) m[y * N + x] = 1; }); return m; };

// ---------- canvas / painter ----------
let cv, TI, OL, MAT;
function begin(tier) {
  cv = new Array(N * N).fill(null); TI = tier;
  OL = BASEC[tier][3];
  MAT = { B: BASEC[tier], A: BANDA[tier], T: TRIM[tier], L: BASEC[0], D: DK, K: BLADE,
    G: GEM[tier] || BASEC[tier], F: [lighten(FEATHER[tier], 50), FEATHER[tier], FEATHER[tier].map(v => Math.round(v * .72)), FEATHER[tier].map(v => Math.round(v * .42))] };
}
// flat two-tone fill: dark inner edge, base, 1px shadow bottom/right, small highlight top-left
function paint(m, mat, o = {}) {
  const r = typeof mat === "string" ? MAT[mat] : mat;
  const ol = o.ol !== false;
  const inn = (x, y) => at(m, x, y);
  const edge = (x, y) => inn(x, y) && !(inn(x - 1, y) && inn(x + 1, y) && inn(x, y - 1) && inn(x, y + 1));
  const solid = (x, y) => inn(x, y) && !(ol && edge(x, y));
  const out = (x, y) => !solid(x, y);
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    if (!inn(x, y)) continue;
    let c;
    if (ol && edge(x, y)) c = o.olc || OL;
    else if (out(x + 1, y) || out(x, y + 1)) c = r[2];
    else if ((out(x, y - 1) && (out(x - 1, y) || out(x - 2, y))) || (out(x - 1, y) && out(x, y - 2))) c = r[0];
    else c = r[1];
    cv[y * N + x] = c;
  }
}
const flat = (m, c) => { for (let i = 0; i < N * N; i++) if (m[i]) cv[i] = typeof c === "string" ? MAT[c][1] : c; };
const gem = (x, y, w = 2, h = 2) => { // small gem: highlight / base / shadow, no outline
  const r = MAT.G;
  for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) cv[(y + j) * N + x + i] = (i === 0 && j === 0) ? r[0] : (i === w - 1 && j === h - 1) ? r[2] : r[1];
};
const sparkle = () => { if (TI < 5) return; const g = MAT.G; for (const [x, y] of [[3, 2], [2, 3], [3, 3], [4, 3], [3, 4]]) if (!cv[y * N + x]) cv[y * N + x] = (x === 3 && y === 3) ? [255, 255, 255] : g[0]; };
const tierGem = (x, y, w = 2, h = 2) => { if (TI >= 3) gem(x, y, w, h); else { const r = MAT.B; for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) cv[(y + j) * N + x + i] = (i === 0 && j === 0) ? r[0] : r[(i === w - 1 && j === h - 1) ? 2 : 1]; } };

// ---------- pieces ----------
const P = {
  warrior: {
    helmet() {
      const dome = or(ell(16, 15, 10, 8), rect(6, 15, 25, 23));
      const notch = rect(11, 18, 20, 25);
      paint(rect(11, 17, 20, 23), "D"); // dark face opening
      paint(poly([[7, 21], [24, 21], [23, 28], [8, 28]]), "A"); // neck guard
      paint(sub(dome, notch), "B");
      paint(sub(rect(6, 16, 25, 17), notch), "T", { ol: false }); // brow band
      paint(rect(11, 17, 20, 20), "D", { ol: false });
      flat(rect(11, 17, 20, 17), DK[2]);
      // ear discs with gem
      for (const [cx] of [[5.5], [26.5]]) { paint(ell(cx, 19.5, 3.4, 3.4), "T"); paint(ell(cx, 19.5, 1.9, 1.9), "B", { ol: false }); }
      cv[19 * N + 5] = MAT.G[1]; cv[19 * N + 26] = MAT.G[1];
      if (TI >= 3) { cv[19 * N + 5] = MAT.G[0]; cv[19 * N + 26] = MAT.G[0]; }
      // swept blade crest: rises from the crown, sweeps back to the right in a long trailing tip
      paint(stroke([[15.5, 10], [15, 5.5], [17.5, 2.6], [23, 1.8], [30, 4.5]], t => 3.2 * (1 - t) ** 0.85 + 0.3), "K", { olc: mixc(BLADE[3], OL, .5) });
      paint(rect(13, 9, 18, 11), "T"); // crest socket
      tierGem(15, 12, 2, 2); // forehead gem
      if (TI >= 3) gem(15, 16, 2, 2);
      sparkle();
    },
    chest() {
      if (TI >= 3) paint(poly([[7, 10], [24, 10], [29, 29], [3, 29]]), "A"); // cape
      paint(rect(4, 13, 9, 21), "B"); paint(rect(22, 13, 27, 21), "B"); // sleeves
      paint(rect(4, 19, 9, 21), "T", { ol: false }); paint(rect(22, 19, 27, 21), "T", { ol: false });
      paint(poly([[9, 7], [22, 7], [22, 26], [24, 28], [7, 28], [9, 26]]), "B");
      paint(ell(16, 7.5, 4.2, 3.2), "D");
      paint(rect(10, 23, 21, 24), "T", { ol: false }); paint(rect(15, 22, 16, 25), "A", { ol: false });
      paint(poly([[16, 11], [20, 15.5], [16, 20], [12, 15.5]]), "T");
      paint(poly([[16, 13], [18, 15.5], [16, 18], [14, 15.5]]), "G", { ol: false });
      // pauldrons
      for (const cx of [6, 26]) { paint(ell(cx, 11, 5, 4.6), "T"); paint(ell(cx, 11.5, 3.3, 2.9), "B", { ol: false }); }
      sparkle();
    },
    legs() {
      paint(poly([[8, 13], [15, 13], [14.5, 28], [9, 28]]), "B"); paint(poly([[17, 13], [24, 13], [23, 28], [17.5, 28]]), "B");
      paint(rect(8, 17, 14, 21), "T"); paint(rect(17, 17, 23, 21), "T");
      paint(rect(10, 18, 12, 20), "B", { ol: false }); paint(rect(19, 18, 21, 20), "B", { ol: false });
      paint(rect(9, 25, 14, 27), "A", { ol: false }); paint(rect(17, 25, 22, 27), "A", { ol: false });
      paint(rect(7, 4, 24, 13), "B"); // pelvis plate + belt
      paint(rect(7, 6, 24, 8), "A", { ol: false }); paint(rect(7, 9, 24, 9), "T", { ol: false });
      paint(rect(14, 5, 17, 9), "T");
      tierGem(15, 6, 2, 2); if (TI >= 3) gem(15, 6, 2, 2);
      sparkle();
    },
    boots() {
      const boot = or(rect(6, 6, 13, 19), poly([[2, 19], [3, 17], [13, 16], [14, 25], [2, 25]]));
      for (const m of [boot, mir(boot)]) paint(m, "B");
      for (const m of [rect(2, 24, 13, 25), mir(rect(2, 24, 13, 25))]) paint(and(m, or(boot, mir(boot))), "A", { ol: false });
      for (const m of [rect(2, 19, 5, 23), mir(rect(2, 19, 5, 23))]) paint(m, "T", { ol: false });
      for (const m of [rect(6, 6, 13, 8), mir(rect(6, 6, 13, 8))]) paint(m, "T");
      for (const m of [rect(6, 15, 13, 15), mir(rect(6, 15, 13, 15))]) paint(m, "T", { ol: false });
      tierGem(8, 10, 3, 3); if (TI >= 3) gem(8, 10, 3, 3);
      for (const x of [8, 21]) { }
      if (TI >= 3) gem(21, 10, 3, 3); else tierGem(21, 10, 3, 3);
      sparkle();
    },
  },
  archer: {
    helmet() {
      paint(ell(16, 20.5, 14.5, 5.5), "T");
      paint(ell(16, 20, 12.5, 3.6), "B", { ol: false });
      const crown = or(ell(16, 11.5, 7, 5), rect(9, 11, 22, 19));
      paint(crown, "B");
      paint(and(rect(9, 15, 22, 18), crown), "A", { ol: false });
      paint(rect(9, 15, 22, 15), "T", { ol: false });
      paint(rect(14, 14, 17, 19), "T");
      tierGem(15, 15, 2, 3); if (TI >= 3) gem(15, 15, 2, 3);
      // ranger feather
      const path = [[21, 16], [25, 12], [27, 7], [25, 2.5]];
      paint(stroke(path, t => 0.7 + 2.3 * Math.sin(Math.PI * Math.min(1, t * 0.95 + 0.04)) ** 0.8), "F");
      flat(stroke([[21.5, 15], [25.2, 11.5], [26.6, 7], [25, 3.5]], () => 0.3), MAT.F[0]);
      sparkle();
    },
    chest() {
      // quiver behind the right shoulder + arrow feathers
      paint(stroke([[24, 4], [21, 13]], () => 2.4), "L");
      paint(rect(22, 1, 23, 5), "F", { ol: false }); paint(rect(25, 2, 26, 6), "F", { ol: false });
      if (TI >= 3) paint(poly([[7, 10], [24, 10], [29, 29], [3, 29]]), "A");
      paint(rect(4, 13, 9, 21), "B"); paint(rect(22, 13, 27, 21), "B");
      paint(rect(4, 19, 9, 21), "A", { ol: false }); paint(rect(22, 19, 27, 21), "A", { ol: false });
      const torso = poly([[10, 8], [21, 8], [22, 27], [9, 27]]);
      paint(torso, "B");
      paint(ell(16, 8, 6, 3.4), "B"); paint(ell(16, 8.3, 3.8, 2.2), "D");
      paint(rect(9, 24, 22, 25), "A", { ol: false });
      paint(and(stroke([[10, 9], [21, 25]], () => 1.4), torso), "T", { ol: false }); // quiver strap
      paint(poly([[16, 14], [19, 17], [16, 20], [13, 17]]), "T");
      gem(15, 16, 2, 2); if (TI < 3) tierGem(15, 16, 2, 2);
      for (const cx of [6, 26]) { paint(ell(cx, 11, 4.4, 4), "B"); paint(ell(cx, 11.5, 2.6, 2.2), "A", { ol: false }); }
      sparkle();
    },
    legs() {
      paint(poly([[8, 13], [15, 13], [14.5, 28], [9, 28]]), "B"); paint(poly([[17, 13], [24, 13], [23, 28], [17.5, 28]]), "B");
      paint(rect(8, 18, 14, 20), "A"); paint(rect(17, 18, 23, 20), "A");
      paint(rect(9, 25, 14, 27), "T", { ol: false }); paint(rect(17, 25, 22, 27), "T", { ol: false });
      paint(rect(18, 13, 23, 17), "L"); flat(rect(18, 14, 23, 14), MAT.T[1]); // thigh pouch
      paint(rect(7, 4, 24, 13), "B");
      paint(rect(7, 5, 24, 7), "T", { ol: false }); paint(rect(7, 8, 24, 9), "A", { ol: false });
      paint(rect(14, 4, 17, 9), "T");
      tierGem(15, 5, 2, 3); if (TI >= 3) gem(15, 5, 2, 3);
      sparkle();
    },
    boots() {
      const boot = or(rect(6, 8, 13, 19), poly([[3, 19], [4, 17], [13, 16], [14, 25], [3, 25]]));
      for (const m of [boot, mir(boot)]) paint(m, "B");
      for (const m of [rect(3, 24, 13, 25), mir(rect(3, 24, 13, 25))]) paint(and(m, or(boot, mir(boot))), "L", { ol: false });
      for (const m of [rect(5, 5, 14, 9), mir(rect(5, 5, 14, 9))]) { paint(m, "T"); }
      for (const m of [rect(6, 7, 13, 7), mir(rect(6, 7, 13, 7))]) flat(m, MAT.A[1]);
      for (const m of [line(7, 12, 12, 12), line(7, 15, 12, 15)]) { flat(m, MAT.A[2]); flat(mir(m), MAT.A[2]); }
      if (TI >= 3) { gem(8, 17, 2, 2); gem(22, 17, 2, 2); } else { tierGem(8, 17, 2, 2); tierGem(22, 17, 2, 2); }
      sparkle();
    },
  },
  mage: {
    helmet() {
      paint(ell(16, 24, 14.5, 4.8), "T");
      paint(ell(16, 23.8, 12.6, 3.2), "B", { ol: false });
      const cone = and(stroke([[16, 23], [16.5, 15], [18, 9.5], [22, 5.5], [26.5, 4.5], [30, 7]], t => 8.2 * (1 - t) ** 1.05 + 0.4), rect(0, 0, 31, 22));
      paint(cone, "B");
      paint(and(rect(0, 15, 31, 18), cone), "A", { ol: false }); paint(and(rect(0, 15, 31, 15), cone), "T", { ol: false });
      paint(rect(14, 14, 17, 19), "T");
      tierGem(15, 15, 2, 3); if (TI >= 3) gem(15, 15, 2, 3);
      // tip star (T5 glows)
      if (TI >= 5) { for (const [x, y] of [[29, 3], [28, 4], [29, 4], [30, 4], [29, 5]]) cv[y * N + x] = (x === 29 && y === 4) ? [255, 255, 255] : MAT.G[0]; }
      else if (TI >= 2) flat(dots([[29, 7]]), MAT.G[1]);
      sparkle();
    },
    chest() {
      if (TI >= 3) paint(poly([[8, 10], [23, 10], [29, 29], [3, 29]]), "A");
      paint(stroke([[9, 9], [4.5, 22]], () => 3.1), "B"); paint(stroke([[22, 9], [26.5, 22]], () => 3.1), "B");
      paint(and(rect(0, 20, 31, 24), or(stroke([[9, 9], [4.5, 22]], () => 3.1), stroke([[22, 9], [26.5, 22]], () => 3.1))), "T", { ol: false });
      const robe = poly([[10, 8], [21, 8], [22, 15], [25, 29], [6, 29], [9, 15]]);
      paint(robe, "B");
      paint(poly([[12, 7], [20, 7], [16, 14]]), "D");
      paint(poly([[12, 7], [13, 7], [16, 12], [15, 14], [11, 8]]), "T", { ol: false }); paint(poly([[19, 7], [20, 7], [21, 8], [17, 14], [16, 12]]), "T", { ol: false });
      paint(and(poly([[14, 14], [18, 14], [19, 29], [13, 29]]), robe), "A", { ol: false });
      paint(and(rect(0, 26, 31, 28), robe), "T", { ol: false });
      paint(rect(13, 15, 18, 17), "T");
      tierGem(15, 15, 2, 2); if (TI >= 3) gem(15, 15, 2, 2);
      for (const cx of [8, 24]) { paint(ell(cx, 9.5, 3.4, 3), "B"); }
      sparkle();
    },
    legs() {
      const skirt = poly([[9, 9], [23, 9], [27, 28], [19.5, 28], [16, 22], [12.5, 28], [5, 28]]);
      paint(skirt, "B");
      paint(and(poly([[9, 9], [23, 9], [27, 28], [19.5, 28], [16, 22], [12.5, 28], [5, 28]]), rect(0, 25, 31, 27)), "T", { ol: false });
      paint(and(poly([[14, 12], [18, 12], [19, 22], [16, 22], [13, 22]]), skirt), "A", { ol: false });
      paint(rect(7, 4, 24, 11), "B");
      paint(rect(7, 5, 24, 7), "T", { ol: false }); paint(rect(7, 8, 24, 8), "A", { ol: false });
      paint(rect(14, 4, 17, 9), "T");
      tierGem(15, 5, 2, 3); if (TI >= 3) gem(15, 5, 2, 3);
      sparkle();
    },
    boots() {
      const boot = or(rect(6, 8, 13, 19), poly([[3, 19], [4, 17], [13, 16], [14, 25], [3, 25]]), poly([[3, 19], [1, 16], [1, 13], [3, 14], [5, 17]]));
      for (const m of [boot, mir(boot)]) paint(m, "B");
      for (const m of [rect(3, 24, 13, 25), mir(rect(3, 24, 13, 25))]) paint(and(m, or(boot, mir(boot))), "A", { ol: false });
      for (const m of [rect(5, 5, 14, 9), mir(rect(5, 5, 14, 9))]) paint(m, "T");
      if (TI >= 3) { gem(8, 12, 2, 3); gem(22, 12, 2, 3); } else { tierGem(8, 12, 2, 3); tierGem(22, 12, 2, 3); }
      sparkle();
    },
  },
};

// ---------- output ----------
function render() {
  const buf = Buffer.alloc(N * N * 4);
  for (let i = 0; i < N * N; i++) { const c = cv[i]; if (!c) continue; buf[i * 4] = c[0]; buf[i * 4 + 1] = c[1]; buf[i * 4 + 2] = c[2]; buf[i * 4 + 3] = 255; }
  return buf;
}
const icons = {};
for (const c of CLASSES) for (let t = 0; t < TIERS; t++) for (const s of SLOTS) {
  begin(t); P[c][s]();
  const name = `${c}_t${t}_${s}`; icons[name] = render();
  const file = png(icons[name], N, N);
  fs.writeFileSync(path.join(prev, name + ".png"), file);
  fs.writeFileSync(path.join(texDir, name + ".png"), file);
  fs.writeFileSync(path.join(modDir, name + ".json"), JSON.stringify({ parent: "minecraft:item/generated", textures: { layer0: `pipitravel:item/armor/${name}` } }) + "\n");
}

// contact sheets (x6)
const SC = 6, CELL = N * SC + 8;
function sheet(cols, rowsN, place, file) {
  const W = cols * CELL + 8, H = rowsN * CELL + 8, b = Buffer.alloc(W * H * 4);
  for (let i = 0; i < W * H; i++) { b[i * 4] = 70; b[i * 4 + 1] = 76; b[i * 4 + 2] = 98; b[i * 4 + 3] = 255; }
  for (let r = 0; r < rowsN; r++) for (let q = 0; q < cols; q++) {
    const name = place(r, q); if (!name) continue;
    const ox = 8 + q * CELL, oy = 8 + r * CELL, ic = icons[name];
    for (let y = 0; y < N * SC; y++) for (let x = 0; x < N * SC; x++) {
      const k = ((y / SC | 0) * N + (x / SC | 0)) * 4, o = ((oy + y) * W + ox + x) * 4;
      const bg = ((x / 6 | 0) + (y / 6 | 0)) % 2 ? 139 : 129;
      const a = ic[k + 3] / 255;
      b[o] = ic[k + 3] ? ic[k] : bg; b[o + 1] = ic[k + 3] ? ic[k + 1] : bg; b[o + 2] = ic[k + 3] ? ic[k + 2] : bg + 6; b[o + 3] = 255;
    }
  }
  fs.writeFileSync(path.join(prev, file), png(b, W, H));
}
sheet(12, TIERS, (r, q) => `${CLASSES[q / 4 | 0]}_t${r}_${SLOTS[q % 4]}`, "sheet.png");
CLASSES.forEach(c => sheet(4, TIERS, (r, q) => `${c}_t${r}_${SLOTS[q]}`, `sheet_${c}.png`));
console.log("icons:", Object.keys(icons).length);
