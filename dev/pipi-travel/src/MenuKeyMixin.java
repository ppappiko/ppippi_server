package dev.pipi.travel.mixin;

import dev.pipi.travel.PipiMenu;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
abstract class MenuKeyMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void pipiMenu(long window, int key, int scan, int action, int mods, CallbackInfo callback) {
        Minecraft client = Minecraft.getInstance();
        if (window == client.getWindow().getWindow() && dev.pipi.travel.CombatBar.key(key, action)) {
            callback.cancel();
            return;
        }
        if (window != client.getWindow().getWindow() || key != GLFW.GLFW_KEY_F1 || client.player == null
            || client.getConnection() == null || (client.screen != null && !(client.screen instanceof PipiMenu))) return;
        if (action == GLFW.GLFW_PRESS) {
            client.setScreen(client.screen instanceof PipiMenu ? null : new PipiMenu());
        }
        callback.cancel();
    }
}

