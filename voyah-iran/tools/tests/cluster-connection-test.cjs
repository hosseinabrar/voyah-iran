// Execute the injected script's registration and connection lifecycle, without Android/build.
const fs=require('fs'),vm=require('vm'),assert=require('node:assert/strict'),path=require('path');
const raw=fs.readFileSync(path.join(__dirname,'../../app/src/main/java/com/abrar/voyahiran/core/bridge/ClusterMusicScript.kt'),'utf8');
const source=raw.split('"""')[1].replaceAll("${'$'}",'$');
let clock=1000,bindResult=true,bindError=false,sends=0,sendError=false,binds=0,unbounds=0,closes=0;
const logs=[],registrations=[],readers=[];
const obj=()=>({equals(other){return other===this;},$dispose(){}});
const hook=()=>({overload:()=>({call(){}})});
const app={...obj(),bindService(){binds++;if(bindError)throw Error('denied');return bindResult;},unbindService(){unbounds++;}};
const classes={};
function use(name){
 if(name==='android.os.SystemClock')return {elapsedRealtime:()=>clock};
 if(name==='android.os.Looper')return {getMainLooper:()=>({})};
 if(name==='android.media.ImageReader')return {newInstance(){const r={...obj(),setOnImageAvailableListener(){},close(){closes++;},acquireLatestImage(){return null;}};readers.push(r);return r;}};
 if(name==='android.content.Intent')return {$new:()=>({...obj(),setClassName(){}})};
 if(name==='android.graphics.Bitmap')return {createBitmap:()=>({...obj(),recycle(){},copyPixelsFromBuffer(){}})};
 if(name==='android.graphics.Bitmap$Config')return {ARGB_8888:{value:1}};
 if(name==='java.nio.ByteBuffer')return {allocateDirect:()=>obj()};
 return classes[name]??={...obj(),name,$new:obj,setVisibility:hook(),onThemeChanged:hook(),onCreate:hook()};
}
const sandbox={console:{log:s=>logs.push(s)},Date:{now:()=>clock},rpc:{},setInterval(){},
 Java:{use,retain:x=>x,perform:f=>f(),scheduleOnMainThread:f=>f(),registerClass(spec){
  if(spec.implements?.some(x=>x.name==='android.content.ServiceConnection')){
   for(const method of ['onServiceConnected','onServiceDisconnected','onNullBinding','onBindingDied'])
    assert.equal(typeof spec.methods[method],'function','Missing ServiceConnection implementation: '+method);
  }
  registrations.push(spec);
  return {$new:()=>({...obj(),...spec.methods})};
 }},app};
vm.createContext(sandbox);vm.runInContext(source,sandbox);
const run=s=>vm.runInContext(s,sandbox);
assert(logs.includes('VI_CLUSTER_MUSIC_READY'),'Full initialization must reach READY');
assert(registrations.some(x=>x.name.includes('ClusterImageConnection')));
sandbox.send=()=>{sends++;if(sendError)throw Error('transaction rejected');};
run('sendSurface=send;mounted={panel:{getContext:()=>({getApplicationContext:()=>app})},image:{postInvalidateOnAnimation(){}}};');
function fresh(session='one'){sandbox.session=session;run('packet={owned:true,session:session};received=Date.now();');}
function advance(ms){clock+=ms;run('received=Date.now()');}
function ensure(){run('ensureFeed()');return run('feed');}
fresh();let first=ensure();assert.equal(binds,1);
first.connection.onServiceConnected(null,{});assert.equal(sends,1);assert(first.connected);
assert.equal(ensure(),first,'Heartbeat must not rebind a live connection');
first.connection.onServiceDisconnected(null);assert.equal(run('feed'),null);assert.equal(unbounds,1);assert.equal(closes,1);
assert(run('mounted')!==null,'Disconnect must not remove the final image');
ensure();assert.equal(binds,1,'Retry has a cooldown');advance(500);let second=ensure();assert.equal(binds,2);
first.connection.onNullBinding(null);first.connection.onBindingDied(null);first.connection.onServiceConnected(null,{});
assert.equal(run('feed'),second,'Late callbacks from the previous binding cannot close or replace a new feed');assert.equal(sends,1);
second.connection.onNullBinding(null);assert.equal(run('feed'),null);assert.equal(run('retryAt'),clock+1000);
advance(1000);let third=ensure();third.connection.onBindingDied(null);assert.equal(run('retryAt'),clock+2000);
advance(2000);let fourth=ensure();sendError=true;fourth.connection.onServiceConnected(null,{});sendError=false;
assert.equal(run('feed'),null);assert(logs.some(x=>x.includes('reason=surface-send')));
advance(4000);bindResult=false;ensure();bindResult=true;assert.equal(run('feed'),null);assert.equal(run('retryAt'),clock+5000);
advance(5000);bindError=true;ensure();bindError=false;assert.equal(run('feed'),null);assert.equal(run('retryAt'),clock+5000);
advance(5000);let pending=ensure();advance(3000);ensure();assert.equal(run('feed'),null);assert(logs.some(x=>x.includes('reason=bind-timeout')));
// A new session starts immediately, rather than inheriting an old failure cooldown.
fresh('two');let connected=ensure();connected.connection.onServiceConnected(null,{});
advance(5000);ensure();assert.equal(run('feed'),null);assert(logs.some(x=>x.includes('reason=first-frame-timeout')));
fresh('three');let successful=ensure();successful.connection.onServiceConnected(null,{});
let imageClosed=0;
successful.reader.acquireLatestImage=()=>({getPlanes:()=>[{getRowStride:()=>7680,getPixelStride:()=>4,
 getBuffer:()=>({rewind(){},remaining:()=>7680*720})}],close(){imageClosed++;}});
sandbox.reader=successful.reader;run('receive(reader)');assert.equal(imageClosed,1);assert(successful.received);assert.equal(run('feedFailures'),0);
advance(10000);assert.equal(ensure(),successful,'Static map does not require continuous new frames after the first');
// Shutdown/stale session callbacks cannot send surfaces or schedule a new binding.
fresh('four');const oldSends=sends;successful.connection.onServiceConnected(null,{});assert.equal(sends,oldSends);
let last=ensure();run('packet.owned=false');last.connection.onBindingDied(null);assert.equal(run('feed'),last);
run('mounted=null;restore()');assert.equal(run('feed'),null);const oldBinds=binds;ensure();assert.equal(binds,oldBinds);
assert.equal(closes,readers.length,'Every created reader is closed exactly once');
// Regression proof: removing either of Android's default callbacks reproduces
// initialization failure in the strict registration mock, instead of passing silently.
for(const name of ['onNullBinding','onBindingDied']){
 const before=logs.length;
 const broken=source.replace(new RegExp(name+':function\\(name\\)\\{[^}]*\\}'),name+':undefined');
 vm.runInNewContext(broken,{...sandbox,rpc:{}});
 const emitted=logs.slice(before);
 assert(!emitted.includes('VI_CLUSTER_MUSIC_READY'));
 assert(emitted.some(x=>x.includes('Missing ServiceConnection implementation: '+name)));
}
console.log('PASS: complete ServiceConnection registration; 4 callbacks, stale callbacks, retry/backoff, timeout, failed bind/send, first frame and shutdown');
