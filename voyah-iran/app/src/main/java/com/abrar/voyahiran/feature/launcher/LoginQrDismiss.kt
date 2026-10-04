package com.abrar.voyahiran.feature.launcher

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import com.abrar.voyahiran.core.compat.VehiclePlatform
import com.abrar.voyahiran.core.logging.AppLog

/** Invoke the OEM QR click handler, never arbitrary coordinates or a generic Back/Home. */
internal class LoginQrDismiss(private val service: AccessibilityService) {
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var attempts = 0
    private var blockedUntil = 0L
    private fun enabled() = VehiclePlatform.localProfileTest &&
        Settings.Global.getInt(service.contentResolver, "voyah_iran_login_dismiss", 0) == 1
    fun scan() {
        if (running || SystemClock.elapsedRealtime() < blockedUntil || !enabled()) return
        val node = qr() ?: return
        node.recycle()
        running = true
        attempts = 0
        AppLog.info("LoginDismiss", "OEM activation QR detected; invoking standard guest exit")
        tick()
    }
    private fun qr(): AccessibilityNodeInfo? {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        service.rootInActiveWindow?.let { roots.add(it) }
        val windows = service.windows
        try {
            windows.forEach { it.root?.let { root -> roots.add(root) } }
            for (root in roots) {
                if (root.packageName?.toString() != "com.qinggan.app.login") continue
                // Offline activation displays refresh_loading over the QR image.
                for (id in listOf("refresh_loading", "aliveQrIV")) {
                    val nodes = root.findAccessibilityNodeInfosByViewId("com.qinggan.app.login:id/$id")
                    val found = nodes.firstOrNull { it.isVisibleToUser && it.isEnabled && it.isClickable }
                    nodes.filter { it !== found }.forEach { it.recycle() }
                    if (found != null) return found
                }
            }
            return null
        } finally {
            roots.forEach { it.recycle() }
            windows.forEach { it.recycle() }
        }
    }
    fun start() {
        AppLog.info("LoginDismiss", "observer connected eligible=${VehiclePlatform.localProfileTest} enabled=${enabled()}")
        val deadline = SystemClock.elapsedRealtime() + 90000
        val poll = object : Runnable {
            override fun run() {
                scan()
                if (SystemClock.elapsedRealtime() < deadline) handler.postDelayed(this, 1000)
            }
        }
        handler.post(poll)
    }
    private fun tick() {
        if (!enabled()) { running = false; return }
        val node = qr()
        if (node == null) {
            AppLog.info("LoginDismiss", "QR no longer visible after clicks=$attempts")
            running = false
            blockedUntil = SystemClock.elapsedRealtime() + 3000
            return
        }
        if (attempts >= 12) {
            node.recycle()
            running = false
            blockedUntil = SystemClock.elapsedRealtime() + 60000
            AppLog.info("LoginDismiss", "QR still visible; bounded attempt stopped")
            return
        }
        val clicked = try { node.performAction(AccessibilityNodeInfo.ACTION_CLICK) } finally { node.recycle() }
        attempts++
        AppLog.info("LoginDismiss", "QR click=$attempts accepted=$clicked")
        // First click can reset OEM's fast-click counter; six clicks cover five increments.
        handler.postDelayed({ tick() }, if (attempts % 6 == 0) 700 else 100)
    }
    fun close() { handler.removeCallbacksAndMessages(null); running = false }
}
