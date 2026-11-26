// Pet (.bbmodel) preview renderer — mirrors PetModels.java (bone order, Blockbench sign conventions, catmull-rom keys).
//   node dev/art/preview-pets.mjs                      -> dev/art/preview/pets/lineup.png (+ <skin>.png, all idle at their in-game size)
//   node dev/art/preview-pets.mjs fox walk 8           -> dev/art/preview/pets/fox_walk.png (8 frames over one loop)
//   YAW=-90 node dev/art/preview-pets.mjs fox walk 8   -> side view
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { readPng, writePng } from "./class-icons/pngio.mjs";
import { SIZE } from "./pet-sizes.mjs";

const here = path.dirname(fileURLToPath(import.meta.url));
const petDir = process.env.PET_DIR ? path.resolve(process.env.PET_DIR) : path.resolve(here, "../pipi-facilities/assets/pipifacilities/pets");
const outDir = process.env.OUT_DIR ? path.resolve(process.env.OUT_DIR) : path.join(here, "preview/pets"); fs.mkdirSync(outDir, { recursive: true });
const YAW = +(process.env.YAW ?? -35), PITCH = +(process.env.PITCH ?? 22);

// ---------------------------------------------------------------- model
const num = v => (typeof v === "number" ? v : parseFloat(v) || 0);
function load(skin) {
  const m = JSON.parse(fs.readFileSync(path.join(petDir, skin + ".bbmodel"), "utf8"));
  const tex = readPng(Buffer.from(m.textures[0].source.split(",")[1], "base64"));
  const cubes = new Map(m.elements.map(e => [e.uuid, e]));
  const bones = [];
  const walk = (o, parent) => o.map(c => {
    if (typeof c === "string") return cubes.get(c);
    const b = { name: c.name, uuid: c.uuid, origin: c.origin.map(num), rot: (c.rotation || [0, 0, 0]).map(num), visible: c.visibility !== false };
    bones.push(b); b.children = walk(c.children || [], b); return b;
  }).filter(Boolean);
  const roots = walk(m.outliner, null).filter(c => c.children);
  const anims = {};
  for (const a of m.animations || []) {
    const tracks = {};
    for (const [id, an] of Object.entries(a.animators || {})) {
      const ch = {};
      for (const k of an.keyframes || []) (ch[k.channel] ||= []).push({ t: num(k.time), v: [num(k.data_points[0].x), num(k.data_points[0].y), num(k.data_points[0].z)], i: k.interpolation || "linear" });
      for (const c in ch) ch[c].sort((x, y) => x.t - y.t);
      tracks[id] = ch;
    }
    anims[a.name] = { name: a.name, loop: a.loop || "loop", length: num(a.length), tracks };
  }
  return { skin, tex, roots, bones, anims, res: m.resolution || { width: 64, height: 64 } };
}

const cr = (p0, a, b, p3, s) => { const s2 = s * s, s3 = s2 * s; return 0.5 * (2 * a + (-p0 + b) * s + (2 * p0 - 5 * a + 4 * b - p3) * s2 + (-p0 + 3 * a - 3 * b + p3) * s3); };
function sample(ks, t, loop, len) {
  if (t <= ks[0].t) return ks[0].v;
  const last = ks[ks.length - 1]; if (t >= last.t) return last.v;
  const wrap = ks.length > 1 && Math.abs(ks[0].t) < 1e-4 && Math.abs(last.t - len) < 1e-4;
  for (let i = 0; i < ks.length - 1; i++) {
    const a = ks[i], b = ks[i + 1];
    if (t < a.t || t > b.t) continue;
    const f = b.t - a.t <= 1e-6 ? 0 : (t - a.t) / (b.t - a.t);
    if (a.i === "step") return a.v;
    if (["catmullrom", "bezier"].includes(a.i) || ["catmullrom", "bezier"].includes(b.i)) {
      const p0 = i > 0 ? ks[i - 1] : loop && wrap ? ks[ks.length - 2] : a, p3 = i + 1 < ks.length - 1 ? ks[i + 2] : loop && wrap ? ks[1] : b;
      return [0, 1, 2].map(k => cr(p0.v[k], a.v[k], b.v[k], p3.v[k], f));
    }
    return [0, 1, 2].map(k => a.v[k] + (b.v[k] - a.v[k]) * f);
  }
  return last.v;
}
function evalBone(anim, b, t) {
  const r = [0, 0, 0, 0, 0, 0, 1, 1, 1]; const ch = anim?.tracks[b.uuid]; if (!ch) return r;
  const loop = anim.loop === "loop", tt = anim.length <= 0 ? 0 : loop ? t % anim.length : Math.min(t, anim.length);
  if (ch.rotation) { const v = sample(ch.rotation, tt, loop, anim.length); r[0] = -v[0]; r[1] = -v[1]; r[2] = v[2]; }
  if (ch.position) { const v = sample(ch.position, tt, loop, anim.length); r[3] = -v[0]; r[4] = v[1]; r[5] = v[2]; }
  if (ch.scale) { const v = sample(ch.scale, tt, loop, anim.length); r[6] = v[0]; r[7] = v[1]; r[8] = v[2]; }
  return r;
}

