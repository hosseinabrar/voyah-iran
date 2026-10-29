package com.abrar.voyahiran.feature.launcher

import android.content.Context
import android.animation.ValueAnimator
import android.view.animation.AccelerateDecelerateInterpolator
import android.graphics.Canvas
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView

/** H97C CompassCardView.rotateView: 200 ms rotation, counter-rotated labels. */
internal class LauncherCompassDial(context:Context):FrameLayout(context) {
    private var light=false
    private val ticks=object:View(context){
        private val artwork=StaticLauncherArtwork(this)
        override fun onDraw(c:Canvas){
            c.save();c.scale(width/400f,height/400f)
            artwork.draw(c,"compass-oem-scale${if(light)"-light" else ""}",RectF(59f,59f,341f,341f))
            artwork.draw(c,"compass-oem-north",RectF(192f,31f,208f,47f))
            c.restore()
        }
    }
    private val labels=(0..15).map { i -> TextView(context).apply {
        text=if(i<12)(i*30).toString() else listOf("N","E","S","W")[i-12]
        gravity=Gravity.CENTER
    } }
    init {addView(ticks);labels.forEach{addView(it)};setWillNotDraw(true);theme(false)}
    fun theme(light:Boolean){
        this.light=light;ticks.invalidate()
        labels.forEachIndexed{i,v->v.setTextColor(if(light)0xff263651.toInt() else if(i<12)0x99ffffff.toInt() else 0xcdffffff.toInt())}
    }
    private var turn:ValueAnimator?=null
    private var heading:Int?=null
    fun bearing(value:Float,animate:Boolean=true){
        val next=value.toInt()
        if(animate && heading==next)return
        heading=next
        turn?.cancel();turn=null
        val target=if(next>=180)360f-next else -next.toFloat()
        var from=rotation
        // Same seam handling as the OEM view (also avoids a full turn at north).
        if(from < -90f && target > 90f)from+=360f
        else if(from > 90f && target < -90f)from-=360f
        if(!animate){pose(target);return}
        turn=ValueAnimator.ofFloat(from,target).apply {
            duration=200
            interpolator=AccelerateDecelerateInterpolator()
            addUpdateListener{pose(it.animatedValue as Float)}
            start()
        }
    }
    private fun pose(value:Float){
        rotation=value
        labels.forEach{it.rotation=-value}
    }
    override fun onDetachedFromWindow(){
        turn?.cancel();turn=null
        super.onDetachedFromWindow()
    }
    override fun onLayout(changed:Boolean,l:Int,t:Int,r:Int,b:Int){
        ticks.layout(0,0,width,height)
        val sx=width/400f;val sy=height/400f
        // Exact complex pixel margins from H97C compass_card_large.xml.
        fun px(value:Int)=(value and -256)/8388608f
        val horizontal=intArrayOf(0,0x2dd1eb20,0xc451e20,0,0x4814720,0x27fd7020,0,0x2dbae120,0xa55c220,0x14e1420,0xc451e20,0x2dd1eb20,0,0x2dfae120,0,0x2df5c220)
        val vertical=intArrayOf(0xa51e20,0xaf85120,0x2f185220,0,0x369d7020,0x127c2920,0x6deb820,0x127c2920,0x369d7020,0,0x2f185220,0xaf85120,0x29dae120,0,0x2f3d7020,0)
        labels.forEachIndexed {i,v->
            v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,(if(i<12)22f else 25f)*sy)
            v.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.AT_MOST),MeasureSpec.makeMeasureSpec(height,MeasureSpec.AT_MOST))
            val w=v.measuredWidth;val h=v.measuredHeight
            val x=when(i){0,6,12,14->(width-w)/2f;1,2,3,4,5,13->width-w-px(horizontal[i])*sx;else->px(horizontal[i])*sx}
            val y=when(i){3,9,13,15->(height-h)/2f;4,5,6,7,8,14->height-h-px(vertical[i])*sy;else->px(vertical[i])*sy}
            v.layout(x.toInt(),y.toInt(),x.toInt()+w,y.toInt()+h)
        }
    }
}
