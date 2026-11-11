package dev.pipi.travel;

import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

final class ShopScreen extends ContainerScreen {
    static final String TITLE = "삐삐서버 · 상점";
    private static final int W = 660, H = 320, TEXT = 0xffe3edf7, MUTED = 0xffa2b6c8, GOLD = 0xffe8c17c;
    private static final int[] STEPS = {-10, -1, 1, 10};
    private static final int[] STEP_X = {249, 288, 342, 381};
    private float scale, left, top;
    private int first, selected = -1, count = 1;
    private boolean dragging, sent;

    ShopScreen(ChestMenu menu, Inventory inventory, Component title, ShopScreen previous) {
        super(menu, inventory, title);
        if (previous != null && previous.tab()==tab()) first = previous.first;
    }

    @Override protected void init() {
        scale = Math.max(.01f, Math.min((width - 14f) / W, (height - 14f) / H));
        left = (width - W * scale) / 2;
        top = (height - H * scale) / 2;
    }

    private ItemStack item(int index) { return menu.getSlot(index).getItem(); }
    private boolean buying() { return item(45).is(Items.LIME_DYE); }
    private int tab() { return buying() ? 0 : item(51).is(Items.LIME_DYE) ? 2 : 1; }
    private boolean confirming() { return !item(48).isEmpty(); }
    private String action() { return buying() ? "구매" : "판매"; }
    private String data(int slot, int line) {
        var lines = item(slot).getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines();
        return line < lines.size() ? lines.get(line).getString() : "";
    }
    private int number(int slot, int line) {
        try { return Integer.parseInt(data(slot, line)); }
        catch (NumberFormatException ignored) { return 0; }
    }
    private int maximum() { return Math.max(0, number(selected, 2)); }
    private static String amount(long value) { return "%,d".formatted(value); }

    @Override public void render(GuiGraphics g, int mx, int my, float delta) {
        double x = (mx-left)/scale, y = (my-top)/scale;
        int hover = -1;
        g.fill(0, 0, width, height, 0xba080e16);
        g.pose().pushPose();
        try {
            g.pose().translate(left, top, 0);
            g.pose().scale(scale, scale, 1);
            panel(g, 0, 0, 354, H, false);
            panel(g, 358, 0, 302, H, false);
            g.drawString(font, "공동 마을 상점", 13, 13, TEXT, false);
            g.renderItem(item(47), 375, 12);
            g.drawString(font, font.plainSubstrByWidth("보유 골드  " + item(47).getHoverName().getString(), 628 - 6 - 395), 395, 16, GOLD, false);
            button(g, "×", 628, 8, 24, 23, hit(x,y,628,8,24,23));
            button(g, "구매", 13, 32, 48, 24, tab()==0);
            button(g, "광물 상점", 65, 32, 70, 24, tab()==1);
            button(g, "재료 상점", 139, 32, 70, 24, tab()==2);
            long seconds = (3600 - Math.floorMod(System.currentTimeMillis()/1000, 3600));
            g.drawString(font, "초기화  %02d:%02d".formatted(seconds/60, seconds%60), 280, 41, MUTED, false);
            g.drawString(font, "아이템 이름", 33, 65, MUTED, false);
            g.drawString(font, "남은 한도", 200, 65, MUTED, false);
            g.drawString(font, "개당 가격", 282, 65, MUTED, false);
            for (int i=0; i<7 && first+i<27 && !item(first+i).isEmpty(); i++) {
                int index=first+i, row=80+i*31;
                panel(g, 12, row, 330, 28, hit(x,y,12,row,330,28));
                g.renderItem(item(index), 19, row+6);
                g.drawString(font, item(index).getHoverName(), 43, row+10, TEXT, false);
                g.drawString(font, data(index,0), 202, row+10, MUTED, false);
                String price=amount(number(index,1));
                g.drawString(font, price, 324-font.width(price), row+10, GOLD, false);
            }
            g.drawString(font, "마우스 휠로 상품 목록 이동", 16, 303, MUTED, false);
            g.drawString(font, "내 가방", 375, 40, TEXT, false);
            for (int i=0;i<36;i++) {
                int sx=375+i%9*29, sy=65+i/9*35+(i>=27?8:0);
                ItemStack stack=item(54+i);
                panel(g,sx,sy,27,27,hit(x,y,sx,sy,27,27));
                g.renderItem(stack,sx+5,sy+5);
                g.renderItemDecorations(font,stack,sx+5,sy+5);
                if (hit(x,y,sx,sy,27,27)) hover=54+i;
            }
            if (selected >= 0 || confirming()) {
                g.pose().translate(0,0,250);
                g.fill(0,0,W,H,0xaa080f16);
                panel(g,209,56,242,215,true);
                int index=confirming()?number(48,0):selected;
                int units=confirming()?number(48,1):count;
                g.renderItem(item(index),322,70);
                g.drawCenteredString(font,item(index).getHoverName(),330,94,TEXT);
                g.drawString(font,"개당 가격  " + amount(number(index,1)),225,118,MUTED,false);
                if (confirming()) {
                    g.drawCenteredString(font,units+"개를 "+action()+"하시겠습니까?",330,151,TEXT);
                } else {
                    for(int i=0;i<STEPS.length;i++)
                        button(g,i==0?"≪":i==1?"‹":i==2?"›":"≫",STEP_X[i],142,28,20,false);
                    g.drawCenteredString(font,Integer.toString(count),330,149,TEXT);
                    g.fill(241,180,419,183,0xff48667d);
                    int cursor=241+(int)(178.0*Math.max(0,count-1)/Math.max(1,maximum()-1));
                    g.fill(cursor-3,176,cursor+4,187,0xff77d7d3);
                }
                g.drawString(font,"총 가격  " + amount((long) units*number(index,1))+"골드",225,200,GOLD,false);
                button(g,confirming()?"확인":action(),230,228,92,26,confirming()||count>0);
                button(g,"취소",338,228,92,26,false);
            }
        } finally {
            g.pose().popPose();
        }
        if (hover >= 0 && selected < 0 && !confirming() && !item(hover).isEmpty()) g.renderTooltip(font,item(hover),mx,my);
    }

