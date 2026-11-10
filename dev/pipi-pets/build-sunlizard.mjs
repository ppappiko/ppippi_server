// Builds the vanilla-style "파로" (sun lizard) pet: a tailless yellow lizard
// with a burning flame where the tail would be. Front faces -Z.
import path from "node:path";
import { fileURLToPath } from "node:url";
import { VBuilder, keyframes, bone } from "./lib/vanilla.mjs";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "../..");
const bbmodelOut = path.join(root, "dev/pipi-facilities/assets/pipifacilities/pets/sunlizard.bbmodel");
const pngOut = path.join(root, "dev/pipi-pets/out/sunlizard.png");

const b = new VBuilder({ width: 64, height: 64 });

// --- materials -------------------------------------------------------------
b.material("scale", [["#E8C030", 5], ["#D8A820", 3], ["#F2D458", 2], ["#C89818", 1]]);
b.material("pale", [["#F6E6A0", 4], ["#EAD688", 2], ["#FFF2BC", 1]]);
b.material("stripe", [["#E08A1C", 4], ["#EF9C2C", 2], ["#D07A14", 1]]);
b.material("claw", [["#8A5A14", 3], ["#6E4510", 1]]);
b.material("eyeg", [["#FFF0A0", 1]]);
b.material("pupil", [["#2A1A10", 1]]);
b.material("tongue", [["#D8323C", 3], ["#B8222C", 1]]);
b.material("fred", [["#D8321A", 4], ["#C02410", 2], ["#E8451E", 2]]);
b.material("forange", [["#F28A1C", 4], ["#FF9A2A", 2], ["#E67A10", 1]]);
b.material("fyellow", [["#FFD94A", 4], ["#FFE770", 2], ["#FFC832", 1]]);
b.material("fwhite", [["#FFF6B8", 3], ["#FFFFE0", 1]]);
b.material("ray", [["#FFC83A", 3], ["#FF9E24", 2], ["#FFE070", 1]]);

// --- legs: sprawled, thigh out to the side, shin down, toes forward -----------
const makeLeg = (prefix, left, z) => {
  const xa = left ? -6 : 3;
  const thigh = b.cube(`${prefix}_thigh`, [xa, 2, z], [xa + 3, 4, z + 2], {
    mat: "scale",
    faces: { up: "stripe", down: "scale" },
  });
  const sxa = left ? -6 : 4;
  const shin = b.cube(`${prefix}_shin`, [sxa, 0, z], [sxa + 2, 2, z + 2], {
    mat: "scale",
    faces: { down: "claw" },
  });
  const toes = b.cube(`${prefix}_toes`, [sxa, 0, z - 1], [sxa + 2, 1, z], { mat: "claw" });
  return [thigh, shin, toes];
};
const flC = makeLeg("leg_fl", true, -5);
const frC = makeLeg("leg_fr", false, -5);
const blC = makeLeg("leg_bl", true, 3);
const brC = makeLeg("leg_br", false, 3);

// --- body -------------------------------------------------------------------
const side = (x, y, w, h) => (y === h - 1 ? "pale" : "scale");
const body = b.cube("body_cube", [-3, 2, -5], [3, 5, 5], {
  mat: "scale",
  faces: {
    north: side, south: side, east: side, west: side,
    up: (x) => (x === 2 || x === 3 ? "stripe" : "scale"),
    down: "pale",
  },
});

// --- head -------------------------------------------------------------------
const head = b.cube("head_cube", [-3, 2, -9], [3, 5, -5], {
  mat: "scale",
  legend: { s: "scale", g: "eyeg", k: "pupil", p: "pale" },
  faces: {
    north: ["gssssg", "kssssk", "ssssss"],
    east: ["sssg", "sssk", "pppp"],
    west: ["gsss", "ksss", "pppp"],
    up: (x) => (x === 2 || x === 3 ? "stripe" : "scale"),
    down: "pale",
    south: "scale",
  },
});
const snout = b.cube("snout_cube", [-2, 2, -12], [2, 4, -9], {
  mat: "scale",
  legend: { s: "scale", k: "pupil", p: "pale" },
  faces: {
    north: ["skks", "pppp"],
    east: ["sss", "ppp"],
    west: ["sss", "ppp"],
    up: "scale",
    down: "pale",
    south: "scale",
  },
});
const tongue = b.cube("tongue_cube", [-1, 2, -14], [1, 3, -12], {
  mat: "tongue",
  legend: { r: "tongue" },
  faces: { up: ["r.", "rr"], down: "tongue", north: "tongue", south: "tongue", east: "tongue", west: "tongue" },
});

