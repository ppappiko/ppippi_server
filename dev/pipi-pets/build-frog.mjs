// Builds the vanilla-style "가마" (samurai frog) pet model.
// Integer-pixel geometry, 1 texel per model pixel, front faces -Z, feet on y=0.
// Vanilla-frog squat body sitting upright on folded hind legs, front legs as
// arms: a wide conical straw sakkat hat shading the eyes, a dark navy cloak
// over the shoulders, and a katana at its side (sheath+hilt in `sword_rest`,
// drawn silver blade in `blade_work`).
import path from "node:path";
import { fileURLToPath } from "node:url";
import { VBuilder, keyframes, bone } from "./lib/vanilla.mjs";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "../..");
const bbmodelOut = path.join(root, "dev/pipi-facilities/assets/pipifacilities/pets/frog.bbmodel");
const pngOut = path.join(root, "dev/pipi-pets/out/frog.png");

const b = new VBuilder({ width: 64, height: 64 });

// --- materials -------------------------------------------------------------
b.material("skin", [["#5E8C3A", 5], ["#6E9E44", 3], ["#4E7830", 2], ["#7AAE4C", 1]]);
b.material("belly", [["#C8D890", 5], ["#D8E8A4", 3], ["#B4C67C", 2]]);
b.material("dark", [["#3A5A26", 3], ["#2E4A1E", 2]]);
b.material("hat", [["#C8A464", 5], ["#D8B878", 3], ["#B08E50", 2]]);
b.material("hat2", [["#B08E50", 5], ["#A07E40", 3], ["#C8A464", 2]]);
b.material("hatRim", [["#8A6A38", 3], ["#7A5C30", 2]]);
b.material("cloak", [["#2A2E48", 5], ["#222640", 3], ["#343A58", 2]]);
b.material("sheath", [["#1E2236", 4], ["#2A3048", 2], ["#141828", 1]]);
b.material("wrap", [["#3A3A46", 3], ["#2A2A34", 2], ["#4A4A58", 1]]);
b.material("gold", [["#D8B040", 3], ["#C09828", 2], ["#EAC860", 1]]);
b.material("blade", [["#D8DCE4", 4], ["#B8BEC8", 2], ["#F0F2F6", 1]]);
b.material("eye", [["#1A1014", 1]]);
b.material("white", [["#FFFFFF", 1]]);
b.material("mouth", [["#2E2018", 1]]);
b.material("tongue", [["#D86A76", 3], ["#C25A66", 2], ["#E88A94", 1]]);

// --- straw hat weave functions ---------------------------------------------
const wovenUp = (x, y, w, h) => {
  if (x === 0 || y === 0 || x === w - 1 || y === h - 1) return "hatRim";
  return (x + y) % 2 === 0 ? "hat" : "hat2";
};
const strawSide = (x) => (x % 2 === 0 ? "hat" : "hat2");

// --- body (squat, pale belly on the front) ---------------------------------
const body = b.cube("body_cube", [-3, 0, -3], [3, 4, 3], {
  mat: "skin",
  legend: { o: "skin", b: "belly" },
  faces: {
    north: ["oooooo", "oooooo", "obbbbo", "bbbbbb"],
    south: "skin",
    east: "skin",
    west: "skin",
    up: "skin",
    down: "belly",
  },
});

// --- head (wide flat vanilla frog head, stern mouth) -----------------------
const head = b.cube("head_cube", [-3, 3, -7], [3, 6, -3], {
  mat: "skin",
  legend: { o: "skin", m: "mouth" },
  faces: {
    north: ["oooooo", "oooooo", "oommoo"],
    south: "skin",
    east: "skin",
    west: "skin",
    up: "skin",
    down: "dark",
  },
});

// --- bulging eyes on top of the head ---------------------------------------
const eyeL = b.cube("eye_l_cube", [-3, 6, -7], [-1, 8, -5], {
  mat: "skin",
  legend: { k: "eye", w: "white" },
  faces: { north: ["kw", "kk"], south: "skin", east: "skin", west: "skin", up: "skin", down: "skin" },
});
const eyeR = b.cube("eye_r_cube", [1, 6, -7], [3, 8, -5], {
  mat: "skin",
  legend: { k: "eye", w: "white" },
  faces: { north: ["wk", "kk"], south: "skin", east: "skin", west: "skin", up: "skin", down: "skin" },
});

