package dev.pipi.life;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 두 번째 레이드: 통곡의 왕좌 (보스 비탄의 망령 군주). 인원 2~6명, 관문 4개 (오늘 파티 전원이 앞 관문을 깼으면 비석이 다음 관문으로 보낸다):
 *   1관문 「군체 저지」 SorrowSwarm · 2관문 「비탄의 허상」 SorrowDecoy · 3관문 「비탄의 왕관」 SorrowCrown.
 * 공통 규칙 (SorrowKit / SorrowBlessing): 가호 장판에서 가호를 받는다 (가호 인원 = 생존 인원 / 2). 가호 60초 → 저주 5초 → 사망.
 * 가호자는 빛나는 적(✦)만, 나머지는 일반 적만 공격할 수 있다. 눈물 성배 · 가호 구체를 가호자 + 일반 인원이 함께 치면 범위 안 모두의 가호가 뒤바뀐다.
 * 통곡 수정은 가호자 + 일반 인원이 함께 쳐야 깨지고 (보호막 수정은 보호막 안에서), 제때 못 깨면 전멸.
 * 모든 수치는 config.yml 의 raid.sorrow.* 에 있다. 잡몹 체력은 가상 풀(1024 상한 회피)이며 피해 숫자는 직접 띄운다.
 */
final class RaidSorrow implements Listener {
    private static final String P = "raid.sorrow.";
    static final String MOB = "pipi_sorrow_mob", FX = "pipi_sorrow_fx", VESSEL = "pipi_sorrow_vessel";
    static final String TITLE = "통곡의 왕좌 · 통곡의 비석"; // PipiTravel RaidGateScreen.SORROW_TITLE
    private static final String STELE = "PIPIFACILITIES_WAILING_STELE";
    private static final String RAID_NAME = "통곡의 왕좌";
    private static final String LORD = "§5[비탄의 망령 군주] §f";
    private static final int GATE_INFO = 4, GATE_MEMBERS = 9, GATE_REWARDS = 36, GATE_ENTER = 49, GATE_CLOSE = 53;
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    final PipiLife plugin;
    private final TownSystems town;
    final DamageNumbers numbers;
    private final SorrowMap map;
    private Session session;
    private long tick;
    private boolean cleaned;

    private enum Stage { FIGHT, CLEARED }

    /** A running gate fight (1관문 SorrowSwarm, 2관문 SorrowDecoy, 3관문 SorrowCrown). */
    interface GateFight {
        void begin(List<Player> on);
        /** Arrival point of party member i of n. */
        Location spawn(int i, int n);
        /** Raid mobs only chase players this returns true for (their own zone). */
        default boolean mayTarget(Entity mob, Player p) { return true; }
        void tick();
        void hit(Entity target, Player p, double dmg);
        List<Player> alive();
        BossBar bar();
        void forget(UUID id);
        void cleanup();
    }

    static final class Session {
        int gate = 1;
        boolean solo; // admin solo test: no party size check, pair mechanics accept one player, no reward / clear record
        final Set<UUID> members = new LinkedHashSet<>();
        final Set<UUID> dead = new HashSet<>();
        final Map<UUID, Location> deathAt = new HashMap<>();
        UUID leader;
        Stage stage = Stage.FIGHT;
        int clearTimer;
        Vote vote;
        GateFight fight;
    }

    private static final class Vote {
        final List<UUID> voters = new ArrayList<>();
        final Map<UUID, Integer> state = new HashMap<>(); // 0 pending, 1 yes, 2 no
        final int total;
        int left, age;
        Vote(int total) { this.total = total; this.left = total; }
    }

