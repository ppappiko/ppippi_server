// Worn armour PREVIEW generator: node dev/art/make-armor-worn.mjs
// Vanilla 64x32 humanoid layers (layer_1 / layer_2) for warrior/archer/mage x T0..T5,
// plus a software 3D renderer (mannequin + inflated armour boxes) and contact sheets.
// Outputs to dev/art/preview/armor_worn/. PREVIEW ONLY - not wired into the game.
import fs from "node:fs";
import path from "node:path";
import zlib from "node:zlib";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const out = path.resolve(here, "preview/armor_worn");
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

// ---------- palettes (same as icon generator) ----------
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
const FEATHER = [hex("#a89a80"), hex("#d8dde2"), hex("#e8e8e8"), hex("#7ef0ff"), hex("#ff7a3a"), hex("#ffffff")];
const DARK = hex("#2b2432"), DARK2 = hex("#3d3345");
const TIERS = ["Starter", "Iron", "Steel", "Blue Diamond", "Amber Redwood", "Legendary"];
const CLASSES = ["warrior", "archer", "mage"];

// ---------- vanilla UV layout ----------
const BOX = {
  head: { u: 0, v: 0, w: 8, h: 8, d: 8 }, hat: { u: 32, v: 0, w: 8, h: 8, d: 8 },
  body: { u: 16, v: 16, w: 8, h: 12, d: 4 }, arm: { u: 40, v: 16, w: 4, h: 12, d: 4 }, leg: { u: 0, v: 16, w: 4, h: 12, d: 4 },
};
function uvRect(b, face) {
  const { u, v, w, h, d } = BOX[b];
  switch (face) {
    case "top": return { x: u + d, y: v, w, h: d };
    case "bottom": return { x: u + d + w, y: v, w, h: d };
    case "right": return { x: u, y: v + d, w: d, h };
    case "front": return { x: u + d, y: v + d, w, h };
    case "left": return { x: u + d + w, y: v + d, w: d, h };
    case "back": return { x: u + 2 * d + w, y: v + d, w, h };
  }
}
const FACES = ["top", "bottom", "right", "front", "left", "back"];
const SIDES = ["right", "front", "left", "back"];

// ---------- texture painter ----------
let tier = 0, cls = "warrior";
const T = t => tier >= t;
const GT = () => (T(3) ? "G" : "T");
const fixTok = k => (k && k.length === 1 && "BTG".includes(k) ? k + "1" : k);
class Tex {
  constructor() { this.t = new Array(64 * 32).fill(null); }
  F(box, face) {
    const r = uvRect(box, face), self = this;
    const put = (x, y, k) => { if (x < 0 || y < 0 || x >= r.w || y >= r.h) return; self.t[(r.y + y) * 64 + r.x + x] = fixTok(k); };
    const f = {
      w: r.w, h: r.h,
      p: (x, y, k) => { put(x, y, k); return f; },
      r: (x, y, w, h, k) => { for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) put(x + i, y + j, k); return f; },
      pts: (list, k) => { list.forEach(([x, y]) => put(x, y, k)); return f; },
      plate: (x, y, w, h, c = "B") => {
        for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) {
          let n = 1;
          if (w >= 2 && h >= 2) { if (j === 0 || i === 0) n = 0; if (j === h - 1 || i === w - 1) n = 2; if (j === h - 1 && i === w - 1 && w > 2 && h > 2) n = 3; if ((j === 0 && i === w - 1) || (i === 0 && j === h - 1)) n = 1; }
          put(x + i, y + j, c + n);
        }
        return f;
      },
      all: k => f.r(0, 0, r.w, r.h, k),
    };
    return f;
  }
  clear(box, face, x, y, w, h) {
    const r = uvRect(box, face);
    for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) this.t[(r.y + y + j) * 64 + r.x + x + i] = null;
  }
  finish() {
    const buf = Buffer.alloc(64 * 32 * 4);
    const body = BODY[cls][tier], trim = TRIM[tier], gem = GEM[tier] || trim;
    const hash = (x, y) => { let h = (x * 374761393 + y * 668265263) >>> 0; h = ((h ^ (h >>> 13)) * 1274126177) >>> 0; return ((h ^ (h >>> 16)) >>> 0) / 4294967295; };
    for (let y = 0; y < 32; y++) for (let x = 0; x < 64; x++) {
      const k = this.t[y * 64 + x]; if (!k) continue;
      let c;
      if (k === "D") c = DARK; else if (k === "D2") c = DARK2;
      else if (k === "F") c = FEATHER[tier];
      else if (k === "F2") c = FEATHER[tier].map(v => Math.min(255, v + 30));
      else {
        let n = +k[1];
        if (k[0] === "B" && n === 1) { const h = hash(x, y), s = cls === "warrior" && tier >= 1 ? .06 : .13; if (h < s) n = 0; else if (h > 1 - s) n = 2; }
        c = (k[0] === "B" ? body : k[0] === "T" ? trim : gem)[n];
      }
      const i = (y * 64 + x) * 4; buf[i] = c[0]; buf[i + 1] = c[1]; buf[i + 2] = c[2]; buf[i + 3] = 255;
    }
    return buf;
  }
}

