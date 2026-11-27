package dev.pipi.life;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

/**
 * 촌장의 집: the elder's house on the north side of the pipi_town plaza (door facing the plaza). The town world is already
 * built, so the house is pasted into it from chief_house.json (plugin jar resource, made by dev/art/town/chief_house.mjs):
 * the "clear" boxes become air (trees, flowers), then every block is placed. Built once (data flag town-chief-house);
 * /pipilife maps chiefhouse builds it again. The 촌장 (VillageChief) stands at the "chief" spot inside.
 */
final class ChiefHouse {
    static final String FLAG = "town-chief-house";
    private final PipiLife plugin;
    private JsonObject root;

    ChiefHouse(PipiLife plugin) { this.plugin = plugin; }

    private boolean load() {
        if (root != null) return true;
        try (InputStream in = plugin.getResource("chief_house.json")) {
            if (in == null) return false;
            root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            return true;
        } catch (Exception ex) {
            plugin.getLogger().warning("chief_house.json load failed: " + ex);
            return false;
        }
    }

    boolean built() { return plugin.data().getBoolean(FLAG); }

    /** Builds the house unless it was built already (force = build again). Null when nothing was done. */
    String build(World world, boolean force) {
        if (world == null) return "마을 월드를 찾지 못했습니다.";
        if (!force && built()) return null;
        if (!load()) return "촌장의 집 데이터(chief_house.json)를 찾지 못했습니다.";
        for (var box : root.getAsJsonArray("clear")) {
            JsonArray b = box.getAsJsonArray();
            for (int y = b.get(1).getAsInt(); y <= b.get(4).getAsInt(); y++)
                for (int z = b.get(2).getAsInt(); z <= b.get(5).getAsInt(); z++)
                    for (int x = b.get(0).getAsInt(); x <= b.get(3).getAsInt(); x++) world.getBlockAt(x, y, z).setType(Material.AIR, false);
        }
        JsonArray pal = root.getAsJsonArray("palette");
        BlockData[] data = new BlockData[pal.size()];
        int bad = 0;
        for (int i = 0; i < pal.size(); i++) {
            try { data[i] = Bukkit.createBlockData(pal.get(i).getAsString()); }
            catch (IllegalArgumentException ex) { data[i] = null; bad++; plugin.getLogger().warning("촌장의 집: 알 수 없는 블록 " + pal.get(i).getAsString()); }
        }
        int placed = 0;
        for (var e : root.getAsJsonArray("blocks")) {
            JsonArray b = e.getAsJsonArray();
            BlockData d = data[b.get(3).getAsInt()];
            if (d == null) continue;
            world.getBlockAt(b.get(0).getAsInt(), b.get(1).getAsInt(), b.get(2).getAsInt()).setBlockData(d, false);
            placed++;
        }
        plugin.data().set(FLAG, true);
        plugin.saveData();
        return "촌장의 집 완성: 블록 " + placed + "개" + (bad > 0 ? " (알 수 없는 블록 종류 " + bad + "개 건너뜀)" : "");
    }

    /** Where the 촌장 stands inside (null until the house is built). */
    Location chiefSpot(World world) {
        if (world == null || !built() || !load()) return null;
        JsonObject c = root.getAsJsonObject("chief");
        return new Location(world, c.get("x").getAsDouble(), c.get("y").getAsDouble(), c.get("z").getAsDouble(), c.get("yaw").getAsFloat(), 0);
    }
}
