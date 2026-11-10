package dev.pipi.travel;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.component.ItemLore;
import org.lwjgl.glfw.GLFW;
final class PetMarketScreen extends ContainerScreen {
 static final String TITLE="삐삐서버 · 펫 가판대";float scale,left,top;boolean pending;
 PetMarketScreen(ChestMenu m,Inventory i,Component t){super(m,i,t);}
 protected void init(){scale=Math.max(.01f,Math.min((width-20f)/620,(height-20f)/340));left=(width-620*scale)/2;top=(height-340*scale)/2;}
 void panel(GuiGraphics g,int x,int y,int w,int h){g.fill(x,y,x+w,y+h,0xff476276);g.fill(x+1,y+1,x+w-1,y+h-1,0xff142230);}
 String name(int slot){return menu.getSlot(slot).getItem().getHoverName().getString();}
 void button(GuiGraphics g,int slot,int x,int y,int w){if(menu.getSlot(slot).getItem().isEmpty())return;panel(g,x,y,w,24);g.drawCenteredString(font,name(slot),x+w/2,y+8,0xffe4eaf0);}
 public void render(GuiGraphics g,int mx,int my,float delta){g.fill(0,0,width,height,0xb8081018);g.pose().pushPose();try{g.pose().translate(left,top,0);g.pose().scale(scale,scale,1);panel(g,0,0,620,340);g.drawString(font,"펫 가판대 · 판매",20,16,0xffe3c385,false);panel(g,12,38,405,264);panel(g,429,38,179,264);
 for(int i=0;i<30;i++){int x=20+(i%6)*65,y=48+(i/6)*49;panel(g,x,y,60,45);var item=menu.getSlot(i).getItem();if(!item.isEmpty()){g.renderItem(item,x+22,y+3);g.drawCenteredString(font,font.plainSubstrByWidth(name(i),57),x+30,y+27,0xffdfe7ee);}}
 if(!menu.getSlot(31).getItem().isEmpty()){g.drawCenteredString(font,name(31),518,65,0xffe4eaf0);g.pose().pushPose();g.pose().translate(487,85,0);g.pose().scale(4,4,1);g.renderItem(menu.getSlot(31).getItem(),0,0);g.pose().popPose();int yy=169;for(var line:menu.getSlot(31).getItem().getOrDefault(DataComponents.LORE,ItemLore.EMPTY).lines()){g.drawString(font,font.plainSubstrByWidth(line.getString(),155),440,yy,0xffa5baca,false);yy+=12;}g.drawCenteredString(font,name(32),518,204,0xffe3c385);button(g,45,445,240,146);}
 button(g,48,20,310,70);button(g,50,100,310,70);button(g,49,520,310,80);
 if(!menu.getSlot(46).getItem().isEmpty()){g.fill(0,0,620,340,0xcc060b12);panel(g,145,110,330,125);g.drawCenteredString(font,"펫을 판매하면 되돌릴 수 없습니다.",310,132,0xffe6c58c);button(g,46,166,183,135);button(g,47,319,183,135);}
 }finally{g.pose().popPose();}}
 void click(int slot){if(pending||menu.getSlot(slot).getItem().isEmpty()||minecraft==null||minecraft.getConnection()==null)return;pending=true;minecraft.getConnection().send(new ServerboundContainerClickPacket(menu.containerId,menu.getStateId(),slot,0,ClickType.PICKUP,menu.getCarried().copy(),Int2ObjectMaps.emptyMap()));}
 public boolean mouseClicked(double mx,double my,int b){if(b!=0)return true;double x=(mx-left)/scale,y=(my-top)/scale;if(!menu.getSlot(46).getItem().isEmpty()){if(y>=183&&y<207){if(x>=166&&x<301)click(46);else if(x>=319&&x<454)click(47);}return true;}if(x>=20&&x<410&&y>=48&&y<293){int c=(int)(x-20)/65,r=(int)(y-48)/49;if((x-20)%65<60&&(y-48)%49<45)click(r*6+c);}else if(y>=240&&y<264&&x>=445&&x<591)click(45);else if(y>=310&&y<334){if(x>=20&&x<90)click(48);else if(x>=100&&x<170)click(50);else if(x>=520&&x<600)onClose();}return true;}
 public boolean keyPressed(int key,int sc,int mod){if(key==GLFW.GLFW_KEY_ESCAPE)onClose();return true;}
}
