package dev.pipi.travel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Party chat mode tag drawn just above the chat input while chatting to the party.
 * Server (PipiLife PartySystem): PIPI_CHATMODE:party|all  — /p switches to party chat, /a back to all.
 */
final class ChatModeTag {
    private static final String PREFIX = "PIPI_CHATMODE:";
    private static boolean party;
    private static Object owner;

    private ChatModeTag() {}

    static void receive(ClientChatReceivedEvent.System event) {
        String text = event.getMessage().getString();
        if (!text.startsWith(PREFIX)) return;
        event.setCanceled(true);
        party = text.substring(PREFIX.length()).trim().equals("party");
        owner = Minecraft.getInstance().getConnection();
    }

    static void render(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof ChatScreen screen)) return;
        if (owner != Minecraft.getInstance().getConnection()) { party = false; owner = null; }
        if (!party) return;
        var g = event.getGuiGraphics();
        var font = Minecraft.getInstance().font;
        String tag = "파티 채팅", help = " /a 전체";
        int x = 2, y = screen.height - 27;
        int w = font.width(tag) + font.width(help) + 10;
        g.fill(x, y, x + w, y + 13, 0xD0261A33);
        g.fill(x, y, x + 2, y + 13, 0xFFD27AF0);
        g.drawString(font, tag, x + 6, y + 3, 0xFFF0B8FF);
        g.drawString(font, help, x + 6 + font.width(tag), y + 3, 0xFF9AA0B4);
    }
}
