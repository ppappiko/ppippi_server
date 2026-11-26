// 2nd raid boss (after the warden) - 3 ORIGINAL design concepts. Only the fight's mechanics are borrowed from another game's raid;
// looks / names are our own. Every concept shows the weak points the fight needs: two glowing green "hands" and a glowing face.
//   node dev/art/make-boss2-concepts.mjs  -> dev/art/preview/boss2/<id>.bbmodel  (render with preview-pets.mjs PET_DIR=...)
// Units: 16 = 1 block, feet at y 0, front = north (-Z).
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { Model, fur, flat, edge, shade, hash } from "./lib-petmodel.mjs";
import { hiveCrownMask } from "./lib-hivemask.mjs";
const here = path.dirname(fileURLToPath(import.meta.url));
const OUT = path.join(here, "preview/boss2"); fs.mkdirSync(OUT, { recursive: true });
const G = "#7dff9a", GD = "#2fbf5a", GP = "#dcffe4", K = "#101018";
const both = (fn) => [fn(1), fn(-1)];
const mx = (s, a, b) => (s > 0 ? [a, b] : [-b, -a]); // mirror an x range to the other side

// 1) 비탄의 망령 군주 — hooded wraith king floating above the ground, broken crown, bone mask face, two huge detached floating hands
function wraith(m) {
  const R = "#2a2f45", RD = "#1b1e2e", AU = "#c9a24a", AUD = "#8a6a2a", BO = "#e8e2cf", BOS = "#bfb59a";
  const robe = fur(R, { noise: .12 }), robeD = fur(RD, { noise: .1 });
  const rune = (x, y, z, f, i, j, w) => (f === "north" && i === (w >> 1) && j % 3 !== 2 ? GD : robe(x, y, z, f, i, j, w));
  const mask = (x, y, z, f, i, j, w, h) => {
    if (f !== "north") return BOS;
    if (j === 2 && (i === 1 || i === 2 || i === w - 2 || i === w - 3)) return G;       // eye slits
    if (j === 0 && i === (w >> 1)) return GP;                                              // forehead gem
    if (j === h - 2 && i > 1 && i < w - 2) return K;                                       // mouth line
    if ((i + j) % 5 === 0 && j > 3) return BOS;
    return BO;
  };
  const palm = (x, y, z, f, i, j, w, h) => (f === "north" && i > 0 && i < w - 1 && j > 0 && j < h - 2 ? ((i + j) % 2 ? G : GP) : f === "north" ? BOS : BO);
  const body = [
    m.cube("robe_low", [-7, 6, -6], [7, 16, 6], rune),
    ...[-6, -3, 0, 3, 6].flatMap((x, k) => [m.cube(`tatter_f${k}`, [x - .9, 2 + (k % 2) * 1.5, -6.2], [x + .9, 6.5, -5.2], robeD), m.cube(`tatter_b${k}`, [x - .9, 2.5 + ((k + 1) % 2) * 1.5, 5.2], [x + .9, 6.5, 6.2], robeD)]),
    m.cube("torso", [-6, 16, -5], [6, 28, 5], robe),
    m.cube("belt", [-6.4, 16, -5.4], [6.4, 17.6, 5.4], edge(AU, AUD)),
    m.cube("chest_sigil", [-1.6, 21, -5.6], [1.6, 24.2, -5], flat(G)),
    m.cube("mantle", [-9, 26, -6], [9, 29.6, 6], edge(RD, AU)),
    ...both(s => m.cube(`shoulder_spike_${s}`, [mx(s, 7.6, 9)[0], 29.6, -1], [mx(s, 7.6, 9)[1], 33.5, 1], flat(BO))),
    ...both(s => m.cube(`sleeve_${s}`, [mx(s, 6, 9.5)[0], 18, -3.2], [mx(s, 6, 9.5)[1], 26, 3.2], robeD)),
    m.cube("hood", [-5, 28, -5], [5, 38, 5], robeD),
    m.cube("hood_peak", [-3, 38, -2.6], [3, 40, 3.4], robeD),
    m.cube("face_mask", [-3.6, 30, -5.7], [3.6, 36.6, -4.9], mask),
    m.cube("crown_band", [-5.4, 38, -5.4], [5.4, 39.6, 5.4], edge(AU, AUD)),
    ...[[-3.6, 3], [0, 5], [3.6, 1.2]].map(([x, h], k) => m.cube(`crown_f${k}`, [x - .7, 39.6, -5.4], [x + .7, 39.6 + h, -4.2], flat(AU))),       // right prong broken short
    ...[[-4.4, 2.4], [4.4, 2.6]].map(([x, h], k) => m.cube(`crown_s${k}`, [x - .6, 39.6, -.7], [x + .6, 39.6 + h, .7], flat(AU))),
    ...[[-2.2, 3.2], [2.2, 2.8]].map(([x, h], k) => m.cube(`crown_b${k}`, [x - .7, 39.6, 4.2], [x + .7, 39.6 + h, 5.4], flat(AU))),
    m.cube("crown_gem", [-.8, 40, -5.8], [.8, 41.6, -5.2], flat(G)),
    ...[[-6, 45, -2], [5, 44, 1], [0, 47, 3]].map(([x, y, z], k) => m.cube(`wisp_${k}`, [x - .5, y - .5, z - .5], [x + .5, y + .5, z + .5], flat(G))),
  ];
  const hand = s => {
    const [x0, x1] = mx(s, 12.5, 17.5);
    return [m.cube(`hand_${s}_palm`, [x0, 18, -6.5], [x1, 24.5, -2.5], palm),
      ...[0, 1, 2, 3].map(k => { const fx = x0 + .3 + k * 1.25; return m.cube(`hand_${s}_finger${k}`, [fx, 13 + (k === 0 || k === 3 ? 1.5 : 0), -5.8], [fx + 1, 18, -4.6], flat(BO)); }),
      m.cube(`hand_${s}_thumb`, s > 0 ? [11.3, 19, -5.8] : [-12.5, 19, -5.8], s > 0 ? [12.5, 22.5, -4.6] : [-11.3, 22.5, -4.6], flat(BO)),
      m.cube(`hand_${s}_cuff`, [x0 - .4, 24.5, -7], [x1 + .4, 26.5, -2], edge(AU, AUD)),
      m.cube(`hand_${s}_wisp`, [mx(s, 13.5, 16.5)[0], 26.5, -5.5], [mx(s, 13.5, 16.5)[1], 28.5, -3.5], flat(GD))];
  };
  return [m.G("root", [0, 0, 0], [m.G("body", [0, 16, 0], body), m.G("hand_l", [-15, 21, -4.5], hand(-1)), m.G("hand_r", [15, 21, -4.5], hand(1))])];
}

