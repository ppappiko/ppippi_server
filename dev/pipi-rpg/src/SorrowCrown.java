package dev.pipi.life;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 통곡의 왕좌 3관문 「비탄의 왕관」 (after 1~2관문 today). Arena: SorrowMap hall 3, three zones (SorrowMap.ZONE) about 60 blocks apart,
 * split by the invisible 통곡의 장막 on SorrowMap.BOUND while zones fight. Holders only hurt blessed (glowing) mobs, the others only
 * normal ones. 가호 구체 sit inside each zone (right side): holder + non-holder hit it together -> everyone in range swaps.
 * 통곡 수정 are shielded (hit from inside the shield) and come up zone after zone, clockwise.
 *
 * 한 라운드:
 *  - 세 구역에 군주가 하나씩 (진짜 1 + 허상 2, 진짜 자리는 랜덤). 구역마다 가호 마법진이 있다. 마법진에 4초 서 있으면 그 구역이 시작되고
 *    (가호자가 모자라면 그 사람이 가호를 받는다), 라운드의 첫 구역이 시작되면 구역 경계에 초록 벽이 올라온다. 파티는 나뉘어 여러 구역을
 *    동시에 해도 된다.
 *  - 구역: 잡몹 60초 -> 보호막 망령 3회 (보호막은 가호자만 벗기고, 맨몸 망령은 일반 인원만 피해) -> 문지기 (가호자 + 일반 인원이 함께 때려
 *    보호막을 벗기면 기절, 깬 가호자는 가호를 잃음) -> 군주가 초록빛 손으로 화염구, 빛나는 손 3번 적중 -> 도끼로 바로 앞 붉은 원 찍기
 *    (문지기가 원 안이면 죽음, 아니면 보호막 복구).
 *  - 시작된 모든 구역의 문지기가 사라지면 벽이 내려가고 (그 라운드엔 다시 안 올라옴) 딜 페이지: 군주들이 손을 내리고 이마의 보석이 빛난다.
 *    보석에 일정 피해 -> 허상은 사라지고 진짜는 휘청인다. 진짜가 아직 시작 안 한 구역에 있으면 그 구역을 마저 진행한다.
 *  - 진짜: 홀 중앙으로 끌려와 무릎 꿇음(그로기) -> 보석이 빛나는 동안 딜 -> 영혼 촉수 기둥(인원별 2~4개, 무작위 위치)에서 영혼이 보석으로
 *    흘러들어 충전 (가득 차면 전멸, 이때 군주는 공격 불가). 기둥을 모두 부수면 다시 딜: 그때 충전 10% 이내 20초 · 50% 15초 · 90% 10초
 *    (g3.pillar.*) -> 다음 라운드 (새 허상 2 + 진짜 1).
 * 가호 이동은 경계 가운데의 가호 구체에서만: 10타를 맞으면 (가호자가 한 번은 때려야 함) 구체 범위 안 모든 사람의 가호가 뒤바뀐다.
 * 통곡 수정: 싸우는 구역마다 30초마다 구역 안 랜덤한 곳에 생기고, 가호자 + 일반 인원이 함께 때려야 깨진다. 30초 안에 못 깨면 전멸.
 * 모든 수치는 raid.sorrow.g2.*.
 */
final class SorrowCrown implements RaidSorrow.GateFight {
    private static final String LORD = "§5[비탄의 망령 군주] §f";
    private static final double FIG_R = 46, PAD_R = 26, AXE_R = 39, AXE_SIZE = 2.8, GATE_R = 30, PAD_SIZE = 2.2, ORB_RANGE = 4.5;
    private static final Color GREEN = Color.fromRGB(120, 255, 140);
    private enum ZStep { IDLE, MOBS, SHIELD, GATE, DONE, REVEAL, GONE }
    private enum Page { ROUND, STAGGER, DPS1, HANDS, DPS2, DONE }

    private final RaidSorrow raid;
    private final RaidSorrow.Session s;
    private final World w;
    private final int n;
    private final BossBar bar;
    private final SorrowBlessing b;
    private final HiveMobs hive;
    private final ThreadLocalRandom rnd = ThreadLocalRandom.current();
    private final float k; // lord scale

    private long ft, pageAt, pageEnd;
    private Page page = Page.ROUND;
    private int roundNo;
    private final Zone[] zones = new Zone[3];
    private Zone realZone;                      // zone whose real lord staggered (DPS page)
    private final Map<UUID, Object[]> parts = new HashMap<>(); // hitbox uuid -> {Zone, "body"|"l"|"r"}
    private double lordHp, lordMax, gemMax, handMax;
    private final double[] handHp = new double[2];
    /** 영혼 촉수 기둥 (손 페이지): souls stream from each standing pillar into the gem; full charge wipes the party. */
    private static final class Pillar { LivingEntity e; double hp, max; }
    private final List<Pillar> pillars = new ArrayList<>();
    private int pillarTotal;
    private double charge;
    private boolean enraged, wallsUp, wallsOpened, begun;
    private final Map<UUID, Long> hintAt = new HashMap<>();
    private final List<int[]> wallPlaced = new ArrayList<>();
    private final List<Fireball> fireballs = new ArrayList<>();
    private final Orb[] orbs = new Orb[3];

    private final class Zone {
        final int i;
        final double ang;
        ZStep step = ZStep.IDLE;
        long stepAt;
        // lord figure
        boolean real;
        ItemDisplay body, hl, hr, gem;
        Shulker hb, hbl, hbr;
        TextDisplay label;
        final double[] lift = new double[2]; // 0 = rest, 1 = at the gem
        Location feet;
        float yaw;
        double gemHp;
        /** The revealed real lord, pulled into the hall centre (reachable for melee); groggy while its gem shines. */
        boolean atCentre;
        // start circle
        int padFill;
        ItemDisplay padFx;
        // mobs
        final List<LivingEntity> mobs = new ArrayList<>();
        final Map<UUID, double[]> hp = new HashMap<>();
        final Map<UUID, String> names = new HashMap<>();
        final Set<UUID> round = new HashSet<>(), shielded = new HashSet<>(), blessed = new HashSet<>();
        int shieldRound;
        long nextRound = -1, nextMob;
        // 문지기 + 도끼
        LivingEntity keeper;
        boolean keeperShield = true;
        long keeperStunEnd = -1, axeAt = -1, nextBall;
        int throwSide = -1, handHits;
        ItemDisplay axe;
        // 통곡 수정
        Shulker crystal;
        ItemDisplay crystalFx, crystalShield, keeperBubble;
        double crystalHp, crystalMax;
        BlockDisplay crystalBlock;
        TextDisplay crystalLabel;
        long crystalDue = -1, crystalEnd;
        Zone(int i) { this.i = i; this.ang = SorrowMap.ZONE[i]; }
        boolean fighting() { return step == ZStep.MOBS || step == ZStep.SHIELD || step == ZStep.GATE; }
        boolean hasFigure() { return body != null || hb != null; }
        List<Entity> figure() { List<Entity> o = new ArrayList<>(); for (Entity e : new Entity[]{body, hl, hr, gem, hb, hbl, hbr, label}) if (e != null) o.add(e); return o; }
        String name() { return (i + 1) + "구역"; }
    }

    private static final class Fireball { Location at; Vector v; int life; }

    private final class Orb {
        final Location c;
        Shulker hb;
        ItemDisplay fx;
        TextDisplay label;
        long readyAt;
        Orb(int i) { c = SorrowMap.hall(w, 3, SorrowMap.ZONE[i] + 24, 27, 0); } // inside zone i, on its right side
    }

    SorrowCrown(RaidSorrow raid, RaidSorrow.Session s, World w, int n) {
        this.raid = raid; this.s = s; this.w = w; this.n = n;
        this.k = (float) Math.clamp(raid.cd("g3.lord-scale"), .5, 6);
        bar = Bukkit.createBossBar("§d통곡의 왕좌 · 3관문", BarColor.PURPLE, BarStyle.SEGMENTED_10);
        for (int i = 0; i < 3; i++) zones[i] = new Zone(i);
        hive = new HiveMobs(raid, w, this::alive, e -> { for (Zone z : zones) if (z.mobs.contains(e)) { killMob(z, e); break; } });
        b = new SorrowBlessing(new SorrowBlessing.Host() {
            @Override public boolean solo() { return s.solo; }
            @Override public List<Player> alive() { return SorrowCrown.this.alive(); }
            @Override public void all(String msg) { SorrowCrown.this.all(msg); }
            @Override public long ft() { return ft; }
            @Override public World world() { return w; }
            @Override public Location padSpot() { return refillSpot(); }
            @Override public long blessTicks() { return Math.max(10, raid.ci(enraged ? "g3.enrage-blessing-seconds" : "g3.blessing-seconds")) * 20L; }
            @Override public long curseTicks() { return Math.max(3, raid.ci("g3.curse-seconds")) * 20L; }
            @Override public long syncTicks() { return raid.ci("g3.sync-window-ticks"); }
            @Override public void started() { begun = true; say("가호를 쥐었는가. 셋 중 진짜 나를 찾아보아라."); }
            @Override public boolean autoPad() { return begun; }
            @Override public boolean trim() { return false; }
        });
    }

    private double hpMult() { return 1 + Math.max(0, n - 2) * raid.cd("g3.hp.per-extra-player-pct") / 100.0; }

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

    private void all(String msg) { for (Player p : raid.online(s)) p.sendMessage(msg); }
    private Location centre() { return SorrowMap.hall(w, 3, 0, 0, 1); }
    private void say(String line) { all(LORD + line); w.playSound(centre(), Sound.ENTITY_VEX_AMBIENT, SoundCategory.HOSTILE, 3f, .5f); }
    private void titleAll(String t, String sub) { for (Player p : raid.online(s)) p.sendTitle(t, sub, 5, 45, 10); }
    private void titleZone(Zone z, String t, String sub) { for (Player p : raid.online(s)) if (p.getWorld().equals(w) && zoneOf(p) == z) p.sendTitle(t, sub, 5, 45, 10); }
    private Location at(double ang, double r, double up) { return SorrowMap.hall(w, 3, ang, r, up); }
    private long secs(String key, int min) { return Math.max(min, raid.ci(key)) * 20L; }
    private Zone zoneOf(Entity e) { Location l = e.getLocation(); return zones[SorrowMap.zoneAt(4, l.getX(), l.getZ())]; }
    private static double flat(Location a, Location c) { return Math.hypot(a.getX() - c.getX(), a.getZ() - c.getZ()); }
    private static Location yaw(Location l, float yaw) { l.setYaw(yaw); l.setPitch(0); return l; }

    private void hint(Player p, String text) {
        Long at = hintAt.get(p.getUniqueId());
        if (at != null && ft - at < 30) return;
        hintAt.put(p.getUniqueId(), ft);
        RaidSorrow.bar(p, text);
    }

    /** Refill pad (fewer holders than the target): in a zone that has players but no holder, else where the first player is. */
    private Location refillSpot() {
        List<Player> al = alive();
        for (Zone z : zones) {
            boolean people = false, holder = false;
            for (Player p : al) if (zoneOf(p) == z) { people = true; if (b.holder(p)) holder = true; }
            if (people && !holder) return at(z.ang + 35, 11, 0);
        }
        Zone z = al.isEmpty() ? zones[0] : zoneOf(al.get(0));
        return at(z.ang + 35, 11, 0);
    }

