package com.abrar.voyahiran.feature.launcher

import android.app.Application
import android.view.MotionEvent
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30],application=Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class PassengerTemperatureWheelTest {
    private fun drag(height:Int,distance:Float,duration:Long):Pair<Float,List<Int>> {
        val view=View(RuntimeEnvironment.getApplication());view.layout(0,0,770,height)
        val commands=mutableListOf<Int>();val wheel=PassengerTemperatureWheel(view){commands.add(it)}
        wheel.sync(220)
        assertEquals(56f*height/370f,wheel.stepPixels,.001f)
        val start=android.os.SystemClock.uptimeMillis()
        fun event(action:Int,t:Long,y:Float,block:(MotionEvent)->Unit){
            val e=MotionEvent.obtain(start,start+t,action,50f,y*height/370f,0)
            block(e);e.recycle()
        }
        event(0,0,150f){wheel.down(it,150f)}
        for(i in 1..10){
            ShadowSystemClock.advanceBy(Duration.ofMillis(duration/10))
            val y=150f+distance*i/10
            event(2,duration*i/10,y){wheel.move(it,y)}
        }
        event(1,duration,150f+distance){wheel.up(it,150f+distance)}
        repeat(500){ShadowSystemClock.advanceBy(Duration.ofMillis(16));wheel.advance()}
        assertFalse(wheel.busy)
        assertTrue("Send only the settled value",commands.size<=1)
        return wheel.position to commands
    }
    @Test fun controlledDragChangesOneHalfDegreeAtBothCardScales(){
        for(h in listOf(185,370)){
            assertEquals(listOf(225),drag(h,40f,600).second)
            assertEquals(listOf(215),drag(h,-40f,600).second)
        }
    }
    @Test fun tinyJitterDoesNotChangeTemperature(){
        assertTrue(drag(370,3f,100).second.isEmpty())
    }
    @Test fun shortQuickGestureDoesNotJumpTenSteps(){
        for(h in listOf(185,370))assertTrue(kotlin.math.abs(drag(h,20f,100).first-13f)<=2f)
    }
}
