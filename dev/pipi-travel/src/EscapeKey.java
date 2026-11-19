package dev.pipi.travel;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

/** R 키 길게 누르기: 긴급 탈출 (서버 /pipilife escape start / stop, 3초 충전 후 근처 평지로, 쿨타임 30분). */
final class EscapeKey {
    static final KeyMapping KEY = new KeyMapping("긴급 탈출 (길게 누르기)", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "PipiTravel");
    private static boolean held;

    private EscapeKey() {}

    static void key(ClientTickEvent.Post event) {
        var client = Minecraft.getInstance();
        boolean down = client.player != null && client.screen == null && client.getConnection() != null && KEY.isDown();
        while (KEY.consumeClick()) { }
        if (down == held) return;
        held = down;
        if (client.getConnection() != null && client.player != null) client.getConnection().sendCommand(down ? "pipilife escape start" : "pipilife escape stop");
    }
}
