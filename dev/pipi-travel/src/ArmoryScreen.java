package dev.pipi.travel;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

/**
 * 무기 관리인 screen (fixed 660x320 window like ShopScreen). Server protocol (hidden chat messages, cancelled here):
 *   PIPI_ARM_BEGIN:count:open(0|1)  then count x  PIPI_ARM_ITEM:invSlot(-1=equipped):hash:equipped(0|1):base64(item bytes)  then PIPI_ARM_END
 *   long lines arrive as PIPI_ARM_PART:id:idx:total:chunk (reassembled here, then handled as the original line; also carries PIPI_SME_* lines)
 * Client commands: pipilife armory equip invSlot hash | unequip | list
 */
final class ArmoryScreen extends Screen {
    record Entry(int slot, String hash, boolean equipped, ItemStack stack, GearCard.Data info) {}

    private static final Logger LOG = LogUtils.getLogger();
    private static final String BEGIN = "PIPI_ARM_BEGIN:", ITEM = "PIPI_ARM_ITEM:", END = "PIPI_ARM_END", PART = "PIPI_ARM_PART:";
    private static final int W = 660, H = 320, ROW = 30, ROWS = 8, LIST_Y = 66;
    private static final int TEXT = 0xffe6eef6, MUTED = 0xff9fb4ca, GOLD = 0xffe6c588, GREEN = 0xff82d99a;
    private static final String[] TABS = {"전체", "전사", "궁수", "마법사"};
    private static final String[] TAB_TYPE = {"", "검", "활", "지팡이"};
    private static final String[] EMBLEM = {"", "w_basic", "a_basic", "m_basic"};

    private static List<Entry> data = List.of(), pending = new ArrayList<>();
    private static boolean pendingOpen, decodeWarned;
    private static int expected;
    private static final Map<Integer, String[]> PARTS = new HashMap<>();

    private float scale, left, top;
    private int tab, first;
    private Entry selected;
    private String selectedHash;
    private Entry dragging;
    private long lastSend, busyUntil;

    ArmoryScreen() {
        super(Component.literal("무기 관리인"));
        selected = equippedEntry();
        selectedHash = selected == null ? null : selected.hash;
    }

    // ---- network ----
    static void receive(ClientChatReceivedEvent.System event) {
        String text = event.getMessage().getString();
        if (!text.startsWith("PIPI_ARM_")) return;
        event.setCanceled(true);
        try {
            if (text.startsWith(PART)) {
                String[] p = text.split(":", 5);
                if (p.length < 5) return;
                int id = Integer.parseInt(p[1]), idx = Integer.parseInt(p[2]), total = Integer.parseInt(p[3]);
                if (total < 1 || total > 256 || idx < 0 || idx >= total) return;
                String[] chunks = PARTS.computeIfAbsent(id, k -> new String[total]);
                if (chunks.length != total) { PARTS.remove(id); return; }
                chunks[idx] = p[4];
                for (String c : chunks) if (c == null) return;
                PARTS.remove(id);
                if (PARTS.size() > 32) PARTS.clear();
                dispatch(String.join("", chunks));
            } else dispatch(text);
        } catch (RuntimeException ex) {
            LOG.warn("[PipiTravel] armory message failed: {}", ex.toString());
        }
    }

