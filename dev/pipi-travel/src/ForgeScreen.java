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
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import org.lwjgl.glfw.GLFW;

/**
 * 대장간 메뉴 + 장비 제작. The craft view is a Lost Ark style window: boss tabs on the far left (엔더 드래곤 = tier 0, 워든 = tier 1),
 * then the recipe list in collapsible tier groups (무기 / 근거리·원거리·마법 방어구 / 도구) with a 전체·전사·궁수·마법사 filter,
 * then the 제작 preview and the 세부 능력치 panel. The server puts every visible recipe in the chest (any slot except
 * 22 preview · 31 result · 40 craft · 41 confirm · 49 back) tagged with PublicBukkitValues["pipilife:forge_group"] = "tier:group".
 */
final class ForgeScreen extends ContainerScreen {
    static final String MENU = "삐삐서버 · 대장간 메뉴", CRAFT = "삐삐서버 · 장비 제작";
    private static final int WIDTH=730, HEIGHT=365, TEXT=0xffe6eef7, MUTED=0xffa6b9cb, GOLD=0xffe9c589;
    /** main menu (designed for 680 wide) is centred; middle/right craft panels sit OX right of the old layout, the dialogs OV. */
    private static final int MAIN_DX=25, OX=50, OV=25;
    private static final int[] ACTIONS={10,12,14,16,22};
    private static final int LIST_X=66, LIST_W=186, LIST_Y=77, LIST_BOTTOM=348, BAR_X=255;
    private static final int HEADER_H=20, ROW_H=22, GAP=2;
    private static final String[] GROUPS={"weapon","melee","ranged","magic","tool"};
    private static final String[] GROUP_LABELS={"무기","근거리 방어구","원거리 방어구","마법 방어구","도구"};
    private static final String[] FILTERS={"전체","전사","궁수","마법사"};
    private static final int[] BOSS_TIERS={0,1};
    private static final String[] BOSS_NAMES={"기본","워든"}; // 0티어 = 기본 장비, 워든 처치 = 1티어 (엔더 드래곤 삭제)
    // UI state survives the server re-opening the chest after every click
    private static int bossTab, classFilter=-1;
    private static double scroll;
    private static final java.util.Set<String> EXPANDED=new java.util.HashSet<>(java.util.Set.of("0:weapon","1:weapon"));

    private record Entry(int slot, ItemStack stack, int tier, String group, int cls) {}
    private record Row(boolean header, String key, String label, Entry entry, int y, int h) {}

    private final boolean main;
    private final ItemStack[] bossIcons={new ItemStack(Items.ANVIL), new ItemStack(Items.SCULK_SHRIEKER)};
    private float scale, left, top;
    private boolean pending, filterOpen, draggingBar;
    private ItemStack hoverStack;
    private String hoverText;

    ForgeScreen(ChestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        main=title.getString().equals(MENU);
    }

    @Override protected void init() {
        scale=Math.max(.01f,Math.min((width-18f)/WIDTH,(height-18f)/HEIGHT));
        left=(width-WIDTH*scale)/2;
        top=(height-HEIGHT*scale)/2;
    }

    // ---------- data ----------
    private java.util.List<Entry> entries() {
        java.util.List<Entry> out=new java.util.ArrayList<>();
        for(int slot=0;slot<54;slot++){
            ItemStack stack=item(slot);
            if(stack.isEmpty())continue;
            var values=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getCompound("PublicBukkitValues");
            if(!values.contains("pipilife:forge_group"))continue;
            String[] parts=values.getString("pipilife:forge_group").split(":");
            if(parts.length!=2)continue;
            int tier;
            try{tier=Integer.parseInt(parts[0]);}catch(NumberFormatException e){continue;}
            int cls=switch(parts[1]){case "melee"->ClassIcons.WARRIOR;case "ranged"->ClassIcons.ARCHER;case "magic"->ClassIcons.MAGE;
                case "weapon"->ClassIcons.of(stack);default->ClassIcons.NONE;};
            out.add(new Entry(slot,stack,tier,parts[1],cls));
        }
        return out;
    }

