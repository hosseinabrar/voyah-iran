const {readFileSync}=require('node:fs'),{resolve}=require('node:path'),vm=require('node:vm'),assert=require('node:assert/strict');
const source=readFileSync(resolve(__dirname,'../../app/src/main/java/com/abrar/voyahiran/core/bridge/ClusterMusicLegacyScript.kt'),'utf8').split('"""')[1].replaceAll("${'$'}",'$');
const enums={PLAY:{equals(v){return v===this}},PAUSE:{},CLEAN:{}};
function info(){const o={$dispose(){}};for(const k of ['SrcType','MediaType','MediaState','Frequency','SongName','Artist','AlbumName']){o['get'+k]=()=>o[k];o['set'+k]=v=>o[k]=v;}return o;}
let now=10000,sends=[],intervals=[];
const factory=info();factory.setSrcType('radio');factory.setSongName('FM');factory.setMediaState(enums.PAUSE);
const component={mLastMediaSrcInfo:{value:factory},equals:o=>o===component};
const hook={call:(self,data)=>sends.push({...data})};
const klass={$className:'legacy',updateMediaSrcInfo:{overload:()=>hook}};
const types={'com.qinggan.canbus.MediaSrcInfo':{$new:info},'com.qinggan.canbus.MediaSrcType':{NetworkMusic:{value:'network'}},
 'com.qinggan.canbus.MediaState':Object.fromEntries(Object.entries(enums).map(([k,v])=>[k,{value:v}])),
 'com.qinggan.canbus.MediaType':{SimpleAudio:{value:'audio'}},'android.os.SystemClock':{elapsedRealtime:()=>now},
 'com.qinggan.canbus.service.protocol.dongfeng.InstrumentInteractionComponent':klass};
const sandbox={Java:{use:n=>types[n],retain:v=>v,perform:f=>f(),scheduleOnMainThread:f=>f(),choose(n,c){c.onMatch(component);c.onComplete()}},
 rpc:{exports:{}},console,Date:{now:()=>now},setInterval:f=>intervals.push(f)};
vm.runInNewContext(source,sandbox);
const frame={package:'user.player',title:'Tamasha',artist:'Shadmehr',album:'Album',playing:true,playbackAt:now};
const send=d=>sandbox.rpc.exports.onFridaStdin(JSON.stringify(d));
send(frame);assert.equal(sends.at(-1).SongName,'Tamasha');assert.equal(sends.at(-1).MediaState,enums.PLAY);
const n=sends.length;now+=1000;send(frame);assert.equal(sends.length,n,'lease refresh must not resend unchanged metadata');
send({...frame,playing:false});assert.equal(sends.at(-1).MediaState,enums.PAUSE);
sandbox.rpc.exports.onFridaStdin('clear');assert.equal(sends.at(-1).SongName,'FM');
send(frame);factory.setMediaState(enums.PLAY);hook.implementation.call(component,factory);
assert.equal(sends.at(-1).SongName,'FM');const count=sends.length;send(frame);assert.equal(sends.length,count,'stale user session must not override resumed OEM audio');
now+=1000;send({...frame,playbackAt:now});assert.equal(sends.at(-1).SongName,'Tamasha');
now+=6000;intervals[0]();assert.equal(sends.at(-1).SongName,'FM');
console.log('Legacy cluster: native metadata, duplicate suppression, pause, restoration, OEM priority and expiry passed');
