// Builds the vanilla-style "피토" raccoon (dexterous craftsman) pet model.
// Integer-pixel geometry, 1 texel per model pixel, front faces -Z.
import path from "node:path";
import { fileURLToPath } from "node:url";
import { VBuilder, keyframes, bone } from "./lib/vanilla.mjs";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "../..");
const bbmodelOut = path.join(root, "dev/pipi-facilities/assets/pipifacilities/pets/raccoon.bbmodel");
const pngOut = path.join(root, "dev/pipi-pets/out/raccoon.png");

const b = new VBuilder({ width: 64, height: 64 });

// --- materials -------------------------------------------------------------
b.material("fur", [["#8E8A88", 5], ["#7E7A78", 3], ["#9C9896", 2], ["#6A6664", 1]]);
b.material("light", [["#B4B0AC", 4], ["#A6A29E", 2], ["#C4C0BC", 1]]);
b.material("dark", [["#2A2828", 4], ["#222020", 2], ["#363232", 2]]);
b.material("white", [["#F4F0EA", 4], ["#E8E2DA", 2], ["#FFFFFF", 1]]);
b.material("pink", [["#EDB4AE", 4], ["#E4A49E", 2], ["#F4C4BE", 1]]);
b.material("pinkd", [["#D68E8A", 1]]);
b.material("leather", [["#6B4423", 4], ["#5C3A1E", 2], ["#7A4E2A", 2]]);
b.material("gold", [["#E3B23C", 2], ["#C99A2A", 1]]);
b.material("metal", [["#B8BCC4", 3], ["#D8DCE4", 1], ["#9A9EA6", 1]]);
b.material("handle", [["#C86A28", 3], ["#B05A20", 1]]);
b.material("eye", [["#1E1512", 1]]);
b.material("amber", [["#D8A040", 1]]);
b.material("nut", [["#9A6A34", 3], ["#7E5428", 2]]);

// --- hind legs: thigh + big foot (sitting up) -------------------------------
const thighL = b.cube("thigh_l_cube", [-5, 1, -1], [-2, 4, 3], { mat: "fur" });
const thighR = b.cube("thigh_r_cube", [2, 1, -1], [5, 4, 3], { mat: "fur" });
const footFaces = {
  north: (x, y) => (y === 1 ? "dark" : "pink"),
  south: "dark",
  east: "dark",
  west: "dark",
  up: "dark",
  down: "pinkd",
};
const footL = b.cube("foot_l_cube", [-5, 0, -4], [-2, 2, -1], { mat: "dark", faces: footFaces });
const footR = b.cube("foot_r_cube", [2, 0, -4], [5, 2, -1], { mat: "dark", faces: footFaces });

// --- body (upright, sitting) -------------------------------------------------
const bellyF = (x, y, w) => (x >= 2 && x <= w - 3 && y >= 1 ? "light" : "fur");
const body = b.cube("body_cube", [-4, 1, -2], [4, 7, 3], {
  mat: "fur",
  faces: { north: bellyF, south: "fur", east: "fur", west: "fur", up: "fur", down: "fur" },
});

// --- tool belt ---------------------------------------------------------------
const beltBuckle = (x) => (x === 3 || x === 4 ? "gold" : "leather");
const belt = b.cube("belt_cube", [-4, 2, -2], [4, 4, 3], {
  mat: "leather",
  inflate: 0.25,
  faces: { north: beltBuckle, south: "leather", east: "leather", west: "leather", up: "leather", down: "leather" },
});
const pouch = b.cube("pouch_cube", [2, 1, -3], [4, 4, -2], { mat: "leather" });
const toolHandle = b.cube("tool_handle_cube", [-3, 2, -3], [-2, 4, -2], { mat: "handle" });
const toolTip = b.cube("tool_tip_cube", [-3, 1, -3], [-2, 2, -2], { mat: "metal" });

// --- head --------------------------------------------------------------------
const head = b.cube("head_cube", [-4, 6, -5], [4, 12, 1], {
  mat: "fur",
  legend: { o: "fur", w: "white", k: "dark", a: "amber" },
  faces: {
    north: ["oooooooo", "owwoowwo", "kkkkkkkk", "kkakkakk", "wwooooww", "owwwwwwo"],
    south: "fur",
    east: (x, y, w, h) => (y >= h - 2 && x < 3 ? "white" : "fur"),
    west: (x, y, w, h) => (y >= h - 2 && x >= w - 3 ? "white" : "fur"),
    up: "fur",
    down: "white",
  },
});
const snout = b.cube("snout_cube", [-2, 6, -7], [2, 8, -5], {
  mat: "white",
  legend: { w: "white", n: "eye" },
  faces: { north: ["wnnw", "wwww"] },
});
const makeEar = (name, x1) =>
  b.cube(name, [x1, 12, -3], [x1 + 3, 14, -2], {
    mat: "fur",
    legend: { o: "fur", w: "white", k: "dark" },
    faces: {
      north: ["owo", "kkk"],
      east: ["k", "o"],
      west: ["k", "o"],
      up: ["kkk"],
    },
  });
