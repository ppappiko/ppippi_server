package dev.pipi.travel;

import com.mojang.math.Axis;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/** Class cast motions: PIPI_ANIM:&lt;entityId&gt;:&lt;type&gt; from the server drives arm poses (third person) and hand transform (first person). */
public final class CastAnim {
    private static final String PREFIX = "PIPI_ANIM:";
    /** flip: sword slashes alternate right→left and left→right (an X). */
    private record Anim(long start, String type, boolean flip) {}
    private static final Map<Integer, Anim> ACTIVE = new ConcurrentHashMap<>();
    private static final Map<Integer, Boolean> LAST_FLIP = new ConcurrentHashMap<>();
    private static boolean flipOf(int id) { Anim a = ACTIVE.get(id); return a != null && a.flip; }

    private CastAnim() {}

    static void receive(ClientChatReceivedEvent.System event) {
        String text = event.getMessage().getString();
        if (!text.startsWith(PREFIX)) return;
        event.setCanceled(true);
        String[] parts = text.substring(PREFIX.length()).trim().split(":");
        if (parts.length != 2) return;
        try {
            long now = System.currentTimeMillis();
            ACTIVE.values().removeIf(a -> now - a.start > 2000);
            int id = Integer.parseInt(parts[0].trim());
            String type = parts[1].trim();
            boolean flip = false;
            if (type.startsWith("sword")) { flip = !LAST_FLIP.getOrDefault(id, true); LAST_FLIP.put(id, flip); }
            ACTIVE.put(id, new Anim(now, type, flip));
        } catch (NumberFormatException ignored) { }
    }

    private static long duration(String type) {
        return switch (type) {
            // release keyframes match the server wind-up: mage 7t (350ms), archer 6t (300ms), warrior skill 6t (300ms),
            // warrior basic 5t (250ms), heal 4t (200ms)
            case "staff_thrust" -> 600;
            case "staff_quick" -> 500; // mage basic: charge, release at 6t (300ms)
            case "sword_swing" -> 520;
            case "sword_big" -> 650;
            case "bow_draw" -> 560;
            case "bow_quick" -> 260;
            default -> 420; // raise
        };
    }

    /** Progress 0..1 of this entity's animation, or -1 when none is active. */
    private static float progress(int id, String[] typeOut) {
        Anim anim = ACTIVE.get(id);
        if (anim == null) return -1;
        long d = duration(anim.type);
        long elapsed = System.currentTimeMillis() - anim.start;
        if (elapsed >= d) { ACTIVE.remove(id, anim); return -1; }
        typeOut[0] = anim.type;
        return Math.max(0f, elapsed / (float) d);
    }

    private static float smooth(float t) { t = Math.clamp(t, 0f, 1f); return t * t * (3 - 2 * t); }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    /** Piecewise-linear keyframes: pairs of (time, value). */
    private static float key(float t, float... kv) {
        if (t <= kv[0]) return kv[1];
        for (int i = 2; i < kv.length; i += 2) {
            if (t <= kv[i]) return lerp(kv[i - 1], kv[i + 1], smooth((t - kv[i - 2]) / (kv[i] - kv[i - 2])));
        }
        return kv[kv.length - 1];
    }
    /** Blend weight: quick ease in, hold, ease out. */
    private static float weight(float t) { return Math.min(smooth(t / 0.15f), smooth((1 - t) / 0.25f)); }

