// Generates 3D station (farm / quarry / lumber yard) item models + one shared palette texture:
//   node dev/art/make-station-models.mjs
// Writes pipi-facilities/assets/pipifacilities/{models/item/station_<name>.json, textures/item/station_palette.png}
// Footprint convention: x,z in 0..16 (fills the whole facility area when the ItemDisplay is scaled to 2x / 3x),
// y starts at 0 (ground) and goes up (trees may exceed 16, up to 32); the plugin lifts the display by scale/2 so model y=0 sits on the floor.
import fs from "node:fs";
import path from "node:path";
import zlib from "node:zlib";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "../pipi-facilities/assets/pipifacilities");
const texDir = path.join(root, "textures/item"), modDir = path.join(root, "models/item");
fs.mkdirSync(texDir, { recursive: true }); fs.mkdirSync(modDir, { recursive: true });

// ---- zlib PNG writer ----
const crcT = new Uint32Array(256).map((_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const crc = b => { let c = 0xffffffff; for (const x of b) c = crcT[(c ^ x) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
function chunk(type, data) {
  const t = Buffer.from(type, "ascii"), l = Buffer.alloc(4), c = Buffer.alloc(4);
  l.writeUInt32BE(data.length); c.writeUInt32BE(crc(Buffer.concat([t, data])));
  return Buffer.concat([l, t, data, c]);
}
function png(px, w, h) {
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) { raw[y * (w * 4 + 1)] = 0; for (let x = 0; x < w * 4; x++) raw[y * (w * 4 + 1) + 1 + x] = px[y * w * 4 + x]; }
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk("IHDR", ihdr), chunk("IDAT", zlib.deflateSync(raw)), chunk("IEND", Buffer.alloc(0))]);
}
let seed = 777;
const rnd = () => (seed = (seed * 1664525 + 1013904223) >>> 0) / 4294967296;
const hex = h => [1, 3, 5].map(i => parseInt(h.slice(i, i + 2), 16));

// ---- palette: 8x8 cells of 16x16 px in a 128x128 texture; every cell is 3-shade pixel noise (vanilla-like, ~12% darker, less saturated) ----
// base colours derived from averaged vanilla block textures (oak/spruce planks+logs, farmland, dirt, stone, andesite, hay, crimson stem ...)
const PAL = {
  wood: "#8f7142", woodDark: "#5e4527", woodLight: "#a08349", hay: "#9a7e22", hayDark: "#6f5a17",
  soil: "#76523a", soilDark: "#57392a", wheat: "#a8913a", wheatDark: "#857126", stalk: "#6f7c30",
  leaf: "#3f7a2a", leafDark: "#2b5a1e", leafLight: "#57923a",
  carrot: "#c47524", carrotDark: "#975618", potato: "#a07f4c", potatoDark: "#7a5c34",
  cabbage: "#5c9a3e", cabbageLight: "#8dbb5c", cabbageDark: "#3a7030",
  stone: "#707070", stoneDark: "#555555", stoneLight: "#838384", stoneBlack: "#3d3d42", wheatGreen: "#97a03c", ironLight: "#c3a587",
  iron: "#a58466", rust: "#80503a",
  cyan: "#4cc9c1", cyanDark: "#2a9496", cyanLight: "#8ee0da",
  amber: "#d4801f", amberDark: "#a05a18", amberLight: "#e8b050",
  gold: "#d9a62c", goldDark: "#a5761a", goldLight: "#ecc858",
  crate: "#b88a28", crateDark: "#7f5a16",
  bark: "#5c4529", barkDark: "#2f200f", ring: "#8a6e40", ringDark: "#6b5230", logEnd: "#957848",
  rbark: "#69202a", rbarkDark: "#40111a", rring: "#7a3b48", rringDark: "#5c2a38", rlogEnd: "#8a4552",
  rleaf: "#5a2a1c", rleafDark: "#3b1a12", rleafLight: "#78402a",
  strawPale: "#dcc470", strawGold: "#c99a2e", strawDark: "#9a6c1c", strawBand: "#693e06", strawGreen: "#878c1c",
  steel: "#8f979f", steelDark: "#5f666e", chip: "#a8895a", chipDark: "#7d6340",
};
const names = Object.keys(PAL);
if (names.length > 64) throw new Error("palette overflow");
const idx = Object.fromEntries(names.map((n, i) => [n, i]));
{
  const T = 128, C = 16, px = new Uint8Array(T * T * 4);
  const shade = (c, f) => c.map(v => Math.max(0, Math.min(255, Math.round(v * f))));
  names.forEach((n, i) => {
    const base = hex(PAL[n]), sh = [shade(base, .88), base, shade(base, 1.08)], cx = (i % 8) * C, cy = Math.floor(i / 8) * C;
    const g = [];   // coarse 2x2 blobs so the noise reads like block texture instead of salt & pepper
    for (let y = 0; y < C / 2; y++) { g.push([]); for (let x = 0; x < C / 2; x++) g[y].push(rnd()); }
    for (let y = 0; y < C; y++) for (let x = 0; x < C; x++) {
      const r = rnd() * .5 + g[y >> 1][x >> 1] * .5, c = sh[r < .27 ? 0 : r > .75 ? 2 : 1], k = ((cy + y) * T + cx + x) * 4;
      px[k] = c[0]; px[k + 1] = c[1]; px[k + 2] = c[2]; px[k + 3] = 255;
    }
  });
  fs.writeFileSync(path.join(texDir, "station_palette.png"), png(px, T, T));
}
const uvOf = n => { const i = idx[n]; if (i === undefined) throw new Error("no colour " + n); const x = (i % 8) * 2, y = Math.floor(i / 8) * 2; return [x + .3, y + .3, x + 1.7, y + 1.7]; };

// ---- model builder ----
class M {
  constructor() { this.el = []; }
  /** colour: name, or {all, top, bottom, n,s,e,w, ends:'x'|'z'(+endc)}; rot: [axis, angle, [ox,oy,oz]] */
  box(f, t, col, rot) {
    const c = typeof col === "string" ? { all: col } : col;
    const faces = {};
    const side = { north: c.n, south: c.s, east: c.e, west: c.w, up: c.top, down: c.bottom };
    if (c.ends === "x") side.east = side.west = c.endc;
    if (c.ends === "z") side.north = side.south = c.endc;
    for (const k of ["north", "south", "east", "west", "up", "down"]) faces[k] = { uv: uvOf(side[k] ?? c.all), texture: "#0" };
    const e = { from: f.map(v => +v.toFixed(3)), to: t.map(v => +v.toFixed(3)), faces };
    if (rot) e.rotation = { axis: rot[0], angle: rot[1], origin: rot[2] };
    this.el.push(e);
    return this;
  }
  json() {
    return JSON.stringify({
      textures: this.tex ? { ...this.tex, particle: this.tex["4"] } : { "0": "pipifacilities:item/station_palette", particle: "pipifacilities:item/station_palette" },
      ...(this.cutout ? { render_type: "minecraft:cutout" } : {}),
      elements: this.el,
      display: { fixed: { rotation: [0, 0, 0], translation: [0, 0, 0], scale: [1, 1, 1] }, gui: { rotation: [30, 45, 0], scale: [0.45, 0.45, 0.45] } },
    });
  }
}
const check = m => { for (const e of m.el) for (const v of [...e.from, ...e.to]) if (v < -16 || v > 32) throw new Error("out of range " + v); };

// ---- shared parts ----
/** slatted wooden planter crate: vertical planks on every side, thick dark rim; interior floor at FY */
const FY = 4.6;
function crate(m) {
  m.box([0, 0, 0], [16, 1.2, 16], { all: "woodDark", top: "woodDark" });                       // base frame
  m.box([.4, 1.2, .4], [15.6, FY, 15.6], { all: "woodDark", top: "soilDark" });                 // dark backing behind the slats
  for (let i = 0; i < 8; i++) {                                                                  // 2-px planks, alternating shade, tiny gaps
    const a = i * 2 + .15, b = i * 2 + 1.85, c = i % 2 ? "wood" : "woodLight", h = FY - (i % 3 === 1 ? .25 : 0);
    m.box([a, 1.2, -.1], [b, h, .5], { all: c, top: "woodLight" });
    m.box([a, 1.2, 15.5], [b, h, 16.1], { all: c, top: "woodLight" });
    m.box([-.1, 1.2, a], [.5, h, b], { all: c, top: "woodLight" });
    m.box([15.5, 1.2, a], [16.1, h, b], { all: c, top: "woodLight" });
  }
  m.box([-.2, 2.2, -.2], [16.2, 2.9, .2], "woodDark"); m.box([-.2, 2.2, 15.8], [16.2, 2.9, 16.2], "woodDark");   // cross rails
  m.box([-.2, 2.2, .2], [.2, 2.9, 15.8], "woodDark"); m.box([15.8, 2.2, .2], [16.2, 2.9, 15.8], "woodDark");
  for (const [x, z] of [[-.3, -.3], [14.3, -.3], [-.3, 14.3], [14.3, 14.3]]) m.box([x, 0, z], [x + 2, FY + 1.2, z + 2], { all: "woodDark", top: "wood" });   // corner posts
  // thick dark rim
  m.box([-.4, FY, -.4], [16.4, FY + 1.5, 1.6], { all: "woodDark", top: "wood" });
  m.box([-.4, FY, 14.4], [16.4, FY + 1.5, 16.4], { all: "woodDark", top: "wood" });
  m.box([-.4, FY, 1.6], [1.6, FY + 1.5, 14.4], { all: "woodDark", top: "wood" });
  m.box([14.4, FY, 1.6], [16.4, FY + 1.5, 14.4], { all: "woodDark", top: "wood" });
}
const TOP = FY + 1.5;   // rim top
const cells = (n, lo, hi) => { const r = [], s = (hi - lo) / n; for (let i = 0; i < n; i++) for (let j = 0; j < n; j++) r.push([lo + s * (i + .5), lo + s * (j + .5), i, j, s]); return r; };

// ---- farms: slatted crate densely packed with the crop ----
// ---- textured wheat field (vanilla style: chunky boxes carrying 16x16 pixel-art textures) ----
const TEXN = ["station_wheat_side", "station_wheat_top", "station_field_plank", "station_field_soil", "station_wheat_tips"];
function decodeRGB(buf) {   // minimal PNG decoder (8-bit RGB / RGBA, non-interlaced)
  let p = 8, w = 0, h = 0, ct = 0; const idat = [];
  while (p < buf.length) { const len = buf.readUInt32BE(p), type = buf.toString("ascii", p + 4, p + 8), d = buf.subarray(p + 8, p + 8 + len); p += 12 + len;
    if (type === "IHDR") { w = d.readUInt32BE(0); h = d.readUInt32BE(4); ct = d[9]; } else if (type === "IDAT") idat.push(d); }
  const ch = ct === 6 ? 4 : 3, raw = zlib.inflateSync(Buffer.concat(idat)), st = w * ch, out = Buffer.alloc(h * st); let pos = 0;
  for (let y = 0; y < h; y++) { const f = raw[pos++]; for (let i = 0; i < st; i++) {
    const a = i >= ch ? out[y * st + i - ch] : 0, b = y ? out[(y - 1) * st + i] : 0, c = y && i >= ch ? out[(y - 1) * st + i - ch] : 0; let v = raw[pos + i];
    if (f === 1) v += a; else if (f === 2) v += b; else if (f === 3) v += (a + b) >> 1; else if (f === 4) { const pp = a + b - c, pa = Math.abs(pp - a), pb = Math.abs(pp - b), pc = Math.abs(pp - c); v += pa <= pb && pa <= pc ? a : pb <= pc ? b : c; }
    out[y * st + i] = v & 255; } pos += st; }
  const px = []; for (let i = 0; i < w * h; i++) px.push([out[i * ch], out[i * ch + 1], out[i * ch + 2]]);
  return { w, h, px };
}
function makeFieldTextures() {
  const put = (name, fn) => {
    const px = new Uint8Array(16 * 16 * 4);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) { const c = fn(x, y), k = (y * 16 + x) * 4; px[k] = c[0]; px[k + 1] = c[1]; px[k + 2] = c[2]; px[k + 3] = 255; }
    fs.writeFileSync(path.join(texDir, name + ".png"), png(px, 16, 16));
  };
  // wheat side: the user's pixel art, used as is
  const sideSrc = path.join(here, "station/wheat_side.png");
  fs.copyFileSync(sideSrc, path.join(texDir, "station_wheat_side.png"));
  const side = decodeRGB(fs.readFileSync(sideSrc));
  // wheat top: wheat-head colours sampled from the upper rows of the side art, scattered (golden + green speckles)
  const pool = side.px.slice(0, 16 * 7);
  put("station_wheat_top", (x, y) => pool[((x * 7 + y * 13 + ((x ^ y) * 5)) * 2654435761 >>> 0) % pool.length]);
  // plank: 4 horizontal planks (4 px tall), dark seam on the last row of each, staggered vertical joints, oak-like noise
  const wood = [[0xa6, 0x82, 0x4e], [0x9a, 0x77, 0x46], [0x8c, 0x6a, 0x3c], [0xb3, 0x8f, 0x58]], seam = [0x5f, 0x45, 0x26];
  put("station_field_plank", (x, y) => {
    const row = y >> 2, ly = y & 3, joint = [5, 12, 2, 9][row];
    if (ly === 3) return seam;
    if (x === joint) return [0x6b, 0x4f, 0x2c];
    const pl = row * 2 + (x > joint ? 1 : 0), n = ((x * 31 + y * 17 + pl * 7) * 2654435761 >>> 0) % 100;
    let c = wood[(pl + (n > 78 ? 2 : n < 18 ? 1 : 0)) % 3];
    if (ly === 0 && n % 3 === 0) c = wood[3];
    if (n > 94) c = [0x7d, 0x5e, 0x34];
    return c;
  });
  // soil: tilled farmland-like, dark brown, darker furrow lines along x every 4 px
  const dirt = [[0x6e, 0x4a, 0x2f], [0x62, 0x41, 0x28], [0x78, 0x53, 0x35]], furrow = [[0x3e, 0x28, 0x18], [0x47, 0x2e, 0x1b]];
  put("station_field_soil", (x, y) => {
    const n = ((x * 29 + y * 41 + 5) * 2654435761 >>> 0) % 100;
    if (y % 4 === 1) return furrow[n % 2];
    if (y % 4 === 2 && n < 30) return furrow[1];
    return dirt[n < 30 ? 1 : n > 72 ? 2 : 0];
  });
}
/** 16x16 RGBA: sparse wheat tips / grass blades growing up from the bottom edge (transparent elsewhere) */
function makeTipsTexture() {
  const px = new Uint8Array(16 * 16 * 4), H = x => ((x * 2654435761 + 97) >>> 0) % 1000;
  const stalk = [[0x6f, 0x7c, 0x30], [0x5a, 0x66, 0x26]], mid = [[0x97, 0xa0, 0x3c], [0xa9, 0xb2, 0x4a]], gold = [[0xd9, 0xb2, 0x3c], [0xc9, 0x9a, 0x2e]], pale = [[0xe8, 0xd8, 0x84], [0xdc, 0xc4, 0x70]];
  for (let x = 0; x < 16; x++) {
    const r = H(x * 31 + 7), dense = x % 3 !== 1;
    if (!dense && r % 3) continue;
    const h = dense ? 4 + (r % 7) : 3 + (r % 4);
    for (let d = 0; d < h; d++) {
      const y = 15 - d, f = d / (h - 1), n = H(x * 17 + d * 5) % 2;
      const c = f < .3 ? stalk[n] : f < .65 ? mid[n] : f < .95 ? gold[n] : pale[n];
      const k = (y * 16 + x) * 4; px[k] = c[0]; px[k + 1] = c[1]; px[k + 2] = c[2]; px[k + 3] = 255;
    }
  }
  fs.writeFileSync(path.join(texDir, "station_wheat_tips.png"), png(px, 16, 16));
}
function wheatFarm() {
  makeFieldTextures(); makeTipsTexture();
  const m = new M(), WF = 3, SY = 2.2;
  m.tex = Object.fromEntries(TEXN.map((n, i) => [String(i + 1), "pipifacilities:item/" + n]));
  /** textured box; faces: {north:[tex,uv],...}; tex is 1..4 */
  const tb = (f, t, faces, rot) => {
    const fc = {}; for (const [k, [tx, uv]] of Object.entries(faces)) fc[k] = { uv, texture: "#" + tx };
    const e = { from: f, to: t, faces: fc }; if (rot) e.rotation = { axis: rot[0], angle: rot[1], origin: rot[2] }; m.el.push(e);
  };
  const [SIDE, TOPT, PLANK, SOIL, TIPS] = [1, 2, 3, 4, 5];
  m.cutout = true;
  // 3x2-cell field (24x16 px; cells 1 2 3 / 4 5 6, 8 px each) with wheat blocks in cells 1, 3, 5 (checker). Built in 0..24 x 0..16, shifted by -4 in x at the end
  // so the model is centred on the ItemDisplay origin (FIXED centres the 0..16 cube); plugin scale for this facility is 2 (24 px = 3 blocks).
  // frame: 4 plank walls (1 px thick, 3 px tall) around a tilled soil bed
  const wall = (x0, z0, x1, z1) => tb([x0, 0, z0], [x1, WF, z1], {
    north: [PLANK, [0, 2, Math.min(16, x1 - x0), 5]], south: [PLANK, [0, 2, Math.min(16, x1 - x0), 5]], east: [PLANK, [0, 2, Math.min(16, z1 - z0), 5]], west: [PLANK, [0, 2, Math.min(16, z1 - z0), 5]],
    up: [PLANK, [0, 0, Math.min(16, x1 - x0), Math.min(16, z1 - z0)]], down: [PLANK, [0, 0, Math.min(16, x1 - x0), Math.min(16, z1 - z0)]],
  });
  wall(0, 0, 24, 1); wall(0, 15, 24, 16); wall(0, 1, 1, 15); wall(23, 1, 24, 15);
  tb([1, 0, 1], [23, SY, 15], { up: [SOIL, [0, 2, 16, 14]], north: [SOIL, [0, 0, 16, 2]], south: [SOIL, [0, 0, 16, 2]], east: [SOIL, [0, 0, 14, 2]], west: [SOIL, [0, 0, 14, 2]], down: [SOIL, [0, 2, 16, 14]] });
  // three hollow wheat blocks (5x5, 6 px tall) centred in cells 1, 3, 5: 4 zero-thickness wall planes with the wheat side art on both faces, no top, grass-tip fringe (4 px) along each top edge
  for (const [cx, cz] of [[4, 4], [20, 4], [12, 12]]) {
    const w = 5, d = 5, x0 = cx - w / 2, z0 = cz - d / 2, y1 = SY + 6, TH = 4, vr = 9;
    const wp = (from, to, fa, tex, uv) => { const e = { from, to, faces: {} }; for (const f of fa) e.faces[f] = { uv, texture: "#" + tex }; m.el.push(e); };
    const sideUV = [3, 0, 13, vr], tipUV = [3, 6, 13, 16];
    wp([x0, SY, z0], [x0 + w, y1, z0], ["north", "south"], SIDE, sideUV);
    wp([x0, SY, z0 + d], [x0 + w, y1, z0 + d], ["north", "south"], SIDE, sideUV);
    wp([x0, SY, z0], [x0, y1, z0 + d], ["east", "west"], SIDE, sideUV);
    wp([x0 + w, SY, z0], [x0 + w, y1, z0 + d], ["east", "west"], SIDE, sideUV);
    wp([x0, y1, z0], [x0 + w, y1 + TH, z0], ["north", "south"], TIPS, tipUV);
    wp([x0, y1, z0 + d], [x0 + w, y1 + TH, z0 + d], ["north", "south"], TIPS, tipUV);
    wp([x0, y1, z0], [x0, y1 + TH, z0 + d], ["east", "west"], TIPS, tipUV);
    wp([x0 + w, y1, z0], [x0 + w, y1 + TH, z0 + d], ["east", "west"], TIPS, tipUV);
  }
  for (const e of m.el) { e.from[0] -= 4; e.to[0] -= 4; }
  return m;
}
function carrotFarm() {
  const m = new M(); crate(m);
  m.box([1.2, FY - .5, 1.2], [14.8, FY + 3.2, 14.8], { all: "leafDark", top: "leafDark" });
  for (const [x, z, i, j] of cells(7, 1.8, 14.2)) {
    const h = 3 + ((i * 5 + j * 3) % 4) * .8, y = TOP - 1;
    m.box([x - .9, y, z - .9], [x + .9, y + h, z + .9], { all: (i + j) % 2 ? "leaf" : "leafDark", top: "leafLight" });
    m.box([x - 1.6, y + h - 1, z - .15], [x + 1.6, y + h - .4, z + .15], "leafLight", ["z", 22.5, [x, y + h - 1, z]]);
    m.box([x - .15, y + h - 1.2, z - 1.6], [x + .15, y + h - .6, z + 1.6], "leaf", ["x", 22.5, [x, y + h - 1.2, z]]);
    if ((i + j) % 3 === 0) m.box([x - .6, y - .3, z - .6], [x + .6, y + .9, z + .6], { all: "carrot", top: "carrotDark" });   // orange carrot shoulder
  }
  return m;
}
function potatoFarm() {
  const m = new M(); crate(m);
  m.box([1.2, FY - .5, 1.2], [14.8, FY + 3, 14.8], { all: "leafDark", top: "leafDark" });
  for (const [x, z, i, j, s] of cells(4, 1.4, 14.6)) {
    const y = TOP - 1, h = 3.4 + ((i + j * 2) % 3) * .8, hs = s / 2 - .05;
    m.box([x - hs, y, z - hs], [x + hs, y + h, z + hs], { all: (i + j) % 2 ? "leaf" : "leafDark", top: "leafLight" });
    m.box([x - hs * .8, y + h, z - hs * .8], [x + hs * .8, y + h + 1.2, z + hs * .8], { all: "leafLight", top: "leafLight" }, ["y", 22.5, [x, y + h, z]]);
    m.box([x - hs * .5, y + h + 1.2, z - hs * .5], [x + hs * .5, y + h + 2, z + hs * .5], { all: "leaf", top: "leafLight" }, ["y", 45, [x, y + h + 1.2, z]]);
    if ((i + j) % 2 === 0) m.box([x + hs - .4, y - .4, z + hs - .5], [x + hs + .9, y + .7, z + hs + .6], "potato");
  }
  return m;
}
function cabbageFarm() {
  const m = new M(); crate(m);
  m.box([1.2, FY - .5, 1.2], [14.8, FY + 2, 14.8], { all: "cabbageDark", top: "cabbageDark" });
  for (const [x, z, i, j, s] of cells(4, 1.3, 14.7)) {
    const y = TOP - 1, h = s - .1 + ((i + j) % 2) * .5, hs = s / 2 - .05;
    m.box([x - hs, y, z - hs], [x + hs, y + h, z + hs], { all: "cabbage", top: "cabbageLight" });
    m.box([x - hs * .8, y + h, z - hs * .8], [x + hs * .8, y + h + .8, z + hs * .8], { all: "cabbageLight", top: "cabbageLight" }, ["y", 45, [x, y + h, z]]);
    m.box([x - hs - .35, y + h * .4, z - .12], [x + hs + .35, y + h * .4 + .45, z + .12], "cabbageDark", ["y", 22.5, [x, y, z]]);
    m.box([x - .12, y + h * .6, z - hs - .35], [x + .12, y + h * .6 + .45, z + hs + .35], "cabbageDark", ["y", -22.5, [x, y, z]]);
  }
  return m;
}

