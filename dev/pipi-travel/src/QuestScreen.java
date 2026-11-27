package dev.pipi.travel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

/**
 * 퀘스트 창 (U): tabs 메인 · 직업 · 생활 · 일일, the quests of the tab on the left (main grouped by chapter) and the selected one
 * on the right: who gives it, the story text, objectives with progress, rewards, 추적 / 보상 받기. Same look as the other
 * PipiTravel windows. Data: PIPI_QLOG:&lt;json&gt; from PipiLife QuestSystem (asked with /pipilife quest log when opened and
 * every 2 s while open); actions: /pipilife quest track|claim &lt;id&gt;.
 */
final class QuestScreen extends Screen {
    static final KeyMapping KEY = new KeyMapping("퀘스트 창", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_U, "PipiTravel");
    private static final String PREFIX = "PIPI_QLOG:";
    private static final int WIDTH = 600, HEIGHT = 340;
    private static final int TEXT = 0xFFE7EEF6, MUTED = 0xFF9AAEC3, BLUE = 0xFF87BEE8, GOLD = 0xFFE5BB7C, DIM = 0xFF54677A, GREEN = 0xFF8FE3A0;
    private static final int LIST_X = 18, LIST_Y = 70, LIST_W = 286, LIST_H = 236, SIDE_X = 314, SIDE_W = 268;
    private static final String[] TABS = {"메인", "직업", "생활", "일일"}, KEYS = {"main", "class", "life", "daily"};
    private static final int ROW_H = 18, HEAD_H = 16;

    private record Obj(String text, int cur, int need) {}
    private record Q(String id, String chapter, String title, String giver, String desc, String state, List<Obj> objs, List<String> rewards) {}
    private static final Map<String, List<Q>> DATA = new LinkedHashMap<>();
    private static String track = "";
    private static int tab = 0;
    private static String selectedId = "";
    private float scale, originX, originY;
    private double scroll;
    private int ticks;

    QuestScreen() { super(Component.literal("퀘스트")); }

    static void key(ClientTickEvent.Post event) {
        var client = Minecraft.getInstance();
        if (client.player == null || client.screen != null) { while (KEY.consumeClick()) { } return; }
        while (KEY.consumeClick()) client.setScreen(new QuestScreen());
    }

    static void receive(ClientChatReceivedEvent.System event) {
        String text = event.getMessage().getString();
        if (!text.startsWith(PREFIX)) return;
        event.setCanceled(true);
        try {
            JsonObject root = JsonParser.parseString(text.substring(PREFIX.length())).getAsJsonObject();
            track = root.has("track") ? root.get("track").getAsString() : "";
            JsonObject tabs = root.getAsJsonObject("tabs");
            DATA.clear();
            for (String k : KEYS) {
                List<Q> list = new ArrayList<>();
                if (tabs != null && tabs.has(k)) for (JsonElement e : tabs.getAsJsonArray(k)) {
                    JsonObject o = e.getAsJsonObject();
                    List<Obj> objs = new ArrayList<>();
                    for (JsonElement oe : o.getAsJsonArray("objs")) { JsonObject j = oe.getAsJsonObject(); objs.add(new Obj(j.get("t").getAsString(), j.get("c").getAsInt(), j.get("n").getAsInt())); }
                    List<String> rew = new ArrayList<>();
                    for (JsonElement r : o.getAsJsonArray("rew")) rew.add(r.getAsString());
                    list.add(new Q(o.get("id").getAsString(), o.get("chapter").getAsString(), o.get("title").getAsString(), o.get("giver").getAsString(),
                        o.get("desc").getAsString(), o.get("state").getAsString(), objs, rew));
                }
                DATA.put(k, list);
            }
        } catch (RuntimeException ignored) { }
    }

    private static void send(String cmd) { var c = Minecraft.getInstance().getConnection(); if (c != null) c.sendCommand(cmd); }

    @Override protected void init() {
        scale = Math.max(.01f, Math.min((width - 12f) / WIDTH, (height - 12f) / HEIGHT));
        originX = (width - WIDTH * scale) / 2;
        originY = (height - HEIGHT * scale) / 2;
        send("pipilife quest log");
        if (selectedId.isEmpty()) selectedId = track;
    }

    @Override public void tick() { if (++ticks % 40 == 0) send("pipilife quest log"); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) { }

    /** Rows of the list: a chapter header (q == null) or a quest. */
    private record Row(String header, Q q) {}

    private List<Row> rows() {
        List<Row> out = new ArrayList<>();
        String last = null;
        for (Q q : DATA.getOrDefault(KEYS[tab], List.of())) {
            if (tab == 0 && !q.chapter.isEmpty() && !q.chapter.equals(last)) { out.add(new Row(q.chapter, null)); last = q.chapter; }
            out.add(new Row(null, q));
        }
        return out;
    }

    private Q selected(List<Row> rows) {
        Q first = null, active = null;
        for (Row r : rows) if (r.q != null) {
            if (r.q.id.equals(selectedId)) return r.q;
            if (first == null) first = r.q;
            if (active == null && (r.q.state.equals("active") || r.q.state.equals("ready"))) active = r.q;
        }
        return active != null ? active : first;
    }

