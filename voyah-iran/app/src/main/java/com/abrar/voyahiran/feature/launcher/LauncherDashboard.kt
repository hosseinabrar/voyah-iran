package com.abrar.voyahiran.feature.launcher

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import com.abrar.voyahiran.R
import java.util.Calendar
import java.util.Locale

/** Layout coordinates match the approved 1775-pixel OEM safe area; no OEM bars are drawn. */
class LauncherDashboard(context: Context) : View(context) {
    var lightTheme = false
        set(value) { if(field!=value) {field=value;invalidate()} }
    private val foreground get() = if(lightTheme) 0xff252f3c.toInt() else Color.WHITE
    private val secondary get() = if(lightTheme) 0xff71839e.toInt() else 0xffbfc9de.toInt()
    private val accentTop get() = if(lightTheme) 0xff719eff.toInt() else 0xff617bbb.toInt()
    private val accentBottom get() = if(lightTheme) 0xff3868e5.toInt() else 0xff354b86.toInt()
    private val darkIconFilter = PorterDuffColorFilter(0xff293543.toInt(),PorterDuff.Mode.SRC_IN)
    var media = LauncherMediaState()
        set(value) { if(field==value)return;field = value; invalidate() }
    private data class MediaTextKey(val title:String,val artist:String,val radio:Boolean,val light:Boolean)
    private var mediaTextKey:MediaTextKey?=null
    private var mediaTextPicture:Picture?=null
    var weather = LauncherWeatherState()
        set(value) { field=value; invalidate() }
    private val moonBitmap by lazy { BitmapFactory.decodeResource(resources,R.drawable.launcher_weather_moon,BitmapFactory.Options().apply {inSampleSize=4}) }
    private val nightCloudBitmap by lazy { BitmapFactory.decodeResource(resources,R.drawable.launcher_weather_overcast) }
    private var weatherIconId = 0
    private var weatherBitmap: Bitmap? = null
    var mapMessage = LauncherMapPreview.LOADING
        set(value) { field = value; invalidate() }
    var onMap: () -> Unit = {}
    var onShowMap: () -> Unit = {}
    private val mapLight by lazy { BitmapFactory.decodeResource(resources,R.drawable.launcher_map_default_light) }
    private val mapDefault=BitmapFactory.decodeResource(resources,R.drawable.launcher_map_default)
    var onMedia: (Int) -> Unit = {}
    var onOpenMusic: () -> Unit = {}
    var onSeek: (Long) -> Unit = {}
    private val loadingDark = SweepGradient(435f,500f,
        intArrayOf(0x00354b78,0xff354b78.toInt(),0xff6385bf.toInt(),0xffa9c6ed.toInt(),0x00a9c6ed),
        floatArrayOf(0f,.28f,.60f,.8333f,1f))
    private val loadingLight = SweepGradient(435f,500f,
        intArrayOf(0x00243860,0xff47628f.toInt(),0xff304b79.toInt(),0xff203656.toInt(),0x00203656),
        floatArrayOf(0f,.28f,.60f,.8333f,1f))
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val defaultCover = BitmapFactory.decodeResource(resources, R.drawable.launcher_album_default)
    private val icons = listOf(R.drawable.launcher_previous, R.drawable.launcher_play, R.drawable.launcher_pause, R.drawable.launcher_next).map { BitmapFactory.decodeResource(resources, it) }
    private val sourceIcons = mapOf(
        "LOCAL_RADIO" to R.drawable.launcher_source_radio,
        "BT_MUSIC" to R.drawable.launcher_source_bluetooth,
        "SYSTEM_BT_MUSIC" to R.drawable.launcher_source_bluetooth,
        "USB_MUSIC" to R.drawable.launcher_source_usb
    ).mapValues { BitmapFactory.decodeResource(resources, it.value) }
    private val persian = resources.getFont(com.abrar.raha.feedback.R.font.iransansx_regular)
    private val persianBold = resources.getFont(com.abrar.raha.feedback.R.font.iransansx_bold)
    private val latin = Typeface.create("sans-serif",Typeface.NORMAL)
    private val latinBold = Typeface.create("sans-serif",Typeface.BOLD)
    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        c.save(); c.scale(width / 1775f, height / 660f)
        fun card(x: Float, y: Float, w: Float, h: Float) {
            paint.shader = LinearGradient(x,y,x+w,y+h,if(lightTheme) intArrayOf(0xfffcfdff.toInt(),0xffeef4fc.toInt()) else intArrayOf(Color.rgb(60,75,119),Color.rgb(27,35,58)),null,Shader.TileMode.CLAMP)
            c.drawRoundRect(x,y,x+w,y+h,16f,16f,paint); paint.shader = null
            c.save(); c.clipPath(Path().apply { addRoundRect(RectF(x,y,x+w,y+h),16f,16f,Path.Direction.CW) })
            paint.shader = RadialGradient(x+w/2,y+h+7,w*.5f,intArrayOf(if(lightTheme) 0x559ec5fc else 0x776278b6,Color.TRANSPARENT),null,Shader.TileMode.CLAMP)
            c.save(); c.scale(1f,.13f,x+w/2,y+h); c.drawCircle(x+w/2,y+h,w*.55f,paint); c.restore(); paint.shader=null;c.restore()
        }
        card(887f,0f,428f,660f); card(1331f,0f,428f,426f); card(1331f,442f,428f,218f)
        if(mapMessage.isNotEmpty()) {
            c.save();c.clipPath(Path().apply {addRoundRect(RectF(0f,0f,871f,660f),16f,16f,Path.Direction.CW)})
            val mapDefault=if(lightTheme)mapLight else mapDefault
            val scale=maxOf(871f/mapDefault.width,660f/mapDefault.height)
            val dw=mapDefault.width*scale;val dh=mapDefault.height*scale
            paint.color=Color.WHITE;c.drawBitmap(mapDefault,null,RectF((871f-dw)/2,(660f-dh)/2,(871f+dw)/2,(660f+dh)/2),paint);c.restore()
        }
        c.save();c.clipRect(0f,0f,871f,660f)
        paint.shader=RadialGradient(435f,667f,435f,intArrayOf(if(lightTheme) 0x449ec5fc else 0x556278b6,Color.TRANSPARENT),null,Shader.TileMode.CLAMP)
        c.scale(1f,.13f,435f,660f);c.drawCircle(435f,660f,450f,paint);paint.shader=null;c.restore()
        fun text(t: String,x:Float,y:Float,size:Float,color:Int=foreground,center:Boolean=false,bold:Boolean=false,fa:Boolean=false,target:Canvas=c) {
            paint.color=color;paint.textSize=size
            val usePersian=fa || t.any { it in '\u0600'..'\u06ff' || it in '\u0750'..'\u077f' || it in '\u08a0'..'\u08ff' || it in '\ufb50'..'\ufdff' || it in '\ufe70'..'\ufeff' }
            paint.typeface=if(usePersian) {if(bold)persianBold else persian} else {if(bold)latinBold else latin}
            paint.textAlign=if(center)Paint.Align.CENTER else Paint.Align.LEFT
            val max=if(x < 1331) 372f else 398f
            var value=t
            while(value.length>1 && paint.measureText(value)>max) value=value.dropLast(1)
            if(value != t) value=value.dropLast(1)+"…"
            target.drawText(value,x,y,paint)
        }
        if(mapMessage == LauncherMapPreview.LOADING) {
            // Large, thick comet ring: a rounded bright head and a fading navy tail.
            c.save()
            val angle=(android.os.SystemClock.uptimeMillis()%1200L)*.3f
            c.rotate(angle,435f,500f)
            paint.color=Color.WHITE; paint.style=Paint.Style.STROKE; paint.strokeWidth=18f
            paint.strokeCap=Paint.Cap.BUTT; paint.shader=if(lightTheme)loadingLight else loadingDark
            c.drawArc(388f,453f,482f,547f,0f,300f,false,paint)
            paint.shader=null; paint.style=Paint.Style.FILL
            paint.color=if(lightTheme)0xff203656.toInt() else 0xffa9c6ed.toInt()
            c.drawCircle(458.5f,459.297f,9f,paint)
            c.restore()
            if(isShown) postInvalidateOnAnimation()
        } else if(mapMessage.isNotEmpty()) {
            paint.shader=LinearGradient(285f,465f,585f,535f,accentTop,accentBottom,Shader.TileMode.CLAMP)
            c.drawRoundRect(285f,465f,585f,535f,14f,14f,paint);paint.shader=null
            text("Show Map",435f,510f,27f,Color.WHITE,center=true)
        }
        val oemIcon = sourceIcons[media.oemSource]
        val hasSourceIcon = oemIcon != null || media.sourceIcon != null
        if (oemIcon != null) {
            paint.color = Color.WHITE
            c.drawBitmap(oemIcon, null, RectF(911f, 23f, 935f, 47f), paint)
        } else media.sourceIcon?.let { icon ->
            icon.setBounds(911, 23, 935, 47); icon.draw(c)
        }
        // Reserve space for the source shortcut even with long application names.
        c.save(); c.clipRect(911f, 0f, 1255f, 65f)
        text(media.source, if(hasSourceIcon) 951f else 911f,45f,23f)
        c.restore()
        paint.color = foreground; paint.strokeWidth = 2.5f
        c.drawLine(1280f, 27f, 1288f, 35f, paint)
        c.drawLine(1288f, 35f, 1280f, 43f, paint)
        if(!media.radio) {
            val cover = media.cover ?: defaultCover
            val rect=RectF(941f,78f,1261f,398f)
            c.save();c.clipPath(Path().apply{addRoundRect(rect,12f,12f,Path.Direction.CW)})
            paint.color=Color.WHITE
            if(lightTheme && media.cover==null) {
                paint.color=0xffdce8f7.toInt();c.drawRect(rect,paint)
                paint.color=0xffeaf1fa.toInt();c.drawCircle(1101f,238f,113f,paint)
                paint.color=0xffd3e2f3.toInt();c.drawCircle(1101f,238f,38f,paint)
            } else c.drawBitmap(cover,null,rect,paint)
            c.restore()
        }
        // Replay retained text drawing commands on progress/clock frames. Re-measure and
        // record only when the labels or their presentation actually change.
        val textKey=MediaTextKey(media.title,media.artist,media.radio,lightTheme)
        if(mediaTextKey!=textKey) {
            val picture=Picture()
            val labels=picture.beginRecording(1775,660)
            text(media.title,1101f,if(media.radio)255f else 437f,if(media.radio)48f else 32f,center=true,bold=true,target=labels)
            text(media.artist,1101f,if(media.radio)310f else 465f,if(media.radio)26f else 23f,secondary,center=true,target=labels)
            picture.endRecording()
            mediaTextPicture=picture;mediaTextKey=textKey
        }
        mediaTextPicture?.let {c.drawPicture(it)}
        if(!media.radio) {
            paint.color=if(lightTheme) 0xffcdd9e8.toInt() else if(media.canSeek) 0xff7c879f.toInt() else 0xff4e5970.toInt();paint.strokeWidth=4f;c.drawLine(911f,494f,1291f,494f,paint)
            if(media.duration>0) { paint.color=if(lightTheme) 0xff668cce.toInt() else if(media.canSeek) Color.WHITE else 0xff8d97aa.toInt();c.drawLine(911f,494f,911f+380f*(media.position.toFloat()/media.duration).coerceIn(0f,1f),494f,paint) }
            fun time(ms:Long)=String.format(Locale.US,"%d:%02d",ms/60000,ms/1000%60)
            text(time(media.position),911f,522f,17f);text(time(media.duration),1250f,522f,17f)
        }
        paint.shader=LinearGradient(1060f,549f,1140f,629f,accentTop,accentBottom,Shader.TileMode.CLAMP)
        c.drawCircle(1101f,580f,38f,paint);paint.shader=null
        listOf(987f to icons[0],1101f to icons[if(media.playing)2 else 1],1215f to icons[3]).forEach { (x,b)->
            paint.colorFilter=if(lightTheme && x!=1101f)darkIconFilter else null
            c.drawBitmap(b,null,RectF(x-18,562f,x+18,598f),paint)
        }
        paint.colorFilter=null
        val now=Calendar.getInstance();val cx=1545f;val cy=177f
        if(lightTheme) {
            paint.shader=RadialGradient(cx,cy+4,135f,intArrayOf(0x00344358,0x00344358,0x22344358,Color.TRANSPARENT),floatArrayOf(0f,.86f,.93f,1f),Shader.TileMode.CLAMP)
            c.drawCircle(cx,cy+4,135f,paint);paint.shader=null
            paint.shader=LinearGradient(cx-100,cy-100,cx+100,cy+124,0xffffffff.toInt(),0xffedf0f4.toInt(),Shader.TileMode.CLAMP)
        } else paint.color=0xff252726.toInt()
        c.drawCircle(cx,cy,124f,paint);paint.shader=null
        for(i in 0..59) { c.save();c.rotate(i*6f,cx,cy);paint.color=if(lightTheme)foreground else 0xffe6eaf1.toInt();paint.strokeWidth=if(i%5==0)2f else 1f;c.drawLine(cx,cy-116,cx,cy-if(i%5==0)107 else 112,paint);c.restore() }
        fun hand(degrees:Float,length:Float,stroke:Float) {c.save();c.rotate(degrees,cx,cy);paint.color=if(lightTheme)foreground else 0xffedf3ff.toInt();paint.strokeWidth=stroke;paint.strokeCap=Paint.Cap.ROUND;c.drawLine(cx,cy,cx,cy-length,paint);paint.strokeCap=Paint.Cap.BUTT;c.restore()}
        hand((now.get(Calendar.HOUR)%12)*30f+now.get(Calendar.MINUTE)*.5f,65f,5f)
        hand(now.get(Calendar.MINUTE)*6f+now.get(Calendar.SECOND)*.1f,84f,4f)
        paint.color=if(lightTheme)foreground else 0xffdfeaff.toInt();c.drawCircle(cx,cy,7f,paint)
        text(LauncherClock.persianDate(now.timeInMillis),cx,349f,26f,center=true,fa=true)
        text(LauncherClock.gregorianDate(now.timeInMillis),cx,387f,17f,secondary,center=true)
        fun degree(value: Double?) = value?.let { String.format(Locale.US,"%.0f°",it) } ?: "—"
        c.save();c.clipRect(1355f,455f,1588f,640f)
        text(weather.city,1387f,494f,23f)
        text(degree(weather.temperature),1387f,548f,51f)
        text(if(weather.temperature==null) weather.notice else LauncherWeatherCode.description(weather.code),1387f,580f,19f,secondary)
        if(weather.temperature!=null) text(degree(weather.high)+" / "+degree(weather.low),1387f,608f,18f,secondary)
        c.restore()
        if(weather.temperature != null) {
            if (!weather.day && weather.code in 0..2) {
                paint.color=Color.WHITE
                val bounds=if(weather.code==2) RectF(1549f,459f,1699f,609f) else RectF(1550f,462f,1730f,642f)
                c.drawBitmap(moonBitmap,null,bounds,paint)
                if(weather.code==2) c.drawBitmap(nightCloudBitmap,null,RectF(1573f,519f,1718f,647f),paint)
            } else {
                val id=LauncherWeatherCode.icon(if(!weather.day && weather.code in 80..82) 63 else weather.code)
                if(id!=weatherIconId) { weatherIconId=id;weatherBitmap=BitmapFactory.decodeResource(resources,id) }
                weatherBitmap?.let { paint.color=Color.WHITE;c.drawBitmap(it,null,RectF(1577f,462f,1757f,642f),paint) }
            }
        }
        text("Open-Meteo"+if(weather.temperature!=null && weather.notice.isNotEmpty()) " · "+weather.notice else "",1355f,646f,12f,secondary)
        c.restore()
    }
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if(e.action==MotionEvent.ACTION_UP) {
            val x=e.x*1775/width;val y=e.y*660/height
            when {
                x<871 -> {
                    if(mapMessage.isEmpty())onMap()
                    else if(mapMessage != LauncherMapPreview.LOADING) onShowMap()
                }
                x in 887f..1315f -> when {
                    y in 536f..616f && x in 947f..1027f -> onMedia(0)
                    y in 536f..616f && x in 1061f..1141f -> onMedia(1)
                    y in 536f..616f && x in 1175f..1255f -> onMedia(2)
                    !media.radio && x in 911f..1291f && y in 478f..540f -> { if(media.canSeek) onSeek(((x-911f)/380f*media.duration).toLong()) }
                    else -> onOpenMusic()
                }
            }
            performClick()
        }
        return true
    }
    override fun performClick():Boolean {super.performClick();return true}
}
