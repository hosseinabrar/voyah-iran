// Real injected mount/style/metadata/layout functions, with typed Android doubles.
// Not an Android render: generic findViewById results deliberately expose NO View methods.
const fs=require('fs'),vm=require('vm'),assert=require('node:assert/strict'),path=require('path');
const raw=fs.readFileSync(path.join(__dirname,'../../app/src/main/java/com/abrar/voyahiran/core/bridge/ClusterMusicScript.kt'),'utf8');
const source=raw.split('"""')[1].replaceAll("${'$'}",'$');
new vm.Script(source);
function exercise(theme,script=source){
 const layouts=[],children=[],logs=[];let adds=0,removes=0;
 const object=()=>({$dispose(){},equals(o){return this===o;}});
 const resources={getIdentifier(name,type){return name==='absent'?0:name;},getString:name=>'OEM:'+name};
 const context={getResources:()=>resources,getPackageName:()=> 'com.qinggan.instrumentcard',
  getPackageManager:()=>({getApplicationIcon:pkg=>'ICON:'+pkg})};
 function view(kind='View'){
  return {...object(),kind,visibility:0,scaleX:1,scaleY:1,w:0,h:0,requested:true,text:'',size:36,
   getContext:()=>context,getVisibility(){return this.visibility;},setVisibility(v){this.visibility=v;},
   setScaleX(v){this.scaleX=v;},setScaleY(v){this.scaleY=v;},getText(){return this.text;},
   getTextSize(){return this.size;},setTextSize(unit,v){assert.equal(unit,0);this.size=v;},
   setSingleLine(){},setEllipsize(){},setSelected(){},setClickable(){},setFocusable(){},setImportantForAccessibility(){},
   setScaleType(){},setLayerType(type){assert.equal(type,1);},setImageDrawable(d){this.drawable=d;},setImageBitmap(b){this.bitmap=b;},
   setMax(v){this.max=v;},setProgress(v){this.progress=v;},setPercent(v){this.percent=v;},
   getWidth(){return this.w;},getHeight(){return this.h;},isLayoutRequested(){return this.requested;},
   measure(w,h){assert(w>=1073741824&&h>=1073741824);},layout(l,t,r,b){this.w=r-l;this.h=b-t;this.requested=false;},
   draw(){this.drawn=true;},getParent(){return this.parent||null;}};
 }
 function inflate(name){
  const v=view('ViewGroup');v.name=name;v.nodes={};
  const names=name.includes('no_media')?['music_tv_title','music_tv_tips']:
   ['music_tv_title','music_tv_song_name','music_tv_artist_name','music_tv_play_time','music_tv_total_time','music_iv_type','music_iv_cover','music_iv_stop','music_progress'];
  for(const name of names)v.nodes[name]=view(name.includes('_tv_')?'TextView':name==='music_progress'?'ProgressBar':'ImageView');
  v.findViewById=id=>v.nodes[id]?{genericObject:v.nodes[id]}:null;
  layouts.push(v);return v;
 }
 const factory=view(),hidden=view();hidden.visibility=8;children.push(factory,hidden);
 const decor={...view('ViewGroup'),getBackground:()=>null,getChildCount:()=>children.length,getChildAt:i=>children[i],
  addView(v){children.push(v);v.parent=this;adds++;},removeView(v){children.splice(children.indexOf(v),1);v.parent=null;removes++;}};
 const panel={...view('ViewGroup'),mThemeId:{value:theme},getWidth:()=>490,getHeight:()=>506,getLocationInWindow(a){a[0]=90;a[1]=154;},
  getChildCount:()=>children.length,getChildAt:i=>children[i],
  addView(v){children.push(v);v.parent=this;adds++;},removeView(v){children.splice(children.indexOf(v),1);v.parent=null;removes++;}};
 const window={...object(),getDecorView:()=>decor,getAttributes:()=>({format:{value:-3}}),setBackgroundDrawable(){},setFormat(){}};
 const activity={getWindow:()=>window};
 const cls=name=>({name});
 const View=cls('View'),Group=cls('ViewGroup'),Image={...cls('ImageView'),$new:()=>view('ImageView')};
 const Text={...cls('TextView'),setText:{overload:()=>({call(t,s){assert.equal(t.kind,'TextView');t.text=s;}})}};
 const paint=()=>({...object(),setXfermode(){}});
 function use(name){
  if(name==='java.lang.String')return {$new:s=>s};
  if(name.endsWith('TruncateAt'))return {END:{value:'END'}};
  if(name.endsWith('ScaleType'))return {FIT_XY:{value:'FIT_XY'}};
  if(name==='android.widget.ProgressBar'||name.endsWith('CircleProgressView'))return cls('ProgressBar');
  if(name.endsWith('Bitmap$Config'))return {ARGB_8888:{value:1}};
  if(name==='android.util.Base64')return {decode:()=>[1,2]};
  if(name==='android.graphics.BitmapFactory')return {decodeByteArray:()=>({...object(),recycle(){}})};
  return {$new:()=>object()};
 }
 const sandbox={console:{log:s=>logs.push(s)},activity,panel,View,Group,Image,Text,
  Paint:{$new:paint},Rect:{$new:object},Canvas:{$new:object},Clock:{elapsedRealtime:()=>1000},
  Bitmap:{createBitmap:()=>({...object(),recycle(){}})},Porter:{ADD:{value:1}},Blend:{$new:object},
  Inflater:{from:()=>({inflate:(id,p,attach)=>{assert.equal(p.kind,'ViewGroup');assert.equal(attach,false);return inflate(id);}})},
  Java:{use,retain:x=>x,array:(t,a)=>a,cast(value,type){
   const v=value.genericObject||value;
   assert(v.kind===type.name||type.name==='View','Invalid cast '+v.kind+' -> '+type.name);
   return v;
  }}};
 vm.createContext(sandbox);
 vm.runInContext(script.slice(0,script.indexOf('rpc.exports=')),sandbox);
 // Assign the actual script's class bindings without replacing the functions under test.
 for(const name of ['View','Group','Image','Text','Paint','Rect','Canvas','Clock','Bitmap','Porter','Blend','Inflater']){
  sandbox.testBinding=sandbox[name];vm.runInContext(name+'=testBinding',sandbox);
 }
 const run=s=>vm.runInContext(s,sandbox);
 run('mount(activity,panel)');
 const m=run('mounted');assert.equal(adds,1);assert.equal(m.image.visibility,4);
 run('originals(true)');assert.equal(factory.visibility,4);assert.equal(hidden.visibility,4);
 assert.equal(m.music.nodes.music_iv_type.scaleX,theme===4?0.4:1);
 assert.equal(m.music.nodes.music_tv_title.size,28.8);
 assert.equal(m.empty.nodes.music_tv_tips.text,'OEM:open_media_app');
 assert.equal(run("find(mounted.music,'absent')"),null);
 assert.equal(run("find(mounted.empty,'music_iv_cover')"),null);
 run("packet={frame:{package:'com.apple.android.music',source:'Apple Music',title:'Tamasha',artist:'Shadmehr',playing:false,position:1000,duration:3000,at:1000,cover:'abc'}};metadata();progress(mounted);geometry(mounted);");
 assert.equal(m.music.nodes.music_tv_song_name.text,'Tamasha');assert.equal(m.music.nodes.music_iv_stop.visibility,0);
 assert.equal(m.music.nodes.music_iv_type.drawable,'ICON:com.apple.android.music');assert(m.music.nodes.music_iv_cover.bitmap);
 assert.equal(m.music.w,490);assert.equal(m.empty.h,506);
 run('packet.frame.playing=true;metadata()');assert.equal(m.music.nodes.music_iv_stop.visibility,8);
 const image=m.image;panel.mThemeId.value=theme===4?1:4;
 run('refreshMusic(panel);metadata();geometry(mounted)');assert.equal(run('mounted.image'),image);assert.equal(adds,1);
 run('restore()');assert.equal(removes,1);assert.equal(factory.visibility,0);assert.equal(hidden.visibility,8);
}
for(const theme of [1,2,4,5])exercise(theme);
// Mutation test proves the old generic-return bug fails during REAL styleMusic/mount.
const broken=source.replace('return found?Java.cast(found,View):null;','return found;');
assert.notEqual(broken,source);assert.throws(()=>exercise(4,broken),/setScaleX.*not a function/);
console.log('PASS: mount/metadata/layout/theme refresh/restore in 4 themes, typed generic lookups, and regression mutation for missing View cast (mock Android)');
