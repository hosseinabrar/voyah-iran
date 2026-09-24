package com.abrar.voyahiran.core.bridge
internal object NeshanHookScript {
 val source = """
'use strict';
// VI_NESHAN_CONFIG_ENGINE_V1: read-only engine; NESHAN_PROFILE is validated signed data.
const profile = NESHAN_PROFILE;
const fields = profile.hook;
let enabled = true;
let heartbeat = Date.now();
let latest = null;
let routeOwner = null, routeType = null, routeField = null;
let routeActiveGetter = null, routeEngineGetter = null;
let lastStateError = 0, stateError = null;
const routeState = fields.routeState;
let hook = null;
let resetHook = null;
let lifecycleStart = null, lifecycleStop = null, lifecycleDestroy = null;
let appClosed = false;
let emitted = false;
let lastStatus = 0;
let sampleState = 'waiting-for-route';
function text(value) { return value === null || value === undefined ? '' : String(value); }
// frida-inject stdout may use an ASCII locale on the head unit. Escape UTF-16
// code units BEFORE writing to stdout; JSONObject restores the original Unicode.
function wireJson(value) {
    return JSON.stringify(value).replace(/[\u007f-\uffff]/g, function(c) {
        return '\\u' + c.charCodeAt(0).toString(16).padStart(4, '0');
    });
}
function clear(reason) { latest = null; sampleState = reason || 'route-reset'; }
function disable() {
    enabled = false; clear();
    if (hook) hook.implementation = null;
    if (resetHook) resetHook.implementation = null;
    if (lifecycleStart) lifecycleStart.implementation = null;
    if (lifecycleStop) lifecycleStop.implementation = null;
    if (lifecycleDestroy) lifecycleDestroy.implementation = null;
    console.log('VI_NESHAN_DISABLED');
}
rpc.exports = {
    onFridaStdin: function(data) {
        const cmd = data.trim();
        if (cmd === 'ping') heartbeat = Date.now();
        if (cmd === 'disable') Java.perform(disable);
    },
    dispose: function() { Java.perform(disable); }
};
Java.perform(function() {
    try {
        const Step = Java.use(fields.stepClass);
        // Lifecycle delivery is independent of obfuscated Neshan class/method names.
        // Home/background and configuration changes are not explicit app exits.
        const Instrumentation = Java.use('android.app.Instrumentation');
        lifecycleStart = Instrumentation.callActivityOnStart.overload('android.app.Activity');
        lifecycleStop = Instrumentation.callActivityOnStop.overload('android.app.Activity');
        lifecycleDestroy = Instrumentation.callActivityOnDestroy.overload('android.app.Activity');
        function closing(activity) {
            if (activity.isTaskRoot() && activity.isFinishing() && !activity.isChangingConfigurations()) {
                appClosed = true;
                clear('app-closed');
                if (emitted) { console.log('VI_NESHAN_CLEAR'); emitted = false; }
                console.log('VI_NESHAN_APP_CLOSED');
            }
        }
        lifecycleStart.implementation = function (activity) {
            try {
                if (activity.isTaskRoot() && appClosed) {
                    appClosed = false;
                    clear('waiting-for-new-route');
                    console.log('VI_NESHAN_APP_REOPENED');
                }
            } catch(e) { console.log('VI_NESHAN_LIFECYCLE_ERROR ' + String(e)); }
            return lifecycleStart.call(this, activity);
        };
        lifecycleStop.implementation = function (activity) {
            try { closing(activity); } catch(e) { console.log('VI_NESHAN_LIFECYCLE_ERROR ' + String(e)); }
            return lifecycleStop.call(this, activity);
        };
        lifecycleDestroy.implementation = function (activity) {
            try { closing(activity); } catch(e) { console.log('VI_NESHAN_LIFECYCLE_ERROR ' + String(e)); }
            return lifecycleDestroy.call(this, activity);
        };
        if (routeState) {
            routeOwner = Java.use(routeState.ownerClass);
            routeType = Java.use(routeState.stateClass);
            // Reflection avoids Frida's field/method name collisions on obfuscated classes.
            routeField = Java.retain(routeOwner.class.getDeclaredField(routeState.instanceField));
            routeField.setAccessible(true);
            routeActiveGetter = routeType[routeState.activeMethod].overload();
            routeEngineGetter = routeType[routeState.engineMethod].overload();
        }
        // Verify the profile before installing either interception.
        ['distance','type','modifier','road','roadExtra','turnIndex'].forEach(function(name) { Step[fields[name]].overload(); });
        hook = Java.use(fields.updateClass)[fields.updateMethod].overload('int', 'double', 'java.util.List');
        resetHook = Java.use(fields.resetClass)[fields.resetMethod].overload('java.util.List');
        resetHook.implementation = function(list) {
            clear();
            return resetHook.call(this, list);
        };
        hook.implementation = function(index, travelled, list) {
            const result = hook.call(this, index, travelled, list);
            if (!enabled || appClosed) return result;
            try {
                if (list === null || index < 0 || index >= list.size()) { clear('invalid-step-index'); return result; }
                const step = Java.cast(list.get(index), Step);
                const errors = [];
                function read(name, convert) {
                    try { const v = step[fields[name]](); return v === null ? null : convert(v); }
                    catch (e) { errors.push(name); return null; }
                }
                const total = read('distance', Number);
                const progress = travelled === null ? null : Number(travelled);
                const delta = total !== null && progress !== null ? total - progress : NaN;
                const meters = Number.isFinite(delta) && delta <= 1000000 ? Math.max(0, Math.trunc(delta)) : null;
                const road = (text(read('road', String)) + ' ' + text(read('roadExtra', String))).trim();
                const type = read('type', v => String(v.name())) || 'UNKNOWN';
                const modifier = read('modifier', v => String(v.name())) || 'UNKNOWN';
                const turnIndex = read('turnIndex', v => Number(v.intValue()));
                sampleState = errors.length ? 'partial:' + errors.join(',') : 'valid';
                latest = { road: road.substring(0, 200), meters: meters, type: type,
                    modifier: modifier, turnIndex: turnIndex, step: index, sourceAt: Date.now() };
            } catch (e) { clear('sample-error'); console.log('VI_NESHAN_SAMPLE_ERROR ' + String(e)); }
            return result;
        };
        console.log('VI_NESHAN_READY profile=' + profile.id + ' source=' + fields.updateClass + '.' + fields.updateMethod);
    } catch (e) {
        disable();
        console.log('VI_NESHAN_ERROR unsupported-profile ' + String(e));
    }
});
function publish() {
    if (!enabled) return;
    let verified = false;
    if (appClosed) clear('app-closed');
    if (routeState && !appClosed) {
        try {
            const raw = routeField.get(null);
            const state = raw === null ? null : Java.cast(raw, routeType);
            verified = state !== null && routeActiveGetter.call(state) === true && routeEngineGetter.call(state) !== null;
            stateError = null;
            if (!verified) clear('navigation-inactive');
        } catch(e) {
            // A failed status read is not an explicit navigation exit. Keep only fresh
            // guidance (the existing 5s limit), never extend its lease on uncertainty.
            stateError = String(e);
            if (Date.now() - lastStateError >= 5000) {
                lastStateError = Date.now();
                console.log('VI_NESHAN_STATE_ERROR ' + wireJson({error: stateError,
                    owner: routeState.ownerClass, field: routeState.instanceField}));
            }
        }
    }
    if (!enabled) return;
    if (Date.now() - heartbeat > 15000) { Java.perform(disable); return; }
    const age = latest ? Date.now() - latest.sourceAt : -1;
    if (Date.now() - lastStatus >= 5000) {
        lastStatus = Date.now();
        console.log('VI_NESHAN_STATUS ' + wireJson({state: stateError ? 'navigation-state-error' : sampleState, error: stateError, ageMs: age,
            routeActive: verified, type: latest ? latest.type : null, meters: latest ? latest.meters : null,
            roadEmpty: latest ? latest.road.length === 0 : null}));
    }
    if (latest && age >= 0 && (verified || age <= 5000)) {
        console.log('VI_NESHAN_DATA ' + wireJson({road: latest.road, meters: latest.meters,
            type: latest.type, modifier: latest.modifier, turnIndex: latest.turnIndex, step: latest.step, ageMs: verified ? 0 : age, sourceAgeMs: age, routeActive: verified}));
        emitted = true;
    } else if (emitted) { console.log('VI_NESHAN_CLEAR'); emitted = false; }
}
setInterval(function() {
    // Poll a known singleton and two getters: no heap scans, UI/OCR or location writes.
    Java.perform(publish);
}, 200);
""".trimIndent()
}
