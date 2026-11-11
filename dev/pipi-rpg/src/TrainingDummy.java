package dev.pipi.life;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Husk;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.inventory.EntityEquipment;

/** 허수아비: stationary Husk (tag pipi_dummy) that never dies and shows DPS / last / total in its name. */
final class TrainingDummy implements Listener {
    static final String TAG = "pipi_dummy";
    private static final long WINDOW_MS = 5_000;
    private static final double HP = 1024;

    private final PipiLife plugin;
    private final Map<UUID, Double> real = new HashMap<>();
    private final Map<UUID, double[]> stats = new HashMap<>(); // {total, windowDamage, windowStartMs, lastHitMs, last}
    private final Map<UUID, TextDisplay> labels = new HashMap<>();
    private static final String LABEL_TAG = "pipi_dummy_label";
    private static final String DEFAULT_TEXT = "§e§l허수아비\n§7공격해서 DPS 측정";

    TrainingDummy(PipiLife plugin) {
        this.plugin = plugin;
        for (World w : Bukkit.getWorlds()) for (Entity e : w.getEntities()) if (e instanceof TextDisplay td && td.getScoreboardTags().contains(LABEL_TAG)) td.remove();
        for (World w : Bukkit.getWorlds()) for (Entity e : w.getEntities()) if (isDummy(e) && e instanceof LivingEntity le) setup(le);
        Bukkit.getScheduler().runTaskTimer(plugin, this::followLabels, 2, 2);
    }

    boolean isDummy(Entity e) { return e instanceof Husk && e.getScoreboardTags().contains(TAG); }

    void shutdown() {
        for (TextDisplay t : labels.values()) if (t.isValid()) t.remove();
        labels.clear(); real.clear(); stats.clear();
    }

    private void followLabels() {
        labels.entrySet().removeIf(en -> {
            Entity owner = Bukkit.getEntity(en.getKey());
            TextDisplay t = en.getValue();
            if (owner == null || !owner.isValid()) { if (t.isValid()) t.remove(); return true; }
            if (!t.isValid()) return true;
            Location at = owner.getLocation().add(0, owner.getHeight() + 1.0, 0);
            if (t.getWorld() != at.getWorld() || t.getLocation().distanceSquared(at) > 0.0001) t.teleport(at);
            return false;
        });
        for (World w : Bukkit.getWorlds()) for (Entity e : w.getEntitiesByClass(Husk.class)) if (isDummy(e) && !labels.containsKey(e.getUniqueId())) label((LivingEntity) e, DEFAULT_TEXT);
    }

    private void label(LivingEntity e, String text) {
        TextDisplay t = labels.get(e.getUniqueId());
        if (t == null || !t.isValid()) {
            Location at = e.getLocation().add(0, e.getHeight() + 1.0, 0);
            t = e.getWorld().spawn(at, TextDisplay.class, d -> {
                d.setPersistent(false);
                d.setBillboard(Display.Billboard.CENTER);
                d.setSeeThrough(false);
                d.setShadowed(true);
                d.setBackgroundColor(org.bukkit.Color.fromARGB(110, 0, 0, 0));
                d.setBrightness(new Display.Brightness(15, 15));
                d.setViewRange(.6f);
                d.setLineWidth(300);
                d.setTeleportDuration(2);
                d.addScoreboardTag(LABEL_TAG);
            });
            labels.put(e.getUniqueId(), t);
        }
        t.setText(text);
    }

