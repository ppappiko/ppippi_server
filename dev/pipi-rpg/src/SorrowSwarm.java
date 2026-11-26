package dev.pipi.life;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;

/**
 * 통곡의 왕좌 1관문 「군체 저지」 (hall 1).
 *  - 인원에 따라 싸우는 구역: 2~3명 = 북쪽 1곳, 4~5명 = 동·서 2곳, 6명 = 3곳 모두. 구역끼리는 통곡의 장막으로 나뉘고 중앙 허브로만 이어진다.
 *  - 허브의 가호 장판에서 가호를 받으면 시작. 가호 60초 -> 저주 5초 -> 사망. 허브 가운데 눈물 성배를 가호자 + 일반 인원이 함께 치면
 *    성배 주변 모두의 가호가 뒤바뀐다 (저주 중이어도 살아난다).
 *  - 한 페이즈: (첫 페이즈만) 허브 양옆 통곡 기사 2 -> 구역마다 빛나는 통곡 기사 + 빛의 시종(위에서 떨어짐) + 일반 망령이 계속
 *    -> 기사를 2번 쓰러뜨리면 통곡 수정 (30초 안에 가호자 + 일반 인원이 함께 깨지 못하면 전멸) -> 기사 한 번 더 -> 양 끝에 비탄의 거인
 *    (하나는 빛나는 거인 = 가호자만, 하나는 일반 거인 = 일반 인원만) -> 잠시 뒤 수정 -> 남은 적을 모두 쓰러뜨리면 페이즈 끝.
 *  - 페이즈 4번이면 클리어. 페이즈가 바뀔 때 군주의 포효가 울린다.
 */
final class SorrowSwarm extends SorrowKit {
    private enum St { WAIT, GUARDS, KNIGHTS, CRYSTAL1, KNIGHT3, OGRES, CRYSTAL2, MOP, BREAK }

    private St st = St.WAIT;
    private long stAt, nextTrickle;
    private int phase, knightsDown;
    private final int[] zones;
    private boolean crystalsOut, trickle;
    private ItemDisplay lordBody, handL, handR;

    SorrowSwarm(RaidSorrow raid, RaidSorrow.Session s, World w, int n) {
        super(raid, s, w, n, 1, "§d통곡의 왕좌 · 1관문");
        zones = n >= 6 ? new int[]{0, 1, 2} : n >= 4 ? new int[]{1, 2} : new int[]{0};
    }

    @Override public Location spawn(int i, int nn) { return SorrowMap.hallSpawn(w, gate, i, nn); }
    @Override Location padSpot() { return hallAt(90, 5, 0); }

    @Override public void begin(List<Player> on) {
        for (Player p : on) bar.addPlayer(p);
        scrubVeil();
        raiseVeil(SorrowMap.veilColumns(gate, false, 7));
        spawnBowl(hallAt(0, 0, 0), "눈물 성배", false, Math.max(2, rd("vessel-range")), false, near -> { });
        Location lord = hallAt(270, 3, 4);
        lordBody = prop("sorrow_body", Material.WITHER_SKELETON_SKULL, lord, 2.6f);
        handL = prop("sorrow_hand_l", Material.BONE, lord, 1.3f);
        handR = prop("sorrow_hand_r", Material.BONE, lord, 1.3f);
        say("감히 내 왕좌에 발을 들이는가… 가호를 쥐어 보아라. 그 순간부터다.");
        List<String> names = new ArrayList<>();
        for (int z : zones) names.add(zoneName(z));
        all("§7싸울 구역: §f" + String.join(", ", names) + " §8(인원 " + n + "명)");
        all("§a가호 장판§7에 4초 서 있으면 가호를 받고 전투가 시작됩니다. 가호자는 §a✦ 빛나는 적§7만, 나머지는 §f일반 적§7만 공격할 수 있습니다.");
        all("§7가호는 60초 뒤 저주로 바뀌고 5초 뒤 사망합니다. 그 전에 허브의 §b눈물 성배§7를 가호자와 일반 인원이 함께 쳐서 서로 바꾸세요.");
        for (Player p : on) p.sendTitle("§a가호 장판", "§7초록 장판 위에 4초 서 있으면 전투가 시작됩니다", 10, 60, 15);
    }

    @Override void onStarted() {
        say("가호를 쥐었구나. 그럼 시작하지.");
        startPhase();
    }

    private void to(St next) { st = next; stAt = ft; }

