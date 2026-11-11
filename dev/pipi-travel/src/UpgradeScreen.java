package dev.pipi.travel;

import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import java.util.List;
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
import net.minecraft.world.item.component.ItemLore;
import org.lwjgl.glfw.GLFW;

final class UpgradeScreen extends ContainerScreen {
    static final String TITLE = "삐삐서버 · 장비 강화";
    private static final int W = 720, H = 370;
    private static final int TEXT = 0xffe6eef6, MUTED = 0xff9fb4ca, GOLD = 0xffe6c588, CYAN = 0xff81cece;
    private float scale, left, top;
    private int beforeScroll, afterScroll;
    private boolean pending;

    UpgradeScreen(ChestMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    @Override protected void init() {
        scale = Math.max(.01f, Math.min((width - 18f) / W, (height - 18f) / H));
        left = (width - W * scale) / 2;
        top = (height - H * scale) / 2;
    }

    private ItemStack item(int slot) { return menu.getSlot(slot).getItem(); }
    private String name(int slot) { return ChatFormatting.stripFormatting(item(slot).getHoverName().getString()); }
    private static List<String> lore(ItemStack item) {
        return item.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().stream()
            .map(line -> ChatFormatting.stripFormatting(line.getString())).toList();
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

    @Override public void render(GuiGraphics g, int mx, int my, float delta) {
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
                panel(g,7,row,196,29,hit(x,y,7,row,196,29) || item(i).equals(item(22)));
                g.renderItem(item(i),12,row+6);
                g.drawString(font,font.plainSubstrByWidth(name(i),165),34,row+15,TEXT,false);
                int slots = 0;
                long filled = 0;
                for (String line : lore(item(i))) {
                    if (line.startsWith("잠재 슬롯 ")) {
                        String number = line.substring("잠재 슬롯 ".length()).replace("개","");
                        try { slots = Integer.parseInt(number); } catch (NumberFormatException ignored) {}
                    }
                    if (AlchemyScreen.isGradeLine(line)) filled++;
                }
                for (int j=0;j<slots;j++) g.drawString(font,j<filled?"◆":"◇",34+j*9,row+3,j<filled?0xff82d99a:MUTED,false);
            }
            if (!item(48).isEmpty()) button(g,8,338,82,24,"이전",x,y);
            if (!item(50).isEmpty()) button(g,120,338,82,24,"다음",x,y);
            button(g,537,338,172,24,"대장간으로",x,y);
            if (item(22).isEmpty()) {
                g.drawCenteredString(font,"강화할 장비를 선택하세요.",368,142,MUTED);
                g.drawCenteredString(font,"가방과 착용 중인 제작 장비가 표시됩니다.",368,161,MUTED);
                return;
            }
            int[] v = values();
            boolean max = v[0] >= v[5];
            panel(g,337,43,62,61,true);
            icon(g,item(22),346,51,2.8f);
            g.drawCenteredString(font,font.plainSubstrByWidth(name(22),288),368,116,TEXT);
            g.drawCenteredString(font,max ? "최대 강화 단계" : v[0]+"강 → "+v[1]+"강",368,135,GOLD);
            g.drawCenteredString(font,max ? "강화 완료" : "강화 성공 확률 "+(v[2]/10)+"."+(v[2]%10)+"%",368,153,GOLD);
            g.drawString(font,"대장장이의 축복",228,173,MUTED,false);
            String blessing = v[3]+" / 100%";
            g.drawString(font,blessing,508-font.width(blessing),173,CYAN,false);
            g.fill(228,187,508,193,0xff080f17);
            g.fill(228,187,228+280*Math.clamp(v[3],0,100)/100,193,GOLD);
            panel(g,226,203,284,57,false);
            int materials = 0;
            for (int s = 36; s <= 38; s++) if (!item(s).isEmpty()) materials++;
            int columnWidth = materials >= 3 ? 94 : 146;
            for (int s = 36, column = 0; s <= 38; s++) {
                if (item(s).isEmpty()) continue;
                int mx0 = 234 + column++ * columnWidth;
                g.renderItem(item(s),mx0,212);
                g.drawString(font,font.plainSubstrByWidth(name(s),columnWidth-27),mx0+21,211,TEXT,false);
                var inputs = lore(item(s));
                if (!inputs.isEmpty()) g.drawString(font,inputs.getFirst(),mx0+21,230,MUTED,false);
            }
            g.renderItem(item(43), 493, 267);
            g.drawString(font,"소모 골드",230,272,MUTED,false);
            g.drawString(font,name(43),505-font.width(name(43)),272,GOLD,false);
            var money = lore(item(43));
            if (!money.isEmpty()) g.drawString(font,money.getFirst(),230,288,MUTED,false);
            if (!item(42).isEmpty()) {
                String result = name(42).split(" · 대장장이")[0];
                g.drawCenteredString(font,result,368,307,GOLD);
            } else g.drawCenteredString(font,v[3]>=100 ? "축복 완성 · 다음 강화는 반드시 성공" :
                (v[0]>=10 ? "실패 시 1단계 하락 · 축복 +"+v[4]+"%p" : "실패 시 단계 유지 · 축복 +"+v[4]+"%p"),368,307,MUTED);
            if (!max) button(g,290,338,156,24,"강화하기",x,y);
            drawStats(g,31,43,"강화 전 능력치",beforeScroll);
            drawStats(g,32,194,"강화 후 능력치",afterScroll);
            if (!item(46).isEmpty()) {
                g.pose().translate(0,0,250);
                g.fill(0,0,W,H,0xbb050a11);
                panel(g,203,120,314,134,true);
                g.drawCenteredString(font,"장비를 강화하시겠습니까?",360,138,GOLD);
                g.drawCenteredString(font,"재료와 "+name(43)+"가 소모됩니다.",360,160,TEXT);
                g.drawCenteredString(font,v[0]>=10 ? "실패하면 강화 단계가 1단계 하락합니다." : "실패해도 강화 단계는 유지됩니다.",360,179,MUTED);
                button(g,226,211,119,26,"확인",x,y);
                button(g,374,211,119,26,"취소",x,y);
            }
        } finally { g.pose().popPose(); }
        if (item(46).isEmpty()) {
            for (int i=0;i<9;i++) if (!item(i).isEmpty() && hit(x,y,7,38+i*31,196,29))
                g.renderTooltip(font,item(i),mx,my);
            if (!item(22).isEmpty() && hit(x,y,337,43,62,61)) g.renderTooltip(font,item(22),mx,my);
        }
    }

