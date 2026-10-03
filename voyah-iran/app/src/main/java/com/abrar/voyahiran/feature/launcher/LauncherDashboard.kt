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
    var media = LauncherMediaState()
        set(value) { field = value; invalidate() }
    var mapMessage = ""
        set(value) { field = value; invalidate() }
    var onMap: () -> Unit = {}
    var onMedia: (Int) -> Unit = {}
    var onOpenMusic: () -> Unit = {}
    var onSeek: (Long) -> Unit = {}
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
    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        c.save(); c.scale(width / 1775f, height / 660f)
        fun card(x: Float, y: Float, w: Float, h: Float) {
            paint.shader = LinearGradient(x,y,x+w,y+h,intArrayOf(Color.rgb(60,75,119),Color.rgb(27,35,58)),null,Shader.TileMode.CLAMP)
            c.drawRoundRect(x,y,x+w,y+h,16f,16f,paint); paint.shader = null
            c.save(); c.clipPath(Path().apply { addRoundRect(RectF(x,y,x+w,y+h),16f,16f,Path.Direction.CW) })
            paint.shader = RadialGradient(x+w/2,y+h+7,w*.5f,intArrayOf(0x776278b6,Color.TRANSPARENT),null,Shader.TileMode.CLAMP)
            c.save(); c.scale(1f,.13f,x+w/2,y+h); c.drawCircle(x+w/2,y+h,w*.55f,paint); c.restore(); paint.shader=null;c.restore()
        }
        card(887f,0f,428f,660f); card(1331f,0f,428f,426f); card(1331f,442f,428f,218f)
        c.save();c.clipRect(0f,0f,871f,660f)
        paint.shader=RadialGradient(435f,667f,435f,intArrayOf(0x556278b6,Color.TRANSPARENT),null,Shader.TileMode.CLAMP)
        c.scale(1f,.13f,435f,660f);c.drawCircle(435f,660f,450f,paint);paint.shader=null;c.restore()
        fun text(t: String,x:Float,y:Float,size:Float,color:Int=Color.WHITE,center:Boolean=false,bold:Boolean=false,fa:Boolean=false) {
            paint.color=color;paint.textSize=size;paint.typeface=if(fa)persian else Typeface.create("sans-serif",if(bold)Typeface.BOLD else Typeface.NORMAL)
            paint.textAlign=if(center)Paint.Align.CENTER else Paint.Align.LEFT
            val max=if(x < 1331) 372f else 398f
            var value=t
            while(value.length>1 && paint.measureText(value)>max) value=value.dropLast(1)
            if(value != t) value=value.dropLast(1)+"…"
            c.drawText(value,x,y,paint)
        }
        if(mapMessage.isNotEmpty()) text(mapMessage,435f,330f,24f,center=true,fa=true)
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
        paint.color = 0xffe1e7f5.toInt(); paint.strokeWidth = 2.5f
        c.drawLine(1288f, 27f, 1280f, 35f, paint)
        c.drawLine(1280f, 35f, 1288f, 43f, paint)
        if(media.radio) {
            text(media.title,1101f,255f,48f,center=true,bold=true)
            text(media.artist,1101f,310f,26f,0xffbfc9de.toInt(),center=true)
        } else {
            val cover = media.cover ?: defaultCover
            val rect=RectF(971f,82f,1231f,342f)
            c.save();c.clipPath(Path().apply{addRoundRect(rect,12f,12f,Path.Direction.CW)})
            paint.color=Color.WHITE;c.drawBitmap(cover,null,rect,paint);c.restore()
            text(media.title,1101f,393f,32f,center=true,bold=true)
            text(media.artist,1101f,435f,23f,0xffbfc9de.toInt(),center=true)
        }
        if(!media.radio) {
            paint.color=0xff7c879f.toInt();paint.strokeWidth=4f;c.drawLine(911f,480f,1291f,480f,paint)
            if(media.duration>0) { paint.color=Color.WHITE;c.drawLine(911f,480f,911f+380f*(media.position.toFloat()/media.duration).coerceIn(0f,1f),480f,paint) }
            fun time(ms:Long)=String.format(Locale.US,"%d:%02d",ms/60000,ms/1000%60)
            text(time(media.position),911f,509f,17f);text(time(media.duration),1250f,509f,17f)
        }
        paint.shader=LinearGradient(1060f,545f,1140f,625f,0xff617bbb.toInt(),0xff354b86.toInt(),Shader.TileMode.CLAMP)
        c.drawCircle(1101f,576f,38f,paint);paint.shader=null
        listOf(987f to icons[0],1101f to icons[if(media.playing)2 else 1],1215f to icons[3]).forEach { (x,b)->c.drawBitmap(b,null,RectF(x-18,558f,x+18,594f),paint) }
        val now=Calendar.getInstance();val cx=1545f;val cy=177f
        paint.color=0xff252726.toInt();c.drawCircle(cx,cy,124f,paint)
        for(i in 0..59) { c.save();c.rotate(i*6f,cx,cy);paint.color=0xffe6eaf1.toInt();paint.strokeWidth=if(i%5==0)2f else 1f;c.drawLine(cx,cy-116,cx,cy-if(i%5==0)107 else 112,paint);c.restore() }
        fun hand(degrees:Float,length:Float,stroke:Float) {c.save();c.rotate(degrees,cx,cy);paint.color=0xffedf3ff.toInt();paint.strokeWidth=stroke;paint.strokeCap=Paint.Cap.ROUND;c.drawLine(cx,cy,cx,cy-length,paint);paint.strokeCap=Paint.Cap.BUTT;c.restore()}
        hand((now.get(Calendar.HOUR)%12)*30f+now.get(Calendar.MINUTE)*.5f,65f,5f)
        hand(now.get(Calendar.MINUTE)*6f+now.get(Calendar.SECOND)*.1f,84f,4f)
        paint.color=0xffdfeaff.toInt();c.drawCircle(cx,cy,7f,paint)
        text(LauncherClock.persianDate(now.timeInMillis),cx,349f,26f,center=true,fa=true)
        text(LauncherClock.gregorianDate(now.timeInMillis),cx,387f,17f,0xffb8c4df.toInt(),center=true)
        text("Weather",1387f,530f,23f);text("—",1387f,588f,51f)
        c.restore()
    }
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if(e.action==MotionEvent.ACTION_UP) {
            val x=e.x*1775/width;val y=e.y*660/height
            when {
                x<871 -> onMap()
                x in 887f..1315f -> when {
                    y in 536f..616f && x in 947f..1027f -> onMedia(0)
                    y in 536f..616f && x in 1061f..1141f -> onMedia(1)
                    y in 536f..616f && x in 1175f..1255f -> onMedia(2)
                    !media.radio && media.duration > 0 && x in 911f..1291f && y in 455f..505f -> onSeek(((x-911f)/380f*media.duration).toLong())
                    else -> onOpenMusic()
                }
            }
            performClick()
        }
        return true
    }
    override fun performClick():Boolean {super.performClick();return true}
}
