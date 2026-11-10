// Builds the vanilla-style "천사픽" (angel guinea pig) pet model.
// Integer-pixel geometry, 1 texel per model pixel, front faces -Z, feet on y=0,
// centered on x=0/z=0. Round loaf-shaped guinea pig with no tail, tiny legs,
// small round ears and a short blunt face; white feathered wings on the back and
// a golden `halo_glow` ring floating above the head. It always hovers ~3 px up.
import path from "node:path";
import { fileURLToPath } from "node:url";
import { VBuilder, keyframes, bone } from "./lib/vanilla.mjs";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "../..");
const bbmodelOut = path.join(root, "dev/pipi-facilities/assets/pipifacilities/pets/angelpig.bbmodel");
const pngOut = path.join(root, "dev/pipi-pets/out/angelpig.png");

const b = new VBuilder({ width: 64, height: 64 });

// --- materials -------------------------------------------------------------
b.material("cream", [["#F2E2C4", 5], ["#E8D6B4", 3], ["#F7EBD6", 2]]);
b.material("ginger", [["#D8944A", 5], ["#C47E36", 3], ["#E0A45C", 2]]);
b.material("white", [["#FFFFFF", 6], ["#F4F4F8", 3], ["#E6E6EC", 2]]);
b.material("wing", [["#FFFFFF", 5], ["#F0F0F6", 3], ["#E0E0EA", 2]]);
b.material("gold", [["#F2C14E", 4], ["#E0AE36", 2], ["#FBD772", 1]]);
b.material("eye", [["#241C1A", 1]]);
b.material("pink", [["#E8A0A8", 3], ["#D98F9C", 2]]);
b.material("dark", [["#4A3A30", 3], ["#3A2E28", 2]]);

// --- body (long low round loaf, tricolor: ginger back over cream) ----------
// A main box with slightly smaller boxes on the top/front/back edges rounds the
// silhouette into a loaf that sits ~1 px above the tiny stub legs.
const sidePatch = (x, y, w, h) => (y < Math.ceil(h / 2) ? "ginger" : "cream");
const makeBodyBox = (name, from, to) =>
  b.cube(name, from, to, {
    mat: "cream",
    faces: {
      up: "ginger",
      down: "white",
      north: "cream",
      south: "cream",
      east: sidePatch,
      west: sidePatch,
    },
  });
const body = makeBodyBox("body_cube", [-4, 1, -5], [4, 7, 5]);
const bodyTop = makeBodyBox("body_top_cube", [-3, 7, -4], [3, 8, 4]);
const bodyFront = makeBodyBox("body_front_cube", [-3, 1, -6], [3, 5, -5]);
const bodyBack = makeBodyBox("body_back_cube", [-3, 1, 5], [3, 5, 6]);

// --- head (short blunt face; white blaze, ginger sides, pink nose) ----------
const head = b.cube("head_cube", [-3, 3, -8], [3, 9, -4], {
  mat: "cream",
  legend: { o: "cream", g: "ginger", w: "white", k: "eye", p: "pink" },
  faces: {
    north: ["oooooo", "oooooo", "ogwwgo", "okwwko", "owwwwo", "ooppoo"],
    up: "cream",
    down: "cream",
    south: "cream",
    east: "ginger",
    west: "ginger",
  },
});

// --- small round ears (pink inner front) -----------------------------------
const makeEar = (name, x1) =>
  b.cube(name, [x1, 8, -6], [x1 + 2, 10, -4], {
    mat: "cream",
    legend: { o: "cream", p: "pink" },
    faces: {
      north: ["po", "oo"],
      south: "cream",
      east: "cream",
      west: "cream",
      up: "cream",
      down: "cream",
    },
  });
const earL = makeEar("ear_l_cube", -4);
const earR = makeEar("ear_r_cube", 2);

// --- tiny stubby legs (2 x 1.5 x 2, mostly hidden under the low body) ------
const makeLeg = (name, x, z) =>
  b.cube(name, [x - 1, 0, z - 1], [x + 1, 1.5, z + 1], {
    mat: "cream",
    faces: { down: "dark", north: "cream", south: "cream", east: "cream", west: "cream", up: "cream" },
  });
const legFl = makeLeg("leg_fl_cube", -2, -2);
const legFr = makeLeg("leg_fr_cube", 2, -2);
const legBl = makeLeg("leg_bl_cube", -2, 2);
const legBr = makeLeg("leg_br_cube", 2, 2);

