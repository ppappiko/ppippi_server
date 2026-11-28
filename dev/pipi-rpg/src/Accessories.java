package dev.pipi.life;

import java.io.File;
import java.io.IOException;
import java.util.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * 악세서리 (목걸이·귀걸이·팔찌). 갑옷처럼 가방(E) 화면의 악세서리 칸 3개에 장착한다 (칸은 PipiTravel 이 그린다).
 * 장착한 악세서리는 서버(plugins/PipiLife/accessories.yml)에 보관된다. 바닐라에는 빈 슬롯이 없어서 클라이언트 칸 조작을
 * 아래 명령어로 받아 서버가 검증한 뒤 처리한다:
 *   /pipilife accessory equip <slot>       커서에 든 악세서리를 칸에 장착 (원래 끼던 것은 커서로)
 *   /pipilife accessory unequip <slot>     칸의 악세서리를 커서로 (커서가 비었을 때)
 *   /pipilife accessory quick <invSlot>    가방 칸의 악세서리를 Shift+클릭으로 장착 (원래 끼던 것은 그 가방 칸으로)
 *   /pipilife accessory quickoff <slot>    칸의 악세서리를 Shift+클릭으로 가방에
 *   /pipilife accessory give <slot> [모델] 테스트 악세서리 (관리자)
 * 손에 들고 우클릭해도 장착된다. 바뀔 때마다 본인에게 "PIPI_ACCI:<모델~아이템|…|…>", 모두에게 외형용
 * "PIPI_ACC:<uuid>:<모델>:<모델>:<모델>" 을 보낸다. 능력치는 CombatStats.stats() 가 equipped() 의 옵션 줄을 더한다 (아이템 레벨 평균 제외).
 */
final class Accessories implements Listener {
    enum Slot {
        NECKLACE("necklace", "목걸이", Material.GOLD_NUGGET),
        EARRING("earring", "귀걸이", Material.AMETHYST_SHARD),
        BRACELET("bracelet", "팔찌", Material.IRON_NUGGET);

        final String id, label;
        final Material icon;
        Slot(String id, String label, Material icon) { this.id = id; this.label = label; this.icon = icon; }

        static Slot of(String id) { for (Slot s : values()) if (s.id.equals(id)) return s; return null; }
    }

    private static Accessories instance;
    private final PipiLife plugin;
    private final CombatStats combat;
    private final File file;
    private final YamlConfiguration data;
    private final NamespacedKey typeKey, modelKey;

    Accessories(PipiLife plugin, CombatStats combat) {
        this.plugin = plugin;
        this.combat = combat;
        typeKey = new NamespacedKey(plugin, "accessory");
        modelKey = new NamespacedKey(plugin, "accessory_model");
        file = new File(plugin.getDataFolder(), "accessories.yml");
        data = YamlConfiguration.loadConfiguration(file);
        instance = this;
    }

    // ------------------------------------------------------------------ 조회 (CombatStats 등)

    /** 장착한 악세서리 (목걸이, 귀걸이, 팔찌 순서, 빈 칸은 null). */
    static ItemStack[] equipped(Player player) {
        Accessories a = instance;
        ItemStack[] out = new ItemStack[Slot.values().length];
        if (a == null) return out;
        for (Slot s : Slot.values()) out[s.ordinal()] = a.stored(player.getUniqueId(), s);
        return out;
    }

    static boolean isAccessory(ItemStack item) { return instance != null && instance.slotOf(item) != null; }

    private ItemStack stored(UUID id, Slot slot) {
        ItemStack item = data.getItemStack(id + "." + slot.id);
        return empty(item) ? null : item.clone();
    }

    private static boolean empty(ItemStack item) { return item == null || item.getType().isAir() || item.getAmount() <= 0; }

    Slot slotOf(ItemStack item) {
        if (empty(item) || !item.hasItemMeta()) return null;
        return Slot.of(item.getItemMeta().getPersistentDataContainer().get(typeKey, PersistentDataType.STRING));
    }

    private String model(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return "-";
        String m = item.getItemMeta().getPersistentDataContainer().get(modelKey, PersistentDataType.STRING);
        return m == null || !m.matches("[a-z0-9_]+") ? "basic" : m;
    }

    /** 한 칸을 바꿔 저장하고 능력치·화면·외형을 갱신한다. 저장에 실패하면 false (호출한 쪽이 아이템을 되돌린다). */
    private boolean put(Player player, Slot slot, ItemStack item) {
        String path = player.getUniqueId() + "." + slot.id;
        Object before = data.get(path);
        data.set(path, empty(item) ? null : item);
        try { data.save(file); }
        catch (IOException ex) {
            data.set(path, before);
            plugin.getLogger().severe("악세서리 저장 실패: " + ex);
            player.sendMessage("§c악세서리를 저장하지 못했어요.");
            return false;
        }
        combat.refresh(player);
        sync(player);
        return true;
    }

