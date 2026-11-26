// New / changed skill icons (32x32) for the 2026-11-20 skill rework: node dev/art/make-skill-icons.mjs
// The class frame is copied from an existing icon of the same class; the inside is repainted with a symbol.
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { readPng, writePng } from "./class-icons/pngio.mjs";
const here = path.dirname(fileURLToPath(import.meta.url));
const DIR = path.resolve(here, "../pipi-travel/assets/pipitravel/textures/gui/skills");
const N = 32;
const hx = s => [parseInt(s.slice(1, 3), 16), parseInt(s.slice(3, 5), 16), parseInt(s.slice(5, 7), 16)];

function icon(frameFrom, bg, draw, out) {
  const { w, px: src } = readPng(fs.readFileSync(path.join(DIR, frameFrom + ".png")));
  if (w !== N) throw new Error("expected 32px icons");
  const px = Buffer.from(src);
  const set = (x, y, c, a = 1) => {
    x = Math.round(x); y = Math.round(y);
    if (x < 3 || y < 3 || x > N - 4 || y > N - 4) return;
    const o = (y * N + x) * 4, [r, g, b] = hx(c);
    px[o] = px[o] * (1 - a) + r * a; px[o + 1] = px[o + 1] * (1 - a) + g * a; px[o + 2] = px[o + 2] * (1 - a) + b * a; px[o + 3] = 255;
  };
  // background: radial gradient from the class colour
  const [b0, b1] = bg.map(hx);
  for (let y = 3; y < N - 3; y++) for (let x = 3; x < N - 3; x++) {
    const t = Math.min(1, Math.hypot(x - 15.5, y - 15.5) / 15);
    const o = (y * N + x) * 4; for (let i = 0; i < 3; i++) px[o + i] = b0[i] * (1 - t) + b1[i] * t; px[o + 3] = 255;
  }
  const line = (x0, y0, x1, y1, c, wdt = 1) => { const n = Math.ceil(Math.hypot(x1 - x0, y1 - y0) * 2); for (let k = 0; k <= n; k++) { const x = x0 + (x1 - x0) * k / n, y = y0 + (y1 - y0) * k / n; for (let dx = 0; dx < wdt; dx++) for (let dy = 0; dy < wdt; dy++) set(x + dx - (wdt >> 1), y + dy - (wdt >> 1), c); } };
  const disc = (cx, cy, r, c) => { for (let y = cy - r; y <= cy + r; y++) for (let x = cx - r; x <= cx + r; x++) if ((x - cx) ** 2 + (y - cy) ** 2 <= r * r) set(x, y, c); };
  draw({ set, line, disc });
  // clean frame: the right / bottom border copies the left / top one (the source icon's art can poke into it)
  for (let k = 0; k < N; k++) for (let d = 0; d < 3; d++) for (const [x, y, mx, my] of [[N - 1 - d, k, d, k], [k, N - 1 - d, k, d]]) { const o = (y * N + x) * 4, m = (my * N + mx) * 4; for (let i = 0; i < 4; i++) px[o + i] = px[m + i]; }
  fs.writeFileSync(path.join(DIR, out + ".png"), writePng(px, N, N));
  console.log(out);
}
const W = ["#5a1a1a", "#2a0a0a"], A = ["#1f4a2a", "#0b1f10"], M = ["#3a2160", "#160a2a"];
icon("w_iai", W, ({ line }) => { line(6, 22, 24, 6, "#f4f4f4", 2); line(9, 27, 27, 11, "#ff6a5a", 2); line(4, 24, 8, 20, "#ffd65a", 2); line(7, 29, 11, 25, "#ffd65a", 2); for (const [x, y] of [[20, 4], [23, 9], [26, 14]]) line(x, y, x + 3, y - 1, "#ffe0d0"); }, "w_iai"); // 더블어택: two parallel slashes
icon("w_rush", W, ({ line }) => { line(7, 25, 25, 7, "#f4f4f4", 2); line(22, 6, 26, 10, "#ffd65a", 2); for (const k of [0, 4, 8]) line(5 + k, 14 + k, 10 + k, 9 + k, "#ffb0a0"); }, "w_thrust");
icon("w_ult", W, ({ line, disc }) => { for (const [x, y] of [[9, 7], [17, 11], [23, 5]]) { line(x - 5, y - 4, x + 2, y + 9, "#ff8a6a", 2); disc(x + 3, y + 11, 2, "#ffe0d0"); } line(5, 26, 27, 26, "#ff6a4a"); }, "w_meteor");
icon("m_seek", M, ({ line, disc }) => { disc(16, 8, 5, "#c9a0ff"); for (const x of [9, 13, 17, 21, 24]) line(x, 13 + (x % 3), x - 1, 24 + (x % 4), "#e2c8ff"); }, "m_mrain");
icon("m_hammer", M, ({ line }) => { for (const [x, h] of [[9, 12], [16, 18], [23, 13]]) { line(x, 27, x, 27 - h, "#d6b8ff", 3); line(x, 27 - h - 3, x, 27 - h, "#ffffff"); } line(4, 27, 28, 27, "#7a52c8"); }, "m_spear");
icon("a_spirit", A, ({ line }) => { for (const k of [0, 5, 10]) line(8 + k, 7, 13 + k, 25, k === 5 ? "#ffffff" : "#d8f0ff", 2); line(6, 16, 26, 16, "#9fe0ff"); }, "a_tiger");
icon("a_focus", A, ({ line, disc }) => { disc(16, 16, 9, "#2f6a4a"); for (let a = 0; a < 6; a++) { const t = a * Math.PI / 3; line(16, 16, 16 + Math.cos(t) * 9, 16 + Math.sin(t) * 9, "#173a28"); } disc(16, 16, 3, "#7fd0a0"); line(16, 5, 16, 9, "#7fd0a0", 2); }, "a_turtle");
icon("m_cannon", M, ({ disc }) => { disc(15, 16, 10, "#fff2b8"); disc(19, 13, 9, "#3a2160"); disc(10, 21, 1, "#ffffff"); disc(7, 13, 1, "#ffffff"); }, "m_moon");
// 2026-11-26 longsword warrior skills
icon("w_iai", W, ({ line }) => { [["#ffe0d8", 0], ["#ff6a5a", 6], ["#ffb0a0", 12]].forEach(([c, dx]) => { let px0 = null; for (let a = -1.05; a <= 1.06; a += .15) { const x = 3 + dx + Math.cos(a) * 9, y = 16 + Math.sin(a) * 11; if (px0) line(px0[0], px0[1], x, y, c, 1); px0 = [x, y]; } }); }, "w_split");  // 삼연 비검: three thin crescents
icon("w_iai", W, ({ line, disc }) => { line(5, 8, 26, 24, "#f4f4f4", 1); line(5, 24, 26, 8, "#ff6a5a", 1); line(16, 4, 16, 28, "#ffb0a0", 1); line(4, 16, 28, 16, "#ffe0d8", 1); for (const [x, y] of [[9, 11], [22, 20], [11, 21], [21, 10]]) disc(x, y, 1, "#ffd65a"); }, "w_heavy"); // 검무: crossing flurry
icon("w_iai", W, ({ line, disc }) => { for (let k = 0; k < 3; k++) { const a = k * 2.094 + .4, cx = 16 + Math.cos(a) * 8, cy = 16 + Math.sin(a) * 8, tx = -Math.sin(a), ty = Math.cos(a); line(cx - tx * 5, cy - ty * 5, cx + tx * 5, cy + ty * 5, "#f4f4f4", 1); line(cx - tx * 5, cy - ty * 5, cx - tx * 3, cy - ty * 3, "#ff6a5a", 2); } for (let t = 0; t < 6.28; t += .3) disc(16 + Math.cos(t) * 11, 16 + Math.sin(t) * 11, 0, "#ff9a8a"); disc(16, 16, 2, "#ffd65a"); }, "w_impale"); // 검막: three swords whirling around
// 청룡의 진노 (archer): the blue dragon art of m_dragon on the archer frame and green background (the purple mage background is
// dropped: dark purple pixels become the archer gradient)
{
  const dragon = readPng(fs.readFileSync(path.join(DIR, "m_dragon.png"))).px, frame = readPng(fs.readFileSync(path.join(DIR, "a_ult.png"))).px;
  const px = Buffer.from(frame), [b0, b1] = A.map(hx);
  for (let y = 3; y < N - 3; y++) for (let x = 3; x < N - 3; x++) {
    const o = (y * N + x) * 4, r = dragon[o], g = dragon[o + 1], b = dragon[o + 2];
    const purpleBg = r > g && b > g && Math.max(r, g, b) < 150;
    if (purpleBg) { const t = Math.min(1, Math.hypot(x - 15.5, y - 15.5) / 15), k = (x + y) % 2 && y > 20 ? .85 : 1; for (let i = 0; i < 3; i++) px[o + i] = (b0[i] * (1 - t) + b1[i] * t) * k; }
    else { px[o] = r; px[o + 1] = g; px[o + 2] = b; }
    px[o + 3] = 255;
  }
  // the inner frame line on the right / bottom (a_ult art pokes into it): mirror the clean left / top line
  for (let k = 0; k < N; k++) for (let d = 0; d < 3; d++) for (const [x, y, mx, my] of [[N - 1 - d, k, d, k], [k, N - 1 - d, k, d]]) { const o = (y * N + x) * 4, m = (my * N + mx) * 4; for (let i = 0; i < 4; i++) px[o + i] = frame[m + i]; }
  fs.writeFileSync(path.join(DIR, "a_dragon.png"), writePng(px, N, N));
  console.log("a_dragon (dragon art on the archer frame)");
}
