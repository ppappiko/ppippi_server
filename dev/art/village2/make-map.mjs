// layout.json -> Minecraft-style top-down map texture (no plot squares) for the client warp screen.
//   node make-map.mjs
//   -> dev/pipi-travel/assets/pipitravel/textures/gui/village2_map.png  (585x472, 1 px = 1 block, same layout / origin as before)
//   -> dev/art/village2/preview.png  (x2 preview with house icons on the plots)
// Pixel (c, r) = world block (c + originX, r + originZ).
//
// Look: a pixel-art map like a Minecraft map item / Xaero top-down render. The map is painted on an "art" grid of
// 2x2 blocks; every 8x8 art cells (16 blocks = one chunk, chunk-aligned) form one block tile that carries a vanilla-like
// 16-colour texture (grass top noise, dirt path, sand, water ripples, oak planks) with a slight bevel on the tile edges.
// Raised hills use a lighter grass with a lit north/west rim and a dark south/east cliff + cast shadow.
import fs from "node:fs";
import zlib from "node:zlib";
import path from "node:path"; import { fileURLToPath } from "node:url";
const here = path.dirname(fileURLToPath(import.meta.url));
const L = JSON.parse(fs.readFileSync(path.join(here, "layout.json"), "utf8"));
const W = L.width, H = L.height;
const type = new Uint8Array(W * H); // 0 water 1 land 2 road 3 raised (hill)
L.rows.forEach((row, z) => { let x = 0; const re = /([wlrh])(\d+)/g; let m; while ((m = re.exec(row))) { const t = "wlrh".indexOf(m[1]); for (let k = 0; k < +m[2] && x < W; k++) type[z * W + x++] = t; } });
const T = (x, z) => (x < 0 || z < 0 || x >= W || z >= H) ? 0 : type[z * W + x];

// ---- art grid: one art cell = 2x2 blocks, offset by one block so 8 cells = one world chunk ----
const A = 2, OFF = ((L.originX % 16) + 16) % 2;               // originX -111 -> texture column -1 starts a cell
const OFFZ = ((L.originZ % 16) + 16) % 2;
const AW = Math.ceil((W + OFF) / A), AH = Math.ceil((H + OFFZ) / A);
const cell = new Uint8Array(AW * AH);
for (let az = 0; az < AH; az++) for (let ax = 0; ax < AW; ax++) {
  const n = [0, 0, 0, 0];
  for (let dz = 0; dz < A; dz++) for (let dx = 0; dx < A; dx++) n[T(ax * A + dx - OFF, az * A + dz - OFFZ)]++;
  let t = 1;
  if (n[2] >= 2) t = 2;                              // roads win ties so thin roads survive
  else { let best = -1; for (const k of [1, 3, 0]) if (n[k] > best) { best = n[k]; t = k; } }
  cell[az * AW + ax] = t;
}
const C = (ax, az) => (ax < 0 || az < 0 || ax >= AW || az >= AH) ? 0 : cell[az * AW + ax];
function distTo(pred, cap) { // 4-neighbour BFS distance on the art grid, capped
  const d = new Int16Array(AW * AH).fill(cap); let q = [];
  for (let z = 0; z < AH; z++) for (let x = 0; x < AW; x++) if (pred(x, z)) { d[z * AW + x] = 0; q.push(z * AW + x); }
  for (let s = 1; s < cap && q.length; s++) { const nq = []; for (const i of q) { const x = i % AW, z = (i / AW) | 0; for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) { const nx = x + dx, nz = z + dz; if (nx < 0 || nz < 0 || nx >= AW || nz >= AH) continue; const j = nz * AW + nx; if (d[j] > s) { d[j] = s; nq.push(j); } } } q = nq; }
  return d;
}
const dLand = distTo((x, z) => C(x, z) !== 0, 12);   // water: distance to shore
const dWater = distTo((x, z) => C(x, z) === 0, 4);   // land: distance to water

