package com.abrar.voyahiran.feature.launcher

import android.app.Activity
import android.app.Application
import android.graphics.Color
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,30],application=Application::class)
class LauncherWindowThemeTest {
    @Suppress("DEPRECATION")
    @Test fun vehicleThemeUpdatesBothBarsAndPreservesLayoutFlags() {
        val controller=Robolectric.buildActivity(Activity::class.java).setup()
        val window=controller.get().window
        window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        val lightFlags=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        LauncherWindowTheme.apply(window,true)
        assertEquals(0xffe5ebf3.toInt(),window.statusBarColor)
        assertEquals(window.statusBarColor,window.navigationBarColor)
        assertEquals(lightFlags,window.decorView.systemUiVisibility and lightFlags)
        LauncherWindowTheme.apply(window,false)
        assertEquals(Color.BLACK,window.statusBarColor)
        assertEquals(Color.BLACK,window.navigationBarColor)
        assertEquals(View.SYSTEM_UI_FLAG_LAYOUT_STABLE,window.decorView.systemUiVisibility)
        controller.pause().stop().destroy()
    }
}
