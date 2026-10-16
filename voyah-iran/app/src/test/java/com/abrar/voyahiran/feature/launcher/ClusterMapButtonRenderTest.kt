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
@Config(sdk=[30],application=Application::class,qualifiers="en-w1920dp-h720dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClusterMapButtonRenderTest {
    @Test fun buttonCentreIsThirtyPercentAboveScreenBottom() {
        for(top in listOf(0,50)) {
            val margin=ClusterMapButton.topMargin(720,top,83)
            assertEquals(504f,top+margin+83/2f,1f)
        }
        val bitmap=Bitmap.createBitmap(1920,720,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap)
        canvas.drawColor(Color.rgb(16,21,32))
        val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color=Color.rgb(38,48,67)
        canvas.drawRoundRect(145f,50f,1016f,704f,16f,16f,paint)
        paint.color=Color.rgb(50,62,86)
        canvas.drawRoundRect(1032f,50f,1460f,704f,16f,16f,paint)
        canvas.drawRoundRect(1476f,50f,1904f,704f,16f,16f,paint)
        paint.color=Color.LTGRAY;paint.textSize=24f
        canvas.drawText("Map area — position preview (not live map)",185f,100f,paint)
        val button=ClusterMapButton(RuntimeEnvironment.getApplication()).apply {mode=ClusterMapMode.AUTO}
        button.layout(0,0,83,83)
        val y=50+ClusterMapButton.topMargin(720,50,83)
        canvas.save();canvas.translate(163f,y.toFloat());button.draw(canvas);canvas.restore()
        paint.color=Color.rgb(75,160,235);paint.strokeWidth=2f
        canvas.drawLine(290f,504f,980f,504f,paint)
        canvas.drawLine(950f,504f,950f,719f,paint)
        canvas.drawText("30% of screen height",620f,610f,paint)
        val out=File("/Users/hosseinabrar/voyah/Assets/cluster-button-position.png")
        out.parentFile!!.mkdirs();out.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
    }
}
