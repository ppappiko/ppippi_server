// Reusable helpers for generating Blockbench .bbmodel pets (ESM, no deps).
import zlib from "node:zlib";
import crypto from "node:crypto";

export function uuid() {
  return crypto.randomUUID();
}

function clamp(v) {
  return v < 0 ? 0 : v > 255 ? 255 : Math.round(v);
}

export function hex(s) {
  s = s.replace("#", "");
  return [parseInt(s.slice(0, 2), 16), parseInt(s.slice(2, 4), 16), parseInt(s.slice(4, 6), 16)];
}

export function shade(c, d) {
  return [clamp(c[0] + d), clamp(c[1] + d), clamp(c[2] + d)];
}

function noise(x, y) {
  let h = (Math.imul(x, 374761393) + Math.imul(y, 668265263)) | 0;
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  h = (h ^ (h >>> 16)) >>> 0;
  return h / 4294967295;
}

// ---------------------------------------------------------------------------
// Canvas
// ---------------------------------------------------------------------------
export class Canvas {
  constructor(width, height) {
    this.width = width;
    this.height = height;
    this.data = new Uint8Array(width * height * 4);
  }

  pixel(x, y, c) {
    x = Math.round(x);
    y = Math.round(y);
    if (x < 0 || y < 0 || x >= this.width || y >= this.height) return;
    const i = (y * this.width + x) * 4;
    this.data[i] = c[0];
    this.data[i + 1] = c[1];
    this.data[i + 2] = c[2];
    this.data[i + 3] = c.length > 3 ? c[3] : 255;
  }

  fillRect(x, y, w, h, c) {
    for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) this.pixel(x + i, y + j, c);
  }
}

// ---------------------------------------------------------------------------
// PNG encoder (RGBA8, filter 0)
// ---------------------------------------------------------------------------
function crc32(buf) {
  let crc = 0xffffffff;
  for (let i = 0; i < buf.length; i++) {
    crc ^= buf[i];
    for (let j = 0; j < 8; j++) crc = (crc >>> 1) ^ (0xedb88320 & -(crc & 1));
  }
  return (crc ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length, 0);
  const typeBuf = Buffer.from(type, "ascii");
  const crcBuf = Buffer.alloc(4);
  crcBuf.writeUInt32BE(crc32(Buffer.concat([typeBuf, data])), 0);
  return Buffer.concat([len, typeBuf, data, crcBuf]);
}

export function encodePNG(canvas) {
  const { width: w, height: h } = canvas;
  const sig = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0);
  ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8;
  ihdr[9] = 6;
  const raw = Buffer.alloc(h * (w * 4 + 1));
  for (let y = 0; y < h; y++) {
    const off = y * (w * 4 + 1);
    raw[off] = 0;
    for (let x = 0; x < w * 4; x++) raw[off + 1 + x] = canvas.data[y * w * 4 + x];
  }
  const idat = zlib.deflateSync(raw, { level: 9 });
  return Buffer.concat([sig, chunk("IHDR", ihdr), chunk("IDAT", idat), chunk("IEND", Buffer.alloc(0))]);
}

// ---------------------------------------------------------------------------
// UV atlas: shelf packer giving every face its own island.
// ---------------------------------------------------------------------------
export class Atlas {
  constructor(width, height, padding = 0) {
    this.width = width;
    this.height = height;
    this.padding = padding;
    this.cursorX = 0;
    this.cursorY = 0;
    this.rowH = 0;
  }

  alloc(w, h) {
    const p = this.padding;
    w = Math.ceil(w);
    h = Math.ceil(h);
    const totalW = w + p * 2;
    if (this.cursorX + totalW > this.width) {
      this.cursorY += this.rowH;
      this.cursorX = 0;
      this.rowH = 0;
    }
    if (this.cursorY + h + p * 2 > this.height) throw new Error(`UV atlas overflow packing ${w}x${h}`);
    const rect = { x: this.cursorX + p, y: this.cursorY + p, w, h };
    this.cursorX += totalW;
    this.rowH = Math.max(this.rowH, h + p * 2);
    return rect;
  }
}

