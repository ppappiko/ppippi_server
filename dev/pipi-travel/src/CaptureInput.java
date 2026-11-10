package dev.pipi.travel;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Only input intent is sent. Server owns item use, projectile, target, HP and capture rolls. */
final class CaptureInput {
    private static boolean holding;
    private static double zoom;
    private static int target=-1;
    private static String chance="";
    private static long received;
    static void receive(net.neoforged.neoforge.client.event.ClientChatReceivedEvent.System event){
        String text=event.getMessage().getString();if(!text.startsWith("PIPI_CAPTURE_AIM:"))return;
        event.setCanceled(true);try{String[] fields=text.split(":");target=Integer.parseInt(fields[1]);double p=Double.parseDouble(fields[2]);chance=String.format(java.util.Locale.ROOT,"%.1f%%",Math.clamp(p,0,100));received=System.nanoTime();}catch(RuntimeException e){target=-1;}
    }
    static void fov(net.neoforged.neoforge.client.event.ViewportEvent.ComputeFov event){if(event.usedConfiguredFov())event.setFOV(event.getFOV()*(1-.28*zoom));}
    static void draw(net.minecraft.client.gui.GuiGraphics g){
        Minecraft mc=Minecraft.getInstance();if(!holding||mc.screen!=null||mc.options.hideGui)return;
        int x=g.guiWidth()/2,y=g.guiHeight()/2;boolean aimed=target>=0&&System.nanoTime()-received<450_000_000L&&mc.level!=null&&mc.level.getEntity(target)!=null;
        int color=aimed?0xFFF1C76B:0xFFB9CBD5;int r=34;
        for(int i=0;i<180;i++){double a=Math.PI*2*i/180;int px=x+(int)Math.round(Math.cos(a)*r),py=y+(int)Math.round(Math.sin(a)*r);g.fill(px,py,px+1,py+1,0x704E5964);}
        int segments=aimed?Math.round(Float.parseFloat(chance.replace("%",""))*1.8f):0;
        for(int i=0;i<segments;i++){double a=-Math.PI/2+Math.PI*2*i/180;int px=x+(int)Math.round(Math.cos(a)*r),py=y+(int)Math.round(Math.sin(a)*r);g.fill(px,py,px+2,py+2,color);}
        for(int sx:new int[]{-1,1})for(int sy:new int[]{-1,1}){int xx=x+sx*22,yy=y+sy*18;g.fill(Math.min(xx,xx-sx*7),yy,Math.max(xx,xx-sx*7)+1,yy+1,color);g.fill(xx,Math.min(yy,yy-sy*7),xx+1,Math.max(yy,yy-sy*7)+1,color);}
        g.drawCenteredString(mc.font,aimed?chance:"조준",x,y-4,color);
        g.drawCenteredString(mc.font,aimed?"포획 확률":"포획 가능한 펫을 조준하세요",x,y+44,0xFFDEE5E9);
        g.drawCenteredString(mc.font,"충전 "+Math.min(100,ticks*5)+"% · 놓으면 투척",x,y+70,0xFFDEE5E9);
        int width=(int)(64*Math.min(1,ticks/20.0));g.fill(x-32,y+60,x+32,y+62,0x904E5964);g.fill(x-32,y+60,x-32+width,y+62,color);
    }

    private static int ticks;
    private static int slot=-1;
    private static net.minecraft.client.multiplayer.ClientPacketListener connection;
    private static boolean trap(ItemStack item){
        var data=item.get(DataComponents.CUSTOM_DATA);if(data==null)return false;
        CompoundTag tag=data.copyTag();
        return tag.getCompound("PublicBukkitValues").contains("pipilife:capture_trap");
    }
    static void tick(ClientTickEvent.Post event){
        zoom+=( (holding?1:0)-zoom)*.25;
        Minecraft mc=Minecraft.getInstance();var current=mc.getConnection();
        if(current!=connection){holding=false;zoom=0;target=-1;ticks=0;slot=-1;connection=current;}
        if(mc.player==null||current==null){holding=false;return;}
        boolean valid=mc.isWindowActive()&&mc.screen==null&&!mc.player.isDeadOrDying()&&trap(mc.player.getMainHandItem());
        boolean down=valid&&mc.options.keyUse.isDown();
        if(holding&&(!valid||mc.player.getInventory().selected!=slot)){
            current.sendCommand("pipilife capture cancel");holding=false;ticks=0;return;
        }
        if(down&&!holding){holding=true;target=-1;slot=mc.player.getInventory().selected;ticks=0;current.sendCommand("pipilife capture start");}
        else if(down&&holding){if(++ticks%10==0)current.sendCommand("pipilife capture keep");}
        else if(holding){current.sendCommand("pipilife capture release");holding=false;ticks=0;slot=-1;}
    }
}