    @SuppressWarnings("deprecation")
    private void setup(LivingEntity e) {
        e.setAI(false);
        e.setSilent(true);
        e.setPersistent(true);
        e.setRemoveWhenFarAway(false);
        e.setCanPickupItems(false);
        e.setFireTicks(0);
        e.setMaximumNoDamageTicks(0);
        e.setCollidable(false);
        e.addScoreboardTag(TAG);
        if (e instanceof org.bukkit.entity.Mob m) { m.setAware(false); m.setTarget(null); }
        if (e instanceof Husk h) h.setBaby(false);
        EntityEquipment eq = e.getEquipment();
        if (eq != null) { eq.clear(); }
        AttributeInstance max = e.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (max != null) max.setBaseValue(HP);
        AttributeInstance kb = e.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE);
        if (kb != null) kb.setBaseValue(1);
        e.setHealth(HP);
        e.setCustomName("§e허수아비");
        e.setCustomNameVisible(false);
        label(e, DEFAULT_TEXT);
    }

    LivingEntity spawn(Location at) {
        Location loc = at.clone();
        return loc.getWorld().spawn(loc, Husk.class, h -> setup(h));
    }

    private static boolean admin(Player p) { return p.isOp() || p.hasPermission("pipilife.admin"); }

    boolean command(Player p, String[] args) {
        if (!admin(p)) { p.sendMessage("§c관리자 전용 명령어입니다."); return true; }
        String sub = args.length > 1 ? args[1].toLowerCase(java.util.Locale.ROOT) : "";
        if (sub.equals("spawn")) {
            Location loc = p.getLocation();
            loc.setYaw(loc.getYaw() + 180);
            loc.setPitch(0);
            spawn(loc);
            p.sendMessage("§a허수아비를 소환했습니다. §7/pipilife dummy remove 로 가장 가까운 허수아비를 제거합니다.");
        } else if (sub.equals("remove")) {
            LivingEntity best = null;
            double bd = 25;
            for (Entity e : p.getNearbyEntities(5, 5, 5)) {
                if (!isDummy(e)) continue;
                double d = e.getLocation().distanceSquared(p.getLocation());
                if (d <= bd) { bd = d; best = (LivingEntity) e; }
            }
            if (best == null) p.sendMessage("§c5블록 안에 허수아비가 없습니다.");
            else {
                stats.remove(best.getUniqueId());
                TextDisplay lt = labels.remove(best.getUniqueId());
                if (lt != null && lt.isValid()) lt.remove();
                real.remove(best.getUniqueId());
                best.remove();
                p.sendMessage("§a허수아비를 제거했습니다.");
            }
        } else p.sendMessage("§e/pipilife dummy spawn | remove");
        return true;
    }

    List<String> complete(String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 2) out.addAll(List.of("spawn", "remove"));
        return out;
    }

    // ---- invulnerability: never dies, never hurt by anything except player attacks

    @EventHandler(priority = EventPriority.LOWEST)
    public void anyDamage(EntityDamageEvent event) {
        if (!isDummy(event.getEntity())) return;
        if (!(event instanceof EntityDamageByEntityEvent by)) { event.setCancelled(true); return; }
        Entity d = by.getDamager();
        if (d instanceof Projectile pr && pr.getShooter() instanceof Entity s) d = s;
        if (!(d instanceof Player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void lethal(EntityDamageEvent event) {
        if (!isDummy(event.getEntity()) || !(event.getEntity() instanceof LivingEntity le)) return;
        double fin = event.getFinalDamage();
        real.put(le.getUniqueId(), fin);
        if (fin >= le.getHealth() - 1) event.setDamage(0);
    }

    @EventHandler(ignoreCancelled = true)
    public void combust(EntityCombustEvent event) { if (isDummy(event.getEntity())) event.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void target(EntityTargetEvent event) { if (isDummy(event.getEntity())) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void death(EntityDeathEvent event) {
        if (!isDummy(event.getEntity())) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
    }

    /** Called from DamageNumbers at MONITOR: records the hit, refreshes the name, restores health. Returns the real damage. */
    double record(LivingEntity e, double finalDamage, double reported) {
        double amount = real.getOrDefault(e.getUniqueId(), reported);
        real.remove(e.getUniqueId());
        long now = System.currentTimeMillis();
        double[] s = stats.computeIfAbsent(e.getUniqueId(), k -> new double[5]);
        if (s[2] == 0 || now - s[3] > WINDOW_MS) { s[1] = 0; s[2] = now; }
        s[0] += amount; s[1] += amount; s[3] = now; s[4] = amount;
        double dps = s[1] / Math.max(1.0, (now - s[2]) / 1000.0);
        label(e, String.format(java.util.Locale.ROOT, "§e§l허수아비\n§cDPS %,d §7| §f마지막 %,d §7| §f총 %,d",
            Math.round(dps), Math.round(s[4]), Math.round(s[0])));
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!e.isValid()) return;
            e.setHealth(HP);
            e.setFireTicks(0);
            e.setVelocity(new org.bukkit.util.Vector());
        });
        return amount;
    }
}
