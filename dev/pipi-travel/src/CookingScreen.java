package dev.pipi.travel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.lwjgl.glfw.GLFW;

/**
 * 조리대 화면. Server protocol (see Cooking.java): slots 0..26 = 요리 수첩 (lore line 0 "cookbook:a,b,c,seasoning"),
 * slot 52 = last result (lore line 0 "serial:N"). The mini-game runs client-side; the result is sent as
 * `pipilife cook <ing1> <ing2> <ing3> <seasoning> <0|1>`.
 */
final class CookingScreen extends ContainerScreen {
    static final String TITLE = "삐삐서버 · 조리대";
    private static final int W = 640, H = 340;
    private static final int TEXT = 0xffe8edf4, MUTED = 0xff9cacbf, GOLD = 0xffebc77f, GREEN = 0xff5fbf6a, RED = 0xff9a3434;
    private static final int RESULT_SLOT = 52, GX = 540, GY = 34, GW = 30, GH = 250;

    private static final String[] INGREDIENTS = {"cabbage", "carrot", "straw", "salmon", "potato"};
    private static final String[] SEASONINGS = {"salt", "sugar", "pepper", "soy_sauce"};
    private static final Map<String, String> LABELS = Map.ofEntries(
        Map.entry("cabbage", "양배추"), Map.entry("carrot", "당근"), Map.entry("straw", "짚"), Map.entry("salmon", "생 연어"),
        Map.entry("potato", "감자"), Map.entry("salt", "소금"), Map.entry("sugar", "설탕"), Map.entry("pepper", "후추"),
        Map.entry("soy_sauce", "간장"));
    private static final Map<String, String> ITEM_IDS = Map.ofEntries(
        Map.entry("cabbage", "pipifacilities:cabbage"), Map.entry("carrot", "pipifacilities:pet_carrot"),
        Map.entry("straw", "pipifacilities:straw"), Map.entry("salmon", "pipifacilities:raw_salmon"),
        Map.entry("potato", "minecraft:potato"), Map.entry("salt", "pipifacilities:salt"),
        Map.entry("sugar", "pipifacilities:sugar"), Map.entry("pepper", "pipifacilities:pepper"),
        Map.entry("soy_sauce", "pipifacilities:soy_sauce"));

    private record Entry(int slot, List<String> ingredients, String seasoning) {}

    private float scale, left, top;
    private final String[] boxes = new String[4];
    private int bookScroll;
    private String status = "";

    // mini-game
    private boolean playing, awaiting;
    private float pos, dir = 1, speed;
    private float zoneStart, zoneSize;
    private long lastNanos, awaitSince;
    private String awaitSerial = "";
    private boolean lastSuccess;
    private boolean showResult, gaugeVisible;

