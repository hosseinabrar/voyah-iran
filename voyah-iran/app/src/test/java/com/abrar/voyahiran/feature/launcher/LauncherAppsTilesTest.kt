package com.abrar.voyahiran.feature.launcher

import android.app.Application
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.View
import com.abrar.voyahiran.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30],application=Application::class)
class LauncherAppsTilesTest {
    @Test fun warmTilesAreDetachedAndNotSharedBetweenDisplays() {
        val c=ContextThemeWrapper(RuntimeEnvironment.getApplication(),R.style.Theme_VoyahLauncher)
        val entry=LauncherAppsEntry("test","Test",null)
        LauncherAppsTiles.warm(c,listOf(entry),false)
        shadowOf(Looper.getMainLooper()).idle()
        val central=LauncherAppsTiles.take(c,0,entry,false)
        assertTrue("Tile was measured before opening the drawer",central.view.measuredWidth>0)
        val passenger=LauncherAppsTiles.take(c,1,entry,false)
        assertNull(central.view.parent)
        assertNotSame(central.view,passenger.view)
        assertEquals(2,central.view.childCount)
        assertFalse(central.view.hasOnClickListeners())
        assertEquals("Test",central.view.contentDescription)
        LauncherAppsTiles.invalidate()
    }
    @Test fun invalidationCancelsPendingPreparation() {
        val c=RuntimeEnvironment.getApplication()
        val entry=LauncherAppsEntry("test","Test",null)
        LauncherAppsTiles.warm(c,listOf(entry),false)
        LauncherAppsTiles.invalidate()
        shadowOf(Looper.getMainLooper()).idle()
        val tile=LauncherAppsTiles.take(c,0,entry,true)
        assertEquals(View.VISIBLE,tile.view.visibility)
        assertNull(tile.view.parent)
    }
}
