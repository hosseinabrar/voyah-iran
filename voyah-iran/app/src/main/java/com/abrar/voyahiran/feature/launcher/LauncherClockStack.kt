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
    var page=preferences.getInt("clock_stack_page",0).coerceIn(0,2); private set
    var telemetry=LauncherTelemetry()
        set(value){
            val changed=when(page){
                1 -> field.battery!=value.battery || field.fuel!=value.fuel || field.batteryRange!=value.batteryRange || field.fuelRange!=value.fuelRange
                2 -> field.distance!=value.distance || field.minutes!=value.minutes || field.averageFuel!=value.averageFuel || field.averagePower!=value.averagePower
                else -> false
            }
            field=value
            if(changed)invalidate()
        }
    var light=false
    private val art=StaticLauncherArtwork(this)
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val latin=Typeface.create("sans-serif",Typeface.NORMAL)
    private val bold=Typeface.create("sans-serif",Typeface.BOLD)
    private val ink get()=if(light)0xff252f3c.toInt() else Color.WHITE
    private val muted get()=if(light)0xff71839e.toInt() else 0xffbfc9de.toInt()
    companion object {
        fun levelTone(value:Double)=when { value<10 -> "red"; value<20 -> "orange"; else -> "blue" }
        val artworkPaths=ClockStackPieces.paths +
            listOf("blue","orange","red").flatMap { tone -> listOf("cap","body").map { "passenger/baked/clock-stack-bar-$tone-$it.png" } }
    }
    private var offset=-page*426f
    private var downOffset=offset
    private var downX=0f; private var downY=0f
    private var dragging=false;private var cancelled=false
    private val slop=ViewConfiguration.get(context).scaledTouchSlop
    private var animation:ValueAnimator?=null
    override fun onAttachedToWindow(){
        super.onAttachedToWindow()
        PassengerArtworkCache.prepare(context,"clock-stack-v5",artworkPaths) {
            art.install(it);invalidate()
        }
    }
    init { isClickable=true;contentDescription="Clock and vehicle information. Swipe up or down to change page." }
    fun tick(){invalidate()}
    fun show(index:Int,animate:Boolean=true) {
        val next=index.coerceIn(0,2)
        if(next!=page){page=next;preferences.edit().putInt("clock_stack_page",page).apply()}
        animation?.cancel()
        val target=-page*426f
        if(!animate){offset=target;invalidate();return}
        animation=ValueAnimator.ofFloat(offset,target).apply {
            duration=300;interpolator=DecelerateInterpolator()
            addUpdateListener {offset=it.animatedValue as Float;invalidate()};start()
        }
    }
    override fun onDetachedFromWindow(){animation?.cancel();offset=-page*426f;super.onDetachedFromWindow()}
    override fun onTouchEvent(e:MotionEvent):Boolean {
        when(e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                animation?.cancel();downOffset=offset;downX=e.x;downY=e.y
                dragging=false;cancelled=false
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val dx=e.x-downX;val dy=e.y-downY
                if(e.pointerCount>1){cancelled=true;show(page);return true}
                if(!cancelled && !dragging && abs(dy)>slop && abs(dy)>abs(dx)*1.25f){
                    dragging=true;parent?.requestDisallowInterceptTouchEvent(true)
                }
                if(dragging){offset=(downOffset+dy*426f/height.coerceAtLeast(1)).coerceIn(-2*426f,0f);invalidate()}
            }
            MotionEvent.ACTION_UP -> {
                val dy=(e.y-downY)*426f/height.coerceAtLeast(1)
                val next=if(dragging && abs(dy)>45f)page+(if(dy<0)1 else -1) else page
                if(!dragging && !cancelled)performClick()
                show(next);parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_CANCEL -> {cancelled=true;show(page);parent?.requestDisallowInterceptTouchEvent(false)}
        }
        return true
    }
    override fun performClick():Boolean {super.performClick();return true}
    override fun onDraw(c:Canvas) {
        c.save();c.scale(width/428f,height/426f);c.clipRect(0f,0f,428f,426f)
        for(i in 0..2) {
            val y=i*426+offset
            if(y<=-426 || y>=426)continue
            c.save();c.translate(0f,y)
            if(i==0)clock(c) else drawPage(c,i)
            c.restore()
        }
        drawPieces(c,10+page)
        paint.alpha=255;c.restore()
    }
    private val pieceBounds=RectF()
    private fun drawPieces(c:Canvas,index:Int){
        ClockStackPieces.pages[Pair(if(light)"light" else "dark",index)]?.forEach {
            pieceBounds.set(it.x,it.y,it.x+it.width,it.y+it.height)
            art.draw(c,it.name,pieceBounds)
        }
    }
    private fun drawPage(c:Canvas,index:Int) {
        // Tight reusable PNG pieces, positioned without resizing.
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
        text(if(index==1)"Energy"else "Since start",30f,48f,25f,strong=true)
        when(index) {
            1->{
                drawPieces(c,index)
                text(number(telemetry.battery)+if(telemetry.battery!=null)"%"else "",98f,145f,66f,strong=true)
                text("Battery",98f,179f,21f,muted);progress(telemetry.battery,205f)
                telemetry.batteryRange?.let{ text(number(it),339f,155f,28f,center=true,strong=true);text("km",339f,183f,18f,muted,center=true) }
                text(number(telemetry.fuel)+if(telemetry.fuel!=null)"%"else "",98f,305f,66f,strong=true)
                text("Fuel",98f,339f,21f,muted);progress(telemetry.fuel,365f)
                telemetry.fuelRange?.let{ text(number(it),339f,315f,28f,center=true,strong=true);text("km",339f,339f,18f,muted,center=true) }
            }
            2->{
                // Center the complete information group below the fixed heading.
                c.save();c.translate(0f,25f)
                drawPieces(c,index)
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
                c.restore()

            }
        }
    }
}
