package com.abrar.voyahiran.feature.launcher

import android.app.Application
import android.graphics.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Explicit offline asset authoring; never runs in the application. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SeatDayAssetBakeTest {
    @org.junit.Ignore("Offline asset authoring only; enable explicitly to regenerate the PNG")
    @Test fun bakeDaySeats() {
        val view=PassengerSeatsView(RuntimeEnvironment.getApplication())
        val load=view.javaClass.getDeclaredMethod("load",String::class.java).apply{isAccessible=true}
        val source=load.invoke(view,"seat_bg_high_level_97c") as Bitmap
        val result=Bitmap.createBitmap(source.width,source.height,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(result)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        for(x in floatArrayOf(406f,623f)) {
            canvas.save();canvas.clipRect(if(x<500f)0f else 536f,0f,if(x<500f)535f else source.width.toFloat(),source.height.toFloat())
            // Finish the right shadow's feather before the shared sprite boundary.
            // Clipping a nonzero alpha here leaves a visible straight edge in the card.
            canvas.translate(x,313f);canvas.scale(if(x<500f)1f else .84f,.18f)
            paint.shader=RadialGradient(0f,0f,100f,intArrayOf(0x99313b45.toInt(),0x55313b45,0x00313b45),floatArrayOf(0f,.4f,1f),Shader.TileMode.CLAMP)
            canvas.drawCircle(0f,0f,100f,paint);canvas.restore()
        }
        paint.shader=null
        paint.colorFilter=ColorMatrixColorFilter(ColorMatrix(floatArrayOf(1.1712f,0f,0f,0f,5.1f,0f,1.1712f,0f,0f,5.1f,0f,0f,1.1712f,0f,5.1f,0f,0f,0f,1f,0f)))
        canvas.drawBitmap(source,0f,0f,paint)
        File("src/main/assets/passenger/oem-x/seat_day_baked.png").outputStream().use{result.compress(Bitmap.CompressFormat.PNG,100,it)}
        result.recycle()
    }
}
