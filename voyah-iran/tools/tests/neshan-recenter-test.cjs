const fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('assert');
const base=path.resolve(__dirname,'../../..');
const profiles=JSON.parse(fs.readFileSync(path.join(base,'voyah-iran-subscription-server/configs/neshan.source.json'))).profiles;
const source=fs.readFileSync(path.join(base,'voyah-iran/app/src/main/java/com/abrar/voyahiran/core/bridge/NeshanRecenterScript.kt'),'utf8').split('"""')[1].replaceAll("${'$'}",'$');
let checks=0;
for(const profile of profiles){
 function run(o={}){
  let now=1000,held=0,freed=0,clicks=0,ups=0;const queue=[],logs=[],events=[];
  const display={getDisplayId:()=>o.leave&&now>=1040?0:7,getName:()=> 'VoyahIran.MapPreview'};
  const button={isShown:()=>ups>=(o.after??1),getVisibility:()=>ups>=(o.after??1)?0:8,isAttachedToWindow:()=>true,isEnabled:()=>!o.disabled,isClickable:()=>!o.notClickable,hasOnClickListeners:()=>!o.noListener,performClick:()=>{clicks++;return !o.rejectClick;}};
  const root={getDisplay:()=>display,isShown:()=>true,isAttachedToWindow:()=>true,isLayoutRequested:()=>false,
   getWidth:()=>871,getHeight:()=>654,getContext:()=>({}),getResources:()=>({getIdentifier:name=>{assert.equal(name,'back_to_navigation_button');return o.noId?0:1;}}),findViewById:()=>o.missing?null:button,
   dispatchTouchEvent:e=>{events.push({action:e.action,at:now});if(e.action===1)ups++;return true;}};
  const rs=profile.hook.routeState,route={[rs.activeMethod]:()=>!o.inactive,[rs.engineMethod]:()=>({})};
  const wm={getClass:()=>({getDeclaredField:()=>({setAccessible(){},get:()=>({size:()=>1,get:()=>root})})})};
  const types={
   [rs.ownerClass]:{[rs.instanceField]:{value:route}},'android.view.WindowManagerGlobal':{getInstance:()=>wm},
   'android.view.MotionEvent':{obtain:{overload:()=>({call:(s,d,t,action)=>({action,setSource(){},recycle(){}})})}},
   'android.os.SystemClock':{uptimeMillis:()=>now},'android.view.ViewConfiguration':{get:()=>({getScaledTouchSlop:()=>8})}
  };
  const ctx={NESHAN_PROFILE:profile,enabled:true,heartbeat:1000,Date:{now:()=>now},console:{log:x=>logs.push(x)},
   setTimeout:(f,ms)=>queue.push({f,at:now+ms}),Java:{perform:f=>f(),scheduleOnMainThread:f=>f(),cast:v=>v,use:n=>types[n]||{},
    retain:v=>{held++;return Object.assign(Object.create(v),{$dispose:()=>freed++});}}};
  vm.createContext(ctx);vm.runInContext(source,ctx);ctx.recenterCard(7,'token',11000);
  while(queue.length){queue.sort((a,b)=>a.at-b.at);const t=queue.shift();now=t.at;t.f();assert(now<=11200);}
  assert.equal(held,freed);assert(logs.some(x=>x.endsWith('accepted')));
  return {events,clicks,logs,now};
 }
 let r=run();assert.equal(r.clicks,1);assert.deepEqual(r.events.map(e=>e.action),[0,2,1]);checks++;
 r=run({after:3});assert.equal(r.clicks,1);assert.deepEqual(r.events.filter(e=>e.action===0).map(e=>e.at),[1000,1200,1400]);checks++;
 r=run({after:1000});assert.equal(r.clicks,0);assert.equal(r.events.filter(e=>e.action===0).length,50);assert(r.logs.at(-1).endsWith('request_expired'));checks++;
 r=run({after:0});assert.equal(r.clicks,1);assert.equal(r.events.length,0);checks++;
 r=run({inactive:true});assert.equal(r.events.length,0);assert(r.logs.at(-1).endsWith('navigation_inactive'));checks++;
 r=run({leave:true});assert.deepEqual(r.events.map(e=>e.action),[0,3]);assert(r.logs.at(-1).endsWith('left_card'));checks++;
 r=run({notClickable:true});assert.equal(r.clicks,1);assert(r.logs.some(x=>x.includes('clickable=false listener=true blocked=none')));checks++;
 for(const [opts,reason] of [[{disabled:true},'disabled'],[{noListener:true},'listener_missing'],[{rejectClick:true},'none']]){
  r=run(opts);assert.equal(r.events.filter(e=>e.action===0).length,1);assert(r.logs.some(x=>x.includes('blocked='+reason+' dragStopped=true')));checks++;
 }
 for(const [opts,reason] of [[{noId:true},'resource_id_missing'],[{missing:true},'view_missing']]){
  r=run(opts);assert(r.logs.some(x=>x.includes('found=false blocked='+reason)));assert.equal(r.clicks,0);checks++;
 }
}
console.log(checks+' bounded stock-gesture loop checks passed');