// ---------- designs ----------
const designs = {
  warrior: {
    helmet(t) {
      for (const fc of FACES) t.F("head", fc).plate(0, 0, 8, 8);
      const fr = t.F("head", "front");
      fr.r(0, 2, 8, 1, "T");
      if (T(1)) { fr.r(1, 3, 6, 2, "D"); fr.plate(1, 5, 6, 3, "B"); fr.pts([[2, 6], [5, 6], [2, 7], [5, 7]], "D"); fr.r(3, 2, 2, 6, "B"); fr.r(4, 3, 1, 5, "T"); }
      else { fr.r(1, 3, 6, 4, "D"); fr.r(1, 3, 6, 1, "D2"); fr.pts([[1, 2], [3, 2], [5, 2]], "B3"); fr.plate(0, 7, 8, 1, "B"); }
      fr.r(0, 3, 1, 5, "B2"); fr.r(7, 3, 1, 5, "B2");
      if (T(2)) { fr.r(3, 0, 2, 2, "T"); fr.pts([[0, 2], [7, 2], [0, 6], [7, 6]], "T0"); }
      if (T(3)) fr.r(3, 1, 2, 1, "G");
      const top = t.F("head", "top");
      if (T(2)) { top.r(3, 0, 2, 8, "T"); top.r(3, 0, 1, 8, "T0"); } else top.r(3, 0, 2, 8, "B2");
      if (T(1)) top.pts([[1, 1], [6, 1], [1, 6], [6, 6]], "T");
      if (T(3)) top.r(3, 3, 2, 2, "G");
      if (!T(1)) top.pts([[1, 2], [1, 5], [6, 2], [6, 5], [2, 1], [5, 1], [2, 6], [5, 6]], "T");
      for (const s of ["right", "left"]) {
        const f = t.F("head", s);
        f.r(0, 2, 8, 1, "T"); f.plate(2, 4, 5, 4, "B"); f.r(3, 4, 2, 2, "D");
        if (T(1)) f.pts([[1, 6], [2, 6]], "T");
        if (T(2)) f.pts([[0, 2], [7, 2], [6, 5]], "T0");
        if (T(3)) { f.pts([[3, 2], [2, 1], [2, 0], [3, 1]], tier >= 4 ? "T" : "B0"); f.pts([[3, 3], [4, 3], [2, 2]], "T"); }
        if (T(4)) f.pts([[1, 1], [1, 0]], "T");
        if (!T(1)) f.pts([[1, 4], [1, 6], [6, 4], [6, 6]], "T");
      }
      const bk = t.F("head", "back");
      bk.r(0, 2, 8, 1, "T"); bk.r(0, 5, 8, 1, "T"); bk.r(1, 6, 6, 2, "B2");
      if (T(1)) { bk.r(3, 0, 2, 5, "B2"); bk.r(3, 3, 2, 1, "T"); }
      if (T(2)) { bk.r(3, 0, 2, 2, "T"); bk.pts([[0, 2], [7, 2], [0, 5], [7, 5]], "T0"); }
      if (T(3)) bk.r(3, 6, 2, 1, "G");
      if (!T(1)) bk.pts([[2, 3], [5, 3], [3, 4], [4, 4]], "T");
      t.F("head", "bottom").all("D");
      // hat region (preview shell): crest ridge + horn bumps
      if (T(2)) { t.F("hat", "front").r(3, 0, 2, 2, "T"); t.F("hat", "back").r(3, 0, 2, 3, "T"); t.F("hat", "top").r(3, 0, 2, 8, "T"); }
      if (T(3)) for (const s of ["right", "left"]) {
        const f = t.F("hat", s);
        f.pts([[3, 3], [4, 3], [3, 2], [4, 2], [3, 1], [2, 1], [2, 0], [1, 0]], "T"); f.pts([[3, 1], [2, 0], [1, 0]], "T0");
        if (T(4)) f.pts([[2, 2], [1, 1], [0, 0], [0, 1]], "T");
      }
    },
    chest(t) {
      for (const fc of FACES) t.F("body", fc).plate(0, 0, 8, 12);
      const fr = t.F("body", "front");
      fr.plate(0, 0, 4, 5).plate(4, 0, 4, 5).plate(1, 5, 6, 3); fr.r(2, 0, 4, 1, "D");
      fr.r(0, 8, 8, 2, "T"); fr.r(3, 8, 2, 2, GT()); fr.plate(0, 10, 8, 2); fr.r(0, 11, 8, 1, "T0");
      if (T(1)) { fr.r(3, 1, 2, 6, "B2"); fr.r(3, 1, 1, 6, "T"); }
      if (T(2)) fr.pts([[0, 1], [7, 1], [0, 6], [7, 6], [1, 10], [6, 10]], "T");
      if (T(3)) fr.r(3, 2, 2, 2, "G");
      if (T(4)) fr.pts([[1, 2], [6, 2], [2, 6], [5, 6]], "T");
      if (!T(1)) fr.pts([[1, 2], [2, 3], [5, 2], [6, 3], [2, 6], [5, 6], [3, 4], [4, 4]], "T");
      const bk = t.F("body", "back");
      bk.plate(0, 0, 4, 6).plate(4, 0, 4, 6).plate(1, 6, 6, 2); bk.r(0, 8, 8, 2, "T"); bk.plate(0, 10, 8, 2); bk.r(0, 11, 8, 1, "T0");
      bk.r(3, 0, 2, 8, "B2"); if (T(1)) bk.r(3, 0, 1, 8, "T");
      if (T(2)) bk.pts([[0, 1], [7, 1], [0, 6], [7, 6]], "T");
      if (T(3)) bk.r(3, 3, 2, 2, "G");
      if (!T(1)) bk.pts([[1, 2], [6, 2], [1, 5], [6, 5], [2, 9], [5, 9]], "T");
      for (const s of ["right", "left"]) { const f = t.F("body", s); f.plate(0, 0, 4, 12); f.r(0, 8, 4, 2, "T"); f.r(0, 0, 4, 3, "D2"); if (T(2)) f.p(1, 11, "T"); }
      t.F("body", "top").plate(0, 0, 8, 4).r(2, 1, 4, 2, "D"); t.F("body", "bottom").all("B3");
      for (const s of SIDES) {
        const f = t.F("arm", s);
        f.r(0, 5, 4, 4, "B3"); f.pts([[0, 6], [2, 6], [1, 7], [3, 7], [0, 8], [2, 8]], "B2");
        f.plate(0, 0, 4, 5); f.r(0, 4, 4, 1, "T");
        f.plate(0, 9, 4, 3); f.r(0, 9, 4, 1, "T");
        if (T(2)) f.pts([[0, 1], [3, 1], [1, 10], [2, 10]], "T");
        if (T(1)) f.pts([[1, 2], [2, 2]], "T0");
        if (!T(1)) f.pts([[1, 2], [2, 2], [1, 11], [2, 11]], "T");
      }
      if (T(3)) t.F("arm", "right").pts([[1, 2], [2, 2], [1, 1], [2, 1]], "G");
      const at = t.F("arm", "top"); at.plate(0, 0, 4, 4); at.r(0, 0, 4, 1, "T0");
      if (T(4)) at.r(1, 1, 2, 2, "T"); if (T(3)) at.p(1, 1, "G");
      t.F("arm", "bottom").all("B3");
    },
    legs(t) {
      for (const s of SIDES) t.F("body", s).plate(0, 6, 8, 6).r(0, 6, 8, 2, "T");
      t.F("body", "front").r(3, 6, 2, 3, GT()); t.F("body", "back").r(3, 6, 2, 1, "B2");
      t.F("body", "bottom").all("B3");
      for (const s of SIDES) {
        const f = t.F("leg", s);
        f.plate(0, 0, 4, 12); f.r(0, 0, 4, 2, "T"); f.plate(0, 5, 4, 3); f.r(0, 5, 4, 1, "T");
        f.plate(0, 8, 4, 4); f.r(0, 10, 4, 1, "T0");
        if (T(2)) f.pts([[0, 3], [3, 3], [0, 9], [3, 9]], "T");
        if (!T(1)) f.pts([[1, 3], [2, 4], [1, 10]], "T");
      }
      t.F("leg", "front").pts([[1, 6], [2, 6]], GT());
      if (T(4)) t.F("leg", "right").pts([[1, 3], [2, 3], [1, 4], [2, 4]], "T");
      t.F("leg", "bottom").all("B3"); t.F("leg", "top").all("B3");
    },
    boots(t) {
      for (const s of SIDES) {
        const f = t.F("leg", s);
        f.r(0, 6, 4, 1, "T"); f.plate(0, 7, 4, 3); f.plate(0, 10, 4, 2); f.r(0, 11, 4, 1, "D");
        if (s === "front") f.r(0, 10, 4, 1, "T");
        if (s === "back") f.r(0, 10, 4, 1, "B2");
        if (T(2)) f.pts([[0, 7], [3, 7]], "T");
        if (T(3) && s !== "back") f.r(1, 8, 2, 2, "G");
        if (T(4)) f.r(0, 5, 4, 1, "T");
        if (!T(1)) f.pts([[1, 8], [2, 8], [1, 9], [2, 9]], "T");
      }
      const b = t.F("leg", "bottom"); b.all("D"); b.pts([[0, 0], [2, 0], [1, 1], [3, 1], [0, 2], [2, 2], [1, 3], [3, 3]], "D2");
      if (T(4)) t.F("leg", "right").pts([[0, 4], [1, 4], [0, 5], [1, 5], [0, 3]], "T");
    },
  },
  archer: {
    helmet(t) {
      for (const fc of FACES) t.F("head", fc).plate(0, 0, 8, 8);
      const fr = t.F("head", "front");
      fr.r(1, 2, 6, 4, "D"); fr.r(2, 1, 4, 1, "D"); fr.r(2, 6, 4, 1, "D2"); fr.r(2, 2, 4, 1, "D2");
      fr.r(1, 1, 1, 6, T(2) ? "T" : "B0"); fr.r(6, 1, 1, 6, T(2) ? "T" : "B2");
      fr.r(0, 0, 1, 8, "B2"); fr.r(7, 0, 1, 8, "B3"); fr.r(1, 7, 6, 1, "B2");
      if (T(1)) fr.r(2, 7, 4, 1, "T");
      fr.r(3, 0, 2, 1, "B0");
      if (T(3)) fr.pts([[3, 0], [4, 0]], "G");
      if (!T(1)) fr.pts([[0, 2], [0, 5], [7, 2], [7, 5]], "T");
      const top = t.F("head", "top"); top.r(3, 0, 2, 8, "B2"); top.r(3, 0, 1, 8, "B1");
      for (let y = 0; y < 8; y += 2) top.pts([[2, y], [5, y]], "T0");
      if (T(3)) top.p(3, 3, "G");
      for (const s of ["right", "left"]) {
        const f = t.F("head", s);
        f.r(0, 4, 8, 4, "B2"); f.plate(0, 0, 8, 5); f.r(1, 6, 6, 2, "B1");
        f.pts([[1, 1], [2, 2], [3, 3], [4, 4]], "B0"); f.pts([[0, 6], [2, 7], [4, 6], [6, 7]], "T0");
        if (T(1)) f.r(1, 5, 6, 1, "T");
        if (T(2)) f.pts([[1, 4], [6, 4]], "T");
      }
      if (T(1)) {
        const lf = t.F("head", "left"), n = T(3) ? 6 : 5;
        for (let i = 0; i < n; i++) { lf.p(2 + (i >> 1), 5 - i, "F"); lf.p(3 + (i >> 1), 5 - i, "F2"); }
        lf.p(2, 5, "T"); lf.p(1, 6, "T");
      }
      const bk = t.F("head", "back"); bk.r(3, 0, 2, 8, "B2"); bk.pts([[3, 2], [4, 3], [3, 4], [4, 5]], "T0"); bk.r(0, 6, 8, 2, "B2");
      if (T(1)) bk.r(2, 6, 4, 1, "T");
      t.F("head", "bottom").all("D");
      if (T(1)) { const f = t.F("hat", "left"), n = T(3) ? 7 : 5; for (let i = 0; i < n; i++) { f.p(2 + (i >> 1), 5 - i, "F"); f.p(3 + (i >> 1), 5 - i, "F2"); } }
      if (T(4)) t.F("hat", "front").pts([[3, 0], [4, 0]], "F");
    },
    chest(t) {
      for (const fc of FACES) t.F("body", fc).plate(0, 0, 8, 12);
      const fr = t.F("body", "front");
      fr.r(2, 0, 4, 2, "D"); fr.r(3, 2, 2, 1, "D2");
      if (T(1)) for (let y = 3; y < 9; y += 2) for (let x = (y & 2) ? 1 : 0; x < 8; x += 2) fr.p(x, y, "T0");
      fr.r(0, 9, 8, 2, "T"); fr.r(3, 9, 2, 2, GT()); fr.r(0, 11, 8, 1, "B2");
      for (let i = 0; i < 12; i++) { const x = Math.round(i * 7 / 11) - 1; fr.p(x, i, "T"); fr.p(x + 1, i, "T"); fr.p(x + 2, i, "T0"); }
      fr.p(4, 5, GT()); fr.p(3, 4, GT());
      if (T(2)) fr.pts([[0, 0], [7, 0], [1, 9], [6, 9]], "T");
      if (!T(1)) fr.pts([[1, 3], [6, 4], [1, 7], [6, 7], [5, 2]], "T0");
      const bk = t.F("body", "back");
      bk.r(0, 9, 8, 2, "T"); bk.r(0, 11, 8, 1, "B2");
      for (let i = 0; i < 9; i++) { const x = 7 - Math.round(i * 7 / 8); bk.p(x - 1, i, "T"); bk.p(x, i, "T"); }
      bk.plate(2, 1, 3, 8, "T"); bk.r(2, 1, 3, 1, "D2"); bk.p(3, 0, "F"); bk.p(2, 0, "F2"); bk.p(4, 0, "F");
      bk.pts([[2, 4], [4, 6], [3, 2]], "T0");
      if (T(1)) bk.pts([[2, 8], [3, 8], [4, 8]], "T0");
      if (T(3)) bk.p(3, 5, "G");
      if (T(2)) bk.pts([[0, 0], [7, 0]], "T");
      for (const s of ["right", "left"]) { const f = t.F("body", s); f.plate(0, 0, 4, 12); f.r(0, 9, 4, 2, "T"); f.r(0, 0, 4, 3, "D2"); }
      t.F("body", "top").plate(0, 0, 8, 4).r(2, 1, 4, 2, "D"); t.F("body", "bottom").all("B3");
      for (const s of SIDES) {
        const f = t.F("arm", s);
        f.plate(0, 0, 4, 12); f.r(0, 0, 4, 2, "B0"); f.r(0, 9, 4, 3, "T"); f.r(0, 11, 4, 1, "T0"); f.pts([[1, 5], [2, 6], [1, 7]], "T0");
        if (T(1)) f.pts([[1, 3], [2, 4], [1, 2]], "T0");
        if (T(2)) f.r(0, 2, 4, 1, "T");
      }
      if (T(3)) t.F("arm", "right").pts([[1, 10], [2, 10]], "G");
      t.F("arm", "top").plate(0, 0, 4, 4); t.F("arm", "bottom").all("B3");
    },
    legs(t) {
      for (const s of SIDES) t.F("body", s).plate(0, 6, 8, 6).r(0, 6, 8, 2, "T");
      t.F("body", "front").r(3, 6, 2, 3, GT()); t.F("body", "bottom").all("B3");
      for (const s of SIDES) {
        const f = t.F("leg", s);
        f.plate(0, 0, 4, 12); f.r(0, 0, 4, 2, "T"); f.r(0, 5, 4, 2, "T"); f.r(0, 10, 4, 1, "T");
        if (T(1)) f.pts([[1, 3], [2, 4], [1, 8], [2, 9]], "T0");
        if (T(2)) f.pts([[0, 2], [3, 2]], "T");
        if (!T(1)) f.pts([[2, 3], [1, 8]], "T");
      }
      const p = t.F("leg", "right"); p.plate(0, 1, 4, 4, "T"); p.r(1, 2, 2, 2, "B"); p.p(2, 3, GT()); if (T(4)) p.pts([[0, 0], [1, 0]], "F");
      t.F("leg", "front").p(2, 6, GT()); t.F("leg", "bottom").all("B3");
    },
    boots(t) {
      for (const s of SIDES) {
        const f = t.F("leg", s);
        f.plate(0, 6, 4, 6); f.r(0, 6, 4, 2, "T"); f.p(0, 6, "T0"); f.r(0, 11, 4, 1, "D");
        if (T(1)) f.r(0, 10, 4, 1, "T");
        if (T(2)) f.pts([[0, 7], [3, 7]], "T0");
        if (T(3) && s !== "back") f.pts([[1, 8], [2, 8]], "G");
        if (!T(1)) f.pts([[1, 9], [2, 9]], "T0");
      }
      const f = t.F("leg", "front"); f.pts([[1, 8], [2, 9]], "T0"); f.r(0, 9, 4, 1, "B2");
      if (T(4)) t.F("leg", "left").pts([[0, 4], [1, 4], [0, 3], [1, 2], [0, 5]], "F");
      t.F("leg", "bottom").all("D");
    },
  },
  mage: {
    helmet(t) {
      for (const s of SIDES) {
        const f = t.F("head", s);
        f.all("B3");
        for (let r = 0; r < 4; r++) { const hw = 1 + r; f.r(4 - hw, r, hw * 2, 1, "B1"); f.r(4 - hw, r, hw, 1, "B0"); f.r(4, r, hw, 1, "B2"); }
        f.r(0, 4, 8, 1, "T"); f.r(0, 5, 8, 1, "B3"); f.plate(0, 6, 8, 2, "B");
        if (T(2)) { f.pts([[3, 1], [4, 2], [3, 3]], "T"); f.r(0, 5, 8, 1, "T0"); }
        if (T(3)) f.pts([[2, 7]], "G");
        if (T(4)) f.pts([[5, 7], [2, 7]], "G");
        if (!T(1)) f.pts([[3, 1], [4, 2], [2, 7], [5, 7]], "T");
      }
      const top = t.F("head", "top"); // pyramid facets seen from above
      for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) {
        const dx = x - 3.5, dy = y - 3.5; let k;
        if (Math.abs(dx) > Math.abs(dy)) k = dx < 0 ? "B0" : "B2"; else k = dy > 0 ? "B1" : "B3";
        if (Math.max(Math.abs(dx), Math.abs(dy)) > 3) k = "T"; // brim edge ring
        top.p(x, y, k);
      }
      top.r(3, 3, 2, 2, T(2) ? "T" : "B0");
      if (T(3)) top.p(3, 3, "G"); if (T(5)) top.pts([[4, 4], [3, 4]], "G");
      t.F("head", "bottom").all("D");
      for (const s of SIDES) { const f = t.F("hat", s); f.r(0, 4, 8, 1, T(2) ? "T" : "T0"); if (T(3)) f.pts([[1, 4], [4, 4], [6, 4]], "G"); if (T(4)) f.pts([[3, 3], [4, 3]], "T"); }
    },
    chest(t) {
      for (const fc of FACES) t.F("body", fc).plate(0, 0, 8, 12);
      const fr = t.F("body", "front");
      fr.r(2, 0, 4, 1, "T"); fr.r(3, 1, 2, 1, "D"); fr.r(3, 2, 2, 4, "B2"); fr.r(3, 2, 1, 4, "T");
      fr.r(0, 6, 8, 2, "T"); fr.r(0, 6, 8, 1, "T0"); fr.r(5, 6, 2, 2, GT()); fr.r(5, 8, 2, 4, "T"); fr.p(5, 11, "T0"); fr.p(6, 11, "T0");
      fr.r(0, 11, 5, 1, "T");
      if (T(1)) fr.pts([[1, 9], [3, 9], [2, 10]], "T0");
      if (T(2)) fr.pts([[1, 2], [1, 3], [2, 3], [6, 3], [6, 2], [1, 8]], "T");
      if (T(3)) fr.pts([[1, 3], [6, 3]], "G");
      if (T(4)) fr.pts([[1, 9], [3, 9], [6, 4]], "G");
      if (!T(1)) fr.pts([[1, 2], [6, 3], [1, 9], [3, 10]], "T");
      const bk = t.F("body", "back");
      bk.r(0, 6, 8, 2, "T"); bk.r(0, 11, 8, 1, "T"); bk.r(2, 0, 4, 1, "T0");
      bk.pts([[3, 2], [4, 2], [2, 3], [5, 3], [2, 4], [5, 4], [3, 5], [4, 5]], T(2) ? "T" : "T0"); bk.pts([[3, 3], [4, 4]], GT()); if (T(2)) bk.pts([[3, 4], [4, 3]], "T");
      bk.pts([[1, 9], [6, 9], [3, 9]], "B2");
      if (T(3)) bk.r(3, 3, 2, 2, "G");
      for (const s of ["right", "left"]) { const f = t.F("body", s); f.plate(0, 0, 4, 12); f.r(0, 6, 4, 2, "T"); f.r(0, 0, 4, 3, "D2"); f.r(0, 11, 4, 1, "T"); }
      t.F("body", "top").plate(0, 0, 8, 4).r(2, 1, 4, 2, "D").r(2, 0, 4, 1, "T0"); t.F("body", "bottom").all("B3");
      for (const s of SIDES) {
        const f = t.F("arm", s);
        f.plate(0, 0, 4, 12); f.r(0, 0, 4, 1, "B0"); f.r(0, 9, 4, 3, "B2"); f.r(0, 10, 4, 2, "T"); f.r(0, 9, 4, 1, "T0");
        for (const [x, y] of [[1, 4], [2, 5], [1, 6]]) f.p(x, y, "B2");
        if (T(1)) f.pts([[0, 11], [1, 11], [2, 11], [3, 11]], "T0");
        if (T(2)) f.pts([[1, 3], [2, 3]], "T");
      }
      if (T(3)) t.F("arm", "right").pts([[1, 4], [2, 4], [2, 5]], "G");
      t.F("arm", "top").plate(0, 0, 4, 4); t.F("arm", "bottom").all("B3");
    },
    legs(t) {
      for (const s of SIDES) t.F("body", s).plate(0, 6, 8, 6).r(0, 6, 8, 2, "T");
      t.F("body", "front").r(3, 8, 2, 4, "B3"); t.F("body", "bottom").all("B3");
      for (const s of SIDES) {
        const f = t.F("leg", s);
        f.plate(0, 0, 4, 12); f.r(0, 0, 4, 1, "T0"); f.r(0, 10, 4, 2, "T"); f.r(0, 10, 4, 1, "T0");
        if (T(1)) f.pts([[0, 4], [2, 6], [1, 8]], "B2");
        if (T(2)) f.pts([[0, 11], [2, 11]], "B3");
        if (T(3)) f.pts([[1, 11], [3, 11]], "G");
        if (T(4)) f.pts([[1, 11]], "G");
        if (!T(1)) f.pts([[1, 3], [2, 5], [1, 8]], "T");
      }
      t.F("leg", "front").r(3, 3, 1, 8, "D2"); t.F("leg", "bottom").all("B3");
    },
    boots(t) {
      for (const s of SIDES) {
        const f = t.F("leg", s);
        f.plate(0, 6, 4, 6); f.r(0, 6, 4, 1, "T"); f.r(0, 7, 4, 1, "B3"); f.r(0, 11, 4, 1, "D");
        if (T(1)) f.r(0, 10, 4, 1, "T");
        if (T(2)) f.pts([[1, 8], [2, 9]], "T");
        if (T(3) && s !== "back") f.pts([[1, 8], [2, 8]], "G");
        if (T(4)) f.r(0, 5, 4, 1, "T");
      }
      t.F("leg", "front").r(0, 11, 4, 1, "T"); t.F("leg", "bottom").all("D");
      if (!T(1)) t.F("leg", "front").pts([[1, 9], [2, 9]], "T0");
    },
  },
};

