const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const path = require('node:path');
const kt = fs.readFileSync(path.join(__dirname, '../../app/src/main/java/com/abrar/voyahiran/core/bridge/SeatMemoryHookScript.kt'), 'utf8');
const source = kt.split('"""')[1].replaceAll('__DOLLAR__', '$');
function method(original) {
    const fn = function(...args) { return (fn.implementation || original).apply(this,args); };
    fn.overload = () => fn;
    fn.call = (self, ...args) => original.apply(self,args);
    return fn;
}
function fixture(role, fail=false) {
    let now=0, thread=1, autoGuest=true, uiGuest=true, callbackGuest=true, feedbackGuest=true, actions=[];
    const logs=[], timers=[], account={isGuest:{value:true}, getName:()=> 'guest'};
    const manager={isGuest:method(()=>true)}; manager.getInstance=()=>manager;
    manager.modifyIsPosSame=method(()=>{feedbackGuest=manager.isGuest();});
    manager.noMemoryModifyPosSame=method(()=>{feedbackGuest=manager.isGuest();});
    const view={isAttachedToWindow:()=>true, initFirstStatusView:method(()=>{uiGuest=manager.isGuest();})};
    view.sendCmd=method(slot=>actions.push('sendSeat0'+slot));
    view.saveSeatPosition=method(slot=>actions.push('saveSeat0'+slot));
    const cb={onSeatAdjustStateChanged:method(()=>{callbackGuest=manager.isGuest();})};
    const acc={getAccountInfo:method(()=>account)};
    function drawerType(){ return {handleSeatClick:method(function(){
        if(acc.getAccountInfo().isGuest.value) {actions.push('guest-toast');return;}
        if(this.moving) {actions.push('blocked-moving');return;}
        if(this.restoring) {actions.push('stop');return;}
        actions.push('sendSeat0'+this.slot);
        if(this.throwError) throw Error('OEM failure');
    })}; }
    const a=drawerType(),b=drawerType();
    const classes={
        'android.os.SystemClock':{elapsedRealtime:()=>now},
        'com.qinggan.app.vehiclebase.model.SeatMemoryManager':manager,
        'com.qinggan.app.vehiclebase.model.SeatMemoryManager$2':cb,
        'com.qinggan.app.vehiclebase.vehicle.fragments.air_and_seat.MirrorMainView':view,
        'com.qinggan.launcher.base.manager.AccountManager':acc,
        'com.qinggan.app.base.manager.AccountManager':acc,
        'com.qinggan.account.AccountInfoBean':{$new:()=>({isGuest:{value:true},setName(){},setGuest(value){this.isGuest.value=value;}})},
        'com.qinggan.mainlauncher.hiboard.vehiclehiboard.widget.ComponentSmallView':a,
        'com.qinggan.app.hiboard.vehiclehiboard.main.ComponentSmallView':a,
        'com.qinggan.app.hiboard.vehiclehiboard.main.ComponentSmallOTA1x1View':b
    };
    if(fail) delete classes['com.qinggan.app.hiboard.vehiclehiboard.main.ComponentSmallOTA1x1View'];
    const ctx={ROLE:role,rpc:{},console:{log:s=>logs.push(s)},Process:{getCurrentThreadId:()=>thread},
        setInterval:fn=>timers.push(fn),Java:{perform:fn=>fn(),scheduleOnMainThread:fn=>fn(),
            use:n=>{if(!classes[n])throw Error('Missing '+n);return classes[n];},
            choose:(n,x)=>{x.onMatch(view);x.onComplete();}}};
    vm.runInNewContext(source,ctx);
    return {logs,a,b,acc,account,manager,view,cb,actions,
        ping:()=>ctx.rpc.exports.onFridaStdin('ping'), disable:()=>ctx.rpc.exports.onFridaStdin('disable'),
        dispose:()=>ctx.rpc.exports.dispose(), tick:()=>{now+=11000;timers.forEach(f=>f());},
        state:()=>({uiGuest,callbackGuest,feedbackGuest})};
}
let checks=0;
for(const role of ['launcher','hiboard']) {
 const f=fixture(role);assert(f.logs.includes('VI_SEAT_READY '+role));
 f.a.handleSeatClick.call({slot:1}); // Original factory path remains Guest before the lease.
 assert.deepEqual(f.actions,['guest-toast']);f.actions.length=0;
 f.ping();
 for(const type of role==='hiboard'?[f.a,f.b]:[f.a]) {
   for(const slot of [1,2,3]) type.handleSeatClick.apply({slot});
 }
 assert.deepEqual(f.actions,role==='hiboard'?['sendSeat01','sendSeat02','sendSeat03','sendSeat01','sendSeat02','sendSeat03']:['sendSeat01','sendSeat02','sendSeat03']);
 assert.equal(f.acc.getAccountInfo(),f.account);assert.equal(f.account.isGuest.value,true);
 f.a.handleSeatClick.apply({slot:2,moving:true});assert.equal(f.actions.at(-1),'blocked-moving');
 f.a.handleSeatClick.apply({slot:2,restoring:true});assert.equal(f.actions.at(-1),'stop');
 assert.throws(()=>f.a.handleSeatClick.apply({slot:1,throwError:true}));
 assert.equal(f.acc.getAccountInfo(),f.account); // Scope unwound even on an OEM exception.
 f.tick();f.a.handleSeatClick.apply({slot:1});assert.equal(f.actions.at(-1),'guest-toast');
 f.ping();f.disable();f.a.handleSeatClick.apply({slot:1});assert.equal(f.actions.at(-1),'guest-toast');
 f.dispose();assert.equal(f.a.handleSeatClick.implementation,null);checks+=12;
}
{
 const f=fixture('vehicle'); f.ping();assert.equal(f.state().uiGuest,false);
 assert.equal(f.manager.isGuest(),true); // Cloud/account/automatic-recall callers untouched.
 f.cb.onSeatAdjustStateChanged({});assert.equal(f.state().callbackGuest,false);
 f.manager.modifyIsPosSame();assert.equal(f.state().feedbackGuest,false);
 assert.equal(f.manager.isGuest(),true);
 f.tick();assert.equal(f.state().uiGuest,true);
 f.ping();assert.equal(f.state().uiGuest,false);
 f.disable();assert.equal(f.state().uiGuest,true);
 f.view.sendCmd(1); f.view.saveSeatPosition(2); assert.deepEqual(f.actions,[]);checks+=9;
}
{
 const f=fixture('hiboard',true);assert(f.logs.some(s=>s.startsWith('VI_SEAT_ERROR')));
 f.ping();f.a.handleSeatClick.apply({slot:1});assert.equal(f.actions.at(-1),'guest-toast');
 assert.equal(f.acc.getAccountInfo(),f.account);checks+=3;
}
console.log(`${checks} seat-memory hook checks passed`);
