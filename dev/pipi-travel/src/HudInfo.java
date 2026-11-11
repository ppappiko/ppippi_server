package dev.pipi.travel;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

/** Top-left gold row + objective panel. Key: J = objectives. */
public final class HudInfo {
    private static final String GOLD_PREFIX = "PIPI_GOLD:", QUEST_PREFIX = "PIPI_QUEST:";

    private static long gold = -1;
    private static String questTitle = "";
    private static List<String> questLines = List.of();
    private static Object owner;
    private static boolean questOpen = true;

    private HudInfo() {}

    private static Minecraft client() { return Minecraft.getInstance(); }

    static void receive(ClientChatReceivedEvent.System event) {
        String text = event.getMessage().getString();
        if (text.startsWith(GOLD_PREFIX)) {
            event.setCanceled(true);
            try { gold = Long.parseLong(text.substring(GOLD_PREFIX.length()).trim()); } catch (NumberFormatException ignored) { return; }
            owner = client().getConnection();
        } else if (text.startsWith(QUEST_PREFIX)) {
            event.setCanceled(true);
            String[] parts = text.substring(QUEST_PREFIX.length()).split("\\|");
            questTitle = parts.length == 0 ? "" : parts[0].trim();
            List<String> lines = new ArrayList<>();
            for (int i = 1; i < parts.length && lines.size() < 5; i++) if (!parts[i].isBlank()) lines.add(parts[i].trim());
            questLines = lines;
            owner = client().getConnection();
        }
    }

    static void key(InputEvent.Key event) {
        var client = client();
        if (client.player == null || client.screen != null || event.getAction() != GLFW.GLFW_PRESS) return;
        if (event.getKey() == GLFW.GLFW_KEY_J) questOpen = !questOpen;
    }

    private static boolean visible() {
        var client = client();
        return client.player != null && client.level != null && !client.options.hideGui
            && !client.getDebugOverlay().showDebugScreen() // F3 open: get out of the way of the debug text (FPS line)
            && (client.screen == null || client.screen instanceof ChatScreen);
    }

    static String goldText() {
        var client = client();
        if (owner != client.getConnection()) { owner = null; gold = -1; questTitle = ""; questLines = List.of(); }
        return gold < 0 ? "-" : "%,d".formatted(gold);
    }

    /** Called from the existing top-left bar: coin icon + gold on a second row. */
    static void drawGold(GuiGraphics g, int x, int y) {
        var font = client().font;
        coin(g, x, y);
        g.drawString(font, "골드", x + 13, y + 1, 0xFFCDD5ED);
        String text = goldText();
        g.drawString(font, text, 141 - font.width(text), y + 1, 0xFFF6D56B);
    }

    private static void coin(GuiGraphics g, int x, int y) {
        int[] half = {2, 3, 4, 4, 4, 3, 2};
        for (int i = 0; i < 7; i++) g.fill(x + 4 - half[i],y + i + 1, x + 4 + half[i] + 1, y + i + 2, 0xFFE8B923);
        g.fill(x + 3, y + 3, x + 6, y + 6, 0xFFFFE27A);
        g.fill(x + 4, y + 4, x + 5, y + 5, 0xFFB8860B);
    }

    static void draw(GuiGraphics g) {
        if (!visible()) return;
        var client = client();
        var font = client.font;
        goldText(); // owner reset
        // objective panel under the info bar (bar is 8..52 tall with the gold row)
        int coordTop = 58;
        if (!questTitle.isEmpty() && !questLines.isEmpty()) {
            int w = 141;
            for (String line : questLines) w = Math.max(w, font.width(line) + 28);
            int top = 58;
            int h = questOpen ? 16 + questLines.size() * 11 + 2 : 16;
            g.fill(8, top, 8 + w, top + h, 0xDE171D2C);
            g.fill(8, top, 11, top + h, 0xFFE8C579);
            g.drawString(font, (questOpen ? "▼ " : "▲ ") + questTitle, 17, top + 4, 0xFFF1F3FF);
            g.drawString(font, "J", 8 + w - 12, top + 4, 0xFFE8C579);
            if (questOpen) {
                int y = top + 16;
                for (String line : questLines) {
                    g.fill(17, y + 3, 20, y + 6, 0xFF7686B1);
                    g.drawString(font, line, 25, y, 0xFFCDD5ED);
                    y += 11;
                }
            }
            coordTop = top + h + 3;
        }
        drawCoords(g, coordTop);
    }

    private static final String[] DIRS = {"남", "남서", "서", "북서", "북", "북동", "동", "남동"};

    /** Block coordinates + facing under the top-left panels; hidden while F3 is open. */
    private static void drawCoords(GuiGraphics g, int top) {
        var client = client();
        if (client.getDebugOverlay().showDebugScreen()) return;
        var p = client.player;
        var font = client.font;
        int idx = Math.floorMod((int) Math.floor(p.getYRot() / 45.0 + 0.5), 8);
        String text = "X " + p.getBlockX() + "  Y " + p.getBlockY() + "  Z " + p.getBlockZ() + "  " + DIRS[idx];
        int w = Math.max(141, font.width(text) + 20);
        g.fill(8, top, 8 + w, top + 15, 0xDE171D2C);
        g.fill(8, top, 11, top + 15, 0xFF7686B1);
        g.drawString(font, text, 17, top + 4, 0xFFE4E9FA);
    }
}
