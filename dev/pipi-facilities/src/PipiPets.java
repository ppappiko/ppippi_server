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
 static final DeferredHolder<EntityType<?>,EntityType<Pet>> COW=register("pet_cow",.64f,.76f);
 /** Per-species model scale (also scales the hitbox height; hitbox width is capped below 1 block so worker pets fit 1-wide paths; imugi's 2x is render-only, its hitbox is already sized). Mirrors dev/art/pet-sizes.mjs. */
 private static final class Sizes{static final java.util.Map<String,Float> SIZE=java.util.Map.ofEntries(java.util.Map.entry("rabbit",0.95f),java.util.Map.entry("sparrow",1.2f),java.util.Map.entry("hornrabbit",1.2f),java.util.Map.entry("cat",1.25f),java.util.Map.entry("mole",1.3f),java.util.Map.entry("fox",1.3f),java.util.Map.entry("otter",1.3f),java.util.Map.entry("ferret",1.35f),java.util.Map.entry("cow",1.35f),java.util.Map.entry("angelpig",1.35f),java.util.Map.entry("vampbat",1.4f),java.util.Map.entry("redpanda",1.4f),java.util.Map.entry("frog",1.5f),java.util.Map.entry("raccoon",1.5f),java.util.Map.entry("thundersheep",1.55f),java.util.Map.entry("sunlizard",1.7f),java.util.Map.entry("tortoise",1.75f),java.util.Map.entry("imugi",2f),java.util.Map.entry("moonbear",2.1f));}
 public static float size(String skin){return Sizes.SIZE.getOrDefault(skin,1f);}
 static DeferredHolder<EntityType<?>,EntityType<Pet>> register(String id,float width,float height){String skin=id.substring(4);float k=skin.equals("imugi")?1f:size(skin);return TYPES.register(id,()->EntityType.Builder.of(Pet::new,MobCategory.CREATURE).sized(skin.equals("imugi")?width*1.56f:Math.min(width*1.56f*k,.95f),height*1.56f*k).clientTrackingRange(10).build("pipifacilities:"+id));}
 static void init(IEventBus bus){TYPES.register(bus);PetAudio.init(bus);bus.addListener(PipiPets::attributes);}
 static void attributes(EntityAttributeCreationEvent e){for(var t:new EntityType[]{SPARROW.get(),FROG.get(),CAT.get(),RABBIT.get(),FOX.get(),REDPANDA.get(),OTTER.get(),TORTOISE.get(),RACCOON.get(),HORNRABBIT.get(),MOLE.get(),THUNDERSHEEP.get(),MOONBEAR.get(),FERRET.get(),ANGELPIG.get(),SUNLIZARD.get(),VAMPBAT.get(),COW.get()})e.put(t,Mob.createMobAttributes().add(Attributes.MAX_HEALTH,24).add(Attributes.MOVEMENT_SPEED,.18).add(Attributes.FOLLOW_RANGE,16).build());e.put(IMUGI.get(),Mob.createMobAttributes().add(Attributes.MAX_HEALTH,24).add(Attributes.MOVEMENT_SPEED,.324).add(Attributes.STEP_HEIGHT,1.0).add(Attributes.FOLLOW_RANGE,16).build());}
 public static Object spawn(Object level,String species,double x,double y,double z){ServerLevel world=(ServerLevel)level;EntityType<Pet> type=switch(species){case "sparrow"->SPARROW.get();case "frog"->FROG.get();case "cat"->CAT.get();case "rabbit"->RABBIT.get();case "fox"->FOX.get();case "redpanda"->REDPANDA.get();case "otter"->OTTER.get();case "tortoise"->TORTOISE.get();case "raccoon"->RACCOON.get();case "hornrabbit"->HORNRABBIT.get();case "mole"->MOLE.get();case "thundersheep"->THUNDERSHEEP.get();case "moonbear"->MOONBEAR.get();case "ferret"->FERRET.get();case "angelpig"->ANGELPIG.get();case "sunlizard"->SUNLIZARD.get();case "imugi"->IMUGI.get();case "vampbat"->VAMPBAT.get();case "cow"->COW.get();default->throw new IllegalArgumentException(species);};Pet pet=type.create(world);pet.moveTo(x,y,z,0,0);world.addFreshEntity(pet);return pet;}
 public static void worker(Object entity,int state,double x,double y,double z,int task,String group){if(entity instanceof Pet pet){pet.getEntityData().set(Pet.TASK,task);pet.getEntityData().set(Pet.GROUP,group);pet.worker=true;pet.setNoGravity(true);pet.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);pet.desired=state;pet.tx=x;pet.ty=y;pet.tz=z;}}
 public static void face(Object entity,double x,double z){if(entity instanceof Pet pet){pet.focusX=x;pet.focusZ=z;}}
 public static boolean reached(Object entity,double x,double y,double z){return entity instanceof Pet pet&&pet.reached(x,y,z);}
 /**
  * Plugin bridge (PipiLife, by reflection): turns this pet into a player mount. Ground only.
  * speed = ridden movement speed (horse is ~0.225), seat = extra rider height in blocks, step = blocks it climbs without jumping.
  */
 public static void mount(Object entity,float speed,float seat,float jump){if(entity instanceof Pet pet){pet.worker=false;pet.goalSelector.removeAllGoals(g->true);pet.targetSelector.removeAllGoals(g->true);pet.getNavigation().stop();pet.setNoGravity(false);pet.getEntityData().set(Pet.MOUNT,true);pet.getEntityData().set(Pet.MOUNT_SPEED,speed);pet.getEntityData().set(Pet.MOUNT_SEAT,seat);pet.getEntityData().set(Pet.MOUNT_JUMP,jump);}}
 public static final class Pet extends PathfinderMob implements PlayerRideableJumping {
  private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> ACTION=net.minecraft.network.syncher.SynchedEntityData.defineId(Pet.class,net.minecraft.network.syncher.EntityDataSerializers.INT);
  private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> TASK=net.minecraft.network.syncher.SynchedEntityData.defineId(Pet.class,net.minecraft.network.syncher.EntityDataSerializers.INT);
  private static final net.minecraft.network.syncher.EntityDataAccessor<String> GROUP=net.minecraft.network.syncher.SynchedEntityData.defineId(Pet.class,net.minecraft.network.syncher.EntityDataSerializers.STRING);
  private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> MOUNT=net.minecraft.network.syncher.SynchedEntityData.defineId(Pet.class,net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
  private static final net.minecraft.network.syncher.EntityDataAccessor<Float> MOUNT_SPEED=net.minecraft.network.syncher.SynchedEntityData.defineId(Pet.class,net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
  private static final net.minecraft.network.syncher.EntityDataAccessor<Float> MOUNT_SEAT=net.minecraft.network.syncher.SynchedEntityData.defineId(Pet.class,net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
  private static final net.minecraft.network.syncher.EntityDataAccessor<Float> MOUNT_JUMP=net.minecraft.network.syncher.SynchedEntityData.defineId(Pet.class,net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
  public int task(){return entityData.get(TASK);}
  public String soundGroup(){return entityData.get(GROUP);}
  private boolean worker;private int desired;private double tx,ty,tz;private double focusX=Double.NaN,focusZ;
  @Override protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder){super.defineSynchedData(builder);builder.define(ACTION,0);builder.define(TASK,0);builder.define(GROUP,"");builder.define(MOUNT,false);builder.define(MOUNT_SPEED,.3f);builder.define(MOUNT_SEAT,0f);builder.define(MOUNT_JUMP,.5f);}
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

  // ---- 탈것 (지상): 플레이어가 조종하고 클라이언트가 움직임을 계산한다 (말과 같은 방식)
  public boolean isMount(){return entityData.get(MOUNT);}
  private float pendingJump;
  @Override public LivingEntity getControllingPassenger(){return isMount()&&getFirstPassenger() instanceof Player rider?rider:super.getControllingPassenger();}
  @Override protected void tickRidden(Player rider,net.minecraft.world.phys.Vec3 input){
   super.tickRidden(rider,input);
   setRot(rider.getYRot(),rider.getXRot()*.5f);yRotO=yBodyRot=yHeadRot=getYRot();
   if(pendingJump>0&&onGround()){var v=getDeltaMovement();setDeltaMovement(v.x,entityData.get(MOUNT_JUMP)*pendingJump,v.z);hasImpulse=true;pendingJump=0;}
  }
  @Override protected net.minecraft.world.phys.Vec3 getRiddenInput(Player rider,net.minecraft.world.phys.Vec3 input){float strafe=rider.xxa*.5f,forward=rider.zza;if(forward<=0)forward*=.25f;return new net.minecraft.world.phys.Vec3(strafe,0,forward);}
  @Override protected float getRiddenSpeed(Player rider){return entityData.get(MOUNT_SPEED);}
  @Override public float maxUpStep(){return isMount()?1.05f:super.maxUpStep();}
  @Override protected net.minecraft.world.phys.Vec3 getPassengerAttachmentPoint(Entity passenger,EntityDimensions dimensions,float scale){return super.getPassengerAttachmentPoint(passenger,dimensions,scale).add(0,entityData.get(MOUNT_SEAT),0);}
  @Override public boolean canJump(){return isMount()&&onGround();}
  @Override public void onPlayerJump(int power){if(!isMount())return;pendingJump=power>=90?1f:.4f+.6f*Math.max(0,power)/90f;}
  @Override public void handleStartJump(int power){}
  @Override public void handleStopJump(){}
  @Override public boolean causeFallDamage(float distance,float multiplier,net.minecraft.world.damagesource.DamageSource source){return !isMount()&&super.causeFallDamage(distance,multiplier,source);}
  @Override protected void registerGoals(){goalSelector.addGoal(0,new FloatGoal(this));goalSelector.addGoal(1,new PanicGoal(this,1.2));goalSelector.addGoal(3,new WaterAvoidingRandomStrollGoal(this,.8));goalSelector.addGoal(4,new LookAtPlayerGoal(this,Player.class,6));goalSelector.addGoal(5,new RandomLookAroundGoal(this));}
  @Override public boolean removeWhenFarAway(double distance){return false;}
 }
}
