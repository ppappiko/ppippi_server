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
 private static final Map<String,EntityType> TYPES=Map.ofEntries(Map.entry("sparrow",EntityType.CHICKEN),Map.entry("frog",EntityType.FROG),Map.entry("cat",EntityType.CAT),Map.entry("rabbit",EntityType.RABBIT),Map.entry("fox",EntityType.FOX),Map.entry("redpanda",EntityType.PANDA),Map.entry("otter",EntityType.AXOLOTL),Map.entry("tortoise",EntityType.TURTLE),Map.entry("raccoon",EntityType.PANDA),Map.entry("mole",EntityType.SILVERFISH),Map.entry("thundersheep",EntityType.SHEEP),Map.entry("moonbear",EntityType.POLAR_BEAR),Map.entry("ferret",EntityType.FOX),Map.entry("angelpig",EntityType.PIG),Map.entry("sunlizard",EntityType.AXOLOTL),Map.entry("vampbat",EntityType.BAT),Map.entry("cow",EntityType.COW));
 /** 빛나는 개체 flag lives on the mod entity (saved in its NBT); these bridge to PipiPets.shiny / isShiny. */
 static void setShiny(LivingEntity e,boolean on){try{Object nms=e.getClass().getMethod("getHandle").invoke(e);Class.forName("dev.pipi.facilities.PipiPets").getMethod("shiny",Object.class,boolean.class).invoke(null,nms,on);}catch(ReflectiveOperationException|LinkageError ignored){}}
 static boolean isShiny(LivingEntity e){try{Object nms=e.getClass().getMethod("getHandle").invoke(e);return (boolean)Class.forName("dev.pipi.facilities.PipiPets").getMethod("isShiny",Object.class).invoke(null,nms);}catch(ReflectiveOperationException|LinkageError ex){return false;}}
 /** 신규 펫 미리보기 (스폰·적성 없이 모델만): skin -> 이름 · 컨셉. */
 static final Map<String,String> PREVIEW=new LinkedHashMap<>();
 static{PREVIEW.put("beaver","댐돌이 · 비버");PREVIEW.put("hermitcrab","집게 · 소라게");PREVIEW.put("penguin","펭이 · 펭귄");PREVIEW.put("deer","하얀뿔 · 사슴");PREVIEW.put("ostrich","깃털이 · 타조");PREVIEW.put("rhino","뿔소 · 코뿔소");PREVIEW.put("monkey","망고 · 원숭이");PREVIEW.put("owl","밤눈 · 부엉이");PREVIEW.put("squirrel","도토리 · 다람쥐");PREVIEW.put("hedgehog","뾰족 · 고슴도치");PREVIEW.put("cloudsheep","뭉게 · 구름 양");PREVIEW.put("clockfox","톱니 · 태엽 여우");PREVIEW.put("beetle","기사단장 · 장수풍뎅이 기사");}
 /** /pipilife petpreview <skin|all> [shiny]: OP only. Non-persistent named pets (not capturable, not culled) to look at new models in game. */
 boolean preview(Player p,String[] args){
  if(!p.isOp()){p.sendMessage("관리자 전용 명령어입니다.");return true;}
  boolean shiny=args.length>2&&args[2].equalsIgnoreCase("shiny");
  List<String> skins=args.length>1&&args[1].equalsIgnoreCase("all")?new ArrayList<>(PREVIEW.keySet()):args.length>1&&PREVIEW.containsKey(args[1])?List.of(args[1]):List.of();
  if(skins.isEmpty()){p.sendMessage("§e/pipilife petpreview <"+String.join("|",PREVIEW.keySet())+"|all> [shiny]");return true;}
  Location base=p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(3));int i=0;
  for(String skin:skins){LivingEntity e=createEntity(base.clone().add((i%5)*2.2,0,(i/5)*2.6),skin);i++;if(e==null)continue;e.setCustomName("§7[미리보기] §f"+PREVIEW.get(skin)+(shiny?" §e✦":""));e.setCustomNameVisible(true);e.setPersistent(false);if(shiny)setShiny(e,true);}
  p.sendMessage("§a신규 펫 "+skins.size()+"마리 미리보기 소환"+(shiny?" (빛나는 개체)":"")+". 청크가 언로드되면 사라집니다.");return true;
 }
 private static class Worker { LivingEntity entity;int food=100;long mealUntil,visitAfter;boolean visiting;TextDisplay label;String shown;Location wander,look;long arrivedAt;int wanderAction;boolean still; Worker(LivingEntity e){entity=e;} }
 /** Ranch grazing (scene task 5): just walk around between random spots inside the ranch (short pause at each, no work or eat motion); not working (no food, full) = stand still where it is. */
 private static final int RANCH_TASK=5;
 private Location ranchSpot(Location center,ThreadLocalRandom rnd){
  // center = anchor + (7, 0, 7) of the 15x15 ranch; graze on open ground inside the fence ring (radius 6): within 4.5 cells of the centre
  for(int i=0;i<20;i++){int dx=rnd.nextInt(-4,5),dz=rnd.nextInt(-4,5);if(dx*dx+dz*dz>20)continue;var b=center.clone().add(dx,0,dz).getBlock();if(b.isPassable()&&!b.isLiquid()&&b.getRelative(0,1,0).isPassable()&&b.getRelative(0,-1,0).getType().isSolid())return center.clone().add(dx+.5,0,dz+.5);}
  return center.clone().add(.5,0,.5);
 }
 private void ranchTarget(Worker w,PipiLife.PetScene scene,long now){
  LivingEntity e=w.entity;ThreadLocalRandom rnd=ThreadLocalRandom.current();
  if(!scene.working()){
   if(!w.still||w.wander==null){Location at=e.getLocation();w.wander=new Location(at.getWorld(),at.getBlockX()+.5,scene.station().getY(),at.getBlockZ()+.5);w.look=null;w.still=true;}
   w.wanderAction=0;return;
  }
  if(w.still||w.wander==null){w.still=false;w.wander=ranchSpot(scene.station(),rnd);w.arrivedAt=0;w.wanderAction=0;return;}
  if(!arrived(e,w.wander))return;
  if(w.arrivedAt==0){w.arrivedAt=now;double a=rnd.nextDouble()*Math.PI*2;w.look=w.wander.clone().add(Math.cos(a),0,Math.sin(a));return;}
  if(now-w.arrivedAt>1500+rnd.nextInt(0,25)*100){w.wander=ranchSpot(scene.station(),rnd);w.arrivedAt=0;w.look=null;w.wanderAction=0;}
 }
 private static final String LABEL_TAG="pipi_pet_label";
 /** Floating worker-status label (작업 중 / 식사하러 가는 중 / ...) that follows the pet; non-persistent, removed with the pet. */
 private void label(Worker w,String status){
  if(status==null||status.isEmpty()){dropLabel(w);return;}
  LivingEntity e=w.entity;
  if(w.label==null||!w.label.isValid()){
   dropLabel(w);
   w.label=e.getWorld().spawn(labelSpot(e),TextDisplay.class,d->{d.addScoreboardTag(LABEL_TAG);d.setPersistent(false);d.setBillboard(Display.Billboard.CENTER);d.setBackgroundColor(Color.fromARGB(115,0,0,0));d.setDefaultBackground(false);d.setShadowed(false);d.setSeeThrough(false);d.setAlignment(TextDisplay.TextAlignment.CENTER);d.setLineWidth(400);d.setTeleportDuration(2);d.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(),new org.joml.AxisAngle4f(),new org.joml.Vector3f(.65f,.65f,.65f),new org.joml.AxisAngle4f()));});
   w.shown=null;
  }
  if(!status.equals(w.shown)){w.label.setText(statusColor(status)+status);w.shown=status;}
 }
 private static String statusColor(String s){return s.startsWith("식량 없음")?"§c":s.equals("식사 중")?"§b":s.equals("작업 중")?"§a":"§e";}
 private static Location labelSpot(LivingEntity e){return e.getLocation().add(0,Math.max(e.getHeight(),.5)+.45,0);}
 private void dropLabel(Worker w){if(w.label!=null){if(w.label.isValid())w.label.remove();w.label=null;}w.shown=null;}
 private void followLabels(){for(Worker w:workers.values()){if(w.label==null)continue;if(!w.label.isValid()||!w.entity.isValid()){dropLabel(w);continue;}Location at=labelSpot(w.entity);if(!w.label.getWorld().equals(at.getWorld())||w.label.getLocation().distanceSquared(at)>.0004)w.label.teleport(at);}}
 CustomPets(PipiLife plugin){this.plugin=plugin;species=new NamespacedKey(plugin,"pet_species");for(World w:Bukkit.getWorlds())for(Entity e:w.getEntities())if(e.getScoreboardTags().contains("pipi_pet_worker")||e.getScoreboardTags().contains(LABEL_TAG))e.remove();Bukkit.getScheduler().runTaskTimer(plugin,this::followLabels,40,2);}
 private boolean ready(String skin){return skin!=null&&TYPES.containsKey(skin);}
 private String skin(LivingEntity e){String s=e.getPersistentDataContainer().get(species,PersistentDataType.STRING);if(s==null&&e.getType().name().startsWith("PIPIFACILITIES_PET_"))s=e.getType().name().substring(19).toLowerCase(java.util.Locale.ROOT);return s;}
 private boolean attach(LivingEntity e){String skin=skin(e);if(dedicated(e)){var previous=ModelEngineAPI.getModeledEntity(e);if(previous!=null){previous.removeModel("pipi_"+skin);previous.setBaseEntityVisible(true);}return true;}if(!ready(skin))return false;if(models.containsKey(e.getUniqueId()))return true;
  try{var modeled=ModelEngineAPI.getOrCreateModeledEntity(e);var model=ModelEngineAPI.createActiveModel("pipi_"+skin);model.setScale(.65);modeled.addModel(model,true);modeled.setBaseEntityVisible(false);models.put(e.getUniqueId(),model);animation(e,"idle");return true;}catch(RuntimeException|LinkageError ex){if(!warned){plugin.getLogger().warning("전용 펫 모델 연결 실패: "+ex);warned=true;}return false;}
 }
 private void animation(LivingEntity e,String name){var model=models.get(e.getUniqueId());if(model==null||name.equals(animations.get(e.getUniqueId())))return;String old=animations.put(e.getUniqueId(),name);if(old!=null)model.getAnimationHandler().stopAnimation(old);model.getAnimationHandler().playAnimation(name,.2,.2,1,true);}
 private LivingEntity spawn(Location at,String skin,boolean worker){LivingEntity e=createEntity(at,skin);if(e==null)return null;e.getPersistentDataContainer().set(species,PersistentDataType.STRING,skin);e.setRemoveWhenFarAway(false);e.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(plugin.petMaxHp(skin));e.setHealth(e.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());if(skin.equals("otter")){e.setMaximumAir(Integer.MAX_VALUE);e.setRemainingAir(Integer.MAX_VALUE);}if(e instanceof Ageable age)eSafeAdult(age);if(worker){e.addScoreboardTag("pipi_pet_worker");e.setAI(false);e.setInvulnerable(true);e.setSilent(true);e.setCollidable(false);e.setPersistent(false);}else e.setPersistent(true);if(!attach(e)){e.remove();return null;}return e;}
 /** Rideable pet for Mounts: not a worker, not saved with the chunk, invulnerable. Null when the entity cannot be created. */
 LivingEntity spawnMount(Location at,String skin){if(!ready(skin))return null;LivingEntity e=spawn(at,skin,false);if(e==null)return null;e.addScoreboardTag(Mounts.TAG);e.setPersistent(false);e.setInvulnerable(true);e.setRotation(at.getYaw(),0);return e;}
 /** Static, invulnerable, non-persistent display pet (tutorial guide). Null when the model or entity cannot be created. */
 LivingEntity spawnDisplay(Location at,String skin){if(!ready(skin))return null;LivingEntity e=spawn(at,skin,true);if(e!=null)e.setRotation(at.getYaw(),0);return e;}
 private LivingEntity createEntity(Location at,String skin){try{Object world=at.getWorld().getClass().getMethod("getHandle").invoke(at.getWorld());Class<?> bridge=Class.forName("dev.pipi.facilities.PipiPets");Object nms=bridge.getMethod("spawn",Object.class,String.class,double.class,double.class,double.class).invoke(null,world,skin,at.getX(),at.getY(),at.getZ());return (LivingEntity)nms.getClass().getMethod("getBukkitEntity").invoke(nms);}catch(ReflectiveOperationException ex){plugin.getLogger().severe("전용 펫 엔티티 생성 실패: PipiFacilities 서버/클라이언트 버전 일치 확인: "+ex);return null;}}
 private boolean dedicated(LivingEntity e){try{return e.getClass().getMethod("getHandle").invoke(e).getClass().getName().equals("dev.pipi.facilities.PipiPets$Pet");}catch(ReflectiveOperationException ex){return false;}}
 private void eSafeAdult(Ageable e){e.setAdult();e.setAgeLock(true);}
 boolean command(Player p,String[] args){if(!p.isOp()){p.sendMessage("관리자 전용 명령어입니다.");return true;}if(args.length>=2){PipiLife.Pet alias=PipiLife.Pet.byCommand(args[1]);if(alias!=null)args[1]=alias.name().toLowerCase(Locale.ROOT);}if(args.length<2||!TYPES.containsKey(args[1])){StringJoiner usage=new StringJoiner("|");for(PipiLife.Pet pt:PipiLife.Pet.values())usage.add(pt.cmd()+"("+pt.label+")");p.sendMessage("§e/pipilife petspawn §f"+usage+" §7[1~5] [shiny]");return true;}String skin=args[1];if(!ready(skin)){p.sendMessage("지원하지 않는 전용 펫 종류입니다.");return true;}if(!plugin.isWildWorld(p.getWorld().getName())){p.sendMessage("야생 월드에서 사용하세요.");return true;}int count=1;try{if(args.length>2)count=Math.clamp(Integer.parseInt(args[2]),1,5);}catch(NumberFormatException ignored){}boolean shinyArg=args.length>3&&args[3].equalsIgnoreCase("shiny");int made=0;for(int i=0;i<count;i++){LivingEntity sp=spawn(p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(2)).add(i*.8,0,0),skin,false);if(sp!=null){made++;if(shinyArg)setShiny(sp,true);}}p.sendMessage("전용 펫 "+made+"마리 소환. 몽둥이·포획틀로 테스트하세요.");return true;}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void noNaturalAnimals(CreatureSpawnEvent event){World world=event.getLocation().getWorld();if(!plugin.isWildWorld(world.getName()))return;CreatureSpawnEvent.SpawnReason reason=event.getSpawnReason();
  // only our own spawns (plugin CUSTOM, commands, eggs, breeding of captured pets) may add animals to the wild world; on the hybrid server natural/chunk spawns arrive with other reasons too
  if(reason==CreatureSpawnEvent.SpawnReason.CUSTOM||reason==CreatureSpawnEvent.SpawnReason.COMMAND||reason==CreatureSpawnEvent.SpawnReason.SPAWNER_EGG||reason==CreatureSpawnEvent.SpawnReason.BREEDING||reason==CreatureSpawnEvent.SpawnReason.DISPENSE_EGG)return;
  if(wildAnimal(event.getEntity()))event.setCancelled(true);}
 private boolean wildAnimal(Entity e){if(!(e instanceof Animals||e instanceof Bat||e instanceof Squid||e instanceof Fish||e instanceof Axolotl||e instanceof Strider))return false;if(e.getPersistentDataContainer().has(species,PersistentDataType.STRING)||e.getScoreboardTags().contains("pipi_pet_worker"))return false;if(e instanceof Tameable t&&t.isTamed())return false;if(e.getCustomName()!=null||!e.getPassengers().isEmpty()||e.getVehicle()!=null)return false;if(e instanceof LivingEntity le&&le.isLeashed())return false;return true;}
 /** Chunk-generation animals (and ones from before the rule) never fire a spawn event we can cancel: remove them when their chunk loads. */
 @EventHandler public void clearWildAnimals(org.bukkit.event.world.EntitiesLoadEvent event){if(!plugin.isWildWorld(event.getWorld().getName()))return;for(Entity e:event.getEntities())if(wildAnimal(e))e.remove();}
 void spawnWild(){
  int cap=Math.max(1,plugin.getConfig().getInt("pet-spawn.cap",8));
  boolean transportUnlocked=plugin.getConfig().getBoolean("transport-unlocked",false);
  double transportChance=plugin.getConfig().getDouble("pet-spawn.transport-chance",.005);
  ThreadLocalRandom rnd=ThreadLocalRandom.current();
  for(World world:Bukkit.getWorlds()){
   if(!plugin.isWildWorld(world.getName()))continue;
   boolean nether=world.getEnvironment()==World.Environment.NETHER;
   for(Player player:world.getPlayers()){cull(player,cap);for(int attempt=0;attempt<3;attempt++)trySpawn(world,player,nether,cap,transportUnlocked,transportChance,rnd);}
  }
 }
 /** Wild pet entities near the player. Counted by entity type too: the species tag of a mod entity may be missing after a chunk reload. */
 private List<LivingEntity> wildPetsNear(Player player){List<LivingEntity> out=new ArrayList<>();for(Entity e:player.getNearbyEntities(64,64,64)){if(!(e instanceof LivingEntity l)||l.getScoreboardTags().contains("pipi_pet_worker")||l.isLeashed()||l.getCustomName()!=null)continue;if(skin(l)!=null||l.getType().name().startsWith("PIPIFACILITIES_PET_"))out.add(l);}return out;}
 /** Over the cap near a player (older spawns that piled up): remove the farthest wild pets that are not hurt (not being hunted). */
 private void cull(Player player,int cap){List<LivingEntity> pets=wildPetsNear(player);if(pets.size()<=cap)return;Location at=player.getLocation();pets.sort(Comparator.comparingDouble((LivingEntity l)->l.getLocation().distanceSquared(at)).reversed());int extra=pets.size()-cap;for(LivingEntity l:pets){if(extra<=0)break;var max=l.getAttribute(Attribute.GENERIC_MAX_HEALTH);if(max!=null&&l.getHealth()<max.getValue())continue;l.remove();extra--;}}
 private void trySpawn(World world,Player player,boolean nether,int cap,boolean transportUnlocked,double transportChance,ThreadLocalRandom rnd){
  int nearby=wildPetsNear(player).size();
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
  LivingEntity wild=spawn(spot,pet.name().toLowerCase(Locale.ROOT),false);
  if(wild!=null&&rnd.nextDouble()<plugin.getConfig().getDouble("pet-spawn.shiny-chance",.01)){
   setShiny(wild,true);
   for(Player near:spot.getWorld().getPlayers())if(near.getLocation().distanceSquared(spot)<64*64){near.sendTitle("","§e✦ 근처에 빛나는 "+pet.label+"이(가) 나타났다!",5,50,15);near.playSound(near.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_CHIME,1f,1.4f);}
  }
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
 private static final Map<String,String> BIOME_KO=Map.ofEntries(Map.entry("meadow","초원"),Map.entry("forest","숲"),Map.entry("plains","평원"),Map.entry("cherry_grove","벚나무 숲"),Map.entry("desert","사막"),Map.entry("warm_ocean","따뜻한 바다"),Map.entry("windswept_gravelly_hills","바람이 세찬 자갈투성이 언덕"),Map.entry("mushroom_fields","버섯섬"),Map.entry("ice_spikes","역고드름"),Map.entry("wooded_badlands","나무가 우거진 악지"),Map.entry("sunflower_plains","해바라기 평원"),Map.entry("deep_dark","딥 다크"),Map.entry("flower_forest","꽃 숲"),Map.entry("mangrove_swamp","맹그로브 늪"),Map.entry("stony_peaks","돌 봉우리"),Map.entry("fortress","네더 요새"),Map.entry("bastion","바스티온"));
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
    p.sendMessage("§f"+pet.label+" §7("+pet.cmd()+") §eLv"+pet.level+" §7| §b"+placeName(pet.place)+" §7| §a시도당 "+chance);
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
   p.sendMessage("§f"+pet.label+" §7("+pet.cmd()+") §eLv"+pet.level+" §7| §a시도당 "+chance);shown++;
  }
  if(shown==0)p.sendMessage("§7이 바이옴에서는 스폰되는 펫이 없습니다.");
  int interval=Math.max(1,plugin.getConfig().getInt("pet-spawn.interval-ticks",200)),cap=Math.max(1,plugin.getConfig().getInt("pet-spawn.cap",8));
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
 // Worker pets/labels are setPersistent(false), but this hybrid server still saves them to chunk files, so saved copies come back as
 // untracked orphans on every reload (634 piled up). Remove untracked ones on load, remove tracked ones before unload, and sweep periodically.
 private boolean transientTag(Entity e){var t=e.getScoreboardTags();return t.contains("pipi_pet_worker")||t.contains(LABEL_TAG);}
 private boolean tracked(UUID id){for(Worker w:workers.values())if(w.entity.getUniqueId().equals(id)||(w.label!=null&&w.label.getUniqueId().equals(id)))return true;return false;}
 private int purgeOrphans(Collection<? extends Entity> entities){int n=0;for(Entity e:entities)if(transientTag(e)&&!tracked(e.getUniqueId())){release(e.getUniqueId());e.remove();n++;}return n;}
 @EventHandler public void orphansOnLoad(EntitiesLoadEvent event){purgeOrphans(new ArrayList<>(event.getEntities()));}
 @EventHandler public void workersOnUnload(EntitiesUnloadEvent event){Set<UUID> gone=new HashSet<>();for(Entity e:event.getEntities())gone.add(e.getUniqueId());
  for(var it=workers.entrySet().iterator();it.hasNext();){Worker w=it.next().getValue();if(gone.contains(w.entity.getUniqueId())||(w.label!=null&&gone.contains(w.label.getUniqueId()))){dropLabel(w);release(w.entity.getUniqueId());w.entity.remove();it.remove();}}
  for(Entity e:event.getEntities())if(transientTag(e)&&e.isValid())e.remove();}
 private int sweepTick;
 void tick(){
  if(++sweepTick%20==0){int n=0;for(World w:Bukkit.getWorlds())n+=purgeOrphans(new ArrayList<>(w.getEntities()));if(n>0)plugin.getLogger().info("저장돼 있던 작업 펫 사본 "+n+"개 정리");}
  for(World w:Bukkit.getWorlds())for(Player p:w.getPlayers())for(Entity e:p.getNearbyEntities(32,20,32))if(e instanceof LivingEntity l&&skin(l)!=null&&!l.getScoreboardTags().contains("pipi_pet_worker")){if(!dedicated(l)){double hp=l.getHealth();LivingEntity replacement=spawn(l.getLocation(),skin(l),false);if(replacement!=null){if(isShiny(l))setShiny(replacement,true);replacement.setHealth(Math.min(hp,replacement.getMaxHealth()));release(l.getUniqueId());l.remove();}}else attach(l);}
  for(UUID id:new ArrayList<>(models.keySet())){Entity e=Bukkit.getEntity(id);if(!(e instanceof LivingEntity l)||!e.isValid()||l.isDead()){release(id);continue;}models.get(id).setScale(l.getPersistentDataContainer().has(NamespacedKey.fromString("pipilife:capture_restore"),PersistentDataType.INTEGER)?0:.65);if(!e.getScoreboardTags().contains("pipi_pet_worker"))animation(l,l.getVelocity().lengthSquared()>.006?"walk":"idle");}
  Set<String>wanted=new HashSet<>();long now=System.currentTimeMillis();var scenes=plugin.petScenes();for(var scene:scenes){
   try{tickScene(scene,wanted,now);}catch(RuntimeException ex){if(failedScenes.add(scene.key()))plugin.getLogger().warning("펫 작업 장면 오류 ("+scene.key()+"): "+ex);}
  }for(String key:new ArrayList<>(workers.keySet()))if(!wanted.contains(key)){Worker w=workers.remove(key);dropLabel(w);release(w.entity.getUniqueId());w.entity.remove();}
  workAudio(scenes);
 }
 private final Set<String> failedScenes=new HashSet<>();
 private boolean chunkLoaded(Location l){return l.getWorld()!=null&&l.getWorld().isChunkLoaded(l.getBlockX()>>4,l.getBlockZ()>>4);}
 private void tickScene(PipiLife.PetScene scene,Set<String>wanted,long now){
   if(!ready(scene.species())||!chunkLoaded(scene.station()))return;wanted.add(scene.key());Worker worker=workers.get(scene.key());if(worker==null||!worker.entity.isValid()){if(worker!=null)dropLabel(worker);LivingEntity e=spawn(scene.station(),scene.species(),true);if(e==null)return;if(scene.shiny())setShiny(e,true);worker=new Worker(e);workers.put(scene.key(),worker);}
   worker.visiting=scene.feeder()!=null;
   String status=scene.status();if("작업 중".equals(status)&&!scene.working())status="대기 중";label(worker,status);
   worker.food=scene.food();Location target=worker.visiting?scene.feeder():scene.station();if(target==null)target=scene.station();var e=worker.entity;
   boolean ranch=scene.task()==RANCH_TASK&&!worker.visiting;if(ranch){ranchTarget(worker,scene,now);target=worker.wander;}else{worker.wander=null;worker.still=false;}
   int action=ranch?worker.wanderAction:scene.working()?2:0;if(worker.visiting&&scene.eating()&&arrived(e,target))action=3;
   try{Object nms=e.getClass().getMethod("getHandle").invoke(e);Class.forName("dev.pipi.facilities.PipiPets").getMethod("worker",Object.class,int.class,double.class,double.class,double.class,int.class,String.class).invoke(null,nms,action,target.getX(),target.getY(),target.getZ(),scene.task(),scene.soundGroup());Location focus=worker.visiting?target.clone().add(0,0,1):ranch?(worker.look!=null?worker.look:target.clone().add(0,0,1)):scene.focus();Class.forName("dev.pipi.facilities.PipiPets").getMethod("face",Object.class,double.class,double.class).invoke(null,nms,focus.getX(),focus.getZ());}catch(ReflectiveOperationException ex){if(!warned){warned=true;plugin.getLogger().warning("펫 작업 동작 연결 실패: "+ex);}}
 }
 private boolean arrived(LivingEntity e,Location target){try{Object nms=e.getClass().getMethod("getHandle").invoke(e);return (boolean)Class.forName("dev.pipi.facilities.PipiPets").getMethod("reached",Object.class,double.class,double.class,double.class).invoke(null,nms,target.getX(),target.getY(),target.getZ());}catch(ReflectiveOperationException ex){return false;}}
 private void workAudio(java.util.List<PipiLife.PetScene> scenes){
  for(Player player:Bukkit.getOnlinePlayers()){
   Map<String,PipiLife.PetScene> audible=new TreeMap<>();
   for(var scene:scenes){if(!scene.working()||!scene.station().getWorld().equals(player.getWorld())||scene.station().distanceSquared(player.getLocation())>256)continue;Worker worker=workers.get(scene.key());if(worker!=null&&(worker.visiting||!arrived(worker.entity,scene.station())))continue;audible.putIfAbsent(scene.soundGroup(),scene);}
   StringJoiner packet=new StringJoiner("|");for(var scene:audible.values()){Location at=scene.station();String key=Base64.getUrlEncoder().withoutPadding().encodeToString(scene.soundGroup().getBytes(java.nio.charset.StandardCharsets.UTF_8));packet.add(key+"~"+scene.task()+"~"+at.getX()+"~"+at.getY()+"~"+at.getZ());}player.sendMessage("PIPI_WORK_AUDIO:"+packet);
  }
 }

 @EventHandler public void unload(ChunkUnloadEvent event){for(Entity e:event.getChunk().getEntities()){if(e.getScoreboardTags().contains(LABEL_TAG))e.remove();else if(models.containsKey(e.getUniqueId()))release(e.getUniqueId());}}
 void close(){for(Worker w:workers.values()){dropLabel(w);release(w.entity.getUniqueId());w.entity.remove();}workers.clear();for(UUID id:new ArrayList<>(models.keySet()))release(id);}
}
