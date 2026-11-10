// Builds the vanilla-style "달투" (moon-blessed black bear cub) pet model.
// Integer-pixel geometry, 1 texel per model pixel, front faces -Z, feet on y=0.
// Heavy polar-bear-like cub: thick body, four thick legs, round ears, tan muzzle;
// a pale silver crescent moon lies flush on the chest in a full-bright glow group.
import path from "node:path";
import { fileURLToPath } from "node:url";
import { VBuilder, keyframes, bone } from "./lib/vanilla.mjs";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "../..");
const bbmodelOut = path.join(root, "dev/pipi-facilities/assets/pipifacilities/pets/moonbear.bbmodel");
const pngOut = path.join(root, "dev/pipi-pets/out/moonbear.png");

const b = new VBuilder({ width: 64, height: 64 });

// --- materials -------------------------------------------------------------
b.material("fur", [["#26232A", 5], ["#1C1A20", 3], ["#302C34", 2]]);
b.material("furlt", [["#3C3842", 3], ["#4A4652", 2]]);
b.material("dark", [["#141218", 3], ["#1A181F", 1]]);
b.material("muzzle", [["#C9A27A", 4], ["#B8916A", 2]]);
b.material("nose", [["#161218", 1]]);
b.material("eye", [["#0B0910", 1]]);
b.material("moon", [["#F2F0E8", 5], ["#DCDAD0", 3], ["#FFFFFF", 2]]);
b.material("star", [["#6E6880", 2], ["#8E86A0", 1]]);

// --- legs (3x4x3, top pivot, fur with dark soles) --------------------------
const legFaces = {
  north: "fur",
  south: "fur",
  east: "fur",
  west: "fur",
  up: "fur",
  down: "dark",
};
const makeLeg = (name, x1, z1) => b.cube(name, [x1, 0, z1], [x1 + 3, 4, z1 + 3], { faces: legFaces });
const legFl = makeLeg("leg_fl_cube", -4, -5);
const legFr = makeLeg("leg_fr_cube", 1, -5);
const legBl = makeLeg("leg_bl_cube", -4, 2);
const legBr = makeLeg("leg_br_cube", 1, 2);

// --- heavy body with faint starry flecks in the fur ------------------------
const fleckUp = (x, y) => (["2,1", "6,3", "1,7", "5,8"].includes(`${x},${y}`) ? "star" : "fur");
const fleckSide = (x, y) => (["3,1", "7,3", "1,4"].includes(`${x},${y}`) ? "star" : "fur");
const body = b.cube("body_cube", [-4, 3, -5], [4, 9, 5], {
  mat: "fur",
  faces: {
    up: fleckUp,
    down: "dark",
    north: "fur",
    south: "fur",
    east: fleckSide,
    west: fleckSide,
  },
});

// --- head (flat vanilla face: tiny eyes + separate tan muzzle) -------------
const head = b.cube("head_cube", [-3, 8, -9], [3, 13, -4], {
  mat: "fur",
  legend: { o: "fur", k: "eye" },
  faces: {
    north: ["oooooo", "oooooo", "okooko", "oooooo", "oooooo"],
    down: "dark",
    south: "fur",
    east: "fur",
    west: "fur",
    up: "fur",
  },
});
const muzzle = b.cube("muzzle_cube", [-2, 8, -11], [2, 10, -9], {
  mat: "muzzle",
  legend: { m: "muzzle", n: "nose" },
  faces: {
    north: ["mmmm", "mnnm"],
    down: "muzzle",
    south: "muzzle",
    east: "muzzle",
    west: "muzzle",
    up: "muzzle",
  },
});

// --- round ears ------------------------------------------------------------
const makeEar = (name, x1) =>
  b.cube(name, [x1, 12, -8], [x1 + 2, 14, -6], {
    mat: "fur",
    faces: { north: "furlt", south: "fur", east: "fur", west: "fur", up: "fur", down: "fur" },
  });
