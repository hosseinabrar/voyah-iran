package com.abrar.voyahiran.feature.keyboard.data

import android.content.Context
import com.abrar.voyahiran.VoyahIranApplication
import com.abrar.voyahiran.feature.keyboard.domain.KeyboardRepository
import com.abrar.voyahiran.feature.keyboard.domain.KeyboardSnapshot

/** Keeps the user's selection separate from the temporarily effective keyboard. */
object KeyboardVipControl {
    private val mutableChanges = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val changes: kotlinx.coroutines.flow.StateFlow<Long> = mutableChanges
    private var appliedVip: Boolean? = null
    @Synchronized fun setEnabled(context: Context, enabled: Boolean): KeyboardSnapshot {
        val app = context.applicationContext as VoyahIranApplication
        check(app.subscriptions.vipAllowed) { "VIP required" }
        val result = KeyboardRepository(AndroidKeyboardPort(app)).setEnabled(enabled)
        check(app.getSharedPreferences("keyboard_vip", Context.MODE_PRIVATE).edit()
            .putBoolean("desired", enabled).commit())
        appliedVip = true
        return result
    }
    @Synchronized fun sync(context: Context) {
        val app = context.applicationContext as VoyahIranApplication
        val vip = app.subscriptions.vipAllowed
        if (appliedVip == vip) return
        val prefs = app.getSharedPreferences("keyboard_vip", Context.MODE_PRIVATE)
        val repository = KeyboardRepository(AndroidKeyboardPort(app))
        if (!prefs.contains("desired")) {
            val snapshot = repository.read()
            check(snapshot.current != null && snapshot.keyboards.any { it.id == snapshot.current }) { "Keyboard state not ready" }
            check(prefs.edit().putBoolean("desired", snapshot.enabled).commit())
        }
        if (prefs.getBoolean("desired", false)) repository.setEnabled(vip)
        appliedVip = vip
        mutableChanges.value += 1
    }
}
