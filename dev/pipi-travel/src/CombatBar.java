package dev.pipi.travel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import org.lwjgl.glfw.GLFW;

public final class CombatBar {
    private static Minecraft client() { return Minecraft.getInstance(); }
    private static net.minecraft.client.multiplayer.ClientPacketListener connection;
    private static int savedSlot = -1;
    private static int selectedSkill;
    private static long lastJump;
    private static final long[] cooldownEnd = new long[7];
    private static final int[] cooldownLength = new int[7];
    private static final java.util.regex.Pattern COOLDOWN = java.util.regex.Pattern.compile("(회복|평타|회피|스킬 [1-4]) 재사용 대기 · ([0-9]{1,5})ms");
    private static ItemStack weapon = ItemStack.EMPTY;

    private CombatBar() {}

    public static boolean active() {
        var current = client().getConnection();
        if (connection != current || client().player == null) {
            connection = current;
            java.util.Arrays.fill(cooldownEnd, 0);
            savedSlot = -1;
            weapon = ItemStack.EMPTY;
        }
        return savedSlot >= 0;
    }

    public static boolean key(int key, int action) {
        var client = client();
        if (client.player == null || client.getConnection() == null || client.screen != null) return false;
        boolean active = active();
        if (key == GLFW.GLFW_KEY_LEFT_ALT) {
            if (action == GLFW.GLFW_PRESS) toggle();
            return true;
        }
        if (active && key == GLFW.GLFW_KEY_SPACE && action == GLFW.GLFW_PRESS) {
            long now = System.nanoTime();
            if (lastJump != 0 && now - lastJump <= 320_000_000L) {
                lastJump = 0;
                client.getConnection().sendCommand("pipilife skill dodge");
                return true;
            }
            lastJump = now;
        }
        if (!active || key < GLFW.GLFW_KEY_1 || key > GLFW.GLFW_KEY_9) return false;
        if (action == GLFW.GLFW_PRESS) {
            selectedSkill = key - GLFW.GLFW_KEY_1;
            if (selectedSkill < 4) client.getConnection().sendCommand("pipilife skill attack " + (selectedSkill + 1));
            else if (selectedSkill == 4) client.getConnection().sendCommand("pipilife skill heal");

        }
        return true;
    }

    public static void basicAttack() {
        selectedSkill = 5;
        client().getConnection().sendCommand("pipilife skill basic");
    }

    private static boolean isWeapon(ItemStack item) {
        return item.getItem() instanceof SwordItem || item.is(Items.BOW)
            || item.is(Items.BLAZE_ROD) && item.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
    }

    private static void toggle() {
        lastJump = 0;
        var player = client().player;
        var bag = player.getInventory();
        if (active()) {
            bag.selected = savedSlot;
            savedSlot = -1;
            weapon = ItemStack.EMPTY;
            return;
        }
        int slot = bag.selected;
        if (!isWeapon(bag.getItem(slot))) {
            slot = 0;
            while (slot < 9 && !isWeapon(bag.getItem(slot))) slot++;
        }
        if (slot == 9) {
            player.displayClientMessage(Component.literal("생활 핫바에 검, 활 또는 지팡이를 넣으세요."), true);
            return;
        }
        savedSlot = bag.selected;
        bag.selected = slot;
        weapon = bag.getItem(slot);
        selectedSkill = 0;
    }

    static void receiveCooldown(net.neoforged.neoforge.client.event.ClientChatReceivedEvent.System event) {
        var match = COOLDOWN.matcher(event.getMessage().getString());
        if (!match.matches()) return;
        boolean showing = active();
        int slot = switch (match.group(1)) {
            case "회복" -> 0;
            case "평타" -> 5;
            case "회피" -> 6;
            default -> match.group(1).charAt(3) - '0';
        };
        int millis = Integer.parseInt(match.group(2));
        if (millis <= 0) return;
        cooldownLength[slot] = millis;
        cooldownEnd[slot] = System.nanoTime() / 1_000_000L + millis;
        if (showing) event.setCanceled(true);
    }

    private static void gauge(GuiGraphics graphics, int slot, int x, int y, int width) {
        long remaining = cooldownEnd[slot] - System.nanoTime() / 1_000_000L;
        if (remaining <= 0 || cooldownLength[slot] <= 0) return;
        int filled = (int) ((1 - Math.clamp((double) remaining / cooldownLength[slot], 0, 1)) * width);
        graphics.fill(x, y, x + width, y + 16, 0xc0091420);
        graphics.fill(x, y + 16, x + width, y + 19, 0xff203147);
        if (filled > 0) graphics.fill(x, y + 16, x + filled, y + 19, 0xff69d9e8);
        String seconds = remaining < 1000 ? String.format(java.util.Locale.ROOT, "%.1f", Math.ceil(remaining / 100.0) / 10)
            : Long.toString((remaining + 999) / 1000);
        graphics.drawCenteredString(client().font, seconds, x + width / 2, y + 4, 0xffe9f8ff);
    }

    static void draw(GuiGraphics graphics) {
        var client = client();
        if (!active() || client.screen != null || client.options.hideGui) return;
        int x = graphics.guiWidth() / 2 - 91;
        int y = graphics.guiHeight() - 22;
        graphics.fill(x + 2, y - 7, x + 180, y + 4, 0xff314359);
        graphics.fill(x, y, x + 182, y + 22, 0xff111b27);
        String[] icons = weapon.is(Items.BOW) ? new String[]{"➶", "◎", "⇊"}
            : weapon.is(Items.BLAZE_ROD) ? new String[]{"✦", "❄", "✹"} : new String[]{"Ⅰ", "↓", "↻"};
        for (int i = 0; i < 9; i++) {
            int sx = x + 1 + i * 20;
            graphics.fill(sx, y + 1, sx + 19, y + 21, i == selectedSkill ? 0xff78b6d4 : 0xff3b5267);
            graphics.fill(sx + 1, y + 2, sx + 18, y + 20, 0xff1b2b3d);
            if (i == 8) {
                graphics.renderItem(weapon, sx + 1, y + 3);
                continue;
            }
            String icon = i < 3 ? icons[i] : i == 3 ? "◇" : i == 4 ? "+" : i == 5 ? "⚔" : i == 6 ? "F" : i == 7 ? "G" : "·";
            graphics.drawCenteredString(client.font, icon, sx + 10, y + 5, i == 4 ? 0xff85e0aa : 0xffe5d6ac);
            graphics.drawString(client.font, i == 5 ? "L" : Integer.toString(i + 1), sx + 2, y + 12, 0xff9cb4c9, false);
            if (i < 6) gauge(graphics, i == 4 ? 0 : i == 5 ? 5 : i + 1, sx + 1, y + 2, 17);
        }
        graphics.fill(x + 185, y, x + 214, y + 22, 0xff22344a);
        graphics.drawCenteredString(client.font, "회피", x + 199, y + 6, 0xffd7eafa);
        gauge(graphics, 6, x + 187, y + 2, 25);
    }
}
