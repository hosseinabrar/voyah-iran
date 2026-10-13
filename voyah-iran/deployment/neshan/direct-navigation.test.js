'use strict';
const fs=require('fs'),vm=require('vm'),assert=require('assert');
const path=require('path');
const source=fs.readFileSync(path.join(__dirname,'direct-navigation.js'),'utf8').replace(/^\uFEFF/,'');
const embedded=fs.readFileSync(path.join(__dirname,'../../app/src/main/java/com/abrar/voyahiran/core/bridge/NeshanHookScript.kt'),'utf8');
assert(embedded.split("${'$'}").join('$').replace(/\r/g, '').includes(source.replace(/\r/g, '').trim()), 'embedded script must match deployed source');
let now=100000, timer, calls=0, routeCalls=0, throws=false;
const output=[];
const update={implementation:null,call(){calls++;if(throws)throw Error('original error');return 42;}};
const reset={implementation:null,call(){routeCalls++;return 9;}};
const stepType={};for(const n of ['a','f','g','h','i','j'])stepType[n]={overload(){return {};}};
const profile=JSON.parse(fs.readFileSync(path.join(__dirname,'../../../voyah-iran-subscription-server/configs/neshan.source.json'),'utf8')).profiles[0];
delete profile.adSuppression; // UI hooks have a separate Android-view harness.
const liveProfile=JSON.parse(JSON.stringify(profile));
delete profile.hook.routeState; // Legacy profiles retain bounded source-age fallback.
const context={NESHAN_PROFILE:profile,Date:{now:()=>now},console:{log:x=>output.push(x)},rpc:{exports:{}},setInterval:(f,ms)=>{assert.equal(ms,200);timer=f;},
 Java:{perform:f=>f(),cast:x=>x,use:n=> {
 if(n==='l10.b')return stepType;
 if(n==='o10.i')return {b:{overload:()=>update}};
 if(n==='m10.b')return {b:{overload:()=>reset}};
 throw Error('unknown class');
 }}};
vm.runInNewContext(source,context);
assert(output.some(x=>x.startsWith('VI_NESHAN_READY')));
let turnIndex = null;
const step={j:()=>turnIndex===null?null:{intValue:()=>turnIndex},a:()=>1000,h:()=> 'علامه',i:()=> 'طباطبایی جنوبی',f:()=>({name:()=> 'TURN'}),g:()=>({name:()=> 'LEFT'})};
const list={size:()=>1,get:()=>step};
// Arrival often has no street label and no direction. Keep its real distance.
const arrival={...step,h:()=>'',i:()=>null,f:()=>({name:()=> 'ARRIVE'}),g:()=>null};
const arrivalList={size:()=>1,get:()=>arrival};
assert.equal(update.implementation.call({},0,770,arrivalList),42);
timer(); let arrivalData=JSON.parse(output.at(-1).slice(15));
assert.equal(arrivalData.type,'ARRIVE'); assert.equal(arrivalData.road,'');
assert.equal(arrivalData.modifier,'UNKNOWN'); assert.equal(arrivalData.meters,230);
update.implementation.call({},0,1000,arrivalList);timer();
assert.equal(JSON.parse(output.at(-1).slice(15)).meters,0);
// Empty names are not a reason to discard ordinary turns either.
update.implementation.call({},0,770,{size:()=>1,get:()=>({...arrival,f:()=>({name:()=> 'TURN'})})});timer();
assert.equal(JSON.parse(output.at(-1).slice(15)).type,'TURN');
calls=0;
const partial={...arrival,a:()=>null,f:()=>null,g:()=>({name:()=> 'LEFT'})};
update.implementation.call({},0,770,{size:()=>1,get:()=>partial});timer();
let partialData=JSON.parse(output.at(-1).slice(15));
assert.equal(partialData.meters,null);assert.equal(partialData.modifier,'LEFT');assert.equal(partialData.road,'');
update.implementation.call({},0,770,{size:()=>1,get:()=>({...partial,a:()=>1000,g:()=>null})});timer();
partialData=JSON.parse(output.at(-1).slice(15));assert.equal(partialData.meters,230);assert.equal(partialData.modifier,'UNKNOWN');
update.implementation.call({},0,770,{size:()=>1,get:()=>({...partial,h:()=> 'Hemmat',g:()=>{throw Error('missing direction');}})});timer();
partialData=JSON.parse(output.at(-1).slice(15));assert.equal(partialData.road,'Hemmat');assert.equal(partialData.meters,null);
calls=0;
assert.equal(update.implementation.call({},0,910,list),42);assert.equal(calls,1);
timer();let data=JSON.parse(output.at(-1).slice('VI_NESHAN_DATA '.length));
assert.equal(data.road,'علامه طباطبایی جنوبی');
assert(/^[\x00-\x7f]*$/.test(output.at(-1)), 'wire output must be ASCII-only');
assert.equal(JSON.parse(Buffer.from(output.at(-1).slice(15),'ascii').toString('ascii')).road, data.road);
assert.equal(data.meters,90);assert.equal(data.modifier,'LEFT');assert.equal(data.ageMs,0);
assert.equal(data.turnIndex,null);
for (const n of [0,1,2,3,4,5,6,7,8]) {
 turnIndex=n; update.implementation.call({},0,910,list); timer();
 assert.equal(JSON.parse(output.at(-1).slice(15)).turnIndex,n);
}
turnIndex=null;
now+=4000;timer();data=JSON.parse(output.at(-1).slice('VI_NESHAN_DATA '.length));assert.equal(data.ageMs,4000);
now+=1001;timer();assert.equal(output.at(-1),'VI_NESHAN_CLEAR');
update.implementation.call({},0,1010,list);timer();assert.equal(JSON.parse(output.at(-1).slice(15)).meters,0);
assert.equal(reset.implementation.call({},list),9);assert.equal(routeCalls,1);timer();assert.equal(output.at(-1),'VI_NESHAN_CLEAR');
throws=true;assert.throws(()=>update.implementation.call({},0,0,list),/original error/);throws=false;
update.implementation.call({},-1,0,list);timer();
context.rpc.exports.onFridaStdin('disable');assert.equal(update.implementation,null);assert.equal(reset.implementation,null);
assert.equal(output.at(-1),'VI_NESHAN_DISABLED');
console.log('PASS: typed fields, exact distance, unchanged original results/errors, source-age expiry, route reset, invalid index, disable, embedded parity');
// Parent disappearance must restore the hooked methods too.
const second = {...context, rpc:{exports:{}}};
vm.runInNewContext(source,second);
assert.equal(typeof update.implementation,'function');
now+=15001; timer();
assert.equal(update.implementation,null);
assert.equal(reset.implementation,null);
console.log('PASS: parent-heartbeat watchdog restores hooks');

