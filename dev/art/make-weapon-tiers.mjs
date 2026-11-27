// 티어별 무기 디자인 (검 · 활 · 지팡이, 0~3티어):  node dev/art/make-weapon-tiers.mjs
//   -> plugins/ItemsAdder/contents/pipi_gear/models/item/weapon_{sword,bow,staff}_t{0..3}.json (+ bow pull states _0.._2) + textures/item/weapon_*.png
//      (ItemsAdder pack: every player gets it on join; after changing run /iazip. Items: contents/pipi_gear/gear.yml, picked by PipiLife GearModels)
//   -> dev/art/preview/weapons/<id>.png and preview/weapons/sheet.png (rows 검/활/지팡이, columns 0~3티어)
// Themes: 0 견습(나무·철) · 1 엔더(흑요석·보라·진주) · 2 심연(검푸른 강철·청록 스컬크 빛·뼈) · 3 통곡(금·연보라 수정).
// Model space: 16 px = 1 block, upright, centred on x = z = 8; vanilla rules: coords -16..32, one-axis rotation 0/±22.5/±45.
// Round parts are octagons (a box plus the same box turned 45 deg on y); orbs are stacked octagons.
import fs from "node:fs";
import path from "node:path";
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { writePng, readPng } from "./class-icons/pngio.mjs";
const here = path.dirname(fileURLToPath(import.meta.url));
const A = path.resolve(here, "../../plugins/ItemsAdder/contents/pipi_gear"), NS = "pipi_gear";
for (const d of ["models/item", "textures/item"]) fs.mkdirSync(path.join(A, d), { recursive: true });
/** how each kind sits in the hand / inventory (models are upright, grip low) */
const DISPLAY = {
  sword: { tp: [0, -90, 0], tpT: [0, 5.5, 1], tpS: 1.1, fp: [0, -90, 10], fpT: [1.13, 3.6, 1.13], fpS: .72, gui: .62, guiR: [0, 0, -45] },
  staff: { tp: [0, -90, 0], tpT: [0, 2, 1], tpS: 1.1, fp: [0, -90, 10], fpT: [1.13, 2, 1.13], fpS: .72, gui: .6, guiR: [0, 0, -45] },
  bow: { tp: [0, 90, 0], tpT: [0, 0, 1.5], tpS: 1.17, fp: [0, 90, -10], fpT: [1.13, 1.5, 1.13], fpS: .78, gui: .85, guiR: [0, 0, -45] },
};
const mir = r => [r[0], -r[1], -r[2]];
const PV = path.join(here, "preview/weapons"); fs.mkdirSync(PV, { recursive: true });
const r3 = v => Math.round(v * 1000) / 1000;
const rgb = h => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];
const D = Math.PI / 180;

/** pal: { name: "#hex" } (<= 16); names ending in "!" are glowing (unshaded). */
function model(id, pal, build) {
  const names = Object.keys(pal);
  if (names.length > 16) throw new Error(id + ": too many colours");
  const px = Buffer.alloc(16 * 16 * 4), UV = {}, glow = {};
  names.forEach((n, i) => {
    const c = rgb(pal[n]), sx = (i % 4) * 4, sy = Math.floor(i / 4) * 4;
    for (let y = sy; y < sy + 4; y++) for (let x = sx; x < sx + 4; x++) { const o = (y * 16 + x) * 4; px[o] = c[0]; px[o + 1] = c[1]; px[o + 2] = c[2]; px[o + 3] = 255; }
    const k = n.replace(/!$/, ""); UV[k] = [sx, sy, sx + 4, sy + 4]; glow[k] = n.endsWith("!");
  });
  fs.writeFileSync(path.join(A, `textures/item/${id}.png`), writePng(px, 16, 16));
  const elements = [];
  const box = (from, to, sw, rot) => {
    if (!UV[sw]) throw new Error(id + ": no colour " + sw);
    for (const v of [...from, ...to]) if (v < -16 || v > 32) throw new Error(id + ": out of range " + v);
    if (rot && ![0, 22.5, -22.5, 45, -45].includes(rot.angle)) throw new Error(id + ": angle " + rot.angle);
    const f = { uv: UV[sw], texture: "#0" };
    const el = { from: from.map(r3), to: to.map(r3), faces: { north: f, east: f, south: f, west: f, up: f, down: f } };
    if (glow[sw]) el.shade = false;
    if (rot && rot.angle) el.rotation = { angle: rot.angle, axis: rot.axis, origin: rot.origin.map(r3) };
    elements.push(el);
  };
  build(box);
  const file = path.join(A, `models/item/${id}.json`);
  fs.writeFileSync(file, JSON.stringify({
    textures: { "0": `${NS}:item/${id}`, particle: `${NS}:item/${id}` }, elements,
    display: (() => {
      const k = DISPLAY[id.split("_")[1]], v = x => [x, x, x];
      return {
        thirdperson_righthand: { rotation: k.tp, translation: k.tpT, scale: v(k.tpS) },
        thirdperson_lefthand: { rotation: mir(k.tp), translation: k.tpT, scale: v(k.tpS) },
        firstperson_righthand: { rotation: k.fp, translation: k.fpT, scale: v(k.fpS) },
        firstperson_lefthand: { rotation: mir(k.fp), translation: k.fpT, scale: v(k.fpS) },
        gui: { rotation: k.guiR, translation: [0, 0, 0], scale: v(k.gui) },
        ground: { rotation: [0, 0, 0], translation: [0, 2, 0], scale: v(.4) },
        fixed: { rotation: k.guiR, translation: [0, 0, 0], scale: v(k.gui) },
      };
    })(),
  }));
  // bows: ItemsAdder links the pull states <model>_0 / _1 / _2 (same look, the arrow is drawn by the game)
  if (id.includes("_bow_")) for (const p of [0, 1, 2]) fs.writeFileSync(path.join(A, `models/item/${id}_${p}.json`), JSON.stringify({ parent: `${NS}:item/${id}` }));
  execFileSync("node", [path.join(here, "preview-station.mjs"), "--model", file, "--out", path.join(PV, id + ".png"), "--size", "360"]);
  console.log(id, elements.length, "elements");
}

