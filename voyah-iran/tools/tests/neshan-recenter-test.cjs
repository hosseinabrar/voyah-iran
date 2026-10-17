const fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('assert');
const base=path.resolve(__dirname,'../../..');
const profiles=JSON.parse(fs.readFileSync(path.join(base,'voyah-iran-subscription-server/configs/neshan.source.json'))).profiles;
const source=fs.readFileSync(path.join(base,'voyah-iran/app/src/main/java/com/abrar/voyahiran/core/bridge/NeshanRecenterScript.kt'),'utf8').split('"""')[1].replaceAll("${'$'}",'$');
let checks=0;
for(const profile of profiles){
 function run(o={}){
  let now=1000,clicked=0,held=0,freed=0,buttonShown=!!o.button,events=[];const queue=[],logs=[];
  const display={getDisplayId:()=>o.display??7,getName:()=> 'VoyahIran.MapPreview'};
  const button={isAttachedToWindow:()=>true,isShown:()=>buttonShown,isEnabled:()=>true,isClickable:()=>true,performClick:()=>{clicked++;return true;}};
  const common={getDisplay:()=>display,isShown:()=>true,isAttachedToWindow:()=>true,getWidth:()=>871,getHeight:()=>654,getContext:()=>({})};
  const map={...common,isMap:true,dispatchTouchEvent:e=>{events.push(e.action);if(e.action===1&&!o.noButton)buttonShown=true;return !o.rejectTouch;}};
  const root={...common,isGroup:true,getChildCount:()=>1,getChildAt:()=>map,getWindowVisibility:()=>0,isLayoutRequested:()=>!!o.layout,
   getResources:()=>({getIdentifier:()=>1}),findViewById:()=>button};
  const rs=profile.hook.routeState,route={[rs.activeMethod]:()=>!o.inactive,[rs.engineMethod]:()=>({})};
  const list={size:()=>1,get:()=>root};const wm={getClass:()=>({getDeclaredField:()=>({setAccessible(){},get:()=>list})})};
  const obtain={call:(self,down,time,action)=>({action,setSource(){},recycle(){}})};
  const types={
   [rs.ownerClass]:{[rs.instanceField]:{value:route}},'android.view.WindowManagerGlobal':{getInstance:()=>wm},
   'android.view.ViewGroup':{class:{isInstance:v=>!!v.isGroup}},'com.carto.ui.MapView':{class:{isInstance:v=>!!v.isMap}},
   'android.view.MotionEvent':{obtain:{overload:()=>obtain}},'android.os.SystemClock':{uptimeMillis:()=>now},
   'android.view.ViewConfiguration':{get:()=>({getScaledTouchSlop:()=>8})}
  };
  const ctx={NESHAN_PROFILE:profile,enabled:true,heartbeat:now,Date:{now:()=>now},console:{log:x=>logs.push(x)},
   setTimeout:(f,ms)=>queue.push({f,ms}),Java:{perform:f=>f(),scheduleOnMainThread:f=>f(),cast:v=>v,use:n=>types[n]||{},
    retain:v=>{held++;return Object.assign(Object.create(v),{$dispose:()=>freed++});}}};
  vm.createContext(ctx);vm.runInContext(source,ctx);ctx.recenterCard(7,'token',2200);
  if(o.leave)o.display=0;
  while(queue.length){const t=queue.shift();now+=t.ms;t.f();assert(now<4000);}
  assert.equal(held,freed);const replies=logs.filter(x=>x.startsWith('VI_NESHAN_RECENTER_RESULT'));assert.equal(replies.length,1);
  return {clicked,events,result:replies[0]};
 }
 let r=run();assert.deepEqual(r.events,[0,2,2,1]);assert.equal(r.clicked,1);assert(r.result.endsWith('applied'));checks++;
 r=run({button:true});assert.equal(r.clicked,1);assert.deepEqual(r.events,[]);checks++;
 for(const [opts,reason] of [[{display:0},'map_view_count:0'],[{inactive:true},'navigation_inactive'],[{layout:true},'map_root_layout_pending'],[{noButton:true},'request_expired'],[{rejectTouch:true},'map_touch_rejected'],[{leave:true},'left_card']]){
  r=run(opts);assert.equal(r.clicked,0);assert(r.result.includes(reason),r.result);checks++;
  if(opts.leave||opts.rejectTouch)assert.equal(r.events.at(-1),3);
 }
}
console.log(checks+' map-pan / stock-follow checks passed');
