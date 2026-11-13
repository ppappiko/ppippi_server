package dev.pipi.life;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.*;
import org.bukkit.persistence.PersistentDataType;
import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ActiveModel;

/** Stable species IDs share existing capture, storage and aptitude data; models are purely visual. */
final class CustomPets implements Listener {
 private final PipiLife plugin;private final NamespacedKey species;private final Map<UUID,ActiveModel> models=new HashMap<>();private final Map<UUID,String> animations=new HashMap<>();private final Map<String,Worker> workers=new HashMap<>();private boolean warned;
 private static final Map<String,EntityType> TYPES=Map.ofEntries(Map.entry("sparrow",EntityType.CHICKEN),Map.entry("frog",EntityType.FROG),Map.entry("cat",EntityType.CAT),Map.entry("rabbit",EntityType.RABBIT),Map.entry("fox",EntityType.FOX),Map.entry("redpanda",EntityType.PANDA),Map.entry("otter",EntityType.AXOLOTL),Map.entry("tortoise",EntityType.TURTLE),Map.entry("raccoon",EntityType.PANDA),Map.entry("hornrabbit",EntityType.RABBIT),Map.entry("mole",EntityType.SILVERFISH),Map.entry("thundersheep",EntityType.SHEEP),Map.entry("moonbear",EntityType.POLAR_BEAR),Map.entry("ferret",EntityType.FOX),Map.entry("angelpig",EntityType.PIG),Map.entry("sunlizard",EntityType.AXOLOTL),Map.entry("imugi",EntityType.SILVERFISH),Map.entry("vampbat",EntityType.BAT));
 private static class Worker { LivingEntity entity;int food=100;long mealUntil,visitAfter;boolean visiting; Worker(LivingEntity e){entity=e;} }
 CustomPets(PipiLife plugin){this.plugin=plugin;species=new NamespacedKey(plugin,"pet_species");for(World w:Bukkit.getWorlds())for(Entity e:w.getEntities())if(e.getScoreboardTags().contains("pipi_pet_worker"))e.remove();}
 private boolean ready(String skin){return skin!=null&&TYPES.containsKey(skin);}
 private String skin(LivingEntity e){return e.getPersistentDataContainer().get(species,PersistentDataType.STRING);}
 private boolean attach(LivingEntity e){String skin=skin(e);if(dedicated(e)){var previous=ModelEngineAPI.getModeledEntity(e);if(previous!=null){previous.removeModel("pipi_"+skin);previous.setBaseEntityVisible(true);}return true;}if(!ready(skin))return false;if(models.containsKey(e.getUniqueId()))return true;
  try{var modeled=ModelEngineAPI.getOrCreateModeledEntity(e);var model=ModelEngineAPI.createActiveModel("pipi_"+skin);model.setScale(.65);modeled.addModel(model,true);modeled.setBaseEntityVisible(false);models.put(e.getUniqueId(),model);animation(e,"idle");return true;}catch(RuntimeException|LinkageError ex){if(!warned){plugin.getLogger().warning("전용 펫 모델 연결 실패: "+ex);warned=true;}return false;}
 }
 private void animation(LivingEntity e,String name){var model=models.get(e.getUniqueId());if(model==null||name.equals(animations.get(e.getUniqueId())))return;String old=animations.put(e.getUniqueId(),name);if(old!=null)model.getAnimationHandler().stopAnimation(old);model.getAnimationHandler().playAnimation(name,.2,.2,1,true);}
 private LivingEntity spawn(Location at,String skin,boolean worker){LivingEntity e=createEntity(at,skin);if(e==null)return null;e.getPersistentDataContainer().set(species,PersistentDataType.STRING,skin);e.setRemoveWhenFarAway(false);e.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(plugin.petMaxHp(skin));e.setHealth(e.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());if(skin.equals("otter")){e.setMaximumAir(Integer.MAX_VALUE);e.setRemainingAir(Integer.MAX_VALUE);}if(e instanceof Ageable age)eSafeAdult(age);if(worker){e.addScoreboardTag("pipi_pet_worker");e.setAI(false);e.setInvulnerable(true);e.setSilent(true);e.setCollidable(false);e.setPersistent(false);}else e.setPersistent(true);if(!attach(e)){e.remove();return null;}return e;}
 /** Static, invulnerable, non-persistent display pet (tutorial guide). Null when the model or entity cannot be created. */
 LivingEntity spawnDisplay(Location at,String skin){if(!ready(skin))return null;LivingEntity e=spawn(at,skin,true);if(e!=null)e.setRotation(at.getYaw(),0);return e;}
 private LivingEntity createEntity(Location at,String skin){try{Object world=at.getWorld().getClass().getMethod("getHandle").invoke(at.getWorld());Class<?> bridge=Class.forName("dev.pipi.facilities.PipiPets");Object nms=bridge.getMethod("spawn",Object.class,String.class,double.class,double.class,double.class).invoke(null,world,skin,at.getX(),at.getY(),at.getZ());return (LivingEntity)nms.getClass().getMethod("getBukkitEntity").invoke(nms);}catch(ReflectiveOperationException ex){plugin.getLogger().severe("전용 펫 엔티티 생성 실패: PipiFacilities 서버/클라이언트 버전 일치 확인: "+ex);return null;}}
 private boolean dedicated(LivingEntity e){try{return e.getClass().getMethod("getHandle").invoke(e).getClass().getName().equals("dev.pipi.facilities.PipiPets$Pet");}catch(ReflectiveOperationException ex){return false;}}
 private void eSafeAdult(Ageable e){e.setAdult();e.setAgeLock(true);}
 boolean command(Player p,String[] args){if(!p.isOp()){p.sendMessage("관리자 전용 명령어입니다.");return true;}if(args.length<2||!TYPES.containsKey(args[1])){StringJoiner usage=new StringJoiner("|");for(PipiLife.Pet pt:PipiLife.Pet.values())usage.add(pt.name().toLowerCase(Locale.ROOT)+"("+pt.label+")");p.sendMessage("§e/pipilife petspawn §f"+usage+" §7[1~5]");return true;}String skin=args[1];if(!ready(skin)){p.sendMessage("지원하지 않는 전용 펫 종류입니다.");return true;}if(!plugin.isWildWorld(p.getWorld().getName())){p.sendMessage("야생 월드에서 사용하세요.");return true;}int count=1;try{if(args.length>2)count=Math.clamp(Integer.parseInt(args[2]),1,5);}catch(NumberFormatException ignored){}int made=0;for(int i=0;i<count;i++)if(spawn(p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(2)).add(i*.8,0,0),skin,false)!=null)made++;p.sendMessage("전용 펫 "+made+"마리 소환. 몽둥이·포획틀로 테스트하세요.");return true;}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void noNaturalAnimals(CreatureSpawnEvent event){World world=event.getLocation().getWorld();if(!plugin.isWildWorld(world.getName()))return;CreatureSpawnEvent.SpawnReason reason=event.getSpawnReason();
  // only our own spawns (plugin CUSTOM, commands, eggs, breeding of captured pets) may add animals to the wild world; on the hybrid server natural/chunk spawns arrive with other reasons too
  if(reason==CreatureSpawnEvent.SpawnReason.CUSTOM||reason==CreatureSpawnEvent.SpawnReason.COMMAND||reason==CreatureSpawnEvent.SpawnReason.SPAWNER_EGG||reason==CreatureSpawnEvent.SpawnReason.BREEDING||reason==CreatureSpawnEvent.SpawnReason.DISPENSE_EGG)return;
  if(wildAnimal(event.getEntity()))event.setCancelled(true);}
 private boolean wildAnimal(Entity e){if(!(e instanceof Animals||e instanceof Bat||e instanceof Squid||e instanceof Fish||e instanceof Axolotl||e instanceof Strider))return false;if(e.getPersistentDataContainer().has(species,PersistentDataType.STRING)||e.getScoreboardTags().contains("pipi_pet_worker"))return false;if(e instanceof Tameable t&&t.isTamed())return false;if(e.getCustomName()!=null||!e.getPassengers().isEmpty()||e.getVehicle()!=null)return false;if(e instanceof LivingEntity le&&le.isLeashed())return false;return true;}
 /** Chunk-generation animals (and ones from before the rule) never fire a spawn event we can cancel: remove them when their chunk loads. */
 @EventHandler public void clearWildAnimals(org.bukkit.event.world.EntitiesLoadEvent event){if(!plugin.isWildWorld(event.getWorld().getName()))return;for(Entity e:event.getEntities())if(wildAnimal(e))e.remove();}
 void spawnWild(){
  int cap=Math.max(1,plugin.getConfig().getInt("pet-spawn.cap",6));
  boolean transportUnlocked=plugin.getConfig().getBoolean("transport-unlocked",false);
  double transportChance=plugin.getConfig().getDouble("pet-spawn.transport-chance",.005);
  ThreadLocalRandom rnd=ThreadLocalRandom.current();
  for(World world:Bukkit.getWorlds()){
   if(!plugin.isWildWorld(world.getName()))continue;
   boolean nether=world.getEnvironment()==World.Environment.NETHER;
   for(Player player:world.getPlayers())for(int attempt=0;attempt<3;attempt++)trySpawn(world,player,nether,cap,transportUnlocked,transportChance,rnd);
  }
 }
 private void trySpawn(World world,Player player,boolean nether,int cap,boolean transportUnlocked,double transportChance,ThreadLocalRandom rnd){
  int nearby=0;for(Entity e:player.getNearbyEntities(64,64,64))if(e instanceof LivingEntity l&&skin(l)!=null&&!l.getScoreboardTags().contains("pipi_pet_worker"))nearby++;
  if(nearby>=cap)return;
  double angle=rnd.nextDouble()*Math.PI*2,radius=24+rnd.nextDouble()*32;
  int x=player.getLocation().getBlockX()+(int)Math.round(Math.cos(angle)*radius);
  int z=player.getLocation().getBlockZ()+(int)Math.round(Math.sin(angle)*radius);
  int playerY=player.getLocation().getBlockY();
  Location spot;
  if(nether)spot=undergroundSpot(world,x,z,playerY);
  else{String hint=biomeKey(world,x,playerY,z);spot=hint.equals("deep_dark")?undergroundSpot(world,x,z,playerY):surfaceSpot(world,x,z,hint.equals("warm_ocean"));}
  if(spot==null)return;
  Block ground=spot.getBlock().getRelative(0,-1,0);
  Set<String> allowed=null;
  if(plugin.regions!=null){Regions.Region reg=plugin.regions.at(spot);if(reg!=null){allowed=reg.allowedSkins();if(allowed!=null&&allowed.isEmpty())return;}}
  PipiLife.Pet pet=pick(world,biomeKey(world,x,spot.getBlockY(),z),ground,transportUnlocked,transportChance,rnd,allowed);
  if(pet==null)return;
  if(!pet.transport()&&rnd.nextDouble()>=levelChance(pet.level))return;
  spawn(spot,pet.name().toLowerCase(Locale.ROOT),false);
 }
 private PipiLife.Pet pick(World world,String biome,Block ground,boolean transportUnlocked,double transportChance,ThreadLocalRandom rnd,Set<String> allowed){
  List<PipiLife.Pet> normal=new ArrayList<>(),transport=new ArrayList<>();
  boolean fortress=structure(ground,"fortress"),bastion=structure(ground,"bastion");
  for(PipiLife.Pet pet:PipiLife.Pet.values()){
   if(allowed!=null&&!allowed.contains(pet.name().toLowerCase(Locale.ROOT)))continue;
   if(pet.netherPlace()){if(pet.place.equals("fortress")&&!fortress)continue;if(pet.place.equals("bastion")&&!bastion)continue;transport.add(pet);}
   else if(pet.place.equals(biome))normal.add(pet);
  }
  if(!transport.isEmpty()&&transportUnlocked&&rnd.nextDouble()<transportChance)return transport.get(rnd.nextInt(transport.size()));
  if(normal.isEmpty())return null;
  return normal.get(rnd.nextInt(normal.size()));
 }
 private static final Map<String,String> BIOME_KO=Map.ofEntries(Map.entry("forest","숲"),Map.entry("plains","평원"),Map.entry("cherry_grove","벚나무 숲"),Map.entry("desert","사막"),Map.entry("warm_ocean","따뜻한 바다"),Map.entry("windswept_gravelly_hills","바람이 세찬 자갈투성이 언덕"),Map.entry("mushroom_fields","버섯섬"),Map.entry("ice_spikes","역고드름"),Map.entry("wooded_badlands","나무가 우거진 악지"),Map.entry("sunflower_plains","해바라기 평원"),Map.entry("deep_dark","딥 다크"),Map.entry("flower_forest","꽃 숲"),Map.entry("mangrove_swamp","맹그로브 늪"),Map.entry("stony_peaks","돌 봉우리"),Map.entry("fortress","네더 요새"),Map.entry("bastion","바스티온"));
 private static String pct(double v){String t=String.format(Locale.ROOT,"%.2f",v*100);t=t.replaceAll("0+$","").replaceAll("\\.$","");return t+"%";}
 private String placeName(String place){String ko=BIOME_KO.get(place);return ko==null?place:ko+"("+place+")";}
 boolean spawnInfo(Player p,String[] args){
  if(!p.isOp()){p.sendMessage("§c관리자 전용 명령어입니다.");return true;}
  boolean unlocked=plugin.getConfig().getBoolean("transport-unlocked",false);
  String transportText=unlocked?pct(plugin.getConfig().getDouble("pet-spawn.transport-chance",.005)):"잠김";
  if(args.length>1&&args[1].equalsIgnoreCase("all")){
   p.sendMessage("§6[전체 펫 스폰 정보]");
   for(PipiLife.Pet pet:PipiLife.Pet.values()){
    int n=0;for(PipiLife.Pet o:PipiLife.Pet.values())if(o.place.equals(pet.place))n++;
    String chance=pet.transport()?transportText:pct(levelChance(pet.level)/n);
    p.sendMessage("§f"+pet.label+" §7("+pet.name().toLowerCase(Locale.ROOT)+") §eLv"+pet.level+" §7| §b"+placeName(pet.place)+" §7| §a시도당 "+chance);
   }
   return true;
  }
  Location loc=p.getLocation();String biome=biomeKey(p.getWorld(),loc.getBlockX(),loc.getBlockY(),loc.getBlockZ());
  boolean nether=p.getWorld().getEnvironment()==World.Environment.NETHER;
  p.sendMessage("§6[스폰 정보] §f현재 바이옴: §b"+biome+(BIOME_KO.containsKey(biome)?" §7("+BIOME_KO.get(biome)+")":""));
  int shown=0;
  for(PipiLife.Pet pet:PipiLife.Pet.values()){
   String chance;
   if(pet.netherPlace()){if(!nether)continue;chance=transportText;}
   else{if(!pet.place.equals(biome))continue;int n=0;for(PipiLife.Pet o:PipiLife.Pet.values())if(!o.netherPlace()&&o.place.equals(biome))n++;chance=pct(levelChance(pet.level)/n);}
   p.sendMessage("§f"+pet.label+" §7("+pet.name().toLowerCase(Locale.ROOT)+") §eLv"+pet.level+" §7| §a시도당 "+chance);shown++;
  }
  if(shown==0)p.sendMessage("§7이 바이옴에서는 스폰되는 펫이 없습니다.");
  int interval=Math.max(1,plugin.getConfig().getInt("pet-spawn.interval-ticks",200)),cap=Math.max(1,plugin.getConfig().getInt("pet-spawn.cap",6));
  int nearby=0;for(Entity e:p.getNearbyEntities(64,64,64))if(e instanceof LivingEntity l&&skin(l)!=null&&!l.getScoreboardTags().contains("pipi_pet_worker"))nearby++;
  p.sendMessage("§7스폰 주기 "+(interval/20)+"초 | 플레이어당 3회 시도 | 주변 상한 "+cap+"마리 | 64블록 내 야생 펫 "+nearby+"마리");
  return true;
 }
 private double levelChance(int level){return plugin.getConfig().getDouble("pet-spawn.level-chance."+level,switch(level){case 2->.35;case 3->.03;default->1.0;});}
 private boolean structure(Block ground,String kind){Material t=ground.getType();return kind.equals("fortress")?t==Material.NETHER_BRICKS:t==Material.BLACKSTONE||t==Material.POLISHED_BLACKSTONE_BRICKS||t==Material.GILDED_BLACKSTONE;}
 private String biomeKey(World world,int x,int y,int z){return world.getBiome(x,y,z).getKey().getKey();}
 private Location surfaceSpot(World world,int x,int z,boolean allowWater){
  int min=world.getMinHeight();
  for(int y=world.getHighestBlockYAt(x,z);y>min;y--){Location l=standing(world,x,y,z);if(l!=null)return l;}
  if(allowWater)for(int y=world.getHighestBlockYAt(x,z);y>min;y--)if(world.getBlockAt(x,y,z).getType()==Material.WATER&&world.getBlockAt(x,y+1,z).getType().isAir())return new Location(world,x+.5,y+1,z+.5);
  return null;
 }
 private Location undergroundSpot(World world,int x,int z,int playerY){
  int top=Math.min(world.getMaxHeight()-3,playerY+16),bottom=Math.max(world.getMinHeight()+1,playerY-16);
  for(int y=top;y>=bottom;y--){Location l=standing(world,x,y,z);if(l!=null)return l;}
  return null;
 }
 private Location standing(World world,int x,int y,int z){
  Material type=world.getBlockAt(x,y,z).getType();
  if(!type.isSolid()||Tag.LEAVES.isTagged(type))return null;
  if(!world.getBlockAt(x,y+1,z).getType().isAir()||!world.getBlockAt(x,y+2,z).getType().isAir())return null;
  return new Location(world,x+.5,y+1,z+.5);
 }
 @EventHandler(priority=EventPriority.HIGHEST)public void workerDamage(EntityDamageEvent e){if(e.getEntity().getScoreboardTags().contains("pipi_pet_worker"))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void hurt(EntityDamageEvent e){if(e.getEntity() instanceof LivingEntity l&&models.containsKey(l.getUniqueId())&&!l.getScoreboardTags().contains("pipi_pet_worker")){var m=models.get(l.getUniqueId());m.getAnimationHandler().playAnimation("hurt",0,.1,1,true);}}
 @EventHandler public void interact(PlayerInteractEntityEvent e){if(e.getRightClicked().getScoreboardTags().contains("pipi_pet_worker")){e.setCancelled(true);if(e.getHand()!=org.bukkit.inventory.EquipmentSlot.HAND)return;for(var en:workers.entrySet())if(en.getValue().entity.equals(e.getRightClicked())){plugin.releaseWorkerByClick(e.getPlayer(),en.getKey());break;}}}
 private void release(UUID id){var model=models.remove(id);animations.remove(id);var base=Bukkit.getEntity(id);if(model!=null){var modeled=base==null?null:ModelEngineAPI.getModeledEntity(base);if(modeled!=null){modeled.setBaseEntityVisible(true);modeled.removeModel(model.getBlueprint().getName());}model.destroy();}}
 void tick(){
  for(World w:Bukkit.getWorlds())for(Player p:w.getPlayers())for(Entity e:p.getNearbyEntities(32,20,32))if(e instanceof LivingEntity l&&skin(l)!=null&&!l.getScoreboardTags().contains("pipi_pet_worker")){if(!dedicated(l)){double hp=l.getHealth();LivingEntity replacement=spawn(l.getLocation(),skin(l),false);if(replacement!=null){replacement.setHealth(Math.min(hp,replacement.getMaxHealth()));release(l.getUniqueId());l.remove();}}else attach(l);}
  for(UUID id:new ArrayList<>(models.keySet())){Entity e=Bukkit.getEntity(id);if(!(e instanceof LivingEntity l)||!e.isValid()||l.isDead()){release(id);continue;}models.get(id).setScale(l.getPersistentDataContainer().has(NamespacedKey.fromString("pipilife:capture_restore"),PersistentDataType.INTEGER)?0:.65);if(!e.getScoreboardTags().contains("pipi_pet_worker"))animation(l,l.getVelocity().lengthSquared()>.006?"walk":"idle");}
  Set<String>wanted=new HashSet<>();long now=System.currentTimeMillis();var scenes=plugin.petScenes();for(var scene:scenes){
   try{tickScene(scene,wanted,now);}catch(RuntimeException ex){if(failedScenes.add(scene.key()))plugin.getLogger().warning("펫 작업 장면 오류 ("+scene.key()+"): "+ex);}
  }for(String key:new ArrayList<>(workers.keySet()))if(!wanted.contains(key)){Worker w=workers.remove(key);release(w.entity.getUniqueId());w.entity.remove();}
  workAudio(scenes);
 }
 private final Set<String> failedScenes=new HashSet<>();
 private boolean chunkLoaded(Location l){return l.getWorld()!=null&&l.getWorld().isChunkLoaded(l.getBlockX()>>4,l.getBlockZ()>>4);}
 private void tickScene(PipiLife.PetScene scene,Set<String>wanted,long now){
   if(!ready(scene.species())||!chunkLoaded(scene.station()))return;wanted.add(scene.key());Worker worker=workers.get(scene.key());if(worker==null||!worker.entity.isValid()){LivingEntity e=spawn(scene.station(),scene.species(),true);if(e==null)return;worker=new Worker(e);workers.put(scene.key(),worker);}
   if(scene.feeder()!=null && now>worker.visitAfter && (scene.food()<=10 || scene.food()>worker.food+5)){worker.visiting=true;worker.mealUntil=0;worker.visitAfter=now+15_000;}
   worker.food=scene.food();Location target=worker.visiting?scene.feeder():scene.station();if(target==null)target=scene.station();var e=worker.entity;double dist=e.getLocation().distanceSquared(target);
   int action=scene.working()?2:0;if(worker.visiting&&arrived(e,target)){action=3;if(worker.mealUntil==0)worker.mealUntil=now+2000;if(scene.food()>0&&now>=worker.mealUntil)worker.visiting=false;}
   try{Object nms=e.getClass().getMethod("getHandle").invoke(e);Class.forName("dev.pipi.facilities.PipiPets").getMethod("worker",Object.class,int.class,double.class,double.class,double.class,int.class,String.class).invoke(null,nms,action,target.getX(),target.getY(),target.getZ(),scene.task(),scene.soundGroup());Location focus=worker.visiting?target.clone().add(0,0,1):scene.focus();Class.forName("dev.pipi.facilities.PipiPets").getMethod("face",Object.class,double.class,double.class).invoke(null,nms,focus.getX(),focus.getZ());}catch(ReflectiveOperationException ex){if(!warned){warned=true;plugin.getLogger().warning("펫 작업 동작 연결 실패: "+ex);}}
 }
 private boolean arrived(LivingEntity e,Location target){try{Object nms=e.getClass().getMethod("getHandle").invoke(e);return (boolean)Class.forName("dev.pipi.facilities.PipiPets").getMethod("reached",Object.class,double.class,double.class,double.class).invoke(null,nms,target.getX(),target.getY(),target.getZ());}catch(ReflectiveOperationException ex){return false;}}
 private void workAudio(java.util.List<PipiLife.PetScene> scenes){
  for(Player player:Bukkit.getOnlinePlayers()){
   Map<String,PipiLife.PetScene> audible=new TreeMap<>();
   for(var scene:scenes){if(!scene.working()||!scene.station().getWorld().equals(player.getWorld())||scene.station().distanceSquared(player.getLocation())>256)continue;Worker worker=workers.get(scene.key());if(worker!=null&&(worker.visiting||!arrived(worker.entity,scene.station())))continue;audible.putIfAbsent(scene.soundGroup(),scene);}
   StringJoiner packet=new StringJoiner("|");for(var scene:audible.values()){Location at=scene.station();String key=Base64.getUrlEncoder().withoutPadding().encodeToString(scene.soundGroup().getBytes(java.nio.charset.StandardCharsets.UTF_8));packet.add(key+"~"+scene.task()+"~"+at.getX()+"~"+at.getY()+"~"+at.getZ());}player.sendMessage("PIPI_WORK_AUDIO:"+packet);
  }
 }

 @EventHandler public void unload(ChunkUnloadEvent event){for(Entity e:event.getChunk().getEntities())if(models.containsKey(e.getUniqueId()))release(e.getUniqueId());}
 void close(){for(Worker w:workers.values()){release(w.entity.getUniqueId());w.entity.remove();}workers.clear();for(UUID id:new ArrayList<>(models.keySet()))release(id);}
}
