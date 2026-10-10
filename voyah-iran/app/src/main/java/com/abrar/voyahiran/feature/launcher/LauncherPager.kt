package com.abrar.voyahiran.feature.launcher

import android.animation.ValueAnimator
import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.animation.Interpolator
import android.widget.FrameLayout
import kotlin.math.abs

/** Horizontal swipes cancel the child's tap; vertical gestures and simple taps stay with the card. */
class LauncherPager(context:Context):FrameLayout(context) {
    var page=0;private set
    var onPage:(Int)->Unit={}
    private var downX=0f;private var downY=0f;private var dragging=false
    private val config=ViewConfiguration.get(context)
    private val slop=config.scaledTouchSlop
    private var velocity:VelocityTracker?=null
    private var animation:ValueAnimator?=null
    private var offset=0f
    private val lastPage get() = (childCount - 1).coerceAtLeast(0)
    private var downOffset=0f

    override fun dispatchTouchEvent(e:MotionEvent):Boolean {
        if(e.actionMasked==MotionEvent.ACTION_DOWN) {
            animation?.cancel()
            downOffset=offset;downX=e.x;downY=e.y;dragging=false
            velocity?.recycle();velocity=VelocityTracker.obtain()
        }
        velocity?.addMovement(e)
        val result=super.dispatchTouchEvent(e)
        if(e.actionMasked==MotionEvent.ACTION_UP || e.actionMasked==MotionEvent.ACTION_CANCEL) {
            velocity?.recycle();velocity=null
        }
        return result
    }
    override fun onInterceptTouchEvent(e:MotionEvent):Boolean {
        if(e.pointerCount>1)return false
        if(e.actionMasked==MotionEvent.ACTION_MOVE && abs(e.x-downX)>slop && abs(e.x-downX)>abs(e.y-downY)*1.5f) {
            dragging=true;return true
        }
        return dragging
    }
    private fun translate(value:Float) {
        offset=value
        for(i in 0 until childCount)getChildAt(i).translationX=i*width+value
    }
    override fun onTouchEvent(e:MotionEvent):Boolean {
        if(e.pointerCount>1 || e.actionMasked==MotionEvent.ACTION_CANCEL) {
            dragging=false;settle(page,true,0f);return true
        }
        when(e.actionMasked) {
            MotionEvent.ACTION_MOVE -> translate((downOffset+e.x-downX).coerceIn(-lastPage*width.toFloat(),0f))
            MotionEvent.ACTION_UP -> {
                translate((downOffset+e.x-downX).coerceIn(-lastPage*width.toFloat(),0f))
                velocity?.computeCurrentVelocity(1000,config.scaledMaximumFlingVelocity.toFloat())
                val vx=velocity?.xVelocity ?: 0f
                val next=if(abs(vx)>=config.scaledMinimumFlingVelocity && abs(e.x-downX)>slop)
                    if(vx<0)page+1 else page-1
                else if(abs(e.x-downX)>width*.08f) page+(if(e.x<downX)1 else -1) else page
                dragging=false;settle(next,true,vx)
            }
        }
        return true
    }
    fun show(index:Int,animate:Boolean=true)=settle(index,animate,0f)
    private fun settle(index:Int,animate:Boolean,releaseVelocity:Float) {
        animation?.cancel()
        val next=index.coerceIn(0,lastPage);val changed=next!=page;page=next
        val target=-page*width.toFloat()
        val distance=target-offset
        if(!animate || abs(distance)<1f)translate(target) else {
            val toward=if(distance*releaseVelocity>0)abs(releaseVelocity) else 0f
            val duration=if(toward>0) (2000f*abs(distance)/toward).toLong().coerceIn(120,420) else 260L
            val tangent=if(toward>0) (toward*duration/1000f/abs(distance)).coerceIn(0f,3f) else 0f
            animation=ValueAnimator.ofFloat(offset,target).apply {
                this.duration=duration
                // Cubic Hermite: use the finger's release velocity, then decelerate to rest.
                interpolator=Interpolator { t -> (-2*t*t*t+3*t*t)+tangent*(t*t*t-2*t*t+t) }
                addUpdateListener { translate(it.animatedValue as Float) }
                start()
            }
        }
        for(i in 0 until childCount)getChildAt(i).importantForAccessibility=
            if(i==page)IMPORTANT_FOR_ACCESSIBILITY_AUTO else IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        if(changed)onPage(page)
    }
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int){super.onSizeChanged(w,h,oldw,oldh);settle(page,false,0f)}
    override fun onDetachedFromWindow(){animation?.cancel();velocity?.recycle();velocity=null;super.onDetachedFromWindow()}
}
