// Host regression: no Android UI/Fragment mocks exist; direct operations must work without them.
const fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('assert');
const root=path.join(__dirname,'../app/src/main/java/com/abrar/voyahiran/core/bridge');
const source=fs.readFileSync(path.join(root,'OemPhaseSevenActions.kt'),'utf8');
const script=source.split('private val SCRIPT="""')[1].split('""".trimIndent()')[0].replaceAll("${'$'}",'$');
function test(action,p,{hot=false,platform=true,reject=false}={}) {
 const writes=[],logs=[],ctx={getContentResolver:()=>({})};let lock=0;
 const back={getBackLight:{overload:()=>({call:()=>50})},getDimmer:()=>0};
 for(const method of ['setBackLight','setDimmer'])back[method]={overload:()=>({call:(_,...args)=>{writes.push([method,...args]);return !reject;}})};
 const types={
  'android.app.ActivityThread':{currentApplication:()=>({getApplicationContext:()=>ctx})},
  'com.qinggan.app.setting.componement.SettingApp':{getInstance:()=>({isHighLCDTemp:{value:hot}})},
  'com.qinggan.setting.netlib.base.SettingManager':{getInstance:()=>({getBackLightManager:()=>back})},
  'com.qinggan.app.vehiclesetting.utils.Utils':{is97CBasePlatform:()=>platform},
  'com.qinggan.provider.QGSettings$Global':{putInt:(_,key,v)=>{writes.push(['lock',key,v]);lock=v;return !reject;},getInt:()=>lock},
  'com.qinggan.canbus.DFVehicleState':{ICM_SCR_BRI_LEVEL:{value:'ICM_SCR_BRI_LEVEL'}}
 };
 const bus={setVehicleState:{overload:()=>({call:(_,field,v)=>{writes.push(['can',field,v]);return 0;}})}};
 const Bus={getInstance:{overload:()=>({call:()=>bus})}};types['com.qinggan.canbus.CanBusManager']=Bus;
 vm.runInNewContext(script,{request:{action,p},console:{log:x=>logs.push(x)},Java:{perform:f=>f(),scheduleOnMainThread:f=>f(),use:n=>{assert(n in types,'unexpected dependency '+n);return types[n];}}});
 return {writes,ok:logs.some(x=>x.startsWith('RAHA_ACTION_OK')),logs};
}
for(const [mode,value] of [['day',1],['night',0],['auto',2]]){
 const r=test('SET_DISPLAY_THEME_MODE',{mode});assert(r.ok);assert.deepEqual(r.writes,[['setDimmer',value,1,19]]);
}
for(const [display,id] of [['central',1],['passenger',2],['cluster',16]]){
 const r=test('SET_DISPLAY_BRIGHTNESS',{display,deltaPercent:10});assert(r.ok);assert.deepEqual(r.writes[0],['setBackLight',60,1,id]);
}
assert.equal(test('SET_DISPLAY_BRIGHTNESS',{display:'all',percent:0}).writes.filter(x=>x[0]==='setBackLight').length,3);
for(const enabled of [true,false]) {const r=test('SET_REAR_DISPLAY_LOCK',{enabled});assert(r.ok);assert.equal(r.writes[0][2],enabled?2:0);}
for(const opts of [{hot:true},{reject:true}])assert(!test('SET_DISPLAY_THEME_MODE',{mode:'day'},opts).ok);
assert(!test('SET_REAR_DISPLAY_LOCK',{enabled:true},{platform:false}).ok);
assert(!/Java\.choose|performClick|CarSettingActivity|\.activity.MainActivity/.test(source));
assert(source.includes('if(request.optBoolean("camera") && action!=RahaAction.STOP_VIDEO_RECORDING)'));
console.log('PASS: headless display/theme/rear-lock dispatch, thermal/platform/rejection guards and no Settings UI dependencies');
