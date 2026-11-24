package dev.pipi.facilities;

import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.CreateGroupEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.JoinGroupEvent;
import de.maxhenkel.voicechat.api.events.LeaveGroupEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 파티 음성 (Simple Voice Chat 연동, 서버 전용).
 * PipiLife(PartySystem) 가 매초 System properties "pipilife.party-voice" 에 올리는 "플레이어 UUID → 파티 id" 표를 읽어,
 * 파티마다 숨겨진 ISOLATED 그룹(그룹 id = 파티 id)을 만들고 파티원을 넣는다.
 * - 파티원끼리는 거리·월드와 관계없이 서로 들리고, 파티 밖 소리는 듣지 않는다 (ISOLATED).
 * - 파티가 없는 플레이어는 SVC 기본 근거리 음성을 쓴다.
 * - 플레이어가 직접 그룹을 만들거나(G 메뉴) 다른 그룹에 들어가거나 파티 그룹에서 나가는 것은 막는다.
 * SVC 가 없으면 이 클래스는 로드되지 않는다 (@ForgeVoicechatPlugin 은 SVC 가 찾아 생성한다).
 */
@ForgeVoicechatPlugin
public final class PartyVoice implements VoicechatPlugin {
    private static final String KEY = "pipilife.party-voice";
    private static final int PERIOD_TICKS = 20;

    private volatile VoicechatServerApi api;
    /** Last party table read from PipiLife (player -> party id). */
    private volatile Map<UUID, UUID> parties = Map.of();
    private int ticks;

    @Override public String getPluginId() { return "pipifacilities_party_voice"; }

    @Override public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, this::started);
        registration.registerEvent(CreateGroupEvent.class, this::create);
        registration.registerEvent(JoinGroupEvent.class, this::join);
        registration.registerEvent(LeaveGroupEvent.class, this::leave);
    }

    private void started(VoicechatServerStartedEvent event) {
        boolean first = api == null;
        api = event.getVoicechat();
        if (first) NeoForge.EVENT_BUS.addListener(this::tick);
    }

    // ---- 플레이어 조작 제한 ----

    /** 그룹은 파티 그룹만 (API 로 만든 그룹은 connection 이 null). */
    private void create(CreateGroupEvent event) {
        if (event.getConnection() != null) event.cancel();
    }

    private void join(JoinGroupEvent event) {
        UUID expected = parties.get(event.getConnection().getPlayer().getUuid());
        if (event.getGroup() == null || !event.getGroup().getId().equals(expected)) event.cancel();
    }

    private void leave(LeaveGroupEvent event) {
        Group g = event.getGroup();
        if (g != null && g.getId().equals(parties.get(event.getConnection().getPlayer().getUuid()))) event.cancel();
    }

    // ---- 동기화 (서버 스레드, 1초마다) ----

    private void tick(ServerTickEvent.Post event) {
        if (++ticks % PERIOD_TICKS != 0) return;
        VoicechatServerApi a = api;
        if (a == null) return;
        try { sync(a, event.getServer()); }
        catch (RuntimeException ex) { if (ticks % (PERIOD_TICKS * 60) == 0) System.err.println("[PipiFacilities] party voice sync failed: " + ex); }
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, UUID> readParties() {
        Object o = System.getProperties().get(KEY);
        if (!(o instanceof Map<?, ?> m)) return Map.of();
        try { return (Map<UUID, UUID>) m; } catch (ClassCastException ex) { return Map.of(); }
    }

    private void sync(VoicechatServerApi a, MinecraftServer server) {
        Map<UUID, UUID> table = readParties();
        parties = table;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            VoicechatConnection c = a.getConnectionOf(p.getUUID());
            if (c == null) continue;
            UUID expected = table.get(p.getUUID());
            Group current = c.getGroup();
            UUID currentId = current == null ? null : current.getId();
            if (Objects.equals(expected, currentId)) continue;
            c.setGroup(expected == null ? null : group(a, expected));
        }
    }

    private Group group(VoicechatServerApi a, UUID partyId) {
        Group g = a.getGroup(partyId);
        return g != null ? g
            : a.groupBuilder().setId(partyId).setName("Party").setPersistent(false).setHidden(true).setType(Group.Type.ISOLATED).build();
    }
}
