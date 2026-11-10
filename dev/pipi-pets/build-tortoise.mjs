// Builds the vanilla-style "딱지" (blocky brown tortoise) pet model.
// Integer-pixel geometry, 1 texel per model pixel, front faces -Z, feet on y=0.
// Low wide tortoise: a stepped angular shell (stacked flat tiers, each smaller,
// with blocky plate seams), four stubby elephant-like legs, a short tail and a
// squared blocky head with a heavy flat brow ridge and a stern little mouth.
import path from "node:path";
import { fileURLToPath } from "node:url";
import { VBuilder, keyframes, bone } from "./lib/vanilla.mjs";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "../..");
const bbmodelOut = path.join(root, "dev/pipi-facilities/assets/pipifacilities/pets/tortoise.bbmodel");
const pngOut = path.join(root, "dev/pipi-pets/out/tortoise.png");

const b = new VBuilder({ width: 64, height: 64 });

// --- materials -------------------------------------------------------------
b.material("shell", [["#5C3E24", 5], ["#6E4A2C", 3], ["#4A3020", 2]]);
b.material("plate", [["#8A6440", 4], ["#7A5636", 2], ["#96724A", 1]]);
b.material("seam", [["#3A2416", 2], ["#2E1C10", 1]]);
b.material("skin", [["#8A6A48", 5], ["#7A5C3E", 3], ["#96754E", 1]]);
b.material("skinD", [["#6A4E34", 2], ["#5E442C", 1]]);
b.material("eye", [["#20160E", 1]]);
b.material("mouth", [["#3A2A1C", 1]]);

// --- shell: stepped tiers, each smaller, with blocky plate seams -----------
// plateTop: dark cross seams through the middle, shell border, lighter centres.
const plateTop = (x, y, w, h) => {
  const midX = Math.floor(w / 2);
  const midY = Math.floor(h / 2);
  if (x === midX || y === midY) return "seam";
  if (x === 0 || y === 0 || x === w - 1 || y === h - 1) return "shell";
  return "plate";
};
// plateSide: lighter top rim, vertical seams every 3 texels.
const plateSide = (x, y, w, h) => {
  if (y === 0) return "plate";
  if (x % 3 === 2) return "seam";
  return "shell";
};
const tierFaces = {
  up: plateTop,
  down: "seam",
  north: plateSide,
  south: plateSide,
  east: plateSide,
  west: plateSide,
};
const tier0 = b.cube("shell_tier_0", [-7, 3, -6], [7, 5, 6], { mat: "shell", faces: tierFaces });
const tier1 = b.cube("shell_tier_1", [-6, 5, -5], [6, 7, 5], { mat: "shell", faces: tierFaces });
const tier2 = b.cube("shell_tier_2", [-4, 7, -4], [4, 9, 4], { mat: "shell", faces: tierFaces });
const tier3 = b.cube("shell_tier_3", [-2, 9, -2], [2, 10, 2], { mat: "shell", faces: tierFaces });

// --- head: squared block, heavy flat brow ridge, stern little mouth --------
const head = b.cube("head_cube", [-3, 3, -11], [3, 7, -6], {
  mat: "skin",
  legend: { o: "skin", d: "skinD", e: "eye", m: "mouth" },
  faces: {
    north: ["dddddd", "oeooeo", "oooooo", "oommoo"],
    south: "skin",
    east: "skin",
    west: "skin",
    up: "skin",
    down: "skinD",
  },
});
const brow = b.cube("brow_cube", [-4, 7, -12], [4, 8, -10], {
  mat: "skin",
  faces: { up: "skin", down: "skinD", north: "skinD", south: "skin", east: "skinD", west: "skinD" },
});
// eyelid: thin skin plate over the eye row, hidden (scale 0) except on blink.
const eyelid = b.cube("eyelid_cube", [-3, 5, -11], [3, 6, -10], {
  mat: "skin",
  inflate: 0.05,
});

// --- short tail ------------------------------------------------------------
const tail = b.cube("tail_cube", [-1, 3, 6], [1, 5, 8], {
  mat: "skin",
  faces: { up: "skin", down: "skinD", south: "skinD", north: "skin", east: "skin", west: "skin" },
});

// --- four stubby elephant-like legs ----------------------------------------
const makeLeg = (name, x1, z1) =>
  b.cube(name, [x1, 0, z1], [x1 + 3, 3, z1 + 3], { mat: "skin", faces: { down: "skinD" } });
