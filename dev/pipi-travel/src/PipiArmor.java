package dev.pipi.travel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Worn rendering for PipiLife armour (families 근거리/원거리/마법 = warrior/archer/mage, tiers 0-5).
 * Identification: the server already writes "pipilife:smith_catalog" (e.g. "3티어 근거리 투구") into PublicBukkitValues of the
 * item's custom_data; the tier digit and family word are parsed from it. Anything else keeps the vanilla look.
 * Rendering: the layer texture on the vanilla armour model + extra 3D cuboid parts baked from assets/pipitravel/armor_parts/*.json.
 */
public final class PipiArmor {
    private PipiArmor() {}

    public record Info(int cls, int tier) {}

    static final String[] CLS = {"warrior", "archer", "mage"};
    private static final Pattern CATALOG = Pattern.compile("^([0-5])티어 (근거리|원거리|마법)");
    private static final Info NONE = new Info(-1, -1);
    private static final Map<ItemStack, Info> CACHE = new WeakHashMap<>();

    // slot indices used by the baked groups
    private static final int HEAD = 0, CHEST = 1, LEGS = 2, FEET = 3;
    // bones
    private static final int B_HEAD = 0, B_BODY = 1, B_RARM = 2, B_LARM = 3, B_RLEG = 4, B_LLEG = 5;
    private static final float[][] PIVOT = {{0, 0, 0}, {0, 0, 0}, {-5, 2, 0}, {5, 2, 0}, {-1.9f, 12, 0}, {1.9f, 12, 0}};

    private record Group(ResourceLocation texture, int bone, float[] data, boolean cape) {}
    private static final Map<String, List<Group>[]> BAKED = new HashMap<>();
    private static final Map<String, ResourceLocation> LAYERS = new HashMap<>();
    /** set -Dpipi.armor.parts=false to draw only the layer textures (debug) */
    private static final boolean PARTS = !"false".equalsIgnoreCase(System.getProperty("pipi.armor.parts"));

