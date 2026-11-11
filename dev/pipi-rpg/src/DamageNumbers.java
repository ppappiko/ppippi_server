package dev.pipi.life;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Floating damage numbers above entities hurt by players (TextDisplay, rises and shrinks over ~16 ticks). */
final class DamageNumbers implements Listener {
    private static final int MAX_LIVE = 60;
    private static final long CRIT_WINDOW_MS = 250;
    private static final Map<UUID, Long> CRITS = new HashMap<>();

    private final PipiLife plugin;
    private final TrainingDummy dummies;
    private final ArrayDeque<TextDisplay> live = new ArrayDeque<>();
    private final Map<UUID, int[]> stacks = new HashMap<>(); // victim -> {lastTick, stackIndex}

    DamageNumbers(PipiLife plugin, TrainingDummy dummies) { this.plugin = plugin; this.dummies = dummies; }

    /** Called by CombatStats when it rolls a critical hit, before the damage is applied. */
    static void markCrit(Entity victim) { CRITS.put(victim.getUniqueId(), System.currentTimeMillis()); }

    /** Non-consuming crit check (HitEffects; must run before takeCrit). */
    static boolean peekCrit(Entity victim) {
        Long at = CRITS.get(victim.getUniqueId());
        return at != null && System.currentTimeMillis() - at <= CRIT_WINDOW_MS;
    }

    private static boolean takeCrit(Entity victim) {
        Long at = CRITS.remove(victim.getUniqueId());
        return at != null && System.currentTimeMillis() - at <= CRIT_WINDOW_MS;
    }

    void shutdown() {
        for (TextDisplay d : live) if (d.isValid()) d.remove();
        live.clear();
        stacks.clear();
        CRITS.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Entity shooter) damager = shooter;
        if (!(damager instanceof Player) || !(event.getEntity() instanceof LivingEntity victim)
            || victim instanceof Player || victim instanceof ArmorStand) return;
        boolean crit = takeCrit(victim);
        double amount = event.getFinalDamage();
        if (dummies != null && dummies.isDummy(victim)) amount = dummies.record(victim, amount, event.getFinalDamage());
        if (!plugin.getConfig().getBoolean("combat.damage-numbers", true)) return;
        long shown = Math.round(amount);
        if (shown <= 0) return;
        spawn(victim, shown, crit);
    }

    private void spawn(LivingEntity victim, long amount, boolean crit) {
        int now = (int) (System.currentTimeMillis() / 50);
        int[] st = stacks.computeIfAbsent(victim.getUniqueId(), k -> new int[]{-100, 0});
        st[1] = now - st[0] <= 10 ? st[1] + 1 : 0;
        st[0] = now;
        if (stacks.size() > 200) stacks.entrySet().removeIf(e -> now - e.getValue()[0] > 40);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Location at = victim.getLocation().add(r.nextDouble(-.35, .35), victim.getHeight() + .75 + (st[1] % 5) * .55, r.nextDouble(-.35, .35));
        while (live.size() >= MAX_LIVE) { TextDisplay old = live.pollFirst(); if (old != null && old.isValid()) old.remove(); }
        float scale = crit ? 3.0f : 2.2f;
        String text = crit ? "§6§l✦" + amount + "§e§l!" : "§f§l" + amount;
        TextDisplay d = at.getWorld().spawn(at, TextDisplay.class, display -> {
            display.setText(text);
            display.setPersistent(false);
            display.setBillboard(Display.Billboard.CENTER);
            display.setSeeThrough(false);
            display.setShadowed(true);
            display.setBackgroundColor(Color.fromARGB(crit ? 150 : 110, 20, 10, 0));
            display.setBrightness(new Display.Brightness(15, 15));
            display.setViewRange(.8f);
            display.setLineWidth(200);
            display.addScoreboardTag("pipi_dmg_num");
            display.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale, scale, scale), new Quaternionf()));
        });
        live.addLast(d);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!d.isValid()) return;
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(20);
            d.setTransformation(new Transformation(new Vector3f(0, 1.4f, 0), new Quaternionf(), new Vector3f(scale * .6f, scale * .6f, scale * .6f), new Quaternionf()));
        }, 1);
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (d.isValid()) d.setTextOpacity((byte) 120); }, 17);
        Bukkit.getScheduler().runTaskLater(plugin, () -> { live.remove(d); if (d.isValid()) d.remove(); }, 22);
    }
}
