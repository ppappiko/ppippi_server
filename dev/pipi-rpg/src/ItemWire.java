package dev.pipi.life;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Item transport for hidden chat lines: "B"+base64(serializeAsBytes) when the server has it, else "T"+base64(UTF-8 JSON). */
final class ItemWire {
    private static java.lang.reflect.Method bytesMethod;
    private static boolean probed, warned;

    private ItemWire() {}

    private static synchronized java.lang.reflect.Method bytesMethod() {
        if (!probed) {
            probed = true;
            try { bytesMethod = ItemStack.class.getMethod("serializeAsBytes"); } catch (ReflectiveOperationException | LinkageError ex) { bytesMethod = null; }
        }
        return bytesMethod;
    }

    private static String json(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> { if (c < 0x20) b.append(String.format("\\u%04x", (int) c)); else b.append(c); }
            }
        }
        return b.append('"').toString();
    }

    /** Encoded item (never null unless it cannot be built at all). owner may be null. */
    static String encode(ItemStack item, Player owner) {
        if (item == null) return null;
        var m = bytesMethod();
        if (m != null) {
            try {
                String b64 = Base64.getEncoder().encodeToString((byte[]) m.invoke(item));
                if (b64.length() <= 60000) return "B" + b64;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) { /* fall back to text */ }
        }
        try {
            ItemMeta meta = item.getItemMeta();
            StringBuilder lore = new StringBuilder();
            if (meta != null && meta.hasLore()) for (String l : meta.getLore()) { if (lore.length() > 0) lore.append(','); lore.append(json(l)); }
            int cmd = meta != null && meta.hasCustomModelData() ? meta.getCustomModelData() : -1;
            String dn = meta != null && meta.hasDisplayName() ? meta.getDisplayName() : "";
            boolean glint = meta != null && meta.hasEnchants();
            boolean unb = meta != null && meta.isUnbreakable();
            String js = "{\"id\":" + json(item.getType().getKey().toString()) + ",\"count\":" + item.getAmount()
                + ",\"name\":" + json(dn) + ",\"lore\":[" + lore + "],\"owner\":" + json(owner == null ? "" : owner.getName())
                + ",\"cmd\":" + cmd + ",\"glint\":" + glint + ",\"unbreakable\":" + unb + "}";
            return "T" + Base64.getEncoder().encodeToString(js.getBytes(StandardCharsets.UTF_8));
        } catch (RuntimeException | LinkageError ex) {
            if (!warned) { warned = true; org.bukkit.Bukkit.getLogger().warning("[ItemWire] 아이템 인코딩 실패: " + ex); }
            return null;
        }
    }
}
