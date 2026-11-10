// Builds the vanilla-style "두류" (cute round mole, one eye lost to a scar, short stubby claws) pet model.
// Integer-pixel geometry, 1 texel per model pixel, front faces -Z, feet on y=0.
import path from "node:path";
import { fileURLToPath } from "node:url";
import { VBuilder, keyframes, bone } from "./lib/vanilla.mjs";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "../..");
const bbmodelOut = path.join(root, "dev/pipi-facilities/assets/pipifacilities/pets/mole.bbmodel");
const pngOut = path.join(root, "dev/pipi-pets/out/mole.png");

const b = new VBuilder({ width: 64, height: 64 });

// --- materials (weighted multi-tone velvet fur) ----------------------------
b.material("fur", [["#5E4E54", 5], ["#6A585E", 3], ["#524348", 2]]);
b.material("belly", [["#86767C", 4], ["#927F86", 2], ["#74646A", 1]]);
b.material("face", [["#7C676E", 4], ["#86717A", 2], ["#705C63", 1]]);
b.material("nose", [["#EE9FAC", 4], ["#E48B9A", 2], ["#F8B4BE", 1]]);
b.material("pink", [["#E8A0A8", 4], ["#DC929C", 2], ["#F0AEB6", 1]]);
b.material("claw", [["#F8DCD4", 1]]);
b.material("scar", [["#F6C6BC", 3], ["#EEB4AA", 2]]);
b.material("mouth", [["#B8606E", 1]]);
b.material("stitch", [["#8A1E2C", 1]]);
b.material("dirt", [["#5A4632", 4], ["#4A3826", 2], ["#6E563C", 1]]);
b.material("eye", [["#0A0708", 1]]);
b.material("white", [["#FFFFFF", 1]]);

// --- body: chubby 8x6x10 with rounding boxes on top and rear -----------------
const sideBelly = (x, y, w, h) => (y >= h - 2 ? "belly" : "fur");
const body = b.cube("body_cube", [-4, 2, -5], [4, 8, 5], {
  mat: "fur",
  faces: { up: "fur", down: "belly", north: sideBelly, south: sideBelly, east: sideBelly, west: sideBelly },
});
const topCap = b.cube("top_cap", [-3, 8, -4], [3, 9, 4], { mat: "fur" });
const rearCap = b.cube("rear_cap", [-3, 3, 5], [3, 7, 6], { mat: "fur" });

// --- head: round 6x5x5, slightly lower than the body top -------------------
// north grid col0 = pet's right (+X). Right eye = black + highlight.
// Left (-X) eye is lost: pale slash brow->cheek with two dark-red stitches.
const head = b.cube("head_cube", [-3, 2, -10], [3, 7, -5], {
  mat: "fur",
  legend: { f: "face", e: "eye", w: "white", s: "scar", d: "stitch" },
  faces: {
    north: ["ffdsdf", "fwffsf", "fefffd", "fffffs", "ffffff"],
    down: "belly",
    up: "fur",
    south: "fur",
    east: "fur",
    west: "fur",
  },
});
const headCap = b.cube("head_cap", [-2, 7, -9], [2, 8, -6], { mat: "fur" });
const snout = b.cube("snout_cube", [-2, 2, -11], [2, 4, -10], {
  mat: "face",
  legend: { m: "face", k: "mouth" },
  faces: { north: ["mmmm", "mkkm"], down: "belly", up: "face" },
});
const nose = b.cube("nose_cube", [-1, 3, -12], [1, 5, -11], { mat: "nose" });

// --- small pink tail -------------------------------------------------------
const tail = b.cube("tail_cube", [-1, 4, 6], [1, 6, 8], { mat: "pink" });

