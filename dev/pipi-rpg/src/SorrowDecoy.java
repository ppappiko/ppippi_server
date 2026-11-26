package dev.pipi.life;

import java.util.*;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 통곡의 왕좌 2관문 「비탄의 허상」 (the hall it shares with 3관문).
 *  - 시작: 허브의 군주를 한 번 치면 가호 장판과 함께 세 방에 보호막을 두른 비탄의 허상이 나타난다. 가호자 + 일반 인원이 허상의
 *    보호막 안에서 함께 치면 셋 중 하나만 남는다. 남은 허상의 방이 보스방, 시계 방향으로 다음 방이 가호방, 그다음이 일반방.
 *  - 한 사이클 (방 사이는 통곡의 장막으로 막힘):
 *    1. 가호방 장판에서 한 명이 가호를 받고 각 방의 적을 정리. 빛나는 거인까지 쓰러뜨리면 가호방|일반방 경계에 가호 구체 ->
 *       구체에서 가호를 바꾸면 그 경계의 장막이 걷힌다.
 *    2. 사람이 있는 방마다 거인이 나오고, 모두 쓰러뜨리면 일반방|보스방 경계에 가호 구체 -> 모두 모여 가호를 바꾸면 장막이 모두 걷힌다.
 *    3. 허상의 보호막은 가호자 + 일반 인원이 보호막 안에서 3초 안에 함께 쳐야 벗겨지고, 그때 근처 가호자의 가호가 모두 사라진다.
 *       허상은 일반 적이라 가호 없는 사람만 피해를 준다. 가호자가 남지 않으면 다시 각자 방으로 (1부터).
 */
final class SorrowDecoy extends SorrowKit {
    private enum St { OPEN, DECOYS, ROLES, S1, S2, S3, RESET }
    private static final double ORB_R = 20, DECOY_REACH = 4.5, DECOY_SPLASH = 8;

    private St st = St.OPEN;
    private long stAt, nextTrickle, vulnerableEnd = -1;
    private int boss = -1, bless, plain, cycle;
    private Shulker lordHb;
    private ItemDisplay lordBody, handL, handR;
    private Mob decoy, s1Ogre;
    private final Set<UUID> roomOgres = new HashSet<>();
    private final Map<UUID, ItemDisplay> bubbles = new HashMap<>();
    private final List<Set<Long>> boundCols = new ArrayList<>();
    private boolean openedHit;

    SorrowDecoy(RaidSorrow raid, RaidSorrow.Session s, World w, int n) {
        super(raid, s, w, n, 2, "§d통곡의 왕좌 · 2관문");
        for (int k = 0; k < 3; k++) {
            Set<Long> set = new HashSet<>();
            for (int[] c : SorrowMap.veilColumns(gate, true, 1 << k)) set.add(((long) c[0] << 32) ^ (c[1] & 0xffffffffL));
            boundCols.add(set);
        }
    }

    @Override public Location spawn(int i, int nn) { return SorrowMap.hallSpawn(w, gate, i, nn); }

    @Override Location padSpot() {
        int room = bless;
        if (st == St.OPEN || st == St.DECOYS || boss < 0) return hallAt(90, 5, 0);
        if (playersIn(room).isEmpty()) for (int z = 0; z < 3; z++) if (!playersIn(z).isEmpty()) { room = z; break; }
        return hallAt(SorrowMap.ZONE[room] + 18, SorrowMap.ZONE_R - 8, 0);
    }

    @Override int zoneOf(Location l) { return SorrowMap.zoneAt(gate, l.getX(), l.getZ()); }
    @Override boolean autoPad() { return openedHit && (st == St.DECOYS || st == St.S1); }
    @Override int holderTarget(int alive) { return st == St.S1 ? 1 : SorrowBlessing.target(alive); }
    @Override boolean trimHolders() { return false; }

