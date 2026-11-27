package dev.pipi.life;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 3D looks for the town NPCs (대장장이 · 상인 · 무기 관리인 · 연금술사; models by dev/art/make-npc-models.mjs in PipiFacilities).
 * The registered villager stays where it is and keeps all the click handling, but turns invisible; an ItemDisplay of its model
 * stands on it and turns toward the nearest player. The display is not saved with the chunk: it is put back whenever the
 * villager's entities load. Without the mod model the villager simply stays visible.
 */
final class TownNpcModels implements Listener {
    static final String TAG = "pipi_npc_model";
    private static final Map<String, String> MODEL = Map.of("npc", "npc_smith", "merchant", "npc_merchant", "armory", "npc_armory", "alchemist", "npc_alchemist");
    private final PipiLife plugin;
    private final TownNpcRegistry npcs;
    private final Map<UUID, UUID> displayOf = new HashMap<>();   // villager -> its model display

    TownNpcModels(PipiLife plugin, TownNpcRegistry npcs) {
        this.plugin = plugin;
        this.npcs = npcs;
        Bukkit.getScheduler().runTaskTimer(plugin, this::face, 45L, 10L);
    }

    /** Dresses every registered town NPC already loaded in the world (a little later: the mod's items may register late). */
    void schedule(World world, int attempt) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (RaidSorrow.fxMaterial("npc_smith") == null && attempt < 6) { schedule(world, attempt + 1); return; }
            int n = 0;
            for (Villager v : world.getEntitiesByClass(Villager.class)) if (dress(v)) n++;
            if (n > 0) plugin.getLogger().info("마을 NPC 모델 " + n + "명 적용");
        }, attempt == 0 ? 70L : 100L);
    }

    private String roleOf(Entity e) {
        if (!(e instanceof Villager)) return null;
        for (String role : MODEL.keySet()) if (npcs.matches(e, role)) return role;
        return null;
    }

    /** Puts the model on a registered town NPC villager; true when it got one now. */
    private boolean dress(Villager v) {
        String role = roleOf(v);
        if (role == null) return false;
        Material material = RaidSorrow.fxMaterial(MODEL.get(role));
        if (material == null) { if (v.isInvisible()) v.setInvisible(false); return false; }
        UUID had = displayOf.get(v.getUniqueId());
        Entity old = had == null ? null : Bukkit.getEntity(had);
        if (old != null && old.isValid()) return false;
        for (Entity e : v.getNearbyEntities(1, 2, 1)) if (e.getScoreboardTags().contains(TAG)) e.remove();
        v.setInvisible(true);
        v.setSilent(true);
        Location at = v.getLocation().add(0, .5, 0);
        at.setPitch(0);
        ItemDisplay d = v.getWorld().spawn(at, ItemDisplay.class, x -> {
            x.setItemStack(new ItemStack(material));
            x.addScoreboardTag(TAG);
            x.setPersistent(false);
            x.setTeleportDuration(8);
        });
        displayOf.put(v.getUniqueId(), d.getUniqueId());
        return true;
    }

    @EventHandler
    public void entitiesLoad(EntitiesLoadEvent event) {
        boolean any = false;
        for (Entity e : event.getEntities()) if (e instanceof Villager) { any = true; break; }
        if (!any) return;
        var list = event.getEntities();
        Bukkit.getScheduler().runTask(plugin, () -> { for (Entity e : list) if (e instanceof Villager v && v.isValid()) dress(v); });
    }

    /** Each model turns toward the nearest player within 8 blocks, back to the villager's own facing when nobody is near. */
    private void face() {
        var it = displayOf.entrySet().iterator();
        while (it.hasNext()) {
            var en = it.next();
            Entity v = Bukkit.getEntity(en.getKey()), d = Bukkit.getEntity(en.getValue());
            if (v == null || d == null || !v.isValid() || !d.isValid()) { if (d != null && v == null) d.remove(); it.remove(); continue; }
            Location base = v.getLocation();
            Player near = null; double best = 64;
            for (Player p : v.getWorld().getPlayers()) { double dist = p.getLocation().distanceSquared(base); if (dist < best) { best = dist; near = p; } }
            float yaw = near == null ? base.getYaw() : (float) Math.toDegrees(Math.atan2(-(near.getLocation().getX() - base.getX()), near.getLocation().getZ() - base.getZ()));
            Location cur = d.getLocation();
            if (Math.abs(((cur.getYaw() - yaw) % 360 + 540) % 360 - 180) < 4) continue;
            Location to = base.clone().add(0, .5, 0); to.setYaw(yaw); to.setPitch(0);
            d.teleport(to);
        }
    }
}