// ---------------------------------------------------------------- 4x4 matrices (column vectors, row-major arrays)
const I = () => [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1];
const mul = (a, b) => { const r = new Array(16).fill(0); for (let i = 0; i < 4; i++) for (let j = 0; j < 4; j++) for (let k = 0; k < 4; k++) r[i * 4 + j] += a[i * 4 + k] * b[k * 4 + j]; return r; };
const T = (x, y, z) => [1, 0, 0, x, 0, 1, 0, y, 0, 0, 1, z, 0, 0, 0, 1];
const S = (x, y, z) => [x, 0, 0, 0, 0, y, 0, 0, 0, 0, z, 0, 0, 0, 0, 1];
const R = (ax, deg) => { const a = deg * Math.PI / 180, c = Math.cos(a), s = Math.sin(a);
  return ax === "x" ? [1, 0, 0, 0, 0, c, -s, 0, 0, s, c, 0, 0, 0, 0, 1] : ax === "y" ? [c, 0, s, 0, 0, 1, 0, 0, -s, 0, c, 0, 0, 0, 0, 1] : [c, -s, 0, 0, s, c, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1]; };
const rot = (m, x, y, z) => { if (z) m = mul(m, R("z", z)); if (y) m = mul(m, R("y", y)); if (x) m = mul(m, R("x", x)); return m; };
const ap = (m, p) => [0, 1, 2].map(i => m[i * 4] * p[0] + m[i * 4 + 1] * p[1] + m[i * 4 + 2] * p[2] + m[i * 4 + 3]);
const apN = (m, n) => [0, 1, 2].map(i => m[i * 4] * n[0] + m[i * 4 + 1] * n[1] + m[i * 4 + 2] * n[2]);

// ---------------------------------------------------------------- raster
function canvas(w, h, bg = [0, 0, 0, 0]) {
  w = Math.round(w); h = Math.round(h);
  const px = Buffer.alloc(w * h * 4), zb = new Float32Array(w * h).fill(1e9);
  for (let i = 0; i < w * h; i++) px.set(bg, i * 4);
  return { w, h, px, zb };
}
function fillQuad(cv, q, z, col) {
  let x0 = Math.floor(Math.min(...q.map(p => p[0]))), x1 = Math.ceil(Math.max(...q.map(p => p[0])));
  let y0 = Math.floor(Math.min(...q.map(p => p[1]))), y1 = Math.ceil(Math.max(...q.map(p => p[1])));
  x0 = Math.max(0, x0); y0 = Math.max(0, y0); x1 = Math.min(cv.w - 1, x1); y1 = Math.min(cv.h - 1, y1);
  const edge = (a, b, x, y) => (b[0] - a[0]) * (y - a[1]) - (b[1] - a[1]) * (x - a[0]);
  for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
    const cx = x + .5, cy = y + .5; let pos = 0, neg = 0;
    for (let k = 0; k < 4; k++) { const e = edge(q[k], q[(k + 1) % 4], cx, cy); if (e > 1e-9) pos++; else if (e < -1e-9) neg++; }
    if (pos && neg) continue;
    const i = y * cv.w + x; if (z >= cv.zb[i]) continue;
    cv.zb[i] = z; cv.px[i * 4] = col[0]; cv.px[i * 4 + 1] = col[1]; cv.px[i * 4 + 2] = col[2]; cv.px[i * 4 + 3] = 255;
  }
}
const FACES = { north: 0, south: 1, east: 2, west: 3, up: 4, down: 5 };
const NRM = [[0, 0, -1], [0, 0, 1], [1, 0, 0], [-1, 0, 0], [0, 1, 0], [0, -1, 0]];
const LIGHT = (() => { const l = [-.45, .8, -.4], n = Math.hypot(...l); return l.map(v => v / n); })();

