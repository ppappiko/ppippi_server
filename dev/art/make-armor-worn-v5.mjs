// Worn armour PREVIEW v5 (flat cartoon style): node dev/art/make-armor-worn-v5.mjs
// Partial, stylish armour (3D hats + extra 3D parts + transparent 64x32 layers) on a mannequin with a base outfit.
// Outputs to dev/art/preview/armor_worn_v2/ (+ models/ with Minecraft-style hat JSON + textures). PREVIEW ONLY.
import fs from "node:fs";
import path from "node:path";
import zlib from "node:zlib";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const out = path.resolve(here, "preview/armor_worn_v5");
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

// ---------- palettes (v5 flat cartoon: ramp = [highlight, base, shadow, outline]) ----------
const BASEC = [["#c98a52", "#a8683a", "#7a4a28", "#4a2812"], ["#c4cad4", "#9aa1ac", "#727a88", "#363b46"], ["#5a78dc", "#3552b8", "#263d90", "#141c54"],
  ["#9adcff", "#4fb0f0", "#2f85cc", "#174a82"], ["#ff8458", "#e8502e", "#b43218", "#66160a"], ["#a870f4", "#7a35d4", "#5522a8", "#2a1066"]].map(r => ramp(...r));
BASEC.forEach(r => { r[3] = mixc(r[2], r[3], .62); });
const BODY = { warrior: BASEC, archer: BASEC, mage: BASEC };
const TRIM = [["#f6b070", "#e08a48", "#a85a26", "#5a2e10"], ["#ffffff", "#dfe3ea", "#a0a8b6", "#4c5260"], ["#fff3a8", "#ffd447", "#d49a14", "#7a5208"],
  ["#ffffff", "#f4fbff", "#b8d6ea", "#5a86a8"], ["#fff3a8", "#ffd447", "#d49a14", "#7a5208"], ["#fff3a8", "#ffd447", "#d49a14", "#7a5208"]].map(r => ramp(...r));
const GEM = [null, null, null, ramp("#e8ffff", "#7ef0ff", "#25b8e8", "#1070b0"), ramp("#fff6b0", "#ffb52e", "#e07a10", "#8a4208"), ramp("#ffffff", "#d8fff4", "#7affd8", "#35c0a8")];
function mixc(a, b, k) { return a.map((v, i) => Math.round(v + (b[i] - v) * k)); }
const _unused = (a, b, k) => a.map((v, i) => Math.round(v + (b[i] - v) * k));
const BANDA = BASEC.map(r => [mixc(r[2], r[3], .2), mixc(r[2], r[3], .45), mixc(r[2], r[3], .75), r[3]]);
const BUCK = TRIM;
const LEATH = BASEC[0];
const FEATHER = [hex("#d8c8a8"), hex("#e6eaee"), hex("#f4f4f4"), hex("#9ef4ff"), hex("#ff8a4a"), hex("#ffffff")];
const DARK = hex("#1f1828"), DARK2 = hex("#3d3345");
const TIERS = ["Starter", "Iron", "Steel", "Blue Diamond", "Amber Redwood", "Legendary"];
const CLASSES = ["warrior", "archer", "mage"];
const CNAME = { warrior: "WARRIOR", archer: "ARCHER", mage: "MAGE" };

let CLS = "warrior", TIER = 0, R = {};
const T = t => TIER >= t;
function setCtx(c, t) { CLS = c; TIER = t; R = { B: BODY[c][t], T: TRIM[t], G: GEM[t] || TRIM[t], A: BANDA[t], K: BUCK[t], L: LEATH, M: BANDA[t] }; }
const hash = (x, y, s = 0) => { let h = (x * 374761393 + y * 668265263 + s * 2246822519) >>> 0; h = ((h ^ (h >>> 13)) * 1274126177) >>> 0; return ((h ^ (h >>> 16)) >>> 0) / 4294967295; };
const lighten = (c, n) => c.map(v => Math.min(255, v + n));
function tokRGBA(tok, x, y) {
  let glow = false; if (tok.endsWith("*")) { glow = true; tok = tok.slice(0, -1); }
  let c;
  if (tok === "BE") c = TIER >= 5 ? [255, 255, 255] : [250, 248, 255]; else if (tok === "BH") c = [226, 220, 246]; else if (tok === "BL") c = [190, 184, 224]; else if (tok === "BS") c = [104, 98, 150];
  else if (tok === "D") c = DARK; else if (tok === "D2") c = DARK2; else if (tok === "W") c = [255, 255, 255]; else if (tok === "H") c = lighten(R.B[0], 26);
  else if (tok === "F") c = FEATHER[TIER]; else if (tok === "F2") c = lighten(FEATHER[TIER], 22); else if (tok === "F3") c = FEATHER[TIER].map(v => v * .72); else if (tok === "FS") c = lighten(FEATHER[TIER], 60);
  else { const L = tok[0], n = tok.length > 1 ? +tok[1] : 1; c = R[L][n]; if (TIER === 5 && L === "G") glow = true; }
  return [c[0], c[1], c[2], glow ? 254 : 255];
}
// flat cartoon face: dark outline ring, optional trim ring/line, base fill, one shadow edge, tiny highlight
function flatTok(f, m, x, y, w, h, o = {}) {
  const tk = o.tk || "T1";
  if (f === "down") return m + "3";
  const e = Math.min(x, y, w - 1 - x, h - 1 - y);
  if (w < 3 || h < 3) return (h < 3 && y === h - 1 && h > 1) ? m + "3" : m + "2";
  if (e === 0) return o.edge ? o.edge : m + "3";
  const big = w >= 4 && h >= 3;
  if (o.ring && e === 1 && big) return tk;
  if (o.rim && f !== "up" && y === h - 2 && big) return tk;
  if (o.top && f !== "up" && y === 1 && big) return tk;
  const d = o.ring ? 2 : 1;
  if (f === "up") return (x === 1 && y === 1) || (x === 2 && y === 1) ? m + "0" : (x === w - 2 || y === h - 2) ? m + "2" : m + "1";
  if (x === w - 1 - d || y === h - 1 - d) return m + "2";
  if ((x === d && y === d) || (x === d + 1 && y === d && w > 5) || (x === d && y === d + 1 && h > 5)) return m + "0";
  return m + "1";
}
const FL = (o = {}) => (f, x, y, w, h) => flatTok(f, o.m || "B", x, y, w, h, o);
const SM = FL; // alias kept from v4 naming
const plateTok = (m, x, y, w, h, f = "south", o = {}) => flatTok(f, m, x, y, w, h, o);


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
      // flat plate: outline ring + optional trim ring/rim line, base, shadow edge, highlight
      pl: (x, y, w, h, o = {}) => { for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) put(x + i, y + j, flatTok("south", o.m || "B", i, j, w, h, o)); return f; },
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

