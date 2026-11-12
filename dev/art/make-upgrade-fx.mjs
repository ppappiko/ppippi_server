// Generates upgrade effect textures: node dev/art/make-upgrade-fx.mjs
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
// Textures are white with varying alpha (tinted at draw time); pixel-art look: alpha quantised to 4 steps.
const q = a => Math.round(Math.max(0, Math.min(1, a)) * 4) / 4;
function make(name, N, f) {
  const px = Buffer.alloc(N * N * 4);
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    const a = q(f(x + .5 - N / 2, y + .5 - N / 2, N));
    const i = (y * N + x) * 4; px[i] = px[i + 1] = px[i + 2] = 255; px[i + 3] = Math.round(a * 255);
  }
  fs.writeFileSync(path.join(out, name), png(N, px));
}
// radial light rays: 12 tapered beams fading outward
make("upgrade_rays.png", 128, (x, y, N) => {
  const d = Math.hypot(x, y) / (N / 2); if (d > 1) return 0;
  const a = Math.atan2(y, x), k = 12, ph = ((a * k / (2 * Math.PI)) % 1 + 1) % 1, w = Math.abs(ph - .5) * 2; // 0 at beam centre
  const width = .55 - .35 * d;
  return w < width ? (1 - w / width) * (1 - d) * 1.2 : 0;
});
// ring: soft double-edged band
make("upgrade_ring.png", 128, (x, y, N) => {
  const d = Math.hypot(x, y) / (N / 2); const t = Math.abs(d - .82);
  return t < .12 ? 1 - t / .12 : 0;
});
// 4-point sparkle
make("upgrade_sparkle.png", 32, (x, y, N) => {
  const ax = Math.abs(x) / (N / 2), ay = Math.abs(y) / (N / 2);
  const cross = Math.max(1 - Math.max(ax, ay) * 1, 0) * (Math.min(ax, ay) < .09 ? 1 : 0);
  const core = Math.max(0, 1 - Math.hypot(ax, ay) / .3);
  return Math.max(cross, core);
});
// crack: jagged fissures from the centre (deterministic)
make("upgrade_crack.png", 64, (x, y, N) => {
  const seg = [];
  const walk = (ang, len, depth) => {
    let px = 0, py = 0, a = ang, s = 0x9e3779b9 ^ Math.round(ang * 1000);
    const rnd = () => { s = (Math.imul(s, 1664525) + 1013904223) >>> 0; return s / 4294967296; };
    for (let i = 0; i < len; i++) {
      const nx = px + Math.cos(a) * 3.2, ny = py + Math.sin(a) * 3.2; seg.push([px, py, nx, ny, 1 - i / len * .4]);
      px = nx; py = ny; a += (rnd() - .5) * 1.1;
      if (depth < 2 && rnd() < .28) walk(a + (rnd() < .5 ? 1 : -1) * (.6 + rnd() * .6), Math.round(len * .5), depth + 1);
    }
  };
  for (let i = 0; i < 6; i++) walk(i * Math.PI / 3 + .3, 9, 0);
  let best = 0;
  for (const [x0, y0, x1, y1, w] of seg) {
    const dx = x1 - x0, dy = y1 - y0, t = Math.max(0, Math.min(1, ((x - x0) * dx + (y - y0) * dy) / (dx * dx + dy * dy)));
    const d = Math.hypot(x - x0 - dx * t, y - y0 - dy * t);
    best = Math.max(best, d < 1.2 ? w : 0);
  }
  return best;
});
console.log("ok");
