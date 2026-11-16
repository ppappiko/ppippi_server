// Sketch -> layout.json + preview.png.   node layout.mjs [scale]
import fs from "node:fs";
import path from "node:path"; import { fileURLToPath } from "node:url";
import { readPng, writePng, edt } from "./lib.mjs";
const here = path.dirname(fileURLToPath(import.meta.url));
const { w, h, gray } = readPng(path.join(here, "sketch.png"));
const S = +process.argv[2] || 0.6, R_LINE = 5, R_OPEN = 20, RIM = 30;
const N4 = [[1,0],[-1,0],[0,1],[0,-1]];
const houses = [[550,95],[720,135],[940,128],[965,350],[420,268],[580,390],[318,452],[395,632],[770,470],[900,572],[585,685]];
const ladders = [[865,195],[515,675],[660,670]];
const gatePx = [358, 118];
const raisedHouses = [2, 10]; // houses (940,128) and (585,685) sit on hills
const hull = [[510,715],[530,640],[580,596],[640,622],[700,668],[728,712],[720,738],[560,742]];
const line = new Uint8Array(w*h); for (let i=0;i<w*h;i++) line[i]=gray[i]<150?1:0;
const dl = edt(line,w,h); const thick = new Uint8Array(w*h); for (let i=0;i<w*h;i++) thick[i]=dl[i]<=R_LINE*R_LINE?1:0;
const outside = new Uint8Array(w*h); { const st=[0]; outside[0]=1; while(st.length){const i=st.pop(),x=i%w,y=(i/w)|0; for(const [dx,dy] of N4){const nx=x+dx,ny=y+dy; if(nx<0||ny<0||nx>=w||ny>=h)continue; const j=ny*w+nx; if(!outside[j]&&!thick[j]){outside[j]=1;st.push(j);}}} }
const island = new Uint8Array(w*h); for (let i=0;i<w*h;i++) island[i]=outside[i]?0:1;
const white = new Uint8Array(w*h); for (let i=0;i<w*h;i++) white[i]=island[i]&&!thick[i]?1:0;
const notW = white.map(v=>v?0:1); const de=edt(notW,w,h);
const er = new Uint8Array(w*h); for (let i=0;i<w*h;i++) er[i]=de[i]>=R_OPEN*R_OPEN?1:0;
const dd=edt(er,w,h); const core=new Uint8Array(w*h); for (let i=0;i<w*h;i++) core[i]=white[i]&&dd[i]<R_OPEN*R_OPEN?1:0;
function inPoly(x,y,p){let c=false;for(let i=0,j=p.length-1;i<p.length;j=i++){const [xi,yi]=p[i],[xj,yj]=p[j]; if((yi>y)!==(yj>y)&&x<(xj-xi)*(y-yi)/(yj-yi)+xi)c=!c;}return c;}
const lab = new Int32Array(w*h); let nl=0;
const hullId = ++nl;
for (let y=0;y<h;y++) for (let x=0;x<w;x++){const i=y*w+x; if(white[i]&&inPoly(x,y,hull)){core[i]=1;lab[i]=hullId;}}
for (let s=0;s<w*h;s++){ if(!core[s]||lab[s]) continue; nl++; const q=[s]; lab[s]=nl; while(q.length){const i=q.pop(),x=i%w,y=(i/w)|0; for(const [dx,dy] of N4){const nx=x+dx,ny=y+dy; if(nx<0||ny<0||nx>=w||ny>=h)continue; const j=ny*w+nx; if(core[j]&&!lab[j]){lab[j]=nl;q.push(j);}}} }
const dout = edt(outside,w,h);
const cand = new Uint8Array(w*h); for (let i=0;i<w*h;i++) cand[i]=white[i]&&(core[i]||dout[i]<RIM*RIM)?1:0;
{ let q=[]; for(let i=0;i<w*h;i++) if(lab[i]) q.push(i);
  while(q.length){const nq=[]; for(const i of q){const x=i%w,y=(i/w)|0; for(const [dx,dy] of N4){const nx=x+dx,ny=y+dy; if(nx<0||ny<0||nx>=w||ny>=h)continue; const j=ny*w+nx; if(cand[j]&&!lab[j]){lab[j]=lab[i];nq.push(j);}}} q=nq;} }
