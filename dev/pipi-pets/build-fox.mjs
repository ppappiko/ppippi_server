// Builds the vanilla-style "빨간 후드 여우" (red-hooded fox) pet model.
// Integer-pixel geometry, 1 texel per model pixel, front faces -Z.
import path from "node:path";
import { fileURLToPath } from "node:url";
import { VBuilder, keyframes, bone } from "./lib/vanilla.mjs";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "../..");
const bbmodelOut = path.join(root, "dev/pipi-facilities/assets/pipifacilities/pets/fox.bbmodel");
const pngOut = path.join(root, "dev/pipi-pets/out/fox.png");

const b = new VBuilder({ width: 64, height: 64 });

// --- materials -------------------------------------------------------------
b.material("fur", [["#E07A2E", 5], ["#D06A22", 3], ["#EA8C3E", 2], ["#C45E1C", 1]]);
b.material("white", [["#F4EEE4", 4], ["#E9E0D2", 2], ["#FFFFFF", 1]]);
b.material("dark", [["#3A2A24", 4], ["#2E211C", 2], ["#4A362D", 2]]);
b.material("red", [["#B3262D", 5], ["#9C1F26", 3], ["#C8343A", 2]]);
b.material("hem", [["#7A161C", 1]]);
b.material("gold", [["#E3B23C", 2], ["#C99A2A", 1]]);
b.material("eye", [["#1E1512", 1]]);

// --- legs (2x5x2, top pivots, top 2 rows fur / bottom 3 rows dark) ----------
const legFaces = {
  north: (x, y) => (y < 2 ? "fur" : "dark"),
  south: (x, y) => (y < 2 ? "fur" : "dark"),
  east: (x, y) => (y < 2 ? "fur" : "dark"),
  west: (x, y) => (y < 2 ? "fur" : "dark"),
  up: "fur",
  down: "dark",
};
const makeLeg = (name, x1, z1) =>
  b.cube(name, [x1, 0, z1], [x1 + 2, 5, z1 + 2], { faces: legFaces });
const legFl = makeLeg("leg_fl_cube", -3, -4);
const legFr = makeLeg("leg_fr_cube", 1, -4);
const legBl = makeLeg("leg_bl_cube", -3, 3);
const legBr = makeLeg("leg_br_cube", 1, 3);

// --- body (long horizontal fox body) ---------------------------------------
const bodyBottomWhite = (x, y, w, h) => (y === h - 1 ? "white" : "fur");
const body = b.cube("body_cube", [-3, 5, -5], [3, 11, 6], {
  mat: "fur",
  faces: {
    up: "fur",
    down: "white",
    north: bodyBottomWhite,
    south: bodyBottomWhite,
    east: bodyBottomWhite,
    west: bodyBottomWhite,
  },
});

// --- cape (short red cloak over the shoulders, body bone) -------------------
const capeHem = (x, y, w, h) => (y === h - 1 ? (x % 2 === 0 ? "gold" : "hem") : "red");
const cape = b.cube("cape_cube", [-4, 7, -4], [4, 11, 1], {
  mat: "red",
  inflate: 0.25,
  faces: { north: capeHem, south: capeHem, east: capeHem, west: capeHem, up: "red", down: "red" },
});

// --- head ------------------------------------------------------------------
const head = b.cube("head_cube", [-4, 8, -10], [4, 14, -4], {
  mat: "fur",
  legend: { o: "fur", w: "white", k: "eye" },
  faces: {
    north: ["oooooooo", "oooooooo", "owkookwo", "wwooooww", "wwwwwwww", "wwwwwwww"],
    down: "white",
    south: "fur",
    east: "fur",
    west: "fur",
    up: "fur",
  },
});
const snout = b.cube("snout_cube", [-2, 8, -13], [2, 10, -10], {
  mat: "white",
  legend: { w: "white", k: "dark" },
  faces: {
    north: ["wkkw", "wwww"],
    up: "fur",
    down: "white",
    east: "white",
    west: "white",
    south: "white",
  },
});
const makeEar = (name, x1) =>
  b.cube(name, [x1, 14, -8], [x1 + 2, 17, -7], {
    mat: "fur",
    legend: { o: "fur", w: "white", k: "dark" },
    faces: {
      north: ["kk", "ow", "ow"],
      south: ["kk", "oo", "oo"],
      east: ["k", "o", "o"],
      west: ["k", "o", "o"],
      up: ["kk"],
      down: ["oo"],
    },
  });
