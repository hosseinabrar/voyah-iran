package com.abrar.voyahiran.core.bridge

import com.abrar.voyahiran.core.shell.CommandResult
import com.abrar.voyahiran.core.shell.ProcessCommandRunner

/** Embedded counterpart of deployment/local-profile/local-profile-test.sh. Keep in sync. */
object LocalProfileTest {
    private val worker by lazy { android.os.HandlerThread("VoyahLoginPolicy").apply { start() } }
    private val handler by lazy { android.os.Handler(worker.looper) }
    private var systemContext: android.content.Context? = null

    fun execute(operation: String): CommandResult {
        val result = java.util.concurrent.CompletableFuture<CommandResult>()
        handler.post {
            try { result.complete(executeOnWorker(operation)) }
            catch (error: Throwable) { result.complete(CommandResult(1, error.toString())) }
        }
        // Wait for completion: never return a timeout while a mutation remains queued.
        return result.get()
    }

    private fun executeOnWorker(request: String): CommandResult {
        val desiredFile = java.io.File("/data/local/voyahiran-login-desired")
        val backupFile = java.io.File("/data/local/voyahiran-login-activity.state")
        if (!desiredFile.exists()) desiredFile.writeText(if (backupFile.exists()) "1" else "0")
        if (request == "apply" && !BridgeSubscriptionSession.vipActive) return CommandResult(126, "VIP required")
        val desired = when(request) { "apply" -> true; "restore" -> false; else -> desiredFile.readText().trim() == "1" }
        val operation = if(request == "reconcile") {
            if(desired && BridgeSubscriptionSession.loginVipActive) "apply" else "restore"
        } else request
        if (operation !in listOf("status", "apply", "restore")) return CommandResult(126, "Operation denied")
        if (!com.abrar.voyahiran.core.compat.VehiclePlatform.localProfileTest)
            return CommandResult(126, "Unsupported vehicle variant")
        return try {
            val context = systemContext ?: run {
                // ActivityThread creates Handlers. Bridge socket workers have no Looper.
                check(android.os.Looper.myLooper() != null)
                val type = Class.forName("android.app.ActivityThread")
                val thread = type.getMethod("currentActivityThread").invoke(null)
                    ?: type.getMethod("systemMain").invoke(null)
                (type.getMethod("getSystemContext").invoke(thread) as android.content.Context)
                    .also { systemContext = it }
            }
            val pm = context.packageManager
            val login = android.content.ComponentName("com.qinggan.app.login", "com.qinggan.app.login.RootActivity")
            val home = android.content.ComponentName("com.qinggan.app.launcher", "com.qinggan.app.launcher.activity.MainActivity")
            val backup = java.io.File("/data/local/voyahiran-login-activity.state")
            val runner = ProcessCommandRunner(useBridge = false)
            pm.getActivityInfo(login, android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS)
            if (request == "reconcile") {
                val blocked = pm.getComponentEnabledSetting(login) == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                if ((operation == "apply" && blocked) || (operation == "restore" && !backup.exists()))
                    return CommandResult(0, "login_activity_blocked=$blocked login_desired=$desired")
                if (operation == "restore" && pm.getComponentEnabledSetting(login) == backup.readText().trim().toInt())
                    return CommandResult(0, "login_activity_blocked=$blocked login_desired=$desired")
            }
            if (operation == "apply") {
                pm.getActivityInfo(home, 0)
                if (!backup.exists()) {
                    val temp = java.io.File(backup.path + ".new")
                    temp.writeText(pm.getComponentEnabledSetting(login).toString())
                    check(temp.renameTo(backup)) { "Cannot save original activity state" }
                }
                val setHome = runner.run(listOf("/system/bin/cmd", "package", "set-home-activity", "--user", "0", home.flattenToString()), 5)
                check(setHome.exitCode == 0) { "Cannot select OEM launcher: ${setHome.output}" }
                pm.setComponentEnabledSetting(login, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    android.content.pm.PackageManager.DONT_KILL_APP)
            } else if (operation == "restore" && backup.exists()) {
                val original = backup.readText().trim().toInt()
                check(original in 0..4)
                pm.setComponentEnabledSetting(login, original, android.content.pm.PackageManager.DONT_KILL_APP)
                check(pm.getComponentEnabledSetting(login) == original)
                if (request != "reconcile") backup.delete()
            }
            // Remove the previous click automation and boot profile writer.
            if (operation != "status") {
                runner.run(listOf("/system/bin/settings", "put", "global", "voyah_iran_login_dismiss", "0"), 3)
                java.io.File("/data/local/voyahiran-local-profile/enabled").delete()
            }
            val state = pm.getComponentEnabledSetting(login)
            val blocked = state == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            check(operation != "apply" || blocked) { "Activity disable not confirmed" }
            if (request == "apply" || request == "restore") desiredFile.writeText(if(desired) "1" else "0")
            val output = "login_activity_blocked=$blocked login_desired=$desired componentState=$state"
            com.abrar.voyahiran.core.logging.AppLog.info("LocalProfile", "operation=$operation $output")
            CommandResult(0, output)
        } catch (e: Exception) {
            com.abrar.voyahiran.core.logging.AppLog.error("LocalProfile", "operation=$operation failed", e)
            val causes = generateSequence(e as Throwable) { it.cause }.take(8)
                .joinToString(" -> ") { "${it.javaClass.simpleName}: ${it.message.orEmpty()}" }
            com.abrar.voyahiran.core.logging.AppLog.info("LocalProfile", "operation=$operation causes=$causes")
            CommandResult(1, causes)
        }
    }

