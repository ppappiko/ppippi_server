# Pet roster from the design sheet (18 pets, capture, wild spawning)

Context: Minecraft server, Youer 1.21.1 hybrid (NeoForge + Bukkit API). Plugin sources: dev/pipi-rpg/src (package dev.pipi.life, deployed jar plugins/PipiLife-0.9.68.jar). Mod sources: dev/pipi-facilities/src (package dev.pipi.facilities, built by `bash dev/pipi-facilities/build.sh` into dev/pipi-facilities/out/). Pets are mod entities `PipiPets$Pet` keyed by a skin id; the plugin stores a species per pet. Models for all 18 skins already exist in dev/pipi-facilities/assets/pipifacilities/pets/<skin>.bbmodel.

Roster (skin id / Korean name / aptitude / level / spawn place):
- sparrow 짱새 FARM 1 forest
- cat 검은고양이 LUMBER 1 plains
- rabbit 마토 CRAFT 2 cherry_grove
- fox 레디 MINE 1 desert
- redpanda 레시 CRAFT 1 forest
- otter 해달수 LUMBER 2 warm_ocean
- tortoise 딱지 MINE 3 windswept_gravelly_hills
- raccoon 피토 CRAFT 3 mushroom_fields
- hornrabbit 설토 LUMBER 2 ice_spikes
- mole 두류 MINE 2 wooded_badlands
- thundersheep 썬더쉽 MINE 2 sunflower_plains
- moonbear 달투 LUMBER 3 deep_dark
- ferret 장꾸펫 FARM 1 forest
- angelpig 천사픽 FARM 2 flower_forest
- frog 가마 LUMBER 2 mangrove_swamp
- sunlizard 파로 FARM 3 stony_peaks
- imugi 이무기 TRANSPORT 2 nether, on a bastion remnant
- vampbat 블벳 TRANSPORT 1 nether, in a nether fortress

Per level: capture rate 120 / 45 / 10, max HP 35 / 55 / 80, pet-stand sale price 3000 / 8000 / 20000.

## s1 Register sunlizard, imugi, vampbat in the mod
files: dev/pipi-facilities/src/PipiPets.java, dev/pipi-facilities/src/PetRenderers.java
difficulty: easy
do: Add entity types pet_sunlizard (hitbox 0.7 wide x 0.5 tall), pet_imugi (1.0 x 0.6) and pet_vampbat (0.6 x 0.7) exactly like the existing 15 pet types: registration, attributes, the `spawn(world, skin, x, y, z)` switch, and renderer registration in PetRenderers (they use the bbmodel path like the others). Don't change the existing types.
done-when: `bash dev/pipi-facilities/build.sh` exits 0.

