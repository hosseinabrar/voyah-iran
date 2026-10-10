package com.abrar.voyahiran.feature.launcher

import android.content.Context
import android.animation.ValueAnimator
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import java.util.Locale
import kotlin.math.*

/** Page two occupies only the same OEM safe rectangle as page one. */
class LauncherVehicleDashboard(context:Context):View(context) {
    private var compassAngle=0f
    private var ground:Float?=null
    private var compassAnimation:ValueAnimator?=null
    private var groundAnimation:ValueAnimator?=null
    var lightTheme=false
        set(v){if(field==v)return;field=v;artwork.light=v;invalidate()}
    var state=LauncherVehicleState()
        set(v){
            if(field==v)return
            if(field.groundMm!=v.groundMm) {
                groundAnimation?.cancel()
                val target=v.groundMm?.toFloat()
                if(ground==null || target==null)ground=target else {
                    groundAnimation=ValueAnimator.ofFloat(ground!!,target).apply {
                        duration=1000;addUpdateListener {ground=it.animatedValue as Float;invalidate()};start()
                    }
                }
            }
            field=v;artwork.state=v;invalidate()
        }
    var compass=LauncherCompassState()
        set(v){
            if(field.bearing!=v.bearing) {
                compassAnimation?.cancel()
                val target=v.bearing ?: 0f
                if(field.bearing==null || v.bearing==null)compassAngle=target else {
                    val delta=((target-compassAngle+540f)%360f)-180f
                    compassAnimation=ValueAnimator.ofFloat(compassAngle,compassAngle+delta).apply {
                        duration=200;addUpdateListener {compassAngle=(it.animatedValue as Float+360f)%360f;invalidate()};start()
                    }
                }
            }
            field=v;invalidate()
        }
    fun stopAnimations(){compassAnimation?.cancel();groundAnimation?.cancel();ground=state.groundMm?.toFloat();compassAngle=compass.bearing ?: 0f}
    var busy=false
        set(v){field=v;invalidate()}
    var onCommand:(String,String)->Unit={_,_->}
    var onDrive:()->Unit={}
    val artwork=LauncherSuspensionArtwork(context){invalidate()}
    private val p=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val energy by lazy {context.assets.open("launcher/ccc/energy_car.png").use {BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply{inSampleSize=2})!!}}
    private val driveIcons by lazy {mapOf(1 to "eco_normal",2 to "comfort_normal",3 to "sport_img",4 to "outing_img",5 to "person_normal",6 to "snow_normal").mapValues {(_,name)->
        runCatching {context.assets.open("launcher/ccc/$name.png").use(BitmapFactory::decodeStream)}.getOrNull()
    }}
    private val fg get()=if(lightTheme)0xff263651.toInt() else Color.WHITE
    private val muted get()=if(lightTheme)0xff6d7e99.toInt() else 0xffc0cbe0.toInt()
    private val cyan=0xff00aaff.toInt()
    private data class Hit(val rect:RectF,val op:String,val value:String,val enabled:Boolean)
    private val hits=mutableListOf<Hit>()
    private fun text(c:Canvas,s:String,x:Float,y:Float,size:Float=25f,center:Boolean=false,color:Int=fg,bold:Boolean=false) {
        p.color=color;p.textSize=size;p.typeface=Typeface.create("sans-serif",if(bold)Typeface.BOLD else Typeface.NORMAL)
        p.textAlign=if(center)Paint.Align.CENTER else Paint.Align.LEFT
        c.drawText(s,x,y,p)
    }
    private fun card(c:Canvas,x:Float,w:Float) {
        p.shader=LinearGradient(x,0f,x+w,660f,if(lightTheme)intArrayOf(0xfffcfdff.toInt(),0xffedf4fd.toInt()) else intArrayOf(0xff3c4b77.toInt(),0xff1b233a.toInt()),null,Shader.TileMode.CLAMP)
        c.drawRoundRect(x,0f,x+w,660f,16f,16f,p);p.shader=null
        c.save();c.clipPath(Path().apply{addRoundRect(RectF(x,0f,x+w,660f),16f,16f,Path.Direction.CW)})
        p.shader=RadialGradient(x+w/2,666f,w/2,intArrayOf(if(lightTheme)0x779ec5fc else 0x996278b6.toInt(),Color.TRANSPARENT),null,Shader.TileMode.CLAMP)
        c.scale(1f,.13f,x+w/2,660f);c.drawCircle(x+w/2,660f,w*.55f,p);p.shader=null;c.restore()
    }
    private fun selector(c:Canvas,x:Float,y:Float,w:Float,labels:List<Pair<String,String>>,selected:String?,op:String,enabled:Boolean) {
        if(labels.isEmpty()){text(c,"Unavailable",x+w/2,y+47,23f,true,muted);return}
        val cell=w/labels.size
        p.color=if(lightTheme)0xffe0e9f7.toInt() else 0xff454e76.toInt();c.drawRoundRect(x,y,x+w,y+78,12f,12f,p)
        labels.forEachIndexed {i,(value,label)->
            val active=enabled && !busy && (op!="height" || when(value){"3"->state.supportsOuting && state.speed in 0..39;"0"->state.manual in 1..2;else->true})
            if(selected==value) {p.color=if(active)cyan else if(lightTheme)0xff91b1cc.toInt() else 0xff496689.toInt();c.drawRoundRect(x+i*cell,y,x+(i+1)*cell,y+78,12f,12f,p)}
            text(c,label,x+(i+.5f)*cell,y+48,if(labels.size>=4)22f else 25f,true,
                if(selected==value)Color.WHITE else if(active)fg else muted)
            hits+=Hit(RectF(x+i*cell,y,x+(i+1)*cell,y+78),op,value,active)
        }
    }
    override fun onDraw(c:Canvas) {
        super.onDraw(c);hits.clear();c.save();c.scale(width/1775f,height/660f)
        val w=575f;card(c,0f,w);card(c,591f,w);card(c,1182f,w)
        // Transparent illustration derived from the CCC tile, so both themes share the card background.
        c.save();c.clipPath(Path().apply {addRoundRect(RectF(0f,0f,575f,319f),16f,16f,Path.Direction.CW)})
        // Extend beyond the card clip so the transparent right fringe stays outside it.
        // Both themes use the same blue car artwork.
        p.color=Color.WHITE;c.drawBitmap(energy,Rect((energy.width*460f/1860).toInt(),0,energy.width,energy.height),RectF(230f,58f,581f,266f),p);c.restore()
        text(c,"Energy mode",32f,53f,25f)
        val mode=state.modes.entries.firstOrNull {it.value==state.energy}?.key
        text(c,LauncherVehicleState.energyNames[mode] ?: "—",32f,115f,32f,bold=true)
        selector(c,32f,322f,511f,state.modes.map {(k,_)->k to LauncherVehicleState.energyNames.getValue(k)},mode,"energy",state.fresh && state.energy in state.modes.values)
        p.color=if(lightTheme)0xffd8e4f2.toInt() else 0xff61708c.toInt();p.strokeWidth=1f;c.drawLine(24f,426f,551f,426f,p)
        text(c,"Energy recovery",32f,484f)
        text(c,when(state.recovery){2->"Low";3->"Medium";4->"High";else->"—"},32f,532f,32f,bold=true)
        for(i in 0..2) {p.color=if(i<(state.recovery?.minus(1) ?: 0))cyan else if(lightTheme)0xffc5d4e9.toInt() else 0xff4a587b.toInt();c.drawRoundRect(420f+i*34,535f-(i+1)*27,442f+i*34,535f,5f,5f,p)}
        selector(c,32f,560f,511f,listOf("2" to "Low","3" to "Medium","4" to "High"),state.recovery?.toString(),"recovery",state.fresh && state.recovery in 2..4)
        fun degrees(n:Double?)=n?.let {String.format(Locale.US,"%.0f°",it)} ?: "—"
        text(c,"Slope ${degrees(compass.slope)}",622f,54f,23f)
        text(c,"Inclination ${degrees(compass.inclination)}",928f,54f,23f)
        val cx=878.5f;val cy=329f
        // Draw in final coordinates. Avoid hundreds of rotated thin-line draw calls on
        // the OEM GPU, including while the parent pager itself is translating.
        val rotation=compassAngle*PI/180.0
        p.color=muted
        for(i in 0 until 180) {
            val a=i*PI/90-PI/2-rotation
            val dx=cos(a).toFloat();val dy=sin(a).toFloat()
            val inner=if(i%15==0)141f else 154f
            p.strokeWidth=if(i%15==0)2.3f else 1f
            c.drawLine(cx+dx*166f,cy+dy*166f,cx+dx*inner,cy+dy*inner,p)
        }
        for(i in 0..11) {
            val a=i*PI/6-PI/2-rotation
            val x=cx+cos(a).toFloat()*199;val y=cy+sin(a).toFloat()*199
            text(c,(i*30).toString(),x,y+8,21f,true)
        }
        listOf("N","E","S","W").forEachIndexed {i,s->
            val a=i*PI/2-PI/2-rotation
            val x=cx+cos(a).toFloat()*126;val y=cy+sin(a).toFloat()*126
            text(c,s,x,y+9,25f,true,bold=true)
        }
        p.color=0xffff3c58.toInt();c.drawPath(Path().apply{moveTo(cx,117f);lineTo(cx-9,137f);lineTo(cx,132f);lineTo(cx+9,137f);close()},p)
        p.color=fg;p.strokeWidth=2f;c.drawLine(cx,138f,cx,177f,p)
        val bearing=compass.bearing
        text(c,if(bearing==null)"—" else listOf("N","NE","E","SE","S","SW","W","NW")[((bearing+22.5f)/45).toInt()%8],cx,316f,30f,true)
        text(c,if(bearing==null)"—°" else String.format(Locale.US,"%.0f°",bearing),cx,360f,27f,true)
        text(c,"Altitude ${compass.altitude?.let {String.format(Locale.US,"%.0f",it)} ?: "—"} m",622f,621f,23f)
        text(c,"Air Pressure ${state.pressure?.takeIf{it>0}?.let{String.format(Locale.US,"%.0f",it/10.0)} ?: "—"} kPa",911f,621f,23f)
        text(c,"Suspension height",1214f,54f)
        p.color=cyan;c.drawRoundRect(1214f,70f,1223f,120f,4f,4f,p)
        text(c,state.heightLabel,1240f,108f,32f,bold=true)
        val rect=RectF(1182f,167f,1757f,467f)
        p.color=Color.WHITE
        if(artwork.wheels.size==4) {
            var x=rect.left
            listOf(190f,130f,100f,156f).forEachIndexed {i,ww->val end=x+ww*575/576;c.drawBitmap(artwork.wheels[i],null,RectF(x,rect.top,end,rect.bottom),p);x=end}
        } else artwork.body?.let {c.drawBitmap(it,null,rect,p)}
        // Fixed callout endpoint below the body even in the Lowest suspension frame.
        if(ground!=null) {
            p.color=muted;p.strokeWidth=1.5f;p.style=Paint.Style.STROKE
            c.drawPath(Path().apply {moveTo(1618f,438f);lineTo(1588f,438f);lineTo(1558f,380f)},p)
            p.style=Paint.Style.FILL;c.drawCircle(1558f,380f,2.5f,p)
        }
        text(c,ground?.let{String.format(Locale.US,"%.0f mm",it)} ?: "— mm",1630f,446f,23f)
        driveIcons[state.drive]?.let {
            p.colorFilter=if(lightTheme)PorterDuffColorFilter(fg,PorterDuff.Mode.SRC_IN) else null
            c.drawBitmap(it,null,RectF(1214f,486f,1248f,520f),p);p.colorFilter=null
        }
        text(c,LauncherVehicleState.driveNames[state.drive] ?: "—",1264f,516f,30f)
        p.color=fg;p.strokeWidth=3f;c.drawLine(1705f,494f,1715f,504f,p);c.drawLine(1715f,504f,1705f,514f,p)
        hits+=Hit(RectF(1208f,473f,1725f,543f),"drive-menu","",state.fresh && state.drive in 1..6 && !busy)
        selector(c,1214f,560f,511f,listOf("0" to "Lowest","1" to "Low","2" to "Medium","3" to "Highest"),state.heightIndex.takeIf{it>=0}?.toString(),"height",state.suspensionEnabled)
        c.restore()
    }
    override fun onTouchEvent(e:MotionEvent):Boolean {
        if(e.action==MotionEvent.ACTION_UP) {
            val x=e.x*1775/width;val y=e.y*660/height
            hits.firstOrNull {it.enabled && it.rect.contains(x,y)}?.let {if(it.op=="drive-menu")onDrive() else onCommand(it.op,it.value)}
            performClick()
        }
        return true
    }
    override fun performClick():Boolean {super.performClick();return true}
}
