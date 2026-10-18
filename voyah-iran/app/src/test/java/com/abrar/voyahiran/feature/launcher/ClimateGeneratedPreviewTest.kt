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
import kotlin.math.max

/** Explicit local review; production artwork is not replaced. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30],application=Application::class,qualifiers="en-w1920dp-h720dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClimateGeneratedPreviewTest {
    @Test fun renderNewWheelWithAirConditioningOn(){
        assumeTrue(System.getenv("CLIMATE_GENERATED_PREVIEW")=="1")
        preparePassengerArtwork("baked");preparePassengerArtwork("oem-ac")
        val root="src/test/artwork/approved-passenger/"
        val output=File("build/reports/passenger-climate-generated").apply{mkdirs()}
        for(light in listOf(false,true)){
            val source=BitmapFactory.decodeFile(root+if(light)"climate-light.png"else "climate-dark.png")
            val background=Bitmap.createBitmap(770,370,Bitmap.Config.ARGB_8888)
            val c=Canvas(background);val p=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val scale=max(770f/source.width,370f/source.height)
            c.drawBitmap(source,null,RectF(385-source.width*scale/2,185-source.height*scale/2,385+source.width*scale/2,185+source.height*scale/2),p)
            if(light){
                val base=0xc7d7e6
                fun fade(x0:Float,y0:Float,x1:Float,y1:Float,alphas:IntArray,stops:FloatArray){
                    p.shader=LinearGradient(x0,y0,x1,y1,alphas.map{base or (it shl 24)}.toIntArray(),stops,Shader.TileMode.CLAMP)
                    c.drawRect(0f,0f,770f,370f,p);p.shader=null
                }
                fade(0f,0f,770f,0f,intArrayOf(255,245,96,0,0,96,245,255),floatArrayOf(0f,.12f,.20f,.27f,.73f,.80f,.88f,1f))
                fade(0f,370f,0f,0f,intArrayOf(255,245,128,0),floatArrayOf(0f,.29f,.39f,.51f))
                fade(0f,0f,0f,370f,intArrayOf(179,51,0),floatArrayOf(0f,.20f,.36f))
            }
            File(output,"background-${if(light)"light"else "dark"}.png").outputStream().use{background.compress(Bitmap.CompressFormat.PNG,100,it)}
            val view=PassengerClimateView(RuntimeEnvironment.getApplication());view.layout(0,0,770,370);view.light=light
            view.update(JSONObject().put("fresh",true).put("getAirLeftTemperature",220).put("getAirRightTemperature",230)
                .put("getAirWindSpeed",3).put("getAirDUALStatus",1).put("getAirACStatus",1).put("getAirSWStatus",1)
                .put("getAirMode",1).put("getAirSupplyStatus",0).put("getAirFrontWindowDefogger",0)
                .put("getAirRearWindowDefogger",0).put("getAirCirculationMode",0))
            PassengerClimateView::class.java.getDeclaredField("airflowTime").apply{isAccessible=true}.setLong(view,1400L)
            val art=PassengerClimateView::class.java.getDeclaredField("staticArtwork").apply{isAccessible=true}.get(view)
            @Suppress("UNCHECKED_CAST")
            val images=StaticLauncherArtwork::class.java.getDeclaredField("images").apply{isAccessible=true}.get(art) as MutableMap<String,Bitmap>
            images["climate-${if(light)"light"else "dark"}"]=background
            val result=Bitmap.createBitmap(770,370,Bitmap.Config.ARGB_8888)
            repeat(15){view.draw(Canvas(result));Thread.sleep(30);org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()}
            File(output,"climate-${if(light)"light"else "dark"}.png").outputStream().use{result.compress(Bitmap.CompressFormat.PNG,100,it)}
            result.recycle();background.recycle();source.recycle()
        }
    }
}
