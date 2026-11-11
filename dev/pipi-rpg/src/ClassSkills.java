package dev.pipi.life;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Class skills (see dev/plans/skills.md). Slots: 0 heal, 1-3 skills, 4 ultimate, 5 basic, 6 dash jump.
 * Damage = attack power x coefficient, dealt through LivingEntity#damage so CombatStats (dmg%, boss%, crit) still applies.
 */
final class ClassSkills implements Listener {
    private static final int MAX_DISPLAYS = 40;
    private final PipiLife plugin;
    private final Map<UUID, long[]> cooldowns = new HashMap<>();
    private final Map<UUID, Long> will = new HashMap<>();        // 투지: -20% damage until
    private final Map<UUID, Long> fallImmune = new HashMap<>();  // dash: no fall damage until
    private final Map<UUID, Long> lastGround = new HashMap<>();
    private final Set<Display> displays = new HashSet<>();
    private boolean flip;
    private double beamLen = 20;
    private enum Job { WARRIOR, ARCHER, MAGE }

    // ---------------------------------------------------------------- loadout (equipped general skills)

    /** Cooldown / charge array size: 8 fixed slots + 5 general skills per class. */
    private static final int KEYS = 8 + 15;
    /** General (equippable) skill ids per class; the index is the skill's ordinal used for cooldown keys. */
    private static final String[][] IDS = {
        {"w_rush", "w_whirl", "w_split", "w_leap", "w_flurry"},
        {"a_triple", "a_pierce", "a_rain", "a_venom", "a_retreat"},
        {"m_seek", "m_frost", "m_thunder", "m_vortex", "m_prison"}};
    private static final char[] CLS = {'w', 'a', 'm'};
    private static ClassSkills current;
    private final Map<UUID, int[][]> loadouts = new HashMap<>(); // per player, per class: 3 skill ordinals for keys 1-3
    private final Map<String, Long> poisons = new HashMap<>();   // targetId|playerId -> expiry millis of the latest poison
    private final java.io.File loadoutFile;
    private final org.bukkit.configuration.file.YamlConfiguration loadoutData;

    private static int gen(Job job, int ord) { return 8 + job.ordinal() * 5 + ord; }

    private static int[][] defaultLoadout() { return new int[][] {{0, 1, 2}, {0, 1, 2}, {0, 1, 2}}; }

    private int[] loadout(Player p, Job job) {
        return loadouts.computeIfAbsent(p.getUniqueId(), k -> defaultLoadout())[job.ordinal()];
    }

    private static String loadoutText(int job, int[] ords) {
        return IDS[job][ords[0]] + "," + IDS[job][ords[1]] + "," + IDS[job][ords[2]];
    }

