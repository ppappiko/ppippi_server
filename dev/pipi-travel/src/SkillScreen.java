package dev.pipi.travel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import org.lwjgl.glfw.GLFW;

/**
 * Client-only skill window (K): class tabs, three columns, hover tooltips, and the skill loadout (which general skills
 * sit in keys 1-3). Data mirrors dev/plans/skills.md.
 */
final class SkillScreen extends Screen {
    private record Skill(String id, String name, String key, String cooldown, String coef, String effect) {}

    private static final String[] TAB_NAMES = {"전사", "궁수", "마법사", "공통"};
    private static final String[] TAB_ICONS = {"w_basic", "a_basic", "m_basic", "c_dash"};
    private static final String[] TAB_CLS = {"w", "a", "m"};
    private static final String[] COLUMNS = {"기본 공격", "일반 스킬", "궁극기"};
    /** Number of equippable general skills at the top of column 1 (the rest, heal / shield, are fixed). */
    private static final int GENERAL = 5;
    private static final String EQUIP = "-"; // key of an equippable skill: it uses whichever key 1-3 it is equipped in

    // per tab: [0] basic, [1] skills (5 equippable, then heal / shield, or dash), [2] ultimate
    private static final List<List<List<Skill>>> DATA = List.of(
        List.of(
            List.of(new Skill("w_basic", "가르기", "좌클릭", "0.5초", "100%", "전방 3.6칸 부채꼴로 베어 적을 공격합니다.")),
            List.of(
                new Skill("w_rush", "돌진 베기", EQUIP, "4초", "180%", "전방으로 약 8칸 대쉬하며 지나친 모든 적을 벱니다. 2회 충전, 연속 적중 시 추가 피해 50%."),
                new Skill("w_whirl", "회오리 베기", EQUIP, "5초", "160%", "반경 5.25칸을 360도로 휩쓸어 벱니다."),
                new Skill("w_split", "대지 가르기", EQUIP, "6초", "220%", "힘을 모아 거대한 검기를 날립니다. 전방 14칸의 적을 꿰뚫습니다."),
                new Skill("w_leap", "도약 강타", EQUIP, "7초", "200%", "앞쪽 약 6칸 위로 도약해 내려찍습니다. 착지 지점 반경 3.5칸의 적에게 피해를 줍니다."),
                new Skill("w_flurry", "연속 베기", EQUIP, "5초", "90% x3", "전방 4칸 부채꼴로 빠르게 세 번 베어냅니다."),
                new Skill("w_heal", "투지", "4", "25초", "", "최대 체력의 25%를 회복하고 4초간 받는 피해가 20% 줄어듭니다."),
                new Skill("w_shield", "강철 의지", "6", "20초", "", "최대 체력의 20%만큼 보호막을 6초간 얻습니다. 보호막이 체력보다 먼저 피해를 흡수합니다.")),
            List.of(new Skill("w_ult", "검기 난사", "5", "90초", "공격력의 40% × 20회", "5초 동안 주변 반경 5.25칸에 검기를 난사해 범위 안의 모든 적을 연속으로 벱니다."))),
        List.of(
            List.of(new Skill("a_basic", "속사", "좌클릭", "0.5초", "100%", "화살 한 발을 빠르게 쏩니다.")),
            List.of(
                new Skill("a_triple", "삼연사", EQUIP, "3초", "90% x3", "화살 3발을 연달아 쏩니다."),
                new Skill("a_pierce", "관통 화살", EQUIP, "5초", "200%", "전방 20칸 직선의 모든 적을 꿰뚫습니다. 2회 충전, 연속 적중 시 추가 피해 50%."),
                new Skill("a_rain", "화살비", EQUIP, "7초", "50% x6", "겨냥한 지점(반경 4칸)에 3초간 화살이 쏟아집니다."),
                new Skill("a_venom", "맹독 화살", EQUIP, "6초", "60% + 30% x5", "독화살을 쏴 적을 중독시킵니다. 5초 동안 1초마다 독 피해를 입힙니다."),
                new Skill("a_retreat", "후퇴 사격", EQUIP, "8초", "80% x3", "뒤로 약 5칸 훌쩍 물러나며 화살 3발을 부채꼴로 쏩니다."),
                new Skill("a_heal", "숲의 숨결", "4", "25초", "", "최대 체력의 20%를 회복하고 5초간 재생과 신속을 얻습니다."),
                new Skill("a_shield", "바람 장막", "6", "18초", "", "최대 체력의 15%만큼 보호막을 6초간 얻고, 3초간 이동 속도가 증가합니다.")),
            List.of(new Skill("a_ult", "폭풍 화살", "5", "90초", "700%", "25칸을 꿰뚫는 거대한 바람 화살."))),
        List.of(
            List.of(new Skill("m_basic", "마력탄", "좌클릭", "0.6초", "100%", "마력 구체 하나를 날립니다.")),
            List.of(
                new Skill("m_seek", "유도 마탄", EQUIP, "3초", "120% x2", "가까운 적 둘을 쫓는 마탄 2발. 적이 없으면 직진합니다. 2회 충전, 연속 적중 시 추가 피해 50%."),
                new Skill("m_frost", "서리 창", EQUIP, "5초", "180%", "겨냥한 지점(최대 14칸) 위 하늘에서 얼음창이 대각선으로 떨어져 반경 2.5칸의 적에게 피해를 주고 3초간 둔화시킵니다."),
                new Skill("m_thunder", "낙뢰", EQUIP, "8초", "공격력의 60% × 최대 6회", "3초 동안 0.5초마다 8칸 이내의 적(최대 3명)에게 번개를 떨어뜨립니다."),
                new Skill("m_vortex", "마력 소용돌이", EQUIP, "9초", "40% x6", "겨냥한 지점(최대 20칸)에 3초간 소용돌이가 머뭅니다. 0.5초마다 반경 3칸의 적에게 피해를 줍니다."),
                new Skill("m_prison", "얼음 감옥", EQUIP, "7초", "120%", "20칸 이내 가장 가까운 적을 얼음 결정으로 가둡니다. 2초간 꼼짝 못 하고 피해를 입습니다."),
                new Skill("m_heal", "치유의 빛", "4", "30초", "", "자신과 6칸 이내 아군의 체력을 30% 회복합니다."),
                new Skill("m_shield", "마력 방벽", "6", "22초", "", "최대 체력의 25%만큼 보호막을 6초간 얻습니다.")),
            List.of(new Skill("m_ult", "운석 낙하", "5", "100초", "900%", "겨냥한 지점(최대 16칸)에 운석이 떨어져 반경 6칸에 피해를 주고 3초간 불태웁니다."))),
        List.of(
            List.of(),
            List.of(new Skill("c_dash", "대시 점프", "공중에서 스페이스 2번", "8초", "", "공중에서 앞으로 돌진하며 2초간 낙하 피해를 받지 않습니다.")),
            List.of()));