// ---- palettes (vanilla-like ramps, dark -> light) ----
const hex = v => [(v >> 16) & 255, (v >> 8) & 255, v & 255];
const P = {
  grass: [0x4a7f2c, 0x568f33, 0x5f9b3a, 0x6aa842, 0x76b54b, 0x84c055].map(hex),
  hill: [0x6a9e3e, 0x78ad48, 0x84ba52, 0x90c55c, 0x9ccf67, 0xa9d873].map(hex),
  path: [0x7b5e34, 0x8c6c3d, 0x9b7a47, 0xa98752, 0xb6955e, 0xc2a36b].map(hex),
  sand: [0xbfb07a, 0xcdbf88, 0xd8cb95, 0xe1d6a2, 0xe9dfb0].map(hex),
  water: [0x23489e, 0x2b55b4, 0x3463c6, 0x3f72d4, 0x5287de, 0x6f9fe6].map(hex),
  plank: [0x6b4f2a, 0x86653a, 0x9c7a48, 0xb08b54].map(hex),
};
const hash = (x, z, s = 0) => { let h = (x * 374761393 + z * 668265263 + s * 1442695041) | 0; h = (h ^ (h >>> 13)) * 1274126177 | 0; return ((h ^ (h >>> 16)) >>> 0) / 4294967296; };
const TILE = 8;
/** 8x8 shade-index textures (index into a ramp, -1..+1 around the ramp centre), randomly rotated per tile like vanilla grass. */
function noiseTile(seed, weights) { // weights[k] = chance of ramp index k
  const t = []; for (let i = 0; i < 64; i++) { let r = hash(i % 8, i >> 3, seed), k = 0; while (k < weights.length - 1 && r >= weights[k]) { r -= weights[k]; k++; } t.push(k); }
  return t;
}
const GRASS_T = [0, 1, 2, 3].map(s => noiseTile(11 + s, [.06, .16, .3, .28, .14, .06]));
const PATH_T = [0, 1].map(s => noiseTile(31 + s, [.05, .15, .3, .3, .14, .06]));
const SAND_T = [0, 1].map(s => noiseTile(41 + s, [.08, .22, .4, .22, .08]));
function tileIdx(tex, ax, az, salt) {
  const tx = Math.floor(ax / TILE), tz = Math.floor(az / TILE), v = tex[Math.floor(hash(tx, tz, salt) * tex.length)];
  let u = ((ax % TILE) + TILE) % TILE, w = ((az % TILE) + TILE) % TILE;
  const rot = Math.floor(hash(tx, tz, salt + 7) * 4);
  for (let r = 0; r < rot; r++) [u, w] = [TILE - 1 - w, u];
  return v[w * TILE + u];
}
const shade = (c, f) => c.map(v => Math.max(0, Math.min(255, Math.round(v * f))));
/** tile bevel: lit top/left row, dark bottom/right row of every 8x8 block tile */
function bevel(c, ax, az, k = 1) {
  const u = ((ax % TILE) + TILE) % TILE, w = ((az % TILE) + TILE) % TILE;
  if (u === TILE - 1 || w === TILE - 1) return shade(c, 1 - .09 * k);
  if (u === 0 || w === 0) return shade(c, 1 + .06 * k);
  return c;
}

