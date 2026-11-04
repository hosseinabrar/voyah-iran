package com.abrar.voyahiran.feature.launcher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import java.util.Locale
import kotlin.math.abs

/** Only the content moves. The existing dashboard card remains fixed underneath. */
internal class LauncherClockStack(context:Context, private val clock:(Canvas)->Unit):View(context) {
    private val preferences=context.getSharedPreferences("launcher",0)
    var page=preferences.getInt("clock_stack_page",0).coerceIn(0,3); private set
    var telemetry=LauncherTelemetry()
        set(value){
            val changed=when(page){
                1 -> field.battery!=value.battery || field.fuel!=value.fuel || field.batteryRange!=value.batteryRange || field.fuelRange!=value.fuelRange
                2 -> field.rpm!=value.rpm || field.instantFuel!=value.instantFuel
                3 -> field.distance!=value.distance || field.minutes!=value.minutes || field.averageFuel!=value.averageFuel || field.averagePower!=value.averagePower
                else -> false
            }
            field=value
            if(changed)invalidate()
        }
    var light=false
    var resetting=false
    var onReset:()->Unit={}
    private val art=StaticLauncherArtwork(this)
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val latin=Typeface.create("sans-serif",Typeface.NORMAL)
    private val bold=Typeface.create("sans-serif",Typeface.BOLD)
    private val ink get()=if(light)0xff252f3c.toInt() else Color.WHITE
    private val muted get()=if(light)0xff71839e.toInt() else 0xffbfc9de.toInt()
    companion object {
        const val HOLD_MS=4000L
        fun levelTone(value:Double)=when { value<10 -> "red"; value<20 -> "orange"; else -> "blue" }
        val artworkPaths=(1..3).flatMap { i -> listOf("light","dark").map {"passenger/baked/clock-stack-$i-$it.png"} } +
            (0..3).flatMap { i -> listOf("light","dark").map {"passenger/baked/clock-stack-dots-$i-$it.png"} } +
            listOf("blue","orange","red").flatMap { tone -> listOf("cap","body").map { "passenger/baked/clock-stack-bar-$tone-$it.png" } } + "passenger/baked/clock-stack-hold-fill.png"
    }
    private val resetBounds=RectF(28f,352f,378f,402f)
    private val filledClip=Path()
    private val resetClip=Path().apply{addRoundRect(resetBounds,12f,12f,Path.Direction.CW)}
    private var holdStarted=0L
    private var completedHold=false
    internal val holdProgress:Float get()=when {
        completedHold -> 1f
        resetArmed -> ((android.os.SystemClock.uptimeMillis()-holdStarted).toFloat()/HOLD_MS).coerceIn(0f,1f)
        else -> 0f
    }
    private val holdFrame=object:Runnable {
        override fun run(){
            if(!resetArmed)return
            invalidate();postOnAnimation(this)
        }
    }
    private fun cancelHold(){resetArmed=false;handler.removeCallbacks(hold);removeCallbacks(holdFrame);completedHold=false;invalidate()}
    private var offset=-page*426f
    private var downOffset=offset
    private var downX=0f; private var downY=0f
    private var dragging=false;private var cancelled=false;private var held=false
    private var resetArmed=false
    private val handler=android.os.Handler(android.os.Looper.getMainLooper())
    private val slop=ViewConfiguration.get(context).scaledTouchSlop
    private var animation:ValueAnimator?=null
    private val hold=Runnable {
        if(resetArmed && !dragging && !cancelled && !resetting && page==3) {
            held=true;resetArmed=false;removeCallbacks(holdFrame);completedHold=true;invalidate()
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            onReset()
        }
    }
    override fun onAttachedToWindow(){
        super.onAttachedToWindow()
        PassengerArtworkCache.prepare(context,"clock-stack-v3",artworkPaths) {
            art.install(it);invalidate()
        }
    }
    init { isClickable=true;contentDescription="Clock and vehicle information. Swipe up or down to change page." }
    fun tick(){invalidate()}
    fun show(index:Int,animate:Boolean=true) {
        cancelHold()
        val next=index.coerceIn(0,3)
        if(next!=page){page=next;preferences.edit().putInt("clock_stack_page",page).apply()}
        animation?.cancel()
        val target=-page*426f
        if(!animate){offset=target;invalidate();return}
        animation=ValueAnimator.ofFloat(offset,target).apply {
            duration=300;interpolator=DecelerateInterpolator()
            addUpdateListener {offset=it.animatedValue as Float;invalidate()};start()
        }
    }
    override fun onDetachedFromWindow(){cancelHold();animation?.cancel();offset=-page*426f;super.onDetachedFromWindow()}
    override fun onWindowFocusChanged(hasWindowFocus:Boolean){super.onWindowFocusChanged(hasWindowFocus);if(!hasWindowFocus)cancelHold()}
    override fun onTouchEvent(e:MotionEvent):Boolean {
        when(e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelHold();animation?.cancel();downOffset=offset;downX=e.x;downY=e.y
                dragging=false;cancelled=false;held=false
                resetArmed=page==3 && abs(offset+page*426f)<0.5f && !resetting && telemetry.distance!=null && inReset(e.x,e.y)
                if(resetArmed){holdStarted=android.os.SystemClock.uptimeMillis();handler.postDelayed(hold,HOLD_MS);postOnAnimation(holdFrame)}
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val dx=e.x-downX;val dy=e.y-downY
                if(e.pointerCount>1){cancelled=true;cancelHold();show(page);return true}
                if(abs(dx)>slop || abs(dy)>slop){cancelHold()}
                if(!cancelled && !dragging && abs(dy)>slop && abs(dy)>abs(dx)*1.25f){
                    dragging=true;parent?.requestDisallowInterceptTouchEvent(true)
                }
                if(dragging){offset=(downOffset+dy*426f/height.coerceAtLeast(1)).coerceIn(-3*426f,0f);invalidate()}
            }
            MotionEvent.ACTION_UP -> {
                cancelHold()
                val dy=(e.y-downY)*426f/height.coerceAtLeast(1)
                val next=if(dragging && abs(dy)>45f)page+(if(dy<0)1 else -1) else page
                if(!held && !dragging && !cancelled)performClick()
                show(next);parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_CANCEL -> {cancelHold();cancelled=true;show(page);parent?.requestDisallowInterceptTouchEvent(false)}
        }
        return true
    }
    override fun performClick():Boolean {super.performClick();return true}
    private fun inReset(x:Float,y:Float):Boolean = x*428/width.coerceAtLeast(1) in 28f..378f && y*426/height.coerceAtLeast(1) in 352f..402f
    override fun onDraw(c:Canvas) {
        c.save();c.scale(width/428f,height/426f);c.clipRect(0f,0f,428f,426f)
        for(i in 0..3) {
            val y=i*426+offset
            if(y<=-426 || y>=426)continue
            c.save();c.translate(0f,y)
            if(i==0)clock(c) else drawPage(c,i)
            c.restore()
        }
        art.draw(c,"clock-stack-dots-$page-${if(light)"light"else "dark"}",RectF(398f,164f,410f,234f))
        paint.alpha=255;c.restore()
    }
    private fun drawPage(c:Canvas,index:Int) {
        val theme=if(light)"light"else "dark"
        // All icon shapes, static separators, tracks and button artwork are pre-baked PNGs.
        art.draw(c,"clock-stack-$index-$theme",RectF(0f,0f,428f,426f))
        fun text(value:String,x:Float,y:Float,size:Float,color:Int=ink,center:Boolean=false,strong:Boolean=false){
            paint.color=color;paint.alpha=255;paint.typeface=if(strong)bold else latin;paint.textSize=size
            paint.textAlign=if(center)Paint.Align.CENTER else Paint.Align.LEFT
            c.drawText(value,x,y,paint)
        }
        fun number(n:Double?,decimals:Int=0)=n?.let{String.format(Locale.US,"%.${decimals}f",it)} ?: "—"
        fun progress(value:Double?,y:Float){
            if(value==null)return
            val width=346f*(value/100).toFloat()
            if(width<=0f)return
            val cap=minOf(10f,width)
            val name="clock-stack-bar-${levelTone(value)}"
            art.draw(c,"$name-cap",RectF(30f,y,30f+cap,y+10f))
            if(width>cap){
                art.draw(c,"$name-body",RectF(30f+cap/2,y,30f+width-cap/2,y+10f))
                art.draw(c,"$name-cap",RectF(30f+width-cap,y,30f+width,y+10f))
            }
        }
        text(when(index){1->"Energy";2->"Engine";else->"Trip"},30f,48f,25f,strong=true)
        when(index) {
            1->{
                text(number(telemetry.battery)+if(telemetry.battery!=null)"%"else "",98f,145f,66f,strong=true)
                text("Battery",98f,179f,21f,muted);progress(telemetry.battery,205f)
                telemetry.batteryRange?.let{ text(number(it),339f,155f,28f,center=true,strong=true);text("km",339f,183f,18f,muted,center=true) }
                text(number(telemetry.fuel)+if(telemetry.fuel!=null)"%"else "",98f,305f,66f,strong=true)
                text("Fuel",98f,339f,21f,muted);progress(telemetry.fuel,365f)
                telemetry.fuelRange?.let{ text(number(it),339f,315f,28f,center=true,strong=true);text("km",339f,339f,18f,muted,center=true) }
            }
            2->{
                text(if(telemetry.rpm?.let { it < 50.0 } == true) "OFF" else number(telemetry.rpm),204f,195f,68f,center=true,strong=true)
                text("rpm",204f,227f,20f,muted,center=true)
                text("Instant fuel",204f,282f,21f,muted,center=true)
                text(number(telemetry.instantFuel,1),204f,346f,58f,center=true,strong=true)
                text("L/100 km",204f,378f,20f,muted,center=true)
            }
            3->{
                val distance=number(telemetry.distance,1)
                text(distance,204f,119f,if(distance.length>7)42f else 60f,center=true,strong=true)
                text("km",204f,147f,19f,muted,center=true)
                val time=telemetry.minutes?.toInt()?.let {String.format(Locale.US,"%02d:%02d",it/60,it%60)} ?: "—"
                text(time,204f,189f,26f,center=true)
                text("Average consumption",204f,238f,19f,muted,center=true)
                text(number(telemetry.averageFuel,1),113f,296f,40f,center=true,strong=true)
                text("L/100 km",113f,323f,17f,muted,center=true)
                text(number(telemetry.averagePower,1),299f,296f,40f,center=true,strong=true)
                text("kWh/100 km",299f,323f,17f,muted,center=true)
                val label=if(telemetry.distance==null)"Trip unavailable"else if(resetting)"Resetting…"else "Hold to reset"
                text(label,203f,385f,21f,center=true)
                val progress=if(resetting)1f else holdProgress
                if(progress>0f){
                    // Reveal a pre-baked blue circle, clipped to the fixed button. No shader or runtime artwork.
                    val radius=180f*progress
                    c.save();c.clipPath(resetClip)
                    art.draw(c,"clock-stack-hold-fill",RectF(203f-radius,377f-radius,203f+radius,377f+radius),alpha=(255*(0.18f+0.82f*progress)).toInt())
                    filledClip.reset();filledClip.addCircle(203f,377f,radius*63f/64f,Path.Direction.CW)
                    c.clipPath(filledClip);text(label,203f,385f,21f,Color.WHITE,center=true);c.restore()
                }
            }
        }
    }
}