function drawModel(cv, model, animName, t, view, action) {
  const anim = model.anims[animName] || model.anims.idle;
  const pose = new Map(model.bones.map(b => [b, evalBone(anim, b, t)]));
  const draw = (b, m) => {
    if (!b.visible) return;
    if (b.name.endsWith("_work") && action !== 2) return;
    if (b.name.endsWith("_eat") && action !== 3) return;
    if (b.name.endsWith("_rest") && action === 2) return;
    const a = pose.get(b);
    let bm = mul(m, T(b.origin[0] + a[3], b.origin[1] + a[4], b.origin[2] + a[5]));
    bm = rot(bm, b.rot[0] + a[0], b.rot[1] + a[1], b.rot[2] + a[2]);
    bm = mul(mul(bm, S(a[6], a[7], a[8])), T(-b.origin[0], -b.origin[1], -b.origin[2]));
    for (const c of b.children) c.children ? draw(c, bm) : cube(c, bm);
  };
  const cube = (c, m) => {
    if (c.name.startsWith("hitbox")) return;
    const r = (c.rotation || [0, 0, 0]).map(num), o = (c.origin || [0, 0, 0]).map(num);
    if (r[0] || r[1] || r[2]) m = mul(rot(mul(m, T(...o)), ...r), T(-o[0], -o[1], -o[2]));
    const inf = num(c.inflate || 0);
    const [x, y, z] = [0, 1, 2].map(i => Math.min(num(c.from[i]), num(c.to[i])) - inf), [X, Y, Z] = [0, 1, 2].map(i => Math.max(num(c.from[i]), num(c.to[i])) + inf);
    const V = [
      [[x, y, z, 1, 1], [x, Y, z, 1, 0], [X, Y, z, 0, 0], [X, y, z, 0, 1]],
      [[X, y, Z, 1, 1], [X, Y, Z, 1, 0], [x, Y, Z, 0, 0], [x, y, Z, 0, 1]],
      [[X, y, z, 1, 1], [X, Y, z, 1, 0], [X, Y, Z, 0, 0], [X, y, Z, 0, 1]],
      [[x, y, Z, 1, 1], [x, Y, Z, 1, 0], [x, Y, z, 0, 0], [x, y, z, 0, 1]],
      [[x, Y, z, 0, 0], [x, Y, Z, 0, 1], [X, Y, Z, 1, 1], [X, Y, z, 1, 0]],
      [[x, y, Z, 0, 0], [x, y, z, 0, 1], [X, y, z, 1, 1], [X, y, Z, 1, 0]]];
    const full = mul(view, m);
    for (const [fname, fi] of Object.entries(FACES)) {
      const face = c.faces?.[fname]; if (!face || !face.uv || face.texture === null || face.texture === undefined) continue;
      const [u1, v1, u2, v2] = face.uv.map(num), frot = ((Math.round(num(face.rotation || 0)) % 360) + 360) % 360 / 90;
      const corner = {}; for (const q of V[fi]) corner[q[3] + "" + q[4]] = q;
      const P = (s, t) => { const a = corner["00"], b = corner["10"], cc = corner["01"], d = corner["11"];
        return [0, 1, 2].map(k => (a[k] * (1 - s) + b[k] * s) * (1 - t) + (cc[k] * (1 - s) + d[k] * s) * t); };
      const n = apN(full, NRM[fi]), nl = Math.hypot(...n) || 1;
      const wn = apN(m, NRM[fi]), wl = Math.hypot(...wn) || 1;
      const shade = .58 + .42 * Math.max(0, (wn[0] * LIGHT[0] + wn[1] * LIGHT[1] + wn[2] * LIGHT[2]) / wl);
      const nu = Math.max(1, Math.ceil(Math.abs(u2 - u1) * 4 - 1e-6)), nv = Math.max(1, Math.ceil(Math.abs(v2 - v1) * 4 - 1e-6)), N = Math.max(nu, nv); // 4 samples per texel: fractional UVs (rounded pets) stay exact
      const scr = p => { const w = ap(full, p); return [w[0], w[1], w[2]]; };
      void nl; void n;
      for (let i = 0; i < N; i++) for (let j = 0; j < N; j++) {
        const s0 = i / N, s1 = (i + 1) / N, t0 = j / N, t1 = (j + 1) / N, sc = (s0 + s1) / 2, tc = (t0 + t1) / 2;
        let ts = sc, tt = tc; for (let k = 0; k < frot; k++) { const ns = tt, nt = 1 - ts; ts = ns; tt = nt; }
        const tu = Math.floor((u1 + (u2 - u1) * ts) / model.res.width * model.tex.w), tv = Math.floor((v1 + (v2 - v1) * tt) / model.res.height * model.tex.h);
        const ti = (Math.min(model.tex.h - 1, Math.max(0, tv)) * model.tex.w + Math.min(model.tex.w - 1, Math.max(0, tu))) * 4;
        if (model.tex.px[ti + 3] < 128) continue;
        const col = [0, 1, 2].map(k => Math.min(255, model.tex.px[ti + k] * shade));
        const q = [P(s0, t0), P(s1, t0), P(s1, t1), P(s0, t1)].map(scr);
        const zc = (q[0][2] + q[1][2] + q[2][2] + q[3][2]) / 4;
        fillQuad(cv, q.map(p => [p[0], p[1]]), zc, col);
      }
    }
  };
  for (const b of model.roots) draw(b, I());
}