    CookingScreen(ChestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override protected void init() {
        scale = Math.max(.01f, Math.min((width - 16f) / W, (height - 16f) / H));
        left = (width - W * scale) / 2;
        top = (height - H * scale) / 2;
    }

    // ------------------------------------------------------------ data

    private ItemStack item(int slot) { return menu.getSlot(slot).getItem(); }

    private static List<String> lore(ItemStack stack) {
        List<String> out = new ArrayList<>();
        for (Component line : stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines())
            out.add(ChatFormatting.stripFormatting(line.getString()));
        return out;
    }

    private List<Entry> book() {
        List<Entry> out = new ArrayList<>();
        for (int i = 0; i < 27; i++) {
            ItemStack stack = item(i);
            if (stack.isEmpty()) continue;
            List<String> lore = lore(stack);
            if (lore.isEmpty() || !lore.get(0).startsWith("cookbook:")) continue;
            String[] parts = lore.get(0).substring(9).split(",");
            if (parts.length == 4) out.add(new Entry(i, List.of(parts[0], parts[1], parts[2]), parts[3]));
        }
        return out;
    }

    private String serial() {
        List<String> lore = lore(item(RESULT_SLOT));
        return lore.isEmpty() || !lore.get(0).startsWith("serial:") ? "" : lore.get(0);
    }

    private int have(String id) {
        if (minecraft == null || minecraft.player == null) return 0;
        String want = ITEM_IDS.get(id);
        int total = 0;
        var inv = minecraft.player.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(want)) total += stack.getCount();
        }
        return total;
    }

    private int used(String id) {
        int n = 0;
        for (String b : boxes) if (id.equals(b)) n++;
        return n;
    }

    private ItemStack icon(String id) {
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(ITEM_IDS.get(id)));
        return new ItemStack(item);
    }

    private boolean filled() {
        for (String b : boxes) if (b == null) return false;
        return true;
    }

    private boolean enough() {
        if (!filled()) return false;
        Map<String, Integer> need = new HashMap<>();
        for (String b : boxes) need.merge(b, 1, Integer::sum);
        for (var e : need.entrySet()) if (have(e.getKey()) < e.getValue()) return false;
        return true;
    }

    private boolean knownRecipe() {
        if (!filled()) return false;
        List<String> mine = new ArrayList<>(List.of(boxes[0], boxes[1], boxes[2]));
        mine.sort(null);
        for (Entry e : book()) {
            List<String> theirs = new ArrayList<>(e.ingredients());
            theirs.sort(null);
            if (theirs.equals(mine) && e.seasoning().equals(boxes[3])) return true;
        }
        return false;
    }

    // ------------------------------------------------------------ mini-game

    private void startGame() {
        float size = knownRecipe() ? .35f : .20f;
        zoneSize = size;
        zoneStart = (float) (Math.random() * (1 - size));
        pos = 0;
        dir = 1;
        speed = .9f;
        playing = true;
        gaugeVisible = true;
        showResult = false;
        status = "";
        lastNanos = System.nanoTime();
    }

    private void tickGame() {
        long now = System.nanoTime();
        float dt = Math.min(.05f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        pos += dir * speed * dt;
        if (pos >= 1) { pos = 1; dir = -1; speed *= 1.1f; }
        else if (pos <= 0) { pos = 0; dir = 1; speed *= 1.1f; }
    }

    private void stopGame() {
        if (!playing) return;
        playing = false;
        lastSuccess = pos >= zoneStart && pos <= zoneStart + zoneSize;
        if (minecraft != null && minecraft.getConnection() != null) {
            minecraft.getConnection().sendCommand("pipilife cook " + boxes[0] + " " + boxes[1] + " " + boxes[2] + " " + boxes[3]
                + " " + (lastSuccess ? 1 : 0));
            awaiting = true;
            awaitSince = System.currentTimeMillis();
            awaitSerial = serial();
        }
        status = lastSuccess ? "성공!" : "아쉽게 빗나갔습니다...";
    }

    // ------------------------------------------------------------ render

    @Override public void render(GuiGraphics g, int mx, int my, float delta) {
        double x = (mx - left) / scale, y = (my - top) / scale;
        if (playing) tickGame();
        if (awaiting) {
            if (!serial().equals(awaitSerial)) {
                awaiting = false;
                showResult = true;
                gaugeVisible = false;
                for (int i = 0; i < boxes.length; i++) boxes[i] = null;
                List<String> l = lore(item(RESULT_SLOT));
                status = l.size() > 1 ? l.get(1) : status;
            } else if (System.currentTimeMillis() - awaitSince > 4000) {
                awaiting = false;
                status = "조리가 처리되지 않았습니다. 잠시 후 다시 시도하세요.";
            }
        }
        g.fill(0, 0, width, height, 0xb7081019);
        g.pose().pushPose();
        try {
            g.pose().translate(left, top, 0);
            g.pose().scale(scale, scale, 1);
            // --- notebook
            panel(g, 6, 6, 150, 328, false);
            heading(g, "요리 수첩", 12, 138);
            List<Entry> book = book();
            int shown = 8;
            bookScroll = Math.max(0, Math.min(bookScroll, Math.max(0, book.size() - shown)));
            if (book.isEmpty()) g.drawCenteredString(font, "배운 레시피가 없습니다", 81, 60, MUTED);
            for (int i = 0; i < shown && bookScroll + i < book.size(); i++) {
                Entry e = book.get(bookScroll + i);
                int row = 34 + i * 36;
                panel(g, 12, row, 138, 32, hit(x, y, 12, row, 138, 32) && !playing);
                g.renderItem(item(e.slot()), 16, row + 8);
                String name = ChatFormatting.stripFormatting(item(e.slot()).getHoverName().getString());
                g.drawString(font, font.plainSubstrByWidth(name, 112), 36, row + 5, TEXT, false);
                g.drawString(font, font.plainSubstrByWidth(shortRecipe(e), 112), 36, row + 18, MUTED, false);
            }
            // --- table
            panel(g, 162, 6, 330, 328, false);
            heading(g, "조리대", 168, 318);
            for (int i = 0; i < 4; i++) {
                int bx = boxX(i);
                panel(g, bx, 38, 44, 44, boxes[i] != null && hit(x, y, bx, 38, 44, 44));
                if (boxes[i] != null) g.renderItem(icon(boxes[i]), bx + 14, 52);
                else g.drawCenteredString(font, i < 3 ? "재료" : "조미료", bx + 22, 56, 0xff55606c);
            }
            g.drawString(font, "+", boxX(3) - 8, 56, MUTED, false);
            g.drawString(font, "→", 388, 56, GOLD, false);
            panel(g, 410, 34, 52, 52, false);
            ItemStack result = item(RESULT_SLOT);
            if (showResult && !result.isEmpty()) {
                g.pose().pushPose();
                g.pose().translate(418, 42, 0);
                g.pose().scale(2, 2, 1);
                g.renderItem(result, 0, 0);
                g.pose().popPose();
            } else g.drawCenteredString(font, "?", 436, 56, 0xff55606c);
            g.drawCenteredString(font, font.plainSubstrByWidth(status, 312), 327, 90, lastSuccess || showResult ? GOLD : MUTED);
            panel(g, 168, 104, 318, 224, false);
            heading(g, "식재료", 174, 306);
            for (int i = 0; i < INGREDIENTS.length; i++) cell(g, INGREDIENTS[i], 174 + (i % 2) * 156, 134 + (i / 2) * 30, x, y);
            heading(g, "조미료", 174, 306, 226);
            for (int i = 0; i < SEASONINGS.length; i++) cell(g, SEASONINGS[i], 174 + (i % 2) * 156, 256 + (i / 2) * 30, x, y);
            // --- gauge
            panel(g, 498, 6, 136, 328, false);
            heading(g, "미니게임", 504, 124);
            g.fill(GX - 1, GY - 1, GX + GW + 1, GY + GH + 1, 0xff344658);
            g.fill(GX, GY, GX + GW, GY + GH, 0xff8c2f2f);
            if (gaugeVisible) {
                int zy1 = GY + Math.round(GH * (1 - zoneStart - zoneSize)), zy2 = GY + Math.round(GH * (1 - zoneStart));
                if (zoneSize > 0) g.fill(GX, zy1, GX + GW, zy2, GREEN);
                int my0 = GY + Math.round(GH * (1 - pos));
                g.fill(GX - 8, my0 - 3, GX + GW + 8, my0 + 3, 0xff1c1410);
                g.fill(GX - 7, my0 - 2, GX + GW + 7, my0 + 2, 0xfff0e4c8);
                g.fill(GX + GW + 8, my0 - 6, GX + GW + 14, my0 + 6, 0xffb78b52); // ladle bowl
            }
            if (playing) g.drawCenteredString(font, "클릭/스페이스로 멈추기", 566, 288, TEXT);
            boolean ready = enough() && !playing && !awaiting;
            String label = playing ? "진행 중" : awaiting ? "조리 중..." : enough() ? "요리하기" : "재료 부족";
            panel(g, 508, 300, 116, 28, ready && hit(x, y, 508, 300, 116, 28));
            g.drawCenteredString(font, label, 566, 310, ready ? TEXT : 0xff66727f);
        } finally {
            g.pose().popPose();
        }
    }

    private static int boxX(int i) { return 172 + i * 54 + (i == 3 ? 12 : 0); }

    private String shortRecipe(Entry e) {
        StringBuilder sb = new StringBuilder();
        for (String ing : e.ingredients()) sb.append(sb.length() == 0 ? "" : "·").append(LABELS.getOrDefault(ing, ing));
        return sb + "+" + LABELS.getOrDefault(e.seasoning(), e.seasoning());
    }

    private void cell(GuiGraphics g, String id, int cx, int cy, double x, double y) {
        int left = have(id) - used(id);
        boolean ok = left > 0;
        panel(g, cx, cy, 150, 26, ok && !playing && hit(x, y, cx, cy, 150, 26));
        g.renderItem(icon(id), cx + 5, cy + 5);
        g.drawString(font, LABELS.get(id), cx + 28, cy + 9, ok ? TEXT : 0xff66727f, false);
        String n = "x" + Math.max(0, left);
        g.drawString(font, n, cx + 144 - font.width(n), cy + 9, ok ? GOLD : 0xff66727f, false);
    }

    private void heading(GuiGraphics g, String label, int x, int w) { heading(g, label, x, w, 12); }

    private void heading(GuiGraphics g, String label, int x, int w, int y) {
        g.fillGradient(x, y - 6, x + w, y + 14, 0xff26394c, 0xff192737);
        g.drawCenteredString(font, label, x + w / 2, y, TEXT);
    }

    private static void panel(GuiGraphics g, int x, int y, int w, int h, boolean active) {
        g.fill(x, y, x + w, y + h, active ? 0xff7b99b5 : 0xff344658);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, active ? 0xff263648 : 0xff17222e, 0xff101820);
    }

    private static boolean hit(double x, double y, int bx, int by, int w, int h) {
        return x >= bx && x < bx + w && y >= by && y < by + h;
    }

    // ------------------------------------------------------------ input

    private void addIngredient(String id) {
        if (have(id) - used(id) <= 0) return;
        for (int i = 0; i < 3; i++) if (boxes[i] == null) { boxes[i] = id; return; }
    }

    private void fillRecipe(Entry e) {
        Map<String, Integer> need = new HashMap<>();
        for (String ing : e.ingredients()) need.merge(ing, 1, Integer::sum);
        need.merge(e.seasoning(), 1, Integer::sum);
        for (var n : need.entrySet()) if (have(n.getKey()) < n.getValue()) { status = "재료가 부족합니다."; return; }
        for (int i = 0; i < 3; i++) boxes[i] = e.ingredients().get(i);
        boxes[3] = e.seasoning();
        status = "";
        showResult = false;
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return true;
        if (playing) { stopGame(); return true; }
        if (awaiting) return true;
        double x = (mx - left) / scale, y = (my - top) / scale;
        List<Entry> book = book();
        for (int i = 0; i < 8 && bookScroll + i < book.size(); i++)
            if (hit(x, y, 12, 34 + i * 36, 138, 32)) { fillRecipe(book.get(bookScroll + i)); return true; }
        for (int i = 0; i < 4; i++) if (hit(x, y, boxX(i), 38, 44, 44)) { boxes[i] = null; return true; }
        for (int i = 0; i < INGREDIENTS.length; i++)
            if (hit(x, y, 174 + (i % 2) * 156, 134 + (i / 2) * 30, 150, 26)) { addIngredient(INGREDIENTS[i]); showResult = false; return true; }
        for (int i = 0; i < SEASONINGS.length; i++)
            if (hit(x, y, 174 + (i % 2) * 156, 256 + (i / 2) * 30, 150, 26) && have(SEASONINGS[i]) > 0) { boxes[3] = SEASONINGS[i]; showResult = false; return true; }
        if (hit(x, y, 508, 300, 116, 28) && enough()) startGame();
        return true;
    }

    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (!playing && dy != 0) bookScroll -= (int) Math.signum(dy);
        return true;
    }
    @Override public boolean mouseReleased(double x, double y, int button) { return true; }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) { return true; }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) onClose();
        else if (key == GLFW.GLFW_KEY_SPACE && playing) stopGame();
        return true;
    }
}