// --- short wide pink paws; claws are 1px lighter pixels on the front face --
const pawFaces = { north: ["cpc"], down: "pink", up: "pink" };
const pawLegend = { c: "claw", p: "pink" };
const stub = (name, x1, x2, z1, z2) => b.cube(name, [x1, 1, z1], [x2, 2, z2], { mat: "fur" });
const paw = (name, x1, x2, z1, z2, front) =>
  b.cube(name, [x1, 0, z1], [x2, 1, z2], {
    mat: "pink",
    legend: pawLegend,
    faces: front ? pawFaces : undefined,
  });
const legFr = [stub("leg_fr_fur", 3, 5, -6, -4), paw("leg_fr_paw", 2, 5, -7, -5, true)];
const legFl = [stub("leg_fl_fur", -5, -3, -6, -4), paw("leg_fl_paw", -5, -2, -7, -5, true)];
const legBr = [stub("leg_br_fur", 3, 5, 2, 4), paw("leg_br_paw", 3, 5, 2, 4, false)];
const legBl = [stub("leg_bl_fur", -5, -3, 2, 4), paw("leg_bl_paw", -5, -3, 2, 4, false)];

// --- dirt crumbs flicked while digging (bone `dirt_work`) ------------------
const dirt1 = b.cube("dirt_1", [2, 0, -9], [3, 1, -8], { mat: "dirt" });
const dirt2 = b.cube("dirt_2", [5, 1, -9], [6, 2, -8], { mat: "dirt" });
const dirt3 = b.cube("dirt_3", [-4, 1, -9], [-3, 2, -8], { mat: "dirt" });

// --- bones -----------------------------------------------------------------
const snoutG = b.group("snout", [0, 3, -9], [snout, nose]);
const headG = b.group("head", [0, 2, -5], [head, headCap, snoutG]);
const tailG = b.group("tail", [0, 5, 6], [tail]);
const bodyG = b.group("body", [0, 2, 0], [body, topCap, rearCap, headG, tailG]);
const legFlG = b.group("leg_fl", [-4, 2, -5], legFl, { rotation: [0, 0, -8] });
const legFrG = b.group("leg_fr", [4, 2, -5], legFr, { rotation: [0, 0, 8] });
const legBlG = b.group("leg_bl", [-5, 2, 3], legBl, { rotation: [0, 0, -6] });
const legBrG = b.group("leg_br", [5, 2, 3], legBr, { rotation: [0, 0, 6] });
const dirtG = b.group("dirt_work", [0, 1, -8], [dirt1, dirt2, dirt3]);
const rootG = b.group("root", [0, 0, 0], [bodyG, legFlG, legFrG, legBlG, legBrG, dirtG]);
b.outliner.push(rootG);

// --- animations ------------------------------------------------------------
// Keyframe rotation is applied by PetModels as (-x,-y,+z); rest bone rotation
// is applied directly. Positive keyframe X tips a head/body's front (-Z) down.
// Curves use catmullrom; snout/tail lag the body for follow-through and every
// loop keeps identical first/last keys.
const CR = "catmullrom";

// idle 4 s: breathing, slow head lift, a long sniffing wiggle, tail sway.
const idle = {
  [bodyG.uuid]: bone(
    "body",
    keyframes("scale", [[0, 1, 1, 1], [1, 1, 1.03, 1], [2, 1, 1, 1], [3, 1, 1.03, 1], [4, 1, 1, 1]], CR),
  ),
  [headG.uuid]: bone(
    "head",
    keyframes(
      "rotation",
      [[0, 0, 0, 0], [1, 2, 0, 0], [1.8, -7, 4, 0], [2.4, -7, -4, 0], [3, 0, 0, 0], [4, 0, 0, 0]],
      CR,
    ),
  ),
  [snoutG.uuid]: bone(
    "snout",
    keyframes(
      "rotation",
      [
        [0, 0, 0, 0],
        [0.4, 0, 0, 7],
        [0.6, 0, 0, -7],
        [0.8, 0, 0, 7],
        [1.0, 0, 0, -5],
        [1.2, 0, 0, 0],
        [2.6, 0, 0, 0],
        [2.8, 0, 0, 6],
        [3.0, 0, 0, -6],
        [3.2, 0, 0, 0],
        [4, 0, 0, 0],
      ],
      CR,
    ),
  ),
  [tailG.uuid]: bone(
    "tail",
    keyframes("rotation", [[0, 0, 0, 0], [1, 0, 9, 0], [2, 0, 0, 0], [3, 0, -9, 0], [4, 0, 0, 0]], CR),
  ),
};

