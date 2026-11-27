// Creature-collector ("팰월드 느낌") restyle of the pets — original designs, not copies of any game's characters.
// Style rules: oversized head (~45% of height), big 2x2 eyes with a highlight, chunky round body, stubby limbs,
// ONE strong signature feature per species (element / prop / silhouette), warm saturated palette with a light belly.
// Bone names stay the same as before so dev/art/make-pet-anims.mjs keeps working.
// node dev/art/make-pal-style.mjs [skin...]   then   node dev/art/make-pet-anims.mjs <skin...>
import path from "node:path";
import { fileURLToPath } from "node:url";
import { Model, fur, flat, edge, faces, mix, shade, hash } from "./lib-petmodel.mjs";
import { MORE } from "./pal-style-more.mjs";
import { NEW } from "./pal-style-new.mjs";
import { EXTRA } from "./pal-style-extra.mjs";
const here = path.dirname(fileURLToPath(import.meta.url));
const OUT = s => path.resolve(here, `../pipi-facilities/assets/pipifacilities/pets/${s}.bbmodel`);
const K = "#15131a", W = "#ffffff";
/** Big expressive eyes on the north face: 2 wide x 2 tall (iris row + pupil row), white highlight top-left, ex = columns in from each side. */
const bigEyes = (base, ex, ey, { iris = "#3a2a1e", pupil = K, blush = null } = {}) => (x, y, z, f, i, j, w, h) => {
  if (f === "north") {
    const left = i === ex || i === ex + 1, right = i === w - 1 - ex || i === w - 2 - ex, col0 = i === ex || i === w - 2 - ex;
    if ((left || right) && j === ey) return col0 ? W : iris;
    if ((left || right) && j === ey + 1) return pupil;
    if (blush && (j === ey + 2) && (i === ex - 1 || i === w - ex)) return blush;
  }
  return base(x, y, z, f, i, j, w, h);
};

