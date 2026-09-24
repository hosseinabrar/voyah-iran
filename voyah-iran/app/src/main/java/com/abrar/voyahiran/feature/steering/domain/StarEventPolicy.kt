package com.abrar.voyahiran.feature.steering.domain

interface KeyPayload
data class RoutedKey(val code: Int, val action: Int, val flags: Int = 0, val display: Int = 0, val payload: KeyPayload? = null)
data class KeyRouting(val consume: Boolean, val events: List<RoutedKey> = emptyList())

/** Only the firmware's SHORT action is replaced; other actions retain their original mapping. */
class StarEventPolicy(private val previous: Int) {
    private var longPress = false
    private var shortPress = false
    private val pending = mutableListOf<RoutedKey>()
    @Synchronized
    fun route(code: Int, action: Int, flags: Int = 0, display: Int = 0, payload: KeyPayload? = null): KeyRouting {
        if (code != SteeringKeys.STAR) return KeyRouting(false)
        if (action == 0x1000) { longPress = false; shortPress = false; pending.clear() }
        if (action != 0x1000 && action != 0x10000 && action != SHORT) longPress = true
        if (action == SHORT && !longPress) {
            pending.clear()
            if (shortPress) return KeyRouting(true)
            shortPress = true
            // Steering Back belongs to the driver/center display, never the last
            // focused passenger task. Do not replay display-specific source extras.
            return KeyRouting(true, listOf(RoutedKey(SteeringKeys.BACK, SHORT, display = CENTER_DISPLAY)))
        }
        if (previous == SteeringKeys.STAR) return KeyRouting(false)
        val original = RoutedKey(previous, action, flags, display, payload)
        // Do not leak DOWN/UP to an old action before the firmware classifies a short press.
        if (!longPress && (action == 0x1000 || action == 0x10000)) {
            if (!shortPress) pending += original
            return KeyRouting(true)
        }
        val replay = pending.toList() + original
        pending.clear()
        return KeyRouting(true, replay)
    }
    companion object { const val SHORT = 0x2000; const val CENTER_DISPLAY = 0 }
}
