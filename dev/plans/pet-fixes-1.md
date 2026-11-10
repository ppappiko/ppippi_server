# Pet review fixes round 1

Context: dev/plans/vanilla-pets.md (recipe + per-pet specs). These are corrections after reviewing offline previews. Edit only the named build script, then regenerate its outputs by running it. Keep everything not mentioned unchanged.

## s1 Moonbear crescent on the chest
files: dev/pipi-pets/build-moonbear.mjs, dev/pipi-facilities/assets/pipifacilities/pets/moonbear.bbmodel, dev/pipi-pets/out/moonbear.png
difficulty: easy
do: The white crescent currently floats as loose blobs hanging below/in front of the chest. Make it a clean crescent moon ("V"/smile-shaped band, about 6 px wide, 2-3 px tall) lying flat on the bear's chest: either painted into the chest-front face pixel art or as 1 px thin cubes flush against that face (at most 0.25 px proud), centered under the chin, inside the `moon_glow` group. Nothing may stick out below the body or between the front legs.
done-when: `node dev/pipi-pets/build-moonbear.mjs` exits 0 and `node dev/pipi-pets/check.mjs moonbear` exits 0.

## s2 Angel guinea pig body shape
files: dev/pipi-pets/build-angelpig.mjs, dev/pipi-facilities/assets/pipifacilities/pets/angelpig.bbmodel, dev/pipi-pets/out/angelpig.png
difficulty: easy
do: It currently stands on long legs like a dog. A guinea pig is a low round loaf: lower the body so it sits about 1 px above the feet, make the legs tiny stubs (about 2x1.5x2 px, mostly hidden under the body), make the body a longer rounded loaf (a main box plus slightly smaller boxes on top/front/back edges to round the silhouette), and lower the wings/halo with it. Keep colors, halo, wings and animations (adjust heights only).
done-when: `node dev/pipi-pets/build-angelpig.mjs` exits 0 and `node dev/pipi-pets/check.mjs angelpig` exits 0.

## s3 Vampire bat silhouette
files: dev/pipi-pets/build-vampbat.mjs, dev/pipi-facilities/assets/pipifacilities/pets/vampbat.bbmodel, dev/pipi-pets/out/vampbat.png
difficulty: easy
do: It currently stands upright on long legs with rabbit-long ears and fangs dangling far below the chin. Make it read as a bat: a small round furry body hanging under the wings with tiny clawed feet (1-2 px, no long legs), ears about 3 px tall and pointed, fangs 1x2 px just below the upper lip, and a short snout. Wings spread to the sides at shoulder height with visible finger bones. Put the root at least 6 px up in the rest pose too (via the root group's origin/children positions, not only animations), so it hovers even without animation. Keep red glowing eyes and animations (adjust to the new geometry).
done-when: `node dev/pipi-pets/build-vampbat.mjs` exits 0 and `node dev/pipi-pets/check.mjs vampbat` exits 0.

## s4 Pirate otter hat and body
files: dev/pipi-pets/build-otter.mjs, dev/pipi-facilities/assets/pipifacilities/pets/otter.bbmodel, dev/pipi-pets/out/otter.png
difficulty: easy
do: The hat reads as a top hat; make it a tricorne: a low black crown with the brim turned up on three sides (left, right and back flaps angled up, pointed front corner), gold edge trim and the white skull mark on the front flap. Also make the body longer and lower (otter, not dog): body about 2 px longer and 1-2 px lower with shorter legs, and a slightly bigger head.
done-when: `node dev/pipi-pets/build-otter.mjs` exits 0 and `node dev/pipi-pets/check.mjs otter` exits 0.

## s5 Cat claws attached
files: dev/pipi-pets/build-cat.mjs, dev/pipi-facilities/assets/pipifacilities/pets/cat.bbmodel, dev/pipi-pets/out/cat.png
difficulty: easy
do: The pink/gold claws float in front of and below the front paws. Attach them: claws must start at the front face of each front paw at foot level (y 0..1) and extend at most 1.5 px forward, as children of the front leg bones so they move with the legs. Also make the eyelashes clearly visible: 1 px dark lash cubes poking out above and to the outer side of each eye.
done-when: `node dev/pipi-pets/build-cat.mjs` exits 0 and `node dev/pipi-pets/check.mjs cat` exits 0.
