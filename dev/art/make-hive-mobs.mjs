// 비탄의 성채 레이드 군체 몬스터 5종 -> pipi-facilities/assets/pipifacilities/pets/hive_<kind>.bbmodel (geometry + texture + animations)
//   node dev/art/make-hive-mobs.mjs            (all)     node dev/art/make-hive-mobs.mjs knight
// Mood reference: an undead chitin swarm (user-supplied reference sheets). Shapes and names are our own:
//   runner  굶주린 구울  eyeless hunched ghoul, pale tarnished-silver hide, very long clawed arms, digitigrade legs
//   bomber  부패 구울    the same ghoul, bloated and studded with glowing green-yellow pustules; shambles up and bursts
//   acolyte 군체 사도    pale cracked-bone limbs under dark purple chitin, flared hood helm, tattered tabard,
//                        a curved dark gun in its right hand that spits fire
//   knight  통곡 기사    huge, covered in dark purple chitin, spiked crown, forked black siege cannon with a blue core
//   wizard  군체 주술사  floating; fan-shaped crest, layered mauve armour, flared cream robe strips, black cape, glowing hands
//   keeper  문지기      short, broad dwarf-like shadow-dark brute built like the lord: the lord crowned mask, beard, a crescent axe in each fist
// Facing -z (north). 16 units = 1 block. Bones ending in _glow are drawn full-bright; _work bones only while attacking.
// Animation rotations are Blockbench keyframes (PetModels negates x/y): limb(f) swings a hanging limb forward by f degrees,
// lean(f) tips an upright part (torso, head) forward by f degrees. Cube rotation (extra.rotation) is applied raw:
// +x swings the part below its pivot forward, +z swings it toward +x.
import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import { fileURLToPath } from "node:url";
import { Model, hash, shade, mix, flat } from "./lib-petmodel.mjs";
import { hiveCrownMask } from "./lib-hivemask.mjs";

const here = path.dirname(fileURLToPath(import.meta.url));
const outDir = path.resolve(here, "../pipi-facilities/assets/pipifacilities/pets");

// ---------------------------------------------------------------- palette
const THR = "#817b6b", THR_L = "#9f9a8c", THR_D = "#595347", THR_DD = "#322d25",            // ghoul hide (tarnished silver)
  BW = "#9c9688", BW_D = "#7a7262", BW_C = "#443e34",                                        // pale cracked bone
  PU = "#352a36", PU_L = "#4e3e4d", PU_D = "#1e171e", PU_HI = "#645261",                     // purple-black chitin
  MOSS = "#49532d", HOOF = "#5a4e42", HOOF_D = "#372e28",
  CLOTH = "#504f43", CLOTH_D = "#35352c",                                                    // acolyte tabard
  GUN = "#25252a", GUN_L = "#3d3c42", GUN_HI = "#63636b", GUN_D = "#141317",                 // dark gunmetal
  WA = "#847575", WA_L = "#9a8e8b", WA_D = "#5b4b51", WG = "#4c5637",                         // wizard armour
  RB = "#9c9175", RB_D = "#796b4e", CAPE = "#151318", CAPE_L = "#221e26",                     // wizard robe / cape
  EYE = "#b8ff5c", EYE_CORE = "#f2ffd0", PUS = "#d4ff52", PUS_D = "#92c22a",
  FIRE = "#ff8a2a", FIRE_L = "#ffd36a", BLUE = "#46b4ff", BLUE_L = "#c4ecff", ORB = "#cfe6ff", ORB_CORE = "#ffffff";

const sideShade = (c, f) => f === "up" ? shade(c, .08) : f === "east" || f === "west" ? shade(c, -.07) : c;
const n3 = (x, y, z, k = 2, s = 0) => hash(Math.floor(x * k) + s, Math.floor(y * k), Math.floor(z * k));
/** tarnished metallic hide: mottled, with dark vertical runs and bright glints */
const hide = (base = THR, lo = THR_D, hi = THR_L) => (x, y, z, f) => {
  if (f === "down") return shade(lo, -.15);
  const c = sideShade(base, f), n = n3(x, y, z, 2.5);
  if (n < .03) return mix(c, lo, .6);
  if (n > .95) return hi;
  if (n3(x, 0, z, 1.4, 7) < .16 && n3(x, y, z, 1.2, 3) < .5) return mix(c, lo, .35);
  return n3(x, y, z, .8, 5) < .3 ? mix(c, lo, .15) : n3(x, y, z, .8, 6) > .75 ? mix(c, hi, .25) : c;
};
/** pale bone with dark hairline cracks */
const bone = (base = BW, rim = BW_D) => (x, y, z, f) => {
  if (f === "down") return shade(rim, -.15);
  const c = sideShade(base, f);
  if (hash(Math.floor(x * 1.2 + y * .6), Math.floor(z * 1.2 - y * .5), 9) < .05) return mix(c, BW_C, .45);
  const n = n3(x, y, z, 1.5);
  return n < .2 ? mix(c, rim, .4) : n > .85 ? shade(c, .04) : c;
};
/** chitin armour plate: lit top rim, dark lower rim, mottle, optional moss patches */
const shell = (base = PU, hi = PU_L, lo = PU_D, moss = 0) => (x, y, z, f, i, j, w, h) => {
  if (f === "down") return shade(lo, -.1);
  if (f === "up") return mix(base, hi, .6);
  let c = sideShade(base, f);
  if (j === 0 && h > 2) c = mix(c, hi, .45);
  else if (j === h - 1 && h > 3) c = mix(c, lo, .4);
  if (moss && n3(x, y, z, .6, 11) < moss) c = mix(c, MOSS, .3);
  const n = n3(x, y, z, 1.4);
  return n < .15 ? mix(c, lo, .25) : n > .88 ? mix(c, hi, .3) : c;
};
/** shell with a raised centre ridge on the north face */
const ridged = (base, hi, lo, ridge) => (x, y, z, f, i, j, w, h) => f === "north" && Math.abs(i - (w - 1) / 2) < .6 ? ridge : shell(base, hi, lo, .15)(x, y, z, f, i, j, w, h);
/** horizontal ribs (every other row darker) */
const ribs = (base, dark) => (x, y, z, f, i, j) => (f === "north" || f === "south" || f === "east" || f === "west") && j % 2 === 1 ? mix(sideShade(base, f), dark, .6) : sideShade(base, f);
/** gunmetal: dark body, bright edge highlights, a few scratches */
const metal = (base = GUN, hi = GUN_L) => (x, y, z, f, i, j, w, h) => {
  if (f === "down") return GUN_D;
  const c = sideShade(base, f);
  if (j === 0) return mix(c, hi, .6);
  if (i === 0 || i === w - 1 || j === h - 1) return mix(c, hi, .3);
  const n = n3(x, y, z, 1.5);
  return n < .08 ? mix(c, GUN_HI, .4) : n > .9 ? mix(c, GUN_D, .4) : c;
};
const glow = (c, core) => (x, y, z, f, i, j, w, h) => core && i > 0 && j > 0 && i < w - 1 && j < h - 1 ? core : c;
/** cloth strip whose bottom rows fray into a darker colour */
const cloth = (base, dark, k = 0) => (x, y, z, f, i, j, w, h) => j >= h - 2 && (i + k + j) % 2 ? dark : n3(x, y, z, 1.2, k) < .14 ? mix(base, dark, .5) : sideShade(base, f);

// ---------------------------------------------------------------- animation helpers
const TAU = Math.PI * 2;
const sin = (p, k = 1, ph = 0) => Math.sin(TAU * (p * k + ph));
const limb = (f, yaw = 0, roll = 0) => [-f, -yaw, roll];
const lean = (f, yaw = 0, roll = 0) => [-f, -yaw, roll];
const A = (name, length, bones, opts = {}) => ({ name, length, loop: opts.loop || "loop", bones, n: opts.n });
const r3 = v => Math.round(v * 1000) / 1000;
function build(spec, ids, skin) {
  const out = { uuid: crypto.randomUUID(), name: spec.name, loop: spec.loop, override: false, length: spec.length, snapping: 20, selected: false, anim_time_update: "", blend_weight: "", start_delay: "", loop_delay: "", animators: {} };
  for (const [b, ch] of Object.entries(spec.bones)) {
    const id = ids[b]; if (!id) throw new Error(`${skin}/${spec.name}: no bone ${b}`);
    const keys = [], n = ch.n ?? spec.n ?? Math.max(4, Math.round(spec.length * 12));
    for (const [k, channel] of [["rot", "rotation"], ["pos", "position"], ["scl", "scale"]]) {
      if (!ch[k]) continue;
      for (let i = 0; i <= n; i++) { const p = i / n, v = ch[k](p); keys.push({ channel, data_points: [{ x: r3(v[0]), y: r3(v[1]), z: r3(v[2]) }], uuid: crypto.randomUUID(), time: r3(p * spec.length), color: -1, interpolation: "catmullrom" }); }
    }
    out.animators[id] = { name: b, type: "bone", keyframes: keys };
  }
  return out;
}
function save(m, outliner, anims) {
  const file = path.join(outDir, m.name + ".bbmodel");
  m.write(outliner, file);
  const j = JSON.parse(fs.readFileSync(file, "utf8")), ids = {};
  const walk = o => { for (const c of o) if (typeof c !== "string") { ids[c.name] = c.uuid; walk(c.children || []); } };
  walk(j.outliner);
  j.animations = anims.map(a => build(a, ids, m.name));
  fs.writeFileSync(file, JSON.stringify(j));
  console.log(m.name.padEnd(13), m.elements.length, "cubes,", anims.map(a => a.name).join(" "));
}
const rot = (r, o) => ({ rotation: r, origin: o });

/** three glowing eyes in a triangle on a face plane at z, centred on x0,y0 */
function eyes(m, name, x0, y0, z, s = 1) {
  const e = (dx, dy, w) => m.cube(name, [x0 + dx - w / 2, y0 + dy - .5 * s, z - .3], [x0 + dx + w / 2, y0 + dy + .5 * s, z + .1], glow(EYE, EYE_CORE));
  return [e(-1.5 * s, 0, 1.4 * s), e(1.5 * s, 0, 1.4 * s), e(0, 1.4 * s, 1.1 * s)];
}

// ---------------------------------------------------------------- curved building blocks
// Big masses are stacks of thin slices whose outline follows a profile; each slice is two crossed boxes (an octagon in section),
// so torsos, domes and limbs read round from every side. Limbs, horns and spikes are chains of short rotated segments that bend.
const R = { raw: true };
const lin = (a, b) => t => a + (b - a) * t;
const bul = (a, b, k) => t => a + (b - a) * t + k * Math.sin(Math.PI * t);
const dom = (W, p = 2) => t => W * Math.sqrt(Math.max(.04, 1 - Math.pow(t, p)));
const cst = v => () => v;
/** soft chitin / metal: no per-box rim lines (slices would band), lit top lips, gentle mottle */
const soft = (base, hi, lo, moss = 0) => (x, y, z, f) => {
  if (f === "down") return mix(base, lo, .5);
  let c = f === "up" ? mix(base, hi, .35) : sideShade(base, f);
  if (moss && n3(x, y, z, .6, 11) < moss) c = mix(c, MOSS, .3);
  const n = n3(x, y, z, 1.4);
  return n < .15 ? mix(c, lo, .25) : n > .88 ? mix(c, hi, .3) : c;
};
/** horizontal bands in world space (ribs, segmented armour) */
const bands = (base, dark, step = 1) => (x, y, z, f) => f !== "up" && f !== "down" && Math.floor(y / step) % 2 ? mix(sideShade(base, f), dark, .6) : sideShade(base, f);
/**
 * Lathe: n slices from a0 to a1 along axis "y" (centre c(t) = [x, z]) or "z" (centre c(t) = [x, y]).
 * w(t) = half-size across x, d(t) = half-size across the other axis. oct < 1 makes each slice an octagon (two crossed boxes).
 */
function lathe(m, name, axis, a0, a1, n, { c, w, d }, paint, oct = .7) {
  const out = [], lo = Math.min(a0, a1), step = Math.abs(a1 - a0) / n;
  for (let i = 0; i < n; i++) {
    const t = (i + .5) / n, s0 = a0 < a1 ? a0 + i * step : a0 - (i + 1) * step, s1 = s0 + step + .02;
    const [p, q] = c(t), W = w(t), D = d(t);
    void lo;
    const box = (ww, dd, k) => axis === "y"
      ? m.cube(`${name}${i}${k}`, [p - ww, s0, q - dd], [p + ww, s1, q + dd], paint, R)
      : m.cube(`${name}${i}${k}`, [p - ww, q - dd, s0], [p + ww, q + dd, s1], paint, R);
    if (oct >= 1) out.push(box(W, D, ""));
    else out.push(box(W, D * oct, "a"), box(W * oct, D, "b"));
  }
  return out;
}
/** curved horn / limb segment chain from base: angle a (deg) from straight up, in plane "x" (+a bends toward +z) or "z" (+a toward -x) */
function horn(m, name, base, { len, n = 3, r0, r1 = r0, a0 = 0, a1 = a0, plane = "x" }, paint) {
  const ids = [], L = len / n;
  let [px, py, pz] = base;
  for (let i = 0; i < n; i++) {
    const t = (i + .5) / n, a = a0 + (a1 - a0) * t, rad = a * Math.PI / 180, r = r0 + (r1 - r0) * t;
    const dir = plane === "x" ? [0, Math.cos(rad), Math.sin(rad)] : [-Math.sin(rad), Math.cos(rad), 0];
    const c = [px + dir[0] * L / 2, py + dir[1] * L / 2, pz + dir[2] * L / 2];
    ids.push(m.cube(`${name}${i}`, [c[0] - r, c[1] - L / 2 - .12, c[2] - r], [c[0] + r, c[1] + L / 2 + .12, c[2] + r], paint,
      { raw: true, rotation: plane === "x" ? [a, 0, 0] : [0, 0, a], origin: c }));
    px += dir[0] * L; py += dir[1] * L; pz += dir[2] * L;
  }
  return { ids, end: [px, py, pz] };
}
/** several horn segments joined end to end (a bending limb) */
function chain(m, name, base, segs, paint) {
  let at = base; const ids = [];
  segs.forEach((s, k) => { const h = horn(m, name + k + "_", at, s, s.paint || paint); ids.push(...h.ids); at = h.end; });
  return { ids, end: at };
}
/** a thin plate bowed outward toward -z (thigh / shin / breast plates) */
const plateF = (m, name, x, y0, y1, w, z, bow, paint, n = 3) => lathe(m, name, "y", y0, y1, n, { c: t => [x, z - bow * Math.sin(Math.PI * t)], w: bul(w * .9, w * .9, w * .15), d: cst(.4) }, paint, 1);

