// Builds the vanilla-style "장꾸펫" (playful prankster ferret) pet model.
// Integer-pixel geometry, 1 texel per model pixel, front faces -Z, feet on y=0.
// Very long, thin, low body split into body_front / body_back bones so the back
// can arch, short legs, long tail. Sable mask; a cheeky grin and a winking eye
// live in the `wink_work` overlay (normal eyes sit in `eyes_rest`).
import path from "node:path";
import { fileURLToPath } from "node:url";
import { VBuilder, keyframes, bone } from "./lib/vanilla.mjs";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "../..");
const bbmodelOut = path.join(root, "dev/pipi-facilities/assets/pipifacilities/pets/ferret.bbmodel");
const pngOut = path.join(root, "dev/pipi-pets/out/ferret.png");

const b = new VBuilder({ width: 64, height: 64 });

// --- materials -------------------------------------------------------------
b.material("sable", [["#5A3E2A", 5], ["#6A4A34", 3], ["#4A3222", 2]]);
b.material("sableL", [["#6A4A34", 1]]);
b.material("cream", [["#E8D6B8", 5], ["#F2E4CA", 3], ["#DCC8A8", 2]]);
b.material("mask", [["#2E2018", 4], ["#241812", 2], ["#3A2A1E", 1]]);
b.material("dark", [["#1A120E", 1]]);
b.material("eye", [["#120C0A", 1]]);
b.material("white", [["#FFFFFF", 1]]);
b.material("pink", [["#C88A80", 2], ["#B07870", 1]]);

// --- legs (short, 2x3x2, sable with dark paws) -----------------------------
const legFaces = {
  north: (x, y, w, h) => (y >= h - 1 ? "dark" : "sable"),
  south: (x, y, w, h) => (y >= h - 1 ? "dark" : "sable"),
  east: (x, y, w, h) => (y >= h - 1 ? "dark" : "sable"),
  west: (x, y, w, h) => (y >= h - 1 ? "dark" : "sable"),
  up: "sable",
  down: "dark",
};
const makeLeg = (name, x1, z1) =>
  b.cube(name, [x1, 0, z1], [x1 + 2, 3, z1 + 2], { faces: legFaces });
const legFl = makeLeg("leg_fl_cube", -2, -5);
const legFr = makeLeg("leg_fr_cube", 0, -5);
const legBl = makeLeg("leg_bl_cube", -2, 2);
const legBr = makeLeg("leg_br_cube", 0, 2);

// --- body (very long, thin, low; cream belly, sable back) -------------------
const bodySide = (x, y, w, h) => (y === h - 1 ? "cream" : "sable");
const bodyFaces = {
  up: "sable",
  down: "cream",
  north: bodySide,
  south: bodySide,
  east: bodySide,
  west: bodySide,
};
const bodyFront = b.cube("body_front_cube", [-2, 3, -6], [2, 7, 0], { mat: "sable", faces: bodyFaces });
const bodyBack = b.cube("body_back_cube", [-2, 3, 0], [2, 7, 6], { mat: "sable", faces: bodyFaces });

// --- head (sable cap + dark sable mask band, cream muzzle) -----------------
const head = b.cube("head_cube", [-3, 5, -10], [3, 9, -6], {
  mat: "sable",
  legend: { o: "sable", m: "mask", c: "cream" },
  faces: {
    north: ["oooooo", "mmmmmm", "mmmmmm", "cccccc"],
    up: "sable",
    down: "cream",
    south: "sable",
    east: (x, y, w, h) => (y >= h - 2 ? "mask" : "sable"),
    west: (x, y, w, h) => (y >= h - 2 ? "mask" : "sable"),
  },
});
const snout = b.cube("snout_cube", [-1, 6, -11.5], [1, 8, -10], {
  mat: "cream",
  faces: { up: "sable", down: "cream", north: "cream", south: "cream", east: "cream", west: "cream" },
});
const nose = b.cube("nose_cube", [-0.5, 7.1, -12], [0.5, 7.9, -11.5], { mat: "dark" });

// --- ears (small rounded, pink inner front) --------------------------------
const makeEar = (name, x1) =>
  b.cube(name, [x1, 9, -9], [x1 + 2, 11, -8], {
    mat: "sable",
    legend: { p: "pink", o: "sable" },
    faces: {
      north: ["pp", "oo"],
      south: "sable",
      east: "sable",
      west: "sable",
      up: "sable",
      down: "sable",
    },
  });
