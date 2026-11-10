// Builds the vanilla-style "마토" (wizard bunny) pet model.
// Integer-pixel geometry, 1 texel per model pixel, front faces -Z, feet on y=0.
// Bipedal villager-proportioned rabbit: upright body, two arms, big hind feet,
// round tail, deep-blue wizard hat with a bent tip and a star patch, glowing wand.
import path from "node:path";
import { fileURLToPath } from "node:url";
import { VBuilder, keyframes, bone } from "./lib/vanilla.mjs";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "../..");
const bbmodelOut = path.join(root, "dev/pipi-facilities/assets/pipifacilities/pets/rabbit.bbmodel");
const pngOut = path.join(root, "dev/pipi-pets/out/rabbit.png");

const b = new VBuilder({ width: 64, height: 64 });

// --- materials -------------------------------------------------------------
b.material("fur", [["#7A5638", 5], ["#8C6644", 3], ["#6A4A30", 2]]);
b.material("cream", [["#EAD9B8", 5], ["#F2E4CA", 3], ["#DCC8A0", 2]]);
b.material("hat", [["#2A3A6E", 5], ["#23305C", 3], ["#34467E", 2]]);
b.material("gold", [["#F2C14E", 3], ["#D9A62E", 2]]);
b.material("wand", [["#3E5EA8", 5], ["#324E8C", 3]]);
b.material("glowblue", [["#8FE3FF", 3], ["#C4F2FF", 2], ["#66C8F0", 1]]);
b.material("eye", [["#1E1512", 1]]);
b.material("pink", [["#E39BA8", 2], ["#D98F9C", 1]]);
b.material("dark", [["#2A2018", 2]]);

// --- torso (upright, cream belly on the front) -----------------------------
const body = b.cube("body_cube", [-3, 5, -2], [3, 11, 2], {
  mat: "fur",
  faces: {
    north: (x, y, w, h) => (y >= h - 2 ? "cream" : "fur"),
    up: "fur",
    down: "fur",
    south: "fur",
    east: "fur",
    west: "fur",
  },
});

// --- head (flat vanilla face, hand-placed eyes / cream muzzle / nose) ------
const head = b.cube("head_cube", [-3, 11, -3], [3, 16, 1], {
  mat: "fur",
  legend: { o: "fur", w: "cream", k: "eye", n: "dark" },
  faces: {
    north: ["oooooo", "oooooo", "okooko", "owwwwo", "owwnwo"],
    down: "cream",
    south: "fur",
    east: "fur",
    west: "fur",
    up: "fur",
  },
});

// --- ears (long, poking up through slits in the hat brim) ------------------
const makeEar = (name, x1, innerCol) =>
  b.cube(name, [x1, 14, -2], [x1 + 2, 21, -1], {
    mat: "fur",
    legend: { o: "fur", p: "pink" },
    faces: {
      north: Array.from({ length: 7 }, () => (innerCol === 0 ? "po" : "op")),
      south: "fur",
      east: "fur",
      west: "fur",
      up: "fur",
      down: "fur",
    },
  });
const earL = makeEar("ear_l_cube", -5, 0);
const earR = makeEar("ear_r_cube", 3, 1);

// --- deep-blue wizard hat: wide brim + tapering crown bending to the side ---
const brim = b.cube("hat_brim", [-5, 15, -5], [5, 16, 3], {
  mat: "hat",
  faces: { down: (x, y, w, h) => "hat", north: "hat", south: "hat", east: "hat", west: "hat", up: "hat" },
});
const crown1 = b.cube("hat_crown_1", [-3, 16, -3], [3, 19, 2], { mat: "hat" });
const crown2 = b.cube("hat_crown_2", [-2, 19, -2], [2, 21, 1], { mat: "hat" });
const crown3 = b.cube("hat_crown_3", [-1, 21, -1], [1, 23, 0], { mat: "hat" });
const tipA = b.cube("hat_tip_a", [0, 23, -1], [2, 25, 0], { mat: "hat" });
const tipB = b.cube("hat_tip_b", [1, 25, -1], [3, 27, 0], { mat: "hat" });
const star = b.cube("hat_star", [-1, 17, -4], [1, 19, -3], { mat: "gold" });

// --- arms (thin, hanging at the sides) -------------------------------------
const armL = b.cube("arm_l_cube", [-5, 5, -1], [-3, 10, 0], { mat: "fur", faces: { down: "dark" } });
const armR = b.cube("arm_r_cube", [3, 5, -1], [5, 10, 0], { mat: "fur", faces: { down: "dark" } });

