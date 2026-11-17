import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
const {batteryPolicy,batteryArtworkState,chargingPresentation}=await import('data:text/javascript;base64,'+Buffer.from((await readFile(new URL('../battery.js',import.meta.url),'utf8')).replace(/^import .*motion.js';\n/, '')).toString('base64'));
const now=100000;
const values={charging:false,chargeComplete:false,chargeConnected:false,chargeLimit:80,chargeScheduled:false,chargeLimitControl:true,chargeScheduleControl:true};
const make=(extra={},age=0)=>({observedAt:now,values:{...values,...extra},fieldTimes:Object.fromEntries(Object.keys(values).map(k=>[k,now-age]))});
assert.deepEqual(batteryPolicy(make(),now),{limit:'',schedule:''});
assert(batteryPolicy(make({charging:true}),now).limit);
assert(batteryPolicy(make({chargeConnected:true}),now).schedule);
assert.equal(batteryPolicy(make({chargeConnected:true}),now).limit,'');
assert(batteryPolicy(make({},30001),now).limit);
assert(batteryPolicy(make({chargeLimitControl:false}),now).limit);
assert(batteryPolicy(make({chargeScheduled:undefined}),now).schedule);
assert(batteryPolicy(make(),now,true).limit);
console.log('PASS battery policy: charging, connected, stale, unknown, unsupported, pending');

for(const [percent,cells] of [[0,0],[1,1],[25,1],[26,2],[50,2],[51,3],[75,3],[76,4],[100,4]])assert.equal(batteryArtworkState(percent,false).cells,cells);
assert.equal(batteryArtworkState(25,false).color,'red');
assert.equal(batteryArtworkState(25,true).color,'green');
assert.equal(batteryArtworkState(68,false).color,'blue');
assert.equal(batteryArtworkState(undefined,false).known,false);
console.log('PASS battery PNG cell thresholds and charging priority');

assert.equal(chargingPresentation({chargeConnected:false,chargeScheduled:true}).label,'شارژر وصل نیست');
assert.equal(chargingPresentation({chargeConnected:true,chargeScheduled:true,chargeNextStartAt:180000},60000).eta,'0 ساعت و 2 دقیقه تا شروع شارژ');
assert.equal(chargingPresentation({chargeConnected:true,charging:true}).charging,true);
assert.equal(chargingPresentation({chargeConnected:true,chargeComplete:true}).charging,false);
assert.equal(chargingPresentation({chargeConnected:true,chargeComplete:true}).color,'green');
console.log('PASS four charging states and absolute-time countdown');

assert(batteryPolicy(make({chargeComplete:true}),now).limit);
assert(batteryPolicy(make({chargeComplete:undefined}),now).limit);
