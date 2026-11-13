package dev.pipi.life;

import java.util.*;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * 신규 플레이어 튜토리얼.
 *  1) 튜토리얼 맵: 기본 펫(레시) 안내 → 레시 지급 → /pipilife finish 로 공동 마을 입장
 *  2) 거주지 가이드: 부지 선점 → 펫 창고 설치 → 펫 창고 확인 → 작업대·연구소 설치 → 레시 배치 → 몽둥이·포획틀 제작
 * 진행 상태는 workshops.yml 의 tutorial.<uuid> 아래에 저장된다. 가이드는 새 흐름으로 finish 한 플레이어에게만 뜬다.
 */
final class Tutorial implements Listener {
    static final PipiLife.Pet STARTER_PET = PipiLife.Pet.REDPANDA;
    private static final String GUIDE_TAG = "pipi_tutorial_pet";
    private static final String CLUB = "club", TRAP = "basictrap";

    /** 거주지 가이드 단계. 앞에서부터 처음으로 충족되지 않은 단계가 현재 단계다. */
    private enum Step {
        CLAIM("부지 선점", "땅 문서를 들고 공동 마을의 빈 부지 안에서 우클릭해 내 거주지를 선점하세요."),
        PET_STORAGE("펫 창고 설치", "가방의 펫 창고 청사진을 들고 내 부지 바닥을 우클릭해 설치하세요."),
        OPEN_STORAGE("펫 창고 확인", "설치한 펫 창고를 우클릭해서 레시가 들어 있는지 확인하세요."),
        STATIONS("작업대·연구소 설치", "기본 작업대와 연구소 청사진도 내 부지 바닥에 설치하세요."),
        ASSIGN("레시 배치", "기본 작업대를 우클릭하고 레시를 골라 작업대에 배치하세요."),
        CRAFT("몽둥이·포획틀 제작", "작업대에서 몽둥이와 기본 포획틀을 하나씩 제작하고 수령하세요.");

        final String title, hint;
        Step(String title, String hint) { this.title = title; this.hint = hint; }
    }

    private final PipiLife plugin;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, String> shown = new HashMap<>();

    Tutorial(PipiLife plugin) { this.plugin = plugin; }

    // ------------------------------------------------------------------ 저장 키

    private String path(Player player) { return plugin.tutorialPath(player); }
    private boolean flag(Player player, String key) { return plugin.data().getBoolean(path(player) + "." + key); }
    private boolean setFlag(Player player, String key) { return flag(player, key) || plugin.store(path(player) + "." + key, true); }

    boolean hasStarterPet(Player player) { return flag(player, "starter-pet"); }
    private boolean guideActive(Player player) {
        return flag(player, "guide") && !flag(player, "guide-done") && plugin.tutorialComplete(player);
    }

    // ------------------------------------------------------------------ 1) 튜토리얼 맵

    private boolean inTutorialWorld(Player player) { return player.getWorld().getName().equals(VillageMaps.TUTORIAL); }

    private final Set<UUID> welcomed = new HashSet<>();

    /** 튜토리얼 맵에서 처음 보는 안내 (접속당 한 번). */
    private void welcome(Player player) {
        if (plugin.tutorialComplete(player) || !inTutorialWorld(player) || hasStarterPet(player)) return;
        if (!welcomed.add(player.getUniqueId())) return;
        player.sendTitle("§e삐삐서버에 오신 걸 환영해요", "§f첫 펫 친구를 만나러 가볼까요?", 10, 60, 20);
        player.sendMessage("§6[튜토리얼] §f삐삐서버에서는 §e펫§f이 함께 일하고, 재료를 모으고, 물건을 만들어요.");
        player.sendMessage("§6[튜토리얼] §f앞쪽 §e펫 안내소§f에 있는 §e레시§f를 우클릭해 첫 펫으로 데려가세요.");
        player.sendMessage("§7(레시가 보이지 않으면 /pipilife tutorial pet)");
    }