    private void drawStats(GuiGraphics g,int slot,int y,String title,int scroll) {
        panel(g,535,y,176,138,false);
        g.drawCenteredString(font,title,623,y+9,GOLD);
        var lines = lore(item(slot));
        int start = Math.clamp(scroll,0,Math.max(0,lines.size()-8));
        for (int i=0;i<8 && start+i<lines.size();i++)
            g.drawString(font,font.plainSubstrByWidth(lines.get(start+i),161),542,y+27+i*12,TEXT,false);
        if (lines.size()>8) g.drawString(font,"휠로 능력치 이동",542,y+126,MUTED,false);
    }

    private static void panel(GuiGraphics g,int x,int y,int w,int h,boolean selected) {
        g.fill(x,y,x+w,y+h,selected?0xffa78c59:0xff3d5369);
        g.fillGradient(x+1,y+1,x+w-1,y+h-1,0xff1a2a3a,0xff0c1721);
    }
    private void heading(GuiGraphics g,int x,int w,String label) {
        g.fillGradient(x,5,x+w,28,0xff2d4054,0xff1c2a3b);
        g.drawString(font,label,x+6,13,TEXT,false);
    }
    private void button(GuiGraphics g,int x,int y,int w,int h,String label,double mx,double my) {
        panel(g,x,y,w,h,hit(mx,my,x,y,w,h));
        g.drawCenteredString(font,label,x+w/2,y+(h-8)/2,TEXT);
    }
    private static void icon(GuiGraphics g,ItemStack item,int x,int y,float size) {
        g.pose().pushPose();
        try {g.pose().translate(x,y,0);g.pose().scale(size,size,1);g.renderItem(item,0,0);}
        finally {g.pose().popPose();}
    }
    private static boolean hit(double x,double y,int bx,int by,int w,int h) {return x>=bx&&y>=by&&x<bx+w&&y<by+h;}
    private void click(int slot) {
        if (item(slot).isEmpty() || minecraft==null || minecraft.getConnection()==null || pending) return;
        if (slot==46) pending=true;
        minecraft.getConnection().send(new ServerboundContainerClickPacket(menu.containerId,menu.getStateId(),slot,0,
            ClickType.PICKUP,menu.getCarried().copy(),Int2ObjectMaps.emptyMap()));
    }
    @Override public boolean mouseClicked(double mx,double my,int button) {
        if (button!=0) return true;
        double x=(mx-left)/scale,y=(my-top)/scale;
        if (!item(46).isEmpty()) {
            if (hit(x,y,226,211,119,26)) click(46);
            else if (hit(x,y,374,211,119,26)) click(47);
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
        if(!item(46).isEmpty())return true;
        int direction=dy>0?-1:dy<0?1:0;
        if(hit(x,y,7,38,196,279))click(direction<0?48:50);
        else if(hit(x,y,535,43,176,138))beforeScroll=Math.clamp(beforeScroll+direction,0,Math.max(0,lore(item(31)).size()-8));
        else if(hit(x,y,535,194,176,138))afterScroll=Math.clamp(afterScroll+direction,0,Math.max(0,lore(item(32)).size()-8));
        return true;
    }
    @Override public boolean keyPressed(int key,int scan,int mods) {if(key==GLFW.GLFW_KEY_ESCAPE)onClose();return true;}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return true;}
    @Override public boolean mouseReleased(double x,double y,int button){return true;}
}
