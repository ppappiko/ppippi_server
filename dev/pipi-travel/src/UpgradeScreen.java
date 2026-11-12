package dev.pipi.travel;

import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.lwjgl.glfw.GLFW;

/**
 * 장비 강화 screen. Flow: select gear -> 강화 하기 -> in-screen confirm popup -> 확인 sends the click, UpgradeFx runs the
 * charge / reveal animation on a frozen snapshot of the old data (the server answers at once and re-opens the screen),
 * then success shows a click-to-close result popup, failure returns to the screen with an animated blessing bar.
 */
final class UpgradeScreen extends ContainerScreen {
    static final String TITLE = "삐삐서버 · 장비 강화";
    private static final int W = 720, H = 370;
    private static final int FX = 368, FY = 72; // item frame centre
    private static final int TEXT = 0xffe6eef6, MUTED = 0xff9fb4ca, GOLD = 0xffe6c588, CYAN = 0xff81cece, GREEN = 0xff82d99a, RED = 0xffe87070;
    private float scale, left, top;
    private int beforeScroll, afterScroll;
    private boolean pending;

    UpgradeScreen(ChestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        UpgradeFx.screenOpened();
    }

    @Override protected void init() {
        scale = Math.max(.01f, Math.min((width - 18f) / W, (height - 18f) / H));
        left = (width - W * scale) / 2;
        top = (height - H * scale) / 2;
    }

    /** Slot content: frozen snapshot while the animation plays, live menu otherwise. */
    private ItemStack item(int slot) { return UpgradeFx.frozen() ? UpgradeFx.frozenItem(slot) : menu.getSlot(slot).getItem(); }
    private String name(int slot) { return ChatFormatting.stripFormatting(item(slot).getHoverName().getString()); }
    private static List<String> lore(ItemStack item) {
        return item.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().stream()
            .map(line -> ChatFormatting.stripFormatting(line.getString())).toList();
    }
    private boolean confirmOpen() { return !UpgradeFx.frozen() && !item(46).isEmpty(); }

    /** First explicit colour of the item name (grade colour). */
    private static int gradeColor(ItemStack item, int fallback) {
        int[] c = {-1};
        item.getHoverName().visit((style, s) -> {
            if (c[0] < 0 && style.getColor() != null && !s.isBlank()) c[0] = style.getColor().getValue();
            return Optional.empty();
        }, Style.EMPTY);
        return c[0] < 0 ? fallback : 0xff000000 | c[0];
    }

    private int[] values() {
        int[] values = new int[6];
        var lines = lore(item(25));
        if (!lines.isEmpty()) {
            String[] parts = lines.getFirst().split(",");
            if (parts.length == values.length) try {
                for (int i = 0; i < parts.length; i++) values[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException ignored) { return new int[6]; }
        }
        return values;
    }

    private static long parseLong(String s) {
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) if (Character.isDigit(ch)) b.append(ch);
        try { return b.isEmpty() ? -1 : Long.parseLong(b.toString()); } catch (NumberFormatException e) { return -1; }
    }
    private static String commas(long v) { return String.format(java.util.Locale.ROOT, "%,d", v); }