// --- wide conical straw sakkat (stacked shrinking tiers) --------------------
const hatBrim = b.cube("hat_brim", [-5, 8, -5], [5, 9, 5], {
  mat: "hat",
  faces: { up: wovenUp, down: "hatRim", north: strawSide, south: strawSide, east: strawSide, west: strawSide },
});
const hatTier1 = b.cube("hat_tier_1", [-3, 9, -3], [3, 10, 3], {
  mat: "hat",
  faces: { up: wovenUp, down: "hatRim", north: strawSide, south: strawSide, east: strawSide, west: strawSide },
});
const hatTier2 = b.cube("hat_tier_2", [-2, 10, -2], [2, 11, 2], {
  mat: "hat",
  faces: { up: wovenUp, down: "hatRim", north: strawSide, south: strawSide, east: strawSide, west: strawSide },
});
const hatTier3 = b.cube("hat_tier_3", [-1, 11, -1], [1, 12, 1], {
  mat: "hat",
  faces: { up: wovenUp, down: "hatRim", north: strawSide, south: strawSide, east: strawSide, west: strawSide },
});

// --- navy cloak draped over the shoulders (gold hem) -----------------------
const cloakHem = (x, y, w, h) => (y === h - 1 ? "gold" : "cloak");
const cloak = b.cube("cloak_cube", [-3.6, 4, -5], [3.6, 6, 3], {
  mat: "cloak",
  inflate: 0.3,
  faces: { north: cloakHem, south: cloakHem, east: cloakHem, west: cloakHem, up: "cloak", down: "cloak" },
});

// --- katana: worn sheath + hilt (sword_rest, hidden while working) ----------
const sheath = b.cube("sheath_cube", [3.2, 2.4, 2], [4.0, 3.2, 8], {
  mat: "sheath",
  faces: { up: "sheath", down: "sheath", north: "sheath", south: "sheath", east: "sheath", west: "sheath" },
});
const sGuard = b.cube("sguard_cube", [3.0, 2.2, 1], [4.2, 3.4, 2], { mat: "gold" });
const sHilt = b.cube("shilt_cube", [3.3, 2.5, -3], [3.9, 3.1, 1], { mat: "wrap" });

// --- katana: drawn blade (blade_work, shown only while working) -------------
const blade = b.cube("blade_cube", [3.6, 3.6, -9], [4.4, 4.4, -1], {
  mat: "blade",
  faces: { up: "blade", down: "blade", north: "blade", south: "blade", east: "blade", west: "blade" },
});
const bGuard = b.cube("bguard_cube", [3.1, 3.2, -1.2], [4.9, 4.8, -0.2], { mat: "gold" });
const bHilt = b.cube("bhilt_cube", [3.7, 3.7, -0.2], [4.3, 4.3, 2.2], { mat: "wrap" });

// --- front legs as arms ----------------------------------------------------
const armL = b.cube("arm_l_cube", [-2.6, 0, -5.4], [-1.2, 3, -3.6], {
  mat: "skin",
  faces: { down: "dark" },
});
const armR = b.cube("arm_r_cube", [1.2, 0, -5.4], [2.6, 3, -3.6], {
  mat: "skin",
  faces: { down: "dark" },
});

// --- folded hind legs with big flat feet -----------------------------------
const thighL = b.cube("thigh_l_cube", [-5, 0, -1], [-3, 3, 4], { mat: "skin", faces: { down: "dark" } });
const footL = b.cube("foot_l_cube", [-5, 0, -4], [-2, 1, 1], { mat: "skin", faces: { down: "dark" } });
const thighR = b.cube("thigh_r_cube", [3, 0, -1], [5, 3, 4], { mat: "skin", faces: { down: "dark" } });
const footR = b.cube("foot_r_cube", [2, 0, -4], [5, 1, 1], { mat: "skin", faces: { down: "dark" } });

// --- flicking tongue (only while eating) -----------------------------------
const tongue = b.cube("tongue_cube", [-1, 4.4, -8.2], [1, 5.4, -6.8], {
  mat: "tongue",
  faces: { down: "tongue", up: "tongue", north: "tongue", south: "tongue", east: "tongue", west: "tongue" },
});

