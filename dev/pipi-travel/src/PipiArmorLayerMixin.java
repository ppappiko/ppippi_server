package dev.pipi.travel.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.pipi.travel.PipiArmor;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces the vanilla armour drawing for PipiLife gear (identified by its catalog tag) with the custom layer + 3D parts. */
@SuppressWarnings({"rawtypes", "unchecked"})
@Mixin(HumanoidArmorLayer.class)
abstract class PipiArmorLayerMixin {
    @Shadow protected abstract void setPartVisibility(HumanoidModel model, EquipmentSlot slot);

    @Inject(method = "renderArmorPiece", at = @At("HEAD"), cancellable = true)
    private void pipiArmor(PoseStack poseStack, MultiBufferSource buffer, LivingEntity entity, EquipmentSlot slot, int light, HumanoidModel model, CallbackInfo callback) {
        PipiArmor.Info info = PipiArmor.identify(entity.getItemBySlot(slot), slot);
        if (info == null) return;
        ((HumanoidModel) ((RenderLayer) (Object) this).getParentModel()).copyPropertiesTo(model);
        setPartVisibility(model, slot);
        PipiArmor.render(poseStack, buffer, model, slot, info, light);
        callback.cancel();
    }
}
