package com.abrar.voyahiran.core.bridge

/** Only consumes the OEM central Home view. Passenger controls and factory bars are untouched. */
internal object LauncherHomeScript {
    val source = """
Java.perform(function () {
    const Global = Java.use('android.provider.Settings' + String.fromCharCode(36) + 'Global');
    const Intent = Java.use('android.content.Intent');
    const Options = Java.use('android.app.ActivityOptions');
    function allowed(ctx) {
        const until = Number(Global.getString(ctx.getContentResolver(), 'voyah_iran_launcher_lease'));
        const remaining = until - Date.now();
        return remaining > 0 && remaining <= 90000 && Global.getInt(ctx.getContentResolver(), 'voyah_iran_launcher_home', 0) === 1;
    }
    // Readiness may arrive after OEM Home is already resumed. Observe both settings
    // instead of relying on a Home click or periodically stealing the foreground.
    let homeCheckPending = false, lastAutoOpen = 0;
    function checkFactoryHome(reason) {
        if (homeCheckPending) return;
        homeCheckPending = true;
        Java.scheduleOnMainThread(function () {
            homeCheckPending = false;
            try {
                const ctx = Java.use('android.app.ActivityThread').currentApplication();
                if (ctx === null || !allowed(ctx)) return;
                const top = String(Java.use('com.qinggan.launcher.base.utils.AppUtils').getTopAppInfo(ctx, 0, 4));
                const activity = top.indexOf('/') >= 0 ? top.substring(top.indexOf('/') + 1) : top;
                if (activity !== 'com.qinggan.app.launcher.activity.MainActivity' &&
                    !(top.indexOf('com.qinggan.app.launcher/') === 0 && activity === '.activity.MainActivity')) return;
                // MainActivity hosts BOTH Home and the app drawer. The OEM updates this
                // state before startActivity, earlier than onNewIntent/showAllApp.
                // Never replace an explicitly selected drawer with our Home.
                try {
                    if (Number(Java.use('com.qinggan.launcher.base.BaseApplication').getMainPagePosition()) !== 0) return;
                } catch(e) {
                    // Unknown OEM layout: leave the foreground alone; explicit Home still works.
                    return;
                }
                if (Date.now() - lastAutoOpen < 2000) return;
                const intent = Intent.${'$'}new();
                intent.setClassName('com.abrar.voyahiran', 'com.abrar.voyahiran.feature.launcher.VoyahLauncherActivity');
                intent.addFlags(0x10000000 | 0x04000000);
                const options = Options.makeBasic(); options.setLaunchDisplayId(0);
                ctx.startActivity(intent, options.toBundle());
                lastAutoOpen = Date.now();
                console.log('VI_HOME_AUTO opened reason=' + reason + ' top=' + top);
            } catch(e) { console.log('VI_HOME_AUTO fallback ' + e); }
        });
    }
    try {
        const Observer = Java.registerClass({
            name: 'com.abrar.voyahiran.bridge.LauncherReadyObserver',
            superClass: Java.use('android.database.ContentObserver'),
            methods: { onChange: [{returnType:'void', argumentTypes:['boolean'], implementation:function () {checkFactoryHome('settings');}}] }
        });
        const ctx = Java.use('android.app.ActivityThread').currentApplication();
        const handler = Java.use('android.os.Handler').${'$'}new(Java.use('android.os.Looper').getMainLooper());
        const observer = Observer.${'$'}new(handler);
        ['voyah_iran_launcher_lease', 'voyah_iran_launcher_home'].forEach(function(key) {
            ctx.getContentResolver().registerContentObserver(Global.getUriFor(key), false, observer);
        });
        checkFactoryHome('attach');
        console.log('VI_HOME_AUTO_READY');
    } catch(e) { console.log('VI_HOME_AUTO_UNAVAILABLE ' + e); }
    // Hook the actual OEM listener; framework performClick can be bypassed by ART compiled callers.
    try {
        const Bar = Java.use('com.qinggan.launcher.navigation.NavigationBarMain');
        const click = Bar.onClick.overload('android.view.View');
        click.implementation = function (view) {
            try {
                const name = view.getId() === -1 ? '' : String(view.getResources().getResourceEntryName(view.getId()));
                if (name === 'screen_up_home_icon' || name === 'screen_down_home_icon') {
                    const screen = Number(this.mScreenId.value);
                    const ctx = this.getContext();
                    const enabled = Global.getInt(ctx.getContentResolver(), 'voyah_iran_launcher_home', 0);
                    const remaining = Number(Global.getString(ctx.getContentResolver(), 'voyah_iran_launcher_lease')) - Date.now();
                    console.log('VI_HOME_CLICK display=' + screen + ' enabled=' + enabled + ' leaseMs=' + remaining);
                    if (screen === 0 && allowed(ctx)) {
                        const intent = Intent.${'$'}new();
                        intent.setClassName('com.abrar.voyahiran', 'com.abrar.voyahiran.feature.launcher.VoyahLauncherActivity');
                        intent.addFlags(0x10000000 | 0x04000000);
                        const options = Options.makeBasic(); options.setLaunchDisplayId(0);
                        ctx.startActivity(intent, options.toBundle());
                        console.log('VI_HOME central launcher opened');
                        return;
                    }
                }
            } catch(e) { console.log('VI_HOME fallback ' + e); }
            return click.call(this, view);
        };
        console.log('VI_HOME_READY listener=NavigationBarMain.onClick');
    } catch(e) { console.log('VI_HOME_UNAVAILABLE ' + e); }
    // OEM updateSelectedApp clears the radio groups for unknown packages. Treat only
    // our central HOME activity as Home, using the OEM RadioButtons/slider artwork.
    try {
        const Bar = Java.use('com.qinggan.launcher.navigation.NavigationBarMain');
        const Apps = Java.use('com.qinggan.launcher.base.utils.AppUtils');
        const selected = Bar.updateSelectedApp.overload('java.lang.String', 'java.lang.String');
        selected.implementation = function (pkg, activity) {
            let home = false;
            try {
                if (Number(this.mScreenId.value) === 0 && String(pkg) === 'com.abrar.voyahiran' && allowed(this.getContext())) {
                    const name = activity === null ? String(Apps.getTopAppInfo(this.getContext(), 0, 4)) : String(activity);
                    home = name === 'com.abrar.voyahiran.feature.launcher.VoyahLauncherActivity' ||
                        name === '.feature.launcher.VoyahLauncherActivity' ||
                        name.indexOf('/com.abrar.voyahiran.feature.launcher.VoyahLauncherActivity') >= 0 ||
                        name.indexOf('/.feature.launcher.VoyahLauncherActivity') >= 0;
                }
                if (home) {
                    this.mScreenUpHomeView.value.setChecked(true);
                    const down = this.mScreenDownHomeView.value;
                    if (down !== null) down.setChecked(true);
                    return;
                }
            } catch(e) { console.log('VI_HOME_SELECTION fallback ' + e); }
            const result = selected.call(this, pkg, activity);
            if (Number(this.mScreenId.value) === 0) checkFactoryHome('foreground');
            return result;
        };
        console.log('VI_HOME_SELECTION_READY');
    } catch(e) { console.log('VI_HOME_SELECTION_UNAVAILABLE ' + e); }
    // Same factory MediaManager consumed by the CCC card. No notification parsing.
    try {
        const Manager = Java.use('com.qinggan.app.mediaCentre.MediaManager');
        const App = Java.use('android.app.ActivityThread');
        let last = '', previous = '', publishedAt = 0;
        function publishMedia() { Java.perform(function () {
            try {
                const ctx = App.currentApplication();
                if (ctx === null || !allowed(ctx)) return;
                const m = Manager.getInstance(); const type = m.getCurMediaType();
                const info = m.getCurMediaInfo(null);
                const value = { source: type === null ? '' : type.toString(), playing: type !== null && m.isPlay(type, null),
                    title: info === null ? '' : String(info.getName() || ''), artist: info === null ? '' : String(info.getArtist() || ''),
                    frequency: info === null ? 0 : info.getRadioFrequency(), band: info === null ? '' : String(info.getRadioType() || ''),
                    cover: info === null ? '' : String(info.getCoverUrl() || ''), duration: info === null ? 0 : Number(info.getDuration()) };
                try {
                    const control=m.getMediaControl(type, false, null);
                    const browser=control === null ? null : control.getMediaBrowserHelper();
                    const controller=browser === null ? null : browser.getMediaController();
                    const state=controller === null ? null : controller.getPlaybackState();
                    if(state !== null) {value.position=Number(state.getPosition());value.positionAt=Number(state.getLastPositionUpdateTime());value.speed=state.getPlaybackSpeed();}
                } catch(ignored) {}
                const signature = JSON.stringify(value);
                if(signature === previous && Date.now() - publishedAt < 5000) return;
                // Changed values immediately, otherwise a bounded heartbeat, not a DB write every tick.
                value.at = Number(Java.use('android.os.SystemClock').elapsedRealtime());
                Global.putString(ctx.getContentResolver(), 'voyah_iran_launcher_media', JSON.stringify(value));
                previous = signature; publishedAt = Date.now();
            } catch(e) { if (String(e) !== last) { last = String(e); console.log('VI_HOME_MEDIA ' + e); } }
        }); }
        let publicationPending = false;
        function mediaChanged() {
            if (publicationPending) return;
            publicationPending = true;
            setTimeout(function () { publicationPending = false; publishMedia(); }, 0);
        }
        ['onMediaInfoChange', 'onMediaStateChange', 'onMediaOriginalStateChange',
         'onMediaTypeChange', 'onMediaConnected', 'onSessionEvent'].forEach(function (name) {
            try {
                Manager[name].overloads.forEach(function (method) {
                    method.implementation = function () {
                        const result = method.apply(this, arguments);
                        mediaChanged();
                        return result;
                    };
                });
            } catch(e) { console.log('VI_HOME_MEDIA_LISTENER ' + name + ' ' + e); }
        });
        publishMedia();
        // Lease/freshness heartbeat only; state changes publish immediately from callbacks.
        setInterval(publishMedia, 5000);
    } catch(e) { console.log('VI_HOME_MEDIA_UNAVAILABLE ' + e); }
});
""".trimIndent()
}
