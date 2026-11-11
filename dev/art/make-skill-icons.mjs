// Generates the skill icons (32x32): node dev/art/make-skill-icons.mjs
import fs from "node:fs";
import path from "node:path";
import zlib from "node:zlib";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const out = path.resolve(here, "../pipi-travel/assets/pipitravel/textures/gui/skills");
fs.mkdirSync(out, { recursive: true });

const crcT = new Uint32Array(256).map((_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const crc = b => { let c = 0xffffffff; for (const x of b) c = crcT[(c ^ x) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
function chunk(type, data) {
  const t = Buffer.from(type, "ascii"), l = Buffer.alloc(4), c = Buffer.alloc(4);
  l.writeUInt32BE(data.length); c.writeUInt32BE(crc(Buffer.concat([t, data])));
  return Buffer.concat([l, t, data, c]);
}
const N = 32;
function png(px) {
  const raw = Buffer.alloc((N * 4 + 1) * N);
  for (let y = 0; y < N; y++) { raw[y * (N * 4 + 1)] = 0; for (let x = 0; x < N * 4; x++) raw[y * (N * 4 + 1) + 1 + x] = px[y * N * 4 + x]; }
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(N, 0); ihdr.writeUInt32BE(N, 4); ihdr[8] = 8; ihdr[9] = 6;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk("IHDR", ihdr), chunk("IDAT", zlib.deflateSync(raw)), chunk("IEND", Buffer.alloc(0))]);
}
const hex = h => [1, 3, 5].map(i => parseInt(h.slice(i, i + 2), 16));

let P;
const set = (x, y, c) => { x = Math.round(x); y = Math.round(y); if (x < 0 || y < 0 || x >= N || y >= N) return; const i = (y * N + x) * 4; P[i] = c[0]; P[i + 1] = c[1]; P[i + 2] = c[2]; P[i + 3] = 255; };
const rect = (x0, y0, x1, y1, c) => { for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) set(x, y, c); };
const disc = (cx, cy, r, c) => { for (let y = -r - 1; y <= r + 1; y++) for (let x = -r - 1; x <= r + 1; x++) if (x * x + y * y <= r * r + r * .6) set(cx + x, cy + y, c); };
const ring = (cx, cy, r, t, c, a0 = 0, a1 = 360) => {
  for (let y = -r - 1; y <= r + 1; y++) for (let x = -r - 1; x <= r + 1; x++) {
    const d = Math.hypot(x, y); if (d > r + .5 || d < r - t + .5) continue;
    let a = Math.atan2(y, x) * 180 / Math.PI; if (a < 0) a += 360;
    const inside = a0 <= a1 ? (a >= a0 && a <= a1) : (a >= a0 || a <= a1);
    if (inside) set(cx + x, cy + y, c);
  }
};
const line = (x0, y0, x1, y1, t, c) => {
  const n = Math.ceil(Math.hypot(x1 - x0, y1 - y0) * 2) + 1;
  for (let i = 0; i <= n; i++) { const u = i / n; const x = x0 + (x1 - x0) * u, y = y0 + (y1 - y0) * u; if (t <= 1) set(x, y, c); else disc(Math.round(x), Math.round(y), (t - 1) / 2, c); }
};
const poly = (pts, c) => {
  const xs = pts.map(p => p[0]), ys = pts.map(p => p[1]);
  for (let y = Math.floor(Math.min(...ys)); y <= Math.ceil(Math.max(...ys)); y++) for (let x = Math.floor(Math.min(...xs)); x <= Math.ceil(Math.max(...xs)); x++) {
    let inside = false;
    for (let i = 0, j = pts.length - 1; i < pts.length; j = i++) {
      const [xi, yi] = pts[i], [xj, yj] = pts[j];
      if ((yi > y) !== (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside;
    }
    if (inside) set(x, y, c);
  }
};
const arrow = (x0, y0, x1, y1, t, c, head = 6) => {
  line(x0, y0, x1, y1, t, c);
  const a = Math.atan2(y1 - y0, x1 - x0), L = head;
  poly([[x1 + Math.cos(a) * 2, y1 + Math.sin(a) * 2], [x1 - Math.cos(a) * L + Math.cos(a + 1.57) * L * .55, y1 - Math.sin(a) * L + Math.sin(a + 1.57) * L * .55], [x1 - Math.cos(a) * L + Math.cos(a - 1.57) * L * .55, y1 - Math.sin(a) * L + Math.sin(a - 1.57) * L * .55]], c);
};

const CLASS = {
  w: { bg: hex("#4a1c14"), bg2: hex("#6e2a1a"), edge: hex("#ff8a3d"), edge2: hex("#9c3a1c") },
  a: { bg: hex("#16381f"), bg2: hex("#235230"), edge: hex("#7be08a"), edge2: hex("#2f7a44") },
  m: { bg: hex("#2c1a4d"), bg2: hex("#43297a"), edge: hex("#b98cff"), edge2: hex("#5f3fa6") },
  c: { bg: hex("#173b5c"), bg2: hex("#235c8a"), edge: hex("#7ccbff"), edge2: hex("#2f78b0") },
};
function frame(k) {
  const s = CLASS[k];
  rect(0, 0, 31, 31, s.edge2); rect(1, 1, 30, 30, s.edge); rect(2, 2, 29, 29, s.edge2); rect(3, 3, 28, 28, s.bg);
  for (let y = 3; y <= 28; y++) for (let x = 3; x <= 28; x++) if ((x + y) % 2 === 0 && y > 16) set(x, y, s.bg2);
  for (const [x, y] of [[0, 0], [31, 0], [0, 31], [31, 31]]) { const i = (y * N + x) * 4; P[i + 3] = 0; }
}
const WH = hex("#ffffff"), RED = hex("#ff4a3a"), DRED = hex("#b3231a"), OR = hex("#ffa63d"), YEL = hex("#ffe27a"), STEEL = hex("#d8e2ee"), BRN = hex("#8a5a2b");
const GRN = hex("#6fe07f"), DGRN = hex("#2f9a4a"), PUR = hex("#b57cff"), DPUR = hex("#7a45d6"), CY = hex("#7ff0ff"), ICE = hex("#bfeeff"), SKY = hex("#9fe0ff");

const icons = {
  w_basic() { // red crescent slash
    disc(16, 16, 11, RED); disc(21, 13, 10, CLASS.w.bg);
    line(7, 24, 25, 6, 1, WH); line(9, 25, 26, 9, 1, hex("#ffd0c8"));
  },
  w_rush() { // arrow-shaped blade
    line(5, 12, 12, 12, 1, hex("#ff8a7a")); line(4, 16, 11, 16, 1, hex("#ff8a7a")); line(5, 20, 12, 20, 1, hex("#ff8a7a"));
    poly([[26, 16], [15, 8], [15, 24]], STEEL); poly([[26, 16], [15, 12], [15, 20]], WH);
    rect(12, 14, 16, 18, RED);
  },
  w_whirl() {
    ring(16, 16, 10, 3, RED, 0, 290); ring(16, 16, 6, 2, OR, 40, 330);
    poly([[24, 20], [28, 12], [19, 14]], WH);
  },
  w_split() { // cracked ground + shock
    rect(4, 22, 27, 27, hex("#7a6a5a")); rect(4, 22, 27, 23, hex("#a89884"));
    line(16, 5, 14, 12, 2, OR); line(14, 12, 18, 18, 2, OR); line(18, 18, 15, 25, 2, RED);
    line(14, 12, 8, 14, 1, YEL); line(18, 18, 24, 17, 1, YEL);
    for (const [x, y] of [[9, 8], [23, 9], [7, 18], [25, 21]]) rect(x, y, x + 1, y + 1, hex("#ffcf9a"));
  },
  w_heal() { // flame shield
    poly([[6, 6], [26, 6], [26, 17], [16, 28], [6, 17]], DRED); poly([[8, 8], [24, 8], [24, 16], [16, 25], [8, 16]], hex("#7a2118"));
    poly([[16, 8], [21, 15], [20, 22], [16, 24], [12, 22], [11, 15], [14, 16]], OR);
    poly([[16, 14], [19, 19], [18, 22], [16, 23], [14, 22], [13, 19]], YEL);
  },
  w_ult() { // giant sword striking down
    poly([[16, 28], [12, 14], [12, 4], [20, 4], [20, 14]], STEEL); poly([[16, 28], [16, 14], [16, 4], [20, 4], [20, 14]], hex("#aab8c8"));
    rect(8, 14, 23, 16, hex("#ffcf4a")); rect(14, 17, 17, 19, BRN);
    line(5, 8, 9, 12, 1, YEL); line(26, 8, 22, 12, 1, YEL); line(4, 20, 8, 20, 1, YEL); line(27, 20, 23, 20, 1, YEL);
    rect(9, 28, 22, 28, RED);
  },
  a_basic() { arrow(6, 25, 25, 6, 2, hex("#e8f4e0"), 8); line(5, 21, 8, 24, 1, GRN); line(8, 24, 4, 24, 1, GRN); line(9, 28, 6, 25, 1, GRN); },
  a_triple() {
    for (const dy of [-6, 0, 6]) { arrow(6, 16 + dy, 25, 16 + dy, 1, hex("#e8f4e0"), 5); line(5, 16 + dy - 1, 8, 16 + dy, 1, GRN); line(5, 16 + dy + 1, 8, 16 + dy, 1, GRN); }
  },
  a_pierce() {
    // firework arrow: diagonal shaft with a red rocket tube and a colourful burst
    line(4, 27, 19, 12, 1, hex("#e8f4e0")); line(4, 27, 2, 29, 1, GRN);
    line(6, 22, 9, 25, 1, GRN); line(9, 19, 12, 22, 1, GRN);
    line(13, 18, 17, 14, 3, RED); line(15, 16, 17, 14, 1, WH);
    poly([[20, 11], [17, 11], [20, 14]], STEEL);
    for (let k = 0; k < 8; k++) { const a = k * Math.PI / 4; const c = [YEL, OR, CY, GRN][k % 4]; line(24 + Math.cos(a) * 2, 8 + Math.sin(a) * 2, 24 + Math.cos(a) * 6, 8 + Math.sin(a) * 6, 1, c); }
    disc(24, 8, 1, WH);
    rect(27, 22, 28, 23, YEL); rect(8, 7, 9, 8, CY);
  },
  a_rain() {
    disc(11, 10, 5, hex("#c8d8e8")); disc(18, 8, 6, hex("#dce8f4")); disc(23, 11, 4, hex("#c8d8e8")); rect(8, 11, 25, 14, hex("#c8d8e8"));
    for (const x of [8, 14, 20, 25]) arrow(x, 16, x - 2, 27, 1, GRN, 4);
  },
  a_heal() {
    poly([[16, 5], [25, 12], [22, 24], [16, 28], [10, 24], [7, 12]], GRN);
    poly([[16, 5], [25, 12], [22, 24], [16, 28]], DGRN);
    line(16, 8, 16, 27, 1, hex("#d6ffd9")); line(16, 15, 11, 12, 1, hex("#d6ffd9")); line(16, 20, 21, 17, 1, hex("#d6ffd9"));
  },
  a_ult() {
    arrow(4, 16, 29, 16, 2, hex("#e8f4e0"), 7);
    ring(10, 16, 7, 1, SKY, 250, 110); ring(17, 16, 8, 1, SKY, 70, 290); ring(23, 16, 6, 1, SKY, 250, 110);
    line(6, 6, 14, 6, 1, WH); line(18, 26, 27, 26, 1, WH);
  },
  m_basic() { disc(16, 16, 9, DPUR); disc(16, 16, 7, PUR); disc(13, 13, 3, hex("#e6d2ff")); rect(12, 12, 13, 13, WH); },
  m_seek() {
    disc(22, 11, 6, DPUR); disc(22, 11, 4, PUR); disc(20, 9, 2, hex("#e6d2ff"));
    line(4, 27, 8, 22, 2, DPUR); line(8, 22, 13, 20, 2, PUR); line(13, 20, 17, 16, 2, PUR);
    line(5, 21, 9, 18, 1, hex("#e6d2ff"));
  },
  m_frost() {
    poly([[27, 5], [20, 7], [6, 22], [9, 25], [24, 12]], ICE); poly([[27, 5], [20, 7], [6, 22], [9, 25]], hex("#6ec8f0"));
    line(5, 23, 4, 28, 2, hex("#6ec8f0"));
    for (const [x, y] of [[10, 8], [22, 22], [26, 17]]) { line(x - 2, y, x + 2, y, 1, WH); line(x, y - 2, x, y + 2, 1, WH); }
  },
  m_thunder() { // lightning bolt
    poly([[19, 4], [9, 17], [15, 17], [12, 28], [24, 13], [17, 13], [21, 4]], hex("#ffe27a"));
    poly([[19, 6], [12, 16], [17, 16], [14, 25], [22, 14], [16, 14], [19, 6]], WH);
    for (const [x, y] of [[6, 8], [26, 22], [7, 24], [25, 7]]) { line(x - 1, y, x + 1, y, 1, PUR); line(x, y - 1, x, y + 1, 1, PUR); }
  },
  m_heal() {
    ring(16, 16, 12, 1, CY, 0, 360);
    rect(13, 7, 18, 24, WH); rect(7, 13, 24, 18, WH); rect(14, 8, 17, 23, CY); rect(8, 14, 23, 17, CY);
  },
  w_shield() { // steel bubble with a shield emblem
    ring(16, 16, 12, 2, hex("#8c9bb0")); disc(16, 16, 10, hex("#2c3544")); ring(16, 16, 10, 1, hex("#c9d4e4"));
    poly([[10, 9], [22, 9], [22, 17], [16, 24], [10, 17]], hex("#b4c0d2")); poly([[12, 11], [20, 11], [20, 16], [16, 21], [12, 16]], hex("#e6edf6"));
    rect(15, 11, 16, 20, hex("#8c9bb0")); rect(12, 14, 20, 15, hex("#8c9bb0"));
  },
  a_shield() { // green wind ring
    ring(16, 16, 12, 2, GRN); ring(16, 16, 8, 2, DGRN); ring(16, 16, 4, 1, hex("#d6ffe0"));
    line(5, 12, 12, 10, 1, WH); line(20, 22, 27, 20, 1, WH); line(8, 25, 14, 23, 1, hex("#d6ffe0")); line(18, 8, 25, 9, 1, hex("#d6ffe0"));
  },
  m_shield() { // purple hex barrier
    const hexa = (cx, cy, r, t, c) => { const pts = []; for (let k = 0; k < 6; k++) pts.push([cx + Math.cos(k * Math.PI / 3 + Math.PI / 6) * r, cy + Math.sin(k * Math.PI / 3 + Math.PI / 6) * r]); for (let k = 0; k < 6; k++) { const a = pts[k], b = pts[(k + 1) % 6]; line(a[0], a[1], b[0], b[1], t, c); } };
    poly([[16, 4], [26, 10], [26, 22], [16, 28], [6, 22], [6, 10]], hex("#33205c")); hexa(16, 16, 12, 1, PUR);
    hexa(16, 16, 6, 1, hex("#e2cbff")); line(16, 4, 16, 10, 1, DPUR); line(26, 22, 21, 19, 1, DPUR); line(6, 22, 11, 19, 1, DPUR);
    disc(16, 16, 2, WH);
  },
  w_heavy() { // big overhead cleave
    poly([[16, 3], [20, 6], [21, 24], [16, 28], [11, 24], [12, 6]], STEEL); poly([[16, 3], [20, 6], [21, 24], [16, 28]], hex("#aab8c8"));
    rect(8, 4, 23, 5, hex("#ffcf4a"));
    ring(16, 27, 11, 2, RED, 180, 360); line(5, 27, 27, 27, 1, OR);
    line(4, 12, 9, 14, 1, YEL); line(28, 12, 23, 14, 1, YEL);
  },
  w_impale() { // sword stuck in ground
    rect(4, 24, 27, 28, hex("#7a6a5a")); rect(4, 24, 27, 25, hex("#a89884"));
    poly([[16, 26], [12, 12], [12, 3], [20, 3], [20, 12]], STEEL); poly([[16, 26], [16, 12], [16, 3], [20, 3], [20, 12]], hex("#aab8c8"));
    rect(9, 12, 22, 14, hex("#ffcf4a")); rect(14, 15, 17, 16, BRN);
    ring(16, 25, 11, 1, OR, 190, 350); line(5, 22, 9, 20, 1, RED); line(27, 22, 23, 20, 1, RED);
  },
  w_iai() { // sheathed blade with a flash line
    rect(3, 18, 14, 22, hex("#2a2a35")); rect(3, 18, 14, 19, hex("#555568")); rect(14, 17, 16, 23, hex("#ffcf4a"));
    rect(2, 19, 3, 21, DRED);
    line(15, 20, 29, 10, 2, WH); line(17, 22, 29, 13, 1, hex("#ffd0c8"));
    line(20, 6, 28, 5, 1, RED); line(22, 26, 29, 24, 1, RED);
    rect(27, 8, 28, 9, YEL);
  },
  w_rage() { // roaring face with sound waves
    disc(16, 16, 8, hex("#d6a074")); rect(8, 8, 24, 11, DRED); rect(8, 11, 9, 12, DRED); rect(23, 11, 24, 12, DRED);
    line(11, 13, 14, 15, 1, hex("#2a1010")); line(21, 13, 18, 15, 1, hex("#2a1010"));
    rect(13, 19, 19, 23, hex("#3a0a0a")); rect(14, 19, 18, 20, WH);
    ring(16, 16, 13, 1, OR, 200, 340); ring(16, 16, 13, 1, OR, 20, 160); line(3, 12, 6, 13, 1, YEL); line(28, 12, 25, 13, 1, YEL);
  },
  w_fort() { // iron-clad body shield (invulnerable)
    poly([[16, 3], [27, 7], [27, 18], [16, 29], [5, 18], [5, 7]], hex("#6b7a90")); poly([[16, 5], [25, 8], [25, 17], [16, 26], [7, 17], [7, 8]], hex("#aebbd0"));
    poly([[16, 5], [25, 8], [25, 17], [16, 26]], hex("#8c9bb0"));
    rect(14, 9, 17, 20, hex("#2c3544")); rect(10, 12, 21, 14, hex("#2c3544"));
    for (const [x, y] of [[8, 9], [23, 9], [16, 24]]) rect(x, y, x + 1, y + 1, WH);
  },
  w_tiger() { // tiger claw swipes
    poly([[5, 6], [8, 4], [12, 4], [14, 8], [10, 8]], OR);
    for (const dx of [0, 7, 14]) { line(6 + dx, 6, 12 + dx, 26, 3, OR); line(6 + dx, 6, 12 + dx, 26, 1, WH); line(8 + dx, 8, 10 + dx, 18, 1, hex("#2a1010")); }
    rect(4, 27, 27, 27, DRED);
  },
  a_spread() { // three arrows in a fan
    for (const [dy, a] of [[-9, -0.38], [0, 0], [9, 0.38]]) { const x1 = 27, y1 = 16 + dy; arrow(5, 16, x1, y1, 1, hex("#e8f4e0"), 5); }
    disc(5, 16, 2, GRN); line(4, 12, 4, 20, 1, DGRN);
  },
  a_shotgun() { // many pellets-arrows bursting from a bow
    ring(7, 16, 7, 2, BRN, 270, 90); line(7, 9, 7, 23, 1, hex("#e8f4e0"));
    for (let k = -4; k <= 4; k++) { const y = 16 + k * 3.4; arrow(9, 16, 28, y, 1, k % 2 ? hex("#e8f4e0") : GRN, 3); }
    disc(9, 16, 2, YEL);
  },
  a_gale() { // big arrow in a wind spiral
    arrow(3, 16, 29, 16, 3, hex("#e8f4e0"), 8);
    ring(10, 16, 6, 1, SKY, 200, 90); ring(18, 16, 7, 1, SKY, 270, 180); ring(24, 16, 5, 1, WH, 180, 40);
    line(5, 7, 14, 7, 1, SKY); line(12, 26, 22, 26, 1, SKY);
  },
  a_focus() { // hawk eye with crosshair
    poly([[3, 16], [10, 9], [22, 9], [29, 16], [22, 23], [10, 23]], hex("#e8d27a")); poly([[5, 16], [11, 11], [21, 11], [27, 16], [21, 21], [11, 21]], WH);
    disc(16, 16, 5, hex("#c8801a")); disc(16, 16, 3, hex("#1a1208")); rect(14, 14, 15, 15, WH);
    line(16, 3, 16, 8, 1, GRN); line(16, 24, 16, 29, 1, GRN); line(2, 16, 4, 16, 1, GRN); line(28, 16, 30, 16, 1, GRN);
  },
  a_spirit() { // wind spirit bow
    ring(14, 16, 11, 3, SKY, 280, 80); line(17, 6, 17, 26, 1, WH);
    arrow(8, 16, 27, 16, 1, GRN, 5);
    rect(19, 4, 20, 5, WH); rect(23, 25, 24, 26, WH); line(4, 8, 8, 9, 1, SKY); line(4, 24, 8, 23, 1, SKY);
    disc(24, 9, 1, CY);
  },
  a_wolf() { // wolf head howling
    poly([[8, 26], [8, 14], [11, 4], [14, 12], [20, 12], [23, 4], [26, 14], [26, 26], [20, 28], [14, 28]], SKY);
    poly([[10, 25], [10, 15], [12, 8], [14, 14], [20, 14], [22, 8], [24, 15], [24, 25]], ICE);
    rect(12, 16, 14, 17, hex("#10304a")); rect(20, 16, 22, 17, hex("#10304a"));
    poly([[15, 21], [19, 21], [17, 25]], hex("#10304a"));
    line(4, 10, 7, 12, 1, WH); line(28, 10, 25, 12, 1, WH);
  },
  m_wave() { // vertical purple wave advancing
    for (const [x, c] of [[8, DPUR], [14, PUR], [20, hex("#e2cbff")]]) { ring(x, 16, 10, 2, c, 300, 60); }
    line(3, 16, 8, 16, 1, PUR); line(5, 11, 9, 11, 1, DPUR); line(5, 21, 9, 21, 1, DPUR);
    rect(26, 8, 27, 9, WH); rect(27, 22, 28, 23, WH);
  },
  m_hammer() { // mana hammer
    line(8, 28, 21, 11, 3, BRN);
    poly([[13, 4], [29, 4], [29, 14], [13, 14], [11, 9]], PUR); poly([[15, 5], [28, 5], [28, 8], [15, 8]], hex("#e2cbff"));
    rect(13, 12, 29, 14, DPUR);
    ring(8, 28, 4, 1, YEL, 180, 360); rect(3, 14, 4, 15, WH); rect(5, 8, 6, 9, PUR);
  },
  m_bow() { // giant mana bow
    ring(18, 16, 13, 3, PUR, 100, 260); line(13, 4, 13, 28, 1, hex("#e2cbff"));
    arrow(6, 16, 27, 16, 2, hex("#e2cbff"), 7);
    disc(12, 16, 1, WH); rect(4, 6, 5, 7, PUR); rect(4, 25, 5, 26, PUR);
  },
  m_focus() { // glowing mind orb with crosshair
    ring(16, 16, 12, 1, PUR); disc(16, 16, 7, DPUR); disc(16, 16, 5, PUR); disc(14, 14, 2, hex("#e6d2ff")); rect(13, 13, 14, 14, WH);
    line(16, 2, 16, 7, 1, YEL); line(16, 25, 16, 30, 1, YEL); line(2, 16, 7, 16, 1, YEL); line(25, 16, 30, 16, 1, YEL);
  },
  m_reset() { // reversing clock
    ring(16, 16, 12, 2, hex("#e2cbff")); disc(16, 16, 10, hex("#33205c"));
    line(16, 16, 16, 8, 2, WH); line(16, 16, 22, 19, 2, PUR);
    poly([[3, 12], [10, 11], [6, 18]], YEL); ring(16, 16, 12, 2, YEL, 150, 215);
    rect(15, 15, 17, 17, WH);
  },
  m_cannon() { // thick energy beam from a core
    disc(6, 16, 5, WH); disc(6, 16, 4, CY);
    rect(8, 10, 28, 22, DPUR); rect(8, 12, 28, 20, PUR); rect(8, 14, 28, 18, hex("#e2cbff")); rect(8, 15, 28, 17, WH);
    poly([[28, 9], [31, 16], [28, 23]], PUR);
    line(14, 6, 20, 8, 1, YEL); line(14, 26, 20, 24, 1, YEL);
  },
  m_dragon() { // dragon head breathing fire
    poly([[3, 8], [12, 6], [18, 10], [20, 18], [14, 24], [5, 22], [3, 14]], hex("#7a45d6")); poly([[5, 9], [12, 8], [16, 12], [17, 18], [13, 21], [6, 20]], PUR);
    poly([[8, 6], [10, 2], [12, 6]], hex("#e2cbff")); rect(8, 12, 10, 13, YEL);
    poly([[18, 12], [29, 6], [27, 16], [30, 24], [18, 20]], OR); poly([[19, 14], [27, 11], [26, 16], [28, 21], [19, 18]], YEL);
    rect(15, 20, 16, 22, WH);
  },
  c_dash() { // winged boot
    poly([[11, 8], [18, 8], [18, 17], [26, 21], [26, 26], [9, 26], [9, 20], [11, 20]], hex("#e9f4ff")); poly([[9, 21], [26, 21], [26, 26], [9, 26]], hex("#8fb4d6"));
    rect(11, 8, 18, 10, SKY);
    poly([[4, 8], [10, 12], [3, 12]], WH); poly([[3, 13], [10, 15], [4, 17]], WH); poly([[5, 4], [10, 9], [3, 8]], WH);
  },
};

let count = 0;
for (const [id, draw] of Object.entries(icons)) {
  P = new Uint8Array(N * N * 4);
  frame(id[0]);
  draw();
  fs.writeFileSync(path.join(out, id + ".png"), png(P));
  count++;
}
console.log("wrote", count, "icons to", out);
