package dev.pipi.life;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Predicate;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 거주지(village2) 월드: 손으로 그린 구상도에서 뽑은 레이아웃(village2_layout.json, 플러그인 jar 리소스)을
 * 틱마다 일정량씩 배치해 만든다. 물 / 풀 / 길 / 언덕(+4) / 계단 / 입구 아치 / 부지 표시.
 * 기존 village-world, 부지, 시설은 건드리지 않는다. 부지는 호출자가 plots-v2 섹션에 따로 등록한다.
 */
final class Village2 {
    static final String WORLD = "pipi_village2";
    private static final int SEA_FLOOR = 56;     // first block above the seabed sand
    private static final int SEA_TOP = 61;       // water surface block
    private static final int BUDGET = 5000;      // blocks per tick
    private static final String FLAT = """
        {"biome":"minecraft:ocean","layers":[{"block":"minecraft:bedrock","height":1},
        {"block":"minecraft:stone","height":117},{"block":"minecraft:sand","height":2},
        {"block":"minecraft:water","height":6}],"features":false,"lakes":false,"structure_overrides":[]}
        """;

    record Plot(String name, int x, int z, int y, boolean raised) {}
    record Stair(int x, int z, BlockFace face, int len, int width) {}

    private final PipiLife plugin;
    private boolean building;

    private int width, height, originX, originZ, baseY, raisedY, plotSize;
    private byte[][] type;
    private final List<Plot> plots = new ArrayList<>();
    private final List<Stair> stairs = new ArrayList<>();

    Village2(PipiLife plugin) { this.plugin = plugin; }

    World open() {
        World loaded = Bukkit.getWorld(WORLD);
        if (loaded != null) return loaded;
        if (!new File(Bukkit.getWorldContainer(), WORLD + "/level.dat").isFile()) return null;
        return Bukkit.createWorld(new WorldCreator(WORLD));
    }

    Location spawn(World world) { return new Location(world, 0.5, 64, 3.5, 0, 0); }

    /** Plots from the bundled layout (same positions buildv2 uses), or null if the layout can't be read. */
    List<Plot> layoutPlots() { return load() ? List.copyOf(plots) : null; }

    private boolean load() {
        try (InputStream in = plugin.getResource("village2_layout.json")) {
            if (in == null) return false;
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            width = root.get("width").getAsInt();
            height = root.get("height").getAsInt();
            originX = root.get("originX").getAsInt();
            originZ = root.get("originZ").getAsInt();
            baseY = root.get("baseY").getAsInt();
            raisedY = root.get("raisedY").getAsInt();
            plotSize = root.get("plotSize").getAsInt();
            type = new byte[height][width];
            JsonArray rows = root.getAsJsonArray("rows");
            for (int z = 0; z < height; z++) {
                String row = rows.get(z).getAsString();
                int x = 0;
                for (int i = 0; i < row.length(); ) {
                    byte t = (byte) "wlrh".indexOf(row.charAt(i++));
                    int n = 0;
                    while (i < row.length() && Character.isDigit(row.charAt(i))) n = n * 10 + (row.charAt(i++) - '0');
                    for (int k = 0; k < n && x < width; k++) type[z][x++] = t;
                }
            }
            plots.clear();
            for (JsonElement e : root.getAsJsonArray("plots")) {
                JsonObject o = e.getAsJsonObject();
                boolean raised = o.get("raised").getAsBoolean();
                plots.add(new Plot(o.get("name").getAsString(), o.get("x").getAsInt(), o.get("z").getAsInt(),
                    raised ? raisedY : baseY, raised));
            }
            stairs.clear();
            for (JsonElement e : root.getAsJsonArray("stairs")) {
                JsonObject o = e.getAsJsonObject();
                BlockFace face = switch (o.get("face").getAsString()) {
                    case "E" -> BlockFace.EAST; case "W" -> BlockFace.WEST; case "S" -> BlockFace.SOUTH; default -> BlockFace.NORTH; };
                stairs.add(new Stair(o.get("x").getAsInt(), o.get("z").getAsInt(), face, o.get("len").getAsInt(), o.get("width").getAsInt()));
            }
            return true;
        } catch (Exception ex) {
            plugin.getLogger().severe("village2 layout load failed: " + ex);
            return false;
        }
    }

    private int t(int gx, int gz) { return gx < 0 || gz < 0 || gx >= width || gz >= height ? 0 : type[gz][gx]; }
    private static int hash(int x, int z, int salt) {
        int h = x * 73856093 ^ z * 19349663 ^ salt * 83492791;
        h ^= h >>> 13; h *= 0x5bd1e995; h ^= h >>> 15;
        return Math.floorMod(h, 100);
    }