// ---------------------------------------------------------------- shared shapes
const C = 8;
/** box symmetric about x = 8 (half-width x) and z = 8 (half-depth z) */
const sym = (box, x, y0, y1, z, sw, rot) => box([C - x, y0, C - z], [C + x, y1, C + z], sw, rot);
/** a left/right pair: given on the +x side, mirrored to -x (a z-rotation angle flips) */
const pair = (box, from, to, sw, rot) => {
  box(from, to, sw, rot);
  box([2 * C - to[0], from[1], from[2]], [2 * C - from[0], to[1], to[2]], sw,
    rot && { angle: rot.axis === "z" ? -rot.angle : rot.angle, axis: rot.axis, origin: [2 * C - rot.origin[0], rot.origin[1], rot.origin[2]] });
};
/** octagonal prism around the vertical axis at (cx, cz) */
const oct = (box, cx, cz, y0, y1, r, a, b = a) => {
  box([cx - r, y0, cz - r], [cx + r, y1, cz + r], a);
  box([cx - r, y0, cz - r], [cx + r, y1, cz + r], b, { angle: 45, axis: "y", origin: [cx, (y0 + y1) / 2, cz] });
};
/** roughly round orb of radius r: stacked octagons */
const orb = (box, cx, cy, cz, r, a, b = a, n = 5) => {
  for (let i = 0; i < n; i++) {
    const t0 = -1 + 2 * i / n, t1 = -1 + 2 * (i + 1) / n, tm = (t0 + t1) / 2;
    oct(box, cx, cz, cy + r * t0 - .01, cy + r * t1 + .01, r * Math.sqrt(1 - tm * tm) * .98, i % 2 ? b : a, i % 2 ? a : b);
  }
};
/** small faceted gem facing +-z at (x, y): a diamond plus a square */
const gem = (box, x, y, z0, z1, s, a, b) => {
  box([x - s, y - s, z0], [x + s, y + s, z1], a, { angle: 45, axis: "z", origin: [x, y, (z0 + z1) / 2] });
  box([x - s * .55, y - s * .55, z0 - .08], [x + s * .55, y + s * .55, z1 + .08], b);
};
/** a double-pointed crystal centred at (cx, cy, cz) */
function crystal(box, cx, cy, cz, r, up, down, a, b, n = 4) {
  for (let i = 0; i < n; i++) {
    const t0 = i / n, t1 = (i + 1) / n, rr = Math.max(.16, r * Math.cos(Math.PI / 2 * (t0 + t1) / 2));
    oct(box, cx, cz, cy + up * t0 - .01, cy + up * t1 + .02, rr, a, b);      // same faces all the way up -> long vertical facets
    oct(box, cx, cz, cy - down * t1 - .02, cy - down * t0 + .01, rr, a, b);
  }
}
/** sword blade: body, two bevel steps per edge, optional ridge / fuller, a smooth point in `steps` stages */
function blade(box, { y0, y1, w, th, body, bevel, edge, ridge, fuller, tipLen = 3, steps = 6 }) {
  const seg = (ya, yb, h) => {
    sym(box, h, ya, yb, th / 2, body);
    pair(box, [C + h - .7, ya, C - th / 2 + .1], [C + h - .3, yb, C + th / 2 - .1], bevel);
    pair(box, [C + h - .32, ya, C - th / 2 + .2], [C + h + .1, yb, C + th / 2 - .2], edge);
    if (ridge && h > .5) sym(box, .14, ya, yb, th / 2 + .07, ridge);
  };
  seg(y0, y1, w / 2);
  if (fuller) sym(box, .32, y0 + .5, y1 - 1.6, th / 2 + .04, fuller);
  for (let i = 0; i < steps; i++) {
    const t = (i + 1) / steps, h = Math.max(.22, w / 2 * Math.pow(1 - t * .97, .8));   // ogive point
    seg(y1 + tipLen * i / steps - .01, y1 + tipLen * t, h);
  }
  const s = Math.max(.3, w * .1);
  box([C - s, y1 + tipLen - s, C - th / 2 + .2], [C + s, y1 + tipLen + s, C + th / 2 - .2], edge, { angle: 45, axis: "z", origin: [C, y1 + tipLen, C] });
}
/** octagonal grip with raised wraps */
function grip(box, { y0, y1, r, col, wrap, wraps = 4, colB }) {
  oct(box, C, C, y0, y1, r, col, colB || col);
  for (let i = 0; i < wraps; i++) { const y = y0 + (y1 - y0) * (i + .5) / wraps - .18; oct(box, C, C, y, y + .36, r + .12, wrap); }
}
/** chained segments in the x-y plane from (x, y), each [len, angleDeg (+ leans to -x), width, colour]; mirrored about y = 8.
 *  Every segment also gets a slimmer bevel layer `bev` on its back (+x side) when given. Returns the joints. */
