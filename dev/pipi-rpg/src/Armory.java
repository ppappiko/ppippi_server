package dev.pipi.life;

import java.io.File;
import java.io.IOException;
import java.util.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * 무기 관리인: the equipped weapon lives in a server-side weapon slot (plugins/PipiLife/armory.yml, serialized ItemStack per UUID).
 * Combat mode (client Alt) calls "/pipilife combat on|off": on moves the weapon into the hotbar (marked), off takes it back.
 */
final class Armory implements Listener {
    static final String TITLE = "삐삐서버 · 무기 장착";
    static final String MSG_START = "전투 모드 시작 · 슬롯 ";
    static final String MSG_BASIC = "전투 모드 기본";
    static final String MSG_END = "전투 모드 종료";
    private static final int EQUIPPED_SLOT = 4, CLOSE_SLOT = 8, FIRST_BAG = 9, SIZE = 45;
    private static Armory instance;

    private final PipiLife plugin;
    private final TownNpcRegistry npcs;
    private final NamespacedKey marker;
    private final File file;
    private final YamlConfiguration data;
    private final Map<UUID, State> states = new HashMap<>();

    /** weapon: equipped weapon while NOT in combat. slot>=0: combat mode, the weapon is the marked item in that hotbar slot. */
    private static final class State {
        ItemStack weapon, displaced, snapshot;
        int slot = -1;
    }

    private static final class Menu implements InventoryHolder {
        Inventory inventory;
        final int[] sources = new int[SIZE];
        final ItemStack[] snapshots = new ItemStack[SIZE];
        @Override public Inventory getInventory() { return inventory; }
    }

    Armory(PipiLife plugin, TownNpcRegistry npcs) {
        this.plugin = plugin;
        this.npcs = npcs;
        marker = new NamespacedKey(plugin, "armory_weapon");
        file = new File(plugin.getDataFolder(), "armory.yml");
        data = YamlConfiguration.loadConfiguration(file);
        var section = data.getConfigurationSection("players");
        if (section != null) for (String id : section.getKeys(false)) {
            try {
                State s = new State();
                String base = "players." + id;
                s.weapon = decode(base + ".weapon");
                s.slot = data.getInt(base + ".combat.slot", -1);
                s.displaced = decode(base + ".combat.displaced");
                s.snapshot = decode(base + ".combat.snapshot");
                states.put(UUID.fromString(id), s);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("무기 장착 데이터를 읽지 못했습니다: " + id + " " + ex);
            }
        }
        instance = this;
    }

    // ---- storage ----
    private static ItemStack encode(ItemStack item) { return item == null || item.getType().isAir() ? null : item; }
    private ItemStack decode(String path) { return data.getItemStack(path); }
    private void save(UUID id) {
        State s = states.get(id);
        String base = "players." + id;
        if (s == null || s.weapon == null && s.slot < 0) data.set(base, null);
        else {
            data.set(base + ".weapon", encode(s.weapon));
            data.set(base + ".combat.slot", s.slot < 0 ? null : s.slot);
            data.set(base + ".combat.displaced", s.slot < 0 ? null : encode(s.displaced));
            data.set(base + ".combat.snapshot", s.slot < 0 ? null : encode(s.snapshot));
        }
        try { data.save(file); }
        catch (IOException ex) { plugin.getLogger().severe("무기 장착 데이터 저장 실패: " + ex); }
    }

