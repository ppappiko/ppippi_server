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
    /** Number of equippable general skills at the top of column 1 (then 3 buffs to pick from, then the fixed shield). */
    private static final int GENERAL = 6;
    private static final String EQUIP = "-"; // key of an equippable skill: it uses whichever key 1-3 it is equipped in

    // per tab: [0] basic, [1] skills (6 equippable general, 3 buffs (pick 1), shield) or dash, [2] ultimates (pick 1)
    private static final List<List<List<Skill>>> DATA = List.of(
        List.of(
            List.of(new Skill("w_basic", "가르기", "좌클릭", "0.5초", "100%", "전방 3.6칸 부채꼴로 베어 적을 공격합니다.")),
            List.of(
                new Skill("w_rush", "돌진 베기", EQUIP, "4초", "180%", "전방으로 약 8칸 대쉬하며 지나친 모든 적을 벱니다. 2회 충전, 연속 적중 시 추가 피해 50%."),
                new Skill("w_whirl", "회오리 베기", EQUIP, "5초", "160%", "반경 5.25칸을 360도로 휩쓸어 벱니다."),
                new Skill("w_split", "대지 가르기", EQUIP, "6초", "220%", "힘을 모아 거대한 검기를 날립니다. 전방 14칸의 적을 꿰뚫습니다."),
                new Skill("w_heavy", "일도양단", EQUIP, "8초", "320%", "1초간 힘을 모은 뒤(이동 둔화) 전방 5칸 140도 부채꼴을 크게 한 번 내려벱니다."),
                new Skill("w_impale", "대검 꽂기", EQUIP, "9초", "120% + 40% × 4", "바닥에 거대한 검을 꽂아 반경 4칸에 피해를 줍니다. 이후 2초간 0.5초마다 지속 피해와 둔화를 줍니다."),
                new Skill("w_iai", "일섬", EQUIP, "14초", "400%", "3초간 칼을 갈무리하고(이동 불가), 바라보는 방향으로 10칸 순간 돌진하며 경로 반경 2.5칸의 적을 벱니다."),
                new Skill("w_heal", "투지", "4", "25초", "", "최대 체력의 25%를 회복하고 5초간 받는 피해가 10% 더 줄어듭니다."),
                new Skill("w_rage", "전투 함성", "4", "30초", "", "최대 체력의 25%를 회복하고 10초간 공격력이 20% 증가합니다."),
                new Skill("w_fort", "불굴", "4", "45초", "", "10초간 모든 피해를 받지 않지만 움직일 수 없습니다. 시점 회전은 가능하며 도중에 해제할 수 없습니다."),
                new Skill("w_shield", "강철 의지", "6", "20초", "", "최대 체력의 20%만큼 보호막을 6초간 얻습니다. 보호막이 체력보다 먼저 피해를 흡수합니다.")),
            List.of(
                new Skill("w_ult", "검기 난사", "5", "90초", "40% × 20회", "5초 동안 주변 반경 5.25칸에 검기를 난사해 범위 안의 모든 적을 연속으로 벱니다."),
                new Skill("w_tiger", "맹호참", "5", "90초", "300% × 3회", "호랑이 형상이 나타나 전방 8칸 160도 부채꼴을 큰 발톱으로 3번 휩쓸습니다."))),
        List.of(
            List.of(new Skill("a_basic", "속사", "좌클릭", "0.5초", "100%", "화살 한 발을 빠르게 쏩니다.")),
            List.of(
                new Skill("a_triple", "삼연사", EQUIP, "3초", "90% × 3", "화살 3발을 연달아 쏩니다. 2회 충전, 연속 적중 시 추가 피해 50%."),
                new Skill("a_rain", "화살비", EQUIP, "7초", "50% × 6", "겨냥한 지점(반경 4칸)에 3초간 화살이 쏟아집니다."),
                new Skill("a_pierce", "폭죽 화살", EQUIP, "6초", "260%", "폭죽을 단 빠른 화살이 전방 24칸의 적을 꿰뚫고 끝에서 폭죽처럼 터집니다."),
                new Skill("a_spread", "부채살", EQUIP, "4초", "100% × 3", "화살 3발을 동시에 부채꼴(좌우 12도)로 발사합니다."),
                new Skill("a_shotgun", "산탄 사격", EQUIP, "12초", "25% × 20", "2초간 활을 당긴 뒤 화살 20발을 원뿔 모양으로 한꺼번에 쏩니다. 발사 반동으로 뒤로 약 5칸 밀려납니다."),
                new Skill("a_gale", "질풍 화살", EQUIP, "9초", "380%", "바람을 두른 큰 화살이 전방 20칸의 적을 꿰뚫습니다."),
                new Skill("a_heal", "숲의 숨결", "4", "25초", "", "최대 체력의 25%를 회복하고 6초간 이동 속도가 20% 증가합니다."),
                new Skill("a_focus", "매의 눈", "4", "30초", "", "최대 체력의 25%를 회복하고 10초간 공격력이 20% 증가합니다."),
                new Skill("a_spirit", "바람 정령", "4", "45초", "", "10초간 활 모양 바람 정령이 곁을 따라다니며 1초마다 14칸 이내 가장 가까운 적에게 바람 화살(60%)을 쏩니다."),
                new Skill("a_shield", "바람 장막", "6", "18초", "", "최대 체력의 15%만큼 보호막을 6초간 얻고, 3초간 이동 속도가 증가합니다.")),
            List.of(
                new Skill("a_ult", "화살 폭풍", "5", "90초", "25% × 40", "바람 활 5개가 뒤에 떠올라 5초간 0.25초마다 2발씩 바라보는 방향으로 화살을 연사합니다."),
                new Skill("a_wolf", "늑대 정령", "5", "90초", "150% (관통)", "8초간 평타가 늑대 정령이 되어 18칸을 꿰뚫고 달려갑니다. 시작할 때 늑대 울음소리가 납니다."))),
        List.of(
            List.of(new Skill("m_basic", "마력탄", "좌클릭", "0.6초", "100%", "마력 구체 하나를 날립니다.")),
            List.of(
                new Skill("m_seek", "유도 마탄", EQUIP, "3초", "120% × 2", "가까운 적 둘을 쫓는 마탄 2발. 적이 없으면 직진합니다. 2회 충전, 연속 적중 시 추가 피해 50%."),
                new Skill("m_frost", "서리 창", EQUIP, "5초", "180%", "겨냥한 지점(최대 14칸) 위 하늘에서 얼음창이 떨어져 반경 2.5칸의 적에게 피해를 주고 3초간 둔화시킵니다."),
                new Skill("m_thunder", "뇌운", EQUIP, "9초", "60% × 8회", "4초 동안 0.5초마다 8칸 이내의 적(최대 3명)에게 번개를 떨어뜨립니다."),
                new Skill("m_wave", "마력 파동", EQUIP, "5초", "200%", "폭 3칸의 넓은 마력 파동이 전방 14칸을 나아가며 적을 꿰뚫습니다."),
                new Skill("m_hammer", "마력 망치", EQUIP, "10초", "380%", "조준 지점(최대 16칸)에 거대한 마력 망치를 내려찍어 반경 3.5칸에 피해를 주고 1초간 기절시킵니다."),
                new Skill("m_bow", "마력 대궁", EQUIP, "14초", "550%", "3초간 거대한 마력 활을 당긴 뒤 30칸을 꿰뚫는 마력 화살을 쏩니다."),
                new Skill("m_heal", "치유의 빛", "4", "30초", "", "최대 체력의 25%를 회복하고, 8칸 이내 다른 플레이어는 최대 체력의 10%를 회복합니다."),
                new Skill("m_focus", "마력 집중", "4", "30초", "", "최대 체력의 35%를 회복하고 10초간 공격력이 20% 증가합니다."),
                new Skill("m_reset", "시간 역행", "4", "90초", "", "장착한 일반 스킬 3종과 보호막의 재사용 대기시간을 초기화하고 10초간 공격력이 20% 증가합니다. 궁극기·대시는 제외."),
                new Skill("m_shield", "마력 방벽", "6", "22초", "", "최대 체력의 25%만큼 보호막을 6초간 얻습니다.")),
            List.of(
                new Skill("m_cannon", "마력포", "5", "100초", "50% × 20회", "3초간 힘을 모은 뒤 5초 동안 전방 20칸에 굵은 에너지 빔을 쏩니다. 빔은 시선을 따라갑니다."),
                new Skill("m_dragon", "용의 숨결", "5", "100초", "45% × 20회", "3초 준비 후 용 머리가 나타나 5초간 전방 10칸 60도 부채꼴에 브레스를 뿜어 피해와 화상을 줍니다."))),
        List.of(
            List.of(),
            List.of(new Skill("c_dash", "대시 점프", "공중에서 스페이스 2번", "8초", "", "공중에서 앞으로 돌진하며 2초간 낙하 피해를 받지 않습니다.")),
            List.of()));

    private static final int W = 346, H = 238, TABW = 30, ROW = 34;
    private static final int[] COLW = {96, 120, 96};
    private static final int PANEL = 0xff10213a, PANEL2 = 0xff182f50, EDGE = 0xff3f6a9c, TEXT = 0xffe8edf4, MUTED = 0xff9cacbf;
    private static final int GOLD = 0xffffe27a;
    // loadout strip (equip slots) below the title bar
    private static final int STRIP_Y = 24, STRIP_H = 34, BOX = 26, BOX_X = 34, BOX_GAP = 4;
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
        for (int s = 0; s < 5; s++)
            if (mx >= boxX(s) && mx < boxX(s) + BOX && my >= boxY() && my < boxY() + BOX) return s;
        return -1;
    }

    /** Kind of a skill id: 0 general, 1 buff, 2 ultimate, -1 neither (basic / heal-less fixed skills). Boxes 0-2 take 0, box 3 takes 1, box 4 takes 2. */
    private static int kindOf(String id) {
        for (int c = 0; c < 3; c++) {
            if (Arrays.asList(CombatBar.GENERAL[c]).contains(id)) return 0;
            if (Arrays.asList(CombatBar.BUFF[c]).contains(id)) return 1;
            if (Arrays.asList(CombatBar.ULT[c]).contains(id)) return 2;
        }
        return -1;
    }

    private static boolean boxTakes(int box, int kind) { return kind >= 0 && (box < 3 ? kind == 0 : box - 2 == kind); }

    /** Row index under the mouse in column c (1 = skills, 2 = ultimates) for rows that can be equipped, or -1. */
    private int rowAt(int c, double mx, double my) {
        if (!classTab()) return -1;
        int x = colX(c), w = COLW[c];
        if (mx < x || mx >= x + w - 7 || my < top + LIST_Y + 2) return -1;
        int r = (int) (my - top - LIST_Y - 2) / ROW + scroll[c];
        int viewRows = (H - LIST_Y - 6) / ROW;
        if (r - scroll[c] >= viewRows || r >= DATA.get(tab).get(c).size()) return -1;
        return kindOf(DATA.get(tab).get(c).get(r).id()) >= 0 ? r : -1;
    }

    /** Id of the equippable skill row under the mouse (any column), or null. */
    private String idAt(double mx, double my) {
        for (int c = 1; c < 3; c++) {
            int r = rowAt(c, mx, my);
            if (r >= 0) return DATA.get(tab).get(c).get(r).id();
        }
        return null;
    }

    private void equip(String id, int slot) {
        var client = net.minecraft.client.Minecraft.getInstance();
        if (id == null || locked() || !classTab() || client.getConnection() == null) return;
        int kind = kindOf(id);
        if (!boxTakes(slot, kind)) return;
        int c = tab;
        String[] cur = CombatBar.loadout(c);
        if (kind == 0) {
            int from = Arrays.asList(cur).subList(0, 3).indexOf(id);
            if (from == slot) return;
            if (from >= 0) { cur[from] = cur[slot]; cur[slot] = id; } else cur[slot] = id;
        } else {
            if (cur[slot].equals(id)) return;
            cur[slot] = id;
        }
        // the server validates and answers with PIPI_LOADOUT, which updates the window and the skill bar
        client.getConnection().sendCommand("pipilife skill loadout " + TAB_CLS[c] + " " + String.join(" ", cur));
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        for (int i = 0; i < 4; i++) {
            int ty = top + 4 + i * 34;
            if (mx >= left && mx < left + TABW && my >= ty && my < ty + 32) { tab = i; selected = null; dragging = null; return true; }
        }
        if (button == 0 && classTab()) {
            int s = boxAt(mx, my);
            if (s >= 0) {
                if (selected != null && !locked()) { equip(selected, s); selected = null; }
                return true;
            }
            String id = idAt(mx, my);
            if (id != null) {
                if (kindOf(id) == 0) selected = id.equals(selected) ? null : id;
                else selected = null;
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
            if (locked()) return true;
            int kind = kindOf(id);
            if (kind == 0) {
                if (s >= 0) { equip(id, s); selected = null; }
            } else if (s >= 0) equip(id, s);                // buff / ultimate dropped on its box
            else if (id.equals(idAt(mx, my))) equip(id, kind + 2); // buff / ultimate clicked: single choice, equip directly
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
            int want = dragging != null ? kindOf(dragging) : selected != null ? 0 : -1;
            for (int s = 0; s < 5; s++) {
                int bx = boxX(s), by = boxY();
                boolean over = mx >= bx && mx < bx + BOX && my >= by && my < by + BOX;
                boolean target = !locked && boxTakes(s, want);
                g.fill(bx - 1, by - 1, bx + BOX + 1, by + BOX + 1, target && over ? GOLD : target ? 0xff8fb4d8 : EDGE);
                g.fill(bx, by, bx + BOX, by + BOX, 0xff0b1627);
                if (s < equipped.length) {
                    g.blit(icon(equipped[s]), bx + 2, by + 2, BOX - 4, BOX - 4, 0, 0, 32, 32, 32, 32);
                    if (locked) g.fill(bx, by, bx + BOX, by + BOX, 0x80091420);
                    if (over) hover = find(equipped[s]);
                }
                g.drawString(font, Integer.toString(s + 1), bx + 3, by + 2, GOLD, true);
            }
            int hx = boxX(4) + BOX + 6;
            String hint = locked ? "전투 모드에서는 바꿀 수 없어요" : selected != null ? "장착할 슬롯(1~3)을 누르세요" : "일반 스킬은 눌러 고르거나 끌어서 1~3에, 버프·궁극기는 누르면 장착돼요";
            g.drawWordWrap(font, Component.literal(hint), hx, top + STRIP_Y + 3, sx1 - hx - 4, locked ? 0xffff8a7a : MUTED);
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
                int kind = classTab() ? kindOf(s.id()) : -1;
                boolean general = kind == 0, single = kind > 0; // single = buff / ultimate: exactly one is equipped
                int slot = general ? Arrays.asList(equipped).subList(0, 3).indexOf(s.id()) : single ? (s.id().equals(equipped[kind + 2]) ? kind + 2 : -1) : -1;
                boolean dim = single && slot < 0;
                boolean over = mx >= x && mx < x + w - 7 && my >= ry && my < ry + ROW - 1;
                boolean picked = general && s.id().equals(selected);
                if (picked) g.fill(x + 1, ry - 1, x + w - 7, ry + ROW - 2, 0xff3a6a2a);
                else if (single && slot >= 0) g.fill(x + 1, ry - 1, x + w - 7, ry + ROW - 2, 0xff25502f);
                else if (over) g.fill(x + 1, ry - 1, x + w - 7, ry + ROW - 2, 0xff2c5280);
                if (over) hover = s;
                g.fill(x + 2, ry, x + 36, ry + 33, picked ? GOLD : slot >= 0 ? (single ? GOLD : 0xff7bdc8a) : EDGE);
                g.blit(icon(s.id()), x + 3, ry + 1, 0, 0, 32, 32, 32, 32);
                if (dim) g.fill(x + 2, ry, x + 36, ry + 33, 0xa0182f50); // not the equipped one: grayed out
                g.drawString(font, s.name(), x + 40, ry + 6, dim ? 0xff6f7d90 : TEXT, false);
                String key = s.key().startsWith("공") ? "[더블 스페이스]" : general || single ? (slot >= 0 ? "장착중" : "미장착") : "[" + s.key() + "]";
                g.drawString(font, key, x + 40, ry + 19, (general || single) && slot >= 0 ? 0xff7bdc8a : dim ? 0xff55627a : MUTED, false);
                if (general && slot >= 0) {
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
            int hk = classTab() ? kindOf(hover.id()) : -1;
            if (hover.key().equals(EQUIP)) {
                int slot = classTab() ? Arrays.asList(equipped).subList(0, 3).indexOf(hover.id()) : -1;
                tip.add(Component.literal(slot >= 0 ? "키: " + (slot + 1) + " (장착 중)" : "키: 장착하면 1~3번 키"));
            } else if (hk > 0) {
                tip.add(Component.literal("키: " + hover.key() + (hover.id().equals(equipped[hk + 2]) ? " (장착중)" : " (클릭해서 장착)")));
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
