// Builds the vanilla-style "해적 수달" (pirate otter) pet model.
// Integer-pixel geometry, 1 texel per model pixel, front faces -Z.
import path from "node:path";
import { fileURLToPath } from "node:url";
import { VBuilder, keyframes, bone } from "./lib/vanilla.mjs";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "../..");
const bbmodelOut = path.join(root, "dev/pipi-facilities/assets/pipifacilities/pets/otter.bbmodel");
const pngOut = path.join(root, "dev/pipi-pets/out/otter.png");

const b = new VBuilder({ width: 64, height: 64 });

// --- materials -------------------------------------------------------------
b.material("fur", [["#6E4A30", 5], ["#7E5638", 3], ["#5E3E28", 2], ["#8A6242", 1]]);
b.material("cream", [["#C9A27A", 5], ["#D8B88E", 3], ["#B8905E", 2]]);
b.material("black", [["#1C1A22", 5], ["#2A2830", 3], ["#101014", 2]]);
b.material("gold", [["#E3B23C", 3], ["#C99A2A", 2], ["#F2CE5A", 1]]);
b.material("patch", [["#0E0B0E", 3], ["#181418", 1]]);
b.material("eye", [["#2A1C12", 1]]);
b.material("white", [["#F2EFEA", 3], ["#FFFFFF", 1]]);

// --- legs (2x2x2 stubs, top pivots, fur with dark paws) -------------------
const legFaces = {
  north: (x, y, w, h) => (y >= h - 1 ? "black" : "fur"),
  south: (x, y, w, h) => (y >= h - 1 ? "black" : "fur"),
  east: (x, y, w, h) => (y >= h - 1 ? "black" : "fur"),
  west: (x, y, w, h) => (y >= h - 1 ? "black" : "fur"),
  up: "fur",
  down: "black",
};
const legFl = b.cube("leg_fl_cube", [-3, 0, -6], [-1, 2, -4], { faces: legFaces });
const legFr = b.cube("leg_fr_cube", [1, 0, -6], [3, 2, -4], { faces: legFaces });
const legBl = b.cube("leg_bl_cube", [-3, 0, 3], [-1, 2, 5], { faces: legFaces });
const legBr = b.cube("leg_br_cube", [1, 0, 3], [3, 2, 5], { faces: legFaces });

// --- body (long sleek low body) --------------------------------------------
const body = b.cube("body_cube", [-3, 2, -7], [3, 7, 5], {
  mat: "fur",
  faces: { up: "fur", down: "cream", north: "fur", south: "fur", east: "fur", west: "fur" },
});

// --- head (8x6x4, fierce: eyepatch on the viewer's-left eye, angry brow) ---
const head = b.cube("head_cube", [-4, 6, -11], [4, 12, -7], {
  mat: "fur",
  legend: { o: "fur", c: "cream", k: "black", y: "eye", p: "patch", w: "white" },
  faces: {
    north: ["oooooooo", "ppppokko", "opppoyoo", "occcccco", "cccccccc", "cccccccc"],
    south: "fur",
    east: (x, y, w, h) => (y === 2 ? "black" : "fur"),
    west: (x, y, w, h) => (y === 2 ? "black" : "fur"),
    up: "fur",
    down: "cream",
  },
});
const muzzle = b.cube("muzzle_cube", [-2, 6, -13], [2, 8, -11], {
  mat: "cream",
  legend: { c: "cream", k: "black", w: "white" },
  faces: {
    north: ["ckkc", "wccw"],
    up: "cream",
    down: "cream",
    east: "cream",
    west: "cream",
    south: "cream",
  },
});

// --- ears (small side nubs under the hat) ------------------------------------
const makeEar = (name, x1, x2) =>
  b.cube(name, [x1, 10, -10], [x2, 12, -9], { mat: "fur", faces: { north: "black", up: "fur", down: "fur", south: "fur", east: "fur", west: "fur" } });
const earL = makeEar("ear_l_cube", -5, -4);
const earR = makeEar("ear_r_cube", 4, 5);

// --- tail (thick, tapered, flat) -------------------------------------------
const tailBase = b.cube("tail_base_cube", [-3, 2, 5], [3, 4, 9], {
  mat: "fur",
  faces: { up: (x, y, w, h) => (y < 1 ? "black" : "fur"), down: "black", north: "fur", south: "fur", east: "fur", west: "fur" },
});
const tailTip = b.cube("tail_tip_cube", [-2, 2, 9], [2, 4, 13], {
  mat: "fur",
  faces: { up: (x, y, w, h) => (y < 1 ? "black" : "fur"), down: "black", north: "fur", south: "black", east: "fur", west: "fur" },
});