    private static void panel(GuiGraphics g,int x,int y,int w,int h,boolean active) {
        g.fill(x,y,x+w,y+h,active?0xff668ba8:0xff344a60);
        g.fillGradient(x+1,y+1,x+w-1,y+h-1,active?0xff2d455b:0xff1b2939,0xff111b27);
    }
    private void button(GuiGraphics g,String label,int x,int y,int w,int h,boolean active) {
        panel(g,x,y,w,h,active);
        g.drawCenteredString(font,label,x+w/2,y+(h-8)/2,TEXT);
    }
    private static boolean hit(double x,double y,int bx,int by,int w,int h) {
        return x>=bx && y>=by && x<bx+w && y<by+h;
    }

    private void sendClick(int slot) {
        if (sent || minecraft == null || minecraft.getConnection() == null) return;
        sent=true;
        minecraft.getConnection().send(new ServerboundContainerClickPacket(menu.containerId,menu.getStateId(),
            slot,0,ClickType.PICKUP,menu.getCarried().copy(),Int2ObjectMaps.emptyMap()));
    }
    private void slide(double x) {
        count=maximum()==0?(buying()?1:0):1+(int)Math.round(Math.clamp((x-241)/178,0,1)*(maximum()-1));
    }
    @Override public boolean mouseClicked(double mx,double my,int button) {
        if(button!=0) return true;
        double x=(mx-left)/scale,y=(my-top)/scale;
        if(hit(x,y,628,8,24,23)) { onClose(); return true; }
        if(confirming()) {
            if(hit(x,y,230,228,92,26)) sendClick(49);
            else if(hit(x,y,338,228,92,26)) sendClick(50);
        } else if(selected>=0) {
            if(hit(x,y,338,228,92,26)) selected=-1;
            else if(hit(x,y,230,228,92,26) && count>0 && !sent && minecraft != null && minecraft.getConnection() != null) {
                sent=true;
                minecraft.getConnection().sendCommand("pipilife shop quote "+selected+" "+count);
            } else if(hit(x,y,234,172,192,20)) {
                dragging=true;
                slide(x);
            } else for(int i=0;i<STEPS.length;i++)
                if(hit(x,y,STEP_X[i],142,28,20) && maximum()>0) count=Math.clamp(count+STEPS[i],1,maximum());
        } else if(hit(x,y,13,32,48,24)) sendClick(45);
        else if(hit(x,y,65,32,70,24)) sendClick(46);
        else if(hit(x,y,139,32,70,24)) sendClick(51);
        else if(hit(x,y,12,80,330,217)) {
            int index=first+(int)((y-80)/31);
            if(index<27 && !item(index).isEmpty()) { selected=index; count=maximum()==0&&!buying()?0:1; }
        }
        return true;
    }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy) {
        if(selected<0 && !confirming() && dy!=0) {
            int size=0;
            while(size<27 && !item(size).isEmpty()) size++;
            first=Math.clamp(first+(dy>0?-1:1),0,Math.max(0,size-7));
        }
        return true;
    }
    @Override public boolean mouseDragged(double x,double y,int b,double dx,double dy) {
        if(dragging) slide((x-left)/scale);
        return true;
    }
    @Override public boolean mouseReleased(double x,double y,int b) { dragging=false; return true; }
    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            if(confirming()) sendClick(50);
            else if(selected>=0) selected=-1;
            else onClose();
        }
        return true;
    }
}
