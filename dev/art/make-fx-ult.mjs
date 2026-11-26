// Ultimate skill "flat light" effect models: textured glowing planes instead of particle dots.  node dev/art/make-fx-ult.mjs
// Writes pipi-facilities/assets/pipifacilities/{models,textures}/item/fx_ult_<name>.{json,png}
// Planes (crescent, sigils, halo) lie flat (y = 8) and span -16..32 = 3 blocks at scale 1, centred on the display.
// Texture top (v = 0) is model -Z, which the ItemDisplay renders toward the facing direction (see ClassSkills.facing).
// beam: two crossed vertical quads from the display origin upward (1.5 blocks tall, 1 block wide at scale 1).
// All shapes are original designs (octagon sigil, spoke web, gradient crescent); only the "flat glowing light" mood is borrowed.
import fs from "node:fs";
import path from "node:path";
import zlib from "node:zlib";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "../pipi-facilities/assets/pipifacilities");
const texDir = path.join(root, "textures/item"), modDir = path.join(root, "models/item");

const crcT = new Uint32Array(256).map((_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const crc = b => { let c = 0xffffffff; for (const x of b) c = crcT[(c ^ x) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
function chunk(type, data) {
  const t = Buffer.from(type, "ascii"), l = Buffer.alloc(4), c = Buffer.alloc(4);
  l.writeUInt32BE(data.length); c.writeUInt32BE(crc(Buffer.concat([t, data])));
  return Buffer.concat([l, t, data, c]);
}
function png(P, N) {
  const raw = Buffer.alloc((N * 4 + 1) * N);
  for (let y = 0; y < N; y++) { raw[y * (N * 4 + 1)] = 0; for (let x = 0; x < N * 4; x++) raw[y * (N * 4 + 1) + 1 + x] = P[y * N * 4 + x]; }
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(N, 0); ihdr.writeUInt32BE(N, 4); ihdr[8] = 8; ihdr[9] = 6;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk("IHDR", ihdr), chunk("IDAT", zlib.deflateSync(raw)), chunk("IEND", Buffer.alloc(0))]);
}
const hex = h => [1, 3, 5].map(i => parseInt(h.slice(i, i + 2), 16));
const mix = (a, b, t) => a.map((v, i) => Math.round(v + (b[i] - v) * t));

/** Canvas with "keep the more opaque pixel" painting, like make-charge-circle. */
class Canvas {
  constructor(n) { this.N = n; this.C = (n - 1) / 2; this.P = new Uint8Array(n * n * 4); }
  set(x, y, c, a = 255) {
    x = Math.round(x); y = Math.round(y); const N = this.N; if (x < 0 || y < 0 || x >= N || y >= N) return;
    const i = (y * N + x) * 4; if (a <= this.P[i + 3]) return;
    this.P[i] = c[0]; this.P[i + 1] = c[1]; this.P[i + 2] = c[2]; this.P[i + 3] = a;
  }
  disc(cx, cy, r, c, a = 255) { for (let y = -r; y <= r; y++) for (let x = -r; x <= r; x++) if (x * x + y * y <= r * r + .5) this.set(cx + x, cy + y, c, a); }
  line(x0, y0, x1, y1, t, c, a = 255) {
    const n = Math.ceil(Math.hypot(x1 - x0, y1 - y0) * 2) + 1, r = Math.max(0, Math.round((t - 1) / 2));
    for (let i = 0; i <= n; i++) { const u = i / n; this.disc(Math.round(x0 + (x1 - x0) * u), Math.round(y0 + (y1 - y0) * u), r, c, a); }
  }
  ring(r, t, c, a = 255) {
    const { N, C } = this;
    for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) { const d = Math.hypot(x - C, y - C); if (Math.abs(d - r) <= t / 2) this.set(x, y, c, a); }
  }
  glow(r0, r1, c, amax) { // soft band fading outward from r0 to r1 (r1 < r0 fades inward)
    const { N, C } = this;
    for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
      const d = Math.hypot(x - C, y - C), u = (d - r0) / (r1 - r0); if (u < 0 || u > 1) continue;
      this.set(x, y, c, Math.round(amax * (1 - u) * (1 - u)));
    }
  }
  pt(r, deg) { return [this.C + Math.cos(deg * Math.PI / 180) * r, this.C + Math.sin(deg * Math.PI / 180) * r]; }
  poly(r, k, rot, t, c, a = 255) {
    for (let i = 0; i < k; i++) { const A = this.pt(r, rot + i * 360 / k), B = this.pt(r, rot + (i + 1) * 360 / k); this.line(A[0], A[1], B[0], B[1], t, c, a); }
  }
}