function chainXY(box, { x, y, th, segs, bev, bevW = .45 }) {
  const pts = [];
  for (const [len, ang, w, sw] of segs) {
    for (const sgn of [1, -1]) {
      const by = sgn > 0 ? y : 16 - y - len;
      const o = [x, sgn > 0 ? y : 16 - y, C], R = ang ? { angle: sgn * ang, axis: "z", origin: o } : undefined;
      box([x - w / 2, by, C - th / 2], [x + w / 2, by + len, C + th / 2], sw, R);
      if (bev) box([x + w / 2 - .05, by + .1, C - th / 2 + .18], [x + w / 2 + bevW - .05, by + len - .1, C + th / 2 - .18], bev, R);
    }
    pts.push({ x, y, ang, len, w });
    x -= Math.sin(ang * D) * len; y += Math.cos(ang * D) * len;
  }
  pts.push({ x, y });
  return pts;
}
/** a point at fraction t along joint i of a chain, offset `out` toward its back (+x side) */
const along = (pts, i, t, out = 0) => {
  const g = pts[i], r = g.ang * D;
  return [g.x - Math.sin(r) * g.len * t + Math.cos(r) * out, g.y + Math.cos(r) * g.len * t + Math.sin(r) * out];
};
/** fins on the back of both limbs: [joint, t, len, width, colour, angle] */
function fins(box, pts, list, th) {
  for (const [i, t, len, wd, sw, fa] of list) {
    const [px, py] = along(pts, i, t, pts[i].w / 2 - .25);
    box([px, py - wd / 2, C - th / 2], [px + len, py + wd / 2, C + th / 2], sw, { angle: fa, axis: "z", origin: [px, py, C] });
    box([px, 16 - py - wd / 2, C - th / 2], [px + len, 16 - py + wd / 2, C + th / 2], sw, { angle: -fa, axis: "z", origin: [px, 16 - py, C] });
  }
}
/** knobs at the chain joints (both limbs) */
const knobs = (box, pts, idx, r, a, b) => { for (const i of idx) { const p = pts[i]; oct(box, p.x, C, p.y - r * .7, p.y + r * .7, r, a, b); oct(box, p.x, C, 16 - p.y - r * .7, 16 - p.y + r * .7, r, a, b); } };
/** bowstring between the tips with thicker serving at the middle and the nocks */
function string(box, tip, sw, serve, t = .3) {
  box([tip[0] - t / 2, 16 - tip[1], C - t / 2], [tip[0] + t / 2, tip[1], C + t / 2], sw);
  for (const [y0, y1] of [[6.6, 9.4], [tip[1] - 1.2, tip[1] - .2], [16 - tip[1] + .2, 16 - tip[1] + 1.2]])
    box([tip[0] - t / 2 - .1, y0, C - t / 2 - .1], [tip[0] + t / 2 + .1, y1, C + t / 2 + .1], serve);
}
/** prongs fanning out of a staff head (one per direction), each a chain [len, angleDeg outward (negative curls in)] */
function prongs(box, { y, r0, segs, w, cols, dirs = [[1, 0], [-1, 0], [0, 1], [0, -1]] }) {
  for (const [dx, dz] of dirs) {
    let o = r0, yy = y, ww = w;
    segs.forEach(([len, ang], k) => {
      const sw = cols[Math.min(k, cols.length - 1)];
      const cx = C + dx * o, cz = C + dz * o;
      if (dx) box([cx - ww / 2, yy, cz - ww / 2], [cx + ww / 2, yy + len, cz + ww / 2], sw, ang ? { angle: -dx * ang, axis: "z", origin: [cx, yy, cz] } : undefined);
      else box([cx - ww / 2, yy, cz - ww / 2], [cx + ww / 2, yy + len, cz + ww / 2], sw, ang ? { angle: dz * ang, axis: "x", origin: [cx, yy, cz] } : undefined);
      o += Math.sin(ang * D) * len; yy += Math.cos(ang * D) * len;
      ww *= .85;
    });
  }
}

// ================================================================ 검
// 0 견습 철검: short iron blade with a ridge, square ricasso, flared cross bar with rivets, wrapped leather grip, disc pommel
model("weapon_sword_t0", { iron: "#bfc6cf", ironL: "#eef1f5", ironM: "#d9dee5", ironD: "#7b838f", leather: "#6b4a2f", leatherD: "#45301e", brass: "#b88a3e", ironK: "#5d646e" }, box => {
  blade(box, { y0: 6.6, y1: 18.6, w: 2.7, th: .8, body: "iron", bevel: "ironM", edge: "ironL", ridge: "ironD", tipLen: 3 });
  sym(box, 1.25, 5.8, 6.7, .5, "ironD");                                   // ricasso
  sym(box, 3.1, 5, 5.9, .6, "ironK");                                      // cross bar
  sym(box, 2.9, 5.15, 5.75, .7, "iron");
  pair(box, [C + 2.8, 4.75, C - .75], [C + 3.6, 6.15, C + .75], "iron");  // flared ends
  pair(box, [C + 3.5, 4.9, C - .6], [C + 3.8, 6, C + .6], "ironD");
  for (const z of [-.72, .72]) box([C - .3, 5.15, C + z - .08], [C + .3, 5.75, C + z + .08], "brass");   // rivets
  grip(box, { y0: .6, y1: 5, r: .58, col: "leather", wrap: "leatherD", wraps: 5 });
  oct(box, C, C, .1, .6, .78, "ironK");                                    // collar
  oct(box, C, C, -1.1, .1, 1, "iron", "ironM");                            // disc pommel
  oct(box, C, C, -1.5, -1.05, .55, "ironD");
});

