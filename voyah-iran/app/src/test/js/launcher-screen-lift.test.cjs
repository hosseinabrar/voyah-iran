const fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('assert/strict');
const root=path.join(__dirname,'../../main/java/com/abrar/voyahiran/core/bridge');
const source=fs.readFileSync(path.join(root,'LauncherScreenLiftScript.kt'),'utf8').split('"""')[1].replaceAll('__DOLLAR__','$');
const own='com.abrar.voyahiran/.feature.launcher.';
const stock='com.qinggan.app.launcher/';
function setup(initial=2){
 const calls=[],tops=[own+'VoyahLauncherActivity',own+'PassengerAppsActivity'];let receive,page=0,active=true;
 const ctx={getContentResolver(){return{}},registerReceiver(r){receive=r.onReceive},startActivity(i,o){calls.push({name:i.name,display:o.display,extras:i.extras});tops[o.display]=i.pkg+'/'+i.name}};
 const Intent={$new(){const i={extras:{},setClassName(pkg,name){this.pkg=pkg;this.name=name},addFlags(){}};i.putExtra={overload(){return{call(self,k,v){self.extras[k]=v}}}};return i}};
 const types={
  'android.app.ActivityThread':{currentApplication:()=>ctx},
  'android.provider.Settings$Global':{getInt:()=>active?1:0,getString:()=>String(Date.now()+60000)},
  'com.qinggan.os.ServiceManager':{getDpyTopAppInfo:(c,d)=>tops[d]},
  'com.qinggan.utils.ScreenLiftUtils':{getScreenLiftState:()=>initial},
  'com.qinggan.launcher.base.BaseApplication':{setMainPagePosition:n=>page=n,getMainPagePosition:()=>page},
  'android.content.Intent':Intent,
  'android.app.ActivityOptions':{makeBasic(){return{setLaunchDisplayId(d){this.display=d},toBundle(){return this}}}},
  'android.content.BroadcastReceiver':{},'android.content.IntentFilter':{$new:()=>({addAction(){}})}
 };
 const context={console,Date,Java:{perform:f=>f(),scheduleOnMainThread:f=>f(),use:n=>{assert(n in types,n);return types[n]},retain:r=>r,registerClass:c=>({$new:()=>c.methods})}};
 vm.runInNewContext(source,context);
 return {calls,tops,context,event(n,action='action.qg.layout.changed'){receive(ctx,{getAction:()=>action,getIntExtra:()=>n})},setPage:n=>page=n,disable:()=>active=false};
}
{
 const s=setup();s.event(1);
 assert.equal(s.calls.length,2);
 assert.equal(s.calls[0].name,'com.qinggan.app.launcher.activity.MainActivity');
 assert.equal(s.calls[1].name,'com.qinggan.secondlauncher.activity.AllAppActivity');
 assert.equal(s.context.VI_LAUNCHER_SCREEN_UP(),false);
 s.calls.length=0;s.event(2);
 assert.deepEqual(s.calls.map(c=>c.name.split('.').pop()),['VoyahLauncherActivity','PassengerAppsActivity']);
}
{
 const s=setup();s.event(1);s.calls.length=0;
 s.tops[0]='com.car.ac/.Main';s.tops[1]='com.car.phone/.Main';s.event(2);
 assert.equal(s.calls.length,0,'must not steal another app');
}
{
 const s=setup();s.event(1);s.calls.length=0;s.setPage(1);
 s.tops[1]=stock+'com.qinggan.secondlauncher.activity.SecondMainActivity';s.event(2);
 assert.deepEqual(s.calls.map(c=>c.name.split('.').pop()),['LauncherAppsActivity','PassengerLauncherActivity']);
}
{
 const s=setup(1);assert.equal(s.calls.length,2,'attach while lowered');
 s.calls.length=0;s.tops[0]=own+'LauncherAppsActivity';s.event(0,'android.intent.action.TOP_ACTIVITY_CHANGED');
 assert.equal(s.calls.length,1);assert.equal(s.calls[0].extras.showAllApp,true);
 s.calls.length=0;s.event(0);assert.equal(s.calls.length,0,'ignore unknown states');
 s.disable();s.event(2);assert.equal(s.calls.length,0,'respect entitlement and preference');
}
for(const file of ['LauncherHomeScript.kt','PassengerHomeScript.kt','LauncherAppsScript.kt','LauncherWakeCoverScript.kt']){
 assert(fs.readFileSync(path.join(root,file),'utf8').includes('!globalThis.VI_LAUNCHER_SCREEN_UP()'),file+' must yield when lowered');
}
console.log('Screen lift routing: 4 scenarios and 4 entry-point guards passed');
