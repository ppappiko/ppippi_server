// Worn armour PREVIEW v3: node dev/art/make-armor-worn-v3.mjs
// Partial, stylish armour (3D hats + extra 3D parts + transparent 64x32 layers) on a mannequin with a base outfit.
// Outputs to dev/art/preview/armor_worn_v2/ (+ models/ with Minecraft-style hat JSON + textures). PREVIEW ONLY.
import fs from "node:fs";
import path from "node:path";
import zlib from "node:zlib";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const out = path.resolve(here, "preview/armor_worn_v3");
const mdir = path.join(out, "models");
fs.mkdirSync(mdir, { recursive: true });

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
function decodePng(buf) {
  let p = 8, w = 0, h = 0, ct = 0; const idat = [];
  while (p < buf.length) { const len = buf.readUInt32BE(p), ty = buf.toString("ascii", p + 4, p + 8), d = buf.subarray(p + 8, p + 8 + len); if (ty === "IHDR") { w = d.readUInt32BE(0); h = d.readUInt32BE(4); ct = d[9]; } if (ty === "IDAT") idat.push(d); p += 12 + len; }
  const bpp = ct === 6 ? 4 : 3, raw = zlib.inflateSync(Buffer.concat(idat)), st = w * bpp, o = Buffer.alloc(w * h * 4), prev = Buffer.alloc(st), cur = Buffer.alloc(st);
  for (let y = 0; y < h; y++) {
    const ft = raw[y * (st + 1)]; for (let i = 0; i < st; i++) {
      const x = raw[y * (st + 1) + 1 + i], a = i >= bpp ? cur[i - bpp] : 0, b = prev[i], c = i >= bpp ? prev[i - bpp] : 0; let v;
      if (ft === 0) v = x; else if (ft === 1) v = x + a; else if (ft === 2) v = x + b; else if (ft === 3) v = x + ((a + b) >> 1); else { const pp = a + b - c, pa = Math.abs(pp - a), pb = Math.abs(pp - b), pc = Math.abs(pp - c); v = x + (pa <= pb && pa <= pc ? a : pb <= pc ? b : c); }
      cur[i] = v & 255;
    }
    for (let x = 0; x < w; x++) { const q = (y * w + x) * 4; o[q] = cur[x * bpp]; o[q + 1] = cur[x * bpp + 1]; o[q + 2] = cur[x * bpp + 2]; o[q + 3] = bpp === 4 ? cur[x * bpp + 3] : 255; }
    cur.copy(prev);
  }
  return { w, h, b: o };
}
const cleanA = b => { const o = Buffer.from(b); for (let i = 3; i < o.length; i += 4) if (o[i] === 254) o[i] = 255; return o; }; // alpha 254 = glow flag (preview only)
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
const LEATH = ramp("#b88a56", "#8a5f36", "#63421f", "#3e2810");
const GOLD = ramp("#fff0a0", "#ffd447", "#d4a019", "#8f6410");
const SILV = ramp("#f4f6fa", "#cfd5de", "#9aa2b0", "#656c7a");
const IRON = ramp("#b9bec6", "#8e949d", "#666c76", "#444a54");
const DKP = ramp("#6a5a74", "#4a3d55", "#352c3e", "#221c29");
const BANDA = [ramp("#9a6d3e", "#744c26", "#523416", "#34200c"), DKP, ramp("#4a3d55", "#352c3e", "#221c29", "#15111a"), ramp("#f0fbff", "#bfe6f5", "#86bcd8", "#4f7f9c"), ramp("#ffd98a", "#f2a93b", "#c27518", "#7d4609"), GOLD];
const BUCK = [IRON, GOLD, GOLD, GOLD, SILV, SILV];
const FEATHER = [hex("#b3a285"), hex("#dde2e6"), hex("#f0f0f0"), hex("#7ef0ff"), hex("#ff7a3a"), hex("#ffffff")];
const MAILR = ramp("#8a909a", "#646a74", "#434851", "#2c3038");
const DARK = hex("#2b2432"), DARK2 = hex("#3d3345");
const TIERS = ["Starter", "Iron", "Steel", "Blue Diamond", "Amber Redwood", "Legendary"];
const CLASSES = ["warrior", "archer", "mage"];
const CNAME = { warrior: "WARRIOR", archer: "ARCHER", mage: "MAGE" };

let CLS = "warrior", TIER = 0, R = {};
const T = t => TIER >= t;
function setCtx(c, t) { CLS = c; TIER = t; R = { B: BODY[c][t], T: TRIM[t], G: GEM[t] || TRIM[t], A: BANDA[t], K: BUCK[t], L: LEATH, M: MAILR }; }
const hash = (x, y, s = 0) => { let h = (x * 374761393 + y * 668265263 + s * 2246822519) >>> 0; h = ((h ^ (h >>> 13)) * 1274126177) >>> 0; return ((h ^ (h >>> 16)) >>> 0) / 4294967295; };
const lighten = (c, n) => c.map(v => Math.min(255, v + n));
function tokRGBA(tok, x, y) {
  let glow = false; if (tok.endsWith("*")) { glow = true; tok = tok.slice(0, -1); }
  let c;
  if (tok === "D") c = DARK; else if (tok === "D2") c = DARK2; else if (tok === "W") c = [255, 255, 255];
  else if (tok === "F") c = FEATHER[TIER]; else if (tok === "F2") c = lighten(FEATHER[TIER], 28); else if (tok === "F3") c = FEATHER[TIER].map(v => v * .78); else if (tok === "FS") c = lighten(FEATHER[TIER], 70);
  else {
    const L = tok[0]; let n = tok.length > 1 ? +tok[1] : 1;
    if (L === "B" && n === 1) { const h = hash(x, y), s = CLS === "warrior" && TIER >= 1 ? .04 : .07; if (h < s) n = 0; else if (h > 1 - s) n = 2; }
    c = R[L][n]; if (TIER === 5 && L === "G") glow = true;
  }
  return [c[0], c[1], c[2], glow ? 254 : 255];
}
const plateTok = (m, x, y, w, h) => { let n = 1; if (w >= 3 && h >= 3) { if (y === 0 || x === 0) n = 0; if (y === h - 1 || x === w - 1) n = 2; } return m + n; };

// ---------- vanilla layer layout (64x32) ----------
const BOX = { head: { u: 0, v: 0, w: 8, h: 8, d: 8 }, body: { u: 16, v: 16, w: 8, h: 12, d: 4 }, arm: { u: 40, v: 16, w: 4, h: 12, d: 4 }, leg: { u: 0, v: 16, w: 4, h: 12, d: 4 } };
function uvRect(b, face) {
  const { u, v, w, h, d } = BOX[b];
  switch (face) {
    case "top": return { x: u + d, y: v, w, h: d }; case "bottom": return { x: u + d + w, y: v, w, h: d };
    case "right": return { x: u, y: v + d, w: d, h }; case "front": return { x: u + d, y: v + d, w, h };
    case "left": return { x: u + d + w, y: v + d, w: d, h }; case "back": return { x: u + 2 * d + w, y: v + d, w, h };
  }
}
const FACES = ["top", "bottom", "right", "front", "left", "back"], SIDES = ["right", "front", "left", "back"];
const OLD2NEW = { front: "south", back: "north", right: "west", left: "east", top: "up", bottom: "down" };

class Tex {
  constructor() { this.t = new Array(64 * 32).fill(null); }
  F(box, face) {
    const r = uvRect(box, face), self = this;
    const fix = k => (k && k.length === 1 && "BTGAKLM".includes(k) ? k + "1" : k);
    const put = (x, y, k) => { if (x < 0 || y < 0 || x >= r.w || y >= r.h) return; self.t[(r.y + y) * 64 + r.x + x] = fix(k); };
    const f = {
      p: (x, y, k) => { put(x, y, k); return f; },
      r: (x, y, w, h, k) => { for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) put(x + i, y + j, k); return f; },
      pts: (list, k) => { list.forEach(([x, y]) => put(x, y, k)); return f; },
      plate: (x, y, w, h, c = "B") => { for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) { let n = 1; if (w >= 2 && h >= 2) { if (j === 0 || i === 0) n = 0; if (j === h - 1 || i === w - 1) n = 2; if ((j === 0 && i === w - 1) || (i === 0 && j === h - 1)) n = 1; } put(x + i, y + j, c + n); } return f; },
      all: k => f.r(0, 0, r.w, r.h, k),
      clr: (x, y, w, h) => { for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) if (x + i >= 0 && y + j >= 0 && x + i < r.w && y + j < r.h) self.t[(r.y + y + j) * 64 + r.x + x + i] = null; return f; },
    };
    return f;
  }
  finish() {
    const buf = Buffer.alloc(64 * 32 * 4);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 64; x++) { const k = this.t[y * 64 + x]; if (!k) continue; const c = tokRGBA(k, x, y), i = (y * 64 + x) * 4; buf[i] = c[0]; buf[i + 1] = c[1]; buf[i + 2] = c[2]; buf[i + 3] = c[3]; }
    return buf;
  }
}

