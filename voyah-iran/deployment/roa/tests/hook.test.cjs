const fs = require('fs');
const vm = require('vm');
const assert = require('assert');
const script = fs.readFileSync(require('path').join(__dirname, '../hook_camera_front_roa_switch.js'), 'utf8');
let type = 0, originalCalls = 0, timer, supported = true;
const messages = [];
const button = { visibility: 8, enabled: false, clickable: false, equals(v) { return v === this; },
    getVisibility() { return this.visibility; }, setVisibility(v) { this.visibility = v; },
    isEnabled() { return this.enabled; }, setEnabled(v) { this.enabled = v; },
    isClickable() { return this.clickable; }, setClickable(v) { this.clickable = v; } };
const activity = { camera_change_iv: { value: button }, isResumed: { value: true } };
const resume = { implementation: null, call() { button.visibility = 8; } };
const change = { implementation: null, call() { originalCalls++; type = (type + 1) % 3; } };
const setting = {};
const classes = {
 'com.qinggan.camera.activity.RecordVideoActivity': {resumeLogic: {overload: () => resume}, changeClick: {overload: () => change}},
 'com.qinggan.camera.common.CameraSetting': {getInstance: () => setting, getCameraType: {overload: () => ({call: () => type})}, setCameraType: {overload: () => ({call: (_, v) => {type = v;}})}},
 'com.qinggan.camera.common.SupportUtils': {isSupportRoa: () => supported},
 'java.lang.ref.WeakReference': {$new: v => ({get: () => v, $dispose() {}})},
 'android.view.View': {}
};
const context = {Java: {perform: f => f(), use: n => {assert(classes[n], n); return classes[n];}, retain: v => v, cast: v => v,
 scheduleOnMainThread: f => f(), choose: (_, callbacks) => {callbacks.onMatch(activity); callbacks.onComplete();}},
 rpc: {exports: {}}, console: {log: s => messages.push(s), error: s => messages.push(s)},
 setInterval: f => {timer = f; return 1;}, clearInterval() {}, Date, Promise};
vm.createContext(context); vm.runInContext(script, context);
(async () => {
 assert(messages.includes('VI_ROA_READY'));
 assert.equal(button.visibility, 0); assert(button.clickable);
 change.implementation.call(activity); assert.equal(type, 1); assert.equal(originalCalls, 1);
 change.implementation.call(activity); assert.equal(type, 0); assert.equal(originalCalls, 2);
 type = 2; change.implementation.call(activity); assert.equal(type, 0); assert.equal(originalCalls, 3);
 supported = false; type = 1; change.implementation.call(activity); assert.equal(type, 2);
 supported = true; resume.implementation.call(activity); assert.equal(button.visibility, 0);
 await context.rpc.exports.onFridaStdin('disable\n');
 assert.equal(button.visibility, 8); assert.equal(button.enabled, false); assert.equal(button.clickable, false);
 assert.equal(resume.implementation, null); assert.equal(change.implementation, null);
 assert(messages.includes('VI_ROA_DISABLED'));
 console.log('PASS: initial attachment, original call order, 0/1 switching, other camera type, capability guard, resume, disable/restore.');
})().catch(e => {console.error(e); process.exitCode = 1;});