// 1 엔더 장검: steel blade with a violet glowing fuller and gems, obsidian swept-up wings with violet tips, pearl at the guard, claw pommel holding a pearl
model("weapon_sword_t1", { steel: "#a9b1bf", steelM: "#cdd3de", steelL: "#eef1f7", obs: "#2a2038", obsL: "#45375c", obsK: "#17111f", vio: "#c77dff!", vioL: "#f2dcff!", pearl: "#2f8f86", pearlL: "#7fe0cf", wrap: "#1b1426", wrapL: "#5c3f86" }, box => {
  blade(box, { y0: 6.4, y1: 21.5, w: 3, th: .85, body: "steel", bevel: "steelM", edge: "steelL", tipLen: 3.4 });
  sym(box, .34, 6.8, 20.4, .5, "obsK");                                    // fuller channel
  sym(box, .2, 7, 20.2, .52, "vio");                                       // glowing line
  for (const y of [9, 12.5, 16]) gem(box, C, y, C - .5, C + .5, .28, "vioL", "vio");
  sym(box, 1.4, 4.7, 6.5, .9, "obs");
  sym(box, 1.15, 4.9, 6.3, 1, "obsL");
  orb(box, C, 5.6, C, .62, "pearl", "pearlL", 4);
  pair(box, [C + .9, 5.1, C - .7], [C + 3, 6.1, C + .7], "obs", { angle: 22.5, axis: "z", origin: [C + .9, 5.6, C] });
  pair(box, [C + 2.7, 6.1, C - .6], [C + 3.7, 8.6, C + .6], "obsL", { angle: -22.5, axis: "z", origin: [C + 3.1, 6.4, C] });
  pair(box, [C + 3.9, 8.2, C - .45], [C + 4.6, 10, C + .45], "obs", { angle: 22.5, axis: "z", origin: [C + 4.2, 8.4, C] });
  pair(box, [C + 3.55, 9.7, C - .5], [C + 4.35, 10.5, C + .5], "vio", { angle: 45, axis: "z", origin: [C + 3.95, 10.1, C] });
  pair(box, [C + 1.2, 3.6, C - .4], [C + 1.9, 5.2, C + .4], "obsL", { angle: 22.5, axis: "z", origin: [C + 1.5, 5, C] });
  grip(box, { y0: .4, y1: 4.7, r: .6, col: "wrap", wrap: "wrapL", wraps: 5 });
  oct(box, C, C, -.1, .45, .85, "obsL", "obs");
  orb(box, C, -1.3, C, .95, "pearl", "pearlL", 5);
  for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) box([C + dx * .85 - .18, -1.6, C + dz * .85 - .18], [C + dx * .85 + .18, .1, C + dz * .85 + .18], "obsK");
  sym(box, .18, -2.6, -2.1, .18, "vioL");
});

// 2 심연 대검: broad blackened blade, cyan vein with branches, serrated back teeth, rib-bone guard around a skull block with cyan eyes, catalyst pommel
model("weapon_sword_t2", { dark: "#16232e", darkM: "#22333f", darkL: "#33505e", edge: "#8aa1ad", cyan: "#3ff0e6!", cyanD: "#14a3a8!", cyanL: "#c8fffb!", bone: "#e3dccb", boneM: "#c9c0ab", boneD: "#9a907c", grip: "#0c1218", gripL: "#1f5f66", black: "#080c10" }, box => {
  blade(box, { y0: 6.4, y1: 22.6, w: 3.8, th: 1, body: "dark", bevel: "darkM", edge: "edge", tipLen: 3.6, steps: 7 });
  sym(box, .32, 6.8, 22.4, .53, "black");
  sym(box, .17, 7, 22.6, .56, "cyan");
  for (const [y, l] of [[8.6, 1.3], [11.4, 1.1], [14.2, 1.2], [17, 1], [19.6, .8]]) {
    pair(box, [C + .1, y, C - .55], [C + .1 + l, y + .22, C + .55], "cyanD", { angle: 22.5, axis: "z", origin: [C + .1, y, C] });
    pair(box, [C + .1 + l * .8, y + .5, C - .55], [C + .1 + l * .8 + .5, y + .7, C + .55], "cyanD", { angle: 45, axis: "z", origin: [C + .1 + l * .8, y + .5, C] });
  }
  for (let y = 8; y < 21.5; y += 1.7) pair(box, [C + 1.75, y, C - .35], [C + 2.45, y + .7, C + .35], "darkL", { angle: 45, axis: "z", origin: [C + 2.1, y + .35, C] });
  sym(box, 1.5, 4.6, 6.6, 1.05, "boneM");
  sym(box, 1.25, 4.8, 6.4, 1.12, "bone");
  for (const s of [-1, 1]) box([C + s * .55 - .3, 5.5, C - 1.2], [C + s * .55 + .3, 6.1, C + 1.2], "cyan");
  sym(box, .25, 4.9, 5.3, 1.15, "black");
  for (const [y, k] of [[5.9, 1], [5.1, .82], [4.3, .64]]) {
    pair(box, [C + 1.3, y, C - .38 * k - .1], [C + 1.3 + 2.2 * k, y + .55, C + .38 * k + .1], "bone", { angle: 22.5, axis: "z", origin: [C + 1.3, y + .25, C] });
    const ex = C + 1.3 + 2.2 * k * Math.cos(22.5 * D), ey = y + .25 + 2.2 * k * Math.sin(22.5 * D);
    pair(box, [ex - .3, ey - .1, C - .3 * k - .05], [ex + .3, ey + 1.7 * k, C + .3 * k + .05], "boneM", { angle: 22.5, axis: "z", origin: [ex, ey, C] });
    pair(box, [ex - .22 - .55 * k, ey + 1.4 * k, C - .2], [ex + .22 - .55 * k, ey + 2.4 * k, C + .2], "boneD", { angle: 45, axis: "z", origin: [ex - .55 * k, ey + 1.5 * k, C] });
  }
  grip(box, { y0: 0, y1: 4.6, r: .62, col: "grip", wrap: "gripL", wraps: 4 });
  for (const y of [1.1, 2.3, 3.5]) oct(box, C, C, y, y + .14, .78, "cyanD");
  sym(box, 1.15, -2.2, -.05, 1.15, "darkM");
  for (const [dx, dz] of [[1, 1], [1, -1], [-1, 1], [-1, -1]]) box([C + dx * 1.05 - .25, -2.35, C + dz * 1.05 - .25], [C + dx * 1.05 + .25, .1, C + dz * 1.05 + .25], "bone");
  orb(box, C, -1.1, C, .9, "cyan", "cyanL", 4);
});

