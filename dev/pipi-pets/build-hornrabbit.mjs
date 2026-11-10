// Builds the vanilla-style "설토" (hornrabbit) pet model.
// Integer-pixel geometry, 1 texel per model pixel, front faces -Z, feet on y=0.
// Vanilla-rabbit quadruped (crouched body, big hind legs, long ears, puff tail).
// Innocent white rabbit with a black cross on the forehead; when working the `angry_work`
// overlay (red brows, narrowed eyes, a fang) replaces the `eyes_rest` eyes.
import path from "node:path";
import { fileURLToPath } from "node:url";
import { VBuilder, keyframes, bone } from "./lib/vanilla.mjs";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "../..");
const bbmodelOut = path.join(root, "dev/pipi-facilities/assets/pipifacilities/pets/hornrabbit.bbmodel");
const pngOut = path.join(root, "dev/pipi-pets/out/hornrabbit.png");

const b = new VBuilder({ width: 64, height: 64 });

// --- materials -------------------------------------------------------------
b.material("fur", [["#F2EFEA", 5], ["#E6E0D8", 3], ["#FFFFFF", 2]]);
b.material("shadow", [["#D8D2C8", 4], ["#C8C2B8", 2], ["#E0DAD0", 1]]);
b.material("pink", [["#F0B8C8", 3], ["#E0A0B4", 2], ["#F7C6D4", 1]]);
b.material("cross", [["#1A1A1A", 3], ["#222222", 1]]);
b.material("eye", [["#2B1D18", 1]]);
b.material("white", [["#FFFFFF", 1]]);
b.material("red", [["#C0392B", 2], ["#A62B20", 1]]);
b.material("angereye", [["#7A2018", 2], ["#5E1810", 1]]);
b.material("mouth", [["#B08A80", 2], ["#9A7A70", 1]]);
b.material("nose", [["#E8909E", 2], ["#D87C8A", 1]]);

// --- body (long, low, crouched) --------------------------------------------
const body = b.cube("body_cube", [-4, 3, -3], [4, 8, 5], {
  mat: "fur",
  faces: { up: "fur", down: "shadow", north: "fur", south: "fur", east: "fur", west: "fur" },
});

// --- head (flat vanilla face; eyes/mouth are separate overlay cubes) --------
const head = b.cube("head_cube", [-3, 6, -8], [3, 11, -3], {
  mat: "fur",
  legend: { o: "fur", m: "mouth", x: "cross" },
  faces: {
    north: ["oooxoo", "oooooo", "oooooo", "oooooo", "oommoo"],
    down: "shadow",
    south: "fur",
    east: "fur",
    west: "fur",
    up: ["ooxooo", "oxxxoo", "ooxooo", "oooooo", "oooooo"],
  },
});
const nose = b.cube("nose_cube", [-1, 7.2, -8.4], [1, 8.2, -8], { mat: "nose" });

// --- long upright ears (pink inner front) ----------------------------------
const earL = b.cube("ear_l_cube", [-3, 11, -5.5], [-1, 19, -4.5], {
  mat: "fur",
  legend: { o: "fur", p: "pink" },
  faces: {
    north: ["oo", "oo", "pp", "pp", "pp", "pp", "oo", "oo"],
    south: "fur",
    east: "fur",
    west: "fur",
    up: "fur",
    down: "fur",
  },
});
const earR = b.cube("ear_r_cube", [1, 11, -5.5], [3, 19, -4.5], {
  mat: "fur",
  legend: { o: "fur", p: "pink" },
  faces: {
    north: ["oo", "oo", "pp", "pp", "pp", "pp", "oo", "oo"],
    south: "fur",
    east: "fur",
    west: "fur",
    up: "fur",
    down: "fur",
  },
});

