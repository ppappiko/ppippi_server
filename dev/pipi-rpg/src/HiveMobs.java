package dev.pipi.life;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.util.Vector;

/**
 * 통곡의 왕좌 군체 몬스터 (PipiFacilities RaidMobs: custom models, the mod only walks them). This class gives them their attacks:
 *  - runner  굶주린 구울: fast, claws whoever it reaches.
 *  - bomber  부패 구울: slow; next to a player it swells for 1 s and bursts (area). Killed, it still bursts (weaker).
 *  - acolyte 군체 사도: keeps ~10 blocks away, fires a 3-shot burst of small fire bolts from its arm cannon.
 *  - knight  통곡 기사: charges its blue siege cannon and fires a slow orb that explodes (area); bashes players that come close.
 *            Its front armour soaks most damage: hit it from behind or the side.
 *  - wizard  군체 주술사: floats ~12 blocks away and hurls two slowly homing fireballs, one from each hand.
 *  - ogre    비탄의 거인: about three players tall. Close: raises both fists and pounds the ground in front (area + knockback).
 *            Far: charges and fires a fast laser-like orb from the middle of its face (mob.damage.ogre-beam).
 *  - keeper  문지기 (3관문): short fat brute with two swords. Signature attack: leaps at a player with both swords raised and
 *            slams them down together where it lands (area around the landing + knockback).
 *            Holds still while its AI is off (stunned).
 * Damage values: raid.sorrow.mob.damage.<kind> (knight cannon: knight-cannon), front armour: mob.knight-front-armor-pct.
 * Without the PipiFacilities mod on the server it falls back to vanilla mobs (spawn returns null) with vanilla attacks.
 */
final class HiveMobs {
    static final String[] KINDS = {"runner", "bomber", "acolyte", "knight", "wizard", "keeper", "tentacle", "ogre"};

    /** Older kind names of the raid map onto the hive roster. */
    static String canon(String kind) {
        return switch (kind) {
            case "wailer" -> "runner";
            case "howler" -> "acolyte";
            case "servant" -> "wizard";
            default -> kind;
        };
    }
    static boolean modeled(String kind) { for (String k : KINDS) if (k.equals(kind)) return true; return false; }
    static String name(String kind) {
        return switch (kind) {
            case "runner" -> "굶주린 구울";
            case "bomber" -> "부패 구울";
            case "acolyte" -> "군체 사도";
            case "knight" -> "통곡 기사";
            case "wizard" -> "군체 주술사";
            case "keeper" -> "문지기";
            case "tentacle" -> "영혼 촉수";
            case "ogre" -> "비탄의 거인";
            default -> kind;
        };
    }
    /** Vanilla stand-in when the mod is missing. */
    static Class<? extends LivingEntity> fallback(String kind) {
        return switch (kind) {
            case "acolyte" -> Skeleton.class;
            case "wizard" -> Stray.class;
            case "knight", "keeper" -> WitherSkeleton.class;
            case "ogre" -> Ravager.class;
            default -> Zombie.class;
        };
    }

    final class Brain {
        final LivingEntity e;
        final String kind;
        Player target;
        int cd = 20 + ThreadLocalRandom.current().nextInt(30), atk = -1;
        boolean burst;
        Brain(LivingEntity e, String kind) { this.e = e; this.kind = kind; }
    }

    final class Bolt {
        Location pos; Vector vel;
        double homing, splash, dmg, hitR;
        Player aim; LivingEntity src;
        int life;
        Color col; Particle trail; float size;
        Sound boom;
    }

    private final RaidSorrow raid;
    private final World w;
    private final Supplier<List<Player>> alive;
    /** Called when a bomber has burst by itself (the fight removes it as killed). */
    private final Consumer<LivingEntity> blasted;
    private final Map<UUID, Brain> brains = new LinkedHashMap<>();
    private final List<Bolt> bolts = new ArrayList<>();
    private final ThreadLocalRandom rnd = ThreadLocalRandom.current();
    private static boolean warned;

    HiveMobs(RaidSorrow raid, World w, Supplier<List<Player>> alive, Consumer<LivingEntity> blasted) {
        this.raid = raid; this.w = w; this.alive = alive; this.blasted = blasted;
    }

