// Builds the vanilla-style "블벳" (vampire bat) pet model.
// Integer-pixel geometry, 1 texel per model pixel, front faces -Z, feet on y=0.
// Vanilla-bat-like: a small round furry body hanging under the wings, tiny
// clawed feet, short pointed ears and a short snout, membrane wings with
// finger-bone ribs (bones wing_l/wing_r, each with an outer wing child for a
// two-joint fold). It hovers in the rest pose too (all geometry sits at least
// 6 px up); animations only bob it around that height. Ordinary-looking
// vampire bat: short 1x2 fangs, red eyes (eyes_glow) and charcoal fur, dark
// maroon wing membrane.
import path from "node:path";
import { fileURLToPath } from "node:url";
import { VBuilder, keyframes, bone } from "./lib/vanilla.mjs";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "../..");
const bbmodelOut = path.join(root, "dev/pipi-facilities/assets/pipifacilities/pets/vampbat.bbmodel");
const pngOut = path.join(root, "dev/pipi-pets/out/vampbat.png");

const b = new VBuilder({ width: 64, height: 64 });

// --- materials -------------------------------------------------------------
b.material("fur", [["#2E2426", 5], ["#3A2E30", 3], ["#241C1E", 2]]);
b.material("furlt", [["#4A3C3E", 3], ["#584749", 2], ["#3C3032", 1]]);
b.material("dark", [["#171214", 3], ["#1E181A", 1]]);
b.material("membrane", [["#4A3238", 5], ["#5A3C44", 3], ["#3A262C", 2]]);
b.material("memin", [["#6A4650", 3], ["#7A525C", 2]]);
b.material("bone", [["#6A5A58", 3], ["#84746E", 2], ["#564A48", 1]]);
b.material("fang", [["#F4F2EA", 4], ["#E4E0D4", 2], ["#FFFFFF", 1]]);
b.material("eye", [["#FF2A2A", 3], ["#D82020", 2], ["#FF6A6A", 1]]);
b.material("glint", [["#FFFFFF", 4], ["#EAF4FF", 2]]);

// --- body (small round furry ball hanging under the wings) -----------------
const body = b.cube("body_cube", [-2, 7, -3], [2, 12, 3], {
  mat: "fur",
  faces: { north: "furlt", down: "dark", south: "fur", east: "fur", west: "fur", up: "fur" },
});
const bodyTop = b.cube("body_top_cube", [-1.5, 12, -2.5], [1.5, 13, 2.5], { mat: "fur", faces: { down: "dark" } });
const bodyBottom = b.cube("body_bottom_cube", [-1.5, 6.5, -2.5], [1.5, 7, 2.5], { mat: "fur", faces: { down: "dark" } });
const bodyFront = b.cube("body_front_cube", [-1.5, 8, -4], [1.5, 11, -3], { mat: "fur", faces: { north: "furlt" } });
const bodyBack = b.cube("body_back_cube", [-1.5, 8, 3], [1.5, 11, 4], { mat: "fur" });

// --- head (short blunt face) with a short snout ----------------------------
const head = b.cube("head_cube", [-2, 11, -6], [2, 15, -2], {
  mat: "fur",
  faces: { north: "fur", down: "furlt", south: "fur", east: "fur", west: "fur", up: "fur" },
});
const snout = b.cube("snout_cube", [-1, 11, -7], [1, 13, -6], {
  mat: "furlt",
  legend: { o: "furlt", n: "dark", m: "dark" },
  faces: { north: ["on", "mm"], down: "dark", south: "furlt", east: "furlt", west: "furlt", up: "fur" },
});

// --- pointed ears (about 3 px tall, pinkish inner membrane) ----------------
const makeEar = (name, x1) => {
  const base = b.cube(`${name}_base`, [x1, 15, -5], [x1 + 2, 17, -3], {
    mat: "fur",
    faces: { north: "memin", south: "fur", east: "fur", west: "fur", up: "dark", down: "fur" },
  });
  const tip = b.cube(`${name}_tip`, [x1 + 0.5, 17, -4], [x1 + 1.5, 18, -3], {
    mat: "fur",
    faces: { north: "memin", south: "fur", east: "fur", west: "fur", up: "dark", down: "fur" },
  });
  return [base, tip];
};
const [earLBase, earLTip] = makeEar("ear_l_cube", -3);
const [earRBase, earRTip] = makeEar("ear_r_cube", 1);

