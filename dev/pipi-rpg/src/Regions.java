package dev.pipi.life;

import java.io.File;
import java.io.IOException;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.*;

/** Admin-defined protected boxes (hand-built resource areas): block protection, pet spawn rules, entry title. */
final class Regions implements Listener {
    static final class Region {
        String id, world, name = "", pets = "none";
        String owner; // plot region: this player's UUID may build/dig inside
        int minX, minY, minZ, maxX, maxY, maxZ;
        boolean enabled = true;

        boolean contains(String w, int x, int y, int z) {
            return enabled && world.equals(w) && x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }

        /** null = every species allowed, empty set = none. */
        Set<String> allowedSkins() {
            if (pets.equalsIgnoreCase("all")) return null;
            Set<String> out = new HashSet<>();
            if (!pets.equalsIgnoreCase("none")) for (String s : pets.split(",")) if (!s.isBlank()) out.add(s.trim().toLowerCase(Locale.ROOT));
            return out;
        }
    }

    private static final Set<String> RESOURCE_IDS = Set.of("AMBER_ROCK", "CABBAGE_CROP", "REDWOOD_TREE_LOG", "ROCK_PILE", "REDWOOD_TREE_LEAVES");
    private static final Set<Material> BLOCKED_ITEMS = new HashSet<>(List.of(Material.FLINT_AND_STEEL, Material.FIRE_CHARGE,
        Material.BONE_MEAL, Material.ARMOR_STAND, Material.ITEM_FRAME, Material.GLOW_ITEM_FRAME, Material.PAINTING, Material.END_CRYSTAL));

    private final PipiLife plugin;
    private final File file;
    private final Map<String, Region> regions = new LinkedHashMap<>();
    private final Map<UUID, Location> pos1 = new HashMap<>(), pos2 = new HashMap<>();
    private final Map<UUID, String> current = new HashMap<>();

