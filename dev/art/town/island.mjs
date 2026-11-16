// Island outline extraction from sketch.png -> block mask (shared by gen.mjs)
import { readPng, edt } from "../village2/lib.mjs";
import path from "node:path"; import { fileURLToPath } from "node:url";
const here = path.dirname(fileURLToPath(import.meta.url));
export const S = 0.25;                // blocks per sketch pixel (island ~200x160)
export const CX = 570, CY = 325;      // sketch pixel of the central warp stone = world (0,0)
export const SK = { // marked spots in the sketch (pixels)
  alchemist: [345, 310], smith: [350, 475], armory: [520, 575], shop: [950, 355],
};
export function loadIsland() {
  const { w, h, gray } = readPng(path.join(here, "sketch.png"));
  const line = new Uint8Array(w * h); for (let i = 0; i < w * h; i++) line[i] = gray[i] < 150 ? 1 : 0;
  const dl = edt(line, w, h), R = 3;
  const thick = new Uint8Array(w * h); for (let i = 0; i < w * h; i++) thick[i] = dl[i] <= R * R ? 1 : 0;
  const outside = new Uint8Array(w * h); const st = [0]; outside[0] = 1;
  while (st.length) { const i = st.pop(), x = i % w, y = (i / w) | 0;
    for (const [dx, dy] of [[1,0],[-1,0],[0,1],[0,-1]]) { const nx = x+dx, ny = y+dy; if (nx<0||ny<0||nx>=w||ny>=h) continue; const j = ny*w+nx; if (!outside[j] && !thick[j]) { outside[j]=1; st.push(j);} } }
  // block grid covering the sketch
  const x0 = Math.floor((0 - CX) * S) - 6, x1 = Math.ceil((w - CX) * S) + 6, z0 = Math.floor((0 - CY) * S) - 6, z1 = Math.ceil((h - CY) * S) + 6;
  const W = x1 - x0 + 1, H = z1 - z0 + 1;
  let f = new Float32Array(W * H);
  for (let z = 0; z < H; z++) for (let x = 0; x < W; x++) {
    const cx = CX + (x + x0) / S, cy = CY + (z + z0) / S; let sum = 0, n = 0;
    for (let a = -1; a <= 1; a++) for (let b = -1; b <= 1; b++) { const px = Math.round(cx + a), py = Math.round(cy + b); n++; if (px >= 0 && py >= 0 && px < w && py < h && !outside[py * w + px]) sum++; }
    f[z * W + x] = sum / n;
  }
  // light blur for a natural coast
  for (let it = 0; it < 2; it++) { const g = new Float32Array(W * H); for (let z = 1; z < H - 1; z++) for (let x = 1; x < W - 1; x++) { let s = 0; for (let a = -1; a <= 1; a++) for (let b = -1; b <= 1; b++) s += f[(z + b) * W + x + a]; g[z * W + x] = s / 9; } f = g; }
  const land = new Uint8Array(W * H); for (let i = 0; i < W * H; i++) land[i] = f[i] > 0.5 ? 1 : 0;
  return { W, H, x0, z0, land, sketchW: w, sketchH: h };
}
export const toWorld = ([px, py]) => [Math.round((px - CX) * S), Math.round((py - CY) * S)];
if (process.argv[1] && process.argv[1].endsWith("island.mjs")) {
  const I = loadIsland(); let n = 0; for (const v of I.land) n += v;
  console.log(I.W, I.H, I.x0, I.z0, "land", n, I.sketchW, I.sketchH);
  for (const [k, p] of Object.entries(SK)) console.log(k, toWorld(p));
}
