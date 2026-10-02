const fs=require('fs'),vm=require('vm'),assert=require('assert'),path=require('path');
const source=fs.readFileSync(path.join(__dirname,'../../src/VoyahIran.Installer.Core/can-tap.js'),'utf8');
assert(!/Java\s*\./.test(source),'native trace must never initialize Java bridge');
function setup(arch='arm', available=['libqg_canbus_jni.so','libpateouevent.so','libqg_runtime.so']) {
 let tick,now=1000;const logs=[],files=new Map(),hooks={},handles=new Map();let fd=10;
 const pointer=n=>({value:n,compare:o=>n-o.value,add:m=>pointer(n+m)});
 const buffer=(data=Buffer.alloc(65536))=>({data,readByteArray:n=>data.subarray(0,n),readUtf8String:n=>data.toString('utf8',0,n)});
 const native={access:(p)=>files.has(p.text)?0:-1,unlink:p=>(files.delete(p.text),0),open:p=>{if(!files.has(p.text))return -1;handles.set(++fd,p.text);return fd;},read:(f,p,n)=>{const b=Buffer.from(files.get(handles.get(f)));b.copy(p.data);return Math.min(b.length,n);},close:f=>(handles.delete(f),0)};
 const libc={getExportByName:n=>({name:n})};
 const modules=Object.fromEntries(['libqg_canbus_jni.so','libpateouevent.so','libqg_runtime.so'].map((name,i)=>[name,{base:pointer(100+i*200),size:100,path:'/system/lib/'+name}]));
 vm.runInNewContext(source,{VI_DIR:'/capture',Date:{now:()=>now},console:{log:s=>logs.push(s)},setInterval:f=>(tick=f,1),clearInterval:()=>{},
 Process:{arch,pointerSize:arch==='arm'?4:8,getModuleByName:n=>{if(n==='libc.so')return libc;if(available.includes(n))return modules[n];throw Error('module absent');}},
 Memory:{allocUtf8String:s=>({text:s}),alloc:n=>buffer(Buffer.alloc(n))},
 NativeFunction:function(p){return native[p.name];},
 Interceptor:{attach:(p,callbacks)=>{hooks[p.name]=callbacks;return{detach:()=>delete hooks[p.name]};}}
 });
 return {logs,files,hooks,advance:n=>now+=n,tick:()=>tick(),rows:()=>logs.filter(s=>s.startsWith('VI_CAN_BATCH ')).flatMap(s=>JSON.parse(s.slice(13))),
 call:(op,bytes,count,caller=150)=>{const data=buffer(Buffer.from(bytes));const context={returnAddress:pointer(caller)};hooks[op].onEnter.call(context,[{toInt32:()=>3},data,{toUInt32:()=>bytes.length}]);hooks[op].onLeave.call(context,{toInt32:()=>count});return data.data;}};
}
for(const arch of ['arm','arm64']) {
 let s=setup(arch);assert(s.logs.includes('VI_CAN_READY'));
 assert.deepEqual(s.call('recv',[0x94,8,0x60,0x90],4),Buffer.from([0x94,8,0x60,0x90]));
 s.call('read',[1],1,250);s.call('recv',[1],-1);s.call('write',[4,5,6],2);
 s.files.set('/capture/marks','a'.repeat(32)+'|1234\n'+'b'.repeat(32)+'|1235\n');s.tick();
 assert.equal(s.rows().filter(r=>r.kind==='RX_TRANSPORT').length,1);
 assert.equal(s.rows().find(r=>r.kind==='RX_TRANSPORT').hex,'94086090');
 assert.equal(s.rows().find(r=>r.kind==='TX_TRANSPORT').hex,'0405');
 assert.equal(s.rows().filter(r=>r.kind==='AUTOPARK_MANUAL_MARK').length,2);
 s.tick();assert.equal(s.rows().filter(r=>r.kind==='AUTOPARK_MANUAL_MARK').length,2);
 s.files.set('/capture/stop','');s.tick();assert.equal(Object.keys(s.hooks).length,0);assert(s.rows().some(r=>r.kind==='STOP_DETACHED'));
 s=setup(arch);s.advance(31000);s.tick();assert.equal(Object.keys(s.hooks).length,0);assert(s.rows().some(r=>r.kind==='WATCHDOG_DETACHED'));
 s=setup(arch);for(let i=0;i<3000;i++)s.call('recv',[0],1);s.tick();assert(s.rows().some(r=>r.kind==='DROPPED'));
 s=setup(arch);s.call('recv',new Array(9000).fill(1),9000);s.tick();assert(s.rows().find(r=>r.kind==='RX_TRANSPORT').truncated);
 // Regression: UEvent packets do not originate in libqg_canbus_jni.
 s=setup(arch);s.call('recvfrom',[0x94,8,0x60,0x90,0,0xff],6,350);
 s.call('recvfrom',[0x99],1,550);s.call('recvfrom',[0x12],1,750);s.tick();
 const incoming=s.rows().filter(r=>r.kind==='RX_TRANSPORT');
 assert.equal(incoming.length,2);assert.equal(incoming[0].hex,'9408609000ff');
 assert(incoming[0].source_module.endsWith('/libpateouevent.so'));
 assert(incoming[1].source_module.endsWith('/libqg_runtime.so'));
 s=setup(arch,['libqg_canbus_jni.so']);assert(s.logs.includes('VI_CAN_READY'));
 s=setup(arch,['libpateouevent.so']);assert(s.logs.includes('VI_CAN_READY'));
 s=setup(arch,[]);assert(s.logs.some(l=>l.startsWith('VI_CAN_FAILED')));assert(!s.logs.includes('VI_CAN_READY'));
}
console.log('PASS ARM32/64: CAN JNI + Pateo UEvent + QG runtime, missing modules, unrelated traffic excluded, no Java, RX/TX bytes, failed syscall, markers, stop, watchdog, bounded queue/truncation');