// ---------- layer designs (partial armour: transparent where the base outfit should show) ----------
const layerDesign = {
  warrior: {
    l1(t) {
      const rg = T(5) ? "T0*" : T(2) ? "T1" : "B0";
      const mail = (f, x, y, w, h) => { for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) f.p(x + i, y + j, (i + j) % 2 ? "M1" : "M2"); };
      const fr = t.F("body", "front");
      fr.plate(0, 0, 8, 9);
      for (let x = 0; x < 8; x++) { fr.p(x, x + 1, rg); fr.p(x, 8 - x, rg); }
      fr.r(3, 6, 1, 3, "B0"); fr.r(4, 6, 1, 3, "B2");
      mail(fr, 2, 0, 4, 1); mail(fr, 3, 1, 2, 1);
      fr.pts([[0, 0], [7, 0], [0, 8], [7, 8]], "K");
      if (T(3)) { fr.pts([[1, 2], [6, 2], [1, 7], [6, 7]], "G"); fr.r(3, 4, 2, 2, "G"); }
      fr.r(0, 9, 8, 1, "A1"); fr.r(3, 9, 2, 1, "K");
      fr.r(0, 10, 8, 1, "B0"); fr.r(0, 11, 8, 1, "B2"); fr.pts([[0, 10], [7, 10]], "K");
      if (T(2)) fr.r(0, 10, 8, 1, "T1");
      t.F("body", "top").plate(0, 0, 8, 4); t.F("body", "bottom").all("D");
      const bk = t.F("body", "back");
      bk.plate(0, 0, 8, 9); bk.r(3, 1, 1, 8, "B0"); bk.r(4, 1, 1, 8, "B2");
      bk.pts([[1, 1], [2, 2], [6, 1], [5, 2], [1, 5], [2, 6], [6, 5], [5, 6]], rg);
      mail(bk, 2, 0, 4, 1); bk.pts([[0, 0], [7, 0], [0, 8], [7, 8]], "K");
      if (T(3)) bk.r(3, 3, 2, 2, "G");
      bk.r(0, 9, 8, 1, "A1"); bk.r(0, 10, 8, 1, "B0"); bk.r(0, 11, 8, 1, "B2");
      if (T(2)) bk.r(0, 10, 8, 1, "T1");
      for (const s of ["right", "left"]) { const f = t.F("body", s); f.plate(0, 0, 4, 9); f.r(0, 3, 4, 1, rg); f.p(1, 1, "K"); f.r(0, 9, 4, 1, "A1"); f.r(0, 10, 4, 1, "B0"); f.r(0, 11, 4, 1, "B2"); if (T(2)) f.r(0, 10, 4, 1, "T1"); }
      for (const s of SIDES) {
        const f = t.F("arm", s);
        f.plate(0, 0, 4, 5); f.r(0, 4, 4, 1, "B2"); mail(f, 0, 5, 4, 2);
        f.plate(0, 7, 4, 2); f.r(0, 9, 4, 1, "T"); f.plate(0, 10, 4, 2);
        if (s === "front" || s === "back") { f.r(1, 7, 2, 1, rg); f.p(1, 10, "K"); f.p(2, 10, "K"); if (T(3)) f.p(1, 8, "G"); }
        else f.p(1, 8, "K");
      }
      t.F("arm", "top").plate(0, 0, 4, 4); t.F("arm", "bottom").all("B3");
      for (const s of SIDES) {
        const f = t.F("leg", s);
        f.plate(0, 0, 4, 6); mail(f, 0, 6, 4, 1); f.plate(0, 7, 4, 3); f.plate(0, 10, 4, 2); f.r(0, 10, 4, 1, "T");
        if (s === "front") { f.r(1, 7, 1, 3, "B0"); f.r(2, 7, 1, 3, "B2"); if (T(3)) f.p(1, 8, "G"); f.pts([[0, 0], [3, 0]], "K"); }
        if (s === "front" || s === "back") f.p(1, 2, rg);
      }
      t.F("leg", "top").all("B2"); t.F("leg", "bottom").all("D");
    },
    l2(t) {
      const mail = (f, w, h) => { for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) f.p(i, j, (i + j) % 2 ? "M1" : "M2"); };
      for (const s of SIDES) { const w = s === "front" || s === "back" ? 8 : 4; mail(t.F("body", s), w, 12); mail(t.F("leg", s), 4, 12); }
    },
  },
  archer: {
    l1(t) {
      const fr = t.F("body", "front");
      fr.plate(0, 0, 3, 9, "L").plate(5, 0, 3, 9, "L"); fr.r(2, 0, 1, 9, "L2"); fr.r(5, 0, 1, 9, "L2");
      fr.pts([[3, 1], [4, 2], [3, 3], [4, 4], [3, 5], [4, 6], [3, 7]], "T"); fr.r(0, 8, 3, 1, "L2"); fr.r(5, 8, 3, 1, "L2");
      for (let i = 0; i < 9; i++) { const x = Math.round(i * 7 / 8) - 0; fr.p(x, i, "D2"); fr.p(x + 1, i, "L3"); fr.p(x - 1, i, "L3"); }
      fr.p(1, 0, "D2"); if (T(2)) fr.pts([[1, 5], [6, 4]], "T"); if (T(3)) fr.pts([[1, 2], [6, 2]], "G");
      const bk = t.F("body", "back"); bk.plate(0, 0, 8, 9, "L"); bk.r(0, 8, 8, 1, "L2");
      for (let i = 0; i < 9; i++) { const x = 7 - Math.round(i * 7 / 8); bk.p(x, i, "D2"); bk.p(x - 1, i, "L3"); bk.p(x + 1, i, "L3"); }
      if (T(2)) bk.pts([[1, 6], [6, 2]], "T");
      for (const s of ["right", "left"]) { const f = t.F("body", s); f.plate(0, 0, 4, 9, "L"); f.r(0, 8, 4, 1, "L2"); f.pts([[1, 2], [2, 4]], "T"); }
      for (const s of SIDES) { const f = t.F("arm", s); f.plate(0, 9, 4, 3, "L"); f.r(0, 9, 4, 1, "T"); f.p(1, 10, "D2"); if (T(2)) f.pts([[0, 9], [3, 9]], "K"); }
      t.F("arm", "bottom").all("L3");
      for (const s of SIDES) {
        const f = t.F("leg", s); f.plate(0, 6, 4, 6, "L"); f.r(0, 6, 4, 1, "T"); f.r(0, 11, 4, 1, "D");
        f.r(0, 8, 4, 1, "D2"); f.r(0, 10, 4, 1, "D2"); f.p(1, 9, "T");
        if (T(2)) f.pts([[0, 6], [3, 6]], "K"); if (T(3) && s === "front") f.p(1, 8, "G");
      }
      t.F("leg", "bottom").all("D");
    },
    l2(t) {
      for (const s of SIDES) { const w = s === "front" || s === "back" ? 8 : 4; const f = t.F("body", s); f.r(0, 9, w, 2, "L"); f.r(0, 9, w, 1, "L0"); f.r(0, 10, w, 1, "L2"); }
      t.F("body", "front").r(3, 9, 2, 2, "K").p(3, 10, "D").p(4, 10, "D");
      for (const s of SIDES) { const f = t.F("leg", s); f.r(0, 3, 4, 1, "L"); f.r(0, 4, 4, 1, "L2"); }
      t.F("leg", "front").p(1, 3, "K");
    },
  },
  mage: {
    l1(t) {
      const fr = t.F("body", "front");
      fr.plate(0, 0, 3, 9).plate(5, 0, 3, 9); fr.r(2, 1, 1, 8, "T0"); fr.r(5, 1, 1, 8, "T0"); fr.r(0, 0, 3, 1, "T"); fr.r(5, 0, 3, 1, "T");
      if (T(2)) { fr.r(2, 1, 1, 8, "T"); fr.r(5, 1, 1, 8, "T"); } fr.pts([[1, 3], [6, 3], [1, 6], [6, 6]], T(1) ? "T" : "T0");
      if (T(3)) fr.pts([[1, 4], [6, 4]], "G"); if (T(5)) fr.pts([[1, 2], [6, 2], [1, 5], [6, 5]], "G");
      const bk = t.F("body", "back"); bk.plate(0, 0, 8, 9); bk.r(0, 0, 8, 1, "T"); bk.r(0, 8, 8, 1, "T0");
      bk.pts([[3, 2], [4, 2], [2, 3], [5, 3], [2, 4], [5, 4], [3, 5], [4, 5], [3, 6], [4, 6]], T(2) ? "T" : "T0"); bk.pts([[3, 3], [4, 4]], "G"); if (T(3)) bk.r(3, 3, 2, 2, "G");
      for (const s of ["right", "left"]) { const f = t.F("body", s); f.plate(0, 0, 4, 9); f.r(0, 0, 4, 1, "T"); f.r(0, 8, 4, 1, "T0"); f.pts([[1, 4], [2, 5]], T(2) ? "T" : "T0"); }
      for (const s of SIDES) { const f = t.F("arm", s); f.plate(0, 3, 4, 9); f.r(0, 3, 4, 1, "T0"); f.r(0, 9, 4, 3, "B2"); f.r(0, 9, 4, 1, "T"); f.r(0, 11, 4, 1, "T"); f.pts([[1, 5], [2, 6], [1, 7]], T(2) ? "T" : "B3"); if (T(3)) f.pts([[1, 10], [2, 10]], "G"); }
      t.F("arm", "bottom").all("B3");
      for (const s of SIDES) { const f = t.F("leg", s); f.plate(0, 8, 4, 4); f.r(0, 8, 4, 1, "T"); f.r(0, 11, 4, 1, "D"); f.r(0, 10, 4, 1, "B3"); if (T(2)) f.pts([[1, 9], [2, 9]], "T"); if (T(3) && s === "front") f.pts([[1, 9], [2, 9]], "G"); }
      t.F("leg", "bottom").all("D");
    },
    l2(t) {
      for (const s of SIDES) { const w = s === "front" || s === "back" ? 8 : 4; const f = t.F("body", s); f.r(0, 9, w, 2, "A"); f.r(0, 9, w, 1, "A0"); f.r(0, 10, w, 1, "A2"); }
      if (T(2)) t.F("body", "front").pts([[0, 9], [7, 9]], "K");
    },
  },
};
function buildLayers(c, ti) {
  setCtx(c, ti);
  const l1 = new Tex(), l2 = new Tex();
  layerDesign[c].l1(l1); layerDesign[c].l2(l2);
  return [l1.finish(), l2.finish()];
}

