package dev.pipi.life;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Sends hidden HUD data to the client: PIPI_GOLD:<n> and PIPI_QUEST:<title>|<line>|... (polled, sent on change). */
final class HudSync {
    private final PipiLife plugin;
    private final TownSystems town;
    private final Map<UUID, Integer> sentGold = new HashMap<>();
    private final Map<UUID, String> sentQuest = new HashMap<>();
    private int ticks;

    HudSync(PipiLife plugin, TownSystems town) {
        this.plugin = plugin;
        this.town = town;
    }

    void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            ticks++;
            sentGold.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
            sentQuest.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
            boolean resend = ticks % 30 == 0; // every 30 s heal a dropped message
            for (Player p : Bukkit.getOnlinePlayers()) {
                try { sync(p, resend); } catch (RuntimeException ignored) { }
            }
        }, 20L, 20L);
        // quest lines every 2 s
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                try { syncQuest(p, false); } catch (RuntimeException ignored) { }
            }
        }, 30L, 40L);
    }

    private void sync(Player p, boolean resend) {
        int gold = town.gold(p).getScore();
        Integer old = sentGold.put(p.getUniqueId(), gold);
        if (resend || old == null || old != gold) p.sendMessage("PIPI_GOLD:" + gold);
        if (resend) syncQuest(p, true);
    }

    private void syncQuest(Player p, boolean force) {
        String text = quest(p);
        String old = sentQuest.get(p.getUniqueId());
        if (old == null && text.isEmpty() && !force) return;
        if (!force && text.equals(old)) return;
        sentQuest.put(p.getUniqueId(), text);
        p.sendMessage("PIPI_QUEST:" + text);
    }

    private String quest(Player p) {
        List<String> lines = new ArrayList<>();
        String title = "";
        if (p.getWorld().getName().equals(VillageMaps.TUTORIAL) && !plugin.tutorialComplete(p)) {
            title = "튜토리얼";
            lines.add("마을을 둘러보며 NPC와 대화하기");
            lines.add("시설 설치법 익히기");
            lines.add("준비되면 /pipilife finish");
        } else if (plugin.tutorialComplete(p)) {
            var data = plugin.data();
            int storage = Math.clamp(data.getInt("pet-storage." + p.getUniqueId() + ".level", 1), 1, 5);
            if (storage < 3) lines.add("펫 창고 레벨업 (Lv." + storage + "/3)");
            if (plugin.craftLevel(p) <= 0) lines.add("제작 담당 펫 배정하기");
            if (!lines.isEmpty()) {
                title = "목표";
                if (lines.size() < 5) lines.add("야생에서 펫 포획하기");
            }
        }
        if (title.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(title);
        for (String l : lines.subList(0, Math.min(5, lines.size()))) sb.append('|').append(l);
        return sb.toString();
    }
}