// ---- quarries: dark stone pillar with big faceted crystals/ore chunks jutting out diagonally + gold nuggets ----
const KINDS = {
  diamond: { c: ["cyan", "cyanDark", "cyanLight"], len: [6.5, 5.5, 4], tip: true },
  amber:   { c: ["amber", "amberDark", "amberLight"], len: [6.5, 5.5, 4], tip: true },
  iron:    { c: ["iron", "rust", "ironLight"], len: [4, 3.4, 2.6], tip: false },
  gold:    { c: ["gold", "goldDark", "goldLight"], len: [5.2, 4.4, 3.2], tip: true },
};
/** crystal growing out of (bx,by,bz) leaning away from the pillar; dir: w/e/n/s. one-axis rotation only (z+ leans -x, x+ leans +z) */
function crystal(m, k, bx, by, bz, dir, ang, wd, scale = 1) {
  const [c, d, l] = k.c, ax = dir === "w" || dir === "e" ? "z" : "x";
  const sign = dir === "w" || dir === "s" ? 1 : -1;
  const rot = [ax, sign * ang, [bx, by, bz]], w = wd * scale / 2;
  let y = by;
  const seg = (len, ww, cc, top) => { m.box([bx - ww, y, bz - ww], [bx + ww, y + len * scale, bz + ww], { all: cc, top }, rot); y += len * scale; };
  seg(k.len[0], w, c, l);
  seg(k.len[1], w * .82, k.tip ? l : d, l);
  seg(k.len[2], w * .6, k.tip ? c : d, l);
  if (k.tip) seg(1.6, w * .3, l, l);
  m.box([bx + w * .6, by + .5, bz - w * .9], [bx + w * 1.05, by + k.len[0] * scale, bz + w * .9], { all: d }, rot);   // dark facet strip
}
function quarry(kind) {
  const m = new M(), k = KINDS[kind];
  m.box([1.5, 0, 1.5], [14.5, 1.6, 14.5], { all: "stoneDark", top: "stone" });
  m.box([4.2, 1.6, 4.2], [11.8, 8, 11.8], { all: "stone", top: "stoneLight" });
  m.box([4.8, 8, 4.8], [11.2, 14, 11.2], { all: "stoneBlack", top: "stoneDark" });
  m.box([5.2, 14, 5.2], [10.8, 17.6, 10.8], { all: "stoneDark", top: "stone" });
  m.box([6, 17.6, 6], [10, 18.6, 10], { all: "stone", top: "stoneLight" });
  m.box([3.7, 3, 5.4], [4.3, 9.5, 8.4], "stoneBlack"); m.box([11.7, 5, 7.6], [12.3, 12, 10.6], "stoneBlack");
  m.box([6, 2, 3.7], [9.4, 7, 4.3], "stoneDark"); m.box([6.8, 3, 11.7], [10.2, 10, 12.3], "stoneDark");
  crystal(m, k, 4.8, 5, 8, "w", 45, 3.6);
  crystal(m, k, 11.2, 7, 8, "e", 22.5, 3.4);
  crystal(m, k, 8, 4.5, 11.2, "s", 45, 3.2, .9);
  crystal(m, k, 7.5, 9, 4.8, "n", 22.5, 3, .85);
  crystal(m, k, 10.8, 14, 8, "e", 45, 2.6, .8);
  crystal(m, k, 5.2, 15, 8, "w", 22.5, 2.4, .7);
  for (const [x, z, s, r] of [[1, 3, 1.5, 22.5], [12.5, 2, 1.3, 0], [13, 13, 1.6, 22.5], [2, 13, 1.2, 0], [7, 14, 1.2, 45], [14, 7.5, 1.1, 0], [3, 7, 1.1, 22.5]])
    m.box([x, 1.6, z], [x + s, 1.6 + s * .8, z + s], { all: "gold", top: "goldLight" }, r ? ["y", r, [x, 1.6, z]] : undefined);
  for (const [x, z, s] of [[.6, .6, 2], [13.6, .8, 1.8], [.8, 13.8, 1.7], [13.8, 13.6, 2]]) m.box([x, 0, z], [x + s, s * .9, z + s], { all: "stoneDark", top: "stone" });
  return m;
}

