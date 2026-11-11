// Generates the capture-trap item textures AND the shared trap item model geometry.
//   node dev/art/traps/make-trap-textures.mjs
// Textures: dev/pipi-facilities/assets/pipifacilities/textures/item/trap_{wood,iron,diamond,bars,floor}.png
// Models:   dev/pipi-facilities/assets/pipifacilities/models/item/{basic,iron,diamond}_trap.json
// The art references are dev/art/traps/{basic_trap.png,iron_trap.png,diamond_trap.webp}.
// PNGs are encoded by hand with zlib (no python, no extra deps).
import fs from "node:fs";
import path from "node:path";
import zlib from "node:zlib";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const texDir = path.resolve(here, "../../pipi-facilities/assets/pipifacilities/textures/item");
const modelDir = path.resolve(here, "../../pipi-facilities/assets/pipifacilities/models/item");

// ---------------------------------------------------------------- PNG encoding
const crcTable = (() => {
  const t = new Int32Array(256);
  for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c; }
  return t;
})();
function crc32(buf) { let c = ~0; for (let i = 0; i < buf.length; i++) c = crcTable[(c ^ buf[i]) & 255] ^ (c >>> 8); return ~c >>> 0; }
function chunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const t = Buffer.from(type, "ascii");
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(Buffer.concat([t, data])));
  return Buffer.concat([len, t, data, crc]);
}
function encodePNG(w, h, rgba) {
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) { raw[y * (w * 4 + 1)] = 0; rgba.copy(raw, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4); }
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
  return Buffer.concat([
    Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
    chunk("IHDR", ihdr), chunk("IDAT", zlib.deflateSync(raw)), chunk("IEND", Buffer.alloc(0)),
  ]);
}

// ---------------------------------------------------------------- tiny canvas
const SIZE = 16;
const hex = s => [parseInt(s.slice(1, 3), 16), parseInt(s.slice(3, 5), 16), parseInt(s.slice(5, 7), 16), 255];
const shade = (c, f) => [Math.min(255, Math.round(c[0] * f)), Math.min(255, Math.round(c[1] * f)), Math.min(255, Math.round(c[2] * f)), c[3] ?? 255];
const newCanvas = () => Buffer.alloc(SIZE * SIZE * 4);
const set = (b, x, y, c) => { const i = (y * SIZE + x) * 4; b[i] = c[0]; b[i + 1] = c[1]; b[i + 2] = c[2]; b[i + 3] = c[3] ?? 255; };
const fill = (b, c) => { for (let y = 0; y < SIZE; y++) for (let x = 0; x < SIZE; x++) set(b, x, y, c); };

function frameTexture(bodyHex, edgeHex, grain) {
  const body = hex(bodyHex), edge = hex(edgeHex), buf = newCanvas();
  fill(buf, body);
  for (let i = 1; i < SIZE - 1; i++) { set(buf, i, 1, shade(body, 1.1)); set(buf, 1, i, shade(body, 1.1)); set(buf, SIZE - 2, i, shade(body, 0.92)); }
  if (grain) for (let k = 0; k < 12; k++) { const y = 2 + ((k * 5) % 11), x = 2 + ((k * 7) % 9); const c = shade(body, 0.88); set(buf, x, y, c); set(buf, x + 1, y, c); }
  for (let i = 0; i < SIZE; i++) { set(buf, i, 0, edge); set(buf, i, SIZE - 1, edge); set(buf, 0, i, edge); set(buf, SIZE - 1, i, edge); }
  return buf;
}
function barsTexture(bodyHex, darkHex) {
  const body = hex(bodyHex), dark = hex(darkHex), buf = newCanvas();
  fill(buf, body);
  for (let y = 0; y < SIZE; y++) for (let x = 0; x < SIZE; x++) {
    if (x < 2 || x > SIZE - 3) set(buf, x, y, dark);
    else if (x === 2 || x === SIZE - 3) set(buf, x, y, shade(body, 0.8));
    else if (x === SIZE >> 1) set(buf, x, y, shade(body, 1.15));
  }
  for (let x = 0; x < SIZE; x++) { set(buf, x, 0, shade(dark, 0.85)); set(buf, x, SIZE - 1, shade(dark, 0.85)); }
  return buf;
}
function floorTexture(bodyHex, gridHex) {
  const body = hex(bodyHex), grid = hex(gridHex), buf = newCanvas();
  fill(buf, body);
  for (let i = 0; i < SIZE; i += 4) for (let j = 0; j < SIZE; j++) { set(buf, j, i, grid); set(buf, i, j, grid); }
  return buf;
}

fs.mkdirSync(texDir, { recursive: true });
const textures = {
  trap_wood: frameTexture("#d9a262", "#8a5a2b", true),
  trap_iron: frameTexture("#e8e8e8", "#9c9c9c", false),
  trap_diamond: frameTexture("#72e6f0", "#2aa3b3", false),
  trap_amber: frameTexture("#f2a23a", "#b8621a", false),
  trap_bars: barsTexture("#8e8e8e", "#5e5e5e"),
  trap_floor: floorTexture("#3a3a3a", "#2a2a2a"),
};
for (const [name, buf] of Object.entries(textures)) {
  fs.writeFileSync(path.join(texDir, name + ".png"), encodePNG(SIZE, SIZE, buf));
  console.log("wrote texture", name + ".png");
}

