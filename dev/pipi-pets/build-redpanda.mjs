// Builds the vanilla-style "레시" (red panda with a leaf on its head) pet model.
// Integer-pixel geometry, 1 texel per model pixel, front faces -Z, feet on y=0.
import path from "node:path";
import { fileURLToPath } from "node:url";
import { VBuilder, keyframes, bone } from "./lib/vanilla.mjs";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "../..");
const bbmodelOut = path.join(root, "dev/pipi-facilities/assets/pipifacilities/pets/redpanda.bbmodel");
const pngOut = path.join(root, "dev/pipi-pets/out/redpanda.png");

const b = new VBuilder({ width: 64, height: 64 });

// --- materials (weighted multi-tone noise, 3 tones each) -------------------
b.material("rust", [["#B8522A", 5], ["#C9622F", 3], ["#A24624", 2]]);
b.material("mask", [["#F4EEE4", 4], ["#E9E0D2", 2], ["#FFFFFF", 1]]);
b.material("dark", [["#3A2420", 5], ["#2E1C18", 2], ["#4A2E28", 2]]);
b.material("cream", [["#EFDFC4", 4], ["#E2CEAE", 2], ["#F7EEDD", 1]]);
b.material("leaf", [["#5FA83E", 4], ["#4E9132", 2], ["#7CC24E", 1]]);
b.material("vein", [["#93D264", 2], ["#A6DC74", 1]]);
b.material("stem", [["#7A8C42", 2], ["#66783A", 1]]);
b.material("nose", [["#2A1A16", 2], ["#1E120F", 1]]);
b.material("eye", [["#1A1210", 1]]);
b.material("stick", [["#6FA83A", 3], ["#5C8F2E", 2], ["#84BC4A", 1]]);

// --- legs (2x3x2, top pivots, all dark) ------------------------------------
const legFaces = { down: "dark" };
const makeLeg = (name, x, z) =>
  b.cube(name, [x - 1, 0, z - 1], [x + 1, 3, z + 1], { mat: "dark", faces: legFaces });
const legFl = makeLeg("leg_fl_cube", -2, -3);
const legFr = makeLeg("leg_fr_cube", 2, -3);
const legBl = makeLeg("leg_bl_cube", -2, 3);
const legBr = makeLeg("leg_br_cube", 2, 3);

// --- body (rust, dark belly) -----------------------------------------------
const body = b.cube("body_cube", [-3, 3, -4], [3, 8, 4], {
  mat: "rust",
  faces: { up: "rust", down: "dark", north: "rust", south: "rust", east: "rust", west: "rust" },
});

// --- head (rust, white mask face) ------------------------------------------
const head = b.cube("head_cube", [-4, 7, -9], [4, 13, -4], {
  mat: "rust",
  legend: { r: "rust", w: "mask", k: "eye", m: "mask", n: "nose" },
  faces: {
    north: [
      "rrrrrrrr",
      "rwwrrwwr",
      "rwkwwkwr",
      "rwmnnmwr",
      "wwmmmmww",
      "wwwwwwww",
    ],
    down: "mask",
    south: "rust",
    east: (x, y, w, h) => (y >= 4 ? "mask" : "rust"),
    west: (x, y, w, h) => (y >= 4 ? "mask" : "rust"),
    up: "rust",
  },
});

// --- ears (2x2x2, white inner) ---------------------------------------------
const makeEar = (name, x1) =>
  b.cube(name, [x1, 13, -8], [x1 + 2, 15, -6], {
    mat: "rust",
    legend: { w: "mask" },
    faces: { north: ["ww", "ww"], down: "dark", up: "rust", south: "rust", east: "rust", west: "rust" },
  });
const earL = makeEar("ear_l_cube", -4);
const earR = makeEar("ear_r_cube", 2);

// --- leaf on the head (blade + stem), bone `leaf` --------------------------
const leafBlade = b.cube("leaf_blade_cube", [-1, 13, -8], [2, 14, -3], {
  mat: "leaf",
  faces: {
    up: (x, y, w, h) => (x === 1 ? "vein" : "leaf"),
    down: "leaf",
    north: "leaf",
    south: "leaf",
    east: "leaf",
    west: "leaf",
  },
});
const leafStem = b.cube("leaf_stem_cube", [0, 13, -3], [1, 14, -2], { mat: "stem" });

// --- tail (long bushy rust with cream rings) -------------------------------
const ring = (x) => (x % 3 === 2 ? "cream" : "rust");
const tail = b.cube("tail_cube", [-2, 3, 4], [2, 9, 13], {
  mat: "rust",
  faces: {
    up: (x, y, w, h) => ring(y),
    down: (x, y, w, h) => ring(y),
    east: (x, y, w, h) => ring(x),
    west: (x, y, w, h) => ring(x),
    north: (x, y, w, h) => (y % 3 === 2 ? "cream" : "rust"),
    south: "cream",
  },
});

// --- food stick held while eating (bone `food_eat`) ------------------------
const food = b.cube("food_cube", [0, 8, -14], [1, 9, -8], {
  mat: "stick",
  faces: { north: "stick", south: "stick", up: "stick", down: "stick", east: "stick", west: "stick" },
});