    private int listHeight(List<Row> rows) { int h = 6; for (Row r : rows) h += r.q == null ? HEAD_H + 4 : ROW_H + 2; return h; }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        double x = (mouseX - originX) / scale, y = (mouseY - originY) / scale;
        g.fill(0, 0, width, height, 0xBD070D16);
        g.pose().pushPose();
        g.pose().translate(originX, originY, 0);
        g.pose().scale(scale, scale, 1);
        panel(g, 0, 0, WIDTH, HEIGHT, 0xFF101B27, 0xFF3B5770);
        g.fillGradient(1, 1, WIDTH - 1, 57, 0xFF273E53, 0xFF162534);
        g.fill(20, 15, 23, 44, BLUE);
        g.drawString(font, "삐삐서버   /   모험", 32, 14, BLUE, false);
        g.drawString(font, "퀘스트", 32, 31, TEXT, false);
        for (int i = 0; i < TABS.length; i++) {
            int tx = 330 + i * 54;
            boolean ready = DATA.getOrDefault(KEYS[i], List.of()).stream().anyMatch(q -> q.state.equals("ready"));
            panel(g, tx, 22, 50, 20, i == tab ? 0xFF243D53 : inside(x, y, tx, 22, 50, 20) ? 0xFF1B2E40 : 0xFF132130, i == tab ? BLUE : 0xFF364E63);
            g.drawCenteredString(font, TABS[i] + (ready ? " §a●" : ""), tx + 25, 28, i == tab ? TEXT : MUTED);
        }
        button(g, WIDTH - 41, 15, 23, 24, "×", inside(x, y, WIDTH - 41, 15, 23, 24), BLUE);

