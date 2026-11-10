// Pixel-art pet model library (vanilla-Minecraft style), ESM, no deps.
//
// Produces Blockbench .bbmodel files with hand-placed 1-texel-per-model-pixel
// faces. Import uuid/hex/encodePNG/Canvas from lib/bbmodel.mjs (left untouched).
//
// ---------------------------------------------------------------------------
// Face UV orientation (mirrors dev/pipi-facilities/src/PetModels.java):
//
// PetModels.drawCube emits, per face, 4 vertices each carrying (s,t) where
// s goes u1->u2 and t goes v1->v2, then samples u=u1+(u2-u1)*s, v=v1+(v2-v1)*t.
// Reading those vertices (x=min, X=max, y=min, Y=max, z=min, Z=max) gives:
//
//   north (seen from -Z): s=0 at +X, s=1 at -X; t=0 at top (y=Y), t=1 bottom
//   south (seen from +Z): s=0 at -X, s=1 at +X; t=0 top, t=1 bottom
//   east  (seen from +X): s=0 at +Z, s=1 at -Z; t=0 top, t=1 bottom
//   west  (seen from -X): s=0 at -Z, s=1 at +Z; t=0 top, t=1 bottom
//   up    (seen from +Y): s=0 at -X, s=1 at +X; t=0 at -Z (north), t=1 at +Z
//   down  (seen from -Y): s=0 at -X, s=1 at +X; t=0 at +Z (south), t=1 at -Z
//
// For every face the "left as seen from outside the cube" edge (the one at
// u1 / s=0) is: north +X, south -X, east +Z, west -Z, and the up/down column 0
// is -X per spec. So a grid written row0=top, col0=left maps uniformly with
// u = u1 + col, v = v1 + row: painting canvas pixel (rect.x+col, rect.y+row)
// reproduces grid cell (col,row) exactly as the game samples it. Row 0 of an
// `up` grid is the north (-Z) edge; row 0 of a `down` grid is the south (+Z)
// edge (that follows directly from t=0 above, no extra mirroring needed).
// ---------------------------------------------------------------------------
import fs from "node:fs";
import path from "node:path";
import { uuid, hex, encodePNG, Canvas, keyframes, bone } from "./bbmodel.mjs";

export { uuid, hex, encodePNG, Canvas, keyframes, bone };

const DIRS = ["north", "south", "east", "west", "up", "down"];

// Deterministic per-texel hash in [0,1). Same coords -> same value every build.
function texHash(x, y) {
  let h = (Math.imul(x + 0x9e3779b9, 374761393) ^ Math.imul(y + 0x85ebca6b, 668265263)) | 0;
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  h = (h ^ (h >>> 16)) >>> 0;
  return h / 4294967296;
}

function isHexToken(s) {
  return typeof s === "string" && (s.startsWith("#") || /^[0-9a-fA-F]{6}$/.test(s));
}

// Shelf packer: pads nothing, gives each face its own island, deterministic.
function shelfPack(specs, width, height) {
  const rects = new Map();
  let cx = 0;
  let cy = 0;
  let rowH = 0;
  for (const sp of specs) {
    const w = Math.ceil(sp.w);
    const h = Math.ceil(sp.h);
    if (cx + w > width) {
      cy += rowH;
      cx = 0;
      rowH = 0;
    }
    if (cy + h > height) return null;
    rects.set(sp, { x: cx, y: cy, w, h });
    cx += w;
    rowH = Math.max(rowH, h);
  }
  return rects;
}

export class VBuilder {
  constructor(opts = {}) {
    this.baseWidth = opts.width ?? 64;
    this.baseHeight = opts.height ?? 64;
    this.materials = new Map();
    this.legends = new Map();
    this.elements = [];
    this.animations = [];
    this.outliner = [];
    this._specs = [];
    this._finalized = false;
    this.width = this.baseWidth;
    this.height = this.baseHeight;
    this.canvas = null;
  }

  // Weighted noise palette: array of [hex, weight]; per-texel deterministic pick.
  material(name, palette) {
    if (!Array.isArray(palette) || palette.length === 0) throw new Error(`material ${name}: palette required`);
    for (const [c, wt] of palette) {
      if (!isHexToken(c)) throw new Error(`material ${name}: bad color ${c}`);
      if (!(wt > 0)) throw new Error(`material ${name}: bad weight ${wt}`);
    }
    this.materials.set(name, { palette });
    return this;
  }

  // Optional builder-level legend (char -> material name or hex) merged with
  // any per-cube legend passed to cube(). Later definitions win.
  legend(map) {
    for (const [k, v] of Object.entries(map)) this.legends.set(k, v);
    return this;
  }

  _tokenColor(token, sx, sy) {
    if (isHexToken(token)) return hex(token);
    const mat = this.materials.get(token);
    if (!mat) throw new Error(`unknown material or color token: ${token}`);
    let total = 0;
    for (const [, wt] of mat.palette) total += wt;
    let r = texHash(sx, sy) * total;
    for (const [c, wt] of mat.palette) {
      r -= wt;
      if (r < 0) return hex(c);
    }
    return hex(mat.palette[mat.palette.length - 1][0]);
  }

