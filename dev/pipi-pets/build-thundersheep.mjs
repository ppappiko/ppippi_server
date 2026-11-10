// Builds the vanilla-style "썬더쉽" (thundersheep) pet model.
// Integer-pixel geometry, 1 texel per model pixel, front faces -Z, feet on y=0.
// Vanilla sheep proportions (wool body + a separate inflated wool layer, a head
// with a wool cap, four stubby legs) but compact. A white sheep struck by
// lightning: static-frizzed wool with glowing yellow zigzag lightning marks
// (marks_glow) and small flickering spark cubes around the wool (sparks_glow).
// It discharges static by ROLLING: walk curls it into a ball (head and legs tuck
// to scale 0) while the root rolls a full turn; work is a full-body static shake.
import path from "node:path";
import { fileURLToPath } from "node:url";
import { VBuilder, keyframes, bone } from "./lib/vanilla.mjs";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "../..");
const bbmodelOut = path.join(root, "dev/pipi-facilities/assets/pipifacilities/pets/thundersheep.bbmodel");
const pngOut = path.join(root, "dev/pipi-pets/out/thundersheep.png");

const b = new VBuilder({ width: 64, height: 64 });

// --- materials -------------------------------------------------------------
b.material("wool", [["#F2F2F2", 5], ["#E4E4EA", 3], ["#FFFFFF", 2]]);
b.material("woolLight", [["#FFFFFF", 3], ["#F8F8FC", 2]]);
b.material("woolShade", [["#DADAE2", 4], ["#CCCED8", 2], ["#E6E6EE", 1]]);
b.material("cream", [["#E8DCC8", 5], ["#DCCFB6", 2], ["#F2E8D6", 1]]);
b.material("creamD", [["#D2C4A8", 3], ["#C6B89C", 2]]);
b.material("bolt", [["#FFD93B", 4], ["#FFF08A", 2], ["#E8C020", 1]]);
b.material("white", [["#FFFFFF", 3], ["#F4F4F8", 1]]);
b.material("eye", [["#2A2420", 1]]);
b.material("pink", [["#C99A88", 2], ["#B98A78", 1]]);
b.material("hoof", [["#8A7A66", 2], ["#7A6A56", 1]]);

// --- body core + separate inflated wool layer ------------------------------
const bodyCore = b.cube("body_cube", [-4, 4, -6], [4, 10, 6], {
  mat: "wool",
  faces: { up: "woolLight", down: "woolShade" },
});
const woolLayer = b.cube("wool_layer", [-4, 4, -6], [4, 10, 6], {
  mat: "wool",
  inflate: 1.0,
  faces: { up: "woolLight", down: "woolShade" },
});

// --- static-frizzed wool spikes (little static tufts) ----------------------
const frizz = (name, x1, y1, z1, x2, y2, z2) =>
  b.cube(name, [x1, y1, z1], [x2, y2, z2], { mat: "wool", faces: { up: "woolLight", down: "woolShade" } });
const frizz1 = frizz("frizz_1", -3, 11, -2, -2, 12.5, -1);
const frizz2 = frizz("frizz_2", 2, 11, 1, 3, 12.5, 2);
const frizz3 = frizz("frizz_3", 0, 11, -4, 1, 12.2, -3);
const frizz4 = frizz("frizz_4", -1, 11, 3, 0, 12.2, 4);

// --- head with wool cap (cream face, blocky vanilla sheep eyes/nose) -------
const head = b.cube("head_cube", [-4, 5, -11], [4, 11, -6], {
  mat: "cream",
  legend: { o: "cream", k: "eye", w: "white", e: "eye", m: "pink" },
  faces: {
    north: [
      "oooooooo",
      "oooooooo",
      "okwookwo",
      "oooooooo",
      "ooeeooee",
      "oommmmoo",
    ],
    down: "creamD",
    south: "cream",
    east: "cream",
    west: "cream",
    up: "cream",
  },
});
const cap = b.cube("wool_cap", [-4, 10, -11], [4, 13, -7], {
  mat: "wool",
  inflate: 0.5,
  faces: { up: "woolLight", down: "woolShade" },
});
const capSpike = frizz("cap_spike", -1, 13, -9, 1, 14.2, -8);

