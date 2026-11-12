// Armor icon PREVIEW generator: node dev/art/make-armor-designs.mjs
// 3 classes x 6 tiers x 4 pieces, 32x32 RGBA, plus contact sheets. Outputs to dev/art/preview/armor/
import fs from "node:fs";
import path from "node:path";
import zlib from "node:zlib";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const out = path.resolve(here, "preview/armor");
fs.mkdirSync(out, { recursive: true });

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
const hex = h => [1, 3, 5].map(i => parseInt(h.slice(i, i + 2), 16));
const ramp = (...a) => a.map(hex);

// ---------- palettes ----------
const BODY = {
  warrior: [ramp("#b0814f", "#8a5f36", "#63421f", "#42290f"), ramp("#d7dbe0", "#aab0b8", "#7b828d", "#505660"), ramp("#a9b9cc", "#7d8fa8", "#56667f", "#36435a"),
    ramp("#b5cde8", "#85a8d0", "#587fb0", "#375683"), ramp("#d77a4a", "#a8502d", "#783520", "#4b1f12"), ramp("#fffdf0", "#f7e8ae", "#e3bd55", "#a97b25")],
  archer: [ramp("#a68a5b", "#7f6640", "#5a4529", "#3a2a16"), ramp("#8fae6a", "#6b8a4a", "#4b6636", "#2f4222"), ramp("#6f8f6a", "#4f7050", "#38523a", "#243826"),
    ramp("#7fc0c8", "#4f93a8", "#35687f", "#213f55"), ramp("#cf6f42", "#a04a2a", "#74311c", "#481c10"), ramp("#fffdf0", "#f3ead0", "#d8c690", "#a08a50")],
  mage: [ramp("#9a9488", "#767064", "#554f45", "#38332b"), ramp("#8d9bb5", "#6a7896", "#4b5876", "#303a54"), ramp("#4d5f95", "#364579", "#25305a", "#171e3a"),
    ramp("#5aa0e0", "#3a78c4", "#27559a", "#173568"), ramp("#e0604a", "#b53a2e", "#832320", "#511414"), ramp("#ffffff", "#f1eefc", "#cfc8ea", "#9a90c0")],
};
const TRIM = [ramp("#9a6d3e", "#744c26", "#523416", "#34200c"), ramp("#8e949d", "#666c76", "#444a54", "#2c3038"), ramp("#fff0a0", "#ffd447", "#d4a019", "#8f6410"),
  ramp("#f0fbff", "#bfe6f5", "#86bcd8", "#4f7f9c"), ramp("#ffd98a", "#f2a93b", "#c27518", "#7d4609"), ramp("#fffbd0", "#ffe680", "#ffc233", "#c98a14")];
const GEM = [null, null, null, ramp("#e8ffff", "#7ef0ff", "#25b8e8", "#1070b0"), ramp("#fff0a0", "#ffb52e", "#e07a10", "#8a4208"), ramp("#ffffff", "#d8fff4", "#7affd8", "#35c0a8")];
const ACC = [null, null, null, hex("#7ef0ff"), hex("#ffb52e"), hex("#aaffee")];
const FEATHER = [hex("#a89a80"), hex("#d8dde2"), hex("#e8e8e8"), hex("#7ef0ff"), hex("#ff7a3a"), hex("#ffffff")];
const OUTLINE = hex("#17121a"), DARK = hex("#2b2432"), DARK2 = hex("#3d3345");
const TIERS = ["Starter", "Iron", "Steel", "Blue Diamond", "Amber Redwood", "Legendary"];

