package dev.pipi.travel;

import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import net.minecraft.world.item.component.ItemLore;

/** Waypoint map: parchment map on the left, collapsible category list on the right. */
final class WarpScreen extends ContainerScreen {
    static final String TITLE = "삐삐서버 · 웨이포인트";
    private static final int W = 690, H = 350;
    private static final int TEXT = 0xffe8edf4, MUTED = 0xff9cacbf, GOLD = 0xffebc77f;
    private static final int MAP_X = 6, MAP_Y = 30, MAP_W = 440, MAP_H = 314;
    private static final int LIST_X = 452, LIST_Y = 30, LIST_W = 232, LIST_H = 314;
    private static final int AX0 = 44, AX1 = 408, AY0 = 100, AY1 = 290;

    private record Wp(int slot, String id, String cat, String world, int x, int z, boolean open, boolean here, String name) {}

    private float scale, left, top;
    private long sentAt;
    private String world, selected;
    private boolean dropdown;
    private final Set<String> collapsed = new HashSet<>();
    private int scroll;

    WarpScreen(ChestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override protected void init() {
        scale = Math.max(.01f, Math.min((width - 16f) / W, (height - 16f) / H));
        left = (width - W * scale) / 2;
        top = (height - H * scale) / 2;
    }

    private static List<Component> lore(ItemStack stack) {
        return stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines();
    }

    private List<Wp> all() {
        List<Wp> out = new ArrayList<>();
        for (int i = 0; i < 45; i++) {
            ItemStack it = menu.getSlot(i).getItem();
            if (it.isEmpty()) continue;
            List<Component> l = lore(it);
            if (l.isEmpty()) continue;
            String s = ChatFormatting.stripFormatting(l.get(0).getString());
            if (s == null || !s.startsWith("wp:")) continue;
            String[] p = s.substring(3).split("\\|");
            if (p.length < 7) continue;
            try {
                out.add(new Wp(i, p[0], p[1], p[2], Integer.parseInt(p[3]), Integer.parseInt(p[4]), "1".equals(p[5]), "1".equals(p[6]),
                    ChatFormatting.stripFormatting(it.getHoverName().getString())));
            } catch (NumberFormatException ignored) { }
        }
        return out;
    }

    private static String friendly(String w) {
        return switch (w) { case "village" -> "마을"; case "world" -> "야생"; default -> w; };
    }

    private List<String> worlds(List<Wp> all) {
        List<String> out = new ArrayList<>();
        for (Wp w : all) if (!out.contains(w.world)) out.add(w.world);
        out.sort((a, b) -> Integer.compare(a.equals("village") ? 0 : 1, b.equals("village") ? 0 : 1));
        return out;
    }

    private void ensureState(List<Wp> all) {
        List<String> ws = worlds(all);
        if (world == null || !ws.contains(world)) {
            Wp here = all.stream().filter(Wp::here).findFirst().orElse(null);
            world = here != null ? here.world : ws.isEmpty() ? null : ws.get(0);
            if (here != null) selected = here.id;
        }
        if (selected != null && all.stream().noneMatch(w -> w.id.equals(selected) && w.world.equals(world))) selected = null;
        if (selected == null) {
            for (Wp w : all) if (w.world.equals(world)) { selected = w.id; break; }
        }
    }

    // ---- geometry ----
    private int[] pos(List<Wp> inWorld, Wp w) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (Wp o : inWorld) {
            minX = Math.min(minX, o.x); maxX = Math.max(maxX, o.x);
            minZ = Math.min(minZ, o.z); maxZ = Math.max(maxZ, o.z);
        }
        double dx = maxX - minX, dz = maxZ - minZ;
        double aw = AX1 - AX0, ah = AY1 - AY0;
        double s = dx == 0 && dz == 0 ? 0 : Math.min(dx == 0 ? Double.MAX_VALUE : aw / dx, dz == 0 ? Double.MAX_VALUE : ah / dz);
        double cx = (AX0 + AX1) / 2.0, cy = (AY0 + AY1) / 2.0;
        double px = cx + (w.x - (minX + maxX) / 2.0) * s, py = cy + (w.z - (minZ + maxZ) / 2.0) * s;
        return new int[]{(int) Math.round(px), (int) Math.round(py)};
    }

