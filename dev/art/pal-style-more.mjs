// Restyled builders for the remaining pets (see make-pal-style.mjs for the style rules). Imported by make-pal-style.mjs.
// Every bone keeps its old name AND pivot (origin) so the existing animations in make-pet-anims.mjs still fit.
import { fur, flat, edge, faces, hash } from "./lib-petmodel.mjs";
const K = "#15131a", W = "#ffffff";
export const bigEyes = (base, ex, ey, { iris = "#3a2a1e", pupil = K, blush = null, h2 = true } = {}) => (x, y, z, f, i, j, w, h) => {
  if (f === "north") {
    const left = i === ex || i === ex + 1, right = i === w - 1 - ex || i === w - 2 - ex, hi = i === ex || i === w - 2 - ex;
    if ((left || right) && j === ey) return hi ? W : iris;
    if ((left || right) && j === ey + 1 && h2) return pupil;
    if (blush && j === ey + 2 && (i === ex - 1 || i === w - ex)) return blush;
  }
  return base(x, y, z, f, i, j, w, h);
};
/** lower-face mask: rows >= row on the north face (between the side columns) painted with 'mask' */
const lowerFace = (base, mask, row, side = 1) => (x, y, z, f, i, j, w, h) => f === "north" && j >= row && i >= side && i < w - side ? mask : base(x, y, z, f, i, j, w, h);
/** Two 3-wide eyes on the north face (cols 1..3 and w-4..w-2, rows ey, ey+1), painted per cell by cell(k, row): k 0 = outer, 2 = inner; null = keep. */
const eyes3 = (base, ey, cell) => (x, y, z, f, i, j, w, h) => {
  if (f === "north" && (j === ey || j === ey + 1)) {
    const k = i >= 1 && i <= 3 ? i - 1 : i >= w - 4 && i <= w - 2 ? w - 2 - i : -1;
    if (k >= 0) { const c = cell(k, j - ey); if (c) return c; }
  }
  return base(x, y, z, f, i, j, w, h);
};
const pawLeg = (m, n, o, x, z, len, t, col, paw) => m.G(n, o, [m.cube(n + "_cube", [x - t, o[1] - len, z - t], [x + t, o[1], z + t], (X, y) => y < o[1] - len + 1.2 ? paw : fur(col)(X, y, z, "north"))]);

