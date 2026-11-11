package dev.pipi.travel;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;

/** 마력포 wind-up overlay: PIPI_CHARGE:&lt;skill&gt;:&lt;ticks&gt; (0 = stop) draws a rotating violet magic circle on screen. */
public final class ChargeOverlay {
    private static final String PREFIX = "PIPI_CHARGE:";
    private static final ResourceLocation OUTER = ResourceLocation.fromNamespaceAndPath("pipitravel", "textures/gui/charge_circle_outer.png");
    private static final ResourceLocation INNER = ResourceLocation.fromNamespaceAndPath("pipitravel", "textures/gui/charge_circle_inner.png");
    private static final long FADE_IN = 350, FLASH = 220, FADE_OUT = 380, STOP_FADE = 250;

    private static long start, durationMs, endAt, stopAt;
    private static float stopAlpha;
    private static boolean active;

    private ChargeOverlay() {}

    static void receive(ClientChatReceivedEvent.System event) {
        String text = event.getMessage().getString();
        if (!text.startsWith(PREFIX)) return;
        event.setCanceled(true);
        String[] parts = text.substring(PREFIX.length()).trim().split(":");
        if (parts.length != 2) return;
        try {
            int ticks = Integer.parseInt(parts[1].trim());
            long now = System.currentTimeMillis();
            if (ticks <= 0) {
                if (active && endAt == 0 && stopAt == 0) { stopAlpha = chargeAlpha(now); stopAt = now; }
                return;
            }
            start = now; durationMs = ticks * 50L; endAt = 0; stopAt = 0; active = true;
        } catch (NumberFormatException ignored) { }
    }

    private static float ease(float t) { t = Math.clamp(t, 0f, 1f); return 1f - (1f - t) * (1f - t); }

    private static float chargeAlpha(long now) {
        long el = now - start;
        float in = ease(el / (float) FADE_IN);
        float p = Math.clamp(el / (float) durationMs, 0f, 1f);
        return in * (0.35f + 0.6f * p);
    }

    static void draw(GuiGraphics graphics) {
        if (!active) return;
        var client = Minecraft.getInstance();
        if (client.player == null || client.level == null) { active = false; return; }
        if (client.options.hideGui) return;
        long now = System.currentTimeMillis();
        long el = now - start;
        float alpha, scale, white = 0f;
        if (stopAt != 0) { // cancelled early: fade away
            float u = (now - stopAt) / (float) STOP_FADE;
            if (u >= 1f) { active = false; stopAt = 0; return; }
            alpha = stopAlpha * (1f - u);
            scale = 1f;
        } else if (el < durationMs) {
            alpha = chargeAlpha(now);
            float p = el / (float) durationMs;
            scale = 0.7f + 0.3f * ease(el / (float) FADE_IN) + 0.05f * p;
            white = p * p * 0.5f;
        } else {
            if (endAt == 0) endAt = now;
            long since = now - endAt;
            if (since >= FLASH + FADE_OUT) { active = false; endAt = 0; return; }
            if (since < FLASH) { float u = since / (float) FLASH; alpha = 0.95f + 0.05f * u; white = 0.5f + 0.5f * u; scale = 1.05f + 0.05f * u; }
            else { float u = (since - FLASH) / (float) FADE_OUT; alpha = 1f - u; white = 1f - u; scale = 1.1f + 0.2f * ease(u); }
        }
        alpha = Math.clamp(alpha, 0f, 1f);
        if (alpha <= 0.01f) return;
        int w = graphics.guiWidth(), h = graphics.guiHeight();
        float size = h * 0.5f * scale;
        float spin = el / 1000f;
        float speed = el < durationMs ? 1f + 2.5f * (el / (float) durationMs) : 3.5f; // spins faster as it nears completion
        float angle = (el < durationMs ? spin * 28f * (0.5f + 0.5f * speed) : 28f * (durationMs / 1000f) * 2.25f);
        var pose = graphics.pose();
        float r = 0.78f + 0.22f * white, g = 0.52f + 0.48f * white, b = 1f;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.setColor(r, g, b, alpha);
        pose.pushPose();
        pose.translate(w / 2f, h / 2f, 0);
        pose.pushPose();
        pose.scale(size / 256f, size / 256f, 1f);
        pose.mulPose(Axis.ZP.rotationDegrees(angle));
        graphics.blit(OUTER, -128, -128, 0f, 0f, 256, 256, 256, 256);
        pose.popPose();
        pose.pushPose();
        pose.scale(size / 256f, size / 256f, 1f);
        pose.mulPose(Axis.ZP.rotationDegrees(-angle * 1.6f));
        graphics.blit(INNER, -128, -128, 0f, 0f, 256, 256, 256, 256);
        pose.popPose();
        pose.popPose();
        graphics.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }
}
