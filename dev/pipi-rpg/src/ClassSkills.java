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
import java.util.function.Function;
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
    private final Map<UUID, Long> spiritUntil = new HashMap<>();  // 바람 정령: running instance per player (own state, never shared with 화살 폭풍)
    private final Map<UUID, Long> rooted = new HashMap<>();      // 백호참 자세: frozen in place (looking allowed) until
    private final Map<UUID, Long> speedMods = new HashMap<>();     // 숲의 숨결 speed modifier: player -> expiry millis
    private final Map<UUID, Long> fallImmune = new HashMap<>();  // dash: no fall damage until
    private final Map<UUID, Long> lastGround = new HashMap<>();
    private final Map<UUID, Long> phoenixUntil = new HashMap<>();  // 피닉스의 강림: fire-arrow basic attacks until

    private final Set<Display> displays = new HashSet<>();
    private boolean flip;
    private double beamLen = 20;
    private enum Job { WARRIOR, ARCHER, MAGE }

    // ---------------------------------------------------------------- loadout (equipped general skills)

    // Cooldown / charge array layout: 0-7 fixed slots, 8-25 general (6 per class), 26-34 buffs (3 per class), 35-40 ultimates (2 per class),
    // 41-47 boss skills (unlocked by the first warden clear). Key per class / ordinal comes from GEN_KEYS / BUFF_KEYS / ULT_KEYS.
    private static final int KEYS = 48;
    /** General (equippable in keys 1-3) skill ids per class; the index is the skill's ordinal used for cooldown keys. */
    private static final String[][] IDS = {
        {"w_rush", "w_whirl", "w_split", "w_heavy", "w_impale", "w_iai", "w_thrust"},
        {"a_triple", "a_rain", "a_pierce", "a_spread", "a_shotgun", "a_gale"},
        {"m_seek", "m_frost", "m_thunder", "m_wave", "m_hammer", "m_bow", "m_mrain", "m_spear"}};
    /** Buff ids per class (key 4, slot 0). */
    private static final String[][] BUFFS = {
        {"w_heal", "w_rage", "w_fort"}, {"a_heal", "a_focus", "a_spirit", "a_tiger", "a_turtle"}, {"m_heal", "m_focus", "m_reset"}};
    /** Ultimate ids per class (key 5, slot 4). m_moon (만월의 추억) replaced m_dragon; 청룡의 진노 moved to the archer as a boss skill. */
    private static final String[][] ULTS = {{"w_ult", "w_tiger", "w_meteor"}, {"a_ult", "a_wolf", "a_dragon"}, {"m_cannon", "m_moon"}};
    /** Boss skills: equippable only after the player's first warden clear. */
    static final java.util.Set<String> BOSS_SKILLS = java.util.Set.of("w_thrust", "w_meteor", "m_mrain", "m_spear", "a_dragon", "a_tiger", "a_turtle");
    private static final int[][] GEN_KEYS = {{8, 9, 10, 11, 12, 13, 41}, {14, 15, 16, 17, 18, 19}, {20, 21, 22, 23, 24, 25, 42, 43}};
    private static final int[][] BUFF_KEYS = {{26, 27, 28}, {29, 30, 31, 44, 45}, {32, 33, 34}};
    private static final int[][] ULT_KEYS = {{35, 36, 46}, {37, 38, 47}, {39, 40}};
    private static final char[] CLS = {'w', 'a', 'm'};

    // skill keys (cooldown / charge / mark index)
    private static final int K_RUSH = 8, K_WHIRL = 9, K_SPLIT = 10, K_HEAVY = 11, K_IMPALE = 12, K_IAI = 13;
    private static final int K_TRIPLE = 14, K_RAIN = 15, K_PIERCE = 16, K_SPREAD = 17, K_SHOTGUN = 18, K_GALE = 19;
    private static final int K_SEEK = 20, K_FROST = 21, K_THUNDER = 22, K_WAVE = 23, K_HAMMER = 24, K_BOW = 25;
    private static final int K_W_HEAL = 26, K_W_RAGE = 27, K_W_FORT = 28, K_A_HEAL = 29, K_A_FOCUS = 30, K_A_SPIRIT = 31;
    private static final int K_M_HEAL = 32, K_M_FOCUS = 33, K_M_RESET = 34;
    private static final int K_W_ULT = 35, K_W_TIGER = 36, K_A_ULT = 37, K_A_WOLF = 38, K_M_CANNON = 39, K_M_DRAGON = 40; // K_M_DRAGON = 만월의 추억 (m_moon)
    private static final int K_W_THRUST = 41, K_M_MRAIN = 42, K_M_SPEAR = 43, K_A_TIGER = 44, K_A_TURTLE = 45, K_W_METEOR = 46, K_A_DRAGON = 47;

    /** Base cooldowns (ms) per key; fixed slots (5 basic, 7 shield) are handled in baseCooldown. */
    private static final int[] CDS = new int[KEYS];
    static {
        int[] g = {4_000, 5_000, 6_000, 8_000, 9_000, 5_000,   3_000, 7_000, 6_000, 4_000, 12_000, 9_000,   3_000, 5_000, 9_000, 5_000, 10_000, 14_000};
        System.arraycopy(g, 0, CDS, 8, g.length);
        int[] b = {25_000, 30_000, 45_000,   25_000, 30_000, 45_000,   30_000, 30_000, 90_000};
        System.arraycopy(b, 0, CDS, 26, b.length);
        int[] u = {90_000, 90_000, 90_000, 90_000, 100_000, 100_000};
        System.arraycopy(u, 0, CDS, 35, u.length);
        int[] boss = {7_000, 8_000, 9_000, 40_000, 60_000, 100_000, 100_000}; // 광속 찌르기, 마력비, 마력창, 백호 강림, 현무의 가오, 유성우, 청룡의 진노
        System.arraycopy(boss, 0, CDS, 41, boss.length);
    }

    private static ClassSkills current;
    /** Per player, per class: [g1, g2, g3, buff, ult] ordinals (general ordinals index IDS, then BUFFS, then ULTS). */
    private final Map<UUID, int[][]> loadouts = new HashMap<>();
    private final java.io.File loadoutFile;
    private final org.bukkit.configuration.file.YamlConfiguration loadoutData;

    private static int gen(Job job, int ord) { return GEN_KEYS[job.ordinal()][ord]; }
    private static int buffKey(Job job, int ord) { return BUFF_KEYS[job.ordinal()][ord]; }
    private static int ultKey(Job job, int ord) { return ULT_KEYS[job.ordinal()][ord]; }
    private boolean bossUnlocked(Player p) { return plugin.bossCleared(p.getUniqueId(), "warden"); }

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
        for (int k = 0; k < parts.length; k++) if (parts[k].trim().equals("m_dragon")) parts[k] = "m_moon"; // old save
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
        if (!bossUnlocked(p)) for (int k = 3; k < args.length; k++) if (BOSS_SKILLS.contains(args[k])) { p.sendMessage("§c보스 스킬이에요. 워든을 처치하면 해금돼요."); return true; }
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
        p.sendMessage("PIPI_BOSSSKILL:" + (bossUnlocked(p) ? 1 : 0));
        Job job = combatJob(p);
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
        cooldowns.clear(); if (current == this) current = null; will.clear(); atkBuff.clear(); fortress.clear(); rooted.clear();
        fallImmune.clear(); lastGround.clear(); phoenixUntil.clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void shortcut(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking() || combatJob(player) == null) return;
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
        if (to != null && (fortress.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis()
            || rooted.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis())) {
            // 불굴 / 백호참 자세: position frozen, looking around still allowed
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
        lastGround.remove(id); phoenixUntil.remove(id); tigerUntil.remove(id); phantomLast.remove(id); kill(phantoms.remove(id)); fortress.remove(id); rooted.remove(id); atkBuff.remove(id); will.remove(id); dashInputs.remove(id); kill(shells.remove(id));
        clearSpeedMod(p);
    }

    @EventHandler
    public void died(org.bukkit.event.entity.PlayerDeathEvent event) {
        UUID id = event.getEntity().getUniqueId();
        fortress.remove(id); rooted.remove(id); spiritUntil.remove(id); atkBuff.remove(id); will.remove(id); phoenixUntil.remove(id); kill(shells.remove(id));
        clearSpeedMod(event.getEntity());
    }

    @EventHandler
    public void worldChanged(org.bukkit.event.player.PlayerChangedWorldEvent event) {
        phoenixUntil.remove(event.getPlayer().getUniqueId()); rooted.remove(event.getPlayer().getUniqueId()); kill(shells.remove(event.getPlayer().getUniqueId()));
    }

    private static final NamespacedKey SPEED_KEY = new NamespacedKey("pipilife", "breath_speed");

    /** Removes the 숲의 숨결 movement-speed modifier (persisted with the player, so also cleaned on join/quit). */
    private void clearSpeedMod(Player p) {
        speedMods.remove(p.getUniqueId());
        var attr = p.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (attr == null) return;
        for (var m : new ArrayList<>(attr.getModifiers())) if (SPEED_KEY.equals(m.getKey())) attr.removeModifier(m);
    }

    /** Job of the main-hand item, only when it is the marked Armory weapon in combat mode. */
    private Job combatJob(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        return Armory.isCombatWeapon(p, hand) ? weaponType(hand) : null;
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
        if (player.getScoreboardTags().contains(RaidDeepDark.GHOST)) return true; // raid spectator: no skills
        if (!combatWorld(player)) {
            player.sendMessage("튜토리얼 밖의 월드에서 서바이벌 또는 모험 모드로 사용하세요.");
            return true;
        }
        Job job = combatJob(player);
        if (job == null && slot != 6) return true; // 전투 모드의 장착 무기가 아니면 스킬은 조용히 무시
        // cooldowns, charges and marks are keyed by skill (key), not by the key-slot the skill currently sits in
        int[] lo = job == null ? null : loadout(player, job);
        final int key = job == null ? slot : slot >= 1 && slot <= 3 ? gen(job, lo[slot - 1])
            : slot == 0 ? buffKey(job, lo[3]) : slot == 4 ? ultKey(job, lo[4]) : slot;
        long[] cd = cooldowns.computeIfAbsent(player.getUniqueId(), ignored -> new long[KEYS]);
        long now = System.currentTimeMillis();
        if (rooted.getOrDefault(player.getUniqueId(), 0L) > now && (slot == 6 || key == K_RUSH)) return true; // 백호참 자세 중 이동기 불가
        long remaining = cd[key] - now;
        int maxCharges = job == null ? 1 : maxCharges(job, key);
        int[] charge = null;
        if (maxCharges > 1) {
            charge = refillCharges(player, job, key, maxCharges, cd, now);
            if (charge[key] <= 0) {
                // on cooldown: the skill bar already shows the timer, no chat notice
                return true;
            }
        } else if (remaining > 0) {
            // on cooldown: no chat notice
            return true;
        }
        if (slot == 5) player.swingMainHand();
        else if (slot != 6) pipiAnim(player, job, slot);
        boolean fired = true;
        if (slot == 6) {
            fired = dash(player, now);
        } else {
            Runnable effect = () -> {
                switch (slot) {
                    case 0 -> heal(player, job, key);
                    case 4 -> { phantomAct(player, job); ultimate(player, job, key); }
                    case 5 -> basic(player, job);
                    case 7 -> shield(player, job);
                    default -> { phantomAct(player, job); skill(player, job, key); }
                }
            };
            int windup = windupTicks(job, key);
            if (windup <= 0) effect.run();
            else {
                World castWorld = player.getWorld();
                prepare(player, job, slot, key, windup);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (ready(player, castWorld)) effect.run();
                    else if (player.isOnline() && (key == K_IAI || key == K_SHOTGUN || key == K_HEAVY)) player.removePotionEffect(PotionEffectType.SLOWNESS); // cancelled wind-up
                }, windup);
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

    /** 2-charge skills: 전사 삼연 비검, 궁수 삼연사, 마법사 유도 마탄. */
    private static int maxCharges(Job job, int key) {
        return key == K_SPLIT || key == K_TRIPLE || key == K_SEEK ? 2 : 1;
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
    private void chargeHit(Player p, LivingEntity target, double damage, int slot, long cast) { chargeHit(p, target, damage, slot, cast, 1); }

    /** Splits one hit into {@code n} quick hits (same total) so every tick shows its own damage number. */
    private void burst(Player p, LivingEntity target, double damage, int n) {
        if (n <= 1) { hit(p, target, damage); return; }
        double each = damage / n;
        hit(p, target, each);
        for (int k = 1; k < n; k++) Bukkit.getScheduler().runTaskLater(plugin, () -> hit(p, target, each), (long) k * 2);
    }

    private void chargeHit(Player p, LivingEntity target, double damage, int slot, long cast, int n) {
        String key = target.getUniqueId() + "|" + p.getUniqueId() + "|" + slot;
        long now = System.currentTimeMillis();
        Long until = marks.get(key);
        Long markCast = markCasts.get(key);
        World w = target.getWorld();
        if (until != null && until > now) {
            if (markCast != null && markCast == cast) { burst(p, target, damage, n); return; } // same cast: mark stays
            marks.remove(key);
            markCasts.remove(key);
            burst(p, target, damage * 1.5, n);
            w.spawnParticle(Particle.CRIT, target.getBoundingBox().getCenter().toLocation(w), 14, .3, .4, .3, .25);
            snd(w, target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, .8f, 1.3f);
            return;
        }
        burst(p, target, damage, n);
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
            case 5: return job == Job.WARRIOR ? 5 : job == Job.MAGE ? 8 : 0; // mage: gathers energy on the phantom staff, then shoots
            case 7: return 0;
            case K_SHOTGUN: return 20;
            case K_BOW: return 30;
            case K_W_METEOR: return 20;
            case K_M_DRAGON: case K_A_DRAGON: return 20;
            case K_M_CANNON: return 60;
            case K_THUNDER: return 12;
            case K_W_TIGER: return 0; // 백호참: the 2 s stance is handled inside tiger()
            default: break;
        }
        if (key >= 26 && key < 35 || key == K_A_TIGER || key == K_A_TURTLE) return 4; // buffs
        return switch (job) { case MAGE -> 7; case ARCHER -> 6; case WARRIOR -> 6; };
    }

    /** Sound at the start of the wind-up. */
    private void castCue(Player p, Job job, int slot, int key) {
        World w = p.getWorld();
        Location at = p.getLocation();
        if (slot == 5) {
            if (job == Job.MAGE) snd(w, at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, .5f, 1.5f);
            return;
        }
        if (slot == 0) { snd(w, at, Sound.BLOCK_BEACON_POWER_SELECT, .5f, 1.4f); return; }
        switch (key) {
            case K_RUSH -> snd(w, at, Sound.ITEM_ARMOR_EQUIP_IRON, .5f, 1.3f);
            case K_WHIRL -> snd(w, at, Sound.ITEM_TRIDENT_RIPTIDE_1, .5f, 1.2f);
            case K_SPLIT -> snd(w, at, Sound.ITEM_ARMOR_EQUIP_IRON, .6f, .8f);
            case K_HEAVY -> snd(w, at, Sound.BLOCK_CHAIN_PLACE, .7f, .6f);
            case K_IMPALE -> snd(w, at, Sound.ITEM_ARMOR_EQUIP_IRON, .5f, .7f);
            case K_TRIPLE -> snd(w, at, Sound.ITEM_CROSSBOW_LOADING_START, .6f, 1.2f);
            case K_RAIN -> snd(w, at, Sound.ITEM_CROSSBOW_LOADING_END, .6f, 1.1f);
            case K_PIERCE -> snd(w, at, Sound.ITEM_CROSSBOW_LOADING_START, .6f, .8f);
            case K_SPREAD -> snd(w, at, Sound.ENTITY_GENERIC_SWIM, .5f, 1.4f);
            case K_GALE -> snd(w, at, Sound.ENTITY_BREEZE_INHALE, .7f, 1.2f);
            case K_SEEK -> snd(w, at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, .5f, 1.5f); // same as the mage basic attack
            case K_FROST -> snd(w, at, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, .5f, 1.8f);
            case K_THUNDER -> snd(w, at, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, .6f, 1.4f);
            case K_WAVE -> snd(w, at, Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, .5f, 1.3f);
            case K_HAMMER -> snd(w, at, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, .6f, .9f);
            case K_BOW -> snd(w, at, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, .6f, 1.2f);
            case K_W_ULT -> snd(w, at, Sound.ITEM_TRIDENT_RIPTIDE_1, .6f, .9f);
            case K_W_TIGER -> snd(w, at, Sound.ENTITY_POLAR_BEAR_WARNING, .8f, .8f);
            case K_A_ULT -> snd(w, at, Sound.ENTITY_BREEZE_CHARGE, .7f, 1.2f);
            case K_A_WOLF -> snd(w, at, Sound.ENTITY_PHANTOM_FLAP, .8f, .8f);
            case K_M_CANNON -> snd(w, at, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, .8f, 1f);
            case K_M_DRAGON -> snd(w, at, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, .8f, .7f);
            case K_A_DRAGON -> snd(w, at, Sound.ENTITY_ENDER_DRAGON_GROWL, .35f, 1.6f);
            case K_W_METEOR -> snd(w, at, Sound.ENTITY_WITHER_SPAWN, .3f, 1.8f);
            case K_W_THRUST -> snd(w, at, Sound.ITEM_TRIDENT_RIPTIDE_2, .6f, 1.6f);
            case K_M_MRAIN, K_M_SPEAR -> snd(w, at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, .7f, .8f);
            default -> { }
        }
    }

    /** Wind-up visuals / restrictions that run from cast until the effect fires. */
    private void prepare(Player p, Job job, int slot, int key, int windup) {
        castCue(p, job, slot, key);
        if (key == K_M_CANNON) cannonChargeFx(p, windup);
        if (key == K_THUNDER) thunderChargeFx(p, windup);
        if ((job == Job.MAGE || job == Job.ARCHER) && slot != 0 && slot != 7) phantomHold(p, job, windup);
        if (job == Job.MAGE && slot != 0) staffCharge(p, windup, key == K_BOW ? 1 : key == K_M_DRAGON ? 2 : 0);
        if (key == K_IAI || key == K_SHOTGUN) {
            boolean iai = key == K_IAI;
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, windup + 2, iai ? 2 : 4, false, false)); // iai: Slowness III (movable but slow)
            World w = p.getWorld();
            snd(w, p.getLocation(), iai ? Sound.ITEM_ARMOR_EQUIP_IRON : Sound.ENTITY_ARROW_SHOOT, .8f, iai ? 1.8f : .6f);
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
                    if (i % 10 == 0) snd(w, hip, Sound.BLOCK_CHAIN_PLACE, .6f, (float) (1 + frac));
                } else { // bow drawn: converging motes in front of the bow
                    Location tip = eye.clone().add(d.clone().multiply(1.4)).add(0, -.3, 0);
                    double r = 1.0 * (1 - frac) + .1;
                    for (int k = 0; k < 4; k++) {
                        double a = i * .8 + k * Math.PI / 2;
                        Vector side = new Vector(-d.getZ(), 0, d.getX());
                        if (side.lengthSquared() > 1e-6) side.normalize();
                        w.spawnParticle(Particle.CRIT, tip.clone().add(side.clone().multiply(Math.cos(a) * r)).add(0, Math.sin(a) * r, 0), 1, 0, 0, 0, 0);
                    }
                    if (i % 4 == 0) snd(w, tip, Sound.ENTITY_ARROW_HIT_PLAYER, .3f, (float) (.6 + frac));
                }
                return false;
            }, null);
        }
        if (key == K_BOW) manaBowFx(p, windup);
    }

    /** Bow display position: low, to the right and well in front so the view stays clear. */
    private static Location bowSpot(Location eye, Vector d) {
        Vector side = new Vector(-d.getZ(), 0, d.getX());
        if (side.lengthSquared() < 1e-6) side = new Vector(1, 0, 0); else side.normalize();
        return eye.clone().add(d.clone().multiply(3.6)).add(side.multiply(1.5)).add(0, -1.1, 0);
    }

    /** 마력 대궁 wind-up: a giant purple bow forms in front of the caster and is drawn back. */
    private void manaBowFx(Player p, int ticks) {
        World w = p.getWorld();
        Location eye0 = p.getEyeLocation();
        Vector d0 = eye0.getDirection().normalize();
        ItemDisplay bow = fx("mana_bow", bowSpot(eye0, d0), facing(d0, 0), vec(.25f), null, ticks + 6);
        run(1, ticks, i -> {
            if (!ready(p, w)) return true;
            Location eye = p.getEyeLocation();
            Vector d = eye.getDirection().normalize();
            moveFx(bow, bowSpot(eye, d));
            if (bow != null && bow.isValid()) tween(bow, 1, 1, new Vector3f(), facing(d, 0), vec((float) (.25 + 1.15 * Math.min(1, i / (double) Math.max(1, ticks - 2)))));
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
    /**
     * Mage wind-up (every mage cast that has one, basic attack included): mana gathers into the phantom staff's orb while a magic
     * circle (fx_mana_circle) is drawn in front of it - a bright point traces the rim, the circle grows and spins faster - and at
     * the cast it flashes and fades. mode is kept for the callers (old: 1 bow, 2 dragon); everything now starts at the staff.
     */
    private void staffCharge(Player p, int ticks, int mode) {
        World w = p.getWorld();
        Vector d0 = p.getEyeLocation().getDirection().normalize();
        float size = ticks >= 20 ? .95f : .6f;
        ItemDisplay circle = fx("mana_circle", muzzle(p, Job.MAGE).add(d0.clone().multiply(.35)), facing(d0, 0), vec(.05f), null, ticks + 8);
        run(1, ticks, i -> {
            if (!ready(p, w)) { kill(circle); return true; }
            Vector d = p.getEyeLocation().getDirection().normalize();
            Location tip = muzzle(p, Job.MAGE), c = tip.clone().add(d.clone().multiply(.35));
            Vector side = new Vector(-d.getZ(), 0, d.getX());
            if (side.lengthSquared() < 1e-6) side = new Vector(1, 0, 0); else side.normalize();
            Vector up = side.clone().crossProduct(d).normalize();
            double frac = (i + 1) / (double) ticks;
            // the circle is drawn: a pen point runs around the rim, leaving a trail, while the circle model grows in
            float sc = (float) (size * Math.min(1, frac * 1.15));
            poseFx(circle, c, facing(d, (float) (i * (.25 + frac * .5))), vec(Math.max(.05f, sc)), 1);
            double rim = sc * 1.15, pen = frac * Math.PI * 2;
            for (int k = 0; k < 4; k++) {
                double a = pen - k * .25;
                w.spawnParticle(Particle.DUST, c.clone().add(side.clone().multiply(Math.cos(a) * rim)).add(up.clone().multiply(Math.sin(a) * rim)), 1, 0, 0, 0, 0,
                    new Particle.DustOptions(k == 0 ? Color.fromRGB(245, 225, 255) : Color.fromRGB(176, 110, 255), k == 0 ? 1.2f : .8f));
            }
            // motes pulled into the orb
            double r = .9 * (1 - frac) + .1;
            for (int k = 0; k < 2; k++) {
                double a = i * .9 + k * Math.PI;
                w.spawnParticle(Particle.DUST, tip.clone().add(side.clone().multiply(Math.cos(a) * r)).add(up.clone().multiply(Math.sin(a) * r)), 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(190, 130, 255), .9f));
            }
            if (i % 4 == 0) snd(w, tip, Sound.BLOCK_AMETHYST_BLOCK_CHIME, .4f, (float) (.8 + frac * 1.1));
            if (i == ticks - 1) { // cast: the circle flashes and fades away
                tween(circle, 1, 2, new Vector3f(), facing(d, (float) (i * .8)), vec(sc * 1.25f));
                tween(circle, 4, 3, new Vector3f(), facing(d, (float) (i * .8 + 1)), vec(.05f));
            }
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
            case ARCHER -> {
                if (phoenixOn(p)) fireArrow(p); else phantomArrow(p);
                if (tigerUntil.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis()) tigerExtra(p);
            }
            case MAGE -> phantomOrb(p);
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
            case K_W_THRUST -> thrust(p);
            case K_M_MRAIN -> manaRain(p);
            case K_M_SPEAR -> manaSpear(p);
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
            case K_A_TIGER -> whiteTiger(p);
            case K_A_TURTLE -> blackTortoise(p);
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
        snd(w, p.getLocation(), job == Job.MAGE ? Sound.BLOCK_AMETHYST_BLOCK_RESONATE : job == Job.ARCHER ? Sound.ENTITY_BREEZE_WIND_BURST : Sound.ITEM_ARMOR_EQUIP_IRON, .8f, job == Job.ARCHER ? 1.4f : 1f);
        if (job == Job.WARRIOR) snd(w, p.getLocation(), Sound.ITEM_SHIELD_BLOCK, .6f, 1.1f);
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
                snd(w, c, Sound.ITEM_SHIELD_BREAK, 1f, job == Job.MAGE ? 1.4f : 1f);
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
        String[] banner = switch (key) { // PIPI_ULT banner (PipiTravel UltBanner): colour, name
            case K_W_ULT -> new String[]{"ff4a3a", "검기 난사"};
            case K_W_TIGER -> new String[]{"e8f0ff", "백호참"};
            case K_W_METEOR -> new String[]{"ffc83a", "유성우"};
            case K_A_ULT -> new String[]{"79ff57", "화살 폭풍"};
            case K_A_WOLF -> new String[]{"ff8a2a", "피닉스의 현현"};
            case K_A_DRAGON -> new String[]{"4ab4ff", "청룡의 진노"};
            case K_M_CANNON -> new String[]{"b57cff", "마력포"};
            case K_M_DRAGON -> new String[]{"cfe6ff", "만월의 추억"};
            default -> null;
        };
        if (banner != null) p.sendMessage("PIPI_ULT:" + banner[0] + ":" + banner[1]);
        switch (key) {
            case K_W_ULT -> swordFall(p);
            case K_W_TIGER -> tiger(p);
            case K_A_ULT -> arrowStorm(p);
            case K_A_WOLF -> phoenix(p);
            case K_M_CANNON -> cannon(p);
            case K_M_DRAGON -> moon(p);
            case K_A_DRAGON -> blueThunder(p);
            case K_W_METEOR -> meteorShower(p);
            default -> { }
        }
    }

    // ---------------------------------------------------------------- warrior

    private void warriorBasic(Player p) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        World w = p.getWorld();
        Vector side = new Vector(-dir.getZ(), 0, dir.getX());
        if (side.lengthSquared() > 0) side.normalize();
        // 분신 무기: a glowing red copy of the sword appears in front of the warrior and does the swing
        Location far = eye.clone().add(dir.clone().multiply(5.4)).add(0, -.25, 0); // at the edge of the 6-block reach, out of the way
        final boolean first = flip;
        phantomAct(p, Job.WARRIOR, first);
        lineSlash(far, dir, side, first);
        Bukkit.getScheduler().runTaskLater(plugin, () -> { // second hit: the sword cuts back the other way
            if (!p.isOnline() || p.isDead()) return;
            phantomAct(p, Job.WARRIOR, !first);
            lineSlash(far, dir, side, !first);
            snd(p.getWorld(), far, Sound.ENTITY_PLAYER_ATTACK_SWEEP, .5f, 1.3f);
        }, 5);
        flip = !flip;
        snd(w, eye, Sound.ENTITY_PLAYER_ATTACK_SWEEP, .7f, flip ? 1.1f : 1f);
        double dmg = power(p, Job.WARRIOR) * BASIC_WARRIOR;
        for (LivingEntity target : near(p, eye, 6)) { // reach 6 blocks
            Vector rel = target.getBoundingBox().getCenter().subtract(eye.toVector());
            if (rel.lengthSquared() > 0 && dir.dot(rel.clone().normalize()) >= .35) burst(p, target, dmg, 2); // 55% x 2
        }
    }

    private void rush(Player p) {
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
        snd(w, origin, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, .7f);
        snd(w, origin, Sound.ITEM_TRIDENT_RIPTIDE_2, .6f, 1.2f);
        phantomAct(p, Job.WARRIOR, true);
        lineSlash(origin.clone().add(0, 1.2, 0).add(heading.clone().multiply(5)), heading, new Vector(-heading.getZ(), 0, heading.getX()), true);
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
                if (done.add(t)) burst(p, t, dmg, 3);
            }
            Location c = now.clone().add(0, 1, 0);
            w.spawnParticle(Particle.DUST, c, 3, .25, .35, .25, 0, new Particle.DustOptions(Color.fromRGB(220, 30, 30), .8f));
            w.spawnParticle(Particle.CRIT, c, 2, .25, .35, .25, .05);
            if (i % 2 == 0) w.spawnParticle(Particle.CLOUD, now.clone().add(0, .2, 0), 1, .2, .1, .2, .02);
            if (i % 2 == 0) w.spawnParticle(Particle.SWEEP_ATTACK, c.clone().add(heading.clone().multiply(1.2)), 1, 0, 0, 0, 0);
            return false;
        }, () -> { if (p.isOnline()) p.setVelocity(p.getVelocity().multiply(.3)); });
    }

    private void whirl(Player p) {
        World w = p.getWorld();
        double dmg = power(p, Job.WARRIOR) * 1.6;
        snd(w, p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, .9f, .9f);
        snd(w, p.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, .5f, 1.4f);
        for (LivingEntity target : near(p, p.getLocation().add(0, 1, 0), 6)) burst(p, target, dmg, 4);
        ItemDisplay ring = fx("whirl_ring", p.getLocation().add(0, 1, 0), new Quaternionf(), new Vector3f(3.2f, .35f, 3.2f), null, 14);
        float[] sizes = {4.6f, 5.4f, 5.6f}; // out at the 6-block edge of the spin, not hugging the player
        for (int s = 1; s <= 3; s++)
            tween(ring, s * 3 - 2, 3, new Vector3f(), new Quaternionf().rotateY((float) Math.toRadians(120 * s)), new Vector3f(sizes[s - 1], sizes[s - 1] * .3f, sizes[s - 1]));
        tween(ring, 10, 3, new Vector3f(), new Quaternionf().rotateY((float) Math.toRadians(420)), new Vector3f(.3f, .05f, .3f));
        run(1, 8, i -> {
            Location c = p.getLocation().add(0, 1, 0);
            moveFx(ring, c);
            for (int k = 0; k < 6; k++) {
                double a = i * .8 + k * Math.PI / 3;
                Location at = c.clone().add(Math.cos(a) * 5.2, Math.sin(i) * .3 - .2, Math.sin(a) * 5.2);
                w.spawnParticle(k % 3 == 0 ? Particle.SWEEP_ATTACK : Particle.CRIT, at, 1, 0, 0, 0, 0);
            }
            return !p.isOnline();
        }, null);
    }

    /**
     * 삼연 비검 (w_split, longsword): three thin red sword-aura crescents fly out 4 ticks apart (upright, tilted left / straight /
     * right), each piercing 14.4 blocks for 75%; the phantom sword cuts with each one. 2 charges; consecutive hits keep the combo bonus.
     */
    private void split(Player p) {
        World w = p.getWorld();
        final long cast = ++castSeq;
        double dmg = power(p, Job.WARRIOR) * .75;
        snd(w, p.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, .5f, 1.5f);
        float[] rolls = {1.15f, 1.57f, 2.0f};
        run(4, 3, n -> {
            if (!ready(p, w)) return true;
            Vector heading = flatLook(p);
            if (heading == null) return true;
            phantomAct(p, Job.WARRIOR, n % 2 == 0);
            flyingSlash(p, heading, rolls[n], dmg, cast);
            snd(w, p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.15f + n * .15f);
            return false;
        }, null);
    }

    /** One thin flying sword-aura crescent (1.2 blocks / tick, 12 ticks = 14.4 blocks), stops at walls, hits each target once. */
    private void flyingSlash(Player p, Vector heading, float roll, double dmg, long cast) {
        World w = p.getWorld();
        Location start = p.getLocation().add(0, 1.2, 0);
        Set<LivingEntity> done = new HashSet<>();
        ItemDisplay red = fx("slash_red", start.clone().add(heading.clone().multiply(1.6)), facing(heading, roll), new Vector3f(2.2f, .16f, 1.5f), null, 13); // long thin crescent
        run(1, 12, i -> {
            Location c = start.clone().add(heading.clone().multiply((i + 1) * 14.4 / 12 + .4));
            if (!c.getBlock().isPassable()) return true;
            moveFx(red, c);
            w.spawnParticle(Particle.DUST, c, 2, .35, .35, .35, 0, new Particle.DustOptions(Color.fromRGB(255, 80, 70), .7f));
            if (i % 2 == 0) w.spawnParticle(Particle.CRIT, c, 2, .3, .3, .3, .05);
            for (LivingEntity target : near(p, c, 2.0)) if (done.add(target)) chargeHit(p, target, dmg, K_SPLIT, cast, 1);
            return !p.isOnline();
        }, () -> kill(red));
    }

    private void resolve(Player p) { // 투지
        healBy(p, .25);
        long ms = CombatStats.duration(p, 5_000);
        will.put(p.getUniqueId(), System.currentTimeMillis() + ms);
        World w = p.getWorld();
        snd(w, p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, .7f, 1.2f);
        snd(w, p.getLocation(), Sound.ITEM_TOTEM_USE, .3f, 1.6f);
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

    /** 검기 난사: 5 s of rapid sword-aura slashes around the player (radius 6), 20 pulses x 40% = 800% total. */
    private void swordFall(Player p) {
        World w = p.getWorld();
        double dmg = power(p, Job.WARRIOR) * .4;
        snd(w, p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, .8f);
        run(5, 20, i -> {
            if (!p.isOnline() || p.isDead()) return true;
            Location c = p.getLocation().add(0, 1, 0);
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            // big flat crimson crescent sweeping around the warrior (fx_ult_crescent, radius ~6), alternating heights and tilts
            float yaw = rnd.nextFloat() * 6.283f, tilt = (i % 3 - 1) * .22f;
            Location sweepAt = p.getLocation().add(0, new double[]{.3, .8, 1.3}[i % 3], 0);
            crescentSweep(sweepAt, yaw, tilt, i % 2 == 0 ? 1f : -1f, 4.2f);
            for (int n = 0; n < 6; n++) { // embers flung off the rim
                double a = yaw + (n - 2.5) * .35;
                Location rim = sweepAt.clone().add(Math.sin(a) * 5.6, 0, Math.cos(a) * 5.6);
                w.spawnParticle(Particle.DUST, rim, 1, .15, .15, .15, 0, new Particle.DustOptions(n % 2 == 0 ? Color.fromRGB(255, 214, 140) : Color.fromRGB(255, 70, 40), 1.1f));
            }
            for (int k = 0; k < 1; k++) {
                double a = rnd.nextDouble() * Math.PI * 2, r = 1.7 + rnd.nextDouble() * 4.0;
                Vector d = new Vector(Math.cos(a), 0, Math.sin(a));
                Location at = c.clone().add(d.clone().multiply(r)).add(0, rnd.nextDouble() - .5, 0);
                slashQuick(k == 0 ? "slash_red" : "slash_arc", at, new Vector(-d.getZ(), 0, d.getX()), 1.2f + rnd.nextFloat() * .5f, (rnd.nextFloat() - .5f) * 1.6f, .8f);
                w.spawnParticle(Particle.SWEEP_ATTACK, at, 1, 0, 0, 0, 0);
                w.spawnParticle(Particle.CRIT, at, 2, .3, .2, .3, .05);
            }
            snd(w, c, Sound.ENTITY_PLAYER_ATTACK_SWEEP, .6f, .9f + rnd.nextFloat() * .4f);
            for (LivingEntity t : near(p, c, 6)) hit(p, t, dmg);
            return false;
        }, null);
    }

    // ---------------------------------------------------------------- archer

    private void arrowShot(Player p, double coef, long[] mark) {
        Location eye = p.getEyeLocation();
        Vector adir = aimDir(p, eye, 25);
        List<LivingEntity> hits = beam(p, eye, adir, 25, .5, 1, Particle.CRIT, Color.WHITE);
        bowArrow(p, eye, adir, beamLen, .3f, 4);
        snd(p.getWorld(), eye, Sound.ENTITY_ARROW_SHOOT, .7f, 1.2f);
        double dmg = power(p, Job.ARCHER) * coef;
        for (LivingEntity t : hits) { if (mark != null) chargeHit(p, t, dmg, (int) mark[0], mark[1]); else burst(p, t, dmg, 2); } // basic: 45% x 2
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
        Location c = aimPoint(p, 25);
        World w = c.getWorld();
        double dmg = power(p, Job.ARCHER) * .5;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        run(10, 6, i -> {
            if (!p.isOnline()) return true;
            w.spawnParticle(Particle.CLOUD, c.clone().add(0, 9, 0), 14, 2, .3, 2, .01);
            snd(w, c, Sound.ITEM_CROSSBOW_SHOOT, .5f, 1.2f);
            snd(w, c, Sound.ENTITY_ARROW_HIT, .4f, .9f);
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
        snd(w, p.getLocation(), Sound.BLOCK_GRASS_BREAK, .8f, 1.2f);
        snd(w, p.getLocation(), Sound.ITEM_TOTEM_USE, .3f, 1.8f);
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
        snd(p.getWorld(), eye, Sound.BLOCK_AMETHYST_BLOCK_CHIME, .6f, 1.6f);
        snd(p.getWorld(), eye, Sound.ENTITY_EVOKER_CAST_SPELL, .3f, 1.7f);
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
                snd(w, p.getEyeLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, .6f, 1.6f); // mage basic launch sounds
                snd(w, p.getEyeLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, .3f, 1.7f);
                orb(p, muzzle(p, Job.MAGE), start, .9, 24, dmg, target, mark, .6f);
            };
            if (delay == 0) launch.run(); else Bukkit.getScheduler().runTaskLater(plugin, launch, delay);
        }
    }

    /** Rotation taking the giant sword model's tip (-Y) onto dir. */
    private static Quaternionf tipTo(Vector dir) {
        Vector n = dir.clone();
        if (n.lengthSquared() < 1e-6) n = new Vector(0, -1, 0); else n.normalize();
        return new Quaternionf().rotationTo(new Vector3f(0, -1, 0), new Vector3f((float) n.getX(), (float) n.getY(), (float) n.getZ()));
    }

    private void orientTip(ItemDisplay d, Vector dir, float scale) {
        if (d == null || !d.isValid()) return;
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(1);
        d.setTransformation(new Transformation(new Vector3f(), tipTo(dir), vec(scale), new Quaternionf()));
    }

    /** 거검진: three giant swords materialize over the caster's head, hover ~6 ticks, then each homes onto an enemy (60% x 3 hits). */
    private void frost(Player p) {
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector look = eye.getDirection().normalize();
        List<LivingEntity> cands = new ArrayList<>();
        for (LivingEntity t : near(p, p.getLocation(), 20)) if (aimable(p, t)) cands.add(t);
        cands.sort(Comparator.comparingDouble(t -> {
            Vector rel = t.getBoundingBox().getCenter().subtract(eye.toVector());
            double d = rel.length();
            return d < 1e-6 ? 0 : d - 12 * look.dot(rel.clone().normalize());
        }));
        List<LivingEntity> targets = new ArrayList<>(cands.subList(0, Math.min(3, cands.size())));
        Location aimed = targets.isEmpty() ? targetPoint(p, 20) : null;
        double dmg = power(p, Job.MAGE) * 1.8; // 3 hits x 60% per sword
        Vector right = new Vector(-look.getZ(), 0, look.getX());
        if (right.lengthSquared() < 1e-6) right = new Vector(1, 0, 0); else right.normalize();
        final Vector rt = right;
        ItemDisplay[] swords = new ItemDisplay[3];
        Vector[] tilt = new Vector[3];
        Function<Integer, Location> spot = s -> p.getLocation().add(0, 3.0 + (s == 0 ? .4 : 0), 0).add(rt.clone().multiply(s * 1.3));
        for (int k = 0; k < 3; k++) {
            int s = k - 1;
            tilt[k] = new Vector(rt.getX() * Math.sin(s * .45), -Math.cos(s * .45), rt.getZ() * Math.sin(s * .45));
            swords[k] = fx("giant_sword", spot.apply(s), tipTo(tilt[k]), vec(.2f), null, 70);
            tween(swords[k], 1, 4, new Vector3f(), tipTo(tilt[k]), vec(.75f));
        }
        snd(w, p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, .8f, 1.5f);
        snd(w, p.getLocation(), Sound.ITEM_ARMOR_EQUIP_DIAMOND, .7f, 1.2f);
        boolean[] cancelled = {false};
        run(1, 6, i -> {
            if (!ready(p, w)) { cancelled[0] = true; return true; }
            for (int k = 0; k < 3; k++) {
                Location at = spot.apply(k - 1);
                moveFx(swords[k], at);
                w.spawnParticle(Particle.END_ROD, at.clone().add(0, -1.0, 0), 1, .1, .1, .1, 0);
                w.spawnParticle(Particle.DUST, at, 2, .15, .3, .15, 0, new Particle.DustOptions(Color.fromRGB(170, 230, 255), 1.1f));
            }
            return false;
        }, () -> {
            if (cancelled[0]) { for (ItemDisplay d : swords) kill(d); return; }
            for (int k = 0; k < 3; k++) {
                LivingEntity target = targets.isEmpty() ? null : targets.get(k % targets.size());
                ItemDisplay sword = swords[k];
                Bukkit.getScheduler().runTaskLater(plugin, () -> launchSword(p, sword, target, aimed, dmg), k * 2L);
            }
        });
    }

    /** One 거검진 sword: accelerates tip-first, homes on the target (or the aimed point), bursts on contact. */
    private void launchSword(Player p, ItemDisplay sword, LivingEntity target, Location aimed, double dmg) {
        World w = p.getWorld();
        if (!ready(p, w)) { kill(sword); return; }
        Location pos = sword != null && sword.isValid() ? sword.getLocation().clone() : p.getLocation().add(0, 3, 0);
        Location goal0 = target != null && target.isValid() ? target.getBoundingBox().getCenter().toLocation(w) : aimed;
        Vector d = goal0 != null ? goal0.toVector().subtract(pos.toVector()) : p.getEyeLocation().getDirection();
        if (d.lengthSquared() < 1e-6) d = new Vector(0, -1, 0);
        d.normalize();
        final Vector dir = d;
        LivingEntity[] cur = {target};
        Color cyan = Color.fromRGB(170, 230, 255);
        snd(w, pos, Sound.ITEM_TRIDENT_THROW, .8f, 1.3f);
        run(1, 40, i -> {
            if (!p.isOnline() || p.getWorld() != w) return true;
            if (cur[0] != null && (!cur[0].isValid() || cur[0].isDead())) {
                cur[0] = null;
                double best = 100;
                for (LivingEntity t : near(p, pos, 10)) { double dd = t.getLocation().distanceSquared(pos); if (dd < best) { best = dd; cur[0] = t; } }
            }
            Location goal = cur[0] != null ? cur[0].getBoundingBox().getCenter().toLocation(w) : aimed;
            if (goal != null) {
                Vector want = goal.toVector().subtract(pos.toVector());
                if (want.lengthSquared() > 1e-6) dir.multiply(.55).add(want.normalize().multiply(.45)).normalize();
            }
            double speed = Math.min(1.9, .7 + i * .25);
            var block = w.rayTraceBlocks(pos, dir, speed, FluidCollisionMode.NEVER, true);
            double step = block != null ? Math.max(.1, block.getHitPosition().distance(pos.toVector())) : speed;
            Location prev = pos.clone();
            pos.add(dir.clone().multiply(step));
            if (sword != null && sword.isValid()) { sword.setTeleportDuration(1); orientTip(sword, dir, .75f); moveFx(sword, pos); }
            for (double s = 0; s <= 1; s += .5) {
                Location at = prev.clone().add(pos.clone().subtract(prev).toVector().multiply(s));
                w.spawnParticle(Particle.END_ROD, at, 1, .03, .03, .03, 0);
                w.spawnParticle(Particle.DUST, at, 1, .05, .05, .05, 0, new Particle.DustOptions(cyan, 1.1f));
            }
            LivingEntity victim = null;
            for (LivingEntity t : near(p, pos, 1.1)) if (victim == null || t == cur[0]) victim = t;
            boolean atPoint = cur[0] == null && aimed != null && pos.distanceSquared(aimed) < 1.5;
            if (victim == null && block == null && !atPoint) return false;
            w.spawnParticle(Particle.CRIT, pos, 14, .3, .3, .3, .25);
            w.spawnParticle(Particle.END_ROD, pos, 10, .25, .25, .25, .12);
            w.spawnParticle(Particle.DUST, pos, 12, .35, .35, .35, 0, new Particle.DustOptions(cyan, 1.5f));
            snd(w, pos, Sound.ITEM_TRIDENT_HIT, .8f, 1.4f);
            snd(w, pos, Sound.BLOCK_AMETHYST_BLOCK_BREAK, .8f, 1.6f);
            if (victim != null) burst(p, victim, dmg, 3);
            else for (LivingEntity t : near(p, pos, 2)) burst(p, t, dmg, 3);
            return true;
        }, () -> kill(sword));
    }

    /** Compressed mana shells being formed per caster (created by the wind-up, taken over by the shot). */
    private final Map<UUID, ItemDisplay> shells = new HashMap<>();

    private Location shellSpot(Player p) {
        return staffTip(p).add(p.getEyeLocation().getDirection().normalize().multiply(.7));
    }

    /** 마력 포탄 wind-up: mana motes are pulled into the staff tip and pressed into a spinning, squeezing shell. */
    private void thunderChargeFx(Player p, int ticks) {
        World w = p.getWorld();
        UUID id = p.getUniqueId();
        kill(shells.remove(id));
        ItemDisplay shell = fx("mana_shell", shellSpot(p), new Quaternionf(), vec(.4f), null, ticks + 30);
        if (shell != null) shells.put(id, shell);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        run(1, ticks, i -> {
            if (!ready(p, w)) { kill(shell); shells.remove(id, shell); return true; }
            Vector d = p.getEyeLocation().getDirection().normalize();
            Location at = shellSpot(p);
            double frac = (i + 1) / (double) ticks;
            float s = (float) (.4 + .9 * frac);
            double sq = Math.sin(i * 1.3) * .14 * frac;
            poseFx(shell, at, facing(d, (float) (i * .5)), new Vector3f((float) (s * (1 + sq)), (float) (s * (1 - sq)), (float) (s * (1 + sq))), 2);
            for (int n = 0; n < 3; n++) {
                Vector off = new Vector(rnd.nextDouble(-1, 1), rnd.nextDouble(-1, 1), rnd.nextDouble(-1, 1));
                if (off.lengthSquared() < 1e-4) continue;
                off.normalize().multiply(2.0 * (1 - frac) + .4);
                w.spawnParticle(Particle.DUST, at.clone().add(off), 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(176, 90, 255), 1.1f));
            }
            if (shell == null) w.spawnParticle(Particle.DUST, at, 3, .1 + .2 * frac, .1 + .2 * frac, .1 + .2 * frac, 0, new Particle.DustOptions(Color.fromRGB(150, 60, 230), (float) (1 + 1.6 * frac)));
            if (i % 3 == 0) snd(w, at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, .5f, (float) (.6 + frac * 1.2));
            return false;
        }, null);
    }

    /** 마력 포탄: the pressed mana shell is fired at the aimed enemy (nearest near the crosshair within 20) or the looked-at point, homes slightly, and explodes (420% within 3.5, small knock-up). */
    private void thunder(Player p) {
        World w = p.getWorld();
        UUID id = p.getUniqueId();
        double dmg = power(p, Job.MAGE) * 4.2;
        Location eye = p.getEyeLocation();
        Vector look = eye.getDirection().normalize();
        ItemDisplay held = shells.remove(id);
        Location pos = held != null && held.isValid() ? held.getLocation().clone() : shellSpot(p);
        LivingEntity lock = aimTarget(p, eye, look, 20, false);
        Vector dir;
        if (lock != null) dir = lock.getBoundingBox().getCenter().subtract(pos.toVector());
        else {
            var rh = w.rayTraceBlocks(eye, look, 20, FluidCollisionMode.NEVER, true);
            Vector point = rh == null ? eye.toVector().add(look.clone().multiply(20)) : rh.getHitPosition();
            dir = point.subtract(pos.toVector());
        }
        final Vector d = dir.lengthSquared() < 1e-6 ? look.clone() : dir.normalize();
        final Location start = pos.clone();
        final ItemDisplay ball = held != null ? held : fx("mana_shell", pos, facing(d, 0), vec(1.3f), null, 50);
        snd(w, pos, Sound.ENTITY_BLAZE_SHOOT, 1f, .5f);
        snd(w, pos, Sound.ENTITY_ENDER_DRAGON_SHOOT, .6f, 1.8f);
        final double speed = 1.1;
        Color purple = Color.fromRGB(176, 90, 255);
        run(1, 24, i -> {
            if (lock != null && lock.isValid() && lock.getWorld() == w) {
                Vector want = lock.getBoundingBox().getCenter().subtract(pos.toVector());
                if (want.lengthSquared() > 1e-6) d.multiply(.85).add(want.normalize().multiply(.15)).normalize();
            }
            var rh = w.rayTraceBlocks(pos, d, speed, FluidCollisionMode.NEVER, true);
            if (rh != null) {
                pos.setX(rh.getHitPosition().getX()); pos.setY(rh.getHitPosition().getY()); pos.setZ(rh.getHitPosition().getZ());
                return true;
            }
            pos.add(d.clone().multiply(speed));
            if (ball != null && ball.isValid()) poseFx(ball, pos, facing(d, (float) (i * .6)), vec(1.3f), 1);
            w.spawnParticle(Particle.DUST, pos, 4, .15, .15, .15, 0, new Particle.DustOptions(purple, 1.5f));
            w.spawnParticle(Particle.DUST, pos, 2, .25, .25, .25, 0, new Particle.DustOptions(Color.fromRGB(110, 40, 190), 1.1f));
            w.spawnParticle(Particle.ENCHANT, pos, 2, .3, .3, .3, .1);
            if (!near(p, pos, 1.0).isEmpty()) return true;
            return pos.distanceSquared(start) >= 400;
        }, () -> {
            kill(ball);
            explodeShell(p, pos, dmg);
        });
    }

    private void explodeShell(Player p, Location at, double dmg) {
        World w = at.getWorld();
        ItemDisplay blast = fx("mana_blast", at, new Quaternionf(), vec(2f), null, 16);
        tween(blast, 1, 5, new Vector3f(), new Quaternionf().rotateY(.8f), vec(4f));
        tween(blast, 6, 4, new Vector3f(), new Quaternionf().rotateY(1.4f), vec(.1f));
        w.spawnParticle(Particle.EXPLOSION, at, 3, 1, 1, 1, 0);
        w.spawnParticle(Particle.EXPLOSION_EMITTER, at, 1, 0, 0, 0, 0);
        w.spawnParticle(Particle.DUST, at, 60, 1.6, 1.6, 1.6, 0, new Particle.DustOptions(Color.fromRGB(176, 90, 255), 2f));
        w.spawnParticle(Particle.DUST, at, 30, 2.2, 2.2, 2.2, 0, new Particle.DustOptions(Color.fromRGB(110, 40, 190), 1.6f));
        w.spawnParticle(Particle.END_ROD, at, 20, 1.2, 1.2, 1.2, .2);
        snd(w, at, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, .55f);
        snd(w, at, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1f, .6f);
        snd(w, at, Sound.ENTITY_WARDEN_SONIC_BOOM, .5f, 1.4f);
        for (LivingEntity t : near(p, at, 3.5)) {
            burst(p, t, dmg, 6);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (t.isValid() && !t.isDead()) t.setVelocity(t.getVelocity().setY(.55));
            }, 1);
        }
    }

    private void light(Player p) { // 치유의 빛
        World w = p.getWorld();
        healBy(p, .25);
        snd(w, p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, .9f, 1.2f);
        snd(w, p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, .7f, 1.6f);
        snd(w, p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, .5f, 1.5f);
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
                snd(w, block.getHitPosition().toLocation(w), Sound.BLOCK_AMETHYST_BLOCK_HIT, .5f, 1.2f);
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
                if (mark != null) chargeHit(p, t, dmg, (int) mark[0], mark[1], 2); else burst(p, t, dmg, 3); // basic: 40% x 3
                w.spawnParticle(Particle.DUST, pos, 14, .3, .3, .3, 0, new Particle.DustOptions(purple, 1.8f));
                snd(w, pos, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, .6f, 1.5f);
                return true;
            }
            return !p.isOnline();
        }, () -> kill(ball));
    }

    // ---------------------------------------------------------------- reworked skills (6 general + 3 buff + 2 ultimate per class)

    private boolean phoenixOn(Player p) { return phoenixUntil.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis(); }

    private boolean atkBuffOn(Player p) { return atkBuff.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis(); }

    /** 공격력 버프: +20% to skill/basic damage dealt through hit() for 10 s, with a faint aura. */
    private void giveAtkBuff(Player p, Color color) {
        long ms = CombatStats.duration(p, 10_000);
        long until = System.currentTimeMillis() + ms;
        atkBuff.put(p.getUniqueId(), until);
        World w = p.getWorld();
        snd(w, p.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, .6f, 1.6f);
        snd(w, p.getLocation(), Sound.ITEM_TOTEM_USE, .3f, 1.5f);
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

    /**
     * 검무 (w_heavy, longsword): six quick cuts over ~1 s; the phantom sword dances left / right and every cut leaves a thin red
     * line at the far end of the reach. Each cut hits the 6-block 140 degree front for 55% (330% total).
     */
    private void heavy(Player p) {
        World w = p.getWorld();
        double dmg = power(p, Job.WARRIOR) * .55, cos = Math.cos(Math.toRadians(70));
        snd(w, p.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, .7f, 1.4f);
        run(4, 6, i -> {
            if (!ready(p, w)) return true;
            Vector dir = flatLook(p);
            if (dir == null) return true;
            Location eye = p.getEyeLocation();
            Vector look = eye.getDirection().normalize();
            Vector side = new Vector(-look.getZ(), 0, look.getX());
            if (side.lengthSquared() < 1e-6) side = new Vector(1, 0, 0); else side.normalize();
            boolean f = i % 2 == 0;
            phantomAct(p, Job.WARRIOR, f);
            Location far = eye.clone().add(look.clone().multiply(4.6 + (i % 3) * .4)).add(0, -.25 + (i % 3 - 1) * .35, 0);
            lineSlash(far, look, side, f);
            snd(w, eye, Sound.ENTITY_PLAYER_ATTACK_SWEEP, .8f, 1f + i * .08f);
            Location center = p.getLocation().add(0, 1, 0);
            for (LivingEntity t : near(p, center, 6)) if (inCone(center, dir, t, cos, true)) hit(p, t, dmg);
            if (i == 5) w.spawnParticle(Particle.SWEEP_ATTACK, center.clone().add(dir.clone().multiply(3)), 6, 1.5, .4, 1.5, 0);
            return false;
        }, null);
    }

    /**
     * 검막 (w_impale, longsword): for 3 s three phantom longswords whirl around the warrior (radius 2.2, chest height), blades
     * pointing along the spin, and cut every enemy within 3.2 blocks 8 times (40% each, every 7-8 ticks). The swords follow the player.
     */
    private void impale(Player p) {
        World w = p.getWorld();
        double dmg = power(p, Job.WARRIOR) * .4;
        ItemDisplay[] swords = new ItemDisplay[3];
        for (int k = 0; k < 3; k++) swords[k] = fx("phantom_sword", p.getLocation().add(0, 1.1, 0), new Quaternionf(), vec(.2f), null, 66);
        snd(w, p.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, .9f, 1.5f);
        snd(w, p.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, .6f, 1.6f);
        Particle.DustOptions red = new Particle.DustOptions(Color.fromRGB(255, 80, 70), .8f);
        run(1, 60, i -> {
            if (!ready(p, w)) return true;
            Location c = p.getLocation().add(0, 1.1, 0);
            double spin = i * .42;
            float grow = (float) Math.min(1, (i + 1) / 4.0);
            for (int k = 0; k < 3; k++) {
                if (swords[k] == null || !swords[k].isValid()) continue;
                double a = spin + k * Math.PI * 2 / 3;
                Location at = c.clone().add(Math.cos(a) * 4.3, Math.sin(i * .2 + k) * .15, Math.sin(a) * 4.3); // orbit 1.5x wider
                Vector tangent = new Vector(-Math.sin(a), 0, Math.cos(a));
                // the sword lies flat-ish along the spin: tip (model +Y) leads, edge outward
                poseFx(swords[k], at, facing(tangent, 0).rotateX((float) (Math.PI / 2)), vec(.95f * grow), 1);
                w.spawnParticle(Particle.DUST, at, 1, .05, .05, .05, 0, red);
            }
            if (i % 8 == 3) {
                for (LivingEntity t : near(p, c, 6.2)) {
                    hit(p, t, dmg);
                    w.spawnParticle(Particle.CRIT, t.getBoundingBox().getCenter().toLocation(w), 4, .25, .3, .25, .2);
                }
                snd(w, c, Sound.ENTITY_PLAYER_ATTACK_SWEEP, .5f, 1.3f + (i % 16 == 3 ? .15f : 0));
            }
            return false;
        }, () -> { for (ItemDisplay d : swords) kill(d); });
    }


    /** 더블어택 (w_iai): two quick slashes in a row straight ahead, each cutting everything on a 10 block line (100% x 2 per slash, 400% total). */
    private void iai(Player p) {
        Vector dir = flatLook(p);
        if (dir == null) return;
        World w = p.getWorld();
        double dmg = power(p, Job.WARRIOR) * 2.0;
        for (int k = 0; k < 2; k++) {
            final boolean second = k == 1;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!ready(p, w)) return;
                Vector d = flatLook(p);
                if (d == null) d = dir;
                Location mid = p.getLocation().add(0, 1.1, 0);
                double reach = 0;
                for (double s = .5; s <= 10; s += .5) {
                    if (!mid.clone().add(d.clone().multiply(s)).getBlock().isPassable()) break;
                    reach = s;
                }
                Vector side = new Vector(-d.getZ(), 0, d.getX());
                phantomAct(p, Job.WARRIOR, !second);
                if (reach >= 1) lineSlash(mid.clone().add(d.clone().multiply(Math.max(1.5, reach - .4))), d, side, !second);
                for (double s = .5; s <= reach; s += .3)
                    w.spawnParticle(Particle.DUST, mid.clone().add(d.clone().multiply(s)), 1, 0, 0, 0, 0, new Particle.DustOptions(second ? Color.fromRGB(255, 90, 80) : Color.fromRGB(255, 235, 230), .45f));
                snd(w, mid, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, second ? .8f : 1.2f);
                snd(w, mid, Sound.ITEM_TRIDENT_RIPTIDE_1, .6f, second ? 1.4f : 1.7f);
                if (reach < .5) return;
                for (LivingEntity t : along(p, mid, d, reach, 1.8)) {
                    burst(p, t, dmg, 2);
                    w.spawnParticle(Particle.CRIT, t.getBoundingBox().getCenter().toLocation(w), 10, .3, .4, .3, .3);
                }
            }, second ? 5 : 1);
        }
    }

    /** 백호참: the warrior is rooted for 2 s (looking allowed), then rakes one huge claw swipe (8 blocks, 160 degree fan, 180% x 5 = 900%). */
    private void tiger(Player p) {
        World w = p.getWorld();
        UUID id = p.getUniqueId();
        long until = System.currentTimeMillis() + 2_000;
        rooted.put(id, until);
        p.setVelocity(new Vector(0, 0, 0));
        Location start = p.getLocation();
        snd(w, start, Sound.ENTITY_POLAR_BEAR_WARNING, .9f, .7f);
        snd(w, start, Sound.ENTITY_RAVAGER_ROAR, .8f, 1.2f);
        double dmg = power(p, Job.WARRIOR) * 9.0; // 5 hits x 180%
        boolean[] cancelled = {false};
        run(1, 40, i -> {
            if (!ready(p, w) || !Long.valueOf(until).equals(rooted.get(id))) { cancelled[0] = true; return true; }
            double frac = (i + 1) / 40.0;
            Location c = p.getLocation().add(0, 1, 0);
            Vector look = p.getEyeLocation().getDirection().setY(0);
            if (look.lengthSquared() < 1e-6) look = new Vector(0, 0, 1); else look.normalize();
            Vector side = new Vector(-look.getZ(), 0, look.getX());
            for (int k = 0; k < 3; k++) { // energy gathers around the warrior
                double a = i * .5 + k * Math.PI * 2 / 3;
                double r = 2.6 * (1 - frac) + .5;
                Location at = c.clone().add(Math.cos(a) * r, Math.sin(a) * r * .7, Math.sin(a * .5) * r);
                w.spawnParticle(Particle.DUST, at, 1, .03, .03, .03, 0, new Particle.DustOptions(Color.fromRGB(255, 255, 255), 1f));
            }
            for (int lane = -1; lane <= 1; lane++) // three-line glint building up in front
                w.spawnParticle(Particle.CRIT, c.clone().add(look.clone().multiply(1.4)).add(side.clone().multiply(lane * .7 * frac)).add(0, .6 * frac, 0), 1, .02, .02, .02, 0);
            if (i % 8 == 0) snd(w, c, Sound.ITEM_ARMOR_EQUIP_IRON, .5f, (float) (.6 + frac * 1.0));
            if (i % 10 == 5) w.spawnParticle(Particle.DUST, p.getLocation().add(0, .15, 0), 8, .8, .05, .8, 0, new Particle.DustOptions(Color.fromRGB(230, 230, 240), 1.2f));
            return false;
        }, () -> {
            rooted.remove(id, until);
            if (cancelled[0] || !ready(p, w)) return;
            Vector d = flatLook(p);
            if (d == null) return;
            Location o = p.getLocation().add(0, 1, 0);
            Vector rs = new Vector(-d.getZ(), 0, d.getX());
            Location pt = o.clone().add(d.clone().multiply(4.2)).add(0, .3, 0);
            for (int hitNo = 0; hitNo < 5; hitNo++) { // one claw-mark set (3 parallel streaks) per damage hit, each at a different angle
                final int hn = hitNo;
                final float sgn = hn % 2 == 0 ? 1f : -1f; // alternate diagonals -> X crossing
                final float tilt = sgn * (.5f + ThreadLocalRandom.current().nextFloat() * .3f);
                final Vector off = rs.clone().multiply((ThreadLocalRandom.current().nextDouble() - .5) * 1.2).add(new Vector(0, (ThreadLocalRandom.current().nextDouble() - .5) * .8, 0));
                Runnable set = () -> {
                    for (int lane = -1; lane <= 1; lane++) {
                        final Location lanePt = pt.clone().add(off).add(rs.clone().multiply(lane * .9 * sgn)).add(0, lane * .3, 0);
                        final int ln = lane;
                        if (ln == -1) clawSlash(lanePt, d, rs, tilt);
                        else Bukkit.getScheduler().runTaskLater(plugin, () -> clawSlash(lanePt, d, rs, tilt), (long) (ln + 1));
                    }
                    snd(w, o, Sound.ENTITY_PLAYER_ATTACK_SWEEP, .8f, .7f + hn * .1f);
                };
                if (hn == 0) set.run(); else Bukkit.getScheduler().runTaskLater(plugin, set, (long) hn * 2);
            }
            snd(w, o, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.3f, .5f);
            snd(w, o, Sound.ENTITY_RAVAGER_ATTACK, 1f, .8f);
            snd(w, o, Sound.ITEM_MACE_SMASH_GROUND, .8f, 1.3f);
            for (double a = -80; a <= 80; a += 20) {
                Vector dd = d.clone().rotateAroundY(Math.toRadians(a));
                for (double r = 2; r <= 8; r += 2) w.spawnParticle(a % 40 == 0 ? Particle.END_ROD : Particle.CRIT, o.clone().add(dd.clone().multiply(r)), 1, .05, .05, .05, 0);
            }
            double cos = Math.cos(Math.toRadians(80));
            for (LivingEntity t : near(p, o, 8)) if (inCone(o, d, t, cos, true)) burst(p, t, dmg, 5);
        });
    }

    private void rage(Player p) { // 전투 함성
        healBy(p, .25);
        giveAtkBuff(p, Color.fromRGB(255, 70, 50));
        snd(p.getWorld(), p.getLocation(), Sound.ENTITY_RAVAGER_ROAR, .9f, 1.3f);
        buffBurst(p, Color.fromRGB(255, 70, 50), Particle.CRIT);
    }

    /** 불굴: 10 s of complete damage immunity while frozen in place (looking around is still allowed). */
    private void fort(Player p) {
        long ms = CombatStats.duration(p, 10_000);
        long until = System.currentTimeMillis() + ms;
        fortress.put(p.getUniqueId(), until);
        World w = p.getWorld();
        snd(w, p.getLocation(), Sound.BLOCK_ANVIL_LAND, .8f, .6f);
        snd(w, p.getLocation(), Sound.ITEM_TOTEM_USE, .5f, 1.4f);
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

    /** 대지의 화살: an earth-spirit rock arrow (260%) piercing 25 blocks; hit enemies get Slowness II for 1.5 s. */
    private void pierce(Player p) {
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector dir = aimDir(p, eye, 25);
        double len = blockLen(p, eye, dir, 25);
        List<LivingEntity> hits = along(p, eye, dir, len, .8);
        Location from = muzzle(p, Job.ARCHER);
        projectileFx("rock_arrow", from, toward(from, eye, dir, len), .9f, from.distance(eye.clone().add(dir.clone().multiply(len))), 4);
        var stone = Material.STONE.createBlockData();
        var dirt = Material.COARSE_DIRT.createBlockData();
        Vector vd = toward(from, eye, dir, len);
        double vk = from.distance(eye.clone().add(dir.clone().multiply(len))) / Math.max(.1, len);
        for (double s = .5; s <= len; s += .7) {
            Location c = from.clone().add(vd.clone().multiply(s * vk)); // along the line from the phantom bow
            w.spawnParticle(Particle.BLOCK, c, 2, .12, .12, .12, 0, ((int) (s / .7)) % 2 == 0 ? stone : dirt);
            if (((int) (s / .7)) % 3 == 0) w.spawnParticle(Particle.DUST, c, 1, .1, .1, .1, 0, new Particle.DustOptions(Color.fromRGB(125, 105, 85), 1.1f));
        }
        snd(w, eye, Sound.ENTITY_ARROW_SHOOT, 1f, .7f);
        snd(w, eye, Sound.BLOCK_STONE_BREAK, .7f, 1.2f);
        double dmg = power(p, Job.ARCHER) * 2.6;
        for (LivingEntity t : hits) {
            burst(p, t, dmg, 4);
            if (t.isValid()) t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 1, false, true));
            rockPuff(w, t.getBoundingBox().getCenter().toLocation(w));
        }
        rockPuff(w, eye.clone().add(dir.clone().multiply(len)));
    }

    private void rockPuff(World w, Location at) {
        w.spawnParticle(Particle.BLOCK, at, 22, .35, .35, .35, 0, Material.STONE.createBlockData());
        w.spawnParticle(Particle.BLOCK, at, 10, .3, .3, .3, 0, Material.COARSE_DIRT.createBlockData());
        w.spawnParticle(Particle.POOF, at, 4, .2, .2, .2, .03);
        snd(w, at, Sound.BLOCK_GRAVEL_BREAK, .8f, .8f);
    }

    /** 수압 화살: one very fast sharp water arrow, piercing 25 blocks (300% to each enemy). */
    private void spread(Player p) {
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector dir = aimDir(p, eye, 25);
        double len = blockLen(p, eye, dir, 25);
        List<LivingEntity> hits = along(p, eye, dir, len, .6);
        Location from = muzzle(p, Job.ARCHER);
        projectileFx("water_arrow", from, toward(from, eye, dir, len), .8f, from.distance(eye.clone().add(dir.clone().multiply(len))), 1);
        Particle.DustOptions blue = new Particle.DustOptions(Color.fromRGB(70, 160, 255), 1.1f);
        Vector vd = toward(from, eye, dir, len);
        double vk = from.distance(eye.clone().add(dir.clone().multiply(len))) / Math.max(.1, len);
        for (double s = .5; s <= len; s += .6) {
            Location c = from.clone().add(vd.clone().multiply(s * vk)); // along the line from the phantom bow
            w.spawnParticle(Particle.SPLASH, c, 3, .12, .12, .12, 0);
            w.spawnParticle(Particle.DUST, c, 1, .06, .06, .06, 0, blue);
            if (((int) (s / .6)) % 2 == 0) w.spawnParticle(Particle.BUBBLE_POP, c, 1, .1, .1, .1, 0);
            if (((int) (s / .6)) % 3 == 0) w.spawnParticle(Particle.DRIPPING_WATER, c, 1, .12, .12, .12, 0);
        }
        snd(w, eye, Sound.ENTITY_ARROW_SHOOT, 1.1f, 1.6f);
        snd(w, eye, Sound.ENTITY_PLAYER_SPLASH, .8f, 1.5f);
        double dmg = power(p, Job.ARCHER) * 3.0;
        for (LivingEntity t : hits) {
            burst(p, t, dmg, 4);
            Location c = t.getBoundingBox().getCenter().toLocation(w);
            w.spawnParticle(Particle.SPLASH, c, 18, .35, .4, .35, .1);
            w.spawnParticle(Particle.BUBBLE_POP, c, 8, .3, .3, .3, .05);
            snd(w, c, Sound.ENTITY_GENERIC_SPLASH, .6f, 1.4f);
        }
    }

    /** 산탄 사격: after a 1 s draw, 20 arrows (25% each) in a +-18 degree cone and a ~5 block recoil. */
    private void shotgun(Player p) {
        p.removePotionEffect(PotionEffectType.SLOWNESS);
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector center = aimDir(p, eye, 25);
        double dmg = power(p, Job.ARCHER) * .25;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Location from = muzzle(p, Job.ARCHER);
        for (int n = 0; n < 20; n++) {
            double ang = Math.toRadians(18) * Math.sqrt(rnd.nextDouble()), phi = rnd.nextDouble() * Math.PI * 2;
            Vector d = center.clone().rotateAroundY(Math.cos(phi) * ang);
            Vector right = d.clone().crossProduct(new Vector(0, 1, 0));
            if (right.lengthSquared() > 1e-6) d.rotateAroundAxis(right.normalize(), Math.sin(phi) * ang);
            d.normalize();
            double len = blockLen(p, from, d, 25);
            List<LivingEntity> hits = along(p, from, d, len, .6);
            if (!hits.isEmpty()) {
                LivingEntity t = hits.get(0);
                len = Math.min(len, t.getBoundingBox().getCenter().distance(from.toVector()) + .3);
                hit(p, t, dmg);
            }
            if (n % 2 == 0) bigArrow(from, d, .26f, len, 3);
            for (double s = 1; s <= len; s += 2) w.spawnParticle(Particle.CRIT, from.clone().add(d.clone().multiply(s)), 1, 0, 0, 0, 0);
        }
        snd(w, eye, Sound.ENTITY_GENERIC_EXPLODE, .6f, 1.8f);
        snd(w, eye, Sound.ENTITY_ARROW_SHOOT, 1.2f, .7f);
        w.spawnParticle(Particle.CLOUD, from, 12, .3, .3, .3, .08);
        Vector back = p.getEyeLocation().getDirection().setY(0);
        if (back.lengthSquared() < .01) return;
        back.normalize().multiply(-1);
        if (!w.getWorldBorder().isInside(p.getLocation().add(back.clone().multiply(3)))) return;
        fallImmune.put(p.getUniqueId(), System.currentTimeMillis() + 2_000);
        p.setVelocity(back.multiply(.85).setY(.42));
    }

    /** 질풍 화살: a huge wind-wrapped arrow (380%) piercing 25 blocks. */
    private void gale(Player p) {
        Location eye = p.getEyeLocation();
        Vector dir = aimDir(p, eye, 25);
        List<LivingEntity> hits = beam(p, eye, dir, 25, 1.1, 0, Particle.CLOUD, Color.fromRGB(150, 240, 255));
        Location pstart = muzzle(p, Job.ARCHER);
        Vector vdir = toward(pstart, eye, dir, beamLen);
        bigArrow(pstart, vdir, .9f, pstart.distance(eye.clone().add(dir.clone().multiply(beamLen))), 5);
        ItemDisplay wind = fx("wind_spiral", pstart, facing(vdir, 0), vec(1.0f), null, 16);
        tween(wind, 1, 4, new Vector3f(), facing(vdir, 2.4f), vec(1.3f));
        if (wind != null) flyFx(wind, eye.clone().add(dir.clone().multiply(beamLen * .85)), 4);
        snd(p.getWorld(), eye, Sound.ENTITY_BREEZE_SHOOT, 1f, 1.1f);
        double dmg = power(p, Job.ARCHER) * 3.8;
        for (LivingEntity t : hits) {
            burst(p, t, dmg, 4);
            t.getWorld().spawnParticle(Particle.CLOUD, t.getBoundingBox().getCenter().toLocation(t.getWorld()), 10, .3, .3, .3, .15);
        }
    }

    /** 매의 눈. */
    private void focus(Player p) {
        healBy(p, .25);
        giveAtkBuff(p, Color.fromRGB(255, 230, 90));
        snd(p.getWorld(), p.getLocation(), Sound.ENTITY_EVOKER_PREPARE_SUMMON, .7f, 1.8f);
        buffBurst(p, Color.fromRGB(255, 230, 90), Particle.END_ROD);
    }

    /** 바람 정령: a wind bow floats beside the archer for 10 s and shoots the nearest enemy (60%) every second. */
    private void spirit(Player p) {
        long ms = CombatStats.duration(p, 10_000);
        World w = p.getWorld();
        UUID sid = p.getUniqueId();
        long sUntil = System.currentTimeMillis() + ms;
        spiritUntil.put(sid, sUntil); // a recast replaces the old spirit instead of stacking
        snd(w, p.getLocation(), Sound.ENTITY_BREEZE_IDLE_GROUND, 1f, 1.2f);
        ItemDisplay bow = fx("wind_bow", p.getLocation().add(0, 2, 0), facing(p.getEyeLocation().getDirection(), 0), vec(1.5f), null, (int) Math.min(260, ms / 50 + 6));
        double dmg = power(p, Job.ARCHER) * .6;
        run(2, (int) Math.max(1, ms / 100), i -> {
            if (!ready(p, w) || spiritUntil.getOrDefault(sid, 0L) != sUntil) return true;
            Vector look = p.getEyeLocation().getDirection().normalize();
            Vector side = new Vector(-look.getZ(), 0, look.getX());
            if (side.lengthSquared() < 1e-6) side = new Vector(1, 0, 0); else side.normalize();
            Location pos = p.getLocation().add(0, 2.0 + Math.sin(i * .2) * .12, 0).add(side.multiply(-.95));
            moveFx(bow, pos);
            tween(bow, 1, 2, new Vector3f(), facing(look, 0), vec(1.5f));
            w.spawnParticle(Particle.CLOUD, pos, 1, .15, .15, .15, .01);
            w.spawnParticle(Particle.DUST, pos, 1, .2, .2, .2, 0, new Particle.DustOptions(Color.fromRGB(160, 240, 210), 1f));
            if (i % 10 == 9) {
                List<LivingEntity> targets = near(p, p.getLocation(), 25);
                targets.sort(Comparator.comparingDouble(t -> t.getLocation().distanceSquared(p.getLocation())));
                if (!targets.isEmpty()) {
                    LivingEntity t = targets.get(0);
                    Vector to = t.getBoundingBox().getCenter().subtract(pos.toVector());
                    double dist = to.length();
                    if (dist > .1) {
                        bigArrow(pos, to.clone().normalize(), .3f, dist, 3);
                        snd(w, pos, Sound.ENTITY_ARROW_SHOOT, .6f, 1.7f);
                        hit(p, t, dmg);
                    }
                }
            }
            return false;
        }, () -> { kill(bow); spiritUntil.remove(sid, sUntil); });
    }

    /** 화살 폭풍: five wind bows hover behind the archer and fire 2 homing arrows every 0.25 s for 5 s (25% each, 40 arrows). */
    private void arrowStorm(Player p) {
        World w = p.getWorld();
        double dmg = power(p, Job.ARCHER) * .25;
        ItemDisplay[] bows = new ItemDisplay[5];
        for (int k = 0; k < 5; k++) {
            bows[k] = fx("wind_bow", p.getLocation().add(0, 2, 0), new Quaternionf(), vec(2.4f), null, 120);
            if (bows[k] != null) bows[k].setTeleportDuration(4);
        }
        snd(w, p.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1.2f, .9f);
        snd(w, p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, .8f, 1.6f);
        // green web sigil under the archer for the whole storm, plus a light pillar on cast
        ItemDisplay web = sigil("ult_sigil_green", p.getLocation().add(0, .06, 0), 2.6f, 100, .6f);
        lightPillar(p.getLocation(), 1.4f, 22f, 14);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        run(5, 20, i -> {
            if (!ready(p, w)) return true;
            if (web != null && web.isValid()) { web.setTeleportDuration(5); moveFx(web, p.getLocation().add(0, .06, 0)); }
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
                if (bows[k] != null && bows[k].isValid()) tween(bows[k], 1, 3, new Vector3f(), facing(look, 0), vec(2.4f));
                w.spawnParticle(Particle.END_ROD, at[k], 1, .1, .1, .1, .01);
                w.spawnParticle(Particle.ENCHANT, at[k], 2, .25, .25, .25, .3);
            }
            for (int n = 0; n < 2; n++) {
                Location from = at[rnd.nextInt(5)];
                Vector d = look.clone().rotateAroundY(Math.toRadians(rnd.nextDouble(-3, 3)));
                LivingEntity lock = null; double best = Double.MAX_VALUE;
                for (LivingEntity t : near(p, from, 25)) {
                    Vector rel = t.getBoundingBox().getCenter().subtract(from.toVector());
                    double score = rel.length() - 8 * look.dot(rel.clone().normalize());
                    if (score < best) { best = score; lock = t; }
                }
                if (lock != null) d = lock.getBoundingBox().getCenter().subtract(from.toVector()).normalize();
                volleyArrow(p, from, d, 25, .6, dmg, .42f, 3);
                if (lock != null && n == 0 && i % 2 == 0) skyArrow(w, lock.getLocation()); // visual: a big arrow drops from the sky onto the target
            }
            snd(w, eye, Sound.ENTITY_ARROW_SHOOT, .5f, 1.5f + rnd.nextFloat() * .3f);
            return false;
        }, () -> { for (ItemDisplay b : bows) kill(b); });
    }

    /** 화살 폭풍 decoration: a big arrow falls steeply onto the spot with a green trail and a green ring flash on landing (no damage). */
    private void skyArrow(World w, Location ground) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Location land = ground.clone().add(rnd.nextDouble(-.6, .6), .3, rnd.nextDouble(-.6, .6));
        Location from = land.clone().add(rnd.nextDouble(-2, 2), 14, rnd.nextDouble(-2, 2));
        Vector dir = land.toVector().subtract(from.toVector()).normalize();
        ItemDisplay a = fx("arrow_big", from, facing(dir, 0), vec(1.1f), new ItemStack(Material.ARROW), 14);
        Particle.DustOptions green = new Particle.DustOptions(Color.fromRGB(121, 255, 87), 1.2f);
        fall(a, from, land, 5, at -> w.spawnParticle(Particle.DUST, at, 2, .08, .08, .08, 0, green), () -> {
            ring(land.clone().add(0, .1, 0), 1.3, 18, at -> w.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, green));
            w.spawnParticle(Particle.END_ROD, land, 6, .2, .4, .2, .06);
            snd(w, land, Sound.ENTITY_ARROW_HIT, .5f, 1.3f);
        });
    }

    /** 피닉스의 강림: a phoenix hovers at the right shoulder for 12 s shooting fireballs (120%, 1 s) while basic attacks become fire arrows. */
    private void phoenix(Player p) {
        World w = p.getWorld();
        UUID id = p.getUniqueId();
        long ms = CombatStats.duration(p, 12_000);
        long until = System.currentTimeMillis() + ms;
        phoenixUntil.put(id, until);
        Vector look0 = p.getEyeLocation().getDirection().normalize();
        Location[] cur = {phoenixSpot(p, look0, 0)};
        ItemDisplay bird = fx("phoenix", cur[0], facing(look0, 0), vec(2.7f), null, (int) Math.min(260, ms / 50 + 6));
        if (bird != null) bird.setTeleportDuration(3);
        snd(w, p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.2f, .6f);
        snd(w, p.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1f, .7f);
        snd(w, p.getLocation(), Sound.ENTITY_PHANTOM_FLAP, .7f, .8f);
        w.spawnParticle(Particle.FLAME, cur[0], 40, .5, .5, .5, .08);
        double dmg = power(p, Job.ARCHER) * 1.2;
        Color orange = Color.fromRGB(255, 140, 40);
        run(1, (int) Math.max(1, ms / 50), i -> {
            if (!ready(p, w) || phoenixUntil.getOrDefault(id, 0L) != until) return true;
            Vector look = p.getEyeLocation().getDirection().normalize();
            Location goal = phoenixSpot(p, look, i);
            Location now = cur[0];
            now.add(goal.clone().subtract(now).toVector().multiply(.22));
            moveFx(bird, now);
            if (bird != null && bird.isValid() && i % 2 == 0)
                tween(bird, 1, 2, new Vector3f(), facing(look, (float) Math.sin(i * .25) * .08f), vec(2.7f * (1f + (float) Math.sin(i * .3) * .035f)));
            if (i % 20 == 0) snd(w, now, Sound.ENTITY_PHANTOM_FLAP, .4f, 1.1f);
            if (i % 3 == 0) {
                w.spawnParticle(Particle.FLAME, now, 2, .7, .5, .7, .01);
                w.spawnParticle(Particle.DUST, now, 2, .8, .6, .8, 0, new Particle.DustOptions(orange, .9f));
            }
            if (i % 20 == 19) {
                Location pl = p.getLocation();
                List<LivingEntity> targets = near(p, pl, 25);
                targets.sort(Comparator.comparingDouble(t -> t.getLocation().distanceSquared(pl)));
                if (!targets.isEmpty()) phoenixFireball(p, now.clone(), targets.get(0), dmg);
            }
            return false;
        }, () -> {
            kill(bird);
            if (phoenixUntil.getOrDefault(id, 0L) == until) phoenixUntil.remove(id);
        });
    }

    /** 피닉스의 현현: high above the head and a little behind (out of the line of sight), with a slow, smooth bob. */
    private Location phoenixSpot(Player p, Vector look, int tick) {
        Vector back = look.clone().setY(0);
        if (back.lengthSquared() < 1e-6) back = new Vector(0, 0, 1); else back.normalize();
        return p.getLocation().add(0, 3.9 + Math.sin(tick * .1) * .15, 0).add(back.multiply(-.6));
    }

    private void phoenixFireball(Player p, Location from, LivingEntity target, double dmg) {
        World w = from.getWorld();
        Location to = target.getBoundingBox().getCenter().toLocation(w);
        ItemDisplay ball = spawnItem(from, new ItemStack(Material.FIRE_CHARGE), .5f, new Quaternionf());
        snd(w, from, Sound.ENTITY_BLAZE_SHOOT, .9f, 1.2f);
        fall(ball, from, to, 4, c -> {
            w.spawnParticle(Particle.FLAME, c, 3, .08, .08, .08, .01);
            w.spawnParticle(Particle.SMALL_FLAME, c, 2, .12, .12, .12, .01);
        }, () -> {
            Location at = target.isValid() ? target.getBoundingBox().getCenter().toLocation(w) : to;
            w.spawnParticle(Particle.FLAME, at, 25, .3, .3, .3, .08);
            w.spawnParticle(Particle.LAVA, at, 4, .3, .3, .3, 0);
            snd(w, at, Sound.ENTITY_GENERIC_BURN, .7f, 1.2f);
            if (target.isValid()) {
                burst(p, target, dmg, 2);
                if (target.isValid()) target.setFireTicks(40);
            }
        });
    }

    /** Basic attack while the phoenix is active: fire arrow, +60% damage, ignites the target for 3 s. */
    private void fireArrow(Player p) {
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector adir = aimDir(p, eye, 25);
        List<LivingEntity> hits = beam(p, eye, adir, 25, .5, 1, Particle.FLAME, Color.fromRGB(255, 140, 40));
        bowArrow(p, eye, adir, beamLen, .3f, 4);
        snd(w, eye, Sound.ITEM_FIRECHARGE_USE, .5f, 1.6f);
        double dmg = power(p, Job.ARCHER) * BASIC_ARCHER * 1.6;
        for (LivingEntity t : hits) {
            burst(p, t, dmg, 2); // 72% x 2
            if (t.isValid()) t.setFireTicks(60);
            w.spawnParticle(Particle.FLAME, t.getBoundingBox().getCenter().toLocation(w), 8, .25, .3, .25, .04);
        }
    }

    // ---- mage

    /** 마력 파동: a 70 degree fan-shaped purple wave expands up to 14 blocks along the look direction, piercing (200%). */
    private void wave(Player p) {
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Vector flat = dir.clone().setY(0);
        if (flat.lengthSquared() < .01) flat = new Vector(0, 0, 1);
        flat.normalize();
        Vector fl = flat;
        Location start = muzzle(p, Job.MAGE);
        double dmg = power(p, Job.MAGE) * 2.0;
        Set<LivingEntity> done = new HashSet<>();
        Color purple = Color.fromRGB(170, 70, 235);
        double cos = Math.cos(Math.toRadians(35));
        snd(w, eye, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 1.2f);
        run(1, 10, i -> {
            if (!p.isOnline()) return true;
            double r = 1.4 + (i + 1) * 1.26;
            for (double a = -35; a <= 35; a += 3.5) {
                Vector d = fl.clone().rotateAroundY(Math.toRadians(a));
                Location at = start.clone().add(d.multiply(r));
                if (!at.getBlock().isPassable()) continue;
                boolean edge = Math.abs(a) > 31;
                w.spawnParticle(Particle.DUST, at, 1, .03, .08, .03, 0, new Particle.DustOptions(purple, edge ? 1.4f : 1.1f));
                w.spawnParticle(Particle.DUST, at.clone().add(0, .35, 0), 1, .03, .05, .03, 0, new Particle.DustOptions(Color.fromRGB(215, 160, 255), .8f));
                if (((int) (a / 3.5)) % 3 == 0) w.spawnParticle(Particle.WITCH, at, 1, 0, 0, 0, 0);
            }
            for (LivingEntity t : near(p, start, r + .8)) {
                if (done.contains(t) || start.toVector().distance(t.getBoundingBox().getCenter()) < r - 1.4) continue;
                if (!inCone(start, fl, t, cos, true)) continue;
                done.add(t);
                burst(p, t, dmg, 4);
            }
            return false;
        }, null);
    }

    /** Hammer orientation: model flipped so the head is on top, swung by theta about the horizontal axis perpendicular to heading h (head moves toward h). */
    private static Quaternionf hammerRot(float theta, Vector h) {
        float phi = (float) Math.atan2(-h.getZ(), h.getX()); // model +-X (striking faces) onto the heading
        Vector a = new Vector(0, 1, 0).crossProduct(h);
        return new Quaternionf().rotateAxis(theta, (float) a.getX(), (float) a.getY(), (float) a.getZ()).rotateY(phi).rotateZ((float) Math.PI);
    }

    /** Translation that keeps the handle end (model y = +1.5 blocks) on the display origin for the given rotation / scale. */
    private static Vector3f hammerShift(Quaternionf q, float s) {
        Vector3f t = new Vector3f(0, 1.5f * s, 0);
        q.transform(t);
        return t.negate();
    }

    private void hammerPose(ItemDisplay d, int after, int dur, float theta, float s, Vector h) {
        Quaternionf q = hammerRot(theta, h);
        tween(d, after, dur, hammerShift(q, s), q, vec(s));
    }

    /** 마력 망치: a giant mana hammer stands up beside the aimed point, then its head swings over (pivot = handle end) and slams the point: 380% in r 3.5, 1 s stun, shockwave. */
    private void hammer(Player p) {
        Location c = aimPoint(p, 16);
        World w = c.getWorld();
        Vector h = flatLook(p);
        if (h == null) h = new Vector(0, 0, 1);
        Vector heading = h;
        final float s = 3.0f;
        final double reach = 1.875 * s; // handle end -> head centre
        // pivot = handle end, raised so the head's striking face is exactly on the ground at the end of the 90 degree swing
        Location pivot = c.clone().add(heading.clone().multiply(-reach)).add(0, .87 * s + .05, 0);
        Quaternionf q0 = hammerRot(0, heading);
        ItemDisplay hammer = fx("mana_hammer", pivot, q0, vec(.4f * s), null, 50);
        if (hammer != null) hammer.setTransformation(new Transformation(hammerShift(q0, .4f * s), q0, vec(.4f * s), new Quaternionf()));
        hammerPose(hammer, 1, 5, 0, s, heading);                       // grows upright
        final float wind = -.25f, end = (float) (Math.PI / 2);
        hammerPose(hammer, 8, 3, wind, s, heading);                    // small back-lean
        for (int k = 1; k <= 8; k++) {                                 // accelerating swing, 8 ticks
            double f = k / 8.0;
            hammerPose(hammer, 10 + k, 1, (float) (wind + (end - wind) * f * f), s, heading);
        }
        snd(w, c, Sound.BLOCK_BEACON_ACTIVATE, 1f, .6f);
        Color purple = Color.fromRGB(170, 70, 235);
        double dmg = power(p, Job.MAGE) * 3.8;
        run(1, 19, i -> {
            if (!p.isOnline()) return true;
            if (i >= 11) {
                double f = (i - 10) / 8.0, th = wind + (end - wind) * f * f;
                Location head = pivot.clone().add(heading.getX() * Math.sin(th) * reach, Math.cos(th) * reach, heading.getZ() * Math.sin(th) * reach);
                w.spawnParticle(Particle.DUST, head, 6, .8, .8, .8, 0, new Particle.DustOptions(purple, 1.6f));
            } else if (i % 3 == 0) {
                w.spawnParticle(Particle.WITCH, pivot.clone().add(0, reach, 0), 4, .8, .5, .8, 0);
            }
            return false;
        }, () -> {
            snd(w, c, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, .7f);
            snd(w, c, Sound.ITEM_MACE_SMASH_GROUND, 1.2f, .7f);
            w.spawnParticle(Particle.EXPLOSION, c, 6, 1.5, .2, 1.5, 0);
            w.spawnParticle(Particle.DUST, c.clone().add(0, .4, 0), 50, 1.8, .4, 1.8, 0, new Particle.DustOptions(purple, 1.9f));
            w.spawnParticle(Particle.WITCH, c.clone().add(0, .6, 0), 30, 1.8, .5, 1.8, .05);
            for (int k = 0; k < 4; k++) spikes(c.clone().add(0, .05, 0), new Vector(Math.cos(k * Math.PI / 4), 0, Math.sin(k * Math.PI / 4)), 3.2f);
            run(1, 4, j -> {
                ring(c.clone().add(0, .2, 0), 1 + j, (int) ((1 + j) * 8), at -> w.spawnParticle(Particle.DUST, at, 1, 0, .1, 0, 0, new Particle.DustOptions(purple, 1.5f)));
                return false;
            }, null);
            for (LivingEntity t : near(p, c, 3.5)) {
                burst(p, t, dmg, 4);
                if (t.isValid()) t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 255, false, true));
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> kill(hammer), 6);
        });
    }

    /** 마력 대궁: after the draw (see manaBowFx) a huge mana arrow homes onto the enemy nearest the crosshair (30 blocks), piercing (550%). */
    private void manaBow(Player p) {
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector look = eye.getDirection().normalize();
        LivingEntity[] lock = {aimTarget(p, eye, look, 30, false)};
        Vector rside = new Vector(-look.getZ(), 0, look.getX());
        if (rside.lengthSquared() < 1e-6) rside = new Vector(1, 0, 0); else rside.normalize();
        Location[] cur = {bowSpot(eye, look)}; // from the giant mana bow drawn during the wind-up
        Vector[] dv = {look.clone()};
        ItemDisplay arrow = fx("arrow_big", cur[0], facing(look, 0), vec(.9f), new ItemStack(Material.ARROW), 60);
        snd(w, eye, Sound.ENTITY_ENDER_DRAGON_SHOOT, 1f, 1.5f);
        snd(w, eye, Sound.ENTITY_ARROW_SHOOT, 1.2f, .5f);
        double dmg = power(p, Job.MAGE) * 5.5;
        Color violet = Color.fromRGB(176, 110, 255);
        Set<LivingEntity> done = new HashSet<>();
        double[] travelled = {0};
        final double step = 1.8, maxTurn = Math.toRadians(14);
        run(1, 30, i -> {
            if (!p.isOnline() || p.getWorld() != w) return true;
            Location cp = cur[0];
            LivingEntity lk = lock[0];
            if (lk != null && lk.isValid() && !lk.isDead()) {
                Vector want = lk.getBoundingBox().getCenter().subtract(cp.toVector());
                if (want.lengthSquared() > 1e-4) {
                    want.normalize();
                    double ang = dv[0].angle(want);
                    if (ang <= maxTurn) dv[0] = want;
                    else {
                        Vector axis = dv[0].clone().crossProduct(want);
                        if (axis.lengthSquared() < 1e-8) axis = new Vector(0, 1, 0); else axis.normalize();
                        dv[0] = dv[0].clone().rotateAroundAxis(axis, maxTurn).normalize();
                    }
                }
            } else lock[0] = null;
            Vector d = dv[0];
            boolean blocked = w.rayTraceBlocks(cp, d, step, FluidCollisionMode.NEVER, true) != null;
            for (LivingEntity t : along(p, cp, d, step, 1.2)) {
                if (!done.add(t)) continue;
                burst(p, t, dmg, 5);
                w.spawnParticle(Particle.DUST, t.getBoundingBox().getCenter().toLocation(w), 20, .4, .5, .4, 0, new Particle.DustOptions(violet, 1.8f));
                if (t == lock[0]) lock[0] = null; // pierced: fly on straight
            }
            Location next = cp.clone().add(d.clone().multiply(step));
            cur[0] = next;
            moveFx(arrow, next);
            if (arrow != null && arrow.isValid()) tween(arrow, 1, 1, new Vector3f(), facing(d, 0), vec(.9f));
            w.spawnParticle(Particle.WITCH, next, 3, .2, .2, .2, 0);
            w.spawnParticle(Particle.DUST, next, 3, .25, .25, .25, 0, new Particle.DustOptions(violet, 1.6f));
            travelled[0] += step;
            return blocked || travelled[0] >= 30;
        }, () -> {
            w.spawnParticle(Particle.DUST, cur[0], 14, .4, .4, .4, 0, new Particle.DustOptions(violet, 1.8f));
            kill(arrow);
        });
    }

    /** 마력 집중. */
    private void manaFocus(Player p) {
        healBy(p, .35);
        giveAtkBuff(p, Color.fromRGB(176, 110, 255));
        snd(p.getWorld(), p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.5f);
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
        snd(w, p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1.8f);
        snd(w, p.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, .8f, 1.6f);
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

    /** The orb of the phantom staff beside the mage: every staff effect (charges, beams, shells) starts here. */
    private Location staffTip(Player p) { return muzzle(p, Job.MAGE); }

    /** Position on the beam axis `off` blocks in front of the staff tip (low / right of the eye so the view stays clear). */
    private Location cannonSpot(Player p, double off) {
        return staffTip(p).add(p.getEyeLocation().getDirection().normalize().multiply(off));
    }

    /** Teleports a display and tweens its transformation over dur ticks (no scheduler delay). */
    private void poseFx(ItemDisplay d, Location at, Quaternionf rot, Vector3f scale, int dur) {
        if (d == null || !d.isValid()) return;
        d.setTeleportDuration(Math.max(1, Math.min(dur, 59)));
        d.teleport(flat(at));
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(dur);
        d.setTransformation(new Transformation(new Vector3f(), rot, scale, new Quaternionf()));
    }

    /** Fixed muzzle point of the cannon: in front of where the caster stood, about 1.5 blocks above the feet. */
    private static Location cannonMuzzle(Location feet, Vector dir) {
        Vector f = dir.clone().setY(0);
        if (f.lengthSquared() < 1e-6) f = new Vector(0, 0, 1); else f.normalize();
        return feet.clone().add(0, 1.5, 0).add(f.multiply(1.2));
    }

    /** 마력포 wind-up: a spinning magic circle (plus a smaller counter-rotating one) grows in front of the caster; the client also draws a screen overlay (PIPI_CHARGE). */
    private void cannonChargeFx(Player p, int ticks) {
        World w = p.getWorld();
        p.sendMessage("PIPI_CHARGE:m_cannon:" + ticks);
        Location eye0 = p.getEyeLocation();
        Vector d0 = eye0.getDirection().normalize();
        ItemDisplay c1 = fx("mana_circle", bowSpot(eye0, d0), facing(d0, 0), vec(.3f), null, ticks + 6);
        ItemDisplay c2 = fx("mana_circle", bowSpot(eye0, d0).add(d0.clone().multiply(.5)), facing(d0, 0), vec(.2f), null, ticks + 6);
        run(1, ticks, i -> {
            if (!ready(p, w)) return true;
            Location eye = p.getEyeLocation();
            Vector d = eye.getDirection().normalize();
            double frac = Math.min(1, i / (double) Math.max(1, ticks - 2));
            double k = Math.max(0, Math.min(1, (frac - .8) / .2));
            Location a = bowSpot(eye, d), b = cannonMuzzle(p.getLocation(), d);
            Location at = a.clone().add(b.clone().subtract(a).toVector().multiply(k));
            float s1 = (float) ((.3 + 2.2 * frac) * (1 - .4 * k));
            poseFx(c1, at, facing(d, (float) (i * .12 * (1 + frac * 2))), vec(s1), 2);
            poseFx(c2, at.clone().add(d.clone().multiply(.5 + k * .8)), facing(d, (float) (-i * .2 * (1 + frac * 2))), vec(s1 * .6f), 2);
            if (c1 == null && i % 2 == 0) {
                Vector side = new Vector(-d.getZ(), 0, d.getX());
                if (side.lengthSquared() < 1e-6) side = new Vector(1, 0, 0); else side.normalize();
                Vector up = side.clone().crossProduct(d).normalize();
                double r = .3 + 1.1 * frac, ang = i * .3;
                for (int n = 0; n < 6; n++) {
                    double aa = ang + n * Math.PI / 3;
                    w.spawnParticle(Particle.DUST, at.clone().add(side.clone().multiply(Math.cos(aa) * r)).add(up.clone().multiply(Math.sin(aa) * r)), 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(176, 90, 255), 1.2f));
                }
            }
            if (i % 4 == 0) w.spawnParticle(Particle.END_ROD, at, 1, .6 * s1, .6 * s1, .6 * s1, 0);
            return false;
        }, () -> { kill(c1); kill(c2); p.sendMessage("PIPI_CHARGE:m_cannon:0"); });
    }

    /** 마력포: after the 3 s charge a fixed magic circle at the cast spot fires a hexagonal mana beam (20 blocks, fixed cast direction) that pulses 50% every 0.25 s for 5 s. */
    private void cannon(Player p) {
        World w = p.getWorld();
        double dmg = power(p, Job.MAGE) * .5;
        Color purple = Color.fromRGB(176, 90, 255);
        snd(w, p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, .5f, 1.6f);
        snd(w, p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, .8f, 1.4f);
        final Vector dir = p.getEyeLocation().getDirection().normalize();
        final Location tip = cannonMuzzle(p.getLocation(), dir);
        // beam model: 32 px (2 blocks at scale 1) along model Z, centred; stretched to the beam length with scale z = length / 2
        final Location root = tip.clone().add(dir.clone().multiply(-.45)); // the beam grows out of the circle itself
        ItemDisplay beam = fx("mana_beam", root, facing(dir, 0), new Vector3f(1.6f, 1.6f, .05f), null, 110);
        ItemDisplay c1 = fx("mana_circle", tip, facing(dir, 0), vec(1.5f), null, 110);
        ItemDisplay c2 = fx("mana_circle", tip.clone().add(dir.clone().multiply(.6)), facing(dir, 0), vec(.9f), null, 110);
        final boolean model = beam != null;
        run(1, 100, i -> {
            if (!ready(p, w)) return true;
            double len = blockLen(p, tip, dir, 20) + .45;
            double grow = Math.min(1, (i + 1) / 4.0), fade = i >= 95 ? Math.max(.04, (99 - i) / 5.0) : 1;
            if (model) {
                double vis = Math.max(.1, len * grow * fade);
                float th = (float) (1.6 * (1 + .1 * Math.sin(i * .9)) * (.3 + .7 * fade));
                poseFx(beam, root.clone().add(dir.clone().multiply(vis / 2)), facing(dir, (float) (i * 1.1)), new Vector3f(th, th, (float) (vis / 2)), 1); // fast spin
                // three helix strands of raw mana whirl around the beam out of the circle
                Vector bs = new Vector(-dir.getZ(), 0, dir.getX());
                if (bs.lengthSquared() < 1e-6) bs = new Vector(1, 0, 0); else bs.normalize();
                Vector bu = bs.clone().crossProduct(dir).normalize();
                for (int strand = 0; strand < 3; strand++) for (double s = 0; s <= Math.min(len, 12); s += .6) {
                    double a = i * .9 + s * 1.4 + strand * 2.094, rr = .95 + .15 * Math.sin(s + i);
                    w.spawnParticle(Particle.DUST, root.clone().add(dir.clone().multiply(s)).add(bs.clone().multiply(Math.cos(a) * rr)).add(bu.clone().multiply(Math.sin(a) * rr)), 1, 0, 0, 0, 0,
                        new Particle.DustOptions(strand == 0 ? Color.fromRGB(245, 225, 255) : purple, 1.1f));
                }
                if (i % 2 == 0) {
                    for (double s = 1; s <= len; s += 1.8) w.spawnParticle(Particle.END_ROD, tip.clone().add(dir.clone().multiply(s)), 1, .35, .35, .35, .02);
                }
            } else {
                for (double s = 0; s <= len; s += .6) {
                    Location at = tip.clone().add(dir.clone().multiply(s));
                    w.spawnParticle(Particle.DUST, at, 2, .18, .18, .18, 0, new Particle.DustOptions(purple, 2f));
                    if (((int) (s / .6)) % 3 == 0) w.spawnParticle(Particle.END_ROD, at, 1, .05, .05, .05, 0);
                }
            }
            float cs = (float) (Math.min(1, (i + 1) / 6.0) * fade);
            poseFx(c1, tip, facing(dir, (float) (i * .9)), vec(Math.max(.05f, 1.5f * cs)), 1);
            poseFx(c2, tip.clone().add(dir.clone().multiply(.6)), facing(dir, (float) (-i * 1.3)), vec(Math.max(.05f, .9f * cs)), 1);
            if (i % 2 == 0) w.spawnParticle(Particle.DUST, tip, 2, .3, .3, .3, 0, new Particle.DustOptions(Color.fromRGB(240, 220, 255), 1.6f));
            if (i % 10 == 0) snd(w, tip, Sound.BLOCK_BEACON_AMBIENT, .6f, 1.8f);
            if (i % 5 == 0) {
                for (LivingEntity t : along(p, tip, dir, len, 1.5)) {
                    hit(p, t, dmg);
                    w.spawnParticle(Particle.DUST, t.getBoundingBox().getCenter().toLocation(w), 8, .3, .4, .3, 0, new Particle.DustOptions(purple, 1.6f));
                }
            }
            return false;
        }, () -> { kill(beam); kill(c1); kill(c2); });
    }

    /** Dragon head spot: 1.2 blocks above the eye, slightly forward (horizontal), so nothing sits in front of the camera. */
    private static Location dragonMouth(Location eye, Vector dir) {
        Vector f = dir.clone().setY(0);
        if (f.lengthSquared() < 1e-6) f = new Vector(0, 0, 1); else f.normalize();
        return eye.clone().add(0, .9, 0).add(f.multiply(.7));
    }

    /** Direction from the raised mouth to the point the player looks at (about 12 blocks ahead). */
    private static Vector aimFrom(Location mouth, Location eye, Vector dir) {
        Vector v = eye.clone().add(dir.clone().multiply(12)).toVector().subtract(mouth.toVector());
        return v.lengthSquared() < 1e-6 ? dir.clone() : v.normalize();
    }

    /** 청룡의 진노: after the 3 s charge a dragon head appears above the cast spot (fixed, faces the cast direction) and breathes blue flame in a 16 block 60 degree cone for 5 s (45% / 0.25 s + burning). */
    private void dragon(Player p, Job job) {
        World w = p.getWorld();
        double dmg = power(p, job) * .45;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Location eye0 = p.getEyeLocation();
        Vector d0 = eye0.getDirection().normalize();
        Location mouth = dragonMouth(eye0, d0);
        Vector aim = aimFrom(mouth, eye0, d0);
        ItemDisplay head = fx("dragon_head", mouth, facing(aim, 0), vec(2.4f), null, 110);
        // breath plume model: narrow origin at model z=+4 (4/16 block*scale ahead of the display centre), widens toward the mouth direction; assumed ~32 model units long
        final float[] bs = {12.8f, 11.5f};
        final Location lip = mouth.clone().add(aim.clone().multiply(2.4 * 15.5 / 16.0)).add(0, .08, 0);
        ItemDisplay[] breath = new ItemDisplay[2];
        for (int k = 0; k < 2; k++) {
            breath[k] = fx("dragon_breath", lip.clone().subtract(aim.clone().multiply(4.0 * bs[k] / 16.0)), facing(aim, k * .8f), new Vector3f(bs[k] * .2f, bs[k] * .2f, bs[k] * .05f), null, 110);
            if (breath[k] != null) breathFx(breath[k], aim, bs[k], 1, bs[k] * .5f, bs[k] * .5f, bs[k] * .5f, k * .8f, 6);
        }
        snd(w, eye0, Sound.ENTITY_ENDER_DRAGON_GROWL, .5f, 1.4f);
        double cos = Math.cos(Math.toRadians(30));
        Color blue = Color.fromRGB(60, 150, 255), cyan = Color.fromRGB(110, 235, 255);
        run(1, 100, i -> {
            if (!ready(p, w)) return true;
            if (i % 3 == 0 && i >= 6 && i < 94) {
                double ph = i * .45;
                for (int k = 0; k < 2; k++) {
                    float sxy = bs[k] * (float) (.95 + .08 * Math.sin(ph + k * 1.7)), sz = bs[k] * (float) (.97 + .05 * Math.sin(ph * 1.3 + k));
                    breathFx(breath[k], aim, bs[k], 1, sxy, sxy, sz, (float) (k * .8 + Math.sin(ph + k) * .35 + i * .12), 3);
                }
            } else if (i == 94) {
                for (int k = 0; k < 2; k++) breathFx(breath[k], aim, bs[k], 1, bs[k] * .15f, bs[k] * .15f, bs[k] * .05f, k * .8f, 6);
            }
            int np = fxOk(breath) ? 4 : 10;
            for (int n = 0; n < np; n++) {
                Vector d = aim.clone().rotateAroundY(Math.toRadians(rnd.nextDouble(-30, 30)));
                Vector right = d.clone().crossProduct(new Vector(0, 1, 0));
                if (right.lengthSquared() > 1e-6) d.rotateAroundAxis(right.normalize(), Math.toRadians(rnd.nextDouble(-20, 20)));
                Location at = mouth.clone().add(d.multiply(.8 + rnd.nextDouble() * 15));
                if (n % 2 == 0) w.spawnParticle(job == Job.ARCHER ? Particle.ELECTRIC_SPARK : Particle.SOUL_FIRE_FLAME, at, 1, .1, .1, .1, .01);
                else w.spawnParticle(Particle.DUST, at, 1, .1, .1, .1, 0, new Particle.DustOptions(n % 4 == 1 ? blue : cyan, 1.4f));
            }
            if (i % 10 == 0) snd(w, mouth, Sound.ENTITY_BLAZE_SHOOT, .6f, .6f);
            if (i % 5 == 0) {
                for (LivingEntity t : near(p, mouth, 16)) {
                    if (!inCone(mouth, aim, t, cos, false)) continue;
                    hit(p, t, dmg);
                    if (t.isValid()) t.setFireTicks(40);
                }
            }
            return false;
        }, () -> { kill(head); kill(breath[0]); kill(breath[1]); });
    }

    private static boolean fxOk(ItemDisplay[] a) { return a[0] != null && a[0].isValid(); }

    /** Tweens a breath plume display (non-uniform scale, origin kept pinned at the mouth). */
    private void breathFx(ItemDisplay d, Vector aim, float base, int after, float sx, float sy, float sz, float roll, int dur) {
        if (d == null) return;
        Vector t = aim.clone().multiply(4.0 * (base - sz) / 16.0);
        tween(d, after, dur, new Vector3f((float) t.getX(), (float) t.getY(), (float) t.getZ()), facing(aim, roll), new Vector3f(sx, sy, sz));
    }

    // ---------------------------------------------------------------- 2026-11-20 skill rework (user's sheet)

    /**
     * 만월의 추억 (m_moon): after the 1 s charge a round full moon (fx_full_moon) appears high above the aimed point and plunges onto
     * it like a meteor (7 ticks); the impact flashes, throws a pale shockwave and hits everything within 4.5 blocks 3 times (140%).
     */
    private void moon(Player p) {
        World w = p.getWorld();
        Location c = aimPoint(p, 24);
        Vector back = p.getEyeLocation().getDirection().setY(0);
        if (back.lengthSquared() < 1e-6) back = new Vector(0, 0, 1);
        back.normalize().multiply(-6);
        Location from = c.clone().add(back).add(0, 22, 0), land = c.clone().add(0, 1.6, 0);
        double dmg = power(p, Job.MAGE) * 1.4;
        Color pale = Color.fromRGB(255, 244, 200), lav = Color.fromRGB(200, 170, 255);
        ItemDisplay moon = fx("full_moon", from, new Quaternionf(), vec(.2f), null, 30);
        if (moon != null) { moon.setGlowing(true); moon.setGlowColorOverride(Color.fromRGB(255, 246, 210)); }
        tween(moon, 1, 2, new Vector3f(), new Quaternionf(), vec(3.4f));
        snd(w, c, Sound.BLOCK_BEACON_POWER_SELECT, 1.2f, 1.8f);
        snd(w, c, Sound.ENTITY_WITHER_SHOOT, .6f, .6f);
        ring(c.clone().add(0, .1, 0), 4.5, 36, at -> w.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, new Particle.DustOptions(lav, 1.2f))); // where it lands
        Bukkit.getScheduler().runTaskLater(plugin, () -> fall(moon, from, land, 7, at -> {
            w.spawnParticle(Particle.DUST, at, 6, .9, .9, .9, 0, new Particle.DustOptions(pale, 1.8f));
            w.spawnParticle(Particle.END_ROD, at, 3, .7, .7, .7, .02);
            w.spawnParticle(Particle.DUST, at.clone().add(0, 1.5, 0), 4, .5, .8, .5, 0, new Particle.DustOptions(lav, 1.4f));
        }, () -> {
            kill(moon);
            snd(w, c, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, .8f);
            snd(w, c, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1.2f, .6f);
            w.spawnParticle(Particle.EXPLOSION_EMITTER, c.clone().add(0, .5, 0), 1, 0, 0, 0, 0);
            w.spawnParticle(Particle.DUST, c.clone().add(0, .6, 0), 60, 2.2, .5, 2.2, 0, new Particle.DustOptions(pale, 1.8f));
            shockwave(c, lav, pale, false);
            run(3, 3, k -> {
                for (LivingEntity t : near(p, c.clone().add(0, 1, 0), 4.5)) {
                    hit(p, t, dmg);
                    w.spawnParticle(Particle.END_ROD, t.getBoundingBox().getCenter().toLocation(w), 6, .3, .3, .3, .05);
                }
                return !p.isOnline();
            }, null);
        }), 3);
    }

    /** 광속 찌르기 (w_thrust, boss): an instant lunge that rips through everything in a 9 block line (45% x 5). */
    private void thrust(Player p) {
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        double len = blockLen(p, eye, dir, 9);
        List<LivingEntity> hits = along(p, eye, dir, len, 1.3);
        phantomAct(p, Job.WARRIOR, true);
        for (double s = .8; s <= len; s += .22) {
            Location c = eye.clone().add(dir.clone().multiply(s)).add(0, -.3, 0);
            w.spawnParticle(Particle.DUST, c, 1, 0, 0, 0, 0, new Particle.DustOptions(s > len - 1.5 ? Color.fromRGB(255, 90, 80) : Color.fromRGB(255, 246, 236), .5f));
        }
        Location tipAt = eye.clone().add(dir.clone().multiply(Math.max(1, len - .3))).add(0, -.3, 0);
        w.spawnParticle(Particle.CRIT, tipAt, 6, .1, .1, .1, .35);
        snd(w, eye, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.8f);
        snd(w, eye, Sound.ITEM_TRIDENT_THROW, .9f, 1.6f);
        Vector lunge = dir.clone().setY(0);
        if (lunge.lengthSquared() > 1e-4) { fallImmune.put(p.getUniqueId(), System.currentTimeMillis() + 1_500); p.setVelocity(lunge.normalize().multiply(1.1).setY(.12)); }
        double dmg = power(p, Job.WARRIOR) * .45;
        for (LivingEntity t : hits) {
            burst(p, t, dmg, 5);
            w.spawnParticle(Particle.SWEEP_ATTACK, t.getBoundingBox().getCenter().toLocation(w), 2, .2, .2, .2, 0);
        }
    }

    /** 유성우 (w_meteor, boss ult): for 5 s sword-energy meteors rain around the aimed point (radius 6), 70% in radius 2.5 per impact. */
    private void meteorShower(Player p) {
        Location c = aimPoint(p, 20);
        World w = c.getWorld();
        double dmg = power(p, Job.WARRIOR) * .7;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        snd(w, c, Sound.ENTITY_WITHER_SHOOT, .7f, 1.4f);
        snd(w, c, Sound.BLOCK_BEACON_POWER_SELECT, 1f, .7f);
        // judgement circle: golden octagon sigil on the ground (radius ~6), a counter-turning halo above, and one great sword planted in the middle
        sigil("ult_sigil_gold", c.clone().add(0, .06, 0), 4.1f, 104, .35f);
        ItemDisplay halo = sigil("ult_halo_gold", c.clone().add(0, 5.2, 0), 2.4f, 104, -.7f);
        Location top = c.clone().add(0, 12, 0), low = c.clone().add(0, .7, 0); // like 대검 꽂기: origin above ground so the point is buried
        ItemDisplay great = fx("giant_sword", top, new Quaternionf(), vec(1.4f), null, 110);
        if (great != null) { great.setTeleportDuration(4); Bukkit.getScheduler().runTaskLater(plugin, () -> moveFx(great, low), 1); }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            snd(w, c, Sound.ITEM_MACE_SMASH_GROUND, 1.2f, .7f);
            w.spawnParticle(Particle.FLASH, c.clone().add(0, .5, 0), 1, 0, 0, 0, 0);
            ring(c.clone().add(0, .2, 0), 2.5, 28, at -> w.spawnParticle(Particle.DUST, at, 1, 0, .05, 0, 0, new Particle.DustOptions(Color.fromRGB(255, 220, 120), 1.4f)));
        }, 5);
        Particle.DustOptions gold = new Particle.DustOptions(Color.fromRGB(255, 200, 60), 1.5f), pale = new Particle.DustOptions(Color.fromRGB(255, 241, 184), 1.1f);
        run(5, 20, i -> {
            if (!p.isOnline()) return true;
            if (halo != null && halo.isValid()) w.spawnParticle(Particle.DUST, c.clone().add(rnd.nextDouble(-2.5, 2.5), 5.2, rnd.nextDouble(-2.5, 2.5)), 1, 0, 0, 0, 0, pale);
            for (int s = 0; s < 3; s++) w.spawnParticle(Particle.DUST, c.clone().add(rnd.nextDouble(-1, 1), 1 + rnd.nextDouble() * 4, rnd.nextDouble(-1, 1)), 1, 0, .2, 0, 0, s == 0 ? pale : gold);
            for (int k = 0; k < 2; k++) {
                double a = rnd.nextDouble() * Math.PI * 2, r = Math.sqrt(rnd.nextDouble()) * 6;
                Location land = c.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r);
                Location from = land.clone().add(-3, 13, -3);
                Vector fallDir = land.toVector().subtract(from.toVector()).normalize();
                // 검기 유성: a red sword-aura crescent standing along its fall line, trailing red streaks
                ItemDisplay sword = fx("slash_red", from, facing(fallDir, (float) (Math.PI / 2)), new Vector3f(.8f, .35f, 1.3f), null, 12);
                fall(sword, from, land, 6, at -> {
                    w.spawnParticle(Particle.DUST, at, 3, .15, .15, .15, 0, new Particle.DustOptions(Color.fromRGB(255, 70, 60), 1.4f));
                    w.spawnParticle(Particle.DUST, at, 1, .05, .05, .05, 0, new Particle.DustOptions(Color.fromRGB(255, 220, 210), .9f));
                }, () -> {
                    kill(sword);
                    w.spawnParticle(Particle.EXPLOSION, land.clone().add(0, .4, 0), 1, 0, 0, 0, 0);
                    w.spawnParticle(Particle.DUST, land.clone().add(0, .3, 0), 24, 1.2, .2, 1.2, 0, new Particle.DustOptions(Color.fromRGB(255, 80, 60), 1.4f));
                    shockwave(land, Color.fromRGB(255, 90, 70), Color.fromRGB(255, 220, 200));
                    ring(land.clone().add(0, .15, 0), 1.6, 16, at -> w.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, pale));
                    snd(w, land, Sound.ENTITY_GENERIC_EXPLODE, .5f, 1.4f);
                    for (LivingEntity t : near(p, land, 2.5)) hit(p, t, dmg);
                });
            }
            return false;
        }, () -> kill(great));
    }

    /** 마력비 (m_mrain, boss): thin mana droplets rain on the aimed area (radius 4.5) for 3 s, 35% every 0.4 s. */
    private void manaRain(Player p) {
        Location c = aimPoint(p, 20);
        World w = c.getWorld();
        double dmg = power(p, Job.MAGE) * .35;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Color purple = Color.fromRGB(176, 100, 255), pink = Color.fromRGB(230, 160, 255);
        run(2, 30, i -> {
            if (!p.isOnline()) return true;
            w.spawnParticle(Particle.DUST, c.clone().add(0, 8, 0), 10, 3, .2, 3, 0, new Particle.DustOptions(purple, 2f));
            for (int k = 0; k < 8; k++) {
                double a = rnd.nextDouble() * Math.PI * 2, r = Math.sqrt(rnd.nextDouble()) * 4.5;
                Location top = c.clone().add(Math.cos(a) * r, 8, Math.sin(a) * r);
                for (double y = 0; y < 8; y += .5) w.spawnParticle(Particle.DUST, top.clone().add(0, -y, 0), 1, 0, 0, 0, 0, new Particle.DustOptions(k % 2 == 0 ? purple : pink, .7f));
            }
            if (i % 4 == 0) {
                snd(w, c, Sound.BLOCK_AMETHYST_BLOCK_STEP, .7f, 1.5f);
                for (LivingEntity t : near(p, c, 4.5)) hit(p, t, dmg);
            }
            return false;
        }, null);
    }

    /**
     * 마력창 (m_spear, boss): purple mana lances (fx_mana_lance) burst out of the ground one after another along a 24 block line
     * (90% each, knocks up). The line homes: every next lance steps toward the enemy nearest the crosshair (turning up to 35 degrees).
     */
    private void manaSpear(Player p) {
        World w = p.getWorld();
        Vector dir = p.getEyeLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 1e-6) dir = new Vector(0, 0, 1);
        dir.normalize();
        Location base = p.getLocation();
        double dmg = power(p, Job.MAGE) * .9;
        Location eye = p.getEyeLocation();
        LivingEntity lock = aimTarget(p, eye, eye.getDirection().normalize(), 24, true);
        Location[] at0 = {base.clone()};
        Vector[] dv = {dir.clone()};
        final double maxTurn = Math.toRadians(35);
        run(2, 12, i -> {
            if (!p.isOnline()) return true;
            if (lock != null && lock.isValid() && !lock.isDead()) { // steer toward the target
                Vector want = lock.getLocation().toVector().subtract(at0[0].toVector()).setY(0);
                if (want.lengthSquared() > 1) {
                    want.normalize();
                    double ang = dv[0].angle(want);
                    if (ang <= maxTurn) dv[0] = want;
                    else { double sgn = Math.signum(dv[0].getX() * want.getZ() - dv[0].getZ() * want.getX()); dv[0] = dv[0].clone().rotateAroundY(-sgn * maxTurn).normalize(); }
                }
            }
            Location at = at0[0].clone().add(dv[0].clone().multiply(2));
            at0[0] = at.clone();
            at.setY(w.getHighestBlockYAt(at) + 1);
            if (Math.abs(at.getY() - base.getY()) > 4) at.setY(base.getY());
            ItemDisplay spear = fx("mana_lance", at.clone().add(0, -1, 0), facing(new Vector(0, 1, 0), 0), vec(1.4f), new ItemStack(Material.AMETHYST_SHARD), 14);
            tween(spear, 1, 3, new Vector3f(0, 1.8f, 0), facing(new Vector(0, 1, 0), 0), vec(1.6f));
            tween(spear, 10, 3, new Vector3f(0, -.5f, 0), facing(new Vector(0, 1, 0), 0), vec(.2f));
            w.spawnParticle(Particle.DUST, at.clone().add(0, .8, 0), 16, .3, .8, .3, 0, new Particle.DustOptions(Color.fromRGB(176, 100, 255), 1.4f));
            w.spawnParticle(Particle.BLOCK, at, 10, .4, .1, .4, 0, Material.AMETHYST_BLOCK.createBlockData());
            snd(w, at, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, .9f, .8f);
            for (LivingEntity t : near(p, at.clone().add(0, 1, 0), 1.9)) {
                hit(p, t, dmg);
                if (t.isValid()) t.setVelocity(t.getVelocity().setY(.55));
            }
            return false;
        }, null);
    }

    /** 백호 강림 (a_tiger, boss buff): for 15 s a white tiger spirit adds an extra claw-arrow hit (60% x 2) to every basic attack. */
    private final Map<UUID, Long> tigerUntil = new HashMap<>();
    private void whiteTiger(Player p) {
        long until = System.currentTimeMillis() + CombatStats.duration(p, 15_000);
        tigerUntil.put(p.getUniqueId(), until);
        World w = p.getWorld();
        snd(w, p.getLocation(), Sound.ENTITY_POLAR_BEAR_WARNING, 1.2f, .7f); // 어흥: a deep growl (the ravager roar sounded like a villager)
        snd(w, p.getLocation(), Sound.ENTITY_WARDEN_ROAR, .25f, 1.6f);
        buffBurst(p, Color.fromRGB(235, 245, 255), Particle.END_ROD);
        run(10, 30, i -> {
            if (!p.isOnline() || p.isDead() || tigerUntil.getOrDefault(p.getUniqueId(), 0L) != until) return true;
            ring(p.getLocation().add(0, .15, 0), .9, 8, at -> w.spawnParticle(Particle.DUST, at.add(0, Math.random() * 1.6, 0), 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(220, 240, 255), 1f)));
            return false;
        }, null);
    }
    private void tigerExtra(Player p) {
        World w = p.getWorld();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!ready(p, w)) return;
            Location eye = p.getEyeLocation();
            Vector d = eye.getDirection().normalize(); // part of the basic attack: straight, no homing
            List<LivingEntity> hits = beam(p, eye, d, 25, .5, 1, Particle.END_ROD, Color.fromRGB(235, 245, 255));
            Location from = muzzle(p, Job.ARCHER);
            for (double s = 0; s <= Math.min(beamLen, 25); s += .5)
                w.spawnParticle(Particle.DUST, from.clone().add(toward(from, eye, d, beamLen).multiply(s)), 1, .04, .04, .04, 0, new Particle.DustOptions(Color.fromRGB(255, 255, 255), .9f));
            w.spawnParticle(Particle.END_ROD, from, 6, .15, .15, .15, .05);
            snd(w, eye, Sound.ENTITY_POLAR_BEAR_WARNING, .45f, .85f); // 어흥 (growl)
            for (LivingEntity t : hits) burst(p, t, power(p, Job.ARCHER) * .6, 2);
        }, 3);
    }

    /** 현무의 가오 (a_turtle, boss buff): 30 s of speed, +20% attack, -10% damage taken and regeneration. */
    private void blackTortoise(Player p) {
        long ms = CombatStats.duration(p, 30_000), until = System.currentTimeMillis() + ms;
        atkBuff.put(p.getUniqueId(), until);
        will.put(p.getUniqueId(), until);
        int ticks = (int) (ms / 50);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ticks, 0, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, ticks, 0, false, true));
        World w = p.getWorld();
        snd(w, p.getLocation(), Sound.ITEM_ARMOR_EQUIP_TURTLE, 1f, .8f);
        snd(w, p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, .6f, 1.3f);
        buffBurst(p, Color.fromRGB(40, 90, 70), Particle.HAPPY_VILLAGER);
        Color shell = Color.fromRGB(60, 120, 90);
        run(10, (int) Math.max(1, ms / 500), i -> {
            if (!p.isOnline() || p.isDead() || atkBuff.getOrDefault(p.getUniqueId(), 0L) != until) return true;
            Location c = p.getLocation();
            for (int k = 0; k < 6; k++) { double a = i * .4 + k * Math.PI / 3; w.spawnParticle(Particle.DUST, c.clone().add(Math.cos(a) * 1, 1.2, Math.sin(a) * 1), 1, 0, 0, 0, 0, new Particle.DustOptions(shell, 1.1f)); }
            return false;
        }, null);
    }

    /**
     * 분신 무기 (every class's basic attack): a small glowing phantom weapon (fx_phantom_sword / bow / staff - its own model, not the
     * held item) floats in front of the player's right side and does the basic attack from there: warrior red sword chops, archer
     * green bow shoots a green arrow, mage purple staff shoots a purple orb. It stays while the player keeps attacking (follows the
     * view every tick) and vanishes 2.5 s after the last basic attack. Returns the display (null when none could spawn).
     */
    private final Map<UUID, ItemDisplay> phantoms = new HashMap<>();
    private final Map<UUID, Long> phantomLast = new HashMap<>(), phantomSwing = new HashMap<>();
    private static final float PHANTOM_SCALE = .95f;
    private ItemDisplay phantom(Player p, Job job, Vector look) {
        UUID id = p.getUniqueId();
        phantomLast.put(id, System.currentTimeMillis());
        String model = switch (job) { case WARRIOR -> "phantom_sword"; case ARCHER -> "phantom_bow"; case MAGE -> "phantom_staff"; };
        ItemDisplay d = phantoms.get(id);
        if (d != null && d.isValid() && model.equals(d.getScoreboardTags().stream().filter(t -> t.startsWith("pipi_ph_")).map(t -> t.substring(8)).findFirst().orElse(""))) return d;
        kill(phantoms.remove(id));
        Location at = phantomSide(p, look);
        d = fx(model, at, phantomRot(job, look, 0), vec(.05f), new ItemStack(Material.AMETHYST_SHARD), 260);
        if (d == null) return null;
        d.addScoreboardTag("pipi_ph_" + model);
        tween(d, 0, 3, new Vector3f(), phantomRot(job, look, 0), vec(PHANTOM_SCALE)); // pop in
        Color col = phantomColor(job);
        p.getWorld().spawnParticle(Particle.DUST, at, 8, .2, .2, .2, 0, new Particle.DustOptions(col, 1f));
        phantoms.put(id, d);
        final ItemDisplay shown = d;
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override public void run() {
                long now = System.currentTimeMillis();
                if (!p.isOnline() || p.isDead() || !shown.isValid() || phantoms.get(id) != shown || now > phantomSwing.getOrDefault(id, 0L) + 150 && now - phantomLast.getOrDefault(id, 0L) > 300 || !p.getWorld().equals(shown.getWorld())) {
                    if (phantoms.get(id) == shown) phantoms.remove(id);
                    kill(shown); cancel(); return;
                }
                Vector lk = p.getEyeLocation().getDirection().normalize();
                shown.teleport(flat(phantomSide(p, lk)));
                if (now > phantomSwing.getOrDefault(id, 0L)) {
                    shown.setInterpolationDelay(0); shown.setInterpolationDuration(2);
                    shown.setTransformation(new Transformation(new Vector3f(), phantomRot(job, lk, 0), vec(PHANTOM_SCALE), new Quaternionf()));
                }
                if (now % 400 < 50) shown.getWorld().spawnParticle(Particle.DUST, shown.getLocation(), 1, .1, .15, .1, 0, new Particle.DustOptions(col, .6f));
            }
        }.runTaskTimer(plugin, 1, 1);
        return d;
    }

    /**
     * The phantom weapon beside the player swings (basic attacks and skills): the red sword chops diagonally (alternating sides),
     * the green bow kicks back with the shot, the purple staff thrusts forward with a twist. The follow-the-view tick waits until
     * the swing is over.
     */
    private boolean phantomFlip;
    private void phantomAct(Player p, Job job) { phantomAct(p, job, phantomFlip = !phantomFlip); }

    private void phantomAct(Player p, Job job, boolean flip) {
        Vector look = p.getEyeLocation().getDirection().normalize();
        ItemDisplay g = phantom(p, job, look);
        if (g == null) return;
        phantomSwing.put(p.getUniqueId(), System.currentTimeMillis() + 420);
        Vector f = look.clone().setY(0);
        if (f.lengthSquared() < 1e-6) f = new Vector(0, 0, 1); else f.normalize();
        Vector r = new Vector(-f.getZ(), 0, f.getX());
        java.util.function.BiFunction<Double, Double, Vector3f> off = (side, up) -> new Vector3f((float) (r.getX() * side), (float) (double) up, (float) (r.getZ() * side));
        Vector3f back = new Vector3f((float) -look.getX(), (float) -look.getY(), (float) -look.getZ());
        switch (job) {
            case WARRIOR -> {
                // the sword (anchored on the left) is raised on one side, then travels across the front along the cut to the other
                // side, blade leading, and floats back. flip = from upper-left to lower-right, else upper-right to lower-left.
                double from = flip ? -.2 : 2.5, to = flip ? 2.5 : -.2;
                float roll = flip ? .9f : -.9f;
                tween(g, 0, 1, new Vector3f(off.apply(from, .75)).add(new Vector3f(back).mul(.2f)), phantomRot(job, look, roll).rotateX(-1.1f), vec(PHANTOM_SCALE));
                tween(g, 2, 3, new Vector3f(off.apply(to, -.55)).add(new Vector3f(back).mul(-.6f)), phantomRot(job, look, -roll).rotateX(1.5f), vec(PHANTOM_SCALE * 1.1f));
                tween(g, 6, 4, new Vector3f(), phantomRot(job, look, 0), vec(PHANTOM_SCALE));
            }
            case ARCHER -> {
                tween(g, 0, 1, new Vector3f(back).mul(.35f), phantomRot(job, look, 0).rotateX(.25f), vec(PHANTOM_SCALE * 1.05f));
                tween(g, 2, 3, new Vector3f(), phantomRot(job, look, 0), vec(PHANTOM_SCALE));
            }
            case MAGE -> {
                tween(g, 0, 1, new Vector3f(back).mul(.25f), phantomRot(job, look, .25f).rotateX(-.15f), vec(PHANTOM_SCALE));      // draw back
                tween(g, 2, 2, new Vector3f(back).mul(-.55f), phantomRot(job, look, -.25f).rotateX(.12f), vec(PHANTOM_SCALE * 1.08f)); // orb-first thrust
                tween(g, 5, 3, new Vector3f(), phantomRot(job, look, 0), vec(PHANTOM_SCALE));
            }
        }
    }

    /** Wind-up: the phantom weapon appears, draws back and glows in the class colour until the cast (then the cast's own act plays). */
    private void phantomHold(Player p, Job job, int ticks) {
        Vector look = p.getEyeLocation().getDirection().normalize();
        ItemDisplay g = phantom(p, job, look);
        if (g == null) return;
        phantomSwing.put(p.getUniqueId(), System.currentTimeMillis() + ticks * 50L + 150);
        Vector3f back = new Vector3f((float) -look.getX(), (float) -look.getY(), (float) -look.getZ());
        tween(g, 0, Math.max(1, ticks), new Vector3f(back).mul(.3f), phantomRot(job, look, 0), vec(PHANTOM_SCALE * 1.06f));
        g.setGlowing(true);
        g.setGlowColorOverride(phantomColor(job));
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (g.isValid()) g.setGlowing(false); }, Math.max(1, ticks));
        if (job == Job.MAGE) { // motes spiral into the orb
            run(1, ticks, i -> {
                if (!g.isValid() || !p.isOnline()) return true;
                Location c = muzzle(p, Job.MAGE);
                double rad = .8 * (1 - i / (double) ticks) + .1;
                for (int k = 0; k < 3; k++) {
                    double b = i * .9 + k * 2.094;
                    p.getWorld().spawnParticle(Particle.DUST, c.clone().add(Math.cos(b) * rad, Math.sin(b * 1.3) * rad * .6, Math.sin(b) * rad), 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(190, 120, 255), .8f));
                }
                return false;
            }, null);
        }
    }

    /** Archer basic charge: the phantom bow draws back and glows while green motes gather on it. */
    private void phantomDraw(Player p, int ticks) {
        Vector look = p.getEyeLocation().getDirection().normalize();
        ItemDisplay g = phantom(p, Job.ARCHER, look);
        if (g == null) return;
        phantomSwing.put(p.getUniqueId(), System.currentTimeMillis() + ticks * 50L + 100);
        Vector3f back = new Vector3f((float) -look.getX(), (float) -look.getY(), (float) -look.getZ());
        tween(g, 0, ticks, new Vector3f(back).mul(.3f), phantomRot(Job.ARCHER, look, 0), vec(PHANTOM_SCALE * 1.08f));
        g.setGlowing(true);
        g.setGlowColorOverride(Color.fromRGB(120, 255, 140));
        run(1, ticks, i -> {
            if (!g.isValid()) return true;
            Location c = g.getLocation();
            double a = i * .9, rad = .7 * (1 - i / (double) ticks) + .1;
            for (int k = 0; k < 3; k++) {
                double b = a + k * 2.094;
                p.getWorld().spawnParticle(Particle.DUST, c.clone().add(Math.cos(b) * rad, Math.sin(b) * rad * .6, Math.sin(b) * rad), 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(120, 255, 140), .7f));
            }
            if (i == ticks - 1) g.setGlowing(false);
            return false;
        }, null);
    }

    /** Visual launch point of archer / mage attacks: the tip of the phantom weapon beside the player (the bow's arrow rest, the staff's orb). */
    private static Location muzzle(Player p, Job job) {
        Vector look = p.getEyeLocation().getDirection().normalize();
        Location a = phantomSide(p, look);
        return job == Job.MAGE ? a.add(look.clone().multiply(.38)).add(0, .17, 0) : a.add(look.clone().multiply(.25));
    }

    /** Unit direction from {@code from} to the end of the eye ray (eye + dir * len). */
    private static Vector toward(Location from, Location eye, Vector dir, double len) {
        Vector v = eye.toVector().add(dir.clone().multiply(len)).subtract(from.toVector());
        return v.lengthSquared() < 1e-6 ? dir.clone() : v.normalize();
    }

    /** A skill arrow leaving the phantom bow toward the end of the eye ray (visual; hits are traced from the eye). */
    private void bowArrow(Player p, Location eye, Vector dir, double len, float scale, int ticks) {
        phantomAct(p, Job.ARCHER, false);
        Location from = muzzle(p, Job.ARCHER);
        bigArrow(from, toward(from, eye, dir, len), scale, from.distance(eye.clone().add(dir.clone().multiply(len))), ticks);
    }

    private static Quaternionf phantomRot(Job job, Vector look, float roll) {
        Quaternionf q = facing(look, roll);
        return job == Job.ARCHER ? q.rotateY((float) Math.PI) : job == Job.MAGE ? q.rotateX(1.15f) : q;
    }

    private static Color phantomColor(Job job) {
        return switch (job) { case WARRIOR -> Color.fromRGB(255, 60, 60); case ARCHER -> Color.fromRGB(90, 230, 110); case MAGE -> Color.fromRGB(176, 90, 255); };
    }

    /** Where the phantom weapon floats: in front of the player's left side, near eye level (clear of the held weapon on the right). */
    private static Location phantomSide(Player p, Vector look) {
        Vector f = look.clone().setY(0);
        if (f.lengthSquared() < 1e-6) f = new Vector(0, 0, 1); else f.normalize();
        Vector right = new Vector(-f.getZ(), 0, f.getX());
        return p.getEyeLocation().add(right.multiply(-1.15)).add(f.multiply(1.6)).add(0, -.2, 0);
    }

    /** Archer basic: the green phantom bow beside the archer shoots a green arrow at the aimed point (45% x 2, 25 blocks). */
    private static final int ARCHER_CHARGE = 8;
    private void phantomArrow(Player p) {
        phantomDraw(p, ARCHER_CHARGE);
        snd(p.getWorld(), p.getLocation(), Sound.ITEM_CROSSBOW_LOADING_MIDDLE, .6f, 1.4f);
        World startWorld = p.getWorld();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && !p.isDead() && p.getWorld().equals(startWorld) && combatJob(p) == Job.ARCHER) looseArrow(p);
        }, ARCHER_CHARGE);
    }

    private void looseArrow(Player p) {
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector look = eye.getDirection().normalize();
        Location from = muzzle(p, Job.ARCHER);
        var hitPos = w.rayTraceBlocks(eye, look, 25, FluidCollisionMode.NEVER, true);
        Location goal = hitPos == null ? eye.clone().add(look.clone().multiply(25)) : hitPos.getHitPosition().toLocation(w); // basic: straight, no homing
        Vector d = goal.toVector().subtract(from.toVector());
        if (d.lengthSquared() < 1e-6) d = look.clone(); else d.normalize();
        phantomAct(p, Job.ARCHER, false);
        List<LivingEntity> hits = beam(p, from, d, 26, .5, 1, Particle.HAPPY_VILLAGER, Color.fromRGB(90, 230, 110));
        bigArrow(from.clone().add(d.clone().multiply(.5)), d, .3f, beamLen, 4);
        snd(w, from, Sound.ENTITY_ARROW_SHOOT, .7f, 1.3f);
        double dmg = power(p, Job.ARCHER) * BASIC_ARCHER;
        for (LivingEntity t : hits) burst(p, t, dmg, 2);
    }

    /** Mage basic: the purple phantom staff beside the mage shoots a purple orb (40% x 3). */
    private void phantomOrb(Player p) {
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector look = eye.getDirection().normalize();
        Location from = muzzle(p, Job.MAGE);
        var hitPos = w.rayTraceBlocks(eye, look, 20, FluidCollisionMode.NEVER, true);
        Location goal = hitPos == null ? eye.clone().add(look.clone().multiply(20)) : hitPos.getHitPosition().toLocation(w); // basic: straight, no homing
        Vector d = goal.toVector().subtract(from.toVector());
        if (d.lengthSquared() < 1e-6) d = look.clone(); else d.normalize();
        phantomAct(p, Job.MAGE);
        w.spawnParticle(Particle.WITCH, from, 5, .1, .1, .1, 0);
        snd(w, from, Sound.BLOCK_AMETHYST_BLOCK_CHIME, .6f, 1.6f);
        snd(w, from, Sound.ENTITY_EVOKER_CAST_SPELL, .3f, 1.7f);
        orb(p, from.clone().add(d.clone().multiply(.5)), d, 1.5, 14, power(p, Job.MAGE) * BASIC_MAGE, null, null, .7f);
    }

    /**
     * 청룡의 진노 (a_dragon, archer boss ult): after the 3 s charge, blue lightning bolts (fx_blue_bolt: glowing orb, jagged bolt,
     * spark splash) strike all around the archer for 5 s - 2 bolts every 0.2 s within 9 blocks, aimed at enemies when there are any
     * (45% in radius 1.8 per bolt).
     */
    private void blueThunder(Player p) {
        World w = p.getWorld();
        double dmg = power(p, Job.ARCHER) * .45;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Color cyan = Color.fromRGB(98, 214, 255), pale = Color.fromRGB(200, 246, 255);
        snd(w, p.getLocation(), Sound.ITEM_TRIDENT_THUNDER, .7f, 1.4f);
        snd(w, p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, .3f, 1.8f);
        run(4, 25, i -> {
            if (!ready(p, w)) return true;
            Location c = p.getLocation();
            List<LivingEntity> foes = near(p, c, 11.7);
            for (int k = 0; k < 2; k++) {
                Location at;
                if (!foes.isEmpty()) at = foes.get(rnd.nextInt(foes.size())).getLocation(); // homing: an enemy nearby is always struck
                else { double a = rnd.nextDouble() * Math.PI * 2, r = 3.2 + rnd.nextDouble() * 8.5; at = c.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r); }
                int gy = w.getHighestBlockYAt(at);
                if (Math.abs(gy + 1 - c.getY()) <= 5) at.setY(gy + 1);
                // a long bolt straight out of the sky (~12 blocks: model foot y -8 sits on the ground at y-scale ys), full size at once,
                // one flicker, gone after 4 ticks
                float ys = 4.9f + rnd.nextFloat() * .6f, xs = 1.5f + rnd.nextFloat() * .3f;
                Quaternionf yaw = new Quaternionf().rotateY((float) (rnd.nextDouble() * Math.PI * 2));
                ItemDisplay bolt = fx("blue_bolt", at.clone().add(0, ys, 0), yaw, new Vector3f(xs, ys, xs), null, 3); // removed the tick it has shrunk
                tween(bolt, 1, 1, new Vector3f(), new Quaternionf(yaw).rotateY(.5f), new Vector3f(xs * 1.2f, ys, xs * 1.2f)); // flicker
                tween(bolt, 2, 1, new Vector3f(), yaw, new Vector3f(xs * .15f, ys, xs * .15f));                                // snap out, then gone
                Bukkit.getScheduler().runTaskLater(plugin, () -> kill(bolt), 3); // fx() keeps displays at least 10 ticks - remove it right away
                float s = 2.4f;
                Location foot = at.clone().add(0, .2, 0);
                w.spawnParticle(Particle.ELECTRIC_SPARK, foot, 20, .6, .2, .6, .25);
                w.spawnParticle(Particle.DUST, foot, 14, .7, .15, .7, 0, new Particle.DustOptions(cyan, 1.6f));
                w.spawnParticle(Particle.DUST, at.clone().add(0, s * 2.4, 0), 6, .2, .2, .2, 0, new Particle.DustOptions(pale, 2f));
                w.spawnParticle(Particle.FLASH, at.clone().add(0, 1, 0), 1, 0, 0, 0, 0);
                for (LivingEntity t : near(p, at, 1.8)) hit(p, t, dmg);
            }
            if (i % 2 == 0) snd(w, c, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, .45f, 1.5f + rnd.nextFloat() * .4f);
            return false;
        }, null);
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
        snd(w, origin, Sound.ENTITY_BAT_TAKEOFF, .8f, 1.4f);
        snd(w, origin, Sound.ITEM_TRIDENT_RIPTIDE_1, .5f, 1.5f);
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

    /** Basic attack coefficients by class concept (fraction of attack power). */
    private static final double BASIC_WARRIOR = 1.1, BASIC_ARCHER = .9, BASIC_MAGE = 1.2;

    /** playSound with volume capped at 1 and a slight random pitch so repeats do not sound identical. */
    private static void snd(World w, Location at, Sound sound, float vol, float pitch) {
        w.playSound(at, sound, Math.min(vol, 1f), pitch * (.94f + ThreadLocalRandom.current().nextFloat() * .12f));
    }

    private final Map<UUID, Long> hitSoundAt = new HashMap<>();

    /** Class-themed impact sound on a skill/basic hit, at most one every 150 ms per player. */
    private void hitSound(Player p, LivingEntity target) {
        Job j = combatJob(p);
        if (j == null) return;
        long now = System.currentTimeMillis();
        if (now - hitSoundAt.getOrDefault(p.getUniqueId(), 0L) < 150) return;
        hitSoundAt.put(p.getUniqueId(), now);
        Location at = target.getLocation().add(0, target.getHeight() / 2, 0);
        World w = target.getWorld();
        switch (j) {
            case WARRIOR -> snd(w, at, Sound.ENTITY_PLAYER_ATTACK_STRONG, .7f, 1f);
            case ARCHER -> snd(w, at, Sound.ENTITY_ARROW_HIT, .8f, 1f);
            case MAGE -> snd(w, at, Sound.BLOCK_AMETHYST_CLUSTER_HIT, .7f, 1.3f);
        }
    }

    private void hit(Player p, LivingEntity target, double damage) {
        if (!p.isOnline() || !target.isValid() || target.isDead()) return;
        hitSound(p, target);
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
        // archer / mage: the trail is drawn from the phantom weapon to where the eye ray ends (hits are still traced from the eye)
        Job job = combatJob(p);
        Location vis = job == Job.ARCHER || job == Job.MAGE ? muzzle(p, job) : start;
        Location end = start.clone().add(dir.clone().multiply(len));
        Vector vdir = toward(vis, start, dir, len);
        double vlen = vis.distance(end);
        for (double s = .5; s <= vlen; s += .5) {
            Location c = vis.clone().add(vdir.clone().multiply(s));
            w.spawnParticle(particle, c, 1, 0, 0, 0, 0);
            w.spawnParticle(Particle.DUST, c, 1, .05, .05, .05, 0, new Particle.DustOptions(dust, 1f));
        }
        return hits;
    }

    private boolean autoAim() { return plugin.getConfig().getBoolean("combat.auto-aim", true); }

    /** Valid auto-aim candidate: an enemy that is not one of the caster's own entities. */
    private boolean aimable(Player p, Entity e) {
        if (!enemy(p, e) || e.getScoreboardTags().contains("pipi_pet_worker") || e.getScoreboardTags().contains("pipi_noaim")) return false; // raid vessels / orbs / crystals are never homed onto
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

    /** Flat yaw rotation (plus a roll around the facing axis) for the horizontal fx_ult_* planes; texture top points along the yaw. */
    private static Quaternionf flatRot(float yaw, float roll) { return new Quaternionf().rotateY(yaw).rotateZ(roll); }

    /**
     * Ultimate crescent: a flat gradient crescent (fx_ult_crescent) lying around {@code at}, swinging ~75 degrees in {@code spin}
     * direction while it grows to {@code scale} (scale 1 = outer radius ~1.4 blocks), then flares wider and is gone (~8 ticks).
     */
    private void crescentSweep(Location at, float yaw, float roll, float spin, float scale) {
        ItemDisplay d = fx("ult_crescent", at, flatRot(yaw - .75f * spin, roll), new Vector3f(scale * .55f, 1f, scale * .55f), null, 8);
        tween(d, 1, 3, new Vector3f(), flatRot(yaw + .25f * spin, roll), new Vector3f(scale, 1f, scale));
        tween(d, 4, 3, new Vector3f(), flatRot(yaw + .6f * spin, roll), new Vector3f(scale * 1.12f, 1f, scale * 1.12f));
    }

    /**
     * Ground sigil (fx_ult_sigil_gold / ult_sigil_green, scale 1 = 3 blocks across): unfolds from small in 4 ticks, turns slowly
     * for {@code life} ticks, then folds away. Returns the display (may be null) so callers can move it.
     */
    private ItemDisplay sigil(String id, Location at, float scale, int life, float turnPerSec) {
        ItemDisplay d = fx(id, at, flatRot(0, 0), vec(scale * .2f), null, life + 6);
        if (d == null) return null;
        tween(d, 1, 4, new Vector3f(), flatRot(.4f * Math.signum(turnPerSec), 0), vec(scale));
        int steps = Math.max(0, (life - 6) / 20);
        for (int s = 1; s <= steps; s++) tween(d, 5 + (s - 1) * 20, 20, new Vector3f(), flatRot(.4f * Math.signum(turnPerSec) + turnPerSec * s, 0), vec(scale));
        tween(d, life, 5, new Vector3f(), flatRot(.4f * Math.signum(turnPerSec) + turnPerSec * (steps + .3f), 0), vec(scale * .05f));
        return d;
    }

    /** Light pillar (fx_ult_beam_green) shooting up from {@code at} to {@code height} blocks, held for {@code hold} ticks, then thinning out. */
    private void lightPillar(Location at, float width, float height, int hold) {
        ItemDisplay d = fx("ult_beam_green", at, new Quaternionf(), new Vector3f(width * .3f, .1f, width * .3f), null, hold + 12);
        float sy = height / 1.5f;
        tween(d, 1, 3, new Vector3f(), new Quaternionf(), new Vector3f(width, sy, width));
        tween(d, 4 + hold, 6, new Vector3f(), new Quaternionf().rotateY(.8f), new Vector3f(.02f, sy * 1.1f, .02f));
    }

    /** Short-lived slash (gone after ~6 ticks) for the basic attack. */
    /**
     * Warrior basic cut: a single razor-thin red line (~4 blocks, diagonal, alternating direction) that snaps across at the far end
     * of the reach, with a few sparks. No wide arc.
     */
    private void lineSlash(Location at, Vector dir, Vector side, boolean flip) {
        World w = at.getWorld();
        float tilt = flip ? .55f : -.55f;
        ItemDisplay d = fx("slash_red", at, facing(dir, tilt).rotateY(flip ? -.25f : .25f), new Vector3f(.35f, .07f, .06f), null, 6);
        tween(d, 1, 2, new Vector3f(), facing(dir, tilt), new Vector3f(1.45f, .085f, .08f));
        tween(d, 3, 3, new Vector3f(), facing(dir, tilt).rotateY(flip ? .15f : -.15f), new Vector3f(1.6f, .03f, .05f));
        Vector up = new Vector(0, 1, 0);
        for (double k = -1; k <= 1.001; k += .5) {
            Vector off = side.clone().multiply(k * 1.9 * (flip ? 1 : -1)).add(up.clone().multiply(-k * 1.1));
            w.spawnParticle(Particle.DUST, at.clone().add(off), 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(255, 90, 90), .5f));
        }
        w.spawnParticle(Particle.CRIT, at, 4, .4, .25, .4, .12);
    }

