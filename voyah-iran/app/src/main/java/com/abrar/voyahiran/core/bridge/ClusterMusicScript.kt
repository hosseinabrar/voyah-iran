package com.abrar.voyahiran.core.bridge

internal object ClusterMusicScript {
val source = """
'use strict';
// Inflate the installed ROM's own layouts in its own process/class loader. All factory
// children continue receiving their normal data; their pixels are hidden while a
// translucent OEM carrier stays alive beneath the map.
let latest = null, received = 0, mounted = null, themeHook = null, createHook = null, stopped = false;
let mapMode = false, mapSettled = false, hiddenWindow = null, fadeSerial = 0, returningUntil = 0;
// Keep an actual translucent OEM surface alive. A zero-alpha whole window can
// be dropped by the vendor virtual-display compositor and flatten the mask to black.
function clearCarrier(item) {
    const saved=item.carrier;if(!saved)return;
    item.carrier=null;
    try {
        saved.decor.removeView(saved.dot);
        saved.children.forEach(function(child) {
            child.view.setAlpha(child.alpha);child.view.setVisibility(child.visibility);child.view.${'$'}dispose();
        });
        item.window.setBackgroundDrawable(saved.background);
        item.window.setFormat(saved.format);
    } finally {
        if(saved.background)saved.background.${'$'}dispose();
        saved.dot.${'$'}dispose();saved.decor.${'$'}dispose();
    }
}
function keepCarrier(item) {
    if(!item.carrier) {
        const decor=Java.cast(item.window.getDecorView(),Java.use('android.view.ViewGroup'));
        const background=decor.getBackground(), children=[];
        const dot=Java.use('android.view.View').${'$'}new(decor.getContext());
        item.carrier={decor:Java.retain(decor),background:background?Java.retain(background):null,
            format:item.window.getAttributes().format.value,children:children,dot:Java.retain(dot)};
        for(let i=0;i<decor.getChildCount();i++) {
            const child=decor.getChildAt(i);
            children.push({view:Java.retain(child),alpha:child.getAlpha(),visibility:child.getVisibility()});
            child.setAlpha(0);child.setVisibility(4); // INVISIBLE preserves layout, unlike GONE.
        }
        item.window.setBackgroundDrawable(Java.use('android.graphics.drawable.ColorDrawable').${'$'}new(0));
        item.window.setFormat(-3); // PixelFormat.TRANSLUCENT
        dot.setBackgroundColor(-16777216);dot.setClickable(false);dot.setFocusable(false);
        dot.setImportantForAccessibility(2);
        const params=Java.use('android.widget.FrameLayout${'$'}LayoutParams').${'$'}new(1,1);
        params.leftMargin.value=Math.round(decor.getWidth()*335/1920);
        params.topMargin.value=Math.round(decor.getHeight()*407/720);
        decor.addView(dot,params);
        console.log('VI_CLUSTER_MUSIC_CARRIER transparent with 1px anchor');
    }
    // Source updates may replace content without a theme callback.
    const saved=item.carrier;
    for(let i=0;i<saved.decor.getChildCount();i++) {
        const child=saved.decor.getChildAt(i);
        if(child.equals(saved.dot))continue;
        if(!saved.children.some(function(entry){return entry.view.equals(child);}))
            saved.children.push({view:Java.retain(child),alpha:child.getAlpha(),visibility:child.getVisibility()});
        child.setAlpha(0);
        // OEM media/title animations can restore alpha independently. Keep their
        // entire content branch invisible; only the carrier dot remains drawable.
        if(child.getVisibility()!==4)child.setVisibility(4);
    }
    const attrs=item.window.getAttributes();attrs.alpha.value=item.alpha;item.window.setAttributes(attrs);
}
function restoreWindow(animated) {
    if (!hiddenWindow) return;
    if (animated) { fadeWindow(hiddenWindow.alpha, 360); return; }
    ++fadeSerial;
    const old=hiddenWindow; hiddenWindow=null;
    try {
        clearCarrier(old);
        const attrs=old.window.getAttributes();
        attrs.alpha.value=old.alpha;
        old.window.setAttributes(attrs);
        console.log('VI_CLUSTER_MUSIC_WINDOW restored');
        console.log('VI_CLUSTER_MUSIC_VISIBLE');
    } finally { old.window.${'$'}dispose(); }
}
function fadeWindow(target, duration) {
    const item=hiddenWindow;
    if (!item) return;
    if (item.target === target) {
        // Theme/source callbacks must not reset a finished hidden window.
        if (target === 0 && item.done) {
            keepCarrier(item);
            console.log('VI_CLUSTER_MUSIC_HIDDEN');
        }
        return;
    }
    item.target=target;item.done=false;
    if(target!==0 && item.carrier) {
        const attrs=item.window.getAttributes();attrs.alpha.value=0;item.window.setAttributes(attrs);
        clearCarrier(item);
    }
    const serial=++fadeSerial, start=Date.now(), from=Number(item.window.getAttributes().alpha.value);
    function frame() {
        Java.scheduleOnMainThread(function() {
            if (hiddenWindow !== item || serial !== fadeSerial) return;
            try {
                const t=Math.max(0,Math.min(1,(Date.now()-start)/duration)), ease=t*t*(3-2*t);
                const attrs=item.window.getAttributes();
                attrs.alpha.value=from+(target-from)*ease;
                item.window.setAttributes(attrs);
                if(t<1) setTimeout(frame,16);
                else if(target===0) {
                    item.done=true;mapSettled=true;restore();keepCarrier(item);console.log('VI_CLUSTER_MUSIC_HIDDEN');
                } else {
                    hiddenWindow=null;item.window.${'$'}dispose();
                    console.log('VI_CLUSTER_MUSIC_WINDOW restored');
                    console.log('VI_CLUSTER_MUSIC_VISIBLE');
                }
            } catch(e) {
                restoreWindow(false);
                console.log('VI_CLUSTER_MUSIC_FADE_ERROR '+e);
            }
        });
    }
    frame();
}
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
    const next = String(value || '');
    // Keep marquee state across heartbeat frames; reset the cache with each layout.
    if (mounted.texts[name] === next) return;
    const target = find(view, name);
    if (target) {
        TextView.setText.overload('java.lang.CharSequence').call(
            Java.cast(target, TextView), Java.use('java.lang.String').${'$'}new(next));
        mounted.texts[name] = next;
    }
}
function time(value) {
    const seconds = Math.floor(Math.max(0, value) / 1000);
    return Math.floor(seconds / 60) + ':' + ('0' + seconds % 60).slice(-2);
}
function render() {
    if (mapMode && !stopped && Date.now()-received<=5000) {
        const activity=Screen.screenActivity.value;
        const window=activity && !activity.isDestroyed() ? activity.getWindow() : null;
        const replacement=hiddenWindow && (!window || !hiddenWindow.window.equals(window));
        if (replacement) restoreWindow(false);
        if (window) {
            const attrs=window.getAttributes();
            if (!hiddenWindow) {
                hiddenWindow={window:Java.retain(window),alpha:attrs.alpha.value};
                // A replacement window must not flash underneath an already visible map.
                if(replacement || mapSettled) {attrs.alpha.value=0;window.setAttributes(attrs);}
                console.log('VI_CLUSTER_MUSIC_WINDOW suppressed for map');
            }
            // Hide OEM contents, but retain a translucent carrier instead of a
            // permanently zero-alpha window. The anchor is covered by the map.
            fadeWindow(0,240);
        } else { restore();mapSettled=true;console.log('VI_CLUSTER_MUSIC_HIDDEN'); }
        return;
    }
    restoreWindow(!stopped && Date.now()-received<=5000);
    if(!hiddenWindow && !stopped) {
        const activity=Screen.screenActivity.value;
        if(activity && !activity.isDestroyed())console.log('VI_CLUSTER_MUSIC_VISIBLE');
    }
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
        const sourceTitle = find(view, 'music_tv_title');
        const sourceIcon = find(view, 'music_iv_type');
        if (sourceIcon && theme === 4) {
            sourceIcon.setScaleX(0.4);
            sourceIcon.setScaleY(0.4);
        }
        if (sourceTitle) {
            const title = Java.cast(sourceTitle, TextView);
            title.setTextSize(0, title.getTextSize() * 0.8); // PX: preserve the ROM's theme-specific scale.
            title.setSingleLine(true);
            title.setEllipsize(Java.use('android.text.TextUtils${'$'}TruncateAt').END.value);
            title.setSelected(false);
        }
        const children = [];
        for (let i=0; i<panel.getChildCount(); i++) {
            const child = panel.getChildAt(i);
            children.push({view:Java.retain(child), visibility:child.getVisibility()});
        }
        panel.addView(view);
        mounted = {panel:Java.retain(panel),view:Java.retain(view),children:children,theme:theme,cover:null,pkg:null,texts:Object.create(null)};
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
        if(mapMode && line.trim() !== 'map') returningUntil=Date.now()+650;
        if(mapMode !== (line.trim() === 'map')) mapSettled=false;
        mapMode = line.trim() === 'map';
        if (mapMode) { latest=null; received=Date.now(); update(); return; }
        if (line.trim() === 'clear') latest = null;
        else { try { latest=JSON.parse(line);received=Date.now(); } catch(e) { latest=null; } }
        update();
    },
    dispose: function() { stopped=true; Java.perform(function() { if(themeHook) themeHook.implementation=null; if(createHook) createHook.implementation=null; update(); }); }
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
            if(hiddenWindow && hiddenWindow.carrier) {
                const attrs=hiddenWindow.window.getAttributes();attrs.alpha.value=0;hiddenWindow.window.setAttributes(attrs);
                clearCarrier(hiddenWindow);
            }
            restore(); const result=themeHook.call(this,theme,force);
            try { render(); } catch(e) { latest=null; restore(); console.log('VI_CLUSTER_MUSIC_ERROR ' + (e.stack || e)); }
            return result;
        };
        createHook=Screen.onCreate.overload('android.os.Bundle');
        createHook.implementation=function(state) {
            const result=createHook.call(this,state);
            // Music has already been restored beneath the map before handoff.
            // Never hide a replacement music window on its first return frame.
            // A replacement OEM activity must be hidden before its first frame.
            try { render(); } catch(e) { console.log('VI_CLUSTER_MUSIC_ERROR ' + (e.stack || e)); }
            return result;
        };
        console.log('VI_CLUSTER_MUSIC_READY');
    } catch(e) { stopped=true; console.log('VI_CLUSTER_MUSIC_UNSUPPORTED ' + e); }
});
setInterval(function() { if(!stopped) update(); }, 500);
""".trimIndent()
}
