'use strict';
// Voyah Iran ROA switch, Frida 16.7.19. Scoped to the OEM Camera process.
let lastHeartbeat = Date.now();
let enabled = true;
let cleanup = function () { return Promise.resolve(); };
rpc.exports = {
    onFridaStdin: function (data) {
        const command = data.trim();
        if (command === 'ping') { lastHeartbeat = Date.now(); return; }
        if (command === 'disable') return cleanup();
    },
    dispose: function () { return cleanup(); }
};
Java.perform(function () {
    try {
        const Activity = Java.use('com.qinggan.camera.activity.RecordVideoActivity');
        const Setting = Java.use('com.qinggan.camera.common.CameraSetting');
        const Support = Java.use('com.qinggan.camera.common.SupportUtils');
        const WeakReference = Java.use('java.lang.ref.WeakReference');
        const View = Java.use('android.view.View');
        const resume = Activity.resumeLogic.overload();
        const change = Activity.changeClick.overload();
        const getType = Setting.getCameraType.overload();
        const setType = Setting.setCameraType.overload('int');
        const buttons = [];
        let watchdog;
        function restoreButtons() {
            buttons.forEach(function (item) {
                const value = item.reference.get();
                if (value !== null) {
                    const button = Java.cast(value, View);
                    button.setVisibility(item.visibility);
                    button.setEnabled(item.enabled);
                    button.setClickable(item.clickable);
                }
                item.reference.$dispose();
            });
            buttons.length = 0;
        }
        cleanup = function () {
            enabled = false;
            return new Promise(function (resolve, reject) {
                Java.scheduleOnMainThread(function () {
                    try {
                        resume.implementation = null;
                        change.implementation = null;
                        restoreButtons();
                        clearInterval(watchdog);
                        console.log('VI_ROA_DISABLED');
                        resolve();
                    } catch (e) { console.error('VI_ROA_ERROR cleanup ' + e); reject(e); }
                });
            });
        };
        function reveal(activity) {
            if (!enabled || !Support.isSupportRoa()) return;
            const button = activity.camera_change_iv.value;
            if (button === null) return;
            // Remove dead Activity references before remembering a new view.
            for (let i = buttons.length - 1; i >= 0; i--) {
                if (buttons[i].reference.get() === null) { buttons[i].reference.$dispose(); buttons.splice(i, 1); }
            }
            if (!buttons.some(item => { const v = item.reference.get(); return v !== null && v.equals(button); })) {
                buttons.push({ reference: Java.retain(WeakReference.$new(button)), visibility: button.getVisibility(),
                    enabled: button.isEnabled(), clickable: button.isClickable() });
            }
            button.setVisibility(0);
            button.setEnabled(true);
            button.setClickable(true);
            console.log('VI_ROA_SWITCH_VISIBLE');
        }
        resume.implementation = function () {
            const result = resume.call(this);
            try { reveal(this); } catch (e) { console.error('VI_ROA_ERROR reveal ' + e); }
            return result;
        };
        change.implementation = function () {
            const setting = Setting.getInstance();
            const previous = getType.call(setting);
            const restrict = enabled && Support.isSupportRoa() && (previous === 0 || previous === 1);
            // Preserve OEM stopRecord(), flags, handler cancellation and caller camera release/rebind.
            const result = change.call(this);
            if (restrict) {
                const target = previous === 0 ? 1 : 0;
                setType.call(setting, target);
                console.log('VI_ROA_SWITCH ' + previous + ' -> ' + target);
            }
            return result;
        };
        watchdog = setInterval(function () {
            if (enabled && Date.now() - lastHeartbeat > 15000) cleanup();
        }, 3000);
        Java.scheduleOnMainThread(function () {
            Java.choose('com.qinggan.camera.activity.RecordVideoActivity', {
                onMatch: function (activity) { if (activity.isResumed.value) reveal(activity); },
                onComplete: function () { console.log('VI_ROA_READY'); }
            });
        });
    } catch (e) { console.error('VI_ROA_ERROR setup ' + e); }
});
