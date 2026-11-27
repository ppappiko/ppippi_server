// Rewrites the idle / walk / work / eat / hurt animations of each pet .bbmodel with a motion set that fits the pet's concept.
//   node dev/art/make-pet-anims.mjs            (all pets that have a spec below)
//   node dev/art/make-pet-anims.mjs fox cat    (only those)
// Geometry, textures and bones are untouched; only the "animations" array is replaced.
// Conventions (Blockbench values, see PetModels.java): rot x+ = pitch nose/limb-tip DOWN for forward-pointing parts,
// leg x+ = foot swings BACK, tail x+ = tail tip UP; left-side wing z− = up, right-side wing z+ = up; pos z+ = backwards.
import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const petDir = path.resolve(here, "../pipi-facilities/assets/pipifacilities/pets");

// ---------------------------------------------------------------- helpers
const TAU = Math.PI * 2;
const sin = (p, k = 1, ph = 0) => Math.sin(TAU * (k * p + ph));
const cos = (p, k = 1, ph = 0) => Math.cos(TAU * (k * p + ph));
/** smooth bump: 0 outside [a,b], 1 at the middle (raised cosine). */
const bump = (p, a, b) => (p <= a || p >= b ? 0 : .5 - .5 * Math.cos(TAU * (p - a) / (b - a)));
/** plateau: eases 0→1 over [a,a+r], holds, eases 1→0 over [b−r,b]. */
const hold = (p, a, b, r = .08) => (p <= a || p >= b ? 0 : p < a + r ? .5 - .5 * Math.cos(Math.PI * (p - a) / r) : p > b - r ? .5 - .5 * Math.cos(Math.PI * (b - p) / r) : 1);
/** one hop arc over [a,b] (0 at the ends, 1 in the middle, parabolic). */
const arc = (p, a = 0, b = 1) => (p <= a || p >= b ? 0 : 1 - Math.pow(2 * (p - a) / (b - a) - 1, 2));
const z3 = () => [0, 0, 0];

/** Build one animation. bones: { boneName: { rot?: p=>[x,y,z], pos?: p=>[x,y,z], scl?: p=>[x,y,z], n?: samples, lin?: true } } */
function A(name, length, bones, { loop = "loop", n: nDefault } = {}) {
  return { name, length, loop, bones, nDefault };
}


// rear-up geometry: body pitched by file-rotation ang (deg, negative = front up) around its origin bo
const Rx = (v, ang) => { const t = -ang * Math.PI / 180, c = Math.cos(t), sn = Math.sin(t); return [v[0], v[1] * c - v[2] * sn, v[1] * sn + v[2] * c]; };
/** body position that keeps 'pivot' (e.g. the hips) still while the body pitches by ang around bo */
const pivotPos = (bo, pivot, ang) => { const r = [0, pivot[1] - bo[1], pivot[2] - bo[2]], q = Rx(r, ang); return [0, r[1] - q[1], r[2] - q[2]]; };
/** where a root-level limb (origin lo) must move so it stays on a body pitched by ang (+ body offset bp) */
const attachPos = (bo, lo, ang, bp = [0, 0, 0]) => { const r = [0, lo[1] - bo[1], lo[2] - bo[2]], q = Rx(r, ang); return [0, q[1] - r[1] + bp[1], q[2] - r[2] + bp[2]]; };
const lerp3 = (a, k) => a.map(v => v * k);

// ---------------------------------------------------------------- reusable motion pieces
const breathe = (amp = .025, k = 1) => ({ scl: p => [1 + amp * .5 * (1 + sin(p, k)), 1 + amp * (1 + sin(p, k)) * .5, 1 + amp * .5 * (1 + sin(p, k))] });
const trotLegs = (amp, opts = {}) => {
  const { lift = 0, lateral = false } = opts; // lateral: same-side legs in phase (pace), else diagonal (trot)
  const ph = { leg_fl: 0, leg_br: lateral ? .5 : 0, leg_fr: .5, leg_bl: lateral ? 0 : .5 };
  const o = {};
  for (const [b, f] of Object.entries(ph)) o[b] = { rot: p => [amp * sin(p, 1, f), 0, 0], ...(lift ? { pos: p => [0, lift * Math.max(0, sin(p, 1, f + .25)), 0] } : {}) };
  return o;
};
const hurtSquash = (body = "body", head = "head", extra = {}) => A("hurt", .4, {
  [body]: { scl: p => { const e = 1 - p; return [1 + .12 * e * cos(p, .5), 1 - .14 * e * cos(p, .5), 1 + .12 * e * cos(p, .5)]; }, rot: p => [0, 0, 8 * sin(p, 1.5) * (1 - p)] },
  [head]: { rot: p => [-25 * (1 - p) * cos(p, .5), 0, 0] },
  ...extra,
}, { loop: "once", n: 6 });

const MB_O = [0, 6, 0], MB_HIP = [0, 4, 3.5], MB_L = [-2.5, 4, -3.5];
const mbBody = ang => ({ rot: [ang, 0, 0], pos: pivotPos(MB_O, MB_HIP, ang) });
const mbLeg = (ang, extra = 0) => ({ rot: [ang + extra, 0, 0], pos: attachPos(MB_O, MB_L, ang, pivotPos(MB_O, MB_HIP, ang)) });
// ---------------------------------------------------------------- species

// ---------------------------------------------------------------- templates for the 13 new pets (2026-11-20)
/** Four-legged pet. o: { walk: len, amp: leg swing, bounce, head: [idle nod], tail: wag deg, work: bones, eat: bones, extra: {idle/walk bones} } */
const quad = o => {
  const amp = o.amp ?? 25, wl = o.walk ?? 1;
  return [
    A("idle", 4, { body: { ...breathe(.03, 2), n: 40 }, head: { rot: p => [5 * sin(p, 1) + (o.look ?? 0) * hold(p, .55, .75), 10 * sin(p, 1, .25) * (1 - hold(p, .55, .75)), 0], n: 60 },
      ...(o.tail !== 0 ? { tail: { rot: p => [0, (o.tail ?? 18) * sin(p, 3) * (hold(p, .1, .3) + hold(p, .6, .8)), 0], n: 60 } } : {}), ...(o.idle || {}) }),
    A("walk", wl, { ...(o.bounce ? { root: { pos: p => [0, o.bounce * Math.abs(sin(p, 2)), 0], n: 20 } } : {}), body: { rot: p => [2 * sin(p, 2), 0, 3 * sin(p, 1)], n: 20 },
      ...trotLegs(amp), head: { rot: p => [-5 * sin(p, 2), 3 * sin(p, 1), 0], n: 20 }, ...(o.tail !== 0 ? { tail: { rot: p => [0, (o.tail ?? 18) * sin(p, 2), 0], n: 20 } } : {}), ...(o.walkExtra || {}) }),
    A("work", o.workLen ?? 2, o.work),
    A("eat", o.eatLen ?? 1.2, o.eat ?? { head: { rot: p => [40 + 6 * Math.max(0, sin(p, 3)), 0, 0], n: 24 }, ...(o.tail !== 0 ? { tail: { rot: p => [0, 10 * sin(p, 2), 0], n: 12 } } : {}) }),
    hurtSquash("body", "head", o.hurt || {}),
  ];
};
/** Two-legged pet (leg_l / leg_r; wings or arms as wing_l/wing_r or arm_l/arm_r). */
const biped = o => {
  const L = o.arms ? ["arm_l", "arm_r"] : ["wing_l", "wing_r"], amp = o.amp ?? 30, wl = o.walk ?? .8, wad = o.waddle ?? 0;
  return [
    A("idle", 4, { body: { ...breathe(.03, 2), rot: p => [0, 0, 1.5 * sin(p, 1)], n: 40 }, head: { rot: p => [4 * sin(p, 1), 18 * sin(p, 1, .25) * hold(p, .3, .7), 0], n: 60 },
      [L[0]]: { rot: p => [0, 0, -(o.flap ?? 12) * (bump(p, .8, .86) + bump(p, .87, .93))], n: 80 }, [L[1]]: { rot: p => [0, 0, (o.flap ?? 12) * (bump(p, .8, .86) + bump(p, .87, .93))], n: 80 }, ...(o.idle || {}) }),
    A("walk", wl, { root: { pos: p => [0, (o.bounce ?? .6) * Math.abs(sin(p, 2)), 0], n: 20 }, body: { rot: p => [3 * sin(p, 2), 0, (5 + wad) * sin(p, 1)], n: 20 },
      leg_l: { rot: p => [amp * sin(p, 1), 0, 0], n: 20 }, leg_r: { rot: p => [amp * sin(p, 1, .5), 0, 0], n: 20 },
      [L[0]]: { rot: p => [o.arms ? -amp * .8 * sin(p, 1) : 0, 0, o.arms ? 0 : -8 - 6 * sin(p, 2)], n: 20 }, [L[1]]: { rot: p => [o.arms ? -amp * .8 * sin(p, 1, .5) : 0, 0, o.arms ? 0 : 8 + 6 * sin(p, 2)], n: 20 },
      head: { rot: p => [(o.headBob ?? 4) * sin(p, 2), 0, 0], n: 20 }, ...(o.walkExtra || {}) }),
    A("work", o.workLen ?? 2, o.work),
    A("eat", o.eatLen ?? 1.2, o.eat ?? { head: { rot: p => [35 + 8 * Math.max(0, sin(p, 4)), 0, 0], n: 24 } }),
    hurtSquash("body", "head", o.hurt || {}),
  ];
};
/** Six-legged pet (leg_l1..3, leg_r1..3): tripod gait. */
const sixLegs = (amp, lift) => { const o = {}; for (const k of [1, 2, 3]) for (const s of ["l", "r"]) { const ph = ((k + (s === "l" ? 0 : 1)) % 2) * .5; o[`leg_${s}${k}`] = { rot: p => [0, amp * sin(p, 1, ph), (s === "l" ? -1 : 1) * lift * Math.max(0, sin(p, 1, ph + .25))], n: 20 }; } return o; };
const sixIdle = () => { const o = {}; for (const k of [1, 2, 3]) for (const s of ["l", "r"]) o[`leg_${s}${k}`] = { rot: p => [0, 4 * sin(p, 1, k * .2), 0], n: 20 }; return o; };