// 3 통곡 왕검: tall gilded blade with a faceted lilac crystal core, crown guard of five points with a crystal heart, feathered gold wings, crystal pommel in a claw
model("weapon_sword_t3", { gold: "#e8b84a", goldM: "#f3cf6a", goldL: "#fff0a8", goldD: "#9a6a1c", goldK: "#6e4a12", lil: "#c9a4ff!", lilL: "#f4eaff!", lilD: "#8a62e0!", wrap: "#3a2456", wrapL: "#6a4a96", white: "#fffaf0" }, box => {
  blade(box, { y0: 6.4, y1: 24.4, w: 4, th: .95, body: "goldD", bevel: "gold", edge: "goldL", tipLen: 4, steps: 7 });
  sym(box, 1.25, 7.2, 23.6, .5, "goldK");                                  // inlay frame
  for (let y = 7.4, i = 0; y < 23.2; y += 1.3, i++) sym(box, 1.05 - (i % 2) * .15, y, y + 1.32, .54, i % 2 ? "lil" : "lilD");
  sym(box, .35, 7.6, 23.2, .57, "lilL");
  for (const y of [10, 15, 20]) gem(box, C, y, C - .58, C + .58, .4, "white", "lilL");
  sym(box, 3.4, 4.7, 6.3, .95, "gold");
  sym(box, 3.2, 4.9, 6.1, 1.02, "goldM");
  sym(box, 3.6, 4.55, 4.85, .8, "goldD");
  for (const [x, h, w] of [[0, 4, .5], [1.4, 2.6, .4], [2.7, 1.8, .35]]) {
    for (const cx of x ? [C + x, C - x] : [C]) {
      box([cx - w, 6.3, C - w], [cx + w, 6.3 + h, C + w], "gold");
      box([cx - w * .6, 6.3 + h - .1, C - w * .6], [cx + w * .6, 6.3 + h + .5, C + w * .6], "goldL", { angle: 45, axis: "y", origin: [cx, 6.3 + h, C] });
    }
  }
  crystal(box, C, 7.9, C, .85, 1.7, 1, "lil", "lilD", 3);
  for (const [y, l, sw] of [[5.2, 2.6, "goldM"], [4.4, 2.2, "gold"], [3.6, 1.8, "goldD"]])
    pair(box, [C + 3.2, y - .3, C - .35], [C + 3.2 + l, y + .3, C + .35], sw, { angle: -22.5, axis: "z", origin: [C + 3.2, y, C] });
  grip(box, { y0: .3, y1: 4.55, r: .62, col: "wrap", wrap: "gold", wraps: 4, colB: "wrapL" });
  oct(box, C, C, -.25, .35, .85, "gold", "goldD");
  for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
    const cx = C + dx * .7, cz = C + dz * .7;
    if (dx) box([cx - .16, -2.2, cz - .16], [cx + .16, -.2, cz + .16], "gold", { angle: dx * 22.5, axis: "z", origin: [cx, -.2, cz] });
    else box([cx - .16, -2.2, cz - .16], [cx + .16, -.2, cz + .16], "gold", { angle: -dz * 22.5, axis: "x", origin: [cx, -.2, cz] });
  }
  crystal(box, C, -1.6, C, .75, .9, 1.7, "lil", "lilD", 3);
});

// ================================================================ 활
// 0 견습 나무활: wooden longbow tapering to horn nocks, leather grip with wraps, arrow rest, served twine string
model("weapon_bow_t0", { wood: "#9a7447", woodL: "#b58d5c", woodD: "#6a4c2c", leather: "#5a3c24", leatherL: "#7a5434", twine: "#e6dcc0", twineD: "#bfb08a", horn: "#d8c6a0" }, box => {
  const pts = chainXY(box, { x: C, y: 9.8, th: 1, bev: "woodL", bevW: .4, segs: [[1.6, 0, 1.5, "wood"], [2.6, 22.5, 1.45, "wood"], [2.6, 22.5, 1.3, "wood"], [2.2, 45, 1.15, "wood"], [1.8, 45, 1, "woodD"], [1.2, 45, .8, "horn"]] });
  knobs(box, pts, [5], .5, "horn", "horn");
  sym(box, .82, 6.2, 9.8, .72, "leather");
  for (const y of [6.6, 7.5, 8.4, 9.2]) sym(box, .9, y, y + .3, .8, "leatherL");
  sym(box, .95, 5.8, 6.25, .82, "woodD"); sym(box, .95, 9.75, 10.2, .82, "woodD");
  box([C - 1.4, 8.3, C - .25], [C - .75, 8.9, C + .25], "horn");
  string(box, [pts[6].x, pts[6].y], "twine", "twineD");
});

// 1 엔더 리커브: layered obsidian recurve with violet inlay, steel tip caps flicking forward with gems, riser with a pearl and sight window, glowing string
model("weapon_bow_t1", { obs: "#2a2038", obsL: "#45375c", obsK: "#17111f", steel: "#b5bdca", steelL: "#e4e8f0", vio: "#c77dff!", vioL: "#f2dcff!", pearl: "#2f8f86", pearlL: "#7fe0cf", wrap: "#1b1426", wrapL: "#5c3f86" }, box => {
  const segs = [[1.4, 0, 1.8, "obsL"], [2.4, 22.5, 1.7, "obs"], [2.4, 22.5, 1.55, "obs"], [2.2, 45, 1.4, "obs"], [1.6, 45, 1.25, "obs"], [1.4, 22.5, 1.1, "steel"], [1.3, 0, .9, "steel"], [.9, -22.5, .7, "steelL"]];
  const pts = chainXY(box, { x: C, y: 10.2, th: 1.1, bev: "obsL", segs });
  chainXY(box, { x: C, y: 10.4, th: 1.24, segs: [[1.2, 0, .24, "vio"], [2.4, 22.5, .24, "vio"], [2.4, 22.5, .22, "vio"], [2.2, 45, .2, "vio"]] });
  knobs(box, pts, [5], .62, "steelL", "steel");
  { const p = pts[7]; gem(box, p.x, p.y + .3, C - .5, C + .5, .3, "vioL", "vio"); gem(box, p.x, 16 - p.y - .3, C - .5, C + .5, .3, "vioL", "vio"); }
  sym(box, .95, 5.4, 10.6, .8, "obs");
  box([C - 1.2, 8.2, C - .6], [C - .55, 10.3, C + .6], "obsK");
  grip(box, { y0: 5.9, y1: 8.1, r: .78, col: "wrap", wrap: "wrapL", wraps: 3 });
  for (const y of [5.3, 10.5]) sym(box, 1.05, y - .3, y + .3, .9, "steel");
  orb(box, C + 1.05, 9.1, C, .62, "pearl", "pearlL", 4);
  string(box, [pts[8].x, pts[8].y], "vio", "vioL", .3);
  box([C - 1.7, 8.1, C - .25], [C - 1.15, 8.7, C + .25], "steelL");
});