/** digitigrade ghoul leg: thigh bends forward to the knee, shin back to the ankle, long foot with two toes */
function ghoulLeg(m, s, x, hy, skin, k = 1, rk = k) {
  const leg = chain(m, `leg_${s}`, [x, hy, 0], [
    { len: 5 * k, n: 3, r0: 1.15 * rk, r1: .8 * rk, a0: -148, a1: -160 },
    { len: 4.8 * k, n: 3, r0: .78 * rk, r1: .55 * rk, a0: 150, a1: 162 },
    { len: 3 * k, n: 2, r0: .62 * rk, r1: .45 * rk, a0: -100, a1: -92 },
  ], skin);
  const e = leg.end;
  return [...leg.ids, ...[-1, 1].flatMap(o => horn(m, `toe_${s}${o}`, [e[0] + o * .38 * k, e[1], e[2]], { len: 1.3 * k, n: 2, r0: .3 * k, r1: .14 * k, a0: -96, a1: -125 }, flat(THR_D)).ids)];
}
/**
 * Hive skull face (the knight's face, scalable): a pale mask curved round the head on arcs - angry V brow, cheekbones,
 * temples, nose bridge and slits, upper teeth with fangs, a slightly open lower jaw with teeth, chin; optional corner tusks.
 * y0 = bottom of the upper jaw, zc / R = the head's centre line and radius (front of the mask at about zc - R), s = scale.
 * Returns { parts, glow }: glow = two slanted eyes deep in the sockets (+ a small lower pair) and a faint throat light.
 */
function hiveFace(m, { y0, zc, R = 3, s = 1, paint, teethPaint = flat(BW), tusks = true, small = true, eyeCol = EYE, eyeCore = EYE_CORE }) {
  const DK = "#120d12";
  const arc = (name, a, b, rr, ks, step, { w = 1.05, d = .8, dy = () => 0 } = {}) => ks.map(k => {
    const ang = k * step, rad = ang * Math.PI / 180, x = Math.sin(rad) * rr * s, z = zc - Math.cos(rad) * rr * s, o = dy(k) * s;
    return m.cube(name + (k + 9), [x - w * s / 2, y0 + a * s + o, z - d * s / 2], [x + w * s / 2, y0 + b * s + o, z + d * s / 2], paint, { raw: true, rotation: [0, -ang, 0], origin: [x, y0 + (a + b) / 2 * s + o, z] });
  });
  const F = zc - R * s; // mask front
  const teeth = [];
  for (let k = -3; k <= 3; k++) {
    const rad = k * 13 * Math.PI / 180, x = Math.sin(rad) * 2.75 * s, z = zc - Math.cos(rad) * 2.75 * s - .1 * s, fang = Math.abs(k) === 2, L = (fang ? 1.25 : .75) * s, t = .2 * s;
    teeth.push(m.cube("tooth_u" + (k + 9), [x - t, y0 + .05 * s - L, z - t], [x + t, y0 + .1 * s, z + t], teethPaint, R));
    if (Math.abs(k) < 3) teeth.push(m.cube("tooth_l" + (k + 9), [x * .9 - t * .85, y0 - 1.35 * s, z + .35 * s - t * .85], [x * .9 + t * .85, y0 + (-.7 + (fang ? .25 : 0)) * s, z + .35 * s + t * .85], flat(BW_D), R));
  }
  const parts = [
    m.cube("mouth_dark", [-1.9 * s, y0 - 1.45 * s, zc - 2.1 * s], [1.9 * s, y0 + .05 * s, zc - 1 * s], flat(DK), R),
    ...arc("brow", 2.65, 3.75, 3.25 * R / 3, [-3, -2, -1, 0, 1, 2, 3], 14, { dy: k => .28 * Math.abs(k) - .3 }),
    ...arc("brow_top", 3.65, 4.25, 3.05 * R / 3, [-2, -1, 0, 1, 2], 16, { d: .6, dy: k => .26 * Math.abs(k) - .2 }),
    ...arc("cheekbone", .65, 1.55, 3.05 * R / 3, [-3, -2, 2, 3], 15, { w: 1.1 }),
    ...arc("temple", 1.35, 3.15, 3.05 * R / 3, [-4, 4], 15, { w: .9 }),
    m.cube("nose_bridge", [-.38 * s, y0 + .65 * s, F - .35 * s], [.38 * s, y0 + 2.75 * s, F + .3 * s], paint, R),
    m.cube("nostril_l", [.12 * s, y0 + .75 * s, F - .42 * s], [.48 * s, y0 + 1.45 * s, F - .2 * s], flat(DK), R),
    m.cube("nostril_r", [-.48 * s, y0 + .75 * s, F - .42 * s], [-.12 * s, y0 + 1.45 * s, F - .2 * s], flat(DK), R),
    ...arc("maxilla", 0, .7, 2.95 * R / 3, [-3, -2, -1, 0, 1, 2, 3], 13, { w: 1, d: .7 }),
    ...arc("jaw", -2.05, -1.3, 2.55 * R / 3, [-3, -2, -1, 0, 1, 2, 3], 14, { w: 1, d: .8, dy: k => .12 * Math.abs(k) }),
    ...arc("chin", -2.55, -1.95, 2.4 * R / 3, [-1, 0, 1], 14, { w: 1, d: .7 }),
    ...teeth,
  ];
  if (tusks) for (const o of [-1, 1])
    parts.push(...horn(m, "tusk" + o, [o * 2.15 * s, y0 - .85 * s, zc - 1.9 * s], { len: 2.6 * s, n: 3, r0: .38 * s, r1: .12 * s, a0: 180 + o * 8, a1: 180 - o * 22, plane: "z" }, teethPaint).ids);
  const eg = glow(eyeCol, eyeCore), ey = y0 + 1.75 * s;
  const glowParts = [
    m.cube("eye_l", [.55 * s, ey, F - .2 * s], [2.15 * s, ey + .6 * s, F + .2 * s], eg, { raw: true, rotation: [0, -10, 16], origin: [1.35 * s, ey + .3 * s, F] }),
    m.cube("eye_r", [-2.15 * s, ey, F - .2 * s], [-.55 * s, ey + .6 * s, F + .2 * s], eg, { raw: true, rotation: [0, 10, -16], origin: [-1.35 * s, ey + .3 * s, F] }),
    m.cube("throat", [-1 * s, y0 - 1.25 * s, zc - 1.9 * s], [1 * s, y0 - .55 * s, zc - 1.7 * s], flat(PUS_D), R),
  ];
  if (small) glowParts.push(
    m.cube("eye_l2", [1.75 * s, y0 + 1.1 * s, F + .1 * s], [2.35 * s, y0 + 1.45 * s, F + .45 * s], flat(eyeCol), R),
    m.cube("eye_r2", [-2.35 * s, y0 + 1.1 * s, F + .1 * s], [-1.75 * s, y0 + 1.45 * s, F + .45 * s], flat(eyeCol), R));
  return { parts, glow: glowParts };
}
/** eyeless ghoul skull: long dome with a centre crest, heavy brow over blind sunken pits, gaunt cheeks, nose slits,
 *  fanged upper teeth and a hinged lower jaw (mouth at height y0, back of the head at z0) */
function ghoulSkull(m, y0, z0, k = 1, skin = hide()) {
  const L = 5.4 * k, F = z0 - L, DK = "#120d12", pale = bone(mix(BW, THR, .4), THR_D);
  const teeth = [];
  for (let q = 0; q < 6; q++) {
    const tx = (q - 2.5) * .62 * k, fang = q === 0 || q === 5, curve = Math.abs(q - 2.5) * .18 * k;
    teeth.push(m.cube("tooth_u" + q, [tx - .2, y0 - (fang ? 1.15 : .75), F + .15 + curve], [tx + .2, y0 + .45, F + .6 + curve], flat("#cdc6b2"), R));
    if (q > 0 && q < 5) teeth.push(m.cube("tooth_l" + q, [tx - .18, y0 - .95, F + .45 + curve], [tx + .18, y0 - .3, F + .85 + curve], flat("#aea793"), R));
  }
  const side = o => [
    m.cube("brow" + o, [o > 0 ? .1 : -1.9 * k, y0 + 2.5 * k, F - .25], [o > 0 ? 1.9 * k : -.1, y0 + 3.3 * k, F + 1.8], pale, { raw: true, rotation: [0, 0, o * 10], origin: [o * k, y0 + 2.9 * k, F] }),
    m.cube("pit" + o, [o > 0 ? .35 : -1.55 * k, y0 + 1.6 * k, F - .06], [o > 0 ? 1.55 * k : -.35, y0 + 2.4 * k, F + .3], flat(DK), R),
    m.cube("cheek" + o, [o > 0 ? 1.3 * k : -1.95 * k, y0 + .6, F + .6], [o > 0 ? 1.95 * k : -1.3 * k, y0 + 1.5 * k, F + 3.4 * k], pale, R),
    m.cube("hollow" + o, [o > 0 ? 1.15 * k : -1.75 * k, y0 - .8, F + 1.2], [o > 0 ? 1.75 * k : -1.15 * k, y0 + .5, F + 2.8 * k], flat(DK), R),
  ];
  return [
    ...lathe(m, "neck", "y", y0 - 1.8, y0 + 1.4, 2, { c: () => [0, z0 - .2], w: cst(1 * k), d: cst(1 * k) }, skin),
    ...lathe(m, "cranium", "z", z0 + .4, F, 7, { c: t => [0, y0 + 2.3 * k - .5 * t * k], w: t => 2.1 * k * (1 - .32 * t * t) + .2 * Math.sin(Math.PI * t), d: t => (1.9 - .55 * t) * k + .25 * Math.sin(Math.PI * t) }, skin),
    ...lathe(m, "crest", "z", z0 - .2, F + 1.4, 5, { c: t => [0, y0 + (4.3 + .35 * Math.sin(Math.PI * t) - .55 * t) * k], w: cst(.32 * k), d: t => .3 + .25 * Math.sin(Math.PI * t) }, pale, 1),
    ...side(1), ...side(-1),
    m.cube("nose_l", [.1, y0 + .8, F - .06], [.4, y0 + 1.5, F + .2], flat(DK), R),
    m.cube("nose_r", [-.4, y0 + .8, F - .06], [-.1, y0 + 1.5, F + .2], flat(DK), R),
    m.cube("mouth", [-1.3 * k, y0 - .9, F + .9], [1.3 * k, y0 + .6, z0 - 1], flat(DK), R),
    ...lathe(m, "gum", "z", F + .5, F + 1.6, 2, { c: () => [0, y0 + .62], w: cst(1.75 * k), d: cst(.2) }, pale, 1),
    ...lathe(m, "jaw", "z", z0 - .6, F + .6, 4, { c: () => [0, y0 - 1.15], w: t => 1.55 * k * (1 - .3 * t), d: cst(.5) }, skin),
    ...teeth,
  ];
}
/** long ghoul arm: bending upper arm and forearm, knobby hand, three short claws fanned slightly apart */
function ghoulArm(m, s, x, sy, skin, k = 1) {
  const a = chain(m, `arm_${s}`, [x, sy + .4, 0], [
    { len: 6 * k, n: 3, r0: 1 * k, r1: .8 * k, a0: 178, a1: 185 },
    { len: 6 * k, n: 3, r0: .8 * k, r1: .62 * k, a0: 188, a1: 196 },
    { len: 1.7 * k, n: 2, r0: .9 * k, r1: .95 * k, a0: 197, a1: 199 },
  ], skin);
  const e = a.end;
  return [...a.ids, ...[-1, 0, 1].flatMap((o, q) => horn(m, `claw_${s}${q}`, [e[0] + o * .78 * k, e[1] + .35, e[2]],
    { len: 1.5 * k, n: 2, r0: .23, r1: .11, a0: 180 + o * 16, a1: 180 + o * 30, plane: "z" }, flat(BW_D)).ids)];
}

