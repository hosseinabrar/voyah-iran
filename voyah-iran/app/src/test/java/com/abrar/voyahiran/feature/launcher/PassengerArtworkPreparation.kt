package com.abrar.voyahiran.feature.launcher

import android.os.Looper
import org.junit.Assert.assertNotNull
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** Native snapshots must wait for the same asynchronous artwork used in production. */
internal fun preparePassengerArtwork(directory:String) {
    val context=RuntimeEnvironment.getApplication()
    val names=context.assets.list("passenger/$directory").orEmpty().filter {
        it.endsWith(".png") && !it.startsWith("fragrance_anim_") && !it.startsWith("fragrance_icon_")
    }
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