// --- tricorne hat: low black crown, three upturned flaps (left/right/back),
// pointed front flap with skull; gold trim on every edge ------------------------
const goldTop = (x, y, w, h) => (y === 0 ? "gold" : "black");
const crown = b.cube("hat_crown_cube", [-3, 12, -11], [3, 14, -7], {
  mat: "black",
  faces: { north: (x, y, w, h) => (y === h - 1 ? "gold" : "black"), south: "black", east: "black", west: "black", up: "black", down: "black" },
});
const flapL = b.cube("hat_flap_l_cube", [-4, 12, -11], [-3, 15, -7], {
  mat: "black",
  faces: { up: "gold", down: "black", north: goldTop, south: goldTop, east: goldTop, west: goldTop },
});
const flapR = b.cube("hat_flap_r_cube", [3, 12, -11], [4, 15, -7], {
  mat: "black",
  faces: { up: "gold", down: "black", north: goldTop, south: goldTop, east: goldTop, west: goldTop },
});
const flapB = b.cube("hat_flap_b_cube", [-3, 12, -7], [3, 15, -6], {
  mat: "black",
  faces: { up: "gold", down: "black", north: goldTop, south: goldTop, east: goldTop, west: goldTop },
});
const flapF = b.cube("hat_flap_f_cube", [-3, 12, -12], [3, 15, -11], {
  mat: "black",
  legend: { l: "black", g: "gold", w: "white", k: "black" },
  faces: { north: ["gggggg", "wkwwkw", "llwwll"], south: "black", east: goldTop, west: goldTop, up: "gold", down: "black" },
});
const tip = b.cube("hat_tip_cube", [-1, 11, -12], [1, 12, -11], {
  mat: "black",
  faces: { north: "gold", south: "black", east: "gold", west: "gold", up: "black", down: "gold" },
});

// --- bones -----------------------------------------------------------------
const earLG = b.group("ear_l", [-4.5, 10, -9.5], [earL]);
const earRG = b.group("ear_r", [4.5, 10, -9.5], [earR]);
const muzzleG = b.group("muzzle", [0, 8, -11], [muzzle]);
const flapLG = b.group("hat_flap_l", [-3, 12, -9], [flapL], { rotation: [0, 0, 22] });
const flapRG = b.group("hat_flap_r", [3, 12, -9], [flapR], { rotation: [0, 0, -22] });
const flapBG = b.group("hat_flap_b", [0, 12, -7], [flapB], { rotation: [22, 0, 0] });
const flapFG = b.group("hat_flap_f", [0, 12, -11], [flapF, tip], { rotation: [-22, 0, 0] });
const hatG = b.group("hat", [0, 12, -9], [crown, flapLG, flapRG, flapBG, flapFG]);
const headG = b.group("head", [0, 7, -7], [head, muzzleG, earLG, earRG, hatG]);
const tailG = b.group("tail", [0, 3, 5], [tailBase, tailTip], { rotation: [10, 0, 0] });
const bodyG = b.group("body", [0, 4, 0], [body, headG, tailG]);
const legFlG = b.group("leg_fl", [-2, 2, -5], [legFl]);
const legFrG = b.group("leg_fr", [2, 2, -5], [legFr]);
const legBlG = b.group("leg_bl", [-2, 2, 4], [legBl]);
const legBrG = b.group("leg_br", [2, 2, 4], [legBr]);
const rootG = b.group("root", [0, 0, 0], [bodyG, legFlG, legFrG, legBlG, legBrG]);
b.outliner.push(rootG);

// --- animations ------------------------------------------------------------
// Keyframe rotation is applied by PetModels as (-x,-y,+z); positive X on the
// head nods it down. Curves use catmullrom; ears/tail lag for follow-through and
// every loop has identical first/last keys.
const CR = "catmullrom";

