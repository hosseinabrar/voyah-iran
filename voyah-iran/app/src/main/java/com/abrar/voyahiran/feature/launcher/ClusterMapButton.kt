package com.abrar.voyahiran.feature.launcher

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View

/** Drawn above the card surface, never into the shared map frames. */
internal class ClusterMapButton(context: Context) : View(context) {
    companion object {
        // Position the centre 30% above the physical screen bottom, independent of safe-area insets.
        internal fun topMargin(screenHeight:Int,cardTop:Int,size:Int)=
            (screenHeight*.70f-cardTop-size/2f).toInt().coerceAtLeast(0)
    }
    var mode = ClusterMapMode.ON
        set(value) { field=value; invalidate(); contentDescription=when(value) {
            ClusterMapMode.OFF -> "نقشهٔ کلاستر: خاموش"
            ClusterMapMode.AUTO -> "نقشهٔ کلاستر: هوشمند"
            ClusterMapMode.ON -> "نقشهٔ کلاستر: روشن"
        } }
    private val active get() = mode != ClusterMapMode.OFF
    var light = false
        set(value) { if(field!=value) {field=value;invalidate()} }
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val activeLight=LinearGradient(0f,0f,0f,100f,
        0xff719eff.toInt(),0xff3868e5.toInt(),Shader.TileMode.CLAMP)
    private val cluster=Path().apply {
        moveTo(18f,40f); quadTo(50f,13f,82f,40f); lineTo(76f,65f)
        quadTo(75f,69f,70f,69f);lineTo(30f,69f);quadTo(25f,69f,24f,65f);close()
    }
    private val eye=Path().apply { moveTo(35f,51f);quadTo(50f,35f,65f,51f);quadTo(50f,67f,35f,51f) }
    init { isClickable=true;isFocusable=true;mode=ClusterMapMode.ON }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save();canvas.scale(width/100f,height/100f)
        if(isPressed)canvas.scale(.94f,.94f,50f,50f)
        paint.style=Paint.Style.FILL
        paint.shader=if(active && light) activeLight else null
        paint.color=if(active && !light)0xff00aaff.toInt() else if(light)0xffd1d8e2.toInt() else 0xff454d5c.toInt()
        canvas.drawCircle(50f,50f,48f,paint);paint.shader=null
        paint.color=if(active || !light)Color.WHITE else 0xff5d6878.toInt()
        paint.strokeWidth=3.6f;paint.strokeCap=Paint.Cap.ROUND;paint.strokeJoin=Paint.Join.ROUND
        paint.style=Paint.Style.STROKE;canvas.drawPath(cluster,paint)
        if(mode == ClusterMapMode.AUTO) {
            paint.style=Paint.Style.FILL;paint.textSize=18f;paint.textAlign=Paint.Align.CENTER
            paint.typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
            canvas.drawText("Smart",50f,57f,paint)
        } else {
        canvas.drawPath(eye,paint)
        if(active) {paint.style=Paint.Style.FILL;canvas.drawCircle(50f,51f,4f,paint)}
        else canvas.drawLine(39f,62f,61f,40f,paint)
        }
        canvas.restore()
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled=super.onTouchEvent(event);invalidate();return handled
    }
}