// --- wand held in the right hand, glowing crystal tip ----------------------
const shaft = b.cube("wand_shaft", [4, 3, -3], [5, 12, -2], { mat: "wand", faces: { down: "dark" } });
const tip = b.cube("wand_tip", [3, 12, -3], [5, 14, -2], { mat: "glowblue" });

// --- work-only sparkles (glow group nested inside the _work group) ---------
const spark1 = b.cube("spark_1", [5, 12, -3], [6, 13, -2], { mat: "gold" });
const spark2 = b.cube("spark_2", [2, 13, -3], [3, 14, -2], { mat: "gold" });
const spark3 = b.cube("spark_3", [3, 15, -3], [4, 16, -2], { mat: "gold" });

// --- round tail ------------------------------------------------------------
const tail = b.cube("tail_cube", [-2, 5, 2], [2, 8, 4], {
  mat: "fur",
  faces: { south: "fur", up: "fur", down: "fur", north: "fur", east: "fur", west: "fur" },
});

// --- big hind feet + thin shins --------------------------------------------
const legL = b.cube("leg_l_cube", [-3, 2, -1], [-1, 5, 0], { mat: "fur" });
const footL = b.cube("foot_l_cube", [-3, 0, -3], [-1, 2, 1], { mat: "fur", faces: { down: "dark" } });
const legR = b.cube("leg_r_cube", [1, 2, -1], [3, 5, 0], { mat: "fur" });
const footR = b.cube("foot_r_cube", [1, 0, -3], [3, 2, 1], { mat: "fur", faces: { down: "dark" } });

// --- bones -----------------------------------------------------------------
const earLG = b.group("ear_l", [-4, 14, -1], [earL]);
const earRG = b.group("ear_r", [4, 14, -1], [earR]);
const hatG = b.group("hat", [0, 16, 0], [brim, crown1, crown2, crown3, tipA, tipB, star]);
const headG = b.group("head", [0, 11, 0], [head, earLG, earRG, hatG]);
const tailG = b.group("tail", [0, 6, 2], [tail], { rotation: [-20, 0, 0] });
const armLG = b.group("arm_l", [-3, 10, 0], [armL]);
const tipG = b.group("tip_glow", [4, 13, -2], [tip]);
const sparkGlowG = b.group("spark_glow", [4, 14, -2], [spark1, spark2, spark3]);
const sparkG = b.group("spark_work", [4, 14, -2], [sparkGlowG]);
const wandG = b.group("wand", [4, 5, -3], [shaft, tipG, sparkG]);
const armRG = b.group("arm_r", [3, 10, 0], [armR, wandG]);
const bodyG = b.group("body", [0, 5, 0], [body, tailG, armLG, armRG, headG]);
const legLG = b.group("leg_l", [-2, 5, 0], [legL, footL]);
const legRG = b.group("leg_r", [2, 5, 0], [legR, footR]);
const rootG = b.group("root", [0, 0, 0], [bodyG, legLG, legRG]);
b.outliner.push(rootG);

// --- animations ------------------------------------------------------------
// Keyframe rotation is applied by PetModels as (-x,-y,+z); positive keyframe X
// on the head nods it down. Catmullrom for organic ease, ears/tail lag the body
// for follow-through, every loop has identical first/last keys, linear accents.
const CR = "catmullrom";

// idle 4 s: breathing, slow look-around, free paw adjusts the hat, ear sway.
const idle = {
  [bodyG.uuid]: bone(
    "body",
    keyframes("scale", [[0, 1, 1, 1], [1, 1, 1.02, 1], [2, 1, 1, 1], [3, 1, 1.02, 1], [4, 1, 1, 1]], CR),
  ),
  [headG.uuid]: bone(
    "head",
    keyframes(
      "rotation",
      [
        [0.0, 0, 0, 0],
        [0.9, 0, 10, 0],
        [1.7, 0, 10, -5],
        [2.4, 0, 0, 0],
        [3.0, 0, -8, 0],
        [3.5, 0, -8, 4],
        [4.0, 0, 0, 0],
      ],
      CR,
    ),
  ),
  [armLG.uuid]: bone(
    "arm_l",
    keyframes(
      "rotation",
      [
        [0.0, 0, 0, 0],
        [2.2, 0, 0, 0],
        [2.6, -110, 0, -20],
        [3.1, -115, 0, -20],
        [3.4, -30, 0, 0],
        [3.7, 0, 0, 0],
        [4.0, 0, 0, 0],
      ],
      CR,
    ),
  ),
  [earLG.uuid]: bone(
    "ear_l",
    keyframes("rotation", [[0, 0, 0, 0], [0.6, 0, 0, 10], [1.4, 0, 0, -6], [2.2, 0, 0, 0], [3.2, 0, 0, 8], [4, 0, 0, 0]], CR),
  ),
  [earRG.uuid]: bone(
    "ear_r",
    keyframes("rotation", [[0, 0, 0, 0], [0.6, 0, 0, -10], [1.4, 0, 0, 6], [2.2, 0, 0, 0], [3.2, 0, 0, -8], [4, 0, 0, 0]], CR),
  ),
  [tailG.uuid]: bone(
    "tail",
    keyframes("rotation", [[0, 0, 10, 0], [1.3, 0, -10, 0], [2.6, 0, 10, 0], [4, 0, 10, 0]], CR),
  ),
};

