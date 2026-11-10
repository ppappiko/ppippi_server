package dev.pipi.life;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.*;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.attribute.Attribute;

final class ClassSkills implements Listener {
    static final int BASIC_COOLDOWN=1_500;
    static final double SWORD_BASIC_REACH=7, SWORD_SKILL_REACH=8;
    private final PipiLife plugin;
    private final Map<UUID, long[]> attacks = new HashMap<>();
    private final Map<UUID, Long> guard = new HashMap<>();
    private final Map<UUID, Long> heals = new HashMap<>();
    private enum Job { WARRIOR, ARCHER, MAGE }

    ClassSkills(PipiLife plugin) { this.plugin = plugin; }

    @EventHandler(ignoreCancelled = true)
    public void shortcut(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        Job job = weaponType(player.getInventory().getItemInMainHand());
        if (!player.isSneaking() || job == null) return;
        event.setCancelled(true);
        cast(player, 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void dodgeDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && combatWorld(player)
            && guard.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis())
            event.setCancelled(true);
    }

    private Job weaponType(ItemStack item) {
        if (item.getType().name().endsWith("_SWORD")) return Job.WARRIOR;
        if (item.getType() == Material.BOW) return Job.ARCHER;
        if (item.getType() == Material.BLAZE_ROD && item.hasItemMeta()
            && item.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(plugin, "smith_catalog"),
                org.bukkit.persistence.PersistentDataType.STRING)) return Job.MAGE;
        return null;
    }

    private boolean combatWorld(Player player) {
        String name = player.getWorld().getName();
        return !name.equals(plugin.villageName()) && !name.equals(VillageMaps.TUTORIAL)
            && (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE);
    }

    boolean cast(Player player, int slot) {
        Job job = weaponType(player.getInventory().getItemInMainHand());
        if (!combatWorld(player)) {
            player.sendMessage("야생 등 전투 가능한 월드에서 서바이벌 또는 모험 모드로 사용하세요.");
            return true;
        }
        if (job == null) {
            player.sendMessage("주 손에 검, 활 또는 대장간 지팡이를 드세요.");
            return true;
        }
        long[] cooldowns = attacks.computeIfAbsent(player.getUniqueId(), ignored -> new long[6]);
        long now = System.currentTimeMillis();
        long remaining = (slot == 0 ? heals.getOrDefault(player.getUniqueId(), 0L) : cooldowns[slot - 1]) - now;
        if (remaining > 0) {
            player.sendMessage("재사용까지 " + (remaining + 999) / 1000 + "초");
            return true;
        }
        if (slot == 0) {
            double max = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
            if (player.getHealth() >= max) return true;
            heals.put(player.getUniqueId(), now + CombatStats.cooldown(player,30_000));
            reportCooldown(player, 0, CombatStats.cooldown(player,30_000));
            double fraction = job == Job.WARRIOR ? .25 : job == Job.ARCHER ? .20 : .30;
            player.setHealth(Math.min(max, player.getHealth() + max * fraction));
            effect(player.getLocation().add(0, 1, 0), Particle.HEART);
            player.sendMessage(switch (job) {
                case WARRIOR -> "전투 재정비";
                case ARCHER -> "사냥꾼의 숨고르기";
                case MAGE -> "치유의 빛";
            });
            return true;
        }
        var world = player.getWorld();
        Location eye = player.getEyeLocation();
        var direction = eye.getDirection();
        if (slot == 6) {
            if (dodge(player, eye)) startCooldown(player, cooldowns, 6, now, 4_000);
            return true;
        }
        if (slot == 5 || slot == 4) {
            Monster target = aim(player, job == Job.WARRIOR ? SWORD_BASIC_REACH : job == Job.ARCHER ? 20 : 14);
            if (target == null) return true;
            boolean basic = slot == 5;
            double base = player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).getValue();
            double damage = switch (job) {
                case WARRIOR -> basic ? base : base * 2.35;
                case ARCHER -> basic ? 3 : 14;
                case MAGE -> basic ? 4 : 15;
            };
            strike(player, target, damage, switch (job) {
                case WARRIOR -> Particle.SWEEP_ATTACK;
                case ARCHER -> Particle.CRIT;
                case MAGE -> Particle.END_ROD;
            });
            startCooldown(player, cooldowns, slot, now, basic ? BASIC_COOLDOWN : 15_000);
            return true;
        }
        if (job == Job.WARRIOR && slot == 1) {
            startCooldown(player, cooldowns, 1, now, 6_000);
            sweep(player, eye, direction);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (ready(player, world, job) && player.getEyeLocation().distanceSquared(eye) <= 4)
                    sweep(player, eye, direction);
            }, 11);
            return true;
        }
        double range = switch (job) { case WARRIOR -> SWORD_SKILL_REACH; case ARCHER -> 20; case MAGE -> 14; };
        Monster target = aim(player, range);
        if (target == null) {
            player.sendMessage("조준선에 몬스터가 없습니다.");
            return true;
        }
        Location impact = target.getLocation().add(0, .8, 0);
        int seconds = switch (job) {
            case WARRIOR -> slot == 2 ? 9 : 12;
            case ARCHER -> slot == 1 ? 5 : slot == 2 ? 8 : 9;
            case MAGE -> slot == 1 ? 5 : slot == 2 ? 8 : 11;
        };
        startCooldown(player, cooldowns, slot, now, seconds * 1000);
        switch (job) {
            case WARRIOR -> {
                effect(impact, Particle.CRIT);
                target.damage(player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).getValue() * (slot == 2 ? 1.65 : 2.0), player);
            }
            case ARCHER -> {
                if (slot == 2) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (ready(player, world, job) && aim(player, range) == target)
                            strike(player, target, 10, Particle.CRIT);
                    }, 8);
                } else {
                    for (int i = 0; i < 3; i++) Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (!ready(player, world, job)) return;
                        if (target.isValid() && target.getWorld() == world
                            && target.getLocation().distanceSquared(player.getLocation()) <= range * range
                            && player.hasLineOfSight(target)) strike(player, target, slot == 3 ? 4 : 3, Particle.CRIT);
                    }, i * 12L);
                }
            }
            case MAGE -> {
                strike(player, target, slot == 1 ? 7 : slot == 2 ? 9 : 13,
                    slot == 2 ? Particle.SNOWFLAKE : Particle.END_ROD);
                if (slot == 2 && target.isValid())
                    target.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS, 50, 1));
                if (slot == 3) effect(impact, Particle.ENCHANT);
            }
        }
        return true;
    }

    private void startCooldown(Player player, long[] cooldowns, int slot, long now, int millis) {
        millis = CombatStats.cooldown(player,millis);
        cooldowns[slot - 1] = now + millis;
        reportCooldown(player, slot, millis);
    }

    private void reportCooldown(Player player, int slot, int millis) {
        String label = switch (slot) {
            case 0 -> "회복";
            case 5 -> "평타";
            case 6 -> "회피";
            default -> "스킬 " + slot;
        };
        player.sendMessage(label + " 재사용 대기 · " + millis + "ms");
    }

    private boolean dodge(Player player, Location eye) {
        var heading = eye.getDirection().setY(0);
        if (heading.lengthSquared() < .01) return false;
        heading.normalize();
        Location origin = player.getLocation();
        if (!origin.getWorld().getWorldBorder().isInside(origin.clone().add(heading.clone().multiply(.75)))) return false;
        boolean sprinting = player.isSprinting();
        var current = player.getVelocity();
        double forward = current.clone().setY(0).dot(heading);
        double speed = sprinting ? Math.max(1.12, forward) : .95;
        player.setVelocity(heading.multiply(speed).setY(Math.max(.38, current.getY())));
        if (sprinting) player.setSprinting(true);
        guard.put(player.getUniqueId(), System.currentTimeMillis() + 450);
        effect(origin, Particle.CLOUD);
        return true;
    }

    private boolean ready(Player player, World world, Job job) {
        return player.isOnline() && !player.isDead() && player.getWorld() == world && combatWorld(player)
            && weaponType(player.getInventory().getItemInMainHand()) == job;
    }

    private Monster aim(Player player, double range) {
        Location eye = player.getEyeLocation();
        var result = player.getWorld().rayTrace(eye, eye.getDirection(), range, FluidCollisionMode.NEVER,
            true, .35, entity -> entity instanceof Monster);
        return result != null && result.getHitEntity() instanceof Monster target ? target : null;
    }

    private void strike(Player player, Monster target, double damage, Particle particle) {
        Location start = player.getEyeLocation(), end = target.getEyeLocation();
        var ray = end.toVector().subtract(start.toVector());
        double length = ray.length();
        if (length > 0) {
            ray.normalize();
            for (double step = .5; step < length; step += .5)
                start.getWorld().spawnParticle(particle, start.clone().add(ray.clone().multiply(step)), 1, 0, 0, 0, 0);
        }
        target.damage(damage + (weaponType(player.getInventory().getItemInMainHand()) == Job.WARRIOR ? 0 : CombatStats.stats(player).attackBonus()), player);
    }

    private void sweep(Player player, Location eye, org.bukkit.util.Vector direction) {
        effect(eye.clone().add(direction), Particle.SWEEP_ATTACK);
        double base = player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).getValue();
        for (var entity : eye.getWorld().getNearbyEntities(eye, 8, 2, 8)) {
            if (!(entity instanceof Monster monster) || !player.hasLineOfSight(monster)) continue;
            var offset = monster.getEyeLocation().toVector().subtract(eye.toVector());
            if (offset.lengthSquared() <= 64 && direction.dot(offset.normalize()) >= .5)
                monster.damage(Math.max(3, base * .75), player);
        }
    }

    private void area(Player player, Location impact, double radius, double damage, Particle particle, Monster... excluded) {
        effect(impact, particle);
        for (var entity : impact.getWorld().getNearbyEntities(impact, radius, radius, radius)) {
            if (entity instanceof Monster monster && (excluded.length == 0 || monster != excluded[0])
                && monster.getLocation().distanceSquared(impact) <= radius * radius && player.hasLineOfSight(monster))
                monster.damage(damage, player);
        }
    }

    private void effect(Location location, Particle particle) {
        location.getWorld().spawnParticle(particle, location, 12, .5, .3, .5, .04);
    }
}