// walk 0.8 s: sprawled leg paddling, body sway/roll, lagging snout and tail.
const SWING = 30;
const walk = {
  [legFlG.uuid]: bone("leg_fl", keyframes("rotation", [[0, 0, 0, 0], [0.2, SWING, 0, 0], [0.4, 0, 0, 0], [0.6, -SWING, 0, 0], [0.8, 0, 0, 0]], CR)),
  [legBrG.uuid]: bone("leg_br", keyframes("rotation", [[0, 0, 0, 0], [0.2, SWING, 0, 0], [0.4, 0, 0, 0], [0.6, -SWING, 0, 0], [0.8, 0, 0, 0]], CR)),
  [legFrG.uuid]: bone("leg_fr", keyframes("rotation", [[0, 0, 0, 0], [0.2, -SWING, 0, 0], [0.4, 0, 0, 0], [0.6, SWING, 0, 0], [0.8, 0, 0, 0]], CR)),
  [legBlG.uuid]: bone("leg_bl", keyframes("rotation", [[0, 0, 0, 0], [0.2, -SWING, 0, 0], [0.4, 0, 0, 0], [0.6, SWING, 0, 0], [0.8, 0, 0, 0]], CR)),
  [bodyG.uuid]: bone(
    "body",
    keyframes("position", [[0, 0, 0, 0], [0.2, 0, -0.3, 0], [0.4, 0, 0, 0], [0.6, 0, -0.3, 0], [0.8, 0, 0, 0]], CR),
    keyframes("rotation", [[0, 0, 0, 0], [0.2, 0, 6, 4], [0.4, 0, 0, 0], [0.6, 0, -6, -4], [0.8, 0, 0, 0]], CR),
  ),
  [headG.uuid]: bone(
    "head",
    keyframes("rotation", [[0, 0, 0, 0], [0.2, -4, 0, 0], [0.4, 0, 0, 0], [0.6, 4, 0, 0], [0.8, 0, 0, 0]], CR),
  ),
  [snoutG.uuid]: bone(
    "snout",
    keyframes("rotation", [[0, 0, 0, 0], [0.1, 0, 0, -5], [0.5, 0, 0, 5], [0.8, 0, 0, 0]], CR),
  ),
  [tailG.uuid]: bone(
    "tail",
    keyframes("rotation", [[0, 0, -10, 0], [0.2, 0, 0, 0], [0.4, 0, 10, 0], [0.6, 0, 0, 0], [0.8, 0, -10, 0]], CR),
  ),
};

