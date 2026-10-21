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
    private var rootIsCard = false
    private var cardRetryAt = 0L
    private var cardFollowRetryAt = 0L
    private val cardVisible get() = com.abrar.voyahiran.feature.launcher.VoyahLauncherActivity.mapPageVisible &&
        com.abrar.voyahiran.feature.launcher.LauncherEmbeddedMapSurface.automationDisplayId != null
    private val cardWatch = object : Runnable {
        override fun run() {
            if (cardVisible && enabled) inspectFocus()
            handler.postDelayed(this, 1500L)
        }
    }
    private var tickPending = false
    private val tick = Runnable { tickPending = false; inspect() }
    private val preferences get() = NavigationPreferences(this)
    private val enabled get() = (application as VoyahIranApplication).subscriptions.vipAllowed &&
        (preferences.autoDriving || preferences.voiceNavigationEnabled || com.abrar.voyahiran.feature.launcher.LauncherPreferences.clusterMap(this))

    override fun onServiceConnected() {
        instance = this
        handler.removeCallbacks(cardWatch)
        handler.postDelayed(cardWatch, 1500L)
        NeshanVoiceNavigation.serviceConnected = true
        AppLog.info(TAG, "accessibility connected; menu entry + visible navigation reader")
        inspectFocus()
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!enabled) { reset(); return }
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event?.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED || !session.inside) inspectFocus()
        if (session.inside) schedule()
    }
    private fun inspectFocus() {
        if (!enabled) { reset(); return }
        val root = automationRoot() ?: return
        try {
            val pkg = root.packageName?.toString()
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
        if (com.abrar.voyahiran.feature.launcher.LauncherMapWakeSession.restarting) { schedule(); return }
        val now = SystemClock.elapsedRealtime()
        val root = automationRoot()
        try {
            if (root != null && root.packageName?.toString() == NavigationPreferences.NESHAN) {
                clusterRouting=NeshanConfigStore.profile?.labels("routeIds")?.any {visible(root,it)} == true
                val route = NeshanVoiceNavigation.pending
                if (route != null) {
                    session.consume(); verifyUntil = 0L // A voice destination never starts free driving in this visit.
                    route.entered = true
                    if (!route.destinationSent) {
                        // No free-driving or route clicks while the normal entry initializes.
                        val profile = NeshanConfigStore.profile
                        val ids = if (profile != null) profile.labels("moreScopeIds") + profile.labels("activeIds") + profile.labels("routeIds")
                            else listOf("bottomNavigationViewMain", "closeMaterialCardViewDrivingControl", "address_panel_upcoming_step_frame")
                        route.startup.observe(ids.any { visible(root, it) }, now)
                    } else if (preferences.voiceNavigationEnabled) enterRoute(root, route, now)
                } else if (rootIsCard && preferences.autoDriving) {
                    maintainCardDriving(root, now)
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
    /** Re-arm only on a recognizable idle map, never on a destination/route preview. */
    private fun maintainCardDriving(root: AccessibilityNodeInfo, now: Long) {
        val profile = NeshanConfigStore.profile ?: return
        // Route guidance owns its existing drag + back-to-route flow. Never click
        // the free-driving follow control over an active route.
        val routing = profile.labels("routeIds").any { visible(root, it) }
        val driving = profile.labels("activeIds").any { visible(root, it) }
        val followVisible = visible(root, "followButton")
        val action = NeshanCardDrivingPolicy.action(routing, driving, followVisible)
        if (action != NeshanCardDrivingPolicy.Action.ENTER_DRIVING) {
            if (action == NeshanCardDrivingPolicy.Action.FOLLOW && now >= cardFollowRetryAt) {
                cardFollowRetryAt = now + 1500L
                if (clickVisibleId(root, "followButton"))
                    AppLog.info(TAG, "card free-driving follow clicked")
            }
            if (!followVisible) cardFollowRetryAt = 0L
            if (verifyUntil != 0L) AppLog.info(TAG, "card driving confirmed")
            session.consume(); verifyUntil = 0L; menuClicked = false; cardRetryAt = 0L
            return
        }
        // A manual route preview can exist before guidance exposes its route IDs.
        if (listOf("شروع", "رانندگی").any { hasVisibleLabel(root, it) }) {
            session.consume(); verifyUntil = 0L; menuClicked = false
            return
        }
        if (verifyUntil != 0L) return
        if (session.consumed) {
            if (now < cardRetryAt || !profile.labels("moreScopeIds").any { visible(root, it) }) return
            session.leave(); session.enter(now); menuClicked = false
            AppLog.info(TAG, "card idle map detected; entering free driving")
        }
        enterDriving(root, now)
        if (session.consumed) cardRetryAt = now + 10_000L
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
    private fun hasVisibleLabel(root: AccessibilityNodeInfo, label: String): Boolean {
        val nodes = root.findAccessibilityNodeInfosByText(label)
        return try {
            nodes.any { node -> node.isVisibleToUser && listOf(node.text, node.contentDescription).any {
                it != null && NeshanDrivingLabels.normalize(it.toString()) == label
            } }
        } finally { nodes.forEach { it.recycle() } }
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
    private fun clickVisibleId(root: AccessibilityNodeInfo, id: String): Boolean {
        val nodes = root.findAccessibilityNodeInfosByViewId("${NavigationPreferences.NESHAN}:id/$id")
        return try {
            nodes.any { it.isVisibleToUser && it.isEnabled && it.isClickable &&
                it.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
        } finally { nodes.forEach { it.recycle() } }
    }
    /** Click nodes in the app itself, never inject a gesture through the launcher card. */
    private fun automationRoot(): AccessibilityNodeInfo? {
        rootIsCard = false
        val cardDisplay = com.abrar.voyahiran.feature.launcher.LauncherEmbeddedMapSurface.automationDisplayId
            .takeIf { com.abrar.voyahiran.feature.launcher.VoyahLauncherActivity.mapPageVisible }
        val target = NeshanVoiceNavigation.pending?.displayId
        val candidates = if (android.os.Build.VERSION.SDK_INT >= 30) {
            val all = windowsOnAllDisplays
            buildList {
                all[0]?.let { addAll(it) }
                all[1]?.let { addAll(it) }
                if (cardDisplay != null && cardDisplay > 1) all[cardDisplay]?.let { addAll(it) }
                // Recycle windows from unrelated displays as well, without inspecting their content.
                for (i in 0 until all.size()) if (all.keyAt(i) != 0 && all.keyAt(i) != 1 && all.keyAt(i) != cardDisplay)
                    all.valueAt(i).forEach { it.recycle() }
            }
        } else windows // Android 9 does not expose windowsOnAllDisplays.
        try {
            for (window in candidates) {
                if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
                // Central background windows must not keep an old Neshan visit alive.
                val card = android.os.Build.VERSION.SDK_INT >= 30 && window.displayId == cardDisplay
                if (android.os.Build.VERSION.SDK_INT >= 30 && target != null && window.displayId != target) continue
                val passenger = android.os.Build.VERSION.SDK_INT >= 30 && window.displayId == 1
                if (!card && !passenger && !window.isActive && !window.isFocused) continue
                val root = window.root ?: continue
                if (root.packageName?.toString() == NavigationPreferences.NESHAN && root.isVisibleToUser) {
                    rootIsCard = card
                    return root
                }
                root.recycle()
            }
            if (android.os.Build.VERSION.SDK_INT >= 30 && target != null && target != 0) return null
            val root = rootInActiveWindow ?: return null
            if (candidates.any { it.id == root.windowId && it.type == AccessibilityWindowInfo.TYPE_APPLICATION }) return root
            root.recycle()
            return null
        } finally { candidates.forEach { it.recycle() } }
    }
    private fun reset() {
        handler.removeCallbacks(tick); tickPending = false; session.leave(); verifyUntil = 0L; menuClicked = false; cardFollowRetryAt = 0L
    }
    override fun onInterrupt() { NeshanVoiceNavigation.pending?.failure = "Accessibility interrupted"; reset() }
    override fun onDestroy() {
        handler.removeCallbacks(cardWatch)
        if (instance === this) instance = null
        NeshanVoiceNavigation.serviceConnected = false
        NeshanVoiceNavigation.pending?.failure = "Accessibility disconnected"
        reset(); super.onDestroy()
    }
    companion object {
        @Volatile var clusterRouting=false
            private set
        private const val TAG = "NeshanDriving"
        private var instance: NeshanDrivingService? = null
        internal fun inspectForRoute() { instance?.let { it.inspectFocus(); it.schedule() } }
    }
}
