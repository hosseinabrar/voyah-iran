package com.abrar.voyahiran.feature.launcher

import android.app.Application
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30],application=Application::class)
class LauncherCompassImmediateTest {
    @Test fun headingUsesRmcOnlyAndHoldsBelowFourKmh(){
        val compass=LauncherCompass(RuntimeEnvironment.getApplication()){}
        val field=LauncherCompass::class.java.getDeclaredField("state").apply{isAccessible=true}
        fun heading()=(field.get(compass) as LauncherCompassState).bearing
        compass.speed=4
        compass.onNmeaMessage("\$GNRMC,120000,A,0,N,0,E,5,42.9,010126,,,A",0)
        assertEquals(42.9f,heading()!!,0.001f)
        compass.onLocationChanged(android.location.Location("gps").apply{bearing=100f;altitude=20.0})
        assertEquals(42.9f,heading()!!,0.001f)
        compass.speed=3
        compass.onNmeaMessage("\$GNRMC,120001,A,0,N,0,E,5,50,010126,,,A",0)
        assertEquals(42.9f,heading()!!,0.001f)
    }
    @Test fun stoppedAnimationUsesOemSignedTargetAndUprightLabels(){
        val dial=LauncherCompassDial(RuntimeEnvironment.getApplication())
        for(heading in listOf(0f,90f,359f,1f,180f)){
            dial.bearing(heading,animate=false)
            val target=if(heading>=180)360f-heading else -heading
            assertEquals(target,dial.rotation,0f)
            for(i in 0 until dial.childCount){
                val child=dial.getChildAt(i)
                if(child is TextView)assertEquals(-target,child.rotation,0f)
            }
        }
    }
    @Test fun oemAnimationUses200MillisecondsAndTruncatesHeading(){
        val dial=LauncherCompassDial(RuntimeEnvironment.getApplication())
        dial.bearing(10f,animate=false)
        dial.bearing(20.9f)
        val field=LauncherCompassDial::class.java.getDeclaredField("turn").apply{isAccessible=true}
        val animation=field.get(dial) as android.animation.ValueAnimator
        assertEquals(200L,animation.duration)
        animation.currentPlayTime=100
        assertEquals(-15f,dial.rotation,0.01f)
        dial.bearing(20.1f)
        org.junit.Assert.assertSame(animation,field.get(dial))
        animation.end()
        assertEquals(-20f,dial.rotation,0.01f)
    }
    @Test fun crossingSouthUsesShortArc(){
        val dial=LauncherCompassDial(RuntimeEnvironment.getApplication())
        dial.bearing(179f,animate=false)
        dial.bearing(181f)
        val field=LauncherCompassDial::class.java.getDeclaredField("turn").apply{isAccessible=true}
        val animation=field.get(dial) as android.animation.ValueAnimator
        animation.currentPlayTime=100
        assertEquals(180f,dial.rotation,0.01f)
        animation.end()
        assertEquals(179f,dial.rotation,0.01f)
    }
}
