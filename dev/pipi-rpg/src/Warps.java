package dev.pipi.life;

import java.io.File;
import java.io.IOException;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Warp stones: admin-registered waypoints, per-player activation, chest-menu map for the client WarpScreen. */
final class Warps implements Listener {
    static final String TITLE = "삐삐서버 · 웨이포인트";
    static final String VIEW_TITLE = TITLE + " (보기)";
    static final String VILLAGE_CATEGORY = "마을";
    private static final Set<String> SUB = Set.of("create", "remove", "list", "rename", "category", "move", "go", "home", "menu", "view");

    private static final class Menu implements InventoryHolder {
        final String[] ids = new String[45];
        boolean view; // view-only session (opened by /pipilife warp view): never allowed to teleport
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    private final PipiLife plugin;
    private final File file;
    private YamlConfiguration cfg = new YamlConfiguration();
    private final Map<UUID, Long> cooldown = new HashMap<>();
    private Material stoneType;
    private boolean stoneResolved;

    Warps(PipiLife plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "warps.yml");
        try { if (file.isFile()) cfg.load(file); }
        catch (IOException | org.bukkit.configuration.InvalidConfigurationException ex) {
            plugin.getLogger().warning("warps.yml을 읽지 못했습니다: " + ex);
            cfg = new YamlConfiguration();
        }
    }

    // ---- helpers ----
    private Material stone() {
        if (!stoneResolved) {
            Material m = Material.matchMaterial("PIPIFACILITIES_WARP_STONE");
            if (m == null) return null; // retry later: the mod may register it after us
            stoneType = m;
            stoneResolved = true;
        }
        return stoneType;
    }

    /** True once the mod's warp-stone block is registered. */
    boolean stoneAvailable() { return stone() != null; }

    private boolean save() {
        try { cfg.save(file); return true; }
        catch (IOException ex) { plugin.getLogger().warning("warps.yml 저장 실패: " + ex); return false; }
    }

    private static boolean admin(Player p) { return p.isOp() || p.hasPermission("pipilife.admin"); }

    private boolean tell(Player p, String msg) { p.sendMessage(msg); return true; }

    boolean handles(String[] args) {
        if (args.length < 2 || !SUB.contains(args[1].toLowerCase(Locale.ROOT))) return false;
        // "go" only when it names one of ours; otherwise the older waypoint command keeps working
        return !args[1].equalsIgnoreCase("go") || (args.length >= 3 && has(args[2]));
    }

    private boolean has(String id) { return cfg.isConfigurationSection("warps." + id); }

    /** True when warps.yml already has a waypoint with this id. */
    boolean hasStone(String id) { return has(id); }

    boolean moveStone(String id, Block target, float yaw) { return moveStone(id, target, yaw, null); }

    /**
     * Moves an existing waypoint's stone to {@code target}, renaming it to {@code name} too when given: the old block is
     * cleared when it still is the warp-stone block, then the coords/yaw/name are stored and the block placed. False when the
     * id is unknown or the mod block is missing.
     */
    boolean moveStone(String id, Block target, float yaw, String name) {
        Material m = stone();
        if (m == null || !has(id)) return false;
        Location old = stoneLocation(id);
        if (old != null && old.getBlock().getType() == m) old.getBlock().setType(Material.AIR, false);
        String path = "warps." + id;
        cfg.set(path + ".world", target.getWorld().getName());
        cfg.set(path + ".x", target.getX());
        cfg.set(path + ".y", target.getY());
        cfg.set(path + ".z", target.getZ());
        cfg.set(path + ".yaw", (double) yaw);
        if (name != null) cfg.set(path + ".name", name);
        if (!save()) return false;
        target.setType(m, false);
        applyFacing(target, yaw);
        return true;
    }

    private Set<String> ids() {
        var s = cfg.getConfigurationSection("warps");
        return s == null ? Set.of() : s.getKeys(false);
    }

    private String name(String id) { return cfg.getString("warps." + id + ".name", id); }
    private String category(String id) { return cfg.getString("warps." + id + ".category", VILLAGE_CATEGORY); }

