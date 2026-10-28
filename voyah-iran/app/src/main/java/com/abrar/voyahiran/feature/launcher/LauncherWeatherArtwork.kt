package com.abrar.voyahiran.feature.launcher

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory

/** Weather PNGs are normalized offline; decoding does not scan pixels or allocate another canvas. */
internal object LauncherWeatherArtwork {
    fun decode(resources: Resources, id: Int): Bitmap =
        requireNotNull(BitmapFactory.decodeResource(resources,id,BitmapFactory.Options().apply{inScaled=false}))
}