// 2 심연 뼈활: vertebra-jointed bone limbs with a cyan core line, hooked spines on the back, rib-wrapped riser with a glowing heart, bone claw tips, cyan string
model("weapon_bow_t2", { bone: "#e3dccb", boneM: "#c9c0ab", boneD: "#9a907c", cyan: "#3ff0e6!", cyanD: "#14a3a8!", cyanL: "#c8fffb!", dark: "#16232e", darkL: "#2c4452", black: "#080c10" }, box => {
  const segs = [[1.4, 0, 1.9, "bone"], [2.4, 22.5, 1.8, "bone"], [2.4, 22.5, 1.6, "bone"], [2.2, 45, 1.45, "bone"], [2, 45, 1.25, "boneM"], [1.4, 45, 1, "boneD"]];
  const pts = chainXY(box, { x: C, y: 10.4, th: 1.1, bev: "boneM", segs });
  chainXY(box, { x: C, y: 10.4, th: 1.26, segs: [[1.4, 0, .3, "cyan"], [2.4, 22.5, .3, "cyan"], [2.4, 22.5, .28, "cyan"], [2.2, 45, .25, "cyanD"]] });
  knobs(box, pts, [1, 2, 3, 4], .62, "boneM", "boneD");
  fins(box, pts, [[1, .5, 1.9, .6, "boneD", 45], [2, .5, 1.7, .55, "boneD", 45], [3, .45, 1.5, .5, "boneM", 45], [4, .5, 1.2, .45, "bone", 22.5]], .7);
  const tp = pts[6];
  for (const s of [1, -1]) {
    const ty = s > 0 ? tp.y : 16 - tp.y;
    box([tp.x - .3, s > 0 ? ty - .2 : ty - 1.4, C - .3], [tp.x + .3, s > 0 ? ty + 1.4 : ty + .2, C + .3], "boneD", { angle: s * 45, axis: "z", origin: [tp.x, ty, C] });
  }
  sym(box, 1, 5.6, 10.4, .85, "dark");
  for (const y of [6.1, 6.9, 7.7, 8.5, 9.3]) pair(box, [C + .5, y, C - .95], [C + 1.25, y + .38, C + .95], "bone");
  sym(box, .4, 5.8, 10.2, .95, "darkL");
  orb(box, C + 1.15, 8, C, .62, "cyan", "cyanL", 4);
  string(box, [tp.x - .2, tp.y], "cyanD", "cyan", .28);
  box([C - 1.7, 8.1, C - .25], [C - 1.05, 8.7, C + .25], "boneD");
});

// 3 통곡 초승달활: golden crescent with gold filigree knobs and two ranks of crystal feathers, crystal tips, crowned riser with a crystal heart, lilac string with gold serving
model("weapon_bow_t3", { gold: "#e8b84a", goldM: "#f3cf6a", goldL: "#fff0a8", goldD: "#9a6a1c", lil: "#c9a4ff!", lilL: "#f4eaff!", lilD: "#8a62e0!", wrap: "#3a2456", wrapL: "#6a4a96", white: "#fffaf0" }, box => {
  const segs = [[1.4, 0, 1.9, "gold"], [2.4, 22.5, 1.8, "gold"], [2.4, 22.5, 1.65, "gold"], [2.2, 45, 1.5, "gold"], [2, 45, 1.3, "goldM"], [1.5, 45, 1.05, "goldL"]];
  const pts = chainXY(box, { x: C, y: 10.4, th: 1.1, bev: "goldM", segs });
  chainXY(box, { x: C, y: 10.6, th: 1.24, segs: [[1.2, 0, .26, "goldD"], [2.4, 22.5, .26, "goldD"], [2.4, 22.5, .24, "goldD"], [2.2, 45, .22, "goldD"]] });
  knobs(box, pts, [1, 2, 3, 4], .58, "goldL", "goldD");
  fins(box, pts, [[1, .2, 3.4, .75, "lil", 22.5], [1, .75, 3.1, .7, "lilD", 22.5], [2, .3, 2.9, .65, "lil", 45], [2, .85, 2.6, .6, "lilD", 45], [3, .4, 2.3, .58, "lil", 45], [4, .3, 1.9, .5, "lilD", 45], [4, .85, 1.5, .45, "lilL", 45]], .55);
  fins(box, pts, [[1, .45, 1.8, .4, "lilL", 22.5], [2, .55, 1.6, .38, "lilL", 45], [3, .7, 1.3, .35, "lilL", 45]], .75);
  const tp = pts[6];
  crystal(box, tp.x, tp.y + .5, C, .45, 1.1, .5, "lil", "lilL", 2);
  crystal(box, tp.x, 16 - tp.y - .5, C, .45, .5, 1.1, "lil", "lilL", 2);
  sym(box, .95, 5.5, 10.5, .8, "goldD");
  grip(box, { y0: 6, y1: 10, r: .78, col: "wrap", wrap: "gold", wraps: 4, colB: "wrapL" });
  for (const y of [5.4, 10.6]) oct(box, C, C, y - .35, y + .35, 1.05, "gold", "goldM");
  box([C + .7, 6.7, C - .55], [C + 1.5, 9.3, C + .55], "gold");
  for (const y of [6.8, 8, 9.2]) box([C + 1.4, y - .2, C - .3], [C + 2.1, y + .2, C + .3], "goldL", { angle: 45, axis: "z", origin: [C + 1.4, y, C] });
  gem(box, C + 1.5, 8, C - .62, C + .62, .5, "lil", "lilL");
  string(box, [tp.x, tp.y], "lilD", "gold", .3);
  box([C - 1.7, 8.1, C - .25], [C - 1.05, 8.7, C + .25], "white");
});

