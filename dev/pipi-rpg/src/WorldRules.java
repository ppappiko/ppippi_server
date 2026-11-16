package dev.pipi.life;

import java.util.List;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.MoistureChangeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.ItemStack;

/**
 * World-wide basics: keepInventory in every world, and the residence-world map protection.
 *
 * Residence worlds (pipi_village2 and the retired old village) are read-only for everybody except creative/spectator
 * players (OPs in survival included): every change to terrain, roads, the gateway plaza, water, plants and hanging /
 * vehicle entities is refused unless the location lies inside the acting player's own plot. Environmental changes
 * (fire, explosions, leaf decay, fluid spread, farmland drying, mob griefing ...) are refused outside plots.
 * Handlers run at HIGHEST without ignoreCancelled so that nothing can silently "un-protect" a world.
 */
final class WorldRules implements Listener {
    private final PipiLife plugin;

    WorldRules(PipiLife plugin) {
        this.plugin = plugin;
        for (World world : plugin.getServer().getWorlds()) apply(world);
    }

    private static void apply(World world) {
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        if (world.getName().equals(Village2.WORLD) || world.getName().equals(TownMap.WORLD)) { // calm rules: no mobs, fixed noon, clear sky, nothing burns, no mob griefing
            world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            world.setGameRule(GameRule.DO_FIRE_TICK, false);
            world.setGameRule(GameRule.MOB_GRIEFING, false);
            world.setStorm(false);
            world.setTime(6000);
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        apply(event.getWorld());
    }

    // ---- helpers ----

    /** Only creative / spectator are free to build the town; OPs in survival are treated like everybody else. */
    private static boolean exempt(Player player) {
        return player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR;
    }

    private boolean guarded(Location location) {
        return location != null && location.getWorld() != null && plugin.isTownWorld(location.getWorld().getName());
    }

    private boolean guarded(Block block) { return block != null && plugin.isTownWorld(block.getWorld().getName()); }

    /** True when the player may not modify this location (residence world, outside the player's own plot). */
    private boolean denied(Player player, Location location) {
        if (!guarded(location) || player == null || exempt(player)) return false;
        return !plugin.owns(player, plugin.plotAt(location));
    }

    private boolean denied(Player player, Block block) { return block != null && denied(player, block.getLocation()); }

    /** True when an environmental (non-player) change at this location must be refused: guarded world and no plot there. */
    private boolean envDenied(Location location) {
        return guarded(location) && plugin.plotAt(location) == null;
    }

    private Player playerOf(Entity entity) {
        if (entity instanceof Player p) return p;
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player p) return p;
        return null;
    }