const earL = makeEar("ear_l_cube", -3);
const earR = makeEar("ear_r_cube", 1);

// --- normal eyes (eyes_rest: only while not working) -----------------------
const eyeL = b.cube("eye_l_cube", [-3, 6, -10.3], [-1, 8, -10], {
  mat: "eye",
  legend: { k: "eye", w: "white" },
  faces: { north: ["wk", "kk"], south: "eye", east: "eye", west: "eye", up: "eye", down: "eye" },
});
const eyeR = b.cube("eye_r_cube", [1, 6, -10.3], [3, 8, -10], {
  mat: "eye",
  legend: { k: "eye", w: "white" },
  faces: { north: ["kw", "kk"], south: "eye", east: "eye", west: "eye", up: "eye", down: "eye" },
});

// --- prankster overlay (wink_work: only while working) ---------------------
const winkL = b.cube("wink_l_cube", [-3, 6.8, -10.4], [-1, 7.5, -10.1], { mat: "dark" });
const openEyeR = b.cube("open_eye_r_cube", [1, 6, -10.3], [3, 8, -10], {
  mat: "eye",
  legend: { k: "eye", w: "white" },
  faces: { north: ["kw", "kk"], south: "eye", east: "eye", west: "eye", up: "eye", down: "eye" },
});
const grin = b.cube("grin_cube", [-1.5, 5, -10.4], [1.5, 6, -10.1], {
  mat: "dark",
  legend: { k: "dark" },
  faces: { north: ["kkk"], south: "dark", east: "dark", west: "dark", up: "dark", down: "dark" },
});
const grinL = b.cube("grin_l_cube", [-2, 5.6, -10.4], [-1.4, 6.3, -10.1], { mat: "dark" });
const grinR = b.cube("grin_r_cube", [1.4, 5.6, -10.4], [2, 6.3, -10.1], { mat: "dark" });

// --- tail (long, tapering, rises at rest) ----------------------------------
const tail1 = b.cube("tail_base_cube", [-1, 3, 6], [1, 5, 10], {
  mat: "sable",
  faces: { up: "sable", down: "cream", north: "sable", south: "sable", east: "sable", west: "sable" },
});
const tail2 = b.cube("tail_tip_cube", [-1, 4, 10], [1, 6, 14], {
  mat: "sable",
  legend: { o: "sable", c: "cream" },
  faces: {
    up: "sable",
    down: "cream",
    north: "sable",
    south: ["cc", "cc"],
    east: "sable",
    west: "sable",
  },
});

// --- bones -----------------------------------------------------------------
const earLG = b.group("ear_l", [-2, 9, -8.5], [earL]);
const earRG = b.group("ear_r", [2, 9, -8.5], [earR]);
const snoutG = b.group("snout", [0, 7, -10], [snout, nose]);
const eyesRestG = b.group("eyes_rest", [0, 7, -10], [eyeL, eyeR]);
const winkG = b.group("wink_work", [0, 7, -10], [winkL, openEyeR, grin, grinL, grinR]);
const headG = b.group("head", [0, 6, -6], [head, snoutG, earLG, earRG, eyesRestG, winkG]);
const tailG = b.group("tail", [0, 4, 6], [tail1, tail2], { rotation: [-22, 0, 0] });
const legFlG = b.group("leg_fl", [-1, 3, -4], [legFl]);
const legFrG = b.group("leg_fr", [1, 3, -4], [legFr]);
const legBlG = b.group("leg_bl", [-1, 3, 3], [legBl]);
const legBrG = b.group("leg_br", [1, 3, 3], [legBr]);
const bodyFrontG = b.group("body_front", [0, 5, -3], [bodyFront, headG, legFlG, legFrG]);
const bodyBackG = b.group("body_back", [0, 5, 3], [bodyBack, tailG, legBlG, legBrG, bodyFrontG]);
const rootG = b.group("root", [0, 0, 0], [bodyBackG]);
b.outliner.push(rootG);

// --- animations ------------------------------------------------------------
// Keyframe rotation is applied by PetModels as (-x,-y,+z); rest bone rotation
// is applied directly. Positive keyframe X on the head nods it down. Curves use
// catmullrom; ears/tail lag the body for follow-through; loops share first/last.
const CR = "catmullrom";

