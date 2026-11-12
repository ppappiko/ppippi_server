package dev.pipi.travel;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;

/** /자랑: server sends PIPI_BRAG:name:ItemWire payload; the client rebuilds the stack and shows a real item hover. */
final class BragChat {
    private static final String BRAG = "PIPI_BRAG:";

    private BragChat() {}

    static void receive(ClientChatReceivedEvent.System event) {
        String text = event.getMessage().getString();
        if (!text.startsWith(BRAG)) return;
        event.setCanceled(true);
        String rest = text.substring(BRAG.length());
        int sep = rest.indexOf(':');
        if (sep < 0) return;
        String name = rest.substring(0, sep);
        ItemStack stack = ItemStack.EMPTY;
        stack = ItemWire.decode(rest.substring(sep + 1));
        Component line;
        if (stack.isEmpty()) {
            line = Component.literal(name + "님이 아이템을 자랑합니다!").withStyle(ChatFormatting.YELLOW);
        } else {
            MutableComponent part = Component.literal("[").append(stack.getHoverName()).append(" ×" + stack.getCount() + "]")
                .withStyle(ChatFormatting.AQUA)
                .withStyle(Style.EMPTY.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackInfo(stack))));
            line = Component.literal(name + "님이 ").withStyle(ChatFormatting.YELLOW).append(part)
                .append(Component.literal("을(를) 자랑합니다!").withStyle(ChatFormatting.YELLOW));
        }
        Minecraft.getInstance().gui.getChat().addMessage(line);
    }

}
