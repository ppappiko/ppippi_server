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
import org.lwjgl.glfw.GLFW;

final class ResearchScreen extends ContainerScreen {
    static final String TITLE = "삐삐서버 · 연구소";
    private static final int WIDTH = 640, HEIGHT = 348;
    private static final int LIST_X = 19, LIST_Y = 70, LIST_RIGHT = 442, LIST_BOTTOM = 303;
    private static final int ROW_HEIGHT = 176, MAX_SCROLL = 3 * ROW_HEIGHT - (LIST_BOTTOM - LIST_Y);
    private static final int TEXT = 0xFFE7EEF6, MUTED = 0xFF9AAEC3, BLUE = 0xFF87BEE8, GOLD = 0xFFE5BB7C;
    private float scale, originX, originY;
    private double scroll;
    private boolean dragging, moved;
    private double pressX, pressY;
    private int selectedSlot;
    private long requestUntil;

    ResearchScreen(ChestMenu menu, Inventory inventory, Component title, ResearchScreen previous) {
        super(menu, inventory, title);
        if (previous != null) {
            scroll = previous.scroll;
            selectedSlot = previous.selectedSlot;
        } else selectedSlot = 1;
    }

    @Override protected void init() {
        scale = Math.min((width - 12f) / WIDTH, (height - 12f) / HEIGHT);
        scale = Math.max(0.01f, scale);
        originX = (width - WIDTH * scale) / 2;
        originY = (height - HEIGHT * scale) / 2;
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        double x = localX(mouseX), y = localY(mouseY);
        g.fill(0, 0, width, height, 0xBD070D16);
        g.pose().pushPose();
        try {
            g.pose().translate(originX, originY, 0);
            g.pose().scale(scale, scale, 1);
            panel(g, 0, 0, WIDTH, HEIGHT, 0xFF101B27, 0xFF3B5770);
            g.fillGradient(1, 1, WIDTH - 1, 59, 0xFF273E53, 0xFF162534);
            g.fill(20, 17, 23, 46, BLUE);
            g.drawString(font, "삐삐서버   /   생활 기술", 32, 15, BLUE, false);
            g.drawString(font, "연구소", 32, 32, TEXT, false);
            button(g, 599, 15, 23, 24, "×", inside(x, y, 599, 15, 23, 24), true);
            panel(g, LIST_X - 1, LIST_Y - 1, 428, 235, 0xFF0A131D, 0xFF283E51);
            drawList(g, x, y);
            drawDetail(g, x, y);
            g.drawString(font, "휠 또는 드래그로 이동   ·   제작법 클릭으로 상세 보기", 20, 323, MUTED, false);
            g.drawString(font, "ESC 닫기", 564, 323, MUTED, false);
        } finally {
            g.pose().popPose();
        }
    }

    private void drawList(GuiGraphics g, double mouseX, double mouseY) {
        g.enableScissor((int) Math.ceil(originX + LIST_X * scale), (int) Math.ceil(originY + LIST_Y * scale),
            (int) Math.floor(originX + LIST_RIGHT * scale), (int) Math.floor(originY + LIST_BOTTOM * scale));
        try {
            for (int stage = 0; stage < 3; stage++) {
                int top = LIST_Y + stage * ROW_HEIGHT - (int) scroll;
                int base = stage * 18;
                boolean locked = menu.getSlot(base).getItem().is(Items.IRON_BARS);
                g.fillGradient(22, top + 4, 437, top + 100, 0xFF1D3042, 0xFF101D2A);
                g.drawString(font, "0" + (stage + 1) + "  /  연구 단계", 31, top + 13, BLUE, false);
                String status = locked ? "잠김" : "개방됨";
                g.drawString(font, status, 428 - font.width(status), top + 13, locked ? MUTED : BLUE, false);
                ItemStack action = menu.getSlot(base + 7).getItem();
                if (!action.isEmpty()) {
                    var lore = action.getOrDefault(DataComponents.LORE, net.minecraft.world.item.component.ItemLore.EMPTY).lines();
                    String cost = lore.isEmpty() ? "" : " · " + ChatFormatting.stripFormatting(lore.getFirst().getString());
                    button(g, 111, top + 8, 126, 19, name(action) + cost,
                        inside(mouseX, mouseY, 111, top + 8, 126, 19)
                            && inside(mouseX, mouseY, LIST_X, LIST_Y, LIST_RIGHT - LIST_X, LIST_BOTTOM - LIST_Y),
                        System.currentTimeMillis() >= requestUntil);
                }
                for (int i = 0; i < 12; i++) {
                    int slot = base + (i >= 6 ? i + 2 : i + 1), left = 31 + (i % 6) * 66, tileY=top+31+(i/6)*65;
                    ItemStack stack = menu.getSlot(slot).getItem();
                    boolean active = selectedSlot == slot;
                    boolean hover = inside(mouseX, mouseY, left, tileY, 62, 59)
                        && inside(mouseX, mouseY, LIST_X, LIST_Y, LIST_RIGHT - LIST_X, LIST_BOTTOM - LIST_Y);
                    panel(g, left, tileY, 62, 59, hover ? 0xFF29465F : 0xFF132130,
                        active ? BLUE : hover ? 0xFF6593B5 : 0xFF364E63);
                    if (stack.isEmpty()) continue;
                    icon(g, stack, left + 20, tileY + 9, 1.4f);
                    if (locked) {
                        g.fill(left + 1, tileY + 1, left + 61, tileY + 41, 0x88101823);
                        g.fill(left + 47, tileY + 7, left + 53, tileY + 13, MUTED);
                        g.fill(left + 45, tileY + 11, left + 52, tileY + 18, MUTED);
                    }
                    g.drawCenteredString(font, font.plainSubstrByWidth(name(stack), 60), left + 31, tileY + 44, locked ? MUTED : TEXT);
                }
            }
        } finally {
            g.disableScissor();
        }
        g.fill(443, LIST_Y + 2, 445, LIST_BOTTOM - 2, 0xFF233647);
        int thumb = LIST_Y + 2 + (int) (scroll / MAX_SCROLL * 157);
        g.fill(442, thumb, 446, thumb + 70, BLUE);
    }

