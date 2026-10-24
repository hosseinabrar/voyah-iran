package com.abrar.voyahiran.feature.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.*

/** Live sensor pose, without temporal interpolation; labels remain upright. */
internal class LauncherCompassDial(context:Context):FrameLayout(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val ticks=object:View(context){
        override fun onDraw(c:Canvas){
            c.save();c.scale(width/440f,height/440f)
            for(i in 0 until 180){
                val a=i*PI/90-PI/2;val inner=if(i%15==0)141f else 154f
                paint.strokeWidth=if(i%15==0)2.3f else 1f
                c.drawLine(220+cos(a).toFloat()*166,220+sin(a).toFloat()*166,
                    220+cos(a).toFloat()*inner,220+sin(a).toFloat()*inner,paint)
            };c.restore()
        }
    }
    private val labels=(0..15).map { i -> TextView(context).apply {
        text=if(i<12)(i*30).toString() else listOf("N","E","S","W")[i-12]
        gravity=Gravity.CENTER;includeFontPadding=false
        if(i>=12)setTypeface(typeface,android.graphics.Typeface.BOLD)
    } }
    init {addView(ticks);labels.forEach{addView(it)};setWillNotDraw(true);theme(false)}
    fun theme(light:Boolean){
        paint.color=if(light)0xff6d7e99.toInt() else 0xffc0cbe0.toInt();ticks.invalidate()
        labels.forEach{it.setTextColor(if(light)0xff263651.toInt() else android.graphics.Color.WHITE)}
    }
    fun bearing(value:Float){
        rotation=-value
        labels.forEach{it.rotation=value}
    }
    override fun onLayout(changed:Boolean,l:Int,t:Int,r:Int,b:Int){
        ticks.layout(0,0,width,height)
        val sx=width/440f;val sy=height/440f
        labels.forEachIndexed {i,v->
            val a=if(i<12)i*PI/6-PI/2 else (i-12)*PI/2-PI/2
            val radius=if(i<12)199f else 126f
            val x=(220+cos(a)*radius)*sx;val y=(220+sin(a)*radius)*sy
            v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,(if(i<12)21f else 25f)*sy)
            v.measure(MeasureSpec.makeMeasureSpec((56*sx).toInt(),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec((36*sy).toInt(),MeasureSpec.EXACTLY))
            v.layout((x-28*sx).toInt(),(y-18*sy).toInt(),(x+28*sx).toInt(),(y+18*sy).toInt())
        }
    }
}
