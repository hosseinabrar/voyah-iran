package com.abrar.voyahiran.core.bridge

/** OEM launcher only; no system_server injection and no vehicle commands. */
internal object OemNavigationScript {
    val source = """
'use strict';
Java.perform(function () {
    try {
        const Drag = Java.use('com.qinggan.launcher.base.drag.DragService');
        const Base = Java.use('com.qinggan.launcher.base.BaseApplication');
        const Global = Java.use('android.provider.Settings' + String.fromCharCode(36) + 'Global');
        const AM = Java.use('android.app.ActivityManager');
        const original = Drag.onReceiveComponent.overload('java.lang.String');
        const dismiss = Drag.dismissIconView.overload();
        const update = Base.updateNavigationVisible.overload('java.lang.String', 'boolean', 'int', 'java.lang.String');
        let service = null;
        let previousPolicy = null;
        function remember(value) {
            if (service !== null) service.__DOLLAR__dispose();
            service = Java.retain(value);
        }
        function active(ctx) {
            const resolver = ctx.getContentResolver();
            const raw = Global.getString(resolver, 'voyah_iran_display_policy');
            const ackRaw = Global.getString(resolver, 'voyah_iran_display_policy_ack');
            const request = raw === null ? null : raw.toString();
            const ack = ackRaw === null ? '' : ackRaw.toString();
            return request === null ? ack === '1:default' :
                (request === '1' || /^1:[a-f0-9]{32}$/.test(request)) && ack === request;
        }
        function eligible(ctx, component) {
            const parts = String(component).split('/');
            if (parts.length !== 2 || !parts[0] || !parts[1]) return false;
            const info = ctx.getPackageManager().getApplicationInfo(parts[0], 0);
            return parts[0] === 'com.abrar.voyahiran' ||
                ((info.flags.value & 129) === 0 && info.uid.value >= 10000);
        }
        function show(ctx, component) {
            const parts = String(component).split('/');
            const activity = parts[1].startsWith('.') ? parts[0] + parts[1] : parts[1];
            const app = Java.cast(Base.getInstance(), Base);
            // Keep all navigation buttons and the OEM animation/touch handling intact.
            // Only suppress the floating-home replacement for eligible center-screen apps.
            update.call(app, parts[0], true, 2, activity);
            dismiss.call(ctx);
            console.log('VI_NAV_SHOW ' + component);
        }
        function apply(ctx, component) {
            if (active(ctx) && eligible(ctx, component)) {
                show(ctx, component);
                return;
            }
            original.call(ctx, component);
        }
        original.implementation = function (component) {
            remember(this);
            try { apply(this, component); }
            catch (error) {
                console.log('VI_NAV_EVENT_ERROR ' + error);
                original.call(this, component);
            }
        };
        function refresh() {
            if (service === null) return;
            const enabled = active(service);
            if (enabled === previousPolicy) return;
            const tasks = Java.cast(service.getSystemService('activity'), AM).getRunningTasks(32);
            for (let i = 0; i < tasks.size(); i++) {
                const task = Java.cast(tasks.get(i), Java.use('android.app.ActivityManager' + String.fromCharCode(36) + 'RunningTaskInfo'));
                if (task.displayId.value !== 0 || task.topActivity.value === null) continue;
                apply(service, task.topActivity.value.flattenToString().toString());
                previousPolicy = enabled;
                console.log('VI_NAV_POLICY enabled=' + enabled);
                return;
            }
        }
        Java.choose('com.qinggan.launcher.base.drag.DragService', {
            onMatch: function (instance) { remember(instance); return 'stop'; },
            onComplete: function () {
                Java.scheduleOnMainThread(function () {
                    try { refresh(); } catch (e) { console.log('VI_NAV_REFRESH_ERROR ' + e); }
                });
            }
        });
        // React to the existing display-policy checkbox, including disable, without restarting launcher.
        let queued = false;
        setInterval(function () {
            if (queued) return;
            queued = true;
            Java.scheduleOnMainThread(function () {
                try { refresh(); } catch (e) { console.log('VI_NAV_REFRESH_ERROR ' + e); }
                finally { queued = false; }
            });
        }, 1000);
        console.log('VI_NAV_READY');
    } catch (error) { console.log('VI_NAV_ERROR ' + error); }
});
""".trimIndent().replace("__DOLLAR__", "$")
}