    // ================================================================ start / rounds
    @Override public Location spawn(int i, int nn) { return SorrowMap.hallSpawn(w, 4, i, nn); }

    @Override public boolean mayTarget(Entity mob, Player p) {
        for (Zone z : zones) if (z.mobs.contains(mob) || mob.equals(z.keeper)) return zoneOf(p) == z;
        return true;
    }

    @Override public void begin(List<Player> on) {
        for (Player p : on) bar.addPlayer(p);
        lordMax = Math.max(1, raid.cd("g3.hp.lord") * hpMult());
        lordHp = lordMax;
        gemMax = Math.max(1, raid.cd("g3.hp.gem") * hpMult());
        handMax = Math.max(1, raid.cd("g3.hp.hand") * hpMult());
        clearWallGlass();
        for (int i = 0; i < 3; i++) spawnOrb(i);
        newRound();
        say("왕관 앞까지 왔구나. 셋 중 누가 진짜 나인지 알겠느냐?");
        all("§7세 구역에 군주가 하나씩 있고 §f진짜는 하나§7입니다. 구역의 §a가호 마법진§7에 §f4초§7 서 있으면 그 구역이 시작됩니다.");
        all("§7파티를 나눠 여러 구역을 함께 진행해도 됩니다. 첫 구역이 시작되면 구역 사이에 §f통곡의 장막§7이 내려옵니다.");
        all("§7가호자는 §a✦ 빛나는 적§7만, 나머지는 §f일반 적§7만 공격할 수 있습니다. 가호는 구역마다 있는 §b가호 구체§7를 가호자와 일반 인원이 함께 쳐서 바꿉니다.");
        all("§a[보호막] 통곡 수정§7은 보호막 안에서 §f가호자 + 일반 인원§7이 함께 쳐야 깨집니다. 제때 못 깨면 전멸합니다.");
        titleAll("§a가호 마법진", "§7싸울 구역의 초록 마법진에 4초 서 있으세요");
    }

    private void newRound() {
        removePillars();
        roundNo++;
        page = Page.ROUND;
        realZone = null;
        lowerWalls(false);
        wallsOpened = false;
        int real = rnd.nextInt(3);
        for (Zone z : zones) {
            clearZone(z);
            removeFigure(z);
            z.real = z.i == real;
            z.step = ZStep.IDLE;
            z.stepAt = ft;
            z.lift[0] = z.lift[1] = 0;
            spawnFigure(z);
            spawnPad(z);
        }
        if (roundNo > 1) {
            say("다시 흩어져라, 나의 허상들아.");
            titleAll("§5" + roundNo + "라운드", "§7허상 2 + 진짜 1이 다시 나타났습니다 · 구역 마법진에 서세요");
        }
    }

