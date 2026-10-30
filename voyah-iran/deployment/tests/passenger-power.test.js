// Execute the shipped scripts with per-display power/focus transitions.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, '../../app/src/main/java/com/abrar/voyahiran/core/bridge');
function scenario(off, passenger) {
  const launches = [], timers = [];
  const state = {off, passenger};
  const ctx = {getContentResolver: () => ({registerContentObserver(){}}),
    registerReceiver(){}, startActivity: (i, o) => launches.push([i.name, o.display])};
  const types = {
    'android.app.ActivityThread': {currentApplication: () => ctx},
    'android.os.SystemProperties': {getBoolean: () => state.off},
    'android.provider.Settings$Global': {getInt: () => 1, getString: () => String(Date.now()+60000), getUriFor: x => x},
    'com.qinggan.os.ServiceManager': {getDpyTopAppInfo: (_, d) => d === 1 ? state.passenger : 'com.qinggan.app.launcher.activity.MainActivity'},
    'com.qinggan.launcher.base.utils.AppUtils': {getTopAppInfo: () => state.passenger},
    'com.qinggan.utils.ScreenLiftUtils': {getScreenLiftState: () => 2},
    'android.app.ActivityOptions': {makeBasic: () => ({setLaunchDisplayId(d){this.display=d}, toBundle(){return this}})},
    'android.content.Intent': {$new: () => ({setClassName(_, n){this.name=n}, addFlags(){}})},
    'android.content.IntentFilter': {$new: () => ({addAction(){}})},
    'android.os.Handler': {$new: () => ({})},
    'android.os.Looper': {getMainLooper: () => ({})}
  };
  const env = vm.createContext({console: {log(){}}, Date, setInterval: f => timers.push(f),
    Java: {perform: f=>f(), scheduleOnMainThread: f=>f(), retain: x=>x,
      use(n){ if(types[n])return types[n]; throw Error('Unused mock '+n); },
      registerClass: () => ({$new: () => ({})})}});
  for(const file of ['LauncherScreenLiftScript.kt','PassengerHomeScript.kt']) {
    const src = fs.readFileSync(path.join(root,file),'utf8').split('"""')[1].replaceAll('__DOLLAR__','$');
    vm.runInContext(src,env);
  }
  return {state,launches,env,tick:()=>timers.forEach(f=>f())};
}
const home='com.qinggan.secondlauncher.activity.SecondMainActivity';
let s=scenario(true,home);
assert.equal(s.launches.length,0);
assert.equal(s.env.VI_PASSENGER_OPEN(false),false);
s.state.off=false;s.tick();
assert.deepEqual(s.launches,[['com.abrar.voyahiran.feature.launcher.PassengerLauncherActivity',1]]);
s.tick();assert.equal(s.launches.length,1); // no repeated wake navigation
s=scenario(true,'other.app/Activity');s.state.off=false;s.tick();assert.equal(s.launches.length,0);
s=scenario(true,'com.qinggan.secondlauncher.activity.AllAppActivity');s.state.off=false;s.tick();
assert.deepEqual(s.launches,[['com.abrar.voyahiran.feature.launcher.PassengerAppsActivity',1]]);
s=scenario(true,home);s.env.VI_LIFT_RECONCILE(1);assert.equal(s.launches.length,0);
console.log('Passenger power: 5 transition scenarios passed');
