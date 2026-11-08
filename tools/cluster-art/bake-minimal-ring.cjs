// Offline asset preparation only. NODE_PATH may point at a runtime providing sharp.
const sharp=require('sharp'),fs=require('fs'),path=require('path');
const root=path.resolve(__dirname,'../..');
(async()=>{
 const {data,info}=await sharp(path.join(__dirname,'oem-panel-simple-music.png')).ensureAlpha().raw().toBuffer({resolveWithObject:true});
 function sample(x,y,k){x=Math.max(0,Math.min(319,x));y=Math.max(0,Math.min(319,y));let ix=Math.floor(x),iy=Math.floor(y),fx=x-ix,fy=y-iy;let p=(xx,yy)=>data[(Math.min(319,yy)*320+Math.min(319,xx))*4+k];return p(ix,iy)*(1-fx)*(1-fy)+p(ix+1,iy)*fx*(1-fy)+p(ix,iy+1)*(1-fx)*fy+p(ix+1,iy+1)*fx*fy;}
 const n=430,c=215,r=210,out=Buffer.alloc(n*n*4);
 // Preserve the OEM 4px silver strip: enlarge its circumference, not its thickness.
 for(let y=0;y<n;y++)for(let x=0;x<n;x++){
  let sum=[0,0,0,0];
  for(let sy=0;sy<4;sy++)for(let sx=0;sx<4;sx++){
   let dx=x+(sx+.5)/4-c,dy=y+(sy+.5)/4-c,d=Math.hypot(dx,dy),a=0,rgb=[0,0,0];
   if(d>=r && d<r+4){
    const t=(dy/d+1)/2;
    // OEM vertical alpha profile measured at the two sides of its silver strip.
    const oy=160+(t*2-1)*158,ox=Math.sqrt(Math.max(0,158*158-(oy-160)**2));
    const fade=(sample(159.5+ox,oy-.5,3)+sample(159.5-ox,oy-.5,3))/510;
    a=Math.max(.07,fade);
    rgb=[0,1,2].map(k=>sample(159.5,3.5-(d-r),k));
    // The edge color remains the OEM silver even where the source alpha vanished.
    if(Math.min(...rgb)<80)rgb=[145,167,179];
   } else if(d>=r-5 && d<r){a=.7*(d-(r-5))/5;}
   sum[3]+=a;for(let k=0;k<3;k++)sum[k]+=rgb[k]*a;
  }
  const i=(y*n+x)*4;for(let k=0;k<3;k++)out[i+k]=sum[3]?Math.round(sum[k]/sum[3]):0;out[i+3]=Math.round(sum[3]/16*255);
 }
 const target=path.join(root,'voyah-iran/app/src/main/res/drawable-nodpi/cluster_map_ring_minimal.png');
 await sharp(out,{raw:{width:n,height:n,channels:4}}).png().toFile(target);
 if(out[(215*n+215)*4+3]!==0)throw Error('Center must remain clear');
 const bottom=out[(427*n+215)*4+3];if(bottom<13||bottom>26)throw Error('Bottom alpha outside 5–10%: '+bottom);
 const previews=path.join(root,'voyah-iran/app/build/reports/cluster-ring');fs.mkdirSync(previews,{recursive:true});
 const svg=Buffer.from(`<svg width="430" height="430"><rect width="430" height="430" fill="#080b10"/><circle cx="215" cy="215" r="210" fill="#607b88"/><path d="M40 170L340 300M100 400L290 20" stroke="#becac1" stroke-width="14"/></svg>`);
 const mask=Buffer.from('<svg width="430" height="430"><circle cx="215" cy="215" r="210" fill="white"/></svg>');
 const demo=await sharp(svg).composite([{input:mask,blend:'dest-in'}]).png().toBuffer();
 await sharp({create:{width:430,height:430,channels:4,background:'#080b10'}}).composite([{input:demo},{input:target}]).png().toFile(path.join(previews,'preview.png'));
 console.log('PNG 430x430; map diameter 420; outer rim 4px; inward shadow 5px; bottom alpha',bottom,'/255');
})();
