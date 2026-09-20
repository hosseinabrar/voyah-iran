// Exercises the shipped injected script with fake OEM objects; no device/network access.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const source = fs.readFileSync(path.join(__dirname, '../app/src/main/java/com/abrar/voyahiran/core/bridge/OemEnergySceneActions.kt'), 'utf8');
const script = source.split('private val SCRIPT = """')[1].split('""".trimIndent()')[0].replaceAll("${'$'}", '$');
const profiles = {
  'false/false': {electric:2,smart:1,fuel:3,save:4},
  'true/false': {electric:2,hybrid:3,save:4},
  'false/true': {electric:2,hybrid:3,fuel:4},
  'true/true': {electric:2,hybrid:3,fuel:4}
};
function run(action,p,options={}) {
  const calls=[], logs=[], props=options.props||{};
  let duration=60, engine=false;
  const overload=fn=>({overload:()=>({call:(_, ...args)=>fn(...args)})});
  const enumClass=new Proxy({}, {get:(_,k)=>({value:k})});
  const bus={getVehicleState:k=>k==='BMS_SOC_DISPLAY'?options.soc??80:k==='IVI_SOC_MODESET'?options.mode??4:0,
    getGearStatus:()=>({toString:()=>options.gear||'Parking'}),
    setVehicleAndAirConditionBundleState:(_,b)=>{for(const [k,v] of Object.entries(b.values))calls.push(['bundle',k,v]);}};
  const manager={inPowerHoldMode:()=>!!options.activeHold,canStartPowerHoldMode:()=>options.canHold!==false,
    isEnableEngineExtender:()=>engine,setEnableEngineExtender:e=>{engine=e;},
    enterPowerHoldMode:(e,m)=>calls.push(['hold',e,m]),exitPowerHoldMode:()=>calls.push(['exitHold'])};
  const prefs={getInt:()=>duration, edit:()=>({putInt:(_,v)=>{duration=v;return {commit:()=>true};}})};
  const ctx={getSharedPreferences:()=>prefs,getResources:()=>({getIdentifier:()=>1,getIntArray:()=>[60,90,120,150,180,-1,30]}),
    startActivity:i=>calls.push(['activity',i.action]),startService:i=>{calls.push(['service',i.action,i.extras]);return {};},
    sendBroadcast:i=>calls.push(['broadcast',i.action]), getPackageManager:()=>({getApplicationInfo:()=>({})})};
  const classes={
    'android.app.ActivityThread':{currentApplication:()=>({getApplicationContext:()=>ctx})},
    'com.qinggan.canbus.CanBusManager':{getInstance:overload(()=>bus)},
    'com.qinggan.canbus.VehicleState':enumClass,'com.qinggan.canbus.DFVehicleState':enumClass,
    'com.qinggan.app.basevehiclesetting.canbustools.CanBusTool':{instance:()=>({
      setVehicleStateBack:overload((k,v)=>calls.push(['write',k,v])),setMultiSerially:overload(m=>calls.push(['serial',...m.values])),backAfterThree(){}})},
    'android.os.SystemProperties':{get:overload((k,d)=>props[k]??d)},
    'android.content.Intent':{$new:a=>{const i={action:a,extras:{},setFlags(){},setPackage(){}};i.putExtra=overload((k,v)=>{i.extras[k]=v;});return i;}},
    'android.os.Bundle':{$new:()=>({values:{},putString(){},putInt(k,v){this.values[k]=v;}})},
    'com.qinggan.utils.ScreenLiftUtils':{getScreenLiftState:()=>options.screen??2},
    'com.qinggan.scene.powerhold.PowerHoldModeManager':{getInstance:()=>manager,SUPPORT_ENGINE:{value:options.engineSupported!==false}},
    'com.qinggan.bus.QGBusEvent':{$new:()=>({setDestination(d){this.destination=d;},setEventType(t){this.type=t;},setData(){}})},
    'com.qinggan.bus.QGBus':{$new:()=>({publish:e=>calls.push(['event',e.destination,e.type])})},
    'com.qinggan.app.vehiclesetting.fragments.drivepreference.DrivePreferenceConfigs':{$new:()=>({
      isSupportExtends:()=>options.extends!==false,isDoFengH97acd:()=>!!options.ca,
      ...(options.legacy?{}:{is97XOversea:()=>!!options.overseas})})},
    'java.util.LinkedHashMap':{$new:()=>({values:[],put(k,v){this.values.push([k,v]);}})},
    'java.lang.Integer':{valueOf:overload(v=>v)}
  };
  vm.runInNewContext(script,{request:{action,p,profiles},console:{log:s=>logs.push(s)},Java:{
    perform:fn=>fn(),scheduleOnMainThread:fn=>fn(),use:name=>{assert.ok(classes[name],name);return classes[name];}}});
  return {calls,logs,duration,engine,ok:logs.some(s=>s.startsWith('RAHA_ACTION_OK'))};
}
let count=0;
function test(name,fn){fn();count++;console.log('OK '+name);}
test('Unsupported Save is rejected without writing on overseas variants',()=>{
  for(const ca of [false,true]){const r=run('SET_ENERGY_STRATEGY',{strategy:'save'},{overseas:true,ca});assert.equal(r.ok,false);assert.deepEqual(r.calls,[]);}
});
test('Same numeric slot follows OEM label, not a global alias',()=>{
  assert.deepEqual(run('SET_ENERGY_STRATEGY',{strategy:'fuel'},{overseas:true,mode:2}).calls,[['bundle','IVI_SOC_MODESET',4]]);
  assert.deepEqual(run('SET_ENERGY_STRATEGY',{strategy:'fuel'},{legacy:true}).calls,[['bundle','IVI_SOC_MODESET',3]]);
  assert.equal(run('SET_ENERGY_STRATEGY',{strategy:'fuel'},{ca:true}).ok,false);
});
test('Non-extender and reserve outside selected reserve mode write nothing',()=>{
  assert.deepEqual(run('SET_ENERGY_STRATEGY',{strategy:'electric'},{extends:false}).calls,[]);
  assert.deepEqual(run('SET_BATTERY_RESERVE_TARGET',{percent:50},{mode:2}).calls,[]);
  assert.deepEqual(run('SET_BATTERY_RESERVE_TARGET',{percent:50}).calls,[['write','SREV_SOC_SET',5]]);
});
test('Stop charge uses both OEM operations in order',()=>assert.deepEqual(run('STOP_CHARGING',{}).calls,[['serial',['HUM_STOP_CHARGE',1],['CHARGE_GUN_UNLOCK_SET',1]]]));
test('Battery preconditioning uses explicit desired values',()=>{
  for(const enabled of [true,false])assert.deepEqual(run('SET_BATTERY_PRECONDITIONING',{enabled}).calls,[['write','DRIVER_PREHEAT_SET',enabled?1:0]]);
});
test('Scene guard rejects moving vehicle, low battery and conflicting scenes',()=>{
  for(const o of [{gear:'Drive'},{soc:10},{props:{'com.qinggan.restmode.state':'on'}},{screen:1}]){
    const r=run('ACTIVATE_SCENE',{scene:'camp'},o);assert.equal(r.ok,false);assert.deepEqual(r.calls,[]);
  }
});
test('Native scene entry and exit routes',()=>{
  assert.deepEqual(run('ACTIVATE_SCENE',{scene:'camp'}).calls,[['activity','com.qinggan.app.CAMP_MODE']]);
  assert.deepEqual(run('ACTIVATE_SCENE',{scene:'nap'}).calls,[['activity','com.qinggan.app.REST_MODE']]);
  assert.equal(run('ACTIVATE_SCENE',{scene:'babyCare'}).calls[0][2].MODE_NAME,'呵护宝宝');
  assert.equal(run('ACTIVATE_SCENE',{scene:'party'}).calls[0][2].MODE_NAME,'party');
  assert.deepEqual(run('ACTIVATE_SCENE',{scene:'quickWarm'}).calls,[['broadcast','com.pateo.ivy.scene.fastwarm']]);
  assert.deepEqual(run('DEACTIVATE_SCENE',{scene:'current'},{props:{'com.qinggan.restmode.state':'on'}}).calls,[['event','com.qinggan.app.restmode','qg_action_exit_rest_mode']]);
});
test('Duration setting does not activate an inactive scene',()=>{
  const r=run('SET_POWER_KEEP_DURATION',{minutes:90});assert.equal(r.ok,true);assert.deepEqual(r.calls,[]);assert.equal(r.duration,90);
  assert.deepEqual(run('SET_POWER_KEEP_DURATION',{minutes:90},{activeHold:true}).calls,[['hold',false,90]]);
});
test('Engine setting respects drivetrain support and applies active mode',()=>{
  assert.equal(run('SET_POWER_KEEP_ENGINE_CHARGING',{enabled:true},{engineSupported:false}).ok,false);
  assert.deepEqual(run('SET_POWER_KEEP_ENGINE_CHARGING',{enabled:true},{activeHold:true}).calls,[['hold',true,60]]);
});
test('Named exit closes the actual active scene, not the spoken scene',()=>{
  for(const scene of ['current','camp','nap','party','babyCare','carWash','quickWarm','powerKeep']) {
    assert.deepEqual(run('DEACTIVATE_SCENE',{scene},{props:{'com.qinggan.restmode.state':'on'}}).calls,
      [['event','com.qinggan.app.restmode','qg_action_exit_rest_mode']]);
  }
});
test('Save plus reserve validates capabilities before any write',()=>{
  const request={strategy:'save',reservePercent:70};
  assert.deepEqual(run('SET_ENERGY_STRATEGY',request,{overseas:true,mode:2}).calls,[]);
  assert.deepEqual(run('SET_ENERGY_STRATEGY',request,{mode:2}).calls,[['bundle','IVI_SOC_MODESET',4],['write','SREV_SOC_SET',9]]);
  assert.deepEqual(run('SET_ENERGY_STRATEGY',{strategy:'save',reservePercent:83},{mode:2}).calls,[]);
});
test('Timed power hold activates and updates an existing session',()=>{
  for(const activeHold of [false,true])assert.deepEqual(run('ACTIVATE_SCENE',{scene:'powerKeep',minutes:30},{activeHold}).calls,[['hold',false,30]]);
  assert.deepEqual(run('ACTIVATE_SCENE',{scene:'powerKeep',minutes:300}).calls,[]);
});
console.log(`${count} script behavior tests passed (hardware integration still requires vehicle testing).`);
