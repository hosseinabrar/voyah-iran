package com.abrar.voyahiran.core.bridge

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.core.shell.CommandResult

/** Runs in our root bridge. No injector attaches to the factory SystemService. */
object OemScreenSwapManager {
    private val runner = com.abrar.voyahiran.core.shell.ProcessCommandRunner(useBridge = false)
    private var settingAt = -10000L
    private var settingEnabled = false
    // app_process has no AMS application record: never use a synthetic Context's ContentResolver.
    private fun setting(force: Boolean): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (force || now - settingAt >= 2000) {
            val result = runner.run(listOf("/system/bin/settings", "get", "global", "voyah_iran_screen_swap"), 2)
            check(result.exitCode == 0 && !result.timedOut) { "Unable to read transfer setting: ${result.output}" }
            settingEnabled = result.output.trim() in listOf("1", "null")
            settingAt = now
        }
        return settingEnabled
    }
    private fun applicationInfo(pkg: String): ApplicationInfo? {
        val pm = Class.forName("android.app.ActivityThread").getMethod("getPackageManager").invoke(null)
        return Class.forName("android.content.pm.IPackageManager").getMethod("getApplicationInfo",
            String::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            .invoke(pm, pkg, 0, 0) as? ApplicationInfo
    }
    private val apiType by lazy { Class.forName("android.app.IActivityTaskManager") }
    private var prepared: List<ScreenSwapPlan.Stack> = emptyList()
    private var preparedAt = 0L
    private var lastMove = -10000L
    private fun enabled(force: Boolean) = BridgeSubscriptionSession.active &&
        setting(force) &&
        Class.forName("android.os.SystemProperties").getMethod("getInt", String::class.java, Int::class.javaPrimitiveType)
            .invoke(null, "persist.qg.canbus.bcm_screenAutoLiftFdb", 2) == 2
    private fun snapshot(api: Any): List<ScreenSwapPlan.Stack> {
        val list = apiType.getMethod("getAllStackInfos").invoke(api) as List<*>
        return list.filterNotNull().map { s ->
            fun field(name: String) = s.javaClass.getField(name).get(s)
            val component = field("topActivity") as? ComponentName
            val config = field("configuration")!!
            val wc = config.javaClass.getField("windowConfiguration").get(config)
            val pkg = component?.packageName.orEmpty()
            val info = if (pkg.isEmpty()) null else applicationInfo(pkg)
            ScreenSwapPlan.Stack(field("stackId") as Int, field("displayId") as Int, pkg,
                component?.className.orEmpty(), wc.javaClass.getMethod("getActivityType").invoke(wc) as Int,
                wc.javaClass.getMethod("getWindowingMode").invoke(wc) as Int, field("visible") as Boolean,
                pkg == "com.abrar.voyahiran" || (info != null && info.uid >= 10000 &&
                    info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0))
        }
    }
    @Synchronized fun command(operation: String): CommandResult {
        val moved = mutableListOf<ScreenSwapPlan.Stack>()
        var api: Any? = null
        fun move(s: ScreenSwapPlan.Stack, display: Int) {
            apiType.getMethod("moveStackToDisplay", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).invoke(api, s.id, display)
        }
        return try {
            if (!enabled(operation == "execute")) { prepared = emptyList(); return CommandResult(0, "OEM") }
            api = Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null)
            val stacks = snapshot(api!!)
            val moves = ScreenSwapPlan.moves(stacks)
            val now = SystemClock.elapsedRealtime()
            if (operation == "prepare") {
                prepared = moves; preparedAt = now
                AppLog.changed("ScreenSwap", "visible=" + stacks.filter { it.visible && it.display in 0..1 } + " plan=" + ScreenSwapPlan.signature(moves))
                return CommandResult(0, if (ScreenSwapPlan.empty(stacks)) "EMPTY" else if (moves.isEmpty()) "OEM" else "READY")
            }
            check(operation == "execute")
            if (ScreenSwapPlan.empty(stacks)) { prepared = emptyList(); return CommandResult(0, "EMPTY") }
            val previous = prepared; prepared = emptyList()
            check(moves.isNotEmpty() && now - preparedAt < 2000 && now - lastMove >= 1500 && moves == previous) {
                "Transfer target changed or debounce active; no transfer"
            }
            val sourceHome = if (moves.size == 1) stacks.firstOrNull {
                it.display == moves.single().display && ScreenSwapPlan.home(it) && it.pkg.isNotEmpty()
            } ?: error("Source Home not found; no move") else null
            lastMove = now
            // Validate both pinned identities before changing either display. Do not run
            // package-manager/snapshot/logging work while the passenger display is empty.
            val before = snapshot(api)
            for (s in moves) {
                val live = before.firstOrNull { it.id == s.id }
                check(live != null && live.display == s.display && live.pkg == s.pkg && live.activity == s.activity)
            }
            for (s in moves) {
                // Record intent before Binder call: an exception may arrive after the move happened.
                moved += s
                move(s, 1 - s.display)
            }
            val after = snapshot(api)
            for (s in moves) {
                check(after.any { it.id == s.id && it.display == 1 - s.display && it.pkg == s.pkg })
                AppLog.info("ScreenSwap", "MOVED stack=${s.id} package=${s.pkg} from=${s.display} to=${1-s.display}")
            }
            if (sourceHome != null) {
                val home = runner.run(listOf("/system/bin/am", "start", "--user", "0", "--display", sourceHome.display.toString(),
                    "-f", "0x10000000", "-n", "${sourceHome.pkg}/${sourceHome.activity}"), 3)
                check(home.exitCode == 0 && !home.timedOut && !home.output.contains("Error:")) { "Source Home failed: ${home.output}" }
                AppLog.info("ScreenSwap", "HOME display=${sourceHome.display} ${home.output}")
            }
            CommandResult(0, "DONE")
        } catch (e: Exception) {
            prepared = emptyList()
            moved.asReversed().forEach { s -> runCatching {
                val live = snapshot(api!!).firstOrNull { it.id == s.id }
                if (live != null && live.pkg == s.pkg && live.display == 1-s.display) {
                    move(s, s.display)
                    check(snapshot(api).any { it.id == s.id && it.display == s.display })
                    AppLog.info("ScreenSwap", "ROLLBACK stack=${s.id}")
                }
            }.onFailure { AppLog.error("ScreenSwap", "Rollback failed stack=${s.id}", it) } }
            val cause = (e as? java.lang.reflect.InvocationTargetException)?.targetException ?: e
            AppLog.error("ScreenSwap", "Transfer failed: ${cause.message}", cause)
            CommandResult(1, "Transfer failed: ${cause.javaClass.simpleName}: ${cause.message}")
        }
    }
}
