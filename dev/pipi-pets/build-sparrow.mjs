// Builds the vanilla-style "짱새" (spiky-crested sparrow) pet model.
// Integer-pixel geometry, 1 texel per model pixel, front faces -Z.
import path from "node:path";
import { fileURLToPath } from "node:url";
import { VBuilder, keyframes, bone } from "./lib/vanilla.mjs";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "../..");
const bbmodelOut = path.join(root, "dev/pipi-facilities/assets/pipifacilities/pets/sparrow.bbmodel");
const pngOut = path.join(root, "dev/pipi-pets/out/sparrow.png");

const b = new VBuilder({ width: 64, height: 64 });

// --- materials -------------------------------------------------------------
b.material("back", [["#7A5236", 5], ["#8E6242", 3], ["#6A4630", 2]]);
b.material("dark", [["#3A2A20", 4], ["#2A1E16", 2], ["#4A362A", 2]]);
b.material("cream", [["#EAD9B8", 5], ["#E0CBA4", 3], ["#F4E6CC", 2]]);
b.material("cap", [["#8E5A32", 5], ["#7A4A26", 3], ["#A06A3E", 2]]);
b.material("beak", [["#E0A030", 5], ["#C88820", 3], ["#F0B850", 2]]);
b.material("leg", [["#C89040", 4], ["#A87428", 2], ["#DCA85C", 1]]);
b.material("eye", [["#1E1512", 1]]);

// --- body (plump, tilted nose-up via the body bone) ------------------------
const body = b.cube("body_cube", [-3, 2, -3], [3, 8, 4], {
  mat: "back",
  legend: { b: "back", c: "cream", d: "dark" },
  faces: {
    north: ["bbbbbb", "bbdbbb", "bccccb", "cccccc", "cccccc", "cccccc"],
    south: (x, y, w, h) => (y >= h - 2 ? "cream" : "back"),
    east: (x, y, w, h) => (y < 2 ? "back" : "cream"),
    west: (x, y, w, h) => (y < 2 ? "back" : "cream"),
    up: "back",
    down: "dark",
  },
});

// --- head (brown, chestnut cap, dark cheek dots, 1px eyes) ------------------
const head = b.cube("head_cube", [-2, 7, -5], [2, 11, -1], {
  mat: "back",
  legend: { b: "back", k: "eye", c: "cream", d: "dark" },
  faces: {
    north: ["bbbb", "kbbk", "bccb", "cddc"],
    south: (x, y, w, h) => (y === 0 ? "cap" : "back"),
    east: (x, y, w, h) => (y === 0 ? "cap" : y >= h - 1 ? "cream" : "back"),
    west: (x, y, w, h) => (y === 0 ? "cap" : y >= h - 1 ? "cream" : "back"),
    up: "cap",
    down: "cream",
  },
});

// --- stubby short beak ------------------------------------------------------
const beak = b.cube("beak_cube", [-1, 8, -7], [1, 9, -5], {
  mat: "beak",
  faces: { down: "#B87A1E" },
});

// --- wings (flat side panels) ----------------------------------------------
const wingL = b.cube("wing_l_cube", [-4, 4, -2], [-3, 6, 1], {
  mat: "back",
  legend: { b: "back", d: "dark" },
  faces: { west: ["bbb", "dbb"], east: "back", down: "dark" },
});
const wingR = b.cube("wing_r_cube", [3, 4, -2], [4, 6, 1], {
  mat: "back",
  legend: { b: "back", d: "dark" },
  faces: { east: ["bbb", "dbb"], west: "back", down: "dark" },
});

// --- short fan tail ---------------------------------------------------------
const tail = b.cube("tail_cube", [-2, 4, 4], [2, 5, 6], {
  mat: "back",
  legend: { b: "back", d: "dark" },
  faces: {
    up: ["bdbb", "bbdb"],
    down: ["dbbd", "bddb"],
    north: "dark",
    south: "back",
  },
});

// --- legs (thin, with a forward toe) ---------------------------------------
const shinL = b.cube("shin_l_cube", [-2, 0, -1], [-1, 3, 0], { mat: "leg", faces: { down: "dark" } });
const footL = b.cube("foot_l_cube", [-2, 0, -2], [-1, 1, 0], { mat: "leg", faces: { down: "dark" } });
const shinR = b.cube("shin_r_cube", [1, 0, -1], [2, 3, 0], { mat: "leg", faces: { down: "dark" } });
const footR = b.cube("foot_r_cube", [1, 0, -2], [2, 1, 0], { mat: "leg", faces: { down: "dark" } });

