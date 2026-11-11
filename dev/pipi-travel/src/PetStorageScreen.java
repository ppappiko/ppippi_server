package dev.pipi.travel;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.lwjgl.glfw.GLFW;

final class PetStorageScreen extends ContainerScreen {
    static final String TITLE = "삐삐서버 · 펫 창고";
    private static final int W=570,H=355, TEXT=0xffe5edf7, BLUE=0xff92c3e3, MUTED=0xff9bb2c6, MAX_LEVEL=3;
    private float scale,left,top;
    private int selected=-1, source=-1;
    private ItemStack held=ItemStack.EMPTY;
    private long pending;
    private boolean confirming;
    private int confirmIndex;
    private String confirmName="",confirmLevel="";
    PetStorageScreen(ChestMenu menu, Inventory inventory, Component title, PetStorageScreen previous) {
        super(menu,inventory,title);
        if(previous!=null){selected=previous.selected;source=previous.source;held=previous.held;}
    }
    @Override protected void init(){
        scale=Math.max(.01f,Math.min((width-16f)/W,(height-16f)/H));
        left=(width-W*scale)/2;top=(height-H*scale)/2;
    }
    private int[] state(){
        int[] values={1,1,1,0,100};
        var lines=menu.getSlot(49).getItem().getOrDefault(DataComponents.LORE,ItemLore.EMPTY).lines();
        if(!lines.isEmpty()){
            String[] fields=lines.getFirst().getString().split(",");
            if(fields.length==5)try{for(int i=0;i<5;i++)values[i]=Integer.parseInt(fields[i]);}
            catch(NumberFormatException ignored){}
        }
        return values;
    }
    @Override public void render(GuiGraphics g,int sx,int sy,float delta){
        int x=(int)((sx-left)/scale),y=(int)((sy-top)/scale);
        int[] state=state();
        g.fill(0,0,width,height,0xba080e16);
        g.pose().pushPose();
        try{
            g.pose().translate(left,top,0);g.pose().scale(scale,scale,1);
            panel(g,0,0,W,H,0xff101a26,0xff405b73);
            g.fillGradient(1,1,W-1,39,0xff294056,0xff172739);
            g.drawString(font,"펫 창고",15,15,TEXT,false);
            g.drawString(font,"Lv. "+state[0],245,15,BLUE,false);
            button(g,286,8,86,23,state[0]>=MAX_LEVEL?"최대 레벨":"레벨업",x,y,state[0]<MAX_LEVEL);
            button(g,533,8,24,23,"×",x,y,true);
            panel(g,12,48,360,294,0xff101e2c,0xff344b61);
            panel(g,380,48,177,294,0xff111f2d,0xff344b61);
            button(g,23,58,27,22,"Q",x,y,state[1]>1);
            button(g,334,58,27,22,"E",x,y,state[1]<state[2]);
            g.drawCenteredString(font,state[1]+" / "+state[2]+" 페이지   ·   보유 "+state[3]+"마리",192,65,BLUE);
            for(int i=0;i<30;i++){
                int px=25+i%6*55,py=90+i/6*44;
                ItemStack item=menu.getSlot(i).getItem();
                panel(g,px,py,49,39,hit(x,y,px,py,49,39)?0xff223d54:0xff101a26,
                    selected==i?BLUE:0xff344b61);
                if(!item.isEmpty()) icon(g,item,px+15,py+10,1.2f);
            }
            g.drawString(font,"Q / E 페이지 이동   ·   드래그로 위치 변경",25,318,MUTED,false);
            g.drawString(font,"펫 정보",392,62,BLUE,false);
            if(selected>=0){
                ItemStack item=menu.getSlot(selected).getItem();
                if(!item.isEmpty()){
                    panel(g,437,91,62,62,0xff101a26,0xff486681);
                    icon(g,item,450,104,2.2f);
                    g.drawCenteredString(font,item.getHoverName(),468,167,TEXT);
                    int iy=193;
                    for(Component line:item.getOrDefault(DataComponents.LORE,ItemLore.EMPTY).lines()){
                        g.drawString(font,font.plainSubstrByWidth(line.getString(),151),392,iy,MUTED,false);
                        iy+=15;
                    }
                    boolean rh=!confirming&&hit(x,y,392,284,157,22);
                    panel(g,392,284,157,22,rh?0xff7a2e2e:0xff4a2020,0xffd46a6a);
                    g.drawCenteredString(font,"방출",470,288,0xffffb0b0);
                }
            }
            if(state[0]<MAX_LEVEL){
                int ly=310;
                g.drawString(font,"레벨업 (+2페이지)",392,ly,MUTED,false);
                for(Component line:menu.getSlot(50).getItem().getOrDefault(DataComponents.LORE,ItemLore.EMPTY).lines()){
                    ly+=11;
                    g.drawString(font,font.plainSubstrByWidth(line.getString(),160),392,ly,MUTED,false);
                }
            }
            if(confirming){
                g.pose().translate(0,0,400);
                g.fill(0,0,W,H,0xaa000000);
                panel(g,95,115,380,125,0xff101a26,0xffd46a6a);
                g.drawCenteredString(font,confirmName+" Lv."+confirmLevel+"을(를) 방출할까요?",285,135,TEXT);
                g.drawCenteredString(font,"방출한 펫은 되돌릴 수 없습니다.",285,155,0xffffb0b0);
                boolean oh=hit(x,y,175,190,100,24),ch=hit(x,y,295,190,100,24);
                panel(g,175,190,100,24,oh?0xff7a2e2e:0xff4a2020,0xffd46a6a);
                g.drawCenteredString(font,"방출",225,198,0xffffb0b0);
                panel(g,295,190,100,24,ch?0xff345773:0xff203548,BLUE);
                g.drawCenteredString(font,"취소",345,198,TEXT);
            }
            if(!held.isEmpty()){
                g.pose().translate(0,0,350);
                icon(g,held,x-8,y-8,1);
            }
        }finally{g.pose().popPose();}
    }
    private static void icon(GuiGraphics g,ItemStack item,int x,int y,float scale){
        g.pose().pushPose();
        try{g.pose().translate(x,y,0);g.pose().scale(scale,scale,1);g.renderItem(item,0,0);}
        finally{g.pose().popPose();}
    }
    private static void panel(GuiGraphics g,int x,int y,int w,int h,int fill,int border){
        g.fill(x,y,x+w,y+h,border);g.fill(x+1,y+1,x+w-1,y+h-1,fill);
    }
    private void button(GuiGraphics g,int x,int y,int w,int h,String text,int mx,int my,boolean enabled){
        panel(g,x,y,w,h,enabled&&hit(mx,my,x,y,w,h)?0xff345773:0xff203548,enabled?BLUE:0xff34495d);
        g.drawCenteredString(font,text,x+w/2,y+(h-8)/2,enabled?TEXT:MUTED);
    }
    private static boolean hit(double x,double y,int left,int top,int w,int h){
        return x>=left&&y>=top&&x<left+w&&y<top+h;
    }
    private int slot(double x,double y){
        for(int i=0;i<30;i++)if(hit(x,y,25+i%6*55,90+i/6*44,49,39))return i;
        return -1;
    }
    private void send(String action){
        if(minecraft==null||minecraft.getConnection()==null||System.currentTimeMillis()<pending)return;
        pending=System.currentTimeMillis()+750;
        minecraft.getConnection().sendCommand("pipilife petstorage "+action);
    }
    private void page(int delta){
        int[] state=state();
        int page=state[1]+delta;
        if(page>=1&&page<=state[2]){selected=-1;send("page "+page);}
    }
    @Override public boolean keyPressed(int key,int scan,int mods){
        if(confirming){if(key==GLFW.GLFW_KEY_ESCAPE)confirming=false;return true;}
        if(key==GLFW.GLFW_KEY_ESCAPE)onClose();
        else if(key==GLFW.GLFW_KEY_Q)page(-1);
        else if(key==GLFW.GLFW_KEY_E)page(1);
        return true;
    }
    @Override public boolean mouseClicked(double sx,double sy,int button){
        if(button!=0)return true;
        double x=(sx-left)/scale,y=(sy-top)/scale;
        if(confirming){
            if(hit(x,y,175,190,100,24)){
                confirming=false;
                int idx=confirmIndex;
                selected=-1;source=-1;held=ItemStack.EMPTY;
                send("release "+idx);
            }else if(hit(x,y,295,190,100,24))confirming=false;
            return true;
        }
        if(selected>=0&&!menu.getSlot(selected).getItem().isEmpty()&&hit(x,y,392,284,157,22)){
            ItemStack item=menu.getSlot(selected).getItem();
            confirmName=item.getHoverName().getString();
            confirmLevel="?";
            for(Component line:item.getOrDefault(DataComponents.LORE,ItemLore.EMPTY).lines()){
                String s=line.getString();int k=s.indexOf("Lv.");
                if(k>=0){confirmLevel=s.substring(k+3).trim();break;}
            }
            confirmIndex=(state()[1]-1)*30+selected;
            confirming=true;
            source=-1;held=ItemStack.EMPTY;
        }
        else if(hit(x,y,533,8,24,23))onClose();
        else if(hit(x,y,286,8,86,23)&&state()[0]<MAX_LEVEL)send("upgrade");
        else if(hit(x,y,23,58,27,22))page(-1);
        else if(hit(x,y,334,58,27,22))page(1);
        else{
            int i=slot(x,y);
            if(i>=0){
                selected=i;
                held=menu.getSlot(i).getItem().copy();
                source=held.isEmpty()?-1:(state()[1]-1)*30+i;
            }
        }
        return true;
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return true;}
    @Override public boolean mouseReleased(double sx,double sy,int button){
        if(button!=0||confirming)return true;
        int i=slot((sx-left)/scale,(sy-top)/scale);
        if(source>=0&&i>=0){
            int target=(state()[1]-1)*30+i;
            if(target!=source){selected=i;send("move "+source+" "+target);}
        }
        source=-1;held=ItemStack.EMPTY;
        return true;
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){return true;}
}
