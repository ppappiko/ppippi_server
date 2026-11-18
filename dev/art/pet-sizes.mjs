// In-game render scale per pet (model size multiplier). Keep in sync with PipiPets.SIZE (hitbox + renderer).
// Small/common critters stay small; rare (Lv3) and heavy pets are big. imugi keeps 2 (mount seat is tuned for it).
export const SIZE = {
  sparrow: .6, rabbit: .72, ferret: .75, mole: .78, hornrabbit: .82, vampbat: .85, frog: .88, cat: .88, redpanda: .9,
  fox: .95, otter: 1, angelpig: 1.05, raccoon: 1.15, thundersheep: 1.25, sunlizard: 1.3, tortoise: 1.45, moonbear: 1.7, imugi: 2,
};