// walk 0.7 s: two-legged waddle, leg out of phase, body sway, arm swing
// (wand arm swings less), lagging ears and tail.
const WSWING = 30;
const walk = {
  [legLG.uuid]: bone("leg_l", keyframes("rotation", [[0, WSWING, 0, 0], [0.35, -WSWING, 0, 0], [0.7, WSWING, 0, 0]], CR)),
  [legRG.uuid]: bone("leg_r", keyframes("rotation", [[0, -WSWING, 0, 0], [0.35, WSWING, 0, 0], [0.7, -WSWING, 0, 0]], CR)),
  [bodyG.uuid]: bone(
    "body",
    keyframes("position", [[0, 0, 0, 0], [0.175, 0, 0.4, 0], [0.35, 0, 0, 0], [0.525, 0, 0.4, 0], [0.7, 0, 0, 0]], CR),
    keyframes("rotation", [[0, 0, 0, 0], [0.175, 0, 0, 3], [0.35, 0, 0, 0], [0.525, 0, 0, -3], [0.7, 0, 0, 0]], CR),
  ),
  [headG.uuid]: bone(
    "head",
    keyframes("position", [[0, 0, 0, 0], [0.175, 0, -0.4, 0], [0.35, 0, 0, 0], [0.525, 0, -0.4, 0], [0.7, 0, 0, 0]], CR),
    keyframes("rotation", [[0, 0, 0, 0], [0.175, 0, -3, 0], [0.35, 0, 0, 0], [0.525, 0, 3, 0], [0.7, 0, 0, 0]], CR),
  ),
  [armLG.uuid]: bone("arm_l", keyframes("rotation", [[0, 22, 0, 0], [0.35, -22, 0, 0], [0.7, 22, 0, 0]], CR)),
  [armRG.uuid]: bone("arm_r", keyframes("rotation", [[0, -8, 0, 0], [0.35, 8, 0, 0], [0.7, -8, 0, 0]], CR)),
  [earLG.uuid]: bone("ear_l", keyframes("rotation", [[0, 0, 0, 0], [0.175, -8, 0, 0], [0.35, 0, 0, 0], [0.525, -8, 0, 0], [0.7, 0, 0, 0]], CR)),
  [earRG.uuid]: bone("ear_r", keyframes("rotation", [[0, 0, 0, 0], [0.175, -8, 0, 0], [0.35, 0, 0, 0], [0.525, -8, 0, 0], [0.7, 0, 0, 0]], CR)),
  [tailG.uuid]: bone("tail", keyframes("rotation", [[0, 0, -10, 0], [0.175, 0, 0, 0], [0.35, 0, 10, 0], [0.525, 0, 0, 0], [0.7, 0, -10, 0]], CR)),
};

