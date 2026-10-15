const fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('assert/strict');
const root=path.resolve(__dirname,'../../app/src/main');
const read=p=>fs.readFileSync(path.join(root,p),'utf8');
const prefix='java/com/abrar/voyahiran/';
const raw=read(prefix+'core/bridge/LauncherAppsScript.kt');
const source=raw.split('val source = """')[1].split('""".trimIndent()')[0].replaceAll('__DOLLAR__','$').replace('__EXCLUDED__','[]');
new vm.Script(source);
let wire='',allowed=true;
const opens=[],replies=[];
const env={Java:{scheduleOnMainThread:f=>f()},Global:{getString:()=>wire},ctx:{getContentResolver:()=>null},
    allowed:()=>allowed,beans:()=>[{subtype:'AC'}],Launch:{startAppBySubType:(_,id,display)=>opens.push({id,display})},
    send:(nonce,value)=>replies.push({nonce,value}),console:{log(){}}};
vm.createContext(env);
vm.runInContext(source.slice(source.indexOf("        let previous='';"),source.indexOf('        const Observer=')),env);
const nonce='a'.repeat(32);
function request(s){wire=s;env.request();}
request(`open:${nonce}:AC`);
request(`open:${nonce}:AC:1`);
assert.deepEqual(opens,[{id:'AC',display:0},{id:'AC',display:1}]);
for(const invalid of ['2','-1','NaN','1:extra'])request(`open:${nonce}:AC:${invalid}`);
assert.equal(opens.length,2,'Reject invalid target displays');
allowed=false;request(`open:${nonce}:AC:0`);
assert.equal(opens.length,2,'VIP lease is required on either display');
assert.match(replies.at(-1).value.error,/VIP/);
const manifest=read('AndroidManifest.xml');
assert.match(manifest, /PassengerAppsActivity[^>]+taskAffinity="com.abrar.voyahiran.passenger.apps"/);
const activity=read(prefix+'feature/launcher/LauncherAppsActivity.kt');
assert.match(activity,/launcherDisplayId==0 && LauncherMapPreview.openHostedMap/);
assert.equal((activity.match(/launchDisplayId=launcherDisplayId/g)||[]).length,2);
assert.match(activity,/LauncherAppsCache.observe/);
assert.match(activity,/unobserveCatalog\?\.invoke/);
console.log('PASS: legacy center route, passenger route, display validation, VIP guard, separate task and catalog observation');
