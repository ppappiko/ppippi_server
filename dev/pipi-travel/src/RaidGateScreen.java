package dev.pipi.travel;

import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

/**
 * Boss entry window of the 봉인된 비석 (server menu "깊은 어둠 · 봉인된 비석", PipiLife RaidDeepDark.openGate).
 * Slots: 4 info (lore: 권장 장비 레벨 N / 파티원 n / m), 9..12 party heads (lore: 장비 레벨 N, 파티장), 36..44 rewards,
 * 49 enter (LIME_DYE = enabled, otherwise GRAY_DYE named with the reason), 53 close.
 * Left: the sealed boss (a client-side Warden model) over a dark sculk backdrop, title + expected rewards at the bottom.
 */
final class RaidGateScreen extends ContainerScreen {
    static final String TITLE = "깊은 어둠 · 봉인된 비석";
    /** Same window for the 통곡의 비석 (PipiLife RaidSorrow): the art shows the wraith lord (fx_sorrow_* item models) instead of the warden. */
    static final String SORROW_TITLE = "통곡의 왕좌 · 통곡의 비석";
    static boolean handles(String title) { return TITLE.equals(title) || SORROW_TITLE.equals(title); }
    private boolean sorrow() { return SORROW_TITLE.equals(title.getString()); }
    private static ItemStack fx(String id) { return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("pipifacilities", id))); }
    private static final int W = 520, H = 300, ART_W = 356, RX = 362, RW = 152;
    private static final int TEXT = 0xffe6eef6, MUTED = 0xff8fa6b8, GOLD = 0xffe6c588, CYAN = 0xff5ff6ee, LINE = 0xff2a3a46;
    private static final Pattern NUM = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)");
    private static final Pattern LAST_NUM = Pattern.compile("(\\d+)\\s*$");
    private static LivingEntity boss;
    private float scale, left, top;
    private boolean pending;
    private final long opened = System.currentTimeMillis();

    RaidGateScreen(ChestMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    @Override protected void init() {
        scale = Math.max(.01f, Math.min((width - 18f) / W, (height - 18f) / H));
        left = (width - W * scale) / 2;
        top = (height - H * scale) / 2;
    }

    private ItemStack item(int slot) { return menu.getSlot(slot).getItem(); }
    private static String plain(ItemStack it) { return ChatFormatting.stripFormatting(it.getHoverName().getString()); }
    private static List<String> lore(ItemStack item) {
        return item.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().stream().map(l -> ChatFormatting.stripFormatting(l.getString())).toList();
    }
    private String infoLine(String prefix) {
        for (String l : lore(item(4))) if (l.startsWith(prefix)) return l.substring(prefix.length()).trim();
        return "";
    }
    private boolean canEnter() { return item(49).is(Items.LIME_DYE); }

    private LivingEntity boss() {
        if (boss == null || boss.level() != minecraft.level) boss = minecraft.level == null ? null : EntityType.WARDEN.create(minecraft.level);
        return boss;
    }

    @Override public void render(GuiGraphics g, int mx, int my, float delta) {
        double x = (mx - left) / scale, y = (my - top) / scale;
        float t = (System.currentTimeMillis() - opened) / 1000f;
        g.fill(0, 0, width, height, 0xd0030609);
        ItemStack tip = ItemStack.EMPTY;
        g.pose().pushPose();
        try {
            g.pose().translate(left, top, 0);
            g.pose().scale(scale, scale, 1);
            g.fill(-1, -1, W + 1, H + 1, 0xff1d2c36);

            // ---- left: sealed boss ----
            g.fillGradient(0, 0, ART_W, H, 0xff03080b, 0xff0a1d23);
            for (int i = 0; i < 5; i++) { // faint light shafts
                int sx = 30 + i * 70, sw = 10 + (i % 2) * 8;
                g.fillGradient(sx, 0, sx + sw, 200, 0x00000000, 0x1846e0e8);
            }
            g.fillGradient(0, 150, ART_W, 215, 0x00000000, 0x2a2fd8e2); // sculk glow on the floor
            for (int i = 0; i < 46; i++) { // drifting soul specks
                float px = (i * 73.13f) % ART_W, sp = 8 + (i % 7) * 3, py = H - ((t * sp + i * 41.7f) % (H + 20));
                int a = (int) (90 + 80 * Math.sin(t * 2 + i));
                int s = i % 5 == 0 ? 2 : 1;
                g.fill((int) px, (int) py, (int) px + s, (int) py + s, (Math.max(0, Math.min(255, a)) << 24) | 0x5ff6ee);
            }
            LivingEntity e = sorrow() ? null : boss();
            if (sorrow()) { // floating wraith lord: body + two hands bobbing out of phase
                float size = 4.5f, cx = ART_W / 2f, cy = 150 + (float) Math.sin(t * 1.1) * 3f;
                icon(g, fx("sorrow_body"), Math.round(cx - 8 * size), Math.round(cy - 8 * size), size);
                for (int side = -1; side <= 1; side += 2) {
                    float hy = cy - 20 + (float) Math.sin(t * 1.6 + side) * 4f, hx = cx + side * 22;
                    icon(g, fx(side < 0 ? "sorrow_hand_r" : "sorrow_hand_l"), Math.round(hx - 8 * size), Math.round(hy - 8 * size), size);
                }
            }
            if (e != null) {
                float sway = (float) Math.sin(t * .6) * 8f;
                e.yBodyRot = e.yBodyRotO = 180 + 20 + sway;
                e.setYRot(180 + 20 + sway);
                e.yHeadRot = e.yHeadRotO = 180 + 10 + sway;
                e.setXRot(-6);
                e.tickCount = (int) (t * 20);
                Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
                Quaternionf cam = new Quaternionf().rotateX((float) Math.toRadians(8));
                pose.mul(cam);
                float bob = (float) Math.sin(t * 1.1) * 2f;
                InventoryScreen.renderEntityInInventory(g, ART_W / 2f, 196 + bob, 64, new Vector3f(), pose, cam, e);
            }
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            g.fillGradient(0, 168, ART_W, H, 0x00000000, 0xf0020507);
            // title + rewards
            g.pose().pushPose();
            g.pose().translate(ART_W / 2f, 206, 0);
            g.pose().scale(2f, 2f, 1);
            g.drawCenteredString(font, plain(item(4)).isEmpty() ? "깊은 어둠" : plain(item(4)), 0, 0, GOLD);
            g.pose().popPose();
            g.drawCenteredString(font, "기대 보상", ART_W / 2, 226, MUTED);
            g.fill(14, 240, ART_W - 14, 241, 0xff8a6f3e);
            g.fill(ART_W / 2 - 3, 241, ART_W / 2 + 4, 242, 0xff8a6f3e);
            g.fill(ART_W / 2 - 1, 242, ART_W / 2 + 2, 243, 0xff8a6f3e);
            int rx = 14;
            for (int s = 36; s <= 44; s++) {
                ItemStack it = item(s);
                if (it.isEmpty()) continue;
                boolean hov = hit(x, y, rx, 252, 32, 32);
                g.fill(rx, 252, rx + 32, 284, hov ? 0xffa78c59 : 0xff31414d);
                g.fillGradient(rx + 1, 253, rx + 31, 283, 0xff16222b, 0xff0b131a);
                g.renderItem(it, rx + 8, 260);
                g.renderItemDecorations(font, it, rx + 8, 260);
                if (hov) tip = it;
                rx += 36;
            }
            g.pose().popPose();

            // ---- right: info + party ----
            g.fillGradient(RX - 6, 0, W, H, 0xff0b1218, 0xff070c10);
            g.fill(RX - 6, 0, RX - 5, H, LINE);
            // emblem
            g.fillGradient(RX, 6, RX + RW, 96, 0xff111a24, 0xff0a1017);
            ring(g, RX + RW / 2, 51, 36, 2, 0xff7d6c4c);
            disc(g, RX + RW / 2, 51, 30, 0xff1b2236, 0xff0d1120);
            ring(g, RX + RW / 2, 51, 30, 1, 0xff3a4a66);
            g.fill(RX + 8, 51, RX + RW / 2 - 38, 52, 0xff5d5038);
            g.fill(RX + RW / 2 + 39, 51, RX + RW - 8, 52, 0xff5d5038);
            ItemStack stele = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("pipifacilities", sorrow() ? "wailing_stele" : "sealed_stele")));
            if (stele.is(Items.AIR)) stele = item(4);
            icon(g, stele, RX + RW / 2 - 18, 33, 2.25f);
            if (hit(x, y, RX + RW / 2 - 30, 21, 60, 60) && !item(4).isEmpty()) tip = item(4);
            // recommended level
            row(g, 102, "권장 장비 레벨", number(infoLine("권장 장비 레벨"), LAST_NUM, 1), TEXT);
            Matcher pm = NUM.matcher(infoLine("파티원"));
            int count = 0, cap = 4;
            if (pm.find()) { count = Integer.parseInt(pm.group(1)); cap = Math.max(1, Math.min(4, Integer.parseInt(pm.group(2)))); }
            g.fill(RX, 124, RX + RW, 144, 0xff121c25);
            g.drawString(font, "파티원", RX + 8, 130, TEXT, false);
            String c = count + "/" + cap;
            g.drawString(font, c, RX + RW - 8 - font.width(c), 130, TEXT, false);
            // members
            for (int i = 0; i < cap; i++) {
                int my2 = 150 + i * 28;
                ItemStack head = item(9 + i);
                g.fill(RX + 8, my2, RX + 30, my2 + 22, 0xff2a3642);
                if (head.isEmpty()) {
                    g.fill(RX + 9, my2 + 1, RX + 29, my2 + 21, 0xff141c24);
                    g.drawString(font, "--", RX + 40, my2 + 7, 0xff56677a, false);
                    continue;
                }
                String name = plain(head);
                PlayerInfo info = minecraft.getConnection() == null ? null : minecraft.getConnection().getPlayerInfo(name);
                if (info != null) PlayerFaceRenderer.draw(g, info.getSkin(), RX + 9, my2 + 1, 20);
                else g.renderItem(head, RX + 11, my2 + 3);
                List<String> l = lore(head);
                boolean lead = l.stream().anyMatch(s -> s.contains("파티장"));
                g.drawString(font, font.plainSubstrByWidth(name, RW - 74), RX + 36, my2 + 2, lead ? GOLD : TEXT, false);
                g.drawString(font, l.isEmpty() ? "-" : l.getFirst(), RX + 36, my2 + 13, MUTED, false);
                if (lead) g.drawString(font, "파티장", RX + RW - 6 - font.width("파티장"), my2 + 2, 0xffc9a052, false);
            }
            // enter button
            boolean ok = canEnter(), hov = ok && hit(x, y, RX, 266, RW, 28) && !pending;
            if (ok) {
                g.fill(RX, 266, RX + RW, 294, hov ? 0xffe2b65e : 0xffa98242);
                g.fillGradient(RX + 1, 267, RX + RW - 1, 293, hov ? 0xffc79a4c : 0xff9b7436, hov ? 0xff8f6a2c : 0xff6e5020);
                g.drawCenteredString(font, sorrow() ? plain(item(49)) + "하기" : "입장하기", RX + RW / 2, 276, 0xfffff4dc); // 통곡의 왕좌: "1관문 입장" / "2관문 입장"
            } else {
                g.fill(RX, 266, RX + RW, 294, 0xff3a4550);
                g.fillGradient(RX + 1, 267, RX + RW - 1, 293, 0xff222b33, 0xff171e24);
                String why = item(49).isEmpty() ? "입장할 수 없습니다" : plain(item(49));
                g.drawCenteredString(font, font.plainSubstrByWidth(why, RW - 8), RX + RW / 2, 276, 0xff8a97a4);
            }
            // close
            boolean cl = hit(x, y, W - 16, 2, 14, 14);
            g.drawCenteredString(font, "✕", W - 9, 5, cl ? 0xffff8a8a : MUTED);
            // member tooltips
            for (int i = 0; i < cap; i++) if (hit(x, y, RX + 8, 150 + i * 28, RW - 8, 22) && !item(9 + i).isEmpty()) tip = item(9 + i);
        } finally { g.pose().popPose(); }
        if (!tip.isEmpty()) g.renderTooltip(font, tip, mx, my);
    }

    private void row(GuiGraphics g, int y, String label, String value, int color) {
        g.fill(RX, y, RX + RW, y + 20, 0xff121c25);
        g.drawString(font, label, RX + 8, y + 6, TEXT, false);
        g.drawString(font, value, RX + RW - 8 - font.width(value), y + 6, color, false);
    }

    private static String number(String s, Pattern p, int group) {
        Matcher m = p.matcher(s);
        return m.find() ? m.group(group) : (s.isEmpty() ? "-" : s);
    }

    /** Filled circle with a vertical gradient (per-row fills). */
    private static void disc(GuiGraphics g, int cx, int cy, int r, int topColor, int bottomColor) {
        for (int dy = -r; dy <= r; dy++) {
            int w = (int) Math.round(Math.sqrt(r * r - dy * dy));
            float f = (dy + r) / (2f * r);
            g.fill(cx - w, cy + dy, cx + w, cy + dy + 1, lerp(topColor, bottomColor, f));
        }
    }

    /** Circle outline of the given thickness (per-row fills of the annulus). */
    private static void ring(GuiGraphics g, int cx, int cy, int r, int th, int color) {
        int ri = r - th;
        for (int dy = -r; dy <= r; dy++) {
            int wo = (int) Math.round(Math.sqrt(r * r - dy * dy));
            if (Math.abs(dy) > ri) { g.fill(cx - wo, cy + dy, cx + wo, cy + dy + 1, color); continue; }
            int wi = (int) Math.round(Math.sqrt(ri * ri - dy * dy));
            g.fill(cx - wo, cy + dy, cx - wi, cy + dy + 1, color);
            g.fill(cx + wi, cy + dy, cx + wo, cy + dy + 1, color);
        }
    }

    private static int lerp(int a, int b, float f) {
        int r = 0;
        for (int sh = 0; sh <= 24; sh += 8) {
            int ca = (a >>> sh) & 255, cb = (b >>> sh) & 255;
            r |= Math.round(ca + (cb - ca) * f) << sh;
        }
        return r;
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
        if (hit(x, y, RX, 266, RW, 28) && canEnter()) click(49);
        else if (hit(x, y, W - 16, 2, 14, 14)) onClose();
        return true;
    }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE || minecraft.options.keyInventory.matches(key, scan)) { onClose(); return true; }
        return true;
    }
    @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) { return true; }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) { return true; }
    @Override public boolean mouseReleased(double x, double y, int button) { return true; }
}