// 2) 눈물 흘리는 석상 마녀 — a living stone statue on a pedestal, green light leaking from cracks, raised sigil palms, glowing tears
function statue(m) {
  const S = "#9a9a92", SD = "#6f6f69", SL = "#b8b8ae";
  const stone = fur(S, { noise: .16, dark: .25 });
  const cracked = (x, y, z, f, i, j, w, h) => (((i * 7 + Math.floor(j / 2) * 3) % 11 === 0 && hash(i, j, 3) > .35) || ((i + j * 5) % 13 === 0 && hash(j, i, 5) > .6) ? G : stone(x, y, z, f, i, j, w, h));
  const face = (x, y, z, f, i, j, w, h) => {
    if (f !== "north") return stone(x, y, z, f, i, j, w, h);
    const eyeL = i === 2 || i === 3, eyeR = i === w - 3 || i === w - 4;
    if (j === 3 && (eyeL || eyeR)) return SD;                                     // closed eyes
    if (j > 3 && (i === 2 || i === w - 3)) return j % 2 ? G : GP;                 // glowing tears running down
    if (j === h - 2 && i > 3 && i < w - 4) return SD;                             // mouth
    return SL;
  };
  const sigil = (x, y, z, f, i, j, w, h) => {
    if (f !== "north") return stone(x, y, z, f, i, j, w, h);
    const cx = (w - 1) / 2, cy = (h - 1) / 2, d = Math.hypot(i - cx, j - cy);
    return d < 1 ? GP : d < 1.8 ? K : d < 2.6 ? G : SL;                           // eye-shaped sigil on the palm
  };
  const body = [
    m.cube("pedestal", [-9, 0, -9], [9, 4, 9], (x, y, z, f, i, j, w, h) => (f === "north" && j === 1 && i % 3 === 1 ? G : edge(SD, "#55554f")(x, y, z, f, i, j, w, h))),
    m.cube("dress", [-7, 4, -6], [7, 18, 6], cracked),
    m.cube("torso", [-5.5, 18, -4.5], [5.5, 29, 4.5], cracked),
    m.cube("veil", [-6, 19, 4], [6, 36, 6.2], stone),
    ...both(s => m.cube(`upper_arm_${s}`, [mx(s, 5.5, 8.5)[0], 21, -3], [mx(s, 5.5, 8.5)[1], 28, 1], stone)),
    ...both(s => m.cube(`forearm_${s}`, [mx(s, 5.7, 8.3)[0], 20, -10], [mx(s, 5.7, 8.3)[1], 23, -3], cracked)),
    ...both(s => m.cube(`palm_${s}`, [mx(s, 4, 10)[0], 21, -13], [mx(s, 4, 10)[1], 28.5, -10.5], sigil)),
    ...both(s => [0, 1, 2].map(k => { const fx = mx(s, 4.3, 9.7)[0] + k * 2; return m.cube(`finger_${s}${k}`, [fx, 28.5, -12.6], [fx + 1.4, 31 + (k === 1 ? 1 : 0), -11.2], stone); })),
    m.cube("neck", [-2, 28.5, -2], [2, 30, 2], stone),
    m.cube("head", [-4.5, 30, -4.5], [4.5, 39, 4.5], face),
    ...Array.from({ length: 8 }, (_, k) => { const a = k * Math.PI / 4, x = Math.sin(a) * 4.2, z = -Math.cos(a) * 4.2, h = k % 2 ? 2.4 : 3.8; return m.cube(`thorn_${k}`, [x - .6, 39, z - .6], [x + .6, 39 + h, z + .6], flat(SD)); }),
    m.cube("brow_gem", [-.9, 37.4, -5], [.9, 39.2, -4.4], flat(G)),
  ];
  return [m.G("root", [0, 0, 0], [m.G("body", [0, 16, 0], body.flat())])];
}

