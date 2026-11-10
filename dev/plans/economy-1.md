# Hunger, multi-pet facilities, recipes, upgrade costs, craft times

Plugin sources: dev/pipi-rpg/src (PipiLife.java mainly, Facility.java, TownSystems.java may hold recipes/shop). Client screens: dev/pipi-travel/src (CraftScreen.java etc.) may show recipe/cost/time text — keep them consistent if they hard-code any of it. Build: `bash dev/pipi-rpg/build.sh`, `bash dev/pipi-travel/build.sh`.

User's words (Korean, verbatim intent):
"배고픔은 항상 가득 차있는 상태에서 반칸만 남아있게 해줘 계속 가득 차 있으니까 피가 계속 차. 그리고 한 작업장에 펫이 여러마리 붙으면 작업 속도가 올라가게 해줘 작업장에 펫 한마리가 있으면 1개 생성하는데 20초 여기에 한마리씩 더 붙을떄마다 1개 생성하는데 3초씩 즐어들게 해줘 이거는 모든 작업대에 적용해줘 제작대 빼고 그리고 작업대 업그레이드는 3레벨까지 있는데 레벨을 올릴때 마다 펫을 더 배치 할 수 있는 칸이 2칸일 해금돼. 펫들 포만감은 작업 1번에 포만감 1 차감되게 해줘. 제작대에서 만들때 들어갈 재료 적어놧어 반영해줘 업그레이드 비용 적은 부분만 적용시켜줘"

## A. Hunger
The existing hunger-removal listener keeps food at 20 and saturation 20, so health regenerates constantly. Change: keep every player's food level fixed at 17 (full bar minus half a shank) with saturation 0 and exhaustion 0. Natural regeneration needs food >= 18, so it stops; sprinting still works (needs > 6). Eating must not raise it (cancel all FoodLevelChangeEvent changes and re-pin to 17).

## B. Producer facilities (every pet workplace except the crafting station / 제작대)
- A facility now produces as one unit: with n working pets placed (n >= 1, a working pet has food > 0), it produces 1 item every T = max(2, 20 - 3*(n-1)) seconds (1 pet 20 s, 2 pets 17 s, 3 pets 14 s, 4 pets 11 s, 5 pets 8 s). Pet aptitude level no longer changes the speed. Pets whose food is 0 don't count; with n = 0 production stops.
- Each produced item costs 1 food from every working pet at that facility (작업 1번 = 포만감 1).
- Upgrades go to level 3. Placement slots: level 1 = 1, level 2 = 3, level 3 = 5 (each upgrade unlocks 2 slots). Find the current slot logic (currently 1 slot per upgrade) and replace it.
- Keep stock capacity, collection, pausing, feeding, GUI flows working; update GUI lore texts that show per-pet speed so they show the facility's current time per item and pet count.

## C. Crafting station recipes (exact ingredients)
- 판자 ← 원목 3
- 강철 ← 주괴 4 (iron ingot)
- 기본 포획틀 ← 원목 5
- 철 채석장 ← 철코어 5, 강철 15, 판자 20, 도면 1
- 블루다이아몬드 ← 다이아 2
- 기본 제작대 ← 판자 5, 도면 1
- 덧댄판자 ← 강철 1, 판자 3
- 식량 저장소 ← 코팅된 판자 10, 도면 1
- 코팅된 판자 ← 덧댄 판자 2, 블루다이아몬드 2
- 펫 가판대 ← 블루다이아몬드 2, 덧댄 판자 10
- 다이아몬드 채석장 ← 다이아몬드 코어 5, 블루다이아몬드 20, 덧댄 판자 15
- 철 포획틀 ← 철 5 (iron ingot), 원목 6
- 다이아 포획틀 ← 다이아 5, 철주괴 10, 원목 12
- 밀밭 ← 씨앗 32, 강철 10, 판자 20
"도면" is the blueprint (청사진) item; match the names to the existing custom items in the code. If an item named here does not exist (e.g. 철코어/다이아몬드 코어/덧댄 판자/코팅된 판자/블루다이아몬드), report it and use the closest existing one, don't invent new ItemsAdder items.

## D. Upgrade costs (only these; leave others as they are)
- 철 채석장 upgrade: 강철 40, 판자 40, 골드 20000
- 다이아몬드 채석장 upgrade: 블루다이아몬드 40, 덧댄 판자 40, 골드 150000
- 밀밭 upgrade: 씨앗 60, 강철 20, 판자 40, 골드 20000
- 벌목장 upgrade: 판자 40, 골드 10000
- 펫 창고 level-up: 강철 50, 판자 50
Same cost for each upgrade step unless the code already scales per level (then report it).

## E. Craft time at the crafting station, per item
- wood items 2 s, iron items 5 s, diamond items 10 s, blueprint (청사진/도면) items 10 min.
- Tier = highest-tier ingredient in the recipe (diamond > iron > wood); blueprints are always 10 min. Classify every recipe and list the classification in the report.