// ================================================================ 굶주린 구울 (runner)
function runner() {
  const m = new Model("hive_runner", 256, { round: true });
  const skin = hide();
  const body = [
    ...lathe(m, "pelvis", "y", 9, 11.2, 2, { c: () => [0, .1], w: lin(2.2, 1.9), d: cst(1.6) }, skin),
    ...lathe(m, "waist", "y", 10.8, 13, 2, { c: () => [0, .3], w: cst(1.3), d: cst(1.1) }, bands(THR_D, THR_DD, .7)),
    ...lathe(m, "ribs", "y", 12.6, 18, 6, { c: t => [0, .1 * Math.sin(Math.PI * t)], w: bul(2.6, 3, .5), d: bul(2, 2, .4) }, bands(THR, THR_D, .9)),
    ...lathe(m, "shoulders", "y", 16.8, 18.9, 2, { c: () => [0, .2], w: lin(4.3, 3.5), d: lin(1.9, 1.5) }, skin),
    ...[0, 1, 2, 3, 4].map(i => m.cube("spine" + i, [-.45, 10.9 + i * 1.6, 2], [.45, 11.9 + i * 1.6, 2.9 + .15 * Math.sin(i)], flat(THR_L), R)),
  ];
  const outliner = [m.G("root", [0, 0, 0], [
    m.G("leg_l", [1.9, 10, 0], ghoulLeg(m, "l", 1.9, 10, skin)), m.G("leg_r", [-1.9, 10, 0], ghoulLeg(m, "r", -1.9, 10, skin)),
    m.G("body", [0, 10, 0], [
      ...body,
      m.G("head", [0, 18, -.6], ghoulSkull(m, 18.6, -.6, .85, skin)),
      m.G("arm_l", [4.4, 17.8, 0], ghoulArm(m, "l", 4.4, 17.8, skin)), m.G("arm_r", [-4.4, 17.8, 0], ghoulArm(m, "r", -4.4, 17.8, skin)),
    ], [-36, 0, 0]),
  ])];
  const anims = [
    A("idle", 1.6, {
      body: { rot: p => lean(2 * sin(p), 0, 0), pos: p => [0, -.2 + .2 * sin(p), 0] },
      head: { rot: p => lean(-26 + 4 * sin(p), 16 * sin(p, 1, .2), 7 * sin(p, 2)) },
      arm_l: { rot: p => limb(58 + 5 * sin(p), 0, -10) }, arm_r: { rot: p => limb(58 + 5 * sin(p, 1, .5), 0, 10) },
    }),
    A("walk", .42, {
      root: { pos: p => [0, Math.abs(sin(p)) * .6, 0] },
      body: { rot: p => lean(12 + 3 * sin(p, 2), 6 * sin(p), 0) },
      head: { rot: p => lean(-38 + 3 * sin(p, 2)) },
      leg_l: { rot: p => limb(40 * sin(p)) }, leg_r: { rot: p => limb(-40 * sin(p)) },
      arm_l: { rot: p => limb(62 - 34 * sin(p), 0, -12) }, arm_r: { rot: p => limb(62 + 34 * sin(p), 0, 12) },
    }),
    A("work", .36, {
      body: { rot: p => lean(10 + 10 * sin(p), 18 * sin(p), 0) },
      head: { rot: p => lean(-30) },
      arm_l: { rot: p => limb(95 + 55 * sin(p), 25 * sin(p), -10) }, arm_r: { rot: p => limb(95 - 55 * sin(p), -25 * sin(p), 10) },
      leg_l: { rot: p => limb(-18) }, leg_r: { rot: p => limb(22) },
    }),
  ];
  save(m, outliner, anims);
}

// ================================================================ 부패 구울 (bomber)
function bomber() {
  const m = new Model("hive_bomber", 256, { round: true });
  const skin = hide("#75705f", "#4c4739", "#959080");
  const veined = (x, y, z, f) => n3(x, y, z, 2.2, 4) < .08 ? PUS_D : skin(x, y, z, f);
  const body = [
    ...lathe(m, "hips", "y", 12, 14.4, 2, { c: () => [0, .2], w: lin(3.3, 3.6), d: cst(2.4) }, skin),
    ...lathe(m, "belly", "y", 13.8, 22.6, 7, { c: t => [0, -.6 - .4 * Math.sin(Math.PI * t)], w: bul(3.8, 4, 1.3), d: bul(3.2, 3, 1.3) }, veined),
    ...lathe(m, "chest", "y", 21.8, 25.4, 3, { c: () => [0, -.1], w: lin(4, 3.1), d: lin(3.1, 2.7) }, bands("#75705f", "#4c4739", .9)),
    ...lathe(m, "hump", "y", 17.6, 25, 5, { c: t => [0, 3.6 + .5 * Math.sin(Math.PI * t)], w: bul(2.8, 3.2, .8), d: bul(1, 1, 1) }, veined),
    ...lathe(m, "shoulders", "y", 24, 26.2, 2, { c: () => [0, .1], w: lin(3.9, 3.2), d: lin(2.5, 2.1) }, skin),
  ];
  const pus = [];
  for (const [x, y, z, s, face] of [[-2.6, 18.4, -5.6, 2.4, "n"], [2.2, 15.6, -5, 2.6, "n"], [3.2, 20.4, -5.2, 1.6, "n"], [-.8, 14.8, -4.6, 1.6, "n"], [0, 22.4, -3.6, 1.8, "n"],
    [-5.2, 17.4, -1, 2, "w"], [5.2, 16.4, 0, 2.2, "e"], [-4.8, 21, 1.6, 1.6, "w"], [-1.8, 20, 5.4, 2.4, "s"], [2, 22.6, 5.2, 2, "s"], [0, 17.4, 5.2, 1.6, "s"],
    [2.8, 26.2, 0, 1.8, "u"], [-2.7, 26.2, -.6, 2, "u"], [1, 25.4, 3.4, 1.8, "u"]]) {
    const d = s * .55, h = s / 2;
    const from = face === "n" ? [x - h, y - h, z - d] : face === "s" ? [x - h, y - h, z] : face === "w" ? [x - d, y - h, z - h] : face === "e" ? [x, y - h, z - h] : [x - h, y - .2, z - h];
    const to = face === "n" ? [x + h, y + h, z + .2] : face === "s" ? [x + h, y + h, z + d] : face === "w" ? [x + .2, y + h, z + h] : face === "e" ? [x + d, y + h, z + h] : [x + h, y + d, z + h];
    pus.push(m.cube("pustule", from, to, glow(PUS, "#f4ffb0")));
  }
  const outliner = [m.G("root", [0, 0, 0], [
    m.G("leg_l", [2.4, 12.4, 0], ghoulLeg(m, "l", 2.4, 12.4, skin, 1.3, 1.7)), m.G("leg_r", [-2.4, 12.4, 0], ghoulLeg(m, "r", -2.4, 12.4, skin, 1.3, 1.7)),
    m.G("body", [0, 12.4, 0], [
      ...body,
      m.G("pustules_glow", [0, 18, 0], pus),
      m.G("head", [0, 25, -1.4], ghoulSkull(m, 25.4, -1.6, .95, skin)),
      m.G("arm_l", [4.3, 25, 0], ghoulArm(m, "l", 4.3, 25, skin, 1.15)), m.G("arm_r", [-4.3, 25, 0], ghoulArm(m, "r", -4.3, 25, skin, 1.15)),
    ], [-12, 0, 0]),
  ])];
  const anims = [
    A("idle", 2.4, {
      body: { rot: p => lean(2 * sin(p), 0, 3 * sin(p)), scl: p => [1 + .02 * sin(p, 2), 1, 1 + .02 * sin(p, 2)] },
      pustules_glow: { scl: p => { const k = 1 + .05 * Math.max(0, sin(p, 2)); return [k, k, k]; } },
      head: { rot: p => lean(-4 + 4 * sin(p), 10 * sin(p, 1, .3), 8 * sin(p)) },
      arm_l: { rot: p => limb(14 + 3 * sin(p), 0, -17) }, arm_r: { rot: p => limb(14 + 3 * sin(p, 1, .5), 0, 17) },
    }),
    A("walk", 1.5, {
      root: { pos: p => [0, .4 * Math.abs(sin(p)), 0] },
      body: { rot: p => lean(4, 8 * sin(p), 7 * sin(p)) },
      head: { rot: p => lean(-2, -8 * sin(p), -6 * sin(p)) },
      leg_l: { rot: p => limb(22 * sin(p), 0, 0) }, leg_r: { rot: p => limb(-22 * sin(p), 0, 0) },
      arm_l: { rot: p => limb(30 - 14 * sin(p), 0, -16) }, arm_r: { rot: p => limb(30 + 14 * sin(p), 0, 16) },
      pustules_glow: { scl: p => { const k = 1 + .04 * Math.max(0, sin(p, 2)); return [k, k, k]; } },
    }),
    A("work", .5, { // swelling up before it bursts
      body: { scl: p => { const k = 1.12 + .08 * sin(p, 2); return [k, 1.04 + .03 * sin(p, 2), k]; }, rot: p => lean(-8, 4 * sin(p, 3), 6 * sin(p, 4)) },
      pustules_glow: { scl: p => { const k = 1.16 + .1 * sin(p, 2); return [k, k, k]; } },
      head: { rot: p => lean(-34 + 6 * sin(p, 4)) },
      arm_l: { rot: p => limb(30, 0, -60 - 10 * sin(p, 4)) }, arm_r: { rot: p => limb(30, 0, 60 + 10 * sin(p, 4)) },
    }),
  ];
  save(m, outliner, anims);
}

