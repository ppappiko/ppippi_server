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
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.Unbreakable;

/** Server item transport: "B"+base64(item NBT bytes) or "T"+base64(UTF-8 JSON {id,count,name,lore,owner,cmd,glint,unbreakable}). */
final class ItemWire {
    private ItemWire() {}

    static ItemStack decode(String wire) {
        try {
            if (wire == null) return ItemStack.EMPTY;
            wire = wire.trim();
            if (wire.length() < 2) return ItemStack.EMPTY;
            byte[] data = Base64.getDecoder().decode(wire.substring(1));
            return switch (wire.charAt(0)) {
                case 'B' -> fromBytes(data);
                case 'T' -> fromJson(data);
                default -> ItemStack.EMPTY;
            };
        } catch (RuntimeException | java.io.IOException ex) {
            return ItemStack.EMPTY;
        }
    }

    private static ItemStack fromBytes(byte[] data) throws java.io.IOException {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return ItemStack.EMPTY;
        CompoundTag tag = NbtIo.readCompressed(new ByteArrayInputStream(data), NbtAccounter.unlimitedHeap());
        tag.remove("DataVersion");
        return ItemStack.parse(mc.level.registryAccess(), tag).orElse(ItemStack.EMPTY);
    }

    private static ItemStack fromJson(byte[] data) {
        var obj = com.google.gson.JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
        ResourceLocation id = ResourceLocation.parse(obj.get("id").getAsString());
        var item = BuiltInRegistries.ITEM.get(id);
        ItemStack stack = new ItemStack(item, Math.max(1, obj.get("count").getAsInt()));
        String name = obj.get("name").getAsString();
        if (!name.isEmpty()) stack.set(DataComponents.CUSTOM_NAME, legacy(name));
        List<Component> lore = new ArrayList<>();
        for (var e : obj.getAsJsonArray("lore")) lore.add(legacy(e.getAsString()));
        if (!lore.isEmpty()) stack.set(DataComponents.LORE, new ItemLore(lore));
        int cmd = obj.get("cmd").getAsInt();
        if (cmd >= 0) stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(cmd));
        if (obj.has("glint") && obj.get("glint").getAsBoolean()) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, Boolean.TRUE);
        if (obj.has("unbreakable") && obj.get("unbreakable").getAsBoolean()) stack.set(DataComponents.UNBREAKABLE, new Unbreakable(false));
        String owner = obj.has("owner") ? obj.get("owner").getAsString() : "";
        if (!owner.isEmpty()) {
            CompoundTag values = new CompoundTag();
            values.putString("pipilife:owner", owner);
            CompoundTag custom = new CompoundTag();
            custom.put("PublicBukkitValues", values);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        }
        return stack;
    }

    /** Legacy section-sign text to a styled component (no italics default, matching Bukkit names). */
    private static Component legacy(String s) {
        MutableComponent root = Component.empty();
        Style style = Style.EMPTY.withItalic(false);
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§' && i + 1 < s.length()) {
                if (buf.length() > 0) { root.append(Component.literal(buf.toString()).withStyle(style)); buf.setLength(0); }
                ChatFormatting f = ChatFormatting.getByCode(s.charAt(++i));
                if (f == null) continue;
                if (f == ChatFormatting.RESET) style = Style.EMPTY.withItalic(false);
                else style = style.applyFormat(f);
            } else buf.append(c);
        }
        if (buf.length() > 0) root.append(Component.literal(buf.toString()).withStyle(style));
        return root;
    }
}
