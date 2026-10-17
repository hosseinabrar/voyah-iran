package com.abrar.voyahiran.feature.launcher

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.MotionEvent
import android.view.View
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
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30],application=Application::class,qualifiers="en-w1920dp-h720dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PassengerCardAnimationTest {
    @Test fun temperatureTracksFingerThenSettlesAndCancelDoesNotSend(){
        val view=View(RuntimeEnvironment.getApplication()).apply{layout(0,0,770,370)}
        val writes=mutableListOf<Int>();val wheel=PassengerTemperatureWheel(view){writes.add(it)}
        wheel.sync(220)
        fun event(t:Long,a:Int,y:Float)=MotionEvent.obtain(0,t,a,90f,y,0)
        event(0,0,100f).also{wheel.down(it,100f);it.recycle()}
        event(300,2,184f).also{wheel.move(it,184f);it.recycle()}
        assertEquals(14.5f,wheel.position,.01f);assertTrue(writes.isEmpty())
        wheel.sync(200);assertEquals(14.5f,wheel.position,.01f)
        event(600,1,184f).also{wheel.up(it,184f);it.recycle()}
        repeat(400){shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));wheel.advance()}
        assertFalse(wheel.busy);assertEquals(1,writes.size);assertTrue(writes.single() in 225..240)
        event(0,0,100f).also{wheel.down(it,100f);it.recycle()}
        event(50,2,200f).also{wheel.move(it,200f);it.recycle()}
        wheel.cancel();repeat(10){wheel.advance()};assertEquals(1,writes.size)
    }
    @Test fun renderMovingAirflowAndFragranceInBothThemes(){
        val output=File("build/reports/passenger-animation").apply{mkdirs()}
        val ctx=RuntimeEnvironment.getApplication()
        preparePassengerArtwork("oem-ac");preparePassengerArtwork("airflow");preparePassengerArtwork("fragrance-wide")
        val ac=PassengerClimateView(ctx).apply{layout(0,0,770,370);update(JSONObject().put("fresh",true)
            .put("getAirSWStatus",1).put("getAirWindSpeed",3).put("getAirSupplyStatus",0)
            .put("getAirLeftTemperature",220).put("getAirRightTemperature",230))}
        val scent=PassengerFragranceView(ctx).apply{layout(0,0,770,280);update(JSONObject().put("fresh",true)
            .put("FCM_SW_REQ_FB",2).put("IVI_FRAG_TASTE_FB",1).put("IVI_FRAG_CONCERNTION_FB",2).put("FCM_DURATION_CONTROL",1))}
        fun set(view:View,name:String,value:Any){view.javaClass.getDeclaredField(name).apply{isAccessible=true}.set(view,value)}
        fun snapshot(view:View):Bitmap{
            val b=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            repeat(6){b.eraseColor(0);view.draw(Canvas(b));Thread.sleep(30);shadowOf(Looper.getMainLooper()).idle()}
            b.eraseColor(0);view.draw(Canvas(b));return b
        }
        for(light in listOf(false,true)){
            ac.light=light;scent.light=light
            var firstAir:Bitmap?=null;var firstScent:Bitmap?=null
            for(i in 0..27){
                set(ac,"airflowTime",i*100L);set(scent,"pulse",i/28f)
                for((name,v)in listOf("climate" to ac,"fragrance" to scent)){
                    val b=snapshot(v)
                    File(output,"$name-${if(light)"light" else "dark"}-${i.toString().padStart(2,'0')}.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)}
                    if(i==0){if(name=="climate")firstAir=b else firstScent=b}
                    else {if(i==14)assertFalse("$name must animate",b.sameAs(if(name=="climate")firstAir else firstScent));b.recycle()}
                }
            };firstAir?.recycle();firstScent?.recycle()
        }
        // Reproduce an actual continuous finger drag in the production card.
        ac.light=false
        MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,90f,90f,0).also{ac.onTouchEvent(it);it.recycle()}
        for(i in 0..27){
            MotionEvent.obtain(0,i*30L+1,MotionEvent.ACTION_MOVE,90f,90f+i*4,0).also{ac.onTouchEvent(it);it.recycle()}
            val b=snapshot(ac)
            File(output,"temperature-${i.toString().padStart(2,'0')}.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()
        }
        MotionEvent.obtain(0,1000,MotionEvent.ACTION_CANCEL,90f,198f,0).also{ac.onTouchEvent(it);it.recycle()}
        ac.update(JSONObject().put("fresh",true).put("getAirSWStatus",0).put("getAirWindSpeed",3).put("getAirSupplyStatus",0))
        set(ac,"airflowTime",100L);val stopped=snapshot(ac)
        set(ac,"airflowTime",1400L);val stoppedLater=snapshot(ac)
        assertTrue("Power off must stop airflow",stopped.sameAs(stoppedLater));stopped.recycle();stoppedLater.recycle()
        scent.update(JSONObject().put("fresh",true).put("FCM_SW_REQ_FB",1).put("IVI_FRAG_TASTE_FB",1))
        set(scent,"pulse",0f);val off=snapshot(scent)
        set(scent,"pulse",.5f);val offLater=snapshot(scent)
        assertTrue("Power off must stop fragrance emission",off.sameAs(offLater));off.recycle();offLater.recycle()
    }
}
