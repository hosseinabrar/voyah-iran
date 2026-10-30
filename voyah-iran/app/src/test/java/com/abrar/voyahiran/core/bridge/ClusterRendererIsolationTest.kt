package com.abrar.voyahiran.core.bridge

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class ClusterRendererIsolationTest {
    @Test fun helperIsPackagedWithoutLoadingProtectedApplicationCode() {
        val dex = RuntimeEnvironment.getApplication().assets
            .open("cluster/renderer/classes.dex").use { it.readBytes() }
        assertEquals("dex\n", String(dex.copyOfRange(0, 4), Charsets.US_ASCII))
        val strings = String(dex, Charsets.ISO_8859_1)
        assertTrue(strings.contains("ClusterTextAssets;"))
        assertTrue(strings.contains("ClusterRoundedImageView;"))
        assertFalse(strings.contains("JniBridge"))
        assertFalse(strings.contains("ProxyComponentFactory"))
        val script = ClusterMusicScript.source
        assertTrue(script.contains("createPackageContext('com.abrar.voyahiran',0)"))
        assertTrue(script.contains("dalvik.system.InMemoryDexClassLoader"))
        assertFalse(script.contains("ownContext.getClassLoader()"))
    }
}
