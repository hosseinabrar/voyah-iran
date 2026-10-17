const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync(require('node:path').join(__dirname, '../src/main/assets/audio-probe.js'), 'utf8');
function harness(role, options = {}) {
    let time = 1000, seq = 0;
    const events = [], timers = [], writes = [];
    const state = {native: 8, dts: true, atmos: true, loudness: 1, navigation: true, gain: 0.25};
    function method(original) {
        const m = {implementation: null, call: (self, ...args) => original.apply(self, args)};
        const fn = function (...args) { return m.implementation ? m.implementation.apply(this, args) : original.apply(this, args); };
        fn.overload = () => m;
        return {fn, m};
    }
    const gain = method(function (v) { state.gain = v; writes.push(['gain', v]); });
    const absolute = method(function (v) { writes.push(['absolute', v]); });
    const nav = method(function (v) { state.navigation = v; writes.push(['navigation', v]); });
    const api = {isDtsEnabled: () => state.dts, setDtsEnabled: v => {state.dts = v; writes.push(['dts', v]);},
        isAtmos: () => state.atmos, setAtmos: v => {state.atmos = v; writes.push(['atmos', v]);},
        getLoudnessState: () => state.loudness, setLoudness: v => {state.loudness = v; writes.push(['loudness', v]);},
        isNaviMix: () => state.navigation, setNaviMix: v => nav.fn.call(objects,v)};
    const native = {getVolume: () => state.native, setVolume: (channel, v) => {
        assert.equal(channel, 1); writes.push(['native', v]);
        if (options.failRestore && v === 8) throw Error('hardware unavailable');
        state.native = options.clamp ? Math.min(30, v) : v; return 0;
    }};
    const objects = { $dispose() {} };
    const classes = role === 'bluetooth' ? {
        'com.android.bluetooth.a2dpsink.A2dpSinkService': {informAudioTrackGainNative: gain.fn},
        'com.android.bluetooth.avrcpcontroller.AvrcpControllerStateMachine': {setAbsVolume: absolute.fn}
    } : {
        'android.os.ServiceManager': {getService: () => ({})},
        'com.qinggan.audiopolicy.IAudioPolicyService$Stub': {asInterface: () => api},
        'com.qinggan.audiopolicy.service.OSAudioPolicyService$1': {setNaviMix: nav.fn}
    };
    const context = {rpc:{exports:{}}, Java:{perform: f => f(), retain:v => v,
        use: n => { if (!classes[n]) throw Error('unavailable: ' + n); return classes[n]; },
        choose: (_, callbacks) => {callbacks.onMatch(native); callbacks.onComplete();}},
        console:{log:line => events.push(JSON.parse(line.substring('AUDIO_LAB '.length)))},
        setInterval:f => timers.push(f), Date:{now:() => time}, Map, Promise, Number, Boolean, JSON, Error};
    vm.runInNewContext(source, context);
    async function request(op, key, extras = {}) {
        const id = String(++seq);
        await context.rpc.exports.onFridaStdin(JSON.stringify({id, op, key, ...extras}));
        return events.find(e => e.id === id);
    }
    async function apply(key, value) {
        const p = await request('prepare', key, {value}); assert(p.ok, p.error);
        return request('apply', key, {before:p.result.before});
    }
    return {state,writes,events,request,apply,gain:v=>gain.fn.call(objects,v),absolute:v=>absolute.fn(v),nav:v=>nav.fn.call(objects,v),
        tick:ms => {time += ms; timers.forEach(f=>f());}, methods:[gain.m,absolute.m,nav.m]};
}
(async () => {
    let h = harness('oem');
    assert.equal(h.writes.length, 0, 'attach must not change state');
    assert.equal((await h.request('apply','native',{before:8})).ok, false, 'apply requires prepare');
    assert.equal((await h.request('prepare','native',{value:41})).ok, false);
    assert.equal((await h.request('prepare','native',{value:2.5})).ok, false);
    assert((await h.apply('native',40)).ok); assert.equal(h.state.native,40);
    h.tick(3001); assert.equal(h.state.native,8, 'native rollback at 3 seconds');
    for (const key of ['dts','atmos','loudness']) assert((await h.apply(key)).ok);
    assert.equal(h.state.dts,false); assert.equal(h.state.loudness,0);
    await h.request('restore','dts',{before:true});
    assert.equal(h.state.dts,true); assert.equal(h.state.atmos,false, 'independent restoration');
    h.tick(10001); assert.equal(h.state.atmos,true); assert.equal(h.state.loudness,1, 'heartbeat expiry restores');
    h = harness('oem', {clamp:true});
    const result = await h.apply('native',40);
    assert(result.ok); assert.equal(result.result.actual,30, 'clamping reported honestly');
    await h.request('restore','native',{before:8}); assert.equal(h.state.native,8);
    h = harness('oem');
    await h.request('prepare','native',{value:20}); h.state.native = 12;
    const race = await h.request('apply','native',{before:8});
    assert.equal(race.ok,false); assert.equal(race.mayHaveChanged,false);
    assert.equal(h.writes.length,0,'state changed since snapshot: do not mutate or blindly restore');
    h = harness('oem');
    assert((await h.apply('navigation')).ok); assert.equal(h.state.navigation,false);
    h.nav(true); assert.equal(h.state.navigation,false);
    await h.request('restore','navigation',{before:true}); assert.equal(h.state.navigation,true, 'restore original/latest natural navigation');
    h = harness('oem', {failRestore:true});
    assert((await h.apply('native',40)).ok);
    h.tick(3001);
    assert(h.events.some(e=>e.event === 'rollback-error'));
    assert(!h.events.some(e=>e.event === 'restored'), 'failed rollback must not acknowledge completion');
    h = harness('bluetooth');
    assert.equal((await h.request('prepare','duck')).ok,false,'unknown gain must refuse');
    h.gain(0); assert.equal((await h.request('prepare','duck')).ok,false,'muted gain must refuse');
    h.gain(0.25); assert((await h.apply('duck')).ok); assert.equal(h.state.gain,1);
    h.gain(0.1); assert.equal(h.state.gain,1);
    h.gain(0); assert.equal(h.state.gain,0,'mute must not be bypassed');
    h.gain(0.2); await h.request('restore','duck',{before:0.25});
    assert.equal(h.state.gain,0.2,'latest natural gain restored, not stale snapshot');
    assert((await h.apply('absolute')).ok); h.absolute(100);
    assert(!h.writes.some(w=>w[0] === 'absolute'));
    await h.request('restore','absolute',{before:false}); h.absolute(80);
    assert(h.writes.some(w=>w[0] === 'absolute' && w[1]===80));
    await h.request('stop'); assert(h.methods.every(m=>m.implementation === null));
    assert.equal((await h.request('prepare','duck')).ok,false,'stopped session refuses new tests');
    console.log('PASS: no writes on attach; prepare/journal boundary; bounds; Native 3s rollback; clamp feedback; independent effects; heartbeat recovery; navigation restoration; failed rollback retained; unknown/mute duck refusal; mute preserved; latest gain restored; incoming absolute suppression; hooks removed.');
})().catch(e=>{console.error(e);process.exitCode=1;});
