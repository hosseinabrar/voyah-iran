// Host-side lifecycle/ROM-layout contract test. Does not replace head-unit integration testing.
const {readFileSync}=require('node:fs');
const {resolve}=require('node:path');
const vm=require('node:vm');
const assert=require('node:assert/strict');
const source=readFileSync(resolve(__dirname,'../../app/src/main/java/com/abrar/voyahiran/core/bridge/ClusterMusicScript.kt'),'utf8')
 .split('"""')[1].replaceAll("${'$'}",'$');
let now=10000, intervals=[], layouts=[];
const widget=()=>({visibility:0,setVisibility(n){this.visibility=n},getVisibility(){return this.visibility},$dispose(){},
 setText(t){this.text=t},setImageBitmap(b){this.bitmap=b},setImageDrawable(d){this.drawable=d},
 setMax(n){this.max=n},setProgress(n){this.progress=n},setPercent(n){this.percent=n},
 getClass(){return {getName:()=>this.circle?'com.qinggan.cardview.panel.CircleProgressView':'android.widget.ProgressBar'}}});
const context={getPackageName:()=> 'com.qinggan.instrumentcard',getResources:()=>({getIdentifier:n=>n}),
 getPackageManager:()=>({getApplicationIcon:p=>p})};
const original=widget(); original.visibility=4;
const panel={mThemeId:{value:4},mViewType:{value:0},children:[original],getContext:()=>context,
 getChildCount(){return this.children.length},getChildAt(i){return this.children[i]},
 addView(v){this.children.push(v);v.parent=this},removeView(v){this.children=this.children.filter(c=>c!==v);v.parent=null},
 equals(p){return p===this},$dispose(){}};
const activity={panelMusicView:{value:panel},isFinishing:()=>false,isDestroyed:()=>false};
const hook={call(self,theme){self.mThemeId.value=theme;return 'original-result'}};
const classes={
 'com.qinggan.cardview.panel.media.PanelView':{onThemeChanged:{overload:()=>hook}},
 'com.qinggan.instrumentcard.ScreenActivity':{screenActivity:{value:activity}},
 'android.view.LayoutInflater':{from:()=>({inflate(id){
   layouts.push(id);const fields={};
   ['music_tv_title','music_tv_song_name','music_tv_artist_name','music_iv_cover','music_iv_type','music_iv_stop','music_progress','music_tv_play_time','music_tv_total_time'].forEach(k=>fields[k]=widget());
   fields.music_progress.circle=panel.mThemeId.value===1;
   return {...widget(),fields,getContext:()=>context,findViewById:n=>fields[n],getParent(){return this.parent}};
 }})},
 'java.lang.String':{$new:s=>s},'android.util.Base64':{decode:s=>[1,2,3]},
 'android.graphics.BitmapFactory':{decodeByteArray:()=> 'cover'},'android.os.SystemClock':{elapsedRealtime:()=>now}
};
const sandbox={Java:{use:n=>classes[n]||{},cast:v=>v,retain:v=>v,perform:f=>f(),scheduleOnMainThread:f=>f()},
 rpc:{exports:{}},console,Date:{now:()=>now},setInterval:f=>intervals.push(f)};
vm.runInNewContext(source,sandbox);
const send=data=>sandbox.rpc.exports.onFridaStdin(JSON.stringify(data));
const frame={source:'User Player',title:'Tamasha',artist:'Shadmehr',package:'user.player',playing:true,duration:10000,position:3000,at:now,cover:'image'};
send(frame);
assert.equal(original.visibility,8);assert.equal(panel.children.length,2);
let current=panel.children[1];assert.equal(current.fields.music_tv_song_name.text,'Tamasha');
assert.equal(current.fields.music_progress.progress,3000);assert.equal(current.fields.music_iv_cover.bitmap,'cover');
now+=1000;intervals[0]();assert.equal(current.fields.music_progress.progress,4000);
assert.equal(hook.implementation.call(panel,1,true),'original-result');
current=panel.children[1];assert.equal(current.fields.music_progress.percent,.4);
assert.equal(layouts.at(-1),'layout_panel_media_music_simple_view');
sandbox.rpc.exports.onFridaStdin('clear');assert.equal(panel.children.length,1);assert.equal(original.visibility,4);
send({...frame,playing:false,cover:'',at:now});current=panel.children[1];now+=2000;intervals[0]();
assert.equal(current.fields.music_progress.percent,.3);assert.equal(current.fields.music_iv_cover.bitmap,null);
now+=4000;intervals[0]();assert.equal(panel.children.length,1);assert.equal(original.visibility,4);
send({...frame,at:now});sandbox.rpc.exports.dispose();assert.equal(panel.children.length,1);assert.equal(hook.implementation,null);
console.log('Cluster music: OEM layouts, themes, pause, missing cover, release, lease expiry and disposal passed');
