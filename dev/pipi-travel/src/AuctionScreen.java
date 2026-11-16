package dev.pipi.travel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

/**
 * 전리품 경매 window (server: PipiLife RaidAuction). Hidden chat lines:
 *   PIPI_AUC_OPEN, PIPI_AUC:raid|cur|remainMs|totalMs|bidder|price|minBid|gold|ended|name~state~winner~itemId~count;...,
 *   PIPI_AUC_MSG:text, PIPI_AUC_END.
 * Commands: /pipilife auction bid <amount> | instant | open. ESC closes the window; /pipilife auction open reopens it.
 */
final class AuctionScreen extends Screen {
    private record Lot(String name, int state, String winner, ItemStack icon) {}

    // ---- state pushed by the server ----
    private static boolean active, ended, pendingOpen;
    private static String raid = "", bidder = "-";
    private static int cur;
    private static long remainMs, totalMs = 20000, price, minBid, gold, stateAt;
    private static Lot[] lots = new Lot[0];
    private static String message = "";
    private static long messageAt;
    private static Object owner;

    private static final int W = 220, H = 292;
    private static final int GOLD = 0xffe8c579, TEXT = 0xffe6eef6, MUTED = 0xff8d9bb0, LABEL = 0xffd99a72;

    private String input = "";
    private float scale = 1;
    private int left, top;

    AuctionScreen() { super(Component.literal("전리품 경매")); }

    private static Minecraft client() { return Minecraft.getInstance(); }

    private static void own() {
        Object c = client().getConnection();
        if (owner != c) { owner = c; active = false; pendingOpen = false; }
    }

    // ---- network ----
    static void receive(ClientChatReceivedEvent.System event) {
        String text = event.getMessage().getString();
        if (!text.startsWith("PIPI_AUC")) return;
        event.setCanceled(true);
        own();
        try {
            if (text.startsWith("PIPI_AUC_OPEN")) { pendingOpen = true; active = true; }
            else if (text.startsWith("PIPI_AUC_END")) ended = true;
            else if (text.startsWith("PIPI_AUC_MSG:")) {
                message = text.substring(13);
                messageAt = System.currentTimeMillis();
                if (!(client().screen instanceof AuctionScreen) && client().player != null) client().gui.setOverlayMessage(Component.literal("§c" + message), false);
            } else if (text.startsWith("PIPI_AUC:")) {
                String[] p = text.substring(9).split("\\|", -1);
                if (p.length < 10) return;
                raid = p[0];
                cur = Integer.parseInt(p[1].trim());
                remainMs = Long.parseLong(p[2].trim());
                totalMs = Math.max(1, Long.parseLong(p[3].trim()));
                bidder = p[4];
                price = Long.parseLong(p[5].trim());
                minBid = Long.parseLong(p[6].trim());
                gold = Long.parseLong(p[7].trim());
                ended = p[8].trim().equals("1");
                String[] ls = p.length > 9 && !p[9].isEmpty() ? p[9].split(";") : new String[0];
                Lot[] out = new Lot[ls.length];
                for (int i = 0; i < ls.length; i++) {
                    String[] f = ls[i].split("~", -1);
                    ItemStack icon = new ItemStack(Items.BARRIER);
                    try {
                        ResourceLocation id = ResourceLocation.tryParse(f.length > 3 ? f[3] : "");
                        Item item = id == null ? null : BuiltInRegistries.ITEM.get(id);
                        if (item != null && item != Items.AIR) icon = new ItemStack(item, Math.max(1, f.length > 4 ? Integer.parseInt(f[4].trim()) : 1));
                    } catch (RuntimeException ignored) { }
                    out[i] = new Lot(f[0], f.length > 1 ? Integer.parseInt(f[1].trim()) : 0, f.length > 2 ? f[2] : "", icon);
                }
                lots = out;
                stateAt = System.currentTimeMillis();
                active = true;
            }
        } catch (RuntimeException ignored) { }
    }

    static void clientTick(ClientTickEvent.Post event) {
        var c = client();
        if (c.player == null) return;
        own();
        if (pendingOpen && active && c.screen == null) { pendingOpen = false; c.setScreen(new AuctionScreen()); }
    }

    private static void send(String cmd) {
        var c = client().getConnection();
        if (c != null) c.sendCommand(cmd);
    }

    private static String fmt(long v) { return String.format(java.util.Locale.ROOT, "%,d", v); }

    private static long remaining() { return ended ? 0 : Math.max(0, remainMs - (System.currentTimeMillis() - stateAt)); }

    // ---- screen ----
    @Override public boolean isPauseScreen() { return false; }

    @Override protected void init() {
        scale = Math.min(1f, Math.min((height - 8) / (float) H, (width - 8) / (float) W));
        left = Math.round((width - W * scale) / 2);
        top = Math.round((height - H * scale) / 2);
    }

