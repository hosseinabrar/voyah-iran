// Offline packaging: retain original tile geometry/boundaries; import generated red lighting only.
const fs=require('fs'),path=require('path');
const {chromium}=require('playwright');
(async()=>{
 const dir=__dirname,root=path.dirname(dir),manifest=JSON.parse(fs.readFileSync(path.join(root,'manifest.json')));
 const browser=await chromium.launch({executablePath:process.env.CHROME_PATH||'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true});
 const page=await browser.newPage();
 for(const name of ['left_fl','left_rl','left_both','right_fr','right_rr','right_both','hood_open','rear_open']){
 const src=path.join(root,'pieces',name+'.png');
 const original=path.join(dir,'original-'+name+'.png');if(!fs.existsSync(original))fs.copyFileSync(src,original);
 const input=[original,path.join(dir,name+'.png')].map(p=>'data:image/png;base64,'+fs.readFileSync(p).toString('base64'));
 const result=await page.evaluate(async({input,name})=>{
  const ims=await Promise.all(input.map(src=>new Promise(resolve=>{const i=new Image();i.onload=()=>resolve(i);i.src=src})));
  const [w,h]=[ims[0].width,ims[0].height];const c=document.createElement('canvas');c.width=w;c.height=h;const ctx=c.getContext('2d');
  ctx.drawImage(ims[0],0,0);const base=ctx.getImageData(0,0,w,h);ctx.drawImage(ims[1],0,0,w,h);const edit=ctx.getImageData(0,0,w,h);
  for(let y=0;y<h;y++)for(let x=0;x<w;x++){
   const i=(y*w+x)*4; const r=edit.data[i],g=edit.data[i+1],b=edit.data[i+2];
   // No background/shape changes, and exact common puzzle boundaries retained.
   if(Math.min(...base.data.slice(i,i+3))>=195||y<2||y>=h-2||x<2||x>=w-2)continue;
   const strength=Math.max(0,Math.min(1,(r-Math.max(g,b)-12)/35));
   for(let k=0;k<3;k++)base.data[i+k]=Math.round(base.data[i+k]*(1-strength)+edit.data[i+k]*strength);
  }
  ctx.putImageData(base,0,0);return c.toDataURL('image/png').split(',')[1];
 },{input,name});fs.writeFileSync(src,Buffer.from(result,'base64'));
 }
 await browser.close();console.log('Imported generated lighting into 8 original puzzle tiles; closed tiles unchanged.');
})();