// ================================================================ 군체 사도 (acolyte)
function acolyte() {
  const m = new Model("hive_acolyte", 256, { round: true });
  const pu = soft(PU, PU_L, PU_D, .2), pl = soft(PU_L, PU_HI, PU_D, .15), b = bone();
  const hoof = (x, y, z, f) => f === "north" && Math.abs(x - Math.round(x)) < .2 ? HOOF_D : sideShade(HOOF, f);
  const leg = (s, x) => [
    ...lathe(m, `thigh_${s}`, "y", 7.6, 13.8, 3, { c: () => [x, 0], w: bul(1.2, 1.5, .25), d: bul(1.2, 1.5, .25) }, b),
    ...plateF(m, `thighp_${s}`, x, 9, 13.4, 1.6, -1.6, .35, pu),
    ...lathe(m, `knee_${s}`, "y", 6.4, 9.4, 3, { c: () => [x, -.8], w: bul(1.3, 1.3, .6), d: bul(1.4, 1.4, .6) }, pl),
    ...lathe(m, `shin_${s}`, "y", 1.6, 7, 3, { c: () => [x, 0], w: lin(1, 1.2), d: lin(1, 1.2) }, b),
    ...plateF(m, `shinp_${s}`, x, 2, 6.6, 1.3, -1.35, .3, pu),
    ...lathe(m, `hoof_${s}`, "y", 0, 2, 2, { c: () => [x, -.8], w: lin(1.6, 1.2), d: lin(2.3, 1.7) }, hoof),
  ];
  const tabard = [];
  for (let k = 0; k < 4; k++) {
    const x0 = -1.9 + k * .95, len = 13 + 4 * hash(k, 3, 9);
    tabard.push(m.cube("tabard_" + k, [x0, 23.4 - len, -4], [x0 + .95, 23.6, -3.55], cloth(CLOTH, CLOTH_D, k)));
  }
  const body = [
    ...lathe(m, "pelvis", "y", 12, 14.6, 2, { c: () => [0, .1], w: lin(3, 2.7), d: cst(2.1) }, pu),
    ...lathe(m, "waist", "y", 14.3, 16.6, 2, { c: () => [0, .1], w: cst(2.2), d: cst(1.6) }, bands(BW, BW_D, .8)),
    ...lathe(m, "torso", "y", 16.2, 24.2, 6, { c: () => [0, .1], w: bul(3.2, 3.6, .7), d: bul(2.2, 2.1, .5) }, pu),
    ...plateF(m, "breast_l", 1.8, 18.4, 24, 1.7, -2.5, .35, pl, 4),
    ...plateF(m, "breast_r", -1.8, 18.4, 24, 1.7, -2.5, .35, pl, 4),
    m.cube("breast_ridge", [-.3, 18.6, -3.4], [.3, 24, -2.6], flat(PU_HI), R),
    ...lathe(m, "collar", "y", 23.6, 25, 2, { c: () => [0, -.3], w: lin(2.8, 2.2), d: lin(2.4, 2) }, b),
    ...horn(m, "back_spike_1", [0, 21, 2.4], { len: 3, n: 3, r0: .7, r1: .18, a0: 40, a1: 78 }, pl).ids,
    ...horn(m, "back_spike_2", [0, 18, 2.4], { len: 2.4, n: 3, r0: .55, r1: .15, a0: 45, a1: 80 }, pl).ids,
    m.cube("sash", [-.45, 14.5, -4.3], [.45, 24, -3.9], flat(CLOTH_D), rot([0, 0, 28], [0, 19, -4.1])),
    m.cube("loin_back", [-2.4, 7.6, 2.2], [2.4, 14.6, 2.7], cloth(CLOTH, CLOTH_D, 5)),
    ...tabard,
  ];
  const hood = soft("#2d3025", "#424434", "#1a1b15", .35);
  const skullP = (x, y, z, f) => f === "north" ? "#120d12" : b(x, y, z, f);
  const face = hiveFace(m, { y0: 25.5, zc: -1.5, R: 3, s: .68, paint: bone(mix(BW, MOSS, .15), BW_D), tusks: false });
  const head = [
    ...lathe(m, "neck", "y", 23.8, 26, 2, { c: () => [0, 0], w: cst(1.1), d: cst(1.1) }, b),
    ...lathe(m, "skull", "y", 24.6, 28.9, 4, { c: () => [0, -1.5], w: bul(1.6, 1.6, .3), d: bul(1.7, 1.7, .25) }, skullP),
    ...face.parts,
    ...lathe(m, "hood", "y", 26.2, 31.8, 7, { c: t => [0, -.3 + 1.2 * t], w: t => 3 * Math.sqrt(1 - t * t * .85), d: t => 2.6 * Math.sqrt(1 - t * t * .8) }, hood),
    ...lathe(m, "hood_brow", "z", -2.2, -4.3, 2, { c: t => [0, 29.4 - .2 * t], w: t => 2.6 - .5 * t, d: cst(.55) }, hood),
    ...horn(m, "hood_wing_l", [2.4, 26.8, -.6], { len: 4.6, n: 3, r0: .8, r1: .4, a0: -8, a1: -32, plane: "z" }, hood).ids,
    ...horn(m, "hood_wing_r", [-2.4, 26.8, -.6], { len: 4.6, n: 3, r0: .8, r1: .4, a0: 8, a1: 32, plane: "z" }, hood).ids,
    ...horn(m, "hood_peak", [0, 31.2, .3], { len: 3, n: 3, r0: .9, r1: .25, a0: 15, a1: 48 }, hood).ids,
    ...horn(m, "hood_tail", [0, 28.4, 1.8], { len: 4, n: 3, r0: 1.3, r1: .6, a0: 160, a1: 148 }, hood).ids,
    ...horn(m, "mandible_l", [1.05, 25.3, -3.1], { len: 1.8, n: 2, r0: .35, r1: .15, a0: 175, a1: 155, plane: "z" }, flat(BW)).ids,
    ...horn(m, "mandible_r", [-1.05, 25.3, -3.1], { len: 1.8, n: 2, r0: .35, r1: .15, a0: 185, a1: 205, plane: "z" }, flat(BW)).ids,
  ];
  const headGlow = face.glow;
  const arm = (s, x) => {
    const o = x > 0 ? 1 : -1;
    return [
      ...lathe(m, `pauldron_${s}`, "y", 21, 25.6, 5, { c: () => [x + o * .2, 0], w: dom(2.6, 1.6), d: dom(2.7, 1.6) }, pu),
      ...lathe(m, `pauldron_rim_${s}`, "y", 20.4, 21.4, 1, { c: () => [x + o * .2, 0], w: cst(2.75), d: cst(2.85) }, pl),
      ...horn(m, `pauldron_spike_${s}`, [x + o * .6, 24.4, 0], { len: 3, n: 3, r0: .65, r1: .18, a0: -o * 15, a1: -o * 42, plane: "z" }, pl).ids,
      ...lathe(m, `upper_${s}`, "y", 16, 21.2, 3, { c: () => [x, 0], w: bul(1, 1.1, .2), d: bul(1, 1.1, .2) }, b),
      ...lathe(m, `fore_${s}`, "y", 10.6, 16.4, 3, { c: () => [x, 0], w: lin(.85, 1), d: lin(.85, 1) }, b),
      ...lathe(m, `bracer_${s}`, "y", 12, 15.2, 3, { c: () => [x, 0], w: bul(1.2, 1.3, .15), d: bul(1.2, 1.3, .15) }, pu),
      ...lathe(m, `hand_${s}`, "y", 9.2, 10.9, 2, { c: () => [x, -.1], w: lin(.9, 1.05), d: lin(1, 1.1) }, b),
      ...[-1, 0, 1].flatMap((dx, q) => horn(m, `claw_${s}${q}`, [x + dx * .6, 9.4, -.5], { len: 2.2, n: 2, r0: .22, r1: .1, a0: 188, a1: 208 }, flat(BW)).ids),
    ];
  };
  // curved dark gun held in the right hand: barrel down the arm (aimed by raising the arm), two jaw-like prongs at the muzzle
  const X = -5.4, g = soft(GUN, GUN_L, GUN_D), gl = soft(GUN_L, GUN_HI, GUN);
  const gun = [
    m.cube("gun_grip", [X - .7, 8.6, -.8], [X + .7, 10.6, 1.6], g),
    ...lathe(m, "gun_body", "y", 3.4, 9.8, 5, { c: t => [X, -1.3 - .5 * Math.sin(Math.PI * t)], w: bul(1, 1.1, .15), d: bul(1.3, 1.4, .4) }, g),
    ...horn(m, "gun_spine", [X, 9.6, -2.9], { len: 6, n: 4, r0: .65, r1: .42, a0: 176, a1: 192 }, gl).ids,
    ...horn(m, "gun_prong_top", [X, 4, -2.4], { len: 4, n: 3, r0: .85, r1: .28, a0: 192, a1: 166 }, gl).ids,
    ...horn(m, "gun_prong_low", [X, 3.8, -.4], { len: 3, n: 3, r0: .6, r1: .22, a0: 168, a1: 198 }, g).ids,
    ...horn(m, "gun_hook", [X, 7.4, .4], { len: 3.2, n: 3, r0: .5, r1: .28, a0: 160, a1: 120 }, g).ids,
  ];
  const gunGlow = [
    m.cube("muzzle", [X - .6, 2.4, -2], [X + .6, 2.9, -.9], glow(FIRE, FIRE_L)),
    ...[5, 6.6].map((y, q) => m.cube("gun_slit_" + q, [X - 1.4, y, -2.4], [X + 1.4, y + .5, -1], glow(FIRE, FIRE))),
  ];
  const outliner = [m.G("root", [0, 0, 0], [
    m.G("leg_l", [2.2, 13, 0], leg("l", 2.2)), m.G("leg_r", [-2.2, 13, 0], leg("r", -2.2)),
    m.G("body", [0, 13, 0], [
      ...body,
      m.G("head", [0, 24, 0], [...head, m.G("eyes_glow", [0, 26.9, -3.6], headGlow)]),
      m.G("arm_l", [5.4, 22.4, 0], arm("l", 5.4)),
      m.G("arm_r", [-5.4, 22.4, 0], [...arm("r", -5.4), ...gun, m.G("gun_glow", [X, 2.6, -1.4], gunGlow)]),
    ]),
  ])];
  const anims = [
    A("idle", 2, {
      body: { rot: p => lean(4 + 1.5 * sin(p)), pos: p => [0, .15 * sin(p), 0] },
      head: { rot: p => lean(-6 + 3 * sin(p), 16 * sin(p, .5), 0) },
      arm_l: { rot: p => limb(10 + 3 * sin(p), 0, -10) }, arm_r: { rot: p => limb(34 + 3 * sin(p, 1, .4), -8, 6) },
      leg_l: { rot: p => limb(6, 0, -4) }, leg_r: { rot: p => limb(-4, 0, 4) },
    }),
    A("walk", .9, {
      root: { pos: p => [0, .45 * Math.abs(sin(p)), 0] },
      body: { rot: p => lean(8, 6 * sin(p), 0) },
      head: { rot: p => lean(-8, -6 * sin(p)) },
      leg_l: { rot: p => limb(30 * sin(p)) }, leg_r: { rot: p => limb(-30 * sin(p)) },
      arm_l: { rot: p => limb(-26 * sin(p), 0, -8) }, arm_r: { rot: p => limb(40 + 6 * sin(p), -8, 6) },
    }),
    A("work", .3, { // aiming and firing the gun
      body: { rot: p => lean(-2 - 3 * Math.max(0, sin(p)), 10, 0) },
      head: { rot: p => lean(0, -6) },
      arm_r: { rot: p => limb(90 + 6 * Math.max(0, sin(p)), 10, 0), pos: p => [0, 0, .8 * Math.max(0, sin(p))] },
      arm_l: { rot: p => limb(14, 0, -14) },
      leg_l: { rot: p => limb(16) }, leg_r: { rot: p => limb(-14) },
      gun_glow: { scl: p => { const k = 1 + .6 * Math.max(0, sin(p)); return [k, k, k]; } },
    }),
  ];
  save(m, outliner, anims);
}

