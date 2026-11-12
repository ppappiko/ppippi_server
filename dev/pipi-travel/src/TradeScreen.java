package dev.pipi.travel;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

/**
 * Player-to-player trade window, same panel style as Armory/Smelt/Upgrade (760x380). Server protocol (hidden chat, cancelled here):
 *   PIPI_TRADE_BEGIN:open:myName:partnerName:myGold:partnerGold:myLock:partnerLock:myConfirm:partnerConfirm:myBalance
 *   PIPI_TRADE_SLOT:side(0 me|1 partner):idx(0-8):ItemWire     (non-empty slots)
 *   PIPI_TRADE_END   (state is applied here; open=1 opens the screen)
 *   PIPI_TRADE_CHAT:sender:text | PIPI_TRADE_MSG:text | PIPI_TRADE_CLOSE:done(0|1):reason
 *   (long lines arrive as PIPI_ARM_PART and are reassembled by ArmoryScreen, which forwards PIPI_TRADE_* here)
 * Client commands: pipilife trade add invSlot count itemId | remove idx | gold n | lock | confirm | chat text | cancel
 */
final class TradeScreen extends Screen {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String P = "PIPI_TRADE_";
    private static final int W = 760, H = 380, HW = 250, SLOTS = 9;
    private static final int PX = 254, IX = 508, CELL = 32, PITCH = 34, GX = 112, GY = 40, ICELL = 26;
    private static final int TEXT = 0xffe6eef6, MUTED = 0xff9fb4ca, GOLD = 0xffe6c588, RED = 0xffff6b6b, GREEN = 0xff82d99a;

    private static final class State {
        boolean open, myLock, pLock, myConf, pConf;
        String me = "", partner = "";
        long myGold, pGold, bal;
        final ItemStack[] mine = new ItemStack[SLOTS], theirs = new ItemStack[SLOTS];
        State() { java.util.Arrays.fill(mine, ItemStack.EMPTY); java.util.Arrays.fill(theirs, ItemStack.EMPTY); }
    }

    private record Line(String text, int color) {}

    private static State cur = new State(), pend;
    private static boolean active, decodeWarned;
    private static final List<Line> chat = new ArrayList<>();

    private float scale, left, top;
    private boolean goldFocus, chatFocus;
    private String goldText = "0", chatText = "";
    private int dragSlot = -1, dragButton;
    private long lastSend;

    TradeScreen() {
        super(Component.literal("거래"));
        goldText = Long.toString(cur.myGold);
    }

    // ---- network ----
    static void receive(ClientChatReceivedEvent.System event) {
        String text = event.getMessage().getString();
        if (!text.startsWith(P)) return;
        event.setCanceled(true);
        process(text);
    }

