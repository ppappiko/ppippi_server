package dev.pipi.life;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;

/**
 * Gives gear its ItemsAdder look (pack namespace pipi_gear, plugins/ItemsAdder/contents/pipi_gear).
 * Swords, bows and staffs of 0~3티어 get their own tier model (sword_t0.. / bow_t0.. / staff_t0.., made by dev/art/make-weapon-tiers.mjs);
 * other gear (and 4~5티어 for now) keeps the shared per-material model.
 */
final class GearModels {
    private GearModels() {}

    /** pipi_gear id -> its custom model data (-1 when ItemsAdder has no such item); cached since every inventory scan calls apply. */
    private static final Map<String, Integer> CMD = new HashMap<>();

    static void apply(ItemStack item) {
        if (item == null || item.getType().isAir()) return;
        String id = id(item);
        if (id == null || !Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) return;
        int cmd = cmd(id, item);
        String s = shared(item);
        if (cmd < 0 && s != null && !s.equals(id)) cmd = cmd(s, item);   // tier model missing (pack not rebuilt yet): old look
        if (cmd < 0) return;
        var meta = item.getItemMeta();
        if (meta == null || meta.hasCustomModelData() && meta.getCustomModelData() == cmd) return;
        meta.setCustomModelData(cmd);
        item.setItemMeta(meta);
    }

    /** Tier weapon id when the item is a 0~3티어 sword / bow / staff, else the shared model id. */
    private static String id(ItemStack item) {
        String kind = switch (item.getType()) {
            case IRON_SWORD, DIAMOND_SWORD, NETHERITE_SWORD -> "sword";
            case BOW -> "bow";
            case BLAZE_ROD -> "staff";
            default -> null;
        };
        int tier = tierOf(item);
        if (kind != null && tier >= 0 && tier <= 3) return kind + "_t" + tier;
        return shared(item);
    }

    private static String shared(ItemStack item) {
        return switch (item.getType()) {
            case IRON_SWORD -> "iron_sword";
            case DIAMOND_SWORD -> "diamond_sword";
            case NETHERITE_SWORD -> "netherite_sword";
            case BOW -> "bow";
            case BLAZE_ROD -> "staff";
            case IRON_PICKAXE -> "iron_pickaxe";
            case DIAMOND_PICKAXE -> "diamond_pickaxe";
            case FISHING_ROD -> "fishing_rod";
            default -> null;
        };
    }

    /** "N티어 …" from the gear catalog, else from the display name (forge menu icons); -1 when there is none. */
    private static int tierOf(ItemStack item) {
        String c = CombatStats.catalog(item);
        if (c.isEmpty() && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) c = ChatColor.stripColor(item.getItemMeta().getDisplayName()).trim();
        var m = java.util.regex.Pattern.compile("^([0-5])티어").matcher(c);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    /** Cached hits only: ItemsAdder may finish loading its items after we start. */
    private static int cmd(String id, ItemStack item) {
        String key = id + "@" + item.getType();
        Integer hit = CMD.get(key);
        if (hit != null) return hit;
        int v = lookup(id, item);
        if (v >= 0) CMD.put(key, v);
        return v;
    }

    private static int lookup(String id, ItemStack item) {
        try {
            Class<?> api = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Object model = api.getMethod("getInstance", String.class).invoke(null, "pipi_gear:" + id);
            if (model == null) return -1;
            ItemStack visual = (ItemStack) api.getMethod("getItemStack").invoke(model);
            var source = visual.getItemMeta();
            return visual.getType() == item.getType() && source != null && source.hasCustomModelData() ? source.getCustomModelData() : -1;
        } catch (ReflectiveOperationException | LinkageError error) {
            Bukkit.getLogger().warning("삐삐서버 장비 모델 로드 실패: " + error.getMessage());
            return -1;
        }
    }
}
