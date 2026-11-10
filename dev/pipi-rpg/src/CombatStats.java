package dev.pipi.life;

import java.util.*;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** Server authoritative equipment stats; no client-supplied combat values. */
final class CombatStats implements Listener {
    static final String TITLE="삐삐서버 · 캐릭터 정보";
    private final PipiLife plugin;
    private final TownSystems town;
    private static int baseLevel=100,tierStep=100,upgradeStep=5;
    private static double levelSlope=.25;
    CombatStats(PipiLife plugin,TownSystems town) {
        this.plugin=plugin;this.town=town;
        var c=plugin.getConfig();
        c.addDefault("combat.item-level.base",100);c.addDefault("combat.item-level.per-tier",100);c.addDefault("combat.item-level.per-upgrade",5);
        c.addDefault("combat.difference-slope",.25);
        for(int floor=1;floor<=10;floor++)c.addDefault("combat.dungeons.tower_%02d.item-level".formatted(floor),100+(floor-1)*50);
        c.options().copyDefaults(true);plugin.saveConfig();
        baseLevel=Math.max(1,c.getInt("combat.item-level.base"));tierStep=Math.max(0,c.getInt("combat.item-level.per-tier"));upgradeStep=Math.max(0,c.getInt("combat.item-level.per-upgrade"));
        levelSlope=Math.clamp(c.getDouble("combat.difference-slope"),0,1);
    }
    private static NamespacedKey key(String suffix) { return NamespacedKey.fromString("pipilife:smith_"+suffix); }
    static int value(ItemStack item,String name,int fallback) {
        return item==null || !item.hasItemMeta()?fallback:item.getItemMeta().getPersistentDataContainer().getOrDefault(key(name),PersistentDataType.INTEGER,fallback);
    }
    static String catalog(ItemStack item) {
        return item==null || !item.hasItemMeta()?"":item.getItemMeta().getPersistentDataContainer().getOrDefault(key("catalog"),PersistentDataType.STRING,"");
    }
    static boolean armor(ItemStack item) { return item!=null && item.getType().name().matches(".*_(HELMET|CHESTPLATE|LEGGINGS|BOOTS)"); }
    static boolean weapon(ItemStack item) { return item!=null && (item.getType().name().endsWith("_SWORD") || item.getType()==Material.BOW || item.getType()==Material.BLAZE_ROD); }
    static boolean eligible(ItemStack item) {
        return item!=null && item.getAmount()==1 && value(item,"lines",0)>=3 && (armor(item)||weapon(item));
    }
    static int tier(ItemStack item) {
        var match=java.util.regex.Pattern.compile("^([0-5])티어").matcher(catalog(item));
        return match.find()?Integer.parseInt(match.group(1)):0;
    }
    static int itemLevel(ItemStack item) { return eligible(item)?baseLevel+tier(item)*tierStep+Math.clamp(value(item,"level",0),0,20)*upgradeStep:0; }
    static boolean needsMigration(ItemStack item) { return eligible(item) && value(item,"combat_schema",0)!=1; }
    static int mainStat(ItemStack item) { return eligible(item)&&armor(item)?(tier(item)+1)*5+value(item,"level",0):0; }
    static int family(ItemStack item) { String name=catalog(item);return name.contains("원거리")?1:name.contains("마법")?2:0; }
    static void decorate(ItemStack item,ItemMeta meta,List<String> lore) {
        if(!eligible(item))return;
        meta.getPersistentDataContainer().set(key("combat_schema"),PersistentDataType.INTEGER,1);
        lore.add(0,"§6아이템레벨 "+itemLevel(item));
        if(armor(item)) {
            lore.add(1,"§7"+new String[]{"힘","민첩","지력"}[family(item)]+" +"+mainStat(item)+" · 행운 +"+((tier(item)+1)*2+value(item,"level",0)/2));
            lore.add(2,"§7기본 체력 +"+((tier(item)+1)*5+value(item,"level",0)));
            if(item.getType().name().endsWith("_LEGGINGS"))lore.add(3,"§7기본 이동속도 +"+(tier(item)+1)+"%");
        } else lore.add(1,"§7기본 치명타 확률 +"+(3+tier(item)+value(item,"level",0)*.1)+"%");
    }
    record Stats(double ilvl,double strength,double dexterity,double intelligence,double luck,double armorHp,double pantsSpeed,double crit,double buff,double cdr) {
        double attackBonus(){return strength*.1+dexterity*.2+intelligence*.1;}
        double hpBonus(){return strength+armorHp;}
        double movementBonus(){return (dexterity*.1+pantsSpeed)/100;}
    }
    static Stats stats(Player player) {
        double total=0,str=0,dex=0,intel=0,luck=0,hp=0,movement=0,crit=0,buff=0,cdr=0;
        ItemStack[] equipped={player.getInventory().getItemInMainHand(),player.getInventory().getHelmet(),player.getInventory().getChestplate(),player.getInventory().getLeggings(),player.getInventory().getBoots()};
        for(int index=0;index<equipped.length;index++) {
            ItemStack item=equipped[index];
            if(!eligible(item) || (index==0?!weapon(item):!armor(item)))continue;
            total+=itemLevel(item);
            if(index>0) {
                int main=mainStat(item);
                switch(family(item)){case 0->str+=main;case 1->dex+=main;case 2->intel+=main;}
                luck+=(tier(item)+1)*2+value(item,"level",0)/2;
                hp+=(tier(item)+1)*5+value(item,"level",0);
                if(index==3)movement+=tier(item)+1;
            } else crit=3+tier(item)+value(item,"level",0)*.1;
            String rolls=item.getItemMeta().getPersistentDataContainer().getOrDefault(key("rolls"),PersistentDataType.STRING,"");
            for(String roll:rolls.split(","))try {int n=Integer.parseInt(roll);if(n==18||n==19)buff+=n%2==1?4:2;if(n==20||n==21)cdr+=n%2==1?4:2;}catch(NumberFormatException ignored){}
        }
        return new Stats(total/5,str,dex,intel,luck,hp,movement,Math.min(100,crit+intel*.2),buff,Math.min(60,cdr));
    }
    static double damageMultiplier(double playerLevel,double dungeonLevel) {
        if(dungeonLevel<=0)return 1;
        return Math.clamp(1+(playerLevel-dungeonLevel)/dungeonLevel*levelSlope,.5,1.5);
    }
    static int cooldown(Player player,int millis){return Math.max(1,(int)Math.round(millis*(1-stats(player).cdr()/100)));}
    static long duration(Player player,long millis){return Math.round(millis*(1+stats(player).buff()/100));}
    private void modifier(Player p,Attribute attr,String name,double amount,AttributeModifier.Operation op) {
        var instance=p.getAttribute(attr);if(instance==null)return;
        var id=key("derived_"+name);
        var old=instance.getModifiers().stream().filter(m->m.getKey().equals(id)).findFirst().orElse(null);
        if(old!=null&&Math.abs(old.getAmount()-amount)<.000001)return;
        if(old!=null)instance.removeModifier(old);
        if(amount!=0)instance.addModifier(new AttributeModifier(id,amount,op,EquipmentSlotGroup.ANY));
    }
    void refresh(Player player) {
        town.refreshCombatGear(player);
        Stats s=stats(player);
        modifier(player,Attribute.GENERIC_MAX_HEALTH,"health",s.hpBonus(),AttributeModifier.Operation.ADD_NUMBER);
        modifier(player,Attribute.GENERIC_ATTACK_DAMAGE,"attack",s.attackBonus(),AttributeModifier.Operation.ADD_NUMBER);
        modifier(player,Attribute.GENERIC_MOVEMENT_SPEED,"movement",s.movementBonus(),AttributeModifier.Operation.ADD_SCALAR);
        double max=player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        if(player.getHealth()>max)player.setHealth(max);
    }
    static double restoredHealth(double saved,double maximum){return Double.isFinite(saved)&&saved>0?Math.min(saved,maximum):0;}
    private void saveHealth(Player p){if(!p.isDead()&&p.getHealth()>0)p.getPersistentDataContainer().set(key("saved_health"),PersistentDataType.DOUBLE,p.getHealth());else p.getPersistentDataContainer().remove(key("saved_health"));}
    @EventHandler(priority=EventPriority.MONITOR)
    public void quitHealth(org.bukkit.event.player.PlayerQuitEvent event){saveHealth(event.getPlayer());}
    @EventHandler(priority=EventPriority.MONITOR)
    public void joinHealth(org.bukkit.event.player.PlayerJoinEvent event){
        Player p=event.getPlayer();Double saved=p.getPersistentDataContainer().get(key("saved_health"),PersistentDataType.DOUBLE);
        Bukkit.getScheduler().runTaskLater(plugin,()->{if(!p.isOnline()||p.isDead())return;refresh(p);double hp=saved==null?0:restoredHealth(saved,p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());if(hp>0)p.setHealth(hp);p.getPersistentDataContainer().remove(key("saved_health"));},2L);
    }
    @EventHandler public void deathHealth(org.bukkit.event.entity.PlayerDeathEvent event){event.getEntity().getPersistentDataContainer().remove(key("saved_health"));}
    void tick(){for(Player p:Bukkit.getOnlinePlayers())refresh(p);}
    void removeModifiers(){for(Player p:Bukkit.getOnlinePlayers())saveHealth(p);for(Player p:Bukkit.getOnlinePlayers())for(Attribute attr:new Attribute[]{Attribute.GENERIC_MAX_HEALTH,Attribute.GENERIC_ATTACK_DAMAGE,Attribute.GENERIC_MOVEMENT_SPEED}) {
        var instance=p.getAttribute(attr);if(instance==null)continue;
        for(var m:List.copyOf(instance.getModifiers()))if(m.getKey().getNamespace().equals("pipilife")&&m.getKey().getKey().startsWith("smith_derived_"))instance.removeModifier(m);
    }}
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void damage(EntityDamageByEntityEvent event) {
        Entity attacker=event.getDamager();
        if(attacker instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter)attacker=shooter;
        if(attacker instanceof Player p && event.getEntity() instanceof LivingEntity && !(event.getEntity() instanceof Player)) {
            refresh(p); Stats s=stats(p);
            double dungeon=plugin.getConfig().getDouble("combat.dungeons."+p.getWorld().getName()+".item-level",0);
            double amount=event.getDamage()*damageMultiplier(s.ilvl(),dungeon);
            if(java.util.concurrent.ThreadLocalRandom.current().nextDouble(100)<s.crit()) {amount*=1.5;p.getWorld().spawnParticle(Particle.CRIT,event.getEntity().getLocation().add(0,1,0),8,.3,.3,.3,0);}
            event.setDamage(amount);
        } else if(event.getEntity() instanceof Player p && attacker instanceof LivingEntity && !(attacker instanceof Player)) {
            double dungeon=plugin.getConfig().getDouble("combat.dungeons."+p.getWorld().getName()+".item-level",0);
            event.setDamage(event.getDamage()/damageMultiplier(stats(p).ilvl(),dungeon));
        }
    }
    static final class Profile implements InventoryHolder {
        Inventory inventory;
        public Inventory getInventory(){return inventory;}
    }
    boolean open(Player player) {
        refresh(player); Stats s=stats(player);Profile holder=new Profile();
        holder.inventory=Bukkit.createInventory(holder,54,TITLE);
        Inventory inv=holder.inventory;
        inv.setItem(0,plugin.statusIcon(Material.PLAYER_HEAD,player.getName()));
        inv.setItem(1,player.getInventory().getItemInMainHand().clone());
        var gear=player.getInventory();
        ItemStack[] armor={gear.getHelmet(),gear.getChestplate(),gear.getLeggings(),gear.getBoots()};
        for(int i=0;i<4;i++)if(armor[i]!=null)inv.setItem(2+i,armor[i].clone());
        double max=player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        double defense=player.getAttribute(Attribute.GENERIC_ARMOR).getValue();
        double attack=player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).getValue();
        double speed=player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).getValue()*1000;
        List<String> data=List.of(f(s.ilvl()),f(weapon(player.getInventory().getItemInMainHand()) ? Math.max(0,attack-s.attackBonus()-1) : 0),f(max),f(defense),f(s.crit()),f(speed),f(100+s.buff()),f(s.cdr()),f(s.strength()),f(s.dexterity()),f(s.intelligence()),f(s.luck()),f(s.attackBonus()),f(s.hpBonus()),f(s.movementBonus()*100));
        inv.setItem(10,plugin.statusIcon(Material.BOOK,"전투 스탯",data.toArray(String[]::new)));
        inv.setItem(49,plugin.statusIcon(Material.BARRIER,"닫기"));player.openInventory(inv);return true;
    }
    private static String f(double v){return String.format(Locale.ROOT,"%.1f",v);}
    @EventHandler public void click(InventoryClickEvent e){if(e.getView().getTopInventory().getHolder() instanceof Profile){e.setCancelled(true);if(e.getRawSlot()==49)e.getWhoClicked().closeInventory();}}
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Profile)e.setCancelled(true);}
}
