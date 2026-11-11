package dev.pipi.travel;

import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
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

final class InheritScreen extends ContainerScreen {
    static final String TITLE = "삐삐서버 · 계승";
    private static final int W = 690, H = 350;
    private static final int TEXT = 0xffe8edf4, MUTED = 0xff9cacbf, GOLD = 0xffebc77f, RED = 0xffe07070;
    private float scale, left, top;
    private int dragging = -1;
    private boolean sent;

    InheritScreen(ChestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override protected void init() {
        scale = Math.max(.01f, Math.min((width - 16f) / W, (height - 16f) / H));
        left = (width - W * scale) / 2;
        top = (height - H * scale) / 2;
    }

    private ItemStack item(int slot) { return menu.getSlot(slot).getItem(); }
    private String name(int slot) { return ChatFormatting.stripFormatting(item(slot).getHoverName().getString()); }
    private static List<Component> lore(ItemStack stack) { return stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines(); }
    private static Component styled(Component c) { return c.copy().withStyle(s -> s.withItalic(false)); }

    @Override public void render(GuiGraphics g, int mx, int my, float delta) {
        double x = (mx - left) / scale, y = (my - top) / scale;
        boolean modal = !item(46).isEmpty();
        g.fill(0, 0, width, height, 0xb7081019);
        g.pose().pushPose();
        try {
            g.pose().translate(left, top, 0);
            g.pose().scale(scale, scale, 1);
            panel(g, 0, 0, 200, H, false);
            panel(g, 203, 0, 294, H, false);
            panel(g, 500, 0, 190, H, false);
            heading(g, "보유 장비", 6, 188);
            heading(g, "계승", 209, 282);
            heading(g, "세부 능력치", 506, 178);
            for (int i = 0; i < 9 && !item(i).isEmpty(); i++) {
                int row = 32 + i * 31;
                panel(g, 7, row, 186, 29, hit(x, y, 7, row, 186, 29));
                g.renderItem(item(i), 13, row + 6);
                g.drawString(font, name(i), 36, row + 15, TEXT, false);
                int filled = 0;
                int slots = 0;
                List<Component> lines = lore(item(i));
                for (Component line : lines) {
                    String text = ChatFormatting.stripFormatting(line.getString());
                    if (text.startsWith("잠재 슬롯 ") && text.length() >= 7)
                        slots = Math.max(slots, Character.digit(text.charAt(6), 10));
                    if (AlchemyScreen.isGradeLine(text)) filled++;
                }
                for (int j = 0; j < slots; j++)
                    g.drawString(font, j < filled ? "◆" : "◇", 36 + j * 9, row + 3, j < filled ? 0xff81d997 : MUTED, false);
            }
            if (!item(48).isEmpty()) button(g, "이전", 8, 319, 77, 22, x, y);
            if (!item(50).isEmpty()) button(g, "다음", 114, 319, 77, 22, x, y);

            panel(g, 300, 32, 40, 36, !item(20).isEmpty());
            if (!item(20).isEmpty()) g.renderItem(item(20), 312, 42);
            panel(g, 380, 32, 40, 36, !item(24).isEmpty());
            if (!item(24).isEmpty()) g.renderItem(item(24), 392, 42);
            g.drawString(font, "◀", 352, 44, MUTED, false);
            g.drawCenteredString(font, "계승 장비", 320, 72, MUTED);
            g.drawCenteredString(font, "계승 재료", 400, 72, MUTED);
            if (!item(20).isEmpty()) g.drawCenteredString(font, font.plainSubstrByWidth(name(20), 92), 320, 84, TEXT);
            if (!item(24).isEmpty()) g.drawCenteredString(font, font.plainSubstrByWidth(name(24), 92), 400, 84, TEXT);

            panel(g, 330, 88, 40, 36, !item(22).isEmpty());
            if (!item(22).isEmpty()) {
                g.renderItem(item(22), 342, 98);
                g.drawCenteredString(font, font.plainSubstrByWidth(name(22), 170), 350, 130, TEXT);
            }

            panel(g, 216, 140, 268, 112, false);
            for (int i = 0; i < 7 && !item(27 + i).isEmpty(); i++) {
                int row = 144 + i * 15;
                if (item(27 + i).is(Items.GRAY_DYE)) g.drawString(font, "◇ " + name(27 + i), 224, row, MUTED, false);
                else g.drawString(font, styled(item(27 + i).getHoverName()), 224, row, TEXT, false);
            }

            panel(g, 216, 256, 268, 64, false);
            List<Component> grade = lore(item(43));
            if (!item(43).isEmpty()) g.drawString(font, styled(item(43).getHoverName()), 224, 260, TEXT, false);
            if (grade.size() > 0) g.drawString(font, ChatFormatting.stripFormatting(grade.get(0).getString()), 224, 272, MUTED, false);
            if (grade.size() > 1) g.drawString(font, ChatFormatting.stripFormatting(grade.get(1).getString()), 224, 284, MUTED, false);
            if (!item(44).isEmpty()) {
                g.drawString(font, "소모 골드", 224, 296, MUTED, false);
                g.drawString(font, name(44), 476 - font.width(name(44)), 296, GOLD, false);
                List<Component> money = lore(item(44));
                g.drawString(font, "보유 골드", 224, 308, MUTED, false);
                String bal = money.isEmpty() ? "" : ChatFormatting.stripFormatting(money.get(0).getString());
                g.drawString(font, bal, 476 - font.width(bal), 308, TEXT, false);
            }

            if (item(45).isEmpty() && !item(42).isEmpty())
                g.drawCenteredString(font, name(42), 350, 330, RED);
            else if (!item(41).isEmpty())
                g.drawCenteredString(font, name(41), 350, 330, GOLD);
            if (!item(45).isEmpty()) button(g, "계승 하기", 275, 324, 150, 22, x, y);

            if (!item(34).isEmpty()) stats(g, item(34), 41, "계승 전 능력치");
            g.drawString(font, "↓", 512, 170, MUTED, false);
            if (!item(35).isEmpty()) stats(g, item(35), 186, "계승 후 능력치");
            button(g, "대장간으로", 520, 312, 149, 25, x, y);

            if (dragging >= 0 && !item(dragging).isEmpty()) {
                g.pose().translate(0, 0, 350);
                icon(g, item(dragging), (int) (x - 8), (int) (y - 8), 1f);
            }

            if (modal) {
                g.pose().translate(0, 0, 250);
                g.fill(0, 0, W, H, 0xa7081019);
                panel(g, 224, 113, 242, 122, true);
                g.drawCenteredString(font, name(22), 345, 126, GOLD);
                g.drawCenteredString(font, "계승하시겠습니까?", 345, 148, TEXT);
                g.drawCenteredString(font, name(44), 345, 168, GOLD);
                button(g, "확인", 244, 192, 93, 25, x, y);
                button(g, "취소", 353, 192, 93, 25, x, y);
            }
        } finally {
            g.pose().popPose();
        }
        if (!modal) {
            for (int i = 0; i < 9; i++)
                if (!item(i).isEmpty() && dragging < 0 && hit(x, y, 7, 32 + i * 31, 186, 29))
                    g.renderTooltip(font, item(i), mx, my);
            if (!item(20).isEmpty() && hit(x, y, 300, 32, 40, 36)) g.renderTooltip(font, item(20), mx, my);
            if (!item(24).isEmpty() && hit(x, y, 380, 32, 40, 36)) g.renderTooltip(font, item(24), mx, my);
            if (!item(22).isEmpty() && hit(x, y, 330, 88, 40, 36)) g.renderTooltip(font, item(22), mx, my);
        }
    }

    private void stats(GuiGraphics g, ItemStack stack, int y, String title) {
        g.drawString(font, title, 512, y, MUTED, false);
        int ly = y + 15;
        for (Component line : lore(stack)) {
            g.drawString(font, font.plainSubstrByWidth(ChatFormatting.stripFormatting(line.getString()), 166), 512, ly, TEXT, false);
            ly += 12;
        }
    }

    private void heading(GuiGraphics g, String label, int x, int w) {
        g.fillGradient(x, 6, x + w, 26, 0xff26394c, 0xff192737);
        g.drawCenteredString(font, label, x + w / 2, 12, TEXT);
    }

    private static void panel(GuiGraphics g, int x, int y, int w, int h, boolean active) {
        g.fill(x, y, x + w, y + h, active ? 0xff7b99b5 : 0xff344658);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, active ? 0xff263648 : 0xff17222e, 0xff101820);
    }