    private boolean marked(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(marker, PersistentDataType.BYTE);
    }
    private ItemStack withMark(ItemStack item, boolean on) {
        ItemStack copy = item.clone();
        var meta = copy.getItemMeta();
        if (on) meta.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte) 1);
        else meta.getPersistentDataContainer().remove(marker);
        copy.setItemMeta(meta);
        return copy;
    }
    static boolean inCombatMode(Player player) {
        Armory a = instance;
        return a != null && a.inCombat(player);
    }
    private boolean inCombat(Player player) {
        State s = states.get(player.getUniqueId());
        return s != null && s.slot >= 0;
    }

    /** The weapon that counts for stats / the status screen: equipped slot (the hand item while in combat mode), else the main hand. */
    static ItemStack weaponFor(Player player) {
        Armory a = instance;
        if (a != null) {
            State s = a.states.get(player.getUniqueId());
            if (s != null) {
                if (s.slot >= 0) {
                    ItemStack hand = player.getInventory().getItem(s.slot);
                    if (a.marked(hand)) return hand;
                    for (ItemStack it : player.getInventory().getContents()) if (a.marked(it)) return it;
                    if (a.marked(player.getItemOnCursor())) return player.getItemOnCursor();
                    if (s.snapshot != null) return s.snapshot;
                } else if (s.weapon != null) return s.weapon;
            }
        }
        // stable: the held item never counts (scrolling the hotbar must not change max HP)
        return new ItemStack(org.bukkit.Material.AIR);
    }
    static boolean hasEquipped(Player player) {
        Armory a = instance;
        State s = a == null ? null : a.states.get(player.getUniqueId());
        return s != null && (s.slot >= 0 || s.weapon != null);
    }

    // ---- combat mode ----
    boolean combatCommand(Player player, String[] args) {
        if (args.length >= 2 && args[1].equals("on")) {
            int preferred = 0;
            if (args.length >= 3) try { preferred = Integer.parseInt(args[2]); } catch (NumberFormatException ignored) { }
            enter(player, Math.clamp(preferred, 0, 8));
        } else if (args.length >= 2 && args[1].equals("off")) {
            if (inCombat(player)) leave(player, true, null);
            else player.sendMessage(MSG_END);
        }
        return true;
    }

    private void enter(Player player, int preferred) {
        UUID id = player.getUniqueId();
        State s = states.get(id);
        if (s != null && s.slot >= 0) { player.sendMessage(MSG_START + (s.slot + 1)); ClassSkills.syncFor(player); return; }
        if (s == null || s.weapon == null) { player.sendMessage(MSG_BASIC); return; }
        var bag = player.getInventory();
        int slot = preferred;
        ItemStack displaced = null;
        ItemStack current = bag.getItem(slot);
        if (current != null && !current.getType().isAir()) {
            slot = -1;
            for (int i = 0; i < 9; i++) { ItemStack it = bag.getItem(i); if (it == null || it.getType().isAir()) { slot = i; break; } }
            if (slot < 0) { slot = preferred; displaced = current.clone(); }
        }
        s.snapshot = s.weapon.clone();
        s.displaced = displaced;
        s.slot = slot;
        bag.setItem(slot, withMark(s.weapon, true));
        s.weapon = null;
        save(id);
        bag.setHeldItemSlot(slot);
        player.sendMessage(MSG_START + (slot + 1));
        ClassSkills.syncFor(player);
    }

    /** Takes the combat weapon back into the weapon slot. drops != null: the player is dying without keepInventory, displaced goes to the drops. */
    private void leave(Player player, boolean notify, List<ItemStack> drops) {
        UUID id = player.getUniqueId();
        State s = states.get(id);
        if (s == null || s.slot < 0) return;
        var bag = player.getInventory();
        ItemStack found = null;
        ItemStack at = bag.getItem(s.slot);
        if (marked(at)) { found = at; bag.setItem(s.slot, null); }
        if (found == null) {
            ItemStack[] all = bag.getContents();
            for (int i = 0; i < all.length && found == null; i++) if (marked(all[i])) { found = all[i]; bag.setItem(i, null); }
        }
        if (found == null && marked(player.getItemOnCursor())) { found = player.getItemOnCursor(); player.setItemOnCursor(null); }
        s.weapon = found != null ? withMark(found, false) : s.snapshot;
        ItemStack displaced = s.displaced;
        int slot = s.slot;
        s.slot = -1; s.displaced = null; s.snapshot = null;
        save(id);
        if (displaced != null) {
            if (drops != null) drops.add(displaced);
            else {
                ItemStack now = bag.getItem(slot);
                if (now == null || now.getType().isAir()) bag.setItem(slot, displaced);
                else for (ItemStack rest : bag.addItem(displaced).values()) player.getWorld().dropItemNaturally(player.getLocation(), rest);
            }
        }
        if (notify) player.sendMessage(MSG_END);
    }

    void close() {
        for (Player p : Bukkit.getOnlinePlayers()) leave(p, false, null);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void quit(PlayerQuitEvent event) { leave(event.getPlayer(), false, null); }

    @EventHandler
    public void join(PlayerJoinEvent event) { leave(event.getPlayer(), false, null); } // crash recovery

    @EventHandler(priority = EventPriority.LOWEST)
    public void death(org.bukkit.event.entity.PlayerDeathEvent event) {
        Player p = event.getEntity();
        if (!inCombat(p)) return;
        if (!event.getKeepInventory()) event.getDrops().removeIf(this::marked); // the weapon stays in the weapon slot
        leave(p, true, event.getKeepInventory() ? null : event.getDrops());
    }

    // while in combat mode the hand weapon cannot be dropped, moved or swapped away
    @EventHandler(ignoreCancelled = true)
    public void drop(PlayerDropItemEvent event) { if (marked(event.getItemDrop().getItemStack())) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void swap(PlayerSwapHandItemsEvent event) {
        if (inCombat(event.getPlayer()) && (marked(event.getMainHandItem()) || marked(event.getOffHandItem()))) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void frame(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof org.bukkit.entity.ItemFrame
            && marked(event.getPlayer().getInventory().getItem(event.getHand()))) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void guardClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p) || !inCombat(p)) return;
        boolean bad = marked(event.getCurrentItem()) || marked(event.getCursor());
        if (!bad && event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() >= 0) bad = marked(p.getInventory().getItem(event.getHotbarButton()));
        if (bad) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void guardDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player p) || !inCombat(p)) return;
        if (marked(event.getOldCursor()) || event.getNewItems().values().stream().anyMatch(this::marked)) event.setCancelled(true);
    }

    // ---- NPC + menu ----
    boolean command(Player player, String[] args) {
        if (!player.isOp()) { player.sendMessage("§c관리자 전용 명령어입니다. 무기 관리인에게 가세요."); return true; }
        if (args.length == 2 && args[1].equals("npc")) return spawnNpc(player);
        open(player);
        return true;
    }

    private boolean spawnNpc(Player player) {
        if (!player.getWorld().getName().equals(plugin.villageName())) { player.sendMessage("공동 마을에서 관리자만 설치할 수 있습니다."); return true; }
        if (npcs.exists(player.getWorld(), "armory", player.getLocation())) { player.sendMessage("무기 관리인은 이미 등록되어 있습니다."); return true; }
        player.getWorld().spawn(player.getLocation(), org.bukkit.entity.Villager.class, npc -> {
            npc.setCustomName("무기 관리인");
            npc.setCustomNameVisible(true);
            npc.setProfession(org.bukkit.entity.Villager.Profession.WEAPONSMITH);
            npc.setAI(false);
            npc.setInvulnerable(true);
            npc.setPersistent(true);
            npcs.register(npc, "armory");
        });
        player.sendMessage("무기 관리인을 설치했습니다.");
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void npc(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !npcs.matches(event.getRightClicked(), "armory")) return;
        event.setCancelled(true);
        if (!plugin.canTravel(event.getPlayer())) { event.getPlayer().sendMessage("튜토리얼을 먼저 완료하세요."); return; }
        open(event.getPlayer());
    }

    private ItemStack display(ItemStack item, String... extra) {
        ItemStack copy = item.clone();
        var meta = copy.getItemMeta();
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add(" ");
        lore.addAll(List.of(extra));
        meta.setLore(lore);
        copy.setItemMeta(meta);
        return copy;
    }

    void open(Player player) {
        if (inCombat(player)) { player.sendMessage("전투 모드를 끈 뒤(Alt) 이용하세요."); return; }
        Menu menu = new Menu();
        Inventory inv = Bukkit.createInventory(menu, SIZE, TITLE);
        menu.inventory = inv;
        render(player, menu);
        player.openInventory(inv);
    }

    private void render(Player player, Menu menu) {
        Inventory inv = menu.inventory;
        inv.clear();
        Arrays.fill(menu.sources, -1);
        Arrays.fill(menu.snapshots, null);
        State s = states.get(player.getUniqueId());
        inv.setItem(0, plugin.statusIcon(Material.BOOK, "§e무기 장착", "장착한 무기는 Alt 전투 모드에서", "자동으로 손에 들립니다.", "상태창 능력치·아이템레벨에도 반영됩니다."));
        inv.setItem(CLOSE_SLOT, plugin.statusIcon(Material.BARRIER, "닫기"));
        if (s != null && s.weapon != null) inv.setItem(EQUIPPED_SLOT, display(s.weapon, "§c장착 중 · 클릭하여 해제", "§7해제하면 가방으로 돌아갑니다."));
        else inv.setItem(EQUIPPED_SLOT, plugin.statusIcon(Material.ITEM_FRAME, "§7장착한 무기 없음", "아래 가방의 무기를 클릭하면 장착합니다."));
        var bag = player.getInventory();
        int shown = FIRST_BAG;
        for (int i = 0; i < 36 && shown < SIZE; i++) {
            ItemStack item = bag.getItem(i);
            if (!CombatStats.weapon(item) || !CombatStats.eligible(item)) continue;
            menu.sources[shown] = i;
            menu.snapshots[shown] = item.clone();
            inv.setItem(shown++, display(item, "§a클릭하여 장착", s != null && s.weapon != null ? "§7장착 중인 무기와 교체됩니다." : "§7가방에서 무기 칸으로 이동합니다."));
        }
        if (shown == FIRST_BAG) inv.setItem(FIRST_BAG, plugin.statusIcon(Material.BARRIER, "§7장착 가능한 무기 없음", "대장간에서 3줄 이상 옵션이 붙은", "검·활·지팡이를 제작하세요."));
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getView().getTopInventory()) return;
        int raw = event.getRawSlot();
        if (raw == CLOSE_SLOT) { player.closeInventory(); return; }
        if (inCombat(player)) { player.closeInventory(); return; }
        UUID id = player.getUniqueId();
        State s = states.computeIfAbsent(id, k -> new State());
        var bag = player.getInventory();
        if (raw == EQUIPPED_SLOT) {
            if (s.weapon == null) return;
            int free = bag.firstEmpty();
            if (free < 0) { player.sendMessage("§c가방이 가득 찼습니다. 빈 칸을 만든 뒤 해제하세요."); return; }
            bag.setItem(free, s.weapon);
            s.weapon = null;
            save(id);
            player.sendMessage("§e무기를 해제했습니다.");
        } else if (raw >= FIRST_BAG && raw < SIZE && menu.sources[raw] >= 0) {
            int source = menu.sources[raw];
            ItemStack item = bag.getItem(source);
            if (item == null || !item.equals(menu.snapshots[raw]) || !CombatStats.weapon(item) || !CombatStats.eligible(item)) {
                player.sendMessage("§c가방이 바뀌었습니다. 다시 선택하세요.");
                render(player, menu);
                return;
            }
            ItemStack old = s.weapon;
            bag.setItem(source, old); // swap: the previous weapon takes the vacated bag slot (null when nothing was equipped)
            s.weapon = item.clone();
            save(id);
            player.sendMessage("§a무기를 장착했습니다. Alt를 눌러 전투 모드에서 사용하세요.");
        } else return;
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 1f, 1f);
        render(player, menu);
    }

    @EventHandler
    public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) event.setCancelled(true);
    }
}
