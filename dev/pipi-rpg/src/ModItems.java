package dev.pipi.life;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** Items registered by the PipiFacilities mod (Material PIPIFACILITIES_<ID>) that replace the old vanilla+CustomModelData items. */
final class ModItems {
    private ModItems() {}

    private static final Map<String, Material> CACHE = new HashMap<>();
    /** Plugin tag (PDC processed_product) -> mod item id. "ingot" is intentionally absent: it is a plain vanilla IRON_INGOT now. */
    private static final Map<String, String> TAG_TO_ID = Map.ofEntries(
        Map.entry("plank", "plank"), Map.entry("plate", "steel"), Map.entry("reinforced_plank", "reinforced_plank"),
        Map.entry("coated_plank", "coated_plank"), Map.entry("blue_diamond", "blue_diamond"), Map.entry("diamond", "refined_diamond"),
        Map.entry("iron_core", "iron_core"), Map.entry("diamond_core", "diamond_core"), Map.entry("gold_core", "gold_core"),
        Map.entry("blueprint_paper", "blueprint_paper"), Map.entry("club", "club"), Map.entry("ironclub", "iron_club"),
        Map.entry("diamondclub", "diamond_club"), Map.entry("hammer", "hammer"), Map.entry("feed", "fish_feed"),
        Map.entry("raw_salmon", "raw_salmon"), Map.entry("straw", "straw"), Map.entry("pet_carrot", "pet_carrot"),
        Map.entry("warden_soul_stone", "warden_soul_stone"), Map.entry("vitality_crystal", "vitality_crystal"),
        Map.entry("amber_ingot", "amber_ingot"), Map.entry("amber_core", "amber_core"), Map.entry("redwood_plank", "redwood_plank"), Map.entry("amber_club", "amber_club"), Map.entry("cabbage_seed", "cabbage_seed"),
        Map.entry("advanced_blueprint_paper", "advanced_blueprint_paper"),
        Map.entry("salt", "salt"), Map.entry("sugar", "sugar"), Map.entry("pepper", "pepper"), Map.entry("soy_sauce", "soy_sauce"));
    private static final String[] TRAP_IDS = {"basic_trap", "iron_trap", "diamond_trap", "amber_trap"};

    /** Resolves a mod item by id; logs and throws a clear error when the mod is missing. */
    static Material byId(String id) {
        Material material = byIdOrNull(id);
        if (material == null) {
            String message = "PipiFacilities 모드 아이템을 찾을 수 없습니다: " + id + " (모드 1.0.24 이상 필요)";
            Bukkit.getLogger().severe(message);
            throw new IllegalStateException(message);
        }
        return material;
    }

    /** Resolves a mod item by id, or null when the mod is not installed / item is missing. */
    static Material byIdOrNull(String id) {
        Material cached = CACHE.get(id);
        if (cached != null) return cached;
        Material material = Material.matchMaterial("PIPIFACILITIES_" + id.toUpperCase(java.util.Locale.ROOT));
        if (material != null) CACHE.put(id, material);
        return material;
    }

    /** Mod material for a processed_product tag, or null when the tag is not one of the converted items. */
    static Material forTag(String tag) {
        String id = TAG_TO_ID.get(tag);
        return id == null ? null : byId(id);
    }

    /** Like {@link #forTag} but returns null (instead of throwing) when the mod item is missing. */
    static Material forTagOrNull(String tag) {
        String id = TAG_TO_ID.get(tag);
        return id == null ? null : byIdOrNull(id);
    }

    static Material repairKit() { return byId("repair_kit"); }
    static Material trap(int tier) { return byId(TRAP_IDS[Math.clamp(tier, 0, 3)]); }

    /** The replacement for an old vanilla-based custom stack, or null when the stack needs no conversion. */
    static ItemStack convert(ItemStack old, NamespacedKey processed, NamespacedKey repairKey, NamespacedKey trapKey) {
        if (old == null || old.getType().isAir() || !old.hasItemMeta()) return null;
        ItemMeta meta = old.getItemMeta();
        var tags = meta.getPersistentDataContainer();
        String tag = tags.get(processed, PersistentDataType.STRING);
        Material target = null;
        if (tag != null) {
            if (tag.equals("ingot")) return new ItemStack(Material.IRON_INGOT, old.getAmount());
            if (tag.equals("starter_fish")) { // retired 민물고기 -> 생 연어 (same amount)
                ItemStack salmon = new ItemStack(byId("raw_salmon"), old.getAmount());
                ItemMeta salmonMeta = salmon.getItemMeta();
                salmonMeta.setDisplayName("생 연어");
                salmonMeta.setLore(java.util.List.of("펫 전용 먹이 · 포만감 +15"));
                salmonMeta.getPersistentDataContainer().set(processed, PersistentDataType.STRING, "raw_salmon");
                salmon.setItemMeta(salmonMeta);
                return salmon;
            }
            target = forTag(tag);
        } else if (tags.has(repairKey, PersistentDataType.BYTE)) target = repairKit();
        else if (tags.has(trapKey, PersistentDataType.INTEGER))
            target = trap(tags.getOrDefault(trapKey, PersistentDataType.INTEGER, 0));
        if (target == null) return null;
        if (old.getType() == target && !meta.hasCustomModelData()) return null;
        ItemStack replacement = new ItemStack(target, old.getAmount());
        meta.setCustomModelData(null);
        if (!replacement.setItemMeta(meta)) {
            Bukkit.getLogger().warning("아이템 변환 실패: " + old.getType() + " -> " + target);
            return null;
        }
        return replacement;
    }

    /** Retired item tags: stacks carrying them are deleted by the migration. */
    private static final java.util.Set<String> REMOVED_TAGS = java.util.Set.of("sprout", "berry_feed", "herb_feed");
    private static final java.util.Set<String> LOGGED = new java.util.HashSet<>();

    static boolean isRemoved(ItemStack item, NamespacedKey processed) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        String tag = item.getItemMeta().getPersistentDataContainer().get(processed, PersistentDataType.STRING);
        return tag != null && REMOVED_TAGS.contains(tag);
    }

    private static void logRemoval(Inventory inventory) {
        String owner = inventory.getHolder() instanceof Player p ? p.getName() : "container";
        if (LOGGED.add(owner)) Bukkit.getLogger().info("삭제된 먹이 아이템 제거: " + owner);
    }

    static void convertInventory(Inventory inventory, NamespacedKey processed, NamespacedKey repairKey, NamespacedKey trapKey) {
        if (inventory == null) return;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (isRemoved(inventory.getItem(slot), processed)) {
                logRemoval(inventory);
                inventory.setItem(slot, null);
                continue;
            }
            ItemStack replacement = convert(inventory.getItem(slot), processed, repairKey, trapKey);
            if (replacement != null) inventory.setItem(slot, replacement);
        }
    }

    static void convertPlayer(Player player, NamespacedKey processed, NamespacedKey repairKey, NamespacedKey trapKey) {
        convertInventory(player.getInventory(), processed, repairKey, trapKey);
        if (isRemoved(player.getItemOnCursor(), processed)) { player.setItemOnCursor(null); return; }
        ItemStack cursor = convert(player.getItemOnCursor(), processed, repairKey, trapKey);
        if (cursor != null) player.setItemOnCursor(cursor);
    }
}
