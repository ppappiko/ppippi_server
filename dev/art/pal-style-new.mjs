// Restyled builders for the 13 new pets (penguin lives in make-pal-style.mjs). Same bone names as the first version
// (dev/art/make-new-pets.mjs, now superseded) so the template animations in make-pet-anims.mjs keep working.
import { fur, flat, edge, faces, hash } from "./lib-petmodel.mjs";
import { bigEyes, lowerFaceMask as lowerFace } from "./pal-style-more.mjs";
const K = "#15131a", W = "#ffffff";
const leg4 = (m, n, x, z, top, t, col, paw) => m.G(n, [x, top, z], [m.cube(n + "_cube", [x - t, 0, z - t], [x + t, top, z + t], (X, y) => y < 1.2 ? paw : fur(col)(X, y, z, "north"))]);

export const NEW = {
  // 댐돌이 — 공사장 비버: hard hat, big buck teeth, crosshatched paddle tail, carries a little log
  beaver(m) {
    const B = "#8a5a34", BL = "#c49668", D = "#5e3a1f", T = "#3f2a1c", HY = "#f5c431", HYD = "#c99a14", LG = "#9a6a3a";
    const body = [m.cube("body_cube", [-3.5, 3, -4], [3.5, 9, 4.5], faces({ north: fur(BL), down: fur(BL) }, fur(B)))];
    const head = [m.cube("head_cube", [-4.5, 6, -10], [4.5, 13, -3.5], bigEyes(lowerFace(fur(B), BL, 4, 2), 1, 2, { iris: "#3a2210", blush: "#f2a08a" })),
      m.cube("teeth", [-1.2, 5.2, -10.3], [1.2, 6.6, -10], edge(W, "#e4dccb")), m.cube("nose", [-.8, 8.2, -10.4], [.8, 9, -10], flat(T)),
      m.cube("hardhat", [-4.3, 12.5, -9.6], [4.3, 15, -3.9], edge(HY, HYD)), m.cube("hardhat_brim", [-4.8, 12.5, -10.8], [4.8, 13.1, -9.4], flat(HYD)),
      m.cube("ear_l", [-5, 11, -6.5], [-4.3, 12.2, -5.3], flat(D)), m.cube("ear_r", [4.3, 11, -6.5], [5, 12.2, -5.3], flat(D)), m.cube("log", [-3, 3.5, -11.5], [3, 5, -10.2], (x, y, z, f) => f === "east" || f === "west" ? "#d8b07a" : LG)];
    const tail = [m.cube("tail_cube", [-2.5, 2.4, 4], [2.5, 3.6, 11], (x, y, z, f, i, j) => (i + j) % 2 ? T : D)];
    return [m.G("root", [0, 4, 0], [m.G("body", [0, 6, 0], [...body, m.G("head", [0, 7, -5], head), m.G("tail", [0, 3, 5], tail)]),
      leg4(m, "leg_fl", -2.3, -2.5, 3.5, 1.1, D, T), leg4(m, "leg_fr", 2.3, -2.5, 3.5, 1.1, D, T), leg4(m, "leg_bl", -2.3, 3, 3.5, 1.1, D, T), leg4(m, "leg_br", 2.3, 3, 3.5, 1.1, D, T)])];
  },
  // 집게 — 별바다 소라게: pastel spiral shell with a starfish sticker, round red body, googly stalk eyes, one big claw
  hermitcrab(m) {
    const S = "#ffd6c8", SD = "#e8a08e", SL = "#fff3ea", R = "#e0563c", RD = "#a83a28", ST = "#ffb83a";
    const spiral = (x, y, z) => { const a = Math.atan2(y - 8, z - 2), r = Math.hypot(y - 8, z - 2); return Math.sin(a * 2 + r * 1.3 + x * .2) > .5 ? SD : r < 1.4 ? SL : S; };
    const shell = [m.cube("shell_cube", [-4.5, 3, -2], [4.5, 12, 6.5], spiral), m.cube("shell_tip", [-3, 11.5, 1], [3, 14, 5.5], spiral), m.cube("shell_point", [-1.2, 13.8, 2.3], [1.2, 15.5, 4.3], flat(SD)),
      m.cube("star_a", [-4.8, 7, 1], [-4.5, 10, 2], flat(ST)), m.cube("star_b", [-4.8, 8, 0], [-4.5, 9, 3], flat(ST))];
    const body = m.cube("body_cube", [-3.5, 1.5, -5], [3.5, 5, 2], faces({ down: flat(RD) }, bigEyes(fur(R), 1, 1, { iris: R, pupil: RD })));
    const stalk = (n, x) => m.G(n, [x, 4.5, -3.7], [m.cube(n + "_stalk", [x - .35, 4.5, -4.05], [x + .35, 7, -3.35], flat(R)),
      m.cube(n + "_ball", [x - 1.1, 6.8, -4.8], [x + 1.1, 9, -2.6], (X, y, z, f, i, j) => f === "north" ? (j === 0 && i === 0 ? W : i >= 1 && j >= 1 ? K : W) : W)]);
    const clawL = m.G("claw_l", [-3, 3, -4], [m.cube("claw_l_arm", [-4.5, 2, -6], [-3, 3.5, -4], flat(R)), m.cube("claw_l_pincer", [-7.5, 1.5, -10], [-3.5, 5.5, -6], edge(R, RD)), m.cube("claw_l_tip", [-7.5, 5, -11], [-5, 6.2, -9], flat(RD))]);
    const clawR = m.G("claw_r", [3, 3, -4], [m.cube("claw_r_arm", [3, 2, -5.5], [4.2, 3.2, -4], flat(R)), m.cube("claw_r_pincer", [3.5, 1.6, -7.5], [5.5, 3.8, -5.5], edge(R, RD))]);
    const legs = [];
    for (const [k, z] of [[1, -3], [2, -1], [3, 1]]) for (const side of [-1, 1]) {
      const n = `leg_${side < 0 ? "l" : "r"}${k}`, x = side * 3.5;
      legs.push(m.G(n, [x, 2.5, z], [m.cube(n + "_up", side < 0 ? [x - 2.5, 2.2, z - .4] : [x, 2.2, z - .4], side < 0 ? [x, 3, z + .4] : [x + 2.5, 3, z + .4], flat(R)),
        m.cube(n + "_down", side < 0 ? [x - 3, 0, z - .35] : [x + 2.3, 0, z - .35], side < 0 ? [x - 2.3, 3, z + .35] : [x + 3, 3, z + .35], flat(RD))]));
    }
    return [m.G("root", [0, 3, 0], [m.G("body", [0, 3, 0], [body, m.G("shell", [0, 4, 2], shell), stalk("eye_l", -1.5), stalk("eye_r", 1.5), clawL, clawR]), ...legs])];
  },
  // 하얀뿔 — 꽃사슴: fawn with white spots, pale antlers wrapped in little flowers, huge gentle eyes
  deer(m) {
    const B = "#c08a55", BL = "#ecd2aa", D = "#7a5232", AN = "#f2ede0", AND = "#c9bfa8", FP = "#ff9ac2", FY = "#ffe066";
    const spots = (x, y, z, f, i, j) => hash(i * 3, j * 5, f.length) < .12 && f !== "down" ? "#f6ecd8" : fur(B)(x, y, z, f);
    const body = m.cube("body_cube", [-3, 8, -5.5], [3, 14, 5.5], faces({ down: fur(BL) }, spots));
    const neck = m.cube("neck", [-1.5, 12.5, -7.5], [1.5, 17, -4.5], fur(B));
    const head = [m.cube("head_cube", [-3.5, 16, -11], [3.5, 21.5, -5.5], bigEyes(lowerFace(fur(B), BL, 4, 2), 1, 1, { iris: "#4a2a14", blush: "#f39a8a" })), m.cube("nose", [-.8, 16.6, -11.3], [.8, 17.4, -11], flat(K)),
      m.cube("ear_l", [-5.5, 19.5, -8.5], [-3.5, 20.7, -7.5], faces({ north: flat("#f2b8a0") }, flat(B))), m.cube("ear_r", [3.5, 19.5, -8.5], [5.5, 20.7, -7.5], faces({ north: flat("#f2b8a0") }, flat(B)))];
    const antler = s => [m.cube(`antler_${s}_base`, s < 0 ? [-2.4, 21.5, -8.4] : [1.6, 21.5, -8.4], s < 0 ? [-1.6, 25, -7.6] : [2.4, 25, -7.6], edge(AN, AND)),
      m.cube(`antler_${s}_out`, s < 0 ? [-4.8, 24.2, -8.3] : [1.6, 24.2, -8.3], s < 0 ? [-1.6, 25, -7.7] : [4.8, 25, -7.7], flat(AN)), m.cube(`antler_${s}_tip`, s < 0 ? [-4.8, 25, -8.3] : [4, 25, -8.3], s < 0 ? [-4, 26.8, -7.7] : [4.8, 26.8, -7.7], flat(AN)),
      m.cube(`flower_${s}`, s < 0 ? [-3.6, 24, -8.8] : [2.4, 24, -8.8], s < 0 ? [-2.4, 25.2, -8.3] : [3.6, 25.2, -8.3], (x, y, z, f, i, j) => i === 0 && j === 0 ? FY : FP)];
    const tail = [m.cube("tail_cube", [-1, 12, 5.5], [1, 14.5, 6.8], flat("#f6f1e6"))];
    return [m.G("root", [0, 10, 0], [m.G("body", [0, 12, 0], [body, m.G("head", [0, 15, -6], [neck, ...head, ...antler(-1), ...antler(1)]), m.G("tail", [0, 15, 6], tail)]),
      leg4(m, "leg_fl", -1.8, -4, 9, .9, D, "#3b2a1c"), leg4(m, "leg_fr", 1.8, -4, 9, .9, D, "#3b2a1c"), leg4(m, "leg_bl", -1.8, 4, 9, .9, D, "#3b2a1c"), leg4(m, "leg_br", 1.8, 4, 9, .9, D, "#3b2a1c")])];
  },
  // 깃털이 — 질주 타조: fluffy black body, white plumes, racing goggles, tall pink legs, big proud eyes
  ostrich(m) {
    const KB = "#2a2626", KL = "#433d3b", WP = "#f4f1ea", P = "#e9a7a0", PD = "#c37f78", BK = "#e8c27a", GO = "#ff9a3a", GOD = "#a85a1a";
    const body = [m.cube("body_cube", [-4.5, 11, -5], [4.5, 18, 5], fur(KB, { noise: .12 })), m.cube("body_fluff", [-5, 12, -4], [5, 17, 4], fur(KL, { noise: .15 }))];
    const neck = [m.cube("neck_low", [-1, 16, -6.5], [1, 22, -4.5], fur(P)), m.cube("neck_high", [-1, 21, -7], [1, 25, -5], fur(P))];
    const head = [m.cube("head_cube", [-3, 24, -10], [3, 29, -4.5], bigEyes(fur("#efb8b0"), 0, 1, { iris: "#4a2a1a" })), m.cube("beak", [-1.3, 24.4, -11.8], [1.3, 25.8, -10], edge(BK, "#b89150")),
      m.cube("goggle_strap", [-3.2, 27.6, -10.2], [3.2, 28.3, -4.3], flat(GOD)), m.cube("goggle_lens", [-2.6, 27.8, -10.5], [2.6, 29, -10], edge(GO, GOD)), m.cube("head_tuft", [-.5, 29, -8], [.5, 30.5, -6], flat(KL))];
    const wing = (n, s) => m.G(n, [s * 4.5, 16, 0], [m.cube(n + "_cube", s < 0 ? [-6, 12, -4] : [5, 12, -4], s < 0 ? [-5, 17, 4] : [6, 17, 4], fur(KB)),
      m.cube(n + "_plume", s < 0 ? [-6.5, 11, 1] : [5, 11, 1], s < 0 ? [-5, 15, 5.5] : [6.5, 15, 5.5], fur(WP, { noise: .12 }))]);
    const tail = [m.cube("tail_cube", [-3, 14, 4.5], [3, 19.5, 7.5], fur(WP, { noise: .15 }))];
    const leg = (n, x) => m.G(n, [x, 12, 0], [m.cube(n + "_thigh", [x - 1.4, 8, -1.4], [x + 1.4, 12, 1.4], fur(P)), m.cube(n + "_shin", [x - .6, 0, -.6], [x + .6, 8, .6], fur(PD)), m.cube(n + "_toe", [x - 1, 0, -2.5], [x + 1, .8, .5], flat(PD))]);
    return [m.G("root", [0, 12, 0], [m.G("body", [0, 14, 0], [...body, m.G("head", [0, 17, -5], [...neck, ...head]), wing("wing_l", -1), wing("wing_r", 1), m.G("tail", [0, 16, 5], tail)]), leg("leg_l", -2), leg("leg_r", 2)])];
  },
  // 뿔소 — 갑옷 코뿔소: grey armoured plates with rivets, one great crystal horn, small fierce-but-cute eyes, chunky legs
  rhino(m) {
    const G = "#8d9096", GD = "#62656b", GL = "#b3b6bb", RV = "#d6d8dc", H = "#9fe0ff", HD = "#4aa0d0";
    const plates = (x, y, z, f, i, j, w, h) => (Math.abs(z - 1) < .5 && f !== "down") ? GD : (i % 4 === 1 && j % 4 === 1 && f !== "down") ? RV : fur(G, { noise: .06 })(x, y, z, f, i, j, w, h);
    const body = [m.cube("body_cube", [-6, 6, -8], [6, 16, 9], plates), m.cube("hump", [-4.5, 16, -6], [4.5, 17.5, 2], plates)];
    const head = [m.cube("head_cube", [-5, 6.5, -15.5], [5, 14, -7.5], bigEyes(lowerFace(fur(G), GL, 5, 2), 1, 2, { iris: "#2a3a5a" })), m.cube("snout", [-3.5, 6.5, -17.5], [3.5, 10.5, -15.5], fur(GL)),
      m.cube("horn_big", [-1.6, 10.5, -17.5], [1.6, 15.5, -14.8], edge(H, HD)), m.cube("horn_tip", [-.9, 15.5, -17], [.9, 18, -15.3], flat("#e6f8ff")),
      m.cube("ear_l", [-6, 13, -10], [-4.5, 15, -9], flat(GD)), m.cube("ear_r", [4.5, 13, -10], [6, 15, -9], flat(GD))];
    const tail = [m.cube("tail_cube", [-.6, 9, 9], [.6, 14, 10], flat(GD)), m.cube("tail_tuft", [-1, 8, 9.2], [1, 9.5, 10.2], flat("#3b3d41"))];
    return [m.G("root", [0, 7, 0], [m.G("body", [0, 11, 0], [...body, m.G("head", [0, 11, -8], head), m.G("tail", [0, 14, 9], tail)]),
      leg4(m, "leg_fl", -3.8, -5, 7, 2.1, G, "#4a4c50"), leg4(m, "leg_fr", 3.8, -5, 7, 2.1, G, "#4a4c50"), leg4(m, "leg_bl", -3.8, 6, 7, 2.1, G, "#4a4c50"), leg4(m, "leg_br", 3.8, 6, 7, 2.1, G, "#4a4c50")])];
  },
  // 망고 — 꼬마 정비공 원숭이: huge round head with a peach face, bandana, curled tail, a mango in hand
  monkey(m) {
    const B = "#7a4e2e", BD = "#58361e", F = "#f0c9a2", MG = "#f2a33a", MGD = "#4f8f2c", BN = "#3a8ad8";
    const body = [m.cube("body_cube", [-3, 5.5, -2], [3, 11, 2.5], faces({ north: fur(F) }, fur(B)))];
    const face = (x, y, z, f, i, j, w, h) => f === "north" && j >= 2 && i > 0 && i < w - 1 ? F : fur(B)(x, y, z, f, i, j, w, h);
    const head = [m.cube("head_cube", [-4.5, 11, -4], [4.5, 19, 3.5], bigEyes(face, 2, 3, { iris: "#4a2a10", blush: "#ff9a8a" })), m.cube("muzzle", [-2, 11.3, -5], [2, 13.5, -4], fur(F)),
      m.cube("bandana", [-4.7, 17, -4.2], [4.7, 18.2, 3.7], (x, y, z, f, i) => (i % 3 === 0) ? "#2a6ab0" : BN), m.cube("bandana_knot", [3.5, 15.5, 2.5], [5, 17.5, 4], flat(BN)),
      m.cube("ear_l", [-6, 14, -1], [-4.5, 16.5, 1], faces({ west: flat(F) }, flat(B))), m.cube("ear_r", [4.5, 14, -1], [6, 16.5, 1], faces({ east: flat(F) }, flat(B)))];
    const arm = (n, s) => m.G(n, [s * 3.5, 10.5, 0], [m.cube(n + "_cube", s < 0 ? [-5, 4, -1] : [3, 4, -1], s < 0 ? [-3, 11, 1] : [5, 11, 1], fur(B)), m.cube(n + "_hand", s < 0 ? [-5.2, 3, -1.2] : [2.8, 3, -1.2], s < 0 ? [-2.8, 4.2, 1.2] : [5.2, 4.2, 1.2], flat(F)),
      ...(s > 0 ? [m.cube("mango", [3, 1.6, -2.6], [5.6, 4, 0], (x, y, z, f, i, j) => j === 0 && f !== "down" ? MGD : MG)] : [])]);
    const tail = [m.cube("tail_a", [-.6, 5, 2.5], [.6, 6.2, 7], flat(B)), m.cube("tail_b", [-.6, 6, 6], [.6, 11, 7.2], flat(B)), m.cube("tail_c", [-.6, 10, 4], [.6, 11.2, 7], flat(BD))];
    const leg = (n, x) => m.G(n, [x, 5.5, 0], [m.cube(n + "_cube", [x - 1.3, 0, -1.3], [x + 1.3, 5.5, 1.3], (X, y) => y < 1 ? F : fur(B)(X, y, 0, "north"))]);
    return [m.G("root", [0, 7, 0], [m.G("body", [0, 9, 0], [...body, m.G("head", [0, 12, 0], head), arm("arm_l", -1), arm("arm_r", 1), m.G("tail", [0, 6, 2.5], tail)]), leg("leg_l", -1.6), leg("leg_r", 1.6)])];
  },
  // 밤눈 — 학자 부엉이: round owl with a tiny graduation cap and round spectacles, glowing amber eyes behind them
  owl(m) {
    const B = "#5e4630", BL = "#8a6a4a", FD = "#e4cfa8", T = "#3b2b1d", Y = "#ffc23a", BK = "#d8a344", CAP = "#2a2a3a", TS = "#e8c33a";
    const feathers = (x, y, z, f, i, j, w, h) => f === "north" && i > 0 && i < w - 1 ? ((i + j) % 3 === 0 ? BL : FD) : ((j + (i >> 1)) % 3 === 0 ? T : fur(B)(x, y, z, f, i, j, w, h));
    const body = [m.cube("body_cube", [-4, 1, -3.5], [4, 9.5, 3.5], feathers)];
    const head = [m.cube("head_cube", [-5, 9, -4], [5, 17, 4], (x, y, z, f, i, j, w, h) => f === "north" && i > 0 && i < w - 1 && j > 0 && j < h - 1 ? FD : fur(B)(x, y, z, f, i, j, w, h)),
      m.cube("beak", [-.7, 10.8, -4.6], [.7, 12.4, -4], flat(BK)), m.cube("tuft_l", [-5, 17, -2], [-3, 19, 0], flat(T)), m.cube("tuft_r", [3, 17, -2], [5, 19, 0], flat(T)),
      m.cube("cap_board", [-4, 17, -3.5], [4, 17.6, 3.5], flat(CAP)), m.cube("cap_base", [-2.5, 16.6, -2.5], [2.5, 17, 2.5], flat(CAP)), m.cube("cap_tassel", [3.2, 15.4, -1.6], [3.8, 17.2, -1], flat(TS)),
      m.cube("spec_bridge", [-.8, 14, -4.4], [.8, 14.4, -4.2], flat(BK))];
    const eyesGlow = [m.cube("eye_l", [-4, 12.5, -4.3], [-1, 15.5, -4], (x, y, z, f, i, j) => i === 0 && j === 0 ? W : i === 1 && j === 1 ? K : Y), m.cube("eye_r", [1, 12.5, -4.3], [4, 15.5, -4], (x, y, z, f, i, j) => i === 0 && j === 0 ? W : i === 1 && j === 1 ? K : Y)];
    const wing = (n, s) => m.G(n, [s * 4, 9, 0], [m.cube(n + "_cube", s < 0 ? [-5, 2.5, -2.5] : [4, 2.5, -2.5], s < 0 ? [-4, 9, 3] : [5, 9, 3], (x, y, z, f, i, j) => j % 3 === 2 ? T : BL)]);
    const foot = (n, x) => m.G(n, [x, 1, -1], [m.cube(n + "_cube", [x - 1.2, 0, -3.2], [x + 1.2, 1, -1], flat(BK))]);
    return [m.G("root", [0, 6, 0], [m.G("body", [0, 6, 0], [...body, m.G("head", [0, 10, 0], [...head, m.G("eyes_glow", [0, 14, -4.2], eyesGlow)]), wing("wing_l", -1), wing("wing_r", 1)]), foot("leg_l", -1.8), foot("leg_r", 1.8)])];
  },
  // 도토리 — 도토리 투구 다람쥐: wears an acorn-cap helmet, giant curled tail, cheek pouches, holds an acorn
  squirrel(m) {
    const O = "#c9672e", OL = "#e8a36a", WB = "#f6e6d0", D = "#7c3d1b", AC = "#a8692f", ACC = "#6e4a2a";
    const body = [m.cube("body_cube", [-2.5, 3, -3.5], [2.5, 7.5, 3], faces({ north: flat(WB), down: flat(WB) }, fur(O)))];
    const head = [m.cube("head_cube", [-4, 5, -8.5], [4, 11, -2.5], bigEyes(lowerFace(fur(O), WB, 4, 2), 1, 2, { iris: "#3a1a08", blush: "#ff9a8a" })), m.cube("nose", [-.5, 7.4, -8.8], [.5, 8, -8.5], flat("#3a1a10")),
      m.cube("acorn_helm", [-4.3, 10.5, -8], [4.3, 13, -3], (x, y, z, f, i, j) => (i + j) % 2 ? ACC : "#8a5e36"), m.cube("helm_stem", [-.4, 13, -5.9], [.4, 14.4, -5.1], flat(ACC)),
      m.cube("ear_l", [-4, 12.5, -4.5], [-2.8, 14, -3.6], flat(D)), m.cube("ear_r", [2.8, 12.5, -4.5], [4, 14, -3.6], flat(D))];
    const cheeks = [m.cube("cheek_l", [-4.6, 6, -7], [-4, 7.8, -5], flat(WB)), m.cube("cheek_r", [4, 6, -7], [4.6, 7.8, -5], flat(WB))];
    const acorn = [m.cube("acorn_nut", [-1.1, 3.4, -10.4], [1.1, 5.6, -8.7], flat(AC)), m.cube("acorn_cap", [-1.3, 5.4, -10.6], [1.3, 6.4, -8.5], flat(ACC))];
    const stripe = (x, y, z, f, i) => i === 1 || i === 4 ? OL : fur(O, { noise: .15 })(x, y, z, f);
    const tail = [m.cube("tail_a", [-2.8, 4, 2.5], [2.8, 9.5, 6.5], stripe), m.cube("tail_b", [-3, 9, 3.5], [3, 15, 8], stripe), m.cube("tail_c", [-2.8, 14, 1], [2.8, 17.5, 6.5], stripe)];
    return [m.G("root", [0, 4, 0], [m.G("body", [0, 5, 0], [...body, m.G("head", [0, 7, -3.5], [...head, m.G("cheeks", [0, 6.8, -6], cheeks), m.G("acorn_eat", [0, 5, -9], acorn)]), m.G("tail", [0, 6, 3], tail)]),
      leg4(m, "leg_fl", -1.5, -2.5, 3, .9, D, "#4a2410"), leg4(m, "leg_fr", 1.5, -2.5, 3, .9, D, "#4a2410"), leg4(m, "leg_bl", -1.6, 1.8, 3, 1, D, "#4a2410"), leg4(m, "leg_br", 1.6, 1.8, 3, 1, D, "#4a2410")])];
  },
  // 뾰족 — 버섯 고슴도치: round spiny ball with little glowing cave mushrooms growing between the spines
  hedgehog(m) {
    const SP = "#6b5240", SPL = "#a88a6e", SPD = "#3e2e22", F = "#ecd6b4", N = "#1d1a1c", MR = "#5ad1c4", MS = "#e9fbf8";
    const spines = (x, y, z, f, i, j) => { const n = hash(i * 3, j * 5, f.length); return n < .35 ? SPL : n > .8 ? SPD : SP; };
    const body = [m.cube("body_cube", [-4.5, 1, -4], [4.5, 8, 5], faces({ down: flat(F) }, spines))];
    for (let k = 0; k < 9; k++) { const x = -3 + (k % 3) * 3, z = -2.5 + Math.floor(k / 3) * 3; body.push(m.cube("spike_" + k, [x - .4, 7.8, z - .4], [x + .4, 9.3, z + .4], flat(SPL))); }
    body.push(m.cube("shroom_stem", [1.4, 8, 1.4], [2, 9.6, 2], flat(MS)), m.cube("shroom_cap", [.6, 9.6, .6], [2.8, 10.4, 2.8], flat(MR)), m.cube("shroom2_stem", [-2.6, 8, 3], [-2.1, 9, 3.5], flat(MS)), m.cube("shroom2_cap", [-3.2, 9, 2.4], [-1.5, 9.6, 4.1], flat(MR)));
    const face = [m.cube("face", [-3.5, 1.4, -8], [3.5, 7, -3.8], bigEyes(fur(F), 1, 1, { iris: "#3a2210", blush: "#ff9aa0" })), m.cube("nose", [-.7, 2.6, -9], [.7, 3.6, -8], flat(N)),
      m.cube("ear_l", [-3.4, 7, -5.5], [-2.2, 8, -4.6], flat(F)), m.cube("ear_r", [2.2, 7, -5.5], [3.4, 8, -4.6], flat(F))];
    return [m.G("root", [0, 2, 0], [m.G("body", [0, 4, 0], [...body, m.G("head", [0, 3.5, -4], face)]),
      leg4(m, "leg_fl", -2.5, -2.5, 1.5, .8, F, "#c9b08a"), leg4(m, "leg_fr", 2.5, -2.5, 1.5, .8, F, "#c9b08a"), leg4(m, "leg_bl", -2.5, 3, 1.5, .8, F, "#c9b08a"), leg4(m, "leg_br", 2.5, 3, 1.5, .8, F, "#c9b08a")])];
  },
  // 뭉게 — 무지개 구름 양: puffy cloud body, sky-blue face with sleepy-happy eyes, a little rainbow arc on its back
  cloudsheep(m) {
    const C = "#f8fbff", CS = "#d8e6f6", F = "#9fc3ea", FD = "#6f97c8", H = "#e6eef8";
    const puff = fur(C, { noise: .12, light: .02, dark: .12 });
    const cloud = [m.cube("cloud_core", [-5, 6, -5], [5, 13, 5], puff), m.cube("cloud_top", [-3.5, 12, -3.5], [3.5, 15, 3], puff),
      m.cube("cloud_l", [-6.5, 7, -3], [-5, 12, 3], puff), m.cube("cloud_r", [5, 7, -3], [6.5, 12, 3], puff), m.cube("cloud_back", [-3, 7, 4.5], [3, 12.5, 7], puff), m.cube("cloud_belly", [-3.5, 5, -3], [3.5, 6.5, 3], flat(CS))];
    ["#ff6a6a", "#ffb84a", "#ffe94a", "#6ad86a", "#5ab0ff"].forEach((c, k) => cloud.push(m.cube("rainbow_" + k, [-.4, 15 + k * .55, 2 - k * .6], [.4, 15.55 + k * .55, 2.6 - k * .6], flat(c))));
    const head = [m.cube("head_cube", [-3.5, 8, -10], [3.5, 14, -4.5], bigEyes(fur(F), 1, 2, { iris: "#203450", blush: "#ffb0c8" })), m.cube("head_puff", [-4, 13.5, -9], [4, 15.5, -4], puff),
      m.cube("ear_l", [-5.5, 11.5, -8], [-3.5, 12.5, -7], flat(FD)), m.cube("ear_r", [3.5, 11.5, -8], [5.5, 12.5, -7], flat(FD))];
    return [m.G("root", [0, 6, 0], [m.G("body", [0, 9, 0], [...cloud, m.G("head", [0, 11, -5], head), m.G("tail", [0, 10, 7], [m.cube("tail_puff", [-1.5, 9, 6.5], [1.5, 11.5, 8.5], puff)])]),
      leg4(m, "leg_fl", -2.5, -3, 6, 1, H, FD), leg4(m, "leg_fr", 2.5, -3, 6, 1, H, FD), leg4(m, "leg_bl", -2.5, 3, 6, 1, H, FD), leg4(m, "leg_br", 2.5, 3, 6, 1, H, FD)])];
  },
  // 톱니 — 태엽 여우: brass plated fox with a big round head, glowing cyan eyes, spinning gear and wind-up key
  clockfox(m) {
    const BR = "#c9973b", BRD = "#8c6420", CU = "#b5643a", CUD = "#7c3f22", ST = "#6d7480", CY = "#62f2ff";
    const plated = (x, y, z, f, i, j, w, h) => (i === 0 || j === 0 || i === w - 1 || j === h - 1) ? BRD : (i * 7 + j * 3) % 11 === 0 ? "#f0d28a" : BR;
    const body = [m.cube("body_cube", [-3, 4.5, -5], [3, 9.5, 4.5], plated), m.cube("belly_plate", [-2.5, 4.1, -4], [2.5, 4.7, 3.5], flat(CU))];
    const head = [m.cube("head_cube", [-4.5, 6, -11], [4.5, 13, -4.5], plated), m.cube("snout", [-1.8, 6, -12.5], [1.8, 8.5, -11], edge(CU, CUD)), m.cube("nose", [-.6, 7.8, -12.9], [.6, 8.6, -12.5], flat("#2a2a2e")),
      m.cube("ear_l", [-4.5, 13, -9], [-1.8, 16.5, -7.5], edge(CU, CUD)), m.cube("ear_r", [1.8, 13, -9], [4.5, 16.5, -7.5], edge(CU, CUD))];
    const eyeGlow = [m.cube("eye_l_glow", [-3.6, 9.5, -11.2], [-1.4, 11.3, -11], (x, y, z, f, i, j) => i === 0 && j === 0 ? W : CY), m.cube("eye_r_glow", [1.4, 9.5, -11.2], [3.6, 11.3, -11], (x, y, z, f, i, j) => i === 0 && j === 0 ? W : CY)];
    const gear = [m.cube("gear_hub", [-1, 9, -2.5], [1, 12, .5], flat(ST)), m.cube("gear_tooth_a", [-.5, 8.5, -3.2], [.5, 12.5, 1.2], flat("#8d95a3")), m.cube("gear_tooth_b", [-.5, 9.8, -3.5], [.5, 11.2, 1.5], flat("#8d95a3"))];
    const key = [m.cube("key_shaft", [3, 7, -.3], [5, 7.6, .3], flat(BRD)), m.cube("key_bow", [5, 5.7, -1.5], [5.6, 8.9, 1.5], edge(BR, BRD))];
    const tail = [m.cube("tail_a", [-1.4, 6.5, 4.5], [1.4, 8.8, 8], plated), m.cube("tail_b", [-1.8, 7, 7.5], [1.8, 10, 11.5], plated), m.cube("tail_tip", [-1, 7.6, 11.5], [1, 9.4, 13], flat(CY))];
    return [m.G("root", [0, 5, 0], [m.G("body", [0, 7, 0], [...body, m.G("head", [0, 8.5, -5.5], [...head, m.G("eyes_glow", [0, 10.4, -11.1], eyeGlow)]), m.G("gear", [0, 10.5, -1], gear), m.G("key", [3, 7.3, 0], key), m.G("tail", [0, 7.5, 4.5], tail)]),
      leg4(m, "leg_fl", -1.8, -3.5, 4.5, .9, CU, ST), leg4(m, "leg_fr", 1.8, -3.5, 4.5, .9, CU, ST), leg4(m, "leg_bl", -1.8, 3, 4.5, .9, CU, ST), leg4(m, "leg_br", 1.8, 3, 4.5, .9, CU, ST)])];
  },
  // 기사단장 — 풍뎅이 기사: steel-blue shell with gold trim, great forked horn, red knight cape, big brave eyes, six legs
  beetle(m) {
    const SH = "#2f4a7a", SHL = "#4e6fa8", SHD = "#1d2f52", GD = "#e3b341", GDD = "#a67b1f", RD = "#c2303a", RDD = "#86202a", KB = "#1a1a20";
    const shell = (x, y, z, f, i, j, w) => Math.abs(x) < .5 && f === "up" ? GD : (i === 0 || i === w - 1) && f !== "down" ? GD : (i + j) % 7 === 0 ? SHL : SH;
    const body = [m.cube("shell_cube", [-4.5, 3, -4], [4.5, 9, 7], shell), m.cube("under", [-3.5, 2, -4], [3.5, 3, 6], flat(SHD)), m.cube("thorax", [-3.5, 3.5, -7], [3.5, 8.5, -4], edge(SHD, GDD))];
    const head = [m.cube("head_cube", [-3.5, 3, -11.5], [3.5, 8.5, -6.5], bigEyes(fur("#2a2a36"), 0, 1, { iris: "#ffdf6e", pupil: "#3a2a08" })),
      m.cube("horn_base", [-.9, 7.5, -12], [.9, 9.5, -9.5], flat(SHD)), m.cube("horn_mid", [-.8, 9, -13.5], [.8, 12.5, -12], flat(SHD)), m.cube("horn_fork_l", [-1.8, 12, -14], [-.2, 13.5, -12.6], flat(GD)), m.cube("horn_fork_r", [.2, 12, -14], [1.8, 13.5, -12.6], flat(GD))];
    const cape = [m.cube("cape_cube", [-4, 2.5, 7], [4, 8.5, 7.6], (x, y, z, f, i, j) => j === 0 ? GD : (i + j) % 4 === 0 ? RDD : RD), m.cube("cape_clasp", [-1, 8, 6.6], [1, 9, 7.3], flat(GD))];
    const legs = [];
    for (const [k, z] of [[1, -5], [2, -1], [3, 3.5]]) for (const side of [-1, 1]) {
      const n = `leg_${side < 0 ? "l" : "r"}${k}`, x = side * 4;
      legs.push(m.G(n, [x, 3.5, z], [m.cube(n + "_up", side < 0 ? [x - 2.5, 3, z - .4] : [x, 3, z - .4], side < 0 ? [x, 4, z + .4] : [x + 2.5, 4, z + .4], flat(KB)),
        m.cube(n + "_down", side < 0 ? [x - 3.2, 0, z - .35] : [x + 2.4, 0, z - .35], side < 0 ? [x - 2.4, 4, z + .35] : [x + 3.2, 4, z + .35], flat(KB))]));
    }
    return [m.G("root", [0, 4, 0], [m.G("body", [0, 5.5, 0], [...body, m.G("head", [0, 5.5, -7], head), m.G("cape", [0, 8.5, 7.2], cape)]), ...legs])];
  },
};
