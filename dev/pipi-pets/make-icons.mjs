// Generates 64x64 item icons from the pet .bbmodel walk animation and installs
// them into the ItemsAdder pack for all 18 skins.
//   node dev/pipi-pets/make-icons.mjs
//
// For each skin it renders dev/pipi-pets/out/icons/<skin>.png (preview.mjs
// --icon) posed mid-stride in `walk`, then copies that PNG to
// plugins/ItemsAdder/contents/pipi_pets/textures/item/<skin>.png and writes the
// matching models/item/<skin>.json item model.
import fs from "node:fs";
import path from "node:path";
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "../..");
const previewPath = path.join(here, "preview.mjs");
const modelsDir = path.join(root, "dev/pipi-facilities/assets/pipifacilities/pets");
const iconDir = path.join(here, "out/icons");
const texDir = path.join(root, "plugins/ItemsAdder/contents/pipi_pets/textures/item");
const itemModelDir = path.join(root, "plugins/ItemsAdder/contents/pipi_pets/models/item");

const SKINS = [
  "fox", "sparrow", "cat", "rabbit", "redpanda", "otter", "tortoise", "raccoon",
  "hornrabbit", "mole", "thundersheep", "moonbear", "ferret", "angelpig", "frog",
  "sunlizard", "imugi", "vampbat",
];

// Mid-stride fraction of the walk animation to pose at.
const MID_STRIDE = 0.25;

function walkTime(skin) {
  const bbmodelPath = path.join(modelsDir, `${skin}.bbmodel`);
  const json = JSON.parse(fs.readFileSync(bbmodelPath, "utf8"));
  const walk = (json.animations || []).find((a) => a.name === "walk");
  if (!walk) throw new Error(`no walk animation in ${skin}.bbmodel`);
  return (walk.length || 0) * MID_STRIDE;
}

function itemModel(skin) {
  return `{"parent": "minecraft:item/generated", "textures": {"layer0": "pipi_pets:item/${skin}"}}`;
}

fs.mkdirSync(iconDir, { recursive: true });
fs.mkdirSync(texDir, { recursive: true });
fs.mkdirSync(itemModelDir, { recursive: true });

for (const skin of SKINS) {
  const time = walkTime(skin);
  execFileSync(process.execPath, [previewPath, skin, "walk", String(time), "--icon"], { stdio: "inherit" });

  fs.copyFileSync(path.join(iconDir, `${skin}.png`), path.join(texDir, `${skin}.png`));
  fs.writeFileSync(path.join(itemModelDir, `${skin}.json`), itemModel(skin), "utf8");
}

console.log(`done: ${SKINS.length} icons`);
