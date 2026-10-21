package com.abrar.voyahiran.core.bridge

import android.os.IBinder
import android.os.Process
import android.os.RemoteException
import com.abrar.voyahiran.core.logging.AppLog
import java.util.concurrent.atomic.AtomicBoolean

/** app_process survives system_server; Android singleton service proxies do not recover.
 * Let init restart this non-oneshot service with fresh framework caches. Never replay commands.
 */
internal object BridgeSystemLiveness {
    // Keep the death recipient and binder alive for the entire process lifetime.
    private var watched: IBinder? = null
    private var recipient: IBinder.DeathRecipient? = null

    fun start() {
        // Subscribe before any manager caches framework services. A missing service at
        // boot is not a death: wait for publication instead of entering a restart loop.
        while (true) {
            val binder = Class.forName("android.os.ServiceManager")
                .getMethod("getService", String::class.java).invoke(null, "activity") as? IBinder
            if (binder != null) {
                watched = binder
                recipient = watch(binder) {
                    AppLog.info("BridgeServer", "system service died; restarting bridge through init; no command replay")
                    Process.killProcess(Process.myPid())
                }
                return
            }
            Thread.sleep(1000)
        }
    }

    internal fun watch(binder: IBinder, restart: () -> Unit): IBinder.DeathRecipient {
        val fired = AtomicBoolean(false)
        val death = IBinder.DeathRecipient { if (fired.compareAndSet(false, true)) restart() }
        try {
            binder.linkToDeath(death, 0)
            // Covers death between service lookup and registration.
            if (!binder.isBinderAlive) death.binderDied()
        } catch (_: RemoteException) {
            death.binderDied()
        }
        return death
    }
}