function labelNear(px,py){ for(let r=0;r<80;r++) for(let dy=-r;dy<=r;dy++) for(let dx=-r;dx<=r;dx++){ if(Math.max(Math.abs(dx),Math.abs(dy))!==r) continue; const i=(py+dy)*w+(px+dx); if(lab[i]) return lab[i]; } return 0; }
{ const hl=new Set(houses.map(([x,y])=>labelNear(Math.round(x),Math.round(y)))); hl.add(hullId);
  const mind=new Map(); for(let i=0;i<w*h;i++){ const L=lab[i]; if(!L||hl.has(L)) continue; mind.set(L, Math.min(mind.get(L)??1e18, dout[i])); }
  for(const [L,m] of mind) if(m>RIM*RIM){ for(let i=0;i<w*h;i++) if(lab[i]===L) lab[i]=0; console.log("dropped house-less core",L); } }
const raisedIds = new Set([hullId, labelNear(...houses[raisedHouses[0]])]);
// pixel type: 0 water 1 land 2 road 3 raised
const ptype = new Uint8Array(w*h), plab = new Int32Array(w*h);
for (let i=0;i<w*h;i++){ if(!island[i]) continue; if(thick[i]) continue; if(lab[i]){plab[i]=lab[i]; ptype[i]=raisedIds.has(lab[i])?3:1;} else ptype[i]=2; }
{ const done=new Uint8Array(w*h); let q=[]; for(let i=0;i<w*h;i++){ if(!island[i]||!thick[i]){done[i]=1; if(island[i]) q.push(i);} }
  while(q.length){const nq=[]; for(const i of q){const x=i%w,y=(i/w)|0; for(const [dx,dy] of N4){const nx=x+dx,ny=y+dy; if(nx<0||ny<0||nx>=w||ny>=h)continue; const j=ny*w+nx; if(!done[j]){done[j]=1; ptype[j]=ptype[i]; plab[j]=plab[i]; nq.push(j);}}} q=nq;} }