// 3) 왕관에 잠식된 박쥐날개 마법사 — floating hooded mage, one huge glowing eye in the dark hood, crown, bat wings whose clawed tips hold green orbs
function batmage(m) {
  const P = "#3b2350", PD = "#221430", MB = "#6a4688", BN = "#d9d0c0", AU = "#d8b04a";
  const robe = fur(P, { noise: .12 });
  const hoodFace = (x, y, z, f, i, j, w, h) => {
    if (f !== "north") return fur(PD)(x, y, z, f, i, j, w, h);
    const cx = (w - 1) / 2, cy = h * .45, d = Math.hypot((i - cx) * .8, j - cy);
    if (i === 0 || i === w - 1 || j === 0) return PD;
    return d < .8 ? K : d < 1.8 ? G : d < 2.4 ? GD : K;                           // one big eye glowing in the void of the hood
  };
  const membrane = (x, y, z, f, i, j, w, h) => (f === "north" || f === "south" ? shade(MB, -.25 * j / h + (hash(i, j, 7) < .12 ? -.06 : 0)) : PD); // smooth leathery web, darker toward the hem
  const body = [
    m.cube("tail", [-2, 6, -2], [2, 11, 2], fur(PD)),
    m.cube("robe_low", [-4.5, 11, -4.5], [4.5, 20, 4.5], (x, y, z, f, i, j, w) => (f === "north" && (i === 1 || i === w - 2) ? AU : robe(x, y, z, f, i, j, w))),
    m.cube("torso", [-5.5, 20, -4.5], [5.5, 30, 4.5], robe),
    m.cube("collar", [-6.5, 28, -5.5], [6.5, 30.5, 5.5], edge(PD, AU)),
    ...both(s => m.cube(`collar_spike_${s}`, [mx(s, 5, 6.5)[0], 30.5, -5.5], [mx(s, 5, 6.5)[1], 34, -4], flat(BN))),
    m.cube("hood", [-4.8, 30, -4.8], [4.8, 39, 4.8], hoodFace),
    m.cube("crown_band", [-5, 38.4, -5], [5, 39.8, 5], edge(AU, "#8a6a2a")),
    ...[-3, 0, 3].map((x, k) => m.cube(`crown_p${k}`, [x - .7, 39.8, -5], [x + .7, 39.8 + (k === 1 ? 5 : 3.4), -3.6], flat(AU))),
    m.cube("crown_gem", [-.8, 40.4, -5.4], [.8, 42, -4.9], flat(G)),
  ];
  const wing = s => {
    const X = v => s * v;
    const lo = (a, b) => Math.min(X(a), X(b)), hi = (a, b) => Math.max(X(a), X(b));
    return [
      m.cube(`wing_${s}_arm`, [lo(5, 23), 33, 1.2], [hi(5, 23), 34.6, 2.8], flat(BN)),                                  // wing bone
      ...[[10, 21], [15, 23], [20, 25]].map(([x, y], k) => m.cube(`wing_${s}_finger${k}`, [lo(x - .6, x + .6), y, 1.4], [hi(x - .6, x + .6), 33, 2.6], flat(BN))),
      m.cube(`wing_${s}_web0`, [lo(5.5, 10), 18, 1.2], [hi(5.5, 10), 33, 2.8], membrane),
      m.cube(`wing_${s}_web1`, [lo(10, 15), 21, 1.2], [hi(10, 15), 33, 2.8], membrane),
      m.cube(`wing_${s}_web2`, [lo(15, 20), 23, 1.2], [hi(15, 20), 33, 2.8], membrane),
      m.cube(`wing_${s}_claw`, [lo(22, 24), 31, .6], [hi(22, 24), 33, 3.4], flat(BN)),
      m.cube(`wing_${s}_orb`, [lo(22.5, 27), 33.5, -.3], [hi(22.5, 27), 38, 4.2], (x, y, z, f, i, j, w, h) => ((i + j) % 3 === 0 ? GP : G)), // glowing "hand" orb held by the claw
    ];
  };
  return [m.G("root", [0, 0, 0], [m.G("body", [0, 16, 0], body), m.G("wing_l", [-5, 33, 2], wing(-1)), m.G("wing_r", [5, 33, 2], wing(1))])];
}


