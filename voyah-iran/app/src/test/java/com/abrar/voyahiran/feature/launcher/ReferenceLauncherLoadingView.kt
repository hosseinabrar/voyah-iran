package com.abrar.voyahiran.feature.launcher

import android.content.Context
import android.graphics.*
import android.view.View

/** Same thick comet ring, palette and rotation period as the map placeholder. */
internal class ReferenceLauncherLoadingView(context:Context):View(context) {
    var light=false
        set(value){field=value;invalidate()}
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val dark=SweepGradient(0f,0f,intArrayOf(0x00354b78,0xff354b78.toInt(),0xff6385bf.toInt(),0xffa9c6ed.toInt(),0x00a9c6ed),floatArrayOf(0f,.28f,.60f,.8333f,1f))
    private val bright=SweepGradient(0f,0f,intArrayOf(0x00243860,0xff47628f.toInt(),0xff304b79.toInt(),0xff203656.toInt(),0x00203656),floatArrayOf(0f,.28f,.60f,.8333f,1f))
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        canvas.save();canvas.translate(width/2f,height/2f)
        val scale=width/1775f
        canvas.scale(scale,scale)
        canvas.rotate((android.os.SystemClock.uptimeMillis()%1200L)*.3f)
        paint.style=Paint.Style.STROKE;paint.strokeWidth=18f;paint.shader=if(light)bright else dark
        canvas.drawArc(-47f,-47f,47f,47f,0f,300f,false,paint)
        paint.shader=null;paint.style=Paint.Style.FILL
        paint.color=if(light)0xff203656.toInt() else 0xffa9c6ed.toInt()
        canvas.drawCircle(23.5f,-40.703f,9f,paint)
        canvas.restore()
        if(isShown)postInvalidateOnAnimation()
    }
}
