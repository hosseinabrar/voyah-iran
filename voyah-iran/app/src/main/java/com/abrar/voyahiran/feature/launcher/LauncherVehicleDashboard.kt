package com.abrar.voyahiran.feature.launcher

import android.content.Context
import android.animation.ValueAnimator
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import java.util.Locale
import kotlin.math.*
import kotlinx.coroutines.*

/** Page two occupies only the same OEM safe rectangle as page one. */
class LauncherVehicleDashboard(context:Context):android.widget.FrameLayout(context) {
    private val dial=LauncherCompassDial(context)
    init {setWillNotDraw(false);addView(dial)}
    override fun onLayout(changed:Boolean,l:Int,t:Int,r:Int,b:Int){
        dial.layout((658.5f*width/1775).toInt(),(109f*height/660).toInt(),(1098.5f*width/1775).toInt(),(549f*height/660).toInt())
    }
    private val motion=LauncherMotion(this)
    var animationsEnabled=true
        set(v){field=v;motion.paused=transitioning||!v;if(!v)stopAnimations()}
    var transitioning=false
        set(v){if(field==v)return;field=v;motion.paused=v||!animationsEnabled;stopAnimations();if(!v)invalidate()}
    private var ground:Float?=null
    private var groundAnimation:ValueAnimator?=null
    var lightTheme=false
        set(v){if(field==v)return;field=v;artwork.light=v;dial.theme(v);invalidate()}
    var state=LauncherVehicleState()
        set(v){
            if(field==v)return
            motion.updated()
            if(transitioning){field=v;artwork.state=v;return}
            if(field.groundMm!=v.groundMm) {
                groundAnimation?.cancel()
                val target=v.groundMm?.toFloat()
                if(!animationsEnabled || ground==null || target==null)ground=target else {
                    groundAnimation=ValueAnimator.ofFloat(ground!!,target).apply {
                        duration=1000;addUpdateListener {ground=it.animatedValue as Float;invalidate()};start()
                    }
                }
            }
            if(field.drive != v.drive || field.manual != v.manual || field.maintain != v.maintain || field.fresh != v.fresh) onDriveStateChanged()
            field=v;artwork.state=v;invalidate()
        }
    var compass=LauncherCompassState()
        set(v){
            if(field==v)return
            motion.updated()
            if(transitioning){field=v;return}
            val previous=field
            if(field.bearing?.roundToInt()!=v.bearing?.roundToInt()) {
                val target=v.bearing?.roundToInt()?.toFloat() ?: 0f
                dial.bearing(target)
            }
            field=v
            // CCC publishes integer values; sub-degree sensor noise must not redraw the page.
            fun shown(n:Double?)=n?.roundToInt()
            if(previous.bearing?.roundToInt()!=v.bearing?.roundToInt() ||
                shown(previous.slope)!=shown(v.slope) || shown(previous.inclination)!=shown(v.inclination) ||
                shown(previous.altitude)!=shown(v.altitude))invalidate()
        }
    fun stopAnimations(){groundAnimation?.cancel();ground=state.groundMm?.toFloat();dial.bearing(compass.bearing ?: 0f)}
    var busy=false
        set(v){field=v;invalidate()}
    var onCommand:(String,String)->Unit={_,_->}
    var onDrive:()->Unit={}
    var onDriveStateChanged:()->Unit={}
    val artwork=LauncherSuspensionArtwork(context){if(!transitioning)invalidate()}
    private val p=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val staticArtwork=StaticLauncherArtwork(this)
    private var energy:Bitmap?=null
    private var energyDay:Bitmap?=null
    private var driveIcons=emptyMap<Int,Bitmap?>()
    private var artworkJob:Job?=null
    fun preload(scope:CoroutineScope):Job {
        artworkJob?.let {if(!it.isCancelled)return it}
        return scope.launch {
            artwork.prepare()
            val loaded=withContext(Dispatchers.IO){
                fun decode(name:String,sample:Int=1)=runCatching{context.assets.open("launcher/ccc/$name.png").use{
                    BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply{inSampleSize=sample})
                }}.getOrNull()
                val car=decode("energy_car_dark_baked")
                energyDay=context.assets.open("passenger/baked/energy-car-light.png").use{BitmapFactory.decodeStream(it)}
                car to mapOf(1 to "eco_normal",2 to "comfort_normal",3 to "sport_img",4 to "outing_img",5 to "person_normal",6 to "snow_normal",10 to "get_off").mapValues{decode(it.value)}
            }
            energy=loaded.first;driveIcons=loaded.second;if(!transitioning)invalidate()
        }.also {artworkJob=it}
    }
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
        staticArtwork.draw(c,"vehicle-card-${if(lightTheme)"light"else "dark"}",RectF(x,0f,x+w,660f))
    }
    private fun selector(c:Canvas,x:Float,y:Float,w:Float,labels:List<Pair<String,String>>,selected:String?,op:String,enabled:Boolean) {
        if(labels.isEmpty()){text(c,"Unavailable",x+w/2,y+47,23f,true,muted);return}
        val cell=w/labels.size
        p.color=if(lightTheme)0xffe0e9f7.toInt() else 0xff454e76.toInt();c.drawRoundRect(x,y,x+w,y+78,12f,12f,p)
        val index=labels.indexOfFirst{it.first==selected}
        fun selectable(value:String?)=enabled && !busy && (op!="height" || when(value){"3"->state.supportsOuting && state.speed in 0..39;"0"->state.manual in 1..2;else->true})
        val position=motion.value("selector-$op",index.toFloat(),spring=true)
        if(index>=0){
            p.color=motion.color("enabled-$op",selectable(selected),if(lightTheme)0xff91b1cc.toInt() else 0xff496689.toInt(),cyan)
            val clip=c.save();c.clipRect(x,y,x+w,y+78)
            c.drawRoundRect(x+position*cell,y,x+(position+1)*cell,y+78,12f,12f,p);c.restoreToCount(clip)
        }
        labels.forEachIndexed {i,(value,label)->
            val active=selectable(value)
            text(c,label,x+(i+.5f)*cell,y+48,if(labels.size>=4)22f else 25f,true,
                motion.color("label-$op-$value",selected==value,if(active)fg else muted,Color.WHITE))
            hits+=Hit(RectF(x+i*cell,y,x+(i+1)*cell,y+78),op,value,active)
        }
    }
    override fun dispatchDraw(c:Canvas) {
        super.dispatchDraw(c)
        // Draw the fixed pointer after the rotating dial and all of its labels.
        c.save();c.scale(width/1775f,height/660f)
        val cx=878.5f
        p.style=Paint.Style.FILL
        p.color=0xffff3c58.toInt();c.drawPath(Path().apply{moveTo(cx,117f);lineTo(cx-9,137f);lineTo(cx,132f);lineTo(cx+9,137f);close()},p)
        p.color=fg;p.strokeWidth=2f;c.drawLine(cx,138f,cx,177f,p)
        c.restore()
    }
    override fun onDraw(c:Canvas) {
        super.onDraw(c);hits.clear();c.save();c.scale(width/1775f,height/660f)
        val w=575f;card(c,0f,w);card(c,591f,w);card(c,1182f,w)
        // Transparent illustration derived from the CCC tile, so both themes share the card background.
        c.save();c.clipPath(Path().apply {addRoundRect(RectF(0f,0f,575f,319f),16f,16f,Path.Direction.CW)})
        // Extend beyond the card clip so the transparent right fringe stays outside it.
        // Both themes use the same blue car artwork.
        p.color=Color.WHITE;p.colorFilter=null
        (if(lightTheme)energyDay else energy)?.let{c.drawBitmap(it,Rect((it.width*460f/1860).toInt(),0,it.width,it.height),RectF(230f,58f,581f,266f),p)}
        p.colorFilter=null;c.restore()
        text(c,"Energy mode",32f,53f,25f)
        val mode=state.modes.entries.firstOrNull {it.value==state.energy}?.key
        motion.text("energy-label",LauncherVehicleState.energyNames[mode] ?: "—",c,RectF(25f,75f,310f,125f)){text(c,it,32f,115f,32f,bold=true)}
        selector(c,32f,322f,511f,state.modes.map {(k,_)->k to LauncherVehicleState.energyNames.getValue(k)},mode,"energy",state.fresh && state.energy in state.modes.values)
        p.color=if(lightTheme)0xffd8e4f2.toInt() else 0xff61708c.toInt();p.strokeWidth=1f;c.drawLine(24f,426f,551f,426f,p)
        text(c,"Energy recovery",32f,484f)
        motion.text("recovery-label",when(state.recovery){2->"Low";3->"Medium";4->"High";else->"—"},c,RectF(25f,490f,310f,540f)){text(c,it,32f,532f,32f,bold=true)}
        for(i in 0..2) {p.color=motion.color("recovery-bar-$i",i<(state.recovery?.minus(1) ?: 0),if(lightTheme)0xffc5d4e9.toInt() else 0xff4a587b.toInt(),cyan);c.drawRoundRect(420f+i*34,535f-(i+1)*27,442f+i*34,535f,5f,5f,p)}
        selector(c,32f,560f,511f,listOf("2" to "Low","3" to "Medium","4" to "High"),state.recovery?.toString(),"recovery",state.fresh && state.recovery in 2..4)
        fun degrees(n:Double?)=n?.let {String.format(Locale.US,"%.0f°",it)} ?: "—"
        // Live readings must remain fully visible on every sensor update.
        fun reading(key:String,value:String,x:Float,y:Float,size:Float=23f,center:Boolean=false){
            text(c,value,x,y,size,center)
        }
        reading("slope","Slope ${degrees(compass.slope)}",622f,54f)
        reading("inclination","Inclination ${degrees(compass.inclination)}",928f,54f)
        val cx=878.5f;val cy=329f
        // The dial is a child view; its fixed pointer is drawn last in dispatchDraw.
        val bearing=compass.bearing
        reading("direction",if(bearing==null)"—" else listOf("N","NE","E","SE","S","SW","W","NW")[((bearing+22.5f)/45).toInt()%8],cx,316f,30f,true)
        reading("bearing",if(bearing==null)"—°" else String.format(Locale.US,"%.0f°",bearing),cx,360f,27f,true)
        reading("altitude","Altitude ${compass.altitude?.let {String.format(Locale.US,"%.0f",it)} ?: "—"} m",622f,621f)
        reading("pressure","Air Pressure ${state.pressure?.takeIf{it>0}?.let{it.toString()} ?: "—"} hPa",911f,621f)
        text(c,"Suspension height",1214f,54f)
        p.color=cyan;c.drawRoundRect(1214f,70f,1223f,120f,4f,4f,p)
        motion.text("height-label",state.heightLabel,c,RectF(1235f,70f,1750f,120f)){text(c,it,1240f,108f,32f,bold=true)}
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
        motion.content("drive-icon",(if(state.manual==2)10 else state.drive).toString(),c,RectF(1214f,486f,1248f,520f)){token->driveIcons[token.toIntOrNull()]?.let {
            if(lightTheme)staticArtwork.draw(c,"drive-$token-light",RectF(1214f,486f,1248f,520f))
            else c.drawBitmap(it,null,RectF(1214f,486f,1248f,520f),p)
        }}
        motion.text("drive-label",state.displayedDrive,c,RectF(1260f,480f,1700f,530f)){text(c,it,1264f,516f,30f)}
        p.textSize=30f
        val arrowX=1264f+p.measureText(state.displayedDrive)+16f
        if(state.driveMenuVisible) {
            p.color=fg;p.strokeWidth=3f;c.drawLine(arrowX,494f,arrowX+10f,504f,p);c.drawLine(arrowX+10f,504f,arrowX,514f,p)
        }
        hits+=Hit(RectF(1208f,473f,1725f,543f),"drive-menu","",state.driveMenuEnabled && !busy)
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
