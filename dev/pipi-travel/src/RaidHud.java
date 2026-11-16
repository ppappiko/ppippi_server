package dev.pipi.travel;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.lwjgl.glfw.GLFW;

/**
 * Raid HUD pieces (server: PipiLife RaidDeepDark / PartySystem, hidden chat lines cancelled here):
 *   PIPI_BOSS:name|hp|max|gates|releasedMask|locked|label|rage   + PIPI_BOSS_END  - Lost Ark style boss bar (top centre); rage -1 = hidden
 *   PIPI_VOTE:enter|leave:total:left:states:myIndex:raidName       + PIPI_VOTE_END:1|0 - entry/exit vote popup under the crosshair
 *   PIPI_PINVITE:inviter:seconds                                  + PIPI_PINVITE_END  - party invite popup above the crosshair
 * Keys: Y = 찬성/수락, N = 반대/거절. While a vote / invite is unanswered a modal {@link VoteScreen} is always open
 * (replacing any other screen, dimmed, cursor shown, game input blocked, ESC ignored) until it is answered or ends.
 */
final class RaidHud {
    static final KeyMapping YES_KEY = new KeyMapping("투표 찬성 / 초대 수락", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, "PipiTravel");
    static final KeyMapping NO_KEY = new KeyMapping("투표 반대 / 초대 거절", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, "PipiTravel");

    private static final int LINES = 50;
    private static final int[] LINE_COLORS = {0xffc8283c, 0xffd8682a, 0xffc9a52c, 0xff4ea04a, 0xff2a9aa0, 0xff3a62c8, 0xff8a3ac8};

    // boss
    private static boolean boss;
    private static String bossName = "", bossLabel = "";
    private static long bossHp, bossMax = 1;
    private static int[] gates = {};
    private static int released;
    private static boolean locked;
    private static int rage = -1;
    private static double lagHp = -1;
    private static long lastFrame;
    // vote
    private static boolean vote, voteEnter;
    private static int voteTotal = 1, voteLeft, voteMe;
    private static long voteAt, voteEndAt;
    private static int voteResult = -1; // -1 running, 0 cancelled, 1 passed
    private static String voteStates = "", voteRaid = "";
    // invite
    private static boolean invite;
    private static String inviter = "";
    private static long inviteAt, inviteMs = 20000;
    private static Object owner;

    private RaidHud() {}

    private static Minecraft client() { return Minecraft.getInstance(); }

    private static void own() {
        Object c = client().getConnection();
        if (owner != c) { owner = c; boss = false; vote = false; invite = false; }
    }

    // ---- network ----
    static void receive(ClientChatReceivedEvent.System event) {
        String text = event.getMessage().getString();
        if (!text.startsWith("PIPI_BOSS") && !text.startsWith("PIPI_VOTE") && !text.startsWith("PIPI_PINVITE")) return;
        event.setCanceled(true);
        own();
        try {
            if (text.startsWith("PIPI_BOSS_END")) boss = false;
            else if (text.startsWith("PIPI_BOSS:")) {
                String[] p = text.substring(10).split("\\|", -1);
                if (p.length < 3) return;
                bossName = p[0];
                bossHp = Long.parseLong(p[1].trim());
                bossMax = Math.max(1, Long.parseLong(p[2].trim()));
                if (p.length > 3 && !p[3].isBlank()) {
                    String[] g = p[3].split(",");
                    gates = new int[g.length];
                    for (int i = 0; i < g.length; i++) gates[i] = Integer.parseInt(g[i].trim());
                } else gates = new int[0];
                released = p.length > 4 && !p[4].isBlank() ? Integer.parseInt(p[4].trim()) : 0;
                locked = p.length > 5 && p[5].trim().equals("1");
                bossLabel = p.length > 6 ? p[6] : "";
                rage = p.length > 7 && !p[7].isBlank() ? Integer.parseInt(p[7].trim()) : -1;
                if (!boss || lagHp < bossHp) lagHp = bossHp;
                boss = true;
            } else if (text.startsWith("PIPI_VOTE_END")) {
                if (vote) { voteResult = text.endsWith("1") ? 1 : 0; voteEndAt = System.currentTimeMillis(); }
            } else if (text.startsWith("PIPI_VOTE:")) {
                String[] p = text.substring(10).split(":", 6);
                if (p.length < 6) return;
                voteEnter = p[0].equals("enter");
                voteTotal = Math.max(1, Integer.parseInt(p[1]));
                voteLeft = Integer.parseInt(p[2]);
                voteStates = p[3];
                voteMe = Integer.parseInt(p[4]);
                voteRaid = p[5];
                voteAt = System.currentTimeMillis();
                voteResult = -1;
                vote = true;
            } else if (text.startsWith("PIPI_PINVITE_END")) invite = false;
            else if (text.startsWith("PIPI_PINVITE:")) {
                String[] p = text.substring(13).split(":");
                inviter = p[0];
                inviteMs = p.length > 1 ? Math.max(1, Integer.parseInt(p[1].trim())) * 1000L : 20000;
                inviteAt = System.currentTimeMillis();
                invite = true;
            }
        } catch (RuntimeException ignored) { }
    }