// ---------- drawing on a char layer ----------
const N = 32;
let L, SYM, tier, cls;
const clear = () => { L = Array.from({ length: N }, () => Array(N).fill(null)); };
const put = (x, y, c) => { if (x >= 0 && y >= 0 && x < N && y < N) L[y][x] = c; };
const px = (x, y, c) => { put(x, y, c); if (SYM) put(31 - x, y, c); };
const R = (x0, y0, x1, y1, c) => { for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) px(x, y, c); };
const rows = (y0, spans, c) => spans.forEach(([a, b], i) => R(a, y0 + i, b, y0 + i, c));
const inPoly = (pts, x, y) => {
  let inside = false;
  for (let i = 0, j = pts.length - 1; i < pts.length; j = i++) {
    const [xi, yi] = pts[i], [xj, yj] = pts[j];
    if ((yi > y) !== (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside;
  }
  return inside;
};
const P = (pts, c) => {
  const xs = pts.map(p => p[0]), ys = pts.map(p => p[1]);
  for (let y = Math.floor(Math.min(...ys)); y <= Math.ceil(Math.max(...ys)); y++)
    for (let x = Math.floor(Math.min(...xs)); x <= Math.ceil(Math.max(...xs)); x++) if (inPoly(pts, x + .5, y + .5)) px(x, y, c);
};
const dots = (list, c) => list.forEach(([x, y]) => px(x, y, c));
const T = t => tier >= t;
const G = () => (T(3) ? "G" : "T"); // gem where available, else trim

// ---------- pieces ----------
const pieces = {
  warrior: {
    helmet() {
      SYM = true;
      if (T(3)) { P([[9, 12], [6, 11], [4, 7], [3, 3], [6, 6], [8, 8], [10, 10]], "T"); } // horns
      if (T(5)) { P([[8, 9], [5, 8], [2, 5], [1, 1], [5, 4], [7, 6]], "T"); }
      rows(5, [[13, 15], [11, 15], [10, 15], [9, 15], [8, 15], [8, 15], [8, 15], [8, 15], [8, 15]], "B");
      R(7, 14, 15, 14, "T"); // brow band
      R(7, 15, 9, 25, "B"); // cheek
      R(9, 22, 15, 25, "B"); // chin/bevor
      R(10, 15, 15, 21, "D"); // face opening
      R(10, 15, 15, 16, "D2");
      rows(15, [[10, 15], [10, 15]], "D");
      R(15, 10, 15, 21, T(1) ? "B" : "D"); // nose guard
      if (T(1)) R(15, 13, 15, 20, "T");
      if (T(2)) { R(15, 2, 15, 8, "T"); R(14, 4, 14, 8, "B"); } // crest
      if (T(2)) dots([[8, 14], [12, 14], [8, 18], [8, 22]], "T"); // rivets
      if (T(3)) R(15, 9, 15, 12, "G");
      if (T(3)) dots([[11, 11], [12, 12]], "G");
      if (T(4)) { P([[7, 20], [4, 22], [5, 25], [8, 25]], "T"); }
      if (!T(1)) dots([[10, 9], [13, 9], [11, 12], [14, 12]], "T"); // leather stitching
    },
    chest() {
      SYM = true;
      // pauldrons
      R(3, 7, 9, 12, "B"); R(4, 6, 8, 6, "B"); R(3, 13, 7, 13, "B");
      R(3, 12, 9, 12, "T"); if (T(1)) dots([[5, 9], [7, 9]], "T");
      // torso
      R(9, 6, 15, 24, "B"); R(8, 22, 15, 26, "B"); R(9, 5, 11, 5, "B");
      R(12, 5, 15, 7, "D"); R(13, 8, 15, 8, "D");
      R(7, 14, 8, 22, "B"); // arm plate edge
      R(8, 20, 15, 21, "T"); dots([[15, 20], [15, 21]], G()); // belt+buckle
      R(15, 9, 15, 19, T(1) ? "T" : "B");
      if (T(2)) { dots([[10, 9], [10, 14], [10, 18], [4, 8], [8, 8], [9, 24]], "T"); }
      if (T(2)) R(8, 25, 15, 26, "T");
      if (T(3)) { R(14, 11, 15, 14, "G"); }
      if (T(3)) { dots([[11, 11], [11, 12], [4, 9], [5, 9]], "G"); }
      if (T(4)) { dots([[5, 4], [7, 4], [4, 5], [8, 5]], "T"); P([[4, 6], [5, 3], [6, 6]], "T"); P([[7, 6], [8, 3], [9, 6]], "T"); }
      if (!T(1)) dots([[11, 11], [13, 11], [11, 15], [13, 15], [11, 18]], "T");
    },
    legs() {
      SYM = true;
      R(8, 4, 15, 8, "B"); R(8, 5, 15, 6, "T"); dots([[15, 5], [15, 6]], G());
      R(8, 9, 14, 24, "B"); R(9, 24, 14, 26, "B");
      R(15, 9, 15, 12, "D");
      R(8, 15, 14, 19, "T"); R(9, 16, 13, 18, "B"); dots([[11, 17]], G());
      R(8, 22, 14, 23, "T");
      if (T(2)) dots([[9, 6], [13, 6], [8, 10], [8, 21]], "T");
      if (T(3)) { dots([[10, 17], [12, 17], [11, 16], [11, 18]], "G"); R(11, 17, 11, 17, "G"); }
      if (T(4)) P([[7, 16], [4, 18], [7, 20]], "T");
      if (!T(1)) { dots([[10, 11], [12, 11], [10, 13], [12, 13]], "T"); }
    },
    boots() {
      SYM = true;
      R(6, 6, 12, 18, "B"); R(5, 6, 13, 8, "T");
      R(3, 17, 13, 24, "B"); R(3, 21, 8, 24, "T"); R(3, 25, 13, 25, "D");
      R(6, 9, 12, 9, "B");
      if (T(2)) dots([[6, 7], [12, 7], [10, 20], [12, 20], [6, 13], [12, 13]], "T");
      if (T(3)) { R(8, 11, 10, 14, "G"); }
      if (T(4)) { P([[13, 20], [15, 22], [13, 24]], "T"); P([[5, 5], [4, 2], [7, 5]], "T"); }
      if (!T(1)) dots([[7, 12], [9, 12], [7, 15], [9, 15]], "T");
    },
  },
  archer: {
    helmet() {
      SYM = true;
      rows(4, [[15, 15], [13, 15], [11, 15], [10, 15], [9, 15], [9, 15], [8, 15], [8, 15], [8, 15], [8, 15], [8, 15], [8, 15], [8, 15], [8, 15], [8, 15], [8, 15]], "B");
      R(8, 20, 15, 23, "B"); R(10, 24, 15, 25, "B"); // cowl
      R(11, 11, 15, 19, "D"); R(12, 10, 15, 10, "D");
      R(10, 12, 10, 18, T(2) ? "T" : "B"); R(11, 10, 11, 10, T(2) ? "T" : "B");
      if (T(1)) R(12, 20, 15, 20, "T");
      if (T(2)) { dots([[8, 21], [9, 22]], "T"); R(15, 21, 15, 22, "T"); }
      R(15, 4, 15, 7, "B");
      if (T(3)) R(15, 24, 15, 24, "G");
      if (T(3)) dots([[12, 7], [13, 7]], "G");
      SYM = false;
      // feather
      if (T(1)) { const l = T(3) ? 8 : 5; for (let i = 0; i < l; i++) { put(21 + Math.round(i * .6), 9 - i, "F"); put(22 + Math.round(i * .6), 9 - i, "F"); } put(20, 10, "T"); put(21, 11, "T"); }
      if (T(4)) { put(26, 3, "F"); put(27, 3, "F"); }
    },
    chest() {
      SYM = true;
      R(7, 6, 15, 24, "B"); R(8, 25, 15, 26, "B"); R(5, 8, 7, 14, "B"); R(4, 9, 4, 12, "B");
      R(12, 6, 15, 8, "D"); R(13, 9, 15, 9, "D");
      for (let y = 10; y <= 22; y += 3) put(15, y, "T"), put(16, y, "T");
      R(5, 14, 7, 14, "T"); R(7, 22, 15, 22, "T");
      if (T(1)) for (let y = 11; y <= 20; y += 3) for (let x = 9 + (y % 2); x <= 13; x += 2) px(x, y, "T");
      if (T(2)) { R(7, 6, 7, 8, "T"); dots([[8, 24]], "T"); }
      if (T(3)) { dots([[15, 12], [11, 15]], "G"); }
      SYM = false;
      // quiver strap (diagonal)
      for (let i = 0; i < 18; i++) { put(9 + i, 6 + i, "T"); put(10 + i, 6 + i, "T"); }
      put(16, 15, G()); put(17, 16, G());
      // quiver + arrows behind right shoulder
      R(22, 5, 25, 9, "T"); put(23, 4, "B"); put(24, 3, "F"); put(22, 3, "F"); put(23, 2, "F"); put(25, 3, "F");
      put(23, 3, "T"); put(24, 4, "T");
      if (T(3)) put(24, 7, "G");
    },
    legs() {
      SYM = true;
      R(8, 4, 15, 8, "B"); R(8, 5, 15, 6, "T"); dots([[15, 5], [15, 6]], G());
      R(9, 9, 14, 25, "B"); R(10, 26, 14, 26, "B"); R(15, 9, 15, 11, "D");
      R(9, 16, 14, 17, "T"); R(9, 22, 14, 22, "T");
      if (T(1)) dots([[10, 12], [12, 12], [11, 13], [13, 13]], "T");
      if (T(2)) { dots([[9, 5], [13, 5], [9, 10]], "T"); }
      if (T(3)) { dots([[11, 16], [11, 17]], "G"); }
      SYM = false;
      R(4, 8, 8, 13, "T"); R(5, 9, 7, 12, "B"); put(6, 10, G()); // thigh pouch
      if (T(4)) { put(5, 7, "F"); put(6, 7, "F"); }
    },
    boots() {
      SYM = true;
      R(6, 6, 12, 19, "B"); R(5, 6, 13, 10, "T"); R(6, 11, 12, 11, "B");
      R(3, 18, 13, 24, "B"); R(3, 25, 13, 25, "D"); R(11, 18, 13, 19, "B");
      for (let y = 12; y <= 17; y += 2) put(11, y, "T");
      if (T(1)) { R(3, 22, 7, 24, "T"); }
      if (T(2)) dots([[7, 8], [11, 8]], "T");
      if (T(3)) { dots([[9, 8]], "G"); R(8, 14, 8, 16, "G"); }
      if (T(4)) { P([[4, 6], [3, 3], [6, 6]], "F"); }
    },
  },
  mage: {
    helmet() {
      SYM = true;
      rows(2, [[15, 15], [14, 15], [14, 15], [13, 15], [13, 15], [12, 15], [12, 15], [11, 15], [11, 15], [10, 15], [10, 15], [9, 15], [9, 15], [8, 15], [8, 15]], "B");
      R(3, 17, 15, 19, "B"); R(5, 20, 15, 20, "B"); R(5, 21, 15, 21, "D"); R(7, 22, 15, 22, "D2");
      R(9, 14, 15, 16, "T"); R(15, 14, 15, 16, G()); dots([[12, 15]], G());
      if (T(1)) R(4, 17, 15, 17, "T");
      if (T(2)) { dots([[12, 9], [11, 10], [12, 11], [13, 6]], "T"); }
      if (T(3)) { dots([[10, 10], [14, 12]], "G"); }
      if (T(4)) { dots([[4, 18], [6, 18]], "G"); }
      if (!T(1)) { dots([[12, 8], [11, 12]], "T"); }
      if (T(5)) dots([[15, 1], [15, 0]], "G");
    },
    chest() {
      SYM = true;
      R(9, 6, 15, 25, "B"); R(8, 22, 15, 26, "B"); R(7, 8, 9, 14, "B");
      P([[8, 9], [3, 12], [2, 22], [8, 22]], "B"); // sleeve
      R(2, 21, 8, 22, "T");
      R(11, 5, 15, 8, "D"); R(12, 4, 15, 5, "T"); R(10, 5, 11, 8, "T"); R(13, 9, 15, 9, "T");
      R(8, 16, 15, 18, "T");
      R(15, 10, 15, 15, "T");
      R(8, 25, 15, 26, "T");
      SYM = false;
      R(16, 19, 17, 24, "T"); put(16, 17, G());
      SYM = true;
      if (T(1)) dots([[10, 23], [12, 23], [14, 23]], "T");
      if (T(2)) dots([[4, 21], [6, 21], [11, 12], [12, 13], [11, 14]], "T");
      if (T(3)) { dots([[15, 14], [3, 15]], "G"); put(16, 17, "G"); }
      if (T(4)) dots([[3, 19], [5, 19], [7, 19]], "G");
      if (!T(1)) dots([[11, 12], [13, 13], [11, 20]], "T");
    },
    legs() {
      SYM = true;
      R(8, 4, 15, 8, "B"); R(8, 5, 15, 6, "T"); dots([[15, 5], [15, 6]], G());
      rows(9, [[8, 15], [8, 15], [8, 15], [7, 15], [7, 15], [7, 15], [6, 15], [6, 15], [6, 15], [5, 15], [5, 15], [5, 15], [5, 15], [4, 15], [4, 15], [4, 15], [4, 15]], "B");
      R(15, 14, 15, 24, "D");
      R(4, 24, 15, 26, "T");
      if (T(1)) dots([[7, 10], [8, 15], [8, 20]], "T");
      if (T(2)) dots([[6, 25], [10, 25], [14, 25], [14, 14]], "B");
      if (T(3)) dots([[6, 25], [10, 25], [14, 25]], "G");
      if (T(4)) dots([[8, 25], [12, 25]], "G");
      if (!T(1)) dots([[9, 12], [11, 14], [8, 19]], "T");
    },
    boots() {
      SYM = true;
      R(6, 6, 12, 17, "B"); R(5, 6, 13, 9, "T");
      P([[3, 18], [13, 18], [13, 24], [3, 24], [1, 19], [2, 16]], "B");
      R(2, 25, 13, 25, "D");
      if (T(1)) R(3, 22, 13, 22, "T");
      if (T(2)) dots([[6, 12], [10, 12], [8, 14]], "T");
      if (T(3)) R(8, 11, 9, 12, "G");
      if (T(4)) { P([[5, 6], [3, 2], [7, 6]], "T"); dots([[5, 20], [9, 20]], "G"); }
      if (!T(1)) dots([[7, 13], [10, 15]], "T");
    },
  },
};

// ---------- render layer -> RGBA ----------
function render() {
  const buf = Buffer.alloc(N * N * 4);
  const body = BODY[cls][tier], trim = TRIM[tier], gem = GEM[tier] || trim;
  const rampFor = c => c === "B" ? body : c === "T" ? trim : c === "G" ? gem : null;
  const at = (x, y) => (x < 0 || y < 0 || x >= N || y >= N) ? null : L[y][x];
  const set = (x, y, c, a = 255) => { const i = (y * N + x) * 4; buf[i] = c[0]; buf[i + 1] = c[1]; buf[i + 2] = c[2]; buf[i + 3] = a; };
  // optional glow behind (T5)
  if (tier === 5) {
    for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
      if (at(x, y)) continue;
      let near = false;
      for (let dy = -2; dy <= 2 && !near; dy++) for (let dx = -2; dx <= 2; dx++) if (Math.abs(dx) + Math.abs(dy) <= 2 && at(x + dx, y + dy)) { near = true; break; }
      if (near) set(x, y, ACC[5], 55);
    }
  }
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    const c = at(x, y);
    if (!c) continue;
    if (c === "D") { set(x, y, DARK); continue; }
    if (c === "D2") { set(x, y, DARK2); continue; }
    if (c === "F") { const f = FEATHER[tier]; const up = at(x, y - 1) === "F"; set(x, y, up ? f : f.map(v => Math.min(255, v + 25))); continue; }
    const r = rampFor(c);
    const same = (xx, yy) => at(xx, yy) === c;
    const emptyUL = !at(x, y - 1) || !at(x - 1, y);
    const dkDown = !same(x, y + 1), dkRight = !same(x + 1, y);
    let idx = 1;
    if (emptyUL && !(dkDown || dkRight)) idx = 0;
    else if (dkDown && dkRight) idx = c === "G" ? 2 : 3 - 0;
    else if (dkDown || dkRight) idx = 2;
    else if (!same(x, y - 1) || !same(x - 1, y)) idx = emptyUL ? 0 : 1;
    else idx = (x >= 19 && c === "B") ? 2 : (x >= 17 && c === "B" && (x + y) % 2 === 0 ? 2 : 1);
    if (c === "G") idx = Math.min(idx, 2);
    set(x, y, r[Math.min(3, idx)]);
  }
  // outline (4-neighbour) on transparent pixels
  const mark = [];
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    if (at(x, y)) continue;
    if (at(x - 1, y) || at(x + 1, y) || at(x, y - 1) || at(x, y + 1)) mark.push([x, y]);
  }
  mark.forEach(([x, y]) => set(x, y, OUTLINE));
  // accents / sparkles
  const spark = (x, y) => {
    set(x, y, [255, 255, 255]);
    for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) { const xx = x + dx, yy = y + dy; if (xx >= 0 && yy >= 0 && xx < N && yy < N && buf[(yy * N + xx) * 4 + 3] < 255) set(xx, yy, [255, 250, 200], 200); }
  };
  if (tier === 5) { for (const [x, y] of SPARKS[cls + "_" + currentPiece] || []) spark(x, y); }
  return buf;
}
let currentPiece = "";
// sparkle positions (T5), chosen per piece to sit on/near silhouette
const SPARKS = {
  warrior_helmet: [[5, 4], [26, 6], [24, 18]], warrior_chest: [[4, 4], [27, 9]], warrior_legs: [[24, 10], [5, 25]], warrior_boots: [[4, 6], [27, 22]],
  archer_helmet: [[6, 6], [26, 12]], archer_chest: [[4, 20], [27, 12]], archer_legs: [[26, 20], [4, 5]], archer_boots: [[3, 8], [28, 20]],
  mage_helmet: [[24, 4], [4, 12], [28, 20]], mage_chest: [[27, 8], [3, 6]], mage_legs: [[27, 12], [3, 5]], mage_boots: [[4, 4], [27, 18]],
};

