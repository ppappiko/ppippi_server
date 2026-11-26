package dev.pipi.life;

import java.io.File;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 통곡의 왕좌 maps (world {@link #WORLD}). Built once with /pipilife maps buildsorrow (async, {@link #BUDGET} blocks per tick).
 *
 *  - Two halls: one for 1관문 (군체 저지), one shared by 2관문 (허상) and 3관문 (왕관) (only one party fights at a time): a round arena of radius {@link #HALL_R} open to the dark sky, ringed by
 *    cliffs and golden pyramid shrines (bronze-tiled floor, chests and rubble near the rim), around a small hub. Three zones
 *    (centre directions {@link #ZONE}, clockwise 0 -> 1 -> 2 seen from above) about 60 blocks apart, so nothing in one zone reaches
 *    another. The zone boundaries ({@link #BOUND}) carry the 통곡의 장막: an invisible barrier wall the raid raises at runtime
 *    (it shows only as a faint shimmer of particles), see {@link #veilColumns}.
 * The old 1·2관문 arenas around (0, 0) / (0, 160) are no longer used.
 */
final class SorrowMap {
    static final String WORLD = "pipi_sorrow";
    static final int FLOOR = 64;                 // top floor block
    static final int CEIL = FLOOR + 26;          // hall ceiling layer
    static final int HALL_R = 56, WALL_T = 3, HUB_R = 8, VEIL_H = 18;
    /** Zone centre directions (degrees, atan2(z, x)): 0 = north (12시), 1 = east-south-east, 2 = west-south-west. */
    static final double[] ZONE = {270, 30, 150};
    /** Boundary k lies between zone k and zone k+1 (clockwise). */
    static final double[] BOUND = {330, 90, 210};
    /** Distance of a zone's centre from the hall centre. */
    static final double ZONE_R = 34;

    private static final int BUDGET = 4000;
    private static final String FLAT = """
        {"biome":"minecraft:soul_sand_valley","layers":[{"block":"minecraft:bedrock","height":1}],
        "features":false,"lakes":false,"structure_overrides":[]}
        """;

    private final PipiLife plugin;
    private boolean building;
    private final Map<Long, BlockData> plan = new LinkedHashMap<>();

    SorrowMap(PipiLife plugin) { this.plugin = plugin; }

    boolean exists() { return Bukkit.getWorld(WORLD) != null || new File(Bukkit.getWorldContainer(), WORLD + "/level.dat").isFile(); }

    World open() {
        World loaded = Bukkit.getWorld(WORLD);
        if (loaded != null) return loaded;
        if (!new File(Bukkit.getWorldContainer(), WORLD + "/level.dat").isFile()) return null;
        World w = Bukkit.createWorld(new WorldCreator(WORLD));
        if (w != null) rules(w);
        return w;
    }

    static void rules(World world) {
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_FIRE_TICK, false);
        world.setGameRule(GameRule.MOB_GRIEFING, false);
        world.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setStorm(false);
        world.setTime(18000);
    }

    // ---- hall geometry ----
    static double rad(double deg) { return Math.toRadians(deg); }
    /** Hall centre block of a gate (1, 2, 3; gates 2 and 3 share one hall); the centre point is (hallX + .5, hallZ + .5). */
    static int hallX(int gate) { return 0; }
    static int hallZ(int gate) { return gate == 1 ? 1000 : 1800; }
    static double hx(int gate, double deg, double r) { return hallX(gate) + .5 + Math.cos(rad(deg)) * r; }
    static double hz(int gate, double deg, double r) { return hallZ(gate) + .5 + Math.sin(rad(deg)) * r; }
    static double hallDist(int gate, double x, double z) { return Math.hypot(x - hallX(gate) - .5, z - hallZ(gate) - .5); }
    static Location hall(World w, int gate, double deg, double r, double up) { return new Location(w, hx(gate, deg, r), FLOOR + 1 + up, hz(gate, deg, r)); }

    /** Zone (0..2) of a point in a hall, by angle. */
    static int zoneAt(int gate, double x, double z) {
        double a = Math.toDegrees(Math.atan2(z - hallZ(gate) - .5, x - hallX(gate) - .5));
        int best = 0; double bd = 999;
        for (int i = 0; i < ZONE.length; i++) { double d = Math.abs(((a - ZONE[i]) % 360 + 540) % 360 - 180); if (d < bd) { bd = d; best = i; } }
        return best;
    }

    /** Block columns (x, z) of the barrier veil on the boundaries; with sealHub the three lines meet at the centre, else they start at the hub. */
    static List<int[]> veilColumns(int gate, boolean sealHub, int boundMask) {
        List<int[]> out = new ArrayList<>();
        int cx = hallX(gate), cz = hallZ(gate);
        for (int dx = -HALL_R - 1; dx <= HALL_R + 1; dx++) for (int dz = -HALL_R - 1; dz <= HALL_R + 1; dz++) {
            double r = Math.hypot(dx, dz);
            if (r > HALL_R + .5) continue;
            for (int k = 0; k < 3; k++) {
                if ((boundMask & (1 << k)) == 0) continue;
                double c = Math.cos(rad(BOUND[k])), sn = Math.sin(rad(BOUND[k]));
                double along = dx * c + dz * sn, perp = Math.abs(-dx * sn + dz * c);
                if (along >= (sealHub ? -.6 : HUB_R) && perp <= .75) { out.add(new int[]{cx + dx, cz + dz}); break; }
            }
        }
        return out;
    }

    /** Arrival: on the south side of the hub, facing north (zone 0). */
    static Location hallSpawn(World w, int gate, int i, int n) {
        double a = 90 + (i - (n - 1) / 2.0) * 14;
        Location l = hall(w, gate, a, HUB_R - 2.5, 0);
        l.setYaw(180);
        return l;
    }

    static int[] bell(int gate) {
        return new int[]{(int) Math.floor(hx(gate, 120, HUB_R - 1)), FLOOR + 2, (int) Math.floor(hz(gate, 120, HUB_R - 1))};
    }

    // ---- plan helpers ----
    private static long key(int x, int y, int z) { return ((x + 4096L) << 32) | ((long) (y + 256) << 16) | (z + 4096L); }
    private void set(int x, int y, int z, Material m) { plan.put(key(x, y, z), m.createBlockData()); }
    private void light(int x, int y, int z, int level) {
        BlockData light = Material.LIGHT.createBlockData();
        if (light instanceof Levelled lv) lv.setLevel(level);
        plan.put(key(x, y, z), light);
    }
    private static int hash(int x, int z, int salt) {
        int h = x * 73856093 ^ z * 19349663 ^ salt * 83492791;
        h ^= h >>> 13; h *= 0x5bd1e995; h ^= h >>> 15;
        return h & 0x7fffffff;
    }

    /**
     * One hall, open to the dark sky: a bronze-and-mud tiled floor with gold seams, a ring of jagged dark cliffs around it,
     * golden truncated pyramid shrines behind each zone (where the lords stand) and on the zone boundaries, bronze chests and
     * rubble near the rim. Accents per gate: 1 = green froglight (군체), 2 = amethyst (허상), 3 = gilded blackstone (왕관).
     */
    private void hall(int gate) {
        int cx = hallX(gate), cz = hallZ(gate), out = HALL_R + WALL_T, cliff = HALL_R + 12;
        Material[] floor = gate == 2
            ? new Material[]{Material.POLISHED_DEEPSLATE, Material.DEEPSLATE_TILES, Material.MUD_BRICKS, Material.POLISHED_BLACKSTONE_BRICKS}
            : new Material[]{Material.POLISHED_BLACKSTONE_BRICKS, Material.MUD_BRICKS, Material.PACKED_MUD, Material.POLISHED_BLACKSTONE, Material.CRACKED_POLISHED_BLACKSTONE_BRICKS};
        Material accent = gate == 2 ? Material.AMETHYST_BLOCK : gate == 3 ? Material.GILDED_BLACKSTONE : Material.VERDANT_FROGLIGHT;
        List<int[]> lines = veilColumns(gate, true, 7);
        Set<Long> line = new HashSet<>();
        for (int[] c : lines) line.add(key(c[0], 0, c[1]));
        for (int dx = -cliff; dx <= cliff; dx++) for (int dz = -cliff; dz <= cliff; dz++) {
            double r = Math.hypot(dx, dz);
            if (r > cliff + .5) continue;
            int x = cx + dx, z = cz + dz;
            for (int y = FLOOR + 1; y <= CEIL + 1; y++) plan.put(key(x, y, z), AIR);   // clears the old walls and ceiling
            set(x, FLOOR - 1, z, Material.BLACKSTONE);
            if (r > out + .5) {                                                         // cliffs: jagged dark rock, gold veins, embers
                double ang = Math.atan2(dz, dx);
                int top = FLOOR + 9 + (int) ((Math.sin(ang * 7 + gate) * .5 + .5) * 8 + (Math.sin(ang * 19) * .5 + .5) * 5 + (r - out) * .9) + hash(x, z, 9) % 4;
                for (int y = FLOOR; y <= top; y++) {
                    int hh = hash(x, y * 31, z);
                    set(x, y, z, hh % 23 == 0 ? Material.GILDED_BLACKSTONE : hh % 41 == 0 ? Material.SHROOMLIGHT : hh % 3 == 0 ? Material.BASALT : hh % 5 == 0 ? Material.TUFF : Material.BLACKSTONE);
                }
                continue;
            }
            Material f = floor[hash(x, z, gate) % floor.length];
            boolean seam = (Math.floorMod(dx, 6) == 0 || Math.floorMod(dz, 6) == 0) && r < HALL_R - 2;
            if (seam) f = hash(x, z, 5) % 7 == 0 ? Material.GILDED_BLACKSTONE : Material.WAXED_EXPOSED_CUT_COPPER;
            if (r > HALL_R + .5) f = Material.POLISHED_BLACKSTONE;
            else if (line.contains(key(x, 0, z)) && r > HUB_R) f = Material.GILDED_BLACKSTONE;                  // where the veil stands
            else if (Math.abs(r - HUB_R) < .5) f = accent;                                                        // hub ring
            else if (Math.abs(r - (HALL_R - 3)) < .5 && hash(x, z, 2) % 3 == 0) f = accent;                       // outer light ring
            else {
                for (int k = 0; k < 3; k++) {                                                                      // zone circles
                    double d = Math.hypot(x + .5 - hx(gate, ZONE[k], ZONE_R), z + .5 - hz(gate, ZONE[k], ZONE_R));
                    if (Math.abs(d - 9) < .5) f = gate == 1 ? Material.GILDED_BLACKSTONE : accent;
                }
            }
            set(x, FLOOR, z, f);
            if (r > HALL_R + .5) for (int y = FLOOR + 1; y <= FLOOR + 3 + hash(x, z, 4) % 3; y++) set(x, y, z, Material.BLACKSTONE); // low rim
            if (r <= HALL_R - 1 && Math.floorMod(dx, 7) == 0 && Math.floorMod(dz, 7) == 0) light(x, FLOOR + 10, z, 13);
        }
        // golden shrines: a big one behind each zone (the lords stand in front of it), smaller ones on the boundaries
        for (int k = 0; k < 3; k++) {
            pyramid((int) Math.floor(hx(gate, ZONE[k], HALL_R + 8)), (int) Math.floor(hz(gate, ZONE[k], HALL_R + 8)), 8, 3, 22);
            pyramid((int) Math.floor(hx(gate, BOUND[k], HALL_R + 7)), (int) Math.floor(hz(gate, BOUND[k], HALL_R + 7)), 6, 2, 15);
            for (int o = -1; o <= 1; o += 2) {
                double a = ZONE[k] + o * 14;
                chest((int) Math.floor(hx(gate, a, HALL_R - 4)), (int) Math.floor(hz(gate, a, HALL_R - 4)), Math.floorMod((int) Math.round(a / 90.0), 2) == 0);
                rubble((int) Math.floor(hx(gate, ZONE[k] + o * 27, HALL_R - 3)), (int) Math.floor(hz(gate, ZONE[k] + o * 27, HALL_R - 3)), 3, gate * 7 + k + o);
            }
        }
        // hub: low dais (gate 1 keeps the middle free for the 눈물 성배)
        for (int dx = -HUB_R; dx <= HUB_R; dx++) for (int dz = -HUB_R; dz <= HUB_R; dz++) {
            double r = Math.hypot(dx, dz);
            if (r <= 2.5 && gate != 1) set(cx + dx, FLOOR + 1, cz + dz, r > 1.5 ? accent : Material.CHISELED_POLISHED_BLACKSTONE);
            if (gate == 1 && r <= 1.5) set(cx + dx, FLOOR, cz + dz, Material.CHISELED_POLISHED_BLACKSTONE);
        }
        int[] bl = bell(gate);
        set(bl[0], FLOOR + 1, bl[2], Material.POLISHED_BLACKSTONE_WALL);
        set(bl[0], FLOOR + 2, bl[2], Material.BELL);
    }

    private static final BlockData AIR = Material.AIR.createBlockData();

    /** Truncated golden pyramid on the floor at (x, z): base half-width b, top half-width t, height h, glowing cap. */
    private void pyramid(int x, int z, int b, int t, int h) {
        for (int y = 0; y < h; y++) {
            int half = (int) Math.round(b - (b - t) * (double) y / (h - 1));
            for (int dx = -half; dx <= half; dx++) for (int dz = -half; dz <= half; dz++) {
                boolean edge = Math.abs(dx) == half || Math.abs(dz) == half;
                if (!edge && y > 0 && y < h - 1) continue;                     // hollow inside
                boolean corner = Math.abs(dx) == half && Math.abs(dz) == half;
                Material m = corner ? Material.GOLD_BLOCK
                    : y % 5 == 4 ? Material.WAXED_EXPOSED_CUT_COPPER
                    : (dx == 0 || dz == 0) && y % 5 == 2 ? Material.CHISELED_SANDSTONE
                    : hash(x + dx, y, z + dz) % 6 == 0 ? Material.CUT_SANDSTONE : Material.SMOOTH_SANDSTONE;
                if (y == h - 1) m = Math.abs(dx) <= 1 && Math.abs(dz) <= 1 ? Material.GILDED_BLACKSTONE : Material.GOLD_BLOCK;
                set(x + dx, FLOOR + 1 + y, z + dz, m);
            }
        }
        set(x, FLOOR + 1 + h, z, Material.SOUL_LANTERN);
        light(x, FLOOR + 3 + h, z, 15);
    }

    /** A bronze chest with a gold-trimmed lid, long along x (or z). */
    private void chest(int x, int z, boolean alongX) {
        for (int i = -1; i <= 1; i++) for (int j = 0; j <= 1; j++) {
            int bx = alongX ? x + i : x + j, bz = alongX ? z + j : z + i;
            set(bx, FLOOR + 1, bz, Math.abs(i) == 1 ? Material.WAXED_EXPOSED_CHISELED_COPPER : Material.WAXED_EXPOSED_CUT_COPPER);
            set(bx, FLOOR + 2, bz, i == 0 ? Material.GOLD_BLOCK : Material.WAXED_EXPOSED_CUT_COPPER_SLAB);
        }
    }

    /** A low mound of broken stone and gold. */
    private void rubble(int x, int z, int rad, int salt) {
        for (int dx = -rad; dx <= rad; dx++) for (int dz = -rad; dz <= rad; dz++) {
            double r = Math.hypot(dx, dz);
            if (r > rad + .3) continue;
            int hgt = (int) Math.round((rad - r) * .9) + hash(x + dx, z + dz, salt) % 2;
            for (int y = 1; y <= hgt; y++) {
                int hh = hash(x + dx, y + salt, z + dz);
                set(x + dx, FLOOR + y, z + dz, hh % 7 == 0 ? Material.GILDED_BLACKSTONE : hh % 3 == 0 ? Material.COBBLED_DEEPSLATE : hh % 5 == 0 ? Material.CRACKED_POLISHED_BLACKSTONE_BRICKS : Material.BLACKSTONE);
            }
        }
    }

    void build(Player player, Runnable finish) {
        if (building) { player.sendMessage("통곡의 왕좌 맵을 만드는 중입니다."); return; }
        boolean had = exists();
        if (!had && new File(Bukkit.getWorldContainer(), WORLD).exists()) {
            player.sendMessage("§c" + WORLD + " 폴더가 있지만 월드가 아닙니다. 폴더를 지운 서버에서 다시 실행하세요.");
            return;
        }
        building = true;
        World world = had ? open() : Bukkit.createWorld(new WorldCreator(WORLD).type(WorldType.FLAT).generatorSettings(FLAT).generateStructures(false));
        if (world == null) { building = false; player.sendMessage("§c월드를 열지 못했습니다."); return; }
        world.setSpawnLocation(hallX(1), FLOOR + 1, hallZ(1) + 4);
        rules(world);
        plan.clear();
        hall(1);
        hall(3);   // shared by 2관문 and 3관문
        final List<Map.Entry<Long, BlockData>> entries = new ArrayList<>(plan.entrySet());
        plan.clear();
        player.sendMessage("§e통곡의 왕좌 맵 생성 시작 (관문 1~3): 블록 " + entries.size() + "개.");
        new BukkitRunnable() {
            private int cursor, lastPct = -1;
            @Override public void run() {
                try {
                    int budget = BUDGET;
                    while (budget-- > 0 && cursor < entries.size()) {
                        var e = entries.get(cursor++);
                        long k = e.getKey();
                        world.getBlockAt((int) (k >> 32) - 4096, (int) ((k >> 16) & 0xFFFF) - 256, (int) (k & 0xFFFF) - 4096).setBlockData(e.getValue(), false);
                    }
                    int pct = cursor * 100 / Math.max(1, entries.size());
                    if (pct / 20 != lastPct / 20) { lastPct = pct; player.sendMessage("§7통곡의 왕좌 맵 진행 " + pct + "%"); }
                    if (cursor < entries.size()) return;
                    world.save();
                    player.sendMessage("§a통곡의 왕좌 맵 완성. §7(/pipilife maps visit sorrow · sorrow2 (= sorrow3))");
                    if (finish != null) finish.run();
                } catch (RuntimeException ex) {
                    plugin.getLogger().severe("sorrow build failed: " + ex);
                    ex.printStackTrace();
                    player.sendMessage("§c맵 생성 중 오류가 발생했습니다. 콘솔을 확인하세요.");
                }
                building = false;
                cancel();
            }
        }.runTaskTimer(plugin, 1, 1);
    }
}
