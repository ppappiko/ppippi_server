package dev.pipi.facilities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import java.util.*;
@EventBusSubscriber(modid="pipifacilities",value=Dist.CLIENT)
public final class PetSoundPlayer {
 private static final Map<String,Loop> ACTIVE=new HashMap<>();
 private record Station(int type,double x,double y,double z){}
 private static final Map<String,Station> STATIONS=new HashMap<>();
 private static final Map<String,StationLoop> STATION_LOOPS=new HashMap<>();
 private static long received;private static int clock;
 @SubscribeEvent public static void status(net.neoforged.neoforge.client.event.ClientChatReceivedEvent.System event){String text=event.getMessage().getString();if(!text.startsWith("PIPI_WORK_AUDIO:"))return;event.setCanceled(true);STATIONS.clear();received=System.currentTimeMillis();String data=text.substring("PIPI_WORK_AUDIO:".length());if(data.isBlank())return;for(String record:data.split("\\|")){String[] fields=record.split("~");try{if(fields.length==5){int type=Integer.parseInt(fields[1]);if(type>=0&&type<5)STATIONS.put(fields[0],new Station(type,Double.parseDouble(fields[2]),Double.parseDouble(fields[3]),Double.parseDouble(fields[4])));}}catch(NumberFormatException ignored){}}}
 @SubscribeEvent public static void reload(net.neoforged.neoforge.client.event.sound.SoundEngineLoadEvent event){ACTIVE.clear();STATION_LOOPS.clear();}
 private static void stations(Minecraft mc){clock++;if(mc.level==null||mc.player==null||System.currentTimeMillis()-received>1800)STATIONS.clear();for(String key:new ArrayList<>(STATION_LOOPS.keySet()))if(!STATIONS.containsKey(key)){mc.getSoundManager().stop(STATION_LOOPS.remove(key));}for(var entry:STATIONS.entrySet()){var loop=STATION_LOOPS.get(entry.getKey());if(loop==null||loop.isStopped()||(clock-loop.started>30&&!mc.getSoundManager().isActive(loop))){if(loop!=null)mc.getSoundManager().stop(loop);loop=new StationLoop(entry.getKey(),entry.getValue());STATION_LOOPS.put(entry.getKey(),loop);mc.getSoundManager().play(loop);}}}
 private static final class StationLoop extends AbstractTickableSoundInstance {
  final String key;final int type,started;
  StationLoop(String key,Station station){super(PetAudio.event(station.type()),SoundSource.NEUTRAL,RandomSource.create());this.key=key;type=station.type();started=clock;looping=true;delay=0;volume=.8f;pitch=1;attenuation=Attenuation.LINEAR;x=station.x();y=station.y()+.3;z=station.z();}
  @Override public void tick(){var station=STATIONS.get(key);if(station==null||station.type()!=type||Minecraft.getInstance().level==null||System.currentTimeMillis()-received>1800){stop();return;}x=station.x();y=station.y()+.3;z=station.z();}
 }

 private static int soundType(PipiPets.Pet p){return p.action()==3?3:-1;}
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){var mc=Minecraft.getInstance();stations(mc);if(mc.level==null||mc.player==null){for(var loop:ACTIVE.values())mc.getSoundManager().stop(loop);ACTIVE.clear();return;}
  Map<String,PipiPets.Pet> wanted=new HashMap<>();
  for(var entity:mc.level.entitiesForRendering())if(entity instanceof PipiPets.Pet pet){int type=soundType(pet);if(type<0||pet.isRemoved()||pet.distanceToSqr(mc.player)>144)continue;String key=type==3?pet.getUUID()+":eat":pet.soundGroup()+":"+type;var old=wanted.get(key);if(old==null||old.distanceToSqr(mc.player)>pet.distanceToSqr(mc.player))wanted.put(key,pet);}
  for(String key:new ArrayList<>(ACTIVE.keySet()))if(!wanted.containsKey(key)){mc.getSoundManager().stop(ACTIVE.remove(key));}
  for(var item:wanted.entrySet()){Loop loop=ACTIVE.get(item.getKey());if(loop==null||loop.pet!=item.getValue()||loop.isStopped()||(clock-loop.started>30&&!mc.getSoundManager().isActive(loop))){if(loop!=null)mc.getSoundManager().stop(loop);loop=new Loop(item.getValue(),soundType(item.getValue()));ACTIVE.put(item.getKey(),loop);mc.getSoundManager().play(loop);}}
 }
 private static final class Loop extends AbstractTickableSoundInstance {
  final PipiPets.Pet pet;final int type,started;
  Loop(PipiPets.Pet pet,int type){super(PetAudio.event(type),SoundSource.NEUTRAL,RandomSource.create());this.pet=pet;this.type=type;started=clock;looping=true;delay=0;volume=.45f;pitch=1;attenuation=Attenuation.LINEAR;x=pet.getX();y=pet.getY()+.3;z=pet.getZ();}
  @Override public void tick(){var mc=Minecraft.getInstance();if(pet.isRemoved()||!pet.isAlive()||mc.player==null||mc.level!=pet.level()||soundType(pet)!=type||pet.distanceToSqr(mc.player)>169){stop();return;}x=pet.getX();y=pet.getY()+.3;z=pet.getZ();}
 }
}
