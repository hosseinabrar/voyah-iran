package com.abrar.raha.feedback

import kotlin.math.sin

/** Shared motion curves; amplitudes taper outward without synchronizing the bars. */
object RahaMotion {
    private val times = floatArrayOf(0f, .13f, .24f, .35f, .56f, 2.1f)
    private val beats = floatArrayOf(0f, 1f, .46f, 1f, 0f, 0f)
    private val taper = floatArrayOf(.10f, .38f, .70f, 1f, .70f, .38f, .10f)
    private val rates = floatArrayOf(7.1f, 9.3f, 8.2f, 10.1f, 7.8f, 9.7f, 8.6f)
    private val phases = floatArrayOf(.2f, 2.4f, 4.1f, 1.1f, 5.3f, 3.2f, .9f)
    fun ease(value: Float): Float = value.coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
    fun heartbeat(seconds: Float): Float {
        val t = seconds % 2.1f
        for (i in 1 until times.size) if (t <= times[i]) {
            return beats[i-1] + (beats[i]-beats[i-1]) * ease((t-times[i-1])/(times[i]-times[i-1]))
        }
        return 0f
    }
    fun barHeight(index: Int, seconds: Float, energy: Float): Float {
        val amplitude = .12f + .88f * (.5f + .5f * sin(seconds * rates[index] + phases[index]))
        return 8f + 52f * amplitude * taper[index] * (.45f + .55f * energy.coerceIn(0f, 1f))
    }
    fun lineStart(seconds: Float): Float = lineEndPoint(seconds, false)
    fun lineEnd(seconds: Float): Float = lineEndPoint(seconds, true)
    private fun lineEndPoint(seconds: Float, end: Boolean): Float {
        if (seconds < .54f) return if (end) 43f - 86f * ease((seconds-.32f)/.22f) else -43f
        val half = (seconds-.54f)/1.2f
        val u = half % 1f
        val a = if (u < .5f) -43f else -43f + 86f * ease((u-.5f)*2)
        val b = if (u < .5f) -43f + 86f * ease(u*2) else 43f
        return if (half.toInt()%2 == 1) { if (end) -a else -b } else if (end) b else a
    }
}