// ---- downsample to cells
const cw = Math.ceil(w*S), ch = Math.ceil(h*S);
const type = Array.from({length:ch},()=>new Uint8Array(cw)), clab = Array.from({length:ch},()=>new Int32Array(cw));
for (let cz=0;cz<ch;cz++) for (let cx=0;cx<cw;cx++){ const px=Math.min(w-1,Math.round((cx+0.5)/S)), py=Math.min(h-1,Math.round((cz+0.5)/S)); type[cz][cx]=ptype[py*w+px]; clab[cz][cx]=plab[py*w+px]; }
function comps(pred){ const id=Array.from({length:ch},()=>new Int32Array(cw)); const out=[]; for(let z=0;z<ch;z++)for(let x=0;x<cw;x++){ if(id[z][x]||!pred(type[z][x]))continue; const cells=[[x,z]]; id[z][x]=out.length+1; for(let k=0;k<cells.length;k++){const [cx,cz]=cells[k]; for(const [dx,dz] of N4){const nx=cx+dx,nz=cz+dz; if(nx<0||nz<0||nx>=cw||nz>=ch)continue; if(!id[nz][nx]&&pred(type[nz][nx])){id[nz][nx]=out.length+1;cells.push([nx,nz]);}}} out.push(cells);} return out; }
for (const c of comps(t=>t===2)) if (c.length<700) for(const [x,z] of c){ type[z][x]=1; clab[z][x]=0; }
{ let ch0=true; while(ch0){ ch0=false; for(let z=0;z<ch;z++)for(let x=0;x<cw;x++){ if(type[z][x]!==1||clab[z][x]) continue; for(const [dx,dz] of N4){ const L=clab[z+dz]?.[x+dx]; if(L&&type[z+dz][x+dx]===1){clab[z][x]=L;ch0=true;break;} } } } }
for (const c of comps(t=>t===1)) if (c.length<500) { const cnt={}; for(const [x,z] of c) for(const [dx,dz] of N4){ const t=type[z+dz]?.[x+dx]; if(t&&t!==1) cnt[t]=(cnt[t]||0)+1; } const best=Object.entries(cnt).sort((a,b)=>b[1]-a[1])[0]; if(best&&best[0]!=="0") for(const [x,z] of c) type[z][x]=+best[0]; }
for(let z=0;z<ch;z++)for(let x=0;x<cw;x++) if(type[z][x]===1&&raisedIds.has(clab[z][x])) type[z][x]=3;
const G = [Math.round(gatePx[0]*S), Math.round(gatePx[1]*S)];
const plazaR = 7;
for (let z=-plazaR;z<=plazaR;z++) for(let x=-plazaR;x<=plazaR;x++) if(x*x+z*z<=plazaR*plazaR){ const cx=G[0]+x,cz=G[1]+z; if(type[cz][cx]!==0) type[cz][cx]=2; }
{ const b=[Math.round(358*S),Math.round(172*S)], a=G; const n=Math.max(Math.abs(b[0]-a[0]),Math.abs(b[1]-a[1]))*2; for(let i=0;i<=n;i++){const cx=Math.round(a[0]+(b[0]-a[0])*i/n), cz=Math.round(a[1]+(b[1]-a[1])*i/n); for(let dz=-2;dz<=2;dz++)for(let dx=-2;dx<=2;dx++) if(type[cz+dz][cx+dx]!==0) type[cz+dz][cx+dx]=2; } }
// ---- plots
const P=44, MARG=3;
const plots=[];
function sameRegion(cx,cz,L,raised){ if(cx<0||cz<0||cx>=cw||cz>=ch) return false; const t=type[cz][cx]; return raised? (t===3&&clab[cz][cx]===L) : (t===1&&clab[cz][cx]===L); }
houses.forEach(([hx,hy],idx)=>{
  const hc=[hx*S,hy*S]; const L=labelNear(Math.round(hx),Math.round(hy)); const raised=raisedIds.has(L);
  let best=null;
  const free=(x0,z0)=>plots.every(q=>Math.abs(q.x0-x0)>=P+6||Math.abs(q.z0-z0)>=P+6);
  for(let z0=0;z0<ch-P;z0++) for(let x0=0;x0<cw-P;x0++){
    const d=Math.hypot(x0+P/2-hc[0], z0+P/2-hc[1]); if(best&&d>=best.d) continue; if(d>90||!free(x0,z0)) continue;
    let ok=true;
    for(let z=z0-MARG;z<z0+P+MARG&&ok;z++) for(let x=x0-MARG;x<x0+P+MARG;x++){ if(!sameRegion(x,z,L,raised)){ok=false;break;} }
    if(ok) best={d,x0,z0,L,raised};
  }
  plots.push(best);
});
plots.forEach((p,i)=>{ if(!p) console.log("NO PLOT for house",i+1); else console.log("plot",i+1,"cell",p.x0,p.z0,"d",p.d.toFixed(1),p.raised?"raised":""); });
if (plots.some(p=>!p)) { console.log("plot placement failed"); process.exit(1); }
// ---- stairs
const stairs=[];
ladders.forEach(([lx,ly])=>{ const c=[lx*S,ly*S]; let hi=[0,0,0], lo=[0,0,0];
  for(let z=Math.floor(c[1])-24;z<=c[1]+24;z++)for(let x=Math.floor(c[0])-24;x<=c[0]+24;x++){ if(x<0||z<0||x>=cw||z>=ch||Math.hypot(x-c[0],z-c[1])>24) continue; const t=type[z][x]; if(t===3){hi[0]+=x;hi[1]+=z;hi[2]++;} else if(t===1||t===2){lo[0]+=x;lo[1]+=z;lo[2]++;} }
  if(!hi[2]){console.log("ladder no raised nearby",lx,ly);return;}
  const dx=hi[0]/hi[2]-lo[0]/Math.max(1,lo[2]), dz=hi[1]/hi[2]-lo[1]/Math.max(1,lo[2]);
  const face = Math.abs(dx)>=Math.abs(dz)?(dx>0?"E":"W"):(dz>0?"S":"N"); const v={E:[1,0],W:[-1,0],S:[0,1],N:[0,-1]}[face];
  let px=Math.round(c[0]), pz=Math.round(c[1]), guard=0; while(type[pz]?.[px]!==3&&guard++<40){px+=v[0];pz+=v[1];}
  while(type[pz-v[1]]?.[px-v[0]]===3&&guard++<80){px-=v[0];pz-=v[1];}
  stairs.push({x:px-v[0], z:pz-v[1], face, len:4, width:5});
});
console.log("stairs",JSON.stringify(stairs));
// ---- crop & world coords
let minx=cw,maxx=0,minz=ch,maxz=0; for(let z=0;z<ch;z++)for(let x=0;x<cw;x++) if(type[z][x]){minx=Math.min(minx,x);maxx=Math.max(maxx,x);minz=Math.min(minz,z);maxz=Math.max(maxz,z);}
const PAD=14; minx-=PAD;minz-=PAD;maxx+=PAD;maxz+=PAD;
const W=maxx-minx+1,H=maxz-minz+1;
const ox=minx-G[0], oz=minz-G[1]; // world coord of grid (0,0): world = grid + origin
const chr="wlrh";
const rows=[]; for(let z=minz;z<=maxz;z++){ let s="",run=0,prev=null; for(let x=minx;x<=maxx;x++){ const t=chr[type[z]?.[x]??0]; if(t===prev)run++; else{ if(prev)s+=prev+run; prev=t;run=1;} } s+=prev+run; rows.push(s); }
const out={ scale:S, width:W, height:H, originX:ox, originZ:oz, baseY:63, raisedY:67,
  gateway:{x:0,z:0}, plotSize:P,
  plots: plots.map((p,i)=>({name:"plot"+String(i+1).padStart(2,"0"), x:p.x0-minx+ox, z:p.z0-minz+oz, raised:p.raised})),
  stairs: stairs.map(s=>({x:s.x-minx+ox,z:s.z-minz+oz,face:s.face,len:s.len,width:s.width})),
  rows };