    private static boolean voteOpen() { return vote && voteResult < 0; }
    private static boolean inviteOpen() { return invite && System.currentTimeMillis() - inviteAt < inviteMs; }

    private static void send(String cmd) {
        var c = client().getConnection();
        if (c != null) c.sendCommand(cmd);
    }

    private static boolean myVotePending() {
        // safety: a vote whose END line never arrived stops asking 2 s after its timer ran out
        return voteOpen() && System.currentTimeMillis() - voteAt < voteLeft * 50L + 2000 && voteMe >= 0 && voteMe < voteStates.length() && voteStates.charAt(voteMe) == '0';
    }

    private static void yes() {
        if (voteOpen()) { send("pipilife raid vote yes"); if (voteMe >= 0 && voteMe < voteStates.length()) voteStates = voteStates.substring(0, voteMe) + "1" + voteStates.substring(voteMe + 1); }
        else if (inviteOpen()) { send("pipilife party accept"); invite = false; }
    }

    private static void no() {
        if (voteOpen()) { send("pipilife raid vote no"); if (voteMe >= 0 && voteMe < voteStates.length()) voteStates = voteStates.substring(0, voteMe) + "2" + voteStates.substring(voteMe + 1); }
        else if (inviteOpen()) { send("pipilife party decline"); invite = false; }
    }

    static void key(ClientTickEvent.Post event) {
        var client = client();
        if (client.player == null) {
            while (YES_KEY.consumeClick()) { }
            while (NO_KEY.consumeClick()) { }
            return;
        }
        own();
        // modal popup: opens right away whenever there is something to answer (replacing any other screen), closes once answered / ended
        boolean want = !client.player.isDeadOrDying() && (myVotePending() || inviteOpen());
        if (client.screen instanceof VoteScreen) {
            if (!want) client.setScreen(null);
        } else if (want) {
            if (client.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) client.player.closeContainer();
            client.setScreen(new VoteScreen());
        }
        if (client.screen != null) {
            while (YES_KEY.consumeClick()) { }
            while (NO_KEY.consumeClick()) { }
            return;
        }
        while (YES_KEY.consumeClick()) yes();
        while (NO_KEY.consumeClick()) no();
    }

    /** Hide the vanilla boss bar while our bar is shown. */
    static void layerPre(RenderGuiLayerEvent.Pre event) {
        if (boss && event.getName().equals(VanillaGuiLayers.BOSS_OVERLAY)) event.setCanceled(true);
    }

    private static boolean visible() {
        var client = client();
        return client.player != null && client.level != null && !client.options.hideGui && !PipiTravel.customScreen(client.screen);
    }

    static void draw(GuiGraphics g) {
        own();
        if (!visible()) return;
        if (boss) drawBoss(g);
        drawPopups(g, client().screen instanceof VoteScreen);
    }

    /** HUD popups; while the modal VoteScreen is open it draws the interactive popup itself, so the HUD copy is skipped. */
    private static void drawPopups(GuiGraphics g, boolean modalOpen) {
        if (!inviteOpen()) invite = false;
        if (vote && voteResult >= 0 && System.currentTimeMillis() - voteEndAt > 1300) vote = false;
        if (modalOpen) return;
        if (invite) drawInvite(g);
        if (vote) drawVote(g);
    }

