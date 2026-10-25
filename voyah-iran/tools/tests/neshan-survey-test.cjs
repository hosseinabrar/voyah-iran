const fs=require('fs'),vm=require('vm'),assert=require('assert'),path=require('path');
const source=fs.readFileSync(path.join(__dirname,'../../app/src/main/java/com/abrar/voyahiran/core/bridge/NeshanSurveyScript.kt'),'utf8').split('val source = """')[1].split('""".trimIndent()')[0].replaceAll("${'$'}",'$');
const catalog=JSON.parse(fs.readFileSync(path.join(__dirname,'../../../voyah-iran-subscription-server/configs/neshan.source.json')));
function harness(config=catalog.profiles[0].surveySuppression) {
 const queue=[],show={call:()=>{}},logs=[];
 function map(){const m=new Map();return {put:(k,v)=>m.set(k,v),remove:k=>m.delete(k),containsKey:k=>m.has(k),clear:()=>m.clear(),keySet:()=>({iterator:()=>iterator([...m.keys()])}),entrySet:()=>({iterator:()=>iterator([...m].map(([k,v])=>({getKey:()=>k,getValue:()=>v})))})};}
 function iterator(a){let i=0;return {hasNext:()=>i<a.length,next:()=>a[i++]};}
 const types={'android.app.Dialog':{show:{overload:()=>show}},'java.util.WeakHashMap':{$new:map},'java.lang.Boolean':{TRUE:{value:true}}};
 const ctx={profile:{surveySuppression:config},enabled:true,heartbeat:1000,Date:{now:()=>1000},console:{log:x=>logs.push(x)},Java:{use:n=>types[n]||{},cast:x=>x,scheduleOnMainThread:f=>queue.push(f),registerClass:s=>({$new:()=>({...s.methods})})}};
 vm.createContext(ctx);vm.runInContext(source+'\ninstallSurveyDismissal();',ctx);
 function dialog(attached=false,displayName='VoyahIran.MapPreview',matching=true){
  let visible=true,count=0;const listeners=new Set();
  const root={getResources:()=>({getIdentifier:(name,type,pkg)=>{assert.equal(pkg,config.resourcePackage);return config.requiredViewIds.includes(name)?1:0}}),findViewById:()=>matching?{}:null,isAttachedToWindow:()=>attached,getDisplay:()=>attached?{getName:()=>displayName,getDisplayId:()=>9}:null,addOnAttachStateChangeListener:l=>listeners.add(l),removeOnAttachStateChangeListener:l=>listeners.delete(l)};
  const d={getWindow:()=>({getDecorView:()=>root}),isShowing:()=>visible,dismiss:()=>{visible=false;count++}};
  return {d,listeners,count:()=>count,attach:()=>{attached=true;for(const l of [...listeners])l.onViewAttachedToWindow(root)},moveToCard:()=>{displayName='VoyahIran.MapPreview'}};
 }
 return {ctx,show,dialog,flush:()=>{while(queue.length)queue.shift()()},logs};
}
for(const p of catalog.profiles){
 const h=harness(p.surveySuppression),d=h.dialog();h.show.implementation.call(d.d);
 assert.equal(d.count(),0);d.attach();assert.equal(d.count(),0);h.flush();assert.equal(d.count(),1);assert.equal(d.listeners.size,0);
 const full=h.dialog(false,'Built-in Screen');h.show.implementation.call(full.d);full.attach();h.flush();assert.equal(full.count(),1);
 full.moveToCard();vm.runInContext('dismissCardSurveys()',h.ctx);assert.equal(full.count(),1);
 const other=h.dialog(true,'VoyahIran.MapPreview',false);h.show.implementation.call(other.d);assert.equal(other.count(),0);
 const ready=h.dialog(true);h.show.implementation.call(ready.d);assert.equal(ready.count(),1);
 const pending=h.dialog();h.show.implementation.call(pending.d);vm.runInContext('restoreSurvey()',h.ctx);assert.equal(pending.listeners.size,0);assert.equal(h.show.implementation,null);
}
assert.equal(harness(null).show.implementation,undefined);
for(const state of ['enabled=false','heartbeat=-20000']) {
 const h=harness(),d=h.dialog();h.show.implementation.call(d.d);
 vm.runInContext(state,h.ctx);d.attach();h.flush();assert.equal(d.count(),0);
}
console.log('PASS: all profiles, delayed attachment, immediate attachment, fullscreen dismissal, no duplicate dismissal, unrelated dialogs, cleanup, missing config');
