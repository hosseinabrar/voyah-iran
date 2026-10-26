package com.abrar.voyahiran.feature.launcher

import android.graphics.Bitmap
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application=android.app.Application::class,sdk=[28])
class LauncherCrashSafetyTest {
    @Test fun missingSeatFrameDoesNotEscapeOrRetryEveryFrame() {
        val view=PassengerSeatsView(RuntimeEnvironment.getApplication())
        val method=PassengerSeatsView::class.java.getDeclaredMethod("load",String::class.java).apply {isAccessible=true}
        val first=method.invoke(view,"missing-test-frame") as Bitmap
        val second=method.invoke(view,"missing-test-frame") as Bitmap
        assertSame(first,second)
        assertEquals(1,first.width)
        assertEquals(0,first.getPixel(0,0))
    }
    @Test fun gpuLifecycleDoesNotBlockCallerOrReuseFailedContextForCleanup() {
        val source=File("src/main/java/com/abrar/voyahiran/feature/launcher/MapFrameRelay.java").readText()
        assertFalse(source.contains("init.get("))
        assertFalse(source.contains("done.await("))
        val cleanup=source.substringAfter("void close()")
        assertFalse(cleanup.contains("destroy(card)"))
        assertTrue(cleanup.contains("cleanup(()->{if(source!=null)source.release();})"))
        assertTrue(cleanup.contains("finally{thread.quitSafely();}"))
    }
}
