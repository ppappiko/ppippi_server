package dev.pipi.life;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * In-memory parties (max 4). The inviter becomes the leader (a new party is created when the inviter had none).
 * Client protocol (hidden chat, PipiTravel cancels these lines):
 *   PIPI_PINVITE:&lt;inviter&gt;:&lt;seconds&gt;  / PIPI_PINVITE_END   - invite popup (Y = accept, N = decline)
 *   PIPI_PARTY:name,cls,hp,max,same,leader,online;...      - party list HUD (cls w|a|m|-, same = same world as the viewer), empty = hide
 *   PIPI_CHATMODE:party|all                                - chat mode tag shown above the chat input
 * Commands: /pipilife party invite &lt;name&gt; | accept | decline | leave | kick &lt;name&gt; | promote &lt;name&gt; | list
 * Party chat: /p (switch to party chat) · /p &lt;msg&gt; (one party message) · /a (switch back to all) · /a &lt;msg&gt; (one public message)
 */
final class PartySystem implements Listener {
    static final int MAX = 4;
    private static final long INVITE_MS = 20_000;

    static final class Party {
        UUID leader;
        final LinkedHashSet<UUID> members = new LinkedHashSet<>();
    }
    private record Invite(UUID inviter, long expires) {}

    private final PipiLife plugin;
    private final Map<UUID, Party> byMember = new HashMap<>();
    private final Map<UUID, Invite> invites = new HashMap<>();
    private final Set<UUID> hudShown = new HashSet<>();
    /** Players whose plain chat goes to the party (read from the async chat thread). */
    private final Set<UUID> partyChat = ConcurrentHashMap.newKeySet();
    /** One public message sent with /a while in party mode: the chat listener lets it through. */
    private final Set<UUID> chatBypass = ConcurrentHashMap.newKeySet();

