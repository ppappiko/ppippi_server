// Builds the vanilla-style "검은고양이" (haughty gyaru black cat) pet model.
// Integer-pixel geometry, 1 texel per model pixel, front faces -Z.
import path from "node:path";
import { fileURLToPath } from "node:url";
import { VBuilder, keyframes, bone } from "./lib/vanilla.mjs";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "../..");
const bbmodelOut = path.join(root, "dev/pipi-facilities/assets/pipifacilities/pets/cat.bbmodel");
const pngOut = path.join(root, "dev/pipi-pets/out/cat.png");

const b = new VBuilder({ width: 64, height: 64 });

// --- materials -------------------------------------------------------------
b.material("fur", [["#2A2630", 5], ["#1E1B22", 3], ["#35303C", 2]]);
b.material("furL", [["#35303C", 1]]);
b.material("dark", [["#0E0C12", 1]]);
b.material("eye", [["#E8C440", 1]]);
b.material("pink", [["#F0A0B4", 3], ["#E68CA4", 1]]);
b.material("gold", [["#E3B23C", 2], ["#C99A2A", 1]]);
b.material("collar", [["#E86FA0", 2], ["#D25A8C", 1]]);
b.material("lash", [["#5A4478", 3], ["#4A3562", 1]]);
b.material("heart", [["#C43A5A", 1]]);

// --- legs (2x5x2, top 3 rows fur / bottom 2 dark) ---------------------------
const legFaces = {
  north: (x, y) => (y < 3 ? "fur" : "dark"),
  south: (x, y) => (y < 3 ? "fur" : "dark"),
  east: (x, y) => (y < 3 ? "fur" : "dark"),
  west: (x, y) => (y < 3 ? "fur" : "dark"),
  up: "fur",
  down: "dark",
};
const makeLeg = (name, x1, z1) =>
  b.cube(name, [x1, 0, z1], [x1 + 2, 5, z1 + 2], { faces: legFaces });
const legFl = makeLeg("leg_fl_cube", -3, -5);
const legFr = makeLeg("leg_fr_cube", 1, -5);
const legBl = makeLeg("leg_bl_cube", -3, 3);
const legBr = makeLeg("leg_br_cube", 1, 3);

// --- front claws (pink with gold tips, 2 per paw) --------------------------
const clawFaces = { north: "gold", south: "pink", east: "pink", west: "pink", up: "pink", down: "pink" };
const makeClaw = (name, x1) => b.cube(name, [x1, 0, -6.5], [x1 + 1, 1, -5], { faces: clawFaces });
const clawFlA = makeClaw("claw_fl_a_cube", -3);
const clawFlB = makeClaw("claw_fl_b_cube", -2);
const clawFrA = makeClaw("claw_fr_a_cube", 1);
const clawFrB = makeClaw("claw_fr_b_cube", 2);

// --- body (long low cat body) ----------------------------------------------
const bodyBottomDark = (x, y, w, h) => (y === h - 1 ? "dark" : y === 0 ? "furL" : "fur");
const body = b.cube("body_cube", [-3, 5, -6], [3, 10, 6], {
  mat: "fur",
  faces: {
    up: "furL",
    down: "dark",
    north: bodyBottomDark,
    south: bodyBottomDark,
    east: bodyBottomDark,
    west: bodyBottomDark,
  },
});

// --- choker (pink band at the neck) with a small heart ---------------------
const choker = b.cube("choker_cube", [-3, 9, -6], [3, 10, -3], {
  mat: "collar",
  inflate: 0.3,
  faces: { up: "collar", down: "collar", north: "collar", south: "collar", east: "collar", west: "collar" },
});
const heart = b.cube("heart_cube", [-1, 8, -7], [1, 10, -6], {
  mat: "heart",
  legend: { h: "heart", p: "pink" },
  faces: { north: ["hh", "pp"], down: "heart", up: "heart", south: "heart", east: "heart", west: "heart" },
});