    /** Boss tabs this player sees: 워든 always, later bosses only once the server sends recipes of their tier (= boss cleared). */
    private java.util.List<Integer> visibleBosses(java.util.List<Entry> entries) {
        java.util.List<Integer> out=new java.util.ArrayList<>();
        for(int b=0;b<BOSS_TIERS.length;b++){
            int tier=BOSS_TIERS[b];
            if(b==0||entries.stream().anyMatch(e->e.tier()==tier))out.add(b);
        }
        return out;
    }

    private int currentBoss(java.util.List<Entry> entries) {
        if(!visibleBosses(entries).contains(bossTab)){bossTab=0;scroll=0;}
        return bossTab;
    }

    private java.util.List<Row> rows(java.util.List<Entry> entries) {
        int tier=BOSS_TIERS[currentBoss(entries)];
        java.util.List<Row> rows=new java.util.ArrayList<>();
        int y=0;
        for(int g=0;g<GROUPS.length;g++){
            String group=GROUPS[g];
            java.util.List<Entry> items=entries.stream().filter(e->e.tier()==tier&&e.group().equals(group)
                &&(classFilter<0||e.cls()==ClassIcons.NONE||e.cls()==classFilter)).toList();
            if(items.isEmpty())continue;
            String key=tier+":"+group;
            rows.add(new Row(true,key,tier+" 티어 "+GROUP_LABELS[g],null,y,HEADER_H));
            y+=HEADER_H+GAP;
            if(!EXPANDED.contains(key))continue;
            for(Entry e:items){rows.add(new Row(false,key,name(e.stack()),e,y,ROW_H));y+=ROW_H+GAP;}
        }
        return rows;
    }

    private static int contentHeight(java.util.List<Row> rows){
        if(rows.isEmpty())return 0;
        Row last=rows.get(rows.size()-1);
        return last.y()+last.h();
    }
    private static double maxScroll(java.util.List<Row> rows){return Math.max(0,contentHeight(rows)-(LIST_BOTTOM-LIST_Y));}

