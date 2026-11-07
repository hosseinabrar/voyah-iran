package com.abrar.voyahiran.core.bridge

/** Only platform Java contracts; no obfuscated Apple classes or APK version assumptions. */
internal object AppleMusicHookScript {
    val source = """
'use strict';
Java.perform(function () {
    const ActivityThread = Java.use('android.app.ActivityThread');
    const app = ActivityThread.currentApplication();
    if (!app || String(app.getPackageName()) !== 'com.apple.android.music') throw Error('wrong package or application not ready');
    const File = Java.use('java.io.File');
    const paths = new Set(['/system/bin/su', '/system/xbin/su', '/system/app/Superuser.apk']);
    function hidden(file) {
        // Normalize aliases such as /system/bin/../bin/su without changing other files.
        let path;
        try { path=String(file.getCanonicalPath()); } catch (_) { path=String(file.getAbsolutePath()); }
        return paths.has(path);
    }
    for (const name of ['exists','isFile','canRead','canWrite','canExecute']) {
        const original=File[name].overload();
        original.implementation=function () { return hidden(this) ? false : original.call(this); };
    }
    const Build=Java.use('android.os.Build');
    if (Build.TAGS.value !== null) Build.TAGS.value=String(Build.TAGS.value).replace(/test-keys/g,'release-keys');
    console.log('VI_APPLE_ROOT_READY');
    // Injection can arrive after the initial check. Recreate the foreground activity
    // once in this process, without launching anything from the background.
    Java.scheduleOnMainThread(function () {
        try {
            const thread=ActivityThread.currentActivityThread();
            const records=thread.mActivities.value.values().toArray();
            for (let i=0;i<records.length;i++) {
                const record=Java.cast(records[i],Java.use('android.app.ActivityThread\u0024ActivityClientRecord'));
                if (!record.paused.value) {
                    const activity=record.activity.value;
                    if (!activity.isFinishing()) { activity.recreate();console.log('VI_APPLE_ROOT_REFRESHED'); }
                    break;
                }
            }
        } catch (e) { console.log('VI_APPLE_ROOT_REFRESH_FAILED '+e); }
    });
});
""".trimIndent()
}
