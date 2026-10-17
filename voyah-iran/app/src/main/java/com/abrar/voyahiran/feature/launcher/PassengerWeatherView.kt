package com.abrar.voyahiran.feature.launcher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.text.*
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Shares the central display's cached weather source and icon artwork; never polls on its own. */
internal class PassengerWeatherView(context:Context):View(context) {
    var light=false
        set(value){if(field!=value){field=value;invalidate()}}
    private var state=LauncherWeatherState()
    private var previous=state
    private var blend=1f
    private var fade:ValueAnimator?=null
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint=TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val font=Typeface.createFromAsset(context.assets,"passenger/fonts/Roboto-Regular.ttf")
    private val persian=Typeface.createFromAsset(context.assets,"passenger/fonts/iransansx_regular.ttf")
    private val icons=HashMap<Int,Bitmap>()
    init {
        val unavailable=LauncherWeatherCode.icon(-1)
        icons[unavailable]=LauncherWeatherArtwork.decode(resources,unavailable)
        contentDescription="Weather unavailable"
    }
    private fun iconId(code:Int,day:Boolean)=LauncherWeatherCode.icon(code,day)
    fun update(next:LauncherWeatherState){
        if(next==state)return
        (listOf(next.code to next.day)+next.hours.take(5).map{it.code to it.day}).forEach{(code,day)->
            val id=iconId(code,day);icons.getOrPut(id){LauncherWeatherArtwork.decode(resources,id)}
        }
        fade?.cancel();previous=state;state=next
        contentDescription=next.city+", "+(next.temperature?.takeIf{it.isFinite()}?.let{"${it.roundToInt()} degrees"}?:"Weather unavailable")+if(next.notice.isNotBlank())", ${next.notice}"else ""
        if(isAttachedToWindow&&isShown&&ValueAnimator.areAnimatorsEnabled()){
            fade=ValueAnimator.ofFloat(0f,1f).apply{duration=300;addUpdateListener{blend=it.animatedValue as Float;invalidate()};start()}
        }else blend=1f
        invalidate()
    }
    override fun onDetachedFromWindow(){fade?.cancel();blend=1f;super.onDetachedFromWindow()}
    private val secondary get()=if(light)0xff71839e.toInt()else 0xffc5d0e8.toInt()
    private val foreground get()=if(light)0xff252f3c.toInt()else Color.WHITE
    private fun text(c:Canvas,s:String,x:Float,y:Float,size:Float,color:Int){
        textPaint.typeface=font;textPaint.textSize=size;textPaint.color=color;textPaint.textAlign=Paint.Align.CENTER;c.drawText(s,x,y,textPaint)
    }
    private fun artwork(c:Canvas,code:Int,day:Boolean,cx:Float,cy:Float,size:Float){
        val b=icons[iconId(code,day)]?:return
        paint.shader=null;paint.color=Color.WHITE;c.drawBitmap(b,null,RectF(cx-size/2,cy-size/2,cx+size/2,cy+size/2),paint)
    }
    override fun onDraw(c:Canvas){
        super.onDraw(c);c.save();c.scale(width/556f,height/154f)
        c.clipPath(Path().apply{addRoundRect(RectF(0f,0f,556f,154f),16f,16f,Path.Direction.CW)})
        paint.shader=LinearGradient(0f,0f,556f,154f,if(light)intArrayOf(0xfff7faff.toInt(),0xffeef4fc.toInt())else intArrayOf(0xff35456e.toInt(),0xff1b233a.toInt()),null,Shader.TileMode.CLAMP)
        c.drawRect(0f,0f,556f,154f,paint);paint.shader=null
        c.save();c.translate(278f,160f);c.scale(1f,.12f);paint.shader=RadialGradient(0f,0f,417f,intArrayOf(if(light)0x356ba5ef else 0x706278b6,0x006278b6),null,Shader.TileMode.CLAMP);c.drawCircle(0f,0f,417f,paint);paint.shader=null;c.restore()
        if(blend<1f){val layer=c.saveLayerAlpha(0f,0f,556f,154f,((1-blend)*255).toInt());contents(c,previous);c.restoreToCount(layer)}
        val layer=c.saveLayerAlpha(0f,0f,556f,154f,(blend*255).toInt());contents(c,state);c.restoreToCount(layer)
        c.restore()
    }
    private fun contents(c:Canvas,s:LauncherWeatherState){
        if(s.code<0 && s.temperature?.isFinite()!=true && s.hours.isEmpty()){
            artwork(c,-1,true,278f,60f,140f)
            text(c,"Weather unknown",278f,140f,18f,secondary)
            return
        }
        artwork(c,s.code,s.day,87.5f,51f,76f)
        city(c,s)
        if(s.notice.isNotBlank()){
            textPaint.typeface=font;textPaint.textSize=11f
            text(c,TextUtils.ellipsize(s.notice,textPaint,158f,TextUtils.TruncateAt.END).toString(),90f,146f,11f,secondary)
        }
        val format=SimpleDateFormat("HH:mm",Locale.US)
        for(i in 0..4){val hour=s.hours.getOrNull(i);val x=216f+i*73f
            text(c,hour?.let{format.format(Date(it.time))}?:"—",x,35f,17f,secondary)
            if(hour!=null)artwork(c,hour.code,hour.day,x,71f,52f)
            text(c,hour?.temperature?.takeIf{it.isFinite()}?.let{"${it.roundToInt()}°"}?:"—",x,122f,23f,foreground)
        }
    }
    private fun city(c:Canvas,s:LauncherWeatherState){
        val name=s.city.ifBlank{"Weather"};val suffix=" · "+(s.temperature?.takeIf{it.isFinite()}?.let{"${it.roundToInt()}°"}?:"—")
        textPaint.typeface=if(name.any{it in '\u0600'..'\u06ff'})persian else font
        textPaint.textSize=23f;textPaint.color=secondary;textPaint.textAlign=Paint.Align.LEFT
        while(textPaint.textSize>17f&&textPaint.measureText(name+suffix)>162f)textPaint.textSize-=1f
        val suffixWidth=textPaint.measureText(suffix)
        val budget=(158f-suffixWidth).coerceAtLeast(1f)
        var title=name
        if(textPaint.measureText(title)>budget){
            while(title.isNotEmpty()&&textPaint.measureText(title+"…")>budget)title=title.substring(0,title.offsetByCodePoints(title.length,-1))
            title+="…"
        }
        val titleWidth=kotlin.math.ceil(textPaint.measureText(title)).toInt()+2
        val x=9f+(162-titleWidth-suffixWidth)/2
        val layout=StaticLayout.Builder.obtain(title,0,title.length,textPaint,titleWidth).setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).setMaxLines(1).setEllipsize(TextUtils.TruncateAt.END).setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR).build()
        c.save();c.clipRect(9f,96f,171f,132f);c.translate(x,125f-layout.getLineBaseline(0));layout.draw(c);c.restore()
        c.drawText(suffix,x+titleWidth,125f,textPaint)
    }
}