    // ---------- render ----------
    @Override public void render(GuiGraphics g,int mx,int my,float delta) {
        double x=(mx-left)/scale,y=(my-top)/scale;
        hoverStack=null;hoverText=null;
        g.fill(0,0,width,height,0xb80a1018);
        g.pose().pushPose();
        try {
            g.pose().translate(left,top,0);g.pose().scale(scale,scale,1);
            if(main) {
                g.pose().translate(MAIN_DX,0,0);
                double ax=x-MAIN_DX;
                g.drawCenteredString(font,"공동 마을  /  대장간",340,106,GOLD);
                for(int i=0;i<ACTIONS.length;i++){
                    int px=34+i*124;
                    ItemStack icon=item(ACTIONS[i]);
                    box(g,px,130,115,89,hit(ax,y,px,130,115,89));
                    if(!icon.isEmpty()){icon(g,icon,px+41,142,2);g.drawCenteredString(font,name(icon),px+57,192,TEXT);}
                }
                return;
            }
            ItemStack preview=item(22), result=item(31);
            boolean dialog=!item(41).isEmpty()||!result.isEmpty();
            double hx=dialog?-999:x, hy=dialog?-999:y;
            box(g,0,0,WIDTH,HEIGHT,false);
            g.fillGradient(1,1,WIDTH-1,37,0xff304255,0xff192735);
            g.drawString(font,"대장간  /  장비 제작",17,15,GOLD,false);
            java.util.List<Entry> entries=entries();
            drawBossTabs(g,entries,filterOpen?-999:hx,hy);
            drawList(g,entries,preview,result,hx,hy);
            box(g,220+OX,47,276,307,false);box(g,503+OX,47,166,307,false);
            g.drawString(font,"제작 미리보기",234+OX,58,MUTED,false);
            g.drawString(font,"세부 능력치",516+OX,58,MUTED,false);
            if(!preview.isEmpty() && result.isEmpty()){
                box(g,331+OX,78,54,53,false);icon(g,preview,342+OX,88,2);
                g.drawCenteredString(font,name(preview),358+OX,142,TEXT);
                box(g,232+OX,160,251,102,false);
                int ly=173;
                for(String line:lore(preview)){g.drawString(font,line,245+OX,ly,ly>=199?GOLD:TEXT,false);ly+=16;}
                textButton(g,265+OX,283,185,25,"제작하기",hx,hy);
                g.drawString(font,"제작 시 잠재 슬롯 3~7개",515+OX,83,MUTED,false);
            } else if(result.isEmpty()) g.drawCenteredString(font,"왼쪽에서 제작할 장비를 선택하세요.",358+OX,151,MUTED);
            textButton(g,516+OX,302,138,24,"돌아가기",hx,hy);
            if(filterOpen && !dialog) drawFilterMenu(g,x,y);
            if(dialog){
                g.pose().translate(0,0,250);
                g.fill(0,0,WIDTH,HEIGHT,0xb0080d14);
                box(g,210+OV,87,260,169,true);
                if(!result.isEmpty()){
                    g.drawCenteredString(font,"제작 완료",340+OV,104,GOLD);
                    box(g,310+OV,120,60,57,true);icon(g,result,324+OV,131,2);
                    g.drawCenteredString(font,name(result),340+OV,188,TEXT);
                    textButton(g,282+OV,214,116,25,"확인",x,y);
                }else{
                    g.drawCenteredString(font,name(preview),340+OV,121,GOLD);
                    g.drawCenteredString(font,"이 장비를 제작하시겠습니까?",340+OV,148,TEXT);
                    textButton(g,236+OV,197,95,25,"확인",x,y);
                    textButton(g,348+OV,197,95,25,"취소",x,y);
                }
            }
        }finally{g.pose().popPose();}
        if(!main && !item(31).isEmpty() && hit(x,y,310+OV,120,60,57)) g.renderTooltip(font,item(31),mx,my);
        else if(hoverStack!=null) g.renderTooltip(font,hoverStack,mx,my);
        else if(hoverText!=null) g.renderTooltip(font,Component.literal(hoverText),mx,my);
    }

    private void drawBossTabs(GuiGraphics g,java.util.List<Entry> entries,double x,double y){
        box(g,10,47,46,307,false);
        int current=currentBoss(entries);
        java.util.List<Integer> bosses=visibleBosses(entries);
        for(int i=0;i<bosses.size();i++){
            int b=bosses.get(i), ty=53+i*44;
            boolean hover=hit(x,y,14,ty,38,38), selected=b==current;
            g.fill(14,ty,52,ty+38,selected?GOLD:hover?0xffbc9862:0xff3d5267);
            g.fillGradient(15,ty+1,51,ty+37,selected?0xff3c5470:hover?0xff2c3d50:0xff141f2b,selected?0xff22344a:0xff0d151f);
            icon(g,bossIcons[b],19,ty+5,1.75f);
            if(hover){hoverText=BOSS_NAMES[b]+" · "+BOSS_TIERS[b]+" 티어";}
        }
    }