const NEW_PETS = {
  // 댐돌이: waddling gnawer — work = slaps its paddle tail on a log, eat = gnaws fast
  beaver: () => quad({ amp: 22, walk: 1, bounce: .4, tail: 0,
    idle: { tail: { rot: p => [-8 * sin(p, 1) - 25 * bump(p, .6, .7), 0, 0], n: 60 } },
    walkExtra: { tail: { rot: p => [-6 * sin(p, 2), 8 * sin(p, 1), 0], n: 20 } },
    work: { body: { rot: p => [-10 * hold(p, .1, .9), 0, 0], n: 40 }, tail: { rot: p => [-50 * Math.max(0, sin(p, 4)), 0, 0], n: 60 }, head: { rot: p => [15 + 6 * sin(p, 8), 0, 0], n: 60 } },
    eat: { head: { rot: p => [30 + 5 * sin(p, 10), 0, 0], n: 40 }, tail: { rot: () => [-10, 0, 0], n: 2 } } }),
  // 집게: sideways scuttle, eye stalks twitch, work = big claw snapping on its load
  hermitcrab: () => [
    A("idle", 4, { body: { ...breathe(.02, 2), n: 30 }, eye_l: { rot: p => [10 * bump(p, .2, .3), 0, -10 * sin(p, 1)], n: 60 }, eye_r: { rot: p => [10 * bump(p, .6, .7), 0, 10 * sin(p, 1, .3)], n: 60 },
      claw_l: { rot: p => [-20 * (bump(p, .4, .46) + bump(p, .48, .54)), 0, 0], n: 80 }, ...sixIdle() }),
    A("walk", .6, { root: { pos: p => [0, .3 * Math.abs(sin(p, 2)), 0], n: 20 }, body: { rot: p => [0, 0, 4 * sin(p, 2)], n: 20 }, ...sixLegs(20, 18), shell: { rot: p => [0, 0, -3 * sin(p, 2)], n: 20 } }),
    A("work", 2, { claw_l: { rot: p => [-35 * Math.max(0, sin(p, 3)), 15 * sin(p, 3), 0], n: 60 }, claw_r: { rot: p => [-20 * Math.max(0, sin(p, 3, .5)), 0, 0], n: 60 }, shell: { rot: p => [5 * sin(p, 3), 0, 0], n: 30 }, eye_l: { rot: p => [-10, 0, 0], n: 2 }, eye_r: { rot: p => [-10, 0, 0], n: 2 } }),
    A("eat", 1.2, { claw_l: { rot: p => [-40 - 20 * Math.max(0, sin(p, 4)), 20, 0], n: 24 }, claw_r: { rot: p => [-40 - 20 * Math.max(0, sin(p, 4, .5)), -20, 0], n: 24 } }),
    A("hurt", .4, { shell: { pos: p => [0, -1.5 * (1 - p), 0], n: 6 }, eye_l: { rot: p => [40 * (1 - p), 0, 0], n: 6 }, eye_r: { rot: p => [40 * (1 - p), 0, 0], n: 6 } }, { loop: "once", n: 6 }),
  ],
  // 펭이: side-to-side waddle, flippers out for balance, work = pulls (leans into a harness), eat = gulps fish
  penguin: () => biped({ amp: 18, walk: .7, waddle: 7, bounce: .3, flap: 25,
    work: { body: { rot: p => [12 * hold(p, .05, .95) + 3 * sin(p, 4), 0, 0], n: 40 }, leg_l: { rot: p => [20 * sin(p, 2), 0, 0], n: 40 }, leg_r: { rot: p => [20 * sin(p, 2, .5), 0, 0], n: 40 },
      wing_l: { rot: p => [0, 0, -30], n: 2 }, wing_r: { rot: p => [0, 0, 30], n: 2 }, scarf: { rot: p => [-10 * sin(p, 4), 0, 0], n: 40 } },
    eat: { head: { rot: p => [-25 + 25 * bump(p, .3, .6), 0, 0], n: 24 }, wing_l: { rot: p => [0, 0, -20 * Math.abs(sin(p, 2))], n: 24 }, wing_r: { rot: p => [0, 0, 20 * Math.abs(sin(p, 2))], n: 24 } } }),
  // 하얀뿔: graceful, ear flicks, work = lowers antlers and pushes, eat = browses leaves high
  deer: () => quad({ amp: 28, walk: 1.1, tail: 25, look: -12,
    idle: { ...{ tail: { rot: p => [-20 * (bump(p, .3, .35) + bump(p, .36, .41)), 0, 0], n: 80 } } },
    work: { head: { rot: p => [35 * hold(p, .1, .8) + 6 * sin(p, 6) * hold(p, .2, .7), 0, 0], n: 60 }, body: { pos: p => [0, 0, -1.5 * hold(p, .2, .7)], rot: p => [6 * hold(p, .1, .8), 0, 0], n: 40 }, leg_bl: { rot: p => [15 * hold(p, .2, .7), 0, 0], n: 40 }, leg_br: { rot: p => [15 * hold(p, .2, .7), 0, 0], n: 40 } },
    eat: { head: { rot: p => [-25 + 4 * sin(p, 4), 6 * sin(p, 1), 0], n: 24 }, body: { rot: p => [-5, 0, 0], n: 2 } } }),
  // 깃털이: long proud strides, head bobbing, work = sprinting in place with wings out, eat = pecks the ground
  ostrich: () => biped({ amp: 35, walk: .8, bounce: 1, flap: 30, headBob: 10,
    walkExtra: { tail: { rot: p => [6 * sin(p, 2), 0, 0], n: 20 } },
    work: { root: { pos: p => [0, 1.5 * Math.abs(sin(p, 4)), 0], n: 40 }, leg_l: { rot: p => [45 * sin(p, 2), 0, 0], n: 40 }, leg_r: { rot: p => [45 * sin(p, 2, .5), 0, 0], n: 40 },
      wing_l: { rot: p => [0, 0, -45 - 15 * sin(p, 4)], n: 40 }, wing_r: { rot: p => [0, 0, 45 + 15 * sin(p, 4)], n: 40 }, head: { rot: p => [-15, 0, 0], n: 2 } }, workLen: 1,
    eat: { head: { rot: p => [70 * bump(p, .1, .45) + 70 * bump(p, .5, .85), 0, 0], n: 30 }, body: { rot: p => [12, 0, 0], n: 2 } } }),
  // 뿔소: slow heavy stomp, snorts, work = lowers head and rams forward
  rhino: () => quad({ amp: 16, walk: 1.4, bounce: .5, tail: 30, look: 6,
    idle: { body: { pos: p => [0, -.4 * bump(p, .7, .8), 0], n: 40 }, leg_fl: { rot: p => [-20 * (bump(p, .74, .8) + bump(p, .84, .9)), 0, 0], n: 80 } },
    work: { body: { pos: p => [0, 0, 3 * hold(p, 0, .35) - 6 * bump(p, .35, .55)], rot: p => [5 * hold(p, 0, .35) - 4 * bump(p, .35, .55), 0, 0], n: 60 }, head: { rot: p => [25 * hold(p, 0, .6) - 10 * bump(p, .4, .55), 0, 0], n: 60 },
      leg_fl: { rot: p => [-25 * bump(p, .05, .15) - 25 * bump(p, .18, .28), 0, 0], n: 60 } },
    eat: { head: { rot: p => [35 + 4 * sin(p, 3), 0, 0], n: 24 } } }),
  // 망고: bouncy knuckle-walk, curious head tilts, work = tinkers with both hands, eat = bites the mango
  monkey: () => biped({ arms: true, amp: 30, walk: .7, bounce: .8,
    idle: { tail: { rot: p => [0, 20 * sin(p, 1), 10 * sin(p, 2)], n: 40 }, head: { rot: p => [5 * sin(p, 1), 0, 15 * hold(p, .3, .5) - 15 * hold(p, .6, .8)], n: 60 } },
    walkExtra: { tail: { rot: p => [0, 15 * sin(p, 2), 0], n: 20 } },
    work: { arm_l: { rot: p => [-60 + 15 * sin(p, 4), 0, 15], n: 40 }, arm_r: { rot: p => [-60 + 15 * sin(p, 4, .5), 0, -15], n: 40 }, head: { rot: p => [20 + 5 * sin(p, 2), 8 * sin(p, 1), 0], n: 40 }, tail: { rot: p => [0, 25 * sin(p, 2), 0], n: 40 } },
    eat: { arm_r: { rot: p => [-100 + 10 * sin(p, 3), 0, -20], n: 24 }, head: { rot: p => [10 + 6 * sin(p, 6), 0, 0], n: 24 } } }),
  // 밤눈: head swivels, eyes blink (glow), work = writes/reads hopping on one foot, eat = gulps
  owl: () => biped({ amp: 20, walk: .7, bounce: 1.2, flap: 40,
    idle: { head: { rot: p => [0, 70 * hold(p, .15, .35) - 70 * hold(p, .55, .75), 0], n: 80 }, eyes_glow: { scl: p => [1, 1 - .9 * bump(p, .45, .5), 1], n: 80 } },
    work: { head: { rot: p => [25 + 5 * sin(p, 4), 10 * sin(p, 1), 0], n: 40 }, wing_l: { rot: p => [-40, 0, -20 - 10 * sin(p, 4)], n: 40 }, wing_r: { rot: p => [-40, 0, 20 + 10 * sin(p, 4, .5)], n: 40 }, eyes_glow: { scl: p => [1 + .15 * sin(p, 2), 1 + .15 * sin(p, 2), 1], n: 20 } },
    eat: { head: { rot: p => [-20 * bump(p, .1, .4) + 30 * bump(p, .5, .8), 0, 0], n: 24 } } }),
  // 도토리: hop-hop scurry, tail flicks, work = stashing (digs fast), eat = nibbles the acorn, cheeks puff
  squirrel: () => quad({ amp: 35, walk: .5, bounce: 1.3, tail: 0,
    idle: { tail: { rot: p => [-10 * sin(p, 2) - 25 * bump(p, .5, .58), 0, 0], n: 60 }, cheeks: { scl: p => { const s = 1 + .5 * hold(p, .2, .45); return [s, s, s]; }, n: 40 } },
    walkExtra: { tail: { rot: p => [-15 * sin(p, 2), 0, 0], n: 20 } },
    work: { body: { rot: p => [15, 0, 0], n: 2 }, leg_fl: { rot: p => [-50 * sin(p, 6), 0, 0], n: 60 }, leg_fr: { rot: p => [-50 * sin(p, 6, .5), 0, 0], n: 60 }, tail: { rot: p => [-20 * sin(p, 3), 0, 0], n: 30 } },
    eat: { body: { rot: () => [-25, 0, 0], n: 2 }, head: { rot: p => [10 + 4 * sin(p, 8), 0, 0], n: 40 }, leg_fl: { rot: () => [-70, 0, 0], n: 2 }, leg_fr: { rot: () => [-70, 0, 0], n: 2 }, cheeks: { scl: p => { const s = 1.6 + .2 * sin(p, 4); return [s, s, s]; }, n: 24 } } }),
  // 뾰족: tiny shuffling legs, sniffs, work = rolls into a spiky ball and rumbles, eat = snuffles
  hedgehog: () => quad({ amp: 35, walk: .5, bounce: .2, tail: 0,
    idle: { head: { rot: p => [6 * sin(p, 6) * hold(p, .2, .4), 0, 0], n: 80 } },
    work: { body: { rot: p => [360 * hold(p, .25, .75) * p, 0, 0], scl: p => { const s = 1 - .15 * hold(p, .1, .9); return [s, s, s]; }, n: 60, lin: true }, head: { rot: p => [60 * hold(p, .1, .9), 0, 0], n: 30 },
      leg_fl: { scl: p => { const s = 1 - hold(p, .1, .9); return [s, s, s]; }, n: 20 }, leg_fr: { scl: p => { const s = 1 - hold(p, .1, .9); return [s, s, s]; }, n: 20 }, leg_bl: { scl: p => { const s = 1 - hold(p, .1, .9); return [s, s, s]; }, n: 20 }, leg_br: { scl: p => { const s = 1 - hold(p, .1, .9); return [s, s, s]; }, n: 20 } },
    eat: { head: { rot: p => [25 + 8 * sin(p, 8), 6 * sin(p, 2), 0], n: 40 } } }),
  // 뭉게: floats and bobs, puffs breathe, work = spins slowly like drifting cloud, eat = nibbles a cloud tuft
  cloudsheep: () => quad({ amp: 15, walk: 1.2, tail: 10,
    idle: { root: { pos: p => [0, 1 + .8 * sin(p, 1), 0], n: 40 }, body: { ...breathe(.06, 2), n: 40 } },
    walkExtra: { root: { pos: p => [0, 1 + .5 * sin(p, 2), 0], n: 20 } },
    work: { root: { pos: p => [0, 2 + sin(p, 2), 0], n: 40 }, body: { rot: p => [0, 360 * p, 0], scl: p => { const s = 1 + .08 * sin(p, 4); return [s, s, s]; }, n: 60, lin: true } },
    eat: { root: { pos: () => [0, .5, 0], n: 2 }, head: { rot: p => [35 + 6 * sin(p, 4), 0, 0], n: 24 } } }),
  // 톱니: ticking mechanical gait, gear and key always turning, work = winds itself (key spins fast, sparks via glow)
  clockfox: () => quad({ amp: 24, walk: .8, bounce: .3, tail: 20,
    idle: { gear: { rot: p => [360 * p, 0, 0], n: 40, lin: true }, key: { rot: p => [45 * Math.floor(p * 8), 0, 0], n: 32, lin: true }, eyes_glow: { scl: p => [1, 1 - .9 * bump(p, .7, .74), 1], n: 80 } },
    walkExtra: { gear: { rot: p => [360 * p, 0, 0], n: 20, lin: true }, key: { rot: p => [180 * p, 0, 0], n: 20, lin: true } },
    work: { key: { rot: p => [720 * p, 0, 0], n: 40, lin: true }, gear: { rot: p => [-720 * p, 0, 0], n: 40, lin: true }, body: { rot: p => [0, 0, 3 * sin(p, 8)], n: 40 }, eyes_glow: { scl: p => { const s = 1 + .4 * Math.abs(sin(p, 4)); return [s, s, 1]; }, n: 40 } },
    eat: { head: { rot: p => [35 + 5 * sin(p, 6), 0, 0], n: 24 }, gear: { rot: p => [360 * p, 0, 0], n: 24, lin: true } } }),
  // 꿀벌찌: hovers with buzzing wings, work = flits between flowers (bobs + spins), eat = sips from the honey pot
  bee: () => biped({ amp: 10, walk: .6, bounce: .6, flap: 70,
    idle: { root: { pos: p => [0, 1.5 + .6 * sin(p, 2), 0], n: 40 }, wing_l: { rot: p => [0, 0, -30 * sin(p, 16)], n: 80 }, wing_r: { rot: p => [0, 0, 30 * sin(p, 16)], n: 80 } },
    walkExtra: { root: { pos: p => [0, 1.5 + .4 * sin(p, 4), 0], n: 20 }, wing_l: { rot: p => [0, 0, -35 * sin(p, 8)], n: 40 }, wing_r: { rot: p => [0, 0, 35 * sin(p, 8)], n: 40 } },
    work: { root: { pos: p => [1.5 * sin(p, 1), 2 + sin(p, 2), 0], n: 40 }, body: { rot: p => [0, 30 * sin(p, 1), 0], n: 40 }, wing_l: { rot: p => [0, 0, -35 * sin(p, 20)], n: 80 }, wing_r: { rot: p => [0, 0, 35 * sin(p, 20)], n: 80 } },
    eat: { head: { rot: p => [30 + 6 * sin(p, 6), 0, 0], n: 24 }, wing_l: { rot: p => [0, 0, -20 * sin(p, 10)], n: 24 }, wing_r: { rot: p => [0, 0, 20 * sin(p, 10)], n: 24 } } }),
  // 딱따구: hops, crest bobs, work = rapid pecking at the trunk, eat = pecks a grub
  woodpecker: () => biped({ amp: 18, walk: .55, bounce: 1.4, flap: 30,
    work: { head: { rot: p => [-10 + 35 * Math.max(0, sin(p, 12)), 0, 0], n: 80 }, body: { rot: p => [8 * Math.max(0, sin(p, 12)), 0, 0], n: 80 }, tail: { rot: () => [35, 0, 0], n: 2 } },
    eat: { head: { rot: p => [40 * bump(p, .1, .3) + 40 * bump(p, .5, .7), 0, 0], n: 24 } } }),
  // 토실: scurries, cheeks puff, work = hammers busily with its front paws, eat = nibbles the seed
  hamster: () => quad({ amp: 32, walk: .45, bounce: .9, tail: 0,
    idle: { head: { rot: p => [6 * sin(p, 8) * hold(p, .2, .35), 0, 0], n: 80 } },
    work: { body: { rot: () => [-20, 0, 0], n: 2 }, leg_fl: { rot: p => [-70 + 35 * Math.max(0, sin(p, 6)), 0, 0], n: 60 }, leg_fr: { rot: p => [-70 + 35 * Math.max(0, sin(p, 6, .5)), 0, 0], n: 60 }, head: { rot: p => [10, 8 * sin(p, 1), 0], n: 40 } },
    eat: { body: { rot: () => [-25, 0, 0], n: 2 }, head: { rot: p => [12 + 5 * sin(p, 10), 0, 0], n: 40 }, leg_fl: { rot: () => [-75, 0, 0], n: 2 }, leg_fr: { rot: () => [-75, 0, 0], n: 2 } } }),
  // 포포: floats and twirls, wings shimmer, work = waves the star wand, eat = holds a star crumb and nibbles
  fairy: () => biped({ amp: 8, walk: .8, bounce: .4, flap: 50,
    idle: { root: { pos: p => [0, 2 + .8 * sin(p, 1), 0], n: 40 }, wing_l: { rot: p => [0, 25 + 18 * sin(p, 2), 0], n: 60 }, wing_r: { rot: p => [0, -25 - 18 * sin(p, 2), 0], n: 60 }, eyes_glow: { rot: p => [0, 360 * p, 0], n: 40, lin: true } },
    walkExtra: { root: { pos: p => [0, 2 + .5 * sin(p, 2), 0], n: 20 }, wing_l: { rot: p => [0, 30 + 28 * sin(p, 1), 0], n: 40 }, wing_r: { rot: p => [0, -30 - 28 * sin(p, 1), 0], n: 40 } },
    work: { root: { pos: p => [0, 2.5 + sin(p, 2), 0], n: 40 }, body: { rot: p => [0, 360 * p, 0], n: 60, lin: true }, wand_glow: { rot: p => [0, 0, 40 * sin(p, 4)], scl: p => { const s = 1 + .4 * Math.max(0, sin(p, 4)); return [s, s, s]; }, n: 60 } },
    eat: { root: { pos: () => [0, 1.5, 0], n: 2 }, head: { rot: p => [20 + 5 * sin(p, 6), 0, 0], n: 24 } } }),
  // 기사단장: marching tripod gait, cape sways, work = salutes and strikes with the great horn, eat = bows head
  beetle: () => [
    A("idle", 4, { body: { ...breathe(.02, 2), n: 30 }, head: { rot: p => [-8 * hold(p, .4, .6), 0, 0], n: 60 }, cape: { rot: p => [6 + 4 * sin(p, 2), 0, 0], n: 40 }, ...sixIdle() }),
    A("walk", .7, { root: { pos: p => [0, .3 * Math.abs(sin(p, 2)), 0], n: 20 }, body: { rot: p => [0, 0, 2 * sin(p, 2)], n: 20 }, ...sixLegs(18, 12), cape: { rot: p => [15 + 6 * sin(p, 2), 0, 0], n: 20 } }),
    A("work", 2, { head: { rot: p => [-30 * hold(p, .05, .35) + 25 * bump(p, .35, .55), 0, 0], n: 60 }, body: { rot: p => [-8 * hold(p, .05, .35) + 8 * bump(p, .35, .55), 0, 0], pos: p => [0, 0, -2 * bump(p, .35, .55)], n: 60 }, cape: { rot: p => [10 + 20 * bump(p, .35, .6), 0, 0], n: 40 } }),
    A("eat", 1.2, { head: { rot: p => [20 + 5 * sin(p, 4), 0, 0], n: 24 } }),
    hurtSquash("body", "head", { cape: { rot: p => [40 * (1 - p), 0, 0] } }),
  ],
};

