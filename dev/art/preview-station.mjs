// Isometric preview renderer for station models:  node dev/art/preview-station.mjs [station_farm ...]  (default: station_farm)
// Samples each face colour from the model texture at the UV centre, shades per face normal, z-buffered. Output: dev/art/preview/<name>.png
// Also: node dev/art/preview-station.mjs --sample <png>   prints the most common colours of a texture.
import fs from "node:fs";
import path from "node:path";
import zlib from "node:zlib";
import { fileURLToPath } from "node:url";
const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "../pipi-facilities/assets/pipifacilities");
const ORTHO = !!process.env.ORTHO;  // ORTHO=1 -> straight-on view of the model front (-Z), implies FRONT
const FRONT = ORTHO || !!process.env.FRONT;  // FRONT=1 -> view from -x,-z (model front)
const fl = (p, c = 8) => FRONT ? [2 * c - p[0], p[1], 2 * c - p[2]] : p;
const TOP = !!process.env.TOP, PY = TOP ? 1.5 : 0.82, PH = TOP ? 0.6 : 1.0;  // TOP=1 -> higher camera angle, writes <name>_top.png
const outDir = path.join(here, "preview"); fs.mkdirSync(outDir, { recursive: true });

function decodePng(buf) {
  let o = 8, w, h; const idat = [];
  while (o < buf.length) {
    const l = buf.readUInt32BE(o), t = buf.toString("ascii", o + 4, o + 8), d = buf.subarray(o + 8, o + 8 + l);
    if (t === "IHDR") { w = d.readUInt32BE(0); h = d.readUInt32BE(4); }
    if (t === "IDAT") idat.push(d);
    o += 12 + l;
  }
  const raw = zlib.inflateSync(Buffer.concat(idat)), bpp = 4, st = w * bpp, px = Buffer.alloc(h * st);
  for (let y = 0; y < h; y++) {
    const f = raw[y * (st + 1)];
    for (let x = 0; x < st; x++) {
      const v = raw[y * (st + 1) + 1 + x], a = x >= bpp ? px[y * st + x - bpp] : 0, b = y ? px[(y - 1) * st + x] : 0, c = x >= bpp && y ? px[(y - 1) * st + x - bpp] : 0;
      let r = v;
      if (f === 1) r += a; else if (f === 2) r += b; else if (f === 3) r += (a + b) >> 1;
      else if (f === 4) { const p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c); r += pa <= pb && pa <= pc ? a : pb <= pc ? b : c; }
      px[y * st + x] = r & 255;
    }
  }
  return { w, h, px };
}
const crcT = new Uint32Array(256).map((_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const crc = b => { let c = 0xffffffff; for (const x of b) c = crcT[(c ^ x) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (type, data) => { const t = Buffer.from(type), l = Buffer.alloc(4), c = Buffer.alloc(4); l.writeUInt32BE(data.length); c.writeUInt32BE(crc(Buffer.concat([t, data]))); return Buffer.concat([l, t, data, c]); };
function encPng(px, w, h) {
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) px.copy(raw, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4);
  const ih = Buffer.alloc(13); ih.writeUInt32BE(w, 0); ih.writeUInt32BE(h, 4); ih[8] = 8; ih[9] = 6;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk("IHDR", ih), chunk("IDAT", zlib.deflateSync(raw)), chunk("IEND", Buffer.alloc(0))]);
}

// corner order per face + outward normal
const FACES = {
  up: [[0, 1, 0], [[0, 1, 1], [1, 1, 1], [1, 1, 0], [0, 1, 0]]],
  down: [[0, -1, 0], [[0, 0, 0], [1, 0, 0], [1, 0, 1], [0, 0, 1]]],
  south: [[0, 0, 1], [[0, 0, 1], [1, 0, 1], [1, 1, 1], [0, 1, 1]]],
  north: [[0, 0, -1], [[1, 0, 0], [0, 0, 0], [0, 1, 0], [1, 1, 0]]],
  east: [[1, 0, 0], [[1, 0, 1], [1, 0, 0], [1, 1, 0], [1, 1, 1]]],
  west: [[-1, 0, 0], [[0, 0, 0], [0, 0, 1], [0, 1, 1], [0, 1, 0]]],
};
function rot(p, r) {
  if (!r) return p;
  const a = r.angle * Math.PI / 180, c = Math.cos(a), s = Math.sin(a), o = r.origin, q = [p[0] - o[0], p[1] - o[1], p[2] - o[2]];
  let n;
  if (r.axis === "x") n = [q[0], q[1] * c - q[2] * s, q[1] * s + q[2] * c];
  else if (r.axis === "y") n = [q[0] * c + q[2] * s, q[1], -q[0] * s + q[2] * c];
  else n = [q[0] * c - q[1] * s, q[0] * s + q[1] * c, q[2]];
  return [n[0] + o[0], n[1] + o[1], n[2] + o[2]];
}
const rotN = (n, r) => r ? rot(n, { ...r, origin: [0, 0, 0] }) : n;
const unit = v => { const m = Math.hypot(...v); return v.map(a => a / m); };

// Standalone mode: node dev/art/preview-station.mjs --model <model.json> [--out <png>] [--size 800]  -> auto-fit & centred, no ground tile
function render(name, opt = {}) {
  const model = JSON.parse(fs.readFileSync(opt.model || path.join(root, "models/item", name + ".json"), "utf8"));
  const texs = {}; for (const [k, v] of Object.entries(model.textures)) if (k !== "particle") texs["#" + k] = decodePng(fs.readFileSync(path.join(root, "textures/item", v.split("/").pop() + ".png")));
  const K = opt.model ? 1 : 0.8; let S = 30, W = 640, H = 560, cx = W / 2, cy = H * 0.7;
  if (opt.model) {   // fit: project at S=1 to get the bounds, then scale/centre to fill ~88% of a square image
    W = H = opt.size || 800;
    const pr = p => { const x = p[0] - 8, z = p[2] - 8; if (ORTHO) return [x, -p[1]]; return [(x - z) * 0.866, ((x + z) * 0.5 * 0.82 - p[1] * 1.0)]; };
    let x0 = 1e9, x1 = -1e9, y0 = 1e9, y1 = -1e9;
    if (opt.grid) for (const gx of [-16, 32]) for (const gz of [-16, 32]) { const q = pr([gx, 0, gz]); x0 = Math.min(x0, q[0]); x1 = Math.max(x1, q[0]); y0 = Math.min(y0, q[1]); y1 = Math.max(y1, q[1]); }
    for (const e of model.elements) for (let i = 0; i < 8; i++) {
      const q = pr(fl(rot([i & 1 ? e.to[0] : e.from[0], i & 2 ? e.to[1] : e.from[1], i & 4 ? e.to[2] : e.from[2]], e.rotation)));
      x0 = Math.min(x0, q[0]); x1 = Math.max(x1, q[0]); y0 = Math.min(y0, q[1]); y1 = Math.max(y1, q[1]);
    }
    S = Math.min(W * .88 / (x1 - x0), H * .88 / (y1 - y0));
    cx = W / 2 - (x0 + x1) / 2 * S; cy = H / 2 - (y0 + y1) / 2 * S;
  }
  if (!opt.model) { let mn = 0, mx = 16; for (const e of model.elements) { mn = Math.min(mn, e.from[0], e.from[2]); mx = Math.max(mx, e.to[0], e.to[2]); } S = 30 * 16 / Math.max(16, mx - mn); }   // wide models (x -4..20) shrink to fit
  // dimetric projection viewed from +x,+z above (depth grows toward camera)
  const proj = p => {
    const x = p[0] - 8, z = p[2] - 8;
    if (ORTHO) return [cx + x * S * K, cy - p[1] * S * K, p[2]];
    return [cx + (x - z) * 0.866 * S * K, cy + ((x + z) * 0.5 * PY - p[1] * PH) * S * K, (x + z) * 0.6 + p[1] * 0.8];
  };
  const L = unit([-0.35, 0.85, 0.45]), V = ORTHO ? [0, 0, 1] : unit(TOP ? [0.3, 1.2, 0.35] : [0.5, 0.65, 0.6]);
  const img = Buffer.alloc(W * H * 4), zb = new Float32Array(W * H).fill(-1e9);
  for (let i = 0; i < W * H; i++) { img[i * 4] = 0x7a; img[i * 4 + 1] = 0x9c; img[i * 4 + 2] = 0x5a; img[i * 4 + 3] = 255; }
  const quad = (pts, shade, uvs, tex) => {
    for (const tri of [[0, 1, 2], [0, 2, 3]]) {
      const [a, b, c] = tri.map(i => pts[i]);
      const minx = Math.max(0, Math.floor(Math.min(a[0], b[0], c[0]))), maxx = Math.min(W - 1, Math.ceil(Math.max(a[0], b[0], c[0])));
      const miny = Math.max(0, Math.floor(Math.min(a[1], b[1], c[1]))), maxy = Math.min(H - 1, Math.ceil(Math.max(a[1], b[1], c[1])));
      const d = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1]);
      if (Math.abs(d) < 1e-9) continue;
      for (let y = miny; y <= maxy; y++) for (let x = minx; x <= maxx; x++) {
        const l1 = ((b[1] - c[1]) * (x + .5 - c[0]) + (c[0] - b[0]) * (y + .5 - c[1])) / d, l2 = ((c[1] - a[1]) * (x + .5 - c[0]) + (a[0] - c[0]) * (y + .5 - c[1])) / d, l3 = 1 - l1 - l2;
        if (l1 < -1e-4 || l2 < -1e-4 || l3 < -1e-4) continue;
        const z = l1 * a[2] + l2 * b[2] + l3 * c[2], k = y * W + x;
        if (z > zb[k]) {
          let col = shade, skip = false;
          if (tex) {
            const ua = uvs[tri[0]], ub = uvs[tri[1]], uc = uvs[tri[2]], u = l1 * ua[0] + l2 * ub[0] + l3 * uc[0], v = l1 * ua[1] + l2 * ub[1] + l3 * uc[1];
            const tx = Math.max(0, Math.min(tex.w - 1, Math.floor(u / 16 * tex.w))), ty = Math.max(0, Math.min(tex.h - 1, Math.floor(v / 16 * tex.h))), o = (ty * tex.w + tx) * 4;
            if (tex.px[o + 3] < 128) skip = true;
            col = [0, 1, 2].map(i => Math.min(255, Math.round(tex.px[o + i] * shade[3] * 1.1)));
          }
          if (skip) continue;
          zb[k] = z; img[k * 4] = col[0]; img[k * 4 + 1] = col[1]; img[k * 4 + 2] = col[2];
        }
      }
    }
  };
  // faint ground tile under the 16x16 footprint (drawn at lowest depth)
  if (!opt.model) quad([[-4, 0, 16], [20, 0, 16], [20, 0, 0], [-4, 0, 0]].map(p => { const q = proj([p[0], -0.01, p[2]]); q[2] = -500; return q; }), [0x86, 0xa8, 0x62, 1]);
  if (opt.grid) {   // light 3x3 ground grid over x/z -16..32 (one line per block = 16 px)
    const gq = (a, b, c, d, col, zz = -500) => quad([a, b, c, d].map(p => { const q = proj([p[0], -0.01, p[1]]); q[2] = zz; return q; }), col, null, null);
    gq([-16, -16], [32, -16], [32, 32], [-16, 32], [0x74, 0x96, 0x52, 1]);
    for (let i = -16; i <= 32; i += 16) { const w = 0.3; for (const [a, b, c, d] of [[[i - w, -16], [i + w, -16], [i + w, 32], [i - w, 32]], [[-16, i - w], [32, i - w], [32, i + w], [-16, i + w]]]) gq(a, b, c, d, [0xd8, 0xe8, 0xc0, 1], -400); }
  }
  for (const e of model.elements) {
    const f = e.from, t = e.to;
    for (const [fn, fd] of Object.entries(e.faces)) {
      const [n0, cs] = FACES[fn], n = fl(rotN(n0, e.rotation), 0);
      if (n[0] * V[0] + n[1] * V[1] + n[2] * V[2] <= 0) continue;
      const sh = .5 + .5 * Math.max(0, n[0] * L[0] + n[1] * L[1] + n[2] * L[2]), [u0, v0, u1, v1] = fd.uv;
      const uvs = [[u0, v1], [u1, v1], [u1, v0], [u0, v0]];
      const pts = cs.map(c => proj(fl(rot([c[0] ? t[0] : f[0], c[1] ? t[1] : f[1], c[2] ? t[2] : f[2]], e.rotation))));
      quad(pts, [0, 0, 0, sh], uvs, texs[fd.texture]);
    }
  }
  const outFile = opt.out || path.join(outDir, name + (TOP ? "_top" : "") + ".png");
  fs.writeFileSync(outFile, encPng(img, W, H));
  console.log("wrote", outFile);
}
if (process.argv[2] === "--sample") {
  const t = decodePng(fs.readFileSync(process.argv[3])), seen = {};
  for (let i = 0; i < t.w * t.h; i++) { if (!t.px[i * 4 + 3]) continue; const h = [...t.px.subarray(i * 4, i * 4 + 3)].map(v => v.toString(16).padStart(2, "0")).join(""); seen[h] = (seen[h] || 0) + 1; }
  console.log(Object.entries(seen).sort((a, b) => b[1] - a[1]).slice(0, 14));
} else if (process.argv[2] === "--model") {
  const a = process.argv, g = k => a.includes(k) ? a[a.indexOf(k) + 1] : undefined;
  render("model", { model: path.resolve(g("--model")), out: g("--out") && path.resolve(g("--out")), size: +g("--size") || 800, grid: a.includes("--grid") });
} else for (const n of (process.argv.length > 2 ? process.argv.slice(2) : ["station_farm"])) render(n);
