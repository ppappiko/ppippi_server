// 젖소 (cow) pet geometry + painted texture -> pipi-facilities/assets/pipifacilities/pets/cow.bbmodel
// Animations are added afterwards by: node dev/art/make-pet-anims.mjs cow
// Concept: big, calm grazing dairy cow. Black patches are painted from 3D blobs so they wrap across faces.
import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import { fileURLToPath } from "node:url";
import { writePng } from "./class-icons/pngio.mjs";
const here = path.dirname(fileURLToPath(import.meta.url));
const out = path.resolve(here, "../pipi-facilities/assets/pipifacilities/pets/cow.bbmodel");

const T = 128, px = Buffer.alloc(T * T * 4);
const hex = s => [parseInt(s.slice(1, 3), 16), parseInt(s.slice(3, 5), 16), parseInt(s.slice(5, 7), 16)];
const set = (u, v, c) => { const o = (v * T + u) * 4; [px[o], px[o + 1], px[o + 2]] = hex(c); px[o + 3] = 255; };
const hash = (a, b, c) => { let h = Math.imul(a | 0, 374761393) ^ Math.imul(b | 0, 668265263) ^ Math.imul(c | 0, 2147483647); h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) >>> 0) / 4294967296; };

// ---- palette
const WHITE = "#f4f1ea", WHITE_S = "#dcd6cb", BLACK = "#2b2a30", BLACK_S = "#1f1e24", PINK = "#f2a9b0", PINK_D = "#d9838d",
  HORN = "#efe2c0", HORN_D = "#c9b48a", HOOF = "#4a3c34", GOLD = "#f2c14e", GOLD_D = "#c58f22", RED = "#c8473f", RED_D = "#9b3029",
  GRASS = "#6fbf4a", GRASS_D = "#4e9a35", EYE = "#1b1a1f", TUFT = "#3a3238";
// black patches (3D blobs in model units)
const BLOBS = [[-5, 14, -3, 3.8], [5, 12, 3, 4.2], [1, 17, 5, 3.4], [-4, 10, 5.5, 2.6], [4.5, 15, -6, 2.4], [-2, 17, -6, 2.2], [0, 9, 8, 2.6],
  [3.5, 17, -12, 2.6], [-5, 4, -5, 2.4], [5, 5, 6, 2.2]];
const patch = (x, y, z) => BLOBS.some(([bx, by, bz, r]) => (x - bx) ** 2 + ((y - by) * 1.15) ** 2 + (z - bz) ** 2 < r * r);
const cow = (x, y, z, face) => {
  const shade = face === "down" || face === "east" && false;
  const n = hash(Math.floor(x * 2), Math.floor(y * 2), Math.floor(z * 2));
  if (patch(x, y, z)) return n < .05 ? BLACK_S : BLACK;
  return n < .035 || shade ? WHITE_S : WHITE;
};