// ---------- element models (Minecraft-style cuboids, world = mannequin coords: head x/z -4..4, y 24..32) ----------
const E6 = ["north", "south", "east", "west", "up", "down"];
const E = o => Object.assign({ mat: "B", edge: true, bone: null, faces: E6 }, o);
function mirrorEl(e) {
  const m = { ...e, mir: true };
  m.from = [-e.to[0], e.from[1], e.from[2]]; m.to = [-e.from[0], e.to[1], e.to[2]];
  if (e.rot) m.rot = { o: [-e.rot.o[0], e.rot.o[1], e.rot.o[2]], axis: e.rot.axis, angle: e.rot.axis === "x" ? e.rot.angle : -e.rot.angle };
  if (e.bone) m.bone = e.bone.replace(/^r/, "L").replace(/^l/, "r").replace(/^L/, "l");
  return m;
}
const both = (e, arr) => { arr.push(e, mirrorEl(e)); };
const rotX = (o, a) => ({ o, axis: "x", angle: a }), rotZ = (o, a) => ({ o, axis: "z", angle: a });
const sizeOf = e => [e.to[0] - e.from[0], e.to[1] - e.from[1], e.to[2] - e.from[2]];
function faceDim(e, f) {
  const [sx, sy, sz] = sizeOf(e), c = v => Math.max(1, Math.ceil(v - 1e-6));
  return f === "north" || f === "south" ? [c(sx), c(sy)] : f === "east" || f === "west" ? [c(sz), c(sy)] : [c(sx), c(sz)];
}
function pack(rects) {
  for (const N of [16, 32, 64, 128, 256]) {
    const order = [...rects].sort((a, b) => b.h - a.h || b.w - a.w); let x = 0, y = 0, rowH = 0, ok = true;
    for (const r of order) {
      if (r.w > N || r.h > N) { ok = false; break; }
      if (x + r.w > N) { x = 0; y += rowH + 1; rowH = 0; }
      if (y + r.h > N) { ok = false; break; }
      r.x = x; r.y = y; x += r.w + 1; rowH = Math.max(rowH, r.h);
    }
    if (ok) return N;
  }
  throw new Error("pack overflow");
}
function buildModel(els) {
  const rects = [];
  for (const e of els) { e._f = {}; for (const f of e.faces) { const [w, h] = faceDim(e, f); const r = { w, h }; e._f[f] = r; rects.push(r); } }
  const N = pack(rects), buf = Buffer.alloc(N * N * 4);
  for (const e of els) for (const f of e.faces) {
    const r = e._f[f];
    for (let y = 0; y < r.h; y++) for (let x = 0; x < r.w; x++) {
      let ff = f, xx = x; if (e.mir) { xx = r.w - 1 - x; if (f === "east") ff = "west"; else if (f === "west") ff = "east"; }
      let tok = e.deco && e.deco(ff, xx, y, r.w, r.h, e);
      if (!tok) tok = e.edge ? plateTok(e.mat, xx, y, r.w, r.h) : e.mat + "1";
      const c = tokRGBA(tok, r.x + x, r.y + y), i = ((r.y + y) * N + r.x + x) * 4; buf[i] = c[0]; buf[i + 1] = c[1]; buf[i + 2] = c[2]; buf[i + 3] = c[3];
    }
  }
  return { els, N, buf };
}
const sparkTok = () => (T(5) ? "G1*" : T(4) ? "T1" : T(3) ? "G0" : T(2) ? "T1" : "T0");
const sparkle = (f, x, y, w, h, rate = .07) => (hash(x * 7 + f.length, y * 13 + w, 5) < rate ? sparkTok() : null);
const ring = (f, x, y, w, h) => (x === 0 || y === 0 || x === w - 1 || y === h - 1 ? "K" : null);

