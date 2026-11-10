package dev.pipi.travel;

import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
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

final class AlchemyScreen extends ContainerScreen {
    static final String TITLE = "삐삐서버 · 연금술사";
    private static final int W = 690, H = 350;
    private static final int TEXT = 0xffe8edf4, MUTED = 0xff9cacbf, GOLD = 0xffebc77f;
    private float scale, left, top;
    private boolean sent;

    AlchemyScreen(ChestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override protected void init() {
        scale = Math.max(.01f, Math.min((width - 16f) / W, (height - 16f) / H));
        left = (width - W * scale) / 2;
        top = (height - H * scale) / 2;
    }

    private ItemStack item(int slot) { return menu.getSlot(slot).getItem(); }
    private String name(int slot) { return ChatFormatting.stripFormatting(item(slot).getHoverName().getString()); }

    @Override public void render(GuiGraphics g, int mx, int my, float delta) {
        double x = (mx - left) / scale, y = (my - top) / scale;
        ItemStack selected = item(22);
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
            heading(g, "인챈트", 209, 282);
            heading(g, "세부 능력치", 506, 178);
            for (int i = 0; i < 9 && !item(i).isEmpty(); i++) {
                int row = 32 + i * 31;
                panel(g, 7, row, 186, 29, selected.equals(item(i)) || hit(x, y, 7, row, 186, 29));
                g.renderItem(item(i), 13, row + 6);
                g.drawString(font, name(i), 36, row + 15, TEXT, false);
                int gemX = 36;
                for (Component line : lore(item(i))) {
                    String text = ChatFormatting.stripFormatting(line.getString());
                    if (!text.startsWith("잠재 슬롯 ") || text.length() < 7) continue;
                    int count = Character.digit(text.charAt(6), 10);
                    long filled = lore(item(i)).stream().map(Component::getString).map(ChatFormatting::stripFormatting)
                        .filter(value -> value.startsWith("[일반]") || value.startsWith("[상급]") || value.startsWith("[이탈]")).count();
                    for (int j = 0; j < count; j++, gemX += 9)
                        g.drawString(font, j < filled ? "◆" : "◇", gemX, row + 3, j < filled ? 0xff81d997 : MUTED, false);
                }
            }
            if (!item(48).isEmpty()) button(g, "이전", 8, 319, 77, 22, x, y);
            if (!item(50).isEmpty()) button(g, "다음", 114, 319, 77, 22, x, y);
            button(g, "닫기", 520, 312, 149, 25, x, y);
            if (selected.isEmpty()) {
                g.drawCenteredString(font, "인챈트할 장비를 선택하세요.", 350, 101, MUTED);
                return;
            }
            panel(g, 330, 37, 40, 36, true);
            g.renderItem(selected, 342, 47);
            g.drawCenteredString(font, name(22), 350, 83, TEXT);
            panel(g, 216, 102, 268, 134, false);
            for (int i = 0; i < 7 && !item(27 + i).isEmpty(); i++) {
                int row = 107 + i * 18;
                boolean filled = !item(27 + i).is(Items.GRAY_DYE);
                g.drawString(font, filled ? "◆" : "◇", 224, row + 4, filled ? 0xff81d997 : MUTED, false);
                g.drawString(font, name(27 + i), 238, row + 4, filled ? TEXT : MUTED, false);
                if (!item(36 + i).isEmpty()) {
                    panel(g, 457, row, 21, 16, item(36 + i).is(Items.GOLD_NUGGET) || hit(x,y,457,row,21,16));
                    g.renderItem(item(36 + i), 459, row);
                }
            }
            g.drawString(font, "소모 레벨", 223, 249, MUTED, false);
            g.renderItem(item(44), 480, 265);
            g.drawString(font, "소모 골드", 223, 270, MUTED, false);
            g.drawString(font, name(43), 473 - font.width(name(43)), 249, TEXT, false);
            g.drawString(font, name(44), 473 - font.width(name(44)), 270, GOLD, false);
            button(g, "인챈트 하기", 275, 307, 150, 29, x, y);
            int sy = 41;
            if (selected.isDamageableItem()) {
                g.drawString(font, "내구도 " + (selected.getMaxDamage() - selected.getDamageValue()) + "/" + selected.getMaxDamage(), 512, sy, TEXT, false);
                sy += 15;
            }
            for (Component line : lore(selected)) {
                g.drawString(font, line, 512, sy, TEXT, false);
                sy += 15;
            }
            if (modal) {
                g.pose().translate(0, 0, 250);
                g.fill(0, 0, W, H, 0xa7081019);
                panel(g, 224, 113, 242, 122, true);
                g.drawCenteredString(font, name(22), 345, 126, GOLD);
                g.drawCenteredString(font, "잠그지 않은 옵션을 새로 부여할까요?", 345, 148, TEXT);
                g.drawCenteredString(font, name(43) + "  /  " + name(44), 345, 168, MUTED);
                button(g, "확인", 244, 192, 93, 25, x, y);
                button(g, "취소", 353, 192, 93, 25, x, y);
            }
        } finally {
            g.pose().popPose();
        }
        if (!modal && hit(x, y, 330, 37, 40, 36)) g.renderTooltip(font, selected, mx, my);
    }

    private static java.util.List<Component> lore(ItemStack stack) {
        return stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines();
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

    private static boolean hit(double x, double y, int bx, int by, int w, int h) {
        return x >= bx && x < bx + w && y >= by && y < by + h;
    }

    private void click(int slot) {
        if (sent || item(slot).isEmpty() || minecraft == null || minecraft.getConnection() == null) return;
        sent = true;
        minecraft.getConnection().send(new ServerboundContainerClickPacket(menu.containerId, menu.getStateId(),
            slot, 0, ClickType.PICKUP, menu.getCarried().copy(), Int2ObjectMaps.emptyMap()));
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return true;
        double x = (mx - left) / scale, y = (my - top) / scale;
        if (!item(46).isEmpty()) {
            if (hit(x, y, 244, 192, 93, 25)) click(45);
            else if (hit(x, y, 353, 192, 93, 25)) click(46);
            return true;
        }
        for (int i = 0; i < 9; i++) if (hit(x, y, 7, 32 + i * 31, 186, 29)) click(i);
        for (int i = 0; i < 7; i++) if (hit(x, y, 457, 107 + i * 18, 21, 16)) click(36 + i);
        if (hit(x, y, 275, 307, 150, 29)) click(45);
        if (hit(x, y, 8, 319, 77, 22)) click(48);
        if (hit(x, y, 114, 319, 77, 22)) click(50);
        if (hit(x, y, 520, 312, 149, 25)) click(49);
        return true;
    }

    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (item(46).isEmpty() && dy != 0) click(dy > 0 ? 48 : 50);
        return true;
    }
    @Override public boolean mouseReleased(double x, double y, int button) { return true; }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) { return true; }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            if (item(46).isEmpty()) onClose();
            else click(46);
        }
        return true;
    }
}