// 4) 비탄의 망령 군주 (current) — a huge pale hairy brute: barrel chest, giant shoulders, a red sash, shaggy pale mane and beard,
//    and the crowned sorrow mask (lib-hivemask.mjs, shared with its 문지기). Its two fists are separate parts (hand_-1_ / hand_1_)
//    with glowing green sigils, the fight lifts them to the face. Axis-aligned except the mask (22.5 deg steps) for the item-model export.
function ogre(m) {
  const SK = "#d6caac", SK_D = "#a39374", HAIR = "#c4b791", SASH = "#7a2622", SASH_D = "#4f1714", CLOTH = "#352b27", AU = "#c9a24a", AUD = "#8a6a2a";
  const skin = fur(SK, { noise: .07, dark: .16 }), skinD = fur(SK_D, { noise: .08, dark: .2 }), hair = fur(HAIR, { noise: .16, dark: .3 });
  const R0 = { raw: true };
  /** octagon slices (two crossed axis-aligned boxes) */
  const lathe = (nm, y0, y1, n, c, w, d, paint) => { const o = [], h = (y1 - y0) / n; for (let i = 0; i < n; i++) { const t = (i + .5) / n, [x, z] = c(t), W = w(t), D = d(t);
    o.push(m.cube(nm + i + "a", [x - W, y0 + i * h, z - D * .72], [x + W, y0 + (i + 1) * h + .02, z + D * .72], paint, R0), m.cube(nm + i + "b", [x - W * .72, y0 + i * h, z - D], [x + W * .72, y0 + (i + 1) * h + .02, z + D], paint, R0)); } return o; };
  const ball = (nm, cx, cy, cz, Rr, n, paint, sq = 1) => lathe(nm, cy - Rr * sq, cy + Rr * sq, n, () => [cx, cz], t => Rr * Math.sqrt(Math.max(.08, 1 - (2 * t - 1) ** 2)), t => Rr * Math.sqrt(Math.max(.08, 1 - (2 * t - 1) ** 2)), paint);
  /** a tube of overlapping boxes from a to b */
  const tube = (nm, a, b, r0, r1, n, paint) => { const o = []; for (let i = 0; i < n; i++) { const t = (i + .5) / n, p = a.map((v, k) => v + (b[k] - v) * t), r = r0 + (r1 - r0) * t;
    o.push(m.cube(nm + i, [p[0] - r, p[1] - r, p[2] - r], [p[0] + r, p[1] + r, p[2] + r], paint, R0)); } return o; };
  const lin = (a, b) => t => a + (b - a) * t, bul = (a, b, k) => t => a + (b - a) * t + k * Math.sin(Math.PI * t), cst = v => () => v;
  const body = [
    ...both(s => [
      ...lathe("thigh" + s, 5, 13.5, 4, () => [s * 4.6, .2], bul(3.1, 3.5, .5), bul(3.1, 3.4, .5), skin),
      ...lathe("shin" + s, 1.2, 6.5, 3, () => [s * 4.6, -.2], bul(2.7, 3, .3), bul(2.8, 3, .3), skin),
      m.cube("foot" + s, [s * 4.6 - 3, 0, -5.2], [s * 4.6 + 3, 1.9, 2.4], skinD),
      ...[-1.8, 0, 1.8].map((dx, k) => m.cube(`toe${s}_${k}`, [s * 4.6 + dx - .7, 0, -6.2], [s * 4.6 + dx + .7, 1.2, -5], fur(AUD, { noise: .1 }), R0)),
    ]).flat(),
    m.cube("loin_front", [-4.2, 5.2, -6], [4.2, 13, -5], fur(CLOTH, { noise: .12 })),
    m.cube("loin_back", [-4.6, 6, 4.4], [4.6, 13, 5.4], fur(CLOTH, { noise: .12 })),
    ...lathe("sash", 11.8, 15.4, 2, () => [0, -.4], cst(7.6), cst(5.8), fur(SASH, { noise: .1, dark: .25 })),
    m.cube("sash_knot", [2.6, 7.5, -6.6], [4.4, 13.6, -5.6], fur(SASH_D, { noise: .1 })),
    ...lathe("belly", 14.6, 24, 6, t => [0, -.9 - .5 * Math.sin(Math.PI * t)], bul(6.6, 7.6, 1.4), bul(5, 5.4, 1.3), skin),
    ...lathe("chest", 23.2, 30, 4, () => [0, -.2], bul(7.6, 7.8, .9), bul(5, 4.8, .6), skin),
    ...both(s => ball("pec" + s, s * 3.4, 25.6, -3.8, 2.8, 3, skin)).flat(),
    ...ball("hump", 0, 29.6, 2.4, 6, 5, skin),
    ...lathe("neck", 26.5, 31.5, 2, () => [0, -3], cst(3.6), cst(3.6), skinD),
    ...both(s => [
      ...ball("shoulder" + s, s * 9.6, 27.6, .2, 4.7, 5, skin),
      ...tube("upper" + s, [s * 10.8, 26.4, .4], [s * 13.6, 18.6, .6], 3.2, 2.8, 6, skin),
      ...ball("elbow" + s, s * 13.7, 18.6, .8, 2.9, 3, skinD),
      ...tube("fore" + s, [s * 13.8, 18.8, .2], [s * 14.5, 20.4, -2.4], 2.8, 2.6, 3, skin),
      ...[0, 1, 2, 3].flatMap(k => tube(`shag${s}_${k}`, [s * (8.2 + k * 1.3), 31 - k * .6, 1.6 - k * .7], [s * (9.4 + k * 1.6), 24.5 - k * .8, 2 - k * .5], .75, .45, 4, hair)),
    ]).flat(),
    ...ball("head", 0, 30.4, -5.4, 3.6, 4, skinD),
    ...hiveCrownMask(m, { cx: 0, cy: 30.3, cz: -5.6, s: 1, name: "mask" }),
  ];
  // mane and beard hanging from under the mask over the chest, longer in the middle
  for (let k = -5; k <= 5; k++) {
    const a = k * 15 * Math.PI / 180, x = Math.sin(a) * 3.5, z = -5.6 - Math.cos(a) * 3.1, len = 11.5 - Math.abs(k) * 1.1;
    body.push(...tube("beard" + (k + 5), [x, 26.8, z], [x * 1.3, 26.8 - len, z - .6 - (5 - Math.abs(k)) * .1], .95, .55, Math.max(3, Math.round(len / 1.3)), k % 2 ? fur(SK_D, { noise: .14 }) : hair));
  }
  for (let k = 0; k < 9; k++) { // back and side mane from the crown of the head
    const a = (100 + k * 20) * Math.PI / 180, x = Math.sin(a) * 3.6, z = -5.6 - Math.cos(a) * 3.4;
    body.push(...tube("mane" + k, [x, 33.6, z], [x * 1.5, 26, z + 2.4], .9, .55, 6, hair));
  }
  const G2 = "#7dff9a", GP2 = "#dcffe4";
  const hand = s => {
    const x = s * 14.6, y = 20.75, z = -4.5;
    return [
      ...lathe(`hand_${s}_fist`, y - 3, y + 3, 4, () => [x, z], bul(3, 3.1, .3), bul(3, 3.1, .3), skin),
      ...[-1.5, -.5, .5, 1.5].map((dx, k) => m.cube(`hand_${s}_knuckle${k}`, [x + dx * 1.3 - .6, y - 2.6, z - 3.8], [x + dx * 1.3 + .6, y - .6, z - 2.9], skinD, R0)),
      m.cube(`hand_${s}_thumb`, [x - s * 3.6 - 1, y - 1.6, z - 2.6], [x - s * 3.6 + 1, y + 1.6, z + .4], skin, R0),
      m.cube(`hand_${s}_sigil`, [x - 1.8, y - .2, z - 3.75], [x + 1.8, y + 2.6, z - 3.3], (px, py, pz, f, i, j) => f === "north" ? ((i + j) % 2 ? G2 : GP2) : G2, R0),
      ...lathe(`hand_${s}_cuff`, y + 2.4, y + 4.2, 1, () => [x, z + .8], cst(3.2), cst(3.2), edge(AU, AUD)),
    ];
  };
  return [m.G("root", [0, 0, 0], [m.G("body", [0, 16, 0], body), m.G("hand_l", [-14.6, 20.75, -4.5], hand(-1)), m.G("hand_r", [14.6, 20.75, -4.5], hand(1))])];
}

for (const [id, build] of [["boss_wraith", wraith], ["boss_statue", statue], ["boss_batmage", batmage], ["boss_ogre", ogre]]) {
  let m = new Model(id, 128, { round: true }), outl;
  try { outl = build(m); } catch (e) { if (!/texture full/.test(e.message)) throw e; m = new Model(id, 256, { round: true }); try { outl = build(m); } catch (e2) { if (!/texture full/.test(e2.message)) throw e2; m = new Model(id, 512, { round: true }); outl = build(m); } }
  console.log(id.padEnd(13), m.write(outl, path.join(OUT, id + ".bbmodel")), "cubes", m.T + "px");
}
