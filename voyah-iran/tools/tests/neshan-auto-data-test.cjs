// Runs the actual embedded hook in a mocked Frida host; no Android build/device.
const fs = require('fs'), path = require('path'), vm = require('vm'), assert = require('assert');
const base = path.join(__dirname, '../../app/src/main/java/com/abrar/voyahiran');
const read = p => fs.readFileSync(path.join(base, p), 'utf8');
const script = read('core/bridge/NeshanHookScript.kt').split('val source = """')[1]
    .split('""".trimIndent()')[0].replaceAll("${'$'}", '$');
function host(routeEnabled) {
    let now = 10000, active = true, engine = {}, timer;
    const logs = [], update = {call() {}}, reset = {call() {}};
    const fields = {stepClass:'Step', updateClass:'Update', updateMethod:'u', resetClass:'Reset', resetMethod:'r',
        distance:'d', type:'t', modifier:'m', road:'road', roadExtra:'extra', turnIndex:'turn',
        routeState:{ownerClass:'Owner', instanceField:'N', stateClass:'State', activeMethod:'H', engineMethod:'A'}};
    const types = {
        Step:Object.fromEntries(['d','t','m','road','extra','turn'].map(n => [n,{overload(){}}])),
        Owner:{N:{value:{H:()=>active, A:()=>engine}}}, State:{H:{overload(){}}, A:{overload(){}}},
        Update:{u:{overload:()=>update}}, Reset:{r:{overload:()=>reset}}
    };
    const context = {NESHAN_PROFILE:{id:'fixture',hook:fields}, NESHAN_ROUTE_ENABLED:routeEnabled,
        Date:{now:()=>now}, Java:{perform:f=>f(),use:n=>types[n],cast:x=>x},rpc:{exports:{}},
        console:{log:s=>logs.push(s)},setInterval:f=>{timer=f}};
    vm.createContext(context); vm.runInContext(script,context);
    return {logs, update, reset,
        tick(){ context.rpc.exports.onFridaStdin('ping'); timer(); },
        active(value){active=value}, advance(ms){now+=ms},
        sample(index, meters){const step={d:()=>meters,t:()=>({name:()=> 'TURN'}),m:()=>({name:()=> 'LEFT'}),
            road:()=> 'road',extra:()=>'',turn:()=>({intValue:()=>1})};
            update.implementation(index,0,{size:()=>10,get:()=>step});},
        last(){const line=logs.at(-1); return line.startsWith('VI_NESHAN_DATA ') ? JSON.parse(line.slice(15)) : line;}
    };
}
const h = host(true);
h.sample(1,199); h.tick();
assert.equal(h.last().meters,199); assert.equal(h.last().step,1);
const epoch=h.last().epoch;
h.sample(1,180); h.tick(); assert.equal(h.last().epoch,epoch);
h.sample(2,800); h.tick(); assert.equal(h.last().step,2); assert.equal(h.last().epoch,epoch);
h.reset.implementation([]); h.tick(); assert.equal(h.last(),'VI_NESHAN_CLEAR');
h.sample(2,190); h.tick(); assert(h.last().epoch>epoch);
h.active(false); h.tick(); assert.equal(h.last(),'VI_NESHAN_CLEAR');
h.active(true); h.tick(); assert(!String(h.last()).startsWith('VI_NESHAN_DATA'));
h.sample(3,100); h.tick(); assert.equal(h.last().step,3);
const disabled=host(false); disabled.tick();
assert.equal(disabled.update.implementation,undefined);
assert(disabled.logs.some(s=>s.includes('source=ad-ui-only')));
const manager=read('core/bridge/NeshanHookManager.kt');
assert(manager.includes('val wantsRoute = flag.exists() || now() < autoUntil'));
assert(manager.includes('if (value != null && flag.exists())'));
const autoBranch=manager.split('"auto-state" -> {')[1].split('"status" ->')[0];
assert(autoBranch.includes('BridgeSubscriptionSession.vipActive'));
assert(!/writeText|renameTo|flag\.delete|cluster-navigation/.test(autoBranch));
assert(read('feature/launcher/LauncherMapPreview.kt').includes('"neshan-direct", "auto-state"'));
console.log('PASS: actual hook distance/step/epoch, reset/end/restart, disabled reader, independent Auto lease and OEM-output gating');
