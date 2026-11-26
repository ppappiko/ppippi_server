package dev.pipi.facilities;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.bus.api.IEventBus;

/**
 * 비탄의 성채 레이드 군체 몬스터 (models: assets/pipifacilities/pets/hive_<kind>.bbmodel, drawn by RaidMobRenderer).
 * The mod only walks them (chase / keep range) and shows their animation; PipiLife owns targets, attacks and damage
 * through the reflection bridge below (spawn, target, act).
 */
public final class RaidMobs {
 static final DeferredHolder<EntityType<?>,EntityType<Ghoul>> RUNNER=register("hive_runner",.6f,1.35f);
 static final DeferredHolder<EntityType<?>,EntityType<Ghoul>> BOMBER=register("hive_bomber",.8f,1.9f);
 static final DeferredHolder<EntityType<?>,EntityType<Ghoul>> ACOLYTE=register("hive_acolyte",.7f,2.1f);
 static final DeferredHolder<EntityType<?>,EntityType<Ghoul>> KNIGHT=register("hive_knight",1.1f,2.8f);
 static final DeferredHolder<EntityType<?>,EntityType<Ghoul>> WIZARD=register("hive_wizard",.8f,2.5f);
 /** 문지기 (gate 4 gatekeeper): short, fat brute with two swords; leaps and slams both down. */
 static final DeferredHolder<EntityType<?>,EntityType<Ghoul>> KEEPER=register("hive_keeper",1.7f,2.6f);
 /** 영혼 촉수 (4관문 기둥): a rooted, writhing soul pillar; never moves. */
 static final DeferredHolder<EntityType<?>,EntityType<Ghoul>> TENTACLE=register("hive_tentacle",1.4f,4.2f);
 /** 비탄의 거인 (ogre): modelled at half size, the plugin sets its scale to 2 (about 5.4 blocks). */
 static final DeferredHolder<EntityType<?>,EntityType<Ghoul>> OGRE=register("hive_ogre",1.3f,2.7f);

