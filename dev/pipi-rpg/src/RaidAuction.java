package dev.pipi.life;

import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;

/**
 * 전리품 경매 (Lost Ark style) after a raid clear. Lots run one after another; each lot has a timer that a new bid extends.
 * Gold is checked when bidding and taken from the winner when the lot closes (falls back to the next highest bidder who is
 * online and can still pay, otherwise 유찰). Party auctions split the winning gold equally among the OTHER participants;
 * a solo auction just consumes it.
 *
 * Client protocol (PipiTravel AuctionScreen, hidden chat lines):
 *   PIPI_AUC_OPEN                                   open the window
 *   PIPI_AUC:raid|cur|remainMs|totalMs|bidder|price|minBid|gold|ended|lots   lots = name~state~winner~itemId~count;...
 *                                                    (state 0 대기, 1 진행 중, 2 낙찰, 3 유찰)
 *   PIPI_AUC_MSG:text                               feedback line (bid rejected etc.)
 *   PIPI_AUC_END                                    auction finished (window stays open showing results)
 * Client -> server: /pipilife auction open | bid <amount> | instant
 */
final class RaidAuction {
    static final class Lot {
        final String name;
        final ItemStack item;
        long start;
        int state; // 0 wait, 1 live, 2 sold, 3 unsold
        String winner = "";
        long finalPrice;
        final Map<UUID, Long> bids = new HashMap<>(); // each bidder's best bid
        UUID top;
        long price;
        Lot(String name, ItemStack item, long start) { this.name = name; this.item = item; this.start = start; }
    }

    private final PipiLife plugin;
    private final String raidName;
    final List<UUID> members = new ArrayList<>();
    final Map<UUID, String> names = new HashMap<>();
    final List<Lot> lots;
    final boolean solo;
    private final long step, lotMs, extendMs, gapMs;
    private int cur = -1;
    private long endAt, gapUntil, lastSync;
    private boolean done;

    RaidAuction(PipiLife plugin, String raidName, List<Player> players, List<Lot> lots, boolean solo, long step, long lotMs, long extendMs, long gapMs) {
        this.plugin = plugin;
        this.raidName = raidName;
        for (Player p : players) { members.add(p.getUniqueId()); names.put(p.getUniqueId(), p.getName()); }
        this.lots = lots;
        this.solo = solo;
        this.step = Math.max(1, step);
        this.lotMs = Math.max(3000, lotMs);
        this.extendMs = Math.max(0, extendMs);
        this.gapMs = Math.max(0, gapMs);
    }

    boolean done() { return done; }
    boolean member(UUID id) { return members.contains(id); }
    boolean involves(Collection<UUID> ids) { for (UUID id : ids) if (members.contains(id)) return true; return false; }

    private List<Player> online() {
        List<Player> out = new ArrayList<>();
        for (UUID id : members) { Player p = Bukkit.getPlayer(id); if (p != null && p.isOnline()) out.add(p); }
        return out;
    }

    /** Gold score by player name (works for offline participants too; same objective as TownSystems.gold). */
    private static Score gold(String name) {
        var board = Bukkit.getScoreboardManager().getMainScoreboard();
        Objective objective = board.getObjective("en_money");
        if (objective == null) objective = board.registerNewObjective("en_money", "dummy", "골드");
        return objective.getScore(name);
    }

    private static String fmt(long v) { return String.format(Locale.ROOT, "%,d", v); }

    // ---- lifecycle ----
    void start() {
        for (Player p : online()) p.sendMessage("PIPI_AUC_OPEN");
        startLot(0);
    }

    private void startLot(int i) {
        cur = i;
        Lot lot = lots.get(i);
        lot.state = 1;
        endAt = System.currentTimeMillis() + lotMs;
        gapUntil = 0;
        broadcast();
    }

    void tick() {
        if (done) return;
        long now = System.currentTimeMillis();
        if (gapUntil > 0) {
            if (now >= gapUntil) {
                if (cur + 1 < lots.size()) startLot(cur + 1); else finish();
            }
        } else if (cur >= 0 && now >= endAt) {
            resolve(lots.get(cur));
            gapUntil = now + gapMs;
            if (gapMs == 0) { if (cur + 1 < lots.size()) startLot(cur + 1); else finish(); }
            else broadcast();
        }
        if (!done && now - lastSync >= 1000) broadcast();
    }

    /** Ends the auction now: the running lot closes normally, lots that never started are 유찰. */
    void abort() {
        if (done) return;
        if (cur >= 0 && gapUntil == 0 && lots.get(cur).state == 1) resolve(lots.get(cur));
        for (Lot lot : lots) if (lot.state == 0) lot.state = 3;
        finish();
    }

    private void finish() {
        done = true;
        gapUntil = 0;
        broadcast();
        for (Player p : online()) {
            p.sendMessage("PIPI_AUC_END");
            p.sendMessage("§6[전리품 경매] §f경매가 끝났습니다.");
        }
    }

    private long minBid(Lot lot) { return lot.top == null ? lot.start : lot.price + step; }

