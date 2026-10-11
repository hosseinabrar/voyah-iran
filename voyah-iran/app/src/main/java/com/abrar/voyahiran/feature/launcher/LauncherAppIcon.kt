package com.abrar.voyahiran.feature.launcher

import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

/** Normalize visible bounds once during catalog loading, not during drawing or dragging. */
internal object LauncherAppIcon {
    fun create(resources:Resources, source:Drawable?, rounded:Boolean=false):Drawable? {
        source ?: return null
        val size=256
        val input=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888)
        val old=Rect(source.bounds)
        val w=source.intrinsicWidth.coerceAtLeast(1)
        val h=source.intrinsicHeight.coerceAtLeast(1)
        val scale=size.toFloat()/maxOf(w,h)
        val dw=(w*scale).toInt().coerceAtLeast(1);val dh=(h*scale).toInt().coerceAtLeast(1)
        try {source.setBounds((size-dw)/2,(size-dh)/2,(size+dw)/2,(size+dh)/2);source.draw(Canvas(input))}
        finally {source.bounds=old}
        val pixels=IntArray(size*size);input.getPixels(pixels,0,size,0,0,size,size)
        var left=size;var top=size;var right=-1;var bottom=-1
        for(y in 0 until size)for(x in 0 until size)if(Color.alpha(pixels[y*size+x])>=128) {
            left=minOf(left,x);top=minOf(top,y);right=maxOf(right,x);bottom=maxOf(bottom,y)
        }
        if(right<left){input.recycle();return source}
        val bounds=Rect(left,top,right+1,bottom+1)
        val output=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(output)
        if(rounded)canvas.clipPath(Path().apply {addRoundRect(RectF(0f,0f,256f,256f),48f,48f,Path.Direction.CW)})
        val fit=size.toFloat()/maxOf(bounds.width(),bounds.height())
        val width=bounds.width()*fit;val height=bounds.height()*fit
        canvas.drawBitmap(input,bounds,RectF((size-width)/2,(size-height)/2,(size+width)/2,(size+height)/2),Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        input.recycle()
        return BitmapDrawable(resources,output)
    }
}