// --- innocent eyes (eyes_rest: only while not working) ---------------------
const eyeL = b.cube("eye_l_cube", [-2.5, 8.4, -8.3], [-0.5, 10.4, -8], {
  mat: "eye",
  legend: { k: "eye", w: "white" },
  faces: { north: ["kw", "kk"], south: "eye", east: "eye", west: "eye", up: "eye", down: "eye" },
});
const eyeR = b.cube("eye_r_cube", [0.5, 8.4, -8.3], [2.5, 10.4, -8], {
  mat: "eye",
  legend: { k: "eye", w: "white" },
  faces: { north: ["wk", "kk"], south: "eye", east: "eye", west: "eye", up: "eye", down: "eye" },
});

// --- fierce overlay (angry_work: only while working) -----------------------
const browL = b.cube("brow_l_cube", [-3, 10.6, -8.35], [-0.6, 11.6, -8.15], { mat: "red", rotation: [0, 0, -18] });
const browR = b.cube("brow_r_cube", [0.6, 10.6, -8.35], [3, 11.6, -8.15], { mat: "red", rotation: [0, 0, 18] });
const angL = b.cube("ang_l_cube", [-2.6, 9.2, -8.5], [-0.4, 10, -8.3], { mat: "angereye" });
const angR = b.cube("ang_r_cube", [0.4, 9.2, -8.5], [2.6, 10, -8.3], { mat: "angereye" });
const fang = b.cube("fang_cube", [-0.5, 6.3, -8.35], [0.5, 7.5, -8.15], { mat: "white" });

// --- puff tail -------------------------------------------------------------
const tail = b.cube("tail_cube", [-2, 4.5, 5], [2, 7.5, 7.5], {
  mat: "fur",
  faces: { up: "fur", down: "shadow", south: "fur", north: "fur", east: "fur", west: "fur" },
});

// --- legs (short front legs, big hind legs) --------------------------------
const legFl = b.cube("leg_fl_cube", [-3, 0, -4.5], [-1, 3, -2.5], { mat: "fur", faces: { down: "shadow" } });
const legFr = b.cube("leg_fr_cube", [1, 0, -4.5], [3, 3, -2.5], { mat: "fur", faces: { down: "shadow" } });
const legBl = b.cube("leg_bl_cube", [-4, 0, 1], [-1, 4, 5], { mat: "fur", faces: { down: "shadow" } });
const legBr = b.cube("leg_br_cube", [1, 0, 1], [4, 4, 5], { mat: "fur", faces: { down: "shadow" } });

// --- bones -----------------------------------------------------------------
const earLG = b.group("ear_l", [-2, 11, -5], [earL], { rotation: [8, 0, 0] });
const earRG = b.group("ear_r", [2, 11, -5], [earR], { rotation: [8, 0, 0] });
const noseG = b.group("nose", [0, 7.7, -8], [nose]);
const eyesRestG = b.group("eyes_rest", [0, 9.4, -8], [eyeL, eyeR]);
const angryG = b.group("angry_work", [0, 9.4, -8], [browL, browR, angL, angR, fang]);
const headG = b.group("head", [0, 8, -3], [head, noseG, earLG, earRG, eyesRestG, angryG]);
const tailG = b.group("tail", [0, 6, 5.5], [tail], { rotation: [-30, 0, 0] });
const bodyG = b.group("body", [0, 5, 0], [body, headG, tailG]);
const legFlG = b.group("leg_fl", [-2, 3, -3.5], [legFl]);
const legFrG = b.group("leg_fr", [2, 3, -3.5], [legFr]);
const legBlG = b.group("leg_bl", [-2.5, 4, 3], [legBl]);
const legBrG = b.group("leg_br", [2.5, 4, 3], [legBr]);
const rootG = b.group("root", [0, 0, 0], [bodyG, legFlG, legFrG, legBlG, legBrG]);
b.outliner.push(rootG);

// --- animations ------------------------------------------------------------
// Keyframe rotation is applied as (-x,-y,+z); positive keyframe X on the head
// nods it down. Catmullrom for organic ease, ears/tail lag the body for
// follow-through, every loop has identical first/last keys.
const CR = "catmullrom";

