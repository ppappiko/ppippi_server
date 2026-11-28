package dev.pipi.travel;

import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.ClientReceiveSoundEvent;
import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * Simple Voice Chat 클라이언트 연동: 누가 말하는지(주변 음성 / 파티 음성)와 목소리 크기를 VoiceFx 로 넘긴다.
 * - EntitySound: 주변(근거리) 음성, StaticSound: 그룹 음성 = 파티 음성 (PipiFacilities PartyVoice 의 파티 그룹), ClientSoundEvent: 내 마이크.
 * SVC 가 @ForgeVoicechatPlugin 을 찾아 생성한다 (SVC 가 없으면 로드되지 않음). 서버에서도 생성되지만 클라이언트에서만 이벤트를 등록한다.
 * 이벤트는 SVC 오디오 스레드에서 오므로 VoiceFx 는 값만 기록하고 그리기는 렌더 스레드에서 한다.
 */
@ForgeVoicechatPlugin
public final class VoiceTalk implements VoicechatPlugin {
    @Override public String getPluginId() { return "pipitravel_voice_fx"; }

    @Override public void registerEvents(EventRegistration registration) {
        if (!FMLEnvironment.dist.isClient()) return;
        registration.registerEvent(ClientReceiveSoundEvent.EntitySound.class, e -> VoiceFx.heard(e.getEntityId(), e.getRawAudio(), false));
        registration.registerEvent(ClientReceiveSoundEvent.StaticSound.class, e -> VoiceFx.heard(e.getId(), e.getRawAudio(), true));
        registration.registerEvent(ClientSoundEvent.class, e -> VoiceFx.self(e.getRawAudio()));
    }
}
