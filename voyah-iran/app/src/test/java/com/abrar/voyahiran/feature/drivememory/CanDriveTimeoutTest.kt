package com.abrar.voyahiran.feature.drivememory

import android.app.Application
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import com.abrar.voyahiran.feature.drivememory.data.CanDriveGateway
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class CanDriveTimeoutTest {
    private class NeverConnects : ContextWrapper(RuntimeEnvironment.getApplication()) {
        var binds = 0
        var unbinds = 0
        override fun getApplicationContext() = this
        override fun bindService(intent: Intent, conn: ServiceConnection, flags: Int): Boolean {
            binds++; return true
        }
        override fun unbindService(conn: ServiceConnection) { unbinds++ }
    }
    @Test fun bindTimeoutIsAnOperationFailureAndNextRequestStillRuns() = runBlocking {
        val context = NeverConnects()
        val gateway = CanDriveGateway(context) {}
        repeat(2) {
            try { gateway.read(); fail("Expected timeout") }
            catch (error: IOException) { assertTrue(error.message!!.contains("timed out")) }
            assertTrue(currentCoroutineContext().isActive)
        }
        assertEquals(2, context.binds)
        assertEquals(2, context.unbinds)
    }
    @Test fun parentCancellationIsNotConvertedIntoRetryableFailure() = runBlocking {
        val context = NeverConnects()
        val gateway = CanDriveGateway(context) {}
        try {
            withTimeout(50) { gateway.read() }
            fail("Expected cancellation")
        } catch (_: TimeoutCancellationException) { }
        assertEquals(1, context.unbinds)
    }
}