const earL = makeEar("ear_l_cube", -4);
const earR = makeEar("ear_r_cube", 2);

// --- hood (red shell framing the face, head bone) ---------------------------
const hoodHem = (x, y, w, h) => (y === h - 1 ? "hem" : "red");
const hood = b.cube("hood_cube", [-4, 9, -8], [4, 14, -4], {
  mat: "red",
  inflate: 0.5,
  faces: { north: hoodHem, south: hoodHem, east: hoodHem, west: hoodHem, up: "red", down: "red" },
});

// --- tail (fur, white tip +Z end) ------------------------------------------
const tail = b.cube("tail_cube", [-2, 6, 6], [2, 10, 15], {
  mat: "fur",
  faces: {
    north: "fur",
    south: "white",
    up: (x, y, w, h) => (y >= h - 3 ? "white" : "fur"),
    down: (x, y, w, h) => (y < 3 ? "white" : "fur"),
    east: (x) => (x < 3 ? "white" : "fur"),
    west: (x, y, w) => (x >= w - 3 ? "white" : "fur"),
  },
});

// --- bones -----------------------------------------------------------------
const earLG = b.group("ear_l", [-3, 14, -7.5], [earL]);
const earRG = b.group("ear_r", [3, 14, -7.5], [earR]);
const headG = b.group("head", [0, 10, -5], [head, snout, hood, earLG, earRG]);
const tailG = b.group("tail", [0, 10, 6], [tail], { rotation: [20, 0, 0] });
const bodyG = b.group("body", [0, 8, 0], [body, cape, headG, tailG]);
const legFlG = b.group("leg_fl", [-2, 5, -3], [legFl]);
const legFrG = b.group("leg_fr", [2, 5, -3], [legFr]);
const legBlG = b.group("leg_bl", [-2, 5, 4], [legBl]);
const legBrG = b.group("leg_br", [2, 5, 4], [legBr]);
const rootG = b.group("root", [0, 0, 0], [bodyG, legFlG, legFrG, legBlG, legBrG]);
b.outliner.push(rootG);

// --- animations ------------------------------------------------------------
// Keyframe rotation is applied by PetModels as (-x,-y,+z); rest bone rotation
// is applied directly. Positive keyframe X on the head nods it down. Curves use
// catmullrom for organic ease; ears/tail lag the body for follow-through, and
// every loop has identical first/last keys.
const CR = "catmullrom";

// idle 4 s: breathing, slow look-around, right-ear twitch, slow tail sway.
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
        [1.0, 0, 12, 0],
        [1.3, 0, 12, 3],
        [2.2, 0, 0, 1],
        [2.5, 0, 0, 0],
        [3.0, 0, -8, 0],
        [3.3, 0, -8, -2],
        [4.0, 0, 0, 0],
      ],
      CR,
    ),
  ),
  [earRG.uuid]: bone(
    "ear_r",
    keyframes(
      "rotation",
      [[0, 0, 0, 0], [2.4, 0, 0, 0], [2.5, 0, 0, 22, "linear"], [2.6, 0, 0, 0], [4.0, 0, 0, 0]],
      CR,
    ),
  ),
  [tailG.uuid]: bone(
    "tail",
    keyframes("rotation", [[0, 0, 0, 0], [1.3, 0, 8, 0], [2.5, 0, 0, 0], [3.3, 0, -8, 0], [4.0, 0, 0, 0]], CR),
  ),
};