 static DeferredHolder<EntityType<?>,EntityType<Ghoul>> register(String id,float w,float h){
  return PipiPets.TYPES.register(id,()->EntityType.Builder.of(Ghoul::new,MobCategory.MONSTER).sized(w,h).fireImmune().clientTrackingRange(10).build("pipifacilities:"+id));
 }
 static void init(IEventBus bus){bus.addListener(RaidMobs::attributes);}
 static void attributes(EntityAttributeCreationEvent e){
  put(e,RUNNER.get(),.33);put(e,BOMBER.get(),.2);put(e,ACOLYTE.get(),.25);put(e,KNIGHT.get(),.21);put(e,WIZARD.get(),.23);put(e,KEEPER.get(),.22);put(e,TENTACLE.get(),0);put(e,OGRE.get(),.24);
 }
 private static void put(EntityAttributeCreationEvent e,EntityType<Ghoul> t,double speed){
  e.put(t,Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,100).add(Attributes.MOVEMENT_SPEED,speed).add(Attributes.ATTACK_DAMAGE,1)
   .add(Attributes.FOLLOW_RANGE,48).add(Attributes.KNOCKBACK_RESISTANCE,t==KNIGHT.get()||t==KEEPER.get()||t==TENTACLE.get()||t==OGRE.get()?1:.5).build());
 }

 // ---------------------------------------------------------------- plugin bridge (PipiLife, by reflection)
 /** kind: runner, bomber, acolyte, knight, wizard, keeper. Returns the new NMS entity (already added to the level). */
 public static Object spawn(Object level,String kind,double x,double y,double z,float yaw){
  ServerLevel world=(ServerLevel)level;
  EntityType<Ghoul> type=switch(kind){case "bomber"->BOMBER.get();case "acolyte"->ACOLYTE.get();case "knight"->KNIGHT.get();case "wizard"->WIZARD.get();case "keeper"->KEEPER.get();case "tentacle"->TENTACLE.get();case "ogre"->OGRE.get();default->RUNNER.get();};
  Ghoul g=type.create(world);
  if(g==null)throw new IllegalStateException("cannot create "+kind);
  g.moveTo(x,y,z,yaw,0);g.yBodyRot=yaw;g.yHeadRot=yaw;g.setPersistenceRequired();
  world.addFreshEntity(g);
  return g;
 }
 /** Sets (or clears, with null) the chased player. */
 public static void target(Object entity,Object target){if(entity instanceof Ghoul g)g.setTarget(target instanceof LivingEntity t?t:null);}
 /** Plays an animation for ticks (2 = attack, 3 = second attack; while attacking, everything but the runner stands still). */
 public static void act(Object entity,int action,int ticks){if(entity instanceof Ghoul g){g.getEntityData().set(Ghoul.ACTION,action);g.actUntil=g.tickCount+Math.max(1,ticks);}}
 public static boolean isGhoul(Object entity){return entity instanceof Ghoul;}

 public static final class Ghoul extends Monster {
  static final net.minecraft.network.syncher.EntityDataAccessor<Integer> ACTION=net.minecraft.network.syncher.SynchedEntityData.defineId(Ghoul.class,net.minecraft.network.syncher.EntityDataSerializers.INT);
  int actUntil;
  public Ghoul(EntityType<? extends Monster> type,Level level){super(type,level);xpReward=0;}
  @Override protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder b){super.defineSynchedData(b);b.define(ACTION,0);}
  public int action(){return entityData.get(ACTION);}
  public String skin(){return EntityType.getKey(getType()).getPath();}
  /** Distance it likes to keep from its target (0 = melee). */
  double range(){var t=getType();return t==KNIGHT.get()?9:t==ACOLYTE.get()?10:t==WIZARD.get()?12:0;}
  @Override protected void registerGoals(){goalSelector.addGoal(0,new FloatGoal(this));goalSelector.addGoal(1,new Chase(this));goalSelector.addGoal(6,new RandomLookAroundGoal(this));}
  @Override public boolean isPushable(){return getType()!=TENTACLE.get()&&super.isPushable();}
  @Override public void tick(){super.tick();if(!level().isClientSide&&actUntil>0&&tickCount>=actUntil){actUntil=0;entityData.set(ACTION,0);}}
  @Override public boolean doHurtTarget(Entity target){return false;}
  @Override public boolean removeWhenFarAway(double d){return false;}
  @Override public void checkDespawn(){}
  @Override protected boolean shouldDespawnInPeaceful(){return false;}
  @Override public boolean causeFallDamage(float d,float m,net.minecraft.world.damagesource.DamageSource s){return false;}
  @Override protected net.minecraft.sounds.SoundEvent getAmbientSound(){var t=getType();return t==RUNNER.get()||t==BOMBER.get()?net.minecraft.sounds.SoundEvents.ZOMBIE_AMBIENT:t==KEEPER.get()||t==OGRE.get()?net.minecraft.sounds.SoundEvents.RAVAGER_AMBIENT:null;}
  @Override public float getVoicePitch(){return getType()==RUNNER.get()?1.5f:.6f;}
 }

 /** Runs at its target (melee kinds) or keeps its range and faces it (shooters). Holds still while attacking. */
 static final class Chase extends Goal {
  final Ghoul g;int repath;
  Chase(Ghoul g){this.g=g;setFlags(java.util.EnumSet.of(Flag.MOVE,Flag.LOOK));}
  @Override public boolean canUse(){var t=g.getTarget();return t!=null&&t.isAlive();}
  @Override public boolean canContinueToUse(){return canUse();}
  @Override public boolean requiresUpdateEveryTick(){return true;}
  @Override public void stop(){g.getNavigation().stop();}
  @Override public void tick(){
   var t=g.getTarget();if(t==null)return;
   g.getLookControl().setLookAt(t,40,40);
   double d=g.distanceTo(t),r=g.range();
   boolean busy=g.action()>=2&&g.getType()!=RaidMobs.RUNNER.get();
   if(busy||(r>0&&d<=r+1.5&&d>=r-4&&g.hasLineOfSight(t))){
    g.getNavigation().stop();
    float yaw=(float)(Math.toDegrees(Math.atan2(t.getZ()-g.getZ(),t.getX()-g.getX()))-90);
    g.setYRot(net.minecraft.util.Mth.approachDegrees(g.getYRot(),yaw,12));g.yBodyRot=g.getYRot();
    return;
   }
   if(--repath>0)return;repath=8;
   if(r>0&&d<r-4){
    var away=g.position().subtract(t.position()).normalize().scale(5).add(g.position());
    g.getNavigation().moveTo(away.x,away.y,away.z,1);
   }else g.getNavigation().moveTo(t,1);
  }
 }
}
