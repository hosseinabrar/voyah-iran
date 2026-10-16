const fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('assert');
const root=path.resolve(__dirname,'../../..');
const profiles=JSON.parse(fs.readFileSync(path.join(root,'voyah-iran-subscription-server/configs/neshan.source.json'))).profiles;
const source=fs.readFileSync(path.join(root,'voyah-iran/app/src/main/java/com/abrar/voyahiran/core/bridge/NeshanRecenterScript.kt'),'utf8').split('"""')[1].replaceAll("${'$'}",'$');
let checks=0;
for(const profile of profiles) {
  function run(options={}) {
    const cfg=profile.cardRecenter,rs=profile.hook.routeState;
    let applied=0,events=[];
    const current={destination:'unchanged'};
    const model={
      [cfg.stateMethod]:()=>({g:()=>current}),
      [cfg.applyMethod]:state=>{assert.strictEqual(state.destination,'unchanged');events.push('apply');applied++;}
    };
    const listener={[cfg.fragmentField]:{value:{[cfg.modelField]:{value:model}}}};
    const field=value=>({setAccessible(){},get:()=>value});
    const info={getClass:()=>({getDeclaredField:()=>field(listener)})};
    const button={isAttachedToWindow:()=>true};
    const display={getDisplayId:()=>options.display??3,getName:()=>options.name??'VoyahIran.MapPreview'};
    const view={getDisplay:()=>display,isShown:()=>true,getWidth:()=>871,getHeight:()=>660,
      isLayoutRequested:()=>!!options.layout,getResources:()=>({getIdentifier:()=>1}),findViewById:()=>button};
    const views={size:()=>1,get:()=>view};
    const windowManager={getClass:()=>({getDeclaredField:()=>field(views)})};
    const state={[rs.activeMethod]:()=>options.active!==false,[rs.engineMethod]:()=>({})};
    const types={
      [rs.ownerClass]:{[rs.instanceField]:{value:state}},
      'android.view.WindowManagerGlobal':{getInstance:()=>windowManager},
      'android.view.View':{class:{getDeclaredField:()=>field(info)}},
      [cfg.listenerClass]:{class:{isInstance:()=>options.listener!==false}},
      [cfg.followClass]:{$new:()=>({follow:true})},
      [cfg.builderClass]:{$new:value=>{
        assert.strictEqual(value,current);
        return {e:follow=>{assert(follow.follow);events.push('follow')},b:()=>current};
      }}
    };
    const context={NESHAN_PROFILE:profile,enabled:options.enabled!==false,heartbeat:Date.now(),Date,
      console:{log(){}},Java:{scheduleOnMainThread:f=>f(),cast:v=>v,use:n=>types[n]||{}}};
    vm.createContext(context);vm.runInContext(source,context);context.recenterCard(3);
    return {applied,events};
  }
  assert.deepStrictEqual(run(),{applied:1,events:['follow','apply']});checks++;
  for(const options of [{active:false},{display:0},{display:1},{name:'Another display'},{layout:true},{listener:false},{enabled:false}]) {
    assert.strictEqual(run(options).applied,0);checks++;
  }
}
console.log(checks+' recenter checks passed');
