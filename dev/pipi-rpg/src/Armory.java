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
    static final String MSG_START = "전투 모드 시작 · 슬롯 ";
    static final String MSG_BASIC = "전투 모드 기본";
    static final String MSG_END = "전투 모드 종료";
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
    /** True only for the marked Armory weapon while in combat mode (the only thing that may cast skills). */
    static boolean isCombatWeapon(Player player, ItemStack item) {
        Armory a = instance;
        return a != null && a.inCombat(player) && a.marked(item);
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

    // ---- NPC + client screen ----
    // Protocol (hidden chat messages, cancelled by the PipiTravel client mod):
    //   PIPI_ARM_BEGIN:<count>:<open 0|1>
    //   PIPI_ARM_ITEM:<invSlot -1..35>:<hash>:<equipped 0|1>:<base64 serializeAsBytes>   (count times)
    //   PIPI_ARM_END
    //   long lines (>8000 chars) arrive as PIPI_ARM_PART:<id>:<idx>:<total>:<chunk> and are reassembled by the client
    // Client commands: /pipilife armory equip <invSlot> <hash> | unequip | list
    boolean command(Player player, String[] args) {
        if (args.length >= 2 && args[1].equals("equip")) { equipCommand(player, args); return true; }
        if (args.length >= 2 && args[1].equals("unequip")) { unequipCommand(player); return true; }
        if (args.length >= 2 && args[1].equals("list")) { sendData(player, false); return true; }
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

    void open(Player player) {
        if (inCombat(player)) { player.sendMessage("전투 모드를 끈 뒤(Alt) 이용하세요."); return; }
        sendData(player, true);
    }

    private static boolean bytesWarned;
    private static final java.util.concurrent.atomic.AtomicInteger PART_ID = new java.util.concurrent.atomic.AtomicInteger();
    /** Debug logging for the hidden-chat item transport: -Dpipi.debug=true or "debug: true" in armory.yml. */
    private boolean debug() { return Boolean.getBoolean("pipi.debug") || data.getBoolean("debug", false); }

    /** ItemWire text ("B"/"T" + base64) of the item with the player as owner, or null. */
    static String bytes(ItemStack item, Player owner) { return ItemWire.encode(item, owner); }

    /** Sends one hidden line; lines over 8000 chars are split into PIPI_ARM_PART:<id>:<idx>:<total>:<chunk> (client reassembles the original line). */
    static void sendLine(Player player, String line) {
        final int max = 8000;
        if (line.length() <= max) { player.sendMessage(line); return; }
        int id = PART_ID.incrementAndGet() & 0xffffff, total = (line.length() + max - 1) / max;
        for (int i = 0; i < total; i++)
            player.sendMessage("PIPI_ARM_PART:" + id + ":" + i + ":" + total + ":" + line.substring(i * max, Math.min(line.length(), (i + 1) * max)));
    }

    /** Sends the equipped weapon (slot -1) and every eligible bag weapon; the client opens the screen when open is true. */
    private void sendData(Player player, boolean open) {
        State s = states.get(player.getUniqueId());
        List<String> lines = new ArrayList<>();
        int scanned = 0, eligibleCount = 0;
        if (s != null && s.weapon != null) {
            String b64 = bytes(s.weapon.clone(), player);
            if (b64 != null) lines.add("PIPI_ARM_ITEM:-1:" + Integer.toHexString(s.weapon.hashCode()) + ":1:" + b64);
        }
        var bag = player.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack item = bag.getItem(i);
            if (CombatStats.weapon(item)) scanned++;
            if (!CombatStats.weapon(item) || !CombatStats.eligible(item)) continue;
            eligibleCount++;
            String b64 = bytes(item.clone(), player);
            if (b64 != null) lines.add("PIPI_ARM_ITEM:" + i + ":" + Integer.toHexString(item.hashCode()) + ":0:" + b64);
        }
        if (debug()) plugin.getLogger().info("[Armory] " + player.getName() + " 에게 무기 " + lines.size() + "개 전송 (가방 무기 " + scanned
            + ", 장착 가능 " + eligibleCount + ", 장착중 " + (s != null && s.weapon != null ? 1 : 0) + ", open=" + open + ")");
        player.sendMessage("PIPI_ARM_BEGIN:" + lines.size() + ":" + (open ? 1 : 0));
        for (String line : lines) sendLine(player, line);
        player.sendMessage("PIPI_ARM_END");
    }

    private void equipCommand(Player player, String[] args) {
        if (inCombat(player)) { player.sendMessage("전투 모드를 끈 뒤(Alt) 이용하세요."); sendData(player, false); return; }
        int source;
        try { source = Integer.parseInt(args[2]); } catch (RuntimeException ex) { return; }
        String hash = args.length >= 4 ? args[3] : null;
        var bag = player.getInventory();
        ItemStack item = source >= 0 && source < 36 ? bag.getItem(source) : null;
        if (item == null || !CombatStats.weapon(item) || !CombatStats.eligible(item)
            || hash != null && !hash.equals(Integer.toHexString(item.hashCode()))) {
            player.sendMessage("§c가방이 바뀌었습니다. 다시 선택하세요.");
            sendData(player, false);
            return;
        }
        UUID id = player.getUniqueId();
        State s = states.computeIfAbsent(id, k -> new State());
        ItemStack old = s.weapon;
        bag.setItem(source, old); // swap: the previous weapon takes the vacated bag slot (null when nothing was equipped)
        s.weapon = item.clone();
        save(id);
        player.sendMessage("§a무기를 장착했습니다. Alt를 눌러 전투 모드에서 사용하세요.");
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 1f, 1f);
        ClassSkills.syncFor(player);
        sendData(player, false);
    }

    private void unequipCommand(Player player) {
        if (inCombat(player)) { player.sendMessage("전투 모드를 끈 뒤(Alt) 이용하세요."); sendData(player, false); return; }
        UUID id = player.getUniqueId();
        State s = states.get(id);
        if (s == null || s.weapon == null) { sendData(player, false); return; }
        var bag = player.getInventory();
        int free = bag.firstEmpty();
        if (free < 0) { player.sendMessage("§c가방이 가득 찼습니다. 빈 칸을 만든 뒤 해제하세요."); sendData(player, false); return; }
        bag.setItem(free, s.weapon);
        s.weapon = null;
        save(id);
        player.sendMessage("§e무기를 해제했습니다.");
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 1f, 1f);
        ClassSkills.syncFor(player);
        sendData(player, false);
    }
}
