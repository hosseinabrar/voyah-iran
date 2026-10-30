const fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('assert/strict');
const src=fs.readFileSync(path.join(__dirname,'../../main/java/com/abrar/voyahiran/core/bridge/LauncherWakeCoverScript.kt'),'utf8').split('"""')[1].replaceAll("${'$'}",'$');
let now=10000,expires=90000,enabled=true,page=0,display=0,theme='simple',lifecycle,receiver,wakes=0;
let timerId=0;const timers=new Map(),main=[],views=[];
const disposable=o=>Object.assign(o,{$dispose(){}});
const overlay={add:v=>views.push(v),remove:v=>{const i=views.indexOf(v);if(i>=0)views.splice(i,1)}};
const decor=disposable({getOverlay:()=>overlay,getLocationOnScreen:a=>{a[0]=0;a[1]=0}});
const activity=disposable({getClass:()=>({getName:()=> 'com.qinggan.app.launcher.activity.MainActivity'}),
  getWindow:()=>({getDecorView:()=>decor}),equals:a=>a===activity,
  getWindowManager:()=>({getDefaultDisplay:()=>({getDisplayId:()=>display,getRealSize:p=>{p.x.value=1920;p.y.value=720}})})});
const app={getContentResolver:()=>({}),registerActivityLifecycleCallbacks:c=>lifecycle=c,registerReceiver:r=>receiver=r};
const classes={
  'android.provider.Settings$Global':{getInt:()=>enabled?1:0,getString:()=>String(expires)},
  'android.os.SystemClock':{elapsedRealtime:()=>now},
  'android.app.ActivityThread':{currentApplication:()=>app},
  'android.view.ViewGroup':{},
  'android.graphics.Point':{$new:()=>({x:{value:0},y:{value:0}})},
  'android.view.View':{$new:()=>disposable({setBackgroundColor(c){this.color=c},layout(...r){this.bounds=r}})},
  'android.os.SystemProperties':{get:()=>theme},
  'com.qinggan.launcher.base.BaseApplication':{getMainPagePosition:()=>page},
  'android.app.Application$ActivityLifecycleCallbacks':{},'android.content.BroadcastReceiver':{},
  'android.content.IntentFilter':{$new:()=>({addAction(){}})}
};
const sandbox={Java:{perform:f=>f(),use:n=>{assert(classes[n],n);return classes[n]},retain:v=>v,cast:v=>v,array:(t,v)=>v,
  scheduleOnMainThread:f=>main.push(f),registerClass:d=>{
    if(d.implements) for(const phase of ['Pre','Post']) for(const event of ['Created','Started','Resumed','Paused','Stopped','SaveInstanceState','Destroyed']) assert(d.methods['onActivity'+phase+event]);
    else assert.deepEqual(Array.from(d.methods.onReceive.argumentTypes),['android.content.Context','android.content.Intent']);
    for(const m of Object.values(d.methods)){assert.equal(m.returnType,'void');assert(Array.isArray(m.argumentTypes));}
    return {$new:()=>Object.fromEntries(Object.entries(d.methods).map(([k,v])=>[k,v.implementation]))};
  },
  choose:(n,c)=>{c.onMatch(activity);c.onComplete()}},Date:{now:()=>now},console,
  setTimeout:f=>{timers.set(++timerId,f);return timerId},clearTimeout:id=>timers.delete(id),
  VI_HOME_WAKE_CHECK:()=>wakes++};
const flush=()=>{while(main.length)main.shift()()};
const screen=action=>receiver.onReceive(app,{getAction:()=>`android.intent.action.${action}`});
vm.runInNewContext(src,sandbox);flush();
lifecycle.onActivityResumed(activity);
assert.equal(views.length,1);assert.equal(views[0].color,-1);
assert.deepEqual(views[0].bounds,[145,50,1920,720],'cover excludes OEM left/top bars');
theme='classic';screen('SCREEN_OFF');assert.equal(views[0].color,-16777216);
now=100000;screen('SCREEN_ON');assert.equal(views.length,1,'brief wake cover can bridge an expired runtime lease after a valid sleep');
assert.equal(wakes,2,'resume and screen-on request Home check');
// A queued OLD expiry cannot tear down a newer cover.
const stale=[...timers.values()][0];stale();
expires=now+90000;sandbox.VI_HOME_COVER();flush();assert.equal(views.length,1);
for(const f of [...timers.values()])f();flush();assert.equal(views.length,0,'failure timeout restores factory Home');
page=1;sandbox.VI_HOME_COVER();assert.equal(views.length,0,'never mask app drawer');page=0;
display=1;sandbox.VI_HOME_COVER();assert.equal(views.length,0,'never mask passenger display');display=0;
enabled=false;screen('SCREEN_OFF');now+=100000;screen('SCREEN_ON');assert.equal(views.length,0,'disabled launcher has no wake cover');
enabled=true;expires=now-1;screen('SCREEN_OFF');screen('SCREEN_ON');assert.equal(views.length,0,'no grace unless eligible at sleep');
expires=now+90000;sandbox.VI_HOME_COVER();sandbox.VI_HOME_UNCOVER();assert.equal(views.length,0,'explicit drawer clears cover');
sandbox.VI_HOME_COVER();lifecycle.onActivityDestroyed(activity);assert.equal(views.length,0,'destroy releases retained window');
console.log('PASS: wake cover bounds, themes, bounded grace, timeout, stale callbacks, drawer/passenger/disabled guards and cleanup');