const legFlCube = makeLeg("leg_fl_cube", -6, -5);
const legFrCube = makeLeg("leg_fr_cube", 3, -5);
const legBlCube = makeLeg("leg_bl_cube", -6, 2);
const legBrCube = makeLeg("leg_br_cube", 3, 2);

// --- bones -----------------------------------------------------------------
const eyelidG = b.group("eyelid", [0, 5.5, -10.5], [eyelid]);
const headG = b.group("head", [0, 5, -6], [head, brow, eyelidG]);
const tailG = b.group("tail", [0, 4, 6], [tail]);
const bodyG = b.group("body", [0, 0, 0], [tier0, tier1, tier2, tier3, tailG, headG]);
const legFlG = b.group("leg_fl", [-4.5, 3, -3.5], [legFlCube]);
const legFrG = b.group("leg_fr", [4.5, 3, -3.5], [legFrCube]);
const legBlG = b.group("leg_bl", [-4.5, 3, 3.5], [legBlCube]);
const legBrG = b.group("leg_br", [4.5, 3, 3.5], [legBrCube]);
const rootG = b.group("root", [0, 0, 0], [bodyG, legFlG, legFrG, legBlG, legBrG]);
b.outliner.push(rootG);

// --- animations ------------------------------------------------------------
// Keyframe rotation is applied by PetModels as (-x,-y,+z); positive keyframe X
// on the head nods it down. Catmullrom for organic ease; every loop has
// identical first/last keys; linear is used only for the snappy retract accent.
const CR = "catmullrom";

// idle 4 s: breathing, slow head bob and look-around, one slow blink.
const idle = {
  [bodyG.uuid]: bone(
    "body",
    keyframes("scale", [[0, 1, 1, 1], [1, 1, 1.02, 1], [2, 1, 1, 1], [3, 1, 1.02, 1], [4, 1, 1, 1]], CR),
  ),
  [headG.uuid]: bone(
    "head",
    keyframes("position", [[0, 0, 0, 0], [1, 0, 0.4, 0], [2, 0, 0, 0], [3, 0, 0.4, 0], [4, 0, 0, 0]], CR),
    keyframes(
      "rotation",
      [[0, 2, 0, 0], [0.9, 2, 10, 0], [1.7, 0, 10, 0], [2.4, 0, 0, 0], [3.1, 2, -8, 0], [3.6, 2, -8, 0], [4, 2, 0, 0]],
      CR,
    ),
  ),
  [eyelidG.uuid]: bone(
    "eyelid",
    keyframes("scale", [[0, 0, 0, 0], [2.4, 0, 0, 0], [2.55, 1, 1, 1], [2.75, 1, 1, 1], [2.9, 0, 0, 0], [4, 0, 0, 0]], CR),
  ),
  [tailG.uuid]: bone("tail", keyframes("rotation", [[0, 0, 0, 0], [1.3, 0, 5, 0], [2.6, 0, 0, 0], [4, 0, 0, 0]], CR)),
};

// walk 1.2 s: slow heavy plod, diagonal leg pairs, body dips and rocks, lagging head/tail.
const SWING = 20;
const walk = {
  [legFlG.uuid]: bone("leg_fl", keyframes("rotation", [[0, SWING, 0, 0], [0.6, -SWING, 0, 0], [1.2, SWING, 0, 0]], CR)),
  [legBrG.uuid]: bone("leg_br", keyframes("rotation", [[0, SWING, 0, 0], [0.6, -SWING, 0, 0], [1.2, SWING, 0, 0]], CR)),
  [legFrG.uuid]: bone("leg_fr", keyframes("rotation", [[0, -SWING, 0, 0], [0.6, SWING, 0, 0], [1.2, -SWING, 0, 0]], CR)),
  [legBlG.uuid]: bone("leg_bl", keyframes("rotation", [[0, -SWING, 0, 0], [0.6, SWING, 0, 0], [1.2, -SWING, 0, 0]], CR)),
  [bodyG.uuid]: bone(
    "body",
    keyframes("position", [[0, 0, 0, 0], [0.3, 0, -0.6, 0], [0.6, 0, 0, 0], [0.9, 0, -0.6, 0], [1.2, 0, 0, 0]], CR),
    keyframes("rotation", [[0, 0, 0, 2], [0.3, 0, 0, 0], [0.6, 0, 0, -2], [0.9, 0, 0, 0], [1.2, 0, 0, 2]], CR),
  ),
  [headG.uuid]: bone(
    "head",
    keyframes("position", [[0, 0, 0, 0], [0.3, 0, 0.4, 0], [0.6, 0, 0, 0], [0.9, 0, 0.4, 0], [1.2, 0, 0, 0]], CR),
    keyframes("rotation", [[0, 0, 0, 0], [0.3, 3, 0, 0], [0.6, 0, 0, 0], [0.9, 3, 0, 0], [1.2, 0, 0, 0]], CR),
  ),
  [tailG.uuid]: bone("tail", keyframes("rotation", [[0, 0, 0, 0], [0.3, 0, 5, 0], [0.6, 0, 0, 0], [0.9, 0, -5, 0], [1.2, 0, 0, 0]], CR)),
};

