package dev.pipi.life;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

final class GearModels {
    private GearModels() {}

    static void apply(ItemStack item) {
        String id = switch (item.getType()) {
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
        if (id == null || !Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) return;
        try {
            Class<?> api = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Object model = api.getMethod("getInstance", String.class).invoke(null, "pipi_gear:" + id);
            if (model == null) return;
            ItemStack visual = (ItemStack) api.getMethod("getItemStack").invoke(model);
            var source = visual.getItemMeta();
            var target = item.getItemMeta();
            if (visual.getType() == item.getType() && source.hasCustomModelData()) {
                target.setCustomModelData(source.getCustomModelData());
                item.setItemMeta(target);
            }
        } catch (ReflectiveOperationException | LinkageError error) {
            Bukkit.getLogger().warning("삐삐서버 장비 모델 로드 실패: " + error.getMessage());
        }
    }
}
