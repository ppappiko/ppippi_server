package dev.pipi.facilities;

import com.google.gson.*;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Client-side loader and renderer for Blockbench (.bbmodel) pet models. */
public final class PetModels {
 private static final org.slf4j.Logger LOG=LogUtils.getLogger();
 private static final Map<String,Model> CACHE=new HashMap<>();
 private static final float BLEND=0.2f;

 private PetModels(){}

 /** Returns the model for the skin, or null when the skin has no (valid) bbmodel. */
 public static Model get(String skin){
  if(CACHE.containsKey(skin))return CACHE.get(skin);
  Model m=null;
  try{m=load(skin);}catch(Throwable t){LOG.warn("[PipiFacilities] pet model '{}' could not be loaded: {}",skin,t.toString());}
  CACHE.put(skin,m);
  return m;
 }

 // ---------------------------------------------------------------- data

 static final class Face{float u1,v1,u2,v2;int rot;}
 static final class Cube{String name;float[] from,to,origin,rot;float inflate;final Face[] faces=new Face[6];}
 static final class Bone{String name,uuid;float[] origin,rot;boolean visible=true;int index;final List<Object> children=new ArrayList<>();}
 static final class Key{float t;float[] v;String interp="linear";}
 static final class Anim{String name,loop="loop";float length;final Map<String,Map<String,Key[]>> tracks=new HashMap<>();}
 static final class State{Anim cur;float animStart;float blendStart=-1;float[][] last,prev;}

 public static final class Model{
  public final ResourceLocation texture;
  final List<Bone> roots=new ArrayList<>();
  final Map<String,Cube> cubes=new HashMap<>();
  final List<Bone> bones=new ArrayList<>();
  final Map<String,Anim> anims=new HashMap<>();
  final float resW,resH;
  final WeakHashMap<Object,State> states=new WeakHashMap<>();

  Model(ResourceLocation texture,float w,float h){this.texture=texture;resW=w;resH=h;}

  /** action: 0 idle, 1 walk, 2 work, 3 eat. */
  public void draw(Object key,float timeSeconds,int action,boolean hurt,PoseStack pose,VertexConsumer vc,int overlay,int light){
   Anim anim=null;
   if(hurt)anim=anims.get("hurt");
   if(anim==null)anim=anims.get(switch(action){case 1->"walk";case 2->"work";case 3->"eat";default->"idle";});
   if(anim==null)anim=anims.get("idle");
   State st=states.computeIfAbsent(key,k->new State());
   float now=timeSeconds;
   if(st.cur!=anim){
    if(st.last!=null){st.prev=copy(st.last);st.blendStart=now;}
    st.cur=anim;st.animStart=now;
   }
   int n=bones.size();
   float[][] pose9=new float[n][];
   for(int i=0;i<n;i++)pose9[i]=evalBone(anim,bones.get(i),now,st.animStart);
   if(st.blendStart>=0&&st.prev!=null){
    float f=(now-st.blendStart)/BLEND;
    if(f>=1||f<0){st.blendStart=-1;st.prev=null;}
    else for(int i=0;i<n;i++)for(int k=0;k<9;k++)pose9[i][k]=st.prev[i][k]+(pose9[i][k]-st.prev[i][k])*f;
   }
   st.last=copy(pose9);
   for(Bone b:roots)drawBone(b,pose9,action,pose,vc,overlay,light);
  }

  private static float[][] copy(float[][] a){float[][] r=new float[a.length][];for(int i=0;i<a.length;i++)r[i]=a[i].clone();return r;}

  /** Returns {rotX,rotY,rotZ,posX,posY,posZ,scaleX,scaleY,scaleZ} with Blockbench sign conventions already applied. */
  private float[] evalBone(Anim a,Bone b,float now,float animStart){
   float[] r={0,0,0,0,0,0,1,1,1};
   if(a==null)return r;
   Map<String,Key[]> ch=a.tracks.get(b.uuid);
   if(ch==null)return r;
   float t;
   if(a.length<=0)t=0;
   else if("loop".equals(a.loop))t=now%a.length;
   else t=Math.min(Math.max(now-animStart,0),a.length);
   boolean loop="loop".equals(a.loop);
   Key[] rot=ch.get("rotation");
   if(rot!=null){float[] v=sample(rot,t,loop,a.length);r[0]=-v[0];r[1]=-v[1];r[2]=v[2];}
   Key[] pos=ch.get("position");
   if(pos!=null){float[] v=sample(pos,t,loop,a.length);r[3]=-v[0];r[4]=v[1];r[5]=v[2];}
   Key[] sc=ch.get("scale");
   if(sc!=null){float[] v=sample(sc,t,loop,a.length);r[6]=v[0];r[7]=v[1];r[8]=v[2];}
   return r;
  }