    PartySystem(PipiLife plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 20L);
        registerChatCommands();
    }

    // ---- queries ----
    Party of(Player p) { return byMember.get(p.getUniqueId()); }
    Party of(UUID id) { return byMember.get(id); }
    UUID leader(Player p) { Party party = of(p); return party == null ? null : party.leader; }

    /** Online members of the player's party (the player alone when not in a party); the player comes first. */
    List<Player> online(Player player) {
        List<Player> out = new ArrayList<>(List.of(player));
        Party party = of(player);
        if (party != null) for (UUID id : party.members) {
            Player other = Bukkit.getPlayer(id);
            if (other != null && other.isOnline() && !other.equals(player)) out.add(other);
        }
        return out;
    }

    private static String name(UUID id) {
        Player p = Bukkit.getPlayer(id);
        if (p != null) return p.getName();
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n == null ? id.toString().substring(0, 8) : n;
    }

    private void tellParty(Party party, String msg) {
        for (UUID id : party.members) { Player p = Bukkit.getPlayer(id); if (p != null) p.sendMessage(msg); }
    }

    // ---- commands ----
    boolean command(Player player, String[] args) {
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "list";
        switch (action) {
            case "invite" -> {
                Player target = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : null;
                if (target == null) player.sendMessage("§c접속 중인 플레이어 이름을 입력하세요. /pipilife party invite <이름>");
                else invite(player, target);
            }
            case "accept" -> accept(player);
            case "decline", "deny" -> decline(player);
            case "leave" -> { if (of(player) == null) player.sendMessage("파티에 속해 있지 않습니다."); else { leave(player.getUniqueId()); player.sendMessage("파티에서 나왔습니다."); } }
            case "kick" -> kick(player, args.length > 2 ? args[2] : null);
            case "promote" -> promote(player, args.length > 2 ? args[2] : null);
            case "create" -> {
                if (of(player) != null) player.sendMessage("이미 파티에 속해 있습니다.");
                else { create(player.getUniqueId()); player.sendMessage("파티를 만들었습니다."); push(of(player)); }
            }
            case "list" -> list(player);
            default -> player.sendMessage("/pipilife party invite <이름> · accept · decline · leave · kick <이름> · promote <이름> · list");
        }
        return true;
    }

    List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 2) return new ArrayList<>(List.of("invite", "accept", "decline", "leave", "kick", "promote", "list"));
        if (args.length == 3 && (args[1].equalsIgnoreCase("invite") || args[1].equalsIgnoreCase("kick") || args[1].equalsIgnoreCase("promote"))) {
            List<String> out = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) if (!p.equals(sender)) out.add(p.getName());
            return out;
        }
        return List.of();
    }

    private Party create(UUID leader) {
        Party party = new Party();
        party.leader = leader;
        party.members.add(leader);
        byMember.put(leader, party);
        return party;
    }

    void invite(Player from, Player target) {
        if (target.equals(from)) { from.sendMessage("§c자기 자신은 초대할 수 없습니다."); return; }
        Party party = of(from);
        if (party != null && !party.leader.equals(from.getUniqueId())) { from.sendMessage("§c파티장만 초대할 수 있습니다."); return; }
        if (party != null && party.members.size() >= MAX) { from.sendMessage("§c파티는 최대 " + MAX + "명입니다."); return; }
        if (of(target) != null) { from.sendMessage("§c" + target.getName() + "님은 이미 파티에 속해 있습니다."); return; }
        Invite old = invites.get(target.getUniqueId());
        if (old != null && old.expires > System.currentTimeMillis() && old.inviter.equals(from.getUniqueId())) { from.sendMessage("§7이미 초대를 보냈습니다."); return; }
        invites.put(target.getUniqueId(), new Invite(from.getUniqueId(), System.currentTimeMillis() + INVITE_MS));
        target.sendMessage("PIPI_PINVITE:" + from.getName() + ":" + INVITE_MS / 1000);
        target.sendMessage("§d" + from.getName() + "님이 파티에 초대했습니다. §7(Y 수락 · N 거절 · /pipilife party accept|decline)");
        from.sendMessage("§d" + target.getName() + "님에게 파티 초대를 보냈습니다.");
    }

    private void accept(Player player) {
        Invite inv = invites.remove(player.getUniqueId());
        player.sendMessage("PIPI_PINVITE_END");
        if (inv == null || inv.expires < System.currentTimeMillis()) { player.sendMessage("§c유효한 파티 초대가 없습니다."); return; }
        if (of(player) != null) { player.sendMessage("§c이미 파티에 속해 있습니다."); return; }
        Player inviter = Bukkit.getPlayer(inv.inviter);
        if (inviter == null) { player.sendMessage("§c초대한 플레이어가 접속 중이 아닙니다."); return; }
        Party party = of(inviter);
        if (party == null) party = create(inviter.getUniqueId());
        else if (!party.leader.equals(inviter.getUniqueId())) { player.sendMessage("§c초대가 더 이상 유효하지 않습니다."); return; }
        if (party.members.size() >= MAX) { player.sendMessage("§c파티 인원이 가득 찼습니다."); return; }
        party.members.add(player.getUniqueId());
        byMember.put(player.getUniqueId(), party);
        tellParty(party, "§d" + player.getName() + "님이 파티에 참가했습니다. §7(" + party.members.size() + "/" + MAX + ")");
        push(party);
    }

    private void decline(Player player) {
        Invite inv = invites.remove(player.getUniqueId());
        player.sendMessage("PIPI_PINVITE_END");
        if (inv == null) { player.sendMessage("§7받은 파티 초대가 없습니다."); return; }
        player.sendMessage("§7파티 초대를 거절했습니다.");
        Player inviter = Bukkit.getPlayer(inv.inviter);
        if (inviter != null) inviter.sendMessage("§7" + player.getName() + "님이 파티 초대를 거절했습니다.");
    }

    private void kick(Player player, String targetName) {
        Party party = of(player);
        if (party == null || !party.leader.equals(player.getUniqueId())) { player.sendMessage("§c파티장만 추방할 수 있습니다."); return; }
        UUID target = null;
        for (UUID id : party.members) if (name(id).equalsIgnoreCase(String.valueOf(targetName))) target = id;
        if (target == null || target.equals(player.getUniqueId())) { player.sendMessage("§c파티원 이름을 입력하세요. /pipilife party kick <이름>"); return; }
        Player t = Bukkit.getPlayer(target);
        tellParty(party, "§7" + name(target) + "님이 파티에서 추방되었습니다.");
        leave(target);
        if (t != null) t.sendMessage("§c파티에서 추방되었습니다.");
    }

    private void promote(Player player, String targetName) {
        Party party = of(player);
        if (party == null || !party.leader.equals(player.getUniqueId())) { player.sendMessage("§c파티장만 위임할 수 있습니다."); return; }
        UUID target = null;
        for (UUID id : party.members) if (name(id).equalsIgnoreCase(String.valueOf(targetName))) target = id;
        if (target == null || target.equals(player.getUniqueId())) { player.sendMessage("§c파티원 이름을 입력하세요. /pipilife party promote <이름>"); return; }
        if (Bukkit.getPlayer(target) == null) { player.sendMessage("§c접속 중인 파티원에게만 위임할 수 있습니다."); return; }
        party.leader = target;
        invites.values().removeIf(inv -> inv.inviter.equals(player.getUniqueId()));
        tellParty(party, "§d" + name(target) + "님이 새 파티장이 되었습니다.");
        push(party);
    }

    /**
     * Party slice of the player menu, seen by {@code viewer} looking at {@code target}:
     * I = can invite, K = same party and viewer leads (kick / promote), M = same party, X|reason = cannot invite.
     */
    String menuState(Player viewer, Player target) {
        Party mine = of(viewer), theirs = of(target);
        if (mine != null && mine == theirs) return mine.leader.equals(viewer.getUniqueId()) ? "K" : "M";
        if (theirs != null) return "X|이미 다른 파티에 속해 있습니다";
        if (mine != null && !mine.leader.equals(viewer.getUniqueId())) return "X|파티장만 초대할 수 있습니다";
        if (mine != null && mine.members.size() >= MAX) return "X|파티 인원이 가득 찼습니다 (" + MAX + "명)";
        return "I";
    }

    private void list(Player player) {
        Party party = of(player);
        if (party == null) { player.sendMessage("파티에 속해 있지 않습니다. 플레이어 우클릭 → 파티 초대"); return; }
        List<String> names = new ArrayList<>();
        for (UUID id : party.members) names.add((id.equals(party.leader) ? "★" : "") + name(id) + (Bukkit.getPlayer(id) == null ? "(오프라인)" : ""));
        player.sendMessage("§d파티 §f(" + party.members.size() + "/" + MAX + ") · " + String.join(", ", names));
    }

    /** Removes a member; the next member becomes leader; the party disbands at one member. */
    void leave(UUID id) {
        Party party = byMember.remove(id);
        if (party == null) return;
        party.members.remove(id);
        hide(id);
        resetChat(id);
        if (party.members.size() <= 1) {
            for (UUID m : party.members) {
                byMember.remove(m);
                hide(m);
                resetChat(m);
                Player p = Bukkit.getPlayer(m);
                if (p != null) p.sendMessage("§7파티가 해산되었습니다.");
            }
            party.members.clear();
            invites.values().removeIf(inv -> inv.inviter.equals(id));
            return;
        }
        if (id.equals(party.leader)) {
            party.leader = party.members.iterator().next();
            tellParty(party, "§d" + name(party.leader) + "님이 새 파티장이 되었습니다.");
            invites.values().removeIf(inv -> inv.inviter.equals(id));
        }
        tellParty(party, "§7" + name(id) + "님이 파티를 떠났습니다.");
        push(party);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void quit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        invites.remove(id);
        hudShown.remove(id);
        leave(id);
        partyChat.remove(id);
        chatBypass.remove(id);
    }

    // ---- party chat ----
    /** /p and /a: plugin.yml comes from the base jar, so they are registered at runtime (taking the label over if another plugin has it). */
    private void registerChatCommands() {
        try {
            var map = (org.bukkit.command.CommandMap) Bukkit.getServer().getClass().getMethod("getCommandMap").invoke(Bukkit.getServer());
            Command p = new Command("p", "파티 채팅: /p 로 전환, /p <메시지> 로 한 번 보내기", "/p [메시지]", List.of("pc")) {
                @Override public boolean execute(CommandSender sender, String label, String[] args) {
                    if (sender instanceof Player player) partyCommand(player, String.join(" ", args).trim());
                    return true;
                }
            };
            Command a = new Command("a", "전체 채팅: /a 로 전환, /a <메시지> 로 한 번 보내기", "/a [메시지]", List.of("ac")) {
                @Override public boolean execute(CommandSender sender, String label, String[] args) {
                    if (sender instanceof Player player) allCommand(player, String.join(" ", args).trim());
                    return true;
                }
            };
            for (Command c : List.of(p, a)) {
                map.register("pipilife", c);
                try { // another plugin may already own the bare label: take it over
                    @SuppressWarnings("unchecked")
                    Map<String, Command> known = (Map<String, Command>) map.getClass().getMethod("getKnownCommands").invoke(map);
                    known.put(c.getName(), c);
                } catch (ReflectiveOperationException | RuntimeException ignored) { }
            }
            for (Player online : Bukkit.getOnlinePlayers()) online.updateCommands();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            plugin.getLogger().warning("/p, /a 명령을 등록하지 못했습니다: " + ex);
        }
    }

    private void partyCommand(Player player, String message) {
        Party party = of(player);
        if (party == null) { player.sendMessage("§c파티에 속해 있지 않습니다. §7(플레이어 우클릭 → 파티 초대)"); return; }
        if (message.isEmpty()) {
            if (partyChat.add(player.getUniqueId())) player.sendMessage("§d파티 채팅으로 전환했습니다. §7(/a 전체 채팅)");
            else player.sendMessage("§7이미 파티 채팅입니다. (/a 전체 채팅)");
            player.sendMessage("PIPI_CHATMODE:party");
            return;
        }
        sendParty(player, party, message);
    }

    private void allCommand(Player player, String message) {
        if (message.isEmpty()) {
            if (partyChat.remove(player.getUniqueId())) player.sendMessage("§f전체 채팅으로 전환했습니다. §7(/p 파티 채팅)");
            else player.sendMessage("§7이미 전체 채팅입니다.");
            player.sendMessage("PIPI_CHATMODE:all");
            return;
        }
        chatBypass.add(player.getUniqueId());
        try { player.chat(message); } finally { chatBypass.remove(player.getUniqueId()); }
    }

    private void sendParty(Player from, Party party, String message) {
        tellParty(party, "§d[파티] §f" + from.getName() + "§7: §f" + message);
        plugin.getLogger().info("[파티] " + from.getName() + ": " + message);
    }

    private void resetChat(UUID id) {
        if (!partyChat.remove(id)) return;
        Player p = Bukkit.getPlayer(id);
        if (p != null) { p.sendMessage("§7파티가 없어 전체 채팅으로 전환했습니다."); p.sendMessage("PIPI_CHATMODE:all"); }
    }

    /** Party-mode chat goes to the party instead (async thread: only the concurrent set is read here). */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void chat(AsyncPlayerChatEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (!partyChat.contains(id) || chatBypass.contains(id)) return;
        event.setCancelled(true);
        String message = event.getMessage();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(id);
            if (player == null) return;
            Party party = of(player);
            if (party == null) { resetChat(id); player.sendMessage("§c파티가 없어 메시지를 보내지 못했습니다: §7" + message); return; }
            sendParty(player, party, message);
        });
    }

    // ---- HUD sync ----
    private void hide(UUID id) {
        Player p = Bukkit.getPlayer(id);
        if (p != null && hudShown.remove(id)) p.sendMessage("PIPI_PARTY:");
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<UUID, Invite>> it = invites.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            if (en.getValue().expires >= now) continue;
            it.remove();
            Player t = Bukkit.getPlayer(en.getKey());
            if (t != null) { t.sendMessage("PIPI_PINVITE_END"); t.sendMessage("§7파티 초대가 만료되었습니다."); }
        }
        Set<Party> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Party party : byMember.values()) if (seen.add(party)) push(party);
    }

    /** Class of the equipped (or held) weapon: w / a / m, "-" when none. */
    static String classOf(Player p) {
        ItemStack it = Armory.weaponFor(p);
        if (it == null || it.getType().isAir()) it = p.getInventory().getItemInMainHand();
        if (it == null) return "-";
        Material m = it.getType();
        if (m.name().endsWith("_SWORD")) return "w";
        if (m == Material.BOW || m == Material.CROSSBOW) return "a";
        if (m == Material.BLAZE_ROD && it.hasItemMeta()
            && it.getItemMeta().getPersistentDataContainer().has(NamespacedKey.fromString("pipilife:smith_catalog"), PersistentDataType.STRING)) return "m";
        return "-";
    }

    void push(Party party) {
        if (party == null || party.members.isEmpty()) return;
        List<Player> on = new ArrayList<>();
        for (UUID id : party.members) { Player p = Bukkit.getPlayer(id); if (p != null && p.isOnline()) on.add(p); }
        for (Player viewer : on) {
            StringBuilder sb = new StringBuilder("PIPI_PARTY:");
            boolean first = true;
            for (UUID id : party.members) {
                Player m = Bukkit.getPlayer(id);
                if (!first) sb.append(';');
                first = false;
                String n = name(id).replace(",", "").replace(";", "");
                if (m == null || !m.isOnline()) { sb.append(n).append(",-,0,1,0,").append(id.equals(party.leader) ? 1 : 0).append(",0"); continue; }
                var attr = m.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                long max = Math.max(1, Math.round(attr == null ? 20 : attr.getValue()));
                long hp = m.isDead() ? 0 : Math.max(1, (long) Math.ceil(m.getHealth() - 1e-9));
                sb.append(n).append(',').append(classOf(m)).append(',').append(Math.min(hp, max)).append(',').append(max).append(',')
                    .append(m.getWorld().equals(viewer.getWorld()) ? 1 : 0).append(',').append(id.equals(party.leader) ? 1 : 0).append(",1");
            }
            viewer.sendMessage(sb.toString());
            hudShown.add(viewer.getUniqueId());
        }
    }
}
