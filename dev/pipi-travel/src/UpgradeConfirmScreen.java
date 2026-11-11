package dev.pipi.travel;

import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.lwjgl.glfw.GLFW;

/** Facility level-up confirmation window (server menu "삐삐서버 · 업그레이드"). */
final class UpgradeConfirmScreen extends ContainerScreen {
    static final String TITLE = "삐삐서버 · 업그레이드";
    private static final int W = 420, H = 296;
    private static final int TEXT = 0xffe6eef6, MUTED = 0xff9fb4ca, GOLD = 0xffe6c588, OK = 0xff82d99a, BAD = 0xffe87070;
    private float scale, left, top;
    private boolean pending;

    UpgradeConfirmScreen(ChestMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    @Override protected void init() {
        scale = Math.max(.01f, Math.min((width - 18f) / W, (height - 18f) / H));
        left = (width - W * scale) / 2;
        top = (height - H * scale) / 2;
    }

    private ItemStack item(int slot) { return menu.getSlot(slot).getItem(); }
    private String name(int slot) { return ChatFormatting.stripFormatting(item(slot).getHoverName().getString()); }
    private static List<String> lore(ItemStack item) {
        return item.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().stream()
            .map(line -> ChatFormatting.stripFormatting(line.getString())).toList();
    }
    private boolean canConfirm() { return item(39).is(Items.LIME_DYE); }

    @Override public void render(GuiGraphics g, int mx, int my, float delta) {
        double x = (mx - left) / scale, y = (my - top) / scale;
        g.fill(0, 0, width, height, 0xb9081019);
        g.pose().pushPose();
        try {
            g.pose().translate(left, top, 0);
            g.pose().scale(scale, scale, 1);
            panel(g, 0, 0, W, H, false);
            g.fillGradient(5, 5, W - 5, 28, 0xff2d4054, 0xff1c2a3b);
            g.drawString(font, "시설 업그레이드", 11, 13, TEXT, false);

            // facility + level change + unlock text
            panel(g, 16, 38, 62, 61, true);
            icon(g, item(4), 25, 46, 2.8f);
            g.drawString(font, font.plainSubstrByWidth(name(4), 316), 92, 48, GOLD, false);
            var unlock = lore(item(4));
            for (int i = 0; i < unlock.size() && i < 4; i++)
                g.drawString(font, font.plainSubstrByWidth(unlock.get(i), 316), 92, 66 + i * 12, i == 0 ? TEXT : MUTED, false);

            // materials
            g.drawString(font, "필요 재료", 16, 110, MUTED, false);
            List<Integer> mats = new ArrayList<>();
            for (int s = 19; s <= 25; s++) if (!item(s).isEmpty()) mats.add(s);
            int cell = 128, cols = Math.max(1, Math.min(3, mats.size()));
            int startX = (W - cols * cell) / 2;
            for (int i = 0; i < mats.size(); i++) {
                int s = mats.get(i);
                int cx = startX + (i % 3) * cell, cy = 124 + (i / 3) * 44;
                panel(g, cx + 3, cy, cell - 6, 40, false);
                g.renderItem(item(s), cx + 9, cy + 12);
                g.drawString(font, font.plainSubstrByWidth(name(s), cell - 36), cx + 30, cy + 8, TEXT, false);
                var lines = lore(item(s));
                String amount = lines.isEmpty() ? "" : lines.getFirst();
                g.drawString(font, amount, cx + 30, cy + 22, enough(amount) ? OK : BAD, false);
            }
            if (mats.isEmpty()) g.drawCenteredString(font, "추가 재료 없음", W / 2, 140, MUTED);

            // gold
            g.renderItem(item(31), 16, 222);
            g.drawString(font, "필요 골드", 38, 218, MUTED, false);
            g.drawString(font, name(31), 38, 230, GOLD, false);
            var money = lore(item(31));
            if (!money.isEmpty()) g.drawString(font, money.getFirst(), W - 16 - font.width(money.getFirst()), 230, MUTED, false);

            // buttons
            if (canConfirm()) button(g, 80, 258, 120, 26, "업그레이드", x, y, true);
            else button(g, 80, 258, 120, 26, "재료 부족", x, y, false);
            button(g, 220, 258, 120, 26, "취소", x, y, true);
        } finally { g.pose().popPose(); }
        if (hit(x, y, 16, 38, 62, 61) && !item(4).isEmpty()) g.renderTooltip(font, item(4), mx, my);
    }

    private static boolean enough(String amount) {
        int slash = amount.indexOf('/');
        if (slash < 0) return true;
        try { return Long.parseLong(amount.substring(0, slash).trim()) >= Long.parseLong(amount.substring(slash + 1).trim()); }
        catch (NumberFormatException ignored) { return true; }
    }

    private static void panel(GuiGraphics g, int x, int y, int w, int h, boolean selected) {
        g.fill(x, y, x + w, y + h, selected ? 0xffa78c59 : 0xff3d5369);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, 0xff1a2a3a, 0xff0c1721);
    }
    private void button(GuiGraphics g, int x, int y, int w, int h, String label, double mx, double my, boolean enabled) {
        panel(g, x, y, w, h, enabled && hit(mx, my, x, y, w, h));
        g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, enabled ? TEXT : 0xff6b7a8a);
    }
    private static void icon(GuiGraphics g, ItemStack item, int x, int y, float size) {
        g.pose().pushPose();
        try { g.pose().translate(x, y, 0); g.pose().scale(size, size, 1); g.renderItem(item, 0, 0); }
        finally { g.pose().popPose(); }
    }
    private static boolean hit(double x, double y, int bx, int by, int w, int h) { return x >= bx && y >= by && x < bx + w && y < by + h; }

    private void click(int slot) {
        if (item(slot).isEmpty() || minecraft == null || minecraft.getConnection() == null || pending) return;
        pending = true;
        minecraft.getConnection().send(new ServerboundContainerClickPacket(menu.containerId, menu.getStateId(), slot, 0,
            ClickType.PICKUP, menu.getCarried().copy(), Int2ObjectMaps.emptyMap()));
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return true;
        double x = (mx - left) / scale, y = (my - top) / scale;
        if (hit(x, y, 80, 258, 120, 26) && canConfirm()) click(39);
        else if (hit(x, y, 220, 258, 120, 26)) click(41);
        return true;
    }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) click(41);
        return true;
    }
    @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) { return true; }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) { return true; }
    @Override public boolean mouseReleased(double x, double y, int button) { return true; }
}
