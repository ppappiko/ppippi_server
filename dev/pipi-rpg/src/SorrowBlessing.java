package dev.pipi.life;

import java.util.*;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 비탄의 가호 machinery shared by both gates of 통곡의 왕좌 (RaidSorrow 1관문, SorrowCrown 2관문).
 *  - holders target = max(1, alive / 2) (alive 0 -> 0). 2·3명 = 1명, 4명 = 2명, 1명만 남으면 그 1명.
 *  - Blessings are only handed out by the 가호 장판: when there are fewer holders than the target, the pad appears
 *    (immediately at the start of a gate, otherwise after a 10 s warning). Standing on it for 4 s fills the gauge to 100%
 *    and grants the blessing. Leaving the pad drains the gauge twice as fast.
 *  - blessing -> curse -> death, and the "together" check (one holder + one non-holder hit the same target within the window).
 */
final class SorrowBlessing {
    interface Host {
        List<Player> alive();
        void all(String msg);
        long ft();
        World world();
        /** Where the pad appears now. */
        Location padSpot();
        long blessTicks();
        long curseTicks();
        long syncTicks();
        /** The first blessing of the gate was granted (the gate's fight starts here). */
        void started();
        /** False while the gate hands out blessings itself (2관문 zone circles before the fight starts). */
        default boolean autoPad() { return true; }
        /** Admin solo test: one player counts as both the holder and the other member of a pair. */
        default boolean solo() { return false; }
        /** How many holders the pad tops up to. */
        default int target(int alive) { return SorrowBlessing.target(alive); }
        /** False when a gate may have more holders than the target (a vessel handed out more); nobody is trimmed then. */
        default boolean trim() { return true; }
    }

    static final int PAD_TICKS = 80, PAD_DELAY = 200;
    static final double PAD_R = 2.2;

    private final Host h;
    final Map<UUID, Long> bless = new LinkedHashMap<>();
    final Map<UUID, Map<UUID, Long>> hits = new HashMap<>();
    private final Map<UUID, Integer> fill = new HashMap<>();
    private final Set<UUID> onPad = new HashSet<>();
    private long padDue = -1;
    private Location pad;
    private ItemDisplay padFx;
    private boolean started;
    private float spin;

    SorrowBlessing(Host h) { this.h = h; }

    boolean started() { return started; }
    boolean holder(Player p) { return bless.containsKey(p.getUniqueId()); }
    boolean onPad(Player p) { return onPad.contains(p.getUniqueId()); }
    boolean padUp() { return pad != null; }
    long padIn() { return padDue < 0 ? -1 : Math.max(0, padDue - h.ft()); }

    static int target(int alive) { return alive <= 0 ? 0 : Math.max(1, alive / 2); }

    String holders() {
        List<String> out = new ArrayList<>();
        for (UUID id : bless.keySet()) { Player p = Bukkit.getPlayer(id); if (p != null) out.add(p.getName()); }
        return out.isEmpty() ? "없음" : String.join(", ", out);
    }

    void mark(Entity target, Player p) { hits.computeIfAbsent(target.getUniqueId(), k -> new HashMap<>()).put(p.getUniqueId(), h.ft()); }

    /** One holder and one non-holder hit this target within the sync window, or null. */
    Player[] pair(Entity target) {
        Map<UUID, Long> m = hits.get(target.getUniqueId());
        if (m == null) return null;
        long win = Math.max(2, h.syncTicks()), ft = h.ft();
        if (h.solo()) for (Player p : h.alive()) { Long at = m.get(p.getUniqueId()); if (at != null && ft - at <= win) return new Player[]{p, p}; }
        Player holder = null, other = null;
        for (Player p : h.alive()) {
            Long at = m.get(p.getUniqueId());
            if (at == null || ft - at > win) continue;
            if (holder(p)) { if (holder == null) holder = p; }
            else if (other == null) other = p;
        }
        return holder != null && other != null ? new Player[]{holder, other} : null;
    }