    private double lx(double mx) { return (mx - left) / scale; }
    private double ly(double my) { return (my - top) / scale; }
    private static boolean hit(double x, double y, int x0, int y0, int w, int h) { return x >= x0 && x < x0 + w && y >= y0 && y < y0 + h; }

    private void coin(GuiGraphics g, int x, int y) {
        g.fill(x + 1, y, x + 7, y + 8, 0xff8a6410);
        g.fill(x, y + 1, x + 8, y + 7, 0xff8a6410);
        g.fill(x + 1, y + 1, x + 7, y + 7, 0xfff2c94c);
        g.fill(x + 3, y + 2, x + 5, y + 6, 0xffb8861a);
    }

    private void button(GuiGraphics g, int x, int y, int w, int h, String label, boolean on, int mx, int my) {
        boolean hov = on && hit(mx, my, x, y, w, h);
        g.fill(x, y, x + w, y + h, on ? 0xff5a7088 : 0xff2c3644);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, hov ? 0xff3e5470 : 0xff2a3a4e, hov ? 0xff2c3e54 : 0xff1c2836);
        g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, on ? TEXT : MUTED);
    }

    private void ring(GuiGraphics g, int cx, int cy, int r, float frac, int color) {
        int pts = 160;
        for (int i = 0; i < pts; i++) {
            if (i >= Math.round(pts * frac)) break;
            double a = -Math.PI / 2 + i * 2 * Math.PI / pts;
            int x = cx + (int) Math.round(Math.cos(a) * r), y = cy + (int) Math.round(Math.sin(a) * r);
            g.fill(x - 1, y - 1, x + 2, y + 2, color);
        }
    }

    @Override public void renderBackground(GuiGraphics g, int mx, int my, float pt) { g.fill(0, 0, width, height, 0x88050810); }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        super.render(g, mouseX, mouseY, pt);
        int mx = (int) Math.floor(lx(mouseX)), my = (int) Math.floor(ly(mouseY));
        g.pose().pushPose();
        g.pose().translate(left, top, 0);
        g.pose().scale(scale, scale, 1f);
        // frame
        g.fill(-1, -1, W + 1, H + 1, 0xff3c4a5c);
        g.fillGradient(0, 0, W, H, 0xff141c28, 0xff0b1018);
        g.fillGradient(0, 0, W, 20, 0xff1f4a50, 0xff142a32);
        g.fill(0, 20, W, 21, 0x5590e0e8);
        g.drawCenteredString(font, "전리품 경매", W / 2, 6, TEXT);
        button(g, W - 18, 3, 14, 14, "×", true, mx, my);
        g.drawCenteredString(font, raid, W / 2, 28, TEXT);
        g.fill(10, 41, W - 10, 42, 0x40ffffff);
        // lot row
        int n = lots.length, gap = 34, x0 = W / 2 - ((n - 1) * gap) / 2;
        for (int i = 0; i < n; i++) {
            Lot l = lots[i];
            int cx = x0 + i * gap, bx = cx - 12, by = 47;
            boolean live = l.state() == 1 || i == cur && !ended && l.state() == 0;
            g.fill(bx - 1, by - 1, bx + 25, by + 25, live ? 0xffe8c579 : 0xff3a4656);
            g.fill(bx, by, bx + 24, by + 24, 0xff0f151e);
            g.renderItem(l.icon(), bx + 4, by + 4);
            g.renderItemDecorations(font, l.icon(), bx + 4, by + 4);
            String st = switch (l.state()) { case 1 -> "진행 중"; case 2 -> "낙찰"; case 3 -> "유찰"; default -> "대기"; };
            int col = switch (l.state()) { case 1 -> GOLD; case 2 -> 0xff8fd99a; case 3 -> 0xffe07070; default -> MUTED; };
            g.drawCenteredString(font, st, cx, by + 28, col);
            if (l.state() == 2 && !l.winner().isEmpty()) {
                g.pose().pushPose();
                g.pose().translate(cx, by + 38, 0);
                g.pose().scale(.7f, .7f, 1f);
                g.drawCenteredString(font, font.plainSubstrByWidth(l.winner(), 60), 0, 0, 0xffbfe8c6);
                g.pose().popPose();
            }
        }
        g.fill(10, 94, W - 10, 95, 0x305a8cd8);
        g.fill(W / 2 - 3, 95, W / 2 + 4, 96, 0x905a8cd8);
        g.fill(W / 2 - 1, 96, W / 2 + 2, 97, 0x905a8cd8);
        // current lot banner
        Lot lot = cur >= 0 && cur < n ? lots[cur] : null;
        g.fillGradient(30, 101, W - 30, 117, 0xc0782838, 0xc0401622);
        g.fill(30, 101, W - 30, 102, 0x60ff8090);
        String banner = ended ? "경매 종료" : lot == null ? "-" : lot.name();
        g.drawCenteredString(font, font.plainSubstrByWidth(banner, W - 66), W / 2, 105, 0xffffd6dc);
        // big icon + countdown ring
        int cx = W / 2, cy = 145;
        long rem = remaining();
        ring(g, cx, cy, 23, 1f, 0xff1d2532);
        if (!ended && lot != null && lot.state() == 1) ring(g, cx, cy, 23, Math.clamp(rem / (float) totalMs, 0f, 1f), rem < 5000 ? 0xffe0603a : 0xffe8a83a);
        g.fill(cx - 17, cy - 17, cx + 17, cy + 17, 0xff3a4656);
        g.fill(cx - 16, cy - 16, cx + 16, cy + 16, 0xff0f151e);
        if (lot != null) {
            g.pose().pushPose();
            g.pose().translate(cx - 16, cy - 16, 0);
            g.pose().scale(2f, 2f, 1f);
            g.renderItem(lot.icon(), 0, 0);
            g.renderItemDecorations(font, lot.icon(), 0, 0);
            g.pose().popPose();
        }
        if (!ended && lot != null && lot.state() == 1) g.drawCenteredString(font, (rem + 999) / 1000 + "초", cx, cy + 27, rem < 5000 ? 0xffff8a6a : MUTED);
        else if (lot != null && lot.state() >= 2) g.drawCenteredString(font, lot.state() == 2 ? "낙찰" : "유찰", cx, cy + 27, lot.state() == 2 ? 0xff8fd99a : 0xffe07070);
        // bidder / price
        g.drawString(font, "◆", 26, 186, 0xffc0603a, false);
        g.drawString(font, "입찰자 닉네임", 36, 186, LABEL, false);
        g.drawString(font, bidder.isEmpty() ? "-" : bidder, 118, 186, GOLD, false);
        g.drawString(font, "◆", 26, 200, 0xffc0603a, false);
        g.drawString(font, "현재 입찰 가격", 36, 200, LABEL, false);
        g.drawString(font, fmt(price), 118, 200, TEXT, false);
        coin(g, 121 + font.width(fmt(price)), 200);
        // bid controls
        g.fill(0, 214, W, 215, 0x40ffffff);
        g.fill(0, 215, W, 266, 0x40000000);
        boolean canBid = active && !ended && lot != null && lot.state() == 1;
        g.drawString(font, "입찰가 입력", 10, 225, TEXT, false);
        g.fill(70, 220, 152, 236, 0xff4a5868);
        g.fill(71, 221, 151, 235, 0xff070b10);
        String shown = input.isEmpty() ? "" : fmt(Long.parseLong(input));
        g.drawString(font, shown + ((System.currentTimeMillis() / 500) % 2 == 0 && canBid ? "_" : ""), 75, 225, TEXT, false);
        button(g, 158, 220, 54, 16, "입찰", canBid && !input.isEmpty(), mx, my);
        g.drawString(font, "최소 입찰가", 10, 247, TEXT, false);
        String mb = fmt(minBid);
        g.drawString(font, mb, 140 - font.width(mb), 247, TEXT, false);
        coin(g, 143, 247);
        button(g, 158, 242, 54, 16, "즉시 입찰", canBid, mx, my);
        g.fill(10, 270, W - 10, 271, 0x40ffffff);
        g.drawString(font, "소지금", 14, 278, TEXT, false);
        String gs = fmt(gold);
        g.drawString(font, gs, W - 26 - font.width(gs), 278, TEXT, false);
        coin(g, W - 22, 278);
        if (!message.isEmpty() && System.currentTimeMillis() - messageAt < 3500) {
            int mw = font.width(message) + 10;
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            g.fill(W / 2 - mw / 2, 166, W / 2 + mw / 2, 180, 0xe0301418);
            g.drawCenteredString(font, message, W / 2, 169, 0xffff8a7a);
            g.pose().popPose();
        }
        g.pose().popPose();
    }

    private void placeBid() {
        if (input.isEmpty()) return;
        send("pipilife auction bid " + input);
        input = "";
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double x = lx(mouseX), y = ly(mouseY);
        if (button == 0) {
            if (hit(x, y, W - 18, 3, 14, 14)) { onClose(); return true; }
            if (hit(x, y, 158, 220, 54, 16)) { placeBid(); return true; }
            if (hit(x, y, 158, 242, 54, 16)) { send("pipilife auction instant"); return true; }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean charTyped(char c, int mods) {
        if (c >= '0' && c <= '9' && input.length() < 12) { if (!(input.isEmpty() && c == '0')) input += c; return true; }
        return super.charTyped(c, mods);
    }

    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_BACKSPACE) { if (!input.isEmpty()) input = input.substring(0, input.length() - 1); return true; }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { placeBid(); return true; }
        return super.keyPressed(key, scan, mods);
    }
}