  // from/to are integer pixel coords. Each face gets a UV island sized exactly
  // to that face (inflate does NOT change UV size). faces maps a direction to:
  //   - a material name or hex string (fill whole face)
  //   - an ASCII grid: array of h equal-length strings of length w; row0=top,
  //     col0=left from outside; chars resolved through the cube `legend`
  //     (char -> material name or hex); '.' = transparent
  //   - a function (x, y, w, h) -> material name | hex | null (transparent)
  // opts.mat is the default material for directions not present in faces.
  cube(name, from, to, opts = {}) {
    const [x1, y1, z1] = from;
    const [x2, y2, z2] = to;
    const w = x2 - x1;
    const h = y2 - y1;
    const d = z2 - z1;
    const sizes = {
      north: [w, h],
      south: [w, h],
      east: [d, h],
      west: [d, h],
      up: [w, d],
      down: [w, d],
    };
    const legend = { ...Object.fromEntries(this.legends), ...(opts.legend ?? {}) };
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

    for (const dir of DIRS) {
      const [fw, fh] = sizes[dir];
      let spec = opts.faces?.[dir];
      if (spec === undefined) spec = opts.mat;
      if (spec === undefined) throw new Error(`cube ${name}: face ${dir} has no material or spec`);
      const record = { element, dir, w: fw, h: fh, spec, legend };
      this._specs.push(record);
      element.faces[dir] = { uv: [0, 0, fw, fh], texture: 0 };
    }
    this.elements.push(element);
    return element;
  }

  group(name, origin, children, opts = {}) {
    return {
      name,
      uuid: uuid(),
      origin: [...origin],
      rotation: opts.rotation ? [...opts.rotation] : [0, 0, 0],
      visibility: opts.visibility ?? true,
      children: children.map((c) => (c && c.type === "cube" ? c.uuid : c)),
    };
  }

  animation(name, loop, length, animators) {
    this.animations.push({ name, loop, length, animators });
  }

  _paintFace(rect, spec) {
    const c = this.canvas;
    const { w, h, legend } = spec;
    const put = (col, row, token) => {
      if (token === null || token === undefined || token === ".") return;
      c.pixel(rect.x + col, rect.y + row, this._tokenColor(token, rect.x + col, rect.y + row));
    };
    const s = spec.spec;
    if (typeof s === "function") {
      for (let row = 0; row < h; row++) {
        for (let col = 0; col < w; col++) put(col, row, s(col, row, w, h));
      }
    } else if (Array.isArray(s)) {
      if (s.length !== h) throw new Error(`face ${spec.element.name}/${spec.dir}: grid has ${s.length} rows, expected ${h}`);
      for (let row = 0; row < h; row++) {
        if (s[row].length !== w) throw new Error(`face ${spec.element.name}/${spec.dir}: row ${row} length ${s[row].length}, expected ${w}`);
        for (let col = 0; col < w; col++) {
          const ch = s[row][col];
          if (ch === ".") continue;
          const token = legend[ch];
          if (token === undefined) throw new Error(`face ${spec.element.name}/${spec.dir}: legend missing '${ch}'`);
          put(col, row, token);
        }
      }
    } else if (typeof s === "string") {
      for (let row = 0; row < h; row++) for (let col = 0; col < w; col++) put(col, row, s);
    } else {
      throw new Error(`face ${spec.element.name}/${spec.dir}: unsupported face spec`);
    }
  }

  finalize() {
    if (this._finalized) return this;
    const order = [...this._specs].sort((a, b) => b.h - a.h || b.w - a.w);
    let width = this.baseWidth;
    let height = this.baseHeight;
    let rects = shelfPack(order, width, height);
    if (!rects) {
      width = Math.max(this.baseWidth, 128);
      height = Math.max(this.baseHeight, 128);
      rects = shelfPack(order, width, height);
    }
    if (!rects) throw new Error(`UV atlas overflow packing into ${width}x${height}`);
    this.width = width;
    this.height = height;
    this.canvas = new Canvas(width, height);
    for (const spec of order) {
      const rect = rects.get(spec);
      spec.rect = rect;
      this._paintFace(rect, spec);
      spec.element.faces[spec.dir].uv = [rect.x, rect.y, rect.x + rect.w, rect.y + rect.h];
    }
    this._finalized = true;
    return this;
  }

  toJSON(modelName) {
    this.finalize();
    const source = "data:image/png;base64," + encodePNG(this.canvas).toString("base64");
    return {
      meta: { format_version: "4.10", model_format: "free" },
      name: modelName,
      box_uv: false,
      resolution: { width: this.width, height: this.height },
      elements: this.elements,
      outliner: this.outliner,
      textures: [
        { path: "", name: modelName, folder: "", namespace: "", id: "0", particle: false, render_mode: "default", source },
      ],
      animations: this.animations,
    };
  }

  write(bbmodelPath, pngPath, modelName) {
    this.finalize();
    fs.mkdirSync(path.dirname(bbmodelPath), { recursive: true });
    fs.writeFileSync(bbmodelPath, JSON.stringify(this.toJSON(modelName)));
    if (pngPath) {
      fs.mkdirSync(path.dirname(pngPath), { recursive: true });
      fs.writeFileSync(pngPath, encodePNG(this.canvas));
    }
    return this;
  }
}
