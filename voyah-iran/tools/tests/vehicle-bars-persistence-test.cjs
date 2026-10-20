const fs=require('fs'),vm=require('vm'),assert=require('assert');
const path=require('path');
const text=fs.readFileSync(path.join(__dirname,'../../app/src/main/java/com/abrar/voyahiran/core/bridge/VehicleBarsScript.kt'),'utf8');
const source=text.split('val source = """')[1].split('""".trimIndent()')[0].replaceAll('__DOLLAR__','$');
let theme='simple',lift=2,adds=0,removes=0;
const method=fn=>({overload(){return this},call(self,...args){return fn(self,...args)}});
const drawable=color=>({getColor:()=>color,equals(other){return this===other}});
const view=()=>({bg:null,setBackground(b){this.bg=b},setBackgroundColor(c){this.bg=drawable(c)},getBackground(){return this.bg},equals(other){return this===other}});
const show=method(()=>{});
const display={getRealSize(p){p.x.value=1920}};
const context={getSystemService(name){return name==='display'?{getDisplay:()=>display}:{}},createDisplayContext(){return this}};
const classes={
 'com.qinggan.launcher.navigation.NavigationBarController':{show},
 'com.qinggan.launcher.navigation.INavigationBar':{getScreenUpView:method(x=>x.view)},
 'android.graphics.drawable.ColorDrawable':{$new:drawable},
 'android.view.View':{$new:view},
 'java.lang.String':{$new:s=>s},
 'android.view.WindowManager$LayoutParams':{$new:()=>({gravity:{},x:{},y:{},setTitle(){}})},
 'android.view.WindowManager':{removeViewImmediate:method(()=>removes++)},
 'android.view.ViewManager':{addView:method(()=>adds++)},
 'android.hardware.display.DisplayManager':{},
 'android.graphics.Point':{$new:()=>({x:{value:0}})},
 'android.os.SystemProperties':{get:()=>theme},
 'com.qinggan.utils.ScreenLiftUtils':{getScreenLiftState:()=>lift}
};
const env={console,Java:{perform:fn=>fn(),use:n=>{assert(classes[n],n);return classes[n]},retain:x=>x,cast:x=>x,choose(){}}};
vm.createContext(env);vm.runInContext(source,env);
for(const id of [0,1]){
 const bar={mScreenId:{value:id},mNavigationBar:{value:{view:view()}},equals(other){return this===other}};
 env.VI_SYNC_BAR_BACKGROUND(context,id,true);
 show.implementation.call(bar);
 const before=adds;
 env.VI_SYNC_BAR_BACKGROUND(context,id,false); // empty top task during HOME
 env.VI_RELEASE_BAR_BACKGROUND(bar);
 assert.equal(removes,0,'HOME/release must retain the light backdrop');
 assert.equal(bar.mNavigationBar.value.view.bg.getColor(),-1709069);
 const replacement={...bar,mNavigationBar:{value:{view:view()}}};
 show.implementation.call(replacement);
 assert.equal(adds,before,'controller replacement must reuse top window');
 assert.equal(replacement.mNavigationBar.value.view.bg.getColor(),-1709069);
}
theme='default';env.VI_SYNC_BAR_BACKGROUND(context,0,false);
assert.equal(removes,1,'dark theme restores OEM when disabled');
lift=1;env.VI_SYNC_BAR_BACKGROUND(context,1,false);
assert.equal(removes,2,'lowered display releases its old geometry');
console.log('PASS: persistent light bars on both displays, controller replacement, theme and lift cleanup');
