package dev.pipi.travel;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.component.ItemLore;
import org.lwjgl.glfw.GLFW;

final class CharacterScreen extends ContainerScreen {
    static final String TITLE="삐삐서버 · 캐릭터 정보";
    private static final int W=520,H=410,TEXT=0xffe5eaf0,MUTED=0xffa6b9ca;
    private float scale,left,top;
    CharacterScreen(ChestMenu menu,Inventory inventory,Component title){super(menu,inventory,title);}
    @Override protected void init(){scale=Math.max(.01f,Math.min((width-20f)/W,(height-20f)/H));left=(width-W*scale)/2;top=(height-H*scale)/2;}
    private List<String> data(){return menu.getSlot(10).getItem().getOrDefault(DataComponents.LORE,ItemLore.EMPTY).lines().stream().map(c->ChatFormatting.stripFormatting(c.getString())).toList();}
    private String val(List<String> values,int i){return values.size()>i?values.get(i):"—";}
    private void line(GuiGraphics g,String label,String value,int x,int y){g.drawString(font,label,x,y,MUTED,false);g.drawString(font,value,x+94,y,TEXT,false);}
    @Override public void render(GuiGraphics g,int mx,int my,float delta){
        g.fill(0,0,width,height,0xb9081019);g.pose().pushPose();
        try {
            g.pose().translate(left,top,0);g.pose().scale(scale,scale,1);
            g.fill(0,0,W,H,0xff314b5d);g.fill(1,1,W-1,H-1,0xff0d1720);
            g.fillGradient(7,7,W-7,32,0xff293d50,0xff1b2937);g.drawCenteredString(font,"캐릭터 정보",W/2,15,TEXT);
            g.drawString(font,"×",W-24,15,TEXT,false);
            var values=data();
            g.fill(17,45,131,185,0xff080f16);
            String name=menu.getSlot(0).getItem().getHoverName().getString();
            if(minecraft!=null&&minecraft.player!=null){
                net.minecraft.world.entity.LivingEntity shown=minecraft.player;
                if(minecraft.level!=null)for(var other:minecraft.level.players())if(other.getGameProfile().getName().equals(name)){shown=other;break;}
                // the entity renderer's scissor uses screen coordinates, so draw it outside the scaled pose
                g.pose().popPose();
                InventoryScreen.renderEntityInInventoryFollowsMouse(g,(int)(left+24*scale),(int)(top+49*scale),(int)(left+124*scale),(int)(top+180*scale),
                    Math.max(8,(int)(48*scale)),.0625f,mx,my,shown);
                g.pose().pushPose();g.pose().translate(left,top,0);g.pose().scale(scale,scale,1);
            }
            g.fill(143,46,500,65,0xff263a4b);g.drawString(font,name,151,52,TEXT,false);
            g.drawString(font,"아이템레벨",205,83,TEXT,false);g.drawString(font,val(values,0),422,83,0xffe6c588,false);
            g.drawString(font,"무기 1개 + 방어구 4개의 평균",205,106,MUTED,false);
            g.drawString(font,"미착용 부위는 0으로 계산",205,121,MUTED,false);
            g.renderItem(menu.getSlot(1).getItem(),208,148);
            line(g,"◇ 전투력",val(values,1),24,198);
            g.fill(17,219,503,220,0xff385367);
            line(g,"체력",val(values,2),25,235);line(g,"방어력",val(values,3),275,235);
            line(g,"치명타 확률",val(values,4)+"%",25,259);line(g,"이동 속도",val(values,5)+"%",275,259);
            line(g,"버프 지속 시간",val(values,6)+"%",25,283);line(g,"쿨타임 감소",val(values,7)+"%",275,283);
            g.drawString(font,"기초 스탯",25,315,TEXT,false);g.fill(17,332,503,333,0xff385367);
            line(g,"· 힘",val(values,8),25,346);line(g,"· 민첩",val(values,9),275,346);
            line(g,"· 지능",val(values,10),25,369);line(g,"· 행운",val(values,11),275,369);
            g.drawCenteredString(font,"ESC 닫기",W/2,394,MUTED);
        } finally{g.pose().popPose();}
        if(minecraft!=null&&minecraft.player!=null){double x=(mx-left)/scale,y=(my-top)/scale;
            var weapon=menu.getSlot(1).getItem();
            if(y>=148&&y<164&&x>=208&&x<224&&!weapon.isEmpty())g.renderTooltip(font,weapon,mx,my);
            else if(y>=233&&y<245&&x>=275&&x<440)g.renderTooltip(font,Component.literal("받는 피해 "+val(data(),15)+"% 감소"),mx,my);
        }
    }
    @Override public boolean mouseClicked(double x,double y,int button){if((x-left)/scale>W-35&&(y-top)/scale<33)onClose();return true;}
    @Override public boolean keyPressed(int key,int scan,int mods){if(key==GLFW.GLFW_KEY_ESCAPE)onClose();return true;}
    @Override public boolean mouseReleased(double x,double y,int button){return true;}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return true;}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){return true;}
}