    // ------------------------------------------------------------------ bridge
    /** The modelled mob, or null when PipiFacilities is not on the server (then use fallback(kind)). */
    LivingEntity spawn(Location at, String kind) {
        if (!modeled(kind)) return null;
        try {
            Object world = at.getWorld().getClass().getMethod("getHandle").invoke(at.getWorld());
            Object nms = Class.forName("dev.pipi.facilities.RaidMobs").getMethod("spawn", Object.class, String.class, double.class, double.class, double.class, float.class)
                .invoke(null, world, kind, at.getX(), at.getY(), at.getZ(), at.getYaw());
            LivingEntity e = (LivingEntity) nms.getClass().getMethod("getBukkitEntity").invoke(nms);
            brains.put(e.getUniqueId(), new Brain(e, kind));
            return e;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            if (!warned) { warned = true; raid.plugin.getLogger().warning("군체 몬스터 모델 생성 실패(바닐라 몹으로 대체): PipiFacilities 서버 버전 확인: " + ex); }
            return null;
        }
    }

    private static Object handle(Entity e) throws ReflectiveOperationException { return e.getClass().getMethod("getHandle").invoke(e); }

    boolean has(Entity e) { return e != null && brains.containsKey(e.getUniqueId()); }
    String kindOf(Entity e) { Brain b = e == null ? null : brains.get(e.getUniqueId()); return b == null ? null : b.kind; }

    void target(LivingEntity e, Player p) {
        Brain b = brains.get(e.getUniqueId());
        if (b == null) { if (e instanceof Mob m) m.setTarget(p); return; }
        if (b.target == p) return;
        b.target = p;
        try { Class.forName("dev.pipi.facilities.RaidMobs").getMethod("target", Object.class, Object.class).invoke(null, handle(e), p == null ? null : handle(p)); }
        catch (ReflectiveOperationException | RuntimeException ignored) { }
    }
    Player targetOf(LivingEntity e) { Brain b = brains.get(e.getUniqueId()); return b == null ? null : b.target; }

    private void act(LivingEntity e, int action, int ticks) {
        try { Class.forName("dev.pipi.facilities.RaidMobs").getMethod("act", Object.class, int.class, int.class).invoke(null, handle(e), action, ticks); }
        catch (ReflectiveOperationException | RuntimeException ignored) { }
    }

    /** Damage multiplier for a hit by p (the knight's front armour). */
    double armor(LivingEntity e, Player p) {
        Brain b = brains.get(e.getUniqueId());
        if (b == null || !b.kind.equals("knight")) return 1;
        Vector to = p.getLocation().toVector().subtract(e.getLocation().toVector()).setY(0);
        if (to.lengthSquared() < 1e-4) return 1;
        return facing(e).dot(to.normalize()) > .35 ? Math.max(0, 1 - raid.cd("mob.knight-front-armor-pct") / 100.0) : 1;
    }

    /** A mob died (killed by players): bombers still burst, weaker. */
    void died(LivingEntity e) {
        Brain b = brains.remove(e.getUniqueId());
        if (b != null && b.kind.equals("bomber") && !b.burst) blast(b, raid.cd("mob.damage.bomber") * .5, 3.2);
    }
    void forget(UUID id) { brains.remove(id); }

    void cleanup() { brains.clear(); bolts.clear(); }

    // ------------------------------------------------------------------ loop (every tick)
    void tick() {
        List<Player> on = alive.get();
        for (Brain b : new ArrayList<>(brains.values())) {
            if (!b.e.isValid() || b.e.isDead()) { brains.remove(b.e.getUniqueId()); continue; }
            think(b, on);
        }
        tickBolts(on);
    }

    private void think(Brain b, List<Player> on) {
        if (b.e instanceof Mob mob && !mob.hasAI()) { b.atk = -1; b.burst = false; return; } // stunned
        if (b.cd > 0) b.cd--;
        Player t = b.target;
        boolean ok = t != null && t.isValid() && on.contains(t) && t.getWorld().equals(w);
        double d = ok ? b.e.getLocation().distance(t.getLocation()) : 99;
        if (b.atk >= 0) { b.atk++; attack(b, ok ? t : null, d); return; }
        if (!ok || b.cd > 0) return;
        switch (b.kind) {
            case "runner" -> { if (d < 2.3) start(b, 9); }
            case "bomber" -> { if (d < 2.9) start(b, 40); }
            case "acolyte" -> { if (d < 20 && b.e.hasLineOfSight(t)) start(b, 18); }
            case "knight" -> { if (d < 3) { b.atk = 100; act(b.e, 2, 8); } else if (d < 24 && b.e.hasLineOfSight(t)) start(b, 28); }
            case "wizard" -> { if (d < 24 && b.e.hasLineOfSight(t)) start(b, 24); }
            case "keeper" -> { if (d < 14 && b.e.hasLineOfSight(t)) start(b, 27); }
            case "ogre" -> {
                if (d < 5.5) start(b, 20);
                else if (d < 30 && b.e.hasLineOfSight(t)) { b.atk = 200; act(b.e, 3, 16); w.playSound(b.e.getLocation(), Sound.ENTITY_GUARDIAN_ATTACK, SoundCategory.HOSTILE, 1.6f, .6f); }
            }
            default -> { }
        }
    }

