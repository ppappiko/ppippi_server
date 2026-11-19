package dev.pipi.life;

import java.util.*;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldLoadEvent;

/**
 * 긴급 탈출: hold R (PipiTravel sends /pipilife escape start, release sends stop) -> 3 s channel -> teleport to the nearest flat,
 * dry ground within 32 blocks (3x3 standable). Cooldown 30 min per player (data escape.&lt;uuid&gt;, survives restarts), charged only on
 * success. For anyone stuck (deep water, pits, walls): no permission needed. Not in the deep dark raid or the tutorial.
 * Also: phantoms never spawn (nobody can sleep on this server) - doInsomnia off in every world, natural phantom spawns cancelled.
 */
final class EmergencyEscape implements Listener {
    static final long COOLDOWN_MS = 30 * 60 * 1000L;
    private static final int CHANNEL_TICKS = 60, RADIUS = 32;
    private final PipiLife plugin;
    private final Map<UUID, Integer> channels = new HashMap<>();
    private final Map<UUID, Location> origin = new HashMap<>();

    EmergencyEscape(PipiLife plugin) {
        this.plugin = plugin;
        for (World w : Bukkit.getWorlds()) noInsomnia(w);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    // ---------------------------------------------------------------- phantoms
    private static void noInsomnia(World w) { if (!Boolean.FALSE.equals(w.getGameRuleValue(GameRule.DO_INSOMNIA))) w.setGameRule(GameRule.DO_INSOMNIA, false); }
    @EventHandler public void worldLoad(WorldLoadEvent e) { noInsomnia(e.getWorld()); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void phantom(CreatureSpawnEvent e) {
        if (e.getEntityType() != EntityType.PHANTOM) return;
        var why = e.getSpawnReason();
        if (why != CreatureSpawnEvent.SpawnReason.COMMAND && why != CreatureSpawnEvent.SpawnReason.CUSTOM) e.setCancelled(true);
    }

    // ---------------------------------------------------------------- escape
    private static void bar(Player p, String text) { p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(text)); }
    private String key(Player p) { return "escape." + p.getUniqueId(); }

    private String blocked(Player p) {
        String world = p.getWorld().getName();
        if (world.equals(DeepDarkMap.WORLD)) return "레이드 지역에서는 긴급 탈출을 쓸 수 없어요.";
        if (world.equals(VillageMaps.TUTORIAL)) return "튜토리얼에서는 긴급 탈출을 쓸 수 없어요.";
        if (RaidDeepDark.ghost(p) || p.isDead()) return "지금은 긴급 탈출을 쓸 수 없어요.";
        return null;
    }

    boolean command(Player p, String[] args) {
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "start";
        if (sub.equals("stop")) { cancel(p, "버튼을 놓았어요"); return true; }
        if (sub.equals("reset") && p.isOp()) {
            Player target = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : p;
            if (target == null) { p.sendMessage("§c접속 중인 플레이어가 아닙니다."); return true; }
            plugin.store(key(target), null);
            p.sendMessage("§a" + target.getName() + "의 긴급 탈출 쿨타임을 초기화했습니다.");
            return true;
        }
        start(p);
        return true;
    }

    private void start(Player p) {
        if (channels.containsKey(p.getUniqueId())) return;
        String why = blocked(p);
        if (why != null) { bar(p, "§c" + why); return; }
        long left = plugin.data().getLong(key(p)) + COOLDOWN_MS - System.currentTimeMillis();
        if (left > 0 && p.getGameMode() != GameMode.CREATIVE) {
            long min = (left + 59_999) / 60_000;
            bar(p, "§c긴급 탈출 쿨타임 §f" + min + "분 §7남았어요");
            return;
        }
        channels.put(p.getUniqueId(), 0);
        origin.put(p.getUniqueId(), p.getLocation().clone());
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, .7f, 1.9f);
    }