    /** Handles one complete hidden line (PIPI_ARM_* here, PIPI_SME_* forwarded to the refine screen). */
    static void dispatch(String text) {
        if (text.startsWith("PIPI_SME_")) { SmeltScreen.process(text); return; }
        if (text.startsWith("PIPI_TRADE_")) { TradeScreen.process(text); return; }
        try {
            if (text.startsWith(BEGIN)) {
                pending = new ArrayList<>();
                pendingOpen = text.endsWith(":1");
                String[] p = text.split(":");
                expected = p.length > 1 ? Integer.parseInt(p[1]) : 0;
            } else if (text.startsWith(ITEM)) {
                String[] p = text.split(":", 5);
                if (p.length < 5) return;
                ItemStack stack = ItemWire.decode(p[4]);
                if (stack.isEmpty()) {
                    if (!decodeWarned) { decodeWarned = true; LOG.warn("[PipiTravel] armory item decoded to an empty stack (level present: {})", Minecraft.getInstance().level != null); }
                    return;
                }
                pending.add(new Entry(Integer.parseInt(p[1]), p[2], p[3].equals("1"), stack, GearCard.parse(stack)));
            } else if (text.startsWith(END)) {
                LOG.debug("[PipiTravel] armory received: expected {}, decoded {}, open={}", expected, pending.size(), pendingOpen);
                data = pending;
                pending = new ArrayList<>();
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen instanceof ArmoryScreen s) s.refresh();
                else if (pendingOpen && mc.level != null) mc.setScreen(new ArmoryScreen());
                pendingOpen = false;
            }
        } catch (RuntimeException ex) {
            if (!decodeWarned) { decodeWarned = true; LOG.warn("[PipiTravel] armory item decode failed: {}", ex.toString()); }
        }
    }

    private static Entry equippedEntry() {
        for (Entry e : data) if (e.equipped) return e;
        return null;
    }

    private void refresh() {
        busyUntil = 0;
        Entry eq = equippedEntry();
        Entry keep = null;
        if (selectedHash != null) for (Entry e : data) if (e.hash.equals(selectedHash)) { keep = e; break; }
        selected = eq != null ? eq : keep;
        selectedHash = selected == null ? null : selected.hash;
        dragging = null;
        first = Math.min(first, maxFirst());
    }

    private void send(String cmd) {
        long now = System.currentTimeMillis();
        if (now < busyUntil || now - lastSend < 250 || minecraft == null || minecraft.getConnection() == null) return;
        lastSend = now;
        busyUntil = now + 1500;
        minecraft.getConnection().sendCommand("pipilife armory " + cmd);
    }

    // ---- layout ----
    @Override
    protected void init() {
        scale = Math.max(.01f, Math.min((width - 14f) / W, (height - 14f) / H));
        left = (width - W * scale) / 2;
        top = (height - H * scale) / 2;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private List<Entry> shown() {
        if (tab == 0) return data;
        List<Entry> out = new ArrayList<>();
        for (Entry e : data) if (e.info.slot().equals(TAB_TYPE[tab])) out.add(e);
        return out;
    }

    private int maxFirst() { return Math.max(0, shown().size() - ROWS); }

    private static int classOf(Entry e) {
        for (int i = 1; i < 4; i++) if (TAB_TYPE[i].equals(e.info.slot())) return i;
        return 0;
    }

    private static ResourceLocation emblem(int cls) {
        return ResourceLocation.fromNamespaceAndPath("pipitravel", "textures/gui/skills/" + EMBLEM[cls] + ".png");
    }

    private static String title(Entry e) {
        return e.info.name() + (e.info.upgrade() > 0 ? " +" + e.info.upgrade() : "");
    }

    // ---- drawing (same panel style as UpgradeScreen) ----
    private static void panel(GuiGraphics g, int x, int y, int w, int h, boolean selected) {
        g.fill(x, y, x + w, y + h, selected ? 0xffa78c59 : 0xff3d5369);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, 0xff1a2a3a, 0xff0c1721);
    }

    private void heading(GuiGraphics g, int x, int w, String label) {
        g.fillGradient(x, 5, x + w, 28, 0xff2d4054, 0xff1c2a3b);
        g.drawString(font, label, x + 6, 13, TEXT, false);
    }

    private void button(GuiGraphics g, String label, int x, int y, int w, int h, boolean on, boolean hover) {
        panel(g, x, y, w, h, on && hover);
        g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, on ? TEXT : 0xff5d6677);
    }

    private void diamond(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int half = r - Math.abs(dy);
            g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0xb9081019);
        double vx = (mouseX - left) / scale, vy = (mouseY - top) / scale;
        List<Entry> list = shown();
        Entry hoverEntry = null;
        String hoverTab = null;
        g.pose().pushPose();
        g.pose().translate(left, top, 0);
        g.pose().scale(scale, scale, 1f);
        try {
            // ---- left: tabs + list ----
            panel(g, 0, 0, 210, H, false);
            heading(g, 6, 198, "보유 무기");
            for (int i = 0; i < 4; i++) {
                int tx = 8 + i * 30;
                panel(g, tx, 34, 26, 26, i == tab);
                int ix = tx + 5, iy = 39;
                switch (i) {
                    case 0 -> {
                        for (int a = 0; a < 3; a++) for (int b = 0; b < 3; b++)
                            g.fill(ix + a * 6, iy + b * 6, ix + a * 6 + 4, iy + b * 6 + 4, i == tab ? GOLD : 0xff7d8aa8);
                    }
                    case 1 -> g.renderItem(new ItemStack(Items.IRON_SWORD), ix, iy);
                    case 2 -> g.renderItem(new ItemStack(Items.BOW), ix, iy);
                    default -> g.renderItem(new ItemStack(Items.BLAZE_ROD), ix, iy);
                }
                if (in(vx, vy, tx, 34, 26, 26)) hoverTab = TABS[i];
            }
            g.drawString(font, TABS[tab], 134, 43, GOLD, false);
            if (list.isEmpty()) {
                g.drawCenteredString(font, "무기가 없습니다", 105, LIST_Y + 60, MUTED);
                g.drawCenteredString(font, "가방에 제작한 무기가 있어야 합니다", 105, LIST_Y + 76, 0xff5d6677);
            }
            for (int i = 0; i < ROWS && first + i < list.size(); i++) {
                Entry e = list.get(first + i);
                int ry = LIST_Y + i * ROW;
                boolean sel = selected != null && e.hash.equals(selected.hash) && e.equipped == selected.equipped;
                boolean hov = in(vx, vy, 7, ry, 183, ROW - 2);
                panel(g, 7, ry, 183, ROW - 2, sel || hov);
                g.renderItem(e.stack, 11, ry + 6);
                g.drawString(font, font.plainSubstrByWidth(title(e), e.equipped ? 96 : 150), 32, ry + 5, TEXT, false);
                int slots = Math.max(e.info.slots(), e.info.enchants().size());
                for (int k = 0; k < slots && k < 8; k++)
                    g.drawString(font, k < e.info.enchants().size() ? "◆" : "◇", 32 + k * 9, ry + 16, k < e.info.enchants().size() ? GREEN : MUTED, false);
                if (e.equipped) g.drawString(font, "장착중", 186 - font.width("장착중"), ry + 5, GREEN, false);
                if (hov) hoverEntry = e;
            }
            if (maxFirst() > 0) {
                int trackH = ROWS * ROW - 2;
                int barH = Math.max(18, trackH * ROWS / list.size());
                int barY = LIST_Y + (int) ((trackH - barH) * (first / (double) maxFirst()));
                g.fill(195, LIST_Y, 199, LIST_Y + trackH, 0xff080f17);
                g.fill(195, barY, 199, barY + barH, GOLD);
            }

            // ---- middle: slot ----
            panel(g, 214, 0, 232, H, false);
            heading(g, 220, 220, "장착");
            int cx = 330, cy = 122;
            diamond(g, cx, cy, 62, 0xff6e5a2c);
            diamond(g, cx, cy, 60, GOLD);
            diamond(g, cx, cy, 57, 0xff0a101d);
            diamond(g, cx, cy, 52, 0xff142038);
            diamond(g, cx, cy, 44, 0xff0f1830);
            for (int[] t : new int[][]{{cx, cy - 62}, {cx, cy + 62}, {cx - 62, cy}, {cx + 62, cy}}) {
                diamond(g, t[0], t[1], 5, GOLD);
                diamond(g, t[0], t[1], 2, 0xff0a101d);
            }
            boolean overSlot = in(vx, vy, cx - 40, cy - 40, 80, 80);
            if (selected != null) {
                g.pose().pushPose();
                g.pose().translate(cx - 24, cy - 24, 0);
                g.pose().scale(3f, 3f, 1f);
                g.renderItem(selected.stack, 0, 0);
                g.pose().popPose();
                g.drawCenteredString(font, font.plainSubstrByWidth(title(selected), 210), cx, 194, 0xfff6e7b0);
                int cls = classOf(selected);
                if (cls > 0) {
                    g.blit(emblem(cls), cx - 16, 208, 32, 32, 0, 0, 32, 32, 32, 32);
                    g.drawCenteredString(font, TABS[cls], cx, 244, MUTED);
                }
                if (overSlot && dragging == null) hoverEntry = selected;
            } else {
                g.drawCenteredString(font, "무기를 선택하세요", cx, cy - 4, MUTED);
                g.drawCenteredString(font, "왼쪽 목록에서 고르거나 끌어다 놓으세요", cx, 194, 0xff5d6677);
            }
            boolean eq = selected != null && selected.equipped;
            button(g, selected == null ? "장착" : eq ? "장착 해제" : "장착", 226, 284, 208, 26, selected != null, in(vx, vy, 226, 284, 208, 26));

            // ---- right: details ----
            panel(g, 450, 0, 210, H, false);
            heading(g, 456, 198, "세부 능력치");
            g.fill(629, 7, 650, 26, in(vx, vy, 629, 7, 21, 19) ? 0xffa78c59 : 0xff3d5369);
            g.fillGradient(630, 8, 649, 25, in(vx, vy, 629, 7, 21, 19) ? 0xff3a4d63 : 0xff1a2a3a, 0xff0c1721);
            g.drawCenteredString(font, "X", 639, 13, TEXT);
            Entry cur = equippedEntry();
            if (cur == null) {
                g.drawCenteredString(font, "장착한 무기가 없습니다", 555, 150, MUTED);
            } else {
                GearCard.Data d = cur.info;
                int accent = d.grade() >= 0 ? GearCard.GRADE_COL[d.grade()] : GREEN;
                panel(g, 458, 36, 22, 22, false);
                g.renderItem(cur.stack, 461, 39);
                g.drawString(font, font.plainSubstrByWidth(title(cur), 166), 486, 38, accent, false);
                g.drawString(font, d.grade() >= 0 ? GearCard.GRADES[d.grade()] : "일반", 486, 49, MUTED, false);
                int y = 68;
                y = row(g, "종류", d.slot() + (classOf(cur) > 0 ? " · " + TABS[classOf(cur)] : ""), y, TEXT);
                if (d.ilvl() > 0) y = row(g, "레벨", "Lv." + d.ilvl(), y, TEXT);
                if (d.main() != null) y = row(g, d.main().label(), d.main().value(), y, 0xfff2e04a);
                for (GearCard.Row r : d.subs()) y = row(g, r.label(), "+" + r.value(), y, GearCard.subColor(r.label()));
                int slots = Math.max(d.slots(), d.enchants().size());
                if (slots > 0) {
                    y += 3;
                    g.fill(458, y, 652, y + 1, 0xff3d5369);
                    y += 5;
                    g.drawString(font, "인챈트", 458, y, MUTED, false);
                    y += 12;
                    for (GearCard.Ench en : d.enchants()) {
                        if (y > 284) break;
                        g.drawString(font, font.plainSubstrByWidth("◆ " + en.text(), 194), 458, y, GREEN, false);
                        y += 11;
                    }
                    for (int i = d.enchants().size(); i < slots && y <= 284; i++) { g.drawString(font, "◇ 빈 인챈트 슬롯", 458, y, MUTED, false); y += 11; }
                }
                g.fill(458, 294, 652, 295, 0xff3d5369);
                g.drawString(font, font.plainSubstrByWidth("소유자: " + (d.owner().isEmpty() ? "-" : d.owner()), 194), 458, 301, MUTED, false);
                if (in(vx, vy, 458, 36, 194, 22)) hoverEntry = cur;
            }

            if (dragging != null) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 300);
                g.renderItem(dragging.stack, (int) vx - 8, (int) vy - 8);
                g.pose().popPose();
            }
        } finally {
            g.pose().popPose();
        }
        if (hoverEntry != null && GearCard.isCard(hoverEntry.stack) && dragging == null)
            GearCard.draw(g, font, hoverEntry.stack, mouseX, mouseY, width, height);
        else if (hoverTab != null) g.renderTooltip(font, Component.literal(hoverTab), mouseX, mouseY);
    }

    private int row(GuiGraphics g, String label, String value, int y, int color) {
        g.drawString(font, label, 458, y, MUTED, false);
        g.drawString(font, value, 652 - font.width(value), y, color, false);
        return y + 12;
    }

    private static boolean in(double x, double y, int rx, int ry, int rw, int rh) {
        return x >= rx && x < rx + rw && y >= ry && y < ry + rh;
    }

    // ---- input ----
    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        if (b != 0) return true;
        double x = (mx - left) / scale, y = (my - top) / scale;
        if (in(x, y, 629, 7, 21, 19)) { onClose(); return true; }
        for (int i = 0; i < 4; i++) if (in(x, y, 8 + i * 30, 34, 26, 26)) { tab = i; first = 0; return true; }
        if (in(x, y, 7, LIST_Y, 183, ROWS * ROW)) {
            int idx = first + (int) ((y - LIST_Y) / ROW);
            List<Entry> list = shown();
            if (idx >= 0 && idx < list.size()) {
                selected = list.get(idx);
                selectedHash = selected.hash;
                dragging = selected;
            }
            return true;
        }
        if (in(x, y, 226, 284, 208, 26) && selected != null) {
            if (selected.equipped) send("unequip");
            else send("equip " + selected.slot + " " + selected.hash);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int b) {
        Entry drop = dragging;
        dragging = null;
        double x = (mx - left) / scale, y = (my - top) / scale;
        if (drop != null && !drop.equipped && in(x, y, 290, 82, 80, 80)) send("equip " + drop.slot + " " + drop.hash);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        first = Math.clamp(first - (int) Math.signum(sy), 0, maxFirst());
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }
}
