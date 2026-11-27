package dev.pipi.life;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * 촌장: the village elder, standing inside 촌장의 집 (ChiefHouse, north side of the pipi_town plaza) facing its door. The future quest giver.
 * Body = ItemDisplay of the PipiFacilities model fx_village_chief (dev/art/make-npc-models.mjs) + an Interaction hitbox + a name
 * label; without the mod a plain villager named 촌장 stands there instead. All parts carry {@link #TAG} and are not saved with the
 * chunk: they are placed again when the plaza chunk loads (no duplicates after restarts);
 * {@link #ensure} clears old parts around the spot before placing, so it is safe to run on every start.
 * Spot: the house's chief spot once the house is built; otherwise (or when config town.chief.x / town.chief.z are set) that block
 * column (default 0 / -4 on the plaza) with the ground height found automatically.
 */
final class VillageChief implements Listener {
    static final String TAG = "pipi_village_chief";
    private final PipiLife plugin;
    private final NamespacedKey met;
    private final Map<UUID, Long> lastTalk = new HashMap<>();
    private Location spot;
    private boolean modeled;
    private ChiefHouse house;

    void house(ChiefHouse house) { this.house = house; }

    VillageChief(PipiLife plugin) {
        this.plugin = plugin;
        met = new NamespacedKey(plugin, "chief_met");
        Bukkit.getScheduler().runTaskTimer(plugin, this::face, 40L, 10L);
    }

    /** Places the chief a little later (the mod's items may register after the plugin starts); retries a few times. */
    void schedule(World world, int attempt) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (RaidSorrow.fxMaterial("village_chief") == null && attempt < 6) { schedule(world, attempt + 1); return; }
            try { plugin.getLogger().info(ensure(world)); }
            catch (RuntimeException ex) { plugin.getLogger().warning("촌장 배치 실패: " + ex); }
        }, attempt == 0 ? 60L : 100L);
    }

    String ensure(World world) {
        int bx = plugin.getConfig().getInt("town.chief.x", 0), bz = plugin.getConfig().getInt("town.chief.z", -4);
        Location inHouse = house == null || plugin.getConfig().contains("town.chief.x") ? null : house.chiefSpot(world);
        if (inHouse != null) { bx = inHouse.getBlockX(); bz = inHouse.getBlockZ(); }
        world.getChunkAt(bx >> 4, bz >> 4).load();
        Location ground = inHouse != null ? inHouse : ground(world, bx, bz);
        if (ground == null) return "촌장: (" + bx + ", " + bz + ")에 설 자리를 찾지 못했습니다.";
        int removed = 0;
        for (Entity e : world.getNearbyEntities(ground, 8, 6, 8)) if (e.getScoreboardTags().contains(TAG)) { e.remove(); removed++; }
        // the 촌장 used to stand on the plaza by the spawn (0, -4) before the house existed: clear what is left there
        Location plaza = new Location(world, 0.5, ground.getY(), -3.5);
        world.getChunkAt(0, -1).load();
        world.getChunkAt(0, 0).load();
        for (Entity e : world.getNearbyEntities(plaza, 12, 40, 12))
            if (e.getScoreboardTags().contains(TAG) || isOldChief(e)) { e.remove(); removed++; }
        ground.setYaw(0);   // facing south, toward the plaza stone and the spawn
        spot = ground;
        var material = RaidSorrow.fxMaterial("village_chief");
        modeled = material != null;
        if (modeled) {
            world.spawn(ground.clone().add(0, .5, 0), ItemDisplay.class, d -> {
                d.setItemStack(new ItemStack(material));
                d.addScoreboardTag(TAG);
                d.setPersistent(false);
                d.setTeleportDuration(8);
                d.setRotation(0, 0);
            });
            world.spawn(ground, Interaction.class, i -> {
                i.setInteractionWidth(1f);
                i.setInteractionHeight(2f);
                i.setResponsive(true);
                i.addScoreboardTag(TAG);
                i.setPersistent(false);
            });
            world.spawn(ground.clone().add(0, 2.25, 0), TextDisplay.class, t -> {
                t.setText("§6§l촌장\n§7우클릭으로 대화");
                t.setBillboard(Display.Billboard.CENTER);
                t.setShadowed(true);
                t.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
                t.addScoreboardTag(TAG);
                t.setPersistent(false);
            });
        } else {
            world.spawn(ground, Villager.class, v -> {
                v.setCustomName("§6촌장");
                v.setCustomNameVisible(true);
                v.setProfession(Villager.Profession.LIBRARIAN);
                v.setVillagerLevel(5);
                v.setAI(false);
                v.setInvulnerable(true);
                v.setSilent(true);
                v.setPersistent(false);
                v.addScoreboardTag(TAG);
            });
        }
        return "촌장 배치: " + world.getName() + " (" + bx + ", " + ground.getBlockY() + ", " + bz + ")" + (modeled ? "" : " — 모드 모델이 없어 주민으로 대신 배치")
            + (removed > 0 ? " · 이전 것 " + removed + "개 정리" : "");
    }

    /** A plain villager named 촌장 from an old build (no tag). */
    private static boolean isOldChief(Entity e) {
        return e instanceof Villager v && v.getCustomName() != null && org.bukkit.ChatColor.stripColor(v.getCustomName()).equals("촌장");
    }

    /** Whenever entities load anywhere, drop 촌장 parts that are not at the current spot (saved copies from old builds). */
    @EventHandler
    public void strayParts(org.bukkit.event.world.EntitiesLoadEvent event) {
        if (spot == null || event.getWorld() != spot.getWorld()) return;
        for (Entity e : event.getEntities())
            if ((e.getScoreboardTags().contains(TAG) || isOldChief(e)) && e.getLocation().distanceSquared(spot) > 16) e.remove();
    }

    /** Standing spot on the highest solid block of the column (two free blocks above). */
    private static Location ground(World world, int x, int z) {
        for (int y = Math.min(world.getMaxHeight() - 3, 120); y > world.getMinHeight(); y--) {
            var b = world.getBlockAt(x, y, z);
            if (b.getType().isSolid() && b.getRelative(0, 1, 0).isPassable() && b.getRelative(0, 2, 0).isPassable())
                return new Location(world, x + .5, y + 1, z + .5);
        }
        return null;
    }

    /** Turns the chief toward the nearest player within 8 blocks (back to facing south when nobody is near). */
    private void face() {
        if (spot == null || !modeled || spot.getWorld() == null || !spot.getWorld().isChunkLoaded(spot.getBlockX() >> 4, spot.getBlockZ() >> 4)) return;
        Player near = null; double best = 64;
        for (Player p : spot.getWorld().getPlayers()) { double d = p.getLocation().distanceSquared(spot); if (d < best) { best = d; near = p; } }
        float yaw = near == null ? 0 : (float) Math.toDegrees(Math.atan2(-(near.getLocation().getX() - spot.getX()), near.getLocation().getZ() - spot.getZ()));
        for (Entity e : spot.getWorld().getNearbyEntities(spot, 1.5, 2, 1.5)) {
            if (!(e instanceof ItemDisplay d) || !d.getScoreboardTags().contains(TAG)) continue;
            if (Math.abs(((d.getLocation().getYaw() - yaw) % 360 + 540) % 360 - 180) < 4) continue;
            Location to = d.getLocation(); to.setYaw(yaw); d.teleport(to);
        }
    }

    /** The parts are not saved with the chunk: put the chief back whenever the plaza chunk's entities load again. */
    @EventHandler
    public void entitiesLoad(org.bukkit.event.world.EntitiesLoadEvent event) {
        if (spot == null || event.getWorld() != spot.getWorld() || event.getChunk().getX() != spot.getBlockX() >> 4 || event.getChunk().getZ() != spot.getBlockZ() >> 4) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Entity e : spot.getWorld().getNearbyEntities(spot, 3, 4, 3)) if (e.getScoreboardTags().contains(TAG)) return;
            plugin.getLogger().info(ensure(spot.getWorld()));
        });
    }

    @EventHandler
    public void click(PlayerInteractEntityEvent event) {
        if (!event.getRightClicked().getScoreboardTags().contains(TAG)) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player p = event.getPlayer();
        long now = System.currentTimeMillis();
        Long last = lastTalk.get(p.getUniqueId());
        if (last != null && now - last < 1500) return;
        lastTalk.put(p.getUniqueId(), now);
        talk(p);
    }

    private static final List<String> TIPS = List.of(
        "연금술사의 탑은 광장 서쪽이라네. 물약 하나쯤은 늘 챙겨 다니게.",
        "대장간에서는 장비를 만들고 다듬을 수 있지. 좋은 칼은 좋은 쇠에서 나오는 법이야.",
        "상점은 동쪽 끝에 있네. 남는 물건은 팔고, 모자란 건 사 오게.",
        "무기고 관리인에게 가면 쓰지 않는 장비를 맡겨 둘 수 있다네.",
        "선착장에서 배를 타면 숲과 평원과 사막으로 갈 수 있지. 길을 잃으면 마을로 돌아오게.",
        "펫은 혼자 두면 심심해한다네. 일을 맡기면 마을 살림도 거들어 주지.",
        "바다 건너 하늘 끝에 사는 용 이야기를 들어 보았나? 젊은 시절엔 나도 그 하늘을 올려다보곤 했지.",
        "깊은 땅 밑에는 소리를 쫓는 괴물이 산다더군. 발소리를 줄이고 다니게.");

    private void talk(Player p) {
        p.playSound(spot != null ? spot : p.getLocation(), Sound.ENTITY_VILLAGER_AMBIENT, SoundCategory.NEUTRAL, .8f, .75f);
        var pdc = p.getPersistentDataContainer();
        boolean first = !pdc.has(met, PersistentDataType.BYTE);
        if (first) {
            pdc.set(met, PersistentDataType.BYTE, (byte) 1);
            say(p, "어서 오게, 젊은이. 나는 이 마을을 지키는 촌장이라네.");
            say(p, "바다 건너 온 모험가들이 하나둘 늘더니, 마을이 제법 북적이게 되었구먼.");
            say(p, "자네의 모험 수첩을 하나 마련해 두었네. U 키로 언제든 펼쳐 보게.");
        }
        QuestSystem qs = plugin.quests();
        if (qs != null && qs.chiefHandIn(p)) return;     // talk objectives + hand in finished quests
        if (first) return;
        List<String> quest = plugin.tutorialQuest(p);
        if (quest != null && quest.size() > 1) {
            say(p, "지금은 §e" + quest.getFirst() + "§f부터 마치는 게 좋겠군.");
            say(p, "다음 할 일: §e" + org.bukkit.ChatColor.stripColor(quest.get(1)));
            return;
        }
        if (qs != null && qs.chiefHint(p)) return;
        say(p, TIPS.get(ThreadLocalRandom.current().nextInt(TIPS.size())));
    }

    private static void say(Player p, String line) { p.sendMessage("§6[촌장] §f" + line); }
}
