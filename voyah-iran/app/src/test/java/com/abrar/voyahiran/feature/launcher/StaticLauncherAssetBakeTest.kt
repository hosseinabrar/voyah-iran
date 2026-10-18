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
import kotlin.math.*

/** Offline authoring only. The APK consumes these immutable PNGs, never this code. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StaticLauncherAssetBakeTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var dir="passenger/oem-ac"
    private fun color(s:String)=Color.parseColor(s)
    private fun read(path:String)=context.assets.open(path).use{BitmapFactory.decodeStream(it)!!}
    private fun save(path:String,b:Bitmap){val f=File("src/main/assets/$path");f.parentFile!!.mkdirs();f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)}}
    private fun bake(name:String,w:Int,h:Int,draw:(Canvas)->Unit){
        val b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
        paint.reset();paint.isAntiAlias=true;paint.isFilterBitmap=true
        draw(Canvas(b));save("passenger/baked/$name.png",b);b.recycle()
    }
    private fun image(c:Canvas,n:String,r:RectF,cover:Boolean=false,matrix:ColorMatrix?=null){
        val b=read("$dir/$n.png");val scale=if(cover)max(r.width()/b.width,r.height()/b.height) else min(r.width()/b.width,r.height()/b.height)
        paint.shader=null;paint.color=Color.WHITE;paint.alpha=255;paint.colorFilter=matrix?.let{ColorMatrixColorFilter(it)}
        c.drawBitmap(b,null,RectF(r.centerX()-b.width*scale/2,r.centerY()-b.height*scale/2,r.centerX()+b.width*scale/2,r.centerY()+b.height*scale/2),paint)
        paint.colorFilter=null;b.recycle()
    }
    private fun filter(degrees:Float,saturation:Float):ColorMatrix{
        val c=cos(degrees*PI/180).toFloat();val s=sin(degrees*PI/180).toFloat()
        return ColorMatrix(floatArrayOf(.213f+.787f*c-.213f*s,.715f-.715f*c-.715f*s,.072f-.072f*c+.928f*s,0f,0f,
            .213f-.213f*c+.143f*s,.715f+.285f*c+.140f*s,.072f-.072f*c-.283f*s,0f,0f,
            .213f-.213f*c-.787f*s,.715f-.715f*c+.715f*s,.072f+.928f*c+.072f*s,0f,0f,0f,0f,0f,1f,0f)).apply{
            postConcat(ColorMatrix().apply{setSaturation(saturation)})
        }
    }

    @org.junit.Ignore("Offline authoring only")
    @Test fun bakeStaticArtwork(){
        for(light in listOf(false,true)){
            val theme=if(light)"light"else "dark"
            val lightBackground=color("#c7d7e6")
            dir="passenger/oem-ac"
            bake("climate-$theme",770,370){c->
        c.drawColor(if(light)lightBackground else color("#14191e"))
        // These are the approved, composed artworks. Do not replace a composed
        // light background with the raw OEM drawable of a different geometry.
        image(c,if(light)"cabin-day-fade-v2" else "air_bg_h97c",RectF(0f,0f,770f,370f),cover=true)
        if(light){
            val base=lightBackground
            fun fade(x0:Float,y0:Float,x1:Float,y1:Float,alphas:IntArray,stops:FloatArray){
                paint.shader=LinearGradient(x0,y0,x1,y1,alphas.map{(base and 0xffffff) or (it shl 24)}.toIntArray(),stops,Shader.TileMode.CLAMP)
                c.drawRect(0f,0f,770f,370f,paint);paint.shader=null
            }
            fade(0f,0f,770f,0f,intArrayOf(255,245,96,0,0,96,245,255),floatArrayOf(0f,.12f,.20f,.27f,.73f,.80f,.88f,1f))
            fade(0f,370f,0f,0f,intArrayOf(255,245,128,0),floatArrayOf(0f,.29f,.39f,.51f))
            fade(0f,0f,0f,370f,intArrayOf(179,51,0),floatArrayOf(0f,.20f,.36f))
        }

            }
            bake("weather-$theme",556,154){c->
        paint.shader=LinearGradient(0f,0f,556f,154f,if(light)intArrayOf(0xfff7faff.toInt(),0xffeef4fc.toInt())else intArrayOf(0xff35456e.toInt(),0xff1b233a.toInt()),null,Shader.TileMode.CLAMP)
        c.drawRect(0f,0f,556f,154f,paint);paint.shader=null
        c.save();c.translate(278f,160f);c.scale(1f,.12f);paint.shader=RadialGradient(0f,0f,417f,intArrayOf(if(light)0x356ba5ef else 0x706278b6,0x006278b6),null,Shader.TileMode.CLAMP);c.drawCircle(0f,0f,417f,paint);paint.shader=null;c.restore()

            }
            bake("music-$theme",556,256){c->
        paint.shader=LinearGradient(0f,0f,556f,256f,if(light)intArrayOf(0xfff7faff.toInt(),0xffeef4fc.toInt())else intArrayOf(0xff35456e.toInt(),0xff1b233a.toInt()),null,Shader.TileMode.CLAMP);c.drawRect(0f,0f,556f,256f,paint);paint.shader=null
        c.save();c.translate(278f,266f);c.scale(1f,.12f);paint.shader=RadialGradient(0f,0f,417f,intArrayOf(if(light)0x356ba5ef else 0x706278b6,0x006278b6),null,Shader.TileMode.CLAMP);c.drawCircle(0f,0f,417f,paint);paint.shader=null;c.restore()

            }
            bake("seats-$theme",556,228){c->
        paint.shader=LinearGradient(0f,0f,556f,228f,if(light)intArrayOf(0xfff7faff.toInt(),0xffeef4fc.toInt())else intArrayOf(0xff35456e.toInt(),0xff1b233a.toInt()),null,Shader.TileMode.CLAMP);c.drawRect(0f,0f,556f,228f,paint);paint.shader=null
        c.save();c.translate(278f,237f);c.scale(1f,.12f);paint.shader=RadialGradient(0f,0f,417f,intArrayOf(if(light)0x356ba5ef else 0x706278b6,0x006278b6),null,Shader.TileMode.CLAMP);c.drawCircle(0f,0f,417f,paint);paint.shader=null;c.restore()

            }
            bake("favorites-$theme",546,662){c->
        paint.shader=LinearGradient(0f,0f,546f,662f,if(light)intArrayOf(0xfff7faff.toInt(),0xffeef4fc.toInt())else intArrayOf(0xff35456e.toInt(),0xff1b233a.toInt()),null,Shader.TileMode.CLAMP)
        c.drawRect(0f,0f,546f,662f,paint)
        c.save();c.translate(273f,688f);c.scale(1f,.16f);paint.shader=RadialGradient(0f,0f,410f,intArrayOf(if(light)0x356ba5ef else 0x706278b6,0x006278b6),null,Shader.TileMode.CLAMP);c.drawCircle(0f,0f,410f,paint);c.restore();paint.shader=null

            }
            dir="passenger/fragrance-wide"
            for(tone in 1..3)for(on in listOf(false,true)){
                bake("fragrance-$theme-$tone-$on",770,280){c->
        val hue=if(light)when(tone){1->18f;2->-52f;else->105f} else when(tone){1->25f;3->90f;else->0f}
        c.save();c.translate(770f,0f);c.scale(-1f,1f)
        image(c,if(light)"landscape-day-v1" else "landscape",RectF(0f,0f,770f,280f),true,
            filter(hue,if(light)when(tone){1->.7f;2->.85f;else->.65f} else 1f))
        c.restore()
        if(light&&tone!=1&&on)c.drawColor(0x29ffffff)
        if(!light){paint.shader=LinearGradient(0f,280f,0f,210f,intArrayOf(0x8006101b.toInt(),0x0006101b),null,Shader.TileMode.CLAMP);c.drawRect(0f,0f,770f,280f,paint);paint.shader=null}
        c.save();c.translate(28.0464f,0f);c.scale(.927152f,.927152f)
        if(light){c.save();c.translate(586.5f,229f);c.scale(1f,30f/133f);paint.shader=RadialGradient(0f,0f,66.5f,intArrayOf(0x70253f57,0x00324e65),null,Shader.TileMode.CLAMP);c.drawCircle(0f,0f,66.5f,paint);paint.shader=null;c.restore()}

                    c.restore()
                }
            }
            bake("all-apps-$theme",86,86){c->
                val x=43f;val y=43f
                paint.shader=LinearGradient(0f,0f,86f,86f,if(light)0xff82a7dc.toInt() else 0xffd0e0ff.toInt(),if(light)0xff668dc8.toInt() else 0xff809acd.toInt(),Shader.TileMode.CLAMP)
                for(r in 0..1)for(col in 0..1)c.drawRoundRect(RectF(col*47f,r*47f,38f+col*47,38f+r*47),7f,7f,paint)
            }
            bake("music-play-$theme",56,56){c->
                paint.shader=LinearGradient(0f,0f,56f,56f,if(light)0xff719eff.toInt()else 0xff536eb5.toInt(),if(light)0xff3868e5.toInt()else 0xff304986.toInt(),Shader.TileMode.CLAMP)
                c.drawCircle(28f,28f,28f,paint)
            }
        }
        // Fixed theme tints: preserve original alpha and geometry.
        val acNames=listOf("air_ic_wind_min","air_ic_wind_max","air_cycle_outer_normal","air_cycle_inner_normal")+listOf("blow_window","blow_face","blow_foot","ac","switch","auto","front_defrost","rear_defrost").map{"air_ic_${it}_off_normal"}
        for(name in acNames.map{"$it.png"}){
            val b=read("passenger/oem-ac/$name")
            bake("ac-$name".removeSuffix(".png"),b.width,b.height){c->paint.colorFilter=PorterDuffColorFilter(0xff303030.toInt(),PorterDuff.Mode.SRC_IN);c.drawBitmap(b,0f,0f,paint)}
            b.recycle()
        }
        for(name in context.assets.list("passenger/airflow").orEmpty().filter{it.endsWith(".png")}){
            val b=read("passenger/airflow/$name")
            for(light in listOf(false,true))bake("air-${if(light)"light"else "dark"}-$name".removeSuffix(".png"),b.width,b.height){c->
                paint.colorFilter=PorterDuffColorFilter(if(light)0xff479cd5.toInt()else 0xffb8e6ff.toInt(),PorterDuff.Mode.SRC_IN);c.drawBitmap(b,0f,0f,paint)
            };b.recycle()
        }
        for(n in listOf("shuigan","zhuzi","mudan")){
            val name="fragrance_icon_${n}_off";val b=read("passenger/fragrance-wide/$name.png")
            bake(name+"-light",b.width,b.height){c->
                paint.colorFilter=ColorMatrixColorFilter(ColorMatrix(floatArrayOf(.09f,0f,0f,0f,25.5f,0f,.09f,0f,0f,25.5f,0f,0f,.09f,0f,25.5f,0f,0f,0f,1f,0f)))
                c.drawBitmap(b,0f,0f,paint)
            };b.recycle()
        }
        val seats=PassengerSeatsView(context)
        val load=seats.javaClass.getDeclaredMethod("load",String::class.java).apply{isAccessible=true}
        save("passenger/oem-x/seat_dark_baked.png",load.invoke(seats,"seat_bg_high_level_97c") as Bitmap)
        val control=seats.javaClass.getDeclaredMethod("control",String::class.java,Boolean::class.javaPrimitiveType).apply{isAccessible=true}
        for(side in listOf("left","right"))for(f in listOf("vent","heat","massage"))for(level in 0..3){
            val name="seat_${side}_${f}_${level}_normal"
            save("passenger/oem-x/light-$name.png",control.invoke(seats,name,true) as Bitmap)
        }
        var car=read("launcher/ccc/energy_car.png")
        repeat(2){val next=Bitmap.createScaledBitmap(car,car.width/2,car.height/2,true);car.recycle();car=next}
        save("launcher/ccc/energy_car_dark_baked.png",car)
        bake("energy-car-light",car.width,car.height){c->
            paint.colorFilter=ColorMatrixColorFilter(ColorMatrix(floatArrayOf(1.08f,0f,0f,0f,3f,0f,1.08f,0f,0f,3f,0f,0f,1.08f,0f,3f,0f,0f,0f,1f,0f)));c.drawBitmap(car,0f,0f,paint)
        };car.recycle()
    }

    @org.junit.Ignore("Offline authoring only")
    @Test fun bakeCentralArtwork(){
        for(lightTheme in listOf(false,true)){
            val theme=if(lightTheme)"light"else "dark"
            val foreground=if(lightTheme)0xff293543.toInt()else Color.WHITE
            for(hh in listOf(660,426,218))bake("central-card-$theme-$hh",428,hh){c->
                val x=0f;val y=0f;val w=428f;val h=hh.toFloat()
            paint.shader = LinearGradient(x,y,x+w,y+h,if(lightTheme) intArrayOf(0xfffcfdff.toInt(),0xffeef4fc.toInt()) else intArrayOf(Color.rgb(60,75,119),Color.rgb(27,35,58)),null,Shader.TileMode.CLAMP)
            c.drawRoundRect(x,y,x+w,y+h,16f,16f,paint); paint.shader = null
            c.save(); c.clipPath(Path().apply { addRoundRect(RectF(x,y,x+w,y+h),16f,16f,Path.Direction.CW) })
            paint.shader = RadialGradient(x+w/2,y+h+7,w*.5f,intArrayOf(if(lightTheme) 0x559ec5fc else 0x776278b6,Color.TRANSPARENT),null,Shader.TileMode.CLAMP)
            c.save(); c.scale(1f,.13f,x+w/2,y+h); c.drawCircle(x+w/2,y+h,w*.55f,paint); c.restore(); paint.shader=null;c.restore()
            }
            bake("vehicle-card-$theme",575,660){c->
                val p=paint;val x=0f;val w=575f
        p.shader=LinearGradient(x,0f,x+w,660f,if(lightTheme)intArrayOf(0xfffcfdff.toInt(),0xffedf4fd.toInt()) else intArrayOf(0xff3c4b77.toInt(),0xff1b233a.toInt()),null,Shader.TileMode.CLAMP)
        c.drawRoundRect(x,0f,x+w,660f,16f,16f,p);p.shader=null
        c.save();c.clipPath(Path().apply{addRoundRect(RectF(x,0f,x+w,660f),16f,16f,Path.Direction.CW)})
        p.shader=RadialGradient(x+w/2,666f,w/2,intArrayOf(if(lightTheme)0x779ec5fc else 0x996278b6.toInt(),Color.TRANSPARENT),null,Shader.TileMode.CLAMP)
        c.scale(1f,.13f,x+w/2,660f);c.drawCircle(x+w/2,660f,w*.55f,p);p.shader=null;c.restore()
            }
            bake("clock-$theme",278,278){c->
                val cx=139f;val cy=135f
        if(lightTheme){
            paint.shader=RadialGradient(cx,cy+4,135f,intArrayOf(0x00344358,0x00344358,0x22344358,Color.TRANSPARENT),floatArrayOf(0f,.86f,.93f,1f),Shader.TileMode.CLAMP)
            c.drawCircle(cx,cy+4,135f,paint)
            paint.shader=LinearGradient(cx-100,cy-100,cx+100,cy+124,0xffffffff.toInt(),0xffedf0f4.toInt(),Shader.TileMode.CLAMP)
        }else paint.color=0xff252726.toInt()
        c.drawCircle(cx,cy,124f,paint);paint.shader=null
        for(i in 0..59){c.save();c.rotate(i*6f,cx,cy);paint.color=if(lightTheme)foreground else 0xffe6eaf1.toInt();paint.strokeWidth=if(i%5==0)2f else 1f;c.drawLine(cx,cy-116,cx,cy-if(i%5==0)107 else 112,paint);c.restore()}

            }
            bake("map-glow-$theme",871,660){c->
                c.clipRect(0f,0f,871f,660f)
                paint.shader=RadialGradient(435f,667f,435f,intArrayOf(if(lightTheme)0x449ec5fc else 0x556278b6,Color.TRANSPARENT),null,Shader.TileMode.CLAMP)
                c.scale(1f,.13f,435f,660f);c.drawCircle(435f,660f,450f,paint)
            }
        }
    }


    @org.junit.Ignore("Offline authoring only")
    @Test fun bakeRemainingFixedVariants(){
        fun tint(path:String,name:String,color:Int){
            val b=read(path);bake(name,b.width,b.height){c->paint.colorFilter=PorterDuffColorFilter(color,PorterDuff.Mode.SRC_IN);c.drawBitmap(b,0f,0f,paint)};b.recycle()
        }
        for(light in listOf(false,true)){
            val theme=if(light)"light"else "dark"
            val top=if(light)0xff719eff.toInt()else 0xff617bbb.toInt()
            val bottom=if(light)0xff3868e5.toInt()else 0xff354b86.toInt()
            bake("central-play-$theme",80,88){c->
                paint.shader=LinearGradient(0f,7f,80f,87f,top,bottom,Shader.TileMode.CLAMP)
                c.drawCircle(41f,38f,38f,paint)
            }
            bake("show-map-$theme",300,70){c->
                paint.shader=LinearGradient(0f,0f,300f,70f,top,bottom,Shader.TileMode.CLAMP)
                c.drawRoundRect(0f,0f,300f,70f,14f,14f,paint)
            }
            tint("passenger/music/volume_music.png","volume-note-$theme",if(light)0xff63788b.toInt()else 0xffeef5ff.toInt())
            tint("passenger/music/volume_music.png","empty-note-$theme",if(light)0xff71839e.toInt()else 0xffbdc9e0.toInt())
        }
        for(n in listOf("launcher_previous","launcher_next"))tint("passenger/music/$n.png","$n-light",0xff293543.toInt())
        for((id,n) in mapOf(1 to "eco_normal",2 to "comfort_normal",3 to "sport_img",4 to "outing_img",5 to "person_normal",6 to "snow_normal",10 to "get_off"))
            tint("launcher/ccc/$n.png","drive-$id-light",0xff263651.toInt())
        // Off-state artwork is also immutable; saturation is animated only during a transition.
        val files=File("src/main/assets/passenger/baked").listFiles()!!.filter{it.name.startsWith("fragrance-")&&it.name.endsWith("-false.png")}
        val objects=listOf("object-1","object-2","object-3","object-1-day-v1","object-2-day-v2","object-3-day-v1")+
            listOf("shuigan","zhuzi","mudan").map{"fragrance_icon_${it}_off"}
        for((input,output) in files.map{it.path to it.path.replace(".png","-gray.png")}+objects.map{"src/main/assets/passenger/fragrance-wide/$it.png" to "src/main/assets/passenger/fragrance-wide/$it-gray.png"}){
            val b=BitmapFactory.decodeFile(input);val dst=Bitmap.createBitmap(b.width,b.height,Bitmap.Config.ARGB_8888)
            val p=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG);p.colorFilter=ColorMatrixColorFilter(ColorMatrix().apply{setSaturation(0f)})
            Canvas(dst).drawBitmap(b,0f,0f,p);File(output).outputStream().use{dst.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle();dst.recycle()
        }
    }

}
