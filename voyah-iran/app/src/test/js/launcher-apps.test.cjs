const fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('assert/strict');
const base=path.join(__dirname,'../../main/java/com/abrar/voyahiran');
const policy=fs.readFileSync(path.join(base,'feature/launcher/LauncherAppsPolicy.kt'),'utf8');
const hidden=[...policy.split('setOf(')[1].split(')')[0].matchAll(/"([^"]+)"/g)].map(m=>m[1]);
const source=fs.readFileSync(path.join(base,'core/bridge/LauncherAppsScript.kt'),'utf8').split('"""')[1].replaceAll('__DOLLAR__','$').replace('__EXCLUDED__',JSON.stringify(hidden));
for(const legacy of [false,true]) {
 let now=1000,opens=0,observer,launched=[];const sent=[];
 const settings={voyah_iran_launcher_home:1,voyah_iran_launcher_lease:2000,voyah_iran_launcher_vip_lease:2000};
 const all=[...hidden,'Music','Seat','APP_RDS','custom.user'];
 const beans=all.map(subtype=>({getClass:()=>({getName:()=> 'Bean'}),getSubType:()=>subtype,isDynamicApp:()=>subtype==='custom.user',getNameRes:()=>1,getIcon:()=>1}));
 const list={size:()=>beans.length,get:i=>beans[i]};
 const ctx={getContentResolver:()=>({registerContentObserver(u,b,o){observer=o}}),getString:()=> 'OEM label',sendBroadcast:i=>sent.push(i),startActivity:()=>opens++};
 const intent=()=>({extras:{},setPackage(){},setClassName(){},addFlags(){},putExtra:{overload:()=>({call(i,k,v){i.extras[k]=v}})}});
 const legacyClick={call(){},implementation:null};
 const classes={
 'android.provider.Settings$Global':{getInt:(c,k)=>settings[k],getString:(c,k)=>settings[k],getUriFor:k=>k},
 'android.content.Intent':{$new:intent},'java.lang.String':{},'android.app.ActivityThread':{currentApplication:()=>ctx},
 [legacy?'com.qinggan.launcher.base.allapp.AllAppDataManager':'com.qinggan.launcher.allapp.AllAppDataManager']:{getAllApps:()=>list},
 'com.qinggan.theme.SkinResourceManager':{getInstance:()=>({getDrawable:()=>null})},
 'com.qinggan.launcher.base.utils.AppLauncher':{startAppBySubType:(c,s,d)=>launched.push([s,d])},
 'android.os.SystemProperties':{get:()=> 'simple'},'Bean':{},'android.database.ContentObserver':{},
 'android.os.Handler':{$new:()=>({})},'android.os.Looper':{getMainLooper:()=>({})},
 'android.app.ActivityOptions':{makeBasic:()=>({setLaunchDisplayId(){},toBundle:()=>({})})}
 };
 if(legacy)classes['com.qinggan.mainlauncher.navigation.NavigationBar']={onClick:{overload:()=>legacyClick},updateSelectedApp:{overloads:[]}};
 const sandbox={Java:{perform:f=>f(),scheduleOnMainThread:f=>f(),use:n=>{if(!classes[n])throw Error(n);return classes[n]},cast:v=>v,retain:v=>v,registerClass:d=>({$new:()=>({onChange:d.methods.onChange[0].implementation})})},Date:{now:()=>now},console};
 vm.runInNewContext(source,sandbox);
 assert.equal(sandbox.VI_OPEN_DRAWER(ctx),true);assert.equal(opens,1);
 if(legacy) {
  const bar={mScreenId:{value:0},mContext:{value:ctx}};
  const view={getId:()=>1,getResources:()=>({getResourceEntryName:()=> 'screen_up_all_app_icon'})};
  legacyClick.implementation.call(bar,view);assert.equal(opens,2);
  bar.mScreenId.value=1;legacyClick.implementation.call(bar,view);assert.equal(opens,2,'passenger is untouched');
 }
 let serial=0;function request(action,id){settings.voyah_iran_drawer_request=action+':'+(++serial).toString(16).padStart(32,'0')+':'+id;observer.onChange();return JSON.parse(sent.at(-1).extras.payload)}
 assert.deepEqual(request('catalog','all').items.map(i=>i.subtype),['Music','Seat','APP_RDS']);
 assert.equal(request('catalog','all').light,true);
 assert.equal(request('open','Seat').opened,true);assert.deepEqual(launched,[['Seat',0]]);
 assert.ok(request('open','Navigation').error);assert.ok(request('open','not.installed').error);
 settings.voyah_iran_launcher_vip_lease=0;assert.equal(sandbox.VI_OPEN_DRAWER(ctx),false);assert.ok(request('open','Music').error);
 settings.voyah_iran_launcher_vip_lease=2000;settings.voyah_iran_launcher_home=0;assert.equal(sandbox.VI_OPEN_DRAWER(ctx),false);
}
console.log('OEM catalog adapters, exclusions, themed metadata, OEM dispatch and VIP gating passed');