// ================================================================ 통곡 기사 (knight)
function knight() {
  const m = new Model("hive_knight", 256, { round: true });
  const pu = soft(PU, PU_L, PU_D, .18), pl = soft(PU_L, PU_HI, PU_D, .15), b = bone();
  const hoof = (x, y, z, f) => f === "north" && Math.abs(x % 3.4) < .3 ? HOOF_D : sideShade(mix(HOOF, PU, .4), f);
  const leg = (s, x) => [
    ...lathe(m, `thigh_${s}`, "y", 11, 18.8, 4, { c: () => [x, 0], w: bul(2.3, 2.7, .45), d: bul(2.3, 2.7, .45) }, pu),
    ...plateF(m, `thighp_${s}`, x, 12.4, 18.4, 2.6, -2.8, .5, pl, 4),
    ...lathe(m, `knee_${s}`, "y", 8.2, 12.2, 4, { c: () => [x, -1.2], w: bul(2.2, 2.2, .7), d: bul(2.1, 2.1, .8) }, pl),
    ...horn(m, `knee_spike_${s}`, [x, 10.2, -3.9], { len: 1.8, n: 2, r0: .6, r1: .2, a0: -80, a1: -55 }, pl).ids,
    ...lathe(m, `knee_bone_${s}`, "y", 8.8, 11.2, 2, { c: () => [x, 1.7], w: cst(1.5), d: cst(1) }, b),
    ...lathe(m, `shin_${s}`, "y", 1.6, 9, 4, { c: () => [x, 0], w: bul(2, 2.3, .3), d: bul(2.1, 2.3, .3) }, pu),
    ...plateF(m, `greave_${s}`, x, 2, 8.6, 2.2, -2.4, .4, pl, 4),
    ...lathe(m, `hoof_${s}`, "y", 0, 2.2, 2, { c: () => [x, -1], w: lin(2.6, 2.2), d: lin(3.5, 2.8) }, hoof),
  ];
  const body = [
    ...lathe(m, "hips", "y", 17, 21.2, 3, { c: () => [0, .1], w: lin(5.2, 5.6), d: cst(3.3) }, pu),
    ...plateF(m, "tasset_l", 2.4, 12.8, 19.8, 2, -3.4, .5, pl, 4),
    ...plateF(m, "tasset_r", -2.4, 12.8, 19.8, 2, -3.4, .5, pl, 4),
    ...lathe(m, "tasset_b", "y", 13.4, 20, 3, { c: t => [0, 3.4 + .4 * Math.sin(Math.PI * t)], w: cst(4.4), d: cst(.45) }, pu, 1),
    ...lathe(m, "torso", "y", 20.5, 33.6, 8, { c: t => [0, .2 + .3 * t], w: bul(5.6, 6.6, 1.1), d: bul(3.5, 3.8, .7) }, pu),
    ...plateF(m, "breast_l", 3, 25.6, 33.4, 3, -4.1, .6, pl, 5),
    ...plateF(m, "breast_r", -3, 25.6, 33.4, 3, -4.1, .6, pl, 5),
    m.cube("breast_ridge", [-.4, 25.8, -5.2], [.4, 33.2, -4.3], flat(PU_HI), R),
    ...lathe(m, "ab", "y", 21, 26.2, 5, { c: () => [0, -3.7], w: bul(3.9, 3.7, .4), d: cst(.6) }, bands(BW, BW_C, 1), 1),
    ...lathe(m, "hump", "y", 22, 33.4, 6, { c: () => [0, 4.3], w: bul(4.8, 5.2, .6), d: bul(1, 1.2, .9) }, pu),
    ...[0, 1, 2].flatMap(k => horn(m, "back_spike_" + k, [0, 25 + k * 3, 5.6], { len: 2.6 + k * .5, n: 3, r0: .8, r1: .18, a0: 40, a1: 72 }, pl).ids),
    ...lathe(m, "gorget", "y", 32.8, 35.8, 3, { c: () => [0, -.4], w: lin(4.8, 3.6), d: lin(4.2, 3.4) }, pl),
  ];
  // ---- face: dark recessed head under a pale skull mask: angry V brow over deep sockets, two slanted glowing eyes
  // (+ a small dim pair below), cheekbones, nose slits, fanged upper teeth, a slightly open lower jaw and corner tusks
  const skullB = bone(mix(BW, MOSS, .22), BW_D), deep = soft(PU_D, PU, "#120d12"), dark = (x, y, z, f) => f === "north" ? deep(x, y, z, f) : pu(x, y, z, f);
  /** pieces set on a horizontal arc (radius R round the head axis at z = zc) so the mask curves round the face */
  const arc = (name, y0, y1, R, zc, ks, step, paint, { w = 1.05, d = .8, dy = () => 0 } = {}) => ks.map(k => {
    const ang = k * step, rad = ang * Math.PI / 180, x = Math.sin(rad) * R, z = zc - Math.cos(rad) * R, o = dy(k);
    return m.cube(name + (k + 9), [x - w / 2, y0 + o, z - d / 2], [x + w / 2, y1 + o, z + d / 2], paint, { raw: true, rotation: [0, -ang, 0], origin: [x, (y0 + y1) / 2 + o, z] });
  });
  const crown = [];
  for (const [a, len] of [[-62, 3.4], [-38, 4.4], [-14, 5.4], [14, 5.4], [38, 4.4], [62, 3.4]])
    crown.push(...horn(m, "crown_spike", [-a / 62 * 2.6, 39.6, -2.4], { len, n: 3, r0: 1.1, r1: .25, a0: a * .8, a1: a * 1.35, plane: "z" }, pl).ids);
  for (const o of [-1, 1]) crown.push(...horn(m, "crown_back", [o * 1.1, 39.4, -.2], { len: 4.2, n: 3, r0: 1, r1: .22, a0: 28, a1: 62 }, pu).ids);
  const teeth = [];
  for (let k = -3; k <= 3; k++) {
    const rad = k * 13 * Math.PI / 180, x = Math.sin(rad) * 2.75, z = -2.2 - Math.cos(rad) * 2.75 - .1, fang = Math.abs(k) === 2, L = fang ? 1.25 : .75;
    teeth.push(m.cube("tooth_u" + (k + 9), [x - .2, 34.9 - L, z - .2], [x + .2, 34.95, z + .2], flat(BW), R));
    if (Math.abs(k) < 3) teeth.push(m.cube("tooth_l" + (k + 9), [x * .9 - .17, 33.5, z + .35 - .17], [x * .9 + .17, 34.15 + (fang ? .25 : 0), z + .35 + .17], flat(BW_D), R));
  }
  const head = [
    ...lathe(m, "head_core", "y", 33.6, 39.4, 6, { c: () => [0, -2.1], w: bul(2.3, 2.4, .3), d: bul(2.5, 2.3, .3) }, dark),
    m.cube("mouth_dark", [-1.9, 33.4, -4.3], [1.9, 34.9, -3.2], flat("#120d12"), R),
    ...arc("brow", 37.5, 38.6, 3.25, -2.2, [-3, -2, -1, 0, 1, 2, 3], 14, skullB, { dy: k => .28 * Math.abs(k) - .3 }),
    ...arc("brow_top", 38.5, 39.1, 3.05, -2.2, [-2, -1, 0, 1, 2], 16, skullB, { d: .6, dy: k => .26 * Math.abs(k) - .2 }),
    ...arc("cheekbone", 35.5, 36.4, 3.05, -2.2, [-3, -2, 2, 3], 15, skullB, { w: 1.1 }),
    ...arc("temple", 36.2, 38, 3.05, -2.2, [-4, 4], 15, skullB, { w: .9 }),
    m.cube("nose_bridge", [-.38, 35.5, -5.55], [.38, 37.6, -4.9], skullB, R),
    m.cube("nostril_l", [.12, 35.6, -5.62], [.48, 36.3, -5.4], flat("#120d12"), R),
    m.cube("nostril_r", [-.48, 35.6, -5.62], [-.12, 36.3, -5.4], flat("#120d12"), R),
    ...arc("maxilla", 34.85, 35.55, 2.95, -2.2, [-3, -2, -1, 0, 1, 2, 3], 13, skullB, { w: 1, d: .7 }),
    ...arc("jaw", 32.8, 33.55, 2.55, -2.1, [-3, -2, -1, 0, 1, 2, 3], 14, skullB, { w: 1, d: .8, dy: k => .12 * Math.abs(k) }),
    ...arc("chin", 32.3, 32.9, 2.4, -2.1, [-1, 0, 1], 14, skullB, { w: 1, d: .7 }),
    ...teeth,
    ...horn(m, "tusk_l", [2.15, 34, -4.1], { len: 2.6, n: 3, r0: .38, r1: .12, a0: 188, a1: 158, plane: "z" }, flat(BW)).ids,
    ...horn(m, "tusk_r", [-2.15, 34, -4.1], { len: 2.6, n: 3, r0: .38, r1: .12, a0: 172, a1: 202, plane: "z" }, flat(BW)).ids,
    ...lathe(m, "crown_band", "y", 38.6, 40.8, 2, { c: () => [0, -1.9], w: lin(3.6, 3.1), d: lin(3.6, 3.1) }, pl),
    ...horn(m, "cheek_l", [2.9, 38.6, -3.2], { len: 4.6, n: 3, r0: .75, r1: .35, a0: 190, a1: 204, plane: "z" }, pl).ids,
    ...horn(m, "cheek_r", [-2.9, 38.6, -3.2], { len: 4.6, n: 3, r0: .75, r1: .35, a0: 170, a1: 156, plane: "z" }, pl).ids,
    ...crown,
  ];
  const eyeG = glow(EYE, EYE_CORE);
  const headGlow = [
    m.cube("eye_l", [.55, 36.6, -5.25], [2.15, 37.2, -4.85], eyeG, { raw: true, rotation: [0, -10, 16], origin: [1.35, 36.9, -5.05] }),
    m.cube("eye_r", [-2.15, 36.6, -5.25], [-.55, 37.2, -4.85], eyeG, { raw: true, rotation: [0, 10, -16], origin: [-1.35, 36.9, -5.05] }),
    m.cube("eye_l2", [1.75, 35.95, -4.95], [2.35, 36.3, -4.6], flat(EYE), R),
    m.cube("eye_r2", [-2.35, 35.95, -4.95], [-1.75, 36.3, -4.6], flat(EYE), R),
    m.cube("throat", [-1, 33.6, -4.3], [1, 34.3, -4.1], flat(PUS_D), R),
  ];
  const arm = (s, x) => {
    const o = x > 0 ? 1 : -1;
    return [
      ...lathe(m, `pauldron_${s}`, "y", 29.6, 36.4, 6, { c: () => [x + o * .3, 0], w: dom(4, 1.5), d: dom(4, 1.5) }, pu),
      ...lathe(m, `pauldron_top_${s}`, "y", 33, 37.2, 4, { c: () => [x + o * .7, 0], w: dom(2.6, 1.6), d: dom(2.9, 1.6) }, pl),
      ...lathe(m, `pauldron_rim_${s}`, "y", 28.4, 30, 1, { c: () => [x + o * .3, 0], w: cst(4.1), d: cst(4.1) }, pl),
      ...horn(m, `pauldron_spike_${s}a`, [x + o * 1.2, 36.2, -1.2], { len: 4, n: 3, r0: .85, r1: .18, a0: -o * 15, a1: -o * 45, plane: "z" }, pl).ids,
      ...horn(m, `pauldron_spike_${s}b`, [x + o * .4, 35.6, 1.6], { len: 3, n: 3, r0: .7, r1: .15, a0: 30, a1: 62 }, pu).ids,
      ...lathe(m, `upper_${s}`, "y", 23, 29.6, 3, { c: () => [x, 0], w: bul(1.9, 2, .3), d: bul(1.9, 2, .3) }, pu),
      ...lathe(m, `elbow_${s}`, "y", 21.6, 24.4, 2, { c: () => [x, 1.6], w: cst(1.5), d: cst(1.1) }, b),
      ...lathe(m, `gauntlet_${s}`, "y", 14.6, 23.4, 5, { c: () => [x, 0], w: bul(2.3, 2.4, .4), d: bul(2.3, 2.4, .4) }, pl),
      m.cube(`gauntlet_ridge_${s}`, [x - .6, 15.2, -3.3], [x + .6, 22.6, -2.6], flat(PU_HI), R),
      ...lathe(m, `fist_${s}`, "y", 12, 14.9, 2, { c: () => [x, 0], w: lin(1.8, 2), d: lin(1.8, 2) }, b),
    ];
  };
  // forked black siege cannon held along the right forearm (barrel down the arm, -y), blue core window in its side
  const X = -9, g = soft(GUN, GUN_L, GUN_D), gl = soft(GUN_L, GUN_HI, GUN);
  const cannon = [
    ...lathe(m, "cannon_body", "y", 4, 20, 8, { c: t => [X, -1.2 - .3 * Math.sin(Math.PI * t)], w: bul(2.4, 2.2, .6), d: bul(2.8, 2.6, .7) }, g),
    ...horn(m, "cannon_spine", [X, 19.4, -4], { len: 14, n: 6, r0: .9, r1: .65, a0: 188, a1: 172 }, gl).ids,
    ...lathe(m, "cannon_back", "y", 9, 21, 3, { c: () => [X, 2.3], w: cst(1.3), d: cst(.8) }, g, 1),
    ...lathe(m, "cannon_barrel", "y", 1, 4.4, 2, { c: () => [X, -1.2], w: lin(1.6, 1.9), d: lin(2, 2.3) }, soft(GUN_D, GUN, GUN_D)),
    ...horn(m, "cannon_fork_a", [X, 5, -3.8], { len: 7, n: 4, r0: 1.1, r1: .32, a0: 186, a1: 160 }, gl).ids,
    ...horn(m, "cannon_fork_b", [X, 6, 1.6], { len: 6.6, n: 4, r0: 1, r1: .3, a0: 172, a1: 196 }, g).ids,
    ...lathe(m, "cannon_cap", "y", 19.6, 22.6, 3, { c: () => [X, -1.2], w: dom(2.3), d: dom(2.7) }, gl),
  ];
  const cannonGlow = [
    m.cube("cannon_core", [X - 3.3, 10, -2.8], [X + 3.3, 15.6, .4], glow(BLUE, BLUE_L)),
    ...[2.2, 3.4].map((y, q) => m.cube("cannon_ring_" + q, [X - 2.05, y, -3.45], [X + 2.05, y + .5, 1.05], glow(BLUE, BLUE))),
    m.cube("cannon_muzzle", [X - 1.4, .7, -2.6], [X + 1.4, 1.1, .2], glow(BLUE, BLUE_L)),
  ];
  const outliner = [m.G("root", [0, 0, 0], [
    m.G("leg_l", [3.4, 18, 0], leg("l", 3.4)), m.G("leg_r", [-3.4, 18, 0], leg("r", -3.4)),
    m.G("body", [0, 18, 0], [
      ...body,
      m.G("head", [0, 34, -1], [...head, m.G("eyes_glow", [0, 36.6, -5.2], headGlow)]),
      m.G("arm_l", [9, 31.6, 0], arm("l", 9)),
      m.G("arm_r", [-9, 31.6, 0], [...arm("r", -9), ...cannon, m.G("cannon_glow", [X, 10, 0], cannonGlow)]),
    ]),
  ])];
  const anims = [
    A("idle", 2.6, {
      body: { rot: p => lean(3 + 1.5 * sin(p)), pos: p => [0, .2 * sin(p), 0] },
      head: { rot: p => lean(-4 + 2 * sin(p), 12 * sin(p, .5), 0) },
      arm_l: { rot: p => limb(8 + 2 * sin(p), 0, -10) }, arm_r: { rot: p => limb(28 + 3 * sin(p, 1, .4), -6, 8) },
      leg_l: { rot: p => limb(6, 0, -3) }, leg_r: { rot: p => limb(-5, 0, 3) },
      cannon_glow: { scl: p => { const k = 1 + .06 * sin(p, 2); return [k, k, k]; } },
    }),
    A("walk", 1.3, {
      root: { pos: p => [0, .7 * Math.abs(sin(p)), 0] },
      body: { rot: p => lean(6, 5 * sin(p), 2 * sin(p)) },
      head: { rot: p => lean(-5, -5 * sin(p)) },
      leg_l: { rot: p => limb(24 * sin(p)) }, leg_r: { rot: p => limb(-24 * sin(p)) },
      arm_l: { rot: p => limb(-18 * sin(p), 0, -10) }, arm_r: { rot: p => limb(32 + 5 * sin(p), -6, 8) },
    }),
    A("work", .6, { // raising and charging the cannon
      body: { rot: p => lean(-4, 14, 0) },
      head: { rot: p => lean(-2, -10) },
      arm_r: { rot: p => limb(88 + 2 * sin(p, 3), 8, 0) },
      arm_l: { rot: p => limb(20, 0, -16) },
      leg_l: { rot: p => limb(14) }, leg_r: { rot: p => limb(-12) },
      cannon_glow: { scl: p => { const k = 1.1 + .25 * (.5 + .5 * sin(p, 2)); return [k, k, k]; } },
    }),
  ];
  save(m, outliner, anims);
}

