package com.abrar.voyahiran.feature.media

internal object ClusterMusicPolicy {
    fun position(position: Long, elapsed: Long, duration: Long): Long {
        val next = position.coerceAtLeast(0).coerceAtMost(Int.MAX_VALUE.toLong()) + elapsed.coerceAtLeast(0).coerceAtMost(Int.MAX_VALUE.toLong())
        return if (duration > 0) next.coerceAtMost(duration) else next
    }
}
