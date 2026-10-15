package com.abrar.voyahiran.feature.launcher

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30],application=Application::class,qualifiers="en-w1920dp-h720dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PassengerClimateRenderTest {
    private fun sample()=JSONObject().put("fresh",true).put("getAirLeftTemperature",220).put("getAirRightTemperature",230)
        .put("getAirWindSpeed",3).put("getAirDUALStatus",1).put("getAirACStatus",1).put("getAirSWStatus",1)
        .put("getAirMode",1).put("getAirSupplyStatus",0).put("getAirFrontWindowDefogger",0)
        .put("getAirRearWindowDefogger",0).put("getAirCirculationMode",0)
    @Test fun renderProductionClimateInBothThemes(){
        preparePassengerArtwork("oem-ac")
        val view=PassengerClimateView(RuntimeEnvironment.getApplication());view.layout(0,0,770,370);view.update(sample())
        val output=File("build/reports/passenger-climate").apply{mkdirs()}
        for(light in listOf(false,true)){
            view.light=light
            val b=Bitmap.createBitmap(770,370,Bitmap.Config.ARGB_8888);view.draw(Canvas(b))
            File(output,"climate-${if(light)"light" else "dark"}.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)}
            assertTrue(b.getPixel(385,100)!=b.getPixel(385,365));b.recycle()
        }
    }
    @Test fun commandsFollowFeedbackAndStaleStateCannotWrite(){
        val view=PassengerClimateView(RuntimeEnvironment.getApplication());view.layout(0,0,770,370)
        val writes=mutableListOf<Pair<String,String>>();view.onCommand={a,b->writes.add(a to b)}
        fun tap(x:Float,y:Float){for(a in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)){
            val e=MotionEvent.obtain(0,1,a,x,y,0);view.onTouchEvent(e);e.recycle()
        }}
        tap(540f,260f);assertTrue(writes.isEmpty())
        view.update(sample());tap(540f,260f);assertEquals("fan" to "4",writes.last())
        tap(110f,255f);assertEquals("sync" to "1",writes.last())
        tap(90f,105f);assertEquals("driver-temp" to "225",writes.last())
        view.update(JSONObject().put("fresh",false));val count=writes.size;tap(540f,260f);assertEquals(count,writes.size)
    }
}