// idle 4 s: breathing, slow look-around, nose twitch, an innocent ear flop.
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
        [1.2, 0, 8, 0],
        [2.0, 0, 8, -2],
        [2.6, 0, 0, 0],
        [3.2, 0, -6, 0],
        [4.0, 0, 0, 0],
      ],
      CR,
    ),
  ),
  [noseG.uuid]: bone(
    "nose",
    keyframes(
      "position",
      [
        [0.0, 0, 0, 0],
        [0.90, 0, 0, 0],
        [0.95, 0, 0, -0.25, "linear"],
        [1.00, 0, 0, 0.1],
        [1.05, 0, 0, 0],
        [1.60, 0, 0, 0],
        [1.65, 0, 0, -0.25, "linear"],
        [1.70, 0, 0, 0],
        [2.40, 0, 0, 0],
        [2.45, 0, 0, -0.25, "linear"],
        [2.50, 0, 0, 0],
        [4.0, 0, 0, 0],
      ],
      CR,
    ),
  ),
  [earRG.uuid]: bone(
    "ear_r",
    keyframes(
      "rotation",
      [[0, 0, 0, 0], [2.3, 0, 0, 10], [2.5, 0, 0, 46, "linear"], [2.75, 0, 0, 12], [3.1, 0, 0, 0], [4, 0, 0, 0]],
      CR,
    ),
  ),
  [earLG.uuid]: bone(
    "ear_l",
    keyframes("rotation", [[0, 0, 0, 0], [0.8, 0, 0, -8], [1.6, 0, 0, 6], [2.4, 0, 0, 0], [4, 0, 0, 0]], CR),
  ),
  [tailG.uuid]: bone(
    "tail",
    keyframes("rotation", [[0, 0, 8, 0], [1.3, 0, -8, 0], [2.6, 0, 8, 0], [4, 0, 8, 0]], CR),
  ),
};

// walk 0.8 s: two-footed hops — root arcs up, hind legs push, body pitches.
const walk = {
  [rootG.uuid]: bone(
    "root",
    keyframes("position", [[0, 0, 0, 0], [0.15, 0, -0.4, 0], [0.4, 0, 2.2, 0], [0.6, 0, -0.3, 0], [0.8, 0, 0, 0]], CR),
    keyframes("rotation", [[0, 0, 0, 0], [0.4, -8, 0, 0], [0.6, 6, 0, 0], [0.8, 0, 0, 0]], CR),
  ),
  [bodyG.uuid]: bone(
    "body",
    keyframes("rotation", [[0, 0, 0, 0], [0.15, 6, 0, 0], [0.4, -10, 0, 0], [0.6, 8, 0, 0], [0.8, 0, 0, 0]], CR),
  ),
  [legBlG.uuid]: bone("leg_bl", keyframes("rotation", [[0, 0, 0, 0], [0.15, 30, 0, 0], [0.4, -42, 0, 0], [0.6, 26, 0, 0], [0.8, 0, 0, 0]], CR)),
  [legBrG.uuid]: bone("leg_br", keyframes("rotation", [[0, 0, 0, 0], [0.15, 30, 0, 0], [0.4, -42, 0, 0], [0.6, 26, 0, 0], [0.8, 0, 0, 0]], CR)),
  [legFlG.uuid]: bone("leg_fl", keyframes("rotation", [[0, 0, 0, 0], [0.15, -26, 0, 0], [0.4, 34, 0, 0], [0.6, -20, 0, 0], [0.8, 0, 0, 0]], CR)),
  [legFrG.uuid]: bone("leg_fr", keyframes("rotation", [[0, 0, 0, 0], [0.15, -26, 0, 0], [0.4, 34, 0, 0], [0.6, -20, 0, 0], [0.8, 0, 0, 0]], CR)),
  [headG.uuid]: bone(
    "head",
    keyframes("rotation", [[0, 0, 0, 0], [0.4, -6, 0, 0], [0.6, 4, 0, 0], [0.8, 0, 0, 0]], CR),
  ),
  [earLG.uuid]: bone("ear_l", keyframes("rotation", [[0, 0, 0, 0], [0.4, 16, 0, 0], [0.6, -10, 0, 0], [0.8, 0, 0, 0]], CR)),
  [earRG.uuid]: bone("ear_r", keyframes("rotation", [[0, 0, 0, 0], [0.45, 16, 0, 0], [0.65, -10, 0, 0], [0.8, 0, 0, 0]], CR)),
  [tailG.uuid]: bone("tail", keyframes("rotation", [[0, 0, -10, 0], [0.4, 0, 12, 0], [0.8, 0, -10, 0]], CR)),
};