// walk 0.8 s: diagonal leg pairs, body bob/roll, head counter-bob, lagging tail.
const SWING = 35;
const walk = {
  [legFlG.uuid]: bone("leg_fl", keyframes("rotation", [[0, SWING, 0, 0], [0.4, -SWING, 0, 0], [0.8, SWING, 0, 0]], CR)),
  [legBrG.uuid]: bone("leg_br", keyframes("rotation", [[0, SWING, 0, 0], [0.4, -SWING, 0, 0], [0.8, SWING, 0, 0]], CR)),
  [legFrG.uuid]: bone("leg_fr", keyframes("rotation", [[0, -SWING, 0, 0], [0.4, SWING, 0, 0], [0.8, -SWING, 0, 0]], CR)),
  [legBlG.uuid]: bone("leg_bl", keyframes("rotation", [[0, -SWING, 0, 0], [0.4, SWING, 0, 0], [0.8, -SWING, 0, 0]], CR)),
  [bodyG.uuid]: bone(
    "body",
    keyframes("position", [[0, 0, 0, 0], [0.2, 0, -0.4, 0], [0.4, 0, 0, 0], [0.6, 0, -0.4, 0], [0.8, 0, 0, 0]], CR),
    keyframes("rotation", [[0, 0, 0, 0], [0.2, 0, 0, 2], [0.4, 0, 0, 0], [0.6, 0, 0, -2], [0.8, 0, 0, 0]], CR),
  ),
  [headG.uuid]: bone(
    "head",
    keyframes("position", [[0, 0, 0, 0], [0.2, 0, 0.4, 0], [0.4, 0, 0, 0], [0.6, 0, 0.4, 0], [0.8, 0, 0, 0]], CR),
    keyframes("rotation", [[0, 0, 0, 0], [0.2, 2, 0, 0], [0.4, 0, 0, 0], [0.6, -2, 0, 0], [0.8, 0, 0, 0]], CR),
  ),
  [tailG.uuid]: bone(
    "tail",
    keyframes("rotation", [[0, 0, -10, 0], [0.2, 0, 0, 0], [0.4, 0, 10, 0], [0.6, 0, 0, 0], [0.8, 0, -10, 0]], CR),
  ),
  [earLG.uuid]: bone("ear_l", keyframes("rotation", [[0, 0, 0, 0], [0.2, -8, 0, 0], [0.4, 0, 0, 0], [0.6, -8, 0, 0], [0.8, 0, 0, 0]], CR)),
  [earRG.uuid]: bone("ear_r", keyframes("rotation", [[0, 0, 0, 0], [0.2, -8, 0, 0], [0.4, 0, 0, 0], [0.6, -8, 0, 0], [0.8, 0, 0, 0]], CR)),
};

// work 1.2 s: body/head held down, front paws scrape alternately, tail wags fast.
const work = {
  [bodyG.uuid]: bone("body", keyframes("rotation", [[0, 5, 0, 0], [1.2, 5, 0, 0]], CR)),
  [headG.uuid]: bone("head", keyframes("rotation", [[0, 25, 0, 0], [1.2, 25, 0, 0]], CR)),
  [legFlG.uuid]: bone("leg_fl", keyframes("rotation", [[0, 0, 0, 0], [0.15, 60, 0, 0], [0.6, 0, 0, 0], [1.2, 0, 0, 0]], CR)),
  [legFrG.uuid]: bone("leg_fr", keyframes("rotation", [[0, 0, 0, 0], [0.6, 0, 0, 0], [0.75, 60, 0, 0], [1.2, 0, 0, 0]], CR)),
  [tailG.uuid]: bone(
    "tail",
    keyframes(
      "rotation",
      [
        [0, 25, -15, 0],
        [0.15, 25, 15, 0],
        [0.3, 25, -15, 0],
        [0.45, 25, 15, 0],
        [0.6, 25, -15, 0],
        [0.75, 25, 15, 0],
        [0.9, 25, -15, 0],
        [1.05, 25, 15, 0],
        [1.2, 25, -15, 0],
      ],
      CR,
    ),
  ),
  [earLG.uuid]: bone("ear_l", keyframes("rotation", [[0, 5, 0, 0], [1.2, 5, 0, 0]], CR)),
  [earRG.uuid]: bone("ear_r", keyframes("rotation", [[0, 5, 0, 0], [1.2, 5, 0, 0]], CR)),
};

// eat 1.6 s: head held down, two chew dips, lift, seamless return, slow tail wag.
const eat = {
  [headG.uuid]: bone(
    "head",
    keyframes(
      "rotation",
      [
        [0.0, 40, 0, 0],
        [0.3, 40, 0, 0],
        [0.6, 45, 0, 0],
        [0.75, 40, 0, 0],
        [0.9, 45, 0, 0],
        [1.05, 40, 0, 0],
        [1.3, 30, 0, 0],
        [1.6, 40, 0, 0],
      ],
      CR,
    ),
  ),
  [tailG.uuid]: bone(
    "tail",
    keyframes("rotation", [[0, 0, -10, 0], [0.4, 0, 10, 0], [0.8, 0, -10, 0], [1.2, 0, 10, 0], [1.6, 0, -10, 0]], CR),
  ),
};

// hurt 0.4 s once: instant flinch, overshoot past rest, settle.
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

b.write(bbmodelOut, pngOut, "fox");
console.log(`wrote ${bbmodelOut}`);
console.log(`wrote ${pngOut}`);
