package dev.pipi.travel;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;

/** Custom equipment spec card (replaces the vanilla tooltip of PipiLife gear). Data comes from the item's name, lore and custom_data. */
final class GearCard {
    private static final Pattern STAT = Pattern.compile("^(공격력|방어력|체력|힘|민첩|지능|행운) \\+([0-9.,]+)$");
    private static final Pattern ILVL = Pattern.compile("^아이템레벨 (\\d+)$");
    private static final Pattern SLOTS = Pattern.compile("^잠재 슬롯 (\\d+)개$");
    private static final Pattern GRADE = Pattern.compile("^인챈트 등급 \\[(.+)]$");
    private static final Pattern LINE = Pattern.compile("^\\[(레어|에픽|유니크|레전더리)] (.+)$");
    private static final Pattern NAME = Pattern.compile("^(.*?)\\s*\\+(\\d+)$");
    static final String[] GRADES = {"레어", "에픽", "유니크", "레전더리"};
    static final int[] GRADE_COL = {0xff6f8cff, 0xffb46bff, 0xffffb02e, 0xff8cff3c};
    private static final int W = 176, MAIN = 0xfff2e04a, GREY = 0xff9aa0aa, TEXT = 0xffe8ebf0;

    record Row(String label, String value) {}
    record Ench(String text, int color) {}
    record Data(String name, int upgrade, int ilvl, String slot, int grade, Row main, List<Row> subs,
                        int slots, List<Ench> enchants, String owner) {}

    private static List<String> lore(ItemStack stack) {
        List<String> out = new ArrayList<>();
        for (Component c : stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines())
            out.add(ChatFormatting.stripFormatting(c.getString()).trim());
        return out;
    }

    /** True for PipiLife gear (has the server-written 아이템레벨 / 잠재 슬롯 lore). */
    static boolean isCard(ItemStack stack) {
        if (stack.isEmpty()) return false;
        for (String l : lore(stack)) if (ILVL.matcher(l).matches() || SLOTS.matcher(l).matches()) return true;
        return false;
    }

    static String slotLabel(ItemStack stack) {
        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (id.endsWith("_helmet")) return "투구";
        if (id.endsWith("_chestplate")) return "상의";
        if (id.endsWith("_leggings")) return "하의";
        if (id.endsWith("_boots")) return "신발";
        if (id.endsWith("_sword")) return "검";
        if (id.equals("bow")) return "활";
        if (id.equals("blaze_rod")) return "지팡이";
        if (id.endsWith("_pickaxe")) return "곡괭이";
        return "장비";
    }

    private static String owner(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return "";
        CompoundTag tag = cd.copyTag();
        CompoundTag pub = tag.contains("PublicBukkitValues") ? tag.getCompound("PublicBukkitValues") : tag;
        return pub.contains("pipilife:owner") ? pub.getString("pipilife:owner") : "";
    }

    private static int gradeIndex(String name) {
        for (int i = 0; i < 4; i++) if (GRADES[i].equals(name)) return i;
        return -1;
    }

    static Data parse(ItemStack stack) {
        String name = ChatFormatting.stripFormatting(stack.getHoverName().getString());
        int upgrade = 0;
        Matcher nm = NAME.matcher(name);
        if (nm.matches()) { name = nm.group(1); upgrade = Integer.parseInt(nm.group(2)); }
        int ilvl = 0, slots = 0, grade = -1;
        Row main = null;
        List<Row> subs = new ArrayList<>();
        List<Ench> ench = new ArrayList<>();
        for (String l : lore(stack)) {
            Matcher m;
            if ((m = ILVL.matcher(l)).matches()) ilvl = Integer.parseInt(m.group(1));
            else if ((m = SLOTS.matcher(l)).matches()) slots = Integer.parseInt(m.group(1));
            else if ((m = GRADE.matcher(l)).matches()) grade = gradeIndex(m.group(1));
            else if ((m = STAT.matcher(l)).matches()) {
                Row r = new Row(m.group(1), m.group(2));
                if (m.group(1).equals("공격력") || m.group(1).equals("방어력")) main = r; else subs.add(r);
            } else if ((m = LINE.matcher(l)).matches()) {
                ench.add(new Ench(m.group(2), GRADE_COL[Math.max(0, gradeIndex(m.group(1)))]));
            }
        }
        return new Data(name, upgrade, ilvl, slotLabel(stack), grade, main, subs, slots, ench, owner(stack));
    }

    static int subColor(String label) {
        return switch (label) {
            case "체력" -> 0xffff6b6b;
            case "지능" -> 0xffb98cff;
            case "힘" -> 0xffff9a4a;
            case "민첩" -> 0xff6fd4ff;
            default -> 0xffffffff;
        };
    }

    static void pre(RenderTooltipEvent.Pre event) {
        ItemStack stack = event.getItemStack();
        if (!isCard(stack)) return;
        event.setCanceled(true);
        draw(event.getGraphics(), event.getFont(), stack, event.getX(), event.getY(), event.getScreenWidth(), event.getScreenHeight());
    }

