package dev.pipi.facilities;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.registries.*;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.bus.api.IEventBus;
public final class PipiPets {
 static final DeferredRegister<EntityType<?>> TYPES=DeferredRegister.create(Registries.ENTITY_TYPE,"pipifacilities");
 static final DeferredHolder<EntityType<?>,EntityType<Pet>> SPARROW=register("pet_sparrow",.5f,.65f);
 static final DeferredHolder<EntityType<?>,EntityType<Pet>> FROG=register("pet_frog",.65f,.55f);
 static final DeferredHolder<EntityType<?>,EntityType<Pet>> CAT=register("pet_cat",.55f,.75f);
 static final DeferredHolder<EntityType<?>,EntityType<Pet>> RABBIT=register("pet_rabbit",.55f,1.05f);
 static final DeferredHolder<EntityType<?>,EntityType<Pet>> FOX=register("pet_fox",.6f,.8f);
 static final DeferredHolder<EntityType<?>,EntityType<Pet>> REDPANDA=register("pet_redpanda",.6f,.75f);
 static final DeferredHolder<EntityType<?>,EntityType<Pet>> OTTER=register("pet_otter",.65f,.85f);
 static final DeferredHolder<EntityType<?>,EntityType<Pet>> TORTOISE=register("pet_tortoise",.85f,.65f);
 static final DeferredHolder<EntityType<?>,EntityType<Pet>> RACCOON=register("pet_raccoon",.7f,.85f);
 static final DeferredHolder<EntityType<?>,EntityType<Pet>> HORNRABBIT=register("pet_hornrabbit",.65f,.9f);
 static final DeferredHolder<EntityType<?>,EntityType<Pet>> MOLE=register("pet_mole",.75f,.65f);
 static final DeferredHolder<EntityType<?>,EntityType<Pet>> THUNDERSHEEP=register("pet_thundersheep",.8f,.85f);
 static final DeferredHolder<EntityType<?>,EntityType<Pet>> MOONBEAR=register("pet_moonbear",.7f,.85f);
 static final DeferredHolder<EntityType<?>,EntityType<Pet>> FERRET=register("pet_ferret",.65f,.7f);
 static final DeferredHolder<EntityType<?>,EntityType<Pet>> ANGELPIG=register("pet_angelpig",.75f,.95f);
 static final DeferredHolder<EntityType<?>,EntityType<Pet>> SUNLIZARD=register("pet_sunlizard",.7f,.5f);
 static final DeferredHolder<EntityType<?>,EntityType<Pet>> IMUGI=register("pet_imugi",2.0f,1.2f);
 static final DeferredHolder<EntityType<?>,EntityType<Pet>> VAMPBAT=register("pet_vampbat",.6f,.7f);
 static DeferredHolder<EntityType<?>,EntityType<Pet>> register(String id,float width,float height){return TYPES.register(id,()->EntityType.Builder.of(Pet::new,MobCategory.CREATURE).sized(width*1.56f,height*1.56f).clientTrackingRange(10).build("pipifacilities:"+id));}
 static void init(IEventBus bus){TYPES.register(bus);PetAudio.init(bus);bus.addListener(PipiPets::attributes);}
 static void attributes(EntityAttributeCreationEvent e){for(var t:new EntityType[]{SPARROW.get(),FROG.get(),CAT.get(),RABBIT.get(),FOX.get(),REDPANDA.get(),OTTER.get(),TORTOISE.get(),RACCOON.get(),HORNRABBIT.get(),MOLE.get(),THUNDERSHEEP.get(),MOONBEAR.get(),FERRET.get(),ANGELPIG.get(),SUNLIZARD.get(),VAMPBAT.get()})e.put(t,Mob.createMobAttributes().add(Attributes.MAX_HEALTH,24).add(Attributes.MOVEMENT_SPEED,.18).add(Attributes.FOLLOW_RANGE,16).build());e.put(IMUGI.get(),Mob.createMobAttributes().add(Attributes.MAX_HEALTH,24).add(Attributes.MOVEMENT_SPEED,.324).add(Attributes.STEP_HEIGHT,1.0).add(Attributes.FOLLOW_RANGE,16).build());}
 public static Object spawn(Object level,String species,double x,double y,double z){ServerLevel world=(ServerLevel)level;EntityType<Pet> type=switch(species){case "sparrow"->SPARROW.get();case "frog"->FROG.get();case "cat"->CAT.get();case "rabbit"->RABBIT.get();case "fox"->FOX.get();case "redpanda"->REDPANDA.get();case "otter"->OTTER.get();case "tortoise"->TORTOISE.get();case "raccoon"->RACCOON.get();case "hornrabbit"->HORNRABBIT.get();case "mole"->MOLE.get();case "thundersheep"->THUNDERSHEEP.get();case "moonbear"->MOONBEAR.get();case "ferret"->FERRET.get();case "angelpig"->ANGELPIG.get();case "sunlizard"->SUNLIZARD.get();case "imugi"->IMUGI.get();case "vampbat"->VAMPBAT.get();default->throw new IllegalArgumentException(species);};Pet pet=type.create(world);pet.moveTo(x,y,z,0,0);world.addFreshEntity(pet);return pet;}
 public static void worker(Object entity,int state,double x,double y,double z,int task,String group){if(entity instanceof Pet pet){pet.getEntityData().set(Pet.TASK,task);pet.getEntityData().set(Pet.GROUP,group);pet.worker=true;pet.setNoGravity(true);pet.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);pet.desired=state;pet.tx=x;pet.ty=y;pet.tz=z;}}
 public static void face(Object entity,double x,double z){if(entity instanceof Pet pet){pet.focusX=x;pet.focusZ=z;}}
 public static boolean reached(Object entity,double x,double y,double z){return entity instanceof Pet pet&&pet.reached(x,y,z);}
 public static final class Pet extends PathfinderMob {
  private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> ACTION=net.minecraft.network.syncher.SynchedEntityData.defineId(Pet.class,net.minecraft.network.syncher.EntityDataSerializers.INT);
  private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> TASK=net.minecraft.network.syncher.SynchedEntityData.defineId(Pet.class,net.minecraft.network.syncher.EntityDataSerializers.INT);
  private static final net.minecraft.network.syncher.EntityDataAccessor<String> GROUP=net.minecraft.network.syncher.SynchedEntityData.defineId(Pet.class,net.minecraft.network.syncher.EntityDataSerializers.STRING);
  public int task(){return entityData.get(TASK);}
  public String soundGroup(){return entityData.get(GROUP);}
  private boolean worker;private int desired;private double tx,ty,tz;private double focusX=Double.NaN,focusZ;
  @Override protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder){super.defineSynchedData(builder);builder.define(ACTION,0);builder.define(TASK,0);builder.define(GROUP,"");}
  public int action(){return entityData.get(ACTION);}
  private java.util.List<PetRoutes.Cell> route=java.util.List.of();private int waypoint,repath,stuck;private double planX=Double.NaN,planY,planZ,lastX,lastZ;private boolean arrived;
  public boolean reached(double x,double y,double z){return arrived&&Math.abs(tx-x)<.05&&Math.abs(ty-y)<.05&&Math.abs(tz-z)<.05;}
  private boolean clear(PetRoutes.Cell p){
   var pos=new net.minecraft.core.BlockPos(p.x(),p.y(),p.z());if(!level().hasChunkAt(pos)||!level().getFluidState(pos).isEmpty())return false;
   var below=pos.below();if(level().getBlockState(below).getCollisionShape(level(),below).isEmpty())return false;
   var block=level().getBlockState(below).getBlock();if(block==net.minecraft.world.level.block.Blocks.MAGMA_BLOCK||block==net.minecraft.world.level.block.Blocks.CAMPFIRE||block==net.minecraft.world.level.block.Blocks.CACTUS)return false;
   double half=getBbWidth()/2+.04;var box=new net.minecraft.world.phys.AABB(p.x()+.5-half,p.y()+.02,p.z()+.5-half,p.x()+.5+half,p.y()+getBbHeight()-.02,p.z()+.5+half);
   return !level().getBlockCollisions(this,box).iterator().hasNext();
  }
  @Override public void tick(){super.tick();
   if(level().isClientSide&&getType()==THUNDERSHEEP.get()&&action()==2&&tickCount%3==0){for(int i=0;i<2;i++){double angle=random.nextDouble()*Math.PI*2;level().addParticle(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,getX()+Math.cos(angle)*getBbWidth()*.6,getY()+getBbHeight()*.55+random.nextDouble()*.25,getZ()+Math.sin(angle)*getBbWidth()*.6,Math.cos(angle)*.035,.015,Math.sin(angle)*.035);}}
   if(level().isClientSide||!worker)return;
   boolean changed=Double.isNaN(planX)||Math.abs(tx-planX)>.05||Math.abs(ty-planY)>.05||Math.abs(tz-planZ)>.05;
   if(changed||(!arrived&&--repath<=0)){
    planX=tx;planY=ty;planZ=tz;arrived=false;waypoint=0;stuck=0;repath=60;
    java.util.Map<PetRoutes.Cell,Boolean> occupancy=new java.util.HashMap<>();
    route=PetRoutes.find(new PetRoutes.Cell(net.minecraft.util.Mth.floor(getX()),net.minecraft.util.Mth.floor(getY()+.1),net.minecraft.util.Mth.floor(getZ())),new PetRoutes.Cell(net.minecraft.util.Mth.floor(tx),net.minecraft.util.Mth.floor(ty+.1),net.minecraft.util.Mth.floor(tz)),cell->occupancy.computeIfAbsent(cell,this::clear));
   }
   if(waypoint<route.size()){
    var point=route.get(waypoint);double dx=point.x()+.5-getX(),dy=point.y()-getY(),dz=point.z()+.5-getZ(),dist=Math.hypot(dx,dz);
    if(dist<.13&&Math.abs(dy)<.3){waypoint++;if(waypoint==route.size())arrived=true;}
    else {double step=Math.min(getType()==IMUGI.get()?.126:.07,dist);move(MoverType.SELF,new net.minecraft.world.phys.Vec3(dist>.001?dx/dist*step:0,Math.clamp(dy,-.12,.12),dist>.001?dz/dist*step:0));float angle=(float)(Math.toDegrees(Math.atan2(dz,dx))-90);setYRot(net.minecraft.util.Mth.approachDegrees(getYRot(),angle,10));yBodyRot=getYRot();if(Math.hypot(getX()-lastX,getZ()-lastZ)<.003)stuck++;else stuck=0;if(stuck>20){route=java.util.List.of();waypoint=0;repath=10;stuck=0;}}
   }
   if(arrived&&!Double.isNaN(focusX)){double dx=focusX-getX(),dz=focusZ-getZ();if(dx*dx+dz*dz>.0025){float angle=(float)(Math.toDegrees(Math.atan2(dz,dx))-90);setYRot(net.minecraft.util.Mth.approachDegrees(getYRot(),angle,10));yBodyRot=getYRot();setYHeadRot(getYRot());}}
   lastX=getX();lastZ=getZ();entityData.set(ACTION,arrived?desired:waypoint<route.size()?1:0);
  }


  public Pet(EntityType<? extends PathfinderMob> type,Level level){super(type,level);}
  @Override protected void registerGoals(){goalSelector.addGoal(0,new FloatGoal(this));goalSelector.addGoal(1,new PanicGoal(this,1.2));goalSelector.addGoal(3,new WaterAvoidingRandomStrollGoal(this,.8));goalSelector.addGoal(4,new LookAtPlayerGoal(this,Player.class,6));goalSelector.addGoal(5,new RandomLookAroundGoal(this));}
  @Override public boolean removeWhenFarAway(double distance){return false;}
 }
}
