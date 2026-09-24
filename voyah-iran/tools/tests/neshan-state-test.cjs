const fs=require('fs'),vm=require('vm'),assert=require('assert'),path=require('path');
const src=fs.readFileSync(path.join(__dirname,'../../app/src/main/java/com/abrar/voyahiran/core/bridge/NeshanHookScript.kt'),'utf8').split('val source = """')[1].split('""".trimIndent()')[0];
let now=10000,active=true,engine={},readError=false,timer;const logs=[];
const update={call:()=>{}},reset={call:()=>{}};
const start={call:()=>{}},stop={call:()=>{}},destroy={call:()=>{}};
const method=fn=>({overload:()=>({call:fn})});
const fields={stepClass:'Step',updateClass:'Update',updateMethod:'u',resetClass:'Reset',resetMethod:'r',distance:'d',type:'t',modifier:'m',road:'road',roadExtra:'extra',turnIndex:'turn',routeState:{ownerClass:'Owner',instanceField:'N',stateClass:'State',activeMethod:'H',engineMethod:'A'}};
const types={'android.app.Instrumentation':{callActivityOnStart:{overload:()=>start},callActivityOnStop:{overload:()=>stop},callActivityOnDestroy:{overload:()=>destroy}},Step:Object.fromEntries(['d','t','m','road','extra','turn'].map(n=>[n,{overload:()=>{}}])),
Owner:{N:()=>{},class:{getDeclaredField:n=>{assert.equal(n,'N');return{setAccessible:()=>{},get:()=>{if(readError)throw Error('read failed');return {}}}}}},
State:{H:method(()=>active),A:method(()=>engine)},Update:{u:{overload:()=>update}},Reset:{r:{overload:()=>reset}}};
const ctx={NESHAN_PROFILE:{id:'test',hook:fields},Date:{now:()=>now},Java:{perform:f=>f(),use:n=>types[n],retain:x=>x,cast:x=>x},rpc:{exports:{}},console:{log:s=>logs.push(s)},setInterval:f=>{timer=f}};
vm.createContext(ctx);vm.runInContext(src,ctx);
assert(logs.some(s=>s.startsWith('VI_NESHAN_READY')));
const step={d:()=>100,t:()=>({name:()=> 'LEFT'}),m:()=>({name:()=> 'LEFT'}),road:()=> 'همت',extra:()=> '',turn:()=>({intValue:()=>7})};
const sample=()=>update.implementation(0,10,{size:()=>1,get:()=>step});
const tick=()=>{ctx.rpc.exports.onFridaStdin('ping');timer()};
sample();tick();assert(logs.at(-1).startsWith('VI_NESHAN_DATA'));
now+=36000000;tick();assert(logs.at(-1).includes('"routeActive":true'));
active=false;tick();assert.equal(logs.at(-1),'VI_NESHAN_CLEAR');
active=true;sample();readError=true;tick();assert(logs.at(-1).includes('"routeActive":false'));
now+=6000;tick();assert.equal(logs.at(-1),'VI_NESHAN_CLEAR');assert(logs.some(s=>s.startsWith('VI_NESHAN_STATE_ERROR')));
readError=false;sample();tick();assert(logs.at(-1).startsWith('VI_NESHAN_DATA'));
engine=null;tick();assert.equal(logs.at(-1),'VI_NESHAN_CLEAR');
console.log('PASS: obfuscated field collision, guidance, 10h stationary lease, explicit exit, fresh fallback, stale expiry, diagnostic, recovery, engine removal');

engine={};sample();tick();
const activity=(finishing,config=false,root=true)=>({isTaskRoot:()=>root,isFinishing:()=>finishing,isChangingConfigurations:()=>config});
stop.implementation(activity(false));now+=36000000;tick();assert(logs.at(-1).startsWith('VI_NESHAN_DATA'));
destroy.implementation(activity(false,true));tick();assert(logs.at(-1).startsWith('VI_NESHAN_DATA'));
destroy.implementation(activity(true,false,false));tick();assert(logs.at(-1).startsWith('VI_NESHAN_DATA'));
stop.implementation(activity(true));const atExit=logs.length;sample();tick();assert(!logs.slice(atExit).some(s=>s.startsWith('VI_NESHAN_DATA')));
start.implementation(activity(false));tick();assert(!logs.slice(atExit).some(s=>s.startsWith('VI_NESHAN_DATA')));
sample();tick();assert(logs.at(-1).startsWith('VI_NESHAN_DATA'));
console.log('PASS: background, configuration recreation, child activity close, explicit root exit, late callback suppression and reopen with fresh data');