// --- bones -----------------------------------------------------------------
const hatG = b.group("hat", [0, 8, 0], [hatBrim, hatTier1, hatTier2, hatTier3]);
const tongueG = b.group("tongue_eat", [0, 4.9, -6.8], [tongue]);
const headG = b.group("head", [0, 4, -4], [head, eyeL, eyeR, hatG, tongueG]);
const swordRestG = b.group("sword_rest", [3.6, 2.8, 1], [sheath, sGuard, sHilt], { rotation: [-42, 0, 8] });
const bladeG = b.group("blade_work", [4, 4, -1], [blade, bGuard, bHilt]);
const armLG = b.group("arm_l", [-2, 3, -3.6], [armL]);
const armRG = b.group("arm_r", [2, 3, -3.6], [armR]);
const bodyG = b.group("body", [0, 4, 0], [body, cloak, headG, swordRestG, bladeG, armLG, armRG]);
const legLG = b.group("leg_l", [-4, 3, 1], [thighL, footL]);
const legRG = b.group("leg_r", [4, 3, 1], [thighR, footR]);
const rootG = b.group("root", [0, 0, 0], [bodyG, legLG, legRG]);
b.outliner.push(rootG);

// --- animations ------------------------------------------------------------
// Keyframe rotation is applied by PetModels as (-x,-y,+z); positive keyframe X
// on the head nods it down. Catmullrom for organic ease, linear only for the
// snappy iaido slash, and every loop has identical first/last keys.
const CR = "catmullrom";

// idle 4 s: breathing, slow look-around, the hat brim tilts and settles.
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
        [1.0, 0, 8, 0],
        [1.8, 0, 8, -2],
        [2.6, 0, 0, 0],
        [3.1, 0, -6, 0],
        [3.6, 0, -6, 0],
        [4.0, 0, 0, 0],
      ],
      CR,
    ),
  ),
  [hatG.uuid]: bone(
    "hat",
    keyframes(
      "rotation",
      [
        [0, 0, 0, 0],
        [1.5, 0, 0, 0],
        [2.0, 8, 0, 0, "linear"],
        [2.4, 6, 0, 0],
        [2.9, -6, 0, 0],
        [3.4, 2, 0, 0],
        [4.0, 0, 0, 0],
      ],
      CR,
    ),
    keyframes("position", [[0, 0, 0, 0], [2.0, 0, -0.3, 0], [2.6, 0, 0, 0], [4.0, 0, 0, 0]], CR),
  ),
  [armLG.uuid]: bone("arm_l", keyframes("rotation", [[0, 0, 0, 0], [1.5, -6, 0, 0], [2.7, 4, 0, 0], [4, 0, 0, 0]], CR)),
  [armRG.uuid]: bone("arm_r", keyframes("rotation", [[0, 0, 0, 0], [1.5, -6, 0, 0], [2.7, 4, 0, 0], [4, 0, 0, 0]], CR)),
  [legLG.uuid]: bone("leg_l", keyframes("rotation", [[0, 0, 0, 0], [2, 2, 0, 0], [4, 0, 0, 0]], CR)),
  [legRG.uuid]: bone("leg_r", keyframes("rotation", [[0, 0, 0, 0], [2, 2, 0, 0], [4, 0, 0, 0]], CR)),
};