const earL = makeEar("ear_l_cube", -3);
const earR = makeEar("ear_r_cube", 1);

// --- crescent moon flush on the chest (full-bright glow group) -------------
// A 6 px wide, 2 px tall "V"/smile band: two upper arms and a lower centre dip,
// built from 0.25 px thin cubes lying flat against the chest front (z = -5).
const moonArmL = b.cube("moon_arm_l", [-3, 6, -5.25], [-1, 7, -5], { mat: "moon" });
const moonArmR = b.cube("moon_arm_r", [1, 6, -5.25], [3, 7, -5], { mat: "moon" });
const moonDip = b.cube("moon_dip", [-1, 5, -5.25], [1, 6, -5], { mat: "moon" });

// --- bones -----------------------------------------------------------------
const earLG = b.group("ear_l", [-2, 12, -7], [earL]);
const earRG = b.group("ear_r", [2, 12, -7], [earR]);
const headG = b.group("head", [0, 9, -4], [head, muzzle, earLG, earRG]);
const moonGlowG = b.group("moon_glow", [0, 6, -5], [moonArmL, moonArmR, moonDip]);
const bodyG = b.group("body", [0, 6, 0], [body, headG, moonGlowG]);
const legFlG = b.group("leg_fl", [-2.5, 4, -3.5], [legFl]);
const legFrG = b.group("leg_fr", [2.5, 4, -3.5], [legFr]);
const legBlG = b.group("leg_bl", [-2.5, 4, 3.5], [legBl]);
const legBrG = b.group("leg_br", [2.5, 4, 3.5], [legBr]);
const rootG = b.group("root", [0, 0, 0], [bodyG, legFlG, legFrG, legBlG, legBrG]);
b.outliner.push(rootG);

// --- animations ------------------------------------------------------------
// Keyframe rotation is applied by PetModels as (-x,-y,+z); positive keyframe X
// on the head nods it down (so negative looks up). Catmullrom for organic ease,
// ears lag the body for follow-through, every loop has identical first/last keys.
const CR = "catmullrom";

// idle 4 s: breathing, then it sits back and looks up at the sky, ears ease.
const idle = {
  [bodyG.uuid]: bone(
    "body",
    keyframes("scale", [[0, 1, 1, 1], [1, 1, 1.02, 1], [2, 1, 1, 1], [3, 1, 1.02, 1], [4, 1, 1, 1]], CR),
    keyframes("rotation", [[0, 0, 0, 0], [1.2, 0, 0, 0], [1.8, -8, 0, 0], [2.8, -8, 0, 0], [3.4, 0, 0, 0], [4, 0, 0, 0]], CR),
  ),
  [headG.uuid]: bone(
    "head",
    keyframes(
      "rotation",
      [
        [0.0, 0, 0, 0],
        [0.6, 0, 0, 0],
        [1.2, 0, -10, 0],
        [1.8, -34, 0, 4],
        [2.6, -36, 0, 6],
        [3.0, -16, 0, 0],
        [3.6, 0, 0, 0],
        [4.0, 0, 0, 0],
      ],
      CR,
    ),
  ),
  [earLG.uuid]: bone("ear_l", keyframes("rotation", [[0, 0, 0, 0], [2.0, 0, 0, 0], [2.3, 0, 0, -14], [2.7, 0, 0, 0], [4, 0, 0, 0]], CR)),
  [earRG.uuid]: bone("ear_r", keyframes("rotation", [[0, 0, 0, 0], [2.1, 0, 0, 0], [2.4, 0, 0, 14], [2.8, 0, 0, 0], [4, 0, 0, 0]], CR)),
};

