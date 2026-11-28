package dev.pipi.life;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockReceiveGameEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.world.GenericGameEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 첫 번째 레이드 보스: 딥 다크 시티 (워든). 준비방(렉턴 우클릭 = 준비)에서 파티 전원이 준비하면 보스 전투장으로 이동해
 * 바로 보스전에 돌입한다 (잡몹 없음). 한 번에 한 파티만 진행(잠금). 모든 수치는 config.yml 의 raid.deepdark.* 에 있다.
 *
 * 보스는 AI를 끈 바닐라 워든(generic.scale 확대)이며 이동/공격/패턴은 모두 이 클래스의 틱 루프가 제어한다.
 * 체력은 가상 풀(hp)로 관리하고(스킬 피해는 CombatStats 를 거친 최종 피해를 그대로 반영), 실제 엔티티 체력은 항상 가득 유지한다.
 */
final class RaidDeepDark implements Listener {
    static final String TAG = "pipi_raid_boss";
    private static final String P = "raid.deepdark.";
    private static final NamespacedKeyHolder KEYS = new NamespacedKeyHolder();
    private static final class NamespacedKeyHolder {
        final NamespacedKey skillbook = NamespacedKey.fromString("pipilife:raid_skillbook");
    }

    private final PipiLife plugin;
    private final TownSystems town;
    private final DamageNumbers numbers;
    private final DeepDarkMap map;
    private Session session;
    /** 전리품 경매 after a clear (outlives the session: works in any world while the participants are online). */
    private RaidAuction auction;
    private long tick;
    private boolean cleaned;

    private enum Stage { PREP, FIGHT, CLEARED }
    private enum St { INTRO, NORMAL, STOMP, PILLARS, WINDUP, CHARGE, STAGGER, GROGGY, BURROW, HEART, SONIC, END }

    private static final class Session {
        final Set<UUID> members = new LinkedHashSet<>();
        final Set<UUID> ready = new HashSet<>();
        final Set<UUID> dead = new HashSet<>();
        final Map<UUID, Location> deathAt = new HashMap<>();
        UUID leader;
        Stage stage = Stage.PREP;
        int countdown = -1, clearTimer;
        long lastActivity = System.currentTimeMillis();
        Fight fight;
        Vote vote;
        /** The running ENTER vote was started at a 봉인된 비석: voters may be anywhere; passing teleports them straight into the arena. */
        boolean gate;
    }

    /** 입장/퇴장 투표: every voter must agree (the proposer agrees automatically). */
    private enum VoteKind { ENTER, LEAVE }
    private static final class Vote {
        final VoteKind kind;
        final List<UUID> voters = new ArrayList<>();
        final Map<UUID, Integer> state = new HashMap<>(); // 0 pending, 1 yes, 2 no
        final int total;
        int left, age;
        Vote(VoteKind kind, int total) { this.kind = kind; this.total = total; this.left = total; }
    }

