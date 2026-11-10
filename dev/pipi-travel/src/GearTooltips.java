package dev.pipi.travel;

import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemLore;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

final class GearTooltips {
    private static final Pattern SLOTS = Pattern.compile("잠재 슬롯 ([3-7])개");

    private static int slots(ItemStack stack) {
        for (Component line : stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines()) {
            var match = SLOTS.matcher(ChatFormatting.stripFormatting(line.getString()));
            if (match.matches()) return Integer.parseInt(match.group(1));
        }
        return 0;
    }

    private static boolean isGear(ItemStack stack) {
        var item = stack.getItem();
        return item instanceof TieredItem || item instanceof ArmorItem || item instanceof BowItem
            || item instanceof CrossbowItem || item instanceof FishingRodItem || item instanceof TridentItem
            || item instanceof ShieldItem || item instanceof MaceItem || item instanceof ShearsItem || slots(stack) > 0;
    }

    static void addDetails(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!isGear(stack) || event.getToolTip().isEmpty()) return;
        var lines = event.getToolTip();
        int slots = slots(stack);
        lines.set(0, lines.getFirst().copy().withStyle(ChatFormatting.GREEN));
        if (stack.isDamageableItem()) {
            int max = stack.getMaxDamage();
            lines.add(1, Component.literal("내구도  " + (max - stack.getDamageValue()) + " / " + max)
                .withStyle(ChatFormatting.GRAY));
        }
        if (slots == 0) return;
        int oldHint = -1;
        boolean existingSection = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = ChatFormatting.stripFormatting(lines.get(i).getString());
            if (line.equals("인챈트")) existingSection = true;
            if (line.equals("큐브를 사용해 잠재 옵션을 부여하세요.")) oldHint = i;
        }
        if (existingSection) return;
        if (oldHint >= 0) {
            lines.remove(oldHint);
            lines.add(oldHint, Component.literal("인챈트").withStyle(ChatFormatting.LIGHT_PURPLE));
            for (int i = 0; i < slots; i++) {
                lines.add(oldHint + i + 1, Component.literal("○ 빈 인챈트 슬롯").withStyle(ChatFormatting.GRAY));
            }
        }
    }

    static void color(RenderTooltipEvent.Color event) {
        if (!isGear(event.getItemStack())) return;
        event.setBackgroundStart(0xf512211a);
        event.setBackgroundEnd(0xf50a0c10);
        event.setBorderStart(0xff66bb53);
        event.setBorderEnd(0xff574477);
    }
}
