package dev.pipi.life;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

final class CaptureSystem implements Listener {
    private final PipiLife plugin;
    private final NamespacedKey shotKey, stateKey;
    private final Map<UUID,Charge> charges=new HashMap<>();
    private final Map<UUID,Attempt> attempts=new HashMap<>();
    private final Map<UUID,Long> clubHits=new HashMap<>();
    private long tick;
    private record Charge(long start,long heartbeat,int slot,int tier) {}
    private record Attempt(UUID owner,LivingEntity target,ItemDisplay cage,TextDisplay ui,Location origin,long start,int passes,double scale,int[] filled,String name) {}
    private final NamespacedKey scaleKey;
    CaptureSystem(PipiLife plugin){this.plugin=plugin;shotKey=new NamespacedKey(plugin,"capture_projectile");stateKey=new NamespacedKey(plugin,"capture_restore");scaleKey=new NamespacedKey(plugin,"capture_scale");
        for(World w:Bukkit.getWorlds())for(Entity e:w.getEntities()){if(e instanceof LivingEntity l)restore(l);if(e.getScoreboardTags().contains("pipi_capture_cage")||e.getScoreboardTags().contains("pipi_capture_ui"))e.remove();}
    }
    private static String slots(int filled,int failed){
        StringBuilder sb=new StringBuilder();
        for(int i=0;i<3;i++){if(i>0)sb.append(' ');sb.append(i==failed?"§c✖":i<filled?"§e●":"§7○");}
        return sb.toString();
    }
    private void setUi(Attempt a,String text){if(a.ui.isValid())a.ui.setText(text);}
    private String uiText(Attempt a,int failed,String extra){return slots(a.filled[0],failed)+"\n"+(extra!=null?extra:"§7"+a.name+"의 포획");}
    private void later(Runnable r,long delay){
        if(!plugin.isEnabled()){r.run();return;}
        Bukkit.getScheduler().runTaskLater(plugin,r,delay);
    }
    private void rock(ItemDisplay cage,float angle,int duration){
        if(!cage.isValid())return;
        var t=cage.getTransformation();t.getLeftRotation().rotationZ(angle);
        cage.setInterpolationDelay(0);cage.setInterpolationDuration(duration);cage.setTransformation(t);
    }
    private void setScale(LivingEntity e,double v){
        var attr=e.getAttribute(org.bukkit.attribute.Attribute.GENERIC_SCALE);
        if(attr!=null)attr.setBaseValue(v);
    }
    private void bar(Player player,String text){player.spigot().sendMessage(ChatMessageType.ACTION_BAR,TextComponent.fromLegacyText(text));}
    boolean command(Player player,String[] args){
        if(args.length!=2)return true;
        String action=args[1]; UUID id=player.getUniqueId();
        if(action.equals("cancel")){charges.remove(id);return true;}
        Integer tier=plugin.trapTier(player.getInventory().getItemInMainHand());
        if(tier==null||!captureWorld(player)||player.isDead()){charges.remove(id);if(action.equals("start"))bar(player,"§e야생 월드의 생존·크리에이티브 모드에서 포획틀을 사용하세요.");return true;}
        Charge c=charges.get(id);
        if(action.equals("start")){
            if(c==null&&!player.hasCooldown(Material.TRIPWIRE_HOOK))charges.put(id,new Charge(tick,tick,player.getInventory().getHeldItemSlot(),tier));
        }else if(action.equals("keep")&&valid(player,c,tier))charges.put(id,new Charge(c.start,tick,c.slot,c.tier));
        else if(action.equals("release")){
            charges.remove(id);
            if(valid(player,c,tier))launch(player,c);
        }
        return true;
    }
    private boolean valid(Player p,Charge c,int tier){return c!=null&&c.tier==tier&&c.slot==p.getInventory().getHeldItemSlot()&&tick-c.heartbeat<=30;}
    private long pending(Player p){return attempts.values().stream().filter(a->a.owner.equals(p.getUniqueId())).count();}
    private boolean hasRoom(Player p){return plugin.captureFreeSlots(p)-pending(p)>0;}
    static boolean allowedMode(GameMode mode){return mode==GameMode.SURVIVAL || mode==GameMode.CREATIVE;}
    private boolean captureWorld(Player player){return wild(player)&&allowedMode(player.getGameMode());}
    private boolean wild(LivingEntity entity){return plugin.isWildWorld(entity.getWorld().getName());}
    private boolean protectedPet(LivingEntity e){return wild(e)&&(attempts.containsKey(e.getUniqueId())||plugin.captureEligible(e));}
    static int clubTier(ItemStack item){
        if(item==null||!item.hasItemMeta())return -1;
        String key=item.getItemMeta().getPersistentDataContainer().get(NamespacedKey.fromString("pipilife:processed_product"),PersistentDataType.STRING);
        if(key==null)return -1;
        return switch(key){case "club"->item.getType()==Material.STICK?0:-1;case "ironclub"->item.getType()==Material.IRON_SWORD?1:-1;case "diamondclub"->item.getType()==Material.DIAMOND_SWORD?2:-1;default->-1;};
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void damage(EntityDamageEvent event){
        if(!(event.getEntity() instanceof LivingEntity entity)||!protectedPet(entity))return;
        if(attempts.containsKey(entity.getUniqueId())){event.setCancelled(true);return;}
        // Environmental damage is real. Combat weapons, tools, projectiles and skill damage cannot hurt wild pets.
        if(!(event instanceof EntityDamageByEntityEvent hit))return;
        if(!(hit.getDamager() instanceof Player player)){event.setCancelled(true);return;}
        int tier=clubTier(player.getInventory().getItemInMainHand());
        if(tier<0 || hit.getCause()!=EntityDamageEvent.DamageCause.ENTITY_ATTACK){event.setCancelled(true);return;}
        if(event.isCancelled() || !captureWorld(player))return;
        if(clubHits.getOrDefault(entity.getUniqueId(),0L)>tick){event.setCancelled(true);return;}
        clubHits.put(entity.getUniqueId(),tick+10);
        boolean critical=player.getFallDistance()>0 && !player.isOnGround() && !player.isInsideVehicle()
            && !player.isSprinting() && !player.hasPotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS)
            && !player.getLocation().getBlock().isLiquid() && !Tag.CLIMBABLE.isTagged(player.getLocation().getBlock().getType());
        event.setDamage(CaptureMath.clubDamage(tier,critical));
        // Let Minecraft apply health loss, red flash, knockback, hurt sound and death normally.
        if(critical)entity.getWorld().spawnParticle(Particle.CRIT,entity.getLocation().add(0,entity.getHeight()*.5,0),12,.2,.3,.2,.05);
    }
    // Suppress ordinary interactions; the client sends only hold/release intent, never a success result.
    @EventHandler(priority=EventPriority.HIGHEST)
    public void interact(PlayerInteractEvent event){
        if(event.getHand()!=EquipmentSlot.HAND||plugin.trapTier(event.getItem())==null)return;
        if(event.getAction()==org.bukkit.event.block.Action.RIGHT_CLICK_AIR||event.getAction()==org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK){
            event.setCancelled(true);
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void interactEntity(PlayerInteractEntityEvent event){if(event.getHand()==EquipmentSlot.HAND&&plugin.trapTier(event.getPlayer().getInventory().getItemInMainHand())!=null)event.setCancelled(true);}
    private void launch(Player player,Charge c){
        if(!hasRoom(player)){bar(player,"§c펫 창고가 가득 찼습니다.");return;}
        ItemStack held=player.getInventory().getItemInMainHand();
        Snowball shot=player.launchProjectile(Snowball.class);
        if(!shot.isValid())return;
        ItemStack visual=plugin.trapItem(c.tier);shot.setItem(visual);
        shot.getPersistentDataContainer().set(shotKey,PersistentDataType.INTEGER,c.tier);
        shot.setPersistent(false);
        shot.setVelocity(player.getEyeLocation().getDirection().multiply(CaptureMath.throwSpeed(tick-c.start)));
        ItemStack left=held.clone();left.setAmount(held.getAmount()-1);player.getInventory().setItemInMainHand(left);
        player.setCooldown(Material.TRIPWIRE_HOOK,12);
        player.getWorld().playSound(player.getLocation(),Sound.ENTITY_SNOWBALL_THROW,.7f,.75f);
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin,()->{if(shot.isValid())shot.remove();},200);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void hit(ProjectileHitEvent event){
        if(!(event.getEntity() instanceof Snowball shot))return;
        Integer tier=shot.getPersistentDataContainer().get(shotKey,PersistentDataType.INTEGER);if(tier==null)return;
        shot.remove();
        if(!(shot.getShooter() instanceof Player player)||!player.isOnline()||!captureWorld(player)||player.isDead()||!player.getWorld().equals(shot.getWorld()))return;
        LivingEntity target=impactTarget(event.getHitEntity(),shot.getLocation());
        if(target==null||attempts.containsKey(target.getUniqueId())||!plugin.captureEligible(target)) {bar(player,"§c포획 가능한 야생 성체 펫을 맞혀주세요.");return;}
        if(!hasRoom(player)){bar(player,"§c펫 창고가 가득 찼습니다.");return;}
        double probability=probability(target,tier);
        ThreadLocalRandom random=ThreadLocalRandom.current();
        int passes=CaptureMath.outcome(target.getHealth(),target.getMaxHealth(),plugin.captureRate(target),tier,
            random.nextInt(65536),random.nextInt(65536),random.nextInt(65536));
        Location origin=target.getLocation().clone();
        ItemDisplay cage=null;TextDisplay ui=null;
        try {
            cage=target.getWorld().spawn(origin.clone().add(0,.3,0),ItemDisplay.class,d->{
                d.setItemStack(plugin.trapItem(tier));d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
                d.setPersistent(false);d.addScoreboardTag("pipi_capture_cage");d.setDisplayWidth(2);d.setDisplayHeight(2);
                var t=d.getTransformation();t.getScale().set(.85f);d.setTransformation(t);d.setInterpolationDuration(4);
            });
            String pname=player.getName();
            ui=target.getWorld().spawn(origin.clone().add(0,1.4,0),TextDisplay.class,d->{
                d.setPersistent(false);d.addScoreboardTag("pipi_capture_cage");d.addScoreboardTag("pipi_capture_ui");
                d.setBillboard(Display.Billboard.CENTER);d.setSeeThrough(false);d.setShadowed(false);
                d.setAlignment(TextDisplay.TextAlignment.CENTER);d.setDisplayWidth(2);d.setDisplayHeight(2);
                d.setText(slots(0,-1)+"\n§7"+pname+"의 포획");
                var t=d.getTransformation();t.getScale().set(.8f);d.setTransformation(t);
            });
            double s0=1.0;var sattr=target.getAttribute(org.bukkit.attribute.Attribute.GENERIC_SCALE);if(sattr!=null)s0=sattr.getBaseValue();
            freeze(target,s0);
            attempts.put(target.getUniqueId(),new Attempt(player.getUniqueId(),target,cage,ui,origin,tick,passes,s0,new int[]{0},pname));
            target.getWorld().spawnParticle(Particle.PORTAL,origin.clone().add(0,.5,0),35,.3,.4,.3,.05);
            bar(player,"§b포획 시작 · 최종 성공 확률 "+String.format(Locale.ROOT,"%.1f%%",probability*100));
        }catch(RuntimeException ex){if(cage!=null)cage.remove();if(ui!=null)ui.remove();restore(target);plugin.getLogger().warning("포획 연출 시작 실패: "+ex.getMessage());}
    }
    private double probability(LivingEntity e,int tier){return CaptureMath.probability(e.getHealth(),e.getMaxHealth(),plugin.captureRate(e),tier);}
    private LivingEntity impactTarget(Entity hit,Location location){
        if(hit instanceof LivingEntity living&&plugin.captureEligible(living))return living;
        if(hit==null)return null;
        LivingEntity best=null;double distance=2.25;
        for(Entity entity:location.getWorld().getNearbyEntities(location,1.5,1.5,1.5))if(entity instanceof LivingEntity pet&&plugin.captureEligible(pet)){
            var box=pet.getBoundingBox().expand(.25);var v=location.toVector();double dx=Math.max(box.getMinX()-v.getX(),Math.max(0,v.getX()-box.getMaxX()));double dy=Math.max(box.getMinY()-v.getY(),Math.max(0,v.getY()-box.getMaxY()));double dz=Math.max(box.getMinZ()-v.getZ(),Math.max(0,v.getZ()-box.getMaxZ()));double d=dx*dx+dy*dy+dz*dz;if(d<distance){best=pet;distance=d;}
        }return best;
    }
    private LivingEntity aimed(Player player){
        var eye=player.getEyeLocation();var direction=eye.getDirection();double nearest=24;LivingEntity found=null;
        var wall=player.getWorld().rayTraceBlocks(eye,direction,24,FluidCollisionMode.NEVER,true);
        if(wall!=null)nearest=wall.getHitPosition().distance(eye.toVector());
        for(var candidate:player.getNearbyEntities(25,25,25))if(candidate instanceof LivingEntity pet&&plugin.captureEligible(pet)&&!attempts.containsKey(pet.getUniqueId())){
            var hit=pet.getBoundingBox().expand(.22).rayTrace(eye.toVector(),direction,nearest);
            if(hit!=null){double distance=hit.getHitPosition().distance(eye.toVector());if(distance<=nearest){nearest=distance;found=pet;}}
        }
        return found;
    }
    void tick(){
        tick++;
        if(tick%200==0)clubHits.entrySet().removeIf(e->e.getValue()<tick);
        for(var entry:new ArrayList<>(charges.entrySet())){
            Player p=Bukkit.getPlayer(entry.getKey());Charge c=entry.getValue();
            Integer tier=p==null?null:plugin.trapTier(p.getInventory().getItemInMainHand());
            if(p==null||!p.isOnline()||p.isDead()||tier==null||!captureWorld(p)||!valid(p,c,tier)){charges.remove(entry.getKey());continue;}
            if(tick%4==0){LivingEntity target=aimed(p);p.sendMessage("PIPI_CAPTURE_AIM:"+(target==null?-1:target.getEntityId())+":"+(target==null?0:String.format(Locale.ROOT,"%.1f",probability(target,tier)*100)));}
        }
        for(Attempt a:new ArrayList<>(attempts.values())){
            Player p=Bukkit.getPlayer(a.owner);long elapsed=tick-a.start;
            if(p==null||!p.isOnline()||p.isDead()||!captureWorld(p)||!p.getWorld().equals(a.target.getWorld())||!a.target.isValid()||!a.cage.isValid()||p.getLocation().distanceSquared(a.origin)>4096){finish(a,false,"포획이 중단되었습니다.");continue;}
            // Shrink the pet into the frame over the first 10 ticks, then hide it for the shakes.
            if(elapsed>=1&&elapsed<=10){
                double f=elapsed/10.0;
                setScale(a.target,a.scale+(Math.max(.1,a.scale*.1)-a.scale)*f);
                var w=a.origin.getWorld();
                Location from=a.origin.clone().add(0,a.target.getHeight()*.5,0);
                Location to=a.origin.clone().add(0,.5,0);
                Location pos=from.clone().add(to.clone().subtract(from).toVector().multiply(f));
                w.spawnParticle(Particle.END_ROD,pos,3,.25*(1-f),.25*(1-f),.25*(1-f),.02);
                w.spawnParticle(Particle.PORTAL,pos,4,.2*(1-f),.2*(1-f),.2*(1-f),.1);
                if(elapsed==10){a.target.setInvisible(true);a.target.setGlowing(false);}
            }
            // Each visible shake is a check; a failed check breaks out at that shake.
            if(elapsed>=20&&(elapsed-20)%20==0){
                int step=(int)((elapsed-20)/20);
                if(step>=3)continue;
                rock(a.cage,(step%2==0?1:-1)*.35f,5);
                a.origin.getWorld().playSound(a.origin,Sound.BLOCK_WOODEN_BUTTON_CLICK_ON,.9f,.8f+step*.15f);
                a.origin.getWorld().playSound(a.origin,Sound.BLOCK_CHAIN_HIT,.7f,1+step*.15f);
                bar(p,"§e포획틀 흔들림 "+(step+1)+" / 3");
            }
            if(elapsed>=28&&(elapsed-28)%20==0){
                int step=(int)((elapsed-28)/20);
                if(step>=3)continue;
                rock(a.cage,0f,5);
                if(a.passes<=step){setUi(a,uiText(a,step,"§c탈출!"));finish(a,false,"펫이 포획틀에서 빠져나왔습니다.",true);continue;}
                a.filled[0]=step+1;setUi(a,uiText(a,-1,null));
                if(step==2)finish(a,true,"");
            }
        }
    }
    private void freeze(LivingEntity e,double scale){
        int flags=(e.hasAI()?1:0)|(e.isInvisible()?2:0)|(e.isInvulnerable()?4:0)|(e.hasGravity()?8:0)|(e.isSilent()?16:0)|(e.isCollidable()?32:0)|(e.isGlowing()?64:0);
        e.getPersistentDataContainer().set(stateKey,PersistentDataType.INTEGER,flags);
        e.getPersistentDataContainer().set(scaleKey,PersistentDataType.DOUBLE,scale);
        // Invisibility is applied after the shrink animation (tick), so the pet visibly folds into the frame.
        e.setAI(false);e.setInvulnerable(true);e.setGravity(false);e.setSilent(true);e.setCollidable(false);e.setGlowing(true);e.setVelocity(new Vector());
    }
    private void restore(LivingEntity e){
        Double sc=e.getPersistentDataContainer().get(scaleKey,PersistentDataType.DOUBLE);
        if(sc!=null){setScale(e,sc);e.getPersistentDataContainer().remove(scaleKey);}
        Integer f=e.getPersistentDataContainer().get(stateKey,PersistentDataType.INTEGER);if(f==null)return;
        e.setAI((f&1)!=0);e.setInvisible((f&2)!=0);e.setInvulnerable((f&4)!=0);e.setGravity((f&8)!=0);e.setSilent((f&16)!=0);e.setCollidable((f&32)!=0);e.setGlowing((f&64)!=0);e.getPersistentDataContainer().remove(stateKey);
    }
    private void finish(Attempt a,boolean success,String message){finish(a,success,message,false);}
    private void finish(Attempt a,boolean success,String message,boolean escape){
        if(attempts.remove(a.target.getUniqueId())==null)return;
        restore(a.target);
        Player p=Bukkit.getPlayer(a.owner);
        boolean stored=success&&p!=null&&p.isOnline()&&a.target.isValid()&&plugin.completeCapture(p,a.target);
        var w=a.origin.getWorld();Location c=a.origin.clone().add(0,.5,0);
        ItemDisplay cage=a.cage;TextDisplay ui=a.ui;
        if(stored){
            w.spawnParticle(Particle.HAPPY_VILLAGER,c,30,.4,.4,.4,.02);
            w.spawnParticle(Particle.TOTEM_OF_UNDYING,c,25,.3,.5,.3,.3);
            w.spawnParticle(Particle.END_ROD,c,20,.4,.5,.4,.08);
            w.playSound(a.origin,Sound.ENTITY_PLAYER_LEVELUP,.8f,1.5f);
            if(cage.isValid()){cage.setBrightness(new Display.Brightness(15,15));cage.setGlowing(true);cage.setInterpolationDelay(0);cage.setInterpolationDuration(4);var t=cage.getTransformation();t.getScale().set(1.1f);cage.setTransformation(t);}
            if(ui.isValid()){ui.setText("§a포획 성공!");ui.setInterpolationDelay(0);ui.setInterpolationDuration(20);var t=ui.getTransformation();t.getTranslation().set(0f,.8f,0f);ui.setTransformation(t);}
            later(()->{cage.remove();},6);later(()->{ui.remove();},22);
            bar(p,"§a포획 성공! 펫 창고에 보관했습니다.");
        }else{
            if(escape){
                w.spawnParticle(Particle.POOF,c,15,.3,.3,.3,.04);
                w.playSound(a.origin,Sound.BLOCK_CHEST_OPEN,.8f,1.2f);
                if(cage.isValid()){cage.setInterpolationDelay(0);cage.setInterpolationDuration(3);var t=cage.getTransformation();t.getScale().set(1.4f);cage.setTransformation(t);}
                later(()->{cage.remove();},4);later(()->{ui.remove();},30);
                LivingEntity pet=a.target;
                if(pet.isValid()){
                    pet.setVelocity(new Vector(0,.4,0));
                    for(int i=0;i<3;i++)later(()->{if(pet.isValid()){pet.playHurtAnimation(0f);pet.getWorld().spawnParticle(Particle.ANGRY_VILLAGER,pet.getLocation().add(0,pet.getHeight()+.3,0),3,.25,.15,.25,0);}},5+i*10L);
                }
            }else{cage.remove();ui.remove();}
            if(p!=null&&p.isOnline())bar(p,"§e"+(message.isEmpty()?"펫을 저장하지 못해 야생으로 돌려보냈습니다.":message));
        }
    }
    @EventHandler public void leave(PlayerQuitEvent event){cancel(event.getPlayer());}
    @EventHandler public void change(PlayerChangedWorldEvent event){cancel(event.getPlayer());}
    private void cancel(Player p){charges.remove(p.getUniqueId());for(Attempt a:new ArrayList<>(attempts.values()))if(a.owner.equals(p.getUniqueId()))finish(a,false,"포획 중단");}
    @EventHandler public void unload(ChunkUnloadEvent event){for(Entity e:event.getChunk().getEntities())if(e instanceof LivingEntity l&&attempts.containsKey(e.getUniqueId()))finish(attempts.get(e.getUniqueId()),false,"포획 중단");}
    @EventHandler public void load(ChunkLoadEvent event){for(Entity e:event.getChunk().getEntities()){if(e instanceof LivingEntity l&&!attempts.containsKey(e.getUniqueId()))restore(l);if(e.getScoreboardTags().contains("pipi_capture_cage")||e.getScoreboardTags().contains("pipi_capture_ui"))e.remove();}}
    void close(){for(Attempt a:new ArrayList<>(attempts.values()))finish(a,false,"서버 종료로 포획 중단");charges.clear();clubHits.clear();}
}
