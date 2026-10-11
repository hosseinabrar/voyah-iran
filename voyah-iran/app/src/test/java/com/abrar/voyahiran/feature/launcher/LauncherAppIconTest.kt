package com.abrar.voyahiran.feature.launcher

import android.app.Application
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import com.abrar.voyahiran.R
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
class LauncherAppIconTest {
    @Test fun paddedFactoryIconFillsSameBoundsAsFullBleedIcon() {
        val resources=RuntimeEnvironment.getApplication().resources
        val input=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888)
        Canvas(input).drawRect(24f,24f,232f,232f,Paint().apply {color=Color.BLUE})
        val output=(LauncherAppIcon.create(resources,BitmapDrawable(resources,input)) as BitmapDrawable).bitmap
        for(x in listOf(0,255)) {
            // Filtered scaling antialiases the outermost pixel, but no padded margin remains.
            assertTrue(Color.alpha(output.getPixel(x,128))>=200)
            assertEquals(255,Color.blue(output.getPixel(x,128)))
        }
    }
    @Test fun roundedOwnIconsRenderInBothThemes() {
        val resources=RuntimeEnvironment.getApplication().resources
        val sheet=Bitmap.createBitmap(640,360,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(sheet);val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        listOf(R.drawable.voyah_iran_icon,R.drawable.launcher_voyah_icon_light).forEachIndexed {i,id ->
            paint.color=if(i==0)Color.BLACK else 0xffe5ebf3.toInt()
            canvas.drawRect(i*320f,0f,(i+1)*320f,360f,paint)
            val icon=LauncherAppIcon.create(resources,resources.getDrawable(id,null),true) as BitmapDrawable
            assertEquals(0,Color.alpha(icon.bitmap.getPixel(0,0)))
            assertEquals(255,Color.alpha(icon.bitmap.getPixel(128,128)))
            icon.setBounds(i*320+48,48,i*320+272,272);icon.draw(canvas)
            paint.color=if(i==0)Color.WHITE else 0xff303947.toInt();paint.textSize=22f;paint.textAlign=Paint.Align.CENTER
            canvas.drawText("Voyah Iran",i*320+160f,316f,paint)
        }
        val output=File("build/reports/launcher-icons-preview.png");output.parentFile?.mkdirs()
        output.outputStream().use {sheet.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
}
