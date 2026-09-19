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
const Base = {getInstance() {return {};}, updateNavigationVisible: {overload() {return {call(app,p,visible,display,activity) {if(fail)throw Error('OEM unavailable');calls.push(['show',p,visible,display,activity]);}};}}};
const types = {'com.qinggan.launcher.base.drag.DragService': Drag, 'com.qinggan.launcher.base.BaseApplication': Base,
 'android.provider.Settings$Global': {getString(r,k) {return settings[k] ?? null;}}, 'android.app.ActivityManager': {}, 'android.app.ActivityManager$RunningTaskInfo': {}};
vm.runInNewContext(source, {console: {log: m => output.push(m)}, setInterval(f) {interval=f;}, Java: {
 perform(f) {f();}, scheduleOnMainThread(f) {f();}, use(n) {if(!(n in types))throw Error(n);return types[n];},
 cast(v) {return v;}, retain(v) {return v;}, choose(n,c) {c.onMatch(service);c.onComplete();}
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
check(() => assert.equal(calls.length,0));
taskDisplay=0;interval();check(() => assert.equal(calls[0][0],'show'));
settings[key+'_ack']='stale';event('test.app/.Main');check(() => assert.equal(calls[0][0],'original'));
settings[key+'_ack']=settings[key];fail=true;event('test.app/.Main');check(() => assert.equal(calls[0][0],'original'));
fail=false;event('not-installed/Activity');check(() => assert.equal(calls[0][0],'original'));
console.log(checks+' OEM navigation policy checks passed');
