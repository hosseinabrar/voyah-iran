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
        let passengerModel = null, passengerHandler = null, passengerState = null;
        const passengerRequests = new Map();
        let passengerApplied = null;
        let passengerTopApi = null, passengerBarType = null;
        function passengerTop(ctx) {
            if (passengerTopApi === null) return null;
            const value = passengerTopApi.getTopAppInfo(ctx, 1, 4);
            return value === null ? '' : value.toString();
        }
        function passengerMoving() {
            return passengerModel === null || passengerModel.mMainScreenMoving.value || passengerModel.mSecondScreenMoving.value;
        }
        function recoverPassenger(ctx, component) {
            if (passengerBarType === null || passengerMoving()) return;
            const raw = passengerModel.mSecondScreenNavigationBar.value;
            if (raw === null) return;
            const bar = Java.cast(raw, passengerBarType);
            const animator = bar.mMoveWindowAnimator.value;
            if (animator !== null && animator.isStarted()) return;
            if (bar.mRootView.value.getParent() === null || bar.mLp.value.x.value !== 0) {
                bar.show();
                console.log('VI_NAV_PASSENGER_RECOVER ' + component);
            }
        }
        // OEM Android display 1 uses navigation type 3, not center type 2.
        // Hook its final handler too, so later OEM hide requests cannot undo the policy.
        try {
            const Model = Java.use('com.qinggan.app.launcher.LauncherModel');
            passengerHandler = Model.handleUpdateSecondNavigationBar.overload('java.lang.String', 'java.lang.String', 'boolean');
            passengerHandler.implementation = function (pkg, activity, visible) {
                if (passengerModel === null) passengerModel = Java.retain(this);
                let result = visible;
                try {
                    const ctx = Java.cast(Base.getInstance(), Base);
                    const component = String(pkg) + '/' + String(activity);
                    if (passengerRequests.size >= 64) passengerRequests.clear();
                    passengerRequests.set(component, visible);
                    if (active(ctx) && eligible(ctx, component)) {
                        result = true;
                        passengerApplied = {component: component, original: visible};
                    }
                } catch (e) { console.log('VI_NAV_PASSENGER_ERROR ' + e); }
                return passengerHandler.call(this, pkg, activity, result);
            };
            Java.choose('com.qinggan.app.launcher.LauncherModel', {
                onMatch: function (instance) {
                    passengerModel = Java.retain(instance);
                    return 'stop';
                }, onComplete: function () {}
            });
        } catch (e) { console.log('VI_NAV_PASSENGER_UNAVAILABLE ' + e); }
        try {
            passengerTopApi = Java.use('com.qinggan.launcher.base.utils.AppUtils');
            passengerBarType = Java.use('com.qinggan.launcher.navigation.NavigationBarController');
            const nativeDismiss = passengerBarType.dismiss.overload();
            const nativeRelease = passengerBarType.release.overload();
            let releasing = 0;
            nativeRelease.implementation = function () {
                releasing++;
                try { return nativeRelease.call(this); } finally { releasing--; }
            };
            nativeDismiss.implementation = function () {
                try {
                    if (this.mScreenId.value === 1 && releasing === 0 && !passengerMoving()) {
                        const ctx = Java.cast(Base.getInstance(), Base);
                        const component = passengerTop(ctx);
                        if (component && active(ctx) && eligible(ctx, component)) {
                            // Keep native hide intent for restoring the unchecked policy.
                            passengerApplied = {component: component, original: false};
                            console.log('VI_NAV_PASSENGER_KEEP ' + component);
                            return;
                        }
                    }
                } catch (e) { console.log('VI_NAV_PASSENGER_GUARD_ERROR ' + e); }
                return nativeDismiss.call(this);
            };
        } catch (e) { console.log('VI_NAV_PASSENGER_GUARD_UNAVAILABLE ' + e); }
        function refreshPassenger(ctx, component, enabled) {
            if (passengerModel === null || passengerHandler === null) return;
            const signature = enabled + ':' + component;
            if (signature === passengerState) {
                if (enabled && eligible(ctx, component)) recoverPassenger(ctx, component);
                return;
            }
            if (eligible(ctx, component)) {
                const parts = String(component).split('/');
                // Empty activity avoids full vs shortened class-name mismatches; OEM still checks the foreground package.
                if (enabled) {
                    const originalVisible = passengerRequests.has(component) ? passengerRequests.get(component) :
                        (passengerApplied !== null && passengerApplied.component === component ? passengerApplied.original : passengerModel.mSecondNavigationBarLastShow.value);
                    passengerApplied = {component: component, original: originalVisible};
                    passengerHandler.call(passengerModel, parts[0], '', true);
                    recoverPassenger(ctx, component);
                } else if (passengerApplied !== null && passengerApplied.component === component) {
                    passengerHandler.call(passengerModel, parts[0], '', passengerApplied.original);
                    passengerApplied = null;
                }
                console.log('VI_NAV_PASSENGER display=1 enabled=' + enabled + ' component=' + component);
            }
            passengerState = signature;
        }
        // Use the factory passenger drawer and its display-aware launch path.
        let refreshDrawer = function () {};
        try {
            const Bar = Java.use('com.qinggan.launcher.navigation.NavigationBarSecond');
            const Data = Java.use('com.qinggan.launcher.allapp.AllAppDataManager');
            const Bean = Java.use('com.qinggan.launcher.allapp.AppBean');
            const List = Java.use('java.util.ArrayList');
            const init = Bar.initAllAppShow.overload();
            const click = Bar.onClick.overload('android.view.View');
            const apps = Data.getAllApps.overload('int');
            const bars = new Map();
            let drawerEnabled = null;
            function enabled() {
                return Global.getInt(Base.getInstance().getContentResolver(), 'voyah_iran_passenger_apps', 1) === 1;
            }
            init.implementation = function () {
                const key = this.hashCode();
                if (!bars.has(key)) bars.set(key, Java.retain(this));
                const nativeValue = this.isShowScreenAllApp.value;
                try {
                    if (enabled()) this.isShowScreenAllApp.value = true;
                    return init.call(this);
                } finally { this.isShowScreenAllApp.value = nativeValue; }
            };
            click.implementation = function (v) {
                try {
                    if (enabled() && this.mScreenUpAllAppView.value !== null &&
                        v.getId() === this.mScreenUpAllAppView.value.getId()) {
                        Java.cast(Base.getInstance(), Base).expandAllApp(1);
                        console.log('VI_PASSENGER_APPS_OPEN display=1');
                        return;
                    }
                } catch (e) { console.log('VI_PASSENGER_APPS_CLICK_ERROR ' + e); }
                return click.call(this, v);
            };
            apps.implementation = function (screen) {
                const stock = apps.call(this, screen);
                if (screen !== 1 || !enabled()) return stock;
                try {
                    const result = List.__DOLLAR__new(stock);
                    const seen = new Set();
                    for (let i=0; i<result.size(); i++) seen.add(Java.cast(result.get(i), Bean).getPackageName().toString());
                    const main = apps.call(this, 0);
                    const pm = Base.getInstance().getPackageManager();
                    for (let i=0; i<main.size(); i++) {
                        const bean = Java.cast(main.get(i), Bean);
                        const pkg = bean.getPackageName();
                        if (pkg === null || seen.has(pkg.toString())) continue;
                        try {
                            const info = pm.getApplicationInfo(pkg, 0);
                            if ((info.flags.value & 129) !== 0 || pm.getLaunchIntentForPackage(pkg) === null) continue;
                            result.add(bean); seen.add(pkg.toString());
                        } catch (_) {} // Uninstalled while the drawer was opening.
                    }
                    return result;
                } catch (e) { console.log('VI_PASSENGER_APPS_LIST_ERROR ' + e); return stock; }
            };
            refreshDrawer = function () {
                const value = enabled();
                if (value === drawerEnabled) return;
                drawerEnabled = value;
                bars.forEach(function (bar) { bar.initAllAppShow(); });
                console.log('VI_PASSENGER_APPS enabled=' + value);
            };
            Java.choose('com.qinggan.launcher.navigation.NavigationBarSecond', {
                onMatch: function (bar) { bars.set(bar.hashCode(), Java.retain(bar)); },
                onComplete: function () { Java.scheduleOnMainThread(function () { refreshDrawer(); }); }
            });
        } catch (e) { console.log('VI_PASSENGER_APPS_UNAVAILABLE ' + e); }
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
            refreshDrawer();
            if (service === null) return;
            const enabled = active(service);
            const centerChanged = enabled !== previousPolicy;
            let centerSeen = false, passengerSeen = false;
            // The OEM API covers passenger tasks absent from ActivityManager's running list.
            try {
                const top = passengerTop(service);
                if (top) { passengerSeen = true; refreshPassenger(service, top, enabled); }
            } catch (e) { console.log('VI_NAV_PASSENGER_TOP_ERROR ' + e); }
            const tasks = Java.cast(service.getSystemService('activity'), AM).getRunningTasks(32);
            for (let i = 0; i < tasks.size(); i++) {
                const task = Java.cast(tasks.get(i), Java.use('android.app.ActivityManager' + String.fromCharCode(36) + 'RunningTaskInfo'));
                if (task.topActivity.value === null) continue;
                const component = task.topActivity.value.flattenToString().toString();
                if (task.displayId.value === 1 && !passengerSeen) {
                    passengerSeen = true;
                    refreshPassenger(service, component, enabled);
                }
                if (task.displayId.value !== 0 || centerSeen) continue;
                centerSeen = true;
                if (centerChanged) {
                    apply(service, component);
                    previousPolicy = enabled;
                    console.log('VI_NAV_POLICY enabled=' + enabled);
                }
            }
            if (!passengerSeen) passengerState = null;
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
