package dev.pipi.travel.mixin;

import dev.pipi.travel.ArmorIcons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemRenderer.class)
abstract class ItemRendererArmorMixin {
    @Inject(method = "getModel", at = @At("RETURN"), cancellable = true)
    private void pipiArmorIcon(ItemStack stack, Level level, LivingEntity entity, int seed, CallbackInfoReturnable<BakedModel> cir) {
        ModelResourceLocation id = ArmorIcons.modelFor(stack);
        if (id == null) return;
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(id);
        if (model != null && model != Minecraft.getInstance().getModelManager().getMissingModel()) cir.setReturnValue(model);
    }
}