assert.equal(vm.runInNewContext(source + '\nJSON.parse(wireJson({road:"یاس 😀"})).road', {...context, rpc:{exports:{}}}), 'یاس 😀');
console.log('PASS: Persian and surrogate-pair Unicode survive ASCII-only wire');

// A stopped car is not a stopped route. Validate live state independently of source age.
let active=true, engine={}, stateFailure=false;
const liveState={H(){if(stateFailure)throw Error('state unavailable');return active;},A:()=>engine};
const owner={N:{value:liveState}};
const stateType={H:{overload(){}},A:{overload(){}}};
const oldUse=context.Java.use;
const liveContext={...context,NESHAN_PROFILE:liveProfile,rpc:{exports:{}},Java:{...context.Java,use:n=> {
 if(n==='org.rajman.neshan.core.BaseApplication')return owner;
 if(n==='ki0.c')return stateType;
 return oldUse(n);
}}};
vm.runInNewContext(source,liveContext);
update.implementation.call({},0,910,list);timer();
for(let i=0;i<3600;i++) {
 now+=10000;liveContext.rpc.exports.onFridaStdin('ping');timer();
 const d=JSON.parse(output.at(-1).slice(15));
 assert.equal(d.meters,90);assert.equal(d.ageMs,0);assert.equal(d.routeActive,true);
}
assert.equal(JSON.parse(output.at(-1).slice(15)).sourceAgeMs,36000000);
active=false;timer();assert.equal(output.at(-1),'VI_NESHAN_CLEAR');
active=true;const length=output.length;timer();assert.equal(output.length,length,'old route must not resurrect');
update.implementation.call({},0,900,list);timer();assert.equal(JSON.parse(output.at(-1).slice(15)).meters,100);
engine=null;timer();assert.equal(output.at(-1),'VI_NESHAN_CLEAR');
engine={};update.implementation.call({},0,900,list);timer();
stateFailure=true;timer();assert.equal(output.at(-1),'VI_NESHAN_CLEAR');stateFailure=false;
update.implementation.call({},0,900,list);timer();
owner.N.value=null;timer();assert.equal(output.at(-1),'VI_NESHAN_CLEAR');owner.N.value=liveState;
update.implementation.call({},0,900,list);timer();reset.implementation.call({},list);timer();assert.equal(output.at(-1),'VI_NESHAN_CLEAR');
update.implementation.call({},0,900,list);timer();now+=15001;timer();assert.equal(output.at(-1),'VI_NESHAN_DISABLED');
console.log('PASS: 10h stationary navigation, end route, null engine/singleton, read failure, reset, watchdog and no stale resurrection');