// --- white feathered wings on the back (3 layered feather rows each) --------
const wingFeather = (name, from, to) =>
  b.cube(name, from, to, {
    mat: "wing",
    faces: { up: "wing", down: "wing", north: "wing", south: "wing", east: "wing", west: "wing" },
  });
const wingL1 = wingFeather("wing_l_1", [-4, 6, 2], [-2, 10, 4]);
const wingL2 = wingFeather("wing_l_2", [-5, 7, 2], [-3, 11, 4]);
const wingL3 = wingFeather("wing_l_3", [-6, 8, 2], [-4, 12, 4]);
const wingR1 = wingFeather("wing_r_1", [2, 6, 2], [4, 10, 4]);
const wingR2 = wingFeather("wing_r_2", [3, 7, 2], [5, 11, 4]);
const wingR3 = wingFeather("wing_r_3", [4, 8, 2], [6, 12, 4]);

// --- golden halo: thin square ring of 4 cubes above the head (`halo_glow`) --
const HY = 13;
const haloN = b.cube("halo_n", [-4, HY, -8], [4, HY + 1, -7], { mat: "gold" });
const haloS = b.cube("halo_s", [-4, HY, -4], [4, HY + 1, -3], { mat: "gold" });
const haloW = b.cube("halo_w", [-4, HY, -8], [-3, HY + 1, -3], { mat: "gold" });
const haloE = b.cube("halo_e", [3, HY, -8], [4, HY + 1, -3], { mat: "gold" });

// --- bones -----------------------------------------------------------------
const earLG = b.group("ear_l", [-3, 8, -5], [earL]);
const earRG = b.group("ear_r", [3, 8, -5], [earR]);
const haloG = b.group("halo_glow", [0, HY, -5.5], [haloN, haloS, haloW, haloE]);
const headG = b.group("head", [0, 6, -4], [head, earLG, earRG, haloG]);
const wingLG = b.group("wing_l", [-3, 6, 3], [wingL1, wingL2, wingL3]);
const wingRG = b.group("wing_r", [3, 6, 3], [wingR1, wingR2, wingR3]);
const bodyG = b.group("body", [0, 4, 0], [body, bodyTop, bodyFront, bodyBack, headG, wingLG, wingRG]);
const legFlG = b.group("leg_fl", [-2, 1.5, -2], [legFl]);
const legFrG = b.group("leg_fr", [2, 1.5, -2], [legFr]);
const legBlG = b.group("leg_bl", [-2, 1.5, 2], [legBl]);
const legBrG = b.group("leg_br", [2, 1.5, 2], [legBr]);
const rootG = b.group("root", [0, 0, 0], [bodyG, legFlG, legFrG, legBlG, legBrG]);
b.outliner.push(rootG);

// --- animations ------------------------------------------------------------
// Keyframe rotation is applied by PetModels as (-x,-y,+z); positive keyframe X
// on the head nods it down. Catmullrom for organic ease, wings/halo lag the body
// for follow-through, every loop has identical first/last keys, linear accents.
// Every animation keeps root ~3 px up with a gentle hover bob.
const CR = "catmullrom";
const HOVER = 3;

// idle 4 s: hover, breathing, head look-around, wing stretch, halo bob.
const idle = {
  [rootG.uuid]: bone(
    "root",
    keyframes("position", [[0, 0, HOVER, 0], [1, 0, HOVER + 0.5, 0], [2, 0, HOVER, 0], [3, 0, HOVER + 0.6, 0], [4, 0, HOVER, 0]], CR),
  ),
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
        [1.0, 0, 10, 0],
        [1.6, 0, 10, -3],
        [2.2, 0, 0, 0],
        [3.0, 0, -8, 0],
        [4.0, 0, 0, 0],
      ],
      CR,
    ),
  ),
  [haloG.uuid]: bone(
    "halo_glow",
    keyframes("position", [[0, 0, 0, 0], [1, 0, 0.5, 0], [2, 0, 0, 0], [3, 0, 0.6, 0], [4, 0, 0, 0]], CR),
  ),
  [wingLG.uuid]: bone(
    "wing_l",
    keyframes("rotation", [[0, 0, 0, 0], [0.5, 0, 0, -6], [1.2, 0, 0, 0], [2.3, 0, 0, 0], [2.6, 0, 0, 52], [2.9, 0, 0, 46], [3.3, 0, 0, 0], [4, 0, 0, 0]], CR),
  ),
  [wingRG.uuid]: bone(
    "wing_r",
    keyframes("rotation", [[0, 0, 0, 0], [0.5, 0, 0, 6], [1.2, 0, 0, 0], [2.3, 0, 0, 0], [2.6, 0, 0, -52], [2.9, 0, 0, -46], [3.3, 0, 0, 0], [4, 0, 0, 0]], CR),
  ),
};