    private static final int W = 346, H = 212, TABW = 30, ROW = 34;
    private static final int[] COLW = {96, 120, 96};
    private static final int PANEL = 0xff10213a, PANEL2 = 0xff182f50, EDGE = 0xff3f6a9c, TEXT = 0xffe8edf4, MUTED = 0xff9cacbf;
    private static final int GOLD = 0xffffe27a;
    // loadout strip (equip slots) below the title bar
    private static final int STRIP_Y = 24, STRIP_H = 34, BOX = 28, BOX_X = 52, BOX_GAP = 8;
    private static final int LIST_Y = STRIP_Y + STRIP_H + 14;

    private int tab, left, top;
    private final int[] scroll = new int[3];
    private String selected; // picked general skill id (click), or the one being dragged
    private String dragging;
    private double dragX, dragY;

    SkillScreen(int tab) {
        super(Component.literal("스킬"));
        this.tab = tab;
    }

    static int tabFor(ItemStack held) {
        if (held.is(Items.BOW)) return 1;
        if (held.is(Items.BLAZE_ROD)) return 2;
        if (held.getItem() instanceof SwordItem) return 0;
        return 0;
    }

    static void open() {
        var client = net.minecraft.client.Minecraft.getInstance();
        if (client.player == null) return;
        client.setScreen(new SkillScreen(tabFor(client.player.getMainHandItem())));
    }

