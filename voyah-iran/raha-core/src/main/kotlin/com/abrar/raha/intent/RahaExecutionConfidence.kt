package com.abrar.raha.intent

import kotlin.math.floor
import kotlin.math.round

/** Match the web's four-decimal score followed by Math.round(score * 100). */
object RahaExecutionConfidence {
    fun isDisplayedHundred(rawScore: Double): Boolean {
        if (!rawScore.isFinite() || rawScore !in 0.0..1.0) return false
        val webScore = round(rawScore * 10000) / 10000
        return floor(webScore * 100 + 0.5).toInt() == 100
    }
}
