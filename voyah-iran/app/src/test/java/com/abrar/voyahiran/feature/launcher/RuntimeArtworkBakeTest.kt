package com.abrar.voyahiran.feature.launcher

import android.app.Application
import android.graphics.*
import com.abrar.raha.feedback.*
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Offline authoring only. Never runs in normal tests or ships in either APK. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30],application=Application::class,qualifiers="en-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RuntimeArtworkBakeTest {
    private fun save(b:Bitmap,file:File){file.parentFile.mkdirs();file.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
    @Test fun bakeApprovedRuntimeArt(){
        assumeTrue(System.getenv("BAKE_RUNTIME_ART")=="1")
        val app=RuntimeEnvironment.getApplication()
        for(light in listOf(false,true)) {
            val theme=if(light)"light" else "dark"
            for((name,state) in listOf("normal" to FeedbackState.WAITING,"success" to FeedbackState.SUCCESS,"failure" to FeedbackState.FAILURE,"unknown" to FeedbackState.UNRECOGNIZED)){
                val source=ReferenceRahaOrbView(app).apply{this.light=light;phase=state}
                fun shader(name:String)=source.javaClass.getDeclaredField(name).apply{isAccessible=true}.get(source) as Shader
                val b=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888);val c=Canvas(b);c.translate(128f,128f)
                val p=Paint(Paint.ANTI_ALIAS_FLAG)
                p.shader=shader("glow");c.drawCircle(0f,0f,121f,p)
                p.shader=shader("face");c.drawCircle(0f,0f,87f,p)
                c.save();c.clipPath(Path().apply{addCircle(0f,0f,87f,Path.Direction.CW)})
                p.shader=shader("bottom");c.drawRect(-88f,-88f,88f,88f,p)
                p.shader=shader("inward");c.drawCircle(0f,0f,87f,p);c.restore()
                save(b,File("../raha-feedback/src/main/assets/raha/baked/$name-$theme.png"))
            }
            val b=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888);val c=Canvas(b);c.translate(128f,128f)
            val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeWidth=4f;strokeCap=Paint.Cap.ROUND;color=if(light)0xffa4dcff.toInt()else 0xffd0f2ff.toInt()}
            for(i in 0 until 40){p.alpha=255*(i+1)/40;c.drawArc(RectF(-87f,-87f,87f,87f),i*2.3f,2.5f,false,p)}
            save(b,File("../raha-feedback/src/main/assets/raha/baked/processing-$theme.png"))
            // Same coordinate system and palettes as both original loading views.
            val ring=Bitmap.createBitmap(240,240,Bitmap.Config.ARGB_8888);val r=Canvas(ring);r.scale(2f,2f);r.translate(60f,60f)
            p.style=Paint.Style.STROKE;p.strokeWidth=18f;p.strokeCap=Paint.Cap.BUTT;p.alpha=255
            p.shader=SweepGradient(0f,0f,if(light)intArrayOf(0x00243860,0xff47628f.toInt(),0xff304b79.toInt(),0xff203656.toInt(),0x00203656)else intArrayOf(0x00354b78,0xff354b78.toInt(),0xff6385bf.toInt(),0xffa9c6ed.toInt(),0x00a9c6ed),floatArrayOf(0f,.28f,.60f,.8333f,1f))
            r.drawArc(-47f,-47f,47f,47f,0f,300f,false,p)
            p.shader=null;p.style=Paint.Style.FILL;p.color=if(light)0xff203656.toInt()else 0xffa9c6ed.toInt();r.drawCircle(23.5f,-40.703f,9f,p)
            save(ring,File("src/main/assets/passenger/baked/loading-$theme.png"))
        }
        // Keep original weather exports outside the app; replace only resources used by the mapping.
        val backup=File(requireNotNull(System.getenv("ARTWORK_BACKUP")),"weather").apply{mkdirs()}
        val ids=(listOf(-1)+(0..99)).flatMap{listOf(LauncherWeatherCode.icon(it,true),LauncherWeatherCode.icon(it,false))}.distinct()
        for(id in ids){
            val name=app.resources.getResourceEntryName(id)
            val file=File("src/main/res/drawable-nodpi/$name.png")
            require(file.exists())
            val original=File(backup,file.name)
            require(original.exists()) { "Supply the original weather artwork backup; never normalize already-baked PNGs again" }
            save(ReferenceLauncherWeatherArtwork.decodeOriginal(original),file)
        }
    }
}