fs.writeFileSync(path.join(here,"layout.json"),JSON.stringify(out));
console.log("grid",W,"x",H,"origin",ox,oz,"json bytes",fs.statSync(path.join(here,"layout.json")).size);
// ---- preview (2px per cell)
const Z=2, PW=W*Z, PH=H*Z, rgb=new Uint8Array(PW*PH*3);
const col={0:[70,130,200],1:[96,160,70],2:[205,175,120],3:[150,205,110]};
function setp(x,y,c){ if(x<0||y<0||x>=PW||y>=PH)return; const i=(y*PW+x)*3; rgb[i]=c[0];rgb[i+1]=c[1];rgb[i+2]=c[2]; }
const T=(x,z)=>type[z+minz]?.[x+minx]??0;
for(let z=0;z<H;z++)for(let x=0;x<W;x++){ const c=col[T(x,z)]; for(let a=0;a<Z;a++)for(let b=0;b<Z;b++) setp(x*Z+a,z*Z+b,c); }
for(let z=0;z<H;z++)for(let x=0;x<W;x++){ if(T(x,z)!==3) continue; for(const [dx,dz] of N4){ if(T(x+dx,z+dz)!==3){ for(let a=0;a<Z;a++)for(let b=0;b<Z;b++) setp(x*Z+a,z*Z+b,[70,120,60]); break; } } }
const dir={E:[1,0],W:[-1,0],S:[0,1],N:[0,-1]};
for(const s of stairs){ const v=dir[s.face], perp=[-v[1],v[0]]; for(let k=0;k<s.len;k++)for(let q=-2;q<=2;q++){ const x=s.x-v[0]*k+perp[0]*q-minx, z=s.z-v[1]*k+perp[1]*q-minz; for(let a=0;a<Z;a++)for(let b=0;b<Z;b++) setp(x*Z+a,z*Z+b,[120,75,35]); } }
const font={"0":"111101101101111","1":"010110010010111","2":"111001111100111","3":"111001111001111","4":"101101111001001","5":"111100111001111","6":"111100111101111","7":"111001001001001","8":"111101111101111","9":"111101111001111"};
plots.forEach((p,i)=>{ const x0=(p.x0-minx)*Z, z0=(p.z0-minz)*Z, s=P*Z; for(let k=0;k<s;k++){ for(const c of [[x0+k,z0],[x0+k,z0+s-1],[x0,z0+k],[x0+s-1,z0+k]]) setp(c[0],c[1],[255,255,255]); }
  const txt=String(i+1); const sc=4; txt.split("").forEach((ch2,ci)=>{ for(let r=0;r<5;r++)for(let q=0;q<3;q++) if(font[ch2][r*3+q]==="1") for(let a=0;a<sc;a++)for(let b=0;b<sc;b++) setp(x0+s/2-txt.length*sc*2+ci*sc*4+q*sc+a, z0+s/2-sc*2.5+r*sc+b,[255,255,255]); }); });
{ const gx=(G[0]-minx)*Z, gz=(G[1]-minz)*Z; for(let r=-14;r<=14;r++)for(let q=-14;q<=14;q++) if(Math.abs(r)+Math.abs(q)<=14&&Math.abs(r)+Math.abs(q)>=10) setp(gx+r,gz+q,[220,20,60]); }
writePng(path.join(here,"preview.png"),PW,PH,rgb);