// ---- cubes: name, from, to, painter(x,y,z,face,i,j,w,h) -> colour, bone
let cursorU = 0, cursorV = 0, rowH = 0;
const elements = [];
function alloc(w, h) { if (cursorU + w > T) { cursorU = 0; cursorV += rowH; rowH = 0; } const at = [cursorU, cursorV]; cursorU += w; rowH = Math.max(rowH, h); if (cursorV + h > T) throw new Error("texture full"); return at; }
function cube(name, from, to, paint, extra = {}) {
  const [w, h, d] = [0, 1, 2].map(i => Math.max(1, Math.ceil(to[i] - from[i])));
  const [u0, v0] = alloc(2 * d + 2 * w, d + h);
  const faces = {
    up: [u0 + d, v0, w, d], down: [u0 + d + w, v0, w, d],
    east: [u0, v0 + d, d, h], north: [u0 + d, v0 + d, w, h], west: [u0 + d + w, v0 + d, d, h], south: [u0 + 2 * d + w, v0 + d, w, h],
  };
  const pos = (f, i, j) => { // texel -> 3D point on the cube face (texel centre)
    const a = i + .5, b = j + .5, [fx, fy, fz] = from, [tx, ty, tz] = to;
    const sx = (tx - fx) / w, sy = (ty - fy) / h, sz = (tz - fz) / d;
    switch (f) {
      case "north": return [tx - a * sx, ty - b * sy, fz];
      case "south": return [fx + a * sx, ty - b * sy, tz];
      case "east": return [tx, ty - b * sy, tz - a * sz];
      case "west": return [fx, ty - b * sy, fz + a * sz];
      case "up": return [fx + a * sx, ty, fz + b * sz];
      default: return [fx + a * sx, fy, tz - b * sz];
    }
  };
  const out = {};
  for (const [f, [u, v, fw, fh]] of Object.entries(faces)) {
    for (let j = 0; j < fh; j++) for (let i = 0; i < fw; i++) set(u + i, v + j, paint(...pos(f, i, j), f, i, j, fw, fh));
    out[f] = { uv: [u, v, u + fw, v + fh], texture: 0 };
  }
  const el = { name, uuid: crypto.randomUUID(), type: "cube", from, to, origin: extra.origin || from.map((v, i) => (v + to[i]) / 2), faces: out };
  if (extra.rotation) el.rotation = extra.rotation;
  elements.push(el);
  return el.uuid;
}
const flat = c => () => c;
const edge = (c, e) => (x, y, z, f, i, j, w, h) => (i === 0 || j === 0 || i === w - 1 || j === h - 1) ? e : c;