    @Override public boolean isPauseScreen() { return false; }
    // vanilla 1.21 blurs the background inside Screen.render(); our panel is drawn first, so skip it to keep the window sharp
    @Override public void renderBackground(GuiGraphics g, int mx, int my, float delta) {}

    @Override protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
    }

    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_K) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    private int colX(int c) {
        int x = left + TABW + 8;
        for (int i = 0; i < c; i++) x += COLW[i] + 6;
        return x;
    }

    private boolean classTab() { return tab < 3; }

    private boolean locked() { return CombatBar.active(); }

    private int boxX(int s) { return left + TABW + 8 + BOX_X + s * (BOX + BOX_GAP); }

    private int boxY() { return top + STRIP_Y + (STRIP_H - BOX) / 2; }

    private int boxAt(double mx, double my) {
        if (!classTab()) return -1;
        for (int s = 0; s < 3; s++)
            if (mx >= boxX(s) && mx < boxX(s) + BOX && my >= boxY() && my < boxY() + BOX) return s;
        return -1;
    }

    /** Row index (in the general-skill column) under the mouse, or -1. Only equippable rows count. */
    private int generalRowAt(double mx, double my) {
        if (!classTab()) return -1;
        int x = colX(1), w = COLW[1];
        if (mx < x || mx >= x + w - 7 || my < top + LIST_Y + 2) return -1;
        int r = (int) (my - top - LIST_Y - 2) / ROW + scroll[1];
        int viewRows = (H - LIST_Y - 6) / ROW;
        if (r - scroll[1] >= viewRows || r >= GENERAL) return -1;
        return r;
    }

    private void equip(int slot) {
        var client = net.minecraft.client.Minecraft.getInstance();
        if (selected == null || locked() || !classTab() || client.getConnection() == null) return;
        int c = tab;
        String[] cur = CombatBar.loadout(c);
        int from = Arrays.asList(cur).indexOf(selected);
        if (from == slot) return;
        if (from >= 0) { cur[from] = cur[slot]; cur[slot] = selected; } else cur[slot] = selected;
        // the server validates and answers with PIPI_LOADOUT, which updates the window and the skill bar
        client.getConnection().sendCommand("pipilife skill loadout " + TAB_CLS[c] + " " + cur[0] + " " + cur[1] + " " + cur[2]);
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        for (int i = 0; i < 4; i++) {
            int ty = top + 4 + i * 34;
            if (mx >= left && mx < left + TABW && my >= ty && my < ty + 32) { tab = i; selected = null; dragging = null; return true; }
        }
        if (button == 0 && classTab()) {
            int s = boxAt(mx, my);
            if (s >= 0) {
                if (selected != null && !locked()) { equip(s); selected = null; }
                return true;
            }
            int r = generalRowAt(mx, my);
            if (r >= 0) {
                String id = DATA.get(tab).get(1).get(r + 0).id();
                selected = id.equals(selected) ? null : id;
                if (!locked()) { dragging = id; dragX = mx; dragY = my; }
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging != null) { dragX = mx; dragY = my; return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override public boolean mouseReleased(double mx, double my, int button) {
        if (dragging != null && button == 0) {
            int s = boxAt(mx, my);
            String id = dragging;
            dragging = null;
            if (s >= 0 && !locked()) { selected = id; equip(s); selected = null; }
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        for (int c = 0; c < 3; c++) {
            int rows = DATA.get(tab).get(c).size(), viewRows = (H - LIST_Y - 6) / ROW;
            if (mx >= colX(c) && mx < colX(c) + COLW[c] && rows > viewRows) {
                scroll[c] = Math.clamp(scroll[c] - (int) Math.signum(dy), 0, rows - viewRows);
                return true;
            }
        }
        return false;
    }

    private static ResourceLocation icon(String id) {
        return ResourceLocation.fromNamespaceAndPath("pipitravel", "textures/gui/skills/" + id + ".png");
    }

    @Override public void render(GuiGraphics g, int mx, int my, float delta) {
        renderTransparentBackground(g);
        g.fill(left - 1, top - 1, left + W + 1, top + H + 1, EDGE);
        g.fill(left, top, left + W, top + H, PANEL);
        // tab strip
        g.fill(left, top, left + TABW, top + H, 0xff0b1627);
        for (int i = 0; i < 4; i++) {
            int ty = top + 4 + i * 34;
            boolean sel = i == tab;
            g.fill(left + 1, ty, left + TABW + (sel ? 3 : 0), ty + 32, sel ? 0xff2f5f94 : PANEL2);
            g.blit(icon(TAB_ICONS[i]), left + 2, ty, 0, 0, 28, 28, 28, 28);
            g.drawCenteredString(font, TAB_NAMES[i].substring(0, 1), left + 16, ty + 21, sel ? 0xffffffff : MUTED);
        }
        // title bar
        g.fill(left + TABW + 4, top + 4, left + W - 4, top + 22, PANEL2);
        g.drawString(font, "스킬 · " + TAB_NAMES[tab], left + TABW + 10, top + 9, TEXT, false);
        g.drawString(font, "K / Esc 닫기", left + W - 10 - font.width("K / Esc 닫기"), top + 9, MUTED, false);

        String[] equipped = classTab() ? CombatBar.loadout(tab) : new String[0];
        Skill hover = null;
        // loadout strip
        int sx0 = left + TABW + 4, sx1 = left + W - 4;
        g.fill(sx0, top + STRIP_Y, sx1, top + STRIP_Y + STRIP_H, PANEL2);
        if (classTab()) {
            boolean locked = locked();
            g.drawString(font, "장착", left + TABW + 10, top + STRIP_Y + 13, TEXT, false);
            for (int s = 0; s < 3; s++) {
                int bx = boxX(s), by = boxY();
                boolean over = mx >= bx && mx < bx + BOX && my >= by && my < by + BOX;
                boolean target = !locked && (selected != null || dragging != null);
                g.fill(bx - 1, by - 1, bx + BOX + 1, by + BOX + 1, target && over ? GOLD : target ? 0xff8fb4d8 : EDGE);
                g.fill(bx, by, bx + BOX, by + BOX, 0xff0b1627);
                if (s < equipped.length) {
                    g.blit(icon(equipped[s]), bx + 2, by + 2, BOX - 4, BOX - 4, 0, 0, 32, 32, 32, 32);
                    if (locked) g.fill(bx, by, bx + BOX, by + BOX, 0x80091420);
                    if (over) hover = find(equipped[s]);
                }
                g.drawString(font, Integer.toString(s + 1), bx + 3, by + 2, GOLD, true);
            }
            int hx = boxX(2) + BOX + 8;
            String hint = locked ? "전투 모드에서는 바꿀 수 없어요" : selected != null ? "장착할 슬롯을 누르세요" : "스킬을 눌러 고른 뒤 슬롯에 놓으세요 (끌어다 놓아도 돼요)";
            g.drawWordWrap(font, Component.literal(hint), hx, top + STRIP_Y + 6, sx1 - hx - 4, locked ? 0xffff8a7a : MUTED);
        } else {
            g.drawString(font, "공통 스킬은 장착하지 않고 항상 사용할 수 있어요.", left + TABW + 10, top + STRIP_Y + 13, MUTED, false);
        }

        for (int c = 0; c < 3; c++) {
            int x = colX(c), w = COLW[c];
            int headY = top + LIST_Y - 12;
            g.fill(x, headY, x + w, headY + 11, 0xff24456f);
            g.drawCenteredString(font, COLUMNS[c], x + w / 2, headY + 2, TEXT);
            g.fill(x, top + LIST_Y, x + w, top + H - 4, PANEL2);
            List<Skill> rows = DATA.get(tab).get(c);
            int viewRows = (H - LIST_Y - 6) / ROW;
            for (int r = 0; r < viewRows && r + scroll[c] < rows.size(); r++) {
                int index = r + scroll[c];
                Skill s = rows.get(index);
                int ry = top + LIST_Y + 2 + r * ROW;
                boolean general = c == 1 && classTab() && index < GENERAL;
                int slot = general ? Arrays.asList(equipped).indexOf(s.id()) : -1;
                boolean over = mx >= x && mx < x + w - 7 && my >= ry && my < ry + ROW - 1;
                boolean picked = general && s.id().equals(selected);
                if (picked) g.fill(x + 1, ry - 1, x + w - 7, ry + ROW - 2, 0xff3a6a2a);
                else if (over) g.fill(x + 1, ry - 1, x + w - 7, ry + ROW - 2, 0xff2c5280);
                if (over) hover = s;
                g.fill(x + 2, ry, x + 36, ry + 33, picked ? GOLD : slot >= 0 ? 0xff7bdc8a : EDGE);
                g.blit(icon(s.id()), x + 3, ry + 1, 0, 0, 32, 32, 32, 32);
                g.drawString(font, s.name(), x + 40, ry + 6, TEXT, false);
                String key = s.key().startsWith("공") ? "[더블 스페이스]" : general ? (slot >= 0 ? "장착 중" : "미장착") : "[" + s.key() + "]";
                g.drawString(font, key, x + 40, ry + 19, general && slot >= 0 ? 0xff7bdc8a : MUTED, false);
                if (slot >= 0) {
                    String badge = "[" + (slot + 1) + "]";
                    g.drawString(font, badge, x + w - 9 - font.width(badge), ry + 6, GOLD, true);
                }
            }
            // scroll bar
            int bx = x + w - 5, by0 = top + LIST_Y + 2, by1 = top + H - 6;
            g.fill(bx, by0, bx + 3, by1, 0xff0b1627);
            int total = Math.max(rows.size(), 1);
            int thumbH = Math.min(by1 - by0, Math.max(10, (by1 - by0) * Math.min(viewRows, total) / total));
            int thumbY = rows.size() <= viewRows ? by0 : by0 + (by1 - by0 - thumbH) * scroll[c] / (rows.size() - viewRows);
            g.fill(bx, thumbY, bx + 3, thumbY + thumbH, 0xff6fa3d8);
        }
        super.render(g, mx, my, delta);
        if (dragging != null) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 400);
            g.blit(icon(dragging), (int) dragX - 12, (int) dragY - 12, 24, 24, 0, 0, 32, 32, 32, 32);
            g.pose().popPose();
        } else if (hover != null) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal(hover.name()));
            if (hover.key().equals(EQUIP)) {
                int slot = classTab() ? Arrays.asList(equipped).indexOf(hover.id()) : -1;
                tip.add(Component.literal(slot >= 0 ? "키: " + (slot + 1) + " (장착 중)" : "키: 장착하면 1~3번 키"));
            } else tip.add(Component.literal("키: " + (hover.key().startsWith("공") ? "공중에서 스페이스 두 번" : hover.key())));
            tip.add(Component.literal("재사용 대기시간: " + hover.cooldown()));
            if (!hover.coef().isEmpty()) tip.add(Component.literal("공격력의 " + hover.coef()));
            tip.add(Component.literal(hover.effect()));
            g.renderComponentTooltip(font, tip, mx, my);
        }
    }

    private Skill find(String id) {
        for (var column : DATA.get(tab)) for (Skill s : column) if (s.id().equals(id)) return s;
        return null;
    }
}
