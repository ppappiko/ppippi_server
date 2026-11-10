package dev.pipi.life;

import java.lang.reflect.Array;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;

final class MenuArt {
    private MenuArt() {}

    record View(Inventory inventory, Object wrapper) {
        void open(Player player) {
            if (wrapper != null) {
                try {
                    wrapper.getClass().getMethod("showInventory", Player.class).invoke(wrapper, player);
                    return;
                } catch (ReflectiveOperationException | LinkageError error) {
                    Bukkit.getLogger().warning("[PipiLife] ItemsAdder showInventory failed: " + (error.getCause() == null ? error : error.getCause()));
                }
            }
            player.openInventory(inventory);
        }
    }

    static View create(InventoryHolder holder, int size, String title, String texture) {
        Plugin itemsAdder = Bukkit.getPluginManager().getPlugin("ItemsAdder");
        if (itemsAdder != null && itemsAdder.isEnabled()) {
            try {
                ClassLoader loader = itemsAdder.getClass().getClassLoader();
                String api = "dev.lone.itemsadder.api.FontImages.";
                Class<?> imageClass = Class.forName(api + "FontImageWrapper", true, loader);
                Object image = imageClass.getConstructor(String.class).newInstance("pipi_ui:" + texture);
                if (Boolean.TRUE.equals(imageClass.getMethod("exists").invoke(image))) {
                    Class<?> inventoryClass = Class.forName(api + "TexturedInventoryWrapper", true, loader);
                    Object images = Array.newInstance(imageClass, 1);
                    Array.set(images, 0, image);
                    Object wrapper = inventoryClass.getConstructor(InventoryHolder.class, int.class, String.class,
                        int.class, int.class, images.getClass()).newInstance(holder, size, "", 0, -8, images);
                    Inventory inventory = (Inventory) inventoryClass.getMethod("getInternal").invoke(wrapper);
                    if (inventory.getHolder() == holder) return new View(inventory, wrapper);
                }
            } catch (ReflectiveOperationException | LinkageError error) {
                Bukkit.getLogger().warning("[PipiLife] ItemsAdder menu " + texture + " failed: " + (error.getCause() == null ? error : error.getCause()));
            }
        }
        return new View(Bukkit.createInventory(holder, size, title), null);
    }
}
