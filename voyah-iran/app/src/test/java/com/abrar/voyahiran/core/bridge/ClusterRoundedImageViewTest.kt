package com.abrar.voyahiran.core.bridge

import android.app.Application
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.widget.FrameLayout
import android.widget.ImageView
import com.abrar.voyahiran.core.bridge.clustertext.ClusterRoundedImageView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30],application=Application::class,qualifiers="en-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClusterRoundedImageViewTest {
    @Test fun masksAllThemesOnSoftwareCanvasAndKeepsConstraints() {
        val context=RuntimeEnvironment.getApplication()
        for(theme in listOf(1,2,4,5)) {
            val parent=FrameLayout(context)
            val original=ImageView(context).apply { id=123 }
            val params=FrameLayout.LayoutParams(140,140)
            parent.addView(original,params)
            ClusterRoundedImageView.replace(original,theme)
            val view=parent.getChildAt(0) as ClusterRoundedImageView
            assertEquals(123,view.id);assertSame(params,view.layoutParams)
            view.layout(0,0,140,140)
            for(bitmapDrawable in listOf(false,true)) {
                if(bitmapDrawable) view.setImageBitmap(Bitmap.createBitmap(140,140,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) })
                else view.setImageDrawable(ColorDrawable(Color.RED))
                val result=Bitmap.createBitmap(140,140,Bitmap.Config.ARGB_8888)
                view.draw(Canvas(result))
                assertEquals(0,Color.alpha(result.getPixel(0,0)))
                assertEquals(255,Color.alpha(result.getPixel(70,70)))
                result.recycle()
            }
        }
    }

    @Test fun renderLightCover() {
        val context=RuntimeEnvironment.getApplication()
        val view=ClusterRoundedImageView(context,4)
        view.scaleType=ImageView.ScaleType.CENTER_CROP
        view.setImageBitmap(javaClass.getResourceAsStream("/passenger-music/tamasha.jpg")!!.use { BitmapFactory.decodeStream(it) })
        view.layout(0,0,140,140)
        val output=Bitmap.createBitmap(420,420,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(output);canvas.drawColor(Color.rgb(238,241,243))
        canvas.scale(2f,2f);canvas.translate(35f,35f);view.draw(canvas)
        val file=File("build/reports/cluster-cover/light-rounded.png");file.parentFile.mkdirs()
        file.outputStream().use { output.compress(Bitmap.CompressFormat.PNG,100,it) }
        output.recycle()
    }
}
