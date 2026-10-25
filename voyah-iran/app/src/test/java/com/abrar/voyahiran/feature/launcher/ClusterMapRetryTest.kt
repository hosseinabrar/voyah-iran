package com.abrar.voyahiran.feature.launcher

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ClusterMapRetryTest {
    @Test fun retriesAfterThreeSecondsThenEveryFiveSeconds() {
        val retry=ClusterMapRetry()
        assertEquals(listOf(3000L,5000L,5000L,5000L),List(4){retry.nextDelay()})
        repeat(1000){assertEquals(5000L,retry.nextDelay())}
        retry.reset()
        assertEquals(3000L,retry.nextDelay())
    }
    @Test fun recoveryIsScheduledAndCancelledWithOwner() {
        val root=File("src/main/java/com/abrar/voyahiran/feature/launcher")
        val embedded=File(root,"LauncherEmbeddedMapSurface.kt").readText()
        assertFalse(embedded.contains("relayFailed"))
        assertTrue(embedded.contains("handler.postDelayed(retryRun,wait)"))
        assertTrue(embedded.contains("attempt!=generation"))
        assertTrue(embedded.contains("closed=true;generation++;handler.removeCallbacks(retryRun)"))
        val cluster=File(root,"LauncherClusterMap.kt").readText()
        assertTrue(cluster.contains("retryJob=scope.launch"))
        assertTrue(cluster.contains("failed && session==id && mapInCard && leaving==null && available(c)"))
        assertTrue(cluster.contains("if(!inCard && retryRelay===relay)"))
    }
}
