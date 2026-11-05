package com.abrar.voyahiran.feature.launcher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

/** Fixed raster artwork with independently paged forecast content. */
internal class LauncherWeatherStack(context:Context):View(context) {
    private val prefs=context.getSharedPreferences("launcher",0)
    var page=prefs.getInt("weather_stack_page",0).coerceIn(0,2);private set
    var light=false
        set(value){if(field!=value){field=value;invalidate()}}
    var state=LauncherWeatherState()
        set(value){if(field!=value){motion.updated();field=value;refreshLabels();invalidate()}}
    internal var now:()->Long={System.currentTimeMillis()}
    private val art=StaticLauncherArtwork(this)
    private val bitmaps=LauncherBitmapLoader(resources){invalidate()}
    private val bitmapPaint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    internal val motion=LauncherMotion(this)
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val normal=Typeface.create("sans-serif",Typeface.NORMAL)
    private val bold=Typeface.create("sans-serif",Typeface.BOLD)
    private val ink get()=if(light)0xff252f3c.toInt() else Color.WHITE
    private val muted get()=if(light)0xff71839e.toInt() else 0xffbfc9de.toInt()
    private val blue get()=if(light)0xff1686ff.toInt() else 0xff70beff.toInt()
    private var hours=emptyList<LauncherWeatherHour>()
    private var days=emptyList<LauncherWeatherDay>()
    private var hourLabels=emptyList<String>()
    private var dayLabels=emptyList<String>()
    private var minute=Long.MIN_VALUE
    private var offset=-page*218f
    private var downOffset=offset
    private var downX=0f;private var downY=0f
    private var dragging=false;private var cancelled=false
    private val slop=ViewConfiguration.get(context).scaledTouchSlop
    private var animation:ValueAnimator?=null
    private val bounds=RectF()
    private val clock=object:Runnable {override fun run(){tick();postDelayed(this,60_000L-now()%60_000L)}}
    init {isClickable=true;contentDescription="Weather. Swipe up or down for hourly and daily forecasts."}
    override fun onAttachedToWindow(){
        super.onAttachedToWindow()
        bitmaps.start()
        PassengerArtworkCache.prepare(context,"weather-stack-v1",WeatherStackPieces.paths){art.install(it);invalidate()}
        removeCallbacks(clock);post(clock)
    }
    override fun onDetachedFromWindow(){bitmaps.stop();removeCallbacks(clock);animation?.cancel();offset=-page*218f;super.onDetachedFromWindow()}
    fun tick(){if(minute!=now()/60_000L){refreshLabels();if(isShown)invalidate()}}
    private fun refreshLabels(){
        val time=now();minute=time/60_000L
        val zone=TimeZone.getTimeZone(state.timezone)
        val hourFormat=SimpleDateFormat("HH:mm",Locale.US).apply{timeZone=zone}
        val dateFormat=SimpleDateFormat("yyyy-MM-dd",Locale.US).apply{timeZone=zone}
        val dayFormat=SimpleDateFormat("EEE",Locale.US).apply{timeZone=zone}
        val today=dateFormat.format(Date(time))
        hours=state.hours.filter{it.time+3_600_000L>time}.take(4)
        days=state.days.filter{dateFormat.format(Date(it.time))>=today}.take(3)
        hourLabels=hours.map{hourFormat.format(Date(it.time))}
        dayLabels=days.map{dayFormat.format(Date(it.time))}
    }
    fun show(index:Int,animate:Boolean=true){
        val next=index.coerceIn(0,2)
        if(page!=next){page=next;prefs.edit().putInt("weather_stack_page",page).apply()}
        animation?.cancel()
        if(!animate){offset=-page*218f;invalidate();return}
        animation=ValueAnimator.ofFloat(offset,-page*218f).apply{
            duration=300;interpolator=DecelerateInterpolator()
            addUpdateListener{offset=it.animatedValue as Float;invalidate()};start()
        }
    }
    override fun onTouchEvent(e:MotionEvent):Boolean{
        when(e.actionMasked){
            MotionEvent.ACTION_DOWN->{animation?.cancel();downOffset=offset;downX=e.x;downY=e.y;dragging=false;cancelled=false}
            MotionEvent.ACTION_POINTER_DOWN->{cancelled=true;show(page)}
            MotionEvent.ACTION_MOVE->{
                val dx=e.x-downX;val dy=e.y-downY
                if(!cancelled && !dragging && abs(dy)>slop && abs(dy)>abs(dx)*1.25f){dragging=true;parent?.requestDisallowInterceptTouchEvent(true)}
                if(dragging && !cancelled){offset=(downOffset+dy*218f/height.coerceAtLeast(1)).coerceIn(-436f,0f);invalidate()}
            }
            MotionEvent.ACTION_UP->{
                val dy=(e.y-downY)*218f/height.coerceAtLeast(1)
                show(if(!cancelled && dragging && abs(dy)>30f)page+if(dy<0)1 else -1 else page)
                if(!dragging && !cancelled)performClick()
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_CANCEL->{show(page);parent?.requestDisallowInterceptTouchEvent(false)}
        };return true
    }
    override fun performClick():Boolean{super.performClick();return true}
    private fun text(c:Canvas,value:String,x:Float,y:Float,size:Float,color:Int=ink,strong:Boolean=false,align:Paint.Align=Paint.Align.LEFT){
        paint.color=color;paint.textSize=size;paint.typeface=if(strong)bold else normal;paint.textAlign=align
        c.drawText(value,x,y,paint)
    }
    private fun image(c:Canvas,name:String,x:Float,y:Float,w:Float,h:Float){bounds.set(x,y,x+w,y+h);art.draw(c,name,bounds)}
    private fun icon(c:Canvas,code:Int,day:Boolean,size:Int,x:Float,y:Float){
        val bitmap=bitmaps.get(LauncherWeatherCode.icon(code,day),weather=true)
        bounds.set(x,y,x+size,y+size)
        c.drawBitmap(bitmap,null,bounds,bitmapPaint)
    }
    private fun degree(value:Double?)=value?.let{String.format(Locale.US,"%.0f°",it)} ?: "—"
    private fun rain(c:Canvas,value:Int?,x:Float,y:Float){
        image(c,"weather-stack-drop",x,y-12,9f,13f)
        text(c,value?.let{"$it%"} ?: "—",x+15,y,16f,blue)
    }
    override fun onDraw(c:Canvas){
        c.save();c.scale(width/428f,height/218f);c.clipRect(0f,0f,428f,218f)
        for(index in 0..2){
            val y=index*218+offset;if(y<=-218 || y>=218)continue
            c.save();c.translate(0f,y)
            if(index==0)drawNow(c) else drawForecast(c,index)
            text(c,listOf("Now","Next hours","Next days")[index],396f,31f,17f,muted,align=Paint.Align.RIGHT)
            val source="Open-Meteo"+if(state.notice.isNotEmpty() && state.temperature!=null)" · ${state.notice}" else ""
            text(c,source,24f,204f,12f,muted)
            c.restore()
        }
        for(i in 0..2)image(c,if(page==i)"clock-stack-dot-active" else "clock-stack-dot-${if(light)"light" else "dark"}",407f,95f+i*16,8f,8f)
        c.restore()
    }
    private fun drawNow(c:Canvas){
        icon(c,state.code,state.day,180,210f,20f)
        c.save();c.clipRect(24f,35f,251f,187f)
        fun fading(key:String,value:String,y:Float,size:Float,color:Int=ink){
            motion.text(key,value,c,RectF(24f,y-size-8,251f,y+8)){text(c,it,56f,y,size,color)}
        }
        fading("city",state.city,57f,23f)
        fading("temperature",degree(state.temperature),106f,51f)
        fading("description",if(state.temperature==null)state.notice else LauncherWeatherCode.description(state.code),138f,19f,muted)
        fading("range",if(state.temperature!=null)"${degree(state.high)} / ${degree(state.low)}" else "",166f,18f,muted)
        c.restore()
    }
    private fun drawForecast(c:Canvas,index:Int){
        c.save();c.clipRect(24f,8f,238f,40f);text(c,state.city,28f,31f,23f);c.restore()
        val line="clock-stack-separator-${if(light)"light" else "dark"}"
        if(index==1){
            for(i in 0..3){
                val x=64f+i*92f;val hour=hours.getOrNull(i)
                text(c,hourLabels.getOrNull(i) ?: "—",x,64f,16f,muted,align=Paint.Align.CENTER)
                icon(c,hour?.code ?: -1,hour?.day ?: true,60,x-30f,68f)
                text(c,degree(hour?.temperature),x,154f,31f,strong=true,align=Paint.Align.CENTER)
                rain(c,hour?.precipitation,x-23f,177f)
                if(i<3)image(c,line,x+46f,51f,1f,128f)
            }
        }else{
            for(i in 0..2){
                val day=days.getOrNull(i);val y=70f+i*47f
                text(c,dayLabels.getOrNull(i) ?: "—",32f,y,20f)
                icon(c,day?.code ?: -1,true,50,97f,y-35f)
                text(c,degree(day?.high),169f,y+1f,27f,strong=true)
                text(c,degree(day?.low),231f,y+1f,22f,muted,strong=true)
                rain(c,day?.precipitation,303f,y)
                if(i<2)image(c,line,28f,y+17f,354f,1f)
            }
        }
    }
}
