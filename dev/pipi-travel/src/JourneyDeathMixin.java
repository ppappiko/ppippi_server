package dev.pipi.travel.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The server keeps inventories on death, so JourneyMap death waypoints are never needed: skip them in every world. */
@Pseudo
@Mixin(targets = "journeymap.client.event.handlers.DeathPointHandler", remap = false)
abstract class JourneyDeathMixin {
    @Inject(method = "createDeathpoint()V", at = @At("HEAD"), cancellable = true, require = 0)
    private void pipiSkipDeathpoint(CallbackInfo callback) {
        callback.cancel();
    }
}