    private static ItemStack one(ItemStack item) { ItemStack c = item.clone(); c.setAmount(1); return c; }

    // ------------------------------------------------------------------ 명령어 (클라이언트 칸 조작)

    boolean command(Player player, String[] args) {
        String sub = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (sub.equals("give")) return give(player, args);
        Slot slot = args.length >= 3 ? Slot.of(args[2]) : null;
        boolean inventoryOpen = player.getOpenInventory().getType() == InventoryType.CRAFTING;
        switch (sub) {
            case "equip" -> { if (slot != null && inventoryOpen) equipCursor(player, slot); }
            case "unequip" -> { if (slot != null && inventoryOpen) unequipCursor(player, slot); }
            case "quickoff" -> { if (slot != null) unequipToBag(player, slot); }
            case "quick" -> {
                if (args.length >= 3 && inventoryOpen) {
                    try { equipFromBag(player, Integer.parseInt(args[2])); } catch (NumberFormatException ignored) {}
                }
            }
            case "sync" -> sync(player);
            default -> player.sendMessage("§7악세서리는 가방(E) 화면의 목걸이·귀걸이·팔찌 칸에 장착하거나, 손에 들고 우클릭하세요.");
        }
        return true;
    }

    private void equipCursor(Player player, Slot slot) {
        ItemStack cursor = player.getItemOnCursor();
        if (slotOf(cursor) != slot) { if (!empty(cursor)) player.sendMessage("§7이 칸에는 " + slot.label + "만 장착할 수 있어요."); sync(player); return; }
        ItemStack old = stored(player.getUniqueId(), slot);
        if (cursor.getAmount() > 1 && old != null) { player.sendMessage("§7먼저 장착한 " + slot.label + "을(를) 빼 주세요."); sync(player); return; }
        if (!put(player, slot, one(cursor))) return;
        if (cursor.getAmount() > 1) { cursor.setAmount(cursor.getAmount() - 1); player.setItemOnCursor(cursor); }
        else player.setItemOnCursor(old);
        sound(player);
    }

    private void unequipCursor(Player player, Slot slot) {
        ItemStack old = stored(player.getUniqueId(), slot);
        if (old == null || !empty(player.getItemOnCursor())) { sync(player); return; }
        if (!put(player, slot, null)) return;
        player.setItemOnCursor(old);
    }

    private void unequipToBag(Player player, Slot slot) {
        ItemStack old = stored(player.getUniqueId(), slot);
        if (old == null) return;
        if (player.getInventory().firstEmpty() < 0) { player.sendMessage("§7가방에 빈 칸이 없어요."); return; }
        if (!put(player, slot, null)) return;
        player.getInventory().addItem(old);
    }

    /** invSlot: 가방 칸 번호 (0~8 핫바, 9~35 가방). */
    private void equipFromBag(Player player, int invSlot) {
        if (invSlot < 0 || invSlot > 35) return;
        ItemStack item = player.getInventory().getItem(invSlot);
        Slot slot = slotOf(item);
        if (slot == null) return;
        equipFromStack(player, slot, item, rest -> player.getInventory().setItem(invSlot, rest));
    }

