package com.abrar.voyahiran.feature.launcher

import android.app.Application
import android.graphics.*
import org.json.JSONObject
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Local artwork review only; never changes packaged assets. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30],application=Application::class,qualifiers="en-w1920dp-h720dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SeatGeneratedPreviewTest {
    @Test fun renderApprovedLighting() {
        assumeTrue(System.getenv("SEAT_GENERATED_PREVIEW")=="1")
        preparePassengerArtwork("baked")
        val context=RuntimeEnvironment.getApplication()
        val root="src/test/artwork/approved-passenger/"
        val output=File("build/reports/passenger-seats-generated").apply{mkdirs()}
        for(heat in listOf(false,true)) {
            val source=BitmapFactory.decodeFile(root+if(heat)"seats-heat.png"else "seats-cool.png")
            val normalized=Bitmap.createBitmap(960,360,Bitmap.Config.ARGB_8888)
            val c=Canvas(normalized);val p=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val crops=if(heat)listOf(Rect(443,18,983,750),Rect(1114,18,1645,750))else listOf(Rect(614,153,1000,700),Rect(1144,153,1518,700))
            for(side in 0..1)c.drawBitmap(source,crops[side],if(side==0)RectF(328f,98f,486f,327f)else RectF(548f,98f,706f,327f),p)
            // Clip any generated surrounding halo to the established seat silhouette.
            val mask=context.assets.open("passenger/oem-x/seat_dark_baked.png").use{BitmapFactory.decodeStream(it)}!!
            p.xfermode=PorterDuffXfermode(PorterDuff.Mode.DST_IN);c.drawBitmap(mask,null,RectF(0f,0f,960f,360f),p);p.xfermode=null
            for(side in listOf("left","right")){
                val sprite=Bitmap.createBitmap(960,360,Bitmap.Config.ARGB_8888)
                val sc=Canvas(sprite);sc.clipRect(if(side=="left")0 else 536,0,if(side=="left")536 else 960,360)
                sc.drawBitmap(normalized,0f,0f,p)
                File(output,"seat_effect_${if(heat)"hot"else "vent"}_$side.png").outputStream().use{sprite.compress(Bitmap.CompressFormat.PNG,100,it)}
                sprite.recycle()
            }
            for(light in listOf(false,true)) {
                val view=PassengerSeatsView(context);view.layout(0,0,556,228);view.light=light
                val state=JSONObject().put("fresh",true)
                for(side in listOf("LEFT","RIGHT")) {
                    for(feature in listOf("VENTILATION","HEATING","MASS")) {
                        val on=feature==if(heat)"HEATING"else "VENTILATION"
                        state.put("FRONT_SEAT_${feature}_SWITCH_$side",if(on)2 else 1)
                        state.put("FRONT_SEAT_${feature}_${if(feature=="MASS")"INTEN"else "COMMAND"}_$side",if(on)2 else 0)
                    }
                }
                view.update(state);kotlinx.coroutines.runBlocking{view.prepareArtwork()}
                @Suppress("UNCHECKED_CAST")
                val cache=PassengerSeatsView::class.java.getDeclaredField("artwork").apply{isAccessible=true}.get(view) as MutableMap<String,Bitmap>
                cache["seat_day_baked"]=normalized;cache["seat_bg_high_level_97c"]=normalized
                val empty=Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888)
                for(side in listOf("left","right"))cache["seat_effect_${if(heat)"hot"else "vent"}_$side"]=empty
                val result=Bitmap.createBitmap(1112,456,Bitmap.Config.ARGB_8888)
                val canvas=Canvas(result);canvas.scale(2f,2f);view.draw(canvas)
                File(output,"${if(light)"light"else "dark"}-${if(heat)"heat"else "cool"}.png").outputStream().use{result.compress(Bitmap.CompressFormat.PNG,100,it)}
                result.recycle()
            }
            source.recycle();mask.recycle();normalized.recycle()
        }
    }
}