const SPARKS = {
  warrior: [[["head", "front", 1, 0], ["body", "front", 1, 4], ["arm", "top", 2, 2]], [["leg", "front", 2, 6], ["body", "front", 6, 9]]],
  archer: [[["head", "front", 6, 0], ["body", "front", 2, 2], ["arm", "front", 1, 4]], [["leg", "front", 1, 2]]],
  mage: [[["head", "front", 4, 1], ["body", "front", 1, 4], ["arm", "front", 2, 6]], [["leg", "front", 1, 8]]],
};
function buildLayers(c, ti) {
  cls = c; tier = ti;
  const d = designs[c], l1 = new Tex(), l2 = new Tex();
  d.helmet(l1); d.chest(l1); d.boots(l1); d.legs(l2);
  if (c === "mage") l1.clear("head", "front", 1, 6, 6, 2); // open face under the brim
  const bufs = [l1, l2].map(l => l.finish());
  if (ti === 5) bufs.forEach((b, li) => {
    for (let k = 0; k < 64 * 32; k++) { const i = k * 4; if (b[i + 3] && b[i] < 215 && b[i + 1] > 200 && b[i + 2] > 180) { b[i] = 170; b[i + 1] = 255; b[i + 2] = 238; } }
    for (const [bx, face, x, y] of SPARKS[c][li]) {
      const r = uvRect(bx, face), i = ((r.y + y) * 64 + r.x + x) * 4;
      if (b[i + 3]) { b[i] = 255; b[i + 1] = 255; b[i + 2] = 255; }
    }
  });
  return bufs;
}

