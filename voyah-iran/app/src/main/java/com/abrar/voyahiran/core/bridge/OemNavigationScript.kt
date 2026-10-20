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
        function passengerHome(component) {
            const parts=String(component).split('/');
            return parts[0]==='com.abrar.voyahiran' &&
                (parts[1]==='com.abrar.voyahiran.feature.launcher.PassengerLauncherActivity' || parts[1]==='.feature.launcher.PassengerLauncherActivity') &&
                typeof globalThis.VI_PASSENGER_HOME_VISIBLE==='function' && globalThis.VI_PASSENGER_HOME_VISIBLE();
        }
        function passengerTop(ctx) {
            const hint=typeof globalThis.VI_PASSENGER_TOP_HINT==='function'?globalThis.VI_PASSENGER_TOP_HINT():'';
            if(hint)return hint;
            if (passengerTopApi === null) return null;
            const value = passengerTopApi.getTopAppInfo(ctx, 1, 4);
            return value === null ? '' : value.toString();
        }
        function passengerMoving() {
            return passengerModel === null || passengerModel.mMainScreenMoving.value || passengerModel.mSecondScreenMoving.value;
        }
        function recoverPassenger(ctx, component) {
            if (passengerHome(component)) return;
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
                    if (passengerHome(component)) {
                        result = false;
                    } else if (active(ctx) && eligible(ctx, component)) {
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
            const nativeShow = passengerBarType.show.overload();
            nativeShow.implementation = function () {
                if (Number(this.mScreenId.value)===1 && passengerHome(passengerTop(Base.getInstance()))) {
                    const animator=this.mMoveWindowAnimator.value;
                    if(this.mRootView.value.getParent()!==null && (animator===null || !animator.isStarted()))nativeDismiss.call(this);
                    return;
                }
                return nativeShow.call(this);
            };
            const nativeRelease = passengerBarType.release.overload();
            let releasing = 0;
            nativeRelease.implementation = function () {
                if(typeof globalThis.VI_RELEASE_BAR_BACKGROUND === 'function') globalThis.VI_RELEASE_BAR_BACKGROUND(this);
                releasing++;
                try { return nativeRelease.call(this); } finally { releasing--; }
            };
            nativeDismiss.implementation = function () {
                try {
                    if (this.mScreenId.value === 1 && releasing === 0 && !passengerMoving()) {
                        const ctx = Java.cast(Base.getInstance(), Base);
                        const component = passengerTop(ctx);
                        if (component && !passengerHome(component) && active(ctx) && eligible(ctx, component)) {
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
        function syncBars(ctx, component, display, enabled) {
            if(typeof globalThis.VI_SYNC_BAR_BACKGROUND !== 'function')return;
            let show=false;
            try {show=enabled && eligible(ctx,component);}catch(e){}
            globalThis.VI_SYNC_BAR_BACKGROUND(ctx,display,show);
        }
        function refreshPassenger(ctx, component, enabled) {
            syncBars(ctx,component,1,enabled);
            if (passengerModel === null || passengerHandler === null) return;
            if (passengerMoving()) return; // Do not cache a policy the OEM cannot apply yet.
            const home=passengerHome(component);
            const signature = enabled + ':' + home + ':' + component;
            if (home) {
                if(signature!==passengerState) {
                    passengerHandler.call(passengerModel, 'com.abrar.voyahiran', '', false);
                    passengerState=signature;
                }
                // Native dismiss calls animateDismiss and removes its touch window.
                // Do not restart an animation already in flight.
                if(passengerBarType!==null && !passengerMoving()) {
                    const raw=passengerModel.mSecondScreenNavigationBar.value;
                    if(raw!==null) {
                        const bar=Java.cast(raw,passengerBarType), animator=bar.mMoveWindowAnimator.value;
                        if(bar.mRootView.value.getParent()!==null && (animator===null || !animator.isStarted()))bar.dismiss();
                    }
                }
                return;
            }
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
        // Data enrichment is independent from passenger button construction.
        if (globalThis.VI_STOCK_PASSENGER_LIST === true) try {
            const Data = Java.use('com.qinggan.launcher.allapp.AllAppDataManager');
            const Bean = Java.use('com.qinggan.launcher.allapp.AppBean');
            const List = Java.use('java.util.ArrayList');
            const apps = Data.getAllApps.overload('int');
            function enabled() {
                return Global.getInt(Base.getInstance().getContentResolver(), 'voyah_iran_passenger_apps', 1) === 1;
            }
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
            syncBars(ctx,component,0,active(ctx));
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
                syncBars(service,component,0,enabled);
                if (centerChanged) {
                    apply(service, component);
                    previousPolicy = enabled;
                    console.log('VI_NAV_POLICY enabled=' + enabled);
                }
            }
            if (!passengerSeen) {passengerState = null;syncBars(service,'',1,false);}
            if (!centerSeen) syncBars(service,'',0,false);
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
        let eventRetries=0, retryPending=false;
        function passengerBusy(){
            if(passengerMoving())return true;
            if(passengerBarType===null)return false;
            const raw=passengerModel.mSecondScreenNavigationBar.value;
            if(raw===null)return true;
            const animator=Java.cast(raw,passengerBarType).mMoveWindowAnimator.value;
            return animator!==null && animator.isStarted();
        }
        function scheduleRefresh() {
            if (queued) return;
            queued = true;
            Java.scheduleOnMainThread(function () {
                try {
                    refresh();
                    if(eventRetries>0 && passengerBusy() && !retryPending){
                        eventRetries--;retryPending=true;
                        console.log('VI_NAV_PASSENGER_DEFER moving-or-animating remaining='+eventRetries);
                        setTimeout(function(){retryPending=false;scheduleRefresh();},100);
                    }else if(!passengerBusy()){eventRetries=0;}
                } catch (e) { console.log('VI_NAV_REFRESH_ERROR ' + e); }
                finally { queued = false; }
            });
        }
        // Use the OEM foreground broadcast, not the recovery timer, for display 1.
        try {
            const Model=Java.use('com.qinggan.app.launcher.LauncherModel');
            const receive=Model.onReceive.overload('android.content.Context','android.content.Intent');
            receive.implementation=function(ctx,intent){
                const passengerEvent=String(intent.getAction())==='android.intent.action.TOP_ACTIVITY_CHANGED' && intent.getIntExtra('displayId',-1)===1;
                if(passengerEvent && typeof globalThis.VI_PASSENGER_FOREGROUND==='function')globalThis.VI_PASSENGER_FOREGROUND(intent);
                const result=receive.call(this,ctx,intent);
                if(String(intent.getAction())==='android.intent.action.TOP_ACTIVITY_CHANGED' && intent.getIntExtra('displayId',-1)===1){
                    if(passengerModel===null)passengerModel=Java.retain(this);
                    console.log('VI_NAV_PASSENGER_EVENT foreground component='+passengerTop(ctx));
                    eventRetries=20;
                    scheduleRefresh();
                }
                return result;
            };
        }catch(e){console.log('VI_NAV_PASSENGER_EVENT_ERROR '+e);}
        setInterval(scheduleRefresh, 1000);
        console.log('VI_NAV_READY');
    } catch (error) { console.log('VI_NAV_ERROR ' + error); }
});
""".trimIndent().replace("__DOLLAR__", "$")
}