// work 6 s: head and legs retract (scale 0), root spins 1440 deg yaw, then pop out.
const retract = keyframes("scale", [
  [0, 1, 1, 1],
  [0.5, 0, 0, 0, "linear"],
  [4.7, 0, 0, 0, "linear"],
  [5.1, 1, 1, 1],
  [6, 1, 1, 1],
]);
const work = {
  [rootG.uuid]: bone(
    "root",
    keyframes("rotation", [[0, 0, 0, 0], [0.5, 0, 0, 0], [2.6, 0, 720, 0], [4.7, 0, 1440, 0], [6, 0, 1440, 0]], CR),
  ),
  [headG.uuid]: bone("head", retract),
  [legFlG.uuid]: bone("leg_fl", retract),
  [legFrG.uuid]: bone("leg_fr", retract),
  [legBlG.uuid]: bone("leg_bl", retract),
  [legBrG.uuid]: bone("leg_br", retract),
  [eyelidG.uuid]: bone("eyelid", keyframes("scale", [[0, 0, 0, 0], [6, 0, 0, 0]], CR)),
};

// eat 1.2 s: head dips to the ground and nibbles, slow tail sway.
const eat = {
  [headG.uuid]: bone(
    "head",
    keyframes("rotation", [[0, 22, 0, 0], [0.3, 28, 0, 0], [0.6, 22, 0, 0], [0.9, 28, 0, 0], [1.2, 22, 0, 0]], CR),
  ),
  [eyelidG.uuid]: bone("eyelid", keyframes("scale", [[0, 0, 0, 0], [1.2, 0, 0, 0]], CR)),
  [bodyG.uuid]: bone("body", keyframes("position", [[0, 0, 0, 0], [0.3, 0, -0.3, 0], [0.6, 0, 0, 0], [0.9, 0, -0.3, 0], [1.2, 0, 0, 0]], CR)),
  [tailG.uuid]: bone("tail", keyframes("rotation", [[0, 0, 5, 0], [0.6, 0, -5, 0], [1.2, 0, 5, 0]], CR)),
};

// hurt 0.35 s once: instant flinch, overshoot past rest, settle.
const hurt = {
  [headG.uuid]: bone("head", keyframes("rotation", [[0, -18, 0, 0], [0.15, 6, 0, 0], [0.35, 0, 0, 0]], CR)),
  [bodyG.uuid]: bone("body", keyframes("scale", [[0, 1.1, 0.88, 1.1], [0.15, 0.97, 1.03, 0.97], [0.35, 1, 1, 1]], CR)),
  [legFlG.uuid]: bone("leg_fl", keyframes("rotation", [[0, -10, 0, 0], [0.15, 3, 0, 0], [0.35, 0, 0, 0]], CR)),
  [legFrG.uuid]: bone("leg_fr", keyframes("rotation", [[0, -10, 0, 0], [0.15, 3, 0, 0], [0.35, 0, 0, 0]], CR)),
  [legBlG.uuid]: bone("leg_bl", keyframes("rotation", [[0, -10, 0, 0], [0.15, 3, 0, 0], [0.35, 0, 0, 0]], CR)),
  [legBrG.uuid]: bone("leg_br", keyframes("rotation", [[0, -10, 0, 0], [0.15, 3, 0, 0], [0.35, 0, 0, 0]], CR)),
  [eyelidG.uuid]: bone("eyelid", keyframes("scale", [[0, 0, 0, 0], [0.35, 0, 0, 0]], CR)),
};

b.animation("idle", "loop", 4.0, idle);
b.animation("walk", "loop", 1.2, walk);
b.animation("work", "loop", 6.0, work);
b.animation("eat", "loop", 1.2, eat);
b.animation("hurt", "once", 0.35, hurt);

b.write(bbmodelOut, pngOut, "tortoise");
console.log(`wrote ${bbmodelOut}`);
console.log(`wrote ${pngOut}`);
