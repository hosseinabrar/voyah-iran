package com.abrar.voyahiran.feature.launcher

import android.app.Application
import android.graphics.*
import java.io.File
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Explicit, offline-only resize. Original files stay outside the repository. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AssetSizingBakeTest {
    @Test fun resizeOnlyAuditedOversizedImages(){
        assumeTrue(System.getenv("RESIZE_AUDITED_ASSETS")=="1")
        val backup=File(requireNotNull(System.getenv("ASSET_SIZE_BACKUP")))
        val entries=AssetSizingPlan.entries()
        assertEquals(558,entries.size)
        val report=StringBuilder("path,before_bytes,after_bytes,width,height\n")
        for(entry in entries){
            val original=AssetSizingPlan.backupFile(backup,entry)
            if(!original.exists()){original.parentFile.mkdirs();entry.file.copyTo(original)}
            val source=requireNotNull(BitmapFactory.decodeFile(original.path))
            assertEquals(original.path,entry.oldWidth,source.width);assertEquals(original.path,entry.oldHeight,source.height)
            val output=Bitmap.createScaledBitmap(source,entry.width,entry.height,true)
            entry.file.outputStream().use{assertTrue(output.compress(Bitmap.CompressFormat.PNG,100,it))}
            output.recycle();if(output!==source)source.recycle()
        }
        // Preserve each source's own alpha; artwork corrections are outside this resize.
        for(entry in entries){
            val original=AssetSizingPlan.backupFile(backup,entry)
            report.append("${entry.file.path},${original.length()},${entry.file.length()},${entry.width},${entry.height}\n")
        }
        File("build/reports/asset-sizing").mkdirs()
        File("build/reports/asset-sizing/sizes.csv").writeText(report.toString())
    }
}