// --- head ------------------------------------------------------------------
const head = b.cube("head_cube", [-3, 9, -10], [3, 14, -5], {
  mat: "fur",
  legend: { o: "fur", k: "dark", e: "eye", p: "pink" },
  faces: {
    north: ["oooooo", "oooooo", "oeooeo", "ooppoo", "oooooo"],
    up: "fur",
    down: "fur",
    south: "fur",
    east: "fur",
    west: "fur",
  },
});
// long lashes poking forward above and outward of each eye (eyes at x 1..2 and -2..-1, y 11..12)
const lashCubes = [
  b.cube("lash_l_top_cube", [1, 12, -11.5], [2, 13, -10], { mat: "lash" }),
  b.cube("lash_l_out_cube", [2, 12, -11.5], [3, 13, -10], { mat: "lash" }),
  b.cube("lash_l_out2_cube", [3, 13, -11], [4, 14, -10], { mat: "lash" }),
  b.cube("lash_r_top_cube", [-2, 12, -11.5], [-1, 13, -10], { mat: "lash" }),
  b.cube("lash_r_out_cube", [-3, 12, -11.5], [-2, 13, -10], { mat: "lash" }),
  b.cube("lash_r_out2_cube", [-4, 13, -11], [-3, 14, -10], { mat: "lash" }),
];
const snout = b.cube("snout_cube", [-1, 9, -11], [1, 11, -10], {
  mat: "fur",
  legend: { o: "fur", p: "pink" },
  faces: { north: ["pp", "oo"], up: "fur", down: "fur", east: "fur", west: "fur", south: "fur" },
});
const makeEar = (name, x1) =>
  b.cube(name, [x1, 14, -8], [x1 + 2, 17, -7], {
    mat: "fur",
    legend: { o: "fur", k: "dark", p: "pink" },
    faces: {
      north: ["kk", "op", "op"],
      south: ["kk", "oo", "oo"],
      east: ["k", "o", "o"],
      west: ["k", "o", "o"],
      up: ["kk"],
      down: ["oo"],
    },
  });
const earL = makeEar("ear_l_cube", -3);
const earR = makeEar("ear_r_cube", 1);

// --- gold hoop earring on the right ear (4 cubes forming a ring) -----------
const hoopTop = b.cube("hoop_top_cube", [3, 14, -8], [4, 15, -6], { mat: "gold" });
const hoopBot = b.cube("hoop_bot_cube", [3, 12, -8], [4, 13, -6], { mat: "gold" });
const hoopFront = b.cube("hoop_front_cube", [3, 13, -8], [4, 14, -7], { mat: "gold" });
const hoopBack = b.cube("hoop_back_cube", [3, 13, -7], [4, 14, -6], { mat: "gold" });

// --- tail (long, curving up; base + tip child) ------------------------------
const tail = b.cube("tail_cube", [-1, 8, 6], [1, 11, 13], {
  mat: "fur",
  faces: { north: "fur", south: "fur", east: "fur", west: "fur", up: "furL", down: "fur" },
});
const tailTip = b.cube("tail_tip_cube", [-1, 8, 13], [1, 11, 18], {
  mat: "fur",
  faces: { north: "fur", south: "furL", east: "fur", west: "fur", up: "furL", down: "fur" },
});

// --- bones -----------------------------------------------------------------
const earLG = b.group("ear_l", [-2, 14, -7.5], [earL]);
const earRG = b.group("ear_r", [2, 14, -7.5], [earR, hoopTop, hoopBot, hoopFront, hoopBack]);
const headG = b.group("head", [0, 10, -5], [head, snout, ...lashCubes, earLG, earRG]);
const tailTipG = b.group("tail_tip", [0, 9.5, 13], [tailTip], { rotation: [-15, 0, 0] });
const tailG = b.group("tail", [0, 9, 6], [tail, tailTipG], { rotation: [-40, 0, 0] });
const bodyG = b.group("body", [0, 8, 0], [body, choker, heart, headG, tailG]);
const legFlG = b.group("leg_fl", [-2, 5, -4], [legFl, clawFlA, clawFlB]);
const legFrG = b.group("leg_fr", [2, 5, -4], [legFr, clawFrA, clawFrB]);
const legBlG = b.group("leg_bl", [-2, 5, 4], [legBl]);
const legBrG = b.group("leg_br", [2, 5, 4], [legBr]);
const rootG = b.group("root", [0, 0, 0], [bodyG, legFlG, legFrG, legBlG, legBrG]);
b.outliner.push(rootG);