// --- messy crest: 5 spikes of different heights ----------------------------
const mkSpike = (name, x1, y1, z1, x2, y2, z2) =>
  b.cube(name, [x1, y1, z1], [x2, y2, z2], { mat: "cap", faces: { down: "dark" } });
const spikes = [
  mkSpike("spike_a_cube", -2, 11, -4, -1, 13, -3),
  mkSpike("spike_b_cube", -1, 11, -3, 0, 14, -2),
  mkSpike("spike_c_cube", 0, 11, -2, 1, 13, -1),
  mkSpike("spike_d_cube", 1, 11, -1, 2, 14, 0),
  mkSpike("spike_e_cube", -1, 11, 0, 0, 12, 1),
];

// --- bones -----------------------------------------------------------------
const crestG = b.group("crest", [0, 11, -2], spikes);
const headG = b.group("head", [0, 8, -2], [head, beak, crestG]);
const wingLG = b.group("wing_l", [-3.5, 5, -0.5], [wingL]);
const wingRG = b.group("wing_r", [3.5, 5, -0.5], [wingR]);
const tailG = b.group("tail", [0, 4.5, 4], [tail], { rotation: [25, 0, 0] });
const bodyG = b.group("body", [0, 3, -0.5], [body, wingLG, wingRG, tailG, headG], { rotation: [15, 0, 0] });
const legLG = b.group("leg_l", [-1.5, 3, -0.5], [shinL, footL]);
const legRG = b.group("leg_r", [1.5, 3, -0.5], [shinR, footR]);
const rootG = b.group("root", [0, 0, 0], [bodyG, legLG, legRG]);
b.outliner.push(rootG);

// --- animations ------------------------------------------------------------
// Keyframe rotation is applied by PetModels as (-x,-y,+z); rest bone rotation
// is applied directly (positive X on the body = nose-up). Curves use catmullrom
// for organic ease; crest/wings lag for follow-through; every loop's first and
// last keys match. Whole-body motion lives on the `root` bone.
const CR = "catmullrom";

// idle 3.5 s: breathing, slow head tilts, crest bristles, gentle tail sway.
const idle = {
  [bodyG.uuid]: bone(
    "body",
    keyframes("scale", [[0, 1, 1, 1], [1, 1, 1.03, 1], [2, 1, 1, 1], [3, 1, 1.03, 1], [3.5, 1, 1, 1]], CR),
  ),
  [headG.uuid]: bone(
    "head",
    keyframes(
      "rotation",
      [
        [0.0, 0, 0, 0],
        [0.8, 0, 14, 4],
        [1.2, 0, 14, 6],
        [1.8, 3, 0, 1],
        [2.6, 0, -14, -5],
        [3.1, 0, -14, -3],
        [3.5, 0, 0, 0],
      ],
      CR,
    ),
  ),
  [crestG.uuid]: bone(
    "crest",
    keyframes(
      "rotation",
      [[0, 0, 0, 0], [1.5, 0, 0, 0], [1.6, -16, 0, 0, "linear"], [1.8, 9, 0, 0, "linear"], [2.0, -4, 0, 0], [2.2, 0, 0, 0], [3.5, 0, 0, 0]],
      CR,
    ),
  ),
  [tailG.uuid]: bone("tail", keyframes("rotation", [[0, 0, 0, 0], [1.2, 0, 8, 0], [2.4, 0, -8, 0], [3.5, 0, 0, 0]], CR)),
  [wingLG.uuid]: bone("wing_l", keyframes("rotation", [[0, 0, 0, 0], [1.0, 0, 0, -6], [2.0, 0, 0, 4], [3.5, 0, 0, 0]], CR)),
  [wingRG.uuid]: bone("wing_r", keyframes("rotation", [[0, 0, 0, 0], [1.0, 0, 0, 6], [2.0, 0, 0, -4], [3.5, 0, 0, 0]], CR)),
};

