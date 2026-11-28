package dev.pipi.travel;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/**
 * 말하는 사람 표시 (Simple Voice Chat, VoiceTalk 가 값을 넣는다).
 * - 말하는 동안 머리(와 모자 레이어)가 커지고 목소리 크기에 맞춰 출렁인다. 커스텀 투구(PipiArmor)는 머리 뼈를 따라가므로 함께 커진다.
 * - 머리 위 아이콘: 보라 마이크 = 파티 음성, 흰 마이크 = 주변 음성.
 * - 파티 HUD(PartyHud) 이름 옆에도 말하는 중인 파티원을 표시한다 (멀리 있어 안 보이는 파티원용).
 */
final class VoiceFx {
    private static final ResourceLocation PARTY = ResourceLocation.fromNamespaceAndPath("pipitravel", "textures/gui/voice_party.png");
    private static final ResourceLocation NEAR = ResourceLocation.fromNamespaceAndPath("pipitravel", "textures/gui/voice_near.png");
    /** 마지막 음성 패킷 뒤 이만큼은 "말하는 중"으로 본다 (패킷은 약 20ms 간격). */
    private static final long HOLD_MS = 250;
    /** 말하는 동안 기본으로 커지는 비율과, 목소리 크기에 따라 더 커지는 최대 비율. */
    private static final float BASE_GROW = .25f, LEVEL_GROW = .75f;

    private record Talk(long at, float level, boolean party) {}

    /** 오디오 스레드가 쓰고 렌더 스레드가 읽는다. */
    private static final Map<UUID, Talk> TALK = new ConcurrentHashMap<>();
    /** 렌더 스레드 전용: 플레이어별 {말하는 중 0..1, 목소리 크기 0..1} 부드럽게 따라가는 값. */
    private static final Map<UUID, float[]> SMOOTH = new HashMap<>();
    private static PlayerModel<?> scaledModel;

    private VoiceFx() {}

    // ---- SVC (오디오 스레드) ----

    static void heard(UUID id, short[] audio, boolean party) {
        if (id == null || audio == null) return;
        TALK.put(id, new Talk(System.currentTimeMillis(), level(audio), party));
    }

    static void self(short[] audio) {
        Player p = Minecraft.getInstance().player;
        if (p != null) heard(p.getUUID(), audio, !PartyHud.members.isEmpty());
    }

    /** RMS 음량을 0..1 로 (보통 말소리가 0.3~0.8 정도 나오게). */
    private static float level(short[] audio) {
        if (audio.length == 0) return 0;
        double sum = 0;
        for (short s : audio) sum += (double) s * s;
        double rms = Math.sqrt(sum / audio.length) / 32768.0;
        return (float) Math.min(1, rms * 6);
    }

    private static Talk active(UUID id) {
        Talk t = TALK.get(id);
        if (t == null) return null;
        if (System.currentTimeMillis() - t.at > HOLD_MS) { TALK.remove(id, t); return null; }
        return t;
    }

    /** 파티 HUD 용: 이 이름의 플레이어가 지금 말하는 중인지. */
    static boolean speaking(String name) {
        var conn = Minecraft.getInstance().getConnection();
        if (conn == null) return false;
        PlayerInfo info = conn.getPlayerInfo(name);
        return info != null && active(info.getProfile().getId()) != null;
    }

    /** 파티 HUD 이름 옆 작은 마이크 (10x10). */
    static void drawHudIcon(GuiGraphics g, int x, int y) {
        g.blit(PARTY, x, y, 10, 10, 0, 0, 16, 16, 16, 16);
    }

    // ---- 렌더 스레드 ----

    static void pre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        UUID id = player.getUUID();
        Talk t = active(id);
        float[] s = SMOOTH.get(id);
        if (t == null && s == null) return;
        if (s == null) SMOOTH.put(id, s = new float[2]);
        s[0] += ((t != null ? 1 : 0) - s[0]) * .25f;
        s[1] += ((t != null ? t.level : 0) - s[1]) * .35f;
        if (t == null && s[0] < .01f) { SMOOTH.remove(id); return; }
        float scale = 1 + s[0] * BASE_GROW + s[1] * LEVEL_GROW;
        PlayerModel<?> model = event.getRenderer().getModel();
        setHead(model, scale);
        scaledModel = model;
    }

    static void post(RenderPlayerEvent.Post event) {
        if (scaledModel != null) { setHead(scaledModel, 1); scaledModel = null; }
        Player player = event.getEntity();
        Talk t = active(player.getUUID());
        if (t == null || player.isInvisible()) return;
        float[] s = SMOOTH.get(player.getUUID());
        float grow = s == null ? 0 : (s[0] * BASE_GROW + s[1] * LEVEL_GROW) * .5f; // 머리(0.5칸)가 커진 만큼 아이콘을 올린다
        icon(event.getPoseStack(), event.getMultiBufferSource().getBuffer(RenderType.entityTranslucent(t.party ? PARTY : NEAR)), player, grow);
    }

    private static void setHead(PlayerModel<?> model, float scale) {
        model.head.xScale = model.head.yScale = model.head.zScale = scale;
        model.hat.xScale = model.hat.yScale = model.hat.zScale = scale;
    }

    /** 이름표 위에 카메라를 바라보는 마이크 아이콘. */
    private static void icon(PoseStack ps, VertexConsumer vc, Player player, float grow) {
        ps.pushPose();
        ps.translate(0, player.getBbHeight() + (player.isCrouching() ? .75f : .95f) + grow, 0);
        ps.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        float h = .18f;
        int light = 0xF000F0;
        var pose = ps.last();
        vc.addVertex(pose, -h, -h, 0).setColor(-1).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
        vc.addVertex(pose, h, -h, 0).setColor(-1).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
        vc.addVertex(pose, h, h, 0).setColor(-1).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
        vc.addVertex(pose, -h, h, 0).setColor(-1).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
        ps.popPose();
    }
}