// work 1.6 s: head driven down, front short paws dig alternately, dirt crumbs fly.
const work = {
  [bodyG.uuid]: bone(
    "body",
    keyframes("position", [[0, 0, 0, 0], [0.3, 0, -0.5, 0], [0.6, 0, 0, 0], [0.9, 0, -0.5, 0], [1.2, 0, 0, 0], [1.6, 0, 0, 0]], CR),
  ),
  [headG.uuid]: bone(
    "head",
    keyframes("rotation", [[0, 24, 0, 0], [0.4, 28, 0, 0], [0.8, 24, 0, 0], [1.2, 28, 0, 0], [1.6, 24, 0, 0]], CR),
  ),
  [legFlG.uuid]: bone(
    "leg_fl",
    keyframes(
      "rotation",
      [[0, 0, 0, 0], [0.15, 78, 0, 0], [0.35, 12, 0, 0], [0.5, 78, 0, 0], [0.7, 12, 0, 0], [1.0, 0, 0, 0], [1.6, 0, 0, 0]],
      CR,
    ),
  ),
  [legFrG.uuid]: bone(
    "leg_fr",
    keyframes(
      "rotation",
      [[0, 0, 0, 0], [0.2, 12, 0, 0], [0.4, 78, 0, 0], [0.6, 12, 0, 0], [0.8, 78, 0, 0], [1.1, 0, 0, 0], [1.6, 0, 0, 0]],
      CR,
    ),
  ),
  [dirtG.uuid]: bone(
    "dirt",
    keyframes("position", [[0, 0, 0, 0], [0.15, 0, 0, 1], [0.35, 0, 1.5, 4], [0.6, 0, 0.5, 2], [0.95, 0, 0, 0], [1.6, 0, 0, 0]], CR),
    keyframes(
      "scale",
      [
        [0, 0.6, 0.6, 0.6],
        [0.12, 1.1, 1.1, 1.1],
        [0.18, 1, 1, 1],
        [0.35, 1.1, 1.1, 1.1],
        [0.55, 0.35, 0.35, 0.35],
        [0.95, 0.6, 0.6, 0.6],
        [1.6, 0.6, 0.6, 0.6],
      ],
      CR,
    ),
  ),
  [tailG.uuid]: bone(
    "tail",
    keyframes("rotation", [[0, 12, 0, 0], [0.8, 12, 0, 0], [1.6, 12, 0, 0]], CR),
  ),
};

// eat 1.6 s: head dips to the ground, snout nibble wiggle, slow tail sway.
const eat = {
  [headG.uuid]: bone(
    "head",
    keyframes("rotation", [[0, 30, 0, 0], [0.3, 34, 0, 0], [0.5, 30, 0, 0], [0.7, 34, 0, 0], [0.9, 30, 0, 0], [1.3, 30, 0, 0], [1.6, 30, 0, 0]], CR),
  ),
  [snoutG.uuid]: bone(
    "snout",
    keyframes("rotation", [[0, 0, 0, 0], [0.2, 10, 0, 0], [0.4, -4, 0, 0], [0.6, 10, 0, 0], [0.8, -4, 0, 0], [1.0, 10, 0, 0], [1.3, 0, 0, 0], [1.6, 0, 0, 0]], CR),
  ),
  [tailG.uuid]: bone(
    "tail",
    keyframes("rotation", [[0, 0, -8, 0], [0.5, 0, 8, 0], [1.0, 0, -8, 0], [1.6, 0, -8, 0]], CR),
  ),
};

// hurt 0.35 s once: instant flinch up, overshoot past rest, settle.
const hurt = {
  [headG.uuid]: bone("head", keyframes("rotation", [[0, -26, 0, 0], [0.18, 8, 0, 0], [0.35, 0, 0, 0]], CR)),
  [bodyG.uuid]: bone("body", keyframes("scale", [[0, 1.08, 0.9, 1.08], [0.18, 0.98, 1.02, 0.98], [0.35, 1, 1, 1]], CR)),
  [snoutG.uuid]: bone("snout", keyframes("rotation", [[0, -16, 0, 0], [0.2, 5, 0, 0], [0.35, 0, 0, 0]], CR)),
  [legFlG.uuid]: bone("leg_fl", keyframes("rotation", [[0, -14, 0, 0], [0.18, 4, 0, 0], [0.35, 0, 0, 0]], CR)),
  [legFrG.uuid]: bone("leg_fr", keyframes("rotation", [[0, -14, 0, 0], [0.18, 4, 0, 0], [0.35, 0, 0, 0]], CR)),
};

b.animation("idle", "loop", 4.0, idle);
b.animation("walk", "loop", 0.8, walk);
b.animation("work", "loop", 1.6, work);
b.animation("eat", "loop", 1.6, eat);
b.animation("hurt", "once", 0.35, hurt);

b.write(bbmodelOut, pngOut, "mole");
console.log(`wrote ${bbmodelOut}`);
console.log(`wrote ${pngOut}`);
