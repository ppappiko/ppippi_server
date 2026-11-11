package dev.pipi.life;

import java.io.File;
import java.io.IOException;
import java.util.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.event.*;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;

final class TownNpcRegistry implements Listener {
    private final PipiLife plugin;
    private final File file;
    private final YamlConfiguration data;
    private final Set<UUID> duplicates = new HashSet<>();

    TownNpcRegistry(PipiLife plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "npc-registry.yml");
        data = YamlConfiguration.loadConfiguration(file);
        for (String value : data.getStringList("confirmed-duplicates")) duplicates.add(UUID.fromString(value));
    }

    boolean matches(Entity entity, String role) {
        String registered = data.getString("roles." + role);
        return registered != null && registered.equals(entity.getUniqueId().toString());
    }

    boolean exists(World world, String role, Location location) {
        location.getChunk().load();
        restoreLoaded(world);
        String registered = data.getString("roles." + role);
        if (registered != null) return true;
        return world.getNearbyEntities(location, 12, 8, 12).stream()
            .filter(Villager.class::isInstance)
            .filter(e -> e.getPersistentDataContainer().has(key(role), PersistentDataType.BYTE))
            .findFirst().map(e -> { register(e, role); return true; }).orElse(false);
    }

    void register(Entity entity, String role) {
        data.set("roles." + role, entity.getUniqueId().toString());
        restore(entity, role);
        save();
    }

    private NamespacedKey key(String role) { return new NamespacedKey(plugin, "smith_" + role); }

    private void restore(Entity entity, String role) {
        entity.getPersistentDataContainer().set(key(role), PersistentDataType.BYTE, (byte) 1);
        entity.addScoreboardTag("pipilife_npc_" + role);
        if (entity instanceof Villager npc) {
            npc.setAI(false);
            npc.setInvulnerable(true);
            npc.setPersistent(true);
            npc.setRemoveWhenFarAway(false);
            npc.setRecipes(List.of());
        }
    }

    private void restoreLoaded(World world) {
        for (Entity entity : world.getEntities()) restoreEntity(entity);
    }

    private void restoreEntity(Entity entity) {
        if (duplicates.contains(entity.getUniqueId())) {
            entity.remove();
            return;
        }
        for (String role : List.of("merchant", "npc", "alchemist", "armory"))
            if (matches(entity, role)) restore(entity, role);
    }

    @EventHandler
    public void load(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) restoreEntity(entity);
    }

    private void save() {
        try { data.save(file); }
        catch (IOException ex) { throw new IllegalStateException("NPC registry could not be saved", ex); }
    }
}

