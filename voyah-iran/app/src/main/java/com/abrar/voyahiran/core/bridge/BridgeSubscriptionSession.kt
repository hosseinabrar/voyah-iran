package com.abrar.voyahiran.core.bridge
import android.os.SystemClock
import com.abrar.voyahiran.core.shell.CommandResult

/** Starts closed on each bridge boot. Only the authenticated app updates this after verifying its token. */
object BridgeSubscriptionSession {
    private val lease=SubscriptionRuntimeLease(SystemClock::elapsedRealtime)
    val active: Boolean get()=lease.active
    fun update(seconds: Int): CommandResult {
        lease.update(seconds)
        // Bounded wall-clock lease for the OEM Home hook, which lives in another process.
        com.abrar.voyahiran.core.shell.ProcessCommandRunner(useBridge=false).run(listOf(
            "/system/bin/settings", "put", "global", "voyah_iran_launcher_lease",
            (System.currentTimeMillis()+seconds*1000L).toString()), 2)
        return CommandResult(0,"Subscription runtime session updated")
    }
}
