package com.abrar.voyahiran.feature.launcher

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowSystemClock
import java.io.File
import java.time.Duration

/** Timeline is drawn by production Android Views, not a separate design mockup. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30],application=Application::class,qualifiers="en-w1920dp-h1080dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class LauncherMotionRenderTest {
    @Test fun renderRetargetableControlsInBothThemes() {
        val controller=Robolectric.buildActivity(Activity::class.java).setup().visible()
        val activity=controller.get();val root=FrameLayout(activity);activity.setContentView(root)
        val fragrance=PassengerFragranceView(activity)
        val climate=PassengerClimateView(activity)
        val vehicle=LauncherVehicleDashboard(activity)
        val music=PassengerMusicView(activity)
        val seats=PassengerSeatsView(activity)
        val favorites=PassengerFavoritesView(activity)
        val apps=listOf("Neshan" to "neshan.webp","Apple Music" to "apple-music.jpeg","Namava" to "namava.jpeg","Video" to "video.png","Music player" to "audio.png").mapIndexed{i,p->
            LauncherAppsEntry("app$i",p.first,javaClass.getResourceAsStream("/passenger-app-icons/${p.second}")!!.use{android.graphics.drawable.Drawable.createFromStream(it,p.second)})
        }
        // Prime each renderer with real assets. The timeline tests animation, not the
        // asynchronous shared decoder (whose completion uses a different test clock).
        fun prime(view:View,directory:String) {
            @Suppress("UNCHECKED_CAST")
            val images=view.javaClass.getDeclaredField("images").apply{isAccessible=true}.get(view) as MutableMap<String,Bitmap>
            activity.assets.list("passenger/$directory").orEmpty().filter{it.endsWith(".png")&&!it.startsWith("fragrance_anim_")}.forEach {name->
                images[name.removeSuffix(".png")]=activity.assets.open("passenger/$directory/$name").use(android.graphics.BitmapFactory::decodeStream)!!
            }
        }
        prime(fragrance,"fragrance-wide");prime(climate,"oem-ac")
        fun add(view:View,w:Int,h:Int){root.addView(view,FrameLayout.LayoutParams(w,h));view.layout(0,0,w,h)}
        add(fragrance,770,280);add(climate,770,370);add(vehicle,1775,660);add(music,556,256)
        add(seats,556,228)
        add(favorites,546,662)
        runBlocking{vehicle.preload(this)}
        val body=vehicle.javaClass.getDeclaredField("artwork").apply{isAccessible=true}.get(vehicle) as LauncherSuspensionArtwork
        body.javaClass.getDeclaredField("body").apply{isAccessible=true}.set(body,activity.assets.open("launcher/ccc/body_dark_60.png").use(android.graphics.BitmapFactory::decodeStream))
        val base=LauncherVehicleState(drive=1,height=5,adjusting=0,recovery=2,energy=1,
            modes=linkedMapOf("smart" to 1,"electric" to 2,"fuel" to 3,"save" to 4),speed=0,
            maintain=1,suspended=0,sensorError=0,manual=1,fresh=true,supportsSuspension=true,supportsOuting=true)
        fun update(high:Boolean) {
            fragrance.update(JSONObject().put("fresh",true).put("FCM_SW_REQ_FB",2).put("IVI_FRAG_TASTE_FB",1)
                .put("IVI_FRAG_CONCERNTION_FB",if(high)3 else 1).put("FCM_DURATION_CONTROL",if(high)0 else 1))
            climate.update(JSONObject().put("fresh",true).put("getAirLeftTemperature",if(high)235 else 220)
                .put("getAirRightTemperature",230).put("getAirDUALStatus",if(high)0 else 1)
                .put("getAirWindSpeed",if(high)6 else 2).put("getAirACStatus",if(high)1 else 0)
                .put("getAirSWStatus",1).put("getAirMode",if(high)1 else 0))
            vehicle.state=if(high)base.copy(energy=3,recovery=4,height=1)else base
            music.update(PassengerMusicPresentation(title="Tamasha",artist="Shadmehr Aghili",playing=high))
            music.updateVolume(if(high)23 else 6)
            favorites.bind(apps,if(high)apps.map{it.id}else List(5){""})
            if(high)favorites.beginEdit()else favorites.dismissEdit()
            seats.update(JSONObject().put("fresh",true).apply{
                for(side in listOf("LEFT","RIGHT")){
                    put("FRONT_SEAT_VENTILATION_SWITCH_$side",if(high&&side=="RIGHT")2 else 1)
                    put("FRONT_SEAT_HEATING_SWITCH_$side",if(high&&side=="LEFT")2 else 1)
                    put("FRONT_SEAT_MASS_SWITCH_$side",if(high)2 else 1)
                    put("FRONT_SEAT_VENTILATION_COMMAND_$side",2)
                    put("FRONT_SEAT_HEATING_COMMAND_$side",3)
                    put("FRONT_SEAT_MASS_INTEN_$side",2)
                    put("FRONT_SEAT_MASS_COMMAND_$side",if(high)2 else 1)
                }
            })
        }
        val output=File("build/reports/launcher-motion").apply{mkdirs()}
        for(light in listOf(false,true)) {
            fragrance.light=light;climate.light=light;vehicle.lightTheme=light;music.light=light
            seats.light=light
            favorites.light=light
            update(false)
            for(frame in 0..35) {
                if(frame==6)update(true)
                if(frame==22)update(false)
                ShadowSystemClock.advanceBy(Duration.ofMillis(40))
                val bitmap=Bitmap.createBitmap(1775,1040,Bitmap.Config.ARGB_8888)
                val c=Canvas(bitmap);c.drawColor(if(light)0xffe2eaf3.toInt()else Color.BLACK)
                vehicle.draw(c)
                c.save();c.translate(0f,670f);climate.draw(c);c.restore()
                c.save();c.translate(785f,670f);fragrance.draw(c);c.restore()
                c.save();c.translate(1190f,950f);c.scale(.35f,.35f);music.draw(c);c.restore()
                assertTrue(fragrance.isAttachedToWindow)
                File(output,"${if(light)"light"else "dark"}-%02d.png".format(frame)).outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
                bitmap.recycle()
                for((name,view) in listOf("seats" to seats,"music" to music,"favorites" to favorites)){
                    val card=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
                    view.draw(Canvas(card))
                    File(output,"$name-${if(light)"light"else "dark"}-%02d.png".format(frame)).outputStream().use{card.compress(Bitmap.CompressFormat.PNG,100,it)}
                    card.recycle()
                }
            }
        }
        controller.pause().stop().destroy()
    }
}
