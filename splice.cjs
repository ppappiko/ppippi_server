const fs=require("fs");
const [,, target, marker, endMarker, file]=process.argv;
let s=fs.readFileSync(target,"utf8").replace(/\r\n/g,"\n");
const i=s.indexOf(marker); if(i<0) throw new Error("no marker");
let j=s.length;
if(endMarker!=="EOF"){ j=s.indexOf(endMarker,i+marker.length); if(j<0) throw new Error("no end"); }
let nb=fs.readFileSync(file,"utf8").replace(/\r\n/g,"\n");
if(endMarker!=="EOF") nb=nb.replace(/\n*$/,"\n\n");
s=s.slice(0,i)+nb+s.slice(j);
fs.writeFileSync(target,s.replace(/\n/g,"\r\n"));
