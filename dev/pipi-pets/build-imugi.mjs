// Builds the vanilla-style "이무기" (imugi, a pre-dragon serpent) pet.
// Head + 7 chained body segment bones (each a child of the previous), front -Z.
import path from "node:path";
import { fileURLToPath } from "node:url";
import { VBuilder, keyframes, bone } from "./lib/vanilla.mjs";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "../..");
const bbmodelOut = path.join(root, "dev/pipi-facilities/assets/pipifacilities/pets/imugi.bbmodel");
const pngOut = path.join(root, "dev/pipi-pets/out/imugi.png");

const b = new VBuilder({ width: 64, height: 64 });

// --- materials -------------------------------------------------------------
b.material("scale", [["#1E3A44", 4], ["#24485A", 3], ["#1A2E38", 3]]);
b.material("sheen", [["#3A7A80", 3], ["#2E6A72", 1]]);
b.material("belly", [["#D8D0A8", 4], ["#C8BE96", 2], ["#E6DEBC", 1]]);
b.material("bellyd", [["#B8AE86", 3], ["#A89E78", 1]]);
b.material("horn", [["#E6D8A8", 3], ["#CDBE88", 1]]);
b.material("gold", [["#F5C518", 1]]);
b.material("pupil", [["#10181C", 1]]);
b.material("whisk", [["#D8C070", 3], ["#C0A850", 1]]);
b.material("tongue", [["#C8283A", 3], ["#A81C2E", 1]]);
b.material("fang", [["#F4F0E0", 1]]);

// Scale pattern: staggered teal sheen specks over the dark-blue noise.
const scaleFn = (x, y) => ((x + (y % 2 ? 2 : 0)) % 4 === 0 ? "sheen" : "scale");
const sideFn = (x, y, w, h) => (y === h - 1 ? "belly" : scaleFn(x, y));
const plates = (x, y) => (y % 2 === 0 ? "belly" : "bellyd");

// --- head (z -20..-12) -------------------------------------------------------
const skull = b.cube("skull_cube", [-3, 0, -17], [3, 5, -12], {
  mat: "scale",
  legend: { s: "scale", h: "sheen", g: "gold", k: "pupil", p: "belly" },
  faces: {
    north: ["ssssss", "sgssgs", "sksskh".replace("sksskh", "ssssss"), "ssssss", "pppppp"],
    east: ["sssss", "sssgg", "sssgk", "sssss", "ppppp"],
    west: ["sssss", "ggsss", "kgsss", "sssss", "ppppp"],
    up: scaleFn,
    down: plates,
    south: scaleFn,
  },
});
const snout = b.cube("snout_cube", [-2, 0, -20], [2, 3, -17], {
  mat: "scale",
  legend: { s: "scale", k: "pupil", p: "belly", f: "fang" },
  faces: {
    north: ["skks", "kkkk", "pppp"],
    east: ["sss", "sfs", "ppp"],
    west: ["sss", "sfs", "ppp"],
    up: scaleFn,
    down: "belly",
    south: scaleFn,
  },
});
const tongue = b.cube("tongue_cube", [-1, 0, -23], [1, 1, -20], {
  mat: "tongue",
  legend: { r: "tongue" },
  faces: { up: ["r.", "rr", "rr"], down: "tongue", north: "tongue", south: "tongue", east: "tongue", west: "tongue" },
});
const hornCubes = [];
for (const [nm, x] of [["l", -3], ["r", 2]]) {
  hornCubes.push(b.cube(`horn_${nm}_base`, [x, 5, -16], [x + 1, 7, -15], { mat: "horn" }));
  hornCubes.push(b.cube(`horn_${nm}_tip`, [x, 7, -15], [x + 1, 8, -14], { mat: "horn" }));
}
// whiskers: two chained bones per side, droop outward and down.
const whisk = (nm, sign) => {
  const x1 = sign < 0 ? -8 : 2;
  const x2 = sign < 0 ? -13 : 8;
  const c1 = b.cube(`whisker_${nm}_1`, [x1, 2, -19], [x1 + 6, 3, -18], { mat: "whisk" });
  const c2 = b.cube(`whisker_${nm}_2`, [x2, 1, -19], [x2 + 5, 2, -18], { mat: "whisk" });
  const w2 = b.group(`whisker_${nm}2`, [sign < 0 ? -8 : 8, 2, -18.5], [c2]);
  const w1 = b.group(`whisker_${nm}`, [sign < 0 ? -2 : 2, 2, -18.5], [c1, w2]);
  return [w1, w2];
};
const [whiskLG, whiskL2G] = whisk("l", -1);
const [whiskRG, whiskR2G] = whisk("r", 1);