    RaidSorrow(PipiLife plugin, TownSystems town, DamageNumbers numbers) {
        this.plugin = plugin;
        this.town = town;
        this.numbers = numbers;
        this.map = new SorrowMap(plugin);
        var c = plugin.getConfig();
        c.addDefault(P + "enabled", true);
        c.addDefault(P + "min-players", 2);
        c.addDefault(P + "max-players", 6);
        c.addDefault(P + "vote-seconds", 15);
        c.addDefault(P + "recommended-item-level", 170);    // 입장 창 표시용 (입장 제한 없음)
        // ---- 공통 규칙 (1~3관문) ----
        c.addDefault(P + "rules.blessing-seconds", 60);
        c.addDefault(P + "rules.curse-seconds", 5);         // 가호가 끝난 뒤 저주 (끝나면 사망)
        c.addDefault(P + "rules.sync-window-ticks", 20);    // 가호자 + 일반 인원이 이 시간 안에 함께 때려야 한다 (1초)
        c.addDefault(P + "rules.vessel-range", 5.0);        // 눈물 성배가 가호를 바꾸는 범위
        c.addDefault(P + "rules.shield-range", 3.5);        // 수정 보호막 반지름: 이 안에서 쳐야 한다
        c.addDefault(P + "rules.crystal-hp", 12000);         // 통곡 수정 체력 (가호자 + 일반이 함께 칠 때만 들어감, 인원 보정)
        c.addDefault(P + "rules.crystal-seconds", 30);      // 시간 제한 있는 수정 (1관문)
        c.addDefault(P + "mob.hp-per-extra-player-pct", 10);
        c.addDefault(P + "mob.hp.wailer", 9000);
        c.addDefault(P + "mob.hp.howler", 7000);
        c.addDefault(P + "mob.hp.servant", 8000);
        c.addDefault(P + "mob.hp.knight", 40000);
        c.addDefault(P + "mob.hp.ogre", 70000);
        c.addDefault(P + "mob.hp.decoy", 1);
        c.addDefault(P + "mob.damage.wailer", 150);
        c.addDefault(P + "mob.damage.howler", 110);
        c.addDefault(P + "mob.damage.servant", 120);
        c.addDefault(P + "mob.damage.knight", 260);
        c.addDefault(P + "mob.damage.ogre", 380);
        c.addDefault(P + "mob.damage.decoy", 300);
        // 군체 몬스터 (HiveMobs)
        c.addDefault(P + "mob.hp.runner", 6000);
        c.addDefault(P + "mob.hp.bomber", 7000);
        c.addDefault(P + "mob.hp.acolyte", 9000);
        c.addDefault(P + "mob.hp.wizard", 14000);
        c.addDefault(P + "mob.damage.runner", 110);
        c.addDefault(P + "mob.damage.bomber", 650);
        c.addDefault(P + "mob.damage.acolyte", 80);
        c.addDefault(P + "mob.damage.wizard", 170);
        c.addDefault(P + "mob.damage.knight-cannon", 340);
        c.addDefault(P + "mob.damage.keeper", 320);
        c.addDefault(P + "mob.damage.ogre-beam", 240);       // 비탄의 거인이 멀리 있는 대상에게 얼굴에서 쏘는 레이저 구체          // 문지기 점프 쌍검 내려찍기
        c.addDefault(P + "mob.knight-front-armor-pct", 60);
        c.addDefault(P + "g1.time-limit-minutes", 25);
        c.addDefault(P + "g1.phases", 4);
        c.addDefault(P + "g1.trickle-max", 3);              // 구역마다 동시에 있는 잡몹 = 이 값 + 구역 인원
        c.addDefault(P + "g1.crystal-delay-seconds", 10);   // 거인이 나오고 수정까지
        c.addDefault(P + "g2.time-limit-minutes", 25);
        c.addDefault(P + "g2.hp.decoy-per-player", 30000);  // 비탄의 허상 체력 = 이 값 x 인원
        c.addDefault(P + "g2.decoy.vulnerable-seconds", 8); // 보호막이 벗겨진 뒤 공격할 수 있는 시간
        c.addDefault(P + "g2.decoy.sync-ticks", 60);        // 허상 보호막: 가호자 + 일반 인원이 3초 안에 함께
        c.addDefault(P + "g2.ogre-after-seconds", 25);      // 가호방의 빛나는 거인이 나오기까지
        c.addDefault(P + "g2.room-ogre-seconds", 15);       // 첫 구체 뒤 방마다 거인이 나오기까지
        c.addDefault(P + "g2.trickle-max", 3);              // 방마다 동시에 있는 잡몹 = 이 값 + 방 인원 (1.5초마다 보충)
        c.addDefault(P + "g2.reward.gold", 200000);
        c.addDefault(P + "g2.reward.steel", 40);
        c.addDefault(P + "g2.reward.level-vouchers", 3);
        // ---- 3관문 · 비탄의 왕관 (SorrowCrown) ----
        c.addDefault(P + "g3.time-limit-minutes", 30);
        c.addDefault(P + "g3.blessing-seconds", 60);
        c.addDefault(P + "g3.enrage-blessing-seconds", 45);
        c.addDefault(P + "g3.enrage-pct", 30);
        c.addDefault(P + "g3.curse-seconds", 5);
        c.addDefault(P + "g3.sync-window-ticks", 20);
        c.addDefault(P + "g3.hp.lord", 360000);
        c.addDefault(P + "g3.hp.gem", 20000);
        c.addDefault(P + "g3.hp.hand", 20000);
        c.addDefault(P + "g3.hp.mob", 9000);
        c.addDefault(P + "g3.hp.per-extra-player-pct", 35);
        c.addDefault(P + "g3.mobs.seconds", 60);
        c.addDefault(P + "g3.mobs.max", 3);
        c.addDefault(P + "g3.shield.rounds", 3);
        c.addDefault(P + "g3.shield.mobs", 2);
        c.addDefault(P + "g3.gate.stun-seconds", 12);
        c.addDefault(P + "g3.gate.hand-hits", 3);
        c.addDefault(P + "g3.gate.fireball-interval-seconds", 1);
        c.addDefault(P + "g3.dps.stagger-seconds", 15);
        c.addDefault(P + "g3.dps.seconds", 10);
        c.addDefault(P + "g3.hands.seconds", 15);
        // 손 페이지 = 영혼 촉수 기둥: 기둥이 다 살아 있으면 charge-seconds 만에 보석이 가득 차서 전멸
        c.addDefault(P + "g3.hp.pillar", 20000);             // 기둥 하나 체력 (인원 보정)
        c.addDefault(P + "g3.pillar.charge-seconds", 20);
        c.addDefault(P + "g3.pillar.dps-fast", 20);           // 충전 10% 이내에 다 부수면 딜 시간(초)
        c.addDefault(P + "g3.pillar.dps-mid", 15);            // 50%
        c.addDefault(P + "g3.pillar.dps-slow", 10);           // 90% 이상
        c.addDefault(P + "g3.crystal.interval-seconds", 30);
        c.addDefault(P + "g3.crystal.seconds", 30);
        c.addDefault(P + "g3.damage.fireball", 220);
        c.addDefault(P + "g3.damage.axe", 500);
        c.addDefault(P + "g3.lord-scale", 4.0);              // 3관문 군주 크기 (1 = 약 2.6블록)
        c.addDefault(P + "g3.reward.gold", 300000);
        c.addDefault(P + "g3.reward.steel", 60);
        c.addDefault(P + "g3.reward.level-vouchers", 4);
        c.addDefault(P + "g3.reward.soul-stone", 1);          // 비탄의 군주 영혼석 (3관문만)
        c.addDefault(P + "g3.reward.accessory-count-min", 2); // 2단계 장신구 개수 (동일 확률, 3관문만)
        c.addDefault(P + "g3.reward.accessory-count-max", 6);
        c.addDefault(P + "g3.auction.accessory-chance", 5.0); // 경매에 보스 전용 장신구가 올라올 확률 (%)
        c.addDefault(P + "auction.start", 55);                // 3관문 클리어 전리품 경매(스킬북) 최소 입찰가: 파티
        c.addDefault(P + "auction.solo-start", 1000000);      //   혼자 클리어
        c.addDefault(P + "g1.time-limit-minutes", 25);
        c.addDefault(P + "g1.blessing-seconds", 60);        // 비탄의 가호 지속 시간
        c.addDefault(P + "g1.curse-seconds", 15);           // 가호가 끝난 뒤 저주 (끝나면 사망)
        c.addDefault(P + "g1.sync-window-ticks", 20);       // 가호자 + 일반 인원이 이 시간 안에 함께 때려야 한다 (1초)
        c.addDefault(P + "g1.wave.base", 3);                // 웨이브 잡몹 수 = base + 인원
        c.addDefault(P + "g1.wave.max-seconds", 30);        // 이 시간이 지나면 웨이브가 남아 있어도 방패 망령 등장
        c.addDefault(P + "g1.hp.wailer", 14000);            // 흐느끼는 망령 (근접)
        c.addDefault(P + "g1.hp.howler", 10000);            // 울부짖는 망령 (원거리)
        c.addDefault(P + "g1.hp.shield", 70000);            // 방패 망령
        c.addDefault(P + "g1.hp.per-extra-player-pct", 35); // 2명 초과 1명당 잡몹 체력 +%
        c.addDefault(P + "g1.shield.regen-seconds", 12);    // 깨진 방패가 다시 생기기까지
        // 고정 피해 (방어력으로만 감소)
        c.addDefault(P + "g1.damage.wailer", 150);
        c.addDefault(P + "g1.damage.howler", 110);
        c.addDefault(P + "g1.damage.shield", 230);
        c.addDefault(P + "g1.lord-scale", 1.6);             // 군주 그림자 크기 (1 = 약 3블록)
        c.addDefault(P + "g1.reward.gold", 120000);         // 1관문 보상 (하루 1회)
        c.addDefault(P + "g1.reward.steel", 25);
        c.addDefault(P + "g1.reward.level-vouchers", 2);
        c.options().copyDefaults(true);
        plugin.saveConfig();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    // ---- config / helpers ----
    double cd(String k) { return plugin.getConfig().getDouble(P + k); }
    int ci(String k) { return plugin.getConfig().getInt(P + k); }
    boolean cb(String k) { return plugin.getConfig().getBoolean(P + k); }
    static void say(Player p, String m) { p.sendMessage(m); }
    static void bar(Player p, String text) { p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(text)); }
    static String fmt(double v) { return String.format(Locale.ROOT, "%,d", Math.round(v)); }
    static String today() { return LocalDate.now(ZONE).toString(); }
    private static boolean inWorld(World w) { return w != null && w.getName().equals(SorrowMap.WORLD); }
    private static boolean inWorld(Block b) { return b != null && inWorld(b.getWorld()); }
    static Material fxMaterial(String id) { return Material.matchMaterial("PIPIFACILITIES_FX_" + id.toUpperCase(Locale.ROOT)); }