// ---------- mannequin skin (64x32) ----------
function makeSkin() {
  const buf = Buffer.alloc(64 * 32 * 4);
  const mix = (a, b) => a.map((v, i) => (v + b[i]) >> 1);
  const paint = (box, base, light, dark) => {
    for (const fc of FACES) {
      const r = uvRect(box, fc), sh = fc === "top" ? light : fc === "bottom" ? dark : fc === "left" || fc === "back" ? mix(dark, base) : base;
      for (let y = 0; y < r.h; y++) for (let x = 0; x < r.w; x++) {
        const k = (x === 0 || y === 0 || x === r.w - 1 || y === r.h - 1) ? .93 : 1;
        const i = ((r.y + y) * 64 + r.x + x) * 4; buf[i] = sh[0] * k; buf[i + 1] = sh[1] * k; buf[i + 2] = sh[2] * k; buf[i + 3] = 255;
      }
    }
  };
  const L = hex("#a2afc2"), D = hex("#667389");
  paint("head", hex("#9aa7ba"), hex("#b0bccd"), hex("#6f7c92")); paint("body", hex("#7f8da5"), L, D); paint("arm", hex("#8a98ae"), L, D); paint("leg", hex("#6f7d95"), L, D);
  const set = (x, y, c) => { const i = (y * 64 + x) * 4; buf[i] = c[0]; buf[i + 1] = c[1]; buf[i + 2] = c[2]; };
  const fr = uvRect("head", "front"), hair = hex("#4a5568");
  for (const fc of FACES) { if (fc === "bottom") continue; const r = uvRect("head", fc), rows = fc === "top" ? r.h : 2; for (let y = 0; y < rows; y++) for (let x = 0; x < r.w; x++) set(r.x + x, r.y + y, hair); }
  for (const x of [1, 2, 5, 6]) set(fr.x + x, fr.y + 5, hex("#1b2230"));
  set(fr.x + 3, fr.y + 7, hex("#5b687e")); set(fr.x + 4, fr.y + 7, hex("#5b687e"));
  return buf;
}