const art = new Uint8Array(AW * AH * 3);
for (let az = 0; az < AH; az++) for (let ax = 0; ax < AW; ax++) {
  const t = C(ax, az), i = az * AW + ax; let c;
  if (t === 0) {
    // water: ripple stripes (wavy horizontal lines per tile), darker with depth, light foam right at the shore
    const d = dLand[i], u = ((ax % TILE) + TILE) % TILE, w = ((az % TILE) + TILE) % TILE;
    const tx = Math.floor(ax / TILE), tz = Math.floor(az / TILE);
    const base = d >= 9 ? 1 : d >= 5 ? 2 : d >= 3 ? 3 : 4;
    let k = base;
    const wave = (w + Math.floor(hash(tx, tz, 3) * 8) + (u >> 2)) % 4;
    if (wave === 0 && hash(ax, az, 5) < .8) k = Math.min(5, base + 1);
    else if (wave === 2 && hash(ax, az, 6) < .5) k = Math.max(0, base - 1);
    c = P.water[k];
    if (d <= 1) c = hash(ax, az, 8) < .5 ? hex(0x8fbdf0) : hex(0xa9cff5);
  } else if (t === 2) {
    c = P.path[1 + tileIdx(PATH_T, ax, az, 2)] || P.path[5];
    if (hash(ax, az, 9) < .05) c = P.path[0];                            // coarse-dirt pebbles
    if (dWater[i] <= 1) c = P.sand[2 + (hash(ax, az, 4) < .4 ? 1 : 0)];
    c = bevel(c, ax, az, .8);
  } else {
    const hill = t === 3;
    const ramp = hill ? P.hill : P.grass;
    c = ramp[tileIdx(GRASS_T, ax, az, hill ? 21 : 1)];
    if (dWater[i] <= 2) { c = P.sand[1 + tileIdx(SAND_T, ax, az, 4)] || P.sand[4]; if (dWater[i] === 2 && hash(ax, az, 12) < .45) c = ramp[2]; }
    else if (hill) {
      // height: lit rim on the north / west edge, dark cliff on the south / east edge
      const s = C(ax, az + 1) !== 3 || C(ax + 1, az) !== 3, s2 = C(ax, az + 2) !== 3;
      const n = C(ax, az - 1) !== 3 || C(ax - 1, az) !== 3;
      if (s) c = hex(0x3c6324); else if (s2 && C(ax, az + 2) !== 0) c = hex(0x55862f); else if (n) c = hex(0xbfe48a);
    } else if (C(ax, az - 1) === 3 || C(ax - 1, az) === 3 || C(ax, az - 2) === 3) c = shade(c, .78); // cast shadow below a hill
    c = bevel(c, ax, az, 1);
  }
  art[i * 3] = c[0]; art[i * 3 + 1] = c[1]; art[i * 3 + 2] = c[2];
}
function apx(ax, az, c) { if (ax < 0 || az < 0 || ax >= AW || az >= AH) return; const i = (az * AW + ax) * 3; art[i] = c[0]; art[i + 1] = c[1]; art[i + 2] = c[2]; }
const toArt = (wx, wz) => [Math.floor((wx - L.originX + OFF) / A), Math.floor((wz - L.originZ + OFFZ) / A)];
// stairs up the hills: oak planks with dark seams
const dir = { E: [1, 0], W: [-1, 0], S: [0, 1], N: [0, -1] };
for (const s of L.stairs) {
  const v = dir[s.face], perp = [-v[1], v[0]], seen = new Set();
  for (let k = 0; k < s.len; k++) for (let q = -2; q <= 2; q++) {
    const [ax, az] = toArt(s.x - v[0] * k + perp[0] * q, s.z - v[1] * k + perp[1] * q), key = ax + "," + az;
    if (seen.has(key)) continue; seen.add(key);
    const along = v[0] ? ax : az;
    apx(ax, az, along % 2 ? P.plank[3] : P.plank[1]);
  }
}
// gateway (world 0,0): red-concrete diamond with a quartz rim and a dark outline
{
  const [gx, gz] = toArt(L.gateway.x, L.gateway.z);
  for (let r = -6; r <= 6; r++) for (let q = -6; q <= 6; q++) {
    const m = Math.abs(r) + Math.abs(q); if (m > 6) continue;
    apx(gx + r, gz + q, m === 6 ? hex(0x3a2a20) : m === 5 ? hex(0xebe5dc) : m === 4 ? hex(0xd2ccc2) : (r + q) % 2 ? hex(0xa8282e) : hex(0xc23238));
  }
}