    @Override public void begin(List<Player> on) {
        for (Player p : on) bar.addPlayer(p);
        scrubVeil();
        Location lord = hallAt(0, 0, 2.5);
        lordBody = prop("sorrow_body", Material.WITHER_SKELETON_SKULL, lord, 3.2f);
        handL = prop("sorrow_hand_l", Material.BONE, lord, 1.6f);
        handR = prop("sorrow_hand_r", Material.BONE, lord, 1.6f);
        lordHb = w.spawn(hallAt(0, 0, 1.2), Shulker.class, e -> {
            e.addScoreboardTag(RaidSorrow.VESSEL);
            e.setAI(false);
            e.setSilent(true);
            e.setGravity(false);
            e.setPersistent(false);
            e.setRemoveWhenFarAway(false);
            e.setInvisible(true);
            e.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false, false));
            e.setCustomName("§5비탄의 망령 군주");
            var sc = e.getAttribute(Attribute.GENERIC_SCALE);
            if (sc != null) sc.setBaseValue(2.4);
        });
        props.add(lordHb);
        say("허상들아, 나를 대신해 울어라. …감히 나를 칠 수 있겠느냐?");
        all("§7허브의 §5군주§7를 한 번 치면 시작합니다. 세 방에 §5비탄의 허상§7이 나타나고, 가호 장판에서 가호를 받을 수 있습니다.");
        all("§7허상의 보호막은 §a가호자 + 일반 인원§7이 §f보호막 안에서§7 함께 쳐야 벗겨집니다.");
        titleAll("§5비탄의 허상", "§7허브의 군주를 한 번 치면 시작합니다");
    }

    private void to(St next) { st = next; stAt = ft; }

    @Override void onOtherHit(Entity t, Player p, double dmg) {
        if (lordHb == null || !t.getUniqueId().equals(lordHb.getUniqueId())) return;
        if (st != St.OPEN) { hint(p, "§7군주에게는 지금 공격이 통하지 않습니다. §5허상§7을 쫓으세요"); return; }
        openedHit = true;
        to(St.DECOYS);
        w.playSound(lordHb.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.HOSTILE, 2f, .8f);
        say("셋 중 누가 나인지 맞혀 보아라.");
        for (int z = 0; z < 3; z++) {
            Mob d = spawnMob("decoy", hallAt(SorrowMap.ZONE[z], SorrowMap.ZONE_R, 0), false, z);
            d.max = d.hp = Math.max(1, gd("hp.decoy-per-player") * Math.max(1, n));
            d.shield = true;
            d.e.setGlowing(true);
            nameMob(d);
        }
        titleAll("§5허상 셋", "§7가호를 받고, 허상의 보호막 안에서 가호자 + 일반 인원이 함께 치세요");
    }

    // ------------------------------------------------------------------ decoy shield
    private boolean pairNear(Entity t, long win) {
        Map<UUID, Long> m = b.hits.get(t.getUniqueId());
        if (m == null) return false;
        boolean h = false, o = false;
        for (Player p : alive()) {
            Long at = m.get(p.getUniqueId());
            if (at == null || ft - at > win) continue;
            if (s.solo) return true;
            if (b.holder(p)) h = true; else o = true;
        }
        return h && o;
    }

    @Override boolean onMobHit(Mob m, Player p, double dmg) {
        if (!m.kind.equals("decoy")) return false;
        if (!m.shield) {
            if (st == St.S3) return false; // vulnerable: normal mob, only non-holders hurt it
            hint(p, "§7지금은 허상에게 공격이 통하지 않습니다");
            return true;
        }
        boolean canBreak = st == St.DECOYS || st == St.S3;
        if (!canBreak) { w.playSound(m.e.getLocation(), Sound.ITEM_SHIELD_BLOCK, SoundCategory.HOSTILE, .8f, .8f); hint(p, "§7허상의 보호막은 장막이 모두 걷힌 뒤에 벗길 수 있습니다"); return true; }
        if (flat(p.getLocation(), m.e.getLocation()) > DECOY_REACH) {
            Map<UUID, Long> marks = b.hits.get(m.e.getUniqueId());
            if (marks != null) marks.remove(p.getUniqueId());
            hint(p, "§5허상의 보호막§7은 §f보호막 안으로 들어가§7 쳐야 합니다");
            return true;
        }
        w.playSound(m.e.getLocation(), Sound.ITEM_SHIELD_BLOCK, SoundCategory.HOSTILE, 1f, .7f);
        if (!pairNear(m.e, Math.max(20, gi("decoy.sync-ticks")))) {
            hint(p, b.holder(p) ? "§7보호막은 §f일반 인원§7과 3초 안에 함께 쳐야 벗겨집니다" : "§7보호막은 §a가호자§7와 3초 안에 함께 쳐야 벗겨집니다");
            return true;
        }
        b.hits.remove(m.e.getUniqueId());
        w.playSound(m.e.getLocation(), Sound.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 2f, .6f);
        w.spawnParticle(Particle.BLOCK, m.e.getLocation().add(0, 1.5, 0), 60, .7, 1, .7, 0, Material.PURPLE_STAINED_GLASS.createBlockData(), true);
        if (st == St.DECOYS) { chooseBoss(m); return true; }
        m.shield = false;
        m.e.setGlowing(false);
        m.e.setAI(false);
        nameMob(m);
        vulnerableEnd = ft + secs("decoy.vulnerable-seconds", 2);
        List<String> lost = new ArrayList<>();
        for (Player q : alive()) if (b.holder(q) && flat(q.getLocation(), m.e.getLocation()) <= DECOY_SPLASH) { lost.add(q.getName()); b.drop(q, null); }
        all("§a허상의 보호막이 벗겨졌습니다! §7가호 없는 사람이 공격하세요 (" + (vulnerableEnd - ft) / 20 + "초)"
            + (lost.isEmpty() ? "" : " §8· §7가호 사라짐: §f" + String.join(", ", lost)));
        say("크윽… 내 껍질이…");
        return true;
    }

    private void chooseBoss(Mob hitOne) {
        List<Mob> ds = new ArrayList<>();
        for (Mob m : mobs.values()) if (m.kind.equals("decoy")) ds.add(m);
        Mob keep = ds.get(rnd.nextInt(ds.size()));
        for (Mob m : ds) if (m != keep) kill(m, true);
        decoy = keep;
        decoy.e.setGlowing(true);
        boss = decoy.zone;
        bless = (boss + 1) % 3;
        plain = (boss + 2) % 3;
        say("…하나는 진짜에 가깝지. 찾아낼 수 있을까?");
        all("§5보스방: §f" + zoneName(boss) + " §8· §a가호방: §f" + zoneName(bless) + " §8· §7일반방: §f" + zoneName(plain));
        all("§710초 뒤 방 사이에 §f통곡의 장막§7이 내려옵니다. 각자 방으로 흩어지세요. 가호 장판은 §a가호방§7에 생깁니다.");
        titleAll("§5" + zoneName(boss) + " = 보스방", "§a가호방 " + zoneName(bless) + " §8· §7일반방 " + zoneName(plain));
        to(St.ROLES);
    }

    // ------------------------------------------------------------------ cycle
    private boolean orbHole(int x, int z) {
        for (int k = 0; k < 3; k++)
            if (Math.hypot(x + .5 - SorrowMap.hx(gate, SorrowMap.BOUND[k], ORB_R), z + .5 - SorrowMap.hz(gate, SorrowMap.BOUND[k], ORB_R)) <= 1.4) return true;
        return false;
    }

    private void startCycle() {
        cycle++;
        to(St.S1);
        s1Ogre = null;
        roomOgres.clear();
        clearBowls();
        decoy.shield = true;
        decoy.e.setGlowing(true);
        decoy.e.setAI(true);
        nameMob(decoy);
        decoy.e.teleport(decoy.home);
        List<int[]> cols = new ArrayList<>();
        for (int[] c : SorrowMap.veilColumns(gate, true, 7)) if (!orbHole(c[0], c[1])) cols.add(c);
        lowerVeil(null);
        raiseVeil(cols);
        b.cleanup();
        b = newBlessing();
        nextTrickle = ft + 60;
        titleAll("§f통곡의 장막", "§7방이 나뉘었습니다 · " + zoneName(bless) + "(가호방)의 장판에서 가호를 받으세요");
        all("§7사이클 " + cycle + ": §a가호방§7에서 한 명이 가호를 받고 적을 정리하세요. §a✦ 빛나는 거인§7을 쓰러뜨리면 가호 구체가 나타납니다.");
    }

    private Location orbAt(int boundary) { return hallAt(SorrowMap.BOUND[boundary], ORB_R, 0); }

    private void openBoundary(int k) {
        lowerVeil(c -> boundCols.get(k).contains(((long) c[0] << 32) ^ (c[2] & 0xffffffffL)));
        soundAll(Sound.BLOCK_BEACON_DEACTIVATE, 1f, .8f);
    }

    private void spawnOrb1() {
        int k = bless; // boundary bless|plain is BOUND[bless] since plain = bless + 1
        spawnBowl(orbAt(k), "가호 구체", true, 4, true, near -> {
            openBoundary(k);
            all("§b" + zoneName(bless) + "|" + zoneName(plain) + " 사이의 장막이 걷혔습니다. §7사람이 있는 방마다 거인이 나타납니다.");
            to(St.S2);
        });
        titleAll("§b가호 구체", "§7" + zoneName(bless) + "와 " + zoneName(plain) + " 경계에서 가호를 바꾸세요");
    }

    private void spawnRoomOgres() {
        for (int z = 0; z < 3; z++) {
            List<Player> here = playersIn(z);
            if (here.isEmpty()) continue;
            boolean h = false, o = false;
            for (Player p : here) { if (b.holder(p)) h = true; else o = true; }
            boolean blessedOgre = h && o ? rnd.nextBoolean() : h;
            Mob og = spawnMob("ogre", hallAt(SorrowMap.ZONE[z] + rnd.nextDouble(-10, 10), SorrowMap.ZONE_R + 6, 0), blessedOgre, z);
            roomOgres.add(og.e.getUniqueId());
            titleZone(z, blessedOgre ? "§a✦ 빛나는 거인" : "§f비탄의 거인", blessedOgre ? "§7가호자가 쓰러뜨리세요" : "§7가호 없는 사람이 쓰러뜨리세요");
        }
        if (roomOgres.isEmpty()) spawnOrb2();
    }

    private void spawnOrb2() {
        int k = plain; // boundary plain|boss is BOUND[plain]
        spawnBowl(orbAt(k), "가호 구체", true, 5, true, near -> {
            lowerVeil(null);
            all("§b모든 장막이 걷혔습니다! §5허상§7의 보호막을 가호자 + 일반 인원이 함께 벗기고, 가호 없는 사람이 공격하세요.");
            titleAll("§5허상을 쓰러뜨려라", "§7보호막을 벗기면 근처 가호자의 가호가 사라집니다");
            to(St.S3);
        });
        titleAll("§b가호 구체", "§7" + zoneName(plain) + "와 " + zoneName(boss) + " 경계에 모두 모여 가호를 바꾸세요");
    }

    @Override void onKill(Mob m) {
        if (m == decoy) { decoy = null; win("§62관문 돌파!", "§7통곡의 비석에서 3관문 · 비탄의 왕관에 도전할 수 있습니다"); return; }
        if (m == s1Ogre && st == St.S1) { s1Ogre = null; spawnOrb1(); return; }
        if (roomOgres.remove(m.e.getUniqueId()) && roomOgres.isEmpty() && st == St.S2) spawnOrb2();
    }

    @Override void step() {
        long k = ft - stAt;
        placeLord();
        switch (st) {
            case OPEN, DECOYS -> { }
            case ROLES -> { if (k >= 200) startCycle(); else if (k % 20 == 0 && k >= 140) titleAll("§f" + (200 - k) / 20, "§7각자 방으로"); }
            case S1 -> {
                trickle();
                if (s1Ogre == null && k >= secs("ogre-after-seconds", 5) && !b.bless.isEmpty() && bowls.isEmpty()) {
                    int room = bless;
                    for (UUID id : b.bless.keySet()) { Player p = Bukkit.getPlayer(id); if (p != null) { room = zoneOf(p.getLocation()); break; } }
                    s1Ogre = spawnMob("ogre", hallAt(SorrowMap.ZONE[room] + 8, SorrowMap.ZONE_R + 6, 0), true, room);
                    titleZone(room, "§a✦ 빛나는 거인", "§7가호자가 쓰러뜨리면 가호 구체가 나타납니다");
                }
            }
            case S2 -> {
                trickle();
                if (k == secs("room-ogre-seconds", 2)) spawnRoomOgres();
            }
            case S3 -> {
                trickle();
                if (decoy == null) return;
                if (!decoy.shield && ft >= vulnerableEnd) {
                    decoy.shield = true;
                    decoy.e.setGlowing(true);
                    decoy.e.setAI(true);
                    nameMob(decoy);
                    all("§5허상의 보호막이 다시 생겼습니다.");
                }
                if (decoy.shield && b.bless.isEmpty() && vulnerableEnd < ft - 40) {
                    to(St.RESET);
                    say("가호가 다 떨어졌느냐. 다시 흩어져라.");
                    titleAll("§7가호자가 없습니다", "§76초 뒤 각자 방으로 돌아가 다시 시작합니다");
                }
            }
            case RESET -> { if (k >= 120) startCycle(); }
        }
        syncBubbles();
    }

    /** The decoy shield: a translucent membrane dome (radius = reach) that follows each shielded decoy; without the mod, glow + a particle ring. */
    private void syncBubbles() {
        Set<UUID> live = new HashSet<>();
        for (Mob m : mobs.values()) {
            if (!m.kind.equals("decoy")) continue;
            UUID id = m.e.getUniqueId();
            live.add(id);
            ItemDisplay bub = bubbles.get(id);
            if (m.shield && (bub == null || !bub.isValid())) {
                bub = SorrowKit.shieldBubble(w, bubbleAt(m), (float) DECOY_REACH, .8f);
                if (bub != null) { bubbles.put(id, bub); props.add(bub); }
            } else if (!m.shield && bub != null) { if (bub.isValid()) bub.remove(); bubbles.remove(id); bub = null; }
            if (bub != null && ft % 2 == 0) bub.teleport(bubbleAt(m));
            m.e.setGlowing(m.shield && bub == null);
            if (m.shield && bub == null && ft % 4 == 0) ring(m.e.getLocation(), DECOY_REACH);
        }
        bubbles.entrySet().removeIf(en -> { if (live.contains(en.getKey())) return false; if (en.getValue().isValid()) en.getValue().remove(); return true; });
    }

    private static Location bubbleAt(Mob m) { Location l = m.e.getLocation().add(0, .2, 0); l.setYaw(0); l.setPitch(0); return l; }

    private void ring(Location c, double r) {
        Particle.DustOptions d = new Particle.DustOptions(Color.fromRGB(190, 120, 255), 1.1f);
        for (int i = 0; i < 20; i++) {
            double a = i * Math.PI / 10 + ft * .04;
            w.spawnParticle(Particle.DUST, c.getX() + Math.cos(a) * r, c.getY() + .2, c.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0, d, true);
        }
    }

    /** Each room with players keeps a steady swarm its people can kill (blessed ones only where a holder is), as many as in 3관문. */
    private void trickle() {
        if (ft < nextTrickle) return;
        nextTrickle = ft + 30;
        for (int z = 0; z < 3; z++) {
            List<Player> here = playersIn(z);
            if (here.isEmpty()) continue;
            boolean h = false, o = false;
            for (Player p : here) { if (b.holder(p)) h = true; else o = true; }
            final int zz = z;
            int cur = count(m -> m.zone == zz && !m.kind.equals("ogre") && !m.kind.equals("decoy"));
            if (cur >= Math.max(2, gi("trickle-max") + here.size())) continue;   // 3관문처럼: 방마다 (설정값 + 방 인원)
            boolean blessedMob = h && o ? rnd.nextInt(3) == 0 : h;
            Location at = hallAt(SorrowMap.ZONE[z] + rnd.nextDouble(-25, 25), SorrowMap.ZONE_R + rnd.nextDouble(-10, 12), 0);
            spawnMob(blessedMob ? "servant" : trash(), at, blessedMob, z);
        }
    }

    private void placeLord() {
        if (ft % 2 != 0) return;
        double bob = Math.sin(ft / 14.0) * .25;
        Location feet = hallAt(0, 0, 2.5 + bob + (st == St.OPEN ? 0 : 5));
        feet.setYaw(0);
        if (lordBody != null && lordBody.isValid()) lordBody.teleport(feet.clone().add(0, 1, 0));
        for (int side = -1; side <= 1; side += 2) {
            ItemDisplay h = side < 0 ? handL : handR;
            if (h == null || !h.isValid()) continue;
            Location at = feet.clone().add(side * 1.7, 2.4 + Math.sin(ft / 9.0 + side) * .2, .4);
            at.setYaw(0);
            h.teleport(at);
        }
    }

    @Override String status(Player p) {
        if (boss < 0) return "";
        int z = zoneOf(p.getLocation());
        return "§7" + zoneName(z) + (z == boss ? " §5(보스방)" : z == bless ? " §a(가호방)" : " §7(일반방)");
    }

    @Override String barText() {
        return switch (st) {
            case OPEN -> "허브의 군주를 치세요";
            case DECOYS -> "허상의 보호막을 함께 벗기세요";
            case ROLES -> "각자 방으로";
            case S1 -> "사이클 " + cycle + " · 가호방에서 빛나는 거인까지" + (bowls.isEmpty() ? "" : " · 가호 구체!");
            case S2 -> "사이클 " + cycle + " · 방마다 거인" + (bowls.isEmpty() ? " " + roomOgres.size() + "마리" : " · 가호 구체!");
            case S3 -> "허상 " + (decoy == null ? "" : decoy.shield ? "보호막" : "공격! " + Math.max(0, (vulnerableEnd - ft + 19) / 20) + "초");
            case RESET -> "다시 각자 방으로";
        };
    }

    @Override double barProgress() { return decoy == null ? (st == St.OPEN ? 1 : 0) : decoy.hp / decoy.max; }
}
