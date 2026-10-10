const fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('assert/strict');
const source=fs.readFileSync(path.join(__dirname,'../../main/java/com/abrar/voyahiran/core/bridge/VehicleBarsScript.kt'),'utf8').split('"""')[1].replaceAll('__DOLLAR__','$');
let theme='simple',lift=2,fail=false;const overlays=[],removed=[],logs=[];
const drawable=color=>({getColor:()=>color,equals(other){return other===this}});
const original=drawable(123), nav={bg:original,getBackground(){return this.bg},setBackground(v){this.bg=v},equals(v){return this===v}};
const bar={mScreenId:{value:0},mNavigationBar:{value:{getScreenUpView:()=>nav}},equals(b){return b===this}};
const wm={addView(v,p){if(fail)throw Error('denied');overlays.push({v,p})},removeViewImmediate(v){removed.push(v)}};
const dc={getSystemService:()=>wm};const ctx={getSystemService:()=>({getDisplay:()=>({getRealSize(p){p.x.value=1920}})}),createDisplayContext:()=>dc};
const classes={
'com.qinggan.launcher.navigation.NavigationBarController':{show:{overload:()=>({call(){}})}},
'android.graphics.drawable.ColorDrawable':{$new:drawable},
'android.view.View':{$new:()=>({setBackgroundColor(c){this.color=c}})},
'android.view.WindowManager$LayoutParams':{$new:(w,h,t,f,fmt)=>({w,h,t,f,fmt,gravity:{},x:{},y:{},setTitle(){}})},
'android.view.WindowManager':{},'android.hardware.display.DisplayManager':{},
'android.graphics.Point':{$new:()=>({x:{value:0}})},
'android.os.SystemProperties':{get:()=>theme},'com.qinggan.utils.ScreenLiftUtils':{getScreenLiftState:()=>lift}};
const sandbox={console:{log:s=>logs.push(s)},Java:{perform:f=>f(),use:n=>{assert.ok(classes[n],n);return classes[n]},cast:x=>x,retain:x=>x,choose(n,c){c.onMatch(bar);c.onComplete()}}};
vm.runInNewContext(source,sandbox);const sync=sandbox.VI_SYNC_BAR_BACKGROUND;
sync(ctx,0,true);assert.equal(nav.bg.getColor(),0xffe5ebf3|0);assert.equal(overlays.length,1);
const lp=overlays[0].p;assert.equal(lp.x.value,145);assert.equal(lp.h,50);assert.equal(lp.w,1775);assert.equal(lp.t,2038);assert.equal(lp.f&24,24);
sync(ctx,0,true);assert.equal(overlays.length,1,'No per-tick window rebuild');
theme='default';sync(ctx,0,true);assert.equal(nav.bg.getColor(),0xff000000|0);assert.equal(removed.length,1);
sync(ctx,0,false);assert.equal(nav.bg,original);assert.equal(removed.length,2);
lift=1;sync(ctx,0,true);assert.equal(nav.bg,original);assert.equal(overlays.length,2);
lift=2;fail=true;sync(ctx,0,true);assert.equal(nav.bg,original,'Failure restores native background');
fail=false;theme='simple';sync(ctx,1,true);assert.equal(overlays.length,3,'Passenger supported');
sync(ctx,2,true);assert.equal(overlays.length,3,'Other displays excluded');
sync(ctx,0,true);sandbox.VI_RELEASE_BAR_BACKGROUND(bar);assert.equal(nav.bg,original);
assert.ok(!logs.some(x=>x.includes('UNAVAILABLE')),logs.join('\n'));
console.log('Vehicle bar background tests passed');