    /** Builds the world. {@code finish} registers the plots and returns whether it worked. */
    void build(Player player, Predicate<List<Plot>> finish) {
        if (building) { player.sendMessage("거주지 맵을 만드는 중입니다."); return; }
        if (Bukkit.getWorld(WORLD) != null || new File(Bukkit.getWorldContainer(), WORLD).exists()) {
            player.sendMessage("§c" + WORLD + " 월드가 이미 있습니다. 새로 만들려면 월드 폴더를 지운 서버에서 실행하세요.");
            return;
        }
        if (!load()) { player.sendMessage("§c플러그인 안의 village2_layout.json을 읽지 못했습니다."); return; }
        building = true;
        World world = Bukkit.createWorld(new WorldCreator(WORLD).type(WorldType.FLAT)
            .generatorSettings(FLAT).generateStructures(false));
        if (world == null) { building = false; player.sendMessage("§c월드를 생성하지 못했습니다."); return; }
        world.setSpawnLocation(0, 64, 3);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setStorm(false);
        world.setTime(6000);

        // distance (Chebyshev, capped) to the nearest water cell
        byte[][] wd = new byte[height][width];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (int z = 0; z < height; z++) for (int x = 0; x < width; x++) {
            if (type[z][x] == 0) queue.add(new int[] {x, z}); else wd[z][x] = 9;
        }
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            int d = wd[c[1]][c[0]];
            if (d >= 5) continue;
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                int nx = c[0] + dx, nz = c[1] + dz;
                if (nx < 0 || nz < 0 || nx >= width || nz >= height || wd[nz][nx] <= d + 1) continue;
                wd[nz][nx] = (byte) (d + 1);
                queue.add(new int[] {nx, nz});
            }
        }
        boolean[][] inPlot = new boolean[height][width];
        for (Plot p : plots)
            for (int z = p.z() - originZ - 5; z < p.z() - originZ + plotSize + 5; z++)
                for (int x = p.x() - originX - 5; x < p.x() - originX + plotSize + 5; x++)
                    if (x >= 0 && z >= 0 && x < width && z < height) inPlot[z][x] = true;

        List<Runnable> extras = new ArrayList<>();
        structures(world, extras, wd, inPlot);
        int cells = width * height;
        player.sendMessage("§e거주지 맵 생성 시작: " + width + " × " + height + " 블록, 부지 " + plots.size() + "개.");
        new BukkitRunnable() {
            private int cursor, extra, lastPct = -1;
            @Override public void run() {
                try {
                    int budget = BUDGET;
                    while (budget > 0 && cursor < cells) budget -= column(world, cursor % width, cursor++ / width, wd, inPlot);
                    while (budget > 0 && cursor >= cells && extra < extras.size()) { extras.get(extra++).run(); budget -= 8; }
                    int pct = (int) ((cursor + (long) extra * 0) * 100L / cells);
                    if (cursor >= cells) pct = 100 - (extras.isEmpty() ? 0 : (extras.size() - extra) * 10 / extras.size());
                    if (pct / 10 != lastPct / 10) { lastPct = pct; player.sendMessage("§7거주지 맵 진행 " + pct + "%"); }
                    if (cursor < cells || extra < extras.size()) return;
                    world.save();
                    player.sendMessage(finish.test(plots)
                        ? "§a거주지 맵 완성 (부지 " + plots.size() + "개 등록). /pipilife maps visit village2"
                        : "§e맵은 완성됐지만 부지 정보 저장에 실패했습니다.");
                } catch (RuntimeException ex) {
                    plugin.getLogger().severe("village2 build failed: " + ex);
                    ex.printStackTrace();
                    player.sendMessage("§c맵 생성 중 오류가 발생했습니다. 콘솔을 확인하세요.");
                }
                building = false;
                cancel();
            }
        }.runTaskTimer(plugin, 1, 1);
    }

    private void set(World w, int x, int y, int z, Material m) { w.getBlockAt(x, y, z).setType(m, false); }

    /** One terrain column; returns the number of blocks written. */
    private int column(World w, int gx, int gz, byte[][] wd, boolean[][] inPlot) {
        int ty = type[gz][gx];
        if (ty == 0) return 1;
        int x = gx + originX, z = gz + originZ, d = wd[gz][gx], n = 0;
        boolean raised = ty == 3;
        boolean cliff = raised && (t(gx + 1, gz) != 3 || t(gx - 1, gz) != 3 || t(gx, gz + 1) != 3 || t(gx, gz - 1) != 3);
        int top = raised ? raisedY : d == 1 ? SEA_TOP : d == 2 ? SEA_TOP + 1 : baseY;
        boolean beach = !raised && d <= 2;
        for (int y = SEA_FLOOR; y < top; y++) {
            Material m;
            if (cliff && y > baseY) {
                int h = hash(x + y, z, 3);
                m = h < 50 ? Material.STONE : h < 75 ? Material.ANDESITE : h < 90 ? Material.COBBLESTONE : Material.MOSSY_COBBLESTONE;
            } else if (beach && y >= top - 2) m = Material.SAND;
            else if (y >= top - 3) m = Material.DIRT;
            else m = Material.STONE;
            set(w, x, y, z, m); n++;
        }
        Material surface;
        if (ty == 2) {
            boolean border = t(gx + 1, gz) == 1 || t(gx - 1, gz) == 1 || t(gx, gz + 1) == 1 || t(gx, gz - 1) == 1
                || t(gx + 1, gz) == 3 || t(gx - 1, gz) == 3 || t(gx, gz + 1) == 3 || t(gx, gz - 1) == 3;
            int h = hash(x, z, 1);
            if (d <= 2) surface = Material.SAND;
            else if (border) surface = h < 45 ? Material.COARSE_DIRT : h < 70 ? Material.DIRT_PATH : h < 85 ? Material.PODZOL : Material.GRASS_BLOCK;
            else surface = h < 50 ? Material.DIRT_PATH : h < 75 ? Material.COARSE_DIRT : h < 92 ? Material.GRAVEL : Material.PODZOL;
        } else surface = beach ? Material.SAND : Material.GRASS_BLOCK;
        set(w, x, top, z, surface); n++;
        if (surface == Material.GRASS_BLOCK && ty != 2 && !inPlot[gz][gx] && !(Math.abs(x) < 12 && Math.abs(z) < 12)) {
            int h = hash(x, z, 7);
            Material deco = h < 2 ? Material.DANDELION : h < 4 ? Material.POPPY : h < 5 ? Material.CORNFLOWER
                : h < 6 ? Material.OXEYE_DAISY : h < 14 ? tuft() : null;
            if (deco != null) { set(w, x, top + 1, z, deco); n++; }
        }
        return n;
    }

    private static Material tuft() {
        Material m = Material.matchMaterial("SHORT_GRASS");
        return m != null ? m : Material.matchMaterial("GRASS");
    }

    private void structures(World w, List<Runnable> out, byte[][] wd, boolean[][] inPlot) {
        // stairs (placed after terrain so they overwrite it)
        for (Stair s : stairs) {
            int vx = s.face().getModX(), vz = s.face().getModZ(), px = -vz, pz = vx;
            for (int k = 0; k < s.len(); k++) for (int q = -s.width() / 2; q <= s.width() / 2; q++) {
                int x = s.x() - vx * k + px * q, z = s.z() - vz * k + pz * q, y = raisedY - k;
                if (t(x - originX, z - originZ) == 3 || t(x - originX, z - originZ) == 0) continue;
                out.add(() -> {
                    for (int yy = baseY; yy < y; yy++) set(w, x, yy, z, Material.STONE_BRICKS);
                    for (int yy = y + 1; yy <= raisedY + 2; yy++) set(w, x, yy, z, Material.AIR);
                    Block b = w.getBlockAt(x, y, z);
                    Stairs data = (Stairs) Material.STONE_BRICK_STAIRS.createBlockData();
                    data.setFacing(s.face());
                    b.setBlockData(data, false);
                });
            }
            // low walls on both sides
            for (int k = 0; k < s.len(); k++) for (int side : new int[] {-1, 1}) {
                int q = side * (s.width() / 2 + 1);
                int x = s.x() - vx * k + px * q, z = s.z() - vz * k + pz * q, y = raisedY - k;
                if (t(x - originX, z - originZ) == 3 || t(x - originX, z - originZ) == 0) continue;
                out.add(() -> {
                    for (int yy = baseY; yy <= y; yy++) set(w, x, yy, z, Material.STONE_BRICKS);
                    set(w, x, y + 1, z, Material.STONE_BRICK_WALL);
                });
            }
        }
        // gateway plaza + arch around (0,0)
        for (int dz = -6; dz <= 6; dz++) for (int dx = -6; dx <= 6; dx++) {
            if (dx * dx + dz * dz > 42 || t(dx - originX, dz - originZ) == 0) continue;
            int x = dx, z = dz, h = hash(x, z, 11);
            out.add(() -> set(w, x, baseY, z, h < 55 ? Material.STONE_BRICKS : h < 70 ? Material.MOSSY_STONE_BRICKS : Material.POLISHED_ANDESITE));
        }
        out.add(() -> {
            for (int sx : new int[] {-4, 3}) for (int dx = 0; dx < 2; dx++) for (int z = -1; z <= 1; z++) {
                int x = sx + dx;
                set(w, x, baseY, z, Material.POLISHED_ANDESITE);
                for (int y = baseY + 1; y <= baseY + 4; y++)
                    set(w, x, y, z, y == baseY + 1 || (y == baseY + 4) ? Material.CHISELED_STONE_BRICKS : Material.STONE_BRICKS);
            }
            for (int x = -4; x <= 4; x++) for (int z = -1; z <= 1; z++) set(w, x, baseY + 5, z, Material.STONE_BRICKS);
            for (int x = -5; x <= 5; x++) for (int z = -2; z <= 2; z++) {
                Slab slab = (Slab) Material.STONE_BRICK_SLAB.createBlockData();
                w.getBlockAt(x, baseY + 6, z).setBlockData(slab, false);
            }
            for (int sx : new int[] {-4, 4}) {
                Lantern lantern = (Lantern) Material.LANTERN.createBlockData();
                w.getBlockAt(sx, baseY + 7, 0).setBlockData(lantern, false);
            }
            for (int sx : new int[] {-2, 2}) {
                Lantern lantern = (Lantern) Material.LANTERN.createBlockData();
                lantern.setHanging(true);
                w.getBlockAt(sx, baseY + 4, 0).setBlockData(lantern, false);
                set(w, sx, baseY + 5, 0, Material.CHISELED_STONE_BRICKS);
            }
            wallSign(w, 0, baseY + 5, 2, BlockFace.SOUTH, "마을 입구", "삐삐 거주지", "환영합니다");
            wallSign(w, 0, baseY + 5, -2, BlockFace.NORTH, "마을 입구", "삐삐 거주지", "환영합니다");
        });
        // plots: corner posts, edge slabs, number sign
        for (Plot p : plots) {
            out.add(() -> {
                int x0 = p.x(), z0 = p.z(), x1 = x0 + plotSize - 1, z1 = z0 + plotSize - 1, y = p.y() + 1;
                for (int i = 0; i < plotSize; i++) {
                    if (i % 4 != 0 && i != plotSize - 1) continue;
                    for (int[] c : new int[][] {{x0 + i, z0}, {x0 + i, z1}, {x0, z0 + i}, {x1, z0 + i}}) {
                        Slab slab = (Slab) Material.OAK_SLAB.createBlockData();
                        w.getBlockAt(c[0], y, c[1]).setBlockData(slab, false);
                    }
                }
                for (int[] c : new int[][] {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
                    set(w, c[0], y, c[1], Material.OAK_FENCE);
                    set(w, c[0], y + 1, c[1], Material.OAK_FENCE);
                    set(w, c[0], y + 2, c[1], Material.LANTERN);
                }
                Block sb = w.getBlockAt(x0 + 2, y, z0 + 2);
                sb.setType(Material.OAK_SIGN, false);
                if (sb.getState() instanceof Sign sign) {
                    sign.setLine(0, p.name());
                    sign.setLine(1, plotSize + " × " + plotSize);
                    sign.setLine(2, "빈 부지");
                    sign.setLine(3, "/pipilife claim");
                    sign.update(true, false);
                }
            });
        }
        // scattered trees away from roads, plots and the shore
        Random rng = new Random(20261115L);
        List<int[]> placed = new ArrayList<>();
        for (int gz = 6; gz < height - 6; gz++) for (int gx = 6; gx < width - 6; gx++) {
            if (type[gz][gx] != 1 || wd[gz][gx] < 5 || inPlot[gz][gx] || rng.nextInt(1000) >= 4) continue;
            int x = gx + originX, z = gz + originZ;
            if (Math.abs(x) < 16 && Math.abs(z) < 16) continue;
            boolean ok = true;
            for (int dz = -3; dz <= 3 && ok; dz++) for (int dx = -3; dx <= 3; dx++) if (t(gx + dx, gz + dz) != 1 || inPlot[gz + dz][gx + dx]) { ok = false; break; }
            for (int[] o : placed) if (Math.abs(o[0] - x) < 6 && Math.abs(o[1] - z) < 6) { ok = false; break; }
            if (!ok) continue;
            placed.add(new int[] {x, z});
            out.add(() -> w.generateTree(new Location(w, x, baseY + 1, z), TreeType.TREE));
        }
    }

    private void wallSign(World w, int x, int y, int z, BlockFace face, String... lines) {
        Block b = w.getBlockAt(x, y, z);
        b.setType(Material.OAK_WALL_SIGN, false);
        Directional data = (Directional) b.getBlockData();
        data.setFacing(face);
        b.setBlockData(data, false);
        if (b.getState() instanceof Sign sign) {
            for (int i = 0; i < lines.length; i++) sign.setLine(i, lines[i]);
            sign.update(true, false);
        }
    }
}
