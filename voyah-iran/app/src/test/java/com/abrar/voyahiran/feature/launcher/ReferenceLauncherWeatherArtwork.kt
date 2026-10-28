package com.abrar.voyahiran.feature.launcher

import android.content.res.Resources
import android.graphics.*

/** Normalize the visible artwork, not its transparent export margins. Decode once per icon. */
internal object ReferenceLauncherWeatherArtwork {
    fun decode(resources: Resources, id: Int): Bitmap {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(resources, id, options)
        options.inSampleSize = 1
        while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 720) {
            options.inSampleSize *= 2
        }
        options.inJustDecodeBounds = false
        val source = requireNotNull(BitmapFactory.decodeResource(resources, id, options))
        return normalize(source)
    }
    fun decodeOriginal(file:java.io.File):Bitmap {
        val options=BitmapFactory.Options().apply{inJustDecodeBounds=true}
        BitmapFactory.decodeFile(file.path,options)
        options.inSampleSize=1
        while(maxOf(options.outWidth,options.outHeight)/options.inSampleSize>720)options.inSampleSize*=2
        options.inJustDecodeBounds=false
        return normalize(requireNotNull(BitmapFactory.decodeFile(file.path,options)))
    }
    private fun normalize(source:Bitmap):Bitmap {
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        var left = source.width; var top = source.height; var right = -1; var bottom = -1
        pixels.forEachIndexed { index, pixel ->
            if (Color.alpha(pixel) >= 128) {
                val x = index % source.width; val y = index / source.width
                left = minOf(left, x); right = maxOf(right, x)
                top = minOf(top, y); bottom = maxOf(bottom, y)
            }
        }
        if (right < left) return source
        val scale = 300f / maxOf(right - left + 1, bottom - top + 1)
        val x = 180f - (left + right + 1) * .5f * scale
        val y = 180f - (top + bottom + 1) * .5f * scale
        val output = Bitmap.createBitmap(360, 360, Bitmap.Config.ARGB_8888)
        Canvas(output).drawBitmap(source, null,
            RectF(x, y, x + source.width * scale, y + source.height * scale),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        source.recycle()
        return output
    }
}
