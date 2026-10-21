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
    @Test fun avmRoundTripsKeepContentVisibleWithoutReplayingEntrance() {
        val controller=Robolectric.buildActivity(ComponentActivity::class.java).create()
        val activity=controller.get();val content=FrameLayout(activity)
        var camera=false
        LauncherEntrance.install(activity,content) {camera}
        fun focus(value:Boolean) {
            android.view.ViewTreeObserver::class.java.getDeclaredMethod("dispatchOnWindowFocusChange",Boolean::class.javaPrimitiveType)
                .apply{isAccessible=true}.invoke(activity.window.decorView.viewTreeObserver,value)
        }
        controller.start().resume();focus(true)
        assertNotNull(content.animation)
        repeat(3) {
            camera=true;focus(false)
            assertEquals(1f,content.alpha,0f)
            assertNull(content.animation)
            controller.pause().stop()
            assertEquals(1f,content.alpha,0f)
            camera=false
            controller.restart().start().resume();focus(true)
            assertEquals(1f,content.alpha,0f)
            assertNull(content.animation)
        }
        // Ordinary app transitions still hide and animate.
        focus(false);controller.pause().stop()
        assertEquals(0f,content.alpha,0f)
        controller.restart().start().resume();focus(true)
        assertNotNull(content.animation)
        controller.pause().stop().destroy()
    }

    @Test fun cameraDoesNotSkipColdEntranceOrHideRealAppSwitch() {
        val controller=Robolectric.buildActivity(ComponentActivity::class.java).create()
        val activity=controller.get();val content=FrameLayout(activity)
        var camera=true
        LauncherEntrance.install(activity,content) {camera}
        fun focus(value:Boolean) {
            android.view.ViewTreeObserver::class.java.getDeclaredMethod("dispatchOnWindowFocusChange",Boolean::class.javaPrimitiveType)
                .apply{isAccessible=true}.invoke(activity.window.decorView.viewTreeObserver,value)
        }
        controller.start().resume();focus(false)
        assertEquals(0f,content.alpha,0f)
        camera=false;focus(true);assertNotNull(content.animation)
        camera=true;focus(false);controller.pause()
        // Camera closes while a different app takes over; onStop must re-arm.
        camera=false;controller.stop()
        assertEquals(0f,content.alpha,0f)
        controller.restart().start().resume();focus(true)
        assertNotNull(content.animation)
        controller.pause().stop().destroy()
    }
    @Test fun onlyContentScalesWhileBackdropRemainsOpaqueInBothThemes() {
        val controller=Robolectric.buildActivity(ComponentActivity::class.java).create()
        val activity=controller.get();val content=FrameLayout(activity)
        LauncherEntrance.install(activity,content)
        assertEquals("Cold content waits for its entrance",0f,content.alpha,0f)
        controller.start().resume()
        assertEquals("Resume alone must not expose content",0f,content.alpha,0f)
        fun focus(value:Boolean=true) {
            android.view.ViewTreeObserver::class.java.getDeclaredMethod("dispatchOnWindowFocusChange",Boolean::class.javaPrimitiveType)
                .apply{isAccessible=true}.invoke(activity.window.decorView.viewTreeObserver,value)
        }
        focus()
        val armed=content.animation
        assertNotNull(armed)
        assertEquals(1f,content.alpha,0f)
        assertEquals(android.view.animation.Animation.START_ON_FIRST_FRAME.toLong(),armed!!.startTime)
        focus()
        assertSame("Late focus must not restart the entrance",armed,content.animation)
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
        controller.pause()
        assertNull(content.animation)
        controller.resume()
        assertEquals(0f,content.alpha,0f)
        focus()
        val resumed=content.animation
        assertNotNull("Warm resume must arm entrance before focus",resumed)
        assertNotSame(armed,resumed)
        assertEquals(android.view.animation.Animation.START_ON_FIRST_FRAME.toLong(),resumed!!.startTime)
        resumed.initialize(1000,600,1000,600)
        resumed.getTransformation(5000,frame)
        frame.matrix.getValues(matrix)
        assertEquals("First returning frame is already scaled",.8f,matrix[Matrix.MSCALE_X],.001f)
        assertEquals(0f,frame.alpha,.001f)
        focus()
        assertSame("Focus after drawing must not cause a second entrance",resumed,content.animation)
        controller.pause().stop()
        assertEquals("Covered HOME must not expose a full-size returning frame",0f,content.alpha,0f)
        assertEquals(1f,backdrop.alpha,0f)
        controller.restart().start()
        assertEquals("onStart before resume must still hide content",0f,content.alpha,0f)
        controller.resume()
        assertEquals("OEM return stays hidden until focus",0f,content.alpha,0f)
        focus()
        assertEquals(1f,content.alpha,0f)
        val afterOem=content.animation!!
        afterOem.initialize(1000,600,1000,600)
        afterOem.getTransformation(9000,frame)
        frame.matrix.getValues(matrix)
        assertEquals(.8f,matrix[Matrix.MSCALE_X],.001f)
        assertEquals(0f,frame.alpha,.001f)
        focus()
        assertSame(afterOem,content.animation)
        // OEM focus loss can occur without a lifecycle pause.
        focus(false)
        assertEquals(0f,content.alpha,0f)
        assertNull(content.animation)
        assertEquals(1f,backdrop.alpha,0f)
        focus()
        assertEquals(1f,content.alpha,0f)
        assertNotSame(afterOem,content.animation)
        // The reverse ordering also occurs: focus arrives before resume.
        controller.pause()
        focus()
        assertEquals(0f,content.alpha,0f)
        controller.resume()
        assertEquals(1f,content.alpha,0f)
        assertNotNull(content.animation)
        controller.pause().stop()
        controller.destroy()
    }
}