// ---------------------------------------------------------------------------
// Per-face painter: base color + soft shading + dithering + bottom rim.
// ---------------------------------------------------------------------------
export function paintFace(canvas, rect, base, kind) {
  let col = base;
  if (kind === "up") col = shade(base, 20);
  else if (kind === "down") col = shade(base, -24);
  else col = shade(base, 0);
  canvas.fillRect(rect.x, rect.y, rect.w, rect.h, col);
  for (let j = 0; j < rect.h; j++) {
    for (let i = 0; i < rect.w; i++) {
      const n = noise(rect.x + i, rect.y + j);
      if (n > 0.82) canvas.pixel(rect.x + i, rect.y + j, shade(col, 5));
      else if (n < 0.18) canvas.pixel(rect.x + i, rect.y + j, shade(col, -6));
    }
  }
  if (kind === "side" && rect.h > 1) {
    canvas.fillRect(rect.x, rect.y + rect.h - 1, rect.w, 1, shade(base, -46));
  }
}

// ---------------------------------------------------------------------------
// Builder
// ---------------------------------------------------------------------------
export class Builder {
  constructor(opts = {}) {
    this.width = opts.width ?? 64;
    this.height = opts.height ?? 64;
    this.padding = opts.padding ?? 0;
    this.canvas = new Canvas(this.width, this.height);
    this.elements = [];
    this.animations = [];
    this.outliner = [];
    this._faces = [];
    this._finalized = false;
  }

  cube(name, from, to, opts = {}) {
    const [x1, y1, z1] = from;
    const [x2, y2, z2] = to;
    const w = x2 - x1;
    const h = y2 - y1;
    const d = z2 - z1;
    const base = opts.base ?? [255, 255, 255];
    const faceColors = opts.faces ?? {};
    const element = {
      name,
      uuid: uuid(),
      type: "cube",
      from: [...from],
      to: [...to],
      origin: opts.origin ? [...opts.origin] : [(x1 + x2) / 2, (y1 + y2) / 2, (z1 + z2) / 2],
      faces: {},
    };
    if (opts.rotation) element.rotation = [...opts.rotation];
    if (opts.inflate) element.inflate = opts.inflate;

    const sizes = {
      north: [w, h],
      south: [w, h],
      east: [d, h],
      west: [d, h],
      up: [w, d],
      down: [w, d],
    };
    const kinds = { north: "side", south: "side", east: "side", west: "side", up: "up", down: "down" };

    element.handles = {};
    for (const dir of ["north", "south", "east", "west", "up", "down"]) {
      const [fw, fh] = sizes[dir];
      const spec = { w: fw, h: fh, base: faceColors[dir] ?? base, kind: kinds[dir], element, dir };
      this._faces.push(spec);
      const face = { uv: [0, 0, fw, fh], texture: 0 };
      if (opts.faceRotation && opts.faceRotation[dir]) face.rotation = opts.faceRotation[dir];
      element.faces[dir] = face;
      element.handles[dir] = spec;
    }
    this.elements.push(element);
    return element;
  }

  group(name, origin, children, opts = {}) {
    const g = {
      name,
      uuid: uuid(),
      origin: [...origin],
      rotation: opts.rotation ? [...opts.rotation] : [0, 0, 0],
      visibility: opts.visibility ?? true,
      children: children.map((c) => (c && c.type === "cube" ? c.uuid : c)),
    };
    return g;
  }

  animation(name, loop, length, animators) {
    this.animations.push({ name, loop, length, animators });
  }

  finalize() {
    if (this._finalized) return;
    const ordered = [...this._faces].sort((a, b) => b.h - a.h || b.w - a.w);
    const atlas = new Atlas(this.width, this.height, this.padding);
    for (const spec of ordered) {
      const rect = atlas.alloc(spec.w, spec.h);
      spec.rect = rect;
      paintFace(this.canvas, rect, spec.base, spec.kind);
      spec.element.faces[spec.dir].uv = [rect.x, rect.y, rect.x + rect.w, rect.y + rect.h];
    }
    this._finalized = true;
  }

  toJSON(name) {
    this.finalize();
    const elements = this.elements.map((e) => {
      const out = { ...e };
      delete out.handles;
      return out;
    });
    const source = "data:image/png;base64," + encodePNG(this.canvas).toString("base64");
    return {
      meta: { format_version: "4.10", model_format: "free" },
      name,
      box_uv: false,
      resolution: { width: this.width, height: this.height },
      elements,
      outliner: this.outliner,
      textures: [
        { path: "", name, folder: "", namespace: "", id: "0", particle: false, render_mode: "default", source },
      ],
      animations: this.animations,
    };
  }
}

export function keyframes(channel, keys, interp = "linear") {
  return keys.map(([time, x, y, z, override]) => ({
    channel,
    time,
    interpolation: override ?? interp,
    data_points: [{ x, y, z }],
  }));
}

export function bone(name, ...sets) {
  return { name, type: "bone", keyframes: sets.flat() };
}
