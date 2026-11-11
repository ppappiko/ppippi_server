package dev.pipi.life;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class PipiLife extends JavaPlugin implements Listener {
    private static final long CYCLE = 120_000;
    private static final int CAPACITY = 64;
    private static final String WILD = "world";

    enum Pet {
        SPARROW(EntityType.CHICKEN, "짱새", Facility.FARM, 1, "forest"),
        CAT(EntityType.CAT, "검은고양이", Facility.LUMBER, 1, "plains"),
        RABBIT(EntityType.RABBIT, "마토", Facility.CRAFT, 2, "cherry_grove"),
        FOX(EntityType.FOX, "레디", Facility.MINE, 1, "desert"),
        REDPANDA(EntityType.PANDA, "레시", Facility.CRAFT, 1, "forest"),
        OTTER(EntityType.AXOLOTL, "해달수", Facility.LUMBER, 2, "warm_ocean"),
        TORTOISE(EntityType.TURTLE, "딱지", Facility.MINE, 3, "windswept_gravelly_hills"),
        RACCOON(EntityType.PANDA, "피토", Facility.CRAFT, 3, "mushroom_fields"),
        HORNRABBIT(EntityType.RABBIT, "설토", Facility.LUMBER, 2, "ice_spikes"),
        MOLE(EntityType.SILVERFISH, "두류", Facility.MINE, 2, "wooded_badlands"),
        THUNDERSHEEP(EntityType.SHEEP, "썬더쉽", Facility.MINE, 2, "sunflower_plains"),
        MOONBEAR(EntityType.POLAR_BEAR, "달투", Facility.LUMBER, 3, "deep_dark"),
        FERRET(EntityType.FOX, "장꾸펫", Facility.FARM, 1, "forest"),
        ANGELPIG(EntityType.PIG, "천사픽", Facility.FARM, 2, "flower_forest"),
        FROG(EntityType.FROG, "가마", Facility.LUMBER, 2, "mangrove_swamp"),
        SUNLIZARD(EntityType.AXOLOTL, "파로", Facility.FARM, 3, "stony_peaks"),
        IMUGI(EntityType.SILVERFISH, "이무기", Facility.TRANSPORT, 2, "bastion"),
        VAMPBAT(EntityType.BAT, "블벳", Facility.TRANSPORT, 1, "fortress");

        final EntityType type;
        final String label;
        final Facility aptitude;
        final int level;
        final String place;

        Pet(EntityType type, String label, Facility aptitude, int level, String place) {
            this.type = type;
            this.label = label;
            this.aptitude = aptitude;
            this.level = level;
            this.place = place;
        }

        Material product() {
            return switch (aptitude) {
                case FARM -> ModItems.byId("straw");
                case CARROT_FARM -> ModItems.byId("pet_carrot");
                case POTATO_FARM -> Material.POTATO;
                case LUMBER -> Material.OAK_LOG;
                default -> Material.IRON_INGOT;
            };
        }

        int maxHp() { return new int[]{35, 55, 80}[level - 1]; }
        int captureRate() { return new int[]{120, 3, 1}[level - 1]; }
        int salePrice() { return new int[]{3000, 8000, 20000}[level - 1]; }
        boolean transport() { return aptitude == Facility.TRANSPORT; }
        boolean netherPlace() { return place.equals("bastion") || place.equals("fortress"); }
    }

    static Pet petBySkin(String skin) {
        if (skin == null) return null;
        try { return Pet.valueOf(skin.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { return null; }
    }

    private static final int MAX_FOOD = 100;

    private record Workshop(int food, int stock, long since, boolean active, int cap) {
        Workshop(int food, int stock, long since, boolean active) { this(food, stock, since, active, CAPACITY); }
        Workshop settle(long now, long cycle) {
            if (!active || food == 0 || stock >= cap) return new Workshop(food, stock, now, active, cap);
            int made = (int) Math.min(Math.min(food, cap - stock), Math.max(0, now - since) / cycle);
            return new Workshop(food - made, stock + made, since + made * cycle, active, cap);
        }
    }

    private YamlConfiguration data;
    private Path stateFile;
    private NamespacedKey trapKey;
    private NamespacedKey compassKey;
    private NamespacedKey stationKey;
    private static NamespacedKey facilityLevelKey;
    private VillageMaps maps;
    private ClassSkills skills;
    private TownSystems town;
    private CombatStats combat;
    private Armory armory;
    private Social social;
    private CustomPets customPets;
    private CaptureSystem captures;
    private ResourceNodes resourceNodes;
    private MobHealthBars healthBars;
    Regions regions;
    private Warps warps;
    private boolean warnedRemovedSpecies;
    Cooking cooking;
    private TrainingDummy dummies;
    private DamageNumbers damageNumbers;
    private HitEffects hitEffects;

    @Override public void onEnable() {
        saveDefaultConfig();
        trapKey = new NamespacedKey(this, "capture_trap");
        compassKey = new NamespacedKey(this, "travel_compass");
        stationKey = new NamespacedKey(this, "pet_station");
        facilityLevelKey = new NamespacedKey(this, "facility_level");
        stateFile = getDataFolder().toPath().resolve("workshops.yml");
        data = new YamlConfiguration();
        try {
            Files.createDirectories(stateFile.getParent());
            if (Files.exists(stateFile)) data.load(stateFile.toFile());
        } catch (IOException | InvalidConfigurationException ex) {
            getLogger().severe("Failed to load workshop data: " + ex);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (!migrateFacilities()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        sanitizePlacements();
        maps = new VillageMaps(this);
        town = new TownSystems(this);
        armory = new Armory(this, town.npcs());
        getServer().getPluginManager().registerEvents(armory, this);
        combat =new CombatStats(this, town);
        combat.startHpSync();
        new HudSync(this, town).start();
        getServer().getPluginManager().registerEvents(combat, this);
        getServer().getScheduler().runTaskTimer(this, combat::tick, 1, 5);
        skills = new ClassSkills(this);
        getServer().getPluginManager().registerEvents(skills, this);
        getServer().getPluginManager().registerEvents(town, this);
        social = new Social(this, town, combat);
        getServer().getPluginManager().registerEvents(social, this);
        cooking = new Cooking(this);
        getServer().getPluginManager().registerEvents(cooking, this);
        try {
            resourceNodes = new ResourceNodes(this, town);
            getServer().getPluginManager().registerEvents(resourceNodes, this);
        } catch (RuntimeException ex) {
            getLogger().warning("자원 노드를 시작하지 못했습니다: " + ex);
        }
        if (data.getBoolean("maps-ready")) {
            maps.open(VillageMaps.TUTORIAL);
            World village = maps.open(maps.village());
            if (village != null) {
                migrateFacilityBlocks(village);
                getLogger().info(town.installAlchemyRoom(village));
                town.installMarketNpcs(village);
            }
        }
        try {
            regions = new Regions(this);
            getServer().getPluginManager().registerEvents(regions, this);
        } catch (RuntimeException ex) {
            regions = null;
            getLogger().warning("보호 구역을 시작하지 못했습니다: " + ex);
        }
        try {
            warps = new Warps(this);
            getServer().getPluginManager().registerEvents(warps, this);
        } catch (RuntimeException | LinkageError ex) {
            warps = null;
            getLogger().warning("워프석을 시작하지 못했습니다: " + ex);
        }
        try {
            getServer().getPluginManager().registerEvents(new WorldRules(this), this);
        } catch (RuntimeException | LinkageError ex) {
            getLogger().warning("월드 규칙을 적용하지 못했습니다: " + ex);
        }
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, this::keepEveryoneFull, 1, 1);
        getServer().getScheduler().runTaskLater(this, () -> {
            for (var world : getServer().getWorlds()) for (var chunk : world.getLoadedChunks()) refreshStationModels(chunk);
        }, 100L);
        if(Bukkit.getPluginManager().isPluginEnabled("ModelEngine")){customPets=new CustomPets(this);getServer().getPluginManager().registerEvents(customPets,this);getServer().getScheduler().runTaskTimer(this,customPets::tick,40,10);getServer().getScheduler().runTaskTimer(this,customPets::spawnWild,60,Math.max(20,getConfig().getLong("pet-spawn.interval-ticks",200)));}
        captures=new CaptureSystem(this);
        healthBars=new MobHealthBars(this);
        getServer().getPluginManager().registerEvents(captures,this);
        getServer().getPluginManager().registerEvents(healthBars,this);
        getServer().getScheduler().runTaskTimer(this,captures::tick,1,1);
        getServer().getScheduler().runTaskTimer(this,healthBars::tick,5,5);



        dummies = new TrainingDummy(this);
        getServer().getPluginManager().registerEvents(dummies, this);
        try { hitEffects = new HitEffects(this); getServer().getPluginManager().registerEvents(hitEffects, this); } catch (Throwable t) { getLogger().warning("HitEffects disabled: " + t); }
        damageNumbers = new DamageNumbers(this, dummies);
        getServer().getPluginManager().registerEvents(damageNumbers, this);
        getServer().getScheduler().runTaskTimer(this, this::previewStations, 5, 5);
        getServer().getScheduler().runTaskTimer(this, this::feedAssignedPets, 20, 20);
        getServer().getScheduler().runTaskTimer(this, this::checkHungryWorkers, 20, 20);
        getServer().getScheduler().runTaskTimer(this, this::refreshLumber, 20, 20);
    }

    private void unassignPlacement(String key) {
        data.set(key + ".active", false);
        data.set(key + ".facility-bound", false);
        data.set(key + ".production-facility", null);
        data.set(key + ".production-anchor", null);
        data.set(key + ".lumber-slot", null);
    }

    private boolean badProducerPlacement(Pet pet, String key) {
        String saved = data.getString(key + ".production-facility");
        if (saved == null) return false;
        Facility facility;
        try { facility = Facility.valueOf(saved); } catch (IllegalArgumentException ex) { facility = null; }
        return pet == null || facility == null || !pet.aptitude.isProducer() || aptitude(null, pet, facility) < 1;
    }

    /** Unassign saved placements that reference removed species or aptitude-mismatched facilities. */
    private void sanitizePlacements() {
        boolean changed = false;
        var players = data.getConfigurationSection("players");
        if (players != null) for (String id : players.getKeys(false)) {
            var section = data.getConfigurationSection("players." + id);
            if (section == null) continue;
            for (String name : section.getKeys(false)) {
                String key = "players." + id + "." + name;
                if (!data.isConfigurationSection(key)) continue;
                Pet pet = petBySkin(name);
                boolean placed = data.contains(key + ".production-facility");
                if (pet == null ? placed : badProducerPlacement(pet, key)) {
                    unassignPlacement(key);
                    changed = true;
                }
            }
        }
        var storage = data.getConfigurationSection("pet-storage");
        if (storage != null) for (String id : storage.getKeys(false)) {
            for (String saved : data.getStringList("pet-storage." + id + ".slots")) {
                if (saved.indexOf(':') < 0) continue;
                String key = "pet-storage." + id + ".workers." + saved.split(":", 2)[1];
                if (!data.isConfigurationSection(key)) continue;
                if (badProducerPlacement(entryPet(saved), key)) { unassignPlacement(key); changed = true; }
            }
        }
        var town = data.getConfigurationSection("town");
        if (town != null) for (String key : town.getKeys(true)) {
            if (!key.equals("craft-worker") && !key.endsWith(".craft-worker")) continue;
            String full = "town." + key;
            String entry = data.getString(full);
            Pet pet = entryPet(entry);
            if (entry != null && (pet == null || !sameAptitudeKind(Facility.CRAFT, pet.aptitude))) {
                data.set(full, null);
                changed = true;
            }
        }
        if (changed) {
            getLogger().warning("유효하지 않은 펫 배치를 해제했습니다.");
            if (data.getKeys(true).stream().anyMatch(k -> "GOLD_MINE".equals(data.getString(k)))) getLogger().warning("삭제된 금광석 채석장 배치가 있어 펫을 창고로 되돌렸습니다.");
            persist();
        }
    }

    private boolean migrateFacilities() {
        if (data.getBoolean("typed-facilities-migrated")) return true;
        var players = data.getConfigurationSection("players");
        long now = System.currentTimeMillis();
        if (players != null) {
            for (String id : players.getKeys(false)) {
                for (Pet pet : Pet.values()) {
                    String key = "players." + id + "." + pet.name().toLowerCase(Locale.ROOT);
                    if (!data.isConfigurationSection(key) || !data.getBoolean(key + ".active")) continue;
                    Workshop previous = new Workshop(data.getInt(key + ".food"), data.getInt(key + ".stock"),
                        data.getLong(key + ".since"), true).settle(now, CYCLE);
                    data.set(key + ".food", previous.food());
                    data.set(key + ".stock", previous.stock());
                    data.set(key + ".since", now);
                    data.set(key + ".active", false);
                }
            }
        }
        data.set("typed-facilities-migrated", true);
        return persist();
    }

    ItemStack trapItem(int tier) {
        ItemStack result = new ItemStack(ModItems.trap(tier));
        var meta = result.getItemMeta();
        meta.setDisplayName(tier == 0 ? "§f기본 포획틀" : tier == 1 ? "§f철 포획틀" : tier == 2 ? "§b다이아몬드 포획틀" : "§6호박석 포획틀");
        meta.setLore(List.of("§7우클릭을 누른 채 조준 · 놓으면 투척","§7몽둥이로 체력을 낮추면 포획 확률 증가","§7야생 성체 펫 전용 · 3회 흔들림 판정"));
        meta.getPersistentDataContainer().set(trapKey, PersistentDataType.INTEGER, tier);
        result.setItemMeta(meta);
        return result;
    }

    private void registerTrap(int tier, Material frame) {
        var recipe = new ShapedRecipe(new NamespacedKey(this, "trap_" + tier), trapItem(tier));
        recipe.shape("FSF", "SHS", "FSF");
        recipe.setIngredient('F', frame);
        recipe.setIngredient('S', Material.STRING);
        recipe.setIngredient('H', Material.TRIPWIRE_HOOK);
        Bukkit.addRecipe(recipe);
    }

    private String path(Player player, Pet pet) {
        return "players." + player.getUniqueId() + "." + pet.name().toLowerCase(Locale.ROOT);
    }

    private boolean save(String path, Workshop next) {
        Object before = data.get(path);
        Map<String, Object> old = new HashMap<>();
        for (String field : List.of("food", "stock", "since", "active")) old.put(field, data.get(path + "." + field));
        if (before == null) data.createSection(path);
        data.set(path + ".food", next.food());
        data.set(path + ".stock", next.stock());
        data.set(path + ".since", next.since());
        data.set(path + ".active", next.active());
        if (persist()) return true;
        if (before == null) data.set(path, null);
        else old.forEach((field, value) -> data.set(path + "." + field, value));
        return false;
    }


    private String saleKey(Player player, Pet pet) {
        return saleKey(player, switch (pet.aptitude) {
            case FARM, CARROT_FARM, POTATO_FARM, CABBAGE_FARM -> "wheat";
            case LUMBER, REDWOOD_LUMBER -> "wood";
            case MINE, DIAMOND_MINE, AMBER_QUARRY -> "iron";
            default -> pet.name().toLowerCase(Locale.ROOT);
        });
    }

    private int soldThisHour(Player player, Pet pet, long now) {
        return soldThisHour(saleKey(player, pet), now);
    }

    private int soldThisHour(String key, long now) {
        List<Long> quota = data.getLongList(key);
        return quota.size() == 2 && quota.get(0) == now / 3_600_000L
            ? Math.max(0, quota.get(1).intValue()) : 0;
    }

    private int saleRemaining(Player player, Pet pet, long now) {
        int limit = hourlySaleLimit();
        return Math.max(0, limit - soldThisHour(player, pet, now));
    }

    private boolean persist() {
        Path temp = stateFile.resolveSibling("workshops.tmp");
        try {
            data.save(temp.toFile());
            Files.move(temp, stateFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException ex) {
            getLogger().severe("Failed to save data: " + ex);
            return false;
        }
    }

    String wildWorldName() { return getConfig().getString("wild-world", WILD); }

    String wildNetherWorldName() {
        String configured = getConfig().getString("wild-nether-world", "");
        if (configured != null && !configured.isBlank()) return configured;
        String wild = wildWorldName();
        String village = getConfig().getString("village-world", "village");
        for (World w : Bukkit.getWorlds())
            if (w.getEnvironment() == World.Environment.NETHER && !w.getName().equals(wild)
                && !w.getName().equals(village) && !w.getName().equals("pipi_tutorial")) return w.getName();
        return null;
    }

    boolean isWildWorld(String name) {
        return name != null && (name.equals(wildWorldName()) || name.equals(wildNetherWorldName()));
    }

    boolean inWild(Player player) {
        return player.getWorld().getName().equals(getConfig().getString("wild-world", WILD))
            && player.getGameMode() == GameMode.SURVIVAL;
    }

    boolean canUseTrap(Player player) {
        if (inWild(player)) return true;
        String world = getConfig().getString("wild-world", WILD);
        if (!isWildWorld(player.getWorld().getName()))
            player.sendMessage("포획은 야생 월드(" + world + ")에서만 가능합니다.");
        else player.sendMessage("포획은 서바이벌 모드에서만 가능합니다. 관리자 테스트: /gamemode survival");
        return false;
    }

    Integer trapTier(ItemStack item) {
        return item != null && item.hasItemMeta()
            ? item.getItemMeta().getPersistentDataContainer().get(trapKey, PersistentDataType.INTEGER) : null;
    }

    private Pet eligible(LivingEntity entity) {
        if (entity.getScoreboardTags().contains("pipi_pet_worker") || entity.isDead()) return null;
        String species = entity.getPersistentDataContainer().get(NamespacedKey.fromString("pipilife:pet_species"), PersistentDataType.STRING);
        return petBySkin(species);
    }

    boolean captureEligible(LivingEntity entity) { return eligible(entity) != null; }
    /** Label of a Lv.2/Lv.3 wild pet (which may flee after a failed capture), else null. */
    String captureFleeLabel(LivingEntity entity) {
        Pet pet = eligible(entity);
        return pet != null && pet.level >= 2 && pet.level <= 3 ? pet.label : null;
    }
    double captureRate(LivingEntity entity) {
        Pet pet = eligible(entity);
        return pet == null ? 0 : pet.captureRate();
    }
    int petMaxHp(String skin) {
        Pet pet = petBySkin(skin);
        return pet == null ? 20 : pet.maxHp();
    }
    boolean captureHasRoom(Player player) { return storageEntries(player).contains(""); }
    int captureFreeSlots(Player player) { return (int) storageEntries(player).stream().filter(String::isEmpty).count(); }
    boolean completeCapture(Player player, LivingEntity entity) {
        Pet pet = eligible(entity);
        if (pet == null) return false;
        String path = path(player, pet);
        boolean alreadyOwned = data.isConfigurationSection(path);
        List<String> box = storageEntries(player);
        int free = box.indexOf("");
        if (free < 0) { player.sendMessage("펫 창고가 가득 찼습니다."); return false; }
        if (alreadyOwned) {
            box.set(free, pet.name() + ":" + UUID.randomUUID());
            if (!store(storageKey(player), box)) { player.sendMessage("펫 저장에 실패했습니다."); return false; }
            entity.remove();
            player.sendMessage(pet.label + " 포획 성공. 펫 창고에서 확인하세요.");
            return true;
        }
        long now = System.currentTimeMillis();
        if (!save(path, new Workshop(0, 0, now, false))) {
            data.set(path, null);
            player.sendMessage("펫 저장에 실패했습니다.");
            return false;
        }
        entity.remove();
        player.sendMessage(pet.label + " 포획 성공. /pipilife pets");
        return true;
    }

    String plotAt(Location location) {
        if (!location.getWorld().getName().equals(getConfig().getString("village-world", "village"))) return null;
        var plots = data.getConfigurationSection("plots");
        if (plots == null) return null;
        for (String name : plots.getKeys(false)) {
            int x = plots.getInt(name + ".x"), z = plots.getInt(name + ".z");
            if (location.getBlockX() >= x && location.getBlockX() < x + 44
                && location.getBlockZ() >= z && location.getBlockZ() < z + 44) return name;
        }
        return null;
    }

    private boolean plotCommand(Player player, String[] args) {
        String uuid = player.getUniqueId().toString();
        if (args[0].equalsIgnoreCase("register") && args.length == 2 && player.isOp()
            && args[1].matches("[a-z0-9_-]{1,32}")) {
            if (!player.getWorld().getName().equals(getConfig().getString("village-world", "village"))) {
                player.sendMessage("마을 월드에서 등록하세요.");
                return true;
            }
            String path = "plots." + args[1];
            int x = player.getLocation().getBlockX(), z = player.getLocation().getBlockZ();
            var plots = data.getConfigurationSection("plots");
            if (plots != null) for (String name : plots.getKeys(false)) {
                int ox = plots.getInt(name + ".x"), oz = plots.getInt(name + ".z");
                if (name.equals(args[1]) || Math.abs((long) x - ox) < 44 && Math.abs((long) z - oz) < 44) {
                    player.sendMessage("이미 등록된 이름이거나 기존 부지와 겹칩니다.");
                    return true;
                }
            }
            data.createSection(path, Map.of("x", x, "z", z));
            if (!persist()) {
                data.set(path, null);
                player.sendMessage("부지 저장 실패.");
            } else player.sendMessage("44×44 부지 등록 완료 " + args[1]);
            return true;
        }
        if (args.length != 1 || args[0].equalsIgnoreCase("register")) {
            player.sendMessage("관리자 부지 등록: /pipilife register <이름>");
            return true;
        }
        String plot = plotAt(player.getLocation());
        if (plot == null) {
            player.sendMessage("등록된 거주지 안에서 사용하세요.");
            return true;
        }
        String path = "plots." + plot + ".owner";
        String owner = data.getString(path);
        if (args[0].equalsIgnoreCase("plot")) {
            player.sendMessage(plot + (owner == null ? " · 빈 부지" : owner.equals(uuid) ? " · 내 부지" : " · 배정됨"));
            return true;
        }
        var plots = data.getConfigurationSection("plots");
        for (String name : plots.getKeys(false)) if (uuid.equals(plots.getString(name + ".owner"))) {
            player.sendMessage("이미 거주지가 있습니다.");
            return true;
        }
        if (owner != null) {
            player.sendMessage("이미 선점된 거주지입니다.");
            return true;
        }
        data.set(path, uuid);
        if (!persist()) {
            data.set(path, null);
            player.sendMessage("선점 저장 실패.");
        } else player.sendMessage(plot + " 부지를 선점했습니다.");
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("capture", "pets", "petstorage", "skill", "workstation", "warp"));
            if (sender.isOp()) out.addAll(List.of("petspawn", "spawninfo", "armory"));
            if (regions != null && (sender.isOp() || sender.hasPermission("pipilife.admin"))) out.add("region");
            if (sender.isOp() || sender.hasPermission("pipilife.admin")) out.add("dummy");
        } else if (dummies != null && args[0].equalsIgnoreCase("dummy") && (sender.isOp() || sender.hasPermission("pipilife.admin"))) {
            out.addAll(dummies.complete(args));
        } else if (warps != null && args[0].equalsIgnoreCase("warp")) {
            out.addAll(warps.complete(sender, args));
        } else if (regions != null && args[0].equalsIgnoreCase("region")) {
            out.addAll(regions.complete(sender, args));
        } else if (sender.isOp() && args[0].equals("petspawn")) {
            if (args.length == 2) for (Pet p : Pet.values()) out.add(p.name().toLowerCase(Locale.ROOT));
            else if (args.length == 3) out.addAll(List.of("1", "2", "3", "4", "5"));
        } else if (sender.isOp() && args[0].equals("spawninfo") && args.length == 2) {
            out.add("all");
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (args.length > 0 && args[0].equalsIgnoreCase("region")) {
            if (regions == null) { player.sendMessage("보호 구역 기능을 불러오지 못했습니다."); return true; }
            return regions.command(player, args);
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("dummy")) return dummies.command(player, args);
        if(args.length>0 && args[0].equals("petspawn")){if(customPets!=null)return customPets.command(player,args);player.sendMessage("ModelEngine과 전용 펫 모델을 먼저 로드하세요.");return true;}
        if(args.length>0 && args[0].equals("spawninfo")){if(!player.isOp()){player.sendMessage("§c관리자 전용 명령어입니다.");return true;}if(customPets!=null)return customPets.spawnInfo(player,args);player.sendMessage("ModelEngine과 전용 펫 모델을 먼저 로드하세요.");return true;}
        if(args.length>0 && args[0].equals("capture")) return captures.command(player,args);
        if (args.length > 0 && args[0].equalsIgnoreCase("brag")) return social.brag(player);
        if (args.length > 0 && args[0].equals("petstorage")) return storageCommand(player, args);
        if (args.length > 0 && args[0].equals("market")) return marketCommand(player, args);
        if (args.length > 0 && args[0].equals("armory")) return armory.command(player, args);
        if (args.length > 0 && args[0].equals("combat")) return armory.combatCommand(player, args);
        if (args.length > 0 && args[0].equals("skill")) {
            if (args.length >= 2 && args[1].equals("loadout")) return skills.loadoutCommand(player, args);
            if (args.length == 2 && args[1].equals("basic")) return skills.cast(player, 5);
            if (args.length == 2 && args[1].equals("dodge")) { skills.dashInput(player, 0, 0); return skills.cast(player, 6); }
            if (args.length == 4 && args[1].equals("dodge")) {
                double strafe = 0, forward = 0;
                try { strafe = Double.parseDouble(args[2]); forward = Double.parseDouble(args[3]); } catch (NumberFormatException ignored) { }
                skills.dashInput(player, strafe, forward);
                return skills.cast(player, 6);
            }
            if (args.length == 2 && args[1].equals("heal")) return skills.cast(player, 0);
            if (args.length == 2 && args[1].equals("ult")) return skills.cast(player, 4);
            if (args.length == 2 && args[1].equals("shield")) return skills.cast(player, 7);
            if (args.length == 2 && args[1].equals("attack")) return skills.cast(player, 1);
            if (args.length == 3 && args[1].equals("attack") && Set.of("1", "2", "3").contains(args[2]))
                return skills.cast(player, Integer.parseInt(args[2]));
            player.sendMessage("/pipilife skill attack <1|2|3> · heal · shield · ult · basic · dodge");
            return true;
        }
        if (args.length > 0 && args[0].equals("testgive")) return town.testGive(player, args);
        if (args.length > 0 && args[0].equals("workstation")) return workstation(player, args);
        if (args.length > 0 && args[0].equals("cook")) return cooking.command(player, args);
        if (warps != null && args.length > 0 && args[0].equalsIgnoreCase("warp") && warps.handles(args)) return warps.command(player, args);
        if (args.length > 0 && Set.of("deed","home","warp","shop","research","process","forge","heal","party","trade","alchemy","inherit").contains(args[0].toLowerCase(Locale.ROOT)))
            return town.command(player, args);
        if (args.length == 1 && Set.of("profile", "pets").contains(args[0])) return args[0].equals("pets") ? openPetStorage(player) : openStatus(player, false);
        if (args.length == 1 && args[0].equalsIgnoreCase("travel")) return openTravel(player);
        if (args.length == 1 && args[0].equalsIgnoreCase("compass")) {
            giveCompass(player);
            return true;
        }
        if (args.length > 0 && Set.of("maps", "finish").contains(args[0].toLowerCase(Locale.ROOT))) return mapCommand(player, args);
        if (args.length > 0 && Set.of("register", "claim", "plot").contains(args[0].toLowerCase(Locale.ROOT)))
            return plotCommand(player, args);
        if (args.length == 1 && args[0].equalsIgnoreCase("station")) {
            player.sendMessage("제작한 식량·벌목·채광·기본 작업대를 내 부지에 설치하고 우클릭하세요.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage("/pipilife home · warp · shop · research · process · forge · alchemy · inherit · party · trade · heal");
            player.sendMessage("/pipilife claim · /pipilife plot · 관리자: /pipilife register <이름>");
            player.sendMessage("/pipilife <assign|stop|feed|collect|sell|status> <sparrow|cat|rabbit|fox|mole>");
            player.sendMessage("펫의 적성 레벨은 /pipilife pets에서 확인하세요.");
            return true;
        }
        Pet pet;
        try {
            if (args.length != 2) throw new IllegalArgumentException();
            pet = Pet.valueOf(args[1].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            player.sendMessage("/pipilife 로 사용법을 확인하세요.");
            return true;
        }
        String path = path(player, pet);
        if (!data.isConfigurationSection(path)) {
            player.sendMessage("아직 " + pet.label + "를 포획하지 않았습니다.");
            return true;
        }
        String plot = plotAt(player.getLocation());
        if (!args[0].equalsIgnoreCase("status") && (plot == null
            || !player.getUniqueId().toString().equals(data.getString("plots." + plot + ".owner")))) {
            player.sendMessage("내 거주지 안에서 작업장을 이용하세요.");
            return true;
        }
        long now = System.currentTimeMillis();
        Workshop state = workshop(player, pet, now);
        Workshop next;
        String saleKey = saleKey(player, pet);
        Object previousSale = data.get(saleKey);
        Object previousBinding = data.get(path + ".facility-bound");
        Runnable apply = () -> {};
        String message;
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "assign" -> {
                if (pet.aptitude == Facility.CRAFT || pet.aptitude.isProducer() || !hasFacility(plot, pet.aptitude)) {
                    player.sendMessage("해당 적성의 작업대를 먼저 설치하세요. 가공 배치는 기본 작업대에서 합니다.");
                    return true;
                }
                if (pet.name().equals(data.getString(craftWorkerKey(player)))) {
                    player.sendMessage("기본 작업대에 배치된 펫입니다. 먼저 가공 배치를 해제하세요.");
                    return true;
                }
                for (Pet other : Pet.values()) {
                    if (other != pet && other.aptitude == pet.aptitude && workshop(player, other, now).active()) {
                        player.sendMessage("이 작업대에는 이미 펫이 배치되어 있습니다.");
                        return true;
                    }
                }
                data.set(path + ".facility-bound", true);
                next = state.active() ? state : new Workshop(state.food(), state.stock(), now, true);
                message = pet.label + "를 작업에 배치했습니다.";
            }
            case "stop" -> {
                if (pet.aptitude.isProducer()) { player.sendMessage("생산 시설 화면에서 작업을 중단하세요."); return true; }
                next = new Workshop(state.food(), state.stock(), now, false);
                message = "작업을 중단했습니다.";
            }
            case "feed" -> {
                ItemStack hand = player.getInventory().getItemInMainHand();
                int nutrition = town.nutrition(hand);
                if (nutrition == 0 || MAX_FOOD - state.food() < nutrition) {
                    player.sendMessage("펫 전용 먹이(" + town.foodSummary() + ")를 손에 드세요. 포만감 용량은 100입니다.");
                    return true;
                }
                int count = Math.min((MAX_FOOD - state.food()) / nutrition, hand.getAmount());
                next = new Workshop(state.food() + count * nutrition, state.stock(),
                    state.food() == 0 ? now : state.since(), state.active());
                apply = () -> hand.setAmount(hand.getAmount() - count);
                message = "먹이 " + count + "개를 넣었습니다. 포만감 +" + count * nutrition;
            }
            case "collect" -> {
                if (pet.aptitude.isProducer()) { player.sendMessage("생산 시설에서 Shift+좌클릭으로 수령하세요."); return true; }
                int slot = player.getInventory().firstEmpty();
                if (slot < 0 || state.stock() == 0) {
                    player.sendMessage("생산품을 수령하려면 재고와 빈 인벤토리 칸이 필요합니다.");
                    return true;
                }
                next = new Workshop(state.food(), 0, state.stock() >= state.cap() ? now : state.since(), state.active());
                apply = () -> player.getInventory().setItem(slot, new ItemStack(pet.product(), state.stock()));
                message = "생산품 " + state.stock() + "개를 받았습니다.";
            }
            case "sell" -> {
                if (pet.aptitude.isProducer()) { player.sendMessage("생산품을 수령한 뒤 상점에서 판매하세요."); return true; }
                var board = Bukkit.getScoreboardManager().getMainScoreboard();
                var money = board.getObjective("en_money");
                if (money == null) money = board.registerNewObjective("en_money", "dummy", "골드");
                if (state.stock() == 0) {
                    player.sendMessage("판매할 재고가 없습니다.");
                    return true;
                }
                var score = money.getScore(player.getName());
                int sold = soldThisHour(player, pet, now);
                int count = Math.min(state.stock(), saleRemaining(player, pet, now));
                if (count == 0) {
                    player.sendMessage("이번 시간의 판매 한도에 도달했습니다. 정각에 초기화됩니다.");
                    return true;
                }
                int amount = count * pet.salePrice();
                long total = (long) score.getScore() + amount;
                if (total > Integer.MAX_VALUE) {
                    player.sendMessage("잔액 한도를 초과합니다.");
                    return true;
                }
                next = new Workshop(state.food(), state.stock() - count, state.stock() >= state.cap() ? now : state.since(), state.active());
                data.set(saleKey, List.of(now / 3_600_000L, sold + count));
                apply = () -> score.setScore((int) total);
                message = "생산품 " + count + "개를 판매해 " + amount + "원을 받았습니다." +
                    (count < state.stock() ? " 나머지 " + (state.stock() - count) + "개는 작업장에 남겼습니다." : "");
            }
            case "status" -> {
                player.sendMessage(pet.label + " · " + (state.active() ? "배치됨" : "대기") +
                    " · 먹이 " + state.food() + "/64 · 생산품 " + state.stock() + "/" + state.cap());
                return true;
            }
            default -> {
                player.sendMessage("/pipilife 로 사용법을 확인하세요.");
                return true;
            }
        }
        if (!save(path, next)) {
            data.set(saleKey, previousSale);
            data.set(path + ".facility-bound", previousBinding);
            player.sendMessage("작업장 저장에 실패했습니다.");
            return true;
        }
        apply.run();
        player.sendMessage(message);
        return true;
    }

    boolean owns(Player player, String plot) {
        return plot != null && player.getUniqueId().toString().equals(data.getString("plots." + plot + ".owner"));
    }


    private static String aptitudeLabel(Facility facility) {
        return switch (facility) {
            case FARM, CARROT_FARM, POTATO_FARM, CABBAGE_FARM -> "농사";
            case LUMBER, REDWOOD_LUMBER -> "벌목";
            case CRAFT -> "제작";
            case MINE, DIAMOND_MINE, AMBER_QUARRY -> "채광";
            case TRANSPORT -> "운반";
            default -> "";
        };
    }

    private static boolean sameAptitudeKind(Facility facility, Facility aptitude) {
        String label = aptitudeLabel(aptitude);
        return !label.isEmpty() && label.equals(aptitudeLabel(facility));
    }

    private int aptitude(Player player, Pet pet, Facility facility) {
        if (facility == null || pet == null || !sameAptitudeKind(facility, pet.aptitude)) return 0;
        return pet.level;
    }

    private Workshop workshop(Player player, Pet pet, long now) {
        String key = path(player, pet);
        if (pet.aptitude.isProducer()) return productionState(player, pet.name(), now);
        boolean active = pet.aptitude != Facility.CRAFT && !pet.aptitude.isProducer() && data.getBoolean(key + ".active")
            && data.getBoolean(key + ".facility-bound") && hasFacility(ownedPlot(player), pet.aptitude);
        Workshop state = new Workshop(data.getInt(key + ".food"), data.getInt(key + ".stock"),
            data.getLong(key + ".since"), active);
        int level = aptitude(player, pet, pet.aptitude);
        return level < 1 ? state : state.settle(now, Facility.duration(CYCLE, level));
    }

    private String facilityKey(String plot, Facility facility) {
        return "plots." + plot + ".facilities." + facility.name();
    }

    private List<List<Integer>> facilityPositions(String plot, Facility facility) {
        if (plot == null) return new ArrayList<>();
        Object raw = data.get(facilityKey(plot, facility));
        List<List<Integer>> positions = new ArrayList<>();
        if (raw instanceof List<?> list) {
            if (list.size() == 3 && list.stream().allMatch(Number.class::isInstance))
                positions.add(list.stream().map(value -> ((Number) value).intValue()).toList());
            else for (Object entry : list) if (entry instanceof List<?> point && point.size() == 3
                && point.stream().allMatch(Number.class::isInstance))
                positions.add(point.stream().map(value -> ((Number) value).intValue()).toList());
        }
        return positions;
    }

    private List<org.bukkit.block.Block> facilityBlocks(String plot, Facility facility) {
        World world = Bukkit.getWorld(maps.village());
        if (world == null) return List.of();
        return facilityPositions(plot, facility).stream()
            .map(point -> world.getBlockAt(point.get(0), point.get(1), point.get(2)))
            .filter(block -> facility.matches(block.getType())).toList();
    }

    private org.bukkit.block.Block facilityBlock(String plot, Facility facility) {
        return facilityBlocks(plot, facility).stream().findFirst().orElse(null);
    }

    private boolean hasFacility(String plot, Facility facility) { return facilityBlock(plot, facility) != null; }

    Facility facilityAt(org.bukkit.block.Block block) {
        if (block == null) return null;
        String plot = plotAt(block.getLocation());
        if (plot == null) return null;
        for (Facility facility : Facility.values()) {
            if (facility.matches(block.getType()) && facilityPositions(plot, facility)
                .contains(List.of(block.getX(), block.getY(), block.getZ()))) return facility;
        }
        return null;
    }

    boolean legacyStation(org.bukkit.block.Block block, String plot) {
        return plot != null && block.getType() == Material.BARREL && data.getIntegerList("plots." + plot + ".station")
            .equals(List.of(block.getX(), block.getY(), block.getZ()));
    }

    ItemStack stationItem(Facility facility) {
        ItemStack result = statusIcon(facility.blueprintMaterial(), facility.label + " 청사진", "내 거주지 바닥을 우클릭하여 설치");
        var meta = result.getItemMeta();
        meta.getPersistentDataContainer().set(stationKey, PersistentDataType.STRING, facility.name());
        result.setItemMeta(meta);
        return result;
    }

    /** Blueprint that keeps the dismantled facility's level (>= 2); the plain item for level 1. */
    ItemStack stationItem(Facility facility, int level) {
        ItemStack result = stationItem(facility);
        if (level < 2) return result;
        level = Math.min(level, MAX_FACILITY_LEVEL);
        var meta = result.getItemMeta();
        meta.setDisplayName("§f" + level + "레벨 " + facility.label);
        meta.setLore(List.of("§7레벨 " + level + " 유지", "§7내 거주지 바닥을 우클릭하여 설치"));
        meta.getPersistentDataContainer().set(facilityLevelKey, PersistentDataType.INTEGER, level);
        result.setItemMeta(meta);
        return result;
    }

    /** Level carried by a leveled blueprint item, or 0 for plain/non-blueprint items. */
    static int facilityLevel(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        Integer level = facilityLevelKey == null ? null
            : item.getItemMeta().getPersistentDataContainer().get(facilityLevelKey, PersistentDataType.INTEGER);
        return level == null ? 0 : level;
    }

    private String facilityLevelDataKey(Player player, String plot, Facility facility, org.bukkit.block.Block anchor) {
        if (facility == Facility.PET_STORAGE) return storageLevelKey(player);
        if (!facility.isProducer()) return null;
        return producerKey(plot, facility, coordinates(anchor)) + ".level";
    }

    private int storedFacilityLevel(Player player, String plot, Facility facility, org.bukkit.block.Block anchor) {
        String key = facilityLevelDataKey(player, plot, facility, anchor);
        return key == null ? 1 : data.getInt(key, 1);
    }

    private void applyFacilityLevel(Player player, String plot, Facility facility, org.bukkit.block.Block anchor, ItemStack item) {
        int level = facilityLevel(item);
        String key = facilityLevelDataKey(player, plot, facility, anchor);
        if (level < 2 || key == null) return;
        data.set(key, Math.clamp(level, 1, facility == Facility.PET_STORAGE ? LEGACY_STORAGE_LEVEL : MAX_FACILITY_LEVEL));
        persist();
    }

    private void registerStationRecipe(Facility facility) {
        ItemStack result = stationItem(facility);
        var recipe = new ShapedRecipe(new NamespacedKey(this, "facility_" + facility.name().toLowerCase(Locale.ROOT)), result);
        recipe.shape(" I ", " B ", " I ");
        recipe.setIngredient('I', facility.ingredient);
        recipe.setIngredient('B', facility.block);
        Bukkit.addRecipe(recipe);
    }

    private static final Material FEEDER_WALL = Material.DARK_OAK_PLANKS;

    private Facility blueprint(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String kind = item.getItemMeta().getPersistentDataContainer().get(stationKey, PersistentDataType.STRING);
        if (kind == null) return null;
        try { Facility f = Facility.valueOf(kind); return f == Facility.GOLD_MINE ? null : f; }
        catch (IllegalArgumentException ignored) { return null; }
    }

    boolean isBlueprintItem(ItemStack item) { return blueprint(item) != null; }

    private void migrateBlueprintInventory(Player player) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack old = inventory.getItem(slot);
            Facility facility = blueprint(old);
            if (facility == null || old.getType() == facility.blueprintMaterial()) continue;
            ItemStack replacement = new ItemStack(facility.blueprintMaterial(), old.getAmount());
            replacement.setItemMeta(old.getItemMeta());
            inventory.setItem(slot, replacement);
        }
    }

    @EventHandler
    public void migrateBlueprintsOnJoin(PlayerJoinEvent event) {
        migrateBlueprintInventory(event.getPlayer());
        town.convertOld(event.getPlayer());
    }

    @EventHandler
    public void migrateBlueprintsOnOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) {
            var opened = event.getInventory();
            Bukkit.getScheduler().runTask(this, () -> {
                migrateBlueprintInventory(player);
                town.convertOld(player);
                town.convertOld(opened);
            });
        }
    }

    @EventHandler
    public void convertOldOnPickup(org.bukkit.event.entity.EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        var stack = event.getItem().getItemStack();
        if (town.isRemovedItem(stack)) {
            event.setCancelled(true);
            event.getItem().remove();
            return;
        }
        ItemStack replacement = town.convertOld(stack);
        if (replacement != null) event.getItem().setItemStack(replacement);
    }

    private void keepFull(Player player) {
        if (player.getFoodLevel() != FIXED_FOOD) player.setFoodLevel(FIXED_FOOD);
        if (player.getSaturation() != 0f) player.setSaturation(0f);
        if (player.getExhaustion() != 0f) player.setExhaustion(0f);
    }

    /** Food bar is pinned at 17 (full minus half a shank): no natural regeneration (needs 18), sprinting still works. */
    private static final int FIXED_FOOD = 19;

    private void keepEveryoneFull() {
        for (Player player : Bukkit.getOnlinePlayers()) keepFull(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void noHunger(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        event.setCancelled(true);
        keepFull(player);
    }

    @EventHandler
    public void noHungerOnJoin(PlayerJoinEvent event) {
        keepFull(event.getPlayer());
    }

    @EventHandler
    public void noHungerOnRespawn(PlayerRespawnEvent event) {
        keepFull(event.getPlayer());
    }

    @EventHandler
    public void noHungerOnWorldChange(PlayerChangedWorldEvent event) {
        keepFull(event.getPlayer());
    }
    private boolean validFootprint(org.bukkit.block.Block anchor, String plot, int width, boolean placed) {
        return validFootprint(anchor, plot, width, width, placed);
    }
    private boolean validFootprint(org.bukkit.block.Block anchor, String plot, int width, int depth, boolean placed) {
        for (int x = 0; x < width; x++) for (int z = 0; z < depth; z++) {
            var cell = anchor.getRelative(x, 0, z);
            if (!Objects.equals(plot, plotAt(cell.getLocation()))
                || (!(placed && x == 0 && z == 0) && !cell.getType().isAir())) return false;
            if (!cell.getRelative(0, -1, 0).getType().isSolid()
                || width == 2 && !cell.getRelative(0, 1, 0).getType().isAir()) return false;
        }
        return true;
    }

    private void previewStations() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Facility facility = blueprint(player.getInventory().getItemInMainHand());
            if (facility == null || !owns(player, plotAt(player.getLocation()))) continue;
            var hit = player.rayTraceBlocks(5);
            if (hit == null || hit.getHitBlock() == null || hit.getHitBlockFace() == null) continue;
            var anchor = hit.getHitBlock().getRelative(hit.getHitBlockFace());
            String plot = plotAt(anchor.getLocation());
            if (!owns(player, plot)) continue;
            int width = facility.width(); int depth = facility.depth();
            boolean allowed = hit.getHitBlockFace() == org.bukkit.block.BlockFace.UP
                && validFootprint(anchor, plot, width, depth, false);
            var dust = new Particle.DustOptions(allowed ? Color.LIME : Color.RED, .9f);
            var base = anchor.getLocation();
            for(int step=0;step<=width*4;step++) for(int edge:new int[]{0,depth}) player.spawnParticle(Particle.DUST,base.clone().add(step/4.0,.06,edge),1,dust);
            for(int step=0;step<=depth*4;step++) for(int edge:new int[]{0,width}) player.spawnParticle(Particle.DUST,base.clone().add(edge,.06,step/4.0),1,dust);
        }
    }

    private boolean wideFeeder(org.bukkit.block.Block anchor) {
        return wideStation(anchor) || anchor.getState() instanceof org.bukkit.block.Barrel barrel
            && barrel.getPersistentDataContainer().has(new NamespacedKey(this, "wide_feeder"), PersistentDataType.BYTE);
    }

    private org.bukkit.block.Block feederAnchor(org.bukkit.block.Block wall) {
        if (wall == null || wall.getType() != FEEDER_WALL && wall.getType() != Material.BARRIER) return null;
        for (int x = 0; x <= 2; x++) for (int z = 0; z <= 2; z++) {
            if (x == 0 && z == 0) continue;
            var anchor = wall.getRelative(-x, 0, -z);
            Facility facility = facilityAt(anchor);
            if (facility != null && x < data.getInt(facilityViewKey(anchor)+".width",2) && z < data.getInt(facilityViewKey(anchor)+".depth",2) && (wall.getType() == Material.BARRIER ? wideStation(anchor) : facility == Facility.FEEDER && wideFeeder(anchor))) return anchor;
        }
        return null;
    }

    private NamespacedKey wideStationKey(org.bukkit.block.Block anchor) {
        return new NamespacedKey(this, "wide_station/" + (anchor.getX() & 15) + "/" + anchor.getY() + "/" + (anchor.getZ() & 15));
    }

    private String facilityViewKey(org.bukkit.block.Block anchor) {
        return "facility-views." + anchor.getWorld().getName() + "." + anchor.getX() + "_" + anchor.getY() + "_" + anchor.getZ();
    }

    private boolean wideStation(org.bukkit.block.Block anchor) {
        return data.getBoolean(facilityViewKey(anchor) + ".wide") || anchor.getChunk().getPersistentDataContainer().has(wideStationKey(anchor), PersistentDataType.BYTE);
    }

    private String feederModelTag(org.bukkit.block.Block anchor) {
        return "pipi_feeder_" + anchor.getX() + "_" + anchor.getY() + "_" + anchor.getZ();
    }

    private static final String STATION_V2_TAG = "pipi_station_v2";
    private static final String STATION_V3_TAG = "pipi_station_v3"; // quarry rock-pile models (3x3, scale = footprint/3)

    private Material stationModelItem(Facility facility) {
        return switch (facility) {
            case FARM, CARROT_FARM, POTATO_FARM, CABBAGE_FARM, MINE, DIAMOND_MINE, AMBER_QUARRY, GOLD_MINE, LUMBER, REDWOOD_LUMBER ->
                Material.matchMaterial("PIPIFACILITIES_STATION_" + facility.name());
            default -> null;
        };
    }

    /** Replace old (ItemsAdder) displays of farm/quarry/lumber stations in this chunk with the new mod models. */
    private void refreshStationModels(org.bukkit.Chunk chunk) {
        // drop leftover station displays whose facility is gone (e.g. dismantled before the removal radius covered 3x3 quarries)
        for (var entity : chunk.getEntities()) {
            if (!(entity instanceof org.bukkit.entity.ItemDisplay) || !entity.getScoreboardTags().contains(STATION_V2_TAG)) continue;
            for (String t : entity.getScoreboardTags()) {
                if (!t.startsWith("pipi_feeder_")) continue;
                String[] c = t.substring("pipi_feeder_".length()).split("_");
                try {
                    if (c.length == 3 && chunk.getWorld().isChunkLoaded(Integer.parseInt(c[0]) >> 4, Integer.parseInt(c[2]) >> 4) && facilityAt(chunk.getWorld().getBlockAt(Integer.parseInt(c[0]), Integer.parseInt(c[1]), Integer.parseInt(c[2]))) == null) entity.remove();
                } catch (NumberFormatException ignored) { }
            }
        }
        var section = data.getConfigurationSection("facility-views." + chunk.getWorld().getName());
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            String[] p = key.split("_");
            if (p.length != 3) continue;
            try {
                int x = Integer.parseInt(p[0]), y = Integer.parseInt(p[1]), z = Integer.parseInt(p[2]);
                if ((x >> 4) != chunk.getX() || (z >> 4) != chunk.getZ()) continue;
                var block = chunk.getWorld().getBlockAt(x, y, z);
                Facility facility = facilityAt(block);
                if (facility == null || stationModelItem(facility) == null) continue;
                showStationModel(block, facility);
            } catch (NumberFormatException ignored) { }
        }
    }

    @EventHandler
    public void stationEntitiesLoaded(org.bukkit.event.world.EntitiesLoadEvent event) {
        if (data != null) refreshStationModels(event.getChunk());
    }

    private void showStationModel(org.bukkit.block.Block anchor, Facility facility) {
        Material modItem = stationModelItem(facility);
        if (modItem == null && !Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) return;
        boolean wide = facility == Facility.FEEDER ? wideFeeder(anchor) : wideStation(anchor);
        Location center = anchor.getLocation().add(wide ? facility.width()/2.0 : .5, 1, wide ? facility.depth()/2.0 : .5);
        String tag = feederModelTag(anchor);
        var existing = center.getWorld().getNearbyEntities(center, 8, 8, 8).stream()
            .filter(entity -> entity.getScoreboardTags().contains(tag)).toList();
        if (!existing.isEmpty()) {
            String need = facility.isQuarry() ? STATION_V3_TAG : STATION_V2_TAG;
            if (modItem == null || existing.stream().allMatch(entity -> entity.getScoreboardTags().contains(need))) return;
            existing.forEach(Entity::remove); // old ItemsAdder model -> replaced below
        }
        if (modItem != null) {
            int fw = wide ? data.getInt(facilityViewKey(anchor) + ".width", facility.width()) : 1;
            int fd = wide ? data.getInt(facilityViewKey(anchor) + ".depth", facility.depth()) : 1;
            // wheat field model is 24x16 px (x -4..20, centred on the 0..16 cube): scale 2 -> exactly 3x2 blocks, no offset needed
            // quarry rock pile is 48x48 px (-16..32): scale footprint/3 -> fills the 3x3 (or an older 2x2) area
            float scale = !wide ? 1f : facility == Facility.FARM ? 2f : facility.isQuarry() ? Math.max(fw, fd) / 3f : Math.max(fw, fd);
            Location at = anchor.getLocation().add(wide ? fw/2.0 : .5, scale / 2.0, wide ? fd/2.0 : .5);
            at.getWorld().spawn(at, org.bukkit.entity.ItemDisplay.class, display -> {
                display.setItemStack(new ItemStack(modItem));
                display.setItemDisplayTransform(org.bukkit.entity.ItemDisplay.ItemDisplayTransform.FIXED);
                var transform = display.getTransformation();
                transform.getScale().set(scale);
                display.setTransformation(transform);
                display.setDisplayWidth(scale * 3f);
                display.setDisplayHeight(scale * 3f);
                display.addScoreboardTag(tag);
                display.addScoreboardTag(STATION_V2_TAG);
                if (facility.isQuarry()) display.addScoreboardTag(STATION_V3_TAG);
            });
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) return;
        try {
            Class<?> api = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Object model = api.getMethod("getInstance", String.class).invoke(null, "pipi_station:" + (facility == Facility.FEEDER ? "feeder_produce" : switch (facility) {
                case AMBER_QUARRY -> "diamond_mine";
                case CABBAGE_FARM -> "carrot_farm";
                case REDWOOD_LUMBER -> "lumber";
                case COOKING_TABLE -> "craft";
                case RANCH -> "farm";
                default -> facility.name().toLowerCase(Locale.ROOT);
            }));
            if (model == null) return;
            ItemStack stack = (ItemStack) api.getMethod("getItemStack").invoke(model);
            center.getWorld().spawn(center, org.bukkit.entity.ItemDisplay.class, display -> {
                display.setItemStack(stack);
                display.setItemDisplayTransform(org.bukkit.entity.ItemDisplay.ItemDisplayTransform.FIXED);
                if (wide && facility != Facility.FEEDER) {
                    var transform = display.getTransformation();
                    transform.getScale().set(2);
                    display.setTransformation(transform);
                    display.setDisplayWidth(6f);
                    display.setDisplayHeight(6f);
                }
                display.addScoreboardTag(tag);
            });
        } catch (ReflectiveOperationException | LinkageError ex) {
            getLogger().warning("시설 모델을 불러오지 못했습니다: " + ex.getMessage());
        }
    }

    private void buildFeeder(org.bukkit.block.Block anchor) {
        data.set(facilityViewKey(anchor) + ".wide", true);
        persist();
        for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++)
            if (x != 0 || z != 0) anchor.getRelative(x, 0, z).setType(Material.BARRIER, false);
        feederInventory(anchor);
        showStationModel(anchor, Facility.FEEDER);
    }
    @EventHandler(priority = EventPriority.HIGH)
    public void placeBlueprint(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        Facility facility = blueprint(item);
        if (facility == null || !acceptsBlueprintMaterial(item.getType().name(), facility)) return;
        var ground = event.getClickedBlock();
        if (ground == null) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        var anchor = ground.getRelative(event.getBlockFace());
        String plot = plotAt(anchor.getLocation());
        int width = facility.width(); int depth = facility.depth();
        if (event.getBlockFace() != org.bukkit.block.BlockFace.UP || !ground.getType().isSolid()
            || !owns(player, plot) || !validFootprint(anchor, plot, width, depth, false)) {
            player.sendMessage("내 거주지의 비어 있는 바닥에 설치하세요.");
            return;
        }
        for (int x = 0; x < width; x++) for (int z = 0; z < depth; z++) {
            var block = anchor.getRelative(x, 0, z);
            var bounds = org.bukkit.util.BoundingBox.of(block);
            if (block.getWorld().getNearbyEntities(bounds).stream().anyMatch(entity -> entity instanceof LivingEntity)) {
                player.sendMessage("설치 공간에서 비켜 주세요.");
                return;
            }
        }
        var previous = anchor.getState();
        anchor.setType(facility.installedBlock(), false);
        if (!recordStation(anchor, plot, facility)) {
            previous.update(true, false);
            player.sendMessage("시설을 저장하지 못했습니다.");
            return;
        }
        if (player.getGameMode() != GameMode.CREATIVE) {
            if (item.getAmount() <= 1) player.getInventory().setItemInMainHand(null);
            else item.setAmount(item.getAmount() - 1);
        }
        if (facility != Facility.FEEDER) {
            anchor.getChunk().getPersistentDataContainer().set(wideStationKey(anchor), PersistentDataType.BYTE, (byte) 1);
            for (int x = 0; x < width; x++) for (int z = 0; z < depth; z++)
                if (x != 0 || z != 0) anchor.getRelative(x, 0, z).setType(Material.BARRIER, false);
        }
        applyFacilityLevel(player, plot, facility, anchor, item);
        finishStation(player, anchor, facility);
    }

    static boolean acceptsBlueprintMaterial(String material, Facility facility) {
        return facility != null && (material.equals("PAPER") || material.equals("PIPIFACILITIES_" + facility.name() + "_BLUEPRINT"));
    }
    private boolean recordStation(org.bukkit.block.Block anchor, String plot, Facility facility) {
        data.set(facilityViewKey(anchor) + ".width", facility.width());
        data.set(facilityViewKey(anchor) + ".depth", facility.depth());
        List<List<Integer>> positions = facilityPositions(plot, facility);
        positions.add(List.of(anchor.getX(), anchor.getY(), anchor.getZ()));
        data.set(facilityViewKey(anchor) + ".wide", true);
        return store(facilityKey(plot, facility), positions);
    }

    private void finishStation(Player player, org.bukkit.block.Block anchor, Facility facility) {
        if (facility == Facility.FEEDER) buildFeeder(anchor);
        else showStationModel(anchor, facility);
        player.sendMessage(facility.label + " 설치 완료. 우클릭하여 이용하세요.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void placeStation(BlockPlaceEvent event) {
        var item = event.getItemInHand();
        if (!item.hasItemMeta() || !item.getItemMeta().getPersistentDataContainer().has(stationKey, PersistentDataType.STRING)) return;
        String kind = item.getItemMeta().getPersistentDataContainer().get(stationKey, PersistentDataType.STRING);
        if (kind == null) return;
        Facility facility;
        try { facility = Facility.valueOf(kind); if (facility == Facility.GOLD_MINE) throw new IllegalArgumentException(); }
        catch (IllegalArgumentException ex) { event.setCancelled(true); return; }
        var block = event.getBlockPlaced();
        Player player = event.getPlayer();
        String plot = plotAt(block.getLocation());
        if (!owns(player, plot) || !facility.matches(block.getType())) {
            event.setCancelled(true);
            player.sendMessage("내 거주지에 설치하세요.");
            return;
        }
        if (!validFootprint(block, plot, facility.width(), facility.depth(), true) || event.getBlockAgainst().getY() != block.getY() - 1) {
            event.setCancelled(true);
            player.sendMessage("내 부지의 평평하고 비어 있는 공간에 설치하세요.");
            return;
        }
        if (!recordStation(block, plot, facility)) {
            event.setCancelled(true);
            player.sendMessage("시설을 저장하지 못했습니다.");
        } else {
            if (facility != Facility.FEEDER) for (int x=0;x<facility.width();x++) for(int z=0;z<facility.depth();z++)
                if(x!=0 || z!=0) block.getRelative(x,0,z).setType(Material.BARRIER,false);
            applyFacilityLevel(player, plot, facility, block, item);
            finishStation(player, block, facility);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void protectStation(BlockBreakEvent event) {
        if(facilityAt(event.getBlock())!=null||feederAnchor(event.getBlock())!=null){event.setCancelled(true);event.getPlayer().sendMessage("건설망치로 우클릭하여 철거하세요.");}
    }
    private void removeStation(BlockBreakEvent event) {
        var anchor = event.getBlock();
        if (feederAnchor(anchor) != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("시설의 기준 블록을 부수면 철거할 수 있습니다.");
            return;
        }
        Facility facility = facilityAt(anchor);
        if (facility == null) return;
        if (facility == Facility.FEEDER && !feederInventory(anchor).isEmpty()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("식량 저장소를 비운 후 철거하세요.");
            return;
        }
        Player player = event.getPlayer();
        String plot = plotAt(event.getBlock().getLocation());
        if (!owns(player, plot)) { event.setCancelled(true); return; }
        int keptLevel = storedFacilityLevel(player, plot, facility, anchor);
        String levelKey = facilityLevelDataKey(player, plot, facility, anchor);
        boolean busy = facility == Facility.CRAFT ? craftWorkerEntry(player, event.getBlock()) != null
            || data.contains(craftKey(player, event.getBlock(), "process"))
            : facility.isProducer() && craftEntries(player).stream().anyMatch(entry -> productionFacility(player, entry) == facility
                && assignedProducer(player, entry, facility).equals(coordinates(anchor))
                && (productionSlot(player, entry) > 0 || data.getInt(craftEntryPath(player, entry) + ".stock") > 0));
        List<List<Integer>> positions = facilityPositions(plot, facility);
        positions.remove(List.of(event.getBlock().getX(), event.getBlock().getY(), event.getBlock().getZ()));
        if (busy
            || facility.isProducer() && !store(legacyProducerKey(plot, facility) + ".anchor", legacyProducerAnchor(plot, facility))
            || facility == Facility.CRAFT && !pinCraftAnchor(player, plot) || !store(facilityKey(plot, facility), positions.isEmpty() ? null : positions)) {
            event.setCancelled(true);
            player.sendMessage("작업을 중단하고 배치를 해제한 뒤 철거하세요.");
        } else {
            if (facility == Facility.FEEDER && wideFeeder(anchor)) {
                for (int x = 0; x <= 1; x++) for (int z = 0; z <= 1; z++) {
                    var cell = anchor.getRelative(x, 0, z);
                    if ((x != 0 || z != 0) && cell.getType() == FEEDER_WALL) cell.setType(Material.AIR, false);
                }
            }
            if (wideStation(anchor)) {
                for (int x = 0; x < data.getInt(facilityViewKey(anchor)+".width",2); x++) for (int z = 0; z < data.getInt(facilityViewKey(anchor)+".depth",2); z++) {
                    var cell = anchor.getRelative(x, 0, z);
                    if ((x != 0 || z != 0) && cell.getType() == Material.BARRIER) cell.setType(Material.AIR, false);
                }
                anchor.getChunk().getPersistentDataContainer().remove(wideStationKey(anchor));
            }
            data.set(facilityViewKey(anchor), null);
            if (levelKey != null) data.set(levelKey, null);
            foodInventories.remove(facilityViewKey(anchor) + ".food");
            persist();
            String tag = feederModelTag(anchor);
            anchor.getWorld().getNearbyEntities(anchor.getLocation().add(1.5, 1, 1.5), 4, 4, 4).stream() // tag is per-anchor; wide box covers 3x3 quarry displays (lifted by scale/2)
                .filter(entity -> entity.getScoreboardTags().contains(tag)).forEach(org.bukkit.entity.Entity::remove);
            event.setDropItems(false);
            if (facility != Facility.GOLD_MINE)             event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), stationItem(facility, keptLevel)).setOwner(player.getUniqueId());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void facilityExplosion(org.bukkit.event.entity.EntityExplodeEvent event) {
        event.blockList().removeIf(block -> facilityAt(block) != null || feederAnchor(block) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void facilityBlockExplosion(org.bukkit.event.block.BlockExplodeEvent event) {
        event.blockList().removeIf(block -> facilityAt(block) != null || feederAnchor(block) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void facilityPistonExtend(org.bukkit.event.block.BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> facilityAt(block) != null || feederAnchor(block) != null)) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void facilityPistonRetract(org.bukkit.event.block.BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> facilityAt(block) != null || feederAnchor(block) != null)) event.setCancelled(true);
    }

    private void feedAssignedPets() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (var block : facilityBlocks(ownedPlot(player), Facility.FEEDER)) {
            Inventory inventory = feederInventory(block);
            Set<String> workers = craftWorkers(player);
            for (String entry : craftEntries(player)) {
                String key = craftEntryPath(player, entry);
                Workshop state = productionState(player, entry, now);
                if (!state.active() && !workers.contains(entry)) continue;
                boolean waitingForFood = facilityBlocks(ownedPlot(player), Facility.CRAFT).stream().anyMatch(station ->
                    entry.equals(craftWorkerEntry(player, station))
                        && data.getLong(craftKey(player, station, "process") + "-hungry") > 0);
                if (state.food() > 10 && !waitingForFood && !data.getBoolean(key + ".refilling")) continue;
                ItemStack[] contents = inventory.getStorageContents();
                int food = state.food();
                for (int i = 0; i < contents.length && food < MAX_FOOD; i++) {
                    ItemStack stack = contents[i];
                    int nutrition = town.storageNutrition(stack);
                    if (nutrition <= 0) continue;
                    int amount = Math.min(stack.getAmount(), (MAX_FOOD - food + nutrition - 1) / nutrition);
                    food = Math.min(MAX_FOOD, food + nutrition * amount);
                    contents[i] = amount == stack.getAmount() ? null : stack.clone();
                    if (contents[i] != null) contents[i].setAmount(stack.getAmount() - amount);
                }
                if (food == state.food()) continue;
                Object previous = data.get(key + ".refilling");
                data.set(key + ".refilling", food < MAX_FOOD ? true : null);
                if (save(key, new Workshop(food, state.stock(), state.food() == 0 ? now : state.since(), state.active())))
                    { inventory.setStorageContents(contents); saveFeederInventory(inventory); }
                else data.set(key + ".refilling", previous);
            }
            }
        }
    }

    private final Map<String, Long> hungerDeadlines = new HashMap<>();

    private void checkHungryWorkers() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (var block : facilityBlocks(ownedPlot(player), Facility.CRAFT)) {
                withCraftStation(block, () -> town.checkHungryCraft(player, now));
            }
            for (Facility facility : Facility.values()) {
                if (!facility.isProducer()) continue;
                for (var station : facilityBlocks(ownedPlot(player), facility)) withCraftStation(station, () -> {
                String key = lumberKey(player, facility) + ".paused";
                boolean hungry = lumberPause(player, facility) == 0 && craftEntries(player).stream()
                    .anyMatch(entry -> assignedHere(player, entry, facility) && productionSlot(player, entry) > 0
                        && productionState(player, entry, now).food() == 0);
                if (!hungry) { hungerDeadlines.remove(key); return; }
                long deadline = hungerDeadlines.computeIfAbsent(key, ignored -> now + 5000);
                if (now >= deadline && store(key, now)) {
                    hungerDeadlines.remove(key);
                    player.sendMessage(facility.label + " 작업 중단 · 펫 먹이가 필요합니다.");
                    if (player.getOpenInventory().getTopInventory().getHolder() instanceof StationMenu menu
                        && station.equals(menu.station))
                        openMenu(player, menu.selected, menu.station, menu.craftPage, menu.craftSelection);
                }
                });
            }
        }
    }

    /** Food cost per produced item by aptitude level: Lv.1 = 1, Lv.2 = 3, Lv.3 = 5. */
    static int foodCost(int level) { return level >= 3 ? 5 : level == 2 ? 3 : 1; }

    private int craftUnitCost(Player player) { return foodCost(Math.max(1, craftLevel(player))); }

    /** Food needed for a craft of {@code count} items; the last item may be paid only partly (down to 0). */
    boolean craftCanAfford(Player player, int count) {
        int cost = craftUnitCost(player);
        return craftFood(player) >= Math.max(1, count * cost - cost + 1);
    }

    boolean reserveCraftFood(Player player, int count, List<String> updated) {
        String worker = data.getString(craftWorkerKey(player));
        if (worker == null || !craftCanAfford(player, count)) return false;
        String foodKey = craftEntryPath(player, worker) + ".food";
        String waitingKey = processKey(player) + "-hungry";
        Object originalFood = data.get(foodKey), originalWaiting = data.get(waitingKey);
        data.set(foodKey, Math.max(0, craftFood(player) - count * craftUnitCost(player)));
        data.set(waitingKey, null);
        if (store(processKey(player), updated)) return true;
        data.set(foodKey, originalFood);
        data.set(waitingKey, originalWaiting);
        return false;
    }

    private final Map<String, Inventory> foodInventories = new HashMap<>();

    private static final class FoodHolder implements InventoryHolder {
        final String key;
        Inventory inventory;
        FoodHolder(String key) { this.key = key; }
        @Override public Inventory getInventory() { return inventory; }
    }

    private Inventory feederInventory(org.bukkit.block.Block block) {
        if (block.getState() instanceof org.bukkit.block.Barrel barrel) return barrel.getInventory();
        String key = facilityViewKey(block) + ".food";
        return foodInventories.computeIfAbsent(key, ignored -> {
            FoodHolder holder = new FoodHolder(key);
            holder.inventory = Bukkit.createInventory(holder, 27, "식량 저장소");
            List<?> items = data.getList(key, List.of());
            for (int i = 0; i < Math.min(items.size(), 27); i++)
                if (items.get(i) instanceof ItemStack item) holder.inventory.setItem(i, item);
            return holder.inventory;
        });
    }

    private void saveFeederInventory(Inventory inventory) {
        if (inventory.getHolder() instanceof FoodHolder holder)
            store(holder.key, Arrays.asList(inventory.getContents()));
    }

    @EventHandler
    public void closeFoodInventory(InventoryCloseEvent event) {
        saveFeederInventory(event.getInventory());
    }

    @Override public void onDisable() {
        if(customPets!=null)customPets.close();
        if(damageNumbers!=null)damageNumbers.shutdown();
        if(hitEffects!=null)hitEffects.shutdown();
        if(dummies!=null)dummies.shutdown();
        if(captures!=null)captures.close();
        if(healthBars!=null)healthBars.close();
        if(resourceNodes!=null)resourceNodes.close();
        if (armory != null) armory.close();
        if (skills != null) skills.shutdown();
        if (combat != null) combat.removeModifiers();
        if (town != null) town.close();
        for (Inventory inventory : foodInventories.values()) saveFeederInventory(inventory);
    }

    private void migrateFacilityBlocks(World world) {
        var plots = data.getConfigurationSection("plots");
        if (plots == null) return;
        for (String plot : plots.getKeys(false)) for (Facility facility : Facility.values())
            for (List<Integer> point : facilityPositions(plot, facility)) {
                var anchor = world.getBlockAt(point.get(0), point.get(1), point.get(2));
                if (anchor.getType() != facility.block) continue;
                if (anchor.getState() instanceof org.bukkit.block.Barrel barrel) {
                    data.set(facilityViewKey(anchor) + ".food", Arrays.asList(barrel.getInventory().getContents()));
                    if (!persist()) throw new IllegalStateException("시설 식량 이전 저장에 실패했습니다.");
                    barrel.getInventory().clear();
                }
                anchor.setType(facility.installedBlock(), false);
                data.set(facilityViewKey(anchor) + ".wide", true);
                for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++)
                    if ((x != 0 || z != 0) && (anchor.getRelative(x,0,z).getType() == Material.BARRIER
                        || facility == Facility.FEEDER && anchor.getRelative(x,0,z).getType() == FEEDER_WALL))
                        anchor.getRelative(x,0,z).setType(Material.BARRIER, false);
            }
        persist();
    }

    private boolean foodStorage(Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof FoodHolder || inventory != null && inventory.getHolder() instanceof org.bukkit.block.Barrel barrel
            && facilityAt(barrel.getBlock()) == Facility.FEEDER;
    }

    @EventHandler
    public void foodStorageClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!foodStorage(top)) return;
        int slot = event.getRawSlot();
        if (event.isShiftClick()) {
            if (slot < top.getSize()) return;
            event.setCancelled(true);
            ItemStack input = event.getCurrentItem();
            if (town.storageNutrition(input) == 0) return;
            int remaining = input.getAmount();
            for (int i = 0; i < 6 && remaining > 0; i++) {
                ItemStack existing = top.getItem(i);
                if (existing != null && !existing.getType().isAir() && !existing.isSimilar(input)) continue;
                int previous = existing == null || existing.getType().isAir() ? 0 : existing.getAmount();
                int transfer = Math.min(remaining, input.getMaxStackSize() - previous);
                if (transfer <= 0) continue;
                ItemStack stack = input.clone();
                stack.setAmount(previous + transfer);
                top.setItem(i, stack);
                remaining -= transfer;
            }
            input.setAmount(remaining);
            event.setCurrentItem(remaining == 0 ? null : input);
        } else if (event.getClick() == ClickType.DOUBLE_CLICK
            || slot >= 0 && slot < top.getSize() && (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT
                || !event.getCursor().getType().isAir() && (slot >= 6 || town.storageNutrition(event.getCursor()) == 0))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void foodStorageDrag(InventoryDragEvent event) {
        if (foodStorage(event.getView().getTopInventory())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void foodStorageHopper(InventoryMoveItemEvent event) {
        if (foodStorage(event.getDestination())) event.setCancelled(true);
    }

    private org.bukkit.block.Block craftContext;

    private void withCraftStation(org.bukkit.block.Block station, Runnable action) {
        var previous = craftContext;
        craftContext = station;
        try { action.run(); }
        finally { craftContext = previous; }
    }

    private org.bukkit.block.Block craftStation(Player player) {
        if (craftContext != null) return craftContext;
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof StationMenu menu
            && facilityAt(menu.station) == Facility.CRAFT) return menu.station;
        return facilityBlocks(ownedPlot(player), Facility.CRAFT).stream()
            .filter(block -> block.getWorld().equals(player.getWorld()))
            .min(Comparator.comparingDouble(block -> block.getLocation().distanceSquared(player.getLocation())))
            .orElse(null);
    }

    private String craftBase(Player player) { return "town." + player.getUniqueId(); }

    private boolean pinCraftAnchor(Player player, String plot) {
        String key = craftBase(player) + ".craft-anchor";
        if (data.contains(key)) return true;
        var positions = facilityPositions(plot, Facility.CRAFT);
        return positions.isEmpty() || store(key, positions.getFirst());
    }

    private String craftKey(Player player, org.bukkit.block.Block station, String suffix) {
        String base = craftBase(player);
        if (station == null) return base + "." + suffix;
        var position = List.of(station.getX(), station.getY(), station.getZ());
        var anchor = data.getIntegerList(base + ".craft-anchor");
        if (anchor.isEmpty()) {
            var positions = facilityPositions(ownedPlot(player), Facility.CRAFT);
            if (!positions.isEmpty()) anchor = positions.getFirst();
        }
        if (position.equals(anchor)) return base + "." + suffix;
        return base + ".craft-stations." + station.getX() + "_" + station.getY() + "_" + station.getZ() + "." + suffix;
    }

    private Set<String> craftWorkers(Player player) {
        Set<String> workers = new HashSet<>();
        for (var block : facilityBlocks(ownedPlot(player), Facility.CRAFT)) {
            String entry = craftWorkerEntry(player, block);
            if (entry != null) workers.add(entry);
        }
        return workers;
    }

    /** Saved craft-worker entry for a station, or null when absent/invalid (removed species, wrong aptitude). */
    private String craftWorkerEntry(Player player, org.bukkit.block.Block station) {
        String entry = data.getString(craftKey(player, station, "craft-worker"));
        if (entry == null || entry.isEmpty()) return null;
        Pet pet = entryPet(entry);
        return pet == null || !sameAptitudeKind(Facility.CRAFT, pet.aptitude) ? null : entry;
    }

    private String craftWorkerKey(Player player) { return craftKey(player, craftStation(player), "craft-worker"); }
    String processKey(Player player) { return craftKey(player, craftStation(player), "process"); }

    private Pet craftWorker(Player player) {
        String value = data.getString(craftWorkerKey(player));
        return Arrays.stream(Pet.values()).filter(pet -> pet.name().equals(value)).findFirst().orElse(null);
    }

    boolean atCraftStation(Player player) {
        var station = craftStation(player);
        return station != null && facilityAt(station) == Facility.CRAFT
            && owns(player, plotAt(station.getLocation())) && station.getWorld().equals(player.getWorld())
            && station.getLocation().add(.5,.5,.5).distanceSquared(player.getLocation()) <= 36;
    }

    boolean nearFacility(Player player, Facility facility) {
        return owns(player, plotAt(player.getLocation())) && facilityBlocks(ownedPlot(player), facility).stream()
            .anyMatch(block -> block.getWorld().equals(player.getWorld())
                && block.getLocation().add(.5,.5,.5).distanceSquared(player.getLocation()) <= 36);
    }

    int craftLevel(Player player) {
        String worker = data.getString(craftWorkerKey(player));
        return worker == null ? 0 : craftAptitude(player, worker);
    }

    int craftFood(Player player) {
        String worker = data.getString(craftWorkerKey(player));
        return worker == null ? 0 : data.getInt(craftEntryPath(player, worker) + ".food");
    }

    void refreshCraft(Player player) {
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof StationMenu menu
            && facilityAt(menu.station) == Facility.CRAFT && menu.station.equals(craftStation(player)) && atCraftStation(player))
            openMenu(player, menu.selected, menu.station, menu.craftPage, menu.craftSelection);
    }

    boolean startCraft(Player player, String recipe, long ready, int count, long interval) {
        String worker = data.getString(craftWorkerKey(player));
        if (worker == null || !atCraftStation(player) || data.contains(processKey(player))) return false;
        long now = System.currentTimeMillis();
        Workshop state = productionState(player, worker, now);
        if (state.active()) return false;
        data.set(processKey(player), List.of(recipe, Long.toString(ready), worker, Integer.toString(count), Long.toString(interval), "0"));
        String waiting = processKey(player) + "-hungry";
        boolean hungry = !craftCanAfford(player, count);
        if (hungry) data.set(waiting, now);
        if (save(craftEntryPath(player, worker), new Workshop(hungry ? state.food() : Math.max(0, state.food() - count * craftUnitCost(player)), state.stock(), now, false))) return true;
        data.set(waiting, null);
        data.set(processKey(player), null);
        return false;
    }
    private void changeCraftWorker(Player player, Pet pet, boolean assign) {
        if (!atCraftStation(player)) return;
        if (data.contains(processKey(player))) {
            player.sendMessage("기존 주문을 수령한 뒤 배치를 변경하세요.");
            return;
        }
        if (assign && (aptitude(player, pet, Facility.CRAFT) == 0 || craftWorkers(player).contains(pet.name()) || workshop(player, pet, System.currentTimeMillis()).active())) {
            player.sendMessage("가공 적성이 있고 다른 작업에 배치되지 않은 펫을 선택하세요.");
            return;
        }
        if (!assign && craftWorker(player) != pet) {
            player.sendMessage("기본 작업대에 배치된 펫을 선택하세요.");
            return;
        }
        if (store(craftWorkerKey(player), assign ? pet.name() : null))
            player.sendMessage(assign ? pet.label + "를 기본 작업대에 배치했습니다." : "가공 배치를 해제했습니다.");
        else player.sendMessage("가공 배치를 저장하지 못했습니다.");
    }

    /** Unassigns the pet placed at a craft station. Returns false when nothing is placed there. */
    private boolean releaseCraftWorkerAt(Player player, org.bukkit.block.Block station) {
        String entry = craftWorkerEntry(player, station);
        if (entry == null) return false;
        if (data.contains(craftKey(player, station, "process"))) {
            player.sendMessage("기존 주문을 수령한 뒤 배치를 변경하세요.");
            return true;
        }
        if (store(craftKey(player, station, "craft-worker"), null))
            player.sendMessage("§a" + entryPet(entry).label + "의 제작대 배치를 해제했습니다.");
        else player.sendMessage("가공 배치를 저장하지 못했습니다.");
        return true;
    }

    /** Unassigns the pet entry from a producer facility. */
    private boolean releaseProducerEntry(Player player, String entry) {
        Pet pet = entryPet(entry);
        if (pet == null) return false;
        Workshop state = productionState(player, entry, System.currentTimeMillis());
        if (save(craftEntryPath(player, entry), new Workshop(state.food(), state.stock(), System.currentTimeMillis(), false)))
            player.sendMessage("§a" + pet.label + "의 배치를 해제했습니다.");
        return true;
    }

    /** Right-click on a worker pet entity (scene key "ownerUuid:entry"); only the owner may release it. */
    boolean releaseWorkerByClick(Player player, String sceneKey) {
        int cut = sceneKey.indexOf(':');
        if (cut < 0 || !sceneKey.substring(0, cut).equals(player.getUniqueId().toString())) return false;
        String entry = sceneKey.substring(cut + 1);
        String plot = ownedPlot(player);
        if (plot == null) return false;
        for (var block : facilityBlocks(plot, Facility.CRAFT))
            if (entry.equals(craftWorkerEntry(player, block))) return releaseCraftWorkerAt(player, block);
        Facility facility = productionFacility(player, entry);
        if (facility != null && productionSlot(player, entry) > 0) return releaseProducerEntry(player, entry);
        return false;
    }

    private static final class StationMenu implements InventoryHolder {
        private final UUID owner;
        private final Pet selected;
        private final org.bukkit.block.Block station;
        private Inventory inventory;
        private int craftPage;
        private String craftSelection;
        private StationMenu(UUID owner, Pet selected, org.bukkit.block.Block station) {
            this.owner = owner;
            this.selected = selected;
            this.station = station;
        }
        @Override public Inventory getInventory() { return inventory; }
    }

    @EventHandler(ignoreCancelled = true)
    public void openStation(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        var block = event.getClickedBlock();
        Facility held = blueprint(event.getItem());
        if (held != null) return;
        var feederBase = feederAnchor(block);
        if (feederBase != null) block = feederBase;
        String plot = block == null ? null : plotAt(block.getLocation());
        if (block == null || facilityAt(block) == null && !legacyStation(block, plot)) return;
        if (plot == null) return;
        event.setCancelled(true);
        if (owns(event.getPlayer(), plot)) {
            if(isConstructionHammer(event.getItem())) {
                var dismantle=new BlockBreakEvent(block,event.getPlayer());removeStation(dismantle);if(!dismantle.isCancelled())block.setType(Material.AIR,false);return;
            }
            Facility installed = facilityAt(block);
            if (installed != null && (installed != Facility.FEEDER || wideFeeder(block))) showStationModel(block, installed);
            if (facilityAt(block) == Facility.FEEDER) event.getPlayer().openInventory(feederInventory(block));
            else if (facilityAt(block) == Facility.PET_MARKET) openPetMarket(event.getPlayer(),0);
            else if (facilityAt(block) == Facility.PET_STORAGE) openPetStorage(event.getPlayer());
            else if (facilityAt(block) == Facility.RESEARCH) town.openSmith(event.getPlayer(), true);
            else if (facilityAt(block) == Facility.COOKING_TABLE) cooking.open(event.getPlayer());
            else if (installed != null && installed.comingSoon()) event.getPlayer().sendMessage(installed.label + " 기능은 준비 중입니다.");
            else openMenu(event.getPlayer(), null, block);
        }
        else if (facilityAt(block) == Facility.PET_MARKET) openPetMarketBuyer(event.getPlayer(), plot, 0);
        else event.getPlayer().sendMessage("이 작업장은 거주지 주인만 이용할 수 있습니다.");
    }

    ItemStack warpBlueprint() { return warps == null ? null : warps.blueprintItem(); }

    /** One residence warp stone blueprint per player (data flag); backfill requires an owned plot. */
    private void giveWarpBlueprint(Player player, boolean requirePlot) {
        String key = tutorialPath(player) + ".warp-blueprint";
        if (data.getBoolean(key) || requirePlot && ownedPlot(player) == null) return;
        ItemStack item = warpBlueprint();
        if (item == null) return;
        if (!store(key, true)) return;
        for (ItemStack remaining : player.getInventory().addItem(item).values())
            player.getWorld().dropItemNaturally(player.getLocation(), remaining).setOwner(player.getUniqueId());
        player.sendMessage("거주지 워프석 청사진을 받았습니다. 내 거주지에서 우클릭하여 설치하세요.");
    }

    boolean isConstructionHammer(ItemStack item) { return item!=null&&item.hasItemMeta()&&"hammer".equals(item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(this,"processed_product"),PersistentDataType.STRING)); }
    // 가판대: 슬롯 0-4 = 판매 목록(lore 첫 줄 = 가격 숫자), 5 = 대금(lore 첫 줄 = 금액, 주인 화면만), 53 = 모드 표식(이름 "주인"/"구매자", lore 첫 줄 = 가판대 주인 이름),
    // 9-38 = 내 펫 창고 목록(주인 화면, 창고 인덱스 = page*30+slot-9), 48/50 = 이전/다음, 49 = 닫기.
    // 명령: market page <n> | list <row0-4> <slot9-38> <price> | stop <row> | collect | sell <slot9-38> | buy <row> <price>
    private static final class PetMarket implements InventoryHolder {
        final int page; final UUID owner; final String plot; final boolean mine; Inventory inventory;
        PetMarket(int page,UUID owner,String plot,boolean mine){this.page=page;this.owner=owner;this.plot=plot;this.mine=mine;}
        public Inventory getInventory(){return inventory;}
    }
    private static final int MARKET_ROWS=5,MARKET_MAX_PRICE=100_000_000;
    private String marketKey(UUID o){return "pet-market."+o;}
    private String marketRow(UUID o,int row){return marketKey(o)+".rows."+row;}
    private Map<String,Object> plainMap(org.bukkit.configuration.ConfigurationSection s){Map<String,Object> m=new LinkedHashMap<>();for(String k:s.getKeys(false)){Object v=s.get(k);m.put(k,v instanceof org.bukkit.configuration.ConfigurationSection c?plainMap(c):v);}return m;}
    private Object snap(String path){Object v=data.get(path);return v instanceof org.bukkit.configuration.ConfigurationSection s?plainMap(s):v;}
    private void restore(String path,Object v){if(v instanceof Map<?,?> m)data.createSection(path,m);else data.set(path,v);}
    private boolean marketNear(Player p,PetMarket h){
        if(h.mine&&!owns(p,h.plot))return false;
        return facilityBlocks(h.plot,Facility.PET_MARKET).stream().anyMatch(b->b.getWorld().equals(p.getWorld())&&b.getLocation().add(.5,.5,.5).distanceSquared(p.getLocation())<=36);
    }
    private void fillMarketRows(Inventory inv,UUID owner,boolean mine){
        for(int i=0;i<MARKET_ROWS;i++){
            String rp=marketRow(owner,i),entry=data.getString(rp+".entry","");Pet pet=entryPet(entry);if(entry.isEmpty()||pet==null)continue;
            int food=data.getInt(rp+".data.food");
            inv.setItem(i,statusIcon(petIcon(pet),pet.label,String.valueOf(data.getInt(rp+".price")),aptitudeLabel(pet.aptitude)+" 적성 Lv."+pet.level,"포만도 "+food+"/100",mine?"판매 중 · 판매 중지로 회수":"클릭하여 구매"));
        }
    }
    private void openPetMarket(Player p,int page){
        String plot=ownedPlot(p);if(plot==null)return;
        List<String> entries=storageEntries(p);int pages=storagePages(p);page=Math.clamp(page,0,pages-1);
        PetMarket h=new PetMarket(page,p.getUniqueId(),plot,true);h.inventory=Bukkit.createInventory(h,54,"삐삐서버 · 펫 가판대");
        fillMarketRows(h.inventory,h.owner,true);
        for(int i=0;i<STORAGE_PAGE_SIZE;i++){
            String e=entries.get(page*STORAGE_PAGE_SIZE+i);Pet pet=entryPet(e);if(e.isEmpty()||pet==null)continue;
            h.inventory.setItem(9+i,statusIcon(petIcon(pet),pet.label,petAssignment(p,e),petSalePrice(p,e)+" 골드",aptitudeLabel(pet.aptitude)+" 적성 Lv."+pet.level));
        }
        h.inventory.setItem(5,statusIcon(Material.GOLD_INGOT,"판매 대금",String.valueOf(data.getLong(marketKey(h.owner)+".proceeds"))));
        h.inventory.setItem(53,statusIcon(Material.NAME_TAG,"주인",p.getName()));h.inventory.setItem(52,statusIcon(Material.BOOK,"페이지",String.valueOf(page+1)));
        if(page>0)h.inventory.setItem(48,statusIcon(Material.ARROW,"이전"));if(page+1<pages)h.inventory.setItem(50,statusIcon(Material.ARROW,"다음"));h.inventory.setItem(49,statusIcon(Material.BARRIER,"닫기"));
        p.openInventory(h.inventory);
    }
    private void openPetMarketBuyer(Player p,String plot,int page){
        String ownerId=data.getString("plots."+plot+".owner");UUID owner;try{owner=UUID.fromString(ownerId);}catch(RuntimeException ex){p.sendMessage("가판대 주인을 확인할 수 없습니다.");return;}
        String name=Bukkit.getOfflinePlayer(owner).getName();
        PetMarket h=new PetMarket(0,owner,plot,false);h.inventory=Bukkit.createInventory(h,54,"삐삐서버 · 펫 가판대");
        fillMarketRows(h.inventory,owner,false);
        h.inventory.setItem(53,statusIcon(Material.NAME_TAG,"구매자",name==null?"알 수 없음":name));h.inventory.setItem(49,statusIcon(Material.BARRIER,"닫기"));
        p.openInventory(h.inventory);
    }
    /** 목록의 펫 하나를 to 의 창고로 옮긴다(판매 중지/구매 공용). 0=성공 1=이미 없음 2=창고 가득 3=동종 중복 4=저장 실패 */
    private int marketTransfer(Player to,UUID owner,int row,long proceedsAdd){
        String rp=marketRow(owner,row),entry=data.getString(rp+".entry","");if(entry.isEmpty()||entryPet(entry)==null)return 1;
        List<String> entries=storageEntries(to);int free=entries.indexOf("");if(free<0)return 2;
        String dp=craftEntryPath(to,entry);
        if(!entry.contains(":")&&(entries.contains(entry)||data.isConfigurationSection(dp)))return 3;
        String sk=storageKey(to),mk=marketKey(owner);Object s1=snap(sk),s2=snap(dp),s3=snap(mk);Object rowData=snap(rp+".data");
        entries.set(free,entry);data.set(sk,entries);
        if(rowData instanceof Map<?,?> m)data.createSection(dp,m);
        data.set(rp,null);if(proceedsAdd>0)data.set(mk+".proceeds",data.getLong(mk+".proceeds")+proceedsAdd);
        if(persist())return 0;
        restore(sk,s1);restore(dp,s2);restore(mk,s3);return 4;
    }
    private boolean marketCommand(Player p,String[] a){
        if(!(p.getOpenInventory().getTopInventory().getHolder() instanceof PetMarket h))return true;
        if(!marketNear(p,h)){p.closeInventory();return true;}
        try{
            String sub=a.length>1?a[1]:"";
            if(h.mine&&sub.equals("page")&&a.length==3)openPetMarket(p,Integer.parseInt(a[2])-1);
            else if(h.mine&&sub.equals("collect")){
                String key=marketKey(h.owner)+".proceeds";long sum=data.getLong(key);var balance=town.gold(p);
                if(sum<=0){p.sendMessage("수령할 대금이 없습니다.");return true;}
                long pay=Math.min(sum,(long)Integer.MAX_VALUE-balance.getScore());
                if(pay<=0){p.sendMessage("골드 보유 한도를 초과합니다.");return true;}
                if(!store(key,sum-pay)){p.sendMessage("저장 실패 · 다시 시도하세요.");return true;}
                balance.setScore(balance.getScore()+(int)pay);p.sendMessage("판매 대금 "+pay+"골드를 받았습니다.");openPetMarket(p,h.page);
            }else if(h.mine&&sub.equals("stop")&&a.length==3){
                int row=Integer.parseInt(a[2]);if(row<0||row>=MARKET_ROWS)return true;
                String name=data.getString(marketRow(h.owner,row)+".entry","");
                switch(marketTransfer(p,h.owner,row,0)){
                    case 0->p.sendMessage((entryPet(name)==null?"펫":entryPet(name).label)+" 판매를 중지하고 펫 창고로 돌려받았습니다.");
                    case 2->p.sendMessage("펫 창고에 빈 칸이 없어 판매를 중지할 수 없습니다.");
                    case 3->p.sendMessage("같은 종의 펫이 이미 창고에 있어 회수할 수 없습니다.");
                    case 4->p.sendMessage("저장 실패 · 다시 시도하세요.");
                    default->{}
                }
                openPetMarket(p,h.page);
            }else if(h.mine&&sub.equals("list")&&a.length==5){
                int row=Integer.parseInt(a[2]),slot=Integer.parseInt(a[3]),price=Integer.parseInt(a[4]);
                if(row<0||row>=MARKET_ROWS||slot<9||slot>=9+STORAGE_PAGE_SIZE)return true;
                if(price<1||price>MARKET_MAX_PRICE){p.sendMessage("가격은 1~"+MARKET_MAX_PRICE+" 골드여야 합니다.");return true;}
                String rp=marketRow(h.owner,row);if(!data.getString(rp+".entry","").isEmpty()){p.sendMessage("이미 판매 중인 칸입니다.");return true;}
                List<String> entries=storageEntries(p);int idx=h.page*STORAGE_PAGE_SIZE+slot-9;
                if(idx<0||idx>=entries.size()||entries.get(idx).isEmpty())return true;
                String entry=entries.get(idx);Pet pet=entryPet(entry);if(pet==null)return true;
                if(!petAssignment(p,entry).equals("창고 대기")){p.sendMessage("배치된 펫은 해제 후 판매하세요.");return true;}
                String sk=storageKey(p),dp=craftEntryPath(p,entry),mk=marketKey(h.owner);Object s1=snap(sk),s2=snap(dp),s3=snap(mk);Object petData=snap(dp);
                data.set(rp+".entry",entry);data.set(rp+".price",price);if(petData instanceof Map<?,?> m)data.createSection(rp+".data",m);
                entries.set(idx,"");data.set(sk,entries);data.set(dp,null);
                if(persist())p.sendMessage(pet.label+" 판매 시작 · "+price+"골드");
                else{restore(sk,s1);restore(dp,s2);restore(mk,s3);p.sendMessage("저장 실패 · 다시 시도하세요.");}
                openPetMarket(p,h.page);
            }else if(h.mine&&sub.equals("sell")&&a.length==3){
                int slot=Integer.parseInt(a[2]);if(slot<9||slot>=9+STORAGE_PAGE_SIZE)return true;
                List<String> entries=storageEntries(p);int index=h.page*STORAGE_PAGE_SIZE+slot-9;
                if(index<0||index>=entries.size()||entries.get(index).isEmpty())return true;
                String entry=entries.get(index);
                if(!petAssignment(p,entry).equals("창고 대기")){p.sendMessage("배치된 펫은 해제 후 판매하세요.");return true;}
                int price=petSalePrice(p,entry);var balance=town.gold(p);if(balance.getScore()>Integer.MAX_VALUE-price){p.sendMessage("골드 보유 한도를 초과합니다.");return true;}
                String path=craftEntryPath(p,entry);var section=data.getConfigurationSection(path);Map<String,Object> old=section==null?null:section.getValues(true);var previous=new ArrayList<>(entries);
                entries.set(index,"");data.set(storageKey(p),entries);data.set(path,null);
                if(!persist()){data.set(storageKey(p),previous);if(old!=null)data.createSection(path,old);p.sendMessage("판매 저장 실패 · 다시 시도하세요.");return true;}
                balance.setScore(balance.getScore()+price);p.sendMessage("펫 판매 완료 · "+price+"골드");openPetMarket(p,h.page);
            }else if(!h.mine&&sub.equals("buy")&&a.length==4){
                int row=Integer.parseInt(a[2]),expect=Integer.parseInt(a[3]);if(row<0||row>=MARKET_ROWS)return true;
                String rp=marketRow(h.owner,row),entry=data.getString(rp+".entry","");int price=data.getInt(rp+".price");
                if(entry.isEmpty()||entryPet(entry)==null){p.sendMessage("이미 판매되었거나 중지된 펫입니다.");openPetMarketBuyer(p,h.plot,0);return true;}
                if(price!=expect){p.sendMessage("가격이 변경되었습니다. 다시 확인하세요.");openPetMarketBuyer(p,h.plot,0);return true;}
                var balance=town.gold(p);if(balance.getScore()<price){p.sendMessage("골드가 부족합니다. ("+price+"골드 필요)");return true;}
                Pet pet=entryPet(entry);
                switch(marketTransfer(p,h.owner,row,price)){
                    case 0->{balance.setScore(balance.getScore()-price);p.sendMessage(pet.label+"을(를) "+price+"골드에 구매했습니다. 펫 창고를 확인하세요.");
                        Player owner=Bukkit.getPlayer(h.owner);if(owner!=null)owner.sendMessage("가판대에서 "+pet.label+"이(가) "+p.getName()+"님에게 "+price+"골드에 판매되었습니다. 가판대에서 대금을 수령하세요.");}
                    case 1->p.sendMessage("이미 판매되었거나 중지된 펫입니다.");
                    case 2->p.sendMessage("펫 창고에 빈 칸이 없어 구매할 수 없습니다.");
                    case 3->p.sendMessage("같은 종의 기본 펫이 이미 창고에 있어 구매할 수 없습니다.");
                    default->p.sendMessage("저장 실패 · 다시 시도하세요.");
                }
                openPetMarketBuyer(p,h.plot,0);
            }
        }catch(NumberFormatException ignored){}
        return true;
    }
    private int petSalePrice(Player p,String entry) {
        int total=0;
        if(entry.contains(":")){for(String field:entry.split(":"))if(field.contains("="))try{total+=Integer.parseInt(field.split("=")[1]);}catch(NumberFormatException ignored){}}
        else{Pet pet=entryPet(entry);return pet==null?0:pet.salePrice();}
        return 100+Math.max(0,total)*50;
    }
    @EventHandler public void petMarketDrag(org.bukkit.event.inventory.InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof PetMarket)e.setCancelled(true);}
    @EventHandler public void petMarketClick(InventoryClickEvent event) {
        if(!(event.getView().getTopInventory().getHolder() instanceof PetMarket h))return;event.setCancelled(true);
        if(!(event.getWhoClicked() instanceof Player p)||!event.isLeftClick())return;int slot=event.getRawSlot();
        Bukkit.getScheduler().runTask(this,()->{
            if(p.getOpenInventory().getTopInventory().getHolder()!=h)return;
            if(slot==49){p.closeInventory();return;}
            // 기본 상자 화면 대체 조작: 판매 중인 칸 클릭 = 구매(구매자)/판매 중지(주인), 5 = 대금 수령, 48/50 = 페이지. 판매 시작(가격 입력)은 클라이언트 화면 필요.
            if(slot>=0&&slot<MARKET_ROWS&&h.inventory.getItem(slot)!=null){
                if(h.mine)marketCommand(p,new String[]{"market","stop",String.valueOf(slot)});
                else marketCommand(p,new String[]{"market","buy",String.valueOf(slot),String.valueOf(data.getInt(marketRow(h.owner,slot)+".price"))});
            }else if(h.mine&&slot==5)marketCommand(p,new String[]{"market","collect"});
            else if(h.mine&&(slot==48||slot==50)&&h.inventory.getItem(slot)!=null)marketCommand(p,new String[]{"market","page",String.valueOf(h.page+(slot==48?0:2))});
        });
    }

    private boolean openPetStorage(Player player) {
        if (!nearFacility(player, Facility.PET_STORAGE)) {
            player.sendMessage("내 거주지에 설치한 펫 창고를 우클릭하세요.");
            return true;
        }
        openStoragePage(player, 0);
        return true;
    }

    private static final int STORAGE_PAGE_SIZE = 30, MAX_STORAGE_LEVEL = 3, LEGACY_STORAGE_LEVEL = 5;
    private static final class StorageHolder implements InventoryHolder {
        final int page;
        Inventory inventory;
        StorageHolder(int page) { this.page = page; }
        @Override public Inventory getInventory() { return inventory; }
    }

    private String storageKey(Player player) { return "pet-storage." + player.getUniqueId() + ".slots"; }
    private String storageLevelKey(Player player) { return "pet-storage." + player.getUniqueId() + ".level"; }
    private int storageLevel(Player player) { return Math.clamp(data.getInt(storageLevelKey(player), 1), 1, LEGACY_STORAGE_LEVEL); }
    private int storagePages(Player player) { return 1 + 2 * (storageLevel(player) - 1); }

    private List<String> storageEntries(Player player) {
        int capacity = storagePages(player) * STORAGE_PAGE_SIZE;
        List<String> entries = new ArrayList<>();
        for (String saved : data.getStringList(storageKey(player))) {
            if (saved.isEmpty()) { entries.add(""); continue; }
            if (entryPet(saved) == null) {
                if (!warnedRemovedSpecies) {
                    warnedRemovedSpecies = true;
                    getLogger().warning("제거된 펫 종의 저장 데이터를 건너뜁니다: " + saved);
                }
                entries.add("");
            } else entries.add(saved);
        }
        while (entries.size() < capacity) entries.add("");
        for (Pet pet : Pet.values()) {
            if (!data.isConfigurationSection(path(player, pet)) || entries.contains(pet.name())) continue;
            int free = entries.indexOf("");
            if (free >= 0) entries.set(free, pet.name());
        }
        return entries;
    }

    private void openStoragePage(Player player, int page) {
        int pages = storagePages(player);
        page = Math.clamp(page, 0, pages - 1);
        StorageHolder holder = new StorageHolder(page);
        Inventory inventory = Bukkit.createInventory(holder, 54, "삐삐서버 · 펫 창고");
        holder.inventory = inventory;
        List<String> entries = storageEntries(player);
        for (int i = 0; i < STORAGE_PAGE_SIZE; i++) {
            String saved = entries.get(page * STORAGE_PAGE_SIZE + i);
            if (saved.isEmpty()) continue;
            Pet pet = entryPet(saved);
            if (pet == null) continue;
            List<String> details = new ArrayList<>();
            details.add(aptitudeLabel(pet.aptitude) + " 적성 Lv." + pet.level);
            if (levelBonusText(pet.level, pet.aptitude) != null) details.add(levelBonusText(pet.level, pet.aptitude));
            if (saved.indexOf(':') < 0) {
                Workshop state = workshop(player, pet, System.currentTimeMillis());
                details.add("포만도 " + state.food() + "/100");
            } else {
                details.add("포만도 " + data.getInt(craftEntryPath(player, saved) + ".food") + "/100");
            }
            details.add(petAssignment(player, saved));
            inventory.setItem(i, statusIcon(petIcon(pet), pet.label, details.toArray(String[]::new)));
        }
        int level = storageLevel(player);
        long owned = entries.stream().filter(entry -> !entry.isEmpty()).count();
        inventory.setItem(49, statusIcon(Material.BOOK, "펫 창고",
            level + "," + (page + 1) + "," + pages + "," + owned + "," + (level * 100)));
        inventory.setItem(45, statusIcon(Material.ARROW, "이전 페이지"));
        inventory.setItem(53, statusIcon(Material.ARROW, "다음 페이지"));
        inventory.setItem(50, statusIcon(Material.GOLD_INGOT, "레벨업", TownSystems.upgradeLines(Facility.PET_STORAGE, level).toArray(String[]::new)));
        player.openInventory(inventory);
    }

    private boolean storageCommand(Player player, String[] args) {
        if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof StorageHolder menu)
            || !nearFacility(player, Facility.PET_STORAGE)) return true;
        try {
            if (args.length == 2 && args[1].equals("upgrade")) {
                int level = storageLevel(player);
                if (level < MAX_STORAGE_LEVEL)
                    town.openFacilityUpgrade(player, Facility.PET_STORAGE, storageLevelKey(player), level,
                        () -> storageLevel(player), () -> openStoragePage(player, menu.page));
                else openStoragePage(player, menu.page);
            } else if (args.length == 3 && args[1].equals("page")) {
                openStoragePage(player, Integer.parseInt(args[2]) - 1);
            } else if (args.length == 3 && args[1].equals("release")) {
                int index = Integer.parseInt(args[2]);
                List<String> entries = storageEntries(player);
                int capacity = storagePages(player) * STORAGE_PAGE_SIZE;
                if (index < 0 || index >= capacity || entries.get(index).isEmpty()) return true;
                String entry = entries.get(index);
                Pet pet = entryPet(entry);
                if (pet == null) return true;
                if (!petAssignment(player, entry).equals("창고 대기")) {
                    player.sendMessage(pet.label + "은(는) 작업 중이라 방출할 수 없습니다. 먼저 배치를 해제하세요.");
                    return true;
                }
                String dataPath = craftEntryPath(player, entry);
                Object previous = data.get(dataPath);
                data.set(dataPath, null);
                entries.set(index, "");
                if (store(storageKey(player), entries)) {
                    player.sendMessage(pet.label + "을(를) 방출했습니다.");
                    openStoragePage(player, index / STORAGE_PAGE_SIZE);
                } else {
                    data.set(dataPath, previous);
                    player.sendMessage("저장에 실패해 방출하지 못했습니다.");
                }
            } else if (args.length == 4 && args[1].equals("move")) {
                int from = Integer.parseInt(args[2]), to = Integer.parseInt(args[3]);
                List<String> entries = storageEntries(player);
                int capacity = storagePages(player) * STORAGE_PAGE_SIZE;
                if (from < 0 || to < 0 || from >= capacity || to >= capacity || from == to || entries.get(from).isEmpty()) return true;
                Collections.swap(entries, from, to);
                if (store(storageKey(player), entries)) openStoragePage(player, to / STORAGE_PAGE_SIZE);
            }
        } catch (NumberFormatException ignored) {}
        return true;
    }

    @EventHandler
    public void storageClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof StorageHolder menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !event.isLeftClick()) return;
        String action = switch (event.getRawSlot()) {
            case 45 -> "page " + menu.page;
            case 53 -> "page " + (menu.page + 2);
            case 50 -> "upgrade";
            default -> null;
        };
        if (action != null) getServer().getScheduler().runTask(this, () -> {
            if (player.getOpenInventory().getTopInventory().getHolder() == menu) player.performCommand("pipilife petstorage " + action);
        });
    }

    @EventHandler
    public void storageDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof StorageHolder) event.setCancelled(true);
    }

    private static final List<Integer> PET_SLOTS = List.of(9, 10, 18, 19, 27, 28, 36, 37, 45, 46, 47, 38);

    private static final Map<Integer, String> STATION_ACTIONS =
        Map.of(21, "assign", 22, "feed", 23, "collect", 31, "sell", 32, "stop");

    record PetScene(String key,String species,Location station,Location focus,boolean working,int food,Location feeder,int task,String soundGroup){}
    private String petSkin(Pet pet){return pet==null?null:pet.name().toLowerCase(Locale.ROOT);}
    private String petSkin(String entry){return petSkin(entryPet(entry));}
    private Location stationFocus(org.bukkit.block.Block b,Facility facility){boolean wide=facility==Facility.FEEDER?wideFeeder(b):wideStation(b);return b.getLocation().add(wide?facility.width()/2.0:.5,0,wide?facility.depth()/2.0:.5);}
    List<PetScene> petScenes(){
        List<PetScene> scenes=new ArrayList<>();long now=System.currentTimeMillis();
        for(Player p:Bukkit.getOnlinePlayers()){
            String plot=ownedPlot(p);if(plot==null)continue;
            Location feeder=facilityBlocks(plot,Facility.FEEDER).stream().findFirst().map(b->b.getLocation().add(.5,0,-.5)).orElse(null);
            for(var b:facilityBlocks(plot,Facility.CRAFT)){
                String entry=craftWorkerEntry(p,b);if(entry==null)continue;
                int food=data.getInt(craftEntryPath(p,entry)+".food");var process=data.getStringList(craftKey(p,b,"process"));boolean work=false;
                try{work=food>0&&process.size()>1&&Long.parseLong(process.get(1))>now&&!data.contains(craftKey(p,b,"process")+"-paused")&&!data.contains(craftKey(p,b,"process")+"-hungry");}catch(NumberFormatException ignored){}
                scenes.add(new PetScene(p.getUniqueId()+":"+entry,petSkin(entry),b.getLocation().add(1.5,0,-.7),stationFocus(b,Facility.CRAFT),work,food,feeder,4,b.getWorld().getUID()+":"+coordinates(b)));
            }
            for(String entry:craftEntries(p)){
                String skin=petSkin(entry);Facility f=productionFacility(p,entry);if(f==null||productionSlot(p,entry)<=0)continue;
                String anchor=assignedProducer(p,entry,f);var block=facilityBlocks(plot,f).stream().filter(b->coordinates(b).equals(anchor)).findFirst().orElse(null);if(block==null)continue;
                Workshop state=productionState(p,entry,now);boolean work=state.food()>0&&state.stock()<state.cap()&&data.getLong(producerKey(plot,f,anchor)+".paused")==0;
                scenes.add(new PetScene(p.getUniqueId()+":"+entry,skin,block.getLocation().add(.4+productionSlot(p,entry)*.45,0,-.7),stationFocus(block,f),work,state.food(),feeder,f==Facility.LUMBER||f==Facility.REDWOOD_LUMBER?2:aptitudeLabel(f).equals("농사")?0:1,block.getWorld().getUID()+":"+coordinates(block)));
            }
        }return scenes;
    }
    private Material petIcon(Pet pet) {
        return Material.valueOf(pet.type.name() + "_SPAWN_EGG");
    }

    private List<Pet> menuPets(Player player, Facility facility) {
        return Arrays.stream(Pet.values())
            .filter(pet -> data.isConfigurationSection(path(player, pet)))
            .filter(pet -> facility == null || facility == Facility.CRAFT || aptitude(player, pet, facility) > 0).toList();
    }

    private String legacyProducerKey(String plot, Facility facility) {
        return "plots." + plot + (facility == Facility.LUMBER ? ".lumber" : ".production." + facility.name());
    }

    private String coordinates(org.bukkit.block.Block block) {
        return block.getX() + "_" + block.getY() + "_" + block.getZ();
    }

    private String legacyProducerAnchor(String plot, Facility facility) {
        String pinned = data.getString(legacyProducerKey(plot, facility) + ".anchor");
        if (pinned != null) return pinned;
        var positions = facilityPositions(plot, facility);
        if (positions.isEmpty()) return "";
        var first = positions.getFirst();
        return first.get(0) + "_" + first.get(1) + "_" + first.get(2);
    }

    private String producerKey(String plot, Facility facility, String anchor) {
        String base = legacyProducerKey(plot, facility);
        return anchor.equals(legacyProducerAnchor(plot, facility)) ? base : base + ".stations." + anchor;
    }

    private org.bukkit.block.Block currentProducer(Player player, Facility facility) {
        if (craftContext != null && facilityAt(craftContext) == facility) return craftContext;
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof StationMenu menu
            && facilityAt(menu.station) == facility) return menu.station;
        return facilityBlocks(ownedPlot(player), facility).stream().findFirst().orElse(null);
    }

    private String lumberKey(Player player, Facility facility) {
        var station = currentProducer(player, facility);
        String plot = ownedPlot(player);
        return producerKey(plot, facility, station == null ? legacyProducerAnchor(plot, facility) : coordinates(station));
    }

    private String assignedProducer(Player player, String entry, Facility facility) {
        return data.getString(craftEntryPath(player, entry) + ".production-anchor", legacyProducerAnchor(ownedPlot(player), facility));
    }

    private boolean assignedHere(Player player, String entry, Facility facility) {
        var station = currentProducer(player, facility);
        return station != null && productionFacility(player, entry) == facility
            && assignedProducer(player, entry, facility).equals(coordinates(station));
    }

    private long lumberPause(Player player, Facility facility) { return data.getLong(lumberKey(player, facility) + ".paused"); }
    private int lumberLevel(Player player, Facility facility) { return Math.clamp(data.getInt(lumberKey(player, facility) + ".level", 1), 1, MAX_FACILITY_LEVEL); }

    private final Map<UUID, Integer> catalogPages = new java.util.HashMap<>();
    int catalogPage(Player player) {
        return Math.clamp(catalogPages.getOrDefault(player.getUniqueId(), 0), 0, town.catalogPages(player) - 1);
    }

    private boolean workstation(Player player, String[] args) {
        if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof StationMenu menu)) return true;
        Facility facility = facilityAt(menu.station);
        if (facility == null || !facility.isProducer() && facility != Facility.CRAFT || !owns(player, plotAt(menu.station.getLocation()))
            || !player.getWorld().equals(menu.station.getWorld())
            || player.getLocation().distanceSquared(menu.station.getLocation().add(.5, .5, .5)) > 36) return true;
        if (args.length >= 2 && (Set.of("page", "select", "assign").contains(args[1])
            || facility == Facility.CRAFT && Set.of("release", "feed").contains(args[1])))
            return stationStorageAction(player, menu, args);
        if (facility == Facility.CRAFT && args.length == 3 && args[1].equals("catalog")) {
            try { catalogPages.put(player.getUniqueId(), Math.clamp(Integer.parseInt(args[2]), 0, town.catalogPages(player) - 1)); }
            catch (NumberFormatException ex) { return true; }
            openMenu(player, menu.selected, menu.station, menu.craftPage, menu.craftSelection);
            return true;
        }
        if (facility == Facility.CRAFT && args.length == 2 && args[1].equals("claim")) {
            town.collectCraft(player, true, false);
            refreshCraft(player);
            return true;
        }
        Pet selected = menu.selected;
        if (args.length == 2 && args[1].equals("toggle")) {
            if (facility == Facility.CRAFT) town.toggleCraft(player);
            else toggleLumber(player, facility);
        } else if (args.length == 2 && args[1].equals("upgrade") && facility.isProducer()) {
            int level = lumberLevel(player, facility);
            if (level < MAX_FACILITY_LEVEL) {
                final Pet backPet = selected;
                final String upgradeKey = lumberKey(player, facility) + ".level";
                town.openFacilityUpgrade(player, facility, upgradeKey, level, () -> lumberLevel(player, facility),
                    () -> openMenu(player, backPet, menu.station, menu.craftPage, menu.craftSelection));
                return true;
            }
        } else if (args.length == 3 && args[1].equals("remove") && facility.isProducer()) {
            int target;
            try { target = Integer.parseInt(args[2]); } catch (NumberFormatException ex) { return true; }
            if (target < 1 || target > 5) return true;
            for (String entry : craftEntries(player)) if (assignedHere(player, entry, facility) && productionSlot(player, entry) == target) {
                Workshop state = productionState(player, entry, System.currentTimeMillis());
                if (save(craftEntryPath(player, entry), new Workshop(state.food(), state.stock(), System.currentTimeMillis(), false)))
                    player.sendMessage(entryPet(entry).label + "를 창고로 돌려보냈습니다.");
                break;
            }
        } else if (args.length == 4 && args[1].equals("assign")) {
            int source, target;
            try { source = Integer.parseInt(args[2]); target = Integer.parseInt(args[3]); }
            catch (NumberFormatException ex) { return true; }
            List<Pet> pets = menuPets(player, facility);
            int index = PET_SLOTS.indexOf(source);
            if (index < 0 || index >= pets.size()) return true;
            selected = pets.get(index);
            if (facility == Facility.CRAFT && target == 1) changeCraftWorker(player, selected, true);
            else if (facility.isProducer()) assignLumber(player, selected.name(), target, facility);
        }
        openMenu(player, selected, menu.station, menu.craftPage, menu.craftSelection);
        return true;
    }

    private static Pet entryPet(String entry) { return entry == null ? null : petBySkin(entry.split(":", 2)[0]); }

    private String craftEntryPath(Player player, String entry) {
        return entry.contains(":") ? "pet-storage." + player.getUniqueId() + ".workers." + entry.split(":")[1]
            : entry == null || entryPet(entry) == null ? "invalid-pets." + player.getUniqueId() + "." + entry : path(player, entryPet(entry));
    }

    private int craftAptitude(Player player, String entry) {
        if (entryPet(entry) == null) return 0;
        return aptitude(player, entryPet(entry), Facility.CRAFT);
    }

    private List<String> craftEntries(Player player) {
        return storageEntries(player).stream().filter(entry -> !entry.isEmpty()).toList();
    }

    private void drawStationStorage(Player player, StationMenu menu, int page, String selection) {
        List<String> entries = craftEntries(player);
        int pages = Math.max(1, (entries.size() + PET_SLOTS.size() - 1) / PET_SLOTS.size());
        menu.craftPage = Math.clamp(page, 0, pages - 1);
        menu.craftSelection = selection != null && entries.contains(selection) ? selection : null;
        Inventory inventory = menu.inventory;
        Facility facility = facilityAt(menu.station);
        for (int i = 0; i < PET_SLOTS.size(); i++) {
            int index = menu.craftPage * PET_SLOTS.size() + i;
            String entry = index < entries.size() ? entries.get(index) : null;
            inventory.setItem(PET_SLOTS.get(i), entry == null ? null : statusIcon(petIcon(entryPet(entry)),
                (entry.equals(menu.craftSelection) ? "§e▶ " : "") + entryPet(entry).label,
                stationPetStatus(player, entry, facility), petAssignment(player, entry)));
        }
        inventory.setItem(0, statusIcon(Material.CHEST, "펫 창고", (menu.craftPage + 1) + "," + pages + "," + entries.size()));
        inventory.setItem(21, null);
        inventory.setItem(22, null);
        inventory.setItem(25, null);
        if (menu.craftSelection == null) {
            inventory.setItem(16, statusIcon(Material.BARRIER, "펫을 선택하세요"));
            return;
        }
        String entry = menu.craftSelection;
        Pet pet = entryPet(entry);
        List<String> details = new ArrayList<>();
        details.add(aptitudeLabel(pet.aptitude) + " 적성 Lv." + pet.level);
        if (levelBonusText(pet.level, facility) != null) details.add(levelBonusText(pet.level, facility));
        details.add(stationPetStatus(player, entry, facility));
        details.add(petAssignment(player, entry));
        inventory.setItem(16, statusIcon(petIcon(pet), pet.label, details.toArray(String[]::new)));
        inventory.setItem(25, statusIcon(Material.CARROT, "먹이 " + data.getInt(craftEntryPath(player, entry) + ".food") + "/100"));
        if (facility == Facility.CRAFT && craftAptitude(player, entry) > 0) inventory.setItem(21, statusIcon(Material.LEAD, "제작대에 배치"));
    }

    private String petAssignment(Player player, String entry) {
        for (var block : facilityBlocks(ownedPlot(player), Facility.CRAFT)) {
            if (entry.equals(craftWorkerEntry(player, block)))
                return "기본 작업대 (" + block.getX() + ", " + block.getY() + ", " + block.getZ() + ") 배치 중";
        }
        Facility facility = productionFacility(player, entry);
        if (facility != null && productionSlot(player, entry) > 0) {
            String anchor = assignedProducer(player, entry, facility);
            long pause = data.getLong(producerKey(ownedPlot(player), facility, anchor) + ".paused");
            return facility.label + " (" + anchor.replace("_", ", ") + ")" + (pause > 0 ? " 작업 대기 중" : " 배치 중");
        }
        return "창고 대기";
    }

    private String stationPetStatus(Player player, String entry, Facility facility) {
        if (facility == Facility.CRAFT) return craftAptitude(player, entry) > 0 ? "배치 가능" : "적성 불일치";
        return productionAptitude(player, entry, facility) == 0 ? "적성 불일치" : "배치 가능";
    }

    private boolean stationStorageAction(Player player, StationMenu menu, String[] args) {
        int page = menu.craftPage;
        String entry = menu.craftSelection;
        try {
            if (args[1].equals("page") && args.length == 3) page = Integer.parseInt(args[2]);
            if ((args[1].equals("select") || args[1].equals("assign")) && args.length >= 3) {
                int index = PET_SLOTS.indexOf(Integer.parseInt(args[2]));
                List<String> entries = craftEntries(player);
                int absolute = page * PET_SLOTS.size() + index;
                if (index < 0 || absolute >= entries.size()) return true;
                entry = entries.get(absolute);
            }
            Facility facility = facilityAt(menu.station);
            if (args[1].equals("assign") && facility != Facility.CRAFT) {
                if (entry != null && args.length == 4 && stationPetStatus(player, entry, facility).equals("배치 가능"))
                    assignLumber(player, entry, Integer.parseInt(args[3]), facility);
                else player.sendMessage("이 개체는 해당 시설에 배치할 수 없습니다.");
            } else if (args[1].equals("assign") || args[1].equals("release")) {
                if (data.contains(processKey(player))) player.sendMessage("기존 주문을 수령한 뒤 배치를 변경하세요.");
                else if (args[1].equals("release")) store(craftWorkerKey(player), null);
                else if (entry != null && craftAptitude(player, entry) > 0
                    && (!craftWorkers(player).contains(entry) || entry.equals(data.getString(craftWorkerKey(player))))
                    && !productionState(player, entry, System.currentTimeMillis()).active())
                    store(craftWorkerKey(player), entry);
                else player.sendMessage("제작 적성이 있고 다른 작업에 배치되지 않은 펫을 선택하세요.");
            } else if (args[1].equals("feed") && entry != null) {
                ItemStack hand = player.getInventory().getItemInMainHand();
                int nutrition = town.nutrition(hand);
                String key = craftEntryPath(player, entry) + ".food";
                int food = data.getInt(key);
                int count = nutrition == 0 ? 0 : Math.min(hand.getAmount(), (100 - food) / nutrition);
                if (count > 0 && store(key, food + count * nutrition)) hand.setAmount(hand.getAmount() - count);
                else player.sendMessage("주 손에 먹이를 들고 펫의 포만도를 확인하세요.");
            }
        } catch (NumberFormatException ignored) { return true; }
        openMenu(player, menu.selected, menu.station, page, entry);
        return true;
    }

    private Facility productionFacility(Player player, String entry) {
        String saved = data.getString(craftEntryPath(player, entry) + ".production-facility");
        if (saved != null) {
            try { return Facility.valueOf(saved); } catch (IllegalArgumentException ex) { return null; }
        }
        Pet species = entryPet(entry);
        if (species == null) return null;
        Facility primary = species.aptitude;
        return primary.isProducer() ? primary : null;
    }

    private int productionAptitude(Player player, String entry, Facility facility) {
        if (facility == null) return 0;
        Pet pet = entryPet(entry);
        return pet != null && pet.aptitude.isProducer() ? aptitude(player, pet, facility) : 0;
    }

    private int productionSlot(Player player, String entry) {
        String key = craftEntryPath(player, entry);
        if (!data.getBoolean(key + ".active") || !data.getBoolean(key + ".facility-bound")) return 0;
        return data.getInt(key + ".lumber-slot", entry.contains(":") ? 0 : 1);
    }

    private Workshop productionState(Player player, String entry, long now) {
        String key = craftEntryPath(player, entry);
        Facility facility = productionFacility(player, entry);
        int aptitude = productionAptitude(player, entry, facility);
        String plot = ownedPlot(player);
        String anchor = facility == null ? "" : assignedProducer(player, entry, facility);
        boolean active = aptitude > 0 && productionSlot(player, entry) > 0
            && facilityBlocks(plot, facility).stream().anyMatch(block -> coordinates(block).equals(anchor));
        if (active) {
            long paused = data.getLong(producerKey(plot, facility, anchor) + ".paused");
            settleFacility(player, facility, anchor, paused > 0 ? Math.min(paused, now) : now);
        }
        int cap = active ? facilityCapacity(player, facility, anchor) : CAPACITY;
        return new Workshop(data.getInt(key + ".food"), data.getInt(key + ".stock"), data.getLong(key + ".since"), active, cap);
    }

    /** Facility stock capacity: 64 + sum over the placed pets of (aptitude level x 64). */
    private int facilityCapacity(Player player, Facility facility, String anchor) {
        if (facility == null) return CAPACITY;
        int cap = CAPACITY;
        for (String entry : facilityGroup(player, facility, anchor))
            cap += Math.max(1, productionAptitude(player, entry, facility)) * 64;
        return cap;
    }

    /** Seconds-per-item for a facility with n working pets: 20 s for one, 3 s faster per extra pet, scaled by aptitude level, minimum 1 s. */
    /** Aptitude level multiplier: 0.7^(level-1) (Lv1 x1.0, Lv2 x0.7, Lv3 x0.49); used by the crafting station. */
    static double levelMultiplier(double level) { return Math.pow(0.7, Math.max(1.0, level) - 1.0); }
    /** Producer speed reduction (fraction of the 20 s base) of one working pet: Lv1 5%, Lv2 10%, Lv3 20%. */
    static double producerReduction(int level) { return level >= 3 ? 0.20 : level == 2 ? 0.10 : 0.05; }
    /** Period in ms for the given working pets' aptitude levels: 20 s x (1 - sum of reductions), never below 1 s. */
    static long facilityPeriod(int[] workingLevels) {
        double reduction = 0;
        for (int level : workingLevels) reduction += producerReduction(level);
        return Math.max(1000L, Math.round(20.0 * (1.0 - reduction) * 1000.0));
    }
    /** Seconds text, one decimal only when not an integer. */
    static String formatSeconds(long millis) {
        if (millis % 1000L == 0) return Long.toString(millis / 1000L);
        return String.format(java.util.Locale.ROOT, "%.1f", millis / 1000.0);
    }
    static String levelBonusText(int level, Facility kind) {
        if (kind != null && kind.isProducer()) return "작업 속도 -" + Math.round(producerReduction(level) * 100.0) + "%";
        long pct = Math.round((1.0 - levelMultiplier(level)) * 100.0);
        return pct > 0 ? "적성 보너스 -" + pct + "%" : null;
    }
    static int facilitySlots(int level) { return 2 * Math.clamp(level, 1, MAX_FACILITY_LEVEL) - 1; }
    private static final int MAX_FACILITY_LEVEL = 3;

    /** Pets placed at one producer station (valid species, slot > 0), ordered by slot. */
    private List<String> facilityGroup(Player player, Facility facility, String anchor) {
        List<String> group = new ArrayList<>();
        for (String entry : craftEntries(player)) {
            if (productionFacility(player, entry) != facility || !assignedProducer(player, entry, facility).equals(anchor)) continue;
            if (productionAptitude(player, entry, facility) < 1 || productionSlot(player, entry) < 1) continue;
            group.add(entry);
        }
        group.sort(Comparator.comparingInt(entry -> productionSlot(player, entry)));
        return group;
    }

    /**
     * Facility-level production: the placed pets work as one unit, producing 1 item per facilityPeriod(n) where n is the
     * number of pets with food; every item costs food from each working pet by its aptitude level (Lv.1 -1, Lv.2 -3, Lv.3 -5). Settles (in memory) up to {@code end}.
     */
    private void settleFacility(Player player, Facility facility, String anchor, long end) {
        List<String> group = facilityGroup(player, facility, anchor);
        int size = group.size();
        if (size == 0) return;
        int[] food = new int[size], stock = new int[size], lvl = new int[size];
        long clock = end;
        int cap = facilityCapacity(player, facility, anchor);
        for (int i = 0; i < size; i++) {
            lvl[i] = Math.max(1, productionAptitude(player, group.get(i), facility));
            String key = craftEntryPath(player, group.get(i));
            food[i] = data.getInt(key + ".food");
            stock[i] = data.getInt(key + ".stock");
            long since = data.getLong(key + ".since");
            clock = Math.min(clock, since > 0 ? since : end);
        }
        while (true) {
            int working = 0, target = -1, held = 0;
            int[] levels = new int[size];
            for (int i = 0; i < size; i++) held += stock[i];
            for (int i = 0; i < size; i++) if (food[i] > 0) {
                levels[working++] = lvl[i];
                if (target < 0 && held < cap) target = i;
            }
            if (working == 0 || target < 0) { clock = end; break; }
            long period = facilityPeriod(Arrays.copyOf(levels, working));
            if (clock + period > end) break;
            clock += period;
            stock[target]++;
            for (int i = 0; i < size; i++) if (food[i] > 0) food[i] = Math.max(0, food[i] - foodCost(lvl[i]));
        }
        for (int i = 0; i < size; i++) {
            String key = craftEntryPath(player, group.get(i));
            data.set(key + ".food", food[i]);
            data.set(key + ".stock", stock[i]);
            data.set(key + ".since", clock);
        }
    }

    private void assignLumber(Player player, String entry, int slot, Facility facility) {
        if (currentProducer(player, facility) == null || productionAptitude(player, entry, facility) == 0 || slot < 1 || slot > facilitySlots(lumberLevel(player, facility))) {
            player.sendMessage("시설에 맞는 적성의 펫을 열린 슬롯에 배치하세요.");
            return;
        }
        if (craftWorkers(player).contains(entry)) {
            player.sendMessage("먼저 제작대에서 펫 배치를 해제하세요."); return;
        }
        for (String other : craftEntries(player)) if (!other.equals(entry) && assignedHere(player, other, facility)
            && productionSlot(player, other) == slot) {
            player.sendMessage("이미 펫이 배치된 슬롯입니다."); return;
        }
        String key = craftEntryPath(player, entry);
        long now = System.currentTimeMillis();
        {   // settle the facility with its current pet count before the pet count changes
            long paused = lumberPause(player, facility);
            settleFacility(player, facility, coordinates(currentProducer(player, facility)), paused > 0 ? Math.min(paused, now) : now);
        }
        Workshop state = productionState(player, entry, now);
        if (!assignedHere(player, entry, facility) && (state.active() || state.stock() > 0)) {
            player.sendMessage("기존 시설에서 배치를 해제하고 생산품을 먼저 수령하세요."); return;
        }
        Map<String, Object> previous = new HashMap<>();
        for (String field : List.of("production-facility", "production-anchor", "lumber-slot", "facility-bound"))
            previous.put(key + "." + field, data.get(key + "." + field));
        data.set(key + ".production-facility", facility.name());
        data.set(key + ".production-anchor", coordinates(currentProducer(player, facility)));
        data.set(key + ".lumber-slot", slot);
        data.set(key + ".facility-bound", true);
        if (!save(key, new Workshop(state.food(), state.stock(), state.active() ? state.since() : now, true)))
            previous.forEach(data::set);
    }

    private void refreshLumber() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof StationMenu menu
                && facilityAt(menu.station) != null && facilityAt(menu.station).isProducer() && nearFacility(player, facilityAt(menu.station)))
                updateLumber(player, menu.inventory, facilityAt(menu.station));
        }
    }

    private void updateLumber(Player player, Inventory inventory, Facility facility) {
        long paused = lumberPause(player, facility), now = System.currentTimeMillis();
        int total = 0, working = 0, cap = CAPACITY;
        List<Integer> workLevels = new ArrayList<>();
        long start = 0, cycle = 0;
        boolean room = false;
        List<String[]> shown = new ArrayList<>();
        for (String entry : craftEntries(player)) {
            if (!assignedHere(player, entry, facility)) continue;
            Pet pet = entryPet(entry);
            if (pet == null || productionAptitude(player, entry, facility) < 1) continue;
            Workshop state = productionState(player, entry, now);
            total += state.stock();
            int slot = productionSlot(player, entry);
            if (slot == 0) continue;
            shown.add(new String[]{entry, Integer.toString(slot)});
            if (state.food() == 0) continue;
            working++;
            workLevels.add(Math.max(1, productionAptitude(player, entry, facility)));
            cap = Math.max(cap, state.cap());
            start = state.since();
        }
        room = working > 0 && total < cap;
        long perItem = facilityPeriod(workLevels.stream().mapToInt(Integer::intValue).toArray());
        if (working > 0 && room) cycle = perItem;
        for (String[] item : shown) {
            Pet pet = entryPet(item[0]);
            Workshop state = productionState(player, item[0], now);
            inventory.setItem(10 + Integer.parseInt(item[1]), statusIcon(petIcon(pet), pet.label,
                "작업 중인 펫 " + working + "마리 · 개당 " + formatSeconds(perItem) + "초",
                "먹이 " + state.food() + "/100", "생산품 " + state.stock() + "/" + state.cap(),
                paused > 0 ? "작업 대기 중" : "배치 중"));
        }
        inventory.setItem(34, statusIcon(product(facility), "생산품 " + total + "개",
            total + "," + start + "," + cycle + "," + paused));
    }

    private static Material product(Facility facility) {
        return switch (facility) {
            case FARM -> ModItems.byId("straw");
            case CARROT_FARM -> ModItems.byId("pet_carrot");
            case POTATO_FARM -> Material.POTATO;
            case MINE -> Material.IRON_INGOT;
            case DIAMOND_MINE -> Material.DIAMOND;
            case AMBER_QUARRY -> modOr("amber", Material.AMETHYST_SHARD);
            case CABBAGE_FARM -> modOr("cabbage", Material.CARROT);
            case REDWOOD_LUMBER -> modOr("redwood_log", Material.DARK_OAK_LOG);
            default -> Material.OAK_LOG;
        };
    }

    private static Material modOr(String id, Material fallback) {
        Material mod = ModItems.byIdOrNull(id);
        return mod != null ? mod : fallback;
    }

    private void collectLumber(Player player, Facility facility) {
        ItemStack output = switch (facility) {
            case FARM -> town.foodItem("straw");
            case CARROT_FARM -> town.foodItem("pet_carrot");
            default -> new ItemStack(product(facility));
        };
        int capacity = 0;
        {
            for (ItemStack stack : player.getInventory().getStorageContents()) {
                if (stack == null || stack.getType().isAir()) capacity += 64;
                else if (stack.isSimilar(output)) capacity += Math.max(0, 64 - stack.getAmount());
            }
        }
        int taken = 0;
        long now = System.currentTimeMillis();
        Map<String, Object> previous = new HashMap<>();
        for (String entry : craftEntries(player)) {
            if (taken == capacity) break;
            String key = craftEntryPath(player, entry);
            if (!assignedHere(player, entry, facility)) continue;
            Workshop state = productionState(player, entry, now);
            int amount = Math.min(state.stock(), capacity - taken);
            if (amount == 0) continue;
            for (String field : List.of("food", "stock", "since")) previous.put(key + "." + field, data.get(key + "." + field));
            data.set(key + ".food", state.food());
            data.set(key + ".stock", state.stock() - amount);
            data.set(key + ".since", state.stock() >= state.cap() ? now : state.since());
            taken += amount;
        }
        if (taken == 0) { if (capacity == 0) player.sendMessage("가방에 빈 칸이 더 필요합니다."); return; }
        if (!persist()) { previous.forEach(data::set); return; }
        while (taken > 0) {
            ItemStack stack = output.clone();
            stack.setAmount(Math.min(taken, stack.getMaxStackSize()));
            player.getInventory().addItem(stack);
            taken -= Math.min(taken, stack.getMaxStackSize());
        }
    }

    private void toggleLumber(Player player, Facility facility) {
        String key = lumberKey(player, facility) + ".paused";
        long paused = lumberPause(player, facility), now = System.currentTimeMillis();
        if (paused == 0) { store(key, now); return; }
        Map<String, Object> original = new HashMap<>();
        for (String entry : craftEntries(player)) if (assignedHere(player, entry, facility) && productionSlot(player, entry) > 0) {
            String since = craftEntryPath(player, entry) + ".since";
            long value = data.getLong(since);
            original.put(since, data.get(since));
            data.set(since, value + Math.max(0, now - Math.max(value, paused)));
        }
        if (!store(key, null)) original.forEach(data::set);
    }

    private void openMenu(Player player, Pet selection, org.bukkit.block.Block station) {
        openMenu(player, selection, station, 0, null);
    }

    private void openMenu(Player player, Pet selection, org.bukkit.block.Block station, int craftPage, String craftSelection) {
        withCraftStation(station, () -> renderStationMenu(player, selection, station, craftPage, craftSelection));
    }

    private void renderStationMenu(Player player, Pet selection, org.bukkit.block.Block station, int craftPage, String craftSelection) {
        Facility facility = facilityAt(station);
        List<Pet> pets = menuPets(player, facility);
        if (selection == null || !pets.contains(selection)) {
            selection = facility == Facility.CRAFT ? craftWorker(player) : null;
            if (selection == null && !pets.isEmpty()) selection = pets.getFirst();
        }
        var holder = new StationMenu(player.getUniqueId(), selection, station);
        String title = facility != null && facility.isProducer() ? "삐삐서버 · " + (facility == Facility.LUMBER ? "벌목장" : facility.label) : facility == Facility.CRAFT ? "삐삐서버 · 기본 작업대" : facility == null ? "펫 창고   ·   펫 정보" : facility.label + "   ·   펫 정보";
        MenuArt.View view = new MenuArt.View(Bukkit.createInventory(holder, 54, title), null);
        Inventory inventory = view.inventory();
        holder.inventory = inventory;
        ItemStack divider = statusIcon(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int row = 0; row < 6; row++) {
            if (facility != Facility.CRAFT && (facility == null || !facility.isProducer()) && !PET_SLOTS.contains(row * 9 + 2)) inventory.setItem(row * 9 + 2, divider);
            if (!PET_SLOTS.contains(row * 9 + 6)) inventory.setItem(row * 9 + 6, divider);
        }
        inventory.setItem(0, statusIcon(Material.CHEST, "§b펫 창고", "펫을 클릭하면 상세 정보를 봅니다."));
        inventory.setItem(4, statusIcon(facility == null ? Material.BARREL : facility.block, facility == null ? "§e펫 작업대" : "§e" + facility.label,
            facility == null ? "종류별 작업대에서 펫을 배치하세요." : "작업대 적성과 펫의 적성이 맞아야 합니다."));
        inventory.setItem(7, statusIcon(Material.BOOK, "§b펫 정보"));
        for (int i = 0; i < Math.min(pets.size(), PET_SLOTS.size()); i++) {
            Pet pet = pets.get(i);
            boolean captured = data.isConfigurationSection(path(player, pet));
            inventory.setItem(PET_SLOTS.get(i), statusIcon(petIcon(pet),
                (pet == selection ? "§e▶ " : "") + pet.label, captured ? "보유 · 클릭하여 선택" : "미포획"));
        }
        if (selection == null) {
            inventory.setItem(16, statusIcon(Material.BARRIER, "보유 펫 없음", "야생에서 펫을 포획하세요."));
        } else {
            long now = System.currentTimeMillis();
            Workshop state = workshop(player, selection, now);
            String status = selection.aptitude.isProducer() && state.active() && lumberPause(player, selection.aptitude) > 0 ? "작업 대기 중" : !state.active() ? "대기 중" : state.stock() >= state.cap() ? "보관함 가득 참"
                : state.food() == 0 ? "먹이 부족" : "작업 중";
            List<String> details = new ArrayList<>();
            for (Facility kind : Facility.values()) {
                int level = aptitude(player, selection, kind);
                if (level > 0) {
                    details.add(aptitudeLabel(kind) + " 적성 Lv." + level);
                    if (levelBonusText(level, kind) != null) details.add(levelBonusText(level, kind));
                }
            }
            if (facility == Facility.CRAFT) status = selection == craftWorker(player) ? "기본 작업대 배치"
                : state.active() ? "다른 작업대에 배치됨" : "가공 배치 가능";
            else if (facility == null && selection == craftWorker(player)) status = "기본 작업대 배치";
            details.add("상태: " + status);
            inventory.setItem(16, statusIcon(petIcon(selection), "§f" + selection.label, details.toArray(String[]::new)));
            inventory.setItem(25, statusIcon(Material.CARROT, "먹이 " + state.food() + "/100"));
            if (facility != Facility.CRAFT) inventory.setItem(34, statusIcon(selection.product(), "생산품 " + state.stock() + "/" + state.cap()));
            inventory.setItem(13, statusIcon(petIcon(selection), selection.label + " · " + status));
            if (station != null) {
                if (facility != null && aptitude(player, selection, facility) > 0)
                    inventory.setItem(21, statusIcon(Material.LEAD, "작업 배치"));
                inventory.setItem(22, statusIcon(Material.CARROT, "먹이 넣기", "펫 전용 먹이(" + town.foodSummary() + ")를 주 손에 들고 클릭"));
                if (facility != Facility.CRAFT) inventory.setItem(23, statusIcon(Material.CHEST, "생산품 수령"));
                if (facility != Facility.CRAFT) inventory.setItem(31, statusIcon(Material.GOLD_INGOT, "생산품 판매", "이번 시간 판매 가능: " + saleRemaining(player, selection, now) + "개", "매시 정각 초기화"));
                inventory.setItem(32, statusIcon(Material.BARRIER, "작업 중단"));
            }
        }
        if (facility == Facility.CRAFT) {
            String worker = data.getString(craftWorkerKey(player));
            inventory.setItem(13, worker == null ? statusIcon(Material.BARRIER, "배치된 펫 없음")
                : statusIcon(petIcon(entryPet(worker)), entryPet(worker).label, "제작 적성 Lv." + craftLevel(player), "포만도 " + craftFood(player) + "/100"));
            inventory.setItem(32, worker == null ? null : statusIcon(Material.BARRIER, "다시 선택"));
            inventory.setItem(34, town.craftJobIcon(player));
            drawStationStorage(player, holder, craftPage, craftSelection);
            int catalogPage = catalogPage(player);
            town.catalogPage(player, catalogPage).forEach((slot, key) -> inventory.setItem(slot, town.recipeIcon(player, key)));
            inventory.setItem(23, statusIcon(Material.BOOK, "제작 목록", (catalogPage + 1) + "," + town.catalogPages(player)));
            
        }
        if (facility != null && facility.isProducer()) {
            int level = lumberLevel(player, facility);
            inventory.setItem(4, level >= MAX_FACILITY_LEVEL ? statusIcon(product(facility), "Lv. " + level)
                : statusIcon(product(facility), "Lv. " + level, TownSystems.upgradeLines(facility, level).toArray(String[]::new)));
            inventory.setItem(32, statusIcon(Material.LEVER, lumberPause(player, facility) == 0 ? "작업 중단" : "작업 개시"));
            for (int i = 0; i < 5; i++)
                inventory.setItem(11 + i, i < facilitySlots(level) ? null : statusIcon(Material.IRON_BARS, "잠긴 슬롯"));
            drawStationStorage(player, holder, craftPage, craftSelection);
            updateLumber(player, inventory, facility);
        }
        view.open(player);
    }

    @EventHandler
    public void stationClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof StationMenu menu)) return;
        if (event.getRawSlot() >= 54 && facilityAt(menu.station) == Facility.CRAFT
            && event.getWhoClicked().getUniqueId().equals(menu.owner)
            && event.getClick() == ClickType.LEFT && event.getAction() != InventoryAction.COLLECT_TO_CURSOR) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !menu.owner.equals(player.getUniqueId())) return;
        int slot = event.getRawSlot();
        Facility kind = facilityAt(menu.station);
        if ((event.isLeftClick() || event.isRightClick()) && !event.isShiftClick() && kind != null
            && (kind == Facility.CRAFT && (slot == 13 || slot == 16 && menu.craftSelection != null
                    && menu.craftSelection.equals(craftWorkerEntry(player, menu.station)))
                || kind.isProducer() && slot >= 11 && slot <= 15)) {
            final int clicked = slot;
            getServer().getScheduler().runTask(this, () -> {
                if (!player.isOnline() || player.getOpenInventory().getTopInventory().getHolder() != menu) return;
                if (!owns(player, plotAt(menu.station.getLocation())) || facilityAt(menu.station) != kind
                    || !player.getWorld().equals(menu.station.getWorld())
                    || player.getLocation().distanceSquared(menu.station.getLocation().add(.5, .5, .5)) > 36) {
                    player.closeInventory();
                    return;
                }
                if (kind == Facility.CRAFT) releaseCraftWorkerAt(player, menu.station);
                else for (String entry : craftEntries(player))
                    if (assignedHere(player, entry, kind) && productionSlot(player, entry) == clicked - 10) {
                        releaseProducerEntry(player, entry);
                        break;
                    }
                openMenu(player, menu.selected, menu.station, menu.craftPage, menu.craftSelection);
            });
            return;
        }
        if (!event.isLeftClick()) return;
        List<Pet> pets = menuPets(player, kind);
        int petIndex = PET_SLOTS.indexOf(slot);
        boolean selecting = petIndex >= 0 && petIndex < pets.size();
        Pet selected = selecting ? pets.get(petIndex) : menu.selected;
        String action = STATION_ACTIONS.get(slot);
        String order = kind == Facility.CRAFT ? ((slot == 49 || slot == 34) ? "collect" : town.catalogPage(player, catalogPage(player)).get(slot)) : null;
        boolean lumberOutput = kind != null && kind.isProducer() && slot == 34;
        if (!((kind == Facility.CRAFT || kind != null && kind.isProducer()) && petIndex >= 0) && !lumberOutput && order == null && (selected == null || !pets.contains(selected) || !selecting && (menu.station == null || action == null))) return;
        if (kind == Facility.CRAFT && ("collect".equals(action) || "sell".equals(action))) return;
        getServer().getScheduler().runTask(this, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory().getHolder() != menu) return;
            if (menu.station != null && (!owns(player, plotAt(menu.station.getLocation()))
                || facilityAt(menu.station) != kind
                || facilityAt(menu.station) == null && !legacyStation(menu.station, plotAt(menu.station.getLocation()))
                || !player.getWorld().equals(menu.station.getWorld())
                || player.getLocation().distanceSquared(menu.station.getLocation().add(.5,.5,.5)) > 36)) {
                player.closeInventory();
                return;
            }
            if ((kind == Facility.CRAFT || kind != null && kind.isProducer()) && petIndex >= 0) {
                stationStorageAction(player, menu, new String[]{"workstation", "select", Integer.toString(slot)});
                return;
            }
            if (lumberOutput) {
                if (event.isShiftClick()) collectLumber(player, kind);
                updateLumber(player, menu.inventory, kind);
                player.updateInventory();
                return;
            }
            if (kind != null && kind.isProducer() && slot == 32 && !selecting) {
                toggleLumber(player, kind);
                openMenu(player, selected, menu.station, menu.craftPage, menu.craftSelection);
                return;
            }
            if (!selecting) {
                Facility facility = facilityAt(menu.station);
                if (order != null) {
                    if (facility == Facility.CRAFT) {
                        if (slot == 34) {
                            if (event.isShiftClick()) Bukkit.getScheduler().runTask(this, () -> {
                                if (player.getOpenInventory().getTopInventory().getHolder() != menu) return;
                                town.collectCraft(player, true, false);
                                refreshCraft(player);
                            });
                            return;
                        } else player.performCommand("pipilife process " + order);
                    }
                } else if (action.equals("assign") || action.equals("stop")) {
                    if (facility == Facility.CRAFT) {
                        Pet target = action.equals("assign") ? selected : craftWorker(player);
                        if (target != null) changeCraftWorker(player, target, action.equals("assign"));
                    }
                    else if (facility == selected.aptitude) player.performCommand("pipilife " + action + " " + selected.name().toLowerCase(Locale.ROOT));
                    else player.sendMessage("이 펫을 배치할 수 있는 작업대에서 사용하세요.");
                } else {
                    player.performCommand("pipilife " + action + " " + selected.name().toLowerCase(Locale.ROOT));
                }
            }
            openMenu(player, selected, menu.station, menu.craftPage, menu.craftSelection);
        });
    }

    @EventHandler
    public void stationDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof StationMenu) event.setCancelled(true);
    }

    boolean tutorialComplete(Player player) {
        return data.getBoolean(tutorialPath(player) + ".done");
    }

    private void giveStarterKit(Player player) {
        String key = tutorialPath(player) + ".starter-kit";
        String installKey = tutorialPath(player) + ".installable-starter-kit";
        if (data.getBoolean(installKey)) return;
        boolean upgrading = data.getBoolean(key);
        List<ItemStack> kit = new ArrayList<>(List.of(stationItem(Facility.PET_STORAGE), stationItem(Facility.RESEARCH)));
        if (!upgrading) {
            kit.add(stationItem(Facility.CRAFT));
            ItemStack pickaxe = new ItemStack(Material.IRON_PICKAXE);
            GearModels.apply(pickaxe);
            kit.add(pickaxe);
        }
        if (!store(installKey, true)) {
            player.sendMessage("시작 보상을 저장하지 못했습니다. 다시 접속하면 재시도합니다.");
            return;
        }
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer()
                .has(new NamespacedKey(this, "starter_menu"), PersistentDataType.STRING)) item.setAmount(0);
        }
        for (ItemStack remaining : player.getInventory().addItem(kit.toArray(ItemStack[]::new)).values())
            player.getWorld().dropItemNaturally(player.getLocation(), remaining).setOwner(player.getUniqueId());
        player.sendMessage(upgrading ? "설치형 펫 창고와 연구소를 받았습니다." : "기본 가공 제작대, 펫 창고, 연구소, 철 곡괭이를 받았습니다.");
    }

    String tutorialPath(Player player) { return "tutorial." + player.getUniqueId(); }

    private boolean mapCommand(Player player, String[] args) {
        String path = tutorialPath(player);
        if (args[0].equalsIgnoreCase("maps")) {
            if (!player.isOp()) return true;
            if (args.length == 2 && args[1].equalsIgnoreCase("build")) {
                if (data.isConfigurationSection("plots")) {
                    player.sendMessage("기존 부지가 있습니다. 새 서버의 PipiLife 데이터로 실행하세요.");
                    return true;
                }
                maps.build(player, () -> {
                    for (VillageMaps.Plot plot : VillageMaps.plots())
                        data.createSection("plots." + plot.name(), Map.of("x", plot.x(), "z", plot.z()));
                    data.set("maps-ready", true);
                    if (persist()) {
                        World village = maps.open(maps.village());
                        if (village != null) town.installMarketNpcs(village);
                        return true;
                    }
                    data.set("plots", null);
                    data.set("maps-ready", null);
                    return false;
                });
            } else if (args.length == 3 && args[1].equalsIgnoreCase("visit")
                && Set.of("tutorial", "village").contains(args[2])) {
                World world = maps.open(args[2].equals("tutorial") ? VillageMaps.TUTORIAL : maps.village());
                if (world != null && data.getBoolean("maps-ready")) player.teleport(world.getSpawnLocation());
                else player.sendMessage("먼저 /pipilife maps build를 실행하세요.");
            } else player.sendMessage("/pipilife maps build · /pipilife maps visit <tutorial|village>");
            return true;
        }
        if (!player.getWorld().getName().equals(VillageMaps.TUTORIAL) || !data.getBoolean("maps-ready")) {
            player.sendMessage("튜토리얼 마을에서 사용하세요.");
            return true;
        }
        if (args[0].equalsIgnoreCase("finish") && args.length == 1) {
            World town = maps.open(maps.village());
            if (town == null || !player.teleport(town.getSpawnLocation())) {
                player.sendMessage("공동 마을로 이동하지 못했습니다.");
                return true;
            }
            data.set(path + ".done", true);
            if (!persist()) { data.set(path + ".done", null); player.sendMessage("튜토리얼 완료 상태 저장 실패."); }
            else { this.town.giveDeed(player); giveStarterKit(player); giveWarpBlueprint(player, false); }
        } else player.sendMessage("/pipilife finish");
        return true;
    }

    @EventHandler public void tutorialJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!data.getBoolean("maps-ready")) return;
        if (tutorialComplete(player)) {
            giveStarterKit(player);
            giveWarpBlueprint(player, true);
            town.giveDeed(player);
            return;
        }
        getServer().getScheduler().runTask(this, () -> {
            if (!player.isOnline()) return;
            World world = maps.open(VillageMaps.TUTORIAL);
            if (world != null && !world.equals(player.getWorld())) player.teleport(world.getSpawnLocation());
        });
    }

    private static final class TravelMenu implements InventoryHolder {
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    boolean canTravel(Player player) {
        return data.getBoolean("maps-ready") && data.getBoolean(tutorialPath(player) + ".done");
    }

    private boolean openTravel(Player player) {
        if (!canTravel(player)) {
            player.sendMessage("튜토리얼을 완료한 뒤 이동 메뉴를 이용할 수 있습니다.");
            return true;
        }
        TravelMenu menu = new TravelMenu();
        menu.inventory = Bukkit.createInventory(menu, 27, "삐삐서버 · 지역 이동");
        menu.inventory.setItem(11, travelIcon(Material.OAK_DOOR, "§a공동 마을", "상점 · 대장간 · 거주지"));
        menu.inventory.setItem(13, travelIcon(Material.WHITE_BED, "§b내 거주지", "선점한 부지로 이동"));
        menu.inventory.setItem(15, travelIcon(Material.GRASS_BLOCK, "§2야생", "사냥 · 채집 · 펫 포획"));
        player.openInventory(menu.inventory);
        return true;
    }

    private ItemStack travelIcon(Material material, String title, String description) {
        ItemStack icon = new ItemStack(material);
        var meta = icon.getItemMeta();
        meta.setDisplayName(title);
        meta.setLore(List.of(description, "§e클릭하여 이동"));
        icon.setItemMeta(meta);
        return icon;
    }

    @EventHandler public void travelClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof TravelMenu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !event.isLeftClick() || !canTravel(player)) return;
        int slot = event.getRawSlot();
        if (slot != 11 && slot != 13 && slot != 15) return;
        String name = slot == 11 ? maps.village() : getConfig().getString("wild-world", WILD);
        getServer().getScheduler().runTask(this, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory().getHolder() != menu || !canTravel(player)) return;
            if (slot == 13) { player.closeInventory(); player.performCommand("pipilife home"); return; }
            World destination = maps.open(name);
            if (destination == null) { player.sendMessage("이동할 월드를 찾지 못했습니다."); return; }
            player.closeInventory();
            if (player.teleport(destination.getSpawnLocation())) player.setFallDistance(0);
            else player.sendMessage("이동이 취소되었습니다.");
        });
    }

    @EventHandler public void travelDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof TravelMenu) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void tutorialSpawn(org.spigotmc.event.player.PlayerSpawnLocationEvent event) {
        Player player = event.getPlayer();
        if (!data.getBoolean("maps-ready") || data.getBoolean(tutorialPath(player) + ".done")) return;
        World world = Bukkit.getWorld(VillageMaps.TUTORIAL);
        if (world != null && (!player.hasPlayedBefore() || !world.equals(event.getSpawnLocation().getWorld()))) {
            event.setSpawnLocation(world.getSpawnLocation());
        }
    }

    private boolean isTravelCompass(ItemStack item) {
        return item != null && item.getType() == Material.COMPASS && item.hasItemMeta()
            && item.getItemMeta().getPersistentDataContainer().has(compassKey, PersistentDataType.BYTE);
    }

    private void giveCompass(Player player) {
        if (!canTravel(player)) {
            player.sendMessage("튜토리얼을 완료하면 이동 나침반을 받을 수 있습니다.");
            return;
        }
        for (ItemStack item : player.getInventory().getContents()) {
            if (isTravelCompass(item)) return;
        }
        int slot = player.getInventory().firstEmpty();
        if (slot < 0) {
            player.sendMessage("빈 칸을 만든 뒤 /pipilife compass로 이동 나침반을 받으세요.");
            return;
        }
        ItemStack compass = travelIcon(Material.COMPASS, "§b이동 나침반", "우클릭하여 지역 이동 메뉴 열기");
        var meta = compass.getItemMeta();
        meta.getPersistentDataContainer().set(compassKey, PersistentDataType.BYTE, (byte) 1);
        compass.setItemMeta(meta);
        player.getInventory().setItem(slot, compass);
        player.sendMessage("이동 나침반을 받았습니다. 손에 들고 우클릭하세요.");
    }

    @EventHandler
    public void useTravelCompass(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !isTravelCompass(event.getItem())
            || event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        event.setCancelled(true);
        openTravel(event.getPlayer());
    }

    private static final class StatusMenu implements InventoryHolder {
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    private boolean openStatus(Player player, boolean pets) {
        return combat.open(player);
    }

    ItemStack displayIcon(ItemStack prototype,String label,String... lore) {
        ItemStack icon=prototype.clone();icon.setAmount(1);var meta=icon.getItemMeta();meta.setDisplayName(label);meta.setLore(List.of(lore));icon.setItemMeta(meta);return icon;
    }
    ItemStack statusIcon(Material material, String label, String... lore) {
        ItemStack icon = new ItemStack(material);
        var meta = icon.getItemMeta();
        meta.setDisplayName(label);
        int visual=switch(org.bukkit.ChatColor.stripColor(label)) {case "참새" -> 48200;case "개구리" -> 48201;case "까만 고양이" -> 48202;case "마법사 토끼" -> 48203;case "빨간 후드 여우" -> 48204;case "수줍은 래서판다" -> 48205;case "해적 수달" -> 48206;case "가시 육지거북" -> 48207;case "통통 너구리" -> 48208;case "작은뿔 흰 토끼" -> 48209;case "큰발톱 두더지" -> 48210;case "전기 양" -> 48211;case "아기 반달가슴곰" -> 48212;case "장난꾸러기 페럿" -> 48213;case "천사 돼지" -> 48214;default -> 0;};if(visual>0)meta.setCustomModelData(visual);
        if(label.equals("잠금 해제"))meta.setCustomModelData(48107);
        if(label.equals("옵션 잠금"))meta.setCustomModelData(48106);
        if (material == Material.GOLD_INGOT && (label.contains("골드") || label.matches("[0-9]+"))) meta.setCustomModelData(48104);
        meta.setLore(List.of(lore));
        icon.setItemMeta(meta);
        return icon;
    }

    @EventHandler public void statusClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof StatusMenu) event.setCancelled(true);
    }

    @EventHandler public void statusDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof StatusMenu) event.setCancelled(true);
    }

    boolean townHome(Player player) { return town.command(player, new String[]{"home"}); }
    YamlConfiguration data() { return data; }
    String villageName() { return maps.village(); }
    World openWorld(String name) { return maps.open(name); }

    String ownedPlot(Player player) {
        var plots = data.getConfigurationSection("plots");
        if (plots == null) return null;
        for (String plot : plots.getKeys(false)) if (owns(player, plot)) return plot;
        return null;
    }

    boolean store(String path, Object value) {
        Object previous = data.get(path);
        data.set(path, value);
        if (persist()) return true;
        data.set(path, previous);
        return false;
    }

    private int hourlySaleLimit() { return Math.max(1, getConfig().getInt("hourly-sale-limit-per-pet", 16)); }

    private String saleKey(Player player, String goods) {
        return "sales." + player.getUniqueId() + "." + goods;
    }

    boolean reserveSale(Player player, String goods, int amount) {
        long now = System.currentTimeMillis();
        String key = saleKey(player, goods);
        int sold = soldThisHour(key, now);
        return amount > 0 && amount <= hourlySaleLimit() - sold
            && store(key, List.of(now / 3_600_000L, sold + amount));
    }
}






