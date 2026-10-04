package com.abrar.voyahiran.feature.launcher

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/** Horizontal swipes cancel the child's tap; vertical gestures and simple taps stay with the card. */
class LauncherPager(context:Context):FrameLayout(context) {
    var page=0;private set
    var onPage:(Int)->Unit={}
    private var downX=0f;private var downY=0f;private var dragging=false
    private val slop=ViewConfiguration.get(context).scaledTouchSlop
    override fun onInterceptTouchEvent(e:MotionEvent):Boolean {
        if(e.pointerCount>1)return false // Leave the car's three-finger display gesture alone.
        when(e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {downX=e.x;downY=e.y;dragging=false}
            MotionEvent.ACTION_MOVE -> if(abs(e.x-downX)>slop && abs(e.x-downX)>abs(e.y-downY)*1.5f) {
                dragging=true;return true
            }
        }
        return dragging
    }
    override fun onTouchEvent(e:MotionEvent):Boolean {
        if(e.pointerCount>1 || e.actionMasked==MotionEvent.ACTION_CANCEL) {dragging=false;position(false);return true}
        when(e.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val dx=(e.x-downX).coerceIn(if(page==0)-width.toFloat() else 0f,if(page==0)0f else width.toFloat())
                for(i in 0 until childCount){getChildAt(i).animate().cancel();getChildAt(i).translationX=(i-page)*width+dx}
            }
            MotionEvent.ACTION_UP -> {show(LauncherVehicleState.swipePage(page,e.x-downX,e.y-downY,width));dragging=false}
        }
        return true
    }
    fun show(index:Int,animate:Boolean=true) {
        val next=index.coerceIn(0,1);val changed=next!=page;page=next
        position(animate);if(changed)onPage(page)
    }
    private fun position(animate:Boolean) {
        for(i in 0 until childCount) {
            val view=getChildAt(i);view.animate().cancel()
            if(animate)view.animate().translationX(((i-page)*width).toFloat()).setDuration(220).start()
            else view.translationX=((i-page)*width).toFloat()
            view.importantForAccessibility=if(i==page)IMPORTANT_FOR_ACCESSIBILITY_AUTO else IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
    }
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int){super.onSizeChanged(w,h,oldw,oldh);position(false)}
}