    // ---- boss bar ----
    private static void drawBoss(GuiGraphics g) {
        var font = client().font;
        int sw = g.guiWidth();
        int bw = Math.min(340, sw - 60), x0 = (sw - bw) / 2, y0 = 6;
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0 : Math.min(.2f, (now - lastFrame) / 1000f);
        lastFrame = now;
        if (lagHp < bossHp) lagHp = bossHp;
        lagHp = Math.max(bossHp, lagHp - Math.max(bossMax * .002, (lagHp - bossHp) * 3.5 * dt));
        // frame
        g.fill(x0 - 3, y0 - 3, x0 + bw + 3, y0 + 35, 0xff3a3046);
        g.fill(x0 - 2, y0 - 2, x0 + bw + 2, y0 + 34, 0xee0d0a12);
        g.fill(x0 - 2, y0 - 2, x0 + bw + 2, y0 - 1, 0x40ffffff);
        // emblem
        int ecx = x0 + 15, ecy = y0 + 16;
        ClassIcons.diamond(g, ecx, ecy, 16, 0xffb08850, 0xff1c1226);
        ClassIcons.diamond(g, ecx, ecy, 12, 0xff6a3aa8, 0xff120a1a);
        g.renderItem(new ItemStack(Items.SCULK_SHRIEKER), ecx - 8, ecy - 8);
        int bx = x0 + 36, barW = bw - 36 - 34, by = y0 + 11, bh = 13;
        g.drawString(font, bossName, bx, y0, 0xffe8d9ff);
        // multi-line bar
        double per = bossMax / (double) LINES;
        int lines = bossHp <= 0 ? 0 : (int) Math.ceil(bossHp / per - 1e-9);
        double cur = lines <= 0 ? 0 : (bossHp - (lines - 1) * per) / per;
        g.fill(bx - 1, by - 1, bx + barW + 1, by + bh + 1, 0xff000000);
        g.fill(bx, by, bx + barW, by + bh, 0xff1a1418);
        if (lines > 1) g.fill(bx, by, bx + barW, by + bh, LINE_COLORS[(lines - 2) % LINE_COLORS.length]);
        if (lines > 0) {
            int lagLines = (int) Math.ceil(lagHp / per - 1e-9);
            double lagFrac = lagLines > lines ? 1 : (lagHp - (lines - 1) * per) / per;
            int fw = (int) Math.round(barW * Math.clamp(cur, 0, 1)), lw = (int) Math.round(barW * Math.clamp(lagFrac, 0, 1));
            if (lw > fw) g.fill(bx + fw, by, bx + lw, by + bh, 0xffe8e0d0);
            int col = LINE_COLORS[(lines - 1) % LINE_COLORS.length];
            g.fill(bx, by, bx + fw, by + bh, col);
            g.fill(bx, by, bx + fw, by + 3, 0x40ffffff);
            g.fill(bx, by + bh - 3, bx + fw, by + bh, 0x30000000);
        }
        String hpText = "%,d / %,d".formatted(bossHp, bossMax);
        g.drawCenteredString(font, hpText, bx + barW / 2, by + 3, 0xffffffff);
        g.drawString(font, "×" + Math.max(0, lines), bx + barW + 5, by + 3, 0xffe8c579);
        if (rage >= 0) { // rage gauge (only sent once the boss is low enough)
            int oy = y0 + 28;
            boolean full = rage >= 100;
            g.fill(bx, oy, bx + barW, oy + 3, 0xff241c2a);
            g.fill(bx, oy, bx + Math.round(barW * Math.clamp(rage / 100f, 0f, 1f)), oy + 3, full ? ((now / 200) % 2 == 0 ? 0xffff3030 : 0xffffa040) : 0xffe0602a);
            g.pose().pushPose();
            g.pose().translate(bx + barW + 5, oy - 1, 0);
            g.pose().scale(.6f, .6f, 1f);
            g.drawString(font, "분노 " + Math.clamp(rage, 0, 100) + "%", 0, 0, full ? 0xffff6a4a : 0xffe8a070);
            g.pose().popPose();
        }
    }

    // ---- popups ----
    private static int panel(GuiGraphics g, int cx, int top, int w, int h) {
        int x = cx - w / 2;
        g.fill(x - 1, top - 1, x + w + 1, top + h + 1, 0xff55647a);
        g.fillGradient(x, top, x + w, top + h, 0xf01b2433, 0xf00f141d);
        g.fill(x, top, x + w, top + 16, 0x50000000);
        g.fill(x, top + 16, x + w, top + 17, 0x40ffffff);
        return x;
    }