    // ---- block break / place (final say, even if another plugin let it through) ----

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent event) {
        if (denied(event.getPlayer(), event.getBlock())) { event.setCancelled(true); event.setDropItems(false); }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlace(BlockPlaceEvent event) {
        if (denied(event.getPlayer(), event.getBlockPlaced())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (denied(event.getPlayer(), event.getBlock()) || denied(event.getPlayer(), event.getBlockClicked())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (denied(event.getPlayer(), event.getBlock()) || denied(event.getPlayer(), event.getBlockClicked())) event.setCancelled(true);
    }

    // ---- right-click tools / items that change the world without a break/place event ----

    private static boolean worldChanging(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        String n = item.getType().name();
        return n.endsWith("_HOE") || n.endsWith("_AXE") || n.endsWith("_SHOVEL") || n.equals("BONE_MEAL")
            || n.equals("FLINT_AND_STEEL") || n.equals("FIRE_CHARGE") || n.endsWith("_SPAWN_EGG") || n.endsWith("_MINECART")
            || n.endsWith("_BOAT") || n.endsWith("_RAFT") || n.equals("ARMOR_STAND") || n.endsWith("ITEM_FRAME")
            || n.equals("PAINTING") || n.equals("END_CRYSTAL") || n.equals("HONEYCOMB") || n.endsWith("_DYE")
            || n.equals("GLOW_INK_SAC") || n.equals("INK_SAC") || n.equals("GLASS_BOTTLE") || n.equals("NAME_TAG") || n.equals("LEAD") || n.equals("TNT")
            || n.endsWith("_BUCKET") || n.equals("SHEARS") || n.equals("BRUSH") || n.equals("POWDER_SNOW_BUCKET");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (block == null || exempt(player) || !guarded(block)) return;
        if (event.getAction() == Action.PHYSICAL) {
            Material type = block.getType();
            if ((type == Material.FARMLAND || type == Material.TURTLE_EGG) && denied(player, block)) event.setCancelled(true);
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        boolean place = event.getAction() == Action.RIGHT_CLICK_BLOCK;
        Block target = block.getRelative(event.getBlockFace());
        boolean ownBlock = !denied(player, block);
        boolean ownTarget = !denied(player, target);
        // fire / extinguishing: left-click on a fire block puts it out (a world change)
        if (!place) {
            if (event.getAction() == Action.LEFT_CLICK_BLOCK && target.getType() == Material.FIRE && !ownTarget) event.setCancelled(true);
            return;
        }
        if (ownBlock && ownTarget) return;
        if (worldChanging(event.getItem())) {
            event.setCancelled(true);
            return;
        }
        // containers / signs / pots etc. of non-owned land (warp stones and NPC blocks are neither)
        Material m = block.getType();
        String n = m.name();
        if (!ownBlock && (block.getState() instanceof org.bukkit.block.Container || n.endsWith("_SIGN") || n.endsWith("_HANGING_SIGN")
            || n.startsWith("POTTED_") || n.endsWith("FLOWER_POT") || n.endsWith("CAKE") || n.equals("COMPOSTER") || n.endsWith("CAMPFIRE")
            || n.equals("LECTERN") || n.equals("JUKEBOX") || n.equals("CHISELED_BOOKSHELF") || n.equals("DECORATED_POT")
            || n.equals("RESPAWN_ANCHOR") || n.equals("SWEET_BERRY_BUSH") || n.equals("BEEHIVE") || n.equals("BEE_NEST")
            || n.equals("CAVE_VINES") || n.equals("CAVE_VINES_PLANT") || n.equals("DRAGON_EGG") || n.endsWith("CAULDRON") || n.equals("BELL")
            || n.equals("BREWING_STAND") || n.equals("DAYLIGHT_DETECTOR") || n.equals("ITEM_FRAME") || n.equals("GLOW_ITEM_FRAME"))) {
            event.setCancelled(true);
        }
    }

    /** Decorative containers (barrels, chests, furnaces, hoppers, shulkers ...) can never be opened outside one's own plot, whoever tries. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player) || exempt(player)) return;
        var inventory = event.getInventory();
        Location at = inventory.getLocation();
        if (at == null || !guarded(at)) return;
        var holder = inventory.getHolder();
        if (holder instanceof org.bukkit.block.BlockState || holder instanceof org.bukkit.block.DoubleChest || holder instanceof org.bukkit.inventory.BlockInventoryHolder) {
            if (denied(player, at)) event.setCancelled(true);
        }
    }

    // ---- hanging / vehicle / armor stand entities ----

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        Entity target = event.getRightClicked();
        if ((target instanceof Hanging || target instanceof ArmorStand || target instanceof Vehicle) && denied(event.getPlayer(), target.getLocation()))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (denied(event.getPlayer(), event.getRightClicked().getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (event.getPlayer() != null && (denied(event.getPlayer(), event.getBlock()) || denied(event.getPlayer(), event.getEntity().getLocation())))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHangingBreak(HangingBreakEvent event) {
        if (!guarded(event.getEntity().getLocation())) return;
        if (event instanceof HangingBreakByEntityEvent byEntity) {
            Player player = playerOf(byEntity.getRemover());
            if (player != null) {
                if (denied(player, event.getEntity().getLocation())) event.setCancelled(true);
                return;
            }
        }
        if (plugin.plotAt(event.getEntity().getLocation()) == null) event.setCancelled(true); // explosions, mobs, obstruction
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityHit(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        if (!(victim instanceof Hanging || victim instanceof ArmorStand || victim instanceof Vehicle
            || victim instanceof org.bukkit.entity.EnderCrystal) || !guarded(victim.getLocation())) return;
        Player player = playerOf(event.getDamager());
        if (player != null ? denied(player, victim.getLocation()) : plugin.plotAt(victim.getLocation()) == null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVehicleDamage(VehicleDamageEvent event) {
        if (!guarded(event.getVehicle().getLocation())) return;
        Player player = playerOf(event.getAttacker());
        if (player != null ? denied(player, event.getVehicle().getLocation()) : plugin.plotAt(event.getVehicle().getLocation()) == null)
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityPlace(org.bukkit.event.entity.EntityPlaceEvent event) {
        if (event.getPlayer() != null && (denied(event.getPlayer(), event.getBlock()) || denied(event.getPlayer(), event.getEntity().getLocation())))
            event.setCancelled(true);
    }

    // ---- environment: fire, explosions, spread, decay, fluids, farmland, mobs, pistons ----

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onIgnite(BlockIgniteEvent event) {
        Player player = event.getPlayer();
        if (player != null) {
            if (denied(player, event.getBlock())) event.setCancelled(true);
        } else if (guarded(event.getBlock())) {
            event.setCancelled(true); // lightning, lava, spread, dispensers
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBurn(BlockBurnEvent event) {
        if (guarded(event.getBlock())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpread(BlockSpreadEvent event) {
        if (envDenied(event.getBlock().getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFade(BlockFadeEvent event) {
        if (envDenied(event.getBlock().getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onForm(BlockFormEvent event) {
        if (envDenied(event.getBlock().getLocation())) event.setCancelled(true); // snow, ice, concrete powder, lava/water mixing
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockGrow(BlockGrowEvent event) {
        if (envDenied(event.getBlock().getLocation())) event.setCancelled(true); // crops, cane, berries, kelp keep their decorative state
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMoisture(MoistureChangeEvent event) {
        if (envDenied(event.getBlock().getLocation())) event.setCancelled(true); // farmland drying back into dirt
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLeaves(LeavesDecayEvent event) {
        if (guarded(event.getBlock())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGrow(StructureGrowEvent event) {
        Player player = event.getPlayer();
        if (!guarded(event.getLocation())) return;
        if (player != null ? denied(player, event.getLocation()) : plugin.plotAt(event.getLocation()) == null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFlow(BlockFromToEvent event) {
        if (!guarded(event.getBlock())) return;
        String from = plugin.plotAt(event.getBlock().getLocation());
        String to = plugin.plotAt(event.getToBlock().getLocation());
        if (from == null || !from.equals(to)) event.setCancelled(true); // water / lava never leaves a plot or floods the terrain
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityChange(EntityChangeBlockEvent event) {
        if (!guarded(event.getBlock())) return;
        if (event.getEntity() instanceof Player player) {
            if (denied(player, event.getBlock())) event.setCancelled(true);
        } else if (plugin.plotAt(event.getBlock().getLocation()) == null) {
            event.setCancelled(true); // endermen, falling blocks, trampling mobs, ravagers ...
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (guarded(event.getLocation())) { event.blockList().clear(); }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (guarded(event.getBlock())) event.blockList().clear();
    }

    private boolean pistonDenied(Block piston, List<Block> moved, BlockFace dir) {
        if (!guarded(piston)) return false;
        String home = plugin.plotAt(piston.getLocation());
        if (home == null) return true;
        for (Block b : moved) {
            if (!home.equals(plugin.plotAt(b.getLocation())) || !home.equals(plugin.plotAt(b.getRelative(dir).getLocation()))) return true;
        }
        return !home.equals(plugin.plotAt(piston.getRelative(dir).getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (pistonDenied(event.getBlock(), event.getBlocks(), event.getDirection())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (pistonDenied(event.getBlock(), event.getBlocks(), event.getDirection())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDispense(BlockDispenseEvent event) {
        if (!guarded(event.getBlock())) return;
        Material type = event.getItem().getType();
        String n = type.name();
        if (!(n.endsWith("_BUCKET") || n.equals("FIRE_CHARGE") || n.equals("FLINT_AND_STEEL") || n.equals("TNT") || n.endsWith("_SPAWN_EGG")
            || n.equals("BONE_MEAL") || n.endsWith("_BOAT") || n.endsWith("_MINECART") || n.equals("ARMOR_STAND") || n.equals("SHEARS")
            || n.endsWith("_SHULKER_BOX") || n.equals("SHULKER_BOX") || n.equals("GLASS_BOTTLE") || n.equals("END_CRYSTAL"))) return;
        BlockFace face = event.getBlock().getBlockData() instanceof Directional d ? d.getFacing() : BlockFace.UP;
        String home = plugin.plotAt(event.getBlock().getLocation());
        if (home == null || !home.equals(plugin.plotAt(event.getBlock().getRelative(face).getLocation()))) event.setCancelled(true);
    }
}