// --- short wool tail -------------------------------------------------------
const tail = b.cube("tail_cube", [-1, 5, 6], [1, 7, 8], {
  mat: "wool",
  faces: { up: "woolLight", down: "woolShade" },
});

// --- four stubby legs (cream, dark hoofs) ----------------------------------
const makeLeg = (name, x1, z1) =>
  b.cube(name, [x1, 0, z1], [x1 + 3, 4, z1 + 3], { mat: "cream", faces: { down: "hoof", up: "creamD" } });
const legFlCube = makeLeg("leg_fl_cube", -4, -4);
const legFrCube = makeLeg("leg_fr_cube", 1, -4);
const legBlCube = makeLeg("leg_bl_cube", -4, 1);
const legBrCube = makeLeg("leg_br_cube", 1, 1);

// --- glowing lightning marks woven into the wool (marks_glow) --------------
const mark = (name, x1, y1, z1, x2, y2, z2) => b.cube(name, [x1, y1, z1], [x2, y2, z2], { mat: "bolt" });
const markTop1 = mark("mark_top_1", -3, 10.6, -5, -1, 11.2, -3);
const markTop2 = mark("mark_top_2", -1, 10.6, -3, 1, 11.2, -1);
const markTop3 = mark("mark_top_3", 1, 10.6, -1, 3, 11.2, 1);
const markTop4 = mark("mark_top_4", -1, 10.6, 1, 1, 11.2, 3);
const markSide1 = mark("mark_side_1", -5.6, 8, -4, -5.05, 9, -1);
const markSide2 = mark("mark_side_2", -5.6, 6.5, -2, -5.05, 7.5, 1);
const markSide3 = mark("mark_side_3", -5.6, 5, 0, -5.05, 6, 3);

// --- flickering spark cubes around the wool (sparks_glow) ------------------
const spark1 = mark("spark_1", -6.4, 9, 1, -5.4, 10, 2);
const spark2 = mark("spark_2", 5.4, 7, -3, 6.4, 8, -2);
const spark3 = mark("spark_3", -0.5, 12, 3, 0.5, 13, 4);
const spark4 = mark("spark_4", 1, 11, -6, 2, 12, -5);

// --- bones -----------------------------------------------------------------
const headG = b.group("head", [0, 6, -6], [head, cap, capSpike]);
const marksGG = b.group("marks_glow", [0, 5, 0], [
  markTop1, markTop2, markTop3, markTop4, markSide1, markSide2, markSide3,
]);
const sparksGG = b.group("sparks_glow", [0, 5, 0], [spark1, spark2, spark3, spark4]);
const tailG = b.group("tail", [0, 6, 6], [tail], { rotation: [15, 0, 0] });
const bodyG = b.group("body", [0, 4, 0], [bodyCore, woolLayer, frizz1, frizz2, frizz3, frizz4, tailG, marksGG, sparksGG, headG]);
const legFlG = b.group("leg_fl", [-2.5, 4, -2.5], [legFlCube]);
const legFrG = b.group("leg_fr", [2.5, 4, -2.5], [legFrCube]);
const legBlG = b.group("leg_bl", [-2.5, 4, 2.5], [legBlCube]);
const legBrG = b.group("leg_br", [2.5, 4, 2.5], [legBrCube]);
const rootG = b.group("root", [0, 5, 0], [bodyG, legFlG, legFrG, legBlG, legBrG]);
b.outliner.push(rootG);

// --- animations ------------------------------------------------------------
// Keyframe rotation is applied by PetModels as (-x,-y,+z); positive keyframe X
// on the head nods it down. Catmullrom for organic ease, ears/tail lag the body
// for follow-through, every loop has identical first/last keys, and linear/step
// is used only for snappy accents (the roll and the static jitter).
const CR = "catmullrom";

