package com.abrar.voyahiran.feature.launcher

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.MotionEvent
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30],application=Application::class,qualifiers="en-w1920dp-h720dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PassengerFragranceRenderTest {
    private fun sample(tone:Int=2,on:Boolean=true)=JSONObject().put("fresh",true).put("IVI_FRAG_TASTE_FB",PassengerFragranceMapping.channel(tone))
        .put("FCM_SW_REQ_FB",if(on)2 else 1).put("IVI_FRAG_CONCERNTION_FB",2).put("FCM_DURATION_CONTROL",1)
    @Test fun renderAllScentsInBothThemesAndPowerStates(){
        preparePassengerArtwork("fragrance-wide")
        val view=PassengerFragranceView(RuntimeEnvironment.getApplication());view.layout(0,0,770,280)
        val output=File("build/reports/passenger-fragrance").apply{mkdirs()}
        for(light in listOf(false,true))for(tone in 1..3)for(on in listOf(false,true)){
            view.light=light;view.update(sample(tone,on))
            val b=Bitmap.createBitmap(770,280,Bitmap.Config.ARGB_8888)
            // Draw once to retain cached assets and request anything evicted during preparation.
            val deadline=System.nanoTime()+5_000_000_000L
            do {
                b.eraseColor(android.graphics.Color.TRANSPARENT);view.draw(Canvas(b))
                if(android.graphics.Color.alpha(b.getPixel(10,100))==255)break
                Thread.sleep(25);shadowOf(Looper.getMainLooper()).idle()
            } while(System.nanoTime()<deadline)
            assertEquals("Landscape must be loaded before snapshot",255,android.graphics.Color.alpha(b.getPixel(10,100)))
            File(output,"fragrance-${if(light)"light" else "dark"}-$tone-${if(on)"on" else "off"}.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)}
            assertNotEquals(b.getPixel(300,10),b.getPixel(300,260));b.recycle()
        }
    }
    @Test fun commandsUseFeedbackAndRejectStaleOrOffSettings(){
        val view=PassengerFragranceView(RuntimeEnvironment.getApplication());view.layout(0,0,770,280)
        val commands=mutableListOf<Pair<String,String>>();view.onCommand={a,b->commands.add(a to b)}
        fun tap(x:Float,y:Float){for(a in listOf(0,1)){val e=MotionEvent.obtain(0,1,a,28.0464f+x*.927152f,y*.927152f,0);view.onTouchEvent(e);e.recycle()}}
        tap(500f,270f);assertTrue(commands.isEmpty())
        view.update(sample());tap(500f,270f);assertEquals("scent" to "0",commands.last())
        tap(665f,270f);assertEquals("scent" to "3",commands.last())
        tap(580f,270f);assertEquals("scent" to "2",commands.last())
        view.update(sample(tone=1));tap(500f,270f);assertEquals("scent" to "1",commands.last())
        tap(310f,115f);assertEquals("intensity" to "3",commands.last())
        tap(310f,210f);assertEquals("duration" to "0",commands.last())
        view.update(sample(on=false));val count=commands.size;tap(310f,115f);assertEquals(count,commands.size)
        view.update(JSONObject().put("fresh",false));tap(500f,270f);assertEquals(count,commands.size)
    }
}