// walk 0.9 s: hovering drift with slow wing beats, tiny leg paddling, body rock.
const walk = {
  [rootG.uuid]: bone(
    "root",
    keyframes("position", [[0, 0, HOVER + 0.3, 0], [0.225, 0, HOVER, 0], [0.45, 0, HOVER + 0.3, 0], [0.675, 0, HOVER, 0], [0.9, 0, HOVER + 0.3, 0]], CR),
  ),
  [bodyG.uuid]: bone(
    "body",
    keyframes("rotation", [[0, 0, 0, 2], [0.225, 0, 0, 0], [0.45, 0, 0, -2], [0.675, 0, 0, 0], [0.9, 0, 0, 2]], CR),
  ),
  [headG.uuid]: bone(
    "head",
    keyframes("rotation", [[0, 0, -3, 0], [0.225, 0, 0, 0], [0.45, 0, 3, 0], [0.675, 0, 0, 0], [0.9, 0, -3, 0]], CR),
  ),
  [wingLG.uuid]: bone("wing_l", keyframes("rotation", [[0, 0, 0, 24], [0.225, 0, 0, -18], [0.45, 0, 0, 24], [0.675, 0, 0, -18], [0.9, 0, 0, 24]], CR)),
  [wingRG.uuid]: bone("wing_r", keyframes("rotation", [[0, 0, 0, -24], [0.225, 0, 0, 18], [0.45, 0, 0, -24], [0.675, 0, 0, 18], [0.9, 0, 0, -24]], CR)),
  [legFlG.uuid]: bone("leg_fl", keyframes("rotation", [[0, 8, 0, 0], [0.45, -8, 0, 0], [0.9, 8, 0, 0]], CR)),
  [legBrG.uuid]: bone("leg_br", keyframes("rotation", [[0, 8, 0, 0], [0.45, -8, 0, 0], [0.9, 8, 0, 0]], CR)),
  [legFrG.uuid]: bone("leg_fr", keyframes("rotation", [[0, -8, 0, 0], [0.45, 8, 0, 0], [0.9, -8, 0, 0]], CR)),
  [legBlG.uuid]: bone("leg_bl", keyframes("rotation", [[0, -8, 0, 0], [0.45, 8, 0, 0], [0.9, -8, 0, 0]], CR)),
  [haloG.uuid]: bone("halo_glow", keyframes("position", [[0, 0, 0.3, 0], [0.45, 0, 0, 0], [0.9, 0, 0.3, 0]], CR)),
};

// work 1.6 s: wings beat fast, body lifts a touch and does a happy 360 spin.
const work = {
  [rootG.uuid]: bone(
    "root",
    keyframes("position", [[0, 0, HOVER, 0], [0.4, 0, HOVER + 0.8, 0], [0.8, 0, HOVER, 0], [1.2, 0, HOVER + 0.8, 0], [1.6, 0, HOVER, 0]], CR),
    keyframes("rotation", [[0, 0, 0, 0], [0.4, 0, 90, 0], [0.8, 0, 180, 0], [1.2, 0, 270, 0], [1.6, 0, 360, 0]], CR),
  ),
  [wingLG.uuid]: bone(
    "wing_l",
    keyframes("rotation", [[0, 0, 0, 50], [0.2, 0, 0, -50], [0.4, 0, 0, 50], [0.6, 0, 0, -50], [0.8, 0, 0, 50], [1.0, 0, 0, -50], [1.2, 0, 0, 50], [1.4, 0, 0, -50], [1.6, 0, 0, 50]], CR),
  ),
  [wingRG.uuid]: bone(
    "wing_r",
    keyframes("rotation", [[0, 0, 0, -50], [0.2, 0, 0, 50], [0.4, 0, 0, -50], [0.6, 0, 0, 50], [0.8, 0, 0, -50], [1.0, 0, 0, 50], [1.2, 0, 0, -50], [1.4, 0, 0, 50], [1.6, 0, 0, -50]], CR),
  ),
  [haloG.uuid]: bone(
    "halo_glow",
    keyframes("position", [[0, 0, 0, 0], [0.4, 0, 0.5, 0], [0.8, 0, 0, 0], [1.2, 0, 0.5, 0], [1.6, 0, 0, 0]], CR),
  ),
  [headG.uuid]: bone("head", keyframes("rotation", [[0, -8, 0, 0], [1.6, -8, 0, 0]], CR)),
  [legFlG.uuid]: bone("leg_fl", keyframes("rotation", [[0, 10, 0, 0], [0.4, -10, 0, 0], [0.8, 10, 0, 0], [1.2, -10, 0, 0], [1.6, 10, 0, 0]], CR)),
  [legFrG.uuid]: bone("leg_fr", keyframes("rotation", [[0, -10, 0, 0], [0.4, 10, 0, 0], [0.8, -10, 0, 0], [1.2, 10, 0, 0], [1.6, -10, 0, 0]], CR)),
  [legBlG.uuid]: bone("leg_bl", keyframes("rotation", [[0, -10, 0, 0], [0.4, 10, 0, 0], [0.8, -10, 0, 0], [1.2, 10, 0, 0], [1.6, -10, 0, 0]], CR)),
  [legBrG.uuid]: bone("leg_br", keyframes("rotation", [[0, 10, 0, 0], [0.4, -10, 0, 0], [0.8, 10, 0, 0], [1.2, -10, 0, 0], [1.6, 10, 0, 0]], CR)),
};