    private void start(Brain b, int animTicks) {
        b.atk = 0;
        act(b.e, 2, animTicks);
        Location l = b.e.getLocation();
        switch (b.kind) {
            case "bomber" -> { w.playSound(l, Sound.ENTITY_CREEPER_PRIMED, SoundCategory.HOSTILE, 1.6f, .55f); w.playSound(l, Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, SoundCategory.HOSTILE, 1.2f, 1.6f); }
            case "acolyte" -> w.playSound(l, Sound.ENTITY_BLAZE_AMBIENT, SoundCategory.HOSTILE, .9f, 1.6f);
            case "knight" -> w.playSound(l, Sound.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.HOSTILE, 1.4f, 1.5f);
            case "wizard" -> w.playSound(l, Sound.ENTITY_EVOKER_PREPARE_ATTACK, SoundCategory.HOSTILE, 1.2f, .8f);
            case "keeper" -> { b.burst = false; w.playSound(l, Sound.ENTITY_RAVAGER_STEP, SoundCategory.HOSTILE, 1.4f, .6f); }
            case "ogre" -> w.playSound(l, Sound.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 1.8f, .55f);
            default -> w.playSound(l, Sound.ENTITY_ZOMBIE_AMBIENT, SoundCategory.HOSTILE, 1f, 1.7f);
        }
    }

    private void done(Brain b, int cd) { b.atk = -1; b.cd = cd; }

