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
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class ResearchScreen extends ContainerScreen {
    static final String TITLE = "삐삐서버 · 연구소";
    private static final int WIDTH = 640, HEIGHT = 348;
    private static final int LIST_X = 19, LIST_Y = 70, LIST_RIGHT = 442, LIST_BOTTOM = 303;
    private static final int VIEW_H = LIST_BOTTOM - LIST_Y;
    private static final int OPEN_H = 176, LOCKED_H = 104, LOCKED_H2 = 120;
    private static final int TEXT = 0xFFE7EEF6, MUTED = 0xFF9AAEC3, BLUE = 0xFF87BEE8,
        GOLD = 0xFFE5BB7C, RED = 0xFFE57373, CHAIN = 0xFF54677A, CHAIN_DARK = 0xFF33434F,
        DIAMOND = 0xFF6E8AA6;
    private static final int PANEL_X = 94, PANEL_RIGHT = 437, PANEL_W = PANEL_RIGHT - PANEL_X;
    private static final int BUTTON_W = 142, BUTTON_H = 22, BUTTON_Y = 70;
    private static final Pattern MARKER = Pattern.compile("^\\d+단계$");
    private float scale, originX, originY;
    private double scroll;
    private boolean dragging, moved;
    private double pressX, pressY;
    private int selectedStage = 1, selectedIndex;
    private long requestUntil;

    private static final class Block {
        final int stage, markerSlot, actionSlot;
        final boolean locked, disabled;
        final List<Integer> entries, items;
        Block(int stage, int markerSlot, int actionSlot, boolean locked, boolean disabled,
              List<Integer> entries, List<Integer> items) {
            this.stage = stage;
            this.markerSlot = markerSlot;
            this.actionSlot = actionSlot;
            this.locked = locked;
            this.disabled = disabled;
            this.entries = entries;
            this.items = items;
        }
        int height() { return locked ? (disabled ? LOCKED_H2 : LOCKED_H) : OPEN_H; }
    }

    ResearchScreen(ChestMenu menu, Inventory inventory, Component title, ResearchScreen previous) {
        super(menu, inventory, title);
        if (previous != null) {
            scroll = previous.scroll;
            selectedStage = previous.selectedStage;
            selectedIndex = previous.selectedIndex;
        }
    }

    @Override protected void init() {
        scale = Math.min((width - 12f) / WIDTH, (height - 12f) / HEIGHT);
        scale = Math.max(0.01f, scale);
        originX = (width - WIDTH * scale) / 2;
        originY = (height - HEIGHT * scale) / 2;
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        double x = localX(mouseX), y = localY(mouseY);
        List<Block> blocks = blocks();
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
            drawList(g, blocks, x, y);
            drawDetail(g, x, y);
            g.drawString(font, "휠 또는 드래그로 이동   ·   제작법 클릭으로 상세 보기", 20, 323, MUTED, false);
            g.drawString(font, "ESC 닫기", 564, 323, MUTED, false);
        } finally {
            g.pose().popPose();
        }
    }

    private List<Block> blocks() {
        List<Block> result = new ArrayList<>();
        int slot = 0;
        while (slot < 50) {
            ItemStack marker = menu.getSlot(slot).getItem();
            if (!isMarker(marker)) { slot++; continue; }
            int markerSlot = slot;
            int actionSlot = slot + 1;
            boolean locked = marker.is(Items.IRON_BARS);
            int end = markerSlot + 2;
            while (end < 50 && !isMarker(menu.getSlot(end).getItem())) end++;
            List<Integer> entries = new ArrayList<>(), items = new ArrayList<>();
            for (int i = markerSlot + 2; i < end; i++) {
                if (menu.getSlot(i).getItem().isEmpty()) continue;
                (locked ? items : entries).add(i);
            }
            boolean disabled = locked && lore(menu.getSlot(actionSlot).getItem()).size() >= 2;
            result.add(new Block(stageOf(marker), markerSlot, actionSlot, locked, disabled, entries, items));
            slot = end;
        }
        return result;
    }

    private double scrollMax(List<Block> blocks) {
        int total = 0;
        for (Block block : blocks) total += block.height();
        return Math.max(0, total - VIEW_H);
    }

    private void drawList(GuiGraphics g, List<Block> blocks, double mouseX, double mouseY) {
        g.enableScissor((int) Math.ceil(originX + LIST_X * scale), (int) Math.ceil(originY + LIST_Y * scale),
            (int) Math.floor(originX + LIST_RIGHT * scale), (int) Math.floor(originY + LIST_BOTTOM * scale));
        boolean inList = inside(mouseX, mouseY, LIST_X, LIST_Y, LIST_RIGHT - LIST_X, VIEW_H);
        int top = LIST_Y - (int) scroll;
        try {
            for (Block block : blocks) {
                if (block.locked) drawLocked(g, block, top, mouseX, mouseY, inList);
                else drawOpen(g, block, top, mouseX, mouseY, inList);
                top += block.height();
            }
        } finally {
            g.disableScissor();
        }
        double max = scrollMax(blocks);
        g.fill(443, LIST_Y + 2, 445, LIST_BOTTOM - 2, 0xFF233647);
        int thumb = LIST_Y + 2 + (int) (max <= 0 ? 0 : scroll / max * 157);
        g.fill(442, thumb, 446, thumb + 70, BLUE);
    }

    private void drawOpen(GuiGraphics g, Block block, int top, double mouseX, double mouseY, boolean inList) {
        g.fillGradient(22, top + 4, 437, top + 100, 0xFF1D3042, 0xFF101D2A);
        g.drawString(font, "0" + block.stage + "  /  연구 단계", 31, top + 13, BLUE, false);
        g.drawString(font, "개방됨", 428 - font.width("개방됨"), top + 13, BLUE, false);
        ItemStack action = menu.getSlot(block.actionSlot).getItem();
        if (!action.isEmpty()) {
            List<String> lore = lore(action);
            String cost = lore.isEmpty() ? "" : " · " + lore.getFirst();
            button(g, 111, top + 8, 126, 19, name(action) + cost,
                inList && inside(mouseX, mouseY, 111, top + 8, 126, 19),
                System.currentTimeMillis() >= requestUntil);
        }
        for (int i = 0; i < block.entries.size() && i < 12; i++) {
            int slot = block.entries.get(i);
            int left = 31 + (i % 6) * 66, tileY = top + 31 + (i / 6) * 65;
            ItemStack stack = menu.getSlot(slot).getItem();
            boolean active = block.stage == selectedStage && i == selectedIndex;
            boolean hover = inList && inside(mouseX, mouseY, left, tileY, 62, 59);
            boolean planned = !lore(stack).isEmpty() && lore(stack).getFirst().equals("제작법 준비 중");
            panel(g, left, tileY, 62, 59, hover ? 0xFF29465F : 0xFF132130,
                active ? BLUE : hover ? 0xFF6593B5 : 0xFF364E63);
            if (stack.isEmpty()) continue;
            icon(g, stack, left + 20, tileY + 9, 1.4f);
            if (planned) {
                g.fill(left + 1, tileY + 1, left + 61, tileY + 41, 0x99101823);
                g.drawCenteredString(font, font.plainSubstrByWidth(name(stack), 60), left + 31, tileY + 44, MUTED);
            } else {
                g.drawCenteredString(font, font.plainSubstrByWidth(name(stack), 60), left + 31, tileY + 44, TEXT);
            }
        }
    }

    private void drawLocked(GuiGraphics g, Block block, int top, double mouseX, double mouseY, boolean inList) {
        int centerX = (PANEL_X + PANEL_RIGHT) / 2;
        panel(g, PANEL_X, top + 4, PANEL_W, 96, 0xFF0C1622, 0xFF2A4155);
        diamond(g, 54, top + 52, 30);
        g.pose().pushPose();
        try {
            g.pose().translate(54, top + 52, 0);
            g.pose().scale(2.4f, 2.4f, 1);
            g.drawCenteredString(font, Integer.toString(block.stage), 0, -4, TEXT);
        } finally {
            g.pose().popPose();
        }
        chains(g, PANEL_X + 2, top + 6, PANEL_W - 4, 92);

        List<ItemStack> icons = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        for (int slot : block.items) {
            ItemStack stack = menu.getSlot(slot).getItem();
            if (stack.isEmpty()) continue;
            List<String> lore = lore(stack);
            icons.add(stack);
            texts.add(lore.isEmpty() ? name(stack) : lore.getFirst());
        }
        String goldText = goldRatio(lore(menu.getSlot(block.actionSlot).getItem()));
        int lineY = top + 44, curX = centerX;
        int total = 0;
        for (String text : texts) total += 18 + font.width(text) + 16;
        total += 18 + font.width(goldText);
        curX = centerX - total / 2;
        for (int i = 0; i < icons.size(); i++) {
            g.renderItem(icons.get(i), curX, lineY - 8);
            g.drawString(font, texts.get(i), curX + 18, lineY - 4, shortfall(texts.get(i)) ? RED : TEXT, false);
            curX += 18 + font.width(texts.get(i)) + 16;
        }
        ItemStack coin = menu.getSlot(52).getItem();
        if (!coin.isEmpty()) g.renderItem(coin, curX, lineY - 8);
        g.drawString(font, goldText, curX + 18, lineY - 4, goldShort(lore(menu.getSlot(block.actionSlot).getItem())) ? RED : GOLD, false);

        int buttonX = centerX - BUTTON_W / 2;
        boolean hover = inList && !block.disabled && inside(mouseX, mouseY, buttonX, top + BUTTON_Y, BUTTON_W, BUTTON_H);
        button(g, buttonX, top + BUTTON_Y, BUTTON_W, BUTTON_H, "개방하기", hover, !block.disabled);
        if (block.disabled) g.drawCenteredString(font, "이전 단계 개방 필요", centerX, top + 96, MUTED);
    }

    private void diamond(GuiGraphics g, int cx, int cy, int r) {
        for (int y = cy - r; y <= cy + r; y++) {
            int half = r - Math.abs(y - cy);
            g.fill(cx - half, y, cx + half, y + 1, 0xFF0A1520);
        }
        for (int t = 0; t <= r; t++) {
            g.fill(cx + t, cy - r + t, cx + t + 1, cy - r + t + 1, DIAMOND);
            g.fill(cx - t, cy - r + t, cx - t + 1, cy - r + t + 1, DIAMOND);
            g.fill(cx + r - t, cy + t, cx + r - t + 1, cy + t + 1, DIAMOND);
            g.fill(cx - r + t, cy + t, cx - r + t + 1, cy + t + 1, DIAMOND);
        }
    }

    private void chains(GuiGraphics g, int px, int py, int pw, int ph) {
        int steps = 42;
        for (int i = 0; i < steps; i++) {
            double t = (i + 0.5) / steps;
            boolean even = i % 2 == 0;
            link(g, px + (int) (pw * t), py + (int) (ph * t), even);
            link(g, px + pw - (int) (pw * t), py + (int) (ph * t), even);
        }
    }

    private static void link(GuiGraphics g, int x, int y, boolean horiz) {
        if (horiz) {
            g.fill(x - 3, y - 2, x + 3, y + 2, CHAIN);
            g.fill(x - 1, y - 1, x + 1, y + 1, CHAIN_DARK);
        } else {
            g.fill(x - 2, y - 3, x + 2, y + 3, CHAIN);
            g.fill(x - 1, y - 1, x + 1, y + 1, CHAIN_DARK);
        }
    }

    private void drawDetail(GuiGraphics g, double x, double y) {
        panel(g, 455, 69, 166, 235, 0xFF142333, 0xFF3C5871);
        g.drawString(font, "제작법 상세", 467, 82, BLUE, false);
        ItemStack detail = menu.getSlot(51).getItem();
        panel(g, 511, 100, 54, 52, 0xFF0C1723, 0xFF4B6C87);
        if (!detail.isEmpty()) {
            icon(g, detail, 522, 110, 2);
            g.drawCenteredString(font, name(detail), 538, 163, TEXT);
            int lineY = 181;
            for (Component line : detail.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines()) {
                String text = ChatFormatting.stripFormatting(line.getString());
                g.drawString(font, font.plainSubstrByWidth(text, 146), 466, lineY, MUTED, false);
                lineY += 12;
                if (lineY > 228) break;
            }
        }
        ItemStack action = menu.getSlot(50).getItem();
        if (!action.isEmpty()) {
            List<String> costs = lore(action);
            g.drawString(font, "선택 연구 · " + (costs.isEmpty() ? "" : costs.getFirst()), 467, 258, GOLD, false);
            button(g, 467, 276, 142, 22, "연구 시작", inside(x, y, 467, 276, 142, 22), System.currentTimeMillis() >= requestUntil);
        }
        ItemStack gold = menu.getSlot(52).getItem();
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

    private static List<String> lore(ItemStack stack) {
        return stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().stream()
            .map(line -> ChatFormatting.stripFormatting(line.getString())).toList();
    }

    private static boolean isMarker(ItemStack stack) {
        return !stack.isEmpty() && MARKER.matcher(name(stack)).matches();
    }

    private static int stageOf(ItemStack stack) {
        String n = name(stack);
        int i = 0;
        while (i < n.length() && Character.isDigit(n.charAt(i))) i++;
        try { return Integer.parseInt(n.substring(0, i)); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private static String goldRatio(List<String> actionLore) {
        if (actionLore.isEmpty()) return "0/0";
        String raw = actionLore.getFirst().replace("골드", "").replace(",", "").trim();
        String[] parts = raw.split("/");
        try {
            long have = Long.parseLong(parts[0].trim());
            long need = parts.length > 1 ? Long.parseLong(parts[1].trim()) : 0;
            return "%,d/%,d".formatted(have, need);
        } catch (RuntimeException ignored) {
            return raw;
        }
    }

    private static boolean goldShort(List<String> actionLore) {
        if (actionLore.isEmpty()) return false;
        String[] parts = actionLore.getFirst().replace("골드", "").replace(",", "").trim().split("/");
        try {
            return parts.length > 1 && Long.parseLong(parts[0].trim()) < Long.parseLong(parts[1].trim());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean shortfall(String ratio) {
        String[] parts = ratio.split("/");
        try {
            return parts.length > 1 && Long.parseLong(parts[0].trim()) < Long.parseLong(parts[1].trim());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private double localX(double x) { return (x - originX) / scale; }
    private double localY(double y) { return (y - originY) / scale; }
    private static boolean inside(double x, double y, int left, int top, int w, int h) {
        return x >= left && x < left + w && y >= top && y < top + h;
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        scroll = Math.clamp(scroll - vertical * 32, 0, scrollMax(blocks()));
        return true;
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button != 0) return true;
        pressX = localX(x);
        pressY = localY(y);
        dragging = inside(pressX, pressY, LIST_X, LIST_Y, LIST_RIGHT - LIST_X, VIEW_H);
        moved = false;
        return true;
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && dragging) {
            if (moved || Math.abs(localY(y) - pressY) > 3) {
                moved = true;
                scroll = Math.clamp(scroll - dy / scale, 0, scrollMax(blocks()));
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
        if (inside(x, y, 467, 276, 142, 22)) sendClick(50);
        else if (inside(x, y, 599, 15, 23, 24)) onClose();
        else if (wasDragging && inside(x, y, LIST_X, LIST_Y, LIST_RIGHT - LIST_X, VIEW_H)) {
            double listY = y - LIST_Y + scroll;
            int top = 0;
            for (Block block : blocks()) {
                int height = block.height();
                if (listY >= top && listY < top + height) {
                    double by = listY - top;
                    if (block.locked) {
                        int buttonX = (PANEL_X + PANEL_RIGHT) / 2 - BUTTON_W / 2;
                        if (!block.disabled && inside(x, by, buttonX, BUTTON_Y, BUTTON_W, BUTTON_H)) sendClick(block.actionSlot);
                    } else if (inside(x, by, 111, 8, 126, 19)) sendClick(block.actionSlot);
                    else {
                        int col = (int) ((x - 31) / 66), row = (int) ((by - 31) / 65);
                        if (x >= 31 && col >= 0 && col < 6 && (x - 31) % 66 < 62
                            && by >= 31 && row >= 0 && row < 2 && (by - 31) % 65 < 59) {
                            int index = col + row * 6;
                            if (index < block.entries.size()) {
                                int slot = block.entries.get(index);
                                if (sendClick(slot)) {
                                    selectedStage = block.stage;
                                    selectedIndex = index;
                                }
                            }
                        }
                    }
                    break;
                }
                top += height;
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
