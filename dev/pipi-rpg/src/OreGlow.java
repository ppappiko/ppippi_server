package dev.pipi.life;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** 반짝임 pickaxe option: ore outlines visible to the holder only. Chunk snapshots are scanned off-thread. */
final class OreGlow implements Listener {
    private static final long COOLDOWN_MS = 5000, SHOW_TICKS = 60;
    private static final int MAX_RADIUS = 12, MAX_SHOWN = 40;
    private final PipiLife plugin;
    private final Map<UUID, Long> last = new HashMap<>();
    private final Set<UUID> pending = new HashSet<>();
    private final Set<BlockDisplay> live = new HashSet<>();

    OreGlow(PipiLife plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40, 20);
    }

    private static boolean ore(Material m) {
        String n = m.name();
        return n.endsWith("_ORE") || m == Material.ANCIENT_DEBRIS;
    }

    private static ChatColor color(Material m) {
        String n = m.name();
        if (n.contains("DIAMOND")) return ChatColor.AQUA;
        if (n.contains("IRON")) return ChatColor.WHITE;
        if (n.contains("GOLD")) return ChatColor.YELLOW;
        if (n.contains("EMERALD")) return ChatColor.GREEN;
        if (n.contains("REDSTONE")) return ChatColor.RED;
        if (n.contains("LAPIS")) return ChatColor.BLUE;
        if (n.contains("COPPER")) return ChatColor.GOLD;
        return ChatColor.GRAY;
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) trigger(p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void mine(BlockBreakEvent event) { trigger(event.getPlayer()); }

    void trigger(Player p) {
        if (!p.isOnline() || p.isDead()) return;
        ItemStack tool = p.getInventory().getItemInMainHand();
        if (!Enchants.pickaxe(tool)) return;
        int radius = (int) Math.min(MAX_RADIUS, Math.round(Enchants.sum(tool, "glow")));
        if (radius <= 0) return;
        long now = System.currentTimeMillis();
        if (pending.contains(p.getUniqueId()) || now - last.getOrDefault(p.getUniqueId(), 0L) < COOLDOWN_MS) return;
        last.put(p.getUniqueId(), now);
        World w = p.getWorld();
        Location c = p.getLocation();
        int cx = c.getBlockX(), cy = c.getBlockY(), cz = c.getBlockZ();
        Map<Long, ChunkSnapshot> snaps = new HashMap<>();
        for (int x = (cx - radius) >> 4; x <= (cx + radius) >> 4; x++)
            for (int z = (cz - radius) >> 4; z <= (cz + radius) >> 4; z++)
                if (w.isChunkLoaded(x, z)) snaps.put(((long) x << 32) ^ (z & 0xffffffffL), w.getChunkAt(x, z).getChunkSnapshot());
        pending.add(p.getUniqueId());
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<int[]> found = new ArrayList<>();
            int minY = w.getMinHeight(), maxY = w.getMaxHeight() - 1;
            for (int x = cx - radius; x <= cx + radius; x++) for (int z = cz - radius; z <= cz + radius; z++) {
                ChunkSnapshot s = snaps.get(((long) (x >> 4) << 32) ^ ((z >> 4) & 0xffffffffL));
                if (s == null) continue;
                for (int y = Math.max(minY, cy - radius); y <= Math.min(maxY, cy + radius); y++) {
                    int dx = x - cx, dy = y - cy, dz = z - cz;
                    int d = dx * dx + dy * dy + dz * dz;
                    if (d > radius * radius) continue;
                    if (ore(s.getBlockType(x & 15, y, z & 15))) found.add(new int[]{x, y, z, d});
                }
            }
            found.sort(Comparator.comparingInt(a -> a[3]));
            List<int[]> picked = found.size() > MAX_SHOWN ? found.subList(0, MAX_SHOWN) : found;
            Bukkit.getScheduler().runTask(plugin, () -> {
                pending.remove(p.getUniqueId());
                if (p.isOnline()) show(p, w, picked);
            });
        });
    }

    private void show(Player p, World w, List<int[]> picked) {
        List<BlockDisplay> made = new ArrayList<>();
        for (int[] a : picked) {
            if (!w.isChunkLoaded(a[0] >> 4, a[2] >> 4)) continue;
            Material m = w.getBlockAt(a[0], a[1], a[2]).getType();
            if (!ore(m)) continue;
            BlockData data = m.createBlockData();
            BlockDisplay d = w.spawn(new Location(w, a[0], a[1], a[2]), BlockDisplay.class, e -> {
                e.setVisibleByDefault(false);
                e.setPersistent(false);
                e.setBlock(data);
                e.setGlowing(true);
                e.setTransformation(new Transformation(new Vector3f(-.005f, -.005f, -.005f), new Quaternionf(),
                    new Vector3f(1.01f, 1.01f, 1.01f), new Quaternionf()));
                var team = glowTeam(color(m));
                if (team != null) team.addEntry(e.getUniqueId().toString());
            });
            p.showEntity(plugin, d);
            made.add(d);
            live.add(d);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (BlockDisplay d : made) { live.remove(d); remove(d); }
        }, SHOW_TICKS);
    }

    private org.bukkit.scoreboard.Team glowTeam(ChatColor color) {
        try {
            var board = Bukkit.getScoreboardManager().getMainScoreboard();
            String name = "pipi_glow_" + color.name().toLowerCase(Locale.ROOT);
            var team = board.getTeam(name);
            if (team == null) { team = board.registerNewTeam(name); team.setColor(color); }
            return team;
        } catch (RuntimeException e) { return null; }
    }

    private void remove(Entity d) {
        try {
            var board = Bukkit.getScoreboardManager().getMainScoreboard();
            for (var t : board.getTeams()) if (t.getName().startsWith("pipi_glow_")) t.removeEntry(d.getUniqueId().toString());
        } catch (RuntimeException ignored) {}
        d.remove();
    }

    void close() {
        for (BlockDisplay d : List.copyOf(live)) remove(d);
        live.clear();
    }
}
