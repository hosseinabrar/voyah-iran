const fs=require('fs'),vm=require('vm'),assert=require('assert');
const source=fs.readFileSync(require('path').join(__dirname,'../../app/src/main/java/com/abrar/voyahiran/core/bridge/OemNavigationScript.kt'),'utf8');
let s=source.split('val source = """')[1].split('""".trimIndent()')[0].replaceAll('__DOLLAR__','$');
new vm.Script(s);
s=s.slice(s.indexOf('        let refreshDrawer'),s.indexOf('        function remember'));
let on=true,opened=0,nativeClicks=0;
const bar={hashCode:()=>1,isShowScreenAllApp:{value:false},mScreenUpAllAppView:{value:{getId:()=>42}},visible:false};
const hook=fn=>({call:(_, ...args)=>fn(...args)});
const init=hook(()=>{bar.visible=bar.isShowScreenAllApp.value});
const click=hook(()=>nativeClicks++);
const list=arr=>({arr:[...arr],size(){return this.arr.length},get(i){return this.arr[i]},add(v){this.arr.push(v)}});
const bean=p=>({getPackageName:()=>p});
const stock=list([bean('stock')]),main=list([bean('user'),bean('system'),bean('stock')]);
const apps=hook(i=>i===1?stock:main);
const pm={getApplicationInfo:p=>({flags:{value:p==='system'?1:0}}),getLaunchIntentForPackage:()=>({})};
const base={getContentResolver:()=>({}),getPackageManager:()=>pm,expandAllApp:i=>{assert.equal(i,1);opened++}};
const types={
 'com.qinggan.launcher.navigation.NavigationBarSecond':{initAllAppShow:{overload:()=>init},onClick:{overload:()=>click}},
 'com.qinggan.launcher.allapp.AllAppDataManager':{getAllApps:{overload:()=>apps}},
 'com.qinggan.launcher.allapp.AppBean':{},'java.util.ArrayList':{$new:l=>list(l.arr)}};
bar.initAllAppShow=()=>init.implementation.call(bar);
const ctx={Java:{use:n=>{assert(types[n],n);return types[n]},retain:x=>x,cast:x=>x,choose:(n,o)=>{o.onMatch(bar);o.onComplete()},scheduleOnMainThread:f=>f()},Base:{getInstance:()=>base},Global:{getInt:()=>on?1:0},console};
vm.createContext(ctx);vm.runInContext(s+'\nglobalThis.refresh=refreshDrawer;',ctx);
assert(bar.visible);assert.equal(bar.isShowScreenAllApp.value,false);
click.implementation.call(bar,{getId:()=>42});assert.equal(opened,1);
assert.deepEqual(apps.implementation(1).arr.map(b=>b.getPackageName()),['stock','user']);
assert.equal(stock.size(),1);assert.strictEqual(apps.implementation(0),main);
on=false;ctx.refresh();assert(!bar.visible);
assert.strictEqual(apps.implementation(1),stock);
click.implementation.call(bar,{getId:()=>42});assert.equal(nativeClicks,1);
bar.isShowScreenAllApp.value=true;bar.initAllAppShow();assert(bar.visible);
console.log('PASS: syntax, default activation, display-1 launch, user-app merge, stock preservation, central isolation, disable and native restoration');
