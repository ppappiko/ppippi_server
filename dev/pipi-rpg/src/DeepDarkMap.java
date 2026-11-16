package dev.pipi.life;

import java.io.File;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 딥 다크 시티 레이드 월드(pipi_deepdark): 원본 디자인의 절차 생성 맵.
 * (a) 준비방(rectangular hall, 렉턴 = 준비, 종 = 나가기) 과 (b) 보스 전투장(원형, 반지름 46, 밀폐)으로 이루어진다.
 * 모든 블록을 먼저 계획(plan)에 모은 뒤 틱마다 일정량씩 배치한다 (Village2 / TownMap 방식). 월드가 이미 있으면 거부한다.
 */
final class DeepDarkMap {
    static final String WORLD = "pipi_deepdark";
    static final int FLOOR = 64;          // top floor block of both rooms
    static final int CEIL = 92;           // arena ceiling layer
    static final int R_IN = 46, R_OUT = 50;
    static final double ACX = .5, ACZ = .5; // arena centre (world coordinates)
    static final int PREP_Z = -120, PREP_HALF = 14, PREP_CEIL = 76;
    /** Exit bell inside the boss arena, just behind the arena spawn line (players arrive at z = 30.5). */
    static final int BELL_X = 0, BELL_Z = 36;
    static final String BELL_LABEL_TAG = "pipi_raid_bell_label";
    private static final int BUDGET = 4000;
    private static final String FLAT = """
        {"biome":"minecraft:deep_dark","layers":[{"block":"minecraft:bedrock","height":1}],
        "features":false,"lakes":false,"structure_overrides":[]}
        """;

    private final PipiLife plugin;
    private boolean building;
    private final Map<Long, BlockData> plan = new LinkedHashMap<>();
    private final Map<Material, BlockData> cache = new EnumMap<>(Material.class);

    DeepDarkMap(PipiLife plugin) { this.plugin = plugin; }

