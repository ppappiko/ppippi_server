import { loadIsland, SK, toWorld } from "./island.mjs";
const I = loadIsland();
const L = (x, z) => { const gx = x - I.x0, gz = z - I.z0; return gx < 0 || gz < 0 || gx >= I.W || gz >= I.H ? 0 : I.land[gz * I.W + gx]; };
let minx = 1e9, maxx = -1e9, minz = 1e9, maxz = -1e9;
for (let z = I.z0; z < I.z0 + I.H; z++) for (let x = I.x0; x < I.x0 + I.W; x++) if (L(x, z)) { minx = Math.min(minx, x); maxx = Math.max(maxx, x); minz = Math.min(minz, z); maxz = Math.max(maxz, z); }
console.log("bbox", minx, maxx, minz, maxz);
for (let z = minz; z <= maxz; z += 8) { let s = ""; for (let x = minx; x <= maxx; x += 4) s += L(x, z) ? "#" : "."; console.log(String(z).padStart(5), s); }
