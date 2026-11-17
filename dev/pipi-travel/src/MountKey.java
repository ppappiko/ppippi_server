package dev.pipi.travel;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

/** Z 키: 지정한 탈것 펫 타기/내리기 (서버 /pipilife mount). 키는 조작 설정에서 바꿀 수 있다. */
final class MountKey {
    static final KeyMapping KEY = new KeyMapping("탈것 펫 타기/내리기", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, "PipiTravel");
    private static long last;

    private MountKey() {}

    static void key(ClientTickEvent.Post event) {
        var client = Minecraft.getInstance();
        if (client.player == null || client.screen != null || client.getConnection() == null) { while (KEY.consumeClick()) { } return; }
        while (KEY.consumeClick()) {
            long now = System.currentTimeMillis();
            if (now - last < 400) continue; // 연타로 소환/내리기가 겹치지 않게
            last = now;
            client.getConnection().sendCommand("pipilife mount");
        }
    }
}
