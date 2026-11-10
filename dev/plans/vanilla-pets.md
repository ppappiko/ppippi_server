# Vanilla-style fantasy pets (user's character sheet)

Recipe for every pet step (the user approved the vanilla-style fox, skin `fox`, as the quality bar):
- Write `dev/pipi-pets/build-<skin>.mjs` on top of `dev/pipi-pets/lib/vanilla.mjs`, structured like `dev/pipi-pets/build-fox.mjs` (read it first; it is the reference for materials, ASCII face grids, groups and animations). Output `dev/pipi-facilities/assets/pipifacilities/pets/<skin>.bbmodel` and `dev/pipi-pets/out/<skin>.png`. Do not modify lib/, check.mjs, preview.mjs or other pets.
- Look: a fantasy creature that could pass as a real Minecraft vanilla mob. Integer-pixel boxes, 1 texel per pixel, weighted multi-tone noise materials (3-4 tones each, like the fox), hand-placed pixel-art faces via ASCII grids (eyes are 1-2 px, vanilla-like, not big SD eyes). Vanilla mob proportions for the body plan given below — pets must NOT all be "box body on four legs"; follow each spec's body plan. Front faces -Z, feet on y=0, centered on x=0/z=0. Size near the given hitbox (blocks x 16 px); hats, horns, halos may exceed.
- Groups whose name ends with `_work` show only while working, `_eat` only while eating, `_rest` only while NOT working; groups ending with `_glow` (and their children) render full-bright (use for fire, halos, sparks, glowing marks).
- Animations with `"catmullrom"` interpolation and the same principles as the fox (easing, anticipation, overlapping follow-through on tails/ears/wings, seamless loops with identical first and last keys, linear/step only for snappy accents): `idle` (3-4 s, breathing + a personality beat), `walk` (loop matching the body plan's gait), `work` (loop, the signature action), `eat` (loop), `hurt` (once, 0.3-0.4 s flinch with overshoot). Whole-body motion goes on the `root` bone. Keep the fox's sign conventions (positive X on a head = nod down).
- Verify with `node dev/pipi-pets/check.mjs <skin>` and render previews with `node dev/pipi-pets/preview.mjs <skin> <anim>` for each animation.

## s0 Glow and rest group suffixes
files: dev/pipi-facilities/src/PetModels.java, dev/pipi-pets/preview.mjs
difficulty: easy
do: In both the renderer and the preview, add the `_rest` suffix (group and its children drawn only when the action is not work/2) next to the existing `_work`/`_eat` handling, and the `_glow` suffix (a group and all descendants drawn with full-bright light 15728880 in Java; in the preview with face light forced to 1.0). Keep Java and JS equivalent.
done-when: `bash dev/pipi-facilities/build.sh` exits 0 and `node dev/pipi-pets/preview.mjs fox` exits 0.

## s1 짱새 sparrow
files: dev/pipi-pets/build-sparrow.mjs, dev/pipi-facilities/assets/pipifacilities/pets/sparrow.bbmodel, dev/pipi-pets/out/sparrow.png
needs: s0
difficulty: medium
do: Hitbox 0.5 x 0.65. Body plan: a small vanilla-parrot-like perching bird — plump body tilted about 20° nose-up, two thin legs, wings as flat side panels (bones wing_l/wing_r), short fan tail. Character: a stubby short beak and a spiky messy crest — 4-5 small spikes of different heights sticking up/back from the head top (bone `crest`). Colors: brown back #7A5236/#8E6242 with darker streaks, cream chest #EAD9B8, chestnut cap, dark cheek dot, beak #E0A030. Gait: `walk` is little two-footed hops. `work`: hover-flaps above the ground pecking (wings fast, root up 3 px). Idle beat: crest bristles (spikes wiggle) and head tilts.
done-when: `node dev/pipi-pets/build-sparrow.mjs` exits 0 and `node dev/pipi-pets/check.mjs sparrow` exits 0.

## s2 검은고양이 cat
files: dev/pipi-pets/build-cat.mjs, dev/pipi-facilities/assets/pipifacilities/pets/cat.bbmodel, dev/pipi-pets/out/cat.png
needs: s0
difficulty: medium
do: Hitbox 0.55 x 0.75. Body plan: vanilla cat proportions (long low body, small head, thin legs, long tail curving up). Character: a haughty, prim "gyaru" black cat — half-lidded narrow eyes with long dark eyelashes (pixel lashes on the face plus 1-px eyelash flicks sticking out at the eye corners), glossy pink lip/nose, long pink-and-gold manicured claws on the front paws (small claw cubes), a gold hoop earring on one ear, a pink choker with a small heart. Colors: fur #1E1B22/#2A2630/#35303C, eye #E8C440 slits. Idle beat: lifts its chin and looks away disdainfully, tail tip flicks. `work`: admires/files its claws then flicks a quick precise swipe. `walk`: elegant catwalk (tail high, slight hip sway).
done-when: `node dev/pipi-pets/build-cat.mjs` exits 0 and `node dev/pipi-pets/check.mjs cat` exits 0.

## s3 마토 rabbit
files: dev/pipi-pets/build-rabbit.mjs, dev/pipi-facilities/assets/pipifacilities/pets/rabbit.bbmodel, dev/pipi-pets/out/rabbit.png
needs: s0
difficulty: medium
do: Hitbox 0.55 x 1.05. Body plan: BIPEDAL — stands and walks upright on two big hind feet like a small villager-proportioned rabbit: upright body, two arms (bones arm_l/arm_r), big feet, round tail. Character: a brown rabbit (#7A5638/#8C6644/#6A4A30, cream muzzle and belly) wearing a deep-blue pointed wizard hat with a bent-over tip and a star patch, long ears poking up through slits in the hat brim, holding a blue magic wand in the right hand with a glowing blue crystal tip (tip in a `_glow` group). `walk`: upright two-legged waddle with arm swing (wand arm swings less). `work`: casting — raises the wand, draws a circle, sparkle cubes (`spark_work` + glow) pop. Idle beat: adjusts hat with the free paw.
done-when: `node dev/pipi-pets/build-rabbit.mjs` exits 0 and `node dev/pipi-pets/check.mjs rabbit` exits 0.

## s4 레시 redpanda
files: dev/pipi-pets/build-redpanda.mjs, dev/pipi-facilities/assets/pipifacilities/pets/redpanda.bbmodel, dev/pipi-pets/out/redpanda.png
needs: s0
difficulty: medium
do: Hitbox 0.6 x 0.75. Body plan: vanilla-panda-like quadruped but smaller and fluffier, with a long bushy ringed tail (cream rings). Character: cute red panda with a single green leaf resting on top of its head (leaf with a vein and stem, tilted, bone `leaf`). Colors: rust #B8522A/#C9622F/#A24624, white face mask (cheeks, brows, ear rims), dark brown legs and belly #3A2420. Idle beat: the leaf wobbles and the panda glances up at it. `work`: stands on hind legs briefly and pats with both front paws, leaf bouncing. `eat`: holds bamboo-ish food (`food_eat` green stick) and nibbles.
done-when: `node dev/pipi-pets/build-redpanda.mjs` exits 0 and `node dev/pipi-pets/check.mjs redpanda` exits 0.

## s5 해달수 otter
files: dev/pipi-pets/build-otter.mjs, dev/pipi-facilities/assets/pipifacilities/pets/otter.bbmodel, dev/pipi-pets/out/otter.png
needs: s0
difficulty: medium
do: Hitbox 0.65 x 0.85. Body plan: long sleek otter body low to the ground with short legs and a thick tapered flat tail; when idle it often rears up (root pitches up so the front half lifts). Character: a fierce pirate — black tricorne hat with gold trim and a white skull mark, a black eyepatch with strap over one eye, the other eye with an angry slanted brow, a bared fang. Colors: brown #6E4A30/#7E5638/#5E3E28, lighter muzzle and throat #C9A27A. Idle beat: rears up and scans the horizon with its one eye. `work`: aggressive gnawing lunge. `walk`: undulating lope.
done-when: `node dev/pipi-pets/build-otter.mjs` exits 0 and `node dev/pipi-pets/check.mjs otter` exits 0.

## s6 딱지 tortoise
files: dev/pipi-pets/build-tortoise.mjs, dev/pipi-facilities/assets/pipifacilities/pets/tortoise.bbmodel, dev/pipi-pets/out/tortoise.png
needs: s0
difficulty: medium
do: Hitbox 0.85 x 0.65. Body plan: low wide tortoise with a hard, angular, stepped shell (stacked flat boxes, each tier smaller, with blocky plate seams in the pixel art), four stubby elephant-like legs, short tail. Character: everything brown and angular — a squared blocky head with a heavy flat brow ridge and a stern little mouth. Colors: shell #5C3E24/#6E4A2C/#4A3020 with lighter plate centers #8A6440, skin #8A6A48/#7A5C3E. `work` (6 s): head and legs retract into the shell (scale to 0), root spins 1440° yaw, then they pop out. `walk`: slow heavy plod. Idle beat: slow blink and head bob.
done-when: `node dev/pipi-pets/build-tortoise.mjs` exits 0 and `node dev/pipi-pets/check.mjs tortoise` exits 0.

## s7 피토 raccoon
files: dev/pipi-pets/build-raccoon.mjs, dev/pipi-facilities/assets/pipifacilities/pets/raccoon.bbmodel, dev/pipi-pets/out/raccoon.png
needs: s0
difficulty: medium
do: Hitbox 0.7 x 0.85. Body plan: stocky quadruped that sits up on its hind legs to use its front paws like hands (separate forearm/paw bones with little fingers). Character: a dexterous craftsman raccoon — black eye mask band, white brows and muzzle, ringed bushy tail, nimble pale-pink hands, a tiny leather tool belt. Colors: gray #7E7A78/#8E8A88/#6A6664, dark #2A2828. `work`: sits up and does fast, precise hand work — rapid alternating paw taps/turns in front of the chest with crisp linear accents, head focused down. Idle beat: rubs hands together. `walk`: rolling waddle.
done-when: `node dev/pipi-pets/build-raccoon.mjs` exits 0 and `node dev/pipi-pets/check.mjs raccoon` exits 0.

## s8 설토 hornrabbit
files: dev/pipi-pets/build-hornrabbit.mjs, dev/pipi-facilities/assets/pipifacilities/pets/hornrabbit.bbmodel, dev/pipi-pets/out/hornrabbit.png
needs: s0
difficulty: medium
do: Hitbox 0.65 x 0.9. Body plan: vanilla-rabbit-like quadruped (crouched body, big hind legs, long ears, puff tail). Character: an innocent-looking white rabbit with big round sparkly eyes and a small ivory horn on the forehead, but fierce inside — a group `angry_work` (red-tinted angry brows and narrowed eye overlay, a tiny flash of fang) shows only while working, and the normal innocent eyes sit in `eyes_rest`. Colors: white #F2EFEA/#E6E0D8/#FFFFFF, pink ears inside, horn #E8D8A8/#D4C08A. `work`: crouch, wiggle, then charge-headbutt with the horn. `walk`: rabbit hops (hind legs push, body arcs). Idle beat: nose twitch and an innocent ear flop.
done-when: `node dev/pipi-pets/build-hornrabbit.mjs` exits 0 and `node dev/pipi-pets/check.mjs hornrabbit` exits 0.

## s9 두류 mole
files: dev/pipi-pets/build-mole.mjs, dev/pipi-facilities/assets/pipifacilities/pets/mole.bbmodel, dev/pipi-pets/out/mole.png
needs: s0
difficulty: medium
do: Hitbox 0.75 x 0.65. Body plan: a crawler — low pear-shaped body hugging the ground, legs sprawled out to the sides, no visible neck, short pink tail. Character: huge, long, hard claws on the front paws (3 long pale bone-colored claw cubes per paw, #D8CCB4/#BFB298, longer than the head is wide), tiny squinting eyes, a pink fleshy nose. Fur dark velvet #3A3038/#463A44/#2E262C. `walk`: crawl with sprawled leg paddling and body sway. `work`: digging — claws rake alternately with strong strokes, dirt-colored crumbs (`dirt_work`) flicking. Idle beat: sniffing nose wiggle.
done-when: `node dev/pipi-pets/build-mole.mjs` exits 0 and `node dev/pipi-pets/check.mjs mole` exits 0.

## s10 썬더쉽 thundersheep
files: dev/pipi-pets/build-thundersheep.mjs, dev/pipi-facilities/assets/pipifacilities/pets/thundersheep.bbmodel, dev/pipi-pets/out/thundersheep.png
needs: s0
difficulty: medium
do: Hitbox 0.8 x 0.85. Body plan: vanilla sheep proportions (body + separate inflated wool layer, head with wool cap, four legs). Character: white sheep struck by lightning — static-frizzed wool with yellow zigzag lightning marks woven in (`marks_glow`), small yellow spark cubes around the wool (`sparks_glow`, flickering via scale keys). Colors: wool #F2F2F2/#E4E4EA/#FFFFFF, face/legs #E8DCC8, bolts #FFD93B/#FFF08A. Signature: it discharges static by ROLLING when it moves — `walk` curls the sheep into a ball (head and legs tuck to scale 0) and the root rolls forward continuously; `work` is a full-body static shake with sparks bursting. Idle beat: a little zap twitch.
done-when: `node dev/pipi-pets/build-thundersheep.mjs` exits 0 and `node dev/pipi-pets/check.mjs thundersheep` exits 0.

## s11 달투 moonbear
files: dev/pipi-pets/build-moonbear.mjs, dev/pipi-facilities/assets/pipifacilities/pets/moonbear.bbmodel, dev/pipi-pets/out/moonbear.png
needs: s0
difficulty: medium
do: Hitbox 0.7 x 0.85. Body plan: vanilla polar-bear-like proportions scaled to a cub (heavy body, thick legs, round ears). Character: black fur #1C1A20/#26232A/#302C34, blessed by the moon — a pale silver-white crescent moon on the chest (`moon_glow`, a few cubes slightly proud of the chest) and faint starry flecks in the fur, tan muzzle. Idle beat: sits back and looks up at the sky. `work`: stands up and slams both paws down. `walk`: heavy lumbering gait with shoulder roll.
done-when: `node dev/pipi-pets/build-moonbear.mjs` exits 0 and `node dev/pipi-pets/check.mjs moonbear` exits 0.

## s12 장꾸펫 ferret
files: dev/pipi-pets/build-ferret.mjs, dev/pipi-facilities/assets/pipifacilities/pets/ferret.bbmodel, dev/pipi-pets/out/ferret.png
needs: s0
difficulty: medium
do: Hitbox 0.65 x 0.7. Body plan: very long, thin, low body (split into front and back body bones so it can arch), short legs, long tail. Character: playful prankster — sable mask, a cheeky grin and a winking eye in the `wink_work` overlay. Colors: cream #E8D6B8/#F2E4CA, sable #5A3E2A/#6A4A34. `walk`: bouncy weasel war-dance gait (back arches). `work`: hops sideways and spins. Idle beat: sudden head-swivel sniff.
done-when: `node dev/pipi-pets/build-ferret.mjs` exits 0 and `node dev/pipi-pets/check.mjs ferret` exits 0.

## s13 천사픽 angelpig (angel guinea pig)
files: dev/pipi-pets/build-angelpig.mjs, dev/pipi-facilities/assets/pipifacilities/pets/angelpig.bbmodel, dev/pipi-pets/out/angelpig.png
needs: s0
difficulty: medium
do: Keep the skin id `angelpig`, but the creature is now an angel GUINEA PIG. Hitbox 0.75 x 0.95. Body plan: round loaf-shaped body with no tail, tiny legs barely visible, small round ears, short blunt face. Character: white feathered wings on the back (bones wing_l/wing_r, layered feather rows) and a golden halo ring floating above the head (`halo_glow`, a thin square ring of 4 cubes). Colors: tricolor guinea pig — cream #F2E2C4, ginger #D8944A/#C47E36 patches, white blaze; halo #F2C14E. It hovers: every animation keeps the root about 3 px up with a gentle bob. `work`: wings beat fast and it does a happy spin. Idle beat: halo bob and wing stretch.
done-when: `node dev/pipi-pets/build-angelpig.mjs` exits 0 and `node dev/pipi-pets/check.mjs angelpig` exits 0.

## s14 가마 frog
files: dev/pipi-pets/build-frog.mjs, dev/pipi-facilities/assets/pipifacilities/pets/frog.bbmodel, dev/pipi-pets/out/frog.png
needs: s0
difficulty: medium
do: Hitbox 0.65 x 0.55 (the hat may exceed). Body plan: vanilla-frog-like squat body sitting upright on folded hind legs, front legs as arms. Character: a samurai frog — a wide conical straw sakkat hat (stacked shrinking flat boxes, #C8A464/#B08E50 with woven pixel pattern) shading the eyes, a dark navy cloak #2A2E48 draped over the shoulders, a katana at its side: sheath + hilt in `sword_rest`, drawn blade (silver #D8DCE4 with a dark hilt) in `blade_work`. Skin #5E8C3A/#6E9E44, pale belly #C8D890. `walk`: short hops. `work`: iaido — crouch, then a fast horizontal slash with the blade and a held pose. Idle beat: tilts the hat brim.
done-when: `node dev/pipi-pets/build-frog.mjs` exits 0 and `node dev/pipi-pets/check.mjs frog` exits 0.

## s15 파로 sunlizard (new pet)
files: dev/pipi-pets/build-sunlizard.mjs, dev/pipi-facilities/assets/pipifacilities/pets/sunlizard.bbmodel, dev/pipi-pets/out/sunlizard.png
needs: s0
difficulty: medium
do: New skin id `sunlizard`. Hitbox about 0.7 x 0.5. Body plan: a crawling lizard — long low body, sprawled legs with elbows out, flat wedge head. Character: yellow scaled lizard (#E8C030/#D8A820/#F2D458, pale belly, orange back stripe) that has NO tail; blessed by the sun, a burning flame sits where the tail would be (`flame_glow`: 3-4 stacked flame cubes yellow→orange→red, flickering with scale/position keys in every animation). Small sun-ray crest on the head. `walk`: fast lizard scuttle with S-curve body sway. `work`: flame flares up big while it basks. Idle beat: tongue flick and head bob.
done-when: `node dev/pipi-pets/build-sunlizard.mjs` exits 0 and `node dev/pipi-pets/check.mjs sunlizard` exits 0.

## s16 이무기 imugi (new pet)
files: dev/pipi-pets/build-imugi.mjs, dev/pipi-facilities/assets/pipifacilities/pets/imugi.bbmodel, dev/pipi-pets/out/imugi.png
needs: s0
difficulty: medium
do: New skin id `imugi`. Hitbox about 0.9 x 0.6. Body plan: a big serpent — head plus 7 chained body segment bones (each a child of the previous) tapering to the tail, lying on the ground. Character: a pre-dragon imugi with dark blue-green scales (#1E3A44/#24485A/#1A2E38 with a teal sheen #3A7A80), pale belly plates, small horn nubs, long whiskers, golden eyes. `walk`: fast slithering — sine-wave yaw through the segments with increasing phase lag toward the tail. `work`: coils and rears the head up, whiskers swaying. Idle beat: tongue flick and slow breathing ripple.
done-when: `node dev/pipi-pets/build-imugi.mjs` exits 0 and `node dev/pipi-pets/check.mjs imugi` exits 0.

## s17 블벳 vampbat (new pet)
files: dev/pipi-pets/build-vampbat.mjs, dev/pipi-facilities/assets/pipifacilities/pets/vampbat.bbmodel, dev/pipi-pets/out/vampbat.png
needs: s0
difficulty: medium
do: New skin id `vampbat`. Hitbox about 0.5 x 0.9. Body plan: vanilla-bat-like — small furry body, big ears, membrane wings with finger bones (bones wing_l/wing_r, each with an outer wing child for a two-joint fold). Character: an ordinary-looking vampire bat with long white fangs, red eyes (`eyes_glow`), dark brown/charcoal fur #2E2426/#3A2E30, wing membrane #4A3238/#5A3C44. It always flies: every animation keeps the root 4-6 px up with a bob. `walk`: flapping flight. `work`: dives and bites. `eat`: hangs and sips (body tilts head-down). Idle beat: slow hovering flaps and a fang glint.
done-when: `node dev/pipi-pets/build-vampbat.mjs` exits 0 and `node dev/pipi-pets/check.mjs vampbat` exits 0.
