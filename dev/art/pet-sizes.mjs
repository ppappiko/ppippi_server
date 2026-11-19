// In-game render scale per pet (model size multiplier). Keep in sync with PipiPets.SIZE (hitbox + renderer).
// 2026-11-19: everything enlarged so a pet's body is about one block. Small critters stay a bit smaller, rare/heavy ones bigger.
// imugi keeps 2 (its hitbox is fixed and the mount seat is tuned for it).
export const SIZE = {
  rabbit: .95, sparrow: 1.2, hornrabbit: 1.2, cat: 1.25, mole: 1.3, fox: 1.3, otter: 1.3, ferret: 1.35, cow: 1.35, angelpig: 1.35,
  vampbat: 1.4, redpanda: 1.4, frog: 1.5, raccoon: 1.5, thundersheep: 1.55, sunlizard: 1.7, tortoise: 1.75, imugi: 2, moonbear: 2.1,
};
