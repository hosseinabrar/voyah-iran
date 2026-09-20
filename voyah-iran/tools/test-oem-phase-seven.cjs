// Run the shipped Frida script against OEM doubles. No vehicle or network writes.
const fs=require('node:fs'), path=require('node:path'), vm=require('node:vm'), assert=require('node:assert/strict');
const source=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/abrar/voyahiran/core/bridge/OemPhaseSevenActions.kt'),'utf8');
const script=source.split('private val SCRIPT="""')[1].split('""".trimIndent()')[0].replaceAll("${'$'}",'$');
function run(action,p,options={}) {
  const calls=[],logs=[]; let checked=options.checked??false, scans=0;
  function view(name,more={}) {return {getClass:()=>({getName:()=>name}),isShown:()=>!options.hidden,isEnabled:()=>!options.disabled,getParent:()=>null,
    performClick:()=>{calls.push(['click',name]);return true;},...more};}
  const cb=view('CheckBox',{isChecked:()=>checked,performClick:()=>{calls.push(['toggle']);if(!options.reject)checked=!checked;return true;}});
  const binding={getClass:()=>({getName:()=> 'Binding'})};
  const node=view('HintSwitch',{switchBinding:{value:{getClass:()=>({getName:()=> 'Binding'}),hintSwitchButton:{value:cb}}},
    getControlVehicle:()=>({equals:s=>s==='TEST_SWITCH'})});
  binding.switchField={value:node};binding.modeButton={value:view('ModeButton')};
  for(const name of ['seekbarDriverScreen','seekbarCopilotScreen','seekbarMeterScreen']) {
    const seek=view('Seek',{getMin:()=>10,getMax:()=>110,getProgress:()=>options.current??60});
    binding[name]={value:view(name,{getSeekBar:()=>seek})};
  }
  const fragment={isAdded:()=>true,isResumed:()=>scans>(options.waitScans??0),getView:()=>node,binding:{value:binding},
    onProgressChanged:(v,s,n,user)=>calls.push(['brightness',v.getClass().getName(),n,user])};
  const audio={isConnected:()=>!options.disconnected,getStreamMaxVolume:()=>30,getStreamVolume:()=>options.current??10,
    setStreamVolume:(...a)=>calls.push(['volume',...a]),setStreamMute:(...a)=>calls.push(['mute',...a])};
  const setting={isInReverseState:{value:!!options.reverse},isRecording:{value:!!options.recording}};
  const classes={
    'android.app.ActivityThread':{currentApplication:()=>({getApplicationContext:()=>({})})},
    'java.lang.Object':{},'Binding':{},'HintSwitch':{},'CheckBox':{},'ModeButton':{},
    'android.view.View':{class:{isInstance:()=>false}},'android.view.ViewGroup':{class:{isInstance:()=>false}},
    'com.qinggan.app.basevehiclesetting.widgets.HintSwitch':{class:{isInstance:()=>true}},
    'com.qinggan.canbus.DFVehicleState':{TEST_SWITCH:{value:'TEST_SWITCH'}},
    'com.qinggan.setting.netlib.base.SettingManager':{getInstance:()=>({getAudioPolicyManager:()=>audio})},
    'com.qinggan.camera.common.CameraSetting':{getInstance:()=>setting},
    'com.qinggan.camera.common.CanBusHelper':{getInstance:()=>({isDisplayOffOrStandby:()=>!!options.standby,getarAppCameraState:()=>options.ar??0,getfaceidRegistState:()=>!!options.face})},
    'com.qinggan.camera.common.CameraVoiceGestureAction':{getHolder:()=>({jumpPhotoCamera:n=>calls.push(['photo',n]),jumpVideoCamera:n=>calls.push(['video',n])})},
    'com.qinggan.camera.bean.PowerState':{STOPRECORD:{value:1},$new:n=>({state:n})},
    'org.greenrobot.eventbus.EventBus':{getDefault:()=>({post:e=>calls.push(['stop',e.state])})}
  };
  for(const name of ['seekbarDriverScreen','seekbarCopilotScreen','seekbarMeterScreen'])classes[name]={};
  const request={action,p,fragment:'Fragment',buttons:[],...options.request};
  vm.runInNewContext(script,{request,console:{log:s=>logs.push(s)},setTimeout:f=>f(),Java:{perform:f=>f(),scheduleOnMainThread:f=>f(),
    cast:x=>x,use:n=>{assert.ok(classes[n],n);return classes[n];},choose:(n,c)=>{scans++;c.onMatch(fragment);c.onComplete();}}});
  return {calls,logs,checked,ok:logs.some(s=>s.startsWith('RAHA_ACTION_OK'))};
}
let count=0;function test(name,f){f();console.log('OK '+name);count++;}
test('Explicit switch values: no duplicate toggle on already desired state',()=>{
  for(const enabled of [true,false])for(const checked of [true,false]) {
    const r=run('SWITCH',{enabled},{checked,request:{toggle:'TEST_SWITCH'}});
    assert.equal(r.ok,true);assert.equal(r.checked,enabled);assert.equal(r.calls.length,enabled===checked?0:1);
  }
});
test('Hidden/disabled/rejected OEM controls never bypass their checks',()=>{
  for(const options of [{hidden:true},{disabled:true},{reject:true}]) {
    const r=run('SWITCH',{enabled:true},{...options,request:{toggleField:'switchField'}});
    assert.equal(r.ok,false);assert.equal(r.checked,false);assert.equal(r.calls.length,options.reject?1:0);
  }
});
test('Lane control waits for initialization then toggles once and invokes actual radio button',()=>{
  const r=run('SET_LANE_SUPPORT_MODE',{mode:'warning'},{waitScans:2,request:{toggle:'TEST_SWITCH',desired:true,buttons:['modeButton']}});
  assert.equal(r.ok,true);assert.deepEqual(r.calls,[['toggle'],['click','ModeButton']]);
});
test('Brightness uses native range, relative step and OEM callback',()=>{
  assert.deepEqual(run('SET_DISPLAY_BRIGHTNESS',{display:'central',percent:50}).calls,[['brightness','seekbarDriverScreen',60,true]]);
  assert.deepEqual(run('SET_DISPLAY_BRIGHTNESS',{display:'passenger',deltaPercent:-10}).calls,[['brightness','seekbarCopilotScreen',50,true]]);
  assert.equal(run('SET_DISPLAY_BRIGHTNESS',{display:'all',percent:100}).calls.length,3);
  assert.deepEqual(run('SET_DISPLAY_BRIGHTNESS',{display:'all',percent:100},{disabled:true}).calls,[]);
});
test('OEM audio channel mapping and range checks',()=>{
  for(const [channel,id] of Object.entries({media:3,call:6,navigation:1,voice:22,system:5})) {
    assert.deepEqual(run('SET_AUDIO_VOLUME',{channel,level:15},{request:{audio:true}}).calls,[['volume',id,15,2]]);
    assert.deepEqual(run('SET_AUDIO_MUTE',{channel,enabled:false},{request:{audio:true}}).calls,[['mute',id,false]]);
  }
  assert.deepEqual(run('ADJUST_AUDIO_VOLUME',{channel:'media',delta:1},{current:30,request:{audio:true}}).calls,[['volume',3,30,2]]);
  assert.equal(run('SET_AUDIO_VOLUME',{channel:'media',level:31},{request:{audio:true}}).ok,false);
  assert.deepEqual(run('SET_AUDIO_VOLUME',{channel:'media',level:10},{disconnected:true,request:{audio:true}}).calls,[]);
});
test('Camera follows OEM photo/video/stop routes and guards',()=>{
  assert.deepEqual(run('TAKE_PHOTO',{view:'default'},{request:{camera:true}}).calls,[['photo',1]]);
  assert.deepEqual(run('START_VIDEO_RECORDING',{view:'default'},{request:{camera:true}}).calls,[['video',2]]);
  assert.deepEqual(run('STOP_VIDEO_RECORDING',{},{recording:true,request:{camera:true}}).calls,[['stop',1]]);
  assert.deepEqual(run('STOP_VIDEO_RECORDING',{},{request:{camera:true}}).calls,[]);
  for(const options of [{reverse:true},{standby:true},{ar:1},{face:true},{recording:true}]) {
    const r=run('START_VIDEO_RECORDING',{view:'default'},{...options,request:{camera:true}});assert.equal(r.ok,false);assert.deepEqual(r.calls,[]);
  }
});
console.log(`${count} phase-seven OEM script tests passed`);