function flatModel(id) {
  const face = { uv: [0, 0, 16, 16], texture: "#0" };
  return { textures: { "0": `pipifacilities:item/fx_${id}`, particle: `pipifacilities:item/fx_${id}` },
    elements: [{ from: [-16, 8, -16], to: [32, 8, 32], shade: false, faces: { up: face, down: face } }] };
}
function beamModel(id) {
  const face = { uv: [0, 0, 16, 16], texture: "#0" };
  return { textures: { "0": `pipifacilities:item/fx_${id}`, particle: `pipifacilities:item/fx_${id}` },
    elements: [
      { from: [0, 8, 8], to: [16, 32, 8], shade: false, faces: { north: face, south: face } },
      { from: [8, 8, 0], to: [8, 32, 16], shade: false, faces: { east: face, west: face } }] };
}
function save(id, canvas, model) {
  fs.writeFileSync(path.join(texDir, `fx_${id}.png`), png(canvas.P, canvas.N));
  fs.writeFileSync(path.join(modDir, `fx_${id}.json`), JSON.stringify(model));
  console.log("fx_" + id);
}

// ---- crimson crescent (검기 난사): thick sweeping arc, white-hot outer edge -> orange -> red -> dark red, ends taper
{
  const N = 64, c = new Canvas(N);
  const ox = 31.5, oy = 31.5, R = 30;    // outer circle centred on the texture = the display origin
  const ix = 31.5, iy = 41.5, r = 29;    // inner circle (offset back so the band tapers to points)
  const band = [[0, hex("#fff6e0")], [.14, hex("#ffd27a")], [.32, hex("#ff8a2a")], [.55, hex("#e8321e")], [.8, hex("#a3141a")], [1, hex("#5c0a12")]];
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    const dO = Math.hypot(x - ox, y - oy), dI = Math.hypot(x - ix, y - iy);
    if (dO > R || dI < r || y > oy + 2) continue;
    const thick = R - (r - (iy - oy));               // nominal thickness at the apex
    const u = Math.min(1, (R - dO) / Math.max(1, thick)); // 0 at the outer edge, 1 toward the inner edge
    let k = 0; while (k < band.length - 2 && u > band[k + 1][0]) k++;
    const t = (u - band[k][0]) / (band[k + 1][0] - band[k][0]);
    const col = mix(band[k][1], band[k + 1][1], Math.max(0, Math.min(1, t)));
    const a = Math.round(255 * (u < .7 ? 1 : 1 - (u - .7) / .3 * .55));
    // stepped (pixel) shading stripes along the arc for the chunky look
    const ang = Math.atan2(y - oy, x - ox), stripe = Math.floor((ang + Math.PI) * 9) % 2;
    c.set(x, y, stripe && u > .2 && u < .9 ? mix(col, [0, 0, 0], .12) : col, a);
  }
  // a few bright embers just outside the edge
  for (const deg of [200, 222, 248, 270, 292, 318, 340]) c.set(ox + Math.cos(deg * Math.PI / 180) * 31.5, oy + Math.sin(deg * Math.PI / 180) * 31.5, hex("#ffe7a8"), 230);
  save("ult_crescent", c, flatModel("ult_crescent"));
}