    private void button(GuiGraphics g, String label, int x, int y, int w, int h, double mx, double my) {
        panel(g, x, y, w, h, hit(mx, my, x, y, w, h));
        g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, TEXT);
    }

    private static void icon(GuiGraphics g, ItemStack stack, int x, int y, float size) {
        g.pose().pushPose();
        try { g.pose().translate(x, y, 0); g.pose().scale(size, size, 1); g.renderItem(stack, 0, 0); }
        finally { g.pose().popPose(); }
    }

    private static boolean hit(double x, double y, int bx, int by, int w, int h) {
        return x >= bx && x < bx + w && y >= by && y < by + h;
    }

    private void click(int slot, int button) {
        if (sent || item(slot).isEmpty() || minecraft == null || minecraft.getConnection() == null) return;
        sent = true;
        minecraft.getConnection().send(new ServerboundContainerClickPacket(menu.containerId, menu.getStateId(),
            slot, button, ClickType.PICKUP, menu.getCarried().copy(), Int2ObjectMaps.emptyMap()));
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 && button != 1) return true;
        double x = (mx - left) / scale, y = (my - top) / scale;
        if (!item(46).isEmpty()) {
            if (button == 0) {
                if (hit(x, y, 244, 192, 93, 25)) click(46, 0);
                else if (hit(x, y, 353, 192, 93, 25)) click(47, 0);
            }
            return true;
        }
        for (int i = 0; i < 9; i++) {
            if (!hit(x, y, 7, 32 + i * 31, 186, 29) || item(i).isEmpty()) continue;
            if (button == 0) dragging = i;
            else click(i, 1);
            return true;
        }
        if (button == 0) {
            if (hit(x, y, 300, 32, 40, 36)) { click(20, 0); return true; }
            if (hit(x, y, 380, 32, 40, 36)) { click(24, 0); return true; }
            if (!item(45).isEmpty() && hit(x, y, 275, 324, 150, 22)) { click(45, 0); return true; }
            if (hit(x, y, 8, 319, 77, 22)) { click(48, 0); return true; }
            if (hit(x, y, 114, 319, 77, 22)) { click(50, 0); return true; }
            if (hit(x, y, 520, 312, 149, 25)) { click(49, 0); return true; }
        }
        return true;
    }

    @Override public boolean mouseReleased(double mx, double my, int button) {
        if (dragging < 0) return true;
        int source = dragging;
        dragging = -1;
        if (button != 0) return true;
        double x = (mx - left) / scale, y = (my - top) / scale;
        if (hit(x, y, 300, 32, 40, 36)) click(source, 0);
        else if (hit(x, y, 380, 32, 40, 36)) click(source, 1);
        return true;
    }

    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (item(46).isEmpty() && dragging < 0 && dy != 0) click(dy > 0 ? 48 : 50, 0);
        return true;
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) { return true; }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (item(46).isEmpty()) onClose();
            else click(47, 0);
        }
        return true;
    }
}
