const fs=require('fs'),vm=require('vm'),assert=require('assert/strict'),path=require('path');
const source=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/cluster_image_layer.js'),'utf8');
new vm.Script(source);
let now=1000,added=0,snapshots=0;const queue=[],logs=[];
const original={visibility:0,equals(x){return this===x;},getVisibility(){return this.visibility;},setVisibility(v){this.visibility=v;}};
const image={alpha:0,equals(x){return this===x;},setAlpha(v){this.alpha=v;},getAlpha(){return this.alpha;},setClickable(){},setFocusable(){},setScaleType(){},setImageDrawable(){},getDrawable(){return{invalidateSelf(){}};}};
const panel={mViewType:{value:0},equals(x){return this===x;},getContext(){return{};},getChildCount(){return added?2:1;},getChildAt(i){return i?image:original;},addView(){added++;}};
const ctx={rpc:{},console:{log:s=>logs.push(s)},Date:{now:()=>now},setTimeout:(f,ms)=>queue.push({f,at:now+ms}),
 Java:{retain:v=>v,scheduleOnMainThread:f=>f(),use:()=>({FIT_CENTER:{value:0}})},image,panel,clock:()=>now,snap:()=>{snapshots++;return{};}};
vm.createContext(ctx);vm.runInContext(source.slice(0,source.indexOf('Java.perform(function(){try{')),ctx);
vm.runInContext("Screen={screenActivity:{value:{isDestroyed:()=>false,panelMusicView:{value:panel}}}};Image={$new:()=>image};Frame={$new:()=>({leftMargin:{},topMargin:{}})};Drawable={$new:()=>({})};Clock={uptimeMillis:clock};music={id:'music'};map={id:'map'};snapshot=snap;",ctx);
function command(s){ctx.rpc.exports.onFridaStdin(s);}
function advance(ms){const end=now+ms;while(queue.some(x=>x.at<=end)){queue.sort((a,b)=>a.at-b.at);const x=queue.shift();now=x.at;x.f();}now=end;}
command('music');advance(400);assert.equal(added,1);assert.equal(image.alpha,1);assert.equal(original.visibility,4);
command('map');advance(180);assert.equal(image.alpha,1);assert.equal(snapshots,1);
command('music');advance(400);assert.equal(image.alpha,1);assert.equal(snapshots,2);assert.equal(added,1);
command('off');assert.equal(original.visibility,0);advance(400);assert.equal(image.alpha,0);assert.equal(added,1);
command('off');advance(400);assert.equal(added,1);
command('map');advance(400);assert.equal(image.alpha,1);assert.equal(original.visibility,4);assert.equal(added,1);
const before=logs.length;command('map');advance(400);assert.equal(logs.length,before,'Repeated button is a no-op');
command('music');advance(80);command('off');advance(400);assert.equal(image.alpha,0);assert.equal(original.visibility,0);assert.equal(added,1);
console.log('PASS: one persistent image, factory visibility, off fade, crossfade/reversal and duplicate commands (mocked)');
