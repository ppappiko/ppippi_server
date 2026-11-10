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
import net.minecraft.world.item.component.ItemLore;
import org.lwjgl.glfw.GLFW;

final class ForgeScreen extends ContainerScreen {
    static final String MENU = "삐삐서버 · 대장간 메뉴", CRAFT = "삐삐서버 · 장비 제작";
    private static final int WIDTH=680, HEIGHT=365, TEXT=0xffe6eef7, MUTED=0xffa6b9cb, GOLD=0xffe9c589;
    private static final int[] ACTIONS={10,12,14,16,22};
    private final boolean main;
    private float scale, left, top;
    private boolean pending;

    ForgeScreen(ChestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        main=title.getString().equals(MENU);
    }

    @Override protected void init() {
        scale=Math.max(.01f,Math.min((width-18f)/WIDTH,(height-18f)/HEIGHT));
        left=(width-WIDTH*scale)/2;
        top=(height-HEIGHT*scale)/2;
    }

    @Override public void render(GuiGraphics g,int mx,int my,float delta) {
        double x=(mx-left)/scale,y=(my-top)/scale;
        g.fill(0,0,width,height,0xb80a1018);
        g.pose().pushPose();
        try {
            g.pose().translate(left,top,0);g.pose().scale(scale,scale,1);
            if(main) {
                g.drawCenteredString(font,"공동 마을  /  대장간",340,106,GOLD);
                for(int i=0;i<ACTIONS.length;i++){
                    int px=34+i*124;
                    ItemStack icon=item(ACTIONS[i]);
                    box(g,px,130,115,89,hit(x,y,px,130,115,89));
                    if(!icon.isEmpty()){icon(g,icon,px+41,142,2);g.drawCenteredString(font,name(icon),px+57,192,TEXT);}
                }
                return;
            }
            box(g,0,0,WIDTH,HEIGHT,false);
            g.fillGradient(1,1,WIDTH-1,37,0xff304255,0xff192735);
            g.drawString(font,"대장간  /  장비 제작",17,15,GOLD,false);
            box(g,10,47,203,307,false);box(g,220,47,276,307,false);box(g,503,47,166,307,false);
            g.drawString(font,"장비 분류",21,58,MUTED,false);
            g.drawString(font,"제작 미리보기",234,58,MUTED,false);
            g.drawString(font,"세부 능력치",516,58,MUTED,false);
            int row=0;
            for(int i=0;i<8;i++)if(!item(i).isEmpty()){
                int ry=78+row++*17;
                textButton(g,21,ry,181,16,name(item(i)),x,y);
            }
            for(int i=0;i<3;i++)if(!item(15+i).isEmpty())
                textButton(g,21+i*61,217,58,18,name(item(15+i)),x,y);
            for(int i=0;i<6;i++)if(!item(9+i).isEmpty()){
                int ry=240+i*17;
                box(g,21,ry,181,16,hit(x,y,21,ry,181,16));
                icon(g,item(9+i),24,ry+1,.8f);
                g.drawString(font,name(item(9+i)),43,ry+4,TEXT,false);
            }
            ItemStack preview=item(22), result=item(31);
            if(!preview.isEmpty() && result.isEmpty()){
                box(g,331,78,54,53,false);icon(g,preview,342,88,2);
                g.drawCenteredString(font,name(preview),358,142,TEXT);
                box(g,232,160,251,102,false);
                int ly=173;
                for(String line:lore(preview)){g.drawString(font,line,245,ly,ly>=199?GOLD:TEXT,false);ly+=16;}
                textButton(g,265,283,185,25,"제작하기",x,y);
                g.drawString(font,"제작 시 잠재 슬롯 3~7개",515,83,MUTED,false);
            } else if(result.isEmpty()) g.drawCenteredString(font,"왼쪽에서 제작할 장비를 선택하세요.",358,151,MUTED);
            textButton(g,516,302,138,24,"돌아가기",x,y);
            if(!item(41).isEmpty() || !result.isEmpty()){
                g.pose().translate(0,0,250);
                g.fill(0,0,WIDTH,HEIGHT,0xb0080d14);
                box(g,210,87,260,169,true);
                if(!result.isEmpty()){
                    g.drawCenteredString(font,"제작 완료",340,104,GOLD);
                    box(g,310,120,60,57,true);icon(g,result,324,131,2);
                    g.drawCenteredString(font,name(result),340,188,TEXT);
                    textButton(g,282,214,116,25,"확인",x,y);

                }else{
                    g.drawCenteredString(font,name(preview),340,121,GOLD);
                    g.drawCenteredString(font,"이 장비를 제작하시겠습니까?",340,148,TEXT);
                    textButton(g,236,197,95,25,"확인",x,y);
                    textButton(g,348,197,95,25,"취소",x,y);
                }
            }
        }finally{g.pose().popPose();}
        if (!item(31).isEmpty() && hit(x,y,310,120,60,57)) g.renderTooltip(font,item(31),mx,my);
    }

