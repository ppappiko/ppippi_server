# SD models for the remaining 14 pets

Every pet step follows the same recipe as the approved fox (the user saw it in game and said it looks right):
- Copy the structure of `dev/pipi-pets/build-fox.mjs` into `dev/pipi-pets/build-<skin>.mjs`, reusing `dev/pipi-pets/lib/bbmodel.mjs` (do not modify the lib; if a helper is missing write it locally in the pet script). Output `dev/pipi-facilities/assets/pipifacilities/pets/<skin>.bbmodel` and `dev/pipi-pets/out/<skin>.png`.
- Same SD/chibi style as the fox: big head roughly as large as the body, short stubby legs, 64x64 texture with per-face UVs and the lib's soft shading, front-facing head face painted with 2x2 dark eyes with a 1px white highlight in the upper-left, a small 2x1 pink blush (#F59AA0) under each eye unless the spec says otherwise. Front faces -Z, feet on y=0, centered on x=0/z=0. Use the same axis/sign conventions the fox uses (rotation keyframes as in build-fox.mjs; positive X rotation tips forward the way the fox's head-nod does).
- Overall size: width and height should roughly match the given hitbox (block units x 16 px), extra height allowed for ears/hats/halos.
- Bone layout like the fox: `root` > `body` > `head` (+ face parts), `tail` if any, `leg_fl` `leg_fr` `leg_bl` `leg_br` (origins at leg tops). Optional props whose group name ends with `_work` are shown only while working, `_eat` only while eating.
- Animations required: `idle` (loop, gentle breathing + one small personality motion), `walk` (loop ~0.6 s, legs in diagonal pairs ±30-35°, body bob), `work` (loop, the signature move below), `eat` (loop ~0.8 s, head dips nibbling), `hurt` (once, 0.3 s squash and recover). Motions that move the whole pet go on the `root` bone.
- Check with the shared checker: `node dev/pipi-pets/check.mjs <skin>`.

## s0 Model checker
files: dev/pipi-pets/check.mjs
difficulty: easy
do: Write a Node ESM script taking one or more skin names. For each it reads `dev/pipi-facilities/assets/pipifacilities/pets/<skin>.bbmodel` and exits non-zero with a message unless: resolution is 64x64, textures[0].source starts with `data:image/png;base64,`, animations named idle, walk, work, eat, hurt all exist, every animator key is the uuid of a group in the outliner tree, every outliner child uuid string matches an element, and every face uv lies within 0..64. It also checks `dev/pipi-pets/out/<skin>.png` exists. Run it on `fox` as the self-test.
done-when: `node dev/pipi-pets/check.mjs fox` exits 0.

## s1 Sparrow 참새
files: dev/pipi-pets/build-sparrow.mjs, dev/pipi-facilities/assets/pipifacilities/pets/sparrow.bbmodel, dev/pipi-pets/out/sparrow.png
needs: s0
difficulty: medium
do: Hitbox 0.5 wide x 0.65 tall. A tiny round chubby sparrow: round head about 7x6x6 sitting on a plump body about 6x5x7, brown cap #8B5A3C with darker streaks, cream cheeks #F4E6CC with a small black cheek dot, short beak 2x1x2 #F0A030, wings as thin side cubes in bones `wing_l`/`wing_r` (brown with darker tips), short fan tail 3x1x3 tilted up, tiny orange legs. `work`: hovering — root rises 3 px with a gentle bob, wings flap ±50° roll fast (0.25 s cycle), head pecks. `eat`: hops forward slightly and pecks the ground.
done-when: `node dev/pipi-pets/build-sparrow.mjs` exits 0 and `node dev/pipi-pets/check.mjs sparrow` exits 0.

## s2 Frog 개구리
files: dev/pipi-pets/build-frog.mjs, dev/pipi-facilities/assets/pipifacilities/pets/frog.bbmodel, dev/pipi-pets/out/frog.png
needs: s0
difficulty: medium
do: Hitbox 0.65 wide x 0.55 tall. A squat round frog: wide head-body blob about 10x5x8, two big eye bumps on top (3x3x3, white with big dark pupils and highlight, instead of painted face eyes), a wide smile line, pink blush, green #7CC46A with lighter #A6DB8C top and cream-yellow #F3EBB0 belly, folded chunky back legs wider than the front legs. A pink tongue cube in a group `tongue_work`. `work`: hop in place (root up 3 px then squash on landing) with the tongue flicking out. `eat`: tongue flick and chewing cheeks (head scale pulse).
done-when: `node dev/pipi-pets/build-frog.mjs` exits 0 and `node dev/pipi-pets/check.mjs frog` exits 0.

## s3 Cat 까만 고양이
files: dev/pipi-pets/build-cat.mjs, dev/pipi-facilities/assets/pipifacilities/pets/cat.bbmodel, dev/pipi-pets/out/cat.png
needs: s0
difficulty: medium
do: Hitbox 0.55 wide x 0.75 tall. A black kitten: fur #2E2A33 with shade #1F1C24 and light #4A4553, big head about 9x8x7, yellow-green eyes #C9E265 (2x2 with a dark 1px slit pupil and white highlight), pink nose and pink inner ears, white socks on paws and a white chest patch, red collar #D23A48 with a small gold bell #F2C14E, long tail curving up. `work`: batting/kneading — front legs alternately raise and swipe, head bobs. `eat`: head dips, tail swish.
done-when: `node dev/pipi-pets/build-cat.mjs` exits 0 and `node dev/pipi-pets/check.mjs cat` exits 0.

