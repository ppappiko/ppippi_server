package dev.pipi.life;

import java.util.*;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 펫 타고 다니기 (지상). 탑승·조종은 PipiFacilities 모드의 펫 엔티티(PipiPets.mount)가 처리하고,
 * 여기서는 탈것 지정·소환·내리기와 제약(config mounts.*)을 맡는다.
 *  /pipilife mount           : 지정한 탈것 펫 타기/내리기 (클라이언트 Z 키)
 *  /pipilife mount set <펫>  : 내가 가진 펫 중 탈것 지정
 *  /pipilife mount list      : 탈 수 있는 내 펫 목록
 *  /pipilife mount test <펫> : (관리자) 가지고 있지 않아도 아무 펫이나 타 보기
 * 탈것 엔티티는 저장되지 않고(persistent=false) 내리면 바로 사라진다.
 */
final class Mounts implements Listener {
    static final String TAG = "pipi_mount";
    private final PipiLife plugin;
    private final CustomPets pets;
    /** 탑승 중인 플레이어 -> 탈것 엔티티 */
    private final Map<UUID, LivingEntity> riding = new HashMap<>();

    Mounts(PipiLife plugin, CustomPets pets) {
        this.plugin = plugin;
        this.pets = pets;
        var c = plugin.getConfig();
        c.addDefault("mounts.allowed", List.of());
        c.addDefault("mounts.default-speed", 0.3);
        c.addDefault("mounts.default-jump", 0.5);
        c.addDefault("mounts.blocked-worlds", List.of(VillageMaps.TUTORIAL, DeepDarkMap.WORLD));
        c.addDefault("mounts.dismount-on-damage", true);
        for (World w : Bukkit.getWorlds()) for (Entity e : w.getEntities()) if (e.getScoreboardTags().contains(TAG)) e.remove();
        Bukkit.getScheduler().runTaskTimer(plugin, this::check, 20, 10);
    }

    private String key(Player player) { return "mounts." + player.getUniqueId(); }

    // ------------------------------------------------------------------ 설정

    private ConfigurationSection cfg() { return plugin.getConfig().getConfigurationSection("mounts"); }

    /** config mounts.allowed 가 비어 있으면 모든 펫, 아니면 적힌 펫(명령어 이름)만 탈 수 있다. */
    private boolean allowed(PipiLife.Pet pet) {
        List<String> list = plugin.getConfig().getStringList("mounts.allowed");
        return list.isEmpty() || list.contains(skin(pet));
    }

    private float value(String field, PipiLife.Pet pet, double fallback) {
        return (float) plugin.getConfig().getDouble("mounts." + field + "." + skin(pet), fallback);
    }

    private static String skin(PipiLife.Pet pet) { return pet.name().toLowerCase(Locale.ROOT); }