    static void draw(GuiGraphics g, Font font, ItemStack stack, int mx, int my, int sw, int sh) {
        Data d = parse(stack);
        int accent = d.grade >= 0 ? GRADE_COL[d.grade] : 0xff66bb53;
        String gradeName = d.grade >= 0 ? GRADES[d.grade] : "일반";
        int y = 46;
        int statsTop = y, statsH = 0;
        if (d.main != null || !d.subs.isEmpty()) {
            statsH = 6 + (d.main != null ? 24 : 0) + d.subs.size() * 14 + 2;
            y += statsH + 8;
        }
        int enchHeader = -1, enchTop = -1, enchH = 0;
        int rows = Math.max(d.enchants.size(), d.slots);
        if (d.slots > 0 || !d.enchants.isEmpty()) {
            enchHeader = y; y += 14; enchTop = y; enchH = 8 + rows * 14; y += enchH + 8;
        }
        y += 4;
        int footerY = y;
        int H = y + 18;
        float scale = Math.min(1f, Math.min((sw - 6f) / W, (sh - 6f) / H));
        int pw = Math.round(W * scale), ph = Math.round(H * scale);
        int px = mx + 12, py = my - 12;
        if (px + pw > sw - 3) px = mx - 12 - pw;
        if (px < 3) px = 3;
        if (py + ph > sh - 3) py = sh - 3 - ph;
        if (py < 3) py = 3;
        g.pose().pushPose();
        g.pose().translate(px, py, 400);
        g.pose().scale(scale, scale, 1f);
        g.fill(0, 0, W, H, 0xff2d3138);
        g.fillGradient(1, 1, W - 1, H - 1, 0xf2151a1d, 0xf20b0d11);
        g.fill(0, 0, W, 3, accent);
        g.fillGradient(1, 3, W - 1, 46, (accent & 0x00ffffff) | 0x30000000, 0x00000000);
        g.fill(8, 9, 36, 37, accent);
        g.fill(9, 10, 35, 36, 0xff0a0c0f);
        g.renderItem(stack, 14, 15);
        g.pose().pushPose();
        g.pose().translate(44, 10, 0);
        g.pose().scale(1.15f, 1.15f, 1f);
        g.drawString(font, d.name + (d.upgrade > 0 ? " +" + d.upgrade : ""), 0, 0, accent, true);
        g.pose().popPose();
        g.drawString(font, gradeName, 44, 24, accent, false);
        g.drawString(font, d.slot, 44, 35, TEXT, false);
        if (d.ilvl > 0) g.drawString(font, "Lv." + d.ilvl, 44 + font.width(d.slot) + 10, 35, GREY, false);
        if (statsH > 0) {
            g.fill(8, statsTop, W - 8, statsTop + statsH, 0xff181b20);
            g.fill(8, statsTop, 10, statsTop + statsH, MAIN);
            int sy = statsTop + 6;
            if (d.main != null) {
                g.drawString(font, d.main.label, 18, sy + 5, TEXT, false);
                g.pose().pushPose();
                g.pose().scale(1.5f, 1.5f, 1f);
                String v = d.main.value;
                g.drawString(font, v, (int) ((W - 18) / 1.5f) - font.width(v), (int) ((sy + 2) / 1.5f), MAIN, false);
                g.pose().popPose();
                sy += 24;
                g.fill(16, sy - 4, W - 16, sy - 3, 0xff343841);
            }
            for (Row r : d.subs) {
                g.drawString(font, r.label, 18, sy, GREY, false);
                String v = "+" + r.value;
                g.drawString(font, v, W - 18 - font.width(v), sy, subColor(r.label), false);
                sy += 14;
            }
        }
        if (enchHeader >= 0) {
            g.drawString(font, "인챈트", 10, enchHeader + 2, 0xffb98cff, false);
            if (d.grade >= 0) {
                String t = "[" + GRADES[d.grade] + "]";
                g.drawString(font, t, W - 10 - font.width(t), enchHeader + 2, GRADE_COL[d.grade], false);
            }
            g.fill(8, enchTop, W - 8, enchTop + enchH, 0xff181b20);
            g.fill(8, enchTop, 10, enchTop + enchH, 0xff8a5cd6);
            int ey = enchTop + 6;
            for (Ench e : d.enchants) { g.drawString(font, "◆ " + e.text, 16, ey, e.color, false); ey += 14; }
            for (int i = d.enchants.size(); i < d.slots; i++) { g.drawString(font, "◇ 빈 인챈트 슬롯", 16, ey, 0xff6a707a, false); ey += 14; }
        }
        g.fill(8, footerY - 6, W - 8, footerY - 5, 0xff343841);
        g.drawString(font, "소유자: " + (d.owner.isEmpty() ? "-" : d.owner), 10, footerY + 2, GREY, false);
        g.pose().popPose();
    }
}