// walk 1.2 s: diagonal heavy plod, body bob plus a shoulder roll, lagging ears.
const SWING = 28;
const walk = {
  [legFlG.uuid]: bone("leg_fl", keyframes("rotation", [[0, SWING, 0, 0], [0.6, -SWING, 0, 0], [1.2, SWING, 0, 0]], CR)),
  [legBrG.uuid]: bone("leg_br", keyframes("rotation", [[0, SWING, 0, 0], [0.6, -SWING, 0, 0], [1.2, SWING, 0, 0]], CR)),
  [legFrG.uuid]: bone("leg_fr", keyframes("rotation", [[0, -SWING, 0, 0], [0.6, SWING, 0, 0], [1.2, -SWING, 0, 0]], CR)),
  [legBlG.uuid]: bone("leg_bl", keyframes("rotation", [[0, -SWING, 0, 0], [0.6, SWING, 0, 0], [1.2, -SWING, 0, 0]], CR)),
  [bodyG.uuid]: bone(
    "body",
    keyframes("position", [[0, 0, 0, 0], [0.3, 0, -0.5, 0], [0.6, 0, 0, 0], [0.9, 0, -0.5, 0], [1.2, 0, 0, 0]], CR),
    keyframes("rotation", [[0, 0, 0, 5], [0.3, 0, 0, 0], [0.6, 0, 0, -5], [0.9, 0, 0, 0], [1.2, 0, 0, 5]], CR),
  ),
  [headG.uuid]: bone(
    "head",
    keyframes("position", [[0, 0, 0, 0], [0.3, 0, 0.4, 0], [0.6, 0, 0, 0], [0.9, 0, 0.4, 0], [1.2, 0, 0, 0]], CR),
    keyframes("rotation", [[0, 0, 0, -5], [0.3, 0, 0, 0], [0.6, 0, 0, 5], [0.9, 0, 0, 0], [1.2, 0, 0, -5]], CR),
  ),
  [earLG.uuid]: bone("ear_l", keyframes("rotation", [[0, 0, 0, 0], [0.3, -8, 0, 0], [0.6, 0, 0, 0], [0.9, -8, 0, 0], [1.2, 0, 0, 0]], CR)),
  [earRG.uuid]: bone("ear_r", keyframes("rotation", [[0, 0, 0, 0], [0.3, -8, 0, 0], [0.6, 0, 0, 0], [0.9, -8, 0, 0], [1.2, 0, 0, 0]], CR)),
};

// work 1.6 s: rears up on the hind legs, then slams both front paws down twice.
const work = {
  [rootG.uuid]: bone(
    "root",
    keyframes("position", [[0, 0, 0, 0], [0.3, 0, 2.4, 0], [0.6, 0, 0, 0], [0.9, 0, 2.4, 0], [1.2, 0, 0, 0], [1.6, 0, 0, 0]], CR),
  ),
  [bodyG.uuid]: bone(
    "body",
    keyframes("rotation", [[0, 0, 0, 0], [0.3, -30, 0, 0], [0.6, 14, 0, 0], [0.9, -30, 0, 0], [1.2, 14, 0, 0], [1.6, 0, 0, 0]], CR),
  ),
  [legFlG.uuid]: bone(
    "leg_fl",
    keyframes("rotation", [[0, 0, 0, 0], [0.3, -70, 0, 0], [0.6, 12, 0, 0], [0.9, -70, 0, 0], [1.2, 12, 0, 0], [1.6, 0, 0, 0]], CR),
  ),
  [legFrG.uuid]: bone(
    "leg_fr",
    keyframes("rotation", [[0, 0, 0, 0], [0.3, -70, 0, 0], [0.6, 12, 0, 0], [0.9, -70, 0, 0], [1.2, 12, 0, 0], [1.6, 0, 0, 0]], CR),
  ),
  [headG.uuid]: bone(
    "head",
    keyframes("rotation", [[0, 0, 0, 0], [0.3, -22, 0, 0], [0.6, 24, 0, 0], [0.9, -22, 0, 0], [1.2, 24, 0, 0], [1.6, 0, 0, 0]], CR),
  ),
  [earLG.uuid]: bone("ear_l", keyframes("rotation", [[0, 0, 0, 0], [0.3, -20, 0, 0], [0.6, 10, 0, 0], [0.9, -20, 0, 0], [1.2, 10, 0, 0], [1.6, 0, 0, 0]], CR)),
  [earRG.uuid]: bone("ear_r", keyframes("rotation", [[0, 0, 0, 0], [0.3, -20, 0, 0], [0.6, 10, 0, 0], [0.9, -20, 0, 0], [1.2, 10, 0, 0], [1.6, 0, 0, 0]], CR)),
  [moonGlowG.uuid]: bone(
    "moon_glow",
    keyframes("scale", [[0, 1, 1, 1], [0.55, 1, 1, 1], [0.6, 1.18, 1.18, 1.18], [0.8, 1.18, 1.18, 1.18], [0.85, 1, 1, 1], [1.15, 1, 1, 1], [1.2, 1.18, 1.18, 1.18], [1.4, 1.18, 1.18, 1.18], [1.45, 1, 1, 1], [1.6, 1, 1, 1]], CR),
  ),
};