    @Override public void render(GuiGraphics g, int mx, int my, float delta) {
        UpgradeFx.update();
        UpgradeFx.seen();
        boolean fx = UpgradeFx.frozen();
        double x = (mx-left)/scale, y = (my-top)/scale;
        g.fill(0, 0, width, height, 0xb9081019);
        g.pose().pushPose();
        try {
            g.pose().translate(left, top, 0);
            g.pose().scale(scale, scale, 1);
            panel(g,0,0,210,H,false);
            panel(g,214,0,308,H,false);
            panel(g,526,0,194,H,false);
            heading(g,6,198,"보유 장비");
            heading(g,220,296,"강화");
            heading(g,532,182,"세부 능력치");
            for (int i=0; i<9; i++) {
                if (item(i).isEmpty()) continue;
                int row = 38+i*31;
                panel(g,7,row,196,29,(!fx && hit(x,y,7,row,196,29)) || item(i).equals(item(22)));
                g.renderItem(item(i),12,row+6);
                int slots = 0;
                long filled = 0;
                for (String line : lore(item(i))) {
                    if (line.startsWith("잠재 슬롯 ")) {
                        String number = line.substring("잠재 슬롯 ".length()).replace("개","");
                        try { slots = Integer.parseInt(number); } catch (NumberFormatException ignored) {}
                    }
                    if (AlchemyScreen.isGradeLine(line)) filled++;
                }
                for (int j=0;j<slots;j++) g.drawString(font,j<filled?"◆":"◇",34+j*9,row+4,j<filled?GREEN:0xff56708a,false);
                g.drawString(font,font.plainSubstrByWidth(name(i),165),34,row+16,gradeColor(item(i),TEXT),false);
            }
            if (!item(48).isEmpty()) button(g,8,338,82,24,"이전",x,y,!fx);
            if (!item(50).isEmpty()) button(g,120,338,82,24,"다음",x,y,!fx);
            button(g,537,338,172,24,"대장간으로",x,y,!fx);
            if (item(22).isEmpty()) {
                g.drawCenteredString(font,"강화할 장비를 선택하세요.",368,142,MUTED);
                g.drawCenteredString(font,"가방과 착용 중인 제작 장비가 표시됩니다.",368,161,MUTED);
                return;
            }
            int[] v = values();
            boolean max = v[0] >= v[5];
            int itemColor = gradeColor(item(22), GREEN);
            UpgradeFx.drawFrame(g, FX, FY, item(22), 0f);
            g.drawCenteredString(font,font.plainSubstrByWidth(name(22),288),368,122,itemColor);
            if (UpgradeFx.failShown()) g.drawCenteredString(font,"강화 실패",368,134,0xffb86464);
            g.drawCenteredString(font,max ? "최대 강화 단계" : v[0]+"강 → "+v[1]+"강",368,146,TEXT);
            g.drawCenteredString(font,max ? "강화 완료" : "강화 성공 확률 "+(v[2]/10)+"."+(v[2]%10)+"%",368,158,GOLD);
            // 대장장이의 축복 bar
            float gauge = UpgradeFx.gauge(v[3]);
            g.drawString(font,"대장장이의 축복",228,173,MUTED,false);
            String blessing = Math.round(gauge)+"% / 100%";
            g.drawString(font,blessing,508-font.width(blessing),173,CYAN,false);
            g.fill(227,185,509,193,0xff3d5369);
            g.fill(228,186,508,192,0xff080f17);
            int fill = Math.round(280*Math.clamp(gauge,0f,100f)/100f);
            if (fill > 0) {
                g.fill(228,186,228+fill,192,gauge >= 100f ? 0xffe9c26a : 0xffc9a25a);
                g.fill(228,186,228+fill,188,0x55ffffff);
            }
            // materials
            panel(g,226,198,284,62,false);
            int row = 0;
            for (int s = 36; s <= 38; s++) {
                if (item(s).isEmpty()) continue;
                int ry = 203 + row++ * 18;
                g.renderItem(item(s),232,ry);
                g.drawString(font,font.plainSubstrByWidth(name(s),190),253,ry+4,TEXT,false);
                var inputs = lore(item(s));
                if (inputs.isEmpty()) continue;
                String[] parts = inputs.getFirst().split("/");
                if (parts.length == 2) {
                    long have = parseLong(parts[0]), need = parseLong(parts[1]);
                    String needText = " / " + commas(Math.max(0, need)), haveText = commas(Math.max(0, have));
                    int nx = 504 - font.width(needText);
                    g.drawString(font,needText,nx,ry+4,TEXT,false);
                    g.drawString(font,haveText,nx-font.width(haveText),ry+4,have >= need ? TEXT : RED,false);
                } else g.drawString(font,inputs.getFirst(),504-font.width(inputs.getFirst()),ry+4,MUTED,false);
            }
            g.fill(226,266,510,267,0xff2a3b4d);
            // gold: "<amount> [coin]" right-aligned as one unit, owned amount on the left below
            g.drawString(font,"소모 골드",232,273,MUTED,false);
            String cost = name(43).replace(" 골드","");
            var money = lore(item(43));
            long owned = money.isEmpty() ? -1 : parseLong(money.getFirst());
            long costValue = parseLong(cost);
            if (costValue >= 0) cost = commas(costValue);
            int coinX = 508 - 16;
            g.renderItem(item(43), coinX, 268);
            g.drawString(font,cost,coinX - 4 - font.width(cost),273,owned >= 0 && costValue > owned ? RED : GOLD,false);
            if (!money.isEmpty()) g.drawString(font,money.getFirst(),232,287,MUTED,false);
            g.drawCenteredString(font,v[3]>=100 ? "축복 완성 · 다음 강화는 반드시 성공" :
                (v[0]>=10 ? "실패 시 1단계 하락 · 축복 +"+v[4]+"%p" : "실패 시 단계 유지 · 축복 +"+v[4]+"%p"),368,310,MUTED);
            if (!max) button(g,290,338,156,24,"강화 하기",x,y,!fx);
            drawStats(g,31,34,"강화 전 능력치",beforeScroll,null);
            // down arrow between the two boxes
            for (int k = 0; k < 7; k++) g.fill(623-7+k,176+k,623+7-k,177+k,0xff6f8bab);
            drawStats(g,32,194,"강화 후 능력치",afterScroll,lore(item(31)));
            if (confirmOpen()) {
                g.pose().translate(0,0,250);
                g.fill(0,0,W,H,0x99050a11);
                int px = 235, py = 130;
                panel(g,px,py,250,112,true);
                g.drawCenteredString(font,font.plainSubstrByWidth(name(22),230),360,py+20,itemColor);
                g.drawCenteredString(font,"장비를 강화하시겠습니까?",360,py+42,TEXT);
                button(g,px+30,py+72,88,26,"확인",x,y,true);
                button(g,px+132,py+72,88,26,"취소",x,y,true);
                g.pose().translate(0,0,-250);
            }
            if (fx) {
                g.pose().pushPose();
                g.pose().translate(0,0,200);
                float dim = UpgradeFx.dim();
                if (dim * .8f > .04f) g.fill(-6000,-6000,6000,6000,((int) (Math.min(1f,dim * .8f) * 255) << 24) | 0x04080e);
                g.pose().popPose();
                UpgradeFx.drawBehind(g, FX, FY);
                UpgradeFx.drawFrame(g, FX, FY, item(22), 210f);
                UpgradeFx.drawFront(g, FX, FY);
                ItemStack shown = menu.getSlot(22).getItem();
                if (shown.isEmpty()) shown = item(22);
                UpgradeFx.drawPopup(g, font, W / 2f, H / 2f, shown, name(22), itemColor);
            }
        } finally { g.pose().popPose(); }
        if (!fx && !confirmOpen()) {
            for (int i=0;i<9;i++) if (!item(i).isEmpty() && hit(x,y,7,38+i*31,196,29))
                g.renderTooltip(font,item(i),mx,my);
            if (!item(22).isEmpty() && hit(x,y,FX-48,FY-48,96,96)) g.renderTooltip(font,item(22),mx,my);
        }
    }