// idle 4 s: breathing, then a sudden head swivel + sniff, tail sway, ear twitch.
const idle = {
  [bodyBackG.uuid]: bone(
    "body_back",
    keyframes("scale", [[0, 1, 1, 1], [1, 1, 1.03, 1], [2, 1, 1, 1], [3, 1, 1.03, 1], [4, 1, 1, 1]], CR),
  ),
  [headG.uuid]: bone(
    "head",
    keyframes(
      "rotation",
      [
        [0.0, 0, 0, 0],
        [1.4, 0, 0, 0],
        [1.5, 0, -34, 0, "linear"],
        [1.8, 0, -34, 0],
        [1.9, 0, -30, 0],
        [2.0, 0, -34, 0],
        [2.1, 0, -30, 0],
        [2.2, 0, -34, 0],
        [2.6, 0, -34, 0],
        [2.9, 0, 0, 0],
        [3.4, 0, 12, 0],
        [3.8, 0, 0, 0],
        [4.0, 0, 0, 0],
      ],
      CR,
    ),
  ),
  [snoutG.uuid]: bone(
    "snout",
    keyframes(
      "position",
      [
        [0, 0, 0, 0],
        [1.8, 0, 0, 0],
        [1.85, 0, 0, -0.25, "linear"],
        [1.9, 0, 0, 0],
        [1.95, 0, 0, -0.25, "linear"],
        [2.0, 0, 0, 0],
        [2.05, 0, 0, -0.25, "linear"],
        [2.1, 0, 0, 0],
        [4.0, 0, 0, 0],
      ],
      CR,
    ),
  ),
  [tailG.uuid]: bone("tail", keyframes("rotation", [[0, -22, 8, 0], [1.3, -22, -8, 0], [2.6, -22, 8, 0], [3.4, -22, -8, 0], [4, -22, 8, 0]], CR)),
  [earRG.uuid]: bone("ear_r", keyframes("rotation", [[0, 0, 0, 0], [2.6, 0, 0, 0], [2.7, 0, 0, 22, "linear"], [2.85, 0, 0, 0], [4, 0, 0, 0]], CR)),
  [earLG.uuid]: bone("ear_l", keyframes("rotation", [[0, 0, 0, 0], [3.0, 0, 0, 0], [3.1, 0, 0, -22, "linear"], [3.25, 0, 0, 0], [4, 0, 0, 0]], CR)),
};

// walk 0.8 s: bouncy weasel war dance — the back arches up on each bounce, the
// legs scurry underneath, the tail whips behind and the head counter-bobs.
const walk = {
  [rootG.uuid]: bone(
    "root",
    keyframes("position", [[0, 0, 0, 0], [0.2, 0, 1.4, 0], [0.4, 0, 0, 0], [0.6, 0, 1.4, 0], [0.8, 0, 0, 0]], CR),
    keyframes("rotation", [[0, 0, 0, 0], [0.2, 0, 0, 5], [0.4, 0, 0, 0], [0.6, 0, 0, -5], [0.8, 0, 0, 0]], CR),
  ),
  [bodyFrontG.uuid]: bone(
    "body_front",
    keyframes("rotation", [[0, 0, 0, 0], [0.2, 16, 0, 0], [0.4, 0, 0, 0], [0.6, 16, 0, 0], [0.8, 0, 0, 0]], CR),
  ),
  [bodyBackG.uuid]: bone(
    "body_back",
    keyframes("rotation", [[0, 0, 0, 0], [0.2, -14, 0, 0], [0.4, 0, 0, 0], [0.6, -14, 0, 0], [0.8, 0, 0, 0]], CR),
  ),
  [legFlG.uuid]: bone("leg_fl", keyframes("rotation", [[0, 30, 0, 0], [0.2, -30, 0, 0], [0.4, 30, 0, 0], [0.6, -30, 0, 0], [0.8, 30, 0, 0]], CR)),
  [legBrG.uuid]: bone("leg_br", keyframes("rotation", [[0, 30, 0, 0], [0.2, -30, 0, 0], [0.4, 30, 0, 0], [0.6, -30, 0, 0], [0.8, 30, 0, 0]], CR)),
  [legFrG.uuid]: bone("leg_fr", keyframes("rotation", [[0, -30, 0, 0], [0.2, 30, 0, 0], [0.4, -30, 0, 0], [0.6, 30, 0, 0], [0.8, -30, 0, 0]], CR)),
  [legBlG.uuid]: bone("leg_bl", keyframes("rotation", [[0, -30, 0, 0], [0.2, 30, 0, 0], [0.4, -30, 0, 0], [0.6, 30, 0, 0], [0.8, -30, 0, 0]], CR)),
  [headG.uuid]: bone(
    "head",
    keyframes("position", [[0, 0, 0, 0], [0.2, 0, -0.4, 0], [0.4, 0, 0, 0], [0.6, 0, -0.4, 0], [0.8, 0, 0, 0]], CR),
    keyframes("rotation", [[0, 0, 0, 0], [0.2, 6, 0, 0], [0.4, 0, 0, 0], [0.6, 6, 0, 0], [0.8, 0, 0, 0]], CR),
  ),
  [tailG.uuid]: bone(
    "tail",
    keyframes("rotation", [[0, -30, 16, 0], [0.2, -30, -16, 0], [0.4, -30, 16, 0], [0.6, -30, -16, 0], [0.8, -30, 16, 0]], CR),
  ),
  [earLG.uuid]: bone("ear_l", keyframes("rotation", [[0, 0, 0, 0], [0.2, -12, 0, 0], [0.4, 0, 0, 0], [0.6, -12, 0, 0], [0.8, 0, 0, 0]], CR)),
  [earRG.uuid]: bone("ear_r", keyframes("rotation", [[0, 0, 0, 0], [0.2, -12, 0, 0], [0.4, 0, 0, 0], [0.6, -12, 0, 0], [0.8, 0, 0, 0]], CR)),
};

