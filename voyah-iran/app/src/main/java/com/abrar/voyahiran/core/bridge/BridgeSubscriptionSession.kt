package com.abrar.voyahiran.core.bridge
import android.os.SystemClock
import com.abrar.voyahiran.core.shell.CommandResult

/** Starts closed on each bridge boot. Only the authenticated app updates this after verifying its token. */
object BridgeSubscriptionSession {
    private val lease=SubscriptionRuntimeLease(SystemClock::elapsedRealtime)
    private val vipLease=SubscriptionRuntimeLease(SystemClock::elapsedRealtime)
    val vipActive: Boolean get()=active && vipLease.active
    private val loginExpiry = java.io.File("/data/local/voyahiran-login-vip-expiry")
    private val loginBoot = java.io.File("/data/local/voyahiran-login-allowed-boot")
    private val bootId by lazy {java.io.File("/proc/sys/kernel/random/boot_id").readText().trim()}
    private val loginPolicy by lazy {
        val sameBoot=runCatching {loginBoot.readText().trim()==bootId}.getOrDefault(false)
        LoginBootPolicy(if(sameBoot) true else LoginBootPolicy.cached(runCatching {loginExpiry.readText()}.getOrNull()))
    }
    // Only login startup has boot-long grace. Live automation retains its short leases.
    val loginVipActive: Boolean? get() = loginPolicy.allowed.also {
        if(it==true) runCatching { if(!loginBoot.exists() || loginBoot.readText().trim()!=bootId) loginBoot.writeText(bootId) }
    }
    @Synchronized fun updateVip(seconds: Int, remaining: Int): CommandResult {
        // Load the previous decision BEFORE replacing its persisted record.
        loginPolicy.update(remaining>0)
        loginVipActive
        val now = System.currentTimeMillis() / 1000
        val temp = java.io.File(loginExpiry.path + ".new")
        temp.writeText("$now:${now + remaining}")
        check(temp.renameTo(loginExpiry))
        vipLease.update(seconds)
        com.abrar.voyahiran.core.shell.ProcessCommandRunner(useBridge=false).run(listOf(
            "/system/bin/settings", "put", "global", "voyah_iran_launcher_vip_lease",
            (System.currentTimeMillis()+seconds*1000L).toString()), 2)
        LocalProfileTest.reconcile()
        return CommandResult(0,"VIP runtime session updated")
    }
    val active: Boolean get()=lease.active
    @Synchronized fun update(seconds: Int): CommandResult {
        lease.update(seconds)
        // Bounded wall-clock lease for the OEM Home hook, which lives in another process.
        com.abrar.voyahiran.core.shell.ProcessCommandRunner(useBridge=false).run(listOf(
            "/system/bin/settings", "put", "global", "voyah_iran_launcher_lease",
            (System.currentTimeMillis()+seconds*1000L).toString()), 2)
        return CommandResult(0,"Subscription runtime session updated")
    }
}
