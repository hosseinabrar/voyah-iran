package com.abrar.voyahiran.core.bridge

/** Only seat UI scopes bypass Guest. Account storage, cloud sync and automatic recall stay OEM. */
internal object SeatMemoryHookScript {
    val source = """
'use strict';
let enabled = false, heartbeat = 0, clock = null;
const hooks = [], scopes = new Map();
const mirror = 'com.qinggan.app.vehiclebase.vehicle.fragments.air_and_seat.MirrorMainView';
function active() { return enabled && clock !== null && Number(clock.elapsedRealtime()) - heartbeat < 10000; }
function scoped() { return active() && (scopes.get(Process.getCurrentThreadId()) || 0) > 0; }
function within(fn) {
    const id = Process.getCurrentThreadId(), old = scopes.get(id) || 0;
    scopes.set(id, old + 1);
    try { return fn(); } finally { if (old) scopes.set(id, old); else scopes.delete(id); }
}
function wrap(type, name, signature) {
    const method = type[name].overload.apply(type[name], signature);
    method.implementation = function() {
        const self = this, args = Array.prototype.slice.call(arguments);
        if (!active()) return method.call.apply(method, [self].concat(args));
        return within(function() { return method.call.apply(method, [self].concat(args)); });
    };
    hooks.push(method);
}
function refreshViews(done) {
    if (ROLE !== 'vehicle') { if (done) done(); return; }
    Java.scheduleOnMainThread(function() {
        Java.choose(mirror, {
            onMatch: function(view) {
                try { if (view.isAttachedToWindow()) view.initFirstStatusView(); }
                catch(e) { console.log('VI_SEAT_UI_ERROR ' + e); }
            }, onComplete: function() { if (done) done(); }
        });
    });
}
function deactivate() {
    enabled = false;
    refreshViews(function() { console.log('VI_SEAT_DISABLED'); });
}
rpc.exports = {
    onFridaStdin: function(data) {
        const command = data.trim();
        Java.perform(function() {
            if (command === 'ping' && clock !== null) {
                const changed = !active();
                heartbeat = Number(clock.elapsedRealtime()); enabled = true;
                if (changed) refreshViews();
            }
            if (command === 'disable') deactivate();
        });
    },
    dispose: function() { Java.perform(function() {
        deactivate(); hooks.forEach(function(h) { h.implementation = null; });
    }); }
};
Java.perform(function() {
    try {
        clock = Java.use('android.os.SystemClock');
        if (ROLE === 'vehicle') {
            const manager = Java.use('com.qinggan.app.vehiclebase.model.SeatMemoryManager');
            const guest = manager.isGuest.overload();
            guest.implementation = function() { return scoped() ? false : guest.call(this); };
            hooks.push(guest);
            wrap(Java.use(mirror), 'initFirstStatusView', []);
            // A visible panel must not retain VIP actions while awaiting the UI reset.
            ['sendCmd', 'saveSeatPosition'].forEach(function(name) {
                const action = Java.use(mirror)[name].overload('int');
                action.implementation = function(slot) {
                    if (!active() && guest.call(manager.getInstance())) {
                        this.initFirstStatusView(); return;
                    }
                    return action.call(this, slot);
                };
                hooks.push(action);
            });
            // Feedback and the small save/recall island after manual seat adjustment.
            wrap(manager, 'modifyIsPosSame', []);
            if (manager.noMemoryModifyPosSame) wrap(manager, 'noMemoryModifyPosSame', []);
            wrap(Java.use('com.qinggan.app.vehiclebase.model.SeatMemoryManager__DOLLAR__2'),
                 'onSeatAdjustStateChanged', ['com.qinggan.canbus.SeatAdjustState']);
        } else {
            const prefix = ROLE === 'launcher' ? 'com.qinggan.launcher.base' : 'com.qinggan.app.base';
            const account = Java.use(prefix + '.manager.AccountManager');
            const get = account.getAccountInfo.overload();
            const Bean = Java.use('com.qinggan.account.AccountInfoBean');
            get.implementation = function() {
                const original = get.call(this);
                if (!scoped()) return original;
                // A private UI-only value: never mutate the shared account or persist a login.
                const local = Bean.__DOLLAR__new();
                local.setName('Voyah seat memory'); local.setGuest(false);
                return local;
            };
            hooks.push(get);
            const names = ROLE === 'launcher'
                ? ['com.qinggan.mainlauncher.hiboard.vehiclehiboard.widget.ComponentSmallView']
                : ['com.qinggan.app.hiboard.vehiclehiboard.main.ComponentSmallView',
                   'com.qinggan.app.hiboard.vehiclehiboard.main.ComponentSmallOTA1x1View'];
            names.forEach(function(name) { wrap(Java.use(name), 'handleSeatClick', []); });
            // OEM click handler retains all three slots, in-progress stop and speed checks.
        }
        console.log('VI_SEAT_READY ' + ROLE);
    } catch(e) {
        enabled = false; hooks.forEach(function(h) { h.implementation = null; });
        console.log('VI_SEAT_ERROR ' + e);
    }
});
setInterval(function() { if (enabled && !active()) Java.perform(deactivate); }, 1000);
""".trimIndent().replace("__DOLLAR__", "${'$'}")
}