// ----- hats -----
function hatMage() {
  const els = [], t = TIER;
  const bands = [[-9, -8, 3], [-8, -6, 6], [-6, -4, 8], [-4, 4, 9], [4, 6, 8], [6, 8, 6], [8, 9, 3]];
  for (const [z0, z1, hw] of bands) els.push(E({ from: [-hw, 30, z0], to: [hw, 31, z1], edge: false, deco: (f, x, y) => (f === "up" ? (t >= 2 && (x + y) % 5 === 0 && hash(x, y) < .5 ? "B0" : null) : f === "down" ? "B3" : "T1") }));
  els.push(E({ from: [-5.5, 31, -5.5], to: [5.5, 34, 5.5], mat: "A", faces: E6, deco: (f, x, y, w, h) => (f === "south" || f === "north") && t >= 4 && x % 3 === 1 && y === 1 ? "T1" : null }));
  // buckle
  els.push(E({ from: [-2, 31, 5.5], to: [2, 34, 6.1], mat: "K", deco: (f, x, y, w, h) => { if (f !== "south") return "K1"; if (x === 0 || y === 0 || x === w - 1 || y === h - 1) return "K1"; return t >= 3 ? "G1" : "D"; } }));
  if (t >= 3) for (const sx of [-1, 1]) els.push(E({ from: [sx > 0 ? 5 : -5.6, 31.8, -0.7], to: [sx > 0 ? 5.6 : -5, 33.2, 0.7], mat: "G", edge: false }));
  const cone = [[10, 34, 35.6], [9, 35.6, 37.2], [8, 37.2, 38.8], [7, 38.8, 40.4], [6, 40.4, 42]];
  cone.forEach(([w, y0, y1], i) => els.push(E({ from: [-w / 2, y0, -w / 2], to: [w / 2, y1, w / 2], edge: false,
    deco: (f, x, y, fw, fh) => {
      if (f === "up") return i === 4 ? "B1" : null; if (f === "down") return "B3";
      if (t >= 4 && y === fh - 1 && i % 2 === 0) return "T1";
      if (t >= 5 && ((x + i * 2) % 5 === 2) && y === 0) return "G1*";
      if (t >= 5 && ((x === 1 && y === 1) || (x === 2 && y === 0))) return "G1*";
      const s = sparkle(f, x + i * 3, y, fw, fh, t >= 5 ? .09 : .05); if (s) return s;
      return null;
    } })));
  // bent tip: 22.5 then 45 then 45 (backward lean)
  const segs = [[5, 2.4, -22.5], [4, 2.4, -45], [3, 2.4, -45]];
  let b = [0, 42, 0];
  segs.forEach(([w, L, a], i) => {
    els.push(E({ from: [-w / 2, b[1] - .2, b[2] - w / 2], to: [w / 2, b[1] + L, b[2] + w / 2], rot: rotX([...b], a), edge: false, deco: f => (f === "down" ? "B3" : "B1") }));
    const r = a * Math.PI / 180; b = [0, b[1] + L * Math.cos(r), b[2] + L * Math.sin(r)];
  });
  if (t >= 5) { const c = [0, b[1] + 1.6, b[2] - .6]; els.push(E({ from: [-1.5, c[1] - .5, c[2] - .5], to: [1.5, c[1] + .5, c[2] + .5], mat: "T", edge: false, deco: () => "T0*" }), E({ from: [-.5, c[1] - 1.5, c[2] - .5], to: [.5, c[1] + 1.5, c[2] + .5], mat: "T", edge: false, deco: () => "T0*" })); }
  else if (t >= 2) els.push(E({ from: [-.7, b[1] + .2, b[2] - .7], to: [.7, b[1] + 1.6, b[2] + .7], mat: t >= 3 ? "G" : "T", edge: false }));
  return els;
}
function hatArcher() {
  const els = [], t = TIER;
  const bands = [[-9, -8, 3, 31], [-8, -6, 6, 31], [-6, -4, 8, 30], [-4, 4, 8, 30], [4, 6, 8, 30], [6, 8, 6, 31], [8, 9, 3, 31]];
  for (const [z0, z1, hw, y0] of bands) els.push(E({ from: [-hw, y0, z0], to: [hw, y0 + 1, z1], edge: false, deco: (f, x, y) => (f === "up" ? (hash(x + z0, y) < .1 ? "B0" : (t >= 2 && (x + y) % 6 === 0 ? "B2" : null)) : f === "down" ? "B3" : (t >= 2 ? "T0" : "B2")) }));
  els.push(E({ from: [-4.5, 31, -4.5], to: [4.5, 34.2, 4.5], edge: false, deco: (f, x, y) => (f === "up" ? "B0" : f === "down" ? "B3" : null) }));
  els.push(E({ from: [-3.5, 34.2, -3.5], to: [3.5, 35, 3.5], edge: false, deco: (f, x, y, w) => (f === "up" ? (x === 3 ? "B2" : "B0") : null) }));
  els.push(E({ from: [-5, 31, -5], to: [5, 33, 5], mat: "A", deco: (f, x, y, w) => (t >= 4 && (f !== "up" && f !== "down") && x % 3 === 0 && y === 0 ? "T1" : null) }));
  els.push(E({ from: [-1.5, 31, 5], to: [1.5, 33, 5.6], mat: "K", deco: (f, x, y, w, h) => { if (f !== "south") return "K1"; if (x === 1) return t >= 3 ? "G1" : "D"; return "K1"; } }));
  if (t >= 3) els.push(E({ from: [-5.6, 31.5, -1], to: [-5, 32.5, 0], mat: "G", edge: false }));
  // long feather at right side of the band angled backward (3 segments)
  const fe = [[5.5, 4.8, -22.5], [4.6, 4.8, -45], [3.4, 3.2, -45]];
  let b = [5, 32, -1];
  fe.forEach(([w, L, a], i) => {
    els.push(E({ from: [b[0] - .5, b[1] - .3, b[2] - w / 2], to: [b[0] + .5, b[1] + L, b[2] + w / 2], rot: rotX([...b], a), edge: false,
      deco: (f, x, y, fw, fh) => {
        if (f === "up" || f === "down") return i === 2 ? "F3" : "F2";
        if (f === "north" || f === "south") return "F";
        const cx = Math.floor(fw / 2); if (x === cx) return t >= 5 ? "FS*" : "FS"; if (t >= 5 && y === fh - 1 && i === 2) return "T1*";
        if (i === 2 && y > fh - 3 && Math.abs(x - cx) > 0 && y - Math.abs(x - cx) > fh - 4) return null;
        if (Math.abs(x - cx) === 0) return "FS"; const d = (y + Math.abs(x - cx)) % 3; return d === 0 ? "F2" : d === 1 ? "F3" : "F";
      } }));
    const r = a * Math.PI / 180; b = [b[0], b[1] + L * Math.cos(r), b[2] + L * Math.sin(r)];
  });
  return els;
}
function hatWarrior() {
  const els = [], t = TIER;
  const rimT = t >= 5 ? "T0*" : t >= 2 ? "T2" : "B3";
  const lowRim = (f, x, y, w, h) => (y === h - 1 && f !== "up" && f !== "down" ? rimT : null);
  const upRim = (f, x, y, w, h) => (f === "up" && (x === 0 || y === 0 || x === w - 1 || y === h - 1) ? "B0" : null);
  const rM = t >= 2 ? "T" : "B";
  const seam = (f, x, y, w, h) => (y === h - 1 && f !== "up" && f !== "down" ? "B3" : null);
  // mail aventail (neck)
  els.push(E({ from: [-4.5, 23.6, -4.5], to: [4.5, 26.2, 4.5], mat: "M", edge: false, deco: (f, x, y) => ((x + y) % 2 ? "M1" : "M2") }));
  // L1 jaw / bevor
  els.push(E({ from: [-4.7, 26, -4.7], to: [4.7, 29, 4.7], deco: (f, x, y, w, h) => {
    if (f === "south") { if ((x === 2 || x === 3 || x === w - 3 || x === w - 4) && y === 1) return "D"; if (y === 0 && (x === 0 || x === w - 1)) return "K"; }
    return lowRim(f, x, y, w, h) || upRim(f, x, y, w, h); } }));
  // L2 skull with eye slit
  els.push(E({ from: [-4.9, 29, -4.9], to: [4.9, 31.2, 4.9], deco: (f, x, y, w, h) => {
    if (f === "south") { if (y === 1 && x >= 1 && x <= w - 2) return "D"; if (y === 0 && t >= 4) return "T1"; if (y === 2) return lowRim(f, x, y, w, h); }
    if ((f === "east" || f === "west") && y === 1 && t >= 2 && x % 2 === 0) return "T0";
    return lowRim(f, x, y, w, h) || upRim(f, x, y, w, h); } }));
  // L3 / L4 / L5 dome
  els.push(E({ from: [-4.3, 31.2, -4.3], to: [4.3, 32.8, 4.3], deco: (f, x, y, w, h) => (f === "south" && y === 0 && (x === 1 || x === w - 2) ? "K" : (seam(f, x, y, w, h) || upRim(f, x, y, w, h))) }));
  els.push(E({ from: [-3.3, 32.8, -3.3], to: [3.3, 33.7, 3.3], deco: (f, x, y, w, h) => seam(f, x, y, w, h) || upRim(f, x, y, w, h) }));
  els.push(E({ from: [-2, 33.7, -2], to: [2, 34.2, 2], deco: (f, x, y, w, h) => upRim(f, x, y, w, h) }));
  // pointed visor ridge down the face + low centre ridge over the crown (no horns)
  els.push(E({ from: [-.6, 26, 4.7], to: [.6, 31.2, 5.5], mat: rM, deco: (f, x, y) => (t >= 5 && f === "south" ? "T0*" : null) }));
  els.push(E({ from: [-.45, 25.4, 4.7], to: [.45, 27, 5.7], mat: rM }));
  els.push(E({ from: [-.5, 31.2, 4.3], to: [.5, 33.4, 4.9], mat: rM, deco: (f, x, y, w, h) => (t >= 3 && f === "south" && y >= 1 && y <= 2 ? "G1" : null) }));
  els.push(E({ from: [-.5, 34.2, -2.6], to: [.5, 34.9, 2.6], mat: rM, deco: (f, x, y) => (t >= 5 && f === "up" ? "T0*" : null) }));
  els.push(E({ from: [-.5, 26, -5.5], to: [.5, 31.2, -4.7], mat: rM }));
  els.push(E({ from: [-.5, 31.2, -4.9], to: [.5, 33.4, -4.3], mat: rM }));
  return els;
}

