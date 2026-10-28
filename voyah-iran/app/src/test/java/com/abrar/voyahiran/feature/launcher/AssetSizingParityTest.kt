package com.abrar.voyahiran.feature.launcher

import android.app.Application
import android.graphics.*
import java.io.File
import kotlin.math.abs
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AssetSizingParityTest {
    private val output=File("build/reports/asset-sizing").apply{mkdirs()}
    private fun save(image:Bitmap,name:String){File(output,"$name.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)}}
    private fun error(a:Bitmap,b:Bitmap):Double {
        assertEquals(a.width,b.width);assertEquals(a.height,b.height)
        var sum=0L
        for(y in 0 until a.height)for(x in 0 until a.width){val p=a.getPixel(x,y);val q=b.getPixel(x,y)
            sum+=maxOf(abs(Color.red(p)-Color.red(q)),abs(Color.green(p)-Color.green(q)),abs(Color.blue(p)-Color.blue(q)),abs(Color.alpha(p)-Color.alpha(q)))
        };return sum.toDouble()/(a.width*a.height)
    }
    @Test fun allResizedFilesHaveExpectedDimensionsAndVisualParity(){
        val entries=AssetSizingPlan.entries()
        assertEquals(558,entries.size)
        val root=System.getenv("ASSET_SIZE_BACKUP")?.let(::File)
        val metrics=StringBuilder("path,theme,mean_max_channel_difference\n")
        for(entry in entries){
            val bitmap=requireNotNull(BitmapFactory.decodeFile(entry.file.path))
            assertEquals(entry.file.path,entry.width,bitmap.width);assertEquals(entry.file.path,entry.height,bitmap.height)
            if(root!=null){
                val before=requireNotNull(BitmapFactory.decodeFile(AssetSizingPlan.backupFile(root,entry).path))
                for(light in listOf(false,true)){
                    fun draw(source:Bitmap)=Bitmap.createBitmap(entry.displayWidth,entry.displayHeight,Bitmap.Config.ARGB_8888).also{
                        it.eraseColor(if(light)0xffedf3fc.toInt()else 0xff25314b.toInt())
                        Canvas(it).drawBitmap(source,null,RectF(0f,0f,it.width.toFloat(),it.height.toFloat()),Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                    }
                    val a=draw(before);val b=draw(bitmap);val delta=error(a,b)
                    metrics.append("${entry.file.path},$light,$delta\n")
                    assertTrue("${entry.file.name} difference=$delta",delta<2.5)
                    a.recycle();b.recycle()
                };before.recycle()
            };bitmap.recycle()
        }
        File(output,"quality.csv").writeText(metrics.toString())
    }
    @Test fun productionCardBeforeAfter(){
        assumeTrue(System.getenv("ASSET_SIZE_BACKUP")!=null)
        val backup=File(System.getenv("ASSET_SIZE_BACKUP"))
        for((group,oldDir)in listOf("passenger-fragrance" to "fragrance-before","passenger-seats" to "seats-before")){
            val paths=File(backup,oldDir).listFiles().orEmpty().filter{it.extension=="png"}
            assertTrue(paths.isNotEmpty())
            for(old in paths){
                val a=requireNotNull(BitmapFactory.decodeFile(old.path))
                val b=requireNotNull(BitmapFactory.decodeFile(File("build/reports/$group",old.name).path))
                val delta=error(a,b)
                assertTrue("${old.name} difference=$delta",delta<1.5)
                if(old.name in listOf("fragrance-light-2-on.png","fragrance-dark-2-on.png","seats-light-both-on.png","seats-dark-both-on.png")){
                    val pair=Bitmap.createBitmap(a.width,a.height*2,Bitmap.Config.ARGB_8888)
                    Canvas(pair).apply{drawBitmap(a,0f,0f,null);drawBitmap(b,0f,a.height.toFloat(),null)}
                    save(pair,"before-after-${old.nameWithoutExtension}");pair.recycle()
                }
                a.recycle();b.recycle()
            }
        }
    }
}
