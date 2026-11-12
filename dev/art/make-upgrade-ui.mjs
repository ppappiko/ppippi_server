// Generates 강화 UI textures: node dev/art/make-upgrade-ui.mjs
import fs from "node:fs";
import path from "node:path";
import zlib from "node:zlib";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const out = path.resolve(here, "../pipi-travel/assets/pipitravel/textures/gui");
fs.mkdirSync(out, { recursive: true });

const crcT = new Uint32Array(256).map((_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const crc = b => { let c = 0xffffffff; for (const x of b) c = crcT[(c ^ x) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
function chunk(type, data) {
  const t = Buffer.from(type, "ascii"), l = Buffer.alloc(4), c = Buffer.alloc(4);
  l.writeUInt32BE(data.length); c.writeUInt32BE(crc(Buffer.concat([t, data])));
  return Buffer.concat([l, t, data, c]);
}
function png(N, px) {
  const raw = Buffer.alloc((N * 4 + 1) * N);
  for (let y = 0; y < N; y++) { raw[y * (N * 4 + 1)] = 0; for (let x = 0; x < N * 4; x++) raw[y * (N * 4 + 1) + 1 + x] = px[y * N * 4 + x]; }
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(N, 0); ihdr.writeUInt32BE(N, 4); ihdr[8] = 8; ihdr[9] = 6;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk("IHDR", ihdr), chunk("IDAT", zlib.deflateSync(raw)), chunk("IEND", Buffer.alloc(0))]);
}
const clamp01 = a => Math.max(0, Math.min(1, a));
// layers: f(x,y,N) returns [r,g,b,a] or null; composited in order (over operator)
function makeColor(name, N, layers) {
  const px = Buffer.alloc(N * N * 4);
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    let r = 0, g = 0, b = 0, a = 0;
    for (const f of layers) {
      const c = f(x + .5 - N / 2, y + .5 - N / 2, N); if (!c || c[3] <= 0) continue;
      const sa = c[3], oa = sa + a * (1 - sa);
      r = (c[0] * sa + r * a * (1 - sa)) / oa; g = (c[1] * sa + g * a * (1 - sa)) / oa; b = (c[2] * sa + b * a * (1 - sa)) / oa; a = oa;
    }
    const i = (y * N + x) * 4;
    px[i] = Math.round(r); px[i + 1] = Math.round(g); px[i + 2] = Math.round(b); px[i + 3] = Math.round(a * 255);
  }
  fs.writeFileSync(path.join(out, name), png(N, px));
}
function segDist(x, y, x0, y0, x1, y1) {
  const dx = x1 - x0, dy = y1 - y0, l = dx * dx + dy * dy || 1;
  const t = Math.max(0, Math.min(1, ((x - x0) * dx + (y - y0) * dy) / l));
  return Math.hypot(x - x0 - dx * t, y - y0 - dy * t);
}

// ---- ornate item frame (96x96): faint rune ring, double diamond, dark plate, gold square slot with bright corner brackets
const GOLD = [214, 178, 106], BRIGHT = [255, 232, 168], STEEL = [86, 110, 140], DEEP = [9, 15, 25];
const cheb = (x, y) => Math.max(Math.abs(x), Math.abs(y));
makeColor("upgrade_frame.png", 96, [
  (x, y) => { const d = Math.hypot(x, y); return (Math.abs(x) + Math.abs(y) < 35 && cheb(x, y) > 19) ? [...DEEP, .82] : null; },
  (x, y) => { const d = Math.hypot(x, y); return Math.abs(d - 45) < .75 ? [...STEEL, .7] : null; },
  (x, y) => { // ring ticks every 15 degrees
    const d = Math.hypot(x, y), a = (Math.atan2(y, x) * 180 / Math.PI + 360) % 15;
    return Math.abs(d - 42.5) < 2 && (a < 1.6 || a > 13.4) ? [...STEEL, .8] : null; },
  (x, y) => Math.abs(Math.abs(x) + Math.abs(y) - 39) < .8 ? [...GOLD, .65] : null,
  (x, y) => Math.abs(Math.abs(x) + Math.abs(y) - 35) < .6 ? [...STEEL, .75] : null,
  (x, y) => { // diamond tip jewels
    for (const [cx, cy] of [[39, 0], [-39, 0], [0, 39], [0, -39]]) if (Math.abs(x - cx) + Math.abs(y - cy) < 4.2) return [...BRIGHT, 1];
    for (const [cx, cy] of [[45, 0], [-45, 0], [0, 45], [0, -45]]) if (Math.hypot(x - cx, y - cy) < 2.4) return [...GOLD, 1];
    return null; },
  (x, y) => { const c = cheb(x, y); return Math.abs(c - 19.5) < 1.1 ? [...GOLD, 1] : null; },
  (x, y) => { // corner brackets
    const ax = Math.abs(x), ay = Math.abs(y);
    if (cheb(x, y) > 22.2 || cheb(x, y) < 17) return null;
    const nearEdgeX = ax > 12 && Math.abs(ay - 20.5) < 1.6, nearEdgeY = ay > 12 && Math.abs(ax - 20.5) < 1.6;
    return nearEdgeX || nearEdgeY ? [...BRIGHT, 1] : null; },
  (x, y) => { // inner thin steel line just inside the square
    const c = cheb(x, y); return Math.abs(c - 17.2) < .55 ? [...STEEL, .55] : null; },
]);

// ---- rune ring (128, white on alpha, tinted at draw time): outer/inner circles + 12 glyph clusters + dots
function glyphSegs() {
  const segs = [];
  let s = 12345; const rnd = () => { s = (Math.imul(s, 1664525) + 1013904223) >>> 0; return s / 4294967296; };
  for (let i = 0; i < 12; i++) {
    const ang = i * Math.PI / 6, ca = Math.cos(ang), sa = Math.sin(ang);
    const loc = (r, t) => [ca * r - sa * t, sa * r + ca * t]; // r outward, t tangential
    const n = 2 + Math.floor(rnd() * 2);
    for (let k = 0; k < n; k++) {
      const t0 = (rnd() - .5) * 8, t1 = (rnd() - .5) * 8, r0 = 49 + rnd() * 2, r1 = 55 + rnd() * 3;
      if (rnd() < .5) segs.push([...loc(r0, t0), ...loc(r1, t0)]); else segs.push([...loc(r0, t0), ...loc(r0 + 3 + rnd() * 3, t1)]);
    }
    segs.push([...loc(50, -5), ...loc(50, 5)]);
  }
  return segs;
}
const GS = glyphSegs();
makeColor("upgrade_runes.png", 128, [
  (x, y) => { const d = Math.hypot(x, y); return Math.abs(d - 60.5) < .8 ? [255, 255, 255, .9] : null; },
  (x, y) => { const d = Math.hypot(x, y); return Math.abs(d - 47) < .6 ? [255, 255, 255, .6] : null; },
  (x, y) => { for (const g of GS) if (segDist(x, y, ...g) < .85) return [255, 255, 255, 1]; return null; },
  (x, y) => { const d = Math.hypot(x, y), a = Math.atan2(y, x); for (let i = 0; i < 12; i++) { const aa = i * Math.PI / 6 + Math.PI / 12; if (Math.hypot(x - Math.cos(aa) * 54, y - Math.sin(aa) * 54) < 1.7) return [255, 255, 255, 1]; } return null; },
]);

// ---- soft radial glow (64, white on alpha), 10 alpha steps for a slightly pixel look
makeColor("upgrade_glow.png", 64, [
  (x, y, N) => { const d = Math.hypot(x, y) / (N / 2); if (d >= 1) return null; const a = Math.pow(1 - d, 1.7); return [255, 255, 255, Math.round(clamp01(a) * 10) / 10]; },
]);
console.log("ok");