// ================================================================ 지팡이
// 0 견습 나무 지팡이: knotted octagonal shaft with a sprouting leaf, iron ferrule, a crook hooking over a twine-bound quartz shard
model("weapon_staff_t0", { wood: "#8b6a44", woodL: "#ab8a5c", woodD: "#5e4529", quartz: "#eaf2ff!", quartzD: "#b7c8e6", twine: "#d8cba6", leaf: "#6fae4a", leafD: "#4a7f30", iron: "#8a919b" }, box => {
  oct(box, C, C, -5, 19.2, .62, "wood", "woodL");
  for (const [y, dx, dz] of [[-1.4, .5, .2], [4.6, -.5, .3], [10.8, .4, -.4], [15.6, -.3, -.3]]) box([C + dx - .45, y, C + dz - .45], [C + dx + .45, y + 1, C + dz + .45], "woodD", { angle: 45, axis: "y", origin: [C + dx, y + .5, C + dz] });
  oct(box, C, C, 6.2, 9.6, .72, "woodD");                                  // hand wear
  oct(box, C, C, -6.4, -4.8, .7, "iron");                                 // ferrule
  box([C - .3, 12.5, C - .2], [C + 1.6, 12.9, C + .2], "woodL", { angle: 22.5, axis: "z", origin: [C + .3, 12.7, C] });
  box([C + 1.3, 12.6, C - .6], [C + 2.6, 13.5, C + .6], "leaf", { angle: -22.5, axis: "z", origin: [C + 1.3, 13, C] });
  box([C + 1.2, 13.4, C - .45], [C + 2.2, 14.2, C + .45], "leafD", { angle: 22.5, axis: "z", origin: [C + 1.2, 13.8, C] });
  box([C - .55, 19, C - .55], [C + .55, 22.4, C + .55], "woodL", { angle: 22.5, axis: "z", origin: [C, 19, C] });
  box([6.7 - .55, 22.1, C - .55], [6.7 + .55, 24.3, C + .55], "wood", { angle: 45, axis: "z", origin: [6.7, 22.1, C] });
  box([5.2 - .55, 21.4, C - .5], [5.2 + .55, 23.8, C + .5], "woodD");
  box([5.2 - .45, 20.6, C - .45], [5.2 + .45, 21.5, C + .45], "woodL");
  crystal(box, C + .2, 21.6, C, .72, 1.9, 1.3, "quartz", "quartzD", 3);
  for (const y of [19.7, 20.3]) oct(box, C + .1, C, y, y + .25, .7, "twine");
});

// 1 엔더 지팡이: obsidian shaft with glowing rune bands and a wrapped grip, four curling prongs holding an ender-eye orb with a violet pupil, steel ferrule
model("weapon_staff_t1", { obs: "#2a2038", obsL: "#45375c", obsK: "#17111f", vio: "#c77dff!", vioL: "#f2dcff!", pearl: "#2f8f86", pearlL: "#7fe0cf", eye: "#103a30", steel: "#b5bdca", steelL: "#e4e8f0", wrap: "#1b1426", wrapL: "#5c3f86" }, box => {
  oct(box, C, C, -5, 18.2, .6, "obs", "obsL");
  for (const y of [-3.8, 12, 15]) { oct(box, C, C, y, y + .7, .78, "obsK"); oct(box, C, C, y + .2, y + .5, .82, "vio"); }
  grip(box, { y0: 3.5, y1: 9, r: .72, col: "wrap", wrap: "wrapL", wraps: 5 });
  for (const y of [3.2, 9.3]) oct(box, C, C, y - .25, y + .25, .86, "steel");
  oct(box, C, C, -6.2, -4.8, .72, "steel", "steelL");
  crystal(box, C, -6.2, C, .5, .01, 1.4, "steelL", "steel", 2);
  oct(box, C, C, 17.4, 18.8, 1.15, "obsL", "obs");
  oct(box, C, C, 18.6, 19.2, 1.4, "obsK");
  prongs(box, { y: 18.8, r0: 1.1, w: .55, cols: ["obsL", "obs", "vio"], segs: [[1.8, 22.5], [1.8, 0], [1.6, -22.5], [1.2, -45]] });
  orb(box, C, 21.8, C, 1.55, "pearl", "pearlL", 5);
  sym(box, .55, 21.2, 22.4, 1.62, "eye");
  sym(box, .2, 21.3, 22.3, 1.66, "vioL");
  box([C - 1.62, 21.2, C - .55], [C + 1.62, 22.4, C + .55], "eye");
  box([C - 1.66, 21.3, C - .2], [C + 1.66, 22.3, C + .2], "vioL");
});