// walk 0.8 s: short two-footed hops — root arcs up twice, hind legs push,
// arms counter-swing, hat and head bob with follow-through.
const walk = {
  [rootG.uuid]: bone(
    "root",
    keyframes(
      "position",
      [[0, 0, 0, 0], [0.2, 0, 2.2, 0], [0.4, 0, 0, 0], [0.6, 0, 2.2, 0], [0.8, 0, 0, 0]],
      CR,
    ),
    keyframes("rotation", [[0, 0, 0, 0], [0.2, -7, 0, 0], [0.4, 5, 0, 0], [0.6, -7, 0, 0], [0.8, 0, 0, 0]], CR),
  ),
  [bodyG.uuid]: bone(
    "body",
    keyframes("rotation", [[0, 0, 0, 0], [0.15, 7, 0, 0], [0.2, -4, 0, 0], [0.4, 0, 0, 0], [0.55, 7, 0, 0], [0.6, -4, 0, 0], [0.8, 0, 0, 0]], CR),
  ),
  [legLG.uuid]: bone("leg_l", keyframes("rotation", [[0, -20, 0, 0], [0.15, 26, 0, 0], [0.4, -20, 0, 0], [0.55, 26, 0, 0], [0.8, -20, 0, 0]], CR)),
  [legRG.uuid]: bone("leg_r", keyframes("rotation", [[0, -20, 0, 0], [0.15, 26, 0, 0], [0.4, -20, 0, 0], [0.55, 26, 0, 0], [0.8, -20, 0, 0]], CR)),
  [armLG.uuid]: bone("arm_l", keyframes("rotation", [[0, 14, 0, 0], [0.2, -16, 0, 0], [0.4, 14, 0, 0], [0.6, -16, 0, 0], [0.8, 14, 0, 0]], CR)),
  [armRG.uuid]: bone("arm_r", keyframes("rotation", [[0, 14, 0, 0], [0.2, -16, 0, 0], [0.4, 14, 0, 0], [0.6, -16, 0, 0], [0.8, 14, 0, 0]], CR)),
  [headG.uuid]: bone("head", keyframes("rotation", [[0, 0, 0, 0], [0.2, -8, 0, 0], [0.4, 5, 0, 0], [0.6, -8, 0, 0], [0.8, 0, 0, 0]], CR)),
  [hatG.uuid]: bone("hat", keyframes("rotation", [[0, 0, 0, 0], [0.25, 10, 0, 0], [0.45, -6, 0, 0], [0.65, 10, 0, 0], [0.8, 0, 0, 0]], CR)),
};

// work 1.8 s: iaido — crouch, then a fast horizontal slash with the drawn
// blade (linear snap into a held pose), then a smooth return to rest.
const work = {
  [rootG.uuid]: bone(
    "root",
    keyframes(
      "position",
      [
        [0, 0, 0, 0],
        [0.5, 0, -0.8, 0],
        [0.7, 0, -0.8, 0],
        [0.95, 0, 0.5, 0],
        [1.2, 0, -0.2, 0],
        [1.5, 0, 0, 0],
        [1.8, 0, 0, 0],
      ],
      CR,
    ),
  ),
  [bodyG.uuid]: bone(
    "body",
    keyframes(
      "rotation",
      [
        [0, 0, 0, 0],
        [0.5, 8, 0, 0],
        [0.7, 8, 0, 0],
        [0.9, -6, 0, 0],
        [1.2, 0, 0, 0],
        [1.8, 0, 0, 0],
      ],
      CR,
    ),
  ),
  [bladeG.uuid]: bone(
    "blade_work",
    keyframes(
      "rotation",
      [
        [0, 0, -120, 0],
        [0.5, 0, -120, 0],
        [0.7, 0, -120, 0],
        [0.9, 0, 110, 0, "linear"],
        [1.15, 0, 110, 0],
        [1.5, 0, -120, 0],
        [1.8, 0, -120, 0],
      ],
      CR,
    ),
  ),
  [armRG.uuid]: bone("arm_r", keyframes("rotation", [[0, 0, 0, 0], [0.5, -30, 0, 0], [0.7, -30, 0, 0], [0.9, 20, 0, 0], [1.5, 0, 0, 0], [1.8, 0, 0, 0]], CR)),
  [armLG.uuid]: bone("arm_l", keyframes("rotation", [[0, 0, 0, 0], [0.5, 14, 0, 0], [0.9, -18, 0, 0], [1.5, 0, 0, 0], [1.8, 0, 0, 0]], CR)),
  [headG.uuid]: bone("head", keyframes("rotation", [[0, 0, 0, 0], [0.5, 14, 0, 0], [0.7, 14, 0, 0], [0.9, -10, 0, 0], [1.2, -10, 0, 0], [1.5, 0, 0, 0], [1.8, 0, 0, 0]], CR)),
  [hatG.uuid]: bone("hat", keyframes("rotation", [[0, 0, 0, 0], [0.7, 8, 0, 0], [0.9, -12, 0, 0], [1.15, -10, 0, 0], [1.5, 0, 0, 0], [1.8, 0, 0, 0]], CR)),
  [legLG.uuid]: bone("leg_l", keyframes("rotation", [[0, 0, 0, 0], [0.5, 18, 0, 0], [0.7, 18, 0, 0], [0.9, -14, 0, 0], [1.5, 0, 0, 0], [1.8, 0, 0, 0]], CR)),
  [legRG.uuid]: bone("leg_r", keyframes("rotation", [[0, 0, 0, 0], [0.5, 18, 0, 0], [0.7, 18, 0, 0], [0.9, -14, 0, 0], [1.5, 0, 0, 0], [1.8, 0, 0, 0]], CR)),
};