// ----- body gear (bone-attached) -----
function pauldrons() {
  const els = [], t = TIER, rim = t >= 5 ? "T0*" : t >= 2 ? "T2" : "B3";
  const n = t >= 2 ? 4 : 3, wx = [-9.8, -10.4, -10.8, -10.8], hz = [3.4, 3.8, 3.9, 3.6];
  const mk = (i, from, to, rotO) => E({ from, to, rot: rotZ(rotO, 22.5), bone: "rarm", grp: "pauldrons", deco: (f, x, y, w, h) => {
    if (f === "up") { if (x === 0 || y === 0 || x === w - 1 || y === h - 1) return rim; if (x === 1 && (y === 1 || y === h - 2)) return "K1"; if (t >= 3 && i <= 1 && y === Math.floor(h / 2) && Math.abs(x - w / 2 + .5) < 1.2) return "G1"; return null; }
    if (f === "down") return "B3";
    if (y === h - 1) return rim; if (y === 0) return "B0";
    if (t >= 3 && i === 0 && f === "north" && y === 1 && x === Math.floor(w / 2)) return "G1";
    return null; } });
  const arr = [];
  for (let i = 0; i < n; i++) { const ytop = 25.4 - 1.6 * i; arr.push(mk(i, [wx[i], ytop - 1.8, -hz[i]], [-4.4, ytop, hz[i]], [-4.4, ytop, 0])); }
  arr.push(E({ from: [-8.2, 25.4, -2.7], to: [-4.2, 26.8, 2.7], rot: rotZ([-4.2, 25.4, 0], 22.5), bone: "rarm", grp: "pauldrons", deco: (f, x, y, w, h) => (f === "up" ? (x === 0 || y === 0 || x === w - 1 || y === h - 1 ? rim : null) : y === h - 1 ? rim : null) }));
  if (t >= 4) arr.push(E({ from: [-7, 26.8, -1], to: [-5.4, 27.8, 1], rot: rotZ([-5.4, 26.8, 0], 22.5), bone: "rarm", grp: "pauldrons", mat: "G", edge: false }));
  arr.forEach(e => both(e, els));
  return els;
}
function gearWarrior() {
  const els = [], t = TIER, rim = t >= 5 ? "T0*" : t >= 2 ? "T2" : "B3";
  els.push(...pauldrons());
  const plateDeco = (f, x, y, w, h) => (f !== "up" && f !== "down" && y === h - 1 ? rim : (y === 0 && f !== "down" ? "B0" : null));
  // couters (winged elbow cups) + gauntlet cuffs
  const couter = [
    E({ from: [-9.9, 16.6, -2.4], to: [-8.7, 20, 2.6], bone: "rarm", grp: "couters", deco: (f, x, y, w, h) => (f === "west" && x === Math.floor(w / 2) && y === Math.floor(h / 2) ? (t >= 3 ? "G1" : "K1") : (f === "west" && (x === 0 || x === w - 1 || y === h - 1) ? rim : null)) }),
    E({ from: [-10.5, 17.2, -4.2], to: [-9.3, 19.4, -2], rot: { o: [-9.7, 18.3, -2.6], axis: "y", angle: 22.5 }, bone: "rarm", grp: "couters", deco: (f, x, y, w, h) => (y === h - 1 || x === 0 ? rim : null) }),
    E({ from: [-9.9, 19.8, -1.4], to: [-8.9, 21.2, 0.6], bone: "rarm", grp: "couters", deco: plateDeco }),
  ];
  couter.forEach(e => both(e, els));
  [E({ from: [-9.5, 13.2, -3.5], to: [-2.5, 15, 3.5], bone: "rarm", grp: "cuffs", deco: (f, x, y, w, h) => (f === "up" ? null : y === 0 ? (t >= 2 ? "T1" : "B0") : y === h - 1 ? rim : null) }),
   E({ from: [-9.8, 12.2, -3.8], to: [-2.2, 13.4, 3.8], bone: "rarm", grp: "cuffs", deco: plateDeco })].forEach(e => both(e, els));
  // fauld lames (waist) wrapping the hips
  for (let i = 0; i < 3; i++) {
    const y1 = 11.6 - 1.5 * i, y0 = y1 - 2.4, zo = .8 + .35 * i;
    const d = (f, x, y, w, h) => (f !== "up" && f !== "down" && y === h - 1 ? rim : (f === "south" && t >= 3 && i === 0 && y === 1 && Math.abs(x - (w - 1) / 2) < .6 ? "G1" : (f !== "down" && y === 0 ? "B0" : ((f === "south" || f === "north") && y === 1 && (x === 1 || x === w - 2) ? "K1" : null))));
    els.push(E({ from: [-5.1, y0, 2.9], to: [5.1, y1, 2.9 + zo], grp: "fauld", deco: d }));
    els.push(E({ from: [-5.1, y0, -2.9 - zo], to: [5.1, y1, -2.9], grp: "fauld", deco: d }));
    both(E({ from: [-5 - zo, y0, -2.9], to: [-4.9, y1, 2.9], grp: "fauld", deco: d }), els);
  }
  // culet V + outer tassets + poleyns + sabaton toes
  els.push(E({ from: [-1.2, 4.6, 3.2], to: [1.2, 6.4, 4.2], grp: "fauld", deco: plateDeco }), E({ from: [-.7, 3.6, 3.3], to: [.7, 5, 4.2], grp: "fauld", deco: plateDeco }));
  both(E({ from: [-6, 6.4, -2.4], to: [-5.2, 11, 2.2], bone: "rleg", grp: "fauld", deco: (f, x, y, w, h) => (f === "west" && y === Math.floor(h / 2) && x === Math.floor(w / 2) ? "K1" : plateDeco(f, x, y, w, h)) }), els);
  both(E({ from: [-4.2, 4.4, 3.0], to: [-0.6, 7, 4.1], bone: "rleg", grp: "greaves", deco: (f, x, y, w, h) => (f === "south" ? (x === Math.floor(w / 2) ? (t >= 3 ? "G1" : rim) : y === 0 ? "B0" : null) : plateDeco(f, x, y, w, h)) }), els);
  both(E({ from: [-5.6, 4.8, -.8], to: [-4.9, 6.8, 2.6], bone: "rleg", grp: "greaves", deco: plateDeco }), els);
  both(E({ from: [-4.4, 0, 3.0], to: [.4, 2, 4.9], bone: "rleg", grp: "greaves", deco: (f, x, y, w, h) => (y === h - 1 ? rim : (f === "up" ? "B0" : null)) }), els);
  both(E({ from: [-3.6, 0, 4.9], to: [-.4, 1.4, 5.6], bone: "rleg", grp: "greaves", deco: plateDeco }), els);
  return els;
}
function gearArcher() {
  const els = [], t = TIER;
  const Q = rotZ([0, 19, -4.4], 22.5), qb = [0, 25.5];
  els.push(E({ from: [-1.7, 12.5, -5.8], to: [1.7, 25.5, -3.2], rot: Q, mat: "L", deco: (f, x, y, w, h) => (f === "up" ? "D" : f === "down" ? "L3" : y === 1 || y === h - 2 ? "T1" : y === h - 1 ? "L3" : (t >= 3 && y === 4 && x === 1 ? "G1" : null)) }));
  [-.9, 0, .9].forEach((dx, i) => {
    els.push(E({ from: [dx - .25, 25.3, -4.65], to: [dx + .25, 28.2 + (i === 1 ? .8 : 0), -4.15], rot: Q, mat: "L", edge: false, deco: () => "L0" }));
    els.push(E({ from: [dx - .6, 27.2 + (i === 1 ? .8 : 0), -4.7], to: [dx + .6, 29.4 + (i === 1 ? .8 : 0), -4.1], rot: Q, mat: "F", edge: false, deco: (f, x, y) => (f === "north" || f === "south" ? (y % 2 === 0 ? "F2" : "F") : "F3") }));
  });
  els.push(E({ from: [4.6, 10.8, -.4], to: [6.4, 13.8, 2.6], mat: "L", deco: (f, x, y, w, h) => (y === 0 ? "L3" : null) }));
  els.push(E({ from: [4.5, 13.2, -.5], to: [6.5, 14.4, 2.7], mat: "L", deco: (f, x, y, w, h) => (f === "east" ? "L0" : (t >= 2 && f === "south" && x === 1 ? "K1" : "L2")) }));
  els.push(E({ from: [3.5, 12.3, -2.5], to: [8.5, 17.6, 2.5], bone: "larm", mat: "L", deco: (f, x, y, w, h) => ((f === "up" || f === "down") ? "L3" : (y === 1 || y === h - 2) ? "D2" : (y === 0 ? "T1" : null)) }));
  els.push(E({ from: [8.4, 13, -1.8], to: [8.8, 17, 1.8], bone: "larm", mat: "T", deco: (f, x, y, w, h) => (f === "east" && t >= 3 && x === 1 && y === 1 ? "G1" : null) }));
  const sp = E({ from: [-9.2, 22.3, -2.9], to: [-3.7, 24.3, 2.9], rot: rotZ([-3.7, 24.3, 0], 22.5), bone: "rarm", mat: "L", deco: (f, x, y, w, h) => (f === "up" && t >= 2 && (x === 1 || x === w - 2) && y === 1 ? "K1" : (y === h - 1 && f !== "up" ? "L3" : null)) });
  els.push(sp);
  both(E({ from: [-5.4, 5.2, -3.5], to: [1.4, 6.9, 3.5], bone: "rleg", mat: "L", deco: (f, x, y, w, h) => (f === "up" ? "L3" : y === 0 ? "T1" : null) }), els);
  return els;
}
function gearMage() {
  const els = [], t = TIER, A = "A";
  const hem = (f, x, y, w, h) => ((f === "south" || f === "north" || f === "east" || f === "west") && y === h - 1 ? "T1" : null);
  // collar
  els.push(E({ from: [-4.8, 25, -5.5], to: [4.8, 29.5, -4.5], rot: rotX([0, 25, -5], -22.5), mat: "B", deco: (f, x, y, w, h) => (y === 0 && f !== "up" ? "T1" : (f === "south" && (x + y) % 5 === 0 && t >= 2 ? "T0" : null)) }));
  els.push(E({ from: [-4.6, 23, -4.3], to: [4.6, 25.2, -3], mat: "B" }));
  both(E({ from: [4.6, 24.4, -4.4], to: [5.4, 28.2, 1.4], rot: rotZ([4.6, 24.4, 0], -22.5), mat: "B", deco: (f, x, y, w, h) => (f === "up" || f === "down" ? null : (x === w - 1 ? "T1" : null)) }), els);
  both(E({ from: [1.1, 18.5, 2.6], to: [3.9, 24.2, 3.4], mat: "B", deco: (f, x, y, w, h) => (f === "south" ? (x === 0 ? "T1" : (t >= 3 && y === 1 && x === 1 ? "G1" : null)) : null) }), els);
  // shoulder mantles
  const mt = (f, x, y, w, h) => ((f === "south" || f === "north" || f === "east" || f === "west") && y === h - 1 ? "T1" : (f === "up" && t >= 2 && (x === 1 || x === w - 2) && y === 1 ? "T1" : null));
  both(E({ from: [-10, 24, -3.8], to: [-4, 25.6, 3.8], rot: rotZ([-4, 25, 0], 22.5), bone: "rarm", mat: "B", deco: mt }), els);
  both(E({ from: [-11, 21.8, -4.1], to: [-4.6, 23.6, 4.1], rot: rotZ([-4.6, 23.6, 0], 22.5), bone: "rarm", mat: "A", deco: mt }), els);
  if (t >= 3) both(E({ from: [-8.6, 25.6, -.7], to: [-7.2, 26.8, .7], rot: rotZ([-7.9, 25, 0], 22.5), bone: "rarm", mat: "G", edge: false }), els);
  // sleeve cuffs
  both(E({ from: [-9.6, 11.6, -3.6], to: [-2.4, 14.6, 3.6], bone: "rarm", mat: "A", deco: (f, x, y, w, h) => (f === "up" ? null : (y === h - 1 ? "T1" : (t >= 2 && y === 0 ? "T1" : null))) }), els);
  // robe tails: back + open front panels
  els.push(E({ from: [-4.4, 8, -3.7], to: [4.4, 14, -2.9], deco: (f, x, y, w, h) => (f === "north" && t >= 1 && x % 4 === 1 && y > 1 ? "B2" : (f === "north" && x === 3 && y === 3 ? "T1" : null)) }));
  els.push(E({ from: [-4.4, 2.8, -3.7], to: [4.4, 8, -2.9], rot: rotX([0, 8, -3.3], 22.5), deco: (f, x, y, w, h) => hem(f, x, y, w, h) || (f === "north" && (x === 3 || x === 4) && y > 0 && y < 4 && t >= 2 ? "T1" : null) }));
  [E({ from: [1.8, 8, 2.6], to: [4.7, 14, 3.4], deco: (f, x, y, w, h) => (f === "south" && x === 0 ? "T1" : null) }), E({ from: [1.8, 3.2, 2.6], to: [4.7, 8, 3.4], rot: rotX([3.2, 8, 3], -22.5), deco: (f, x, y, w, h) => hem(f, x, y, w, h) || (f === "south" && x === 0 ? "T1" : null) })].forEach(e => both(e, els));
  // sash knot + hanging ends
  els.push(E({ from: [-1.3, 12.6, 2.6], to: [1.3, 14.6, 3.6], mat: "A", deco: (f, x, y, w, h) => (f === "south" && t >= 3 && x === 1 && y === 0 ? "G1" : null) }));
  els.push(E({ from: [-1.4, 7, 2.9], to: [-.2, 12.6, 3.4], rot: rotX([-.8, 12.6, 3], -10 * 0), mat: "A", deco: (f, x, y, w, h) => (y === h - 1 ? "T1" : null) }));
  els.push(E({ from: [.2, 6, 2.9], to: [1.4, 12.6, 3.4], mat: "A", deco: (f, x, y, w, h) => (y === h - 1 ? "T1" : null) }));
  // wide boot cuffs
  both(E({ from: [-5.2, 4.2, -3.5], to: [1.2, 5.8, 3.5], bone: "rleg", mat: "A", deco: (f, x, y, w, h) => (f === "up" ? "B3" : (y === 0 ? "T1" : null)) }), els);
  return els;
}
const HATS = { warrior: hatWarrior, archer: hatArcher, mage: hatMage }, GEARS = { warrior: gearWarrior, archer: gearArcher, mage: gearMage };