// work 1.6 s: hops sideways then spins a full turn, tail whipping, head cocked.
const work = {
  [rootG.uuid]: bone(
    "root",
    keyframes(
      "position",
      [
        [0, 0, 0, 0],
        [0.2, -2.4, 1.6, 0],
        [0.4, -2.8, 0, 0],
        [1.2, -2.8, 0, 0],
        [1.45, 0, 1.2, 0],
        [1.6, 0, 0, 0],
      ],
      CR,
    ),
    keyframes(
      "rotation",
      [
        [0, 0, 0, 0],
        [0.35, 0, 0, 0],
        [1.25, 0, 360, 0],
        [1.6, 0, 360, 0],
      ],
      CR,
    ),
  ),
  [bodyFrontG.uuid]: bone(
    "body_front",
    keyframes(
      "rotation",
      [
        [0, 0, 0, 0],
        [0.2, -18, 0, 0],
        [0.4, -8, 0, 0],
        [1.25, -8, 0, 0],
        [1.45, -18, 0, 0],
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
        [0.2, -10, 14, 0],
        [0.4, -6, 30, 0],
        [1.25, -6, 30, 0],
        [1.45, -8, 0, 0],
        [1.6, 0, 0, 0],
      ],
      CR,
    ),
  ),
  [legFlG.uuid]: bone("leg_fl", keyframes("rotation", [[0, 0, 0, 0], [0.2, 34, 0, 0], [0.4, 0, 0, 0], [1.45, 0, 0, 0], [1.6, 0, 0, 0]], CR)),
  [legFrG.uuid]: bone("leg_fr", keyframes("rotation", [[0, 0, 0, 0], [0.2, 34, 0, 0], [0.4, 0, 0, 0], [1.45, 0, 0, 0], [1.6, 0, 0, 0]], CR)),
  [legBlG.uuid]: bone("leg_bl", keyframes("rotation", [[0, 0, 0, 0], [0.2, -18, 0, 0], [0.4, 0, 0, 0], [1.45, 0, 0, 0], [1.6, 0, 0, 0]], CR)),
  [legBrG.uuid]: bone("leg_br", keyframes("rotation", [[0, 0, 0, 0], [0.2, -18, 0, 0], [0.4, 0, 0, 0], [1.45, 0, 0, 0], [1.6, 0, 0, 0]], CR)),
  [tailG.uuid]: bone(
    "tail",
    keyframes(
      "rotation",
      [
        [0, -30, 14, 0],
        [0.2, -34, -18, 0],
        [0.4, -30, 18, 0],
        [0.8, -34, -18, 0],
        [1.2, -30, 18, 0],
        [1.45, -30, -14, 0],
        [1.6, -30, 14, 0],
      ],
      CR,
    ),
  ),
  [earLG.uuid]: bone("ear_l", keyframes("rotation", [[0, 0, 0, 0], [0.4, 0, 0, 0], [0.5, 0, 0, 24, "linear"], [0.7, 0, 0, 0], [1.6, 0, 0, 0]], CR)),
  [earRG.uuid]: bone("ear_r", keyframes("rotation", [[0, 0, 0, 0], [0.4, 0, 0, 0], [0.55, 0, 0, -24, "linear"], [0.75, 0, 0, 0], [1.6, 0, 0, 0]], CR)),
};

