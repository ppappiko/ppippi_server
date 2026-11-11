package dev.pipi.travel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import org.lwjgl.glfw.GLFW;

public final class CombatBar {
    private static Minecraft client() { return Minecraft.getInstance(); }
    private static net.minecraft.client.multiplayer.ClientPacketListener connection;
    private static int savedSlot = -1;
    private static int selectedSkill;
    private static long lastJump;
    private static final long[] cooldownEnd = new long[8];
    private static final int[] cooldownLength = new int[8];
    private static final java.util.regex.Pattern COOLDOWN = java.util.regex.Pattern.compile("(회복|평타|대시|궁극기|보호막|스킬 [1-3]) 재사용 대기 · ([0-9]{1,6})ms(?: · 충전 ([0-9])/([0-9]))?");
    private static final int[] chargeCur = new int[8], chargeMax = new int[8];
    private static ItemStack weapon = ItemStack.EMPTY;
    private static final String MSG_START = "전투 모드 시작 · 슬롯 ", MSG_BASIC = "전투 모드 기본", MSG_END = "전투 모드 종료";
    private static boolean serverMode;
    private static int pendingSaved = -1;
    private static long pendingSince;
    private static Object lastPlayer;

    private CombatBar() {}

    // ---- skill loadout (server: PIPI_LOADOUT:<w|a|m>:<g1>,<g2>,<g3>,<buff>,<ult>; the old 3-id form keeps buff / ult at defaults)
    /** Equippable general skills per class (index 0 = warrior, 1 = archer, 2 = mage). */
    static final String[][] GENERAL = {
        {"w_rush", "w_whirl", "w_split", "w_heavy", "w_impale", "w_iai"},
        {"a_triple", "a_rain", "a_pierce", "a_spread", "a_shotgun", "a_gale"},
        {"m_seek", "m_frost", "m_thunder", "m_wave", "m_hammer", "m_bow"}};
    /** Buff choices (key 4, one equipped) and ultimate choices (key 5, one equipped) per class. */
    static final String[][] BUFF = {{"w_heal", "w_rage", "w_fort"}, {"a_heal", "a_focus", "a_spirit"}, {"m_heal", "m_focus", "m_reset"}};
    static final String[][] ULT = {{"w_ult", "w_tiger"}, {"a_ult", "a_wolf"}, {"m_cannon", "m_dragon"}};
    private static final String LOADOUT_PREFIX = "PIPI_LOADOUT:";
    private static final String[][] loadouts = {
        {"w_rush", "w_whirl", "w_split", "w_heal", "w_ult"}, {"a_triple", "a_rain", "a_pierce", "a_heal", "a_ult"}, {"m_seek", "m_frost", "m_thunder", "m_heal", "m_cannon"}};
    private static long suppressCooldownUntil;

    static int classIndex(String cls) { return switch (cls) { case "a" -> 1; case "m" -> 2; default -> 0; }; }

    /** Currently equipped ids of the class index (0 w, 1 a, 2 m): keys 1-3 general, [3] buff (key 4), [4] ultimate (key 5). */
    static String[] loadout(int cls) { return loadouts[cls].clone(); }

    private static boolean isCharged(String id) { return id.equals("w_rush") || id.equals("a_triple") || id.equals("m_seek"); }

    private static void receiveLoadout(String text) {
        String[] parts = text.substring(LOADOUT_PREFIX.length()).split(":");
        if (parts.length != 2) return;
        int c = classIndex(parts[0]);
        String[] ids = parts[1].split(",");
        if (ids.length != 3 && ids.length != 5) return;
        for (int i = 0; i < 3; i++) if (!java.util.Arrays.asList(GENERAL[c]).contains(ids[i])) return;
        String buff = BUFF[c][0], ult = ULT[c][0];
        if (ids.length == 5) {
            if (!java.util.Arrays.asList(BUFF[c]).contains(ids[3]) || !java.util.Arrays.asList(ULT[c]).contains(ids[4])) return;
            buff = ids[3];
            ult = ids[4];
        }
        System.arraycopy(ids, 0, loadouts[c], 0, 3);
        loadouts[c][3] = buff;
        loadouts[c][4] = ult;
        // the skills in keys 1-3 may have changed: forget their cooldown state, the server re-sends the running ones right after
        for (int s = 1; s <= 5; s++) { cooldownEnd[s] = 0; cooldownLength[s] = 0; chargeMax[s] = 0; chargeCur[s] = 0; }
        suppressCooldownUntil = System.nanoTime() / 1_000_000L + 1500;
    }