// ================================================================ 군체 주술사 (wizard) — floats; no legs
function wizard() {
  const m = new Model("hive_wizard", 256, { round: true });
  const wa = soft(WA, WA_L, WA_D), wl = soft(WA_L, "#aaa19d", WA_D), wp = soft(PU, PU_L, PU_D);
  /** robe strips: cream panels flaring out from the waist, uneven frayed lengths; a few long splayed tatters at the sides */
  const hem = [];
  for (let k = 0; k < 16; k++) {
    const a = k / 16 * TAU, r = 3.3, x = Math.sin(a) * r, z = -Math.cos(a) * r, len = 10 + 5 * hash(k, 7, 3), t = 14 + 8 * hash(k, 2, 5);
    const w = Math.abs(Math.cos(a)) > .6 ? 1.5 : 1.1;
    // each panel in two pieces so it bends outward more toward the hem
    const top = len * .55;
    hem.push(m.cube("robe_strip", [x - w, 16.4 - top, z - .35], [x + w, 16.6, z + .35], cloth(k % 3 ? RB : RB_D, RB_D, k),
      rot([t * .7, -a * 180 / Math.PI, 0], [x, 16.4, z])));
    const tr = t * .7 * Math.PI / 180, bx = x + Math.sin(a) * Math.sin(tr) * top, bz = z - Math.cos(a) * Math.sin(tr) * top, by = 16.4 - Math.cos(tr) * top;
    hem.push(m.cube("robe_hem", [bx - w, by - (len - top), bz - .35], [bx + w, by + .3, bz + .35], cloth(k % 3 ? RB : RB_D, RB_D, k + 3),
      rot([t * 1.5, -a * 180 / Math.PI, 0], [bx, by, bz])));
  }
  for (const [a, t, len] of [[-.32, 48, 12], [.3, 44, 13], [.9, 56, 10], [-.95, 52, 11], [.55, 30, 14], [-.6, 34, 14]]) {
    const ang = a * Math.PI, x = Math.sin(ang) * 3.4, z = -Math.cos(ang) * 3.4;
    hem.push(m.cube("robe_tatter", [x - .35, 16 - len, z - .3], [x + .35, 16.2, z + .3], (px, y, pz, f, i, j, w, h) => j > h * .7 ? RB_D : RB,
      rot([Math.cos(ang) * t, 0, Math.sin(ang) * t], [x, 16, z])));
  }
  const cape = [];
  for (let k = 0; k < 7; k++) {
    const x0 = -4.2 + k * 1.2, len = 20 + 6 * hash(k, 9, 1), splay = (k - 3) * 4;
    cape.push(m.cube("cape_" + k, [x0, 27.6 - len, 3], [x0 + 1.2, 28, 3.7], cloth(CAPE, CAPE_L, k), rot([-6, 0, splay], [x0 + .6, 28, 3.3])));
  }
  const body = [
    ...lathe(m, "belt", "y", 15, 17.4, 2, { c: () => [0, 0], w: lin(3.4, 3), d: lin(3, 2.7) }, wp),
    ...lathe(m, "waist", "y", 17, 21.6, 3, { c: () => [0, 0], w: cst(2.4), d: cst(1.9) }, bands(WA_D, PU_D, .8)),
    ...lathe(m, "core", "y", 15.6, 22.4, 4, { c: () => [0, -2.4], w: bul(1.3, 1.2, .2), d: cst(.5) }, bands(WG, shade(WG, -.25), .8), 1),
    ...lathe(m, "torso", "y", 21, 29.2, 6, { c: () => [0, .1], w: t => 2.6 + 1.5 * Math.pow(t, .8), d: bul(2.3, 2.6, .4) }, wa),
    ...plateF(m, "chest_l", 1.9, 23.4, 28.8, 1.6, -2.5, .4, wl, 4),
    ...plateF(m, "chest_r", -1.9, 23.4, 28.8, 1.6, -2.5, .4, wl, 4),
    m.cube("chest_core", [-.7, 21.4, -3.5], [.7, 28.4, -2.7], flat(WG), R),
    ...lathe(m, "collar", "y", 28.4, 30.2, 2, { c: () => [0, 0], w: lin(3.6, 3), d: lin(3, 2.6) }, wp),
    ...lathe(m, "back_plate", "y", 20, 28.6, 4, { c: () => [0, 2.9], w: bul(3.2, 3.4, .4), d: cst(.4) }, wa, 1),
    ...cape,
  ];
  const crest = (x, y, z, f) => (f === "north" || f === "south") && ((Math.floor(x * 1.2) % 3) + 3) % 3 === 1 ? WA_D : wa(x, y, z, f);
  const faceP = (x, y, z, f) => f === "north" ? "#120d12" : wa(x, y, z, f);
  const face = hiveFace(m, { y0: 30.2, zc: -1.2, R: 3, s: .6, paint: soft(WA_L, "#aaa19d", WA_D), teethPaint: flat(WA_L), tusks: false });
  const head = [
    ...lathe(m, "neck", "y", 28.4, 30.4, 2, { c: () => [0, 0], w: cst(1), d: cst(1) }, flat(WA_D)),
    ...lathe(m, "face", "y", 28.8, 33.2, 4, { c: () => [0, -1.2], w: bul(1.5, 1.4, .3), d: bul(1.6, 1.5, .25) }, faceP),
    ...face.parts,
    ...lathe(m, "crest", "y", 32.6, 38.2, 7, { c: t => [0, -.8 + .5 * t], w: t => 2.3 + 3.2 * Math.pow(t, 1.4), d: t => 1.8 - .8 * t }, crest, .75),
    ...horn(m, "crest_lobe_l", [3.9, 37.8, -.4], { len: 3.6, n: 3, r0: .9, r1: .3, a0: -15, a1: -58, plane: "z" }, wl).ids,
    ...horn(m, "crest_lobe_r", [-3.9, 37.8, -.4], { len: 3.6, n: 3, r0: .9, r1: .3, a0: 15, a1: 58, plane: "z" }, wl).ids,
    ...horn(m, "crest_peak", [0, 38, -.4], { len: 2.4, n: 2, r0: .7, r1: .25, a0: 0, a1: 12 }, wl).ids,
    m.cube("crest_ridge", [-.5, 32.4, -3.1], [.5, 38, -2.3], flat(PU_D), R),
    ...lathe(m, "crest_back", "y", 32, 36.4, 3, { c: () => [0, 1.2], w: lin(2, 1.4), d: cst(.6) }, wa, 1),
  ];
  const headGlow = face.glow;
  const arm = (s, x) => {
    const o = x > 0 ? 1 : -1;
    return [
      ...lathe(m, `pauldron_${s}`, "y", 25.6, 31, 5, { c: () => [x + o * .2, 0], w: dom(2.9, 1.5), d: dom(3.1, 1.5) }, wa),
      ...lathe(m, `pauldron_low_${s}`, "y", 24.2, 26.4, 2, { c: () => [x + o * .2, 0], w: lin(3.2, 3), d: lin(3.3, 3.1) }, wl),
      ...horn(m, `pauldron_flare_${s}`, [x + o * 1.6, 29.4, 0], { len: 3.4, n: 3, r0: .9, r1: .28, a0: -o * 20, a1: -o * 58, plane: "z" }, wl).ids,
      ...lathe(m, `upper_${s}`, "y", 20.4, 25.2, 3, { c: () => [x, 0], w: bul(1.1, 1.2, .2), d: bul(1.1, 1.2, .2) }, wa),
      ...lathe(m, `fore_${s}`, "y", 14.6, 20.8, 4, { c: () => [x, 0], w: bul(1.2, 1.35, .25), d: bul(1.2, 1.35, .25) }, bands(WA_L, PU_D, 1.6)),
      ...[-1, 0, 1].flatMap((dz, q) => horn(m, `finger_${s}${q}`, [x + dz * .3, 14.8, dz * .55], { len: 2.2, n: 2, r0: .2, r1: .12, a0: 180 - o * 6, a1: 180 + o * 12, plane: "z" }, flat(WA_L)).ids),
    ];
  };
  const orb = (s, x) => [
    m.cube(`orb_${s}`, [x - 1.3, 11.6, -1.3], [x + 1.3, 14.2, 1.3], glow(ORB, ORB_CORE)),
    m.cube(`orb_${s}_spark`, [x - .5, 10.6, -.5], [x + .5, 15.2, .5], glow(ORB_CORE, ORB_CORE)),
  ];
  const outliner = [m.G("root", [0, 0, 0], [
    m.G("body", [0, 18, 0], [
      ...body,
      m.G("robe", [0, 16.4, 0], hem),
      m.G("head", [0, 29, -.5], [...head, m.G("eyes_glow", [0, 31.4, -3.1], headGlow)]),
      m.G("arm_l", [5.2, 27.6, 0], [...arm("l", 5.2), m.G("hand_l_glow", [5.2, 13, 0], orb("l", 5.2))]),
      m.G("arm_r", [-5.2, 27.6, 0], [...arm("r", -5.2), m.G("hand_r_glow", [-5.2, 13, 0], orb("r", -5.2))]),
    ]),
  ])];
  const flicker = (ph = 0) => ({ scl: p => { const k = 1 + .12 * sin(p, 3, ph) + .06 * sin(p, 7, ph); return [k, k, k]; } });
  const anims = [
    A("idle", 3, {
      root: { pos: p => [0, 1.2 * sin(p), 0] },
      body: { rot: p => lean(2 * sin(p, 1, .25), 0, 2 * sin(p, .5)) },
      robe: { rot: p => lean(3 * sin(p, 1, .1), 0, 3 * sin(p, 2)) },
      head: { rot: p => lean(-2 + 3 * sin(p), 15 * sin(p, .5), 0) },
      arm_l: { rot: p => limb(14 + 6 * sin(p), 0, -22 - 4 * sin(p)) }, arm_r: { rot: p => limb(14 + 6 * sin(p, 1, .5), 0, 22 + 4 * sin(p, 1, .5)) },
      hand_l_glow: flicker(0), hand_r_glow: flicker(.4),
    }, { n: 36 }),
    A("walk", 2, { // drifting
      root: { pos: p => [0, .8 * sin(p, 2), 0] },
      body: { rot: p => lean(9, 0, 3 * sin(p)) },
      robe: { rot: p => lean(-14 + 4 * sin(p, 2), 0, 4 * sin(p)) },
      head: { rot: p => lean(-8, 6 * sin(p)) },
      arm_l: { rot: p => limb(-10 + 6 * sin(p), 0, -26) }, arm_r: { rot: p => limb(-10 - 6 * sin(p), 0, 26) },
      hand_l_glow: flicker(0), hand_r_glow: flicker(.4),
    }, { n: 24 }),
    A("work", .5, { // hurling fire from alternating hands
      root: { pos: p => [0, .6, 0] },
      body: { rot: p => lean(-3, 12 * sin(p), 0) },
      head: { rot: p => lean(-6) },
      robe: { rot: p => lean(6, 0, 0) },
      arm_l: { rot: p => limb(60 + 35 * sin(p), -10, -6) }, arm_r: { rot: p => limb(60 - 35 * sin(p), 10, 6) },
      hand_l_glow: { scl: p => { const k = 1.25 + .35 * Math.max(0, sin(p)); return [k, k, k]; } },
      hand_r_glow: { scl: p => { const k = 1.25 + .35 * Math.max(0, -sin(p)); return [k, k, k]; } },
    }),
  ];
  save(m, outliner, anims);
}