    private void cancel(Player p, String why) {
        origin.remove(p.getUniqueId());
        if (channels.remove(p.getUniqueId()) == null) return;
        bar(p, "§c긴급 탈출 취소 · " + why);
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, .7f, 1.6f);
    }

    @EventHandler public void quit(PlayerQuitEvent e) { channels.remove(e.getPlayer().getUniqueId()); origin.remove(e.getPlayer().getUniqueId()); }

    private void tick() {
        if (channels.isEmpty()) return;
        for (var it = channels.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            Player p = Bukkit.getPlayer(en.getKey());
            if (p == null || !p.isOnline() || p.isDead()) { it.remove(); continue; }
            Location from = origin.get(p.getUniqueId());
            // drifting in water is fine; walking away (more than 4 blocks) cancels
            if (from == null || from.getWorld() != p.getWorld() || from.distanceSquared(p.getLocation()) > 16) {
                it.remove(); origin.remove(p.getUniqueId());
                bar(p, "§c긴급 탈출 취소 · 너무 멀리 움직였어요");
                continue;
            }
            int t = en.getValue() + 1;
            en.setValue(t);
            int filled = Math.min(20, t * 20 / CHANNEL_TICKS);
            bar(p, "§e긴급 탈출 §f" + "§e▮".repeat(filled) + "§8▮".repeat(20 - filled) + " §7R 키를 누르고 있으세요");
            if (t % 3 == 0) p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, .3 + t / (double) CHANNEL_TICKS * 1.6, 0), 4, .4, .1, .4, .01);
            if (t < CHANNEL_TICKS) continue;
            it.remove();
            origin.remove(p.getUniqueId());
            finish(p);
        }
    }

    private void finish(Player p) {
        String why = blocked(p);
        if (why != null) { bar(p, "§c" + why); return; }
        Location to = flatGround(p.getLocation());
        if (to == null) { bar(p, "§c근처에 올라갈 평지를 찾지 못했어요. 조금 이동한 뒤 다시 시도하세요."); return; }
        if (p.getGameMode() != GameMode.CREATIVE && !plugin.store(key(p), System.currentTimeMillis())) { bar(p, "§c긴급 탈출을 저장하지 못했어요."); return; }
        if (p.isInsideVehicle()) p.leaveVehicle();
        to.setYaw(p.getLocation().getYaw());
        to.setPitch(p.getLocation().getPitch());
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 1, 0), 30, .4, .8, .4, .05);
        p.teleport(to);
        p.setFallDistance(0);
        p.setRemainingAir(p.getMaximumAir());
        p.playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, .8f, 1.4f);
        bar(p, "§a근처 평지로 탈출했어요 §7· 다음 사용까지 30분");
    }

    // ---------------------------------------------------------------- flat ground search
    private static boolean ground(Block b) {
        Material m = b.getType();
        return m.isSolid() && !b.isLiquid() && m != Material.MAGMA_BLOCK && m != Material.CACTUS && m != Material.CAMPFIRE && m != Material.SOUL_CAMPFIRE
            && m != Material.POWDER_SNOW && m != Material.BARRIER && !m.name().endsWith("_LEAVES") && !Tag.FENCES.isTagged(m) && !Tag.WALLS.isTagged(m);
    }
    private static boolean open(Block b) { return b.isPassable() && !b.isLiquid() && b.getType() != Material.FIRE && b.getType() != Material.SWEET_BERRY_BUSH && b.getType() != Material.POWDER_SNOW; }
    /** Feet cell at (x, y, z): solid dry ground below, two open cells. */
    private static boolean stand(World w, int x, int y, int z) { return ground(w.getBlockAt(x, y - 1, z)) && open(w.getBlockAt(x, y, z)) && open(w.getBlockAt(x, y + 1, z)); }
    private static boolean flat(World w, int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (!stand(w, x + dx, y, z + dz)) return false;
        return true;
    }

    /** Nearest 3x3 flat standable spot within RADIUS (closest ring first; per column the topmost spot at most 24 above / 16 below). */
    private Location flatGround(Location at) {
        World w = at.getWorld();
        int cx = at.getBlockX(), cz = at.getBlockZ(), py = at.getBlockY();
        int top = Math.min(w.getMaxHeight() - 2, py + 24), bottom = Math.max(w.getMinHeight() + 1, py - 16);
        for (int r = 0; r <= RADIUS; r++) {
            Location best = null;
            double bestD = Double.MAX_VALUE;
            for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                int x = cx + dx, z = cz + dz;
                if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
                int start = w.getEnvironment() == World.Environment.NORMAL ? Math.min(top, w.getHighestBlockYAt(x, z) + 1) : top;
                for (int y = start; y >= bottom; y--) {
                    if (!stand(w, x, y, z)) continue;
                    if (flat(w, x, y, z)) {
                        double d = dx * dx + dz * dz + (y - py) * (y - py) * .25;
                        if (d < bestD) { bestD = d; best = new Location(w, x + .5, y, z + .5); }
                    }
                    break; // topmost standable cell of this column only
                }
            }
            if (best != null && (r > 0 || !at.getBlock().isLiquid())) return best;
        }
        return null;
    }
}
