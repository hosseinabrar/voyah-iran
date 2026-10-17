// Executable compositor tests with mocked Android views/canvases, NOT a device test.
const fs=require('fs'),vm=require('vm'),assert=require('node:assert/strict'),path=require('path');
const file=path.join(__dirname,'../../app/src/main/java/com/abrar/voyahiran/core/bridge/ClusterMusicScript.kt');
const source=fs.readFileSync(file,'utf8').split('"""')[1].replaceAll("${'$'}",'$');
new vm.Script(source);
let clock=1000,removed=0,draws=[],musicFrames=0,idleFrames=0,invalidations=0;
const factory={visibility:0,equals(x){return this===x;},getVisibility(){return this.visibility;},setVisibility(x){this.visibility=x;},$dispose(){}};
const other={...factory,visibility:8};
const image={visibility:0,setVisibility(v){this.visibility=v;},equals(x){return this===x;},setImageDrawable(){},setImageBitmap(b){assert.equal(b,m.outputBitmap);invalidations++;},$dispose(){}};
const children=[factory,other,image];
const decor={getChildCount:()=>children.length,getChildAt:i=>children[i],getWidth:()=>1920,getHeight:()=>720,
 removeView:v=>{assert.equal(v,image);removed++;},$dispose(){}};
const paint=()=>({alpha:255,setAlpha(x){this.alpha=x;},setColor(){},$dispose(){}});
let clears=0;
const canvas={drawColor(){clears++;},drawRect(){},drawBitmap(bitmap,...args){draws.push({bitmap,alpha:args.at(-1).alpha});}};
const m={image,panel:decor,saved:[],paint:paint(),addPaint:paint(),mapSrc:{},mapDst:{set(){}},outputCanvas:canvas,outputBitmap:{recycle(){}},
 musicBitmap:{recycle(){},$dispose(){}},canvas:{drawColor(){}},music:{draw(){musicFrames++;}},empty:{draw(){idleFrames++;}},
 w:490,h:506,location:[90,154],ready:false,window:{setBackgroundDrawable(){},setFormat(){}}};
const context={console,Date:{now:()=>clock},clock:()=>clock,m,canvas,
 Java:{retain:x=>x},setInterval(){},rpc:{}};
vm.createContext(context);
vm.runInContext(source.slice(0,source.indexOf('rpc.exports=')),context);
const run=s=>vm.runInContext(s,context);
run('Clock={elapsedRealtime:clock};Porter={CLEAR:{value:0}};geometry=()=>true;progress=()=>{};mounted=m;packet={owned:true,map:false,frame:{idle:false,musicEnabled:true}};feed={received:true,bitmap:{},frames:1};');
function draw(){draws=[];run('received=Date.now();renderFrame()');return draws;}
draw();assert.equal(factory.visibility,4);assert.equal(other.visibility,4);assert.equal(draws[0].alpha,255);
assert.equal(removed,0);assert.equal(musicFrames,1);
run('packet.map=true');draw();clock+=180;draw();
assert.equal(draws[0].alpha,128);assert.equal(draws[1].alpha,128);
clock+=180;draw();assert.equal(draws.length,1,'Stable map copies only the map input');assert.equal(draws[0].bitmap,run('feed.bitmap'));assert.equal(draws[0].alpha,255);
assert.equal(musicFrames,4,'Music view continues rendering underneath a fully visible map');
const bitmap=m.musicBitmap;
for(let i=0;i<10;i++){clock+=16;draw();}
assert.equal(m.musicBitmap,bitmap);assert.equal(removed,0,'No view teardown on heartbeat');
run('packet.map=false');draw();clock+=100;draw();const before=run('alpha(now())');
run('packet.map=true');draw();assert.equal(run('alpha(now())'),before,'Rapid reversal starts at actual current mixture');
clock+=400;draw();assert.equal(draws.length,1);assert.equal(draws[0].bitmap,run('feed.bitmap'));
run('packet.frame={idle:true,musicEnabled:true}');draw();assert(idleFrames>0,'Idle is rendered by our detached OEM-layout clone');
assert.equal(factory.visibility,4,'Idle never reveals factory views');
// Fullscreen selects music but keeps the same image and live feed.
run('packet.map=false');draw();clock+=400;draw();assert.equal(draws[0].alpha,255);assert.equal(removed,0);
factory.visibility=0;draw();assert.equal(factory.visibility,4,'Repair OEM visibility writes before our frame');
run('feed.received=false;packet.map=true');draw();assert.equal(run('blendTo'),0,'Do not fade music out before a map frame exists');
assert(invalidations>10,'Each completed output is handed to the image');
assert.equal(clears,invalidations,'The output bitmap is fully cleared before EVERY frame');
const priorMusic=musicFrames,priorIdle=idleFrames;
run('packet.frame.musicEnabled=false;packet.map=false;mounted.ready=false');draw();
assert.equal(image.visibility,4);assert.equal(factory.visibility,0);assert.equal(other.visibility,8);
assert.equal(m.ready,true,'Map-only hidden output still reports ready to avoid first-frame deadlock');
assert.equal(removed,0,'Map-only OFF keeps the image attached');
factory.visibility=8;draw();assert.equal(factory.visibility,8,'OEM keeps updating itself while our image is hidden');
factory.visibility=0;
run('packet.map=true;feed.received=true');draw();
assert.equal(image.visibility,0);assert.equal(factory.visibility,4);assert.equal(draws.length,1);
assert.equal(draws[0].bitmap,run('feed.bitmap'));
run('packet.map=false');draw();assert.equal(image.visibility,4);assert.equal(factory.visibility,0);
assert.equal(musicFrames,priorMusic);assert.equal(idleFrames,priorIdle,'Map-only never renders our music or idle card');
run('closeFeed=()=>{feed=null};restore()');assert.equal(removed,1);assert.equal(factory.visibility,0);assert.equal(other.visibility,8);
console.log('PASS: single image, complementary alpha, live/idle render, reversal, fullscreen, first-frame gating, factory restore (mocked)');
