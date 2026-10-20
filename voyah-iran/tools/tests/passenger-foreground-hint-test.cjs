const fs=require('fs'),vm=require('vm'),assert=require('assert/strict'),path=require('path');
const dir=path.resolve(__dirname,'../../app/src/main/java/com/abrar/voyahiran/core/bridge');
const read=n=>fs.readFileSync(path.join(dir,n+'.kt'),'utf8').split('"""')[1];
const source=read('PassengerHomeScript');let now=1000;
const env={Date:{now:()=>now},Apps:{getTopAppInfo:()=> 'old.app/.Main'},ctx:{}};
vm.createContext(env);
vm.runInContext(source.slice(source.indexOf("let foregroundHint="),source.indexOf('function open(')),env);
const event=component=>({getStringExtra:key=>{assert.equal(key,'android.intent.extra.COMPONENT_NAME');return component;}});
for(const route of ['home-button','back','app-finish','move-to-center','move-to-virtual']){
 const home='com.abrar.voyahiran/.feature.launcher.PassengerLauncherActivity';
 env.VI_PASSENGER_FOREGROUND(event(home));assert.equal(env.top(),home,route+' ignores stale task cache');
 env.VI_PASSENGER_FOREGROUND(event('next.app/.Main'));assert.equal(env.top(),'next.app/.Main',route+' newer foreground wins');
}
now+=2201;assert.equal(env.top(),'old.app/.Main','hint expires');
env.VI_PASSENGER_FOREGROUND(event(null));assert.equal(env.top(),'old.app/.Main','empty event falls back');
for(const name of ['OemNavigationScript','PassengerBarScript']){
 const s=read(name),hook=s.slice(s.indexOf('receive.implementation=function'));
 assert(hook.indexOf('VI_PASSENGER_FOREGROUND(intent)')<hook.indexOf('receive.call(this,ctx,intent)'),name+' consumes event before OEM callbacks');
 assert(hook.includes("getIntExtra('displayId',-1)===1"));
 assert(s.includes('eventRetries=20'));
 new vm.Script(s.replaceAll('__DOLLAR__','$'));
}
console.log('PASS: foreground hint, stale-cache bypass, replacement, expiry, both ROM handlers and bounded retry contracts');
