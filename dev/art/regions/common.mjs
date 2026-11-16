// Shared driver bits: road mask, lot placement along roads, output (plugin binary + previews).
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { exportRegion, alongPath, hash, SEA } from "./lib.mjs";
import { topdown, iso } from "./render.mjs";

export const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../..");

export class Roads {
  constructor(V) { this.V = V; this.m = new Uint8Array(V.W * V.D); this.lines = []; }
  /** stamps a road; mat(x, z, t, d) -> surface block (or null) */
  add(pts, r, mat, opt = {}) {
    const V = this.V; this.lines.push({ pts, r });
    alongPath(pts, r, (x, z, t, d) => {
      if (x < 0 || z < 0 || x >= V.W || z >= V.D) return;
      const h = V.h(x, z);
      if (h <= SEA && !opt.bridge) return;
      if (h <= SEA && opt.bridge) { V.set(x, SEA + 1, z, opt.bridge); for (let y = SEA + 2; y <= SEA + 5; y++) V.set(x, y, z, 0); this.m[z * V.W + x] = 1; return; }
      const s = mat(x, z, t, d); if (s) V.set(x, h, z, s);
      for (let y = h + 1; y <= h + 4; y++) if (!V.occupied(x, z)) V.set(x, y, z, 0);
      this.m[z * V.W + x] = 1;
    });
  }
  on(x, z, pad = 0) {
    const V = this.V;
    for (let dz = -pad; dz <= pad; dz++) for (let dx = -pad; dx <= pad; dx++) { const X = x + dx, Z = z + dz; if (X >= 0 && Z >= 0 && X < V.W && Z < V.D && this.m[Z * V.W + X]) return true; }
    return false;
  }
}

/**
 * Lots along every road line: every `step` blocks, on both sides, sizeFn(r) -> [frontage, depth], door facing the road.
 * accept(x0, z0, x1, z1) decides; build(x0, z0, x1, z1, face, rnd) builds.
 */
export function lots(V, roads, lines, { step = 12, gap = 2, sizeFn, accept, build, seed = 1, margin = 2 }) {
  let n = 0;
  for (const { pts, r } of lines) {
    let acc = 0;
    for (let i = 1; i < pts.length; i++) {
      const [ax, az] = pts[i - 1], [bx, bz] = pts[i], len = Math.hypot(bx - ax, bz - az); if (!len) continue;
      const tx = (bx - ax) / len, tz = (bz - az) / len;
      for (let s = 0; s < len; s += 1) {
        acc += 1; if (acc % step !== 0) continue;
        const px = ax + tx * s, pz = az + tz * s;
        for (const side of [1, -1]) {
          const rnd = hash(Math.round(px), Math.round(pz), seed + side);
          const [front, deep] = sizeFn(rnd);
          const nx = -tz * side, nz = tx * side;
          // face: the road direction from the house (opposite of the normal)
          const horiz = Math.abs(nx) > Math.abs(nz), w = horiz ? deep : front, d = horiz ? front : deep;
          const face = horiz ? (nx > 0 ? "west" : "east") : (nz > 0 ? "north" : "south");
          const off = r + gap + (horiz ? w : d) / 2 + 0.5;
          const cx = Math.round(px + nx * off), cz = Math.round(pz + nz * off);
          const x0 = cx - Math.floor(w / 2), z0 = cz - Math.floor(d / 2), x1 = x0 + w - 1, z1 = z0 + d - 1;
          if (!free(V, roads, x0 - margin, z0 - margin, x1 + margin, z1 + margin)) continue;
          if (!accept(x0, z0, x1, z1)) continue;
          build(x0, z0, x1, z1, face, rnd); n++;
        }
      }
    }
  }
  return n;
}
export function free(V, roads, x0, z0, x1, z1) {
  for (let z = z0; z <= z1; z++) for (let x = x0; x <= x1; x++) {
    if (x < 0 || z < 0 || x >= V.W || z >= V.D) return false;
    if (V.occupied(x, z) || roads.on(x, z) || V.h(x, z) <= SEA) return false;
  }
  return true;
}

export function output(V, id, spawn, crops = [], entrance = null) {
  const here = path.join(ROOT, "dev/art/regions", id);
  fs.mkdirSync(here, { recursive: true });
  exportRegion(V, path.join(ROOT, "dev/pipi-rpg/res/regions", id + ".bin"), spawn, entrance);
  fs.writeFileSync(path.join(here, "meta.json"), JSON.stringify({ id, spawn, entrance }, null, 1));
  topdown(V, path.join(here, "preview.png"), 2);
  iso(V, path.join(here, "iso.png"), { a: 2 });
  for (const c of crops) iso(V, path.join(here, "iso_" + c.name + ".png"), { a: c.a ?? 5, x0: c.x - c.r, z0: c.z - c.r, x1: c.x + c.r, z1: c.z1 ?? c.z + c.r, rot: c.rot ?? 0 });
  console.log("previews in", here);
}