// --- body segments --------------------------------------------------------------
const L = [5, 5, 5, 5, 4, 4, 4];
const SW = [6, 6, 4, 4, 4, 2, 2];
const SH = [5, 5, 4, 4, 3, 3, 2];
const segCube = [];
const segZ = [];
{
  let z = -12;
  for (let k = 0; k < 7; k++) {
    segZ.push(z);
    const hw = SW[k] / 2;
    const h = SH[k];
    const body = b.cube(`seg${k + 1}_cube`, [-hw, 0, z], [hw, h, z + L[k]], {
      mat: "scale",
      faces: { up: scaleFn, down: plates, north: sideFn, south: sideFn, east: sideFn, west: sideFn },
    });
    const ridge = b.cube(`seg${k + 1}_ridge`, [-1, h, z + 1], [1, h + 1, z + L[k] - 1], { mat: "sheen" });
    segCube.push([body, ridge]);
    z += L[k];
  }
}

// --- bones -----------------------------------------------------------------
const tongueG = b.group("tongue", [0, 0.5, -20], [tongue]);
const headG = b.group("head", [0, 2.5, -12], [skull, snout, tongueG, ...hornCubes, whiskLG, whiskRG]);
const segG = new Array(7);
for (let k = 6; k >= 0; k--) {
  const kids = [...segCube[k]];
  if (k === 0) kids.push(headG);
  if (k < 6) kids.push(segG[k + 1]);
  segG[k] = b.group(`seg${k + 1}`, [0, SH[k] / 2, segZ[k]], kids);
}
const rootG = b.group("root", [0, 0, 0], [segG[0]]);
b.outliner.push(rootG);

// --- animations ------------------------------------------------------------
const CR = "catmullrom";
const TAU = Math.PI * 2;
const r2 = (v) => +v.toFixed(3);

// Sample a per-segment function f(k, t) -> [x, y, z] on a regular grid; loops by
// construction when f is periodic in `len` (last key copies the first).
function sample(len, n, f) {
  const keys = [];
  for (let i = 0; i <= n; i++) {
    const t = i === n ? 0 : (i * len) / n;
    const v = f(t).map(r2);
    keys.push([r2((i * len) / n), ...v]);
  }
  return keys;
}
const rot = (len, n, f) => keyframes("rotation", sample(len, n, f), CR);