const earL = makeEar("ear_l_cube", -4);
const earR = makeEar("ear_r_cube", 1);

// --- arms: upper arm, forearm, paw with fingers ----------------------------------
const makeUpper = (name, x1) => b.cube(name, [x1, 4, -3], [x1 + 2, 7, -1], { mat: "fur" });
const makeFore = (name, x1) => b.cube(name, [x1, 2, -3], [x1 + 2, 4, -1], { mat: "fur", faces: { down: "light" } });
const makePalm = (name, x1) => b.cube(name, [x1, 1, -3], [x1 + 2, 2, -1], { mat: "pink", faces: { up: "fur", down: "pinkd" } });
const makeFinger = (name, x1) => b.cube(name, [x1, 0, -3], [x1 + 1, 1, -2], { mat: "pink", faces: { down: "pinkd" } });
const upperL = makeUpper("upper_l_cube", -5);
const upperR = makeUpper("upper_r_cube", 3);
const foreL = makeFore("fore_l_cube", -5);
const foreR = makeFore("fore_r_cube", 3);
const palmL = makePalm("palm_l_cube", -5);
const palmR = makePalm("palm_r_cube", 3);
const fingL1 = makeFinger("finger_l1_cube", -5);
const fingL2 = makeFinger("finger_l2_cube", -4);
const fingR1 = makeFinger("finger_r1_cube", 3);
const fingR2 = makeFinger("finger_r2_cube", 4);

// --- ringed tail ---------------------------------------------------------------
// Tail runs along +Z (8 long): index i = 0 at the body, 7 at the tip. Rings at i=2,5, dark tip.
const ringAt = (i) => (i >= 7 || i === 2 || i === 5 ? "dark" : "fur");
const tail = b.cube("tail_cube", [-2, 2, 3], [2, 6, 11], {
  mat: "fur",
  faces: {
    north: "fur",
    south: "dark",
    up: (x, y) => ringAt(y),
    down: (x, y, w, h) => ringAt(h - 1 - y),
    east: (x, y, w) => ringAt(w - 1 - x),
    west: (x) => ringAt(x),
  },
});

// --- food held while eating -------------------------------------------------------
const nutC = b.cube("nut_cube", [-1, 4, -8], [1, 6, -6], { mat: "nut" });

// --- bones -------------------------------------------------------------------------
const earLG = b.group("ear_l", [-2.5, 12, -2.5], [earL]);
const earRG = b.group("ear_r", [2.5, 12, -2.5], [earR]);
const headG = b.group("head", [0, 7, -1], [head, snout, earLG, earRG]);
const mkArm = (side, x, upper, fore, palm, f1, f2) => {
  const foreG = b.group(`fore_${side}`, [x, 4, -2], [fore, palm, f1, f2], { rotation: [95, 0, 0] });
  const armG = b.group(`arm_${side}`, [x, 7, -2], [upper, foreG], { rotation: [15, 0, 0] });
  return [armG, foreG];
};
const [armLG, foreLG] = mkArm("l", -4, upperL, foreL, palmL, fingL1, fingL2);
const [armRG, foreRG] = mkArm("r", 4, upperR, foreR, palmR, fingR1, fingR2);
const foodG = b.group("food_eat", [0, 5, -7], [nutC]);
const bodyG = b.group("body", [0, 1, 0], [body, belt, pouch, toolHandle, toolTip, headG, armLG, armRG, foodG]);
const tailG = b.group("tail", [0, 4, 3], [tail], { rotation: [-35, 0, 0] });
const legLG = b.group("leg_l", [-3.5, 4, 1], [thighL, footL]);
const legRG = b.group("leg_r", [3.5, 4, 1], [thighR, footR]);
const rootG = b.group("root", [0, 0, 0], [bodyG, tailG, legLG, legRG]);
b.outliner.push(rootG);

// --- animations ------------------------------------------------------------
// Keyframes are added to the rest rotation; positive keyframe X on a head nods down.
const CR = "catmullrom";
const L = "linear";