// --- animations ------------------------------------------------------------
// Keyframe rotation is applied by PetModels as (-x,-y,+z); rest bone rotation
// is applied directly. Positive keyframe X on the head nods it down. Curves use
// catmullrom; ears/tail lag the body for follow-through; loops share first/last.
const CR = "catmullrom";

// idle 4 s: breathing, lift the chin and look away disdainfully, tail-tip flick.
const idle = {
  [bodyG.uuid]: bone(
    "body",
    keyframes("scale", [[0, 1, 1, 1], [1, 1, 1.03, 1], [2, 1, 1, 1], [3, 1, 1.03, 1], [4, 1, 1, 1]], CR),
  ),
  [headG.uuid]: bone(
    "head",
    keyframes(
      "rotation",
      [
        [0.0, 0, 0, 0],
        [1.1, 0, 0, 0],
        [1.4, -14, 20, 0],
        [2.6, -12, 22, 0],
        [2.9, 0, 4, 0],
        [3.3, 0, 0, 0],
        [4.0, 0, 0, 0],
      ],
      CR,
    ),
  ),
  [tailG.uuid]: bone("tail", keyframes("rotation", [[0, -40, 5, 0], [1.3, -40, -5, 0], [2.5, -40, 5, 0], [3.3, -40, -5, 0], [4.0, -40, 5, 0]], CR)),
  [tailTipG.uuid]: bone(
    "tail_tip",
    keyframes(
      "rotation",
      [
        [0, -15, 0, 0],
        [2.2, -15, 0, 0],
        [2.3, -15, 16, 0, "linear"],
        [2.45, -15, -8, 0],
        [2.7, -15, 0, 0],
        [4.0, -15, 0, 0],
      ],
      CR,
    ),
  ),
  [earLG.uuid]: bone("ear_l", keyframes("rotation", [[0, 0, 0, 0], [1.6, 0, 0, 0], [1.7, 0, 0, 18], [1.9, 0, 0, 0], [4.0, 0, 0, 0]], CR)),
};

// walk 0.8 s: elegant catwalk, diagonal legs, tail high with a slow sway, hip roll.
const SWING = 28;
const walk = {
  [legFlG.uuid]: bone("leg_fl", keyframes("rotation", [[0, SWING, 0, 0], [0.4, -SWING, 0, 0], [0.8, SWING, 0, 0]], CR)),
  [legBrG.uuid]: bone("leg_br", keyframes("rotation", [[0, SWING, 0, 0], [0.4, -SWING, 0, 0], [0.8, SWING, 0, 0]], CR)),
  [legFrG.uuid]: bone("leg_fr", keyframes("rotation", [[0, -SWING, 0, 0], [0.4, SWING, 0, 0], [0.8, -SWING, 0, 0]], CR)),
  [legBlG.uuid]: bone("leg_bl", keyframes("rotation", [[0, -SWING, 0, 0], [0.4, SWING, 0, 0], [0.8, -SWING, 0, 0]], CR)),
  [bodyG.uuid]: bone(
    "body",
    keyframes("position", [[0, 0, 0, 0], [0.2, 0, -0.4, 0], [0.4, 0, 0, 0], [0.6, 0, -0.4, 0], [0.8, 0, 0, 0]], CR),
    keyframes("rotation", [[0, 0, 0, 0], [0.2, 0, 0, 3], [0.4, 0, 0, 0], [0.6, 0, 0, -3], [0.8, 0, 0, 0]], CR),
  ),
  [headG.uuid]: bone(
    "head",
    keyframes("rotation", [[0, 0, 0, 0], [0.2, -3, 0, 0], [0.4, 0, 0, 0], [0.6, -3, 0, 0], [0.8, 0, 0, 0]], CR),
  ),
  [tailG.uuid]: bone("tail", keyframes("rotation", [[0, -50, -10, 0], [0.2, -50, 0, 0], [0.4, -50, 10, 0], [0.6, -50, 0, 0], [0.8, -50, -10, 0]], CR)),
  [tailTipG.uuid]: bone("tail_tip", keyframes("rotation", [[0, -15, -8, 0], [0.4, -15, 8, 0], [0.8, -15, -8, 0]], CR)),
};