// ---- golden octagon sigil (유성우 / 심판 mood): double ring, two offset octagons, spokes, rune dots, soft core
{
  const N = 128, c = new Canvas(N);
  const G = hex("#ffc83a"), L = hex("#fff1b8"), D = hex("#c9861a");
  c.glow(60, 40, G, 70);
  c.ring(61, 3, G); c.ring(61, 1, L); c.ring(55, 2, D);
  c.poly(52, 8, 22.5, 3, G); c.poly(52, 8, 0, 2, D, 220);
  c.ring(34, 2, G); c.ring(20, 2, L, 230);
  for (let i = 0; i < 8; i++) { const A = c.pt(20, i * 45), B = c.pt(52, i * 45 + 22.5); c.line(A[0], A[1], B[0], B[1], 1, D, 200); }
  for (let i = 0; i < 16; i++) { const A = c.pt(56, i * 22.5), B = c.pt(61, i * 22.5); c.line(A[0], A[1], B[0], B[1], i % 2 ? 1 : 2, L); }
  for (let i = 0; i < 8; i++) { const p = c.pt(43, i * 45 + 22.5); c.disc(Math.round(p[0]), Math.round(p[1]), 3, L); c.disc(Math.round(p[0]), Math.round(p[1]), 1, G); }
  for (let i = 0; i < 4; i++) { const p = c.pt(61, i * 90); c.disc(Math.round(p[0]), Math.round(p[1]), 4, L); }
  c.glow(0, 18, L, 120); c.disc(64, 64, 4, L);
  save("ult_sigil_gold", c, flatModel("ult_sigil_gold"));
}

// ---- golden halo: thin bright ring with notches, floats above the sigil
{
  const N = 64, c = new Canvas(N);
  const G = hex("#ffc83a"), L = hex("#fffbe6");
  c.glow(28, 31.5, G, 150); c.glow(28, 23, G, 150);
  c.ring(28, 3, G); c.ring(28, 1, L);
  for (let i = 0; i < 8; i++) { const A = c.pt(24, i * 45), B = c.pt(31, i * 45); c.line(A[0], A[1], B[0], B[1], 1, L); }
  save("ult_halo_gold", c, flatModel("ult_halo_gold"));
}

// ---- green web sigil (화살 폭풍): ring + 12 spokes + spiral web strands, target mark at the centre
{
  const N = 128, c = new Canvas(N);
  const G = hex("#79ff57"), L = hex("#e6ffd6"), D = hex("#2fae3a");
  c.glow(60, 44, G, 60);
  c.ring(60, 2, G); c.ring(60, 1, L); c.ring(50, 1, D, 220);
  for (let i = 0; i < 12; i++) { const A = c.pt(6, i * 30), B = c.pt(60, i * 30); c.line(A[0], A[1], B[0], B[1], i % 3 ? 1 : 2, i % 3 ? D : G, 230); }
  for (const r of [16, 27, 39]) for (let i = 0; i < 12; i++) { // web strands between neighbouring spokes, bowed inward
    const A = c.pt(r, i * 30), B = c.pt(r, i * 30 + 30), M = c.pt(r - 4, i * 30 + 15);
    c.line(A[0], A[1], M[0], M[1], 1, G); c.line(M[0], M[1], B[0], B[1], 1, G);
  }
  c.poly(10, 4, 45, 2, L); c.disc(64, 64, 2, L);
  for (let i = 0; i < 4; i++) { const p = c.pt(60, i * 90 + 45); c.disc(Math.round(p[0]), Math.round(p[1]), 3, L); }
  save("ult_sigil_green", c, flatModel("ult_sigil_green"));
}

// ---- green light pillar: bright white-green core fading to transparent edges, faint streaks, softer toward the top
{
  const N = 32, c = new Canvas(N);
  const core = hex("#f2ffe8"), G = hex("#79ff57"), D = hex("#2fae3a");
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    const u = Math.abs(x - 15.5) / 16, top = 1 - y / N * .55; // y = 0 is the top of the quad
    const col = u < .12 ? core : u < .35 ? mix(core, G, (u - .12) / .23) : mix(G, D, (u - .35) / .65);
    const a = Math.round(255 * top * Math.max(0, 1 - Math.pow(u, 1.4) * 1.05));
    const streak = (x === 9 || x === 22) && (y * 7 + x) % 5 < 3;
    c.set(x, y, streak ? L(col) : col, a);
  }
  function L(col) { return mix(col, [255, 255, 255], .5); }
  save("ult_beam_green", c, beamModel("ult_beam_green"));
}