const SPECS = {
  ...NEW_PETS,
  // 짱새 — tiny forest sparrow: hops on both feet, twitchy head, rapid pecking
  sparrow: () => [
    A("idle", 3.5, {
      head: { rot: p => [32 * (bump(p, .30, .38) + bump(p, .40, .48)), 35 * hold(p, .05, .22, .02) - 30 * hold(p, .55, .72, .02), 0], n: 70, lin: true },
      tail: { rot: p => [18 * (bump(p, .12, .17) + bump(p, .8, .85)), 0, 0], n: 70 },
      crest: { rot: p => [-15 * bump(p, .6, .7), 0, 0], n: 35 },
      wing_l: { rot: p => [0, 0, -25 * bump(p, .88, .95)], n: 70 },
      wing_r: { rot: p => [0, 0, 25 * bump(p, .88, .95)], n: 70 },
      body: { scl: p => [1 + .03 * sin(p, 3), 1 + .03 * sin(p, 3), 1], n: 24 },
    }),
    // flying: the renderer lifts the sparrow ~1 block while it moves or works (PetRenderers hover); here it beats its wings
    A("walk", .7, {
      root: { pos: p => [0, 1.8 * sin(p, 2, .25), 0], rot: p => [14, 0, 6 * sin(p, 1)], n: 16 },
      wing_l: { rot: p => [0, 10 * sin(p, 2, .25), -(5 + 50 * sin(p, 2))], n: 16 }, wing_r: { rot: p => [0, -10 * sin(p, 2, .25), 5 + 50 * sin(p, 2)], n: 16 },
      leg_l: { rot: () => [70, 0, 0] }, leg_r: { rot: () => [70, 0, 0] },
      tail: { rot: p => [-10 + 8 * sin(p, 4, .3), 0, 0], n: 24 },
      head: { rot: p => [-12 + 3 * sin(p, 2), 0, 0], n: 24 },
    }),
    A("work", 1, {
      root: { pos: p => [0, 2.5 * sin(p, 1) - 1, -1.5 * sin(p, 1, .25)], rot: p => [10 * sin(p, 1, .25), 0, 10 * sin(p, 1)], n: 24 },
      body: { rot: p => [18 + 6 * sin(p, 4), 0, 0], n: 16 },
      head: { rot: p => [45 * Math.max(0, sin(p, 4)), 0, 0], n: 32 },
      wing_l: { rot: p => [0, 10 * sin(p, 4, .25), -(10 + 45 * sin(p, 4))], n: 32 }, wing_r: { rot: p => [0, -10 * sin(p, 4, .25), 10 + 45 * sin(p, 4)], n: 32 },
      leg_l: { rot: () => [55, 0, 0] }, leg_r: { rot: () => [55, 0, 0] },
      tail: { rot: p => [20 + 10 * sin(p, 4), 0, 0], n: 16 },
    }),
    A("eat", .8, { body: { rot: () => [18, 0, 0] }, head: { rot: p => [55 * Math.max(0, sin(p, 2)), 0, 0], n: 16 }, tail: { rot: p => [15 * sin(p, 2), 0, 0], n: 8 } }),
    hurtSquash("body", "head", { wing_l: { rot: p => [0, 0, -60 * (1 - p)] }, wing_r: { rot: p => [0, 0, 60 * (1 - p)] } }),
  ],

  // 가마 — samurai frog: leaps, puffs its throat, slashes with the blade
  frog: () => [
    A("idle", 4, {
      body: { scl: p => [1 + .07 * bump(p, .2, .32) + .07 * bump(p, .34, .46), 1, 1 + .05 * bump(p, .2, .32) + .05 * bump(p, .34, .46)], n: 48 },
      head: { rot: p => [0, 14 * hold(p, .55, .8), -6 * hold(p, .55, .8)], n: 32 },
      hat: { rot: p => [0, 0, 8 * bump(p, .85, .95)], n: 32 },
      arm_r: { rot: p => [-70 * hold(p, .82, .97), 0, 0], n: 32 },
    }),
    A("walk", .8, {
      root: { pos: p => [0, 4.5 * arc(p, .2, .75), 0], rot: p => [-18 * arc(p, .2, .55) + 10 * bump(p, .55, .85), 0, 0], n: 16 },
      body: { scl: p => { const c = bump(p, 0, .22) + bump(p, .72, 1); return [1 + .1 * c, 1 - .14 * c, 1 + .06 * c]; }, n: 16 },
      leg_l: { rot: p => [-25 * bump(p, 0, .22) + 60 * arc(p, .18, .7), 0, 0], n: 16 }, leg_r: { rot: p => [-25 * bump(p, 0, .22) + 60 * arc(p, .18, .7), 0, 0], n: 16 },
      arm_l: { rot: p => [-40 * arc(p, .2, .7), 0, -20 * arc(p, .2, .7)], n: 16 }, arm_r: { rot: p => [-40 * arc(p, .2, .7), 0, 20 * arc(p, .2, .7)], n: 16 },
    }),
    A("work", 1.8, {
      body: { rot: p => [0, -25 * bump(p, .05, .3) + 30 * bump(p, .35, .6) - 10 * hold(p, .7, .95), 0], n: 36 },
      arm_r: { rot: p => [-110 * bump(p, .02, .22) + 20 * bump(p, .2, .32) - 120 * bump(p, .32, .5) + 10 * bump(p, .5, .62) - 90 * hold(p, .7, .95), 0, 30 * bump(p, .32, .5)], n: 36 },
      blade_work: { rot: p => [0, -70 * bump(p, .02, .28) + 80 * bump(p, .3, .56), -50 * bump(p, .32, .5) + 20 * hold(p, .7, .95)], n: 36 },
      arm_l: { rot: p => [-50 * hold(p, .68, .96), 0, -15], n: 18 },
      root: { pos: p => [0, 1.5 * arc(p, .3, .45), -2 * bump(p, .3, .55)], n: 36 },
      head: { rot: p => [10 * hold(p, .7, .95), 0, 0], n: 18 },
    }),
    A("eat", 1.2, {
      tongue_eat: { pos: p => [0, 0, -5 * bump(p, .05, .35)], scl: p => [1, 1, 1 + 3 * bump(p, .05, .35)], n: 24 },
      head: { rot: p => [-10 * bump(p, 0, .35) + 6 * Math.max(0, sin(p, 4, .3)) * (p > .4 ? 1 : 0), 0, 0], n: 24 },
      body: { scl: p => [1 + .05 * Math.max(0, sin(p, 4)) * (p > .4 ? 1 : 0), 1, 1], n: 24 },
    }),
    hurtSquash("body", "head", { hat: { rot: p => [-20 * (1 - p), 0, 15 * (1 - p)] } }),
  ],

  // 검은고양이 — sleek black cat: slinky pace with an S-curving tail, ear flicks, a big stretch, claw scratching
  cat: () => [
    A("idle", 4, {
      tail: { rot: p => [35 + 8 * sin(p, 1), 22 * sin(p, 1), 0], n: 24 }, tail_tip: { rot: p => [20, -28 * sin(p, 1, .15), 0], n: 24 },
      ear_l: { rot: p => [0, 0, -25 * bump(p, .3, .34)], n: 80 }, ear_r: { rot: p => [0, 0, 25 * bump(p, .74, .78)], n: 80 },
      head: { rot: p => [-18 * bump(p, .52, .72), 20 * hold(p, .05, .3) - 15 * hold(p, .78, .95), 0], n: 40 },
      body: { rot: p => [8 * bump(p, .45, .75), 0, 0], pos: p => [0, -1 * bump(p, .45, .75), -1 * bump(p, .45, .75)], n: 40 },
      leg_fl: { rot: p => [-55 * bump(p, .45, .75), 0, 0], pos: p => [0, 0, -2 * bump(p, .45, .75)], n: 40 },
      leg_fr: { rot: p => [-55 * bump(p, .46, .76), 0, 0], pos: p => [0, 0, -2 * bump(p, .46, .76)], n: 40 },
    }),
    A("walk", .8, {
      ...trotLegs(30, { lateral: true }),
      body: { rot: p => [0, 4 * sin(p, 1), 3 * sin(p, 1, .25)], pos: p => [0, -.3 * Math.abs(sin(p, 1)), 0], n: 16 },
      head: { rot: p => [4, -4 * sin(p, 1), -2 * sin(p, 1, .25)], n: 16 },
      tail: { rot: p => [45 + 5 * sin(p, 2), 14 * sin(p, 1, .3), 0], n: 16 }, tail_tip: { rot: p => [30, -20 * sin(p, 1, .55), 0], n: 16 },
    }),
    A("work", 1.6, {
      body: { rot: () => [-35, 0, 0], pos: () => pivotPos([0, 8, 0], [0, 6, 4], -35) },
      leg_fl: { rot: p => [-35 - 60 + 45 * Math.max(0, sin(p, 3)), 0, 0], pos: () => attachPos([0, 8, 0], [-2, 5, -4], -35, pivotPos([0, 8, 0], [0, 6, 4], -35)), n: 32 },
      leg_fr: { rot: p => [-35 - 60 + 45 * Math.max(0, sin(p, 3, .5)), 0, 0], pos: () => attachPos([0, 8, 0], [2, 5, -4], -35, pivotPos([0, 8, 0], [0, 6, 4], -35)), n: 32 },
      head: { rot: p => [14 + 4 * sin(p, 6), 0, 0], n: 24 },
      tail: { rot: p => [20, 30 * sin(p, 2), 0], n: 16 }, tail_tip: { rot: p => [10, -30 * sin(p, 2, .2), 0], n: 16 },
      ear_l: { rot: () => [0, 0, -12] }, ear_r: { rot: () => [0, 0, 12] },
    }),
    A("eat", 1.4, {
      head: { rot: p => [38 + 7 * Math.max(0, sin(p, 3)), 0, 0], n: 21 }, body: { rot: () => [6, 0, 0] },
      tail: { rot: p => [15, 18 * sin(p, 1), 0], n: 14 }, tail_tip: { rot: p => [10, -20 * sin(p, 1, .2), 0], n: 14 },
    }),
    hurtSquash("body", "head", { tail: { rot: p => [70 * (1 - p), 0, 0] }, ear_l: { rot: p => [30 * (1 - p), 0, -30 * (1 - p)] }, ear_r: { rot: p => [30 * (1 - p), 0, 30 * (1 - p)] } }),
  ],

  // 마토 — wizard bunny: bipedal bunny hops, nose wiggles, raises the wand and casts sparks
  rabbit: () => [
    A("idle", 4, {
      head: { rot: p => [3 * sin(p, 16) * hold(p, .1, .3), 12 * hold(p, .5, .7), 4 * sin(p, 1)], n: 80 },
      ear_l: { rot: p => [-15 * bump(p, .35, .42), 0, -10 * bump(p, .8, .86)], n: 80 }, ear_r: { rot: p => [-12 * bump(p, .38, .45), 0, 0], n: 80 },
      arm_r: { rot: p => [-30 * hold(p, .72, .95), 0, 20 * sin(p, 3) * hold(p, .72, .95)], n: 40 },
      hat: { rot: p => [0, 0, 6 * bump(p, .4, .5)], n: 40 },
      body: breathe(.03, 2),
    }),
    A("walk", .7, {
      root: { pos: p => [0, 3 * arc(p, .1, .9), 0], n: 14 },
      body: { scl: p => { const c = bump(p, 0, .15) + bump(p, .85, 1); return [1 + .08 * c, 1 - .1 * c, 1 + .08 * c]; }, rot: p => [8 * arc(p, .1, .9), 0, 0], n: 14 },
      leg_l: { rot: p => [40 * arc(p, .15, .85), 0, 0], n: 14 }, leg_r: { rot: p => [40 * arc(p, .15, .85), 0, 0], n: 14 },
      ear_l: { rot: p => [30 * bump(p, .4, 1) - 20 * bump(p, 0, .4), 0, 0], n: 14 }, ear_r: { rot: p => [30 * bump(p, .45, 1) - 20 * bump(p, 0, .45), 0, 0], n: 14 },
      arm_l: { rot: p => [-30 * arc(p, .1, .9), 0, -15 * arc(p, .1, .9)], n: 14 }, arm_r: { rot: p => [-30 * arc(p, .1, .9), 0, 15 * arc(p, .1, .9)], n: 14 },
    }),
    A("work", 1.6, {
      arm_r: { rot: p => [-150 * hold(p, .05, .4, .1) - 60 * bump(p, .4, .6) - 150 * hold(p, .55, .9, .1), 0, 25 * sin(p, 4)], n: 48 },
      arm_l: { rot: p => [-60 - 15 * sin(p, 2), 0, -25], n: 16 },
      spark_work: { scl: p => { const s = .2 + 1.4 * (bump(p, .38, .55) + bump(p, .86, 1)); return [s, s, s]; }, n: 48 },
      body: { rot: p => [-8 * hold(p, .05, .4) - 8 * hold(p, .55, .9) + 10 * (bump(p, .38, .55) + bump(p, .86, 1)), 0, 0], n: 32 },
      ear_l: { rot: () => [-20, 0, -8] }, ear_r: { rot: () => [-20, 0, 8] },
      head: { rot: p => [-14 * hold(p, .05, .9), 0, 0], n: 16 },
    }),
    A("eat", 1, {
      arm_l: { rot: () => [-75, 0, 18] }, arm_r: { rot: () => [-75, 0, -18] },
      head: { rot: p => [12 + 6 * Math.max(0, sin(p, 4)), 0, 2 * sin(p, 8)], n: 32 },
      ear_l: { rot: p => [8 * sin(p, 4), 0, 0], n: 16 }, ear_r: { rot: p => [8 * sin(p, 4, .5), 0, 0], n: 16 },
    }),
    hurtSquash("body", "head", { ear_l: { rot: p => [60 * (1 - p), 0, 0] }, ear_r: { rot: p => [60 * (1 - p), 0, 0] }, hat: { rot: p => [0, 0, -25 * (1 - p)] } }),
  ],

  // 레디 — desert miner fox: springy trot, sniff-and-pounce idle, frantic digging
  fox: () => [
    A("idle", 4, {
      head: { rot: p => [4 * sin(p, 20) * hold(p, .05, .2) + 25 * hold(p, .4, .58, .05) - 15 * bump(p, .6, .68), 18 * hold(p, .2, .36), 0], n: 80 },
      body: { rot: p => [10 * hold(p, .4, .58, .05) - 14 * bump(p, .58, .7), 0, 0], pos: p => [0, -2 * hold(p, .4, .58, .05) + 4 * arc(p, .58, .7), -5 * bump(p, .56, .72)], n: 60 },
      leg_fl: { rot: p => [-45 * bump(p, .58, .72), 0, 0], n: 60 },
      leg_fr: { rot: p => [-45 * bump(p, .58, .72), 0, 0], n: 60 },
      leg_bl: { rot: p => [40 * bump(p, .58, .72), 0, 0], n: 60 },
      leg_br: { rot: p => [40 * bump(p, .58, .72), 0, 0], n: 60 },
      ear_l: { rot: p => [0, -20 * hold(p, .2, .36), 0], n: 40 }, ear_r: { rot: p => [0, 20 * hold(p, .22, .38), 0], n: 40 },
      tail: { rot: p => [10 + 25 * hold(p, .4, .58), 12 * sin(p, 2), 0], n: 40 },
    }),
    A("walk", .8, {
      ...trotLegs(42),
      body: { pos: p => [0, .8 * Math.abs(sin(p, 1)) - .4, 0], rot: p => [2 * sin(p, 2), 0, 0], n: 16 },
      head: { pos: p => [0, -.6 * Math.abs(sin(p, 1)), 0], rot: p => [-2 * sin(p, 2), 0, 0], n: 16 },
      tail: { rot: p => [12 + 6 * sin(p, 2, .2), 18 * sin(p, 1, .2), 0], n: 16 },
      ear_l: { rot: p => [-10 * Math.abs(sin(p, 1)), 0, 0], n: 16 }, ear_r: { rot: p => [-10 * Math.abs(sin(p, 1)), 0, 0], n: 16 },
    }),
    A("work", 1.2, {
      body: { rot: () => [16, 0, 0], pos: () => [0, -1, -1] },
      head: { rot: p => [30 + 6 * sin(p, 6), 0, 0], n: 24 },
      leg_fl: { rot: p => [-20 + 55 * sin(p, 4), 0, 0], n: 32 }, leg_fr: { rot: p => [-20 + 55 * sin(p, 4, .5), 0, 0], n: 32 },
      leg_bl: { rot: () => [-12, 0, 0] }, leg_br: { rot: () => [-12, 0, 0] },
      tail: { rot: p => [40, 30 * sin(p, 4), 0], n: 32 },
      ear_l: { rot: () => [15, 0, -10] }, ear_r: { rot: () => [15, 0, 10] },
    }),
    A("eat", 1.6, { head: { rot: p => [40 + 6 * Math.max(0, sin(p, 4)), 0, 0], n: 32 }, body: { rot: () => [6, 0, 0] }, tail: { rot: p => [8, 20 * sin(p, 1), 0], n: 16 } }),
    hurtSquash("body", "head", { tail: { rot: p => [60 * (1 - p), 0, 0] } }),
  ],

  // 바라 (id redpanda) — calm capybara carpenter: unhurried waddle, looks around with the tangerine wobbling, hammers with a mallet
  redpanda: () => [
    A("idle", 4.5, {
      body: { ...breathe(.02, 2), rot: p => [4 * hold(p, .5, .85), 0, 0], pos: p => pivotPos([0, 7, 0], [0, 4, 3.5], 4 * hold(p, .5, .85)), n: 54 },
      head: { rot: p => [6 * sin(p, 1) + 10 * hold(p, .5, .85), 20 * hold(p, .12, .3) - 16 * hold(p, .32, .46), 0], n: 54 },
      ear_l: { rot: p => [0, 0, -14 * bump(p, .6, .64)], n: 90 }, ear_r: { rot: p => [0, 0, 14 * bump(p, .66, .7)], n: 90 },
      leaf: { rot: p => [0, 0, 6 * sin(p, 2) + 10 * bump(p, .12, .2) - 10 * bump(p, .32, .4)], n: 54 },
      tail: { rot: p => [0, 10 * bump(p, .75, .8), 0], n: 54 },
    }),
    A("walk", .9, {
      ...trotLegs(26),
      body: { rot: p => [0, 0, 3 * sin(p, 1)], pos: p => [0, .45 * Math.abs(sin(p, 1)), 0], n: 18 },
      head: { rot: p => [-3 * sin(p, 2), 0, -3 * sin(p, 1)], n: 18 },
      leaf: { rot: p => [0, 0, 8 * sin(p, 1, .3)], n: 18 },
    }),
    A("work", 1.2, {
      body: { rot: () => [-16, 0, 0], pos: () => pivotPos([0, 7, 0], [0, 4, 3.5], -16) },
      leg_fr: { rot: p => [-115 * hold(p, .05, .45, .15) - 40 * bump(p, .45, .6) + 15 * bump(p, .6, .7), 0, 0], n: 36 },
      leg_fl: { rot: () => [-20, 0, -6] },
      head: { rot: p => [24 + 8 * bump(p, .48, .62), 0, 0], n: 36 },
      leaf: { rot: p => [0, 0, 10 * bump(p, .5, .7) * sin(p, 6)], n: 36 },
    }),
    A("eat", 1.6, {
      head: { rot: p => [32 + 6 * Math.max(0, sin(p, 4)), 0, 0], n: 32 },
      body: { rot: () => [6, 0, 0] },
      leaf: { rot: p => [0, 0, 5 * sin(p, 4)], n: 32 },
    }),
    hurtSquash("body", "head", { leaf: { rot: p => [-30 * (1 - p), 0, 20 * (1 - p)] } }),
  ],

  // 해달수 — river otter lumberjack: humping gallop, rubs its face, gnaws wood in fast chomps and slaps its tail
  otter: () => [
    A("idle", 4, {
      leg_fl: { rot: p => [-85 * hold(p, .15, .45) + 12 * sin(p, 12) * hold(p, .2, .4), 0, 10 * hold(p, .15, .45)], n: 80 },
      leg_fr: { rot: p => [-85 * hold(p, .15, .45) + 12 * sin(p, 12, .5) * hold(p, .2, .4), 0, -10 * hold(p, .15, .45)], n: 80 },
      head: { rot: p => [20 * hold(p, .15, .45) + 4 * sin(p, 12) * hold(p, .2, .4), 15 * hold(p, .6, .8), 0], n: 80 },
      body: { rot: p => [-10 * hold(p, .15, .45), 0, 0], ...breathe(.025, 2), n: 40 },
      tail: { rot: p => [5 * sin(p, 2), 10 * sin(p, 1), 0], n: 24 },
      hat_flap_l: { rot: p => [0, 0, -10 * bump(p, .6, .7)], n: 40 }, hat_flap_r: { rot: p => [0, 0, 10 * bump(p, .62, .72)], n: 40 },
    }),
    A("walk", .8, {
      body: { rot: p => [10 * sin(p, 1), 0, 0], pos: p => [0, 1.2 * Math.max(0, sin(p, 1, .1)), 0], n: 16 },
      leg_fl: { rot: p => [45 * sin(p, 1), 0, 0], n: 16 }, leg_fr: { rot: p => [45 * sin(p, 1, .05), 0, 0], n: 16 },
      leg_bl: { rot: p => [45 * sin(p, 1, .5), 0, 0], n: 16 }, leg_br: { rot: p => [45 * sin(p, 1, .55), 0, 0], n: 16 },
      head: { rot: p => [-8 * sin(p, 1), 0, 0], n: 16 },
      tail: { rot: p => [-15 * sin(p, 1, .25), 0, 0], n: 16 },
      hat: { rot: p => [0, 0, 6 * sin(p, 1, .3)], n: 16 },
    }),
    A("work", 1.2, {
      body: { rot: () => [12, 0, 0] },
      head: { rot: p => [22 + 8 * Math.max(0, sin(p, 6)), 22 * sin(p, 2), 0], n: 48 },
      leg_fl: { rot: () => [-50, 0, 10] }, leg_fr: { rot: () => [-50, 0, -10] },
      tail: { rot: p => [40 * bump(p, .4, .55) - 25 * bump(p, .55, .7) + 40 * bump(p, .9, 1) * 0, 0, 0], n: 36 },
      hat: { rot: p => [0, 0, 8 * sin(p, 2, .25)], n: 24 },
    }),
    A("eat", 1.6, {
      body: { rot: () => [-25, 0, 0], pos: () => [0, 1, 1] }, leg_fl: { rot: () => [-80, 0, 15] }, leg_fr: { rot: () => [-80, 0, -15] },
      head: { rot: p => [25 + 8 * Math.max(0, sin(p, 5)), 0, 0], n: 40 },
    }),
    hurtSquash("body", "head", { hat: { pos: p => [0, 2 * (1 - p), 0] } }),
  ],

  // 딱지 — heavy rock tortoise (rare): slow plodding, hides in its shell and peeks out, headbutts rock
  tortoise: () => [
    A("idle", 4, {
      head: { pos: p => [0, -.5 * hold(p, .2, .55, .05), 3.5 * hold(p, .2, .55, .05) - 1.2 * bump(p, .55, .75)], rot: p => [0, 14 * hold(p, .7, .9) - 14 * bump(p, .55, .65), 0], n: 60 },
      eyelid: { scl: p => [1, 1 - .9 * (bump(p, .1, .14) + bump(p, .8, .84)), 1], n: 100 },
      leg_fl: { pos: p => [.8 * hold(p, .22, .52), 1 * hold(p, .22, .52), 0], n: 40 }, leg_fr: { pos: p => [-.8 * hold(p, .22, .52), 1 * hold(p, .22, .52), 0], n: 40 },
      leg_bl: { pos: p => [.8 * hold(p, .22, .52), 1 * hold(p, .22, .52), 0], n: 40 }, leg_br: { pos: p => [-.8 * hold(p, .22, .52), 1 * hold(p, .22, .52), 0], n: 40 },
      body: { pos: p => [0, -.6 * hold(p, .22, .52), 0], n: 40 },
      tail: { rot: p => [0, 10 * sin(p, 1), 0], n: 16 },
    }),
    A("walk", 1.6, {
      ...trotLegs(22, { lift: .6 }),
      body: { rot: p => [0, 2 * sin(p, 1), 3 * sin(p, 1, .25)], pos: p => [0, .3 * Math.abs(sin(p, 1)), 0], n: 24 },
      head: { pos: p => [0, 0, -.8 * Math.abs(sin(p, 1))], rot: p => [0, -3 * sin(p, 1), 0], n: 24 },
      tail: { rot: p => [0, 8 * sin(p, 1, .3), 0], n: 16 },
    }),
    A("work", 2.4, {
      body: { rot: p => [-6 * bump(p, 0, .35) + 8 * bump(p, .35, .5) - 6 * bump(p, .5, .85) + 8 * bump(p, .85, 1), 0, 0], n: 48 },
      head: { pos: p => [0, 0, 2 * bump(p, 0, .35) - 2.5 * bump(p, .33, .5) + 2 * bump(p, .5, .85) - 2.5 * bump(p, .83, 1)], rot: p => [20 * (bump(p, .35, .5) + bump(p, .85, 1)), 0, 0], n: 48 },
      root: { pos: p => [.4 * sin(p, 20) * (bump(p, .42, .55) + bump(p, .92, 1)), 0, 0], n: 96 },
    }),
    A("eat", 1.2, { head: { rot: p => [25 + 6 * Math.max(0, sin(p, 2)), 0, 0], pos: p => [0, 0, -.6], n: 16 } }),
    A("hurt", .5, {
      head: { pos: p => [0, 0, 3.5 * (1 - p * p)], n: 6 },
      body: { scl: p => [1 + .06 * (1 - p), 1 - .08 * (1 - p), 1 + .06 * (1 - p)], n: 6 },
    }, { loop: "once" }),
  ],

  // 피토 — mushroom-field raccoon (rare): hunched shuffle, washes its hands, rummages furiously
  raccoon: () => [
    A("idle", 3.6, {
      arm_l: { rot: p => [-55 * hold(p, .05, .55), 0, 10 * hold(p, .05, .55)], n: 72 }, arm_r: { rot: p => [-55 * hold(p, .05, .55), 0, -10 * hold(p, .05, .55)], n: 72 },
      fore_l: { rot: p => [-30 * hold(p, .05, .55) + 18 * sin(p, 10) * hold(p, .1, .5), 0, 15 * sin(p, 10, .25) * hold(p, .1, .5)], n: 72 },
      fore_r: { rot: p => [-30 * hold(p, .05, .55) + 18 * sin(p, 10, .5) * hold(p, .1, .5), 0, -15 * sin(p, 10, .75) * hold(p, .1, .5)], n: 72 },
      head: { rot: p => [18 * hold(p, .05, .55), 25 * hold(p, .62, .78) - 25 * hold(p, .8, .95), 0], n: 48 },
      tail: { rot: p => [0, 20 * sin(p, 1), 0], n: 24 },
      body: breathe(.02, 2),
    }),
    A("walk", .8, {
      leg_l: { rot: p => [32 * sin(p, 1), 0, 0], n: 16 }, leg_r: { rot: p => [32 * sin(p, 1, .5), 0, 0], n: 16 },
      body: { rot: p => [10, 3 * sin(p, 1), 5 * sin(p, 1)], pos: p => [0, .5 * Math.abs(sin(p, 1)), 0], n: 16 },
      arm_l: { rot: p => [30 * sin(p, 1, .5), 0, 0], n: 16 }, arm_r: { rot: p => [30 * sin(p, 1), 0, 0], n: 16 },
      head: { rot: p => [-8, 0, -4 * sin(p, 1)], n: 16 },
      tail: { rot: p => [0, 25 * sin(p, 1, .3), 0], n: 16 },
    }),
    A("work", 1, {
      body: { rot: p => [22 + 4 * sin(p, 4), 6 * sin(p, 2), 0], n: 24 },
      arm_l: { rot: p => [-70 + 40 * sin(p, 4), 0, 10], n: 32 }, arm_r: { rot: p => [-70 + 40 * sin(p, 4, .5), 0, -10], n: 32 },
      fore_l: { rot: p => [-30 * Math.max(0, sin(p, 4, .2)), 0, 0], n: 32 }, fore_r: { rot: p => [-30 * Math.max(0, sin(p, 4, .7)), 0, 0], n: 32 },
      head: { rot: p => [20 + 6 * sin(p, 4), 0, 0], n: 24 },
      tail: { rot: p => [10, 30 * sin(p, 2), 0], n: 16 },
    }),
    A("eat", 1.6, {
      arm_l: { rot: () => [-70, 0, 20] }, arm_r: { rot: () => [-70, 0, -20] }, fore_l: { rot: () => [-35, 0, 0] }, fore_r: { rot: () => [-35, 0, 0] },
      head: { rot: p => [12 + 7 * Math.max(0, sin(p, 4)), 0, 0], n: 32 }, tail: { rot: p => [0, 15 * sin(p, 1), 0], n: 16 },
    }),
    hurtSquash("body", "head", { tail: { rot: p => [40 * (1 - p), 0, 0] } }),
  ],

  // 두류 — badlands mole miner: low scurry with a wiggling rump, sniffs the air, breast-stroke digging
  mole: () => [
    A("idle", 4, {
      head: { rot: p => [-22 * hold(p, .15, .5), 12 * sin(p, 2) * hold(p, .15, .5), 0], n: 48 },
      snout: { rot: p => [6 * sin(p, 24) * hold(p, .15, .5), 0, 0], scl: p => [1, 1, 1 + .1 * Math.max(0, sin(p, 24)) * hold(p, .15, .5)], n: 100 },
      leg_fl: { rot: p => [-30 * hold(p, .6, .85) + 15 * sin(p, 12) * hold(p, .62, .83), 0, 0], n: 72 }, leg_fr: { rot: p => [-30 * hold(p, .6, .85) + 15 * sin(p, 12, .5) * hold(p, .62, .83), 0, 0], n: 72 },
      body: breathe(.03, 2),
      tail: { rot: p => [0, 15 * sin(p, 2), 0], n: 24 },
    }),
    A("walk", .8, {
      leg_fl: { rot: p => [35 * sin(p, 2), 0, 0], n: 24 }, leg_br: { rot: p => [35 * sin(p, 2), 0, 0], n: 24 },
      leg_fr: { rot: p => [35 * sin(p, 2, .5), 0, 0], n: 24 }, leg_bl: { rot: p => [35 * sin(p, 2, .5), 0, 0], n: 24 },
      body: { rot: p => [0, 6 * sin(p, 2), 3 * sin(p, 2, .25)], n: 24 },
      head: { rot: p => [-6, -5 * sin(p, 2), 0], n: 24 },
      snout: { rot: p => [5 * sin(p, 8), 0, 0], n: 32 },
      tail: { rot: p => [0, 20 * sin(p, 2, .3), 0], n: 24 },
    }),
    A("work", 1.6, {
      leg_fl: { rot: p => [-50 + 50 * sin(p, 3), 40 * sin(p, 3, .25), 0], n: 48 }, leg_fr: { rot: p => [-50 + 50 * sin(p, 3), -40 * sin(p, 3, .25), 0], n: 48 },
      body: { rot: p => [12, 8 * sin(p, 3), 0], pos: p => [0, -.5 + .4 * sin(p, 6), 0], n: 48 },
      head: { rot: p => [25 + 6 * sin(p, 6), 0, 0], n: 48 },
      dirt_work: { scl: p => { const s = .8 + .4 * Math.abs(sin(p, 3)); return [s, s, s]; }, pos: p => [0, 1.5 * Math.abs(sin(p, 3)), 0], n: 48 },
      tail: { rot: p => [10, 25 * sin(p, 3), 0], n: 48 },
    }),
    A("eat", 1.6, { head: { rot: p => [22 + 6 * Math.max(0, sin(p, 4)), 0, 0], n: 32 }, snout: { rot: p => [6 * sin(p, 8), 0, 0], n: 32 } }),
    hurtSquash("body", "head"),
  ],

  // 썬더쉽 — fluffy thunder sheep: bouncy prance, wool puffs and crackles, charging headbutt with a spark burst
  // 젖소 — big calm dairy cow (ranch): slow 4-beat amble with bell swing, grazes head-down chewing a grass tuft (grass_eat)
  cow: () => {
    const graze = p => 58 + 4 * sin(p, 4);                     // head down to the grass, chewing
    return [
    A("idle", 4, {
      body: { ...breathe(.02, 2), rot: p => [0, 0, 1.2 * sin(p, 1)], n: 40 },
      head: { rot: p => [6 * sin(p, 1) + 18 * hold(p, .55, .8) + 2.5 * sin(p, 12) * hold(p, .6, .78), 6 * sin(p, 1, .25) * (1 - hold(p, .55, .8)), -10 * sin(p, 4) * bump(p, .2, .32)], n: 80 },
      ear_l: { rot: p => [0, 0, -25 * (bump(p, .1, .16) + bump(p, .4, .45))], n: 80 },
      ear_r: { rot: p => [0, 0, 25 * bump(p, .7, .76)], n: 80 },
      tail: { rot: p => [6 * sin(p, 1), 30 * sin(p, 3) * (hold(p, 0, .25) + hold(p, .5, .7)), 0], n: 80 },
      bell: { rot: p => [8 * sin(p, 4) * bump(p, .2, .32) + 3 * sin(p, 2), 0, 10 * sin(p, 4) * bump(p, .2, .34)], n: 80 },
    }),
    A("walk", 1.4, {
      root: { pos: p => [0, .5 * Math.abs(sin(p, 2)), 0], n: 28 },
      body: { rot: p => [1.5 * sin(p, 2), 0, 3 * sin(p, 1)], n: 28 },
      leg_fl: { rot: p => [22 * sin(p, 1), 0, 0], n: 28 }, leg_bl: { rot: p => [22 * sin(p, 1, .25), 0, 0], n: 28 },
      leg_fr: { rot: p => [22 * sin(p, 1, .5), 0, 0], n: 28 }, leg_br: { rot: p => [22 * sin(p, 1, .75), 0, 0], n: 28 },
      head: { rot: p => [5 + 5 * sin(p, 2, .1), 4 * sin(p, 1), 0], n: 28 },
      bell: { rot: p => [-14 * sin(p, 2, .2), 0, 16 * sin(p, 1, .3)], n: 28 },
      udder: { rot: p => [0, 0, 6 * sin(p, 1, .4)], n: 28 },
      tail: { rot: p => [8, 14 * sin(p, 1, .3), 0], n: 28 },
      ear_l: { rot: p => [0, 0, -6 * sin(p, 2)], n: 28 }, ear_r: { rot: p => [0, 0, 6 * sin(p, 2)], n: 28 },
    }),
    // ranch grazing: head down sniffing the grass, a slow step forward, chew, look up, back down
    A("work", 4, {
      root: { pos: p => [0, -.6 * (1 - hold(p, .42, .58)), -1.5 * bump(p, .2, .4)], n: 60 },
      body: { rot: p => [6 * (1 - hold(p, .42, .58)), 0, 1.5 * sin(p, 2)], n: 60 },
      head: { rot: p => [graze(p) * (1 - hold(p, .42, .58)) + 3 * sin(p, 1), 6 * sin(p, 2) * (1 - hold(p, .42, .58)), 0], pos: p => lerp3([0, -1.6, -.8], 1 - hold(p, .42, .58)), n: 80 },
      leg_fl: { rot: p => [-18 * bump(p, .2, .3), 0, 0], n: 60 }, leg_fr: { rot: p => [-18 * bump(p, .28, .38), 0, 0], n: 60 },
      tail: { rot: p => [6, 28 * sin(p, 5) * hold(p, .6, .9), 0], n: 80 },
      ear_l: { rot: p => [0, 0, -20 * bump(p, .45, .52)], n: 60 }, ear_r: { rot: p => [0, 0, 20 * bump(p, .47, .54)], n: 60 },
      bell: { rot: p => [-25 * (1 - hold(p, .42, .58)) + 5 * sin(p, 4), 0, 6 * sin(p, 2)], n: 60 },
    }),
    A("eat", 1.6, {
      root: { pos: () => [0, -.6, 0], n: 2 },
      body: { rot: p => [6, 0, 1 * sin(p, 1)], n: 16 },
      head: { rot: p => [graze(p), 5 * sin(p, 1), 3 * sin(p, 2)], pos: () => [0, -1.6, -.8], n: 32 },
      grass_eat: { scl: p => { const s = 1 - .25 * Math.abs(sin(p, 2)); return [s, 1, 1]; }, n: 16 },
      tail: { rot: p => [6, 22 * sin(p, 2), 0], n: 16 },
      bell: { rot: p => [-25 + 4 * sin(p, 4), 0, 0], n: 16 },
      ear_l: { rot: p => [0, 0, -8 * sin(p, 2)], n: 16 }, ear_r: { rot: p => [0, 0, 8 * sin(p, 2, .5)], n: 16 },
    }),
    hurtSquash("body", "head", { bell: { rot: p => [0, 0, 40 * sin(p, 2) * (1 - p)] }, tail: { rot: p => [-30 * (1 - p), 0, 0] } }),
  ];
  },

  thundersheep: () => {
    // roll around the middle of the wool ball (root origin y 5; ball centre ~ y 12 in model space)
    const C = [0, 12, 0], O = [0, 5, 0];
    const ramp = (p, a, b) => p <= a ? 0 : p >= b ? 1 : .5 - .5 * Math.cos(Math.PI * (p - a) / (b - a));
    const go = p => ramp(p, .12, .45) - ramp(p, .55, .88);   // 0 -> 1 (rolled forward) -> 0 (rolled back)
    const tuck = p => 1 - .85 * hold(p, .04, .96, .08);
    return [
    A("idle", 4, {
      body: { scl: p => { const s = 1 + .04 * (1 + sin(p, 2)) / 2; return [s, s, s]; }, n: 48 },
      head: { rot: p => [30 * hold(p, .1, .45) + 5 * sin(p, 10) * hold(p, .15, .4), 12 * hold(p, .6, .8), 0], n: 60 },
      leg_fl: { rot: p => [-25 * (bump(p, .78, .84) + bump(p, .86, .92)), 0, 0], n: 100 },
      tail: { rot: p => [0, 25 * sin(p, 6) * hold(p, .5, .62), 0], n: 60 },
    }),
    A("walk", 1, {
      root: { pos: p => [0, 1.2 * Math.abs(sin(p, 2)), 0], n: 20 },
      body: { rot: p => [3 * sin(p, 2), 0, 3 * sin(p, 1)], scl: p => [1, 1 + .04 * Math.abs(sin(p, 2, .25)), 1], n: 20 },
      leg_fl: { rot: p => [28 * sin(p, 1), 0, 0], n: 20 }, leg_br: { rot: p => [28 * sin(p, 1), 0, 0], n: 20 },
      leg_fr: { rot: p => [28 * sin(p, 1, .5), 0, 0], n: 20 }, leg_bl: { rot: p => [28 * sin(p, 1, .5), 0, 0], n: 20 },
      head: { rot: p => [-6 * sin(p, 2), 0, 0], n: 20 },
      tail: { rot: p => [0, 18 * sin(p, 2), 0], n: 20 },
    }),
    A("work", 3, {
      root: { rot: p => [360 * go(p), 0, 0], pos: p => { const q = pivotPos(O, C, 360 * go(p)); return [0, q[1], q[2] - 7 * go(p)]; }, n: 90 },
      head: { pos: p => [0, -1.5 * (1 - tuck(p)), 2 * (1 - tuck(p))], scl: p => { const k = .6 + .4 * tuck(p); return [k, k, k]; }, n: 36 },
      leg_fl: { scl: p => [1, tuck(p), 1], n: 24 }, leg_fr: { scl: p => [1, tuck(p), 1], n: 24 },
      leg_bl: { scl: p => [1, tuck(p), 1], n: 24 }, leg_br: { scl: p => [1, tuck(p), 1], n: 24 },
      tail: { scl: p => [1, tuck(p), tuck(p)], n: 24 },
      body: { scl: p => { const k = 1 + .08 * (1 - tuck(p)); return [k, k, k]; }, n: 24 },
    }),
    A("eat", 1.4, { head: { rot: p => [45 + 6 * Math.max(0, sin(p, 3)), 4 * sin(p, 1), 0], n: 28 }, tail: { rot: p => [0, 12 * sin(p, 2), 0], n: 14 } }),
    hurtSquash("body", "head"),
  ];
  },

  // 달투 — deep-dark moon bear (rare, big): heavy lumbering gait, stands up to sniff, rears and claws trees down
  moonbear: () => {
    const rearI = p => -38 * hold(p, .3, .6, .08), rearW = p => -55 + 8 * (bump(p, .2, .4) + bump(p, .65, .85));
    return [
    A("idle", 4, {
      body: { rot: p => mbBody(rearI(p)).rot, pos: p => mbBody(rearI(p)).pos, ...breathe(.025, 2), n: 48 },
      head: { rot: p => [6 - 30 * hold(p, .3, .6) + 3 * sin(p, 16) * hold(p, .38, .52), 15 * hold(p, .7, .9), 0], n: 80 },
      leg_fl: { rot: p => mbLeg(rearI(p), 20 * hold(p, .3, .6)).rot, glued: true, pos: p => mbLeg(rearI(p)).pos, n: 48 },
      leg_fr: { rot: p => mbLeg(rearI(p), 20 * hold(p, .3, .6)).rot, glued: true, pos: p => mbLeg(rearI(p)).pos, n: 48 },
      moon_glow: { scl: p => { const s = 1 + .12 * (1 + sin(p, 1)) / 2; return [s, s, s]; }, n: 24 },
      ear_l: { rot: p => [0, 0, -15 * bump(p, .12, .17)], n: 80 }, ear_r: { rot: p => [0, 0, 15 * bump(p, .14, .19)], n: 80 },
    }),
    A("walk", 1.2, {
      ...trotLegs(24, { lift: .8 }),
      body: { rot: p => [0, 4 * sin(p, 1), 5 * sin(p, 1, .25)], pos: p => [0, .6 * Math.abs(sin(p, 1)), 0], n: 24 },
      head: { rot: p => [10 + 3 * sin(p, 2), -7 * sin(p, 1), -3 * sin(p, 1, .25)], n: 24 },
      ear_l: { rot: p => [-6 * Math.abs(sin(p, 1)), 0, 0], n: 24 }, ear_r: { rot: p => [-6 * Math.abs(sin(p, 1)), 0, 0], n: 24 },
    }),
    A("work", 1.6, {
      body: { rot: p => mbBody(rearW(p)).rot, pos: p => mbBody(rearW(p)).pos, n: 40 },
      leg_fl: { rot: p => mbLeg(rearW(p), -70 * hold(p, 0, .2, .08) + 60 * bump(p, .2, .4)).rot, glued: true, pos: p => mbLeg(rearW(p)).pos, n: 40 },
      leg_fr: { rot: p => mbLeg(rearW(p), -70 * hold(p, .45, .65, .08) + 60 * bump(p, .65, .85)).rot, glued: true, pos: p => mbLeg(rearW(p)).pos, n: 40 },
      head: { rot: p => [45 + 10 * (bump(p, .2, .4) + bump(p, .65, .85)), 0, 0], n: 40 },
      moon_glow: { scl: p => { const s = 1 + .2 * (bump(p, .2, .4) + bump(p, .65, .85)); return [s, s, s]; }, n: 40 },
    }),
    A("eat", 1.6, {
      body: { rot: () => mbBody(-30).rot, pos: () => mbBody(-30).pos },
      leg_fr: { rot: p => mbLeg(-30, -60 + 10 * sin(p, 2)).rot, glued: true, pos: () => mbLeg(-30).pos, n: 16 }, leg_fl: { rot: () => mbLeg(-30, 15).rot, glued: true, pos: () => mbLeg(-30).pos },
      head: { rot: p => [28 + 8 * Math.max(0, sin(p, 4)), 0, 0], n: 32 },
    }),
    A("hurt", .4, {
      body: { rot: p => [-12 * (1 - p), 0, 0], scl: p => [1 + .06 * (1 - p), 1 - .06 * (1 - p), 1 + .06 * (1 - p)], n: 6 },
      head: { rot: p => [-30 * (1 - p) * cos(p, .5), 0, 0], n: 6 },
    }, { loop: "once" }),
  ];
  },

  // 장꾸펫 — mischievous ferret: slinky inchworm bound, the sideways "war dance", playful pounce-digging
  ferret: () => [
    A("idle", 4, {
      root: { pos: p => [-2.5 * arc(p, .3, .38) * 1 + 2.5 * arc(p, .4, .48) * 1 - 2.5 * arc(p, .5, .58), 2.2 * (arc(p, .3, .38) + arc(p, .4, .48) + arc(p, .5, .58)), 0], rot: p => [0, 25 * hold(p, .28, .6, .04), 10 * (arc(p, .3, .38) - arc(p, .4, .48) + arc(p, .5, .58))], n: 100 },
      body_back: { rot: p => [-20 * hold(p, .28, .6, .04), 0, 0], n: 50 },
      body_front: { rot: p => [30 * hold(p, .28, .6, .04), 0, 0], n: 50 },
      head: { rot: p => [-25 * hold(p, .28, .6, .04), 30 * hold(p, .05, .15, .02) - 30 * hold(p, .15, .25, .02) + 20 * hold(p, .7, .85, .02), 0], n: 100 },
      tail: { rot: p => [25 * hold(p, .28, .6), 20 * sin(p, 2), 0], n: 48 },
      ear_l: { rot: p => [0, 0, -15 * bump(p, .88, .92)], n: 100 },
    }),
    A("walk", .8, {
      body_front: { rot: p => [-12 * sin(p, 1), 0, 0], pos: p => [0, 1.2 * Math.max(0, sin(p, 1)), 0], n: 16 },
      body_back: { rot: p => [14 * sin(p, 1, .15), 0, 0], pos: p => [0, 1.2 * Math.max(0, sin(p, 1, .5)), 0], n: 16 },
      leg_fl: { rot: p => [50 * sin(p, 1), 0, 0], n: 16 }, leg_fr: { rot: p => [50 * sin(p, 1, .04), 0, 0], n: 16 },
      leg_bl: { rot: p => [50 * sin(p, 1, .5), 0, 0], n: 16 }, leg_br: { rot: p => [50 * sin(p, 1, .54), 0, 0], n: 16 },
      head: { rot: p => [10 * sin(p, 1), 0, 0], n: 16 },
      tail: { rot: p => [15 * sin(p, 1, .4), 10 * sin(p, 1), 0], n: 16 },
    }),
    A("work", 1.6, {
      body_front: { rot: p => [20 + 8 * sin(p, 4), 0, 0], n: 32 }, body_back: { rot: () => [-10, 0, 0] },
      leg_fl: { rot: p => [-30 + 55 * sin(p, 5), 0, 0], n: 40 }, leg_fr: { rot: p => [-30 + 55 * sin(p, 5, .5), 0, 0], n: 40 },
      head: { rot: p => [25 + 8 * Math.max(0, sin(p, 8)), 15 * sin(p, 1), 0], n: 48 },
      tail: { rot: p => [30, 35 * sin(p, 3), 0], n: 32 },
      snout: { scl: p => [1, 1, 1 + .1 * Math.max(0, sin(p, 8))], n: 32 },
    }),
    A("eat", 1, { body_front: { rot: () => [15, 0, 0] }, head: { rot: p => [30 + 7 * Math.max(0, sin(p, 4)), 0, 0], n: 24 }, tail: { rot: p => [10, 20 * sin(p, 2), 0], n: 16 } }),
    hurtSquash("body_front", "head", { tail: { rot: p => [50 * (1 - p), 0, 0] } }),
  ],

  // 천사픽 — angel piglet: hovers on little wings, halo bobs and spins, roots the soil with its snout
  angelpig: () => [
    A("idle", 4, {
      root: { pos: p => [0, 1.8 + .8 * sin(p, 2), 0], n: 32 },
      wing_l: { rot: p => [0, 25 * sin(p, 8), -20 - 10 * sin(p, 8)], n: 96 }, wing_r: { rot: p => [0, -25 * sin(p, 8), 20 + 10 * sin(p, 8)], n: 96 },
      halo_glow: { pos: p => [0, .6 * sin(p, 2, .2), 0], rot: p => [0, 360 * p, 0], n: 40, lin: true },
      leg_fl: { rot: p => [10 * sin(p, 2), 0, 0], n: 32 }, leg_fr: { rot: p => [10 * sin(p, 2, .2), 0, 0], n: 32 },
      leg_bl: { rot: p => [10 * sin(p, 2, .4), 0, 0], n: 32 }, leg_br: { rot: p => [10 * sin(p, 2, .6), 0, 0], n: 32 },
      head: { rot: p => [-6 * hold(p, .3, .5), 14 * hold(p, .6, .8), 0], n: 40 },
      ear_l: { rot: p => [0, 0, -14 * bump(p, .2, .26)], n: 80 }, ear_r: { rot: p => [0, 0, 14 * bump(p, .22, .28)], n: 80 },
    }),
    A("walk", .9, {
      root: { pos: p => [0, 2.5 + .7 * sin(p, 2), 0], rot: p => [6, 0, 0], n: 18 },
      wing_l: { rot: p => [0, 35 * sin(p, 4), -25 - 15 * sin(p, 4)], n: 36 }, wing_r: { rot: p => [0, -35 * sin(p, 4), 25 + 15 * sin(p, 4)], n: 36 },
      leg_fl: { rot: p => [20 + 15 * sin(p, 2), 0, 0], n: 18 }, leg_fr: { rot: p => [20 + 15 * sin(p, 2, .5), 0, 0], n: 18 },
      leg_bl: { rot: p => [25 + 15 * sin(p, 2, .25), 0, 0], n: 18 }, leg_br: { rot: p => [25 + 15 * sin(p, 2, .75), 0, 0], n: 18 },
      halo_glow: { pos: p => [0, .5 * sin(p, 2, .3), 0], n: 18 },
      ear_l: { rot: p => [15, 0, -8], n: 2 }, ear_r: { rot: p => [15, 0, 8], n: 2 },
    }),
    A("work", 1.6, {
      head: { rot: p => [30 + 14 * Math.max(0, sin(p, 4)), 8 * sin(p, 2), 0], pos: p => [0, 0, -.8 * Math.max(0, sin(p, 4))], n: 48 },
      body: { rot: p => [10 + 4 * sin(p, 4), 0, 0], n: 32 },
      wing_l: { rot: p => [0, 15 * sin(p, 6), -15 - 8 * sin(p, 6)], n: 48 }, wing_r: { rot: p => [0, -15 * sin(p, 6), 15 + 8 * sin(p, 6)], n: 48 },
      halo_glow: { pos: p => [0, .5 * sin(p, 2), 0], rot: p => [0, 360 * p, 0], n: 32, lin: true },
    }),
    A("eat", 1, {
      head: { rot: p => [35 + 6 * Math.max(0, sin(p, 4)), 0, 0], n: 24 },
      wing_l: { rot: p => [0, 10 * sin(p, 2), -12], n: 16 }, wing_r: { rot: p => [0, -10 * sin(p, 2), 12], n: 16 },
      halo_glow: { rot: p => [0, 180 * p, 0], n: 16, lin: true },
    }),
    hurtSquash("body", "head", { wing_l: { rot: p => [0, 0, -60 * (1 - p)] }, wing_r: { rot: p => [0, 0, 60 * (1 - p)] }, halo_glow: { pos: p => [0, 2 * (1 - p), 0] } }),
  ],

  // 파로 — sun lizard (rare): fast sprawled skitter with an S-wave spine, push-up display, tongue flicks, fiery tail
  sunlizard: () => [
    A("idle", 3.6, {
      body: { pos: p => [0, 1.2 * (arc(p, .2, .28) + arc(p, .3, .38) + arc(p, .4, .48)), 0], rot: p => [-5 * (arc(p, .2, .28) + arc(p, .3, .38) + arc(p, .4, .48)), 0, 0], n: 100 },
      head: { rot: p => [-8 * hold(p, .2, .5), 18 * hold(p, .62, .78), 0], n: 60 },
      tongue: { scl: p => [1, 1, 1 + 2.5 * (bump(p, .55, .58) + bump(p, .6, .63) + bump(p, .9, .93))], n: 120 },
      crest_glow: { scl: p => { const s = 1 + .18 * bump(p, .15, .5); return [s, s, 1]; }, n: 40 },
      tail: { rot: p => [-4, 14 * sin(p, 1), 0], n: 36 }, tail_tip: { rot: p => [-6, 18 * sin(p, 1, -.15), 0], n: 36 },
    }),
    A("walk", .6, {
      body: { rot: p => [0, 10 * sin(p, 1), 0], n: 12 },
      head: { rot: p => [0, -12 * sin(p, 1), 0], n: 12 },
      tail: { rot: p => [0, -18 * sin(p, 1, .1), 0], n: 12 }, tail_tip: { rot: p => [0, -22 * sin(p, 1, .2), 0], n: 12 },
      ...Object.fromEntries([["leg_fl", -1, 0], ["leg_br", 1, 0], ["leg_fr", 1, .5], ["leg_bl", -1, .5]].map(([b, side, ph]) => [b, {
        rot: p => [0, -side * 30 * sin(p, 1, ph), side * 10 * Math.max(0, cos(p, 1, ph))], pos: p => [0, .9 * Math.max(0, cos(p, 1, ph)), 0], n: 16 }])),
    }),
    A("work", 2.4, {
      head: { rot: p => [12, 26 * sin(p, 1), 0], n: 36 },
      body: { rot: p => [0, -10 * sin(p, 1), 0], n: 36 },
      tongue: { scl: p => [1, 1, 1 + 2 * Math.max(0, sin(p, 4))], n: 48 },
      crest_glow: { scl: p => { const s = 1.12 + .12 * sin(p, 4); return [s, s, 1]; }, n: 36 },
      tail: { rot: p => [-8, 20 * sin(p, 1, .5), 0], n: 36 }, tail_tip: { rot: p => [-10, 24 * sin(p, 1, .35), 0], n: 36 },
    }),
    A("eat", 1.6, {
      head: { rot: p => [15 - 20 * bump(p, .1, .25) + 25 * bump(p, .25, .4) + 5 * Math.max(0, sin(p, 4)) * (p > .45 ? 1 : 0), 0, 0], n: 48 },
      tongue: { scl: p => [1, 1, 1 + 3 * bump(p, .2, .32)], n: 48 },
    }),
    hurtSquash("body", "head", { crest_glow: { scl: p => { const s = 1 + .4 * (1 - p); return [s, s, 1]; } } }),
  ],

  // 블벳 — fortress vampire bat courier: two-joint wing flaps, swoops, menacing glowing eyes
  vampbat: () => {
    const flap = (k, amp, base = 0) => ({
      wing_l: { rot: p => [0, 10 * sin(p, k, .25), -(base + amp * sin(p, k))], n: k * 8 }, wing_r: { rot: p => [0, -10 * sin(p, k, .25), base + amp * sin(p, k)], n: k * 8 },
      wing_l_outer: { rot: p => [0, 0, -(amp * .6) * sin(p, k, -.12)], n: k * 8 }, wing_r_outer: { rot: p => [0, 0, amp * .6 * sin(p, k, -.12)], n: k * 8 },
    });
    return [
      A("idle", 3.5, {
        ...flap(5, 35, 5),
        root: { pos: p => [0, 1.2 * sin(p, 5, .25), 0], n: 40 },
        head: { rot: p => [0, 20 * hold(p, .2, .4) - 20 * hold(p, .6, .8), 14 * bump(p, .45, .55)], n: 60 },
        ear_l: { rot: p => [0, 0, -12 * bump(p, .1, .15)], n: 70 }, ear_r: { rot: p => [0, 0, 12 * bump(p, .12, .17)], n: 70 },
        eyes_glow: { scl: p => { const s = 1 - .8 * bump(p, .7, .74); return [1, s, 1]; }, n: 100 },
        leg_l: { rot: p => [15 + 8 * sin(p, 5), 0, 0], n: 40 }, leg_r: { rot: p => [15 + 8 * sin(p, 5, .1), 0, 0], n: 40 },
      }),
      A("walk", .7, {
        ...flap(2, 50, 5),
        root: { pos: p => [0, 1.8 * sin(p, 2, .25), 0], rot: p => [14, 0, 6 * sin(p, 1)], n: 16 },
        head: { rot: p => [-10, 0, -4 * sin(p, 1)], n: 16 },
        leg_l: { rot: () => [45, 0, 0] }, leg_r: { rot: () => [45, 0, 0] },
        tail: { rot: p => [10 * sin(p, 2), 0, 0], n: 16 },
      }),
      A("work", 1.2, {
        ...flap(4, 45, 10),
        root: { pos: p => [0, 2.5 * sin(p, 1) - 1, -1.5 * sin(p, 1, .25)], rot: p => [10 * sin(p, 1, .25), 0, 10 * sin(p, 1)], n: 24 },
        head: { rot: p => [10 * sin(p, 1), 0, 0], n: 24 },
        leg_l: { rot: () => [-20, 0, 8] }, leg_r: { rot: () => [-20, 0, -8] },
      }),
      A("eat", 1.6, {
        ...flap(4, 22, 15),
        head: { rot: p => [30 + 12 * Math.max(0, sin(p, 4)), 0, 0], n: 32 },
        fang_glint: { scl: p => { const s = 1 + .6 * Math.max(0, sin(p, 4)); return [s, s, s]; }, n: 32 },
      }),
      hurtSquash("body", "head", { wing_l: { rot: p => [0, 0, 70 * (1 - p)] }, wing_r: { rot: p => [0, 0, -70 * (1 - p)] } }),
    ];
  },
};