// ================================================================ 문지기 (keeper) — a short, fat, dwarf-like brute of the lord's kind:
// shadow-dark hide, huge round belly on stubby legs, red sash, shaggy beard, the lord's crowned sorrow mask, a crescent axe in each fist.
// Signature attack (work): crouch, leap with both axes raised overhead, slam both down together on landing.
function keeper() {
  const m = new Model("hive_keeper", 512, { round: true });
  // shadow-dark hide (the pale mask glows out of it)
  const SK = "#2f2b35", SK_D = "#1c1a21", HAIR = "#26232b", SASH = "#6a211d", CLOTH = "#1e1a1c";
  const skin = soft(SK, "#46404f", SK_D), skinD = soft(SK_D, SK, "#0f0e13"), hairP = soft(HAIR, "#3a3542", "#121015");
  const ball = (nm, cx, cy, cz, Rr, n, paint, sq = 1) => lathe(m, nm, "y", cy - Rr * sq, cy + Rr * sq, n, { c: () => [cx, cz], w: t => Rr * Math.sqrt(Math.max(.08, 1 - (2 * t - 1) ** 2)), d: t => Rr * Math.sqrt(Math.max(.08, 1 - (2 * t - 1) ** 2)) }, paint);
  // ---- stubby legs
  const leg = (s, x) => [
    ...lathe(m, `thigh_${s}`, "y", 4.4, 10.5, 3, { c: () => [x, .2], w: bul(2.8, 3.2, .6), d: bul(2.8, 3.1, .6) }, skin),
    ...lathe(m, `shin_${s}`, "y", 1.4, 5.2, 2, { c: () => [x, -.2], w: lin(2.5, 2.8), d: lin(2.6, 2.8) }, skin),
    ...lathe(m, `foot_${s}`, "y", 0, 1.8, 1, { c: () => [x, -1.2], w: cst(2.9), d: cst(3.8) }, skinD),
    ...[-1.5, 0, 1.5].map((dx, q) => m.cube(`toe_${s}${q}`, [x + dx - .6, 0, -5.6], [x + dx + .6, 1.1, -4.6], flat(HOOF_D), R)),
  ];
  // ---- belly, chest, hump; the head sits low and forward between the shoulders
  const body = [
    ...lathe(m, "hips", "y", 9, 12.6, 2, { c: () => [0, .2], w: cst(6.2), d: cst(4.6) }, soft(CLOTH, "#4a3e38", "#1f1916")),
    m.cube("loin_front", [-3.6, 4, -5.2], [3.6, 11, -4.3], cloth(CLOTH, "#1f1916", 2)),
    ...lathe(m, "sash", "y", 11.4, 14, 2, { c: () => [0, -.5], w: cst(7.6), d: cst(6) }, soft(SASH, "#9a3a33", "#4f1714")),
    m.cube("sash_knot", [-4.6, 7.4, -7], [-2.8, 13.6, -6], soft("#4f1714", SASH, "#2e0c0a"), R),
    // built like the lord: a moderate belly under a broad barrel chest, big shoulders and a hump
    ...lathe(m, "belly", "y", 12.6, 22, 6, { c: t => [0, -.6 - .4 * Math.sin(Math.PI * t)], w: bul(5.8, 6.6, 1), d: bul(4.4, 4.6, .9) }, skin),
    ...lathe(m, "chest", "y", 21, 29, 5, { c: () => [0, -.2], w: bul(7.4, 7.6, 1), d: bul(4.8, 4.6, .6) }, skin),
    ...[-1, 1].flatMap(o => ball("pec" + o, o * 3.2, 25.2, -3.6, 2.6, 3, skin)),
    ...ball("hump", 0, 28.4, 2.4, 5.4, 5, skin),
    ...lathe(m, "navel_scar", "y", 15, 18.6, 2, { c: () => [0, -6.1], w: cst(.35), d: cst(.25) }, flat(SK_D), 1),
  ];
  const HC = [0, 30.8, -5.8];
  const head = [
    ...ball("head", HC[0], HC[1], HC[2] + .2, 3.3, 4, skinD),
    ...lathe(m, "neck", "y", 26, 30, 2, { c: () => [0, HC[2] + 2], w: cst(3.2), d: cst(3) }, skinD),
    ...hiveCrownMask(m, { cx: HC[0], cy: HC[1], cz: HC[2], s: .95, name: "mask" }),
  ];
  for (let k = -4; k <= 4; k++) { // beard over the belly
    const a = k * 17 * Math.PI / 180, x = Math.sin(a) * 2.9, z = HC[2] - Math.cos(a) * 2.6, len = 9 - Math.abs(k) * 1.1;
    head.push(...chain(m, "beard" + (k + 4), [x, HC[1] - 3.4, z], [{ len, n: 3, r0: .85, r1: .45, a0: 180 + k * 2, a1: 180 + k * 6, plane: "z" }], k % 2 ? skinD : hairP).ids);
  }
  for (let k = 0; k < 7; k++) { // mane down the back of the head
    const a = (110 + k * 23) * Math.PI / 180, x = Math.sin(a) * 3, z = HC[2] - Math.cos(a) * 2.8;
    head.push(...horn(m, "mane" + k, [x, HC[1] + 2.4, z], { len: 6.5, n: 3, r0: .8, r1: .45, a0: 150 + (k % 3) * 6, a1: 165 }, hairP).ids);
  }
  // ---- arms: round shoulders, thick arms, big fists, each gripping an axe
  const arm = (s, x) => {
    const o = x > 0 ? 1 : -1;
    return [
      ...ball(`shoulder_${s}`, x, 26.6, .2, 4.4, 5, skin),
      ...lathe(m, `upper_${s}`, "y", 17.6, 26, 4, { c: () => [x + o * .4, 0], w: bul(2.8, 3.1, .6), d: bul(2.8, 3.1, .6) }, skin),
      ...lathe(m, `fore_${s}`, "y", 11, 18.4, 4, { c: () => [x + o * .5, 0], w: bul(2.8, 3.2, .6), d: bul(2.8, 3.2, .6) }, skin),
      ...lathe(m, `wrap_${s}`, "y", 12, 14.6, 2, { c: () => [x + o * .5, 0], w: cst(3.1), d: cst(3.1) }, soft(SASH, "#9a3a33", "#4f1714")),
      ...ball(`fist_${s}`, x + o * .5, 9.2, -.2, 2.7, 3, skinD),
      ...[0, 1, 2].flatMap(k => horn(m, `shag_${s}${k}`, [x + o * (1.4 + k * .9), 29 - k * .8, .8 - k * .6], { len: 4.4, n: 3, r0: .7, r1: .4, a0: 180 - o * 8, a1: 180 - o * 18, plane: "z" }, hairP).ids),
    ];
  };
  /** a heavy crescent axe built upright through the fist at (x, 9.2, -.2), blade on the -z side; the "axe_*" bone tips it forward */
  const axe = (s, x) => {
    const g = soft(GUN, GUN_L, GUN_D), gl = soft(GUN_L, GUN_HI, GUN), edge = soft("#6d6f78", "#9a9ca8", GUN), FZ = -.2;
    return [
      ...lathe(m, `haft_${s}`, "y", 4.6, 31.5, 6, { c: () => [x, FZ], w: cst(.75), d: cst(.75) }, soft(HOOF_D, HOOF, "#2a221d"), 1),
      ...[14, 18.5].map((y, q) => m.cube(`wrap_${s}${q}`, [x - .95, y, FZ - .95], [x + .95, y + 1.2, FZ + .95], soft(SASH, "#8a2f29", "#3e100d"), R)),
      m.cube(`pommel_${s}`, [x - 1.1, 3.2, FZ - 1.1], [x + 1.1, 4.8, FZ + 1.1], gl),
      m.cube(`socket_${s}`, [x - 1.1, 22.6, FZ - 1.4], [x + 1.1, 30.4, FZ + 1.4], gl),
      ...lathe(m, `head_${s}`, "y", 22.4, 30.6, 5, { c: t => [x, FZ - 4.3 - .5 * Math.sin(Math.PI * t)], w: cst(.5), d: t => 3.4 + 1.2 * Math.sin(Math.PI * t) }, g, 1),
      ...lathe(m, `edge_${s}`, "y", 20.4, 32.6, 7, { c: t => [x, FZ - 8.6 - 1.4 * Math.sin(Math.PI * t)], w: cst(.38), d: cst(.75) }, edge, 1),
      ...horn(m, `horn_top_${s}`, [x, 32.2, FZ - 8.4], { len: 2.6, n: 2, r0: .5, r1: .15, a0: 20, a1: 55 }, edge).ids,
      ...horn(m, `horn_low_${s}`, [x, 20.8, FZ - 8.4], { len: 2.6, n: 2, r0: .5, r1: .15, a0: 160, a1: 125 }, edge).ids,
      ...horn(m, `spike_${s}`, [x, 26.5, FZ + 1.2], { len: 3.4, n: 2, r0: .7, r1: .15, a0: 90, a1: 75 }, gl).ids,
      ...horn(m, `tip_${s}`, [x, 31.4, FZ], { len: 2.2, n: 2, r0: .55, r1: .12, a0: 0, a1: -8 }, gl).ids,
      ...[24.4, 26.6, 28.8].map((y, q) => m.cube(`rune_${s}${q}`, [x - .6, y, FZ - 7.2 + q * .9], [x + .6, y + .5, FZ - 3.6], flat(PUS_D), R)),
    ];
  };
  const outliner = [m.G("root", [0, 0, 0], [
    m.G("leg_l", [3.6, 10.5, 0], leg("l", 3.6)), m.G("leg_r", [-3.6, 10.5, 0], leg("r", -3.6)),
    m.G("body", [0, 10.5, 0], [
      ...body,
      m.G("head", [0, 27, -3], head),
      m.G("arm_l", [8.8, 26.4, 0], [...arm("l", 8.8), m.G("axe_l", [9.3, 9.2, -.2], axe("l", 9.3), [-40, 0, 0])]),
      m.G("arm_r", [-8.8, 26.4, 0], [...arm("r", -8.8), m.G("axe_r", [-9.3, 9.2, -.2], axe("r", -9.3), [-40, 0, 0])]),
    ]),
  ])];
  const ease = t => t * t * (3 - 2 * t);
  /** jump slam: crouch (0-.15), airborne with both axes overhead (.15-.72), slam (.72-.8), recover */
  const armsUp = p => p < .15 ? -6 - 24 * ease(p / .15) : p < .45 ? -30 + 195 * ease((p - .15) / .3) : p < .72 ? 165 : p < .8 ? 165 - 190 * ease((p - .72) / .08) : -25 + 19 * ease((p - .8) / .2);
  const anims = [
    A("idle", 2.4, {
      body: { rot: p => lean(2 * sin(p), 0, 2 * sin(p, .5)), pos: p => [0, -.15 + .25 * sin(p), 0], scl: p => [1 + .02 * sin(p), 1, 1 + .02 * sin(p)] },
      head: { rot: p => lean(-4 + 3 * sin(p), 10 * sin(p, .5), 0) },
      arm_l: { rot: p => limb(-4 + 3 * sin(p), 0, -10) }, arm_r: { rot: p => limb(-4 + 3 * sin(p, 1, .4), 0, 10) },
      leg_l: { rot: p => limb(3, 0, -5) }, leg_r: { rot: p => limb(-3, 0, 5) },
    }),
    A("walk", 1, { // waddle
      root: { pos: p => [0, .6 * Math.abs(sin(p)), 0] },
      body: { rot: p => lean(5, 6 * sin(p), 9 * sin(p)) },
      head: { rot: p => lean(-6, -5 * sin(p), -6 * sin(p)) },
      leg_l: { rot: p => limb(30 * sin(p), 0, -4) }, leg_r: { rot: p => limb(-30 * sin(p), 0, 4) },
      arm_l: { rot: p => limb(4 - 16 * sin(p), 0, -14) }, arm_r: { rot: p => limb(4 + 16 * sin(p), 0, 14) },
    }),
    A("work", 1.35, {
      root: { pos: p => [0, p < .15 ? -1.6 * ease(p / .15) : p < .8 ? -1.6 + 1.6 * ease(Math.min(1, (p - .15) / .1)) : -1.2 * Math.sin(Math.PI * (p - .8) / .2), 0] },
      body: { rot: p => lean(p < .15 ? 14 * ease(p / .15) : p < .72 ? 14 - 26 * ease(Math.min(1, (p - .15) / .3)) : p < .8 ? -12 + 40 * ease((p - .72) / .08) : 28 - 28 * ease((p - .8) / .2)) },
      head: { rot: p => lean(-8) },
      arm_l: { rot: p => limb(armsUp(p), -4, -10) }, arm_r: { rot: p => limb(armsUp(p), 4, 10) },
      leg_l: { rot: p => limb(p < .15 ? 26 * ease(p / .15) : p < .72 ? 26 - 40 * ease(Math.min(1, (p - .15) / .2)) : 10, 0, -6) },
      leg_r: { rot: p => limb(p < .15 ? 26 * ease(p / .15) : p < .72 ? 26 - 10 * ease(Math.min(1, (p - .15) / .2)) : -10, 0, 6) },
    }, { n: 40 }),
  ];
  save(m, outliner, anims);
}

// ================================================================ 영혼 촉수 (tentacle) — the soul pillar of the lord's hands page:
// a thick writhing tentacle (about 4 blocks) rising from a root mound, rows of pale suckers, thorns and glowing green veins,
// its tip curled round a glowing soul orb. Five nested segment bones sway one after another (idle = walk = work).
function tentacle() {
  const m = new Model("hive_tentacle", 512, { round: true });
  const flesh = soft(PU, PU_L, PU_D, .25), fleshD = soft(PU_D, PU, "#140e14", .2), sucker = soft(mix(BW, PU_HI, .25), BW, BW_D);
  const veinG = glow(PUS_D, PUS);
  const ringed = (x, y, z, f) => f !== "up" && f !== "down" && Math.floor(y / 1.6) % 3 === 0 ? mix(flesh(x, y, z, f), PU_D, .45) : flesh(x, y, z, f);
  // ---- root mound with tendrils spread over the floor
  const base = [
    ...lathe(m, "mound", "y", 0, 5.4, 4, { c: () => [0, 0], w: lin(7.2, 4.2), d: lin(7.2, 4.2) }, fleshD),
    ...[0, 1, 2, 3, 4, 5].flatMap(k => {
      const a = k * 60 + 20, rad = a * Math.PI / 180, bx = Math.sin(rad) * 3.4, bz = -Math.cos(rad) * 3.4;
      // a root crawling outward: plane z for the x part is enough when it mostly runs along x, plane x otherwise
      const alongX = Math.abs(Math.sin(rad)) > .7, sgn = alongX ? Math.sign(Math.sin(rad)) : -Math.sign(Math.cos(rad));
      return chain(m, "root" + k, [bx, 1.2, bz], [{ len: 5 + (k % 3), n: 3, r0: 1.3, r1: .45, a0: alongX ? -sgn * 100 : sgn * 100, a1: alongX ? -sgn * 115 : sgn * 112, plane: alongX ? "z" : "x" }], fleshD).ids;
    }),
  ];
  // ---- trunk: five segments, each a tapering ringed lathe with suckers on the front, thorns at the back, a glowing vein
  const segs = [], H = 11, R0 = 4;
  for (let i = 0; i < 5; i++) {
    const y0 = 5 + i * H, r0 = R0 * (1 - i * .15), r1 = R0 * (1 - (i + 1) * .15);
    const parts = [
      ...lathe(m, `seg${i}`, "y", y0 - .4, y0 + H + .4, 4, { c: () => [0, 0], w: lin(r0, r1), d: lin(r0, r1) }, ringed),
    ];
    for (let q = 0; q < 3; q++) for (const o of [-1, 1]) { // two rows of suckers facing -z
      const y = y0 + 2 + q * 3.2, r = r0 + (r1 - r0) * (q * 3.2 + 2) / H, sz = .9 - i * .1;
      parts.push(m.cube(`sucker${i}${q}${o}`, [o * r * .45 - sz, y - sz, -r - .35], [o * r * .45 + sz, y + sz, -r + .4], sucker, R));
      parts.push(m.cube(`sucker_hole${i}${q}${o}`, [o * r * .45 - sz * .45, y - sz * .45, -r - .45], [o * r * .45 + sz * .45, y + sz * .45, -r - .2], flat(PU_D), R));
    }
    if (i < 4) parts.push(...horn(m, `thorn${i}`, [0, y0 + 5, r0 * .9], { len: 2.4 - i * .3, n: 2, r0: .55, r1: .12, a0: 70, a1: 40 }, sucker).ids);
    if (i < 4) parts.push(...horn(m, `thorn_s${i}`, [(i % 2 ? 1 : -1) * r0 * .9, y0 + 8, 0], { len: 2 - i * .2, n: 2, r0: .45, r1: .1, a0: (i % 2 ? -1 : 1) * 70, a1: (i % 2 ? -1 : 1) * 40, plane: "z" }, sucker).ids);
    const vx = (i % 2 ? .6 : -.6) * r0, vz = .55 * r0;
    const vein = [m.cube(`vein${i}`, [vx - .3, y0 + .5, vz], [vx + .3, y0 + H - .5, vz + .5], veinG, R),
      m.cube(`pustule${i}`, [vx - .7, y0 + H * .55, vz + .2], [vx + .7, y0 + H * .55 + 1.4, vz + 1.1], glow(PUS, "#f4ffb0"), R)];
    segs.push({ parts, vein, y0 });
  }
  // ---- tip: a hook curling forward round a glowing soul orb
  const top = 5 + 5 * H;
  const hook = chain(m, "hook", [0, top - .4, 0], [{ len: 7, n: 5, r0: R0 * .25 + .3, r1: .35, a0: -10, a1: -150 }], flesh);
  const orb = [
    ...lathe(m, "orb", "y", top + 1.2, top + 5.6, 4, { c: () => [0, -2.6], w: t => 2.2 * Math.sqrt(Math.max(.1, 1 - (2 * t - 1) ** 2)), d: t => 2.2 * Math.sqrt(Math.max(.1, 1 - (2 * t - 1) ** 2)) }, glow(EYE, EYE_CORE)),
    m.cube("orb_core", [-.8, top + 2.6, -3.4], [.8, top + 4.2, -1.8], flat(EYE_CORE), R),
  ];
  // nest: base > seg0 > seg1 > ... > seg4 > tip; static S-bend so it never stands as a straight pole
  const bend = [7, -9, 10, -8, 6];
  let inner = [m.G("tip", [0, top, 0], [...hook.ids, m.G("orb_glow", [0, top + 3.4, -2.6], orb)])];
  for (let i = 4; i >= 0; i--) inner = [m.G(`s${i}`, [0, segs[i].y0, 0], [...segs[i].parts, m.G(`veins${i}_glow`, [0, segs[i].y0, 0], segs[i].vein), ...inner], [bend[i], 0, (i % 2 ? 4 : -4)])];
  const outliner = [m.G("root", [0, 0, 0], [m.G("base", [0, 0, 0], base), ...inner])];
  const writhe = (i, sp = 1) => ({ rot: p => lean(7 * sin(p, sp, i * .13), 6 * sin(p, sp, i * .11 + .3), 9 * sin(p, sp, i * .13 + .25)) });
  const pulse = { scl: p => { const k = 1 + .14 * Math.max(0, sin(p, 2)); return [k, k, k]; } };
  const bones = sp => ({ s0: writhe(0, sp), s1: writhe(1, sp), s2: writhe(2, sp), s3: writhe(3, sp), s4: writhe(4, sp), tip: { rot: p => lean(10 * sin(p, sp, .7)) }, orb_glow: pulse });
  const anims = [A("idle", 3.2, bones(1), { n: 40 }), A("walk", 3.2, bones(1), { n: 40 }), A("work", 1.6, bones(1), { n: 30 })];
  save(m, outliner, anims);
}