    private void loadLoadouts() {
        for (String id : loadoutData.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(id);
                int[][] all = defaultLoadout();
                for (int j = 0; j < 3; j++) {
                    String text = loadoutData.getString(id + "." + CLS[j]);
                    if (text == null) continue;
                    String[] parts = text.split(",");
                    if (parts.length != 3) continue;
                    int[] ords = new int[3];
                    boolean ok = true;
                    for (int k = 0; k < 3; k++) {
                        ords[k] = java.util.Arrays.asList(IDS[j]).indexOf(parts[k].trim());
                        if (ords[k] < 0) ok = false;
                    }
                    if (ok && ords[0] != ords[1] && ords[0] != ords[2] && ords[1] != ords[2]) all[j] = ords;
                }
                loadouts.put(uuid, all);
            } catch (IllegalArgumentException ignored) { }
        }
    }

    private void saveLoadouts(Player p) {
        int[][] all = loadouts.get(p.getUniqueId());
        if (all == null) return;
        for (int j = 0; j < 3; j++) loadoutData.set(p.getUniqueId() + "." + CLS[j], loadoutText(j, all[j]));
        try { loadoutData.save(loadoutFile); } catch (java.io.IOException e) { plugin.getLogger().warning("loadouts.yml 저장 실패: " + e.getMessage()); }
    }

    /** /pipilife skill loadout &lt;w|a|m&gt; &lt;id1&gt; &lt;id2&gt; &lt;id3&gt;: only outside combat mode. */
    boolean loadoutCommand(Player p, String[] args) {
        if (Armory.inCombatMode(p)) { p.sendMessage("전투 모드에서는 스킬 장착을 바꿀 수 없어요. (Alt로 전투 모드를 끄세요)"); return true; }
        if (args.length != 6) { p.sendMessage("/pipilife skill loadout <w|a|m> <스킬1> <스킬2> <스킬3>"); return true; }
        int j = switch (args[2].toLowerCase(java.util.Locale.ROOT)) {
            case "w", "warrior" -> 0; case "a", "archer" -> 1; case "m", "mage" -> 2; default -> -1; };
        if (j < 0) { p.sendMessage("직업은 w, a, m 중 하나여야 해요."); return true; }
        int[] ords = new int[3];
        for (int k = 0; k < 3; k++) {
            ords[k] = java.util.Arrays.asList(IDS[j]).indexOf(args[3 + k]);
            if (ords[k] < 0) { p.sendMessage("이 직업의 스킬이 아니에요: " + args[3 + k]); return true; }
        }
        if (ords[0] == ords[1] || ords[0] == ords[2] || ords[1] == ords[2]) { p.sendMessage("같은 스킬을 두 번 장착할 수 없어요."); return true; }
        loadouts.computeIfAbsent(p.getUniqueId(), k -> defaultLoadout())[j] = ords;
        saveLoadouts(p);
        syncLoadouts(p);
        return true;
    }

    /** Hidden PIPI_LOADOUT messages for all three classes (client cancels them), then the cooldowns of the skills now in keys 1-3. */
    void syncLoadouts(Player p) {
        int[][] all = loadouts.computeIfAbsent(p.getUniqueId(), k -> defaultLoadout());
        for (int j = 0; j < 3; j++) p.sendMessage("PIPI_LOADOUT:" + CLS[j] + ":" + loadoutText(j, all[j]));
        Job job = weaponType(p.getInventory().getItemInMainHand());
        if (job == null) job = weaponType(Armory.weaponFor(p));
        long[] cd = cooldowns.get(p.getUniqueId());
        if (job == null || cd == null) return;
        long now = System.currentTimeMillis();
        int[] mine = all[job.ordinal()];
        for (int slot = 1; slot <= 3; slot++) {
            int key = gen(job, mine[slot - 1]), max = maxCharges(job, key);
            if (max > 1) {
                int[] c = refillCharges(p, job, key, max, cd, now);
                if (c[key] < max) p.sendMessage("스킬 " + slot + " 재사용 대기 · " + Math.max(1, cd[key] - now) + "ms · 충전 " + c[key] + "/" + max);
            } else if (cd[key] > now) p.sendMessage("스킬 " + slot + " 재사용 대기 · " + (cd[key] - now) + "ms");
        }
    }

    /** Armory calls this when a player enters combat mode. */
    static void syncFor(Player p) { if (current != null) current.syncLoadouts(p); }

    @EventHandler
    public void joined(org.bukkit.event.player.PlayerJoinEvent event) {
        Player p = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) syncLoadouts(p); }, 40);
    }

    ClassSkills(PipiLife plugin) {
        this.plugin = plugin;
        current = this;
        loadoutFile = new java.io.File(plugin.getDataFolder(), "loadouts.yml");
        loadoutData = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(loadoutFile);
        loadLoadouts();
    }

    /** Plugin disable: remove every effect entity still alive. Scheduler tasks are cancelled by Bukkit. */
    void shutdown() {
        for (Display display : new ArrayList<>(displays)) display.remove();
        displays.clear();
        charges.clear(); marks.clear(); markCasts.clear();
        cooldowns.clear(); poisons.clear(); if (current == this) current = null; will.clear(); fallImmune.clear(); lastGround.clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void shortcut(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking() || weaponType(player.getInventory().getItemInMainHand()) == null) return;
        event.setCancelled(true);
        cast(player, 1);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void skillDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        long now = System.currentTimeMillis();
        if (event.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL
            && fallImmune.getOrDefault(player.getUniqueId(), 0L) > now) { event.setCancelled(true); return; }
        if (will.getOrDefault(player.getUniqueId(), 0L) > now) event.setDamage(event.getDamage() * .8);
    }

    @EventHandler
    public void move(PlayerMoveEvent event) {
        if (event.getPlayer().isOnGround()) lastGround.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) { lastGround.remove(event.getPlayer().getUniqueId()); }

    private Job weaponType(ItemStack item) {
        if (item.getType().name().endsWith("_SWORD")) return Job.WARRIOR;
        if (item.getType() == Material.BOW) return Job.ARCHER;
        if (item.getType() == Material.BLAZE_ROD && item.hasItemMeta()
            && item.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(plugin, "smith_catalog"),
                org.bukkit.persistence.PersistentDataType.STRING)) return Job.MAGE;
        return null;
    }

    private boolean combatWorld(Player player) {
        // 거주지(마을)에서도 사용 가능, 튜토리얼 월드만 제외
        return !player.getWorld().getName().equals(VillageMaps.TUTORIAL)
            && (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE);
    }

    private boolean ready(Player player, World world) {
        return player.isOnline() && !player.isDead() && player.getWorld() == world && combatWorld(player);
    }

    // ---------------------------------------------------------------- casting

    boolean cast(Player player, int slot) {
        if (!combatWorld(player)) {
            player.sendMessage("튜토리얼 밖의 월드에서 서바이벌 또는 모험 모드로 사용하세요.");
            return true;
        }
        Job job = weaponType(player.getInventory().getItemInMainHand());
        if (job == null && slot != 6) {
            player.sendMessage("주 손에 검, 활 또는 대장간 지팡이를 드세요.");
            return true;
        }
        // cooldowns, charges and marks are keyed by skill (key), not by the key-slot the skill currently sits in
        final int key = slot >= 1 && slot <= 3 && job != null ? gen(job, loadout(player, job)[slot - 1]) : slot;
        long[] cd = cooldowns.computeIfAbsent(player.getUniqueId(), ignored -> new long[KEYS]);
        long now = System.currentTimeMillis();
        long remaining = cd[key] - now;
        int maxCharges = job == null ? 1 : maxCharges(job, key);
        int[] charge = null;
        if (maxCharges > 1) {
            charge = refillCharges(player, job, key, maxCharges, cd, now);
            if (charge[key] <= 0) {
                player.sendMessage("재사용까지 " + (remaining + 999) / 1000 + "초");
                return true;
            }
        } else if (remaining > 0) {
            player.sendMessage("재사용까지 " + (remaining + 999) / 1000 + "초");
            return true;
        }
        if (slot != 6) pipiAnim(player, job, slot);
        boolean fired = true;
        if (slot == 6) {
            fired = dash(player, now);
        } else {
            Runnable effect = () -> {
                switch (slot) {
                    case 0 -> heal(player, job);
                    case 4 -> ultimate(player, job);
                    case 5 -> basic(player, job);
                    case 7 -> shield(player, job);
                    default -> skill(player, job, key);
                }
            };
            int windup = windupTicks(job, key);
            if (windup <= 0) effect.run();
            else {
                World castWorld = player.getWorld();
                if (key == gen(Job.WARRIOR, 2)) chargeFx(player, windup);
                if (job == Job.MAGE && slot != 0) staffCharge(player, windup);
                Bukkit.getScheduler().runTaskLater(plugin, () -> { if (ready(player, castWorld)) effect.run(); }, windup);
            }
        }
        if (fired && slot == 6) startCooldown(player, cd, slot, slot, now, 8_000); // 대시 점프 8초
        if (fired && slot != 6) {
            int base = baseCooldown(job, key);
            if (charge != null) {
                // charges: spending one from a full stack starts the refill timer; later casts keep the running timer
                if (charge[key] >= maxCharges) cd[key] = now + CombatStats.cooldown(player, base);
                charge[key]--;
                player.sendMessage("스킬 " + slot + " 재사용 대기 · " + Math.max(1, cd[key] - now) + "ms · 충전 " + charge[key] + "/" + maxCharges);
            } else startCooldown(player, cd, key, slot, now, base);
        }
        return true;
    }

    private static int baseCooldown(Job job, int slot) {
        return switch (slot) {
            case 0 -> job == Job.MAGE ? 30_000 : 25_000;
            case 4 -> job == Job.MAGE ? 100_000 : 90_000;
            case 5 -> job == Job.MAGE ? 600 : 500;
            case 7 -> job == Job.WARRIOR ? 20_000 : job == Job.ARCHER ? 18_000 : 22_000;
            default -> (switch (job) {
                case WARRIOR -> new int[] {4_000, 5_000, 6_000, 7_000, 5_000};
                case ARCHER -> new int[] {3_000, 5_000, 7_000, 6_000, 8_000};
                case MAGE -> new int[] {3_000, 5_000, 8_000, 9_000, 7_000};
            })[Math.floorMod(slot - 8, 5)];
        };
    }

    // ---------------------------------------------------------------- charges + combo marks

    private final Map<UUID, int[]> charges = new HashMap<>();
    private final Map<String, Long> marks = new HashMap<>(); // targetId|playerId|slot -> expiry millis
    private final Map<String, Long> markCasts = new HashMap<>(); // same key -> id of the cast that placed the mark
    private long castSeq;

    /** 2-charge skills: 전사 돌진 베기, 궁수 관통 화살, 마법사 유도 마탄. */
    private static int maxCharges(Job job, int key) {
        return key == gen(Job.WARRIOR, 0) || key == gen(Job.ARCHER, 1) || key == gen(Job.MAGE, 0) ? 2 : 1;
    }

    /** Applies pending refills (one charge per cooldown) and returns the player's charge array (index = slot). */
    private int[] refillCharges(Player player, Job job, int slot, int max, long[] cd, long now) {
        int[] c = charges.computeIfAbsent(player.getUniqueId(), k -> { int[] a = new int[KEYS]; java.util.Arrays.fill(a, -1); return a; });
        if (c[slot] < 0) c[slot] = max;
        int len = CombatStats.cooldown(player, baseCooldown(job, slot));
        while (c[slot] < max && now >= cd[slot]) {
            c[slot]++;
            if (c[slot] < max) cd[slot] += len;
        }
        return c;
    }

    /** Hit by a charge skill: a marked target takes +50% and loses the mark, otherwise it gets a 3 s mark. */
    private void chargeHit(Player p, LivingEntity target, double damage, int slot, long cast) {
        String key = target.getUniqueId() + "|" + p.getUniqueId() + "|" + slot;
        long now = System.currentTimeMillis();
        Long until = marks.get(key);
        Long markCast = markCasts.get(key);
        World w = target.getWorld();
        if (until != null && until > now) {
            if (markCast != null && markCast == cast) { hit(p, target, damage); return; } // same cast: mark stays
            marks.remove(key);
            markCasts.remove(key);
            hit(p, target, damage * 1.5);
            w.spawnParticle(Particle.CRIT, target.getBoundingBox().getCenter().toLocation(w), 14, .3, .4, .3, .25);
            w.playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, .8f, 1.3f);
            return;
        }
        hit(p, target, damage);
        if (!target.isValid() || target.isDead()) return;
        long expiry = now + 3_000;
        marks.put(key, expiry);
        markCasts.put(key, cast);
        run(5, 12, i -> {
            Long cur = marks.get(key);
            if (cur == null || cur != expiry || !target.isValid() || target.isDead()) return true;
            Location head = target.getEyeLocation().add(0, .6, 0);
            w.spawnParticle(Particle.DUST, head, 2, .08, .05, .08, 0, new Particle.DustOptions(Color.fromRGB(255, 210, 60), .9f));
            return false;
        }, () -> { if (marks.remove(key, expiry)) markCasts.remove(key); });
    }

    /** Ticks between cast start (animation) and the actual effect; matches the client's release keyframe (CastAnim). */
    private static int windupTicks(Job job, int slot) {
        if (slot == 0) return 4;
        if (slot == 7) return 0;
        if (slot == 5) return job == Job.WARRIOR ? 5 : job == Job.MAGE ? 6 : 0;
        return switch (job) { case MAGE -> 7; case ARCHER -> 6; case WARRIOR -> 6; };
    }

    /** Warrior 대지 가르기 wind-up: sparks spiral into the blade while a slash arc grows there. */
    private void chargeFx(Player p, int ticks) {
        World w = p.getWorld();
        Vector dir0 = p.getEyeLocation().getDirection().setY(0);
        if (dir0.lengthSquared() < .01) return;
        dir0.normalize();
        Location blade0 = p.getLocation().add(0, 1.3, 0).add(dir0.clone().multiply(1.1));
        ItemDisplay glow = fx("slash_arc", blade0, facing(dir0, 1.45f), vec(.3f), null, ticks + 3);
        tween(glow, 1, ticks - 1, new Vector3f(), facing(dir0, 1.45f), vec(1.5f));
        run(1, ticks, i -> {
            if (!ready(p, w)) return true;
            Vector d = p.getEyeLocation().getDirection().setY(0);
            if (d.lengthSquared() < .01) d = dir0; else d.normalize();
            Location blade = p.getLocation().add(0, 1.3, 0).add(d.clone().multiply(1.1));
            moveFx(glow, blade);
            double r = 1.6 * (1 - i / (double) ticks) + .2;
            for (int k = 0; k < 6; k++) {
                double a = i * .9 + k * Math.PI / 3;
                Vector off = new Vector(-d.getZ(), 0, d.getX()).multiply(Math.cos(a) * r).add(new Vector(0, Math.sin(a) * r, 0));
                w.spawnParticle(Particle.END_ROD, blade.clone().add(off), 1, 0, 0, 0, 0);
                if (k % 2 == 0) w.spawnParticle(Particle.DUST, blade.clone().add(off), 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(255, 80, 60), 1.2f));
            }
            return false;
        }, () -> kill(glow));
    }

    /** Mage wind-up: purple motes spiral into the staff tip (held straight out front) with a growing glow and rising pitch. */
    private void staffCharge(Player p, int ticks) {
        World w = p.getWorld();
        run(1, ticks, i -> {
            if (!ready(p, w)) return true;
            Location eye = p.getEyeLocation();
            Vector d = eye.getDirection().normalize();
            Vector side = new Vector(-d.getZ(), 0, d.getX());
            if (side.lengthSquared() < 1e-6) side = new Vector(1, 0, 0); else side.normalize();
            Vector up = side.clone().crossProduct(d).normalize();
            Location tip = eye.clone().add(d.clone().multiply(1.6)).add(side.clone().multiply(-.25)).add(0, -.35, 0);
            double frac = i / (double) ticks;
            double r = .9 * (1 - frac) + .1;
            for (int k = 0; k < 4; k++) {
                double a = i * .9 + k * Math.PI / 2;
                Vector off = side.clone().multiply(Math.cos(a) * r).add(up.clone().multiply(Math.sin(a) * r));
                w.spawnParticle(Particle.DUST, tip.clone().add(off), 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(176, 110, 255), 1.1f));
                if (k % 2 == 0) w.spawnParticle(Particle.ENCHANT, tip.clone().add(off), 1, 0, 0, 0, 0);
            }
            w.spawnParticle(Particle.DUST, tip, 1, .05, .05, .05, 0, new Particle.DustOptions(Color.fromRGB(230, 200, 255), (float) (.7 + frac * 1.6)));
            if (i % 2 == 0) w.playSound(tip, Sound.BLOCK_AMETHYST_BLOCK_CHIME, .5f, (float) (.8 + frac * 1.1));
            return false;
        }, null);
    }

    /** Broadcast a hidden PIPI_ANIM message so clients play the cast motion (staff/sword/bow). */
    private void pipiAnim(Player p, Job job, int slot) {
        String type = slot == 0 || slot == 7 ? "raise" : switch (job) {
            case MAGE -> slot == 5 ? "staff_quick" : "staff_thrust";
            case ARCHER -> slot == 5 ? "bow_quick" : "bow_draw";
            case WARRIOR -> slot == 5 ? "sword_swing" : "sword_big";
        };
        String msg = "PIPI_ANIM:" + p.getEntityId() + ":" + type;
        for (Player other : p.getWorld().getPlayers()) {
            if (other == p || other.getLocation().distanceSquared(p.getLocation()) <= 48 * 48) other.sendMessage(msg);
        }
    }

    private void startCooldown(Player player, long[] cd, int key, int slot, long now, int millis) {
        millis = CombatStats.cooldown(player, millis);
        cd[key] = now + millis;
        String label = switch (slot) {
            case 0 -> "회복";
            case 4 -> "궁극기";
            case 5 -> "평타";
            case 6 -> "대시";
            case 7 -> "보호막";
            default -> "스킬 " + slot;
        };
        player.sendMessage(label + " 재사용 대기 · " + millis + "ms");
    }

    private void basic(Player p, Job job) {
        switch (job) {
            case WARRIOR -> warriorBasic(p);
            case ARCHER -> arrowShot(p, 1.0);
            case MAGE -> mageOrb(p, 1.0);
        }
    }

    private void skill(Player p, Job job, int key) {
        switch (job) {
            case WARRIOR -> { switch (Math.floorMod(key - 8, 5)) { case 0 -> rush(p); case 1 -> whirl(p); case 2 -> split(p); case 3 -> leap(p); default -> flurry(p); } }
            case ARCHER -> { switch (Math.floorMod(key - 8, 5)) { case 0 -> triple(p); case 1 -> pierce(p); case 2 -> rain(p); case 3 -> venom(p); default -> retreat(p); } }
            case MAGE -> { switch (Math.floorMod(key - 8, 5)) { case 0 -> seek(p); case 1 -> frost(p); case 2 -> thunder(p); case 3 -> vortex(p); default -> prison(p); } }
        }
    }

    private void heal(Player p, Job job) {
        switch (job) {
            case WARRIOR -> resolve(p);
            case ARCHER -> breath(p);
            case MAGE -> light(p);
        }
    }

    /** 보호막: 강철 의지 (전사 20%), 바람 장막 (궁수 15% + 이동속도), 마력 방벽 (마법사 25%); absorbs damage after defense. */
    private void shield(Player p, Job job) {
        double max = p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        double amount = max * switch (job) { case WARRIOR -> .20; case ARCHER -> .15; case MAGE -> .25; };
        long ms = CombatStats.duration(p, 6_000);
        CombatStats.giveShield(p, amount, ms);
        if (job == Job.ARCHER) p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, (int) (CombatStats.duration(p, 3_000) / 50), 1));
        World w = p.getWorld();
        w.playSound(p.getLocation(), job == Job.MAGE ? Sound.BLOCK_AMETHYST_BLOCK_RESONATE : Sound.ITEM_ARMOR_EQUIP_IRON, 1f, job == Job.ARCHER ? 1.6f : 1f);
        Color col = switch (job) { case WARRIOR -> Color.fromRGB(190, 200, 215); case ARCHER -> Color.fromRGB(90, 230, 120); case MAGE -> Color.fromRGB(176, 100, 255); };
        int life = (int) Math.min(130, ms / 50 + 4);
        int count = job == Job.WARRIOR ? 4 : job == Job.MAGE ? 3 : 1;
        ItemDisplay[] parts = new ItemDisplay[count];
        for (int g = 0; g < count; g++) {
            Location at = p.getLocation().add(0, 1, 0);
            parts[g] = switch (job) {
                case WARRIOR -> fx("crystal", at, new Quaternionf(), vec(.4f), null, life);
                case ARCHER -> fx("whirl_ring", at, new Quaternionf(), vec(2.4f), null, life);
                case MAGE -> fx("orb", at, new Quaternionf(), vec(.45f), new ItemStack(Material.AMETHYST_SHARD), life);
            };
        }
        run(2, (int) Math.max(1, ms / 100) + 1, i -> {
            if (!p.isOnline() || p.isDead() || p.getWorld() != w) { CombatStats.clearShield(p); return true; }
            double[] state = CombatStats.shieldState(p);
            if (state == null) return true; // expired
            Location c = p.getLocation().add(0, 1, 0);
            if (state[3] != 0) { // depleted: break effect
                w.spawnParticle(Particle.DUST, c, 30, .6, .8, .6, 0, new Particle.DustOptions(col, 1.6f));
                w.spawnParticle(Particle.CRIT, c, 12, .6, .8, .6, .2);
                w.playSound(c, Sound.ITEM_SHIELD_BREAK, 1f, job == Job.MAGE ? 1.4f : 1f);
                CombatStats.clearShield(p);
                return true;
            }
            for (int g = 0; g < count; g++) {
                ItemDisplay d = parts[g];
                if (d == null || !d.isValid()) continue;
                d.setTeleportDuration(2);
                if (job == Job.ARCHER) {
                    moveFx(d, c);
                    tween(d, 1, 2, new Vector3f(), new Quaternionf().rotateY(i * .8f), vec(2.4f));
                } else {
                    double a = i * .35 + g * Math.PI * 2 / count;
                    moveFx(d, c.clone().add(Math.cos(a) * 1.15, Math.sin(i * .3 + g) * .35, Math.sin(a) * 1.15));
                    tween(d, 1, 2, new Vector3f(), new Quaternionf().rotateY((float) (i * .6 + g)).rotateZ(.15f), vec(job == Job.WARRIOR ? .4f : .45f));
                }
            }
            for (int k = 0; k < 6; k++) { // faint bubble: random points on a 1.15 radius shell (hex lattice look for the mage)
                double th = Math.random() * Math.PI * 2, ph = Math.acos(2 * Math.random() - 1);
                if (job == Job.ARCHER) { th = i * .7 + k * Math.PI / 3; ph = Math.PI / 2; }
                Location at = c.clone().add(Math.sin(ph) * Math.cos(th) * 1.15, Math.cos(ph) * 1.15, Math.sin(ph) * Math.sin(th) * 1.15);
                w.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, new Particle.DustOptions(col, .9f));
            }
            return false;
        }, () -> { for (ItemDisplay d : parts) kill(d); });
    }

    private void ultimate(Player p, Job job) {
        switch (job) {
            case WARRIOR -> swordFall(p);
            case ARCHER -> stormArrow(p);
            case MAGE -> meteor(p);
        }
    }

    // ---------------------------------------------------------------- warrior

    private void warriorBasic(Player p) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        World w = p.getWorld();
        w.spawnParticle(Particle.SWEEP_ATTACK, eye.clone().add(dir.clone().multiply(2.2)).add(0, -.3, 0), 1, 0, 0, 0, 0);
        Vector side = new Vector(-dir.getZ(), 0, dir.getX());
        if (side.lengthSquared() > 0) side.normalize();
        for (double a = -1.1; a <= 1.1; a += .22) { // white arc
            Vector point = dir.clone().multiply(Math.cos(a) * 2.9).add(side.clone().multiply(Math.sin(a) * 2.9));
            w.spawnParticle(Particle.DUST, eye.clone().add(point).add(0, -.4, 0), 1, .03, .03, .03, 0,
                new Particle.DustOptions(Color.WHITE, .7f));
        }
        flip = !flip;
        slashQuick("slash_arc", eye.clone().add(dir.clone().multiply(2.3)).add(0, -.4, 0), dir, 1.8f, flip ? .45f : -.45f, .8f);
        double dmg = power(p, Job.WARRIOR);
        for (LivingEntity target : near(p, eye, 3.84)) { // reach 3.2 x 1.2
            Vector rel = target.getBoundingBox().getCenter().subtract(eye.toVector());
            if (rel.lengthSquared() > 0 && dir.dot(rel.clone().normalize()) >= .35) hit(p, target, dmg);
        }
    }

    private void rush(Player p) {
        final long cast = ++castSeq;
        Location origin = p.getLocation();
        Vector dir = p.getEyeLocation().getDirection().setY(0);
        if (dir.lengthSquared() < .01) dir = origin.getDirection().setY(0);
        if (dir.lengthSquared() < .01) return;
        dir.normalize();
        World w = origin.getWorld();
        double dmg = power(p, Job.WARRIOR) * 1.8;
        Set<LivingEntity> done = new HashSet<>();
        Vector heading = dir;
        fallImmune.put(p.getUniqueId(), System.currentTimeMillis() + 2_000);
        w.playSound(origin, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, .7f);
        slash("slash_red", origin.clone().add(0, 1, 0).add(heading.clone().multiply(1.6)), heading, 2.7f, -.5f, .8f);
        // 6 ticks x ~1.35 blocks = ~8 blocks forward; velocity is re-applied each tick so ground friction cannot eat it
        run(1, 6, i -> {
            if (!ready(p, w)) return true;
            Location now = p.getLocation();
            Location foot = now.clone().add(heading.clone().multiply(.8));
            boolean feetBlocked = !foot.getBlock().isPassable();
            boolean headBlocked = !foot.clone().add(0, 1, 0).getBlock().isPassable();
            if (feetBlocked && headBlocked) return true;
            double vy = p.isOnGround() ? .1 : Math.max(p.getVelocity().getY(), -.08);
            if (i == 0) vy = .25;
            if (feetBlocked && foot.clone().add(0, 2, 0).getBlock().isPassable()) vy = .5; // step over a 1-block bump
            p.setVelocity(heading.clone().multiply(1.35).setY(vy));
            for (LivingEntity t : along(p, now.clone().add(0, 1, 0).subtract(heading.clone().multiply(.6)), heading, 3.0, 1.3)) {
                if (done.add(t)) chargeHit(p, t, dmg, gen(Job.WARRIOR, 0), cast);
            }
            Location c = now.clone().add(0, 1, 0);
            w.spawnParticle(Particle.DUST, c, 6, .3, .4, .3, 0, new Particle.DustOptions(Color.fromRGB(220, 30, 30), 1.4f));
            w.spawnParticle(Particle.CRIT, c, 3, .3, .4, .3, .05);
            w.spawnParticle(Particle.CLOUD, now.clone().add(0, .2, 0), 2, .2, .1, .2, .02);
            if (i % 2 == 0) w.spawnParticle(Particle.SWEEP_ATTACK, c.clone().add(heading.clone().multiply(1.2)), 1, 0, 0, 0, 0);
            return false;
        }, () -> { if (p.isOnline()) p.setVelocity(p.getVelocity().multiply(.3)); });
    }

    private void whirl(Player p) {
        World w = p.getWorld();
        double dmg = power(p, Job.WARRIOR) * 1.6;
        for (LivingEntity target : near(p, p.getLocation().add(0, 1, 0), 5.25)) hit(p, target, dmg);
        ItemDisplay ring = fx("whirl_ring", p.getLocation().add(0, 1, 0), new Quaternionf(), vec(1.8f), null, 24);
        float[] sizes = {3.6f, 4.5f, 4.65f};
        for (int s = 1; s <= 3; s++)
            tween(ring, s * 3 - 2, 3, new Vector3f(), new Quaternionf().rotateY((float) Math.toRadians(120 * s)), vec(sizes[s - 1]));
        tween(ring, 10, 3, new Vector3f(), new Quaternionf().rotateY((float) Math.toRadians(420)), vec(.45f));
        run(1, 8, i -> {
            Location c = p.getLocation().add(0, 1, 0);
            moveFx(ring, c);
            for (int k = 0; k < 12; k++) {
                double a = i * .8 + k * Math.PI / 6;
                Location at = c.clone().add(Math.cos(a) * 4.5, Math.sin(i) * .3 - .2, Math.sin(a) * 4.5);
                w.spawnParticle(k % 3 == 0 ? Particle.SWEEP_ATTACK : Particle.CRIT, at, 1, 0, 0, 0, 0);
            }
            return !p.isOnline();
        }, null);
    }

    private void split(Player p) {
        World w = p.getWorld();
        Location base = p.getLocation();
        Vector dir = p.getEyeLocation().getDirection().setY(0);
        if (dir.lengthSquared() < .01) dir = base.getDirection().setY(0);
        dir.normalize();
        Vector heading = dir;
        Set<LivingEntity> done = new HashSet<>();
        double dmg = power(p, Job.WARRIOR) * 2.2;
        // sword-aura wave: same reach as the mage basic orb (1.2 blocks/tick × 12 = 14.4 blocks), chest height, big upright crescents
        Location start = base.clone().add(0, 1.2, 0);
        ItemDisplay arc = fx("slash_arc", start.clone().add(heading.clone().multiply(1.2)), facing(heading, 1.45f), vec(2.5f), null, 18);
        ItemDisplay red = fx("slash_red", start.clone().add(heading.clone().multiply(1.0)), facing(heading, 1.45f), vec(2.5f), null, 18);
        w.playSound(base, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.5f, .6f);
        run(1, 12, i -> {
            Location c = start.clone().add(heading.clone().multiply((i + 1) * 14.4 / 12 + .2));
            if (!c.getBlock().isPassable()) return true;
            moveFx(arc, c);
            moveFx(red, c.clone().subtract(heading.clone().multiply(.5)));
            w.spawnParticle(Particle.SWEEP_ATTACK, c, 1, .6, .6, .6, 0);
            w.spawnParticle(Particle.CRIT, c, 10, .8, .8, .8, .1);
            w.spawnParticle(Particle.DUST, c, 8, .8, .8, .8, 0, new Particle.DustOptions(Color.fromRGB(255, 80, 60), 1.5f));
            for (LivingEntity target : near(p, c, 2.2)) {
                if (!done.add(target)) continue;
                hit(p, target, dmg);
            }
            return !p.isOnline();
        }, () -> { kill(arc); kill(red); });
    }

    /** 도약 강타: leap ~6 blocks forward and up, slam on landing (radius 3.5, 200%). */
    private void leap(Player p) {
        World w = p.getWorld();
        Vector dir = p.getEyeLocation().getDirection().setY(0);
        if (dir.lengthSquared() < .01) dir = p.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < .01) dir = new Vector(0, 0, 1);
        dir.normalize();
        Vector heading = dir;
        fallImmune.put(p.getUniqueId(), System.currentTimeMillis() + 4_000);
        p.setVelocity(heading.clone().multiply(.78).setY(.8));
        w.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, .7f);
        w.spawnParticle(Particle.CLOUD, p.getLocation().add(0, .2, 0), 14, .4, .1, .4, .05);
        double dmg = power(p, Job.WARRIOR) * 2.0;
        boolean[] slammed = {false};
        Runnable slam = () -> {
            if (slammed[0]) return;
            slammed[0] = true;
            if (!ready(p, w)) return;
            Location c = p.getLocation();
            w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.2f);
            w.playSound(c, Sound.ITEM_MACE_SMASH_GROUND, 1f, .9f);
            w.spawnParticle(Particle.EXPLOSION, c, 3, 1, .1, 1, 0);
            w.spawnParticle(Particle.CRIT, c.clone().add(0, .5, 0), 30, 1.6, .3, 1.6, .3);
            w.spawnParticle(Particle.BLOCK, c.clone().add(0, .2, 0), 40, 1.5, .2, 1.5, 0, c.clone().subtract(0, .5, 0).getBlock().getBlockData());
            for (int k = 0; k < 4; k++) spikes(c.clone().add(0, .05, 0), new Vector(Math.cos(k * Math.PI / 4), 0, Math.sin(k * Math.PI / 4)), 2.2f);
            run(1, 4, i -> {
                double r = 1 + i;
                ring(c.clone().add(0, .2, 0), r, (int) (r * 8), at -> w.spawnParticle(Particle.DUST, at, 1, 0, .1, 0, 0, new Particle.DustOptions(Color.fromRGB(255, 120, 60), 1.4f)));
                return false;
            }, null);
            for (LivingEntity t : near(p, c, 3.5)) hit(p, t, dmg);
        };
        run(1, 30, i -> {
            if (!ready(p, w)) return true;
            Location c = p.getLocation().add(0, 1, 0);
            w.spawnParticle(Particle.DUST, c, 4, .3, .4, .3, 0, new Particle.DustOptions(Color.fromRGB(255, 110, 50), 1.3f));
            w.spawnParticle(Particle.CLOUD, c.clone().subtract(0, .8, 0), 1, .15, .05, .15, .01);
            if (i >= 4 && p.isOnGround()) { slam.run(); return true; }
            return false;
        }, slam);
    }

    /** 연속 베기: three quick forward slashes (cone 4 blocks, 90% each). */
    private void flurry(Player p) {
        World w = p.getWorld();
        double dmg = power(p, Job.WARRIOR) * .9;
        run(4, 3, i -> {
            if (!ready(p, w)) return true;
            Location eye = p.getEyeLocation();
            Vector dir = eye.getDirection().normalize();
            Location at = eye.clone().add(dir.clone().multiply(2.3)).add(0, -.4, 0);
            slashQuick(i == 1 ? "slash_red" : "slash_arc", at, dir, 2.0f, i % 2 == 0 ? .7f : -.7f, .9f);
            w.spawnParticle(Particle.SWEEP_ATTACK, at, 1, 0, 0, 0, 0);
            w.spawnParticle(Particle.CRIT, at, 6, .6, .4, .6, .1);
            w.playSound(eye, Sound.ENTITY_PLAYER_ATTACK_SWEEP, .9f, 1f + i * .15f);
            for (LivingEntity target : near(p, eye, 4)) {
                Vector rel = target.getBoundingBox().getCenter().subtract(eye.toVector());
                if (rel.lengthSquared() > 0 && dir.dot(rel.clone().normalize()) >= .35) hit(p, target, dmg);
            }
            return false;
        }, null);
    }

    private void resolve(Player p) { // 투지
        healBy(p, .25);
        long ms = CombatStats.duration(p, 4_000);
        will.put(p.getUniqueId(), System.currentTimeMillis() + ms);
        World w = p.getWorld();
        int life = (int) Math.min(120, ms / 50 + 8);
        ItemDisplay[] gems = new ItemDisplay[3];
        for (int g = 0; g < 3; g++) gems[g] = fx("crystal", p.getLocation(), new Quaternionf(), vec(.3f), null, life);
        run(4, (int) Math.max(1, ms / 200), i -> {
            Location c = p.getLocation();
            for (int g = 0; g < 3; g++) {
                double ga = i * .7 + g * Math.PI * 2 / 3;
                if (gems[g] != null && gems[g].isValid()) {
                    gems[g].setTeleportDuration(4);
                    moveFx(gems[g], c.clone().add(Math.cos(ga) * 1.2, 1.1 + Math.sin(i * .8 + g) * .25, Math.sin(ga) * 1.2));
                    tween(gems[g], 1, 4, new Vector3f(), new Quaternionf().rotateY((float) (i * .9 + g)).rotateZ(.12f), vec(.34f));
                }
            }
            for (int k = 0; k < 8; k++) {
                double a = i * .6 + k * Math.PI / 4;
                Location at = c.clone().add(Math.cos(a) * .9, .2 + (k % 4) * .5, Math.sin(a) * .9);
                w.spawnParticle(Particle.FLAME, at, 1, 0, 0, 0, 0);
                w.spawnParticle(Particle.DUST, at, 1, .05, .05, .05, 0, new Particle.DustOptions(Color.fromRGB(255, 140, 30), 1.2f));
            }
            return !p.isOnline() || p.isDead();
        }, () -> { for (ItemDisplay g : gems) kill(g); });
    }

    /** 검기 난사: 5 s of rapid sword-aura slashes around the player (radius 5.25), 20 pulses x 40% = 800% total. */
    private void swordFall(Player p) {
        World w = p.getWorld();
        double dmg = power(p, Job.WARRIOR) * .4;
        w.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, .8f);
        run(5, 20, i -> {
            if (!p.isOnline() || p.isDead()) return true;
            Location c = p.getLocation().add(0, 1, 0);
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            for (int k = 0; k < 3; k++) {
                double a = rnd.nextDouble() * Math.PI * 2, r = 1.5 + rnd.nextDouble() * 3.5;
                Vector d = new Vector(Math.cos(a), 0, Math.sin(a));
                Location at = c.clone().add(d.clone().multiply(r)).add(0, rnd.nextDouble() - .5, 0);
                slashQuick(k == 0 ? "slash_red" : "slash_arc", at, new Vector(-d.getZ(), 0, d.getX()), 2.2f + rnd.nextFloat(), (rnd.nextFloat() - .5f) * 1.6f, .8f);
                w.spawnParticle(Particle.SWEEP_ATTACK, at, 1, 0, 0, 0, 0);
                w.spawnParticle(Particle.CRIT, at, 4, .4, .3, .4, .05);
            }
            w.playSound(c, Sound.ENTITY_PLAYER_ATTACK_SWEEP, .6f, .9f + rnd.nextFloat() * .4f);
            for (LivingEntity t : near(p, c, 5.25)) hit(p, t, dmg);
            return false;
        }, null);
    }

    // ---------------------------------------------------------------- archer

    private void arrowShot(Player p, double coef) {
        Location eye = p.getEyeLocation();
        Vector adir = aimDir(p, eye, 20);
        List<LivingEntity> hits = beam(p, eye, adir, 20, .5, 1, Particle.CRIT, Color.WHITE);
        bigArrow(eye.clone().add(adir.clone().multiply(.9)).add(0, -.2, 0), adir, .3f, beamLen, 3);
        double dmg = power(p, Job.ARCHER) * coef;
        for (LivingEntity t : hits) hit(p, t, dmg);
    }

    private void triple(Player p) {
        World w = p.getWorld();
        run(6, 3, i -> {
            if (!ready(p, w)) return true;
            arrowShot(p, .9);
            return false;
        }, null);
    }

    private void pierce(Player p) {
        final long cast = ++castSeq;
        Location eye = p.getEyeLocation();
        Vector pdir = aimDir(p, eye, 20);
        List<LivingEntity> hits = beam(p, eye, pdir, 20, .8, 0, Particle.HAPPY_VILLAGER, Color.fromRGB(60, 220, 80));
        Location pstart = eye.clone().add(pdir.clone().multiply(.9)).add(0, -.2, 0);
        bigArrow(pstart, pdir, .55f, beamLen, 4);
        ItemDisplay wind = fx("wind_spiral", pstart, facing(pdir, 0), vec(.8f), null, 16);
        tween(wind, 1, 4, new Vector3f(0, 0, 0), facing(pdir, 2.4f), vec(.95f));
        if (wind != null) flyFx(wind, eye.clone().add(pdir.clone().multiply(beamLen * .85)), 4);
        double dmg = power(p, Job.ARCHER) * 2.0;
        for (LivingEntity t : hits) {
            chargeHit(p, t, dmg, gen(Job.ARCHER, 1), cast);
            t.getWorld().spawnParticle(Particle.CRIT, t.getBoundingBox().getCenter().toLocation(t.getWorld()), 10, .3, .3, .3, .2);
        }
    }

    private void rain(Player p) {
        Location c = aimPoint(p, 22);
        World w = c.getWorld();
        double dmg = power(p, Job.ARCHER) * .5;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        run(10, 6, i -> {
            if (!p.isOnline()) return true;
            w.spawnParticle(Particle.CLOUD, c.clone().add(0, 9, 0), 14, 2, .3, 2, .01);
            for (int k = 0; k < 12; k++) {
                double a = rnd.nextDouble() * Math.PI * 2, r = Math.sqrt(rnd.nextDouble()) * 4;
                Location from = c.clone().add(Math.cos(a) * r, 9, Math.sin(a) * r);
                if (k < 4) bigArrow(from, new Vector(0, -1, 0), .32f, 8.7, 4);
                for (double y = 0; y <= 9; y += .9) w.spawnParticle(Particle.CRIT, from.clone().add(0, -y, 0), 1, 0, 0, 0, 0);
            }
            for (LivingEntity t : near(p, c, 4)) hit(p, t, dmg);
            return false;
        }, null);
    }

    /** 맹독 화살: auto-aimed arrow (60%) that poisons the target: 30% every second for 5 s. */
    private void venom(Player p) {
        Location eye = p.getEyeLocation();
        Vector adir = aimDir(p, eye, 20);
        List<LivingEntity> hits = beam(p, eye, adir, 20, .5, 1, Particle.HAPPY_VILLAGER, Color.fromRGB(80, 220, 60));
        bigArrow(eye.clone().add(adir.clone().multiply(.9)).add(0, -.2, 0), adir, .32f, beamLen, 3);
        double dmg = power(p, Job.ARCHER);
        for (LivingEntity t : hits) {
            hit(p, t, dmg * .6);
            if (t.isValid() && !t.isDead()) poison(p, t, dmg * .3);
        }
    }

    private void poison(Player p, LivingEntity t, double tick) {
        String key = t.getUniqueId() + "|" + p.getUniqueId();
        long expiry = System.currentTimeMillis() + 5_500;
        poisons.put(key, expiry); // a newer cast replaces the running one
        World w = t.getWorld();
        w.playSound(t.getLocation(), Sound.ENTITY_SPIDER_HURT, .7f, 1.5f);
        run(10, 11, i -> {
            Long cur = poisons.get(key);
            if (cur == null || cur != expiry || !p.isOnline() || !t.isValid() || t.isDead() || t.getWorld() != w) return true;
            Location c = t.getBoundingBox().getCenter().toLocation(w);
            w.spawnParticle(Particle.DUST, c, 5, .3, .4, .3, 0, new Particle.DustOptions(Color.fromRGB(80, 220, 60), 1.2f));
            w.spawnParticle(Particle.HAPPY_VILLAGER, c, 3, .3, .4, .3, 0);
            if (i > 0 && i % 2 == 0) {
                w.spawnParticle(Particle.DUST, c, 12, .35, .5, .35, 0, new Particle.DustOptions(Color.fromRGB(40, 160, 40), 1.6f));
                hit(p, t, tick);
            }
            return false;
        }, () -> poisons.remove(key, expiry));
    }

    /** 후퇴 사격: hop ~5 blocks backwards, then a 3-arrow fan (80% each). */
    private void retreat(Player p) {
        World w = p.getWorld();
        Vector back = p.getEyeLocation().getDirection().setY(0);
        if (back.lengthSquared() < .01) back = p.getLocation().getDirection().setY(0);
        if (back.lengthSquared() < .01) back = new Vector(0, 0, 1);
        back.normalize().multiply(-1);
        Location origin = p.getLocation();
        if (!w.getWorldBorder().isInside(origin.clone().add(back.clone().multiply(3)))) back.multiply(0);
        fallImmune.put(p.getUniqueId(), System.currentTimeMillis() + 3_000);
        p.setVelocity(back.multiply(.85).setY(.42));
        w.spawnParticle(Particle.CLOUD, origin.clone().add(0, .3, 0), 16, .35, .1, .35, .05);
        w.playSound(origin, Sound.ENTITY_BAT_TAKEOFF, .8f, 1.5f);
        run(1, 5, i -> {
            w.spawnParticle(Particle.CLOUD, p.getLocation().add(0, .4, 0), 4, .2, .2, .2, .02);
            return !p.isOnline();
        }, null);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!ready(p, w)) return;
            Location eye = p.getEyeLocation();
            Vector center = aimDir(p, eye, 20);
            double dmg = power(p, Job.ARCHER) * .8;
            for (int k = -1; k <= 1; k++) {
                Vector d = center.clone().rotateAroundY(Math.toRadians(14 * k));
                List<LivingEntity> hits = beam(p, eye, d, 20, .5, 1, Particle.CRIT, Color.WHITE);
                bigArrow(eye.clone().add(d.clone().multiply(.9)).add(0, -.2, 0), d, .3f, beamLen, 3);
                for (LivingEntity t : hits) hit(p, t, dmg);
            }
            w.playSound(eye, Sound.ENTITY_ARROW_SHOOT, 1f, 1.2f);
        }, 5);
    }

    private void breath(Player p) { // 숲의 숨결
        healBy(p, .20);
        int ticks = (int) (CombatStats.duration(p, 5_000) / 50);
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, ticks, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ticks, 1));
        World w = p.getWorld();
        run(2, 12, i -> {
            Location c = p.getLocation();
            for (int k = 0; k < 6; k++) {
                double a = i * .5 + k * Math.PI / 3;
                Location at = c.clone().add(Math.cos(a) * 1.1, .3 + i * .12, Math.sin(a) * 1.1);
                if (k < 2) {
                    ItemDisplay leaf = fx("leaf", at, new Quaternionf().rotateY((float) (a * 2)).rotateX(.5f), vec(.2f), null, 12);
                    tween(leaf, 1, 6, new Vector3f(0, .9f, 0), new Quaternionf().rotateY((float) (a * 2 + 2.4)).rotateX(.9f).rotateZ(.6f), vec(.36f));
                    tween(leaf, 7, 4, new Vector3f(0, 1.3f, 0), new Quaternionf().rotateY((float) (a * 2 + 3.4)).rotateX(1.1f), vec(.05f));
                }
                w.spawnParticle(Particle.CHERRY_LEAVES, at, 1, .1, .1, .1, 0);
                w.spawnParticle(Particle.DUST, at, 1, .05, .05, .05, 0, new Particle.DustOptions(Color.fromRGB(70, 200, 90), 1.1f));
            }
            return !p.isOnline();
        }, null);
    }

    private void stormArrow(Player p) {
        Location eye = p.getEyeLocation();
        Vector dir = aimDir(p, eye, 25);
        World w = p.getWorld();
        double len = blockLen(p, eye, dir, 25);
        double dmg = power(p, Job.ARCHER) * 7;
        Vector right = dir.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < .01) right = new Vector(1, 0, 0);
        right.normalize();
        Vector up = right.clone().crossProduct(dir).normalize();
        for (LivingEntity t : along(p, eye, dir, len, 4.5)) {
            Vector rel = t.getBoundingBox().getCenter().subtract(eye.toVector());
            double along = rel.dot(dir);
            Vector toLine = dir.clone().multiply(along).subtract(rel); // from entity to the line
            if (toLine.length() <= 2.4) hit(p, t, dmg);
        }
        ItemDisplay arrow = fx("arrow_big", eye.clone().add(dir), facing(dir, 0), vec(1.5f), new ItemStack(Material.ARROW), 40);
        ItemDisplay windFx = fx("wind_spiral", eye.clone().add(dir.clone().multiply(2)), facing(dir, 0), vec(2.3f), null, 40);
        Vector r = right, u = up;
        int steps = 10;
        run(1, steps, i -> {
            double from = len * i / steps, to = len * (i + 1) / steps;
            for (double s = from; s < to; s += .5) {
                double a = s * 1.3;
                Location axis = eye.clone().add(dir.clone().multiply(s));
                Location spiral = axis.clone().add(r.clone().multiply(Math.cos(a) * 1.3)).add(u.clone().multiply(Math.sin(a) * 1.3));
                w.spawnParticle(Particle.CLOUD, spiral, 1, 0, 0, 0, 0);
                w.spawnParticle(Particle.SWEEP_ATTACK, axis, 1, 0, 0, 0, 0);
            }
            if (arrow != null) { arrow.setTeleportDuration(1); moveFx(arrow, eye.clone().add(dir.clone().multiply(to))); }
            if (windFx != null) {
                moveFx(windFx, eye.clone().add(dir.clone().multiply(to + 1.2)));
                tween(windFx, 1, 1, new Vector3f(), facing(dir, (float) (i * 1.1 + 1)), vec(2.3f));
            }
            return false;
        }, () -> { kill(arrow); kill(windFx); });
    }

    // ---------------------------------------------------------------- mage

    private void mageOrb(Player p, double coef) {
        Location eye = p.getEyeLocation();
        Vector odir = aimDir(p, eye, 20);
        orb(p, eye.clone().add(odir.clone().multiply(.8)).add(0, -.2, 0), odir, 1.5, 14, power(p, Job.MAGE) * coef, null, null, .7f);
    }

    private void seek(Player p) {
        Location eye = p.getEyeLocation();
        Vector dir = aimDir(p, eye, 20);
        List<LivingEntity> targets = near(p, p.getLocation(), 20);
        targets.sort(Comparator.comparingDouble(t -> t.getLocation().distanceSquared(p.getLocation())));
        double dmg = power(p, Job.MAGE) * 1.2;
        World w = p.getWorld();
        long[] mark = {gen(Job.MAGE, 0), ++castSeq};
        for (int n = 0; n < 2; n++) {
            LivingEntity target = targets.isEmpty() ? null : targets.get(Math.min(n, targets.size() - 1));
            double angle = (n == 0 ? 1 : -1) * Math.toRadians(targets.isEmpty() ? 8 : 35);
            Vector start = dir.clone().rotateAroundY(angle);
            long delay = target != null && targets.size() == 1 ? n * 4L : 0;
            Runnable launch = () -> {
                if (!ready(p, w)) return;
                orb(p, p.getEyeLocation().add(start.clone().multiply(.8)), start, .9, 24, dmg, target, mark, .6f);
            };
            if (delay == 0) launch.run(); else Bukkit.getScheduler().runTaskLater(plugin, launch, delay);
        }
    }

    private void frost(Player p) {
        Location c = aimPoint(p, 20);
        World w = c.getWorld();
        Vector h = p.getEyeLocation().getDirection().setY(0);
        if (h.lengthSquared() < 1e-6) h = new Vector(0, 0, 1); else h.normalize();
        // comes in from the upper right (as seen by the caster): ~7 up, ~4 to the right, ~1.5 beyond the target, tip first
        Vector right = new Vector(-h.getZ(), 0, h.getX());
        Location from = c.clone().add(right.multiply(4)).add(h.clone().multiply(1.5)).add(0, 7, 0);
        Vector travel = c.toVector().subtract(from.toVector());
        Vector dir = travel.clone().normalize();
        double dmg = power(p, Job.MAGE) * 1.8;
        ItemDisplay lance = fx("ice_lance", from, facing(dir, 0), vec(1.4f), new ItemStack(Material.PACKED_ICE), 30);
        fall(lance, from, c, 8, at -> {
            w.spawnParticle(Particle.SNOWFLAKE, at, 3, .15, .15, .15, .01);
            w.spawnParticle(Particle.DUST, at, 2, .1, .1, .1, 0, new Particle.DustOptions(Color.fromRGB(170, 230, 255), 1.2f));
        }, () -> {
            if (!w.equals(p.getWorld()) || !p.isOnline()) return;
            w.spawnParticle(Particle.SNOWFLAKE, c, 40, 1.0, .6, 1.0, .08);
            w.spawnParticle(Particle.BLOCK, c.clone().add(0, .3, 0), 40, 1.0, .5, 1.0, 0, Material.ICE.createBlockData());
            w.spawnParticle(Particle.BLOCK, c.clone().add(0, .3, 0), 25, .6, .4, .6, 0, Material.PACKED_ICE.createBlockData());
            w.playSound(c, Sound.BLOCK_GLASS_BREAK, 1.2f, 1.2f);
            w.playSound(c, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1f, 1.3f);
            for (LivingEntity t : near(p, c, 2.5)) {
                hit(p, t, dmg);
                if (t.isValid()) t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2));
            }
        });
    }

    /** 낙뢰: for 3 s, every 0.5 s lightning (visual only) strikes up to 3 nearby enemies for 60% attack each. */
    private void thunder(Player p) {
        World w = p.getWorld();
        double dmg = power(p, Job.MAGE) * .6;
        run(10, 6, i -> {
            if (!ready(p, w)) return true;
            Location c = p.getLocation();
            List<LivingEntity> targets = near(p, c, 12);
            targets.sort(Comparator.comparingDouble(t -> t.getLocation().distanceSquared(c)));
            if (targets.isEmpty()) {
                Location at = c.clone().add(0, 1, 0);
                w.spawnParticle(Particle.ELECTRIC_SPARK, at, 25, 1.2, .8, 1.2, .1);
                w.spawnParticle(Particle.END_ROD, at, 6, 1, .8, 1, .05);
                w.playSound(c, Sound.BLOCK_BEEHIVE_SHEAR, .6f, 2f);
                return false;
            }
            for (int n = 0; n < Math.min(3, targets.size()); n++) {
                LivingEntity t = targets.get(n);
                Location at = t.getLocation();
                w.strikeLightningEffect(at);
                w.spawnParticle(Particle.ELECTRIC_SPARK, at.clone().add(0, 1, 0), 30, .5, 1, .5, .15);
                w.spawnParticle(Particle.END_ROD, at.clone().add(0, 1, 0), 10, .3, 1.2, .3, .08);
                w.playSound(at, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, .6f, 1.4f);
                hit(p, t, dmg);
            }
            return false;
        }, null);
    }

    /** 마력 소용돌이: for 3 s a vortex at the aimed point (no pull) pulses every 0.5 s: radius 3, 40% attack. */
    private void vortex(Player p) {
        Location c = aimPoint(p, 20).add(0, .15, 0);
        World w = c.getWorld();
        double dmg = power(p, Job.MAGE) * .4;
        Color purple = Color.fromRGB(160, 70, 235);
        ItemDisplay ring = fx("whirl_ring", c, new Quaternionf(), vec(4.2f), null, 72);
        w.playSound(c, Sound.BLOCK_BEACON_ACTIVATE, .8f, 1.6f);
        run(2, 30, i -> {
            if (!ready(p, w)) return true;
            if (ring != null && ring.isValid()) tween(ring, 1, 2, new Vector3f(), new Quaternionf().rotateY(i * .55f), vec(4.2f));
            for (int k = 0; k < 10; k++) {
                double a = i * .6 + k * Math.PI / 5, r = 2.8 - (k % 5) * .35, y = (k % 5) * .45;
                Location at = c.clone().add(Math.cos(a) * r, y, Math.sin(a) * r);
                w.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, new Particle.DustOptions(purple, 1.3f));
                if (k % 3 == 0) w.spawnParticle(Particle.WITCH, at, 1, .05, .05, .05, 0);
            }
            if (i % 5 == 0) {
                w.playSound(c, Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, .5f, 1.5f);
                w.spawnParticle(Particle.PORTAL, c.clone().add(0, 1, 0), 24, 1.5, .8, 1.5, .4);
                for (LivingEntity t : near(p, c, 3)) hit(p, t, dmg);
            }
            return false;
        }, () -> kill(ring));
    }

    /** 얼음 감옥: encases the nearest enemy within 20 in crystal shards: Slowness 255 for 2 s and 120% attack. */
    private void prison(Player p) {
        Location from = p.getLocation();
        List<LivingEntity> targets = near(p, from, 20);
        if (targets.isEmpty()) return;
        targets.sort(Comparator.comparingDouble(t -> t.getLocation().distanceSquared(from)));
        LivingEntity t = targets.get(0);
        World w = t.getWorld();
        hit(p, t, power(p, Job.MAGE) * 1.2);
        if (!t.isValid() || t.isDead()) return;
        t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 255, false, true));
        w.playSound(t.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, .8f);
        w.playSound(t.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1f, 1.1f);
        float h = (float) Math.max(1.2, t.getHeight()), r = (float) Math.max(.7, t.getWidth() * .8 + .3);
        ItemDisplay[] shards = new ItemDisplay[8];
        for (int s = 0; s < 8; s++) {
            double a = s * Math.PI / 4;
            Location at = t.getLocation().add(Math.cos(a) * r, h * .45, Math.sin(a) * r);
            shards[s] = fx("crystal", at, new Quaternionf().rotateY((float) -a).rotateZ(.35f), vec(h * .45f), null, 50);
        }
        run(2, 20, i -> {
            if (!t.isValid() || t.isDead() || t.getWorld() != w) return true;
            Location base = t.getLocation();
            for (int s = 0; s < 8; s++) {
                double a = s * Math.PI / 4;
                moveFx(shards[s], base.clone().add(Math.cos(a) * r, h * .45, Math.sin(a) * r));
            }
            if (i % 3 == 0) {
                w.spawnParticle(Particle.SNOWFLAKE, t.getBoundingBox().getCenter().toLocation(w), 6, r * .5, h * .35, r * .5, .01);
                w.spawnParticle(Particle.DUST, base.clone().add(0, h * .5, 0), 6, r * .5, h * .4, r * .5, 0, new Particle.DustOptions(Color.fromRGB(170, 230, 255), 1.2f));
            }
            return false;
        }, () -> {
            for (ItemDisplay d : shards) kill(d);
            if (t.isValid()) {
                w.spawnParticle(Particle.BLOCK, t.getBoundingBox().getCenter().toLocation(w), 30, r * .5, h * .4, r * .5, 0, Material.ICE.createBlockData());
                w.playSound(t.getLocation(), Sound.BLOCK_GLASS_BREAK, .8f, 1.4f);
            }
        });
    }

    private void light(Player p) { // 치유의 빛
        World w = p.getWorld();
        healBy(p, .30);
        for (Player ally : w.getPlayers())
            if (ally != p && !ally.isDead() && ally.getGameMode() != GameMode.SPECTATOR
                && ally.getLocation().distanceSquared(p.getLocation()) <= 36) {
                healBy(ally, .30);
                w.spawnParticle(Particle.HEART, ally.getLocation().add(0, 2, 0), 3, .3, .2, .3, 0);
            }
        Location c = p.getLocation();
        ItemDisplay cross = fx("heal_cross", c.clone().add(0, 2.5, 0), new Quaternionf(), vec(.2f), null, 24);
        for (int s = 1; s <= 3; s++) {
            tween(cross, s * 3 - 2, 3, new Vector3f(0, s * .15f, 0), new Quaternionf().rotateY((float) Math.toRadians(120 * s)).rotateZ(.2f), vec(s == 1 ? .55f : .7f));
        }
        tween(cross, 11, 4, new Vector3f(0, .8f, 0), new Quaternionf().rotateY((float) Math.toRadians(480)), vec(.05f));
        for (int g = 0; g < 4; g++) {
            double ga = g * Math.PI / 2 + .4;
            ItemDisplay gem = fx("crystal", c.clone().add(Math.cos(ga) * 1.6, .3, Math.sin(ga) * 1.6), new Quaternionf(), vec(.12f), null, 22);
            tween(gem, 1, 8, new Vector3f(0, 1.8f, 0), new Quaternionf().rotateY(2f).rotateZ(.1f), vec(.3f));
            tween(gem, 10, 4, new Vector3f(0, 2.4f, 0), new Quaternionf().rotateY(3f), vec(.03f));
        }
        for (double y = 0; y <= 3; y += .3) w.spawnParticle(Particle.END_ROD, c.clone().add(0, y, 0), 1, 0, 0, 0, 0);
        for (double x = -1; x <= 1; x += .3) w.spawnParticle(Particle.END_ROD, c.clone().add(x, 2, 0), 1, 0, 0, 0, 0); // cross
        run(1, 6, i -> {
            double r = 1 + i;
            ring(p.getLocation().add(0, .2, 0), r, (int) (r * 7), at -> {
                w.spawnParticle(Particle.END_ROD, at, 1, 0, 0, 0, 0);
                w.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(80, 230, 255), 1.2f));
            });
            return !p.isOnline();
        }, null);
    }

    private void meteor(Player p) {
        Location target = aimPoint(p, 22);
        World w = target.getWorld();
        Location top = target.clone().add(0, 18, 0);
        float msc = 3.2f;
        ItemDisplay rock = fx("meteor", top, new Quaternionf().rotateY(.6f), vec(msc), new ItemStack(Material.MAGMA_BLOCK), 60);
        Location to = target.clone().add(0, msc * .35, 0);
        double dmg = power(p, Job.MAGE) * 9;
        fall(rock, top, to, 12, c -> {
            w.spawnParticle(Particle.FLAME, c, 12, .8, .8, .8, .05);
            w.spawnParticle(Particle.LARGE_SMOKE, c, 6, .8, .8, .8, .02);
            ring(target.clone().add(0, .2, 0), 6, 24, at -> w.spawnParticle(Particle.FLAME, at, 1, 0, 0, 0, 0));
        }, () -> {
            w.spawnParticle(Particle.EXPLOSION_EMITTER, target, 2, 1, .3, 1, 0);
            w.spawnParticle(Particle.EXPLOSION, target, 20, 2.5, .5, 2.5, 0);
            w.spawnParticle(Particle.FLAME, target.clone().add(0, 1, 0), 120, 2.5, 1, 2.5, .15);
            w.playSound(target, Sound.ENTITY_GENERIC_EXPLODE, 2f, .6f);
            for (int k = 0; k < 4; k++) spikes(target.clone().add(0, .05, 0), new Vector(Math.cos(k * Math.PI / 4), 0, Math.sin(k * Math.PI / 4)), 2.6f);
            run(1, 6, i -> {
                double r = 1 + i;
                ring(target.clone().add(0, .2, 0), r, (int) (r * 8), at -> w.spawnParticle(Particle.FLAME, at, 1, 0, .1, 0, .02));
                return false;
            }, null);
            for (LivingEntity t : near(p, target, 6)) {
                hit(p, t, dmg);
                if (t.isValid()) t.setFireTicks(60);
            }
        });
    }

    /** Magic orb: straight or homing, stops at blocks or the first enemy within 0.9 blocks. */
    private void orb(Player p, Location start, Vector dir, double speed, int ticks, double dmg, LivingEntity homing, long[] mark, float size) {
        World w = start.getWorld();
        ItemDisplay ball = fx("orb", start, new Quaternionf(), vec(size), new ItemStack(Material.AMETHYST_SHARD), ticks + 10);
        Location pos = start.clone();
        Vector d = dir.clone().normalize();
        Color purple = Color.fromRGB(170, 70, 235);
        run(1, ticks, i -> {
            if (homing != null && homing.isValid() && homing.getWorld() == w) {
                Vector want = homing.getBoundingBox().getCenter().subtract(pos.toVector());
                if (want.lengthSquared() > 0) d.multiply(.75).add(want.normalize().multiply(.25)).normalize();
            }
            var block = w.rayTraceBlocks(pos, d, speed, FluidCollisionMode.NEVER, true);
            if (block != null) {
                w.spawnParticle(Particle.DUST, block.getHitPosition().toLocation(w), 10, .2, .2, .2, 0, new Particle.DustOptions(purple, 1.5f));
                return true;
            }
            pos.add(d.clone().multiply(speed));
            if (ball != null) {
                ball.setTeleportDuration(1);
                moveFx(ball, pos);
                tween(ball, 1, 1, new Vector3f(), new Quaternionf().rotateY(i * .9f).rotateX(i * .6f), vec(size));
            }
            w.spawnParticle(Particle.DUST, pos, 4, .08, .08, .08, 0, new Particle.DustOptions(purple, 1.6f));
            w.spawnParticle(Particle.WITCH, pos, 1, .05, .05, .05, 0);
            for (LivingEntity t : near(p, pos, .9)) {
                if (mark != null) chargeHit(p, t, dmg, (int) mark[0], mark[1]); else hit(p, t, dmg);
                w.spawnParticle(Particle.DUST, pos, 14, .3, .3, .3, 0, new Particle.DustOptions(purple, 1.8f));
                return true;
            }
            return !p.isOnline();
        }, () -> kill(ball));
    }

    // ---------------------------------------------------------------- dash jump

    /** WASD input sent with the dash (strafe: left +, forward: forward +); consumed by the next dash. */
    private final Map<UUID, double[]> dashInputs = new HashMap<>();
    void dashInput(Player p, double strafe, double forward) {
        dashInputs.put(p.getUniqueId(), new double[]{Math.clamp(strafe, -1, 1), Math.clamp(forward, -1, 1)});
    }

    private boolean dash(Player p, long now) {
        boolean air = !p.isOnGround() || now - lastGround.getOrDefault(p.getUniqueId(), 0L) < 300;
        if (!air) return false;
        Vector look = p.getEyeLocation().getDirection().setY(0);
        if (look.lengthSquared() < .01) return false;
        look.normalize();
        Vector heading = look.clone();
        double[] in = dashInputs.remove(p.getUniqueId());
        if (in != null && (Math.abs(in[0]) > .05 || Math.abs(in[1]) > .05)) {
            Vector left = new Vector(look.getZ(), 0, -look.getX()); // 90° to the left of the view
            Vector moved = look.clone().multiply(in[1]).add(left.multiply(in[0]));
            if (moved.lengthSquared() > .0025) heading = moved.normalize();
        }
        Location origin = p.getLocation();
        if (!origin.getWorld().getWorldBorder().isInside(origin.clone().add(heading.clone().multiply(1.5)))) return false;
        double lift = Math.max(.25, Math.min(.5, p.getVelocity().getY() + .2));
        p.setVelocity(heading.multiply(1.2).setY(lift));
        fallImmune.put(p.getUniqueId(), now + 2_000);
        World w = origin.getWorld();
        w.spawnParticle(Particle.CLOUD, origin.clone().add(0, .3, 0), 18, .35, .15, .35, .05);
        w.playSound(origin, Sound.ENTITY_BAT_TAKEOFF, .8f, 1.4f);
        run(1, 5, i -> {
            w.spawnParticle(Particle.CLOUD, p.getLocation().add(0, .5, 0), 5, .25, .25, .25, .02);
            return !p.isOnline();
        }, null);
        return true;
    }

    // ---------------------------------------------------------------- helpers

    /** 공격력: attack attribute (includes CombatStats bonus); bows and staves have no weapon base so add a flat 4. */
    private double power(Player p, Job job) {
        double a = p.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).getValue();
        return job == Job.WARRIOR ? a : a + 4;
    }

    private void hit(Player p, LivingEntity target, double damage) {
        if (!p.isOnline() || !target.isValid() || target.isDead()) return;
        target.setNoDamageTicks(0);
        Vector before = target.getVelocity(); // 스킬은 넉백 없음: 피해 직후 속도를 되돌린다
        target.damage(damage, p);
        if (target.isValid()) {
            target.setVelocity(before);
            Bukkit.getScheduler().runTask(plugin, () -> { if (target.isValid()) target.setVelocity(before); });
        }
    }

    private void healBy(Player p, double fraction) {
        double max = p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        p.setHealth(Math.min(max, p.getHealth() + max * fraction));
        p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 2, 0), 4, .3, .2, .3, 0);
    }

    private boolean enemy(Player p, Entity e) {
        return e instanceof Enemy && e instanceof LivingEntity le && !(e instanceof Player) && le.isValid() && !le.isDead();
    }

    /** Enemies whose body is within {@code r} of {@code c} (players are never returned). */
    private List<LivingEntity> near(Player p, Location c, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : c.getWorld().getNearbyEntities(c, r + 2, r + 2, r + 2))
            if (enemy(p, e) && e.getBoundingBox().getCenter().distance(c.toVector()) <= r + ((LivingEntity) e).getWidth() / 2)
                out.add((LivingEntity) e);
        return out;
    }

    /** Enemies inside a capsule from {@code start} along {@code dir}, nearest first. */
    private List<LivingEntity> along(Player p, Location start, Vector dir, double len, double width) {
        List<LivingEntity> out = new ArrayList<>();
        Location mid = start.clone().add(dir.clone().multiply(len / 2));
        double box = len / 2 + width + 2;
        for (Entity e : start.getWorld().getNearbyEntities(mid, box, box, box)) {
            if (!enemy(p, e)) continue;
            LivingEntity le = (LivingEntity) e;
            Vector rel = le.getBoundingBox().getCenter().subtract(start.toVector());
            double t = rel.dot(dir);
            if (t < -.5 || t > len + .5) continue;
            if (rel.clone().subtract(dir.clone().multiply(t)).length() <= width + le.getWidth() / 2) out.add(le);
        }
        out.sort(Comparator.comparingDouble(t -> t.getLocation().distanceSquared(start)));
        return out;
    }

    private double blockLen(Player p, Location start, Vector dir, double len) {
        var hit = start.getWorld().rayTraceBlocks(start, dir, len, FluidCollisionMode.NEVER, true);
        return hit == null ? len : Math.max(.5, hit.getHitPosition().distance(start.toVector()));
    }

    /** Draws a particle beam (trimmed at blocks / the last allowed target) and returns the enemies it hits. */
    private List<LivingEntity> beam(Player p, Location start, Vector dir, double len, double width, int max, Particle particle, Color dust) {
        len = blockLen(p, start, dir, len);
        List<LivingEntity> hits = along(p, start, dir, len, width);
        beamLen = len;
        if (max > 0 && hits.size() > max) hits = new ArrayList<>(hits.subList(0, max));
        if (max > 0 && hits.size() == max)
            len = Math.min(len, hits.get(max - 1).getBoundingBox().getCenter().distance(start.toVector()) + .5);
        beamLen = len;
        World w = start.getWorld();
        for (double s = .5; s <= len; s += .5) {
            Location c = start.clone().add(dir.clone().multiply(s));
            w.spawnParticle(particle, c, 1, 0, 0, 0, 0);
            w.spawnParticle(Particle.DUST, c, 1, .05, .05, .05, 0, new Particle.DustOptions(dust, 1f));
        }
        return hits;
    }

    private boolean autoAim() { return plugin.getConfig().getBoolean("combat.auto-aim", true); }

    /** Valid auto-aim candidate: an enemy that is not one of the caster's own entities. */
    private boolean aimable(Player p, Entity e) {
        if (!enemy(p, e) || e.getScoreboardTags().contains("pipi_pet_worker")) return false;
        if (e instanceof org.bukkit.entity.Tameable t && t.isTamed() && p.equals(t.getOwner())) return false;
        return !p.getPassengers().contains(e) && !e.equals(p.getVehicle());
    }

    /** Nearest enemy inside a 40 degree cone of the look direction within range; optionally any enemy within range. */
    private LivingEntity aimTarget(Player p, Location eye, Vector look, double range, boolean anyInRange) {
        double cos = Math.cos(Math.toRadians(40));
        LivingEntity cone = null, any = null;
        double coneD = Double.MAX_VALUE, anyD = Double.MAX_VALUE;
        for (Entity e : eye.getWorld().getNearbyEntities(eye, range + 2, range + 2, range + 2)) {
            if (!aimable(p, e)) continue;
            Vector rel = e.getBoundingBox().getCenter().subtract(eye.toVector());
            double d = rel.length();
            if (d > range || d < 1e-6) continue;
            if (d < anyD) { anyD = d; any = (LivingEntity) e; }
            if (look.dot(rel) / d >= cos && d < coneD) { coneD = d; cone = (LivingEntity) e; }
        }
        return cone != null ? cone : anyInRange ? any : null;
    }

    /** Look direction, or the direction to the preferred enemy (40 degree cone) when auto-aim is on. */
    private Vector aimDir(Player p, Location eye, double range) {
        Vector look = eye.getDirection().normalize();
        if (!autoAim()) return look;
        LivingEntity t = aimTarget(p, eye, look, range, false);
        if (t == null) return look;
        Vector d = t.getBoundingBox().getCenter().subtract(eye.toVector());
        return d.lengthSquared() < 1e-6 ? look : d.normalize();
    }

    /** Ground point: the preferred enemy's feet (cone, else nearest in range), else the aimed point. */
    private Location aimPoint(Player p, double range) {
        if (autoAim()) {
            Location eye = p.getEyeLocation();
            LivingEntity t = aimTarget(p, eye, eye.getDirection().normalize(), range, true);
            if (t != null) return t.getLocation().add(0, .1, 0);
        }
        return targetPoint(p, range);
    }

    /** Aimed ground point within {@code range}: block hit, else the ray end dropped onto the floor. */
    private Location targetPoint(Player p, double range) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        World w = eye.getWorld();
        var hit = w.rayTraceBlocks(eye, dir, range, FluidCollisionMode.NEVER, true);
        if (hit != null) {
            Location at = hit.getHitPosition().toLocation(w);
            if (hit.getHitBlockFace() != null) at.add(hit.getHitBlockFace().getDirection().multiply(.1));
            return at;
        }
        Location end = eye.clone().add(dir.multiply(range));
        var floor = w.rayTraceBlocks(end, new Vector(0, -1, 0), 30, FluidCollisionMode.NEVER, true);
        return floor == null ? end : floor.getHitPosition().toLocation(w).add(0, .1, 0);
    }

    private void ring(Location c, double r, int points, Consumer<Location> each) {
        for (int k = 0; k < points; k++) {
            double a = Math.PI * 2 * k / points;
            each.accept(c.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r));
        }
    }

    /** Runs {@code body} every {@code period} ticks for {@code steps} steps (body returns true to stop), then {@code done}. */
    private void run(int period, int steps, IntPredicate body, Runnable done) {
        new BukkitRunnable() {
            int i = 0;
            @Override public void run() {
                if (i >= steps || body.test(i++)) {
                    cancel();
                    if (done != null) done.run();
                }
            }
        }.runTaskTimer(plugin, 0, period);
    }

    /** Moves a display (may be null) from {@code from} to {@code to} over {@code ticks}, calls trail each tick, then removes it and calls land. */
    private void fall(Display display, Location from, Location to, int ticks, Consumer<Location> trail, Runnable land) {
        run(1, ticks, i -> {
            Location c = from.clone().add(to.clone().subtract(from).toVector().multiply((i + 1.0) / ticks));
            if (display != null && display.isValid()) display.teleport(c);
            trail.accept(c);
            return false;
        }, () -> {
            kill(display);
            land.run();
        });
    }

    // ---- 3D effect models (PipiFacilities items fx_<name>, see dev/art/make-fx-models.mjs)

    private static final Map<String, Material> FX_MATERIALS = new HashMap<>();

    private static Vector3f vec(float s) { return new Vector3f(s, s, s); }

    private static Material fxMaterial(String id) {
        return FX_MATERIALS.computeIfAbsent(id, k -> Material.matchMaterial("PIPIFACILITIES_FX_" + k.toUpperCase(java.util.Locale.ROOT)));
    }

    /** Rotation taking the model forward axis (+Z once rendered) onto dir, then rolling around it. */
    private static Quaternionf facing(Vector dir, float roll) {
        Vector d = dir.clone();
        if (d.lengthSquared() < 1e-6) d = new Vector(0, 0, 1); else d.normalize();
        float yaw = (float) Math.atan2(d.getX(), d.getZ());
        float pitch = (float) Math.asin(Math.max(-1, Math.min(1, d.getY())));
        return new Quaternionf().rotateY(yaw).rotateX(-pitch).rotateZ(roll);
    }

    private static Location flat(Location l) { Location c = l.clone(); c.setYaw(0); c.setPitch(0); return c; }

    /**
     * Spawns an ItemDisplay showing the 3D effect item id (full brightness, fixed billboard, entity rotation zeroed so only
     * the transformation orients it). Falls back to the given vanilla item when the mod item is missing; null when nothing to show or cap hit.
     */
    private ItemDisplay fx(String id, Location at, Quaternionf rot, Vector3f scale, ItemStack fallback, int life) {
        if (displays.size() >= MAX_DISPLAYS) return null;
        Material m = fxMaterial(id);
        ItemStack item = m != null ? new ItemStack(m) : fallback;
        if (item == null) return null;
        ItemDisplay d = at.getWorld().spawn(flat(at), ItemDisplay.class, display -> {
            display.setItemStack(item);
            display.setPersistent(false);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setBillboard(Display.Billboard.FIXED);
            display.setTeleportDuration(1);
            display.setViewRange(3f);
            display.setTransformation(new Transformation(new Vector3f(), rot, scale, new Quaternionf()));
        });
        track(d, Math.max(10, Math.min(life, 140)));
        return d;
    }

    /** Smoothly animates a display to the given transformation over dur ticks, starting after ticks from now. */
    private void tween(ItemDisplay d, int after, int dur, Vector3f translate, Quaternionf rot, Vector3f scale) {
        if (d == null) return;
        Vector3f tr = new Vector3f(translate); Quaternionf q = new Quaternionf(rot); Vector3f sc = new Vector3f(scale);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!d.isValid()) return;
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(dur);
            d.setTransformation(new Transformation(tr, q, sc, new Quaternionf()));
        }, Math.max(1, after));
    }

    private void moveFx(Display d, Location to) {
        if (d != null && d.isValid()) d.teleport(flat(to));
    }

    /** Smooth straight flight of a display to the target over the given ticks (teleport interpolation). */
    private void flyFx(Display d, Location to, int ticks) {
        if (d == null) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!d.isValid()) return;
            d.setTeleportDuration(Math.max(1, Math.min(59, ticks)));
            d.teleport(flat(to));
        }, 1);
    }

    /** Sweeping slash crescent: grows while rotating across its own plane, then shrinks away (about 9 ticks). */
    private void slash(String id, Location at, Vector dir, float scale, float roll, float sweep) {
        Quaternionf base = facing(dir, roll);
        ItemDisplay d = fx(id, at, new Quaternionf(base).rotateY(-sweep), new Vector3f(scale * .45f, scale * .45f, scale * .45f), null, 20);
        tween(d, 1, 3, new Vector3f(), new Quaternionf(base).rotateY(sweep * .6f), vec(scale));
        tween(d, 5, 3, new Vector3f(), new Quaternionf(base).rotateY(sweep), new Vector3f(scale * 1.05f, scale * .2f, scale * 1.05f));
    }

    /** Short-lived slash (gone after ~6 ticks) for the basic attack. */
    private void slashQuick(String id, Location at, Vector dir, float scale, float roll, float sweep) {
        Quaternionf base = facing(dir, roll);
        ItemDisplay d = fx(id, at, new Quaternionf(base).rotateY(-sweep), new Vector3f(scale * .45f, scale * .45f, scale * .45f), null, 6);
        tween(d, 1, 2, new Vector3f(), new Quaternionf(base).rotateY(sweep * .6f), vec(scale));
        tween(d, 3, 2, new Vector3f(), new Quaternionf(base).rotateY(sweep), new Vector3f(scale * 1.05f, scale * .2f, scale * 1.05f));
    }

    /** Big arrow flying from the start along dir for dist blocks over the given ticks, then gone. */
    private void bigArrow(Location from, Vector dir, float scale, double dist, int ticks) {
        ItemDisplay a = fx("arrow_big", from, facing(dir, 0), vec(scale), new ItemStack(Material.ARROW), ticks + 4);
        if (a == null) return;
        flyFx(a, from.clone().add(dir.clone().normalize().multiply(dist)), ticks);
        tween(a, ticks, 2, new Vector3f(), facing(dir, 0), vec(scale * .1f));
    }

    /** Ground spike row perpendicular to heading: thrusts up and sinks away again. */
    private void spikes(Location at, Vector heading, float scale) {
        Quaternionf q = facing(heading, 0);
        float lift = .12f * scale;
        ItemDisplay d = fx("shockwave", at, q, new Vector3f(scale, scale * .1f, scale), null, 22);
        tween(d, 1, 2, new Vector3f(0, lift, 0), q, new Vector3f(scale, scale * 1.1f, scale));
        tween(d, 6, 4, new Vector3f(0, lift * .2f, 0), q, new Vector3f(scale * .9f, scale * .05f, scale * .9f));
    }

    private ItemDisplay spawnItem(Location at, ItemStack item, float scale, Quaternionf rotation) {
        if (displays.size() >= MAX_DISPLAYS) return null;
        ItemDisplay d = at.getWorld().spawn(at, ItemDisplay.class, display -> {
            display.setItemStack(item);
            display.setPersistent(false);
            display.setBillboard(Display.Billboard.VERTICAL);
            display.setTeleportDuration(1);
            display.setViewRange(2f);
            display.setTransformation(new Transformation(new Vector3f(), rotation, new Vector3f(scale, scale, scale), new Quaternionf()));
        });
        track(d);
        return d;
    }

    private BlockDisplay spawnBlock(Location at, Material material, float scale) {
        if (displays.size() >= MAX_DISPLAYS) return null;
        BlockDisplay d = at.getWorld().spawn(at, BlockDisplay.class, display -> {
            display.setBlock(material.createBlockData());
            display.setPersistent(false);
            display.setTeleportDuration(1);
            display.setViewRange(2f);
            display.setTransformation(new Transformation(new Vector3f(-scale / 2, -scale / 2, -scale / 2), new Quaternionf(),
                new Vector3f(scale, scale, scale), new Quaternionf()));
        });
        track(d);
        return d;
    }

    /** Registers a display and guarantees removal after 5 s even if its effect task dies. */
    private void track(Display d) { track(d, 100); }

    private void track(Display d, int life) {
        displays.add(d);
        Bukkit.getScheduler().runTaskLater(plugin, () -> kill(d), life);
    }

    private void kill(Display d) {
        if (d == null) return;
        displays.remove(d);
        if (d.isValid()) d.remove();
    }
}