// ---- art grid -> 1 px per block texture ----
const rgb = new Uint8Array(W * H * 3);
for (let z = 0; z < H; z++) for (let x = 0; x < W; x++) {
  const ax = Math.floor((x + OFF) / A), az = Math.floor((z + OFFZ) / A), s = (az * AW + ax) * 3, d = (z * W + x) * 3;
  rgb[d] = art[s]; rgb[d + 1] = art[s + 1]; rgb[d + 2] = art[s + 2];
}

// ---- png ----
const crcT = (() => { const t = []; for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
const crc = b => { let c = 0xffffffff; for (const x of b) c = crcT[(c ^ x) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (t, d) => { const tb = Buffer.from(t), l = Buffer.alloc(4), c = Buffer.alloc(4); l.writeUInt32BE(d.length); c.writeUInt32BE(crc(Buffer.concat([tb, d]))); return Buffer.concat([l, tb, d, c]); };
function png(w, h, data, ch) {
  const raw = Buffer.alloc((w * ch + 1) * h);
  for (let z = 0; z < h; z++) { raw[z * (w * ch + 1)] = 0; Buffer.from(data.buffer, data.byteOffset + z * w * ch, w * ch).copy(raw, z * (w * ch + 1) + 1); }
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = ch === 4 ? 6 : 2;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk("IHDR", ihdr), chunk("IDAT", zlib.deflateSync(raw)), chunk("IEND", Buffer.alloc(0))]);
}
const out = path.resolve(here, "../../pipi-travel/assets/pipitravel/textures/gui/village2_map.png");
fs.writeFileSync(out, png(W, H, rgb, 3));
console.log("wrote", out, W + "x" + H, "origin", L.originX, L.originZ);

// ---- preview (x2, house icons at the plot centres like WarpScreen draws them) ----
const Z = 2, PW = W * Z, PH = H * Z, prev = new Uint8Array(PW * PH * 3);
for (let y = 0; y < PH; y++) for (let x = 0; x < PW; x++) { const s = (((y / Z) | 0) * W + ((x / Z) | 0)) * 3, d = (y * PW + x) * 3; prev[d] = rgb[s]; prev[d + 1] = rgb[s + 1]; prev[d + 2] = rgb[s + 2]; }
const { readPng } = await import("../class-icons/pngio.mjs");
const iconDir = path.dirname(out), icons = ["house_owned.png", "house_empty.png"].map(f => fs.existsSync(path.join(iconDir, f)) ? readPng(fs.readFileSync(path.join(iconDir, f))) : null);
// the screen shows the map at ~0.65 GUI px per block and the 16 px icon at 16 GUI px -> icon covers ~25 blocks
L.plots.forEach((p, n) => {
  const ic = icons[n % 3 === 0 ? 0 : 1]; if (!ic) return;
  const cx = (p.x + L.plotSize / 2 - L.originX) * Z, cz = (p.z + L.plotSize / 2 - L.originZ) * Z, k = 25 * Z / 16;
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const s = (y * 16 + x) * 4; if (!ic.px[s + 3]) continue;
    for (let yy = Math.floor(y * k); yy < Math.floor((y + 1) * k); yy++) for (let xx = Math.floor(x * k); xx < Math.floor((x + 1) * k); xx++) {
      const X = Math.round(cx - 8 * k + xx), Y = Math.round(cz - 9 * k + yy); if (X < 0 || Y < 0 || X >= PW || Y >= PH) continue;
      const d = (Y * PW + X) * 3; prev[d] = ic.px[s]; prev[d + 1] = ic.px[s + 1]; prev[d + 2] = ic.px[s + 2];
    }
  }
});
fs.writeFileSync(path.join(here, "preview.png"), png(PW, PH, prev, 3));
console.log("wrote", path.join(here, "preview.png"));
