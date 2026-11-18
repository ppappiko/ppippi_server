package dev.pipi.travel;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/**
 * Radial player menu opened by right-clicking another player (server: PipiLife Social).
 * Server: PIPI_PMENU:&lt;name&gt;|&lt;party&gt;[|reason]   party: I invite · K kick/promote (I lead) · M same party · X cannot invite (reason).
 * Hover a slice and click (or press its number). ESC / right-click / clicking the centre closes.
 */
final class PlayerMenuScreen extends Screen {
    private static final String PREFIX = "PIPI_PMENU:";
    private static final float INNER = 34, OUTER = 86, GAP_DEG = 1.6f;
    private static final long OPEN_MS = 120;

    private record Slice(String label, ItemStack icon, boolean enabled, String hint, Runnable action, boolean confirm) {}

    private final String target;
    private final List<Slice> slices = new ArrayList<>();
    private final long openedAt = System.currentTimeMillis();
    private int hovered = -1;
    private int armed = -1; // slice waiting for a second click (kick)

    static void receive(ClientChatReceivedEvent.System event) {
        String text = event.getMessage().getString();
        if (!text.startsWith(PREFIX)) return;
        event.setCanceled(true);
        String[] p = text.substring(PREFIX.length()).split("\\|", 3);
        if (p.length < 2 || p[0].isBlank()) return;
        String name = p[0].trim(), party = p[1].trim(), reason = p.length > 2 ? p[2] : "";
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.player == null || (client.screen != null && !(client.screen instanceof PlayerMenuScreen))) return;
            client.setScreen(new PlayerMenuScreen(name, party, reason));
        });
    }

    private PlayerMenuScreen(String target, String party, String reason) {
        super(Component.literal(target));
        this.target = target;
        slices.add(new Slice("거래", new ItemStack(Items.EMERALD), true, "아이템과 골드를 안전하게 교환합니다", () -> send("pipilife social trade " + target), false));
        switch (party) {
            case "I" -> slices.add(new Slice("파티 초대", new ItemStack(Items.CAKE), true, "내 파티에 초대합니다 (최대 4명)", () -> send("pipilife party invite " + target), false));
            case "K" -> {
                slices.add(new Slice("파티장 위임", new ItemStack(Items.GOLDEN_HELMET), true, "이 파티원에게 파티장을 넘깁니다", () -> send("pipilife party promote " + target), false));
                slices.add(new Slice("파티 추방", new ItemStack(Items.BARRIER), true, "파티에서 내보냅니다 (한 번 더 클릭해 확인)", () -> send("pipilife party kick " + target), true));
            }
            case "M" -> slices.add(new Slice("파티원", new ItemStack(Items.CAKE), false, "같은 파티원입니다", () -> { }, false));
            default -> slices.add(new Slice("파티 초대", new ItemStack(Items.CAKE), false, reason.isBlank() ? "지금은 초대할 수 없습니다" : reason, () -> { }, false));
        }
        if (party.equals("K") || party.equals("M"))
            slices.add(new Slice("파티 채팅", new ItemStack(Items.WRITABLE_BOOK), true, "채팅을 파티 채팅으로 전환합니다 (/a 전체)", () -> send("p"), false));
        slices.add(new Slice("상태창", new ItemStack(Items.BOOK), true, "장비와 전투 스탯을 봅니다", () -> send("pipilife social info " + target), false));
        slices.add(new Slice("귓속말", new ItemStack(Items.PAPER), true, "이 플레이어에게만 메시지를 보냅니다", () -> {
            Minecraft.getInstance().setScreen(new ChatScreen("/msg " + target + " "));
        }, false));
    }

    private static void send(String cmd) {
        var c = Minecraft.getInstance().getConnection();
        if (c != null) c.sendCommand(cmd);
    }

    @Override public boolean isPauseScreen() { return false; }

    /** Light dim only (no blur): the target stays visible behind the ring. */
    @Override public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fillGradient(0, 0, width, height, 0x30000000, 0x60000000);
    }

    private int sliceAt(double mx, double my) {
        double dx = mx - width / 2.0, dy = my - height / 2.0;
        if (dx * dx + dy * dy < (INNER * .7) * (INNER * .7)) return -1;
        double span = 360.0 / slices.size();
        double deg = Math.toDegrees(Math.atan2(dy, dx)) + 90 + span / 2; // slice 0 is centred at the top
        return (int) Math.floor(((deg % 360) + 360) % 360 / span);
    }

    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        int n = slices.size();
        int under = sliceAt(mx, my);
        if (under != hovered) { hovered = under; armed = -1; } // moving to another slice cancels a pending confirm
        float cx = width / 2f, cy = height / 2f;
        float t = Math.clamp((System.currentTimeMillis() - openedAt) / (float) OPEN_MS, 0f, 1f);
        float scale = .82f + .18f * (1 - (1 - t) * (1 - t));
        var pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0);
        pose.scale(scale, scale, 1);
        float span = 360f / n;
        for (int i = 0; i < n; i++) {
            Slice s = slices.get(i);
            float a0 = -90 - span / 2 + i * span + GAP_DEG / 2, a1 = a0 + span - GAP_DEG;
            boolean hot = i == hovered;
            int fill = !s.enabled ? 0xC80E121B : hot ? (i == armed ? 0xE8642A2A : 0xE8323E62) : 0xD8171D2C;
            arc(g, 0, 0, INNER, OUTER + (hot ? 4 : 0), a0, a1, fill);
            arc(g, 0, 0, OUTER + (hot ? 4 : 0), OUTER + (hot ? 6 : 1), a0, a1, hot && s.enabled ? 0xFFE8C579 : 0xFF4A5675);
        }
        arc(g, 0, 0, 0, INNER - 3, 0, 360, 0xE60D111A);
        arc(g, 0, 0, INNER - 3, INNER - 2, 0, 360, 0xFF7686B1);
        g.flush();
        float mid = (INNER + OUTER) / 2f + 2;
        for (int i = 0; i < n; i++) {
            Slice s = slices.get(i);
            double a = Math.toRadians(-90 + i * span);
            int ix = Math.round((float) Math.cos(a) * mid), iy = Math.round((float) Math.sin(a) * mid);
            g.renderItem(s.icon, ix - 8, iy - 14);
            String label = (i == armed ? "확인?" : s.label);
            int color = !s.enabled ? 0xFF6A7186 : i == hovered ? 0xFFFFE6A8 : 0xFFE6EAF6;
            g.drawCenteredString(font, label, ix, iy + 4, color);
            if (n > 1 && i < 9) g.drawString(font, String.valueOf(i + 1), ix + 9, iy - 16, 0xFF7D8597);
        }
        g.drawCenteredString(font, font.plainSubstrByWidth(target, (int) (INNER * 2 - 8)), 0, -8, 0xFFF1F3FF);
        g.drawCenteredString(font, "ESC 닫기", 0, 3, 0xFF7D8597);
        pose.popPose();
        if (hovered >= 0) {
            Slice s = slices.get(hovered);
            String hint = hovered == armed ? "§c한 번 더 클릭하면 " + s.label + "합니다" : (s.enabled ? "§f" : "§7") + s.hint;
            int y = (int) (cy + OUTER + 14);
            int w = font.width(hint) + 12;
            g.fill((int) cx - w / 2, y - 4, (int) cx + w / 2, y + 12, 0xD0101522);
            g.drawCenteredString(font, hint, (int) cx, y, 0xFFFFFFFF);
        }
    }

    /** Filled ring sector (degrees, clockwise on screen from +x), same winding as GuiGraphics.fill. */
    private static void arc(GuiGraphics g, float cx, float cy, float r0, float r1, float a0, float a1, int argb) {
        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        int steps = Math.max(2, (int) Math.ceil((a1 - a0) / 4f));
        for (int k = 0; k < steps; k++) {
            double b0 = Math.toRadians(a0 + (a1 - a0) * k / steps), b1 = Math.toRadians(a0 + (a1 - a0) * (k + 1) / steps);
            float c0 = (float) Math.cos(b0), s0 = (float) Math.sin(b0), c1 = (float) Math.cos(b1), s1 = (float) Math.sin(b1);
            vc.addVertex(m, cx + c0 * r0, cy + s0 * r0, 0).setColor(argb);
            vc.addVertex(m, cx + c1 * r0, cy + s1 * r0, 0).setColor(argb);
            vc.addVertex(m, cx + c1 * r1, cy + s1 * r1, 0).setColor(argb);
            vc.addVertex(m, cx + c0 * r1, cy + s0 * r1, 0).setColor(argb);
        }
    }

    private void choose(int i) {
        if (i < 0 || i >= slices.size()) { onClose(); return; }
        Slice s = slices.get(i);
        if (!s.enabled) return;
        if (s.confirm && armed != i) { armed = i; return; }
        onClose();
        s.action.run();
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) { onClose(); return true; }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) { choose(sliceAt(mx, my)); return true; }
        return super.mouseClicked(mx, my, button);
    }

    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) {
            int i = key - GLFW.GLFW_KEY_1;
            if (i < slices.size()) { hovered = i; choose(i); }
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}
