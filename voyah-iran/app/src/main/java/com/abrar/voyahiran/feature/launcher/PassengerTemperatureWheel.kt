package com.abrar.voyahiran.feature.launcher

import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.OverScroller
import kotlin.math.abs
import kotlin.math.roundToInt

/** OEM pattern: freely scroll, coast, center the nearest half-degree, then send one request. */
internal class PassengerTemperatureWheel(private val view:View, private val commit:(Int)->Unit) {
    private val scroller=OverScroller(view.context)
    private val configuration=ViewConfiguration.get(view.context)
    // OverScroller friction operates in physical pixels, not arbitrary fixed-point units.
    internal val stepPixels get()=56f*view.height.coerceAtLeast(1)/370f
    private var velocity:VelocityTracker?=null
    var position=0f; private set
    var busy=false; private set
    private var down=0f
    private var start=0f
    private var snapping=false
    private var changed=false
    fun sync(value:Int){if(!busy)position=(value-155)/5f}
    fun down(e:MotionEvent,y:Float){
        scroller.abortAnimation();velocity?.recycle();velocity=VelocityTracker.obtain().also{it.addMovement(e)}
        down=y;start=position;busy=true;changed=false;snapping=false
    }
    fun move(e:MotionEvent,y:Float){
        velocity?.addMovement(e)
        changed=changed||abs(y-down)*view.height/370f>configuration.scaledTouchSlop
        if(changed)position=(start+(y-down)/56f).coerceIn(0f,30f)
        view.invalidate()
    }
    fun up(e:MotionEvent,y:Float){
        move(e,y)
        velocity?.computeCurrentVelocity(1000,configuration.scaledMaximumFlingVelocity.toFloat())
        val speed=velocity?.yVelocity?:0f
        velocity?.recycle();velocity=null
        if(!changed){position=(position+if(y<130)1 else if(y>185)-1 else 0).coerceIn(0f,30f);finish();return}
        if(abs(speed)>=configuration.scaledMinimumFlingVelocity) {
            scroller.fling(0,(position*stepPixels).roundToInt(),0,speed.roundToInt(),0,0,0,(30*stepPixels).roundToInt())
        } else snap()
        view.postInvalidateOnAnimation()
    }
    fun advance(){
        if(!busy||velocity!=null)return
        if(scroller.computeScrollOffset()){
            position=(scroller.currY/stepPixels).coerceIn(0f,30f);view.postInvalidateOnAnimation()
        }else if(!snapping){
            snap()
        }else finish()
    }
    private fun snap(){
        snapping=true
        val from=(position*stepPixels).roundToInt();val to=(position.roundToInt()*stepPixels).roundToInt()
        if(from==to)finish() else {scroller.startScroll(0,from,0,to-from,180);view.postInvalidateOnAnimation()}
    }
    private fun finish(){position=position.roundToInt().coerceIn(0,30).toFloat();busy=false;if(position!=start)commit(155+position.toInt()*5);view.invalidate()}
    fun cancel(){scroller.abortAnimation();velocity?.recycle();velocity=null;busy=false}
}
