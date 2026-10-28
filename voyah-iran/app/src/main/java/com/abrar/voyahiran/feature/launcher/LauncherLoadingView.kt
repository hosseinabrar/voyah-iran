package com.abrar.voyahiran.feature.launcher

import android.content.Context
import android.graphics.*
import android.view.View

/** Same thick comet ring, palette and rotation period as the map placeholder. */
internal class LauncherLoadingView(context:Context):View(context) {
    var light=false
        set(value){field=value;invalidate()}
    private val artwork=StaticLauncherArtwork(this)
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        canvas.save();canvas.translate(width/2f,height/2f)
        val scale=width/1775f
        canvas.scale(scale,scale)
        canvas.rotate((android.os.SystemClock.uptimeMillis()%1200L)*.3f)
        artwork.draw(canvas,"loading-${if(light)"light"else "dark"}",RectF(-60f,-60f,60f,60f))
        canvas.restore()
        if(isShown)postInvalidateOnAnimation()
    }
}
