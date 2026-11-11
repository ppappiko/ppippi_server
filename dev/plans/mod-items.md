# Replace vanilla-based custom items with real mod items

User's words: "지금 제작 아이템중에 바탕 아이템이 있는거 싹 다 바탕 아이템을 빼고 새로운 아이템으로 바꿔줘. 바탕 아이템이 있으면 나중에 플레이어가 플레이 할때 그 아이템을 만들면 리소스가 적용 될꺼 아니야 그 부분 방지 하고 싶어 악용할 수 도 있고"

Today the PipiLife plugin (dev/pipi-rpg/src, mostly TownSystems.java `taggedItem(Material, key, label)` + PipiLife.java) makes its custom items as vanilla items (OAK_PLANKS, IRON_NUGGET, DIAMOND, AMETHYST_SHARD, PAPER, FLINT, STICK, IRON_SWORD, DIAMOND_SWORD, IRON_AXE, TRIPWIRE_HOOK, COOKED_COD, COD, IRON_INGOT...) with a PDC tag (`processed` key) and CustomModelData drawn by ItemsAdder (plugins/ItemsAdder/contents/pipi_materials, pipi_cores, pipi_blueprints). They then work in vanilla recipes (e.g. 9 강철 nuggets → iron ingot, flint & steel, books) and a vanilla item with that CustomModelData shows the custom texture. The PipiFacilities NeoForge mod (dev/pipi-facilities/src, required on clients) already registers facility blocks and `*_blueprint` items; the plugin reaches mod items through `Material.matchMaterial("PIPIFACILITIES_" + ID)` (see dev/pipi-rpg/src/Facility.java `blueprintMaterial()`).

Item list (key → Korean name → mod item id). Confirm every taggedItem/custom item site in the plugin and include any missed one (except the real vanilla starter pickaxe and gear the smithing system enchants, unless they are custom-textured materials):
- plank 판자 → plank
- plate 강철 → steel
- reinforced_plank 덧댄 판자 → reinforced_plank
- coated_plank 코팅된 판자 → coated_plank
- blue_diamond 블루 다이아몬드 → blue_diamond
- diamond 다이아몬드 (the tagged crafted one) → refined_diamond
- iron_core 철 코어 → iron_core; diamond_core 다이아몬드 코어 → diamond_core; gold_core (legacy, no longer dropped) → gold_core
- blueprint_paper 도면 → blueprint_paper
- repair_kit 수리키트 → repair_kit
- basic/iron/diamond capture frames (traps) 기본/철/다이아 포획틀 → basic_trap, iron_trap, diamond_trap
- club 몽둥이, ironclub 철몽둥이, diamondclub 다이아몬드 방망이 → club, iron_club, diamond_club (tools with durability: 59 / 250 / 1561, handheld model)
- hammer 건설망치 → hammer (durability 250, handheld)
- feed 생선 먹이 → fish_feed; starter_fish 민물고기 → river_fish
- ingot 철 주괴: make the plugin produce a PLAIN vanilla IRON_INGOT (no tag, no CustomModelData) — the recipes accept vanilla iron ingots anyway.

## s1 Register the mod items
files: dev/pipi-facilities/src, dev/pipi-facilities/assets
difficulty: medium
do: In the PipiFacilities mod register every mod item id above as a plain Item (stack size 64; tools: stack 1 with the given durability, no vanilla tool behaviour needed — the plugin handles damage), following how `*_blueprint` items are registered (same DeferredRegister, same mod id `pipifacilities`). Add item models in dev/pipi-facilities/assets/pipifacilities/models/item/<id>.json (`item/generated`, tools `item/handheld`) and copy textures from the ItemsAdder contents (plugins/ItemsAdder/contents/pipi_materials/textures/item, pipi_cores, pipi_blueprints, pipi_pets... find the PNG each item currently uses through its CustomModelData/model_path) into dev/pipi-facilities/assets/pipifacilities/textures/item/<id>.png; if an item has no custom texture, make a simple 16x16 placeholder that is clearly distinct (not a vanilla texture). Add assets/pipifacilities/lang/ko_kr.json and en_us.json names (Korean names above for both). Don't register any vanilla recipes for them. Keep existing items/blocks unchanged.
done-when: `bash dev/pipi-facilities/build.sh` exits 0 and `unzip -l dev/pipi-facilities/out/PipiFacilities-*.jar | grep -c "textures/item/steel.png"` prints a number > 0 for the newest jar.

## s2 Plugin uses the mod items and converts old ones
files: dev/pipi-rpg/src
needs: s1
difficulty: hard
do: Change `taggedItem` (and every other custom-item creation site, including CaptureSystem / PipiLife capture frames, repair kit, shop goods, drops, testgive, starter items) to build the item from `Material.matchMaterial("PIPIFACILITIES_" + ID.toUpperCase())` (fail clearly with a log if the mod is missing), keeping the same display name, lore and PDC `processed` tag, and no CustomModelData. Everything that identifies items keeps using the PDC tag; update any ingredient/match code that also checks the vanilla Material (e.g. `tagged(Material.IRON_NUGGET, "plate", ...)`, `actual.getType()==Material.IRON_INGOT && found.equals("ingot")`) so it matches the mod material, and so 철 주괴 accepts plain vanilla IRON_INGOT. Tools keep their attack/durability handling working on the new materials (attribute modifiers set via meta; durability through Damageable meta). Migration: when a player joins, and when any inventory is opened (InventoryOpenEvent, incl. chests and the plugin's own storage GUIs) convert old items — a vanilla-material ItemStack carrying one of these PDC tags → new mod ItemStack with the same amount and the same ItemMeta copied over (display name, lore, PDC incl. smithing/enchant tags, damage), minus CustomModelData; old tagged 철 주괴 → plain IRON_INGOT. Also convert items stored by the plugin in its data files if it stores ItemStacks (e.g. pet storage, facility stock is counts only?) — check. Remove now-dead CustomModelData constants (materialModel etc.) and the matching entries from plugins/ItemsAdder/contents/*.yml for the converted items (leave pet icons and unrelated items).
done-when: `bash dev/pipi-rpg/build.sh` exits 0 and `! grep -n "setCustomModelData(4810[2-9]\|setCustomModelData(4811" dev/pipi-rpg/src/TownSystems.java`.
