package com.abrar.voyahiran.core.bridge
internal object NeshanHookScript {
 val source = """
'use strict';
// VI_NESHAN_CONFIG_ENGINE_V1: route observer and temporary ad UI suppression; NESHAN_PROFILE is validated signed data.
const profile = NESHAN_PROFILE;
const fields = profile.hook;
let enabled = true;
const routeEnabled = typeof NESHAN_ROUTE_ENABLED === 'undefined' || NESHAN_ROUTE_ENABLED;
let restoreAds = function() {};
function installAdSuppression() {
    // The signed, APK-matched profile owns every Neshan-specific selector.
    const ads = profile.adSuppression;
    if (!ads || !ads.enabled) return;
    const View = Java.use('android.view.View');
    const Group = Java.use('android.view.ViewGroup');
    const Integer = Java.use('java.lang.Integer');
    const saved = Java.use('java.util.WeakHashMap').${'$'}new();
    const visibility = View.setVisibility.overload('int');
    const attach = View.onAttachedToWindow.overload();
    function isAd(v) {
        return ads.viewIds.indexOf(v.getId()) >= 0 || ads.viewClasses.indexOf(String(v.getClass().getName())) >= 0;
    }
    function hide(v, desired) {
        saved.put(v, Integer.valueOf(desired));
        visibility.call(v, 8);
    }
    function scan(v) {
        if (isAd(v)) { if (!saved.containsKey(v)) hide(v, v.getVisibility()); return; }
        if (Group.class.isInstance(v)) {
            const group = Java.cast(v, Group);
            for (let i = 0; i < group.getChildCount(); i++) scan(group.getChildAt(i));
        }
    }
    visibility.implementation = function(value) {
        if (enabled && isAd(this)) { hide(this, value); return; }
        return visibility.call(this, value);
    };
    attach.implementation = function() {
        const result = attach.call(this);
        if (enabled && isAd(this) && !saved.containsKey(this)) hide(this, this.getVisibility());
        return result;
    };
    restoreAds = function() {
        visibility.implementation = null;
        attach.implementation = null;
        Java.scheduleOnMainThread(function() {
            const entries = saved.entrySet().iterator();
            while (entries.hasNext()) {
                const entry = entries.next();
                visibility.call(Java.cast(entry.getKey(), View), Java.cast(entry.getValue(), Integer).intValue());
            }
            saved.clear();
        });
    };
    // One traversal at injection. Subsequent changes are event driven, no polling/heap scans.
    Java.scheduleOnMainThread(function() {
        if (!enabled) return;
        try {
            const wm = Java.use('android.view.WindowManagerGlobal').getInstance();
            const f = wm.getClass().getDeclaredField('mViews');
            f.setAccessible(true);
            const roots = Java.cast(f.get(wm), Java.use('java.util.List'));
            for (let i = 0; i < roots.size(); i++) scan(Java.cast(roots.get(i), View));
            console.log('VI_NESHAN_ADS_READY');
        } catch (e) { console.log('VI_NESHAN_ADS_SCAN_ERROR ' + e); }
    });
}
let heartbeat = Date.now();
let latest = null;
let routeEpoch = 0;
let routeOwner = null, routeType = null;
const routeState = fields.routeState;
let hook = null;
let resetHook = null;
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
function clear(reason) { if (latest !== null) routeEpoch++; latest = null; sampleState = reason || 'route-reset'; }
function disable() {
    Java.scheduleOnMainThread(cancelRecenterSession);
    enabled = false; clear();
    restoreAds();
    restoreSurvey();
    if (hook) hook.implementation = null;
    if (resetHook) resetHook.implementation = null;
    console.log('VI_NESHAN_DISABLED');
}
rpc.exports = {
    onFridaStdin: function(data) {
        const cmd = data.trim();
        if (cmd === 'ping') heartbeat = Date.now();
        if (cmd === 'disable') Java.perform(disable);
        const recenter=/^recenter ([0-9]{1,5}) ([a-f0-9-]{36}) ([0-9]{13})$/.exec(cmd);
        if(recenter && typeof recenterCard==='function')Java.scheduleOnMainThread(function(){recenterCard(Number(recenter[1]),recenter[2],Number(recenter[3]));});
    },
    dispose: function() { Java.perform(disable); }
};
Java.perform(function() {
    try {
        installAdSuppression();
        installSurveyDismissal();
        if (!routeEnabled) {
            console.log('VI_NESHAN_READY profile=' + profile.id + ' source=ad-ui-only');
            return;
        }
        const Step = Java.use(fields.stepClass);
        if (routeState) {
            routeOwner = Java.use(routeState.ownerClass);
            routeType = Java.use(routeState.stateClass);
            if (routeOwner[routeState.instanceField] === undefined) throw Error('route state field missing');
            routeType[routeState.activeMethod].overload();
            routeType[routeState.engineMethod].overload();
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
            if (!enabled) return result;
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
    if (Date.now() - heartbeat > 15000) { disable(); return; }
    if (!routeEnabled) return;
    let verified = false;
    if (routeState) {
        try {
            const raw = routeOwner[routeState.instanceField].value;
            const state = raw === null ? null : Java.cast(raw, routeType);
            verified = state !== null && state[routeState.activeMethod]() === true && state[routeState.engineMethod]() !== null;
            if (!verified) clear('navigation-inactive');
        } catch(e) {
            clear('navigation-state-error');
        }
    }
    if (!enabled) return;
    if (Date.now() - heartbeat > 15000) { Java.perform(disable); return; }
    const age = latest ? Date.now() - latest.sourceAt : -1;
    if (Date.now() - lastStatus >= 5000) {
        lastStatus = Date.now();
        console.log('VI_NESHAN_STATUS ' + wireJson({state: sampleState, ageMs: age,
            routeActive: verified, type: latest ? latest.type : null, meters: latest ? latest.meters : null,
            roadEmpty: latest ? latest.road.length === 0 : null}));
    }
    if (latest && age >= 0 && (verified || age <= 5000)) {
        console.log('VI_NESHAN_DATA ' + wireJson({road: latest.road, meters: latest.meters,
            type: latest.type, modifier: latest.modifier, turnIndex: latest.turnIndex, step: latest.step, epoch: routeEpoch, ageMs: verified ? 0 : age, sourceAgeMs: age, routeActive: verified}));
        emitted = true;
    } else if (emitted) { console.log('VI_NESHAN_CLEAR'); emitted = false; }
}
setInterval(function() {
    // Poll a known singleton and two getters: no heap scans, UI/OCR or location writes.
    Java.perform(publish);
}, 200);
""".trimIndent()
}
