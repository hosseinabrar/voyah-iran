package com.abrar.voyahiran.feature.media

import com.abrar.voyahiran.core.bridge.BridgePolicy
import org.junit.Assert.*
import org.junit.Test

class ClusterMusicPolicyTest {
    @Test fun progressClampsAtTrackEndAndHandlesUnknownDuration() {
        assertEquals(10000L, ClusterMusicPolicy.position(9500, 1000, 10000))
        assertEquals(10500L, ClusterMusicPolicy.position(9500, 1000, 0))
        assertEquals(0L, ClusterMusicPolicy.position(-1, -1, 10000))
        assertEquals(3000L, ClusterMusicPolicy.position(3000, 0, 10000))
    }
    @Test fun bridgeDoesNotAcceptCallerControlledFilesOrScripts() {
        assertNotNull(BridgePolicy.resolve(listOf("cluster-music", "refresh")))
        assertNotNull(BridgePolicy.resolve(listOf("cluster-music", "clear")))
        assertNull(BridgePolicy.resolve(listOf("cluster-music", "/data/local/tmp/script.js")))
        assertNull(BridgePolicy.resolve(listOf("cluster-music", "refresh", "/some/file")))
    }
}