    public static boolean active() {
        var current = client().getConnection();
        if (connection != current || client().player == null || lastPlayer != client().player) {
            connection = current;
            lastPlayer = client().player; // respawn / dimension change makes a new player: the server already left combat mode
            java.util.Arrays.fill(cooldownEnd, 0);
            java.util.Arrays.fill(chargeMax, 0);
            savedSlot = -1;
            serverMode = false;
            pendingSaved = -1;
            weapon = ItemStack.EMPTY;
        }
        return savedSlot >= 0;
    }

    public static boolean key(int key, int action) {
        var client = client();
        if (client.player == null || client.getConnection() == null || client.screen != null) return false;
        boolean active = active();
        if (key == GLFW.GLFW_KEY_K) {
            if (action == GLFW.GLFW_PRESS) SkillScreen.open();
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT_ALT) {
            if (action == GLFW.GLFW_PRESS) toggle();
            return true;
        }
        if (active && key == GLFW.GLFW_KEY_SPACE && action == GLFW.GLFW_PRESS) {
            long now = System.nanoTime();
            if (lastJump != 0 && now - lastJump <= 320_000_000L) {
                lastJump = 0;
                // send the movement keys (strafe left+, forward+) so the dash follows WASD; none pressed = look direction
                var in = client.player.input;
                client.getConnection().sendCommand(String.format(java.util.Locale.ROOT, "pipilife skill dodge %.2f %.2f", in.leftImpulse, in.forwardImpulse));
                return true;
            }
            lastJump = now;
        }
        if (!active || key < GLFW.GLFW_KEY_1 || key > GLFW.GLFW_KEY_9) return false;
        if (action == GLFW.GLFW_PRESS) {
            selectedSkill = key - GLFW.GLFW_KEY_1;
            if (selectedSkill < 3) client.getConnection().sendCommand("pipilife skill attack " + (selectedSkill + 1));
            else if (selectedSkill == 3) client.getConnection().sendCommand("pipilife skill heal");
            else if (selectedSkill == 4) client.getConnection().sendCommand("pipilife skill ult");
            else if (selectedSkill == 5) client.getConnection().sendCommand("pipilife skill shield");
        }
        return true;
    }

    public static void basicAttack() {
        selectedSkill = 6;
        client().getConnection().sendCommand("pipilife skill basic");
    }

    private static boolean isWeapon(ItemStack item) {
        return item.getItem() instanceof SwordItem || item.is(Items.BOW)
            || item.is(Items.BLAZE_ROD) && item.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
    }

    private static void toggle() {
        lastJump = 0;
        var player = client().player;
        var bag = player.getInventory();
        if (active()) {
            if (serverMode) client().getConnection().sendCommand("pipilife combat off");
            bag.selected = savedSlot;
            savedSlot = -1;
            serverMode = false;
            weapon = ItemStack.EMPTY;
            return;
        }
        long now = System.nanoTime();
        if (pendingSaved >= 0 && now - pendingSince < 2_000_000_000L) return; // waiting for the server's answer
        pendingSaved = bag.selected;
        pendingSince = now;
        // the server puts the equipped weapon (무기 관리인) into the hand; without one it answers MSG_BASIC and we use the hotbar weapon
        client().getConnection().sendCommand("pipilife combat on " + bag.selected);
    }

    private static void enterLocal() {
        var player = client().player;
        var bag = player.getInventory();
        int slot = bag.selected;
        if (!isWeapon(bag.getItem(slot))) {
            slot = 0;
            while (slot < 9 && !isWeapon(bag.getItem(slot))) slot++;
        }
        if (slot == 9) {
            player.displayClientMessage(Component.literal("무기 관리인에게 무기를 장착하세요."), true);
            return;
        }
        savedSlot = bag.selected;
        bag.selected = slot;
        weapon = bag.getItem(slot);
        selectedSkill = 0;
    }