// ---- lumber yards: one huge tree (trunk ~7 px, canopy up to y=32), roots + stump + log at the foot ----
function lumber(red) {
  const bk = red ? "rbark" : "bark", bkd = red ? "rbarkDark" : "barkDark", rg = red ? "rring" : "ring", rgd = red ? "rringDark" : "ringDark", le = red ? "rlogEnd" : "logEnd";
  const lf = red ? "rleaf" : "leaf", lfd = red ? "rleafDark" : "leafDark", lfl = red ? "rleafLight" : "leafLight";
  const m = new M();
  // roots (footprint stays inside 0..16)
  m.box([1, 0, 6], [5.5, 2.6, 9.6], { all: bk, top: bkd });
  m.box([.4, 0, 6.8], [2.4, 1.2, 8.8], { all: bkd, top: bk });
  m.box([10.5, 0, 6.2], [15, 2.8, 9.8], { all: bk, top: bkd });
  m.box([14, 0, 7], [15.8, 1.2, 9], { all: bkd, top: bk });
  m.box([6.2, 0, 1], [9.8, 2.6, 5.5], { all: bk, top: bkd });
  m.box([6.9, 0, .3], [8.9, 1.2, 2.2], { all: bkd, top: bk });
  m.box([6, 0, 10.5], [9.6, 2.4, 15], { all: bk, top: bkd });
  m.box([3, 0, 3], [6, 2, 6], { all: bkd, top: bk }, ["y", 45, [4.5, 0, 4.5]]);
  m.box([10, 0, 10], [13, 2, 13], { all: bkd, top: bk }, ["y", 45, [11.5, 0, 11.5]]);
  // trunk: wide base, slightly slimmer upper part, bark strips stick out for texture
  m.box([4.5, 0, 4.5], [11.5, 11, 11.5], { all: bk, top: bkd });
  m.box([5, 11, 5], [11, 25, 11], { all: bk, top: bkd });
  for (const [a, b] of [[5.6, 7.2], [8.6, 10.4]]) {
    m.box([a, 0, 4.2], [b, 9, 4.5], bkd); m.box([a + .5, 3, 11.5], [b + .3, 13, 11.8], bkd);
    m.box([4.2, 2, a + .4], [4.5, 11, b + .4], bkd); m.box([11.5, 0, a], [11.8, 8, b], bkd);
    m.box([a + .2, 11, 4.7], [b - .2, 22, 5], bkd); m.box([4.7, 12, a + .6], [5, 23, b + .4], bkd);
  }
  m.box([6.4, 5, 4.2], [9.6, 7.2, 4.5], { all: bk }); m.box([11.5, 12, 6], [11.8, 15, 9], { all: bk });
  // two heavy branches into the canopy
  m.box([10.6, 16, 6.4], [16.8, 18.4, 9.6], { all: bk, top: bkd }, ["z", 22.5, [10.6, 16, 8]]);
  m.box([-.8, 17, 6.4], [5.4, 19.4, 9.6], { all: bk, top: bkd }, ["z", -22.5, [5.4, 17, 8]]);
  // canopy: layered leaf boxes, offsets differ per layer so no faces are coplanar
  const L = (f, t, c = {}) => m.box(f, t, { all: lf, top: lfl, bottom: lfd, ...c });
  L([-4, 18, -3], [20, 23.4, 19]);
  L([-6.2, 19.4, 1.5], [-3.6, 25, 14]);  L([18.4, 20, 3.5], [22.2, 26, 15]);  L([2, 19.8, -5.6], [13, 25.2, -2.6]);  L([3.5, 20.2, 17.6], [15, 24.6, 21.6]);
  L([-5, 23.4, -5], [21, 28.2, 20.6]);
  L([-2.6, 28.2, -1.8], [18.6, 30.6, 17.6]);
  L([1.5, 30.6, 1.2], [14, 32, 14.8], { top: lfl });
  L([-2, 25, 7], [0.4, 27.4, 15.6]); L([15.2, 27, -1], [19.8, 29, 10]);
  // hanging leaf clumps under the canopy, light-top blobs on top
  for (const [x, z, s, h] of [[-3, 2, 3.6, 3.4], [17, 14, 3.2, 3], [1, 16.6, 3, 2.6], [15, -1.5, 3.4, 3.6], [-2.2, 12, 2.6, 2.2], [18, 4.5, 2.4, 2.4], [8, 18.6, 2.8, 2.4], [8.2, -2, 2.6, 2.8]])
    m.box([x, 18 - h, z], [x + s, 18.2, z + s], { all: lfd, top: lf });
  for (const [x, z, s] of [[2, 3, 2.2], [10, 9, 2.4], [14, 2, 2], [4, 12, 2.2]]) m.box([x, 32 - .01 - s * .0, z], [x + s, 32, z + s], { all: lfl, top: lfl });
  // foot: a log lying along z, chips
  m.box([12.2, 0, 10.2], [15.4, 3.2, 15.8], { all: bk, top: bkd, ends: "z", endc: le });
  for (const [x, z, s] of [[1.5, 1.5, 1], [3.4, 8, .8], [12, 4, .9], [11.6, 1, 1], [.8, 9.6, .8], [14.6, 9.6, .8], [9.8, 5.2, .7], [5.4, 14.6, .8]])
    m.box([x, 0, z], [x + s * 1.4, .5, z + s], { all: x > 6 ? "chipDark" : "chip", top: "chip" }, ["y", [0, 22.5, 45, -22.5][Math.floor(x * 7) % 4], [x, 0, z]]);
  return m;
}