// eat 1.0 s: hovers low, head held down with three nibble dips, wing sway.
const eat = {
  [rootG.uuid]: bone(
    "root",
    keyframes("position", [[0, 0, HOVER - 0.5, 0], [0.25, 0, HOVER - 0.4, 0], [0.5, 0, HOVER - 0.5, 0], [0.75, 0, HOVER - 0.4, 0], [1, 0, HOVER - 0.5, 0]], CR),
  ),
  [headG.uuid]: bone(
    "head",
    keyframes("rotation", [[0, 26, 0, 0], [0.2, 34, 0, 0], [0.4, 26, 0, 0], [0.6, 34, 0, 0], [0.8, 26, 0, 0], [1, 26, 0, 0]], CR),
  ),
  [bodyG.uuid]: bone("body", keyframes("scale", [[0, 1, 1, 1], [0.5, 1, 1.02, 1], [1, 1, 1, 1]], CR)),
  [wingLG.uuid]: bone("wing_l", keyframes("rotation", [[0, 0, 0, 12], [0.5, 0, 0, -12], [1, 0, 0, 12]], CR)),
  [wingRG.uuid]: bone("wing_r", keyframes("rotation", [[0, 0, 0, -12], [0.5, 0, 0, 12], [1, 0, 0, -12]], CR)),
  [haloG.uuid]: bone("halo_glow", keyframes("position", [[0, 0, -0.3, 0], [0.5, 0, 0, 0], [1, 0, -0.3, 0]], CR)),
};

// hurt 0.35 s once: instant flinch, root dips, wings and halo overshoot, settle.
const hurt = {
  [rootG.uuid]: bone("root", keyframes("position", [[0, 0, HOVER, 0], [0.12, 0, HOVER - 0.8, 0], [0.22, 0, HOVER + 0.2, 0], [0.35, 0, HOVER, 0]], CR)),
  [bodyG.uuid]: bone("body", keyframes("scale", [[0, 1.12, 0.85, 1.12], [0.15, 0.97, 1.04, 0.97], [0.35, 1, 1, 1]], CR)),
  [headG.uuid]: bone("head", keyframes("rotation", [[0, -22, 0, 0], [0.15, 6, 0, 0], [0.35, 0, 0, 0]], CR)),
  [wingLG.uuid]: bone("wing_l", keyframes("rotation", [[0, 0, 0, 46], [0.15, 0, 0, 4], [0.35, 0, 0, 0]], CR)),
  [wingRG.uuid]: bone("wing_r", keyframes("rotation", [[0, 0, 0, -46], [0.15, 0, 0, -4], [0.35, 0, 0, 0]], CR)),
  [haloG.uuid]: bone("halo_glow", keyframes("position", [[0, 0, 0.6, 0], [0.15, 0, 0, 0], [0.35, 0, 0, 0]], CR)),
};

b.animation("idle", "loop", 4.0, idle);
b.animation("walk", "loop", 0.9, walk);
b.animation("work", "loop", 1.6, work);
b.animation("eat", "loop", 1.0, eat);
b.animation("hurt", "once", 0.35, hurt);

b.write(bbmodelOut, pngOut, "angelpig");
console.log(`wrote ${bbmodelOut}`);
console.log(`wrote ${pngOut}`);