    /** Called from the HumanoidModel#setupAnim mixin (tail). */
    public static void apply(LivingEntity entity, HumanoidModel<?> model) {
        if (!(entity instanceof Player)) return;
        String[] type = new String[1];
        float t = progress(entity.getId(), type);
        if (t < 0) return;
        float w = weight(t);
        float headX = model.head.xRot, headY = model.head.yRot;
        switch (type[0]) {
            case "staff_thrust", "staff_quick" -> {
                // staff pushed straight out front (head forward, butt toward the player), trembling while charging, short push on release.
                // The vanilla held-item transform makes a rod stick out of the fist roughly perpendicular to the arm, so the arm is
                // raised only ~55 degrees (not fully horizontal) which lays the rod about level with the view; it follows head pitch.
                float rel = type[0].equals("staff_quick") ? 0.6f : 0.583f;
                float hold = key(t, 0f, 0f, 0.2f, 1f, rel - 0.03f, 1f, rel + 0.03f, 0f);
                float shake = (float) Math.sin(t * 90f) * 0.03f * hold;
                float x = key(t, 0f, -0.5f, 0.2f, -0.95f, rel - 0.03f, -1.0f, rel + 0.03f, -1.2f, 1f, -0.9f) + headX + shake;
                float y = key(t, 0f, -0.2f, 0.2f, -0.05f, 1f, -0.05f) + headY + shake;
                set(model.rightArm, x, y, 0f, w);
            }
            case "sword_swing" -> {
                // basic: diagonal slash with the blade pointing at the target, alternating ↙ (right→left) and ↘ (left→right) = an X.
                // arm mostly forward/down so the held sword's tip points ahead; start high on one side, cut down to the other.
                float rel = 0.48f;
                float s = flipOf(entity.getId()) ? -1f : 1f; // +1: start upper-right, -1: start upper-left
                float hold = key(t, 0f, 0f, 0.3f, 1f, rel - 0.02f, 1f, rel + 0.08f, 0f);
                float shake = (float) Math.sin(t * 100f) * 0.03f * hold;
                float x = key(t, 0f, -0.9f, 0.3f, -1.75f, rel - 0.02f, -1.75f, rel + 0.12f, -0.45f, 1f, -0.7f) + headX * 0.5f + shake;
                float y = s * key(t, 0f, -0.3f, 0.3f, -0.75f, rel - 0.02f, -0.75f, rel + 0.12f, 0.7f, 1f, 0.45f);
                float z = s * key(t, 0f, 0.2f, 0.3f, 0.45f, rel - 0.02f, 0.45f, rel + 0.12f, -0.35f, 1f, -0.15f);
                set(model.rightArm, x, y, z, w);
            }
            case "sword_big" -> {
                // anticipation: raise and hold with a tremble, fast heavy slash at the release key, long follow-through
                boolean big = type[0].equals("sword_big");
                float rel = big ? 0.46f : 0.48f;
                float hold = key(t, 0f, 0f, 0.3f, 1f, rel - 0.02f, 1f, rel + 0.08f, 0f);
                float shake = (float) Math.sin(t * 100f) * 0.03f * hold;
                float x = key(t, 0f, -1.0f, 0.3f, big ? -2.75f : -2.45f, rel - 0.02f, big ? -2.8f : -2.45f, rel + 0.1f, -0.9f, 0.7f, -0.7f, 1f, -1.1f) + headX * 0.5f + shake;
                float y = key(t, 0f, -0.9f, 0.3f, big ? -1.5f : -1.3f, rel - 0.02f, big ? -1.5f : -1.3f, rel + 0.1f, big ? 1.2f : 1.0f, 1f, big ? 1.0f : 0.8f);
                set(model.rightArm, x, y, big ? key(t, 0f, 0.3f, rel, 0.3f, 1f, -0.2f) : 0f, w);
            }
            case "bow_draw", "bow_quick" -> {
                // right arm holds the bow forward, left arm pulls the string back (held), snap on release (~0.54)
                set(model.rightArm, -1.5708f + headX, -0.1f + headY, 0f, w);
                float pull = key(t, 0f, 0f, 0.5f, 1f, 0.56f, 0f, 0.75f, 0.12f, 1f, 0f);
                float x = -1.5708f + headX + pull * 0.1f;
                float y = headY + lerp(0.9f, 0.2f, pull);
                set(model.leftArm, x, y, 0f, w);
            }
            default -> { // raise
                float x = key(t, 0f, -0.4f, 0.35f, -2.3f, 0.48f, -2.4f, 1f, -2.1f);
                set(model.rightArm, x, -0.15f, 0.1f, w);
            }
        }
    }

    private static void set(net.minecraft.client.model.geom.ModelPart arm, float x, float y, float z, float w) {
        arm.xRot = lerp(arm.xRot, x, w);
        arm.yRot = lerp(arm.yRot, y, w);
        arm.zRot = lerp(arm.zRot, z, w);
    }