/**
     * Impact shockwave: a flat ring (fx_shockwave) bursts out along the ground and fades, a dust ring races outward in 3 steps,
     * ground chunks are thrown up and a short flash at the centre.
     */
    private void shockwave(Location land, Color main, Color pale) { shockwave(land, main, pale, true); }

    private void shockwave(Location land, Color main, Color pale, boolean flash) {
        World w = land.getWorld();
        Location g = land.clone().add(0, .12, 0);
        ItemDisplay ring = fx("shockwave", g, new Quaternionf(), new Vector3f(.3f, .3f, .3f), null, 8);
        tween(ring, 1, 3, new Vector3f(), new Quaternionf().rotateY(.6f), new Vector3f(1.6f, .5f, 1.6f));
        tween(ring, 4, 3, new Vector3f(), new Quaternionf().rotateY(1f), new Vector3f(2.2f, .1f, 2.2f));
        for (int k = 0; k < 3; k++) {
            final double rr = .8 + k * .9;
            Bukkit.getScheduler().runTaskLater(plugin, () -> ring(g, rr, 18 + (int) (rr * 6), at -> w.spawnParticle(Particle.DUST, at, 1, 0, .04, 0, 0, new Particle.DustOptions(rr < 2 ? pale : main, 1.5f))), k + 1L);
        }
        var under = g.clone().add(0, -1, 0).getBlock().getBlockData();
        if (!under.getMaterial().isAir()) w.spawnParticle(Particle.BLOCK, g, 22, .9, .15, .9, .2, under);
        if (flash) w.spawnParticle(Particle.FLASH, g.clone().add(0, .3, 0), 1, 0, 0, 0, 0);
        w.spawnParticle(Particle.CLOUD, g, 8, .6, .05, .6, .06);
    }

    /** Warrior basic cut: a wide, razor-thin red blade arc (thin height, long width), swept across in ~5 ticks. */
    private void thinSlash(Location at, Vector dir, float scale, float roll, float sweep) {
        Quaternionf base = facing(dir, roll);
        ItemDisplay d = fx("slash_red", at, new Quaternionf(base).rotateY(-sweep), new Vector3f(scale * .5f, scale * .05f, scale * .5f), null, 6);
        tween(d, 1, 2, new Vector3f(), new Quaternionf(base).rotateY(sweep * .6f), new Vector3f(scale, scale * .09f, scale * 1.1f));
        tween(d, 3, 2, new Vector3f(), new Quaternionf(base).rotateY(sweep), new Vector3f(scale * 1.1f, scale * .03f, scale * 1.2f));
    }

    private void slashQuick(String id, Location at, Vector dir, float scale, float roll, float sweep) {
        Quaternionf base = facing(dir, roll);
        ItemDisplay d = fx(id, at, new Quaternionf(base).rotateY(-sweep), new Vector3f(scale * .45f, scale * .45f, scale * .45f), null, 6);
        tween(d, 1, 2, new Vector3f(), new Quaternionf(base).rotateY(sweep * .6f), vec(scale));
        tween(d, 3, 2, new Vector3f(), new Quaternionf(base).rotateY(sweep), new Vector3f(scale * 1.05f, scale * .2f, scale * 1.05f));
    }

    /** Razor-thin claw streak: long along the sweep, very thin in height, tilted to rake diagonally, gone in ~5 ticks. */
    private void clawStreak(String id, Location at, Vector dir, float scale, float roll, float sweep) {
        Quaternionf base = facing(dir, roll);
        ItemDisplay d = fx(id, at, new Quaternionf(base).rotateY(-sweep), new Vector3f(scale * .5f, scale * .03f, scale * .5f), null, 5);
        tween(d, 1, 2, new Vector3f(), new Quaternionf(base).rotateY(sweep * .6f), new Vector3f(scale, scale * .07f, scale * 1.15f));
        tween(d, 3, 2, new Vector3f(), new Quaternionf(base).rotateY(sweep), new Vector3f(scale * 1.05f, scale * .02f, scale * 1.3f));
    }

    /**
     * Razor-thin red claw wound: ~9 blocks long, almost no thickness (thin axes are absolute values, not tied to the length),
     * stretches out in 2 ticks, gone within 5. Crimson dust and a few bright sparks along the line, no particle clouds.
     */
    private void clawSlash(Location at, Vector dir, Vector right, float tilt) {
        World w = at.getWorld();
        Quaternionf base = facing(dir, tilt);
        // slash_red spans ~3 blocks at scale 1 along its width axis: x 3.0 -> ~9 blocks; y/z stay very small so it reads as a blade line
        ItemDisplay d = fx("slash_red", at, new Quaternionf(base).rotateY(-.35f), new Vector3f(1.3f, .2f, .1f), null, 5);
        tween(d, 1, 2, new Vector3f(), new Quaternionf(base).rotateY(.1f), new Vector3f(3.0f, .25f, .16f));
        tween(d, 3, 2, new Vector3f(), new Quaternionf(base).rotateY(.35f), new Vector3f(3.1f, .06f, .08f));
        Vector up = new Vector(0, 1, 0);
        Particle.DustOptions dark = new Particle.DustOptions(Color.fromRGB(120, 8, 14), .9f);
        Particle.DustOptions red = new Particle.DustOptions(Color.fromRGB(220, 25, 30), .7f);
        double ct = Math.cos(tilt), st = Math.sin(tilt);
        for (int k = -9; k <= 9; k++) {
            double t = k / 9.0;
            Location pt = at.clone().add(right.clone().multiply(t * 4.5 * ct)).add(up.clone().multiply(t * 4.5 * st * .6)).add(dir.clone().multiply((1 - t * t) * .6));
            w.spawnParticle(Particle.DUST, pt, 1, 0, 0, 0, 0, dark);
            w.spawnParticle(Particle.DUST, pt.clone().add(0, .05, 0), 1, 0, 0, 0, 0, red);
            if (k % 4 == 0) w.spawnParticle(Particle.CRIT, pt, 1, .02, .02, .02, .02);
            if (k % 6 == 0) w.spawnParticle(Particle.DUST, pt, 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(255, 235, 235), .6f));
        }
    }

    /** Big arrow flying from the start along dir for dist blocks over the given ticks, then gone. */
    private void bigArrow(Location from, Vector dir, float scale, double dist, int ticks) {
        projectileFx("arrow_big", from, dir, scale, dist, ticks);
    }

    /** Same as bigArrow for any model id that points to its forward axis like arrow_big (falls back to a vanilla arrow). */
    private void projectileFx(String id, Location from, Vector dir, float scale, double dist, int ticks) {
        ItemDisplay a = fx(id, from, facing(dir, 0), vec(scale), new ItemStack(Material.ARROW), ticks + 4);
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
