// Run with node. Executes the actual injected source against a fake OEM/Android API.
const fs = require('fs'), path = require('path'), vm = require('vm'), assert = require('assert/strict');
const kotlin = fs.readFileSync(path.join(__dirname, '../../main/java/com/abrar/voyahiran/core/bridge/OemNavigationScript.kt'), 'utf8');
const source = kotlin.split('"""')[1].replaceAll('__DOLLAR__', '$');
const key = 'voyah_iran_display_policy';
const settings = {[key]: '1:' + 'a'.repeat(32), [key+'_ack']: '1:' + 'a'.repeat(32)};
const calls = [], output = [];
let interval, foreground = 'test.app/test.Main', taskDisplay = 0, fail = false;
const task = { displayId: {get value() { return taskDisplay; }}, topActivity: {get value() { return {flattenToString: () => foreground}; }} };
const infos = {
 'test.app': {flags: {value: 0}, uid: {value: 10123}},
 'com.qinggan.camera': {flags: {value: 1}, uid: {value: 1000}},
 'com.abrar.voyahiran': {flags: {value: 1}, uid: {value: 1000}},
 'updated.system': {flags: {value: 128}, uid: {value: 10200}}
};
const service = {$dispose() {}, getContentResolver() {return {};},
 getPackageManager() {return {getApplicationInfo(p) {if (!infos[p]) throw Error('missing package'); return infos[p];}};},
 getSystemService() {return {getRunningTasks() {return {size: () => 1, get: () => task};}};}
};
const original = {call(ctx, c) {calls.push(['original', c]);}};
const Drag = {onReceiveComponent: {overload() {return original;}}, dismissIconView: {overload() {return {call() {calls.push(['dismiss']);}};}}};
const Base = {getInstance() {return service;}, updateNavigationVisible: {overload() {return {call(app,p,visible,display,activity) {if(fail)throw Error('OEM unavailable');calls.push(['show',p,visible,display,activity]);}};}}};
const passenger = {call(ctx,p,a,v) {calls.push(['passenger',p,a,v]);}};
let oemPassengerTop = null, attached = true, barX = 0, animating = false;
const bar = {mScreenId:{value:1},mMoveWindowAnimator:{value:{isStarted:()=>animating}},
 mRootView:{value:{getParent:()=>attached ? {} : null}},mLp:{value:{x:{get value(){return barX;}}}},
 show(){calls.push(['recover']); attached=true;barX=0;}};
const nativeDismiss={call(){calls.push(['nativeDismiss']);attached=false;}};
const nativeRelease={call(ctx){nativeDismiss.implementation.call(ctx);}};
const Bar={dismiss:{overload(){return nativeDismiss;}},release:{overload(){return nativeRelease;}}};
const model = {$dispose(){}, mSecondNavigationBarLastShow:{value:false},
 mMainScreenMoving:{value:false},mSecondScreenMoving:{value:false},mSecondScreenNavigationBar:{value:bar}};
const Model = {handleUpdateSecondNavigationBar:{overload(){return passenger;}}};
const types = {'com.qinggan.launcher.base.drag.DragService': Drag, 'com.qinggan.launcher.base.BaseApplication': Base,
 'com.qinggan.app.launcher.LauncherModel':Model,
 'com.qinggan.launcher.base.utils.AppUtils':{getTopAppInfo(){return oemPassengerTop;}},
 'com.qinggan.launcher.navigation.NavigationBarController':Bar,
 'android.provider.Settings$Global': {getString(r,k) {return settings[k] ?? null;}}, 'android.app.ActivityManager': {}, 'android.app.ActivityManager$RunningTaskInfo': {}};
