package com.abrar.voyahiran.core.bridge

/** H97A's OEM media serializer. No raw CAN frames and no vehicle-control methods. */
internal object ClusterMusicLegacyScript {
val source = """
'use strict';
let component=null, original=null, saved=null, latest=null, received=0, owned=false, stopped=false;
let Info, Src, State, Type, Clock, oemResumedAt = -1, lastKey = '';
function copy(info) {
    if(!info || !info.getSrcType()) return null;
    const result=Info.${'$'}new();
    result.setSrcType(info.getSrcType()); result.setMediaType(info.getMediaType());
    result.setMediaState(info.getMediaState()); result.setFrequency(info.getFrequency());
    result.setSongName(info.getSongName()); result.setArtist(info.getArtist()); result.setAlbumName(info.getAlbumName());
    return Java.retain(result);
}
function release() {
    if(!component || !owned) return;
    owned=false;lastKey='';
    if(saved) original.call(component,saved);
    else {
        const blank=Info.${'$'}new(); blank.setSrcType(Src.NetworkMusic.value);
        blank.setMediaType(Type.SimpleAudio.value); blank.setMediaState(State.CLEAN.value);
        original.call(component,blank);
    }
}
function apply() {
    if(!component) return;
    if(stopped || !latest || Date.now()-received>5000 || latest.playbackAt <= oemResumedAt) { release(); return; }
    const data=latest, key=JSON.stringify([data.package,data.title,data.artist,data.album,data.playing]);
    if(owned && key===lastKey) return;
    const info=Info.${'$'}new();
    info.setSrcType(Src.NetworkMusic.value); info.setMediaType(Type.SimpleAudio.value);
    info.setMediaState(data.playing ? State.PLAY.value : State.PAUSE.value);
    info.setSongName(data.title); info.setArtist(data.artist); info.setAlbumName(data.album || '');
    owned=true; original.call(component,info);lastKey=key;
}
function update() { Java.scheduleOnMainThread(function() { try { apply(); } catch(e) { latest=null;release();console.log('VI_CLUSTER_MUSIC_ERROR '+e); } }); }
rpc.exports={
    onFridaStdin:function(line) {
        if(line.trim()==='clear') latest=null;
        else { try { latest=JSON.parse(line);received=Date.now(); } catch(e) { latest=null; } }
        update();
    },
    dispose:function() { stopped=true;Java.perform(function(){if(original)original.implementation=null;update();}); }
};
Java.perform(function() {
    try {
        Info=Java.use('com.qinggan.canbus.MediaSrcInfo');Src=Java.use('com.qinggan.canbus.MediaSrcType');
        State=Java.use('com.qinggan.canbus.MediaState');Type=Java.use('com.qinggan.canbus.MediaType');
        Clock=Java.use('android.os.SystemClock');
        const klass=Java.use('com.qinggan.canbus.service.protocol.dongfeng.InstrumentInteractionComponent');
        original=klass.updateMediaSrcInfo.overload('com.qinggan.canbus.MediaSrcInfo');
        Java.choose(klass.${'$'}className, {
            onMatch:function(instance) { component=Java.retain(instance);saved=copy(instance.mLastMediaSrcInfo.value);return 'stop'; },
            onComplete:function() {
                if(!component) {console.log('VI_CLUSTER_MUSIC_UNSUPPORTED no legacy media instance');return;}
                original.implementation=function(info) {
                    if(component.equals(this)) {
                        if(saved)saved.${'$'}dispose(); saved=copy(info);
                        if(info && info.getMediaState() && info.getMediaState().equals(State.PLAY.value)) {
                            latest=null;owned=false;oemResumedAt=Number(Clock.elapsedRealtime());
                        }
                        // OEM updates remain untouched outside our short-lived ownership.
                        if(owned && latest && Date.now()-received<=5000) return;
                    }
                    return original.call(this,info);
                };
                console.log('VI_CLUSTER_MUSIC_READY');
                console.log('VI_CLUSTER_MUSIC_CAPABILITY legacy text/state only; OEM progress is a no-op');
            }
        });
    } catch(e) {stopped=true;console.log('VI_CLUSTER_MUSIC_UNSUPPORTED '+e);}
});
setInterval(function(){if(!stopped && owned && Date.now()-received>5000)update();},500);
""".trimIndent()
}
