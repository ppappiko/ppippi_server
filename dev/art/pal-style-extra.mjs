// 4 more pets for the 적성 plan (2026-11-27): 꿀벌찌 (농사 2, 운반), 딱따구 (벌목 1), 토실 (가공 1), 포포 (가공 3, 운반).
// Same creature-collector Minecraft style as pal-style-new.mjs (big head, 2x2 eyes, painted pixel faces) with a few more pieces
// for depth (layered fur, props, glowing bits). Bone names follow the templates in make-pet-anims.mjs (biped / quad).
import { fur, flat, edge, faces } from "./lib-petmodel.mjs";
import { bigEyes, lowerFaceMask as lowerFace } from "./pal-style-more.mjs";
const K = "#15131a", W = "#ffffff";

export const EXTRA = {
  // 꿀벌찌 — 꿀단지 꿀벌: fuzzy striped body, pollen-ball antennae, two pairs of glassy wings, carries a dripping honey pot
  bee(m) {
    const Y = "#f6c834", YL = "#ffe68a", B = "#2a2230", BL = "#4a3f52", WG = "#e8f7ff", WGF = "#9cc6e6", HN = "#f0a020", HNL = "#ffd060", PT = "#c98a4a", PTD = "#8a5a2a", PK = "#ff9fb0", FL = "#ff7fa8";
    const stripes = (x, y, z, f, i, j, w, h) => (f === "north" || f === "south") ? (j % 3 === 2 ? B : fur(Y)(x, y, z, f, i, j, w, h)) : (Math.floor(z + 20) % 3 === 0 ? B : fur(Y, { noise: .08 })(x, y, z, f, i, j, w, h));
    const body = [
      m.cube("thorax", [-3.2, 4, -2.5], [3.2, 9, 1], fur(BL)),
      m.cube("abdomen", [-3.8, 3, 0.5], [3.8, 9.5, 7], stripes),
      m.cube("abdomen_tip", [-2.6, 3.8, 6.8], [2.6, 8.2, 8.6], stripes),
      m.cube("stinger", [-.45, 5.4, 8.5], [.45, 6.2, 10], flat(B)),
      m.cube("ruff", [-4.2, 7.6, -3.2], [4.2, 9.8, -.6], fur(YL, { noise: .18 })),
      m.cube("belly_fuzz", [-2.6, 2.6, -1.5], [2.6, 3.4, 5], flat(YL)),
    ];
    const head = [
      m.cube("head_cube", [-4.5, 6, -9], [4.5, 13.5, -2], bigEyes(lowerFace(fur(Y), YL, 4, 2), 1, 2, { iris: "#3a2a10", blush: PK })),
      m.cube("head_band", [-4.6, 12.4, -9.1], [4.6, 13.7, -1.9], flat(B)),
      m.cube("smile", [-1, 7.6, -9.2], [1, 8, -9], flat("#7a3a20")),
      m.cube("antenna_l", [-2.4, 13.6, -6.2], [-1.8, 16.4, -5.6], flat(B)), m.cube("pollen_l", [-2.9, 16.2, -6.7], [-1.3, 17.8, -5.1], edge(HNL, HN)),
      m.cube("antenna_r", [1.8, 13.6, -6.2], [2.4, 16.4, -5.6], flat(B)), m.cube("pollen_r", [1.3, 16.2, -6.7], [2.9, 17.8, -5.1], edge(HNL, HN)),
      m.cube("flower", [2.6, 13.4, -4.2], [4, 14.4, -2.8], flat(FL)), m.cube("flower_eye", [3, 14.4, -3.8], [3.6, 14.8, -3.2], flat(HNL)),
    ];
    const wing = (n, s) => m.G(n, [s * 3, 9, -.5], [
      m.cube(n + "_upper", s < 0 ? [-10, 9, -1.5] : [3.2, 9, -1.5], s < 0 ? [-3.2, 9.6, 4.5] : [10, 9.6, 4.5], (x, y, z, f, i, j, w, h) => i === 0 || j === 0 || i === w - 1 || j === h - 1 ? WGF : (i + j) % 5 === 0 ? WGF : WG),
      m.cube(n + "_lower", s < 0 ? [-7.5, 8.2, 2.5] : [3.2, 8.2, 2.5], s < 0 ? [-3.2, 8.7, 6.5] : [7.5, 8.7, 6.5], edge(WG, WGF)),
    ]);
    const pot = [
      m.cube("pot_body", [-2.2, 2.4, -11.4], [2.2, 5.8, -8], (x, y, z, f, i, j, w, h) => j === h - 1 ? PTD : j === 1 ? "#e8b060" : PT),
      m.cube("pot_rim", [-2.5, 5.6, -11.7], [2.5, 6.3, -7.7], flat(PTD)),
      m.cube("pot_honey", [-1.8, 6.2, -11.3], [1.8, 6.6, -8.1], flat(HN)),
      m.cube("pot_drip", [-1.9, 4.2, -11.6], [-1.1, 6.3, -11.3], flat(HNL)),
      m.cube("arm_l_hold", [-3.2, 4.6, -9], [-2, 6.2, -6], flat(B)), m.cube("arm_r_hold", [2, 4.6, -9], [3.2, 6.2, -6], flat(B)),
    ];
    const leg = (n, x) => m.G(n, [x, 3, -.5], [m.cube(n + "_cube", [x - .5, 0, -1.2], [x + .5, 3.2, 0], flat(B)), m.cube(n + "_mid", [x - .5, .6, 1.4], [x + .5, 3.2, 2.4], flat(B))]);
    return [m.G("root", [0, 3, 0], [m.G("body", [0, 6, 0], [...body, m.G("head", [0, 8, -3], head), wing("wing_l", -1), wing("wing_r", 1), m.G("pot_eat", [0, 4.5, -9.5], pot)]),
      leg("leg_l", -1.8), leg("leg_r", 1.8)])];
  },

  // 딱따구 — 나무꾼 딱따구리: red crest, black-and-white plumage, chisel beak, plaid lumberjack scarf, stiff prop tail, tiny hatchet on its back
  woodpecker(m) {
    const R = "#e0362c", RD = "#a82218", BK = "#24222a", BKL = "#3c3a44", WH = "#f4f1ea", BE = "#5a5560", BED = "#33303a", PL = "#c8382c", PLD = "#7a1e18", WD = "#9a6a3a", ST = "#b8bec8";
    const plumage = (x, y, z, f, i, j, w, h) => (f === "east" || f === "west") ? ((j % 2 === 0 && i % 3 === 1) ? WH : fur(BK)(x, y, z, f, i, j, w, h)) : f === "north" ? fur(WH)(x, y, z, f, i, j, w, h) : fur(BK)(x, y, z, f, i, j, w, h);
    const body = [
      m.cube("body_cube", [-3.2, 2, -3], [3.2, 9.5, 3.5], plumage),
      m.cube("chest_fluff", [-2.6, 3, -3.6], [2.6, 7.5, -2.9], fur(WH, { noise: .12 })),
      m.cube("scarf", [-3.6, 8.4, -3.5], [3.6, 10.2, 3.9], (x, y, z, f, i, j) => (i + j) % 2 ? PL : PLD),
      m.cube("scarf_end", [2.4, 5.8, -3.9], [3.6, 8.6, -3.3], (x, y, z, f, i, j) => (i + j) % 2 ? PL : PLD),
      m.cube("hatchet_haft", [-.4, 4, 3.4], [.4, 10, 4.2], flat(WD)), m.cube("hatchet_head", [-.6, 8.4, 4], [.6, 10.4, 6.4], edge(ST, "#7c838e")),
    ];
    const head = [
      m.cube("head_cube", [-4, 9, -5.5], [4, 16, 2], bigEyes((x, y, z, f, i, j, w, h) => f === "north" && j >= 3 ? WH : fur(BK)(x, y, z, f, i, j, w, h), 1, 2, { iris: "#2a1a10", blush: "#f08a7a" })),
      m.cube("cheek_stripe_l", [-4.1, 11, -5], [-3.9, 12, 1.5], flat(WH)), m.cube("cheek_stripe_r", [3.9, 11, -5], [4.1, 12, 1.5], flat(WH)),
      m.cube("beak", [-.9, 11, -9.2], [.9, 12.6, -5.5], edge(BE, BED)), m.cube("beak_tip", [-.5, 11.2, -10], [.5, 12, -9.2], flat(BED)),
      m.cube("crest", [-2.6, 15.8, -4.5], [2.6, 18, 1.5], fur(R, { noise: .12 })),
      m.cube("crest_top", [-1.6, 17.8, -2.5], [1.6, 19.2, 2.5], flat(R)), m.cube("crest_back", [-1, 16, 1.5], [1, 18.4, 3.6], flat(RD)),
    ];
    const tail = [m.cube("tail_cube", [-1.8, 1, 3], [1.8, 2.4, 8], (x, y, z, f, i, j) => i % 2 ? BK : BKL), m.cube("tail_tip", [-1.4, .4, 7.6], [1.4, 1.4, 9], flat(BK))];
    const wing = (n, s) => m.G(n, [s * 3.2, 8.5, 0], [m.cube(n + "_cube", s < 0 ? [-4.2, 3, -2] : [3.2, 3, -2], s < 0 ? [-3.2, 8.8, 3.2] : [4.2, 8.8, 3.2], (x, y, z, f, i, j) => j % 2 === 0 && i % 2 === 0 ? WH : BK)]);
    const foot = (n, x) => m.G(n, [x, 2, -.5], [m.cube(n + "_leg", [x - .4, .8, -.9], [x + .4, 2.2, -.1], flat("#6a6470")), m.cube(n + "_toes", [x - 1, 0, -2.2], [x + 1, .8, .4], flat("#6a6470"))]);
    return [m.G("root", [0, 0, 0], [m.G("body", [0, 5, 0], [...body, m.G("head", [0, 9.5, -1], head), m.G("tail", [0, 2, 3], tail, [20, 0, 0]), wing("wing_l", -1), wing("wing_r", 1)]),
      foot("leg_l", -1.6), foot("leg_r", 1.6)])];
  },

  // 토실 — 공방 햄스터: chubby golden hamster, stuffed cheeks, round pink ears, a little tool belt (hammer + wrench), holds a sunflower seed
  hamster(m) {
    const G = "#e8a04a", GL = "#f6c88a", WB = "#fff2dc", PK = "#ff9aa8", PKD = "#e0707e", BL = "#6a4428", BLL = "#8a5e36", ST = "#c0c6d0", STD = "#7c838e", WD = "#a8723c", SD = "#3a3a3a", SL = "#e8e0c8";
    const body = [
      m.cube("body_cube", [-4.2, 2, -3.8], [4.2, 9.5, 4.2], faces({ north: fur(WB), down: fur(WB) }, fur(G, { noise: .07 }))),
      m.cube("back_fluff", [-3.6, 9, -1.5], [3.6, 10.2, 4], fur(GL, { noise: .15 })),
      m.cube("belt", [-4.4, 4.4, -4], [4.4, 5.6, 4.4], (x, y, z, f, i) => i % 4 === 0 ? BLL : BL),
      m.cube("buckle", [-.7, 4.2, -4.3], [.7, 5.8, -3.9], edge("#f2c14e", "#b8892a")),
      m.cube("hammer_haft", [-5, 2.6, -1], [-4.4, 6.4, -.2], flat(WD)), m.cube("hammer_head", [-5.3, 6, -2], [-4.2, 7.2, .8], edge(ST, STD)),
      m.cube("wrench", [4.4, 2.4, -1.2], [5, 6.4, -.4], flat(ST)), m.cube("wrench_jaw", [4.2, 6, -1.6], [5.2, 7, 0], flat(STD)),
      m.cube("tail_nub", [-.8, 3, 4.1], [.8, 4.4, 5], flat(GL)),
    ];
    const head = [
      m.cube("head_cube", [-4.8, 7.5, -8.8], [4.8, 15, -2], bigEyes(lowerFace(fur(G), WB, 4, 2), 1, 2, { iris: "#2a1608", blush: PK })),
      m.cube("cheek_l", [-6, 7.6, -7.4], [-4.6, 10.6, -3.6], fur(WB, { noise: .1 })), m.cube("cheek_r", [4.6, 7.6, -7.4], [6, 10.6, -3.6], fur(WB, { noise: .1 })),
      m.cube("nose", [-.6, 9.8, -9.1], [.6, 10.6, -8.8], flat(PKD)), m.cube("teeth", [-.5, 8.4, -9], [.5, 9.2, -8.8], flat(W)),
      m.cube("ear_l", [-4.6, 14.6, -5.5], [-2.4, 16.8, -4], faces({ north: flat(PK) }, fur(G))), m.cube("ear_r", [2.4, 14.6, -5.5], [4.6, 16.8, -4], faces({ north: flat(PK) }, fur(G))),
      m.cube("goggles", [-4.9, 13.2, -8.9], [4.9, 14, -2], flat(BL)), m.cube("goggle_l", [-3.6, 13.6, -9.2], [-1.4, 15.2, -8.7], edge("#9fe0ff", BL)), m.cube("goggle_r", [1.4, 13.6, -9.2], [3.6, 15.2, -8.7], edge("#9fe0ff", BL)),
    ];
    const seed = [m.cube("seed_a", [-1.2, 5.4, -11], [1.2, 7.8, -9.2], (x, y, z, f, i, j) => i % 2 ? SD : SL), m.cube("seed_tip", [-.6, 7.8, -10.6], [.6, 8.6, -9.6], flat(SD))];
    const leg = (n, x, z, top) => m.G(n, [x, top, z], [m.cube(n + "_cube", [x - 1.1, 0, z - 1.1], [x + 1.1, top, z + 1.1], (X, y) => y < 1 ? PK : fur(G)(X, y, z, "north"))]);
    return [m.G("root", [0, 2, 0], [m.G("body", [0, 5, 0], [...body, m.G("head", [0, 8, -3.5], [...head]), m.G("acorn_eat", [0, 6.5, -9.8], seed)]),
      leg("leg_fl", -2.3, -2.6, 2.4), leg("leg_fr", 2.3, -2.6, 2.4), leg("leg_bl", -2.4, 2.6, 2.4), leg("leg_br", 2.4, 2.6, 2.4)])];
  },

  // 포포 — 별빛 요정: a floating star sprite, round head with a glowing star crown, lilac petal dress, butterfly wings with glowing veins, star wand
  fairy(m) {
    const SK = "#ffe2d4", SKD = "#f2c2ae", HR = "#b8a4ff", HRD = "#8a74e0", DR = "#d8b8ff", DRD = "#a882f0", DRL = "#f2e6ff", WG = "#dff0ff", WGV = "#9ad8ff", ST = "#ffe36a", STD = "#f2b81c", WD = "#c8a0ff";
    const body = [
      m.cube("dress_top", [-2.6, 6, -2], [2.6, 9.5, 2], fur(DR, { noise: .05 })),
      m.cube("dress_skirt", [-3.8, 2.4, -3], [3.8, 6.4, 3], (x, y, z, f, i, j, w, h) => j === h - 1 ? DRD : (i % 3 === 1 ? DRL : DR)),
      m.cube("skirt_hem", [-4.2, 2, -3.4], [4.2, 2.8, 3.4], (x, y, z, f, i) => i % 2 ? DRD : DR),
      m.cube("sash", [-2.8, 6.4, -2.2], [2.8, 7.2, 2.2], flat(STD)), m.cube("sash_gem", [-.6, 6.2, -2.5], [.6, 7.4, -2.1], flat(ST)),
      m.cube("arm_l", [-3.6, 6.2, -1], [-2.6, 9, .4], flat(SK)), m.cube("arm_r", [2.6, 6.2, -1.8], [3.6, 9, -.4], flat(SK)),
      m.cube("wand", [3.2, 6, -4.5], [3.8, 11.5, -3.9], flat(WD)),
    ];
    const glow = [
      m.cube("wand_star", [2.3, 11.3, -5.2], [4.7, 13.7, -3.2], (x, y, z, f, i, j, w, h) => (i === 0 || i === w - 1) && (j === 0 || j === h - 1) ? STD : ST),
      m.cube("crown_star", [-1.4, 17.4, -1.4], [1.4, 20, 1.4], (x, y, z, f, i, j, w, h) => (i === 0 || i === w - 1) && (j === 0 || j === h - 1) ? STD : ST),
      m.cube("crown_spark_l", [-3.6, 17, -.6], [-2.6, 18, .4], flat(ST)), m.cube("crown_spark_r", [2.6, 17, -.6], [3.6, 18, .4], flat(ST)),
    ];
    const head = [
      m.cube("head_cube", [-4.2, 9.2, -4.2], [4.2, 16.8, 4], bigEyes(fur(SK), 1, 3, { iris: "#7a5ae0", pupil: "#2a1a50", blush: "#ffa8c0" })),
      m.cube("hair_top", [-4.5, 15.6, -4.5], [4.5, 17.6, 4.4], fur(HR, { noise: .1 })),
      m.cube("hair_back", [-4.5, 10.4, 1.6], [4.5, 16.6, 4.6], fur(HR, { noise: .1 })),
      m.cube("bang_l", [-4.5, 14.4, -4.6], [-1.6, 16, -4.2], flat(HRD)), m.cube("bang_r", [1, 14.6, -4.6], [4.5, 16, -4.2], flat(HRD)),
      m.cube("lock_l", [-4.7, 10.6, -3], [-4.2, 15.6, 1], flat(HR)), m.cube("lock_r", [4.2, 10.6, -3], [4.7, 15.6, 1], flat(HR)),
      m.cube("ear_l", [-5.4, 12.4, -1.2], [-4.2, 14.6, .2], flat(SKD)), m.cube("ear_r", [4.2, 12.4, -1.2], [5.4, 14.6, .2], flat(SKD)),
      m.G("eyes_glow", [0, 17, 0], glow.slice(1)),
    ];
    const veins = (x, y, z, f, i, j, w, h) => i === 0 || j === 0 || i === w - 1 || j === h - 1 ? WGV : (i === j || i === w - 1 - j) ? WGV : WG;
    const wing = (n, s) => m.G(n, [s * 1.5, 9, 2], [
      m.cube(n + "_upper", s < 0 ? [-8.5, 9.5, 2] : [1.5, 9.5, 2], s < 0 ? [-1.5, 16.5, 2.6] : [8.5, 16.5, 2.6], veins),
      m.cube(n + "_lower", s < 0 ? [-6.5, 5.5, 2.1] : [1.5, 5.5, 2.1], s < 0 ? [-1.5, 9.8, 2.6] : [6.5, 9.8, 2.6], veins),
      m.cube(n + "_dot", s < 0 ? [-6.5, 13, 1.9] : [5.5, 13, 1.9], s < 0 ? [-5.5, 14, 2.1] : [6.5, 14, 2.1], flat(ST)),
    ]);
    const leg = (n, x) => m.G(n, [x, 2.4, 0], [m.cube(n + "_cube", [x - .5, .4, -.5], [x + .5, 2.6, .5], flat(SK)), m.cube(n + "_shoe", [x - .7, 0, -1], [x + .7, .6, .6], flat(DRD))]);
    return [m.G("root", [0, 2, 0], [m.G("body", [0, 5, 0], [...body, m.G("wand_glow", [3.5, 12.5, -4.2], glow.slice(0, 1)), m.G("head", [0, 9.5, 0], head), wing("wing_l", -1), wing("wing_r", 1)]),
      leg("leg_l", -1.3), leg("leg_r", 1.3)])];
  },
};
