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
    @Test fun everyHeadingIsAppliedWithoutAdvancingAnimationClock(){
        val dial=LauncherCompassDial(RuntimeEnvironment.getApplication())
        for(heading in listOf(0f,90f,359f,1f,180f)){
            dial.bearing(heading)
            assertEquals(-heading,dial.rotation,0f)
            for(i in 0 until dial.childCount){
                val child=dial.getChildAt(i)
                if(child is TextView)assertEquals(heading,child.rotation,0f)
            }
        }
    }
}
