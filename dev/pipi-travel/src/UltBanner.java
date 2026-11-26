package dev.pipi.travel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;

/**
 * Ultimate cast banner: PIPI_ULT:&lt;rrggbb&gt;:&lt;skill name&gt; shows a small yellow "ULTIMATE" badge and the skill name in the
 * skill's colour at the top centre, with thin light lines on both sides. Pops in (scale 1.6 -> 1), holds, then fades (~2 s).
 */
public final class UltBanner {
    private static final String PREFIX = "PIPI_ULT:";
    private static final long POP = 160, HOLD = 1500, FADE = 450;

    private static long start;
    private static int color = 0xFFC83A;
    private static String name = "";
    private static boolean active;

    private UltBanner() {}

    static void receive(ClientChatReceivedEvent.System event) {
        String text = event.getMessage().getString();
        if (!text.startsWith(PREFIX)) return;
        event.setCanceled(true);
        String rest = text.substring(PREFIX.length());
        int cut = rest.indexOf(':');
        if (cut <= 0) return;
        try { color = Integer.parseInt(rest.substring(0, cut).trim(), 16) & 0xFFFFFF; } catch (NumberFormatException e) { return; }
        name = rest.substring(cut + 1).trim();
        start = System.currentTimeMillis();
        active = !name.isEmpty();
    }

    private static int argb(float a, int rgb) { return (Math.round(Math.clamp(a, 0f, 1f) * 255) << 24) | (rgb & 0xFFFFFF); }

    static void draw(GuiGraphics g) {
        if (!active) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        long el = System.currentTimeMillis() - start;
        if (el >= POP + HOLD + FADE) { active = false; return; }
        float alpha = el < POP ? el / (float) POP : el < POP + HOLD ? 1f : 1f - (el - POP - HOLD) / (float) FADE;
        float pop = el < POP ? 1.6f - 0.6f * (el / (float) POP) : 1f;
        if (alpha <= 0.02f) return;
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        float cx = w / 2f, cy = Math.max(34, h * 0.17f);
        float big = 2.6f * pop;
        int nameW = font.width(name);
        var pose = g.pose();

        // side lines through the name
        float half = nameW * big / 2f + 10;
        int lineLen = (int) Math.min(w * 0.3f, 150);
        int y0 = Math.round(cy + 4 * big);
        for (int i = 0; i < lineLen; i += 2) {
            float fade = 1f - i / (float) lineLen;
            int c = argb(alpha * fade * 0.85f, color);
            g.fill(Math.round(cx + half + i), y0, Math.round(cx + half + i + 2), y0 + 1, c);
            g.fill(Math.round(cx - half - i - 2), y0, Math.round(cx - half - i), y0 + 1, c);
        }

        // badge
        String badge = "ULTIMATE";
        int bw = font.width(badge) + 8, bh = 11;
        int bx = Math.round(cx - bw / 2f), by = Math.round(cy - 4 - bh);
        g.fill(bx, by, bx + bw, by + bh, argb(alpha, 0xFFD43B));
        g.fill(bx + 1, by + bh, bx + bw - 1, by + bh + 1, argb(alpha * .8f, 0x9A6A00));
        g.drawString(font, badge, bx + 4, by + 2, argb(alpha, 0x3A2600), false);

        // name: dark outline + coloured fill, scaled up
        pose.pushPose();
        pose.translate(cx, cy, 0);
        pose.scale(big, big, 1f);
        int x = -nameW / 2;
        int outline = argb(alpha, 0x1A0A0A);
        for (int[] o : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {1, 1}}) g.drawString(font, name, x + o[0], o[1], outline, false);
        g.drawString(font, name, x, 0, argb(alpha, color), false);
        pose.popPose();
    }
}
