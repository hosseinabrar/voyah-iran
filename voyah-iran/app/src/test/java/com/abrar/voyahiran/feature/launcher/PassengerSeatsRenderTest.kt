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
class PassengerSeatsRenderTest {
    private fun sample(left:Boolean=false,right:Boolean=true,heat:Boolean=false)=JSONObject().put("fresh",true).apply {
        for(side in listOf("LEFT","RIGHT")){val active=if(side=="LEFT")left else right
            put("FRONT_SEAT_VENTILATION_COMMAND_$side",if(active&&!heat)2 else 0)
            put("FRONT_SEAT_HEATING_COMMAND_$side",if(active&&heat)3 else 0)
            put("FRONT_SEAT_MASS_INTEN_$side",if(active)1 else 0)
            put("FRONT_SEAT_MASS_COMMAND_$side",if(side=="LEFT")2 else 1)
        }
    }
    @Test fun renderThemesAndIndependentSeatStates(){
        val view=PassengerSeatsView(RuntimeEnvironment.getApplication());view.layout(0,0,556,228)
        val output=File("build/reports/passenger-seats").apply{mkdirs()}
        for(light in listOf(false,true))for((name,state) in listOf("one-on" to sample(),"both-off" to sample(false,false),"both-on" to sample(true,true),"heat" to sample(true,true,true))){
            view.light=light;view.update(state)
            val bitmap=Bitmap.createBitmap(556,228,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap))
            val repeated=Bitmap.createBitmap(556,228,Bitmap.Config.ARGB_8888);view.draw(Canvas(repeated))
            assertTrue("Repeated drawing must not inherit paint alpha",bitmap.sameAs(repeated));repeated.recycle()
            assertEquals(255,android.graphics.Color.alpha(bitmap.getPixel(10,100)))
            File(output,"seats-${if(light)"light"else "dark"}-$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            assertNotEquals(bitmap.getPixel(140,80),bitmap.getPixel(278,80));bitmap.recycle()
        }
    }
    @Test fun writesUseIndependentFeedbackAndModesRequireActiveMassage(){
        val view=PassengerSeatsView(RuntimeEnvironment.getApplication());view.layout(0,0,556,228)
        val writes=mutableListOf<Pair<String,String>>();view.onCommand={a,b->writes.add(a to b)}
        fun tap(side:Int,x:Float,y:Float){for(action in listOf(0,1)){val event=MotionEvent.obtain(0,1,action,side*278+25.02f+x*.82f,y*.82f,0);view.onTouchEvent(event);event.recycle()}}
        tap(0,73f,238f);assertTrue(writes.isEmpty())
        view.update(sample());tap(0,73f,238f);assertEquals("driver-vent" to "1",writes.last())
        tap(1,73f,238f);assertEquals("passenger-vent" to "3",writes.last())
        tap(1,40f,130f);assertEquals("passenger-mode" to "2",writes.last())
        val count=writes.size;tap(0,238f,87f);assertEquals(count,writes.size)
        view.update(sample(true,true,true));tap(0,139f,238f);assertEquals("driver-heat" to "0",writes.last())
        view.update(JSONObject().put("fresh",false));val stale=writes.size;tap(1,205f,238f);assertEquals(stale,writes.size)
    }
}