// idle 4 s: slow breathing ripple down the body, head sway, tongue flick, whiskers drift.
const IL = 4;
const idle = {};
for (let k = 0; k < 7; k++) {
  idle[segG[k].uuid] = bone(
    `seg${k + 1}`,
    rot(IL, 16, (t) => [0, 3 * Math.sin((TAU * t) / IL - 0.7 * k), 0]),
    keyframes("scale", sample(IL, 16, (t) => [1, 1 + 0.035 * Math.max(0, Math.sin((TAU * t) / IL - 0.6 * k)), 1]), CR),
  );
}
idle[headG.uuid] = bone(
  "head",
  keyframes("rotation", [[0, 0, 0, 0], [0.8, -6, 8, 0], [1.4, -6, 8, 0], [2.2, 4, 0, 0], [2.8, 0, -8, 0], [3.3, 0, -8, 0], [4, 0, 0, 0]], CR),
);
idle[tongueG.uuid] = bone(
  "tongue",
  keyframes("scale", [[0, 1, 1, 0.2], [1.5, 1, 1, 0.2], [1.6, 1, 1, 1.8, "linear"], [1.9, 1, 1, 1.0], [2.0, 1, 1, 1.8, "linear"], [2.3, 1, 1, 0.2], [4, 1, 1, 0.2]], CR),
);
for (const [g, g2, s] of [[whiskLG, whiskL2G, 1], [whiskRG, whiskR2G, -1]]) {
  idle[g.uuid] = bone(g.name, rot(IL, 8, (t) => [0, s * 6 * Math.sin((TAU * t) / IL), 5 * Math.sin((TAU * t) / IL - 0.5)]));
  idle[g2.uuid] = bone(g2.name, rot(IL, 8, (t) => [0, s * 10 * Math.sin((TAU * t) / IL - 0.9), 8 * Math.sin((TAU * t) / IL - 1.2)]));
}

// walk 0.7 s: fast slithering — yaw sine wave, phase lag grows toward the tail.
const WL = 0.7;
const walk = {};
for (let k = 0; k < 7; k++) {
  const amp = 15 + 2 * k;
  walk[segG[k].uuid] = bone(
    `seg${k + 1}`,
    rot(WL, 14, (t) => [0, amp * Math.sin((TAU * t) / WL - 0.95 * k), 0]),
    keyframes("position", sample(WL, 14, (t) => [0, 0, 0]), CR),
  );
}
walk[headG.uuid] = bone("head", rot(WL, 14, (t) => [3 * Math.sin((TAU * 2 * t) / WL), 14 * Math.sin((TAU * t) / WL + 1.0), 0]));
for (const [g, g2, s] of [[whiskLG, whiskL2G, 1], [whiskRG, whiskR2G, -1]]) {
  walk[g.uuid] = bone(g.name, rot(WL, 14, (t) => [0, s * 14, 0]));
  walk[g2.uuid] = bone(g2.name, rot(WL, 14, (t) => [0, s * (16 + 10 * Math.sin((TAU * t) / WL)), 8 * Math.sin((TAU * t) / WL - 0.8)]));
}

// work 3.2 s: coil and rear the head up, whiskers sway, then settle back.
const WK = 3.2;
const rearX = [-25, -25, -20, -10, 40, 25, 15];
let cum = 0;
let H = 0;
for (let k = 0; k < 7; k++) {
  cum += rearX[k];
  H += L[k] * Math.sin((Math.abs(Math.min(cum, 0)) * Math.PI) / 180);
}
// Rise factor: tiny anticipation dip, overshoot, hold, ease down.
const smooth = (a, b2, t) => {
  const u = Math.min(1, Math.max(0, (t - a) / (b2 - a)));
  return u * u * (3 - 2 * u);
};
const rise = (t) => -0.12 * smooth(0, 0.3, t) * (1 - smooth(0.3, 0.6, t)) + 1.06 * smooth(0.3, 1.1, t) - 0.06 * smooth(1.1, 1.4, t) - smooth(2.4, 3.2, t);
const work = {};
for (let k = 0; k < 7; k++) {
  work[segG[k].uuid] = bone(
    `seg${k + 1}`,
    rot(WK, 32, (t) => [rearX[k] * rise(t), 4 * Math.sin((TAU * 2 * t) / WK - 0.5 * k) * smooth(1, 1.6, t) * (1 - smooth(2.4, 3.0, t)), 0]),
  );
}
work[rootG.uuid] = bone("root", keyframes("position", sample(WK, 32, (t) => [0, H * rise(t), 0]), CR));
work[headG.uuid] = bone("head", rot(WK, 32, (t) => [38 * rise(t) - 5 * Math.sin((TAU * 2 * t) / WK) * smooth(1, 1.6, t), 10 * Math.sin((TAU * 2 * t) / WK) * smooth(1.1, 1.8, t) * (1 - smooth(2.4, 3.0, t)), 0]));
work[tongueG.uuid] = bone(
  "tongue",
  keyframes("scale", [[0, 1, 1, 0.2], [1.3, 1, 1, 0.2], [1.4, 1, 1, 1.8, "linear"], [1.6, 1, 1, 0.5], [1.7, 1, 1, 1.8, "linear"], [1.9, 1, 1, 0.2], [3.2, 1, 1, 0.2]], CR),
);
for (const [g, g2, s] of [[whiskLG, whiskL2G, 1], [whiskRG, whiskR2G, -1]]) {
  work[g.uuid] = bone(g.name, rot(WK, 16, (t) => [0, s * (8 + 14 * Math.sin((TAU * 2 * t) / WK)) * smooth(0.6, 1.2, t) * (1 - smooth(2.4, 3.0, t)), 6 * Math.sin((TAU * 2 * t) / WK - 0.6)]));
  work[g2.uuid] = bone(g2.name, rot(WK, 16, (t) => [0, s * 14 * Math.sin((TAU * 2 * t) / WK - 1.0), 12 * Math.sin((TAU * 2 * t) / WK - 1.4)]));
}