// ---------------------------------------------------------------- glue
// Pets whose legs are root-level bones: every animation that moves / tilts the body also moves those legs so their hips stay on
// the body (renderer convention: body rotation = Rz(z) Ry(-y) Rx(-x), translation = (-px, py, pz)). A leg channel marked
// glued: true already carries its own offset. Root-level legs keep their own rotation (they stay planted when the body rears up).
const GLUE = {
  fox: ["leg_fl", "leg_fr", "leg_bl", "leg_br"], moonbear: ["leg_fl", "leg_fr", "leg_bl", "leg_br"], otter: ["leg_fl", "leg_fr", "leg_bl", "leg_br"],
  raccoon: ["leg_l", "leg_r", "tail"], redpanda: ["leg_bl", "leg_br"], tortoise: ["leg_fl", "leg_fr", "leg_bl", "leg_br"],
  sunlizard: ["leg_fl", "leg_fr", "leg_bl", "leg_br"], thundersheep: ["leg_fl", "leg_fr", "leg_bl", "leg_br"],
};
function boneOrigins(m) { const map = {}; const walk = o => { for (const c of o) if (typeof c !== "string") { map[c.name] = c.origin.map(Number); walk(c.children || []); } }; walk(m.outliner); return map; }
function rotVec(v, [rx, ry, rz]) {
  const d = Math.PI / 180;
  let [x, y, z] = v, a = -rx * d, c = Math.cos(a), s = Math.sin(a);
  [y, z] = [y * c - z * s, y * s + z * c];
  a = -ry * d; c = Math.cos(a); s = Math.sin(a);
  [x, z] = [x * c + z * s, -x * s + z * c];
  a = rz * d; c = Math.cos(a); s = Math.sin(a);
  [x, y] = [x * c - y * s, x * s + y * c];
  return [x, y, z];
}
function glue(specs, origins, legs) {
  const bo = origins.body;
  for (const sp of specs) {
    const body = sp.bones.body;
    if (!body || (!body.rot && !body.pos)) continue;
    for (const leg of legs) {
      const lo = origins[leg]; if (!lo) continue;
      const ch = sp.bones[leg] || (sp.bones[leg] = {});
      if (ch.glued) continue;
      const own = ch.pos, v = [lo[0] - bo[0], lo[1] - bo[1], lo[2] - bo[2]];
      ch.pos = p => {
        const r = body.rot ? body.rot(p) : [0, 0, 0], bp = body.pos ? body.pos(p) : [0, 0, 0], q = rotVec(v, r);
        const t = [-bp[0] + q[0] - v[0], bp[1] + q[1] - v[1], bp[2] + q[2] - v[2]], o = own ? own(p) : [0, 0, 0];
        return [-t[0] + o[0], t[1] + o[1], t[2] + o[2]];
      };
      const nn = Math.max(ch.n ?? 0, body.n ?? 0);
      if (nn) ch.n = nn;
    }
  }
}