    private void drawList(GuiGraphics g,java.util.List<Entry> entries,ItemStack preview,ItemStack result,double x,double y){
        box(g,60,47,206,307,false);
        // 전체 ∨ filter header
        boolean headerHover=hit(x,y,LIST_X,53,194,19);
        box(g,LIST_X,53,194,19,headerHover||filterOpen);
        if(classFilter<0){for(int r=0;r<3;r++)for(int c=0;c<3;c++)g.fill(LIST_X+6+c*4,57+r*4,LIST_X+9+c*4,60+r*4,TEXT);}
        else ClassIcons.draw(g,classFilter,LIST_X+4,55,15);
        g.drawString(font,FILTERS[classFilter+1],LIST_X+23,59,TEXT,false);
        chevron(g,LIST_X+183,62,false);

        java.util.List<Row> rows=rows(entries);
        scroll=Math.clamp(scroll,0,maxScroll(rows));
        String selectedName=!preview.isEmpty()&&result.isEmpty()?name(preview):null;
        boolean inList=hit(x,y,LIST_X,LIST_Y,LIST_W,LIST_BOTTOM-LIST_Y) && !filterOpen;
        g.enableScissor((int)Math.ceil(left+LIST_X*scale),(int)Math.ceil(top+LIST_Y*scale),
            (int)Math.floor(left+(LIST_X+LIST_W)*scale),(int)Math.floor(top+LIST_BOTTOM*scale));
        try{
            for(Row row:rows){
                int ry=LIST_Y+row.y()-(int)scroll;
                if(ry+row.h()<LIST_Y||ry>LIST_BOTTOM)continue;
                boolean hover=inList&&hit(x,y,LIST_X,ry,LIST_W,row.h());
                if(row.header()){
                    boolean open=EXPANDED.contains(row.key());
                    g.fill(LIST_X,ry,LIST_X+LIST_W,ry+row.h(),hover?0xff6b8299:0xff3d5267);
                    g.fillGradient(LIST_X+1,ry+1,LIST_X+LIST_W-1,ry+row.h()-1,hover?0xff3a4f66:0xff26384b,0xff1a2838);
                    g.drawString(font,row.label(),LIST_X+8,ry+6,TEXT,false);
                    chevron(g,LIST_X+LIST_W-12,ry+10,false);
                }else{
                    Entry e=row.entry();
                    boolean selected=row.label().equals(selectedName);
                    g.fill(LIST_X+4,ry,LIST_X+LIST_W,ry+row.h(),selected?GOLD:hover?0xffbc9862:0xff2a3a4b);
                    g.fillGradient(LIST_X+5,ry+1,LIST_X+LIST_W-1,ry+row.h()-1,selected?0xff3a3424:0xff0f1820,0xff0a1118);
                    icon(g,e.stack(),LIST_X+9,ry+3,1f);
                    g.drawString(font,font.plainSubstrByWidth(row.label(),LIST_W-40),LIST_X+32,ry+7,selected?GOLD:TEXT,false);
                    if(hover)hoverStack=e.stack();
                }
            }
            if(rows.isEmpty())g.drawCenteredString(font,"제작 가능한 장비가 없습니다.",LIST_X+LIST_W/2,LIST_Y+12,MUTED);
        }finally{g.disableScissor();}
        // scrollbar
        double max=maxScroll(rows);
        g.fill(BAR_X,LIST_Y,BAR_X+3,LIST_BOTTOM,0xff26384b);
        if(max>0){
            int view=LIST_BOTTOM-LIST_Y, thumb=Math.max(20,view*view/Math.max(1,contentHeight(rows)));
            int ty=LIST_Y+(int)Math.round((view-thumb)*scroll/max);
            g.fill(BAR_X,ty,BAR_X+3,ty+thumb,draggingBar?GOLD:0xff8aa2b8);
        }
    }

    private void drawFilterMenu(GuiGraphics g,double x,double y){
        g.pose().pushPose();
        g.pose().translate(0,0,200);
        box(g,LIST_X,73,194,4*18+2,false);
        for(int i=0;i<FILTERS.length;i++){
            int oy=74+i*18;
            boolean hover=hit(x,y,LIST_X+1,oy,192,18), selected=i-1==classFilter;
            if(hover||selected)g.fill(LIST_X+1,oy,LIST_X+193,oy+18,hover?0xff34465b:0xff22303f);
            if(i==0){for(int r=0;r<3;r++)for(int c=0;c<3;c++)g.fill(LIST_X+6+c*4,oy+4+r*4,LIST_X+9+c*4,oy+7+r*4,TEXT);}
            else ClassIcons.draw(g,i-1,LIST_X+4,oy+1,15);
            g.drawString(font,FILTERS[i],LIST_X+23,oy+5,selected?GOLD:TEXT,false);
        }
        g.pose().popPose();
    }

