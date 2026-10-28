package com.abrar.voyahiran.feature.launcher

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
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
class LauncherCompassRenderTest {
    @Test fun renderProductionCompass(){
        assumeTrue(System.getenv("RAHA_RENDER_COMPASS")=="1")
        preparePassengerArtwork("baked")
        val view=LauncherVehicleDashboard(RuntimeEnvironment.getApplication()).apply {animationsEnabled=false}
        view.layout(0,0,1775,660)
        val output=File("build/reports/compass").apply {mkdirs()}
        for(light in listOf(false,true))for(heading in listOf(null,0f,15f,90f)){
            view.lightTheme=light
            view.compass=LauncherCompassState(bearing=heading,altitude=1200.0,slope=0.0,inclination=0.0)
            val image=Bitmap.createBitmap(575,660,Bitmap.Config.ARGB_8888)
            val canvas=Canvas(image);canvas.translate(-591f,0f);view.draw(canvas)
            fun red(x:Int,y:Int):Boolean {val color=image.getPixel(x,y);return android.graphics.Color.red(color)>220&&android.graphics.Color.green(color)<100&&android.graphics.Color.blue(color)<130}
            if(heading==null||heading==0f){
                assertFalse("North must not touch the zero label",(116..145).any { y->(277..298).any {x->red(x,y)} })
                assertTrue("North is outside the tick ring",(147..162).any {y->(280..295).any{x->red(x,y)}})
            }
            File(output,"compass-${if(light)"light"else "dark"}-${heading?.toInt() ?: "unknown"}.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)}
            image.recycle()
        }
    }
}
