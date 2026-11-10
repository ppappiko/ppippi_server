package dev.pipi.travel;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;

final class FoodStorageScreen extends ContainerScreen {
    FoodStorageScreen(ChestMenu menu, Inventory inventory) {
        super(menu, inventory, Component.literal("식량 저장소"));
    }

    @Override protected void renderBg(GuiGraphics graphics, float partial, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        graphics.fill(x - 4, y - 4, x + imageWidth + 4, y + imageHeight + 4, 0xff455b70);
        graphics.fillGradient(x - 3, y - 3, x + imageWidth + 3, y + imageHeight + 3, 0xff202f40, 0xff101821);
        graphics.fill(x, y + 66, x + imageWidth, y + 68, 0xff35495d);
        for (int index = 0; index < menu.slots.size(); index++) {
            var slot = menu.getSlot(index);
            if (index >= 6 && index < 27 && !slot.hasItem()) continue;
            int sx = x + slot.x, sy = y + slot.y;
            graphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, index < 6 ? 0xff577187 : 0xff304457);
            graphics.fill(sx, sy, sx + 16, sy + 16, 0xff101a25);
        }
        graphics.drawString(font, "먹이 전용  ·  6칸", x + 8, y + 47, 0xff9fb4c9, false);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int x, int y) {
        graphics.drawString(font, title, 8, 6, 0xffebf1fa, false);
        graphics.drawString(font, "보관함", 8, inventoryLabelY, 0xffbbcadb, false);
    }
}
