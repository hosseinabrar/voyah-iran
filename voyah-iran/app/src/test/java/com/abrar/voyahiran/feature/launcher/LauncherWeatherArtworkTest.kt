package com.abrar.voyahiran.feature.launcher

import android.app.Application
import android.graphics.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LauncherWeatherArtworkTest {
    @Test fun artworkHasConsistentVisibleBoundsAndTransparentMargins() {
        val app=RuntimeEnvironment.getApplication()
        val codes=listOf(0,2,3,45,48,61,63,65,66,71,73,75,77,80,81,82,85,95,96,-1)
        val sheet=Bitmap.createBitmap(1200,1000,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(sheet)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        codes.forEachIndexed {i,code ->
            val bitmap=LauncherWeatherArtwork.decode(app.resources,LauncherWeatherCode.icon(code))
            assertEquals(360,bitmap.width)
            assertEquals(360,bitmap.height)
            assertEquals(0,Color.alpha(bitmap.getPixel(0,0)))
            var left=360;var top=360;var right=0;var bottom=0
            for(y in 0 until 360) for(x in 0 until 360) if(Color.alpha(bitmap.getPixel(x,y))>=128) {
                left=minOf(left,x);top=minOf(top,y);right=maxOf(right,x);bottom=maxOf(bottom,y)
            }
            assertTrue("code=$code",maxOf(right-left+1,bottom-top+1) in 298..302)
            val x=(i%5)*240f;val y=(i/5)*250f
            paint.color=if(i%2==0)0xff202d48.toInt() else 0xffe5ebf3.toInt()
            canvas.drawRect(x,y,x+240,y+250,paint)
            canvas.drawBitmap(bitmap,null,RectF(x+20,y+8,x+220,y+208),paint)
            paint.color=if(i%2==0)Color.WHITE else 0xff303947.toInt()
            paint.textSize=16f;paint.textAlign=Paint.Align.CENTER
            canvas.drawText(LauncherWeatherCode.description(code),x+120,y+232,paint)
            bitmap.recycle()
        }
        val file=File("build/reports/weather-artwork-preview.png");file.parentFile?.mkdirs()
        file.outputStream().use {sheet.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
}