// idle 4 s: rears up (root pitches, front lifts) and scans the horizon, tail
// sways, ears twitch, body breathes.
const idle = {
  [rootG.uuid]: bone(
    "root",
    keyframes("rotation", [[0, 0, 0, 0], [0.8, -20, 0, 0], [2.4, -20, 0, 0], [3.2, 0, 0, 0], [4.0, 0, 0, 0]], CR),
    keyframes("position", [[0, 0, 0, 0], [0.8, 0, 1, 0], [2.4, 0, 1, 0], [3.2, 0, 0, 0], [4.0, 0, 0, 0]], CR),
  ),
  [bodyG.uuid]: bone("body", keyframes("scale", [[0, 1, 1, 1], [1.6, 1, 1.03, 1], [2.6, 1, 1, 1], [4.0, 1, 1, 1]], CR)),
  [headG.uuid]: bone(
    "head",
    keyframes(
      "rotation",
      [
        [0.0, 0, 0, 0],
        [0.8, -10, 0, 0],
        [1.4, -12, -24, 0],
        [1.9, -12, -24, 0],
        [2.2, -12, 24, 0],
        [2.7, -12, 24, 0],
        [3.2, 0, 0, 0],
        [4.0, 0, 0, 0],
      ],
      CR,
    ),
  ),
  [tailG.uuid]: bone(
    "tail",
    keyframes("rotation", [[0, 0, 0, 0], [1.0, -14, 0, 12], [2.0, -14, 0, 0], [3.0, -14, 0, -12], [4.0, 0, 0, 0]], CR),
  ),
  [earLG.uuid]: bone("ear_l", keyframes("rotation", [[0, 0, 0, 0], [2.7, 0, 0, 0], [2.8, 0, 0, 20, "linear"], [2.95, 0, 0, 0], [4.0, 0, 0, 0]], CR)),
  [earRG.uuid]: bone("ear_r", keyframes("rotation", [[0, 0, 0, 0], [2.7, 0, 0, 0], [2.8, 0, 0, -20, "linear"], [2.95, 0, 0, 0], [4.0, 0, 0, 0]], CR)),
};

// walk 0.8 s: undulating lope, diagonal leg pairs, body bob/roll, lagging tail.
const SWING = 32;
const walk = {
  [legFlG.uuid]: bone("leg_fl", keyframes("rotation", [[0, SWING, 0, 0], [0.4, -SWING, 0, 0], [0.8, SWING, 0, 0]], CR)),
  [legBrG.uuid]: bone("leg_br", keyframes("rotation", [[0, SWING, 0, 0], [0.4, -SWING, 0, 0], [0.8, SWING, 0, 0]], CR)),
  [legFrG.uuid]: bone("leg_fr", keyframes("rotation", [[0, -SWING, 0, 0], [0.4, SWING, 0, 0], [0.8, -SWING, 0, 0]], CR)),
  [legBlG.uuid]: bone("leg_bl", keyframes("rotation", [[0, -SWING, 0, 0], [0.4, SWING, 0, 0], [0.8, -SWING, 0, 0]], CR)),
  [bodyG.uuid]: bone(
    "body",
    keyframes("position", [[0, 0, 0, 0], [0.2, 0, -0.5, 0], [0.4, 0, 0, 0], [0.6, 0, -0.5, 0], [0.8, 0, 0, 0]], CR),
    keyframes("rotation", [[0, 0, 0, 0], [0.2, 3, 6, 0], [0.4, 0, 0, 0], [0.6, -3, -6, 0], [0.8, 0, 0, 0]], CR),
  ),
  [headG.uuid]: bone(
    "head",
    keyframes("position", [[0, 0, 0, 0], [0.2, 0, 0.4, 0], [0.4, 0, 0, 0], [0.6, 0, 0.4, 0], [0.8, 0, 0, 0]], CR),
    keyframes("rotation", [[0, -6, 0, 0], [0.2, 2, 0, 0], [0.4, -6, 0, 0], [0.6, 2, 0, 0], [0.8, -6, 0, 0]], CR),
  ),
  [tailG.uuid]: bone(
    "tail",
    keyframes("rotation", [[0, -6, 14, 0], [0.2, -6, 0, 0], [0.4, -6, -14, 0], [0.6, -6, 0, 0], [0.8, -6, 14, 0]], CR),
  ),
  [earLG.uuid]: bone("ear_l", keyframes("rotation", [[0, 0, 0, 0], [0.2, -8, 0, 0], [0.4, 0, 0, 0], [0.6, -8, 0, 0], [0.8, 0, 0, 0]], CR)),
  [earRG.uuid]: bone("ear_r", keyframes("rotation", [[0, 0, 0, 0], [0.2, -8, 0, 0], [0.4, 0, 0, 0], [0.6, -8, 0, 0], [0.8, 0, 0, 0]], CR)),
};

