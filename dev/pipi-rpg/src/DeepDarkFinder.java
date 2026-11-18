package dev.pipi.life;

import java.util.*;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.generator.structure.Structure;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * 심층 나침반: 야생 월드 스폰에서 가장 가까운 고대 도시(딥다크) 한 곳만 가리키는 무소모 아이템.
 * 자석석 나침반(lodestone compass)으로 만들어 야생에서는 바늘이 목표를 가리키고 다른 월드에서는 돌아간다.
 * 위치는 config {@code deepdark-finder.target}("x,y,z")가 있으면 그 값을, 없으면 야생 스폰 기준
 * {@code world.locateNearestStructure} 결과를 플러그인 데이터에 한 번만 캐시해 재사용한다.
 * 항상 야생 스폰에서만 찾으므로 다른 곳에서 써도 다른 도시를 찾는 데 악용할 수 없다.
 */
final class DeepDarkFinder implements Listener {
    static final NamespacedKey KEY = NamespacedKey.fromString("pipilife:deepdark_finder");
    static final String TARGET_KEY = "deepdark-finder.target";
    private static final int SEARCH_RADIUS_CHUNKS = 100;
    private static final long COOLDOWN_MS = 1000;

    private final PipiLife plugin;
    private final Map<UUID, Long> cooldown = new HashMap<>();
    private boolean resolved;
    private long retryAt;
    private Location target;

    DeepDarkFinder(PipiLife plugin) { this.plugin = plugin; }

    static ItemStack item() {
        ItemStack it = new ItemStack(Material.COMPASS);
        var meta = it.getItemMeta();
        meta.setDisplayName("§3심층 나침반");
        meta.setLore(List.of("§7야생 스폰 근처의 고대 도시(딥다크)를 가리킵니다", "§7야생에서만 작동합니다"));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.INTEGER, 1);
        it.setItemMeta(meta);
        return it;
    }

    static boolean is(ItemStack it) {
        return it != null && it.getType() == Material.COMPASS && it.hasItemMeta()
            && it.getItemMeta().getPersistentDataContainer().has(KEY, PersistentDataType.INTEGER);
    }

    /** 새 나침반 하나를 만들어 목표를 가리키게 한다 (목표를 못 찾으면 자석 미설정 상태로 준다). */
    ItemStack create() {
        ItemStack it = item();
        Location t = target();
        if (t != null) point(it, t);
        return it;
    }

    private static void point(ItemStack it, Location t) {
        if (t == null || !is(it)) return;
        var meta = it.getItemMeta();
        if (meta instanceof CompassMeta compass) {
            compass.setLodestone(t);
            compass.setLodestoneTracked(false);
            it.setItemMeta(compass);
        }
    }

    private static void bar(Player p, String text) { p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(text)); }

    private static Location parse(String value, World world) {
        if (value == null || value.isBlank() || world == null) return null;
        String[] parts = value.split(",");
        if (parts.length < 3) return null;
        try {
            return new Location(world, Integer.parseInt(parts[0].trim()) + .5, Integer.parseInt(parts[1].trim()),
                Integer.parseInt(parts[2].trim()) + .5);
        } catch (NumberFormatException ex) { return null; }
    }

    /** 야생 스폰 기준 고대 도시 위치. config -> 캐시된 데이터 -> locateNearestStructure 순으로 한 번만 결정한다. */
    Location target() {
        if (resolved) return target;
        long now = System.currentTimeMillis();
        if (now < retryAt) return null;   // a failed search is retried at most once a minute
        retryAt = now + 60_000L;
        World world = Bukkit.getWorld(plugin.wildWorldName());
        Location configured = parse(plugin.getConfig().getString(TARGET_KEY), world);
        if (configured != null) { target = configured; resolved = true; return target; }
        Location cached = parse(plugin.data().getString(TARGET_KEY), world);
        if (cached != null) { target = cached; resolved = true; return target; }
        if (world == null) {
            plugin.getLogger().warning("딥다크 파인더: 야생 월드(" + plugin.wildWorldName() + ")가 로드되지 않아 고대 도시를 찾지 못했습니다.");
            return null;
        }
        try {
            var result = world.locateNearestStructure(world.getSpawnLocation(), Structure.ANCIENT_CITY,
                SEARCH_RADIUS_CHUNKS, false);
            Location found = result == null ? null : result.getLocation();
            if (found == null) {
                plugin.getLogger().warning("딥다크 파인더: 야생 스폰 근처에서 고대 도시를 찾지 못했습니다 (" + world.getName() + ").");
                return null;
            }
            target = found;
            resolved = true;
            plugin.store(TARGET_KEY, found.getBlockX() + "," + found.getBlockY() + "," + found.getBlockZ());
        } catch (Throwable t) {
            plugin.getLogger().warning("딥다크 파인더: locateNearestStructure를 사용할 수 없습니다: " + t);
        }
        return target;
    }

    private static String direction(double dx, double dz) {
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        if (angle < 0) angle += 360;
        String[] names = {"북", "북동", "동", "남동", "남", "남서", "서", "북서"};
        return names[(int) Math.round(angle / 45.0) % 8];
    }

    private void updateHeld(Player p) {
        ItemStack held = p.getInventory().getItemInMainHand();
        if (!is(held)) return;
        Location t = target();
        if (t != null) point(held, t);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void use(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!is(e.getItem())) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND && is(e.getPlayer().getInventory().getItemInMainHand())) return; // main hand handles it
        Player p = e.getPlayer();
        long now = System.currentTimeMillis();
        Long last = cooldown.get(p.getUniqueId());
        if (last != null && now - last < COOLDOWN_MS) return;
        cooldown.put(p.getUniqueId(), now);
        if (!plugin.isWildOverworld(p.getWorld().getName())) { bar(p, "§7야생에서만 작동합니다."); return; }
        Location t = target();
        if (t == null) { bar(p, "§7고대 도시를 찾지 못했습니다"); return; }
        point(e.getItem(), t);
        double dx = t.getX() - p.getLocation().getX(), dz = t.getZ() - p.getLocation().getZ();
        int distance = (int) Math.round(Math.hypot(dx, dz));
        bar(p, "§3고대 도시까지 약 " + distance + "블록 · 방향 " + direction(dx, dz));
    }

    @EventHandler
    public void held(PlayerItemHeldEvent e) {
        ItemStack it = e.getPlayer().getInventory().getItem(e.getNewSlot());
        if (!is(it)) return;
        Location t = target();
        if (t != null) point(it, t);
    }

    @EventHandler public void join(PlayerJoinEvent e) { updateHeld(e.getPlayer()); }
    @EventHandler public void changedWorld(PlayerChangedWorldEvent e) { updateHeld(e.getPlayer()); }
}
