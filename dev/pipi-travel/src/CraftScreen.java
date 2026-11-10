package dev.pipi.travel;

import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.lwjgl.glfw.GLFW;

final class CraftScreen extends ContainerScreen {
    static final String TITLE = "삐삐서버 · 기본 작업대";
    static final String LUMBER_TITLE = "삐삐서버 · 벌목장";
    static boolean isProductionTitle(String title) {
        return java.util.Set.of(LUMBER_TITLE, "삐삐서버 · 밀 농장", "삐삐서버 · 당근 농장", "삐삐서버 · 감자 농장", "삐삐서버 · 철광석 채석장", "삐삐서버 · 금광석 채석장", "삐삐서버 · 다이아 채석장").contains(title);
    }
    private final boolean lumber;
    private final String facilityTitle;
    private int draggedPet = -1;
    private static final int W = 720, H = 358, MAX_BATCH = 64;
    private static final int[] PETS = {9, 10, 18, 19, 27, 28, 36, 37, 45, 46, 47, 38};
    private static final int[] RECIPES = java.util.stream.IntStream.range(0,54).toArray();
    private static final int TEXT = 0xffe6eef6, MUTED = 0xff9cb1c7, CYAN = 0xff7cceca;
    private static final java.util.regex.Pattern INGREDIENT = java.util.regex.Pattern.compile("(.+) (\\d+)/(\\d+)");
    private float scale, left, top;
    private int recipe = -1, count = 1, recipeScroll;
    private boolean sliding;
    private long pendingUntil;

    CraftScreen(ChestMenu menu, Inventory inventory, Component title, CraftScreen previous) {
        super(menu, inventory, title);
        lumber = isProductionTitle(title.getString());
        facilityTitle = title.getString().replace("삐삐서버 · ", "");
        if (previous != null) {
            recipe = previous.recipe;
            count = previous.count;
            recipeScroll = previous.recipeScroll;
        }
    }

    @Override protected void init() {
        scale = Math.max(.01f, Math.min((width - 16f) / W, (height - 16f) / H));
        left = (width - W * scale) / 2;
        top = (height - H * scale) / 2;
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        int x = (int) ((mouseX - left) / scale), y = (int) ((mouseY - top) / scale);
        g.fill(0, 0, width, height, 0xbb070d15);
        g.pose().pushPose();
        try {
            g.pose().translate(left, top, 0);
            g.pose().scale(scale, scale, 1);
            panel(g, 0, 0, W, H, 0xff101a26, 0xff466078);
            g.fillGradient(1, 1, W - 1, 39, 0xff294155, 0xff162535);
            g.drawString(font, "삐삐서버   /   생활 기술   /   기본 작업대", 16, 15, CYAN, false);
            button(g, 681, 8, 24, 23, "×", x, y, true);
            section(g, 12, 214, "펫 창고");
            section(g, 234, 333, facilityTitle);
            section(g, 575, 133, "펫 정보");
            drawPets(g, x, y);
            g.pose().pushPose(); g.pose().translate(40,0,0); if (lumber) drawLumber(g, (int)inputX(x), y); else drawCraft(g, (int)inputX(x), y); g.pose().popPose();
            drawPetInfo(g,x,y);
            if (draggedPet >= 0) {
                g.pose().translate(0, 0, 350);
                icon(g, item(draggedPet), x - 8, y - 8, 1);
            }
            if (!menu.getCarried().isEmpty()) {
                g.pose().translate(0, 0, 400);
                icon(g, menu.getCarried(), x - 8, y - 8, 1);
                g.renderItemDecorations(font, menu.getCarried(), x - 8, y - 8);
            }
        } finally {
            g.pose().popPose();
        }
    }

    private void section(GuiGraphics g, int x, int w, String title) {
        panel(g, x, 48, w, 298, 0xff121f2d, 0xff344b61);
        g.fillGradient(x + 1, 49, x + w - 1, 76, 0xff253a4c, 0xff1b2c3d);
        g.drawString(font, title, x + 10, 58, TEXT, false);
    }

