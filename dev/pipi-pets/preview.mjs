// Offline software preview renderer for pipi pet .bbmodel files.
//   node dev/pipi-pets/preview.mjs <skin> [animation] [timeSeconds]
//
// Mirrors dev/pipi-facilities/src/PetModels.java for transforms and per-face
// UV mapping so a preview matches what the game draws:
//   * bone transform   = T(origin+pos) * Rz * Ry * Rx * scale * T(-origin)
//   * keyframe rotation is (-x,-y,+z), position is (-x,+y,+z)
//   * rotation is applied Z then Y then X (mulPose order)
//   * cube UV corners are sampled as s,t with the same per-face vertex order
//   * _work/_eat/_rest bones only draw for the matching action, _glow bones and
//     their descendants render full-bright; hitbox* cubes skipped
//
// No npm dependencies: PNG decode/encode use node:zlib only.
import fs from "node:fs";
import path from "node:path";
import zlib from "node:zlib";
import { fileURLToPath } from "node:url";
import { Canvas, encodePNG } from "./lib/bbmodel.mjs";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "../..");
const outDir = path.join(here, "out");

const FACE_NAMES = ["north", "south", "east", "west", "up", "down"];
// Face light values, Minecraft-like directional shading.
const LIGHT_UP = 1.0;
const LIGHT_NS = 0.8;
const LIGHT_EW = 0.6;
const LIGHT_DOWN = 0.5;
const BG = [208, 208, 208];
const FLOOR = [150, 150, 150];

// ---------------------------------------------------------------------------
// PNG decode (8-bit RGB/RGBA, non-interlaced, all 5 filter types)
// ---------------------------------------------------------------------------
function decodePNG(buf) {
  if (buf.readUInt32BE(0) !== 0x89504e47) throw new Error("not a PNG");
  let p = 8;
  let width = 0, height = 0, bitDepth = 0, colorType = 0, interlace = 0;
  const idat = [];
  while (p < buf.length) {
    const len = buf.readUInt32BE(p);
    const type = buf.toString("ascii", p + 4, p + 8);
    const data = buf.subarray(p + 8, p + 8 + len);
    p += 12 + len;
    if (type === "IHDR") {
      width = data.readUInt32BE(0);
      height = data.readUInt32BE(4);
      bitDepth = data[8];
      colorType = data[9];
      interlace = data[12];
    } else if (type === "IDAT") idat.push(data);
    else if (type === "IEND") break;
  }
  const channels = colorType === 6 ? 4 : colorType === 2 ? 3 : 0;
  if (!channels || bitDepth !== 8 || interlace !== 0) {
    throw new Error(`unsupported PNG (bitDepth=${bitDepth} colorType=${colorType} interlace=${interlace})`);
  }
  const raw = zlib.inflateSync(Buffer.concat(idat));
  const stride = width * channels;
  const out = Buffer.alloc(height * stride);
  let pos = 0;
  for (let y = 0; y < height; y++) {
    const filter = raw[pos++];
    const line = raw.subarray(pos, pos + stride);
    pos += stride;
    const prev = y > 0 ? out.subarray((y - 1) * stride, y * stride) : null;
    const cur = out.subarray(y * stride, (y + 1) * stride);
    for (let i = 0; i < stride; i++) {
      const a = i >= channels ? cur[i - channels] : 0;
      const b = prev ? prev[i] : 0;
      const c = prev && i >= channels ? prev[i - channels] : 0;
      let val = line[i];
      switch (filter) {
        case 0: break;
        case 1: val = (val + a) & 255; break;
        case 2: val = (val + b) & 255; break;
        case 3: val = (val + ((a + b) >> 1)) & 255; break;
        case 4: {
          const pp = a + b - c;
          const pa = Math.abs(pp - a), pb = Math.abs(pp - b), pc = Math.abs(pp - c);
          val = (val + (pa <= pb && pa <= pc ? a : pb <= pc ? b : c)) & 255;
          break;
        }
        default: throw new Error(`bad PNG filter ${filter}`);
      }
      cur[i] = val;
    }
  }
  const rgba = new Uint8Array(width * height * 4);
  for (let i = 0; i < width * height; i++) {
    if (channels === 4) {
      rgba[i * 4] = out[i * 4]; rgba[i * 4 + 1] = out[i * 4 + 1];
      rgba[i * 4 + 2] = out[i * 4 + 2]; rgba[i * 4 + 3] = out[i * 4 + 3];
    } else {
      rgba[i * 4] = out[i * 3]; rgba[i * 4 + 1] = out[i * 3 + 1];
      rgba[i * 4 + 2] = out[i * 3 + 2]; rgba[i * 4 + 3] = 255;
    }
  }
  return { width, height, data: rgba };
}