// work 1.6 s: raises the wand, draws a circle overhead, sparkles pop, head up.
const work = {
  [rootG.uuid]: bone("root", keyframes("position", [[0, 0, 0, 0], [0.8, 0, 0.6, 0], [1.6, 0, 0, 0]], CR)),
  [armRG.uuid]: bone(
    "arm_r",
    keyframes("rotation", [[0, 0, 0, 0], [0.4, 0, 0, 80], [1.2, 0, 0, 80], [1.6, 0, 0, 0]], CR),
  ),
  [wandG.uuid]: bone(
    "wand",
    keyframes(
      "rotation",
      [[0, 0, 0, 0], [0.4, 25, 0, 25], [0.8, 0, 0, 0], [1.2, -25, 0, -25], [1.6, 0, 0, 0]],
      CR,
    ),
  ),
  [headG.uuid]: bone("head", keyframes("rotation", [[0, -10, 0, 0], [1.6, -10, 0, 0]], CR)),
  [sparkG.uuid]: bone(
    "spark_work",
    keyframes(
      "scale",
      [
        [0, 0, 0, 0],
        [0.35, 0, 0, 0],
        [0.4, 1, 1, 1],
        [0.6, 1, 1, 1],
        [0.65, 0, 0, 0],
        [0.9, 0, 0, 0],
        [0.95, 1, 1, 1],
        [1.15, 1, 1, 1],
        [1.2, 0, 0, 0],
        [1.4, 0, 0, 0],
        [1.45, 1, 1, 1],
        [1.55, 1, 1, 1],
        [1.6, 0, 0, 0],
      ],
      CR,
    ),
  ),
  [tailG.uuid]: bone(
    "tail",
    keyframes("rotation", [[0, 0, 10, 0], [0.4, 0, -10, 0], [0.8, 0, 10, 0], [1.2, 0, -10, 0], [1.6, 0, 10, 0]], CR),
  ),
  [earLG.uuid]: bone("ear_l", keyframes("rotation", [[0, -6, 0, 0], [1.6, -6, 0, 0]], CR)),
  [earRG.uuid]: bone("ear_r", keyframes("rotation", [[0, -6, 0, 0], [1.6, -6, 0, 0]], CR)),
};

// eat 1.0 s: head held down, two nibble dips, free paw helps, tail sways.
const eat = {
  [headG.uuid]: bone(
    "head",
    keyframes("rotation", [[0, 28, 0, 0], [0.25, 34, 0, 0], [0.5, 28, 0, 0], [0.75, 34, 0, 0], [1, 28, 0, 0]], CR),
  ),
  [armLG.uuid]: bone("arm_l", keyframes("rotation", [[0, -40, 0, 0], [0.5, -45, 0, 0], [1, -40, 0, 0]], CR)),
  [earLG.uuid]: bone("ear_l", keyframes("rotation", [[0, 0, 0, 0], [0.25, 12, 0, 0], [0.5, 0, 0, 0], [0.75, 12, 0, 0], [1, 0, 0, 0]], CR)),
  [earRG.uuid]: bone("ear_r", keyframes("rotation", [[0, 0, 0, 0], [0.25, 12, 0, 0], [0.5, 0, 0, 0], [0.75, 12, 0, 0], [1, 0, 0, 0]], CR)),
  [tailG.uuid]: bone("tail", keyframes("rotation", [[0, 0, 8, 0], [0.5, 0, -8, 0], [1, 0, 8, 0]], CR)),
};

// hurt 0.35 s once: instant flinch, overshoot past rest, settle.
const hurt = {
  [bodyG.uuid]: bone("body", keyframes("scale", [[0, 1.12, 0.85, 1.12], [0.15, 0.97, 1.04, 0.97], [0.35, 1, 1, 1]], CR)),
  [headG.uuid]: bone("head", keyframes("rotation", [[0, -20, 0, 0], [0.15, 6, 0, 0], [0.35, 0, 0, 0]], CR)),
  [armLG.uuid]: bone("arm_l", keyframes("rotation", [[0, -30, 0, 0], [0.15, 5, 0, 0], [0.35, 0, 0, 0]], CR)),
  [armRG.uuid]: bone("arm_r", keyframes("rotation", [[0, -30, 0, 0], [0.15, 5, 0, 0], [0.35, 0, 0, 0]], CR)),
  [earLG.uuid]: bone("ear_l", keyframes("rotation", [[0, -30, 0, 0], [0.15, 5, 0, 0], [0.35, 0, 0, 0]], CR)),
  [earRG.uuid]: bone("ear_r", keyframes("rotation", [[0, -30, 0, 0], [0.15, 5, 0, 0], [0.35, 0, 0, 0]], CR)),
};

b.animation("idle", "loop", 4.0, idle);
b.animation("walk", "loop", 0.7, walk);
b.animation("work", "loop", 1.6, work);
b.animation("eat", "loop", 1.0, eat);
b.animation("hurt", "once", 0.35, hurt);

b.write(bbmodelOut, pngOut, "rabbit");
console.log(`wrote ${bbmodelOut}`);
console.log(`wrote ${pngOut}`);