// --- short fangs (1x2 px) just below the upper lip -------------------------
const fangL = b.cube("fang_l_cube", [-1.5, 9.5, -7], [-0.5, 11.5, -6], { mat: "fang", faces: { up: "dark" } });
const fangR = b.cube("fang_r_cube", [0.5, 9.5, -7], [1.5, 11.5, -6], { mat: "fang", faces: { up: "dark" } });

// --- glowing red eyes (eyes_glow, 1 px proud of the face) ------------------
const eyeL = b.cube("eye_l_cube", [-1.5, 13, -6.5], [-0.5, 14, -6], { mat: "eye" });
const eyeR = b.cube("eye_r_cube", [0.5, 13, -6.5], [1.5, 14, -6], { mat: "eye" });

// --- fang glint (idle sparkle, scale 0 until it flashes) -------------------
const glint = b.cube("glint_cube", [1, 9.5, -7.4], [2, 10.5, -6.6], { mat: "glint" });

// --- wings: broad membrane panels at shoulder height, finger-bone ribs -----
const membraneFaces = { up: "membrane", down: "dark", north: "membrane", south: "membrane", east: "membrane", west: "membrane" };
const ribFaces = { up: "bone", down: "bone", north: "bone", south: "bone", east: "bone", west: "bone" };

// left
const wingLInner = b.cube("wing_l_inner", [-5, 11, -3], [-2, 12, 2], { mat: "membrane", faces: membraneFaces });
const wingLOuter = b.cube("wing_l_outer", [-8, 11, -2], [-5, 12, 3], { mat: "membrane", faces: membraneFaces });
const ribL1 = b.cube("rib_l_1", [-5, 12, -3], [-3, 12.5, -2], { mat: "bone", faces: ribFaces });
const ribL2 = b.cube("rib_l_2", [-5, 12, -1], [-3, 12.5, 0], { mat: "bone", faces: ribFaces });
const ribLo = b.cube("rib_l_o", [-8, 12, -2], [-6, 12.5, -1], { mat: "bone", faces: ribFaces });
// right
const wingRInner = b.cube("wing_r_inner", [2, 11, -3], [5, 12, 2], { mat: "membrane", faces: membraneFaces });
const wingROuter = b.cube("wing_r_outer", [5, 11, -2], [8, 12, 3], { mat: "membrane", faces: membraneFaces });
const ribR1 = b.cube("rib_r_1", [3, 12, -3], [5, 12.5, -2], { mat: "bone", faces: ribFaces });
const ribR2 = b.cube("rib_r_2", [3, 12, -1], [5, 12.5, 0], { mat: "bone", faces: ribFaces });
const ribRo = b.cube("rib_r_o", [6, 12, -2], [8, 12.5, -1], { mat: "bone", faces: ribFaces });

// --- short tail membrane ---------------------------------------------------
const tail = b.cube("tail_cube", [-1.5, 6, 2], [1.5, 7, 4], { mat: "membrane", faces: { up: "membrane", down: "dark" } });

// --- tiny clawed feet (1 px, no long legs) ---------------------------------
const footL = b.cube("foot_l_cube", [-1.5, 6, -1], [-0.5, 7, 1], { mat: "fur", faces: { down: "dark", north: "dark" } });
const footR = b.cube("foot_r_cube", [0.5, 6, -1], [1.5, 7, 1], { mat: "fur", faces: { down: "dark", north: "dark" } });