// eat 1.6 s: head held down over the food, two chew dips, ears bob along.
const eat = {
  [headG.uuid]: bone(
    "head",
    keyframes(
      "rotation",
      [
        [0.0, 30, 0, 0],
        [0.3, 34, 0, 0],
        [0.55, 30, 0, 0],
        [0.8, 34, 0, 0],
        [1.05, 30, 0, 0],
        [1.3, 18, 0, 0],
        [1.6, 30, 0, 0],
      ],
      CR,
    ),
  ),
  [bodyG.uuid]: bone(
    "body",
    keyframes("scale", [[0, 1, 1, 1], [0.3, 1.02, 0.99, 1.02], [0.55, 1, 1, 1], [0.8, 1.02, 0.99, 1.02], [1.05, 1, 1, 1], [1.6, 1, 1, 1]], CR),
  ),
  [earLG.uuid]: bone("ear_l", keyframes("rotation", [[0, 0, 0, 0], [0.3, 10, 0, 0], [0.55, 0, 0, 0], [0.8, 10, 0, 0], [1.05, 0, 0, 0], [1.6, 0, 0, 0]], CR)),
  [earRG.uuid]: bone("ear_r", keyframes("rotation", [[0, 0, 0, 0], [0.3, 10, 0, 0], [0.55, 0, 0, 0], [0.8, 10, 0, 0], [1.05, 0, 0, 0], [1.6, 0, 0, 0]], CR)),
};

// hurt 0.4 s once: instant squash flinch, overshoot past rest, settle.
const hurt = {
  [bodyG.uuid]: bone("body", keyframes("scale", [[0, 1.12, 0.86, 1.12], [0.15, 0.97, 1.04, 0.97], [0.4, 1, 1, 1]], CR)),
  [headG.uuid]: bone("head", keyframes("rotation", [[0, -20, 0, 0], [0.15, 6, 0, 0], [0.4, 0, 0, 0]], CR)),
  [earLG.uuid]: bone("ear_l", keyframes("rotation", [[0, -30, 0, 0], [0.15, 5, 0, 0], [0.4, 0, 0, 0]], CR)),
  [earRG.uuid]: bone("ear_r", keyframes("rotation", [[0, -30, 0, 0], [0.15, 5, 0, 0], [0.4, 0, 0, 0]], CR)),
};

b.animation("idle", "loop", 4.0, idle);
b.animation("walk", "loop", 1.2, walk);
b.animation("work", "loop", 1.6, work);
b.animation("eat", "loop", 1.6, eat);
b.animation("hurt", "once", 0.4, hurt);

b.write(bbmodelOut, pngOut, "moonbear");
console.log(`wrote ${bbmodelOut}`);
console.log(`wrote ${pngOut}`);