    /** 레시 지급. 이미 받았으면 아무것도 하지 않는다. */
    boolean giveStarterPet(Player player) {
        if (plugin.tutorialComplete(player)) {
            player.sendMessage("§7이미 튜토리얼을 마친 계정이라 레시를 받을 수 없어요.");
            if (player.isOp() || player.hasPermission("pipilife.admin"))
                player.sendMessage("§7다시 테스트하려면 /pipilife tutorial reset 으로 튜토리얼을 초기화하세요.");
            return true;
        }
        if (hasStarterPet(player)) {
            player.sendMessage("§7이미 레시를 데려갔어요. 펫 창고에서 확인할 수 있어요.");
            return true;
        }
        if (!inTutorialWorld(player)) {
            player.sendMessage("튜토리얼 마을에서 받을 수 있어요.");
            return true;
        }
        int food = Math.clamp(plugin.getConfig().getInt("tutorial.starter-pet-food", 20), 0, 100);
        if (!plugin.grantStarterPet(player, STARTER_PET, food) || !setFlag(player, "starter-pet")) {
            player.sendMessage("레시를 저장하지 못했어요. 잠시 후 다시 시도하세요.");
            return true;
        }
        int logs = Math.max(0, plugin.getConfig().getInt("tutorial.starter-logs", 9));
        if (logs > 0) {
            for (ItemStack left : player.getInventory().addItem(new ItemStack(Material.OAK_LOG, logs)).values())
                player.getWorld().dropItemNaturally(player.getLocation(), left).setOwner(player.getUniqueId());
        }
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
        player.sendTitle("§a레시가 친구가 됐어요!", "§f펫 창고에 들어갔어요", 10, 50, 15);
        player.sendMessage("§6[튜토리얼] §a레시§f를 데려왔어요. 레시는 §e가공 적성§f이 있어서 기본 작업대에서 물건을 만들 수 있어요.");
        player.sendMessage("§6[튜토리얼] §f펫은 일할 때 §e포만감§f을 써요. 레시는 포만감 " + food + "로 시작해요.");
        if (logs > 0) player.sendMessage("§6[튜토리얼] §f첫 제작에 쓸 §e참나무 원목 " + logs + "개§f도 챙겨 드렸어요.");
        player.sendMessage("§6[튜토리얼] §f이제 앞쪽 문 옆 표지판에서 §e/pipilife finish§f 로 공동 마을에 들어가세요.");
        refresh(player);
        return true;
    }

    /** 튜토리얼 맵의 레시 안내 엔티티 우클릭. CustomPets 가 이벤트를 취소해도 받는다. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void clickGuidePet(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !event.getRightClicked().getScoreboardTags().contains(GUIDE_TAG)) return;
        event.setCancelled(true);
        giveStarterPet(event.getPlayer());
    }

    /** 튜토리얼 맵에 레시 안내 엔티티를 유지한다 (워커 태그라 저장되지 않으므로 주기적으로 다시 세운다). */
    private void keepGuidePet() {
        World world = Bukkit.getWorld(VillageMaps.TUTORIAL);
        if (world == null || world.getPlayers().isEmpty()) return;
        Location at = VillageMaps.tutorialPetSpot(world);
        if (!world.isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) return;
        List<Entity> guides = new ArrayList<>();
        for (Entity e : world.getNearbyEntities(at, 4, 3, 4)) if (e.getScoreboardTags().contains(GUIDE_TAG)) guides.add(e);
        for (int i = 1; i < guides.size(); i++) guides.get(i).remove();
        if (!guides.isEmpty()) {
            if (guides.getFirst().getLocation().distanceSquared(at) > 1) guides.getFirst().teleport(at);
            return;
        }
        LivingEntity pet = plugin.spawnDisplayPet(at, STARTER_PET.name().toLowerCase(Locale.ROOT));
        if (pet == null) return;
        pet.addScoreboardTag(GUIDE_TAG);
        pet.setCustomName("§e레시 §7· 우클릭해서 데려가기");
        pet.setCustomNameVisible(true);
    }

    // ------------------------------------------------------------------ 2) 거주지 가이드

    /** /pipilife finish 성공 직후 호출. 이 시점부터 거주지 가이드가 시작된다. */
    void startGuide(Player player) {
        if (!setFlag(player, "guide")) return;
        player.sendMessage("§6[튜토리얼] §f공동 마을에 도착했어요! 이제 내 거주지를 꾸려 볼게요.");
        player.sendMessage("§6[튜토리얼] §f화면 위쪽 안내를 따라가면 돼요.");
        shown.remove(player.getUniqueId());
        refresh(player);
    }