// Flicker the spark cubes: a few quick scale dips/flares across `dur` seconds.
const sparkFlicker = (dur, pattern) => {
  const ks = [];
  for (const [t, s] of pattern) ks.push([t, s, s, s, "linear"]);
  if (ks[0][0] !== 0) ks.unshift([0, 1, 1, 1, "linear"]);
  if (ks[ks.length - 1][0] !== dur) ks.push([dur, ks[ks.length - 1][1], ks[ks.length - 1][1], ks[ks.length - 1][1], "linear"]);
  return keyframes("scale", ks);
};

// Static jitter for work: alternating linear steps, first/last identical 0.
const jitter = (dur, amp, n) => {
  const ks = [[0, 0, 0, 0, "linear"]];
  for (let i = 0; i < n; i++) {
    const t = (dur * (i + 1)) / (n + 1);
    ks.push([t, i % 2 === 0 ? amp : -amp, 0, 0, "linear"]);
  }
  ks.push([dur, 0, 0, 0, "linear"]);
  return keyframes("rotation", ks);
};

// idle 4 s: breathing, a slow look-around, and a little static zap twitch at
// t=2.6 with the sparks flaring.
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
        [1.0, 0, 8, 0],
        [1.6, 0, 8, 0],
        [2.2, 0, 0, 0],
        [2.6, -5, 0, 0, "linear"],
        [2.72, 4, 0, 0, "linear"],
        [2.85, 0, 0, 0, "linear"],
        [3.4, 0, -7, 0],
        [4.0, 0, 0, 0],
      ],
      CR,
    ),
  ),
  [sparksGG.uuid]: bone(
    "sparks_glow",
    sparkFlicker(4.0, [
      [1.5, 1], [1.62, 0.4], [1.75, 1],
      [2.6, 1], [2.68, 1.7], [2.82, 1],
      [3.5, 1], [3.62, 0.5], [3.75, 1],
    ]),
  ),
  [tailG.uuid]: bone("tail", keyframes("rotation", [[0, 0, 0, 0], [1.3, 0, 6, 0], [2.6, 0, 0, 0], [4, 0, 0, 0]], CR)),
};

// walk 1 s: tuck the head and legs away (scale 0) to become a wool ball, roll
// the root a full forward turn (positive keyframe X), then pop back out.
const tuck = keyframes("scale", [
  [0, 1, 1, 1],
  [0.22, 0, 0, 0, "linear"],
  [0.86, 0, 0, 0, "linear"],
  [1, 1, 1, 1],
]);
const walk = {
  [rootG.uuid]: bone(
    "root",
    keyframes("rotation", [[0, 0, 0, 0], [0.22, 0, 0, 0, "linear"], [0.86, 360, 0, 0, "linear"], [1, 360, 0, 0, "linear"]]),
  ),
  [headG.uuid]: bone("head", tuck),
  [legFlG.uuid]: bone("leg_fl", tuck),
  [legFrG.uuid]: bone("leg_fr", tuck),
  [legBlG.uuid]: bone("leg_bl", tuck),
  [legBrG.uuid]: bone("leg_br", tuck),
  [bodyG.uuid]: bone(
    "body",
    keyframes("scale", [[0, 1, 1, 1], [0.28, 1.07, 1.07, 1.07], [0.8, 1.07, 1.07, 1.07], [1, 1, 1, 1]], CR),
  ),
  [sparksGG.uuid]: bone(
    "sparks_glow",
    sparkFlicker(1.0, [[0.2, 1], [0.3, 0.45], [0.4, 1], [0.55, 1], [0.65, 0.5], [0.78, 1]]),
  ),
};

