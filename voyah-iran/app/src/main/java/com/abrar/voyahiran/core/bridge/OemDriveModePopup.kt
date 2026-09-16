package com.abrar.voyahiran.core.bridge

import com.abrar.voyahiran.core.logging.AppLog
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** One-shot UI presentation in the already running OEM process. Never writes vehicle state. */
object OemDriveModePopup {
    @Synchronized fun show() {
        val runtime=File("/data/local/tmp/voyahiran-bridge/roa/frida-inject")
        val expected=File(runtime.path+".sha256").readText().trim()
        val hash=java.security.MessageDigest.getInstance("SHA-256")
        runtime.inputStream().use { input ->
            val buffer=ByteArray(65536)
            while(true) { val n=input.read(buffer); if(n<0)break; hash.update(buffer,0,n) }
        }
        check(hash.digest().joinToString("") { "%02x".format(it) }==expected) { "Popup runtime hash mismatch" }
        val pid=File("/proc").listFiles().orEmpty().filter { it.name.all(Char::isDigit) }.singleOrNull {
            runCatching { File(it,"cmdline").readText().substringBefore('\u0000')=="com.qinggan.app.vehicle" }.getOrDefault(false)
        }?.name ?: error("OEM vehicle UI process unavailable")
        val script=File.createTempFile("drive-popup-", ".js",runtime.parentFile)
        var process: Process?=null
        try {
            script.writeText(SCRIPT)
            val child=ProcessBuilder(runtime.path,"-p",pid,"-s",script.path,"-i").redirectErrorStream(true).start()
            process=child
            AppLog.info("RahaOemUi", "Popup attach started pid=$pid script=${script.name}")
            val done=CountDownLatch(1)
            val stopping=java.util.concurrent.atomic.AtomicBoolean(false)
            val failure=java.util.concurrent.atomic.AtomicReference<String?>(null)
            val ok=java.util.concurrent.atomic.AtomicBoolean(false)
            val reader=thread(name="raha-popup-output",isDaemon=true) {
                try { child.inputStream.bufferedReader().useLines { lines -> lines.forEach { line ->
                    AppLog.info("RahaOemUi",line.take(1500))
                    if(line.contains("RAHA_POPUP_OK")) { ok.set(true); done.countDown() }
                    if(line.contains("RAHA_POPUP_ERROR")) { failure.set(line.take(1500)); done.countDown() }
                } } } catch(e:Exception) {
                    // Closing/killing frida can interrupt readLine. Never let this escape the
                    // worker: Android's uncaught-exception handler terminates the whole bridge.
                    if(!stopping.get() && !ok.get()) {
                        failure.compareAndSet(null,"Output read failed: ${e.javaClass.simpleName}: ${e.message}")
                        AppLog.error("RahaOemUi","Popup output reader failed",e)
                    }
                } finally { done.countDown() }
            }
            try {
                val completed=done.await(8,TimeUnit.SECONDS)
                val exit=runCatching { child.exitValue().toString() }.getOrDefault("running")
                AppLog.info("RahaOemUi","Popup attach completed=$completed confirmed=${ok.get()} process=$exit failure=${failure.get()}")
                check(completed && ok.get()) { failure.get() ?: "OEM popup not confirmed: completed=$completed process=$exit" }
            } finally {
                stopping.set(true)
                child.destroy()
                if(!child.waitFor(1,TimeUnit.SECONDS)) child.destroyForcibly()
                reader.join(500)
            }
        } finally {
            process?.destroy()
            if(process?.waitFor(1,TimeUnit.SECONDS)==false) process.destroyForcibly()
            script.delete()
        }
    }
    private val SCRIPT="""
        'use strict';
        Java.perform(function () {
            Java.scheduleOnMainThread(function () {
                let stage='manager';
                function concrete(value) {
                    if(value===null) throw new Error('Null Java object at '+stage);
                    // Interface wrappers need an Object cast before invoking getClass().
                    const object=Java.cast(value,Java.use('java.lang.Object'));
                    const name=object.getClass().getName().toString();
                    console.log('RAHA_POPUP_CLASS stage='+stage+' class='+name);
                    return Java.cast(value,Java.use(name));
                }
                try {
                    const Manager=Java.use('com.qinggan.app.vehiclebase.ui97.DriveModeViewManager');
                    const manager=Java.cast(Manager.mDriveModeViewManager.value, Manager);
                    if(manager===null) throw new Error('DriveModeViewManager not initialized');
                    // Do not disturb a popup the driver is already interacting with.
                    if(manager.isShowing.value) { console.log('RAHA_POPUP_OK already visible'); return; }
                    stage='read-mode';
                    const Bus=Java.use('com.qinggan.canbus.CanBusManager');
                    const bus=Java.cast(Bus.getInstance.overload().call(Bus),Bus);
                    const state=Java.use('com.qinggan.canbus.DFVehicleState');
                    const mode=bus.getVehicleState(state.DRIVING_MODE_SET.value);
                    const offroad=Java.use('com.qinggan.app.vehiclebase.ui97.VoiceListenerService').supportOffload.value;
                    const indexes=offroad ? {1:0,2:1,3:2,4:3,6:4,5:5} : {1:0,2:1,3:2,6:3,5:4};
                    const index=indexes[mode];
                    if(index===undefined) throw new Error('No popup item for mode '+mode);
                    stage='select-item';
                    const view=Java.cast(manager.contentView.value,Java.use('com.qinggan.app.vehiclebase.ui97.DriveModeView'));
                    view.setCurrentItem(index);
                    // Bypass show(): it schedules message 3, which writes drive mode again.
                    stage='resolve-window-manager';
                    // WindowManager extends ViewManager. Resolve on the concrete class so
                    // Frida exposes inherited interface methods such as addView.
                    const windows=concrete(manager.mWindowManager.value);
                    stage='add-window';
                    windows.addView.overload('android.view.View','android.view.ViewGroup${'$'}LayoutParams').call(windows,view,manager.mParams.value);
                    manager.isShowing.value=true;
                    stage='schedule-hide';
                    manager.eightSecondsQuit();
                    console.log('RAHA_POPUP_OK mode='+mode+' index='+index);
                } catch(e) { console.log('RAHA_POPUP_ERROR stage='+stage+' '+e+' stack='+e.stack); }
            });
        });
    """.trimIndent()
}