// ---------- mannequin skin ----------
function makeSkin() {
  const buf = Buffer.alloc(64 * 32 * 4), mix = (a, b) => a.map((v, i) => (v + b[i]) >> 1);
  const SKIN = hex("#dcaa88"), SHIRT = hex("#8a8470"), PANTS = hex("#4f5463"), HAIR = hex("#4a382c"), SHOE = hex("#2f2a30");
  const paint = (box, fn) => { for (const fc of FACES) { const r = uvRect(box, fc); for (let y = 0; y < r.h; y++) for (let x = 0; x < r.w; x++) {
    let c = fn(fc, x, y, r); const sh = fc === "top" ? 1.1 : fc === "bottom" ? .6 : fc === "left" || fc === "back" ? .82 : 1; const e = (x === 0 || y === 0 || x === r.w - 1 || y === r.h - 1) ? .95 : 1;
    const i = ((r.y + y) * 64 + r.x + x) * 4; buf[i] = Math.min(255, c[0] * sh * e); buf[i + 1] = Math.min(255, c[1] * sh * e); buf[i + 2] = Math.min(255, c[2] * sh * e); buf[i + 3] = 255; } } };
  paint("head", (fc, x, y) => { if (fc === "bottom") return SKIN; if (fc === "top" || (fc !== "front" && y < 3) || (fc === "front" && y < 2) || (fc === "front" && y === 2 && (x < 1 || x > 6))) return HAIR; if (fc === "front" && y === 5 && [1, 2, 5, 6].includes(x)) return hex("#1b1620"); if (fc === "front" && y === 7 && (x === 3 || x === 4)) return hex("#a06a55"); return SKIN; });
  paint("body", () => SHIRT);
  paint("arm", (fc, x, y) => (fc === "top" || y < 4 ? SHIRT : fc === "bottom" ? SKIN : y < 5 ? mix(SHIRT, SKIN) : SKIN));
  paint("leg", (fc, x, y) => (fc === "bottom" ? SHOE : y >= 10 ? SHOE : PANTS));
  return buf;
}

// ---------- software renderer ----------
const vsub = (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
const vcross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const vnorm = a => { const l = Math.hypot(...a) || 1; return a.map(v => v / l); };
const vdot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
function faceDefs(mn, mx) {
  const [x0, y0, z0] = mn, [x1, y1, z1] = mx, sx = x1 - x0, sy = y1 - y0, sz = z1 - z0;
  return [
    { f: "south", o: [x0, y1, z1], u: [sx, 0, 0], v: [0, -sy, 0] }, { f: "north", o: [x1, y1, z0], u: [-sx, 0, 0], v: [0, -sy, 0] },
    { f: "west", o: [x0, y1, z0], u: [0, 0, sz], v: [0, -sy, 0] }, { f: "east", o: [x1, y1, z1], u: [0, 0, -sz], v: [0, -sy, 0] },
    { f: "up", o: [x0, y1, z0], u: [sx, 0, 0], v: [0, 0, sz] }, { f: "down", o: [x0, y0, z1], u: [sx, 0, 0], v: [0, 0, -sz] },
  ];
}
const SS = 3, DEG = Math.PI / 180;
function rotPt(p, rot) {
  if (!rot) return p; const a = rot.angle * DEG, c = Math.cos(a), s = Math.sin(a), [ox, oy, oz] = rot.o, x = p[0] - ox, y = p[1] - oy, z = p[2] - oz;
  if (rot.axis === "x") return [p[0], oy + y * c - z * s, oz + y * s + z * c];
  if (rot.axis === "z") return [ox + x * c - y * s, oy + x * s + y * c, p[2]];
  return [ox + x * c + z * s, p[1], oz - x * s + z * c];
}
function renderFigure(items, yaw, W, H, scale, cyc = 15, pitch = 20) {
  const w = W * SS, h = H * SS, s = scale * SS;
  const col = new Float32Array(w * h * 4), zb = new Float32Array(w * h).fill(-1e9);
  const th = yaw * DEG, ph = pitch * DEG, cy = Math.cos(th), sy_ = Math.sin(th), cp = Math.cos(ph), sp = Math.sin(ph);
  const L = vnorm([-0.45, 0.75, 0.55]);
  const cam = p => { const x = p[0] * cy + p[2] * sy_, z = -p[0] * sy_ + p[2] * cy; return [x, p[1] * cp - z * sp, p[1] * sp + z * cp]; };
  for (const it of items) {
    const { min, max, rot, pose, faces, tex, flip } = it;
    const tr = p => { p = rotPt(p, rot); if (pose) p = rotPt(p, { o: [0, pose.pivot[0], pose.pivot[1]], axis: "x", angle: pose.ang }); return cam(p); };
    const ctr = tr([(min[0] + max[0]) / 2, (min[1] + max[1]) / 2, (min[2] + max[2]) / 2]);
    for (const d of faceDefs(min, max)) {
      const rect = faces[d.f]; if (!rect) continue; const O = d.o, U = d.u, V = d.v;
      const cs = [[0, 0], [1, 0], [1, 1], [0, 1]].map(([a, c]) => ({ a, c, p: tr([O[0] + U[0] * a + V[0] * c, O[1] + U[1] * a + V[1] * c, O[2] + U[2] * a + V[2] * c]) }));
      let n = vnorm(vcross(vsub(cs[1].p, cs[0].p), vsub(cs[3].p, cs[0].p)));
      const fc = [0, 1, 2].map(i => (cs[0].p[i] + cs[2].p[i]) / 2);
      if (vdot(n, vsub(fc, ctr)) < 0) n = n.map(v => -v);
      const lit = .58 + .42 * Math.max(0, vdot(n, L)), shade = Math.max(it.minSh || 0, n[2] > 0 ? lit : lit * .82);
      for (const tri of [[0, 1, 2], [0, 2, 3]]) {
        const A = tri.map(i => cs[i]);
        const pr = A.map(q => [w / 2 + q.p[0] * s, h / 2 - (q.p[1] - cyc * cp) * s, q.p[2]]);
        const minx = Math.max(0, Math.floor(Math.min(...pr.map(q => q[0])))), maxx = Math.min(w - 1, Math.ceil(Math.max(...pr.map(q => q[0]))));
        const miny = Math.max(0, Math.floor(Math.min(...pr.map(q => q[1])))), maxy = Math.min(h - 1, Math.ceil(Math.max(...pr.map(q => q[1]))));
        const den = (pr[1][1] - pr[2][1]) * (pr[0][0] - pr[2][0]) + (pr[2][0] - pr[1][0]) * (pr[0][1] - pr[2][1]);
        if (Math.abs(den) < 1e-9) continue;
        for (let y = miny; y <= maxy; y++) for (let x = minx; x <= maxx; x++) {
          const px = x + .5, py = y + .5;
          const l0 = ((pr[1][1] - pr[2][1]) * (px - pr[2][0]) + (pr[2][0] - pr[1][0]) * (py - pr[2][1])) / den;
          const l1 = ((pr[2][1] - pr[0][1]) * (px - pr[2][0]) + (pr[0][0] - pr[2][0]) * (py - pr[2][1])) / den;
          const l2 = 1 - l0 - l1;
          if (l0 < -1e-6 || l1 < -1e-6 || l2 < -1e-6) continue;
          const z = l0 * pr[0][2] + l1 * pr[1][2] + l2 * pr[2][2];
          if (z <= zb[y * w + x] + 1e-5) continue;
          let a = l0 * A[0].a + l1 * A[1].a + l2 * A[2].a; const c = l0 * A[0].c + l1 * A[1].c + l2 * A[2].c;
          if (flip) a = 1 - a;
          const tx = rect.x + Math.min(rect.w - 1, Math.max(0, Math.floor(a * rect.w))), ty = rect.y + Math.min(rect.h - 1, Math.max(0, Math.floor(c * rect.h)));
          const ti = (ty * tex.w + tx) * 4;
          if (tex.b[ti + 3] < 128) continue;
          zb[y * w + x] = z;
          const sh = tex.b[ti + 3] === 254 ? 1.12 : shade, o = (y * w + x) * 4;
          col[o] = Math.min(255, tex.b[ti] * sh); col[o + 1] = Math.min(255, tex.b[ti + 1] * sh); col[o + 2] = Math.min(255, tex.b[ti + 2] * sh); col[o + 3] = 1;
        }
      }
    }
  }
  const o = Buffer.alloc(W * H * 4);
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    let r = 0, g = 0, bl = 0, al = 0;
    for (let j = 0; j < SS; j++) for (let i = 0; i < SS; i++) { const k = ((y * SS + j) * w + x * SS + i) * 4; r += col[k]; g += col[k + 1]; bl += col[k + 2]; al += col[k + 3]; }
    const q = (y * W + x) * 4; if (al > 0) { o[q] = r / al; o[q + 1] = g / al; o[q + 2] = bl / al; o[q + 3] = 255 * al / (SS * SS); }
  }
  return o;
}

