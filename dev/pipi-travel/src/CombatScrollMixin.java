package dev.pipi.travel.mixin;

import dev.pipi.travel.CombatBar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
abstract class CombatScrollMixin {
    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void combatClick(long window, int button, int action, int modifiers, CallbackInfo callback) {
        Minecraft client = Minecraft.getInstance();
        if (window != client.getWindow().getWindow() || client.screen != null || !CombatBar.active()
            || !client.mouseHandler.isMouseGrabbed() || button != org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT
            || action != org.lwjgl.glfw.GLFW.GLFW_PRESS) return;
        CombatBar.basicAttack();
        callback.cancel();
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void holdCombatSlot(long window, double horizontal, double vertical, CallbackInfo callback) {
        Minecraft client = Minecraft.getInstance();
        if (window == client.getWindow().getWindow() && client.screen == null && CombatBar.active()) callback.cancel();
    }
}
