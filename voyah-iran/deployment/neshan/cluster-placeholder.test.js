'use strict';
const fs=require('fs'),vm=require('vm'),assert=require('assert'),path=require('path');
const source=fs.readFileSync(path.join(__dirname,'cluster-placeholder.js'),'utf8');
assert(fs.readFileSync(path.join(__dirname,'../../app/src/main/java/com/abrar/voyahiran/core/bridge/ClusterPlaceholderScript.kt'),'utf8').replace(/\r/g,'').includes(source.replace(/\r/g,'')));
let now=0,tick;
const distance={call:(self,n)=>String(n)};
const unit={call:(self,c,n)=> n>=1000?'km':'m'};
const output=[];
const context={Date:{now:()=>now},console:{log:x=>output.push(x)},rpc:{exports:{}},setInterval:f=>tick=f,
 Java:{perform:f=>f(),use:n=>{assert.equal(n,'com.qinggan.cluster.GuidanceFormat');return {
 toDistance:{overload:()=>distance},toDistanceUnit:{overload:()=>unit}};}}};
vm.runInNewContext(source,context);
assert(output.includes('VI_CLUSTER_PLACEHOLDER_READY'));
assert.equal(distance.implementation(-2147483000),'-');
assert.equal(unit.implementation({},-2147483000),'');
for(const value of [-1,0,10,230,8778]) {
 assert.equal(distance.implementation(value),String(value));
 assert.equal(unit.implementation({},value),value>=1000?'km':'m');
}
now=16000;tick();assert.equal(distance.implementation,null);assert.equal(unit.implementation,null);
console.log('PASS: only our sentinel displays dash, normal values unchanged, watchdog restores methods, embedded parity');
