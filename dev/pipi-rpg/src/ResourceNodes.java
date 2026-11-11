package dev.pipi.life;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Hand-built resource region: amber rock piles, redwood trees and cabbage crops (PipiFacilities mod blocks).
 * Survival players harvest them, the block is replaced / removed and put back after a configurable delay.
 * Creative players break and place them normally (building). Pending restores survive restarts (resource-nodes.yml).
 */
final class ResourceNodes implements Listener {
    private static final int TREE_CAP = 300;

    private record Pending(String world, int x, int y, int z, String data, long due) {}

    private final PipiLife plugin;
    private final TownSystems town;
    private final Path file;
    private final List<Pending> pending = new ArrayList<>();
    private boolean dirty;
    private boolean warnedMissing;
    private org.bukkit.scheduler.BukkitTask task;

    ResourceNodes(PipiLife plugin, TownSystems town) {
        this.plugin = plugin;
        this.town = town;
        this.file = plugin.getDataFolder().toPath().resolve("resource-nodes.yml");
        load();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40, 20);
    }

    void close() {
        if (task != null) task.cancel();
        save();
    }

    // ---- material lookup (a missing mod block must never break anything) ----

    private static Material mat(String id) { return Material.matchMaterial("PIPIFACILITIES_" + id.toUpperCase(Locale.ROOT)); }
    private static boolean is(Block block, String id) { Material m = mat(id); return m != null && block.getType() == m; }
    private static boolean is(Material type, String id) { Material m = mat(id); return m != null && type == m; }

    private boolean isNode(Material type) {
        return is(type, "amber_rock") || is(type, "rock_pile") || is(type, "redwood_tree_log")
            || is(type, "redwood_tree_leaves") || is(type, "cabbage_crop");
    }

    private ItemStack item(String id, int amount) {
        Material m = ModItems.byIdOrNull(id);
        if (m == null) {
            if (!warnedMissing) { warnedMissing = true; plugin.getLogger().warning("PipiFacilities 모드 아이템이 없습니다: " + id); }
            return null;
        }
        return new ItemStack(m, amount);
    }

    private ItemStack amberCore() {
        try { return town.taggedProduct("amber_core", "호박석 코어"); }
        catch (RuntimeException ex) { return null; }
    }

    private long seconds(String key, int fallback) {
        return Math.max(1, plugin.getConfig().getLong("resource-nodes." + key, fallback));
    }

    private void drop(Location where, ItemStack stack) {
        if (stack == null) return;
        int left = stack.getAmount(), max = Math.max(1, stack.getMaxStackSize());
        while (left > 0) {
            ItemStack part = stack.clone();
            part.setAmount(Math.min(left, max));
            where.getWorld().dropItemNaturally(where, part);
            left -= part.getAmount();
        }
    }

    // ---- events ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isNode(block.getType())) return;
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) return;
        event.setCancelled(true);
        if (player.getGameMode() != GameMode.SURVIVAL) return;
        ItemStack tool = player.getInventory().getItemInMainHand();
        Location center = block.getLocation().add(.5, .5, .5);
        long now = System.currentTimeMillis();
        if (is(block, "amber_rock")) {
            mineAmber(block, player, tool, center, now);
        } else if (is(block, "redwood_tree_log")) {
            if (!tool.getType().name().endsWith("_AXE")) { player.sendMessage("도끼로 베어야 합니다."); return; }
            fellTree(block, now);
        } else if (is(block, "cabbage_crop")) {
            drop(center, item("cabbage_seed", 1));
            addPending(block, now + seconds("cabbage-respawn-seconds", 600) * 1000L);
            block.setType(Material.AIR, false);
        }
        // rock_pile and redwood leaves: cancelled above, nothing else happens.
    }

    private void mineAmber(Block block, Player player, ItemStack tool, Location center, long now) {
        var random = ThreadLocalRandom.current();
        int amount = 1;
        double bonus = Enchants.pickaxe(tool) ? Enchants.sum(tool, "ore") : 0;
        if (bonus > 0) amount += (int) bonus + (random.nextDouble() < bonus % 1 ? 1 : 0);
        drop(center, item("amber", amount));
        double chance = TownSystems.CORE_CHANCE + (Enchants.pickaxe(tool) ? Enchants.sum(tool, "core") : 0);
        if (random.nextDouble(100) < chance) drop(center, amberCore());
        Material pile = mat("rock_pile");
        if (pile == null) return; // cannot show the empty state: leave the rock as it is
        addPending(block, now + seconds("amber-respawn-seconds", 300) * 1000L);
        block.setBlockData(pile.createBlockData(), false);
    }

    private void fellTree(Block start, long now) {
        Set<Block> tree = new LinkedHashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        tree.add(start);
        queue.add(start);
        while (!queue.isEmpty() && tree.size() < TREE_CAP) {
            Block current = queue.poll();
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dy == 0 && dz == 0) continue;
                Block next = current.getRelative(dx, dy, dz);
                if (tree.contains(next) || tree.size() >= TREE_CAP) continue;
                if (is(next, "redwood_tree_log") || is(next, "redwood_tree_leaves")) { tree.add(next); queue.add(next); }
            }
        }
        long due = now + seconds("tree-respawn-seconds", 600) * 1000L;
        int logs = 0;
        for (Block b : tree) {
            if (is(b, "redwood_tree_log")) logs++;
            addPending(b, due);
        }
        drop(start.getLocation().add(.5, .5, .5), item("redwood_log", logs));
        for (Block b : tree) b.setType(Material.AIR, false);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDecay(LeavesDecayEvent event) {
        if (is(event.getBlock(), "redwood_tree_leaves")) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) { event.blockList().removeIf(b -> isNode(b.getType())); }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(BlockExplodeEvent event) { event.blockList().removeIf(b -> isNode(b.getType())); }

    // ---- pending restores ----

    private void addPending(Block block, long due) {
        pending.add(new Pending(block.getWorld().getName(), block.getX(), block.getY(), block.getZ(),
            block.getBlockData().getAsString(), due));
        dirty = true;
    }

    private void tick() {
        long now = System.currentTimeMillis();
        int budget = 400;
        for (Iterator<Pending> it = pending.iterator(); it.hasNext() && budget > 0; ) {
            Pending p = it.next();
            if (p.due() > now) continue;
            World world = Bukkit.getWorld(p.world());
            if (world == null) continue; // world not loaded yet: keep until it is
            budget--;
            it.remove();
            dirty = true;
            try {
                Block block = world.getBlockAt(p.x(), p.y(), p.z());
                if (!block.getType().isAir() && !is(block, "rock_pile")) continue; // never overwrite a build
                BlockData data = Bukkit.createBlockData(p.data());
                block.setBlockData(data, false);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("자원 노드 복구 실패(" + p.world() + " " + p.x() + "," + p.y() + "," + p.z() + "): " + ex.getMessage());
            }
        }
        if (dirty) save();
    }

    private void load() {
        if (!Files.exists(file)) return;
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
            for (Map<?, ?> m : yaml.getMapList("pending")) {
                try {
                    pending.add(new Pending(String.valueOf(m.get("world")), ((Number) m.get("x")).intValue(),
                        ((Number) m.get("y")).intValue(), ((Number) m.get("z")).intValue(),
                        String.valueOf(m.get("data")), ((Number) m.get("due")).longValue()));
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning("resource-nodes.yml 항목을 건너뜁니다: " + ex);
                }
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("resource-nodes.yml 읽기 실패: " + ex);
        }
    }

    private void save() {
        dirty = false;
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            List<Map<String, Object>> list = new ArrayList<>();
            for (Pending p : pending) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("world", p.world());
                m.put("x", p.x());
                m.put("y", p.y());
                m.put("z", p.z());
                m.put("data", p.data());
                m.put("due", p.due());
                list.add(m);
            }
            yaml.set("pending", list);
            Files.createDirectories(file.getParent());
            Files.writeString(file, yaml.saveToString());
        } catch (IOException ex) {
            plugin.getLogger().warning("resource-nodes.yml 저장 실패: " + ex);
        }
    }
}