## s2 Plugin build script
files: dev/pipi-rpg/build.sh
difficulty: easy
do: Write a bash script (mirroring dev/pipi-facilities/build.sh in style) that compiles all dev/pipi-rpg/src/*.java with `javac --release 21 -proc:none -encoding UTF-8` against the classpath C:/Users/user/Documents/Codex/2026-11-04/sor/work/pipi-rpg/compile-api (an extracted class directory; add the server's plugins/*.jar if classes are missing), unpacks the newest non-.bak/.failed plugins/PipiLife-*.jar into a temp dir, deletes its old dev/pipi/life/*.class (so removed inner classes don't linger), copies in the compiled classes, bumps the patch version in plugin.yml (0.9.68 -> 0.9.69), and writes dev/pipi-rpg/out/PipiLife-<ver>.jar. It must not touch plugins/. Print the output path and version.
done-when: `bash dev/pipi-rpg/build.sh` exits 0.

## s3 Pet data: 18-pet roster, levels, HP, price, names; remove vanilla pets
files: dev/pipi-rpg/src/PipiLife.java, dev/pipi-rpg/src/CustomPets.java, plugins/ItemsAdder/contents/pipi_pets/pets.yml
needs: s2
difficulty: hard
do: Rebuild the plugin's Pet enum to exactly the 18 roster pets above and delete all vanilla-animal pets (FOX, WOLF, CAT, RABBIT, CHICKEN, PIG, SILVERFISH, BEE, GOAT, PANDA, ALLAY and any other non-roster entry) together with code that only served them. Each pet now has ONE aptitude (facility kind + level 1..3) instead of a map; pets never have a PET_STORAGE aptitude (pets don't work in the pet storage). Add a TRANSPORT aptitude kind labelled "운반": not a producer, and no facility accepts it yet (its workplace comes later), so transport pets can be captured, stored and sold but not placed anywhere. Wherever the old aptitude map was read (production speed, placement checks, status lore "<적성> 적성 Lv.N", craft aptitude, scene task selection) use the single aptitude: a pet works only in facilities of its aptitude kind, with its level as the old map value. Labels are the Korean names above. Max HP from level (35/55/80) is set on every spawned pet (replace the frog/cat/20 special case in CustomPets.spawn). `captureRate` returns the level's rate (120/45/10). The pet-stand sale price is the level's price (3000/8000/20000): find where the old `price` field becomes money and keep that path with these values. Keep the per-pet EntityType only where still needed (spawn-egg icon, eligibility); new three: sunlizard AXOLOTL, imugi SILVERFISH, vampbat BAT. Update every species list: CustomPets TYPES and the petspawn usage text (all 18 skins), saleKey, species-to-Pet mapping, petSkin. Saved data referring to a removed species must be skipped without exceptions (treated as an empty slot, one warning logged). In pets.yml rename display names to the Korean names above and add entries for the three new pets like the existing ones (reuse an existing icon texture if none exists; next free model_id).
done-when: `bash dev/pipi-rpg/build.sh` exits 0 and `! grep -q "WOLF\|ALLAY" dev/pipi-rpg/src/PipiLife.java`.

## s4 Pokemon gen-5 capture formula
files: dev/pipi-rpg/src/CaptureMath.java, dev/pipi-rpg/src/CaptureSystem.java
needs: s2
difficulty: easy
do: Replace the Cobblemon-style math with the design sheet's formula. a = (3*M - 2*H) * rate * ball / (3*M), where M = max HP, H = current HP, rate = species capture rate, ball = 1 / 1.5 / 2 for basic / iron / diamond capture frame. If a >= 255 the capture succeeds immediately (100%). Otherwise b = 65536 / (255 / a)^(3/16) and there are exactly three checks (the first plus two shakes): each draws a random integer 0..65535 and passes if below b; all three passing = captured; failing at check i means the pet breaks out after i shakes (keep the existing outcome/animation flow in CaptureSystem that uses the failing check's index). Remove the 0.98 cap. Keep throwSpeed/clubDamage/weakenedHealth unchanged. Any displayed capture-chance percentage shows the true overall probability (1.0 when a >= 255, else (b/65536)^3).
done-when: `bash dev/pipi-rpg/build.sh` exits 0.

## s5 Biome-based wild spawning
files: dev/pipi-rpg/src/CustomPets.java, dev/pipi-rpg/src/CaptureSystem.java, dev/pipi-rpg/src/PipiLife.java, plugins/PipiLife/config.yml
needs: s3, s4
difficulty: hard
do: Replace the current "turn a naturally spawned vanilla animal into a pet 15% of the time" listener with direct spawning. In the wild worlds cancel every natural and chunk-generation spawn of passive animals (org.bukkit.entity.Animals plus bats, squid, glow squid, fish, axolotls, striders; hostile monsters stay untouched): hunger is being removed, so animals aren't needed. The wild worlds are the configured `wild-world` and its nether (config key `wild-nether-world`, default auto: the first world with Environment.NETHER other than village/pipi_tutorial); capture and the petspawn command must also accept the wild nether. A repeating task (every `pet-spawn.interval-ticks`, default 200) makes, for each player in a wild world, a few attempts at a random spot 24..56 blocks away: skip if `pet-spawn.cap` (default 6) or more roster pets are within 64 blocks; find a standing spot (surface pets: highest solid non-leaf block with 2 air above; deep_dark and nether: search within ±16 of the player's Y for solid ground with 2 air above; warm_ocean may also use the water surface, and otters take no drowning damage); read the biome there and pick uniformly among roster pets whose place matches. Nether pets: imugi only on a bastion remnant, vampbat only in a nether fortress — use World#getStructures / GeneratedStructure if the API has it, otherwise the block below (nether bricks = fortress; blackstone, polished blackstone bricks, gilded blackstone = bastion). Transport pets (imugi, vampbat) are extremely rare: chance `pet-spawn.transport-chance` (default 0.005) per matching attempt, and never while `transport-unlocked` is false (default false; set true once the nether boss exists, a later task). Spawned pets use the existing spawn(...) path (persistent, wild, capturable). Add the new keys with Korean comments to plugins/PipiLife/config.yml.
done-when: `bash dev/pipi-rpg/build.sh` exits 0.
