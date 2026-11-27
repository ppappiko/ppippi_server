package dev.pipi.travel;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Party list on the left (under the top-left info panels): diamond class frame, name, HP bar.
 * HP bar is red when the member is in the same area (world) as you, dark grey when not.
 * Server: PIPI_PARTY:name,cls,hp,max,same,leader,online[,area,dist];...  (empty = not in a party). Key: P = the party window (PartyScreen),
 * which also shows / hides this list.
 */
final class PartyHud {
    static final KeyMapping TOGGLE_KEY = new KeyMapping("파티 창", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_P, "PipiTravel");
    private static final String PREFIX = "PIPI_PARTY:";

    record Member(String name, int cls, long hp, long max, boolean same, boolean leader, boolean online, String area, long dist) {}

    static List<Member> members = List.of();
    static boolean shown = true;
    private static Object owner;

    private PartyHud() {}

    private static Minecraft client() { return Minecraft.getInstance(); }

    static void receive(ClientChatReceivedEvent.System event) {
        String text = event.getMessage().getString();
        if (!text.startsWith(PREFIX)) return;
        event.setCanceled(true);
        boolean wasEmpty = members.isEmpty();
        List<Member> out = new ArrayList<>();
        for (String e : text.substring(PREFIX.length()).split(";")) {
            String[] p = e.split(",");
            if (p.length < 7) continue;
            try {
                out.add(new Member(p[0], ClassIcons.parse(p[1]), Long.parseLong(p[2].trim()), Math.max(1, Long.parseLong(p[3].trim())),
                    p[4].trim().equals("1"), p[5].trim().equals("1"), p[6].trim().equals("1"),
                    p.length > 7 ? p[7].trim() : "", p.length > 8 ? Long.parseLong(p[8].trim()) : -1));
            } catch (NumberFormatException ignored) { }
        }
        members = out;
        owner = client().getConnection();
        if (wasEmpty && !out.isEmpty()) shown = true; // joining a party shows the list
    }

    static void key(ClientTickEvent.Post event) {
        var client = client();
        if (client.player == null || client.screen != null) { while (TOGGLE_KEY.consumeClick()) { } return; }
        while (TOGGLE_KEY.consumeClick()) client.setScreen(new PartyScreen());
    }

    static void draw(GuiGraphics g) {
        var client = client();
        if (owner != client.getConnection()) { owner = null; members = List.of(); }
        if (members.isEmpty() || !shown) return;
        if (client.player == null || client.options.hideGui || client.getDebugOverlay().showDebugScreen()) return;
        if (client.screen != null && !(client.screen instanceof ChatScreen) && !(client.screen instanceof RaidHud.VoteScreen)) return;
        var font = client.font;
        int top = Math.max(HudInfo.bottom + 10, (int) (g.guiHeight() * .30f));
        int x = 8;
        g.drawString(font, "파티 " + members.size() + "/4  §8[" + TOGGLE_KEY.getTranslatedKeyMessage().getString() + "]", x + 2, top, 0xffe8c579);
        int y = top + 12;
        for (Member m : members) {
            int cx = x + 13, cy = y + 13;
            ClassIcons.diamond(g, cx, cy, 13, m.leader ? 0xffe8c579 : 0xffb8bcc8, 0xff141a26);
            ClassIcons.diamond(g, cx, cy, 10.5f, 0x00000000, 0xff1e2636);
            if (m.cls >= 0) ClassIcons.draw(g, m.cls, cx - 8f, cy - 8f, 16f, m.online ? 1f : .45f);
            int nx = x + 30;
            String name = (m.leader ? "★ " : "") + m.name;
            g.fill(nx - 2, y + 3, nx + 96, y + 23, 0x60000000);
            g.drawString(font, font.plainSubstrByWidth(name, 94), nx, y + 5, m.online ? 0xfff1f3ff : 0xff7d8597);
            int bw = 92, by = y + 15;
            g.fill(nx - 1, by - 1, nx + bw + 1, by + 5, 0xff000000);
            g.fill(nx, by, nx + bw, by + 4, 0xff2a2a30);
            if (m.online) {
                int fw = (int) Math.round(bw * Math.clamp(m.hp / (double) m.max, 0, 1));
                g.fill(nx, by, nx + fw, by + 4, m.same ? 0xffd23a3a : 0xff5a5f6a);
                if (m.same) g.fill(nx, by, nx + fw, by + 1, 0x50ffffff);
            }
            y += 30;
        }
    }
}