const models = {
  station_farm: wheatFarm(), station_carrot_farm: carrotFarm(), station_potato_farm: potatoFarm(), station_cabbage_farm: cabbageFarm(),
  // quarries (station_mine / _diamond_mine / _amber_quarry / _gold_mine) are written by make-rock-pile-3x3.mjs
  station_lumber: lumber(false), station_redwood_lumber: lumber(true),
};
// strict validation of the vanilla model rules (violations render as the missing-model cube)
const ANG = [-45, -22.5, 0, 22.5, 45];
function validate(name, json) {
  const j = JSON.parse(json), err = s => { throw new Error(name + ": " + s); };
  for (const [k, v] of Object.entries(j.textures)) if (v !== "pipifacilities:item/station_palette" && !/^pipifacilities:item[/]station_(wheat_side|wheat_top|field_plank|field_soil|wheat_tips)$/.test(v)) err("bad texture " + k);
  j.elements.forEach((e, i) => {
    const w = s => err(`element ${i}: ${s}`);
    for (const v of [...e.from, ...e.to]) if (!Number.isFinite(v) || v < -16 || v > 32) w("out of range " + v);
    if (e.from.some((v, a) => v > e.to[a])) w("from > to");
    if (e.rotation) {
      if (!ANG.includes(e.rotation.angle)) w("rotation angle " + e.rotation.angle);
      if (!["x", "y", "z"].includes(e.rotation.axis)) w("rotation axis");
      if (!Array.isArray(e.rotation.origin) || e.rotation.origin.length !== 3 || e.rotation.origin.some(v => !Number.isFinite(v))) w("rotation origin");
      if (Object.keys(e.rotation).some(k => !["axis", "angle", "origin"].includes(k))) w("rotation keys");
    }
    for (const [f, d] of Object.entries(e.faces)) {
      if (!["north", "south", "east", "west", "up", "down"].includes(f)) w("face " + f);
      if (!/^#[0-5]$/.test(d.texture) || !(d.texture.slice(1) in j.textures)) w("face texture");
      if (d.uv.length !== 4 || d.uv.some(v => !Number.isFinite(v) || v < 0 || v > 16)) w("bad uv " + d.uv);
    }
  });
  if (!j.elements.length) err("no elements");
}
for (const [n, m] of Object.entries(models)) {
  check(m);
  const json = m.json();
  validate(n, json);
  fs.writeFileSync(path.join(modDir, n + ".json"), json);
  console.log(n, m.el.length, "elements OK");
}
