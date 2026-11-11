package dev.pipi.life;

import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.WorldLoadEvent;

/**
 * World-wide basics: keepInventory in every world, and extra village protection that complements
 * TownSystems (block break/place/containers) and Regions (wild protected areas): buckets, fire,
 * farmland trampling, explosions and item frames / armor stands outside the player's own plot.
 */
final class WorldRules implements Listener {
    private final PipiLife plugin;

    WorldRules(PipiLife plugin) {
        this.plugin = plugin;
        for (World world : plugin.getServer().getWorlds()) apply(world);
    }

    private static void apply(World world) {
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        apply(event.getWorld());
    }

    private boolean exempt(Player player) {
        return player.isOp() || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR;
    }

    /** True when the player may not modify this location (village world, outside an owned plot). */
    private boolean denied(Player player, Location location) {
        if (location == null || location.getWorld() == null || exempt(player)) return false;
        if (!location.getWorld().getName().equals(plugin.villageName())) return false;
        return !plugin.owns(player, plugin.plotAt(location));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (denied(event.getPlayer(), event.getBlock().getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (denied(event.getPlayer(), event.getBlock().getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        Player player = event.getPlayer();
        if (player != null) {
            if (denied(player, event.getBlock().getLocation())) event.setCancelled(true);
        } else if (event.getBlock().getWorld().getName().equals(plugin.villageName())) {
            event.setCancelled(true); // lightning, lava, spread
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onTrample(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        Material type = block.getType();
        if ((type == Material.FARMLAND || type == Material.TURTLE_EGG) && denied(event.getPlayer(), block.getLocation()))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        Entity target = event.getRightClicked();
        if ((target instanceof ItemFrame || target instanceof ArmorStand) && denied(event.getPlayer(), target.getLocation()))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (event.getRemover() instanceof Player player && denied(player, event.getEntity().getLocation()))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (event.getLocation().getWorld().getName().equals(plugin.villageName())) event.blockList().clear();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (event.getBlock().getWorld().getName().equals(plugin.villageName())) event.blockList().clear();
    }
}
