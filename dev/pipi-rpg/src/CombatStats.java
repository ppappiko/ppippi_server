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
    private static int baseLevel=100,tierStep=0,upgradeStep=10;
    private static double levelSlope=.25;
    CombatStats(PipiLife plugin,TownSystems town) {
        this.plugin=plugin;this.town=town;
        var c=plugin.getConfig();
        c.addDefault("combat.item-level.base",100);c.addDefault("combat.item-level.per-tier",0);c.addDefault("combat.item-level.per-upgrade",10);
        c.addDefault("combat.difference-slope",.25);
        c.addDefault("combat.pvp",false);
        // 체력 급락 진단 로그(HP진단): 피격 없이 체력이 25% 넘게 떨어지면 logs/latest.log 에 경고를 남깁니다. 원인 파악 후 false 로 끄세요.
        c.addDefault("combat.debug-health",false);
        c.addDefault("combat.regen.out-of-combat-seconds",6);c.addDefault("combat.regen.percent-per-second",2.0);c.addDefault("combat.regen.village-percent-per-second",5.0);
        c.addDefault("combat.defense-percent-per-point",.02);c.addDefault("combat.defense-max-reduction",80);
        for(int floor=1;floor<=10;floor++)c.addDefault("combat.dungeons.tower_%02d.item-level".formatted(floor),100+(floor-1)*50);
        c.options().copyDefaults(true);plugin.saveConfig();
        baseLevel=Math.max(1,c.getInt("combat.item-level.base"));tierStep=Math.max(0,c.getInt("combat.item-level.per-tier"));upgradeStep=Math.max(0,c.getInt("combat.item-level.per-upgrade"));
        levelSlope=Math.clamp(c.getDouble("combat.difference-slope"),0,1);
        defensePercentPerPoint=Math.clamp(c.getDouble("combat.defense-percent-per-point"),0,100);defenseMaxReduction=Math.clamp(c.getDouble("combat.defense-max-reduction"),0,100);
    }
    private final java.util.Map<java.util.UUID,String> sentHp=new java.util.HashMap<>();
    /** Sends the real HP to the client HUD (the client only sees a 20-point scaled value); polled every 5 ticks, sent on change. */
    private final java.util.Map<java.util.UUID,Long> lastCombat=new java.util.HashMap<>();
    private int hpSyncTicks;
    void startHpSync(){
        Bukkit.getScheduler().runTaskTimer(plugin,()->{
            hpSyncTicks++;
            sentHp.keySet().removeIf(id->Bukkit.getPlayer(id)==null);
            lastCombat.keySet().removeIf(id->Bukkit.getPlayer(id)==null);
            boolean resend=hpSyncTicks%8==0; // 40 ticks: resend even when unchanged so a dropped/stale HUD value heals itself
            for(Player p:Bukkit.getOnlinePlayers()){
                var attr=p.getAttribute(Attribute.GENERIC_MAX_HEALTH);if(attr==null)continue;
                double max=attr.getValue(),hp=p.isDead()?0:p.getHealth();
                debugSample(p,hp,max);
                // alive players never show 0 (0.4 HP used to round to "0/530"); round up so the heart reads at least 1
                long shown=p.isDead()?0:Math.max(1,(long)Math.ceil(hp-1e-9));
                long sh=(long)Math.ceil(shield(p)-1e-9);
                String text=Math.min(shown,Math.round(max))+"/"+Math.round(max)+(sh>0?"/"+sh:"");
                if(!text.equals(sentHp.put(p.getUniqueId(),text))||resend)p.sendMessage("PIPI_HP:"+text);
            }
        },20L,5L);
        // plugin-side regeneration (vanilla regen is off because hunger is pinned at 17), once per second
        Bukkit.getScheduler().runTaskTimer(plugin,()->{
            long now=System.currentTimeMillis();
            var c=plugin.getConfig();
            long quiet=Math.max(0,c.getLong("combat.regen.out-of-combat-seconds",6))*1000L;
            double normal=Math.max(0,c.getDouble("combat.regen.percent-per-second",2.0)),town=Math.max(0,c.getDouble("combat.regen.village-percent-per-second",5.0));
            for(Player p:Bukkit.getOnlinePlayers()){
                if(p.isDead()||p.getGameMode()==GameMode.SPECTATOR||p.getGameMode()==GameMode.CREATIVE)continue;
                if(now-lastCombat.getOrDefault(p.getUniqueId(),0L)<quiet)continue;
                var attr=p.getAttribute(Attribute.GENERIC_MAX_HEALTH);if(attr==null)continue;
                double max=attr.getValue(),hp=p.getHealth();if(hp>=max)continue;
                double rate=p.getWorld().getName().equals(plugin.villageName())?town:normal;
                if(rate>0)p.setHealth(Math.min(max,hp+max*rate/100));
            }
        },40L,20L);
    }
    private final java.util.Map<java.util.UUID,double[]> hpSample=new java.util.HashMap<>();
    private final java.util.Set<java.util.UUID> damagedSince=new java.util.HashSet<>();
    private void debugSample(Player p,double hp,double max){
        if(!plugin.getConfig().getBoolean("combat.debug-health",false))return;
        double[] last=hpSample.put(p.getUniqueId(),new double[]{hp,max});
        boolean hit=damagedSince.remove(p.getUniqueId());
        if(last!=null&&!p.isDead()&&!hit&&last[0]-hp>last[1]*.25)
            plugin.getLogger().warning("[HP진단] "+p.getName()+" hp "+last[0]+"→"+hp+" max "+last[1]+"→"+max+" weapon="+Armory.weaponFor(p).getType()+" combat="+Armory.hasEquipped(p));
    }
    private void markCombat(Entity e){if(e instanceof Player p)lastCombat.put(p.getUniqueId(),System.currentTimeMillis());}
    @EventHandler(priority=EventPriority.MONITOR)
    public void hpDebugDamage(org.bukkit.event.entity.EntityDamageEvent event){
        if(!(event.getEntity() instanceof Player p))return;
        damagedSince.add(p.getUniqueId());
        if(!plugin.getConfig().getBoolean("combat.debug-health",false))return;
        String by=event instanceof EntityDamageByEntityEvent e?" by="+e.getDamager().getType():"";
        plugin.getLogger().warning("[HP진단] damage "+p.getName()+" cause="+event.getCause()+by+" raw="+event.getDamage()+" final="+event.getFinalDamage()
            +" cancelled="+event.isCancelled()+" hp="+p.getHealth()+" absorb="+p.getAbsorptionAmount()+" loc="+p.getLocation().getBlock().getType()+"/"+p.getEyeLocation().getBlock().getType());
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void hpDebugRegain(org.bukkit.event.entity.EntityRegainHealthEvent event){
        if(!(event.getEntity() instanceof Player p)||!plugin.getConfig().getBoolean("combat.debug-health",false))return;
        plugin.getLogger().warning("[HP진단] regain "+p.getName()+" reason="+event.getRegainReason()+" amount="+event.getAmount()+" cancelled="+event.isCancelled()+" hp="+p.getHealth());
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void combatMark(org.bukkit.event.entity.EntityDamageEvent event){
        markCombat(event.getEntity());
        if(event instanceof EntityDamageByEntityEvent by){Entity a=by.getDamager();if(a instanceof Projectile pr&&pr.getShooter() instanceof Entity s)a=s;markCombat(a);}
    }
    /** combat.pvp=false: players cannot hurt other players (melee, projectiles, skills). */
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true)
    public void noPvp(EntityDamageByEntityEvent event){
        if(!(event.getEntity() instanceof Player victim)||plugin.getConfig().getBoolean("combat.pvp",false))return;
        Entity attacker=event.getDamager();
        if(attacker instanceof Projectile pr&&pr.getShooter() instanceof Entity s)attacker=s;
        if(attacker instanceof Player p&&!p.equals(victim))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true)
    public void noPvpFire(org.bukkit.event.entity.EntityCombustByEntityEvent event){
        if(!(event.getEntity() instanceof Player victim)||plugin.getConfig().getBoolean("combat.pvp",false))return;
        Entity attacker=event.getCombuster();
        if(attacker instanceof Projectile pr&&pr.getShooter() instanceof Entity s)attacker=s;
        if(attacker instanceof Player p&&!p.equals(victim))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void hpSyncJoin(org.bukkit.event.player.PlayerJoinEvent event){sentHp.remove(event.getPlayer().getUniqueId());}
    @EventHandler(priority=EventPriority.MONITOR)
    public void hpSyncRespawn(org.bukkit.event.player.PlayerRespawnEvent event){
        Player p=event.getPlayer();sentHp.remove(p.getUniqueId());lastCombat.remove(p.getUniqueId());
        // respawn with full health (the vanilla respawn health is set before our max-HP modifier is refreshed)
        Bukkit.getScheduler().runTaskLater(plugin,()->{if(!p.isOnline())return;refresh(p);p.setHealth(p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());sentHp.remove(p.getUniqueId());},2L);
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
    static boolean needsMigration(ItemStack item) { return eligible(item) && value(item,"combat_schema",0)!=3; }
    /** 0 검사(전사), 1 궁수, 2 마법사. */
    /** 0 전사(검), 1 궁수(활), 2 마법사(지팡이). Weapons go by item type; armor by its catalog family (근거리/원거리/마법). */
    static int family(ItemStack item) {
        if(item!=null&&item.getType()==Material.BOW)return 1;
        if(item!=null&&item.getType()==Material.BLAZE_ROD)return 2;
        if(item!=null&&item.getType().name().endsWith("_SWORD"))return 0;
        String name=catalog(item);return name.contains("원거리")||name.contains("활")?1:name.contains("마법")||name.contains("지팡이")?2:0;
    }
    static int slotIndex(ItemStack item) {
        String n=item.getType().name();
        return n.endsWith("_HELMET")?0:n.endsWith("_CHESTPLATE")?1:n.endsWith("_LEGGINGS")?2:3;
    }
    // ---- base stat tables (index = tier; tiers above 0 are not designed yet, so every tier uses tier 0) ----
    /** armor tier-0 rows: [family][slot head/chest/legs/boots] = {방어력, 체력, 주스탯, 부스탯}. */
    private static final int[][][] ARMOR_T0={
        {{111,43,9,6},{332,129,26,14},{221,86,18,10},{111,43,9,6}},   // 검사: 힘/민첩
        {{111,43,9,7},{332,129,26,15},{221,86,18,11},{111,43,9,7}},   // 궁수: 민첩(주)/힘(부)
        {{111,43,9,5},{332,129,26,13},{221,86,18,9},{111,43,9,5}}};   // 마법사: 지능/행운
    /** weapon tier-0 rows: [family] = {공격력, 주스탯, 부스탯}. */
    private static final int[][] WEAPON_T0={{120,60,31},{120,60,32},{120,60,30}};
    private static int tierRow(int tier){return 0;}
    /** armor enhancement gain per step n1-5 by slot: {방어력, 체력, 주, 부}. */
    private static final int[][] ARMOR_UP={{6,12,2,1},{19,36,6,3},{13,24,4,2},{6,12,2,1}};
    private static final int[] ARMOR_UP_HEAD_6_10={6,0,2,1};
    /** weapon enhancement gain per step: {공격력, 주, 부} for n1-5 / n6-10 / n11+. */
    private static int[] weaponUp(int step){return step<=5?new int[]{10,8,4}:step<=10?new int[]{21,12,6}:new int[]{40,16,8};}
    private static void addMainSub(double[] a,int family,double main,double sub){
        switch(family){case 0->{a[3]+=main;a[4]+=sub;}case 1->{a[4]+=main;a[3]+=sub;}default->{a[5]+=main;a[6]+=sub;}}
    }
    /** Gear base + enhancement: {공격력, 방어력, 체력, 힘, 민첩, 지능, 행운}. */
    static double[] baseStats(ItemStack item) {
        double[] a=new double[7];
        if(!eligible(item))return a;
        int family=family(item),level=Math.clamp(value(item,"level",0),0,20),t=tierRow(tier(item));
        if(armor(item)) {
            int slot=slotIndex(item);int[] b=ARMOR_T0[family][slot];
            a[1]+=b[0];a[2]+=b[1];addMainSub(a,family,b[2],b[3]);
            for(int n=1;n<=level;n++) {
                int[] u=slot==0&&n>=6&&n<=10?ARMOR_UP_HEAD_6_10:ARMOR_UP[slot];
                a[1]+=u[0];a[2]+=u[1];addMainSub(a,family,u[2],u[3]);
            }
        } else {
            int[] b=WEAPON_T0[family];
            a[0]+=b[0];addMainSub(a,family,b[1],b[2]);
            for(int n=1;n<=level;n++){int[] u=weaponUp(n);a[0]+=u[0];addMainSub(a,family,u[1],u[2]);}
        }
        return a;
    }
    private static String n0(double v){return v==Math.rint(v)?String.valueOf((long)v):String.format(Locale.ROOT,"%.1f",v);}
    /** Lore/preview lines for the gear's own base+enhancement stats. */
    static List<String> baseLines(ItemStack item,String color) {
        double[] a=baseStats(item);List<String> out=new ArrayList<>();
        String[] names={"공격력","방어력","체력","힘","민첩","지능","행운"};
        for(int i=0;i<a.length;i++)if(a[i]>0)out.add(color+names[i]+" +"+n0(a[i]));
        return out;
    }
    static void decorate(ItemStack item,ItemMeta meta,List<String> lore) {
        if(!eligible(item))return;
        meta.getPersistentDataContainer().set(key("combat_schema"),PersistentDataType.INTEGER,3);
        lore.add(0,"§6아이템레벨 "+itemLevel(item));
        lore.addAll(1,baseLines(item,"§7"));
    }
    /** strength/dexterity/intelligence/luck are final totals (base + flat options, then percent options). */
    record Stats(double ilvl,double strength,double dexterity,double intelligence,double luck,double attackBase,double defenseBase,double armorHp,double crit,double buff,double cdr,
                 double hpFlat,double hpPct,double moveFlat,double movePct,double defFlat,double defPct,double atkFlat,double atkPct,double dmgPct,double bossPct) {
        /** 공격력: gear 공격력 + 힘x0.5 + 민첩x0.5 + 지능x1.5, with 공격력 options. */
        double attackBonus(){return (attackBase+atkFlat+strength*.5+dexterity*.5+intelligence*1.5)*(1+atkPct/100);}
        double hpBonus(){return (20+strength+armorHp+hpFlat)*(1+hpPct/100)-20;}
        /** 이동속도 stat (민첩 + flat options, times percent options); 100 = +1% speed. */
        double moveStat(){return (dexterity+moveFlat)*(1+movePct/100);}
        double movementBonus(){return moveStat()/10000;}
        /** summed 방어력 (gear + flat options, times percent options). */
        double defense(){return (defenseBase+defFlat)*(1+defPct/100);}
    }
    static Stats stats(Player player) {
        double total=0,crit=0,buff=0,cdr=0;
        double[] base=new double[7];
        double hpFlat=0,hpPct=0,moveFlat=0,movePct=0,defFlat=0,defPct=0,critP=0,atkF=0,atkP=0,dmgP=0,bossP=0;
        double strF=0,dexF=0,intF=0,luckF=0,strP=0,dexP=0,intP=0,allF=0,allP=0;
        ItemStack[] equipped={Armory.weaponFor(player),player.getInventory().getHelmet(),player.getInventory().getChestplate(),player.getInventory().getLeggings(),player.getInventory().getBoots()};
        for(int index=0;index<equipped.length;index++) {
            ItemStack item=equipped[index];
            if(!eligible(item) || (index==0?!weapon(item):!armor(item)))continue;
            total+=itemLevel(item);
            double[] b=baseStats(item);for(int i=0;i<7;i++)base[i]+=b[i];
            for(var line:Enchants.read(item)) {
                var o=Enchants.byId(line.opt());if(o==null)continue;
                double v=Enchants.value(o,line.grade());
                switch(o.kind()) {
                    case "str"->strF+=v;case "dex"->dexF+=v;case "int"->intF+=v;case "luck"->luckF+=v;
                    case "str%"->strP+=v;case "dex%"->dexP+=v;case "int%"->intP+=v;
                    case "all"->allF+=v;case "all%"->allP+=v;
                    case "atk"->atkF+=v;case "atk%"->atkP+=v;case "dmg%"->dmgP+=v;case "boss%"->bossP+=v;
                    case "hp"->hpFlat+=v;case "hp%"->hpPct+=v;
                    case "move"->moveFlat+=v;case "move%"->movePct+=v;
                    case "def"->defFlat+=v;case "def%"->defPct+=v;
                    case "crit%"->critP+=v;
                    default->{}
                }
            }
        }
        double str=(base[3]+strF+allF)*(1+(strP+allP)/100),dex=(base[4]+dexF+allF)*(1+(dexP+allP)/100),intel=(base[5]+intF+allF)*(1+(intP+allP)/100),luck=(base[6]+luckF+allF)*(1+allP/100);
        return new Stats(total/5,str,dex,intel,luck,base[0],base[1],base[2],Math.min(100,intel*.5+luck+critP),buff,Math.min(60,cdr),hpFlat,hpPct,moveFlat,movePct,defFlat,defPct,atkF,atkP,dmgP,bossP);
    }
    /** damage reduction percent = defense x percent-per-point, capped (config combat.defense-percent-per-point / defense-max-reduction). */
    private static double defensePercentPerPoint=.02,defenseMaxReduction=80;
    static double defenseReductionPercent(double defense){return Math.clamp(defense*defensePercentPerPoint,0,defenseMaxReduction);}
    static double reducedDamage(double damage,double defense){return damage<=0?damage:Math.max(0,damage*(1-defenseReductionPercent(defense)/100));}
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
        double oldMax=player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        // remove+add of the max-HP modifier makes vanilla clamp health to the temporary low max (20); remember the true value first
        final double hpBefore=player.getHealth();
        modifier(player,Attribute.GENERIC_MAX_HEALTH,"health",s.hpBonus(),AttributeModifier.Operation.ADD_NUMBER);
        double newMax=player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        // max HP changed (gear/options): keep the same health ratio instead of leaving e.g. 20/530
        // never lose health: keep hpBefore (full stays full), only cap at the new max
        if(!player.isDead()&&hpBefore>0){
            double target=hpBefore>=oldMax-.0001?newMax:Math.min(hpBefore,newMax);
            target=Math.max(target,Math.min(hpBefore,newMax));
            if(Math.abs(player.getHealth()-target)>.000001)player.setHealth(target);
        }
        if(plugin.getConfig().getBoolean("combat.debug-health",false)&&(Math.abs(newMax-oldMax)>.0001||Math.abs(player.getHealth()-hpBefore)>.01))
            plugin.getLogger().warning("[HP진단] refresh "+player.getName()+" hp "+hpBefore+"→"+player.getHealth()+" max "+oldMax+"→"+newMax+" weapon="+Armory.weaponFor(player).getType()+" combat="+Armory.hasEquipped(player));
        modifier(player,Attribute.GENERIC_ATTACK_DAMAGE,"attack",s.attackBonus(),AttributeModifier.Operation.ADD_NUMBER);
        modifier(player,Attribute.GENERIC_MOVEMENT_SPEED,"movement",s.movementBonus(),AttributeModifier.Operation.ADD_SCALAR);
        // defense is applied by defenseDamage() (flat reduction), not through the vanilla armor attribute
        modifier(player,Attribute.GENERIC_ARMOR,"armor",0,AttributeModifier.Operation.ADD_NUMBER);
        modifier(player,Attribute.GENERIC_ARMOR,"armor_pct",0,AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        double max=player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        if(player.getHealth()>max)player.setHealth(max);
        // keep the heart bar at 10 hearts however large max HP gets
        // NO health scaling: on this hybrid server the scaled (≤20) value leaks back into the real health and drains it toward 0.
        // The vanilla hearts are hidden by the client HUD (HudBar), which shows the real HP from PIPI_HP.
        if(player.isHealthScaled())player.setHealthScaled(false);
    }
    static double restoredHealth(double saved,double maximum){return Double.isFinite(saved)&&saved>0?Math.min(saved,maximum):0;}
    private void saveHealth(Player p){if(!p.isDead()&&p.getHealth()>0)p.getPersistentDataContainer().set(key("saved_health"),PersistentDataType.DOUBLE,p.getHealth());else p.getPersistentDataContainer().remove(key("saved_health"));}
    @EventHandler(priority=EventPriority.MONITOR)
    public void quitHealth(org.bukkit.event.player.PlayerQuitEvent event){saveHealth(event.getPlayer());clearShield(event.getPlayer());}
    @EventHandler(priority=EventPriority.MONITOR)
    public void joinHealth(org.bukkit.event.player.PlayerJoinEvent event){
        Player p=event.getPlayer();Double saved=p.getPersistentDataContainer().get(key("saved_health"),PersistentDataType.DOUBLE);
        Bukkit.getScheduler().runTaskLater(plugin,()->{if(!p.isOnline()||p.isDead())return;refresh(p);double max=p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();double hp=saved==null?max:restoredHealth(saved,max);if(hp<=0)hp=max;p.setHealth(Math.min(hp,max));sentHp.remove(p.getUniqueId());p.getPersistentDataContainer().remove(key("saved_health"));},2L);
    }
    @EventHandler public void deathHealth(org.bukkit.event.entity.PlayerDeathEvent event){clearShield(event.getEntity());event.getEntity().getPersistentDataContainer().remove(key("saved_health"));}
    void tick(){for(Player p:Bukkit.getOnlinePlayers())refresh(p);}
    void removeModifiers(){for(Player p:Bukkit.getOnlinePlayers())saveHealth(p);for(Player p:Bukkit.getOnlinePlayers())p.setHealthScaled(false);for(Player p:Bukkit.getOnlinePlayers())for(Attribute attr:new Attribute[]{Attribute.GENERIC_MAX_HEALTH,Attribute.GENERIC_ATTACK_DAMAGE,Attribute.GENERIC_MOVEMENT_SPEED,Attribute.GENERIC_ARMOR}) {
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
            double amount=event.getDamage()*damageMultiplier(s.ilvl(),dungeon)*(1+s.dmgPct()/100);
            Entity victim=event.getEntity();
            if(s.bossPct()>0&&(victim.getScoreboardTags().contains("pipi_boss")||victim instanceof org.bukkit.entity.Boss))amount*=1+s.bossPct()/100;
            if(java.util.concurrent.ThreadLocalRandom.current().nextDouble(100)<s.crit()) {amount*=1.5;DamageNumbers.markCrit(event.getEntity());p.getWorld().spawnParticle(Particle.CRIT,event.getEntity().getLocation().add(0,1,0),8,.3,.3,.3,0);}
            event.setDamage(amount);
        } else if(event.getEntity() instanceof Player p && attacker instanceof LivingEntity && !(attacker instanceof Player)) {
            double dungeon=plugin.getConfig().getDouble("combat.dungeons."+p.getWorld().getName()+".item-level",0);
            event.setDamage(event.getDamage()/damageMultiplier(stats(p).ilvl(),dungeon));
        }
    }
    /** Flat defense reduction for every damage source hitting a player (runs after the other HIGH handlers). */
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void defenseDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if(!(event.getEntity() instanceof Player p))return;
        String cause=event.getCause().name();
        if(cause.equals("VOID")||cause.equals("SUICIDE")||cause.equals("KILL")||cause.equals("WORLD_BORDER"))return;
        double defense=stats(p).defense();
        double dmg=event.getDamage();
        if(defense>0)dmg=reducedDamage(dmg,defense);
        // 보호막: absorbs after the defense reduction, before health
        double[] sh=SHIELDS.get(p.getUniqueId());
        if(sh!=null&&sh[0]>0&&sh[1]>System.currentTimeMillis()&&dmg>0){
            double take=Math.min(sh[0],dmg);
            sh[0]-=take;dmg-=take;
            if(sh[0]<=0.0001){sh[0]=0;sh[3]=1;} // depleted: the visual loop plays the break effect
        }
        if(dmg!=event.getDamage())event.setDamage(dmg);
    }
    /** Shield state per player: {amount, expiresAtMillis, maxAmount, depletedFlag}. */
    private static final java.util.Map<java.util.UUID,double[]> SHIELDS=new java.util.concurrent.ConcurrentHashMap<>();
    static void giveShield(Player p,double amount,long millis){SHIELDS.put(p.getUniqueId(),new double[]{amount,System.currentTimeMillis()+millis,amount,0});}
    static void clearShield(Player p){SHIELDS.remove(p.getUniqueId());}
    /** State {amount, expires, max, depleted} or null when none; expired shields are dropped. */
    static double[] shieldState(Player p){
        double[] sh=SHIELDS.get(p.getUniqueId());
        if(sh!=null&&sh[1]<=System.currentTimeMillis()&&sh[3]==0){SHIELDS.remove(p.getUniqueId());return null;}
        return sh;
    }
    static double shield(Player p){
        double[] sh=shieldState(p);
        return sh==null||sh[3]!=0||sh[1]<=System.currentTimeMillis()?0:Math.max(0,sh[0]);
    }
    static final class Profile implements InventoryHolder {
        Inventory inventory;
        public Inventory getInventory(){return inventory;}
    }
    boolean open(Player player) { return open(player, player); }
    /** Read-only status view of {@code player} shown to {@code viewer} (social: 상태창). */
    boolean open(Player viewer, Player player) {
        refresh(player); Stats s=stats(player);Profile holder=new Profile();
        holder.inventory=Bukkit.createInventory(holder,54,TITLE);
        Inventory inv=holder.inventory;
        inv.setItem(0,plugin.statusIcon(Material.PLAYER_HEAD,player.getName()));
        inv.setItem(1,markOwner(Armory.weaponFor(player).clone(),player.getName()));
        var gear=player.getInventory();
        ItemStack[] armor={gear.getHelmet(),gear.getChestplate(),gear.getLeggings(),gear.getBoots()};
        for(int i=0;i<4;i++)if(armor[i]!=null)inv.setItem(2+i,markOwner(armor[i].clone(),player.getName()));
        double max=player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        double defense=s.defense();
        double attack=s.attackBonus();
        double speed=player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).getValue()*1000;
        List<String> data=List.of(f(s.ilvl()),f(attack),f(max),f(defense),f(s.crit()),f(speed),f(100+s.buff()),f(s.cdr()),f(s.strength()),f(s.dexterity()),f(s.intelligence()),f(s.luck()),f(s.attackBonus()),f(s.hpBonus()),f(s.movementBonus()*100),f(defenseReductionPercent(defense)));
        inv.setItem(10,plugin.statusIcon(Material.BOOK,"전투 스탯",data.toArray(String[]::new)));
        inv.setItem(49,plugin.statusIcon(Material.BARRIER,"닫기"));viewer.openInventory(inv);return true;
    }
    /** Stamps the owner name (pipilife:owner) so the client spec card can show 소유자. */
    static ItemStack markOwner(ItemStack item,String owner){if(item==null||item.getType().isAir()||!item.hasItemMeta())return item;var m=item.getItemMeta();m.getPersistentDataContainer().set(NamespacedKey.fromString("pipilife:owner"),PersistentDataType.STRING,owner);item.setItemMeta(m);return item;}
    private static String f(double v){return String.format(Locale.ROOT,"%.1f",v);}
    @EventHandler public void click(InventoryClickEvent e){if(e.getView().getTopInventory().getHolder() instanceof Profile){e.setCancelled(true);if(e.getRawSlot()==49)e.getWhoClicked().closeInventory();}}
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Profile)e.setCancelled(true);}
}
