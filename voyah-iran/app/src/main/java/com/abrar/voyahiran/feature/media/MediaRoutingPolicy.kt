package com.abrar.voyahiran.feature.media

/** OEM codes from Qinggan AndroidSink, not Android KeyEvent codes. */
object MediaRoutingPolicy {
    val keys = listOf(3, 4, 6, 8, 9)
    fun androidKey(oem: Int): Int? = when (oem) {
        3 -> 87; 4 -> 88; 6 -> 85; 8 -> 126; 9 -> 127; else -> null
    }
    data class Session<T>(val id: T, val playing: Boolean, val resumable: Boolean)
    fun <T> choose(sessions: List<Session<T>>, last: T?): T? =
        sessions.firstOrNull { it.playing }?.id
            ?: sessions.firstOrNull { it.id == last && it.resumable }?.id
            ?: sessions.firstOrNull { it.resumable }?.id
}

/** Latch the recipient for a complete OEM press; emit once at SHORT, never at DOWN/UP. */
class MediaPressPolicy<T> {
    data class Result<T>(val consumed: Boolean, val target: T? = null)
    private data class Press<T>(val target: T?, var emitted: Boolean = false)
    private val presses = mutableMapOf<Int, Press<T>>()
    @Synchronized fun route(key: Int, action: Int, target: T?): Result<T> {
        if (key !in MediaRoutingPolicy.keys) return Result(false)
        if (action == 0x1000) presses[key] = Press(target)
        val press = presses.getOrPut(key) { Press(target) }
        if (press.target == null) return Result(false)
        if (action == 0x2000 && !press.emitted) {
            press.emitted = true
            return Result(true, press.target)
        }
        return Result(true)
    }
    @Synchronized fun reset() = presses.clear()
}
