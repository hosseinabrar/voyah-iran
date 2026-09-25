const fs=require('fs'),vm=require('vm'),assert=require('assert'),path=require('path');
const kt=fs.readFileSync(path.join(__dirname,'../../app/src/main/java/com/abrar/voyahiran/core/bridge/OemPhaseSevenActions.kt'),'utf8');
const script=kt.split('private val SCRIPT="""')[1].split('""".trimIndent()')[0].replaceAll("${'$'}", '$');
function run(visible=true,recording=false){
 let clicks=0;const logs=[];const raw={};
 const button={isShown:()=>visible,isEnabled:()=>true,performClick:()=>{clicks++;return true}};
 const activity={getWindow:()=>({getDecorView:()=>({isShown:()=>true})}),getResources:()=>({getIdentifier:()=>12}),findViewById:()=>raw};
 const settings={isInReverseState:{value:false},isRecording:{value:recording},active:{value:true},getTopActivity:()=>activity};
 const types={'android.app.Activity':{},'android.view.View':{},'android.app.ActivityThread':{currentApplication:()=>({getApplicationContext:()=>({})})},'com.qinggan.camera.common.CameraSetting':{getInstance:()=>settings},'com.qinggan.camera.common.CanBusHelper':{getInstance:()=>({isDisplayOffOrStandby:()=>false,getarAppCameraState:()=>0,getfaceidRegistState:()=>false})},'com.qinggan.camera.common.SupportUtils':{isSupportDvr:()=>true}};
 vm.runInNewContext(script,{request:{camera:true,action:'SWITCH_CAMERA',p:{}},Java:{use:n=>{assert(types[n],n);return types[n]},cast:(x,t)=>x===raw?button:x,perform:f=>f(),scheduleOnMainThread:f=>f()},console:{log:s=>logs.push(s)}});
 return{clicks,logs};
}
assert.equal(run().clicks,1);assert(run().logs.some(s=>s.startsWith('RAHA_ACTION_OK')));
assert.equal(run(false).clicks,0);assert.equal(run(true,true).clicks,0);
console.log('PASS: generic View cast, one click, no unused voice-holder access, hidden/recording rejection');
