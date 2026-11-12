package dev.pipi.life;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.Score;

/**
 * Player-to-player trade window (server authoritative). Items are moved into escrow the moment they are added,
 * gold is only reserved (checked, not deducted) until both sides press 교환하기.
 *
 * Client commands (/pipilife trade ...): req <name> | ok <name> | no <name> | add <invSlot> [count] [itemId] | remove <slot 0-8>
 *   | gold <amount> | lock | confirm | cancel | chat <text> | list
 * Hidden messages to clients (cancelled by the PipiTravel mod; long lines are split by Armory.sendLine):
 *   PIPI_TRADE_BEGIN:<open 0|1>:<myName>:<partnerName>:<myGold>:<partnerGold>:<myLock>:<partnerLock>:<myConfirm>:<partnerConfirm>:<myBalance>
 *   PIPI_TRADE_SLOT:<side 0=me 1=partner>:<idx 0-8>:<ItemWire>   (non-empty slots only)
 *   PIPI_TRADE_END
 *   PIPI_TRADE_CHAT:<sender>:<text>   PIPI_TRADE_MSG:<text>   PIPI_TRADE_CLOSE:<done 0|1>:<reason>
 * Escrow is persisted to trade-escrow.yml so a crash/logout/death never loses items (returned on join/respawn/startup).
 */
final class TradeWindow implements Listener {
    static final int SLOTS = 9;
    private static final double MAX_DIST_SQ = 100;
    private static final long REQUEST_MS = 30_000;

    private static final class Session {
        final UUID[] id = new UUID[2];
        final String[] name = new String[2];
        final ItemStack[][] items = new ItemStack[2][SLOTS];
        final long[] gold = new long[2];
        final boolean[] lock = new boolean[2], confirm = new boolean[2];
    }

    private record Request(UUID from, long expires) {}

    private final PipiLife plugin;
    private final TownSystems town;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, Request> requests = new HashMap<>(); // keyed by target
    private final File escrowFile;
    private final YamlConfiguration escrow;
    private int task = -1;

