package dev.pipi.travel;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * 파티 창 (P): the party members as cards (class, leader star, HP, online, where they are and how far), leader tools
 * (리더 위임 / 강퇴), 파티 나가기 / 파티 만들기, and a toggle for the small party list on the left of the HUD.
 * Same look as the other PipiTravel windows (ResearchScreen colours). Data: PartyHud.members (PIPI_PARTY from PipiLife).
 */
final class PartyScreen extends Screen {
    private static final int WIDTH = 540, HEIGHT = 314;
    private static final int TEXT = 0xFFE7EEF6, MUTED = 0xFF9AAEC3, BLUE = 0xFF87BEE8, GOLD = 0xFFE5BB7C, RED = 0xFFE57373, DIM = 0xFF54677A;
    private static final int CARD_X = 18, CARD_Y = 70, CARD_W = 340, CARD_H = 50, CARD_GAP = 6, SIDE_X = 372, SIDE_W = 150;
    private float scale, originX, originY;

    PartyScreen() { super(Component.literal("파티")); }

    @Override protected void init() {
        scale = Math.max(.01f, Math.min((width - 12f) / WIDTH, (height - 12f) / HEIGHT));
        originX = (width - WIDTH * scale) / 2;
        originY = (height - HEIGHT * scale) / 2;
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) { }

    private String me() { var p = Minecraft.getInstance().player; return p == null ? "" : p.getGameProfile().getName(); }
    private boolean iLead(List<PartyHud.Member> ms) { for (PartyHud.Member m : ms) if (m.leader() && m.name().equals(me())) return true; return false; }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        double x = (mouseX - originX) / scale, y = (mouseY - originY) / scale;
        List<PartyHud.Member> ms = PartyHud.members;
        boolean lead = iLead(ms);
        g.fill(0, 0, width, height, 0xBD070D16);
        g.pose().pushPose();
        g.pose().translate(originX, originY, 0);
        g.pose().scale(scale, scale, 1);
        panel(g, 0, 0, WIDTH, HEIGHT, 0xFF101B27, 0xFF3B5770);
        g.fillGradient(1, 1, WIDTH - 1, 57, 0xFF273E53, 0xFF162534);
        g.fill(20, 15, 23, 44, BLUE);
        g.drawString(font, "삐삐서버   /   모험", 32, 14, BLUE, false);
        g.drawString(font, "파티" + (ms.isEmpty() ? "" : "   §7" + ms.size() + " / 4"), 32, 31, TEXT, false);
        button(g, WIDTH - 41, 15, 23, 24, "×", inside(x, y, WIDTH - 41, 15, 23, 24), true);

        // member cards
        if (ms.isEmpty()) {
            panel(g, CARD_X, CARD_Y, CARD_W, 4 * CARD_H + 3 * CARD_GAP, 0xFF0A131D, 0xFF283E51);
            g.drawCenteredString(font, "파티에 속해 있지 않습니다", CARD_X + CARD_W / 2, CARD_Y + 70, TEXT);
            g.drawCenteredString(font, "다른 플레이어를 우클릭해 파티에 초대하거나", CARD_X + CARD_W / 2, CARD_Y + 88, MUTED);
            g.drawCenteredString(font, "아래 버튼으로 혼자 파티를 만들 수 있습니다", CARD_X + CARD_W / 2, CARD_Y + 100, MUTED);
            button(g, CARD_X + CARD_W / 2 - 60, CARD_Y + 124, 120, 22, "파티 만들기", inside(x, y, CARD_X + CARD_W / 2 - 60, CARD_Y + 124, 120, 22), true);
        } else for (int k = 0; k < 4; k++) {
            int cy = CARD_Y + k * (CARD_H + CARD_GAP);
            if (k >= ms.size()) {
                panel(g, CARD_X, cy, CARD_W, CARD_H, 0xFF0C1622, 0xFF203242);
                g.drawCenteredString(font, "빈 자리  ·  플레이어를 우클릭해 초대", CARD_X + CARD_W / 2, cy + CARD_H / 2 - 4, DIM);
                continue;
            }
            PartyHud.Member m = ms.get(k);
            boolean self = m.name().equals(me());
            panel(g, CARD_X, cy, CARD_W, CARD_H, 0xFF132130, m.leader() ? GOLD : self ? BLUE : 0xFF364E63);
            int ix = CARD_X + 26, iy = cy + CARD_H / 2;
            ClassIcons.diamond(g, ix, iy, 17, m.leader() ? 0xffe8c579 : 0xffb8bcc8, 0xff141a26);
            ClassIcons.diamond(g, ix, iy, 14, 0x00000000, 0xff1e2636);
            if (m.cls() >= 0) ClassIcons.draw(g, m.cls(), ix - 10f, iy - 10f, 20f, m.online() ? 1f : .45f);
            int nx = CARD_X + 52;
            g.drawString(font, (m.leader() ? "§e★ §r" : "") + m.name() + (self ? " §7(나)" : ""), nx, cy + 8, m.online() ? TEXT : MUTED, false);
            String where = !m.online() ? "§8오프라인" : (m.area().isEmpty() || m.area().equals("-") ? "" : "§b" + m.area()) + (m.dist() >= 0 && !self ? "§7 · " + m.dist() + "칸" : m.same() || self ? "" : "§7 · 다른 지역");
            g.drawString(font, where, nx + 150, cy + 8, MUTED, false);
            int bw = 180, by = cy + 26;
            g.fill(nx - 1, by - 1, nx + bw + 1, by + 9, 0xFF000000);
            g.fill(nx, by, nx + bw, by + 8, 0xFF0A1520);
            if (m.online()) {
                int fw = (int) Math.round(bw * Math.clamp(m.hp() / (double) m.max(), 0, 1));
                g.fill(nx, by, nx + fw, by + 8, m.same() || self ? 0xFFD23A3A : 0xFF5A5F6A);
                g.fill(nx, by, nx + fw, by + 2, 0x40FFFFFF);
                g.drawString(font, m.hp() + " / " + m.max(), nx + bw + 6, by, MUTED, false);
            }
            if (lead && !self) {
                int bx = CARD_X + CARD_W - 62;
                small(g, bx, cy + 6, 56, 16, "리더 위임", inside(x, y, bx, cy + 6, 56, 16), BLUE);
                small(g, bx, cy + 27, 56, 16, "강퇴", inside(x, y, bx, cy + 27, 56, 16), RED);
            }
        }