// idle 3.6 s: breathing, look around, then rubs hands together (1.4-2.4 s).
const idle = {
  [bodyG.uuid]: bone("body", keyframes("scale", [[0, 1, 1, 1], [0.9, 1, 1.02, 1], [1.8, 1, 1, 1], [2.7, 1, 1.02, 1], [3.6, 1, 1, 1]], CR)),
  [headG.uuid]: bone(
    "head",
    keyframes("rotation", [[0, 0, 0, 0], [0.7, 0, 10, 2], [1.2, 0, 10, 2], [1.4, 8, 0, 0], [2.4, 8, 0, 0], [2.8, 0, -8, -2], [3.2, 0, -8, -2], [3.6, 0, 0, 0]], CR),
  ),
  [armLG.uuid]: bone("arm_l", keyframes("rotation", [[0, 0, 0, 0], [1.2, 0, 0, 0], [1.4, -22, 0, 0], [2.4, -22, 0, 0], [2.7, 0, 0, 0], [3.6, 0, 0, 0]], CR)),
  [armRG.uuid]: bone("arm_r", keyframes("rotation", [[0, 0, 0, 0], [1.2, 0, 0, 0], [1.4, -22, 0, 0], [2.4, -22, 0, 0], [2.7, 0, 0, 0], [3.6, 0, 0, 0]], CR)),
  [foreLG.uuid]: bone("fore_l", keyframes("rotation", [[0, 0, 0, 0], [1.2, 0, 0, 0], [1.4, 0, -18, 0], [1.6, 0, 6, 0], [1.8, 0, -18, 0], [2.0, 0, 6, 0], [2.2, 0, -18, 0], [2.4, 0, 0, 0], [3.6, 0, 0, 0]], CR)),
  [foreRG.uuid]: bone("fore_r", keyframes("rotation", [[0, 0, 0, 0], [1.2, 0, 0, 0], [1.4, 0, 18, 0], [1.6, 0, -6, 0], [1.8, 0, 18, 0], [2.0, 0, -6, 0], [2.2, 0, 18, 0], [2.4, 0, 0, 0], [3.6, 0, 0, 0]], CR)),
  [tailG.uuid]: bone("tail", keyframes("rotation", [[0, 0, 0, 0], [1.2, 0, 9, 0], [2.4, 0, 0, 0], [3.0, 0, -9, 0], [3.6, 0, 0, 0]], CR)),
  [earRG.uuid]: bone("ear_r", keyframes("rotation", [[0, 0, 0, 0], [2.9, 0, 0, 0], [3.0, 0, 0, 20, L], [3.1, 0, 0, 0], [3.6, 0, 0, 0]], CR)),
};

// walk 0.8 s: rolling waddle, side-to-side roll, alternate legs, arms swing.
const SW = 28;
const walk = {
  [legLG.uuid]: bone("leg_l", keyframes("rotation", [[0, SW, 0, 0], [0.4, -SW, 0, 0], [0.8, SW, 0, 0]], CR)),
  [legRG.uuid]: bone("leg_r", keyframes("rotation", [[0, -SW, 0, 0], [0.4, SW, 0, 0], [0.8, -SW, 0, 0]], CR)),
  [armLG.uuid]: bone("arm_l", keyframes("rotation", [[0, -SW * 0.6, 0, 0], [0.4, SW * 0.6, 0, 0], [0.8, -SW * 0.6, 0, 0]], CR)),
  [armRG.uuid]: bone("arm_r", keyframes("rotation", [[0, SW * 0.6, 0, 0], [0.4, -SW * 0.6, 0, 0], [0.8, SW * 0.6, 0, 0]], CR)),
  [bodyG.uuid]: bone(
    "body",
    keyframes("position", [[0, 0, 0, 0], [0.2, 0, 0.5, 0], [0.4, 0, 0, 0], [0.6, 0, 0.5, 0], [0.8, 0, 0, 0]], CR),
    keyframes("rotation", [[0, 0, 0, 5], [0.4, 0, 0, -5], [0.8, 0, 0, 5]], CR),
  ),
  [headG.uuid]: bone("head", keyframes("rotation", [[0, 0, 0, -4], [0.4, 0, 0, 4], [0.8, 0, 0, -4]], CR)),
  [tailG.uuid]: bone("tail", keyframes("rotation", [[0, 0, -14, 0], [0.4, 0, 14, 0], [0.8, 0, -14, 0]], CR)),
};

