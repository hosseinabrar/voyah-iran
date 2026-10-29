package com.abrar.voyahiran.core.bridge

/** A bounded cover IN the factory Home window: cannot cover another app, camera or OEM bars. */
internal object LauncherWakeCoverScript {
    val source = """
Java.perform(function () {
    const Global = Java.use('android.provider.Settings' + String.fromCharCode(36) + 'Global');
    const Clock = Java.use('android.os.SystemClock');
    const app = Java.use('android.app.ActivityThread').currentApplication();
    let activity = null, cover = null, decor = null, timer = null, generation = 0, eligibleAtSleep = false, wakeUntil = 0;
    function enabled() { return Global.getInt(app.getContentResolver(), 'voyah_iran_launcher_home', 0) === 1; }
    function valid() {
        return enabled() && ['voyah_iran_launcher_lease', 'voyah_iran_launcher_vip_lease'].every(function(key) {
            const remaining = Number(Global.getString(app.getContentResolver(), key)) - Date.now();
            return remaining > 0 && remaining <= 90000;
        });
    }
    function remove() {
        generation++;
        if (timer !== null) { clearTimeout(timer); timer = null; }
        try { if (cover !== null && decor !== null) decor.getOverlay().remove(cover); } catch(ignored) {}
        if (cover !== null) { cover.${'$'}dispose(); cover = null; }
        if (decor !== null) { decor.${'$'}dispose(); decor = null; }
    }
    function show() {
        try {
            if(typeof globalThis.VI_LAUNCHER_SCREEN_UP==='function' && !globalThis.VI_LAUNCHER_SCREEN_UP()){remove();return;}
            if (activity === null || !enabled() || (!valid() && Number(Clock.elapsedRealtime()) >= wakeUntil)) return;
            if (Number(activity.getWindowManager().getDefaultDisplay().getDisplayId()) !== 0 ||
                Number(Java.use('com.qinggan.launcher.base.BaseApplication').getMainPagePosition()) !== 0) { remove(); return; }
            remove();
            decor = Java.retain(Java.cast(activity.getWindow().getDecorView(), Java.use('android.view.ViewGroup')));
            const size = Java.use('android.graphics.Point').${'$'}new();
            activity.getWindowManager().getDefaultDisplay().getRealSize(size);
            const location = Java.array('int', [0, 0]); decor.getLocationOnScreen(location);
            const left = Math.max(0, Math.round(Number(size.x.value) * 145 / 1920) - location[0]);
            const top = Math.max(0, Math.round(Number(size.y.value) * 50 / 720) - location[1]);
            cover = Java.retain(Java.use('android.view.View').${'$'}new(activity));
            const light = String(Java.use('android.os.SystemProperties').get('persist.sys.qg.currenttheme', '')) === 'simple';
            cover.setBackgroundColor(light ? -1 : -16777216);
            cover.layout(left, top, Number(size.x.value) - location[0], Number(size.y.value) - location[1]);
            decor.getOverlay().add(cover);
            // Never leave an unusable Home covered after a bridge/startup failure.
            const current = generation;
            timer = setTimeout(function () { Java.scheduleOnMainThread(function () { if (current === generation) remove(); }); }, 5000);
        } catch(e) { remove(); console.log('VI_HOME_COVER fallback ' + e); }
    }
    function remember(value) {
        if (String(value.getClass().getName()) !== 'com.qinggan.app.launcher.activity.MainActivity') return false;
        remove();
        if (activity !== null) activity.${'$'}dispose();
        activity = Java.retain(value);
        return true;
    }
    globalThis.VI_HOME_COVER = show;
    globalThis.VI_HOME_UNCOVER = remove;
    try {
        const Lifecycle = Java.registerClass({
            name: 'com.abrar.voyahiran.bridge.HomeWakeLifecycle',
            implements: [Java.use('android.app.Application' + String.fromCharCode(36) + 'ActivityLifecycleCallbacks')],
            methods: {
                onActivityCreated: function(a, state) { remember(a); },
                onActivityStarted: function(a) {},
                onActivityResumed: function(a) {
                    // Some ROMs resume Home before delivering SCREEN_ON.
                    if (eligibleAtSleep) wakeUntil = Number(Clock.elapsedRealtime()) + 5000;
                    if (remember(a)) {
                        show();
                        if (typeof globalThis.VI_HOME_WAKE_CHECK === 'function') globalThis.VI_HOME_WAKE_CHECK();
                    }
                },
                onActivityPaused: function(a) {},
                onActivityStopped: function(a) {},
                onActivitySaveInstanceState: function(a, state) {},
                onActivityDestroyed: function(a) {
                    if (activity !== null && activity.equals(a)) { remove(); activity.${'$'}dispose(); activity = null; }
                }
            }
        });
        app.registerActivityLifecycleCallbacks(Lifecycle.${'$'}new());
        const Receiver = Java.registerClass({
            name: 'com.abrar.voyahiran.bridge.HomeWakeReceiver',
            superClass: Java.use('android.content.BroadcastReceiver'),
            methods: { onReceive: function(ctx, intent) {
                const action = String(intent.getAction());
                if (action === 'android.intent.action.SCREEN_OFF') {
                    eligibleAtSleep = valid();
                    if (eligibleAtSleep) show();
                } else {
                    wakeUntil = eligibleAtSleep ? Number(Clock.elapsedRealtime()) + 5000 : 0;
                    eligibleAtSleep = false;
                    show();
                    if (typeof globalThis.VI_HOME_WAKE_CHECK === 'function') globalThis.VI_HOME_WAKE_CHECK();
                }
            } }
        });
        const filter = Java.use('android.content.IntentFilter').${'$'}new('android.intent.action.SCREEN_OFF');
        filter.addAction('android.intent.action.SCREEN_ON');
        app.registerReceiver(Receiver.${'$'}new(), filter);
        Java.choose('com.qinggan.app.launcher.activity.MainActivity', {
            onMatch: function(a) { const held=Java.retain(a); Java.scheduleOnMainThread(function() {remember(held);held.${'$'}dispose();}); },
            onComplete: function() {}
        });
    } catch(e) { console.log('VI_HOME_COVER_UNAVAILABLE ' + e); }
});
""".trimIndent()
}