        panel(g, LIST_X, LIST_Y, LIST_W, LIST_H, 0xFF0A131D, 0xFF283E51);
        panel(g, SIDE_X, LIST_Y, SIDE_W, LIST_H, 0xFF142333, 0xFF3C5871);
        List<Row> rows = rows();
        Q sel = selected(rows);
        if (rows.isEmpty()) {
            g.drawCenteredString(font, DATA.isEmpty() ? "불러오는 중…" : tab == 3 ? "오늘의 퀘스트가 없습니다" : "이 탭의 퀘스트가 없습니다", LIST_X + LIST_W / 2, LIST_Y + LIST_H / 2 - 4, MUTED);
        } else {
            scroll = Math.clamp(scroll, 0, Math.max(0, listHeight(rows) - LIST_H));
            g.enableScissor((int) (originX + (LIST_X + 1) * scale), (int) (originY + (LIST_Y + 1) * scale), (int) (originX + (LIST_X + LIST_W - 1) * scale), (int) (originY + (LIST_Y + LIST_H - 1) * scale));
            int ry = LIST_Y + 6 - (int) scroll;
            for (Row r : rows) {
                if (r.q == null) { g.drawString(font, r.header, LIST_X + 10, ry + 4, GOLD, false); ry += HEAD_H + 4; continue; }
                Q q = r.q;
                boolean isSel = q == sel, hover = inside(x, y, LIST_X + 4, ry, LIST_W - 8, ROW_H) && inside(x, y, LIST_X, LIST_Y, LIST_W, LIST_H);
                if (isSel) { g.fill(LIST_X + 4, ry, LIST_X + LIST_W - 4, ry + ROW_H, 0xFF29465F); g.fill(LIST_X + 4, ry, LIST_X + 6, ry + ROW_H, BLUE); }
                else if (hover) g.fill(LIST_X + 4, ry, LIST_X + LIST_W - 4, ry + ROW_H, 0xFF162636);
                String mark; int col; String right;
                switch (q.state) {
                    case "done" -> { mark = "✔"; col = MUTED; right = "§7완료"; }
                    case "ready" -> { mark = "★"; col = GREEN; right = "§a보상"; }
                    case "active" -> { mark = "●"; col = TEXT; right = q.id.equals(track) ? "§b추적 중" : "§e진행 중"; }
                    default -> { mark = "▪"; col = DIM; right = "§8잠김"; }
                }
                g.drawString(font, mark + " " + font.plainSubstrByWidth(q.title, LIST_W - 90), LIST_X + 14, ry + 5, col, false);
                g.drawString(font, right, LIST_X + LIST_W - 12 - font.width(right.substring(2)), ry + 5, MUTED, false);
                ry += ROW_H + 2;
            }
            g.disableScissor();
        }
        if (sel != null) drawDetail(g, sel, x, y);
        g.drawString(font, "추적 중인 퀘스트는 화면 왼쪽에 표시  ·  보상은 촌장에게서도 받을 수 있어요", 20, HEIGHT - 22, MUTED, false);
        g.drawString(font, "U · ESC 닫기", WIDTH - 20 - font.width("U · ESC 닫기"), HEIGHT - 22, MUTED, false);
        g.pose().popPose();
    }

    private void drawDetail(GuiGraphics g, Q q, double mx, double my) {
        int x = SIDE_X + 10, y = LIST_Y + 9, w = SIDE_W - 20;
        String head = !q.chapter.isEmpty() ? q.chapter : TABS[tab] + " 퀘스트";
        g.drawString(font, head, x, y, GOLD, false);
        g.drawString(font, font.plainSubstrByWidth(q.title, w), x, y + 13, TEXT, false);
        y += 30;
        if (q.state.equals("locked")) {
            g.drawString(font, "앞의 퀘스트를 마치면 열립니다.", x, y, DIM, false);
            return;
        }
        g.drawString(font, "— " + q.giver, x, y, BLUE, false); y += 12;
        for (var line : font.split(Component.literal(q.desc), w)) { g.drawString(font, line, x, y, MUTED, false); y += 10; }
        y += 6;
        g.drawString(font, "목표", x, y, GOLD, false); y += 12;
        for (Obj o : q.objs) {
            boolean ok = o.cur >= o.need;
            g.drawString(font, (ok ? "§a✔ §7" : "§f□ ") + font.plainSubstrByWidth(o.text, w - 60), x, y, TEXT, false);
            if (o.need > 1) {
                String n = Math.min(o.cur, o.need) + " / " + o.need;
                g.drawString(font, n, x + w - font.width(n), y, ok ? GREEN : BLUE, false);
                int bw = w, by = y + 10;
                g.fill(x, by, x + bw, by + 3, 0xFF0A1520);
                g.fill(x, by, x + (int) Math.round(bw * Math.min(1, o.cur / (double) o.need)), by + 3, ok ? 0xFF5FBF74 : BLUE);
                y += 16;
            } else y += 12;
        }
        y += 4;
        if (!q.rewards.isEmpty()) {
            g.drawString(font, "보상", x, y, GOLD, false); y += 12;
            int rx = x;
            for (String r : q.rewards) {
                int tw = font.width(r) + 10;
                if (rx + tw > x + w) { rx = x; y += 18; }
                panel(g, rx, y, tw, 15, 0xFF0C1723, 0xFF4B6C87);
                g.drawString(font, r, rx + 5, y + 4, TEXT, false);
                rx += tw + 4;
            }
        }
        int by = LIST_Y + LIST_H - 30, bw = (w - 6) / 2;
        boolean canTrack = q.state.equals("active") || q.state.equals("ready");
        if (canTrack) button(g, x, by, bw, 20, q.id.equals(track) ? "추적 중" : "추적하기", inside(mx, my, x, by, bw, 20), q.id.equals(track) ? 0xFF42566B : BLUE);
        if (q.state.equals("ready")) button(g, x + bw + 6, by, bw, 20, "§a보상 받기", inside(mx, my, x + bw + 6, by, bw, 20), GREEN);
        else if (q.state.equals("done")) g.drawCenteredString(font, "완료한 퀘스트", x + w / 2, by + 6, MUTED);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scroll -= vertical * 24;
        return true;
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int buttonNo) {
        double x = (mouseX - originX) / scale, y = (mouseY - originY) / scale;
        if (inside(x, y, WIDTH - 41, 15, 23, 24)) { onClose(); return true; }
        for (int i = 0; i < TABS.length; i++) if (inside(x, y, 330 + i * 54, 22, 50, 20)) { tab = i; selectedId = ""; scroll = 0; click(); return true; }
        List<Row> rows = rows();
        if (inside(x, y, LIST_X, LIST_Y, LIST_W, LIST_H)) {
            int ry = LIST_Y + 6 - (int) scroll;
            for (Row r : rows) {
                if (r.q == null) { ry += HEAD_H + 4; continue; }
                if (inside(x, y, LIST_X + 4, ry, LIST_W - 8, ROW_H)) { selectedId = r.q.id; click(); return true; }
                ry += ROW_H + 2;
            }
        }
        Q sel = selected(rows);
        if (sel != null) {
            int bx = SIDE_X + 10, w = SIDE_W - 20, by = LIST_Y + LIST_H - 30, bw = (w - 6) / 2;
            boolean canTrack = sel.state.equals("active") || sel.state.equals("ready");
            if (canTrack && inside(x, y, bx, by, bw, 20) && !sel.id.equals(track)) { send("pipilife quest track " + sel.id); track = sel.id; click(); return true; }
            if (sel.state.equals("ready") && inside(x, y, bx + bw + 6, by, bw, 20)) { send("pipilife quest claim " + sel.id); click(); return true; }
        }
        return super.mouseClicked(mouseX, mouseY, buttonNo);
    }

    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE || KEY.matches(key, scan)) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
    }

    private void button(GuiGraphics g, int x, int y, int w, int h, String label, boolean hover, int border) {
        panel(g, x, y, w, h, hover ? 0xFF365D7B : 0xFF243D53, border);
        g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, TEXT);
    }

    private static void panel(GuiGraphics g, int x, int y, int w, int h, int fill, int border) {
        g.fill(x, y, x + w, y + h, border);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    private static boolean inside(double x, double y, int left, int top, int w, int h) { return x >= left && x < left + w && y >= top && y < top + h; }
}