// ---------------------------------------------------------------- write
function boneIndex(m) { const map = {}; const walk = o => { for (const c of o) if (typeof c !== "string") { map[c.name] = c.uuid; walk(c.children || []); } }; walk(m.outliner); return map; }
const r3 = v => Math.round(v * 1000) / 1000;
function build(spec, bones, skin) {
  const out = { uuid: crypto.randomUUID(), name: spec.name, loop: spec.loop, override: false, length: spec.length, snapping: 20, selected: false, anim_time_update: "", blend_weight: "", start_delay: "", loop_delay: "", animators: {} };
  for (const [bone, chans] of Object.entries(spec.bones)) {
    const id = bones[bone]; if (!id) throw new Error(`${skin}/${spec.name}: no bone "${bone}"`);
    const keys = [];
    for (const [ch, name] of [["rot", "rotation"], ["pos", "position"], ["scl", "scale"]]) {
      const fn = chans[ch]; if (!fn) continue;
      const n = chans.n ?? spec.nDefault ?? Math.max(2, Math.round(spec.length * 10));
      const once = spec.loop === "once";
      for (let i = 0; i <= n; i++) {
        const p = i / n, v = fn(p); void once;
        keys.push({ channel: name, data_points: [{ x: r3(v[0]), y: r3(v[1]), z: r3(v[2]) }], uuid: crypto.randomUUID(), time: r3(p * spec.length), color: -1, interpolation: chans.lin ? "linear" : "catmullrom" });
      }
    }
    out.animators[id] = { name: bone, type: "bone", keyframes: keys };
  }
  return out;
}
const only = process.argv.slice(2);
for (const [skin, make] of Object.entries(SPECS)) {
  if (only.length && !only.includes(skin)) continue;
  const file = path.join(petDir, skin + ".bbmodel"), m = JSON.parse(fs.readFileSync(file, "utf8")), bones = boneIndex(m);
  const specs = make();
  if (GLUE[skin]) glue(specs, boneOrigins(m), GLUE[skin]);
  m.animations = specs.map(s => build(s, bones, skin));
  fs.writeFileSync(file, JSON.stringify(m));
  console.log(skin.padEnd(13), specs.map(s => `${s.name}(${s.length}s,${Object.keys(s.bones).length}b)`).join(" "));
}