    RaidDeepDark(PipiLife plugin, TownSystems town, DamageNumbers numbers) {
        this.plugin = plugin;
        this.town = town;
        this.numbers = numbers;
        this.map = new DeepDarkMap(plugin);
        var c = plugin.getConfig();
        c.addDefault(P + "enabled", true);
        c.addDefault(P + "max-players", 4);
        c.addDefault(P + "auto-start", true);                // true: 전원 준비 시 카운트다운 후 자동 입장, false: 파티장이 /pipilife raid start
        c.addDefault(P + "countdown-seconds", 5);
        c.addDefault(P + "prep-timeout-minutes", 20);
        c.addDefault(P + "time-limit-minutes", 25);
        c.addDefault(P + "vote-seconds", 15);                // 입장/퇴장 투표 시간
        c.addDefault(P + "vanilla-bossbar", false);          // true: 바닐라 보스바도 함께 표시 (기본은 PipiTravel 전용 HUD)
        c.addDefault(P + "hp.base", 500000);                 // 보스 체력 = base + per-extra-player x (인원-1)
        c.addDefault(P + "hp.per-extra-player", 150000);
        c.addDefault(P + "threshold.gate", 70);              // 체력 % 에서 무적(체력 고정) + 첫 돌진 패턴. 그로기 시작 시 피해 다시 적용
        c.addDefault(P + "rage.per-hit", 1.0);               // 첫 돌진 패턴이 끝난 뒤 피격 1회(다단히트는 타마다)당 분노 게이지 증가량 (100 = 돌진 패턴 재발동)
        c.addDefault(P + "threshold.p2", 35);                // 체력 % 이하에서 2페이즈(심장 소리/구슬)
        c.addDefault(P + "scale", 3.0);                      // 워든 generic.scale
        c.addDefault(P + "move-speed", 0.28);                // 블록/틱 (평상시 추적 속도)
        c.addDefault(P + "darkness.enabled", false);         // 워든 주변 주기적 어둠(시야 좁아짐). 기본 꺼짐
        c.addDefault(P + "darkness.radius", 20);
        c.addDefault(P + "darkness.interval-ticks", 120);
        c.addDefault(P + "darkness.duration-ticks", 100);
        c.addDefault(P + "damage.multiplier", 1.0);          // 전체 피해 배율
        c.addDefault(P + "damage.enrage-multiplier", 1.25);  // 1페이즈 이후 피해 배율
        // 고정 피해량 (체력 비례 아님, 장비 레벨 차이 보정 없음). 방어력으로만 줄어든다 (survive 는 방어력 무시, 체력 1은 남김).
        // 기준: 권장 장비 레벨 160 = 0티어 +6 풀세트 (방어력 1039 → 20.8% 감소, 최대 체력 검사 1071 / 궁수 948 / 마법사 813, 평균 약 944)
        //  -> 방어력 적용 후 평균 체력의 음파 28% · 내려찍기 22% · 돌진 55% · 버티기 성공 20% 가 되도록 맞춘 값
        c.addDefault(P + "damage.sonic", 335);
        c.addDefault(P + "damage.slam", 260);
        c.addDefault(P + "damage.charge", 655);
        c.addDefault(P + "damage.survive", 0);               // 2페이즈 광역 음파를 파훼했을 때 받는 피해 (0 = 피해 없음). 실패하면 전원 사망 → 레이드 실패
        c.addDefault(P + "attack.interval-ticks", 40);
        c.addDefault(P + "sonic.radius", 7.0);
        c.addDefault(P + "sonic.telegraph-ticks", 28);
        c.addDefault(P + "slam.range", 11.0);
        c.addDefault(P + "slam.half-angle", 55);
        c.addDefault(P + "slam.windup-ticks", 10);
        c.addDefault(P + "slam.gap-ticks", 20);              // 첫 타 -> 두 번째 타 (1초)
        c.addDefault(P + "slam.chain-ticks", 4);             // 두 번째 -> 세 번째 (연달아)
        c.addDefault(P + "slam.target-range", 10.0);         // 이 거리 안의 가장 가까운 참가자를 내려찍음. 아무도 없으면 가장 가까운 참가자에게 달려감
        c.addDefault(P + "slam.run-speed", 0.55);            // 달려가는 속도 (블록/틱)
        c.addDefault(P + "slam.run-stop-range", 7.0);        // 이 거리까지 접근하면 내려찍기 시작
        c.addDefault(P + "slam.run-max-ticks", 80);          // 달리기 최대 시간 (지나면 그 자리에서 내려찍기)
        c.addDefault(P + "charge.windup-ticks", 100);        // 5초 준비
        c.addDefault(P + "charge.lock-ticks", 20);           // 돌진 직전 방향 고정
        c.addDefault(P + "charge.speed", 2.0);               // blocks/tick: straight charge, stops at a wall or pillar
        c.addDefault(P + "charge.pillar-radius", 24);
        c.addDefault(P + "charge.pillar-height", 9);
        c.addDefault(P + "groggy.ticks", 200);
        c.addDefault(P + "groggy.damage-multiplier", 1.5);
        c.addDefault(P + "orbs.count", 10);
        c.addDefault(P + "orbs.min-radius", 6);
        c.addDefault(P + "orbs.max-radius", 32);
        c.addDefault(P + "orbs.pickup-radius", 1.7);
        c.addDefault(P + "heartbeat.spacing-ticks", 26);
        c.addDefault(P + "heartbeat.volume", 2.5);
        c.addDefault(P + "heartbeat.pitch", 0.85);
        for (int n = 1; n <= 10; n++) {                      // 나중에 심장 소리 10개 파일을 연결: 사운드 키(예: pipi:raid.heartbeat_5)와 재생 길이(틱)
            c.addDefault(P + "heartbeat.custom-sound." + n, "");
            c.addDefault(P + "heartbeat.custom-ticks." + n, 100);
        }
        c.addDefault(P + "sonic-charge-seconds", 20);
        c.addDefault(P + "regen-per-second", 10);            // max passive HP regen per second in the raid world (skill heals excluded)
        c.addDefault(P + "recommended-item-level", 160);    // 봉인된 비석 입장 창에 표시되는 권장 장비 레벨 (표시만, 입장 제한 없음)
        c.addDefault(P + "reward.gold", 260000);
        c.addDefault(P + "reward.steel", 60);
        c.addDefault(P + "reward.soul-stone", 1);
        c.addDefault(P + "reward.level-vouchers", 5);
        // 1단계 장신구 (전원): count-min~max 개, 각각 부위 랜덤 + 힘/민첩/지능/행운 중 1줄 stat-min~max (모두 동일 확률)
        c.addDefault(P + "reward.accessory-count-min", 2);
        c.addDefault(P + "reward.accessory-count-max", 6);
        c.addDefault(P + "reward.accessory-stat-min", 5);
        c.addDefault(P + "reward.accessory-stat-max", 20);
        // 전리품 경매 (클리어 직후): 영혼석 -> 스킬북 순서
        c.addDefault(P + "auction.step", 55);                // 입찰 단위 (현재가 + step 이상)
        c.addDefault(P + "auction.start", 55);               // 파티(2인 이상) 최소 입찰가
        c.addDefault(P + "auction.solo-start-first", 200000);   // 솔로: 1번(영혼석) 최소 입찰가
        c.addDefault(P + "auction.solo-start-other", 1000000);  // 솔로: 2번(스킬북) 최소 입찰가
        c.addDefault(P + "auction.lot-seconds", 20);
        c.addDefault(P + "auction.extend-seconds", 10);      // 입찰 시 남은 시간을 최소 이만큼으로
        c.addDefault(P + "auction.gap-seconds", 3);          // 낙찰/유찰 결과를 보여 준 뒤 다음 물품까지
        c.options().copyDefaults(true);
        plugin.saveConfig();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    // ---- config ----
    private double cd(String k) { return plugin.getConfig().getDouble(P + k); }
    private int ci(String k) { return plugin.getConfig().getInt(P + k); }
    private boolean cb(String k) { return plugin.getConfig().getBoolean(P + k); }

    // ---- small helpers ----
    private static void say(Player p, String m) { p.sendMessage(m); }
    private static void bar(Player p, String text) { p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(text)); }
    private void snd(World w, double x, double y, double z, Sound s, float vol, float pitch) { w.playSound(new Location(w, x, y, z), s, SoundCategory.HOSTILE, vol, pitch); }
    private void dust(World w, double x, double y, double z, Color c, float size) { w.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, new Particle.DustOptions(c, size), true); }
    private static double yawTo(double fx, double fz, double tx, double tz) { return Math.toDegrees(Math.atan2(-(tx - fx), tz - fz)); }
    private static double angDiff(double a, double b) { double d = Math.abs(a - b) % 360; return d > 180 ? 360 - d : d; }
    private static String fmt(double v) { return String.format(Locale.ROOT, "%,d", Math.round(v)); }

    private World world() {
        World w = map.open();
        if (w != null && !cleaned) {
            cleaned = true;
            for (Entity e : w.getEntities()) if (e.getScoreboardTags().contains(TAG) || e.getScoreboardTags().contains("pipi_raid_orb")) e.remove();
        }
        return w;
    }

    boolean build(Player player) {
        map.build(player, null);
        return true;
    }

    Location visitSpawn() {
        World w = world();
        return w == null ? null : DeepDarkMap.prepSpawn(w);
    }

    private Session sessionOf(Player p) {
        Session s = session;
        return s != null && s.members.contains(p.getUniqueId()) ? s : null;
    }

    private List<Player> online(Session s) {
        List<Player> out = new ArrayList<>();
        for (UUID id : s.members) { Player p = Bukkit.getPlayer(id); if (p != null && p.isOnline()) out.add(p); }
        return out;
    }

    private void heal(Player p) {
        var a = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (a != null && !p.isDead()) p.setHealth(a.getValue());
        p.removePotionEffect(PotionEffectType.DARKNESS);
        p.setFireTicks(0);
    }

    private void toPrep(Player p) {
        World w = world();
        if (w == null) return;
        p.teleport(DeepDarkMap.prepSpawn(w).add((Math.random() - .5) * 6, 0, (Math.random() - .5) * 3));
        p.setFallDistance(0);
        heal(p);
    }

    private void goHome(Player p) {
        Location home = plugin.homeFor(p);
        if (home != null) { p.teleport(home); p.setFallDistance(0); }
        p.removePotionEffect(PotionEffectType.DARKNESS);
    }

    // ---- spectators (dead party members while the rest still fight) ----
    /** Scoreboard tag (saved with the player, so a crash mid-fight is repaired on the next join). ClassSkills checks it too. */
    static final String GHOST = "pipi_raid_ghost";

    static boolean ghost(Player p) { return p.getScoreboardTags().contains(GHOST); }

    /** Adventure-mode flying spectator: invisible, invulnerable, no collision / pickup / interaction (walls still block). */
    private void makeGhost(Player p) {
        p.addScoreboardTag(GHOST);
        p.setGameMode(GameMode.ADVENTURE);
        p.setAllowFlight(true);
        p.setFlying(true);
        p.setInvulnerable(true);
        p.setCollidable(false);
        p.setCanPickupItems(false);
        p.setSilent(true);
        p.setFireTicks(0);
        p.setFallDistance(0);
        p.removePotionEffect(PotionEffectType.DARKNESS);
        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false, false));
        for (Player o : Bukkit.getOnlinePlayers()) if (o != p) o.hidePlayer(plugin, p);
        var a = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (a != null && !p.isDead()) p.setHealth(a.getValue());
    }

    /** Back to a normal survival player (no-op unless the player is a raid spectator). */
    private void unGhost(Player p) {
        if (!ghost(p)) return;
        p.removeScoreboardTag(GHOST);
        p.setGameMode(GameMode.SURVIVAL);
        p.setFlying(false);
        p.setAllowFlight(false);
        p.setInvulnerable(false);
        p.setCollidable(true);
        p.setCanPickupItems(true);
        p.setSilent(false);
        for (PotionEffect e : new ArrayList<>(p.getActivePotionEffects())) p.removePotionEffect(e.getType());
        for (Player o : Bukkit.getOnlinePlayers()) if (o != p) o.showPlayer(plugin, p);
        Location l = p.getLocation(); // flying spectator in the arena: put them back on the floor instead of dropping them
        if (!p.isDead() && l.getWorld() != null && l.getWorld().getName().equals(DeepDarkMap.WORLD) && inArena(l) && l.getY() > DeepDarkMap.FLOOR + 1.5)
            p.teleport(new Location(l.getWorld(), l.getX(), DeepDarkMap.FLOOR + 1, l.getZ(), l.getYaw(), l.getPitch()));
        p.setFallDistance(0);
        heal(p);
    }

    private void unGhostAll(Session s) { for (Player p : online(s)) unGhost(p); }

    private static boolean inArena(Location l) {
        return Math.hypot(l.getX() - DeepDarkMap.ACX, l.getZ() - DeepDarkMap.ACZ) <= DeepDarkMap.R_IN + 2;
    }

    private static Location arenaSpawn(World w) { return new Location(w, DeepDarkMap.ACX, DeepDarkMap.FLOOR + 1, 30.5, 180, 0); }

    // ---- commands ----
    boolean command(Player player, String[] args) {
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "deepdark", "enter" -> enter(player);
            case "ready", "start" -> voteOrPropose(player);
            case "unready" -> castVote(player, false);
            case "vote" -> {
                String v = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "";
                if (v.equals("yes") || v.equals("y") || v.equals("찬성")) voteOrPropose(player);
                else if (v.equals("no") || v.equals("n") || v.equals("반대")) castVote(player, false);
                else say(player, "/pipilife raid vote yes|no");
            }
            case "leave" -> leaveCmd(player);
            case "status" -> status(player);
            case "stop" -> {
                if (!player.isOp()) say(player, "§c관리자 전용 명령어입니다.");
                else if (session == null) say(player, "진행 중인 레이드가 없습니다.");
                else { endSession(session, "§e관리자가 레이드를 종료했습니다."); say(player, "레이드를 종료했습니다."); }
            }
            default -> {
                say(player, "/pipilife raid deepdark · ready · vote yes|no · leave · status" + (player.isOp() ? " · stop" : ""));
                say(player, "딥 다크 시티: 보스 입구의 봉인된 비석을 파티장이 우클릭해 입장을 신청합니다 (파티원 Y 찬성 / N 반대).");
            }
        }
        return true;
    }

    List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            List<String> out = new ArrayList<>(List.of("deepdark", "ready", "vote", "leave", "status"));
            if (sender.isOp()) out.add("stop");
            return out;
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("vote")) return new ArrayList<>(List.of("yes", "no"));
        return List.of();
    }

    private void enter(Player player) {
        if (!player.isOp() && sessionOf(player) == null) { say(player, "§7보스 입구의 §b봉인된 비석§7을 우클릭해 입장하세요."); return; }
        if (!cb("enabled")) { say(player, "레이드가 비활성화되어 있습니다."); return; }
        if (!plugin.canTravel(player)) { say(player, "튜토리얼을 완료한 뒤 입장할 수 있습니다."); return; }
        if (player.isDead()) return;
        World w = world();
        if (w == null) { say(player, "§c딥 다크 맵이 없습니다. 관리자가 /pipilife maps builddeepdark 를 실행해야 합니다."); return; }
        Session cur = sessionOf(player);
        if (cur != null) {
            if (cur.stage == Stage.FIGHT) { say(player, "전투 중에는 다시 입장할 수 없습니다."); return; }
            toPrep(player);
            return;
        }
        UUID leader = town.partyLeader(player);
        if (leader != null && !leader.equals(player.getUniqueId())) {
            Session s = session;
            if (s != null && s.stage == Stage.PREP && leader.equals(s.leader)) {
                if (s.members.size() >= Math.max(1, ci("max-players"))) { say(player, "인원이 가득 찼습니다."); return; }
                s.members.add(player.getUniqueId());
                s.lastActivity = System.currentTimeMillis();
                toPrep(player);
                for (Player m : online(s)) say(m, "§d" + player.getName() + "님이 준비방에 입장했습니다.");
            } else say(player, "파티장이 먼저 /pipilife raid deepdark 로 입장해야 합니다.");
            return;
        }
        if (session != null) { say(player, "§c다른 파티가 레이드를 진행 중입니다. (한 번에 한 파티만 도전할 수 있습니다)"); return; }
        Session s = new Session();
        s.leader = player.getUniqueId();
        int cap = Math.max(1, ci("max-players"));
        for (Player m : town.partyOnline(player)) {
            if (s.members.size() >= cap) break;
            if (m.isDead() || !plugin.canTravel(m)) continue;
            s.members.add(m.getUniqueId());
        }
        session = s;
        for (Player m : online(s)) {
            toPrep(m);
            say(m, "§5[딥 다크 시티] §f준비방에 입장했습니다. 렉턴을 우클릭하면 입장 투표가 시작됩니다.");
            say(m, "§7파티 전원이 찬성(Y)하면 " + Math.max(1, ci("countdown-seconds")) + "초 뒤 보스전이 시작됩니다. 반대는 N.");
        }
    }

    // ---- 봉인된 비석 (boss gate): entry window -> party vote (from anywhere) -> straight into the arena ----
    /** Chest-menu title; PipiTravel replaces this menu with its RaidGateScreen. */
    static final String GATE_TITLE = "깊은 어둠 · 봉인된 비석";
    private static final String STELE = "PIPIFACILITIES_SEALED_STELE";
    private static final int GATE_INFO = 4, GATE_MEMBERS = 9, GATE_REWARDS = 36, GATE_ENTER = 49, GATE_CLOSE = 53;
    private static final double GATE_REACH = 8;

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
        if (p.isSneaking() && e.getItem() != null && p.getGameMode() == GameMode.CREATIVE) return; // builders can place against it
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND || ghost(p)) return;
        openGate(p, e.getClickedBlock().getLocation().add(.5, 0, .5));
    }

    /** Why the player cannot start the entry vote right now, or null. */
    private String gateBlocked(Player p) {
        if (!cb("enabled")) return "레이드가 비활성화되어 있습니다";
        if (!plugin.canTravel(p)) return "튜토리얼을 완료한 뒤 입장할 수 있습니다";
        UUID leader = town.partyLeader(p);
        if (leader != null && !leader.equals(p.getUniqueId())) return "파티장만 입장을 신청할 수 있습니다";
        if (sessionOf(p) != null) return "이미 레이드에 참여 중입니다";
        if (session != null) return "다른 파티가 도전 중입니다";
        return null;
    }

    private static String ilvl(Player p) {
        try { return String.valueOf((int) Math.round(CombatStats.stats(p).ilvl())); } catch (RuntimeException ex) { return "-"; }
    }

    private void openGate(Player p, Location at) {
        GateMenu menu = new GateMenu();
        menu.at = at;
        menu.inv = Bukkit.createInventory(menu, 54, GATE_TITLE);
        List<Player> party = town.partyOnline(p);
        int cap = Math.max(1, ci("max-players"));
        menu.inv.setItem(GATE_INFO, plugin.statusIcon(Material.ECHO_SHARD, "§b" + RAID_NAME,
            "§7권장 장비 레벨 " + Math.max(0, ci("recommended-item-level")),
            "§7파티원 " + Math.min(party.size(), cap) + " / " + cap,
            "§7보스 심연의 감시자 워든"));
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
        long gold = Math.max(0, plugin.getConfig().getLong(P + "reward.gold"));
        rewards.add(plugin.statusIcon(Material.GOLD_INGOT, "§6골드 " + fmt(gold), "§7클리어 보상 (전원)"));
        try { rewards.add(town.raidItem("plate", Math.max(1, ci("reward.steel")))); } catch (RuntimeException ignored) { }
        try { rewards.add(town.levelVoucher(Math.max(1, ci("reward.level-vouchers")))); } catch (RuntimeException ignored) { }
        try { rewards.add(town.raidItem("soul", Math.max(1, ci("reward.soul-stone")))); } catch (RuntimeException ignored) { }
        int[] acc = accessoryRange("reward.accessory-count-min", "reward.accessory-count-max");
        int[] stat = accessoryRange("reward.accessory-stat-min", "reward.accessory-stat-max");
        rewards.add(plugin.statusIcon(Material.ECHO_SHARD, "§91단계 장신구 " + acc[0] + "~" + acc[1] + "개",
            "§7클리어 보상 (전원) · 부위 랜덤", "§7힘 / 민첩 / 지능 / 행운 중 1줄 +" + stat[0] + "~" + stat[1]));
        rewards.add(ClassSkills.skillBook(plugin, "deepdark"));
        for (int i = 0; i < rewards.size() && i < 9; i++) menu.inv.setItem(GATE_REWARDS + i, rewards.get(i));
        String why = gateBlocked(p);
        menu.inv.setItem(GATE_ENTER, why == null ? plugin.statusIcon(Material.LIME_DYE, "§a입장하기", "§7파티원 전원이 찬성하면 보스방으로 이동합니다")
            : plugin.statusIcon(Material.GRAY_DYE, "§7" + why));
        menu.inv.setItem(GATE_CLOSE, plugin.statusIcon(Material.BARRIER, "§c닫기"));
        p.openInventory(menu.inv);
        p.playSound(p.getLocation(), Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, .35f, .7f);
    }

    @EventHandler public void gateClick(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof GateMenu menu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
        if (e.getRawSlot() == GATE_CLOSE) { p.closeInventory(); return; }
        if (e.getRawSlot() != GATE_ENTER) return;
        p.closeInventory();
        startGateVote(p, menu.at);
    }

    @EventHandler public void gateDrag(org.bukkit.event.inventory.InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof GateMenu) e.setCancelled(true);
    }

    /** Leader pressed 입장하기: reserve the raid and ask every online party member (wherever they are). */
    private void startGateVote(Player leader, Location at) {
        String why = gateBlocked(leader);
        if (why != null) { say(leader, "§c" + why + "."); return; }
        if (leader.isDead()) return;
        if (at == null || at.getWorld() != leader.getWorld() || at.distance(leader.getLocation()) > GATE_REACH) { say(leader, "§c봉인된 비석 가까이에서 입장할 수 있습니다."); return; }
        if (world() == null) { say(leader, "§c딥 다크 맵이 없습니다. 관리자가 /pipilife maps builddeepdark 를 실행해야 합니다."); return; }
        Session s = new Session();
        s.leader = leader.getUniqueId();
        int cap = Math.max(1, ci("max-players"));
        for (Player m : town.partyOnline(leader)) {
            if (s.members.size() >= cap) break;
            if (m.isDead() || ghost(m) || !plugin.canTravel(m) || sessionOf(m) != null) continue;
            s.members.add(m.getUniqueId());
        }
        session = s;
        List<Player> voters = online(s);
        if (voters.size() <= 1) { gateEnter(s, voters); return; } // solo: no vote needed
        Vote v = new Vote(VoteKind.ENTER, Math.max(5, ci("vote-seconds")) * 20);
        for (Player m : voters) { v.voters.add(m.getUniqueId()); v.state.put(m.getUniqueId(), 0); }
        v.state.put(leader.getUniqueId(), 1);
        s.vote = v;
        s.gate = true;
        for (Player m : voters) {
            m.playSound(m.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.2f);
            say(m, "§d[" + RAID_NAME + "] §f" + leader.getName() + "님이 보스 입장을 신청했습니다. §a찬성 Y §7/ §c반대 N §8(/pipilife raid vote yes|no)");
        }
        sendVote(s);
    }

    /** Gate vote passed (or solo): everyone who agreed goes straight into the arena. */
    private void gateEnter(Session s, List<Player> voters) {
        World w = world();
        if (w == null) { endSession(s, "§c맵을 열지 못했습니다."); return; }
        s.ready.clear();
        for (Player p : voters) {
            if (p == null || !p.isOnline() || p.isDead() || !s.members.contains(p.getUniqueId())) continue;
            toPrep(p); // into the raid world first (heals, lands in the prep room as the fallback)
            s.ready.add(p.getUniqueId());
            p.sendTitle("§b봉인 해제", "§7" + RAID_NAME + "에 입장합니다", 5, 40, 10);
            p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_EMERGE, .8f, 1f);
        }
        if (s.ready.isEmpty()) { endSession(s, null); return; }
        s.lastActivity = System.currentTimeMillis();
        startFight(s);
    }


    // ---- entry / exit vote ----
    private static final String RAID_NAME = "깊은 어둠";

    private static boolean inPrepRoom(Player p) {
        Location l = p.getLocation();
        return l.getWorld() != null && l.getWorld().getName().equals(DeepDarkMap.WORLD) && l.getZ() < DeepDarkMap.PREP_Z + DeepDarkMap.PREP_HALF + 4;
    }

    /** Y / ready: agree to the running vote, or propose one (lectern in the prep room / bell in the arena). */
    private void voteOrPropose(Player player) {
        Session s = sessionOf(player);
        if (s == null) { say(player, "레이드에 참여 중이 아닙니다. /pipilife raid deepdark"); return; }
        if (s.vote != null) { castVote(player, true); return; }
        if (s.stage == Stage.PREP) startVote(s, player, VoteKind.ENTER);
        else if (s.stage == Stage.FIGHT) say(player, "전투장 입구 쪽의 종을 우클릭하면 퇴장 투표가 시작됩니다.");
        else say(player, "지금은 투표할 수 없습니다.");
    }

    private void startVote(Session s, Player by, VoteKind kind) {
        if (s.vote != null) { castVote(by, true); return; }
        List<Player> voters = new ArrayList<>();
        if (kind == VoteKind.ENTER) {
            if (s.stage != Stage.PREP) { say(by, "준비방에서만 입장 투표를 할 수 있습니다."); return; }
            if (s.countdown >= 0) { say(by, "이미 입장 카운트다운 중입니다."); return; }
            if (!inPrepRoom(by) || by.isDead()) { say(by, "준비방에서만 입장 투표를 할 수 있습니다."); return; }
            // party members who are in the prep room join this attempt
            int cap = Math.max(1, ci("max-players"));
            for (Player m : town.partyOnline(by)) {
                if (s.members.size() >= cap) break;
                if (!s.members.contains(m.getUniqueId()) && inPrepRoom(m) && !m.isDead() && plugin.canTravel(m) && sessionOf(m) == null) {
                    s.members.add(m.getUniqueId());
                    for (Player o : online(s)) say(o, "§d" + m.getName() + "님이 레이드 파티에 합류했습니다.");
                }
            }
            for (Player m : online(s)) if (inPrepRoom(m) && !m.isDead()) voters.add(m);
        } else {
            if (s.stage != Stage.FIGHT || s.fight == null) { say(by, "보스전 중에만 퇴장 투표를 할 수 있습니다."); return; }
            voters.addAll(s.fight.alive());
            if (!voters.contains(by)) { say(by, "전투 중인 인원만 퇴장 투표를 할 수 있습니다."); return; }
        }
        if (voters.isEmpty()) return;
        Vote v = new Vote(kind, Math.max(5, ci("vote-seconds")) * 20);
        for (Player p : voters) { v.voters.add(p.getUniqueId()); v.state.put(p.getUniqueId(), 0); }
        v.state.put(by.getUniqueId(), 1);
        s.vote = v;
        s.lastActivity = System.currentTimeMillis();
        for (Player p : voters) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.2f);
            say(p, "§d[" + RAID_NAME + "] §f" + by.getName() + "님이 " + (kind == VoteKind.ENTER ? "입장" : "퇴장") + " 투표를 시작했습니다. §a찬성 Y §7/ §c반대 N §8(/pipilife raid vote yes|no)");
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
        player.playSound(player.getLocation(), yes ? Sound.BLOCK_SCULK_SENSOR_CLICKING : Sound.BLOCK_SCULK_SENSOR_CLICKING_STOP, 1f, yes ? 1.2f : .8f);
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
            if (p != null) p.sendMessage("PIPI_VOTE:" + (v.kind == VoteKind.ENTER ? "enter" : "leave") + ":" + v.total + ":" + Math.max(0, v.left) + ":" + st + ":" + i + ":" + RAID_NAME);
        }
    }

    private void endVoteHud(Vote v, boolean ok) {
        for (UUID id : v.voters) { Player p = Bukkit.getPlayer(id); if (p != null) p.sendMessage("PIPI_VOTE_END:" + (ok ? 1 : 0)); }
    }

    private void cancelVote(Session s) {
        if (s.vote == null) return;
        endVoteHud(s.vote, false);
        s.vote = null;
    }

    private void voteTick(Session s) {
        Vote v = s.vote;
        if (v == null) return;
        v.age++;
        v.left--;
        // voters that left the raid / went offline drop out of the vote
        boolean changed = v.voters.removeIf(id -> { Player p = Bukkit.getPlayer(id); return p == null || !p.isOnline() || !s.members.contains(id); });
        if (v.voters.isEmpty()) { s.vote = null; if (s.gate) { s.gate = false; endSession(s, null); } return; }
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
        String what = v.kind == VoteKind.ENTER ? "입장" : "퇴장";
        if (!ok) {
            for (Player p : voters) { say(p, "§c" + what + " 투표가 취소되었습니다. §7" + (why == null ? "" : why)); p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, .6f); }
            if (v.kind == VoteKind.ENTER && s.gate) { s.gate = false; endSession(s, null); } // nobody moved yet: just drop the reservation
            return;
        }
        if (v.kind == VoteKind.ENTER && s.gate) {
            s.gate = false;
            gateEnter(s, voters);
        } else if (v.kind == VoteKind.ENTER) {
            if (s.stage != Stage.PREP) return;
            s.ready.clear();
            s.ready.addAll(v.voters);
            s.countdown = Math.max(1, ci("countdown-seconds")) * 20;
            for (Player p : voters) say(p, "§a전원 찬성! §f" + s.countdown / 20 + "초 뒤 보스전이 시작됩니다.");
        } else {
            if (s.stage != Stage.FIGHT) return;
            retreat(s);
        }
    }

    private void leaveCmd(Player player) {
        Session s = sessionOf(player);
        if (s == null) {
            if (player.getWorld().getName().equals(DeepDarkMap.WORLD)) goHome(player); else say(player, "레이드에 참여 중이 아닙니다.");
            return;
        }
        removeMember(s, player);
        goHome(player);
        say(player, "레이드에서 나왔습니다.");
    }

    private void status(Player player) {
        Session s = session;
        if (s == null) { say(player, "딥 다크 시티: 현재 진행 중인 레이드가 없습니다."); return; }
        List<String> names = new ArrayList<>();
        for (Player p : online(s)) names.add(p.getName() + (s.ready.contains(p.getUniqueId()) ? "(입장)" : "") + (s.dead.contains(p.getUniqueId()) ? "(사망)" : ""));
        say(player, "딥 다크 시티 · " + (s.stage == Stage.PREP ? "준비 중" : s.stage == Stage.FIGHT ? "전투 중" : "클리어") + " · " + String.join(", ", names));
    }

    // ---- session lifecycle ----
    private void removeMember(Session s, Player p) {
        unGhost(p);
        s.members.remove(p.getUniqueId());
        s.deathAt.remove(p.getUniqueId());
        s.ready.remove(p.getUniqueId());
        s.dead.remove(p.getUniqueId());
        if (s.fight != null) s.fight.bar.removePlayer(p);
        p.sendMessage("PIPI_BOSS_END");
        if (s.vote != null && s.vote.voters.remove(p.getUniqueId())) p.sendMessage("PIPI_VOTE_END:0");
        p.removePotionEffect(PotionEffectType.DARKNESS);
        if (s.members.isEmpty()) { endSession(s, null); return; }
        if (p.getUniqueId().equals(s.leader)) s.leader = s.members.iterator().next();
        s.countdown = -1;
    }

    private void endSession(Session s, String message) {
        if (session == s) session = null;
        cancelVote(s);
        if (s.fight != null) { s.fight.cleanup(); s.fight = null; }
        for (UUID id : new ArrayList<>(s.members)) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) continue;
            unGhost(p);
            if (message != null) say(p, message);
            if (p.getWorld().getName().equals(DeepDarkMap.WORLD) && !p.isDead()) goHome(p);
        }
        s.members.clear();
    }

    private void tick() {
        tick++;
        RaidAuction a = auction;
        if (a != null) {
            try { a.tick(); } catch (RuntimeException ex) { plugin.getLogger().severe("raid auction tick failed: " + ex); ex.printStackTrace(); auction = null; }
        }
        Session s = session;
        if (s == null) return;
        try {
            voteTick(s);
            if (session != s) return;
            switch (s.stage) {
                case PREP -> prepTick(s);
                case FIGHT -> { if (s.fight != null) s.fight.tick(); }
                case CLEARED -> {
                    if (auction != null && !auction.done() && auction.involves(s.members)) { // wait for the loot auction
                        if (tick % 100 == 0) for (Player p : online(s)) bar(p, "§7전리품 경매가 끝나면 거주지로 이동합니다 (/pipilife raid leave 로 바로 이동)");
                    } else if (--s.clearTimer <= 0) endSession(s, "§7레이드가 종료되어 거주지로 돌아갑니다.");
                    else if (s.clearTimer % 100 == 0) for (Player p : online(s)) bar(p, "§7" + s.clearTimer / 20 + "초 뒤 거주지로 이동합니다 (/pipilife raid leave 로 바로 이동)");
                }
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().severe("deepdark raid tick failed: " + ex);
            ex.printStackTrace();
            endSession(s, "§c레이드 처리 중 오류가 발생해 종료되었습니다.");
        }
    }

    private void prepTick(Session s) {
        List<Player> on = online(s);
        if (on.isEmpty()) { endSession(s, null); return; }
        if (System.currentTimeMillis() - s.lastActivity > ci("prep-timeout-minutes") * 60_000L) { endSession(s, "§e준비 시간이 지나 레이드가 종료되었습니다."); return; }
        if (s.countdown < 0) {
            if (tick % 20 == 0 && s.vote == null) for (Player p : on) bar(p, "§5딥 다크 시티 §f파티 " + on.size() + "명 §7· 렉턴 우클릭 = 입장 투표");
            return;
        }
        // an entering member left the prep room / went offline: abort the countdown
        s.ready.removeIf(id -> { Player p = Bukkit.getPlayer(id); return p == null || !p.isOnline() || p.isDead() || !inPrepRoom(p); });
        if (s.ready.isEmpty()) { s.countdown = -1; return; }
        if (s.countdown % 20 == 0 && s.countdown > 0) for (Player p : on) if (s.ready.contains(p.getUniqueId())) { p.sendTitle("§e" + s.countdown / 20, "§7보스전이 곧 시작됩니다", 0, 22, 0); p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1f, 1f); }
        if (s.countdown-- <= 0) startFight(s);
    }

    private void startFight(Session s) {
        World w = world();
        if (w == null) { endSession(s, "§c맵을 열지 못했습니다."); return; }
        List<Player> on = new ArrayList<>();
        for (Player p : online(s)) if (p.getWorld().equals(w) && !p.isDead() && (s.ready.isEmpty() || s.ready.contains(p.getUniqueId()))) on.add(p);
        if (on.isEmpty()) { s.countdown = -1; s.ready.clear(); return; }
        cancelVote(s);
        try { DeepDarkMap.ensureArenaBell(w); } catch (RuntimeException ex) { plugin.getLogger().warning("arena bell: " + ex); }
        s.stage = Stage.FIGHT;
        s.countdown = -1;
        s.dead.clear();
        s.deathAt.clear();
        for (UUID id : s.members) { Player m = Bukkit.getPlayer(id); if (m == null || !on.contains(m)) s.dead.add(id); } // absent / dead at the start: sit this attempt out
        s.ready.clear();
        int n = on.size();
        for (int i = 0; i < n; i++) {
            Player p = on.get(i);
            p.teleport(new Location(w, DeepDarkMap.ACX + (i - (n - 1) / 2.0) * 3, DeepDarkMap.FLOOR + 1, 30.5, 180, 0));
            p.setFallDistance(0);
            heal(p);
        }
        s.fight = new Fight(s, w, n);
    }

    /** 퇴장 투표 통과: the fight resets and everyone returns to the prep room. */
    private void retreat(Session s) {
        if (s.fight != null) { s.fight.cleanup(); s.fight = null; }
        s.stage = Stage.PREP;
        s.ready.clear();
        s.dead.clear();
        s.countdown = -1;
        s.lastActivity = System.currentTimeMillis();
        List<Player> on = online(s);
        if (on.isEmpty()) { endSession(s, null); return; }
        s.deathAt.clear();
        for (Player p : on) {
            unGhost(p);
            if (p.isDead()) continue;
            toPrep(p);
            p.sendTitle("§e퇴장", "§7파티 전원이 퇴장에 동의했습니다", 5, 40, 10);
            say(p, "§e파티가 보스전에서 퇴장했습니다. §7(전투 초기화 · 렉턴으로 다시 도전하거나 종/ /pipilife raid leave 로 나가기)");
        }
    }

    private void fail(Session s, String reason) {
        cancelVote(s);
        if (s.fight != null) { s.fight.cleanup(); s.fight = null; }
        s.stage = Stage.PREP;
        s.ready.clear();
        s.dead.clear();
        s.countdown = -1;
        s.lastActivity = System.currentTimeMillis();
        List<Player> on = online(s);
        if (on.isEmpty()) { endSession(s, null); return; }
        s.deathAt.clear();
        for (Player p : on) {
            unGhost(p); // spectators come back to life: survival, visible, full health
            if (p.isDead()) continue; // dead players respawn into the prep room via the respawn handler
            toPrep(p);
            p.sendTitle("§c토벌 실패", "§7" + reason, 5, 50, 10);
            say(p, "§c토벌 실패: " + reason + " §7(준비방에서 다시 도전하거나 /pipilife raid leave)");
        }
    }

    // ---- listeners ----
    @EventHandler(priority = EventPriority.HIGHEST)
    public void bossDamaged(EntityDamageEvent e) {
        Entity v = e.getEntity();
        if (!v.getScoreboardTags().contains(TAG)) return;
        Session s = session;
        Fight f = s == null ? null : s.fight;
        if (f == null || f.boss != v) { e.setCancelled(true); return; }
        Player p = null;
        if (e instanceof EntityDamageByEntityEvent by) {
            Entity d = by.getDamager();
            if (d instanceof Projectile pr && pr.getShooter() instanceof Entity sh) d = sh;
            if (d instanceof Player pp) p = pp;
        }
        if (p == null || !f.canHit(p)) { e.setCancelled(true); return; }
        double dmg = e.getFinalDamage();
        e.setDamage(.001);
        f.damage(p, dmg);
    }

    /** Where/until when (ms) item and XP-orb spawns around the dying raid boss are suppressed (vanilla loot / XP that bypass the death event). */
    private Location bossDropGuardAt;
    private long bossDropGuardUntil;

    private void guardBossDrops(Location at) {
        bossDropGuardAt = at == null ? null : at.clone();
        bossDropGuardUntil = System.currentTimeMillis() + 2000;
    }

    private boolean bossDropGuarded(Location at) {
        Location g = bossDropGuardAt;
        if (g == null || at == null || System.currentTimeMillis() > bossDropGuardUntil) return false;
        return at.getWorld() != null && at.getWorld().equals(g.getWorld()) && at.distanceSquared(g) <= 24 * 24;
    }

    @EventHandler(priority = EventPriority.HIGHEST) public void bossDeath(EntityDeathEvent e) {
        if (!e.getEntity().getScoreboardTags().contains(TAG)) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
        guardBossDrops(e.getEntity().getLocation());
    }

    /** Re-clear after every other listener (e.g. loot plugins) so the raid boss never drops anything. */
    @EventHandler(priority = EventPriority.MONITOR) public void bossDeathFinal(EntityDeathEvent e) {
        if (!e.getEntity().getScoreboardTags().contains(TAG)) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
    }

    @EventHandler(priority = EventPriority.HIGHEST) public void bossLootItem(org.bukkit.event.entity.ItemSpawnEvent e) {
        if (bossDropGuarded(e.getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST) public void bossLootXp(org.bukkit.event.entity.EntitySpawnEvent e) {
        if (e.getEntity() instanceof ExperienceOrb && bossDropGuarded(e.getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void playerDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        Session s = sessionOf(p);
        if (s == null || s.stage != Stage.FIGHT || !p.getWorld().getName().equals(DeepDarkMap.WORLD)) return;
        s.dead.add(p.getUniqueId());
        s.deathAt.put(p.getUniqueId(), p.getLocation().clone());
        for (Player m : online(s)) say(m, "§c" + p.getName() + "님이 쓰러졌습니다.");
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline() && p.isDead()) p.spigot().respawn(); }, 30L);
    }

    /**
     * Called first by PipiLife's respawn handler. A participant who died in the arena while party members are still fighting
     * comes back where they fell as a spectator ({@link #makeGhost}); otherwise (wipe / not fighting) in the prep room.
     */
    boolean respawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        Session s = sessionOf(p);
        if (s == null) return false;
        World w = world();
        if (w == null) return false;
        Fight f = s.fight;
        Location died = s.deathAt.get(p.getUniqueId());
        if (s.stage == Stage.FIGHT && f != null && f.st != St.END && s.dead.contains(p.getUniqueId()) && died != null
            && died.getWorld() == w && inArena(died) && !f.alive().isEmpty()) {
            Location at = died.getY() < DeepDarkMap.FLOOR - 4 ? arenaSpawn(w) : died.clone();
            if (at.getY() < DeepDarkMap.FLOOR + 1) at.setY(DeepDarkMap.FLOOR + 1);
            event.setRespawnLocation(at);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!p.isOnline() || p.isDead()) return;
                Session cur = sessionOf(p);
                if (cur == s && s.stage == Stage.FIGHT && s.fight == f && s.dead.contains(p.getUniqueId())) {
                    makeGhost(p);
                    say(p, "§7관전 중입니다.");
                } else if (cur != null) toPrep(p);
                else if (p.getWorld().getName().equals(DeepDarkMap.WORLD)) goHome(p);
            });
            return true;
        }
        event.setRespawnLocation(DeepDarkMap.prepSpawn(w));
        return true;
    }

    @EventHandler public void quit(PlayerQuitEvent e) {
        Session s = sessionOf(e.getPlayer());
        if (s != null) removeMember(s, e.getPlayer());
        unGhost(e.getPlayer());
    }

    @EventHandler public void joinGhost(org.bukkit.event.player.PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (ghost(p)) unGhost(p); // left over from a crash / unclean stop
        RaidAuction a = auction;
        if (a != null && !a.done() && a.member(p.getUniqueId()))
            Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline() && auction == a && !a.done()) a.open(p); }, 60L);
        for (Player o : Bukkit.getOnlinePlayers()) if (o != p && ghost(o)) p.hidePlayer(plugin, o);
    }

    // spectators: never hurt, never hurt anything, never pick up / drop / interact / get targeted
    @EventHandler(priority = EventPriority.LOWEST)
    public void ghostDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && ghost(p)) { e.setCancelled(true); return; }
        if (e instanceof EntityDamageByEntityEvent by) {
            Entity d = by.getDamager();
            if (d instanceof Projectile pr && pr.getShooter() instanceof Entity sh) d = sh;
            if (d instanceof Player pp && ghost(pp)) e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void ghostInteract(PlayerInteractEvent e) { if (ghost(e.getPlayer())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void ghostInteractEntity(org.bukkit.event.player.PlayerInteractEntityEvent e) { if (ghost(e.getPlayer())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void ghostPickup(org.bukkit.event.entity.EntityPickupItemEvent e) { if (e.getEntity() instanceof Player p && ghost(p)) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void ghostDrop(org.bukkit.event.player.PlayerDropItemEvent e) { if (ghost(e.getPlayer())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void ghostTarget(EntityTargetEvent e) { if (e.getTarget() instanceof Player p && ghost(p)) e.setCancelled(true); }

    @EventHandler public void changedWorld(PlayerChangedWorldEvent e) {
        Session s = sessionOf(e.getPlayer());
        if (s != null && !e.getPlayer().getWorld().getName().equals(DeepDarkMap.WORLD)) removeMember(s, e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void spawnLocation(org.spigotmc.event.player.PlayerSpawnLocationEvent e) {
        Location at = e.getSpawnLocation();
        if (at.getWorld() == null || !at.getWorld().getName().equals(DeepDarkMap.WORLD)) return;
        if (!plugin.canTravel(e.getPlayer())) return;
        Location home = plugin.homeFor(e.getPlayer());
        if (home != null) e.setSpawnLocation(home);
    }

    @EventHandler public void worldLoad(WorldLoadEvent e) {
        if (e.getWorld().getName().equals(DeepDarkMap.WORLD)) DeepDarkMap.rules(e.getWorld());
    }

    private static boolean inWorld(Block b) { return b != null && b.getWorld().getName().equals(DeepDarkMap.WORLD); }
    private static boolean builder(Player p) { return p.getGameMode() == GameMode.CREATIVE; }

    @EventHandler(priority = EventPriority.HIGHEST) public void noBreak(BlockBreakEvent e) { if (inWorld(e.getBlock()) && !builder(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void noPlace(BlockPlaceEvent e) { if (inWorld(e.getBlock()) && !builder(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void noBucket(PlayerBucketEmptyEvent e) { if (inWorld(e.getBlock()) && !builder(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void noExplode(EntityExplodeEvent e) { if (e.getLocation().getWorld().getName().equals(DeepDarkMap.WORLD)) e.blockList().clear(); }
    @EventHandler(priority = EventPriority.HIGHEST) public void noBlockExplode(BlockExplodeEvent e) { if (inWorld(e.getBlock())) e.blockList().clear(); }
    /** Sculk sensors / shriekers are decor only: they never receive vibrations. */
    @EventHandler(priority = EventPriority.HIGHEST) public void noVibration(BlockReceiveGameEvent e) { if (inWorld(e.getBlock())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGH)
    public void interact(PlayerInteractEvent e) {
        Block b = e.getClickedBlock();
        if (b == null || e.getAction() != Action.RIGHT_CLICK_BLOCK || !inWorld(b)) return;
        if (ghost(e.getPlayer())) { e.setCancelled(true); return; } // spectators cannot use the lectern / bell
        if (b.getType() == Material.LECTERN) {
            e.setCancelled(true);
            if (e.getHand() == EquipmentSlot.HAND) {
                Session s = sessionOf(e.getPlayer());
                if (s == null) say(e.getPlayer(), "레이드에 참여 중이 아닙니다. /pipilife raid deepdark");
                else if (s.vote != null) castVote(e.getPlayer(), true);
                else if (s.stage == Stage.PREP) startVote(s, e.getPlayer(), VoteKind.ENTER);
            }
        } else if (b.getType() == Material.BELL) {
            e.setCancelled(true);
            if (e.getHand() != EquipmentSlot.HAND) return;
            Player p = e.getPlayer();
            boolean arena = Math.hypot(b.getX() + .5 - DeepDarkMap.ACX, b.getZ() + .5 - DeepDarkMap.ACZ) <= DeepDarkMap.R_OUT + 2;
            Session s = sessionOf(p);
            if (arena && s != null && s.stage == Stage.FIGHT) {
                if (s.vote != null) castVote(p, true); else startVote(s, p, VoteKind.LEAVE);
            } else leaveCmd(p);
        }
    }

    // ---- vanilla warden mechanics off: no darkness (any source unless the raid itself pulses it), no anger / targeting / vibrations ----
    private boolean applyingDarkness;

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void noDarkness(EntityPotionEffectEvent e) {
        PotionEffect ne = e.getNewEffect();
        if (ne == null || !ne.getType().equals(PotionEffectType.DARKNESS)) return;
        if (e.getCause() == EntityPotionEffectEvent.Cause.WARDEN || (e.getEntity().getWorld().getName().equals(DeepDarkMap.WORLD) && !applyingDarkness)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void noBossTarget(EntityTargetEvent e) {
        if (e.getEntity().getScoreboardTags().contains(TAG)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void noGameEvents(GenericGameEvent e) {
        Location l = e.getLocation();
        if (l != null && l.getWorld() != null && l.getWorld().getName().equals(DeepDarkMap.WORLD)) e.setCancelled(true);
    }

    /** /pipilife maps repairdeepdark: adds the arena exit bell + label to an already-built map. */
    void repair(Player player) {
        World w = world();
        if (w == null) { say(player, "§c딥 다크 맵이 없습니다. /pipilife maps builddeepdark"); return; }
        boolean changed = DeepDarkMap.ensureArenaBell(w);
        say(player, "§a전투장 나가기 종을 " + (changed ? "설치" : "확인") + "했습니다. §7(" + DeepDarkMap.BELL_X + ", " + (DeepDarkMap.FLOOR + 2) + ", " + DeepDarkMap.BELL_Z + ")");
    }

    // ---- loot auction ----
    /** /pipilife auction open | bid <amount> | instant */
    boolean auctionCommand(Player player, String[] args) {
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "open";
        RaidAuction a = auction;
        if (a == null || a.done()) { say(player, "진행 중인 전리품 경매가 없습니다."); return true; }
        switch (sub) {
            case "bid" -> {
                long amount;
                try { amount = Long.parseLong(args.length > 2 ? args[2].replace(",", "") : ""); }
                catch (NumberFormatException ex) { player.sendMessage("PIPI_AUC_MSG:입찰가를 숫자로 입력하세요."); return true; }
                a.bid(player, amount);
            }
            case "instant" -> a.instant(player);
            default -> a.open(player);
        }
        return true;
    }

    private void startAuction(Session s) {
        List<Player> on = online(s);
        if (on.isEmpty()) return;
        if (auction != null && !auction.done()) auction.abort();
        boolean solo = on.size() < 2;
        long first = solo ? ci("auction.solo-start-first") : ci("auction.start"), other = solo ? ci("auction.solo-start-other") : ci("auction.start");
        List<RaidAuction.Lot> lots = new ArrayList<>();
        lots.add(new RaidAuction.Lot("워든의 영혼석", town.raidItem("soul", Math.max(1, ci("reward.soul-stone"))), Math.max(1, first)));
        lots.add(new RaidAuction.Lot("스킬북 · 깊은 어둠", ClassSkills.skillBook(plugin, "deepdark"), Math.max(1, other)));
        auction = new RaidAuction(plugin, RAID_NAME, on, lots, solo, ci("auction.step"), ci("auction.lot-seconds") * 1000L,
            ci("auction.extend-seconds") * 1000L, ci("auction.gap-seconds") * 1000L);
        auction.start();
    }

    /** Another raid's loot auction (통곡의 왕좌) on this auction slot, window and timers; waits while one is still running. */
    void startLootAuction(String raidName, List<Player> on, List<RaidAuction.Lot> lots, long tries) {
        List<Player> live = on.stream().filter(Player::isOnline).toList();
        if (live.isEmpty() || tries > 60) return;
        if (auction != null && !auction.done()) { Bukkit.getScheduler().runTaskLater(plugin, () -> startLootAuction(raidName, live, lots, tries + 1), 100L); return; }
        auction = new RaidAuction(plugin, raidName, live, lots, live.size() < 2, ci("auction.step"), ci("auction.lot-seconds") * 1000L,
            ci("auction.extend-seconds") * 1000L, ci("auction.gap-seconds") * 1000L);
        auction.start();
    }

    // ---- shutdown ----
    void shutdown() {
        if (auction != null && !auction.done()) { try { auction.abort(); } catch (RuntimeException ignored) { } }
        Session s = session;
        session = null;
        if (s != null) {
            if (s.fight != null) { s.fight.cleanup(); s.fight = null; }
            for (UUID id : s.members) { Player p = Bukkit.getPlayer(id); if (p != null) unGhost(p); }
            for (UUID id : s.members) { Player p = Bukkit.getPlayer(id); if (p != null && p.getWorld().getName().equals(DeepDarkMap.WORLD) && !p.isDead()) goHome(p); }
        }
        for (Player p : Bukkit.getOnlinePlayers()) unGhost(p);
        World w = Bukkit.getWorld(DeepDarkMap.WORLD);
        if (w != null) for (Entity e : w.getEntities()) if (e.getScoreboardTags().contains(TAG) || e.getScoreboardTags().contains("pipi_raid_orb")) e.remove();
    }

    // ---- rewards ----
    private void give(Player p, ItemStack it) {
        if (it == null) return;
        int left = it.getAmount(), max = Math.max(1, Math.min(64, it.getMaxStackSize()));
        while (left > 0) {
            ItemStack c = it.clone();
            c.setAmount(Math.min(max, left));
            left -= c.getAmount();
            for (ItemStack extra : p.getInventory().addItem(c).values()) p.getWorld().dropItemNaturally(p.getLocation(), extra).setOwner(p.getUniqueId());
        }
    }

    /** {min, max} from two config keys, swapped if reversed and at least 1. */
    private int[] accessoryRange(String minKey, String maxKey) {
        int a = Math.max(1, ci(minKey)), b = Math.max(1, ci(maxKey));
        return new int[] { Math.min(a, b), Math.max(a, b) };
    }


    private void rewards(Session s) {
        for (Player p : online(s)) rewardPlayer(p);
    }

    /** 관리자 테스트: 클리어 보상 그대로 지급 (loot = 경매품인 영혼석·스킬북도 같이). 클리어 기록은 건드리지 않음. */
    void adminReward(Player p, boolean loot) {
        rewardPlayer(p);
        if (!loot) return;
        give(p, town.raidItem("soul", Math.max(1, ci("reward.soul-stone"))));
        give(p, ClassSkills.skillBook(plugin, "deepdark"));
        say(p, "§6[경매품] §f워든의 영혼석 " + Math.max(1, ci("reward.soul-stone")) + " · 스킬북 · 깊은 어둠");
    }

    private void rewardPlayer(Player p) {
        long gold = Math.max(0, plugin.getConfig().getLong(P + "reward.gold"));
        {
            try {
                var sc = town.gold(p);
                sc.setScore((int) Math.min(Integer.MAX_VALUE, sc.getScore() + gold));
                give(p, town.raidItem("plate", ci("reward.steel")));
                give(p, town.levelVoucher(ci("reward.level-vouchers")));
                say(p, "§6[클리어 보상] §f골드 " + fmt(gold) + " · 강철 " + ci("reward.steel") + " · 레벨 +1 교환권 " + ci("reward.level-vouchers"));
            } catch (RuntimeException ex) {
                plugin.getLogger().severe("raid reward failed for " + p.getName() + ": " + ex);
                say(p, "§c보상 지급 중 오류가 발생했습니다. 관리자에게 문의하세요.");
            }
            try { giveAccessories(p); } catch (RuntimeException ex) {
                plugin.getLogger().severe("raid accessory reward failed for " + p.getName() + ": " + ex);
                say(p, "§c장신구 지급 중 오류가 발생했습니다. 관리자에게 문의하세요.");
            }
        }
    }

    /** 1단계 장신구 count-min~count-max 개 (동일 확률), 각각 부위·옵션·수치 랜덤. */
    private void giveAccessories(Player p) {
        int[] count = accessoryRange("reward.accessory-count-min", "reward.accessory-count-max");
        int[] stat = accessoryRange("reward.accessory-stat-min", "reward.accessory-stat-max");
        int n = ThreadLocalRandom.current().nextInt(count[0], count[1] + 1);
        List<String> got = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ItemStack it = Accessories.tier1(RAID_NAME, stat[0], stat[1]);
            if (it == null) return;
            give(p, it);
            List<String> lore = it.getItemMeta().getLore();
            got.add(it.getItemMeta().getDisplayName() + " §7(" + (lore != null && lore.size() > 1 ? lore.get(1).replaceAll("§.", "").replaceFirst("^\\[[^]]+] ", "") : "") + ")");
        }
        say(p, "§6[클리어 보상] §f1단계 장신구 " + n + "개");
        for (String g : got) p.sendMessage("  §8- " + g);
    }

    // =====================================================================================
    //                                    THE FIGHT
    // =====================================================================================
    private static final class Pillar { final int cx, cz; boolean alive; int layers; Pillar(int cx, int cz) { this.cx = cx; this.cz = cz; } }
    private record Orb(BlockDisplay d, double x, double y, double z) {}

    private final class Fight {
        final Session s; final World w; final int n;
        final Warden boss; final BossBar bar;
        double hp, max;
        St st = St.INTRO;
        int t, attackTimer, darkTimer, atkType, atkT, heartN, heartEnd, stagger, sonicTotal, lastBarPct = -1;
        long fightTick;
        boolean p2Done, enraged, groggy, hidden;
        /** 70% gate: gateDone once it triggered; firstRun while that first charge pattern runs (boss invulnerable until groggy). */
        boolean gateDone, firstRun;
        /** 분노 게이지 0..100: active once the first charge pattern ended; every hit adds rage.per-hit; at 100 the charge pattern runs again. */
        boolean rageOn;
        double rage;
        double bx = DeepDarkMap.ACX, bz = DeepDarkMap.ACZ, yaw, atkX, atkZ, atkYaw;
        double lastX = Double.NaN, lastZ, lastYaw; boolean lastHidden;
        final Map<UUID, Long> noise = new HashMap<>();
        final List<Pillar> pillars = new ArrayList<>();
        final List<Orb> orbs = new ArrayList<>();
        /** charge pattern: CHARGES straight charges per pattern; blocked = how many of them ended on a pillar. */
        static final int CHARGES = 4;
        int blocked, charges;
        Player target;
        double dx, dz, travelled;
        /** players already hit by the current charge (each player is damaged at most once per charge) */
        final Set<UUID> chargeHit = new HashSet<>();
        /** phase-2 gate: HP stops at 35% until the phase pattern resolves (surviving the big sonic). */
        boolean p2Released;
        String lastHud;
        /** red ground telegraphs (flat full-bright red BlockDisplays), grouped by attack */
        final Map<String, List<BlockDisplay>> marks = new HashMap<>();
        BlockDisplay chargeMark;
        final ThreadLocalRandom rnd = ThreadLocalRandom.current();

        Fight(Session s, World w, int n) {
            this.s = s; this.w = w; this.n = n;
            max = Math.max(1000, cd("hp.base") + cd("hp.per-extra-player") * (n - 1));
            hp = max;
            Location at = new Location(w, bx, DeepDarkMap.FLOOR + 1, bz, 0, 0);
            boss = w.spawn(at, Warden.class, e -> {
                e.setAI(false);
                e.setPersistent(false);
                e.setRemoveWhenFarAway(false);
                e.setSilent(true);
                e.setCollidable(false);
                e.setGravity(false);
                e.addScoreboardTag(TAG);
                e.addScoreboardTag("pipi_boss");
                e.setCustomName("§5§l심연의 감시자 워든");
                e.setCustomNameVisible(false);
                var sc = e.getAttribute(Attribute.GENERIC_SCALE);
                if (sc != null) sc.setBaseValue(Math.clamp(cd("scale"), 1.0, 8.0));
                var kb = e.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE);
                if (kb != null) kb.setBaseValue(1.0);
                try { e.setAware(false); } catch (RuntimeException | LinkageError ignored) { }
            });
            bar = Bukkit.createBossBar("§5§l심연의 감시자 워든", BarColor.PURPLE, BarStyle.SEGMENTED_20);
            if (cb("vanilla-bossbar")) for (Player p : online(s)) bar.addPlayer(p);
            refreshBar();
        }

        // -------- red ground telegraphs --------
        /** One flat red plate: local rect x∈[offX, offX+sx], z∈[offZ, offZ+sz] in the frame rotated by yaw (local +z = facing) around (ox, oz). */
        BlockDisplay plate(double ox, double oz, double yawDeg, double offX, double offZ, double sx, double sz) {
            float th = (float) -Math.toRadians(yawDeg);
            Quaternionf rot = new Quaternionf().rotateY(th);
            Vector3f tr = rot.transform(new Vector3f((float) offX, 0, (float) offZ));
            return w.spawn(new Location(w, ox, groundY() + .015, oz), BlockDisplay.class, d -> {
                d.setBlock(Material.RED_CONCRETE.createBlockData());
                d.setPersistent(false);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setViewRange(2f);
                d.setShadowRadius(0);
                d.addScoreboardTag(TAG);
                d.setTransformation(new Transformation(tr, rot, new Vector3f((float) sx, .02f, (float) sz), new Quaternionf()));
            });
        }

        List<BlockDisplay> mark(String key) { return marks.computeIfAbsent(key, k -> new ArrayList<>()); }

        void clearMark(String key) {
            List<BlockDisplay> list = marks.remove(key);
            if (list != null) for (BlockDisplay d : list) if (d.isValid()) d.remove();
        }

        /** Filled disc as 0.5-wide strips. */
        void markDisc(String key, double cx, double cz, double r, double strip) {
            List<BlockDisplay> list = mark(key);
            for (double x = -r; x < r - 1e-6; x += strip) {
                double xc = x + strip / 2, hl = Math.sqrt(Math.max(0, r * r - xc * xc));
                if (hl < .05) continue;
                list.add(plate(cx, cz, 0, x, -hl, strip, 2 * hl));
            }
        }

        /** Filled cone (sector) from (ox, oz) as a fan of rotated strips, plus the close-range disc around the boss. */
        void markCone(String key, double ox, double oz, double yawDeg, double len, double half, double closeR) {
            List<BlockDisplay> list = mark(key);
            double step = 6, width = 2 * len * Math.tan(Math.toRadians(step / 2)) + .25;
            for (double a = -half; a <= half + 1e-6; a += step) list.add(plate(ox, oz, yawDeg + a, -width / 2, 0, width, len));
            if (closeR > 0) markDisc(key, ox, oz, closeR, .5);
        }

        /** Distance from (x, z) along (ux, uz) to the arena wall. */
        static double toWall(double x, double z, double ux, double uz) {
            double px = x - DeepDarkMap.ACX, pz = z - DeepDarkMap.ACZ, b = px * ux + pz * uz, c = px * px + pz * pz - DeepDarkMap.R_IN * DeepDarkMap.R_IN;
            double disc = b * b - c;
            return disc < 0 ? 0 : -b + Math.sqrt(disc);
        }

        /** The charge lane: one plate re-aimed every tick (from the boss to the wall, as wide as the warden's hit box). */
        void aimChargeMark() {
            double width = 2 * (hitR() + .6), len = Math.max(1, Math.min(64, toWall(bx, bz, dx, dz)));
            float th = (float) -Math.atan2(-dx, dz);
            Quaternionf rot = new Quaternionf().rotateY(th);
            Vector3f tr = rot.transform(new Vector3f((float) (-width / 2), 0, 0));
            Transformation tf = new Transformation(tr, rot, new Vector3f((float) width, .02f, (float) len), new Quaternionf());
            if (chargeMark == null || !chargeMark.isValid()) {
                chargeMark = plate(bx, bz, 0, 0, 0, 1, 1);
                try { chargeMark.setTeleportDuration(1); } catch (RuntimeException | LinkageError ignored) { }
            } else chargeMark.teleport(new Location(w, bx, groundY() + .015, bz));
            chargeMark.setTransformation(tf);
        }

        void clearChargeMark() {
            if (chargeMark != null && chargeMark.isValid()) chargeMark.remove();
            chargeMark = null;
        }

        void clearAllMarks() {
            for (String k : new ArrayList<>(marks.keySet())) clearMark(k);
            clearChargeMark();
        }

        // -------- client boss HUD (PipiTravel) --------
        boolean patternLock() { return p2Done && !p2Released; }

        /** The rage (charge / pillar / groggy) pattern is running. */
        boolean chargePattern() {
            return st == St.STOMP || st == St.PILLARS || st == St.WINDUP || st == St.CHARGE || st == St.STAGGER || st == St.GROGGY;
        }

        /** Rage shown on the HUD (-1 = hidden until the first charge pattern has ended). */
        int rageHud() { return st != St.END && rageOn ? (int) Math.floor(Math.clamp(rage, 0, 100)) : -1; }

        void syncHud(boolean force) {
            // name|hp|max|gates(none)|released|locked|label(none)|rage - thresholds / phases are never revealed
            String text = "PIPI_BOSS:심연의 감시자 워든|" + (long) Math.ceil(Math.max(0, hp)) + "|" + (long) max + "||0|0||" + rageHud();
            if (!force && text.equals(lastHud)) return;
            lastHud = text;
            for (Player p : online(s)) p.sendMessage(text);
        }

        double scale() { var a = boss.getAttribute(Attribute.GENERIC_SCALE); return a == null ? 1 : a.getValue(); }
        double hitR() { return .6 * scale(); }
        double gateHp() { return max * cd("threshold.gate") / 100.0; }
        double p2Hp() { return max * cd("threshold.p2") / 100.0; }
        double groundY() { return DeepDarkMap.FLOOR + 1; }

        /** Raid spectators currently in the arena world. */
        List<Player> ghosts() {
            List<Player> out = new ArrayList<>();
            for (Player p : online(s)) if (ghost(p) && !p.isDead() && p.getWorld().equals(w)) out.add(p);
            return out;
        }

        List<Player> alive() {
            List<Player> out = new ArrayList<>();
            for (UUID id : s.members) {
                Player p = Bukkit.getPlayer(id);
                if (p == null || !p.isOnline() || p.isDead() || s.dead.contains(id) || !p.getWorld().equals(w)) continue;
                if (p.getGameMode() == GameMode.SPECTATOR || ghost(p)) continue;
                out.add(p);
            }
            return out;
        }

        boolean canHit(Player p) {
            return (st == St.NORMAL || st == St.STOMP || st == St.PILLARS || st == St.WINDUP || st == St.CHARGE || st == St.STAGGER || st == St.GROGGY)
                && !hidden && alive().contains(p);
        }

        void damage(Player p, double dmg) {
            double mult = groggy ? cd("groggy.damage-multiplier") : 1;
            double real = dmg * mult;
            noise.put(p.getUniqueId(), fightTick);
            if (patternLock()) return; // 35% gate: HP stays frozen (silently) until the phase-2 pattern resolves
            if (chargePattern() && !groggy) return; // every charge pattern (70% gate and rage repeats): invulnerable until the groggy starts
            double floor = !p2Done ? p2Hp() : 0;
            if (!gateDone) floor = Math.max(floor, gateHp());
            double before = hp;
            hp = Math.max(floor, hp - real);
            if (before - hp >= 1) numbers.show(boss, before - hp);
            // rage: every damage instance after the first charge pattern, except while a charge pattern itself runs
            if (rageOn && !chargePattern() && rage < 100) rage = Math.min(100, rage + Math.max(0, cd("rage.per-hit")));
            if (hp <= 0) win();
        }

        // -------- movement & presentation --------
        void place() {
            if (lastX == bx && lastZ == bz && lastYaw == yaw && lastHidden == hidden) return;
            lastX = bx; lastZ = bz; lastYaw = yaw; lastHidden = hidden;
            boss.teleport(new Location(w, bx, hidden ? DeepDarkMap.FLOOR - 18 : groundY(), bz, (float) yaw, 0));
        }

        void go(St next) { st = next; t = 0; }

        void face(double x, double z) { yaw = yawTo(bx, bz, x, z); }

        Player nearest(List<Player> list) {
            Player best = null; double bd = Double.MAX_VALUE;
            for (Player p : list) { double d = Math.hypot(p.getLocation().getX() - bx, p.getLocation().getZ() - bz); if (d < bd) { bd = d; best = p; } }
            return best;
        }

        Player random(List<Player> list) { return list.isEmpty() ? null : list.get(rnd.nextInt(list.size())); }

        /** 진동 감지: the player who most recently made noise (hit the boss / sprinted), else a random player. */
        Player noisiest(List<Player> list) {
            Player best = null; long bt = fightTick - 100;
            for (Player p : list) { Long at = noise.get(p.getUniqueId()); if (at != null && at > bt) { bt = at; best = p; } }
            return best != null ? best : random(list);
        }

        void ring(double cx, double cz, double r, Color c, float size) {
            int pts = (int) Math.max(16, Math.min(72, r * 5));
            for (int i = 0; i < pts; i++) {
                double a = i * 2 * Math.PI / pts;
                dust(w, cx + Math.cos(a) * r, groundY() + .15, cz + Math.sin(a) * r, c, size);
            }
        }

        void refreshBar() {
            int pct = (int) Math.ceil(hp / max * 100);
            bar.setTitle("§5§l심연의 감시자 워든 §f" + pct + "% §7(" + fmt(hp) + " / " + fmt(max) + ")");
            bar.setProgress(Math.clamp(hp / max, 0, 1));
            syncHud(fightTick % 40 == 0);
        }

        // -------- main loop --------
        void tick() {
            fightTick++;
            if (!boss.isValid()) { fail(s, "보스가 사라졌습니다."); return; }
            if (boss.getHealth() < 100) { var a = boss.getAttribute(Attribute.GENERIC_MAX_HEALTH); if (a != null) boss.setHealth(a.getValue()); }
            List<Player> alive = alive();
            if (st != St.END && alive.isEmpty()) { fail(s, "전멸했습니다."); return; }
            if (fightTick > ci("time-limit-minutes") * 1200L && st != St.END) { fail(s, "제한 시간 초과"); return; }
            if (cb("vanilla-bossbar")) { for (Player p : online(s)) if (!bar.getPlayers().contains(p)) bar.addPlayer(p); }
            else if (!bar.getPlayers().isEmpty()) bar.removeAll();
            if (fightTick % 20 == 0) {
                for (Player p : online(s)) { try { boss.clearAnger(p); } catch (RuntimeException | LinkageError ignored) { } }
                if (boss.getTarget() != null) boss.setTarget(null);
                // exit bell beacon
                w.spawnParticle(Particle.END_ROD, DeepDarkMap.BELL_X + .5, DeepDarkMap.FLOOR + 3.2, DeepDarkMap.BELL_Z + .5, 6, .4, .5, .4, .01, null, true);
            }
            for (Player p : alive) {
                if (p.isSprinting()) noise.put(p.getUniqueId(), fightTick);
                Location l = p.getLocation();
                if (st != St.END && (l.getY() < DeepDarkMap.FLOOR - 4 || Math.hypot(l.getX() - DeepDarkMap.ACX, l.getZ() - DeepDarkMap.ACZ) > DeepDarkMap.R_IN + 1))
                    p.teleport(new Location(w, DeepDarkMap.ACX, DeepDarkMap.FLOOR + 1, 28.5, 180, 0));
            }
            if (st != St.END) for (Player p : ghosts()) {
                Location l = p.getLocation();
                if (l.getY() < DeepDarkMap.FLOOR - 4 || Math.hypot(l.getX() - DeepDarkMap.ACX, l.getZ() - DeepDarkMap.ACZ) > DeepDarkMap.R_IN + 1) {
                    p.teleport(new Location(w, DeepDarkMap.ACX, DeepDarkMap.FLOOR + 2, 28.5, 180, 0));
                    p.setFlying(true);
                }
            }
            if (!p2Done && hp <= p2Hp() + 1e-6 && chargePattern()) { abortCharge(); p2Done = true; go(St.BURROW); }
            // 70% gate: HP locked, the first charge pattern starts right away
            if (!gateDone && st == St.NORMAL && hp <= gateHp() + 1e-6) { gateDone = true; firstRun = true; go(St.STOMP); }
            // 어둠: every 6 s, all living players within 20 m of the warden
            if (cb("darkness.enabled") && !hidden && st != St.INTRO && st != St.END && ++darkTimer >= Math.max(20, ci("darkness.interval-ticks"))) {
                darkTimer = 0;
                double r = cd("darkness.radius");
                Location c = new Location(w, bx, groundY() + scale() * 1.5, bz);
                for (Player p : alive) if (p.getLocation().distanceSquared(c) <= r * r) {
                    applyingDarkness = true;
                    try { p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, ci("darkness.duration-ticks"), 0, false, false, false)); }
                    finally { applyingDarkness = false; }
                    p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_NEARBY_CLOSEST, SoundCategory.HOSTILE, 1f, .8f);
                }
            }
            int k = t++;
            switch (st) {
                case INTRO -> intro(k);
                case NORMAL -> normal(alive);
                case STOMP -> stomp(k);
                case PILLARS -> pillarsTick(k, alive);
                case WINDUP -> windup(k, alive);
                case CHARGE -> charge(k, alive);
                case STAGGER -> staggerTick(k);
                case GROGGY -> groggyTick(k);
                case BURROW -> burrow(k);
                case HEART -> heart(k, alive);
                case SONIC -> sonic(k, alive);
                case END -> { }
            }
            place();
            if (fightTick % 5 == 0) refreshBar();
        }

        // -------- intro / normal --------
        void intro(int k) {
            if (k == 0) {
                snd(w, bx, groundY(), bz, Sound.ENTITY_WARDEN_EMERGE, 5f, .6f);
                w.spawnParticle(Particle.BLOCK, bx, groundY() + .3, bz, 120, 4, .2, 4, .1, Material.DEEPSLATE.createBlockData(), true);
                for (Player p : online(s)) p.sendTitle("§5심연의 감시자", "§7딥 다크 시티 · 워든", 10, 50, 15);
            }
            if (k == 40) { snd(w, bx, groundY(), bz, Sound.ENTITY_WARDEN_ROAR, 5f, .7f); boss.playEffect(EntityEffect.WARDEN_TENDRIL_SHAKE); }
            if (k >= 80) { go(St.NORMAL); attackTimer = 20; }
        }

        void normal(List<Player> alive) {
            if (atkType == 0) {
                if (!p2Done && hp <= p2Hp()) { p2Done = true; go(St.BURROW); return; }
                if (rageOn && rage >= 100) { go(St.STOMP); return; } // 분노 게이지 100%: charge pattern
                Player near = nearest(alive);
                if (near != null) {
                    double d = Math.hypot(near.getLocation().getX() - bx, near.getLocation().getZ() - bz);
                    face(near.getLocation().getX(), near.getLocation().getZ());
                    if (d > 4.5) {
                        double step = Math.min(cd("move-speed") * (enraged ? 1.15 : 1), d - 4.5);
                        double ux = (near.getLocation().getX() - bx) / d, uz = (near.getLocation().getZ() - bz) / d;
                        bx += ux * step; bz += uz * step; clampBoss();
                        if (fightTick % 8 == 0) snd(w, bx, groundY(), bz, Sound.ENTITY_WARDEN_STEP, 2.5f, .7f);
                    }
                }
            }
            attackTimer++;
            if (atkType != 0) atkTick(alive);
            else if (attackTimer >= Math.max(10, ci("attack.interval-ticks"))) { attackTimer = 0; startAttack(alive); }
        }

        void clampBoss() {
            double ox = bx - DeepDarkMap.ACX, oz = bz - DeepDarkMap.ACZ, r = Math.hypot(ox, oz), lim = 36;
            if (r > lim) { bx = DeepDarkMap.ACX + ox / r * lim; bz = DeepDarkMap.ACZ + oz / r * lim; }
        }

        // -------- basic attacks --------
        void startAttack(List<Player> alive) {
            if (alive.isEmpty()) return;
            if (ThreadLocalRandom.current().nextInt(2) == 0) { // fresh 50/50 coin each cycle. 진동 감지: sonic area attack on the noisiest player
                Player tg = noisiest(alive);
                atkX = tg.getLocation().getX(); atkZ = tg.getLocation().getZ();
                atkType = 1; atkT = 0;
                face(atkX, atkZ);
                boss.playEffect(EntityEffect.WARDEN_SONIC_ATTACK);
                snd(w, bx, groundY() + 3, bz, Sound.ENTITY_WARDEN_SONIC_CHARGE, 3f, 1f);
            } else { // 냄새 감지: slam the nearest participant within slam.target-range; nobody that close -> run at the nearest one first
                Player tg = nearest(alive);
                double d = Math.hypot(tg.getLocation().getX() - bx, tg.getLocation().getZ() - bz);
                if (d <= cd("slam.target-range")) beginSlam(tg);
                else {
                    atkType = 3; atkT = 0;
                    face(tg.getLocation().getX(), tg.getLocation().getZ());
                    snd(w, bx, groundY() + 3, bz, Sound.ENTITY_WARDEN_ROAR, 3f, 1.3f);
                }
            }
        }

        void beginSlam(Player tg) {
            atkYaw = yawTo(bx, bz, tg.getLocation().getX(), tg.getLocation().getZ());
            yaw = atkYaw;
            atkType = 2; atkT = 0;
            snd(w, bx, groundY() + 3, bz, Sound.ENTITY_WARDEN_ANGRY, 3f, 1.1f);
        }

        /** Slam approach: run at the nearest participant until within slam.run-stop-range (or the time cap), then slam. */
        void runTick(List<Player> alive) {
            Player tg = nearest(alive);
            if (tg == null) { atkType = 0; return; }
            double tx = tg.getLocation().getX(), tz = tg.getLocation().getZ(), d = Math.hypot(tx - bx, tz - bz), stop = cd("slam.run-stop-range");
            face(tx, tz);
            if (d <= stop || atkT >= Math.max(10, ci("slam.run-max-ticks"))) { beginSlam(tg); attackTimer = 0; return; }
            double step = Math.min(cd("slam.run-speed"), d - stop + .05);
            bx += (tx - bx) / d * step; bz += (tz - bz) / d * step;
            clampBoss();
            if (atkT % 5 == 0) snd(w, bx, groundY(), bz, Sound.ENTITY_WARDEN_STEP, 3f, .9f);
            if (atkT % 2 == 0) w.spawnParticle(Particle.BLOCK, bx, groundY() + .2, bz, 5, hitR() * .5, .1, hitR() * .5, .05, Material.DEEPSLATE.createBlockData(), true);
            atkT++;
        }

        void atkTick(List<Player> alive) {
            if (atkType == 3) { runTick(alive); return; }
            if (atkType == 1) {
                int tele = Math.max(6, ci("sonic.telegraph-ticks"));
                double r = cd("sonic.radius");
                if (atkT == 0) markDisc("sonic", atkX, atkZ, r, .5);
                if (atkT <= tele && atkT % 2 == 0) { ring(atkX, atkZ, r, Color.fromRGB(255, 40, 40), 1.6f); ring(atkX, atkZ, r * atkT / tele, Color.fromRGB(255, 230, 120), 1.4f); }
                if (atkT == tele) {
                    clearMark("sonic");
                    snd(w, atkX, groundY() + 1, atkZ, Sound.ENTITY_WARDEN_SONIC_BOOM, 4f, 1f);
                    for (int i = 0; i < 9; i++) {
                        double a = rnd.nextDouble(Math.PI * 2), rr = rnd.nextDouble(r);
                        w.spawnParticle(Particle.SONIC_BOOM, atkX + Math.cos(a) * rr, groundY() + 1.2, atkZ + Math.sin(a) * rr, 1, 0, 0, 0, 0, null, true);
                    }
                    w.spawnParticle(Particle.EXPLOSION, atkX, groundY() + .5, atkZ, 6, r / 3, .3, r / 3, 0, null, true);
                    for (Player p : alive) {
                        Location l = p.getLocation();
                        if (Math.hypot(l.getX() - atkX, l.getZ() - atkZ) <= r && Math.abs(l.getY() - groundY()) < 5) hurt(p, cd("damage.sonic"));
                    }
                }
                if (atkT >= tele + 8) atkType = 0;
            } else {
                int h1 = Math.max(2, ci("slam.windup-ticks")), h2 = h1 + Math.max(4, ci("slam.gap-ticks")), h3 = h2 + Math.max(1, ci("slam.chain-ticks"));
                if (atkT == h1 + 3) { Player nr = nearest(alive); if (nr != null) { atkYaw = yawTo(bx, bz, nr.getLocation().getX(), nr.getLocation().getZ()); yaw = atkYaw; } }
                // red cone on the ground: before hit 1, then again (re-aimed) before hits 2 + 3
                if (atkT == 0 || atkT == h1 + 3) markCone("slam", bx, bz, atkYaw, cd("slam.range"), cd("slam.half-angle"), hitR() + 1.2);
                if (atkT == h1 || atkT == h2 || atkT == h3) slamHit(alive);
                if (atkT == h1 || atkT == h3) clearMark("slam");
                if (atkT >= h3 + 10) atkType = 0;
            }
            atkT++;
        }

        void cone(Color c) {
            double len = cd("slam.range"), half = cd("slam.half-angle");
            for (int i = -4; i <= 4; i++) {
                double a = Math.toRadians(atkYaw + half * i / 4.0);
                double ux = -Math.sin(a), uz = Math.cos(a);
                dust(w, bx + ux * len, groundY() + .15, bz + uz * len, c, 1.6f);
                if (Math.abs(i) == 4) for (double d = 2; d < len; d += 1.4) dust(w, bx + ux * d, groundY() + .15, bz + uz * d, c, 1.2f);
            }
        }

        void slamHit(List<Player> alive) {
            boss.playEffect(EntityEffect.WARDEN_ATTACK);
            snd(w, bx, groundY() + 1, bz, Sound.ENTITY_WARDEN_ATTACK_IMPACT, 4f, .7f);
            snd(w, bx, groundY() + 1, bz, Sound.BLOCK_DEEPSLATE_BREAK, 3f, .6f);
            double len = cd("slam.range"), half = cd("slam.half-angle");
            for (int i = 0; i < 28; i++) {
                double a = Math.toRadians(atkYaw + rnd.nextDouble(-half, half)), d = rnd.nextDouble(2, len);
                w.spawnParticle(Particle.BLOCK, bx - Math.sin(a) * d, groundY() + .4, bz + Math.cos(a) * d, 4, .3, .2, .3, .05, Material.DEEPSLATE_BRICKS.createBlockData(), true);
            }
            w.spawnParticle(Particle.EXPLOSION, bx - Math.sin(Math.toRadians(atkYaw)) * len * .6, groundY() + .5, bz + Math.cos(Math.toRadians(atkYaw)) * len * .6, 3, 1.5, .2, 1.5, 0, null, true);
            for (Player p : alive) {
                Location l = p.getLocation();
                double vx = l.getX() - bx, vz = l.getZ() - bz, d = Math.hypot(vx, vz);
                if (Math.abs(l.getY() - groundY()) > 6) continue;
                boolean inCone = d <= len && angDiff(Math.toDegrees(Math.atan2(-vx, vz)), atkYaw) <= half;
                if (inCone || d < hitR() + 1.2) hurt(p, cd("damage.slam"));
            }
        }

        /** Fixed boss damage (config damage.*), reduced only by the target's defense (CombatStats.defenseDamage). */
        void hurt(Player p, double base) {
            if (!p.isOnline() || p.isDead()) return;
            double amt = base * cd("damage.multiplier") * (enraged ? cd("damage.enrage-multiplier") : 1);
            p.setNoDamageTicks(0);
            p.damage(amt, boss);
        }

        // -------- phase 1: rage / pillars / charges --------
        void stomp(int k) {
            if (k == 0) {
                atkType = 0;
                clearMark("sonic"); clearMark("slam");
                snd(w, bx, groundY() + 3, bz, Sound.ENTITY_WARDEN_ROAR, 5f, .6f);
                boss.playEffect(EntityEffect.WARDEN_TENDRIL_SHAKE);
            }
            if (k == 10 || k == 30 || k == 50) {
                boss.playEffect(EntityEffect.WARDEN_ATTACK);
                snd(w, bx, groundY(), bz, Sound.ENTITY_WARDEN_ATTACK_IMPACT, 5f, .5f);
                snd(w, bx, groundY(), bz, Sound.BLOCK_DEEPSLATE_BREAK, 4f, .5f);
                for (int i = 0; i < 3; i++) ring(bx, bz, 3 + i * 3, Color.fromRGB(120, 100, 130), 1.5f);
                w.spawnParticle(Particle.BLOCK, bx, groundY() + .3, bz, 80, 3, .2, 3, .1, Material.DEEPSLATE.createBlockData(), true);
            }
            if (k >= 70) go(St.PILLARS);
        }

        List<Pillar> makePillars() {
            List<Pillar> list = new ArrayList<>();
            int c = (int) Math.round(cd("charge.pillar-radius") / Math.sqrt(2));
            for (int sx : new int[] {1, -1}) for (int sz : new int[] {1, -1}) list.add(new Pillar(sx * c, sz * c));
            return list;
        }

        Material pillarMat(int ox, int oz, int layer, int h) {
            boolean corner = Math.abs(ox) == 2 && Math.abs(oz) == 2, edge = Math.abs(ox) == 2 || Math.abs(oz) == 2;
            if (layer == h - 1 || layer == 0) return corner ? Material.CHISELED_DEEPSLATE : Material.DEEPSLATE_TILES;
            if (layer % 3 == 0 && edge) return Material.CHISELED_DEEPSLATE;
            if (corner) return Material.POLISHED_DEEPSLATE;
            return edge ? Material.DEEPSLATE_BRICKS : Material.REINFORCED_DEEPSLATE;
        }

        void raiseLayer(Pillar p, int layer) {
            int h = Math.max(4, ci("charge.pillar-height"));
            for (int ox = -2; ox <= 2; ox++) for (int oz = -2; oz <= 2; oz++)
                w.getBlockAt(p.cx + ox, DeepDarkMap.FLOOR + 1 + layer, p.cz + oz).setType(pillarMat(ox, oz, layer, h), false);
            p.layers = Math.max(p.layers, layer + 1);
            snd(w, p.cx + .5, groundY() + layer, p.cz + .5, Sound.BLOCK_DEEPSLATE_BRICKS_PLACE, 2.5f, .5f);
            w.spawnParticle(Particle.POOF, p.cx + .5, groundY() + .3, p.cz + .5, 6, 2.2, .1, 2.2, .02, null, true);
        }

        void eject(Player pl, Pillar p) {
            Location l = pl.getLocation();
            double vx = l.getX() - (p.cx + .5), vz = l.getZ() - (p.cz + .5), d = Math.hypot(vx, vz);
            if (d > 3.7) return;
            if (d < .01) { vx = 1; vz = 0; d = 1; }
            Location to = new Location(w, p.cx + .5 + vx / d * 4.2, groundY(), p.cz + .5 + vz / d * 4.2, l.getYaw(), l.getPitch());
            pl.teleport(to);
        }

        void destroy(Pillar p) {
            p.alive = false;
            for (int layer = 0; layer < p.layers; layer++) for (int ox = -2; ox <= 2; ox++) for (int oz = -2; oz <= 2; oz++)
                w.getBlockAt(p.cx + ox, DeepDarkMap.FLOOR + 1 + layer, p.cz + oz).setType(Material.AIR, false);
            p.layers = 0;
            w.spawnParticle(Particle.BLOCK, p.cx + .5, groundY() + 3, p.cz + .5, 200, 2.2, 3, 2.2, .1, Material.DEEPSLATE_BRICKS.createBlockData(), true);
            w.spawnParticle(Particle.EXPLOSION_EMITTER, p.cx + .5, groundY() + 2, p.cz + .5, 2, 1, 1, 1, 0, null, true);
            snd(w, p.cx + .5, groundY() + 2, p.cz + .5, Sound.ENTITY_GENERIC_EXPLODE, 5f, .7f);
            snd(w, p.cx + .5, groundY() + 2, p.cz + .5, Sound.BLOCK_DEEPSLATE_BRICKS_BREAK, 5f, .5f);
        }

        int livePillars() { int c = 0; for (Pillar p : pillars) if (p.alive) c++; return c; }

        void pillarsTick(int k, List<Player> alive) {
            int h = Math.max(4, ci("charge.pillar-height"));
            if (k == 0) {
                pillars.clear();
                pillars.addAll(makePillars());
                for (Pillar p : pillars) { p.alive = true; p.layers = 0; }
                blocked = 0;
                charges = 0;
                snd(w, bx, groundY(), bz, Sound.ENTITY_WARDEN_TENDRIL_CLICKS, 4f, .6f);
                for (Pillar p : pillars) mark("pillars").add(plate(p.cx - 2, p.cz - 2, 0, 0, 0, 5, 5));
            }
            if (k == 40) clearMark("pillars");
            if (k >= 40 && k < 40 + 2 * h && (k - 40) % 2 == 0) {
                int layer = (k - 40) / 2;
                List<Player> inside = new ArrayList<>(alive);
                inside.addAll(ghosts());
                for (Pillar p : pillars) { for (Player pl : inside) eject(pl, p); raiseLayer(p, layer); }
            }
            if (k >= 40 + 2 * h + 10) go(St.WINDUP);
        }

        void pickTarget(List<Player> alive) { target = random(alive); }

        void windup(int k, List<Player> alive) {
            int total = Math.max(20, ci("charge.windup-ticks")), lock = Math.min(total - 5, Math.max(5, ci("charge.lock-ticks")));
            if (k == 0) {
                pickTarget(alive);
                snd(w, bx, groundY() + 3, bz, Sound.ENTITY_WARDEN_ROAR, 4f, .8f);
                boss.playEffect(EntityEffect.WARDEN_TENDRIL_SHAKE);
            }
            if (target == null || !alive.contains(target)) { pickTarget(alive); if (target == null) return; }
            Location tl = target.getLocation();
            if (k < total - lock) {
                double d = Math.hypot(tl.getX() - bx, tl.getZ() - bz);
                if (d > .5) { dx = (tl.getX() - bx) / d; dz = (tl.getZ() - bz) / d; }
            }
            yaw = Math.toDegrees(Math.atan2(-dx, dz));
            if (k < total - lock || k == total - lock || chargeMark == null) aimChargeMark();
            if (k == total - lock) {
                snd(w, bx, groundY() + 2, bz, Sound.ENTITY_WARDEN_AGITATED, 5f, .8f);
                target.playSound(target.getLocation(), Sound.BLOCK_ANVIL_LAND, 1f, .6f);
            }
            if (k % 10 == 0) snd(w, bx, groundY() + 1, bz, Sound.ENTITY_WARDEN_STEP, 3f, .5f);
            if (k >= total) { travelled = 0; chargeHit.clear(); go(St.CHARGE); snd(w, bx, groundY() + 3, bz, Sound.ENTITY_WARDEN_ROAR, 5f, 1.2f); }
        }

        static double boxDist(double x, double z, double cx, double cz, double half) {
            double ox = Math.max(0, Math.abs(x - cx) - half), oz = Math.max(0, Math.abs(z - cz) - half);
            return Math.hypot(ox, oz);
        }

        /** A solid (non-pillar) block right in front of the charging boss: the charge stops there. */
        boolean solidAhead(double x, double z, double hr) {
            int bxi = (int) Math.floor(x + dx * (hr + .4)), bzi = (int) Math.floor(z + dz * (hr + .4));
            for (Pillar p : pillars) if (p.layers > 0 && Math.abs(bxi - p.cx) <= 2 && Math.abs(bzi - p.cz) <= 2) return false; // pillars are handled by the pillar hit
            // full blocks only (walls, broken columns): lanterns / veins / slabs on the floor do not stop the charge
            for (int y = 1; y <= 3; y++) if (w.getBlockAt(bxi, DeepDarkMap.FLOOR + y, bzi).getType().isOccluding()) return true;
            return false;
        }

        /** One straight charge ended (pillar or wall): count it and stagger. */
        void endCharge(int staggerTicks) {
            charges++;
            stagger = staggerTicks;
            clearChargeMark();
            chargeHit.clear();
            go(St.STAGGER);
        }

        /** Straight-line charge along (dx, dz), no homing. Stops at a pillar (destroys it) or at the arena wall / solid blocks; damages players it passes through. */
        void charge(int k, List<Player> alive) {
            double speed = Math.max(.2, cd("charge.speed")), hr = hitR();
            double nx = bx + dx * speed, nz = bz + dz * speed;
            for (Pillar p : pillars) {
                if (!p.alive || boxDist(nx, nz, p.cx + .5, p.cz + .5, 2.5) > hr) continue;
                destroy(p);
                blocked++;
                boss.playEffect(EntityEffect.HURT);
                endCharge(50);
                return;
            }
            double ox = nx - DeepDarkMap.ACX, oz = nz - DeepDarkMap.ACZ, r = Math.hypot(ox, oz), wallR = DeepDarkMap.R_IN - 3 - hr;
            boolean wall = r > wallR || solidAhead(nx, nz, hr) || travelled > 4 * DeepDarkMap.R_IN;
            if (r > wallR) { nx = DeepDarkMap.ACX + ox / r * wallR; nz = DeepDarkMap.ACZ + oz / r * wallR; }
            for (Player p : alive) {
                if (chargeHit.contains(p.getUniqueId())) continue;
                Location l = p.getLocation();
                if (Math.hypot(l.getX() - nx, l.getZ() - nz) <= hr + .6 && Math.abs(l.getY() - groundY()) < 7) {
                    chargeHit.add(p.getUniqueId());
                    hurt(p, cd("damage.charge"));
                    snd(w, nx, groundY() + 1, nz, Sound.ENTITY_WARDEN_ATTACK_IMPACT, 5f, .6f);
                    w.spawnParticle(Particle.EXPLOSION, l.getX(), groundY() + 1, l.getZ(), 4, 1, .5, 1, 0, null, true);
                }
            }
            bx = nx; bz = nz;
            yaw = Math.toDegrees(Math.atan2(-dx, dz));
            travelled += speed;
            if (wall) {
                snd(w, bx, groundY() + 2, bz, Sound.ENTITY_WARDEN_ATTACK_IMPACT, 5f, .5f);
                snd(w, bx, groundY() + 2, bz, Sound.BLOCK_DEEPSLATE_BREAK, 4f, .5f);
                w.spawnParticle(Particle.BLOCK, bx + dx * hr, groundY() + 1.5, bz + dz * hr, 60, 1.2, 1.2, 1.2, .1, Material.DEEPSLATE.createBlockData(), true);
                endCharge(40);
                return;
            }
            aimChargeMark();
            if (k % 3 == 0) snd(w, bx, groundY(), bz, Sound.ENTITY_WARDEN_STEP, 3f, .5f);
            w.spawnParticle(Particle.BLOCK, bx, groundY() + .2, bz, 8, hr * .6, .1, hr * .6, .05, Material.DEEPSLATE.createBlockData(), true);
        }

        void staggerTick(int k) {
            if (k == 0) w.spawnParticle(Particle.LARGE_SMOKE, bx, groundY() + 2, bz, 30, 1.5, 1.5, 1.5, .02, null, true);
            if (k < stagger) return;
            if (charges < CHARGES) go(St.WINDUP);
            else if (blocked >= CHARGES) go(St.GROGGY); // every charge hit a pillar -> groggy
            else endChargePattern();                    // missed at least once: no groggy, straight back to basic attacks
        }

        void groggyTick(int k) {
            int total = Math.max(20, ci("groggy.ticks"));
            if (k == 0) {
                groggy = true;
                clearChargeMark();
                snd(w, bx, groundY() + 3, bz, Sound.ENTITY_WARDEN_DEATH, 5f, .7f);
            }
            if (k % 8 == 0) w.spawnParticle(Particle.SCULK_SOUL, bx, groundY() + scale() * 2.2, bz, 8, 1.2, .8, 1.2, .02, null, true);
            if (k >= total) endChargePattern();
        }

        /** The charge pattern is over (after the groggy, or right after the 4th charge if one missed): pillars go, rage resets, back to NORMAL (vulnerable again). */
        void endChargePattern() {
            groggy = false;
            enraged = true;
            clearChargeMark();
            snd(w, bx, groundY() + 3, bz, Sound.ENTITY_WARDEN_ROAR, 5f, .7f);
            for (Pillar p : pillars) if (p.alive || p.layers > 0) destroy(p);
            pillars.clear();
            blocked = 0;
            charges = 0;
            target = null;
            chargeHit.clear();
            rage = 0; // gauge refills -> the charge pattern repeats whenever it is full again
            if (firstRun) { firstRun = false; rageOn = true; } // the rage gauge only starts after the first charge pattern
            attackTimer = 0;
            go(St.NORMAL);
        }

        /** HP reached the phase-2 gate mid charge pattern: tear it down cleanly (pillars, lane, groggy) before phase 2. */
        void abortCharge() {
            groggy = false;
            atkType = 0;
            clearAllMarks();
            for (Pillar p : pillars) if (p.alive || p.layers > 0) destroy(p);
            pillars.clear();
            blocked = 0;
            charges = 0;
            target = null;
            chargeHit.clear();
            rage = 0;
            firstRun = false;
            rageOn = true;
        }

        // -------- phase 2: heartbeat / orbs / sonic --------
        void burrow(int k) {
            if (k == 0) {
                atkType = 0;
                clearMark("sonic"); clearMark("slam");
                heartN = 1 + rnd.nextInt(Math.max(1, ci("orbs.count")));
                snd(w, bx, groundY(), bz, Sound.ENTITY_WARDEN_DIG, 5f, .7f);
                boss.playEffect(EntityEffect.WARDEN_TENDRIL_SHAKE);
            }
            if (k < 30 && k % 3 == 0) w.spawnParticle(Particle.BLOCK, bx, groundY() + .3, bz, 40, 2.5, .2, 2.5, .1, Material.DEEPSLATE.createBlockData(), true);
            if (k == 30) { hidden = true; boss.setInvisible(true); spawnOrbs(); }
            if (k >= 60) go(St.HEART);
        }

        void spawnOrbs() {
            int count = Math.max(1, ci("orbs.count"));
            double rmin = cd("orbs.min-radius"), rmax = cd("orbs.max-radius");
            List<double[]> pts = new ArrayList<>();
            int guard = 0;
            while (pts.size() < count && guard++ < 2000) {
                double a = rnd.nextDouble(Math.PI * 2), r = rmin + Math.sqrt(rnd.nextDouble()) * (rmax - rmin);
                double x = DeepDarkMap.ACX + Math.cos(a) * r, z = DeepDarkMap.ACZ + Math.sin(a) * r;
                boolean ok = true;
                for (double[] q : pts) if (Math.hypot(q[0] - x, q[1] - z) < 5) { ok = false; break; }
                if (ok) pts.add(new double[] {x, z});
            }
            for (double[] q : pts) {
                Location l = new Location(w, q[0], groundY() + .4, q[1]);
                BlockDisplay d = w.spawn(l, BlockDisplay.class, e -> {
                    e.setBlock(Material.BLACK_CONCRETE.createBlockData());
                    e.setPersistent(false);
                    e.setBillboard(Display.Billboard.FIXED);
                    e.setViewRange(2f);
                    e.setGlowing(true);
                    e.setGlowColorOverride(Color.fromRGB(130, 50, 220));
                    e.addScoreboardTag("pipi_raid_orb");
                    e.setTransformation(new Transformation(new Vector3f(-.45f, 0, -.45f), new Quaternionf().rotateY(.7f).rotateX(.5f), new Vector3f(.9f, .9f, .9f), new Quaternionf()));
                });
                orbs.add(new Orb(d, q[0], groundY() + .9, q[1]));
            }
        }

        void heart(int k, List<Player> alive) {
            int spacing = Math.max(8, ci("heartbeat.spacing-ticks"));
            String custom = plugin.getConfig().getString(P + "heartbeat.custom-sound." + heartN, "");
            if (k == 0) {
                heartEnd = custom != null && !custom.isBlank() ? 40 + Math.max(20, ci("heartbeat.custom-ticks." + heartN)) + 20 : 40 + heartN * spacing + 40;
            }
            if (custom != null && !custom.isBlank()) {
                if (k == 40) for (Player p : alive) p.playSound(p.getLocation(), custom, SoundCategory.HOSTILE, (float) cd("heartbeat.volume"), 1f);
            } else if (k >= 40 && (k - 40) % spacing == 0 && (k - 40) / spacing < heartN) {
                float vol = (float) cd("heartbeat.volume"), pitch = (float) cd("heartbeat.pitch");
                for (Player p : alive) p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, vol, pitch);
            }
            orbFx(k);
            if (k >= heartEnd) go(St.SONIC);
        }

        void orbFx(int k) {
            if (k % 4 != 0) return;
            for (Orb o : orbs) {
                w.spawnParticle(Particle.SQUID_INK, o.x, o.y, o.z, 2, .3, .3, .3, .01, null, true);
                w.spawnParticle(Particle.SCULK_SOUL, o.x, o.y + .3, o.z, 1, .2, .2, .2, .01, null, true);
            }
        }

        void sonic(int k, List<Player> alive) {
            sonicTotal = Math.max(100, ci("sonic-charge-seconds") * 20);
            if (k == 0) {
                hidden = false;
                boss.setInvisible(false);
                bx = DeepDarkMap.ACX; bz = DeepDarkMap.ACZ; yaw = 0;
                snd(w, bx, groundY(), bz, Sound.ENTITY_WARDEN_EMERGE, 5f, .6f);
                snd(w, bx, groundY() + 3, bz, Sound.ENTITY_WARDEN_ROAR, 5f, .6f);
                w.spawnParticle(Particle.BLOCK, bx, groundY() + .3, bz, 150, 4, .2, 4, .1, Material.DEEPSLATE.createBlockData(), true);
            }
            // pickup
            double pr = cd("orbs.pickup-radius");
            for (Iterator<Orb> it = orbs.iterator(); it.hasNext(); ) {
                Orb o = it.next();
                for (Player p : alive) {
                    Location l = p.getLocation();
                    if (Math.hypot(l.getX() - o.x, l.getZ() - o.z) <= pr && Math.abs(l.getY() + 1 - o.y) < 2.6) {
                        if (o.d.isValid()) o.d.remove();
                        it.remove();
                        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, .5f);
                        w.spawnParticle(Particle.SCULK_CHARGE_POP, o.x, o.y, o.z, 12, .3, .3, .3, .02, null, true);
                        for (Player m : online(s)) say(m, "§8" + p.getName() + "님이 검은 구슬을 흡수했습니다.");
                        break;
                    }
                }
            }
            orbFx(k);
            // big telegraph: swirling energy at the warden + a ring growing to the arena wall
            if (k % 4 == 0) {
                double rr = DeepDarkMap.R_IN * (k / (double) sonicTotal);
                ring(DeepDarkMap.ACX, DeepDarkMap.ACZ, Math.max(2, rr), Color.fromRGB(255, 50 + (int) (150 * (1 - k / (double) sonicTotal)), 60), 2f);
                w.spawnParticle(Particle.SCULK_SOUL, bx, groundY() + scale() * 1.5, bz, 10, 1.5, 2, 1.5, .05, null, true);
            }
            if (k % 100 == 0) snd(w, bx, groundY() + 3, bz, Sound.ENTITY_WARDEN_SONIC_CHARGE, 5f, .8f + .4f * k / sonicTotal);
            if (k == sonicTotal - 40) boss.playEffect(EntityEffect.WARDEN_SONIC_ATTACK);
            if (k == Math.max(0, sonicTotal - 100)) markDisc("bigsonic", DeepDarkMap.ACX, DeepDarkMap.ACZ, DeepDarkMap.R_IN, 1.0);
            if (k >= sonicTotal) releaseSonic(alive);
        }

        void releaseSonic(List<Player> alive) {
            int total = Math.max(1, ci("orbs.count")), eaten = total - orbs.size();
            boolean ok = eaten == heartN;
            clearMark("bigsonic");
            for (Orb o : orbs) if (o.d.isValid()) o.d.remove();
            orbs.clear();
            snd(w, bx, groundY() + 3, bz, Sound.ENTITY_WARDEN_SONIC_BOOM, 8f, .6f);
            for (int i = 0; i < 40; i++) {
                double a = rnd.nextDouble(Math.PI * 2), rr = rnd.nextDouble(DeepDarkMap.R_IN);
                w.spawnParticle(Particle.SONIC_BOOM, DeepDarkMap.ACX + Math.cos(a) * rr, groundY() + 1.5 + rnd.nextDouble(4), DeepDarkMap.ACZ + Math.sin(a) * rr, 1, 0, 0, 0, 0, null, true);
            }
            w.spawnParticle(Particle.EXPLOSION_EMITTER, bx, groundY() + 3, bz, 6, 4, 2, 4, 0, null, true);
            for (Player p : alive) p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 4f, .7f);
            if (ok) {
                for (Player p : alive) {
                    double dmg = cd("damage.survive") * cd("damage.multiplier");
                    if (dmg <= 0) continue;   // 파훼 성공: 피해 없음
                    p.setHealth(Math.max(1, p.getHealth() - dmg));
                    p.playEffect(EntityEffect.HURT);
                }
                for (Player p : online(s)) p.sendTitle("§a음파를 견뎌냈다!", "", 0, 50, 10);
                enraged = true;
                p2Released = true; // 35% gate opens
                attackTimer = -40;
                atkType = 0;
                go(St.NORMAL);
            } else {
                for (Player p : online(s)) p.sendTitle("§c전멸...", "", 0, 80, 10);
                for (Player p : alive) p.setHealth(0);
                go(St.END);
                Bukkit.getScheduler().runTaskLater(plugin, () -> { if (session == s && s.fight == this && s.stage == Stage.FIGHT) fail(s, "광역 음파 공격에 전멸했습니다."); }, 60L);
            }
        }

        // -------- end --------
        void win() {
            if (st == St.END) return;
            go(St.END);
            groggy = false;
            for (Iterator<Pillar> it = pillars.iterator(); it.hasNext(); ) { Pillar p = it.next(); if (p.alive) destroy(p); }
            s.stage = Stage.CLEARED;
            s.clearTimer = 20 * 25;
            bar.setProgress(0);
            bar.setTitle("§a토벌 완료");
            hp = 0;
            clearAllMarks();
            syncHud(true);
            guardBossDrops(boss.getLocation());
            boss.setHealth(0);
            snd(w, bx, groundY() + 3, bz, Sound.ENTITY_WARDEN_DEATH, 6f, .6f);
            unGhostAll(s);
            s.deathAt.clear();
            try { town.grantBossClear(new ArrayList<>(s.members), "warden"); } // 보스 처치 기록 (대장간 보스 탭)
            catch (RuntimeException ex) { plugin.getLogger().severe("warden clear record failed: " + ex); }
            for (Player p : online(s)) {
                p.sendTitle("§6딥 다크 시티 토벌 완료!", "§7워든이 쓰러졌다", 5, 80, 20);
                p.removePotionEffect(PotionEffectType.DARKNESS);
            }
            try { startAuction(s); } catch (RuntimeException ex) { plugin.getLogger().severe("raid auction start failed: " + ex); ex.printStackTrace(); }
            Bukkit.getScheduler().runTaskLater(plugin, () -> { if (session == s) rewards(s); }, 60L);
        }

        void cleanup() {
            try { bar.removeAll(); } catch (RuntimeException ignored) { }
            clearAllMarks();
            for (Player p : online(s)) p.sendMessage("PIPI_BOSS_END");
            for (Pillar p : pillars) if (p.alive || p.layers > 0) destroy(p);
            pillars.clear();
            for (Orb o : orbs) if (o.d.isValid()) o.d.remove();
            orbs.clear();
            if (boss.isValid()) boss.remove();
            for (Player p : online(s)) p.removePotionEffect(PotionEffectType.DARKNESS);
        }
    }
}
