package dev.pipi.life;

import java.io.File;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.bukkit.*;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

final class VillageMaps {
    static final String TUTORIAL = "pipi_tutorial";
    private static final int GROUND = 63;
    private static final String FLAT = """
        {"biome":"minecraft:plains","layers":[{"block":"minecraft:bedrock","height":1},
        {"block":"minecraft:stone","height":123},{"block":"minecraft:dirt","height":3},
        {"block":"minecraft:grass_block","height":1}],"features":false,"lakes":false,"structure_overrides":[]}
        """;
    record Plot(String name, int x, int z) {}
    static List<Plot> plots() {
        List<Plot> result = new ArrayList<>();
        for (int row = 0; row < 2; row++)
            for (int col = 0; col < 5; col++)
                result.add(new Plot("plot%02d".formatted(row * 5 + col + 1), -122 + col * 50, row == 0 ? -79 : 36));
        return result;
    }

    private final PipiLife plugin;
    private final List<Runnable> blocks = new ArrayList<>();
    private boolean building;

    VillageMaps(PipiLife plugin) { this.plugin = plugin; }

    String village() { return plugin.getConfig().getString("village-world", "village"); }

    World open(String name) {
        World loaded = Bukkit.getWorld(name);
        if (loaded != null) return loaded;
        if (!new File(Bukkit.getWorldContainer(), name + "/level.dat").isFile()) return null;
        return Bukkit.createWorld(new WorldCreator(name));
    }

    void build(Player player, BooleanSupplier finish) {
        if (building) { player.sendMessage("맵을 만드는 중입니다."); return; }
        String village = village();
        if (!village.matches("[a-zA-Z0-9_-]+") || village.equals(TUTORIAL) ||
            exists(village) || exists(TUTORIAL)) {
            player.sendMessage("새 월드 이름이 필요합니다. 마을 또는 튜토리얼 월드가 이미 존재하는지 확인하세요.");
            return;
        }
        building = true;
        World town = create(village), tutorial = create(TUTORIAL);
        if (town == null || tutorial == null) {
            building = false;
            player.sendMessage("월드를 생성하지 못했습니다.");
            return;
        }
        town(town);
        tutorial(tutorial);
        player.sendMessage("마을과 튜토리얼 맵을 생성하고 있습니다.");
        new BukkitRunnable() {
            private int index;
            @Override public void run() {
                try {
                    int end = Math.min(index + 512, blocks.size());
                    while (index < end) blocks.get(index++).run();
                    if (index < blocks.size()) return;
                    town.save();
                    tutorial.save();
                    player.sendMessage(finish.getAsBoolean()
                        ? "맵과 부지 10개 준비 완료. /pipilife maps visit tutorial"
                        : "맵은 생성됐지만 부지 설정 저장에 실패했습니다.");
                } catch (RuntimeException ex) {
                    plugin.getLogger().severe("Map build failed: " + ex);
                    player.sendMessage("맵 생성 중 오류가 발생했습니다.");
                }
                blocks.clear();
                building = false;
                cancel();
            }
        }.runTaskTimer(plugin, 1, 1);
    }

    private boolean exists(String name) {
        return Bukkit.getWorld(name) != null || new File(Bukkit.getWorldContainer(), name).exists();
    }

    private World create(String name) {
        World world = Bukkit.createWorld(new WorldCreator(name).type(WorldType.FLAT)
            .generatorSettings(FLAT).generateStructures(false));
        if (world != null) {
            world.setSpawnLocation(0, 64, 0);
            world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setTime(6000);
        }
        return world;
    }

    private void town(World world) {
        fill(world, -128, GROUND, -6, 128, GROUND, 6, Material.STONE_BRICKS);
        fill(world, -38, GROUND, -26, 38, GROUND, 26, Material.SMOOTH_STONE);
        fill(world, -3, GROUND, -35, 3, GROUND, 36, Material.STONE_BRICKS);
        pavilion(world, -31, 9, Material.OAK_LOG, Material.DARK_OAK_PLANKS);
        pavilion(world, 18, 9, Material.POLISHED_ANDESITE, Material.DEEPSLATE_TILES);
        sign(world, -25, 64, 6, "상점", "테스트 건물");
        sign(world, 24, 64, 6, "대장간", "우클릭하여 이용");
        sign(world, 25, 64, 6, "연구소", "우클릭하여 이용");
        put(world, 24, 64, 15, Material.ANVIL);
        sign(world, 0, 64, 8, "공동 마을", "빈 부지: /pipilife claim");
        for (Plot plot : plots()) {
            int x = plot.x(), z = plot.z();
            int edge = z < 0 ? z + 43 : z;
            fill(world, x, GROUND, z, x + 43, GROUND, z, Material.OAK_PLANKS);
            fill(world, x, GROUND, z + 43, x + 43, GROUND, z + 43, Material.OAK_PLANKS);
            fill(world, x, GROUND, z, x, GROUND, z + 43, Material.OAK_PLANKS);
            fill(world, x + 43, GROUND, z, x + 43, GROUND, z + 43, Material.OAK_PLANKS);
            fill(world, x + 20, GROUND, Math.min(edge, 0), x + 23, GROUND, Math.max(edge, 0), Material.GRAVEL);
            sign(world, x + 19, 64, edge, plot.name(), "44 × 44", "/pipilife claim");
            put(world, x + 23, 64, z < 0 ? edge - 3 : edge + 3, Material.BARREL);
            put(world, x + 18, 64, edge, Material.SHROOMLIGHT);
        }
    }