    private void attack(Brain b, Player t, double d) {
        int a = b.atk;
        Location l = b.e.getLocation();
        switch (b.kind) {
            case "runner" -> {
                if (a == 3 && t != null && d < 2.8) {
                    hurt(t, raid.cd("mob.damage.runner"), b.e);
                    w.playSound(l, Sound.ENTITY_PHANTOM_BITE, SoundCategory.HOSTILE, 1f, 1.3f);
                    w.spawnParticle(Particle.SWEEP_ATTACK, t.getLocation().add(0, 1, 0), 1, 0, 0, 0, 0);
                }
                if (a >= 9) done(b, 12 + rnd.nextInt(6));
            }
            case "bomber" -> {
                if (a % 3 == 0) w.spawnParticle(Particle.DUST, l.clone().add(0, 1.2, 0), 8, .45, .6, .45, 0, new Particle.DustOptions(Color.fromRGB(212, 255, 82), 1.4f), true);
                if (a % 6 == 0) w.playSound(l, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, SoundCategory.HOSTILE, 1f, .5f + a / 40f);
                if (a >= 20) { blast(b, raid.cd("mob.damage.bomber"), 3.6); blasted.accept(b.e); }
            }
            case "acolyte" -> {
                if (a < 6 && a % 2 == 0) w.spawnParticle(Particle.FLAME, muzzle(b, 1.35, 1.1, .33), 2, .05, .05, .05, .01);
                if ((a == 6 || a == 9 || a == 12) && t != null) {
                    Location from = muzzle(b, 1.35, 1.1, .33);
                    shoot(b, from, aimAt(t, from, rnd.nextDouble(-.4, .4)), .95, 0, 0, raid.cd("mob.damage.acolyte"), .7,
                        Color.fromRGB(255, 138, 42), Particle.FLAME, .9f, Sound.ENTITY_BLAZE_SHOOT);
                }
                if (a >= 18) done(b, 35 + rnd.nextInt(25));
            }
            case "knight" -> {
                if (a >= 100) { // close-range bash
                    if (a == 103 && t != null && d < 3.4) {
                        hurt(t, raid.cd("mob.damage.knight"), b.e);
                        t.setVelocity(t.getLocation().toVector().subtract(l.toVector()).setY(0).normalize().multiply(1.1).setY(.45));
                        w.playSound(l, Sound.ENTITY_IRON_GOLEM_ATTACK, SoundCategory.HOSTILE, 1.3f, .6f);
                    }
                    if (a >= 108) done(b, 26);
                    return;
                }
                if (a < 16 && a % 2 == 0) {
                    Location m = muzzle(b, 1.9, 1.8, .54);
                    w.spawnParticle(Particle.SOUL_FIRE_FLAME, m, 4, .25, .25, .25, .02);
                    w.spawnParticle(Particle.DUST, m, 3, .3, .3, .3, 0, new Particle.DustOptions(Color.fromRGB(70, 180, 255), 1.6f), true);
                }
                if (a == 16 && t != null) {
                    Location from = muzzle(b, 1.9, 1.8, .54);
                    shoot(b, from, aimAt(t, from, 0), .6, 0, 3.2, raid.cd("mob.damage.knight-cannon"), 1.0,
                        Color.fromRGB(70, 180, 255), Particle.SOUL_FIRE_FLAME, 2.2f, Sound.ENTITY_WARDEN_SONIC_BOOM);
                }
                if (a >= 28) done(b, 55 + rnd.nextInt(30));
            }
            case "wizard" -> {
                if (a % 3 == 0) for (int s = -1; s <= 1; s += 2) w.spawnParticle(Particle.FLAME, muzzle(b, 1.75, .7, .34 * s), 1, .08, .08, .08, .01);
                if ((a == 6 || a == 14) && t != null) {
                    Location from = muzzle(b, 1.85, .95, a == 6 ? .34 : -.34);
                    shoot(b, from, aimAt(t, from, 0), .55, .07, 1.8, raid.cd("mob.damage.wizard"), .8,
                        Color.fromRGB(255, 120, 30), Particle.FLAME, 1.6f, Sound.ENTITY_GHAST_SHOOT);
                }
                if (a >= 24) done(b, 45 + rnd.nextInt(25));
            }
            case "keeper" -> { // crouch (0-2), leap at tick 3, both swords slam down on landing (or at tick 24 at the latest)
                if (a == 3) {
                    Location to = t != null ? t.getLocation() : l;
                    Vector h = to.toVector().subtract(l.toVector()).setY(0);
                    double dist = h.length();
                    h = dist < .1 ? new Vector() : h.normalize().multiply(Math.min(1.35, Math.max(0, dist - 1.2) / 9.5));
                    b.e.setVelocity(h.setY(.78));
                    w.playSound(l, Sound.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 1.4f, .8f);
                    w.spawnParticle(Particle.CLOUD, l.clone().add(0, .2, 0), 14, .6, .1, .6, .05);
                }
                if (a > 3 && a < 24 && a % 2 == 0) for (int sd = -1; sd <= 1; sd += 2)
                    w.spawnParticle(Particle.DUST, muzzle(b, 3, -.2, .7 * sd), 2, .1, .3, .1, 0, new Particle.DustOptions(Color.fromRGB(150, 230, 70), 1.2f), true);
                if (a >= 8 && !b.burst && (b.e.isOnGround() || a >= 24)) {
                    b.burst = true; // reused as "slammed" for the keeper
                    Location hit = b.e.getLocation().add(facing(b.e).multiply(1.2));
                    double rr = 3.6;
                    w.spawnParticle(Particle.EXPLOSION, hit, 3, .8, .1, .8, 0);
                    for (int i = 0; i < 24; i++) { double ang = i * Math.PI / 12;
                        w.spawnParticle(Particle.DUST, hit.clone().add(Math.cos(ang) * rr, .2, Math.sin(ang) * rr), 2, .1, .05, .1, 0, new Particle.DustOptions(Color.fromRGB(150, 230, 70), 1.6f), true); }
                    w.playSound(hit, Sound.ENTITY_IRON_GOLEM_ATTACK, SoundCategory.HOSTILE, 1.8f, .5f);
                    w.playSound(hit, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1.2f, 1.2f);
                    for (Player p : alive.get()) {
                        if (!p.getWorld().equals(w)) continue;
                        if (Math.hypot(p.getLocation().getX() - hit.getX(), p.getLocation().getZ() - hit.getZ()) > rr + .4) continue;
                        if (Math.abs(p.getLocation().getY() - hit.getY()) > 2.5) continue;
                        hurt(p, raid.cd("mob.damage.keeper"), b.e);
                        p.setVelocity(p.getLocation().toVector().subtract(hit.toVector()).setY(0).normalize().multiply(1.1).setY(.55));
                    }
                }
                if (a >= 27) { b.burst = false; done(b, 45 + rnd.nextInt(25)); }
            }
            case "ogre" -> {
                if (a >= 200) { // face beam: charge at the face (0-9), fire a fast orb at tick 10
                    int k = a - 200;
                    Location face = muzzle(b, 1.25, .8, 0);
                    if (k < 10 && k % 2 == 0) {
                        w.spawnParticle(Particle.END_ROD, face, 3, .15, .15, .15, .01);
                        w.spawnParticle(Particle.DUST, face, 4, .25, .25, .25, 0, new Particle.DustOptions(Color.fromRGB(205, 165, 255), 1.4f), true);
                    }
                    if (k == 10 && t != null) shoot(b, face, aimAt(t, face, 0), 1.7, 0, 0, raid.cd("mob.damage.ogre-beam"), .9,
                        Color.fromRGB(215, 180, 255), Particle.END_ROD, 1.7f, Sound.ENTITY_SHULKER_SHOOT);
                    if (k >= 16) done(b, 40 + rnd.nextInt(30));
                    return;
                }
                // both fists raised (ticks 0-10), pounded down in front at tick 12
                if (a == 12) {
                    Location hit = l.clone().add(facing(b.e).multiply(3.2));
                    w.spawnParticle(Particle.EXPLOSION, hit, 3, .8, .1, .8, 0);
                    w.spawnParticle(Particle.BLOCK, hit.clone().add(0, .2, 0), 60, 1.6, .2, 1.6, 0, Material.DEEPSLATE.createBlockData(), true);
                    w.playSound(hit, Sound.ENTITY_IRON_GOLEM_DAMAGE, SoundCategory.HOSTILE, 2f, .45f);
                    w.playSound(hit, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1.2f, .9f);
                    for (Player p : alive.get()) {
                        if (!p.getWorld().equals(w)) continue;
                        if (Math.hypot(p.getLocation().getX() - hit.getX(), p.getLocation().getZ() - hit.getZ()) > 3.6) continue;
                        if (Math.abs(p.getLocation().getY() - hit.getY()) > 3) continue;
                        hurt(p, raid.cd("mob.damage.ogre"), b.e);
                        p.setVelocity(p.getLocation().toVector().subtract(l.toVector()).setY(0).normalize().multiply(1.3).setY(.6));
                    }
                }
                if (a >= 20) done(b, 30 + rnd.nextInt(20));
            }
            default -> done(b, 20);
        }
    }