// --- bones -----------------------------------------------------------------
const earLG = b.group("ear_l", [-2, 15, -4], [earLBase, earLTip]);
const earRG = b.group("ear_r", [2, 15, -4], [earRBase, earRTip]);
const eyesGlowG = b.group("eyes_glow", [0, 13.5, -6], [eyeL, eyeR]);
const glintG = b.group("fang_glint", [1.5, 10, -7], [glint]);
const headG = b.group("head", [0, 12, -4], [head, snout, earLG, earRG, fangL, fangR, eyesGlowG, glintG]);
const wingOuterLG = b.group("wing_l_outer", [-5, 12, -1], [wingLOuter, ribLo]);
const wingLG = b.group("wing_l", [-2, 12, -1], [wingLInner, ribL1, ribL2, wingOuterLG]);
const wingOuterRG = b.group("wing_r_outer", [5, 12, -1], [wingROuter, ribRo]);
const wingRG = b.group("wing_r", [2, 12, -1], [wingRInner, ribR1, ribR2, wingOuterRG]);
const tailG = b.group("tail", [0, 6.5, 2], [tail], { rotation: [15, 0, 0] });
const bodyG = b.group("body", [0, 10, 0], [body, bodyTop, bodyBottom, bodyFront, bodyBack, headG, wingLG, wingRG, tailG]);
const footLG = b.group("leg_l", [-1, 6, 0], [footL]);
const footRG = b.group("leg_r", [1, 6, 0], [footR]);
const rootG = b.group("root", [0, 6, 0], [bodyG, footLG, footRG]);
b.outliner.push(rootG);

// --- animations ------------------------------------------------------------
// Keyframe rotation is applied by PetModels as (-x,-y,+z); positive keyframe X
// on the head nods it down. Wings flap with Z rotation (L +, R -); the outer
// wing child lags for a two-joint fold. Catmullrom for organic ease, every loop
// has identical first/last keys, linear only for the snappy bite. Whole-body
// motion lives on `root`; the rest pose already hovers 6 px up, so root keys
// only bob around that height.
const CR = "catmullrom";

// idle 3.5 s: slow hovering flaps, a little look-around, and a fang glint.
const idle = {
  [rootG.uuid]: bone(
    "root",
    keyframes("position", [[0, 0, 0, 0], [0.9, 0, 0.7, 0], [1.8, 0, 0, 0], [2.7, 0, 0.7, 0], [3.5, 0, 0, 0]], CR),
  ),
  [wingLG.uuid]: bone("wing_l", keyframes("rotation", [[0, 0, 0, 12], [0.875, 0, 0, -12], [1.75, 0, 0, 12], [2.625, 0, 0, -12], [3.5, 0, 0, 12]], CR)),
  [wingRG.uuid]: bone("wing_r", keyframes("rotation", [[0, 0, 0, -12], [0.875, 0, 0, 12], [1.75, 0, 0, -12], [2.625, 0, 0, 12], [3.5, 0, 0, -12]], CR)),
  [wingOuterLG.uuid]: bone("wing_l_outer", keyframes("rotation", [[0, 0, 0, 7], [1.0, 0, 0, -7], [1.95, 0, 0, 7], [2.85, 0, 0, -7], [3.5, 0, 0, 7]], CR)),
  [wingOuterRG.uuid]: bone("wing_r_outer", keyframes("rotation", [[0, 0, 0, -7], [1.0, 0, 0, 7], [1.95, 0, 0, -7], [2.85, 0, 0, 7], [3.5, 0, 0, -7]], CR)),
  [headG.uuid]: bone(
    "head",
    keyframes("rotation", [[0, 0, 0, 0], [1.0, 0, 12, 0], [1.7, 0, 12, 3], [2.4, 0, -10, 0], [3.0, 0, -10, -2], [3.5, 0, 0, 0]], CR),
  ),
  [glintG.uuid]: bone(
    "fang_glint",
    keyframes("scale", [[0, 0, 0, 0], [2.3, 0, 0, 0], [2.4, 1, 1, 1, "linear"], [2.7, 1, 1, 1], [2.8, 0, 0, 0], [3.5, 0, 0, 0]], CR),
  ),
  [tailG.uuid]: bone("tail", keyframes("rotation", [[0, 0, 0, 0], [1.2, 0, 8, 0], [2.4, 0, -8, 0], [3.5, 0, 0, 0]], CR)),
};

