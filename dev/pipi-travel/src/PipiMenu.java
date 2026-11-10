package dev.pipi.travel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class PipiMenu extends Screen {
    public PipiMenu() { super(Component.literal("삐삐서버 메뉴")); }

    @Override protected void init() {
        button(151, "이동", "travel");
        button(203, "캐릭터", "profile");
        button(255, "펫", "pets");
        addRenderableWidget(Button.builder(Component.literal("×"), button -> onClose())
            .bounds(307, 12, 18, 20).build());
    }

    private void button(int x, String label, String command) {
        addRenderableWidget(Button.builder(Component.literal(label), button -> {
            Minecraft client = Minecraft.getInstance();
            if (client.getConnection() == null) return;
            client.setScreen(null);
            client.getConnection().sendCommand("pipilife " + command);
        }).bounds(x, 12, 49, 20).build());
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        PipiTravel.drawBar(graphics, true);
        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {}
    @Override public boolean isPauseScreen() { return false; }
}
