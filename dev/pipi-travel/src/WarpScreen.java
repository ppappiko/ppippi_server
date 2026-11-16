package dev.pipi.travel;

import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/** Waypoint map: parchment map on the left, collapsible category list on the right. */
final class WarpScreen extends ContainerScreen {
    static final String TITLE = "삐삐서버 · 웨이포인트";
    static final String VIEW_TITLE = TITLE + " (보기)";
    static final KeyMapping VIEW_KEY = new KeyMapping("워프 지도", InputConstants.Type.KEYSYM, org.lwjgl.glfw.GLFW.GLFW_KEY_M, "PipiTravel");

    /** M pressed in game (no screen open): ask the server for the view-only warp map. */
    static void keyTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || mc.getConnection() == null) { while (VIEW_KEY.consumeClick()) { } return; }
        boolean fire = false;
        while (VIEW_KEY.consumeClick()) fire = true;
        if (fire) mc.getConnection().sendCommand("pipilife warp view");
    }
    private static final int W = 690, H = 350;
    private static final int TEXT = 0xffe8edf4, MUTED = 0xff9cacbf, GOLD = 0xffebc77f;
    private static final int MAP_X = 6, MAP_Y = 30, MAP_W = 440, MAP_H = 314;
    private static final int LIST_X = 452, LIST_Y = 30, LIST_W = 232, LIST_H = 314;
    /** Area dropdown sits in the title bar so it never covers map markers. */
    private static final int DD_X = 6, DD_Y = 3;
    private static final int AX0 = 44, AX1 = 408, AY0 = 100, AY1 = 290;

    // ---- painted maps: texture pixel (c, r) = world block (c / ppb + orgX, r / ppb + orgZ) ----
    /** world -> map texture: pipi_village2 (shown as 거주지) and pipi_town (shown as 마을). ppb = texture pixels per block. */
    private record MapDef(String world, ResourceLocation tex, int pw, int ph, int orgX, int orgZ, double ppb) {}
    private static final List<MapDef> MAPS = List.of(
        new MapDef("pipi_village2", ResourceLocation.fromNamespaceAndPath("pipitravel", "textures/gui/village2_map.png"), 585, 472, -111, -78, 1),
        new MapDef("pipi_town", ResourceLocation.fromNamespaceAndPath("pipitravel", "textures/gui/town_map.png"), 432, 354, -88, -84, 2));
    private static final String HOUSE_WORLD = "pipi_village2";
    private static final String PLOTS_PREFIX = "PIPI_PLOTS:";

    /** A residence plot: min corner (x, z), square size, owner name ("" = empty). */
    private record House(String name, int x, int z, int size, String owner) {}
    private static final Map<String, List<House>> HOUSES = new java.util.HashMap<>();

    /** Hidden server line PIPI_PLOTS:world;size;name,x,z,owner|name,x,z,owner|... */
    static void receive(ClientChatReceivedEvent.System event) {
        String text = event.getMessage().getString();
        if (!text.startsWith(PLOTS_PREFIX)) return;
        event.setCanceled(true);
        String[] head = text.substring(PLOTS_PREFIX.length()).split(";", 3);
        if (head.length < 3) return;
        List<House> list = new ArrayList<>();
        try {
            int size = Integer.parseInt(head[1].trim());
            for (String e : head[2].split("\\|")) {
                String[] p = e.split(",", -1);
                if (p.length < 4) continue;
                list.add(new House(p[0], Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()), size, p[3].trim()));
            }
        } catch (NumberFormatException ex) { return; }
        HOUSES.put(head[0].trim(), list);
    }

    private record Wp(int slot, String id, String cat, String world, int x, int z, boolean open, boolean here, String name) {}

    private record Me(String world, int x, int z) {}

    private final boolean view;
    private long hintUntil;
    private float scale, left, top;
    private long sentAt;
    private String world, selected;
    private boolean dropdown;
    private final Set<String> collapsed = new HashSet<>();
    private int scroll;

    WarpScreen(ChestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.view = VIEW_TITLE.equals(title.getString());
    }

    private Me me() {
        if (!view) return null;
        ItemStack it = menu.getSlot(48).getItem();
        if (it.isEmpty()) return null;
        List<Component> l = lore(it);
        if (l.isEmpty()) return null;
        String s = ChatFormatting.stripFormatting(l.get(0).getString());
        if (s == null || !s.startsWith("me|")) return null;
        String[] p = s.split("\\|");
        if (p.length < 4) return null;
        try { return new Me(p[1], Integer.parseInt(p[2].trim()), Integer.parseInt(p[3].trim())); }
        catch (NumberFormatException e) { return null; }
    }

    private void hint() { hintUntil = System.currentTimeMillis() + 2200; }

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
        return switch (w) { case "pipi_town" -> "마을"; case "pipi_village2" -> "거주지"; case "world" -> "야생"; default -> w; };
    }

    private List<String> worlds(List<Wp> all) {
        List<String> out = new ArrayList<>();
        for (Wp w : all) if (!out.contains(w.world)) out.add(w.world);
        out.sort((a, b) -> Integer.compare(worldRank(a), worldRank(b)));
        return out;
    }

    private static int worldRank(String w) { return switch (w) { case "pipi_town" -> 0; case "pipi_village2" -> 1; case "world" -> 2; default -> 3; }; }

    private void ensureState(List<Wp> all) {
        List<String> ws = worlds(all);
        if (world == null || !ws.contains(world)) {
            Wp here = all.stream().filter(Wp::here).findFirst().orElse(null);
            world = here != null ? here.world : ws.isEmpty() ? null : ws.get(0);
            if (here != null) selected = here.id;
            Me me = me();
            if (me != null && ws.contains(me.world)) world = me.world;
        }
        if (selected != null && all.stream().noneMatch(w -> w.id.equals(selected) && w.world.equals(world))) selected = null;
        if (selected == null) {
            for (Wp w : all) if (w.world.equals(world)) { selected = w.id; break; }
        }
    }

    // ---- geometry ----
    private MapDef mapDef() {
        for (MapDef m : MAPS) if (m.world.equals(world)) return m;
        return null;
    }

    private boolean mapMode() { return mapDef() != null; }

    /** Fitted image rectangle inside the map frame: {x, y, w, h, screen pixels per block}. */
    private double[] fit() {
        MapDef m = mapDef();
        double aw = MAP_W - 6, ah = MAP_H - 6, s = Math.min(aw / m.pw, ah / m.ph);
        double w = m.pw * s, h = m.ph * s;
        return new double[]{MAP_X + 3 + (aw - w) / 2, MAP_Y + 3 + (ah - h) / 2, w, h, s * m.ppb};
    }

    private int[] worldToMap(double wx, double wz) {
        MapDef m = mapDef();
        double[] f = fit();
        return new int[]{(int) Math.round(f[0] + (wx - m.orgX) * f[4]), (int) Math.round(f[1] + (wz - m.orgZ) * f[4])};
    }

    private int[] pos(List<Wp> inWorld, Wp w) {
        if (mapMode()) return worldToMap(w.x, w.z);
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

    /** Minecraft-style 16x16 house icons (dev/art/village2/make-house-icons.mjs): red roof = owned, grey roof = empty. */
    private static final ResourceLocation HOUSE_OWNED = ResourceLocation.fromNamespaceAndPath("pipitravel", "textures/gui/house_owned.png");
    private static final ResourceLocation HOUSE_EMPTY = ResourceLocation.fromNamespaceAndPath("pipitravel", "textures/gui/house_empty.png");

    private static void house(GuiGraphics g, int cx, int cy, boolean owned, boolean hover) {
        int x = cx - 8, y = cy - 9 - (hover ? 1 : 0); // hovered house hops up one pixel
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        g.blit(owned ? HOUSE_OWNED : HOUSE_EMPTY, x, y, 0f, 0f, 16, 16, 16, 16);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }

    private static boolean sameName(String a, String b) { return !a.isEmpty() && a.equalsIgnoreCase(b); }

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
            g.drawCenteredString(font, view ? "웨이포인트 (보기 전용 · M)" : "웨이포인트", W / 2, 9, TEXT);
            g.drawString(font, "✕", W - 16, 9, hit(x, y, W - 22, 3, 18, 18) ? TEXT : MUTED, false);

            // map
            g.fill(MAP_X, MAP_Y, MAP_X + MAP_W, MAP_Y + MAP_H, 0xff5a4426);
            g.fillGradient(MAP_X + 3, MAP_Y + 3, MAP_X + MAP_W - 3, MAP_Y + MAP_H - 3, 0xffe9d9b0, 0xffd6c08a);
            if (mapMode()) {
                double[] f = fit();
                MapDef md = mapDef();
                g.blit(md.tex, (int) Math.round(f[0]), (int) Math.round(f[1]), (int) Math.round(f[2]), (int) Math.round(f[3]), 0f, 0f, md.pw, md.ph, md.pw, md.ph);
            } else {
                for (int gx = MAP_X + 3 + 40; gx < MAP_X + MAP_W - 3; gx += 40) g.fill(gx, MAP_Y + 3, gx + 1, MAP_Y + MAP_H - 3, 0x30705030);
                for (int gy = MAP_Y + 3 + 40; gy < MAP_Y + MAP_H - 3; gy += 40) g.fill(MAP_X + 3, gy, MAP_X + MAP_W - 3, gy + 1, 0x30705030);
                cloud(g, 90, 150, 26);
                cloud(g, 330, 120, 22);
                cloud(g, 250, 250, 30);
                cloud(g, 150, 290, 18);
                compass(g, MAP_X + MAP_W - 44, MAP_Y + MAP_H - 50);
            }

            Wp hover = null;
            Wp sel = null;
            for (Wp w : here) {
                int[] p = pos(here, w);
                if (w.id.equals(selected)) sel = w;
                if (!dropdown && hit(x, y, p[0] - 9, p[1] - 19, 18, 28)) hover = w;
            }
            List<House> houses = HOUSE_WORLD.equals(world) ? HOUSES.getOrDefault(world, List.of()) : List.of();
            String me = minecraft != null && minecraft.player != null ? minecraft.player.getName().getString() : "";
            House houseHover = null;
            int[][] hc = new int[houses.size()][];
            for (int i = 0; i < houses.size(); i++) {
                House h = houses.get(i);
                hc[i] = worldToMap(h.x + h.size / 2.0, h.z + h.size / 2.0);
                if (hover == null && !dropdown && hit(x, y, hc[i][0] - 9, hc[i][1] - 8, 18, 16)) houseHover = h;
            }
            for (int i = 0; i < houses.size(); i++) {
                House h = houses.get(i);
                boolean mine = sameName(h.owner, me), hov = h == houseHover;
                if (mine || hov) {
                    int[] a = worldToMap(h.x, h.z), b = worldToMap(h.x + h.size, h.z + h.size);
                    int col = mine ? GOLD : 0xffffffff;
                    if (hov) g.fill(a[0], a[1], b[0], b[1], 0x30ffffff);
                    g.fill(a[0], a[1], b[0], a[1] + 1, col);
                    g.fill(a[0], b[1] - 1, b[0], b[1], col);
                    g.fill(a[0], a[1], a[0] + 1, b[1], col);
                    g.fill(b[0] - 1, a[1], b[0], b[1], col);
                }
                house(g, hc[i][0], hc[i][1], !h.owner.isEmpty(), hov);
            }
            for (Wp w : here) {
                int[] p = pos(here, w);
                crystal(g, p[0], p[1], w.open, w.here, w == hover, w.id.equals(selected));
            }
            Me mp0 = me();
            if (mp0 != null && mp0.world.equals(world) && mapMode()) {
                int[] mp = worldToMap(mp0.x + .5, mp0.z + .5);
                ellipse(g, mp[0], mp[1], 6, 6, 0xffffffff);
                ellipse(g, mp[0], mp[1], 4, 4, 0xffe03030);
                int tw = font.width("내 위치") + 8;
                int lx = Math.max(MAP_X + 6, Math.min(MAP_X + MAP_W - 6 - tw, mp[0] - tw / 2));
                g.fill(lx, mp[1] - 20, lx + tw, mp[1] - 8, 0xdd101820);
                g.drawCenteredString(font, "내 위치", lx + tw / 2, mp[1] - 17, 0xffffa0a0);
            }
            if (houseHover != null) {
                String tip = houseHover.owner.isEmpty() ? "비어있는 거주지" : houseHover.owner + "의 거주지";
                int i = houses.indexOf(houseHover);
                int tw = font.width(tip) + 10;
                int lx = Math.max(MAP_X + 6, Math.min(MAP_X + MAP_W - 6 - tw, hc[i][0] - tw / 2));
                int ty = Math.max(MAP_Y + 4, hc[i][1] - 25);
                g.fill(lx, ty, lx + tw, ty + 13, 0xdd101820);
                g.drawCenteredString(font, tip, lx + tw / 2, ty + 3, houseHover.owner.isEmpty() ? MUTED : TEXT);
            }
            if (sel != null && !dropdown) {
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
            {
            boolean homeOff = homeDisabled();
            boolean homeHover = hit(x, y, 412, 38, 28, 24);
            panel(g, 412, 38, 28, 24, homeHover && !homeOff);
            int homeCol = homeOff ? 0xff59616b : GOLD;
            triangle(g, 426, 43, 0, 1, 7, 8, homeCol);
            g.fill(421, 50, 432, 58, homeCol);
            g.fill(425, 53, 428, 58, 0xff17222e);
            if (homeOff && homeHover) {
                String tip = "야생에서는 거주지로 이동할 수 없습니다 · 사망하면 거주지로 돌아갑니다";
                int tw = font.width(tip) + 12;
                int lx = Math.max(4, 440 - tw);
                g.fill(lx, 64, lx + tw, 78, 0xee101820);
                g.drawString(font, tip, lx + 6, 67, TEXT, false);
            }
            }

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
            boolean canGo = !view && sel != null && sel.open && !sel.here;
            button(g, view ? "워프석에서만 이동 가능" : sel != null && !sel.open ? "잠김" : "이동", LIST_X + 8, LIST_Y + LIST_H - 34, LIST_W - 16, 26, x, y, canGo);

            // area dropdown (drawn last so it overlays)
            List<String> ws = worlds(all);
            button(g, (world == null ? "-" : friendly(world)) + "  ∨", DD_X, DD_Y, 120, 20, x, y, true);
            if (dropdown) {
                for (int i = 0; i < ws.size(); i++) {
                    int ry = DD_Y + 21 + i * 20;
                    panel(g, DD_X, ry, 120, 20, hit(x, y, DD_X, ry, 120, 20) || ws.get(i).equals(world));
                    g.drawCenteredString(font, friendly(ws.get(i)), DD_X + 60, ry + 6, TEXT);
                }
            }
            if (System.currentTimeMillis() < hintUntil) {
                String msg = "워프석에서만 이동할 수 있습니다";
                int tw = font.width(msg) + 16;
                int lx = MAP_X + (MAP_W - tw) / 2;
                g.fill(lx, MAP_Y + MAP_H - 30, lx + tw, MAP_Y + MAP_H - 12, 0xee101820);
                g.drawCenteredString(font, msg, lx + tw / 2, MAP_Y + MAP_H - 24, GOLD);
            }
        } finally {
            g.pose().popPose();
        }
    }

    private boolean homeDisabled() {
        ItemStack it = menu.getSlot(49).getItem();
        return it.isEmpty() || it.getItem() != net.minecraft.world.item.Items.RED_BED;
    }

    private void click(int slot) {
        if (view && slot != 49) { hint(); return; }
        if (slot == 49 && homeDisabled()) return;
        long now = System.currentTimeMillis();
        if (now - sentAt < 600 || menu.getSlot(slot).getItem().isEmpty() || minecraft == null || minecraft.getConnection() == null) return;
        sentAt = now;
        minecraft.getConnection().send(new ServerboundContainerClickPacket(menu.containerId, menu.getStateId(),
            slot, 0, ClickType.PICKUP, menu.getCarried().copy(), Int2ObjectMaps.emptyMap()));
    }

    private void choose(Wp w) {
        if (view) { if (w.id.equals(selected)) hint(); else selected = w.id; return; }
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
                if (hit(x, y, DD_X, DD_Y + 21 + i * 20, 120, 20)) { world = ws.get(i); selected = null; scroll = 0; }
            dropdown = false;
            return true;
        }
        if (hit(x, y, DD_X, DD_Y, 120, 20)) { dropdown = true; return true; }
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
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE || (view && VIEW_KEY.matches(key, scan))) onClose();
        return true;
    }
}
