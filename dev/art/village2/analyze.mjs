import { readPng, writePng, edt } from "./lib.mjs";
import path from "node:path"; import { fileURLToPath } from "node:url";
const here = path.dirname(fileURLToPath(import.meta.url));
const { w, h, gray } = readPng(path.join(here, "sketch.png"));
const R_LINE = +process.argv[2] || 5, R_OPEN = +process.argv[3] || 22;
const line = new Uint8Array(w * h); for (let i = 0; i < w * h; i++) line[i] = gray[i] < 150 ? 1 : 0;
const dl = edt(line, w, h); const thick = new Uint8Array(w * h); for (let i = 0; i < w * h; i++) thick[i] = dl[i] <= R_LINE * R_LINE ? 1 : 0;
// outside flood
const outside = new Uint8Array(w * h); const st = [0]; outside[0] = 1;
while (st.length) { const i = st.pop(), x = i % w, y = (i / w) | 0;
  for (const [dx, dy] of [[1,0],[-1,0],[0,1],[0,-1]]) { const nx = x+dx, ny = y+dy; if (nx<0||ny<0||nx>=w||ny>=h) continue; const j = ny*w+nx; if (!outside[j] && !thick[j]) { outside[j]=1; st.push(j);} } }
const island = new Uint8Array(w*h); let ia=0; for (let i=0;i<w*h;i++){ island[i]=outside[i]?0:1; ia+=island[i]; }
console.log("island px", ia, "of", w*h);
const white = new Uint8Array(w*h); for (let i=0;i<w*h;i++) white[i]= island[i]&&!thick[i]?1:0;
// opening
const notW = new Uint8Array(w*h); for (let i=0;i<w*h;i++) notW[i]=white[i]?0:1;
const de = edt(notW,w,h); const er = new Uint8Array(w*h); for (let i=0;i<w*h;i++) er[i]= de[i] >= R_OPEN*R_OPEN ? 1:0;
const dd = edt(er,w,h); const op = new Uint8Array(w*h); for (let i=0;i<w*h;i++) op[i]= white[i] && dd[i] < R_OPEN*R_OPEN ? 1:0;
// label opened components
const lab = new Int32Array(w*h); let nl=0; const sizes=[0];
for (let s=0;s<w*h;s++){ if(!op[s]||lab[s]) continue; nl++; let c=0; const q=[s]; lab[s]=nl; while(q.length){const i=q.pop();c++; const x=i%w,y=(i/w)|0; for(const [dx,dy] of [[1,0],[-1,0],[0,1],[0,-1]]){const nx=x+dx,ny=y+dy; if(nx<0||ny<0||nx>=w||ny>=h)continue; const j=ny*w+nx; if(op[j]&&!lab[j]){lab[j]=nl;q.push(j);}}} sizes.push(c); }
console.log("land comps", sizes.map((s,i)=>i?i+":"+s:"").filter(Boolean).join(" "));
const houses=[[550,95],[720,135],[940,128],[965,350],[420,268],[580,390],[318,452],[395,632],[770,470],[900,572],[585,685]];
console.log("house labels", houses.map(([x,y])=>lab[y*w+x]).join(","));
const pal=[[255,255,255],[255,0,0],[0,160,0],[0,0,255],[200,150,0],[160,0,160],[0,160,160],[120,120,0],[255,120,0],[120,0,0],[0,120,60],[80,80,200],[200,80,140],[90,200,90]];
const rgb=new Uint8Array(w*h*3);
for(let i=0;i<w*h;i++){ let c; if(outside[i]) c=[120,170,230]; else if(thick[i]&&line[i]) c=[0,0,0]; else if(thick[i]) c=[90,90,90]; else if(white[i]&&!op[i]) c=[230,200,140]; else c=pal[1+(lab[i]%13)]; rgb[i*3]=c[0];rgb[i*3+1]=c[1];rgb[i*3+2]=c[2]; }
writePng(path.join(here,"debug.png"),w,h,rgb);