    private World world() {
        World w = map.open();
        if (w != null && !cleaned) {
            cleaned = true;
            for (Entity e : w.getEntities()) if (tagged(e)) e.remove();
        }
        return w;
    }

    private static boolean tagged(Entity e) { var t = e.getScoreboardTags(); return t.contains(MOB) || t.contains(FX) || t.contains(VESSEL); }

    boolean build(Player player) { map.build(player, null); return true; }

    Location visitSpawn(int gate) {
        World w = world();
        if (w == null) return null;
        return SorrowMap.hallSpawn(w, gate >= 1 && gate <= 3 ? gate : 1, 0, 1);
    }

    private Session sessionOf(Player p) { Session s = session; return s != null && s.members.contains(p.getUniqueId()) ? s : null; }

    /** True when /pipilife raid … from this player belongs to this raid (member, or standing in its world). */
    boolean owns(Player p) { return sessionOf(p) != null || inWorld(p.getWorld()); }

    List<Player> online(Session s) {
        List<Player> out = new ArrayList<>();
        for (UUID id : s.members) { Player p = Bukkit.getPlayer(id); if (p != null && p.isOnline()) out.add(p); }
        return out;
    }

    void heal(Player p) {
        var a = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (a != null && !p.isDead()) p.setHealth(a.getValue());
        p.setFireTicks(0);
    }

    void goHome(Player p) {
        Location home = plugin.homeFor(p);
        if (home != null) { p.teleport(home); p.setFallDistance(0); }
        p.setGlowing(false);
    }

