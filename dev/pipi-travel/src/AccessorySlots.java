package dev.pipi.travel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * 악세서리 칸 3개 (목걸이·귀걸이·팔찌)를 가방(E) 화면의 갑옷 칸 쪽 빈자리(플레이어 모습 오른쪽, 왼손 칸 위)에 그린다.
 * 장착 정보는 서버 PipiLife 가 갖고 있고, "PIPI_ACCI:<아이템|아이템|아이템>" (ItemWire, 없으면 -) 으로 받는다.
 * 칸 클릭은 서버 명령어로 보낸다: equip/unequip <slot> (커서), quickoff <slot> (Shift), 가방 칸 Shift+클릭은 quick <invSlot>.
 */
final class AccessorySlots {
    private static final String PREFIX = "PIPI_ACCI:";
    private static final String[] IDS = {"necklace", "earring", "bracelet"};
    /** 빈 칸에 그리는 회색 실루엣 (바닐라 빈 갑옷 칸처럼). dev/art/make-accessories.py 가 만든다. */
    static final ResourceLocation[] EMPTY = {
        ResourceLocation.fromNamespaceAndPath("pipitravel", "textures/gui/accessory/empty_necklace.png"),
        ResourceLocation.fromNamespaceAndPath("pipitravel", "textures/gui/accessory/empty_earring.png"),
        ResourceLocation.fromNamespaceAndPath("pipitravel", "textures/gui/accessory/empty_bracelet.png")};
    /** 바닐라 가방 화면 기준 칸 위치: x 77, y 8 / 26 / 44 (그 아래 y 62 가 왼손 칸) */
    private static final int X = 77, Y = 8, STEP = 18;
    private static final ItemStack[] ITEMS = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    private static long lastClick;

    private AccessorySlots() {}

    static void receive(ClientChatReceivedEvent.System event) {
        String text = event.getMessage().getString();
        if (!text.startsWith(PREFIX)) return;
        event.setCanceled(true);
        process(text);
    }

    /** Also called from ArmoryScreen.dispatch for lines the server split into PIPI_ARM_PART chunks. */
    static void process(String text) {
        String[] parts = text.substring(PREFIX.length()).split("\\|", -1);
        for (int i = 0; i < ITEMS.length; i++) {
            String part = i < parts.length ? parts[i] : "-";
            if (part.equals("-")) { ITEMS[i] = ItemStack.EMPTY; continue; }
            int cut = part.indexOf('~');
            String model = cut > 0 ? part.substring(0, cut) : "basic";
            ItemStack stack = ItemWire.decode(cut > 0 ? part.substring(cut + 1) : part);
            if (!stack.isEmpty()) tag(stack, IDS[i], model);
            ITEMS[i] = stack;
        }
    }

    /** 아이템 전송 중 PublicBukkitValues 가 빠져도 ArmorIcons 가 악세서리 아이콘을 고를 수 있게 종류·모델을 다시 적는다. */
    private static void tag(ItemStack stack, String slot, String model) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();
        CompoundTag pub = tag.getCompound("PublicBukkitValues");
        pub.putString("pipilife:accessory", slot);
        if (model.matches("[a-z0-9_]+")) pub.putString("pipilife:accessory_model", model);
        tag.put("PublicBukkitValues", pub);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    static boolean isAccessory(ItemStack stack) {
        if (stack.isEmpty()) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return false;
        CompoundTag tag = data.copyTag();
        CompoundTag pub = tag.contains("PublicBukkitValues") ? tag.getCompound("PublicBukkitValues") : tag;
        return pub.contains("pipilife:accessory");
    }

    private static int slotAt(InventoryScreen screen, double mx, double my) {
        double x = mx - screen.getGuiLeft() - X, y = my - screen.getGuiTop() - Y;
        if (x < -1 || x >= 17) return -1;
        for (int i = 0; i < IDS.length; i++) if (y >= i * STEP - 1 && y < i * STEP + 17) return i;
        return -1;
    }

    static void render(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen screen)) return;
        GuiGraphics g = event.getGuiGraphics();
        var font = Minecraft.getInstance().font;
        int left = screen.getGuiLeft(), top = screen.getGuiTop();
        int hover = slotAt(screen, event.getMouseX(), event.getMouseY());
        g.pose().pushPose();
        g.pose().translate(0, 0, 100);
        for (int i = 0; i < IDS.length; i++) {
            int x = left + X, y = top + Y + i * STEP;
            // 바닐라 칸 모양 (안쪽 회색, 왼쪽·위 어둡게, 오른쪽·아래 밝게)
            g.fill(x - 1, y - 1, x + 17, y, 0xff373737);
            g.fill(x - 1, y - 1, x, y + 17, 0xff373737);
            g.fill(x, y + 16, x + 17, y + 17, 0xffffffff);
            g.fill(x + 16, y, x + 17, y + 17, 0xffffffff);
            g.fill(x, y, x + 16, y + 16, 0xff8b8b8b);
            if (ITEMS[i].isEmpty()) g.blit(EMPTY[i], x, y, 0, 0, 16, 16, 16, 16);
            else { g.renderItem(ITEMS[i], x, y); g.renderItemDecorations(font, ITEMS[i], x, y); }
            if (i == hover) g.fill(x, y, x + 16, y + 16, 0x80ffffff);
        }
        g.pose().popPose();
        if (hover >= 0 && screen.getMenu().getCarried().isEmpty()) {
            if (ITEMS[hover].isEmpty()) g.renderTooltip(font, net.minecraft.network.chat.Component.literal("§7" + new String[]{"목걸이", "귀걸이", "팔찌"}[hover] + " 칸"), event.getMouseX(), event.getMouseY());
            else g.renderTooltip(font, ITEMS[hover], event.getMouseX(), event.getMouseY());
        }
    }

    static void click(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen screen)) return;
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) return;
        int i = slotAt(screen, event.getMouseX(), event.getMouseY());
        boolean shift = Screen.hasShiftDown();
        if (i < 0) {
            // 가방 칸의 악세서리를 Shift+클릭: 맞는 악세서리 칸으로 장착
            var slot = screen.getSlotUnderMouse();
            if (shift && event.getButton() == 0 && slot != null && slot.container instanceof Inventory && slot.getContainerSlot() <= 35
                && isAccessory(slot.getItem()) && screen.getMenu().getCarried().isEmpty()) {
                event.setCanceled(true);
                if (throttle()) connection.sendCommand("pipilife accessory quick " + slot.getContainerSlot());
            }
            return;
        }
        event.setCanceled(true);
        if (event.getButton() != 0 && event.getButton() != 1 || !throttle()) return;
        ItemStack carried = screen.getMenu().getCarried();
        if (shift) { if (!ITEMS[i].isEmpty()) connection.sendCommand("pipilife accessory quickoff " + IDS[i]); }
        else if (!carried.isEmpty()) connection.sendCommand("pipilife accessory equip " + IDS[i]);
        else if (!ITEMS[i].isEmpty()) connection.sendCommand("pipilife accessory unequip " + IDS[i]);
    }

    static void release(ScreenEvent.MouseButtonReleased.Pre event) {
        if (event.getScreen() instanceof InventoryScreen screen && slotAt(screen, event.getMouseX(), event.getMouseY()) >= 0) event.setCanceled(true);
    }

    private static boolean throttle() {
        long now = System.currentTimeMillis();
        if (now - lastClick < 150) return false;
        lastClick = now;
        return true;
    }
}