export const MORE = {
  // 짱새 — 밀짚모자 참새: round fluff ball, straw hat with a sprout on top (crest)
  sparrow(m) {
    const B = "#9a6a42", BL = "#d9b48a", CR = "#f4e6cc", BK = "#f2a23a", HT = "#e8c766", HTD = "#b9953f", SP = "#6cc04a";
    const body = [m.cube("body_cube", [-3.5, 2.5, -3.5], [3.5, 8, 3.5], lowerFace(fur(B, { noise: .12 }), CR, 1))];
    const head = [m.cube("head_cube", [-4, 6.5, -5.5], [4, 12.5, 1.5], bigEyes(lowerFace(fur(B), CR, 4), 1, 2, { blush: "#f3a0a0" })), m.cube("beak", [-1, 7.8, -6.6], [1, 9, -5.5], edge(BK, "#c77a1a")),
      m.cube("hat_brim", [-5, 12.3, -6.3], [5, 12.9, 2.3], edge(HT, HTD)), m.cube("hat_top", [-3, 12.9, -4], [3, 14.6, 0], (x, y, z, f, i, j) => j === 1 ? "#c8423a" : HT)];
    const crest = [m.cube("sprout_stem", [-.3, 14.6, -2.3], [.3, 16.2, -1.7], flat(SP)), m.cube("sprout_leaf_l", [-1.8, 15.8, -2.3], [-.3, 16.6, -1.7], flat(SP)), m.cube("sprout_leaf_r", [.3, 16, -2.3], [1.8, 16.8, -1.7], flat("#8bd66a"))];
    const wing = (n, s) => m.G(n, [s * 3.5, 5, -.5], [m.cube(n + "_cube", s < 0 ? [-4.4, 3, -2.5] : [3.5, 3, -2.5], s < 0 ? [-3.5, 7, 2.5] : [4.4, 7, 2.5], (x, y, z, f, i, j) => j % 2 ? BL : B)]);
    const tail = [m.cube("tail_cube", [-1.5, 4, 3.5], [1.5, 5, 6.5], flat("#7a5232"))];
    const leg = (n, x) => m.G(n, [x, 3, -.5], [m.cube(n + "_cube", [x - .4, 0, -.9], [x + .4, 3, -.1], flat(BK)), m.cube(n + "_toe", [x - .8, 0, -1.8], [x + .8, .5, -.1], flat(BK))]);
    return [m.G("root", [0, 0, 0], [m.G("body", [0, 3, -.5], [...body, wing("wing_l", -1), wing("wing_r", 1), m.G("tail", [0, 4.5, 4], tail), m.G("head", [0, 8, -2], [...head, m.G("crest", [0, 11, -2], crest)])]), leg("leg_l", -1.5), leg("leg_r", 1.5)])];
  },
  // 검은고양이 — 벌목꾼 고양이: black cat, golden eyes, red plaid neckerchief, white tail tip
  cat(m) {
    const KB = "#24222b", KL = "#3a3744", Y = "#ffcc33", RD = "#c8383a", RDD = "#8e2224", PK = "#f2a3b3";
    const plaid = (x, y, z, f, i, j) => (i % 3 === 0 || j % 2 === 0) ? RDD : RD;
    const body = [m.cube("body_cube", [-3, 4, -4], [3, 9, 5], fur(KB, { noise: .06 })), m.cube("scarf", [-3.4, 7.5, -5], [3.4, 9.6, -3.4], plaid), m.cube("scarf_knot", [-1, 5.5, -5.6], [1, 7.6, -4.9], plaid)];
    // cat eyes: almond-shaped lime-gold eyes with a vertical slit pupil and a small highlight (instead of the round chibi eyes)
    const LG = "#c6e348", LGD = "#8fb52a";
    const catFace = eyes3(lowerFace(fur(KB), KL, 5, 2), 2, (k, r) => k === 1 ? K : r === 0 ? (k === 0 ? W : LG) : (k === 0 ? LGD : LG));
    const head = [m.cube("head_cube", [-4.5, 8, -10], [4.5, 15, -3.5], (x, y, z, f, i, j, w, h) => f === "north" && j === 4 && (i === 0 || i === w - 1) ? PK : catFace(x, y, z, f, i, j, w, h)), m.cube("nose", [-.5, 9.6, -10.3], [.5, 10.2, -10], flat(PK)),
      m.cube("whisker_l", [-6, 9.5, -9.8], [-4.5, 9.8, -9.6], flat("#c9c4d6")), m.cube("whisker_r", [4.5, 9.5, -9.8], [6, 9.8, -9.6], flat("#c9c4d6"))];
    const ear = s => [m.cube(`ear_${s}_cube`, s === "l" ? [-4.5, 15, -8] : [1.5, 15, -8], s === "l" ? [-1.5, 18, -6] : [4.5, 18, -6], faces({ north: (x, y, z, f, i, j, w) => i > 0 && i < w - 1 && j > 0 ? PK : KB }, flat(KB)))];
    const tail = [m.cube("tail_cube", [-.8, 8.5, 5], [.8, 10, 12.5], flat(KB))], tip = [m.cube("tail_tip_cube", [-1, 9, 12.5], [1, 13, 14], (x, y) => y > 11.5 ? W : KB)];
    const leg = (n, x, z) => pawLeg(m, n, [x, 5, z], x, z, 5, 1.1, KB, KL);
    return [m.G("root", [0, 0, 0], [m.G("body", [0, 8, 0], [...body, m.G("head", [0, 10, -5], [...head, m.G("ear_l", [-2, 14, -7.5], ear("l")), m.G("ear_r", [2, 14, -7.5], ear("r"))]), m.G("tail", [0, 9, 6], [...tail, m.G("tail_tip", [0, 9.5, 13], tip)])]),
      leg("leg_fl", -2, -2.8), leg("leg_fr", 2, -2.8), leg("leg_bl", -2, 3.8), leg("leg_br", 2, 3.8)])];
  },
  // 마토 — 견습 마법사 토끼: white bunny in a star robe, tall crooked wizard hat, glowing wand
  rabbit(m) {
    const WB = "#f8f6f2", PK = "#f4a7b9", PU = "#6c4bc4", PUD = "#4a3190", GD = "#ffd65a", WD = "#8a5a34", MG = "#9be8ff";
    const robe = (x, y, z, f, i, j) => hash(i, j, 7) < .06 ? GD : j === 0 ? GD : PU;
    const body = [m.cube("body_cube", [-3.5, 4, -2.5], [3.5, 10.5, 2.5], robe), m.cube("robe_hem", [-4, 3.5, -3], [4, 5, 3], flat(PUD))];
    const head = [m.cube("head_cube", [-4.5, 10.5, -4], [4.5, 17, 3.5], bigEyes(fur(WB), 1, 2, { iris: "#c03a5a", blush: PK })), m.cube("nose", [-.5, 12, -4.3], [.5, 12.6, -4], flat(PK))];
    const ear = s => [m.cube(`ear_${s}_cube`, s === "l" ? [-4, 16, -1.5] : [2, 16, -1.5], s === "l" ? [-2, 22, 0] : [4, 22, 0], faces({ north: (x, y, z, f, i, j, w) => i > 0 && i < w - 1 ? PK : WB }, flat(WB)))];
    const hat = [m.cube("hat_brim", [-5.5, 16.6, -5], [5.5, 17.4, 4.5], edge(PU, PUD)), m.cube("hat_cone", [-3, 17.4, -2.5], [3, 21, 2.5], robe), m.cube("hat_tip", [-1.5, 21, -1.5], [1.5, 23, 1.5], flat(PUD)), m.cube("hat_star", [-.6, 23, -.6], [.6, 24.2, .6], flat(GD))];
    const arm = (n, s) => m.G(n, [s * 3, 10, 0], [m.cube(n + "_cube", s < 0 ? [-5, 6, -1] : [3, 6, -1], s < 0 ? [-3, 10, 1] : [5, 10, 1], flat(PU)), m.cube(n + "_paw", s < 0 ? [-5, 5, -1] : [3, 5, -1], s < 0 ? [-3, 6, 1] : [5, 6, 1], flat(WB)),
      ...(s > 0 ? [m.G("wand", [4, 5, -3], [m.cube("wand_stick", [3.6, 5, -3.4], [4.4, 13, -2.6], flat(WD)), m.G("tip_glow", [4, 13, -2], [m.cube("wand_gem", [3.2, 13, -3.8], [4.8, 14.6, -2.2], flat(MG))]),
        m.G("spark_work", [4, 14, -2], [m.G("spark_glow", [4, 14, -2], [m.cube("spark_a", [3, 15, -3], [3.8, 15.8, -2.2], flat(GD)), m.cube("spark_b", [5, 15.6, -3], [5.8, 16.4, -2.2], flat(MG))])])])] : [])]);
    const leg = (n, x) => m.G(n, [x, 5, 0], [m.cube(n + "_cube", [x - 1.4, 0, -2.5], [x + 1.4, 4, 1.4], (X, y) => y < 1 ? "#e9e2d6" : WB)]);
    const tail = [m.cube("tail_puff", [-1.5, 5, 2.5], [1.5, 8, 4.5], flat(WB))];
    return [m.G("root", [0, 0, 0], [m.G("body", [0, 5, 0], [...body, m.G("tail", [0, 6, 2], tail), arm("arm_l", -1), arm("arm_r", 1), m.G("head", [0, 11, 0], [...head, m.G("ear_l", [-4, 14, -1], ear("l")), m.G("ear_r", [4, 14, -1], ear("r")), m.G("hat", [0, 16, 0], hat)])]), leg("leg_l", -2), leg("leg_r", 2)])];
  },
  // 바라 — 귤 얹은 카피바라 목수 (2026-11-26, replaces the red panda; the internal id stays "redpanda" so owned pets, the tutorial
  // starter and saved data keep working): barrel-shaped brown body, boxy head with a blunt dark snout, small round ears, calm
  // half-lidded eyes, a tangerine with a leaf balanced on its head, a stub tail; holds a little wooden mallet while working.
  redpanda(m) {
    const BR = "#9c6c44", BRL = "#bd8a5c", BRD = "#6c4628", SN = "#4a3426", OR = "#ff9a2e", ORD = "#e0731a", LF = "#5fbf4a", LFD = "#3e9131", WD = "#b9844e", WDD = "#7d5531";
    const coat = (x, y, z, f, i, j, w, h) => f === "down" ? BRL : f === "up" ? fur(BRD, { noise: .1 })(x, y, z, f, i, j, w, h) : fur(BR, { noise: .1 })(x, y, z, f, i, j, w, h);
    const body = [m.cube("body_cube", [-3.2, 3.8, -3.4], [3.2, 8.8, 4.2], coat), m.cube("body_rump", [-3, 4.2, 2.4], [3, 8.4, 4.8], coat)];
    // calm eyes: a heavy lid row over a dark eye, a tiny highlight, soft blush
    const face = (x, y, z, f, i, j, w, h) => {
      if (f === "north") {
        const k = i === 1 || i === 2 ? i - 1 : i === w - 2 || i === w - 3 ? w - 2 - i : -1;
        if (k >= 0 && j === 1) return BRD;
        if (k >= 0 && j === 2) return k === 1 ? "#f4ead8" : "#1d140e";
        if (j === 3 && (i === 0 || i === w - 1)) return "#e9a08a";
      }
      return fur(BR)(x, y, z, f, i, j, w, h);
    };
    const head = [m.cube("head_cube", [-4, 7.5, -9.5], [4, 13.5, -3], face),
      // rounded snout: a brown muzzle, a narrower bridge on top and a smaller dark nose cap in front, so the outline steps in
      // like a dome instead of one square block
      m.cube("snout", [-2.6, 7.7, -11.1], [2.6, 10.5, -9.5], fur(BRD)),
      m.cube("snout_bridge", [-2, 10.5, -10.8], [2, 11.1, -9.5], fur(BRD)),
      m.cube("snout_nose", [-1.9, 8.2, -11.6], [1.9, 10.1, -11.1], faces({ north: (x, y, z, f, i, j) => j === 0 && (i === 0 || i === 3) ? "#1d140e" : SN }, flat(SN)))];
    const ear = s => [m.cube(`ear_${s}_cube`, s === "l" ? [-4.3, 12.8, -5.6] : [2.9, 12.8, -5.6], s === "l" ? [-2.9, 14.1, -4.3] : [4.3, 14.1, -4.3], flat(BRD))];
    const fruit = [m.cube("fruit_cube", [-1.6, 13.5, -7.6], [1.6, 15.9, -4.4], (x, y, z, f, i, j) => f === "up" && i === 1 && j === 1 ? ORD : (i + j) % 4 === 0 ? ORD : OR),
      m.cube("fruit_stem", [-.25, 15.9, -6.25], [.25, 16.4, -5.75], flat(LFD)), m.cube("fruit_leaf", [.2, 15.95, -6.6], [2, 16.3, -5.4], (x, y, z, f, i) => i === 1 ? LFD : LF)];
    const food = [m.cube("grass", [-1.6, 7.2, -12.4], [1.6, 8.2, -11.3], (x, y, z, f, i) => i % 2 ? LF : "#7cc35a")];
    const tail = [m.cube("tail_cube", [-.8, 6.2, 4.6], [.8, 7.4, 5.3], flat(BRD))];
    const mallet = [m.cube("mallet_handle", [1.75, .5, -5.4], [2.25, 1, -2.2], flat(WD)), m.cube("mallet_head", [1.1, -.3, -6.9], [2.9, 1.8, -5.3], edge(WD, WDD))];
    const leg = (n, x, z, t, extra = []) => m.G(n, [x, 3, z], [m.cube(n + "_cube", [x - t, 0, z - t], [x + t, 5, z + t], (X, y) => y < 1 ? BRD : fur(BR)(X, y, z, "north")), ...extra]);
    return [m.G("root", [0, 0, 0], [m.G("body", [0, 7, 0], [...body, m.G("head", [0, 10, -4], [...head, m.G("ear_l", [-3.6, 13, -5], ear("l")), m.G("ear_r", [3.6, 13, -5], ear("r")),
      m.G("leaf", [0, 13.5, -6], fruit), m.G("food_eat", [0, 7.7, -11.8], food)]),
      m.G("tail", [0, 6.8, 4.8], tail), leg("leg_fl", -2, -2.3, 1.2), leg("leg_fr", 2, -2.3, 1.2, [m.G("mallet_work", [2, .8, -3], mallet)])]), leg("leg_bl", -2.1, 2.9, 1.35), leg("leg_br", 2.1, 2.9, 1.35)])];
  },
  // 해달수 — 낚시꾼 해달: sleek brown otter, cream face, yellow fisherman rain hat with flaps
  otter(m) {
    const B = "#7a5434", BL = "#a3784e", CR = "#f1dcbc", HY = "#ffd23f", HYD = "#d9a417", N = "#2a1c14";
    const body = [m.cube("body_cube", [-3.5, 2.5, -6], [3.5, 8, 5], faces({ down: flat(CR) }, fur(B)))];
    const head = [m.cube("head_cube", [-4.5, 5, -12], [4.5, 11, -6], bigEyes(fur(B), 1, 1, { iris: "#2a1a10", blush: "#e9a08a" }))];
    const muzzle = [m.cube("muzzle_cube", [-2.5, 5, -13], [2.5, 8, -11.6], faces({ north: (x, y, z, f, i, j) => j === 0 && i >= 1 && i <= 3 ? N : CR }, flat(CR))),
      // real-otter whiskers: a fan of long, thin, slightly swept-back whiskers from each side of the muzzle (each whisker is its
      // own tilted bone so they splay instead of lying in rows)
      ...[-1, 1].flatMap(s => [[7.1, -20, 4.6], [6.6, -7, 5.2], [6.1, 5, 5], [5.6, 17, 4.2]].map(([y, roll, len], k) => {
        const bx = s * 2.7, n = (s < 0 ? "whisker_l" : "whisker_r") + k;
        return m.G(n, [bx, y, -12.75], [m.cube(n + "_cube", s < 0 ? [bx - len, y - .09, -12.84] : [bx, y - .09, -12.84], s < 0 ? [bx, y + .09, -12.66] : [bx + len, y + .09, -12.66], flat("#f6f1e8"))],
          [0, s < 0 ? 22 : -22, s < 0 ? roll : -roll]);
      }))];
    const ear = s => [m.cube(`ear_${s}_cube`, s === "l" ? [-5, 9.5, -10] : [4, 9.5, -10], s === "l" ? [-4, 10.5, -9] : [5, 10.5, -9], flat(BL))];
    const hat = [m.cube("hat_crown", [-3.5, 11, -11], [3.5, 13.5, -7], edge(HY, HYD))];
    const flap = (n, from, to) => m.G(n, from.map((v, i) => (v + to[i]) / 2), [m.cube(n + "_cube", from, to, flat(HYD))]);
    const tail = [m.cube("tail_cube", [-1.5, 2.5, 5], [1.5, 4.5, 12], (x, y, z, f, i) => fur(B)(x, y, z, f))];
    const leg = (n, x, z) => pawLeg(m, n, [x, 2.5, z], x, z, 2.5, 1.2, B, N);
    return [m.G("root", [0, 0, 0], [m.G("body", [0, 4, 0], [...body, m.G("head", [0, 7, -7], [...head, m.G("muzzle", [0, 8, -11], muzzle), m.G("ear_l", [-4.5, 10, -9.5], ear("l")), m.G("ear_r", [4.5, 10, -9.5], ear("r")),
      m.G("hat", [0, 12, -9], [...hat, m.G("hat_flap_l", [-3, 12, -9], [m.cube("hat_flap_l_cube", [-5, 11, -11], [-3.5, 12, -7], flat(HYD))]), m.G("hat_flap_r", [3, 12, -9], [m.cube("hat_flap_r_cube", [3.5, 11, -11], [5, 12, -7], flat(HYD))]),
        m.G("hat_flap_b", [0, 12, -7], [m.cube("hat_flap_b_cube", [-3.5, 10.5, -7], [3.5, 12, -5.5], flat(HYD))]), m.G("hat_flap_f", [0, 12, -11], [m.cube("hat_flap_f_cube", [-3.5, 11.6, -12.6], [3.5, 12, -11], flat(HYD))])])]), m.G("tail", [0, 3, 5], tail)]),
      leg("leg_fl", -2.4, -4), leg("leg_fr", 2.4, -4), leg("leg_bl", -2.4, 3.5), leg("leg_br", 2.4, 3.5)])];
  },
  // 딱지 — 수정 등껍질 거북: domed shell with amethyst crystal clusters, sleepy big-eyed head (eyelid)
  tortoise(m) {
    const SH = "#7a6a3c", SHL = "#a08f55", SHD = "#4f4426", SK = "#9bb06a", SKD = "#6f8448", AM = "#b48cf0", AMD = "#7a52c8", AML = "#e2d0ff";
    const shellPat = (x, y, z, f, i, j) => (Math.round(x) % 4 === 0 || Math.round(z) % 4 === 0) ? SHD : (i + j) % 5 === 0 ? SHL : SH;
    const body = [m.cube("shell_cube", [-6, 3, -6], [6, 9, 6.5], shellPat), m.cube("shell_dome", [-4.5, 9, -4.5], [4.5, 10.5, 5], shellPat), m.cube("shell_rim", [-6.5, 2.5, -6.5], [6.5, 3.5, 7], flat(SHD)), m.cube("plastron", [-5, 2, -5], [5, 2.6, 5.5], flat("#d8c78f")),
      m.cube("crystal_a", [-2, 10.5, -1], [0, 14, 1], edge(AM, AMD)), m.cube("crystal_b", [.5, 10.5, 1], [2, 12.8, 2.5], edge(AML, AM)), m.cube("crystal_c", [-3.5, 10, 2], [-2.2, 12, 3.3], edge(AM, AMD)), m.cube("crystal_d", [2.2, 10, -3], [3.6, 11.6, -1.6], flat(AMD))];
    const head = [m.cube("head_cube", [-3.5, 3.5, -12], [3.5, 9, -6.5], bigEyes(fur(SK), 1, 2, { iris: "#2a3a18", blush: "#e9a08a" })), m.cube("beak", [-1.5, 3.5, -12.6], [1.5, 5, -12], flat(SKD))];
    // brows sit above the eyes (eyes span y ~5.3..7.2), one per eye; the neck reaches back into the shell so the head never floats
    const lid = [m.cube("eyelid_cube", [-2.7, 7.45, -12.15], [-.4, 7.95, -12.05], flat(SKD)), m.cube("eyelid_r_cube", [.4, 7.45, -12.15], [2.7, 7.95, -12.05], flat(SKD))];
    head.push(m.cube("neck", [-2.2, 4, -7], [2.2, 7.6, -3], fur(SK)));
    const tail = [m.cube("tail_cube", [-.8, 3, 6.5], [.8, 4.5, 8.5], flat(SK))];
    const leg = (n, x, z) => pawLeg(m, n, [x, 3, z], x, z, 3, 1.6, SK, SKD);
    return [m.G("root", [0, 0, 0], [m.G("body", [0, 0, 0], [...body, m.G("tail", [0, 4, 6], tail), m.G("head", [0, 5, -6], [...head, m.G("eyelid", [0, 7.7, -12.1], lid)])]),
      leg("leg_fl", -4.5, -3.5), leg("leg_fr", 4.5, -3.5), leg("leg_bl", -4.5, 3.5), leg("leg_br", 4.5, 3.5)])];
  },
  // 피토 — 손재주 너구리: grey raccoon with bandit mask, goggles pushed up, ringed tail, little tool belt
  raccoon(m) {
    const G = "#8d8e96", GL = "#c9cad0", MK = "#2a2a30", BL = "#7a5a3a", GO = "#7fd0ff", BRS = "#c9973b";
    const face = (x, y, z, f, i, j, w, h) => f === "north" && j >= 2 && j <= 3 ? MK : f === "north" && j >= 4 && i >= 2 && i <= w - 3 ? GL : fur(G)(x, y, z, f, i, j, w, h);
    const body = [m.cube("body_cube", [-3.5, 4, -3], [3.5, 10, 3], faces({ north: fur(GL) }, fur(G))), m.cube("belt", [-3.8, 5, -3.3], [3.8, 6, 3.3], flat(BL)), m.cube("belt_tool", [2.4, 3.4, -3.6], [3.4, 6, -3.2], flat(BRS))];
    const head = [m.cube("head_cube", [-4.5, 9.5, -5], [4.5, 16, 2.5], bigEyes(face, 1, 2, { iris: "#e8c45a", blush: "#e9a0a0" })), m.cube("nose", [-.6, 10.6, -5.3], [.6, 11.3, -5], flat(MK)),
      m.cube("goggle_strap", [-4.7, 14.2, -5.2], [4.7, 15, 2.7], flat(BL)), m.cube("goggle_l", [-3.6, 14.4, -5.6], [-.8, 16, -5.1], edge(GO, BL)), m.cube("goggle_r", [.8, 14.4, -5.6], [3.6, 16, -5.1], edge(GO, BL))];
    const ear = s => [m.cube(`ear_${s}_cube`, s === "l" ? [-4.5, 15.5, -3] : [2, 15.5, -3], s === "l" ? [-2, 17.5, -1.5] : [4.5, 17.5, -1.5], faces({ north: flat(GL) }, flat(MK)))];
    const arm = (n, fn, s) => m.G(n, [s * 4, 7, -2], [m.cube(n + "_cube", s < 0 ? [-5, 5, -3] : [3.5, 5, -3], s < 0 ? [-3.5, 8, -1] : [5, 8, -1], flat(G)), m.G(fn, [s * 4, 4, -2], [m.cube(fn + "_cube", s < 0 ? [-5, 3, -3] : [3.5, 3, -3], s < 0 ? [-3.5, 5, -1] : [5, 5, -1], flat(MK))])]);
    const food = [m.cube("food_cube", [-1, 4.4, -8], [1, 6, -6.4], flat("#e85a3a"))];
    const tail = [m.cube("tail_cube", [-1.8, 3.5, 3], [1.8, 6.5, 10], (x, y, z) => Math.floor(z) % 2 === 0 ? MK : G)];
    // hind legs under the middle of the body (was behind it) and reaching up into the body so they never look detached
    const leg = (n, x) => m.G(n, [x, 4, -.5], [m.cube(n + "_thigh", [x - 1.6, 2.4, -2.1], [x + 1.6, 5.6, 1.1], fur(G)), m.cube(n + "_cube", [x - 1.2, 0, -1.7], [x + 1.2, 2.8, .5], (X, y) => y < 1 ? MK : G)]);
    body.push(m.cube("rump", [-3.3, 3.6, -1.6], [3.3, 6.4, 3.2], fur(G)), m.cube("back_slope", [-3, 8.5, 1.5], [3, 9.6, 3.4], fur(G)));
    return [m.G("root", [0, 0, 0], [m.G("body", [0, 1, 0], [...body, m.G("head", [0, 7, -1], [...head, m.G("ear_l", [-2.5, 12, -2.5], ear("l")), m.G("ear_r", [2.5, 12, -2.5], ear("r"))]), arm("arm_l", "fore_l", -1), arm("arm_r", "fore_r", 1), m.G("food_eat", [0, 5, -7], food)]),
      m.G("tail", [0, 4, 3], tail), leg("leg_l", -2), leg("leg_r", 2)])];
  },
  // 두류 — 광부 두더지: velvet mole with a yellow mining helmet + lamp, pink star snout, huge digging claws
  mole(m) {
    const V = "#4a4256", VL = "#6a6078", PK = "#f29ab8", HY = "#f5c431", HYD = "#c99a14", LP = "#fff6b0", CL = "#f2e6d0";
    const body = [m.cube("body_cube", [-4.5, 1, -5], [4.5, 7, 5], fur(V, { noise: .08 }))];
    const head = [m.cube("head_cube", [-4, 1.5, -10], [4, 7.5, -4.5], bigEyes(fur(VL), 1, 2, { iris: "#1d1a24", pupil: K, blush: PK })),
      m.cube("helmet", [-4.3, 7, -9.5], [4.3, 9.5, -4.8], edge(HY, HYD)), m.cube("helmet_brim", [-4.6, 7, -10.4], [4.6, 7.6, -9.3], flat(HYD)), m.cube("lamp", [-1.2, 8, -10.3], [1.2, 9.6, -9.5], (x, y, z, f) => f === "north" ? LP : "#9a9aa2")];
    const snout = [m.cube("snout_cube", [-1.5, 2.5, -11.5], [1.5, 4.5, -10], faces({ north: (x, y, z, f, i, j) => (i + j) % 2 ? PK : "#ffc0d4" }, flat(PK)))];
    const tail = [m.cube("tail_cube", [-.5, 3, 5], [.5, 4, 7], flat(PK))];
    const claw = (n, x, z, s) => m.G(n, [x, 2, z], [m.cube(n + "_arm", [x - 1.2, 0, z - 1.2], [x + 1.2, 2, z + 1.2], flat(V)), m.cube(n + "_claw", s < 0 ? [x - 2.5, 0, z - 2.6] : [x - .5, 0, z - 2.6], s < 0 ? [x + .5, 1, z - 1] : [x + 2.5, 1, z - 1], (X, y, Z, f, i) => i % 2 ? CL : "#d6c6aa")]);
    const leg = (n, x, z) => pawLeg(m, n, [x, 2, z], x, z, 2, 1, V, PK);
    const dirt = [m.cube("dirt_a", [-2, 0, -9], [0, 1.5, -7], flat("#7a5232")), m.cube("dirt_b", [.5, 0, -8.5], [2, 1, -7.2], flat("#5e3e24"))];
    return [m.G("root", [0, 0, 0], [m.G("body", [0, 2, 0], [...body, m.G("head", [0, 2, -5], [...head, m.G("snout", [0, 3, -9], snout)]), m.G("tail", [0, 5, 6], tail)]),
      claw("leg_fl", -4, -5, -1), claw("leg_fr", 4, -5, 1), leg("leg_bl", -5, 3), leg("leg_br", 5, 3), m.G("dirt_work", [0, 1, -8], dirt)])];
  },
  // 달투 — 초승달 곰: big black bear, glowing crescent moon on the chest, round ears, sleepy-calm eyes
  moonbear(m) {
    const KB = "#22202a", KL = "#38354a", MN = "#fff0a8", MND = "#ffd34a", MZ = "#c9a78a";
    const body = [m.cube("body_cube", [-4.5, 4, -5], [4.5, 11, 5], fur(KB, { noise: .08 })), m.cube("hump", [-3.5, 11, -3.5], [3.5, 12.5, 2], fur(KB))];
    // sharp, scary eyes: amber eyes slanting down toward the nose with a red slit and a heavy angled brow
    const head = [m.cube("head_cube", [-5, 7, -11], [5, 14, -4], bigEyes(fur(KB), 2, 3, { iris: "#e9e4d8", pupil: "#0b0a0f" })), m.cube("muzzle", [-2.5, 7, -12.5], [2.5, 10, -11], faces({ north: (x, y, z, f, i, j) => j === 0 && i >= 1 && i <= 3 ? K : MZ }, flat(MZ)))];
    const ear = s => [m.cube(`ear_${s}_cube`, s === "l" ? [-5, 13.5, -8] : [2.5, 13.5, -8], s === "l" ? [-2.5, 15.5, -6.5] : [5, 15.5, -6.5], faces({ north: flat(KL) }, flat(KB)))];
    const moon = [m.cube("moon_a", [-2.5, 5, -5.3], [-.5, 9, -5.05], flat(MN)), m.cube("moon_b", [-.5, 4.5, -5.3], [1.5, 5.5, -5.05], flat(MND)), m.cube("moon_c", [-.5, 8.5, -5.3], [1.5, 9.5, -5.05], flat(MND))];
    const leg = (n, x, z) => m.G(n, [x, 4, z], [m.cube(n + "_cube", [x - 1.45, 0, z - 2.1], [x + 1.45, 6, z + 2.1], (X, y) => y < 1.2 ? KL : fur(KB)(X, y, z, "north"))]);
    return [m.G("root", [0, 0, 0], [m.G("body", [0, 6, 0], [...body, m.G("head", [0, 9, -4], [...head, m.G("ear_l", [-2, 12, -7], ear("l")), m.G("ear_r", [2, 12, -7], ear("r"))]), m.G("moon_glow", [0, 6, -5], moon)]),
      leg("leg_fl", -2.5, -3.5), leg("leg_fr", 2.5, -3.5), leg("leg_bl", -2.5, 3.5), leg("leg_br", 2.5, 3.5)])];
  },
  // 장꾸펫 — 장난꾸러기 족제비: long cream-and-brown body, bandit mask, mischievous wink, striped scarf
  ferret(m) {
    const B = "#8a6a4a", CR = "#f6e6cc", MK = "#4a3424", SC = "#4aa8e0", SCD = "#2b78b0";
    const face = (x, y, z, f, i, j, w, h) => f === "north" && j >= 1 && j <= 2 ? MK : f === "north" ? CR : fur(B)(x, y, z, f, i, j, w, h);
    const back = [m.cube("back_cube", [-2.5, 3, 0], [2.5, 7, 6], fur(B))], front = [m.cube("front_cube", [-2.5, 3, -5], [2.5, 7, 0], fur(B)), m.cube("scarf", [-2.8, 5.5, -5.4], [2.8, 7.4, -3.6], (x, y, z, f, i) => i % 2 ? SCD : SC)];
    const head = [m.cube("head_cube", [-3.5, 4.5, -10], [3.5, 10, -5], face)];
    const snout = [m.cube("snout_cube", [-1.2, 5, -11], [1.2, 6.8, -10], flat(CR)), m.cube("nose", [-.5, 6.2, -11.2], [.5, 6.8, -11], flat(MK))];
    const ear = s => [m.cube(`ear_${s}_cube`, s === "l" ? [-3.5, 10, -8.5] : [1.5, 10, -8.5], s === "l" ? [-1.5, 11.5, -7.5] : [3.5, 11.5, -7.5], flat(CR))];
    const eyesN = [m.cube("eyes_l", [-2.6, 7.6, -10.15], [-1, 8.8, -10.05], (x, y, z, f, i, j) => i === 0 && j === 0 ? W : K), m.cube("eyes_r", [1, 7.6, -10.15], [2.6, 8.8, -10.05], (x, y, z, f, i, j) => i === 0 && j === 0 ? W : K)];
    const wink = [m.cube("wink_l", [-2.6, 7.6, -10.15], [-1, 8.8, -10.05], (x, y, z, f, i, j) => i === 0 && j === 0 ? W : K), m.cube("wink_r", [1, 8.1, -10.25], [2.6, 8.5, -10.15], flat(K))];
    const tail = [m.cube("tail_cube", [-1, 4, 6], [1, 6, 12], (x, y, z) => z > 10 ? MK : B)];
    const leg = (n, x, z) => pawLeg(m, n, [x, 3, z], x, z, 3, .9, B, MK);
    return [m.G("root", [0, 0, 0], [m.G("body_back", [0, 5, 3], [...back, m.G("tail", [0, 4, 6], tail), leg("leg_bl", -1.4, 3), leg("leg_br", 1.4, 3),
      m.G("body_front", [0, 5, -3], [...front, m.G("head", [0, 6, -6], [...head, m.G("snout", [0, 7, -10], snout), m.G("ear_l", [-2, 9, -8.5], ear("l")), m.G("ear_r", [2, 9, -8.5], ear("r")), m.G("eyes_rest", [0, 7, -10], eyesN), m.G("wink_work", [0, 7, -10], wink)]),
        leg("leg_fl", -1.4, -3.5), leg("leg_fr", 1.4, -3.5)])])])];
  },
  // 천사픽 — 천사 돼지: plump pink piglet, golden halo, small fluffy wings, heart blush
  angelpig(m) {
    const P = "#f6b3c4", PL = "#ffd3de", PD = "#e08aa2", HL = "#ffe27a", HLD = "#f2b81c", WG = "#ffffff", WGS = "#e3eef8";
    const body = [m.cube("body_cube", [-4, 1.5, -3.5], [4, 7.5, 4.5], fur(P, { noise: .05 }))];
    const head = [m.cube("head_cube", [-4.5, 4, -9.5], [4.5, 11, -3], bigEyes(fur(P), 1, 2, { iris: "#7a4a9a", blush: "#ff7a9a" })), m.cube("snout", [-2, 5, -10.5], [2, 7.2, -9.5], faces({ north: (x, y, z, f, i, j) => j === 1 && (i === 1 || i === 2) ? PD : PL }, flat(PL)))];
    const ear = s => [m.cube(`ear_${s}_cube`, s === "l" ? [-4.5, 10.5, -6] : [2.5, 10.5, -6], s === "l" ? [-2.5, 12, -4.5] : [4.5, 12, -4.5], flat(PD))];
    const halo = [m.cube("halo_n", [-2.5, 13, -8], [2.5, 13.6, -7.4], flat(HL)), m.cube("halo_s", [-2.5, 13, -3.6], [2.5, 13.6, -3], flat(HL)), m.cube("halo_w", [-3.1, 13, -7.4], [-2.5, 13.6, -3.6], flat(HLD)), m.cube("halo_e", [2.5, 13, -7.4], [3.1, 13.6, -3.6], flat(HLD))];
    const wing = (n, s) => m.G(n, [s * 3, 6, 3], [m.cube(n + "_cube", s < 0 ? [-8, 5, 1.5] : [4, 5, 1.5], s < 0 ? [-4, 9, 4] : [8, 9, 4], (x, y, z, f, i, j) => j % 2 ? WGS : WG)]);
    const leg = (n, x, z) => pawLeg(m, n, [x, 1.5, z], x, z, 1.5, 1.1, P, PD);
    return [m.G("root", [0, 0, 0], [m.G("body", [0, 4, 0], [...body, m.G("head", [0, 6, -4], [...head, m.G("ear_l", [-3, 8, -5], ear("l")), m.G("ear_r", [3, 8, -5], ear("r")), m.G("halo_glow", [0, 13, -5.5], halo)]), wing("wing_l", -1), wing("wing_r", 1)]),
      leg("leg_fl", -2, -2), leg("leg_fr", 2, -2), leg("leg_bl", -2, 2.5), leg("leg_br", 2, 2.5)])];
  },
  // 가마 — 사무라이 개구리: round green frog with a straw kasa hat, sheathed katana (drawn blade when working)
  frog(m) {
    const G = "#5fbf4a", GL = "#a6e07a", GD = "#3e8a30", HT = "#e2c066", HTD = "#b0913c", SB = "#3a2a1e", BL = "#dde6ee", RD = "#d4473f";
    const body = [m.cube("body_cube", [-4, 1.5, -3.5], [4, 7, 3.5], faces({ north: fur(GL), down: flat(GL) }, fur(G)))];
    const head = [m.cube("head_cube", [-4.5, 4, -6.5], [4.5, 9, -1], bigEyes(lowerFace(fur(G), GL, 4), 1, 1, { iris: "#d9a417", blush: "#f39a8a" })),
      m.cube("eye_bump_l", [-4, 9, -6], [-1.5, 10.2, -3.5], flat(G)), m.cube("eye_bump_r", [1.5, 9, -6], [4, 10.2, -3.5], flat(G)), m.cube("mouth", [-2, 5.3, -6.6], [2, 5.6, -6.5], flat(GD))];
    // hat centred over the eye bumps / ears (z ~ -4.75) instead of sitting on the back of the head
    const hat = [m.cube("kasa_brim", [-6, 10, -10.25], [6, 10.7, .75], edge(HT, HTD)), m.cube("kasa_mid", [-4, 10.7, -8.25], [4, 11.6, -1.25], flat(HT)), m.cube("kasa_top", [-2, 11.6, -6.25], [2, 12.4, -3.25], flat(HTD))];
    const tongue = [m.cube("tongue_cube", [-.6, 4.6, -9], [.6, 5.2, -6.5], flat("#f27a8a"))];
    const sheath = [m.cube("sheath_cube", [3.3, 2.5, -2], [4.1, 3.3, 5], flat(SB)), m.cube("tsuba", [3.1, 2.3, -2.6], [4.3, 3.5, -2], flat(HTD)), m.cube("hilt", [3.4, 2.6, -4.6], [4, 3.2, -2.6], (x, y, z, f, i) => i % 2 ? RD : SB)];
    const blade = [m.cube("blade_cube", [3.7, 4, -10], [4.3, 4.8, -1], edge(BL, "#a9b4c0")), m.cube("blade_hilt", [3.6, 3.9, -1], [4.4, 4.9, 1.5], (x, y, z, f, i) => i % 2 ? RD : SB)];
    const arm = (n, x) => m.G(n, [x, 3, -3.6], [m.cube(n + "_cube", [x - .8, 1, -4.4], [x + .8, 3.5, -2.8], flat(G))]);
    const leg = (n, x) => m.G(n, [x, 3, 1], [m.cube(n + "_thigh", [x - 1.4, 1, -1], [x + 1.4, 3.5, 3], flat(G)), m.cube(n + "_foot", [x - 1.6, 0, -2.5], [x + 1.6, .8, 1], flat(GD))]);
    return [m.G("root", [0, 0, 0], [m.G("body", [0, 4, 0], [...body, m.G("head", [0, 4, -4], [...head, m.G("hat", [0, 10.4, -4.75], hat), m.G("tongue_eat", [0, 4.9, -6.8], tongue)]), m.G("sword_rest", [3.6, 2.8, 1], sheath), m.G("blade_work", [4, 4, -1], blade), arm("arm_l", -2), arm("arm_r", 2)]),
      leg("leg_l", -3.5), leg("leg_r", 3.5)])];
  },
  // 파로 — 태양 목도리도마뱀 (2026-11-25 새 디자인): slim golden gecko body with a row of red back spikes, a big round sun frill
  // behind the head (glows), gentle big eyes, splayed lizard legs, a long two-part tapering tail. The tail-tip fire is a particle
  // effect (PipiPets.Pet.tick), not modelled.
  sunlizard(m) {
    const O = "#f2a53c", OL = "#ffd27a", OD = "#c46a24", BL = "#fff1c8", SUN = "#ffe04a", SUND = "#ffb02a", RD = "#f2562a", RDD = "#b8361c";
    const back = (x, y, z, f, i, j, w, h) => f === "up" && Math.abs(x) < 1.2 ? OD : f === "down" ? BL : fur(O, { noise: .08 })(x, y, z, f, i, j, w, h);
    const body = [m.cube("body_cube", [-2.8, 2.4, -5], [2.8, 6.2, 5], back),
      ...[-3, 0, 3].map((z, k) => m.cube("spike_" + k, [-.5, 6.2, z - .7], [.5, 7.4 - k * .2, z + .7], edge(RD, RDD)))];
    const face = (x, y, z, f, i, j, w, h) => f === "north" && j >= 4 && i >= 1 && i <= w - 2 ? OL : fur(O)(x, y, z, f, i, j, w, h);
    const head = [m.cube("head_cube", [-3.6, 3, -11], [3.6, 8.2, -4.6], bigEyes(face, 1, 1, { iris: "#7a3a14", blush: "#ff9a7a" })),
      m.cube("snout", [-2.4, 3, -12.2], [2.4, 5.4, -11], faces({ north: (x, y, z, f, i, j) => j === 0 && (i === 1 || i === 3) ? OD : OL }, flat(OL))),
      m.cube("neck", [-2.2, 3.4, -5.6], [2.2, 6.6, -3.6], fur(O))];
    const tongue = [m.cube("tongue_cube", [-.4, 3.4, -14], [.4, 3.8, -12.2], flat("#e84a6a"))];
    // round sun frill behind the head: rays alternating sun-yellow / orange around a darker inner ring
    const frill = (x, y, z, f) => { const dx = x, dy = y - 6, r = Math.hypot(dx, dy), a = Math.atan2(dy, dx);
      if (f !== "north" && f !== "south") return SUND; if (r < 2.6) return OD; return Math.floor((a + Math.PI) / (Math.PI / 6)) % 2 ? SUN : SUND; };
    const crest = [m.cube("frill_core", [-5.2, 1.6, -4.9], [5.2, 10.4, -4.4], frill), m.cube("frill_top", [-3, 10.4, -4.9], [3, 11.8, -4.4], frill),
      m.cube("frill_l", [-6.6, 3.6, -4.9], [-5.2, 8.4, -4.4], frill), m.cube("frill_r", [5.2, 3.6, -4.9], [6.6, 8.4, -4.4], frill)];
    const tailBase = [m.cube("tail_cube", [-1.7, 2.8, 4.6], [1.7, 5.4, 10.5], fur(O, { noise: .08 }))];
    const tailTip = [m.cube("tail_tip_cube", [-1.05, 3.3, 10.2], [1.05, 4.9, 16], (x, y, z) => z > 14.4 ? RD : z > 12.8 ? SUND : O)];
    // lizard legs: splayed out to the sides, short, with a darker foot
    const leg = (n, x, z) => { const s = Math.sign(x); return m.G(n, [x, 3.6, z], [m.cube(n + "_upper", s < 0 ? [x - 1.6, 1.6, z - 1] : [x, 1.6, z - 1], s < 0 ? [x, 3.8, z + 1] : [x + 1.6, 3.8, z + 1], fur(O)),
      m.cube(n + "_foot", s < 0 ? [x - 2.4, 0, z - 1.3] : [x + .8, 0, z - 1.3], s < 0 ? [x - .8, 1.6, z + 1.3] : [x + 2.4, 1.6, z + 1.3], (X, y) => y < .6 ? OD : O)]); };
    return [m.G("root", [0, 0, 0], [m.G("body", [0, 4, 0], [...body, m.G("head", [0, 4.5, -5], [...head, m.G("tongue", [0, 3.6, -12], tongue)]), m.G("crest_glow", [0, 6, -4.6], crest),
      m.G("tail", [0, 4, 5], [...tailBase, m.G("tail_tip", [0, 4, 10.4], tailTip)])]),
      leg("leg_fl", -2.8, -3), leg("leg_fr", 2.8, -3), leg("leg_bl", -2.8, 3.2), leg("leg_br", 2.8, 3.2)])];
  },
  // 블벳 — 솜뭉치 박쥐: round fluffy bat body, huge ears, glowing red eyes, tiny fang, two-part wings
  vampbat(m) {
    const P = "#4a3a5e", PL = "#6e5a88", MB = "#2e2440", RED = "#ff3a4a", FG = "#ffffff", BE = "#c9b6e6";
    const body = [m.cube("body_cube", [-3.5, 6.5, -3], [3.5, 12, 3], fur(P, { noise: .12 })), m.cube("chest_fluff", [-2.5, 7, -3.4], [2.5, 11, -3], flat(BE))];
    const head = [m.cube("head_cube", [-4, 11, -7], [4, 16.5, -1], fur(PL))];
    const ear = s => [m.cube(`ear_${s}_cube`, s === "l" ? [-4, 16, -5] : [1, 16, -5], s === "l" ? [-1, 20.5, -3.5] : [4, 20.5, -3.5], faces({ north: flat(BE) }, flat(P)))];
    const eyes = [m.cube("eye_l", [-3, 13.2, -7.15], [-1, 15, -7.05], (x, y, z, f, i, j) => i === 0 && j === 0 ? "#ffd0d0" : RED), m.cube("eye_r", [1, 13.2, -7.15], [3, 15, -7.05], (x, y, z, f, i, j) => i === 0 && j === 0 ? "#ffd0d0" : RED)];
    const fang = [m.cube("fang_cube", [1, 11.4, -7.2], [1.6, 12.2, -7.05], flat(FG))];
    // pointed wings: the outer wing tapers to a sharp tip and the membrane ends in hanging finger points
    const X = (s, a, b) => s < 0 ? [-b, -a] : [a, b];
    const wc = (n, s, x0, x1, y0, y1, paint = flat(MB)) => { const [a, b] = X(s, x0, x1); return m.cube(n, [a, y0, -1.3], [b, y1, -.7], paint); };
    const top = (x, y, z, f, i, j) => j === 0 ? PL : MB;
    const wing = (n, no, s) => m.G(n, [s * 2, 12, -1], [wc(n + "_cube", s, 2, 5, 8, 12.5), wc(n + "_point", s, 3.3, 3.9, 6.6, 8),
      m.G(no, [s * 5, 12, -1], [wc(no + "_cube", s, 5, 6.6, 7.6, 12.5, top), wc(no + "_b", s, 6.6, 8.2, 9, 12.5, top), wc(no + "_c", s, 8.2, 9.8, 10.4, 12.5, top), wc(no + "_tip", s, 9.8, 11.6, 11.6, 12.5, top),
        wc(no + "_point_a", s, 5.6, 6.2, 6.1, 7.6), wc(no + "_point_b", s, 7.2, 7.7, 7.8, 9)])]);
    const tail = [m.cube("tail_cube", [-.4, 5, 1.5], [.4, 7, 2.3], flat(MB))];
    const leg = (n, x) => m.G(n, [x, 6, 0], [m.cube(n + "_cube", [x - .4, 4.5, -.4], [x + .4, 6.5, .4], flat(MB))]);
    return [m.G("root", [0, 6, 0], [m.G("body", [0, 10, 0], [...body, m.G("head", [0, 12, -4], [...head, m.G("ear_l", [-2, 15, -4], ear("l")), m.G("ear_r", [2, 15, -4], ear("r")), m.G("eyes_glow", [0, 13.5, -6], eyes), m.G("fang_glint", [1.5, 10, -7], fang)]),
      wing("wing_l", "wing_l_outer", -1), wing("wing_r", "wing_r_outer", 1), m.G("tail", [0, 6.5, 2], tail)]), leg("leg_l", -1), leg("leg_r", 1)])];
  },
};
export const lowerFaceMask = lowerFace;