    private void drawPets(GuiGraphics g, int x, int y) {
        int owned = 0;
        for (int slot : PETS) if (item(slot).getItem() instanceof net.minecraft.world.item.SpawnEggItem) owned++;
        int[] pages = craftPages();
        g.drawString(font, "보유 펫  " + pages[2], 23, 85, MUTED, false);
        if (pages[1] > 1) g.drawString(font, "Q " + pages[0] + "/" + pages[1] + " E", 112, 85, CYAN, false);
        for (int i = 0; i < PETS.length; i++) {
            int px = 23 + i % 4 * 51, py = 103 + i / 4 * 74;
            ItemStack pet = item(PETS[i]);
            if (!(pet.getItem() instanceof net.minecraft.world.item.SpawnEggItem)) pet = ItemStack.EMPTY;
            boolean selected = !pet.isEmpty() && name(pet).startsWith("▶");
            panel(g, px, py, 46, 52, hit(x,y,px,py,46,52) ? 0xff264259 : 0xff101b28,
                selected ? CYAN : 0xff30465a);
            if (!pet.isEmpty()) {
                icon(g, pet, px + 12, py + 5, 1.35f);
                boolean mismatch = lore(pet).contains("적성 불일치");
                if (mismatch) { g.pose().pushPose(); g.pose().translate(0, 0, 250); g.fill(px + 1, py + 1, px + 45, py + 34, 0xbb080f19); g.pose().popPose(); }
                drawAssignment(g,pet,px+16,py+55);
                g.drawCenteredString(font, font.plainSubstrByWidth(name(pet).replace("▶ ", ""), 42), px + 23, py + 35, mismatch ? MUTED : TEXT);
            }
        }
        g.drawString(font, "클릭: 상세 · 드래그: 배치 · 우클릭: 배치 해제", 23, 338, MUTED, false);
    }

    private int[] craftPages() {
        var lines = lore(item(0));
        if (!lines.isEmpty()) {
            String[] values = lines.getFirst().split(",");
            if (values.length == 3) try { return new int[]{Integer.parseInt(values[0]), Integer.parseInt(values[1]), Integer.parseInt(values[2])}; }
            catch (NumberFormatException ignored) {}
        }
        return new int[]{1, 1, 0};
    }

    private void drawLumber(GuiGraphics g, int x, int y) {
        String level = name(item(4));
        g.drawString(font, level, 391, 58, CYAN, false);
        button(g, 435, 53, 81, 19, level.equals("Lv. 3") ? "최대 레벨" : "레벨업", x, y, !level.equals("Lv. 3"));
        if (!level.equals("Lv. 3")) {
            int costY = 76;
            for (String costLine : lore(item(4))) {
                if (costY > 94) break;
                g.drawString(font, costLine, 391, costY, MUTED, false);
                costY += 10;
            }
        }
        for (int i = 0; i < 5; i++) {
            int px = 212 + i * 61;
            ItemStack pet = item(11 + i);
            panel(g, px, 105, 54, 58, 0xff0f1d2a, hit(x,y,px,105,54,58) ? CYAN : 0xff3b556b);
            if (!pet.isEmpty()) {
                icon(g, pet, px + 16, 112, 1.35f);
                
                g.drawCenteredString(font, font.plainSubstrByWidth(name(pet), 48), px + 27, 144, TEXT);
            } else g.drawCenteredString(font, "+", px + 27, 128, MUTED);
        }
        panel(g, 205, 179, 311, 103, 0xff0d1925, 0xff344e64);
        long[] progress = lumberProgress();
        panel(g, 334, 186, 54, 49, 0xff111f2e, progress[0] > 0 ? CYAN : 0xff385166);
        if (progress[0] > 0) {
            icon(g, item(34), 345, 193, 2);
            g.drawString(font, Long.toString(progress[0]), 371, 220, TEXT);
        }
        long clock = progress[3] > 0 ? progress[3] : System.currentTimeMillis();
        int filled = progress[2] == 0 ? 0 : (int) (250 * Math.clamp(clock - progress[1], 0, progress[2]) / progress[2]);
        g.fill(236, 247, 486, 253, 0xff344e64);
        if (filled > 0) g.fill(236, 247, 236 + filled, 253, CYAN);
        g.drawCenteredString(font, progress[3] > 0 ? "작업 대기 중" : progress[2] == 0 ? "작업 대기" : "다음 생산품 준비 중", 361, 262, MUTED);
        g.drawCenteredString(font, "Shift+좌클릭으로 완성품 수령", 361, 287, MUTED);
        button(g, 282, 311, 158, 24, name(item(32)), x, y, !item(32).isEmpty());
    }

