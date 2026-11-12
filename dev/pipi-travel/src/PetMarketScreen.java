package dev.pipi.travel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.lwjgl.glfw.GLFW;
/** 펫 가판대. 서버 프로토콜: 슬롯 0-4 판매 목록(lore[0]=가격), 5 대금(lore[0]=금액), 9-38 내 펫 창고, 48/50 페이지, 53 모드("주인"/"구매자", lore[0]=주인 이름).
 *  명령: pipilife market page|list|stop|collect|sell|buy (자세한 형식은 PipiLife.marketCommand). */
final class PetMarketScreen extends ContainerScreen {
 static final String TITLE="삐삐서버 · 펫 가판대";float scale,left,top;long lastSend;
 int selRow=-1,selSlot=-1;String price="";boolean sellConfirm;
 PetMarketScreen(ChestMenu m,Inventory i,Component t){super(m,i,t);}
 protected void init(){scale=Math.max(.01f,Math.min((width-20f)/620,(height-20f)/340));left=(width-620*scale)/2;top=(height-340*scale)/2;}
 boolean mine(){return menu.getSlot(53).getItem().getHoverName().getString().equals("주인");}
 String lore(int slot,int line){var lines=menu.getSlot(slot).getItem().getOrDefault(DataComponents.LORE,ItemLore.EMPTY).lines();return line<lines.size()?lines.get(line).getString():"";}
 String name(int slot){return menu.getSlot(slot).getItem().getHoverName().getString();}
 boolean has(int slot){return !menu.getSlot(slot).getItem().isEmpty();}
 void panel(GuiGraphics g,int x,int y,int w,int h){g.fill(x,y,x+w,y+h,0xff476276);g.fill(x+1,y+1,x+w-1,y+h-1,0xff142230);}
 void button(GuiGraphics g,String text,int x,int y,int w,int h,boolean on){panel(g,x,y,w,h);g.drawCenteredString(font,text,x+w/2,y+(h-8)/2,on?0xffe4eaf0:0xff6c7a86);}
 int priceValue(){try{long v=Long.parseLong(price);return v>=1&&v<=100000000?(int)v:-1;}catch(NumberFormatException e){return -1;}}
 String coins(String n){try{return String.format(java.util.Locale.ROOT,"%,d",Long.parseLong(n.trim()))+" 골드";}catch(NumberFormatException e){return n+" 골드";}}
 public void render(GuiGraphics g,int mx,int my,float delta){
  g.fill(0,0,width,height,0xb8081018);g.pose().pushPose();
  try{
   g.pose().translate(left,top,0);g.pose().scale(scale,scale,1);panel(g,0,0,620,340);
   boolean mine=mine();
   g.drawString(font,mine?"펫 가판대":"펫 가판대 · "+lore(53,0)+"님의 가판대",20,16,0xffe3c385,false);
   panel(g,12,38,300,264);
   for(int i=0;i<5;i++){
    int y=46+i*50;panel(g,20,y,284,46);
    if(mine&&selRow==i&&!has(i)){g.fill(20,y,304,y+2,0xffe3c385);g.fill(20,y+44,304,y+46,0xffe3c385);}
    if(has(i)){
     g.renderItem(menu.getSlot(i).getItem(),28,y+14);g.drawString(font,name(i),52,y+9,0xffdfe7ee,false);
     g.drawString(font,coins(lore(i,0)),52,y+27,0xffe3c385,false);
     button(g,mine?"판매 중지":"구매",228,y+11,68,24,true);
    }else g.drawString(font,mine?(selRow==i?"이 칸에 등록할 펫을 고르세요":"빈 칸 (클릭하여 선택)"):"비어 있음",30,y+19,0xff6c7a86,false);
   }
   if(mine){
    panel(g,320,8,180,24);g.drawString(font,"대금 "+coins(lore(5,0)),328,16,0xffe3c385,false);
    button(g,"대금수령",508,8,100,24,!lore(5,0).equals("0"));
    panel(g,320,38,288,264);
    for(int i=0;i<30;i++){
     int x=326+(i%6)*47,y=44+(i/6)*42;panel(g,x,y,44,39);
     if(has(9+i)){
      if(selSlot==9+i)g.fill(x+1,y+1,x+43,y+38,0xff2a4a62);
      g.renderItem(menu.getSlot(9+i).getItem(),x+14,y+2);g.drawCenteredString(font,font.plainSubstrByWidth(name(9+i),42),x+22,y+22,0xffdfe7ee);
     }
    }
    g.drawString(font,"가격",328,276,0xffa5baca,false);panel(g,356,270,110,22);
    g.drawString(font,price.isEmpty()?"1~100,000,000":price+((System.currentTimeMillis()/500%2==0)?"_":""),362,277,price.isEmpty()?0xff6c7a86:0xffe4eaf0,false);
    button(g,"판매 시작",474,270,126,22,selRow>=0&&!has(Math.max(selRow,0))&&selSlot>=0&&priceValue()>0);
    if(has(48))button(g,"이전",20,310,70,24,true);if(has(50))button(g,"다음",100,310,70,24,true);
    button(g,sellConfirm?"정말 판매 (되돌릴 수 없음)":"상점에 바로 판매",180,310,200,24,selSlot>=0);
   }
   button(g,"닫기",520,310,80,24,true);
  }finally{g.pose().popPose();}
 }
 void send(String cmd){long now=System.currentTimeMillis();if(now-lastSend<300||minecraft==null||minecraft.getConnection()==null)return;lastSend=now;minecraft.getConnection().sendCommand("pipilife market "+cmd);}
 boolean in(double x,double y,int rx,int ry,int rw,int rh){return x>=rx&&x<rx+rw&&y>=ry&&y<ry+rh;}
 public boolean mouseClicked(double mx,double my,int b){
  if(b!=0)return true;double x=(mx-left)/scale,y=(my-top)/scale;boolean mine=mine();
  if(in(x,y,520,310,80,24)){onClose();return true;}
  for(int i=0;i<5;i++){int ry=46+i*50;
   if(!in(x,y,20,ry,284,46))continue;
   if(has(i)){if(in(x,y,228,ry+11,68,24)){if(mine)send("stop "+i);else send("buy "+i+" "+lore(i,0));}}
   else if(mine){selRow=i;}
   return true;
  }
  if(!mine)return true;
  if(in(x,y,508,8,100,24)){if(!lore(5,0).equals("0"))send("collect");return true;}
  for(int i=0;i<30;i++){int gx=326+(i%6)*47,gy=44+(i/6)*42;if(in(x,y,gx,gy,44,39)){if(has(9+i)){selSlot=9+i;sellConfirm=false;}return true;}}
  if(in(x,y,474,270,126,22)){int p=priceValue();if(selRow>=0&&!has(selRow)&&selSlot>=0&&p>0)send("list "+selRow+" "+selSlot+" "+p);return true;}
  if(in(x,y,20,310,70,24)&&has(48)){send("page "+pageNumber(-1));return true;}
  if(in(x,y,100,310,70,24)&&has(50)){send("page "+pageNumber(1));return true;}
  if(in(x,y,180,310,200,24)&&selSlot>=0){if(sellConfirm)send("sell "+selSlot);else sellConfirm=true;return true;}
  return true;
 }
 int pageNumber(int delta){int cur=1;try{cur=Integer.parseInt(lore(52,0));}catch(NumberFormatException e){}return Math.max(1,cur+delta);}
 public boolean charTyped(char c,int mod){if(mine()&&c>='0'&&c<='9'&&price.length()<9){price+=c;return true;}return true;}
 public boolean keyPressed(int key,int sc,int mod){
  if(key==GLFW.GLFW_KEY_ESCAPE)onClose();
  else if(key==GLFW.GLFW_KEY_BACKSPACE&&!price.isEmpty())price=price.substring(0,price.length()-1);
  return true;
 }
}