    boolean exists() {
        return Bukkit.getWorld(WORLD) != null || new File(Bukkit.getWorldContainer(), WORLD + "/level.dat").isFile();
    }

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
        world.setStorm(false);
        world.setTime(18000);
    }

    static Location prepSpawn(World w) { return new Location(w, .5, FLOOR + 1, PREP_Z + PREP_HALF - 3 + .5, 180, 0); }

    // ---- plan helpers ----
    private static long key(int x, int y, int z) { return ((x + 4096L) << 32) | ((long) (y + 256) << 16) | (z + 4096L); }
    private BlockData data(Material m) { return cache.computeIfAbsent(m, Material::createBlockData); }
    private void set(int x, int y, int z, Material m) { plan.put(key(x, y, z), data(m)); }
    private void put(int x, int y, int z, BlockData d) { plan.put(key(x, y, z), d); }
    private void fill(int x1, int y1, int z1, int x2, int y2, int z2, Material m) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) set(x, y, z, m);
    }
    private static int hash(int x, int z, int salt) {
        int h = x * 73856093 ^ z * 19349663 ^ salt * 83492791;
        h ^= h >>> 13; h *= 0x5bd1e995; h ^= h >>> 15;
        return Math.floorMod(h, 100);
    }
    private void lantern(int x, int y, int z, boolean hanging) {
        Lantern l = (Lantern) Material.SOUL_LANTERN.createBlockData();
        l.setHanging(hanging);
        put(x, y, z, l);
    }
    private void vein(int x, int y, int z) { // sculk vein lying on the block below
        BlockData d = Material.SCULK_VEIN.createBlockData();
        if (d instanceof MultipleFacing mf) mf.setFace(BlockFace.DOWN, true);
        put(x, y, z, d);
    }

    // ---- structures ----
    private Material tile(int x, int z) {
        int h = hash(x, z, 1);
        return h < 48 ? Material.DEEPSLATE_TILES : h < 68 ? Material.DEEPSLATE_BRICKS : h < 80 ? Material.POLISHED_DEEPSLATE
            : h < 91 ? Material.CRACKED_DEEPSLATE_TILES : Material.COBBLED_DEEPSLATE;
    }

    private Material arenaFloor(int x, int z, double r) {
        if (r < 3) return Material.SCULK;
        if (r >= 8 && r < 9.5) return Material.REINFORCED_DEEPSLATE;
        if (r >= 30 && r < 31) return Material.POLISHED_DEEPSLATE;
        if (r >= R_IN - 2 && r < R_IN - 1) return Material.CHISELED_DEEPSLATE;
        if (r > 9.5 && r < R_IN - 2) { // eight radial inlay lines
            double ang = Math.atan2(z, x), step = Math.PI / 4, k = Math.rint(ang / step);
            if (Math.abs(r * Math.sin(ang - k * step)) < .7) return Material.POLISHED_DEEPSLATE;
        }
        if (hash(Math.floorDiv(x, 5), Math.floorDiv(z, 5), 2) < 12 && hash(x, z, 3) < 70) return Material.SCULK;
        return tile(x, z);
    }

    private void arena() {
        for (int x = -R_OUT - 2; x <= R_OUT + 2; x++) for (int z = -R_OUT - 2; z <= R_OUT + 2; z++) {
            double r = Math.hypot(x, z);
            if (r > R_OUT + .5) continue;
            if (r <= R_IN + .5) {
                set(x, FLOOR - 1, z, Material.DEEPSLATE);
                Material f = arenaFloor(x, z, r);
                set(x, FLOOR, z, f);
                if (f == Material.SCULK && hash(x, z, 4) < 45) vein(x, FLOOR + 1, z);
            } else {
                for (int y = FLOOR - 1; y <= CEIL; y++) {
                    Material m;
                    if (y <= FLOOR + 1) m = Material.POLISHED_DEEPSLATE;
                    else if (y >= CEIL - 2 && y <= CEIL - 1) m = Material.CHISELED_DEEPSLATE;
                    else {
                        int h = hash(x + y, z, 5);
                        m = h < 50 ? Material.DEEPSLATE_BRICKS : h < 75 ? Material.DEEPSLATE_TILES : h < 90 ? Material.CRACKED_DEEPSLATE_BRICKS : Material.COBBLED_DEEPSLATE;
                    }
                    set(x, y, z, m);
                }
            }
            // roof (2 layers so no skylight leaks in)
            int h = hash(x, z, 6);
            set(x, CEIL, z, r > R_IN - 4 && r < R_IN - 2 ? Material.CHISELED_DEEPSLATE : h < 60 ? Material.DEEPSLATE_TILES : Material.DEEPSLATE_BRICKS);
            set(x, CEIL + 1, z, Material.DEEPSLATE);
        }
        // sixteen wall pillars (ancient-city style columns) with decor between them
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8, ux = Math.cos(a), uz = Math.sin(a);
            for (int x = -R_OUT; x <= R_OUT; x++) for (int z = -R_OUT; z <= R_OUT; z++) {
                double s = x * ux + z * uz, lat = Math.abs(-x * uz + z * ux);
                if (s < R_IN - 3 || s > R_IN + .5 || lat > 1.6) continue;
                for (int y = FLOOR + 1; y < CEIL; y++) {
                    Material m = (y == FLOOR + 1 || y == CEIL - 1 || (y - FLOOR) % 9 == 0) ? Material.CHISELED_DEEPSLATE
                        : (lat > 1.0 || s < R_IN - 2) ? Material.DEEPSLATE_BRICKS : Material.POLISHED_DEEPSLATE;
                    set(x, y, z, m);
                }
            }
            // decor between pillars: sensor / shrieker at the foot of the wall (non-functional: game events are cancelled), lantern above
            double b = a + Math.PI / 16;
            int dx = (int) Math.round(Math.cos(b) * (R_IN - .6)), dz = (int) Math.round(Math.sin(b) * (R_IN - .6));
            set(dx, FLOOR + 1, dz, i % 2 == 0 ? Material.SCULK_SENSOR : Material.SCULK_SHRIEKER);
            lantern(dx, FLOOR + 2, dz, false);
            // low broken columns just inside the rim
            int rx = (int) Math.round(Math.cos(b) * 40), rz = (int) Math.round(Math.sin(b) * 40);
            int ht = 2 + hash(rx, rz, 7) % 4;
            for (int ox = 0; ox < 2; ox++) for (int oz = 0; oz < 2; oz++) {
                for (int y = 1; y <= ht; y++) set(rx + ox, FLOOR + y, rz + oz, y == 1 ? Material.CHISELED_DEEPSLATE : hash(rx + ox + y, rz + oz, 8) < 25 ? Material.CRACKED_DEEPSLATE_BRICKS : Material.DEEPSLATE_BRICKS);
                if (hash(rx + ox, rz + oz, 9) < 60) set(rx + ox, FLOOR + ht + 1, rz + oz, Material.DEEPSLATE_BRICK_SLAB);
            }
            lantern(rx, FLOOR + ht + 1, rz, false);
        }
        // floor lanterns (light for the fight) on two rings and beside the dais
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3 + .3;
            lantern((int) Math.round(Math.cos(a) * 18), FLOOR + 1, (int) Math.round(Math.sin(a) * 18), false);
        }
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI / 5 + .15;
            lantern((int) Math.round(Math.cos(a) * 31), FLOOR + 1, (int) Math.round(Math.sin(a) * 31), false);
        }
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2 + Math.PI / 4;
            lantern((int) Math.round(Math.cos(a) * 11), FLOOR + 1, (int) Math.round(Math.sin(a) * 11), false);
        }
        // hanging chains with lanterns from the roof
        for (int ring = 0; ring < 3; ring++) {
            int count = 6 + ring * 4, rad = 12 + ring * 11;
            for (int i = 0; i < count; i++) {
                double a = i * 2 * Math.PI / count + ring * .4;
                int x = (int) Math.round(Math.cos(a) * rad), z = (int) Math.round(Math.sin(a) * rad);
                for (int y = CEIL - 3; y < CEIL; y++) set(x, y, z, Material.CHAIN);
                lantern(x, CEIL - 4, z, true);
            }
        }
    }

    /** Arena exit bell: pedestal, bell, lanterns and invisible light blocks (placed through the plan on a fresh build). */
    private void arenaBell() {
        for (int x = BELL_X - 2; x <= BELL_X + 2; x++) for (int z = BELL_Z - 1; z <= BELL_Z + 1; z++) set(x, FLOOR, z, Material.POLISHED_DEEPSLATE);
        set(BELL_X, FLOOR, BELL_Z, Material.CHISELED_DEEPSLATE);
        set(BELL_X, FLOOR + 1, BELL_Z, Material.CHISELED_DEEPSLATE);
        set(BELL_X, FLOOR + 2, BELL_Z, Material.BELL);
        lantern(BELL_X - 2, FLOOR + 1, BELL_Z, false);
        lantern(BELL_X + 2, FLOOR + 1, BELL_Z, false);
        for (int[] o : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) set(BELL_X + o[0], FLOOR + 3, BELL_Z + o[1], Material.LIGHT);
    }

    /** Makes sure the arena exit bell (and its floating label) exist in an already-built world. Returns true when blocks were changed. */
    static boolean ensureArenaBell(World w) {
        boolean changed = false;
        Block bell = w.getBlockAt(BELL_X, FLOOR + 2, BELL_Z);
        if (bell.getType() != Material.BELL) {
            for (int x = BELL_X - 2; x <= BELL_X + 2; x++) for (int z = BELL_Z - 1; z <= BELL_Z + 1; z++) {
                w.getBlockAt(x, FLOOR, z).setType(Material.POLISHED_DEEPSLATE, false);
                for (int y = FLOOR + 1; y <= FLOOR + 3; y++) w.getBlockAt(x, y, z).setType(Material.AIR, false);
            }
            w.getBlockAt(BELL_X, FLOOR, BELL_Z).setType(Material.CHISELED_DEEPSLATE, false);
            w.getBlockAt(BELL_X, FLOOR + 1, BELL_Z).setType(Material.CHISELED_DEEPSLATE, false);
            bell.setType(Material.BELL, false);
            for (int sx : new int[] {-2, 2}) {
                Lantern l = (Lantern) Material.SOUL_LANTERN.createBlockData();
                l.setHanging(false);
                w.getBlockAt(BELL_X + sx, FLOOR + 1, BELL_Z).setBlockData(l, false);
            }
            changed = true;
        }
        for (int[] o : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
            Block b = w.getBlockAt(BELL_X + o[0], FLOOR + 3, BELL_Z + o[1]);
            if (b.getType().isAir()) { b.setType(Material.LIGHT, false); changed = true; }
        }
        Location at = new Location(w, BELL_X + .5, FLOOR + 3.9, BELL_Z + .5);
        for (org.bukkit.entity.Entity e : w.getNearbyEntities(at, 4, 4, 4)) if (e.getScoreboardTags().contains(BELL_LABEL_TAG)) e.remove();
        w.spawn(at, org.bukkit.entity.TextDisplay.class, t -> {
            t.setText("§e§l나가기 §f(우클릭)\n§7파티 전원 동의 시 퇴장");
            t.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            t.setShadowed(true);
            t.setSeeThrough(true);
            t.setGlowing(true);
            t.setGlowColorOverride(Color.fromRGB(255, 220, 90));
            t.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
            t.setBackgroundColor(Color.fromARGB(150, 20, 10, 30));
            t.setViewRange(1.5f);
            t.setPersistent(false); // respawned at every fight start (and by repairdeepdark), never saved
            t.addScoreboardTag(BELL_LABEL_TAG);
        });
        return changed;
    }

    private void prep() {
        int x0 = -PREP_HALF - 2, x1 = PREP_HALF + 2, z0 = PREP_Z - PREP_HALF - 2, z1 = PREP_Z + PREP_HALF + 2;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            boolean wall = x < -PREP_HALF || x > PREP_HALF || z < PREP_Z - PREP_HALF || z > PREP_Z + PREP_HALF;
            set(x, FLOOR - 1, z, Material.DEEPSLATE);
            if (wall) {
                for (int y = FLOOR; y <= PREP_CEIL; y++) {
                    int h = hash(x + y, z, 11);
                    set(x, y, z, y == FLOOR ? Material.POLISHED_DEEPSLATE : y == PREP_CEIL - 1 ? Material.CHISELED_DEEPSLATE
                        : h < 55 ? Material.DEEPSLATE_BRICKS : h < 80 ? Material.DEEPSLATE_TILES : Material.CRACKED_DEEPSLATE_BRICKS);
                }
            } else {
                double r = Math.hypot(x, z - PREP_Z);
                Material f = r < 3 ? Material.CHISELED_DEEPSLATE : r >= 6 && r < 7 ? Material.REINFORCED_DEEPSLATE
                    : (Math.abs(x) == PREP_HALF - 1 || Math.abs(z - PREP_Z) == PREP_HALF - 1) ? Material.POLISHED_DEEPSLATE
                    : hash(Math.floorDiv(x, 4), Math.floorDiv(z, 4), 12) < 14 && hash(x, z, 13) < 65 ? Material.SCULK : tile(x, z);
                set(x, FLOOR, z, f);
                if (f == Material.SCULK && hash(x, z, 14) < 45) vein(x, FLOOR + 1, z);
            }
            set(x, PREP_CEIL, z, hash(x, z, 15) < 60 ? Material.DEEPSLATE_TILES : Material.DEEPSLATE_BRICKS);
            set(x, PREP_CEIL + 1, z, Material.DEEPSLATE);
        }
        // four interior pillars
        for (int sx : new int[] {-9, 9}) for (int sz : new int[] {-9, 9}) {
            int cz = PREP_Z + sz;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                for (int y = FLOOR + 1; y < PREP_CEIL; y++)
                    set(sx + dx, y, cz + dz, y == FLOOR + 1 || y == PREP_CEIL - 1 || y == FLOOR + 6 ? Material.CHISELED_DEEPSLATE
                        : (dx == 0 && dz == 0) ? Material.REINFORCED_DEEPSLATE : Material.DEEPSLATE_BRICKS);
            lantern(sx + 2, FLOOR + 1, cz, false);
            lantern(sx - 2, FLOOR + 1, cz, false);
        }
        // alcoves along both side walls: sculk sensors / shriekers (decor), lanterns above
        for (int z = PREP_Z - 11; z <= PREP_Z + 11; z += 6) for (int side : new int[] {-1, 1}) {
            int x = side * (PREP_HALF - 1);
            set(x, FLOOR + 1, z, (z / 6) % 2 == 0 ? Material.SCULK_SENSOR : Material.SCULK_SHRIEKER);
            lantern(x, FLOOR + 2, z, false);
        }
        // hanging lanterns
        for (int x = -10; x <= 10; x += 10) for (int zz = -10; zz <= 10; zz += 10) {
            int z = PREP_Z + zz;
            for (int y = PREP_CEIL - 3; y < PREP_CEIL; y++) set(x, y, z, Material.CHAIN);
            lantern(x, PREP_CEIL - 4, z, true);
        }
        // gate on the far (north) wall: reinforced frame, sculk gateway, lanterns
        int gz = PREP_Z - PREP_HALF - 1;
        for (int d = 0; d < 2; d++) {
            for (int y = FLOOR + 1; y <= FLOOR + 9; y++) {
                for (int x = -5; x <= 5; x++) {
                    boolean frame = Math.abs(x) >= 4 || y >= FLOOR + 8;
                    set(x, y, gz - d, frame ? Material.REINFORCED_DEEPSLATE : Material.SCULK);
                }
            }
        }
        for (int sx : new int[] {-7, 7}) { lantern(sx, FLOOR + 1, PREP_Z - PREP_HALF + 1, false); lantern(sx, FLOOR + 2, PREP_Z - PREP_HALF + 1, false); }
        // ready dais + lectern + sign
        int lz = PREP_Z - PREP_HALF + 7;
        for (int x = -2; x <= 2; x++) for (int z = lz - 1; z <= lz + 1; z++) set(x, FLOOR + 1, z, x == 0 ? Material.CHISELED_DEEPSLATE : Material.POLISHED_DEEPSLATE);
        Directional lect = (Directional) Material.LECTERN.createBlockData();
        lect.setFacing(BlockFace.SOUTH);
        put(0, FLOOR + 2, lz, lect);
        lantern(-2, FLOOR + 2, lz, false);
        lantern(2, FLOOR + 2, lz, false);
        set(3, FLOOR + 1, lz + 3, Material.CHISELED_DEEPSLATE);
        set(3, FLOOR + 2, lz + 3, Material.DARK_OAK_SIGN);
        // exit bell near the entrance
        set(0, FLOOR + 1, PREP_Z + PREP_HALF - 1, Material.BELL);
        set(1, FLOOR + 1, PREP_Z + PREP_HALF - 1, Material.DEEPSLATE_BRICK_WALL);
        set(-1, FLOOR + 1, PREP_Z + PREP_HALF - 1, Material.DEEPSLATE_BRICK_WALL);
    }

    private void signs(World w) {
        int lz = PREP_Z - PREP_HALF + 7;
        Block b = w.getBlockAt(3, FLOOR + 2, lz + 3);
        if (b.getState() instanceof Sign sign) {
            sign.setLine(0, "딥 다크 시티");
            sign.setLine(1, "준비방");
            sign.setLine(2, "렉턴 = 입장 투표");
            sign.setLine(3, "종 우클릭 = 나가기");
            sign.update(true, false);
        }
    }

    /** Builds the world in batches; {@code finish} runs when everything is placed. */
    void build(Player player, Runnable finish) {
        if (building) { player.sendMessage("딥 다크 맵을 만드는 중입니다."); return; }
        if (exists() || new File(Bukkit.getWorldContainer(), WORLD).exists()) {
            player.sendMessage("§c" + WORLD + " 월드가 이미 있습니다. 새로 만들려면 월드 폴더를 지운 서버에서 실행하세요.");
            return;
        }
        building = true;
        World world = Bukkit.createWorld(new WorldCreator(WORLD).type(WorldType.FLAT).generatorSettings(FLAT).generateStructures(false));
        if (world == null) { building = false; player.sendMessage("§c월드를 생성하지 못했습니다."); return; }
        world.setSpawnLocation(0, FLOOR + 1, PREP_Z + PREP_HALF - 3);
        rules(world);
        plan.clear();
        arena();
        arenaBell();
        prep();
        final List<Map.Entry<Long, BlockData>> entries = new ArrayList<>(plan.entrySet());
        plan.clear();
        player.sendMessage("§e딥 다크 맵 생성 시작: 블록 " + entries.size() + "개.");
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
                    if (pct / 10 != lastPct / 10) { lastPct = pct; player.sendMessage("§7딥 다크 맵 진행 " + pct + "%"); }
                    if (cursor < entries.size()) return;
                    signs(world);
                    ensureArenaBell(world);
                    world.save();
                    player.sendMessage("§a딥 다크 맵 완성. /pipilife raid deepdark 또는 /pipilife maps visit deepdark");
                    if (finish != null) finish.run();
                } catch (RuntimeException ex) {
                    plugin.getLogger().severe("deepdark build failed: " + ex);
                    ex.printStackTrace();
                    player.sendMessage("§c맵 생성 중 오류가 발생했습니다. 콘솔을 확인하세요.");
                }
                building = false;
                cancel();
            }
        }.runTaskTimer(plugin, 1, 1);
    }
}