    private void drawDetail(GuiGraphics g, double x, double y) {
        panel(g, 455, 69, 166, 235, 0xFF142333, 0xFF3C5871);
        g.drawString(font, "제작법 상세", 467, 82, BLUE, false);
        ItemStack detail = menu.getSlot(17).getItem();
        panel(g, 511, 100, 54, 52, 0xFF0C1723, 0xFF4B6C87);
        if (!detail.isEmpty()) {
            icon(g, detail, 522, 110, 2);
            g.drawCenteredString(font, name(detail), 538, 163, TEXT);
            int lineY = 181;
            for (Component line : detail.getOrDefault(DataComponents.LORE, net.minecraft.world.item.component.ItemLore.EMPTY).lines()) {
                String text = ChatFormatting.stripFormatting(line.getString());
                g.drawString(font, font.plainSubstrByWidth(text, 146), 466, lineY, MUTED, false);
                lineY += 12;
                if (lineY > 228) break;
            }
        }
        ItemStack action=menu.getSlot(16).getItem();
        if(!action.isEmpty()) {
            var costs=action.getOrDefault(DataComponents.LORE,net.minecraft.world.item.component.ItemLore.EMPTY).lines();
            g.drawString(font,"선택 연구 · "+(costs.isEmpty()?"":ChatFormatting.stripFormatting(costs.getFirst().getString())),467,258,GOLD,false);
            button(g,467,276,142,22,"연구 시작",inside(x,y,467,276,142,22),System.currentTimeMillis()>=requestUntil);
        }
        ItemStack gold = menu.getSlot(35).getItem();
        if (!gold.isEmpty()) g.drawString(font, name(gold), 467, 237, GOLD, false);
    }

    private void icon(GuiGraphics g, ItemStack stack, int x, int y, float size) {
        g.pose().pushPose();
        try {
            g.pose().translate(x, y, 0);
            g.pose().scale(size, size, 1);
            g.renderItem(stack, 0, 0);
        } finally {
            g.pose().popPose();
        }
    }

    private void button(GuiGraphics g, int x, int y, int w, int h, String label, boolean hover, boolean enabled) {
        panel(g, x, y, w, h, enabled && hover ? 0xFF365D7B : 0xFF243D53, enabled ? BLUE : 0xFF42566B);
        g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, enabled ? TEXT : MUTED);
    }

    private static void panel(GuiGraphics g, int x, int y, int w, int h, int fill, int border) {
        g.fill(x, y, x + w, y + h, border);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    private static String name(ItemStack item) { return ChatFormatting.stripFormatting(item.getHoverName().getString()); }
    private double localX(double x) { return (x - originX) / scale; }
    private double localY(double y) { return (y - originY) / scale; }
    private static boolean inside(double x, double y, int left, int top, int w, int h) {
        return x >= left && x < left + w && y >= top && y < top + h;
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        scroll = Math.clamp(scroll - vertical * 32, 0, MAX_SCROLL);
        return true;
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button != 0) return true;
        pressX = localX(x);
        pressY = localY(y);
        dragging = inside(pressX, pressY, LIST_X, LIST_Y, 427, LIST_BOTTOM - LIST_Y);
        moved = false;
        return true;
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && dragging) {
            if (moved || Math.abs(localY(y) - pressY) > 3) {
                moved = true;
                scroll = Math.clamp(scroll - dy / scale, 0, MAX_SCROLL);
            }
        }
        return true;
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        boolean wasDragging = dragging;
        dragging = false;
        x = localX(x);
        y = localY(y);
        if (button != 0 || moved || Math.abs(x - pressX) > 4 || Math.abs(y - pressY) > 4) return true;
        if(inside(x,y,467,276,142,22))sendClick(16);
        else if (inside(x, y, 599, 15, 23, 24)) onClose();
        else if (wasDragging && inside(x, y, LIST_X, LIST_Y, LIST_RIGHT - LIST_X, LIST_BOTTOM - LIST_Y)) {
            double listY = y - LIST_Y + scroll;
            int stage = (int) (listY / ROW_HEIGHT), col = (int) ((x - 31) / 66);
            double rowY = listY % ROW_HEIGHT;
            if (stage < 3 && inside(x, rowY, 111, 8, 126, 19)) sendClick(stage * 18 + 7);
            else if (stage < 3 && x >= 31 && col < 6 && (x - 31) % 66 < 62
                && listY % ROW_HEIGHT >= 31 && listY % ROW_HEIGHT < 155 && (listY % ROW_HEIGHT - 31) % 65 < 59) {
                int index=col+(int)((listY % ROW_HEIGHT-31)/65)*6;
                int slot = stage * 18 + (index >= 6 ? index + 2 : index + 1);
                if (sendClick(slot)) selectedSlot = slot;
            }
        }
        return true;
    }

    private boolean sendClick(int slot) {
        if (minecraft == null || minecraft.getConnection() == null || menu.getSlot(slot).getItem().isEmpty()
            || System.currentTimeMillis() < requestUntil) return false;
        requestUntil = System.currentTimeMillis() + 1000;
        minecraft.getConnection().send(new ServerboundContainerClickPacket(menu.containerId, menu.getStateId(), slot,
            0, ClickType.PICKUP, menu.getCarried().copy(), Int2ObjectMaps.emptyMap()));
        return true;
    }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) onClose();
        return true;
    }
}
