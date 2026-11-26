package dev.pipi.life;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Shared machinery of the 통곡의 왕좌 gates 1-2 (SorrowSwarm, SorrowDecoy).
 *  - 비탄의 가호 rule: holders only hurt blessed (green-glowing, "✦") enemies, everyone else only normal ones.
 *  - Mobs keep a virtual HP pool, stay in their zone (leash) and only chase players of their own zone.
 *  - 통곡 수정: a purple shard inside a translucent membrane shield. Damage only goes in while a holder and a non-holder hit it
 *    together from inside the shield; at 0 HP it breaks. Unshielded (floating) crystals can be hit from anywhere.
 *    A crystal with a timer wipes the party when it runs out (paused while nobody holds the blessing).
 *  - 눈물 성배 range is the glowing ring on the floor: only players standing inside it swap.
 *  - 눈물 성배 / 가호 구체: holder + non-holder hit it together -> everyone in range swaps state.
 *  - 통곡의 장막: an invisible barrier wall on the zone boundaries, shown by a faint shimmer.
 * Rules shared by all gates are raid.sorrow.rules.*, mob stats raid.sorrow.mob.*, gate values raid.sorrow.g<gate>.*.
 */
abstract class SorrowKit implements RaidSorrow.GateFight {
    static final String BLESSED = "pipi_sorrow_blessed", NOAIM = "pipi_noaim", KIND = "pipi_sk_";
    static final Color GREEN = Color.fromRGB(120, 255, 140), CYAN = Color.fromRGB(110, 200, 255), VEIL = Color.fromRGB(190, 255, 215);
    private static final String LORD = "§5[비탄의 망령 군주] §f";
    private static final String TEAM = "pipi_sorrow_bl";

    final RaidSorrow raid;
    final RaidSorrow.Session s;
    final World w;
    final int n, gate;
    final BossBar bar;
    final ThreadLocalRandom rnd = ThreadLocalRandom.current();
    final Map<UUID, Long> hintAt = new HashMap<>();
    final Map<UUID, Mob> mobs = new LinkedHashMap<>();
    final List<Crystal> crystals = new ArrayList<>();
    final List<Bowl> bowls = new ArrayList<>();
    final List<int[]> veil = new ArrayList<>();
    final List<Entity> props = new ArrayList<>();
    SorrowBlessing b;
    final HiveMobs hive;
    long ft;
    boolean over;

    // ------------------------------------------------------------------ parts
    final class Mob {
        final LivingEntity e;
        final String kind, name;
        final boolean blessed;
        final int zone;
        final Location home;
        double hp, max;
        boolean shield;
        Mob(LivingEntity e, String kind, String name, boolean blessed, int zone, Location home, double max) {
            this.e = e; this.kind = kind; this.name = name; this.blessed = blessed; this.zone = zone; this.home = home; this.max = max; this.hp = max;
        }
    }

    final class Crystal {
        final Location at;
        final boolean shield;
        final int zone;
        final Runnable onBreak;
        Shulker hb;
        ItemDisplay fx, shieldFx;
        TextDisplay label;
        long end;
        double hp, max;
        Crystal(Location at, boolean shield, int zone, Runnable onBreak) { this.at = at; this.shield = shield; this.zone = zone; this.onBreak = onBreak; }
    }

    final class Bowl {
        final Location at;
        final String name;
        final boolean orb, once;
        final double range;
        final Consumer<List<Player>> after;
        Shulker hb;
        Display fx, ring;
        TextDisplay label;
        long readyAt;
        Bowl(Location at, String name, boolean orb, double range, boolean once, Consumer<List<Player>> after) {
            this.at = at; this.name = name; this.orb = orb; this.range = range; this.once = once; this.after = after;
        }
        Location centre() { return at.clone().add(0, 1.3, 0); }
    }

    SorrowKit(RaidSorrow raid, RaidSorrow.Session s, World w, int n, int gate, String title) {
        this.raid = raid; this.s = s; this.w = w; this.n = n; this.gate = gate;
        bar = Bukkit.createBossBar(title, BarColor.PURPLE, BarStyle.SEGMENTED_10);
        b = newBlessing();
        hive = new HiveMobs(raid, w, this::alive, e -> { Mob m = mobs.get(e.getUniqueId()); if (m != null) kill(m, false); });
    }

    SorrowBlessing newBlessing() {
        return new SorrowBlessing(new SorrowBlessing.Host() {
            @Override public boolean solo() { return s.solo; }
            @Override public List<Player> alive() { return SorrowKit.this.alive(); }
            @Override public void all(String msg) { SorrowKit.this.all(msg); }
            @Override public long ft() { return ft; }
            @Override public World world() { return w; }
            @Override public Location padSpot() { return SorrowKit.this.padSpot(); }
            @Override public long blessTicks() { return SorrowKit.this.blessTicks(); }
            @Override public long curseTicks() { return SorrowKit.this.curseTicks(); }
            @Override public long syncTicks() { return Math.max(2, ri("sync-window-ticks")); }
            @Override public void started() { SorrowKit.this.onStarted(); }
            @Override public boolean autoPad() { return SorrowKit.this.autoPad(); }
            @Override public int target(int alive) { return SorrowKit.this.holderTarget(alive); }
            @Override public boolean trim() { return SorrowKit.this.trimHolders(); }
        });
    }