// ---------- build all ----------
const CLASSES = ["warrior", "archer", "mage"];
const PIECES = ["helmet", "chest", "legs", "boots"];
const icons = {};
for (cls of CLASSES) for (tier = 0; tier <= 5; tier++) for (const p of PIECES) {
  clear(); SYM = true; currentPiece = p;
  pieces[cls][p]();
  const buf = render();
  icons[`${cls}_t${tier}_${p}`] = buf;
  fs.writeFileSync(path.join(out, `${cls}_t${tier}_${p}.png`), png(buf, N, N));
}

// ---------- sheets ----------
const FONT = {
  A: "010101111101101", B: "110101110101110", C: "011100100100011", D: "110101101101110", E: "111100110100111", F: "111100110100100", G: "011100101101011",
  H: "101101111101101", I: "111010010010111", J: "001001001101010", K: "101101110101101", L: "100100100100111", M: "101111111101101", N: "110101101101101",
  O: "010101101101010", P: "110101110100100", Q: "010101101111011", R: "110101110101101", S: "011100010001110", T: "111010010010010", U: "101101101101111",
  V: "101101101101010", W: "101101111111101", X: "101101010101101", Y: "101101010010010", Z: "111001010100111",
  0: "111101101101111", 1: "010110010010111", 2: "110001010100111", 3: "110001010001110", 4: "101101111001001", 5: "111100110001110",
  "/": "001001010100100", " ": "000000000000000",
};
function canvas(w, h, bg) {
  const b = Buffer.alloc(w * h * 4);
  for (let i = 0; i < w * h; i++) { b[i * 4] = bg[0]; b[i * 4 + 1] = bg[1]; b[i * 4 + 2] = bg[2]; b[i * 4 + 3] = 255; }
  return { w, h, b };
}
function fill(cv, x0, y0, w, h, c) { for (let y = y0; y < y0 + h; y++) for (let x = x0; x < x0 + w; x++) { if (x < 0 || y < 0 || x >= cv.w || y >= cv.h) continue; const i = (y * cv.w + x) * 4; cv.b[i] = c[0]; cv.b[i + 1] = c[1]; cv.b[i + 2] = c[2]; cv.b[i + 3] = 255; } }
function text(cv, s, x, y, sc, c) {
  for (const ch of s.toUpperCase()) {
    const g = FONT[ch] || FONT[" "];
    for (let i = 0; i < 15; i++) if (g[i] === "1") fill(cv, x + (i % 3) * sc, y + Math.floor(i / 3) * sc, sc, sc, c);
    x += 4 * sc;
  }
}
const textW = (s, sc) => s.length * 4 * sc - sc;
function blit(cv, src, x0, y0, sc) {
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    const i = (y * N + x) * 4, a = src[i + 3] / 255; if (a === 0) continue;
    for (let dy = 0; dy < sc; dy++) for (let dx = 0; dx < sc; dx++) {
      const X = x0 + x * sc + dx, Y = y0 + y * sc + dy; if (X >= cv.w || Y >= cv.h) continue;
      const j = (Y * cv.w + X) * 4;
      for (let k = 0; k < 3; k++) cv.b[j + k] = Math.round(src[i + k] * a + cv.b[j + k] * (1 - a));
    }
  }
}
const NAVY = hex("#0e1430"), CELL = hex("#1a2250"), WHITE = hex("#ffffff"), GREY = hex("#9aa6d0"), GOLD = hex("#ffd447");
const CLS_LABEL = { warrior: "WARRIOR", archer: "ARCHER", mage: "MAGE" };
const PIECE_LABEL = ["HELMET", "CHEST", "LEGS", "BOOTS"];
const save = (cv, name) => fs.writeFileSync(path.join(out, name), png(cv.b, cv.w, cv.h));

