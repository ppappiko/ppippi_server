package dev.pipi.travel;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;

/**
 * 강화 연출. The server sends PIPI_UPGRADE:&lt;success|fail&gt;:&lt;newLevel&gt;:&lt;itemName&gt; right before it re-opens the screen.
 * UpgradeScreen calls {@link #begin} when the player confirms; this class then drives the whole timeline
 * (charge 0-1.1 s, reveal, success popup / fail aura) from static state so it survives the screen being re-created.
 * Without a begun animation the message falls back to the old HUD effect (screen closed).
 */
final class UpgradeFx {
    private static final String PREFIX = "PIPI_UPGRADE:";
    private static final ResourceLocation RAYS = tex("upgrade_rays"), RING = tex("upgrade_ring"), SPARK = tex("upgrade_sparkle"), CRACK = tex("upgrade_crack"),
        FRAME = tex("upgrade_frame"), RUNES = tex("upgrade_runes"), GLOW = tex("upgrade_glow");
    static final int CHARGE_MS = 1100, FLASH_MS = 300, FAIL_MS = 700;

    // ---- screen-driven state
    private static boolean active, known, success, revealed, popup;
    private static long start, revealAt, popupAt;
    private static int step, level;
    private static ItemStack[] snap;
    private static long failUntil, gaugeAt;
    private static int gaugeFrom = -1;
    private static long lastSeen;
    private static final long[] CHARGE_AT = {0, 260, 560, 860};

    // ---- HUD fallback state
    private static long hudStart;
    private static boolean hudActive, hudSuccess, hudBig;

    private UpgradeFx() {}