vm.runInNewContext(source, {console: {log: m => output.push(m)}, setInterval(f) {interval=f;}, Java: {
 perform(f) {f();}, scheduleOnMainThread(f) {f();}, use(n) {if(!(n in types))throw Error(n);return types[n];},
 cast(v) {return v;}, retain(v) {return v;}, choose(n,c) {c.onMatch(n === 'com.qinggan.app.launcher.LauncherModel' ? model : service);c.onComplete();}
}});
let checks=0;
function check(f){f();checks++;}
check(() => assert(output.includes('VI_NAV_READY')));
check(() => assert.deepEqual(calls[0], ['show','test.app',true,2,'test.Main']));
function event(c){calls.length=0; original.implementation.call(service,c);}
event('test.app/.Main');
check(() => assert.deepEqual(calls,[['show','test.app',true,2,'test.app.Main'],['dismiss']]));
event('com.abrar.voyahiran/.ui.MainActivity');
check(() => assert.equal(calls[0][0],'show'));
event('com.qinggan.camera/Camera');
check(() => assert.deepEqual(calls,[['original','com.qinggan.camera/Camera']]));
event('updated.system/Screen');
check(() => assert.equal(calls[0][0],'original'));
settings[key] = '0:'+'b'.repeat(32); settings[key+'_ack'] = settings[key]; calls.length=0; interval();
check(() => assert.deepEqual(calls,[['original',foreground]]));
event('test.app/.Main');check(() => assert.equal(calls[0][0],'original'));
settings[key] = '1:'+'c'.repeat(32);settings[key+'_ack'] = settings[key];taskDisplay=1;calls.length=0;interval();
check(() => assert.deepEqual(calls,[['passenger','test.app','',true]]));
taskDisplay=0;calls.length=0;interval();check(() => assert.equal(calls[0][0],'show'));
settings[key+'_ack']='stale';event('test.app/.Main');check(() => assert.equal(calls[0][0],'original'));
settings[key+'_ack']=settings[key];fail=true;event('test.app/.Main');check(() => assert.equal(calls[0][0],'original'));
fail=false;event('not-installed/Activity');check(() => assert.equal(calls[0][0],'original'));
// Passenger policy uses its own handler; it never dismisses the center floating icon.
foreground='test.app/test.Main';taskDisplay=1;calls.length=0;interval();
calls.length=0;passenger.implementation.call(model,'test.app','test.Main',false);
check(() => assert.deepEqual(calls,[['passenger','test.app','test.Main',true]]));
calls.length=0;interval();check(() => assert.equal(calls.length,0));
settings[key]='0:'+'d'.repeat(32);settings[key+'_ack']=settings[key];calls.length=0;interval();
check(() => assert.deepEqual(calls,[['passenger','test.app','',false]]));
calls.length=0;passenger.implementation.call(model,'test.app','test.Main',false);
check(() => assert.equal(calls[0][3],false));
settings[key]='1:'+'e'.repeat(32);settings[key+'_ack']=settings[key];
calls.length=0;passenger.implementation.call(model,'com.qinggan.camera','Camera',false);
check(() => assert.equal(calls[0][3],false));
calls.length=0;taskDisplay=2;interval();check(() => assert.equal(calls.length,0));
// OEM top API works even when ActivityManager only reports the center task.
oemPassengerTop='test.app/test.Main';taskDisplay=0;calls.length=0;interval();
check(() => assert(calls.some(c=>c[0]==='passenger' && c[2]==='' && c[3]===true)));
// A late direct OEM hide cannot remove the passenger bar.
calls.length=0;nativeDismiss.implementation.call(bar);check(()=>assert.equal(calls.length,0));
// Recover a previously hidden/slid-out bar even though app and policy did not change.
attached=false;calls.length=0;interval();check(()=>assert.deepEqual(calls,[['recover']]));
barX=-145;calls.length=0;interval();check(()=>assert.deepEqual(calls,[['recover']]));
// Do not interrupt move animations or native controller destruction.
model.mSecondScreenMoving.value=true;calls.length=0;nativeDismiss.implementation.call(bar);
check(()=>assert.deepEqual(calls,[['nativeDismiss']]));
calls.length=0;interval();check(()=>assert.equal(calls.length,0));model.mSecondScreenMoving.value=false;
attached=true;calls.length=0;nativeRelease.implementation.call(bar);check(()=>assert.deepEqual(calls,[['nativeDismiss']]));
oemPassengerTop='com.qinggan.camera/Camera';calls.length=0;nativeDismiss.implementation.call(bar);
check(()=>assert.deepEqual(calls,[['nativeDismiss']]));
oemPassengerTop='test.app/test.Main';bar.mScreenId.value=0;calls.length=0;nativeDismiss.implementation.call(bar);
check(()=>assert.deepEqual(calls,[['nativeDismiss']]));bar.mScreenId.value=1;
settings[key]='0:'+'f'.repeat(32);settings[key+'_ack']=settings[key];calls.length=0;nativeDismiss.implementation.call(bar);
check(()=>assert.deepEqual(calls,[['nativeDismiss']]));
console.log(checks+' OEM navigation policy checks passed');
