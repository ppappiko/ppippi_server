package dev.pipi.travel;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

/**
 * 무기 제련(파쇄) screen, same window/style as UpgradeScreen (720x370). Server protocol (hidden chat, cancelled here):
 *   PIPI_SME_BEGIN:count:open(0|1)  then count x  PIPI_SME_ITEM:invSlot:hash:warn(0|1):base64(weapon):base64(refund),base64(refund)...  then PIPI_SME_END
 *   (long lines are split by the server as PIPI_ARM_PART and reassembled in ArmoryScreen)
 * Client commands: pipilife smelt list | smelt do invSlot hash [confirm]
 */
final class SmeltScreen extends Screen {
    record Entry(int slot, String hash, boolean warn, ItemStack stack, List<ItemStack> refund, GearCard.Data info) {}

    private static final Logger LOG = LogUtils.getLogger();
    private static final String BEGIN = "PIPI_SME_BEGIN:", ITEM = "PIPI_SME_ITEM:", END = "PIPI_SME_END";
    private static final int W = 720, H = 370, ROWS = 9;
    private static final int TEXT = 0xffe6eef6, MUTED = 0xff9fb4ca, GOLD = 0xffe6c588, RED = 0xffff6b6b, GREEN = 0xff82d99a;

    private static List<Entry> data = List.of(), pending = new ArrayList<>();
    private static boolean pendingOpen, decodeWarned;
    private static int expected;

    private float scale, left, top;
    private int first;
    private String selectedHash;
    private boolean armed;
    private long lastSend, busyUntil;

    SmeltScreen() { super(Component.literal("무기 제련")); }

    // ---- network ----
    static void receive(ClientChatReceivedEvent.System event) {
        String text = event.getMessage().getString();
        if (!text.startsWith("PIPI_SME_")) return;
        event.setCanceled(true);
        process(text);
    }

