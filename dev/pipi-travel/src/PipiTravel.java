package dev.pipi.travel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

@Mod(value = "pipitravel", dist = Dist.CLIENT)
public final class PipiTravel {
    public PipiTravel(IEventBus bus) {
        bus.addListener(this::registerHud);
        NeoForge.EVENT_BUS.addListener(CombatBar::receiveCooldown);
        NeoForge.EVENT_BUS.addListener(CaptureInput::tick);
        NeoForge.EVENT_BUS.addListener(CaptureInput::receive);
        NeoForge.EVENT_BUS.addListener(CaptureInput::fov);
        NeoForge.EVENT_BUS.addListener(this::openResearch);
        NeoForge.EVENT_BUS.addListener(GearTooltips::addDetails);
        NeoForge.EVENT_BUS.addListener(GearTooltips::color);
    }

    private void openResearch(ScreenEvent.Opening event) {
        if (!(event.getNewScreen() instanceof ContainerScreen incoming)
            || incoming instanceof PetMarketScreen || incoming instanceof CharacterScreen || incoming instanceof UpgradeScreen || incoming instanceof ShopScreen || incoming instanceof FoodStorageScreen || incoming instanceof AlchemyScreen || incoming instanceof ForgeScreen || incoming instanceof ResearchScreen || incoming instanceof CraftScreen || incoming instanceof PetStorageScreen ) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        if (incoming.getMenu().getRowCount() == 3 && java.util.Set.of("펫 먹이통", "식량 저장소").contains(incoming.getTitle().getString())) {
            event.setNewScreen(new FoodStorageScreen(incoming.getMenu(), client.player.getInventory()));
            return;
        }
        if (incoming.getMenu().getRowCount() != 6) return;
        ResearchScreen previous = event.getCurrentScreen() instanceof ResearchScreen research ? research : null;
        if (PetMarketScreen.TITLE.equals(incoming.getTitle().getString()))
            event.setNewScreen(new PetMarketScreen(incoming.getMenu(),client.player.getInventory(),incoming.getTitle()));
        else if (CharacterScreen.TITLE.equals(incoming.getTitle().getString()))
            event.setNewScreen(new CharacterScreen(incoming.getMenu(), client.player.getInventory(), incoming.getTitle()));
        else if (UpgradeScreen.TITLE.equals(incoming.getTitle().getString()))
            event.setNewScreen(new UpgradeScreen(incoming.getMenu(), client.player.getInventory(), incoming.getTitle()));
        else if (ShopScreen.TITLE.equals(incoming.getTitle().getString()))
            event.setNewScreen(new ShopScreen(incoming.getMenu(), client.player.getInventory(), incoming.getTitle(),
                event.getCurrentScreen() instanceof ShopScreen shop ? shop : null));
        else if (AlchemyScreen.TITLE.equals(incoming.getTitle().getString()))
            event.setNewScreen(new AlchemyScreen(incoming.getMenu(), client.player.getInventory(), incoming.getTitle()));
        else if (ForgeScreen.MENU.equals(incoming.getTitle().getString()) || ForgeScreen.CRAFT.equals(incoming.getTitle().getString()))
            event.setNewScreen(new ForgeScreen(incoming.getMenu(), client.player.getInventory(), incoming.getTitle()));
        else if (ResearchScreen.TITLE.equals(incoming.getTitle().getString()))
            event.setNewScreen(new ResearchScreen(incoming.getMenu(), client.player.getInventory(), incoming.getTitle(), previous));
        else if (PetStorageScreen.TITLE.equals(incoming.getTitle().getString()))
            event.setNewScreen(new PetStorageScreen(incoming.getMenu(), client.player.getInventory(), incoming.getTitle(),
                event.getCurrentScreen() instanceof PetStorageScreen box ? box : null));
        else if (CraftScreen.TITLE.equals(incoming.getTitle().getString()) || CraftScreen.isProductionTitle(incoming.getTitle().getString()))
            event.setNewScreen(new CraftScreen(incoming.getMenu(), client.player.getInventory(), incoming.getTitle(),
                event.getCurrentScreen() instanceof CraftScreen craft ? craft : null));
    }

    private void registerHud(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath("pipitravel", "capture_aim"), (graphics, delta) -> CaptureInput.draw(graphics));
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath("pipitravel", "combat_bar"), (graphics, delta) -> CombatBar.draw(graphics));
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath("pipitravel", "menu_bar"), (graphics, delta) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null && client.level != null && !client.options.hideGui
                && !(client.screen instanceof PipiMenu) && !(client.screen instanceof ResearchScreen) && !(client.screen instanceof CraftScreen) && !(client.screen instanceof PetStorageScreen) && !(client.screen instanceof ForgeScreen) && !(client.screen instanceof AlchemyScreen) && !(client.screen instanceof ShopScreen) && !(client.screen instanceof UpgradeScreen) && !(client.screen instanceof CharacterScreen) && !(client.screen instanceof PetMarketScreen)) drawBar(graphics, false);
        });
    }

    static void drawBar(GuiGraphics graphics, boolean expanded) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        int right = expanded ? 329 : 149;
        graphics.fill(8, 8, right, 36, 0xDE171D2C);
        graphics.fill(8, 35, right, 36, 0xFF7686B1);
        graphics.drawString(client.font, "삐삐서버", 17, 18, 0xFFF1F3FF);
        int minutes = (int) ((Math.floorMod(client.level.getDayTime(), 24000L) * 60 / 1000 + 360) % 1440);
        graphics.drawString(client.font, "%02d:%02d".formatted(minutes / 60, minutes % 60), 72, 18, 0xFFCDD5ED);
        graphics.drawString(client.font, "F1", 123, 18, 0xFFE8C579);
    }
}