    // ------------------------------------------------------------------ gate hooks
    abstract Location padSpot();
    abstract void step();
    abstract String barText();
    double barProgress() { return 0; }
    void onStarted() { }
    boolean autoPad() { return true; }
    int holderTarget(int alive) { return SorrowBlessing.target(alive); }
    boolean trimHolders() { return true; }
    /** Extra text after the blessing state in a player's action bar. */
    String status(Player p) { return ""; }
    void onKill(Mob m) { }
    /** True when the gate handled the hit itself (shields and the like). */
    boolean onMobHit(Mob m, Player p, double dmg) { return false; }
    void onOtherHit(Entity t, Player p, double dmg) { }
    /** True when the gate handled a wipe itself (a gate with checkpoints could override this). */
    boolean onWipe() { return false; }
    void extraCleanup() { }

    // ------------------------------------------------------------------ config / helpers
    int gi(String k) { return raid.ci("g" + gate + "." + k); }
    double gd(String k) { return raid.cd("g" + gate + "." + k); }
    int ri(String k) { return raid.ci("rules." + k); }
    double rd(String k) { return raid.cd("rules." + k); }
    long secs(String gateKey, int min) { return Math.max(min, gi(gateKey)) * 20L; }
    long blessTicks() { return Math.max(10, ri("blessing-seconds")) * 20L; }
    long curseTicks() { return Math.max(2, ri("curse-seconds")) * 20L; }
    double hpMult() { return 1 + Math.max(0, n - 2) * raid.cd("mob.hp-per-extra-player-pct") / 100.0; }

    @Override public BossBar bar() { return bar; }
    @Override public void forget(UUID id) { b.forget(id); }