const PETS = {
  // 레디 — 사막의 꼬마 광부 여우: huge ember-lined ears, miner goggles on the forehead, flame-tipped fluffy tail
  fox(m) {
    const O = "#e8823a", OL = "#f5b26b", WH = "#fff4e6", D = "#5a3424", EM = "#ffcf6a", RD = "#e2492d", GL = "#ffb43a", ST = "#6b4a2e";
    const body = [m.cube("body_cube", [-3, 3, -3.5], [3, 8, 3.5], faces({ north: fur(WH), down: fur(WH) }, fur(O))), m.cube("ruff", [-3.4, 6.5, -4.2], [3.4, 9, -2.5], fur(WH, { noise: .15 }))];
    const head = [
      m.cube("head_cube", [-4.5, 6, -9.5], [4.5, 13, -2.5], bigEyes((x, y, z, f, i, j, w, h) => f === "north" && j >= 4 ? WH : fur(O)(x, y, z, f, i, j, w, h), 1, 2, { iris: "#7a3e14", blush: "#f39a8a" })),
      m.cube("muzzle", [-1.8, 6, -10.6], [1.8, 8.4, -9.5], faces({ north: (x, y, z, f, i, j) => j === 0 && (i === 1 || i === 2) ? K : WH }, flat(WH))),
      m.cube("cheek_l", [-5.1, 6.5, -7.5], [-4.5, 8.8, -4.5], flat(WH)), m.cube("cheek_r", [4.5, 6.5, -7.5], [5.1, 8.8, -4.5], flat(WH)),
      m.cube("goggle_strap", [-4.7, 10.8, -9.7], [4.7, 11.7, -2.3], flat(ST)),
      m.cube("goggle_l", [-3.6, 11, -10], [-.6, 12.8, -9.5], edge(GL, ST)), m.cube("goggle_r", [.6, 11, -10], [3.6, 12.8, -9.5], edge(GL, ST)),
    ];
    const ear = s => [m.cube(`ear_${s}_cube`, s === "l" ? [-4.5, 13, -7.5] : [1.5, 13, -7.5], s === "l" ? [-1.5, 18, -5.5] : [4.5, 18, -5.5], faces({ north: (x, y, z, f, i, j, w, h) => i > 0 && i < w - 1 && j > 0 ? EM : O }, fur(O))),
      m.cube(`ear_${s}_tip`, s === "l" ? [-4, 18, -7] : [2, 18, -7], s === "l" ? [-2, 19, -6] : [4, 19, -6], flat(D))];
    // one smooth tail (no stacked pieces): a single long rounded block that fades from orange to an ember tip, tilted up by the bone
    const tailPaint = (x, y, z, f, i, j, w, h) => { const t = (z - 3) / 9.5; return t > .84 ? EM : t > .66 ? GL : fur(O, { noise: .08 })(x, y, z, f, i, j, w, h); };
    const tail = [m.cube("tail_cube", [-1.9, 6.6, 3], [1.9, 10.2, 12.5], tailPaint)];
    const leg = (n, x, z) => m.G(n, [x, 4, z], [m.cube(n + "_cube", [x - 1.1, 0, z - 1.1], [x + 1.1, 4, z + 1.1], (X, y) => y < 1.4 ? D : fur(O)(X, y, z, "north"))]);
    return [m.G("root", [0, 0, 0], [m.G("body", [0, 8, 0], [...body, m.G("head", [0, 9, -4], [...head, m.G("ear_l", [-3, 14, -6.5], ear("l")), m.G("ear_r", [3, 14, -6.5], ear("r"))]), m.G("tail", [0, 8, 3.5], tail, [-28, 0, 0])]),
      leg("leg_fl", -1.8, -2.2), leg("leg_fr", 1.8, -2.2), leg("leg_bl", -1.8, 2.4), leg("leg_br", 1.8, 2.4)])];
  },
  // 썬더쉽 — 번개구름 양: puffy cream wool with glowing zigzag bolts, navy face with yellow eyes, curled golden horns, bolt-shaped tail
  thundersheep(m) {
    const WO = "#fbf3dc", WOS = "#e3d6b4", NV = "#2c3550", NVL = "#46527a", Y = "#ffe14a", YD = "#f2b81c", HR = "#e9c25a", HRD = "#b8892a";
    const wool = fur(WO, { noise: .14, light: .04, dark: .16 });
    const body = [m.cube("body_cube", [-4.5, 3.5, -4.5], [4.5, 11, 4.5], wool), m.cube("wool_top", [-3.5, 10.5, -3.5], [3.5, 13, 3.5], wool),
      m.cube("wool_side_l", [-5.5, 5, -3], [-4.5, 10, 3], wool), m.cube("wool_side_r", [4.5, 5, -3], [5.5, 10, 3], wool), m.cube("belly", [-3.5, 3, -3.5], [3.5, 3.6, 3.5], flat(WOS))];
    const bolt = (n, pts) => pts.map(([x, y, z, w, h], k) => m.cube(`${n}_${k}`, [x, y, z], [x + w, y + h, z + .5], flat(k % 2 ? Y : YD)));
    const marks = [...bolt("bolt_l", [[-5.7, 9, -2, .5, 1.5], [-5.7, 7.5, -1, .5, 1.5], [-5.7, 6, 0, .5, 1.5]].map(([x, y, z, w, h]) => [x, y, z, w, h])),
      ...bolt("bolt_r", [[5.2, 9, -2, .5, 1.5], [5.2, 7.5, -1, .5, 1.5], [5.2, 6, 0, .5, 1.5]]), m.cube("bolt_top", [-.5, 13, -2], [.5, 13.4, 2], flat(Y))];
    const sparks = [m.cube("spark_1", [-6.5, 11, 1], [-5.5, 12, 2], flat(Y)), m.cube("spark_2", [5.5, 8, -3], [6.5, 9, -2], flat(Y)), m.cube("spark_3", [-.5, 14, 2.5], [.5, 15, 3.5], flat(Y)), m.cube("spark_4", [2, 13, -3.5], [3, 14, -2.5], flat(Y))];
    // gentle face: soft slate-blue with big warm brown eyes and pink blush (was a navy face with sharp yellow eyes)
    const head = [m.cube("head_cube", [-3.5, 4.5, -9.5], [3.5, 10.5, -4], bigEyes(fur("#56618a"), 1, 2, { iris: "#7a5038", pupil: "#2a1c16", blush: "#f4a3b0" })),
      m.cube("head_wool", [-4, 9.5, -8.5], [4, 12, -3.5], wool),
      m.cube("horn_l", [-5.5, 8, -7.5], [-3.5, 10, -5], edge(HR, HRD)), m.cube("horn_l_curl", [-5.8, 6.5, -6.8], [-4.4, 8.2, -5.2], flat(HRD)),
      m.cube("horn_r", [3.5, 8, -7.5], [5.5, 10, -5], edge(HR, HRD)), m.cube("horn_r_curl", [4.4, 6.5, -6.8], [5.8, 8.2, -5.2], flat(HRD)),
      m.cube("nose", [-1, 5, -9.8], [1, 6, -9.5], flat(NVL))];
    const tail = [m.cube("tail_bolt_a", [-.6, 7, 4.5], [.6, 9.5, 5.5], flat(Y)), m.cube("tail_bolt_b", [-.6, 8.5, 5.2], [.6, 9.7, 7.2], flat(YD)), m.cube("tail_bolt_c", [-.6, 9.5, 6.8], [.6, 12, 7.8], flat(Y))];
    const leg = (n, x, z) => m.G(n, [x, 4, z], [m.cube(n + "_cube", [x - 1.1, 0, z - 1.1], [x + 1.1, 4, z + 1.1], (X, y) => y < 1.2 ? "#1f2333" : NV)]);
    return [m.G("root", [0, 5, 0], [m.G("body", [0, 4, 0], [...body, m.G("tail", [0, 8, 4.5], tail), m.G("marks_glow", [0, 8, 0], marks), m.G("head", [0, 7, -4.5], head)]),
      leg("leg_fl", -2.5, -2.5), leg("leg_fr", 2.5, -2.5), leg("leg_bl", -2.5, 2.5), leg("leg_br", 2.5, 2.5)])];
  },
  // 젖소 — 방울 젖소: chibi big head with flower-crown hay bit, big brown eyes, heart-shaped patch, golden bell, chunky short legs
  cow(m) {
    const WH = "#fbf8f1", BK = "#2a2830", PK = "#f4a9b4", PKD = "#d9808d", HN = "#f3e2b8", G = "#f2c14e", GD = "#c58f22", RD = "#d4473f", HOOF = "#4a3c34", GR = "#7cc35a", FL = "#ffd84a";
    const heart = (x, y, z) => { const X = (x - 2.5) / 2.2, Y = (y - 9.5) / 2.2; return (X * X + Y * Y - 1) ** 3 - X * X * Y * Y * Y < 0; };
    const patches = (x, y, z, f, i, j, w, h) => (f === "west" || f === "east") && heart(Math.abs(z), y) ? BK : (f === "up" && (x - 1) ** 2 + (z + 1) ** 2 < 5) ? BK : fur(WH, { noise: .03 })(x, y, z, f, i, j, w, h);
    const body = [m.cube("body_cube", [-5, 5, -6], [5, 13, 6.5], patches), m.cube("belly", [-4, 4.4, -4], [4, 5.2, 4.5], flat("#efe8dc"))];
    const udder = [m.cube("udder_cube", [-2, 3, 1.5], [2, 4.6, 4.5], (x, y, z, f, i, j, w, h) => j === h - 1 ? PKD : PK)];
    const head = [m.cube("head_cube", [-5, 9, -13], [5, 17, -5], bigEyes((x, y, z, f, i, j, w, h) => f === "north" && i >= 6 && j < 3 ? BK : fur(WH)(x, y, z, f, i, j, w, h), 2, 3, { iris: "#6a4026", blush: PK })),
      m.cube("muzzle", [-3.5, 9, -14.5], [3.5, 12.5, -13], faces({ north: (x, y, z, f, i, j) => j === 1 && (i === 1 || i === 5) ? PKD : PK }, flat(PK))),
      m.cube("horn_l", [-5.5, 16, -10], [-3.5, 18.5, -8.5], edge(HN, "#cdb88a")), m.cube("horn_r", [3.5, 16, -10], [5.5, 18.5, -8.5], edge(HN, "#cdb88a")),
      m.cube("hay_crown", [-3.5, 17, -10.5], [3.5, 18, -7.5], (x, y, z, f, i) => i % 3 === 1 ? FL : "#e8c85a")];
    const earL = [m.cube("ear_l_cube", [-8, 13.5, -10], [-5, 15, -8.6], faces({ north: flat(PK) }, flat(WH)))], earR = [m.cube("ear_r_cube", [5, 13.5, -10], [8, 15, -8.6], faces({ north: flat(PK) }, flat(BK)))];
    const bell = [m.cube("collar", [-4, 8.6, -6.5], [4, 9.6, -4.8], (x, y, z, f, i) => i % 3 === 0 ? "#9b3029" : RD), m.cube("bell_cube", [-1.4, 6.2, -7], [1.4, 8.8, -5], (x, y, z, f, i, j, w, h) => j === h - 1 ? GD : i === 0 ? "#ffe28a" : G), m.cube("bell_clapper", [-.4, 5.6, -6.3], [.4, 6.2, -5.7], flat(GD))];
    const grass = [m.cube("grass_1", [-2.5, 8.5, -15.4], [2.5, 9.5, -14.4], (x, y, z, f, i) => i % 2 ? GR : "#5aa33d"), m.cube("grass_2", [-1.5, 7.5, -15.2], [-.5, 8.5, -14.6], flat(GR))];
    const tail = [m.cube("tail_cube", [-.5, 7.5, 6.5], [.5, 12.5, 7.5], flat(WH)), m.cube("tail_tuft", [-1, 6, 6.2], [1, 8, 7.8], flat(BK))];
    const leg = (n, x, z) => m.G(n, [x, 5.5, z], [m.cube(n + "_cube", [x - 1.8, 0, z - 1.8], [x + 1.8, 5.5, z + 1.8], (X, y) => y < 1.5 ? HOOF : fur(WH)(X, y, z, "north"))]);
    return [m.G("root", [0, 5.5, 0], [m.G("body", [0, 9, 0], [...body, m.G("udder", [0, 4.5, 3], udder), m.G("tail", [0, 12.5, 7], tail, [12, 0, 0]),
      m.G("head", [0, 12, -5.5], [...head, m.G("ear_l", [-5, 14.2, -9.3], earL), m.G("ear_r", [5, 14.2, -9.3], earR), m.G("bell", [0, 9, -6], bell), m.G("grass_eat", [0, 9, -15], grass)])]),
      leg("leg_fl", -3, -3.5), leg("leg_fr", 3, -3.5), leg("leg_bl", -3, 4), leg("leg_br", 3, 4)])];
  },
  // 펭이 — 썰매꾼 펭귄: chibi round penguin, huge head, red scarf with a tail flapping, snow goggles, little sled rope
  penguin(m) {
    const KB = "#22283a", KBL = "#38415c", WB = "#f7f8fb", O = "#f39a2b", OD = "#c96f12", SC = "#d4383c", SCD = "#9c2427", GO = "#8fd3ff", GOD = "#3b6b8f";
    const body = [m.cube("body_cube", [-4, 1, -3.5], [4, 9.5, 4], (x, y, z, f, i, j, w, h) => f === "north" && i > 0 && i < w - 1 ? WB : fur(KB)(x, y, z, f, i, j, w, h))];
    const scarf = [m.cube("scarf_ring", [-4.4, 9, -4], [4.4, 10.8, 4.2], (x, y, z, f, i) => i % 3 === 0 ? SCD : SC), m.cube("scarf_tail", [2.2, 5, 3.6], [3.8, 9.4, 4.4], (x, y, z, f, i, j) => j % 2 ? SCD : SC)];
    const head = [m.cube("head_cube", [-4.5, 10, -4], [4.5, 18, 4], bigEyes((x, y, z, f, i, j, w, h) => f === "north" && j >= 2 && i > 0 && i < w - 1 ? WB : fur(KB)(x, y, z, f, i, j, w, h), 2, 3, { iris: "#2a3a5a", blush: "#f5a3a9" })),
      m.cube("beak", [-1.3, 12, -5.4], [1.3, 13.4, -4], edge(O, OD)), m.cube("tuft", [-.5, 18, -1], [.5, 19.5, 1], flat(KBL)),
      m.cube("goggle_strap", [-4.7, 15.8, -4.2], [4.7, 16.6, 4.2], flat(GOD)), m.cube("goggle_l", [-3.6, 16, -4.6], [-.8, 17.6, -4.1], edge(GO, GOD)), m.cube("goggle_r", [.8, 16, -4.6], [3.6, 17.6, -4.1], edge(GO, GOD))];
    const wing = (n, s) => m.G(n, [s * 4, 8.5, 0], [m.cube(n + "_cube", s < 0 ? [-5, 3, -2] : [4, 3, -2], s < 0 ? [-4, 9, 2.5] : [5, 9, 2.5], fur(KBL)),
      ...(s > 0 ? [m.cube("rope", [5, 2.5, 1.5], [5.4, 3.3, 6], flat("#b48a54"))] : [])]);
    const foot = (n, x) => m.G(n, [x, 1, -1], [m.cube(n + "_cube", [x - 1.6, 0, -4.4], [x + 1.6, 1.1, -.5], edge(O, OD))]);
    return [m.G("root", [0, 5, 0], [m.G("body", [0, 5, 0], [...body, m.G("scarf", [0, 10, 0], scarf), m.G("head", [0, 10, 0], head), wing("wing_l", -1), wing("wing_r", 1)]), foot("leg_l", -2), foot("leg_r", 2)])];
  },
};

Object.assign(PETS, MORE, NEW, EXTRA);
const only = process.argv.slice(2);
for (const [skin, build] of Object.entries(PETS)) {
  if (only.length && !only.includes(skin)) continue;
  // rounded (chamfered) boxes + shading for a soft blocky look; bigger texture when 128 px is not enough
  let m = new Model(skin, 128, { round: true }), outl;
  try { outl = build(m); } catch (e) { if (!/texture full/.test(e.message)) throw e; m = new Model(skin, 256, { round: true }); outl = build(m); }
  console.log(skin.padEnd(13), m.write(outl, OUT(skin)), "cubes", m.T + "px");
}