    private ItemStack item(int slot){return menu.getSlot(slot).getItem();}
    private static String name(ItemStack stack){return ChatFormatting.stripFormatting(stack.getHoverName().getString());}
    private static java.util.List<String> lore(ItemStack stack){
        return stack.getOrDefault(DataComponents.LORE,ItemLore.EMPTY).lines().stream()
            .map(line->ChatFormatting.stripFormatting(line.getString())).toList();
    }
    private static void box(GuiGraphics g,int x,int y,int w,int h,boolean hover){
        g.fill(x,y,x+w,y+h,hover?0xffbc9862:0xff3d5267);
        g.fillGradient(x+1,y+1,x+w-1,y+h-1,hover?0xff34465b:0xff182533,0xff101a25);
    }
    private void textButton(GuiGraphics g,int x,int y,int w,int h,String label,double mx,double my){
        box(g,x,y,w,h,hit(mx,my,x,y,w,h));g.drawCenteredString(font,label,x+w/2,y+(h-8)/2,TEXT);
    }
    private static void icon(GuiGraphics g,ItemStack stack,int x,int y,float scale){
        g.pose().pushPose();
        try{g.pose().translate(x,y,0);g.pose().scale(scale,scale,1);g.renderItem(stack,0,0);}
        finally{g.pose().popPose();}
    }
    private static boolean hit(double x,double y,int bx,int by,int w,int h){return x>=bx&&y>=by&&x<bx+w&&y<by+h;}
    private void click(int slot){
        if(item(slot).isEmpty()||minecraft==null||minecraft.getConnection()==null)return;
        if(slot==41 && pending)return;
        if(slot==41)pending=true;
        minecraft.getConnection().send(new ServerboundContainerClickPacket(menu.containerId,menu.getStateId(),slot,0,
            ClickType.PICKUP,menu.getCarried().copy(),Int2ObjectMaps.emptyMap()));
    }
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(button!=0)return true;
        double x=(mx-left)/scale,y=(my-top)/scale;
        if(main){for(int i=0;i<ACTIONS.length;i++)if(hit(x,y,34+i*124,130,115,89))click(ACTIONS[i]);}
        else if(!item(31).isEmpty()){if(hit(x,y,282,214,116,25))click(49);}
        else if(!item(41).isEmpty()){
            if(hit(x,y,236,197,95,25))click(41);
            else if(hit(x,y,348,197,95,25))click(49);
        }else{
            int row=0;
            for(int i=0;i<8;i++)if(!item(i).isEmpty()&&hit(x,y,21,78+row++*17,181,16)){click(i);return true;}
            for(int i=0;i<3;i++)if(!item(15+i).isEmpty() && hit(x,y,21+i*61,217,58,18)){click(15+i);return true;}
            for(int i=0;i<6;i++)if(hit(x,y,21,240+i*17,181,16)){click(9+i);return true;}
            if(hit(x,y,265,283,185,25))click(40);
            else if(hit(x,y,516,302,138,24))click(49);
        }
        return true;
    }
    @Override public boolean mouseReleased(double x,double y,int button){return true;}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return true;}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){return true;}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(key==GLFW.GLFW_KEY_ESCAPE)onClose();return true;}
}