    private long[] lumberProgress() {
        long[] values = new long[4];
        var lines = lore(item(34));
        if (!lines.isEmpty()) {
            String[] fields = lines.getFirst().split(",");
            if (fields.length == values.length) try {
                for (int i = 0; i < values.length; i++) values[i] = Long.parseLong(fields[i]);
            } catch (NumberFormatException ignored) { return new long[4]; }
        }
        return values;
    }

    private void drawCraft(GuiGraphics g, int x, int y) {
        ItemStack worker = item(13);
        boolean assigned = !worker.isEmpty() && !worker.is(Items.BARRIER);
        g.fillGradient(202, 79, 519, 125, 0xff203549, 0xff142232);
        panel(g, 206, 82, 31, 40, 0xff142232, hit(x,y,206,82,31,40) ? CYAN : 0xff405a70);
        if (assigned) icon(g, worker, 210, 89, 1.4f);
        else g.drawCenteredString(font, "+", 221, 99, MUTED);
        g.drawString(font, assigned ? name(worker) : "배치된 펫 없음", 239, 89, TEXT, false);
        g.drawString(font, assigned ? String.join("   ·   ", lore(worker)) : "창고에서 가공 적성이 있는 펫을 선택하세요.", 239, 105, MUTED, false);
        if (!item(34).isEmpty()) {
            drawProgress(g, item(34), x, y);
            return;
        }
        if (recipe < 0) {
            panel(g, 205, 133, 311, 167, 0xff0d1925, 0xff2d455a);
            var choices = availableRecipes();
            int rows = (choices.size() + 1) / 2;
            recipeScroll = Math.clamp(recipeScroll, 0, Math.max(0, rows - 4));
            for (int visible = 0; visible < 8; visible++) {
                int index = recipeScroll * 2 + visible;
                if (index >= choices.size()) break;
                int px = 209 + visible % 2 * 151, py = 137 + visible / 2 * 39;
                ItemStack option = item(RECIPES[choices.get(index)]);
                panel(g, px, py, 147, 36, 0xff152537,
                    hit(x,y,px,py,147,36) ? CYAN : 0xff385166);
                icon(g, option, px + 4, py + 7, 1.3f);
                g.drawString(font, font.plainSubstrByWidth(name(option), 112), px + 29, py + 14, TEXT, false);
            }
            if (rows > 4) {
                g.fill(511, 137, 513, 293, 0xff344b61);
                int thumb = 137 + recipeScroll * 116 / (rows - 4);
                g.fill(511, thumb, 513, thumb + 40, CYAN);
            }
            g.drawCenteredString(font, "제작 목록 · 휠 스크롤로 아이템 선택", 361, 317, MUTED);
            return;
        }
        button(g, 205, 133, 311, 24, "다른 제작 아이템 선택", x,y,true);        ItemStack target = item(RECIPES[recipe]);
        panel(g, 205, 165, 311, 66, 0xff0d1925, 0xff2d455a);
        if (!target.isEmpty()) {
            icon(g, target, 213, 175, 1.4f);
            g.drawString(font, name(target), 242, 175, TEXT, false);
            java.util.List<java.util.regex.Matcher> ingredients = new java.util.ArrayList<>();
            java.util.List<String> others = new java.util.ArrayList<>();
            for (String line : lore(target)) {
                var ingredient = INGREDIENT.matcher(line);
                if (ingredient.matches()) ingredients.add(ingredient); else others.add(line);
            }
            boolean twoColumns = ingredients.size() > 3;
            int lineY = 187;
            for (int i = 0; i < ingredients.size(); i++) {
                var ingredient = ingredients.get(i);
                int have = Integer.parseInt(ingredient.group(2));
                int need = Integer.parseInt(ingredient.group(3)) * count;
                int column = twoColumns ? i % 2 : 0;
                g.drawString(font, font.plainSubstrByWidth(ingredient.group(1) + "  " + have + " / " + need, twoColumns ? 135 : 260),
                    242 + column * 140, lineY, have >= need ? CYAN : 0xffffa59a, false);
                if (!twoColumns || column == 1 || i == ingredients.size() - 1) lineY += 11;
            }
            for (String line : others) {
                if (line.startsWith("제작 시간 ")) continue;
                g.drawString(font, line, 242, lineY, MUTED, false);
                lineY += 11;
            }
        }
        button(g, 213, 239, 27, 22, "<<", x,y,true);
        button(g, 244, 239, 23, 22, "<", x,y,true);
        button(g, 455, 239, 23, 22, ">", x,y,true);
        button(g, 482, 239, 27, 22, ">>", x,y,true);
        g.drawCenteredString(font, "제작 수량  " + count, 361, 245, TEXT);
        g.fill(223, 274, 500, 276, 0xff466074);
        int knob = 223 + (count - 1) * 276 / (MAX_BATCH - 1);
        g.fill(223, 274, knob, 276, CYAN);
        g.fill(knob - 4, 270, knob + 5, 280, CYAN);
        int level = workerLevel(worker);
        long perItem = 0;
        for (String line : lore(target)) if (line.startsWith("제작 시간 ")) {
            try { perItem = Long.parseLong(line.substring("제작 시간 ".length()).replace("초", "").trim()); } catch (NumberFormatException ignored) {}
        }
        String timing = level == 0 || perItem <= 0 ? "제작 예상 시간  —" : "제작 예상 시간  " + duration(perItem * count);
        g.drawCenteredString(font, timing, 361, 291, CYAN);
        button(g, 205, 311, 150, 24, "다시 선택", x,y,assigned);
        button(g, 362, 311, 154, 24, "제작", x,y,assigned && !target.isEmpty());
    }