    static void process(String text) {
        try {
            if (text.startsWith(BEGIN)) {
                pending = new ArrayList<>();
                pendingOpen = text.endsWith(":1");
                String[] p = text.split(":");
                expected = p.length > 1 ? Integer.parseInt(p[1]) : 0;
            } else if (text.startsWith(ITEM)) {
                String[] p = text.split(":", 6);
                if (p.length < 5) return;
                ItemStack stack = ItemWire.decode(p[4]);
                if (stack.isEmpty()) {
                    if (!decodeWarned) { decodeWarned = true; LOG.warn("[PipiTravel] smelt item decoded to an empty stack"); }
                    return;
                }
                List<ItemStack> refund = new ArrayList<>();
                if (p.length >= 6) for (String part : p[5].split(",")) {
                    if (part.isBlank()) continue;
                    ItemStack r = ItemWire.decode(part);
                    if (!r.isEmpty()) refund.add(r);
                }
                pending.add(new Entry(Integer.parseInt(p[1]), p[2], p[3].equals("1"), stack, refund, GearCard.parse(stack)));
            } else if (text.startsWith(END)) {
                LOG.debug("[PipiTravel] smelt received: expected {}, decoded {}, open={}", expected, pending.size(), pendingOpen);
                data = pending;
                pending = new ArrayList<>();
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen instanceof SmeltScreen s) s.refresh();
                else if (pendingOpen && mc.level != null) mc.setScreen(new SmeltScreen());
                pendingOpen = false;
            }
        } catch (RuntimeException ex) {
            if (!decodeWarned) { decodeWarned = true; LOG.warn("[PipiTravel] smelt item decode failed: {}", ex.toString()); }
        }
    }

    private void refresh() {
        busyUntil = 0;
        armed = false;
        if (selected() == null) selectedHash = null;
        first = Math.min(first, maxFirst());
    }

    private Entry selected() {
        if (selectedHash == null) return null;
        for (Entry e : data) if (e.hash.equals(selectedHash)) return e;
        return null;
    }

    private int maxFirst() { return Math.max(0, data.size() - ROWS); }

    private void send(String cmd) {
        long now = System.currentTimeMillis();
        if (now < busyUntil || now - lastSend < 250 || minecraft == null || minecraft.getConnection() == null) return;
        lastSend = now;
        busyUntil = now + 1500;
        minecraft.getConnection().sendCommand("pipilife " + cmd);
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

    private static String name(ItemStack s) { return ChatFormatting.stripFormatting(s.getHoverName().getString()); }

    // ---- drawing (identical helpers/colours to UpgradeScreen) ----
    private static void panel(GuiGraphics g, int x, int y, int w, int h, boolean selected) {
        g.fill(x, y, x + w, y + h, selected ? 0xffa78c59 : 0xff3d5369);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, 0xff1a2a3a, 0xff0c1721);
    }

    private void heading(GuiGraphics g, int x, int w, String label) {
        g.fillGradient(x, 5, x + w, 28, 0xff2d4054, 0xff1c2a3b);
        g.drawString(font, label, x + 6, 13, TEXT, false);
    }

    private void button(GuiGraphics g, int x, int y, int w, int h, String label, double mx, double my) {
        panel(g, x, y, w, h, hit(mx, my, x, y, w, h));
        g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, TEXT);
    }

    private static void icon(GuiGraphics g, ItemStack item, int x, int y, float size) {
        g.pose().pushPose();
        try { g.pose().translate(x, y, 0); g.pose().scale(size, size, 1); g.renderItem(item, 0, 0); }
        finally { g.pose().popPose(); }
    }

    private static boolean hit(double x, double y, int bx, int by, int w, int h) { return x >= bx && y >= by && x < bx + w && y < by + h; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        double x = (mx - left) / scale, y = (my - top) / scale;
        g.fill(0, 0, width, height, 0xb9081019);
        Entry sel = selected();
        ItemStack hoverStack = null;
        g.pose().pushPose();
        try {
            g.pose().translate(left, top, 0);
            g.pose().scale(scale, scale, 1);
            panel(g, 0, 0, 210, H, false);
            panel(g, 214, 0, 308, H, false);
            panel(g, 526, 0, 194, H, false);
            heading(g, 6, 198, "보유 무기");
            heading(g, 220, 296, "제련");
            heading(g, 532, 182, "무기 정보");
            g.fill(691, 7, 712, 26, hit(x, y, 691, 7, 21, 19) ? 0xffa78c59 : 0xff3d5369);
            g.fillGradient(692, 8, 711, 25, hit(x, y, 691, 7, 21, 19) ? 0xff3a4d63 : 0xff1a2a3a, 0xff0c1721);
            g.drawCenteredString(font, "X", 701, 13, TEXT);
            for (int i = 0; i < ROWS && first + i < data.size(); i++) {
                Entry e = data.get(first + i);
                int row = 38 + i * 31;
                panel(g, 7, row, 196, 29, hit(x, y, 7, row, 196, 29) || sel == e);
                g.renderItem(e.stack, 12, row + 6);
                g.drawString(font, font.plainSubstrByWidth(name(e.stack), 165), 34, row + 4, TEXT, false);
                if (e.warn) g.drawString(font, "강화·인챈트 적용", 34, row + 16, RED, false);
                if (hit(x, y, 7, row, 196, 29)) hoverStack = e.stack;
            }
            if (data.isEmpty()) g.drawCenteredString(font, "제련할 무기가 없습니다", 105, 160, MUTED);
            if (first > 0) button(g, 8, 338, 82, 24, "이전", x, y);
            if (first < maxFirst()) button(g, 120, 338, 82, 24, "다음", x, y);
            button(g, 537, 338, 172, 24, "대장간으로", x, y);

            if (sel == null) {
                g.drawCenteredString(font, "제련할 무기를 선택하세요.", 368, 142, MUTED);
                g.drawCenteredString(font, "가방에 있는 제작 무기가 표시됩니다.", 368, 161, MUTED);
                return;
            }
            // centre: only the resources returned by smelting
            g.drawCenteredString(font, "파쇄하면 돌려받는 재료", 368, 40, GOLD);
            int n = Math.min(5, sel.refund.size());
            for (int i = 0; i < n; i++) {
                ItemStack r = sel.refund.get(i);
                int ry = 62 + i * 46;
                panel(g, 236, ry, 264, 40, false);
                icon(g, r, 246, ry + 4, 2f);
                g.drawString(font, font.plainSubstrByWidth(name(r), 150), 288, ry + 16, TEXT, false);
                String count = "×" + r.getCount();
                g.drawString(font, count, 490 - font.width(count), ry + 16, GOLD, false);
                if (hit(x, y, 236, ry, 264, 40)) hoverStack = r;
            }
            if (sel.refund.isEmpty()) g.drawCenteredString(font, "돌려받는 재료가 없습니다", 368, 150, MUTED);
            if (sel.warn) {
                g.drawCenteredString(font, "강화·인챈트가 적용된 무기입니다. 모두 사라집니다", 368, 300, RED);
                g.drawCenteredString(font, armed ? "한 번 더 누르면 영구 파쇄됩니다" : "파쇄하면 복구할 수 없습니다", 368, 314, armed ? RED : MUTED);
            }
            button(g, 290, 338, 156, 24, armed ? "파쇄 확인" : "파쇄", x, y);

            // right: weapon info
            GearCard.Data d = sel.info;
            panel(g, 535, 38, 176, 290, false);
            g.renderItem(sel.stack, 542, 44);
            g.drawString(font, font.plainSubstrByWidth(d.name() + (d.upgrade() > 0 ? " +" + d.upgrade() : ""), 138), 564, 45, TEXT, false);
            g.drawString(font, d.grade() >= 0 ? GearCard.GRADES[d.grade()] : "일반", 564, 56, MUTED, false);
            int ty = 76;
            if (d.ilvl() > 0) { ty = row(g, "레벨", "Lv." + d.ilvl(), ty, TEXT); }
            if (d.main() != null) ty = row(g, d.main().label(), d.main().value(), ty, 0xfff2e04a);
            for (GearCard.Row r : d.subs()) ty = row(g, r.label(), "+" + r.value(), ty, GearCard.subColor(r.label()));
            for (GearCard.Ench en : d.enchants()) {
                if (ty > 312) break;
                g.drawString(font, font.plainSubstrByWidth("◆ " + en.text(), 162), 542, ty, GREEN, false);
                ty += 12;
            }
            if (hit(x, y, 535, 38, 176, 28)) hoverStack = sel.stack;
        } finally { g.pose().popPose(); }
        if (hoverStack != null) {
            if (GearCard.isCard(hoverStack)) GearCard.draw(g, font, hoverStack, mx, my, width, height);
            else g.renderTooltip(font, hoverStack, mx, my);
        }
    }

    private int row(GuiGraphics g, String label, String value, int y, int color) {
        g.drawString(font, label, 542, y, MUTED, false);
        g.drawString(font, value, 704 - font.width(value), y, color, false);
        return y + 12;
    }

    // ---- input ----
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return true;
        double x = (mx - left) / scale, y = (my - top) / scale;
        if (hit(x, y, 691, 7, 21, 19)) { onClose(); return true; }
        for (int i = 0; i < ROWS; i++) {
            if (hit(x, y, 7, 38 + i * 31, 196, 29) && first + i < data.size()) {
                String hash = data.get(first + i).hash;
                if (!hash.equals(selectedHash)) armed = false;
                selectedHash = hash;
                return true;
            }
        }
        Entry sel = selected();
        if (hit(x, y, 8, 338, 82, 24) && first > 0) first = Math.max(0, first - ROWS);
        else if (hit(x, y, 120, 338, 82, 24) && first < maxFirst()) first = Math.min(maxFirst(), first + ROWS);
        else if (hit(x, y, 537, 338, 172, 24)) send("forge");
        else if (sel != null && hit(x, y, 290, 338, 156, 24)) {
            if (sel.warn && !armed) { armed = true; return true; }
            send("smelt do " + sel.slot + " " + sel.hash + (sel.warn ? " confirm" : ""));
            armed = false;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        first = Math.clamp(first - (int) Math.signum(dy), 0, maxFirst());
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }
}