// eat 1.6 s: head dips down to the food, chews, body shifts slightly forward.
const eat = {
  [headG.uuid]: bone(
    "head",
    keyframes("rotation", [[0, 28, 0, 0], [0.3, 28, 0, 0], [0.5, 36, 0, 0], [0.7, 28, 0, 0], [0.9, 36, 0, 0], [1.1, 28, 0, 0], [1.3, 22, 0, 0], [1.6, 28, 0, 0]], CR),
  ),
  [tongueG.uuid]: bone("tongue", keyframes("scale", [[0, 1, 1, 0.2], [0.12, 1, 1, 1.8, "linear"], [0.3, 1, 1, 0.2], [1.6, 1, 1, 0.2]], CR)),
};
for (let k = 0; k < 7; k++) {
  eat[segG[k].uuid] = bone(`seg${k + 1}`, rot(1.6, 8, (t) => [0, 2.5 * Math.sin((TAU * t) / 1.6 - 0.7 * k), 0]));
}

// hurt 0.4 s once: recoil with a body shudder, overshoot and settle.
const hurt = {
  [headG.uuid]: bone("head", keyframes("rotation", [[0, -24, 14, 0], [0.25, 6, -4, 0], [0.4, 0, 0, 0]], CR)),
  [rootG.uuid]: bone("root", keyframes("scale", [[0, 1.06, 0.88, 1.06], [0.25, 0.98, 1.03, 0.98], [0.4, 1, 1, 1]], CR)),
};
for (let k = 0; k < 7; k++) {
  const a = 14 - k * 1.2;
  hurt[segG[k].uuid] = bone(`seg${k + 1}`, keyframes("rotation", [[0, 0, a * (k % 2 ? -1 : 1), 0], [0.25, 0, -a * 0.4 * (k % 2 ? -1 : 1), 0], [0.4, 0, 0, 0]], CR));
}
for (const [g, g2, s] of [[whiskLG, whiskL2G, 1], [whiskRG, whiskR2G, -1]]) {
  hurt[g.uuid] = bone(g.name, keyframes("rotation", [[0, 0, s * -30, 0], [0.25, 0, s * 8, 0], [0.4, 0, 0, 0]], CR));
}

b.animation("idle", "loop", IL, idle);
b.animation("walk", "loop", WL, walk);
b.animation("work", "loop", WK, work);
b.animation("eat", "loop", 1.6, eat);
b.animation("hurt", "once", 0.4, hurt);

b.write(bbmodelOut, pngOut, "imugi");
console.log(`wrote ${bbmodelOut}`);
console.log(`wrote ${pngOut}`);