    private static ResourceLocation tex(String n) { return ResourceLocation.fromNamespaceAndPath("pipitravel", "textures/gui/" + n + ".png"); }
    private static float clamp(float v) { return Math.clamp(v, 0f, 1f); }
    private static float ease(float u) { u = clamp(u); return 1f - (1f - u) * (1f - u); }
    private static int argb(float a, int r, int g, int b) { return (Math.clamp(Math.round(a * 255), 0, 255) << 24) | (r << 16) | (g << 8) | b; }
    private static float rnd(int i, int salt) {
        int h = i * 374761393 + salt * 668265263; h = (h ^ (h >>> 13)) * 1274126177; h ^= h >>> 16;
        return (h & 0xffff) / 65535f;
    }
    private static void ui(SoundEvent e, float pitch, float vol) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(e, pitch, vol));
    }

    // ================= state machine =================

    static void begin(ItemStack[] slots, int gauge) {
        if (active || popup) return;
        snap = slots;
        gaugeFrom = Math.clamp(gauge, 0, 100);
        gaugeAt = 0;
        active = true; known = false; revealed = false; popup = false; step = 0;
        start = System.currentTimeMillis();
        failUntil = 0;
    }

    /** A new UpgradeScreen was created: no result message arrived with it means the server refused the attempt. */
    static void screenOpened() {
        lastSeen = System.currentTimeMillis();
        if (active && !known && System.currentTimeMillis() - start > 30) cancel();
    }
    static void seen() { lastSeen = System.currentTimeMillis(); }

    private static void cancel() { active = false; popup = false; revealed = false; snap = null; gaugeFrom = -1; }

    static void receive(ClientChatReceivedEvent.System event) {
        String text = event.getMessage().getString();
        if (!text.startsWith(PREFIX)) return;
        event.setCanceled(true);
        String[] p = text.substring(PREFIX.length()).split(":", 3);
        if (p.length < 2) return;
        try {
            int lv = Integer.parseInt(p[1].trim());
            boolean ok = p[0].trim().equals("success");
            if (active && !known) {
                level = lv; success = ok; known = true;
                revealAt = Math.max(start + CHARGE_MS, System.currentTimeMillis());
                return;
            }
            level = lv;
            hudSuccess = ok; hudBig = ok && lv >= 10;
            hudStart = System.currentTimeMillis(); hudActive = true;
            if (ok) { ui(SoundEvents.ANVIL_USE, 1.2f, .6f); ui(SoundEvents.EXPERIENCE_ORB_PICKUP, 1f, .8f); }
            else { ui(SoundEvents.ANVIL_LAND, .6f, .5f); ui(SoundEvents.GLASS_BREAK, .8f, .5f); }
        } catch (NumberFormatException ignored) { }
    }

    /** Advances the timeline; call once per frame from the screen. */
    static void update() {
        long now = System.currentTimeMillis();
        if (!active) return;
        long ms = now - start;
        if (!known && ms > 3500) { cancel(); return; }
        while (step < CHARGE_AT.length && ms >= CHARGE_AT[step] && !revealed) {
            if (step == 0) ui(SoundEvents.BEACON_POWER_SELECT, 1.3f, .55f);
            else ui(SoundEvents.AMETHYST_BLOCK_CHIME, .6f + .3f * step, .7f);
            step++;
        }
        if (known && !revealed && now >= revealAt) {
            revealed = true;
            if (success) { ui(SoundEvents.ANVIL_USE, 1.2f, .6f); ui(SoundEvents.EXPERIENCE_ORB_PICKUP, 1f, .8f); }
            else { ui(SoundEvents.ANVIL_LAND, .55f, .6f); ui(SoundEvents.GLASS_BREAK, .8f, .5f); }
        }
        if (revealed) {
            long r = now - revealAt;
            if (success && r >= FLASH_MS) { active = false; popup = true; popupAt = now; }
            else if (!success && r >= FAIL_MS) { active = false; snap = null; failUntil = now + 2200; gaugeAt = now; }
        }
    }

    static boolean frozen() { return (active || popup) && snap != null; }
    static boolean blocking() { return active; }
    static boolean popupShowing() { return popup && System.currentTimeMillis() - popupAt > 120; }
    static ItemStack frozenItem(int slot) { return snap != null && slot < snap.length ? snap[slot] : ItemStack.EMPTY; }
    static void closePopup() { popup = false; snap = null; gaugeFrom = -1; }
    static boolean failShown() { return System.currentTimeMillis() < failUntil; }

    /** Blessing gauge to display: animates from the pre-attempt value to the live one after a failure. */
    static float gauge(int live) {
        if (gaugeFrom < 0 || gaugeAt == 0 || frozen()) return live;
        float u = (System.currentTimeMillis() - gaugeAt) / 700f;
        if (u >= 1f) { gaugeFrom = -1; return live; }
        return gaugeFrom + (live - gaugeFrom) * ease(u);
    }

    private static long revealMs() { return revealed ? System.currentTimeMillis() - revealAt : -1; }

    /** Screen dim 0..1 (multiplied by the caller's colour alpha). */
    static float dim() {
        if (popup) return .72f;
        if (!active) return 0f;
        long ms = System.currentTimeMillis() - start;
        float in = clamp(ms / 250f);
        if (revealed && !success) { long r = revealMs(); return in * (r < 450 ? 1f : 1f - clamp((r - 450) / 250f)); }
        return in;
    }

    // ================= drawing =================

    private static void blit(GuiGraphics g, ResourceLocation tex, float cx, float cy, float size, float rot, int texSize, float r, float gr, float b, float a) {
        if (a <= .04f) return;
        g.setColor(r, gr, b, clamp(a));
        var pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0);
        pose.scale(size / texSize, size / texSize, 1f);
        if (rot != 0f) pose.mulPose(Axis.ZP.rotationDegrees(rot));
        g.blit(tex, -texSize / 2, -texSize / 2, 0f, 0f, texSize, texSize, texSize, texSize);
        pose.popPose();
        g.setColor(1f, 1f, 1f, 1f);
    }

    /** The ornate slot frame with the item inside (drawn 1:1 at 96x96 centred on cx,cy). */
    static void drawFrame(GuiGraphics g, float cx, float cy, ItemStack item, float lift) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(0, 0, lift);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.fill(Math.round(cx - 19), Math.round(cy - 19), Math.round(cx + 19), Math.round(cy + 19), 0xff0b131d);
        g.blit(FRAME, Math.round(cx - 48), Math.round(cy - 48), 0f, 0f, 96, 96, 96, 96);
        if (!item.isEmpty()) {
            pose.pushPose();
            pose.translate(cx - 16, cy - 16, 20);
            pose.scale(2f, 2f, 1f);
            g.renderItem(item, 0, 0);
            pose.popPose();
        }
        pose.popPose();
    }

    /** Light drawn behind the frame while the screen is dimmed (glow + rotating rune rings; red aura on failure). */
    static void drawBehind(GuiGraphics g, float cx, float cy) {
        if (!active) return;
        var pose = g.pose();
        pose.pushPose();
        pose.translate(0, 0, 205);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        long ms = System.currentTimeMillis() - start;
        long r = revealMs();
        float p = clamp(ms / (float) CHARGE_MS);
        if (revealed && !success) {
            float in = clamp(r / 120f), out = r > 520 ? 1f - clamp((r - 520) / 180f) : 1f;
            float pulse = .78f + .22f * (float) Math.sin(r * .022f);
            float a = in * out;
            blit(g, GLOW, cx, cy, 190f + 30f * pulse, 0f, 64, .55f, .02f, .04f, a * pulse);
            blit(g, GLOW, cx, cy, 120f, 0f, 64, .25f, 0f, .02f, a * .9f);
            blit(g, RING, cx, cy, 100f + 34f * pulse, 0f, 128, .75f, .05f, .07f, a * .75f);
            blit(g, RUNES, cx, cy, 128f, -r * .05f, 128, .6f, .08f, .08f, a * .45f * (1f - clamp(r / 300f)));
            blit(g, GLOW, cx, cy, 150f, 0f, 64, 1f, .85f, .5f, (1f - clamp(r / 150f)) * .6f);
        } else {
            float pulse = .85f + .15f * (float) Math.sin(ms * .02f);
            blit(g, GLOW, cx, cy, 90f + 120f * p, 0f, 64, 1f, .82f, .42f, (.22f + .6f * p) * pulse * (revealed ? 1f - clamp(r / (float) FLASH_MS) : 1f));
            blit(g, RUNES, cx, cy, 128f + 22f * p, ms * .06f, 128, 1f, .88f, .55f, .3f + .6f * p);
            blit(g, RUNES, cx, cy, 98f + 14f * p, -ms * .035f, 128, .55f, .85f, 1f, .5f * p);
        }
        pose.popPose();
        g.setColor(1f, 1f, 1f, 1f);
    }

    /** Effects drawn above the frame: gathering sparks, success flash and burst ring. cx,cy is the frame centre in screen units. */
    static void drawFront(GuiGraphics g, float cx, float cy) {
        if (!active) return;
        var pose = g.pose();
        pose.pushPose();
        pose.translate(0, 0, 500);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        long ms = System.currentTimeMillis() - start;
        long r = revealMs();
        float p = clamp(ms / (float) CHARGE_MS);
        if (!(revealed && !success)) {
            float fade = revealed ? 1f - clamp(r / (float) FLASH_MS) : 1f;
            for (int i = 0; i < 20; i++) {
                float u = ((ms / 760f) + rnd(i, 1)) % 1f;
                float ang = rnd(i, 2) * 6.2832f + ms * .0012f;
                float rad = 96f * (1f - u) + 6f;
                float size = 7f + rnd(i, 3) * 7f;
                blit(g, SPARK, cx + (float) Math.cos(ang) * rad, cy + (float) Math.sin(ang) * rad, size, 0f, 32, 1f, .92f, .6f, (float) Math.sin(u * Math.PI) * (.35f + .65f * p) * fade);
            }
        }
        if (revealed && success) {
            float u = clamp(r / (float) FLASH_MS);
            blit(g, RING, cx, cy, 90f + 560f * ease(u), 0f, 128, 1f, .97f, .85f, 1f - u * u);
            if (u > .1f) blit(g, RING, cx, cy, 60f + 380f * ease((u - .1f) / .9f), 0f, 128, 1f, 1f, .95f, (1f - u) * .8f);
            blit(g, GLOW, cx, cy, 120f + 480f * ease(u), 0f, 64, 1f, .96f, .8f, 1f - u);
            float flash = (r < 60 ? r / 60f : 1f - (r - 60) / 240f) * .95f;
            if (flash > .04f) g.fill(-6000, -6000, 6000, 6000, argb(flash, 255, 245, 210));
        }
        pose.popPose();
        g.setColor(1f, 1f, 1f, 1f);
    }

    private static void scaled(GuiGraphics g, Font font, String s, float cx, float y, float sc, int color) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(cx, y, 0);
        pose.scale(sc, sc, 1f);
        g.drawString(font, s, -font.width(s) / 2, 0, color, true);
        pose.popPose();
    }

    /** 강화 성공 result popup centred on (cx,cy) of the screen space. */
    static void drawPopup(GuiGraphics g, Font font, float cx, float cy, ItemStack item, String itemName, int nameColor) {
        if (!popup) return;
        long now = System.currentTimeMillis();
        long pt = now - popupAt;
        float a = clamp(pt / 220f);
        if (a < .1f) return;
        int w = 400, h = 232, x = Math.round(cx - w / 2f), y = Math.round(cy - h / 2f);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(0, 0, 400);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, argb(a * .9f, 90, 78, 52));
        g.fillGradient(x, y, x + w, y + h, argb(a * .96f, 20, 31, 46), argb(a * .96f, 8, 14, 22));
        float gcx = cx, gcy = y + 118;
        float pulse = .85f + .15f * (float) Math.sin(pt * .004f);
        blit(g, RAYS, gcx, gcy, 210f, pt * .012f, 128, 1f, .85f, .38f, a * .28f);
        blit(g, GLOW, gcx, gcy, 190f * pulse, 0f, 64, 1f, .82f, .35f, a * .75f);
        blit(g, RUNES, gcx, gcy, 128f, pt * .03f, 128, 1f, .88f, .55f, a * .5f);
        // header + divider
        scaled(g, font, "강화 성공", cx, y + 16, 1.7f, 0xffe6c588);
        g.fill(x + 16, y + 42, x + w - 16, y + 43, argb(a * .85f, 214, 178, 106));
        for (int k = 0; k < 4; k++) g.fill(Math.round(cx) - 4 + k, y + 43 + k, Math.round(cx) + 4 - k, y + 44 + k, argb(a, 214, 178, 106));
        drawFrame(g, gcx, gcy, item, 20f);
        // floating sparkles drifting up
        for (int i = 0; i < 34; i++) {
            float life = 2.4f + rnd(i, 4) * 2.2f;
            float u = ((pt / 1000f + rnd(i, 5) * 6f) / life) % 1f;
            float px = x + 14 + rnd(i, 6) * (w - 28) + (float) Math.sin(pt * .002f + i) * 6f;
            float py = y + h - 16 - u * (h - 66);
            float tw = .55f + .45f * (float) Math.sin(pt * .01f + i * 1.7f);
            blit(g, SPARK, px, py, 4f + rnd(i, 7) * 8f, 0f, 32, 1f, .86f, .42f, (float) Math.sin(u * Math.PI) * tw * a);
        }
        scaled(g, font, "+" + level, cx, y + 172, 1.6f, 0xffe6c588);
        if (itemName != null && !itemName.isEmpty()) scaled(g, font, itemName, cx, y + 196, 1f, nameColor);
        if (pt > 400) scaled(g, font, "클릭하여 닫기", cx, y + h - 13, 1f, now / 500 % 2 == 0 ? 0xff9fb4ca : 0xff7b8fa6);
        pose.popPose();
        g.setColor(1f, 1f, 1f, 1f);
    }

    // ================= HUD fallback (screen closed) =================

    private static float hudT() {
        if (!hudActive) return 2f;
        float t = (System.currentTimeMillis() - hudStart) / 1300f;
        if (t >= 1f) hudActive = false;
        return t;
    }

    private static void hudDraw(GuiGraphics g, float cx, float cy, float unit) {
        float t = hudT();
        if (!hudActive || t >= 1f) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        long ms = System.currentTimeMillis() - hudStart;
        var pose = g.pose();
        pose.pushPose();
        pose.translate(0, 0, 300);
        int huge = 6000;
        if (hudSuccess) {
            float flash = Math.max(0f, 1f - t / .22f) * .38f;
            if (flash > .04f) g.fill(-huge, -huge, huge, huge, argb(flash, 255, 250, 230));
            float rayA = t < .12f ? t / .12f : 1f - (t - .12f) / .88f;
            float rays = (hudBig ? 250f : 190f) * unit;
            blit(g, RAYS, cx, cy, rays * (.7f + .45f * ease(t * 1.4f)), ms * .018f, 128, 1f, .85f, .38f, rayA * .95f);
            blit(g, RAYS, cx, cy, rays * .75f * (.7f + .45f * ease(t * 1.4f)), -ms * .03f, 128, 1f, .95f, .7f, rayA * .6f);
            blit(g, RING, cx, cy, (hudBig ? 230f : 170f) * unit * ease(t / .6f), 0f, 128, 1f, .88f, .4f, 1f - clamp(t / .7f));
            blit(g, SPARK, cx, cy, 70f * unit * (1f - clamp(t / .35f)) + 1f, 0f, 32, 1f, 1f, .9f, 1f - clamp(t / .35f));
            int n = hudBig ? 26 : 16;
            for (int i = 0; i < n; i++) {
                float a = rnd(i, 1) * 6.2832f, sp = (.35f + rnd(i, 2) * .65f) * (hudBig ? 130f : 95f) * unit;
                float delay = rnd(i, 3) * .25f, u = clamp((t - delay) / .7f);
                if (u <= 0f || u >= 1f) continue;
                float d = 14f * unit + sp * ease(u);
                float tw = .6f + .4f * (float) Math.sin(ms * .03f + i * 2.1f);
                float size = (8f + rnd(i, 4) * 12f) * unit * (1f - u * .5f);
                blit(g, SPARK, cx + (float) Math.cos(a) * d, cy + (float) Math.sin(a) * d - 8f * unit * u, size, 0f, 32, 1f, .9f - .15f * rnd(i, 5), .45f + .4f * rnd(i, 6), (1f - u) * tw + .1f);
            }
        } else {
            float dim = Math.max(0f, 1f - t / .3f) * .32f;
            if (dim > .04f) g.fill(-huge, -huge, huge, huge, argb(dim, 0, 0, 0));
            float crackA = (t < .07f ? 0f : 1f) * (1f - clamp((t - .65f) / .35f));
            blit(g, CRACK, cx, cy, 62f * unit, 0f, 64, .75f, .2f, .2f, crackA);
            blit(g, CRACK, cx, cy, 62f * unit, 0f, 64, .3f, .3f, .32f, crackA * .55f);
        }
        pose.popPose();
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    /** HUD fallback centred on screen; skipped while an upgrade screen is open. Also drops a stale screen animation. */
    static void drawHud(GuiGraphics g) {
        var client = Minecraft.getInstance();
        if ((active || popup) && !(client.screen instanceof UpgradeScreen) && System.currentTimeMillis() - lastSeen > 1500) cancel();
        hudT();
        if (!hudActive) return;
        if (client.player == null || client.options.hideGui || client.screen instanceof UpgradeScreen) return;
        float unit = Math.max(.8f, g.guiHeight() / 270f) * .75f;
        hudDraw(g, g.guiWidth() / 2f, g.guiHeight() / 2f - 20f * unit, unit);
    }
}