// --- sun-ray crest (full bright) ---------------------------------------------
const rayH = [1, 2, 3, 3, 2, 1];
const rays = rayH.map((h, i) => b.cube(`ray_${i}`, [-3 + i, 5, -7], [-2 + i, 5 + h, -6], { mat: "ray" }));

// --- flame where the tail would be --------------------------------------------
const f1 = b.cube("flame_1", [-2, 2, 5], [2, 5, 8], { mat: "fred" });
const f2 = b.cube("flame_2", [-2, 4, 5], [2, 7, 8], { mat: "forange" });
const f3 = b.cube("flame_3", [-1, 7, 5], [1, 10, 8], { mat: "fyellow" });
const f4 = b.cube("flame_4", [-1, 10, 6], [0, 13, 7], { mat: "fwhite" });
const f5 = b.cube("flame_5", [-3, 2, 5], [-2, 4, 7], { mat: "forange" });
const f6 = b.cube("flame_6", [2, 3, 5], [3, 6, 7], { mat: "fyellow" });
const f7 = b.cube("flame_7", [-1, 2, 8], [1, 4, 10], { mat: "forange" });

// --- bones -----------------------------------------------------------------
const tongueG = b.group("tongue", [0, 2.5, -12], [tongue]);
const crestG = b.group("crest_glow", [0, 5, -6.5], rays);
const headG = b.group("head", [0, 3, -8], [head, snout, tongueG, crestG]);
const flameG = b.group("flame_glow", [0, 2, 6], [f1, f2, f3, f4, f5, f6, f7]);
const bodyG = b.group("body", [0, 3.5, 0], [body, headG, flameG]);
const legFlG = b.group("leg_fl", [-3, 3, -4], flC);
const legFrG = b.group("leg_fr", [3, 3, -4], frC);
const legBlG = b.group("leg_bl", [-3, 3, 4], blC);
const legBrG = b.group("leg_br", [3, 3, 4], brC);
const rootG = b.group("root", [0, 0, 0], [bodyG, legFlG, legFrG, legBlG, legBrG]);
b.outliner.push(rootG);

// --- animations ------------------------------------------------------------
const CR = "catmullrom";

// Flame flicker: scale keys every `step`, deterministic wobble, loops seamlessly.
function flickerScale(len, step, big = 1) {
  const n = Math.round(len / step);
  const keys = [];
  for (let i = 0; i <= n; i++) {
    const w = i === n ? 0 : i;
    const s = Math.sin(w * 2.3) * 0.6 + Math.sin(w * 4.1 + 1) * 0.4;
    const sy = big * (1.08 + 0.17 * s);
    const sxz = big * (1 - 0.08 * s);
    keys.push([+(i * step).toFixed(3), +sxz.toFixed(3), +sy.toFixed(3), +sxz.toFixed(3)]);
  }
  return keyframes("scale", keys, CR);
}
function flickerRot(len, step, amp) {
  const n = Math.round(len / step);
  const keys = [];
  for (let i = 0; i <= n; i++) {
    const w = i === n ? 0 : i;
    keys.push([+(i * step).toFixed(3), amp * Math.sin(w * 1.7), 0, amp * Math.sin(w * 2.9 + 1)]);
  }
  return keyframes("rotation", keys, CR);
}
const flame = (len, step, big = 1, rotAmp = 6) =>
  bone("flame_glow", flickerScale(len, step, big), flickerRot(len, step, rotAmp));