    static void receiveCooldown(net.neoforged.neoforge.client.event.ClientChatReceivedEvent.System event) {
        String text = event.getMessage().getString();
        if (text.startsWith(LOADOUT_PREFIX)) {
            event.setCanceled(true);
            receiveLoadout(text);
            return;
        }
        if (text.startsWith(MSG_START) || text.equals(MSG_BASIC) || text.equals(MSG_END)) {
            event.setCanceled(true);
            var client = client();
            if (client.player == null) return;
            var bag = client.player.getInventory();
            if (text.equals(MSG_END)) {
                pendingSaved = -1;
                if (active() && serverMode) {
                    if (savedSlot >= 0 && savedSlot < 9) bag.selected = savedSlot;
                    savedSlot = -1;
                    serverMode = false;
                    weapon = ItemStack.EMPTY;
                }
                return;
            }
            if (pendingSaved < 0 || active()) return;
            int saved = pendingSaved;
            pendingSaved = -1;
            if (text.equals(MSG_BASIC)) { enterLocal(); return; }
            try {
                int slot = Integer.parseInt(text.substring(MSG_START.length()).trim()) - 1;
                if (slot < 0 || slot > 8) return;
                savedSlot = saved;
                bag.selected = slot;
                serverMode = true;
                selectedSkill = 0;
            } catch (NumberFormatException ignored) { }
            return;
        }
        var match = COOLDOWN.matcher(text);
        if (!match.matches()) return;
        boolean showing = active() || System.nanoTime() / 1_000_000L < suppressCooldownUntil;
        int slot = switch (match.group(1)) {
            case "평타" -> 0;
            case "회복" -> 4;
            case "궁극기" -> 5;
            case "대시" -> 6;
            case "보호막" -> 7;
            default -> match.group(1).charAt(3) - '0';
        };
        int millis = Integer.parseInt(match.group(2));
        if (millis <= 0) return;
        if (match.group(3) != null) {
            chargeMax[slot] = Integer.parseInt(match.group(4));
            chargeCur[slot] = Integer.parseInt(match.group(3));
            if (chargeCur[slot] < chargeMax[slot] - 1) { // refill already running: keep the full per-charge length for the overlay
                if (cooldownLength[slot] < millis) cooldownLength[slot] = millis;
                cooldownEnd[slot] = System.nanoTime() / 1_000_000L + millis;
                if (showing) event.setCanceled(true);
                return;
            }
        } else chargeMax[slot] = 0;
        cooldownLength[slot] = millis;
        cooldownEnd[slot] = System.nanoTime() / 1_000_000L + millis;
        if (showing) event.setCanceled(true);
    }

    private static void gauge(GuiGraphics graphics, int slot, int x, int y, int size) {
        long nowMs = System.nanoTime() / 1_000_000L;
        long remaining = cooldownEnd[slot] - nowMs;
        int max = chargeMax[slot];
        if (max > 1) {
            // client-side refill: one charge returns each time the running cooldown ends
            while (cooldownEnd[slot] > 0 && remaining <= 0 && chargeCur[slot] < max) {
                chargeCur[slot]++;
                if (chargeCur[slot] < max) { cooldownEnd[slot] += Math.max(1, cooldownLength[slot]); remaining = cooldownEnd[slot] - nowMs; }
            }
            var pose = graphics.pose();
            String count = Integer.toString(chargeCur[slot]);
            pose.pushPose();
            pose.translate(x + size - 1, y + size - 7, 200);
            pose.scale(.8f, .8f, 1);
            graphics.drawString(client().font, count, -client().font.width(count), 0, chargeCur[slot] > 0 ? 0xffffe27a : 0xffff6a5a, true);
            pose.popPose();
            if (chargeCur[slot] < max && chargeCur[slot] > 0 && remaining > 0 && cooldownLength[slot] > 0) {
                int w = (int) (size * Math.clamp(1 - (double) remaining / cooldownLength[slot], 0, 1));
                graphics.fill(x, y + size - 2, x + size, y + size, 0xb0091420);
                graphics.fill(x, y + size - 2, x + w, y + size, 0xffffe27a);
                return;
            }
            if (chargeCur[slot] > 0) return;
        }
        if (remaining <= 0 || cooldownLength[slot] <= 0) return;
        double frac = Math.clamp((double) remaining / cooldownLength[slot], 0, 1);
        int h = (int) Math.ceil(size * frac);
        graphics.fill(x, y + size - h, x + size, y + size, 0xb0091420); // darkened part shrinks as the cooldown runs out
        String seconds = remaining < 1000 ? String.format(java.util.Locale.ROOT, "%.1f", Math.ceil(remaining / 100.0) / 10)
            : Long.toString((remaining + 999) / 1000);
        graphics.drawCenteredString(client().font, seconds, x + size / 2, y + size / 2 - 4, 0xffe9f8ff);
    }

