package com.abrar.voyahiran.feature.launcher

import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.*
import org.junit.Test

/** Theme/power changes must never move a flower, leaf, or pyramid silhouette. */
class FragranceGeometryContractTest {
    @Test fun allVariantsShareCanvasAndPerScentAlpha() {
        val assets=File("src/main/assets/passenger/fragrance-wide")
        val master=ImageIO.read(File(assets,"object-2.png"))
        for(tone in 1..3) {
            val base=ImageIO.read(File(assets,"object-$tone.png"))
            val day="object-$tone-day-v${if(tone==2)2 else 1}"
            for(name in listOf("object-$tone","object-$tone-gray",day,"$day-gray")) {
                val variant=ImageIO.read(File(assets,"$name.png"))
                assertEquals(name,master.width,variant.width)
                assertEquals(name,master.height,variant.height)
                val a=base.getRGB(0,0,base.width,base.height,null,0,base.width)
                val b=variant.getRGB(0,0,variant.width,variant.height,null,0,variant.width)
                assertTrue("Alpha geometry drift: $name",a.indices.all{a[it] ushr 24==b[it] ushr 24})
            }
        }
    }
}