    // ---- spectators: same tag as RaidDeepDark, so skills / escape / return stones and the join repair treat them alike ----
    void makeGhost(Player p) {
        p.addScoreboardTag(RaidDeepDark.GHOST);
        p.setGameMode(GameMode.ADVENTURE);
        p.setAllowFlight(true);
        p.setFlying(true);
        p.setInvulnerable(true);
        p.setCollidable(false);
        p.setCanPickupItems(false);
        p.setSilent(true);
        p.setGlowing(false);
        p.setFireTicks(0);
        p.setFallDistance(0);
        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false, false));
        for (Player o : Bukkit.getOnlinePlayers()) if (o != p) o.hidePlayer(plugin, p);
        heal(p);
    }

    void unGhost(Player p) {
        if (!RaidDeepDark.ghost(p)) return;
        p.removeScoreboardTag(RaidDeepDark.GHOST);
        p.setGameMode(GameMode.SURVIVAL);
        p.setFlying(false);
        p.setAllowFlight(false);
        p.setInvulnerable(false);
        p.setCollidable(true);
        p.setCanPickupItems(true);
        p.setSilent(false);
        for (PotionEffect e : new ArrayList<>(p.getActivePotionEffects())) p.removePotionEffect(e.getType());
        for (Player o : Bukkit.getOnlinePlayers()) if (o != p) o.showPlayer(plugin, p);
        Location l = p.getLocation();
        if (!p.isDead() && inWorld(l.getWorld()) && l.getY() > SorrowMap.FLOOR + 1.5)
            p.teleport(new Location(l.getWorld(), l.getX(), SorrowMap.FLOOR + 1, l.getZ(), l.getYaw(), l.getPitch()));
        p.setFallDistance(0);
        heal(p);
    }

    // ---- commands (/pipilife raid … is routed here when owns(player)) ----
    boolean command(Player player, String[] args) {
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "vote", "ready", "unready" -> {
                String v = sub.equals("ready") ? "yes" : sub.equals("unready") ? "no" : args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "";
                if (v.equals("yes") || v.equals("y") || v.equals("찬성")) castVote(player, true);
                else if (v.equals("no") || v.equals("n") || v.equals("반대")) castVote(player, false);
                else say(player, "/pipilife raid vote yes|no");
            }
            case "test" -> soloTest(player, args);
            case "leave" -> leaveCmd(player);
            case "status" -> status(player);
            case "stop" -> {
                if (!player.isOp()) say(player, "§c관리자 전용 명령어입니다.");
                else if (session == null) say(player, "진행 중인 레이드가 없습니다.");
                else { endSession(session, "§e관리자가 레이드를 종료했습니다."); say(player, "레이드를 종료했습니다."); }
            }
            default -> say(player, "§d통곡의 왕좌§7: 통곡의 비석을 파티장이 우클릭해 입장합니다. /pipilife raid vote yes|no · leave · status");
        }
        return true;
    }

    /** /pipilife raid test [1|2|3] (op): starts 통곡의 왕좌 alone for testing. */
    private void soloTest(Player player, String[] args) {
        if (!player.isOp()) { say(player, "§c관리자 전용 명령어입니다."); return; }
        int gate = 1;
        if (args.length > 2) try { gate = Math.clamp(Integer.parseInt(args[2]), 1, 3); } catch (NumberFormatException ignored) { }
        if (session != null) { say(player, "§c다른 파티가 도전 중입니다. /pipilife raid stop 으로 먼저 종료하세요."); return; }
        if (world() == null) { say(player, "§c통곡의 왕좌 맵이 없습니다. /pipilife maps buildsorrow 를 먼저 실행하세요."); return; }
        if (player.isDead()) return;
        Session s = new Session();
        s.leader = player.getUniqueId();
        s.gate = gate;
        s.solo = true;
        s.members.add(player.getUniqueId());
        session = s;
        say(player, "§e[혼자 테스트] §f" + gate + "관문을 시작합니다. 둘이 함께 해야 하는 판정은 혼자서도 되고, 보상과 클리어 기록은 없습니다.");
        startFight(s, List.of(player));
    }

    private void leaveCmd(Player player) {
        Session s = sessionOf(player);
        if (s == null) { if (inWorld(player.getWorld())) goHome(player); else say(player, "레이드에 참여 중이 아닙니다."); return; }
        removeMember(s, player);
        goHome(player);
        say(player, "레이드에서 나왔습니다.");
    }

    private void status(Player player) {
        Session s = session;
        if (s == null) { say(player, "통곡의 왕좌: 진행 중인 레이드가 없습니다."); return; }
        List<String> names = new ArrayList<>();
        for (Player p : online(s)) names.add(p.getName() + (s.dead.contains(p.getUniqueId()) ? "(사망)" : ""));
        say(player, "통곡의 왕좌 " + s.gate + "관문 · " + (s.vote != null ? "입장 투표 중" : s.stage == Stage.FIGHT ? "전투 중" : "클리어") + " · " + String.join(", ", names));
    }

    // ---- 통곡의 비석: entry window -> party vote (from anywhere) -> straight into the arena ----
    private static final class GateMenu implements org.bukkit.inventory.InventoryHolder {
        org.bukkit.inventory.Inventory inv;
        Location at;
        @Override public org.bukkit.inventory.Inventory getInventory() { return inv; }
    }

    private static boolean stele(Block b) { return b != null && b.getType().name().equals(STELE); }

    @EventHandler(priority = EventPriority.HIGH)
    public void steleClick(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || !stele(e.getClickedBlock())) return;
        Player p = e.getPlayer();
        if (p.isSneaking() && e.getItem() != null && p.getGameMode() == GameMode.CREATIVE) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND || RaidDeepDark.ghost(p)) return;
        openGate(p, e.getClickedBlock().getLocation().add(.5, 0, .5));
    }

    private String gateBlocked(Player p) {
        if (!cb("enabled")) return "레이드가 비활성화되어 있습니다";
        if (!plugin.canTravel(p)) return "튜토리얼을 완료한 뒤 입장할 수 있습니다";
        UUID leader = town.partyLeader(p);
        if (leader != null && !leader.equals(p.getUniqueId())) return "파티장만 입장을 신청할 수 있습니다";
        if (sessionOf(p) != null) return "이미 레이드에 참여 중입니다";
        if (session != null) return "다른 파티가 도전 중입니다";
        if (town.partyOnline(p).size() < Math.max(1, ci("min-players"))) return "최소 " + Math.max(1, ci("min-players")) + "인 파티가 필요합니다";
        return null;
    }

    private static String ilvl(Player p) {
        try { return String.valueOf((int) Math.round(CombatStats.stats(p).ilvl())); } catch (RuntimeException ex) { return "-"; }
    }

    /** The first gate (1..3) that not every (capped) party member has cleared today after clearing all the ones before it. */
    private int gateFor(Player leader) {
        int cap = Math.max(1, ci("max-players"));
        String day = today();
        for (int g = 1; g < 3; g++) {
            int i = 0;
            for (Player m : town.partyOnline(leader)) {
                if (i++ >= cap) break;
                if (!day.equals(plugin.data().getString("sorrow." + m.getUniqueId() + ".g" + g))) return g;
            }
        }
        return 3;
    }

    static final String[] GATE_NAMES = {"", "군체 저지", "비탄의 허상", "비탄의 왕관"};

    private void openGate(Player p, Location at) {
        GateMenu menu = new GateMenu();
        menu.at = at;
        menu.inv = Bukkit.createInventory(menu, 54, TITLE);
        List<Player> party = town.partyOnline(p);
        int cap = Math.max(1, ci("max-players"));
        int gate = gateFor(p);
        List<String> lore = new ArrayList<>(List.of("§7권장 장비 레벨 " + Math.max(0, ci("recommended-item-level")),
            "§7파티원 " + Math.min(party.size(), cap) + " / " + cap, "§7보스 비탄의 망령 군주"));
        for (int g = 1; g <= 3; g++) {
            boolean done = today().equals(plugin.data().getString("sorrow." + p.getUniqueId() + ".g" + g));
            lore.add((g <= gate ? "§a" : "§8") + g + "관문 · " + GATE_NAMES[g] + (done ? " §7(오늘 클리어)" : g == gate ? " §f← 입장" : g > gate ? " §8(앞 관문 클리어 필요)" : ""));
        }
        lore.add("§7인원 " + Math.max(1, ci("min-players")) + "~" + cap + "명 · 가호 인원 = 인원 ÷ 2");
        menu.inv.setItem(GATE_INFO, plugin.statusIcon(Material.CRYING_OBSIDIAN, "§d" + RAID_NAME, lore.toArray(new String[0])));
        UUID leader = town.partyLeader(p);
        for (int i = 0; i < party.size() && i < cap; i++) {
            Player m = party.get(i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            var meta = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(m);
            meta.setDisplayName("§f" + m.getName());
            boolean lead = leader == null ? m.equals(p) : leader.equals(m.getUniqueId());
            meta.setLore(List.of("§7장비 레벨 " + ilvl(m), lead ? "§6파티장" : "§7파티원"));
            head.setItemMeta(meta);
            menu.inv.setItem(GATE_MEMBERS + i, head);
        }
        List<ItemStack> rewards = new ArrayList<>();
        String g = "g" + gate + ".";
        rewards.add(plugin.statusIcon(Material.GOLD_INGOT, "§6골드 " + fmt(Math.max(0, ci(g + "reward.gold"))), "§7" + gate + "관문 클리어 보상 (하루 1회)"));
        try { rewards.add(town.raidItem("plate", Math.max(1, ci(g + "reward.steel")))); } catch (RuntimeException ignored) { }
        try { rewards.add(town.levelVoucher(Math.max(1, ci(g + "reward.level-vouchers")))); } catch (RuntimeException ignored) { }
        if (ci(g + "reward.soul-stone") > 0) try { rewards.add(town.raidItem("sorrow_soul", ci(g + "reward.soul-stone"))); } catch (RuntimeException ignored) { }
        if (gate == 3) rewards.add(ClassSkills.skillBook(plugin, "sorrow"));
        for (int i = 0; i < rewards.size() && i < 9; i++) menu.inv.setItem(GATE_REWARDS + i, rewards.get(i));
        String why = gateBlocked(p);
        menu.inv.setItem(GATE_ENTER, why == null ? plugin.statusIcon(Material.LIME_DYE, "§a" + gate + "관문 입장", "§7파티원 전원이 찬성하면 경기장으로 이동합니다",
            gate > 1 ? "§7파티 전원이 오늘 " + (gate - 1) + "관문까지 클리어해 " + gate + "관문으로 입장합니다" : "§7파티 전원이 앞 관문을 클리어하면 다음 관문이 열립니다")
            : plugin.statusIcon(Material.GRAY_DYE, "§7" + why));
        menu.inv.setItem(GATE_CLOSE, plugin.statusIcon(Material.BARRIER, "§c닫기"));
        p.openInventory(menu.inv);
        p.playSound(p.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 1f, .7f);
    }

    @EventHandler public void gateClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof GateMenu menu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
        if (e.getRawSlot() == GATE_CLOSE) { p.closeInventory(); return; }
        if (e.getRawSlot() != GATE_ENTER) return;
        p.closeInventory();
        startVote(p, menu.at);
    }

    @EventHandler public void gateDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof GateMenu) e.setCancelled(true);
    }

    // ---- entry vote ----
    private void startVote(Player leader, Location at) {
        String why = gateBlocked(leader);
        if (why != null) { say(leader, "§c" + why + "."); return; }
        if (leader.isDead()) return;
        if (at == null || at.getWorld() != leader.getWorld() || at.distance(leader.getLocation()) > 8) { say(leader, "§c통곡의 비석 가까이에서 입장할 수 있습니다."); return; }
        if (world() == null) { say(leader, "§c통곡의 왕좌 맵이 없습니다. 관리자가 /pipilife maps buildsorrow 를 실행해야 합니다."); return; }
        Session s = new Session();
        s.leader = leader.getUniqueId();
        s.gate = gateFor(leader);
        int cap = Math.max(1, ci("max-players"));
        for (Player m : town.partyOnline(leader)) {
            if (s.members.size() >= cap) break;
            if (m.isDead() || RaidDeepDark.ghost(m) || !plugin.canTravel(m)) continue;
            s.members.add(m.getUniqueId());
        }
        if (s.members.size() < Math.max(1, ci("min-players"))) { say(leader, "§c입장 가능한 파티원이 부족합니다."); return; }
        session = s;
        Vote v = new Vote(Math.max(5, ci("vote-seconds")) * 20);
        for (Player m : online(s)) { v.voters.add(m.getUniqueId()); v.state.put(m.getUniqueId(), 0); }
        v.state.put(leader.getUniqueId(), 1);
        s.vote = v;
        for (Player m : online(s)) {
            m.playSound(m.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.2f);
            say(m, "§d[" + RAID_NAME + "] §f" + leader.getName() + "님이 " + s.gate + "관문 입장을 신청했습니다. §a찬성 Y §7/ §c반대 N §8(/pipilife raid vote yes|no)");
        }
        sendVote(s);
    }

    private void castVote(Player player, boolean yes) {
        Session s = sessionOf(player);
        Vote v = s == null ? null : s.vote;
        if (v == null) { say(player, "진행 중인 투표가 없습니다."); return; }
        if (!v.voters.contains(player.getUniqueId())) { say(player, "이 투표의 대상이 아닙니다."); return; }
        if (v.state.getOrDefault(player.getUniqueId(), 0) == (yes ? 1 : 2)) return;
        v.state.put(player.getUniqueId(), yes ? 1 : 2);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, yes ? 1.4f : .7f);
        if (!yes) { resolveVote(s, false, player.getName() + "님이 반대했습니다."); return; }
        sendVote(s);
    }

    private void sendVote(Session s) {
        Vote v = s.vote;
        if (v == null) return;
        StringBuilder st = new StringBuilder();
        for (UUID id : v.voters) st.append(v.state.getOrDefault(id, 0));
        for (int i = 0; i < v.voters.size(); i++) {
            Player p = Bukkit.getPlayer(v.voters.get(i));
            if (p != null) p.sendMessage("PIPI_VOTE:enter:" + v.total + ":" + Math.max(0, v.left) + ":" + st + ":" + i + ":" + RAID_NAME);
        }
    }

    private void endVoteHud(Vote v, boolean ok) {
        for (UUID id : v.voters) { Player p = Bukkit.getPlayer(id); if (p != null) p.sendMessage("PIPI_VOTE_END:" + (ok ? 1 : 0)); }
    }

    private void voteTick(Session s) {
        Vote v = s.vote;
        if (v == null) return;
        v.age++;
        v.left--;
        boolean changed = v.voters.removeIf(id -> { Player p = Bukkit.getPlayer(id); return p == null || !p.isOnline() || !s.members.contains(id); });
        if (v.voters.isEmpty()) { s.vote = null; endSession(s, null); return; }
        boolean all = true;
        for (UUID id : v.voters) if (v.state.getOrDefault(id, 0) != 1) all = false;
        if (all && v.age >= 20) { resolveVote(s, true, null); return; }
        if (v.left <= 0) { resolveVote(s, false, "시간이 초과되었습니다."); return; }
        if (changed || v.age % 40 == 0) sendVote(s);
    }

    private void resolveVote(Session s, boolean ok, String why) {
        Vote v = s.vote;
        if (v == null) return;
        s.vote = null;
        endVoteHud(v, ok);
        List<Player> voters = new ArrayList<>();
        for (UUID id : v.voters) { Player p = Bukkit.getPlayer(id); if (p != null) voters.add(p); }
        if (!ok) {
            for (Player p : voters) { say(p, "§c입장 투표가 취소되었습니다. §7" + (why == null ? "" : why)); p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, .6f); }
            endSession(s, null);
            return;
        }
        startFight(s, voters);
    }

    // ---- session lifecycle ----
    private void startFight(Session s, List<Player> voters) {
        World w = world();
        if (w == null) { endSession(s, "§c맵을 열지 못했습니다."); return; }
        List<Player> on = new ArrayList<>();
        for (Player p : voters) if (p.isOnline() && !p.isDead() && s.members.contains(p.getUniqueId())) on.add(p);
        s.members.retainAll(on.stream().map(Player::getUniqueId).toList());
        if (!s.solo && on.size() < Math.max(1, ci("min-players"))) { endSession(s, "§c입장 인원이 부족해 취소되었습니다."); return; }
        s.stage = Stage.FIGHT;
        s.dead.clear();
        s.deathAt.clear();
        GateFight f = switch (s.gate) {
            case 2 -> new SorrowDecoy(this, s, w, on.size());
            case 3 -> new SorrowCrown(this, s, w, on.size());
            default -> new SorrowSwarm(this, s, w, on.size());
        };
        s.fight = f;
        for (int i = 0; i < on.size(); i++) {
            Player p = on.get(i);
            p.teleport(f.spawn(i, on.size()));
            p.setFallDistance(0);
            heal(p);
            p.sendTitle("§d통곡의 왕좌", "§7" + s.gate + "관문 · " + GATE_NAMES[s.gate], 10, 60, 15);
            p.playSound(p.getLocation(), Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 1f, .8f);
        }
        f.begin(on);
    }

    private void removeMember(Session s, Player p) {
        unGhost(p);
        p.setGlowing(false);
        s.members.remove(p.getUniqueId());
        s.dead.remove(p.getUniqueId());
        s.deathAt.remove(p.getUniqueId());
        if (s.fight != null) { s.fight.bar().removePlayer(p); s.fight.forget(p.getUniqueId()); }
        if (s.vote != null && s.vote.voters.remove(p.getUniqueId())) p.sendMessage("PIPI_VOTE_END:0");
        if (s.members.isEmpty()) { endSession(s, null); return; }
        if (p.getUniqueId().equals(s.leader)) s.leader = s.members.iterator().next();
    }

    private void endSession(Session s, String message) {
        if (session == s) session = null;
        if (s.vote != null) { endVoteHud(s.vote, false); s.vote = null; }
        if (s.fight != null) { s.fight.cleanup(); s.fight = null; }
        for (UUID id : new ArrayList<>(s.members)) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) continue;
            unGhost(p);
            p.setGlowing(false);
            if (message != null) say(p, message);
            if (inWorld(p.getWorld()) && !p.isDead()) goHome(p);
        }
        s.members.clear();
    }

    void fail(Session s, String reason) {
        for (Player p : online(s)) {
            p.sendTitle("§c관문 실패", "§7" + reason, 5, 50, 10);
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, .5f, .6f);
        }
        endSession(s, "§c" + s.gate + "관문 실패: " + reason + " §7(통곡의 비석에서 다시 도전할 수 있습니다)");
    }

    private void tick() {
        tick++;
        Session s = session;
        if (s == null) return;
        try {
            voteTick(s);
            if (session != s || s.vote != null) return;
            if (s.stage == Stage.FIGHT && s.fight != null) s.fight.tick();
            else if (s.stage == Stage.CLEARED) {
                if (--s.clearTimer <= 0) endSession(s, "§7관문이 끝나 거주지로 돌아갑니다.");
                else if (s.clearTimer % 100 == 0) for (Player p : online(s)) bar(p, "§7" + s.clearTimer / 20 + "초 뒤 거주지로 이동합니다 (/pipilife raid leave 로 바로 이동)");
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().severe("sorrow raid tick failed: " + ex);
            ex.printStackTrace();
            endSession(s, "§c레이드 처리 중 오류가 발생해 종료되었습니다.");
        }
    }

    void shutdown() {
        Session s = session;
        if (s != null) endSession(s, null);
        World w = Bukkit.getWorld(SorrowMap.WORLD);
        if (w != null) for (Entity e : w.getEntities()) if (tagged(e)) e.remove();
    }

    // ---- listeners ----
    private static Player attacker(EntityDamageEvent e) {
        if (!(e instanceof EntityDamageByEntityEvent by)) return null;
        Entity d = by.getDamager();
        if (d instanceof Projectile pr && pr.getShooter() instanceof Entity sh) d = sh;
        return d instanceof Player p ? p : null;
    }

    /** Raid mobs and vessels: the event is always cancelled; hits go to the fight's virtual HP / pairing logic. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void raidTargetDamaged(EntityDamageEvent e) {
        Entity v = e.getEntity();
        if (!v.getScoreboardTags().contains(MOB) && !v.getScoreboardTags().contains(VESSEL)) return;
        Session s = session;
        GateFight f = s == null ? null : s.fight;
        Player p = attacker(e);
        double dmg = e.getFinalDamage();
        e.setCancelled(true);
        if (f == null || p == null || s.stage != Stage.FIGHT || !f.alive().contains(p)) return;
        f.hit(v, p, dmg);
    }

    /** Set by HiveMobs around its own attacks: the exact damage of that hit. */
    static double forcedDamage = Double.NaN;

    /** Fixed damage from raid mobs (melee and arrows); defense still applies later. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void mobHitsPlayer(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Entity d = e.getDamager();
        if (d instanceof Projectile pr && pr.getShooter() instanceof Entity sh) d = sh;
        if (!d.getScoreboardTags().contains(MOB)) return;
        if (!Double.isNaN(forcedDamage)) { e.setDamage(forcedDamage); return; }
        String kind = d instanceof Skeleton ? "howler" : d instanceof WitherSkeleton ? "knight" : "wailer";
        for (String t : d.getScoreboardTags()) if (t.startsWith(SorrowKit.KIND)) kind = t.substring(SorrowKit.KIND.length());
        e.setDamage(Math.max(0, cd("mob.damage." + kind)));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void mobDeath(EntityDeathEvent e) {
        if (!tagged(e.getEntity())) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void mobTarget(EntityTargetEvent e) {
        if (!e.getEntity().getScoreboardTags().contains(MOB)) return;
        if (!(e.getTarget() instanceof Player p) || RaidDeepDark.ghost(p)) { e.setCancelled(true); return; }
        Session s = session;
        if (s != null && s.fight != null && !s.fight.mayTarget(e.getEntity(), p)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void playerDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        Session s = sessionOf(p);
        if (s == null || s.stage != Stage.FIGHT || !inWorld(p.getWorld())) return;
        s.dead.add(p.getUniqueId());
        s.deathAt.put(p.getUniqueId(), p.getLocation().clone());
        p.setGlowing(false);
        if (s.fight != null) s.fight.forget(p.getUniqueId());
        for (Player m : online(s)) say(m, "§c" + p.getName() + "님이 쓰러졌습니다.");
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline() && p.isDead()) p.spigot().respawn(); }, 30L);
    }

    /** Called by PipiLife's respawn handler before the residence fallback. */
    boolean respawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        Session s = sessionOf(p);
        if (s == null) return false;
        GateFight f = s.fight;
        Location died = s.deathAt.get(p.getUniqueId());
        if (s.stage == Stage.FIGHT && f != null && s.dead.contains(p.getUniqueId()) && died != null && inWorld(died.getWorld()) && !f.alive().isEmpty()) {
            Location at = died.clone();
            if (at.getY() < SorrowMap.FLOOR - 5) at = f.spawn(0, 1);
            if (at.getY() < SorrowMap.FLOOR + 1) at.setY(SorrowMap.FLOOR + 1);
            event.setRespawnLocation(at);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!p.isOnline() || p.isDead()) return;
                if (sessionOf(p) == s && s.fight == f && s.dead.contains(p.getUniqueId())) { makeGhost(p); say(p, "§7관전 중입니다. §8(/pipilife raid leave 로 나가기)"); }
                else if (inWorld(p.getWorld())) goHome(p);
            });
            return true;
        }
        Location home = plugin.homeFor(p);
        if (home != null) event.setRespawnLocation(home);
        return true;
    }

    @EventHandler public void quit(PlayerQuitEvent e) {
        Session s = sessionOf(e.getPlayer());
        if (s != null) removeMember(s, e.getPlayer());
    }

    @EventHandler public void changedWorld(PlayerChangedWorldEvent e) {
        Session s = sessionOf(e.getPlayer());
        if (s != null && s.vote == null && !inWorld(e.getPlayer().getWorld())) removeMember(s, e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void spawnLocation(org.spigotmc.event.player.PlayerSpawnLocationEvent e) {
        if (!inWorld(e.getSpawnLocation().getWorld()) || !plugin.canTravel(e.getPlayer())) return;
        Location home = plugin.homeFor(e.getPlayer());
        if (home != null) e.setSpawnLocation(home);
    }

    @EventHandler public void worldLoad(WorldLoadEvent e) { if (inWorld(e.getWorld())) SorrowMap.rules(e.getWorld()); }

    private static boolean builder(Player p) { return p.getGameMode() == GameMode.CREATIVE; }
    @EventHandler(priority = EventPriority.HIGHEST) public void noBreak(BlockBreakEvent e) { if (inWorld(e.getBlock()) && !builder(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void noPlace(BlockPlaceEvent e) { if (inWorld(e.getBlock()) && !builder(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void noBucket(PlayerBucketEmptyEvent e) { if (inWorld(e.getBlock()) && !builder(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void noExplode(EntityExplodeEvent e) { if (inWorld(e.getLocation().getWorld())) e.blockList().clear(); }

    /** The bell next to the arrival point: leave the raid. */
    @EventHandler(priority = EventPriority.HIGH)
    public void bell(PlayerInteractEvent e) {
        Block b = e.getClickedBlock();
        if (b == null || e.getAction() != Action.RIGHT_CLICK_BLOCK || !inWorld(b) || b.getType() != Material.BELL) return;
        e.setCancelled(true);
        if (e.getHand() == EquipmentSlot.HAND && !RaidDeepDark.ghost(e.getPlayer())) leaveCmd(e.getPlayer());
    }

    // ---- rewards ----
    void give(Player p, ItemStack it) {
        if (it == null) return;
        for (ItemStack extra : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), extra).setOwner(p.getUniqueId());
    }

    /** Session still running this fight. */
    boolean current(Session s, GateFight f) { return session == s && s.fight == f; }

    /** A gate was cleared: stage CLEARED (20 s, then home) and the daily reward 2 s later. */
    void cleared(Session s, int gate) {
        s.stage = Stage.CLEARED;
        s.clearTimer = 20 * 20;
        s.dead.clear();
        s.deathAt.clear();
        for (Player p : online(s)) { unGhost(p); p.setGlowing(false); }
        if (s.solo) { for (Player p : online(s)) say(p, "§e[혼자 테스트] §7보상과 클리어 기록은 없습니다."); return; }
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (session == s) rewardGate(s, gate); }, 40L);
        if (gate == 3) Bukkit.getScheduler().runTaskLater(plugin, () -> {   // 전리품 경매: 통곡의 왕좌 스킬북 (딥 다크 경매 창 · 타이머 공유)
            RaidDeepDark dd = plugin.deepDarkRaid();
            List<Player> on = online(s);
            if (dd == null || on.isEmpty()) return;
            long start = Math.max(1, ci(on.size() < 2 ? "auction.solo-start" : "auction.start"));
            List<RaidAuction.Lot> lots = new ArrayList<>(List.of(new RaidAuction.Lot("스킬북 · 통곡의 왕좌", ClassSkills.skillBook(plugin, "sorrow"), start)));
            ItemStack boss = rollBossAccessory();
            if (boss != null) lots.add(new RaidAuction.Lot("보스 전용 장신구", boss, start));
            try { dd.startLootAuction(RAID_NAME, on, lots, 0); }
            catch (RuntimeException ex) { plugin.getLogger().severe("sorrow auction start failed: " + ex); }
        }, 70L);
    }

    private void rewardGate(Session s, int gate) {
        String day = today(), g = "g" + gate + ".";
        for (Player p : online(s)) {
            String path = "sorrow." + p.getUniqueId() + ".g" + gate;
            if (day.equals(plugin.data().getString(path))) { say(p, "§7오늘은 이미 " + gate + "관문 보상을 받았습니다. §8(클리어 기록은 유지됩니다)"); continue; }
            if (!plugin.store(path, day)) { say(p, "§c클리어 기록 저장에 실패했습니다. 관리자에게 문의하세요."); continue; }
            giveGate(p, gate);
        }
    }

    /** 관리자 테스트: gate 관문 보상 그대로 지급 (하루 1회 기록은 남기지 않음, loot = 3관문 경매품 스킬북도 같이). */
    void adminReward(Player p, int gate, boolean loot) {
        giveGate(p, gate);
        if (loot && gate == 3) {
            give(p, ClassSkills.skillBook(plugin, "sorrow"));
            ItemStack boss = Accessories.tier2Boss(RAID_NAME, BOSS_NAME);
            give(p, boss);
            say(p, "§6[경매품] §f스킬북 · 통곡의 왕좌" + (boss != null ? " · " + boss.getItemMeta().getDisplayName() : ""));
        }
    }

    private static final String BOSS_NAME = "비탄의 군주";

    /** 3관문 경매: g3.auction.accessory-chance % 확률로 보스 전용 장신구 1개 (아니면 null). */
    private ItemStack rollBossAccessory() {
        double chance = plugin.getConfig().getDouble(P + "g3.auction.accessory-chance");
        if (java.util.concurrent.ThreadLocalRandom.current().nextDouble(100) >= chance) return null;
        return Accessories.tier2Boss(RAID_NAME, BOSS_NAME);
    }

    private void giveGate(Player p, int gate) {
        String g = "g" + gate + ".";
        {
            try {
                long gold = Math.max(0, ci(g + "reward.gold"));
                var sc = town.gold(p);
                sc.setScore((int) Math.min(Integer.MAX_VALUE, sc.getScore() + gold));
                give(p, town.raidItem("plate", Math.max(1, ci(g + "reward.steel"))));
                give(p, town.levelVoucher(Math.max(1, ci(g + "reward.level-vouchers"))));
                int souls = ci(g + "reward.soul-stone");
                if (souls > 0) give(p, town.raidItem("sorrow_soul", souls));
                say(p, "§6[" + gate + "관문 보상] §f골드 " + fmt(gold) + " · 강철 " + ci(g + "reward.steel") + " · 레벨 +1 교환권 " + ci(g + "reward.level-vouchers")
                    + (souls > 0 ? " · 비탄의 군주 영혼석 " + souls : ""));
            } catch (RuntimeException ex) {
                plugin.getLogger().severe("sorrow reward failed for " + p.getName() + ": " + ex);
                say(p, "§c보상 지급 중 오류가 발생했습니다. 관리자에게 문의하세요.");
            }
            if (gate == 3) try { giveAccessories(p); } catch (RuntimeException ex) {
                plugin.getLogger().severe("sorrow accessory reward failed for " + p.getName() + ": " + ex);
                say(p, "§c장신구 지급 중 오류가 발생했습니다. 관리자에게 문의하세요.");
            }
        }
    }

    /** 2단계 장신구 count-min~count-max 개 (동일 확률), 각각 부위·옵션 2줄·수치 랜덤. */
    private void giveAccessories(Player p) {
        int a = Math.max(1, ci("g3.reward.accessory-count-min")), b = Math.max(1, ci("g3.reward.accessory-count-max"));
        int n = java.util.concurrent.ThreadLocalRandom.current().nextInt(Math.min(a, b), Math.max(a, b) + 1);
        List<String> got = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ItemStack it = Accessories.tier2(RAID_NAME);
            if (it == null) return;
            give(p, it);
            List<String> lore = it.getItemMeta().getLore();
            StringBuilder opts = new StringBuilder();
            if (lore != null) for (int j = 1; j < lore.size() && j <= 2; j++)
                opts.append(j > 1 ? ", " : "").append(lore.get(j).replaceAll("§.", "").replaceFirst("^\\[[^]]+] ", ""));
            got.add(it.getItemMeta().getDisplayName() + " §7(" + opts + ")");
        }
        say(p, "§6[3관문 보상] §f2단계 장신구 " + n + "개");
        for (String g : got) p.sendMessage("  §8- " + g);
    }
}
