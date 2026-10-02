package com.abrar.voyahiran.core.bridge

import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.core.shell.CommandResult
import com.abrar.voyahiran.feature.steering.domain.StarBackTransport

/** Runs only in the authenticated root bridge, not in the app's Binder callback. */
object LegacySteeringBack {
    @Synchronized
    fun press(): CommandResult {
        if (Build.VERSION.SDK_INT != 28) return CommandResult(1, "STAR_BACK_UNSUPPORTED_PLATFORM")
        if (!BridgeSubscriptionSession.active) return CommandResult(1, "SUBSCRIPTION_REQUIRED")
        return try {
            val managerType = Class.forName("android.hardware.input.InputManager")
            val manager = managerType.getMethod("getInstance").invoke(null)
            val inject = managerType.getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
            val downTime = SystemClock.uptimeMillis()
            // Verified in the supplied API-28 InputManagerService: this overload calls
            // injectInputEventInternal(event, /*displayId=*/0, mode). No last-task cache.
            val accepted = StarBackTransport.press { action ->
                val event = KeyEvent(downTime, SystemClock.uptimeMillis(), action, KeyEvent.KEYCODE_BACK,
                    0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD)
                // ASYNC avoids waiting for app/UI dispatch while an OEM callback is active.
                inject.invoke(manager, event, 0) == true
            }
            AppLog.info("StarEvent", "backend=android9-input display=0 downUpAccepted=$accepted")
            CommandResult(if (accepted) 0 else 1, if (accepted) "STAR_BACK_CENTER_ACCEPTED" else "STAR_BACK_REJECTED")
        } catch (error: Exception) {
            AppLog.error("StarEvent", "backend=android9-input display=0 failed; no replay", error)
            CommandResult(1, "STAR_BACK_INJECTION_FAILED")
        }
    }
}