    void swap(Player from, Player to, String how) {
        if (from == to) { // solo test: the blessing is renewed instead of handed over
            bless.put(to.getUniqueId(), h.ft() + h.blessTicks());
            to.setGlowing(true);
            to.sendTitle("§a비탄의 가호", "§7갱신 · " + h.blessTicks() / 20 + "초", 0, 30, 8);
            to.playSound(to.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.2f, 1.3f);
            h.all("§a" + how + " §7(혼자 테스트) 가호가 갱신되었습니다.");
            return;
        }
        bless.remove(from.getUniqueId());
        bless.put(to.getUniqueId(), h.ft() + h.blessTicks());
        from.setGlowing(false);
        to.setGlowing(true);
        from.sendTitle("", "§7가호를 넘겼습니다", 0, 25, 8);
        to.sendTitle("§a비탄의 가호", "§7" + h.blessTicks() / 20 + "초", 0, 30, 8);
        for (Player p : List.of(from, to)) p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.2f, 1.3f);
        h.world().spawnParticle(Particle.HAPPY_VILLAGER, to.getLocation().add(0, 1, 0), 20, .4, .6, .4, 0, null, true);
        h.all("§a" + how + " §f" + from.getName() + " §7→ §a" + to.getName() + " §7가호가 옮겨갔습니다.");
    }

    /** Takes the blessing away (no hand-over). */
    void drop(Player p, String why) {
        if (bless.remove(p.getUniqueId()) == null) return;
        p.setGlowing(false);
        p.sendTitle("", "§7가호가 사라졌습니다", 0, 30, 8);
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1.2f);
        if (why != null) h.all(why);
    }

    void forget(UUID id) { bless.remove(id); fill.remove(id); onPad.remove(id); }

    /** Per tick: trims holders, runs the pad, blessing -> curse -> death and the holder particles. */
    void tick(List<Player> alive) {
        long ft = h.ft();
        Set<UUID> ids = new HashSet<>();
        for (Player p : alive) ids.add(p.getUniqueId());
        bless.keySet().removeIf(id -> !ids.contains(id));
        int target = h.target(alive.size());
        while (h.trim() && bless.size() > target) { // newest holder goes first (an orb that hands out more than the target keeps the old ones)
            UUID drop = null;
            for (UUID id : bless.keySet()) drop = id;
            bless.remove(drop);
            Player p = Bukkit.getPlayer(drop);
            if (p != null) p.setGlowing(false);
        }
        padTick(alive, target);
        World w = h.world();
        for (Player p : alive) {
            Long end = bless.get(p.getUniqueId());
            if (end == null) continue;
            long left = end - ft;
            if (left == 0) {
                p.sendTitle("§5저주", "§7" + h.curseTicks() / 20 + "초 안에 가호를 넘기지 못하면 사망합니다", 0, 40, 10);
                p.playSound(p.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 1f, .7f);
                h.all("§5" + p.getName() + "님의 가호가 저주로 바뀌었습니다!");
            }
            if (left <= -h.curseTicks()) {
                bless.remove(p.getUniqueId());
                p.setGlowing(false);
                h.all("§5" + p.getName() + "님이 저주에 삼켜졌습니다.");
                p.setHealth(0);
                continue;
            }
            if (ft % 5 == 0) {
                Location l = p.getLocation().add(0, 1, 0);
                if (left > 0) w.spawnParticle(Particle.DUST, l, 4, .35, .6, .35, 0, new Particle.DustOptions(Color.fromRGB(110, 255, 140), 1.1f), true);
                else w.spawnParticle(Particle.DUST, l, 6, .35, .6, .35, 0, new Particle.DustOptions(Color.fromRGB(120, 40, 160), 1.3f), true);
            }
        }
        if (ft % 20 == 0) for (Player p : alive) p.setGlowing(holder(p));
    }

    private void padTick(List<Player> alive, int target) {
        long ft = h.ft();
        onPad.clear();
        if (!h.autoPad()) { if (pad != null || padDue >= 0) removePad(); return; }
        if (bless.size() >= target) { if (pad != null || padDue >= 0) removePad(); return; }
        if (pad == null) {
            if (padDue < 0) {
                padDue = ft + (started ? PAD_DELAY : 0);
                if (started) {
                    h.all("§e가호를 가진 사람이 부족합니다. §f10초 뒤§e 가호 장판이 생깁니다.");
                    for (Player p : alive) p.sendTitle("", "§e10초 뒤 가호 장판이 생깁니다", 5, 40, 10);
                }
            }
            if (ft < padDue) return;
            spawnPad();
        }
        World w = h.world();
        for (Player p : alive) {
            UUID id = p.getUniqueId();
            Location l = p.getLocation();
            boolean on = !holder(p) && l.getWorld() == pad.getWorld() && Math.hypot(l.getX() - pad.getX(), l.getZ() - pad.getZ()) <= PAD_R && Math.abs(l.getY() - pad.getY()) < 2.5;
            int f = fill.getOrDefault(id, 0);
            f = on ? f + 1 : Math.max(0, f - 2);
            if (f <= 0) fill.remove(id); else fill.put(id, f);
            if (!on) continue;
            onPad.add(id);
            if (ft % 4 == 0) {
                int pct = Math.min(100, f * 100 / PAD_TICKS);
                StringBuilder g = new StringBuilder();
                for (int i = 0; i < 10; i++) g.append(i < pct / 10 ? "§a■" : "§8■");
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText("§a가호 장판 " + g + " §f" + pct + "%"));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, .4f, .6f + pct / 100f);
            }
            if (f >= PAD_TICKS) {
                fill.clear();
                grant(p);
                if (bless.size() >= target) { removePad(); return; }
            }
        }
        if (ft % 5 == 0) { // rim + rising motes so the pad reads from far away
            for (int i = 0; i < 16; i++) {
                double a = i * Math.PI / 8 + ft * .04;
                w.spawnParticle(Particle.DUST, pad.getX() + Math.cos(a) * PAD_R, pad.getY() + .15, pad.getZ() + Math.sin(a) * PAD_R, 1, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.fromRGB(120, 255, 140), 1.2f), true);
            }
            w.spawnParticle(Particle.HAPPY_VILLAGER, pad.clone().add(0, .5, 0), 2, .8, .3, .8, 0, null, true);
        }
        if (padFx != null && padFx.isValid() && ft % 20 == 0) {
            spin += 1.2f;
            padFx.setInterpolationDelay(0);
            padFx.setInterpolationDuration(20);
            padFx.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateY(spin), new Vector3f(1.5f, 1.5f, 1.5f), new Quaternionf()));
        }
    }

    void grant(Player p) { grant(p, "가호 장판에서"); }

    /** Holders may only hurt blessed (glowing) mobs, everyone else only normal mobs. */
    boolean canHurt(Player p, boolean blessedMob) { return h.solo() || holder(p) == blessedMob; }

    /** 눈물 성배 / 가호 구체: everyone in the list swaps state (holders lose the blessing, the others receive it). */
    void invert(List<Player> near, String how) {
        if (near.isEmpty()) return;
        if (h.solo()) { for (Player p : near) { if (holder(p)) swap(p, p, how); else grant(p, how + "에서"); } return; }
        List<Player> lose = new ArrayList<>(), gain = new ArrayList<>();
        for (Player p : near) (holder(p) ? lose : gain).add(p);
        for (Player p : lose) {
            bless.remove(p.getUniqueId());
            p.setGlowing(false);
            p.sendTitle("", "§7가호를 내려놓았습니다", 0, 25, 8);
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.2f, .9f);
        }
        for (Player p : gain) {
            bless.put(p.getUniqueId(), h.ft() + h.blessTicks());
            p.setGlowing(true);
            p.sendTitle("§a비탄의 가호", "§7" + h.blessTicks() / 20 + "초", 0, 30, 8);
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.2f, 1.4f);
            h.world().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 16, .4, .6, .4, 0, null, true);
        }
        List<String> l = new ArrayList<>(), g = new ArrayList<>();
        for (Player p : lose) l.add(p.getName());
        for (Player p : gain) g.add(p.getName());
        h.all("§b" + how + " §7가호 내려놓음: §f" + (l.isEmpty() ? "없음" : String.join(", ", l)) + " §8· §7가호 받음: §a" + (g.isEmpty() ? "없음" : String.join(", ", g)));
        if (!gain.isEmpty() && !started) { started = true; h.started(); }
    }

    void grant(Player p, String where) {
        bless.put(p.getUniqueId(), h.ft() + h.blessTicks());
        p.setGlowing(true);
        p.sendTitle("§a비탄의 가호", "§7" + h.blessTicks() / 20 + "초 안에 일반 인원에게 넘기세요", 5, 40, 10);
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.4f);
        h.world().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 30, .4, .8, .4, .2, null, true);
        h.all("§a" + p.getName() + "님이 " + where + " 비탄의 가호를 받았습니다.");
        if (!started) { started = true; h.started(); }
    }

    private void spawnPad() {
        padDue = -1;
        pad = h.padSpot().clone();
        World w = h.world();
        Material m = Material.matchMaterial("PIPIFACILITIES_FX_ULT_SIGIL_GREEN");
        if (m != null) padFx = w.spawn(pad.clone().add(0, .07, 0), ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(m));
            d.setPersistent(false);
            d.addScoreboardTag(RaidSorrow.FX);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(3f);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1.5f, 1.5f, 1.5f), new Quaternionf()));
        });
        w.playSound(pad, Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.HOSTILE, 2f, 1.2f);
        h.all("§a가호 장판이 생겼습니다! §7가호가 없는 사람이 §f4초§7 동안 올라가 있으면 가호를 받습니다.");
    }

    void removePad() {
        padDue = -1;
        pad = null;
        fill.clear();
        onPad.clear();
        if (padFx != null && padFx.isValid()) padFx.remove();
        padFx = null;
    }

    void cleanup() {
        removePad();
        for (UUID id : bless.keySet()) { Player p = Bukkit.getPlayer(id); if (p != null) p.setGlowing(false); }
        bless.clear();
        hits.clear();
    }
}
