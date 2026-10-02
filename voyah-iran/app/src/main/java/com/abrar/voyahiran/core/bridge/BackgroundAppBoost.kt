package com.abrar.voyahiran.core.bridge

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.SystemClock
import android.util.Xml
import java.io.File
import org.xmlpull.v1.XmlPullParser
import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import kotlin.concurrent.thread

/** Observe transitions, not a recurring kill-all sweep. All displays count as foreground. */
internal object BackgroundAppBoost {
    private val runner = ProcessCommandRunner(useBridge = false)
    private val transitions = BackgroundAppTransitions()
    private var started = false
    private val legacy get() = Build.VERSION.SDK_INT == 28
    private val owner get() = if (legacy) "android.app.ActivityManager" else "android.app.ActivityTaskManager"
    private val api get() = Class.forName(if (legacy) "android.app.IActivityManager" else "android.app.IActivityTaskManager")

    private fun visible(): Set<String> {
        check(Build.VERSION.SDK_INT in listOf(28, 30))
        val service = Class.forName(owner).getMethod("getService").invoke(null)
        val stacks = api.getMethod("getAllStackInfos").invoke(service) as List<*>
        return stacks.filterNotNull().filter { it.javaClass.getField("visible").getBoolean(it) }
            .map { s ->
                check(s.javaClass.getField("userId").getInt(s) == 0) { "Other user visible" }
                val top = s.javaClass.getField("topActivity").get(s) as? ComponentName
                checkNotNull(top) { "Incomplete foreground snapshot" }.packageName
            }.toSet().also { check(it.isNotEmpty()) { "No reliable foreground snapshot" } }
    }
    private fun userApp(pkg: String): Boolean {
        if (pkg == "com.abrar.voyahiran") return false
        val pm = Class.forName("android.app.ActivityThread").getMethod("getPackageManager").invoke(null)
        val info = Class.forName("android.content.pm.IPackageManager").getMethod("getApplicationInfo",
            String::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            .invoke(pm, pkg, 0, 0) as? ApplicationInfo ?: return false
        return info.uid >= 10000 && info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
    }
    private fun selectedMediaApps(): Set<String> {
        // The bridge is a separate root process: read the committed preferences freshly,
        // rather than using a stale cross-process SharedPreferences cache or a second copy.
        val file = File("/data/user/0/com.abrar.voyahiran/shared_prefs/display_media_map.xml")
        val selected = mutableSetOf<String>()
        file.inputStream().use { input ->
            val parser = Xml.newPullParser()
            parser.setInput(input, "UTF-8")
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "string" &&
                    parser.getAttributeValue(null, "name") in setOf("navigator", "musicPlayer")) {
                    parser.nextText().substringBefore('#').trim().takeIf { it.isNotEmpty() }?.let(selected::add)
                }
                parser.next()
            }
        }
        // Read/parse failure propagates: never stop apps when their exemptions are unknown.
        return selected
    }
    private fun read(vararg args: String): String {
        val result = runner.run(args.toList(), 3)
        check(result.exitCode == 0 && !result.timedOut && !Regex("Error|Exception|denied", RegexOption.IGNORE_CASE).containsMatchIn(result.output))
        return result.output.trim()
    }
    private fun backgroundAllowed(pkg: String): Boolean {
        // Same definition as PermissionRepository's background checkbox. An unreadable
        // permission aborts the candidate rather than being interpreted as a denial.
        val idle = read("/system/bin/dumpsys", "deviceidle", "whitelist", "=$pkg")
        check(idle in listOf("true", "false"))
        val modes = listOf("RUN_ANY_IN_BACKGROUND", "RUN_IN_BACKGROUND").map { op ->
            val output = read("/system/bin/cmd", "appops", "get", "--user", "0", pkg, op)
            if (output.contains("No operations")) "default"
            else Regex("$op: (allow|deny|ignore|default|foreground)").find(output)?.groupValues?.get(1)
                ?: error("Unknown background permission")
        }
        return idle == "true" && modes.all { it in listOf("allow", "default") }
    }
    private fun tick() {
        if (!SystemLoggingControl.enabled() || !BridgeSubscriptionSession.active) {
            transitions.reset(); return
        }
        val current = visible()
        val now = SystemClock.elapsedRealtime()
        for (pkg in transitions.sample(current, now)) {
            if (!userApp(pkg) || pkg in selectedMediaApps() || backgroundAllowed(pkg)) continue
            val keyboard = read("/system/bin/settings", "--user", "0", "get", "secure", "default_input_method")
            check(keyboard.contains('/')) { "Active keyboard unknown" }
            if (keyboard.substringBefore('/') == pkg) continue
            SystemLoggingControl.whileEnabled {
                // Recheck after permission IPCs, including passenger display transfers.
                if (BridgeSubscriptionSession.active && pkg !in visible() && userApp(pkg) && pkg !in selectedMediaApps()) {
                    val result = runner.run(listOf("/system/bin/am", "force-stop", "--user", "0", pkg), 5)
                    AppLog.info("BackgroundBoost", "background stop package=$pkg exit=${result.exitCode}")
                }
            }
        }
    }
    @Synchronized fun start() {
        if (started) return
        started = true
        thread(name = "voyah-background-boost", isDaemon = true) {
            while (true) {
                try { tick() } catch (e: Exception) {
                    transitions.reset()
                    AppLog.changed("BackgroundBoost", "snapshot skipped: ${e.javaClass.simpleName}: ${e.message}")
                }
                Thread.sleep(2000)
            }
        }
    }
}