## s4 Rabbit 마법사 토끼
files: dev/pipi-pets/build-rabbit.mjs, dev/pipi-facilities/assets/pipifacilities/pets/rabbit.bbmodel, dev/pipi-pets/out/rabbit.png
needs: s0
difficulty: medium
do: Hitbox 0.55 wide x 1.05 tall. A wizard bunny: cream-white fur #F4EEE6 with shade #DCD2C6, tall ears (about 6 px) with pink inner front faces, purple wizard hat #6A4C9C (shade #4E3678) with a wide brim and a tall tapering top built from shrinking cubes, a small gold star #F2C14E on the hat, short purple cape, round fluffy tail. A small wooden wand with a glowing tip in a group `wand_work` and a floating yellow sparkle cube in `spark_work`. `work`: casting — head sways, wand waves in a circle, sparkle bobs. `eat`: nose wiggle and nibbling.
done-when: `node dev/pipi-pets/build-rabbit.mjs` exits 0 and `node dev/pipi-pets/check.mjs rabbit` exits 0.

## s5 Red panda 수줍은 래서판다
files: dev/pipi-pets/build-redpanda.mjs, dev/pipi-facilities/assets/pipifacilities/pets/redpanda.bbmodel, dev/pipi-pets/out/redpanda.png
needs: s0
difficulty: medium
do: Hitbox 0.6 wide x 0.75 tall. A shy red panda: rust fur #C4532A (light #DE7444), white face markings (cheek patches, eyebrow dots, white ear rims), dark brown #3A2420 legs and belly, a big fluffy tail with painted cream rings. Extra-large blush. `work`: shy — front paws rise to cover the face alternately while the head tilts side to side. `eat`: holds paws near mouth and nibbles.
done-when: `node dev/pipi-pets/build-redpanda.mjs` exits 0 and `node dev/pipi-pets/check.mjs redpanda` exits 0.

## s6 Otter 해적 수달
files: dev/pipi-pets/build-otter.mjs, dev/pipi-facilities/assets/pipifacilities/pets/otter.bbmodel, dev/pipi-pets/out/otter.png
needs: s0
difficulty: medium
do: Hitbox 0.65 wide x 0.85 tall. A pirate otter: chubby, brown #8A5A3A with light #B07A52, cream face and belly #F1DFC4, a black eyepatch over one eye with a strap, a black tricorne pirate hat with a white skull-dot and gold trim, small whisker dots, flat wide tail. `work`: gnawing — lunges forward and bites, head pitching down, front paws pulling. `eat`: sits up holding food to the mouth with both front paws.
done-when: `node dev/pipi-pets/build-otter.mjs` exits 0 and `node dev/pipi-pets/check.mjs otter` exits 0.

## s7 Tortoise 가시 육지거북
files: dev/pipi-pets/build-tortoise.mjs, dev/pipi-facilities/assets/pipifacilities/pets/tortoise.bbmodel, dev/pipi-pets/out/tortoise.png
needs: s0
difficulty: medium
do: Hitbox 0.85 wide x 0.65 tall. A spiky tortoise: domed shell from stacked cubes, olive brown #6B7A3A with darker plate outlines painted on top and light rim #9AA65A, a few small stubby spikes on top (bone `spikes`), head and legs tan-green #A8B86A, big cute head poking out the front. `work` (length 6 s): head and legs retract into the shell (scale to 0 over 0.6 s), the root spins 1440° yaw over 4 s, then they pop back out. `eat`: neck stretches forward and munches.
done-when: `node dev/pipi-pets/build-tortoise.mjs` exits 0 and `node dev/pipi-pets/check.mjs tortoise` exits 0.

## s8 Raccoon 통통 너구리
files: dev/pipi-pets/build-raccoon.mjs, dev/pipi-facilities/assets/pipifacilities/pets/raccoon.bbmodel, dev/pipi-pets/out/raccoon.png
needs: s0
difficulty: medium
do: Hitbox 0.7 wide x 0.85 tall. A chubby raccoon: gray fur #8E8A88 (light #ABA7A4), black mask band across the eyes with the eyes inside it (use white highlight so eyes still read), white muzzle and eyebrows, rounder lighter belly, dark paws, a fluffy tail with painted dark rings. `work`: washing hands — front paws rub together in front of the chest with small side offsets, head looks down. `eat`: holds paws to mouth and chews with cheek puffs.
done-when: `node dev/pipi-pets/build-raccoon.mjs` exits 0 and `node dev/pipi-pets/check.mjs raccoon` exits 0.