    private Location stoneLocation(String id) {
        String p = "warps." + id;
        World w = Bukkit.getWorld(cfg.getString(p + ".world", ""));
        if (w == null) w = plugin.openWorld(cfg.getString(p + ".world", ""));
        if (w == null) return null;
        return new Location(w, cfg.getInt(p + ".x"), cfg.getInt(p + ".y"), cfg.getInt(p + ".z"), (float) cfg.getDouble(p + ".yaw"), 0);
    }

    private String idAt(Block b) {
        for (String id : ids()) {
            String p = "warps." + id;
            if (cfg.getInt(p + ".x") == b.getX() && cfg.getInt(p + ".y") == b.getY() && cfg.getInt(p + ".z") == b.getZ()
                && b.getWorld().getName().equals(cfg.getString(p + ".world"))) return id;
        }
        return null;
    }

    private boolean isProtected(Block b) {
        Material m = stone();
        return m != null && b.getType() == m && idAt(b) != null;
    }

    private boolean unlocked(Player p, String id) {
        if (restricted(id)) return owner(p, id);
        return VILLAGE_CATEGORY.equals(category(id)) || isPublic(id) || cfg.getStringList("players." + p.getUniqueId()).contains(id);
    }

    /** Public waypoints (warps.<id>.public: true) are unlocked for everybody and registered on join. */
    private boolean isPublic(String id) { return cfg.getBoolean("warps." + id + ".public"); }

    @EventHandler
    public void registerPublicOnJoin(org.bukkit.event.player.PlayerJoinEvent e) { registerPublic(e.getPlayer()); }

    private void registerPublic(Player p) {
        String key = "players." + p.getUniqueId();
        List<String> list = new ArrayList<>(cfg.getStringList(key));
        boolean changed = false;
        for (String id : ids()) if (isPublic(id) && !restricted(id) && !list.contains(id)) { list.add(id); changed = true; }
        if (changed) { cfg.set(key, list); save(); }
    }

    // ---- residence warp stones (owner-only waypoints) ----
    static final String HOME_CATEGORY = "거주지";
    private boolean restricted(String id) { return !cfg.getStringList("warps." + id + ".owners").isEmpty(); }
    private boolean owner(Player p, String id) { return cfg.getStringList("warps." + id + ".owners").contains(p.getUniqueId().toString()); }
    private boolean visible(Player p, String id) { return !restricted(id) || owner(p, id); }