  private static boolean isCurve(String s){return "catmullrom".equals(s)||"bezier".equals(s);}

  /** True when the track's first key sits at 0 and its last at the animation length, so neighbours wrap. */
  private static boolean wrap(Key[] ks,float length){
   return ks.length>1&&Math.abs(ks[0].t)<1e-4f&&Math.abs(ks[ks.length-1].t-length)<1e-4f;
  }

  private static Key neighbourBefore(Key[] ks,int i,boolean loop,float length){
   if(i>0)return ks[i-1];
   if(loop&&wrap(ks,length))return ks[ks.length-2];
   return ks[i];
  }

  private static Key neighbourAfter(Key[] ks,int j,boolean loop,float length){
   if(j<ks.length-1)return ks[j+1];
   if(loop&&wrap(ks,length))return ks[1];
   return ks[j];
  }

  /** Uniform Catmull-Rom through P0,a,b,P3 at local parameter s in [0,1]. */
  private static float cr(float p0,float a,float b,float p3,float s){
   float s2=s*s,s3=s2*s;
   return 0.5f*((2*a)+(-p0+b)*s+(2*p0-5*a+4*b-p3)*s2+(-p0+3*a-3*b+p3)*s3);
  }

  private static float[] sample(Key[] ks,float t,boolean loop,float length){
   if(t<=ks[0].t)return ks[0].v;
   Key last=ks[ks.length-1];
   if(t>=last.t)return last.v;
   for(int i=0;i<ks.length-1;i++){
    Key a=ks[i],b=ks[i+1];
    if(t>=a.t&&t<=b.t){
     float span=b.t-a.t;float f=span<=1e-6f?0:(t-a.t)/span;
     if("step".equals(a.interp))return a.v;
     if(isCurve(a.interp)||isCurve(b.interp)){
      Key p0=neighbourBefore(ks,i,loop,length),p3=neighbourAfter(ks,i+1,loop,length);
      return new float[]{cr(p0.v[0],a.v[0],b.v[0],p3.v[0],f),cr(p0.v[1],a.v[1],b.v[1],p3.v[1],f),cr(p0.v[2],a.v[2],b.v[2],p3.v[2],f)};
     }
     return new float[]{a.v[0]+(b.v[0]-a.v[0])*f,a.v[1]+(b.v[1]-a.v[1])*f,a.v[2]+(b.v[2]-a.v[2])*f};
    }
   }
   return last.v;
  }

  private static final int FULL_BRIGHT=15728880;

  private void drawBone(Bone b,float[][] p,int action,PoseStack pose,VertexConsumer vc,int overlay,int light){
   if(!b.visible)return;
   if(b.name.endsWith("_work")&&action!=2)return;
   if(b.name.endsWith("_eat")&&action!=3)return;
   if(b.name.endsWith("_rest")&&action==2)return;
   int l=b.name.endsWith("_glow")?FULL_BRIGHT:light;
   float[] a=p[b.index];
   pose.pushPose();
   pose.translate(b.origin[0]+a[3],b.origin[1]+a[4],b.origin[2]+a[5]);
   rotate(pose,b.rot[0]+a[0],b.rot[1]+a[1],b.rot[2]+a[2]);
   pose.scale(a[6],a[7],a[8]);
   pose.translate(-b.origin[0],-b.origin[1],-b.origin[2]);
   for(Object c:b.children){
    if(c instanceof Bone cb)drawBone(cb,p,action,pose,vc,overlay,l);
    else drawCube((Cube)c,pose,vc,overlay,l);
   }
   pose.popPose();
  }

  private static void rotate(PoseStack pose,float x,float y,float z){
   if(z!=0)pose.mulPose(Axis.ZP.rotationDegrees(z));
   if(y!=0)pose.mulPose(Axis.YP.rotationDegrees(y));
   if(x!=0)pose.mulPose(Axis.XP.rotationDegrees(x));
  }