    private void spawnFigure(Zone z) {
        z.atCentre = false;
        z.feet = at(z.ang, FIG_R, 0);
        z.body = part("sorrow_body", z.feet, 2 * k);
        z.hl = part("sorrow_hand_l", z.feet, k);
        z.hr = part("sorrow_hand_r", z.feet, k);
        z.hb = hitbox(z.feet, k * 1.1, "§5비탄의 망령 군주");
        z.hbl = hitbox(z.feet, k * .9, "§7왼손");
        z.hbr = hitbox(z.feet, k * .9, "§7오른손");
        parts.put(z.hb.getUniqueId(), new Object[]{z, "body"});
        parts.put(z.hbl.getUniqueId(), new Object[]{z, "l"});
        parts.put(z.hbr.getUniqueId(), new Object[]{z, "r"});
        z.label = w.spawn(z.feet.clone().add(0, 2.85 * k, 0), TextDisplay.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(RaidSorrow.FX);
            d.setBillboard(Display.Billboard.CENTER);
            d.setShadowed(true);
            d.setSeeThrough(false);
        });
        w.spawnParticle(Particle.SOUL, z.feet.clone().add(0, k, 0), 40, .6, k, .6, .03, null, true);
        label(z);
    }

    private ItemDisplay part(String id, Location at, float scale) {
        Material m = RaidSorrow.fxMaterial(id);
        if (m == null) return null;
        return w.spawn(at, ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(m));
            d.setPersistent(false);
            d.addScoreboardTag(RaidSorrow.FX);
            d.setBrightness(new Display.Brightness(9, 9));
            d.setViewRange(4f);
            d.setTeleportDuration(3);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale, scale, scale), new Quaternionf()));
        });
    }

    private Shulker hitbox(Location at, double scale, String name) {
        return w.spawn(at, Shulker.class, e -> {
            e.addScoreboardTag(RaidSorrow.VESSEL);
            e.setAI(false);
            e.setSilent(true);
            e.setGravity(false);
            e.setPersistent(false);
            e.setRemoveWhenFarAway(false);
            e.setInvisible(true);
            e.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false, false));
            e.setCustomName(name);
            e.setCustomNameVisible(false);
            var sc = e.getAttribute(Attribute.GENERIC_SCALE);
            if (sc != null) sc.setBaseValue(Math.clamp(scale, .2, 8));
        });
    }

    private void spawnPad(Zone z) {
        removePad(z);
        Material m = Material.matchMaterial("PIPIFACILITIES_FX_ULT_SIGIL_GREEN");
        if (m == null) return;
        z.padFx = w.spawn(at(z.ang, PAD_R, .07), ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(m));
            d.setPersistent(false);
            d.addScoreboardTag(RaidSorrow.FX);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(3f);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1.5f, 1.5f, 1.5f), new Quaternionf()));
        });
    }

    private void removePad(Zone z) {
        if (z.padFx != null && z.padFx.isValid()) z.padFx.remove();
        z.padFx = null;
        z.padFill = 0;
    }

    private void removeFigure(Zone z) {
        for (Entity e : z.figure()) { parts.remove(e.getUniqueId()); b.hits.remove(e.getUniqueId()); if (e.isValid()) e.remove(); }
        z.body = z.hl = z.hr = z.gem = null;
        z.hb = z.hbl = z.hbr = null;
        z.label = null;
    }

    private void label(Zone z) {
        if (z.label == null || !z.label.isValid()) return;
        boolean known = z == realZone;
        String head = known ? "§5§l비탄의 망령 군주\n§c" + RaidSorrow.fmt(Math.ceil(lordHp)) + " §7/ " + RaidSorrow.fmt(lordMax) : "§5§l비탄의 망령 군주 §7?";
        String tail = switch (z.step) {
            case IDLE -> "\n§a" + z.name() + " · 가호 마법진에 서세요";
            case REVEAL -> "\n§a보석 " + (int) Math.ceil(z.gemHp / gemMax * 100) + "%";
            default -> "";
        };
        if (known && page == Page.HANDS) tail = "\n§a보석 충전 " + (int) Math.floor(charge * 100) + "% §8· §a남은 기둥 " + pillarsLeft();
        else if (known && (page == Page.DPS1 || page == Page.DPS2)) tail = "\n§e보석이 빛납니다 · 공격하세요";
        else if (known && page == Page.STAGGER) tail = "\n§7휘청이는 중…";
        z.label.setText(head + tail);
    }

    private String pct(double h) { return h <= 0 ? "§8막음" : (int) Math.ceil(h / handMax * 100) + "%"; }

    // ================================================================ walls / orbs
    private void raiseWalls() {
        if (wallsUp || wallsOpened) return;
        wallsUp = true;
        wallPlaced.clear();
        var glass = Material.BARRIER.createBlockData();
        for (int[] c : SorrowMap.veilColumns(4, true, 7)) for (int y = SorrowMap.FLOOR + 1; y <= SorrowMap.FLOOR + SorrowMap.VEIL_H; y++) {
            Block bl = w.getBlockAt(c[0], y, c[1]);
            if (!bl.getType().isAir()) continue;
            bl.setBlockData(glass, false);
            wallPlaced.add(new int[]{c[0], y, c[1]});
        }
        // anyone standing in the wall is moved into the zone they are in
        for (Player p : raid.online(s)) {
            if (!p.getWorld().equals(w)) continue;
            Block feet = p.getLocation().getBlock(), head = feet.getRelative(0, 1, 0);
            if (feet.getType() != Material.BARRIER && head.getType() != Material.BARRIER) continue;
            Zone z = zoneOf(p);
            Location l = p.getLocation();
            double r = Math.max(SorrowMap.HUB_R + 3, SorrowMap.hallDist(3, l.getX(), l.getZ()));
            Location to = at(z.ang, Math.min(r, SorrowMap.HALL_R - 4), .6);
            to.setYaw(l.getYaw());
            p.teleport(to);
        }
        w.playSound(centre(), Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.HOSTILE, 4f, .6f);
        all("§f통곡의 장막§7이 구역 사이를 막았습니다. §7가호 구체는 구역마다 오른쪽에 있습니다.");
    }

    private void lowerWalls(boolean announce) {
        if (!wallsUp) return;
        wallsUp = false;
        var air = Material.AIR.createBlockData();
        for (int[] c : wallPlaced) {
            Block bl = w.getBlockAt(c[0], c[1], c[2]);
            if (bl.getType() == Material.BARRIER) bl.setBlockData(air, false);
        }
        for (int i = 0; i < wallPlaced.size(); i += 12) {
            int[] c = wallPlaced.get(i);
            if (c[1] <= SorrowMap.FLOOR + 4) w.spawnParticle(Particle.END_ROD, c[0] + .5, c[1] + .5, c[2] + .5, 1, .3, .3, .3, .01, null, true);
        }
        wallPlaced.clear();
        for (Player p : raid.online(s)) p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.HOSTILE, 1f, .8f);
        if (announce) all("§a통곡의 장막이 걷혔습니다! §7다른 구역으로 갈 수 있습니다.");
    }

    /** Glass left on the boundary lines (a crash mid-fight) is cleared at the start. */
    private void clearWallGlass() {
        var air = Material.AIR.createBlockData();
        for (int[] c : SorrowMap.veilColumns(4, true, 7)) for (int y = SorrowMap.FLOOR + 1; y <= SorrowMap.FLOOR + SorrowMap.VEIL_H; y++) {
            Block bl = w.getBlockAt(c[0], y, c[1]);
            if (bl.getType() == Material.BARRIER) bl.setBlockData(air, false);
        }
    }

    private void spawnOrb(int i) {
        Orb o = new Orb(i);
        orbs[i] = o;
        o.hb = hitbox(o.c.clone(), 2.6, "§b가호 구체");
        o.hb.addScoreboardTag(SorrowKit.NOAIM);
        Material m = RaidSorrow.fxMaterial("orb");
        if (m != null) o.fx = w.spawn(o.c.clone().add(0, 1.3, 0), ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(m));
            d.setPersistent(false);
            d.addScoreboardTag(RaidSorrow.FX);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setGlowing(true);
            d.setGlowColorOverride(Color.fromRGB(110, 200, 255));
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(2f, 2f, 2f), new Quaternionf()));
        });
        o.label = w.spawn(o.c.clone().add(0, 3.4, 0), TextDisplay.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(RaidSorrow.FX);
            d.setBillboard(Display.Billboard.CENTER);
            d.setShadowed(true);
        });
        orbLabel(o);
    }

    private void orbLabel(Orb o) {
        if (o.label == null || !o.label.isValid()) return;
        o.label.setText("§b가호 구체\n§7가호자 + 일반 인원이 함께 때리면\n§7범위 안 모두의 가호가 뒤바뀝니다");
    }

    private void hitOrb(Orb o, Player p) {
        w.playSound(o.c, Sound.BLOCK_AMETHYST_BLOCK_HIT, SoundCategory.HOSTILE, 1f, 1.5f);
        if (ft < o.readyAt) { hint(p, "§7가호 구체가 다시 차오르는 중입니다"); return; }
        if (b.pair(o.hb) == null) { hint(p, b.holder(p) ? "§7가호 구체는 §f일반 인원§7과 함께 때려야 합니다" : "§7가호 구체는 §a가호자§7와 함께 때려야 합니다"); return; }
        b.hits.remove(o.hb.getUniqueId());
        o.readyAt = ft + 40;
        fireOrb(o);
    }

    /** Everyone inside the orb's range swaps: holders lose the blessing, the others receive it. */
    private void fireOrb(Orb o) {
        Location c = o.c.clone().add(0, 1.3, 0);
        List<Player> near = new ArrayList<>();
        for (Player p : alive()) if (p.getLocation().add(0, 1, 0).distance(c) <= ORB_RANGE) near.add(p);
        b.invert(near, "가호 구체!");
        w.playSound(c, Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.HOSTILE, 2f, 1.6f);
        w.spawnParticle(Particle.END_ROD, c, 60, ORB_RANGE * .5, ORB_RANGE * .5, ORB_RANGE * .5, .05, null, true);
    }

    /** The barrier veil is invisible: a faint shimmer near players shows where it stands. */
    private void veilShimmer() {
        if (!wallsUp || ft % 4 != 0 || wallPlaced.isEmpty()) return;
        List<Player> on = raid.online(s);
        Particle.DustOptions d = new Particle.DustOptions(SorrowKit.VEIL, .8f);
        int step = Math.max(1, wallPlaced.size() / 90);
        for (int i = (int) (ft / 4 % step); i < wallPlaced.size(); i += step) {
            int[] c = wallPlaced.get(i);
            if (c[1] > SorrowMap.FLOOR + 7) continue;
            boolean seen = false;
            for (Player p : on) if (Math.abs(p.getLocation().getX() - c[0]) < 26 && Math.abs(p.getLocation().getZ() - c[2]) < 26) { seen = true; break; }
            if (seen) w.spawnParticle(Particle.DUST, c[0] + rnd.nextDouble(), c[1] + rnd.nextDouble(), c[2] + rnd.nextDouble(), 1, 0, 0, 0, 0, d, false);
        }
    }

    private void orbTick() {
        if (ft % 4 != 0) return;
        Particle.DustOptions d = new Particle.DustOptions(Color.fromRGB(110, 200, 255), .9f);
        for (Orb o : orbs) {
            if (o == null) continue;
            Location c = o.c.clone().add(0, 1.3, 0);
            for (int j = 0; j < 10; j++) { // range shell
                double th = rnd.nextDouble(Math.PI * 2), ph = Math.acos(rnd.nextDouble(-1, 1));
                w.spawnParticle(Particle.DUST, c.getX() + ORB_RANGE * Math.sin(ph) * Math.cos(th), c.getY() + ORB_RANGE * Math.cos(ph), c.getZ() + ORB_RANGE * Math.sin(ph) * Math.sin(th), 1, 0, 0, 0, 0, d, true);
            }
            if (o.fx != null && o.fx.isValid() && ft % 20 == 0) {
                o.fx.setInterpolationDelay(0);
                o.fx.setInterpolationDuration(20);
                o.fx.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateY(ft * .03f), new Vector3f(2f, 2f, 2f), new Quaternionf()));
            }
        }
    }

    // ================================================================ placement
    private void place(Zone z) {
        if (!z.hasFigure()) return;
        double dx = -Math.cos(Math.toRadians(z.ang)), dz = -Math.sin(Math.toRadians(z.ang)); // toward the hall centre
        if (z.atCentre) { dx = -dx; dz = -dz; } // in the centre: facing back toward its own zone
        z.yaw = (float) Math.toDegrees(Math.atan2(-dx, dz)); // model face (-Z) toward the centre
        boolean awake = z.step != ZStep.IDLE;
        // groggy: slumped down to its knees and swaying while the gem shines (and in the short stagger before it)
        boolean stagger = z == realZone && z.atCentre && (page == Page.STAGGER || page == Page.DPS1 || page == Page.DPS2);
        double bob = stagger ? -.24 * k + Math.sin(ft / 5.0) * .1 : awake ? Math.sin(ft / 14.0 + z.ang) * .2 : 0;
        z.feet = at(z.ang, z.atCentre ? 0 : FIG_R, bob);
        float tilt = stagger ? (float) Math.sin(ft / 6.0) * 7f : 0;
        if (z.body != null && z.body.isValid()) z.body.teleport(yaw(z.feet.clone().add(0, k * .5, 0), z.yaw + tilt));
        double ry = Math.toRadians(z.yaw), ux = Math.cos(ry), uz = Math.sin(ry), fx = -Math.sin(ry), fz = Math.cos(ry);
        Location gemAt = gemSpot(z);
        for (int side = 0; side < 2; side++) {
            int sg = side == 0 ? -1 : 1;
            double sway = awake && z.lift[side] == 0 ? Math.sin(ft / 9.0 + side) * .2 : 0;
            Location rest = z.feet.clone().add(ux * sg * 14.6 * k / 16 + fx * -4.5 * k / 16, 20.75 * k / 16 + sway, uz * sg * 14.6 * k / 16 + fz * -4.5 * k / 16);
            Location goal = gemAt.clone().add(ux * sg * .45 * k + fx * -.35 * k, -.15 * k, uz * sg * .45 * k + fz * -.35 * k);
            double t = z.lift[side];
            Location hand = new Location(w, rest.getX() + (goal.getX() - rest.getX()) * t, rest.getY() + (goal.getY() - rest.getY()) * t, rest.getZ() + (goal.getZ() - rest.getZ()) * t);
            ItemDisplay h = side == 0 ? z.hl : z.hr;
            if (h != null && h.isValid()) h.teleport(yaw(hand.clone(), z.yaw));
            Shulker hb = side == 0 ? z.hbl : z.hbr;
            if (ft % 2 == 0 && hb != null && hb.isValid()) hb.teleport(hand.clone().add(0, -.65 * k, 0));
        }
        if (ft % 2 == 0 && z.hb != null && z.hb.isValid()) z.hb.teleport(z.feet.clone().add(0, .35 * k, 0));
        if (z.label != null && z.label.isValid() && ft % 2 == 0) z.label.teleport(z.feet.clone().add(0, 2.85 * k, 0));
        if (z.gem != null && z.gem.isValid() && ft % 2 == 0) z.gem.teleport(gemAt);
        if (ft % 5 == 0) crown(z);
        if (stagger && ft % 4 == 0) w.spawnParticle(Particle.CRIT, z.feet.clone().add(0, 2.3 * k, 0), 5, .5, .1, .5, .05, null, true);
    }

    private Location gemSpot(Zone z) {
        double ry = Math.toRadians(z.yaw), fx = -Math.sin(ry), fz = Math.cos(ry);
        return yaw(z.feet.clone().add(fx * -.61 * k, 2.01 * k, fz * -.61 * k), z.yaw); // the mask forehead (boss_ogre: y 32.2, z -9.8)
    }

    private static double fx(Zone z) { return -Math.sin(Math.toRadians(z.yaw)); }
    private static double fz(Zone z) { return Math.cos(Math.toRadians(z.yaw)); }

    private Location handLoc(Zone z, int side) {
        ItemDisplay h = side == 0 ? z.hl : z.hr;
        return h != null && h.isValid() ? h.getLocation() : z.feet.clone().add(0, 1.3 * k, 0);
    }

    /** Gold crown ring over the head (all three look the same: the real one is only revealed by the gem). */
    private void crown(Zone z) {
        Location c = z.feet.clone().add(fx(z) * -.35 * k, 2.4 * k, fz(z) * -.35 * k);
        Particle.DustOptions gold = new Particle.DustOptions(Color.fromRGB(255, 205, 70), .9f);
        double r = .42 * k, rot = Math.toRadians(z.yaw);
        for (int i = 0; i < 12; i++) {
            double a = rot + i * Math.PI / 6;
            double up = i % 3 == 0 ? .18 * k : 0;
            w.spawnParticle(Particle.DUST, c.getX() + Math.cos(a) * r, c.getY() + up, c.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0, gold, true);
        }
    }

    private void glow(ItemDisplay d, Color c) {
        if (d == null || !d.isValid()) return;
        d.setGlowing(c != null);
        if (c != null) d.setGlowColorOverride(c);
    }

    private void gemOn(Zone z, boolean on) {
        if (!on) { if (z.gem != null && z.gem.isValid()) z.gem.remove(); z.gem = null; return; }
        if (z.gem != null && z.gem.isValid()) return;
        z.gem = w.spawn(gemSpot(z), ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(Material.EMERALD));
            d.setPersistent(false);
            d.addScoreboardTag(RaidSorrow.FX);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setGlowing(true);
            d.setGlowColorOverride(Color.fromRGB(80, 255, 120));
            float sc = .55f * k;
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(sc, sc, sc), new Quaternionf()));
        });
    }

    // ================================================================ zone steps
    private void to(Zone z, ZStep st) { z.step = st; z.stepAt = ft; label(z); }

    private void startZone(Zone z, Player starter) {
        removePad(z);
        if (starter != null && !b.holder(starter) && b.bless.size() < SorrowBlessing.target(alive().size())) b.grant(starter, z.name() + " 마법진에서");
        raiseWalls();
        say(z.name() + "에 왔느냐… 정말 나일까?");
        startMobs(z);
        z.crystalDue = ft + secs("g3.crystal.interval-seconds", 5) + z.i * secs("g3.crystal.interval-seconds", 5) / 3; // staggered clockwise
    }

    private void startMobs(Zone z) {
        clearZone(z);
        to(z, ZStep.MOBS);
        z.nextMob = ft;
        titleZone(z, "§7망령의 무리", "§7" + secs("g3.mobs.seconds", 5) / 20 + "초 동안 몰려오는 망령을 처치하세요");
    }

    private void startShield(Zone z, int r) {
        to(z, ZStep.SHIELD);
        z.shieldRound = r;
        z.nextRound = -1;
        int cnt = Math.max(2, raid.ci("g3.shield.mobs") + playersIn(z)), sh = (cnt + 1) / 2;
        for (int i = 0; i < cnt; i++) {
            LivingEntity e = spawnMob(z, rnd.nextBoolean() ? "runner" : "acolyte");
            z.round.add(e.getUniqueId());
            if (i < sh) { z.shielded.add(e.getUniqueId()); e.setGlowing(true); }
            nameMob(z, e);
        }
        titleZone(z, "§a보호막 망령 " + r + " / " + Math.max(1, raid.ci("g3.shield.rounds")), "§7가호자가 보호막을 벗기고 §f일반 인원§7이 처치하세요");
    }

    private void startGate(Zone z) {
        for (LivingEntity e : new ArrayList<>(z.mobs)) { b.hits.remove(e.getUniqueId()); e.remove(); }
        z.mobs.clear(); z.hp.clear(); z.names.clear(); z.round.clear(); z.shielded.clear();
        to(z, ZStep.GATE);
        spawnKeeper(z);
        titleZone(z, "§5문지기", "§7군주 앞 §c붉은 원§7으로 끌고 와 보호막을 벗기세요");
        for (Player p : raid.online(s)) if (p.getWorld().equals(w) && zoneOf(p) == z) {
            p.sendMessage("§7문지기의 보호막은 §a가호자 + 일반 인원§7이 함께 때려야 깨집니다 (깬 가호자는 가호를 잃음).");
            p.sendMessage("§7문지기가 기절하면 군주가 화염구를 던집니다. §a초록빛으로 빛나는 손§7을 맞히면 도끼로 §c붉은 원§7을 찍습니다.");
        }
    }

    private int playersIn(Zone z) { int c = 0; for (Player p : alive()) if (zoneOf(p) == z) c++; return Math.max(1, c); }

    /** Every started zone has lost its 문지기: walls down, the lords lower their hands and the gems light up. */
    private void checkAllGates() {
        boolean any = false;
        for (Zone z : zones) { if (z.fighting()) return; if (z.step == ZStep.DONE) any = true; }
        if (!any) return;
        lowerWalls(true);
        wallsOpened = true;
        for (Zone z : zones) if (z.step == ZStep.DONE) {
            z.gemHp = gemMax;
            z.lift[0] = z.lift[1] = 0;
            gemOn(z, true);
            to(z, ZStep.REVEAL);
        }
        say("내 문지기들이… 그럼 보석을 깨 보아라. 진짜라면 말이지.");
        titleAll("§a딜 페이지", "§7군주의 이마 보석이 빛납니다 · 보석에 피해를 입혀 진짜를 찾으세요");
        for (Player p : raid.online(s)) p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, .7f);
    }

    private void revealed(Zone z) {
        gemOn(z, false);
        w.playSound(z.feet, Sound.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.HOSTILE, 2f, 1.3f);
        w.spawnParticle(Particle.FLASH, z.feet.clone().add(0, k, 0), 1, 0, 0, 0, 0, null, true);
        if (!z.real) {
            w.spawnParticle(Particle.SOUL, z.feet.clone().add(0, k, 0), 80, .7, k, .7, .06, null, true);
            w.playSound(z.feet, Sound.PARTICLE_SOUL_ESCAPE, SoundCategory.HOSTILE, 3f, .6f);
            removeFigure(z);
            to(z, ZStep.GONE);
            say("…들켰구나. 나는 허상일 뿐.");
            all("§7" + z.name() + "의 군주는 §f허상§7이었습니다.");
            boolean waiting = false;
            for (Zone o : zones) if (o.step == ZStep.REVEAL || o.fighting()) waiting = true;
            if (!waiting) for (Zone o : zones) if (o.step == ZStep.IDLE && o.real) {
                titleAll("§7진짜는 " + o.name() + "에", "§7" + o.name() + " 마법진에서 시작하세요");
                all("§e진짜 군주는 아직 시작하지 않은 §f" + o.name() + "§e에 있습니다. 그 구역의 마법진에서 시작하세요.");
            }
            return;
        }
        // the real one: the other gems fade, the decoys scatter, the DPS page starts
        realZone = z;
        for (Zone o : zones) if (o != z && o.hasFigure()) {
            gemOn(o, false);
            clearZone(o);
            removePad(o);
            w.spawnParticle(Particle.SOUL, o.feet.clone().add(0, k, 0), 60, .7, k, .7, .05, null, true);
            removeFigure(o);
            to(o, ZStep.GONE);
        }
        to(z, ZStep.GONE);
        // pulled into the centre of the hall and brought to its knees, so every class can reach it
        Location from = z.feet.clone();
        w.spawnParticle(Particle.SOUL, from.clone().add(0, k, 0), 90, .8, k, .8, .07, null, true);
        z.atCentre = true;
        Location mid = at(z.ang, 0, 0);
        w.spawnParticle(Particle.FLASH, mid.clone().add(0, k, 0), 1, 0, 0, 0, 0, null, true);
        w.spawnParticle(Particle.EXPLOSION_EMITTER, mid.clone().add(0, .5, 0), 2, .5, .2, .5, 0, null, true);
        w.spawnParticle(Particle.SOUL_FIRE_FLAME, mid.clone().add(0, .3, 0), 120, 3, .3, 3, .08, null, true);
        w.playSound(mid, Sound.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 3f, .6f);
        w.playSound(mid, Sound.ENTITY_IRON_GOLEM_DEATH, SoundCategory.HOSTILE, 3f, .5f);
        page(Page.STAGGER, secs("g3.dps.stagger-seconds", 1));
        say("크윽…! 내 보석이…!");
        titleAll("§a진짜 군주!", "§7군주가 중앙으로 끌려와 쓰러졌습니다 · 모두 중앙으로!");
        all("§a" + z.name() + "§7의 군주가 진짜입니다! 군주가 §f홀 중앙§7으로 끌려와 무릎을 꿇었습니다. 보석이 빛나는 동안 중앙에서 공격하세요.");
    }

    private void page(Page p, long len) { page = p; pageAt = ft; pageEnd = ft + len; if (realZone != null) label(realZone); }

    private void pageTick() {
        Zone z = realZone;
        if (z == null) return;
        switch (page) {
            case STAGGER -> { if (ft >= pageEnd) dps(Page.DPS1); }
            case DPS1, DPS2 -> {
                if (z.gem != null && z.gem.isValid() && ft % 4 == 0) w.spawnParticle(Particle.HAPPY_VILLAGER, z.gem.getLocation(), 3, .2, .2, .2, 0, null, true);
                if (ft >= pageEnd) {
                    gemOn(z, false);
                    if (page == Page.DPS1) startHands(); else { say("…아직이다."); newRound(); }
                }
            }
            case HANDS -> {
                int up = pillarsLeft();
                double rate = up / (double) Math.max(1, pillarTotal) / Math.max(20, raid.cd("g3.pillar.charge-seconds") * 20);
                charge = Math.min(1, charge + rate);
                if (ft % 2 == 0) soulStreams(z);
                if (ft % 3 == 0) for (int i = 0; i < 2; i++)
                    w.spawnParticle(Particle.DUST, handLoc(z, i), 3, .3 * k, .3 * k, .3 * k, 0, new Particle.DustOptions(GREEN, 1.4f), true);
                if (ft % 10 == 0) label(z);
                if (ft % 20 == 0) for (Player pl : raid.online(s)) RaidSorrow.bar(pl, "§a보석 충전 §f" + (int) Math.floor(charge * 100) + "% §8· §a남은 촉수 기둥 §f" + up);
                if (charge >= 1) handsReached();
            }
            default -> { }
        }
    }

    private void dps(Page p) { dps(p, secs("g3.dps.seconds", 3)); }

    private void dps(Page p, long len) {
        gemOn(realZone, true);
        page(p, len);
        titleAll("§e딜 타임!", "§7보석이 빛나는 동안 공격하세요 (" + (pageEnd - ft) / 20 + "초)");
        for (Player pl : raid.online(s)) pl.playSound(pl.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, .9f);
    }

    private void startHands() {
        Zone z = realZone;
        z.lift[0] = z.lift[1] = .5;   // hands raised to the chest, drawing the souls in
        glow(z.hl, GREEN);
        glow(z.hr, GREEN);
        charge = 0;
        spawnPillars(z);
        gemOn(z, true);
        page(Page.HANDS, 20L * 60 * 10);   // ends when the gem is full (wipe) or every pillar is broken
        say("영혼들이여… 보석으로 모여라…");
        titleAll("§a촉수 기둥을 부숴라!", "§7영혼이 보석에 가득 차면 전멸합니다");
        all("§a영혼 촉수 기둥 " + pillarTotal + "개§7가 솟았습니다. 기둥을 모두 부수면 딜 시간! §7(빨리 부술수록 딜 시간이 길어집니다)");
    }

    private int pillarsLeft() { int n = 0; for (Pillar pl : pillars) if (pl.hp > 0) n++; return n; }

    /** 2-3 players: 2 pillars, 4-5: 3, 6: 4, at random spots 12-40 blocks around the lord in the centre. */
    private void spawnPillars(Zone z) {
        removePillars();
        int n = alive().size(), count = n <= 3 ? 2 : n <= 5 ? 3 : 4;
        double max = Math.max(1, raid.cd("g3.hp.pillar") * hpMult());
        List<Location> spots = new ArrayList<>();
        for (int tries = 0; spots.size() < count && tries < 200; tries++) {
            Location l = at(rnd.nextDouble(360), rnd.nextDouble(12, 40), 0);
            boolean ok = true;
            for (Location o : spots) if (o.distance(l) < 10) ok = false;
            if (ok) spots.add(l);
        }
        for (Location l : spots) {
            l.setYaw((float) rnd.nextDouble(360));
            LivingEntity e = hive.spawn(l, "tentacle");
            java.util.function.Consumer<LivingEntity> setup = m -> {
                m.addScoreboardTag(RaidSorrow.VESSEL);
                m.setPersistent(false);
                m.setRemoveWhenFarAway(false);
                m.setSilent(true);
                m.setCustomNameVisible(true);
                if (m instanceof Mob mob) mob.setAI(false);
            };
            if (e != null) setup.accept(e);
            else e = w.spawn(l, Shulker.class, sh -> { setup.accept(sh); sh.setGlowing(true);
                var sc = sh.getAttribute(Attribute.GENERIC_SCALE); if (sc != null) sc.setBaseValue(1.6); });
            Pillar pl = new Pillar();
            pl.e = e; pl.hp = pl.max = max;
            pillars.add(pl);
            namePillar(pl);
            w.spawnParticle(Particle.SCULK_SOUL, l.clone().add(0, 1, 0), 40, .8, 1.5, .8, .05, null, true);
            w.playSound(l, Sound.ENTITY_WARDEN_EMERGE, SoundCategory.HOSTILE, 1.6f, 1.2f);
        }
        pillarTotal = pillars.size();
    }

    private void namePillar(Pillar pl) {
        if (pl.e != null && pl.e.isValid()) pl.e.setCustomName("§a영혼 촉수 §c" + RaidSorrow.fmt(Math.ceil(pl.hp)));
    }

    private void removePillars() {
        for (Pillar pl : pillars) if (pl.e != null) { hive.forget(pl.e.getUniqueId()); if (pl.e.isValid()) pl.e.remove(); }
        pillars.clear();
    }

    /** Souls drift from each standing pillar's tip up into the gem. */
    private void soulStreams(Zone z) {
        Location gem = gemSpot(z);
        for (Pillar pl : pillars) {
            if (pl.hp <= 0 || pl.e == null || !pl.e.isValid()) continue;
            Location from = pl.e.getLocation().add(0, 4, 0);
            Vector d = gem.toVector().subtract(from.toVector());
            for (int j = 0; j < 3; j++) {
                double t = ((ft * .02) + j / 3.0 + pl.max % 1) % 1;
                Location p = from.clone().add(d.clone().multiply(t)).add(0, Math.sin(t * Math.PI) * 2.5, 0);
                w.spawnParticle(j == 0 ? Particle.SOUL : Particle.SCULK_SOUL, p, 1, .1, .1, .1, 0, null, true);
            }
            w.spawnParticle(Particle.DUST, from, 2, .3, .3, .3, 0, new Particle.DustOptions(GREEN, 1.3f), true);
        }
    }

    private void hitPillar(Pillar pl, Player p, double dmg) {
        if (page != Page.HANDS || pl.hp <= 0) return;
        double before = pl.hp;
        pl.hp = Math.max(0, pl.hp - dmg);
        raid.numbers.show(pl.e, before - pl.hp);
        namePillar(pl);
        w.playSound(pl.e.getLocation(), Sound.ENTITY_SLIME_SQUISH, SoundCategory.HOSTILE, 1f, .6f);
        if (pl.hp > 0) return;
        Location l = pl.e.getLocation();
        w.spawnParticle(Particle.SCULK_SOUL, l.clone().add(0, 2, 0), 60, .6, 2, .6, .08, null, true);
        w.spawnParticle(Particle.DUST, l.clone().add(0, 2, 0), 50, .6, 2, .6, 0, new Particle.DustOptions(GREEN, 1.8f), true);
        w.playSound(l, Sound.ENTITY_WARDEN_DEATH, SoundCategory.HOSTILE, 1.4f, 1.4f);
        hive.forget(pl.e.getUniqueId());
        pl.e.remove();
        int left = pillarsLeft();
        all("§a영혼 촉수 기둥을 부쉈습니다! §7남은 기둥 " + left);
        if (realZone != null) label(realZone);
        if (left == 0) pillarsCleared();
    }

    /** Every pillar broken: the faster (lower charge), the longer the next DPS window (10% -> 20 s, 50% -> 15 s, 90% -> 10 s). */
    private void pillarsCleared() {
        Zone z = realZone;
        double c = charge;
        double fast = raid.cd("g3.pillar.dps-fast"), mid = raid.cd("g3.pillar.dps-mid"), slow = raid.cd("g3.pillar.dps-slow");
        double sec = c <= .1 ? fast : c <= .5 ? fast + (mid - fast) * (c - .1) / .4 : c <= .9 ? mid + (slow - mid) * (c - .5) / .4 : slow;
        removePillars();
        z.lift[0] = z.lift[1] = 0;
        glow(z.hl, null);
        glow(z.hr, null);
        w.playSound(z.feet, Sound.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.HOSTILE, 2f, 1.4f);
        say("크윽… 영혼들이…!");
        all("§a기둥을 모두 부쉈습니다! §7보석 충전 " + (int) Math.floor(c * 100) + "% → 딜 시간 §e" + (int) Math.round(sec) + "초");
        dps(Page.DPS2, Math.max(20, Math.round(sec * 20)));
    }

    private void handsReached() {
        Zone z = realZone;
        removePillars();
        page(Page.DONE, 0);
        say("보석이 가득 찼다. 모두 함께 울어라.");
        w.playSound(z.feet, Sound.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 4f, .5f);
        all("§5영혼이 보석에 가득 찼습니다. 파티가 통곡에 삼켜졌습니다.");
        raid.fail(s, "보석에 영혼이 가득 차 전멸했습니다.");
    }

    private void handsBlocked() {
        Zone z = realZone;
        z.lift[0] = z.lift[1] = 0;
        glow(z.hl, null);
        glow(z.hr, null);
        w.playSound(z.feet, Sound.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.HOSTILE, 2f, 1.4f);
        say("크윽… 손이…!");
        dps(Page.DPS2);
    }

    // ================================================================ mobs
    private LivingEntity spawnMob(Zone z, String kind) {
        double a = z.ang + rnd.nextDouble(-30, 30), r = rnd.nextDouble(22, 44);
        Location at = at(a, r, 0);
        String name = (kind.equals("wizard") ? "§a✦ " : "§7") + HiveMobs.name(kind);
        double max = Math.max(1, raid.cd("g3.hp.mob") * hpMult() * (kind.equals("wizard") ? 1.5 : kind.equals("bomber") ? .8 : 1));
        LivingEntity modeled = hive.spawn(at, kind);
        java.util.function.Consumer<LivingEntity> setup = m -> {
            m.addScoreboardTag(RaidSorrow.MOB);
            m.addScoreboardTag(SorrowKit.KIND + kind);
            m.setPersistent(false);
            m.setRemoveWhenFarAway(false);
            m.setCanPickupItems(false);
            m.setCustomNameVisible(true);
            if (m instanceof Zombie zo) zo.setAdult();
            var eq = m.getEquipment();
            if (eq != null) eq.setHelmet(null);
            var fr = m.getAttribute(Attribute.GENERIC_FOLLOW_RANGE);
            if (fr != null) fr.setBaseValue(40);
            var kb = m.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE);
            if (kb != null) kb.setBaseValue(.4);
        };
        LivingEntity e;
        if (modeled != null) { setup.accept(modeled); e = modeled; } else e = w.spawn(at, HiveMobs.fallback(kind), setup::accept);
        z.hp.put(e.getUniqueId(), new double[]{max, max});
        z.names.put(e.getUniqueId(), name);
        z.mobs.add(e);
        nameMob(z, e);
        target(z, e);
        w.spawnParticle(Particle.SOUL, at.clone().add(0, .5, 0), 15, .3, .5, .3, .02, null, true);
        return e;
    }

    /** Mobs go for the players of their own zone. */
    private void target(Zone z, LivingEntity e) {
        List<Player> here = new ArrayList<>();
        for (Player p : alive()) if (zoneOf(p) == z) here.add(p);
        if (!here.isEmpty()) hive.target(e, here.get(rnd.nextInt(here.size())));
    }

    /** Hive mobs pick a new player of their zone when theirs died or left. */
    private void retargetHive(List<Player> alive) {
        for (Zone z : zones) for (LivingEntity e : new ArrayList<>(z.mobs)) {
            if (!hive.has(e)) continue;
            Player t = hive.targetOf(e);
            if (t == null || !alive.contains(t) || zoneOf(t) != z) target(z, e);
        }
        for (Zone z : zones) {
            LivingEntity k = z.keeper;
            if (k == null || !k.isValid() || !z.keeperShield || !hive.has(k)) continue;
            Player t = hive.targetOf(k);
            if (t == null || !alive.contains(t) || zoneOf(t) != z) target(z, k);
        }
    }

    private void nameMob(Zone z, LivingEntity e) {
        double[] h = z.hp.get(e.getUniqueId());
        if (h == null) return;
        String tag = z.shielded.contains(e.getUniqueId()) ? " §a[보호막]" : z.round.contains(e.getUniqueId()) ? " §f[일반 인원]" : "";
        e.setCustomName(z.names.getOrDefault(e.getUniqueId(), "망령") + tag + " §c" + RaidSorrow.fmt(Math.ceil(h[0])));
    }

    private void killMob(Zone z, LivingEntity e) {
        z.mobs.remove(e);
        z.hp.remove(e.getUniqueId());
        z.round.remove(e.getUniqueId());
        z.shielded.remove(e.getUniqueId());
        if (z.blessed.remove(e.getUniqueId())) try { SorrowKit.team().removeEntry(e.getUniqueId().toString()); } catch (RuntimeException ignored) { }
        b.hits.remove(e.getUniqueId());
        hive.died(e);
        w.spawnParticle(Particle.SOUL, e.getLocation().add(0, 1, 0), 25, .3, .6, .3, .03, null, true);
        w.playSound(e.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, SoundCategory.HOSTILE, 2f, .8f);
        e.remove();
    }

    /** Mobs, 문지기, axe and crystal of a zone. */
    private void clearZone(Zone z) {
        for (LivingEntity e : new ArrayList<>(z.mobs)) {
            if (e.isValid()) w.spawnParticle(Particle.SOUL, e.getLocation().add(0, 1, 0), 10, .3, .6, .3, .02, null, true);
            b.hits.remove(e.getUniqueId());
            hive.forget(e.getUniqueId());
            e.remove();
        }
        z.mobs.clear();
        z.hp.clear();
        z.names.clear();
        z.round.clear();
        z.shielded.clear();
        z.blessed.clear();
        removeKeeper(z);
        removeCrystal(z);
        z.crystalDue = -1;
    }

    // ================================================================ 문지기 / 도끼 / 화염구
    private void spawnKeeper(Zone z) {
        Location l = at(z.ang, GATE_R, 0);
        LivingEntity modeled = hive.spawn(l, "keeper"); // 군체 거인 모델 (PipiFacilities), 없으면 위더 스켈레톤
        java.util.function.Consumer<LivingEntity> setup = m -> {
            m.addScoreboardTag(RaidSorrow.MOB);
            m.addScoreboardTag(SorrowKit.KIND + "keeper");
            m.setPersistent(false);
            m.setRemoveWhenFarAway(false);
            m.setCanPickupItems(false);
            m.setCustomNameVisible(true);
            var eq = m.getEquipment();
            if (eq != null && modeled == null) { eq.setHelmet(null); eq.setItemInMainHand(new ItemStack(Material.STONE_AXE)); }
            var fr = m.getAttribute(Attribute.GENERIC_FOLLOW_RANGE);
            if (fr != null) fr.setBaseValue(40);
            var kb = m.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE);
            if (kb != null) kb.setBaseValue(1);
            var sp = m.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
            if (sp != null) sp.setBaseValue(.22);
            var sc = m.getAttribute(Attribute.GENERIC_SCALE);
            if (sc != null) sc.setBaseValue(modeled != null ? 1 : 1.6);
        };
        if (modeled != null) { setup.accept(modeled); z.keeper = modeled; } else z.keeper = w.spawn(l, WitherSkeleton.class, setup::accept);
        z.keeperStunEnd = -1;
        z.throwSide = -1;
        z.axeAt = -1;
        shieldKeeper(z, true);
        w.spawnParticle(Particle.SOUL, l.clone().add(0, 1, 0), 40, .5, 1, .5, .03, null, true);
    }

    private void shieldKeeper(Zone z, boolean up) {
        if (z.keeper == null || !z.keeper.isValid()) return;
        z.keeperShield = up;
        z.keeper.setAI(up);
        if (up && (z.keeperBubble == null || !z.keeperBubble.isValid())) z.keeperBubble = SorrowKit.shieldBubble(w, keeperCentre(z), 1.9f, 1.25f);
        if (!up && z.keeperBubble != null) { if (z.keeperBubble.isValid()) z.keeperBubble.remove(); z.keeperBubble = null; }
        z.keeper.setGlowing(up && z.keeperBubble == null);   // no mod: keep the old glow
        z.keeper.setCustomName("§5§l문지기" + (up ? " §a[보호막]" : " §e[기절]"));
        if (up) target(z, z.keeper);
    }

    private Location keeperCentre(Zone z) { return z.keeper.getLocation().add(0, 1.3, 0); }

    private void removeKeeper(Zone z) {
        if (z.keeperBubble != null) { if (z.keeperBubble.isValid()) z.keeperBubble.remove(); z.keeperBubble = null; }
        if (z.keeper != null) { b.hits.remove(z.keeper.getUniqueId()); hive.forget(z.keeper.getUniqueId()); if (z.keeper.isValid()) z.keeper.remove(); }
        z.keeper = null;
        stopThrow(z);
        if (z.axe != null && z.axe.isValid()) z.axe.remove();
        z.axe = null;
        z.axeAt = -1;
    }

    private void startThrow(Zone z) {
        z.throwSide = rnd.nextInt(2);
        z.handHits = 0;
        z.nextBall = ft + 20;
        z.lift[z.throwSide] = .3;
        glow(z.throwSide == 0 ? z.hl : z.hr, GREEN);
        titleZone(z, "§a초록빛 손!", "§7화염구를 던지는 §a" + (z.throwSide == 0 ? "왼손" : "오른손") + "§7을 " + Math.max(1, raid.ci("g3.gate.hand-hits")) + "번 맞히세요");
    }

    private void stopThrow(Zone z) {
        if (z.throwSide >= 0) { z.lift[z.throwSide] = 0; glow(z.throwSide == 0 ? z.hl : z.hr, null); }
        z.throwSide = -1;
    }

    private void launchBall(Zone z) {
        List<Player> here = new ArrayList<>();
        for (Player p : alive()) if (zoneOf(p) == z) here.add(p);
        if (here.isEmpty() || z.throwSide < 0) return;
        Player t = here.get(rnd.nextInt(here.size()));
        Fireball o = new Fireball();
        o.at = handLoc(z, z.throwSide).clone();
        Vector d = t.getLocation().add(0, 1, 0).toVector().subtract(o.at.toVector());
        double len = Math.max(1, d.length());
        o.v = d.normalize().multiply(.75);
        o.life = (int) (len / .75) + 10;
        fireballs.add(o);
        w.playSound(o.at, Sound.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 1.5f, .8f);
    }

    private void fireballTick() {
        if (fireballs.isEmpty()) return;
        List<Player> al = alive();
        Particle.DustOptions g = new Particle.DustOptions(GREEN, 1.6f);
        double dmg = Math.max(0, raid.cd("g3.damage.fireball"));
        for (Iterator<Fireball> it = fireballs.iterator(); it.hasNext(); ) {
            Fireball o = it.next();
            o.at.add(o.v);
            w.spawnParticle(Particle.SOUL_FIRE_FLAME, o.at, 3, .12, .12, .12, .01, null, true);
            w.spawnParticle(Particle.DUST, o.at, 2, .15, .15, .15, 0, g, true);
            boolean hit = false;
            for (Player p : al) if (p.getLocation().add(0, 1, 0).distanceSquared(o.at) < 1.6) { p.damage(dmg); p.setFireTicks(30); hit = true; break; }
            boolean wall = o.at.getBlock().getType().isSolid();
            if (hit || wall || --o.life <= 0 || o.at.getY() < SorrowMap.FLOOR + .5) {
                w.spawnParticle(Particle.SOUL_FIRE_FLAME, o.at, 12, .3, .3, .3, .04, null, true);
                if (hit) w.playSound(o.at, Sound.ENTITY_GENERIC_BURN, SoundCategory.HOSTILE, 1f, 1.2f);
                it.remove();
            }
        }
    }

    private Location axeSpot(Zone z) { return at(z.ang, AXE_R, 0); }

    private void startAxe(Zone z) {
        int side = z.throwSide;
        stopThrow(z);
        z.axeAt = ft + 30;
        w.playSound(z.feet, Sound.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 2f, .6f);
        Location from = handLoc(z, side).add(0, 1.2, 0);
        from.setYaw(z.yaw);
        float sc = 2.9f * k;
        z.axe = w.spawn(from, ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(Material.NETHERITE_AXE));
            d.setPersistent(false);
            d.addScoreboardTag(RaidSorrow.FX);
            d.setBrightness(new Display.Brightness(12, 12));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setTeleportDuration(4);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateX(-.9f), new Vector3f(sc, sc, sc), new Quaternionf()));
        });
        titleZone(z, "§c도끼!", "§7붉은 원에서 피하세요 · 문지기는 원 안에 두세요");
    }

    private void chop(Zone z) {
        z.axeAt = -1;
        Location c = axeSpot(z);
        if (z.axe != null && z.axe.isValid()) {
            Location down = c.clone().add(0, 1.4, 0);
            down.setYaw(z.yaw);
            z.axe.teleport(down);
            z.axe.setInterpolationDelay(0);
            z.axe.setInterpolationDuration(4);
            float sc = 2.9f * k;
            z.axe.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateX(1.3f), new Vector3f(sc, sc, sc), new Quaternionf()));
            ItemDisplay gone = z.axe;
            Bukkit.getScheduler().runTaskLater(raid.plugin, () -> { if (gone.isValid()) gone.remove(); }, 25L);
            z.axe = null;
        }
        w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 3f, .6f);
        w.spawnParticle(Particle.EXPLOSION, c.clone().add(0, .5, 0), 6, 1.2, .3, 1.2, 0, null, true);
        w.spawnParticle(Particle.BLOCK, c.clone().add(0, .3, 0), 80, 1.5, .2, 1.5, 0, Material.DEEPSLATE.createBlockData(), true);
        double dmg = Math.max(0, raid.cd("g3.damage.axe"));
        for (Player p : alive()) if (flat(p.getLocation(), c) <= AXE_SIZE + .4) p.damage(dmg);
        if (z.keeper != null && z.keeper.isValid() && flat(z.keeper.getLocation(), c) <= AXE_SIZE + .6) {
            w.spawnParticle(Particle.SOUL, z.keeper.getLocation().add(0, 1, 0), 60, .5, 1, .5, .06, null, true);
            all("§a" + z.name() + "의 문지기가 도끼에 쓰러졌습니다!");
            removeKeeper(z);
            removeCrystal(z);
            z.crystalDue = -1;
            to(z, ZStep.DONE);
            boolean others = false;
            for (Zone o : zones) if (o.fighting()) others = true;
            if (others) titleZone(z, "§a문지기 처치", "§7다른 구역의 문지기가 모두 쓰러지면 벽이 내려갑니다");
            checkAllGates();
        } else {
            all("§5" + z.name() + "의 문지기가 도끼를 피했습니다. §7보호막이 다시 생기고 기절이 풀렸습니다.");
            z.keeperStunEnd = -1;
            shieldKeeper(z, true);
        }
    }

    private void ring(Location c, double r, Color col, float size, int pts) {
        Particle.DustOptions d = new Particle.DustOptions(col, size);
        for (int i = 0; i < pts; i++) {
            double a = i * 2 * Math.PI / pts + ft * .03;
            w.spawnParticle(Particle.DUST, c.getX() + Math.cos(a) * r, c.getY() + .15, c.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0, d, true);
        }
    }

    // ================================================================ hits
    @Override public void hit(Entity target, Player p, double dmg) {
        b.mark(target, p);
        if (page == Page.DONE) return;
        for (Orb o : orbs) if (o != null && o.hb != null && target.getUniqueId().equals(o.hb.getUniqueId())) {
            if (!b.started()) { hint(p, "§7먼저 구역의 §a가호 마법진§7에서 시작하세요"); return; }
            hitOrb(o, p);
            return;
        }
        for (Pillar pl : pillars) if (pl.e != null && target.getUniqueId().equals(pl.e.getUniqueId())) { hitPillar(pl, p, dmg); return; }
        for (Zone z : zones) {
            if (z.crystal != null && target.getUniqueId().equals(z.crystal.getUniqueId())) { hitCrystal(z, p, dmg); return; }
            if (z.keeper != null && target.getUniqueId().equals(z.keeper.getUniqueId())) { hitKeeper(z, p); return; }
            if (target instanceof LivingEntity le && z.hp.containsKey(le.getUniqueId())) { hitMob(z, le, p, dmg); return; }
        }
        Object[] pt = parts.get(target.getUniqueId());
        if (pt == null) return;
        Zone z = (Zone) pt[0];
        String where = (String) pt[1];
        int side = where.equals("l") ? 0 : where.equals("r") ? 1 : -1;
        if (z == realZone) {
            if (page == Page.DPS1 || page == Page.DPS2) { damageLord((LivingEntity) target, side < 0 ? dmg : dmg * .5); return; }
            w.playSound(target.getLocation(), Sound.ITEM_SHIELD_BLOCK, SoundCategory.HOSTILE, .8f, 1.2f);
            hint(p, page == Page.HANDS ? "§7군주는 공격이 통하지 않습니다 · §a영혼 촉수 기둥§7을 부수세요" : "§7보석이 다시 빛날 때 공격하세요");
            return;
        }
        if (z.step == ZStep.REVEAL) {
            double before = z.gemHp;
            z.gemHp = Math.max(0, z.gemHp - dmg);
            raid.numbers.show((LivingEntity) target, before - z.gemHp);
            label(z);
            if (z.gemHp <= 0) revealed(z);
            return;
        }
        if (z.step == ZStep.GATE && side >= 0 && z.throwSide >= 0 && z.axeAt < 0) {
            if (side != z.throwSide) { hint(p, "§7빛나는 손은 §a" + (z.throwSide == 0 ? "왼손" : "오른손") + "§7입니다"); return; }
            z.handHits++;
            int need = Math.max(1, raid.ci("g3.gate.hand-hits"));
            w.playSound(target.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.HOSTILE, 1.5f, 1.4f);
            w.spawnParticle(Particle.HAPPY_VILLAGER, target.getLocation().add(0, .5, 0), 10, .3, .3, .3, 0, null, true);
            RaidSorrow.bar(p, "§a빛나는 손 적중! §f" + Math.min(z.handHits, need) + " / " + need);
            if (z.handHits >= need) startAxe(z);
            return;
        }
        w.playSound(target.getLocation(), Sound.ITEM_SHIELD_BLOCK, SoundCategory.HOSTILE, .8f, 1.2f);
        hint(p, switch (z.step) {
            case IDLE -> "§7" + z.name() + "의 §a가호 마법진§7에 서서 시작하세요";
            case MOBS -> "§7먼저 몰려오는 망령을 처치하세요";
            case SHIELD -> "§7보호막 망령을 먼저 처치하세요";
            case GATE -> z.throwSide >= 0 ? "§7화염구를 던지는 §a빛나는 손§7을 맞히세요" : "§7먼저 문지기의 보호막을 벗기세요";
            case DONE -> "§7다른 구역의 문지기가 모두 쓰러지면 보석이 빛납니다";
            default -> "§7지금은 공격이 통하지 않습니다";
        });
    }

    private void hitHand(Zone z, int side, LivingEntity target, double dmg) {
        if (handHp[side] <= 0) return;
        double before = handHp[side];
        handHp[side] = Math.max(0, handHp[side] - dmg);
        raid.numbers.show(target, before - handHp[side]);
        if (handHp[side] <= 0) {
            z.lift[side] = 0;
            glow(side == 0 ? z.hl : z.hr, null);
            w.playSound(target.getLocation(), Sound.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 2f, .7f);
            all("§a" + (side == 0 ? "왼손" : "오른손") + "을 막았습니다!");
        }
        label(z);
        if (handHp[0] <= 0 && handHp[1] <= 0) handsBlocked();
    }

    private void hitMob(Zone z, LivingEntity le, Player p, double dmg) {
        UUID id = le.getUniqueId();
        if (z.shielded.contains(id)) {
            if (!b.holder(p)) {
                w.playSound(le.getLocation(), Sound.ITEM_SHIELD_BLOCK, SoundCategory.HOSTILE, 1f, .8f);
                hint(p, "§7보호막은 §a가호자§7만 벗길 수 있습니다");
                return;
            }
            z.shielded.remove(id);
            le.setGlowing(false);
            nameMob(z, le);
            w.playSound(le.getLocation(), Sound.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 1.5f, 1.1f);
            w.spawnParticle(Particle.BLOCK, le.getLocation().add(0, 1, 0), 30, .4, .6, .4, 0, Material.LIME_STAINED_GLASS.createBlockData(), true);
            return;
        }
        if (z.blessed.contains(id) && !b.canHurt(p, true)) {
            w.playSound(le.getLocation(), Sound.ITEM_SHIELD_BLOCK, SoundCategory.HOSTILE, .7f, 1.4f);
            hint(p, "§a✦ 빛나는 적§7은 §a가호자§7만 공격할 수 있습니다");
            return;
        }
        if (z.round.contains(id) && b.holder(p) && !s.solo) {
            w.playSound(le.getLocation(), Sound.ITEM_SHIELD_BLOCK, SoundCategory.HOSTILE, .7f, 1.4f);
            hint(p, "§7가호자의 공격은 들어가지 않습니다. §f일반 인원§7이 처치하세요");
            return;
        }
        double[] h = z.hp.get(id);
        double before = h[0];
        h[0] = Math.max(0, h[0] - dmg);
        if (before - h[0] >= 1) raid.numbers.show(le, before - h[0]);
        try { le.playHurtAnimation(0); } catch (RuntimeException | LinkageError ignored) { }
        nameMob(z, le);
        if (h[0] <= 0) killMob(z, le);
    }

    private void hitKeeper(Zone z, Player p) {
        if (!z.keeperShield) { hint(p, "§7문지기는 §c군주의 도끼§7로만 쓰러뜨릴 수 있습니다"); return; }
        Player[] pr = b.pair(z.keeper);
        if (pr == null) {
            w.playSound(z.keeper.getLocation(), Sound.ITEM_SHIELD_BLOCK, SoundCategory.HOSTILE, 1f, .8f);
            hint(p, b.holder(p) ? "§7보호막은 §f일반 인원§7과 함께 때려야 깨집니다" : "§7보호막은 §a가호자§7와 함께 때려야 깨집니다");
            return;
        }
        b.hits.remove(z.keeper.getUniqueId());
        w.playSound(z.keeper.getLocation(), Sound.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 2f, .6f);
        w.spawnParticle(Particle.BLOCK, z.keeper.getLocation().add(0, 1.2, 0), 60, .6, .8, .6, 0, Material.LIME_STAINED_GLASS.createBlockData(), true);
        shieldKeeper(z, false);
        z.keeperStunEnd = ft + secs("g3.gate.stun-seconds", 3);
        b.drop(pr[0], "§a문지기 보호막 파괴! §7보호막을 깬 §f" + pr[0].getName() + "§7님의 가호가 사라졌습니다.");
        startThrow(z);
    }

    private void damageLord(LivingEntity at, double d) {
        double before = lordHp;
        lordHp = Math.max(0, lordHp - d);
        if (before - lordHp >= 1) raid.numbers.show(at, before - lordHp);
        if (realZone != null) label(realZone);
        if (!enraged && lordHp / lordMax * 100 < Math.max(0, raid.ci("g3.enrage-pct"))) {
            enraged = true;
            bar.setColor(BarColor.RED);
            say("왕관이… 무너진다… 너희도 같이 무너져라!");
            titleAll("§c광폭화", "§7가호 시간이 " + raid.ci("g3.enrage-blessing-seconds") + "초로 줄어듭니다");
            for (Player p : raid.online(s)) p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, .6f, .7f);
        }
        if (lordHp <= 0) win();
    }

    // ================================================================ 통곡 수정 (per fighting zone)
    private void spawnCrystal(Zone z) {
        Location spot = null;
        for (int tries = 0; tries < 20 && spot == null; tries++) {
            Location c = at(z.ang + rnd.nextDouble(-30, 30), rnd.nextDouble(22, 44), 0);
            if (c.getBlock().getType().isAir() && c.clone().add(0, 1, 0).getBlock().getType().isAir()) spot = c;
        }
        if (spot == null) spot = at(z.ang, 14, 0);
        Location at = spot;
        z.crystal = w.spawn(at, Shulker.class, e -> {
            e.addScoreboardTag(RaidSorrow.VESSEL);
            e.setAI(false);
            e.setSilent(true);
            e.setGravity(false);
            e.setPersistent(false);
            e.setInvisible(true);
            e.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false, false));
            e.setCustomName("§a통곡 수정");
            e.setCustomNameVisible(false);
            e.addScoreboardTag(SorrowKit.NOAIM);
            var sc = e.getAttribute(Attribute.GENERIC_SCALE);
            if (sc != null) sc.setBaseValue(1.4);
        });
        z.crystalFx = SorrowKit.crystalModel(w, at);
        z.crystalShield = SorrowKit.shieldBubble(w, at.clone().add(0, 1, 0), (float) raid.cd("rules.shield-range"), 1f);
        z.crystalMax = z.crystalHp = Math.max(1, raid.cd("rules.crystal-hp") * hpMult());
        if (z.crystalFx == null) z.crystalBlock = w.spawn(at.clone().add(-.6, 0, -.6), BlockDisplay.class, d -> {
            d.setBlock(Material.EMERALD_BLOCK.createBlockData());
            d.setPersistent(false);
            d.addScoreboardTag(RaidSorrow.FX);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setGlowing(true);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1.2f, 1.6f, 1.2f), new Quaternionf()));
        });
        z.crystalLabel = w.spawn(at.clone().add(0, 2.6, 0), TextDisplay.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(RaidSorrow.FX);
            d.setBillboard(Display.Billboard.CENTER);
            d.setShadowed(true);
        });
        z.crystalEnd = ft + secs("g3.crystal.seconds", 5);
        z.crystalDue = ft + secs("g3.crystal.interval-seconds", 5);
        w.playSound(at, Sound.BLOCK_AMETHYST_CLUSTER_PLACE, SoundCategory.HOSTILE, 3f, .6f);
        w.spawnParticle(Particle.END_ROD, at.clone().add(0, 1, 0), 30, .4, .8, .4, .05, null, true);
        titleZone(z, "§a통곡 수정 §7[보호막]", "§7보호막 안에서 가호자 + 일반 인원이 동시에 때려 부수세요 (" + (z.crystalEnd - ft) / 20 + "초)");
        for (Player p : raid.online(s)) if (p.getWorld().equals(w) && zoneOf(p) == z) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, .7f);
    }

    private void hitCrystal(Zone z, Player p, double dmg) {
        w.playSound(z.crystal.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_HIT, SoundCategory.HOSTILE, 1.2f, 1.2f);
        Location cl = z.crystal.getLocation(), pl = p.getLocation();
        if (flat(pl, cl) > raid.cd("rules.shield-range") + .3 || Math.abs(pl.getY() - cl.getY()) > 3.5) {
            Map<UUID, Long> marks = b.hits.get(z.crystal.getUniqueId());
            if (marks != null) marks.remove(p.getUniqueId());
            hint(p, "§d통곡 수정§7은 §f보호막 안으로 들어가§7 공격해야 합니다");
            return;
        }
        if (b.pair(z.crystal) == null) { hint(p, b.holder(p) ? "§7수정은 §f일반 인원§7과 동시에 때려야 피해가 들어갑니다" : "§7수정은 §a가호자§7와 동시에 때려야 피해가 들어갑니다"); return; }
        double before = z.crystalHp;
        z.crystalHp = Math.max(0, z.crystalHp - dmg);
        raid.numbers.show(z.crystal, before - z.crystalHp);
        w.spawnParticle(Particle.DUST, cl.clone().add(0, 1.2, 0), 6, .3, .6, .3, 0, new Particle.DustOptions(SorrowKit.CRYSTAL, 1.2f), true);
        if (z.crystalHp > 0) return;
        Location at = z.crystal.getLocation();
        w.playSound(at, Sound.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 3f, .8f);
        w.playSound(at, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.HOSTILE, 3f, .6f);
        w.spawnParticle(Particle.BLOCK, at.clone().add(0, 1, 0), 60, .5, .8, .5, 0, Material.AMETHYST_BLOCK.createBlockData(), true);
        removeCrystal(z);
        all("§a" + z.name() + "의 통곡 수정을 깼습니다.");
    }

    private void removeCrystal(Zone z) {
        for (Entity e : new Entity[]{z.crystal, z.crystalFx, z.crystalShield, z.crystalBlock, z.crystalLabel}) if (e != null && e.isValid()) e.remove();
        if (z.crystal != null) b.hits.remove(z.crystal.getUniqueId());
        z.crystal = null; z.crystalFx = null; z.crystalShield = null; z.crystalBlock = null; z.crystalLabel = null;
    }

    private void crystalTick(Zone z) {
        if (!z.fighting()) return;
        boolean anyHolder = !b.bless.isEmpty();
        if (!anyHolder) { if (z.crystalDue >= 0) z.crystalDue++; if (z.crystal != null) z.crystalEnd++; } // paused while nobody holds the blessing
        if (z.crystal == null) {
            if (z.crystalDue >= 0 && ft >= z.crystalDue) spawnCrystal(z);
            return;
        }
        long left = z.crystalEnd - ft;
        if (z.crystalShield == null && ft % 4 == 0) { // no mod: a particle shield bubble
            double r = raid.cd("rules.shield-range");
            Particle.DustOptions dd = new Particle.DustOptions(GREEN, 1f);
            Location cc = z.crystal.getLocation();
            for (int j = 0; j < 18; j++) {
                double th = rnd.nextDouble(Math.PI * 2), ph = Math.acos(rnd.nextDouble(-.2, 1));
                w.spawnParticle(Particle.DUST, cc.getX() + r * Math.sin(ph) * Math.cos(th), cc.getY() + .2 + r * Math.cos(ph), cc.getZ() + r * Math.sin(ph) * Math.sin(th), 1, 0, 0, 0, 0, dd, true);
            }
        }
        if (ft % 5 == 0 && z.crystalLabel != null && z.crystalLabel.isValid())
            z.crystalLabel.setText("§d통곡 수정 §f" + (int) Math.ceil(z.crystalHp / z.crystalMax * 100) + "%\n" + (left <= 100 ? "§c" : "§f") + (left + 19) / 20 + "초" + (anyHolder ? "" : " §8(가호자 없음)"));
        if (z.crystalFx != null && z.crystalFx.isValid() && ft % 10 == 0) {
            z.crystalFx.setInterpolationDelay(0);
            z.crystalFx.setInterpolationDuration(10);
            z.crystalFx.setTransformation(new Transformation(new Vector3f(0, (float) Math.sin(ft / 10.0) * .15f, 0), new Quaternionf().rotateY(ft * .03f), z.crystalFx.getTransformation().getScale(), new Quaternionf()));
        }
        if (left > 0 && left <= 100 && left % 20 == 0) for (Player p : raid.online(s)) p.playSound(z.crystal.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1.5f, .6f);
        if (left <= 0) {
            Location at = z.crystal.getLocation();
            removeCrystal(z);
            w.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 4f, .5f);
            w.spawnParticle(Particle.EXPLOSION_EMITTER, at.clone().add(0, 1, 0), 1, 0, 0, 0, 0, null, true);
            say("통곡이 터졌다. 모두 함께 울어라.");
            page(Page.DONE, 0);
            raid.fail(s, z.name() + "의 통곡 수정을 깨지 못해 전멸했습니다.");
        }
    }

    // ================================================================ main loop
    @Override public void tick() {
        ft++;
        List<Player> alive = alive();
        if (page != Page.DONE && alive.isEmpty()) { raid.fail(s, "파티가 전멸했습니다."); return; }
        if (ft > Math.max(1, raid.ci("g3.time-limit-minutes")) * 1200L) { raid.fail(s, "제한 시간 초과"); return; }
        for (Player p : raid.online(s)) if (!bar.getPlayers().contains(p)) bar.addPlayer(p);
        b.tick(alive);
        if (!raid.current(s, this)) return;
        for (Player p : alive) {
            Location l = p.getLocation();
            if (l.getY() < SorrowMap.FLOOR - 3 || SorrowMap.hallDist(3, l.getX(), l.getZ()) > SorrowMap.HALL_R + 1) {
                p.teleport(SorrowMap.hallSpawn(w, 4, 0, 1));
                p.setFallDistance(0);
            }
        }
        if (page == Page.ROUND) {
            for (Zone z : zones) {
                zoneTick(z, alive);
                if (!raid.current(s, this)) return;
                if (page != Page.ROUND) break;
            }
        } else pageTick();
        if (!raid.current(s, this) || page == Page.DONE) return;
        if (ft % 20 == 0) retargetHive(alive);
        hive.tick();
        if (!raid.current(s, this)) return;
        fireballTick();
        orbTick();
        veilShimmer();
        for (Zone z : zones) place(z);
        if (ft % 10 == 0) hud(alive);
    }

    private void zoneTick(Zone z, List<Player> alive) {
        switch (z.step) {
            case IDLE -> padTick(z, alive);
            case MOBS -> {
                if (ft - z.stepAt >= secs("g3.mobs.seconds", 5)) startShield(z, 1);
                else if (ft >= z.nextMob && mobsAlive(z) < Math.max(1, raid.ci("g3.mobs.max") + playersIn(z))) {
                    int roll = rnd.nextInt(9);
                    String kind = roll < 3 ? "wizard" : roll < 6 ? "runner" : roll < 8 ? "acolyte" : "bomber";
                    LivingEntity mob = spawnMob(z, kind);
                    if (kind.equals("wizard")) { z.blessed.add(mob.getUniqueId()); mob.setGlowing(true); SorrowKit.team().addEntry(mob.getUniqueId().toString()); nameMob(z, mob); }
                    else z.round.add(mob.getUniqueId());
                    z.nextMob = ft + 30;
                }
            }
            case SHIELD -> {
                z.mobs.removeIf(m -> !m.isValid());
                boolean left = false;
                for (LivingEntity m : z.mobs) if (z.round.contains(m.getUniqueId())) { left = true; break; }
                if (!left && z.nextRound < 0) z.nextRound = ft + 50;
                if (z.nextRound >= 0 && ft >= z.nextRound) {
                    if (z.shieldRound >= Math.max(1, raid.ci("g3.shield.rounds"))) startGate(z); else startShield(z, z.shieldRound + 1);
                }
                if (ft % 4 == 0) for (LivingEntity m : z.mobs) if (z.shielded.contains(m.getUniqueId()))
                    w.spawnParticle(Particle.DUST, m.getLocation().add(0, 1, 0), 2, .4, .6, .4, 0, new Particle.DustOptions(GREEN, 1f), true);
            }
            case GATE -> gateTick(z);
            case REVEAL -> {
                if (z.gem != null && z.gem.isValid() && ft % 4 == 0) w.spawnParticle(Particle.HAPPY_VILLAGER, z.gem.getLocation(), 3, .2, .2, .2, 0, null, true);
            }
            default -> { }
        }
        if (page == Page.ROUND && b.started()) crystalTick(z);
    }

    private int mobsAlive(Zone z) { z.mobs.removeIf(m -> !m.isValid()); return z.mobs.size(); }

    /** Zone start circle: someone stands on it for 4 s -> the zone starts (and that player gets the blessing if holders are short). */
    private void padTick(Zone z, List<Player> alive) {
        Location c = at(z.ang, PAD_R, 0);
        Player on = null;
        for (Player p : alive) if (flat(p.getLocation(), c) <= PAD_SIZE && Math.abs(p.getLocation().getY() - c.getY()) < 2.5) { if (on == null || !b.holder(p)) on = p; }
        z.padFill = on != null ? z.padFill + 1 : Math.max(0, z.padFill - 2);
        int need = SorrowBlessing.PAD_TICKS;
        if (on != null && ft % 4 == 0) {
            int pct = Math.min(100, z.padFill * 100 / need);
            StringBuilder g = new StringBuilder();
            for (int i = 0; i < 10; i++) g.append(i < pct / 10 ? "§a■" : "§8■");
            for (Player p : alive) if (flat(p.getLocation(), c) <= PAD_SIZE)
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText("§a" + z.name() + " 마법진 " + g + " §f" + pct + "%"));
            on.playSound(on.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, .4f, .6f + pct / 100f);
        }
        if (ft % 5 == 0) {
            ring(c, PAD_SIZE, Color.fromRGB(120, 255, 140), 1.2f, 16);
            if (z.padFx != null && z.padFx.isValid() && ft % 20 == 0) {
                z.padFx.setInterpolationDelay(0);
                z.padFx.setInterpolationDuration(20);
                z.padFx.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateY(ft * .06f), new Vector3f(1.5f, 1.5f, 1.5f), new Quaternionf()));
            }
        }
        if (z.padFill >= need && on != null) startZone(z, on);
    }

    private void gateTick(Zone z) {
        if (z.keeper == null || !z.keeper.isValid()) { spawnKeeper(z); return; }
        if (z.keeperBubble != null && z.keeperBubble.isValid()) z.keeperBubble.teleport(keeperCentre(z));
        Location c = axeSpot(z);
        boolean windup = z.axeAt >= 0;
        if (ft % (windup ? 2 : 6) == 0) ring(c, AXE_SIZE, windup ? Color.fromRGB(255, 40, 40) : Color.fromRGB(170, 40, 40), windup ? 1.6f : 1.1f, windup ? 28 : 18);
        if (windup && ft % 4 == 0) w.spawnParticle(Particle.DUST, c.clone().add(0, .3, 0), 10, AXE_SIZE * .6, .1, AXE_SIZE * .6, 0, new Particle.DustOptions(Color.fromRGB(255, 60, 60), 1.4f), true);
        if (windup && ft >= z.axeAt) { chop(z); return; }
        if (!z.keeperShield) {
            if (ft % 6 == 0) w.spawnParticle(Particle.CRIT, z.keeper.getLocation().add(0, 2.4, 0), 4, .3, .1, .3, .03, null, true);
            if (z.throwSide >= 0) {
                if (ft >= z.nextBall) { launchBall(z); z.nextBall = ft + secs("g3.gate.fireball-interval-seconds", 1); }
                if (ft % 3 == 0) w.spawnParticle(Particle.DUST, handLoc(z, z.throwSide), 3, .3 * k, .3 * k, .3 * k, 0, new Particle.DustOptions(GREEN, 1.4f), true);
            }
            if (!windup && z.keeperStunEnd >= 0 && ft >= z.keeperStunEnd) {
                stopThrow(z);
                z.keeperStunEnd = -1;
                shieldKeeper(z, true);
                all("§5" + z.name() + "의 문지기 기절이 풀리고 보호막이 다시 생겼습니다.");
            }
        }
    }

    private String zoneText(Zone z) {
        String t = switch (z.step) {
            case IDLE -> "마법진에 서세요";
            case MOBS -> "망령 " + Math.max(0, (secs("g3.mobs.seconds", 5) - (ft - z.stepAt) + 19) / 20) + "초";
            case SHIELD -> "보호막 망령 " + z.shieldRound + "/" + Math.max(1, raid.ci("g3.shield.rounds"));
            case GATE -> z.axeAt >= 0 ? "도끼!" : z.throwSide >= 0 ? "빛나는 손을 맞히세요" : "문지기를 붉은 원으로";
            case DONE -> "다른 구역 대기";
            case REVEAL -> "보석 공격";
            case GONE -> "허상";
        };
        return t + (z.crystal != null ? " §a[수정 " + Math.max(0, (z.crystalEnd - ft + 19) / 20) + "초]§f" : "");
    }

    private void hud(List<Player> alive) {
        for (Player p : alive) {
            if (b.onPad(p)) continue;
            Long hinted = hintAt.get(p.getUniqueId());
            if (hinted != null && ft - hinted < 30) continue;
            Zone z = zoneOf(p);
            String zt = page == Page.ROUND ? " §8· §7" + z.name() + " §f" + zoneText(z) : "";
            Long end = b.bless.get(p.getUniqueId());
            long padIn = b.padIn();
            if (end == null) RaidSorrow.bar(p, "§7가호 없음 §8· §7가호자 §a" + b.holders()
                + (b.padUp() ? " §8· §a가호 장판에 올라가세요" : padIn >= 0 ? " §8· §e가호 장판 " + (padIn + 19) / 20 + "초 뒤" : "") + zt);
            else {
                long left = end - ft;
                if (left > 0) RaidSorrow.bar(p, "§a✦ 비탄의 가호 §f" + (left + 19) / 20 + "초 §8· §7가호 구체에서 바꾸기" + zt);
                else RaidSorrow.bar(p, "§5☠ 저주 §f" + (Math.max(3, raid.ci("g3.curse-seconds")) * 20L + left + 19) / 20 + "초 §8· §d가호 구체로!");
            }
        }
        String what = switch (page) {
            case ROUND -> {
                List<String> bits = new ArrayList<>();
                for (Zone z : zones) if (z.step != ZStep.IDLE && z.step != ZStep.GONE) bits.add(z.name() + " " + zoneText(z));
                yield bits.isEmpty() ? roundNo + "라운드 · 구역 마법진에 서세요" : String.join(" §8| §f", bits);
            }
            case STAGGER -> "군주가 중앙에 쓰러졌습니다 " + Math.max(0, (pageEnd - ft + 19) / 20) + "초";
            case DPS1, DPS2 -> "딜 타임 " + Math.max(0, (pageEnd - ft + 19) / 20) + "초";
            case HANDS -> "촉수 기둥을 부수세요 · 보석 충전 " + (int) Math.floor(charge * 100) + "%";
            case DONE -> "";
        };
        bar.setTitle("§d3관문" + (enraged ? " §c[광폭화]" : "") + " §8· §f" + what);
        bar.setProgress(Math.clamp(lordHp / lordMax, 0, 1));
    }

    private void win() {
        page = Page.DONE;
        for (Zone z : zones) { clearZone(z); removePad(z); }
        fireballs.clear();
        lowerWalls(false);
        bar.setProgress(0);
        bar.setTitle("§a3관문 돌파! 비탄의 망령 군주 처치");
        say("왕관이… 부서진다… 이 통곡도… 이제…");
        if (realZone != null && realZone.feet != null) w.spawnParticle(Particle.SOUL, realZone.feet.clone().add(0, k, 0), 120, 1, 1.5 * k, 1, .08, null, true);
        for (Zone z : zones) removeFigure(z);
        removeOrbs();
        for (Player p : raid.online(s)) {
            p.sendTitle("§6통곡의 왕좌 클리어!", "§7비탄의 망령 군주를 쓰러뜨렸습니다", 5, 90, 20);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
        b.cleanup();
        raid.cleared(s, 4);
    }

    private void removeOrbs() {
        for (int i = 0; i < orbs.length; i++) {
            Orb o = orbs[i];
            if (o == null) continue;
            for (Entity e : new Entity[]{o.hb, o.fx, o.label}) if (e != null && e.isValid()) e.remove();
            orbs[i] = null;
        }
    }

    @Override public void cleanup() {
        try { bar.removeAll(); } catch (RuntimeException ignored) { }
        for (Zone z : zones) { clearZone(z); removePad(z); removeFigure(z); }
        fireballs.clear();
        removePillars();
        hive.cleanup();
        removeOrbs();
        lowerWalls(false);
        b.cleanup();
    }
}
