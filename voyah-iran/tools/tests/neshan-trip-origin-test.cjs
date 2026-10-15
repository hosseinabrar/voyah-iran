const fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('assert/strict');
const root=path.resolve(__dirname,'../../..');
const profiles=JSON.parse(fs.readFileSync(path.join(root,'voyah-iran-subscription-server/configs/neshan.source.json'),'utf8')).profiles;
const source=fs.readFileSync(path.join(root,'voyah-iran/app/src/main/java/com/abrar/voyahiran/core/bridge/NeshanTripScript.kt'),'utf8').split('"""')[1];
for(const profile of profiles) {
 const cfg=profile.routeRestart,s=profile.hook.routeState;
 let active=false,display=0,clicks=0,stops=0,mode='CAR',shown=true;
 const logs=[];
 const start={call(){active=true}},stop={call(){active=false}};
 let destinationLat=35.7;
 const engine={[cfg.navigatorMethod]:()=>({[cfg.currentDestinationMethod]:()=>({[cfg.latitudeMethod]:()=>destinationLat,[cfg.longitudeMethod]:()=>51.4})})};
 const state={[s.activeMethod]:()=>active,[s.engineMethod]:()=>active?engine:null};
 const activity={getClass:()=>({getName:()=>cfg.activityClass}),isFinishing:()=>false,isDestroyed:()=>false,
  getWindow:()=>({getDecorView:()=>({isShown:()=>shown})}),getWindowManager:()=>({getDefaultDisplay:()=>({getDisplayId:()=>display,getName:()=>display>1?'VoyahIran.MapPreview':'physical'})}),
  getResources:()=>({getIdentifier:()=>100}),getPackageName:()=> 'neshan',findViewById:()=>({isAttachedToWindow:()=>true,isEnabled:()=>true,hasOnClickListeners:()=>true,
   performClick:()=>{clicks++;stop.implementation();return true}})};
 const records={values:()=>({iterator:()=>{let done=false;return {hasNext:()=>!done,next:()=>{done=true;return {getClass:()=>({getDeclaredField:()=>({setAccessible(){},get:()=>activity})})}}}}})};
 const types={
  [s.ownerClass]:{class:{getDeclaredField:()=>({setAccessible(){},get:()=>state})}},
  [s.stateClass]:{[cfg.startMethod]:{overload:()=>start},[cfg.stopMethod]:{overload:()=>stop}},
  'android.app.ActivityThread':{currentActivityThread:()=>({getClass:()=>({getDeclaredField:()=>({setAccessible(){},get:()=>records})})})}
 };
 const ctx={NESHAN_PROFILE:profile,Date:{now:()=>10000},console:{log:x=>logs.push(x)},Java:{use:n=>types[n],cast:x=>x,scheduleOnMainThread:f=>f()}};
 vm.createContext(ctx);vm.runInContext(source+'\nglobalThis.tripApi=VI_TRIP;',ctx);const api=ctx.tripApi;api.install();
 const snapshot=()=>{api.publish();return JSON.parse(logs.at(-1).replace('VI_NESHAN_TRIP ',''))};
 const route={[cfg.metadataMethod]:()=>({[cfg.destinationMethod]:()=>({[cfg.latitudeMethod]:()=>35.7,[cfg.longitudeMethod]:()=>51.4}),
  [cfg.stopsMethod]:()=>({size:()=>stops}),[cfg.routingTypeMethod]:()=>({name:()=>mode})})};
 const begin=()=>{start.implementation(route,0,'','');return snapshot()};
 active=true;assert.equal(snapshot().valid,false,'Late attach must not infer origin');
 display=3;let cardTrip=begin();assert.equal(cardTrip.origin,'card');
 display=0;snapshot();display=1;snapshot();display=3;
 api.cancel(cardTrip.id,3);assert.equal(clicks,0,'Same card trip, including passenger detour, must not restart');
 display=0;stop.implementation();let outside=begin();assert.notEqual(outside.id,cardTrip.id);
 api.cancel(outside.id,3);assert.equal(clicks,0,'Never cancel on physical display');
 display=3;api.cancel(outside.id,3);assert.equal(clicks,1);assert.equal(snapshot().stage,'stopped');assert.equal(snapshot().valid,false);
 api.cancel(outside.id,3);assert.equal(clicks,1,'One shot');
 const replacement=begin();assert.equal(replacement.origin,'card');api.cancel(outside.id,3);assert.equal(clicks,1,'Stale id cannot cancel replacement');
 display=1;stop.implementation();outside=begin();display=3;api.cancel(outside.id,4);assert.equal(clicks,1,'Display mismatch');
 api.cancel(outside.id,3);assert.equal(clicks,2,'New trip started on passenger is eligible');
 display=0;stops=2;outside=begin();display=3;api.cancel(outside.id,3);assert.equal(clicks,2,'Do not drop waypoints');
 stops=0;mode='PEDESTRIAN';display=0;outside=begin();display=3;api.cancel(outside.id,3);assert.equal(clicks,2,'Do not change travel mode');
 mode='CAR';display=0;outside=begin();display=3;destinationLat=36;api.cancel(outside.id,3);assert.equal(clicks,2,'Changed live destination must not be overwritten');destinationLat=35.7;
 shown=false;outside=begin();assert.equal(outside.origin,'unknown');shown=true;api.cancel(outside.id,3);assert.equal(clicks,2);
 const tasks=[];ctx.Java.scheduleOnMainThread=f=>tasks.push(f);
 display=0;outside=begin();display=3;api.cancel(outside.id,3);
 begin();tasks.shift()();assert.equal(clicks,2,'A queued cancellation must revalidate the trip on the UI thread');
 api.disable();assert.equal(start.implementation,null);assert.equal(stop.implementation,null);
 console.log('PASS: '+profile.versionName+' trip origin, new trip, stale ids, once-only, unknown history, stops, modes, cleanup');
}
