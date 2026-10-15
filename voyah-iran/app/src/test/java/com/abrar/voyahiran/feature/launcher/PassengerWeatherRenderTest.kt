package com.abrar.voyahiran.feature.launcher

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.ZonedDateTime
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30],application=Application::class,qualifiers="en-w1920dp-h720dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PassengerWeatherRenderTest {
    private fun sample():LauncherWeatherState {
        val start=ZonedDateTime.parse("2026-10-15T19:00:00+03:30").toInstant().toEpochMilli()
        return LauncherWeatherState(city="Tehran",temperature=20.0,code=0,day=false,notice="",hours=(0..4).map{LauncherWeatherHour(start+it*3600000L,20.0-it,if(it in 1..2)3 else 0,false)})
    }
    private fun render(view:PassengerWeatherView,name:String):Bitmap {
        view.layout(0,0,556,154);val b=Bitmap.createBitmap(556,154,Bitmap.Config.ARGB_8888);view.draw(Canvas(b))
        val output=File("build/reports/passenger-weather").apply{mkdirs()};File(output,"weather-$name.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};return b
    }
    @Test fun renderThemesLongCityAndMissingForecast(){
        val original=TimeZone.getDefault();TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tehran"))
        try{
            val view=PassengerWeatherView(RuntimeEnvironment.getApplication())
            for(light in listOf(false,true)){
                view.light=light;val theme=if(light)"light"else "dark"
                view.update(sample());val normal=render(view,theme)
                view.update(sample().copy(city="آران و بیدگل"));val long=render(view,"$theme-long-city")
                // City layout must never change or cover any forecast column.
                for(x in 180 until 556)for(y in 0 until 154)assertEquals(normal.getPixel(x,y),long.getPixel(x,y))
                normal.recycle();long.recycle()
                val partial=sample().copy(notice="Saved · 18:00",hours=sample().hours.take(2))
                view.update(partial);val reference=render(view,"$theme-partial-short")
                view.update(partial.copy(city="A very very long city name"));val clipped=render(view,"$theme-partial")
                for(x in 180 until 556)for(y in 0 until 154)assertEquals(reference.getPixel(x,y),clipped.getPixel(x,y))
                reference.recycle();clipped.recycle()
                view.update(LauncherWeatherState());render(view,"$theme-unavailable").recycle()
                assertTrue(view.contentDescription.contains("unavailable"))
            }
        }finally{TimeZone.setDefault(original)}
    }
    @Test fun allSupportedWeatherArtworkRenders(){
        val view=PassengerWeatherView(RuntimeEnvironment.getApplication());view.layout(0,0,556,154)
        val bitmap=Bitmap.createBitmap(556,154,Bitmap.Config.ARGB_8888)
        for(code in listOf(0,1,2,3,45,48,51,53,55,56,57,61,63,65,66,67,71,73,75,77,80,81,82,85,86,95,96,97,99)){
            view.update(sample().copy(code=code,day=true));view.draw(Canvas(bitmap))
        };bitmap.recycle()
    }
}