// idle 3.6 s: breathing, head bob, tongue flick, crest shimmer.
const idle = {
  [bodyG.uuid]: bone("body", keyframes("scale", [[0, 1, 1, 1], [0.9, 1, 1.04, 1], [1.8, 1, 1, 1], [2.7, 1, 1.04, 1], [3.6, 1, 1, 1]], CR)),
  [headG.uuid]: bone(
    "head",
    keyframes("rotation", [[0, 0, 0, 0], [0.6, -8, 0, 0], [1.0, 6, 0, 0], [1.3, -4, 0, 0], [1.7, 0, 0, 0], [2.6, 0, 10, 0], [3.1, 0, 10, 0], [3.6, 0, 0, 0]], CR),
  ),
  [tongueG.uuid]: bone(
    "tongue",
    keyframes("scale", [[0, 1, 1, 1], [1.4, 1, 1, 1], [1.5, 1, 1, 3, "linear"], [1.65, 1, 1, 1, "linear"], [1.8, 1, 1, 3, "linear"], [1.95, 1, 1, 1], [3.6, 1, 1, 1]], CR),
  ),
  [crestG.uuid]: bone("crest_glow", keyframes("scale", [[0, 1, 1, 1], [0.9, 1, 1.25, 1], [1.8, 1, 1, 1], [2.7, 1, 1.25, 1], [3.6, 1, 1, 1]], CR)),
  [flameG.uuid]: flame(3.6, 0.3),
};

// walk 0.5 s: fast diagonal scuttle with S-curve body sway, flame streams behind.
const SW = 40;
const lg = (name, s) =>
  bone(name, keyframes("rotation", [[0, s * SW, 0, 0], [0.25, -s * SW, 0, 0], [0.5, s * SW, 0, 0]], CR));
const walk = {
  [legFlG.uuid]: lg("leg_fl", 1),
  [legBrG.uuid]: lg("leg_br", 1),
  [legFrG.uuid]: lg("leg_fr", -1),
  [legBlG.uuid]: lg("leg_bl", -1),
  [bodyG.uuid]: bone(
    "body",
    keyframes("rotation", [[0, 0, 12, 0], [0.125, 0, 0, 0], [0.25, 0, -12, 0], [0.375, 0, 0, 0], [0.5, 0, 12, 0]], CR),
    keyframes("position", [[0, 0, 0, 0], [0.125, 0, -0.3, 0], [0.25, 0, 0, 0], [0.375, 0, -0.3, 0], [0.5, 0, 0, 0]], CR),
  ),
  [headG.uuid]: bone("head", keyframes("rotation", [[0, 0, -16, 0], [0.125, 0, 0, 0], [0.25, 0, 16, 0], [0.375, 0, 0, 0], [0.5, 0, -16, 0]], CR)),
  [flameG.uuid]: flame(0.5, 0.125, 1.1, 10),
  [crestG.uuid]: bone("crest_glow", keyframes("rotation", [[0, -6, 0, 0], [0.25, 6, 0, 0], [0.5, -6, 0, 0]], CR)),
};

