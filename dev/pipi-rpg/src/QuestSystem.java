package dev.pipi.life;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Story and quest lines (quests.yml, copied to plugins/PipiLife/quests.yml): the main story told by the 촌장 in six chapters,
 * class and life lines, and three daily quests. Lines advance one quest at a time; a quest whose objectives are all met is
 * "ready" and is claimed by talking to the 촌장 or from the quest window (U, PipiTravel QuestScreen).
 * Progress lives in the plugin data under quest.&lt;uuid&gt; (done list, per-objective counts, tracked quest, daily picks).
 * Client lines: PIPI_QLOG:&lt;json&gt; (the quest window) on request / change; the HUD objective panel goes through HudSync.
 */
final class QuestSystem implements Listener {
    record Obj(String type, String arg, int need, boolean classOnly, String text) {
        boolean counted() { return switch (type) { case "capture", "kill", "mine", "fish", "research", "assign" -> true; default -> false; }; }
    }
    record Quest(String id, String line, String chapter, String title, String giver, String desc, List<Obj> objs,
                 long gold, int steel, int vouchers, List<String> outro) {}
    record Chapter(String id, String name, List<String> intro) {}

    static final List<String> LINES = List.of("main", "class", "life");
    private static final String[] LINE_NAMES = {"메인", "직업", "생활"};
    private static final int DAILY_COUNT = 3;
    private final PipiLife plugin;
    private final TownSystems town;
    private final Map<String, Quest> quests = new LinkedHashMap<>();
    private final Map<String, List<Quest>> lines = new LinkedHashMap<>();
    private final List<Quest> daily = new ArrayList<>();
    private final Map<String, Chapter> chapters = new LinkedHashMap<>();
    private final Map<UUID, Long> notified = new HashMap<>();
    private boolean dirty;