// work 2.4 s: crouch, wiggle, then a charge headbutt, recover.
const work = {
  [rootG.uuid]: bone(
    "root",
    keyframes(
      "position",
      [
        [0, 0, 0, 0],
        [0.4, 0, -0.6, 0],
        [0.9, 0, -0.6, 0],
        [1.4, 0, 0.4, -2.2],
        [1.7, 0, 0.2, -2.2],
        [2.1, 0, 0, 0.3],
        [2.4, 0, 0, 0],
      ],
      CR,
    ),
    keyframes(
      "rotation",
      [
        [0, 0, 0, 0],
        [0.5, 0, 9, 0],
        [0.7, 0, -9, 0],
        [0.9, 0, 9, 0],
        [1.1, 0, 0, 0],
        [1.4, -10, 0, 0],
        [1.7, -10, 0, 0],
        [2.1, 0, 0, 0],
        [2.4, 0, 0, 0],
      ],
      CR,
    ),
  ),
  [bodyG.uuid]: bone(
    "body",
    keyframes(
      "scale",
      [
        [0, 1, 1, 1],
        [0.4, 1.1, 0.85, 1.1],
        [0.9, 1.1, 0.85, 1.1],
        [1.4, 0.9, 1.15, 0.9],
        [1.7, 0.9, 1.15, 0.9],
        [2.1, 1, 1, 1],
        [2.4, 1, 1, 1],
      ],
      CR,
    ),
  ),
  [headG.uuid]: bone(
    "head",
    keyframes(
      "rotation",
      [
        [0, 0, 0, 0],
        [0.4, 16, 0, 0],
        [0.9, 16, 0, 0],
        [1.4, 36, 0, 0],
        [1.7, 36, 0, 0],
        [2.1, 0, 0, 0],
        [2.4, 0, 0, 0],
      ],
      CR,
    ),
  ),
  [legBlG.uuid]: bone("leg_bl", keyframes("rotation", [[0, 0, 0, 0], [0.4, 36, 0, 0], [0.9, 36, 0, 0], [1.4, -32, 0, 0], [1.7, -16, 0, 0], [2.1, 0, 0, 0], [2.4, 0, 0, 0]], CR)),
  [legBrG.uuid]: bone("leg_br", keyframes("rotation", [[0, 0, 0, 0], [0.4, 36, 0, 0], [0.9, 36, 0, 0], [1.4, -32, 0, 0], [1.7, -16, 0, 0], [2.1, 0, 0, 0], [2.4, 0, 0, 0]], CR)),
  [legFlG.uuid]: bone("leg_fl", keyframes("rotation", [[0, 0, 0, 0], [0.4, -20, 0, 0], [0.9, -20, 0, 0], [1.4, 26, 0, 0], [2.1, 0, 0, 0], [2.4, 0, 0, 0]], CR)),
  [legFrG.uuid]: bone("leg_fr", keyframes("rotation", [[0, 0, 0, 0], [0.4, -20, 0, 0], [0.9, -20, 0, 0], [1.4, 26, 0, 0], [2.1, 0, 0, 0], [2.4, 0, 0, 0]], CR)),
  [earLG.uuid]: bone("ear_l", keyframes("rotation", [[0, 0, 0, 0], [0.4, -10, 0, 0], [0.9, -10, 0, 0], [1.4, 28, 0, 0], [2.1, 0, 0, 0], [2.4, 0, 0, 0]], CR)),
  [earRG.uuid]: bone("ear_r", keyframes("rotation", [[0, 0, 0, 0], [0.4, -10, 0, 0], [0.9, -10, 0, 0], [1.4, 28, 0, 0], [2.1, 0, 0, 0], [2.4, 0, 0, 0]], CR)),
  [tailG.uuid]: bone(
    "tail",
    keyframes("rotation", [[0, 0, 0, 0], [0.9, 0, 14, 0], [1.4, 0, -14, 0], [1.7, 0, 8, 0], [2.1, 0, 0, 0], [2.4, 0, 0, 0]], CR),
  ),
};