// ---------- figure assembly ----------
const AR = 10, LG = 9;
const PARTS = {
  head: { mn: [-4, 24, -4], mx: [4, 32, 4], uv: "head" }, body: { mn: [-4, 12, -2], mx: [4, 24, 2], uv: "body" },
  rarm: { mn: [-8, 12, -2], mx: [-4, 24, 2], uv: "arm", pose: { pivot: [22, 0], ang: -AR } }, larm: { mn: [4, 12, -2], mx: [8, 24, 2], uv: "arm", mirror: true, pose: { pivot: [22, 0], ang: AR } },
  rleg: { mn: [-4, 0, -2], mx: [0, 12, 2], uv: "leg", pose: { pivot: [12, 0], ang: LG } }, lleg: { mn: [0, 0, -2], mx: [4, 12, 2], uv: "leg", mirror: true, pose: { pivot: [12, 0], ang: -LG } },
};
const POSE = { rarm: PARTS.rarm.pose, larm: PARTS.larm.pose, rleg: PARTS.rleg.pose, lleg: PARTS.lleg.pose };
function vanItem(name, buf, d) {
  const p = PARTS[name], faces = {};
  for (const f of FACES) faces[OLD2NEW[f]] = uvRect(p.uv, f);
  if (p.mirror) { const e = faces.east; faces.east = faces.west; faces.west = e; }
  return { min: p.mn.map(v => v - d), max: p.mx.map(v => v + d), faces, tex: { b: buf, w: 64, h: 32 }, flip: !!p.mirror, pose: p.pose };
}
function modelItems(model) {
  return model.els.map(e => ({ min: e.from, max: e.to, rot: e.rot, pose: e.bone ? POSE[e.bone] : null, faces: e._f, tex: { b: model.buf, w: model.N, h: model.N }, minSh: .8 }));
}
const skinBuf = makeSkin();
function figureItems({ l1, l2, gear, hat, skinOnlyHead }) {
  const items = [];
  const names = skinOnlyHead ? ["head"] : ["head", "body", "rarm", "larm", "rleg", "lleg"];
  for (const n of names) items.push(vanItem(n, skinBuf, 0));
  if (l2) for (const n of ["body", "rleg", "lleg"]) items.push(vanItem(n, l2, .5));
  if (l1) for (const n of ["body", "rarm", "larm", "rleg", "lleg"]) items.push(vanItem(n, l1, 1));
  if (gear) items.push(...modelItems(gear));
  if (hat) items.push(...modelItems(hat));
  return items;
}

// ---------- model export (Minecraft-style JSON) ----------
const OK_ANG = [-45, -22.5, 0, 22.5, 45];
function exportHat(c, ti, model, name = `hat_${c}_t${ti}`, yoff = 24, frame = "head-local: head cube = x4..12, y0..8, z4..12 (player front = south/+z); rotation = right-handed about origin (+x angle tilts +y toward +z)") {
  const N = model.N, probs = [];
  const sh = v => [v[0] + 8, v[1] - yoff, v[2] + 8];
  const r4 = v => Math.round(v * 1e4) / 1e4;
  const elements = model.els.map((e, i) => {
    const o = { name: `${c}_${i}${e.bone ? "_" + e.bone : ""}`, from: sh(e.from).map(r4), to: sh(e.to).map(r4), faces: {} };
    if (e.rot) o.rotation = { origin: sh(e.rot.o).map(r4), axis: e.rot.axis, angle: e.rot.angle };
    for (const f of e.faces) { const r = e._f[f]; o.faces[f] = { uv: [r.x, r.y, r.x + r.w, r.y + r.h].map(v => r4(v * 16 / N)), texture: "#0" }; }
    return o;
  });
  for (const e of elements) {
    for (const v of [...e.from, ...e.to]) if (v < -16 || v > 32) probs.push(`${name}: coord ${v} out of -16..32`);
    if (e.rotation && !OK_ANG.includes(e.rotation.angle)) probs.push(`${name}: bad angle ${e.rotation.angle}`);
    if (e.rotation && !["x", "y", "z"].includes(e.rotation.axis)) probs.push(`${name}: bad axis`);
    for (const f of Object.values(e.faces)) for (const v of f.uv) if (v < 0 || v > 16) probs.push(`${name}: uv ${v} out of 0..16`);
    for (let k = 0; k < 3; k++) if (e.to[k] - e.from[k] < 0) probs.push(`${name}: negative size`);
  }
  const json = { credit: "pipi preview - worn armour v3", texture_size: [N, N], textures: { "0": `pipi:item/${name}`, particle: `pipi:item/${name}` },
    _frame: frame, elements,
    display: { head: { translation: [0, 0, 0], scale: [1, 1, 1] } } };
  fs.writeFileSync(path.join(mdir, `${name}.json`), JSON.stringify(json, null, 2));
  fs.writeFileSync(path.join(mdir, `${name}.png`), png(cleanA(model.buf), N, N));
  return { probs, n: elements.length, N };
}

// ---------- build everything ----------
const FW = 210, FH = 360, SCALE = 6.1, CY = 26;
const figs = {}, hatImg = {}, layers = {}, stat = [], allProbs = [], gearM = {}, hatM = {};
for (const c of CLASSES) for (let ti = 0; ti < 6; ti++) {
  const [l1, l2] = buildLayers(c, ti);
  setCtx(c, ti);
  const hat = buildModel(HATS[c]()); const gearEls = GEARS[c]();
  const ex = exportHat(c, ti, hat); allProbs.push(...ex.probs);
  if (c === "warrior") for (const g of ["pauldrons", "couters", "cuffs", "fauld", "greaves"]) {
    const sub = gearEls.filter(e => e.grp === g), gm = buildModel(sub);
    const gx = exportHat(c, ti, gm, `${g}_${c}_t${ti}`, 0, "mannequin-space + (8,0,8): player feet-centre = x8,z8,y0; front = south/+z; bone tag in element name says which limb the piece follows (rarm/larm/rleg/lleg, none = body); rotation single-axis about origin"); allProbs.push(...gx.probs); stat.push(`  ${g}: ${gx.n} elements tex ${gx.N}x${gx.N}`);
  }
  const gear = buildModel(gearEls); stat.push(`${c} T${ti}: hat ${ex.n} elements tex ${ex.N}x${ex.N}; gear ${gear.els.length} elements tex ${gear.N}x${gear.N}`);
  layers[`${c}_${ti}`] = [l1, l2]; gearM[`${c}_${ti}`] = gear; hatM[`${c}_${ti}`] = hat;
  const items = figureItems({ l1, l2, gear, hat });
  figs[`${c}_${ti}`] = { front: renderFigure(items, -32, FW, FH, SCALE, CY), back: renderFigure(items, 148, FW, FH, SCALE, CY) };
  hatImg[`${c}_${ti}`] = renderFigure(figureItems({ hat, skinOnlyHead: true }), -32, 170, 230, 7, 38);
  fs.writeFileSync(path.join(out, `${c}_t${ti}_layer_1.png`), png(cleanA(l1), 64, 32));
  fs.writeFileSync(path.join(out, `${c}_t${ti}_layer_2.png`), png(cleanA(l2), 64, 32));
}
fs.writeFileSync(path.join(out, "mannequin.png"), png(renderFigure(figureItems({}), -32, FW, FH, SCALE, CY), FW, FH));

