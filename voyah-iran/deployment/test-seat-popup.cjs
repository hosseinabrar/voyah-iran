const fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('assert');
const source=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/abrar/voyahiran/core/bridge/OemSeatPopup.kt'),'utf8');
const script=source.split('internal val SCRIPT = """')[1].split('""".trimIndent()')[0].replaceAll("${'$'}",'$');
function run(existing=false){
 let showing=existing,closed=0,timer=null;const logs=[];
 const show={implementation:null,call:()=>{showing=true;}},dismiss={implementation:null,call:()=>{showing=false;}};
 const dialog={isShowing:()=>showing,closePage:()=>{closed++;if(dismiss.implementation)dismiss.implementation();else dismiss.call();},$dispose:()=>{}};
 const type={getInstance:()=>dialog,show:{overload:()=>show},dismiss:{overload:()=>dismiss}};
 vm.runInNewContext(script,{console:{log:x=>logs.push(x)},setTimeout:(f,ms)=>{assert.equal(ms,3000);timer=f;},Java:{retain:x=>x,perform:f=>f(),scheduleOnMainThread:f=>f(),use:n=>{
  if(n==='android.app.ActivityThread')return {currentApplication:()=>({getApplicationContext:()=>({})})};
  assert(n.endsWith('.SeatMirrorOtaDialog'));return type;
 }}});
 return {show,dismiss,dialog,logs,tick:()=>timer(),hasTimer:()=>timer!==null,closed:()=>closed,showing:()=>showing};
}
let r=run();assert(r.showing());assert.equal(r.closed(),0);r.tick();assert.equal(r.closed(),1);assert(!r.showing());assert.equal(r.show.implementation,null);assert.equal(r.dismiss.implementation,null);
r=run(true);assert(!r.hasTimer());assert(r.showing());
r=run();r.dialog.closePage();r.show.implementation(true,{});r.tick();assert(r.showing());assert.equal(r.closed(),1);
assert(!/saveSeat|sendSeat|stopRecoverSeat/.test(script));
console.log('PASS: 3000ms closePage, hook cleanup, pre-existing/manual-reopened window preserved, no seat operation replay');