    private static PipiLife.Pet pet(String name) {
        if (name == null) return null;
        try { return PipiLife.Pet.valueOf(name.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException ex) { return null; }
    }

    private String blockedReason(Player player) {
        if (plugin.getConfig().getStringList("mounts.blocked-worlds").contains(player.getWorld().getName())) return "이 지역에서는 펫을 탈 수 없어요.";
        if (Armory.inCombatMode(player)) return "전투 모드에서는 펫을 탈 수 없어요. (Alt로 전투 모드를 끄세요)";
        if (player.getVehicle() != null && !isMount(player.getVehicle())) return "다른 것을 타고 있을 때는 펫을 부를 수 없어요.";
        if (player.isFlying() || player.isGliding() || player.isSwimming()) return "땅 위에서만 펫을 탈 수 있어요.";
        return null;
    }

    private boolean owns(Player player, PipiLife.Pet pet) {
        return plugin.data().isConfigurationSection("players." + player.getUniqueId() + "." + skin(pet));
    }

    static boolean isMount(Entity entity) { return entity != null && entity.getScoreboardTags().contains(TAG); }

    // ------------------------------------------------------------------ 명령어

    boolean command(Player player, String[] args) {
        if (args.length == 1) return toggle(player, null);
        String sub = args[1].toLowerCase(Locale.ROOT);
        if (sub.equals("list")) return list(player);
        if (sub.equals("set") && args.length == 3) return set(player, args[2]);
        if (sub.equals("test") && args.length == 3) {
            if (!player.isOp()) { player.sendMessage("§c관리자 전용 명령어입니다."); return true; }
            PipiLife.Pet pet = pet(args[2]);
            if (pet == null) { player.sendMessage("펫 이름을 확인하세요. 예: /pipilife mount test imugi"); return true; }
            return toggle(player, pet);
        }
        if (sub.equals("off")) { dismount(player, true); return true; }
        player.sendMessage("/pipilife mount §7(타기/내리기, Z 키)§f · mount set <펫> · mount list" + (player.isOp() ? " · mount test <펫>" : ""));
        return true;
    }

    private boolean list(Player player) {
        PipiLife.Pet chosen = pet(plugin.data().getString(key(player)));
        List<String> names = new ArrayList<>();
        for (PipiLife.Pet pet : PipiLife.Pet.values())
            if (owns(player, pet) && allowed(pet)) names.add((pet == chosen ? "§a★ " : "§f") + pet.label + " §7(" + skin(pet) + ")");
        if (names.isEmpty()) player.sendMessage("§7탈 수 있는 펫이 없어요.");
        else player.sendMessage("§6[탈것] §f탈 수 있는 펫: " + String.join("§7, ", names));
        player.sendMessage("§7/pipilife mount set <이름> 으로 탈것을 지정하세요.");
        return true;
    }

    private boolean set(Player player, String name) {
        PipiLife.Pet pet = pet(name);
        if (pet == null || !owns(player, pet)) { player.sendMessage("가지고 있는 펫만 탈것으로 지정할 수 있어요. (/pipilife mount list)"); return true; }
        if (!allowed(pet)) { player.sendMessage("§c" + pet.label + "은(는) 탈것으로 쓸 수 없는 펫이에요."); return true; }
        if (!plugin.store(key(player), skin(pet))) { player.sendMessage("탈것을 저장하지 못했어요."); return true; }
        player.sendMessage("§6[탈것] §a" + pet.label + "§f을(를) 탈것으로 지정했어요. §eZ 키§f로 타고 내릴 수 있어요.");
        return true;
    }

    // ------------------------------------------------------------------ 타기 / 내리기

    /** test 가 null 이면 지정한 탈것을, 아니면 그 펫을 (관리자 테스트) 탄다. 이미 타고 있으면 내린다. */
    private boolean toggle(Player player, PipiLife.Pet test) {
        if (riding.containsKey(player.getUniqueId())) { dismount(player, true); return true; }
        PipiLife.Pet pet = test != null ? test : pet(plugin.data().getString(key(player)));
        if (pet == null) {
            player.sendMessage("§7탈것으로 지정한 펫이 없어요. /pipilife mount list 로 확인하고 /pipilife mount set <이름> 으로 지정하세요.");
            return true;
        }
        if (test == null && (!owns(player, pet) || !allowed(pet))) {
            player.sendMessage("§c" + pet.label + "은(는) 지금 탈 수 없어요. 탈것을 다시 지정하세요.");
            return true;
        }
        String blocked = blockedReason(player);
        if (blocked != null) { player.sendMessage("§c" + blocked); return true; }
        if (!player.isOnGround()) { player.sendMessage("§c땅에 선 채로 펫을 부르세요."); return true; }
        if (pets == null) { player.sendMessage("펫 모델이 로드되지 않았어요."); return true; }

        Location at = player.getLocation();
        LivingEntity mount = pets.spawnMount(at, skin(pet));
        if (mount == null) { player.sendMessage("탈것 펫을 부르지 못했어요."); return true; }
        if (!bridge(mount, value("speed", pet, cfg().getDouble("default-speed", .3)),
                value("seat", pet, 0), value("jump", pet, cfg().getDouble("default-jump", .5)))
            || !mount.addPassenger(player)) {
            mount.remove();
            player.sendMessage("탈것에 오르지 못했어요. (서버·클라이언트 PipiFacilities 버전을 확인하세요)");
            return true;
        }
        mount.setCustomName("§e" + player.getName() + "§7의 " + pet.label);
        mount.setCustomNameVisible(false);
        riding.put(player.getUniqueId(), mount);
        player.playSound(at, Sound.ENTITY_HORSE_SADDLE, .6f, 1.4f);
        player.sendActionBar("§a" + pet.label + "§f에 탔어요 §7· 내리기: Z 또는 Shift");
        return true;
    }

    private boolean bridge(LivingEntity mount, float speed, float seat, float jump) {
        try {
            Object nms = mount.getClass().getMethod("getHandle").invoke(mount);
            Class.forName("dev.pipi.facilities.PipiPets").getMethod("mount", Object.class, float.class, float.class, float.class)
                .invoke(null, nms, speed, seat, jump);
            return true;
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().warning("탈것 연결 실패 (PipiFacilities 를 새 버전으로 바꿨는지 확인): " + ex);
            return false;
        }
    }

    /** 탈것에서 내리고 탈것 펫을 없앤다. */
    void dismount(Player player, boolean message) {
        LivingEntity mount = riding.remove(player.getUniqueId());
        if (mount == null) return;
        if (mount.isValid()) {
            mount.eject();
            mount.remove();
        }
        if (message && player.isOnline()) player.sendActionBar("§7펫에서 내렸어요");
    }

    void close() {
        for (UUID id : new ArrayList<>(riding.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) dismount(player, false);
            else { LivingEntity m = riding.remove(id); if (m != null) m.remove(); }
        }
    }

    /** 0.5초마다: 막힌 지역·전투 모드로 바뀌었거나 탈것이 사라졌으면 내린다. */
    private void check() {
        for (UUID id : new ArrayList<>(riding.keySet())) {
            Player player = Bukkit.getPlayer(id);
            LivingEntity mount = riding.get(id);
            if (player == null || mount == null || !mount.isValid() || !mount.getPassengers().contains(player)) {
                if (player != null) dismount(player, false);
                else { riding.remove(id); if (mount != null) mount.remove(); }
                continue;
            }
            if (plugin.getConfig().getStringList("mounts.blocked-worlds").contains(player.getWorld().getName()) || Armory.inCombatMode(player)) {
                dismount(player, true);
            }
        }
    }

    // ------------------------------------------------------------------ 이벤트

    @EventHandler public void leave(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player player && isMount(event.getDismounted()))
            Bukkit.getScheduler().runTask(plugin, () -> dismount(player, true));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void hurt(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !riding.containsKey(player.getUniqueId())) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) return;
        if (plugin.getConfig().getBoolean("mounts.dismount-on-damage", true)) dismount(player, true);
    }

    @EventHandler public void quit(PlayerQuitEvent event) { dismount(event.getPlayer(), false); }
    @EventHandler public void death(PlayerDeathEvent event) { dismount(event.getEntity(), false); }
    @EventHandler public void world(PlayerChangedWorldEvent event) { dismount(event.getPlayer(), false); }
}