    private void drawStats(GuiGraphics g,int slot,int y,String title,int scroll,List<String> compare) {
        panel(g,535,y,176,134,false);
        g.drawCenteredString(font,title,623,y+9,GOLD);
        var lines = lore(item(slot));
        int start = Math.clamp(scroll,0,Math.max(0,lines.size()-8));
        for (int i=0;i<8 && start+i<lines.size();i++) {
            String line = lines.get(start+i);
            boolean changed = compare != null && (start+i >= compare.size() || !compare.get(start+i).equals(line));
            g.drawString(font,font.plainSubstrByWidth(line,161),542,y+26+i*12,changed ? GREEN : TEXT,false);
        }
        if (lines.size()>8) g.drawString(font,"휠로 능력치 이동",542,y+123,MUTED,false);
    }

    private static void panel(GuiGraphics g,int x,int y,int w,int h,boolean selected) {
        g.fill(x,y,x+w,y+h,selected?0xffa78c59:0xff3d5369);
        g.fillGradient(x+1,y+1,x+w-1,y+h-1,0xff1a2a3a,0xff0c1721);
    }
    private void heading(GuiGraphics g,int x,int w,String label) {
        g.fillGradient(x,5,x+w,28,0xff2d4054,0xff1c2a3b);
        g.drawString(font,label,x+6,13,TEXT,false);
    }
    private void button(GuiGraphics g,int x,int y,int w,int h,String label,double mx,double my,boolean enabled) {
        panel(g,x,y,w,h,enabled && hit(mx,my,x,y,w,h));
        g.drawCenteredString(font,label,x+w/2,y+(h-8)/2,enabled ? TEXT : 0xff6b7a8a);
    }
    private static boolean hit(double x,double y,int bx,int by,int w,int h) {return x>=bx&&y>=by&&x<bx+w&&y<by+h;}