// ---------- software renderer ----------
const vsub = (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
const vcross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const vnorm = a => { const l = Math.hypot(...a) || 1; return a.map(v => v / l); };
const vdot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];

// world: x = viewer's right when looking at the front, y up, +z = front of the player.
function faceDefs(mn, mx, mirror) {
  const [x0, y0, z0] = mn, [x1, y1, z1] = mx, sx = x1 - x0, sy = y1 - y0, sz = z1 - z0;
  const defs = [
    { geo: "front", tex: "front", o: [x0, y1, z1], u: [sx, 0, 0], v: [0, -sy, 0] },
    { geo: "back", tex: "back", o: [x1, y1, z0], u: [-sx, 0, 0], v: [0, -sy, 0] },
    { geo: "right", tex: "right", o: [x0, y1, z0], u: [0, 0, sz], v: [0, -sy, 0] },
    { geo: "left", tex: "left", o: [x1, y1, z1], u: [0, 0, -sz], v: [0, -sy, 0] },
    { geo: "top", tex: "top", o: [x0, y1, z0], u: [sx, 0, 0], v: [0, 0, sz] },
    { geo: "bottom", tex: "bottom", o: [x0, y0, z1], u: [sx, 0, 0], v: [0, 0, -sz] },
  ];
  if (mirror) for (const d of defs) { if (d.geo === "left") d.tex = "right"; else if (d.geo === "right") d.tex = "left"; d.flip = true; }
  return defs;
}
const SS = 3;
function renderFigure(boxes, yaw, W, H, scale) {
  const w = W * SS, h = H * SS, s = scale * SS;
  const col = new Float32Array(w * h * 4), zb = new Float32Array(w * h).fill(-1e9);
  const th = yaw * Math.PI / 180, ph = 20 * Math.PI / 180;
  const cy = Math.cos(th), sy_ = Math.sin(th), cp = Math.cos(ph), sp = Math.sin(ph);
  const L = vnorm([-0.45, 0.75, 0.55]);
  const cam = p => { const x = p[0] * cy + p[2] * sy_, z = -p[0] * sy_ + p[2] * cy; return [x, p[1] * cp - z * sp, p[1] * sp + z * cp]; };
  for (const b of boxes) {
    const { min, max, uv, tex, mirror, pose } = b;
    const pose3 = p => {
      if (!pose) return p;
      const [py, pz] = pose.pivot, a = pose.ang * Math.PI / 180, ca = Math.cos(a), sa = Math.sin(a);
      return [p[0], py + (p[1] - py) * ca - (p[2] - pz) * sa, pz + (p[1] - py) * sa + (p[2] - pz) * ca];
    };
    const ctr = cam(pose3([(min[0] + max[0]) / 2, (min[1] + max[1]) / 2, (min[2] + max[2]) / 2]));
    for (const d of faceDefs(min, max, mirror)) {
      const rect = uvRect(uv, d.tex), O = d.o, U = d.u, V = d.v;
      const cs = [[0, 0], [1, 0], [1, 1], [0, 1]].map(([a, c]) => ({ a, c, p: cam(pose3([O[0] + U[0] * a + V[0] * c, O[1] + U[1] * a + V[1] * c, O[2] + U[2] * a + V[2] * c])) }));
      let n = vnorm(vcross(vsub(cs[1].p, cs[0].p), vsub(cs[3].p, cs[0].p)));
      const fc = [0, 1, 2].map(i => (cs[0].p[i] + cs[2].p[i]) / 2);
      if (vdot(n, vsub(fc, ctr)) < 0) n = n.map(v => -v);
      const lit = .5 + .5 * Math.max(0, vdot(n, L)), shade = n[2] > 0 ? lit : lit * .75;
      for (const tri of [[0, 1, 2], [0, 2, 3]]) {
        const A = tri.map(i => cs[i]);
        const pr = A.map(q => [w / 2 + q.p[0] * s, h / 2 - (q.p[1] - 15 * cp) * s, q.p[2]]);
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
          if (d.flip) a = 1 - a;
          const tx = rect.x + Math.min(rect.w - 1, Math.max(0, Math.floor(a * rect.w))), ty = rect.y + Math.min(rect.h - 1, Math.max(0, Math.floor(c * rect.h)));
          const ti = (ty * 64 + tx) * 4;
          if (tex[ti + 3] < 128) continue;
          zb[y * w + x] = z;
          const o = (y * w + x) * 4;
          col[o] = Math.min(255, tex[ti] * shade); col[o + 1] = Math.min(255, tex[ti + 1] * shade); col[o + 2] = Math.min(255, tex[ti + 2] * shade); col[o + 3] = 1;
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

// mannequin + armour box lists (vanilla dilation: head/body/arms/boots 1.0, leggings 0.5)
function figureBoxes(skin, l1, l2) {
  const boxes = [], AR = 10, LG = 9;
  const parts = {
    head: { mn: [-4, 24, -4], mx: [4, 32, 4], uv: "head" }, hat: { mn: [-4, 24, -4], mx: [4, 32, 4], uv: "hat" },
    body: { mn: [-4, 12, -2], mx: [4, 24, 2], uv: "body" },
    rarm: { mn: [-8, 12, -2], mx: [-4, 24, 2], uv: "arm", pose: { pivot: [22, 0], ang: -AR } }, larm: { mn: [4, 12, -2], mx: [8, 24, 2], uv: "arm", mirror: true, pose: { pivot: [22, 0], ang: AR } },
    rleg: { mn: [-4, 0, -2], mx: [0, 12, 2], uv: "leg", pose: { pivot: [12, 0], ang: LG } }, lleg: { mn: [0, 0, -2], mx: [4, 12, 2], uv: "leg", mirror: true, pose: { pivot: [12, 0], ang: -LG } },
  };
  const add = (name, tex, d) => { const p = parts[name]; boxes.push({ min: p.mn.map(v => v - d), max: p.mx.map(v => v + d), uv: p.uv, tex, mirror: p.mirror, pose: p.pose }); };
  for (const n of ["head", "body", "rarm", "larm", "rleg", "lleg"]) add(n, skin, 0);
  if (l2) { add("body", l2, .5); add("rleg", l2, .5); add("lleg", l2, .5); }
  if (l1) { for (const n of ["head", "body", "rarm", "larm", "rleg", "lleg"]) add(n, l1, 1); add("hat", l1, 1.5); }
  return boxes;
}

// ---------- layers -> files, figures ----------
const skin = makeSkin();
const FW = 190, FH = 276, SCALE = 6.4;
const figs = {}, layers = {};
for (const c of CLASSES) for (let ti = 0; ti < 6; ti++) {
  const [l1, l2] = buildLayers(c, ti);
  layers[`${c}_${ti}`] = [l1, l2];
  fs.writeFileSync(path.join(out, `${c}_t${ti}_layer_1.png`), png(l1, 64, 32));
  fs.writeFileSync(path.join(out, `${c}_t${ti}_layer_2.png`), png(l2, 64, 32));
  const boxes = figureBoxes(skin, l1, l2);
  figs[`${c}_${ti}`] = { front: renderFigure(boxes, -32, FW, FH, SCALE), back: renderFigure(boxes, 148, FW, FH, SCALE) };
  fs.writeFileSync(path.join(out, `${c}_t${ti}_front.png`), png(figs[`${c}_${ti}`].front, FW, FH));
  fs.writeFileSync(path.join(out, `${c}_t${ti}_back.png`), png(figs[`${c}_${ti}`].back, FW, FH));
}
fs.writeFileSync(path.join(out, "mannequin.png"), png(renderFigure(figureBoxes(skin, null, null), -32, FW, FH, SCALE), FW, FH));

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
const NAVY = hex("#0e1430"), CELL = hex("#1a2250"), WHITE = hex("#ffffff"), GREY = hex("#9aa6d0"), GOLD = hex("#ffd447");
const save = (cv, name) => fs.writeFileSync(path.join(out, name), png(cv.b, cv.w, cv.h));
const shadow = (cv, cx, cy, rw) => { for (let y = -8; y <= 8; y++) for (let x = -rw; x <= rw; x++) if ((x * x) / (rw * rw) + (y * y) / 64 <= 1) fill(cv, cx + x, cy + y, 1, 1, [0, 0, 0], 90); };
for (const c of CLASSES) {
  const pad = 10, labelH = 54, cw = FW + pad, ch = FH + pad;
  const cv = canvas(6 * cw + pad, labelH + 2 * ch + pad, NAVY);
  text(cv, c.toUpperCase() + " ARMOR T0-T5 (FRONT / BACK)", 10, 10, 4, WHITE);
  for (let ti = 0; ti < 6; ti++) {
    const x = pad + ti * cw; text(cv, "T" + ti, x + 4, 34, 4, GOLD); text(cv, TIERS[ti], x + 52, 38, 2, GREY);
    ["front", "back"].forEach((v, r) => { const y = labelH + r * ch; fill(cv, x, y, FW, FH, CELL); shadow(cv, x + FW / 2 - 4, y + FH - 26, 52); blit(cv, figs[`${c}_${ti}`][v], FW, FH, x, y); });
  }
  save(cv, `sheet_${c}.png`);
}
{
  const k = 0.82, pad = 8, labelW = 120, head = 52;
  const w0 = Math.round(FW * k), h0 = Math.round(FH * k);
  const cv = canvas(labelW + 6 * (w0 + pad) + pad, head + 3 * (h0 + pad) + pad, NAVY);
  text(cv, "WORN ARMOR - WARRIOR / ARCHER / MAGE - T0-T5", 10, 8, 3, WHITE);
  CLASSES.forEach((c, r) => {
    const y = head + r * (h0 + pad); text(cv, c.toUpperCase(), 10, y + h0 / 2 - 6, 3, GOLD);
    for (let ti = 0; ti < 6; ti++) {
      const x = labelW + ti * (w0 + pad); fill(cv, x, y, w0, h0, CELL);
      if (r === 0) text(cv, "T" + ti, x + 4, head - 20, 3, GREY);
      const s = scaled(figs[`${c}_${ti}`].front, FW, FH, k); shadow(cv, x + w0 / 2 - 4, y + h0 - 22, 42); blit(cv, s.b, s.w, s.h, x, y);
    }
  });
  save(cv, "sheet_all.png");
}
{ // texture overview (layers at 4x, checker bg)
  const sc = 4, cw = 64 * sc + 6, chh = 32 * sc + 6;
  const cv = canvas(120 + 6 * cw, 40 + 6 * chh, hex("#2a2f4a"));
  text(cv, "LAYER 1 / LAYER 2 TEXTURES (64X32)", 10, 10, 3, WHITE);
  CLASSES.forEach((c, r) => { for (let ti = 0; ti < 6; ti++) for (let li = 0; li < 2; li++) {
    const x = 120 + ti * cw, y = 36 + (r * 2 + li) * chh, b = layers[`${c}_${ti}`][li];
    for (let j = 0; j < 32 * sc; j += 8) for (let i = 0; i < 64 * sc; i += 8) fill(cv, x + i, y + j, 8, 8, ((i + j) / 8) % 2 ? hex("#3a4060") : hex("#333853"));
    for (let yy = 0; yy < 32; yy++) for (let xx = 0; xx < 64; xx++) { const i = (yy * 64 + xx) * 4; if (b[i + 3]) fill(cv, x + xx * sc, y + yy * sc, sc, sc, [b[i], b[i + 1], b[i + 2]]); }
    if (ti === 0) text(cv, c.slice(0, 3) + " L" + (li + 1), 6, y + 56, 2, GOLD);
  } });
  save(cv, "sheet_layers.png");
}
console.log("done ->", out);