// work 2 s: a full-body static shake (alternating linear jitter) with the
// sparks bursting and the marks pulsing.
const work = {
  [rootG.uuid]: bone(
    "root",
    jitter(2.0, 3.2, 16),
    keyframes("position", jitterPosition(2.0, 0.16, 16)),
  ),
  [bodyG.uuid]: bone(
    "body",
    keyframes("scale", [[0, 1, 1, 1], [0.3, 1.05, 0.96, 1.05], [1.0, 1, 1, 1], [1.7, 1.05, 0.96, 1.05], [2.0, 1, 1, 1]], CR),
  ),
  [sparksGG.uuid]: bone(
    "sparks_glow",
    sparkFlicker(2.0, [
      [0.30, 1.8], [0.40, 1], [0.55, 1],
      [0.75, 2.0], [0.85, 1], [1.0, 1],
      [1.20, 1.7], [1.30, 1], [1.45, 1],
      [1.65, 2.0], [1.75, 1], [1.9, 1],
    ]),
  ),
  [marksGG.uuid]: bone(
    "marks_glow",
    keyframes("scale", [[0, 1, 1, 1], [0.4, 1, 1.25, 1], [0.8, 1, 1, 1], [1.4, 1, 1.25, 1], [2.0, 1, 1, 1]], CR),
  ),
};

function jitterPosition(dur, amp, n) {
  const ks = [[0, 0, 0, 0, "linear"]];
  for (let i = 0; i < n; i++) {
    const t = (dur * (i + 1)) / (n + 1);
    ks.push([t, i % 2 === 0 ? amp : -amp, 0, 0, "linear"]);
  }
  ks.push([dur, 0, 0, 0, "linear"]);
  return ks;
}

// eat 1.4 s: head dips to graze and nibbles twice, body settles, sparks quiet.
const eat = {
  [headG.uuid]: bone(
    "head",
    keyframes("rotation", [[0, 45, 0, 0], [0.3, 52, 0, 0], [0.55, 45, 0, 0], [0.8, 52, 0, 0], [1.05, 45, 0, 0], [1.4, 45, 0, 0]], CR),
  ),
  [bodyG.uuid]: bone(
    "body",
    keyframes("position", [[0, 0, 0, 0], [0.35, 0, -0.25, 0], [0.7, 0, 0, 0], [1.05, 0, -0.25, 0], [1.4, 0, 0, 0]], CR),
  ),
  [sparksGG.uuid]: bone(
    "sparks_glow",
    sparkFlicker(1.4, [[0.4, 0.6], [0.55, 1], [0.9, 0.7], [1.05, 1]]),
  ),
  [tailG.uuid]: bone("tail", keyframes("rotation", [[0, 0, 6, 0], [0.7, 0, -6, 0], [1.4, 0, 6, 0]], CR)),
};

// hurt 0.35 s once: instant flinch, overshoot past rest, settle.
const hurt = {
  [rootG.uuid]: bone("root", keyframes("position", [[0, 0, 0, 0], [0.08, 0, -0.7, 0], [0.35, 0, 0, 0]], CR)),
  [headG.uuid]: bone("head", keyframes("rotation", [[0, -18, 0, 0], [0.15, 6, 0, 0], [0.35, 0, 0, 0]], CR)),
  [bodyG.uuid]: bone("body", keyframes("scale", [[0, 1.12, 0.85, 1.12], [0.15, 0.97, 1.04, 0.97], [0.35, 1, 1, 1]], CR)),
  [sparksGG.uuid]: bone("sparks_glow", keyframes("scale", [[0, 1.9, 1.9, 1.9], [0.1, 1.6, 1.6, 1.6], [0.35, 1, 1, 1]], CR)),
};

b.animation("idle", "loop", 4.0, idle);
b.animation("walk", "loop", 1.0, walk);
b.animation("work", "loop", 2.0, work);
b.animation("eat", "loop", 1.4, eat);
b.animation("hurt", "once", 0.35, hurt);

b.write(bbmodelOut, pngOut, "thundersheep");
console.log(`wrote ${bbmodelOut}`);
console.log(`wrote ${pngOut}`);
