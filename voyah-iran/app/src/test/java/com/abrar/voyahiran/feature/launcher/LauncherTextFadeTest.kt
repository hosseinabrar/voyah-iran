package com.abrar.voyahiran.feature.launcher

import android.app.Activity
import android.app.Application
import android.graphics.*
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class LauncherTextFadeTest {
    @Test fun firstPaintIsSolidChangesFadeAndRepeatedValuesDoNotRestart() {
        val controller=Robolectric.buildActivity(Activity::class.java).setup().visible()
        val view=View(controller.get());controller.get().setContentView(view)
        val motion=LauncherMotion(view)
        val bitmap=Bitmap.createBitmap(20,20,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap);val paint=Paint()
        fun render(token:String,crossfade:Boolean=false):Int {
            bitmap.eraseColor(Color.TRANSPARENT)
            val draw:(String)->Unit={paint.color=if(it=="old")Color.RED else Color.BLUE;canvas.drawRect(0f,0f,20f,20f,paint)}
            if(crossfade)motion.content("sensor",token,canvas,RectF(0f,0f,20f,20f),draw=draw)
            else motion.text("label",token,canvas,RectF(0f,0f,20f,20f),draw)
            return bitmap.getPixel(10,10)
        }
        assertEquals(Color.RED,render("old"))
        assertEquals(Color.RED,render("new"))
        ShadowSystemClock.advanceBy(Duration.ofMillis(70))
        assertTrue(Color.alpha(render("new")) in 1..254)
        ShadowSystemClock.advanceBy(Duration.ofMillis(220))
        assertEquals(Color.BLUE,render("new"))
        assertEquals(Color.BLUE,render("new"))
        motion.paused=true
        assertEquals(Color.RED,render("old"))
        motion.paused=false
        assertEquals(Color.RED,render("old"))
        render("old",true);render("new",true)
        ShadowSystemClock.advanceBy(Duration.ofMillis(140))
        assertTrue("Fast sensor labels must not fade through blank",Color.alpha(render("new",true))>150)
        controller.pause().stop().destroy();bitmap.recycle()
    }
}
