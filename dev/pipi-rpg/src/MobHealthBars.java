package dev.pipi.life;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.*;
import org.bukkit.persistence.PersistentDataType;
/** Camera-facing solid health bar, independent of entity names and NPC metadata. */
final class MobHealthBars implements Listener {
 private final PipiLife plugin; private final NamespacedKey capturing;
 private final Map<UUID,List<BlockDisplay>> labels=new HashMap<>(); private int updates;
 MobHealthBars(PipiLife plugin){this.plugin=plugin;capturing=new NamespacedKey(plugin,"capture_restore");for(World w:Bukkit.getWorlds())for(Entity e:w.getEntities())if(e.getScoreboardTags().contains("pipi_mob_health"))e.remove();}
 private boolean shown(LivingEntity e){return !(e instanceof Player)&&!(e instanceof ArmorStand)&&!(e instanceof Villager)&&!e.isDead()&&e.isValid()&&!e.isInvisible()&&!e.getPersistentDataContainer().has(capturing,PersistentDataType.INTEGER);}
 static double ratio(double hp,double max){return Math.clamp(hp/Math.max(1,max),0,1);}
 // Retained only for diagnostics, not rendered as text.
 static String text(double hp,double max){return String.format(Locale.ROOT,"%.1f / %.1f",hp,max);}
 private BlockDisplay part(LivingEntity e,Material material){return e.getWorld().spawn(e.getLocation().add(0,e.getHeight()+.3,0),BlockDisplay.class,d->{d.setBlock(material.createBlockData());d.setBillboard(Display.Billboard.CENTER);d.setPersistent(false);d.setViewRange(.5f);d.setShadowRadius(0);d.setTeleportDuration(5);d.setDisplayWidth(2);d.setDisplayHeight(.3f);d.addScoreboardTag("pipi_mob_health");d.setBrightness(new Display.Brightness(material==Material.BLACK_CONCRETE?0:15,material==Material.BLACK_CONCRETE?0:15));});}
 private void shape(BlockDisplay d,float x,float y,float z,float width,float height){var t=d.getTransformation();t.getTranslation().set(x,y,z);t.getScale().set(width,height,.015f);d.setTransformation(t);}
 void tick(){updates++;
  if(updates%4==1){Set<UUID>wanted=new HashSet<>();for(Player p:Bukkit.getOnlinePlayers())for(Entity entity:p.getNearbyEntities(20,14,20)){if(wanted.size()>=160)break;if(entity instanceof LivingEntity e&&shown(e)){wanted.add(e.getUniqueId());if(!labels.containsKey(e.getUniqueId()))labels.put(e.getUniqueId(),List.of(part(e,Material.WHITE_CONCRETE),part(e,Material.BLACK_CONCRETE),part(e,Material.RED_CONCRETE)));}}
   for(UUID id:new ArrayList<>(labels.keySet()))if(!wanted.contains(id))remove(id);
  }
  for(UUID id:new ArrayList<>(labels.keySet())){Entity entity=Bukkit.getEntity(id);var parts=labels.get(id);if(!(entity instanceof LivingEntity e)||!shown(e)||parts.stream().anyMatch(d->!d.isValid())){remove(id);continue;}
   Location pos=e.getLocation().add(0,e.getHeight()+.3,0);for(BlockDisplay d:parts)d.teleport(pos);
   shape(parts.get(0),-.8f,-.06f,0,1.6f,.12f);shape(parts.get(1),-.775f,-.035f,.018f,1.55f,.07f);shape(parts.get(2),-.775f,-.035f,.036f,(float)(1.55*ratio(e.getHealth(),e.getMaxHealth())),.07f);
  }
 }
 private void remove(UUID id){var parts=labels.remove(id);if(parts!=null)for(Entity d:parts)d.remove();}
 @EventHandler public void death(EntityDeathEvent e){remove(e.getEntity().getUniqueId());}
 @EventHandler public void unload(ChunkUnloadEvent e){for(Entity entity:e.getChunk().getEntities())remove(entity.getUniqueId());}
 @EventHandler public void load(ChunkLoadEvent e){for(Entity entity:e.getChunk().getEntities())if(entity.getScoreboardTags().contains("pipi_mob_health"))entity.remove();}
 void close(){for(UUID id:new ArrayList<>(labels.keySet()))remove(id);}
}
