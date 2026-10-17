'use strict';
// One ImageView per OEM panel lifetime. OFF never removes it.
let desired='off',shown='off',mounted=null,lastPing=Date.now(),generation=0;
let from=null,to=null,mix=1,fade=null;
let View,Group,Image,Frame,Bitmap,Canvas,Paint,Clock,Screen,Drawable;
const saved=[];let music,map;
function log(s){console.log('VI_IMAGE_TEST '+s);}
function originals(hide){
    const p=mounted.panel;
    for(let i=0;i<p.getChildCount();i++){
        const v=p.getChildAt(i);if(v.equals(mounted.image))continue;
        let entry=saved.find(x=>x.view.equals(v));
        if(!entry){entry={view:Java.retain(v),visibility:v.getVisibility()};saved.push(entry);}
        v.setVisibility(hide?4:entry.visibility);
    }
}
function draw(canvas){
    if(!to)return;
    const rect=Java.use('android.graphics.Rect').$new(0,0,512,512);
    const paint=Paint.$new(3);
    const count=canvas.saveLayer(0,0,512,512,null,31);
    if(from && mix<1){paint.setAlpha(Math.round(255*(1-mix)));canvas.drawBitmap(from,null,rect,paint);}
    paint.setAlpha(Math.round(255*mix));
    paint.setXfermode(Java.use('android.graphics.PorterDuffXfermode').$new(Java.use('android.graphics.PorterDuff$Mode').ADD.value));
    canvas.drawBitmap(to,null,rect,paint);canvas.restoreToCount(count);
}
function snapshot(){
    const bitmap=Bitmap.createBitmap(512,512,Java.use('android.graphics.Bitmap$Config').ARGB_8888.value);
    draw(Canvas.$new(bitmap));return bitmap;
}
function update(){
    const a=Screen.screenActivity.value;
    if(!a || a.isDestroyed())return;
    const p=a.panelMusicView.value;if(!p || p.mViewType.value!==0)return;
    if(mounted && !mounted.panel.equals(p)){
        // A destroyed/replaced factory panel requires one new view, never a toggle.
        originals(false);mounted.image.setAlpha(0);
        mounted.image.$dispose();mounted.panel.$dispose();mounted=null;
        saved.forEach(x=>x.view.$dispose());saved.length=0;shown='off';generation++;
    }
    if(!mounted){
        const image=Image.$new(p.getContext());image.setAlpha(0);image.setClickable(false);image.setFocusable(false);
        image.setScaleType(Java.use('android.widget.ImageView$ScaleType').FIT_CENTER.value);
        image.setImageDrawable(Drawable.$new());
        const params=Frame.$new(480,480);params.leftMargin.value=95;params.topMargin.value=167;
        p.addView(image,params);
        mounted={panel:Java.retain(p),image:Java.retain(image)};
        log('mounted one ImageView');
    }
    originals(desired!=='off');
    if(desired===shown)return;
    const previous=shown;shown=desired;
    const serial=++generation,start=Number(Clock.uptimeMillis()),alpha=Number(mounted.image.getAlpha());
    if(desired==='off' && from){to=snapshot();from=null;mix=1;}
    if(desired!=='off'){
        const next=desired==='map'?map:music;
        // Interrupted crossfades start from the currently rendered mixture.
        from=previous!=='off' && to?snapshot():null;to=next;mix=from?0:1;
    }
    const target=desired==='off'?0:1;
    log('transition '+previous+' -> '+desired+' alpha='+alpha);
    function frame(){Java.scheduleOnMainThread(function(){
        if(serial!==generation||!mounted)return;
        const t=Math.min(1,(Number(Clock.uptimeMillis())-start)/360),ease=t*t*(3-2*t);
        mounted.image.setAlpha(alpha+(target-alpha)*ease);
        if(from)mix=ease;
        mounted.image.getDrawable().invalidateSelf();
        if(t<1)setTimeout(frame,16);else {from=null;mix=1;log('settled '+shown+' alpha='+mounted.image.getAlpha());}
    });}frame();
}
rpc.exports={onFridaStdin:function(line){
    const value=line.trim();lastPing=Date.now();
    if(['map','music','off'].includes(value)){desired=value;Java.scheduleOnMainThread(update);}
},dispose:function(){desired='off';Java.scheduleOnMainThread(function(){if(mounted){originals(false);mounted.image.setAlpha(0);}});}};
Java.perform(function(){try{
    View=Java.use('android.view.View');Group=Java.use('android.view.ViewGroup');Image=Java.use('android.widget.ImageView');
    Frame=Java.use('android.widget.FrameLayout$LayoutParams');Bitmap=Java.use('android.graphics.Bitmap');Canvas=Java.use('android.graphics.Canvas');Paint=Java.use('android.graphics.Paint');Clock=Java.use('android.os.SystemClock');
    Screen=Java.use('com.qinggan.instrumentcard.ScreenActivity');
    const B64=Java.use('android.util.Base64'),Factory=Java.use('android.graphics.BitmapFactory');
    function decode(s){const b=B64.decode(s,0),raw=Factory.decodeByteArray(b,0,b.length);if(!raw)throw Error('PNG decode failed');const small=Bitmap.createScaledBitmap(raw,512,512,true);return Java.retain(small);}
    music=decode('__MUSIC__');map=decode('__MAP__');
    Drawable=Java.registerClass({name:'com.abrar.voyahclustertest.ImageBlendDrawable',superClass:Java.use('android.graphics.drawable.Drawable'),methods:{
        draw:[{returnType:'void',argumentTypes:['android.graphics.Canvas'],implementation:function(c){const b=this.getBounds(),save=c.save();c.translate(b.left.value,b.top.value);c.scale(b.width()/512,b.height()/512);draw(c);c.restoreToCount(save);}}],
        setAlpha:[{returnType:'void',argumentTypes:['int'],implementation:function(a){}}],
        setColorFilter:[{returnType:'void',argumentTypes:['android.graphics.ColorFilter'],implementation:function(f){}}],
        getOpacity:[{returnType:'int',argumentTypes:[],implementation:function(){return -3;}}],
        getIntrinsicWidth:[{returnType:'int',argumentTypes:[],implementation:function(){return 512;}}],
        getIntrinsicHeight:[{returnType:'int',argumentTypes:[],implementation:function(){return 512;}}]
    }});
    log('READY');
    setInterval(function(){if(Date.now()-lastPing>3000)desired='off';Java.scheduleOnMainThread(function(){try{update();}catch(e){log('ERROR '+e.stack);}});},250);
}catch(e){log('ERROR '+e.stack);}});
