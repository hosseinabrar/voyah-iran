package com.abrar.voyahiran.feature.launcher

import android.os.Looper
import org.junit.Assert.assertNotNull
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** Native snapshots must wait for the same asynchronous artwork used in production. */
internal fun preparePassengerArtwork(directory:String) {
    val context=RuntimeEnvironment.getApplication()
    // Preparing unused historical fragrance variants evicts the actual landscapes from
    // the bounded production cache before the first snapshot can retain them.
    val names=if(directory=="fragrance-wide") listOf("landscape","landscape-day-v1","object-1","object-2","object-3",
        "object-1-day-v1","object-2-day-v2","object-3-day-v1").map{"$it.png"} +
        context.assets.list("passenger/$directory").orEmpty().filter{it.startsWith("fragrance_icon_")}
    else context.assets.list("passenger/$directory").orEmpty().filter {it.endsWith(".png")}
    for(name in names) {
        val path="passenger/$directory/$name"
        val deadline=System.nanoTime()+5_000_000_000L
        var bitmap=PassengerArtworkCache.get(context,path)
        while(bitmap==null && System.nanoTime()<deadline) {
            Thread.sleep(5)
            shadowOf(Looper.getMainLooper()).idle()
            bitmap=PassengerArtworkCache.get(context,path)
        }
        assertNotNull("Artwork not prepared: $path",bitmap)
    }
}