function block(cv, c, ox, oy, sc, pad, labelW, withLabels) {
  const cell = N * sc;
  for (let r = 0; r < 6; r++) {
    if (withLabels) {
      text(cv, "T" + r, ox + 6, oy + r * (cell + pad) + cell / 2 - 14, 4, GOLD);
      text(cv, TIERS[r], ox + 6, oy + r * (cell + pad) + cell / 2 + 12, 2, GREY);
    }
    for (let p = 0; p < 4; p++) {
      const x = ox + labelW + p * (cell + pad), y = oy + r * (cell + pad);
      fill(cv, x - 3, y - 3, cell + 6, cell + 6, CELL);
      blit(cv, icons[`${c}_t${r}_${PIECES[p]}`], x, y, sc);
    }
  }
}
for (const c of CLASSES) {
  const sc = 6, pad = 14, labelW = 150, head = 70, cell = N * sc;
  const w = labelW + 4 * (cell + pad) + 10, h = head + 6 * (cell + pad) + 6;
  const cv = canvas(w, h, NAVY);
  text(cv, CLS_LABEL[c] + " ARMOR T0 TO T5", 10, 10, 4, WHITE);
  for (let p = 0; p < 4; p++) text(cv, PIECE_LABEL[p], labelW + p * (cell + pad) + (cell - textW(PIECE_LABEL[p], 3)) / 2, 44, 3, GREY);
  block(cv, c, 0, head, sc, pad, labelW, true);
  save(cv, `sheet_${c}.png`);
}
{
  const sc = 3, pad = 6, cell = N * sc, bw = 4 * (cell + pad), gap = 24, labelW = 70, head = 56;
  const w = labelW + 3 * bw + 2 * gap + 10, h = head + 6 * (cell + pad) + 6;
  const cv = canvas(w, h, NAVY);
  CLASSES.forEach((c, i) => {
    const ox = i === 0 ? 0 : labelW + i * (bw + gap) - gap + gap; // block origin
    const bx = labelW + i * (bw + gap);
    text(cv, CLS_LABEL[c], bx + 4, 8, 3, WHITE);
    for (let p = 0; p < 4; p++) text(cv, PIECE_LABEL[p], bx + p * (cell + pad) + (cell - textW(PIECE_LABEL[p], 2)) / 2, 36, 2, GREY);
    block(cv, c, bx - labelW, head, sc, pad, labelW, false);
  });
  for (let r = 0; r < 6; r++) text(cv, "T" + r, 10, head + r * (cell + pad) + cell / 2 - 10, 4, GOLD);
  save(cv, "sheet_all.png");
}
console.log("done", Object.keys(icons).length, "icons ->", out);