// body
const body = [
  cube("body_cube", [-5, 8, -8], [5, 17, 8], (x, y, z, f) => f === "down" ? (patch(x, y, z) ? BLACK_S : WHITE_S) : cow(x, y, z, f)),
  cube("belly", [-4.5, 7, -5], [4.5, 8, 5], (x, y, z, f) => patch(x, y + 1, z) ? BLACK_S : WHITE_S),
  cube("hump", [-4, 17, -7], [4, 17.6, -2], (x, y, z, f) => cow(x, y, z, f)),
];
const udder = [
  cube("udder_cube", [-2.5, 5.5, 1.5], [2.5, 7.2, 5.5], (x, y, z, f, i, j, w, h) => f === "up" ? PINK_D : j === h - 1 ? PINK_D : PINK),
  ...[[-1.5, 2.2], [0.6, 2.2], [-1.5, 4], [0.6, 4]].map(([x, z], k) => cube("teat_" + k, [x, 4.5, z], [x + .9, 5.5, z + .9], flat(PINK_D))),
];
// head
const head = [
  cube("head_cube", [-3.5, 11.5, -15], [3.5, 18, -8], (x, y, z, f, i, j, w, h) => {
    if (f === "north") { // face: white blaze, eyes
      if (j === 2 && (i === 1 || i === w - 2)) return EYE;
      if (j === 1 && (i === 1 || i === w - 2)) return "#ffffff";
      return (i <= 1 || i >= w - 2) && j < 4 && patch(x, y, z + 3) ? BLACK : WHITE;
    }
    return f === "down" ? WHITE_S : cow(x, y, z, f);
  }),
  cube("muzzle", [-3, 11.5, -16.5], [3, 14.5, -14.5], (x, y, z, f, i, j, w, h) => {
    if (f === "north" && j === 1 && (i === 1 || i === w - 2)) return PINK_D; // nostrils
    return f === "up" ? PINK : j === h - 1 && f !== "down" ? PINK_D : PINK;
  }),
  cube("forelock", [-1.5, 18, -12], [1.5, 18.8, -9.5], flat(TUFT)),
  cube("horn_l", [-5.5, 17.2, -11.6], [-3.5, 18.4, -10.4], edge(HORN, HORN_D)),
  cube("horn_l_tip", [-6.4, 18.2, -11.4], [-5.4, 19.6, -10.6], flat(HORN_D)),
  cube("horn_r", [3.5, 17.2, -11.6], [5.5, 18.4, -10.4], edge(HORN, HORN_D)),
  cube("horn_r_tip", [5.4, 18.2, -11.4], [6.4, 19.6, -10.6], flat(HORN_D)),
];
const earL = [cube("ear_l_cube", [-7, 15.4, -11.2], [-3.5, 16.8, -10.2], (x, y, z, f) => f === "north" ? PINK : cow(x - 2, y, z, f))];
const earR = [cube("ear_r_cube", [3.5, 15.4, -11.2], [7, 16.8, -10.2], (x, y, z, f) => f === "north" ? PINK : cow(x + 2, y, z, f))];
const grass = [ // only shown while eating (bone suffix _eat): a tuft of grass in the mouth
  cube("grass_1", [-2.5, 11, -17.3], [2.5, 12, -16.3], (x, y, z, f, i) => i % 2 ? GRASS : GRASS_D),
  cube("grass_2", [-1.6, 10, -17.1], [-.6, 11, -16.5], flat(GRASS)),
  cube("grass_3", [.8, 9.6, -17.1], [1.8, 11, -16.5], flat(GRASS_D)),
];
// collar + bell
const collar = [cube("collar", [-3.6, 10.6, -9.6], [3.6, 11.6, -8.2], (x, y, z, f, i) => i % 3 === 0 ? RED_D : RED)];
const bell = [
  cube("bell_cube", [-1.2, 8, -10.4], [1.2, 10.4, -8.6], (x, y, z, f, i, j, w, h) => j === h - 1 ? GOLD_D : i === 0 ? "#ffe28a" : GOLD),
  cube("bell_clapper", [-.4, 7.4, -9.8], [.4, 8, -9.2], flat(GOLD_D)),
];
// tail
const tail = [
  cube("tail_cube", [-.5, 9.5, 8], [.5, 16.5, 9], (x, y, z, f) => cow(x, y, z + 1, f)),
  cube("tail_tuft", [-1, 7.5, 7.6], [1, 10, 9.4], flat(TUFT)),
];
// legs (origin at the hip/shoulder, y = 8)
const leg = (n, x, z) => [cube(`leg_${n}_cube`, [x - 1.6, 0, z - 1.6], [x + 1.6, 8, z + 1.6], (px_, y, pz, f) => y < 1.6 ? HOOF : f === "down" ? HOOF : y < 2.2 ? WHITE_S : cow(px_, y, pz, f))];
const legs = { leg_fl: leg("fl", -3, -5), leg_fr: leg("fr", 3, -5), leg_bl: leg("bl", -3, 5), leg_br: leg("br", 3, 5) };

const G = (name, origin, children, rotation = [0, 0, 0]) => ({ name, uuid: crypto.randomUUID(), origin, rotation, visibility: true, children });
const outliner = [G("root", [0, 8, 0], [
  G("body", [0, 12, 0], [
    ...body,
    G("udder", [0, 7, 3.5], udder),
    G("tail", [0, 16.5, 8.5], tail, [12, 0, 0]),
    G("head", [0, 15, -8], [
      ...head, ...collar,
      G("ear_l", [-3.5, 16.1, -10.7], earL),
      G("ear_r", [3.5, 16.1, -10.7], earR),
      G("bell", [0, 10.8, -9.5], bell),
      G("grass_eat", [0, 11, -16.8], grass),
    ]),
  ]),
  ...Object.entries(legs).map(([n, cubes]) => G(n, [n.endsWith("l") ? -3 : 3, 8, n.includes("_f") ? -5 : 5], cubes)),
])];

const model = {
  meta: { format_version: "4.10", model_format: "free" }, name: "cow", box_uv: false, resolution: { width: T, height: T },
  elements, outliner,
  textures: [{ path: "", name: "cow", folder: "", namespace: "", id: "0", particle: false, render_mode: "default",
    source: "data:image/png;base64," + writePng(px, T, T).toString("base64") }],
  animations: [],
};
fs.writeFileSync(out, JSON.stringify(model));
console.log("cow.bbmodel", elements.length, "cubes");
