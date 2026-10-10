package com.abrar.voyahiran.core.bridge
import android.os.SystemClock
import com.abrar.voyahiran.core.shell.CommandResult

/** Starts closed on each bridge boot. Only the authenticated app updates this after verifying its token. */
object BridgeSubscriptionSession {
    private val lease=SubscriptionRuntimeLease(SystemClock::elapsedRealtime)
    private val vipLease=SubscriptionRuntimeLease(SystemClock::elapsedRealtime)
    val vipActive: Boolean get()=active && vipLease.active
    @Volatile private var vipReceived = false
    private val loginExpiry = java.io.File("/data/local/voyahiran-login-vip-expiry")
    // Only the persistent login component needs entitlement before the app connects at boot.
    // All live automation continues to require the short monotonic lease.
    val loginVipActive: Boolean get() = if (vipReceived) vipActive else runCatching {
        val values = loginExpiry.readText().split(':').map(String::toLong)
        val now = System.currentTimeMillis() / 1000
        now >= values[0] && now < values[1]
    }.getOrDefault(false)
    fun updateVip(seconds: Int, remaining: Int): CommandResult {
        val now = System.currentTimeMillis() / 1000
        val temp = java.io.File(loginExpiry.path + ".new")
        temp.writeText("$now:${now + remaining}")
        check(temp.renameTo(loginExpiry))
        vipLease.update(seconds); vipReceived = true
        com.abrar.voyahiran.core.shell.ProcessCommandRunner(useBridge=false).run(listOf(
            "/system/bin/settings", "put", "global", "voyah_iran_launcher_vip_lease",
            (System.currentTimeMillis()+seconds*1000L).toString()), 2)
        LocalProfileTest.reconcile()
        return CommandResult(0,"VIP runtime session updated")
    }
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