// ---------- layer designs (flat cartoon; partial armour: transparent where the base outfit should show) ----------
const gem2 = (f, x, y) => { f.p(x, y, "G0").p(x + 1, y, "G1").p(x, y + 1, "G1").p(x + 1, y + 1, "G2"); };
const layerDesign = {
  warrior: {
    l1(t) {
      const fr = t.F("body", "front");
      fr.pl(0, 0, 8, 9, { ring: true }); fr.r(2, 2, 4, 4, "B3"); gem2(fr, 3, 3);
      fr.r(0, 9, 8, 1, "A1"); fr.r(3, 9, 2, 1, "K1");
      fr.r(0, 10, 8, 1, "T1"); fr.r(0, 11, 8, 1, "B3"); fr.pts([[0, 10], [7, 10]], "B3");
      t.F("body", "top").pl(0, 0, 8, 4); t.F("body", "bottom").all("D");
      const bk = t.F("body", "back");
      bk.pl(0, 0, 8, 9, { ring: true }); bk.r(3, 3, 2, 2, "T1"); bk.pts([[3, 3], [4, 3]], "T0");
      bk.r(0, 9, 8, 1, "A1"); bk.r(0, 10, 8, 1, "T1"); bk.r(0, 11, 8, 1, "B3"); bk.pts([[0, 10], [7, 10]], "B3");
      for (const s of ["right", "left"]) { const f = t.F("body", s); f.pl(0, 0, 4, 9); f.r(0, 9, 4, 1, "A1"); f.r(0, 10, 4, 1, "T1"); f.r(0, 11, 4, 1, "B3"); f.pts([[0, 10], [3, 10]], "B3"); }
      for (const s of SIDES) { const f = t.F("arm", s); f.pl(0, 0, 4, 8); f.pl(0, 8, 4, 4); }
      t.F("arm", "top").pl(0, 0, 4, 4); t.F("arm", "bottom").all("B3");
      for (const s of SIDES) { const f = t.F("leg", s); f.pl(0, 0, 4, 6); f.pl(0, 6, 4, 4); f.pl(0, 9, 4, 3, { m: "A" }); }
      t.F("leg", "top").all("B3"); t.F("leg", "bottom").all("D");
    },
    l2() {},
  },
  archer: {
    l1(t) {
      const fr = t.F("body", "front");
      fr.pl(0, 0, 8, 12); fr.r(2, 0, 4, 1, "T1"); fr.pts([[3, 1], [4, 1]], "B3");
      for (let y = 1; y <= 8; y++) { const x = 6 - Math.round((y - 1) * 5 / 7); fr.p(x, y, "T1"); fr.p(x - 1, y, "T2"); fr.p(x + 1, y, "B3"); }
      gem2(fr, 1, 2); fr.r(0, 11, 8, 1, "T1");
      const bk = t.F("body", "back");
      bk.pl(0, 0, 8, 12); bk.r(2, 0, 4, 1, "T1");
      for (let y = 1; y <= 8; y++) { const x = 1 + Math.round((y - 1) * 5 / 7); bk.p(x, y, "T1"); bk.p(x + 1, y, "T2"); bk.p(x - 1, y, "B3"); }
      bk.r(0, 11, 8, 1, "T1");
      for (const s of ["right", "left"]) { const f = t.F("body", s); f.pl(0, 0, 4, 12); f.r(0, 11, 4, 1, "T1"); }
      for (const s of SIDES) { const f = t.F("arm", s); f.pl(0, 0, 4, 5); f.pl(0, 8, 4, 4, { m: "A" }); }
      t.F("arm", "top").pl(0, 0, 4, 4); t.F("arm", "bottom").all("B3");
      for (const s of SIDES) { const f = t.F("leg", s); f.pl(0, 8, 4, 4, { m: "A" }); f.r(0, 8, 4, 1, "T1"); }
      t.F("leg", "bottom").all("D");
    },
    l2(t) {
      for (const s of SIDES) { const w = s === "front" || s === "back" ? 8 : 4; const f = t.F("body", s); f.r(0, 9, w, 1, "A1"); f.r(0, 10, w, 1, "A3"); }
      t.F("body", "front").r(3, 9, 2, 1, "K1").r(3, 10, 2, 1, "K2");
    },
  },
  mage: {
    l1(t) {
      const fr = t.F("body", "front");
      fr.pl(0, 0, 3, 9).pl(5, 0, 3, 9); fr.r(3, 0, 2, 9, "A2"); fr.r(3, 8, 2, 1, "A3"); fr.r(2, 1, 1, 8, "T1"); fr.r(5, 1, 1, 8, "T1");
      fr.r(0, 0, 3, 1, "T1"); fr.r(5, 0, 3, 1, "T1");
      const bk = t.F("body", "back"); bk.pl(0, 0, 8, 9, { ring: true }); bk.r(3, 3, 2, 2, "T1"); bk.pts([[3, 3], [4, 3]], "T0");
      for (const s of ["right", "left"]) { const f = t.F("body", s); f.pl(0, 0, 4, 9); f.r(0, 0, 4, 1, "T1"); }
      for (const s of SIDES) { const f = t.F("arm", s); f.pl(0, 0, 4, 8); f.pl(0, 8, 4, 3, { m: "A" }); }
      t.F("arm", "top").pl(0, 0, 4, 4); t.F("arm", "bottom").all("B3");
      for (const s of SIDES) { const f = t.F("leg", s); f.pl(0, 8, 4, 4, { m: "A" }); }
      t.F("leg", "bottom").all("D");
    },
    l2(t) {
      for (const s of SIDES) { const w = s === "front" || s === "back" ? 8 : 4; const f = t.F("body", s); f.r(0, 9, w, 1, "A1"); f.r(0, 10, w, 1, "A3"); f.r(0, 9, w, 1, "T1"); }
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
      if (!tok) tok = e.edge ? flatTok(ff, e.mat, xx, y, r.w, r.h, e.fo || {}) : e.mat + "1";
      const c = tokRGBA(tok, r.x + x, r.y + y), i = ((r.y + y) * N + r.x + x) * 4; buf[i] = c[0]; buf[i + 1] = c[1]; buf[i + 2] = c[2]; buf[i + 3] = c[3];
    }
  }
  return { els, N, buf };
}
const gemDeco = (f, x, y, w, h) => ((f === "south" || f === "north") ? (x === 0 && y === 0 ? "G0" : x === w - 1 && y === h - 1 ? "G2" : "G1") : "G2");
// wide brim from stacked 1-unit bands: dark side outline, trim ring on the top silhouette
function brimBands(bands, t) {
  return bands.map(([z0, z1, hw, y0], i) => {
    const pv = i > 0 ? bands[i - 1][2] : -1, nx = i < bands.length - 1 ? bands[i + 1][2] : -1;
    return E({ from: [-hw, y0, z0], to: [hw, y0 + 1, z1], edge: false, deco: (f, x, y, w, h) => {
      if (f === "down") return "B3";
      if (f !== "up") return "B3";
      const wx = Math.abs(x + .5 - w / 2);
      if (x === 0 || x === w - 1) return "T1";
      if (y === 0 && wx > pv) return "T1"; if (y === h - 1 && wx > nx) return "T1";
      return (x + y) % 11 === 3 && wx < hw - 2 ? "B0" : "B1";
    } });
  });
}
const coneDeco = (f, x, y, w, h) => (f === "down" ? "B3" : (x === 0 || x === w - 1) ? "B3" : x === 1 ? "B0" : x === w - 2 ? "B2" : "B1");
// ----- hats -----
function hatMage() {
  const els = [], t = TIER;
  const bands = [[-9, -8, 3, 30], [-8, -6, 6, 30], [-6, -4, 8, 30], [-4, 4, 9, 30], [4, 6, 8, 30], [6, 8, 6, 30], [8, 9, 3, 30]];
  els.push(...brimBands(bands, t));
  // buckle band
  els.push(E({ from: [-5.5, 31, -5.5], to: [5.5, 34, 5.5], mat: "A", fo: { m: "A" } }));
  els.push(E({ from: [-2.2, 31, 5.5], to: [2.2, 34, 6.1], mat: "T", deco: (f, x, y, w, h) => { if (f !== "south") return "T1"; if (x === 0 || y === 0 || x === w - 1 || y === h - 1) return "T1"; return x >= 1 && x <= w - 2 && y >= 1 && y <= h - 2 ? (t >= 3 ? "G1" : "A3") : "T2"; } }));
  const cone = [[10, 34, 35.6], [9, 35.6, 37.2], [8, 37.2, 38.8], [7, 38.8, 40.4], [6, 40.4, 42]];
  cone.forEach(([w, y0, y1]) => els.push(E({ from: [-w / 2, y0, -w / 2], to: [w / 2, y1, w / 2], edge: false, deco: (f, x, y, fw, fh) => (f === "up" ? "B1" : coneDeco(f, x, y, fw, fh)) })));
  // bent tip: 22.5 then 45 then 45 (backward lean)
  const segs = [[5, 2.4, -22.5], [4, 2.4, -45], [3, 2.4, -45]];
  let b = [0, 42, 0];
  segs.forEach(([w, L, a]) => {
    els.push(E({ from: [-w / 2, b[1] - .2, b[2] - w / 2], to: [w / 2, b[1] + L, b[2] + w / 2], rot: rotX([...b], a), edge: false, deco: (f, x, y, fw, fh) => (f === "down" ? "B3" : f === "up" ? "B1" : (x === 0 || x === fw - 1) ? "B3" : "B1") }));
    const r = a * Math.PI / 180; b = [0, b[1] + L * Math.cos(r), b[2] + L * Math.sin(r)];
  });
  if (t >= 5) { const c = [0, b[1] + 1.6, b[2] - .6]; els.push(E({ from: [-1.5, c[1] - .5, c[2] - .5], to: [1.5, c[1] + .5, c[2] + .5], mat: "G", edge: false, deco: () => "G1*" }), E({ from: [-.5, c[1] - 1.5, c[2] - .5], to: [.5, c[1] + 1.5, c[2] + .5], mat: "G", edge: false, deco: () => "G1*" })); }
  else if (t >= 2) els.push(E({ from: [-.7, b[1] + .2, b[2] - .7], to: [.7, b[1] + 1.6, b[2] + .7], mat: "G", edge: false, deco: gemDeco }));
  return els;
}
function hatArcher() {
  const els = [], t = TIER;
  const bands = [[-9, -8, 3, 31], [-8, -6, 6, 31], [-6, -4, 8, 30], [-4, 4, 8, 30], [4, 6, 8, 30], [6, 8, 6, 31], [8, 9, 3, 31]];
  els.push(...brimBands(bands, t));
  els.push(E({ from: [-4.5, 31, -4.5], to: [4.5, 34.2, 4.5], fo: {}, deco: (f, x, y, w, h) => (f === "up" ? null : null) }));
  els.push(E({ from: [-3.5, 34.2, -3.5], to: [3.5, 35.2, 3.5], edge: false, deco: (f, x, y, w, h) => (f === "up" ? "B1" : f === "down" ? "B3" : (x === 0 || x === w - 1 || y === h - 1) ? "B3" : "B1") }));
  els.push(E({ from: [-5, 31, -5], to: [5, 33.2, 5], mat: "A", fo: { m: "A" } }));
  els.push(E({ from: [-1.7, 31, 5], to: [1.7, 33.2, 5.6], mat: "T", deco: (f, x, y, w, h) => { if (f !== "south") return "T1"; if (x === 0 || y === 0 || x === w - 1 || y === h - 1) return "T1"; return t >= 3 ? "G1" : "A3"; } }));
  // long feather at right side of the band angled backward (3 segments)
  const fe = [[5.5, 4.8, -22.5], [4.6, 4.8, -45], [3.4, 3.2, -45]];
  let b = [5, 32, -1];
  fe.forEach(([w, L, a], i) => {
    els.push(E({ from: [b[0] - .5, b[1] - .3, b[2] - w / 2], to: [b[0] + .5, b[1] + L, b[2] + w / 2], rot: rotX([...b], a), edge: false,
      deco: (f, x, y, fw, fh) => {
        if (f === "up" || f === "down") return i === 2 ? "F3" : "F2";
        if (f === "north" || f === "south") return "F";
        const cx = Math.floor(fw / 2);
        if (x === cx) return t >= 5 ? "FS*" : "FS";
        if (x === 0 || x === fw - 1 || y === fh - 1) return "F3";
        return x < cx ? "F2" : "F";
      } }));
    const r = a * Math.PI / 180; b = [b[0], b[1] + L * Math.cos(r), b[2] + L * Math.sin(r)];
  });
  return els;
}
function hatWarrior() {
  const els = [], t = TIER;
  // reference-style helm (open face): gold-banded brow, split dome (coloured front / dark rear), gold centre ridge,
  // round gold-rimmed ear discs with gem, dark neck flaps, swept-back plume. Face (z+, y24..29) stays clear. No horns.
  const band = (f, x, y, w, h) => (f === "up" ? "B1" : f === "down" ? "B3" : (y === 0 || y === h - 1) ? "T1" : (f === "south" && x === 0) || x === w - 1 ? "B3" : "B1");
  els.push(E({ from: [-4.8, 29.2, -4.8], to: [4.8, 31.5, 4.8], edge: false, deco: band }));
  els.push(E({ from: [-1.6, 29.5, 4.8], to: [1.6, 31.2, 5.2], mat: "T", fo: { m: "T" } }));
  els.push(E({ from: [-.8, 29.9, 5.2], to: [.8, 30.8, 5.6], mat: "G", edge: false, deco: gemDeco }));
  // lower, rounder dome: 3 stepped layers (front coloured / rear dark)
  [[31.5, 33.0, 4.6], [33.0, 34.2, 3.8], [34.2, 35.0, 2.6]].forEach(([y0, y1, r]) => {
    els.push(E({ from: [-r, y0, 0], to: [r, y1, r] }));
    els.push(E({ from: [-r, y0, -r], to: [r, y1, 0], mat: "A", fo: { m: "A" } }));
  });
  // tier-coloured blade socket on the crown
  els.push(E({ from: [-1.1, 34.4, -4.2], to: [1.1, 35.6, 2.6], mat: "T", edge: false, deco: (f, x, y, w, h) => (f === "up" ? "T1" : f === "down" ? "T3" : (y === h - 1 || x === 0 || x === w - 1) ? "T2" : "T0") }));
  // ear discs: gold rim + coloured face + gem
  both(E({ from: [4.6, 26.6, -2.8], to: [5.4, 30.6, 1.2], mat: "T", fo: { m: "T" } }), els);
  both(E({ from: [5.4, 27.4, -2.0], to: [5.8, 29.8, 0.4], mat: "B", edge: false, deco: (f, x, y, w, h) => (f === "east" || f === "west") ? ((x >= w / 2 - 1 && x <= w / 2 && y >= (h >> 1) - 1 && y <= h >> 1) ? ((x === (w >> 1) - 1 || w % 2) && y === (h >> 1) - 1 ? "G0" : "G1") : "B1") : "T1" }), els);
  // cheek tabs beside the face (dark)
  both(E({ from: [4.1, 24.4, -3.2], to: [4.9, 26.6, 1.0], mat: "A", fo: { m: "A" } }), els);
  // back neck guard + two dark feet-like tabs
  els.push(E({ from: [-4.6, 26.6, -5.0], to: [4.6, 29.2, -4.0], mat: "A", fo: { m: "A" } }));
  both(E({ from: [1.2, 24.0, -5.0], to: [3.8, 26.6, -4.0], mat: "A", fo: { m: "A" } }), els);
  // swept BLADE crest: low 1px fin on the centre line hugging the dome, sweeping back/down into a long trailing tip; saw-tooth lower edge
  const bl = (f, x, y, w, h) => {
    const g = t >= 5 ? "*" : "";
    if (f === "up" || f === "north" || f === "south") return "BE" + g;
    if (f === "down") return "BS";
    if (x === 0) return "BS";               // inner/dome side (dark spine)
    if (x === w - 1 || y === 0) return "BE" + g; // outer cutting edge
    return y >= h - 1 ? "BH" : "BL";
  };
  const tooth = (f, x, y, w, h) => (f === "east" || f === "west") ? "BL" : "BE";
  // one continuous profile: over the dome (overlapping stair boxes), down the back of the helm, flaring into the tail
  [[-2.4, 0.6, 35.4, 38.0], [-4.0, -2.2, 34.6, 37.2], [-5.2, -3.6, 32.8, 36.0], [-6.0, -4.6, 30.2, 34.2], [-6.4, -5.0, 27.4, 31.0]].forEach(([z0, z1, y0, y1], i) =>
    els.push(E({ from: [-.5, y0, z0], to: [.5, y1, z1], edge: false, deco: (f, x, y, w, h) => (f === "east" || f === "west") ? ((y === 0 || x === w - 1) ? "BE" + (t >= 5 ? "*" : "") : y >= h - 1 ? "BH" : "BL") : bl(f, x, y, w, h) })));
  // filled wedge body in front of the peak (stacked stairs, overlapping the tip boxes)
  [[35.4, 36.2, 3.8], [36.2, 37.0, 3.2], [37.0, 38.0, 2.4]].forEach(([y0, y1, z1]) =>
    els.push(E({ from: [-.5, y0, 0.4], to: [.5, y1, z1], edge: false, deco: (f, x, y, w, h) => (f === "east" || f === "west") ? ((y === 0 || x === w - 1) ? "BE" + (t >= 5 ? "*" : "") : y >= h - 1 ? "BS" : "BL") : bl(f, x, y, w, h) })));
  // sharp prow: 45deg thin blade tapering to a 1px point leaning forward over the brow
  { let fz = -0.4, fy = 34.8;
    [[2.6, 1.8], [1.8, 1.6], [1.0, 1.4], [.5, 1.2]].forEach(([w, L]) => {
      els.push(E({ from: [-.5, fy - .3, fz - w / 2], to: [.5, fy + L, fz + w / 2], rot: rotX([0, fy, fz], 45), edge: false, deco: (f, x, y, w, h) => (f === "east" || f === "west") ? ((y === 0 || x === w - 1) ? "BE" + (t >= 5 ? "*" : "") : y >= h - 1 ? "BH" : "BL") : bl(f, x, y, w, h) }));
      fy += L * Math.SQRT1_2; fz += L * Math.SQRT1_2;
    }); }
  // saw notches: small teeth on the lower/inner edge of the tail part only
  [[-6.9, -6.2, 30.6, 31.6], [-7.0, -6.4, 28.0, 29.0]].forEach(([z0, z1, y0, y1]) => els.push(E({ from: [-.5, y0, z0], to: [.5, y1, z1], edge: false, deco: tooth })));
  let bz = -5.7, by = 27.8;
  [[1.4, 2.2, 22.5], [1.2, 2.0, 22.5], [1.0, 1.8, 45], [.7, 1.6, 45]].forEach(([w, L, a], i) => {
    const r = a * Math.PI / 180;
    els.push(E({ from: [-.5, by - L, bz - w / 2], to: [.5, by + .3, bz + w / 2], rot: rotX([0, by, bz], a), edge: false, deco: bl }));
    if (i < 3) els.push(E({ from: [-.5, by - L * .8, bz - w / 2 - .6], to: [.5, by - L * .8 + 1.0, bz - w / 2 + .1], rot: rotX([0, by, bz], a), edge: false, deco: tooth }));
    by -= L * Math.cos(r); bz -= L * Math.sin(r);
  });
  return els;
}

