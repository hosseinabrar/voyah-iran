package com.abrar.voyahiran.feature.launcher

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import kotlin.math.cos

/** Retarget from the currently displayed value, never from the previous CAN target. */
internal class MotionValue(initial: Float) {
    var target = initial; private set
    private var origin = initial
    private var started = 0L
    private var duration = 0L
    private var spring = false
    fun value(now: Long): Float {
        if (duration == 0L) return target
        val t = ((now - started).toFloat() / duration).coerceIn(0f, 1f)
        val eased = if (spring) { val x=t-1f; x*x*(2f*x+1f)+1f }
            else ((1.0-cos(Math.PI*t))/2.0).toFloat()
        return origin + (target-origin)*eased
    }
    fun moving(now: Long) = duration > 0 && now-started < duration
    fun set(next: Float, now: Long, animate: Boolean, spring: Boolean = false) {
        if (!animate) { target=next; origin=next; duration=0; return }
        if (target == next) return
        origin=value(now); target=next; started=now; duration=if(spring)400 else 280
        this.spring=spring
    }
    fun finish() { origin=target; duration=0 }
}

/** One frame callback per card, only while a visible control is actually changing.
 * OEM QGRadioGroup: 400 ms + OvershootInterpolator(1). No bitmap snapshots/layout work.
 */
internal class LauncherMotion(private val view: View) {
    private val values=HashMap<String,MotionValue>()
    private val contents=HashMap<String,String>()
    private val variants=HashMap<String,MutableSet<String>>()
    private var queued=false
    var paused=false
        set(value){field=value;if(value)finish()}
    private fun enabled()=!paused && view.isAttachedToWindow && view.isShown &&
        view.windowVisibility==View.VISIBLE && ValueAnimator.areAnimatorsEnabled()
    private val frame=object:Runnable {
        override fun run() {
            queued=false
            if(!enabled()){finish();return}
            view.invalidate()
            if(values.values.any{it.moving(SystemClock.uptimeMillis())})schedule()
        }
    }
    init {view.addOnAttachStateChangeListener(object:View.OnAttachStateChangeListener{
        override fun onViewAttachedToWindow(v:View){}
        override fun onViewDetachedFromWindow(v:View){finish()}
    })}
    private fun schedule(){if(!queued){queued=true;view.postOnAnimation(frame)}}
    fun finish(){view.removeCallbacks(frame);queued=false;values.values.forEach{it.finish()}}
    fun updated(){if(!enabled()){finish();values.clear();contents.clear();variants.clear()}}
    fun value(key:String,target:Float,spring:Boolean=false):Float {
        val now=SystemClock.uptimeMillis()
        val v=values.getOrPut(key){MotionValue(target)}
        v.set(target,now,enabled()&&(!spring || (v.target>=0f&&target>=0f)),spring)
        if(v.moving(now))schedule()
        return v.value(now)
    }
    fun color(key:String,active:Boolean,off:Int,on:Int):Int {
        val f=value(key,if(active)1f else 0f).coerceIn(0f,1f)
        fun channel(a:Int,b:Int)=(a+(b-a)*f).toInt()
        return Color.argb(channel(Color.alpha(off),Color.alpha(on)),channel(Color.red(off),Color.red(on)),
            channel(Color.green(off),Color.green(on)),channel(Color.blue(off),Color.blue(on)))
    }
    /** Small local crossfade, retaining intermediate weights when taps arrive rapidly. */
    fun text(key:String,token:String,c:Canvas,bounds:RectF,draw:(String)->Unit)=content(key,token,c,bounds,true,draw)
    fun content(key:String,token:String,c:Canvas,bounds:RectF,sequential:Boolean=false,draw:(String)->Unit) {
        val first=contents.put(key,token)==null
        val tokens=variants.getOrPut(key){linkedSetOf(token)}
        if(tokens.add(token))values["$key/$token"]=MotionValue(0f)
        if(first)values["$key/$token"]=MotionValue(1f)
        val iterator=tokens.iterator()
        while(iterator.hasNext()) {
            val item=iterator.next()
            val weight=value("$key/$item",if(item==token)1f else 0f).coerceIn(0f,1f)
            val alpha=if(sequential)(weight*2f-1f).coerceIn(0f,1f)else weight
            if(alpha>=1f)draw(item)
            else if(alpha>0f){val layer=c.saveLayerAlpha(bounds,(alpha*255).toInt());draw(item);c.restoreToCount(layer)}
            else if(weight==0f&&item!=token){iterator.remove();values.remove("$key/$item")}
        }
    }
}
