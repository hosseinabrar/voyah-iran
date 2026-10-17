'use strict';
// Injected into ONE allowlisted OEM process. No filesystem/system image changes.
// Prepare -> durable client journal -> apply. Nothing writes merely on attach.
const active = new Map();
const prepared = new Map();
const hooks = [];
let api = null, native = null, role = '', heartbeat = Date.now(), stopped = false;
let gainObject = null, naturalGain = null, gainMethod = null;
let naviObject = null, naturalNavi = null, naviMethod = null;
let ownNavigationWrite = false;
function log(data) { console.log('AUDIO_LAB ' + JSON.stringify(data)); }
function scalar(x) { return typeof x === 'boolean' ? x : Number(x); }
const definitions = {
    dts: ['isDtsEnabled', 'setDtsEnabled', false],
    atmos: ['isAtmos', 'setAtmos', false],
    loudness: ['getLoudnessState', 'setLoudness', 0]
};
function ensure(condition, message) { if (!condition) throw new Error(message); }
function observeGain(obj, value) {
    if (gainObject) gainObject.$dispose();
    gainObject = Java.retain(obj); naturalGain = Number(value);
    log({event: 'gain', requested: naturalGain, overridden: active.has('duck') && value > 0 && value < 1});
}
function read(key) {
    if (key === 'native') {
        ensure(native !== null, 'Existing Native instance unavailable');
        const value = Number(native.getVolume(1));
        ensure(Number.isInteger(value) && value >= 0 && value <= 40, 'Invalid media Native feedback: ' + value);
        return value;
    }
    if (key === 'duck') {
        ensure(gainMethod && gainObject && naturalGain !== null, 'No observed Bluetooth gain yet. Reconnect/play Bluetooth or trigger navigation, then retry.');
        return naturalGain;
    }
    if (key === 'absolute') { ensure(role === 'bluetooth' && hooks.some(h => h.key === key), 'Absolute volume hook unavailable'); return false; }
    if (key === 'navigation') {
        ensure(api && naviMethod, 'OEM navigation-mix hook unavailable');
        return Boolean(api.isNaviMix());
    }
    const d = definitions[key];
    ensure(d && api && api[d[0]] && api[d[1]], 'Unsupported control: ' + key);
    return scalar(api[d[0]]());
}
function write(key, value) {
    if (key === 'native') return Number(native.setVolume(1, value)); // OEM channel 1 = MEDIA. No arbitrary channel access.
    if (key === 'duck') {
        ensure(gainObject && gainMethod, 'Bluetooth gain object lost');
        return gainMethod.call(gainObject, value);
    }
    if (key === 'absolute') return;
    if (key === 'navigation') {
        ownNavigationWrite = true;
        try { return api.setNaviMix(Boolean(value)); } finally { ownNavigationWrite = false; }
    }
    const d = definitions[key];
    return api[d[1]](value);
}
function restore(key, snapshot) {
    ensure(['native', 'duck', 'absolute', 'navigation', ...Object.keys(definitions)].includes(key), 'Unknown rollback key');
    const entry = active.get(key);
    // Keep the entry on failed rollback so the watchdog can retry; don't mask failures.
    if (entry) entry.restoring = true;
    const value = key === 'duck' && naturalGain !== null ? naturalGain :
        key === 'navigation' && naturalNavi !== null ? naturalNavi : snapshot;
    try {
        const result = write(key, value);
        const actual = key === 'absolute' ? false : key === 'duck' ? value : read(key);
        // getVolume may return a clamped value during the test; rollback MUST match.
        ensure(actual === value, 'Rollback not confirmed: ' + key + ' expected=' + value + ' actual=' + actual);
        active.delete(key); prepared.delete(key);
        log({event: 'restored', key, value, result, verification: key === 'duck' ? 'setter-completed-no-gain-getter' : key === 'absolute' ? 'hook-disabled' : 'getter-matched'});
    } catch (e) { if (entry) entry.restoring = false; throw e; }
}
function stop() {
    const errors = [];
    for (const [key, entry] of active) {
        try { restore(key, entry.before); } catch (e) { errors.push(key + ': ' + e); }
    }
    // Remove suppression even when restoring a hardware value failed.
    for (const h of hooks) h.method.implementation = null;
    stopped = true;
    ensure(errors.length === 0, errors.join('; '));
}
function command(c) {
    if (c.op === 'ping') { heartbeat = Date.now(); return {}; }
    if (c.op === 'stop') { stop(); return {}; }
    ensure(!stopped, 'Session stopped; reconnect');
    const key = c.key;
    if (c.op === 'restore') { restore(key, c.before); return {}; }
    if (c.op === 'prepare') {
        ensure(!active.has(key) && !prepared.has(key), 'Already active/prepared; restore first');
        const before = read(key);
        if (key === 'duck') ensure(before > 0, 'Bluetooth is muted / has lost audio focus; start playback first');
        if (key === 'navigation') naturalNavi = before;
        const target = key === 'native' ? Number(c.value) : key === 'duck' ? 1 :
            key === 'absolute' ? true : key === 'navigation' ? false : definitions[key][2];
        if (key === 'native') ensure(Number.isInteger(target) && target >= 0 && target <= 40, 'Native media range for this experiment is 0..40');
        prepared.set(key, {before, target});
        return {before, target};
    }
    if (c.op === 'apply') {
        const entry = prepared.get(key);
        ensure(entry && entry.before === c.before, 'Missing/mismatched durable snapshot');
        if (!['duck', 'absolute'].includes(key) && read(key) !== entry.before) {
            prepared.delete(key);
            throw new Error('State changed during prepare; no write performed; retry');
        }
        entry.until = Date.now() + (key === 'native' ? 3000 : 60000);
        active.set(key, entry); prepared.delete(key); // install rollback before any write
        const result = write(key, entry.target);
        const actual = key === 'duck' ? null : read(key);
        if (key !== 'native' && key !== 'duck' && key !== 'absolute') ensure(actual === entry.target, 'Setter had no effect; restore required');
        return {requested: entry.target, actual, result, expiresMs: key === 'native' ? 3000 : 60000};
    }
    throw new Error('Unknown operation');
}
rpc.exports = {
    onFridaStdin(data) {
        return new Promise(resolve => Java.perform(() => {
            let c;
            try {
                c = JSON.parse(data.trim());
                const result = command(c);
                if (c.op !== 'ping') log({id: c.id, ok: true, result});
            } catch (e) { log({id: c && c.id, ok: false, error: String(e), mayHaveChanged: Boolean(c && active.has(c.key))}); }
            resolve();
        }));
    },
    dispose() { return new Promise(resolve => Java.perform(() => { try { stop(); } catch (e) { log({event:'rollback-error', error:String(e)}); } resolve(); })); }
};
Java.perform(() => {
    const capabilities = [];
    try {
        const Sink = Java.use('com.android.bluetooth.a2dpsink.A2dpSinkService');
        role = 'bluetooth';
        gainMethod = Sink.informAudioTrackGainNative.overload('float');
        gainMethod.implementation = function (value) {
            observeGain(this, value);
            const entry = active.get('duck');
            // Never override mute/focus-loss (0), only positive attenuation.
            return gainMethod.call(this, entry && !entry.restoring && value > 0 && value < 1 ? 1 : value);
        };
        hooks.push({key:'duck', method:gainMethod}); capabilities.push('duck');
        const Abs = Java.use('com.android.bluetooth.avrcpcontroller.AvrcpControllerStateMachine');
        const abs = Abs.setAbsVolume.overload('int');
        abs.implementation = function (value) {
            log({event:'absolute-volume', requested:value, blocked:active.has('absolute')});
            if (!active.has('absolute')) return abs.call(this, value);
        };
        hooks.push({key:'absolute', method:abs}); capabilities.push('absolute');
    } catch (e) { log({event:'capability', component:'bluetooth', error:String(e)}); }
    if (!role) {
        role = 'oem';
        try {
            const ServiceManager = Java.use('android.os.ServiceManager');
            const Stub = Java.use('com.qinggan.audiopolicy.IAudioPolicyService$Stub');
            api = Stub.asInterface(ServiceManager.getService('qg.audio_policy'));
            ensure(api !== null, 'qg.audio_policy unavailable');
            for (const key of Object.keys(definitions)) {
                try { read(key); capabilities.push(key); } catch (e) { log({event:'unsupported', key, error:String(e)}); }
            }
            for (const name of ['OSAudioPolicyService', 'MixAudioPolicyService']) {
                try {
                    const Binder = Java.use('com.qinggan.audiopolicy.service.' + name + '$1');
                    const method = Binder.setNaviMix.overload('boolean');
                    method.implementation = function (value) {
                        if (!ownNavigationWrite) naturalNavi = Boolean(value);
                        if (naviObject) naviObject.$dispose(); naviObject = Java.retain(this);
                        const entry = active.get('navigation');
                        return method.call(this, entry && !entry.restoring ? false : value);
                    };
                    naviMethod = method; hooks.push({key:'navigation', method});
                } catch (_) { }
            }
            if (naviMethod) capabilities.push('navigation');
            Java.choose('com.qinggan.audiopolicy.service.common.AudioPolicyNative', {
                onMatch(instance) { native = Java.retain(instance); return 'stop'; },
                onComplete() {
                    try { read('native'); capabilities.push('native'); } catch (e) { log({event:'unsupported', key:'native', error:String(e)}); }
                    log({event:'ready', role, capabilities});
                }
            });
        } catch (e) { log({event:'fatal', error:String(e)}); }
    } else log({event:'ready', role, capabilities});
});
setInterval(() => Java.perform(() => {
    for (const [key, entry] of active) {
        if (Date.now() > entry.until || Date.now() - heartbeat > 10000) {
            try { restore(key, entry.before); } catch (e) { log({event:'rollback-error', key, error:String(e)}); }
        }
    }
}), 500);