// work 1.2 s: aggressive gnawing lunge — body lunges, head bites, jaws snap.
const work = {
  [rootG.uuid]: bone(
    "root",
    keyframes("position", [[0, 0, 0, 0], [0.2, 0, 0, -2.5], [0.4, 0, 0, 0], [0.8, 0, 0, -2.5], [1.0, 0, 0, 0], [1.2, 0, 0, 0]], CR),
  ),
  [bodyG.uuid]: bone(
    "body",
    keyframes("rotation", [[0, 0, 0, 0], [0.2, -8, 0, 0], [0.4, 0, 0, 0], [0.8, -8, 0, 0], [1.0, 0, 0, 0], [1.2, 0, 0, 0]], CR),
  ),
  [headG.uuid]: bone(
    "head",
    keyframes("rotation", [[0, 0, 0, 0], [0.2, 30, 0, 0], [0.4, 0, 0, 0], [0.8, 30, 0, 0], [1.0, 0, 0, 0], [1.2, 0, 0, 0]], CR),
  ),
  [muzzleG.uuid]: bone(
    "muzzle",
    keyframes(
      "rotation",
      [
        [0.0, 0, 0, 0],
        [0.1, 18, 0, 0],
        [0.2, 0, 0, 0],
        [0.3, 18, 0, 0],
        [0.4, 0, 0, 0],
        [0.7, 18, 0, 0],
        [0.8, 0, 0, 0],
        [0.9, 18, 0, 0],
        [1.0, 0, 0, 0],
        [1.2, 0, 0, 0],
      ],
      CR,
    ),
  ),
  [legFlG.uuid]: bone("leg_fl", keyframes("rotation", [[0, 0, 0, 0], [0.15, -50, 0, 0], [0.5, 0, 0, 0], [0.9, -50, 0, 0], [1.2, 0, 0, 0]], CR)),
  [legFrG.uuid]: bone("leg_fr", keyframes("rotation", [[0, 0, 0, 0], [0.55, -50, 0, 0], [0.9, 0, 0, 0], [1.2, 0, 0, 0]], CR)),
  [tailG.uuid]: bone(
    "tail",
    keyframes("rotation", [[0, -14, 0, 0], [0.2, -14, -14, 0], [0.4, -14, 10, 0], [0.7, -14, -14, 0], [1.0, -14, 0, 0], [1.2, -14, 0, 0]], CR),
  ),
  [earLG.uuid]: bone("ear_l", keyframes("rotation", [[0, -10, 0, 0], [1.2, -10, 0, 0]], CR)),
  [earRG.uuid]: bone("ear_r", keyframes("rotation", [[0, -10, 0, 0], [1.2, -10, 0, 0]], CR)),
};

// eat 1.6 s: head held low, jaws nibble, tail wags slowly.
const eat = {
  [headG.uuid]: bone(
    "head",
    keyframes(
      "rotation",
      [
        [0.0, 35, 0, 0],
        [0.3, 35, 0, 0],
        [0.55, 40, 0, 0],
        [0.7, 35, 0, 0],
        [0.85, 40, 0, 0],
        [1.0, 35, 0, 0],
        [1.3, 25, 0, 0],
        [1.6, 35, 0, 0],
      ],
      CR,
    ),
  ),
  [muzzleG.uuid]: bone(
    "muzzle",
    keyframes(
      "rotation",
      [
        [0.0, 0, 0, 0],
        [0.25, 14, 0, 0],
        [0.5, 0, 0, 0],
        [0.75, 14, 0, 0],
        [1.0, 0, 0, 0],
        [1.25, 14, 0, 0],
        [1.6, 0, 0, 0],
      ],
      CR,
    ),
  ),
  [tailG.uuid]: bone(
    "tail",
    keyframes("rotation", [[0, -6, -8, 0], [0.4, -6, 8, 0], [0.8, -6, -8, 0], [1.2, -6, 8, 0], [1.6, -6, -8, 0]], CR),
  ),
};

// hurt 0.4 s once: instant flinch, overshoot, settle.
const hurt = {
  [headG.uuid]: bone("head", keyframes("rotation", [[0, -20, 0, 0], [0.25, 5, 0, 0], [0.4, 0, 0, 0]], CR)),
  [bodyG.uuid]: bone("body", keyframes("scale", [[0, 1.08, 0.9, 1.08], [0.25, 0.97, 1.03, 0.97], [0.4, 1, 1, 1]], CR)),
  [earLG.uuid]: bone("ear_l", keyframes("rotation", [[0, -30, 0, 0], [0.25, 5, 0, 0], [0.4, 0, 0, 0]], CR)),
  [earRG.uuid]: bone("ear_r", keyframes("rotation", [[0, -30, 0, 0], [0.25, 5, 0, 0], [0.4, 0, 0, 0]], CR)),
};

b.animation("idle", "loop", 4.0, idle);
b.animation("walk", "loop", 0.8, walk);
b.animation("work", "loop", 1.2, work);
b.animation("eat", "loop", 1.6, eat);
b.animation("hurt", "once", 0.4, hurt);

b.write(bbmodelOut, pngOut, "otter");
console.log(`wrote ${bbmodelOut}`);
console.log(`wrote ${pngOut}`);