    void storageOpened(Player player) {
        if (guideActive(player) && setFlag(player, "guide-storage-opened")) refresh(player);
    }

    void crafted(Player player, String recipe) {
        if (!guideActive(player)) return;
        if (recipe.equals(CLUB) && setFlag(player, "guide-club")) refresh(player);
        if (recipe.equals(TRAP) && setFlag(player, "guide-trap")) refresh(player);
    }

    private Step current(Player player) {
        String plot = plugin.ownedPlot(player);
        if (plot == null) return Step.CLAIM;
        if (!plugin.hasFacilityIn(plot, Facility.PET_STORAGE)) return Step.PET_STORAGE;
        if (!flag(player, "guide-storage-opened")) return Step.OPEN_STORAGE;
        if (!plugin.hasFacilityIn(plot, Facility.CRAFT) || !plugin.hasFacilityIn(plot, Facility.RESEARCH)) return Step.STATIONS;
        if (!plugin.hasCraftWorker(player)) return Step.ASSIGN;
        if (!flag(player, "guide-club") || !flag(player, "guide-trap")) return Step.CRAFT;
        return null;
    }

    /** 퀘스트 패널(클라이언트 모드)용: 첫 줄 제목, 나머지는 최대 5줄 목록. 튜토리얼 중이 아니면 null. */
    List<String> questLines(Player player) {
        if (!plugin.tutorialComplete(player)) {
            if (!inTutorialWorld(player)) return null;
            boolean pet = hasStarterPet(player);
            return List.of("튜토리얼", (pet ? "✔ " : "▶ ") + "펫 안내소에서 레시 데려가기",
                (pet ? "▶ " : "· ") + "/pipilife finish 로 공동 마을 입장");
        }
        if (!guideActive(player)) return null;
        Step now = current(player);
        if (now == null) return null;
        List<String> lines = new ArrayList<>();
        lines.add("거주지 가이드 " + (now.ordinal() + 1) + "/" + Step.values().length);
        // 패널은 5줄까지라 현재 단계 앞뒤로 보여준다
        int from = Math.clamp(now.ordinal() - 1, 0, Step.values().length - 5);
        for (int i = from; i < from + 5; i++) {
            Step step = Step.values()[i];
            lines.add((step.ordinal() < now.ordinal() ? "✔ " : step == now ? "▶ " : "· ") + step.title);
        }
        return lines;
    }

    private void finishGuide(Player player) {
        if (!setFlag(player, "guide-done")) return;
        hide(player);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        player.sendTitle("§a튜토리얼 완료!", "§f이제 모험을 떠나 보세요", 10, 70, 20);
        player.sendMessage("§6[튜토리얼] §a기본 준비가 끝났어요!");
        player.sendMessage("§6[튜토리얼] §f이동 나침반으로 §2야생§f에 가서 펫을 잡아보세요. §e몽둥이§f로 체력을 깎고 §e포획틀§f을 길게 눌러 던지면 돼요.");
        player.sendMessage("§6[튜토리얼] §f펫 먹이가 떨어지면 대장간에서 낚시대를 만들어 §e낚시§f로 생 연어를 구할 수 있어요.");
        player.sendMessage("§6[튜토리얼] §f나머지는 직접 돌아다니며 알아가 보세요. 즐거운 모험 되세요!");
    }