// eat 1.0 s: head held down, three quick nibbles, body breathes, tail sways.
const eat = {
  [headG.uuid]: bone(
    "head",
    keyframes(
      "rotation",
      [
        [0.0, 34, 0, 0],
        [0.1, 40, 0, 0],
        [0.2, 34, 0, 0],
        [0.3, 40, 0, 0],
        [0.4, 34, 0, 0],
        [0.5, 40, 0, 0],
        [0.6, 34, 0, 0],
        [0.7, 40, 0, 0],
        [0.8, 34, 0, 0],
        [1.0, 34, 0, 0],
      ],
      CR,
    ),
  ),
  [snoutG.uuid]: bone(
    "snout",
    keyframes(
      "rotation",
      [
        [0.0, 0, 0, 0],
        [0.1, 12, 0, 0],
        [0.2, 0, 0, 0],
        [0.3, 12, 0, 0],
        [0.4, 0, 0, 0],
        [0.5, 12, 0, 0],
        [0.6, 0, 0, 0],
        [0.7, 12, 0, 0],
        [0.8, 0, 0, 0],
        [1.0, 0, 0, 0],
      ],
      CR,
    ),
  ),
  [bodyBackG.uuid]: bone("body_back", keyframes("scale", [[0, 1, 1, 1], [0.5, 1, 1.02, 1], [1, 1, 1, 1]], CR)),
  [tailG.uuid]: bone("tail", keyframes("rotation", [[0, -22, 8, 0], [0.5, -22, -8, 0], [1, -22, 8, 0]], CR)),
  [earLG.uuid]: bone("ear_l", keyframes("rotation", [[0, 0, 0, 0], [0.25, 8, 0, 0], [0.5, 0, 0, 0], [0.75, 8, 0, 0], [1, 0, 0, 0]], CR)),
  [earRG.uuid]: bone("ear_r", keyframes("rotation", [[0, 0, 0, 0], [0.25, 8, 0, 0], [0.5, 0, 0, 0], [0.75, 8, 0, 0], [1, 0, 0, 0]], CR)),
};

// hurt 0.35 s once: instant flinch back with overshoot, then settle.
const hurt = {
  [rootG.uuid]: bone("root", keyframes("position", [[0, 0, 0, 0.4], [0.15, 0, 0, 0], [0.35, 0, 0, 0]], CR)),
  [bodyBackG.uuid]: bone("body_back", keyframes("scale", [[0, 1.12, 0.85, 1.12], [0.15, 0.97, 1.04, 0.97], [0.35, 1, 1, 1]], CR)),
  [headG.uuid]: bone("head", keyframes("rotation", [[0, -24, 0, 0], [0.15, 6, 0, 0], [0.35, 0, 0, 0]], CR)),
  [tailG.uuid]: bone("tail", keyframes("rotation", [[0, -52, 0, 0], [0.15, -12, 0, 0], [0.35, -22, 0, 0]], CR)),
  [earLG.uuid]: bone("ear_l", keyframes("rotation", [[0, -34, 0, 0], [0.15, 8, 0, 0], [0.35, 0, 0, 0]], CR)),
  [earRG.uuid]: bone("ear_r", keyframes("rotation", [[0, -34, 0, 0], [0.15, 8, 0, 0], [0.35, 0, 0, 0]], CR)),
};

b.animation("idle", "loop", 4.0, idle);
b.animation("walk", "loop", 0.8, walk);
b.animation("work", "loop", 1.6, work);
b.animation("eat", "loop", 1.0, eat);
b.animation("hurt", "once", 0.35, hurt);

b.write(bbmodelOut, pngOut, "ferret");
console.log(`wrote ${bbmodelOut}`);
console.log(`wrote ${pngOut}`);