// walk 0.7 s: flapping flight, body bob plus a light roll, lagging outer wings.
const walk = {
  [rootG.uuid]: bone(
    "root",
    keyframes("position", [[0, 0, 0, 0], [0.175, 0, 0.5, 0], [0.35, 0, 0, 0], [0.525, 0, 0.5, 0], [0.7, 0, 0, 0]], CR),
  ),
  [wingLG.uuid]: bone("wing_l", keyframes("rotation", [[0, 0, 0, 38], [0.175, 0, 0, -38], [0.35, 0, 0, 38], [0.525, 0, 0, -38], [0.7, 0, 0, 38]], CR)),
  [wingRG.uuid]: bone("wing_r", keyframes("rotation", [[0, 0, 0, -38], [0.175, 0, 0, 38], [0.35, 0, 0, -38], [0.525, 0, 0, 38], [0.7, 0, 0, -38]], CR)),
  [wingOuterLG.uuid]: bone("wing_l_outer", keyframes("rotation", [[0, 0, 0, 22], [0.23, 0, 0, -22], [0.41, 0, 0, 22], [0.58, 0, 0, -22], [0.7, 0, 0, 22]], CR)),
  [wingOuterRG.uuid]: bone("wing_r_outer", keyframes("rotation", [[0, 0, 0, -22], [0.23, 0, 0, 22], [0.41, 0, 0, -22], [0.58, 0, 0, 22], [0.7, 0, 0, -22]], CR)),
  [bodyG.uuid]: bone(
    "body",
    keyframes("rotation", [[0, 0, 0, 3], [0.175, 0, 0, -3], [0.35, 0, 0, 3], [0.525, 0, 0, -3], [0.7, 0, 0, 3]], CR),
  ),
  [headG.uuid]: bone(
    "head",
    keyframes("rotation", [[0, -3, 0, 0], [0.175, 3, 0, 0], [0.35, -3, 0, 0], [0.525, 3, 0, 0], [0.7, -3, 0, 0]], CR),
  ),
  [glintG.uuid]: bone("fang_glint", keyframes("scale", [[0, 0, 0, 0]], CR)),
  [tailG.uuid]: bone("tail", keyframes("rotation", [[0, 0, 0, 0], [0.175, 0, 10, 0], [0.35, 0, 0, 0], [0.525, 0, -10, 0], [0.7, 0, 0, 0]], CR)),
};

// work 1.2 s: swoops down and forward, then snaps a bite, wings raking fast.
const work = {
  [rootG.uuid]: bone(
    "root",
    keyframes(
      "position",
      [[0, 0, 0, 0], [0.35, 0, 1, 0], [0.55, 0, -0.3, -2], [0.8, 0, 0.6, 0], [1.2, 0, 0, 0]],
      CR,
    ),
  ),
  [bodyG.uuid]: bone(
    "body",
    keyframes("rotation", [[0, 0, 0, 0], [0.35, 0, 0, 0], [0.55, 55, 0, 0], [0.8, 0, 0, 0], [1.2, 0, 0, 0]], CR),
  ),
  [headG.uuid]: bone(
    "head",
    keyframes(
      "rotation",
      [[0, 0, 0, 0], [0.45, -12, 0, 0], [0.55, 30, 0, 0, "linear"], [0.62, -14, 0, 0, "linear"], [0.72, 26, 0, 0, "linear"], [0.9, -6, 0, 0], [1.2, 0, 0, 0]],
      CR,
    ),
  ),
  [wingLG.uuid]: bone("wing_l", keyframes("rotation", [[0, 0, 0, 30], [0.15, 0, 0, 62], [0.3, 0, 0, 20], [0.55, 0, 0, -50], [0.7, 0, 0, 45], [0.9, 0, 0, -10], [1.2, 0, 0, 30]], CR)),
  [wingRG.uuid]: bone("wing_r", keyframes("rotation", [[0, 0, 0, -30], [0.15, 0, 0, -62], [0.3, 0, 0, -20], [0.55, 0, 0, 50], [0.7, 0, 0, -45], [0.9, 0, 0, 10], [1.2, 0, 0, -30]], CR)),
  [wingOuterLG.uuid]: bone("wing_l_outer", keyframes("rotation", [[0, 0, 0, 18], [0.2, 0, 0, 40], [0.55, 0, 0, -35], [0.75, 0, 0, 28], [1.2, 0, 0, 18]], CR)),
  [wingOuterRG.uuid]: bone("wing_r_outer", keyframes("rotation", [[0, 0, 0, -18], [0.2, 0, 0, -40], [0.55, 0, 0, 35], [0.75, 0, 0, -28], [1.2, 0, 0, -18]], CR)),
  [glintG.uuid]: bone("fang_glint", keyframes("scale", [[0, 0, 0, 0]], CR)),
  [tailG.uuid]: bone("tail", keyframes("rotation", [[0, 0, 0, 0], [0.4, 0, -12, 0], [0.7, 0, 12, 0], [1.2, 0, 0, 0]], CR)),
};

