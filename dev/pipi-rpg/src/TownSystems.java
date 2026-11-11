package dev.pipi.life;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scoreboard.Score;

final class TownSystems implements Listener {
    private final PipiLife plugin;
    private final TownNpcRegistry npcs;
    private final NamespacedKey deed;
    private final NamespacedKey processed;
    private final Map<UUID, LinkedHashSet<UUID>> parties = new HashMap<>();
    private final Map<UUID, UUID> membership = new HashMap<>();
    private final Map<UUID, Invite> invites = new HashMap<>();
    private final Map<UUID, Trade> trades = new HashMap<>();
    private record Trade(UUID seller, int slot, ItemStack item, int price, long expires) {}
    private record Invite(UUID leader, long expires) {}
    private record Goods(Material item, int price) {}
    private static final Map<String, Goods> GOODS = Map.of(
        "wood", new Goods(Material.OAK_LOG, 4), "iron", new Goods(Material.RAW_IRON, 6),
        "leather", new Goods(Material.LEATHER, 5), "fish", new Goods(Material.COD, 5),
        "carrot", new Goods(Material.CARROT, 2), "plank", new Goods(Material.OAK_PLANKS, 4),
        "ingot", new Goods(Material.IRON_INGOT, 14));

    TownSystems(PipiLife plugin) {
        this.plugin = plugin;
        npcs = new TownNpcRegistry(plugin);
        Bukkit.getPluginManager().registerEvents(npcs, plugin);
        deed = new NamespacedKey(plugin, "plot_deed");
        processed = new NamespacedKey(plugin, "processed_product");
        glow = new OreGlow(plugin);
        Bukkit.getPluginManager().registerEvents(glow, plugin);
    }

    private final OreGlow glow;
    void close() { glow.close(); }
    TownNpcRegistry npcs() { return npcs; }

    private boolean tell(Player player, String message) { player.sendMessage(message); return true; }