    TradeWindow(PipiLife plugin, TownSystems town) {
        this.plugin = plugin;
        this.town = town;
        this.escrowFile = new File(plugin.getDataFolder(), "trade-escrow.yml");
        this.escrow = YamlConfiguration.loadConfiguration(escrowFile);
        for (Player p : Bukkit.getOnlinePlayers()) recover(p);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10, 10).getTaskId();
    }

    /** Plugin disable: close every trade; escrowed items stay in trade-escrow.yml and return on the next join. */
    void close() {
        if (task >= 0) Bukkit.getScheduler().cancelTask(task);
        task = -1;
        for (Session s : new HashSet<>(sessions.values())) cancel(s, "서버가 종료되어 거래가 취소되었습니다.", Set.of(s.id[0], s.id[1]));
        saveEscrow();
    }

    // ---- requests ----
    void request(Player from, Player to) {
        if (from.equals(to) || !to.isOnline()) return;
        recover(from);
        recover(to);
        if (sessions.containsKey(from.getUniqueId())) { from.sendMessage("§c이미 거래 중입니다."); return; }
        if (sessions.containsKey(to.getUniqueId())) { from.sendMessage("§c" + to.getName() + "님은 다른 거래 중입니다."); return; }
        if (!near(from, to)) { from.sendMessage("§c거래 상대와 10블록 안에 있어야 합니다."); return; }
        requests.put(to.getUniqueId(), new Request(from.getUniqueId(), System.currentTimeMillis() + REQUEST_MS));
        from.sendMessage("§e" + to.getName() + "님에게 거래를 요청했습니다. (30초 안에 수락해야 합니다)");
        TextComponent line = new TextComponent(from.getName() + "님이 거래를 요청했습니다. ");
        line.setColor(ChatColor.YELLOW);
        TextComponent yes = new TextComponent("[수락]");
        yes.setColor(ChatColor.GREEN);
        yes.setBold(true);
        yes.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pipilife trade ok " + from.getName()));
        TextComponent no = new TextComponent(" [거절]");
        no.setColor(ChatColor.RED);
        no.setBold(true);
        no.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pipilife trade no " + from.getName()));
        line.addExtra(yes);
        line.addExtra(no);
        to.spigot().sendMessage(line);
    }

    private void accept(Player to, String fromName) {
        Request req = requests.remove(to.getUniqueId());
        Player from = req == null ? null : Bukkit.getPlayer(req.from());
        if (req == null || from == null || !from.getName().equalsIgnoreCase(fromName) || req.expires() < System.currentTimeMillis()) {
            to.sendMessage("§c거래 요청이 없거나 만료되었습니다.");
            return;
        }
        recover(from);
        recover(to);
        if (sessions.containsKey(from.getUniqueId()) || sessions.containsKey(to.getUniqueId())) { to.sendMessage("§c상대가 이미 거래 중입니다."); return; }
        if (!near(from, to) || from.isDead() || to.isDead()) { to.sendMessage("§c거래 상대와 10블록 안에 있어야 합니다."); from.sendMessage("§c거래가 시작되지 않았습니다. (거리)"); return; }
        Session s = new Session();
        s.id[0] = from.getUniqueId(); s.name[0] = from.getName();
        s.id[1] = to.getUniqueId(); s.name[1] = to.getName();
        sessions.put(s.id[0], s);
        sessions.put(s.id[1], s);
        persist(s);
        broadcast(s, true);
    }

    private void decline(Player to, String fromName) {
        Request req = requests.get(to.getUniqueId());
        Player from = req == null ? null : Bukkit.getPlayer(req.from());
        if (req == null || from == null || !from.getName().equalsIgnoreCase(fromName)) return;
        requests.remove(to.getUniqueId());
        to.sendMessage("§7거래 요청을 거절했습니다.");
        from.sendMessage("§c" + to.getName() + "님이 거래를 거절했습니다.");
    }

    // ---- command entry (returns false when not a trade-window subcommand, so the old handler can run) ----
    boolean command(Player p, String[] args) {
        if (args.length < 2) return false;
        String sub = args[1].toLowerCase(java.util.Locale.ROOT);
        switch (sub) {
            case "req" -> {
                Player t = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : null;
                if (t == null) p.sendMessage("§c상대를 찾을 수 없습니다.");
                else request(p, t);
            }
            case "ok" -> { if (args.length > 2) accept(p, args[2]); }
            case "no" -> { if (args.length > 2) decline(p, args[2]); }
            case "add", "remove", "gold", "lock", "confirm", "cancel", "chat", "list" -> {
                Session s = sessions.get(p.getUniqueId());
                if (s == null) { if (!sub.equals("cancel")) p.sendMessage("§c진행 중인 거래가 없습니다."); p.sendMessage("PIPI_TRADE_CLOSE:0:"); return true; }
                int me = side(s, p);
                if (!valid(s)) return true;
                switch (sub) {
                    case "add" -> add(s, me, p, args);
                    case "remove" -> remove(s, me, p, args);
                    case "gold" -> setGold(s, me, p, args);
                    case "lock" -> toggleLock(s, me);
                    case "confirm" -> confirm(s, me, p);
                    case "cancel" -> cancel(s, p.getName() + "님이 거래를 취소했습니다.", Set.of());
                    case "chat" -> chat(s, me, args);
                    default -> broadcast(s, false);
                }
            }
            default -> { return false; }
        }
        return true;
    }

    private int side(Session s, Player p) { return s.id[0].equals(p.getUniqueId()) ? 0 : 1; }

    private boolean near(Player a, Player b) {
        return a.isOnline() && b.isOnline() && a.getWorld().equals(b.getWorld()) && a.getLocation().distanceSquared(b.getLocation()) <= MAX_DIST_SQ;
    }

    /** Cancels (and returns false) when the trade can no longer continue. */
    private boolean valid(Session s) {
        Player a = Bukkit.getPlayer(s.id[0]), b = Bukkit.getPlayer(s.id[1]);
        if (a == null || b == null) { cancel(s, "상대가 접속 중이 아니라 거래가 취소되었습니다.", Set.of()); return false; }
        if (a.isDead() || b.isDead()) { cancel(s, "사망하여 거래가 취소되었습니다.", Set.of()); return false; }
        if (!a.getWorld().equals(b.getWorld())) { cancel(s, "월드가 달라 거래가 취소되었습니다.", Set.of()); return false; }
        if (a.getLocation().distanceSquared(b.getLocation()) > MAX_DIST_SQ) { cancel(s, "거리가 10블록 이상 멀어져 거래가 취소되었습니다.", Set.of()); return false; }
        return true;
    }

    private void tick() {
        long now = System.currentTimeMillis();
        requests.entrySet().removeIf(e -> e.getValue().expires() < now);
        for (Session s : new HashSet<>(sessions.values())) valid(s);
    }

    // ---- editing ----
    private boolean editable(Session s, int me, Player p) {
        if (s.lock[me]) { p.sendMessage("PIPI_TRADE_MSG:등록 중에는 수정할 수 없습니다. 등록을 취소하세요."); return false; }
        return true;
    }

    private void resetLocks(Session s) {
        s.lock[0] = s.lock[1] = false;
        s.confirm[0] = s.confirm[1] = false;
    }

    private void add(Session s, int me, Player p, String[] args) {
        if (!editable(s, me, p) || args.length < 3) return;
        int slot, count = Integer.MAX_VALUE;
        try {
            slot = Integer.parseInt(args[2]);
            if (args.length > 3) count = Integer.parseInt(args[3]);
        } catch (NumberFormatException ex) { return; }
        if (slot < 0 || slot > 35 || count < 1) return;
        ItemStack item = p.getInventory().getItem(slot);
        if (item == null || item.getType().isAir()) { broadcast(s, false); return; }
        if (args.length > 4 && !args[4].equals(item.getType().getKey().toString())) { p.sendMessage("PIPI_TRADE_MSG:가방이 바뀌었습니다. 다시 선택하세요."); broadcast(s, false); return; }
        int n = Math.min(count, item.getAmount());
        ItemStack[] mine = s.items[me];
        int target = -1;
        for (int i = 0; i < SLOTS && target < 0; i++)
            if (mine[i] != null && mine[i].isSimilar(item) && mine[i].getAmount() + n <= mine[i].getMaxStackSize()) target = i;
        for (int i = 0; i < SLOTS && target < 0; i++) if (mine[i] == null) target = i;
        if (target < 0) { p.sendMessage("PIPI_TRADE_MSG:거래 슬롯이 가득 찼습니다."); return; }
        // move: escrow first, then shrink the inventory stack (single main-thread step, so the item exists exactly once)
        if (mine[target] == null) { ItemStack moved = item.clone(); moved.setAmount(n); mine[target] = moved; }
        else mine[target].setAmount(mine[target].getAmount() + n);
        if (n >= item.getAmount()) p.getInventory().setItem(slot, null);
        else item.setAmount(item.getAmount() - n);
        resetLocks(s);
        persist(s);
        broadcast(s, false);
    }

    private void remove(Session s, int me, Player p, String[] args) {
        if (!editable(s, me, p) || args.length < 3) return;
        int idx;
        try { idx = Integer.parseInt(args[2]); } catch (NumberFormatException ex) { return; }
        if (idx < 0 || idx >= SLOTS || s.items[me][idx] == null) return;
        ItemStack it = s.items[me][idx];
        if (!fits(p, List.of(it))) { p.sendMessage("PIPI_TRADE_MSG:가방 공간이 부족합니다."); return; }
        s.items[me][idx] = null;
        p.getInventory().addItem(it.clone());
        resetLocks(s);
        persist(s);
        broadcast(s, false);
    }

    private void setGold(Session s, int me, Player p, String[] args) {
        if (!editable(s, me, p) || args.length < 3) return;
        long amount;
        try { amount = Long.parseLong(args[2].replace(",", "")); } catch (NumberFormatException ex) { p.sendMessage("PIPI_TRADE_MSG:골드를 정수로 입력하세요."); return; }
        long balance = town.gold(p).getScore();
        if (amount < 0) amount = 0;
        if (amount > balance) { p.sendMessage("PIPI_TRADE_MSG:보유 골드(" + TownSystems.fmt(balance) + ")보다 많이 걸 수 없습니다."); broadcast(s, false); return; }
        s.gold[me] = amount;
        resetLocks(s);
        broadcast(s, false);
    }

    private void toggleLock(Session s, int me) {
        if (s.lock[me]) { s.lock[me] = false; s.confirm[0] = s.confirm[1] = false; }
        else {
            Player p = Bukkit.getPlayer(s.id[me]);
            if (p != null && s.gold[me] > town.gold(p).getScore()) {
                s.gold[me] = 0;
                p.sendMessage("PIPI_TRADE_MSG:골드 잔액이 부족해 걸어둔 골드를 0으로 되돌렸습니다.");
            }
            s.lock[me] = true;
            s.confirm[0] = s.confirm[1] = false;
        }
        broadcast(s, false);
    }

    private void confirm(Session s, int me, Player p) {
        if (!s.lock[0] || !s.lock[1]) { p.sendMessage("PIPI_TRADE_MSG:양쪽이 모두 등록해야 교환할 수 있습니다."); return; }
        s.confirm[me] = true;
        if (s.confirm[0] && s.confirm[1]) complete(s);
        else broadcast(s, false);
    }

    private void chat(Session s, int me, String[] args) {
        if (args.length < 3) return;
        String text = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)).replace('§', ' ').replaceAll("[\\r\\n]", " ").trim();
        if (text.isEmpty()) return;
        if (text.length() > 80) text = text.substring(0, 80);
        for (int i = 0; i < 2; i++) {
            Player q = Bukkit.getPlayer(s.id[i]);
            if (q != null) q.sendMessage("PIPI_TRADE_CHAT:" + s.name[me] + ":" + text);
        }
    }

    // ---- completion ----
    private void complete(Session s) {
        Player[] pl = {Bukkit.getPlayer(s.id[0]), Bukkit.getPlayer(s.id[1])};
        if (pl[0] == null || pl[1] == null || !valid(s)) return;
        Score[] gold = {town.gold(pl[0]), town.gold(pl[1])};
        for (int i = 0; i < 2; i++) {
            long after = (long) gold[i].getScore() - s.gold[i] + s.gold[1 - i];
            if (s.gold[i] > gold[i].getScore()) { fail(s, s.name[i] + "님의 골드가 부족합니다."); return; }
            if (after > Integer.MAX_VALUE) { fail(s, s.name[i] + "님의 골드 보유 한도를 초과합니다."); return; }
        }
        List<ItemStack> toA = items(s, 1), toB = items(s, 0);
        if (!fits(pl[0], toA)) { fail(s, s.name[0] + "님의 가방 공간이 부족합니다."); return; }
        if (!fits(pl[1], toB)) { fail(s, s.name[1] + "님의 가방 공간이 부족합니다."); return; }
        // point of no return: tear the session down first so nothing can be touched twice
        String log = "[Trade] " + s.name[0] + " <-> " + s.name[1] + " | " + s.name[0] + " gave " + describe(toB) + " + " + s.gold[0]
            + "G | " + s.name[1] + " gave " + describe(toA) + " + " + s.gold[1] + "G";
        long g0 = s.gold[0], g1 = s.gold[1];
        sessions.remove(s.id[0]);
        sessions.remove(s.id[1]);
        for (int i = 0; i < 2; i++) { java.util.Arrays.fill(s.items[i], null); escrow.set(s.id[i].toString(), null); }
        saveEscrow();
        gold[0].setScore((int) (gold[0].getScore() - g0 + g1));
        gold[1].setScore((int) (gold[1].getScore() - g1 + g0));
        give(pl[0], toA);
        give(pl[1], toB);
        plugin.getLogger().info(log);
        for (Player p : pl) {
            p.sendMessage("PIPI_TRADE_CLOSE:1:거래가 완료되었습니다.");
            p.sendMessage("§a거래가 완료되었습니다.");
        }
    }

    private void fail(Session s, String message) {
        s.confirm[0] = s.confirm[1] = false;
        for (int i = 0; i < 2; i++) {
            Player q = Bukkit.getPlayer(s.id[i]);
            if (q != null) q.sendMessage("PIPI_TRADE_MSG:" + message);
        }
        broadcast(s, false);
    }

    private List<ItemStack> items(Session s, int side) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack it : s.items[side]) if (it != null) out.add(it.clone());
        return out;
    }

    private String describe(List<ItemStack> list) {
        if (list.isEmpty()) return "[]";
        StringBuilder b = new StringBuilder("[");
        for (ItemStack it : list) {
            if (b.length() > 1) b.append(", ");
            String dn = it.hasItemMeta() && it.getItemMeta().hasDisplayName() ? it.getItemMeta().getDisplayName().replace('§', '&') : "";
            b.append(it.getType().getKey()).append(" x").append(it.getAmount()).append(dn.isEmpty() ? "" : " \"" + dn + "\"");
        }
        return b.append(']').toString();
    }

    /** True when every item would fit into the player's storage slots right now (simulated on a copy). */
    private boolean fits(Player p, List<ItemStack> incoming) {
        Inventory tmp = Bukkit.createInventory(null, 36);
        ItemStack[] cur = p.getInventory().getStorageContents();
        ItemStack[] copy = new ItemStack[36];
        for (int i = 0; i < 36 && i < cur.length; i++) copy[i] = cur[i] == null ? null : cur[i].clone();
        tmp.setContents(copy);
        for (ItemStack it : incoming) if (!tmp.addItem(it.clone()).isEmpty()) return false;
        return true;
    }

    private void give(Player p, List<ItemStack> items) {
        for (ItemStack it : items)
            for (ItemStack left : p.getInventory().addItem(it).values()) p.getWorld().dropItem(p.getLocation(), left);
    }

    // ---- cancel / recovery ----
    /** Ends the trade. Players in defer keep their items in the escrow file (returned on next join/respawn); everyone else gets them now. */
    private void cancel(Session s, String reason, Set<UUID> defer) {
        if (sessions.get(s.id[0]) != s && sessions.get(s.id[1]) != s) return;
        sessions.remove(s.id[0]);
        sessions.remove(s.id[1]);
        for (int i = 0; i < 2; i++) {
            Player p = Bukkit.getPlayer(s.id[i]);
            boolean deferred = defer.contains(s.id[i]) || p == null || p.isDead();
            if (!deferred) {
                give(p, items(s, i));
                escrow.set(s.id[i].toString(), null);
            }
            java.util.Arrays.fill(s.items[i], null);
            if (p != null) {
                p.sendMessage("PIPI_TRADE_CLOSE:0:" + reason);
                p.sendMessage("§c" + reason);
            }
        }
        saveEscrow();
    }

    /** Hands back anything left in the escrow file for this player (not while they are in a trade). */
    private void recover(Player p) {
        String key = p.getUniqueId().toString();
        if (sessions.containsKey(p.getUniqueId()) || !escrow.contains(key) || p.isDead()) return;
        List<?> list = escrow.getList(key);
        escrow.set(key, null);
        saveEscrow();
        if (list == null) return;
        List<ItemStack> items = new ArrayList<>();
        for (Object o : list) if (o instanceof ItemStack it) items.add(it);
        give(p, items);
        if (!items.isEmpty()) p.sendMessage("§e진행 중이던 거래가 취소되어 맡겨 둔 아이템을 돌려받았습니다.");
        plugin.getLogger().info("[Trade] escrow returned to " + p.getName() + ": " + describe(items));
    }

    private void persist(Session s) {
        for (int i = 0; i < 2; i++) {
            List<ItemStack> list = items(s, i);
            escrow.set(s.id[i].toString(), list.isEmpty() ? null : list);
        }
        saveEscrow();
    }

    private void saveEscrow() {
        try { escrow.save(escrowFile); }
        catch (IOException ex) { plugin.getLogger().warning("거래 보관함을 저장하지 못했습니다: " + ex); }
    }

    // ---- network ----
    private void broadcast(Session s, boolean open) {
        for (int me = 0; me < 2; me++) {
            Player p = Bukkit.getPlayer(s.id[me]);
            if (p == null) continue;
            int o = 1 - me;
            p.sendMessage("PIPI_TRADE_BEGIN:" + (open ? 1 : 0) + ":" + s.name[me] + ":" + s.name[o] + ":" + s.gold[me] + ":" + s.gold[o]
                + ":" + (s.lock[me] ? 1 : 0) + ":" + (s.lock[o] ? 1 : 0) + ":" + (s.confirm[me] ? 1 : 0) + ":" + (s.confirm[o] ? 1 : 0)
                + ":" + town.gold(p).getScore());
            for (int side = 0; side < 2; side++) {
                int owner = side == 0 ? me : o;
                for (int i = 0; i < SLOTS; i++) {
                    ItemStack it = s.items[owner][i];
                    if (it == null) continue;
                    String wire = ItemWire.encode(it.clone(), null);
                    if (wire != null) Armory.sendLine(p, "PIPI_TRADE_SLOT:" + side + ":" + i + ":" + wire);
                }
            }
            p.sendMessage("PIPI_TRADE_END");
        }
    }

    // ---- events ----
    @EventHandler public void quit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        UUID id = p.getUniqueId();
        requests.remove(id);
        requests.values().removeIf(r -> r.from().equals(id));
        Session s = sessions.get(id);
        if (s != null) cancel(s, p.getName() + "님이 접속을 종료해 거래가 취소되었습니다.", Set.of(id));
    }

    @EventHandler public void join(PlayerJoinEvent event) { recover(event.getPlayer()); }

    @EventHandler public void death(PlayerDeathEvent event) {
        Session s = sessions.get(event.getEntity().getUniqueId());
        if (s != null) cancel(s, event.getEntity().getName() + "님이 사망해 거래가 취소되었습니다.", new LinkedHashSet<>(List.of(event.getEntity().getUniqueId())));
    }

    @EventHandler public void respawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) recover(p); }, 2);
    }
}
