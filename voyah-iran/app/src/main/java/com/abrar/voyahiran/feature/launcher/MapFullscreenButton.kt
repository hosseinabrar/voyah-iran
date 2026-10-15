package com.abrar.voyahiran.feature.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

internal class MapFullscreenButton(context:Context):View(context) {
    var light=false
        set(value){field=value;invalidate()}
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    init {isClickable=true;isFocusable=true;contentDescription="نمایش تمام‌صفحهٔ نقشه"}
    override fun onDraw(c:Canvas) {
        c.save();c.scale(width/100f,height/100f)
        paint.style=Paint.Style.FILL;paint.color=if(light)0xffd1d8e2.toInt() else 0xff454d5c.toInt()
        c.drawCircle(50f,50f,if(isPressed)44f else 48f,paint)
        paint.color=if(light)0xff5d6878.toInt() else android.graphics.Color.WHITE
        paint.style=Paint.Style.STROKE;paint.strokeWidth=4f;paint.strokeCap=Paint.Cap.ROUND
        for(x in listOf(30f,70f))for(y in listOf(30f,70f)) {
            c.drawLine(x,y,x+(if(x<50)14 else -14),y,paint)
            c.drawLine(x,y,x,y+(if(y<50)14 else -14),paint)
        }
        c.restore()
    }
    override fun onTouchEvent(e:MotionEvent):Boolean {
        if(e.actionMasked==MotionEvent.ACTION_DOWN)parent.requestDisallowInterceptTouchEvent(true)
        val result=super.onTouchEvent(e);invalidate()
        if(e.actionMasked in listOf(MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL))parent.requestDisallowInterceptTouchEvent(false)
        return result
    }
}
