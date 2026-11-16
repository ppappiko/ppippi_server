package dev.pipi.travel;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Class icons (warrior / archer / mage, 64x64 from dev/art/class-icons/make.mjs) + the diamond frame used by the party list. */
final class ClassIcons {
    static final int NONE = -1, WARRIOR = 0, ARCHER = 1, MAGE = 2;
    private static final ResourceLocation[] TEX = {
        ResourceLocation.fromNamespaceAndPath("pipitravel", "textures/gui/class/warrior.png"),
        ResourceLocation.fromNamespaceAndPath("pipitravel", "textures/gui/class/archer.png"),
        ResourceLocation.fromNamespaceAndPath("pipitravel", "textures/gui/class/mage.png")};
    static final String[] NAMES = {"전사", "궁수", "마법사"};

    private ClassIcons() {}

    static int parse(String code) {
        return switch (code == null ? "" : code.trim()) { case "w" -> WARRIOR; case "a" -> ARCHER; case "m" -> MAGE; default -> NONE; };
    }

    /** Class of a PipiLife weapon / armour piece: sword = warrior, bow = archer, blaze rod (staff) = mage, armour by its catalog family. */
    static int of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return NONE;
        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (id.endsWith("_sword")) return WARRIOR;
        if (stack.is(Items.BOW) || stack.is(Items.CROSSBOW)) return ARCHER;
        if (stack.is(Items.BLAZE_ROD)) return MAGE;
        if (stack.getItem() instanceof ArmorItem armor) {
            EquipmentSlot slot = armor.getEquipmentSlot();
            PipiArmor.Info info = PipiArmor.identify(stack, slot);
            if (info != null && info.cls() >= 0 && info.cls() <= 2) return info.cls();
        }
        return NONE;
    }

    /** Draws the icon scaled into a size x size box (alpha blended). */
    static void draw(GuiGraphics g, int cls, float x, float y, float size, float alpha) {
        if (cls < 0 || cls > 2) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1f, 1f, 1f, alpha);
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(size / 64f, size / 64f, 1f);
        g.blit(TEX[cls], 0, 0, 0f, 0f, 64, 64, 64, 64);
        g.pose().popPose();
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    static void draw(GuiGraphics g, int cls, int x, int y, int size) { draw(g, cls, (float) x, (float) y, (float) size, 1f); }

    /** Diamond (45° square) frame centred on (cx, cy) with half-diagonal r: outer border colour + inner fill. */
    static void diamond(GuiGraphics g, float cx, float cy, float r, int border, int fill) {
        float side = (float) (r * Math.sqrt(2));
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(45));
        g.pose().scale(side / 100f, side / 100f, 1f);
        g.fill(-50, -50, 50, 50, border);
        g.fill(-42, -42, 42, 42, fill);
        g.fill(-42, -42, 42, -36, 0x30ffffff);
        g.pose().popPose();
    }
}