  private void drawCube(Cube c,PoseStack pose,VertexConsumer vc,int overlay,int light){
   if(c.name.startsWith("hitbox"))return;
   pose.pushPose();
   boolean rotated=c.rot[0]!=0||c.rot[1]!=0||c.rot[2]!=0;
   if(rotated){pose.translate(c.origin[0],c.origin[1],c.origin[2]);rotate(pose,c.rot[0],c.rot[1],c.rot[2]);pose.translate(-c.origin[0],-c.origin[1],-c.origin[2]);}
   float i=c.inflate;
   float x=Math.min(c.from[0],c.to[0])-i,y=Math.min(c.from[1],c.to[1])-i,z=Math.min(c.from[2],c.to[2])-i;
   float X=Math.max(c.from[0],c.to[0])+i,Y=Math.max(c.from[1],c.to[1])+i,Z=Math.max(c.from[2],c.to[2])+i;
   // face order: north, south, east, west, up, down. Each vertex: px,py,pz, s,t (s: u1->u2, t: v1->v2)
   float[][][] v={
    {{x,y,z,1,1},{x,Y,z,1,0},{X,Y,z,0,0},{X,y,z,0,1}},
    {{X,y,Z,1,1},{X,Y,Z,1,0},{x,Y,Z,0,0},{x,y,Z,0,1}},
    {{X,y,z,1,1},{X,Y,z,1,0},{X,Y,Z,0,0},{X,y,Z,0,1}},
    {{x,y,Z,1,1},{x,Y,Z,1,0},{x,Y,z,0,0},{x,y,z,0,1}},
    {{x,Y,z,0,0},{x,Y,Z,0,1},{X,Y,Z,1,1},{X,Y,z,1,0}},
    {{x,y,Z,0,0},{x,y,z,0,1},{X,y,z,1,1},{X,y,Z,1,0}}};
   float[][] nrm={{0,0,-1},{0,0,1},{1,0,0},{-1,0,0},{0,1,0},{0,-1,0}};
   var last=pose.last();
   for(int f=0;f<6;f++){
    Face face=c.faces[f];
    if(face==null)continue;
    for(int k=0;k<4;k++){
     float[] q=v[f][k];
     float s=q[3],t=q[4];
     for(int r=0;r<face.rot/90%4;r++){float ns=t,nt=1-s;s=ns;t=nt;}
     float u=(face.u1+(face.u2-face.u1)*s)/resW,w=(face.v1+(face.v2-face.v1)*t)/resH;
     vc.addVertex(last,q[0],q[1],q[2]).setColor(255,255,255,255).setUv(u,w).setOverlay(overlay).setLight(light).setNormal(last,nrm[f][0],nrm[f][1],nrm[f][2]);
    }
   }
   pose.popPose();
  }
 }

 // ---------------------------------------------------------------- loading