// eat 1.0 s: head held down, three nibble dips, nose twitches, ear sway.
const eat = {
  [headG.uuid]: bone(
    "head",
    keyframes(
      "rotation",
      [
        [0, 30, 0, 0],
        [0.15, 38, 0, 0],
        [0.3, 30, 0, 0],
        [0.45, 38, 0, 0],
        [0.6, 30, 0, 0],
        [0.75, 38, 0, 0],
        [1.0, 30, 0, 0],
      ],
      CR,
    ),
  ),
  [noseG.uuid]: bone(
    "nose",
    keyframes("position", [[0, 0, 0, 0], [0.1, 0, 0, -0.3], [0.2, 0, 0, 0], [0.3, 0, 0, -0.3], [0.4, 0, 0, 0], [0.5, 0, 0, -0.3], [0.6, 0, 0, 0], [0.7, 0, 0, -0.3], [0.8, 0, 0, 0], [1.0, 0, 0, 0]], CR),
  ),
  [bodyG.uuid]: bone("body", keyframes("scale", [[0, 1, 1, 1], [0.5, 1, 1.02, 1], [1, 1, 1, 1]], CR)),
  [earLG.uuid]: bone("ear_l", keyframes("rotation", [[0, 0, 0, 0], [0.25, 10, 0, 0], [0.5, 0, 0, 0], [0.75, 10, 0, 0], [1, 0, 0, 0]], CR)),
  [earRG.uuid]: bone("ear_r", keyframes("rotation", [[0, 0, 0, 0], [0.25, 10, 0, 0], [0.5, 0, 0, 0], [0.75, 10, 0, 0], [1, 0, 0, 0]], CR)),
  [tailG.uuid]: bone("tail", keyframes("rotation", [[0, 0, 8, 0], [0.5, 0, -8, 0], [1, 0, 8, 0]], CR)),
};

// hurt 0.35 s once: instant flinch, overshoot past rest, settle.
const hurt = {
  [rootG.uuid]: bone("root", keyframes("position", [[0, 0, 0, 0.4], [0.15, 0, 0, 0], [0.35, 0, 0, 0]], CR)),
  [bodyG.uuid]: bone("body", keyframes("scale", [[0, 1.12, 0.85, 1.12], [0.15, 0.97, 1.04, 0.97], [0.35, 1, 1, 1]], CR)),
  [headG.uuid]: bone("head", keyframes("rotation", [[0, -22, 0, 0], [0.15, 6, 0, 0], [0.35, 0, 0, 0]], CR)),
  [earLG.uuid]: bone("ear_l", keyframes("rotation", [[0, -35, 0, 0], [0.15, 8, 0, 0], [0.35, 0, 0, 0]], CR)),
  [earRG.uuid]: bone("ear_r", keyframes("rotation", [[0, -35, 0, 0], [0.15, 8, 0, 0], [0.35, 0, 0, 0]], CR)),
};

b.animation("idle", "loop", 4.0, idle);
b.animation("walk", "loop", 0.8, walk);
b.animation("work", "loop", 2.4, work);
b.animation("eat", "loop", 1.0, eat);
b.animation("hurt", "once", 0.35, hurt);

b.write(bbmodelOut, pngOut, "hornrabbit");
console.log(`wrote ${bbmodelOut}`);
console.log(`wrote ${pngOut}`);