// ----- cape (T3+): 3D, from the shoulders down the back to the knees, slight flare; inside = darker base -----
const capeDeco = (i, n) => (f, x, y, w, h) => {
  if (f === "down") return "B3";
  if (f === "up") return i === 0 ? "B2" : "B3";
  if (f === "south") return (x === 0 || x === w - 1 || y === h - 1 || (i === 0 && y === 0)) ? "B3" : (x === 2 || x === w - 3) ? "B3" : "B2";
  if (f === "east" || f === "west") return "B3";
  if (x === 0 || x === w - 1 || y === h - 1 || (i === 0 && y === 0)) return "B3";
  if (x === 1 || x === w - 2 || (i === n - 1 && y === h - 2)) return "T1";
  if (i === 0 && y === 1 && x >= 3 && x <= w - 4) return "T1";
  if (i === 0 && y >= 2 && y <= 3 && (x === (w >> 1) - 1 || x === w >> 1) && w % 2 === 0) return y === 2 ? (x === (w >> 1) - 1 ? "G0" : "G1") : (x === (w >> 1) - 1 ? "G1" : "G2");
  if (x === 3 || x === w - 4) return "B2";
  return "B1";
};
function cape() {
  const segs = [
    { from: [-5.0, 17.0, -4.6], to: [5.0, 24.6, -3.8] },
    { from: [-5.8, 11.4, -4.6], to: [5.8, 17.0, -3.8] },
    { from: [-6.5, 6.8, -4.6], to: [6.5, 11.4, -3.8], rot: rotX([0, 11.4, -4.2], 22.5) },
  ];
  return segs.map((s, i) => E({ ...s, mat: "B", grp: "cape", deco: capeDeco(i, 3) }));
}