// walk 0.6 s: little two-footed hops, body arcs up, wings flick for balance.
const walk = {
  [rootG.uuid]: bone("root", keyframes("position", [[0, 0, 0, 0], [0.15, 0, 2.6, 0], [0.3, 0, 0, 0], [0.45, 0, 2.6, 0], [0.6, 0, 0, 0]], CR)),
  [legLG.uuid]: bone("leg_l", keyframes("rotation", [[0, 0, 0, 0], [0.1, -30, 0, 0], [0.25, 15, 0, 0], [0.3, 0, 0, 0], [0.4, -30, 0, 0], [0.55, 15, 0, 0], [0.6, 0, 0, 0]], CR)),
  [legRG.uuid]: bone("leg_r", keyframes("rotation", [[0, 0, 0, 0], [0.1, -30, 0, 0], [0.25, 15, 0, 0], [0.3, 0, 0, 0], [0.4, -30, 0, 0], [0.55, 15, 0, 0], [0.6, 0, 0, 0]], CR)),
  [bodyG.uuid]: bone(
    "body",
    keyframes("scale", [[0, 1, 0.9, 1], [0.15, 1, 1.06, 1], [0.3, 1, 0.9, 1], [0.45, 1, 1.06, 1], [0.6, 1, 0.9, 1]], CR),
    keyframes("rotation", [[0, 4, 0, 0], [0.15, 12, 0, 0], [0.3, 4, 0, 0], [0.45, 12, 0, 0], [0.6, 4, 0, 0]], CR),
  ),
  [headG.uuid]: bone("head", keyframes("rotation", [[0, 0, 0, 0], [0.15, -6, 0, 0], [0.3, 0, 0, 0], [0.45, -6, 0, 0], [0.6, 0, 0, 0]], CR)),
  [tailG.uuid]: bone("tail", keyframes("rotation", [[0, 0, 0, 0], [0.15, 0, 0, 10], [0.3, 0, 0, 0], [0.45, 0, 0, -10], [0.6, 0, 0, 0]], CR)),
  [wingLG.uuid]: bone("wing_l", keyframes("rotation", [[0, 0, 0, 0], [0.15, 0, 0, -14], [0.3, 0, 0, 0], [0.45, 0, 0, -14], [0.6, 0, 0, 0]], CR)),
  [wingRG.uuid]: bone("wing_r", keyframes("rotation", [[0, 0, 0, 0], [0.15, 0, 0, 14], [0.3, 0, 0, 0], [0.45, 0, 0, 14], [0.6, 0, 0, 0]], CR)),
};

// work 1.0 s: hovers 3 px up on fast wing-flaps while pecking at the ground.
const work = {
  [rootG.uuid]: bone("root", keyframes("position", [[0, 0, 3, 0], [0.25, 0, 3.6, 0], [0.5, 0, 3, 0], [0.75, 0, 3.6, 0], [1.0, 0, 3, 0]], CR)),
  [wingLG.uuid]: bone(
    "wing_l",
    keyframes(
      "rotation",
      [
        [0, 0, 0, 40],
        [0.125, 0, 0, -45],
        [0.25, 0, 0, 40],
        [0.375, 0, 0, -45],
        [0.5, 0, 0, 40],
        [0.625, 0, 0, -45],
        [0.75, 0, 0, 40],
        [0.875, 0, 0, -45],
        [1.0, 0, 0, 40],
      ],
      CR,
    ),
  ),
  [wingRG.uuid]: bone(
    "wing_r",
    keyframes(
      "rotation",
      [
        [0, 0, 0, -40],
        [0.125, 0, 0, 45],
        [0.25, 0, 0, -40],
        [0.375, 0, 0, 45],
        [0.5, 0, 0, -40],
        [0.625, 0, 0, 45],
        [0.75, 0, 0, -40],
        [0.875, 0, 0, 45],
        [1.0, 0, 0, -40],
      ],
      CR,
    ),
  ),
  [headG.uuid]: bone(
    "head",
    keyframes(
      "rotation",
      [[0, 0, 0, 0], [0.15, 38, 0, 0], [0.3, 5, 0, 0], [0.5, 0, 0, 0], [0.65, 38, 0, 0], [0.8, 5, 0, 0], [1.0, 0, 0, 0]],
      CR,
    ),
  ),
  [crestG.uuid]: bone(
    "crest",
    keyframes("rotation", [[0, 0, 0, 0], [0.15, -10, 0, 0], [0.3, 6, 0, 0], [0.5, 0, 0, 0], [0.65, -10, 0, 0], [0.8, 6, 0, 0], [1.0, 0, 0, 0]], CR),
  ),
  [tailG.uuid]: bone("tail", keyframes("rotation", [[0, 0, 0, 0], [0.25, 0, 10, 0], [0.5, 0, 0, 0], [0.75, 0, -10, 0], [1.0, 0, 0, 0]], CR)),
  [legLG.uuid]: bone("leg_l", keyframes("rotation", [[0, -20, 0, 0], [0.5, -20, 0, 0], [1.0, -20, 0, 0]], CR)),
  [legRG.uuid]: bone("leg_r", keyframes("rotation", [[0, -20, 0, 0], [0.5, -20, 0, 0], [1.0, -20, 0, 0]], CR)),
};

