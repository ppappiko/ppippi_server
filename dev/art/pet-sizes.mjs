// In-game render scale per pet (model size multiplier). Keep in sync with PipiPets.SIZE (hitbox + renderer).
// 2026-11-20: sized by concept / real-world animal size (a fox is small, a cow is big), recomputed after the
// creature-collector style remodel so each pet keeps its target height (player = 1.8 blocks): scale = target * 16 / model px.
// imugi keeps 2 (its hitbox is fixed and the mount seat is tuned for it).
export const SIZE = {
  // 작은 동물 (≈0.4~0.6 블록)
  sparrow: .43, rabbit: .52, ferret: .53, mole: .45, squirrel: .51, hedgehog: .61, hermitcrab: .62, owl: .63, frog: .71,
  // 중간 (≈0.6~1 블록)
  vampbat: .68, cat: .71, fox: .72, otter: .89, redpanda: .82, beaver: .55, penguin: .74, monkey: .84,
  raccoon: .78, angelpig: 1.06, clockfox: .78, beetle: .83,
  // 큰 동물 (≈1.3~2.4 블록)
  deer: 1.02, sunlizard: 1.27, ostrich: 1.26, cow: 1.38, thundersheep: 1.39, cloudsheep: 1.17, tortoise: 1.03, rhino: 1.6, moonbear: 1.54,
};
