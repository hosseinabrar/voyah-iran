package com.abrar.voyahiran.core.bridge

import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.core.shell.CommandResult
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/** Presentation only; never dispatches a seat movement/save. Does not hold the bridge lock. */
internal object OemSeatPopup {
    private val active = AtomicBoolean(false)
    fun show(): CommandResult {
        if (!BridgeSubscriptionSession.active) return CommandResult(126, "Subscription runtime inactive")
        if (!active.compareAndSet(false, true)) return CommandResult(0, "Seat popup already scheduled")
        thread(name = "RahaSeatPopup", isDaemon = true) {
            try { OemActionScriptRunner.run("com.qinggan.app.vehicle", SCRIPT) }
            catch (e: Exception) { AppLog.error("RahaOemUi", "Seat popup failed; seat operation will not be repeated", e) }
            finally { active.set(false) }
        }
        return CommandResult(0, "Seat popup scheduled with 3000 ms auto-close")
    }
    internal val SCRIPT = """
        'use strict';
        Java.perform(function() { Java.scheduleOnMainThread(function() {
            let showHook=null, dismissHook=null, owned=false;
            function restore() {
                if(showHook!==null)showHook.implementation=null;
                if(dismissHook!==null)dismissHook.implementation=null;
            }
            try {
                const app=Java.use('android.app.ActivityThread').currentApplication();
                const ctx=app.getApplicationContext();
                const Type=Java.use('com.qinggan.app.vehiclebase.vehicle.fragments.air_and_seat.SeatMirrorOtaDialog');
                const dialog=Java.retain(Type.getInstance(ctx));
                if(dialog.isShowing()) { dialog.${'$'}dispose();console.log('RAHA_ACTION_OK existing seat popup preserved');return; }
                showHook=Type.show.overload('boolean','android.content.Context');
                dismissHook=Type.dismiss.overload();
                // A manual close/reopen transfers ownership back to the user.
                showHook.implementation=function(a,b) {owned=false;return showHook.call(this,a,b);};
                dismissHook.implementation=function() {owned=false;return dismissHook.call(this);};
                showHook.call(dialog,true,ctx);
                owned=true;
                console.log('RAHA_ACTION_STAGE seat popup shown; auto-close in 3000 ms');
                setTimeout(function() { Java.scheduleOnMainThread(function() {
                    try {
                        if(owned && dialog.isShowing()) {dialog.closePage();console.log('RAHA_ACTION_STAGE seat popup auto-closed');}
                        restore();console.log('RAHA_ACTION_OK seat popup presentation completed');
                    } catch(e) {restore();console.log('RAHA_ACTION_ERROR seat popup close '+e);}
                    finally {dialog.${'$'}dispose();}
                });},3000);
            } catch(e) {restore();console.log('RAHA_ACTION_ERROR seat popup show '+e);}
        }); });
    """.trimIndent()
}