    /** First person: offset/rotate the main hand + item while the animation runs. */
    static void hand(RenderHandEvent event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        String[] type = new String[1];
        float t = progress(player.getId(), type);
        if (t < 0) return;
        float w = weight(t);
        var pose = event.getPoseStack();
        switch (type[0]) {
            case "staff_thrust", "staff_quick" -> {
                // staff lies along the look axis (head into the screen, butt toward the player), slightly right/below the crosshair;
                // held with a tremble, short forward push at the release key, then recover
                float rel = type[0].equals("staff_quick") ? 0.6f : 0.583f;
                float push = key(t, 0f, 0f, rel - 0.03f, 0f, rel + 0.03f, 1f, 1f, 0f);
                float hold = key(t, 0f, 0f, 0.2f, 1f, rel - 0.03f, 1f, rel + 0.03f, 0f);
                float shake = (float) Math.sin(t * 90f) * 0.01f * hold;
                // stays visible on the right: shaft from the lower-right corner, head leaning forward toward the crosshair
                pose.translate((-0.08f * w) + shake, (0.06f * w) + shake, (-0.12f * w) - 0.25f * push * w);
                pose.mulPose(Axis.XP.rotationDegrees(-38f * w)); // tip the head forward into the scene
                pose.mulPose(Axis.ZP.rotationDegrees(14f * w));  // lean the head toward the screen centre
                pose.mulPose(Axis.YP.rotationDegrees(-6f * w));
            }
            case "sword_swing" -> {
                // basic: tip pointed forward, diagonal cut alternating ↙ / ↘ (an X): start high on one side, end low on the other
                float rel = 0.48f;
                float s = flipOf(player.getId()) ? -1f : 1f;
                float hold = key(t, 0f, 0f, 0.3f, 1f, rel - 0.02f, 1f, rel + 0.08f, 0f);
                float shake = (float) Math.sin(t * 100f) * 0.012f * hold;
                float cut = key(t, 0f, -0.5f, 0.3f, -1f, rel - 0.02f, -1f, rel + 0.12f, 1f, 1f, 0.7f); // -1 high start, +1 low end
                float thrust = key(t, 0f, 0f, rel - 0.02f, 0f, rel + 0.12f, 1f, 1f, 0.3f);
                pose.translate((-cut * 0.32f * s) * w + shake, (-cut * 0.22f) * w, (0.05f - 0.3f * thrust) * w);
                pose.mulPose(Axis.XP.rotationDegrees(-55f * w));                 // tip toward the target
                pose.mulPose(Axis.ZP.rotationDegrees(s * (35f - cut * 25f) * w)); // diagonal blade angle
                pose.mulPose(Axis.YP.rotationDegrees(cut * s * 25f * w));
            }
            case "sword_big" -> {
                boolean big = type[0].equals("sword_big");
                float rel = big ? 0.46f : 0.48f;
                float hold = key(t, 0f, 0f, 0.3f, 1f, rel - 0.02f, 1f, rel + 0.08f, 0f);
                float shake = (float) Math.sin(t * 100f) * 0.012f * hold;
                // -1 = raised back, +1 = follow-through across
                float swing = key(t, 0f, -0.5f, 0.3f, -1f, rel - 0.02f, -1f, rel + 0.1f, 1f, 1f, 0.7f);
                pose.translate(swing * (big ? 0.45f : 0.35f) * w + shake, (-0.12f + 0.2f * hold) * w, -0.25f * w);
                pose.mulPose(Axis.YP.rotationDegrees(-swing * (big ? 60f : 45f) * w));
                pose.mulPose(Axis.ZP.rotationDegrees(swing * (big ? 38f : 28f) * w));
                pose.mulPose(Axis.XP.rotationDegrees(-30f * w));
            }
            case "bow_draw", "bow_quick" -> {
                float pull = key(t, 0f, 0f, 0.5f, 1f, 0.56f, 0f, 0.75f, 0.12f, 1f, 0f);
                // bow on the right side and pushed forward, clear of the screen centre
                pose.translate(0.22f * w, -0.05f * w, -0.3f * w + 0.12f * pull * w);
                pose.mulPose(Axis.YP.rotationDegrees(4f * w));
                pose.mulPose(Axis.XP.rotationDegrees(-8f * pull * w));
            }
            default -> {
                pose.translate(0f, 0.25f * w, -0.1f * w);
                pose.mulPose(Axis.XP.rotationDegrees(-40f * w));
            }
        }
    }
}