    /** The residence warp stone blueprint, or null when the mod item is missing. */
    ItemStack blueprintItem() {
        Material m = ModItems.byIdOrNull("warp_stone_blueprint");
        if (m == null) return null;
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName("§b거주지 워프석 청사진");
        meta.setLore(List.of("내 거주지 안에서 우클릭하여 워프석 설치", "거주지마다 1개 · 건설망치로 철거"));
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "warp_blueprint"), org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    private boolean isBlueprint(ItemStack it) {
        return it != null && it.hasItemMeta()
            && it.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(plugin, "warp_blueprint"), org.bukkit.persistence.PersistentDataType.BYTE);
    }

    private void giveBack(Player p, ItemStack it) {
        for (ItemStack extra : p.getInventory().addItem(it).values())
            p.getWorld().dropItemNaturally(p.getLocation(), extra).setOwner(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void placeHome(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND || !isBlueprint(e.getItem())) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        Block ground = e.getClickedBlock();
        if (ground == null) return;
        Material m = stone();
        if (m == null) { p.sendMessage("워프석 블록이 로드되지 않았습니다."); return; }
        String plot = plugin.plotAt(ground.getLocation());
        if (!plugin.owns(p, plot)) { p.sendMessage("내 거주지 안에서만 설치할 수 있습니다."); return; }
        String id = "home_" + plot;
        if (has(id)) { p.sendMessage("이 거주지에는 이미 워프석이 있습니다."); return; }
        Block target = ground.getRelative(BlockFace.UP);
        if (!Objects.equals(plot, plugin.plotAt(target.getLocation())) || !(target.getType().isAir() || target.isPassable() && !target.isLiquid())
            || !ground.getType().isSolid()) { p.sendMessage("내 거주지의 비어 있는 바닥 위에 설치하세요."); return; }
        if (target.getWorld().getNearbyEntities(org.bukkit.util.BoundingBox.of(target)).stream().anyMatch(en -> en instanceof org.bukkit.entity.LivingEntity)) {
            p.sendMessage("설치 공간에서 비켜 주세요."); return;
        }
        String path = "warps." + id;
        cfg.set(path + ".world", target.getWorld().getName());
        cfg.set(path + ".x", target.getX());
        cfg.set(path + ".y", target.getY());
        cfg.set(path + ".z", target.getZ());
        cfg.set(path + ".yaw", (double) p.getLocation().getYaw());
        cfg.set(path + ".category", HOME_CATEGORY);
        cfg.set(path + ".name", p.getName() + "의 거주지");
        cfg.set(path + ".owners", List.of(p.getUniqueId().toString()));
        if (!save()) { cfg.set(path, null); p.sendMessage("저장 실패."); return; }
        target.setType(m, false);
        applyFacing(target, p.getLocation().getYaw());
        if (p.getGameMode() != GameMode.CREATIVE) {
            ItemStack hand = e.getItem();
            if (hand.getAmount() <= 1) p.getInventory().setItemInMainHand(null); else hand.setAmount(hand.getAmount() - 1);
        }
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, .6f, 1.4f);
        p.sendMessage("거주지 워프석을 설치했습니다. 건설망치로 우클릭하면 철거할 수 있습니다.");
    }

    private void applyFacing(Block b, float yaw) {
        try {
            if (b.getBlockData() instanceof Directional d) {
                BlockFace f = BlockFace.values()[0];
                float y = ((yaw % 360) + 360) % 360;
                f = y < 45 || y >= 315 ? BlockFace.SOUTH : y < 135 ? BlockFace.WEST : y < 225 ? BlockFace.NORTH : BlockFace.EAST;
                if (d.getFaces().contains(f)) { d.setFacing(f); b.setBlockData(d, false); }
            }
        } catch (RuntimeException ignored) { }
    }

    // ---- retired old village + public gateway stone ----
    static final String GATE_ID = "village_gate";
    private static final int[][] GATE_SPOTS = {{0, 64, 6}, {1, 64, 6}, {-1, 64, 6}, {0, 64, 7}};

    /** Removes every waypoint registered in a retired world (warps.yml is backed up first). Returns the removed ids. */
    List<String> retireWorld(String world) {
        List<String> gone = new ArrayList<>();
        for (String id : ids()) if (world.equals(cfg.getString("warps." + id + ".world"))) gone.add(id);
        if (gone.isEmpty()) return gone;
        plugin.backupData("warps.yml", "village-retire");
        for (String id : gone) cfg.set("warps." + id, null);
        save();
        return gone;
    }

    /**
     * Public waypoint "마을 입구" (category 마을: unlocked for everybody) with its warp stone next to the NPCs on the
     * pipi_village2 gateway plaza. Idempotent; {@code force} re-registers it at the default spot (moving an old stone).
     * Returns a message, or null when the mod's stone block is not registered yet (caller may retry).
     */
    String ensureGateway(World world, boolean force) {
        Material m = stone();
        if (m == null) return null;
        if (world == null) return "거주지(v2) 월드를 찾지 못했습니다.";
        if (has(GATE_ID) && !force) {
            Location at = stoneLocation(GATE_ID);
            if (at == null) return "마을 입구 워프석의 월드를 불러오지 못했습니다.";
            at.getChunk().load();
            if (at.getBlock().getType() == m) return "마을 입구 워프석은 이미 있습니다.";
            at.getBlock().setType(m, false);
            applyFacing(at.getBlock(), at.getYaw());
            return "마을 입구 워프석 블록을 복구했습니다.";
        }
        Block spot = null;
        for (int[] c : GATE_SPOTS) {
            Block b = world.getBlockAt(c[0], c[1], c[2]);
            b.getChunk().load();
            boolean free = b.getType().isAir() || b.isPassable() && !b.isLiquid() || b.getType() == m;
            if (free && b.getRelative(BlockFace.DOWN).getType().isSolid()) { spot = b; break; }
        }
        if (spot == null) return "광장에 워프석을 놓을 빈 자리가 없습니다.";
        if (has(GATE_ID)) {
            Location old = stoneLocation(GATE_ID);
            if (old != null && isProtected(old.getBlock())) old.getBlock().setType(Material.AIR, false);
        }
        String path = "warps." + GATE_ID;
        cfg.set(path + ".world", world.getName());
        cfg.set(path + ".x", spot.getX());
        cfg.set(path + ".y", spot.getY());
        cfg.set(path + ".z", spot.getZ());
        cfg.set(path + ".yaw", 180.0);
        cfg.set(path + ".category", VILLAGE_CATEGORY);
        cfg.set(path + ".name", "마을 입구");
        cfg.set(path + ".owners", null);
        if (!save()) return "warps.yml 저장 실패.";
        spot.setType(m, false);
        applyFacing(spot, 180f);
        return "마을 입구 워프석을 설치했습니다 (" + spot.getX() + ", " + spot.getY() + ", " + spot.getZ() + ").";
    }

    /**
     * pipi_town waypoints (plaza + one per building), all in category 마을 so they are unlocked for everybody.
     * Idempotent: missing ones are registered, existing ones only get their stone block restored.
     * Returns a message, or null when the mod's stone block is not registered yet (caller may retry).
     */
    String ensureTownStones(World world, List<TownMap.Stone> stones) {
        Material m = stone();
        if (m == null) return null;
        if (world == null) return "마을 월드를 찾지 못했습니다.";
        int added = 0, fixed = 0;
        for (TownMap.Stone s : stones) {
            Block b = world.getBlockAt(s.x(), s.y(), s.z());
            b.getChunk().load();
            String path = "warps." + s.id();
            if (!has(s.id())) {
                cfg.set(path + ".world", world.getName());
                cfg.set(path + ".x", s.x());
                cfg.set(path + ".y", s.y());
                cfg.set(path + ".z", s.z());
                cfg.set(path + ".yaw", (double) s.yaw());
                cfg.set(path + ".category", VILLAGE_CATEGORY);
                cfg.set(path + ".name", s.name());
                cfg.set(path + ".owners", null);
                if (!save()) { cfg.set(path, null); return "warps.yml 저장 실패."; }
                b.setType(m, false);
                applyFacing(b, s.yaw());
                added++;
            } else if (b.getType() != m && world.getName().equals(cfg.getString(path + ".world"))) {
                b.setType(m, false);
                applyFacing(b, s.yaw());
                fixed++;
            }
        }
        return "마을 워프석: 새로 등록 " + added + "개, 복구 " + fixed + "개 (전체 " + stones.size() + "개).";
    }

    /**
     * Waypoints of a wild map. {@code pub}: registered with {@code public: true} so every player has them unlocked (and gets
     * them added to their list on join); otherwise a normal waypoint that a player registers the first time they right-click
     * the stone ({@link #interact}). Idempotent like {@link #ensureTownStones}: missing ones are registered and placed, existing
     * ones only get their stone block restored. Null when the mod's stone block is not registered yet.
     */
    String ensureWildStones(World world, List<TownMap.Stone> stones, String category, boolean pub) {
        Material m = stone();
        if (m == null) return null;
        if (world == null) return category + " 월드를 찾지 못했습니다.";
        int added = 0, fixed = 0;
        for (TownMap.Stone s : stones) {
            Block b = world.getBlockAt(s.x(), s.y(), s.z());
            b.getChunk().load();
            String path = "warps." + s.id();
            if (!has(s.id())) {
                cfg.set(path + ".world", world.getName());
                cfg.set(path + ".x", s.x());
                cfg.set(path + ".y", s.y());
                cfg.set(path + ".z", s.z());
                cfg.set(path + ".yaw", (double) s.yaw());
                cfg.set(path + ".category", category);
                cfg.set(path + ".name", s.name());
                cfg.set(path + ".owners", null);
                cfg.set(path + ".public", pub ? true : null);
                if (!save()) { cfg.set(path, null); return "warps.yml 저장 실패."; }
                b.setType(m, false);
                applyFacing(b, s.yaw());
                added++;
            } else if (b.getType() != m && world.getName().equals(cfg.getString(path + ".world"))) {
                b.setType(m, false);
                applyFacing(b, s.yaw());
                fixed++;
            }
        }
        if (pub) {
            List<String> ids = new ArrayList<>();
            for (TownMap.Stone s : stones) ids.add(s.id());
            makePublic(ids); // existing entries too; registers everybody online
        }
        return category + " 워프석: 새로 등록 " + added + "개, 복구 " + fixed + "개 (전체 " + stones.size() + "개" + (pub ? ", 모든 플레이어 등록)." : ", 처음 우클릭할 때 등록).");
    }

    /**
     * Marks the existing waypoints among {@code ids} {@code public: true} (missing ids are skipped) and registers every public
     * waypoint for all online players (players joining later get them via {@link #registerPublicOnJoin}). Returns how many
     * entries were switched to public.
     */
    int makePublic(java.util.Collection<String> ids) {
        List<String> changed = new ArrayList<>();
        for (String id : ids) if (has(id) && !isPublic(id) && !restricted(id)) { cfg.set("warps." + id + ".public", true); changed.add(id); }
        if (!changed.isEmpty() && !save()) { for (String id : changed) cfg.set("warps." + id + ".public", null); return 0; }
        for (Player online : Bukkit.getOnlinePlayers()) registerPublic(online);
        return changed.size();
    }

    // ---- commands ----
    boolean command(Player p, String[] args) {
        String sub = args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "home": return home(p);
            case "menu": return open(p, null, !admin(p) && plugin.isWildWorld(p.getWorld().getName())); // wild: teleporting only at a stone
            case "view": return open(p, null, true);
            case "go":
                if (args.length < 3) return tell(p, "/pipilife warp go <id>");
                return go(p, args[2]);
            default: break;
        }
        if (!admin(p)) return tell(p, "§c관리자 전용 명령어입니다.");
        String usage = "/pipilife warp create <id> <카테고리> <이름...> · remove <id> · list · rename <id> <이름...> · category <id> <카테고리> · move <id>";
        switch (sub) {
            case "list": {
                if (ids().isEmpty()) return tell(p, "등록된 워프석이 없습니다.");
                for (String id : ids()) p.sendMessage(id + " · " + category(id) + " · " + name(id) + " · "
                    + cfg.getString("warps." + id + ".world") + " " + cfg.getInt("warps." + id + ".x") + " "
                    + cfg.getInt("warps." + id + ".y") + " " + cfg.getInt("warps." + id + ".z"));
                return true;
            }
            case "create": {
                if (args.length < 5 || !args[2].matches("[a-z0-9_-]{1,32}")) return tell(p, usage);
                if (has(args[2])) return tell(p, "이미 있는 ID입니다.");
                Material m = stone();
                if (m == null) return tell(p, "워프석 블록이 로드되지 않았습니다. (모드 pipifacilities 필요)");
                Block b = p.getLocation().getBlock();
                String path = "warps." + args[2];
                cfg.set(path + ".world", b.getWorld().getName());
                cfg.set(path + ".x", b.getX());
                cfg.set(path + ".y", b.getY());
                cfg.set(path + ".z", b.getZ());
                cfg.set(path + ".yaw", (double) p.getLocation().getYaw());
                cfg.set(path + ".category", args[3]);
                cfg.set(path + ".name", String.join(" ", Arrays.copyOfRange(args, 4, args.length)));
                if (!save()) { cfg.set(path, null); return tell(p, "저장 실패."); }
                b.setType(m, false);
                applyFacing(b, p.getLocation().getYaw());
                return tell(p, "워프석을 설치했습니다: " + args[2]);
            }
            case "remove": {
                if (args.length < 3 || !has(args[2])) return tell(p, "없는 ID입니다.");
                Location l = stoneLocation(args[2]);
                if (l != null && isProtected(l.getBlock())) l.getBlock().setType(Material.AIR, false);
                cfg.set("warps." + args[2], null);
                for (String key : cfg.getConfigurationSection("players") == null ? Set.<String>of() : cfg.getConfigurationSection("players").getKeys(false)) {
                    List<String> list = new ArrayList<>(cfg.getStringList("players." + key));
                    if (list.remove(args[2])) cfg.set("players." + key, list);
                }
                save();
                return tell(p, "워프석을 제거했습니다: " + args[2]);
            }
            case "rename": {
                if (args.length < 4 || !has(args[2])) return tell(p, usage);
                cfg.set("warps." + args[2] + ".name", String.join(" ", Arrays.copyOfRange(args, 3, args.length)));
                save();
                return tell(p, "이름을 변경했습니다.");
            }
            case "category": {
                if (args.length != 4 || !has(args[2])) return tell(p, usage);
                cfg.set("warps." + args[2] + ".category", args[3]);
                save();
                return tell(p, "카테고리를 변경했습니다.");
            }
            case "move": {
                if (args.length != 3 || !has(args[2])) return tell(p, usage);
                Material m = stone();
                if (m == null) return tell(p, "워프석 블록이 로드되지 않았습니다.");
                Location old = stoneLocation(args[2]);
                if (old != null && isProtected(old.getBlock())) old.getBlock().setType(Material.AIR, false);
                Block b = p.getLocation().getBlock();
                String path = "warps." + args[2];
                cfg.set(path + ".world", b.getWorld().getName());
                cfg.set(path + ".x", b.getX());
                cfg.set(path + ".y", b.getY());
                cfg.set(path + ".z", b.getZ());
                cfg.set(path + ".yaw", (double) p.getLocation().getYaw());
                save();
                b.setType(m, false);
                applyFacing(b, p.getLocation().getYaw());
                return tell(p, "워프석을 현재 위치로 옮겼습니다.");
            }
            default: return tell(p, usage);
        }
    }

    List<String> complete(org.bukkit.command.CommandSender s, String[] args) {
        List<String> out = new ArrayList<>();
        boolean adm = s.isOp() || s.hasPermission("pipilife.admin");
        if (args.length == 2) {
            out.addAll(List.of("go", "home", "menu", "view"));
            if (adm) out.addAll(List.of("create", "remove", "list", "rename", "category", "move"));
        } else if (args.length == 3) {
            String sub = args[1].toLowerCase(Locale.ROOT);
            if (sub.equals("go") || (adm && Set.of("remove", "rename", "category", "move").contains(sub))) out.addAll(ids());
        } else if (args.length == 4 && adm && args[1].equalsIgnoreCase("category")
            || args.length == 4 && adm && args[1].equalsIgnoreCase("create")) {
            out.add(VILLAGE_CATEGORY);
            for (String id : ids()) if (!out.contains(category(id))) out.add(category(id));
        }
        return out;
    }

    // ---- teleport ----
    private boolean home(Player p) {
        return plugin.townHome(p);
    }

    private boolean go(Player p, String id) {
        if (!has(id) || !visible(p, id)) return tell(p, "없는 웨이포인트입니다.");
        if (!unlocked(p, id)) return tell(p, "아직 활성화하지 않은 웨이포인트입니다.");
        long now = System.currentTimeMillis();
        Long last = cooldown.get(p.getUniqueId());
        if (last != null && now - last < 3000) return tell(p, "잠시 후 다시 이동할 수 있습니다.");
        Location stone = stoneLocation(id);
        if (stone == null) return tell(p, "웨이포인트의 월드를 불러오지 못했습니다.");
        Location dest = safeSpot(stone);
        if (dest == null) {
            plugin.getLogger().warning("warp " + id + ": no standing spot near " + stone.getWorld().getName() + " " + stone.getBlockX() + " " + stone.getBlockY() + " " + stone.getBlockZ());
            return tell(p, "도착 공간이 막혀 있습니다.");
        }
        cooldown.put(p.getUniqueId(), now);
        p.closeInventory();
        if (!p.teleport(dest)) return tell(p, "이동이 취소되었습니다.");
        p.setFallDistance(0);
        p.sendMessage("이동했습니다: " + name(id));
        return true;
    }

    private static boolean hazard(Material m) {
        return switch (m) {
            case FIRE, SOUL_FIRE, LAVA, CACTUS, MAGMA_BLOCK, CAMPFIRE, SOUL_CAMPFIRE, SWEET_BERRY_BUSH, WITHER_ROSE, POWDER_SNOW, COBWEB, BAMBOO_SAPLING -> true;
            default -> false;
        };
    }

    /** Standing room: passable, dry, harmless feet and head space. */
    private static boolean room(Block b) {
        return b.isPassable() && !b.isLiquid() && !hazard(b.getType());
    }

    /** Floor test by collision (not Material.isSolid, which is false for end rods, slabs-on-edges, lanterns ...). */
    private static boolean floor(Block b) {
        return !b.isPassable() && !b.isLiquid() && !hazard(b.getType());
    }

    /**
     * A free standing spot around the stone: nearest ring around 2 blocks away first (the stone's front preferred),
     * out to 4 blocks, then on top of the stone itself. Null only when nothing within reach has headroom and a floor.
     */
    private Location safeSpot(Location stone) {
        double rad = Math.toRadians(stone.getYaw());
        double fx = -Math.sin(rad), fz = Math.cos(rad); // direction the stone faces
        World w = stone.getWorld();
        int sx = stone.getBlockX(), sy = stone.getBlockY(), sz = stone.getBlockZ();
        List<int[]> offsets = new ArrayList<>();
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) if (dx != 0 || dz != 0) offsets.add(new int[] {dx, dz});
        offsets.sort(Comparator.comparingDouble((int[] o) -> Math.abs(Math.sqrt(o[0] * o[0] + o[1] * o[1]) - 2.0)
            - 0.01 * (o[0] * fx + o[1] * fz) / Math.max(1.0, Math.sqrt(o[0] * o[0] + o[1] * o[1]))));
        for (int[] o : offsets) {
            int bx = sx + o[0], bz = sz + o[1];
            for (int dy = 1; dy >= -2; dy--) {
                Block feet = w.getBlockAt(bx, sy + dy, bz);
                if (room(feet) && room(feet.getRelative(0, 1, 0)) && floor(feet.getRelative(0, -1, 0))) {
                    float yaw = (float) Math.toDegrees(Math.atan2(-o[0], o[1]));
                    return new Location(w, bx + .5, feet.getY(), bz + .5, yaw, 0);
                }
            }
        }
        Block top = w.getBlockAt(sx, sy + 1, sz);
        if (room(top) && room(top.getRelative(0, 1, 0))) return new Location(w, sx + .5, sy + 1, sz + .5, stone.getYaw(), 0);
        return null;
    }

    // ---- residence plot data for the client map: PIPI_PLOTS:<world>;<size>;<name>,<x>,<z>,<ownerName>|... ----
    static final String PLOT_WORLD = "pipi_village2";
    private static final int PLOT_SIZE = 44;

    /** Hidden line with every plots-v2 plot (min corner, size, owner name or empty). Names resolve via Bukkit.getOfflinePlayer. */
    void sendPlots(Player p) {
        var plots = plugin.data().getConfigurationSection("plots-v2");
        if (plots == null) return;
        StringBuilder sb = new StringBuilder("PIPI_PLOTS:" + PLOT_WORLD + ";" + PLOT_SIZE + ";");
        boolean first = true;
        for (String name : plots.getKeys(false)) {
            String uuid = plots.getString(name + ".owner");
            String owner = "";
            if (uuid != null && !uuid.isEmpty()) {
                owner = "주인";
                try {
                    String n = Bukkit.getOfflinePlayer(UUID.fromString(uuid)).getName();
                    if (n != null) owner = n;
                } catch (IllegalArgumentException ignored) { }
                owner = owner.replaceAll("[,|;]", "");
            }
            if (!first) sb.append('|');
            first = false;
            sb.append(name).append(',').append(plots.getInt(name + ".x")).append(',').append(plots.getInt(name + ".z")).append(',').append(owner);
        }
        p.sendMessage(sb.toString());
    }

    /** Re-sends the plot list to everyone who currently has the warp map open (call after a plot is claimed). */
    void refreshPlots() {
        for (Player p : Bukkit.getOnlinePlayers())
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof Menu) sendPlots(p);
    }

    // ---- menu ----
    private boolean open(Player p, String here) { return open(p, here, false); }

    private boolean open(Player p, String here, boolean view) {
        List<String> order = new ArrayList<>(ids());
        order.removeIf(id -> !visible(p, id));
        order.sort(Comparator.comparing((String id) -> VILLAGE_CATEGORY.equals(category(id)) ? 0 : 1)
            .thenComparing(this::category).thenComparing(this::name));
        Menu menu = new Menu();
        menu.view = view;
        menu.inv = Bukkit.createInventory(menu, 54, view ? VIEW_TITLE : TITLE);
        for (int i = 0; i < order.size() && i < 45; i++) {
            String id = order.get(i);
            boolean open = unlocked(p, id);
            ItemStack it = new ItemStack(open ? Material.AMETHYST_SHARD : Material.GRAY_DYE);
            ItemMeta meta = it.getItemMeta();
            meta.setDisplayName(name(id));
            String path = "warps." + id;
            meta.setLore(List.of("wp:" + id + "|" + category(id) + "|" + cfg.getString(path + ".world") + "|"
                + cfg.getInt(path + ".x") + "|" + cfg.getInt(path + ".z") + "|" + (open ? 1 : 0) + "|" + (id.equals(here) ? 1 : 0)));
            it.setItemMeta(meta);
            menu.inv.setItem(i, it);
            menu.ids[i] = id;
        }
        if (view) {
            Location l = p.getLocation();
            ItemStack me = new ItemStack(Material.COMPASS);
            ItemMeta mm = me.getItemMeta();
            mm.setDisplayName("내 위치");
            mm.setLore(List.of("me|" + l.getWorld().getName() + "|" + l.getBlockX() + "|" + l.getBlockZ()));
            me.setItemMeta(mm);
            menu.inv.setItem(48, me);
        }
        ItemStack home = new ItemStack(Material.RED_BED);
        ItemMeta hm = home.getItemMeta();
        hm.setDisplayName("내 거주지");
        hm.setLore(List.of("home"));
        home.setItemMeta(hm);
        if (view && plugin.isWildWorld(p.getWorld().getName())) { // wild: only at a warp stone
            home = new ItemStack(Material.GRAY_DYE);
            hm = home.getItemMeta();
            hm.setDisplayName("야생에서는 워프석에서만 거주지로 이동할 수 있습니다");
            hm.setLore(List.of("homeoff"));
            home.setItemMeta(hm);
        }
        menu.inv.setItem(49, home);
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cm = close.getItemMeta();
        cm.setDisplayName("닫기");
        close.setItemMeta(cm);
        menu.inv.setItem(53, close);
        sendPlots(p);
        p.openInventory(menu.inv);
        return true;
    }

    // ---- events ----
    @EventHandler(priority = EventPriority.HIGH)
    public void interact(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Block b = e.getClickedBlock();
        if (b == null || !isProtected(b)) return;
        Player p = e.getPlayer();
        if (p.isSneaking() && e.getItem() != null) return; // allow sneak-place against it
        e.setCancelled(true);
        String id = idAt(b);
        if (id == null) return;
        if (restricted(id)) {
            if (!owner(p, id)) { p.sendMessage("다른 플레이어의 거주지 워프석입니다."); return; }
            if (plugin.isConstructionHammer(e.getItem())) {
                cfg.set("warps." + id, null);
                if (!save()) { p.sendMessage("저장 실패."); return; }
                b.setType(Material.AIR, false);
                ItemStack back = blueprintItem();
                if (back != null) giveBack(p, back);
                p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, .8f, 1f);
                p.sendMessage("거주지 워프석을 철거했습니다. 청사진을 돌려받았습니다.");
                return;
            }
        }
        if (!unlocked(p, id)) {
            List<String> list = new ArrayList<>(cfg.getStringList("players." + p.getUniqueId()));
            list.add(id);
            cfg.set("players." + p.getUniqueId(), list);
            save();
            p.sendMessage("웨이포인트를 활성화했습니다: " + name(id));
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, .6f, 1.4f);
        }
        open(p, id);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void breakBlock(BlockBreakEvent e) {
        if (!isProtected(e.getBlock())) return;
        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE && admin(p)) {
            String id = idAt(e.getBlock());
            if (id != null) { cfg.set("warps." + id, null); save(); p.sendMessage("워프석 등록을 해제했습니다: " + id); }
            e.setDropItems(false);
            return;
        }
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void explode(EntityExplodeEvent e) { e.blockList().removeIf(this::isProtected); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void explode(BlockExplodeEvent e) { e.blockList().removeIf(this::isProtected); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void piston(BlockPistonExtendEvent e) { if (e.getBlocks().stream().anyMatch(this::isProtected)) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void piston(BlockPistonRetractEvent e) { if (e.getBlocks().stream().anyMatch(this::isProtected)) e.setCancelled(true); }

    @EventHandler public void drag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }

    @EventHandler public void click(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
        int slot = e.getRawSlot();
        if (slot == 53) { p.closeInventory(); return; }
        if (slot == 49) { p.closeInventory(); if (menu.view) home(p); else plugin.townHomeFromStone(p); return; } // at a stone: works in the wild too; view-only: refused in the wild
        if (menu.view) return; // view-only session: no waypoint teleporting
        if (slot >= 0 && slot < 45 && menu.ids[slot] != null) go(p, menu.ids[slot]);
    }
}
