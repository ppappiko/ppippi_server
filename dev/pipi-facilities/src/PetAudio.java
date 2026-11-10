package dev.pipi.facilities;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.bus.api.IEventBus;
public final class PetAudio {
 static final String[] NAMES={"farm","mine","lumber","eat","craft"};
 static final DeferredRegister<SoundEvent> SOUNDS=DeferredRegister.create(Registries.SOUND_EVENT,"pipifacilities");
 static final java.util.List<DeferredHolder<SoundEvent,SoundEvent>> EVENTS=new java.util.ArrayList<>();
 static {for(String name:NAMES)EVENTS.add(SOUNDS.register("work_"+name,()->SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("pipifacilities","work_"+name))));}
 static void init(IEventBus bus){SOUNDS.register(bus);}
 static SoundEvent event(int type){return EVENTS.get(Math.clamp(type,0,4)).get();}
}
