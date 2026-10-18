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
    @org.junit.Before fun prepareBakedArtwork(){preparePassengerArtwork("baked")}
    @Test fun rememberedLevelsCannotActivateAnOffOrUnknownSwitch(){
        for(side in 0..1)for((feature,type) in listOf("vent" to "VENTILATION","heat" to "HEATING","massage" to "MASS")){
            val suffix=if(side==0)"LEFT"else "RIGHT"
            val switch="FRONT_SEAT_${type}_SWITCH_$suffix"
            val command="FRONT_SEAT_${type}_${if(type=="MASS")"INTEN"else "COMMAND"}_$suffix"
            val state=JSONObject().put("fresh",true).put(command,3)
            assertEquals(-1,PassengerSeatFeedback.level(state,side,feature))
            state.put(switch,1);assertEquals(0,PassengerSeatFeedback.level(state,side,feature))
            state.put(switch,2);assertEquals(3,PassengerSeatFeedback.level(state,side,feature))
            for(invalid in listOf(0,3,255,-1)){
                state.put(switch,invalid);assertEquals(-1,PassengerSeatFeedback.level(state,side,feature))
            }
            state.put(switch,2).put(command,255);assertEquals(-1,PassengerSeatFeedback.level(state,side,feature))
            state.put(command,3).put("fresh",false);assertEquals(-1,PassengerSeatFeedback.level(state,side,feature))
        }
    }
    @Test fun offSeatsRenderIdenticallyEvenWithRememberedIntensities(){
        val view=PassengerSeatsView(RuntimeEnvironment.getApplication());view.layout(0,0,556,228)
        val off=sample(false,false)
        view.update(off)
        kotlinx.coroutines.runBlocking{view.prepareArtwork()}
        val baseline=Bitmap.createBitmap(556,228,Bitmap.Config.ARGB_8888);view.draw(Canvas(baseline))
        val remembered=JSONObject(off.toString())
        for(side in listOf("LEFT","RIGHT"))for(field in listOf("HEATING_COMMAND","VENTILATION_COMMAND","MASS_INTEN"))
            remembered.put("FRONT_SEAT_${field}_$side",3)
        view.update(remembered)
        val result=Bitmap.createBitmap(556,228,Bitmap.Config.ARGB_8888);view.draw(Canvas(result))
        assertTrue("Off switches must suppress levels, overlays and massage mode buttons",baseline.sameAs(result))
        baseline.recycle();result.recycle()
    }
    private fun sample(left:Boolean=false,right:Boolean=true,heat:Boolean=false)=JSONObject().put("fresh",true).apply {
        for(side in listOf("LEFT","RIGHT")){val active=if(side=="LEFT")left else right
            put("FRONT_SEAT_VENTILATION_SWITCH_$side",if(active&&!heat)2 else 1)
            put("FRONT_SEAT_HEATING_SWITCH_$side",if(active&&heat)2 else 1)
            put("FRONT_SEAT_MASS_SWITCH_$side",if(active)2 else 1)
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
            kotlinx.coroutines.runBlocking{view.prepareArtwork()}
            val bitmap=Bitmap.createBitmap(556,228,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap))
            val repeated=Bitmap.createBitmap(556,228,Bitmap.Config.ARGB_8888);view.draw(Canvas(repeated))
            assertTrue("Repeated drawing must not inherit paint alpha",bitmap.sameAs(repeated));repeated.recycle()
            assertEquals(255,android.graphics.Color.alpha(bitmap.getPixel(10,100)))
            File(output,"seats-${if(light)"light"else "dark"}-$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            assertNotEquals(bitmap.getPixel(140,80),bitmap.getPixel(278,80));bitmap.recycle()
        }
    }
    @Test fun isolatedEffectsNeverLoadReplacementUpholstery(){
        val output=File("build/reports/passenger-seats").apply{mkdirs()}
        for(light in listOf(false,true))for(feature in listOf("VENTILATION","HEATING","MASS")){
            val view=PassengerSeatsView(RuntimeEnvironment.getApplication());view.layout(0,0,556,228)
            view.light=light
            val state=sample(false,false)
            for(side in listOf("LEFT","RIGHT")){
                state.put("FRONT_SEAT_${feature}_SWITCH_$side",2)
                state.put("FRONT_SEAT_${feature}_${if(feature=="MASS")"INTEN"else "COMMAND"}_$side",2)
            }
            view.update(state)
            val names=PassengerSeatsView::class.java.getDeclaredMethod("names").apply{isAccessible=true}.invoke(view) as List<*>
            assertFalse("Old OEM upholstery must never cover our seat",names.any{it.toString().startsWith("seatfront_")})
            assertEquals(if(feature=="MASS")2 else 4,names.size)
            kotlinx.coroutines.runBlocking{view.prepareArtwork()}
            val bitmap=Bitmap.createBitmap(556,228,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap))
            File(output,"seats-${if(light)"light"else "dark"}-${feature.lowercase()}.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
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