 private static Model load(String skin) throws Exception{
  var rm=Minecraft.getInstance().getResourceManager();
  ResourceLocation rl=ResourceLocation.fromNamespaceAndPath("pipifacilities","pets/"+skin+".bbmodel");
  var res=rm.getResource(rl);
  if(res.isEmpty())throw new IllegalStateException("resource "+rl+" not found");
  JsonObject root;
  try(InputStream in=res.get().open();InputStreamReader rd=new InputStreamReader(in,StandardCharsets.UTF_8)){
   root=JsonParser.parseReader(rd).getAsJsonObject();
  }
  float rw=64,rh=64;
  if(root.has("resolution")){JsonObject r=root.getAsJsonObject("resolution");rw=num(r.get("width"),64);rh=num(r.get("height"),64);}
  JsonArray texs=root.getAsJsonArray("textures");
  if(texs==null||texs.size()==0)throw new IllegalStateException("no texture");
  String src=texs.get(0).getAsJsonObject().get("source").getAsString();
  int comma=src.indexOf(',');
  byte[] png=Base64.getMimeDecoder().decode(src.substring(comma+1));
  NativeImage img=NativeImage.read(new java.io.ByteArrayInputStream(png));
  ResourceLocation texLoc=ResourceLocation.fromNamespaceAndPath("pipifacilities","dynamic/pet_"+skin);
  Minecraft.getInstance().getTextureManager().register(texLoc,new DynamicTexture(img));
  Model m=new Model(texLoc,rw,rh);

  JsonArray els=root.getAsJsonArray("elements");
  if(els!=null)for(JsonElement e:els){
   JsonObject o=e.getAsJsonObject();
   if(o.has("type")&&!"cube".equals(o.get("type").getAsString()))continue;
   Cube c=new Cube();
   c.name=o.has("name")?o.get("name").getAsString():"";
   c.from=vec(o.get("from"),0,0,0);c.to=vec(o.get("to"),0,0,0);
   c.origin=vec(o.get("origin"),0,0,0);c.rot=vec(o.get("rotation"),0,0,0);
   c.inflate=o.has("inflate")?num(o.get("inflate"),0):0;
   JsonObject fs=o.getAsJsonObject("faces");
   String[] names={"north","south","east","west","up","down"};
   if(fs!=null)for(int i=0;i<6;i++){
    if(!fs.has(names[i]))continue;
    JsonObject fo=fs.getAsJsonObject(names[i]);
    if(!fo.has("uv")||fo.get("uv").isJsonNull())continue;
    if(fo.has("texture")&&fo.get("texture").isJsonNull())continue;
    JsonArray uv=fo.getAsJsonArray("uv");
    Face f=new Face();f.u1=num(uv.get(0),0);f.v1=num(uv.get(1),0);f.u2=num(uv.get(2),0);f.v2=num(uv.get(3),0);
    f.rot=fo.has("rotation")?((Math.round(num(fo.get("rotation"),0))%360)+360)%360:0;
    c.faces[i]=f;
   }
   m.cubes.put(o.get("uuid").getAsString(),c);
  }
  JsonArray ol=root.getAsJsonArray("outliner");
  if(ol!=null)for(JsonElement e:ol){
   if(e.isJsonObject())m.roots.add(parseBone(e.getAsJsonObject(),m));
   else if(e.isJsonPrimitive()){/* top-level loose cubes are ignored; models use groups */}
  }
  if(m.roots.isEmpty())throw new IllegalStateException("empty outliner");

  JsonArray an=root.getAsJsonArray("animations");
  if(an!=null)for(JsonElement e:an){
   JsonObject o=e.getAsJsonObject();
   Anim a=new Anim();
   a.name=o.get("name").getAsString();
   a.loop=o.has("loop")?o.get("loop").getAsString():"loop";
   a.length=num(o.get("length"),0);
   JsonObject ans=o.has("animators")?o.getAsJsonObject("animators"):new JsonObject();
   for(var en:ans.entrySet()){
    JsonObject ao=en.getValue().getAsJsonObject();
    JsonArray kfs=ao.getAsJsonArray("keyframes");
    if(kfs==null)continue;
    Map<String,List<Key>> byCh=new HashMap<>();
    for(JsonElement ke:kfs){
     JsonObject ko=ke.getAsJsonObject();
     JsonArray dps=ko.getAsJsonArray("data_points");
     if(dps==null||dps.size()==0)continue;
     JsonObject dp=dps.get(0).getAsJsonObject();
     Key k=new Key();
     k.t=num(ko.get("time"),0);
     k.v=new float[]{num(dp.get("x"),0),num(dp.get("y"),0),num(dp.get("z"),0)};
     k.interp=ko.has("interpolation")&&!ko.get("interpolation").isJsonNull()?ko.get("interpolation").getAsString():"linear";
     byCh.computeIfAbsent(ko.get("channel").getAsString(),x->new ArrayList<>()).add(k);
    }
    Map<String,Key[]> chm=new HashMap<>();
    for(var ce:byCh.entrySet()){
     Key[] arr=ce.getValue().toArray(new Key[0]);
     Arrays.sort(arr,Comparator.comparingDouble(k->k.t));
     chm.put(ce.getKey(),arr);
    }
    a.tracks.put(en.getKey(),chm);
   }
   m.anims.put(a.name,a);
  }
  return m;
 }

 private static Bone parseBone(JsonObject o,Model m){
  Bone b=new Bone();
  b.name=o.has("name")?o.get("name").getAsString():"";
  b.uuid=o.has("uuid")?o.get("uuid").getAsString():b.name;
  b.origin=vec(o.get("origin"),0,0,0);
  b.rot=vec(o.get("rotation"),0,0,0);
  b.visible=!o.has("visibility")||o.get("visibility").getAsBoolean();
  b.index=m.bones.size();
  m.bones.add(b);
  JsonArray ch=o.getAsJsonArray("children");
  if(ch!=null)for(JsonElement e:ch){
   if(e.isJsonObject())b.children.add(parseBone(e.getAsJsonObject(),m));
   else{Cube c=m.cubes.get(e.getAsString());if(c!=null)b.children.add(c);}
  }
  return b;
 }

 private static float num(JsonElement e,float def){
  if(e==null||e.isJsonNull())return def;
  try{
   if(e.isJsonPrimitive()){
    JsonPrimitive p=e.getAsJsonPrimitive();
    if(p.isNumber())return p.getAsFloat();
    if(p.isString()){String s=p.getAsString().trim();if(s.isEmpty())return def;return Float.parseFloat(s);}
    if(p.isBoolean())return p.getAsBoolean()?1:0;
   }
  }catch(RuntimeException ex){return def;}
  return def;
 }

 private static float[] vec(JsonElement e,float dx,float dy,float dz){
  if(e==null||!e.isJsonArray()||e.getAsJsonArray().size()<3)return new float[]{dx,dy,dz};
  JsonArray a=e.getAsJsonArray();
  return new float[]{num(a.get(0),dx),num(a.get(1),dy),num(a.get(2),dz)};
 }
}
