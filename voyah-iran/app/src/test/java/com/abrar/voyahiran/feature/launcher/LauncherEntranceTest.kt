package com.abrar.voyahiran.feature.launcher

import android.app.Application
import android.graphics.Matrix
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.animation.Transformation
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30],application=Application::class)
@org.robolectric.annotation.LooperMode(org.robolectric.annotation.LooperMode.Mode.PAUSED)
class LauncherEntranceTest {
    @Test fun onlyContentScalesWhileBackdropRemainsOpaqueInBothThemes() {
        val controller=Robolectric.buildActivity(ComponentActivity::class.java).create()
        val activity=controller.get();val content=FrameLayout(activity)
        LauncherEntrance.install(activity,content)
        controller.start().resume()
        val backdrop=content.parent as View
        for(light in listOf(true,false)) {
            LauncherWindowTheme.apply(activity.window,light)
            assertEquals(LauncherWindowTheme.background(light),(backdrop.background as ColorDrawable).color)
            assertNull(backdrop.animation)
            assertEquals(1f,backdrop.alpha,0f)
            assertEquals(1f,backdrop.scaleX,0f)
        }
        val animation=content.animation!!
        animation.initialize(1000,600,1000,600);animation.startTime=0
        val frame=Transformation();animation.getTransformation(0,frame)
        val matrix=FloatArray(9);frame.matrix.getValues(matrix)
        assertEquals(.8f,matrix[Matrix.MSCALE_X],.001f)
        assertEquals(0f,frame.alpha,.001f)
        animation.getTransformation(300,frame);frame.matrix.getValues(matrix)
        assertEquals(1f,matrix[Matrix.MSCALE_X],.001f)
        assertEquals(1f,frame.alpha,.001f)
        controller.pause().stop()
        assertNull(content.animation)
        controller.destroy()
    }
}