    private void drawPetInfo(GuiGraphics g, int x, int y) {
        ItemStack pet=item(16);
        if(pet.isEmpty()||pet.is(Items.BARRIER)){wrapped(g,"펫을 선택하세요.",587,91,109,MUTED,3);return;}
        panel(g,616,91,50,50,0xff0d1926,0xff45637b);
        icon(g,pet,625,100,2);
        int lineY=152;
        for(var text:font.split(Component.literal(name(pet)),109)){g.drawCenteredString(font,text,641,lineY,TEXT);lineY+=12;}
        lineY=Math.max(176,lineY+8);
        for(String text:lore(pet)){
            for(var row:font.split(Component.literal(text),109)){if(lineY>265)break;g.drawString(font,row,587,lineY,MUTED,false);lineY+=12;}
        }
        if(!item(25).isEmpty())wrapped(g,name(item(25)),587,278,109,CYAN,1);
        if(!lumber&&item(34).isEmpty()){
            button(g,587,292,109,18,"먹이 넣기",x,y,true);
            if(menu.getCarried().isEmpty())button(g,587,315,109,20,"제작대에 배치",x,y,!item(21).isEmpty());
        }
    }
    private void wrapped(GuiGraphics g,String text,int x,int y,int width,int color,int maxLines){int n=0;for(var line:font.split(Component.literal(text),width)){if(n++>=maxLines)break;g.drawString(font,line,x,y,color,false);y+=12;}}

    private void drawBag(GuiGraphics g) {}
    private void drawAssignment(GuiGraphics g,ItemStack pet,int x,int y){String status=String.join(" ",lore(pet));String task=null;if(status.contains("배치 중")||status.contains("작업 대기 중")){task=status.contains("농장")?"farm":status.contains("채석장")?"mine":status.contains("벌목")?"lumber":status.contains("기본 작업대")?"craft":status.contains("운반")?"transport":null;}if(task!=null){g.pose().pushPose();g.pose().translate(0,0,260);g.blit(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("pipitravel","textures/gui/task_"+task+".png"),x,y,0,0,14,14,14,14);g.pose().popPose();}}
    private static double inputX(double x){if(x>=234&&x<=567)return x-40;return x;}