// eat 1.6 s: hangs head-down and sips, body swaying, fangs working.
const eat = {
  [rootG.uuid]: bone("root", keyframes("position", [[0, 0, 0, 0], [0.8, 0, 0.5, 0], [1.6, 0, 0, 0]], CR)),
  [bodyG.uuid]: bone(
    "body",
    keyframes("rotation", [[0, 105, 0, 0], [0.8, 108, 0, 6], [1.6, 105, 0, 0]], CR),
  ),
  [headG.uuid]: bone(
    "head",
    keyframes("rotation", [[0, 0, 0, 0], [0.4, 10, 0, 0], [0.8, -8, 0, 0], [1.2, 10, 0, 0], [1.6, 0, 0, 0]], CR),
  ),
  [wingLG.uuid]: bone("wing_l", keyframes("rotation", [[0, 0, 0, 8], [0.4, 0, 0, -15], [0.8, 0, 0, 8], [1.2, 0, 0, -15], [1.6, 0, 0, 8]], CR)),
  [wingRG.uuid]: bone("wing_r", keyframes("rotation", [[0, 0, 0, -8], [0.4, 0, 0, 15], [0.8, 0, 0, -8], [1.2, 0, 0, 15], [1.6, 0, 0, -8]], CR)),
  [glintG.uuid]: bone("fang_glint", keyframes("scale", [[0, 0, 0, 0]], CR)),
  [tailG.uuid]: bone("tail", keyframes("rotation", [[0, 0, 0, 0], [0.5, 0, 6, 0], [1.1, 0, -6, 0], [1.6, 0, 0, 0]], CR)),
};

// hurt 0.35 s once: instant flinch, splayed wings, overshoot, settle.
const hurt = {
  [rootG.uuid]: bone("root", keyframes("position", [[0, 0, 0, 0], [0.08, 0, 1, 0], [0.35, 0, 0, 0]], CR)),
  [bodyG.uuid]: bone("body", keyframes("scale", [[0, 1.1, 0.86, 1.1], [0.18, 0.97, 1.05, 0.97], [0.35, 1, 1, 1]], CR)),
  [headG.uuid]: bone("head", keyframes("rotation", [[0, -20, 0, 0], [0.18, 6, 0, 0], [0.35, 0, 0, 0]], CR)),
  [wingLG.uuid]: bone("wing_l", keyframes("rotation", [[0, 0, 0, 60], [0.18, 0, 0, 8], [0.35, 0, 0, 0]], CR)),
  [wingRG.uuid]: bone("wing_r", keyframes("rotation", [[0, 0, 0, -60], [0.18, 0, 0, -8], [0.35, 0, 0, 0]], CR)),
  [earLG.uuid]: bone("ear_l", keyframes("rotation", [[0, -30, 0, 0], [0.18, 5, 0, 0], [0.35, 0, 0, 0]], CR)),
  [earRG.uuid]: bone("ear_r", keyframes("rotation", [[0, -30, 0, 0], [0.18, 5, 0, 0], [0.35, 0, 0, 0]], CR)),
  [glintG.uuid]: bone("fang_glint", keyframes("scale", [[0, 0, 0, 0]], CR)),
};

b.animation("idle", "loop", 3.5, idle);
b.animation("walk", "loop", 0.7, walk);
b.animation("work", "loop", 1.2, work);
b.animation("eat", "loop", 1.6, eat);
b.animation("hurt", "once", 0.35, hurt);

b.write(bbmodelOut, pngOut, "vampbat");
console.log(`wrote ${bbmodelOut}`);
console.log(`wrote ${pngOut}`);