// eat 1.2 s: the tongue flicks out three times to snap up a fly.
const eat = {
  [bodyG.uuid]: bone("body", keyframes("scale", [[0, 1, 1, 1], [0.6, 1, 1.02, 1], [1.2, 1, 1, 1]], CR)),
  [headG.uuid]: bone(
    "head",
    keyframes(
      "rotation",
      [
        [0, 0, 0, 0],
        [0.2, 9, 0, 0],
        [0.4, -4, 0, 0],
        [0.6, 9, 0, 0],
        [0.8, -4, 0, 0],
        [1.0, 9, 0, 0],
        [1.2, 0, 0, 0],
      ],
      CR,
    ),
  ),
  [tongueG.uuid]: bone(
    "tongue_eat",
    keyframes(
      "position",
      [
        [0, 0, 0, 0],
        [0.16, 0, 0, 0, "linear"],
        [0.28, 0, 0, -3.5, "linear"],
        [0.4, 0, 0, 0, "linear"],
        [0.56, 0, 0, -3.5, "linear"],
        [0.68, 0, 0, 0, "linear"],
        [0.84, 0, 0, -3.5, "linear"],
        [0.96, 0, 0, 0, "linear"],
        [1.2, 0, 0, 0],
      ],
      CR,
    ),
  ),
  [hatG.uuid]: bone("hat", keyframes("rotation", [[0, 0, 0, 0], [0.28, 6, 0, 0], [0.5, -4, 0, 0], [0.9, 6, 0, 0], [1.2, 0, 0, 0]], CR)),
  [armLG.uuid]: bone("arm_l", keyframes("rotation", [[0, 0, 0, 0], [0.3, -8, 0, 0], [0.7, 6, 0, 0], [1.2, 0, 0, 0]], CR)),
  [armRG.uuid]: bone("arm_r", keyframes("rotation", [[0, 0, 0, 0], [0.3, -8, 0, 0], [0.7, 6, 0, 0], [1.2, 0, 0, 0]], CR)),
};

// hurt 0.35 s once: instant flinch, overshoot past rest, settle.
const hurt = {
  [rootG.uuid]: bone("root", keyframes("position", [[0, 0, 0.5, 0], [0.15, 0, 0, 0], [0.35, 0, 0, 0]], CR)),
  [bodyG.uuid]: bone("body", keyframes("scale", [[0, 1.12, 0.85, 1.12], [0.15, 0.97, 1.04, 0.97], [0.35, 1, 1, 1]], CR)),
  [headG.uuid]: bone("head", keyframes("rotation", [[0, -20, 0, 0], [0.15, 6, 0, 0], [0.35, 0, 0, 0]], CR)),
  [hatG.uuid]: bone("hat", keyframes("rotation", [[0, -14, 0, 0], [0.15, 5, 0, 0], [0.35, 0, 0, 0]], CR)),
  [armLG.uuid]: bone("arm_l", keyframes("rotation", [[0, -26, 0, 0], [0.15, 5, 0, 0], [0.35, 0, 0, 0]], CR)),
  [armRG.uuid]: bone("arm_r", keyframes("rotation", [[0, -26, 0, 0], [0.15, 5, 0, 0], [0.35, 0, 0, 0]], CR)),
  [legLG.uuid]: bone("leg_l", keyframes("rotation", [[0, -10, 0, 0], [0.15, 3, 0, 0], [0.35, 0, 0, 0]], CR)),
  [legRG.uuid]: bone("leg_r", keyframes("rotation", [[0, -10, 0, 0], [0.15, 3, 0, 0], [0.35, 0, 0, 0]], CR)),
};

b.animation("idle", "loop", 4.0, idle);
b.animation("walk", "loop", 0.8, walk);
b.animation("work", "loop", 1.8, work);
b.animation("eat", "loop", 1.2, eat);
b.animation("hurt", "once", 0.35, hurt);

b.write(bbmodelOut, pngOut, "frog");
console.log(`wrote ${bbmodelOut}`);
console.log(`wrote ${pngOut}`);