    // ------------------------------------------------------------------ geometry
    private static Vector facing(LivingEntity e) {
        double yaw = Math.toRadians(e.getLocation().getYaw());
        return new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
    }
    /** A point on the mob: height, forward, side (+ = its left hand, where the cannons are). */
    private Location muzzle(Brain b, double up, double fwd, double side) {
        double k = 1;
        var sc = b.e.getAttribute(org.bukkit.attribute.Attribute.GENERIC_SCALE);
        if (sc != null) k = sc.getValue();
        Vector f = facing(b.e), left = new Vector(f.getZ(), 0, -f.getX());
        return b.e.getLocation().add(f.multiply(fwd * k)).add(left.multiply(side * k)).add(0, up * k, 0);
    }
    private Vector aimAt(Player t, Location from, double spread) {
        Vector v = t.getLocation().add(0, 1.1, 0).toVector().subtract(from.toVector());
        if (spread != 0) v.rotateAroundY(spread * .12);
        return v.lengthSquared() < 1e-4 ? new Vector(0, 0, 1) : v.normalize();
    }

    // ------------------------------------------------------------------ projectiles
    private void shoot(Brain b, Location from, Vector dir, double speed, double homing, double splash, double dmg, double hitR,
                       Color col, Particle trail, float size, Sound sound) {
        Bolt o = new Bolt();
        o.pos = from; o.vel = dir.multiply(speed); o.homing = homing; o.splash = splash; o.dmg = dmg; o.hitR = hitR;
        o.aim = b.target; o.src = b.e; o.life = 70; o.col = col; o.trail = trail; o.size = size;
        o.boom = splash > 0 ? Sound.ENTITY_GENERIC_EXPLODE : Sound.ENTITY_BLAZE_HURT;
        bolts.add(o);
        w.playSound(from, sound, SoundCategory.HOSTILE, splash > 2 ? .9f : 1f, splash > 2 ? 1.6f : 1.2f);
    }

