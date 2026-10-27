package com.abrar.voyahiran.feature.launcher

/** Acknowledgements belong to a surface generation, not the lifetime of a lease. */
internal class ClusterMapReadiness {
    @Volatile var generation = 0L
        private set
    var acknowledged = false
        private set

    fun invalidate() { generation++; acknowledged = false }

    fun reply(sentGeneration: Long, locallyReady: Boolean, remoteReady: Boolean) {
        if (sentGeneration == generation) acknowledged = locallyReady && remoteReady
    }
}
