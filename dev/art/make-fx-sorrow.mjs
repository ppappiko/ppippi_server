// 비탄의 망령 군주 (통곡의 왕좌 raid boss) as ItemDisplay item models, split so the two floating hands can move on their own.
//   node dev/art/make-boss2-concepts.mjs && node dev/art/make-fx-sorrow.mjs
//   in : dev/art/preview/boss2/boss_ogre.bbmodel (128x128 texture, 16 px = 1 block, feet at y = 0, face = -Z)
//   out: pipi-facilities/assets/pipifacilities/models/item/fx_sorrow_{body,hand_l,hand_r}.json + textures/item/fx_sorrow.png
// Item models must fit -16..32, so geometry is stored at half size and display.fixed scales it back up (x2).
// body : origin (8, 8, 8) of the model = the boss's feet centre + 8 px up (model y 0 -> item y 4).
// hands: origin = the palm centre.
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
const here = path.dirname(fileURLToPath(import.meta.url));
const A = path.resolve(here, "../pipi-facilities/assets/pipifacilities");
const src = JSON.parse(fs.readFileSync(path.join(here, "preview/boss2/boss_ogre.bbmodel"), "utf8"));
const r3 = v => Math.round(v * 1000) / 1000;
const tw = src.resolution.width, th = src.resolution.height;

const png = src.textures[0].source.replace(/^data:image\/png;base64,/, "");
fs.writeFileSync(path.join(A, "textures/item/fx_sorrow.png"), Buffer.from(png, "base64"));

function write(id, els, centre, scale) {
  const map = p => p.map((v, i) => r3(8 + (v - centre[i]) * scale));
  const elements = els.map(e => {
    const faces = {};
    for (const [d, f] of Object.entries(e.faces)) {
      if (!f || !f.uv) continue;
      faces[d] = { uv: [f.uv[0] * 16 / tw, f.uv[1] * 16 / th, f.uv[2] * 16 / tw, f.uv[3] * 16 / th].map(r3), texture: "#0" };
    }
    const from = map(e.from), to = map(e.to);
    for (const v of [...from, ...to]) if (v < -16 || v > 32) throw new Error(`${id}: ${e.name} out of range ${v}`);
    const out = { from, to, shade: false, faces };
    const rot = (e.rotation || [0, 0, 0]).map(Number), axes = [0, 1, 2].filter(i => Math.abs(rot[i]) > 1e-6);
    if (axes.length > 1) throw new Error(`${id}: ${e.name} rotates about more than one axis`);
    if (axes.length) {
      const angle = rot[axes[0]];
      if (![-45, -22.5, 22.5, 45].includes(angle)) throw new Error(`${id}: ${e.name} angle ${angle} not allowed in item models`);
      out.rotation = { angle, axis: "xyz"[axes[0]], origin: map(e.origin) };
    }
    return out;
  });
  const k = 1 / scale;
  fs.writeFileSync(path.join(A, `models/item/fx_${id}.json`), JSON.stringify({
    textures: { "0": "pipifacilities:item/fx_sorrow", particle: "pipifacilities:item/fx_sorrow" }, elements,
    display: { fixed: { rotation: [0, 0, 0], translation: [0, 0, 0], scale: [k, k, k] }, gui: { rotation: [10, 200, 0], translation: [0, 0, 0], scale: [.35 * k, .35 * k, .35 * k] } },
  }));
  console.log(`fx_${id}`, elements.length, "elements");
}

const hand = s => src.elements.filter(e => e.name.startsWith(`hand_${s}_`));
const body = src.elements.filter(e => !e.name.startsWith("hand_"));
write("sorrow_body", body, [0, 8, 0], .5);
write("sorrow_hand_l", hand(-1), [-14.6, 20.75, -4.5], 1);
write("sorrow_hand_r", hand(1), [14.6, 20.75, -4.5], 1);