        // side panel: how-to and actions
        panel(g, SIDE_X, CARD_Y, SIDE_W, 4 * CARD_H + 3 * CARD_GAP, 0xFF142333, 0xFF3C5871);
        int ty = CARD_Y + 10;
        g.drawString(font, "파티 안내", SIDE_X + 10, ty, GOLD, false); ty += 16;
        for (String line : new String[]{"초대: 플레이어 우클릭", "파티 채팅: §f/p §7메시지", "전체 채팅: §f/a §7메시지", "최대 4명", "같은 지역 멤버는", "체력바가 빨갛게 보입니다"}) {
            g.drawString(font, line, SIDE_X + 10, ty, MUTED, false); ty += 12;
        }
        int bx = SIDE_X + 10, bw2 = SIDE_W - 20;
        button(g, bx, CARD_Y + 140, bw2, 22, "파티 목록 " + (PartyHud.shown ? "숨기기" : "보이기"), inside(x, y, bx, CARD_Y + 140, bw2, 22), true);
        if (!ms.isEmpty()) button(g, bx, CARD_Y + 168, bw2, 22, "§c파티 나가기", inside(x, y, bx, CARD_Y + 168, bw2, 22), true);
        g.drawString(font, "왼쪽 화면의 작은 파티 목록", SIDE_X + 10, CARD_Y + 196, DIM, false);

        int footY = CARD_Y + 4 * CARD_H + 3 * CARD_GAP + 7;   // under the card / side panels (they end at 288)
        g.drawString(font, "카드 버튼으로 리더 위임 · 강퇴 (리더만)", CARD_X + 2, footY, MUTED, false);
        String keys = "P 열기   ·   ESC 닫기";
        g.drawString(font, keys, SIDE_X + SIDE_W - font.width(keys), footY, MUTED, false);
        g.pose().popPose();
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int buttonNo) {
        double x = (mouseX - originX) / scale, y = (mouseY - originY) / scale;
        var conn = Minecraft.getInstance().getConnection();
        if (inside(x, y, WIDTH - 41, 15, 23, 24)) { onClose(); return true; }
        List<PartyHud.Member> ms = PartyHud.members;
        if (ms.isEmpty()) {
            if (inside(x, y, CARD_X + CARD_W / 2 - 60, CARD_Y + 124, 120, 22) && conn != null) { conn.sendCommand("pipilife party create"); click(); return true; }
        } else if (iLead(ms)) for (int k = 0; k < ms.size(); k++) {
            PartyHud.Member m = ms.get(k);
            if (m.name().equals(me())) continue;
            int cy = CARD_Y + k * (CARD_H + CARD_GAP), bx = CARD_X + CARD_W - 62;
            if (inside(x, y, bx, cy + 6, 56, 16) && conn != null) { conn.sendCommand("pipilife party promote " + m.name()); click(); return true; }
            if (inside(x, y, bx, cy + 27, 56, 16) && conn != null) { conn.sendCommand("pipilife party kick " + m.name()); click(); return true; }
        }
        int bx = SIDE_X + 10, bw2 = SIDE_W - 20;
        if (inside(x, y, bx, CARD_Y + 140, bw2, 22)) { PartyHud.shown = !PartyHud.shown; click(); return true; }
        if (!ms.isEmpty() && inside(x, y, bx, CARD_Y + 168, bw2, 22) && conn != null) { conn.sendCommand("pipilife party leave"); click(); onClose(); return true; }
        return super.mouseClicked(mouseX, mouseY, buttonNo);
    }

    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE || PartyHud.TOGGLE_KEY.matches(key, scan)) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
    }

    private void button(GuiGraphics g, int x, int y, int w, int h, String label, boolean hover, boolean enabled) {
        panel(g, x, y, w, h, enabled && hover ? 0xFF365D7B : 0xFF243D53, enabled ? BLUE : 0xFF42566B);
        g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, enabled ? TEXT : MUTED);
    }

    private void small(GuiGraphics g, int x, int y, int w, int h, String label, boolean hover, int border) {
        panel(g, x, y, w, h, hover ? 0xFF365D7B : 0xFF1B2E40, border);
        g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, TEXT);
    }

    private static void panel(GuiGraphics g, int x, int y, int w, int h, int fill, int border) {
        g.fill(x, y, x + w, y + h, border);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    private static boolean inside(double x, double y, int left, int top, int w, int h) { return x >= left && x < left + w && y >= top && y < top + h; }
}
