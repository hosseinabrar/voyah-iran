const fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('assert/strict');
const root=path.resolve(__dirname,'../../..');
const read=p=>fs.readFileSync(path.join(root,p),'utf8');
const raw=read('voyah-iran/app/src/main/java/com/abrar/voyahiran/core/bridge/PassengerHomeScript.kt');
const source=raw.split('"""')[1].replaceAll('__DOLLAR__','$');
let now=100000,enabled=1,lease=160000,vip=160000,top='com.qinggan.app.launcher/com.qinggan.secondlauncher.activity.SecondMainActivity';
const launches=[];
const ctx={getContentResolver:()=>null,startActivity:(intent,options)=>launches.push({component:intent.component,display:options.display})};
const types={
 'android.provider.Settings$Global':{getInt:()=>enabled,getString:(_,k)=>k.includes('vip')?vip:lease},
 'android.content.Intent':{$new:()=>({setClassName(p,c){this.component=p+'/'+c;},addFlags(){}})},
 'android.app.ActivityOptions':{makeBasic:()=>({setLaunchDisplayId(i){this.display=i;},toBundle(){return this;}})},
 'android.app.ActivityThread':{currentApplication:()=>ctx},
 'com.qinggan.launcher.base.utils.AppUtils':{getTopAppInfo:(_,d)=>{assert.equal(d,1);return top;}}
};
const env={Date:{now:()=>now},console:{log(){}},Java:{perform:f=>f(),scheduleOnMainThread:f=>f(),use:n=>{if(!(n in types))throw Error('Not installed');return types[n];}}};
vm.createContext(env);vm.runInContext(source,env);
assert.equal(launches.length,1);assert.equal(launches[0].display,1);assert.match(launches[0].component,/PassengerLauncherActivity$/);
now+=2000;top='com.qinggan.app.launcher/com.qinggan.secondlauncher.activity.AllAppActivity';env.VI_PASSENGER_HOME_CHECK();assert.equal(launches.length,1,'Never steal factory drawer');
top='third.party/.Main';env.VI_PASSENGER_HOME_CHECK();assert.equal(launches.length,1,'Never steal another app');
assert.equal(env.VI_PASSENGER_OPEN(true),true);assert.match(launches.at(-1).component,/PassengerAppsActivity$/);
top='com.abrar.voyahiran/.feature.launcher.PassengerLauncherActivity';assert.equal(env.VI_PASSENGER_HOME_VISIBLE(),true);
for(const changed of ['enabled','lease','vip']){enabled=1;lease=now+60000;vip=now+60000;if(changed==='enabled')enabled=0;if(changed==='lease')lease=now;if(changed==='vip')vip=now;
 assert.equal(env.VI_PASSENGER_OPEN(false),false);assert.equal(env.VI_PASSENGER_HOME_VISIBLE(),false);}
const manifest=read('voyah-iran/app/src/main/AndroidManifest.xml');assert.match(manifest,/PassengerLauncherActivity[^>]*taskAffinity="com.abrar.voyahiran.passenger.home"/);
for(const p of ['display/helpers/VoyahIranDisplayPolicy.smali','android9/helpers/com/android/server/am/VoyahIranLegacyDisplay.smali']){
 const s=read('voyah-iran-installer/tools/patch/'+p).split('.method public static leftInset(')[1];
 assert(s.includes('getDisplayId()I'));assert(s.includes('const/4 v1, 0x1'));assert(s.includes('const-string p0, "com.abrar.voyahiran"'));assert(s.includes('PassengerLauncherActivity'));assert(s.includes('const/16 v0, 0x91'));
}
const required=read('voyah-iran-installer/src/VoyahIran.Installer.Core/Android9FrameworkAssets.cs').match(/"patch\/android9\/previous-merge-production\/[^"\n]+"/g);
for(const s of required){const p=s.slice(1,-1).replace('previous-merge-production','previous-before-passenger');assert(fs.existsSync(path.join(root,'voyah-iran-installer/tools',p)),p);}
console.log('PASS: passenger-only Home/drawer, no foreground stealing, same checkbox and both leases, independent task, bounded gutter exception, upgrade archive completeness');
