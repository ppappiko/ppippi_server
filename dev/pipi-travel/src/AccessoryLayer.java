package dev.pipi.travel;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * Worn accessories (목걸이·귀걸이·팔찌) on players.
 * The server (PipiLife Accessories) sends "PIPI_ACC:<uuid>:<necklace>:<earring>:<bracelet>" (model id or "-") whenever
 * someone's accessories change and on join; parts come from assets/pipitravel/armor_parts/acc_<slot>_<model>.json.
 */
final class AccessoryLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final String PREFIX = "PIPI_ACC:";
    private static final String[] SLOTS = {"necklace", "earring", "bracelet"};
    private static final Map<UUID, String[]> WORN = new ConcurrentHashMap<>();

    AccessoryLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) { super(parent); }

    static void receive(ClientChatReceivedEvent.System event) {
        String text = event.getMessage().getString();
        if (!text.startsWith(PREFIX)) return;
        event.setCanceled(true);
        String[] p = text.substring(PREFIX.length()).split(":");
        if (p.length < 1 + SLOTS.length) return;
        try {
            UUID id = UUID.fromString(p[0]);
            String[] models = new String[SLOTS.length];
            boolean any = false;
            for (int i = 0; i < SLOTS.length; i++) {
                String m = p[1 + i];
                models[i] = m.matches("[a-z0-9_]+") ? m : null;
                any |= models[i] != null;
            }
            if (any) WORN.put(id, models); else WORN.remove(id);
        } catch (IllegalArgumentException ignored) {}
    }

    /** Mod bus: adds the layer to both player skin models (wide / slim). */
    static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins())
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) renderer.addLayer(new AccessoryLayer(renderer));
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buffer, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        String[] models = WORN.get(player.getUUID());
        if (models == null || player.isInvisible()) return;
        for (int i = 0; i < SLOTS.length; i++) {
            if (models[i] == null) continue;
            String name = "acc_" + SLOTS[i] + "_" + models[i];
            // 갑옷 위로 보이도록: 목걸이·팔찌는 상의, 귀걸이는 투구를 입었으면 "_armored" 버전을 쓴다 (없으면 기본)
            EquipmentSlot cover = i == 1 ? EquipmentSlot.HEAD : EquipmentSlot.CHEST;
            if (!player.getItemBySlot(cover).isEmpty() && PipiArmor.hasAccessory(name + "_armored")) name += "_armored";
            PipiArmor.renderAccessory(ps, buffer, getParentModel(), name, light);
        }
    }
}