// work 2.4 s: basking — body lowers, head lifts to the sun, flame flares big.
const work = {
  [bodyG.uuid]: bone(
    "body",
    keyframes("position", [[0, 0, 0, 0], [0.4, 0, 0.3, 0], [0.9, 0, -0.6, 0], [1.8, 0, -0.6, 0], [2.4, 0, 0, 0]], CR),
    keyframes("rotation", [[0, 0, 0, 0], [0.4, 3, 0, 0], [0.9, -8, 0, 0], [1.8, -8, 0, 0], [2.4, 0, 0, 0]], CR),
  ),
  [headG.uuid]: bone(
    "head",
    keyframes("rotation", [[0, 0, 0, 0], [0.4, 8, 0, 0], [0.9, -22, 0, 0], [1.3, -18, 6, 0], [1.8, -22, -6, 0], [2.4, 0, 0, 0]], CR),
  ),
  [legFlG.uuid]: bone("leg_fl", keyframes("rotation", [[0, 0, 0, 0], [0.9, 0, 0, -12], [1.8, 0, 0, -12], [2.4, 0, 0, 0]], CR)),
  [legFrG.uuid]: bone("leg_fr", keyframes("rotation", [[0, 0, 0, 0], [0.9, 0, 0, 12], [1.8, 0, 0, 12], [2.4, 0, 0, 0]], CR)),
  [flameG.uuid]: bone(
    "flame_glow",
    keyframes(
      "scale",
      [[0, 1, 1, 1], [0.4, 0.9, 0.9, 0.9], [0.6, 1.4, 1.9, 1.4], [0.8, 1.5, 1.7, 1.5], [1.0, 1.35, 2.0, 1.35], [1.2, 1.5, 1.75, 1.5], [1.4, 1.35, 2.0, 1.35], [1.6, 1.5, 1.75, 1.5], [1.8, 1.4, 1.9, 1.4], [2.1, 1.1, 1.2, 1.1], [2.4, 1, 1, 1]],
      CR,
    ),
    flickerRot(2.4, 0.2, 7),
  ),
  [crestG.uuid]: bone("crest_glow", keyframes("scale", [[0, 1, 1, 1], [0.9, 1.1, 1.8, 1.1], [1.8, 1.1, 1.8, 1.1], [2.4, 1, 1, 1]], CR)),
  [tongueG.uuid]: bone("tongue", keyframes("scale", [[0, 1, 1, 1], [1.1, 1, 1, 1], [1.2, 1, 1, 3, "linear"], [1.35, 1, 1, 1], [2.4, 1, 1, 1]], CR)),
};

// eat 1.6 s: head down, two chews, tongue grabs, flame ticks gently.
const eat = {
  [headG.uuid]: bone(
    "head",
    keyframes("rotation", [[0, 30, 0, 0], [0.3, 30, 0, 0], [0.5, 36, 0, 0], [0.7, 30, 0, 0], [0.9, 36, 0, 0], [1.1, 30, 0, 0], [1.3, 24, 0, 0], [1.6, 30, 0, 0]], CR),
  ),
  [bodyG.uuid]: bone("body", keyframes("rotation", [[0, 6, 0, 0], [1.6, 6, 0, 0]], CR)),
  [tongueG.uuid]: bone("tongue", keyframes("scale", [[0, 1, 1, 1], [0.15, 1, 1, 3, "linear"], [0.3, 1, 1, 1], [1.6, 1, 1, 1]], CR)),
  [flameG.uuid]: flame(1.6, 0.2, 0.9, 5),
};

// hurt 0.4 s once: flinch, flame puffs, overshoot, settle.
const hurt = {
  [headG.uuid]: bone("head", keyframes("rotation", [[0, -22, 0, 0], [0.25, 6, 0, 0], [0.4, 0, 0, 0]], CR)),
  [bodyG.uuid]: bone("body", keyframes("scale", [[0, 1.08, 0.88, 1.08], [0.25, 0.97, 1.03, 0.97], [0.4, 1, 1, 1]], CR)),
  [flameG.uuid]: bone("flame_glow", keyframes("scale", [[0, 1.6, 1.6, 1.6], [0.25, 0.85, 0.9, 0.85], [0.4, 1, 1, 1]], CR)),
  [crestG.uuid]: bone("crest_glow", keyframes("scale", [[0, 1, 1.6, 1], [0.25, 1, 0.9, 1], [0.4, 1, 1, 1]], CR)),
};

b.animation("idle", "loop", 3.6, idle);
b.animation("walk", "loop", 0.5, walk);
b.animation("work", "loop", 2.4, work);
b.animation("eat", "loop", 1.6, eat);
b.animation("hurt", "once", 0.4, hurt);

b.write(bbmodelOut, pngOut, "sunlizard");
console.log(`wrote ${bbmodelOut}`);
console.log(`wrote ${pngOut}`);
