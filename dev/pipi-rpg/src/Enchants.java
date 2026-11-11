package dev.pipi.life;

import java.math.BigDecimal;
import java.util.*;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Enchant (cube + ability-lock) data model.
 * PDC (namespace pipilife): smith_lines (int slot count), smith_grade (int 0..3 item grade),
 * smith_enchant (string "optId:grade,optId:grade,..."; one entry per filled line, absent = never enchanted).
 * Legacy smith_rolls is converted on read (see legacy()).
 */
final class Enchants {
    private Enchants() {}

    static final String[] GRADES = {"레어", "에픽", "유니크", "레전더리"};
    static final String[] COLORS = {"§9", "§5", "§6", "§a"};
    /** chance (percent) to grade up when rolling, by current grade. */
    static final double[] GRADE_UP = {15, 5, 3, 0};
    static final int[] BASE_COST = {1_000, 5_000, 50_000, 100_000};

    /** kind: logic key. pct: percent option (value shown with %, +3%p per grade); otherwise doubles per grade. */
    record Opt(int id, String kind, String label, double base, boolean pct) {}

    static final List<Opt> PICK = List.of(
        new Opt(101, "core", "하급 코어 획득 확률 증가", 1, true),
        new Opt(102, "ore", "하급 광물 획득 증가", .4, false),
        new Opt(103, "reach", "길이 증가", .02, false),
        new Opt(104, "glow", "반짝임", 4, false),
        new Opt(105, "speed", "채굴 속도 증가", 2, false),
        new Opt(106, "dura", "내구도 증가", 1, false));

    static final List<Opt> GEAR = List.of(
        new Opt(201, "move%", "이동속도 증가", 3, true),
        new Opt(202, "move", "이동속도 증가", 20, false),
        new Opt(203, "def%", "방어력 증가", 3, true),
        new Opt(204, "def", "방어력 증가", 20, false),
        new Opt(205, "luck", "행운 증가", 20, false),
        new Opt(206, "str", "힘 증가", 20, false),
        new Opt(207, "dex", "민첩 증가", 20, false),
        new Opt(208, "int", "지능 증가", 20, false),
        new Opt(209, "str%", "힘 증가", 3, true),
        new Opt(210, "dex%", "민첩 증가", 3, true),
        new Opt(211, "int%", "지능 증가", 3, true),
        new Opt(212, "crit%", "치명타 확률 증가", 3, true),
        new Opt(213, "hp", "체력 증가", 20, false),
        new Opt(214, "hp%", "체력 증가", 3, true),
        new Opt(215, "atk%", "공격력 증가", 3, true),
        new Opt(216, "atk", "공격력 증가", 10, false),
        new Opt(217, "boss%", "보스 몬스터 공격 시 데미지 증가", 3, true),
        new Opt(218, "dmg%", "데미지 증가", 3, true),
        new Opt(219, "crit%", "치명타 확률 증가", 5, true),
        new Opt(220, "all%", "올스탯 증가", 3, true),
        new Opt(221, "all", "올스탯 증가", 20, false),
        new Opt(222, "all%", "올스탯 증가", 1, true),
        new Opt(223, "all", "올스탯 증가", 10, false));

    /** Weapon option ids (공격력/스탯/데미지 계열). */
    static final int[] WEAPON_IDS = {215, 216, 208, 206, 207, 211, 209, 210, 217, 218, 219, 220, 221};
    /** Armor option ids (이동속도/방어력/체력/스탯 계열). */
    static final int[] ARMOR_IDS = {201, 202, 203, 204, 205, 208, 206, 207, 211, 209, 210, 212, 213, 214, 222, 223};

    record Line(int opt, int grade) {}

    static Opt byId(int id) {
        for (Opt o : PICK) if (o.id() == id) return o;
        for (Opt o : GEAR) if (o.id() == id) return o;
        return null;
    }

    static boolean weaponItem(ItemStack item) { return CombatStats.weapon(item); }

    static boolean pickaxe(ItemStack item) { return item != null && item.getType().name().endsWith("_PICKAXE"); }

    /** Value of an option at a grade. */
    static double value(Opt o, int grade) {
        grade = Math.clamp(grade, 0, 3);
        double v = o.kind().equals("core") ? o.base() + grade // core: 1% at 레어, +1%p per grade
            : o.pct() ? o.base() + 3.0 * grade : o.base() * (1 << grade);
        return Math.round(v * 10000) / 10000.0;
    }

    static String fmt(double v) {
        return BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();
    }