// ----- body gear (bone-attached) -----
function pauldrons() {
  const els = [];
  const O = [-5.0, 26.0, 0], R = () => rotZ(O, 22.5), arr = [];
  arr.push(E({ from: [-11.2, 22.4, -4.0], to: [-5.0, 26.0, 4.0], rot: R(), bone: "rarm", grp: "pauldrons", deco: (f, x, y, w, h) => (f === "west" && y >= 1 && y <= 2 && x >= (w >> 1) - 1 && x <= w >> 1 ? (y === 1 ? (x === (w >> 1) - 1 ? "G0" : "G1") : (x === (w >> 1) - 1 ? "G1" : "G2")) : flatTok(f, "B", x, y, w, h, { ring: true })) }));
  arr.push(E({ from: [-9.4, 26.0, -3.2], to: [-5.4, 28.4, 3.2], rot: R(), bone: "rarm", grp: "pauldrons" }));
  arr.push(E({ from: [-11.6, 19.6, -3.6], to: [-5.4, 22.4, 3.6], rot: R(), bone: "rarm", grp: "pauldrons", fo: { rim: true } }));
  arr.forEach(e => both(e, els));
  return els;
}
function gearWarrior() {
  const els = [], t = TIER;
  els.push(...pauldrons());
  both(E({ from: [-9.6, 16.2, -2.8], to: [-8.0, 20.0, 2.8], bone: "rarm", grp: "couters" }), els);
  both(E({ from: [-8.8, 12.2, -3.2], to: [-3.4, 15.2, 3.2], bone: "rarm", grp: "cuffs" }), els);
  // fauld: 2 broad lames wrapping the hips
  for (let i = 0; i < 2; i++) {
    const y1 = 12.4 - 2.8 * i, y0 = y1 - 3, zo = .8 - .2 * i, xo = 5.1 - .2 * i, o = i === 0 ? { rim: true } : {};
    els.push(E({ from: [-xo, y0, 2.9], to: [xo, y1, 2.9 + zo], grp: "fauld", fo: o }));
    els.push(E({ from: [-xo, y0, -2.9 - zo], to: [xo, y1, -2.9], grp: "fauld", fo: o }));
    both(E({ from: [-xo - zo + .1, y0, -2.9], to: [-xo + .2, y1, 2.9], grp: "fauld", fo: o }), els);
  }
  both(E({ from: [-4.2, 5.8, 3.4], to: [-0.3, 8.8, 4.2], bone: "rleg", grp: "greaves" }), els);
  both(E({ from: [-4.1, 3.0, 2.6], to: [-0.4, 5.6, 3.5], bone: "rleg", grp: "greaves" }), els);
  both(E({ from: [-4.5, 0, 2.0], to: [.5, 2.4, 5.0], bone: "rleg", grp: "greaves", mat: "A", fo: { m: "A" } }), els);
  if (t >= 3) els.push(...cape());
  return els;
}
function gearArcher() {
  const els = [], t = TIER;
  const Q = rotZ([0, 19, -4.4], 22.5);
  els.push(E({ from: [-1.9, 12.5, -5.8], to: [1.9, 25.5, -3.2], rot: Q, mat: "A", deco: (f, x, y, w, h) => (f === "up" ? "D" : f === "down" ? "A3" : (y === 1 || y === h - 3) && x > 0 && x < w - 1 ? "T1" : flatTok(f, "A", x, y, w, h)) }));
  [-.9, 0, .9].forEach((dx, i) => {
    els.push(E({ from: [dx - .25, 25.3, -4.65], to: [dx + .25, 28.2 + (i === 1 ? .8 : 0), -4.15], rot: Q, mat: "T", edge: false, deco: () => "T0" }));
    els.push(E({ from: [dx - .6, 27.2 + (i === 1 ? .8 : 0), -4.7], to: [dx + .6, 29.4 + (i === 1 ? .8 : 0), -4.1], rot: Q, mat: "F", edge: false, deco: (f, x, y) => (f === "north" || f === "south" ? (y % 2 === 0 ? "F2" : "F") : "F3") }));
  });
  // belt pouch
  els.push(E({ from: [4.6, 10.8, -.4], to: [6.6, 13.8, 2.6], mat: "B", fo: {} }));
  els.push(E({ from: [4.5, 12.8, -.5], to: [6.7, 14.6, 2.7], mat: "A", deco: (f, x, y, w, h) => (f === "south" && x === 1 && y === 1 ? "K1" : flatTok(f, "A", x, y, w, h)) }));
  // left bracer
  els.push(E({ from: [3.5, 12.2, -2.6], to: [8.5, 17.6, 2.6], bone: "larm", mat: "A", fo: { m: "A", ring: true } }));
  // right shoulder pad
  els.push(E({ from: [-9.4, 21.4, -3.0], to: [-3.7, 24.4, 3.0], rot: rotZ([-3.7, 24.4, 0], 22.5), bone: "rarm", mat: "B", fo: { rim: true } }));
  both(E({ from: [-5.4, 4.8, -3.5], to: [1.4, 6.9, 3.5], bone: "rleg", mat: "A", deco: (f, x, y, w, h) => (f === "up" ? "A3" : y === 0 ? "T1" : flatTok(f, "A", x, y, w, h)) }), els);
  if (t >= 3) els.push(...cape());
  return els;
}
function gearMage() {
  const els = [], t = TIER;
  const hem = (m = "B") => (f, x, y, w, h) => (f !== "up" && f !== "down" && y === h - 1 ? "T1" : flatTok(f, m, x, y, w, h));
  const topT = (m = "B") => (f, x, y, w, h) => (f !== "up" && f !== "down" && y === 0 ? "T1" : flatTok(f, m, x, y, w, h));
  // high collar (stands up behind the neck, flares forward) + open-coat lapels
  els.push(E({ from: [-4.8, 25, -5.5], to: [4.8, 29.5, -4.5], rot: rotX([0, 25, -5], -22.5), mat: "B", deco: topT() }));
  els.push(E({ from: [-4.6, 23, -4.3], to: [4.6, 25.2, -3], mat: "B" }));
  both(E({ from: [4.6, 24.4, -4.4], to: [5.4, 28.2, 1.4], rot: rotZ([4.6, 24.4, 0], -22.5), mat: "B", deco: topT() }), els);
  both(E({ from: [1.1, 18.5, 2.6], to: [3.9, 24.2, 3.4], mat: "B", deco: (f, x, y, w, h) => (f === "south" && x === 0 ? "T1" : flatTok(f, "B", x, y, w, h)) }), els);
  // closing clasp gem on the chest
  els.push(E({ from: [-1.5, 21.2, 2.6], to: [1.5, 23.8, 3.1], mat: "A", fo: { m: "A" } }));
  els.push(E({ from: [-.8, 21.7, 3.1], to: [.8, 23.3, 3.5], mat: "G", edge: false, deco: gemDeco }));
  // shoulder mantles
  both(E({ from: [-10, 23.2, -3.8], to: [-4, 26, 3.8], rot: rotZ([-4, 25, 0], 22.5), bone: "rarm", mat: "B", fo: { top: true } }), els);
  both(E({ from: [-11, 20.6, -4.1], to: [-4.6, 23.6, 4.1], rot: rotZ([-4.6, 23.6, 0], 22.5), bone: "rarm", mat: "A", deco: hem("A") }), els);
  // sleeve cuffs
  both(E({ from: [-9.6, 11.6, -3.6], to: [-2.4, 14.6, 3.6], bone: "rarm", mat: "A", deco: topT("A") }), els);
  // robe tails: back + open front panels
  els.push(E({ from: [-4.4, 8, -3.7], to: [4.4, 14, -2.9], fo: {} }));
  els.push(E({ from: [-4.4, 2.8, -3.7], to: [4.4, 8, -2.9], rot: rotX([0, 8, -3.3], 22.5), deco: hem() }));
  [E({ from: [1.8, 8, 2.6], to: [4.7, 14, 3.4], deco: (f, x, y, w, h) => (f === "south" && x === 0 ? "T1" : flatTok(f, "B", x, y, w, h)) }), E({ from: [1.8, 3.2, 2.6], to: [4.7, 8, 3.4], rot: rotX([3.2, 8, 3], -22.5), deco: (f, x, y, w, h) => (f !== "up" && f !== "down" && (y === h - 1 || (f === "south" && x === 0)) ? "T1" : flatTok(f, "B", x, y, w, h)) })].forEach(e => both(e, els));
  // sash knot + hanging ends
  els.push(E({ from: [-1.4, 12.6, 2.6], to: [1.4, 14.6, 3.6], mat: "A", fo: { m: "A" } }));
  els.push(E({ from: [-1.4, 7, 2.9], to: [-.2, 12.6, 3.4], mat: "A", deco: hem("A") }));
  els.push(E({ from: [.2, 6, 2.9], to: [1.4, 12.6, 3.4], mat: "A", deco: hem("A") }));
  // wide boot cuffs
  both(E({ from: [-5.2, 3.8, -3.5], to: [1.2, 5.9, 3.5], bone: "rleg", mat: "A", deco: (f, x, y, w, h) => (f === "up" ? "A3" : y === 0 ? "T1" : flatTok(f, "A", x, y, w, h)) }), els);
  if (t >= 3) els.push(...cape());
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
      const lit = .8 + .2 * Math.max(0, vdot(n, L)), shade = Math.max(it.minSh || 0, n[2] > 0 ? lit : lit * .9);
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
  return model.els.map(e => ({ min: e.from, max: e.to, rot: e.rot, pose: e.bone ? POSE[e.bone] : null, faces: e._f, tex: { b: model.buf, w: model.N, h: model.N }, minSh: .86 }));
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
  const json = { credit: "pipi preview - worn armour v5", texture_size: [N, N], textures: { "0": `pipi:item/${name}`, particle: `pipi:item/${name}` },
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
  { const gs = [...new Set(gearEls.map(e => e.grp || "gear"))]; for (const g of gs) {
    const sub = gearEls.filter(e => (e.grp || "gear") === g), gm = buildModel(sub);
    const gx = exportHat(c, ti, gm, `${g}_${c}_t${ti}`, 0, "mannequin-space + (8,0,8): player feet-centre = x8,z8,y0; front = south/+z; bone tag in element name says which limb the piece follows (rarm/larm/rleg/lleg, none = body); rotation single-axis about origin"); allProbs.push(...gx.probs); stat.push(`  ${g}: ${gx.n} elements tex ${gx.N}x${gx.N}`);
  } }
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
const NAVY = hex("#2a2f45"), CELL = hex("#d6d9e8"), WHITE = hex("#ffffff"), GREY = hex("#c4c9e0"), GOLDC = hex("#ffd447");
const save = (cv, name) => fs.writeFileSync(path.join(out, name), png(cv.b, cv.w, cv.h));
const shadow = (cv, cx, cy, rw) => { for (let y = -8; y <= 8; y++) for (let x = -rw; x <= rw; x++) if ((x * x) / (rw * rw) + (y * y) / 64 <= 1) fill(cv, cx + x, cy + y, 1, 1, [0, 0, 0], 90); };
for (const c of CLASSES) {
  const pad = 10, labelH = 54, cw = FW + pad, ch = FH + pad;
  const cv = canvas(6 * cw + pad, labelH + 2 * ch + pad, NAVY);
  text(cv, CNAME[c] + " ARMOR V5 T0-T5 (FRONT / BACK)", 10, 10, 4, WHITE);
  for (let ti = 0; ti < 6; ti++) {
    const x = pad + ti * cw; text(cv, "T" + ti, x + 4, 34, 4, GOLDC); text(cv, TIERS[ti], x + 52, 38, 2, GREY);
    ["front", "back"].forEach((v, r) => { const y = labelH + r * ch; fill(cv, x, y, FW, FH, CELL); shadow(cv, x + FW / 2 - 4, y + FH - 38, 52); blit(cv, figs[`${c}_${ti}`][v], FW, FH, x, y); });
  }
  save(cv, `sheet_${c}.png`);
}
{
  const k = 0.8, pad = 8, labelW = 120, head = 52, w0 = Math.round(FW * k), h0 = Math.round(FH * k);
  const cv = canvas(labelW + 6 * (w0 + pad) + pad, head + 3 * (h0 + pad) + pad, NAVY);
  text(cv, "WORN ARMOR V5 - WARRIOR / ARCHER / MAGE - T0-T5", 10, 8, 3, WHITE);
  CLASSES.forEach((c, r) => {
    const y = head + r * (h0 + pad); text(cv, CNAME[c], 10, y + h0 / 2 - 6, 3, GOLDC);
    for (let ti = 0; ti < 6; ti++) { const x = labelW + ti * (w0 + pad); fill(cv, x, y, w0, h0, CELL); if (r === 0) text(cv, "T" + ti, x + 4, head - 20, 3, GREY); const s = scaled(figs[`${c}_${ti}`].front, FW, FH, k); shadow(cv, x + w0 / 2 - 4, y + h0 - 30, 42); blit(cv, s.b, s.w, s.h, x, y); }
  });
  save(cv, "sheet_all.png");
}
{
  const pad = 8, cw = 170, chh = 230, labelW = 100, head = 46;
  const cv = canvas(labelW + 6 * (cw + pad) + pad, head + 3 * (chh + pad) + pad, NAVY);
  text(cv, "HATS V5 - T0-T5 (FRONT 3/4 CLOSE-UP)", 10, 8, 3, WHITE);
  CLASSES.forEach((c, r) => {
    const y = head + r * (chh + pad); text(cv, CNAME[c], 6, y + chh / 2 - 6, 2, GOLDC);
    for (let ti = 0; ti < 6; ti++) { const x = labelW + ti * (cw + pad); fill(cv, x, y, cw, chh, CELL); if (r === 0) text(cv, "T" + ti, x + 4, head - 20, 3, GREY); blit(cv, hatImg[`${c}_${ti}`], cw, chh, x, y); }
  });
  save(cv, "sheet_hats.png");
}
for (const c of CLASSES) {
  const SC = 10, W = 290, H = 520, CYC = 19.5, pad = 8, head = 40, tiers = [0, 3, 5];
  const cv = canvas(pad + tiers.length * 2 * (W + pad), head + H + pad, NAVY);
  text(cv, CNAME[c] + " V5 ZOOM T0 / T3 / T5 (FRONT + BACK)", 10, 8, 3, WHITE);
  let x = pad;
  for (const ti of tiers) {
    const items = figureItems({ l1: layers[c + "_" + ti][0], l2: layers[c + "_" + ti][1], gear: gearM[c + "_" + ti], hat: hatM[c + "_" + ti] });
    for (const yaw of [-32, 148]) { fill(cv, x, head, W, H, CELL); shadow(cv, x + W / 2 - 6, head + H - 40, 90); blit(cv, renderFigure(items, yaw, W, H, SC, CYC), W, H, x, head); x += W + pad; }
  }
  save(cv, "sheet_zoom_" + c + ".png");
}
{
  const ref = decodePng(fs.readFileSync("C:/Users/user/AppData/Local/Temp/claude/66971b31cf2dbcd7/827626d3-9015-423c-ac73-5d3d52f40683/images/90.png"));
  const K = 10, RW = ref.w * K, RH = ref.h * K, W = 400, H = 440, pad = 8, head = 40;
  const cv = canvas(pad + RW + pad + 2 * (W + pad), head + Math.max(RH, H) + pad, NAVY);
  text(cv, "HELM COMPARE - REFERENCE X10 / T5 FRONT 3/4 / T5 SIDE", 10, 8, 3, WHITE);
  fill(cv, pad, head, RW, RH, CELL);
  const up = Buffer.alloc(RW * RH * 4); for (let y = 0; y < RH; y++) for (let x = 0; x < RW; x++) { const i = ((y / K | 0) * ref.w + (x / K | 0)) * 4, j = (y * RW + x) * 4; up[j] = ref.b[i]; up[j + 1] = ref.b[i + 1]; up[j + 2] = ref.b[i + 2]; up[j + 3] = ref.b[i + 3]; }
  blit(cv, up, RW, RH, pad, head);
  let x = pad + RW + pad;
  for (const yaw of [-32, 90]) { fill(cv, x, head, W, H, CELL); blit(cv, renderFigure(figureItems({ hat: hatM["warrior_5"], skinOnlyHead: true }), yaw, W, H, 16, 31, 12), W, H, x, head); x += W + pad; }
  save(cv, "helm_compare.png");
}
fs.writeFileSync(path.join(mdir, "validation.txt"), (allProbs.length ? allProbs.join("\n") : "OK: all hat + warrior piece models pass (coords -16..32, single-axis rotation in {-45,-22.5,22.5,45}, uv 0..16)") + "\n\n" + stat.join("\n") + "\n");
console.log(stat.join("\n")); console.log(allProbs.length ? "PROBLEMS:\n" + allProbs.join("\n") : "validation OK"); console.log("done ->", out);