## s9 Horn rabbit 작은뿔 흰 토끼
files: dev/pipi-pets/build-hornrabbit.mjs, dev/pipi-facilities/assets/pipifacilities/pets/hornrabbit.bbmodel, dev/pipi-pets/out/hornrabbit.png
needs: s0
difficulty: medium
do: Hitbox 0.65 wide x 0.9 tall. A fluffy white rabbit #FAF7F2 (shade #E2DCD3) with a small ivory-gold spiral horn #F2D98A on the forehead made from 2-3 shrinking cubes, medium upright ears with pink inners, fluffy cheek tufts, round tail. `work` (3 s): crouches, then lunges forward headbutting with the horn and recovers. `eat`: nose wiggle and nibbling.
done-when: `node dev/pipi-pets/build-hornrabbit.mjs` exits 0 and `node dev/pipi-pets/check.mjs hornrabbit` exits 0.

## s10 Mole 큰발톱 두더지
files: dev/pipi-pets/build-mole.mjs, dev/pipi-facilities/assets/pipifacilities/pets/mole.bbmodel, dev/pipi-pets/out/mole.png
needs: s0
difficulty: medium
do: Hitbox 0.75 wide x 0.65 tall. A round velvety mole: dark gray-brown #4A3A42 (light #65525C), a pink star nose #F38BA8 at the front of a short snout, tiny happy closed eyes (1px dark curved lines instead of 2x2 eyes) plus blush, huge pale claws #F0E0C8 on the front paws built as separate claw cubes, a small yellow miner hard hat #F2C14E with a lamp cube. `work`: digging — front claws alternately swipe down and outward. `eat`: snout dips and wiggles.
done-when: `node dev/pipi-pets/build-mole.mjs` exits 0 and `node dev/pipi-pets/check.mjs mole` exits 0.

## s11 Thunder sheep 전기 양
files: dev/pipi-pets/build-thundersheep.mjs, dev/pipi-facilities/assets/pipifacilities/pets/thundersheep.bbmodel, dev/pipi-pets/out/thundersheep.png
needs: s0
difficulty: medium
do: Hitbox 0.8 wide x 0.85 tall. A fluffy electric sheep: cloud-like wool body from several overlapping puff cubes #F4F4FA with a faint pale yellow light #FFF6C8 on tops, face and legs dark slate #3B3F5C, a yellow zigzag lightning tuft #FFD93B on top of the head, small curled ears. Yellow bolt cubes in a group `spark_work`. `work`: curls up (head and legs scale toward 0 over 0.75 s), rolls forward with root pitch 1440° over about 3 s, then uncurls; bolts flicker. `eat`: head dips grazing.
done-when: `node dev/pipi-pets/build-thundersheep.mjs` exits 0 and `node dev/pipi-pets/check.mjs thundersheep` exits 0.

## s12 Moon bear 아기 반달가슴곰
files: dev/pipi-pets/build-moonbear.mjs, dev/pipi-facilities/assets/pipifacilities/pets/moonbear.bbmodel, dev/pipi-pets/out/moonbear.png
needs: s0
difficulty: medium
do: Hitbox 0.7 wide x 0.85 tall. A baby Asian black bear: black fur #26232A (light #3C3842), a white crescent V on the chest #F2F0E8, round ears, tan muzzle #C9A27A with a dark nose, chubby round body. `work` (1.2 s): lifts both front paws high (about 110°) and brings them down like hammering, head following. `eat`: head dips licking.
done-when: `node dev/pipi-pets/build-moonbear.mjs` exits 0 and `node dev/pipi-pets/check.mjs moonbear` exits 0.

## s13 Ferret 장난꾸러기 페럿
files: dev/pipi-pets/build-ferret.mjs, dev/pipi-facilities/assets/pipifacilities/pets/ferret.bbmodel, dev/pipi-pets/out/ferret.png
needs: s0
difficulty: medium
do: Hitbox 0.65 wide x 0.7 tall. A mischievous ferret: still a big SD head, but an elongated low body; cream #EFE0C8 with a brown #6E4E36 eye mask, brown legs and long brown tail, small round ears, a cheeky grin. `work`: playful — bounces and spins (root yaw 360° in 0.4 s, then 0.8 s pause). `eat`: head dips quickly several times.
done-when: `node dev/pipi-pets/build-ferret.mjs` exits 0 and `node dev/pipi-pets/check.mjs ferret` exits 0.

## s14 Angel pig 천사 돼지
files: dev/pipi-pets/build-angelpig.mjs, dev/pipi-facilities/assets/pipifacilities/pets/angelpig.bbmodel, dev/pipi-pets/out/angelpig.png
needs: s0
difficulty: medium
do: Hitbox 0.75 wide x 0.95 tall. A floating angel piglet: pink #F7A8B8 with light #FFD0DA, a flat snout disc in darker pink with two nostrils, floppy ears, curly tail, small white feathered wings on the back in bones `wing_l`/`wing_r`, and a gold halo #F2C14E (a thin square ring of 4 cubes) floating above the head in a bone `halo`. In every animation the root floats about 3 px up with a gentle bob and the legs dangle; the halo bobs slightly. `work`: spins 360° yaw while the wings flap fast. `eat`: head dips, wings slow.
done-when: `node dev/pipi-pets/build-angelpig.mjs` exits 0 and `node dev/pipi-pets/check.mjs angelpig` exits 0.