    // ---- bidding ----
    void bid(Player p, long amount) {
        if (!member(p.getUniqueId())) { msg(p, "이 경매의 참여자가 아닙니다."); return; }
        if (done || cur < 0 || gapUntil > 0) { msg(p, "지금은 입찰할 수 없습니다."); return; }
        Lot lot = lots.get(cur);
        if (lot.state != 1) { msg(p, "지금은 입찰할 수 없습니다."); return; }
        if (p.getUniqueId().equals(lot.top)) { msg(p, "이미 최고 입찰자입니다."); return; }
        long min = minBid(lot);
        if (amount < min) { msg(p, "최소 입찰가는 " + fmt(min) + " 골드입니다."); return; }
        long have = gold(p.getName()).getScore();
        if (have < amount) { msg(p, "골드가 부족합니다. (소지금 " + fmt(have) + ")"); return; }
        lot.bids.put(p.getUniqueId(), amount);
        lot.top = p.getUniqueId();
        lot.price = amount;
        endAt = Math.max(endAt, System.currentTimeMillis() + extendMs);
        for (Player o : online()) o.playSound(o.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, .7f, o == p ? 1.6f : 1.1f);
        broadcast();
    }

    void instant(Player p) {
        if (done || cur < 0 || gapUntil > 0) { msg(p, "지금은 입찰할 수 없습니다."); return; }
        bid(p, minBid(lots.get(cur)));
    }

    private void resolve(Lot lot) {
        List<Map.Entry<UUID, Long>> order = new ArrayList<>(lot.bids.entrySet());
        order.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        for (Map.Entry<UUID, Long> e : order) {
            Player w = Bukkit.getPlayer(e.getKey());
            long price = e.getValue();
            if (w == null || !w.isOnline()) continue;
            Score sc = gold(w.getName());
            if (sc.getScore() < price) { msg(w, "골드가 부족해 낙찰되지 못했습니다."); continue; }
            sc.setScore((int) (sc.getScore() - price));
            give(w, lot.item);
            lot.state = 2;
            lot.winner = w.getName();
            lot.finalPrice = price;
            lot.top = w.getUniqueId();
            lot.price = price;
            w.playSound(w.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, .8f, 1.2f);
            List<UUID> others = new ArrayList<>(members);
            others.remove(w.getUniqueId());
            long share = solo || others.isEmpty() ? 0 : price / others.size();
            for (Player o : online()) o.sendMessage("§6[전리품 경매] §f" + lot.name + " §7→ §e" + w.getName() + " §7낙찰 (" + fmt(price) + " 골드)"
                + (share > 0 ? " §7· 분배금 1인당 " + fmt(share) : ""));
            if (share > 0) for (UUID id : others) {
                String name = names.get(id);
                if (name == null) continue;
                Score os = gold(name);
                os.setScore((int) Math.min(Integer.MAX_VALUE, (long) os.getScore() + share));
            }
            return;
        }
        lot.state = 3;
        lot.top = null;
        for (Player o : online()) o.sendMessage("§6[전리품 경매] §f" + lot.name + " §7유찰");
    }

    private static void give(Player p, ItemStack it) {
        int left = it.getAmount(), max = Math.max(1, Math.min(64, it.getMaxStackSize()));
        while (left > 0) {
            ItemStack c = it.clone();
            c.setAmount(Math.min(max, left));
            left -= c.getAmount();
            for (ItemStack extra : p.getInventory().addItem(c).values()) p.getWorld().dropItemNaturally(p.getLocation(), extra).setOwner(p.getUniqueId());
        }
    }

    // ---- client sync ----
    private static void msg(Player p, String text) { p.sendMessage("PIPI_AUC_MSG:" + text); }

    void open(Player p) {
        if (done || !member(p.getUniqueId())) { p.sendMessage("진행 중인 전리품 경매가 없습니다."); return; }
        p.sendMessage("PIPI_AUC_OPEN");
        send(p);
    }

    private void broadcast() {
        lastSync = System.currentTimeMillis();
        for (Player p : online()) send(p);
    }

    private static String clean(String s) { return s.replace("|", "/").replace(";", ",").replace("~", "-"); }

    private void send(Player p) {
        Lot lot = cur >= 0 ? lots.get(cur) : null;
        long now = System.currentTimeMillis();
        long remain = done || lot == null || gapUntil > 0 ? 0 : Math.max(0, endAt - now);
        long total = Math.max(lotMs, remain);
        String bidder = lot == null || lot.top == null ? "-" : names.getOrDefault(lot.top, "-");
        long price = lot == null ? 0 : lot.top == null ? 0 : lot.price;
        long min = lot == null ? 0 : minBid(lot);
        StringBuilder b = new StringBuilder("PIPI_AUC:").append(clean(raidName)).append('|').append(Math.max(0, cur)).append('|').append(remain).append('|').append(total)
            .append('|').append(bidder).append('|').append(price).append('|').append(min).append('|').append(gold(p.getName()).getScore()).append('|').append(done ? 1 : 0).append('|');
        for (int i = 0; i < lots.size(); i++) {
            Lot l = lots.get(i);
            if (i > 0) b.append(';');
            String id;
            try { id = l.item.getType().getKey().toString(); } catch (RuntimeException ex) { id = "minecraft:barrier"; }
            b.append(clean(l.name)).append('~').append(l.state).append('~').append(l.winner).append('~').append(id).append('~').append(l.item.getAmount());
        }
        p.sendMessage(b.toString());
    }
}