    /** 튜토리얼 맵에서 레시 안내 펫이 서 있는 자리 (펫 안내소 가운데). */
    static Location tutorialPetSpot(World world) { return new Location(world, 8.5, 64, 12.5, 180, 0); }

    private void tutorial(World world) {
        fill(world, -17, GROUND, -10, 17, GROUND, 29, Material.SMOOTH_STONE);
        fill(world, -2, GROUND, -9, 2, GROUND, 28, Material.STONE_BRICKS);
        pavilion(world, -6, -19, Material.SPRUCE_LOG, Material.SPRUCE_PLANKS);
        sign(world, 3, 64, 2, "삐삐서버", "튜토리얼 마을");
        petCorner(world);
        sign(world, 3, 64, 24, "공동 마을 입장", "/pipilife finish");
        fill(world, -2, 64, 27, -2, 68, 27, Material.SPRUCE_LOG);
        fill(world, 2, 64, 27, 2, 68, 27, Material.SPRUCE_LOG);
        fill(world, -2, 68, 27, 2, 68, 27, Material.SPRUCE_PLANKS);
    }

    /** 펫 안내소: 레시가 서 있는 받침과 안내 표지판. */
    private void petCorner(World world) {
        fill(world, 6, GROUND, 10, 10, GROUND, 15, Material.POLISHED_ANDESITE);
        put(world, 6, 64, 15, Material.SHROOMLIGHT);
        put(world, 10, 64, 15, Material.SHROOMLIGHT);
        sign(world, 8, 64, 9, "펫 안내소", "레시를 우클릭해", "첫 펫으로", "데려가세요");
        sign(world, 3, 64, 12, "펫은 함께 일하고", "재료를 모으고", "물건을 만들어요");
    }

    /**
     * 이미 만들어진 튜토리얼 맵을 새 구성으로 바꾼다: 예전 직업 선택 표지판과 받침을 지우고 펫 안내소를 세운다.
     * 블록 몇십 개라 바로 적용한다.
     */
    boolean refreshTutorial() {
        World world = open(TUTORIAL);
        if (world == null || building) return false;
        List<Runnable> queued = new ArrayList<>(blocks);
        blocks.clear();
        for (int x : new int[] {-8, 0, 8}) {
            put(world, x, 64, 10, Material.AIR);
            put(world, x, 64, 14, Material.AIR);
            fill(world, x - 2, GROUND, 9, x + 2, GROUND, 14, Material.SMOOTH_STONE);
        }
        fill(world, -2, GROUND, 9, 2, GROUND, 14, Material.STONE_BRICKS);
        petCorner(world);
        for (Runnable block : blocks) block.run();
        blocks.clear();
        blocks.addAll(queued);
        world.save();
        return true;
    }

    private void pavilion(World world, int x, int z, Material post, Material roof) {
        fill(world, x, GROUND, z, x + 12, GROUND, z + 9, Material.SPRUCE_PLANKS);
        for (int dx : new int[] {0, 12}) for (int dz : new int[] {0, 9})
            fill(world, x + dx, 64, z + dz, x + dx, 68, z + dz, post);
        fill(world, x - 1, 69, z - 1, x + 13, 69, z + 10, roof);
        fill(world, x + 1, 64, z + 7, x + 11, 64, z + 7, Material.STRIPPED_SPRUCE_WOOD);
        put(world, x + 6, 68, z + 4, Material.SHROOMLIGHT);
    }

    private void fill(World world, int x1, int y1, int z1, int x2, int y2, int z2, Material material) {
        for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++)
            for (int y = y1; y <= y2; y++) put(world, x, y, z, material);
    }

    private void put(World world, int x, int y, int z, Material material) {
        blocks.add(() -> world.getBlockAt(x, y, z).setType(material, false));
    }

    private void sign(World world, int x, int y, int z, String... lines) {
        put(world, x, y, z, Material.OAK_SIGN);
        blocks.add(() -> {
            if (world.getBlockAt(x, y, z).getState() instanceof Sign sign) {
                for (int i = 0; i < lines.length; i++) sign.setLine(i, lines[i]);
                sign.update(true, false);
            }
        });
    }
}