// --- bones -----------------------------------------------------------------
const earLG = b.group("ear_l", [-3, 13, -7], [earL]);
const earRG = b.group("ear_r", [3, 13, -7], [earR]);
const leafG = b.group("leaf", [0, 13, -3], [leafBlade, leafStem], { rotation: [-6, 0, 16] });
const foodG = b.group("food_eat", [-0.5, 8, -8], [food]);
const headG = b.group("head", [0, 10, -4], [head, earLG, earRG, leafG, foodG]);
const tailG = b.group("tail", [0, 6, 4], [tail]);
const legFlG = b.group("leg_fl", [-2, 3, -3], [legFl]);
const legFrG = b.group("leg_fr", [2, 3, -3], [legFr]);
const bodyG = b.group("body", [0, 7, 0], [body, headG, tailG, legFlG, legFrG]);
const legBlG = b.group("leg_bl", [-2, 3, 3], [legBl]);
const legBrG = b.group("leg_br", [2, 3, 3], [legBr]);
const rootG = b.group("root", [0, 0, 0], [bodyG, legBlG, legBrG]);
b.outliner.push(rootG);

// --- animations ------------------------------------------------------------
// Keyframe rotation is applied by PetModels as (-x,-y,+z); rest bone rotation
// is applied directly. Positive keyframe X on a head/body tips its front (-Z)
// down, so rearing up uses negative X. Loops keep identical first/last keys;
// the leaf, tail and ears lag the body for overlapping follow-through.
const CR = "catmullrom";

// idle 4 s: breathing, glance up at the leaf, leaf wobble, tail sway, ear twitch.
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
        [0, 0, 0, 0],
        [0.8, 0, 0, 0],
        [1.5, -16, 0, 0],
        [2.0, -16, 8, 0],
        [2.4, -4, 0, 0],
        [3.0, 0, 0, 0],
        [4.0, 0, 0, 0],
      ],
      CR,
    ),
  ),
  [leafG.uuid]: bone(
    "leaf",
    keyframes(
      "rotation",
      [[0, 0, 0, 0], [0.6, 4, 0, 6], [1.4, -3, 0, -8], [2.2, 3, 0, 5], [3.0, -2, 0, -3], [3.6, 1, 0, 2], [4.0, 0, 0, 0]],
      CR,
    ),
  ),
  [tailG.uuid]: bone(
    "tail",
    keyframes("rotation", [[0, 0, 0, 0], [1, 0, 10, 0], [2, 0, 0, 0], [3, 0, -10, 0], [4, 0, 0, 0]], CR),
  ),
  [earLG.uuid]: bone(
    "ear_l",
    keyframes("rotation", [[0, 0, 0, 0], [2.4, 0, 0, 0], [2.5, 0, 0, 22, "linear"], [2.6, 0, 0, 0], [4.0, 0, 0, 0]], CR),
  ),
};

// walk 0.8 s: diagonal leg pairs, body bob, head counter-bob, lagging tail/leaf.
const SWING = 32;
const walk = {
  [legFlG.uuid]: bone("leg_fl", keyframes("rotation", [[0, SWING, 0, 0], [0.4, -SWING, 0, 0], [0.8, SWING, 0, 0]], CR)),
  [legBrG.uuid]: bone("leg_br", keyframes("rotation", [[0, SWING, 0, 0], [0.4, -SWING, 0, 0], [0.8, SWING, 0, 0]], CR)),
  [legFrG.uuid]: bone("leg_fr", keyframes("rotation", [[0, -SWING, 0, 0], [0.4, SWING, 0, 0], [0.8, -SWING, 0, 0]], CR)),
  [legBlG.uuid]: bone("leg_bl", keyframes("rotation", [[0, -SWING, 0, 0], [0.4, SWING, 0, 0], [0.8, -SWING, 0, 0]], CR)),
  [bodyG.uuid]: bone(
    "body",
    keyframes("position", [[0, 0, 0, 0], [0.2, 0, -0.3, 0], [0.4, 0, 0, 0], [0.6, 0, -0.3, 0], [0.8, 0, 0, 0]], CR),
    keyframes("rotation", [[0, 0, 0, 0], [0.2, 0, 0, 3], [0.4, 0, 0, 0], [0.6, 0, 0, -3], [0.8, 0, 0, 0]], CR),
  ),
  [headG.uuid]: bone(
    "head",
    keyframes("rotation", [[0, 0, 0, 0], [0.2, 3, 0, 0], [0.4, 0, 0, 0], [0.6, -3, 0, 0], [0.8, 0, 0, 0]], CR),
  ),
  [tailG.uuid]: bone(
    "tail",
    keyframes("rotation", [[0, 0, -10, 0], [0.2, 0, 0, 0], [0.4, 0, 10, 0], [0.6, 0, 0, 0], [0.8, 0, -10, 0]], CR),
  ),
  [leafG.uuid]: bone(
    "leaf",
    keyframes("rotation", [[0, 0, 0, 0], [0.2, -6, 0, 0], [0.4, 0, 0, 0], [0.6, -6, 0, 0], [0.8, 0, 0, 0]], CR),
  ),
};