// ---------------------------------------------------------------------------
// 4x4 matrices (row-major), matching Minecraft PoseStack semantics
// ---------------------------------------------------------------------------
const RAD = Math.PI / 180;

function matMul(a, b) {
  const r = new Array(16).fill(0);
  for (let i = 0; i < 4; i++)
    for (let j = 0; j < 4; j++) {
      let s = 0;
      for (let k = 0; k < 4; k++) s += a[i * 4 + k] * b[k * 4 + j];
      r[i * 4 + j] = s;
    }
  return r;
}
const IDENT = [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1];
function translate(x, y, z) { return [1, 0, 0, x, 0, 1, 0, y, 0, 0, 1, z, 0, 0, 0, 1]; }
function scaleM(x, y, z) { return [x, 0, 0, 0, 0, y, 0, 0, 0, 0, z, 0, 0, 0, 0, 1]; }
function rotX(d) { const c = Math.cos(d * RAD), s = Math.sin(d * RAD); return [1, 0, 0, 0, 0, c, -s, 0, 0, s, c, 0, 0, 0, 0, 1]; }
function rotY(d) { const c = Math.cos(d * RAD), s = Math.sin(d * RAD); return [c, 0, s, 0, 0, 1, 0, 0, -s, 0, c, 0, 0, 0, 0, 1]; }
function rotZ(d) { const c = Math.cos(d * RAD), s = Math.sin(d * RAD); return [c, -s, 0, 0, s, c, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1]; }
function xform(m, p) {
  return [
    m[0] * p[0] + m[1] * p[1] + m[2] * p[2] + m[3],
    m[4] * p[0] + m[5] * p[1] + m[6] * p[2] + m[7],
    m[8] * p[0] + m[9] * p[1] + m[10] * p[2] + m[11],
  ];
}
function vecCross(a, b) { return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]; }
function vecDot(a, b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
function vecNorm(a) { const l = Math.hypot(a[0], a[1], a[2]) || 1; return [a[0] / l, a[1] / l, a[2] / l]; }

// ---------------------------------------------------------------------------
// Model loading
// ---------------------------------------------------------------------------
function loadModel(bbmodelPath) {
  const json = JSON.parse(fs.readFileSync(bbmodelPath, "utf8"));
  const resW = json.resolution?.width ?? 64;
  const resH = json.resolution?.height ?? 64;

  const texSrc = json.textures?.[0]?.source;
  if (typeof texSrc !== "string") throw new Error("no embedded texture");
  const tex = decodePNG(Buffer.from(texSrc.slice(texSrc.indexOf(",") + 1), "base64"));

  const cubes = new Map();
  for (const el of json.elements || []) {
    if (el.type && el.type !== "cube") continue;
    const c = {
      isCube: true,
      name: el.name || "",
      from: el.from,
      to: el.to,
      origin: el.origin || [0, 0, 0],
      rot: el.rotation || [0, 0, 0],
      inflate: el.inflate || 0,
      faces: FACE_NAMES.map((nm) => {
        const f = el.faces?.[nm];
        if (!f || !f.uv) return null;
        return {
          u1: f.uv[0], v1: f.uv[1], u2: f.uv[2], v2: f.uv[3],
          rot: f.rotation ? ((Math.round(f.rotation) % 360) + 360) % 360 : 0,
        };
      }),
    };
    cubes.set(el.uuid, c);
  }

  const bones = [];
  const roots = [];
  const parseBone = (o) => {
    const b = {
      isBone: true,
      name: o.name || "",
      uuid: o.uuid || o.name || "",
      origin: o.origin || [0, 0, 0],
      rot: o.rotation || [0, 0, 0],
      visible: o.visibility === undefined ? true : !!o.visibility,
      index: bones.length,
      children: [],
    };
    bones.push(b);
    for (const child of o.children || []) {
      if (child && typeof child === "object") b.children.push(parseBone(child));
      else { const c = cubes.get(child); if (c) b.children.push(c); }
    }
    return b;
  };
  for (const node of json.outliner || []) {
    if (node && typeof node === "object") roots.push(parseBone(node));
    // top-level loose cube uuids are ignored, same as PetModels
  }
  if (roots.length === 0) throw new Error("empty outliner");

  const anims = new Map();
  for (const a of json.animations || []) {
    const tracks = new Map();
    const animators = a.animators || {};
    for (const uuid of Object.keys(animators)) {
      const kfs = animators[uuid].keyframes || [];
      const byCh = new Map();
      for (const kf of kfs) {
        const dp = kf.data_points?.[0];
        if (!dp) continue;
        const ch = kf.channel;
        if (!byCh.has(ch)) byCh.set(ch, []);
        byCh.get(ch).push({ t: kf.time || 0, v: [dp.x || 0, dp.y || 0, dp.z || 0], interp: kf.interpolation || "linear" });
      }
      const chm = new Map();
      for (const [ch, arr] of byCh) { arr.sort((x, y) => x.t - y.t); chm.set(ch, arr); }
      tracks.set(uuid, chm);
    }
    anims.set(a.name, { name: a.name, loop: a.loop || "loop", length: a.length || 0, tracks });
  }

  return { resW, resH, tex, bones, roots, anims };
}

// ---------------------------------------------------------------------------
// Pose evaluation (mirror of PetModels.evalBone / sample)
// ---------------------------------------------------------------------------
function isCurve(s) {
  return s === "catmullrom" || s === "bezier";
}

// True when the track's first key sits at 0 and its last at the animation length, so neighbours wrap.
function wraps(ks, length) {
  return ks.length > 1 && Math.abs(ks[0].t) < 1e-4 && Math.abs(ks[ks.length - 1].t - length) < 1e-4;
}

function neighbourBefore(ks, i, loop, length) {
  if (i > 0) return ks[i - 1];
  if (loop && wraps(ks, length)) return ks[ks.length - 2];
  return ks[i];
}

function neighbourAfter(ks, j, loop, length) {
  if (j < ks.length - 1) return ks[j + 1];
  if (loop && wraps(ks, length)) return ks[1];
  return ks[j];
}

// Uniform Catmull-Rom through p0,a,b,p3 at local parameter s in [0,1].
function cr(p0, a, b, p3, s) {
  const s2 = s * s, s3 = s2 * s;
  return 0.5 * (2 * a + (-p0 + b) * s + (2 * p0 - 5 * a + 4 * b - p3) * s2 + (-p0 + 3 * a - 3 * b + p3) * s3);
}

function sample(ks, t, loop, length) {
  if (t <= ks[0].t) return ks[0].v;
  const last = ks[ks.length - 1];
  if (t >= last.t) return last.v;
  for (let i = 0; i < ks.length - 1; i++) {
    const a = ks[i], b = ks[i + 1];
    if (t >= a.t && t <= b.t) {
      const span = b.t - a.t;
      const f = span <= 1e-6 ? 0 : (t - a.t) / span;
      if (a.interp === "step") return a.v;
      if (isCurve(a.interp) || isCurve(b.interp)) {
        const p0 = neighbourBefore(ks, i, loop, length), p3 = neighbourAfter(ks, i + 1, loop, length);
        return [cr(p0.v[0], a.v[0], b.v[0], p3.v[0], f), cr(p0.v[1], a.v[1], b.v[1], p3.v[1], f), cr(p0.v[2], a.v[2], b.v[2], p3.v[2], f)];
      }
      return [a.v[0] + (b.v[0] - a.v[0]) * f, a.v[1] + (b.v[1] - a.v[1]) * f, a.v[2] + (b.v[2] - a.v[2]) * f];
    }
  }
  return last.v;
}

function evalBone(anim, bone, now, animStart) {
  const r = [0, 0, 0, 0, 0, 0, 1, 1, 1];
  if (!anim) return r;
  const ch = anim.tracks.get(bone.uuid);
  if (!ch) return r;
  let t;
  if (anim.length <= 0) t = 0;
  else if (anim.loop === "loop") t = now % anim.length;
  else t = Math.min(Math.max(now - animStart, 0), anim.length);
  const loop = anim.loop === "loop";
  const rot = ch.get("rotation");
  if (rot) { const v = sample(rot, t, loop, anim.length); r[0] = -v[0]; r[1] = -v[1]; r[2] = v[2]; }
  const pos = ch.get("position");
  if (pos) { const v = sample(pos, t, loop, anim.length); r[3] = -v[0]; r[4] = v[1]; r[5] = v[2]; }
  const sc = ch.get("scale");
  if (sc) { const v = sample(sc, t, loop, anim.length); r[6] = v[0]; r[7] = v[1]; r[8] = v[2]; }
  return r;
}

function evalPose(model, anim, now, animStart) {
  const pose = new Array(model.bones.length);
  for (let i = 0; i < model.bones.length; i++) {
    pose[i] = evalBone(anim, model.bones[i], now, animStart);
  }
  return pose;
}

// ---------------------------------------------------------------------------
// Geometry: collect world-space textured triangles (mirror drawBone/drawCube)
// ---------------------------------------------------------------------------
function faceLight(n) {
  const ax = Math.abs(n[0]), ay = Math.abs(n[1]), az = Math.abs(n[2]);
  if (ay >= ax && ay >= az) return n[1] > 0 ? LIGHT_UP : LIGHT_DOWN;
  if (az >= ax) return LIGHT_NS;
  return LIGHT_EW;
}

function boneMatrix(bone, a) {
  let m = translate(bone.origin[0] + a[3], bone.origin[1] + a[4], bone.origin[2] + a[5]);
  const rx = bone.rot[0] + a[0], ry = bone.rot[1] + a[1], rz = bone.rot[2] + a[2];
  if (rz) m = matMul(m, rotZ(rz));
  if (ry) m = matMul(m, rotY(ry));
  if (rx) m = matMul(m, rotX(rx));
  m = matMul(m, scaleM(a[6], a[7], a[8]));
  m = matMul(m, translate(-bone.origin[0], -bone.origin[1], -bone.origin[2]));
  return m;
}

function cubeRotationMatrix(c) {
  let m = translate(c.origin[0], c.origin[1], c.origin[2]);
  if (c.rot[2]) m = matMul(m, rotZ(c.rot[2]));
  if (c.rot[1]) m = matMul(m, rotY(c.rot[1]));
  if (c.rot[0]) m = matMul(m, rotX(c.rot[0]));
  m = matMul(m, translate(-c.origin[0], -c.origin[1], -c.origin[2]));
  return m;
}

// Per-face vertex corners px,py,pz,s,t copied verbatim from PetModels.drawCube.
function buildTriangles(model, pose, action) {
  const tris = [];
  const push = (p0, p1, p2, u0, u1, u2, v0, v1, v2, light) =>
    tris.push({ p0, p1, p2, u0, u1, u2, v0, v1, v2, light });

  const collectCube = (c, boneM, glow) => {
    if (c.name.startsWith("hitbox")) return;
    let m = boneM;
    if (c.rot[0] || c.rot[1] || c.rot[2]) m = matMul(boneM, cubeRotationMatrix(c));
    const i = c.inflate;
    const x = Math.min(c.from[0], c.to[0]) - i, y = Math.min(c.from[1], c.to[1]) - i, z = Math.min(c.from[2], c.to[2]) - i;
    const X = Math.max(c.from[0], c.to[0]) + i, Y = Math.max(c.from[1], c.to[1]) + i, Z = Math.max(c.from[2], c.to[2]) + i;
    const v = [
      [[x, y, z, 1, 1], [x, Y, z, 1, 0], [X, Y, z, 0, 0], [X, y, z, 0, 1]],
      [[X, y, Z, 1, 1], [X, Y, Z, 1, 0], [x, Y, Z, 0, 0], [x, y, Z, 0, 1]],
      [[X, y, z, 1, 1], [X, Y, z, 1, 0], [X, Y, Z, 0, 0], [X, y, Z, 0, 1]],
      [[x, y, Z, 1, 1], [x, Y, Z, 1, 0], [x, Y, z, 0, 0], [x, y, z, 0, 1]],
      [[x, Y, z, 0, 0], [x, Y, Z, 0, 1], [X, Y, Z, 1, 1], [X, Y, z, 1, 0]],
      [[x, y, Z, 0, 0], [x, y, z, 0, 1], [X, y, z, 1, 1], [X, y, Z, 1, 0]],
    ];
    for (let f = 0; f < 6; f++) {
      const face = c.faces[f];
      if (!face) continue;
      const corners = [];
      for (let k = 0; k < 4; k++) {
        const q = v[f][k];
        let s = q[3], t = q[4];
        const steps = Math.floor(face.rot / 90) % 4;
        for (let r = 0; r < steps; r++) { const ns = t, nt = 1 - s; s = ns; t = nt; }
        corners.push({
          pos: xform(m, [q[0], q[1], q[2]]),
          u: face.u1 + (face.u2 - face.u1) * s,
          v: face.v1 + (face.v2 - face.v1) * t,
        });
      }
      const n = vecCross(
        [corners[1].pos[0] - corners[0].pos[0], corners[1].pos[1] - corners[0].pos[1], corners[1].pos[2] - corners[0].pos[2]],
        [corners[3].pos[0] - corners[0].pos[0], corners[3].pos[1] - corners[0].pos[1], corners[3].pos[2] - corners[0].pos[2]],
      );
      if (Math.hypot(n[0], n[1], n[2]) < 1e-9) continue;
      const light = glow ? 1.0 : faceLight(n);
      const [a, b, c2, d] = corners;
      push(a.pos, b.pos, c2.pos, a.u, b.u, c2.u, a.v, b.v, c2.v, light);
      push(a.pos, c2.pos, d.pos, a.u, c2.u, d.u, a.v, c2.v, d.v, light);
    }
  };

  const collectBone = (bone, parentM, glow) => {
    if (!bone.visible) return;
    if (bone.name.endsWith("_work") && action !== 2) return;
    if (bone.name.endsWith("_eat") && action !== 3) return;
    if (bone.name.endsWith("_rest") && action === 2) return;
    const g = glow || bone.name.endsWith("_glow");
    const m = matMul(parentM, boneMatrix(bone, pose[bone.index]));
    for (const child of bone.children) {
      if (child.isBone) collectBone(child, m, g);
      else collectCube(child, m, g);
    }
  };

  for (const r of model.roots) collectBone(r, IDENT, false);
  return tris;
}

// ---------------------------------------------------------------------------
// Orthographic projection for one panel
// ---------------------------------------------------------------------------
function makeProjection(points, W, H, yawDeg, pitchDeg, fill) {
  const yaw = yawDeg * RAD, pitch = pitchDeg * RAD;
  const cp = Math.cos(pitch), sp = Math.sin(pitch);
  const f = [Math.sin(yaw) * cp, -sp, Math.cos(yaw) * cp];
  const r = vecNorm(vecCross(f, [0, 1, 0]));
  const u = vecNorm(vecCross(r, f));

  let mnx = Infinity, mny = Infinity, mnz = Infinity, mxx = -Infinity, mxy = -Infinity, mxz = -Infinity;
  for (const p of points) {
    if (p[0] < mnx) mnx = p[0]; if (p[1] < mny) mny = p[1]; if (p[2] < mnz) mnz = p[2];
    if (p[0] > mxx) mxx = p[0]; if (p[1] > mxy) mxy = p[1]; if (p[2] > mxz) mxz = p[2];
  }
  const target = [(mnx + mxx) / 2, (mny + mxy) / 2, (mnz + mxz) / 2];

  let minx = Infinity, maxx = -Infinity, miny = Infinity, maxy = -Infinity;
  for (const p of points) {
    const d = [p[0] - target[0], p[1] - target[1], p[2] - target[2]];
    const vx = vecDot(d, r), vy = vecDot(d, u);
    if (vx < minx) minx = vx; if (vx > maxx) maxx = vx;
    if (vy < miny) miny = vy; if (vy > maxy) maxy = vy;
  }
  const bw = Math.max(maxx - minx, 1e-3), bh = Math.max(maxy - miny, 1e-3);
  const sc = fill * Math.min(W / bw, H / bh);
  const cx = (minx + maxx) / 2, cy = (miny + maxy) / 2;

  return {
    target,
    project(p) {
      const d = [p[0] - target[0], p[1] - target[1], p[2] - target[2]];
      const vx = vecDot(d, r), vy = vecDot(d, u), vz = vecDot(d, f);
      return [W / 2 + (vx - cx) * sc, H / 2 - (vy - cy) * sc, vz];
    },
  };
}

function edge(ax, ay, bx, by, px, py) { return (bx - ax) * (py - ay) - (by - ay) * (px - ax); }

function rasterize(canvas, zbuf, tex, proj, tri, W, H) {
  const A = proj.project(tri.p0), B = proj.project(tri.p1), C = proj.project(tri.p2);
  const area = edge(A[0], A[1], B[0], B[1], C[0], C[1]);
  if (Math.abs(area) < 1e-9) return;
  const minX = Math.max(0, Math.floor(Math.min(A[0], B[0], C[0])));
  const maxX = Math.min(W - 1, Math.ceil(Math.max(A[0], B[0], C[0])));
  const minY = Math.max(0, Math.floor(Math.min(A[1], B[1], C[1])));
  const maxY = Math.min(H - 1, Math.ceil(Math.max(A[1], B[1], C[1])));
  const tw = tex.width, th = tex.height;
  for (let y = minY; y <= maxY; y++) {
    for (let x = minX; x <= maxX; x++) {
      const px = x + 0.5, py = y + 0.5;
      const w0 = edge(B[0], B[1], C[0], C[1], px, py);
      const w1 = edge(C[0], C[1], A[0], A[1], px, py);
      const w2 = edge(A[0], A[1], B[0], B[1], px, py);
      if (area > 0) { if (w0 < 0 || w1 < 0 || w2 < 0) continue; }
      else { if (w0 > 0 || w1 > 0 || w2 > 0) continue; }
      const l0 = w0 / area, l1 = w1 / area, l2 = w2 / area;
      const depth = l0 * A[2] + l1 * B[2] + l2 * C[2];
      const idx = y * W + x;
      if (depth >= zbuf[idx]) continue;
      const u = l0 * tri.u0 + l1 * tri.u1 + l2 * tri.u2;
      const v = l0 * tri.v0 + l1 * tri.v1 + l2 * tri.v2;
      let tx = Math.floor(u), ty = Math.floor(v);
      if (tx < 0) tx = 0; else if (tx >= tw) tx = tw - 1;
      if (ty < 0) ty = 0; else if (ty >= th) ty = th - 1;
      const si = (ty * tw + tx) * 4;
      if (tex.data[si + 3] < 128) continue;
      const li = idx * 4;
      const L = tri.light;
      canvas.data[li] = tex.data[si] * L;
      canvas.data[li + 1] = tex.data[si + 1] * L;
      canvas.data[li + 2] = tex.data[si + 2] * L;
      canvas.data[li + 3] = 255;
      zbuf[idx] = depth;
    }
  }
}

function drawFloorLine(canvas, proj, W, H) {
  const fp = proj.project([proj.target[0], 0, proj.target[2]]);
  const fy = Math.round(fp[1]);
  if (fy < 0 || fy >= H) return;
  for (let x = 0; x < W; x++) {
    const i = (fy * W + x) * 4;
    canvas.data[i] = FLOOR[0];
    canvas.data[i + 1] = FLOOR[1];
    canvas.data[i + 2] = FLOOR[2];
    canvas.data[i + 3] = 255;
  }
}

function renderPanel(tris, tex, W, H, yawDeg, pitchDeg, fill, opts = {}) {
  const canvas = new Canvas(W, H);
  canvas.fillRect(0, 0, W, H, opts.background ?? BG);
  if (tris.length === 0) return canvas;
  const pts = [];
  for (const t of tris) { pts.push(t.p0, t.p1, t.p2); }
  const proj = makeProjection(pts, W, H, yawDeg, pitchDeg, fill);
  const zbuf = new Float32Array(W * H).fill(Infinity);
  for (const t of tris) rasterize(canvas, zbuf, tex, proj, t, W, H);
  if (opts.floor !== false) drawFloorLine(canvas, proj, W, H);
  return canvas;
}

// ---------------------------------------------------------------------------
// Output assembly
// ---------------------------------------------------------------------------
function blit(dst, src, dx, dy) {
  for (let y = 0; y < src.height; y++) {
    for (let x = 0; x < src.width; x++) {
      const si = (y * src.width + x) * 4;
      const di = ((dy + y) * dst.width + (dx + x)) * 4;
      dst.data[di] = src.data[si];
      dst.data[di + 1] = src.data[si + 1];
      dst.data[di + 2] = src.data[si + 2];
      dst.data[di + 3] = src.data[si + 3];
    }
  }
}

function main() {
  const args = process.argv.slice(2);
  const iconMode = args.includes("--icon");
  const rest = args.filter((a) => a !== "--icon");
  const skin = rest[0];
  if (!skin) {
    console.error("usage: node dev/pipi-pets/preview.mjs <skin> [animation] [timeSeconds] [--icon]");
    process.exit(2);
  }
  const animName = rest[1];
  const timeArg = rest[2] !== undefined ? parseFloat(rest[2]) : 0;

  const bbmodelPath = path.join(root, "dev/pipi-facilities/assets/pipifacilities/pets", `${skin}.bbmodel`);
  const model = loadModel(bbmodelPath);

  const anim = animName ? model.anims.get(animName) : null;
  if (animName && !anim) throw new Error(`animation "${animName}" not found in ${skin}.bbmodel`);
  const action = animName === "work" ? 2 : animName === "eat" ? 3 : 0;

  // --- icon mode: single 64x64 transparent item icon ------------------------
  if (iconMode) {
    const iconDir = path.join(outDir, "icons");
    fs.mkdirSync(iconDir, { recursive: true });
    const pose = evalPose(model, anim, timeArg, 0);
    const tris = buildTriangles(model, pose, action);
    const icon = renderPanel(tris, model.tex, 64, 64, 35, 20, 0.9, {
      background: [0, 0, 0, 0],
      floor: false,
    });
    const iconPath = path.join(iconDir, `${skin}.png`);
    fs.writeFileSync(iconPath, encodePNG(icon));
    console.log(`wrote ${path.relative(root, iconPath)}`);
    return;
  }

  fs.mkdirSync(outDir, { recursive: true });

  // --- three-view preview ---------------------------------------------------
  const W = 1024, H = 384;
  const preview = new Canvas(W, H);
  preview.fillRect(0, 0, W, H, BG);
  const pose = evalPose(model, anim, timeArg, 0);
  const tris = buildTriangles(model, pose, action);

  const views = [
    [35, 20],   // front three-quarter (pet's front-left)
    [90, 0],    // pure side, pet facing left
    [145, 20],  // back three-quarter
  ];
  for (let i = 0; i < views.length; i++) {
    const x0 = Math.round((i * W) / views.length);
    const x1 = Math.round(((i + 1) * W) / views.length);
    const panel = renderPanel(tris, model.tex, x1 - x0, H, views[i][0], views[i][1], 0.8);
    blit(preview, panel, x0, 0);
  }
  const previewPath = path.join(outDir, `${skin}-preview.png`);
  fs.writeFileSync(previewPath, encodePNG(preview));
  console.log(`wrote ${path.relative(root, previewPath)}`);

  // --- animation strip ------------------------------------------------------
  if (anim) {
    const frames = 6, PW = 256, PH = 256;
    const strip = new Canvas(PW * frames, PH);
    strip.fillRect(0, 0, PW * frames, PH, BG);
    for (let i = 0; i < frames; i++) {
      const t = (anim.length * i) / frames;
      const p = evalPose(model, anim, t, 0);
      const tt = buildTriangles(model, p, action);
      const panel = renderPanel(tt, model.tex, PW, PH, 35, 20, 0.8);
      blit(strip, panel, i * PW, 0);
    }
    const stripPath = path.join(outDir, `${skin}-${animName}-strip.png`);
    fs.writeFileSync(stripPath, encodePNG(strip));
    console.log(`wrote ${path.relative(root, stripPath)}`);
  }
}

main();
