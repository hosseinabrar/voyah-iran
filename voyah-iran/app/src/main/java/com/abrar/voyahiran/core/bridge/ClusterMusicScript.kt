package com.abrar.voyahiran.core.bridge

internal object ClusterMusicScript {
val source = """
'use strict';
// Inflate the installed ROM's own layouts in its own process/class loader. All factory
// children continue receiving their normal data; only their visibility is temporarily masked.
let latest = null, received = 0, mounted = null, themeHook = null, stopped = false;
let Panel, Screen, View, ViewGroup, TextView, ImageView, ProgressBar, Inflater, Base64, BitmapFactory, Clock;
function restore() {
    if (!mounted) return;
    const old = mounted; mounted = null;
    try {
        old.panel.removeView(old.view);
        old.children.forEach(function(item) { item.view.setVisibility(item.visibility); item.view.${'$'}dispose(); });
        old.view.${'$'}dispose(); old.panel.${'$'}dispose();
    } catch (e) { console.log('VI_CLUSTER_MUSIC_RESTORE ' + e); }
}
function find(view, name) {
    const context = view.getContext();
    const id = context.getResources().getIdentifier(name, 'id', context.getPackageName());
    const found = id ? view.findViewById(id) : null;
    return found ? Java.cast(found, View) : null;
}
function text(view, name, value) {
    const target = find(view, name);
    if (target) TextView.setText.overload('java.lang.CharSequence').call(
        Java.cast(target, TextView), Java.use('java.lang.String').${'$'}new(String(value || '')));
}
function time(value) {
    const seconds = Math.floor(Math.max(0, value) / 1000);
    return Math.floor(seconds / 60) + ':' + ('0' + seconds % 60).slice(-2);
}
function render() {
    if (stopped || !latest || Date.now() - received > 5000) { restore(); return; }
    const activity = Screen.screenActivity.value;
    if (!activity || activity.isFinishing() || activity.isDestroyed()) { restore(); return; }
    const panel = activity.panelMusicView.value;
    if (!panel || panel.mViewType.value !== 0) { restore(); return; }
    const theme = panel.mThemeId.value;
    const style = {1:'simple',2:'classic',4:'white',5:'golden'}[theme];
    if (!style) { restore(); return; }
    if (mounted && (!mounted.panel.equals(panel) || mounted.theme !== theme || mounted.view.getParent() === null)) restore();
    if (!mounted) {
        const context = panel.getContext(), resources = context.getResources();
        const id = resources.getIdentifier('layout_panel_media_music_' + style + '_view', 'layout', context.getPackageName());
        if (!id) throw new Error('OEM music layout unavailable: ' + style);
        const view = Inflater.from(context).inflate(id, Java.cast(panel, ViewGroup), false);
        // Validate before hiding any OEM UI; unknown ROM layouts remain untouched.
        if (!find(view, 'music_tv_song_name') || !find(view, 'music_iv_cover')) throw new Error('OEM music fields unavailable');
        const children = [];
        for (let i=0; i<panel.getChildCount(); i++) {
            const child = panel.getChildAt(i);
            children.push({view:Java.retain(child), visibility:child.getVisibility()});
        }
        panel.addView(view);
        mounted = {panel:Java.retain(panel),view:Java.retain(view),children:children,theme:theme,cover:null,pkg:null};
        children.forEach(function(item) { item.view.setVisibility(8); });
        console.log('VI_CLUSTER_MUSIC_MOUNTED theme=' + theme);
    }
    const view = mounted.view, data = latest;
    text(view, 'music_tv_title', data.source);
    text(view, 'music_tv_song_name', data.title);
    text(view, 'music_tv_artist_name', data.artist);
    const pause = find(view, 'music_iv_stop');
    if (pause) pause.setVisibility(data.playing ? 8 : 0);
    const duration = Math.min(2147483647, Math.max(0, data.duration));
    const delta = data.playing ? Math.max(0, Number(Clock.elapsedRealtime()) - data.at) : 0;
    const position = Math.min(duration, Math.max(0, data.position + delta));
    const progress = find(view, 'music_progress');
    if (progress) {
        // The ROM's simple layout uses CircleProgressView; all other supported
        // layouts use ProgressBar (including Golden's PathProgressView subclass).
        if (theme === 1)
            Java.cast(progress, Java.use('com.qinggan.cardview.panel.CircleProgressView')).setPercent(duration > 0 ? position / duration : 0);
        else { const bar = Java.cast(progress, ProgressBar); bar.setMax(duration); bar.setProgress(position); }
    }
    text(view, 'music_tv_play_time', time(position)); text(view, 'music_tv_total_time', time(duration));
    if (mounted.pkg !== data.package) {
        const icon = find(view, 'music_iv_type');
        if (icon) Java.cast(icon, ImageView).setImageDrawable(view.getContext().getPackageManager().getApplicationIcon(data.package));
        mounted.pkg = data.package;
    }
    if (mounted.cover !== data.cover) {
        const cover = Java.cast(find(view, 'music_iv_cover'), ImageView);
        let bitmap = null;
        if (data.cover) { const bytes = Base64.decode(data.cover, 0); bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length); }
        cover.setImageBitmap(bitmap); // Original ROM background remains visible when metadata has no cover.
        mounted.cover = data.cover;
    }
}
function update() {
    Java.scheduleOnMainThread(function() {
        try { render(); } catch(e) { latest=null; restore(); console.log('VI_CLUSTER_MUSIC_ERROR ' + (e.stack || e)); }
    });
}
rpc.exports = {
    onFridaStdin: function(line) {
        if (line.trim() === 'clear') latest = null;
        else { try { latest=JSON.parse(line);received=Date.now(); } catch(e) { latest=null; } }
        update();
    },
    dispose: function() { stopped=true; Java.perform(function() { if(themeHook) themeHook.implementation=null; update(); }); }
};
Java.perform(function() {
    try {
        Panel=Java.use('com.qinggan.cardview.panel.media.PanelView');
        Screen=Java.use('com.qinggan.instrumentcard.ScreenActivity');
        View=Java.use('android.view.View'); ViewGroup=Java.use('android.view.ViewGroup'); TextView=Java.use('android.widget.TextView');
        ImageView=Java.use('android.widget.ImageView'); ProgressBar=Java.use('android.widget.ProgressBar');
        Inflater=Java.use('android.view.LayoutInflater'); Base64=Java.use('android.util.Base64');
        BitmapFactory=Java.use('android.graphics.BitmapFactory'); Clock=Java.use('android.os.SystemClock');
        themeHook=Panel.onThemeChanged.overload('int','boolean');
        themeHook.implementation=function(theme, force) {
            restore(); const result=themeHook.call(this,theme,force);
            try { render(); } catch(e) { latest=null; restore(); console.log('VI_CLUSTER_MUSIC_ERROR ' + (e.stack || e)); }
            return result;
        };
        console.log('VI_CLUSTER_MUSIC_READY');
    } catch(e) { stopped=true; console.log('VI_CLUSTER_MUSIC_UNSUPPORTED ' + e); }
});
setInterval(function() { if(!stopped) update(); }, 500);
""".trimIndent()
}
