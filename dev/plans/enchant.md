# Enchant system (MapleStory cube + ability-lock style)

Code today: dev/pipi-rpg/src/TownSystems.java (gear crafting at the blacksmith sets PDC `smith_lines` = nextInt(3,8) at ~1372 and ~1554; options stored as `smith_rolls` comma list of ints; `updateGear` builds lore/attributes; PICK_OPTIONS/pickBonus; a "큐브 재설정" button in the smith menu slot 22), dev/pipi-rpg/src/CombatStats.java (combat stats / derived modifiers), client dev/pipi-travel/src/AlchemyScreen.java + GearTooltips.java. Builds: `bash dev/pipi-rpg/build.sh`, `bash dev/pipi-travel/build.sh`.

## Rules (from the user)
- Enchant slots when the blacksmith crafts gear/tools: 3..6 (was 3..7). Existing items with 7 keep 7.
- Fishing rods can't be enchanted.
- Enchanting happens at the 연금술사 (alchemist), for gear and tools the player holds/selects. Remove the "큐브 재설정" from the blacksmith.
- Grades: 레어 → 에픽 → 유니크 → 레전더리. Every item has a permanent enchant grade (starts 레어 on first enchant). On each roll the item may grade up: 레어→에픽 15%, 에픽→유니크 5%, 유니크→레전더리 3%. Grade never goes down.
- On a roll, every UNLOCKED line is re-rolled: line 1 always gets an option of the item's current grade; every other line gets the current grade with 1% chance, otherwise (99%) one grade below (레어 items: 레어). Options are drawn uniformly from the item type's list (duplicates allowed).
- Lock (ability style): the player can lock any lines; a locked line keeps its option AND its grade unchanged. At least one line must stay unlocked. Grade-up can still happen while lines are locked (it affects only newly rolled lines; line 1 if locked keeps its old grade).
- Cost per roll by the item's current grade: 레어 1,000 / 에픽 5,000 / 유니크 50,000 / 레전더리 100,000 골드, multiplied by the lock count factor: 0 locks ×1, 1 ×2, 2 ×2.5, 3 ×3, 4 ×3.5, 5 ×4 (each extra lock +0.5).
- Value scaling by grade: percent options (shown with %) add +3%p per grade step from the 레어 value (3% → 6% → 9% → 12%), except the core option which adds +1%p per grade (1% → 2% → 3% → 4%); flat "+" options double per grade (레어 v, 에픽 2v, 유니크 4v, 레전더리 8v).

## Pickaxe options (values at 레어)
- 하급 코어 획득 확률 증가 +1% at 레어 (percent; applies to iron/diamond core drop chance in dropOreCore). Only rollable on 레어/에픽 lines (from 유니크 the core option is for 중급 cores, which don't exist yet: exclude it from 유니크/레전더리 pools for now).
- 하급 광물 획득 증가 +0.4 (flat): when mining iron/diamond ore, extra ore drops on average = value (e.g. 0.4 → 40% chance +1; 1.6 → +1 guaranteed and 60% +1 more).
- 길이 증가 +0.02 (flat): block interaction range (existing attribute).
- 반짝임 +4 (flat): ore highlight radius in blocks — while holding the pickaxe, every few seconds (cooldown ~5 s, triggered on mining or holding), iron/diamond/other ores within the radius are shown glowing to that player only for ~3 s (e.g. glowing BlockDisplay entities visible only to him, removed afterwards). Keep it cheap: scan with ChunkSnapshots off the main thread or cap the scanned volume, max N highlights (e.g. 40).
- 채굴 속도 증가 +2 (flat): block break speed attribute.
- 내구도 증가 +1 (flat): max durability.

## Gear options (weapons 검/활/지팡이 and armor; values at 레어)
- 이동속도 증가 +3% ; 이동속도 증가 +20 (이동속도 stat)
- 방어력 증가 +3% ; 방어력 증가 +20
- 행운 증가 +20
- 힘 증가 +20 ; 민첩 증가 +20 ; 지능 증가 +20 (three separate options)
- 힘 증가 +3% ; 민첩 증가 +3% ; 지능 증가 +3% (three separate options)
- 치명타 확률 증가 +3%
- 체력 증가 +20 ; 체력 증가 +3%

## Stats (apply in CombatStats / combat)
- 힘 1 → 공격력 +0.5, 체력 +1
- 민첩 1 → 공격력 +1, 이동속도 stat +0.5
- 지능 1 → 공격력 +1, 치명타 확률 +0.5%
- 행운 1 → 치명타 확률 +1%
- 이동속도 stat 100 → 이동 속도 +1%
Percent options multiply the summed base stat (e.g. 힘 +3% of total 힘). Show totals in the status/character screen if one exists.
