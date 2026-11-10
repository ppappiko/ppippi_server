// Validates generated Blockbench .bbmodel pets against the shared SD recipe.
// Usage: node dev/pipi-pets/check.mjs <skin> [<skin> ...]
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "../..");
const REQUIRED_ANIMS = ["idle", "walk", "work", "eat", "hurt"];

function collectGroups(node, groups) {
  if (!node || typeof node !== "object") return;
  if (node.uuid) groups.add(node.uuid);
  if (Array.isArray(node.children)) {
    for (const child of node.children) {
      if (child && typeof child === "object") collectGroups(child, groups);
    }
  }
}

function checkSkin(skin) {
  const errors = [];
  const bbmodelPath = path.join(root, "dev/pipi-facilities/assets/pipifacilities/pets", `${skin}.bbmodel`);
  const pngPath = path.join(root, "dev/pipi-pets/out", `${skin}.png`);

  if (!fs.existsSync(bbmodelPath)) {
    return [`missing ${path.relative(root, bbmodelPath)}`];
  }
  if (!fs.existsSync(pngPath)) {
    errors.push(`missing ${path.relative(root, pngPath)}`);
  }

  let model;
  try {
    model = JSON.parse(fs.readFileSync(bbmodelPath, "utf8"));
  } catch (err) {
    return [`cannot parse ${path.relative(root, bbmodelPath)}: ${err.message}`];
  }

  const res = model.resolution;
  if (!res || res.width !== 64 || res.height !== 64) {
    errors.push(`resolution must be 64x64 (got ${res ? `${res.width}x${res.height}` : "none"})`);
  }

  const tex = Array.isArray(model.textures) ? model.textures[0] : null;
  if (!tex || typeof tex.source !== "string" || !tex.source.startsWith("data:image/png;base64,")) {
    errors.push("textures[0].source must start with data:image/png;base64,");
  }

  const anims = Array.isArray(model.animations) ? model.animations : [];
  const animNames = new Set(anims.map((a) => a.name));
  for (const name of REQUIRED_ANIMS) {
    if (!animNames.has(name)) errors.push(`missing animation "${name}"`);
  }

  const elements = Array.isArray(model.elements) ? model.elements : [];
  const elementUuids = new Set(elements.map((e) => e.uuid));

  const groups = new Set();
  const outliner = Array.isArray(model.outliner) ? model.outliner : [];
  for (const node of outliner) collectGroups(node, groups);

  for (const anim of anims) {
    const animators = anim.animators || {};
    for (const key of Object.keys(animators)) {
      if (!groups.has(key)) {
        errors.push(`animation "${anim.name}" animator ${key} is not an outliner group uuid`);
      }
    }
  }

  const visitChildren = (node) => {
    if (!node || typeof node !== "object") return;
    if (Array.isArray(node.children)) {
      for (const child of node.children) {
        if (typeof child === "string") {
          if (!elementUuids.has(child)) {
            errors.push(`outliner child ${child} does not match any element`);
          }
        } else {
          visitChildren(child);
        }
      }
    }
  };
  for (const node of outliner) visitChildren(node);

  for (const el of elements) {
    const faces = el.faces || {};
    for (const [dir, face] of Object.entries(faces)) {
      const uv = face && face.uv;
      if (!Array.isArray(uv) || uv.length !== 4) {
        errors.push(`element ${el.name} face ${dir} has invalid uv`);
        continue;
      }
      for (const v of uv) {
        if (typeof v !== "number" || v < 0 || v > 64) {
          errors.push(`element ${el.name} face ${dir} uv ${v} outside 0..64`);
        }
      }
    }
  }

  return errors;
}

const skins = process.argv.slice(2);
if (skins.length === 0) {
  console.error("usage: node dev/pipi-pets/check.mjs <skin> [<skin> ...]");
  process.exit(2);
}

let failed = false;
for (const skin of skins) {
  const errors = checkSkin(skin);
  if (errors.length) {
    failed = true;
    console.error(`FAIL ${skin}`);
    for (const e of errors) console.error(`  - ${e}`);
  } else {
    console.log(`ok ${skin}`);
  }
}
process.exit(failed ? 1 : 0);
