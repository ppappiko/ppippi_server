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
    private final Map<UUID, Long> will = new HashMap<>();        // 투지: -10% damage until
    private final Map<UUID, Long> atkBuff = new HashMap<>();     // 공격력 버프: +20% damage until
    private final Map<UUID, Long> fortress = new HashMap<>();    // 불굴: invulnerable + frozen until
    private final Map<UUID, Long> wolfUntil = new HashMap<>();   // 늑대 정령: archer basic is a wolf spirit until
    private final Map<UUID, Long> speedMods = new HashMap<>();     // 숲의 숨결 speed modifier: player -> expiry millis
    private final Map<UUID, Long> fallImmune = new HashMap<>();  // dash: no fall damage until
    private final Map<UUID, Long> lastGround = new HashMap<>();
    private final Set<Display> displays = new HashSet<>();
    private boolean flip;
    private double beamLen = 20;
    private enum Job { WARRIOR, ARCHER, MAGE }

    // ---------------------------------------------------------------- loadout (equipped general skills)

    // Cooldown / charge array layout: 0-7 fixed slots, 8-25 general (6 per class), 26-34 buffs (3 per class), 35-40 ultimates (2 per class).
    private static final int KEYS = 41;
    /** General (equippable in keys 1-3) skill ids per class; the index is the skill's ordinal used for cooldown keys. */
    private static final String[][] IDS = {
        {"w_rush", "w_whirl", "w_split", "w_heavy", "w_impale", "w_iai"},
        {"a_triple", "a_rain", "a_pierce", "a_spread", "a_shotgun", "a_gale"},
        {"m_seek", "m_frost", "m_thunder", "m_wave", "m_hammer", "m_bow"}};
    /** Buff ids per class (key 4, slot 0). */
    private static final String[][] BUFFS = {
        {"w_heal", "w_rage", "w_fort"}, {"a_heal", "a_focus", "a_spirit"}, {"m_heal", "m_focus", "m_reset"}};
    /** Ultimate ids per class (key 5, slot 4). */
    private static final String[][] ULTS = {{"w_ult", "w_tiger"}, {"a_ult", "a_wolf"}, {"m_cannon", "m_dragon"}};
    private static final char[] CLS = {'w', 'a', 'm'};

    // skill keys (cooldown / charge / mark index)
    private static final int K_RUSH = 8, K_WHIRL = 9, K_SPLIT = 10, K_HEAVY = 11, K_IMPALE = 12, K_IAI = 13;
    private static final int K_TRIPLE = 14, K_RAIN = 15, K_PIERCE = 16, K_SPREAD = 17, K_SHOTGUN = 18, K_GALE = 19;
    private static final int K_SEEK = 20, K_FROST = 21, K_THUNDER = 22, K_WAVE = 23, K_HAMMER = 24, K_BOW = 25;
    private static final int K_W_HEAL = 26, K_W_RAGE = 27, K_W_FORT = 28, K_A_HEAL = 29, K_A_FOCUS = 30, K_A_SPIRIT = 31;
    private static final int K_M_HEAL = 32, K_M_FOCUS = 33, K_M_RESET = 34;
    private static final int K_W_ULT = 35, K_W_TIGER = 36, K_A_ULT = 37, K_A_WOLF = 38, K_M_CANNON = 39, K_M_DRAGON = 40;

    /** Base cooldowns (ms) per key; fixed slots (5 basic, 7 shield) are handled in baseCooldown. */
    private static final int[] CDS = new int[KEYS];
    static {
        int[] g = {4_000, 5_000, 6_000, 8_000, 9_000, 14_000,   3_000, 7_000, 6_000, 4_000, 12_000, 9_000,   3_000, 5_000, 9_000, 5_000, 10_000, 14_000};
        System.arraycopy(g, 0, CDS, 8, g.length);
        int[] b = {25_000, 30_000, 45_000,   25_000, 30_000, 45_000,   30_000, 30_000, 90_000};
        System.arraycopy(b, 0, CDS, 26, b.length);
        int[] u = {90_000, 90_000, 90_000, 90_000, 100_000, 100_000};
        System.arraycopy(u, 0, CDS, 35, u.length);
    }

    private static ClassSkills current;
    /** Per player, per class: [g1, g2, g3, buff, ult] ordinals (general ordinals index IDS, then BUFFS, then ULTS). */
    private final Map<UUID, int[][]> loadouts = new HashMap<>();
    private final java.io.File loadoutFile;
    private final org.bukkit.configuration.file.YamlConfiguration loadoutData;

    private static int gen(Job job, int ord) { return 8 + job.ordinal() * 6 + ord; }
    private static int buffKey(Job job, int ord) { return 26 + job.ordinal() * 3 + ord; }
    private static int ultKey(Job job, int ord) { return 35 + job.ordinal() * 2 + ord; }

    private static int[][] defaultLoadout() { return new int[][] {{0, 1, 2, 0, 0}, {0, 1, 2, 0, 0}, {0, 1, 2, 0, 0}}; }

    private int[] loadout(Player p, Job job) {
        return loadouts.computeIfAbsent(p.getUniqueId(), k -> defaultLoadout())[job.ordinal()];
    }

    private static String loadoutText(int job, int[] o) {
        return IDS[job][o[0]] + "," + IDS[job][o[1]] + "," + IDS[job][o[2]] + "," + BUFFS[job][o[3]] + "," + ULTS[job][o[4]];
    }

    /** Parses 3 (old form: buff/ult default) or 5 ids; null when any id is unknown/duplicated. */
    private static int[] parseLoadout(int j, String[] parts) {
        if (parts.length != 3 && parts.length != 5) return null;
        int[] o = new int[5];
        for (int k = 0; k < 3; k++) {
            o[k] = java.util.Arrays.asList(IDS[j]).indexOf(parts[k].trim());
            if (o[k] < 0) return null;
        }
        if (o[0] == o[1] || o[0] == o[2] || o[1] == o[2]) return null;
        if (parts.length == 5) {
            o[3] = java.util.Arrays.asList(BUFFS[j]).indexOf(parts[3].trim());
            o[4] = java.util.Arrays.asList(ULTS[j]).indexOf(parts[4].trim());
            if (o[3] < 0 || o[4] < 0) return null;
        }
        return o;
    }

    private void loadLoadouts() {
        for (String id : loadoutData.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(id);
                int[][] all = defaultLoadout();
                for (int j = 0; j < 3; j++) {
                    String text = loadoutData.getString(id + "." + CLS[j]);
                    if (text == null) continue;
                    int[] o = parseLoadout(j, text.split(","));
                    if (o != null) all[j] = o;
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

    /** /pipilife skill loadout &lt;w|a|m&gt; &lt;g1&gt; &lt;g2&gt; &lt;g3&gt; [&lt;buff&gt; &lt;ult&gt;]: only outside combat mode. */
    boolean loadoutCommand(Player p, String[] args) {
        if (Armory.inCombatMode(p)) { p.sendMessage("전투 모드에서는 스킬 장착을 바꿀 수 없어요. (Alt로 전투 모드를 끄세요)"); return true; }
        if (args.length != 6 && args.length != 8) { p.sendMessage("/pipilife skill loadout <w|a|m> <스킬1> <스킬2> <스킬3> [<버프> <궁극기>]"); return true; }
        int j = switch (args[2].toLowerCase(java.util.Locale.ROOT)) {
            case "w", "warrior" -> 0; case "a", "archer" -> 1; case "m", "mage" -> 2; default -> -1; };
        if (j < 0) { p.sendMessage("직업은 w, a, m 중 하나여야 해요."); return true; }
        int[] ords = new int[5];
        int[] cur = loadouts.computeIfAbsent(p.getUniqueId(), k -> defaultLoadout())[j];
        for (int k = 0; k < 3; k++) {
            ords[k] = java.util.Arrays.asList(IDS[j]).indexOf(args[3 + k]);
            if (ords[k] < 0) { p.sendMessage("이 직업의 스킬이 아니에요: " + args[3 + k]); return true; }
        }
        if (ords[0] == ords[1] || ords[0] == ords[2] || ords[1] == ords[2]) { p.sendMessage("같은 스킬을 두 번 장착할 수 없어요."); return true; }
        if (args.length == 8) {
            ords[3] = java.util.Arrays.asList(BUFFS[j]).indexOf(args[6]);
            ords[4] = java.util.Arrays.asList(ULTS[j]).indexOf(args[7]);
            if (ords[3] < 0) { p.sendMessage("이 직업의 버프가 아니에요: " + args[6]); return true; }
            if (ords[4] < 0) { p.sendMessage("이 직업의 궁극기가 아니에요: " + args[7]); return true; }
        } else { ords[3] = cur[3]; ords[4] = cur[4]; }
        loadouts.get(p.getUniqueId())[j] = ords;
        saveLoadouts(p);
        syncLoadouts(p);
        return true;
    }

    /** Hidden PIPI_LOADOUT messages for all three classes (client cancels them), then the cooldowns of the skills now equipped. */
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
        int bk = buffKey(job, mine[3]), uk = ultKey(job, mine[4]);
        if (cd[bk] > now) p.sendMessage("회복 재사용 대기 · " + (cd[bk] - now) + "ms");
        if (cd[uk] > now) p.sendMessage("궁극기 재사용 대기 · " + (cd[uk] - now) + "ms");
    }

    /** Armory calls this when a player enters combat mode. */
    static void syncFor(Player p) { if (current != null) current.syncLoadouts(p); }

    @EventHandler
    public void joined(org.bukkit.event.player.PlayerJoinEvent event) {
        Player p = event.getPlayer();
        clearSpeedMod(p);
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
        for (UUID id : new ArrayList<>(speedMods.keySet())) { Player pl = Bukkit.getPlayer(id); if (pl != null) clearSpeedMod(pl); }
        speedMods.clear();
        charges.clear(); marks.clear(); markCasts.clear(); dashInputs.clear();
        cooldowns.clear(); if (current == this) current = null; will.clear(); atkBuff.clear(); fortress.clear(); wolfUntil.clear();
        fallImmune.clear(); lastGround.clear();
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
        if (fortress.getOrDefault(player.getUniqueId(), 0L) > now) { event.setCancelled(true); return; } // 불굴
        if (event.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL
            && fallImmune.getOrDefault(player.getUniqueId(), 0L) > now) { event.setCancelled(true); return; }
        if (will.getOrDefault(player.getUniqueId(), 0L) > now) event.setDamage(event.getDamage() * .9);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void move(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        Location from = event.getFrom(), to = event.getTo();
        if (to != null && fortress.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis()) {
            // 불굴: position frozen, looking around still allowed
            if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ())
                event.setTo(new Location(from.getWorld(), from.getX(), from.getY(), from.getZ(), to.getYaw(), to.getPitch()));
            return;
        }
        if (p.isOnGround()) lastGround.put(p.getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        UUID id = p.getUniqueId();
        lastGround.remove(id); fortress.remove(id); wolfUntil.remove(id); atkBuff.remove(id); will.remove(id); dashInputs.remove(id);
        clearSpeedMod(p);
    }

    @EventHandler
    public void died(org.bukkit.event.entity.PlayerDeathEvent event) {
        UUID id = event.getEntity().getUniqueId();
        fortress.remove(id); wolfUntil.remove(id); atkBuff.remove(id); will.remove(id);
        clearSpeedMod(event.getEntity());
    }

    private static final NamespacedKey SPEED_KEY = new NamespacedKey("pipilife", "breath_speed");

    /** Removes the 숲의 숨결 movement-speed modifier (persisted with the player, so also cleaned on join/quit). */
    private void clearSpeedMod(Player p) {
        speedMods.remove(p.getUniqueId());
        var attr = p.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (attr == null) return;
        for (var m : new ArrayList<>(attr.getModifiers())) if (SPEED_KEY.equals(m.getKey())) attr.removeModifier(m);
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
        int[] lo = job == null ? null : loadout(player, job);
        final int key = job == null ? slot : slot >= 1 && slot <= 3 ? gen(job, lo[slot - 1])
            : slot == 0 ? buffKey(job, lo[3]) : slot == 4 ? ultKey(job, lo[4]) : slot;
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
                    case 0 -> heal(player, job, key);
                    case 4 -> ultimate(player, job, key);
                    case 5 -> basic(player, job);
                    case 7 -> shield(player, job);
                    default -> skill(player, job, key);
                }
            };
            int windup = windupTicks(job, key);
            if (windup <= 0) effect.run();
            else {
                World castWorld = player.getWorld();
                prepare(player, job, slot, key, windup);
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

    private static int baseCooldown(Job job, int key) {
        return switch (key) {
            case 5 -> job == Job.MAGE ? 600 : 500;
            case 7 -> job == Job.WARRIOR ? 20_000 : job == Job.ARCHER ? 18_000 : 22_000;
            default -> key >= 8 && key < KEYS ? CDS[key] : 1_000;
        };
    }

    // ---------------------------------------------------------------- charges + combo marks

    private final Map<UUID, int[]> charges = new HashMap<>();
    private final Map<String, Long> marks = new HashMap<>(); // targetId|playerId|slot -> expiry millis
    private final Map<String, Long> markCasts = new HashMap<>(); // same key -> id of the cast that placed the mark
    private long castSeq;

    /** 2-charge skills: 전사 돌진 베기, 궁수 삼연사, 마법사 유도 마탄. */
    private static int maxCharges(Job job, int key) {
        return key == K_RUSH || key == K_TRIPLE || key == K_SEEK ? 2 : 1;
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
    private static int windupTicks(Job job, int key) {
        switch (key) {
            case 5: return job == Job.WARRIOR ? 5 : job == Job.MAGE ? 6 : 0;
            case 7: return 0;
            case K_HEAVY: return 20;
            case K_IAI: return 60;
            case K_SHOTGUN: return 40;
            case K_BOW: case K_M_CANNON: case K_M_DRAGON: return 60;
            default: break;
        }
        if (key >= 26 && key < 35) return 4; // buffs
        return switch (job) { case MAGE -> 7; case ARCHER -> 6; case WARRIOR -> 6; };
    }

    /** Wind-up visuals / restrictions that run from cast until the effect fires. */
    private void prepare(Player p, Job job, int slot, int key, int windup) {
        if (key == K_SPLIT || key == K_HEAVY) chargeFx(p, windup);
        if (job == Job.MAGE && slot != 0) staffCharge(p, windup);
        if (key == K_HEAVY) p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, windup + 2, 3, false, false));
        if (key == K_IAI || key == K_SHOTGUN) {
            boolean iai = key == K_IAI;
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, windup + 2, iai ? 255 : 4, false, false));
            World w = p.getWorld();
            w.playSound(p.getLocation(), iai ? Sound.ITEM_ARMOR_EQUIP_IRON : Sound.ENTITY_ARROW_SHOOT, .8f, iai ? 1.8f : .6f);
            run(2, windup / 2, i -> {
                if (!ready(p, w)) return true;
                Location eye = p.getEyeLocation();
                Vector d = eye.getDirection().normalize();
                double frac = i / (windup / 2.0);
                if (iai) { // sheath: a dark scabbard line at the hip, steel glints growing as the draw is held
                    Vector side = new Vector(-d.getZ(), 0, d.getX());
                    if (side.lengthSquared() > 1e-6) side.normalize();
                    Location hip = p.getLocation().add(0, 1, 0).add(side.clone().multiply(-.4));
                    for (double s = 0; s < .9; s += .15)
                        w.spawnParticle(Particle.DUST, hip.clone().add(d.clone().setY(0).multiply(s * .6)), 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(70, 60, 80), 1f));
                    if (i % 3 == 0) w.spawnParticle(Particle.END_ROD, hip.clone().add(d.clone().setY(0).multiply(.5)), 1 + (int) (frac * 3), .15, .1, .15, 0);
                    if (i % 10 == 0) w.playSound(hip, Sound.BLOCK_CHAIN_PLACE, .6f, (float) (1 + frac));
                } else { // bow drawn: converging motes in front of the bow
                    Location tip = eye.clone().add(d.clone().multiply(1.4)).add(0, -.3, 0);
                    double r = 1.0 * (1 - frac) + .1;
                    for (int k = 0; k < 4; k++) {
                        double a = i * .8 + k * Math.PI / 2;
                        Vector side = new Vector(-d.getZ(), 0, d.getX());
                        if (side.lengthSquared() > 1e-6) side.normalize();
                        w.spawnParticle(Particle.CRIT, tip.clone().add(side.clone().multiply(Math.cos(a) * r)).add(0, Math.sin(a) * r, 0), 1, 0, 0, 0, 0);
                    }
                    if (i % 4 == 0) w.playSound(tip, Sound.ENTITY_ARROW_HIT_PLAYER, .3f, (float) (.6 + frac));
                }
                return false;
            }, null);
        }
        if (key == K_BOW) manaBowFx(p, windup);
    }

    /** 마력 대궁 wind-up: a giant purple bow forms in front of the caster and is drawn back. */
    private void manaBowFx(Player p, int ticks) {
        World w = p.getWorld();
        Location eye0 = p.getEyeLocation();
        Vector d0 = eye0.getDirection().normalize();
        ItemDisplay bow = fx("mana_bow", eye0.clone().add(d0.clone().multiply(2.8)).add(0, -.2, 0), facing(d0, 0), vec(.4f), null, ticks + 6);
        run(1, ticks, i -> {
            if (!ready(p, w)) return true;
            Location eye = p.getEyeLocation();
            Vector d = eye.getDirection().normalize();
            moveFx(bow, eye.clone().add(d.clone().multiply(2.8)).add(0, -.2, 0));
            if (bow != null && bow.isValid()) tween(bow, 1, 1, new Vector3f(), facing(d, 0), vec((float) (.4 + 2.8 * Math.min(1, i / (double) (ticks - 2)))));
            return false;
        }, () -> kill(bow));
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
            case ARCHER -> { if (wolfUntil.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis()) wolfRun(p); else arrowShot(p, 1.0, null); }
            case MAGE -> mageOrb(p, 1.0);
        }
    }

    private void skill(Player p, Job job, int key) {
        switch (key) {
            case K_RUSH -> rush(p);
            case K_WHIRL -> whirl(p);
            case K_SPLIT -> split(p);
            case K_HEAVY -> heavy(p);
            case K_IMPALE -> impale(p);
            case K_IAI -> iai(p);
            case K_TRIPLE -> triple(p);
            case K_RAIN -> rain(p);
            case K_PIERCE -> pierce(p);
            case K_SPREAD -> spread(p);
            case K_SHOTGUN -> shotgun(p);
            case K_GALE -> gale(p);
            case K_SEEK -> seek(p);
            case K_FROST -> frost(p);
            case K_THUNDER -> thunder(p);
            case K_WAVE -> wave(p);
            case K_HAMMER -> hammer(p);
            case K_BOW -> manaBow(p);
            default -> { }
        }
    }

    private void heal(Player p, Job job, int key) {
        switch (key) {
            case K_W_HEAL -> resolve(p);
            case K_W_RAGE -> rage(p);
            case K_W_FORT -> fort(p);
            case K_A_HEAL -> breath(p);
            case K_A_FOCUS -> focus(p);
            case K_A_SPIRIT -> spirit(p);
            case K_M_HEAL -> light(p);
            case K_M_FOCUS -> manaFocus(p);
            case K_M_RESET -> timeReset(p, job);
            default -> { }
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

    private void ultimate(Player p, Job job, int key) {
        switch (key) {
            case K_W_ULT -> swordFall(p);
            case K_W_TIGER -> tiger(p);
            case K_A_ULT -> arrowStorm(p);
            case K_A_WOLF -> wolfSpirit(p);
            case K_M_CANNON -> cannon(p);
            case K_M_DRAGON -> dragon(p);
            default -> { }
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

    private void resolve(Player p) { // 투지
        healBy(p, .25);
        long ms = CombatStats.duration(p, 5_000);
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

    private void arrowShot(Player p, double coef, long[] mark) {
        Location eye = p.getEyeLocation();
        Vector adir = aimDir(p, eye, 20);
        List<LivingEntity> hits = beam(p, eye, adir, 20, .5, 1, Particle.CRIT, Color.WHITE);
        bigArrow(eye.clone().add(adir.clone().multiply(.9)).add(0, -.2, 0), adir, .3f, beamLen, 3);
        double dmg = power(p, Job.ARCHER) * coef;
        for (LivingEntity t : hits) { if (mark != null) chargeHit(p, t, dmg, (int) mark[0], mark[1]); else hit(p, t, dmg); }
    }

    private void triple(Player p) {
        World w = p.getWorld();
        final long[] mark = {K_TRIPLE, ++castSeq};
        run(6, 3, i -> {
            if (!ready(p, w)) return true;
            arrowShot(p, .9, mark);
            return false;
        }, null);
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

    private void breath(Player p) { // 숲의 숨결
        healBy(p, .25);
        long ms = CombatStats.duration(p, 6_000);
        clearSpeedMod(p);
        var attr = p.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (attr != null) {
            attr.addModifier(new org.bukkit.attribute.AttributeModifier(SPEED_KEY, .2, org.bukkit.attribute.AttributeModifier.Operation.ADD_SCALAR,
                org.bukkit.inventory.EquipmentSlotGroup.ANY));
            final long until = System.currentTimeMillis() + ms;
            speedMods.put(p.getUniqueId(), until);
            Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline() && speedMods.getOrDefault(p.getUniqueId(), 0L) == until) clearSpeedMod(p); }, ms / 50);
        }
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

    /** 뇌운: for 4 s, every 0.5 s lightning (visual only) strikes up to 3 nearby enemies within 8 for 60% attack each (8 hits). */
    private void thunder(Player p) {
        World w = p.getWorld();
        double dmg = power(p, Job.MAGE) * .6;
        run(10, 8, i -> {
            if (!ready(p, w)) return true;
            Location c = p.getLocation();
            List<LivingEntity> targets = near(p, c, 8);
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

    private void light(Player p) { // 치유의 빛
        World w = p.getWorld();
        healBy(p, .25);
        for (Player ally : w.getPlayers())
            if (ally != p && !ally.isDead() && ally.getGameMode() != GameMode.SPECTATOR
                && ally.getLocation().distanceSquared(p.getLocation()) <= 64) {
                healBy(ally, .10);
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

    // ---------------------------------------------------------------- reworked skills (6 general + 3 buff + 2 ultimate per class)

    private boolean atkBuffOn(Player p) { return atkBuff.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis(); }

    /** 공격력 버프: +20% to skill/basic damage dealt through hit() for 10 s, with a faint aura. */
    private void giveAtkBuff(Player p, Color color) {
        long ms = CombatStats.duration(p, 10_000);
        long until = System.currentTimeMillis() + ms;
        atkBuff.put(p.getUniqueId(), until);
        World w = p.getWorld();
        w.playSound(p.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, .6f, 1.6f);
        run(10, (int) Math.max(1, ms / 500), i -> {
            if (!p.isOnline() || p.isDead() || p.getWorld() != w || atkBuff.getOrDefault(p.getUniqueId(), 0L) != until) return true;
            Location c = p.getLocation();
            ring(c.clone().add(0, .15, 0), .8, 8, at -> w.spawnParticle(Particle.DUST, at.add(0, Math.random() * 1.6, 0), 1, 0, 0, 0, 0, new Particle.DustOptions(color, 1f)));
            return false;
        }, null);
    }

    /** Burst of rings + hearts used by the self-buffs. */
    private void buffBurst(Player p, Color color, Particle extra) {
        World w = p.getWorld();
        run(1, 5, i -> {
            if (!p.isOnline() || p.getWorld() != w) return true;
            double r = .6 + i * .3;
            ring(p.getLocation().add(0, .2 + i * .35, 0), r, 14, at -> {
                w.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, new Particle.DustOptions(color, 1.3f));
                if (extra != null) w.spawnParticle(extra, at, 1, 0, 0, 0, 0);
            });
            return false;
        }, null);
    }

    /** 3D cone test: target centre within {@code cos} of {@code dir} seen from {@code origin} (very close targets always count). */
    private boolean inCone(Location origin, Vector dir, LivingEntity t, double cos, boolean flat) {
        Vector rel = t.getBoundingBox().getCenter().subtract(origin.toVector());
        if (flat) rel.setY(0);
        if (rel.lengthSquared() < .64) return true;
        return dir.dot(rel.normalize()) >= cos;
    }

    private Vector flatLook(Player p) {
        Vector d = p.getEyeLocation().getDirection().setY(0);
        if (d.lengthSquared() < .01) d = p.getLocation().getDirection().setY(0);
        if (d.lengthSquared() < .01) return null;
        return d.normalize();
    }

    /** Single piercing-free arrow from {@code from}: first enemy in the way takes the damage. */
    private void volleyArrow(Player p, Location from, Vector dir, double range, double width, double dmg, float scale, int ticks) {
        double len = blockLen(p, from, dir, range);
        List<LivingEntity> hits = along(p, from, dir, len, width);
        if (!hits.isEmpty()) {
            LivingEntity t = hits.get(0);
            len = Math.min(len, t.getBoundingBox().getCenter().distance(from.toVector()) + .3);
            hit(p, t, dmg);
        }
        bigArrow(from, dir, scale, len, ticks);
    }

    // ---- warrior

    /** 일도양단: after a 1 s charge a huge 140 degree, 5 block swing (320%). */
    private void heavy(Player p) {
        p.removePotionEffect(PotionEffectType.SLOWNESS);
        Vector dir = flatLook(p);
        if (dir == null) return;
        World w = p.getWorld();
        Location base = p.getLocation();
        double dmg = power(p, Job.WARRIOR) * 3.2;
        Location at = base.clone().add(0, 1.2, 0).add(dir.clone().multiply(2.4));
        w.playSound(base, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.5f, .5f);
        w.playSound(base, Sound.ITEM_MACE_SMASH_GROUND, 1f, 1.1f);
        slash("slash_red", at, dir, 7.5f, -.35f, 1.1f);
        slash("slash_arc", at.clone().add(dir.clone().multiply(.4)), dir, 6.2f, -.35f, 1.1f);
        for (int k = 0; k < 3; k++) spikes(base.clone().add(dir.clone().multiply(1.8 + k * 1.3)).add(0, .05, 0), dir, 3f);
        for (double a = -70; a <= 70; a += 10) {
            Vector d = dir.clone().rotateAroundY(Math.toRadians(a));
            for (double r = 1.5; r <= 5; r += 1.25) {
                Location pt = base.clone().add(0, 1, 0).add(d.clone().multiply(r));
                w.spawnParticle(Particle.DUST, pt, 1, .05, .05, .05, 0, new Particle.DustOptions(Color.fromRGB(255, 70, 50), 1.5f));
                if (r > 4.5) w.spawnParticle(Particle.SWEEP_ATTACK, pt, 1, 0, 0, 0, 0);
            }
        }
        Location center = base.clone().add(0, 1, 0);
        double cos = Math.cos(Math.toRadians(70));
        for (LivingEntity t : near(p, center, 5)) if (inCone(center, dir, t, cos, true)) hit(p, t, dmg);
    }

    /** 대검 꽂기: giant sword stabs the ground in front (r 4): 120% at once, then 40% x4 every 0.5 s with slowness. */
    private void impale(Player p) {
        Vector dir = flatLook(p);
        if (dir == null) return;
        World w = p.getWorld();
        Location c = p.getLocation().add(dir.clone().multiply(3));
        double power = power(p, Job.WARRIOR);
        Location top = c.clone().add(0, 11, 0), low = c.clone().add(0, 3.4, 0);
        ItemDisplay sword = fx("giant_sword", top, new Quaternionf(), vec(2.4f), null, 80);
        w.playSound(c, Sound.ITEM_TRIDENT_THROW, 1f, .6f);
        run(1, 4, i -> {
            moveFx(sword, top.clone().add(low.clone().subtract(top).toVector().multiply((i + 1) / 4.0)));
            return !p.isOnline();
        }, () -> {
            w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 1f, .8f);
            w.playSound(c, Sound.ITEM_MACE_SMASH_GROUND, 1.2f, .8f);
            w.spawnParticle(Particle.BLOCK, c.clone().add(0, .2, 0), 50, 1.8, .2, 1.8, 0, c.clone().subtract(0, .5, 0).getBlock().getBlockData());
            for (int k = 0; k < 4; k++) spikes(c.clone().add(0, .05, 0), new Vector(Math.cos(k * Math.PI / 4), 0, Math.sin(k * Math.PI / 4)), 3.4f);
            run(1, 4, i -> {
                ring(c.clone().add(0, .2, 0), 1 + i, (int) ((1 + i) * 8), at -> w.spawnParticle(Particle.DUST, at, 1, 0, .1, 0, 0, new Particle.DustOptions(Color.fromRGB(110, 220, 255), 1.4f)));
                return false;
            }, null);
            run(10, 5, i -> {
                if (!ready(p, w)) return true;
                if (i > 0) {
                    ring(c.clone().add(0, .2, 0), 3.8, 30, at -> w.spawnParticle(Particle.DUST, at, 1, 0, .1, 0, 0, new Particle.DustOptions(Color.fromRGB(110, 220, 255), 1.1f)));
                    w.spawnParticle(Particle.CRIT, c.clone().add(0, .6, 0), 14, 2, .4, 2, .1);
                }
                for (LivingEntity t : near(p, c, 4)) {
                    hit(p, t, power * (i == 0 ? 1.2 : .4));
                    if (t.isValid()) t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 2, false, true));
                }
                return false;
            }, () -> kill(sword));
        });
    }

    /** 일섬: after the 3 s draw, dash 10 blocks in the facing direction in an instant and cut everything on the path (400%). */
    private void iai(Player p) {
        p.removePotionEffect(PotionEffectType.SLOWNESS);
        Vector dir = flatLook(p);
        if (dir == null) return;
        World w = p.getWorld();
        Location start = p.getLocation();
        double reach = 0;
        for (double s = .5; s <= 10; s += .5) {
            Location c = start.clone().add(dir.clone().multiply(s));
            if (!c.getBlock().isPassable() || !c.clone().add(0, 1, 0).getBlock().isPassable() || !w.getWorldBorder().isInside(c)) break;
            reach = s;
        }
        if (reach < .5) return;
        Location mid = start.clone().add(0, 1, 0);
        List<LivingEntity> hits = along(p, mid, dir, reach, 2.5);
        double dmg = power(p, Job.WARRIOR) * 4.0;
        for (double s = 0; s <= reach; s += .5) {
            Location c = mid.clone().add(dir.clone().multiply(s));
            w.spawnParticle(Particle.SWEEP_ATTACK, c, 1, .3, .4, .3, 0);
            w.spawnParticle(Particle.DUST, c, 4, .3, .5, .3, 0, new Particle.DustOptions(Color.fromRGB(235, 245, 255), 1.5f));
            w.spawnParticle(Particle.END_ROD, c, 1, .2, .4, .2, .02);
        }
        w.playSound(start, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.5f, .5f);
        w.playSound(start, Sound.ITEM_TRIDENT_RIPTIDE_3, 1f, 1.3f);
        Location dest = start.clone().add(dir.clone().multiply(reach));
        dest.setYaw(start.getYaw()); dest.setPitch(start.getPitch());
        fallImmune.put(p.getUniqueId(), System.currentTimeMillis() + 2_000);
        p.teleport(dest);
        p.setVelocity(new Vector(0, 0, 0));
        for (LivingEntity t : hits) {
            hit(p, t, dmg);
            w.spawnParticle(Particle.CRIT, t.getBoundingBox().getCenter().toLocation(w), 16, .3, .4, .3, .3);
        }
        final double len = reach;
        run(2, 3, i -> { // afterimage slashes along the path
            for (int k = 0; k < 3; k++) {
                Location at = mid.clone().add(dir.clone().multiply(len * (k + .5) / 3));
                slashQuick(k == 1 ? "slash_red" : "slash_arc", at, dir, 5.5f, i % 2 == 0 ? .6f : -.6f, .9f);
            }
            return false;
        }, null);
    }

    /** 맹호참: a spectral tiger sweeps its claws three times (300% each, 8 block 160 degree cone). */
    private void tiger(Player p) {
        Vector dir = flatLook(p);
        if (dir == null) return;
        World w = p.getWorld();
        Location base = p.getLocation();
        Location at = base.clone().add(0, 1.6, 0).add(dir.clone().multiply(3));
        ItemDisplay beast = fx("tiger_claw", at, facing(dir, 0), vec(.8f), null, 60);
        tween(beast, 1, 4, new Vector3f(), facing(dir, 0), vec(4.6f));
        w.playSound(base, Sound.ENTITY_RAVAGER_ROAR, 1.2f, .9f);
        w.playSound(base, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1f, .6f);
        double dmg = power(p, Job.WARRIOR) * 3.0;
        double cos = Math.cos(Math.toRadians(80));
        Bukkit.getScheduler().runTaskLater(plugin, () -> run(6, 3, i -> {
            if (!ready(p, w)) return true;
            Location o = p.getLocation().add(0, 1, 0);
            float scale = i == 2 ? 9f : 6.5f;
            float roll = i == 0 ? .7f : i == 1 ? -.7f : 0f;
            Vector d = flatLook(p);
            if (d == null) d = dir;
            Location pt = o.clone().add(d.clone().multiply(3.2)).add(0, .3, 0);
            slash("slash_red", pt, d, scale, roll, 1.2f);
            slash("slash_arc", pt, d, scale * .85f, -roll, 1.2f);
            w.playSound(o, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, .6f + i * .2f);
            w.playSound(o, Sound.ENTITY_CAT_HISS, .8f, .5f);
            for (double a = -80; a <= 80; a += 16) {
                Vector dd = d.clone().rotateAroundY(Math.toRadians(a));
                for (double r = 2; r <= 8; r += 2)
                    w.spawnParticle(i == 2 ? Particle.CRIT : Particle.SWEEP_ATTACK, o.clone().add(dd.multiply(r)), 1, .1, .1, .1, 0);
            }
            for (LivingEntity t : near(p, o, 8)) if (inCone(o, d, t, cos, true)) hit(p, t, dmg);
            return false;
        }, () -> kill(beast)), 4);
    }

    private void rage(Player p) { // 전투 함성
        healBy(p, .25);
        giveAtkBuff(p, Color.fromRGB(255, 70, 50));
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_RAVAGER_ROAR, .9f, 1.3f);
        buffBurst(p, Color.fromRGB(255, 70, 50), Particle.CRIT);
    }

    /** 불굴: 10 s of complete damage immunity while frozen in place (looking around is still allowed). */
    private void fort(Player p) {
        long ms = CombatStats.duration(p, 10_000);
        long until = System.currentTimeMillis() + ms;
        fortress.put(p.getUniqueId(), until);
        World w = p.getWorld();
        w.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, .8f, .6f);
        w.playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, .5f, 1.4f);
        p.setVelocity(new Vector(0, 0, 0));
        ItemDisplay[] gems = new ItemDisplay[4];
        for (int g = 0; g < 4; g++) gems[g] = fx("crystal", p.getLocation(), new Quaternionf(), vec(.45f), null, (int) Math.min(260, ms / 50 + 6));
        run(2, (int) Math.max(1, ms / 100), i -> {
            if (!p.isOnline() || p.isDead() || p.getWorld() != w || fortress.getOrDefault(p.getUniqueId(), 0L) != until) return true;
            Location c = p.getLocation();
            for (int g = 0; g < 4; g++) {
                double a = i * .3 + g * Math.PI / 2;
                moveFx(gems[g], c.clone().add(Math.cos(a) * 1.2, 1 + Math.sin(i * .2 + g) * .25, Math.sin(a) * 1.2));
            }
            ring(c.clone().add(0, .1, 0), 1.1, 12, at -> w.spawnParticle(Particle.DUST, at.add(0, Math.random() * 2, 0), 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(255, 220, 90), 1.1f)));
            return false;
        }, () -> {
            for (ItemDisplay g : gems) kill(g);
            if (p.isOnline() && fortress.getOrDefault(p.getUniqueId(), 0L) == until) fortress.remove(p.getUniqueId());
        });
    }

    // ---- archer

    /** 폭죽 화살: a fast firework arrow (260%) piercing 24 blocks and bursting where it ends / hits. */
    private void pierce(Player p) {
        Location eye = p.getEyeLocation();
        Vector dir = aimDir(p, eye, 24);
        List<LivingEntity> hits = beam(p, eye, dir, 24, .8, 0, Particle.FIREWORK, Color.fromRGB(255, 190, 80));
        double len = beamLen;
        bigArrow(eye.clone().add(dir.clone().multiply(.9)).add(0, -.2, 0), dir, .5f, len, 2);
        double dmg = power(p, Job.ARCHER) * 2.6;
        World w = p.getWorld();
        for (LivingEntity t : hits) {
            hit(p, t, dmg);
            firework(w, t.getBoundingBox().getCenter().toLocation(w));
        }
        firework(w, eye.clone().add(dir.clone().multiply(len)));
    }

    private void firework(World w, Location at) {
        w.spawnParticle(Particle.FIREWORK, at, 28, .3, .3, .3, .2);
        Color[] cols = {Color.fromRGB(255, 80, 80), Color.fromRGB(255, 220, 80), Color.fromRGB(90, 230, 120), Color.fromRGB(90, 200, 255), Color.fromRGB(255, 120, 220)};
        for (Color c : cols) w.spawnParticle(Particle.DUST, at, 6, .6, .6, .6, 0, new Particle.DustOptions(c, 1.4f));
        w.playSound(at, Sound.ENTITY_FIREWORK_ROCKET_BLAST, .8f, 1.2f);
    }

    /** 부채살: three arrows at once in a +-12 degree fan (100% each). */
    private void spread(Player p) {
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector center = aimDir(p, eye, 20);
        double dmg = power(p, Job.ARCHER);
        for (int k = -1; k <= 1; k++) {
            Vector d = center.clone().rotateAroundY(Math.toRadians(12 * k));
            List<LivingEntity> hits = beam(p, eye, d, 20, .5, 1, Particle.CRIT, Color.WHITE);
            bigArrow(eye.clone().add(d.clone().multiply(.9)).add(0, -.2, 0), d, .3f, beamLen, 3);
            for (LivingEntity t : hits) hit(p, t, dmg);
        }
        w.playSound(eye, Sound.ENTITY_ARROW_SHOOT, 1f, 1.2f);
    }

    /** 산탄 사격: after a 2 s draw, 20 arrows (25% each) in a +-18 degree cone and a ~5 block recoil. */
    private void shotgun(Player p) {
        p.removePotionEffect(PotionEffectType.SLOWNESS);
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector center = aimDir(p, eye, 18);
        double dmg = power(p, Job.ARCHER) * .25;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Location from = eye.clone().add(center.clone().multiply(.9)).add(0, -.2, 0);
        for (int n = 0; n < 20; n++) {
            double ang = Math.toRadians(18) * Math.sqrt(rnd.nextDouble()), phi = rnd.nextDouble() * Math.PI * 2;
            Vector d = center.clone().rotateAroundY(Math.cos(phi) * ang);
            Vector right = d.clone().crossProduct(new Vector(0, 1, 0));
            if (right.lengthSquared() > 1e-6) d.rotateAroundAxis(right.normalize(), Math.sin(phi) * ang);
            d.normalize();
            double len = blockLen(p, from, d, 18);
            List<LivingEntity> hits = along(p, from, d, len, .6);
            if (!hits.isEmpty()) {
                LivingEntity t = hits.get(0);
                len = Math.min(len, t.getBoundingBox().getCenter().distance(from.toVector()) + .3);
                hit(p, t, dmg);
            }
            if (n % 2 == 0) bigArrow(from, d, .26f, len, 3);
            for (double s = 1; s <= len; s += 2) w.spawnParticle(Particle.CRIT, from.clone().add(d.clone().multiply(s)), 1, 0, 0, 0, 0);
        }
        w.playSound(eye, Sound.ENTITY_GENERIC_EXPLODE, .6f, 1.8f);
        w.playSound(eye, Sound.ENTITY_ARROW_SHOOT, 1.2f, .7f);
        w.spawnParticle(Particle.CLOUD, from, 12, .3, .3, .3, .08);
        Vector back = p.getEyeLocation().getDirection().setY(0);
        if (back.lengthSquared() < .01) return;
        back.normalize().multiply(-1);
        if (!w.getWorldBorder().isInside(p.getLocation().add(back.clone().multiply(3)))) return;
        fallImmune.put(p.getUniqueId(), System.currentTimeMillis() + 2_000);
        p.setVelocity(back.multiply(.85).setY(.42));
    }

    /** 질풍 화살: a huge wind-wrapped arrow (380%) piercing 20 blocks. */
    private void gale(Player p) {
        Location eye = p.getEyeLocation();
        Vector dir = aimDir(p, eye, 20);
        List<LivingEntity> hits = beam(p, eye, dir, 20, 1.1, 0, Particle.CLOUD, Color.fromRGB(150, 240, 255));
        Location pstart = eye.clone().add(dir.clone().multiply(.9)).add(0, -.2, 0);
        bigArrow(pstart, dir, .9f, beamLen, 4);
        ItemDisplay wind = fx("wind_spiral", pstart, facing(dir, 0), vec(1.0f), null, 16);
        tween(wind, 1, 4, new Vector3f(), facing(dir, 2.4f), vec(1.3f));
        if (wind != null) flyFx(wind, eye.clone().add(dir.clone().multiply(beamLen * .85)), 4);
        p.getWorld().playSound(eye, Sound.ENTITY_BREEZE_SHOOT, 1f, 1.1f);
        double dmg = power(p, Job.ARCHER) * 3.8;
        for (LivingEntity t : hits) {
            hit(p, t, dmg);
            t.getWorld().spawnParticle(Particle.CLOUD, t.getBoundingBox().getCenter().toLocation(t.getWorld()), 10, .3, .3, .3, .15);
        }
    }

    /** 매의 눈. */
    private void focus(Player p) {
        healBy(p, .25);
        giveAtkBuff(p, Color.fromRGB(255, 230, 90));
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_EVOKER_PREPARE_SUMMON, .7f, 1.8f);
        buffBurst(p, Color.fromRGB(255, 230, 90), Particle.END_ROD);
    }

    /** 바람 정령: a wind bow floats beside the archer for 10 s and shoots the nearest enemy (60%) every second. */
    private void spirit(Player p) {
        long ms = CombatStats.duration(p, 10_000);
        World w = p.getWorld();
        w.playSound(p.getLocation(), Sound.ENTITY_BREEZE_IDLE_GROUND, 1f, 1.2f);
        ItemDisplay bow = fx("wind_bow", p.getLocation().add(0, 2, 0), facing(p.getEyeLocation().getDirection(), 0), vec(1.5f), null, (int) Math.min(260, ms / 50 + 6));
        double dmg = power(p, Job.ARCHER) * .6;
        run(2, (int) Math.max(1, ms / 100), i -> {
            if (!ready(p, w)) return true;
            Vector look = p.getEyeLocation().getDirection().normalize();
            Vector side = new Vector(-look.getZ(), 0, look.getX());
            if (side.lengthSquared() < 1e-6) side = new Vector(1, 0, 0); else side.normalize();
            Location pos = p.getLocation().add(0, 2.0 + Math.sin(i * .2) * .12, 0).add(side.multiply(-.95));
            moveFx(bow, pos);
            tween(bow, 1, 2, new Vector3f(), facing(look, 0), vec(1.5f));
            w.spawnParticle(Particle.CLOUD, pos, 1, .15, .15, .15, .01);
            w.spawnParticle(Particle.DUST, pos, 1, .2, .2, .2, 0, new Particle.DustOptions(Color.fromRGB(160, 240, 210), 1f));
            if (i % 10 == 9) {
                List<LivingEntity> targets = near(p, p.getLocation(), 14);
                targets.sort(Comparator.comparingDouble(t -> t.getLocation().distanceSquared(p.getLocation())));
                if (!targets.isEmpty()) {
                    LivingEntity t = targets.get(0);
                    Vector to = t.getBoundingBox().getCenter().subtract(pos.toVector());
                    double dist = to.length();
                    if (dist > .1) {
                        bigArrow(pos, to.clone().normalize(), .3f, dist, 3);
                        w.playSound(pos, Sound.ENTITY_ARROW_SHOOT, .6f, 1.7f);
                        hit(p, t, dmg);
                    }
                }
            }
            return false;
        }, () -> kill(bow));
    }

    /** 화살 폭풍: five wind bows hover behind the archer and fire 2 arrows every 0.25 s for 5 s (25% each, 40 arrows). */
    private void arrowStorm(Player p) {
        World w = p.getWorld();
        double dmg = power(p, Job.ARCHER) * .25;
        ItemDisplay[] bows = new ItemDisplay[5];
        for (int k = 0; k < 5; k++) {
            bows[k] = fx("wind_bow", p.getLocation().add(0, 2, 0), new Quaternionf(), vec(1.8f), null, 120);
            if (bows[k] != null) bows[k].setTeleportDuration(4);
        }
        w.playSound(p.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1.2f, .9f);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        run(5, 20, i -> {
            if (!ready(p, w)) return true;
            Location eye = p.getEyeLocation();
            Vector look = eye.getDirection().normalize();
            Vector back = look.clone().setY(0);
            if (back.lengthSquared() < .01) back = new Vector(0, 0, 1);
            back.normalize().multiply(-1);
            Location[] at = new Location[5];
            for (int k = 0; k < 5; k++) {
                Vector off = back.clone().rotateAroundY(Math.toRadians((k - 2) * 38)).multiply(2.0);
                at[k] = p.getLocation().add(0, 1.9 + (k == 2 ? .35 : (k % 2 == 0 ? .1 : -.1)), 0).add(off);
                moveFx(bows[k], at[k]);
                if (bows[k] != null && bows[k].isValid()) tween(bows[k], 1, 3, new Vector3f(), facing(look, 0), vec(1.8f));
                w.spawnParticle(Particle.CLOUD, at[k], 1, .1, .1, .1, .01);
            }
            for (int n = 0; n < 2; n++) {
                Location from = at[rnd.nextInt(5)];
                Vector d = look.clone().rotateAroundY(Math.toRadians(rnd.nextDouble(-3, 3)));
                volleyArrow(p, from, d, 25, .6, dmg, .28f, 3);
            }
            w.playSound(eye, Sound.ENTITY_ARROW_SHOOT, .5f, 1.5f + rnd.nextFloat() * .3f);
            return false;
        }, () -> { for (ItemDisplay b : bows) kill(b); });
    }

    /** 늑대 정령: for 8 s the basic attack is a wolf spirit that runs 18 blocks and pierces (150%). */
    private void wolfSpirit(Player p) {
        long ms = 8_000;
        long until = System.currentTimeMillis() + ms;
        wolfUntil.put(p.getUniqueId(), until);
        World w = p.getWorld();
        w.playSound(p.getLocation(), Sound.ENTITY_WOLF_HOWL, 1.5f, 1f);
        run(5, (int) (ms / 250), i -> {
            if (!p.isOnline() || p.isDead() || p.getWorld() != w || wolfUntil.getOrDefault(p.getUniqueId(), 0L) != until) return true;
            Location c = p.getLocation();
            ring(c.clone().add(0, .2 + (i % 8) * .22, 0), .9, 8, at -> w.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(140, 215, 255), 1.1f)));
            return false;
        }, () -> wolfUntil.remove(p.getUniqueId(), until));
    }

    /** Wolf-spirit basic attack. */
    private void wolfRun(Player p) {
        Location eye = p.getEyeLocation();
        Vector dir = aimDir(p, eye, 18);
        List<LivingEntity> hits = beam(p, eye, dir, 18, 1.0, 0, Particle.CLOUD, Color.fromRGB(150, 220, 255));
        double len = beamLen;
        Location from = eye.clone().add(dir.clone().multiply(1.2)).add(0, -.9, 0);
        ItemDisplay wolf = fx("spirit_wolf", from, facing(dir, 0), vec(1.6f), null, 12);
        if (wolf != null) flyFx(wolf, from.clone().add(dir.clone().multiply(len)), 6);
        p.getWorld().playSound(eye, Sound.ENTITY_WOLF_GROWL, .8f, 1.4f);
        double dmg = power(p, Job.ARCHER) * 1.5;
        for (LivingEntity t : hits) {
            hit(p, t, dmg);
            t.getWorld().spawnParticle(Particle.SWEEP_ATTACK, t.getBoundingBox().getCenter().toLocation(t.getWorld()), 1, 0, 0, 0, 0);
        }
    }

    // ---- mage

    /** 마력 파동: a wide (3 block) upright purple wave rolls 14 blocks forward, piercing (200%). */
    private void wave(Player p) {
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Vector side = new Vector(-dir.getZ(), 0, dir.getX());
        if (side.lengthSquared() < 1e-6) side = new Vector(1, 0, 0); else side.normalize();
        Vector up = side.clone().crossProduct(dir).normalize();
        Vector sd = side, ud = up;
        Location start = eye.clone().add(0, -.4, 0);
        double dmg = power(p, Job.MAGE) * 2.0;
        Quaternionf q = facing(dir, 0).rotateX((float) (Math.PI / 2));
        ItemDisplay ring = fx("whirl_ring", start.clone().add(dir.clone().multiply(1.2)), q, vec(3f), null, 24);
        Set<LivingEntity> done = new HashSet<>();
        Color purple = Color.fromRGB(170, 70, 235);
        w.playSound(eye, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 1.2f);
        run(1, 10, i -> {
            if (!p.isOnline()) return true;
            Location c = start.clone().add(dir.clone().multiply(1.2 + (i + 1) * 1.3));
            if (!c.getBlock().isPassable()) return true;
            moveFx(ring, c);
            for (int k = 0; k < 18; k++) {
                double a = k * Math.PI * 2 / 18;
                Location at = c.clone().add(sd.clone().multiply(Math.cos(a) * 1.5)).add(ud.clone().multiply(Math.sin(a) * 1.5));
                w.spawnParticle(Particle.DUST, at, 1, .03, .03, .03, 0, new Particle.DustOptions(purple, 1.5f));
                if (k % 3 == 0) w.spawnParticle(Particle.WITCH, at, 1, 0, 0, 0, 0);
            }
            for (LivingEntity t : near(p, c, 2)) if (done.add(t)) hit(p, t, dmg);
            return false;
        }, () -> kill(ring));
    }

    /** 마력 망치: a giant mana hammer smashes down on the aimed point (max 16): 380% in r 3.5, 1 s stun, shockwave. */
    private void hammer(Player p) {
        Location c = aimPoint(p, 16);
        World w = c.getWorld();
        Vector h = flatLook(p);
        if (h == null) h = new Vector(0, 0, 1);
        Vector heading = h;
        float yaw = (float) Math.atan2(h.getX(), h.getZ());
        Location startAt = c.clone().add(0, 3.4, 0).subtract(h.clone().multiply(1.6));
        Vector travel = c.clone().add(0, 1.0, 0).add(h.clone().multiply(.6)).subtract(startAt).toVector();
        ItemDisplay hammer = fx("mana_hammer", startAt, new Quaternionf().rotateY(yaw).rotateX(-.2f), vec(1f), null, 40);
        tween(hammer, 1, 5, new Vector3f(), new Quaternionf().rotateY(yaw).rotateX(-.9f), vec(4.4f));
        tween(hammer, 8, 5, new Vector3f((float) travel.getX(), (float) travel.getY(), (float) travel.getZ()), new Quaternionf().rotateY(yaw).rotateX(1.5f), vec(4.4f));
        w.playSound(c, Sound.BLOCK_BEACON_ACTIVATE, 1f, .6f);
        Color purple = Color.fromRGB(170, 70, 235);
        double dmg = power(p, Job.MAGE) * 3.8;
        run(1, 14, i -> {
            if (!p.isOnline()) return true;
            if (i < 12) {
                w.spawnParticle(Particle.DUST, startAt.clone().add(travel.clone().multiply(Math.max(0, (i - 8) / 5.0))), 6, .8, .8, .8, 0, new Particle.DustOptions(purple, 1.6f));
                return false;
            }
            return true;
        }, () -> {
            w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, .7f);
            w.playSound(c, Sound.ITEM_MACE_SMASH_GROUND, 1.2f, .7f);
            w.spawnParticle(Particle.EXPLOSION, c, 6, 1.5, .2, 1.5, 0);
            w.spawnParticle(Particle.DUST, c.clone().add(0, .4, 0), 50, 1.8, .4, 1.8, 0, new Particle.DustOptions(purple, 1.9f));
            w.spawnParticle(Particle.WITCH, c.clone().add(0, .6, 0), 30, 1.8, .5, 1.8, .05);
            for (int k = 0; k < 4; k++) spikes(c.clone().add(0, .05, 0), new Vector(Math.cos(k * Math.PI / 4), 0, Math.sin(k * Math.PI / 4)), 3.2f);
            run(1, 4, j -> {
                ring(c.clone().add(0, .2, 0), 1 + j, (int) ((1 + j) * 8), at -> w.spawnParticle(Particle.DUST, at, 1, 0, .1, 0, 0, new Particle.DustOptions(purple, 1.5f)));
                return false;
            }, null);
            for (LivingEntity t : near(p, c, 3.5)) {
                hit(p, t, dmg);
                if (t.isValid()) t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 255, false, true));
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> kill(hammer), 6);
        });
    }

    /** 마력 대궁: after the 3 s draw (see manaBowFx), a huge mana arrow pierces 30 blocks (550%). */
    private void manaBow(Player p) {
        Location eye = p.getEyeLocation();
        Vector dir = aimDir(p, eye, 30);
        List<LivingEntity> hits = beam(p, eye, dir, 30, 1.2, 0, Particle.WITCH, Color.fromRGB(176, 110, 255));
        double len = beamLen;
        bigArrow(eye.clone().add(dir.clone().multiply(1.2)).add(0, -.2, 0), dir, 1.5f, len, 4);
        World w = p.getWorld();
        w.playSound(eye, Sound.ENTITY_ENDER_DRAGON_SHOOT, 1f, 1.5f);
        w.playSound(eye, Sound.ENTITY_ARROW_SHOOT, 1.2f, .5f);
        double dmg = power(p, Job.MAGE) * 5.5;
        for (LivingEntity t : hits) {
            hit(p, t, dmg);
            w.spawnParticle(Particle.DUST, t.getBoundingBox().getCenter().toLocation(w), 20, .4, .5, .4, 0, new Particle.DustOptions(Color.fromRGB(176, 110, 255), 1.8f));
        }
    }

    /** 마력 집중. */
    private void manaFocus(Player p) {
        healBy(p, .35);
        giveAtkBuff(p, Color.fromRGB(176, 110, 255));
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.5f);
        buffBurst(p, Color.fromRGB(176, 110, 255), Particle.ENCHANT);
    }

    /** 시간 역행: resets the equipped general skills + shield (cooldowns and charges) and grants the attack buff. */
    private void timeReset(Player p, Job job) {
        UUID id = p.getUniqueId();
        long[] cd = cooldowns.get(id);
        int[] c = charges.get(id);
        int[] lo = loadout(p, job);
        for (int slot = 1; slot <= 3; slot++) {
            int key = gen(job, lo[slot - 1]), max = maxCharges(job, key);
            if (cd != null) cd[key] = 0;
            if (c != null) c[key] = -1; // refillCharges restores a full stack
            p.sendMessage("스킬 " + slot + " 재사용 대기 · 0ms" + (max > 1 ? " · 충전 " + max + "/" + max : ""));
        }
        if (cd != null) cd[7] = 0;
        p.sendMessage("보호막 재사용 대기 · 0ms");
        giveAtkBuff(p, Color.fromRGB(120, 220, 255));
        World w = p.getWorld();
        w.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1.8f);
        w.playSound(p.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, .8f, 1.6f);
        run(1, 16, i -> {
            if (!p.isOnline() || p.getWorld() != w) return true;
            Location o = p.getLocation().add(0, .1 + i * .12, 0);
            for (int k = 0; k < 3; k++) {
                double a = -i * .55 + k * Math.PI * 2 / 3, r = 1.3 - i * .05;
                w.spawnParticle(Particle.REVERSE_PORTAL, o.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r), 1, 0, 0, 0, 0);
                w.spawnParticle(Particle.DUST, o.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r), 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(120, 220, 255), 1.2f));
            }
            return false;
        }, null);
    }

    private Location staffTip(Player p) {
        Location eye = p.getEyeLocation();
        Vector d = eye.getDirection().normalize();
        Vector side = new Vector(-d.getZ(), 0, d.getX());
        if (side.lengthSquared() < 1e-6) side = new Vector(1, 0, 0); else side.normalize();
        return eye.clone().add(d.clone().multiply(1.6)).add(side.multiply(-.25)).add(0, -.35, 0);
    }

    /** 마력포: after the 3 s charge a thick beam (20 blocks, follows the aim) pulses 50% every 0.25 s for 5 s. */
    private void cannon(Player p) {
        World w = p.getWorld();
        double dmg = power(p, Job.MAGE) * .5;
        Color purple = Color.fromRGB(176, 90, 255);
        w.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, .8f, 1.6f);
        run(1, 100, i -> {
            if (!ready(p, w)) return true;
            Location tip = staffTip(p);
            Vector dir = p.getEyeLocation().getDirection().normalize();
            double len = blockLen(p, tip, dir, 20);
            for (double s = 0; s <= len; s += .6) {
                Location at = tip.clone().add(dir.clone().multiply(s));
                w.spawnParticle(Particle.DUST, at, 2, .18, .18, .18, 0, new Particle.DustOptions(purple, 2f));
                if (((int) (s / .6)) % 3 == 0) w.spawnParticle(Particle.END_ROD, at, 1, .05, .05, .05, 0);
            }
            w.spawnParticle(Particle.DUST, tip, 6, .3, .3, .3, 0, new Particle.DustOptions(Color.fromRGB(240, 220, 255), 2.4f));
            if (i % 10 == 0) w.playSound(tip, Sound.BLOCK_BEACON_AMBIENT, 1f, 1.8f);
            if (i % 5 == 0) {
                for (LivingEntity t : along(p, tip, dir, len, 1.5)) {
                    hit(p, t, dmg);
                    w.spawnParticle(Particle.DUST, t.getBoundingBox().getCenter().toLocation(w), 8, .3, .4, .3, 0, new Particle.DustOptions(purple, 1.6f));
                }
            }
            return false;
        }, null);
    }

    /** 용의 숨결: after the 3 s charge a dragon head appears and breathes fire in a 10 block 60 degree cone for 5 s (45% / 0.25 s + burning). */
    private void dragon(Player p) {
        World w = p.getWorld();
        double dmg = power(p, Job.MAGE) * .45;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Location eye0 = p.getEyeLocation();
        Vector d0 = eye0.getDirection().normalize();
        ItemDisplay head = fx("dragon_head", eye0.clone().add(d0.clone().multiply(2.6)).add(0, -.3, 0), facing(d0, 0), vec(3f), null, 110);
        if (head != null) head.setTeleportDuration(2);
        w.playSound(eye0, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.2f, 1.1f);
        double cos = Math.cos(Math.toRadians(30));
        run(1, 100, i -> {
            if (!ready(p, w)) return true;
            Location eye = p.getEyeLocation();
            Vector dir = eye.getDirection().normalize();
            Location mouth = eye.clone().add(dir.clone().multiply(2.6)).add(0, -.3, 0);
            moveFx(head, mouth);
            if (head != null && head.isValid()) tween(head, 1, 1, new Vector3f(), facing(dir, 0), vec(3f));
            for (int n = 0; n < 8; n++) {
                Vector d = dir.clone().rotateAroundY(Math.toRadians(rnd.nextDouble(-30, 30)));
                Vector right = d.clone().crossProduct(new Vector(0, 1, 0));
                if (right.lengthSquared() > 1e-6) d.rotateAroundAxis(right.normalize(), Math.toRadians(rnd.nextDouble(-20, 20)));
                Location at = mouth.clone().add(d.multiply(1 + rnd.nextDouble() * 9));
                w.spawnParticle(n % 3 == 0 ? Particle.DRAGON_BREATH : Particle.FLAME, at, 1, .1, .1, .1, .01);
            }
            if (i % 10 == 0) w.playSound(mouth, Sound.ENTITY_BLAZE_SHOOT, .8f, .6f);
            if (i % 5 == 0) {
                for (LivingEntity t : near(p, eye, 10)) {
                    if (!inCone(eye, dir, t, cos, false)) continue;
                    hit(p, t, dmg);
                    if (t.isValid()) t.setFireTicks(40);
                }
            }
            return false;
        }, () -> kill(head));
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
        if (atkBuffOn(p)) damage *= 1.2;
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
        track(d, Math.max(10, Math.min(life, 260)));
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