    /** Small pixel chevron centred on (cx, cy): down (∨) or up (∧). */
    private static void chevron(GuiGraphics g,int cx,int cy,boolean up){
        for(int i=0;i<4;i++){
            int row=up?cy+1-i:cy-2+i;
            g.fill(cx-4+i,row,cx-2+i,row+1,TEXT);
            g.fill(cx+2-i,row,cx+4-i,row+1,TEXT);
        }
    }

    // ---------- helpers ----------
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

    // ---------- input ----------
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(button!=0)return true;
        double x=(mx-left)/scale,y=(my-top)/scale;
        if(main){for(int i=0;i<ACTIONS.length;i++)if(hit(x-MAIN_DX,y,34+i*124,130,115,89))click(ACTIONS[i]);return true;}
        if(!item(31).isEmpty()){if(hit(x,y,282+OV,214,116,25))click(49);return true;}
        if(!item(41).isEmpty()){
            if(hit(x,y,236+OV,197,95,25))click(41);
            else if(hit(x,y,348+OV,197,95,25))click(49);
            return true;
        }
        java.util.List<Entry> entries=entries();
        if(filterOpen){
            for(int i=0;i<FILTERS.length;i++)if(hit(x,y,LIST_X+1,74+i*18,192,18)){classFilter=i-1;scroll=0;}
            filterOpen=false;
            return true;
        }
        if(hit(x,y,LIST_X,53,194,19)){filterOpen=true;return true;}
        java.util.List<Integer> bosses=visibleBosses(entries);
        for(int i=0;i<bosses.size();i++)if(hit(x,y,14,53+i*44,38,38)){
            if(bossTab!=bosses.get(i)){bossTab=bosses.get(i);scroll=0;}
            return true;
        }
        java.util.List<Row> rows=rows(entries);
        if(maxScroll(rows)>0 && hit(x,y,BAR_X-2,LIST_Y,7,LIST_BOTTOM-LIST_Y)){draggingBar=true;dragBar(y,rows);return true;}
        if(hit(x,y,LIST_X,LIST_Y,LIST_W,LIST_BOTTOM-LIST_Y)){
            for(Row row:rows){
                int ry=LIST_Y+row.y()-(int)scroll;
                if(!hit(x,y,LIST_X,ry,LIST_W,row.h()))continue;
                if(row.header()){if(!EXPANDED.remove(row.key()))EXPANDED.add(row.key());scroll=Math.clamp(scroll,0,maxScroll(rows(entries)));}
                else click(row.entry().slot());
                return true;
            }
            return true;
        }
        if(hit(x,y,265+OX,283,185,25))click(40);
        else if(hit(x,y,516+OX,302,138,24))click(49);
        return true;
    }
    private void dragBar(double y,java.util.List<Row> rows){
        double max=maxScroll(rows);
        int view=LIST_BOTTOM-LIST_Y, thumb=Math.max(20,view*view/Math.max(1,contentHeight(rows)));
        scroll=Math.clamp((y-LIST_Y-thumb/2.0)/Math.max(1,view-thumb)*max,0,max);
    }
    @Override public boolean mouseReleased(double x,double y,int button){draggingBar=false;return true;}
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){
        if(draggingBar && !main)dragBar((my-top)/scale,rows(entries()));
        return true;
    }
    @Override public boolean mouseScrolled(double mx,double my,double dx,double dy){
        if(main||filterOpen)return true;
        double x=(mx-left)/scale,y=(my-top)/scale;
        if(hit(x,y,LIST_X,LIST_Y,LIST_W+12,LIST_BOTTOM-LIST_Y))
            scroll=Math.clamp(scroll-dy*(ROW_H+GAP),0,maxScroll(rows(entries())));
        return true;
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(key==GLFW.GLFW_KEY_ESCAPE){if(filterOpen)filterOpen=false;else onClose();}
        return true;
    }
}