    static void process(String text) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (text.startsWith(P + "BEGIN:")) {
                String[] p = text.split(":");
                State s = new State();
                s.open = p[1].equals("1");
                s.me = p[2]; s.partner = p[3];
                s.myGold = Long.parseLong(p[4]); s.pGold = Long.parseLong(p[5]);
                s.myLock = p[6].equals("1"); s.pLock = p[7].equals("1");
                s.myConf = p[8].equals("1"); s.pConf = p[9].equals("1");
                s.bal = Long.parseLong(p[10]);
                pend = s;
            } else if (text.startsWith(P + "SLOT:")) {
                String[] p = text.split(":", 4);
                if (pend == null || p.length < 4) return;
                int side = Integer.parseInt(p[1]), idx = Integer.parseInt(p[2]);
                if (idx < 0 || idx >= SLOTS) return;
                ItemStack stack = ItemWire.decode(p[3]);
                if (stack.isEmpty() && !decodeWarned) { decodeWarned = true; LOG.warn("[PipiTravel] trade item decoded to an empty stack"); }
                (side == 0 ? pend.mine : pend.theirs)[idx] = stack;
            } else if (text.equals(P + "END")) {
                if (pend == null) return;
                State s = pend;
                pend = null;
                cur = s;
                if (mc.screen instanceof TradeScreen t) t.sync();
                else if (s.open && mc.level != null) {
                    chat.clear();
                    active = true;
                    mc.setScreen(new TradeScreen());
                }
            } else if (text.startsWith(P + "CHAT:")) {
                String[] p = text.split(":", 3);
                if (p.length < 3) return;
                addLine(p[1] + ": " + p[2], p[1].equals(cur.me) ? GOLD : TEXT);
            } else if (text.startsWith(P + "MSG:")) {
                addLine("[안내] " + text.substring(P.length() + 4), RED);
            } else if (text.startsWith(P + "CLOSE:")) {
                String[] p = text.split(":", 3);
                active = false;
                if (p.length >= 3 && !p[2].isEmpty()) addLine("[안내] " + p[2], p[1].equals("1") ? GREEN : RED);
                if (mc.screen instanceof TradeScreen) mc.setScreen(null);
            }
        } catch (RuntimeException ex) {
            if (!decodeWarned) { decodeWarned = true; LOG.warn("[PipiTravel] trade message failed: {}", ex.toString()); }
        }
    }

    private static void addLine(String text, int color) {
        chat.add(new Line(text, color));
        while (chat.size() > 60) chat.remove(0);
    }

    private void sync() {
        if (!goldFocus) goldText = Long.toString(cur.myGold);
    }

    private void send(String cmd) {
        if (minecraft == null || minecraft.getConnection() == null) return;
        minecraft.getConnection().sendCommand("pipilife trade " + cmd);
    }

    private void sendThrottled(String cmd) {
        long now = System.currentTimeMillis();
        if (now - lastSend < 120) return;
        lastSend = now;
        send(cmd);
    }

    private void applyGold() {
        long v = 0;
        try { if (!goldText.isEmpty()) v = Long.parseLong(goldText); } catch (NumberFormatException ignored) { }
        goldText = Long.toString(v);
        if (v != cur.myGold) send("gold " + v);
    }

    // ---- layout ----
    @Override
    protected void init() {
        scale = Math.max(.01f, Math.min((width - 18f) / W, (height - 18f) / H));
        left = (width - W * scale) / 2;
        top = (height - H * scale) / 2;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void removed() {
        if (active) { active = false; send("cancel"); }
    }

    private static String name(ItemStack s) { return ChatFormatting.stripFormatting(s.getHoverName().getString()); }

    // ---- drawing (same helpers/colours as SmeltScreen) ----
    private static void panel(GuiGraphics g, int x, int y, int w, int h, boolean selected) {
        g.fill(x, y, x + w, y + h, selected ? 0xffa78c59 : 0xff3d5369);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, 0xff1a2a3a, 0xff0c1721);
    }

    private void heading(GuiGraphics g, int x, int w, String label) {
        g.fillGradient(x, 5, x + w, 28, 0xff2d4054, 0xff1c2a3b);
        g.drawString(font, label, x + 6, 13, TEXT, false);
    }

    private void button(GuiGraphics g, int x, int y, int w, int h, String label, boolean enabled, double mx, double my) {
        panel(g, x, y, w, h, enabled && hit(mx, my, x, y, w, h));
        g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, enabled ? TEXT : 0xff5d6d7e);
    }

    private static boolean hit(double x, double y, int bx, int by, int w, int h) { return x >= bx && y >= by && x < bx + w && y < by + h; }

    private static String money(long v) { return "%,d".formatted(v); }
    private static String grouped(String digits) { try { return money(Long.parseLong(digits)); } catch (NumberFormatException e) { return digits; } }

    private void badges(GuiGraphics g, int x, boolean lock, boolean conf) {
        String t = conf ? "교환 동의" : lock ? "등록 완료" : "등록 대기";
        int c = conf ? GOLD : lock ? GREEN : MUTED;
        panel(g, x + 8, 186, HW - 16, 20, false);
        g.drawCenteredString(font, t, x + HW / 2, 192, c);
    }

    private void half(GuiGraphics g, int x, String owner, ItemStack[] items, double mx, double my, boolean mineSide, ItemStack[] hover) {
        panel(g, x, 0, HW, 210, false);
        heading(g, x + 4, HW - 8, mineSide ? "내 제안 · " + owner : "상대 제안 · " + owner);
        panel(g, x + 8, 36, 98, 118, false);
        for (int i = 0; i < SLOTS; i++) {
            int cx = x + GX + (i % 3) * PITCH, cy = GY + (i / 3) * PITCH;
            boolean over = hit(mx, my, cx, cy, CELL, CELL);
            panel(g, cx, cy, CELL, CELL, over && !items[i].isEmpty());
            if (items[i].isEmpty()) continue;
            g.renderItem(items[i], cx + 8, cy + 8);
            g.renderItemDecorations(font, items[i], cx + 8, cy + 8);
            if (over) hover[0] = items[i];
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        double x = (mx - left) / scale, y = (my - top) / scale;
        State s = cur;
        g.fill(0, 0, width, height, 0xb9081019);
        ItemStack[] hover = {null};
        g.pose().pushPose();
        try {
            g.pose().translate(left, top, 0);
            g.pose().scale(scale, scale, 1);
            half(g, 0, s.me, s.mine, x, y, true, hover);
            half(g, PX, s.partner, s.theirs, x, y, false, hover);

            // gold rows
            panel(g, 8, 160, 160, 22, goldFocus);
            g.drawString(font, "골드", 14, 167, MUTED, false);
            String shown = s.myLock ? money(s.myGold) : grouped(goldText);
            String caret = goldFocus && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "";
            g.drawString(font, shown + caret, 160 - font.width(shown + caret) + 0, 167, GOLD, false);
            button(g, 172, 160, 70, 22, "적용", !s.myLock, x, y);
            panel(g, PX + 8, 160, HW - 16, 22, false);
            g.drawString(font, "골드", PX + 14, 167, MUTED, false);
            String pg = money(s.pGold);
            g.drawString(font, pg, PX + HW - 14 - font.width(pg), 167, GOLD, false);
            badges(g, 0, s.myLock, s.myConf);
            badges(g, PX, s.pLock, s.pConf);

            // chat
            panel(g, 0, 214, 504, 104, false);
            int first = Math.max(0, chat.size() - 8);
            for (int i = first; i < chat.size(); i++) {
                Line l = chat.get(i);
                g.drawString(font, font.plainSubstrByWidth(l.text, 488), 8, 220 + (i - first) * 12, l.color, false);
            }
            if (chat.isEmpty()) g.drawString(font, "상대와 대화할 수 있습니다.", 8, 220, MUTED, false);
            panel(g, 0, 322, 440, 22, chatFocus);
            String ct = chatText + (chatFocus && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
            if (chatText.isEmpty() && !chatFocus) g.drawString(font, "채팅 입력 후 Enter", 8, 329, 0xff5d6d7e, false);
            else g.drawString(font, font.plainSubstrByWidth(ct, 426, true), 8, 329, TEXT, false);
            button(g, 444, 322, 60, 22, "전송", true, x, y);

            // bottom buttons + status
            boolean canConfirm = s.myLock && s.pLock && !s.myConf;
            button(g, 0, 350, 110, 26, s.myLock ? "등록 취소" : "등록하기", true, x, y);
            button(g, 116, 350, 110, 26, s.myConf ? "상대 대기중" : "교환하기", canConfirm, x, y);
            String status = s.myConf && !s.pConf ? "상대가 교환하기를 누르길 기다리는 중입니다"
                : s.myLock && s.pLock ? "교환하기를 누르세요" : "양쪽 모두 교환하기를 누르면 거래가 완료됩니다";
            g.drawString(font, status, 236, 359, MUTED, false);

            // inventory
            panel(g, IX, 0, 252, H, false);
            heading(g, IX + 4, 244, "내 가방");
            g.fill(IX + 225, 7, IX + 246, 26, hit(x, y, IX + 225, 7, 21, 19) ? 0xffa78c59 : 0xff3d5369);
            g.fillGradient(IX + 226, 8, IX + 245, 25, hit(x, y, IX + 225, 7, 21, 19) ? 0xff3a4d63 : 0xff1a2a3a, 0xff0c1721);
            g.drawCenteredString(font, "X", IX + 235, 13, TEXT);
            var mc = Minecraft.getInstance();
            for (int slot = 0; slot < 36; slot++) {
                int cx = invX(slot), cy = invY(slot);
                ItemStack st = mc.player == null ? ItemStack.EMPTY : mc.player.getInventory().getItem(slot);
                boolean over = hit(x, y, cx, cy, ICELL, ICELL);
                panel(g, cx, cy, ICELL, ICELL, over && !st.isEmpty() && !s.myLock);
                if (st.isEmpty()) continue;
                g.renderItem(st, cx + 5, cy + 5);
                g.renderItemDecorations(font, st, cx + 5, cy + 5);
                if (over) hover[0] = st;
            }
            panel(g, IX + 8, 164, 236, 176, false);
            g.drawString(font, "가방에서 아이템을 클릭하거나", IX + 16, 174, MUTED, false);
            g.drawString(font, "거래창으로 끌어다 놓으세요.", IX + 16, 186, MUTED, false);
            g.drawString(font, "우클릭: 1개만 올리기", IX + 16, 202, MUTED, false);
            g.drawString(font, "올린 아이템은 클릭하면 돌려받습니다.", IX + 16, 218, MUTED, false);
            g.drawString(font, "등록하면 수정할 수 없고,", IX + 16, 240, MUTED, false);
            g.drawString(font, "양쪽이 등록 후 교환하기를 눌러야", IX + 16, 252, MUTED, false);
            g.drawString(font, "아이템이 이동합니다.", IX + 16, 264, MUTED, false);
            g.drawString(font, "보유 골드", IX + 16, 300, MUTED, false);
            String bal = money(s.bal);
            g.drawString(font, bal, IX + 236 - font.width(bal), 300, GOLD, false);
            g.drawString(font, "거리 10블록 이상 멀어지면 취소됩니다", IX + 16, 320, MUTED, false);
        } finally { g.pose().popPose(); }

        // player models (entity renderer scissors in screen coordinates, so drawn outside the scaled pose)
        var mc = Minecraft.getInstance();
        LivingEntity mineE = mc.player, theirE = null;
        if (mc.level != null) for (var other : mc.level.players()) if (other.getGameProfile().getName().equals(s.partner)) { theirE = other; break; }
        model(g, 8, mineE, mx, my);
        model(g, PX + 8, theirE, mx, my);

        if (dragSlot >= 0 && mc.player != null) {
            ItemStack d = mc.player.getInventory().getItem(dragSlot);
            if (!d.isEmpty()) g.renderItem(d, mx - 8, my - 8);
        } else if (hover[0] != null) {
            if (GearCard.isCard(hover[0])) GearCard.draw(g, font, hover[0], mx, my, width, height);
            else g.renderTooltip(font, hover[0], mx, my);
        }
    }

    private void model(GuiGraphics g, int lx, LivingEntity e, int mx, int my) {
        if (e == null) return;
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, (int) (left + (lx + 1) * scale), (int) (top + 37 * scale),
            (int) (left + (lx + 97) * scale), (int) (top + 153 * scale), Math.max(8, (int) (42 * scale)), .0625f, mx, my, e);
    }

    private static int invX(int slot) { return IX + 9 + (slot % 9) * ICELL; }

    private static int invY(int slot) { return slot < 9 ? 38 + 3 * ICELL + 6 : 38 + (slot / 9 - 1) * ICELL; }

    // ---- input ----
    private int invHit(double x, double y) {
        for (int slot = 0; slot < 36; slot++) if (hit(x, y, invX(slot), invY(slot), ICELL, ICELL)) return slot;
        return -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        double x = (mx - left) / scale, y = (my - top) / scale;
        State s = cur;
        if (goldFocus && !hit(x, y, 8, 160, 160, 22)) { goldFocus = false; if (!s.myLock) applyGold(); }
        if (button != 0 && button != 1) return true;
        if (hit(x, y, IX + 225, 7, 21, 19)) { onClose(); return true; }
        if (button == 0) {
            if (hit(x, y, 8, 160, 160, 22) && !s.myLock) { goldFocus = true; chatFocus = false; return true; }
            if (hit(x, y, 172, 160, 70, 22) && !s.myLock) { goldFocus = false; applyGold(); return true; }
            chatFocus = hit(x, y, 0, 322, 440, 22);
            if (chatFocus) return true;
            if (hit(x, y, 444, 322, 60, 22)) { sendChat(); return true; }
            if (hit(x, y, 0, 350, 110, 26)) { send("lock"); return true; }
            if (hit(x, y, 116, 350, 110, 26)) { if (s.myLock && s.pLock && !s.myConf) send("confirm"); return true; }
            for (int i = 0; i < SLOTS; i++)
                if (hit(x, y, GX + (i % 3) * PITCH, GY + (i / 3) * PITCH, CELL, CELL) && !s.mine[i].isEmpty()) { sendThrottled("remove " + i); return true; }
        }
        int slot = invHit(x, y);
        if (slot >= 0 && minecraft != null && minecraft.player != null && !minecraft.player.getInventory().getItem(slot).isEmpty()) {
            dragSlot = slot;
            dragButton = button;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragSlot >= 0 && button == dragButton) {
            double x = (mx - left) / scale, y = (my - top) / scale;
            int slot = dragSlot;
            dragSlot = -1;
            boolean dropped = hit(x, y, 0, 30, HW, 180) || invHit(x, y) == slot;
            if (dropped && !cur.myLock && minecraft != null && minecraft.player != null) {
                ItemStack st = minecraft.player.getInventory().getItem(slot);
                if (!st.isEmpty()) {
                    String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
                    sendThrottled("add " + slot + " " + (button == 1 ? 1 : 2304) + " " + id);
                }
            }
        }
        return true;
    }

    private void sendChat() {
        String t = chatText.trim();
        if (t.isEmpty()) return;
        send("chat " + t);
        chatText = "";
    }

    @Override
    public boolean charTyped(char c, int mod) {
        if (goldFocus) {
            if (c >= '0' && c <= '9' && goldText.length() < 9) goldText = goldText.equals("0") ? String.valueOf(c) : goldText + c;
            return true;
        }
        if (!goldFocus) chatFocus = true;
        if (chatFocus && c >= 32 && c != 127 && c != '§' && chatText.length() < 60) { chatText += c; return true; }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (goldFocus) {
            if (key == GLFW.GLFW_KEY_BACKSPACE && !goldText.isEmpty()) { goldText = goldText.substring(0, goldText.length() - 1); if (goldText.isEmpty()) goldText = "0"; }
            else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { goldFocus = false; applyGold(); }
            return true;
        }
        if (chatFocus) {
            if (key == GLFW.GLFW_KEY_BACKSPACE && !chatText.isEmpty()) chatText = chatText.substring(0, chatText.length() - 1);
            else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) sendChat();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}