    private static net.minecraft.resources.ResourceLocation icon(String id) {
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("pipitravel", "textures/gui/skills/" + id + ".png");
    }

    private static final long FLIP_MS = 250;
    private static boolean lastActive;
    private static long flipStart;

    /** Returns {showSkillBar(0/1), scaleY} for the bar to display right now. */
    static float[] flipState() {
        boolean now = active();
        long t = System.currentTimeMillis();
        if (now != lastActive) {
            lastActive = now;
            flipStart = t;
        }
        float p = (t - flipStart) / (float) FLIP_MS;
        if (p >= 1f || flipStart == 0) return new float[] {now ? 1 : 0, 1};
        float q = p < 0.5f ? p * 2 : (p - 0.5f) * 2;
        float e = q * q * (3 - 2 * q);
        boolean skillSide = p < 0.5f ? !now : now;
        return new float[] {skillSide ? 1 : 0, p < 0.5f ? 1 - e : e};
    }

    /** The held-item name belongs to the normal bar: hide it while the skill side is showing. */
    static void layerPre(net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre event) {
        if (!event.getName().equals(net.neoforged.neoforge.client.gui.VanillaGuiLayers.SELECTED_ITEM_NAME)) return;
        var client = client();
        if (client.player == null || client.screen != null || client.options.hideGui) return;
        if (flipState()[0] == 1) event.setCanceled(true);
    }

    static void updateWeapon() {
        if (active()) weapon = client().player.getInventory().getSelected();
    }

    /** Slot positions: 0 = off-hand position, 1-4 = hotbar 1-4 (left group), 5-9 = hotbar 5-9 (right group). Maps to the skill order (attack 1-3, heal, ult, dash). */
    private static int skillIndex(int pos) {
        return switch (pos) { case 1, 2, 3, 4 -> pos - 1; case 5 -> 4; case 6 -> 5; case 0 -> 6; default -> -1; };
    }

    static String skillLabel(int pos) {
        return switch (pos) { case 0 -> "대시"; case 1, 2, 3, 4, 5, 6 -> Integer.toString(pos); case 7 -> "평타"; default -> ""; };
    }

    static boolean skillSelected(int pos) {
        int idx = skillIndex(pos);
        if (pos == 7) return selectedSkill == 6;
        return idx >= 0 && idx < 6 && idx == selectedSkill;
    }

    static boolean skillUsed(int pos) { return pos <= 7; }

    /** Draws the skill for the slot at logical position pos (0..9) with its 16x16 content at (x, y). */
    static void skillSlot(GuiGraphics graphics, int pos, int x, int y) {
        String cls = weapon.is(Items.BOW) ? "a" : weapon.is(Items.BLAZE_ROD) ? "m" : "w";
        if (pos == 7) {
            graphics.renderItem(weapon, x, y);
            gauge(graphics, 0, x, y, 16);
            return;
        }
        // 2-charge skills: 전사 돌진 베기, 궁수 삼연사, 마법사 유도 마탄 (wherever they are equipped)
        String equipped = pos >= 1 && pos <= 3 ? loadouts[classIndex(cls)][pos - 1] : null;
        if (equipped != null) {
            if (!isCharged(equipped)) chargeMax[pos] = 0;
            else if (chargeMax[pos] == 0) { chargeMax[pos] = 2; chargeCur[pos] = 2; }
        }
        String id = switch (pos) {
            case 6 -> cls + "_shield";
            case 1, 2, 3 -> equipped;
            case 4 -> loadouts[classIndex(cls)][3];
            case 5 -> loadouts[classIndex(cls)][4];
            case 0 -> "c_dash";
            default -> null;
        };
        if (id == null) return;
        graphics.blit(icon(id), x, y, 16, 16, 0, 0, 32, 32, 32, 32);
        gauge(graphics, pos == 6 ? 7 : pos == 0 ? 6 : skillIndex(pos) + 1, x, y, 16);
    }
}
