// 통곡의 왕좌 props as ItemDisplay item models (model centre (8, 8, 8) = the display entity, 16 units = 1 block):
//   fx_sorrow_crystal  floating double-pointed amethyst crystal with rounded tips (the 통곡 수정), y -6 .. 24.5
//   fx_soul_shield     hollow translucent membrane bubble, radius 14 units (scale = radius in blocks * 16 / 14)
//   fx_soul_ring       flat glowing range ring on the floor at model y 8, radius 14 units (same scaling)
//   node dev/art/make-fx-sorrow-props.mjs           PREVIEW_DIR=<dir> also writes <dir>/fx_<name>.bbmodel for preview-pets.mjs
// Vanilla item-model rules: coordinates -16..32, rotations only 0/±22.5/±45 about one axis.
import fs from "node:fs";
import path from "node:path";
import zlib from "node:zlib";
import crypto from "node:crypto";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "../pipi-facilities/assets/pipifacilities");
const crcT = new Uint32Array(256).map((_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const crc = b => { let c = 0xffffffff; for (const x of b) c = crcT[(c ^ x) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (type, data) => { const t = Buffer.from(type, "ascii"), l = Buffer.alloc(4), c = Buffer.alloc(4); l.writeUInt32BE(data.length); c.writeUInt32BE(crc(Buffer.concat([t, data]))); return Buffer.concat([l, t, data, c]); };
function png(px, w = 16, h = 16) {
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) { raw[y * (w * 4 + 1)] = 0; for (let x = 0; x < w * 4; x++) raw[y * (w * 4 + 1) + 1 + x] = px[y * w * 4 + x]; }
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk("IHDR", ihdr), chunk("IDAT", zlib.deflateSync(raw)), chunk("IEND", Buffer.alloc(0))]);
}
let seed = 91;
const rnd = () => (seed = (seed * 1664525 + 1013904223) >>> 0) / 4294967296;
const hex = h => [1, 3, 5].map(i => parseInt(h.slice(i, i + 2), 16));
function paletteTexture(pal) {
  const px = new Uint8Array(16 * 16 * 4);
  pal.forEach((e, i) => {
    const o = typeof e === "string" ? { c: e } : e, base = hex(o.c), a = o.a ?? 255, cx = (i % 4) * 4, cy = Math.floor(i / 4) * 4;
    for (let y = 0; y < 4; y++) for (let x = 0; x < 4; x++) {
      const j = (rnd() - .5) * 2 * (o.noise ?? 0);
      let c = base.map(v => Math.max(0, Math.min(255, Math.round(v + j))));
      if (o.speck && rnd() < o.speck[1]) c = hex(o.speck[0]);
      const k = ((cy + y) * 16 + cx + x) * 4; px[k] = c[0]; px[k + 1] = c[1]; px[k + 2] = c[2]; px[k + 3] = a;
    }
  });
  return png(px);
}

class Model {
  constructor(name, pal, { shade = false } = {}) { this.name = name; this.pal = pal; this.els = []; this.shade = shade; }
  box(from, to, ci, rot) {
    const f = from.map((v, i) => Math.min(v, to[i])), t = from.map((v, i) => Math.max(v, to[i]));
    for (let i = 0; i < 3; i++) if (t[i] - f[i] < .2) t[i] = f[i] + .2;
    const col = ci % 4 * 4, row = Math.floor(ci / 4) * 4, face = { uv: [col, row, col + 4, row + 4], texture: "#0" };
    const el = { from: f.map(v => +v.toFixed(3)), to: t.map(v => +v.toFixed(3)), shade: this.shade, faces: { north: face, east: face, south: face, west: face, up: face, down: face } };
    if (rot && rot.angle) {
      if (![-45, -22.5, 22.5, 45].includes(rot.angle)) throw new Error(this.name + ": bad angle " + rot.angle);
      el.rotation = { angle: rot.angle, axis: rot.axis, origin: rot.origin ?? f.map((v, i) => (v + t[i]) / 2) };
    }
    this.els.push(el);
  }
  /** round slice: 8 narrow bars through the centre every 22.5 deg (16-gon outline) */
  round(y0, y1, r, ci, cx = 8, cz = 8) {
    const w = r * .2 + .15, o = [cx, (y0 + y1) / 2, cz];
    for (const a of [0, 22.5, 45, -22.5, -45]) this.box([cx - r, y0, cz - w], [cx + r, y1, cz + w], ci, a ? { axis: "y", angle: a, origin: o } : null);
    for (const a of [0, 22.5, -22.5]) this.box([cx - w, y0, cz - r], [cx + w, y1, cz + r], ci, a ? { axis: "y", angle: a, origin: o } : null);
  }
  /** a ring of 16 thin panels (radius R, height y0..y1, thickness th) */
  ring(y0, y1, R, th, ci, ciAlt = ci) {
    const D = Math.PI / 180, L = 2 * R * Math.tan(Math.PI / 16) + .35;
    for (let k = 0; k < 16; k++) {
      const thd = k * 22.5, x = 8 + Math.sin(thd * D) * R, z = 8 - Math.cos(thd * D) * R;
      let a = ((-thd % 90) + 90) % 90; if (a > 45) a -= 90;
      const swap = Math.abs(((thd % 180) + 180) % 180 - 90) < 44.9 || ((thd % 180) + 180) % 180 === 45;
      const w = swap ? th : L, d = swap ? L : th;
      this.box([x - w / 2, y0, z - d / 2], [x + w / 2, y1, z + d / 2], k % 3 ? ci : ciAlt, a ? { axis: "y", angle: a, origin: [x, (y0 + y1) / 2, z] } : null);
    }
  }
  write() {
    for (const e of this.els) for (const v of [...e.from, ...e.to]) if (!(v >= -16 && v <= 32)) throw new Error(this.name + ": coord out of -16..32 " + v);
    if (this.els.length > 400) throw new Error(this.name + " too many elements " + this.els.length);
    const tex = "pipifacilities:item/fx_" + this.name, id = { rotation: [0, 0, 0], translation: [0, 0, 0], scale: [1, 1, 1] };
    const texPng = paletteTexture(this.pal);
    fs.writeFileSync(path.join(root, "models/item", `fx_${this.name}.json`), JSON.stringify({ textures: { "0": tex, particle: tex }, elements: this.els,
      display: { none: id, fixed: id, gui: { rotation: [30, 45, 0], scale: [.4, .4, .4] } } }));
    fs.writeFileSync(path.join(root, "textures/item", `fx_${this.name}.png`), texPng);
    console.log(`fx_${this.name}: ${this.els.length} elements`);
    if (process.env.PREVIEW_DIR) {
      const elements = this.els.map(e => {
        const rot = [0, 0, 0]; if (e.rotation) rot["xyz".indexOf(e.rotation.axis)] = e.rotation.angle;
        const faces = {}; for (const [k, f] of Object.entries(e.faces)) faces[k] = { uv: f.uv, texture: 0 };
        const sh = v => v.map((q, i) => i === 1 ? q + 8 : q - 8);
        return { name: "c", uuid: crypto.randomUUID(), type: "cube", from: sh(e.from), to: sh(e.to), origin: e.rotation ? sh(e.rotation.origin) : [0, 8, 0], rotation: rot, faces };
      });
      const bb = { meta: { format_version: "4.5" }, resolution: { width: 16, height: 16 }, elements,
        outliner: [{ name: "root", uuid: crypto.randomUUID(), origin: [0, 0, 0], rotation: [0, 0, 0], children: elements.map(e => e.uuid) }],
        textures: [{ source: "data:image/png;base64," + texPng.toString("base64") }], animations: [{ name: "idle", loop: "loop", length: 1, animators: {} }] };
      fs.mkdirSync(process.env.PREVIEW_DIR, { recursive: true });
      fs.writeFileSync(path.join(process.env.PREVIEW_DIR, `fx_${this.name}.bbmodel`), JSON.stringify(bb));
    }
  }
}

// ---------------------------------------------------------------- 통곡 수정: one floating double-pointed amethyst crystal —
// a long convex upper point, a short straight waist, a shorter lower point. Each layer is two octagonal prisms
// turned 45 deg, so the facets alternate light / dark.
{
  const m = new Model("sorrow_crystal", [
    { c: "#9e82fb", noise: 2 },   // 0 crystal (one even colour)
    { c: "#7e62ee", noise: 3 },   // 1 facet B (shade)
    { c: "#d6c8ff", noise: 3 },   // 2 girdle
    { c: "#4b33a6", noise: 2 },   // 3 ring line
  ]);
  const prism = (y0, y1, r) => {   // one even colour; four squares turned 0 / 22.5 / 45 / -22.5 deg -> a near-round 16-sided outline
    const o = [8, (y0 + y1) / 2, 8], k = r * .9;
    m.box([8 - k, y0, 8 - k], [8 + k, y1, 8 + k], 0);
    for (const a of [22.5, 45, -22.5]) m.box([8 - k, y0, 8 - k], [8 + k, y1, 8 + k], 0, { axis: "y", angle: a, origin: o });
  };
  const R = 4, BOT = -6, G0 = 5, G1 = 8.6, TOP = 24.5;
  // thin layers, the same two facet colours all the way up, so the faces read as continuous long facets;
  // both ends finish in a small rounded dome instead of a needle point
  const half = (from, to, n, capR, capN) => {        // from = girdle edge, to = tip end (either direction)
    const dir = Math.sign(to - from), L = Math.abs(to - from), bodyL = L - capR;
    // the original long convex curve r = R cos(pi/2 * t), cut where it narrows to capR, then capped by the dome
    const tEnd = Math.acos(capR / R) * 2 / Math.PI;
    for (let i = 0; i < n; i++) {
      const t0 = i / n, t1 = (i + 1) / n, ya = from + dir * bodyL * t0, yb = from + dir * (bodyL * t1 + .04);
      const r = R * Math.cos(Math.PI / 2 * tEnd * (t0 + t1) / 2);
      prism(Math.min(ya, yb), Math.max(ya, yb), r * .98 + .05, 0, 1);
    }
    const c = from + dir * bodyL;                                   // dome: a quarter circle of radius capR
    for (let i = 0; i < capN; i++) {
      const u0 = i / capN, u1 = (i + 1) / capN, ya = c + dir * capR * u0 - dir * .03, yb = c + dir * capR * u1;
      const r = capR * Math.sqrt(Math.max(.04, 1 - ((u0 + u1) / 2) ** 2));
      prism(Math.min(ya, yb), Math.max(ya, yb), r, 0, 1);
    }
  };
  half(G0, BOT, 20, 1.1, 7);
  prism(G0, G1, R, 0, 1);
  half(G1, TOP, 28, 1.2, 7);
  m.write();
}

// ---------------------------------------------------------------- 보호막: a hollow translucent membrane bubble
{
  const m = new Model("soul_shield", [
    { c: "#c9b8ff", a: 88 },   // 0 membrane
    { c: "#e6dcff", a: 118 },  // 1 lighter cells
    { c: "#ffffff", a: 150 },  // 2 rim highlight
  ]);
  const R = 14, bands = 11;
  for (let i = 0; i < bands; i++) {
    const y0 = -R + i * (2 * R / bands), y1 = y0 + 2 * R / bands, yc = (y0 + y1) / 2;
    const r = Math.sqrt(Math.max(0, R * R - yc * yc));
    if (r < 4) { m.round(8 + (yc > 0 ? y1 - .25 : y0), 8 + (yc > 0 ? y1 : y0 + .25), r + 1, 1); continue; }
    m.ring(8 + y0, 8 + y1, r, .25, i % 2 ? 0 : 1, 2);
  }
  m.write();
}

// ---------------------------------------------------------------- 범위 고리: a glowing ring on the floor
{
  const m = new Model("soul_ring", [
    { c: "#ffffff", a: 235 },  // 0 bright core line
    { c: "#cbb6ff", a: 200 },  // 1 lilac
    { c: "#9d7cff", a: 90 },   // 2 faint outer glow
  ]);
  m.ring(8, 8.25, 14, .45, 0);
  m.ring(8, 8.15, 13.3, .6, 1);
  m.ring(8, 8.1, 14.6, .7, 2);
  m.write();
}
