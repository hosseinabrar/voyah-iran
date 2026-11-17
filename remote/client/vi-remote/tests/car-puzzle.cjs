const {chromium}=require('playwright');
const fs=require('fs'),path=require('path'),assert=require('assert');
(async()=>{
 const root=path.resolve(__dirname,'..');
 const m=await import('data:text/javascript;base64,'+fs.readFileSync(path.join(root,'car-puzzle.js')).toString('base64'));
 const keys=['doorFL','doorFR','doorRL','doorRR','hood','trunk'];const seen=new Set();
 for(let mask=0;mask<64;mask++){
  const v=Object.fromEntries(keys.map((k,i)=>[k,!!(mask&(1<<i))]));const pieces=m.selectCarPieces(v);
  assert.equal(pieces.length,4);seen.add(pieces.join(','));
  for(const piece of pieces)assert(fs.existsSync(path.join(root,'assets/car-puzzle',piece+'.png')));
 }
 assert.equal(seen.size,64);
 const browser=await chromium.launch({executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true});
 const page=await browser.newPage({viewport:{width:1200,height:1100}});
 await page.goto(process.env.VI_REMOTE_TEST_URL||'http://127.0.0.1:18774');
 await page.evaluate(async()=>{
  const {puzzleLayers,selectCarPieces}=await import('./car-puzzle.js');
  document.body.innerHTML='';document.body.style.cssText='background:#101820;display:grid;grid-template-columns:repeat(4,300px);margin:0;max-width:none;direction:ltr';
  const masks=[0,1,2,4,8,15,48,63];
  for(const mask of masks){const v=Object.fromEntries(['doorFL','doorFR','doorRL','doorRR','hood','trunk'].map((k,i)=>[k,!!(mask&(1<<i))]));
   const box=document.createElement('div');box.style.cssText='position:relative;height:550px;overflow:hidden';
   const label=document.createElement('div');label.textContent='State '+mask;label.style.cssText='color:white;text-align:center';box.append(label);
   const scene=document.createElement('div');scene.style.cssText='position:absolute;top:30px;left:150px;transform:scale(.75);transform-origin:0 0';
   for(const key of selectCarPieces(v)){const l=puzzleLayers[key];const im=new Image();im.src='assets/car-puzzle/'+key+'.png';im.style.cssText=`position:absolute;left:${l.x}px;top:${l.y}px;width:${l.w}px;height:${l.h}px`;scene.append(im)}box.append(scene);document.body.append(box);
  }
  await Promise.all([...document.images].map(i=>i.decode()));
 });
 await page.screenshot({path:path.join(root,'artwork/puzzle/qa/states.png'),fullPage:true});await browser.close();
 console.log('PASS: 64 unique four-piece states; assets exist; eight representative browser renders decoded.');
})().catch(e=>{console.error(e);process.exit(1)});