    private void tickBolts(List<Player> on) {
        for (Iterator<Bolt> it = bolts.iterator(); it.hasNext(); ) {
            Bolt o = it.next();
            if (o.homing > 0 && o.aim != null && o.aim.isValid() && on.contains(o.aim)) {
                double sp = o.vel.length();
                Vector want = o.aim.getLocation().add(0, 1.1, 0).toVector().subtract(o.pos.toVector());
                if (want.lengthSquared() > 1e-4) o.vel = o.vel.clone().normalize().multiply(1 - o.homing).add(want.normalize().multiply(o.homing)).normalize().multiply(sp);
            }
            boolean hit = false;
            Player victim = null;
            for (int s = 0; s < 3 && !hit; s++) {
                o.pos.add(o.vel.clone().multiply(1 / 3.0));
                if (o.pos.getBlock().getType().isSolid()) { hit = true; break; }
                for (Player p : on) {
                    if (!p.getWorld().equals(w)) continue;
                    if (p.getBoundingBox().expand(o.hitR * .5).contains(o.pos.toVector())) { hit = true; victim = p; break; }
                }
            }
            w.spawnParticle(Particle.DUST, o.pos, 3, .08 * o.size, .08 * o.size, .08 * o.size, 0, new Particle.DustOptions(o.col, o.size), true);
            w.spawnParticle(o.trail, o.pos, o.size > 2 ? 3 : 1, .1, .1, .1, .01);
            if (--o.life <= 0) hit = true;
            if (!hit) continue;
            it.remove();
            impact(o, victim, on);
        }
    }

    private void impact(Bolt o, Player victim, List<Player> on) {
        Location at = o.pos;
        if (o.splash > 0) {
            w.spawnParticle(Particle.EXPLOSION, at, 1, 0, 0, 0, 0);
            w.spawnParticle(Particle.DUST, at, 40, o.splash * .4, .6, o.splash * .4, 0, new Particle.DustOptions(o.col, 1.8f), true);
            w.spawnParticle(o.trail, at, 25, o.splash * .35, .4, o.splash * .35, .05);
            w.playSound(at, o.boom, SoundCategory.HOSTILE, 1.2f, 1.3f);
            for (Player p : on) {
                if (!p.getWorld().equals(w)) continue;
                double dist = p.getLocation().add(0, .9, 0).distance(at);
                if (dist <= o.splash + .6) hurt(p, o.dmg * (dist < o.splash * .5 ? 1 : .6), o.src);
            }
        } else {
            w.spawnParticle(Particle.DUST, at, 10, .2, .2, .2, 0, new Particle.DustOptions(o.col, 1.2f), true);
            w.spawnParticle(Particle.SMOKE, at, 4, .1, .1, .1, .01);
            if (victim != null) { hurt(victim, o.dmg, o.src); w.playSound(at, o.boom, SoundCategory.HOSTILE, .8f, 1.4f); }
        }
    }

    private void blast(Brain b, double dmg, double r) {
        b.burst = true;
        Location at = b.e.getLocation().add(0, 1, 0);
        w.spawnParticle(Particle.EXPLOSION_EMITTER, at, 1, 0, 0, 0, 0);
        w.spawnParticle(Particle.DUST, at, 70, r * .4, .8, r * .4, 0, new Particle.DustOptions(Color.fromRGB(212, 255, 82), 2f), true);
        w.spawnParticle(Particle.SNEEZE, at, 30, r * .3, .5, r * .3, .05);
        w.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1.6f, .8f);
        w.playSound(at, Sound.ENTITY_SLIME_SQUISH, SoundCategory.HOSTILE, 1.6f, .5f);
        for (Player p : alive.get()) {
            if (!p.getWorld().equals(w)) continue;
            double dist = p.getLocation().add(0, .9, 0).distance(at);
            if (dist > r) continue;
            hurt(p, dmg * (dist < r * .5 ? 1 : .6), b.e);
            p.setVelocity(p.getLocation().toVector().subtract(at.toVector()).setY(0).normalize().multiply(.7).setY(.4));
        }
    }

    /** Exact raid damage (RaidSorrow.mobHitsPlayer keeps the amount; defence still applies). */
    private void hurt(Player p, double amount, LivingEntity src) {
        if (amount <= 0 || p.isDead()) return;
        RaidSorrow.forcedDamage = amount;
        try {
            p.setNoDamageTicks(0);
            if (src != null && src.isValid()) p.damage(amount, src); else p.damage(amount);
        } finally { RaidSorrow.forcedDamage = Double.NaN; }
    }
}
