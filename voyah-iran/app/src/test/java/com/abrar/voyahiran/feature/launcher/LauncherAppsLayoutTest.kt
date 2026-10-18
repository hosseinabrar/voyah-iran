package com.abrar.voyahiran.feature.launcher

import android.app.Application
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30],application=Application::class)
class LauncherAppsLayoutTest {
    @Test fun twentySevenTilesPerPageAndIndependentPages() {
        val controller=Robolectric.buildActivity(LauncherAppsActivity::class.java).create()
        val activity=controller.get()
        val rows=(1..55).map {LauncherAppsEntry("app$it","App $it",ColorDrawable(Color.BLUE),null)}
        LauncherAppsTiles.warm(activity,rows,false)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        LauncherAppsActivity::class.java.getDeclaredField("entries").apply {isAccessible=true}.set(activity,rows)
        LauncherAppsActivity::class.java.getDeclaredMethod("render",Boolean::class.javaPrimitiveType).apply {isAccessible=true}.invoke(activity,true)
        val pager=LauncherAppsActivity::class.java.getDeclaredField("pager").apply {isAccessible=true}.get(activity) as LauncherPager
        pager.measure(View.MeasureSpec.makeMeasureSpec(1775,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(642,View.MeasureSpec.EXACTLY))
        pager.layout(0,0,1775,642)
        assertEquals(3,pager.childCount)
        val first=pager.getChildAt(0) as ViewGroup
        assertEquals(27,first.childCount)
        assertEquals(27,(pager.getChildAt(1) as ViewGroup).childCount)
        assertEquals(1,(pager.getChildAt(2) as ViewGroup).childCount)
        assertEquals(first.getChildAt(0).top,first.getChildAt(8).top)
        assertTrue(first.getChildAt(9).top>=first.getChildAt(0).bottom)
        assertTrue(first.getChildAt(26).right<=1775)
        assertTrue(first.getChildAt(26).bottom<=642)
        controller.destroy()
    }
}
