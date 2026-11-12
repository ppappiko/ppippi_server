package dev.pipi.life;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 조리대 (COOKING_TABLE): 재료 3개 + 조미료 1개로 요리를 만든다.
 * Chest-menu protocol (client: CookingScreen, title {@link #TITLE}):
 *  - slots 0..26: learned recipes (요리 수첩). Lore line 0 = "cookbook:<ing1>,<ing2>,<ing3>,<seasoning>", the rest is display text.
 *  - slot 52: last cook result (the dish). Lore line 0 = "serial:<n>" (changes on every cook), line 1 = message.
 *  - the client counts the player's ingredients itself and sends `pipilife cook <ing1> <ing2> <ing3> <seasoning> <0|1>`.
 */
final class Cooking implements Listener {
    static final String TITLE = "삐삐서버 · 조리대";
    private static final long COOLDOWN_MS = 3000;
    private static final int RESULT_SLOT = 52, BOOK_SLOTS = 27;
    private static final String FAILED = "failed_dish";

    /** Ingredient / seasoning id -> Korean label. */
    private static final Map<String, String> INGREDIENTS = new LinkedHashMap<>(), SEASONINGS = new LinkedHashMap<>();
    static {
        INGREDIENTS.put("cabbage", "양배추"); INGREDIENTS.put("carrot", "당근"); INGREDIENTS.put("straw", "짚");
        INGREDIENTS.put("salmon", "생 연어"); INGREDIENTS.put("potato", "감자");
        SEASONINGS.put("salt", "소금"); SEASONINGS.put("sugar", "설탕"); SEASONINGS.put("pepper", "후추"); SEASONINGS.put("soy_sauce", "간장");
    }
    private static final Map<String, String> BUFF_LABELS = Map.of("health", "체력 강화", "strength", "힘", "speed", "신속", "regeneration", "재생");
    private static final Map<String, String> BUFF_EFFECTS = Map.of("health", "health_boost", "strength", "strength", "speed", "speed", "regeneration", "regeneration");

    private record Buff(String type, int amplifier, int seconds) {}
    private record Recipe(String id, String name, List<String> ingredients, String seasoning, int petFood, Buff buff) {
        String key() { return key(ingredients, seasoning); }
        static String key(List<String> ingredients, String seasoning) {
            List<String> sorted = new ArrayList<>(ingredients);
            Collections.sort(sorted);
            return String.join(",", sorted) + "+" + seasoning;
        }
    }

    private static final class Menu implements InventoryHolder {
        Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    private final PipiLife plugin;
    private final NamespacedKey processed;
    private final Path recipeFile, bookFile;
    private final Map<String, Recipe> recipes = new LinkedHashMap<>(); // dish id -> recipe
    private final Map<String, Recipe> byKey = new HashMap<>();
    private final Map<UUID, Long> lastCook = new HashMap<>();
    private final Map<UUID, Integer> serial = new HashMap<>();
    private final YamlConfiguration book = new YamlConfiguration();
    private int failedPetFood = 5;
    private String failedName = "정체불명의 요리";

    Cooking(PipiLife plugin) {
        this.plugin = plugin;
        this.processed = new NamespacedKey(plugin, "processed_product");
        this.recipeFile = plugin.getDataFolder().toPath().resolve("cooking.yml");
        this.bookFile = plugin.getDataFolder().toPath().resolve("cookbook.yml");
        try {
            Files.createDirectories(recipeFile.getParent());
            if (!Files.exists(recipeFile)) Files.writeString(recipeFile, DEFAULT_YML, StandardCharsets.UTF_8);
            if (Files.exists(bookFile)) book.load(bookFile.toFile());
        } catch (Exception ex) {
            plugin.getLogger().severe("요리 데이터 로드 실패: " + ex);
        }
        loadRecipes();
    }

    private void loadRecipes() {
        recipes.clear();
        byKey.clear();
        YamlConfiguration yml = new YamlConfiguration();
        try { yml.load(recipeFile.toFile()); } catch (Exception ex) { plugin.getLogger().severe("cooking.yml 로드 실패: " + ex); return; }
        failedPetFood = yml.getInt("failed-dish.pet-food", 5);
        failedName = yml.getString("failed-dish.name", failedName);
        ConfigurationSection section = yml.getConfigurationSection("recipes");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            ConfigurationSection r = section.getConfigurationSection(id);
            if (r == null) continue;
            List<String> ingredients = r.getStringList("ingredients");
            String seasoning = r.getString("seasoning", "");
            if (ingredients.size() != 3 || !INGREDIENTS.keySet().containsAll(ingredients) || !SEASONINGS.containsKey(seasoning)
                || ModItems.byIdOrNull(id) == null) {
                plugin.getLogger().warning("cooking.yml 레시피 무시됨: " + id);
                continue;
            }
            Buff buff = null;
            ConfigurationSection b = r.getConfigurationSection("buff");
            if (b != null && BUFF_EFFECTS.containsKey(b.getString("type", "")))
                buff = new Buff(b.getString("type"), Math.max(0, b.getInt("amplifier", 0)), Math.max(1, b.getInt("seconds", 60)));
            Recipe recipe = new Recipe(id, r.getString("name", id), List.copyOf(ingredients), seasoning, Math.max(0, r.getInt("pet-food", 0)), buff);
            recipes.put(id, recipe);
            byKey.put(recipe.key(), recipe);
        }
    }

    // ---------------------------------------------------------------- dishes

    boolean isDish(String tag) { return tag != null && (tag.equals(FAILED) || recipes.containsKey(tag)); }

    boolean dishMatches(ItemStack item, String tag) {
        Material material = ModItems.byIdOrNull(tag);
        return material != null && item.getType() == material;
    }

    int petFood(String tag) {
        if (tag.equals(FAILED)) return failedPetFood;
        Recipe recipe = recipes.get(tag);
        return recipe == null ? 0 : recipe.petFood();
    }

    private String dishName(String id) { return id.equals(FAILED) ? failedName : recipes.get(id).name(); }

    private List<String> dishLore(String id) {
        List<String> lore = new ArrayList<>();
        int food = petFood(id);
        if (food > 0) lore.add("펫 먹이 · 포만감 +" + food);
        Recipe recipe = recipes.get(id);
        if (recipe != null && recipe.buff() != null)
            lore.add("우클릭하여 먹기 · " + buffText(recipe.buff()));
        if (lore.isEmpty()) lore.add("삐삐서버 전용 요리");
        return lore;
    }

    private static String roman(int level) { return switch (level) { case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V"; default -> Integer.toString(level); }; }

    private static String buffText(Buff buff) {
        return BUFF_LABELS.get(buff.type()) + " " + roman(buff.amplifier() + 1) + " " + buff.seconds() + "초";
    }

    private ItemStack dish(String id, int amount) {
        Material material = ModItems.byIdOrNull(id);
        if (material == null) material = Material.BREAD;
        ItemStack item = plugin.statusIcon(material, dishName(id), dishLore(id).toArray(String[]::new));
        item.setAmount(amount);
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(processed, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    // ---------------------------------------------------------------- cookbook

    private List<String> learned(Player player) {
        List<String> ids = new ArrayList<>(book.getStringList(player.getUniqueId().toString()));
        ids.removeIf(id -> !recipes.containsKey(id));
        return ids;
    }

    private void learn(Player player, String id) {
        List<String> ids = book.getStringList(player.getUniqueId().toString());
        if (ids.contains(id)) return;
        ids = new ArrayList<>(ids);
        ids.add(id);
        book.set(player.getUniqueId().toString(), ids);
        try { book.save(bookFile.toFile()); } catch (IOException ex) { plugin.getLogger().severe("요리 수첩 저장 실패: " + ex); }
    }

    private ItemStack bookIcon(Recipe recipe) {
        List<String> lore = new ArrayList<>();
        lore.add("cookbook:" + String.join(",", recipe.ingredients()) + "," + recipe.seasoning());
        List<String> names = new ArrayList<>();
        for (String ing : recipe.ingredients()) names.add(INGREDIENTS.get(ing));
        lore.add("재료: " + String.join(" + ", names) + " + " + SEASONINGS.get(recipe.seasoning()));
        lore.addAll(dishLore(recipe.id()));
        return plugin.displayIcon(dish(recipe.id(), 1), recipe.name(), lore.toArray(String[]::new));
    }

    private void fill(Player player, Inventory inventory) {
        for (int i = 0; i < BOOK_SLOTS; i++) inventory.setItem(i, null);
        int slot = 0;
        for (String id : learned(player)) {
            if (slot >= BOOK_SLOTS) break;
            inventory.setItem(slot++, bookIcon(recipes.get(id)));
        }
    }

    // ---------------------------------------------------------------- menu

    void open(Player player) {
        Menu holder = new Menu();
        holder.inventory = Bukkit.createInventory(holder, 54, TITLE);
        fill(player, holder.inventory);
        player.openInventory(holder.inventory);
    }

    @EventHandler public void click(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) event.setCancelled(true);
    }

    @EventHandler public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) event.setCancelled(true);
    }

    @EventHandler public void quit(PlayerQuitEvent event) {
        lastCook.remove(event.getPlayer().getUniqueId());
        serial.remove(event.getPlayer().getUniqueId());
    }

    // ---------------------------------------------------------------- cooking

    private static Material materialOf(String id) {
        return switch (id) {
            case "cabbage" -> ModItems.byIdOrNull("cabbage");
            case "carrot" -> ModItems.byIdOrNull("pet_carrot");
            case "straw" -> ModItems.byIdOrNull("straw");
            case "salmon" -> ModItems.byIdOrNull("raw_salmon");
            case "potato" -> Material.POTATO;
            default -> SEASONINGS.containsKey(id) ? ModItems.byIdOrNull(id) : null;
        };
    }

    private static int count(Player player, Material material) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getStorageContents())
            if (stack != null && stack.getType() == material) total += stack.getAmount();
        return total;
    }

    private static void take(Player player, Material material, int amount) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getStorageContents().length && amount > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType() != material) continue;
            int used = Math.min(amount, stack.getAmount());
            amount -= used;
            if (used == stack.getAmount()) inventory.setItem(slot, null);
            else stack.setAmount(stack.getAmount() - used);
        }
    }

    /** `pipilife cook <ing1> <ing2> <ing3> <seasoning> <0|1>` */
    boolean command(Player player, String[] args) {
        if (args.length != 6 || !(player.getOpenInventory().getTopInventory().getHolder() instanceof Menu menu)) return true;
        if (!plugin.nearFacility(player, Facility.COOKING_TABLE)) { player.closeInventory(); return true; }
        List<String> ingredients = List.of(args[1], args[2], args[3]);
        String seasoning = args[4];
        if (!INGREDIENTS.keySet().containsAll(ingredients) || !SEASONINGS.containsKey(seasoning) || !args[5].equals("0") && !args[5].equals("1")) return true;
        boolean success = args[5].equals("1");
        long now = System.currentTimeMillis();
        Long last = lastCook.get(player.getUniqueId());
        if (last != null && now - last < COOLDOWN_MS) {
            player.sendMessage("조리는 " + COOLDOWN_MS / 1000 + "초에 한 번만 할 수 있습니다.");
            return true;
        }
        Map<Material, Integer> need = new LinkedHashMap<>();
        for (String id : ingredients) need.merge(materialOf(id), 1, Integer::sum);
        need.merge(materialOf(seasoning), 1, Integer::sum);
        if (need.containsKey(null)) { player.sendMessage("요리 재료 아이템을 찾을 수 없습니다. 모드를 확인하세요."); return true; }
        List<String> lacking = new ArrayList<>();
        for (var entry : need.entrySet()) {
            int missing = entry.getValue() - count(player, entry.getKey());
            if (missing <= 0) continue;
            String label = null;
            for (var id : INGREDIENTS.keySet()) if (materialOf(id) == entry.getKey()) label = INGREDIENTS.get(id);
            for (var id : SEASONINGS.keySet()) if (materialOf(id) == entry.getKey()) label = SEASONINGS.get(id);
            lacking.add((label == null ? entry.getKey().name() : label) + " " + missing + "개");
        }
        if (!lacking.isEmpty()) { player.sendTitle("§c재료 부족", String.join(" · ", lacking) + " 부족", 5, 40, 10); return true; }
        if (ModItems.byIdOrNull(FAILED) == null) { player.sendMessage("요리 아이템을 찾을 수 없습니다. 모드를 확인하세요."); return true; }
        lastCook.put(player.getUniqueId(), now);
        need.forEach((material, amount) -> take(player, material, amount));
        Recipe recipe = byKey.get(Recipe.key(ingredients, seasoning));
        String result;
        String note;
        if (success && recipe != null) {
            result = recipe.id();
            boolean fresh = !book.getStringList(player.getUniqueId().toString()).contains(result);
            learn(player, result);
            note = fresh ? "새 레시피를 배웠습니다: " + recipe.name() : recipe.name() + " 완성!";
            if (fresh) fill(player, menu.inventory);
        } else if (success) {
            result = FAILED;
            note = "알맞은 레시피가 아닌 것 같습니다... " + failedName + "이(가) 만들어졌습니다.";
        } else {
            result = FAILED;
            note = "요리에 실패했습니다. " + failedName + "이(가) 만들어졌습니다.";
        }
        ItemStack output = dish(result, 1);
        for (ItemStack left : player.getInventory().addItem(output).values())
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        player.sendMessage(note);
        int n = serial.merge(player.getUniqueId(), 1, Integer::sum);
        menu.inventory.setItem(RESULT_SLOT, plugin.displayIcon(output, dishName(result), "serial:" + n, note));
        return true;
    }

    // ---------------------------------------------------------------- eating

    @EventHandler(ignoreCancelled = true)
    public void eat(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.useItemInHand() == org.bukkit.event.Event.Result.DENY) return;
        var clicked = event.getClickedBlock();
        Player player = event.getPlayer();
        if (clicked != null && clicked.getType().isInteractable() && !player.isSneaking()) return;
        ItemStack hand = event.getItem();
        if (hand == null || hand.getType().isAir() || !hand.hasItemMeta()) return;
        String tag = hand.getItemMeta().getPersistentDataContainer().get(processed, PersistentDataType.STRING);
        Recipe recipe = tag == null ? null : recipes.get(tag);
        if (recipe == null || recipe.buff() == null || !dishMatches(hand, tag)) return;
        PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(BUFF_EFFECTS.get(recipe.buff().type())));
        if (type == null) return;
        event.setCancelled(true);
        hand.setAmount(hand.getAmount() - 1);
        player.addPotionEffect(new PotionEffect(type, recipe.buff().seconds() * 20, recipe.buff().amplifier()));
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EAT, 1f, 1f);
        player.sendMessage(recipe.name() + "을(를) 먹었습니다. " + buffText(recipe.buff()));
    }

    private static final String DEFAULT_YML = """
        # 조리대 레시피 설정 (서버 재시작 시 적용)
        # 재료 id: cabbage(양배추) carrot(당근) straw(짚) salmon(생 연어) potato(감자)
        # 조미료 id: salt(소금) sugar(설탕) pepper(후추) soy_sauce(간장)
        # 레시피 키 = 결과 요리 아이템 id (hearty_salmon_stew / hearty_carrot_soup / hearty_veggie_salad)
        # ingredients: 재료 3개 (순서 무관, 중복 가능), seasoning: 조미료 1개
        # pet-food: 펫 먹이로 줄 때의 포만감 (0이면 펫 먹이 아님)
        # buff: 우클릭으로 먹을 때의 효과. type = health | strength | speed | regeneration
        #       amplifier = 0이 I, 1이 II ... / seconds = 지속 시간(초)
        recipes:
          hearty_salmon_stew:
            name: 든든한 연어 양배추찜
            ingredients: [salmon, cabbage, potato]
            seasoning: salt
            pet-food: 60
          hearty_carrot_soup:
            name: 든든한 당근 수프
            ingredients: [carrot, carrot, potato]
            seasoning: sugar
            pet-food: 40
            buff:
              type: regeneration
              amplifier: 0
              seconds: 60
          hearty_veggie_salad:
            name: 든든한 채소 샐러드
            ingredients: [cabbage, carrot, straw]
            seasoning: pepper
            buff:
              type: speed
              amplifier: 0
              seconds: 180
        # 레시피가 맞지 않거나 미니게임에 실패했을 때 나오는 요리
        failed-dish:
          name: 정체불명의 요리
          pet-food: 5
        """;
}