    /** item 에서 1개를 장착한다. 원래 끼던 것은 item 이 1개였으면 그 자리로, 아니면 가방으로 돌아간다. */
    private void equipFromStack(Player player, Slot slot, ItemStack item, java.util.function.Consumer<ItemStack> setRest) {
        ItemStack old = stored(player.getUniqueId(), slot);
        if (item.getAmount() > 1 && old != null && player.getInventory().firstEmpty() < 0) { player.sendMessage("§7가방에 빈 칸이 없어요."); return; }
        if (!put(player, slot, one(item))) return;
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
            setRest.accept(item);
            if (old != null) player.getInventory().addItem(old);
        } else setRest.accept(old);
        sound(player);
        player.sendActionBar("§a" + slot.label + "을(를) 장착했어요");
    }

    private void sound(Player player) { player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, .7f, 1.3f); }

    /** 손에 들고 우클릭 (블록을 볼 때는 Shift+우클릭): 갑옷처럼 바로 장착 (이미 끼고 있으면 서로 바꾼다). */
    @EventHandler(priority = EventPriority.HIGH)
    public void rightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        Slot slot = slotOf(hand);
        if (slot == null) return;
        // 바닐라 갑옷처럼: 허공 우클릭은 바로 장착, 상자 같은 블록을 보고 있으면 그냥 우클릭은 블록을 열고 Shift+우클릭은 장착
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && !player.isSneaking()
            && event.getClickedBlock() != null && event.getClickedBlock().getType().isInteractable()) return;
        event.setCancelled(true);
        equipFromStack(player, slot, hand, rest -> player.getInventory().setItemInMainHand(rest));
    }

    private boolean give(Player player, String[] args) {
        if (!player.isOp()) { player.sendMessage("§c관리자 전용 명령어입니다."); return true; }
        Slot slot = args.length >= 3 ? Slot.of(args[2]) : null;
        if (slot == null) { player.sendMessage("/pipilife accessory give <necklace|earring|bracelet> [모델]"); return true; }
        ItemStack item = testItem(slot, args.length >= 4 ? args[3].toLowerCase(Locale.ROOT) : "basic");
        for (ItemStack left : player.getInventory().addItem(item).values()) player.getWorld().dropItemNaturally(player.getLocation(), left);
        player.sendMessage("§a테스트 " + slot.label + "을(를) 받았어요. 우클릭하거나 가방(E)의 " + slot.label + " 칸에 넣어 장착하세요.");
        return true;
    }

    /** 테스트용 악세서리: 옵션 줄 2개 (스탯 기획 전 임시 값). */
    private ItemStack testItem(Slot slot, String model) {
        List<Enchants.Line> lines = switch (slot) {
            case NECKLACE -> List.of(new Enchants.Line(205, 1), new Enchants.Line(212, 0));   // 행운 +40, 치명타 +3%
            case EARRING -> List.of(new Enchants.Line(206, 1), new Enchants.Line(213, 0));    // 힘 +40, 체력 +20
            case BRACELET -> List.of(new Enchants.Line(207, 1), new Enchants.Line(214, 0));   // 민첩 +40, 체력 +3%
        };
        return create(slot, "§b[테스트] " + slot.label, model, "악세서리", lines, List.of("§8스탯은 기획 확정 전 임시 값입니다."));
    }

    private ItemStack create(Slot slot, String name, String model, String kind, List<Enchants.Line> lines, List<String> extra) {
        ItemStack item = new ItemStack(slot.icon);
        var meta = item.getItemMeta();
        meta.setDisplayName(name);
        Enchants.write(meta, lines, 0);
        List<String> lore = new ArrayList<>();
        lore.add("§7" + slot.label + " §8· " + kind);
        for (Enchants.Line l : lines) lore.add(Enchants.loreLine(l));
        lore.add("§8우클릭하거나 가방(E)의 " + slot.label + " 칸에 넣어 장착");
        lore.addAll(extra);
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, slot.id);
        meta.getPersistentDataContainer().set(modelKey, PersistentDataType.STRING, model);
        meta.setMaxStackSize(1);
        item.setItemMeta(meta);
        return item;
    }

    /** 1단계 장신구 옵션: 행운 / 힘 / 민첩 / 지능 (2단계 보스부터 옵션을 늘릴 예정). */
    private static final int[] TIER1_OPTS = {205, 206, 207, 208};

    /**
     * 1단계 보스(딥 다크 시티) 보상 장신구: 부위 3종 중 하나, 옵션 1줄 (4종 중 하나, 값 min~max 정수 동일 확률), 레어 등급.
     * 장신구마다 부위·옵션·수치를 따로 뽑는다 (같은 부위가 몰려 나오는 것도 운).
     * 악세서리 시스템이 아직 준비되지 않았으면 null.
     */
    static ItemStack tier1(String source, int min, int max) {
        Accessories a = instance;
        if (a == null) return null;
        var r = java.util.concurrent.ThreadLocalRandom.current();
        Slot slot = Slot.values()[r.nextInt(Slot.values().length)];
        int lo = Math.max(1, Math.min(min, max)), hi = Math.max(lo, Math.max(min, max));
        var line = new Enchants.Line(TIER1_OPTS[r.nextInt(TIER1_OPTS.length)], 0, r.nextInt(lo, hi + 1));
        return a.create(slot, Enchants.COLORS[0] + source + " " + slot.label, "deep", "1단계 장신구", List.of(line), List.of("§8" + source + " 클리어 보상"));
    }

    /**
     * 2단계 장신구 옵션 풀: 장비 옵션 전부 + 스킬 쿨타임 감소 / 스킬 지속시간 증가.
     * 같은 효과가 무기·방어구 두 개로 나뉜 옵션(치명타%, 올스탯, 올스탯%)은 한 번만 (방어구 쪽 수치).
     */
    private static final int[] TIER2_OPTS = {201, 202, 203, 204, 205, 206, 207, 208, 209, 210, 211, 212, 213, 214, 215, 216, 217, 218, 222, 223, 224, 225};

    /**
     * 2단계 보스(통곡의 왕좌) 보상 장신구: 부위 3종 중 하나, 옵션 2줄 (줄마다 풀에서 독립으로 뽑음, 같은 옵션 중복 가능), 에픽 등급.
     * 수치는 레어 값 ~ 에픽 값 사이 동일 확률: 고정 수치는 정수, % 옵션은 소수점 한 자리.
     */
    static ItemStack tier2(String source) {
        Accessories a = instance;
        if (a == null) return null;
        var r = java.util.concurrent.ThreadLocalRandom.current();
        Slot slot = Slot.values()[r.nextInt(Slot.values().length)];
        int first = r.nextInt(TIER2_OPTS.length), second = r.nextInt(TIER2_OPTS.length); // 줄마다 독립 시행 (같은 옵션이 두 줄 나올 수 있음)
        List<Enchants.Line> lines = List.of(rollLine(TIER2_OPTS[first], 0, 1, r), rollLine(TIER2_OPTS[second], 0, 1, r));
        return a.create(slot, Enchants.COLORS[1] + source + " " + slot.label, "sorrow", "2단계 장신구", lines, List.of("§8" + source + " 클리어 보상"));
    }

    /**
     * 2단계 보스 경매 전용 장신구 옵션 풀: 스킬 쿨타임 감소, 스킬 지속시간 증가, 데미지%, 올스탯%, 공격력%, 보스 데미지%, 치명타 확률%.
     * 올스탯%·치명타는 무기 쪽 옵션 (에픽 값이 더 높음: 6%, 8%).
     */
    private static final int[] TIER2_BOSS_OPTS = {224, 225, 218, 220, 215, 217, 219};

    /** 2단계 보스 경매 전용 장신구: 부위 랜덤, 옵션 2줄 (줄마다 독립으로 뽑음, 중복 가능), 수치는 에픽 최대값 고정. */
    static ItemStack tier2Boss(String source, String bossName) {
        Accessories a = instance;
        if (a == null) return null;
        var r = java.util.concurrent.ThreadLocalRandom.current();
        Slot slot = Slot.values()[r.nextInt(Slot.values().length)];
        int first = r.nextInt(TIER2_BOSS_OPTS.length), second = r.nextInt(TIER2_BOSS_OPTS.length); // 줄마다 독립 시행 (같은 옵션이 두 줄 나올 수 있음)
        List<Enchants.Line> lines = List.of(new Enchants.Line(TIER2_BOSS_OPTS[first], 1), new Enchants.Line(TIER2_BOSS_OPTS[second], 1));
        return a.create(slot, "§6" + bossName + "의 " + slot.label, "lord", "보스 전용 장신구", lines, List.of("§8" + source + " 전리품 경매"));
    }

    /** 옵션 한 줄: lowGrade 값 ~ highGrade 값 사이 (고정 수치 정수, % 소수점 한 자리), 표시 등급은 highGrade. */
    private static Enchants.Line rollLine(int id, int lowGrade, int highGrade, java.util.Random r) {
        Enchants.Opt o = Enchants.byId(id);
        double lo = Enchants.value(o, lowGrade), hi = Enchants.value(o, highGrade);
        double v = o.pct() ? r.nextInt((int) Math.round(lo * 10), (int) Math.round(hi * 10) + 1) / 10.0
            : r.nextInt((int) Math.round(lo), (int) Math.round(hi) + 1);
        return new Enchants.Line(id, highGrade, v);
    }

    // ------------------------------------------------------------------ 동기화

    /** 본인: 칸에 그릴 아이템, 모두: 몸에 그릴 모델. */
    private void sync(Player player) {
        StringBuilder items = new StringBuilder("PIPI_ACCI:");
        ItemStack[] worn = equipped(player);
        for (int i = 0; i < worn.length; i++) {
            if (i > 0) items.append('|');
            String wire = worn[i] == null ? null : ItemWire.encode(worn[i], player);
            // "<모델>~<아이템>": 아이템 전송에서 PDC가 빠질 수 있어 클라이언트가 아이콘을 고르도록 모델을 따로 붙인다
            items.append(wire == null ? "-" : model(worn[i]) + "~" + wire);
        }
        Armory.sendLine(player, items.toString());
        String line = visualLine(player);
        for (Player other : Bukkit.getOnlinePlayers()) other.sendMessage(line);
    }

    private String visualLine(Player player) {
        StringBuilder b = new StringBuilder("PIPI_ACC:").append(player.getUniqueId());
        for (ItemStack item : equipped(player)) b.append(':').append(item == null ? "-" : model(item));
        return b.toString();
    }

    @EventHandler public void join(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            for (Player other : Bukkit.getOnlinePlayers()) if (other != player) player.sendMessage(visualLine(other));
            sync(player);
        }, 40L);
    }
}
