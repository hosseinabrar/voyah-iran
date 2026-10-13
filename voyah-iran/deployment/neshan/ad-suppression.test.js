'use strict';
const fs=require('fs'),vm=require('vm'),assert=require('assert'),path=require('path');
const source=fs.readFileSync(path.join(__dirname,'direct-navigation.js'),'utf8');
const profile=JSON.parse(fs.readFileSync(path.join(__dirname,'../../../voyah-iran-subscription-server/configs/neshan.source.json'),'utf8')).profiles.find(p=>p.versionCode===Number(process.env.NESHAN_TEST_VERSION || 70799));
let tick,now=1000; const logs=[];
const visibility={implementation:null,call(v,n){v.visible=n;}},attach={implementation:null,call(){}};
const saved=new Map();
const integer=n=>({intValue:()=>n});
const weak={put:(k,v)=>saved.set(k,v),containsKey:k=>saved.has(k),clear:()=>saved.clear(),entrySet:()=>({iterator:()=>{const a=[...saved];let i=0;return {hasNext:()=>i<a.length,next:()=>{const [k,v]=a[i++];return {getKey:()=>k,getValue:()=>v};}};}})};
const view=(id,name='android.view.View')=>({visible:0,getId:()=>id,getClass:()=>({getName:()=>name}),getVisibility(){return this.visible;}});
const banner=view(profile.adSuppression.viewIds[0]),takeover=view(-1,profile.adSuppression.viewClasses[0]),map=view(123);
const roots=[banner,takeover,map];
const classes={
 'android.view.View':{setVisibility:{overload:()=>visibility},onAttachedToWindow:{overload:()=>attach}},
 'android.view.ViewGroup':{class:{isInstance:()=>false}},
 'java.lang.Integer':{valueOf:integer},'java.util.WeakHashMap':{$new:()=>weak},'java.util.List':{},
 'android.view.WindowManagerGlobal':{getInstance:()=>({getClass:()=>({getDeclaredField:()=>({setAccessible(){},get:()=>({size:()=>roots.length,get:i=>roots[i]})})})})}
};
const context={NESHAN_PROFILE:profile,NESHAN_ROUTE_ENABLED:false,Date:{now:()=>now},console:{log:x=>logs.push(x)},rpc:{exports:{}},setInterval:f=>tick=f,Java:{perform:f=>f(),scheduleOnMainThread:f=>f(),cast:x=>x,use:n=>{assert(classes[n],n);return classes[n];}}};
vm.runInNewContext(source,context);
assert.equal(banner.visible,8);assert.equal(takeover.visible,8);assert.equal(map.visible,0);
visibility.implementation.call(banner,0);assert.equal(banner.visible,8);
visibility.implementation.call(map,4);assert.equal(map.visible,4);
const later=view(-1,profile.adSuppression.viewClasses[0]);attach.implementation.call(later);assert.equal(later.visible,8);
assert(logs.some(x=>x.startsWith('VI_NESHAN_READY')));
now+=16000;tick();assert.equal(banner.visible,0);assert.equal(takeover.visible,0);assert.equal(later.visible,0);assert.equal(map.visible,4);
assert.equal(visibility.implementation,null);assert.equal(attach.implementation,null);
assert(!logs.some(x=>x.startsWith('VI_NESHAN_DATA')));
console.log('PASS: both ads, existing and new views, re-show suppression, unrelated views, ad-only mode and watchdog restoration');

for (const setting of [undefined,{enabled:false,viewIds:[],viewClasses:[]}]) {
 const next={...profile}; if(setting)next.adSuppression=setting;else delete next.adSuppression;
 vm.runInNewContext(source,{...context,NESHAN_PROFILE:next});
 assert.equal(visibility.implementation,null);assert.equal(attach.implementation,null);
}
console.log('PASS: omitted/disabled config leaves ad views untouched');