    static String describe(Opt o, int grade) {
        return o.label() + " +" + fmt(value(o, grade)) + (o.pct() ? "%" : "");
    }

    /** Colored lore line: "[등급] 옵션 +값". */
    static String loreLine(Line l) {
        Opt o = byId(l.opt());
        int g = Math.clamp(l.grade(), 0, 3);
        return COLORS[g] + "[" + GRADES[g] + "] " + (o == null ? "?" : describe(o, g));
    }

    static List<Opt> pool(ItemStack item, int grade) {
        boolean pickaxe = pickaxe(item);
        List<Opt> out = new ArrayList<>();
        if (pickaxe) out.addAll(PICK);
        else for (int id : weaponItem(item) ? WEAPON_IDS : ARMOR_IDS) out.add(byId(id));
        if (pickaxe && grade >= 2) out.removeIf(o -> o.kind().equals("core")); // core option: 레어/에픽 only for now
        return out;
    }

    // ---- PDC ----
    private static NamespacedKey key(String s) { return NamespacedKey.fromString("pipilife:smith_" + s); }

    static int grade(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        return Math.clamp(item.getItemMeta().getPersistentDataContainer().getOrDefault(key("grade"), PersistentDataType.INTEGER, 0), 0, 3);
    }

    static boolean enchanted(ItemStack item) { return !read(item).isEmpty(); }

    static List<Line> read(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return List.of();
        var pdc = item.getItemMeta().getPersistentDataContainer();
        String s = pdc.get(key("enchant"), PersistentDataType.STRING);
        if (s == null) {
            String old = pdc.get(key("rolls"), PersistentDataType.STRING);
            return old == null ? List.of() : legacy(old, pickaxe(item), CombatStats.armor(item));
        }
        return parse(s);
    }

    static List<Line> parse(String s) {
        List<Line> out = new ArrayList<>();
        if (s == null || s.isBlank()) return out;
        for (String part : s.split(",")) {
            String[] p = part.split(":");
            try { out.add(new Line(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()))); }
            catch (RuntimeException ignored) {}
        }
        return out;
    }

    static String encode(List<Line> lines) {
        StringBuilder b = new StringBuilder();
        for (Line l : lines) { if (b.length() > 0) b.append(','); b.append(l.opt()).append(':').append(l.grade()); }
        return b.toString();
    }

    static void write(ItemMeta meta, List<Line> lines, int grade) {
        var pdc = meta.getPersistentDataContainer();
        pdc.set(key("enchant"), PersistentDataType.STRING, encode(lines));
        pdc.set(key("grade"), PersistentDataType.INTEGER, Math.clamp(grade, 0, 3));
        pdc.remove(key("rolls"));
    }

    /**
     * Migration of old smith_rolls (comma ints) to new lines, all at 레어 (grade 0).
     * Pickaxe roll 6..17: (roll-6)/2 = old option index -> same PICK option (core 1% -> 3%).
     * Gear: roll/2 = 0 attack/defense -> 힘 +20 (weapon) / 방어력 +20 (armor); 1 max health -> 체력 +20;
     * 2 attack-speed/move-speed -> 이동속도 +3%; 18/19 buff duration -> 지능 +20; 20/21 cooldown -> 행운 +20.
     */
    static List<Line> legacy(String rolls, boolean pickaxe, boolean armor) {
        List<Line> out = new ArrayList<>();
        for (String r : rolls.split(",")) {
            int roll;
            try { roll = Integer.parseInt(r.trim()); } catch (NumberFormatException e) { continue; }
            int opt;
            if (pickaxe) {
                if (roll < 6 || roll >= 18) continue;
                opt = 101 + (roll - 6) / 2;
            } else if (roll >= 20 && roll <= 21) opt = 205;
            else if (roll >= 18 && roll <= 19) opt = 208;
            else switch (roll / 2) {
                case 0 -> opt = armor ? 204 : 206;
                case 1 -> opt = 213;
                case 2 -> opt = 201;
                default -> { continue; }
            }
            out.add(new Line(opt, 0));
        }
        return out;
    }

    /** Sum of an option kind over an item's lines (pct kinds: percent points). */
    static double sum(ItemStack item, String kind) {
        double total = 0;
        for (Line l : read(item)) {
            Opt o = byId(l.opt());
            if (o != null && o.kind().equals(kind)) total += value(o, l.grade());
        }
        return total;
    }

    static double cost(int grade, int locks) {
        double factor = locks <= 0 ? 1 : 1.5 + .5 * locks;
        return Math.round(BASE_COST[Math.clamp(grade, 0, 3)] * factor);
    }
}