    private static int workerLevel(ItemStack item) {
        for (String line : lore(item)) if (line.startsWith("제작 적성 Lv.")) {
            try { return Math.clamp(Integer.parseInt(line.substring("제작 적성 Lv.".length())), 1, 3); }
            catch (NumberFormatException ignored) { return 0; }
        }
        return 0;
    }

    private record Progress(long ready, int total, long interval, int claimed, long paused) {
        int completed(long now) {
            if (paused > 0) now = paused;
            if (now >= ready) return total;
            return interval == 0 ? 0 : (int) Math.clamp(total - 1L - (ready - now - 1) / interval, 0, total);
        }
    }

    private static Progress progress(ItemStack job) {
        var lines = lore(job);
        if (lines.isEmpty()) return new Progress(Long.MAX_VALUE, 1, 0, 0, 0);
        String[] fields = lines.getFirst().split(",");
        try {
            return fields.length >= 4 ? new Progress(Long.parseLong(fields[0]), Integer.parseInt(fields[1]),
                Long.parseLong(fields[2]), Integer.parseInt(fields[3]), fields.length == 5 ? Long.parseLong(fields[4]) : 0) : new Progress(Long.parseLong(fields[0]), 1, 0, 0, 0);
        } catch (NumberFormatException ignored) { return new Progress(Long.MAX_VALUE, 1, 0, 0, 0); }
    }

    private void drawProgress(GuiGraphics g, ItemStack job, int x, int y) {
        Progress progress = progress(job);
        long now = System.currentTimeMillis();
        if (progress.paused() > 0) now = progress.paused();
        int complete = progress.completed(now), available = Math.max(0, complete - progress.claimed());
        panel(g, 205, 137, 311, 158, 0xff0d1925, 0xff344e64);
        g.drawString(font, "제작 진행", 218, 149, CYAN, false);
        panel(g, 332, 164, 58, 53, 0xff152537, available > 0 ? CYAN : 0xff385166);
        if (available > 0) {
            icon(g, job, 343, 173, 2.2f);
            g.drawString(font, Integer.toString(available * (job.is(Items.OAK_PLANKS) ? 4 : 1)), 370, 202, TEXT);
        }
        g.drawCenteredString(font, name(job), 361, 227, TEXT);
        g.drawCenteredString(font, complete + "/" + progress.total() + "회 완료   ·   " + available + "회 수령 가능", 361, 246, CYAN);
        g.fill(222, 266, 499, 270, 0xff364d61);
        long next = progress.ready() - Math.max(0, progress.total() - complete - 1L) * progress.interval();
        long cycle = progress.interval();
        int filled = complete == progress.total() ? 277
            : cycle <= 0 ? 0 : (int) (277 * Math.clamp(now - (next - cycle), 0, cycle) / cycle);
        if (filled > 0) g.fill(222, 266, 222 + filled, 270, CYAN);
        if (complete < progress.total()) g.drawCenteredString(font, "다음 완성까지 " + duration(Math.max(0, (next - now + 999) / 1000)), 361, 278, MUTED);
        g.drawCenteredString(font, "Shift+좌클릭으로 완성품 수령", 361, 304, MUTED);
        if (complete < progress.total()) button(g, 284, 319, 154, 20, progress.paused() > 0 ? "작업 개시" : "작업 중단", x, y, true);
    }

    private static String duration(long seconds) {
        return seconds >= 60 ? (seconds / 60) + "분 " + (seconds % 60) + "초" : seconds + "초";
    }

    private ItemStack item(int slot) { return menu.getSlot(slot).getItem(); }
    private static String name(ItemStack item) { return ChatFormatting.stripFormatting(item.getHoverName().getString()); }
    private static java.util.List<String> lore(ItemStack item) {
        return item.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().stream()
            .map(line -> ChatFormatting.stripFormatting(line.getString())).toList();
    }

