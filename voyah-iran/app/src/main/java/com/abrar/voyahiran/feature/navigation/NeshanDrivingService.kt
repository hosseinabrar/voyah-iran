package com.abrar.voyahiran.feature.navigation

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.abrar.voyahiran.VoyahIranApplication
import com.abrar.voyahiran.core.logging.AppLog

/** Operates Neshan's visible menu and reads its upcoming-turn panel, without coordinates. */
@Suppress("DEPRECATION") // Nodes are pooled on the car's Android 11 and must be recycled there.
class NeshanDrivingService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val session = NeshanEntrySession()
    private var verifyUntil = 0L
    private var menuClicked = false
    private var tickPending = false
    private val tick = Runnable { tickPending = false; inspect() }
    private val preferences get() = NavigationPreferences(this)
    private val enabled get() = (application as VoyahIranApplication).subscriptions.allowed &&
        (preferences.autoDriving || preferences.voiceNavigationEnabled)

    override fun onServiceConnected() {
        NeshanVoiceNavigation.serviceConnected = true
        AppLog.info(TAG, "accessibility connected; menu entry + visible navigation reader")
        inspectFocus()
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!enabled) { reset(); return }
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || !session.inside) inspectFocus()
        if (session.inside) schedule()
    }
    private fun inspectFocus() {
        if (!enabled) { reset(); return }
        val root = rootInActiveWindow ?: return
        try {
            val pkg = root.packageName?.toString()
            if (!isCentralApplicationWindow(root)) return
            if (pkg == NavigationPreferences.NESHAN) {
                NeshanVoiceNavigation.pending?.entered = true
                if (!session.inside) AppLog.info(TAG, "foreground visit started")
                session.enter(SystemClock.elapsedRealtime())
                schedule()
            } else if (pkg != null && pkg != "com.android.systemui" &&
                packageManager.getLaunchIntentForPackage(pkg) != null) {
                if (session.inside) AppLog.info(TAG, "foreground visit ended next=$pkg")
                NeshanVoiceNavigation.pending?.takeIf { it.entered }?.failure = "Neshan left foreground before navigation completed"
                reset()
            }
        } finally { root.recycle() }
    }
    private fun schedule() {
        if (!tickPending) { tickPending = true; handler.postDelayed(tick, 350) }
    }
    private fun inspect() {
        if (!enabled || !session.inside) { reset(); return }
        val now = SystemClock.elapsedRealtime()
        val root = rootInActiveWindow
        try {
            if (root != null && root.packageName?.toString() == NavigationPreferences.NESHAN && isCentralApplicationWindow(root)) {
                val route = NeshanVoiceNavigation.pending
                if (route != null) {
                    session.consume(); verifyUntil = 0L // A voice destination never starts free driving in this visit.
                    route.entered = true
                    if (preferences.voiceNavigationEnabled) enterRoute(root, route, now)
                } else if (preferences.autoDriving && (!session.consumed || verifyUntil != 0L)) enterDriving(root, now)
            }
        } catch (e: RuntimeException) {
            AppLog.error(TAG, "inspect failed", e)
        } finally { root?.recycle() }
        if (verifyUntil != 0L && now >= verifyUntil) {
            AppLog.info(TAG, "free-driving not confirmed; check GPS, permissions or changed Neshan UI")
            verifyUntil = 0L
        }
        if (NeshanVoiceNavigation.pending != null || (preferences.autoDriving && (!session.consumed || verifyUntil != 0L))) schedule()
    }
    private fun enterRoute(root: AccessibilityNodeInfo, request: NeshanVoiceNavigation.Request, now: Long) {
        val progress = request.progress
        if (request.completed || request.failure != null || !progress.ready(now)) return
        val labels = when (progress.step) {
            NeshanRouteProgress.Step.DRIVING -> listOf("رانندگی")
            NeshanRouteProgress.Step.START -> listOf("شروع")
            else -> emptyList()
        }
        if (labels.any { clickLabel(root, NeshanDrivingLabels.normalize(it)) }) {
            AppLog.info("NeshanRoute", "clicked stage=${progress.step} destination=${request.destination.name}")
            progress.clicked(now)
        }
        val routeIds = NeshanConfigStore.profile?.labels("routeIds")
            ?: listOf("address_panel_upcoming_step_frame", "streetNameNormal", "streetNameCompact")
        if (progress.verify(routeIds.any { visible(root, it) }, now)) {
            request.completed = true
            AppLog.info("NeshanRoute", "route guidance verified destination=${request.destination.name}")
        }
        if (now - progress.startedAt > 43_000) {
            dump(root, "voice-route-timeout-${progress.step}")
            request.failure = "Navigation buttons or guidance not found: ${progress.step}"
        }
    }
    private fun enterDriving(root: AccessibilityNodeInfo, now: Long) {
        val profile = NeshanConfigStore.profile
        if (profile == null) {
            AppLog.changed(TAG, "Waiting for a downloaded profile matching installed Neshan")
            session.leave(); session.enter(now) // Config/network delay must not consume the entry window.
            return
        }
        if (profile.labels("activeIds").any { visible(root, it) }) {
            AppLog.info(TAG, if (verifyUntil != 0L) "free-driving verified" else "already driving; no action")
            session.consume(); verifyUntil = 0L
            return
        }
        // Never replace an active route with free driving.
        if (profile.labels("routeIds").any { visible(root, it) }) {
            AppLog.info(TAG, "route guidance already visible; skip free-driving entry")
            session.consume(); verifyUntil = 0L
            return
        }
        if (verifyUntil != 0L) return
        if (!session.canAttempt(now)) {
            AppLog.info(TAG, "menu entry timed out menuClicked=$menuClicked")
            dump(root, "entry-timeout")
            session.consume()
            return
        }
        // This menu route is available at rest, unlike the speed-dependent map button.
        if (profile.labels("entryLabels").map(NeshanDrivingLabels::normalize).any { clickLabel(root, it) }) {
            session.consume() // Do not re-enter after the user manually leaves the mode.
            verifyUntil = now + 8000L
            AppLog.info(TAG, "free-driving menu item clicked")
        } else if (!menuClicked) {
            val label = NeshanDrivingLabels.normalize(profile.driving.getString("moreLabel"))
            for (id in profile.labels("moreScopeIds")) {
                val nodes = root.findAccessibilityNodeInfosByViewId("${NavigationPreferences.NESHAN}:id/$id")
                try { menuClicked = nodes.any { it.isVisibleToUser && clickLabel(it, label) } }
                finally { nodes.forEach { it.recycle() } }
                if (menuClicked) break
            }
            if (menuClicked) AppLog.info(TAG, "More tab clicked; profile=${profile.id}")
        }
    }
    private fun clickLabel(root: AccessibilityNodeInfo, label: String): Boolean {
        val nodes = if (listOf(root.text, root.contentDescription).any { it?.toString()?.let(NeshanDrivingLabels::normalize) == label })
            listOf(AccessibilityNodeInfo.obtain(root)) else root.findAccessibilityNodeInfosByText(label)
        try {
            for (node in nodes) {
                val exact = listOf(node.text, node.contentDescription).any { it != null && NeshanDrivingLabels.normalize(it.toString()) == label }
                if (!exact || !node.isVisibleToUser || !node.isEnabled) continue
                if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    AppLog.info(TAG, "label clicked label=$label id=${node.viewIdResourceName}")
                    return true
                }
                var parent = node.parent
                var depth = 0
                while (parent != null && depth++ < 4) {
                    val current = parent
                    parent = null
                    try {
                        if (current.isVisibleToUser && current.isEnabled && current.isClickable) {
                            val clicked = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            AppLog.info(TAG, "label parent click label=$label id=${current.viewIdResourceName} result=$clicked")
                            if (clicked) return true
                        }
                        parent = current.parent
                    } finally { current.recycle() }
                }
                parent?.recycle()
            }
            return false
        } finally { nodes.forEach { it.recycle() } }
    }
    private fun dump(root: AccessibilityNodeInfo, reason: String) {
        var count = 0
        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > 18 || count >= 120 || !node.isVisibleToUser) return
            count++
            AppLog.info("NeshanUi", "$reason id=${node.viewIdResourceName} text=${node.text?.take(100)} description=${node.contentDescription?.take(100)} clickable=${node.isClickable}")
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                try { visit(child, depth + 1) } finally { child.recycle() }
            }
        }
        visit(root, 0)
    }
    private fun visible(root: AccessibilityNodeInfo, id: String): Boolean {
        val nodes = root.findAccessibilityNodeInfosByViewId("${NavigationPreferences.NESHAN}:id/$id")
        return try { nodes.any { it.isVisibleToUser } } finally { nodes.forEach { it.recycle() } }
    }
    private fun isCentralApplicationWindow(root: AccessibilityNodeInfo): Boolean {
        val currentWindows = windows
        return try { currentWindows.any { it.id == root.windowId && (android.os.Build.VERSION.SDK_INT < 30 || it.displayId == 0) && it.type == AccessibilityWindowInfo.TYPE_APPLICATION } }
        finally { currentWindows.forEach { it.recycle() } }
    }
    private fun reset() {
        handler.removeCallbacks(tick); tickPending = false; session.leave(); verifyUntil = 0L; menuClicked = false
    }
    override fun onInterrupt() { NeshanVoiceNavigation.pending?.failure = "Accessibility interrupted"; reset() }
    override fun onDestroy() {
        NeshanVoiceNavigation.serviceConnected = false
        NeshanVoiceNavigation.pending?.failure = "Accessibility disconnected"
        reset(); super.onDestroy()
    }
    companion object { private const val TAG = "NeshanDriving" }
}