// work 1.6 s: admire/files the left claws, then a quick precise swipe with the right.
const work = {
  [legFlG.uuid]: bone(
    "leg_fl",
    keyframes(
      "rotation",
      [
        [0, 0, 0, 0],
        [0.25, -100, 0, 0],
        [0.45, -95, 0, 0],
        [0.6, -105, 0, 0],
        [0.75, -95, 0, 0],
        [1.0, 0, 0, 0],
        [1.6, 0, 0, 0],
      ],
      CR,
    ),
  ),
  [legFrG.uuid]: bone(
    "leg_fr",
    keyframes(
      "rotation",
      [
        [0, 0, 0, 0],
        [1.05, 0, 0, 0],
        [1.15, -120, 0, 0, "linear"],
        [1.25, -40, 0, 0],
        [1.4, 0, 0, 0],
        [1.6, 0, 0, 0],
      ],
      CR,
    ),
  ),
  [headG.uuid]: bone(
    "head",
    keyframes(
      "rotation",
      [
        [0, 0, 0, 0],
        [0.25, 10, -12, 0],
        [0.75, 8, -12, 0],
        [1.1, 0, 12, 0],
        [1.4, 0, 0, 0],
        [1.6, 0, 0, 0],
      ],
      CR,
    ),
  ),
  [tailG.uuid]: bone("tail", keyframes("rotation", [[0, -40, 12, 0], [0.4, -40, -12, 0], [0.8, -40, 12, 0], [1.2, -40, -12, 0], [1.6, -40, 12, 0]], CR)),
  [earRG.uuid]: bone("ear_r", keyframes("rotation", [[0, 0, 0, 0], [1.1, 0, 0, 0], [1.2, 0, 0, 26], [1.35, 0, 0, 0], [1.6, 0, 0, 0]], CR)),
};

// eat 1.4 s: head down nibbling, slow tail sway, ears relaxed.
const eat = {
  [headG.uuid]: bone(
    "head",
    keyframes(
      "rotation",
      [
        [0.0, 36, 0, 0],
        [0.25, 36, 0, 0],
        [0.5, 41, 0, 0],
        [0.65, 36, 0, 0],
        [0.8, 41, 0, 0],
        [0.95, 36, 0, 0],
        [1.15, 26, 0, 0],
        [1.4, 36, 0, 0],
      ],
      CR,
    ),
  ),
  [tailG.uuid]: bone("tail", keyframes("rotation", [[0, -30, 8, 0], [0.35, -30, -8, 0], [0.7, -30, 8, 0], [1.05, -30, -8, 0], [1.4, -30, 8, 0]], CR)),
  [earLG.uuid]: bone("ear_l", keyframes("rotation", [[0, 0, 0, 0], [1.4, 0, 0, 0]], CR)),
};

// hurt 0.4 s once: instant flinch back, overshoot past rest, settle.
const hurt = {
  [headG.uuid]: bone("head", keyframes("rotation", [[0, -18, 0, 0], [0.25, 4, 0, 0], [0.4, 0, 0, 0]], CR)),
  [bodyG.uuid]: bone("body", keyframes("scale", [[0, 1.06, 0.92, 1.06], [0.25, 0.98, 1.02, 0.98], [0.4, 1, 1, 1]], CR)),
  [earLG.uuid]: bone("ear_l", keyframes("rotation", [[0, -26, 0, 0], [0.25, 5, 0, 0], [0.4, 0, 0, 0]], CR)),
  [earRG.uuid]: bone("ear_r", keyframes("rotation", [[0, -26, 0, 0], [0.25, 5, 0, 0], [0.4, 0, 0, 0]], CR)),
  [tailG.uuid]: bone("tail", keyframes("rotation", [[0, -20, 0, 0], [0.25, -45, 0, 0], [0.4, -40, 0, 0]], CR)),
};

b.animation("idle", "loop", 4.0, idle);
b.animation("walk", "loop", 0.8, walk);
b.animation("work", "loop", 1.6, work);
b.animation("eat", "loop", 1.4, eat);
b.animation("hurt", "once", 0.4, hurt);

b.write(bbmodelOut, pngOut, "cat");
console.log(`wrote ${bbmodelOut}`);
console.log(`wrote ${pngOut}`);