    /** 현재 상태에 맞게 보스바와 채팅 힌트를 갱신한다. */
    private void refresh(Player player) {
        if (!player.isOnline()) return;
        String title;
        double progress;
        String key;
        if (!plugin.tutorialComplete(player)) {
            if (!inTutorialWorld(player)) { hide(player); return; }
            boolean pet = hasStarterPet(player);
            title = pet ? "§e튜토리얼 §f· 문 옆 표지판에서 /pipilife finish 로 공동 마을에 입장하세요"
                        : "§e튜토리얼 §f· 펫 안내소의 레시를 우클릭해 첫 펫으로 데려가세요";
            progress = pet ? 0.5 : 0.0;
            key = pet ? "map-finish" : "map-pet";
        } else if (guideActive(player)) {
            Step step = current(player);
            if (step == null) { finishGuide(player); return; }
            int index = step.ordinal() + 1, total = Step.values().length;
            title = "§e거주지 가이드 " + index + "/" + total + " §f· " + step.title;
            progress = (double) step.ordinal() / total;
            key = step.name();
            if (!key.equals(shown.get(player.getUniqueId())))
                player.sendMessage("§6[튜토리얼 " + index + "/" + total + "] §e" + step.title + " §f- " + step.hint);
        } else { hide(player); return; }
        shown.put(player.getUniqueId(), key);
        BossBar bar = bars.computeIfAbsent(player.getUniqueId(), id -> {
            BossBar created = Bukkit.createBossBar("", BarColor.YELLOW, BarStyle.SEGMENTED_6);
            created.addPlayer(player);
            return created;
        });
        if (!bar.getPlayers().contains(player)) bar.addPlayer(player);
        bar.setTitle(title);
        bar.setProgress(Math.clamp(progress, 0, 1));
        bar.setVisible(true);
    }

    private void hide(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) bar.removeAll();
        shown.remove(player.getUniqueId());
    }

    void tick() {
        keepGuidePet();
        for (Player player : Bukkit.getOnlinePlayers()) refresh(player);
    }

    void close() {
        for (BossBar bar : bars.values()) bar.removeAll();
        bars.clear();
        World world = Bukkit.getWorld(VillageMaps.TUTORIAL);
        if (world != null) for (Entity e : world.getEntities()) if (e.getScoreboardTags().contains(GUIDE_TAG)) e.remove();
    }

    @EventHandler public void join(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // tutorialJoin 이 튜토리얼 맵으로 옮긴 뒤에 안내한다
        Bukkit.getScheduler().runTaskLater(plugin, () -> { welcome(player); refresh(player); }, 20L);
    }

    @EventHandler public void changeWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        welcome(player);
        refresh(player);
    }

    @EventHandler public void quit(PlayerQuitEvent event) {
        hide(event.getPlayer());
        welcomed.remove(event.getPlayer().getUniqueId());
    }

    boolean command(Player player, String[] args) {
        if (args.length == 2 && args[1].equalsIgnoreCase("pet")) return giveStarterPet(player);
        boolean admin = player.isOp() || player.hasPermission("pipilife.admin");
        if (admin && args.length >= 2 && args.length <= 3 && args[1].equalsIgnoreCase("reset")) {
            Player target = args.length == 3 ? Bukkit.getPlayerExact(args[2]) : player;
            if (target == null) { player.sendMessage("접속 중인 플레이어 이름을 입력하세요."); return true; }
            reset(player, target);
            return true;
        }
        player.sendMessage(admin ? "/pipilife tutorial pet · 관리자: /pipilife tutorial reset [플레이어]" : "/pipilife tutorial pet");
        return true;
    }

    /**
     * 테스트용: 튜토리얼 진행 기록(tutorial.<uuid>)만 지우고 튜토리얼 맵으로 보낸다.
     * 부지·시설·펫은 그대로라 이미 충족한 가이드 단계는 자동으로 넘어간다. finish 시 시작 키트를 다시 받는다.
     */
    private void reset(Player admin, Player target) {
        if (!plugin.store(path(target), null)) { admin.sendMessage("튜토리얼 기록을 지우지 못했습니다."); return; }
        hide(target);
        welcomed.remove(target.getUniqueId());
        World world = plugin.openWorld(VillageMaps.TUTORIAL);
        boolean moved = world != null && target.teleport(world.getSpawnLocation());
        admin.sendMessage("§a" + target.getName() + "의 튜토리얼 진행을 초기화했습니다." + (moved ? " 튜토리얼 맵으로 이동했습니다." : " (튜토리얼 맵 이동 실패)"));
        admin.sendMessage("§7부지·시설·펫은 그대로라 이미 끝낸 가이드 단계는 자동으로 넘어갑니다.");
        if (target != admin) target.sendMessage("§e관리자가 튜토리얼을 처음부터 다시 시작하도록 초기화했습니다.");
        welcome(target);
        refresh(target);
    }
}
