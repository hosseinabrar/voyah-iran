const fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('assert/strict');
const src=fs.readFileSync(path.join(__dirname,'../../main/java/com/abrar/voyahiran/core/bridge/LauncherTransferScript.kt'),'utf8').split('"""')[1].replaceAll("${'$'}",'$');
for(const safe of [true,false]) {
 let now=1000,launched=0,removed=0,draw,params,duration,source=true;const timers=[],logs=[];
 const pkg='map.app';const bounds={left:{value:safe?145:0},top:{value:safe?50:0},isEmpty:()=>false,width:()=>safe?1775:1920,height:()=>safe?670:720};
 const stack={displayId:{get value(){return source?7:1}},topActivity:{value:{getPackageName:()=>pkg}},taskNames:{value:[]},visible:{value:true},bounds:{value:bounds}};
 const launch={call(){assert.ok(params,'mask installed before launch');launched++;source=false},implementation:null};
 const anim={alpha(){return this},setDuration(n){duration=n;return this},start(){}};
 const view={setBackgroundColor(){},getViewTreeObserver:()=>({addOnDrawListener(v){draw=v},removeOnDrawListener(){}}),animate:()=>anim};
 const wm={addView(v,lp){params=lp;draw.onDraw()},removeView(){removed++}};
 const disp=id=>({getName:()=>id===7?'VoyahIran.MapPreview':'physical',getRealSize(p){p.x.value=1920;p.y.value=720}});
 const ctx={getContentResolver:()=>({}),getSystemService:()=>({getDisplay:disp}),createDisplayContext:()=>({getSystemService:()=>wm}),getPackageManager:()=>({getApplicationInfo:()=>({flags:{value:0},uid:{value:10100}})})};
 const classes={
 'android.provider.Settings$Global':{getString(r,k){return k.endsWith('lease')?'80000':k.endsWith('_ack')?(safe?'1:default':'0'):k.endsWith('_policy')?(safe?null:'0'):null},getInt:()=>1,putString(){}},
 'android.app.ActivityTaskManager':{getService:()=>({getAllStackInfos:()=>({size:()=>1,get:()=>stack})})},
 'android.app.ActivityManager$StackInfo':{},'android.hardware.display.DisplayManager':{},'android.view.WindowManager':{},
 'android.view.WindowManager$LayoutParams':{$new(w,h){return {w,h,x:{},y:{},gravity:{},setTitle(s){assert.equal(s.javaString,true,'CharSequence must be Java String')}}}},
 'android.view.View':{$new:()=>view},'java.lang.String':{$new:s=>({javaString:true,text:s})},'android.graphics.Point':{$new:()=>({x:{value:0},y:{value:0}})},
 'com.pateo.material.anim.ActivityAnimUtils':{startActivityByAnim:{overload:()=>launch}},'android.view.ViewTreeObserver$OnDrawListener':{}};
 vm.runInNewContext(src,{Java:{perform:f=>f(),use:n=>{assert.ok(classes[n],n);return classes[n]},cast:x=>x,retain:x=>x,registerClass:d=>({$new:()=>d.methods}),scheduleOnMainThread:f=>f()},console:{log:s=>logs.push(s)},Date:{now:()=>now},setTimeout:(f,n)=>timers.push({f,t:now+n})});
 launch.implementation(ctx,{getComponent:()=>({getPackageName:()=>pkg})},1);
 assert.equal(params.x.value,safe?145:0);assert.equal(params.y.value,safe?50:0);assert.equal(params.w,safe?1775:1920);
 assert.equal(launched,0,'wait until black frame has drawn');
 while(timers.length){timers.sort((a,b)=>a.t-b.t);const job=timers.shift();now=job.t;job.f();}
 assert.equal(launched,1);assert.equal(duration,350);assert.equal(removed,1);assert.ok(!logs.some(x=>x.includes('fallback')),logs.join('\n'));
}
console.log('Map transfer mask: strict Java title, safe/full bounds, draw-before-launch and 350ms fade passed');