// work 1.0 s: sits up, head down, fast alternating paw taps (linear) with a turning twist.
const taps = (phase, s) => {
  const k = [];
  for (let i = 0; i <= 8; i++) {
    const down = (i + phase) % 2 === 0;
    const twist = s * 10 * (i % 4 < 2 ? 1 : -1);
    k.push([i * 0.125, down ? 16 : -4, twist, 0, L]);
  }
  return k;
};
const work = {
  [bodyG.uuid]: bone("body", keyframes("rotation", [[0, 6, 0, 0], [0.5, 7, 0, 0], [1.0, 6, 0, 0]], CR)),
  [headG.uuid]: bone("head", keyframes("rotation", [[0, 22, 0, 0], [0.25, 24, 0, 0], [0.5, 22, 0, 0], [0.75, 24, 0, 0], [1.0, 22, 0, 0]], CR)),
  [armLG.uuid]: bone("arm_l", keyframes("rotation", [[0, -20, 0, 0], [1.0, -20, 0, 0]], CR)),
  [armRG.uuid]: bone("arm_r", keyframes("rotation", [[0, -20, 0, 0], [1.0, -20, 0, 0]], CR)),
  [foreLG.uuid]: bone("fore_l", keyframes("rotation", taps(0, 1), L)),
  [foreRG.uuid]: bone("fore_r", keyframes("rotation", taps(1, -1), L)),
  [tailG.uuid]: bone("tail", keyframes("rotation", [[0, 0, 6, 0], [0.5, 0, -6, 0], [1.0, 0, 6, 0]], CR)),
  [earLG.uuid]: bone("ear_l", keyframes("rotation", [[0, 6, 0, 0], [1.0, 6, 0, 0]], CR)),
  [earRG.uuid]: bone("ear_r", keyframes("rotation", [[0, 6, 0, 0], [1.0, 6, 0, 0]], CR)),
};

// eat 1.6 s: paws hold the nut up to the mouth, head nibbles.
const eat = {
  [armLG.uuid]: bone("arm_l", keyframes("rotation", [[0, -40, 0, 0], [1.6, -40, 0, 0]], CR)),
  [armRG.uuid]: bone("arm_r", keyframes("rotation", [[0, -40, 0, 0], [1.6, -40, 0, 0]], CR)),
  [foreLG.uuid]: bone("fore_l", keyframes("rotation", [[0, -10, 0, 0], [1.6, -10, 0, 0]], CR)),
  [foreRG.uuid]: bone("fore_r", keyframes("rotation", [[0, -10, 0, 0], [1.6, -10, 0, 0]], CR)),
  [headG.uuid]: bone(
    "head",
    keyframes("rotation", [[0, 14, 0, 0], [0.3, 20, 0, 0], [0.5, 14, 0, 0], [0.8, 20, 0, 0], [1.0, 14, 0, 0], [1.3, 18, 0, 0], [1.6, 14, 0, 0]], CR),
  ),
  [tailG.uuid]: bone("tail", keyframes("rotation", [[0, 0, -8, 0], [0.8, 0, 8, 0], [1.6, 0, -8, 0]], CR)),
};

// hurt 0.35 s once: flinch back with overshoot.
const hurt = {
  [headG.uuid]: bone("head", keyframes("rotation", [[0, -20, 0, 0], [0.2, 6, 0, 0], [0.35, 0, 0, 0]], CR)),
  [bodyG.uuid]: bone("body", keyframes("scale", [[0, 1.08, 0.9, 1.08], [0.2, 0.97, 1.03, 0.97], [0.35, 1, 1, 1]], CR)),
  [armLG.uuid]: bone("arm_l", keyframes("rotation", [[0, -30, 0, 0], [0.2, 6, 0, 0], [0.35, 0, 0, 0]], CR)),
  [armRG.uuid]: bone("arm_r", keyframes("rotation", [[0, -30, 0, 0], [0.2, 6, 0, 0], [0.35, 0, 0, 0]], CR)),
  [tailG.uuid]: bone("tail", keyframes("rotation", [[0, -20, 0, 0], [0.2, 6, 0, 0], [0.35, 0, 0, 0]], CR)),
};

b.animation("idle", "loop", 3.6, idle);
b.animation("walk", "loop", 0.8, walk);
b.animation("work", "loop", 1.0, work);
b.animation("eat", "loop", 1.6, eat);
b.animation("hurt", "once", 0.35, hurt);

b.write(bbmodelOut, pngOut, "raccoon");
console.log(`wrote ${bbmodelOut}`);
console.log(`wrote ${pngOut}`);
