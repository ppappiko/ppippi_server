package dev.pipi.travel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** Custom bottom HUD: split hotbar (off-hand + 1-4 | heart | 5-9), heart HP with level and a thin XP bar. */
public final class HudBar {
    private static final int SLOT = 20, STEP = 22, GROUP = STEP * 5 - 2, GAP = 34, TOTAL = GROUP * 2 + GAP;
    private static final int HEART = 28, TAB = 8;
    private static final ResourceLocation HEART_EMPTY = ResourceLocation.fromNamespaceAndPath("pipitravel", "textures/gui/hud/heart_empty.png");
    private static final ResourceLocation HEART_FULL = ResourceLocation.fromNamespaceAndPath("pipitravel", "textures/gui/hud/heart_full.png");
    private static final String HP_PREFIX = "PIPI_HP:";

    private static int hpCur = -1, hpMax = -1, hpShield;
    private static Object hpOwner;

    private HudBar() {}

    private static Minecraft client() { return Minecraft.getInstance(); }

    static void receive(ClientChatReceivedEvent.System event) {
        String text = event.getMessage().getString();
        if (!text.startsWith(HP_PREFIX)) return;
        event.setCanceled(true);
        try {
            String[] parts = text.substring(HP_PREFIX.length()).trim().split("/");
            if (parts.length != 2 && parts.length != 3) return;
            int cur = Integer.parseInt(parts[0].trim()), max = Integer.parseInt(parts[1].trim());
            if (max <= 0) return;
            hpCur = Math.clamp(cur, 0, max);
            hpMax = max;
            hpShield = parts.length == 3 ? Math.max(0, Integer.parseInt(parts[2].trim())) : 0;
            hpOwner = client().getConnection();
        } catch (NumberFormatException ignored) { }
    }

    private static boolean hudOn() {
        var client = client();
        return client.player != null && client.level != null && !client.options.hideGui;
    }

    private static boolean replaced(ResourceLocation id) {
        return id.equals(VanillaGuiLayers.HOTBAR) || id.equals(VanillaGuiLayers.PLAYER_HEALTH) || id.equals(VanillaGuiLayers.ARMOR_LEVEL)
            || id.equals(VanillaGuiLayers.FOOD_LEVEL) || id.equals(VanillaGuiLayers.EXPERIENCE_BAR) || id.equals(VanillaGuiLayers.EXPERIENCE_LEVEL);
    }

    static void layerPre(RenderGuiLayerEvent.Pre event) {
        if (!hudOn()) return;
        var id = event.getName();
        if (replaced(id)) event.setCanceled(true);
        else if (id.equals(VanillaGuiLayers.AIR_LEVEL)) { // vanilla stacks the bubbles above food: lift them above our bar instead
            event.getGuiGraphics().pose().pushPose();
            event.getGuiGraphics().pose().translate(0, -16, 0);
        }
    }

    static void layerPost(RenderGuiLayerEvent.Post event) {
        if (hudOn() && event.getName().equals(VanillaGuiLayers.AIR_LEVEL)) event.getGuiGraphics().pose().popPose();
    }

    private static int[] hp() {
        var client = client();
        if (hpOwner != client.getConnection()) { hpOwner = null; hpCur = hpMax = -1; hpShield = 0; }
        if (hpMax > 0) return new int[] {hpCur, hpMax};
        var player = client.player;
        int max = Math.max(1, Math.round(player.getMaxHealth()));
        return new int[] {Math.clamp(Math.round(player.getHealth()), 0, max), max};
    }

