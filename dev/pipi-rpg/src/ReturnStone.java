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
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * 귀환석: right-click -> 3 s channel (moving > 0.5 blocks or taking damage cancels) -> teleport to the residence plot
 * (same target as respawn: PipiLife.homeFor) and consume one. Works in every world except the deep dark raid and the tutorial.
 * Sold by the town shop (TownSystems.buyProducts) for 100,000 gold.
 *
 * Texture hook: vanilla HEART_OF_THE_SEA with CustomModelData {@link #MODEL}; PipiTravel ships
 * assets/minecraft/models/item/heart_of_the_sea.json (override) -> pipitravel:item/return_stone -> textures/item/return_stone.png.
 */
final class ReturnStone implements Listener {
    static final NamespacedKey KEY = NamespacedKey.fromString("pipilife:return_stone");
    static final Material MATERIAL = Material.HEART_OF_THE_SEA;
    static final int MODEL = 48300;
    private static final int CHANNEL_TICKS = 60;

    private final PipiLife plugin;
    private final Map<UUID, Channel> channels = new HashMap<>();

    private static final class Channel {
        final Location from;
        int t;
        Channel(Location from) { this.from = from; }
    }

    ReturnStone(PipiLife plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    static ItemStack item(int amount) {
        ItemStack it = new ItemStack(MATERIAL, Math.max(1, amount));
        var meta = it.getItemMeta();
        meta.setDisplayName("§b귀환석");
        meta.setLore(List.of("§7우클릭하면 3초 뒤 거주지로 귀환합니다.", "§7움직이거나 피해를 받으면 취소됩니다.", "§8사용 시 1개 소모"));
        meta.setCustomModelData(MODEL);
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    static boolean is(ItemStack it) {
        return it != null && it.getType() == MATERIAL && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(KEY, PersistentDataType.BYTE);
    }

    private static void bar(Player p, String text) { p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(text)); }

    private String blocked(Player p) {
        String world = p.getWorld().getName();
        if (world.equals(DeepDarkMap.WORLD) || world.equals(SorrowMap.WORLD)) return "레이드 지역에서는 귀환석을 사용할 수 없습니다.";
        if (world.equals(VillageMaps.TUTORIAL) || !plugin.canTravel(p)) return "튜토리얼 중에는 귀환석을 사용할 수 없습니다.";
        if (RaidDeepDark.ghost(p)) return "지금은 귀환석을 사용할 수 없습니다.";
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void use(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!is(e.getItem())) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND && is(e.getPlayer().getInventory().getItemInMainHand())) return; // main hand handles it
        start(e.getPlayer());
    }

    /** True when the player carries at least one 귀환석 (main inventory or off hand). */
    static boolean has(Player p) {
        for (ItemStack s : p.getInventory().getContents()) if (is(s)) return true;
        return false;
    }

    /** Starts the 3 s channel (right-click, or the warp map home button in the wild). True when it started or is already running. */
    boolean start(Player p) {
        if (channels.containsKey(p.getUniqueId())) return true;
        if (!has(p)) { bar(p, "§c귀환석이 없습니다."); return false; }
        String why = blocked(p);
        if (why != null) { bar(p, "§c" + why); return false; }
        if (plugin.homeFor(p) == null) { bar(p, "§c돌아갈 거주지가 없습니다."); return false; }
        channels.put(p.getUniqueId(), new Channel(p.getLocation().clone()));
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, .7f, 1.6f);
        return true;
    }

    private void cancel(Player p, String why) {
        if (channels.remove(p.getUniqueId()) == null) return;
        bar(p, "§c귀환 취소 · " + why);
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, .7f, 1.4f);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void damaged(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && channels.containsKey(p.getUniqueId())) cancel(p, "피해를 받았습니다");
    }

    @EventHandler public void quit(PlayerQuitEvent e) { channels.remove(e.getPlayer().getUniqueId()); }

    private void tick() {
        if (channels.isEmpty()) return;
        for (Iterator<Map.Entry<UUID, Channel>> it = channels.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            Player p = Bukkit.getPlayer(en.getKey());
            Channel c = en.getValue();
            if (p == null || !p.isOnline() || p.isDead()) { it.remove(); continue; }
            Location l = p.getLocation();
            if (l.getWorld() != c.from.getWorld() || l.distance(c.from) > .5) {
                it.remove();
                bar(p, "§c귀환 취소 · 움직였습니다");
                p.playSound(l, Sound.BLOCK_BEACON_DEACTIVATE, .7f, 1.4f);
                continue;
            }
            c.t++;
            int filled = Math.min(20, c.t * 20 / CHANNEL_TICKS);
            bar(p, "§b귀환 중 §f" + "§b▮".repeat(filled) + "§8▮".repeat(20 - filled) + " §7" + String.format(Locale.ROOT, "%.1f", Math.max(0, CHANNEL_TICKS - c.t) / 20.0) + "초");
            if (c.t % 2 == 0) {
                double a = c.t * .35;
                for (int i = 0; i < 2; i++) {
                    double ang = a + i * Math.PI;
                    p.getWorld().spawnParticle(Particle.END_ROD, l.getX() + Math.cos(ang) * .8, l.getY() + .2 + c.t / (double) CHANNEL_TICKS * 1.8, l.getZ() + Math.sin(ang) * .8, 1, 0, 0, 0, 0);
                }
                p.getWorld().spawnParticle(Particle.PORTAL, l.getX(), l.getY() + 1, l.getZ(), 6, .4, .6, .4, .3);
            }
            if (c.t < CHANNEL_TICKS) continue;
            it.remove();
            finish(p);
        }
    }

    private void finish(Player p) {
        String why = blocked(p);
        if (why != null) { bar(p, "§c" + why); return; }
        Location home = plugin.homeFor(p);
        if (home == null) { bar(p, "§c돌아갈 거주지가 없습니다."); return; }
        if (!consume(p)) { bar(p, "§c귀환석이 없습니다."); return; }
        p.getWorld().spawnParticle(Particle.REVERSE_PORTAL, p.getLocation().add(0, 1, 0), 40, .4, .8, .4, .05);
        p.teleport(home);
        p.setFallDistance(0);
        p.playSound(home, Sound.ENTITY_ENDERMAN_TELEPORT, .8f, 1.2f);
        bar(p, "§b거주지로 귀환했습니다.");
    }

    private static boolean consume(Player p) {
        var inv = p.getInventory();
        ItemStack off = inv.getItemInOffHand();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!is(s)) continue;
            if (s.getAmount() > 1) s.setAmount(s.getAmount() - 1); else inv.setItem(i, null);
            return true;
        }
        if (is(off)) {
            if (off.getAmount() > 1) off.setAmount(off.getAmount() - 1); else inv.setItemInOffHand(null);
            return true;
        }
        return false;
    }
}