    // ---- drawing helpers ----
    private static void panel(GuiGraphics g, int x, int y, int w, int h, boolean active) {
        g.fill(x, y, x + w, y + h, active ? 0xff7b99b5 : 0xff344658);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, active ? 0xff263648 : 0xff17222e, 0xff101820);
    }

    private static boolean hit(double x, double y, int bx, int by, int w, int h) {
        return x >= bx && x < bx + w && y >= by && y < by + h;
    }

    private void button(GuiGraphics g, String label, int x, int y, int w, int h, double mx, double my, boolean enabled) {
        panel(g, x, y, w, h, enabled && hit(mx, my, x, y, w, h));
        g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, enabled ? TEXT : 0xff55606c);
    }

    private static void ellipse(GuiGraphics g, int cx, int cy, int rx, int ry, int color) {
        for (int dy = -ry; dy <= ry; dy++) {
            int half = (int) Math.round(rx * Math.sqrt(1 - (double) dy * dy / ((double) ry * ry + 1)));
            g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }

    private static void triangle(GuiGraphics g, int tipX, int tipY, int dirX, int dirY, int len, int baseHalf, int color) {
        for (int i = 0; i <= len; i++) {
            int half = (int) Math.round(baseHalf * (double) i / len);
            if (dirX == 0) g.fill(tipX - half, tipY + dirY * i, tipX + half + 1, tipY + dirY * i + 1, color);
            else g.fill(tipX + dirX * i, tipY - half, tipX + dirX * i + 1, tipY + half + 1, color);
        }
    }

    private static void lock(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 1, y, x + 4, y + 1, color);
        g.fill(x + 1, y, x + 2, y + 3, color);
        g.fill(x + 3, y, x + 4, y + 3, color);
        g.fill(x, y + 3, x + 5, y + 7, color);
    }

    private void crystal(GuiGraphics g, int cx, int cy, boolean open, boolean outlined, boolean hover, boolean sel) {
        // pedestal
        int stone = open ? 0xff3b4252 : 0xff30343c;
        if (outlined || sel || hover) g.fill(cx - 8, cy + 2, cx + 9, cy + 8, sel ? GOLD : outlined ? 0xffffffff : 0xffbff6ff);
        g.fill(cx - 7, cy + 3, cx + 8, cy + 7, stone);
        g.fill(cx - 4, cy, cx + 5, cy + 3, 0xff2e3440);
        // faceted crystal (bipyramid)
        int[] w = {1, 2, 3, 4, 4, 3, 2, 1};
        int[] light = open ? new int[]{0xffbff6ff, 0xff5fd6e6, 0xff2a9fb5} : new int[]{0xff9aa3aa, 0xff6f777e, 0xff4a5056};
        for (int i = 0; i < w.length; i++) {
            int y = cy - 17 + i * 2;
            g.fill(cx - w[i], y, cx + w[i] + 1, y + 2, light[1]);
            g.fill(cx - w[i], y, cx - w[i] / 2, y + 2, light[0]);
            g.fill(cx + w[i] / 2 + 1, y, cx + w[i] + 1, y + 2, light[2]);
        }
        if (!open) lock(g, cx - 2, cy - 11, 0xffd7dde4);
    }

    private void cloud(GuiGraphics g, int x, int y, int s) {
        int c = 0x40ffffff;
        ellipse(g, x, y, s, s / 2, c);
        ellipse(g, x + s / 2, y - s / 3, s * 2 / 3, s / 2, c);
        ellipse(g, x - s / 2, y - s / 6, s / 2, s / 3, c);
    }

    private void compass(GuiGraphics g, int cx, int cy) {
        int line = 0xff7a5c36, fill = 0xffa07a46;
        ellipse(g, cx, cy, 20, 20, 0x22604020);
        triangle(g, cx, cy - 22, 0, 1, 22, 4, line);
        triangle(g, cx, cy + 22, 0, -1, 22, 4, fill);
        triangle(g, cx - 22, cy, 1, 0, 22, 4, fill);
        triangle(g, cx + 22, cy, -1, 0, 22, 4, line);
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xff5a4426);
        g.drawCenteredString(font, "N", cx, cy - 34, 0xff5a4426);
    }

    @Override public void render(GuiGraphics g, int mx, int my, float delta) {
        double x = (mx - left) / scale, y = (my - top) / scale;
        List<Wp> all = all();
        ensureState(all);
        List<Wp> here = new ArrayList<>();
        for (Wp w : all) if (w.world.equals(world)) here.add(w);
        g.fill(0, 0, width, height, 0xb7081019);
        g.pose().pushPose();
        try {
            g.pose().translate(left, top, 0);
            g.pose().scale(scale, scale, 1);
            g.fillGradient(0, 0, W, 26, 0xff26394c, 0xff192737);
            g.drawCenteredString(font, "웨이포인트", W / 2, 9, TEXT);
            g.drawString(font, "✕", W - 16, 9, hit(x, y, W - 22, 3, 18, 18) ? TEXT : MUTED, false);

            // map
            g.fill(MAP_X, MAP_Y, MAP_X + MAP_W, MAP_Y + MAP_H, 0xff5a4426);
            g.fillGradient(MAP_X + 3, MAP_Y + 3, MAP_X + MAP_W - 3, MAP_Y + MAP_H - 3, 0xffe9d9b0, 0xffd6c08a);
            for (int gx = MAP_X + 3 + 40; gx < MAP_X + MAP_W - 3; gx += 40) g.fill(gx, MAP_Y + 3, gx + 1, MAP_Y + MAP_H - 3, 0x30705030);
            for (int gy = MAP_Y + 3 + 40; gy < MAP_Y + MAP_H - 3; gy += 40) g.fill(MAP_X + 3, gy, MAP_X + MAP_W - 3, gy + 1, 0x30705030);
            cloud(g, 90, 150, 26);
            cloud(g, 330, 120, 22);
            cloud(g, 250, 250, 30);
            cloud(g, 150, 290, 18);
            compass(g, MAP_X + MAP_W - 44, MAP_Y + MAP_H - 50);

            Wp hover = null;
            Wp sel = null;
            for (Wp w : here) {
                int[] p = pos(here, w);
                if (w.id.equals(selected)) sel = w;
                if (!dropdown && hit(x, y, p[0] - 9, p[1] - 19, 18, 28)) hover = w;
            }
            for (Wp w : here) {
                int[] p = pos(here, w);
                crystal(g, p[0], p[1], w.open, w.here, w == hover, w.id.equals(selected));
            }
            if (sel != null) {
                int[] p = pos(here, sel);
                int tw = font.width(sel.name) + 10;
                int lx = Math.max(MAP_X + 6, Math.min(MAP_X + MAP_W - 6 - tw, p[0] - tw / 2));
                g.fill(lx, p[1] + 11, lx + tw, p[1] + 24, 0xcc2a2014);
                g.drawCenteredString(font, sel.name, lx + tw / 2, p[1] + 14, GOLD);
            }
            if (hover != null && hover != sel) {
                int[] p = pos(here, hover);
                int tw = font.width(hover.name) + 10;
                int lx = Math.max(MAP_X + 6, Math.min(MAP_X + MAP_W - 6 - tw, p[0] - tw / 2));
                g.fill(lx, p[1] - 34, lx + tw, p[1] - 21, 0xdd101820);
                g.drawCenteredString(font, hover.name, lx + tw / 2, p[1] - 31, TEXT);
            }
            if (here.isEmpty()) g.drawCenteredString(font, "등록된 웨이포인트가 없습니다.", MAP_X + MAP_W / 2, MAP_Y + MAP_H / 2, 0xff5a4426);

            // home button
            boolean homeHover = hit(x, y, 412, 38, 28, 24);
            panel(g, 412, 38, 28, 24, homeHover);
            triangle(g, 426, 43, 0, 1, 7, 8, GOLD);
            g.fill(421, 50, 432, 58, GOLD);
            g.fill(425, 53, 428, 58, 0xff17222e);

            // list
            panel(g, LIST_X, LIST_Y, LIST_W, LIST_H, false);
            Map<String, List<Wp>> groups = new LinkedHashMap<>();
            for (Wp w : here) groups.computeIfAbsent(w.cat, k -> new ArrayList<>()).add(w);
            int row = LIST_Y + 6 - scroll;
            int bottom = LIST_Y + LIST_H - 40;
            for (var e : groups.entrySet()) {
                boolean closed = collapsed.contains(e.getKey());
                if (row >= LIST_Y && row + 20 <= bottom) {
                    g.fillGradient(LIST_X + 5, row, LIST_X + LIST_W - 5, row + 20, 0xff26394c, 0xff192737);
                    g.drawString(font, e.getKey() + (closed ? " ∧" : " ∨"), LIST_X + 12, row + 6, TEXT, false);
                }
                row += 22;
                if (closed) continue;
                for (Wp w : e.getValue()) {
                    if (row >= LIST_Y && row + 18 <= bottom) {
                        boolean on = w.id.equals(selected);
                        if (on) g.fill(LIST_X + 8, row, LIST_X + LIST_W - 8, row + 18, 0xff3d6b86);
                        else if (hit(x, y, LIST_X + 8, row, LIST_W - 16, 18)) g.fill(LIST_X + 8, row, LIST_X + LIST_W - 8, row + 18, 0xff233647);
                        g.drawString(font, "• " + w.name, LIST_X + 16, row + 5, w.open ? TEXT : 0xff6b7683, false);
                        if (!w.open) lock(g, LIST_X + LIST_W - 22, row + 5, 0xff8793a0);
                        else if (w.here) g.drawString(font, "현재", LIST_X + LIST_W - 36, row + 5, GOLD, false);
                    }
                    row += 20;
                }
            }
            boolean canGo = sel != null && sel.open && !sel.here;
            button(g, sel != null && !sel.open ? "잠김" : "이동", LIST_X + 8, LIST_Y + LIST_H - 34, LIST_W - 16, 26, x, y, canGo);

            // area dropdown (drawn last so it overlays)
            List<String> ws = worlds(all);
            button(g, (world == null ? "-" : friendly(world)) + "  ∨", MAP_X + 8, MAP_Y + 8, 120, 20, x, y, true);
            if (dropdown) {
                for (int i = 0; i < ws.size(); i++) {
                    int ry = MAP_Y + 30 + i * 20;
                    panel(g, MAP_X + 8, ry, 120, 20, hit(x, y, MAP_X + 8, ry, 120, 20) || ws.get(i).equals(world));
                    g.drawCenteredString(font, friendly(ws.get(i)), MAP_X + 68, ry + 6, TEXT);
                }
            }
        } finally {
            g.pose().popPose();
        }
    }

    private void click(int slot) {
        long now = System.currentTimeMillis();
        if (now - sentAt < 600 || menu.getSlot(slot).getItem().isEmpty() || minecraft == null || minecraft.getConnection() == null) return;
        sentAt = now;
        minecraft.getConnection().send(new ServerboundContainerClickPacket(menu.containerId, menu.getStateId(),
            slot, 0, ClickType.PICKUP, menu.getCarried().copy(), Int2ObjectMaps.emptyMap()));
    }

    private void choose(Wp w) {
        if (w.id.equals(selected)) { if (w.open && !w.here) click(w.slot); else if (!w.open) click(w.slot); }
        else selected = w.id;
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return true;
        double x = (mx - left) / scale, y = (my - top) / scale;
        List<Wp> all = all();
        ensureState(all);
        List<String> ws = worlds(all);
        if (hit(x, y, W - 22, 3, 18, 18)) { onClose(); return true; }
        if (dropdown) {
            for (int i = 0; i < ws.size(); i++)
                if (hit(x, y, MAP_X + 8, MAP_Y + 30 + i * 20, 120, 20)) { world = ws.get(i); selected = null; scroll = 0; }
            dropdown = false;
            return true;
        }
        if (hit(x, y, MAP_X + 8, MAP_Y + 8, 120, 20)) { dropdown = true; return true; }
        if (hit(x, y, 412, 38, 28, 24)) { click(49); return true; }
        List<Wp> here = new ArrayList<>();
        for (Wp w : all) if (w.world.equals(world)) here.add(w);
        for (Wp w : here) {
            int[] p = pos(here, w);
            if (hit(x, y, p[0] - 9, p[1] - 19, 18, 28)) { choose(w); return true; }
        }
        Wp sel = here.stream().filter(w -> w.id.equals(selected)).findFirst().orElse(null);
        if (hit(x, y, LIST_X + 8, LIST_Y + LIST_H - 34, LIST_W - 16, 26)) {
            if (sel != null) click(sel.slot);
            return true;
        }
        Map<String, List<Wp>> groups = new LinkedHashMap<>();
        for (Wp w : here) groups.computeIfAbsent(w.cat, k -> new ArrayList<>()).add(w);
        int row = LIST_Y + 6 - scroll;
        int bottom = LIST_Y + LIST_H - 40;
        for (var e : groups.entrySet()) {
            if (hit(x, y, LIST_X + 5, row, LIST_W - 10, 20) && row >= LIST_Y && row + 20 <= bottom) {
                if (!collapsed.remove(e.getKey())) collapsed.add(e.getKey());
                return true;
            }
            row += 22;
            if (collapsed.contains(e.getKey())) continue;
            for (Wp w : e.getValue()) {
                if (hit(x, y, LIST_X + 8, row, LIST_W - 16, 18) && row >= LIST_Y && row + 18 <= bottom) { choose(w); return true; }
                row += 20;
            }
        }
        return true;
    }

    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        scroll = Math.max(0, scroll - (int) Math.signum(dy) * 20);
        return true;
    }
    @Override public boolean mouseReleased(double x, double y, int button) { return true; }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) { return true; }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) onClose();
        return true;
    }
}