    fun reconcile() {
        if (com.abrar.voyahiran.core.compat.VehiclePlatform.localProfileTest)
            handler.post { runCatching { executeOnWorker("reconcile") } }
    }
    fun startAtBoot() {
        if (!com.abrar.voyahiran.core.compat.VehiclePlatform.localProfileTest) return
        handler.post(object : Runnable {
            override fun run() {
                runCatching { executeOnWorker("reconcile") }
                handler.postDelayed(this, 10_000)
            }
        })
    }

    private val SCRIPT = """
#!/system/bin/sh
# Experimental local-only profile. No cloud token, VIN, NVRAM or CAN writes.
set -eu
step=initializing
report_exit() {
    result=${'$'}?
    trap - EXIT
    echo "LOCAL_PROFILE_RESULT step=${'$'}step exit=${'$'}result"
    exit "${'$'}result"
}
trap report_exit EXIT
[ "${'$'}(id -u)" = 0 ] || { echo 'Root required'; exit 1; }
base=/data/local/voyahiran-local-profile
account=/private/configs/token/accountInfo
fake=/tmp/fake_login
operation=${'$'}{1:-status}
case "${'$'}operation" in status|apply|restore|watch) ;; *) exit 2;; esac
status() {
    [ -f "${'$'}base/enabled" ] && echo 'boot_apply=enabled' || echo 'boot_apply=disabled'
    [ -e "${'$'}fake" ] && echo 'activation_override=present' || echo 'activation_override=absent'
    [ "${'$'}(getprop ignoreActivation)" = true ] && echo 'login_bypass=enabled' || echo 'login_bypass=disabled'
    if [ ! -f "${'$'}account" ]; then echo 'profile=missing'
    elif [ -f "${'$'}base/profile" ] && cmp -s "${'$'}account" "${'$'}base/profile"; then echo 'profile=local_test'
    elif [ "${'$'}(cat "${'$'}account")" = guest ]; then echo 'profile=guest'
    else echo 'profile=other_preserved'; fi
}
lock() {
    mkdir /data/local/tmp/voyahiran-local-profile.lock 2>/dev/null || { echo 'Another profile operation is running'; exit 1; }
    trap 'result=${'$'}?; rmdir /data/local/tmp/voyahiran-local-profile.lock || :; echo "LOCAL_PROFILE_RESULT step=${'$'}step exit=${'$'}result"; exit "${'$'}result"' EXIT
}
restore() (
    step=lock
    lock
    rm -f "${'$'}base/enabled"
    [ -f "${'$'}base/ready" ] || { echo 'No completed backup'; exit 1; }
    if [ -f "${'$'}account" ] && ! cmp -s "${'$'}account" "${'$'}base/profile" && [ "${'$'}(cat "${'$'}account")" != guest ]; then
        echo 'Different account detected; refusing to overwrite it'; exit 1
    fi
    if [ -f "${'$'}base/account.original" ]; then
        cp -p "${'$'}base/account.original" "${'$'}account.voyah-restore"
        mv -f "${'$'}account.voyah-restore" "${'$'}account"
    else rm -f "${'$'}account"; fi
    if [ ! -f "${'$'}base/fake.existed" ] && [ -e "${'$'}fake" ]; then rm -f "${'$'}fake"; fi
    for prop in ignoreActivation com.qinggan.user_started; do
        setprop "${'$'}prop" "${'$'}(cat "${'$'}base/${'$'}prop.original")"
    done
    rm -f "${'$'}base/watch"
    echo 'LOCAL_PROFILE_RESTORED'
)
apply() (
    step=lock
    lock
    step=check_account_directory
    [ -d /private/configs/token ] || { echo 'OEM account directory missing'; exit 1; }
    [ ! -L "${'$'}account" ] || { echo 'Unexpected account symlink'; exit 1; }
    if [ -f "${'$'}account" ] && [ "${'$'}(cat "${'$'}account")" != guest ]; then
        if [ ! -f "${'$'}base/profile" ] || ! cmp -s "${'$'}account" "${'$'}base/profile"; then
            echo 'Existing non-guest account preserved'; return 3
        fi
    fi
    if [ -d "${'$'}base" ] && [ ! -f "${'$'}base/ready" ]; then
        echo 'Incomplete previous backup; refusing to overwrite it'; exit 1
    fi
    step=backup
    mkdir -p "${'$'}base"; chmod 700 "${'$'}base"
    if [ ! -f "${'$'}base/ready" ]; then
        [ ! -f "${'$'}account" ] || cp -p "${'$'}account" "${'$'}base/account.original"
        [ ! -e "${'$'}fake" ] || touch "${'$'}base/fake.existed"
        for prop in ignoreActivation com.qinggan.user_started; do getprop "${'$'}prop" > "${'$'}base/${'$'}prop.original"; done
        if [ ! -f "${'$'}base/profile" ]; then
            identity=${'$'}(cat /proc/sys/kernel/random/uuid | tr -d '-')
            [ ${'$'}{#identity} = 32 ] || exit 1
            printf 'Voyah Iran%%0%%0%%%s%%100001' "${'$'}identity" > "${'$'}base/profile"
        fi
        touch "${'$'}base/ready"
    fi
    step=create_login_marker
    # Some production images have a read-only root and no /tmp. The OEM
    # ignoreActivation path can still be tested; never remount the system here.
    if [ ! -e "${'$'}fake" ]; then
        if mkdir -p /tmp && touch "${'$'}fake" && chmod 644 "${'$'}fake"; then
            echo 'LOCAL_PROFILE_MARKER_CREATED'
        else
            echo 'LOCAL_PROFILE_MARKER_UNAVAILABLE'
        fi
    fi
    step=write_profile
    if ! cmp -s "${'$'}account" "${'$'}base/profile"; then
        # Copy original metadata when present; atomic replacement prevents partial reads.
        if [ -f "${'$'}account" ]; then cp -p "${'$'}account" "${'$'}account.voyah-new"; else touch "${'$'}account.voyah-new"; chmod 644 "${'$'}account.voyah-new"; fi
        cat "${'$'}base/profile" > "${'$'}account.voyah-new"
        mv -f "${'$'}account.voyah-new" "${'$'}account"
        echo 'LOCAL_PROFILE_WRITTEN'
    fi
    step=set_ignore_activation
    [ "${'$'}(getprop ignoreActivation)" = true ] || setprop ignoreActivation true
    step=close_login_view
    [ "${'$'}(getprop com.qinggan.user_started)" = closeLoginView ] || setprop com.qinggan.user_started closeLoginView
    step=verify
    cmp -s "${'$'}account" "${'$'}base/profile" || { echo 'LOCAL_PROFILE_VERIFY_FAILED account'; exit 1; }
    [ "${'$'}(getprop ignoreActivation)" = true ] || { echo 'LOCAL_PROFILE_VERIFY_FAILED ignoreActivation'; exit 1; }
    [ "${'$'}(getprop com.qinggan.user_started)" = closeLoginView ] || { echo 'LOCAL_PROFILE_VERIFY_FAILED user_started'; exit 1; }
    touch "${'$'}base/enabled"
    echo 'LOCAL_PROFILE_SETTINGS_VERIFIED'
)
case "${'$'}operation" in
status) status;;
apply) apply; status;;
restore) restore; status;;
watch)
    apply
    touch "${'$'}base/watch"
    echo 'Monitoring every 10 seconds; restore or remove watch file to stop'
    while [ -f "${'$'}base/watch" ]; do sleep 10; [ ! -f "${'$'}base/watch" ] || apply || exit ${'$'}?; done
    ;;
esac

""".trimIndent()
}