    private void startPhase() {
        phase++;
        knightsDown = 0;
        crystalsOut = false;
        soundAll(Sound.ENTITY_RAVAGER_ROAR, 1f, .5f);
        soundAll(Sound.BLOCK_BELL_RESONATE, 1f, .6f);
        titleAll("§5페이즈 " + phase + " / " + phases(), phase == 1 ? "§7허브 양옆의 통곡 기사부터 쓰러뜨리세요" : "§7구역으로 흩어지세요");
        if (phase == 1) {
            to(St.GUARDS);
            spawnMob("knight", hallAt(0, 6, 0), true, -1);
            spawnMob("knight", hallAt(180, 6, 0), true, -1);
            say("내 기사들이 문을 지킨다.");
        } else startKnights();
    }

    private int phases() { return Math.max(1, gi("phases")); }

    private void startKnights() {
        to(St.KNIGHTS);
        trickle = true;
        nextTrickle = ft;
        for (int z : zones) spawnKnight(z);
        if (phase > 1 || knightsDown == 0) all("§7각 구역에 §a✦ 통곡 기사§7가 나타났습니다. 기사는 §a가호자§7만, 일반 망령은 §f일반 인원§7만 쓰러뜨릴 수 있습니다.");
    }

    private void spawnKnight(int z) {
        spawnMob("knight", hallAt(SorrowMap.ZONE[z] + rnd.nextDouble(-15, 15), SorrowMap.ZONE_R + rnd.nextDouble(-6, 6), 0), true, z);
        titleZone(z, "§a✦ 통곡 기사", "§7가호자가 쓰러뜨리세요");
    }

    private void spawnCrystals() {
        crystalsOut = true;
        long life = Math.max(5, ri("crystal-seconds")) * 20L;
        for (int z : zones) {
            Location at = hallAt(SorrowMap.ZONE[z] + rnd.nextDouble(-20, 20), SorrowMap.ZONE_R + rnd.nextDouble(-8, 8), 0);
            spawnCrystal(at, true, life, z, () -> all("§a" + zoneName(z) + "의 통곡 수정을 깼습니다."));
            titleZone(z, "§a통곡 수정", "§7가호자 + 일반 인원이 함께 깨세요 (" + life / 20 + "초)");
        }
        say("통곡이 차오른다. 깨지 못하면 모두 함께 울게 될 것이다.");
        soundAll(Sound.BLOCK_NOTE_BLOCK_BELL, 1f, .7f);
    }

    private void spawnOgres() {
        to(St.OGRES);
        int a = zones.length == 1 ? zones[0] : zones.length == 2 ? zones[0] : 1;
        int bz = zones.length == 1 ? zones[0] : zones.length == 2 ? zones[1] : 2;
        boolean blessedFirst = rnd.nextBoolean();
        spawnMob("ogre", hallAt(SorrowMap.ZONE[a] - 12, SorrowMap.ZONE_R + 4, 0), blessedFirst, a);
        spawnMob("ogre", hallAt(SorrowMap.ZONE[bz] + 12, SorrowMap.ZONE_R + 4, 0), !blessedFirst, bz);
        say("거인들아, 깨어나라.");
        all("§5비탄의 거인§7이 나타났습니다! §a✦ 빛나는 거인§7(" + zoneName(blessedFirst ? a : bz) + ")은 가호자만, §f일반 거인§7(" + zoneName(blessedFirst ? bz : a) + ")은 일반 인원만 쓰러뜨릴 수 있습니다.");
        titleAll("§5비탄의 거인", "§7빛나는 거인은 가호자, 일반 거인은 일반 인원");
    }

    @Override void onKill(Mob m) {
        if (!m.kind.equals("knight")) return;
        if (st == St.KNIGHTS && m.zone >= 0) {
            knightsDown++;
            int perRound = zones.length;
            if (knightsDown < perRound * 2) {
                if (knightsDown % perRound == 0) for (int z : zones) spawnKnight(z); // second knight wave
            }
        }
    }