// work 1.6 s: rears up on the hind legs, pats with both front paws, leaf bounces.
const work = {
  [bodyG.uuid]: bone(
    "body",
    keyframes(
      "rotation",
      [[0, 0, 0, 0], [0.2, -34, 0, 0], [0.9, -34, 0, 0], [1.15, -6, 0, 0], [1.4, 0, 0, 0], [1.6, 0, 0, 0]],
      CR,
    ),
  ),
  [legFlG.uuid]: bone(
    "leg_fl",
    keyframes(
      "rotation",
      [[0, 0, 0, 0], [0.25, -70, 0, 0], [0.4, -20, 0, 0], [0.55, -70, 0, 0], [0.7, -20, 0, 0], [0.85, -70, 0, 0], [1.0, -20, 0, 0], [1.25, 0, 0, 0], [1.6, 0, 0, 0]],
      CR,
    ),
  ),
  [legFrG.uuid]: bone(
    "leg_fr",
    keyframes(
      "rotation",
      [[0, 0, 0, 0], [0.3, -20, 0, 0], [0.45, -70, 0, 0], [0.6, -20, 0, 0], [0.75, -70, 0, 0], [0.9, -20, 0, 0], [1.25, 0, 0, 0], [1.6, 0, 0, 0]],
      CR,
    ),
  ),
  [headG.uuid]: bone(
    "head",
    keyframes("rotation", [[0, 0, 0, 0], [0.2, -14, 0, 0], [0.9, -14, 0, 0], [1.25, 0, 0, 0], [1.6, 0, 0, 0]], CR),
  ),
  [leafG.uuid]: bone(
    "leaf",
    keyframes(
      "rotation",
      [[0, 0, 0, 0], [0.25, 0, 0, 12], [0.45, 0, 0, -14], [0.7, 0, 0, 10], [0.95, 0, 0, -10], [1.3, 0, 0, 4], [1.6, 0, 0, 0]],
      CR,
    ),
  ),
  [tailG.uuid]: bone(
    "tail",
    keyframes(
      "rotation",
      [[0, 0, 0, 0], [0.2, 20, 10, 0], [0.5, 20, -10, 0], [0.8, 20, 10, 0], [1.1, 20, -10, 0], [1.4, 0, 0, 0], [1.6, 0, 0, 0]],
      CR,
    ),
  ),
};

// eat 1.6 s: head dips to the held stick, front paws hold it, two nibbles.
const eat = {
  [headG.uuid]: bone(
    "head",
    keyframes(
      "rotation",
      [[0, 30, 0, 0], [0.3, 34, 0, 0], [0.5, 28, 0, 0], [0.7, 34, 0, 0], [0.9, 28, 0, 0], [1.2, 30, 0, 0], [1.6, 30, 0, 0]],
      CR,
    ),
  ),
  [legFlG.uuid]: bone(
    "leg_fl",
    keyframes("rotation", [[0, -95, 0, 0], [1.6, -95, 0, 0]], CR),
  ),
  [legFrG.uuid]: bone(
    "leg_fr",
    keyframes("rotation", [[0, -95, 0, 0], [1.6, -95, 0, 0]], CR),
  ),
  [tailG.uuid]: bone(
    "tail",
    keyframes("rotation", [[0, 0, -8, 0], [0.5, 0, 8, 0], [1.0, 0, -8, 0], [1.6, 0, -8, 0]], CR),
  ),
  [leafG.uuid]: bone(
    "leaf",
    keyframes("rotation", [[0, 0, 0, 0], [0.4, 3, 0, -5], [0.9, -3, 0, 5], [1.6, 0, 0, 0]], CR),
  ),
};

// hurt 0.4 s once: instant flinch, overshoot past rest, settle.
const hurt = {
  [headG.uuid]: bone("head", keyframes("rotation", [[0, -20, 0, 0], [0.25, 6, 0, 0], [0.4, 0, 0, 0]], CR)),
  [bodyG.uuid]: bone("body", keyframes("scale", [[0, 1.08, 0.9, 1.08], [0.25, 0.97, 1.03, 0.97], [0.4, 1, 1, 1]], CR)),
  [earLG.uuid]: bone("ear_l", keyframes("rotation", [[0, -28, 0, 0], [0.25, 6, 0, 0], [0.4, 0, 0, 0]], CR)),
  [earRG.uuid]: bone("ear_r", keyframes("rotation", [[0, -28, 0, 0], [0.25, 6, 0, 0], [0.4, 0, 0, 0]], CR)),
  [leafG.uuid]: bone("leaf", keyframes("rotation", [[0, -24, 0, 0], [0.25, 8, 0, 0], [0.4, 0, 0, 0]], CR)),
};

b.animation("idle", "loop", 4.0, idle);
b.animation("walk", "loop", 0.8, walk);
b.animation("work", "loop", 1.6, work);
b.animation("eat", "loop", 1.6, eat);
b.animation("hurt", "once", 0.4, hurt);

b.write(bbmodelOut, pngOut, "redpanda");
console.log(`wrote ${bbmodelOut}`);
console.log(`wrote ${pngOut}`);