    private void click(int slot) {
        if (item(slot).isEmpty() || minecraft==null || minecraft.getConnection()==null || pending) return;
        if (slot==46) {
            pending=true;
            int[] v = values();
            ItemStack[] snapshot = new ItemStack[menu.slots.size() < 54 ? menu.slots.size() : 54];
            for (int i=0;i<snapshot.length;i++) snapshot[i] = menu.getSlot(i).getItem().copy();
            UpgradeFx.begin(snapshot, v[3]);
        }
        minecraft.getConnection().send(new ServerboundContainerClickPacket(menu.containerId,menu.getStateId(),slot,0,
            ClickType.PICKUP,menu.getCarried().copy(),Int2ObjectMaps.emptyMap()));
    }
    @Override public boolean mouseClicked(double mx,double my,int button) {
        if (UpgradeFx.popupShowing()) { UpgradeFx.closePopup(); return true; }
        if (UpgradeFx.frozen()) return true;
        if (button!=0) return true;
        double x=(mx-left)/scale,y=(my-top)/scale;
        if (confirmOpen()) {
            if (hit(x,y,265,202,88,26)) click(46);
            else if (hit(x,y,367,202,88,26)) click(47);
            return true;
        }
        for(int i=0;i<9;i++) if(hit(x,y,7,38+i*31,196,29)){click(i);return true;}
        if(hit(x,y,8,338,82,24))click(48);
        else if(hit(x,y,120,338,82,24))click(50);
        else if(hit(x,y,537,338,172,24))click(49);
        else if(hit(x,y,290,338,156,24))click(45);
        return true;
    }
    @Override public boolean mouseScrolled(double mx,double my,double dx,double dy) {
        double x=(mx-left)/scale,y=(my-top)/scale;
        if(confirmOpen() || UpgradeFx.frozen())return true;
        int direction=dy>0?-1:dy<0?1:0;
        if(hit(x,y,7,38,196,279))click(direction<0?48:50);
        else if(hit(x,y,535,34,176,134))beforeScroll=Math.clamp(beforeScroll+direction,0,Math.max(0,lore(item(31)).size()-8));
        else if(hit(x,y,535,194,176,134))afterScroll=Math.clamp(afterScroll+direction,0,Math.max(0,lore(item(32)).size()-8));
        return true;
    }
    @Override public boolean keyPressed(int key,int scan,int mods) {
        if(key==GLFW.GLFW_KEY_ESCAPE) {
            if (UpgradeFx.popupShowing()) UpgradeFx.closePopup();
            else if (!UpgradeFx.frozen()) onClose();
        }
        return true;
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return true;}
    @Override public boolean mouseReleased(double x,double y,int button){return true;}
}