// view: model units -> screen px. Model front faces -Z; camera sits in front (−Z), looking +Z.
function viewMatrix(px, cx, cy, size = 1) {
  let v = mul(T(cx, cy, 0), S(px * size, -px * size, px * size));
  v = mul(v, R("x", -PITCH)); v = mul(v, R("y", YAW));
  return v;
}
function crop(cv, pad = 2) {
  let x0 = cv.w, y0 = cv.h, x1 = -1, y1 = -1;
  for (let y = 0; y < cv.h; y++) for (let x = 0; x < cv.w; x++) if (cv.zb[y * cv.w + x] < 1e9) { x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y); }
  return [Math.max(0, x0 - pad), Math.max(0, y0 - pad), Math.min(cv.w - 1, x1 + pad), Math.min(cv.h - 1, y1 + pad)];
}
function save(cv, file, box) {
  const [x0, y0, x1, y1] = box || [0, 0, cv.w - 1, cv.h - 1], w = x1 - x0 + 1, h = y1 - y0 + 1, px = Buffer.alloc(w * h * 4);
  for (let y = 0; y < h; y++) cv.px.copy(px, y * w * 4, ((y + y0) * cv.w + x0) * 4, ((y + y0) * cv.w + x1 + 1) * 4);
  fs.writeFileSync(path.join(outDir, file), writePng(px, w, h));
}
const ACTION = { idle: 0, walk: 1, work: 2, eat: 3, hurt: 0 };
const skins = fs.readdirSync(petDir).filter(f => f.endsWith(".bbmodel")).map(f => f.slice(0, -8));

const [skinArg, animArg = "idle", framesArg = "6"] = process.argv.slice(2);
if (skinArg) {
  const model = load(skinArg), anim = model.anims[animArg], frames = +framesArg, PX = 8, cell = +(process.env.CELL ?? 46) * PX * (SIZE[skinArg] ?? 1);
  const cv = canvas(Math.ceil(cell * frames), Math.ceil(cell), [236, 238, 242, 255]);
  for (let f = 0; f < frames; f++) {
    const t = anim ? anim.length * f / frames : 0;
    drawModel(cv, model, animArg, t, viewMatrix(PX, cell * (f + .5), cell * .72, SIZE[skinArg] ?? 1), ACTION[animArg] ?? 0);
  }
  save(cv, `${skinArg}_${animArg}.png`);
  console.log(`${skinArg}_${animArg}.png`, anim ? anim.length + "s" : "(no anim)");
} else {
  // lineup: every pet idle at its in-game size, next to a player-height bar (1.8 blocks = 28.8 units)
  const PX = 6, order = skins.sort((a, b) => (SIZE[a] ?? 1) - (SIZE[b] ?? 1));
  const per = order.map(s => { const m = load(s); const cv = canvas(900, 700); drawModel(cv, m, "idle", 0, viewMatrix(PX, 450, 560, SIZE[s] ?? 1), 0); return { s, cv, box: crop(cv, 6) }; });
  const W = per.reduce((a, p) => a + p.box[2] - p.box[0] + 1, 0) + 40, H = Math.round(28.8 * PX + 40), base = H - 16;
  const out = canvas(W, H, [236, 238, 242, 255]);
  for (let x = 4; x < 12; x++) for (let y = base - Math.round(28.8 * PX); y < base; y++) out.px.set([90, 110, 160, 255], (y * W + x) * 4);
  let x = 24;
  for (const p of per) {
    const [x0, y0, x1, y1] = p.box, dy = base - (560 - y0);
    for (let yy = y0; yy <= y1; yy++) for (let xx = x0; xx <= x1; xx++) { const si = (yy * 900 + xx) * 4; if (p.cv.zb[yy * 900 + xx] >= 1e9) continue; const ty = dy + (yy - y0), tx = x + xx - x0; if (ty < 0 || ty >= H) continue; p.cv.px.copy(out.px, (ty * W + tx) * 4, si, si + 4); }
    for (let xx = x; xx <= x + x1 - x0; xx++) out.px.set([180, 184, 190, 255], (base * W + xx) * 4);
    save(p.cv, p.s + ".png", p.box);
    x += x1 - x0 + 1;
  }
  save(out, "lineup.png");
  console.log("lineup (small -> big):", order.map(s => s + "=" + (SIZE[s] ?? 1)).join(" "));
}
