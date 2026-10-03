package com.abrar.voyahiran.core.bridge

/** Only consumes the OEM central Home view. Passenger controls and factory bars are untouched. */
internal object LauncherHomeScript {
    val source = """
Java.perform(function () {
    const Global = Java.use('android.provider.Settings' + String.fromCharCode(36) + 'Global');
    const Intent = Java.use('android.content.Intent');
    const Options = Java.use('android.app.ActivityOptions');
    const View = Java.use('android.view.View');
    const click = View.performClick.overload();
    function allowed(ctx) {
        const until = Number(Global.getString(ctx.getContentResolver(), 'voyah_iran_launcher_lease'));
        const remaining = until - Date.now();
        return remaining > 0 && remaining <= 90000 && Global.getInt(ctx.getContentResolver(), 'voyah_iran_launcher_home', 0) === 1;
    }
    click.implementation = function () {
        try {
            const display = this.getDisplay();
            if (display !== null && display.getDisplayId() === 0 && this.getId() !== -1 &&
                this.getResources().getResourceEntryName(this.getId()).toString() === 'screen_up_home_icon' &&
                allowed(this.getContext())) {
                const intent = Intent.${'$'}new();
                intent.setClassName('com.abrar.voyahiran', 'com.abrar.voyahiran.feature.launcher.VoyahLauncherActivity');
                intent.addFlags(0x10000000 | 0x04000000);
                const options = Options.makeBasic(); options.setLaunchDisplayId(0);
                this.getContext().startActivity(intent, options.toBundle());
                console.log('VI_HOME central launcher opened');
                return true;
            }
        } catch(e) { console.log('VI_HOME fallback ' + e); }
        return click.call(this);
    };
    console.log('VI_HOME_READY');
    // Same factory MediaManager consumed by the CCC card. No notification parsing.
    try {
        const Manager = Java.use('com.qinggan.app.mediaCentre.MediaManager');
        const App = Java.use('android.app.ActivityThread');
        let last = '', previous = '', publishedAt = 0;
        setInterval(function () { Java.perform(function () {
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
        }); }, 1000);
    } catch(e) { console.log('VI_HOME_MEDIA_UNAVAILABLE ' + e); }
});
""".trimIndent()
}
