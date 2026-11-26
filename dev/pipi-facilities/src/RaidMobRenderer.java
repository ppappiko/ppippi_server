package dev.pipi.facilities;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

/** Draws the raid's hive mobs from their bbmodel (PetModels): idle / walk / work(attack) animations, red hurt flash, fall-over death. */
final class RaidMobRenderer extends EntityRenderer<RaidMobs.Ghoul> {
 final String skin;
 RaidMobRenderer(EntityRendererProvider.Context c,String skin){super(c);this.skin=skin;shadowRadius=skin.equals("hive_ogre")?1.1f:skin.equals("hive_keeper")?1.1f:skin.equals("hive_knight")?.8f:.45f;}
 static void register(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers e){
  e.registerEntityRenderer(RaidMobs.RUNNER.get(),c->new RaidMobRenderer(c,"hive_runner"));
  e.registerEntityRenderer(RaidMobs.BOMBER.get(),c->new RaidMobRenderer(c,"hive_bomber"));
  e.registerEntityRenderer(RaidMobs.ACOLYTE.get(),c->new RaidMobRenderer(c,"hive_acolyte"));
  e.registerEntityRenderer(RaidMobs.KNIGHT.get(),c->new RaidMobRenderer(c,"hive_knight"));
  e.registerEntityRenderer(RaidMobs.WIZARD.get(),c->new RaidMobRenderer(c,"hive_wizard"));
  e.registerEntityRenderer(RaidMobs.KEEPER.get(),c->new RaidMobRenderer(c,"hive_keeper"));
  e.registerEntityRenderer(RaidMobs.TENTACLE.get(),c->new RaidMobRenderer(c,"hive_tentacle"));
  e.registerEntityRenderer(RaidMobs.OGRE.get(),c->new RaidMobRenderer(c,"hive_ogre"));
 }
 @Override public ResourceLocation getTextureLocation(RaidMobs.Ghoul g){var m=PetModels.get(skin);return m!=null?m.texture:ResourceLocation.withDefaultNamespace("textures/misc/white.png");}
 @Override public void render(RaidMobs.Ghoul g,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
  if(!g.isInvisible()){
   PetModels.Model m=PetModels.get(skin);
   if(m!=null){
    int action=g.action();
    if(action==0&&g.walkAnimation.speed(partial)>.02f)action=1;
    pose.pushPose();
    float body=net.minecraft.util.Mth.rotLerp(partial,g.yBodyRotO,g.yBodyRot);
    pose.mulPose(Axis.YP.rotationDegrees(180-body));
    if(g.deathTime>0){float f=Math.min(1,(g.deathTime+partial-1)/12f);pose.mulPose(Axis.ZP.rotationDegrees(f*90));}
    float s=g.getScale()/16f;pose.scale(s,s,s);
    boolean hurt=g.hurtTime>0||g.deathTime>0;
    m.draw(g,(g.tickCount+partial)/20f,action,false,pose,buffers.getBuffer(RenderType.entityCutoutNoCull(m.texture)),OverlayTexture.pack(0,hurt),light);
    pose.popPose();
   }
  }
  super.render(g,yaw,partial,pose,buffers,light);
 }
}