    @Override void step() {
        long k = ft - stAt;
        if (trickle) trickleTick();
        placeLord();
        switch (st) {
            case WAIT, BREAK -> { if (st == St.BREAK && k >= 100) startPhase(); }
            case GUARDS -> { if (count(m -> m.kind.equals("knight")) == 0) { all("§a허브의 기사를 쓰러뜨렸습니다. §7구역으로 흩어지세요!"); startKnights(); } }
            case KNIGHTS -> {
                if (knightsDown >= zones.length * 2 && count(m -> m.kind.equals("knight")) == 0) { to(St.CRYSTAL1); spawnCrystals(); }
            }
            case CRYSTAL1 -> {
                if (crystals.isEmpty() && k > 20) { to(St.KNIGHT3); for (int z : zones) spawnKnight(z); }
            }
            case KNIGHT3 -> { if (count(m -> m.kind.equals("knight")) == 0 && k > 20) spawnOgres(); }
            case OGRES -> { if (k >= secs("crystal-delay-seconds", 2)) { to(St.CRYSTAL2); spawnCrystals(); } }
            case CRYSTAL2 -> {
                if (crystals.isEmpty() && k > 20) {
                    to(St.MOP);
                    trickle = false;
                    all("§a수정을 모두 깼습니다. §7남은 적을 모두 쓰러뜨리세요.");
                }
            }
            case MOP -> {
                if (mobs.isEmpty()) {
                    if (phase >= phases()) { win("§61관문 돌파!", "§7통곡의 비석에서 2관문 · 비탄의 허상에 도전할 수 있습니다"); return; }
                    to(St.BREAK);
                    say("…아직이다. 군단은 끝이 없다.");
                    titleAll("§a페이즈 " + phase + " 완료", "§75초 뒤 다음 페이즈 · 지금 성배에서 가호를 정리하세요");
                }
            }
        }
    }

    /** Servants and slaves keep coming in every fighting zone (blessed servants drop from above). */
    private void trickleTick() {
        if (ft < nextTrickle) return;
        nextTrickle = ft + 40;
        for (int z : zones) {
            int here = 0, blessed = 0;
            for (Mob m : mobs.values()) if (m.zone == z && !m.kind.equals("knight") && !m.kind.equals("ogre")) { here++; if (m.blessed) blessed++; }
            int cap = Math.max(2, gi("trickle-max") + playersIn(z).size());
            if (here >= cap) continue;
            boolean wantBlessed = blessed * 3 < here + 1; // about a third blessed
            Location at = hallAt(SorrowMap.ZONE[z] + rnd.nextDouble(-25, 25), SorrowMap.ZONE_R + rnd.nextDouble(-12, 12), 0);
            if (wantBlessed) spawnMob("servant", at, true, z);
            else spawnMob(trash(), at, false, z);
        }
    }

    private void placeLord() {
        if (ft % 2 != 0) return;
        double bob = Math.sin(ft / 14.0) * .25;
        Location feet = hallAt(270, 3, 4 + bob);
        feet.setYaw(0);
        if (lordBody != null && lordBody.isValid()) lordBody.teleport(feet.clone().add(0, .8, 0));
        for (int side = -1; side <= 1; side += 2) {
            ItemDisplay h = side < 0 ? handL : handR;
            if (h == null || !h.isValid()) continue;
            Location at = feet.clone().add(side * 1.4, 2 + Math.sin(ft / 9.0 + side) * .2, .4);
            at.setYaw(0);
            h.teleport(at);
        }
    }

    @Override String status(Player p) {
        int z = zoneOf(p.getLocation());
        return "§7" + zoneName(z);
    }

    @Override String barText() {
        String what = switch (st) {
            case WAIT -> "가호 장판에 올라가세요";
            case GUARDS -> "허브의 통곡 기사를 쓰러뜨리세요";
            case KNIGHTS -> "통곡 기사 " + knightsDown + " / " + zones.length * 2;
            case CRYSTAL1, CRYSTAL2 -> "통곡 수정을 깨세요 (" + crystals.size() + "개 남음)";
            case KNIGHT3 -> "마지막 통곡 기사";
            case OGRES -> "비탄의 거인!";
            case MOP -> "남은 적 " + mobs.size();
            case BREAK -> "다음 페이즈 준비";
        };
        return "페이즈 " + Math.max(1, phase) + "/" + phases() + " §8· §f" + what;
    }

    @Override double barProgress() {
        double part = switch (st) { case WAIT, GUARDS -> 0; case KNIGHTS -> .2; case CRYSTAL1 -> .4; case KNIGHT3 -> .5; case OGRES -> .6; case CRYSTAL2 -> .75; case MOP -> .9; case BREAK -> 1; };
        return (Math.max(0, phase - 1) + part) / phases();
    }
}