    QuestSystem(PipiLife plugin, TownSystems town) {
        this.plugin = plugin;
        this.town = town;
        load();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 60L, 40L);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> { if (dirty) { dirty = false; plugin.saveData(); } }, 1200L, 1200L);
    }

    // ------------------------------------------------------------------ data file
    String load() {
        quests.clear(); lines.clear(); daily.clear(); chapters.clear();
        File file = new File(plugin.getDataFolder(), "quests.yml");
        if (!file.exists()) plugin.saveResource("quests.yml", false);
        else try (var in = plugin.getResource("quests.yml")) {   // a newer bundled story replaces the server copy (kept as quests.yml.bak)
            if (in != null) {
                int bundled = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getInt("version", 0);
                if (bundled > YamlConfiguration.loadConfiguration(file).getInt("version", 0)) {
                    java.nio.file.Files.copy(file.toPath(), new File(plugin.getDataFolder(), "quests.yml.bak").toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    plugin.saveResource("quests.yml", true);
                }
            }
        } catch (java.io.IOException ex) { plugin.getLogger().warning("quests.yml update: " + ex); }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection cs = y.getConfigurationSection("chapters");
        if (cs != null) for (String id : cs.getKeys(false)) chapters.put(id, new Chapter(id, cs.getString(id + ".name", id), cs.getStringList(id + ".intro")));
        for (String line : LINES) {
            List<Quest> list = new ArrayList<>();
            for (Map<?, ?> m : y.getMapList("lines." + line)) { Quest q = parse(line, m); if (q != null) { list.add(q); quests.put(q.id, q); } }
            lines.put(line, list);
        }
        for (Map<?, ?> m : y.getMapList("daily")) { Quest q = parse("daily", m); if (q != null) { daily.add(q); quests.put(q.id, q); } }
        return "퀘스트 " + quests.size() + "개 (메인 " + lines.get("main").size() + " · 직업 " + lines.get("class").size() + " · 생활 " + lines.get("life").size() + " · 일일 후보 " + daily.size() + ")";
    }

    private Quest parse(String line, Map<?, ?> m) {
        Object id = m.get("id");
        if (id == null) return null;
        List<Obj> objs = new ArrayList<>();
        if (m.get("objectives") instanceof List<?> list) for (Object o : list) {
            String s = String.valueOf(o);
            int bar = s.indexOf('|');
            String spec = (bar < 0 ? s : s.substring(0, bar)).trim(), text = bar < 0 ? spec : s.substring(bar + 1).trim();
            String[] t = spec.split("\\s+");
            String type = t[0].toLowerCase(java.util.Locale.ROOT), arg = t.length > 1 ? t[1] : "";
            int need = 1;
            boolean cls = false;
            switch (type) {
                case "capture", "kill", "mine", "fish", "research", "assign" -> { need = Math.max(1, parseInt(arg, 1)); cls = t.length > 2 && t[2].equalsIgnoreCase("class"); }
                default -> { }
            }
            objs.add(new Obj(type, arg, need, cls, text));
        }
        long gold = 0; int steel = 0, vouchers = 0;
        if (m.get("rewards") instanceof Map<?, ?> r) {
            gold = parseInt(String.valueOf(r.get("gold") == null ? 0 : r.get("gold")), 0);
            steel = parseInt(String.valueOf(r.get("steel") == null ? 0 : r.get("steel")), 0);
            vouchers = parseInt(String.valueOf(r.get("vouchers") == null ? 0 : r.get("vouchers")), 0);
        }
        List<String> outro = new ArrayList<>();
        if (m.get("outro") instanceof List<?> l) for (Object o : l) outro.add(String.valueOf(o));
        return new Quest(String.valueOf(id), line, m.get("chapter") == null ? "" : String.valueOf(m.get("chapter")), str(m, "title", String.valueOf(id)),
            str(m, "giver", "촌장"), str(m, "desc", ""), objs, gold, steel, vouchers, outro);
    }

    private static String str(Map<?, ?> m, String k, String def) { Object v = m.get(k); return v == null ? def : String.valueOf(v); }
    private static int parseInt(String s, int def) { try { return Integer.parseInt(s.trim()); } catch (RuntimeException e) { return def; } }

    // ------------------------------------------------------------------ per-player state
    private String base(Player p) { return "quest." + p.getUniqueId(); }
    private boolean done(Player p, Quest q) { return plugin.data().getStringList(base(p) + ".done").contains(q.id); }
    private int prog(Player p, Quest q, int i) { return plugin.data().getInt(base(p) + ".prog." + q.id + "." + i, 0); }
    private void setProg(Player p, Quest q, int i, int v) { plugin.data().set(base(p) + ".prog." + q.id + "." + i, v); dirty = true; }
    private boolean objDone(Player p, Quest q, int i) { return prog(p, q, i) >= q.objs.get(i).need; }
    private boolean ready(Player p, Quest q) { for (int i = 0; i < q.objs.size(); i++) if (!objDone(p, q, i)) return false; return true; }

    /** Current quest of a line (the first not done), or null when the line is finished. */
    private Quest current(Player p, String line) { for (Quest q : lines.getOrDefault(line, List.of())) if (!done(p, q)) return q; return null; }

    /** Today's daily quests (picked once a day). */
    private List<Quest> dailies(Player p) {
        String today = LocalDate.now().toString(), path = base(p) + ".daily";
        if (!today.equals(plugin.data().getString(path + ".date"))) {
            List<Quest> pool = new ArrayList<>(daily);
            java.util.Collections.shuffle(pool);
            List<String> ids = new ArrayList<>();
            for (Quest q : pool.subList(0, Math.min(DAILY_COUNT, pool.size()))) ids.add(q.id);
            plugin.data().set(path + ".date", today);
            plugin.data().set(path + ".ids", ids);
            List<String> doneList = new ArrayList<>(plugin.data().getStringList(base(p) + ".done"));
            List<String> told = new ArrayList<>(plugin.data().getStringList(base(p) + ".ready-told"));
            for (Quest q : daily) { doneList.remove(q.id); told.remove(q.id); plugin.data().set(base(p) + ".prog." + q.id, null); }
            plugin.data().set(base(p) + ".done", doneList);
            plugin.data().set(base(p) + ".ready-told", told);
            dirty = true;
        }
        List<Quest> out = new ArrayList<>();
        for (String id : plugin.data().getStringList(path + ".ids")) { Quest q = quests.get(id); if (q != null) out.add(q); }
        return out;
    }

    /** Every quest the player is working on now (line heads + today's dailies not yet claimed). */
    private List<Quest> active(Player p) {
        List<Quest> out = new ArrayList<>();
        for (String line : LINES) { Quest q = current(p, line); if (q != null) out.add(q); }
        for (Quest q : dailies(p)) if (!done(p, q)) out.add(q);
        return out;
    }

    // ------------------------------------------------------------------ conditions and counters
    private boolean check(Player p, Obj o) {
        return switch (o.type) {
            case "tutorial" -> plugin.tutorialComplete(p);
            case "plot" -> plugin.ownedPlot(p) != null;
            case "worker" -> plugin.hasCraftWorker(p);
            case "petstorage" -> plugin.data().getInt("pet-storage." + p.getUniqueId() + ".level", 1) >= parseInt(o.arg, 1);
            case "wild" -> plugin.inWild(p) || p.getWorld().getName().equals("world");
            case "gold" -> town.gold(p).getScore() >= parseInt(o.arg, 0);
            case "weapon" -> hasWeapon(p, parseInt(o.arg, 0));
            case "boss" -> plugin.bossCleared(p.getUniqueId(), o.arg);
            case "sorrow" -> plugin.data().contains("sorrow." + p.getUniqueId() + ".g" + o.arg);
            case "blueprint" -> hasBlueprint(p);
            case "upgrade" -> { try { yield plugin.facilityLevelOf(p, Facility.valueOf(o.arg.toUpperCase(java.util.Locale.ROOT))) >= 2; } catch (IllegalArgumentException ex) { yield false; } }
            default -> false;
        };
    }

    /** A facility 청사진 (made from 도면 at the work bench) in the bag. */
    private static boolean hasBlueprint(Player p) {
        java.util.Set<Material> bps = new java.util.HashSet<>();
        for (Facility f : Facility.values()) try { bps.add(f.blueprintMaterial()); } catch (RuntimeException ignored) { }
        for (ItemStack it : p.getInventory().getContents()) if (it != null && bps.contains(it.getType())) return true;
        return false;
    }

    private static boolean hasWeapon(Player p, int tier) {
        List<ItemStack> items = new ArrayList<>(java.util.Arrays.asList(p.getInventory().getContents()));
        ItemStack armed = Armory.weaponFor(p);
        if (armed != null) items.add(armed);
        for (ItemStack it : items) {
            if (it == null || !CombatStats.weapon(it)) continue;
            if (!CombatStats.catalog(it).matches("^[0-5]티어.*")) continue;
            if (CombatStats.tier(it) >= tier) return true;
        }
        return false;
    }

    /** Adds to the counted objectives of the given type in every active quest. */
    void count(Player p, String type, int amount) {
        if (p == null || p.getGameMode() == GameMode.CREATIVE) return;
        boolean cls = !PartySystem.classOf(p).equals("-");
        for (Quest q : active(p)) for (int i = 0; i < q.objs.size(); i++) {
            Obj o = q.objs.get(i);
            if (!o.type.equals(type) || objDone(p, q, i) || (o.classOnly && !cls)) continue;
            setProg(p, q, i, Math.min(o.need, prog(p, q, i) + amount));
            if (objDone(p, q, i)) p.sendMessage("§a✔ §f" + o.text + " §7(" + q.title + ")");
        }
    }

    /** Marks "talk <role>" objectives done; true when one was. */
    private boolean talked(Player p, String role) {
        boolean any = false;
        for (Quest q : active(p)) for (int i = 0; i < q.objs.size(); i++) {
            Obj o = q.objs.get(i);
            if (!o.type.equals("talk") || !o.arg.equalsIgnoreCase(role) || objDone(p, q, i)) continue;
            setProg(p, q, i, o.need);
            p.sendMessage("§a✔ §f" + o.text + " §7(" + q.title + ")");
            any = true;
        }
        return any;
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                for (Quest q : active(p)) {
                    for (int i = 0; i < q.objs.size(); i++) {
                        Obj o = q.objs.get(i);
                        if (o.counted() || o.type.equals("talk") || objDone(p, q, i)) continue;
                        if (check(p, o)) { setProg(p, q, i, o.need); p.sendMessage("§a✔ §f" + o.text + " §7(" + q.title + ")"); }
                    }
                    if (ready(p, q)) announceReady(p, q);
                }
                introIfNew(p);
            } catch (RuntimeException ex) { plugin.getLogger().warning("quest tick " + p.getName() + ": " + ex); }
        }
    }

    private void announceReady(Player p, Quest q) {
        String key = base(p) + ".ready-told";
        List<String> told = plugin.data().getStringList(key);
        if (told.contains(q.id)) return;
        told = new ArrayList<>(told); told.add(q.id);
        plugin.data().set(key, told); dirty = true;
        p.sendTitle("§a퀘스트 완료", "§f" + q.title, 5, 40, 10);
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, .6f, 1.2f);
        p.sendMessage("§a[퀘스트] §f" + q.title + " §a완료! §7촌장에게 보고하거나 퀘스트 창(U)에서 보상을 받으세요.");
    }

    /** Tells the chapter intro the first time a chapter's quest becomes current. */
    private void introIfNew(Player p) {
        Quest q = current(p, "main");
        if (q == null || q.chapter.isEmpty()) return;
        String key = base(p) + ".chapters";
        List<String> seen = plugin.data().getStringList(key);
        if (seen.contains(q.chapter)) return;
        seen = new ArrayList<>(seen); seen.add(q.chapter);
        plugin.data().set(key, seen); dirty = true;
        Chapter c = chapters.get(q.chapter);
        if (c == null) return;
        p.sendTitle("§6" + c.name, "§7" + q.title, 10, 60, 20);
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 1f, .9f);
        for (int i = 0; i < c.intro.size(); i++) {
            String line = c.intro.get(i);
            Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) p.sendMessage("§6[촌장] §f" + line); }, 20L + i * 50L);
        }
    }

    // ------------------------------------------------------------------ claiming
    /** Claims a ready quest: rewards, marks it done; the next quest of the line opens. */
    private boolean claim(Player p, Quest q) {
        if (q == null || done(p, q) || !ready(p, q)) return false;
        if (!q.line.equals("daily") && current(p, q.line) != q) return false;
        List<String> doneList = new ArrayList<>(plugin.data().getStringList(base(p) + ".done"));
        doneList.add(q.id);
        plugin.data().set(base(p) + ".done", doneList);
        plugin.data().set(base(p) + ".prog." + q.id, null);
        if (!plugin.saveData()) { p.sendMessage("§c퀘스트 저장에 실패했습니다. 관리자에게 문의하세요."); return false; }
        List<String> got = new ArrayList<>();
        try {
            if (q.gold > 0) { var sc = town.gold(p); sc.setScore((int) Math.min(Integer.MAX_VALUE, sc.getScore() + q.gold)); got.add("골드 " + String.format("%,d", q.gold)); }
            if (q.steel > 0) { give(p, town.raidItem("plate", q.steel)); got.add("강철 " + q.steel); }
            if (q.vouchers > 0) { give(p, town.levelVoucher(q.vouchers)); got.add("레벨 +1 교환권 " + q.vouchers); }
        } catch (RuntimeException ex) { plugin.getLogger().severe("quest reward failed " + p.getName() + " " + q.id + ": " + ex); }
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, .7f, 1.3f);
        p.sendMessage("§6[퀘스트 보상] §f" + q.title + (got.isEmpty() ? "" : " §8· §e" + String.join(" · ", got)));
        for (int i = 0; i < q.outro.size(); i++) { String line = q.outro.get(i); Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) p.sendMessage("§6[촌장] §f" + line); }, 30L + i * 50L); }
        Quest next = current(p, q.line);
        if (next != null && !q.line.equals("daily")) p.sendMessage("§e[새 퀘스트] §f" + next.title + " §7— " + next.objs.stream().map(Obj::text).reduce((a, b) -> a + ", " + b).orElse(""));
        pushLog(p);
        return true;
    }

    private static void give(Player p, ItemStack item) {
        if (item == null) return;
        for (ItemStack left : p.getInventory().addItem(item).values()) p.getWorld().dropItemNaturally(p.getLocation(), left).setOwner(p.getUniqueId());
    }

    /** The 촌장 was clicked: "talk chief" objectives, then every ready quest is handed in. True when something happened. */
    boolean chiefHandIn(Player p) {
        boolean talked = talked(p, "chief");
        int claimed = 0;
        for (Quest q : active(p)) if (ready(p, q) && claim(p, q)) claimed++;
        return claimed > 0 || talked;
    }

    /** The 촌장 points at the current story quest; false when the story is finished. */
    boolean chiefHint(Player p) {
        Quest q = current(p, "main");
        if (q == null) return false;
        p.sendMessage("§6[촌장] §f지금은 §e" + q.title + "§f에 힘써 보게.");
        if (!q.desc.isEmpty()) p.sendMessage("§7" + q.desc);
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void npcTalk(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !(e.getRightClicked() instanceof Villager)) return;
        Entity t = e.getRightClicked();
        for (String role : List.of("npc", "merchant", "alchemist", "armory")) if (town.npcs().matches(t, role)) { talked(e.getPlayer(), role); return; }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void kill(EntityDeathEvent e) {
        Player k = e.getEntity().getKiller();
        if (k != null && e.getEntity() instanceof Monster || k != null && e.getEntity() instanceof org.bukkit.entity.Enemy) count(k, "kill", 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void mine(BlockBreakEvent e) {
        Material m = e.getBlock().getType();
        if (m.name().endsWith("_ORE") || m == Material.ANCIENT_DEBRIS) count(e.getPlayer(), "mine", 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void fish(PlayerFishEvent e) {
        if (e.getState() == PlayerFishEvent.State.CAUGHT_FISH) count(e.getPlayer(), "fish", 1);
    }

    // ------------------------------------------------------------------ HUD and quest window
    /** Objective panel lines (title first) for the tracked quest, or null. */
    List<String> hud(Player p) {
        Quest q = tracked(p);
        if (q == null) return null;
        List<String> out = new ArrayList<>();
        out.add(q.title);
        if (ready(p, q)) { out.add("§a완료! 촌장에게 보고 · U 보상"); return out; }
        for (int i = 0; i < q.objs.size() && out.size() < 5; i++) {
            Obj o = q.objs.get(i);
            boolean ok = objDone(p, q, i);
            out.add((ok ? "§a✔ §7" : "§f") + o.text + (o.need > 1 && !ok ? " §7(" + prog(p, q, i) + "/" + o.need + ")" : ""));
        }
        return out;
    }

    private Quest tracked(Player p) {
        Quest t = quests.get(plugin.data().getString(base(p) + ".track", ""));
        if (t != null && active(p).contains(t)) return t;
        return current(p, "main");
    }

    /** Sends the whole quest log to the client (PIPI_QLOG:json). */
    void pushLog(Player p) {
        JsonObject root = new JsonObject();
        Quest tr = tracked(p);
        root.addProperty("track", tr == null ? "" : tr.id);
        JsonObject tabs = new JsonObject();
        for (String line : LINES) {
            JsonArray arr = new JsonArray();
            Quest cur = current(p, line);
            for (Quest q : lines.get(line)) arr.add(json(p, q, done(p, q) ? "done" : q == cur ? (ready(p, q) ? "ready" : "active") : "locked"));
            tabs.add(line, arr);
        }
        JsonArray d = new JsonArray();
        for (Quest q : dailies(p)) d.add(json(p, q, done(p, q) ? "done" : ready(p, q) ? "ready" : "active"));
        tabs.add("daily", d);
        root.add("tabs", tabs);
        p.sendMessage("PIPI_QLOG:" + root);
    }

    private JsonObject json(Player p, Quest q, String state) {
        JsonObject o = new JsonObject();
        o.addProperty("id", q.id);
        Chapter c = chapters.get(q.chapter);
        o.addProperty("chapter", c == null ? "" : c.name);
        o.addProperty("title", q.title);
        o.addProperty("giver", q.giver);
        o.addProperty("desc", state.equals("locked") ? "" : q.desc);
        o.addProperty("state", state);
        JsonArray objs = new JsonArray();
        for (int i = 0; i < q.objs.size(); i++) {
            Obj ob = q.objs.get(i);
            JsonObject j = new JsonObject();
            j.addProperty("t", ob.text);
            j.addProperty("c", state.equals("done") ? ob.need : state.equals("locked") ? 0 : Math.min(ob.need, prog(p, q, i)));
            j.addProperty("n", ob.need);
            objs.add(j);
        }
        o.add("objs", objs);
        JsonArray rew = new JsonArray();
        if (q.gold > 0) rew.add("골드 " + String.format("%,d", q.gold));
        if (q.steel > 0) rew.add("강철 " + q.steel);
        if (q.vouchers > 0) rew.add("레벨 교환권 " + q.vouchers);
        o.add("rew", rew);
        return o;
    }

    // ------------------------------------------------------------------ commands: /pipilife quest log | track <id> | claim <id> | reload (op) | reset <player> (op)
    boolean command(Player p, String[] args) {
        String sub = args.length > 1 ? args[1].toLowerCase(java.util.Locale.ROOT) : "log";
        switch (sub) {
            case "log" -> pushLog(p);
            case "track" -> {
                Quest q = args.length > 2 ? quests.get(args[2]) : null;
                if (q != null && active(p).contains(q)) { plugin.data().set(base(p) + ".track", q.id); dirty = true; p.sendMessage("§7추적: §f" + q.title); }
                pushLog(p);
            }
            case "claim" -> {
                Quest q = args.length > 2 ? quests.get(args[2]) : null;
                if (q == null || !claim(p, q)) { p.sendMessage("§7아직 보상을 받을 수 없는 퀘스트입니다."); pushLog(p); }
            }
            case "reload" -> { if (p.isOp()) p.sendMessage("§a" + load()); }
            case "reset" -> {
                if (!p.isOp()) return true;
                Player t = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : p;
                if (t != null) { plugin.data().set(base(t), null); plugin.saveData(); p.sendMessage("§a" + t.getName() + "의 퀘스트 진행을 초기화했습니다."); pushLog(t); }
            }
            default -> p.sendMessage("/pipilife quest [log|track <id>|claim <id>]");
        }
        return true;
    }

    static String lineName(String line) { int i = LINES.indexOf(line); return i < 0 ? "일일" : LINE_NAMES[i]; }
}