    @Override public List<Player> alive() {
        List<Player> out = new ArrayList<>();
        for (UUID id : s.members) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline() || p.isDead() || s.dead.contains(id) || !p.getWorld().equals(w) || RaidDeepDark.ghost(p)) continue;
            out.add(p);
        }
        return out;
    }

    void all(String msg) { for (Player p : raid.online(s)) p.sendMessage(msg); }
    void say(String line) {
        all(LORD + line);
        for (Player p : raid.online(s)) p.playSound(p.getLocation(), Sound.ENTITY_VEX_AMBIENT, SoundCategory.HOSTILE, 1f, .5f);
    }
    void titleAll(String t, String sub) { for (Player p : raid.online(s)) p.sendTitle(t, sub, 5, 45, 10); }
    void titleZone(int z, String t, String sub) { for (Player p : raid.online(s)) if (p.getWorld().equals(w) && zoneOf(p.getLocation()) == z) p.sendTitle(t, sub, 5, 45, 10); }
    void soundAll(Sound snd, float vol, float pitch) { for (Player p : raid.online(s)) p.playSound(p.getLocation(), snd, SoundCategory.HOSTILE, vol, pitch); }

    void hint(Player p, String text) {
        Long at = hintAt.get(p.getUniqueId());
        if (at != null && ft - at < 30) return;
        hintAt.put(p.getUniqueId(), ft);
        RaidSorrow.bar(p, text);
    }

    static double flat(Location a, Location c) { return Math.hypot(a.getX() - c.getX(), a.getZ() - c.getZ()); }
    Location hallAt(double deg, double r, double up) { return SorrowMap.hall(w, gate, deg, r, up); }
    /** Zone of a point: -1 in the hub, else 0..2. */
    int zoneOf(Location l) {
        if (SorrowMap.hallDist(gate, l.getX(), l.getZ()) < SorrowMap.HUB_R + 3) return -1;
        return SorrowMap.zoneAt(gate, l.getX(), l.getZ());
    }
    List<Player> playersIn(int zone) { List<Player> o = new ArrayList<>(); for (Player p : alive()) if (zoneOf(p.getLocation()) == zone) o.add(p); return o; }
    String zoneName(int z) { return switch (z) { case 0 -> "북쪽 구역"; case 1 -> "동쪽 구역"; case 2 -> "서쪽 구역"; default -> "중앙"; }; }

    // ------------------------------------------------------------------ mobs
    static org.bukkit.scoreboard.Team team() {
        var sb = Bukkit.getScoreboardManager().getMainScoreboard();
        var t = sb.getTeam(TEAM);
        if (t == null) { t = sb.registerNewTeam(TEAM); t.setColor(ChatColor.GREEN); }
        return t;
    }

    static String kindName(String kind) {
        if (HiveMobs.modeled(kind)) return HiveMobs.name(kind);
        return switch (kind) {
            case "howler" -> "울부짖는 망령";
            case "servant" -> "빛의 시종";
            case "knight" -> "통곡 기사";
            case "ogre" -> "비탄의 거인";
            case "decoy" -> "비탄의 허상";
            default -> "흐느끼는 망령";
        };
    }

    Mob spawnMob(String spawnKind, Location at, boolean blessed, int zone) {
        boolean dropIn = spawnKind.equals("servant");
        String kind = HiveMobs.canon(spawnKind);
        Class<? extends LivingEntity> type = HiveMobs.modeled(kind) ? HiveMobs.fallback(kind) : switch (kind) {
            case "howler" -> Skeleton.class;
            case "servant" -> Stray.class;
            case "knight", "decoy" -> WitherSkeleton.class;
            case "ogre" -> Ravager.class;
            default -> Zombie.class;
        };
        boolean big = kind.equals("knight") || kind.equals("ogre") || kind.equals("decoy");
        String name = (blessed ? "§a✦ " : "§7") + (big ? "§l" : "") + kindName(kind);
        double max = Math.max(1, raid.cd("mob.hp." + kind) * hpMult());
        Location spawnAt = dropIn ? at.clone().add(0, 7, 0) : at;
        LivingEntity modeled = hive.spawn(spawnAt, kind.equals("decoy") ? "keeper" : kind); // 허상 = 문지기 모델
        Consumer<LivingEntity> setup = m -> {
            m.addScoreboardTag(RaidSorrow.MOB);
            m.addScoreboardTag(KIND + kind);
            if (blessed) m.addScoreboardTag(BLESSED);
            m.setPersistent(false);
            m.setRemoveWhenFarAway(false);
            m.setCanPickupItems(false);
            m.setCustomNameVisible(true);
            if (m instanceof Zombie z) z.setAdult();
            var eq = m.getEquipment();
            if (eq != null) {
                eq.setHelmet(null);
                if (kind.equals("knight") && modeled == null) { eq.setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD)); eq.setChestplate(new ItemStack(Material.CHAINMAIL_CHESTPLATE)); }
                if (kind.equals("decoy") && modeled == null) eq.setItemInMainHand(new ItemStack(Material.STONE_AXE));
                eq.setItemInMainHandDropChance(0);
                eq.setChestplateDropChance(0);
            }
            var fr = m.getAttribute(Attribute.GENERIC_FOLLOW_RANGE);
            if (fr != null) fr.setBaseValue(28);
            var kb = m.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE);
            if (kb != null) kb.setBaseValue(big ? 1 : .4);
            var sc = m.getAttribute(Attribute.GENERIC_SCALE);
            if (sc != null) sc.setBaseValue(switch (kind) { case "knight" -> modeled != null ? 1 : 1.3; case "ogre" -> modeled != null ? 2 : 1.25; case "decoy" -> modeled != null ? 1 : 1.7; default -> 1; });
            if (kind.equals("ogre")) { var sp = m.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED); if (sp != null) sp.setBaseValue(.24); }
            if (dropIn) m.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 60, 0, false, false, false));
            if (blessed) m.setGlowing(true);
        };
        LivingEntity e;
        if (modeled != null) { setup.accept(modeled); e = modeled; } else e = w.spawn(spawnAt, type, setup::accept);
        if (blessed) team().addEntry(e.getUniqueId().toString());
        Mob mob = new Mob(e, kind, name, blessed, zone, at.clone(), max);
        mobs.put(e.getUniqueId(), mob);
        nameMob(mob);
        retarget(mob);
        w.spawnParticle(Particle.SOUL, at.clone().add(0, .5, 0), 15, .3, .5, .3, .02, null, true);
        return mob;
    }

    /** A random ordinary swarm mob: mostly 굶주린 구울, then 군체 사도, sometimes a 부패 구울. */
    String trash() { int r = rnd.nextInt(100); return r < 45 ? "runner" : r < 75 ? "acolyte" : "bomber"; }

    void nameMob(Mob m) {
        String tag = m.shield ? " §a[보호막]" : "";
        m.e.setCustomName(m.name + tag + " §c" + RaidSorrow.fmt(Math.ceil(m.hp)));
    }

    void hitMob(Mob m, Player p, double dmg) {
        if (onMobHit(m, p, dmg)) return;
        if (!b.canHurt(p, m.blessed)) {
            w.playSound(m.e.getLocation(), Sound.ITEM_SHIELD_BLOCK, SoundCategory.HOSTILE, .8f, 1.3f);
            hint(p, m.blessed ? "§a✦ 빛나는 적§7은 §a가호자§7만 공격할 수 있습니다" : "§7가호자는 §a✦ 빛나는 적§7만 공격할 수 있습니다");
            return;
        }
        double k = hive.armor(m.e, p);
        if (k < 1) {
            w.playSound(m.e.getLocation(), Sound.ITEM_SHIELD_BLOCK, SoundCategory.HOSTILE, .7f, .7f);
            hint(p, "§7통곡 기사의 §f정면 갑옷§7이 피해를 막습니다 §8· §e등 뒤나 옆을 노리세요");
        }
        damage(m, dmg * k);
    }

    void damage(Mob m, double dmg) {
        double before = m.hp;
        m.hp = Math.max(0, m.hp - dmg);
        if (before - m.hp >= 1) raid.numbers.show(m.e, before - m.hp);
        try { m.e.playHurtAnimation(0); } catch (RuntimeException | LinkageError ignored) { }
        nameMob(m);
        if (m.hp <= 0) kill(m, false);
    }

    void kill(Mob m, boolean silent) {
        if (mobs.remove(m.e.getUniqueId()) == null) return;
        b.hits.remove(m.e.getUniqueId());
        if (m.blessed) try { team().removeEntry(m.e.getUniqueId().toString()); } catch (RuntimeException ignored) { }
        if (silent) hive.forget(m.e.getUniqueId()); else hive.died(m.e);
        if (m.e.isValid()) {
            w.spawnParticle(Particle.SOUL, m.e.getLocation().add(0, 1, 0), silent ? 8 : 25, .3, .6, .3, .03, null, true);
            if (!silent) w.playSound(m.e.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, SoundCategory.HOSTILE, 2f, .8f);
            m.e.remove();
        }
        if (!silent) onKill(m);
    }

    void clearMobs(Predicate<Mob> which) { for (Mob m : new ArrayList<>(mobs.values())) if (which.test(m)) kill(m, true); }
    int count(Predicate<Mob> which) { int c = 0; for (Mob m : mobs.values()) if (which.test(m)) c++; return c; }

    /** Chases the nearest living player of its own zone (hub mobs: anyone), or nobody. */
    void retarget(Mob m) {
        Player best = null; double bd = Double.MAX_VALUE;
        for (Player p : alive()) {
            if (m.zone >= 0 && zoneOf(p.getLocation()) != m.zone) continue;
            double d = p.getLocation().distanceSquared(m.e.getLocation());
            if (d < bd) { bd = d; best = p; }
        }
        hive.target(m.e, best);
    }

    @Override public boolean mayTarget(Entity mob, Player p) {
        Mob m = mobs.get(mob.getUniqueId());
        return m == null || m.zone < 0 || zoneOf(p.getLocation()) == m.zone;
    }

    private void mobTick() {
        if (ft % 20 != 0) return;
        for (Mob m : new ArrayList<>(mobs.values())) {
            if (!m.e.isValid() || m.e.isDead()) { kill(m, false); continue; }
            Location l = m.e.getLocation();
            boolean away = l.getY() < SorrowMap.FLOOR - 4 || m.zone >= 0 && gate != 2 && (zoneOf(l) != m.zone || flat(l, m.home) > 24);
            if (away) { m.e.teleport(m.home); m.e.setFallDistance(0); }
            LivingEntity t = hive.has(m.e) ? hive.targetOf(m.e) : m.e instanceof org.bukkit.entity.Mob ai ? ai.getTarget() : null;
            if (t == null || !t.isValid() || !(t instanceof Player tp) || !alive().contains(tp) || m.zone >= 0 && zoneOf(tp.getLocation()) != m.zone) retarget(m);
        }
    }

    // ------------------------------------------------------------------ 통곡 수정
    Crystal spawnCrystal(Location at, boolean shield, long lifeTicks, int zone, Runnable onBreak) {
        Crystal c = new Crystal(at.clone(), shield, zone, onBreak);
        c.hb = w.spawn(at, Shulker.class, e -> {
            e.addScoreboardTag(RaidSorrow.VESSEL);
            e.addScoreboardTag(NOAIM);
            e.setAI(false);
            e.setSilent(true);
            e.setGravity(false);
            e.setPersistent(false);
            e.setRemoveWhenFarAway(false);
            e.setInvisible(true);
            e.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false, false));
            e.setCustomName("§a통곡 수정");
            e.setCustomNameVisible(false);
            var sc = e.getAttribute(Attribute.GENERIC_SCALE);
            if (sc != null) sc.setBaseValue(1.4);
        });
        c.fx = crystalModel(w, at);
        if (shield) c.shieldFx = shieldBubble(w, at.clone().add(0, 1, 0), (float) rd("shield-range"), 1f);
        c.max = c.hp = Math.max(1, rd("crystal-hp") * hpMult());
        c.label = w.spawn(at.clone().add(0, 2.6, 0), TextDisplay.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(RaidSorrow.FX);
            d.setBillboard(Display.Billboard.CENTER);
            d.setShadowed(true);
        });
        c.end = lifeTicks > 0 ? ft + lifeTicks : -1;
        crystals.add(c);
        w.playSound(at, Sound.BLOCK_AMETHYST_CLUSTER_PLACE, SoundCategory.HOSTILE, 3f, .6f);
        w.spawnParticle(Particle.END_ROD, at.clone().add(0, 1, 0), 30, .4, .8, .4, .05, null, true);
        return c;
    }

    static final Color CRYSTAL = Color.fromRGB(170, 120, 255);

    /** A PipiFacilities prop model (fx_<id>) or null when the mod is missing. */
    static ItemDisplay fxModel(World w, String id, Location at, float scale, int light) {
        Material m = RaidSorrow.fxMaterial(id);
        if (m == null) return null;
        return w.spawn(at, ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(m));
            d.setPersistent(false);
            d.addScoreboardTag(RaidSorrow.FX);
            d.setBrightness(new Display.Brightness(light, light));
            d.setTeleportDuration(2);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale, scale, scale), new Quaternionf()));
        });
    }
    /** The purple 통곡 수정 standing on the floor at at (falls back to an amethyst block). */
    static ItemDisplay crystalModel(World w, Location at) {
        ItemDisplay d = fxModel(w, "sorrow_crystal", at.clone().add(0, .95, 0), 1.3f, 15);
        if (d == null) d = fxModel(w, "crystal", at.clone().add(0, .8, 0), 1.6f, 15);
        if (d == null) d = w.spawn(at.clone().add(0, .8, 0), ItemDisplay.class, x -> { x.setItemStack(new ItemStack(Material.AMETHYST_BLOCK)); x.setPersistent(false); x.addScoreboardTag(RaidSorrow.FX); });
        d.setGlowing(true);
        d.setGlowColorOverride(CRYSTAL);
        return d;
    }
    /** Translucent membrane bubble of the given radius (blocks) around centre; yScale stretches it upward. */
    static ItemDisplay shieldBubble(World w, Location centre, float radius, float yScale) {
        float k = radius * 16f / 14f;
        ItemDisplay d = fxModel(w, "soul_shield", centre, k, 13);
        if (d != null) d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(k, k * yScale, k), new Quaternionf()));
        return d;
    }
    /** Glowing range ring lying on the floor at at. */
    static ItemDisplay floorRing(World w, Location at, float radius) {
        return fxModel(w, "soul_ring", at.clone().add(0, .06, 0), radius * 16f / 14f, 15);
    }

    boolean insideShield(Crystal c, Player p) {
        Location l = p.getLocation();
        return flat(l, c.at) <= rd("shield-range") + .3 && Math.abs(l.getY() - c.at.getY()) < 3.5;
    }

    void hitCrystal(Crystal c, Player p, double dmg) {
        w.playSound(c.at, Sound.BLOCK_AMETHYST_BLOCK_HIT, SoundCategory.HOSTILE, 1.2f, 1.2f);
        if (c.shield && !insideShield(c, p)) { hint(p, "§d통곡 수정§7은 §f보호막 안으로 들어가§7 공격해야 합니다"); return; }
        b.mark(c.hb, p);
        if (b.pair(c.hb) == null) { hint(p, b.holder(p) ? "§7수정은 §f일반 인원§7과 동시에 때려야 피해가 들어갑니다" : "§7수정은 §a가호자§7와 동시에 때려야 피해가 들어갑니다"); return; }
        double before = c.hp;
        c.hp = Math.max(0, c.hp - dmg);
        raid.numbers.show(c.hb, before - c.hp);
        w.spawnParticle(Particle.DUST, c.at.clone().add(0, 1.2, 0), 6, .3, .6, .3, 0, new Particle.DustOptions(CRYSTAL, 1.2f), true);
        if (c.hp > 0) return;
        w.playSound(c.at, Sound.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 3f, .8f);
        w.playSound(c.at, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.HOSTILE, 3f, .6f);
        w.spawnParticle(Particle.BLOCK, c.at.clone().add(0, 1, 0), 60, .5, .8, .5, 0, Material.AMETHYST_BLOCK.createBlockData(), true);
        removeCrystal(c);
        if (c.onBreak != null) c.onBreak.run();
    }

    void removeCrystal(Crystal c) {
        crystals.remove(c);
        if (c.hb != null) b.hits.remove(c.hb.getUniqueId());
        for (Entity e : new Entity[]{c.hb, c.fx, c.shieldFx, c.label}) if (e != null && e.isValid()) e.remove();
    }

    void clearCrystals() { for (Crystal c : new ArrayList<>(crystals)) removeCrystal(c); }

    private void crystalTick() {
        boolean anyHolder = !b.bless.isEmpty();
        for (Crystal c : new ArrayList<>(crystals)) {
            if (c.end >= 0 && !anyHolder) c.end++; // paused while nobody holds the blessing
            long left = c.end < 0 ? -1 : c.end - ft;
            if (ft % 5 == 0 && c.label != null && c.label.isValid())
                c.label.setText("§d통곡 수정 §f" + (int) Math.ceil(c.hp / c.max * 100) + "%" + (c.shield ? " §7[보호막]" : "") + (left < 0 ? "" : "\n" + (left <= 100 ? "§c" : "§f") + (left + 19) / 20 + "초" + (anyHolder ? "" : " §8(가호자 없음)")));
            if (c.fx != null && c.fx.isValid() && ft % 10 == 0) {
                c.fx.setInterpolationDelay(0);
                c.fx.setInterpolationDuration(10);
                Vector3f sc = c.fx.getTransformation().getScale();
                c.fx.setTransformation(new Transformation(new Vector3f(0, (float) Math.sin(ft / 10.0) * .15f, 0), new Quaternionf().rotateY(ft * .03f), sc, new Quaternionf()));
            }
            if (c.shield && c.shieldFx == null && ft % 4 == 0) { // no mod: a particle bubble
                double r = rd("shield-range");
                Particle.DustOptions d = new Particle.DustOptions(CRYSTAL, 1f);
                for (int i = 0; i < 18; i++) {
                    double th = rnd.nextDouble(Math.PI * 2), ph = Math.acos(rnd.nextDouble(-.2, 1));
                    w.spawnParticle(Particle.DUST, c.at.getX() + r * Math.sin(ph) * Math.cos(th), c.at.getY() + .2 + r * Math.cos(ph), c.at.getZ() + r * Math.sin(ph) * Math.sin(th), 1, 0, 0, 0, 0, d, true);
                }
            }
            if (left > 0 && left <= 100 && left % 20 == 0) for (Player p : raid.online(s)) p.playSound(c.at, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1.5f, .6f);
            if (left == 0) {
                removeCrystal(c);
                w.playSound(c.at, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 4f, .5f);
                w.spawnParticle(Particle.EXPLOSION_EMITTER, c.at.clone().add(0, 1, 0), 1, 0, 0, 0, 0, null, true);
                say("통곡이 터졌다. 모두 함께 울어라.");
                wipe("통곡 수정을 제때 깨지 못해 전멸했습니다.");
                return;
            }
        }
    }

    // ------------------------------------------------------------------ 눈물 성배 / 가호 구체
    Bowl spawnBowl(Location at, String name, boolean orb, double range, boolean once, Consumer<List<Player>> after) {
        Bowl o = new Bowl(at.clone(), name, orb, range, once, after);
        o.hb = w.spawn(at, Shulker.class, e -> {
            e.addScoreboardTag(RaidSorrow.VESSEL);
            e.addScoreboardTag(NOAIM);
            e.setAI(false);
            e.setSilent(true);
            e.setGravity(false);
            e.setPersistent(false);
            e.setRemoveWhenFarAway(false);
            e.setInvisible(true);
            e.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false, false));
            e.setCustomName("§b" + name);
            e.setCustomNameVisible(false);
            var sc = e.getAttribute(Attribute.GENERIC_SCALE);
            if (sc != null) sc.setBaseValue(orb ? 2.2 : 1.6);
        });
        if (orb) {
            Material m = RaidSorrow.fxMaterial("orb");
            ItemStack item = new ItemStack(m != null ? m : Material.HEART_OF_THE_SEA);
            o.fx = w.spawn(at.clone().add(0, 1.3, 0), ItemDisplay.class, d -> {
                d.setItemStack(item);
                d.setPersistent(false);
                d.addScoreboardTag(RaidSorrow.FX);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setGlowing(true);
                d.setGlowColorOverride(CYAN);
                d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(2f, 2f, 2f), new Quaternionf()));
            });
        } else if (RaidSorrow.fxMaterial("tear_vessel") instanceof Material vm) {
            // 군체 솥 + 빛나는 구체 모델 (PipiFacilities fx_tear_vessel): feet at model y 0, model centre = entity + 0.5 up
            o.fx = w.spawn(at.clone().add(0, .5, 0), ItemDisplay.class, d -> {
                d.setItemStack(new ItemStack(vm));
                d.setPersistent(false);
                d.addScoreboardTag(RaidSorrow.FX);
                d.setBrightness(new Display.Brightness(13, 13));
            });
        } else {
            BlockData cup = Material.WATER_CAULDRON.createBlockData();
            if (cup instanceof Levelled lv) lv.setLevel(lv.getMaximumLevel());
            o.fx = w.spawn(new Location(w, Math.floor(at.getX()), at.getY(), Math.floor(at.getZ())), BlockDisplay.class, d -> {
                d.setBlock(cup);
                d.setPersistent(false);
                d.addScoreboardTag(RaidSorrow.FX);
                d.setBrightness(new Display.Brightness(13, 13));
                d.setTransformation(new Transformation(new Vector3f(.05f, 0, .05f), new Quaternionf(), new Vector3f(.9f, .9f, .9f), new Quaternionf()));
            });
        }
        if (!orb) o.ring = floorRing(w, at, (float) range);
        o.label = w.spawn(at.clone().add(0, orb ? 3.4 : 2.5, 0), TextDisplay.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(RaidSorrow.FX);
            d.setBillboard(Display.Billboard.CENTER);
            d.setShadowed(true);
            d.setText("§b" + name + "\n§7가호자 + 일반 인원이 함께 때리면\n§7" + (orb ? "범위" : "바닥 고리") + " 안 모두의 가호가 뒤바뀝니다");
        });
        bowls.add(o);
        w.playSound(at, Sound.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.HOSTILE, 2f, .7f);
        return o;
    }

    void hitBowl(Bowl o, Player p) {
        w.playSound(o.at, Sound.BLOCK_AMETHYST_BLOCK_HIT, SoundCategory.HOSTILE, 1f, 1.5f);
        if (ft < o.readyAt) { hint(p, "§7" + o.name + "이(가) 다시 차오르는 중입니다"); return; }
        if (b.pair(o.hb) == null) { hint(p, b.holder(p) ? "§7" + o.name + "은(는) §f일반 인원§7과 함께 때려야 합니다" : "§7" + o.name + "은(는) §a가호자§7와 함께 때려야 합니다"); return; }
        b.hits.remove(o.hb.getUniqueId());
        Location c = o.centre();
        List<Player> near = new ArrayList<>();
        for (Player q : alive()) if (o.orb ? q.getLocation().add(0, 1, 0).distance(c) <= o.range : flat(q.getLocation(), o.at) <= o.range && Math.abs(q.getLocation().getY() - o.at.getY()) < 3) near.add(q);
        b.invert(near, o.name + "!");
        w.playSound(c, Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.HOSTILE, 2f, 1.6f);
        w.spawnParticle(Particle.END_ROD, c, 50, o.range * .4, o.range * .4, o.range * .4, .05, null, true);
        w.spawnParticle(Particle.SOUL_FIRE_FLAME, c, 30, .4, .6, .4, .04, null, true);
        o.readyAt = ft + 40;
        if (o.once) removeBowl(o);
        if (o.after != null) o.after.accept(near);
    }

    void removeBowl(Bowl o) {
        bowls.remove(o);
        if (o.hb != null) b.hits.remove(o.hb.getUniqueId());
        for (Entity e : new Entity[]{o.hb, o.fx, o.ring, o.label}) if (e != null && e.isValid()) e.remove();
    }

    void clearBowls() { for (Bowl o : new ArrayList<>(bowls)) removeBowl(o); }

    private void bowlTick() {
        if (ft % 5 != 0) return;
        for (Bowl o : bowls) {
            Location c = o.centre();
            Particle.DustOptions d = new Particle.DustOptions(CYAN, .9f);
            if (!o.orb) { // shimmer rising from the floor ring
                for (int j = 0; j < 6; j++) { double th = rnd.nextDouble(Math.PI * 2);
                    w.spawnParticle(Particle.END_ROD, o.at.getX() + Math.cos(th) * o.range, o.at.getY() + .15, o.at.getZ() + Math.sin(th) * o.range, 0, 0, .06, 0, 1, null, true); }
                if (o.ring == null) for (int j = 0; j < 24; j++) { double th = j * Math.PI / 12;
                    w.spawnParticle(Particle.DUST, o.at.getX() + Math.cos(th) * o.range, o.at.getY() + .15, o.at.getZ() + Math.sin(th) * o.range, 1, 0, 0, 0, 0, d, true); }
            }
            for (int j = 0; j < (o.orb ? 8 : 0); j++) {
                double th = rnd.nextDouble(Math.PI * 2), ph = Math.acos(rnd.nextDouble(-.3, 1));
                w.spawnParticle(Particle.DUST, c.getX() + o.range * Math.sin(ph) * Math.cos(th), c.getY() + o.range * Math.cos(ph), c.getZ() + o.range * Math.sin(ph) * Math.sin(th), 1, 0, 0, 0, 0, d, true);
            }
            if (!o.orb) w.spawnParticle(Particle.SOUL_FIRE_FLAME, o.at.clone().add(0, 1.35, 0), 2, .35, .3, .35, .01, null, true);
            if (o.orb && o.fx != null && o.fx.isValid() && ft % 20 == 0) {
                o.fx.setInterpolationDelay(0);
                o.fx.setInterpolationDuration(20);
                o.fx.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateY(ft * .03f), new Vector3f(2f, 2f, 2f), new Quaternionf()));
            }
        }
    }

    // ------------------------------------------------------------------ 통곡의 장막
    /** Raises invisible barrier columns (only where there is air) and moves anyone caught inside into their zone. */
    void raiseVeil(List<int[]> cols) {
        BlockData barrier = Material.BARRIER.createBlockData();
        for (int[] c : cols) for (int y = SorrowMap.FLOOR + 1; y <= SorrowMap.FLOOR + SorrowMap.VEIL_H; y++) {
            Block bl = w.getBlockAt(c[0], y, c[1]);
            if (!bl.getType().isAir()) continue;
            bl.setBlockData(barrier, false);
            veil.add(new int[]{c[0], y, c[1]});
        }
        for (Player p : raid.online(s)) {
            if (!p.getWorld().equals(w)) continue;
            Block feet = p.getLocation().getBlock();
            if (feet.getType() != Material.BARRIER && feet.getRelative(0, 1, 0).getType() != Material.BARRIER) continue;
            Location l = p.getLocation();
            int z = SorrowMap.zoneAt(gate, l.getX(), l.getZ());
            double r = Math.max(SorrowMap.HUB_R + 3, SorrowMap.hallDist(gate, l.getX(), l.getZ()));
            Location to = hallAt(SorrowMap.ZONE[z], Math.min(r, SorrowMap.HALL_R - 4), .1);
            to.setYaw(l.getYaw());
            p.teleport(to);
        }
        soundAll(Sound.BLOCK_BEACON_ACTIVATE, 1f, .6f);
    }

    /** Takes down the veil columns accepted by the filter (all when null). */
    void lowerVeil(Predicate<int[]> which) {
        BlockData air = Material.AIR.createBlockData();
        for (Iterator<int[]> it = veil.iterator(); it.hasNext(); ) {
            int[] c = it.next();
            if (which != null && !which.test(c)) continue;
            Block bl = w.getBlockAt(c[0], c[1], c[2]);
            if (bl.getType() == Material.BARRIER) bl.setBlockData(air, false);
            it.remove();
        }
    }

    /** Clears barrier blocks left on every boundary line of this hall (a crash mid-fight). */
    void scrubVeil() {
        BlockData air = Material.AIR.createBlockData();
        for (int[] c : SorrowMap.veilColumns(gate, true, 7)) for (int y = SorrowMap.FLOOR + 1; y <= SorrowMap.FLOOR + SorrowMap.VEIL_H; y++) {
            Block bl = w.getBlockAt(c[0], y, c[1]);
            if (bl.getType() == Material.BARRIER) bl.setBlockData(air, false);
        }
    }

    private void veilTick() {
        if (veil.isEmpty() || ft % 4 != 0) return;
        List<Player> on = raid.online(s);
        Particle.DustOptions d = new Particle.DustOptions(VEIL, .8f);
        int step = Math.max(1, veil.size() / 90);
        for (int i = (int) (ft / 4 % step); i < veil.size(); i += step) {
            int[] c = veil.get(i);
            if (c[1] > SorrowMap.FLOOR + 7) continue;
            boolean seen = false;
            for (Player p : on) if (Math.abs(p.getLocation().getX() - c[0]) < 26 && Math.abs(p.getLocation().getZ() - c[2]) < 26) { seen = true; break; }
            if (!seen) continue;
            w.spawnParticle(Particle.DUST, c[0] + rnd.nextDouble(), c[1] + rnd.nextDouble(), c[2] + rnd.nextDouble(), 1, 0, 0, 0, 0, d, false);
        }
    }

    // ------------------------------------------------------------------ hits
    @Override public void hit(Entity t, Player p, double dmg) {
        if (over) return;
        UUID id = t.getUniqueId();
        for (Crystal c : new ArrayList<>(crystals)) if (c.hb != null && c.hb.getUniqueId().equals(id)) { hitCrystal(c, p, dmg); return; }
        b.mark(t, p);
        for (Bowl o : new ArrayList<>(bowls)) if (o.hb != null && o.hb.getUniqueId().equals(id)) { hitBowl(o, p); return; }
        Mob m = mobs.get(id);
        if (m != null) { hitMob(m, p, dmg); return; }
        onOtherHit(t, p, dmg);
    }

    // ------------------------------------------------------------------ loop
    boolean live() { return !over && raid.current(s, this); }

    @Override public void tick() {
        ft++;
        if (over) return;
        List<Player> alive = alive();
        if (alive.isEmpty()) { if (!onWipe()) wipe("파티가 전멸했습니다."); return; }
        if (ft > Math.max(1, gi("time-limit-minutes")) * 1200L) { wipe("제한 시간 초과"); return; }
        for (Player p : raid.online(s)) if (!bar.getPlayers().contains(p)) bar.addPlayer(p);
        b.tick(alive);
        if (!live()) return;
        for (Player p : alive) bounds(p);
        step();
        if (!live()) return;
        mobTick();
        hive.tick();
        if (!live()) return;
        crystalTick();
        if (!live()) return;
        bowlTick();
        veilTick();
        if (ft % 10 == 0) hud(alive);
    }

    /** Hall gates: back to the zone when someone falls out or slips past the wall. */
    void bounds(Player p) {
        Location l = p.getLocation();
        if (l.getY() >= SorrowMap.FLOOR - 3 && SorrowMap.hallDist(gate, l.getX(), l.getZ()) <= SorrowMap.HALL_R + 1) return;
        int z = SorrowMap.zoneAt(gate, l.getX(), l.getZ());
        p.teleport(hallAt(SorrowMap.ZONE[z], SorrowMap.ZONE_R, .1));
        p.setFallDistance(0);
    }

    void hud(List<Player> alive) {
        for (Player p : alive) {
            if (b.onPad(p)) continue;
            Long hinted = hintAt.get(p.getUniqueId());
            if (hinted != null && ft - hinted < 30) continue;
            String extra = status(p);
            String tail = extra.isEmpty() ? "" : " §8· " + extra;
            Long end = b.bless.get(p.getUniqueId());
            long padIn = b.padIn();
            if (end == null) RaidSorrow.bar(p, "§7가호 없음 §8· §7가호자 §a" + b.holders()
                + (b.padUp() ? " §8· §a가호 장판에 올라가세요" : padIn >= 0 ? " §8· §e가호 장판 " + (padIn + 19) / 20 + "초 뒤" : "") + tail);
            else {
                long left = end - ft;
                if (left > 0) RaidSorrow.bar(p, "§a✦ 비탄의 가호 §f" + (left + 19) / 20 + "초" + tail);
                else RaidSorrow.bar(p, "§5☠ 저주 §f" + (curseTicks() + left + 19) / 20 + "초 §8· §d성배·구체에서 가호를 바꾸세요!");
            }
        }
        bar.setTitle("§d" + gate + "관문 §8· §f" + barText());
        bar.setProgress(Math.clamp(barProgress(), 0, 1));
    }

    void wipe(String reason) {
        if (over) return;
        over = true;
        raid.fail(s, reason);
    }

    void win(String title, String sub) {
        if (over) return;
        over = true;
        clearMobs(m -> true);
        clearCrystals();
        clearBowls();
        lowerVeil(null);
        bar.setProgress(1);
        bar.setTitle("§a" + gate + "관문 돌파!");
        for (Player p : raid.online(s)) {
            p.sendTitle(title, sub, 5, 80, 20);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
        b.cleanup();
        raid.cleared(s, gate);
    }

    @Override public void cleanup() {
        over = true;
        try { bar.removeAll(); } catch (RuntimeException ignored) { }
        clearMobs(m -> true);
        hive.cleanup();
        clearCrystals();
        clearBowls();
        for (Entity e : props) if (e != null && e.isValid()) e.remove();
        props.clear();
        lowerVeil(null);
        try { extraCleanup(); } catch (RuntimeException ex) { raid.plugin.getLogger().warning("sorrow cleanup: " + ex); }
        for (UUID id : s.members) { Player p = Bukkit.getPlayer(id); if (p != null) p.setGlowing(false); }
        b.cleanup();
    }

    /** A plain display prop (removed on cleanup). */
    ItemDisplay prop(String fxId, Material fallback, Location at, float scale) {
        Material m = RaidSorrow.fxMaterial(fxId);
        ItemStack item = new ItemStack(m != null ? m : fallback);
        ItemDisplay d = w.spawn(at, ItemDisplay.class, e -> {
            e.setItemStack(item);
            e.setPersistent(false);
            e.addScoreboardTag(RaidSorrow.FX);
            e.setBrightness(new Display.Brightness(10, 10));
            e.setViewRange(4f);
            e.setTeleportDuration(3);
            e.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale, scale, scale), new Quaternionf()));
        });
        props.add(d);
        return d;
    }
}