    private static void person(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 2, y, x + 6, y + 4, color);
        g.fill(x, y + 5, x + 8, y + 10, color);
        g.fill(x + 1, y + 4, x + 7, y + 5, color);
    }

    private static void timer(GuiGraphics g, int x, int y, int w, float frac) {
        g.fill(x, y, x + w, y + 4, 0xff2a3240);
        g.fill(x, y, x + Math.round(w * Math.clamp(frac, 0f, 1f)), y + 4, 0xff5ec8e8);
    }

    private static String keyName(KeyMapping k) { return k.getTranslatedKeyMessage().getString(); }

    // HUD-only (non-interactive) popups: shown when the modal VoteScreen is not open (already voted, result flash, dead...)
    private static final float POP_SCALE = .85f;
    private static final int POP_W = 190, VOTE_H = 104, INVITE_H = 70, VOTE_BTN_Y = 86, INVITE_BTN_Y = 57;

    /** Popup origin (centre-top) in GUI coordinates. */
    private static float voteOriginY(int sh) { return sh / 2f + 16; }
    private static float inviteOriginY(int sh) { return sh / 2f - 16 - INVITE_H * POP_SCALE; }

    private static void drawVote(GuiGraphics g) {
        var font = client().font;
        int sw = g.guiWidth(), sh = g.guiHeight();
        float s = POP_SCALE;
        int w = POP_W, h = VOTE_H;
        g.pose().pushPose();
        g.pose().translate(sw / 2f, voteOriginY(sh), 400);
        g.pose().scale(s, s, 1f);
        int x = panel(g, 0, 0, w, h);
        String title = voteResult == 1 ? "전원 찬성!" : voteResult == 0 ? "투표가 취소되었습니다" : voteEnter ? "입장 하시겠습니까?" : "퇴장 하시겠습니까?";
        g.drawCenteredString(font, title, 0, 4, voteResult == 1 ? 0xff82d99a : voteResult == 0 ? 0xffff7b7b : 0xffe6eef6);
        g.pose().pushPose();
        g.pose().translate(0, 22, 0);
        g.pose().scale(1.4f, 1.4f, 1f);
        g.drawCenteredString(font, voteRaid, 0, 0, 0xffe8c579);
        g.pose().popPose();
        int n = voteStates.length(), px = -(n * 12 - 4) / 2;
        for (int i = 0; i < n; i++) {
            char c = voteStates.charAt(i);
            int col = c == '1' ? 0xfff2f5ff : c == '2' ? 0xffe05050 : 0xff4c5566;
            person(g, px + i * 12, 42, col);
            if (i == voteMe) g.fill(px + i * 12, 54, px + i * 12 + 8, 55, 0xffe8c579);
        }
        g.drawCenteredString(font, "모든 인원이 찬성해야 합니다", 0, 60, 0xff9fb4ca);
        long el = System.currentTimeMillis() - voteAt;
        float frac = voteResult >= 0 ? 0 : (voteLeft * 50f - el) / (voteTotal * 50f);
        timer(g, x + 8, 75, w - 16, frac);
        boolean mine1 = voteMe >= 0 && voteMe < n && voteStates.charAt(voteMe) == '1', mine2 = voteMe >= 0 && voteMe < n && voteStates.charAt(voteMe) == '2';
        String status = voteResult >= 0 ? "" : mine1 ? "찬성 완료 · 다른 인원 대기 중" : mine2 ? "반대했습니다"
            : "[" + keyName(YES_KEY) + "] 찬성    [" + keyName(NO_KEY) + "] 반대";
        g.drawCenteredString(font, status, 0, VOTE_BTN_Y, mine1 ? 0xff82d99a : mine2 ? 0xffff7b7b : 0xff9fb4ca);
        g.pose().popPose();
    }

    private static void drawInvite(GuiGraphics g) {
        var font = client().font;
        int sw = g.guiWidth(), sh = g.guiHeight();
        float s = POP_SCALE;
        int w = POP_W, h = INVITE_H;
        g.pose().pushPose();
        g.pose().translate(sw / 2f, inviteOriginY(sh), 400);
        g.pose().scale(s, s, 1f);
        int x = panel(g, 0, 0, w, h);
        g.drawCenteredString(font, "파티 초대", 0, 4, 0xffe6eef6);
        g.drawCenteredString(font, font.plainSubstrByWidth(inviter + "님의 파티 초대", w - 10), 0, 24, 0xffe8c579);
        g.drawCenteredString(font, "파티에 참가하시겠습니까?", 0, 36, 0xff9fb4ca);
        float frac = 1f - (System.currentTimeMillis() - inviteAt) / (float) inviteMs;
        timer(g, x + 8, 49, w - 16, frac);
        g.drawCenteredString(font, "[" + keyName(YES_KEY) + "] 수락    [" + keyName(NO_KEY) + "] 거절", 0, INVITE_BTN_Y, 0xff9fb4ca);
        g.pose().popPose();
    }

    /**
     * Modal vote / invite popup: small (180x90) panel centred on the crosshair, dimmed background, mouse cursor, game input
     * blocked (multiplayer: no pause). Drawn unscaled in screen GUI coordinates with real vanilla {@link Button} widgets, so
     * hit-testing is done by the widgets themselves on exactly the drawn rectangles. ESC does nothing; it closes once answered or ended.
     */
    static final class VoteScreen extends Screen {
        private static final int W = 180, H = 90, BTN_H = 20;
        private int left, top;
        private boolean inviteMode;

        VoteScreen() { super(Component.literal("투표")); }

        /** Invite is shown only when there is no own vote to answer (the vote takes priority). */
        private static boolean wantsInvite() { return !myVotePending() && inviteOpen(); }

        @Override protected void init() {
            left = (width - W) / 2;
            top = (height - H) / 2;
            inviteMode = wantsInvite();
            int bw = (W - 22) / 2, by = top + H - BTN_H - 6;
            addRenderableWidget(Button.builder(Component.literal((inviteMode ? "수락" : "찬성") + " [" + keyName(YES_KEY) + "]"), b -> answer(true))
                .bounds(left + 8, by, bw, BTN_H).build());
            addRenderableWidget(Button.builder(Component.literal((inviteMode ? "거절" : "반대") + " [" + keyName(NO_KEY) + "]"), b -> answer(false))
                .bounds(left + W - 8 - bw, by, bw, BTN_H).build());
        }

        private void answer(boolean yes) {
            if (inviteMode) {
                if (inviteOpen()) { send(yes ? "pipilife party accept" : "pipilife party decline"); invite = false; }
            } else if (myVotePending()) {
                if (yes) yes(); else no();
            }
        }

        /** The popup switches between invite / vote content (e.g. a vote arrives while an invite is shown): rebuild the buttons once. */
        @Override public void tick() {
            if (wantsInvite() != inviteMode && (myVotePending() || inviteOpen())) rebuildWidgets();
        }

        @Override public boolean isPauseScreen() { return false; }
        @Override public boolean shouldCloseOnEsc() { return false; }

        /** Screen.render draws the background first, then the widgets: the panel goes here so the buttons sit on top of it. */
        @Override public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
            renderTransparentBackground(g);
            drawPanel(g);
        }

        private void drawPanel(GuiGraphics g) {
            int cx = left + W / 2;
            g.fill(left - 1, top - 1, left + W + 1, top + H + 1, 0xff55647a);
            g.fillGradient(left, top, left + W, top + H, 0xf01b2433, 0xf00f141d);
            g.fill(left, top, left + W, top + 14, 0x50000000);
            g.fill(left, top + 14, left + W, top + 15, 0x40ffffff);
            if (inviteMode) {
                g.drawCenteredString(font, font.plainSubstrByWidth(inviter + "님의 파티 초대", W - 10), cx, top + 3, 0xffe6eef6);
                g.drawCenteredString(font, "파티에 참가하시겠습니까?", cx, top + 26, 0xff9fb4ca);
                timer(g, left + 8, top + 52, W - 16, 1f - (System.currentTimeMillis() - inviteAt) / (float) inviteMs);
            } else {
                g.drawCenteredString(font, voteEnter ? "입장 하시겠습니까?" : "퇴장 하시겠습니까?", cx, top + 3, 0xffe6eef6);
                g.drawCenteredString(font, font.plainSubstrByWidth(voteRaid, W - 10), cx, top + 19, 0xffe8c579);
                int n = voteStates.length(), px = cx - (n * 12 - 4) / 2;
                for (int i = 0; i < n; i++) {
                    char c = voteStates.charAt(i);
                    person(g, px + i * 12, top + 31, c == '1' ? 0xfff2f5ff : c == '2' ? 0xffe05050 : 0xff4c5566);
                    if (i == voteMe) g.fill(px + i * 12, top + 43, px + i * 12 + 8, top + 44, 0xffe8c579);
                }
                timer(g, left + 8, top + 52, W - 16, (voteLeft * 50f - (System.currentTimeMillis() - voteAt)) / (voteTotal * 50f));
            }
        }

        @Override public boolean keyPressed(int key, int scan, int mods) {
            if (YES_KEY.matches(key, scan)) { answer(true); return true; }
            if (NO_KEY.matches(key, scan)) { answer(false); return true; }
            return super.keyPressed(key, scan, mods);
        }

        @Override public void onClose() {
            if (!myVotePending() && !inviteOpen()) super.onClose(); // still unanswered: stay open
        }
    }
}