// ---------- sheets ----------
const FONT = {
  A: "010101111101101", B: "110101110101110", C: "011100100100011", D: "110101101101110", E: "111100110100111", F: "111100110100100", G: "011100101101011",
  H: "101101111101101", I: "111010010010111", J: "001001001101010", K: "101101110101101", L: "100100100100111", M: "101111111101101", N: "110101101101101",
  O: "010101101101010", P: "110101110100100", Q: "010101101111011", R: "110101110101101", S: "011100010001110", T: "111010010010010", U: "101101101101111",
  V: "101101101101010", W: "101101111111101", X: "101101010101101", Y: "101101010010010", Z: "111001010100111",
  0: "111101101101111", 1: "010110010010111", 2: "110001010100111", 3: "110001010001110", 4: "101101111001001", 5: "111100110001110",
  "/": "001001010100100", " ": "000000000000000", "-": "000000111000000", "(": "010100100100010", ")": "010001001001010",
};
const canvas = (w, h, bg) => { const b = Buffer.alloc(w * h * 4); for (let i = 0; i < w * h; i++) { b[i * 4] = bg[0]; b[i * 4 + 1] = bg[1]; b[i * 4 + 2] = bg[2]; b[i * 4 + 3] = 255; } return { w, h, b }; };
function fill(cv, x0, y0, w, h, c, a = 255) { const k = a / 255; for (let y = Math.floor(y0); y < y0 + h; y++) for (let x = Math.floor(x0); x < x0 + w; x++) { if (x < 0 || y < 0 || x >= cv.w || y >= cv.h) continue; const i = (y * cv.w + x) * 4; for (let q = 0; q < 3; q++) cv.b[i + q] = c[q] * k + cv.b[i + q] * (1 - k); } }
function text(cv, s, x, y, sc, c) { for (const ch of s.toUpperCase()) { const g = FONT[ch] || FONT[" "]; for (let i = 0; i < 15; i++) if (g[i] === "1") fill(cv, x + (i % 3) * sc, y + Math.floor(i / 3) * sc, sc, sc, c); x += 4 * sc; } }
function blit(cv, src, sw, sh, x0, y0) {
  for (let y = 0; y < sh; y++) for (let x = 0; x < sw; x++) {
    const i = (y * sw + x) * 4, a = src[i + 3] / 255; if (!a) continue;
    const X = x0 + x, Y = y0 + y; if (X < 0 || Y < 0 || X >= cv.w || Y >= cv.h) continue;
    const j = (Y * cv.w + X) * 4; for (let q = 0; q < 3; q++) cv.b[j + q] = src[i + q] * a + cv.b[j + q] * (1 - a);
  }
}
function scaled(src, sw, sh, k) {
  const w = Math.round(sw * k), h = Math.round(sh * k), o = Buffer.alloc(w * h * 4);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    let r = 0, g = 0, b = 0, a = 0, n = 0;
    for (let j = Math.floor(y / k); j < Math.min(sh, Math.ceil((y + 1) / k)); j++) for (let i = Math.floor(x / k); i < Math.min(sw, Math.ceil((x + 1) / k)); i++) { const q = (j * sw + i) * 4, al = src[q + 3]; r += src[q] * al; g += src[q + 1] * al; b += src[q + 2] * al; a += al; n++; }
    const q = (y * w + x) * 4; if (a) { o[q] = r / a; o[q + 1] = g / a; o[q + 2] = b / a; o[q + 3] = a / n; }
  }
  return { b: o, w, h };
}
const NAVY = hex("#0e1430"), CELL = hex("#1a2250"), WHITE = hex("#ffffff"), GREY = hex("#9aa6d0"), GOLDC = hex("#ffd447");
const save = (cv, name) => fs.writeFileSync(path.join(out, name), png(cv.b, cv.w, cv.h));
const shadow = (cv, cx, cy, rw) => { for (let y = -8; y <= 8; y++) for (let x = -rw; x <= rw; x++) if ((x * x) / (rw * rw) + (y * y) / 64 <= 1) fill(cv, cx + x, cy + y, 1, 1, [0, 0, 0], 90); };
for (const c of CLASSES) {
  const pad = 10, labelH = 54, cw = FW + pad, ch = FH + pad;
  const cv = canvas(6 * cw + pad, labelH + 2 * ch + pad, NAVY);
  text(cv, CNAME[c] + " ARMOR V3 T0-T5 (FRONT / BACK)", 10, 10, 4, WHITE);
  for (let ti = 0; ti < 6; ti++) {
    const x = pad + ti * cw; text(cv, "T" + ti, x + 4, 34, 4, GOLDC); text(cv, TIERS[ti], x + 52, 38, 2, GREY);
    ["front", "back"].forEach((v, r) => { const y = labelH + r * ch; fill(cv, x, y, FW, FH, CELL); shadow(cv, x + FW / 2 - 4, y + FH - 38, 52); blit(cv, figs[`${c}_${ti}`][v], FW, FH, x, y); });
  }
  save(cv, `sheet_${c}.png`);
}
{
  const k = 0.8, pad = 8, labelW = 120, head = 52, w0 = Math.round(FW * k), h0 = Math.round(FH * k);
  const cv = canvas(labelW + 6 * (w0 + pad) + pad, head + 3 * (h0 + pad) + pad, NAVY);
  text(cv, "WORN ARMOR V3 - WARRIOR / ARCHER / MAGE - T0-T5", 10, 8, 3, WHITE);
  CLASSES.forEach((c, r) => {
    const y = head + r * (h0 + pad); text(cv, CNAME[c], 10, y + h0 / 2 - 6, 3, GOLDC);
    for (let ti = 0; ti < 6; ti++) { const x = labelW + ti * (w0 + pad); fill(cv, x, y, w0, h0, CELL); if (r === 0) text(cv, "T" + ti, x + 4, head - 20, 3, GREY); const s = scaled(figs[`${c}_${ti}`].front, FW, FH, k); shadow(cv, x + w0 / 2 - 4, y + h0 - 30, 42); blit(cv, s.b, s.w, s.h, x, y); }
  });
  save(cv, "sheet_all.png");
}
{
  const pad = 8, cw = 170, chh = 230, labelW = 100, head = 46;
  const cv = canvas(labelW + 6 * (cw + pad) + pad, head + 3 * (chh + pad) + pad, NAVY);
  text(cv, "HATS V3 - T0-T5 (FRONT 3/4 CLOSE-UP)", 10, 8, 3, WHITE);
  CLASSES.forEach((c, r) => {
    const y = head + r * (chh + pad); text(cv, CNAME[c], 6, y + chh / 2 - 6, 2, GOLDC);
    for (let ti = 0; ti < 6; ti++) { const x = labelW + ti * (cw + pad); fill(cv, x, y, cw, chh, CELL); if (r === 0) text(cv, "T" + ti, x + 4, head - 20, 3, GREY); blit(cv, hatImg[`${c}_${ti}`], cw, chh, x, y); }
  });
  save(cv, "sheet_hats.png");
}
{
  const SC = 19.5, W = 540, H = 760, CYC = 17.5;
  const items = figureItems({ l1: layers.warrior_2[0], l2: layers.warrior_2[1], gear: gearM.warrior_2, hat: hatM.warrior_2 });
  const a = renderFigure(items, -32, W, H, SC, CYC), b = renderFigure(items, 148, W, H, SC, CYC);
  let ref = null; const rp = process.env.REF_PNG; if (rp && fs.existsSync(rp)) ref = decodePng(fs.readFileSync(rp));
  const rw = ref ? ref.w : 0, rh = ref ? ref.h : 0, pad = 10, head = 46;
  const cv = canvas(pad + (ref ? rw + pad : 0) + 2 * (W + pad), head + Math.max(H, rh) + pad, NAVY);
  text(cv, "WARRIOR T2 STEEL - REFERENCE / FRONT / BACK", 10, 8, 3, WHITE);
  let x = pad; if (ref) { fill(cv, x, head, rw, rh, [255, 255, 255]); blit(cv, ref.b.map((v, i) => (i % 4 === 3 && v < 250 ? 0 : v)), rw, rh, x, head); x += rw + pad; }
  for (const im of [a, b]) { fill(cv, x, head, W, H, CELL); shadow(cv, x + W / 2 - 8, head + H - 20, 130); blit(cv, im, W, H, x, head); x += W + pad; }
  save(cv, "sheet_warrior_compare.png");
}
fs.writeFileSync(path.join(mdir, "validation.txt"), (allProbs.length ? allProbs.join("\n") : "OK: all hat + warrior piece models pass (coords -16..32, single-axis rotation in {-45,-22.5,22.5,45}, uv 0..16)") + "\n\n" + stat.join("\n") + "\n");
console.log(stat.join("\n")); console.log(allProbs.length ? "PROBLEMS:\n" + allProbs.join("\n") : "validation OK"); console.log("done ->", out);
