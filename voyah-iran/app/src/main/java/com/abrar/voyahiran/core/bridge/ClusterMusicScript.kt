package com.abrar.voyahiran.core.bridge

/** One final ImageView in the OEM window, fed by two live offscreen inputs. */
internal object ClusterMusicScript {
val source = """
'use strict';
let packet={owned:false},received=0,stopped=false,mounted=null,feed=null;
let Screen,View,Group,Image,Bitmap,Canvas,Paint,Clock,Inflater,Text,Handler,Looper,Rect,Porter;
let ownText=null,ownContext=null;
let Connection,Listener,themeHook,createHook,visibilityHook,handler,restoring=false,framePending=false;
let mapAlpha=0,blendFrom=0,blendTo=0,blendAt=0,lastLog=0;
let retrySession=null,retryAt=0,feedFailures=0;
const ids=Object.create(null);
const errors=Object.create(null);
function log(s){console.log('VI_CLUSTER_IMAGE '+s);}
function fail(where,e){const at=Date.now();if(errors[where]&&at-errors[where]<2000)return;errors[where]=at;log('ERROR '+where+' '+(e.stack||e));}
function now(){return Number(Clock.elapsedRealtime());}
function alpha(at){const t=Math.max(0,Math.min(1,(at-blendAt)/360));return blendFrom+(blendTo-blendFrom)*t*t*(3-2*t);}
function selectMap(show){
    const target=show?1:0;if(blendTo===target)return;
    const at=now();blendFrom=alpha(at);blendTo=target;blendAt=at;
    log('blend from='+blendFrom+' to='+target+' session='+packet.session);
}
function dispose(x){if(x)try{x.${'$'}dispose();}catch(e){}}
function recycle(x){if(x)try{x.recycle();dispose(x);}catch(e){}}
function find(v,name){
    let id=ids[name];
    if(id===undefined){const c=v.getContext();id=c.getResources().getIdentifier(name,'id',c.getPackageName());ids[name]=id;}
    // findViewById's generic return can be exposed as java.lang.Object by Frida.
    // Normalize here before any caller invokes View methods (scale/visibility/etc.).
    const found=id?v.findViewById(id):null;
    return found?Java.cast(found,View):null;
}
function text(v,name,value){
    const node=find(v,name);if(!node)return;
    const t=Java.cast(node,Text),s=String(value||'');
    if(String(t.getText())!==s)Text.setText.overload('java.lang.CharSequence').call(t,Java.use('java.lang.String').${'$'}new(s));
}
function resourceText(c,name){const r=c.getResources(),id=r.getIdentifier(name,'string',c.getPackageName());if(!id)throw Error('Missing OEM string '+name);return String(r.getString(id));}
function inflate(c,p,name){
    const id=c.getResources().getIdentifier(name,'layout',c.getPackageName());if(!id)throw Error('Missing OEM layout '+name);
    // MusicBaseView.initView inflates INTO a FrameLayout. Preserve the XML child's
    // fixed dimensions instead of measuring the ConstraintLayout as the whole panel.
    const root=Java.use('android.widget.FrameLayout').${'$'}new(c);
    Inflater.from(c).inflate(id,root,true);
    if(!ownText){
        ownContext=Java.retain(c.createPackageContext('com.abrar.voyahiran',3));
        ownText=Java.ClassFactory.get(ownContext.getClassLoader()).use('com.abrar.voyahiran.core.bridge.clustertext.ClusterTextAssets');
    }
    const count=ownText.install(root,ownContext);
    log('local text installed layout='+name+' count='+count+' font=cluster/text/semibold.ttf');
    return Java.retain(root);
}
function time(ms){return String(Java.use('com.qinggan.cardview.utils.Tools').convertMinutes(Math.floor(Math.max(0,ms))));}
function visibility(v,name,value){const node=find(v,name);if(node)node.setVisibility(value);}
function imageResource(v,name,res){const node=find(v,name);if(!node)return;const c=v.getContext();const id=c.getResources().getIdentifier(res,'mipmap',c.getPackageName());if(!id)throw Error('Missing OEM image '+res);Java.cast(node,Image).setImageResource(id);}
function styleMusic(v,theme){
    const originalCover=find(v,'music_iv_cover');
    if(originalCover){ownText.installCover(Java.cast(originalCover,Image),theme);log('local rounded cover installed theme='+theme);}
    // Keep OEM layout, plus the owner's title sizing and white-theme source badge.
    if(theme===5){visibility(v,'view_1',4);visibility(v,'view_2',4);}
    const titleNode=find(v,'music_tv_title');if(titleNode){const title=Java.cast(titleNode,Text);title.setTextSize(0,title.getTextSize()*0.8);
        title.setSingleLine(true);title.setEllipsize(Java.use('android.text.TextUtils${'$'}TruncateAt').END.value);title.setSelected(false);}
    if(theme===4){const sourceBadge=find(v,'music_iv_type');if(sourceBadge)ownText.installSourceBadge(Java.cast(sourceBadge,Image));}
    const icon=find(v,'music_iv_type');
    if(icon&&theme===4){
        const lp=Java.cast(icon.getLayoutParams(),Java.use('androidx.constraintlayout.widget.ConstraintLayout${'$'}LayoutParams'));
        // Match the classic XML badge (34dp, end 6.3dp, top 5.4dp), mirrored vertically.
        const density=v.getContext().getResources().getDisplayMetrics().density.value;
        lp.width.value=Math.round(34*density);lp.height.value=Math.round(34*density);
        lp.setMarginEnd(Math.round(6.3*density));lp.bottomMargin.value=Math.round(5.4*density);
        lp.startToStart.value=-1;lp.topToTop.value=-1;
        const coverId=v.getContext().getResources().getIdentifier('music_iv_cover','id',v.getContext().getPackageName());
        lp.endToEnd.value=coverId;lp.bottomToBottom.value=coverId;icon.setLayoutParams(lp);
        // Default View pivot is its center: shrink visually without moving the badge.
        icon.setScaleX(0.90);icon.setScaleY(0.90);
        icon.setTranslationX(-2);icon.setTranslationY(-2);
    }
    const cover=find(v,'music_iv_cover');if(cover)Java.cast(cover,Image).setScaleType(Java.use('android.widget.ImageView${'$'}ScaleType').CENTER_CROP.value);
    ['loading_group','loading_cover','loading','music_tv_disconnect'].forEach(name=>visibility(v,name,8));
    imageResource(v,'music_iv_cover',theme===5?'panel_golden_music_bg':'icon_music_'+({1:'simple',2:'classic',4:'white'}[theme])+'_bg');
}
function originals(hide){
    if(!mounted)return;const m=mounted;
    if(!hide&&!m.hideFactory)return; // Leave live OEM updates alone after handing back control.
    m.hideFactory=hide;
    // INVISIBLE preserves OEM geometry for the detached music layout.
    for(let i=0;i<m.panel.getChildCount();i++){
        const v=m.panel.getChildAt(i);if(v.equals(m.image))continue;
        let saved=m.saved.find(x=>x.view.equals(v));
        if(!saved){saved={view:Java.retain(v),visibility:v.getVisibility()};m.saved.push(saved);}
        v.setVisibility(hide?4:saved.visibility);
    }
    if(!hide){m.saved.forEach(x=>dispose(x.view));m.saved=[];}
}
function closeFeed(){
    const f=feed;feed=null;if(!f)return;
    try{if(f.bound)f.context.unbindService(f.connection);}catch(e){fail('unbind',e);}
    try{f.reader.setOnImageAvailableListener(null,null);}catch(e){fail('reader-listener-close',e);}
    try{f.reader.close();}catch(e){fail('reader-close',e);}
    recycle(f.bitmap);dispose(f.bytes);dispose(f.reader);dispose(f.connection);dispose(f.listener);dispose(f.context);
}
function currentConnection(connection){
    return feed && feed.connection.equals(connection) && packet.owned && !stopped &&
        packet.session===feed.session && Date.now()-received<=5000;
}
function retryFeed(f,reason,error){
    if(feed!==f)return;
    const delay=Math.min(5000,500*Math.pow(2,Math.min(feedFailures++,4)));
    retryAt=now()+delay;
    log('connection retry reason='+reason+' session='+f.session+' attempt='+feedFailures+' delayMs='+delay);
    if(error)fail('connection-'+reason,error);
    closeFeed(); // Only the map input closes; the shared image/music stays mounted.
}
function connectionLost(connection,reason){
    if(!currentConnection(connection)){log('ignored stale callback '+reason);return;}
    retryFeed(feed,reason);
}
function connectionReady(connection,binder){
    if(!currentConnection(connection)){log('ignored stale callback connected');return;}
    const f=feed;
    try{sendSurface(f,binder);f.connected=true;f.deadline=now()+5000;}
    catch(e){retryFeed(f,'surface-send',e);}
}
function restore(){
    closeFeed();retrySession=null;retryAt=0;feedFailures=0;const m=mounted;if(!m)return;
    restoring=true;try{originals(false);}finally{restoring=false;}mounted=null;m.panel.removeView(m.image);
    m.saved.forEach(x=>dispose(x.view));
    m.image.setImageDrawable(null);
    [m.image,m.panel,m.window,m.music,m.empty,m.canvas,m.outputCanvas,m.paint,m.addPaint,m.mapSrc,m.mapDst].forEach(dispose);
    recycle(m.musicBitmap);recycle(m.outputBitmap);recycle(m.coverBitmap);recycle(m.previousMusic);
    blendFrom=0;blendTo=0;blendAt=0;mapAlpha=0;
    log('released image; factory restored');
}
function mount(a,p){
    const theme=p.mThemeId.value,style={1:'simple',2:'classic',4:'white',5:'golden'}[theme];
    if(!style)throw Error('Unsupported theme '+theme);
    const c=p.getContext(),window=a.getWindow();
    const music=inflate(c,p,'layout_panel_media_music_'+style+'_view');
    const empty=inflate(c,p,'layout_panel_no_media_'+style+'_view');
    styleMusic(music,theme);
    text(empty,'music_tv_title',resourceText(c,'title_music'));text(empty,'music_tv_tips',resourceText(c,'open_media_app'));
    const image=Image.${'$'}new(c);image.setClickable(false);image.setFocusable(false);image.setImportantForAccessibility(2);
    image.setScaleType(Java.use('android.widget.ImageView${'$'}ScaleType').FIT_XY.value);
    // A plain bitmap consumer: no custom Drawable, blending or visibility work in onDraw.
    image.setLayerType(1,null);
    const paint=Paint.${'$'}new(3),add=Paint.${'$'}new(3);
    add.setXfermode(Java.use('android.graphics.PorterDuffXfermode').${'$'}new(Porter.ADD.value));
    mounted={panel:Java.retain(p),window:Java.retain(window),image:Java.retain(image),
        theme:theme,music:music,empty:empty,saved:[],paint:Java.retain(paint),addPaint:Java.retain(add),
        musicBitmap:null,canvas:null,outputBitmap:null,outputCanvas:null,w:0,h:0,cover:null,coverBitmap:null,pkg:null,
        previousMusic:null,musicIdentity:null,musicFadeAt:0,musicDrawn:false,
        location:Java.array('int',[0,0]),mapSrc:Java.retain(Rect.${'$'}new(0,0,1920,720)),
        mapDst:Java.retain(Rect.${'$'}new(0,0,1920,720)),ready:false,frames:0,maxDrawMs:0};
    p.addView(image,Java.use('android.widget.FrameLayout${'$'}LayoutParams').${'$'}new(-1,-1));
    image.setVisibility(4);log('mounted SINGLE image theme='+theme);
}
function refreshMusic(p){
    // A theme/media-source change may replace the factory panel or its children,
    // but must never replace our final image or interrupt the map Surface.
    const m=mounted,theme=p.mThemeId.value,style={1:'simple',2:'classic',4:'white',5:'golden'}[theme];
    if(!style)throw Error('Unsupported theme '+theme);
    const c=p.getContext(),music=inflate(c,p,'layout_panel_media_music_'+style+'_view');
    const empty=inflate(c,p,'layout_panel_no_media_'+style+'_view');
    styleMusic(music,theme);
    text(empty,'music_tv_title',resourceText(c,'title_music'));text(empty,'music_tv_tips',resourceText(c,'open_media_app'));
    dispose(m.music);dispose(m.empty);dispose(m.panel);recycle(m.coverBitmap);
    m.music=music;m.empty=empty;m.panel=Java.retain(p);m.theme=theme;m.textDiagnostics=false;
    m.cover=null;m.coverBitmap=null;m.pkg=null;
    recycle(m.previousMusic);m.previousMusic=null;m.musicIdentity=null;m.musicDrawn=false;
    log('theme/panel refreshed; SAME image and Surface theme='+theme);
}
function geometry(m){
    const w=m.panel.getWidth(),h=m.panel.getHeight();if(w<=0||h<=0)return false;
    if(w!==m.w||h!==m.h){
        m.image.setImageDrawable(null);
        recycle(m.previousMusic);m.previousMusic=null;m.musicIdentity=null;m.musicDrawn=false;
        recycle(m.musicBitmap);recycle(m.outputBitmap);dispose(m.canvas);dispose(m.outputCanvas);m.w=w;m.h=h;
        m.musicBitmap=Java.retain(Bitmap.createBitmap(w,h,Java.use('android.graphics.Bitmap${'$'}Config').ARGB_8888.value));
        m.canvas=Java.retain(Canvas.${'$'}new(m.musicBitmap));
        m.outputBitmap=Java.retain(Bitmap.createBitmap(w,h,Java.use('android.graphics.Bitmap${'$'}Config').ARGB_8888.value));
        m.outputCanvas=Java.retain(Canvas.${'$'}new(m.outputBitmap));
    }
    [m.music,m.empty].forEach(v=>{if(v.isLayoutRequested()||v.getWidth()!==w||v.getHeight()!==h){v.measure(1073741824+w,1073741824+h);v.layout(0,0,w,h);}});
    if(!m.textDiagnostics){
        ['music_tv_title','music_tv_song_name','music_tv_artist_name'].forEach(id=>{
            const v=find(m.music,id);if(!v){log('local text missing id='+id);return;}
            const t=Java.cast(v,Java.use('android.widget.TextView'));
            log('local text id='+id+' class='+t.getClass().getName()+' bounds='+t.getLeft()+','+t.getTop()+','+t.getWidth()+'x'+t.getHeight()+' visibility='+t.getVisibility()+' alpha='+t.getAlpha()+' color='+t.getCurrentTextColor()+' length='+t.getText().length()+' font='+t.getTypeface());
        });m.textDiagnostics=true;
    }
    m.panel.getLocationInWindow(m.location);return true;
}
function metadata(){
    if(!mounted)return;const m=mounted,d=packet.frame;if(!d||d.idle)return;
    const source=d.oemSource||'',radio=['DAB','XMLA_MUSIC','RADIO_YUNTING','LOCAL_RADIO'].indexOf(source)>=0;
    text(m.music,'music_tv_title',d.source||resourceText(m.music.getContext(),radio?'title_radio':'title_music'));
    text(m.music,'music_tv_song_name',d.title||resourceText(m.music.getContext(),'unknown'));
    text(m.music,'music_tv_artist_name',d.artist||resourceText(m.music.getContext(),'unknown'));
    const pause=find(m.music,'music_iv_stop');if(pause)pause.setVisibility(d.playing?8:0);
    const sourceKey=(d.package||'')+':'+source;
    if(m.pkg!==sourceKey){
        const icon=find(m.music,'music_iv_type');
        const nativeIcon={BT_MUSIC:'bluetooth',SYSTEM_BT_MUSIC:'bluetooth',USB_MUSIC:'usb',KW_MUSIC:'kuwo',WECAR_FLOW:'wecar',XMLA_MUSIC:'xmly',RADIO_YUNTING:'yunting',DAB:'dab'}[source];
        if(nativeIcon){const suffix={1:'simple',2:'classic',4:'white',5:'new'}[m.theme];const prefix=nativeIcon==='xmly'&&(m.theme===2||m.theme===5)?'icon_radio_':'icon_music_';imageResource(m.music,'music_iv_type',prefix+nativeIcon+'_'+(nativeIcon==='kuwo'&&m.theme===1?'sipmle':suffix));}
        else if(icon){const iv=Java.cast(icon,Image);try{iv.setImageDrawable(m.music.getContext().getPackageManager().getApplicationIcon(d.package));}catch(e){iv.setImageDrawable(null);}}
        m.pkg=sourceKey;
    }
    if(m.cover!==d.cover){
        let bitmap=null;
        if(d.cover){const bytes=Java.use('android.util.Base64').decode(d.cover,0);bitmap=Java.use('android.graphics.BitmapFactory').decodeByteArray(bytes,0,bytes.length);}
        const iv=find(m.music,'music_iv_cover');if(iv){if(bitmap)Java.cast(iv,Image).setImageBitmap(bitmap);
            else imageResource(m.music,'music_iv_cover',m.theme===5?'panel_golden_music_bg':'icon_music_'+({1:'simple',2:'classic',4:'white'}[m.theme])+'_bg');}
        recycle(m.coverBitmap);m.coverBitmap=bitmap?Java.retain(bitmap):null;m.cover=d.cover;
    }
}
function progress(m){
    const d=packet.frame;if(!d||d.idle)return;
    const duration=Math.min(2147483647,Math.max(0,d.duration||0));
    const show=duration>0&&!d.radio;
    ['music_tv_play_time','music_tv_total_time'].forEach(name=>visibility(m.music,name,show?0:(m.theme===5?8:4)));
    visibility(m.music,'music_progress',d.radio||(!show&&(m.theme===1||m.theme===5))?8:0);
    visibility(m.music,'music_progress_reflection',show?0:8);
    const position=Math.min(duration,Math.max(0,(d.position||0)+(d.playing?Math.max(0,now()-d.at):0)));
    const node=find(m.music,'music_progress');
    if(node){if(m.theme===1)Java.cast(node,Java.use('com.qinggan.cardview.panel.CircleProgressView')).setPercent(duration>0?position/duration:0);
        else{const bar=Java.cast(node,Java.use('android.widget.ProgressBar'));bar.setMax(duration);bar.setProgress(position);}}
    text(m.music,'music_tv_play_time',show?time(position):'');text(m.music,'music_tv_total_time',show?time(duration):'');
}
function musicIdentity(d){
    if(!d||d.idle)return ['idle'];
    // Playback ticks, pause/resume and navigation changes are not track changes.
    return [d.package||'',d.oemSource||'',d.source||'',d.title||'',d.artist||'',d.cover||''];
}
function sameMusic(a,b){return a!==null&&a.length===b.length&&a.every((v,i)=>v===b[i]);}
function drawMusic(m,enabled){
    if(!enabled){recycle(m.previousMusic);m.previousMusic=null;m.musicIdentity=null;m.musicDrawn=false;m.canvas.drawColor(0,Porter.CLEAR.value);return;}
    const identity=musicIdentity(packet.frame),at=now();
    if(!sameMusic(m.musicIdentity,identity)){
        recycle(m.previousMusic);m.previousMusic=null;
        // Snapshot the last displayed blend, so rapid skips do not jump back.
        if(m.musicDrawn){m.previousMusic=Java.retain(m.musicBitmap.copy(Java.use('android.graphics.Bitmap${'$'}Config').ARGB_8888.value,false));m.musicFadeAt=at;}
        m.musicIdentity=identity;
    }
    const t=Math.max(0,Math.min(1,(at-m.musicFadeAt)/320)),mix=m.previousMusic?t*t*(3-2*t):1;
    m.canvas.drawColor(0,Porter.CLEAR.value);
    const view=packet.frame&&!packet.frame.idle?m.music:m.empty;
    if(mix>=1){view.draw(m.canvas);recycle(m.previousMusic);m.previousMusic=null;}
    else {
        const layer=m.canvas.saveLayerAlpha(0,0,m.w,m.h,Math.round(255*mix));
        view.draw(m.canvas);m.canvas.restoreToCount(layer);
        // Weighted premultiplied sum preserves brightness, including shared pixels.
        m.addPaint.setAlpha(255-Math.round(255*mix));m.canvas.drawBitmap(m.previousMusic,0,0,m.addPaint);
    }
    m.musicDrawn=true;
}
function renderFrame(){
    const m=mounted;if(!m)return;
    const started=now();
    if(!packet.owned||stopped||Date.now()-received>5000||!geometry(m))return;
    const musicEnabled=!!packet.frame&&packet.frame.musicEnabled===true;
    const mapVisible=packet.map===true&&!!feed&&feed.received;
    // Readiness describes the output surface, not whether music is enabled.
    if(!m.ready){m.ready=true;console.log('VI_CLUSTER_MUSIC_VISIBLE');}
    if(!musicEnabled){
        blendFrom=blendTo=mapAlpha=mapVisible?1:0;blendAt=now();
        originals(mapVisible);m.image.setVisibility(mapVisible?0:4);
        if(!mapVisible)return;
    }else{
        originals(true);m.image.setVisibility(0);progress(m);
    }
    // Always clear old pixels, including text and artwork from the previous song.
    drawMusic(m,musicEnabled);
    selectMap(mapVisible);mapAlpha=alpha(now());
    const c=m.outputCanvas,x=Number(m.location[0]),y=Number(m.location[1]);
    m.mapDst.set(-x,-y,1920-x,720-y);
    c.drawColor(0,Porter.CLEAR.value);
    // Stable states copy ONE input. Only intermediate frames mix, offscreen.
    if(musicEnabled&&mapAlpha<1){m.paint.setAlpha(Math.round(255*(1-mapAlpha)));c.drawBitmap(m.musicBitmap,0,0,m.paint);}
    if(feed&&feed.bitmap&&mapAlpha>0){
        const paint=mapAlpha===1?m.paint:m.addPaint;paint.setAlpha(Math.round(255*mapAlpha));
        c.drawBitmap(feed.bitmap,m.mapSrc,m.mapDst,paint);
    }
    m.paint.setColor(-16777216);m.paint.setAlpha(255);
    c.drawRect(m.w/2,m.h/2,m.w/2+1,m.h/2+1,m.paint);
    // The factory renderer receives only this finished image, exactly one output.
    m.image.setImageBitmap(m.outputBitmap);
    m.frames++;m.maxDrawMs=Math.max(m.maxDrawMs,now()-started);
    if(!m.ready){m.ready=true;console.log('VI_CLUSTER_MUSIC_VISIBLE');}
    if(now()-lastLog>5000){lastLog=now();log('output='+(mapAlpha===0?'music':mapAlpha===1?'map':'fade')+' singleBitmap=true musicAlpha='+(1-mapAlpha)+' mapAlpha='+mapAlpha+' target='+blendTo+' mapFrames='+(feed?feed.frames:0)+' idle='+(!packet.frame||packet.frame.idle)+' panel='+m.w+'x'+m.h+' at='+m.location[0]+','+m.location[1]+' drawMaxMs='+m.maxDrawMs+' copyMaxMs='+(feed?feed.maxCopyMs:0)+' frames='+m.frames);m.maxDrawMs=0;if(feed)feed.maxCopyMs=0;}
}
function sendSurface(f,binder){
    const Parcel=Java.use('android.os.Parcel'),data=Parcel.obtain(),reply=Parcel.obtain();
    try{data.writeInterfaceToken('com.abrar.voyahiran.ClusterImageSurface');data.writeString(f.session);
        f.reader.getSurface().writeToParcel(data,0);
        if(!binder.transact(1,data,reply,0))throw Error('Surface transaction rejected');reply.readException();log('Surface sent session='+f.session);
    }finally{data.recycle();reply.recycle();}
}
function ensureFeed(){
    const session=packet.session||null;
    if(retrySession!==session){closeFeed();retrySession=session;retryAt=0;feedFailures=0;}
    if(!session||!mounted||!packet.owned||stopped||Date.now()-received>5000)return;
    if(feed){
        if(!feed.received && now()>=feed.deadline)retryFeed(feed,feed.connected?'first-frame-timeout':'bind-timeout');
        return;
    }
    if(now()<retryAt)return;
    const c=mounted.panel.getContext().getApplicationContext(),reader=Java.use('android.media.ImageReader').newInstance(1920,720,1,3);
    const f={session:session,reader:Java.retain(reader),context:Java.retain(c),connection:Java.retain(Connection.${'$'}new()),
        listener:Java.retain(Listener.${'$'}new()),bitmap:null,bytes:null,stride:0,received:false,frames:0,maxCopyMs:0,bound:false,
        connected:false,deadline:now()+3000};
    feed=f;
    try{
        reader.setOnImageAvailableListener(f.listener,handler);
        const intent=Java.use('android.content.Intent').${'$'}new();intent.setClassName('com.abrar.voyahiran','com.abrar.voyahiran.feature.launcher.ClusterImageService');
        // Android requires cleanup even for unsuccessful binding attempts.
        f.bound=true;
        if(!c.bindService(intent,f.connection,1))retryFeed(f,'bind-rejected');
        else log('binding session='+session);
    }catch(e){retryFeed(f,'bind-exception',e);}
}
function receive(reader){
    let image=null;
    const started=now();
    try{
        image=reader.acquireLatestImage();if(!image)return;
        const f=feed;if(!f||!f.reader.equals(reader))return;
        const plane=image.getPlanes()[0],stride=plane.getRowStride(),pixel=plane.getPixelStride();
        if(pixel!==4||stride%4!==0)throw Error('Unexpected RGBA stride='+stride+' pixel='+pixel);
        if(!f.bitmap||f.stride!==stride){recycle(f.bitmap);dispose(f.bytes);
            f.bitmap=Java.retain(Bitmap.createBitmap(stride/4,720,Java.use('android.graphics.Bitmap${'$'}Config').ARGB_8888.value));
            f.bytes=Java.retain(Java.use('java.nio.ByteBuffer').allocateDirect(stride*720));f.stride=stride;}
        const buffer=plane.getBuffer();buffer.rewind();
        // Some gralloc implementations omit trailing padding in the final row.
        if(buffer.remaining()>=stride*720)f.bitmap.copyPixelsFromBuffer(buffer);
        else{f.bytes.clear();f.bytes.put.overload('java.nio.ByteBuffer').call(f.bytes,buffer);f.bytes.rewind();f.bitmap.copyPixelsFromBuffer(f.bytes);}
        f.received=true;f.frames++;if(f.frames===1){feedFailures=0;retryAt=0;log('FIRST_MAP_FRAME session='+f.session+' stride='+stride);}
        f.maxCopyMs=Math.max(f.maxCopyMs,now()-started);
    }catch(e){fail('map-frame',e);}finally{if(image)image.close();}
}
function update(){
    if(stopped||!packet.owned||Date.now()-received>5000){restore();return;}
    const a=Screen.screenActivity.value;if(!a||a.isFinishing()||a.isDestroyed()){restore();return;}
    const p=a.panelMusicView.value;if(!p||p.mViewType.value!==0)return;
    if(mounted&&(!mounted.window.equals(a.getWindow())||!mounted.panel.equals(p)))restore();
    // OEM theme changes can remove its children; reattach the SAME output image.
    if(mounted&&mounted.image.getParent()===null)p.addView(mounted.image,Java.use('android.widget.FrameLayout${'$'}LayoutParams').${'$'}new(-1,-1));
    if(mounted&&(!mounted.panel.equals(p)||mounted.theme!==p.mThemeId.value))refreshMusic(p);
    if(!mounted)mount(a,p);
    metadata();ensureFeed();
}
function schedule(){Java.scheduleOnMainThread(function(){try{update();}catch(e){fail('update',e);}});}
rpc.exports={onFridaStdin:function(line){
    try{packet=line.trim()==='clear'?{owned:false}:JSON.parse(line);received=Date.now();}catch(e){fail('packet',e);return;}schedule();
},dispose:function(){stopped=true;Java.perform(function(){if(themeHook)themeHook.implementation=null;if(createHook)createHook.implementation=null;if(visibilityHook)visibilityHook.implementation=null;schedule();});}};
Java.perform(function(){try{
    Screen=Java.use('com.qinggan.instrumentcard.ScreenActivity');View=Java.use('android.view.View');Group=Java.use('android.view.ViewGroup');Image=Java.use('android.widget.ImageView');
    Bitmap=Java.use('android.graphics.Bitmap');Canvas=Java.use('android.graphics.Canvas');Paint=Java.use('android.graphics.Paint');Rect=Java.use('android.graphics.Rect');
    Porter=Java.use('android.graphics.PorterDuff${'$'}Mode');Clock=Java.use('android.os.SystemClock');Inflater=Java.use('android.view.LayoutInflater');Text=Java.use('android.widget.TextView');
    Handler=Java.use('android.os.Handler');Looper=Java.use('android.os.Looper');handler=Java.retain(Handler.${'$'}new(Looper.getMainLooper()));
    const suffix=String(Date.now());
    Connection=Java.registerClass({name:'com.abrar.voyahiran.ClusterImageConnection'+suffix,implements:[Java.use('android.content.ServiceConnection')],methods:{
        onServiceConnected:function(name,binder){connectionReady(this,binder);},
        onServiceDisconnected:function(name){connectionLost(this,'disconnected');},
        onNullBinding:function(name){connectionLost(this,'null-binding');},
        onBindingDied:function(name){connectionLost(this,'binding-died');}
    }});
    Listener=Java.registerClass({name:'com.abrar.voyahiran.ClusterImageListener'+suffix,implements:[Java.use('android.media.ImageReader${'$'}OnImageAvailableListener')],methods:{onImageAvailable:function(r){receive(r);}}});
    visibilityHook=View.setVisibility.overload('int');
    visibilityHook.implementation=function(value){
        if(mounted&&!restoring&&mounted.hideFactory&&packet.owned&&mounted.saved.some(x=>x.view.equals(this)))value=4;
        return visibilityHook.call(this,value);
    };
    const Panel=Java.use('com.qinggan.cardview.panel.media.PanelView');themeHook=Panel.onThemeChanged.overload('int','boolean');
    themeHook.implementation=function(theme,force){const result=themeHook.call(this,theme,force);try{update();}catch(e){fail('theme',e);}return result;};
    createHook=Screen.onCreate.overload('android.os.Bundle');
    createHook.implementation=function(state){const result=createHook.call(this,state);try{update();}catch(e){fail('create',e);}return result;};
    console.log('VI_CLUSTER_MUSIC_READY');setInterval(function(){if(!stopped)schedule();},500);
    setInterval(function(){
        if(stopped||!mounted||framePending)return;
        framePending=true;
        Java.scheduleOnMainThread(function(){try{renderFrame();}catch(e){fail('render-frame',e);}finally{framePending=false;}});
    },16);
}catch(e){stopped=true;fail('init',e);}});
""".trimIndent()
}