    public static void registerReload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> BAKED.clear());
    }

    // ---------- identification ----------
    public static Info identify(ItemStack stack, EquipmentSlot slot) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ArmorItem armor) || armor.getEquipmentSlot() != slot) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        Info info = CACHE.get(stack);
        if (info == null) {
            info = NONE;
            CompoundTag tag = data.copyTag();
            CompoundTag pub = tag.contains("PublicBukkitValues") ? tag.getCompound("PublicBukkitValues") : tag;
            if (pub.contains("pipilife:smith_catalog")) {
                Matcher m = CATALOG.matcher(pub.getString("pipilife:smith_catalog"));
                if (m.find()) info = new Info(switch (m.group(2)) { case "원거리" -> 1; case "마법" -> 2; default -> 0; }, Integer.parseInt(m.group(1)));
            }
            CACHE.put(stack, info);
        }
        return info == NONE ? null : info;
    }

    // ---------- rendering ----------
    public static void render(PoseStack ps, MultiBufferSource buffer, HumanoidModel<?> model, EquipmentSlot slot, Info info, int light) {
        int s = switch (slot) { case HEAD -> HEAD; case CHEST -> CHEST; case LEGS -> LEGS; default -> FEET; };
        String cls = CLS[info.cls()], tier = "_t" + info.tier();
        // layer texture on the vanilla armour model (inner for legs, outer otherwise); its visible parts are already set for this slot
        String suffix = s == LEGS ? "2" : "1";
        ResourceLocation layer = LAYERS.computeIfAbsent(cls + tier + "_" + suffix,
            k -> ResourceLocation.fromNamespaceAndPath("pipitravel", "textures/models/armor/" + cls + tier + "_layer_" + suffix + ".png"));
        model.renderToBuffer(ps, buffer.getBuffer(RenderType.armorCutoutNoCull(layer)), light, OverlayTexture.NO_OVERLAY, -1);
        if (!PARTS) return;
        if (s == HEAD) {
            parts(ps, buffer, model, s, "hat_" + cls + tier, light);
        } else if (s == CHEST) {
            if (info.cls() == 0) for (String p : new String[]{"pauldrons", "couters", "cuffs"}) parts(ps, buffer, model, s, p + "_warrior" + tier, light);
            else parts(ps, buffer, model, s, "gear_" + cls + tier, light);
            if (info.tier() >= 3) parts(ps, buffer, model, s, "cape_" + cls + tier, light);
        } else if (info.cls() == 0) {
            parts(ps, buffer, model, s, "fauld_warrior" + tier, light);
            parts(ps, buffer, model, s, "greaves_warrior" + tier, light);
        } else {
            parts(ps, buffer, model, s, "gear_" + cls + tier, light);
        }
    }

    private static ModelPart bone(HumanoidModel<?> m, int b) {
        return switch (b) { case B_HEAD -> m.head; case B_BODY -> m.body; case B_RARM -> m.rightArm; case B_LARM -> m.leftArm; case B_RLEG -> m.rightLeg; default -> m.leftLeg; };
    }

    private static void parts(PoseStack ps, MultiBufferSource buffer, HumanoidModel<?> model, int slot, String name, int light) {
        List<Group> groups = BAKED.computeIfAbsent(name, PipiArmor::bake)[slot];
        if (groups.isEmpty()) return;
        for (Group g : groups) {
            VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(g.texture()));
            ps.pushPose();
            bone(model, g.bone()).translateAndRotate(ps);
            if (g.cape()) {
                float swing = Mth.clamp(Math.abs(model.rightLeg.xRot) * 0.45f, 0f, 0.45f);
                ps.translate(0, -0.6f / 16f, 4.2f / 16f);
                ps.mulPose(Axis.XP.rotation(swing));
                ps.translate(0, 0.6f / 16f, -4.2f / 16f);
            }
            PoseStack.Pose pose = ps.last();
            float[] d = g.data();
            for (int i = 0; i < d.length; i += 8)
                vc.addVertex(pose, d[i], d[i + 1], d[i + 2]).setColor(-1).setUv(d[i + 3], d[i + 4])
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, d[i + 5], d[i + 6], d[i + 7]);
            ps.popPose();
        }
    }

    // ---------- baking (once per part after each resource reload) ----------
    private static final String[] FACES = {"south", "north", "west", "east", "up", "down"};

    @SuppressWarnings("unchecked")
    private static List<Group>[] bake(String name) {
        List<Group>[] out = new List[4];
        for (int i = 0; i < 4; i++) out[i] = new ArrayList<>();
        ResourceManager rm = Minecraft.getInstance().getResourceManager();
        var res = rm.getResource(ResourceLocation.fromNamespaceAndPath("pipitravel", "armor_parts/" + name + ".json"));
        if (res.isEmpty()) return out;
        JsonArray elements;
        try (BufferedReader r = res.get().openAsReader()) {
            elements = JsonParser.parseReader(r).getAsJsonObject().getAsJsonArray("elements");
        } catch (Exception e) {
            return out;
        }
        ResourceLocation tex = ResourceLocation.fromNamespaceAndPath("pipitravel", "textures/models/armor/parts/" + name + ".png");
        boolean hat = name.startsWith("hat_"), cape = name.startsWith("cape_");
        float yoff = hat ? 24f : 0f;
        Map<Integer, List<Float>> acc = new HashMap<>(); // slot*8+bone -> interleaved vertex data
        for (JsonElement je : elements) {
            JsonObject e = je.getAsJsonObject();
            String en = e.get("name").getAsString();
            int bone = hat ? B_HEAD : en.endsWith("_rarm") ? B_RARM : en.endsWith("_larm") ? B_LARM : en.endsWith("_rleg") ? B_RLEG : en.endsWith("_lleg") ? B_LLEG : B_BODY;
            float[] f = vec(e.getAsJsonArray("from")), t = vec(e.getAsJsonArray("to"));
            float cy = (f[1] + t[1]) / 2f;
            int slot = hat ? HEAD : cape ? CHEST
                : bone == B_BODY ? (cy < 12f ? LEGS : CHEST)
                : bone == B_RARM || bone == B_LARM ? CHEST
                : (cy < 3f ? FEET : LEGS);
            // mannequin frame: x = bx-8, y = by+yoff, z = bz-8 (y up, front = +z)
            float x0 = f[0] - 8, y0 = f[1] + yoff, z0 = f[2] - 8, x1 = t[0] - 8, y1 = t[1] + yoff, z1 = t[2] - 8;
            float sx = x1 - x0, sy = y1 - y0, sz = z1 - z0;
            String axis = null;
            float angle = 0;
            float[] org = null;
            if (e.has("rotation")) {
                JsonObject ro = e.getAsJsonObject("rotation");
                axis = ro.get("axis").getAsString();
                angle = (float) Math.toRadians(ro.get("angle").getAsFloat());
                float[] o = vec(ro.getAsJsonArray("origin"));
                org = new float[]{o[0] - 8, o[1] + yoff, o[2] - 8};
            }
            JsonObject faces = e.getAsJsonObject("faces");
            float[][] origin = {{x0, y1, z1}, {x1, y1, z0}, {x0, y1, z0}, {x1, y1, z1}, {x0, y1, z0}, {x0, y0, z1}};
            float[][] du = {{sx, 0, 0}, {-sx, 0, 0}, {0, 0, sz}, {0, 0, -sz}, {sx, 0, 0}, {sx, 0, 0}};
            float[][] dv = {{0, -sy, 0}, {0, -sy, 0}, {0, -sy, 0}, {0, -sy, 0}, {0, 0, sz}, {0, 0, -sz}};
            float[][] nrm = {{0, 0, 1}, {0, 0, -1}, {-1, 0, 0}, {1, 0, 0}, {0, 1, 0}, {0, -1, 0}};
            List<Float> list = acc.computeIfAbsent(slot * 8 + bone, k -> new ArrayList<>());
            for (int fi = 0; fi < 6; fi++) {
                if (!faces.has(FACES[fi])) continue;
                JsonArray uvA = faces.getAsJsonObject(FACES[fi]).getAsJsonArray("uv");
                float u1 = uvA.get(0).getAsFloat() / 16f, v1 = uvA.get(1).getAsFloat() / 16f, u2 = uvA.get(2).getAsFloat() / 16f, v2 = uvA.get(3).getAsFloat() / 16f;
                float[] n = rot(nrm[fi], axis, angle, new float[]{0, 0, 0});
                int[][] corner = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
                for (int[] c : corner) {
                    float[] p = new float[3];
                    for (int k = 0; k < 3; k++) p[k] = origin[fi][k] + du[fi][k] * c[0] + dv[fi][k] * c[1];
                    p = rot(p, axis, angle, org);
                    // model frame (px): X = mx, Y = 24 - my, Z = -mz, relative to the bone pivot, then /16
                    float[] pv = PIVOT[bone];
                    list.add((p[0] - pv[0]) / 16f);
                    list.add((24 - p[1] - pv[1]) / 16f);
                    list.add((-p[2] - pv[2]) / 16f);
                    list.add(u1 + (u2 - u1) * c[0]);
                    list.add(v1 + (v2 - v1) * c[1]);
                    list.add(n[0]);
                    list.add(-n[1]);
                    list.add(-n[2]);
                }
            }
        }
        for (var en : acc.entrySet()) {
            float[] arr = new float[en.getValue().size()];
            for (int i = 0; i < arr.length; i++) arr[i] = en.getValue().get(i);
            out[en.getKey() / 8].add(new Group(tex, en.getKey() % 8, arr, cape));
        }
        return out;
    }

    private static float[] vec(JsonArray a) { return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()}; }

    /** single-axis right-handed rotation about origin o (same convention as the art generator's rotPt). */
    private static float[] rot(float[] p, String axis, float a, float[] o) {
        if (axis == null) return p.clone();
        float c = (float) Math.cos(a), s = (float) Math.sin(a), x = p[0] - o[0], y = p[1] - o[1], z = p[2] - o[2];
        return switch (axis) {
            case "x" -> new float[]{p[0], o[1] + y * c - z * s, o[2] + y * s + z * c};
            case "z" -> new float[]{o[0] + x * c - y * s, o[1] + x * s + y * c, p[2]};
            default -> new float[]{o[0] + x * c + z * s, p[1], o[2] - x * s + z * c};
        };
    }
}