// ================================================================ 비탄의 거인 (ogre) — a huge hunched brute, about three players tall.
// Built at HALF size (feet y 0, about 2.7 blocks); the plugin sets the entity scale to 2. Pale bone hide with purple streaks,
// small mossy green chitin pads on the back and shoulders, a HUGE head: a bulging cluster of purple sacs over a
// toothed skull face, very long arms with three big claws, thick digitigrade legs on hooves.
function ogre() {
  const m = new Model("hive_ogre", 512, { round: true });
  const OB = "#cdc4b0", OB_D = "#968c78", STREAK = "#7a5c98", MOSSG = "#4f6a47", MOSSG_D = "#33452f", SAC = "#8c6aa0", SAC_L = "#b494c6", SAC_D = "#5d4370";
  const hideP = (x, y, z, f) => {
    if (f === "down") return OB_D;
    const c = sideShade(OB, f);
    if (hash(Math.floor(y * .9 + x * .3), Math.floor(x * .45 + z * .45), 7) < .12) return mix(c, STREAK, .75);   // purple streaks
    const n = n3(x, y, z, 1.6);
    return n < .12 ? mix(c, OB_D, .5) : n > .9 ? shade(c, .05) : c;
  };
  const moss = soft(MOSSG, "#6b8a5e", MOSSG_D, .0), plateB = bone(mix(BW, OB, .5), OB_D);
  const sac = (x, y, z, f) => { const n = n3(x, y, z, 1.8); return n < .15 ? SAC_D : n > .85 ? SAC_L : sideShade(SAC, f); };
  const ball = (nm, cx, cy, cz, Rr, n, paint) => lathe(m, nm, "y", cy - Rr, cy + Rr, n, { c: () => [cx, cz], w: t => Rr * Math.sqrt(Math.max(.08, 1 - (2 * t - 1) ** 2)), d: t => Rr * Math.sqrt(Math.max(.08, 1 - (2 * t - 1) ** 2)) }, paint);
  // ---- legs: thick digitigrade with hooves
  const leg = (s, x) => {
    const c = chain(m, `leg_${s}`, [x, 12.5, 0], [
      { len: 6.4, n: 6, r0: 3.1, r1: 2.4, a0: -148, a1: -162 },
      { len: 5.6, n: 6, r0: 2.3, r1: 1.9, a0: 150, a1: 162 },
      { len: 2.2, n: 2, r0: 1.8, r1: 1.7, a0: 172, a1: 180 },
    ], hideP);
    const e = c.end;
    return [...c.ids,
      ...lathe(m, `hoof_${s}`, "y", 0, 1.8, 2, { c: () => [x, e[2] - .6], w: cst(2), d: cst(2.6) }, soft(HOOF, "#9b8775", HOOF_D)),
      m.cube(`hoof_split_${s}`, [x - .2, 0, e[2] - 3.3], [x + .2, 1.7, e[2] - 1.6], flat(HOOF_D), R),
      ...horn(m, `knee_spike_${s}`, [x, 8.2, -3.4], { len: 2.2, n: 4, r0: .7, r1: .15, a0: -70, a1: -35 }, plateB).ids];
  };
  // ---- body (pivot at the hips, tipped well forward)
  const body = [
    ...lathe(m, "pelvis", "y", 10.5, 14.5, 4, { c: () => [0, .3], w: lin(5, 5.6), d: cst(3.8) }, hideP),
    ...lathe(m, "belly", "y", 14, 20, 6, { c: () => [0, -.4], w: bul(5.2, 6.2, .8), d: bul(4, 4.4, .6) }, hideP),
    ...lathe(m, "chest", "y", 19.5, 28.5, 9, { c: () => [0, -.2], w: bul(6.8, 7.4, 1.3), d: bul(4.6, 4.8, .9) }, hideP),
    ...[-1, 1].flatMap(o => ball("pec" + o, o * 3.2, 23, -3.8, 2.6, 5, hideP)),
    // mossy chitin hump over the back and shoulders
    ...ball("hump", 0, 26.4, 2.6, 3.6, 6, moss),
    ...[-1, 1].flatMap(o => ball("mantle" + o, o * 6.4, 27.4, .8, 2.6, 5, moss)),
    ...[0, 1].flatMap(k => horn(m, "back_spike" + k, [0, 24 + k * 2.4, 5.6 + k * .4], { len: 1.6, n: 3, r0: .6, r1: .12, a0: 45, a1: 78 }, plateB).ids),
  ];
  // ---- head: hangs low in front of the chest; skull face under a cluster of purple sacs
  const HC = [0, 25.2, -7.6], HS = 1.6;   // head centre and head scale (the head is the biggest part of it)
  const sacs = [], glowSacs = [];
  for (const [x0, y0, z0, r0, g] of [[0, 2.6, .4, 3.4, 0], [-2.2, 2, -.8, 2.3, 1], [2.3, 2.2, -.6, 2.4, 0], [-1.2, 3.8, -1.6, 2, 0], [1.4, 4, -1.4, 1.9, 1], [0, 1.4, -2.4, 2.1, 0], [-2.8, .6, .8, 1.7, 0], [2.9, .8, .9, 1.6, 0], [0, 4.6, 1.2, 2.2, 0]])
    { const x = x0 * HS, y = y0 * HS, z = z0 * HS, r = r0 * HS;
      (g ? glowSacs : sacs).push(...ball("sac", HC[0] + x, HC[1] + y, HC[2] + z, r, 6, g ? glow(SAC_L, "#f0dcff") : sac)); }
  // face below the sacs, scaled by HS: q(dx, dy, dz) = a point relative to the head centre
  const q = (dx, dy, dz) => [HC[0] + dx * HS, HC[1] + dy * HS, HC[2] + dz * HS];
  const qb = (nm, a, b2, paint) => m.cube(nm, q(...a), q(...b2), paint, R);
  const teeth = [];
  for (let k = 0; k < 6; k++) { const tx = (k - 2.5) * .62, fang = k === 0 || k === 5;
    teeth.push(qb("tooth_u" + k, [tx - .2, -2.6 - (fang ? .5 : 0), -3], [tx + .2, -1.8, -2.6], flat(BW)));
    if (k > 0 && k < 5) teeth.push(qb("tooth_l" + k, [tx - .18, -3.6, -2.8], [tx + .18, -2.9, -2.4], flat(BW_D))); }
  const head = [
    ...lathe(m, "skull", "y", HC[1] - 3 * HS, HC[1] + 1.4 * HS, 7, { c: () => [HC[0], HC[2] - .6 * HS], w: lin(2.3 * HS, 3 * HS), d: lin(2.3 * HS, 2.9 * HS) }, plateB),
    qb("brow_shadow", [-2.4, -.4, -3.6], [2.4, .6, -3.2], flat("#2a1f2e")),
    ...[-1, 1].map(o => qb("eye_pit" + o, [o * 1.1 - .5, -1.2, -3.5], [o * 1.1 + .5, -.5, -3.3], flat("#1a1320"))),
    qb("mouth", [-1.9, -3.6, -3], [1.9, -1.8, -1.4], flat("#1a1320")),
    ...lathe(m, "jaw", "z", HC[2] - 1 * HS, HC[2] - 3 * HS, 4, { c: () => [HC[0], HC[1] - 3.9 * HS], w: lin(2.1 * HS, 1.7 * HS), d: cst(.5 * HS) }, plateB),
    ...teeth,
    ...sacs,
  ];
  // ---- arms: horned shoulder plates, very long arms, big three-clawed hands
  const arm = (s, x) => {
    const o = x > 0 ? 1 : -1;
    const a = chain(m, `arm_${s}`, [x, 26.5, 0], [
      { len: 9, n: 7, r0: 2.4, r1: 2, a0: 180 - o * 4, a1: 180 + o * 2 },
      { len: 9.5, n: 7, r0: 2.1, r1: 1.8, a0: 186, a1: 194 },
      { len: 2.6, n: 3, r0: 1.95, r1: 2.05, a0: 196, a1: 198 },
    ], hideP);
    const e = a.end;
    return [...a.ids,
      ...lathe(m, `shoulder_plate_${s}`, "y", 26, 29.4, 4, { c: () => [x + o * .4, .6], w: bul(2.4, 1.8, .4), d: bul(2.6, 2, .4) }, moss),
      ...horn(m, `shoulder_horn_${s}`, [x + o * 1.8, 28.6, .4], { len: 3.4, n: 5, r0: 1, r1: .15, a0: -o * 18, a1: -o * 40, plane: "z" }, plateB).ids,
      ...horn(m, `elbow_spike_${s}`, [x + o * .8, 17.6, 1.6], { len: 3.2, n: 4, r0: .9, r1: .15, a0: 120, a1: 152 }, plateB).ids,
      ...lathe(m, `bracer_${s}`, "y", 9, 14, 5, { c: () => [x, -.8], w: cst(2.2), d: cst(2.2) }, moss),
      // hand: a thick palm and three long fingers that taper to sharp hooked points (two hook forward, one hooks back),
      // and a thumb curving up the back of the hand
      ...lathe(m, `palm_${s}`, "y", e[1] - .8, e[1] + 2.6, 4, { c: () => [e[0], e[2] - .1], w: bul(2.1, 2.2, .35), d: bul(1.8, 1.9, .35) }, hideP),
      ...[[-1, -.6, 188, 204, 218, 258], [1, -.6, 188, 202, 214, 252], [0, .9, 174, 160, 148, 112]].flatMap(([dx, dz, a0, a1, b0, b1], k) =>
        chain(m, `finger_${s}${k}`, [e[0] + dx * 1.05, e[1] - .4, e[2] + dz], [
          { len: 2.8, n: 4, r0: 1.05, r1: .85, a0, a1, plane: "x" },
          { len: 3.6, n: 6, r0: .82, r1: .1, a0: b0, a1: b1, plane: "x" },
        ], hideP).ids),
      ...chain(m, `thumb_${s}`, [e[0] - o * .9, e[1] + 1.6, e[2] + 1.2], [
        { len: 1.6, n: 2, r0: .85, r1: .75, a0: 60, a1: 45, plane: "x" },
        { len: 2.6, n: 4, r0: .72, r1: .1, a0: 38, a1: 15, plane: "x" },
      ], hideP).ids,
    ];
  };
  const outliner = [m.G("root", [0, 0, 0], [
    m.G("leg_l", [3.8, 12.5, 0], leg("l", 3.8)), m.G("leg_r", [-3.8, 12.5, 0], leg("r", -3.8)),
    m.G("body", [0, 12.5, 0], [
      ...body,
      m.G("head", [0, 25, -3.5], [...head, m.G("sacs_glow", [HC[0], HC[1] + 3, HC[2]], glowSacs)]),
      m.G("arm_l", [9.2, 26.5, 0], arm("l", 9.2)), m.G("arm_r", [-9.2, 26.5, 0], arm("r", -9.2)),
    ], [-28, 0, 0]),
  ])];
  const ease = t => t * t * (3 - 2 * t);
  /** two-fisted pound: arms raised high (0-.5), smashed down (.5-.62), recover */
  const pound = p => p < .5 ? 30 + 120 * ease(p / .5) : p < .62 ? 150 - 140 * ease((p - .5) / .12) : 10 + 20 * ease((p - .62) / .38);
  const anims = [
    A("idle", 3, {
      body: { rot: p => lean(2 * sin(p)), pos: p => [0, -.2 + .25 * sin(p), 0], scl: p => [1 + .015 * sin(p), 1, 1 + .015 * sin(p)] },
      head: { rot: p => lean(-10 + 4 * sin(p), 12 * sin(p, .5), 5 * sin(p, 1, .3)) },
      arm_l: { rot: p => limb(30 + 4 * sin(p), 0, -8) }, arm_r: { rot: p => limb(30 + 4 * sin(p, 1, .5), 0, 8) },
      sacs_glow: { scl: p => { const k = 1 + .05 * Math.max(0, sin(p, 2)); return [k, k, k]; } },
    }),
    A("walk", 1.8, {
      root: { pos: p => [0, .7 * Math.abs(sin(p)), 0] },
      body: { rot: p => lean(6 + 2 * sin(p, 2), 7 * sin(p), 4 * sin(p)) },
      head: { rot: p => lean(-14, -6 * sin(p)) },
      leg_l: { rot: p => limb(26 * sin(p)) }, leg_r: { rot: p => limb(-26 * sin(p)) },
      arm_l: { rot: p => limb(32 - 22 * sin(p), 0, -10) }, arm_r: { rot: p => limb(32 + 22 * sin(p), 0, 10) },
    }),
    A("eat", .8, { // face beam: head drawn back while it charges (0-.6), then thrust forward as it fires
      body: { rot: p => lean(p < .6 ? -6 * ease(p / .6) : -6 + 14 * ease(Math.min(1, (p - .6) / .1))) },
      head: { rot: p => lean(p < .6 ? -22 * ease(p / .6) : -22 + 34 * ease(Math.min(1, (p - .6) / .1))), scl: p => { const k = 1 + .06 * Math.max(0, sin(p, 4)); return [k, k, k]; } },
      sacs_glow: { scl: p => { const k = 1 + (p < .6 ? .16 * p / .6 : .16 - .16 * (p - .6) / .4); return [k, k, k]; } },
      arm_l: { rot: p => limb(24, 0, -14) }, arm_r: { rot: p => limb(24, 0, 14) },
    }, { n: 24 }),
    A("work", 1, {
      body: { rot: p => lean(p < .5 ? -14 * ease(p / .5) : p < .62 ? -14 + 34 * ease((p - .5) / .12) : 20 - 20 * ease((p - .62) / .38)) },
      head: { rot: p => lean(-10) },
      arm_l: { rot: p => limb(pound(p), 0, -10) }, arm_r: { rot: p => limb(pound(p), 0, 10) },
      leg_l: { rot: p => limb(14) }, leg_r: { rot: p => limb(-10) },
    }, { n: 30 }),
  ];
  save(m, outliner, anims);
}

const ALL = { runner, bomber, acolyte, knight, wizard, keeper, tentacle, ogre };
const only = process.argv.slice(2);
for (const [k, f] of Object.entries(ALL)) if (!only.length || only.includes(k)) f();
void mix;
