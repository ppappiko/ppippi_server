package dev.pipi.life;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.ItemTag;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.TranslatableComponent;
import net.md_5.bungee.api.chat.hover.content.Item;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Social features: /자랑 (item brag with hover tooltip) and the right-click radial player menu (거래 / 파티 / 상태창 / 귓속말). */
final class Social implements Listener {
    private final PipiLife plugin;
    private final TownSystems town;
    private final CombatStats combat;
    private final Map<UUID, UUID> pendingPrice = new HashMap<>();
    private final Map<UUID, Long> pendingExpires = new HashMap<>();
    private final Map<UUID, Long> lastBrag = new HashMap<>();
    private final Map<UUID, Long> lastMenu = new HashMap<>();
    TradeWindow trades;

    Social(PipiLife plugin, TownSystems town, CombatStats combat) {
        this.plugin = plugin;
        this.town = town;
        this.combat = combat;
        registerBragCommand();
    }

    /** plugin.yml comes from the base jar, so the Korean alias is registered at runtime. */
    private void registerBragCommand() {
        try {
            Command command = new Command("자랑", "손에 든 아이템을 모두에게 자랑합니다", "/자랑", List.of("brag")) {
                @Override public boolean execute(CommandSender sender, String label, String[] args) {
                    if (sender instanceof Player player) brag(player);
                    else sender.sendMessage("플레이어만 사용할 수 있습니다.");
                    return true;
                }
            };
            var map = (org.bukkit.command.CommandMap) Bukkit.getServer().getClass().getMethod("getCommandMap").invoke(Bukkit.getServer());
            map.register("pipilife", command);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            plugin.getLogger().warning("/자랑 명령을 등록하지 못했습니다 (/pipilife brag 사용 가능): " + ex);
        }
    }

    boolean brag(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) {
            player.sendMessage("§c자랑할 아이템을 손에 드세요.");
            return true;
        }
        long now = System.currentTimeMillis();
        if (now - lastBrag.getOrDefault(player.getUniqueId(), 0L) < 3000) {
            player.sendMessage("§c잠시 후에 다시 자랑하세요.");
            return true;
        }
        lastBrag.put(player.getUniqueId(), now);
        ItemStack shown = item.clone();
        CombatStats.markOwner(shown, player.getName());
        if (broadcastHidden(shown, player)) return true;
        BaseComponent name = shown.hasItemMeta() && shown.getItemMeta().hasDisplayName()
            ? new TextComponent(TextComponent.fromLegacyText(shown.getItemMeta().getDisplayName()))
            : new TranslatableComponent(shown.getType().getItemTranslationKey());
        TextComponent part = new TextComponent("[");
        part.setColor(ChatColor.AQUA);
        part.addExtra(name);
        part.addExtra(" ×" + shown.getAmount() + "]");
        try {
            String nbt = shown.hasItemMeta() ? shown.getItemMeta().getAsComponentString() : "[]";
            part.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ITEM,
                new Item(shown.getType().getKey().toString(), shown.getAmount(), ItemTag.ofNbt(nbt))));
        } catch (RuntimeException | LinkageError ex) {
            plugin.getLogger().warning("자랑 툴팁을 만들지 못했습니다: " + ex);
        }
        TextComponent line = new TextComponent(player.getName() + "님이 ");
        line.setColor(ChatColor.YELLOW);
        line.addExtra(part);
        TextComponent tail = new TextComponent("을(를) 자랑합니다!");
        tail.setColor(ChatColor.YELLOW);
        line.addExtra(tail);
        Bukkit.spigot().broadcast(line);
        return true;
    }

    // ---- hidden-message brag: the client mod rebuilds the item and its hover ----
    private boolean broadcastHidden(ItemStack shown, Player player) {
        String name = player.getName();
        String enc = ItemWire.encode(shown, player);
        if (enc == null) return false;
        String message = "PIPI_BRAG:" + name + ":" + enc;
        for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage(message);
        Bukkit.getConsoleSender().sendMessage(name + "님이 [" + shown.getType().getKey() + " ×" + shown.getAmount() + "]을(를) 자랑합니다!");
        return true;
    }

    // ---- right-click a player: radial player menu (PipiTravel) ----
    /**
     * Right-click (outside combat mode) or Shift+right-click (always) another player opens the client radial menu:
     *   PIPI_PMENU:&lt;name&gt;|&lt;party&gt;[|reason]   party: I invite · K kick/promote (viewer leads) · M same party · X cannot invite
     * The client answers with /pipilife social trade|info &lt;name&gt; or /pipilife party invite|kick|promote &lt;name&gt;.
     */
    @EventHandler(ignoreCancelled = true)
    public void interact(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Player target)
            || target.hasMetadata("NPC")) return;
        Player player = event.getPlayer();
        if (target.equals(player) || (!player.isSneaking() && Armory.inCombatMode(player))) return;
        event.setCancelled(true);
        long now = System.currentTimeMillis();
        if (now - lastMenu.getOrDefault(player.getUniqueId(), 0L) < 300) return;
        lastMenu.put(player.getUniqueId(), now);
        player.sendMessage("PIPI_PMENU:" + target.getName() + "|" + town.partySystem.menuState(player, target));
    }

    /** /pipilife social trade|info &lt;name&gt; (sent by the radial menu). */
    boolean command(Player player, String[] args) {
        if (args.length < 3) { player.sendMessage("/pipilife social trade|info <이름>"); return true; }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null || target.equals(player)) { player.sendMessage("§c상대가 접속 중이 아닙니다."); return true; }
        switch (args[1]) {
            case "trade" -> {
                if (trades != null) trades.request(player, target);
                else player.sendMessage("§c거래창을 사용할 수 없습니다.");
            }
            case "info" -> combat.open(player, target);
            default -> player.sendMessage("/pipilife social trade|info <이름>");
        }
        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void chat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID targetId = pendingPrice.get(player.getUniqueId());
        if (targetId == null) return;
        event.setCancelled(true);
        String text = event.getMessage().trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            long expires = pendingExpires.getOrDefault(player.getUniqueId(), 0L);
            pendingPrice.remove(player.getUniqueId());
            pendingExpires.remove(player.getUniqueId());
            Player target = Bukkit.getPlayer(targetId);
            if (text.equals("취소") || expires < System.currentTimeMillis() || target == null) {
                player.sendMessage("§7거래 제안을 취소했습니다.");
                return;
            }
            try {
                int price = Integer.parseInt(text.replace(",", ""));
                town.command(player, new String[]{"trade", "offer", target.getName(), Integer.toString(price)});
            } catch (NumberFormatException ex) {
                player.sendMessage("§c골드를 정수로 입력하세요. 다시 시도하려면 상대를 우클릭하세요.");
            }
        });
    }

    @EventHandler public void quit(PlayerQuitEvent event) {
        pendingPrice.remove(event.getPlayer().getUniqueId());
        pendingExpires.remove(event.getPlayer().getUniqueId());
        lastBrag.remove(event.getPlayer().getUniqueId());
        lastMenu.remove(event.getPlayer().getUniqueId());
    }
}
