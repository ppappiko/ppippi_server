package dev.pipi.life;

import java.util.ArrayDeque;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Small class-specific 3D impact effect (<= 8 ticks) where a player's hit lands: sword slash, arrow burst, magic orb pop. */
final class HitEffects implements Listener {
    private static final int MAX_LIVE = 40;
    private static final int LIFE = 7;

    private final PipiLife plugin;
    private final ArrayDeque<ItemDisplay> live = new ArrayDeque<>();

    HitEffects(PipiLife plugin) { this.plugin = plugin; }

    void shutdown() {
        for (ItemDisplay d : live) if (d.isValid()) d.remove();
        live.clear();
    }

    private static Material fxMaterial(String id) {
        return Material.matchMaterial("PIPIFACILITIES_FX_" + id.toUpperCase(java.util.Locale.ROOT));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!plugin.getConfig().getBoolean("combat.hit-effects", true)) return;
        Entity damager = event.getDamager();
        boolean projectile = false;
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Entity shooter) { damager = shooter; projectile = true; }
        if (!(damager instanceof Player p) || !(event.getEntity() instanceof LivingEntity victim)
            || victim instanceof Player || victim instanceof ArmorStand) return;
        try {
            int kind = kindOf(p, projectile);
            if (kind < 0) return;
            play(p, victim, kind, DamageNumbers.peekCrit(victim));
        } catch (Throwable ignored) { }
    }

    /** 0 warrior, 1 archer, 2 mage, -1 none. */
    private static int kindOf(Player p, boolean projectile) {
        ItemStack w = Armory.weaponFor(p);
        if (w == null || w.getType() == Material.AIR) w = p.getInventory().getItemInMainHand();
        Material m = w.getType();
        if (m.name().endsWith("_SWORD")) return 0;
        if (m == Material.BOW) return 1;
        if (m == Material.BLAZE_ROD) return 2;
        return projectile ? 1 : -1;
    }

    private void play(Player p, LivingEntity victim, int kind, boolean crit) {
        World w = victim.getWorld();
        Location at = victim.getBoundingBox().getCenter().toLocation(w);
        Vector toward = p.getLocation().toVector().subtract(at.toVector()).setY(0);
        if (toward.lengthSquared() > 1e-6) at.add(toward.normalize().multiply(.3));
        ThreadLocalRandom r = ThreadLocalRandom.current();
        switch (kind) {
            case 0 -> {
                float roll = (float) r.nextDouble(-.9, .9);
                slash(fxMaterial("slash_arc"), at, roll, p);
                // single crisp white line along the cut
                double dx = Math.cos(roll), dy = Math.sin(roll);
                Vector right = new Vector(toward.getZ(), 0, -toward.getX());
                if (right.lengthSquared() < 1e-6) right = new Vector(1, 0, 0); else right.normalize();
                Particle.DustOptions white = new Particle.DustOptions(Color.fromRGB(255, 255, 255), .8f);
                for (int i = -4; i <= 4; i++) {
                    double t = i * .15;
                    Location pt = at.clone().add(right.clone().multiply(dx * t)).add(0, dy * t, 0);
                    w.spawnParticle(Particle.DUST, pt, 1, 0, 0, 0, 0, white);
                }
                w.spawnParticle(Particle.CRIT, at, 1, 0, 0, 0, .1);
            }
            case 1 -> {
                w.spawnParticle(Particle.CRIT, at, 14, .1, .1, .1, .6);
                if (crit) w.spawnParticle(Particle.ENCHANTED_HIT, at, 10, .2, .2, .2, .5);
            }
            default -> {
                pop(fxMaterial("orb"), at, .25f, .55f, (float) r.nextDouble(0, 3), p, 5);
                w.spawnParticle(Particle.WITCH, at, 3, .15, .15, .15, .03);
                w.spawnParticle(Particle.DUST, at, 3, .15, .15, .15, 0, new Particle.DustOptions(Color.fromRGB(170, 70, 235), 1.1f));
            }
        }
        if (crit) {
            Particle.DustOptions gold = new Particle.DustOptions(Color.fromRGB(255, 200, 40), 1.2f);
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                Vector v = new Vector(Math.cos(a), r.nextDouble(-.15, .15), Math.sin(a));
                for (double k = .15; k <= .55; k += .2) w.spawnParticle(Particle.DUST, at.clone().add(v.clone().multiply(k)), 1, 0, 0, 0, 0, gold);
            }
            w.spawnParticle(Particle.END_ROD, at, 5, .1, .1, .1, .12);
        }
    }

    /** Thin, long slash streak that appears instantly and shrinks away in ~4 ticks. */
    private void slash(Material m, Location at, float roll, Player viewer) {
        spawnFx(m, at, new Vector3f(1.4f, .12f, .4f), new Vector3f(1.7f, .02f, .4f), roll, viewer, 4);
    }

    private void pop(Material m, Location at, float from, float to, float roll, Player viewer, int life) {
        spawnFx(m, at, new Vector3f(from, from, from), new Vector3f(to, to, to), roll, viewer, life);
    }

    private void spawnFx(Material m, Location at, Vector3f fromScale, Vector3f toScale, float roll, Player viewer, int life) {
        if (m == null) return;
        while (live.size() >= MAX_LIVE) { ItemDisplay old = live.pollFirst(); if (old != null && old.isValid()) old.remove(); }
        Quaternionf rot = new Quaternionf();
        Vector look = viewer.getLocation().toVector().subtract(at.toVector());
        if (look.lengthSquared() > 1e-6) {
            look.normalize();
            rot.rotateY((float) Math.atan2(look.getX(), look.getZ())).rotateX((float) -Math.asin(Math.max(-1, Math.min(1, look.getY())))).rotateZ(roll);
        }
        Location loc = at.clone();
        loc.setYaw(0);
        loc.setPitch(0);
        ItemDisplay d = at.getWorld().spawn(loc, ItemDisplay.class, display -> {
            display.setItemStack(new ItemStack(m));
            display.setPersistent(false);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setBillboard(Display.Billboard.FIXED);
            display.setViewRange(1.5f);
            display.addScoreboardTag("pipi_hit_fx");
            display.setTransformation(new Transformation(new Vector3f(), rot, fromScale, new Quaternionf()));
        });
        live.addLast(d);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!d.isValid()) return;
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(Math.max(1, life - 2));
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(rot).rotateZ(.2f), toScale, new Quaternionf()));
        }, 1);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (d.isValid()) {
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(1);
                d.setTransformation(new Transformation(new Vector3f(), rot, new Vector3f(0, 0, 0), new Quaternionf()));
            }
        }, life - 1);
        Bukkit.getScheduler().runTaskLater(plugin, () -> { live.remove(d); if (d.isValid()) d.remove(); }, life + 1);
    }
}