// 2 심연 지팡이: twisted shaft with cyan rings and drips, a shrieker-jaw head of bone tendrils curling around a glowing sculk core
model("weapon_staff_t2", { dark: "#16232e", darkM: "#22333f", darkL: "#33505e", cyan: "#3ff0e6!", cyanD: "#14a3a8!", cyanL: "#c8fffb!", bone: "#e3dccb", boneM: "#c9c0ab", boneD: "#9a907c", black: "#080c10" }, box => {
  for (let i = 0; i < 12; i++) { const y = -5.5 + i * 2; sym(box, .62, y, y + 2.03, .62, i % 2 ? "dark" : "darkL", { angle: i % 2 ? 22.5 : -22.5, axis: "y", origin: [C, y + 1, C] }); }
  for (const y of [-2.2, 3.4, 9, 14.2]) { oct(box, C, C, y, y + .45, .82, "cyan"); box([C + .6, y - .9, C - .12], [C + .84, y, C + .12], "cyanD"); }
  oct(box, C, C, -6.4, -5.4, .8, "boneD", "bone");
  crystal(box, C, -6.4, C, .55, .01, 1.5, "bone", "boneM", 2);
  oct(box, C, C, 17.8, 19.4, 1.2, "darkM", "dark");
  oct(box, C, C, 19.2, 19.7, 1.5, "black");
  prongs(box, { y: 19.4, r0: 1.25, w: .6, cols: ["bone", "boneM", "boneD", "boneD"], segs: [[1.6, 45], [1.8, 22.5], [1.8, -22.5], [1.4, -45]] });
  prongs(box, { y: 19.4, r0: .85, w: .45, cols: ["boneM", "boneD"], segs: [[1.6, 22.5], [1.6, 0], [1.2, -45]] });
  orb(box, C, 22, C, 1.45, "cyan", "cyanL", 5);
  oct(box, C, C, 21.7, 22.3, 1.55, "cyanD");
  for (const [dx, dz, l] of [[.9, .4, 1.3], [-.7, -.6, 1], [.2, -.9, .8]]) box([C + dx - .12, 19.4 - l, C + dz - .12], [C + dx + .12, 19.4, C + dz + .12], "cyanD");
});

// 3 통곡 왕관 지팡이: gilded shaft with filigree rings, wrapped grip, a crystal set mid-shaft, a two-tier crown with eight crystal-tipped points, a floating crystal circled by four shards
model("weapon_staff_t3", { gold: "#e8b84a", goldM: "#f3cf6a", goldL: "#fff0a8", goldD: "#9a6a1c", goldK: "#6e4a12", lil: "#c9a4ff!", lilL: "#f4eaff!", lilD: "#8a62e0!", wrap: "#3a2456", wrapL: "#6a4a96", white: "#fffaf0" }, box => {
  oct(box, C, C, -5, 17.4, .58, "gold", "goldM");
  for (const y of [-4, 1.5, 10, 13.2]) { oct(box, C, C, y, y + .6, .78, "goldD"); oct(box, C, C, y + .15, y + .45, .84, "goldL"); }
  grip(box, { y0: 2.4, y1: 9.2, r: .7, col: "wrap", wrap: "gold", wraps: 5, colB: "wrapL" });
  oct(box, C, C, 14.4, 16.8, .78, "goldD");
  for (const z of [-1, 1]) gem(box, C, 15.6, C + z * .8 - .1, C + z * .8 + .1, .42, "lil", "lilL");
  oct(box, C, C, -6.4, -4.8, .75, "gold", "goldD");
  crystal(box, C, -6.4, C, .55, .01, 1.6, "goldL", "gold", 2);
  oct(box, C, C, 16.8, 18, 1.1, "goldM", "gold");
  oct(box, C, C, 17.8, 18.4, 1.6, "goldD");
  const ring = (y, r, th, rot, sw) => {
    const R = rot ? { angle: 45, axis: "y", origin: [C, y, C] } : undefined;
    box([C - r, y, C - r], [C + r, y + th, C - r + .4], sw, R); box([C - r, y, C + r - .4], [C + r, y + th, C + r], sw, R);
    box([C - r, y, C - r], [C - r + .4, y + th, C + r], sw, R); box([C + r - .4, y, C - r], [C + r, y + th, C + r], sw, R);
  };
  ring(18.2, 2.3, .7, false, "gold"); ring(18.2, 2.3, .7, true, "gold");
  ring(19, 2.6, .35, false, "goldL"); ring(19, 2.6, .35, true, "goldL");
  for (const [dx, dz, h] of [[1, 1, 2.2], [-1, 1, 2.2], [1, -1, 2.2], [-1, -1, 2.2], [1.414, 0, 1.6], [-1.414, 0, 1.6], [0, 1.414, 1.6], [0, -1.414, 1.6]]) {
    const cx = C + dx * 1.62, cz = C + dz * 1.62;
    box([cx - .25, 18.6, cz - .25], [cx + .25, 18.6 + h, cz + .25], "gold");
    crystal(box, cx, 18.6 + h + .35, cz, .28, .7, .35, "lil", "lilL", 2);
  }
  crystal(box, C, 23.4, C, 1.35, 4.6, 2.8, "lil", "lilD", 6);
  oct(box, C, C, 23.25, 23.55, 1.45, "lilL");
  for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) crystal(box, C + dx * 2.6, 23.6 + dx * .6 - dz * .4, C + dz * 2.6, .3, .8, .5, "lilL", "lil", 2);
});

// ---------------------------------------------------------------- contact sheet
{
  const ids = ["sword", "bow", "staff"].flatMap(k => [0, 1, 2, 3].map(t => `weapon_${k}_t${t}`));
  const S = 360, cols = 4, rows = 3, W = S * cols, H = S * rows, out = Buffer.alloc(W * H * 4);
  for (let i = 0; i < W * H; i++) { out[i * 4] = 34; out[i * 4 + 1] = 32; out[i * 4 + 2] = 40; out[i * 4 + 3] = 255; }
  ids.forEach((id, k) => {
    const img = readPng(fs.readFileSync(path.join(PV, id + ".png"))), ox = (k % cols) * S, oy = Math.floor(k / cols) * S;
    const d = img.data || img.px, w = img.width || img.w, h = img.height || img.h;
    for (let y = 0; y < Math.min(h, S); y++) for (let x = 0; x < Math.min(w, S); x++) {
      const s = (y * w + x) * 4, a = d[s + 3] / 255, o = ((oy + y) * W + ox + x) * 4;
      for (let c = 0; c < 3; c++) out[o + c] = Math.round(d[s + c] * a + out[o + c] * (1 - a));
    }
  });
  fs.writeFileSync(path.join(PV, "sheet.png"), writePng(out, W, H));
  console.log("sheet", path.join(PV, "sheet.png"));
}
