package dev.pipi.travel;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.client.event.ModelEvent;

/** v5 inventory icons for PipiLife armour. Stacks are identified by custom_data PublicBukkitValues["pipilife:smith_catalog"] ("3티어 근거리 투구");
 *  the baked model is swapped client-side (see ItemRendererArmorMixin); vanilla/ItemsAdder item models are untouched. */
public final class ArmorIcons {
    private static final Pattern LABEL = Pattern.compile("^(\\d)티어 (근거리|원거리|마법) (투구|갑옷|각반|신발)");
    private static final String[] CLASS = {"warrior", "archer", "mage"}, SLOT = {"helmet", "chest", "legs", "boots"};
    private static final String[] FAMILY = {"근거리", "원거리", "마법"}, WORD = {"투구", "갑옷", "각반", "신발"};
    private static final Map<CustomData, ModelResourceLocation> CACHE = new HashMap<>();
    private static final ModelResourceLocation NONE = standalone(ResourceLocation.fromNamespaceAndPath("pipitravel", "item/armor/none"));
    private static CustomData lastKey;
    private static ModelResourceLocation lastVal;

    private ArmorIcons() {}

    /** 1.21.1 NeoForge has no ModelResourceLocation.standalone(): additional models use the "standalone" variant. */
    private static ModelResourceLocation standalone(ResourceLocation id) { return new ModelResourceLocation(id, "standalone"); }

    private static ModelResourceLocation mrl(int cls, int tier, int slot) {
        return standalone(ResourceLocation.fromNamespaceAndPath("pipitravel", "item/armor/" + CLASS[cls] + "_t" + tier + "_" + SLOT[slot]));
    }

    /** Accessory icons in assets/pipitravel/models/item/accessory/<slot>_<model>.json (made by dev/art/make-accessories.py). */
    private static final String[] ACCESSORIES = {"necklace_basic", "earring_basic", "bracelet_basic"};

    private static ModelResourceLocation accessory(String name) {
        return standalone(ResourceLocation.fromNamespaceAndPath("pipitravel", "item/accessory/" + name));
    }

    public static void register(ModelEvent.RegisterAdditional event) {
        for (int c = 0; c < 3; c++) for (int t = 0; t <= 5; t++) for (int s = 0; s < 4; s++) event.register(mrl(c, t, s));
        for (String name : ACCESSORIES) event.register(accessory(name));
    }

    /** pipilife:accessory = slot, pipilife:accessory_model = model; an unknown model falls back to the slot's basic icon. */
    private static ModelResourceLocation accessoryFor(CompoundTag pub) {
        String slot = pub.getString("pipilife:accessory");
        if (!slot.matches("[a-z]+")) return null;
        String name = slot + "_" + pub.getString("pipilife:accessory_model");
        for (String known : ACCESSORIES) if (known.equals(name)) return accessory(name);
        for (String known : ACCESSORIES) if (known.equals(slot + "_basic")) return accessory(known);
        return null;
    }

    /** Standalone model for a PipiLife armour stack, or null for anything else. */
    public static ModelResourceLocation modelFor(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        if (data == lastKey) return lastVal;
        ModelResourceLocation found = CACHE.get(data);
        if (found == null) {
            found = NONE;
            CompoundTag tag = data.getUnsafe();
            if (tag.contains("PublicBukkitValues", 10)) {
                CompoundTag pub = tag.getCompound("PublicBukkitValues");
                String label = pub.getString("pipilife:smith_catalog");
                Matcher m = LABEL.matcher(label);
                ModelResourceLocation acc = pub.contains("pipilife:accessory") ? accessoryFor(pub) : null;
                if (acc != null) found = acc;
                else if (m.find()) {
                    int tier = Integer.parseInt(m.group(1));
                    int cls = 0, slot = 0;
                    for (int i = 0; i < 3; i++) if (FAMILY[i].equals(m.group(2))) cls = i;
                    for (int i = 0; i < 4; i++) if (WORD[i].equals(m.group(3))) slot = i;
                    if (tier <= 5) found = mrl(cls, tier, slot);
                }
            }
            if (CACHE.size() > 512) CACHE.clear();
            CACHE.put(data, found);
        }
        lastKey = data;
        lastVal = found == NONE ? null : found;
        return lastVal;
    }
}