    // ---- vanilla enchanting is disabled everywhere ----
    private static final String NO_VANILLA = "§c바닐라 마법 부여는 사용할 수 없습니다. 연금술사에게 인챈트하세요.";
    private static boolean hasVanillaEnchants(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        var m = item.getItemMeta();
        if (m.hasEnchants()) return true;
        return m instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta s && s.hasStoredEnchants();
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void noEnchantTable(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        var block = event.getClickedBlock();
        if (block == null || block.getType() != Material.ENCHANTING_TABLE) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(NO_VANILLA);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void noEnchantOpen(org.bukkit.event.inventory.InventoryOpenEvent event) {
        if (event.getInventory().getType() != org.bukkit.event.inventory.InventoryType.ENCHANTING) return;
        event.setCancelled(true);
        if (event.getPlayer() instanceof Player p) p.sendMessage(NO_VANILLA);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void noEnchantPrepare(org.bukkit.event.enchantment.PrepareItemEnchantEvent event) { event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void noEnchantDo(org.bukkit.event.enchantment.EnchantItemEvent event) { event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void noAnvilEnchant(org.bukkit.event.inventory.PrepareAnvilEvent event) {
        var result = event.getResult();
        if (result == null) return;
        var inv = event.getInventory();
        var left = inv.getItem(0);
        var right = inv.getItem(1);
        boolean bad = (right != null && (right.getType() == Material.ENCHANTED_BOOK || hasVanillaEnchants(right)))
            || !result.getEnchantments().equals(left == null ? Map.of() : left.getEnchantments())
            || (result.hasItemMeta() && result.getItemMeta() instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta s && s.hasStoredEnchants());
        if (!bad) return;
        event.setResult(null);
        for (var viewer : event.getViewers()) if (viewer instanceof Player p) p.sendMessage(NO_VANILLA);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void noSmithingEnchant(org.bukkit.event.inventory.PrepareSmithingEvent event) {
        var result = event.getResult();
        if (result == null || !result.hasItemMeta() || !result.getItemMeta().hasEnchants()) return;
        var copy = result.clone();
        var meta = copy.getItemMeta();
        for (var e : List.copyOf(meta.getEnchants().keySet())) meta.removeEnchant(e);
        copy.setItemMeta(meta);
        event.setResult(copy);
    }
    private boolean town(Player player) { return player.getWorld().getName().equals(plugin.villageName()); }
    private String user(Player player) { return "town." + player.getUniqueId(); }

    void giveDeed(Player player) {
        if (!plugin.canTravel(player) || plugin.ownedPlot(player) != null) return;
        for (var item : player.getInventory().getContents()) if (isDeed(item)) return;
        int slot = player.getInventory().firstEmpty();
        if (slot < 0) { tell(player, "빈 칸을 만든 뒤 /pipilife deed를 사용하세요."); return; }
        ItemStack item = plugin.statusIcon(Material.PAPER, "거주지 땅 문서", "빈 부지 안에서 우클릭하여 선점");
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(deed, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        player.getInventory().setItem(slot, item);
    }

    private boolean isDeed(ItemStack item) {
        return item != null && item.hasItemMeta() &&
            item.getItemMeta().getPersistentDataContainer().has(deed, PersistentDataType.BYTE);
    }

    @EventHandler public void useDeed(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !isDeed(event.getItem()) ||
            event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        player.performCommand("pipilife claim");
        if (plugin.ownedPlot(player) != null) event.getItem().setAmount(event.getItem().getAmount() - 1);
    }

    @EventHandler(ignoreCancelled = true) public void protectBuild(BlockPlaceEvent event) {
        if (blocked(event.getPlayer(), event.getBlockPlaced())) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true) public void protectBreak(BlockBreakEvent event) {
        if (blocked(event.getPlayer(), event.getBlock())) event.setCancelled(true);
    }
    private boolean blocked(Player player, Block block) {
        return !player.isOp() && block.getWorld().getName().equals(plugin.villageName())
            && !plugin.owns(player, plugin.plotAt(block.getLocation()));
    }
    @EventHandler(ignoreCancelled = true) public void protectContainer(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && block != null
            && block.getState() instanceof org.bukkit.block.Container && blocked(event.getPlayer(), block))
            event.setCancelled(true);
    }

    boolean command(Player player, String[] args) {
        String action = args[0].toLowerCase(Locale.ROOT);
        if (action.equals("alchemy") && args.length == 2 && args[1].equals("install")) return installAlchemy(player);
        if (action.equals("party")) return party(player, args);
        if (!plugin.canTravel(player)) return tell(player, "튜토리얼을 먼저 완료하세요.");
        return switch (action) {
            case "deed" -> { giveDeed(player); yield true; }
            case "home" -> home(player);
            case "warp" -> warp(player, args);
            case "shop" -> shop(player, args);
            case "research" -> openSmith(player, true);
            case "process" -> {
                boolean result = process(player, args);
                if (args.length > 2) plugin.refreshCraft(player);
                yield result;
            }
            case "forge" -> args.length == 2 && args[1].equals("npc") ? spawnSmith(player) : openForgeDesk(player, -1, -1, false, null);
            case "trade" -> trade(player, args);
            case "alchemy" -> openAlchemy(player, 0, -1, 0, false, null);
            case "inherit" -> openInherit(player, 0, -1, -1, null, null, false, null);
            case "heal" -> heal(player);
            default -> false;
        };
    }

    private boolean home(Player player) {
        String plot = plugin.ownedPlot(player);
        World world = plugin.openWorld(plugin.villageName());
        if (plot == null || world == null) return tell(player, "공동 마을의 빈 부지를 먼저 선점하세요.");
        int x = plugin.data().getInt("plots." + plot + ".x") + 22;
        int z = plugin.data().getInt("plots." + plot + ".z") + 22;
        int y = world.getHighestBlockYAt(x, z) + 1;
        return move(player, new Location(world, x + .5, y, z + .5));
    }

    private boolean move(Player player, Location destination) {
        Block feet = destination.getBlock();
        if (!feet.isPassable() || !feet.getRelative(0, 1, 0).isPassable()
            || !feet.getRelative(0, -1, 0).getType().isSolid())
            return tell(player, "도착 공간이 막혔거나 바닥이 없습니다.");
        if (!player.teleport(destination)) return tell(player, "이동이 취소되었습니다.");
        player.setFallDistance(0);
        return true;
    }

    private boolean warp(Player player, String[] args) {
        var data = plugin.data();
        if (args.length == 1) {
            var points = data.getConfigurationSection("waypoints");
            if (points == null) return tell(player, "등록된 워프석이 없습니다.");
            var known = points.getKeys(false).stream()
                .filter(name -> data.getBoolean(user(player) + ".known." + name)).toList();
            return tell(player, known.isEmpty() ? "발견한 워프석이 없습니다." : String.join(", ", known));
        }
        if (args.length != 3 || !args[2].matches("[a-z0-9_-]{1,24}"))
            return tell(player, "/pipilife warp <discover|go|set> <이름>");
        String name = args[2], key = "waypoints." + name;
        if (args[1].equals("set") && player.isOp()) {
            Location l = player.getLocation();
            return tell(player, plugin.store(key, l) ? "워프석 등록 " + name : "저장 실패");
        }
        if (!args[1].equals("discover") && !args[1].equals("go"))
            return tell(player, "/pipilife warp <discover|go> <이름>");
        Location location;
        try { location = data.getLocation(key); }
        catch (IllegalArgumentException ex) { return tell(player, "워프 월드가 로드되지 않았습니다."); }
        if (location == null || location.getWorld() == null) return tell(player, "등록된 워프석이 없습니다.");
        String discovery = user(player) + ".known." + name;
        if (args[1].equals("discover")) {
            if (!location.getWorld().equals(player.getWorld()) || location.distanceSquared(player.getLocation()) > 16)
                return tell(player, "워프석 4블록 안에서 발견할 수 있습니다.");
            return tell(player, plugin.store(discovery, true) ? "워프 발견 " + name : "저장 실패");
        }
        if (!data.getBoolean(discovery)) return tell(player, "먼저 워프석을 직접 발견하세요.");
        return move(player, location);
    }

    Score gold(Player player) {
        var board = Bukkit.getScoreboardManager().getMainScoreboard();
        var objective = board.getObjective("en_money");
        if (objective == null) objective = board.registerNewObjective("en_money", "dummy", "골드");
        return objective.getScore(player.getName());
    }

    private int plainCount(Player player, Material material) {
        return itemCount(player, new ItemStack(material));
    }

    private int itemCount(Player player, ItemStack sample) {
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents())
            if (item != null && item.isSimilar(sample)) count += item.getAmount();
        return count;
    }

    private void remove(Player player, Material material, int amount) {
        player.getInventory().removeItem(new ItemStack(material, amount));
    }

    boolean testGive(Player player, String[] args) {
        if (!player.isOp()) return tell(player, "관리자 전용 명령입니다.");
        if (args.length == 3 && args[1].equals("gear")) {
            Material material = switch (args[2]) {
                case "sword" -> Material.IRON_SWORD;
                case "bow" -> Material.BOW;
                case "staff" -> Material.BLAZE_ROD;
                case "iron_pickaxe" -> Material.IRON_PICKAXE;
                case "diamond_pickaxe" -> Material.DIAMOND_PICKAXE;
                case "fishing_rod" -> Material.FISHING_ROD;
                default -> null;
            };
            if (material == null) return tell(player, "장비 ID: sword, bow, staff, iron_pickaxe, diamond_pickaxe, fishing_rod");
            ItemStack item = new ItemStack(material);
            var meta = item.getItemMeta();
            if (material == Material.BLAZE_ROD) meta.setDisplayName("연습용 지팡이");
            meta.getPersistentDataContainer().set(gearKey("catalog"), PersistentDataType.STRING,
                material == Material.BLAZE_ROD ? "연습용 지팡이" : material.name());
            item.setItemMeta(meta);
            GearModels.apply(item);
            for (ItemStack extra : player.getInventory().addItem(item).values())
                player.getWorld().dropItemNaturally(player.getLocation(), extra).setOwner(player.getUniqueId());
            return tell(player, "외형 테스트용 장비를 지급했습니다.");
        }
        if (args.length == 2 && args[1].equals("warden_soul_stone")) {
            for (ItemStack extra : player.getInventory().addItem(wardenSoulStoneItem(5)).values())
                player.getWorld().dropItemNaturally(player.getLocation(), extra).setOwner(player.getUniqueId());
            return tell(player, "워든의 영혼석 5개를 지급했습니다.");
        }
        if (args.length == 2 && args[1].equals("warp_stone_blueprint")) {
            ItemStack bp = plugin.warpBlueprint();
            if (bp == null) return tell(player, "워프석 청사진 아이템을 불러오지 못했습니다.");
            for (ItemStack extra : player.getInventory().addItem(bp).values())
                player.getWorld().dropItemNaturally(player.getLocation(), extra).setOwner(player.getUniqueId());
            return tell(player, "거주지 워프석 청사진을 지급했습니다.");
        }
        if (args.length == 3 && args[1].equals("blueprint")) {
            try {
                Facility facility = Facility.valueOf(args[2].toUpperCase(Locale.ROOT));
                if (facility == Facility.GOLD_MINE) throw new IllegalArgumentException();
                for (ItemStack extra : player.getInventory().addItem(plugin.stationItem(facility)).values())
                    player.getWorld().dropItemNaturally(player.getLocation(), extra).setOwner(player.getUniqueId());
                return tell(player, facility.label + " 청사진을 지급했습니다.");
            } catch (IllegalArgumentException ignored) {
                return tell(player, "시설 ID: " + String.join(", ", Arrays.stream(Facility.values())
                    .filter(facility -> facility != Facility.GOLD_MINE).map(facility -> facility.name().toLowerCase(Locale.ROOT)).toList()));
            }
        }
        if (args.length == 3) {
            Facility facility = switch (args[2]) {
                case "iron" -> Facility.MINE;
                case "diamond" -> Facility.DIAMOND_MINE;
                default -> null;
            };
            if (facility != null && (args[1].equals("core") || args[1].equals("quarry"))) {
                ItemStack item = args[1].equals("quarry") ? plugin.stationItem(facility)
                    : taggedItem(args[2] + "_core",
                        switch (args[2]) { case "iron" -> "철 코어"; default -> "다이아몬드 코어"; });
                if (args[1].equals("core")) item.setAmount(3);
                for (ItemStack extra : player.getInventory().addItem(item).values())
                    player.getWorld().dropItemNaturally(player.getLocation(), extra).setOwner(player.getUniqueId());
                return tell(player, "테스트 아이템을 지급했습니다.");
            }
        }
        return tell(player, "/pipilife testgive blueprint <시설 ID> 또는 testgive warp_stone_blueprint 또는 testgive <core|quarry> <iron|gold|diamond>");
    }

    private static final String SHOP_TITLE = "삐삐서버 · 상점";
    private static final long SHOP_HOUR = 3_600_000L;
    private record Product(String key, String name, ItemStack item, int price, int limit) {}
    private record Quote(int index, int count, long hour) {}

    private static final class ShopMenu implements InventoryHolder {
        final boolean buy;
        final int tab; // 0 구매, 1 광물 상점, 2 재료 상점
        final List<Product> products;
        final Quote quote;
        Inventory inventory;
        ShopMenu(int tab, List<Product> products, Quote quote) {
            this.tab = tab;
            this.buy = tab == 0;
            this.products = products;
            this.quote = quote;
        }
        @Override public Inventory getInventory() { return inventory; }
    }

    private ItemStack repairKit() {
        ItemStack item = plugin.statusIcon(ModItems.repairKit(), "§a수리키트", "인벤토리에서 집은 뒤 장비를 우클릭 · 최대 내구도 20% 수리");
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(gearKey("repair_kit"), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler(ignoreCancelled = true)
    public void useRepairKit(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
            || event.getView().getType() != org.bukkit.event.inventory.InventoryType.CRAFTING
            || !(event.getClickedInventory() instanceof org.bukkit.inventory.PlayerInventory)
            || event.getClick() != org.bukkit.event.inventory.ClickType.RIGHT) return;
        ItemStack kit = event.getCursor();
        if (kit == null || !kit.hasItemMeta() || !kit.getItemMeta().getPersistentDataContainer()
            .has(gearKey("repair_kit"), PersistentDataType.BYTE)) return;
        ItemStack current = event.getCurrentItem();
        if (current == null || !(current.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable damage)) return;
        int max = damage.hasMaxDamage() ? damage.getMaxDamage() : current.getType().getMaxDurability();
        if (max <= 0) return;
        event.setCancelled(true);
        if (damage.getDamage() <= 0) return;
        ItemStack target = current.clone();
        damage.setDamage(Math.max(0, damage.getDamage() - (int) Math.ceil(max * .20)));
        if (!target.setItemMeta(damage)) return;
        event.setCurrentItem(target);
        ItemStack remainder = kit.clone();
        remainder.setAmount(kit.getAmount() - 1);
        player.setItemOnCursor(remainder.getAmount() == 0 ? null : remainder);
    }

    record SaleSpec(String key,String name,Material material,String tag,int limit,int price) {}
    static final List<SaleSpec> SELL_CATALOG=List.of(
        new SaleSpec("raw_copper","구리 원석",Material.RAW_COPPER,null,1000,100),
        new SaleSpec("redstone","레드스톤 가루",Material.REDSTONE,null,1000,100),
        new SaleSpec("coal","석탄",Material.COAL,null,1000,100),
        new SaleSpec("lapis","청금석",Material.LAPIS_LAZULI,null,1000,100),
        new SaleSpec("quartz","네더 석영",Material.QUARTZ,null,1000,300),
        new SaleSpec("raw_gold","금 원석",Material.RAW_GOLD,null,1000,350),
        
        new SaleSpec("iron","철 원석",Material.RAW_IRON,null,1000,400),
        new SaleSpec("iron_ingot","철 주괴",Material.IRON_INGOT,null,1000,400),
        new SaleSpec("raw_diamond","다이아몬드",Material.DIAMOND,null,1000,1200),
        new SaleSpec("plate","강철",Material.IRON_NUGGET,"plate",200,2400),
        new SaleSpec("blue_diamond","블루 다이아몬드",Material.DIAMOND,"blue_diamond",200,3600),
        new SaleSpec("reinforced_plank","덧댄 판자",Material.OAK_PLANKS,"reinforced_plank",200,4000),
        new SaleSpec("iron_core","철 코어",Material.AMETHYST_SHARD,"iron_core",200,5000),
        new SaleSpec("diamond_core","다이아몬드 코어",Material.AMETHYST_SHARD,"diamond_core",200,10000),
        new SaleSpec("coated_plank","코팅된 판자",Material.OAK_PLANKS,"coated_plank",200,20250));
    /** Buy-side catalog: exactly the repair kit and the common blueprint paper (도면); price is per unit in gold. */
    private List<Product> buyProducts() {
        List<Product> products = new ArrayList<>(List.of(new Product("repair_kit", "수리키트", repairKit(), 1000, 0),
            new Product("blueprint_paper", "도면", blueprintPaper(), 100000, 0)));
        if (ModItems.byIdOrNull("advanced_blueprint_paper") != null) // 중급 도면: skipped when the mod item is missing
            products.add(new Product("advanced_blueprint_paper", "중급 도면", taggedItem("advanced_blueprint_paper", "중급 도면"), 500000, 0));
        for (String[] seasoning : new String[][]{{"salt","소금","200"},{"sugar","설탕","200"},{"pepper","후추","300"},{"soy_sauce","간장","500"}})
            if (ModItems.byIdOrNull(seasoning[0]) != null) // 조미료: 요리 상점 조수 NPC 전까지 임시로 상점에서 판매
                products.add(new Product(seasoning[0], seasoning[1], taggedItem(seasoning[0], seasoning[1]), Integer.parseInt(seasoning[2]), 0));
        return products;
    }
    /** A plugin-tagged mod product (e.g. 호박석 코어) for the resource nodes. */
    ItemStack taggedProduct(String key, String label) { return taggedItem(key, label); }
    ItemStack blueprintPaper() { return taggedItem("blueprint_paper", "도면"); }
    private List<Product> saleProducts() { return SELL_CATALOG.stream().map(s->new Product(s.key(),s.name(),
        s.tag()==null?new ItemStack(s.material()):taggedItem(s.tag(),s.name()),s.price(),s.limit())).toList(); }
    /** 재료 상점: farm / facility products the player sells (key, label, tag-or-null, vanilla/mod material id, price). */
    private List<Product> materialProducts() {
        List<Product> list = new ArrayList<>();
        list.add(new Product("m_straw", "짚", foodItem("straw"), 50, 1000));
        list.add(new Product("m_pet_carrot", "당근", foodItem("pet_carrot"), 50, 1000));
        Material cabbage = ModItems.byIdOrNull("cabbage");
        if (cabbage != null) list.add(new Product("m_cabbage", "양배추", new ItemStack(cabbage), 500, 1000));
        list.add(new Product("m_wheat", "밀", new ItemStack(Material.WHEAT), 200, 1000));
        list.add(new Product("m_potato", "감자", new ItemStack(Material.POTATO), 100, 1000));
        list.add(new Product("m_raw_salmon", "생 연어", foodItem("raw_salmon"), 100, 1000));
        return list;
    }
    static boolean saleMatches(ItemStack actual,ItemStack expected) {
        if(actual==null||expected==null||actual.getType()!=expected.getType())return false;
        NamespacedKey identity=NamespacedKey.fromString("pipilife:processed_product");
        String wanted=expected.hasItemMeta()?expected.getItemMeta().getPersistentDataContainer().getOrDefault(identity,PersistentDataType.STRING,""):"";
        String found=actual.hasItemMeta()?actual.getItemMeta().getPersistentDataContainer().getOrDefault(identity,PersistentDataType.STRING,""):"";
        return wanted.equals(found) || wanted.isEmpty() && actual.getType()==Material.IRON_INGOT && found.equals("ingot");
    }
    static int saleMaximum(int owned,int remaining) {return Math.max(0,Math.min(owned,remaining));}
    private String shopKey(Player player, Product item, boolean buy) {
        return user(player) + ".shop." + (buy ? "buy" : "sell") + "." + item.key();
    }

    private int remaining(Player player, Product item, boolean buy, long hour) {
        if (item.limit() == 0) return Integer.MAX_VALUE;
        var record = plugin.data().getLongList(shopKey(player, item, buy));
        return record.size() == 2 && record.getFirst() == hour
            ? Math.max(0, item.limit() - record.getLast().intValue()) : item.limit();
    }

    private int unitPrice(Product item, boolean buy) { return item.price(); }

    private int tradeCapacity(Player player, Product item, boolean buy) {
        if (!buy) return java.util.Arrays.stream(player.getInventory().getStorageContents()).filter(stack->saleMatches(stack,item.item())).mapToInt(ItemStack::getAmount).sum();
        int space = 0, max = item.item().getMaxStackSize();
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null || stack.getType().isAir()) space += max;
            else if (stack.isSimilar(item.item())) space += Math.max(0, max - stack.getAmount());
        }
        return space;
    }

    private boolean shop(Player player, String[] args) {
        if (!town(player)) return tell(player, "공동 마을에서 상점을 이용하세요.");
        if (args.length == 1) { openShop(player, true, null); return true; }
        if (args.length != 4 || !args[1].equals("quote")
            || !(player.getOpenInventory().getTopInventory().getHolder() instanceof ShopMenu menu) || menu.quote != null) return true;
        try {
            int index = Integer.parseInt(args[2]), count = Integer.parseInt(args[3]);
            if (index >= 0 && index < menu.products.size() && count > 0) {
                if (menu.buy && (long) unitPrice(menu.products.get(index), true) * count > gold(player).getScore()) {
                    returnTitle(player, "골드가 부족합니다.");
                    return true;
                }
                int maximum=saleMaximum(tradeCapacity(player,menu.products.get(index),menu.buy),remaining(player,menu.products.get(index),menu.buy,System.currentTimeMillis()/SHOP_HOUR));
                if(count>maximum){returnTitle(player,"현재 거래 가능한 수량은 "+maximum+"개입니다.");return true;}
                openShopTab(player, menu.tab, new Quote(index, count, System.currentTimeMillis() / SHOP_HOUR));
            }
        } catch (NumberFormatException ignored) {}
        return true;
    }

    private void openShop(Player player, boolean buy, Quote quote) { openShopTab(player, buy ? 0 : 1, quote); }

    private void openShopTab(Player player, int tab, Quote quote) {
        boolean buy = tab == 0;
        List<Product> products = buy ? buyProducts() : tab == 2 ? materialProducts() : saleProducts();
        ShopMenu menu = new ShopMenu(tab, products, quote);
        Inventory inventory = Bukkit.createInventory(menu, 54, SHOP_TITLE);
        menu.inventory = inventory;
        long hour = System.currentTimeMillis() / SHOP_HOUR;
        for (int i = 0; i < products.size(); i++) {
            Product item = products.get(i);
            int remaining = remaining(player, item, buy, hour);
            int quantity = saleMaximum(tradeCapacity(player,item,buy),remaining);
            if (buy) quantity = Math.min(quantity, Math.max(0, gold(player).getScore()) / unitPrice(item, true));
            inventory.setItem(i, plugin.displayIcon(item.item(), item.name(),
                remaining == Integer.MAX_VALUE ? "무제한" : remaining + "/" + item.limit(),
                Integer.toString(unitPrice(item, buy)), Integer.toString(quantity)));
        }
        inventory.setItem(45, plugin.statusIcon(tab == 0 ? Material.LIME_DYE : Material.GRAY_DYE, "구매"));
        inventory.setItem(46, plugin.statusIcon(tab == 1 ? Material.LIME_DYE : Material.GRAY_DYE, "광물 상점"));
        inventory.setItem(51, plugin.statusIcon(tab == 2 ? Material.LIME_DYE : Material.GRAY_DYE, "재료 상점"));
        inventory.setItem(47, plugin.statusIcon(Material.GOLD_INGOT, Integer.toString(gold(player).getScore())));
        if (quote != null) {
            Product product = products.get(quote.index());
            inventory.setItem(48, plugin.displayIcon(product.item(), product.name(),
                Integer.toString(quote.index()), Integer.toString(quote.count())));
            inventory.setItem(49, plugin.statusIcon(Material.EMERALD, "확인"));
            inventory.setItem(50, plugin.statusIcon(Material.BARRIER, "취소"));
        }
        player.openInventory(inventory);
    }

    private void returnTitle(Player player, String message) {
        if (player.getOpenInventory().getTopInventory().getType() == org.bukkit.event.inventory.InventoryType.CHEST)
            player.closeInventory();
        player.sendTitle("§c" + message, "", 0, 40, 10);
        player.sendMessage(message);
    }

    private void executeTrade(Player player, ShopMenu menu) {
        Quote quote = menu.quote;
        Product product = menu.products.get(quote.index());
        long hour = System.currentTimeMillis() / SHOP_HOUR;
        int remaining = remaining(player, product, menu.buy, hour);
        if (quote.hour() != hour || quote.count() > remaining) {
            returnTitle(player, "거래 제한을 다시 확인하세요.");
            return;
        }
        if (tradeCapacity(player, product, menu.buy) < quote.count()) {
            returnTitle(player, menu.buy ? "가방 공간이 부족합니다." : "판매할 아이템이 부족합니다.");
            return;
        }
        Score score = gold(player);
        int total = unitPrice(product, menu.buy) * quote.count();
        long balance = (long) score.getScore() + (menu.buy ? -total : total);
        if (balance < 0 || balance > Integer.MAX_VALUE) {
            returnTitle(player, balance < 0 ? "골드가 부족합니다." : "잔액 한도를 초과합니다.");
            return;
        }
        var bag = player.getInventory();
        var contents = bag.getStorageContents();
        int left = quote.count();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack stack = contents[i];
            boolean empty = stack == null || stack.getType().isAir();
            if (!(menu.buy && empty) && (empty || !(menu.buy ? stack.isSimilar(product.item()) : saleMatches(stack,product.item())))) continue;
            int current = empty ? 0 : stack.getAmount();
            int count = Math.min(left, menu.buy ? product.item().getMaxStackSize() - current : current);
            int next = current + (menu.buy ? count : -count);
            contents[i] = next == 0 ? null : (menu.buy ? product.item().clone() : stack.clone());
            if (contents[i] != null) contents[i].setAmount(next);
            left -= count;
        }
        if (left != 0) { returnTitle(player, "거래 수량을 다시 확인하세요."); return; }
        if (product.limit() > 0 && !plugin.store(shopKey(player, product, menu.buy),
            List.of(hour, (long) product.limit() - remaining + quote.count()))) {
            returnTitle(player, "거래 기록을 저장하지 못했습니다.");
            return;
        }
        bag.setStorageContents(contents);
        score.setScore((int) balance);
        tell(player, (menu.buy ? "구매" : "판매") + " 완료 · " + total + "골드");
        openShopTab(player, menu.tab, null);
    }

    @EventHandler(ignoreCancelled = true)
    public void shopNpc(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !npcs.matches(event.getRightClicked(), "merchant")) return;
        event.setCancelled(true);
        if (town(event.getPlayer()) && plugin.canTravel(event.getPlayer())) openShop(event.getPlayer(), true, null);
    }

    @EventHandler
    public void shopClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ShopMenu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !event.isLeftClick()) return;
        int slot = event.getRawSlot();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!town(player) || player.getOpenInventory().getTopInventory().getHolder() != menu) return;
            if (menu.quote != null) {
                if (slot == 49) executeTrade(player, menu);
                else if (slot == 50) openShopTab(player, menu.tab, null);
            } else if (slot == 45 || slot == 46 || slot == 51) openShopTab(player, slot == 45 ? 0 : slot == 46 ? 1 : 2, null);
        });
    }

    @EventHandler
    public void shopDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ShopMenu) event.setCancelled(true);
    }

    /** modId != null: a PipiFacilities mod item matched by material alone (any meta); level > 0 additionally requires that leveled-blueprint level. */
    private record Ingredient(Material material, String tag, String label, int count, String modId, int level) {
        Ingredient(Material material, String tag, String label, int count) { this(material, tag, label, count, null, 0); }
        boolean matches(ItemStack stack, TownSystems town) {
            if (modId != null) return stack != null && stack.getType() == mat() && (level == 0 || PipiLife.facilityLevel(stack) == level);
            return stack != null && stack.getType() == mat()
                && (tag == null ? !stack.hasItemMeta() : town.isProcessed(stack, tag));
        }
        /** Actual item material: the PipiFacilities mod item for tagged ingredients, the vanilla material otherwise. */
        Material mat() {
            if (modId != null) { Material mod = ModItems.byIdOrNull(modId); return mod == null ? Material.BARRIER : mod; }
            if (tag == null) return material;
            Material mod = ModItems.forTagOrNull(tag);
            return mod == null ? material : mod;
        }
    }

    private record Recipe(List<Ingredient> inputs, Facility station) {}

    /** The common 도면 item (blueprint_paper) used by every 청사진 recipe. */
    private static Ingredient blueprintIngredient(int count) {
        return new Ingredient(Material.PAPER, "blueprint_paper", "도면", count);
    }
    /** Mod-item ingredient (resolved lazily so a missing mod item never breaks class init). */
    private static Ingredient modItem(String id, String label, int count) { return new Ingredient(Material.BARRIER, null, label, count, id, 0); }
    private static Ingredient amber(int count) { return modItem("amber", "호박석", count); }
    private static Ingredient amberIngot(int count) { return modItem("amber_ingot", "호박석 주괴", count); }
    private static Ingredient amberCore(int count) { return modItem("amber_core", "호박석 코어", count); }
    private static Ingredient redwoodLog(int count) { return modItem("redwood_log", "홍서나무 원목", count); }
    private static Ingredient redwoodPlank(int count) { return modItem("redwood_plank", "홍서나무 판자", count); }
    private static Ingredient cabbageSeed(int count) { return modItem("cabbage_seed", "양배추 씨앗", count); }
    private static Ingredient cabbage(int count) { return modItem("cabbage", "양배추", count); }
    private static Ingredient vitalityCrystal(int count) { return modItem("vitality_crystal", "활력의 결정체", count); }
    private static Ingredient advancedBlueprint(int count) { return modItem("advanced_blueprint_paper", "중급 도면", count); }
    /** A leveled (PDC facility_level == level) lumber blueprint. */
    private static Ingredient leveledLumberBlueprint(int level, int count) {
        return new Ingredient(Material.BARRIER, null, level + "레벨 벌목장 청사진", count, "lumber_blueprint", level);
    }
    private static Ingredient steel(int count) { return tagged(Material.IRON_NUGGET, "plate", "강철", count); }
    private static Ingredient planks(int count) { return tagged(Material.OAK_PLANKS, "plank", "판자", count); }
    private static Ingredient reinforced(int count) { return tagged(Material.OAK_PLANKS, "reinforced_plank", "덧댄 판자", count); }
    private static Ingredient coated(int count) { return tagged(Material.OAK_PLANKS, "coated_plank", "코팅된 판자", count); }
    private static Ingredient blueDiamond(int count) { return tagged(Material.DIAMOND, "blue_diamond", "블루 다이아몬드", count); }

    /** Seconds per crafted item at the crafting station: wood 2, iron 5, diamond 10, blueprint 600. */
    static long craftMillis(String key, int petLevel) {
        return Math.max(1000L, Math.round(craftSeconds(key) * PipiLife.levelMultiplier(petLevel) * 1000.0));
    }

    static int craftSeconds(String key) {
        Recipe recipe = RECIPES.get(key);
        if (recipe == null) return 2;
        if (recipe.station() != null) return 600;
        int tier = 0;
        for (Ingredient ingredient : recipe.inputs()) {
            if (ingredient.modId() != null && ingredient.modId().startsWith("amber")) { tier = 3; continue; }
            int t = switch (ingredient.material()) {
                case DIAMOND -> 2;
                case IRON_INGOT, RAW_IRON, IRON_NUGGET -> 1;
                default -> "reinforced_plank".equals(ingredient.tag()) ? 1 : 0;
            };
            if ("coated_plank".equals(ingredient.tag())) t = 2;
            tier = Math.max(tier, t);
        }
        return tier == 3 ? 20 : tier == 2 ? 10 : tier == 1 ? 5 : 2;
    }

    /** Upgrade price of a producer facility (or the pet storage); null keeps the legacy gold-only price. */
    private record UpgradeCost(List<Ingredient> items, int gold) {}
    private static UpgradeCost upgradeCostOf(Facility facility, int level) {
        if (facility == null) return null;
        return switch (facility) {
            case LUMBER -> new UpgradeCost(List.of(planks(40)), 10_000);
            case MINE -> new UpgradeCost(List.of(steel(40), planks(40)), 20_000);
            case DIAMOND_MINE -> new UpgradeCost(List.of(blueDiamond(40), reinforced(40)), 150_000);
            case FARM -> new UpgradeCost(List.of(raw(Material.WHEAT_SEEDS, "씨앗", 60), steel(20), planks(40)), 20_000);
            case AMBER_QUARRY -> new UpgradeCost(List.of(amber(45), redwoodLog(90)), 750_000);
            case CABBAGE_FARM -> new UpgradeCost(List.of(cabbageSeed(60), amberIngot(40), redwoodLog(120)), 750_000);
            case REDWOOD_LUMBER -> new UpgradeCost(List.of(amberIngot(30), redwoodLog(120)), 750_000);
            case PET_STORAGE -> level <= 1
                ? new UpgradeCost(List.of(raw(Material.IRON_INGOT, "철 주괴", 30), steel(20), raw(Material.OAK_LOG, "참나무 원목", 60)), 40_000)
                : new UpgradeCost(List.of(steel(40), coated(30), reinforced(50)), 400_000);
            default -> null;
        };
    }

    /** Lore lines (two prices per line) describing the next upgrade step. */
    static List<String> upgradeLines(Facility facility, int level) {
        UpgradeCost cost = upgradeCostOf(facility, level);
        if (facility == Facility.PET_STORAGE && level >= 3) return List.of("최대 레벨");
        List<String> tokens = new ArrayList<>();
        if (cost == null) tokens.add((100 * level) + "골드");
        else {
            for (Ingredient ingredient : cost.items()) tokens.add(ingredient.label() + " " + ingredient.count());
            if (cost.gold() > 0) tokens.add(cost.gold() + "골드");
        }
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i += 2)
            lines.add(String.join(" · ", tokens.subList(i, Math.min(tokens.size(), i + 2))));
        return lines;
    }

    private static Ingredient raw(Material material, String label, int count) {
        return new Ingredient(material, null, label, count);
    }

    private static Ingredient tagged(Material material, String tag, String label, int count) {
        return new Ingredient(material, tag, label, count);
    }

    private static Recipe mine(Facility facility, String tag, String label) {
        return new Recipe(List.of(tagged(Material.AMETHYST_SHARD, tag, label, 3),
            tagged(Material.IRON_NUGGET, "plate", "강철", 4), blueprintIngredient(1)), facility);
    }

    private static final Map<String, Recipe> RECIPES = Map.ofEntries(
        Map.entry("amber_ingot", new Recipe(List.of(amber(4)), null)),
        Map.entry("redwood_plank", new Recipe(List.of(redwoodLog(4)), null)),
        Map.entry("amber_club", new Recipe(List.of(amberIngot(4)), null)),
        Map.entry("cabbage_farm", new Recipe(List.of(advancedBlueprint(1), cabbageSeed(32), amberIngot(20), redwoodLog(80)), Facility.CABBAGE_FARM)),
        Map.entry("ranch", new Recipe(List.of(amberIngot(15), redwoodLog(80), advancedBlueprint(1)), Facility.RANCH)),
        Map.entry("pet_storage", new Recipe(List.of(raw(Material.IRON_INGOT, "철 주괴", 20), raw(Material.OAK_LOG, "참나무 원목", 30)), Facility.PET_STORAGE)),
        Map.entry("amber_quarry", new Recipe(List.of(amberCore(5), redwoodLog(60), amberIngot(30), advancedBlueprint(1)), Facility.AMBER_QUARRY)),
        Map.entry("cooking_table", new Recipe(List.of(advancedBlueprint(1), amberIngot(15), redwoodLog(80)), Facility.COOKING_TABLE)),
        Map.entry("amber_trap", new Recipe(List.of(amber(20), raw(Material.DIAMOND, "다이아몬드", 10), raw(Material.IRON_INGOT, "철 주괴", 20), redwoodLog(20)), null)),
        Map.entry("redwood_lumber", new Recipe(List.of(leveledLumberBlueprint(3, 1), amberIngot(20), redwoodLog(80)), Facility.REDWOOD_LUMBER)),
        Map.entry("vitality_crystal", new Recipe(List.of(raw(Material.IRON_INGOT, "철 주괴", 100), raw(Material.DIAMOND, "다이아몬드", 50), amber(20)), null)),
        Map.entry("research_desk", new Recipe(List.of(raw(Material.LECTERN, "독서대", 1), raw(Material.BOOK, "책", 2), blueprintIngredient(1)), Facility.RESEARCH)),
        Map.entry("reinforced_plank",new Recipe(List.of(steel(1),planks(3)),null)),
        Map.entry("coated_plank",new Recipe(List.of(reinforced(2),blueDiamond(2)),null)),
        Map.entry("blue_diamond",new Recipe(List.of(raw(Material.DIAMOND,"다이아몬드",2)),null)),
        Map.entry("diamondclub",new Recipe(List.of(blueDiamond(4)),null)),
        Map.entry("diamondtrap",new Recipe(List.of(raw(Material.DIAMOND,"다이아몬드",5),raw(Material.IRON_INGOT,"철 주괴",10),raw(Material.OAK_LOG,"참나무 원목",12)),null)),
        Map.entry("pet_market",new Recipe(List.of(blueDiamond(2),reinforced(10),blueprintIngredient(1)),Facility.PET_MARKET)),
        Map.entry("basictrap", new Recipe(List.of(raw(Material.OAK_LOG, "참나무 원목", 5)), null)),
        Map.entry("club", new Recipe(List.of(raw(Material.OAK_LOG, "참나무 원목", 4)), null)),
        Map.entry("ironclub", new Recipe(List.of(steel(4)), null)),
        Map.entry("hammer", new Recipe(List.of(planks(10), steel(20)), null)),
        Map.entry("irontrap", new Recipe(List.of(raw(Material.IRON_INGOT, "철 주괴", 5), raw(Material.OAK_LOG, "참나무 원목", 6)), null)),
        Map.entry("plank", new Recipe(List.of(raw(Material.OAK_LOG, "참나무 원목", 3)), null)),
        Map.entry("ingot", new Recipe(List.of(raw(Material.RAW_IRON, "철 원석", 1)), null)),
        Map.entry("plate", new Recipe(List.of(raw(Material.IRON_INGOT, "철 주괴", 4)), null)),
        Map.entry("diamond", new Recipe(List.of(tagged(Material.IRON_NUGGET, "plate", "강철", 1),
            raw(Material.DIAMOND, "다이아몬드", 1)), null)),
        Map.entry("farm", new Recipe(List.of(raw(Material.WHEAT_SEEDS, "씨앗", 32), steel(10), planks(20), blueprintIngredient(1)), Facility.FARM)),
        Map.entry("carrotfarm", new Recipe(List.of(raw(Material.CARROT, "당근", 12), tagged(Material.IRON_NUGGET, "plate", "강철", 2), blueprintIngredient(1)), Facility.CARROT_FARM)),
        Map.entry("potatofarm", new Recipe(List.of(raw(Material.POTATO, "감자", 12), tagged(Material.IRON_NUGGET, "plate", "강철", 4), blueprintIngredient(1)), Facility.POTATO_FARM)),
        Map.entry("lumber", new Recipe(List.of(raw(Material.OAK_LOG, "참나무 원목", 2),
            raw(Material.LOOM, "베틀", 1), blueprintIngredient(1)), Facility.LUMBER)),
        Map.entry("mine", new Recipe(List.of(tagged(Material.AMETHYST_SHARD, "iron_core", "철 코어", 5), steel(15), planks(20), blueprintIngredient(1)), Facility.MINE)),
        Map.entry("diamondmine", new Recipe(List.of(tagged(Material.AMETHYST_SHARD, "diamond_core", "다이아몬드 코어", 5), blueDiamond(20), reinforced(15), blueprintIngredient(1)), Facility.DIAMOND_MINE)),
        Map.entry("craft", new Recipe(List.of(planks(5), blueprintIngredient(1)), Facility.CRAFT)),
        Map.entry("feeder", new Recipe(List.of(coated(10), blueprintIngredient(1)), Facility.FEEDER)));

    private ItemStack recipeOutput(String key) {
        Recipe recipe = RECIPES.get(key);
        if (recipe.station() != null) return plugin.stationItem(recipe.station());
        return switch (key) {
            case "reinforced_plank" -> taggedItem(key,"덧댄 판자");
            case "coated_plank" -> taggedItem(key,"코팅된 판자");
            case "blue_diamond" -> taggedItem(key,"블루 다이아몬드");
            case "diamondclub" -> taggedItem(key,"다이아몬드 방망이");
            case "amber_ingot" -> taggedItem(key, "호박석 주괴");
            case "redwood_plank" -> taggedItem(key, "홍서나무 판자");
            case "amber_club" -> taggedItem(key, "호박석 몽둥이");
            case "diamondtrap" -> plugin.trapItem(2);
            case "amber_trap" -> plugin.trapItem(3);
            case "vitality_crystal" -> taggedItem(key, "활력의 결정체");
            case "basictrap" -> plugin.trapItem(0);
            case "club" -> taggedItem(key,"몽둥이");
            case "ironclub" -> taggedItem(key,"철몽둥이");
            case "hammer" -> taggedItem(key,"건설망치");
            case "irontrap" -> plugin.trapItem(1);
            case "plate" -> taggedItem(key, "강철");
            case "diamond" -> taggedItem(key, "다이아몬드");
            default -> saleItem(key);
        };
    }

    private int ingredientCount(Player player, Ingredient ingredient) { return ingredientCount(player, ingredient, false); }

    private void consume(Player player, Ingredient ingredient) { consume(player, ingredient, false); }

    /** Real chest / trapped chest / barrel inventories inside the plot the player owns (empty without a plot). */
    private List<Inventory> residenceChests(Player player) {
        String plot = plugin.ownedPlot(player);
        if (plot == null) return List.of();
        World world = plugin.openWorld(plugin.villageName());
        if (world == null) return List.of();
        int px = plugin.data().getInt("plots." + plot + ".x"), pz = plugin.data().getInt("plots." + plot + ".z");
        List<Inventory> result = new ArrayList<>();
        for (int cx = px >> 4; cx <= (px + 43) >> 4; cx++)
            for (int cz = pz >> 4; cz <= (pz + 43) >> 4; cz++)
                for (var state : world.getChunkAt(cx, cz).getTileEntities()) {
                    Block block = state.getBlock();
                    if (block.getX() < px || block.getX() >= px + 44 || block.getZ() < pz || block.getZ() >= pz + 44) continue;
                    Material type = block.getType();
                    if (type != Material.CHEST && type != Material.TRAPPED_CHEST && type != Material.BARREL) continue;
                    if (plugin.facilityAt(block) != null || plugin.legacyStation(block, plot)) continue;
                    if (state instanceof org.bukkit.block.Container container) result.add(container.getInventory() instanceof org.bukkit.inventory.DoubleChestInventory
                        ? ((org.bukkit.block.Chest) container).getBlockInventory() : container.getInventory());
                }
        return result;
    }

    /** Bag count, plus residence chests when {@code chests} (crafting only). */
    private int ingredientCount(Player player, Ingredient ingredient, boolean chests) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getStorageContents())
            if (ingredient.matches(stack, this)) count += stack.getAmount();
        if (chests) for (Inventory box : residenceChests(player))
            for (ItemStack stack : box.getContents())
                if (ingredient.matches(stack, this)) count += stack.getAmount();
        return count;
    }

    /** Takes from the bag first, then (when {@code chests}) from the residence chests. */
    private void consume(Player player, Ingredient ingredient, boolean chests) {
        var inventory = player.getInventory();
        int left = ingredient.count();
        for (int slot = 0; slot < inventory.getStorageContents().length && left > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!ingredient.matches(stack, this)) continue;
            int take = Math.min(left, stack.getAmount());
            left -= take;
            if (take == stack.getAmount()) inventory.setItem(slot, null);
            else stack.setAmount(stack.getAmount() - take);
        }
        if (chests && left > 0) for (Inventory box : residenceChests(player)) {
            for (int slot = 0; slot < box.getSize() && left > 0; slot++) {
                ItemStack stack = box.getItem(slot);
                if (!ingredient.matches(stack, this)) continue;
                int take = Math.min(left, stack.getAmount());
                left -= take;
                if (take == stack.getAmount()) box.setItem(slot, null);
                else stack.setAmount(stack.getAmount() - take);
            }
            if (left <= 0) break;
        }
    }

    ItemStack recipeIcon(Player player, String key) {
        ItemStack icon = recipeOutput(key);
        var meta = icon.getItemMeta();
        List<String> lore = new ArrayList<>();
        for (Ingredient ingredient : RECIPES.get(key).inputs()) {
            int have = ingredientCount(player, ingredient, true);
            lore.add((have >= ingredient.count() ? "§a" : "§c") + ingredient.label()
                + " " + have + "/" + ingredient.count());
        }
        lore.add("§7거주지 상자 포함");
        lore.add("제작 시간 " + PipiLife.formatSeconds(craftMillis(key, Math.max(1, plugin.craftLevel(player)))) + "초");
        lore.add(learned(player, key) ? "§7클릭하여 펫에게 제작 주문" : "§c연구소에서 해금 필요");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin,"craft_recipe"),PersistentDataType.STRING,key);
        icon.setItemMeta(meta);
        return icon;
    }

    private String processedLabel(String key) {
        return switch (key) {
            case "plank" -> "판자";
            case "ingot" -> "철 주괴";
            default -> key;
        };
    }

    private ItemStack saleItem(String key) {
        Material material = GOODS.get(key).item();
        if (!key.equals("plank")) return new ItemStack(material); // incl. ingot: plain vanilla IRON_INGOT
        return taggedItem(key, processedLabel(key));
    }

    /** Custom item built from its PipiFacilities mod material (no vanilla base, no CustomModelData). */
    private ItemStack taggedItem(String key, String label) {
        Material mod = ModItems.forTag(key);
        if (mod == null) throw new IllegalArgumentException("모드 아이템이 아닌 태그: " + key);
        return taggedItem(mod, key, label);
    }

    boolean isRemovedItem(ItemStack item) { return ModItems.isRemoved(item, processed); }

    private ItemStack taggedItem(Material material, String key, String label) {
        String lore = key.equals("blueprint_paper") ? "청사진 제작에 쓰이는 공통 도면"
            : petFood().containsKey(key) ? "펫 전용 먹이 · 포만감 +" + petFood().get(key) : "삐삐서버 전용 재료";
        ItemStack item = plugin.statusIcon(material, label, lore);
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(processed, PersistentDataType.STRING, key);
        item.setItemMeta(meta);
        return item;
    }

    /** Converts old vanilla-based custom items (and CustomModelData leftovers) in the stack to mod items; null when unchanged. */
    ItemStack convertOld(ItemStack old) {
        return ModItems.convert(old, processed, gearKey("repair_kit"), new NamespacedKey(plugin, "capture_trap"));
    }

    void convertOld(Inventory inventory) {
        ModItems.convertInventory(inventory, processed, gearKey("repair_kit"), new NamespacedKey(plugin, "capture_trap"));
    }

    void convertOld(Player player) {
        ModItems.convertPlayer(player, processed, gearKey("repair_kit"), new NamespacedKey(plugin, "capture_trap"));
    }
    boolean isProcessed(ItemStack item, String key) {
        return item.hasItemMeta() && key.equals(item.getItemMeta().getPersistentDataContainer()
            .get(processed, PersistentDataType.STRING));
    }

    static int completedCrafts(long now, long ready, long interval, int count) {
        if (now >= ready) return count;
        if (interval == 0) return 0;
        return (int) Math.clamp(count - 1L - (ready - now - 1) / interval, 0, count);
    }

    record CraftJob(long ready, int count, long interval, int claimed) {
        static CraftJob read(List<String> job) {
            if (job.size() < 2 || job.size() > 7 || job.size() == 5) throw new IllegalArgumentException();
            CraftJob value = new CraftJob(Long.parseLong(job.get(1)),
                job.size() >= 4 ? Integer.parseInt(job.get(3)) : 1,
                job.size() >= 6 ? Long.parseLong(job.get(4)) : 0,
                job.size() >= 6 ? Integer.parseInt(job.get(5)) : 0);
            if (value.count < 1 || value.count > MAX_CRAFT_BATCH || value.claimed < 0
                || value.claimed >= value.count || value.interval < 0) throw new IllegalArgumentException();
            return value;
        }
    }

    private static long pausedAt(List<String> job) {
        return job.size() == 7 ? Long.parseLong(job.get(6)) : 0;
    }

    void checkHungryCraft(Player player, long now) {
        String key = plugin.processKey(player);
        long waiting = plugin.data().getLong(key + "-hungry");
        if (waiting <= 0) return;
        List<String> job = plugin.data().getStringList(key);
        if (job.isEmpty()) { plugin.store(key + "-hungry", null); return; }
        CraftJob progress = CraftJob.read(job);
        if (pausedAt(job) > 0) return;
        if (plugin.craftCanAfford(player, progress.count())) {
            if (plugin.reserveCraftFood(player, progress.count(), List.of(job.get(0), Long.toString(progress.ready() + Math.max(0, now - waiting)),
                job.get(2), job.get(3), job.get(4), job.get(5), "0"))) plugin.refreshCraft(player);
        } else if (now - waiting >= 5000) {
            if (plugin.store(key, List.of(job.get(0), job.get(1), job.get(2), job.get(3), job.get(4), job.get(5),
                Long.toString(waiting + 5000)))) {
                player.sendMessage("제작 중단 · 펫 먹이가 필요합니다.");
                plugin.refreshCraft(player);
            }
        }
    }

    void toggleCraft(Player player) {
        String key = plugin.processKey(player);
        List<String> job = plugin.data().getStringList(key);
        if (job.size() < 3) return;
        try {
            CraftJob progress = CraftJob.read(job);
            long paused = pausedAt(job), now = System.currentTimeMillis();
            if (paused == 0 && now >= progress.ready()) return;
            long ready = paused == 0 ? progress.ready() : progress.ready() + Math.max(0, now - paused);
            if (paused > 0 && plugin.data().getLong(key + "-hungry") > 0) plugin.data().set(key + "-hungry", now);
            plugin.store(key, List.of(job.get(0), Long.toString(ready), job.get(2), Integer.toString(progress.count()),
                Long.toString(progress.interval()), Integer.toString(progress.claimed()), Long.toString(paused == 0 ? now : 0)));
        } catch (IllegalArgumentException error) {
            tell(player, "저장된 제작 주문이 잘못됐습니다.");
        }
    }

    /** Upgrade a producer facility / the pet storage by one level, charging items + gold from {@link #upgradeCostOf}. */
    boolean upgradeFacility(Player player, Facility facility, String key, int level) {
        UpgradeCost cost = upgradeCostOf(facility, level);
        if (cost == null) return upgradeLumber(player, key, level);
        Score score = gold(player);
        for (Ingredient ingredient : cost.items())
            if (ingredientCount(player, ingredient) < ingredient.count())
                { returnTitle(player, ingredient.label() + " " + ingredient.count() + "개가 필요합니다."); return false; }
        if (score.getScore() < cost.gold()) { returnTitle(player, cost.gold() + "골드가 필요합니다."); return false; }
        if (!plugin.store(key, level + 1)) return false;
        for (Ingredient ingredient : cost.items()) consume(player, ingredient);
        if (cost.gold() > 0) score.setScore(score.getScore() - cost.gold());
        return true;
    }

    // ---- facility upgrade confirmation window ("삐삐서버 · 업그레이드") ----
    private static final String FACILITY_UPGRADE_TITLE = "삐삐서버 · 업그레이드";
    private record FacilityUpgrade(Facility facility, String key, int level, java.util.function.IntSupplier current,
                                   Runnable back, Inventory[] view) implements InventoryHolder {
        @Override public Inventory getInventory() { return view[0]; }
    }

    private static List<String> unlockLines(Facility facility, int level) {
        if (facility == Facility.PET_STORAGE)
            return List.of("창고 페이지 +2", "페이지 " + (1 + 2 * (level - 1)) + " → " + (1 + 2 * level));
        return List.of("펫 배치 칸 " + PipiLife.facilitySlots(level) + " → " + PipiLife.facilitySlots(level + 1));
    }

    private ItemStack upgradeMaterialIcon(Player player, Ingredient ingredient) {
        ItemStack stack;
        try { stack = smeltStack(ingredient, 1); }
        catch (RuntimeException error) { stack = new ItemStack(ingredient.material() == Material.BARRIER ? Material.PAPER : ingredient.material()); }
        return plugin.displayIcon(stack, ingredient.label(), ingredientCount(player, ingredient) + "/" + ingredient.count());
    }

    /** Opens the upgrade confirmation window; {@code back} re-opens the facility's previous screen. */
    void openFacilityUpgrade(Player player, Facility facility, String key, int level,
                             java.util.function.IntSupplier current, Runnable back) {
        UpgradeCost cost = upgradeCostOf(facility, level);
        List<Ingredient> items = cost == null ? List.of() : cost.items();
        int goldCost = cost == null ? 100 * level : cost.gold();
        var menu = new FacilityUpgrade(facility, key, level, current, back, new Inventory[1]);
        Inventory view = Bukkit.createInventory(menu, 54, FACILITY_UPGRADE_TITLE);
        menu.view()[0] = view;
        Material blockIcon;
        try { blockIcon = facility.blueprintMaterial(); } catch (RuntimeException error) { blockIcon = facility.block; }
        List<String> unlock = new ArrayList<>(unlockLines(facility, level));
        view.setItem(4, plugin.statusIcon(blockIcon, facility.label + " Lv." + level + " → Lv." + (level + 1), unlock.toArray(String[]::new)));
        boolean enough = true;
        int n = Math.min(items.size(), 4);
        for (int i = 0; i < n; i++) {
            Ingredient ingredient = items.get(i);
            if (ingredientCount(player, ingredient) < ingredient.count()) enough = false;
            view.setItem(22 - (n - 1) + 2 * i, upgradeMaterialIcon(player, ingredient));
        }
        long have = gold(player).getScore();
        if (have < goldCost) enough = false;
        view.setItem(31, plugin.statusIcon(Material.GOLD_INGOT, goldCost + " 골드", "보유 " + have + " 골드"));
        view.setItem(39, enough ? plugin.statusIcon(Material.LIME_DYE, "업그레이드") : plugin.statusIcon(Material.GRAY_DYE, "재료 부족"));
        view.setItem(41, plugin.statusIcon(Material.BARRIER, "취소"));
        player.openInventory(view);
    }

    @EventHandler
    public void facilityUpgradeDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof FacilityUpgrade) event.setCancelled(true);
    }

    @EventHandler
    public void facilityUpgradeClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof FacilityUpgrade menu)) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (!(event.getWhoClicked() instanceof Player player) || !event.isLeftClick() || slot < 0 || slot >= 54) return;
        ItemStack clicked = menu.getInventory().getItem(slot);
        if (clicked == null || slot != 39 && slot != 41) return;
        boolean confirm = slot == 39 && clicked.getType() == Material.LIME_DYE;
        if (slot == 39 && !confirm) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.getOpenInventory().getTopInventory().getHolder() != menu) return;
            if (confirm && menu.current().getAsInt() == menu.level())
                upgradeFacility(player, menu.facility(), menu.key(), menu.level());
            menu.back().run();
        });
    }

    boolean upgradeLumber(Player player, String key, int level) {
        Score score = gold(player);
        int cost = 100 * level;
        if (score.getScore() < cost) { returnTitle(player, cost + "골드가 필요합니다."); return false; }
        if (!plugin.store(key, level + 1)) return false;
        score.setScore(score.getScore() - cost);
        return true;
    }

    ItemStack craftJobIcon(Player player) {
        List<String> job = plugin.data().getStringList(plugin.processKey(player));
        if (job.size() < 2 || !RECIPES.containsKey(job.getFirst())) return null;
        try {
            CraftJob progress = CraftJob.read(job);
            ItemStack icon = recipeOutput(job.getFirst());
            var meta = icon.getItemMeta();
            meta.setLore(List.of(progress.ready() + "," + progress.count() + "," + progress.interval() + "," + progress.claimed() + "," + pausedAt(job)));
            icon.setItemMeta(meta);
            return icon;
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    static final int MAX_CRAFT_BATCH = 64;

    boolean collectCraft(Player player, boolean all, boolean cursor) {
        String key = plugin.processKey(player);
        List<String> job = plugin.data().getStringList(key);
        if (job.isEmpty()) return tell(player, "진행 중인 가공이 없습니다.");
        if (job.size() >= 3 && !plugin.atCraftStation(player)) return tell(player, "내 기본 작업대 가까이에서 수령하세요.");
        if (!RECIPES.containsKey(job.get(0)) || job.size() == 2 && !GOODS.containsKey(job.get(0))) return tell(player, "저장된 가공 주문이 잘못됐습니다.");
        CraftJob progress;
        try { progress = CraftJob.read(job); }
        catch (IllegalArgumentException ex) { return tell(player, "저장된 제작 주문이 잘못됐습니다."); }
        if (plugin.data().getLong(key + "-hungry") > 0) return tell(player, "펫이 먹이를 기다리고 있습니다.");
        int completed = completedCrafts(pausedAt(job) > 0 ? pausedAt(job) : System.currentTimeMillis(), progress.ready(), progress.interval(), progress.count());
        if (completed <= progress.claimed()) return tell(player, "아직 수령할 수 있는 제작품이 없습니다.");
        ItemStack output = job.size() >= 3 ? recipeOutput(job.get(0)) : saleItem(job.get(0));
        output.setAmount(job.get(0).equals("plank") ? 4 : 1);
        var inventory = player.getInventory();
        int unit = output.getAmount();
        int available = completed - progress.claimed();
        int take = 1;
        ItemStack[] storage = inventory.getStorageContents();
        if (cursor) {
            ItemStack carried = player.getItemOnCursor();
            if (!carried.getType().isAir() && (!carried.isSimilar(output)
                || carried.getAmount() + unit > output.getMaxStackSize()))
                return tell(player, "커서에 든 아이템을 먼저 내려놓으세요.");
            if (!carried.getType().isAir()) output.setAmount(carried.getAmount() + unit);
        } else {
            int capacity = 0;
            int limit = output.getMaxStackSize();
            for (ItemStack stack : storage) {
                if (stack == null || stack.getType().isAir()) capacity += limit;
                else if (stack.isSimilar(output)) capacity += Math.max(0, limit - stack.getAmount());
            }
            take = Math.min(all ? available : 1, capacity / unit);
            if (take == 0) return tell(player, "가방에 빈 칸이 더 필요합니다.");
        }
        int claimed = progress.claimed() + take;
        Object next = claimed == progress.count() ? null : List.of(job.get(0), job.get(1), job.get(2),
            Integer.toString(progress.count()), Long.toString(progress.interval()), Integer.toString(claimed), Long.toString(pausedAt(job)));
        if (!plugin.store(key, next)) return tell(player, "가공 상태를 저장하지 못했습니다.");
        if (cursor) player.setItemOnCursor(output);
        else {
            int left = take * unit;
            while (left > 0) {
                ItemStack stack = output.clone();
                stack.setAmount(Math.min(left, stack.getMaxStackSize()));
                inventory.addItem(stack);
                left -= Math.min(left, output.getMaxStackSize());
            }
        }
        return true;
    }

    private boolean process(Player player, String[] args) {
        if (!plugin.owns(player, plugin.plotAt(player.getLocation()))) return tell(player, "내 거주지에서 가공하세요.");
        String key = plugin.processKey(player);
        List<String> job = plugin.data().getStringList(key);
        if (args.length == 2 && args[1].equals("collect")) return collectCraft(player, false, false);
        String recipe = args.length == 2 ? args[1] : "";
        int count = 1;
        if (args.length == 4 && args[1].equals("batch")) {
            try {
                recipe = catalogPage(player, plugin.catalogPage(player)).getOrDefault(Integer.parseInt(args[2]), "");
                count = Integer.parseInt(args[3]);
            } catch (NumberFormatException ex) { return tell(player, "제작 수량이 잘못됐습니다."); }
        }
        if (count < 1 || count > MAX_CRAFT_BATCH) return tell(player, "제작 수량은 1~64회입니다.");
        if (!RECIPES.containsKey(recipe) || !CATALOG.contains(recipe))
            return tell(player, "기본 작업대에서 레시피를 선택하거나 /pipilife process collect 를 사용하세요.");
        if (!plugin.atCraftStation(player)) return tell(player, "내 기본 작업대에서 펫에게 가공을 주문하세요.");
        int level = plugin.craftLevel(player);
        if (level == 0) return tell(player, "가공 적성이 있는 펫을 먼저 배치하세요.");
        if (!job.isEmpty()) return tell(player, "기존 주문부터 수령하세요.");
        if (!learned(player, recipe)) return tell(player, "마을 연구소에서 먼저 레시피를 해금하세요.");
        List<Ingredient> inputs = new ArrayList<>();
        for (Ingredient unit : RECIPES.get(recipe).inputs())
            inputs.add(new Ingredient(unit.material(), unit.tag(), unit.label(), unit.count() * count));
        for (Ingredient ingredient : inputs)
            if (ingredientCount(player, ingredient, true) < ingredient.count())
                { returnTitle(player, ingredient.label() + " " + ingredient.count() + "개가 필요합니다."); return true; }
        long each = craftMillis(recipe, level);
        long duration = each * count;
        if (!plugin.startCraft(player, recipe, System.currentTimeMillis() + duration, count, each))
            return tell(player, "배치된 펫이 일할 수 없거나 먹이가 부족합니다. 가공 1회당 포만감이 펫 적성 Lv.1 1, Lv.2 3, Lv.3 5 필요합니다.");
        for (Ingredient ingredient : inputs) consume(player, ingredient, true);
        return tell(player, "펫이 가공을 시작했습니다. " + (duration >= 120_000 ? duration / 60_000 + "분" : PipiLife.formatSeconds(duration) + "초") + " 뒤 수령하세요.");
    }

    /** Pet food table (tag -> 포만감). Read from config.yml `pet-food`; falls back to the built-in defaults when absent. */
    private static final Map<String, Integer> DEFAULT_PET_FOOD = Map.of("straw", 10, "pet_carrot", 20, "raw_salmon", 15);

    Map<String, Integer> petFood() {
        var section = plugin.getConfig().getConfigurationSection("pet-food");
        if (section == null) return DEFAULT_PET_FOOD;
        Map<String, Integer> food = new java.util.LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            int value = section.getInt(key, 0);
            if (value > 0) food.put(key, value);
        }
        return food;
    }

    /** 포만감 of a pet food item (mod item carrying its PDC tag); 0 for everything else. */
    int nutrition(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return 0;
        String tag = item.getItemMeta().getPersistentDataContainer().get(processed, PersistentDataType.STRING);
        if (tag == null) return 0;
        if (plugin.cooking != null && plugin.cooking.isDish(tag)) return plugin.cooking.dishMatches(item, tag) ? plugin.cooking.petFood(tag) : 0;
        Material mod = ModItems.forTag(tag);
        return mod != null && item.getType() == mod ? petFood().getOrDefault(tag, 0) : 0;
    }

    int storageNutrition(ItemStack item) { return nutrition(item); }

    /** Pet food item for a food tag (straw / pet_carrot). */
    ItemStack foodItem(String tag) {
        return taggedItem(tag, foodLabel(tag));
    }

    /** Lore listing the accepted pet foods, e.g. "짚 +10, 당근 +20". */
    String foodSummary() {
        List<String> parts = new ArrayList<>();
        petFood().forEach((tag, value) -> parts.add(foodLabel(tag) + " +" + value));
        parts.add("요리");
        return String.join(", ", parts);
    }

    private static String foodLabel(String tag) {
        return switch (tag) { case "straw" -> "짚"; case "pet_carrot" -> "당근"; case "raw_salmon" -> "생 연어"; default -> tag; };
    }

    private boolean wild(World world) {
        return world.getName().equals(plugin.getConfig().getString("wild-world", "world"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void starterFishing(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH
            && event.getCaught() instanceof org.bukkit.entity.Item item)
            item.setItemStack(taggedItem("raw_salmon", "생 연어")); // every catch, every world: exactly 1 생 연어 (XP kept)
    }

    /** Base drop chance (%) shared by every core. */
    static final int CORE_CHANCE = 3;
    private record OreCore(String key, String label, int chance) {}

    private OreCore oreCore(Block block) {
        if (!block.getWorld().getName().equals(plugin.getConfig().getString("wild-world", "world"))) return null;
        return switch (block.getType()) {
            case IRON_ORE, DEEPSLATE_IRON_ORE -> new OreCore("iron_core", "철 코어", CORE_CHANCE);
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> new OreCore("diamond_core", "다이아몬드 코어", CORE_CHANCE);
            default -> null;
        };
    }

    private NamespacedKey placedOreKey(Block block) {
        return new NamespacedKey(plugin, "placed_ore/" + (block.getX() & 15) + "/" + block.getY() + "/" + (block.getZ() & 15));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void recordPlacedOre(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (oreCore(block) != null)
            block.getChunk().getPersistentDataContainer().set(placedOreKey(block), PersistentDataType.BYTE, (byte) 1);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void protectOreTracking(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> oreCore(block) != null)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void protectOreTracking(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> oreCore(block) != null)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void dropOreCore(BlockBreakEvent event) {
        Block block = event.getBlock();
        OreCore core = oreCore(block);
        if (core == null) return;
        var marks = block.getChunk().getPersistentDataContainer();
        NamespacedKey key = placedOreKey(block);
        if (marks.has(key, PersistentDataType.BYTE)) {
            marks.remove(key);
            return;
        }
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (player.getGameMode() != GameMode.SURVIVAL || !event.isDropItems()
            || tool.containsEnchantment(Enchantment.SILK_TOUCH) || block.getDrops(tool, player).isEmpty()) return;
        var random = java.util.concurrent.ThreadLocalRandom.current();
        double bonus = Enchants.pickaxe(tool) ? Enchants.sum(tool, "ore") : 0;
        if (bonus > 0) {
            int extra = (int) bonus + (random.nextDouble() < bonus % 1 ? 1 : 0);
            if (extra > 0) {
                ItemStack drop = block.getDrops(tool, player).iterator().next().clone();
                drop.setAmount(extra);
                block.getWorld().dropItemNaturally(block.getLocation().add(.5, .5, .5), drop);
            }
        }
        if (random.nextDouble(100) < core.chance() + (Enchants.pickaxe(tool) ? Enchants.sum(tool, "core") : 0))
            block.getWorld().dropItemNaturally(block.getLocation().add(.5, .5, .5),
                taggedItem(core.key(), core.label()));
    }

    private static final int MAX_UPGRADE = 20;
    private static final int PRIME_LINE_CHANCE = 10;
    private static final List<List<String>> RESEARCH_STAGES = List.of(
        List.of("research_desk", "pet_storage", "plank", "club", "basictrap"),
        List.of("plate", "lumber", "mine", "hammer", "ironclub", "irontrap", "farm", "craft", "reinforced_plank"),
        List.of("feeder", "diamondclub", "diamondtrap", "pet_market", "diamondmine", "blue_diamond", "coated_plank"),
        List.of("amber_ingot", "amber_trap", "amber_club", "amber_quarry", "cabbage_farm",
            "redwood_lumber", "cooking_table", "ranch", "vitality_crystal", "redwood_plank"));
    /** 연구 entries whose 제작법 is not implemented yet: skipped by the craft catalog until a recipe exists. */
    private static final java.util.Set<String> PLANNED = java.util.Set.of();
    /** Mod item id backing each tier-4 research icon (cooking_table uses the craft station blueprint instead). */
    private static final Map<String, String> TIER4_MOD = Map.ofEntries(
        Map.entry("amber_ingot", "amber_ingot"), Map.entry("amber_trap", "amber_trap"),
        Map.entry("amber_club", "amber_club"), Map.entry("amber_quarry", "amber"),
        Map.entry("cabbage_farm", "cabbage"), Map.entry("redwood_lumber", "redwood_log"),
        Map.entry("ranch", "ranch_blueprint"), Map.entry("vitality_crystal", "vitality_crystal"),
        Map.entry("redwood_plank", "redwood_plank"));
    private static final Map<String, String> TIER4_LABEL = Map.ofEntries(
        Map.entry("amber_ingot", "호박석 주괴"), Map.entry("amber_trap", "호박석 포획틀"),
        Map.entry("amber_club", "호박석 몽둥이"), Map.entry("amber_quarry", "호박석 채석장"),
        Map.entry("cabbage_farm", "양배추 농장"), Map.entry("redwood_lumber", "홍서나무 벌목장"),
        Map.entry("cooking_table", "조리대"), Map.entry("ranch", "목장"),
        Map.entry("vitality_crystal", "활력의 결정체"), Map.entry("redwood_plank", "홍서나무 판자"));
    private static final Map<String, Material> TIER4_FALLBACK = Map.ofEntries(
        Map.entry("amber_ingot", Material.GOLD_INGOT), Map.entry("amber_trap", Material.IRON_BARS),
        Map.entry("amber_club", Material.STICK), Map.entry("amber_quarry", Material.AMETHYST_SHARD),
        Map.entry("cabbage_farm", Material.CARROT), Map.entry("redwood_lumber", Material.OAK_LOG),
        Map.entry("ranch", Material.PAPER), Map.entry("vitality_crystal", Material.AMETHYST_SHARD),
        Map.entry("redwood_plank", Material.OAK_PLANKS));
    static final java.util.Set<Integer> CRAFT_RESERVED=java.util.Set.of(0,9,10,18,19,27,28,36,37,45,46,47,38,13,16,21,22,23,25,31,32,34,49);
    static final int[] CATALOG_SLOTS=java.util.stream.IntStream.range(0,54).filter(i->!CRAFT_RESERVED.contains(i)).toArray();
    static final List<String> CATALOG=buildCraftCatalog();
    /** Recipes this player has unlocked at the research lab (unresearched ones are hidden from the crafting station). */
    List<String> visibleCatalog(Player player){return CATALOG.stream().filter(key->learned(player,key)).toList();}
    int catalogPages(Player player){return Math.max(1,(visibleCatalog(player).size()+CATALOG_SLOTS.length-1)/CATALOG_SLOTS.length);}
    /** Visible recipes of one catalog page: slot -> key. */
    Map<Integer,String> catalogPage(Player player,int page){
        List<String> visible=visibleCatalog(player);
        page=Math.clamp(page,0,Math.max(1,(visible.size()+CATALOG_SLOTS.length-1)/CATALOG_SLOTS.length)-1);
        Map<Integer,String> result=new java.util.LinkedHashMap<>();
        for(int i=0;i<CATALOG_SLOTS.length;i++){int index=page*CATALOG_SLOTS.length+i;if(index>=visible.size())break;result.put(CATALOG_SLOTS[i],visible.get(index));}
        return result;
    }
    private static List<String> buildCraftCatalog() {
        java.util.LinkedHashSet<String> keys=new java.util.LinkedHashSet<>();
        for(var stage:RESEARCH_STAGES)for(String key:stage) {
            if(RECIPES.containsKey(key))keys.add(key);
            else if(PLANNED.contains(key)){ /* 제작법 미구현: 카탈로그에서 제외 */ }
            else if(!key.startsWith("smith_tier_"))throw new IllegalStateException("연구 제작법에 레시피가 없습니다: "+key);
        }
        RECIPES.keySet().stream().filter(k->!java.util.Set.of("ingot","diamond").contains(k)).sorted().forEach(keys::add);
        return List.copyOf(keys);
    }
    private record Gear(Material material, String name, int plates, boolean armor) {}
    private static final List<Gear> GEAR = List.of(
        new Gear(Material.DIAMOND_SWORD, "제련 다이아 검", 3, false),
        new Gear(Material.DIAMOND_AXE, "제련 다이아 도끼", 4, false),
        new Gear(Material.DIAMOND_HELMET, "제련 다이아 투구", 3, true),
        new Gear(Material.DIAMOND_CHESTPLATE, "제련 다이아 갑옷", 5, true),
        new Gear(Material.DIAMOND_LEGGINGS, "제련 다이아 각반", 4, true),
        new Gear(Material.DIAMOND_BOOTS, "제련 다이아 장화", 2, true));

    /** Success chance in tenths of a percent (step n = level+1, linear inside each range of 5 steps). */
    static int successChance(boolean armor, int level, int gauge) {
        if (gauge >= 100) return 1000;
        int n = level + 1;
        double[][] ranges = armor
            ? new double[][]{{90, 70}, {70, 50}, {50, 30}}
            : new double[][]{{90, 60}, {60, 40}, {40, 20}};
        double rate;
        if (n >= 16) rate = armor ? 30 : 20;
        else {
            double[] r = ranges[(n - 1) / 5];
            int pos = (n - 1) % 5; // 0..4
            rate = r[0] + (r[1] - r[0]) * pos / 4.0;
        }
        return (int) Math.round(rate * 10);
    }

    static String percentText(int tenths) { return tenths % 10 == 0 ? (tenths / 10) + "%" : (tenths / 10) + "." + (tenths % 10) + "%"; }

    /** 대장장이의 축복: fails needed for a guaranteed success is 3 (n1-5), 7 (n6-10), 20 (n11+); gain per fail = ceil(100/N). */
    static int gaugeGain(int level) {
        int n = level + 1;
        int fails = n <= 5 ? 3 : n <= 10 ? 7 : 20;
        return (100 + fails - 1) / fails;
    }
    static int failedLevel(int level) { return level >= 10 ? level - 1 : level; }

    private boolean learned(Player player, String key) {
        if (key.equals("smith_tier_0")) return true;
        if (Set.of("research_desk","pet_storage","plank","club","basictrap").contains(key)) return true;
        if (key.equals("carrotfarm") || key.equals("potatofarm")) key = "farm";
        return plugin.data().getBoolean(user(player) + ".research." + key);
    }

    static int researchPrice(int stage) { return stage * 20; }
    static int openingPrice(int stage) {
        return switch (stage) {
            case 2 -> 10_000;
            case 3 -> 50_000;
            case 4 -> 100_000;
            default -> 0;
        };
    }

    /** A research entry is only researchable once its 제작법 exists (or it is not a planned entry). */
    private static boolean researchable(String key) {
        return !PLANNED.contains(key) || RECIPES.containsKey(key);
    }

    private static Ingredient wardenSoulStone(int count) {
        return tagged(Material.PAPER, "warden_soul_stone", "워든의 영혼석", count);
    }

    /** The real 워든의 영혼석 stack (mod item) with the plugin tag so it counts for stage-4 unlocking. */
    private ItemStack wardenSoulStoneItem(int count) {
        Material mod = ModItems.byIdOrNull("warden_soul_stone");
        ItemStack item = plugin.statusIcon(mod != null ? mod : Material.PAPER, "워든의 영혼석",
            "워든을 처치하면 얻는 영혼석", "4티어 연구 개방 재료");
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(processed, PersistentDataType.STRING, "warden_soul_stone");
        item.setItemMeta(meta);
        item.setAmount(count);
        return item;
    }

    /** Extra items required to unlock a stage (the real item stacks shown on the locked panel). */
    private static List<Ingredient> unlockRequirements(int stage) {
        return stage == 4 ? List.of(wardenSoulStone(5)) : List.of();
    }

    private int openStage(Player player) {
        return Math.clamp(plugin.data().getInt(user(player) + ".research-stage", 1), 1, RESEARCH_STAGES.size());
    }

    private List<String> pendingResearch(Player player, int stage) {
        return RESEARCH_STAGES.get(stage - 1).stream()
            .filter(key -> !learned(player, key))
            .filter(TownSystems::researchable)
            .toList();
    }

    private ItemStack researchIcon(Player player, String key) {
        if (key.startsWith("smith_tier_")) {
            int tier = Integer.parseInt(key.substring(11));
            return modeledIcon(weaponMaterial(1, tier), tier + "티어 장비 제작법");
        }
        for (Gear gear : GEAR)
            if (gearResearchKey(gear).equals(key)) return plugin.statusIcon(gear.material(), gear.name());
        if (key.equals("cooking_table")) return plugin.stationItem(Facility.CRAFT);
        String modId = TIER4_MOD.get(key);
        if (modId != null) {
            String label = TIER4_LABEL.get(key);
            Material mod = ModItems.byIdOrNull(modId);
            return plugin.statusIcon(mod != null ? mod : TIER4_FALLBACK.get(key), label);
        }
        return recipeOutput(key);
    }

    /** The real item stack shown in a locked stage's required-item slot. */
    private ItemStack requirementIcon(Player player, Ingredient requirement) {
        Material mod = ModItems.forTagOrNull(requirement.tag());
        ItemStack item = plugin.statusIcon(mod != null ? mod : requirement.material(), requirement.label(),
            ingredientCount(player, requirement) + "/" + requirement.count());
        return item;
    }

    private static final class SmithMenu implements InventoryHolder {
        final boolean research;
        final int stage;
        final int selection;
        Inventory inventory;
        SmithMenu(boolean research, int stage, int selection) {
            this.research = research;
            this.stage = stage;
            this.selection = selection;
        }
        @Override public Inventory getInventory() { return inventory; }
    }

    private NamespacedKey gearKey(String key) { return new NamespacedKey(plugin, "smith_" + key); }

    private int gearValue(ItemStack item, String key, int fallback) {
        if (!item.hasItemMeta()) return fallback;
        return item.getItemMeta().getPersistentDataContainer()
            .getOrDefault(gearKey(key), PersistentDataType.INTEGER, fallback);
    }

    private String gearResearchKey(Gear gear) { return "gear_" + gear.material().name().toLowerCase(Locale.ROOT); }

    private Gear gearFor(ItemStack item) {
        if (item == null || item.getAmount() != 1 || gearValue(item, "lines", 0) < 3) return null;
        String label = item.getItemMeta().getPersistentDataContainer().get(gearKey("catalog"), PersistentDataType.STRING);
        if (label != null) return new Gear(item.getType(), label, 0, item.getType().name().matches(".*_(HELMET|CHESTPLATE|LEGGINGS|BOOTS)"));
        return GEAR.stream().filter(gear -> gear.material() == item.getType()).findFirst().orElse(null);
    }

    boolean openSmith(Player player, boolean research) {
        return openSmith(player, research, 1, 0);
    }

    private boolean openSmith(Player player, boolean research, int stage, int selection) {
        if (!town(player)) return tell(player, "공동 마을에서 이용하세요.");
        if (research && !plugin.tutorialComplete(player)) return tell(player, "튜토리얼을 완료하면 연구소를 이용할 수 있습니다.");
        if (research && !plugin.nearFacility(player, Facility.RESEARCH))
            return tell(player, "내 거주지에 설치한 연구소를 우클릭하세요.");
        SmithMenu menu = new SmithMenu(research, stage, selection);
        MenuArt.View view = research ? new MenuArt.View(Bukkit.createInventory(menu, 54, "삐삐서버 · 연구소"), null)
            : MenuArt.create(menu, 27, "삐삐서버 · 대장간", "smith");
        Inventory inventory = view.inventory();
        menu.inventory = inventory;
        if (research) {
            drawResearch(player, menu);
        } else {
            for (int i = 0; i < GEAR.size(); i++) {
                Gear gear = GEAR.get(i);
                inventory.setItem(i, plugin.statusIcon(gear.material(), gear.name(),
                    "철판 " + gear.plates() + "개 + 다이아몬드 1개",
                    learned(player, gearResearchKey(gear)) ? "제작 시 잠재 칸 3~6개" : "§c연구소에서 해금 필요"));
            }
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (gearFor(hand) != null) {
                int level = gearValue(hand, "level", 0);
                int gauge = gearValue(hand, "gauge", 0);
                inventory.setItem(13, hand.clone());
                inventory.setItem(20, plugin.statusIcon(Material.ANVIL, level == MAX_UPGRADE ? "최대 강화" : "+" + level + " → +" + (level + 1),
                    "성공률 " + percentText(successChance(CombatStats.armor(hand), level, gauge)) + " · " + upgradeCost(hand, level) + "골드 · " + upgradeMatsText(level),
                    "§e" + "■".repeat(gauge / 10) + "§7" + "□".repeat(10 - gauge / 10) + " " + gauge + "%",
                    "실패 시 게이지 +" + gaugeGain(level) + "%p" + (level >= 10 ? " · 1단계 하락" : "")));
            } else inventory.setItem(13, plugin.statusIcon(Material.BARRIER, "제작한 장비를 주 손에 드세요."));
        }
        if (!research) inventory.setItem(26, plugin.statusIcon(Material.BOOK, "연구소 열기"));
        view.open(player);
        return true;
    }

    private record ForgeRecipe(Material material, String label, int plates, int diamonds, int gold) {}
    private static final class ForgeDesk implements InventoryHolder {
        final int tab, selected;
        final boolean confirming;
        final ItemStack result;
        Inventory inventory;
        ForgeDesk(int tab, int selected, boolean confirming, ItemStack result) {
            this.tab = tab;
            this.selected = selected;
            this.confirming = confirming;
            this.result = result;
        }
        @Override public Inventory getInventory() { return inventory; }
    }

    private Material weaponMaterial(int tab, int tier) {
        return switch (tab) {
            case 8 -> Material.BOW;
            case 9 -> Material.BLAZE_ROD;
            default -> tier < 2 ? Material.IRON_SWORD : tier < 5 ? Material.DIAMOND_SWORD : Material.NETHERITE_SWORD;
        };
    }

    private ItemStack modeledIcon(Material material, String name, String... lore) {
        ItemStack icon = plugin.statusIcon(material, name, lore);
        GearModels.apply(icon);
        return icon;
    }

    private List<ForgeRecipe> forgeRecipes(Player player, int tab) {
        if (tab == 0) {
            List<ForgeRecipe> tools = new ArrayList<>(List.of(new ForgeRecipe(Material.IRON_PICKAXE, "철 곡괭이", 5, 0, 20),
                new ForgeRecipe(Material.FISHING_ROD, "낚시대", 5, 0, 5000)));
            if (learned(player, "smith_tier_2")) tools.add(new ForgeRecipe(Material.DIAMOND_PICKAXE, "다이아몬드 곡괭이", 0, 5, 100));
            return tools;
        }
        List<ForgeRecipe> recipes = new ArrayList<>();
        if (tab == 1 || tab == 8 || tab == 9) {
            for (int tier = 0; tier <= 5; tier++) if (learned(player, "smith_tier_" + tier)) {
                Material material = weaponMaterial(tab, tier);
                String weapon = switch (tab) { case 9 -> "지팡이"; case 8 -> "활"; default -> "검"; };
                recipes.add(new ForgeRecipe(material, tier + "티어 " + weapon, 2 + tier, tier, 30 + tier * 30));
            }
        } else if (tab >= 2 && tab <= 7 || tab >= 10 && tab <= 27) {
            int tier = tab >= 10 ? (tab - 10) / 3 : tab - 2;
            String family = new String[]{"근거리", "원거리", "마법"}[tab >= 10 ? (tab - 10) % 3 : 0];
            if (!learned(player, "smith_tier_" + tier)) return recipes;
            String prefix = tier < 2 ? "IRON_" : tier < 5 ? "DIAMOND_" : "NETHERITE_";
            String[] suffix = {"HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"};
            String[] label = {"투구", "갑옷", "각반", "신발"};
            for (int i = 0; i < suffix.length; i++) recipes.add(new ForgeRecipe(Material.valueOf(prefix + suffix[i]),
                tier + "티어 " + family + " " + label[i], 2 + tier + (i == 1 ? 2 : 0), tier, 30 + tier * 30));
        }
        return recipes;
    }

    void installMarketNpcs(World world) {
        installMarketNpc(world, -25, -25, "상점", "상인", "merchant", org.bukkit.entity.Villager.Profession.FARMER);
        installMarketNpc(world, 24, 24, "대장간", "대장장이", "npc", org.bukkit.entity.Villager.Profession.TOOLSMITH);
    }

    private void installMarketNpc(World world, int x, int signX, String signName, String name, String key,
                                  org.bukkit.entity.Villager.Profession profession) {
        Location location = new Location(world, x + .5, 64, 12.5, 180, 0);
        location.getChunk().load();
        if (!npcs.exists(world, key, location)) {
            if (!location.getBlock().isPassable() || !location.clone().add(0, 1, 0).getBlock().isPassable()
                || !location.clone().add(0, -1, 0).getBlock().getType().isSolid()) return;
            world.spawn(location, org.bukkit.entity.Villager.class, npc -> {
                npc.setCustomName(name);
                npc.setCustomNameVisible(true);
                npc.setProfession(profession);
                npc.setAI(false);
                npc.setInvulnerable(true);
                npc.setPersistent(true);
                npcs.register(npc, key);
            });
        }
        Block signBlock = world.getBlockAt(signX, 64, 6);
        if (signBlock.getState() instanceof org.bukkit.block.Sign sign && sign.getLine(0).equals(signName))
            signBlock.setType(Material.AIR);
    }

    private boolean spawnSmith(Player player) {
        if (!town(player) || !player.isOp()) return tell(player, "공동 마을에서 관리자만 설치할 수 있습니다.");
        if (npcs.exists(player.getWorld(), "npc", player.getLocation())) return tell(player, "대장장이는 이미 등록되어 있습니다.");
        player.getWorld().spawn(player.getLocation(), org.bukkit.entity.Villager.class, npc -> {
            npc.setCustomName("대장장이");
            npc.setCustomNameVisible(true);
            npc.setProfession(org.bukkit.entity.Villager.Profession.TOOLSMITH);
            npc.setAI(false);
            npc.setInvulnerable(true);
            npcs.register(npc, "npc");
        });
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void smithNpc(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !npcs.matches(event.getRightClicked(), "npc")) return;
        event.setCancelled(true);
        openForgeDesk(event.getPlayer(), -1, -1, false, null);
    }

    private boolean openForgeDesk(Player player, int tab, int selected, boolean confirm, ItemStack result) {
        if (!town(player)) return tell(player, "공동 마을 대장간에서 이용하세요.");
        ForgeDesk menu = new ForgeDesk(tab, selected, confirm, result);
        Inventory inventory = Bukkit.createInventory(menu, 54, tab < 0 ? "삐삐서버 · 대장간 메뉴" : "삐삐서버 · 장비 제작");
        menu.inventory = inventory;
        if (tab < 0) {
            inventory.setItem(10, plugin.statusIcon(Material.ANVIL, "제작"));
            inventory.setItem(12, plugin.statusIcon(Material.IRON_SWORD, "강화"));
            inventory.setItem(14, plugin.statusIcon(Material.ENCHANTED_BOOK, "계승", "계승 화면을 엽니다", "새 티어 장비로 인챈트 옵션과 강화를 옮깁니다", "계승 재료는 사라집니다"));
            inventory.setItem(16, plugin.statusIcon(Material.GRINDSTONE, "제련", "제작한 장비·도구를 부수고", "제작 재료의 절반(올림)을 돌려받습니다"));
            inventory.setItem(22, plugin.statusIcon(Material.BARRIER, "나가기"));
        } else {
            inventory.setItem(0, modeledIcon(Material.IRON_PICKAXE, "도구"));
            inventory.setItem(1, modeledIcon(weaponMaterial(1, 0), "무기"));
            for (int tier = 0; tier <= 5; tier++) if (learned(player, "smith_tier_" + tier))
                inventory.setItem(tier + 2, plugin.statusIcon(Material.IRON_CHESTPLATE, tier + "티어 " + "방어구"));
            if (tab == 1 || tab == 8 || tab == 9) {
                inventory.setItem(15, modeledIcon(Material.IRON_SWORD, "검"));
                inventory.setItem(16, modeledIcon(Material.BOW, "활"));
                inventory.setItem(17, modeledIcon(Material.BLAZE_ROD, "지팡이"));
            }
            if (tab >= 2 && tab <= 7 || tab >= 10 && tab <= 27) {
                inventory.setItem(15, plugin.statusIcon(Material.IRON_CHESTPLATE, "근거리"));
                inventory.setItem(16, plugin.statusIcon(Material.BOW, "원거리"));
                inventory.setItem(17, plugin.statusIcon(Material.BLAZE_ROD, "마법"));
            }
            List<ForgeRecipe> recipes = forgeRecipes(player, tab);
            for (int i = 0; i < recipes.size(); i++)
                inventory.setItem(9 + i, modeledIcon(recipes.get(i).material(), recipes.get(i).label()));
            if (selected >= 0 && selected < recipes.size()) {
                ForgeRecipe recipe = recipes.get(selected);
                inventory.setItem(22, modeledIcon(recipe.material(), recipe.label(),
                    forgeIngredientText(player, forgeMetal(recipe)),
                    forgeIngredientText(player, forgeDiamond(recipe)),
                    "소모 골드 " + recipe.gold(), "보유 골드 " + gold(player).getScore()));
                inventory.setItem(40, plugin.statusIcon(Material.ANVIL, "제작하기"));
                if (confirm) inventory.setItem(41, plugin.statusIcon(Material.LIME_DYE, "제작 확인"));
            }
            if (result != null) inventory.setItem(31, result.clone());
            inventory.setItem(49, plugin.statusIcon(Material.ARROW, "돌아가기"));
        }
        player.openInventory(inventory);
        return true;
    }

    @EventHandler
    public void forgeDeskDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ForgeDesk) event.setCancelled(true);
    }

    @EventHandler
    public void forgeDeskClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ForgeDesk menu)) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (!(event.getWhoClicked() instanceof Player player) || !event.isLeftClick() || slot < 0 || slot >= 54
            || menu.inventory.getItem(slot) == null) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!town(player) || player.getOpenInventory().getTopInventory().getHolder() != menu) return;
            if (menu.tab < 0) {
                switch (slot) {
                    case 10 -> openForgeDesk(player, 0, -1, false, null);
                    case 12 -> openUpgrade(player, 0, -1, false, null, null);
                    case 14 -> openInherit(player, 0, -1, -1, null, null, false, null);
                    case 16 -> openSmelt(player, -1);
                    case 22 -> player.closeInventory();
                    default -> {}
                }
            } else if (slot == 49) {
                if (menu.confirming || menu.result != null) openForgeDesk(player, menu.tab, menu.selected, false, null);
                else openForgeDesk(player, -1, -1, false, null);
            } else if (menu.confirming && slot == 41) {
                List<ForgeRecipe> recipes = forgeRecipes(player, menu.tab);
                if (menu.selected < 0 || menu.selected >= recipes.size()) return;
                ItemStack result = createForgeItem(player, recipes.get(menu.selected));
                if (player.getOpenInventory().getTopInventory().getHolder() == menu) openForgeDesk(player, menu.tab, menu.selected, false, result);
            } else if (!menu.confirming && menu.result == null) {
                if (slot < 8) openForgeDesk(player, slot, -1, false, null);
                else if (slot >= 9 && slot <= 14) openForgeDesk(player, menu.tab, slot - 9, false, null);
                else if (slot >= 15 && slot <= 17) {
                    boolean armorTab = menu.tab >= 2 && menu.tab <= 7 || menu.tab >= 10;
                    int tier = menu.tab >= 10 ? (menu.tab - 10) / 3 : menu.tab - 2;
                    openForgeDesk(player, armorTab ? 10 + tier * 3 + slot - 15 : slot == 15 ? 1 : slot - 8, -1, false, null);
                }
                else if (slot == 40) openForgeDesk(player, menu.tab, menu.selected, true, null);
            }
        });
    }

    private Ingredient forgeMetal(ForgeRecipe recipe) {
        if (recipe.material() == Material.FISHING_ROD) return raw(Material.OAK_LOG, "참나무 원목", recipe.plates());
        return recipe.material() == Material.IRON_PICKAXE
            ? raw(Material.IRON_INGOT, "철 주괴", recipe.plates()) : plates(recipe.plates());
    }

    private Ingredient forgeDiamond(ForgeRecipe recipe) {
        return recipe.material() == Material.DIAMOND_PICKAXE
            ? raw(Material.DIAMOND, "다이아몬드", recipe.diamonds())
            : raw(Material.DIAMOND, "다이아몬드", recipe.diamonds());
    }

    private String forgeIngredientText(Player player, Ingredient ingredient) {
        return ingredient.count() == 0 ? "" : ingredient.label() + " " + ingredientCount(player, ingredient) + " / " + ingredient.count();
    }

    private ItemStack createForgeItem(Player player, ForgeRecipe recipe) {
        int slot = player.getInventory().firstEmpty();
        Score score = gold(player);
        if (slot < 0 || score.getScore() < recipe.gold()) {
            returnTitle(player, slot < 0 ? "가방 공간이 부족합니다." : "골드가 부족합니다."); return null;
        }
        Ingredient metal = forgeMetal(recipe), diamond = forgeDiamond(recipe);
        if (!hasIngredients(player, metal, diamond)) return null;
        ItemStack item = new ItemStack(recipe.material());
        var meta = item.getItemMeta();
        if (recipe.material() == Material.FISHING_ROD) { // plain working rod: no gear tags, no custom model
            meta.setDisplayName("§f낚시대");
            meta.setLore(List.of("§7대장장이가 만든 낚시대"));
            item.setItemMeta(meta);
            consume(player, metal);
            consume(player, diamond);
            score.setScore(score.getScore() - recipe.gold());
            player.getInventory().setItem(slot, item);
            return item;
        }
        int lines = java.util.concurrent.ThreadLocalRandom.current().nextInt(3, 7);
        meta.getPersistentDataContainer().set(gearKey("lines"), PersistentDataType.INTEGER, lines);
        meta.getPersistentDataContainer().set(gearKey("catalog"), PersistentDataType.STRING, recipe.label());
        item.setItemMeta(meta);
        GearModels.apply(item);
        Gear gear = gearFor(item);
        updateGear(item, gear);
        consume(player, metal);
        consume(player, diamond);
        score.setScore(score.getScore() - recipe.gold());
        player.getInventory().setItem(slot, item);
        return item;
    }

    private void dismantle(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (gearFor(hand) == null) { tell(player, "분해할 제작 장비를 주 손에 드세요."); return; }
        player.getInventory().setItemInMainHand(taggedItem("plate", "강철"));
        tell(player, "장비를 분해해 철판 1개를 받았습니다.");
    }

    @EventHandler(ignoreCancelled = true)
    public void smithSign(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK
            || !town(event.getPlayer()) || event.getClickedBlock() == null
            || !(event.getClickedBlock().getState() instanceof org.bukkit.block.Sign sign)) return;
        String label = sign.getLine(0);
        if (!label.equals("연구소")) return;
        event.setCancelled(true);
        openSmith(event.getPlayer(), true);
    }

    @EventHandler
    public void smithClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SmithMenu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !event.isLeftClick()) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= menu.inventory.getSize() || menu.inventory.getItem(slot) == null) return;
        ItemStack shown = menu.inventory.getItem(13);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || !town(player) || player.getOpenInventory().getTopInventory().getHolder() != menu) return;
            if (menu.research) {
                if (!plugin.nearFacility(player, Facility.RESEARCH) || slot == 53) { player.closeInventory(); return; }
                if (slot == 50) {
                    directResearch(player,menu.stage,menu.selection);
                    if(player.getOpenInventory().getTopInventory().getHolder()==menu)openSmith(player,true,menu.stage,menu.selection);
                    return;
                }
                int opened = openStage(player);
                int cursor = 0;
                int actionStage = -1, entryStage = -1, entryIndex = -1;
                for (int stage = 1; stage <= RESEARCH_STAGES.size(); stage++) {
                    boolean open = stage <= opened;
                    int size = open ? Math.min(RESEARCH_STAGES.get(stage - 1).size(), 12) : unlockRequirements(stage).size();
                    if (slot == cursor + 1) { actionStage = stage; }
                    else if (open && slot > cursor + 1 && slot < cursor + 2 + size) {
                        entryStage = stage; entryIndex = slot - (cursor + 2);
                    }
                    if (actionStage != -1 || entryStage != -1) break;
                    cursor += 2 + size;
                }
                if (actionStage != -1) {
                    research(player, actionStage);
                    if (player.getOpenInventory().getTopInventory().getHolder() == menu) openSmith(player, true, menu.stage, menu.selection);
                } else if (entryStage != -1) {
                    openSmith(player, true, entryStage, entryIndex);
                }
                return;
            }
            if (slot == 26) { openSmith(player, true); return; }
            if (slot < GEAR.size()) craftGear(player, GEAR.get(slot));
            else if (!menu.research && slot == 20
                && player.getInventory().getItemInMainHand().equals(shown)) {
                modifyGear(player);
            }
            if (player.getOpenInventory().getTopInventory().getHolder() == menu) openSmith(player, menu.research);
        });
    }

    @EventHandler
    public void smithDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof SmithMenu) event.setCancelled(true);
    }

    private void drawResearch(Player player, SmithMenu menu) {
        Inventory inventory = menu.inventory;
        int opened = openStage(player);
        int cursor = 0;
        for (int stage = 1; stage <= RESEARCH_STAGES.size(); stage++) {
            List<String> keys = RESEARCH_STAGES.get(stage - 1);
            boolean open = stage <= opened;
            inventory.setItem(cursor, plugin.statusIcon(open ? Material.DIAMOND : Material.IRON_BARS, "§b" + stage + "단계"));
            if (open && !pendingResearch(player, stage).isEmpty()) {
                inventory.setItem(cursor + 1, plugin.statusIcon(Material.ENCHANTED_BOOK, "랜덤 연구", researchPrice(stage) + "골드"));
            } else if (open) {
                inventory.setItem(cursor + 1, plugin.statusIcon(Material.GRAY_DYE, "연구 완료"));
            } else {
                List<String> lore = new ArrayList<>();
                lore.add("골드 " + gold(player).getScore() + "/" + openingPrice(stage));
                if (stage != opened + 1) lore.add("이전 단계 개방 필요");
                inventory.setItem(cursor + 1, plugin.statusIcon(Material.IRON_BARS, "개방하기",
                    lore.toArray(String[]::new)));
            }
            if (open) {
                int count = Math.min(keys.size(), 12);
                for (int i = 0; i < count; i++) {
                    String key = keys.get(i);
                    ItemStack icon = researchIcon(player, key);
                    var meta = icon.getItemMeta();
                    meta.setLore(List.of(researchStatus(player, key, stage, opened)));
                    if (stage == menu.stage && i == menu.selection) meta.setEnchantmentGlintOverride(true);
                    icon.setItemMeta(meta);
                    inventory.setItem(cursor + 2 + i, icon);
                }
                cursor += 2 + count;
            } else {
                List<Ingredient> requirements = unlockRequirements(stage);
                for (int i = 0; i < requirements.size(); i++)
                    inventory.setItem(cursor + 2 + i, requirementIcon(player, requirements.get(i)));
                cursor += 2 + requirements.size();
            }
        }
        int selStage = Math.clamp(menu.stage, 1, opened);
        List<String> selKeys = RESEARCH_STAGES.get(selStage - 1);
        int selIndex = Math.clamp(menu.selection, 0, selKeys.size() - 1);
        String key = selKeys.get(selIndex);
        ItemStack detail = researchIcon(player, key);
        var meta = detail.getItemMeta();
        List<String> lore = new ArrayList<>();
        lore.add(researchStatus(player, key, selStage, opened));
        if (key.equals("vitality_crystal")) lore.add("0티어 장비 강화 재료");
        Recipe recipe = RECIPES.get(key);
        if (key.startsWith("smith_tier_")) {
            lore.add("검 · 활 · 지팡이 · 방어구 제작 해금");
            if (key.equals("smith_tier_2")) lore.add("도구 탭에 다이아몬드 곡괭이 추가");
        }
        if (recipe != null) {
            lore.add("§b제작 재료");
            for (Ingredient ingredient : recipe.inputs()) lore.add(ingredient.label() + " ×" + ingredient.count());
        } else for (Gear gear : GEAR) {
            if (gearResearchKey(gear).equals(key)) {
                lore.add("철판 ×" + gear.plates() + " · 다이아몬드 ×1");
                lore.add("잠재 슬롯 3~6개");
                break;
            }
        }
        meta.setLore(lore);
        detail.setItemMeta(meta);
        inventory.setItem(51, detail);
        if (researchable(key) && !learned(player, key) && selStage <= opened) inventory.setItem(50,
            plugin.statusIcon(Material.ENCHANTED_BOOK, "연구 시작", (researchPrice(selStage) * 5) + "골드"));
        inventory.setItem(52, plugin.statusIcon(Material.GOLD_INGOT, "보유 골드 " + gold(player).getScore(),
            "선택한 제작법의 상세 정보는 위 아이콘에 마우스를 올려 확인하세요."));
        inventory.setItem(53, plugin.statusIcon(Material.BARRIER, "닫기"));
    }

    private void directResearch(Player player,int stage,int selection) {
        if(stage<1||stage>openStage(player)||!plugin.nearFacility(player,Facility.RESEARCH))return;
        String key=RESEARCH_STAGES.get(stage-1).get(selection);
        if(learned(player,key)||!researchable(key))return;
        int cost=researchPrice(stage)*5;Score score=gold(player);
        if(score.getScore()<cost){returnTitle(player,cost+"골드가 필요합니다.");return;}
        if(!plugin.store(user(player)+".research."+key,true)){tell(player,"연구 저장에 실패했습니다.");return;}
        score.setScore(score.getScore()-cost);tell(player,"선택 연구 완료");
    }
    private String researchStatus(Player player, String key, int stage, int opened) {
        if (learned(player, key)) return "§a해금 완료";
        if (!researchable(key)) return "§7제작법 준비 중";
        return stage > opened ? "§7단계 잠김" : "§e랜덤 연구 대상";
    }

    private void research(Player player, int stage) {
        int opened = openStage(player);
        Score score = gold(player);
        if (stage > opened) {
            if (stage != opened + 1) { tell(player, "이전 단계를 먼저 개방하세요."); return; }
            int cost = openingPrice(stage);
            List<Ingredient> requirements = unlockRequirements(stage);
            if (score.getScore() < cost) { returnTitle(player, cost + "골드가 필요합니다."); return; }
            for (Ingredient requirement : requirements)
                if (ingredientCount(player, requirement) < requirement.count()) {
                    returnTitle(player, requirement.label() + " " + requirement.count() + "개가 필요합니다."); return;
                }
            if (!plugin.store(user(player) + ".research-stage", stage)) {
                tell(player, "단계 개방을 저장하지 못했습니다."); return;
            }
            score.setScore(score.getScore() - cost);
            for (Ingredient requirement : requirements) consume(player, requirement);
            tell(player, stage + "단계를 개방했습니다.");
            return;
        }
        List<String> pending = pendingResearch(player, stage);
        if (pending.isEmpty()) { tell(player, "이 단계의 연구가 모두 끝났습니다."); return; }
        int cost = researchPrice(stage);
        if (score.getScore() < cost) { returnTitle(player, cost + "골드가 필요합니다."); return; }
        String key = pending.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(pending.size()));
        if (!plugin.store(user(player) + ".research." + key, true)) {
            tell(player, "연구를 저장하지 못했습니다."); return;
        }
        score.setScore(score.getScore() - cost);
        tell(player, "연구 완료 · " + researchIcon(player, key).getItemMeta().getDisplayName());
    }

    private Ingredient plates(int count) { return tagged(Material.IRON_NUGGET, "plate", "강철", count); }
    private Ingredient refinedDiamond() { return raw(Material.DIAMOND, "다이아몬드", 1); }

    private boolean hasIngredients(Player player, Ingredient... inputs) {
        for (Ingredient input : inputs) {
            if (ingredientCount(player, input) < input.count()) {
                returnTitle(player, input.label() + " " + input.count() + "개가 필요합니다.");
                return false;
            }
        }
        return true;
    }

    private void craftGear(Player player, Gear gear) {
        if (!learned(player, gearResearchKey(gear))) { tell(player, "연구소에서 먼저 레시피를 해금하세요."); return; }
        int slot = player.getInventory().firstEmpty();
        if (slot < 0) { tell(player, "가방에 빈 칸이 필요합니다."); return; }
        if (!hasIngredients(player, plates(gear.plates()), refinedDiamond())) return;
        ItemStack item = new ItemStack(gear.material());
        var meta = item.getItemMeta();
        var tags = meta.getPersistentDataContainer();
        tags.set(gearKey("lines"), PersistentDataType.INTEGER, java.util.concurrent.ThreadLocalRandom.current().nextInt(3, 7));
        item.setItemMeta(meta);
        updateGear(item, gear);
        consume(player, plates(gear.plates()));
        consume(player, refinedDiamond());
        player.getInventory().setItem(slot, item);
        tell(player, gear.name() + " 제작 완료");
    }

    /** 강화 골드 for step n = level + 1. 무기: 2000 시작, +5까지 +1000, +10까지 +1400, +15까지(이후도) +2300 / 방어구: 1000 시작, +1000, +1200, +2000. */
    private int upgradeCost(ItemStack item, int level) {
        boolean armor = CombatStats.armor(item);
        int cost = armor ? 1000 : 2000;
        for (int n = 2; n <= level + 1; n++)
            cost += n <= 5 ? 1000 : n <= 10 ? (armor ? 1400 : 1600) : (armor ? 2300 : 2600);
        return cost;
    }

    /** 재료 per 강화 step n = level + 1. n >= 10 reuses the n = 10 cost for now. */
    private record UpgradeMats(int steel, int blue, int vitality) {}
    private UpgradeMats upgradeMats(int level) {
        return switch (level + 1) {
            case 1 -> new UpgradeMats(2, 0, 0);
            case 2 -> new UpgradeMats(4, 0, 0);
            case 3 -> new UpgradeMats(6, 0, 0);
            case 4 -> new UpgradeMats(8, 0, 0);
            case 5 -> new UpgradeMats(2, 1, 0);
            case 6 -> new UpgradeMats(4, 1, 0);
            case 7 -> new UpgradeMats(6, 1, 0);
            case 8, 9 -> new UpgradeMats(8, 2, 0);
            case 10 -> new UpgradeMats(10, 4, 0);
            default -> new UpgradeMats(0, 0, 1); // 11강~: 활력의 결정체 1개 + 골드만
        };
    }
    private String upgradeMatsText(int level) {
        UpgradeMats mats = upgradeMats(level);
        List<String> parts = new ArrayList<>();
        if (mats.steel() > 0) parts.add("강철 " + mats.steel());
        if (mats.blue() > 0) parts.add("블루 다이아몬드 " + mats.blue());
        if (mats.vitality() > 0) parts.add("활력의 결정체 " + mats.vitality());
        return String.join(" · ", parts);
    }

    private void modifyGear(Player player) {
        applyUpgrade(player, player.getInventory().getHeldItemSlot(), player.getInventory().getItemInMainHand());
    }

    private record UpgradeResult(ItemStack item, String message, boolean success) {}

    private UpgradeResult applyUpgrade(Player player, int selected, ItemStack snapshot) {
        ItemStack item = player.getInventory().getItem(selected);
        if (snapshot == null || !snapshot.equals(item) || !CombatStats.eligible(item)) {
            tell(player, "장비가 바뀌었습니다. 다시 선택하세요.");
            return null;
        }
        int level = gearValue(item, "level", 0);
        int gauge = Math.clamp(gearValue(item, "gauge", 0), 0, 100);
        if (level >= MAX_UPGRADE) return null;
        Score score = gold(player);
        int cost = upgradeCost(item, level);
        if (score.getScore() < cost) { returnTitle(player, cost + "골드가 필요합니다."); return null; }
        UpgradeMats mats = upgradeMats(level);
        List<Ingredient> inputs = new ArrayList<>();
        if (mats.steel() > 0) inputs.add(plates(mats.steel()));
        if (mats.blue() > 0) inputs.add(blueDiamond(mats.blue()));
        if (mats.vitality() > 0) inputs.add(vitalityCrystal(mats.vitality()));
        if (!inputs.isEmpty() && !hasIngredients(player, inputs.toArray(Ingredient[]::new))) return null;
        boolean success = java.util.concurrent.ThreadLocalRandom.current().nextInt(1000) < successChance(CombatStats.armor(item), level, gauge);
        int next = success ? level + 1 : failedLevel(level);
        int nextGauge = success ? 0 : Math.min(100, gauge + gaugeGain(level));
        ItemStack updated = upgradePreview(item, next);
        var meta = updated.getItemMeta();
        meta.getPersistentDataContainer().set(gearKey("gauge"), PersistentDataType.INTEGER, nextGauge);
        updated.setItemMeta(meta);
        for (Ingredient input : inputs) consume(player, input);
        score.setScore(score.getScore() - cost);
        player.getInventory().setItem(selected, updated);
        String result = (success ? "강화 성공" : "강화 실패") + " · +" + next + " · 대장장이의 축복 " + nextGauge + "%";
        tell(player, result);
        return new UpgradeResult(updated.clone(), result, success);
    }

    private ItemStack upgradePreview(ItemStack item, int level) {
        ItemStack preview = item.clone();
        var meta = preview.getItemMeta();
        meta.getPersistentDataContainer().set(gearKey("level"), PersistentDataType.INTEGER, level);
        preview.setItemMeta(meta);
        updateGear(preview, gearFor(preview));
        return preview;
    }

    private static final String UPGRADE_TITLE = "삐삐서버 · 장비 강화";
    private record UpgradeDesk(int page, int selected, boolean confirm, ItemStack snapshot,
                               List<Integer> slots, Inventory[] view) implements InventoryHolder {
        @Override public Inventory getInventory() { return view[0]; }
    }

    private boolean openUpgrade(Player player, int page, int selected, boolean confirm,
                                ItemStack snapshot, UpgradeResult result) {
        if (!town(player) || !plugin.canTravel(player)) return tell(player, "공동 마을 대장간에서 이용하세요.");
        var bag = player.getInventory();
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < bag.getSize(); i++) if (CombatStats.eligible(bag.getItem(i))) slots.add(i);
        page = Math.clamp(page, 0, Math.max(0, (slots.size() - 1) / 9));
        ItemStack chosen = slots.contains(selected) ? bag.getItem(selected) : null;
        if (chosen == null || snapshot != null && !snapshot.equals(chosen)) {
            selected = -1;
            chosen = null;
            confirm = false;
        }
        var menu = new UpgradeDesk(page, selected, confirm, chosen == null ? null : chosen.clone(), slots, new Inventory[1]);
        Inventory view = Bukkit.createInventory(menu, 54, UPGRADE_TITLE);
        menu.view()[0] = view;
        for (int i = 0; i < 9 && page * 9 + i < slots.size(); i++)
            view.setItem(i, bag.getItem(slots.get(page * 9 + i)).clone());
        if (page > 0) view.setItem(48, plugin.statusIcon(Material.ARROW, "이전"));
        if ((page + 1) * 9 < slots.size()) view.setItem(50, plugin.statusIcon(Material.ARROW, "다음"));
        view.setItem(49, plugin.statusIcon(Material.ARROW, "대장간으로"));
        if (chosen != null) {
            int level = gearValue(chosen, "level", 0);
            int gauge = Math.clamp(gearValue(chosen, "gauge", 0), 0, 100);
            int next = Math.min(MAX_UPGRADE, level + 1);
            view.setItem(22, chosen.clone());
            ItemStack preview = upgradePreview(chosen, next);
            view.setItem(23, preview);
            view.setItem(25, plugin.statusIcon(Material.PAPER, "강화 정보",
                level + "," + next + "," + successChance(CombatStats.armor(chosen), level, gauge) + "," + gauge + "," + gaugeGain(level) + "," + MAX_UPGRADE));
            view.setItem(31, plugin.statusIcon(Material.WRITABLE_BOOK, "강화 전 능력치", gearStats(chosen).toArray(String[]::new)));
            view.setItem(32, plugin.statusIcon(Material.WRITABLE_BOOK, "강화 후 능력치", gearStats(preview).toArray(String[]::new)));
            if (upgradeMats(level).steel() > 0) view.setItem(36, plugin.displayIcon(recipeOutput("plate"), "강철",
                ingredientCount(player, plates(1)) + " / " + upgradeMats(level).steel()));
            if (upgradeMats(level).blue() > 0) view.setItem(37, plugin.displayIcon(recipeOutput("blue_diamond"), "블루 다이아몬드",
                ingredientCount(player, blueDiamond(1)) + " / " + upgradeMats(level).blue()));
            if (upgradeMats(level).vitality() > 0) {
                Material vitality = ModItems.byIdOrNull("vitality_crystal");
                view.setItem(38, plugin.displayIcon(taggedItem(vitality != null ? vitality : Material.AMETHYST_SHARD, "vitality_crystal", "활력의 결정체"),
                    "활력의 결정체", ingredientCount(player, vitalityCrystal(1)) + " / " + upgradeMats(level).vitality()));
            }
            view.setItem(43, plugin.statusIcon(Material.GOLD_INGOT, upgradeCost(chosen, level) + " 골드",
                "보유 " + gold(player).getScore() + " 골드"));
            view.setItem(44, plugin.statusIcon(Material.BOOK, "강화 안내",
                "성공하면 대장장이의 축복이 초기화됩니다.",
                level >= 10 ? "실패하면 강화가 1단계 하락합니다." : "실패해도 강화 단계가 유지됩니다.",
                "실패 시 대장장이의 축복 +" + gaugeGain(level) + "%p",
                "축복 100%이면 다음 시도는 반드시 성공합니다."));
            if (level < MAX_UPGRADE) view.setItem(45, plugin.statusIcon(Material.ANVIL, "강화하기"));
            if (confirm && level < MAX_UPGRADE) {
                view.setItem(46, plugin.statusIcon(Material.LIME_DYE, "강화 확인"));
                view.setItem(47, plugin.statusIcon(Material.BARRIER, "취소"));
            }
        }
        if (result != null) view.setItem(42, plugin.statusIcon(result.success() ? Material.LIME_DYE : Material.RED_DYE, result.message()));
        player.openInventory(view);
        return true;
    }

    private List<String> gearStats(ItemStack item) {
        Gear gear = gearFor(item);
        var slot = gear.armor() ? item.getType().getEquipmentSlot() : EquipmentSlot.HAND;
        var meta = item.getItemMeta();
        List<String> stats = new ArrayList<>();
        stats.add("아이템레벨 " + CombatStats.itemLevel(item));
        stats.add("강화 단계 +" + gearValue(item, "level", 0));
        stats.addAll(CombatStats.baseLines(item, "")); // 공격력/방어력/체력/힘/민첩/지능/행운 (기본 + 강화)
        Attribute[] attributes = {Attribute.GENERIC_ATTACK_DAMAGE, Attribute.GENERIC_ARMOR,
            Attribute.GENERIC_ARMOR_TOUGHNESS, Attribute.GENERIC_MAX_HEALTH, Attribute.GENERIC_ATTACK_SPEED,
            Attribute.GENERIC_MOVEMENT_SPEED, Attribute.PLAYER_BLOCK_INTERACTION_RANGE, Attribute.PLAYER_BLOCK_BREAK_SPEED};
        String[] labels = {"공격력", "방어력", "방어 강도", "최대 체력", "공격 속도", "이동 속도", "채굴 거리", "채굴 속도"};
        for (int i = 4; i < attributes.length; i++) { // 0-3 (공격력/방어력/방어 강도/체력) now come from CombatStats.baseLines
            var modifiers = meta.hasAttributeModifiers() ? meta.getAttributeModifiers(attributes[i])
                : item.getType().getDefaultAttributeModifiers(slot).get(attributes[i]);
            if (modifiers == null) modifiers = List.of();
            if (modifiers.isEmpty() && !(i == 0 && !gear.armor() || i == 1 && gear.armor() || i == 4 && !gear.armor())) continue;
            double base = switch (i) { case 0 -> 1; case 3 -> 20; case 4 -> 4; case 5 -> .1; case 6 -> 4.5; case 7 -> 1; default -> 0; };
            double added = modifiers.stream().filter(m -> m.getOperation() == AttributeModifier.Operation.ADD_NUMBER)
                .mapToDouble(AttributeModifier::getAmount).sum();
            double scalar = modifiers.stream().filter(m -> m.getOperation() == AttributeModifier.Operation.ADD_SCALAR)
                .mapToDouble(AttributeModifier::getAmount).sum();
            double value = (base + added) * (1 + scalar);
            for (var modifier : modifiers) if (modifier.getOperation() == AttributeModifier.Operation.MULTIPLY_SCALAR_1)
                value *= 1 + modifier.getAmount();
            stats.add(labels[i] + " " + String.format(Locale.ROOT, i == 5 ? "%.3f" : "%.2f", value));
        }
        if (meta instanceof org.bukkit.inventory.meta.Damageable damageable && !CombatStats.eligible(item))
            stats.add("최대 내구도 " + (damageable.hasMaxDamage() ? damageable.getMaxDamage() : item.getType().getMaxDurability()));
        stats.add("인챈트 슬롯 " + gearValue(item, "lines", 3));
        return stats;
    }

    @EventHandler
    public void upgradeDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof UpgradeDesk) event.setCancelled(true);
    }

    @EventHandler
    public void upgradeClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof UpgradeDesk menu)) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (!(event.getWhoClicked() instanceof Player player) || !event.isLeftClick()
            || slot < 0 || slot >= 54 || menu.getInventory().getItem(slot) == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!town(player) || player.getOpenInventory().getTopInventory().getHolder() != menu) return;
            if (slot == 49) { openForgeDesk(player, -1, -1, false, null); return; }
            if (menu.confirm()) {
                if (slot == 47) openUpgrade(player, menu.page(), menu.selected(), false, menu.snapshot(), null);
                else if (slot == 46) {
                    UpgradeResult result = applyUpgrade(player, menu.selected(), menu.snapshot());
                    openUpgrade(player, menu.page(), menu.selected(), false,
                        result == null ? menu.snapshot() : result.item(), result);
                }
                return;
            }
            if (slot < 9) openUpgrade(player, menu.page(), menu.slots().get(menu.page() * 9 + slot), false, null, null);
            else if (slot == 48 || slot == 50)
                openUpgrade(player, menu.page() + (slot == 48 ? -1 : 1), menu.selected(), false, menu.snapshot(), null);
            else if (slot == 45) openUpgrade(player, menu.page(), menu.selected(), true, menu.snapshot(), null);
        });
    }
    private boolean pickaxe(ItemStack item) { return Enchants.pickaxe(item); }

    private static double pickSum(List<Enchants.Line> lines, String kind) {
        double total = 0;
        for (var l : lines) {
            var o = Enchants.byId(l.opt());
            if (o != null && o.kind().equals(kind)) total += Enchants.value(o, l.grade());
        }
        return total;
    }

    private void updateGear(ItemStack item, Gear gear) {
        var meta = item.getItemMeta();
        int level = gearValue(item, "level", 0);
        int lines = gearValue(item, "lines", 3);
        var slot = gear.armor() ? item.getType().getEquipmentSlot() : EquipmentSlot.HAND;
        var group = slot.getGroup();
        if (!meta.hasAttributeModifiers()) {
            item.getType().getDefaultAttributeModifiers(slot).entries()
                .forEach(entry -> {
                    var original = entry.getValue();
                    if (gear.armor() && (entry.getKey() == Attribute.GENERIC_ARMOR || entry.getKey() == Attribute.GENERIC_ARMOR_TOUGHNESS)) return; // defense comes from CombatStats
                    var key = gearKey("base_" + entry.getKey().name().toLowerCase(Locale.ROOT) + "_" + original.getKey().getKey());
                    meta.addAttributeModifier(entry.getKey(), new AttributeModifier(key, original.getAmount(), original.getOperation(), original.getSlotGroup()));
                });
        }
        if (gear.armor()) {
            // strip vanilla armor/toughness (old items) and pin an explicit zero modifier so material defaults never apply
            meta.removeAttributeModifier(Attribute.GENERIC_ARMOR);
            meta.removeAttributeModifier(Attribute.GENERIC_ARMOR_TOUGHNESS);
            meta.addAttributeModifier(Attribute.GENERIC_ARMOR, new AttributeModifier(gearKey("base_zero_armor"), 0, AttributeModifier.Operation.ADD_NUMBER, group));
        }
        var primary = gear.armor() ? Attribute.GENERIC_ARMOR : Attribute.GENERIC_ATTACK_DAMAGE;
        Attribute[] options = {primary, Attribute.GENERIC_MAX_HEALTH,
            gear.armor() ? Attribute.GENERIC_MOVEMENT_SPEED : Attribute.GENERIC_ATTACK_SPEED};
        String[] labels = {gear.armor() ? "방어력" : "공격력", "최대 체력", gear.armor() ? "이동 속도" : "공격 속도"};
        double[] totals = {0, 0, 0}; // gear stats / enhancement / enchant effects live in CombatStats (no per-item attribute bonus)
        List<String> lore = new ArrayList<>();
        // migrate legacy smith_rolls -> smith_enchant (all lines 레어)
        var tags = meta.getPersistentDataContainer();
        var enchantLines = Enchants.read(item);
        if (tags.has(gearKey("rolls"), PersistentDataType.STRING)) {
            int grade0 = Enchants.grade(item);
            Enchants.write(meta, enchantLines, enchantLines.isEmpty() ? 0 : grade0);
        }
        int itemGrade = Enchants.grade(item);
        lore.add("§7잠재 슬롯 " + lines + "개");
        lore.add(enchantLines.isEmpty() ? "§5인챈트" : "§5인챈트 §7등급 " + Enchants.COLORS[itemGrade] + "[" + Enchants.GRADES[itemGrade] + "]");
        for (var line : enchantLines) lore.add(Enchants.loreLine(line));
        for (int i = enchantLines.size(); i < lines; i++) lore.add("§7○ 빈 인챈트 슬롯");
        for (int i = 0; i < options.length; i++) {
            var key = gearKey("bonus_" + slot.name().toLowerCase(Locale.ROOT) + "_" + i);
            var existing = meta.hasAttributeModifiers() ? meta.getAttributeModifiers(options[i]) : null;
            if (existing != null) for (var modifier : List.copyOf(existing))
                if (modifier.getKey().equals(key)) meta.removeAttributeModifier(options[i], modifier);
            if (totals[i] > 0) meta.addAttributeModifier(options[i], new AttributeModifier(key,
                totals[i], AttributeModifier.Operation.ADD_NUMBER, group));
        }
        if (pickaxe(item)) {
            Attribute[] pickAttributes = {Attribute.PLAYER_BLOCK_INTERACTION_RANGE, Attribute.PLAYER_BLOCK_BREAK_SPEED};
            String[] pickOptions = {"reach", "speed"};
            for (int i = 0; i < pickAttributes.length; i++) {
                var attribute = pickAttributes[i];
                var key = gearKey("pick_" + i);
                var existing = meta.hasAttributeModifiers() ? meta.getAttributeModifiers(attribute) : null;
                if (existing != null) for (var modifier : List.copyOf(existing))
                    if (modifier.getKey().equals(key)) meta.removeAttributeModifier(attribute, modifier);
                double bonus = pickSum(enchantLines, pickOptions[i]);
                if (bonus > 0) meta.addAttributeModifier(attribute, new AttributeModifier(key, bonus,
                    AttributeModifier.Operation.ADD_NUMBER, group));
            }
            if (meta instanceof org.bukkit.inventory.meta.Damageable damageable) {
                int durability = (int) pickSum(enchantLines, "dura");
                damageable.setMaxDamage(durability > 0 ? item.getType().getMaxDurability() + durability : null);
                damageable.setDamage(Math.min(damageable.getDamage(), item.getType().getMaxDurability() + durability - 1));
            }
            if (pickSum(enchantLines, "glow") > 0) meta.setEnchantmentGlintOverride(true);
            else if (meta.hasEnchantmentGlintOverride()) meta.setEnchantmentGlintOverride(null);
        }
        for (var vanilla : List.copyOf(meta.getEnchants().keySet())) meta.removeEnchant(vanilla); // no vanilla enchants on our gear
        if (CombatStats.eligible(item)) makeUnbreakable(meta); // weapons/armor have no durability
        meta.setDisplayName("§b" + gear.name() + " +" + level);
        CombatStats.decorate(item, meta, lore);
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    private static void makeUnbreakable(org.bukkit.inventory.meta.ItemMeta meta) {
        meta.setUnbreakable(true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_UNBREAKABLE);
        if (meta instanceof org.bukkit.inventory.meta.Damageable d && d.hasDamage()) d.setDamage(0);
    }

    /** Runs every 5 ticks per player: only writes a slot back when something actually changed (re-setting items every pass resent inventory + attribute packets and stuttered the client). */
    void refreshCombatGear(Player player) {
        for (int i=0;i<player.getInventory().getSize();i++) {
            ItemStack item=player.getInventory().getItem(i);
            if (item == null || item.getType().isAir()) continue;
            ItemStack before=item.clone();
            CaptureSystem.refreshClub(item);
            if (item.hasItemMeta() && CombatStats.eligible(item)
                && (!item.getItemMeta().isUnbreakable() || (item.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable dm && dm.hasDamage()))) {
                var m = item.getItemMeta(); makeUnbreakable(m); item.setItemMeta(m);
            }
            if (item.hasItemMeta()) {
                ItemStack converted=convertOld(item);
                if(converted!=null)item=converted;
            }
            if(gearFor(item)!=null && !item.getItemMeta().hasCustomModelData())GearModels.apply(item);
            if (gearFor(item)!=null && (item.getItemMeta().getPersistentDataContainer().has(gearKey("rolls"), PersistentDataType.STRING)
                || hasVanillaEnchants(item) || CombatStats.needsMigration(item))) updateGear(item,gearFor(item));
            if (!item.equals(before)) player.getInventory().setItem(i,item);
        }
    }
    private Enchantment enhancement(ItemStack hand) {
        String type = hand.getType().name();
        return type.endsWith("_SWORD") ? Enchantment.SHARPNESS
            : hand.getType() == Material.BOW ? Enchantment.POWER
            : type.endsWith("_PICKAXE") || type.endsWith("_AXE") || type.endsWith("_SHOVEL") ? Enchantment.EFFICIENCY
            : type.matches(".*_(HELMET|CHESTPLATE|LEGGINGS|BOOTS)") ? Enchantment.PROTECTION : null;
    }

    private static final String ALCHEMY_TITLE = "삐삐서버 · 연금술사";
    private static final int ALCHEMY_PAGE = 9;

    private record AlchemyDesk(int page, int selected, int locks, boolean confirm, ItemStack snapshot,
                               List<Integer> slots, Inventory[] view) implements InventoryHolder {
        @Override public Inventory getInventory() { return view[0]; }
    }

    private boolean installAlchemy(Player player) {
        if (!town(player) || !player.isOp()) return tell(player, "공동 마을에서 관리자만 설치할 수 있습니다.");
        return tell(player, installAlchemyRoom(player.getWorld()));
    }

    String installAlchemyRoom(World world) {
        world.getChunkAt(0, -2).load();
        if (npcs.exists(world, "alchemist", new Location(world, .5, 64, -17.5)))
            return "이미 연금술사가 설치되어 있습니다.";
        for (int x = -6; x <= 6; x++) for (int z = -21; z <= -11; z++)
            for (int y = 64; y <= 69; y++) if (!world.getBlockAt(x, y, z).getType().isAir())
                return "중앙 북쪽 설치 구역에 기존 블록이 있습니다. 좌표 X -6~6, Y 64~69, Z -21~-11을 확인하세요.";
        for (int x = -6; x <= 6; x++) for (int z = -21; z <= -11; z++) {
            for (int y = 64; y <= 69; y++) {
                boolean entrance = z == -11 && Math.abs(x) <= 1 && y <= 66;
                if (y == 69 || !entrance && (Math.abs(x) == 6 || z == -21 || z == -11))
                    world.getBlockAt(x, y, z).setType(y == 69 ? Material.DEEPSLATE_TILES : Material.POLISHED_DEEPSLATE, false);
            }
        }
        world.getBlockAt(0, 68, -16).setType(Material.SHROOMLIGHT);
        world.getBlockAt(2, 64, -18).setType(Material.BREWING_STAND);
        world.spawn(new Location(world, .5, 64, -17.5), org.bukkit.entity.Villager.class, npc -> {
            npc.setCustomName("연금술사");
            npc.setCustomNameVisible(true);
            npc.setProfession(org.bukkit.entity.Villager.Profession.CLERIC);
            npc.setAI(false);
            npc.setInvulnerable(true);
            npc.setPersistent(true);
            npcs.register(npc, "alchemist");
        });
        return "마을 중앙 북쪽에 연금술사 방을 설치했습니다.";
    }

    @EventHandler(ignoreCancelled = true)
    public void alchemistNpc(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !npcs.matches(event.getRightClicked(), "alchemist")) return;
        event.setCancelled(true);
        openAlchemy(event.getPlayer(), 0, -1, 0, false, null);
    }

    /** Gold cost of one roll for an item with its current grade and number of locked lines. */
    private long alchemyGold(ItemStack item, int locks) {
        return (long) Enchants.cost(Enchants.grade(item), Integer.bitCount(locks));
    }

    private boolean openAlchemy(Player player, int page, int selected, int locks, boolean confirm, ItemStack snapshot) {
        if (!town(player) || !plugin.canTravel(player)) return tell(player, "공동 마을에서 연금술을 사용하세요.");
        var bag = player.getInventory();
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < bag.getSize(); i++) {
            ItemStack item = bag.getItem(i);
            if (item != null && gearFor(item) != null) slots.add(i);
        }
        page = Math.clamp(page, 0, Math.max(0, (slots.size() - 1) / ALCHEMY_PAGE));
        ItemStack chosen = slots.contains(selected) ? bag.getItem(selected) : null;
        if (chosen == null || snapshot != null && !snapshot.equals(chosen)) {
            selected = -1;
            chosen = null;
            locks = 0;
            confirm = false;
        }
        var menu = new AlchemyDesk(page, selected, locks, confirm, chosen == null ? null : chosen.clone(), slots, new Inventory[1]);
        Inventory view = Bukkit.createInventory(menu, 54, ALCHEMY_TITLE);
        menu.view()[0] = view;
        for (int i = 0; i < ALCHEMY_PAGE && page * ALCHEMY_PAGE + i < slots.size(); i++)
            view.setItem(i, bag.getItem(slots.get(page * ALCHEMY_PAGE + i)).clone());
        if (page > 0) view.setItem(48, plugin.statusIcon(Material.ARROW, "이전"));
        if ((page + 1) * ALCHEMY_PAGE < slots.size()) view.setItem(50, plugin.statusIcon(Material.ARROW, "다음"));
        view.setItem(49, plugin.statusIcon(Material.BARRIER, "닫기"));
        if (chosen != null) {
            view.setItem(22, chosen.clone());
            var rolls = Enchants.read(chosen);
            int grade = Enchants.grade(chosen);
            for (int i = 0; i < Math.min(7, gearValue(chosen, "lines", 3)); i++) {
                boolean filled = i < rolls.size();
                boolean locked = (locks & (1 << i)) != 0;
                view.setItem(27 + i, plugin.statusIcon(filled ? Material.EMERALD : Material.GRAY_DYE,
                    filled ? Enchants.loreLine(rolls.get(i)) : "빈 인챈트 슬롯"));
                if (filled) view.setItem(36 + i, plugin.statusIcon(locked ? Material.GOLD_NUGGET : Material.TRIPWIRE_HOOK,
                    locked ? "잠금 해제" : "옵션 잠금"));
            }
            view.setItem(44, plugin.statusIcon(Material.GOLD_INGOT, alchemyGold(chosen, locks) + " 골드", "보유 " + gold(player).getScore()));
            view.setItem(43, plugin.statusIcon(Material.NETHER_STAR, Enchants.COLORS[grade] + "[" + Enchants.GRADES[grade] + "]",
                grade >= 3 ? "최고 등급" : "등급 상승 확률 " + Enchants.fmt(Enchants.GRADE_UP[grade]) + "% → " + Enchants.GRADES[grade + 1],
                "잠금 " + Integer.bitCount(locks) + "개"));
            view.setItem(45, plugin.statusIcon(Material.ENCHANTING_TABLE, confirm ? "확인" : "인챈트 하기"));
            if (confirm) view.setItem(46, plugin.statusIcon(Material.BARRIER, "취소"));
        }
        player.openInventory(view);
        return true;
    }

    @EventHandler
    public void alchemyDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof AlchemyDesk) event.setCancelled(true);
    }

    @EventHandler
    public void alchemyClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof AlchemyDesk menu)) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (!(event.getWhoClicked() instanceof Player player) || !event.isLeftClick() || slot < 0 || slot >= 54
            || menu.getInventory().getItem(slot) == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!town(player) || player.getOpenInventory().getTopInventory().getHolder() != menu) return;
            if (slot == 49) { player.closeInventory(); return; }
            if (menu.confirm() && slot != 45 && slot != 46) return;
            int selected = menu.selected(), locks = menu.locks(), page = menu.page();
            ItemStack snapshot = menu.snapshot();
            boolean confirm = false;
            if (slot < ALCHEMY_PAGE) {
                selected = menu.slots().get(page * ALCHEMY_PAGE + slot);
                snapshot = menu.getInventory().getItem(slot);
                locks = 0;
            } else if (slot == 48 || slot == 50) page += slot == 48 ? -1 : 1;
            else if (slot >= 36 && slot <= 42) {
                int next = locks ^ (1 << (slot - 36));
                if (Integer.bitCount(next) >= Math.max(1, Enchants.read(snapshot).size())) {
                    tell(player, "인챈트 슬롯을 최소 한 칸 열어두세요.");
                } else locks = next;
            } else if (slot == 45) {
                if (!menu.confirm()) confirm = true;
                else snapshot = applyAlchemy(player, menu);
            } else if (slot != 46) return;
            if (player.getOpenInventory().getTopInventory().getHolder() == menu) openAlchemy(player, page, selected, locks, confirm, snapshot);
        });
    }

    private ItemStack applyAlchemy(Player player, AlchemyDesk menu) {
        ItemStack item = player.getInventory().getItem(menu.selected());
        ItemStack snapshot = menu.snapshot();
        if (!snapshot.equals(item)) {
            tell(player, "장비가 바뀌었습니다. 다시 선택하세요.");
            return snapshot;
        }
        if (gearFor(item) == null) return snapshot;
        Score score = gold(player);
        long cost = alchemyGold(item, menu.locks());
        if (score.getScore() < cost) {
            returnTitle(player, "인챈트에 필요한 골드가 부족합니다. (" + cost + "골드)");
            return snapshot;
        }
        var rolls = Enchants.read(item);
        int count = gearValue(item, "lines", 3);
        int locks = menu.locks();
        if (rolls.isEmpty()) locks = 0; // nothing to lock on a fresh item
        if (Integer.bitCount(locks) >= count || (locks >>> rolls.size()) != 0) return snapshot;
        boolean pick = pickaxe(item);
        var random = java.util.concurrent.ThreadLocalRandom.current();
        int grade = Enchants.grade(item);
        boolean gradeUp = grade < 3 && random.nextDouble(100) < Enchants.GRADE_UP[grade];
        if (gradeUp) grade++;
        List<Enchants.Line> next = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if ((locks & (1 << i)) != 0) { next.add(rolls.get(i)); continue; }
            int lineGrade = i == 0 || grade == 0 || random.nextInt(100) < 1 ? grade : grade - 1;
            var pool = Enchants.pool(item, lineGrade);
            next.add(new Enchants.Line(pool.get(random.nextInt(pool.size())).id(), lineGrade));
        }
        ItemStack updated = item.clone();
        var meta = updated.getItemMeta();
        Enchants.write(meta, next, grade);
        updated.setItemMeta(meta);
        updateGear(updated, gearFor(updated));
        player.getInventory().setItem(menu.selected(), updated);
        score.setScore(score.getScore() - (int) cost);
        tell(player, gradeUp ? "인챈트를 부여했습니다. 등급 상승! " + Enchants.COLORS[grade] + "[" + Enchants.GRADES[grade] + "]"
            : "인챈트를 부여했습니다.");
        return updated;
    }

    private static final String INHERIT_TITLE = "삐삐서버 · 계승";
    private static final int INHERIT_PAGE = 9;

    private record InheritDesk(int page, int target, int source, ItemStack targetSnap, ItemStack sourceSnap,
                               boolean confirm, String result, List<Integer> slots, Inventory[] view) implements InventoryHolder {
        @Override public Inventory getInventory() { return view[0]; }
    }

    /** Grade index after 계승: 유니크(2)/레전더리(3) drop one step, 레어/에픽 stay. */
    private static int inheritGradeDrop(int grade) { return grade >= 2 ? grade - 1 : grade; }

    private long inheritCost(ItemStack target) { return 100_000L * (1L << CombatStats.tier(target)); }

    /** The target gear as it would be after inheriting the source's enchant (mutates neither item). */
    private ItemStack inheritPreview(ItemStack target, ItemStack source) {
        var lines = Enchants.read(source);
        List<Enchants.Line> dropped = new ArrayList<>();
        for (var line : lines) dropped.add(new Enchants.Line(line.opt(), inheritGradeDrop(line.grade())));
        int sourceLines = Math.max(gearValue(source, "lines", 3), lines.size());
        int level = Math.max(gearValue(target, "level", 0), gearValue(source, "level", 0) / 3);
        ItemStack preview = target.clone();
        var meta = preview.getItemMeta();
        Enchants.write(meta, dropped, inheritGradeDrop(Enchants.grade(source)));
        meta.getPersistentDataContainer().set(gearKey("lines"), PersistentDataType.INTEGER, sourceLines);
        meta.getPersistentDataContainer().set(gearKey("level"), PersistentDataType.INTEGER, level);
        meta.getPersistentDataContainer().remove(new NamespacedKey(plugin, "forge_attempts"));
        preview.setItemMeta(meta);
        updateGear(preview, gearFor(preview));
        return preview;
    }

    /** Reason the current 계승 장비/계승 재료 pair cannot be inherited, or null when valid. */
    private String inheritReason(Player player, ItemStack target, ItemStack source) {
        String kind = inheritKind(target);
        if (kind == null || !kind.equals(inheritKind(source)))
            return "같은 종류의 장비끼리만 계승할 수 있습니다. (검→검, 활→활, 지팡이→지팡이, 같은 부위 방어구, 곡괭이→곡괭이)";
        if (Enchants.read(source).isEmpty())
            return "계승 재료 장비에 계승할 인챈트가 없습니다.";
        if (inheritTier(target) <= inheritTier(source))
            return "계승 장비가 계승 재료 장비보다 높은 티어여야 합니다.";
        if (Enchants.enchanted(target))
            return "계승 장비에 이미 인챈트가 있어 계승할 수 없습니다.";
        long cost = inheritCost(target);
        if (gold(player).getScore() < cost)
            return "계승 비용 " + cost + "골드가 필요합니다.";
        return null;
    }

    private boolean openInherit(Player player, int page, int target, int source,
                                ItemStack targetSnap, ItemStack sourceSnap, boolean confirm, String result) {
        if (!town(player) || !plugin.canTravel(player)) return tell(player, "공동 마을 대장간에서 이용하세요.");
        var bag = player.getInventory();
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < bag.getSize(); i++) {
            ItemStack item = bag.getItem(i);
            if (item != null && item.getAmount() == 1 && inheritKind(item) != null && gearFor(item) != null
                && (CombatStats.eligible(item) || gearValue(item, "lines", 0) >= 3 || Enchants.enchanted(item))) slots.add(i);
        }
        page = Math.clamp(page, 0, Math.max(0, (slots.size() - 1) / INHERIT_PAGE));
        if (!slots.contains(target) || targetSnap == null || !targetSnap.equals(bag.getItem(target))) { target = -1; targetSnap = null; confirm = false; }
        if (!slots.contains(source) || sourceSnap == null || !sourceSnap.equals(bag.getItem(source))) { source = -1; sourceSnap = null; confirm = false; }
        if (target >= 0 && target == source) { source = -1; sourceSnap = null; confirm = false; }
        var menu = new InheritDesk(page, target, source, targetSnap == null ? null : targetSnap.clone(),
            sourceSnap == null ? null : sourceSnap.clone(), confirm, result, slots, new Inventory[1]);
        Inventory view = Bukkit.createInventory(menu, 54, INHERIT_TITLE);
        menu.view()[0] = view;
        for (int i = 0; i < INHERIT_PAGE && page * INHERIT_PAGE + i < slots.size(); i++)
            view.setItem(i, bag.getItem(slots.get(page * INHERIT_PAGE + i)).clone());
        if (page > 0) view.setItem(48, plugin.statusIcon(Material.ARROW, "이전"));
        if ((page + 1) * INHERIT_PAGE < slots.size()) view.setItem(50, plugin.statusIcon(Material.ARROW, "다음"));
        view.setItem(49, plugin.statusIcon(Material.ARROW, "대장간으로"));
        if (targetSnap != null) view.setItem(20, targetSnap.clone());
        if (sourceSnap != null) view.setItem(24, sourceSnap.clone());
        if (targetSnap != null && sourceSnap != null) {
            ItemStack preview = inheritPreview(targetSnap, sourceSnap);
            var previewLines = Enchants.read(preview);
            int lineCount = gearValue(preview, "lines", 3);
            view.setItem(22, preview.clone());
            for (int i = 0; i < 7 && i < lineCount; i++)
                view.setItem(27 + i, i < previewLines.size()
                    ? plugin.statusIcon(Material.EMERALD, Enchants.loreLine(previewLines.get(i)))
                    : plugin.statusIcon(Material.GRAY_DYE, "빈 인챈트 슬롯"));
            int sourceGrade = Enchants.grade(sourceSnap);
            int resultGrade = inheritGradeDrop(sourceGrade);
            int sourceLevel = gearValue(sourceSnap, "level", 0);
            int resultLevel = Math.max(gearValue(targetSnap, "level", 0), sourceLevel / 3);
            view.setItem(43, plugin.statusIcon(Material.NETHER_STAR, Enchants.COLORS[resultGrade] + "[" + Enchants.GRADES[resultGrade] + "]",
                Enchants.GRADES[sourceGrade] + " → " + Enchants.GRADES[resultGrade],
                "강화 +" + sourceLevel + " → +" + resultLevel));
            view.setItem(34, plugin.statusIcon(Material.WRITABLE_BOOK, "계승 전 능력치", gearStats(targetSnap).toArray(String[]::new)));
            view.setItem(35, plugin.statusIcon(Material.WRITABLE_BOOK, "계승 후 능력치", gearStats(preview).toArray(String[]::new)));
            view.setItem(44, plugin.statusIcon(Material.GOLD_INGOT, inheritCost(targetSnap) + " 골드",
                "보유 " + gold(player).getScore() + " 골드"));
            String reason = inheritReason(player, targetSnap, sourceSnap);
            if (reason != null) view.setItem(42, plugin.statusIcon(Material.BARRIER, reason));
            else {
                view.setItem(45, plugin.statusIcon(Material.ANVIL, "계승 하기"));
                if (confirm) {
                    view.setItem(46, plugin.statusIcon(Material.LIME_DYE, "계승 확인"));
                    view.setItem(47, plugin.statusIcon(Material.BARRIER, "취소"));
                }
            }
        }
        if (result != null) view.setItem(41, plugin.statusIcon(Material.LIME_DYE, result));
        player.openInventory(view);
        return true;
    }

    /** Performs the 계승 after re-validating snapshots and gold; returns an error message or null on success. */
    private String performInherit(Player player, InheritDesk menu) {
        if (menu.target() < 0 || menu.source() < 0 || menu.targetSnap() == null || menu.sourceSnap() == null)
            return "계승할 장비와 재료를 모두 선택하세요.";
        var bag = player.getInventory();
        ItemStack target = bag.getItem(menu.target()), source = bag.getItem(menu.source());
        if (!menu.targetSnap().equals(target) || !menu.sourceSnap().equals(source))
            return "장비가 바뀌었습니다. 다시 선택하세요.";
        String reason = inheritReason(player, target, source);
        if (reason != null) return reason;
        long cost = inheritCost(target);
        Score score = gold(player);
        if (score.getScore() < cost) return "계승 비용 " + cost + "골드가 필요합니다.";
        ItemStack updatedTarget = inheritPreview(target, source);
        bag.setItem(menu.target(), updatedTarget);
        bag.setItem(menu.source(), null);
        score.setScore(score.getScore() - (int) cost);
        return null;
    }

    @EventHandler
    public void inheritDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof InheritDesk) event.setCancelled(true);
    }

    @EventHandler
    public void inheritClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof InheritDesk menu)) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (!(event.getWhoClicked() instanceof Player player) || slot < 0 || slot >= 54
            || menu.getInventory().getItem(slot) == null) return;
        int button = event.isLeftClick() ? 0 : event.isRightClick() ? 1 : -1;
        if (button < 0) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!town(player) || player.getOpenInventory().getTopInventory().getHolder() != menu) return;
            if (slot == 49) { openForgeDesk(player, -1, -1, false, null); return; }
            if (menu.confirm()) {
                if (slot == 47) openInherit(player, menu.page(), menu.target(), menu.source(), menu.targetSnap(), menu.sourceSnap(), false, null);
                else if (slot == 46) {
                    String error = performInherit(player, menu);
                    openInherit(player, menu.page(), -1, -1, null, null, false,
                        error == null ? "인챈트 등급과 옵션을 계승 장비로 계승했습니다. (계승 재료는 사라집니다)" : error);
                }
                return;
            }
            int target = menu.target(), source = menu.source(), page = menu.page();
            ItemStack targetSnap = menu.targetSnap(), sourceSnap = menu.sourceSnap();
            if (slot < INHERIT_PAGE) {
                int bagSlot = menu.slots().get(page * INHERIT_PAGE + slot);
                ItemStack snap = menu.getInventory().getItem(slot);
                if (button == 0) {
                    target = bagSlot;
                    targetSnap = snap;
                    if (source == bagSlot) { source = -1; sourceSnap = null; }
                } else {
                    source = bagSlot;
                    sourceSnap = snap;
                    if (target == bagSlot) { target = -1; targetSnap = null; }
                }
            } else if (slot == 48 || slot == 50) page += slot == 48 ? -1 : 1;
            else if (slot == 20 && button == 0) { target = -1; targetSnap = null; }
            else if (slot == 24 && button == 0) { source = -1; sourceSnap = null; }
            else if (slot == 45) { openInherit(player, page, target, source, targetSnap, sourceSnap, true, null); return; }
            else return;
            openInherit(player, page, target, source, targetSnap, sourceSnap, false, null);
        });
    }

    /** Kind key for 계승 (same kind required), or null if not inheritable. */
    private String inheritKind(ItemStack item) {
        String type = item.getType().name();
        if (item.getType() == Material.FISHING_ROD) return null;
        if (type.endsWith("_SWORD")) return "sword";
        if (item.getType() == Material.BOW) return "bow";
        if (item.getType() == Material.BLAZE_ROD) return "staff";
        if (type.endsWith("_PICKAXE")) return "pickaxe";
        if (type.endsWith("_AXE")) return "axe";
        for (String slot : new String[]{"HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"})
            if (type.endsWith("_" + slot)) return slot;
        return null;
    }

    /** Tier rank: the "N티어" crafting label if present, otherwise material rank (pickaxes etc). */
    private int inheritTier(ItemStack item) {
        String label = item.getItemMeta().getPersistentDataContainer().get(gearKey("catalog"), PersistentDataType.STRING);
        if (label != null) {
            var m = java.util.regex.Pattern.compile("^(\\d+)티어").matcher(label);
            if (m.find()) return Integer.parseInt(m.group(1)) * 10;
        }
        String type = item.getType().name();
        int rank = type.startsWith("NETHERITE_") ? 4 : type.startsWith("DIAMOND_") ? 3 : type.startsWith("IRON_") ? 2
            : type.startsWith("GOLDEN_") || type.startsWith("CHAINMAIL_") || type.startsWith("STONE_") ? 1 : 0;
        return rank * 10;
    }

    private boolean nearby(Player a, Player b) {
        return a.getWorld().equals(b.getWorld()) && a.getLocation().distanceSquared(b.getLocation()) <= 36;
    }

    private boolean trade(Player player, String[] args) {
        UUID buyer = player.getUniqueId();
        if (args.length == 4 && args[1].equals("offer")) {
            Player target = Bukkit.getPlayerExact(args[2]);
            int price;
            try { price = Integer.parseInt(args[3]); }
            catch (NumberFormatException ex) { return tell(player, "골드를 정수로 입력하세요."); }
            var inv = player.getInventory();
            ItemStack item = inv.getItemInMainHand();
            if (target == null || target.equals(player) || !nearby(player, target) || price < 1 || item.getType().isAir())
                return tell(player, "거래 상대와 6블록 안에서 양수 가격을 입력하고 팔 물품을 손에 드세요.");
            trades.put(target.getUniqueId(), new Trade(buyer, inv.getHeldItemSlot(), item.clone(), price, System.currentTimeMillis() + 60_000));
            target.sendMessage(player.getName() + "님의 거래 제안 · " + item.getType() + " ×" + item.getAmount()
                + " / " + price + "골드 · /pipilife trade accept 또는 decline");
            return tell(player, "거래를 제안했습니다. 60초 안에 수락할 수 있습니다.");
        }
        if (args.length == 2 && args[1].equals("decline")) {
            trades.remove(buyer);
            return tell(player, "받은 거래 제안을 삭제했습니다.");
        }
        if (args.length == 2 && args[1].equals("cancel")) {
            trades.values().removeIf(offer -> offer.seller().equals(buyer));
            return tell(player, "보낸 거래 제안을 취소했습니다.");
        }
        if (args.length == 2 && args[1].equals("accept")) {
            Trade offer = trades.remove(buyer);
            Player seller = offer == null ? null : Bukkit.getPlayer(offer.seller());
            if (seller == null || offer.expires() < System.currentTimeMillis() || !nearby(player, seller)
                || !offer.item().equals(seller.getInventory().getItem(offer.slot())))
                return tell(player, "제안이 만료됐거나 판매 물품 또는 거리가 달라졌습니다.");
            int slot = player.getInventory().firstEmpty();
            Score source = gold(seller), destination = gold(player);
            long total = (long) source.getScore() + offer.price();
            if (slot < 0 || destination.getScore() < offer.price() || total > Integer.MAX_VALUE)
                return tell(player, "가방 공간 또는 거래 가능 잔액이 부족합니다.");
            seller.getInventory().setItem(offer.slot(), null);
            player.getInventory().setItem(slot, offer.item());
            destination.setScore(destination.getScore() - offer.price());
            source.setScore((int) total);
            seller.sendMessage("거래 완료 · " + offer.price() + "골드를 받았습니다.");
            return tell(player, "거래 완료 · 물품을 받았습니다.");
        }
        return tell(player, "/pipilife trade <offer 이름 가격|accept|decline|cancel>");
    }

    // ---- 제련: destroy a crafted gear/tool, get half of its crafting materials back (rounded up) ----
    private static final class SmeltMenu implements InventoryHolder {
        final int selected; // player inventory slot, -1 = list
        final int[] slots = new int[54];
        Inventory inventory;
        SmeltMenu(int selected) { this.selected = selected; java.util.Arrays.fill(slots, -1); }
        @Override public Inventory getInventory() { return inventory; }
    }

    private static int half(int n) { return (n + 1) / 2; }

    private ItemStack smeltStack(Ingredient ingredient, int amount) {
        ItemStack stack = ingredient.modId() != null ? new ItemStack(ModItems.byId(ingredient.modId()))
            : ingredient.tag() != null ? taggedItem(ingredient.tag(), ingredient.label()) : new ItemStack(ingredient.material());
        stack.setAmount(amount);
        return stack;
    }

    /** Materials returned for smelting this item, or null when it is not a smeltable crafted item. */
    private List<ItemStack> smeltRefund(ItemStack item) {
        if (item == null || item.getType().isAir() || item.getAmount() != 1 || !item.hasItemMeta()) return null;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        List<ItemStack> out = new ArrayList<>();
        String catalog = pdc.get(gearKey("catalog"), PersistentDataType.STRING);
        if (catalog != null) {
            if (catalog.equals("철 곡괭이")) out.add(new ItemStack(Material.IRON_INGOT, half(5)));
            else if (catalog.equals("다이아몬드 곡괭이")) out.add(new ItemStack(Material.DIAMOND, half(5)));
            else {
                var matcher = java.util.regex.Pattern.compile("^(\\d+)티어 ").matcher(catalog);
                if (!matcher.find()) return null;
                int tier = Integer.parseInt(matcher.group(1));
                int plates = 2 + tier + (catalog.endsWith("갑옷") ? 2 : 0);
                ItemStack plate = taggedItem("plate", "강철");
                plate.setAmount(half(plates));
                out.add(plate);
                if (tier > 0) out.add(new ItemStack(Material.DIAMOND, half(tier)));
            }
            return out;
        }
        if (item.getType() == Material.FISHING_ROD && "§f낚시대".equals(item.getItemMeta().getDisplayName())) {
            out.add(new ItemStack(Material.OAK_LOG, half(5)));
            return out;
        }
        String tag = pdc.get(processed, PersistentDataType.STRING);
        String key = tag == null ? null : switch (tag) {
            case "club", "ironclub", "diamondclub", "amber_club", "hammer" -> tag;
            default -> null;
        };
        Recipe recipe = key == null ? null : RECIPES.get(key);
        if (recipe == null) return null;
        for (Ingredient ingredient : recipe.inputs()) {
            if (ingredient.level() > 0) continue;
            out.add(smeltStack(ingredient, half(ingredient.count())));
        }
        return out.isEmpty() ? null : out;
    }

    private static String stackLabel(ItemStack stack) {
        return stack.hasItemMeta() && stack.getItemMeta().hasDisplayName() ? stack.getItemMeta().getDisplayName()
            : stack.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private boolean smeltWarn(ItemStack item) {
        return gearValue(item, "level", 0) > 0 || Enchants.enchanted(item);
    }

    private boolean openSmelt(Player player, int selected) {
        if (!town(player)) return tell(player, "공동 마을 대장간에서 이용하세요.");
        SmeltMenu menu = new SmeltMenu(selected);
        Inventory inventory = Bukkit.createInventory(menu, 54, "삐삐서버 · 제련");
        menu.inventory = inventory;
        ItemStack[] bag = player.getInventory().getStorageContents();
        if (selected < 0) {
            int shown = 0;
            for (int i = 0; i < bag.length && shown < 45; i++) {
                List<ItemStack> refund = smeltRefund(bag[i]);
                if (refund == null) continue;
                ItemStack icon = bag[i].clone();
                var meta = icon.getItemMeta();
                List<String> lore = new ArrayList<>(meta.hasLore() ? meta.getLore() : List.of());
                lore.add("§6제련 시 돌려받는 재료");
                for (ItemStack r : refund) lore.add("§7 · " + stackLabel(r) + " ×" + r.getAmount());
                if (smeltWarn(bag[i])) lore.add("§c강화·인챈트가 적용된 장비입니다");
                meta.setLore(lore);
                icon.setItemMeta(meta);
                menu.slots[shown] = i;
                inventory.setItem(shown++, icon);
            }
            if (shown == 0) inventory.setItem(22, plugin.statusIcon(Material.BARRIER, "제련할 수 있는 제작 장비가 없습니다"));
            inventory.setItem(49, plugin.statusIcon(Material.ARROW, "돌아가기"));
        } else {
            ItemStack item = selected < bag.length ? bag[selected] : null;
            List<ItemStack> refund = smeltRefund(item);
            if (refund == null) { openSmelt(player, -1); return true; }
            inventory.setItem(13, item.clone());
            for (int i = 0; i < refund.size() && i < 5; i++) inventory.setItem(29 + i, refund.get(i).clone());
            if (smeltWarn(item)) inventory.setItem(22, plugin.statusIcon(Material.RED_STAINED_GLASS_PANE, "§c경고",
                "이 장비에는 강화 또는 인챈트가 적용되어 있습니다", "제련하면 모두 사라지고 복구할 수 없습니다"));
            inventory.setItem(40, plugin.statusIcon(Material.LIME_DYE, "제련 확인", "장비가 파괴되고 위 재료를 받습니다"));
            inventory.setItem(49, plugin.statusIcon(Material.ARROW, "취소"));
        }
        player.openInventory(inventory);
        return true;
    }

    @EventHandler
    public void smeltDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof SmeltMenu) event.setCancelled(true);
    }

    @EventHandler
    public void smeltClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SmeltMenu menu)) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (!(event.getWhoClicked() instanceof Player player) || !event.isLeftClick() || slot < 0 || slot >= 54
            || menu.inventory.getItem(slot) == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!town(player) || player.getOpenInventory().getTopInventory().getHolder() != menu) return;
            if (slot == 49) {
                if (menu.selected >= 0) openSmelt(player, -1);
                else openForgeDesk(player, -1, -1, false, null);
            } else if (menu.selected < 0) {
                if (slot < 45 && menu.slots[slot] >= 0) openSmelt(player, menu.slots[slot]);
            } else if (slot == 40) {
                ItemStack item = player.getInventory().getItem(menu.selected);
                List<ItemStack> refund = smeltRefund(item);
                if (refund == null) { tell(player, "제련할 장비가 바뀌었습니다."); openSmelt(player, -1); return; }
                player.getInventory().setItem(menu.selected, null);
                for (ItemStack extra : player.getInventory().addItem(refund.toArray(ItemStack[]::new)).values())
                    player.getWorld().dropItemNaturally(player.getLocation(), extra).setOwner(player.getUniqueId());
                tell(player, "제련 완료 · 재료를 돌려받았습니다.");
                openSmelt(player, -1);
            }
        });
    }

    private boolean heal(Player player) {
        if (town(player) || player.getWorld().getName().equals(VillageMaps.TUTORIAL)
            || player.getGameMode() != GameMode.SURVIVAL || player.isDead()) return tell(player, "야외 생존 모드에서 회복기를 사용하세요.");
        long now = System.currentTimeMillis();
        String key = user(player) + ".last-heal";
        if (now - plugin.data().getLong(key) < 45_000) return tell(player, "회복기는 45초마다 쓸 수 있습니다.");
        if (player.getHealth() >= player.getMaxHealth()) return tell(player, "이미 체력이 가득합니다.");
        if (!plugin.store(key, now)) return tell(player, "회복 상태를 저장하지 못했습니다.");
        player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + 6));
        return tell(player, "자기 회복기를 사용했습니다.");
    }

    private boolean party(Player player, String[] args) {
        UUID id = player.getUniqueId(), leader = membership.get(id);
        String action = args.length > 1 ? args[1] : "list";
        if (action.equals("create") && leader == null) {
            parties.put(id, new LinkedHashSet<>(List.of(id)));
            membership.put(id, id);
            return tell(player, "파티를 만들었습니다.");
        }
        if (action.equals("invite") && args.length == 3 && id.equals(leader)) {
            Player target = Bukkit.getPlayerExact(args[2]);
            if (target == null || membership.containsKey(target.getUniqueId()) || parties.get(id).size() >= 4)
                return tell(player, "초대할 수 없습니다. 최대 4명까지 가능합니다.");
            invites.put(target.getUniqueId(), new Invite(id, System.currentTimeMillis() + 60_000));
            target.sendMessage(player.getName() + "님의 파티 초대 · /pipilife party accept");
            return tell(player, "초대를 보냈습니다.");
        }
        if (action.equals("accept") && leader == null) {
            Invite invite = invites.remove(id);
            var members = invite == null ? null : parties.get(invite.leader());
            if (members == null || invite.expires() < System.currentTimeMillis() || members.size() >= 4)
                return tell(player, "유효한 파티 초대가 없습니다.");
            members.add(id);
            membership.put(id, invite.leader());
            return tell(player, "파티에 가입했습니다.");
        }
        if (action.equals("leave") && leader != null) {
            leave(id);
            return tell(player, "파티에서 나왔습니다.");
        }
        if (action.equals("list") && leader != null) {
            var names = parties.get(leader).stream().map(Bukkit::getOfflinePlayer)
                .map(p -> Objects.toString(p.getName(), p.getUniqueId().toString())).toList();
            return tell(player, "파티 · " + String.join(", ", names));
        }
        return tell(player, "/pipilife party <create|invite 이름|accept|leave|list>");
    }

    private void leave(UUID id) {
        UUID leader = membership.remove(id);
        if (leader == null) return;
        if (leader.equals(id)) {
            for (UUID member : parties.remove(leader)) {
                membership.remove(member);
                Player online = Bukkit.getPlayer(member);
                if (online != null && !member.equals(id)) online.sendMessage("파티가 해산되었습니다.");
            }
            invites.values().removeIf(invite -> invite.leader().equals(leader));
        } else parties.get(leader).remove(id);
    }

    @EventHandler public void quit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        invites.remove(id);
        trades.remove(id);
        trades.values().removeIf(offer -> offer.seller().equals(id));
        leave(id);
    }
}