    Regions(PipiLife plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "regions.yml");
        load();
    }

    // ---- storage ----

    private void load() {
        regions.clear();
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String id : yml.getKeys(false)) {
            ConfigurationSection s = yml.getConfigurationSection(id);
            if (s == null) continue;
            Region r = new Region();
            r.id = id;
            r.world = s.getString("world", "world");
            r.name = s.getString("name", id);
            r.pets = s.getString("pets", "none");
            r.enabled = s.getBoolean("enabled", true);
            r.owner = s.getString("owner");
            r.minX = s.getInt("min.x"); r.minY = s.getInt("min.y"); r.minZ = s.getInt("min.z");
            r.maxX = s.getInt("max.x"); r.maxY = s.getInt("max.y"); r.maxZ = s.getInt("max.z");
            regions.put(id, r);
        }
    }

    private void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Region r : regions.values()) {
            yml.set(r.id + ".world", r.world);
            yml.set(r.id + ".name", r.name);
            yml.set(r.id + ".pets", r.pets);
            yml.set(r.id + ".enabled", r.enabled);
            if (r.owner != null) yml.set(r.id + ".owner", r.owner);
            yml.set(r.id + ".min.x", r.minX); yml.set(r.id + ".min.y", r.minY); yml.set(r.id + ".min.z", r.minZ);
            yml.set(r.id + ".max.x", r.maxX); yml.set(r.id + ".max.y", r.maxY); yml.set(r.id + ".max.z", r.maxZ);
        }
        try {
            file.getParentFile().mkdirs();
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("regions.yml 저장 실패: " + ex);
        }
    }

    // ---- lookup ----

    Region at(Location l) {
        if (l == null || l.getWorld() == null) return null;
        return at(l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }

    Region at(Block b) { return at(b.getWorld().getName(), b.getX(), b.getY(), b.getZ()); }

    private Region at(String w, int x, int y, int z) {
        for (Region r : regions.values()) if (r.contains(w, x, y, z)) return r;
        return null;
    }

    /** Protected for this player here: inside a region, not creative/spectator, and not inside a plot region they own (own plot wins over other regions). */
    private boolean locked(Player p, Location l) {
        if (l == null || l.getWorld() == null || bypass(p)) return false;
        String w = l.getWorld().getName(), me = p.getUniqueId().toString();
        boolean inside = false;
        for (Region r : regions.values()) if (r.contains(w, l.getBlockX(), l.getBlockY(), l.getBlockZ())) {
            if (me.equals(r.owner)) return false;
            inside = true;
        }
        return inside;
    }

    private boolean locked(Player p, Block b) { return locked(p, b.getLocation()); }

    /** Deed claim: protect the whole 44x44 plot column; only the owner may build/dig there. */
    void claimPlot(String plot, String ownerUuid, String ownerName, org.bukkit.World world, int x, int z) {
        String id = "plot_" + plot.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        Region r = regions.getOrDefault(id, new Region());
        r.id = id;
        r.world = world.getName();
        r.name = (ownerName == null ? "누군가" : ownerName) + "의 거주지";
        r.owner = ownerUuid;
        r.minX = x; r.maxX = x + 43; r.minZ = z; r.maxZ = z + 43;
        r.minY = world.getMinHeight(); r.maxY = world.getMaxHeight() - 1;
        regions.put(id, r);
        save();
    }

    boolean hasRegion(String plot) { return regions.containsKey("plot_" + plot.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_")); }

    private static boolean bypass(Player p) {
        return p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR;
    }

    private static boolean isResource(Material type) {
        String n = type.name();
        if (!n.startsWith("PIPIFACILITIES_")) return false;
        return RESOURCE_IDS.contains(n.substring("PIPIFACILITIES_".length()));
    }

    // ---- block protection (players) ----

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!locked(e.getPlayer(), e.getBlock())) return;
        if (isResource(e.getBlock().getType())) return; // ResourceNodes handles these (HIGH)
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (locked(e.getPlayer(), e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (locked(e.getPlayer(), e.getBlock()) || locked(e.getPlayer(), e.getBlockClicked())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (locked(e.getPlayer(), e.getBlock()) || locked(e.getPlayer(), e.getBlockClicked())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent e) {
        Block b = e.getClickedBlock();
        if (b == null || bypass(e.getPlayer())) return;
        if (e.getAction() == org.bukkit.event.block.Action.PHYSICAL) {
            Material t = b.getType();
            if ((t == Material.FARMLAND || t == Material.TURTLE_EGG) && locked(e.getPlayer(), b)) e.setCancelled(true);
            return;
        }
        if (e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        Material item = e.getItem() == null ? Material.AIR : e.getItem().getType();
        String n = item.name();
        boolean worldChanging = BLOCKED_ITEMS.contains(item) || n.endsWith("_HOE") || n.endsWith("_AXE") || n.endsWith("_SHOVEL")
            || n.endsWith("_SPAWN_EGG") || n.endsWith("_BOAT") || n.endsWith("_MINECART") || n.equals("LAVA_BUCKET") || n.equals("WATER_BUCKET");
        if (!worldChanging) return;
        if (locked(e.getPlayer(), b) || locked(e.getPlayer(), b.getRelative(e.getBlockFace()))) e.setUseItemInHand(Event.Result.DENY);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent e) {
        if (e.getRemover() instanceof Player p && locked(p, e.getEntity().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingBreakAny(HangingBreakEvent e) {
        if (e.getCause() == HangingBreakEvent.RemoveCause.EXPLOSION && at(e.getEntity().getLocation()) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent e) {
        Player p = e.getPlayer();
        if (p != null && locked(p, e.getEntity().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent e) {
        if (locked(e.getPlayer(), e.getRightClicked().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFrameInteract(PlayerInteractEntityEvent e) {
        Entity t = e.getRightClicked();
        if ((t instanceof ItemFrame || t instanceof ArmorStand) && locked(e.getPlayer(), t.getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onArmorStandHit(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof ArmorStand && e.getDamager() instanceof Player p && locked(p, e.getEntity().getLocation()))
            e.setCancelled(true);
    }

    // ---- environment (everyone) ----

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) { e.blockList().removeIf(b -> at(b) != null); }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onExplode(BlockExplodeEvent e) { e.blockList().removeIf(b -> at(b) != null); }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) { if (at(e.getBlock()) != null) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        if (e.getPlayer() != null && bypass(e.getPlayer())) return;
        if (at(e.getBlock()) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        Region to = at(e.getToBlock());
        if (to != null && at(e.getBlock()) != to) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onLeaves(LeavesDecayEvent e) { if (at(e.getBlock()) != null) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent e) {
        if (e.getEntity() instanceof Player) return;
        if (at(e.getBlock()) != null) e.setCancelled(true); // endermen, trampling mobs, falling blocks, wither...
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) { piston(e, e.getBlock(), e.getBlocks(), e.getDirection(), true); }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) { piston(e, e.getBlock(), e.getBlocks(), e.getDirection(), false); }

    private void piston(BlockPistonEvent e, Block piston, List<Block> moved, BlockFace dir, boolean extend) {
        Region home = at(piston);
        BlockFace step = extend ? dir : dir.getOppositeFace();
        if (extend && at(piston.getRelative(dir)) != home) { e.setCancelled(true); return; }
        for (Block b : moved) {
            if (at(b) != home || at(b.getRelative(step)) != home) { e.setCancelled(true); return; }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        CreatureSpawnEvent.SpawnReason r = e.getSpawnReason();
        if (r != CreatureSpawnEvent.SpawnReason.NATURAL && r != CreatureSpawnEvent.SpawnReason.CHUNK_GEN) return;
        if (at(e.getLocation()) != null) e.setCancelled(true);
    }

    // ---- entry title ----

    private void update(Player p, Location to) {
        Region r = at(to);
        String id = r == null ? null : r.id;
        String old = current.get(p.getUniqueId());
        if (Objects.equals(old, id)) return;
        if (id == null) current.remove(p.getUniqueId()); else current.put(p.getUniqueId(), id);
        if (r != null) p.sendTitle("§6" + r.name, "§7보호 구역", 10, 40, 10);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location f = e.getFrom(), t = e.getTo();
        if (t == null || (f.getBlockX() == t.getBlockX() && f.getBlockY() == t.getBlockY() && f.getBlockZ() == t.getBlockZ() && f.getWorld() == t.getWorld())) return;
        if (regions.isEmpty()) return;
        update(e.getPlayer(), t);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) { if (e.getTo() != null) update(e.getPlayer(), e.getTo()); }

    @EventHandler public void onJoin(PlayerJoinEvent e) { update(e.getPlayer(), e.getPlayer().getLocation()); }

    @EventHandler public void onQuit(PlayerQuitEvent e) {
        current.remove(e.getPlayer().getUniqueId());
        pos1.remove(e.getPlayer().getUniqueId());
        pos2.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler public void onWorld(PlayerChangedWorldEvent e) { update(e.getPlayer(), e.getPlayer().getLocation()); }

    // ---- commands: /pipilife region ... ----

    private static boolean admin(CommandSender s) { return s.isOp() || s.hasPermission("pipilife.admin"); }

    boolean command(Player p, String[] args) {
        if (!admin(p)) { p.sendMessage("§c관리자 전용 명령어입니다."); return true; }
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "pos1", "pos2" -> {
                Location l = p.getLocation().getBlock().getLocation();
                (sub.equals("pos1") ? pos1 : pos2).put(p.getUniqueId(), l);
                p.sendMessage("§a" + sub + " 설정: " + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ());
            }
            case "create" -> create(p, args);
            case "remove" -> {
                if (args.length < 3) { p.sendMessage("§e/pipilife region remove <id>"); return true; }
                if (regions.remove(args[2].toLowerCase(Locale.ROOT)) == null) { p.sendMessage("§c그런 구역이 없습니다."); return true; }
                save();
                current.values().removeIf(v -> v.equalsIgnoreCase(args[2]));
                p.sendMessage("§a구역을 삭제했습니다: " + args[2]);
            }
            case "list" -> {
                if (regions.isEmpty()) { p.sendMessage("§7등록된 구역이 없습니다."); return true; }
                for (Region r : regions.values()) p.sendMessage("§e" + r.id + " §f" + r.name + " §7(" + r.world + ", 펫: " + r.pets + (r.enabled ? "" : ", 비활성") + ")");
            }
            case "info" -> {
                Region r = at(p.getLocation());
                if (r == null) { p.sendMessage("§7현재 위치는 보호 구역이 아닙니다."); return true; }
                describe(p, r);
            }
            case "name" -> {
                Region r = args.length >= 4 ? regions.get(args[2].toLowerCase(Locale.ROOT)) : null;
                if (r == null) { p.sendMessage("§e/pipilife region name <id> <이름...> (id가 존재해야 합니다)"); return true; }
                r.name = joinName(args, 3);
                save();
                p.sendMessage("§a이름 변경: " + r.name);
            }
            case "pets" -> pets(p, args);
            case "expand" -> expand(p, args);
            default -> {
                p.sendMessage("§e/pipilife region pos1 | pos2 | create <id> <이름...> | remove <id> | list | info");
                p.sendMessage("§e/pipilife region name <id> <이름...> | pets <id> none|all|스킨,스킨 | expand <id> <up|down|n|s|e|w> <수>");
            }
        }
        return true;
    }

    private static String joinName(String[] args, int from) {
        return String.join(" ", Arrays.copyOfRange(args, from, args.length)).replace('&', '§');
    }

    private void describe(Player p, Region r) {
        p.sendMessage("§e" + r.id + " §f" + r.name + " §7(" + r.world + ")");
        p.sendMessage("§7범위: " + r.minX + "," + r.minY + "," + r.minZ + " ~ " + r.maxX + "," + r.maxY + "," + r.maxZ + " · 펫: " + r.pets + " · " + (r.enabled ? "활성" : "비활성"));
    }

    private void create(Player p, String[] args) {
        if (args.length < 4) { p.sendMessage("§e/pipilife region create <id> <이름...>"); return; }
        String id = args[2].toLowerCase(Locale.ROOT);
        if (!id.matches("[a-z0-9_-]{1,32}")) { p.sendMessage("§cid는 영문 소문자/숫자/_/- 만 가능합니다."); return; }
        if (regions.containsKey(id)) { p.sendMessage("§c이미 있는 id입니다."); return; }
        Location a = pos1.get(p.getUniqueId()), b = pos2.get(p.getUniqueId());
        if (a == null || b == null) { p.sendMessage("§c먼저 /pipilife region pos1, pos2 를 설정하세요."); return; }
        if (a.getWorld() != b.getWorld()) { p.sendMessage("§cpos1과 pos2가 서로 다른 월드입니다."); return; }
        Region r = new Region();
        r.id = id;
        r.world = a.getWorld().getName();
        r.name = joinName(args, 3);
        r.minX = Math.min(a.getBlockX(), b.getBlockX()); r.maxX = Math.max(a.getBlockX(), b.getBlockX());
        r.minY = Math.min(a.getBlockY(), b.getBlockY()); r.maxY = Math.max(a.getBlockY(), b.getBlockY());
        r.minZ = Math.min(a.getBlockZ(), b.getBlockZ()); r.maxZ = Math.max(a.getBlockZ(), b.getBlockZ());
        regions.put(id, r);
        save();
        p.sendMessage("§a구역을 만들었습니다 (펫 스폰: none).");
        describe(p, r);
        update(p, p.getLocation());
    }

    private void pets(Player p, String[] args) {
        Region r = args.length >= 4 ? regions.get(args[2].toLowerCase(Locale.ROOT)) : null;
        if (r == null) { p.sendMessage("§e/pipilife region pets <id> none|all|스킨,스킨"); return; }
        String v = args[3].toLowerCase(Locale.ROOT);
        if (!v.equals("none") && !v.equals("all")) {
            Set<String> valid = new HashSet<>();
            for (PipiLife.Pet pet : PipiLife.Pet.values()) valid.add(pet.name().toLowerCase(Locale.ROOT));
            for (String s : v.split(",")) if (!valid.contains(s.trim())) { p.sendMessage("§c알 수 없는 펫 스킨: " + s); return; }
        }
        r.pets = v;
        save();
        p.sendMessage("§a펫 스폰 설정: " + v);
    }

    private void expand(Player p, String[] args) {
        Region r = args.length >= 5 ? regions.get(args[2].toLowerCase(Locale.ROOT)) : null;
        if (r == null) { p.sendMessage("§e/pipilife region expand <id> <up|down|n|s|e|w> <수>"); return; }
        int amt;
        try { amt = Integer.parseInt(args[4]); } catch (NumberFormatException ex) { p.sendMessage("§c수를 입력하세요."); return; }
        int minX = r.minX, minY = r.minY, minZ = r.minZ, maxX = r.maxX, maxY = r.maxY, maxZ = r.maxZ;
        switch (args[3].toLowerCase(Locale.ROOT)) {
            case "up" -> maxY += amt;
            case "down" -> minY -= amt;
            case "n" -> minZ -= amt;
            case "s" -> maxZ += amt;
            case "e" -> maxX += amt;
            case "w" -> minX -= amt;
            default -> { p.sendMessage("§c방향: up, down, n, s, e, w"); return; }
        }
        if (minX > maxX || minY > maxY || minZ > maxZ) { p.sendMessage("§c구역이 비어 버립니다."); return; }
        r.minX = minX; r.minY = minY; r.minZ = minZ; r.maxX = maxX; r.maxY = maxY; r.maxZ = maxZ;
        save();
        describe(p, r);
    }

    List<String> complete(CommandSender sender, String[] args) {
        List<String> out = new ArrayList<>();
        if (!admin(sender)) return out;
        if (args.length == 2) out.addAll(List.of("pos1", "pos2", "create", "remove", "list", "info", "name", "pets", "expand"));
        else if (args.length == 3 && List.of("remove", "name", "pets", "expand").contains(args[1].toLowerCase(Locale.ROOT))) out.addAll(regions.keySet());
        else if (args.length == 4 && args[1].equalsIgnoreCase("pets")) {
            out.addAll(List.of("none", "all"));
            for (PipiLife.Pet pet : PipiLife.Pet.values()) out.add(pet.name().toLowerCase(Locale.ROOT));
        } else if (args.length == 4 && args[1].equalsIgnoreCase("expand")) out.addAll(List.of("up", "down", "n", "s", "e", "w"));
        return out;
    }
}