    static void draw(GuiGraphics graphics) {
        var client = client();
        if (!hudOn() || client.player.isSpectator() || PipiTravel.customScreen(client.screen)) return;
        var player = client.player;
        var font = client.font;
        var pose = graphics.pose();
        int width = graphics.guiWidth(), height = graphics.guiHeight();
        float scale = width < TOTAL + 12 ? (float) width / (TOTAL + 12) : 1f;
        int cx = width / 2;
        pose.pushPose();
        pose.translate(cx, height, 0);
        pose.scale(scale, scale, 1);
        pose.translate(-cx, -height, 0);

        int left0 = cx - TOTAL / 2, right0 = left0 + GROUP + GAP;
        int slotTop = height - 27;

        // slots (flip between items and combat skills)
        float[] flip = CombatBar.flipState();
        boolean skills = flip[0] == 1;
        CombatBar.updateWeapon();
        float rowMid = slotTop + 6;
        pose.pushPose();
        pose.translate(0, rowMid, 0);
        pose.scale(1, Math.max(flip[1], 0.001f), 1);
        pose.translate(0, -rowMid, 0);
        var bag = player.getInventory();
        for (int pos = 0; pos < 10; pos++) {
            int x = pos < 5 ? left0 + pos * STEP : right0 + (pos - 5) * STEP;
            ItemStack stack = pos == 0 ? player.getOffhandItem() : bag.getItem(pos - 1);
            boolean selected = skills ? CombatBar.skillSelected(pos) : pos > 0 && bag.selected == pos - 1;
            String label = skills ? CombatBar.skillLabel(pos) : pos == 0 ? "보조" : Integer.toString(pos);
            slot(graphics, x, slotTop, label, selected, !skills || CombatBar.skillUsed(pos));
            if (skills) CombatBar.skillSlot(graphics, pos, x + 2, slotTop + 2);
            else if (!stack.isEmpty()) {
                graphics.renderItem(stack, x + 2, slotTop + 2);
                graphics.renderItemDecorations(font, stack, x + 2, slotTop + 2);
            }
        }
        if (flip[1] < 1) {
            int alpha = (int) ((1 - flip[1]) * 200) << 24;
            graphics.fill(left0, slotTop - TAB, left0 + GROUP, slotTop + SLOT, alpha | 0x0a1420);
            graphics.fill(right0, slotTop - TAB, right0 + GROUP, slotTop + SLOT, alpha | 0x0a1420);
        }
        pose.popPose();

        // XP bar along the bottom of the whole bar
        int xpL = left0, xpR = right0 + GROUP;
        graphics.fill(xpL, height - 5, xpR, height - 1, 0xc0080a0e);
        int fillW = Math.round((xpR - xpL - 2) * Math.clamp(player.experienceProgress, 0f, 1f));
        if (fillW > 0) {
            graphics.fill(xpL + 1, height - 4, xpL + 1 + fillW, height - 2, 0xff4fcf4a);
            graphics.fill(xpL + 1, height - 4, xpL + 1 + fillW, height - 3, 0xff8be884);
        }

        // heart
        int[] hp = hp();
        int hx = cx - HEART / 2, hy = height - 42;
        graphics.blit(HEART_EMPTY, hx, hy, 0f, 0f, HEART, HEART, HEART, HEART);
        int filled = hp[0] <= 0 ? 0 : Math.clamp((int) Math.ceil(HEART * (double) hp[0] / hp[1]), 1, HEART);
        if (filled > 0) graphics.blit(HEART_FULL, hx, hy + HEART - filled, 0f, (float) (HEART - filled), HEART, filled, HEART, HEART);
        String text = hp[0] + "/" + hp[1];
        int tw = Math.max(1, font.width(text));
        float ts = Math.min(0.8f, 22f / tw);
        pose.pushPose();
        pose.translate(cx, hy + 12, 0);
        pose.scale(ts, ts, 1);
        graphics.drawString(font, text, -tw / 2, -4, 0xffffffff, true);
        pose.popPose();
        if (hpShield > 0 && hpMax > 0) {
            // silver arc around the heart (clockwise from the top), length = shield / max HP
            double sweep = Math.PI * 2 * Math.min(1.0, (double) hpShield / hpMax);
            double r = HEART / 2.0 + 2;
            double hcx = hx + HEART / 2.0, hcy = hy + HEART / 2.0;
            for (double a = 0; a <= sweep; a += 0.07) {
                int px = (int) Math.round(hcx + Math.sin(a) * r), py = (int) Math.round(hcy - Math.cos(a) * r);
                graphics.fill(px - 1, py - 1, px + 1, py + 1, 0xe6dfe8f4);
            }
            String sh = "+" + hpShield;
            int sw = Math.max(1, font.width(sh));
            float ss = Math.min(0.7f, 22f / sw);
            pose.pushPose();
            pose.translate(cx, hy + 21, 0);
            pose.scale(ss, ss, 1);
            graphics.drawString(font, sh, -sw / 2, -4, 0xffdfe8f4, true);
            pose.popPose();
        }
        graphics.drawCenteredString(font, "Lv" + player.experienceLevel, cx, height - 13, 0xffe8f2ff);

        pose.popPose();
    }

    private static void slot(GuiGraphics graphics, int x, int y, String label, boolean selected, boolean used) {
        var font = client().font;
        // tab with the key label
        graphics.fill(x, y - TAB, x + SLOT, y, selected ? 0xd0303a4c : 0xb0161a22);
        graphics.renderOutline(x, y - TAB, SLOT, TAB + 1, selected ? 0xffe6eeff : 0xff343b48);
        // slot body
        graphics.fill(x, y, x + SLOT, y + SLOT, 0xb00c0f14);
        graphics.renderOutline(x, y, SLOT, SLOT, selected ? 0xffe6eeff : 0xff343b48);
        if (selected) graphics.renderOutline(x - 1, y - TAB - 1, SLOT + 2, SLOT + TAB + 2, 0x80e6eeff);
        if (!label.isEmpty()) {
            var pose = graphics.pose();
            pose.pushPose();
            pose.translate(x + 3, y - TAB + 2, 0);
            pose.scale(0.75f, 0.75f, 1);
            graphics.drawString(font, label, 0, 0, selected ? 0xffffffff : used ? 0xffb4bccb : 0xff6a7280, false);
            pose.popPose();
        }
    }
}
