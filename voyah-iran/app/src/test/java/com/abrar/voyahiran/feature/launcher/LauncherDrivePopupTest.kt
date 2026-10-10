package com.abrar.voyahiran.feature.launcher

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30],application=Application::class,qualifiers="w1775dp-h660dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LauncherDrivePopupTest {
    @Test fun factoryModeMenuOnlyOpensForStandardModes() {
        assertEquals(listOf(1,2,6,5),LauncherVehicleState.standardDriveModes)
        for(mode in 1..6) {
            val state=LauncherVehicleState(drive=mode,manual=1,fresh=true)
            assertEquals(mode in listOf(1,2,6,5),state.driveMenuEnabled)
            assertFalse(state.copy(manual=2).driveMenuVisible)
            assertFalse(state.copy(maintain=2).driveMenuEnabled)
            assertFalse(state.copy(fresh=false).driveMenuEnabled)
        }
        assertEquals("Easy exit",LauncherVehicleState(drive=1,manual=2).displayedDrive)
    }
    @Test fun factoryPopupRendersAndSelectsMode() = renderPopup(false)
    @Test fun lightPopupRendersAndSelectsMode() = renderPopup(true)
    private fun renderPopup(light: Boolean) {
        val controller=Robolectric.buildActivity(Activity::class.java).setup()
        val activity=controller.get()
        val anchor=View(activity);activity.setContentView(anchor);anchor.layout(0,0,1775,660)
        var selected=0
        val popup=LauncherDrivePopup.show(anchor,LauncherVehicleState(drive=1),light) {selected=it}
        val content=popup.contentView
        content.measure(View.MeasureSpec.makeMeasureSpec(340,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY))
        content.layout(0,0,340,400)
        val bitmap=Bitmap.createBitmap(340,400,Bitmap.Config.ARGB_8888)
        content.draw(Canvas(bitmap))
        val file=java.io.File("build/reports/launcher/drive-popup${if(light) "-light" else ""}.png");file.parentFile.mkdirs()
        file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        val rows=(content as ScrollView).getChildAt(0) as LinearLayout
        assertEquals(4,rows.childCount)
        assertTrue(rows.getChildAt(0).isSelected)
        rows.getChildAt(1).performClick()
        assertEquals(2,selected);assertFalse(popup.isShowing)
        controller.pause().stop().destroy()
    }
}