// ---------------------------------------------------------------- trap model
// 16-unit space. Cage x2..14, z2..14, y0..11; rims 2 thick; 2x2 posts; 3x3x3 corner
// caps bulging 0.5 out; dark lid + floor panels; frame cross beam and arch handle;
// 1x1 bars on the four sides; 3x3x2 lock on the front.
const FRAME = "#frame", BARS = "#bars", FLOOR = "#floor";
const box = (from, to, tex) => {
  const faces = {};
  for (const f of ["north", "south", "east", "west", "up", "down"]) faces[f] = { uv: [0, 0, 16, 16], texture: tex };
  return { from, to, faces };
};

function elements() {
  const els = [];
  const rims = (y0, y1) => [
    [2, y0, 2, 14, y1, 4],
    [2, y0, 12, 14, y1, 14],
    [2, y0, 4, 4, y1, 12],
    [12, y0, 4, 14, y1, 12],
  ];
  for (const r of [...rims(0, 2), ...rims(9, 11)]) els.push(box([r[0], r[1], r[2]], [r[3], r[4], r[5]], FRAME));
  for (const p of [[2, 2, 2, 4, 9, 4], [12, 2, 2, 14, 9, 4], [2, 2, 12, 4, 9, 14], [12, 2, 12, 14, 9, 14]])
    els.push(box([p[0], p[1], p[2]], [p[3], p[4], p[5]], FRAME));
  for (const x of [[1.5, 4.5], [11.5, 14.5]])
    for (const z of [[1.5, 4.5], [11.5, 14.5]])
      for (const y of [[0, 3], [8, 11]])
        els.push(box([x[0], y[0], z[0]], [x[1], y[1], z[1]], FRAME));
  els.push(box([4, 10.3, 4], [12, 10.8, 12], FLOOR));
  els.push(box([4, 10.7, 6.5], [12, 11.3, 9.5], FRAME));
  els.push(box([4, 1.5, 4], [12, 2, 12], FLOOR));
  els.push(box([5, 11, 7], [6.5, 14, 9], FRAME));
  els.push(box([9.5, 11, 7], [11, 14, 9], FRAME));
  els.push(box([5, 13, 7], [11, 14.5, 9], FRAME));
  const vx = [[5, 6], [7.5, 8.5], [10, 11]];
  for (const [a, b] of vx) { els.push(box([a, 2, 2.5], [b, 9, 3.5], BARS)); els.push(box([a, 2, 12.5], [b, 9, 13.5], BARS)); }
  for (const [a, b] of vx) { els.push(box([2.5, 2, a], [3.5, 9, b], BARS)); els.push(box([12.5, 2, a], [13.5, 9, b], BARS)); }
  els.push(box([4, 5.5, 2.5], [12, 6.5, 3.5], BARS));
  els.push(box([4, 5.5, 12.5], [12, 6.5, 13.5], BARS));
  els.push(box([6.5, 4.5, 1.3], [9.5, 7.5, 3.3], FRAME));
  return els;
}

const display = {
  firstperson_righthand: { rotation: [0, 35, 0], translation: [1, -2, -2], scale: [0.65, 0.65, 0.65] },
  thirdperson_righthand: { rotation: [65, 45, 0], translation: [0, 1, 0], scale: [0.5, 0.5, 0.5] },
  gui: { rotation: [22, 35, 0], translation: [0, 0, 0], scale: [0.9, 0.9, 0.9] },
  ground: { scale: [0.4, 0.4, 0.4] },
};

fs.mkdirSync(modelDir, { recursive: true });
const base = { gui_light: "side", elements: elements(), display };
fs.writeFileSync(path.join(modelDir, "trap_base.json"), JSON.stringify(base, null, 2) + "\n");
console.log("wrote model trap_base.json", `(${base.elements.length} elements)`);
for (const [model, frame] of [["basic_trap", "wood"], ["iron_trap", "iron"], ["diamond_trap", "diamond"], ["amber_trap", "amber"]]) {
  const json = {
    parent: "pipifacilities:item/trap_base",
    textures: {
      frame: `pipifacilities:item/trap_${frame}`,
      bars: "pipifacilities:item/trap_bars",
      floor: "pipifacilities:item/trap_floor",
      particle: `pipifacilities:item/trap_${frame}`,
    },
  };
  fs.writeFileSync(path.join(modelDir, model + ".json"), JSON.stringify(json, null, 2) + "\n");
  console.log("wrote model", model + ".json");
}

// The old trap art is superseded by the custom trap_* textures above and nothing
// references it any more (checked under assets/ and dev/pipi-rpg), so drop it.
for (const legacy of ["basic_trap.png", "iron_trap.png", "diamond_trap.png"]) fs.rmSync(path.join(texDir, legacy), { force: true });