// eat 0.8 s: head down, small nibble pecks, tail and crest wag.
const eat = {
  [rootG.uuid]: bone("root", keyframes("position", [[0, 0, 0, 0], [0.3, 0, -0.6, 0], [0.6, 0, -0.6, 0], [0.8, 0, 0, 0]], CR)),
  [headG.uuid]: bone(
    "head",
    keyframes("rotation", [[0, 32, 0, 0], [0.2, 40, 0, 0], [0.3, 32, 0, 0], [0.5, 40, 0, 0], [0.6, 32, 0, 0], [0.8, 32, 0, 0]], CR),
  ),
  [tailG.uuid]: bone("tail", keyframes("rotation", [[0, 0, 0, 0], [0.2, 0, 6, 0], [0.4, 0, -6, 0], [0.6, 0, 6, 0], [0.8, 0, 0, 0]], CR)),
  [crestG.uuid]: bone("crest", keyframes("rotation", [[0, 0, 0, 0], [0.2, -8, 0, 0], [0.4, 0, 0, 0], [0.6, -8, 0, 0], [0.8, 0, 0, 0]], CR)),
  [wingLG.uuid]: bone("wing_l", keyframes("rotation", [[0, 0, 0, 6], [0.4, 0, 0, 0], [0.8, 0, 0, 6]], CR)),
  [wingRG.uuid]: bone("wing_r", keyframes("rotation", [[0, 0, 0, -6], [0.4, 0, 0, 0], [0.8, 0, 0, -6]], CR)),
};

// hurt 0.35 s once: instant flinch, overshoot past rest, settle.
const hurt = {
  [headG.uuid]: bone("head", keyframes("rotation", [[0, -18, 0, 0], [0.2, 8, 0, 0], [0.35, 0, 0, 0]], CR)),
  [bodyG.uuid]: bone("body", keyframes("scale", [[0, 1.1, 0.88, 1.1], [0.2, 0.97, 1.04, 0.97], [0.35, 1, 1, 1]], CR)),
  [crestG.uuid]: bone("crest", keyframes("rotation", [[0, -34, 0, 0], [0.2, 12, 0, 0], [0.35, 0, 0, 0]], CR)),
  [wingLG.uuid]: bone("wing_l", keyframes("rotation", [[0, 0, 0, -40], [0.2, 0, 0, 8], [0.35, 0, 0, 0]], CR)),
  [wingRG.uuid]: bone("wing_r", keyframes("rotation", [[0, 0, 0, 40], [0.2, 0, 0, -8], [0.35, 0, 0, 0]], CR)),
  [legLG.uuid]: bone("leg_l", keyframes("rotation", [[0, 20, 0, 0], [0.2, -5, 0, 0], [0.35, 0, 0, 0]], CR)),
  [legRG.uuid]: bone("leg_r", keyframes("rotation", [[0, 20, 0, 0], [0.2, -5, 0, 0], [0.35, 0, 0, 0]], CR)),
};

b.animation("idle", "loop", 3.5, idle);
b.animation("walk", "loop", 0.6, walk);
b.animation("work", "loop", 1.0, work);
b.animation("eat", "loop", 0.8, eat);
b.animation("hurt", "once", 0.35, hurt);

b.write(bbmodelOut, pngOut, "sparrow");
console.log(`wrote ${bbmodelOut}`);
console.log(`wrote ${pngOut}`);
