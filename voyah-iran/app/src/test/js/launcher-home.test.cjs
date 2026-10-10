const fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('assert/strict');
const src=fs.readFileSync(path.join(__dirname,'../../main/java/com/abrar/voyahiran/core/bridge/LauncherHomeScript.kt'),'utf8').split('"""')[1].replaceAll("${'$'}",'$');
let page=0,now=10000,opens=0,top='com.qinggan.app.launcher/com.qinggan.app.launcher.activity.MainActivity',supported=true;
const queued=[],observers=[];const method=()=>({call(){},implementation:null});const click=method(),selected=method();
const ctx={getContentResolver:()=>({registerContentObserver(u,b,o){observers.push(o)}}),startActivity(){opens++}};
const classes={
'android.provider.Settings$Global':{getString:()=>String(now+30000),getInt:()=>1,getUriFor:k=>k},
'android.content.Intent':{$new:()=>({setClassName(){},addFlags(){}})},
'android.app.ActivityOptions':{makeBasic:()=>({setLaunchDisplayId(){},toBundle(){return {}}})},
'android.app.ActivityThread':{currentApplication:()=>ctx},
'com.qinggan.launcher.base.utils.AppUtils':{getTopAppInfo:()=>top},
'com.qinggan.launcher.base.BaseApplication':{getMainPagePosition(){if(!supported)throw Error('unavailable');return page}},
'android.database.ContentObserver':{},'android.os.Handler':{$new:()=>({})},'android.os.Looper':{getMainLooper:()=>({})},
'com.qinggan.launcher.navigation.NavigationBarMain':{onClick:{overload:()=>click},updateSelectedApp:{overload:()=>selected}}
};
vm.runInNewContext(src,{Java:{perform:f=>f(),use:n=>{if(!classes[n])throw Error(n);return classes[n]},scheduleOnMainThread:f=>queued.push(f),registerClass:d=>({$new:()=>({onChange:d.methods.onChange[0].implementation})})},Date:{now:()=>now},console:{log(){}},setInterval(){},setTimeout(){}});
const flush=()=>{while(queued.length)queued.shift()()};flush();assert.equal(opens,1,'boot Home opens custom launcher');
const bar={mScreenId:{value:0},getContext:()=>ctx};
now+=3000;selected.implementation.call(bar,'com.qinggan.app.launcher','.activity.MainActivity');
page=1;flush();assert.equal(opens,1,'drawer state precedes delayed foreground callback/onNewIntent');
for(let i=0;i<5;i++){now+=3000;observers[0].onChange();selected.implementation.call(bar,'com.qinggan.app.launcher','.activity.MainActivity');flush()}
assert.equal(opens,1,'lease refresh never steals drawer even beyond cooldown');
click.implementation.call(bar,{getId:()=>1,getResources:()=>({getResourceEntryName:()=> 'screen_up_home_icon'})});assert.equal(opens,2,'explicit Home works from drawer');
page=0;now+=3000;observers[0].onChange();flush();assert.equal(opens,3,'actual OEM Home still redirected');
top='third.party/Activity';now+=3000;observers[0].onChange();flush();assert.equal(opens,3);
top='com.qinggan.app.launcher/.activity.MainActivity';supported=false;now+=3000;observers[0].onChange();flush();assert.equal(opens,3,'unknown OEM page state does not steal foreground');
console.log('Home/drawer race, repeated lease updates, boot, explicit Home and unknown-layout checks passed');
