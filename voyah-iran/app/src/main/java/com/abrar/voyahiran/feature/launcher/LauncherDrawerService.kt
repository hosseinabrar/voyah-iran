package com.abrar.voyahiran.feature.launcher

import android.accessibilityservice.AccessibilityService
import android.app.ActivityOptions
import android.content.Intent
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.abrar.voyahiran.core.logging.AppLog

/** Observe only the known central drawer button. Never intercept Home or coordinates. */
class LauncherDrawerService : AccessibilityService() {
    private var lastOpen = -2000L
    private val handler = Handler(Looper.getMainLooper())
    private var targetDisplay = 0
    private val open = Runnable { openDrawer(targetDisplay) }
    override fun onServiceConnected() { AppLog.info("LauncherDrawer", "button observer connected") }
    override fun onInterrupt() = Unit
    @Suppress("DEPRECATION")
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!com.abrar.voyahiran.core.compat.VehiclePlatform.launcherDrawer) return
        if (event?.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) return
        // A later navigation click cancels an opening still waiting for the OEM handler.
        handler.removeCallbacks(open)
        val source = event.source ?: run {
            AppLog.info("LauncherDrawer", "launcher click has no source node")
            return
        }
        val id: String?
        val windowDisplay: Int?
        try {
            id = source.viewIdResourceName
            val sourceWindow = source.window
            windowDisplay = try {
                sourceWindow?.let { readDisplayId(it) }
            } finally { sourceWindow?.recycle() }
        } finally { source.recycle() }
        if (!LauncherFallbackPolicy.accepts(
                com.abrar.voyahiran.core.compat.VehiclePlatform.launcherDrawer,
                event.packageName?.toString(), id)) return
        val eventDisplay = readDisplayId(event)
        val clickedDisplay = LauncherFallbackPolicy.buttonDisplay(eventDisplay, windowDisplay)
        if (eventDisplay == null) AppLog.info("LauncherDrawer",
            "resolve button display=$clickedDisplay window=$windowDisplay")
        val destination = LauncherFallbackPolicy.targetDisplay(clickedDisplay,
            com.abrar.voyahiran.core.compat.VehiclePlatform.isH97a)
        if (destination == null) {
            AppLog.info("LauncherDrawer", "keep OEM menu display=$clickedDisplay")
            return
        }
        targetDisplay = destination
        val now = SystemClock.elapsedRealtime()
        if (now - lastOpen < 1000) return
        lastOpen = now
        // Let the factory click finish opening its page before bringing ours forward.
        handler.postDelayed(open, 250)
    }
    private fun readDisplayId(value: Any): Int? = runCatching {
        (value.javaClass.getMethod("getDisplayId").invoke(value) as Int).takeIf { it >= 0 }
    }.getOrNull()
    private fun openDrawer(displayId: Int) {
        if (!com.abrar.voyahiran.core.compat.VehiclePlatform.launcherDrawer) return
        try {
            startActivity(Intent(this, LauncherDrawerActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK),
                ActivityOptions.makeBasic().apply { launchDisplayId = displayId }.toBundle())
            AppLog.info("LauncherDrawer", "drawer opened from OEM button display=$displayId")
        } catch (e: Exception) { AppLog.error("LauncherDrawer", "open failed", e) }
    }
    override fun onDestroy() { handler.removeCallbacks(open); super.onDestroy() }
}