    private static void icon(GuiGraphics g, ItemStack item, int x, int y, float scale) {
        g.pose().pushPose();
        try { g.pose().translate(x,y,0); g.pose().scale(scale,scale,1); g.renderItem(item,0,0); }
        finally { g.pose().popPose(); }
    }
    private static void panel(GuiGraphics g,int x,int y,int w,int h,int fill,int border) {
        g.fill(x,y,x+w,y+h,border); g.fill(x+1,y+1,x+w-1,y+h-1,fill);
    }
    private void button(GuiGraphics g,int x,int y,int w,int h,String label,int mx,int my,boolean enabled) {
        panel(g,x,y,w,h,enabled && hit(mx,my,x,y,w,h) ? 0xff34586a : 0xff1b3043,enabled ? 0xff507287 : 0xff324353);
        g.drawCenteredString(font,label,x+w/2,y+(h-8)/2,enabled ? TEXT : MUTED);
    }
    private static boolean hit(double x,double y,int bx,int by,int w,int h) {
        return x>=bx && y>=by && x<bx+w && y<by+h;
    }

    @Override public boolean mouseClicked(double sx, double sy, int button) {
        double rawX=(sx-left)/scale;double x=inputX(rawX), y=(sy-top)/scale; if(button==0 && hit(rawX,y,681,8,24,23)){onClose();return true;}
        if (button == 1 && lumber) {
            for (int i=0;i<5;i++) if (hit(x,y,212+i*61,105,54,58)) command("remove " + (i+1));
            return true;
        }
        if (button == 1 && !lumber && item(34).isEmpty()) {
            ItemStack placed = item(13);
            if (!placed.isEmpty() && !placed.is(Items.BARRIER)) {
                boolean onHeader = hit(x,y,202,79,317,46);
                boolean onPlacedPet = false;
                for (int i=0;i<PETS.length;i++)
                    if (hit(x,y,23+i%4*51,103+i/4*74,46,52) && item(PETS[i]).getItem() instanceof net.minecraft.world.item.SpawnEggItem) {
                        String status = String.join(" ", lore(item(PETS[i])));
                        if ((status.contains("배치 중") || status.contains("작업 대기 중")) && status.contains("기본 작업대")) onPlacedPet = true;
                    }
                if (onHeader || onPlacedPet) command("release");
            }
            return true;
        }
        if (button != 0) return true;
        if (hit(x,y,681,8,24,23)) { onClose(); return true; }
        for (int i=0;i<PETS.length;i++) if (hit(x,y,23+i%4*51,103+i/4*74,46,52) && item(PETS[i]).getItem() instanceof net.minecraft.world.item.SpawnEggItem) {
            draggedPet=PETS[i];
            return true;
        }
        if (lumber) {
            if (hit(x,y,435,53,81,19)) command("upgrade");
            else if (hit(x,y,282,311,158,24)) command("toggle");
            else if (hit(x,y,334,186,54,49) && lumberProgress()[0] > 0)
                { if (hasShiftDown()) sendSlot(34, ClickType.QUICK_MOVE); }

            return true;
        }
        if (!item(34).isEmpty()) {
            Progress progress = progress(item(34));
            if (hit(x,y,284,319,154,20) && progress.completed(System.currentTimeMillis()) < progress.total()) { command("toggle"); return true; }
            if (hit(x,y,332,164,58,53)
                 ) {
                if (hasShiftDown()) command("claim");
                return true;
            }
            for (int i=0;i<PETS.length;i++)
                if (hit(x,y,23+i%4*51,103+i/4*74,46,52)) { click(PETS[i]); break; }
            return true;
        }
        if (recipe < 0) {
            var choices = availableRecipes();
            for (int visible = 0; visible < 8; visible++) {
                int index = recipeScroll * 2 + visible;
                if (index >= choices.size()) break;
                if (hit(x,y,209+visible%2*151,137+visible/2*39,147,36)) {
                    recipe = choices.get(index);
                    count = 1;
                    return true;
                }
            }
            if (hit(x,y,587,315,109,20) && !item(21).isEmpty()) command("assign");
            else if (hit(x,y,587,292,109,18)) command("feed");
            return true;
        }
        if (hit(x,y,205,133,311,24)) { recipe = -1; return true; }        if (hit(x,y,213,266,296,18)) { sliding=true; slider(x); return true; }
        if (hit(x,y,213,239,27,22)) change(-10);
        else if (hit(x,y,244,239,23,22)) change(-1);
        else if (hit(x,y,455,239,23,22)) change(1);
        else if (hit(x,y,482,239,27,22)) change(10);
        else if (hit(x,y,205,311,150,24) && item(34).isEmpty()) command("release");
        else if (hit(x,y,587,315,109,20) && item(34).isEmpty() && !item(21).isEmpty()) command("assign");
        else if (hit(x,y,587,292,109,18) && item(34).isEmpty()) command("feed");
        else if (hit(x,y,362,311,154,24)) {
            if (item(34).isEmpty()) {
                if (workerLevel(item(13)) > 0 && !item(RECIPES[recipe]).isEmpty() && request())
                    minecraft.getConnection().sendCommand("pipilife process batch " + RECIPES[recipe] + " " + count);
            }
        } else {
            for (int i=0;i<PETS.length;i++)
                if (hit(x,y,23+i%4*51,103+i/4*74,46,52)) { click(PETS[i]); return true; }

        }
        return true;
    }
    private void change(int amount) { count=Math.clamp(count+amount,1,MAX_BATCH); }
    private void slider(double x) { count=1+(int)Math.round(Math.clamp((x-223)/276,0,1)*(MAX_BATCH-1)); }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy) {
        if (button==0 && sliding) slider(inputX((x-left)/scale));
        return true;
    }
    @Override public boolean mouseReleased(double x,double y,int button) {
        sliding=false;
        if (button != 0 || draggedPet < 0) return true;
        int source=draggedPet;
        draggedPet=-1;
        double mx=inputX((x-left)/scale), my=(y-top)/scale;
        if (lumber) {
            for (int i=0;i<5;i++) if (hit(mx,my,212+i*61,105,54,58)) {
                command("assign " + source + " " + (i+1));
                return true;
            }
        } else if (hit(mx,my,206,82,31,40) && item(34).isEmpty()) {
            command("assign " + source + " 1");
            return true;
        }
        for (int i=0;i<PETS.length;i++) if (PETS[i]==source && hit(mx,my,23+i%4*51,103+i/4*74,46,52)) { command("select " + source); }
        return true;
    }

    private void command(String action) {
        if (request()) minecraft.getConnection().sendCommand("pipilife workstation " + action);
    }
    private java.util.List<Integer> availableRecipes() {
        var choices = new java.util.ArrayList<Integer>();
        for (int i = 0; i < RECIPES.length; i++) if (item(RECIPES[i]).getOrDefault(DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getCompound("PublicBukkitValues").contains("pipilife:craft_recipe")) choices.add(i);
        return choices;
    }

    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {
        double mx = inputX((x-left)/scale), my = (y-top)/scale;
        if (!lumber && recipe < 0 && item(34).isEmpty() && hit(mx,my,205,133,311,167)) {
            int max = Math.max(0, (availableRecipes().size()+1)/2 - 4);
            recipeScroll = Math.clamp(recipeScroll - (int)Math.signum(vertical), 0, max);
        }
        return true;
    }
    @Override public boolean keyPressed(int key,int scan,int mods) {
        if (key==GLFW.GLFW_KEY_ESCAPE) onClose();
        else if (key==GLFW.GLFW_KEY_Q || key==GLFW.GLFW_KEY_E) {
            int[] pages=craftPages();
            int next=pages[0]+(key==GLFW.GLFW_KEY_Q?-1:1);
            if(next>=1 && next<=pages[1]) command("page "+(next-1));
        }
        return true;
    }
    private boolean request() {
        if (minecraft==null || minecraft.getConnection()==null || System.currentTimeMillis()<pendingUntil) return false;
        pendingUntil=System.currentTimeMillis()+1000;
        return true;
    }
    private void click(int slot) {
        if (!item(slot).isEmpty()) sendSlot(slot, ClickType.PICKUP);
    }
    private void sendSlot(int slot, ClickType type) {
        if (request()) minecraft.getConnection().send(new ServerboundContainerClickPacket(
            menu.containerId,menu.getStateId(),slot,0,type,menu.getCarried().copy(),Int2ObjectMaps.emptyMap()));
    }
}


