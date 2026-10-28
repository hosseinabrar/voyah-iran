package com.abrar.voyahiran.feature.launcher

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import org.json.JSONObject
import kotlin.math.*

/** Native rendering of the approved 770x280 fragrance card, using its original layered assets. */
internal class PassengerFragranceView(context:Context):View(context) {
    var light=false
        set(v){if(field!=v){field=v;invalidate()}}
    var onCommand:(String,String)->Unit={_,_->}
    private var state=JSONObject()
    private val motion=LauncherMotion(this)
    private var scent=2
    private var oldScent=2
    private var blend=1f
    private var fade:ValueAnimator?=null
    private var breath:ValueAnimator?=null
    private var pulse=0f
    private var artReady=false
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val staticArtwork=StaticLauncherArtwork(this)
    private val font=Typeface.createFromAsset(context.assets,"passenger/fonts/Roboto-Regular.ttf")
    private val images=HashMap<String,Bitmap>()
    private var downX=0f;private var downY=0f
    private fun value(k:String)=state.optInt(k,-1)
    private fun valid(k:String,r:IntRange)=state.optBoolean("fresh")&&value(k) in r
    private val on get()=state.optBoolean("fresh")&&value("FCM_SW_REQ_FB")==2
    init {
        isClickable=true;contentDescription="Fragrance selection, intensity and duration"
        val paths=buildList {
            for(i in 0..27)add("passenger/fragrance-wide/fragrance_anim_${(i*2).toString().padStart(2,'0')}.png")
            for(tone in 1..3)for(day in listOf(false,true)){
                val objectName="object-$tone"+if(day)if(tone==2)"-day-v2"else "-day-v1"else ""
                add("passenger/fragrance-wide/$objectName.png");add("passenger/fragrance-wide/$objectName-gray.png")
                for(on in listOf(false,true)){
                    val base="passenger/baked/fragrance-${if(day)"light"else "dark"}-$tone-$on"
                    add("$base.png");if(!on)add("$base-gray.png")
                }
            }
        }
        PassengerArtworkCache.prepare(context,"fragrance-scenes",paths){loaded->
            loaded.filterKeys{it.startsWith("passenger/fragrance-wide/")}.forEach{(path,b)->images[path.substringAfterLast('/').removeSuffix(".png")]=b}
            staticArtwork.install(loaded);artReady=true;animateObject();invalidate()
        }
    }
    fun update(s:JSONObject){
        if(state.toString()==s.toString())return
        motion.updated()
        state=s
        val artwork=PassengerFragranceMapping.artwork(value("IVI_FRAG_TASTE_FB"))
        if(valid("IVI_FRAG_TASTE_FB",1..3)&&artwork!=null&&scent!=artwork){
            fade?.cancel();oldScent=scent;scent=artwork
            if(artReady&&isAttachedToWindow&&isShown&&ValueAnimator.areAnimatorsEnabled()){
                fade=ValueAnimator.ofFloat(0f,1f).apply{duration=320;addUpdateListener{blend=it.animatedValue as Float;invalidate()};start()}
            }else blend=1f
        }
        invalidate()
        animateObject()
    }
    private fun animateObject(){
        if(artReady&&isAttachedToWindow&&isShown&&windowVisibility==VISIBLE&&on&&ValueAnimator.areAnimatorsEnabled()){
            // OEM uses 56 frames at 40 ms. Bundled artwork contains every other frame.
            if(breath==null)breath=ValueAnimator.ofFloat(0f,1f).apply{duration=2240;interpolator=LinearInterpolator();repeatCount=ValueAnimator.INFINITE;addUpdateListener{val p=it.animatedValue as Float;if((p*28).toInt()!=(pulse*28).toInt()){pulse=p;invalidate()}};start()}
        }else{breath?.cancel();breath=null;pulse=0f}
    }
    override fun onAttachedToWindow(){super.onAttachedToWindow();animateObject()}
    override fun onWindowVisibilityChanged(visibility:Int){super.onWindowVisibilityChanged(visibility);animateObject()}
    override fun onVisibilityChanged(changedView:View,visibility:Int){super.onVisibilityChanged(changedView,visibility);animateObject()}
    override fun onDetachedFromWindow(){fade?.cancel();breath?.cancel();breath=null;pulse=0f;blend=1f;super.onDetachedFromWindow()}
    private var grayscale=false
    private fun gray(value:Int):Int { if(!grayscale)return value;val y=(Color.red(value)*.213f+Color.green(value)*.715f+Color.blue(value)*.072f).toInt();return Color.argb(Color.alpha(value),y,y,y) }
    private fun color(s:String)=gray(Color.parseColor(s))
    private fun asset(n:String):Bitmap?=images[n] ?: PassengerArtworkCache.get(context,"passenger/fragrance-wide/$n.png",this)?.also{images[n]=it}
    private fun image(c:Canvas,n:String,r:RectF,cover:Boolean=false,alpha:Int=255){
        val b=asset(if(grayscale)"$n-gray"else n) ?: return;val scale=if(cover)max(r.width()/b.width,r.height()/b.height) else min(r.width()/b.width,r.height()/b.height)
        paint.shader=null;paint.color=Color.WHITE;paint.alpha=alpha;paint.colorFilter=null
        c.drawBitmap(b,null,RectF(r.centerX()-b.width*scale/2,r.centerY()-b.height*scale/2,r.centerX()+b.width*scale/2,r.centerY()+b.height*scale/2),paint)
        paint.colorFilter=null;paint.alpha=255
    }
    private fun text(c:Canvas,s:String,x:Float,y:Float,size:Float,tint:Int){
        paint.shader=null;paint.color=gray(tint);paint.typeface=font;paint.textSize=size;paint.textAlign=Paint.Align.CENTER;c.drawText(s,x,y,paint)
    }
    override fun onDraw(c:Canvas){
        super.onDraw(c);c.save();c.scale(width/770f,height/280f)
        c.clipPath(Path().apply{addRoundRect(RectF(0f,0f,770f,280f),16f,16f,Path.Direction.CW)})
        val power=motion.value("power",if(on)1f else 0f)
        grayscale=!on&&power<=0f
        val offLayer=if(!grayscale&&power<1f){paint.color=Color.WHITE;paint.colorFilter=ColorMatrixColorFilter(ColorMatrix().apply{setSaturation(power)});c.saveLayer(0f,0f,770f,280f,paint).also{paint.colorFilter=null}}else -1
        if(blend<1f)scene(c,oldScent)
        if(blend>=1f)scene(c,scent)
        else {val layer=c.saveLayerAlpha(0f,0f,770f,280f,(blend*255).toInt());scene(c,scent);c.restoreToCount(layer)}
        if(offLayer>=0)c.restoreToCount(offLayer)
        c.restore()
    }
    private fun scene(c:Canvas,tone:Int){
        staticArtwork.draw(c,"fragrance-${if(light)"light"else "dark"}-$tone-$on${if(grayscale)"-gray"else ""}",RectF(0f,0f,770f,280f))
        c.save();c.translate(28.0464f,0f);c.scale(.927152f,.927152f)
        // Offset in the 770x280 card coordinates, compensating for the scene scale.
        val objectRect=RectF(467f,46f,707f,242f).apply{offset(10f/.927152f,3f/.927152f)}
        image(c,"object-$tone"+if(light)if(tone==2)"-day-v2" else "-day-v1" else "",objectRect,alpha=(191+64*motion.value("power",if(on)1f else 0f)).toInt())
        val label=if(light)color(when(tone){1->"#707985";2->"#747970";else->"#7c7182"}) else Color.WHITE
        text(c,listOf("","Watery mist","Fresh bamboo","Peony scarlet")[tone],587f,37f,20f,label)
        c.save();c.scale(1.1f,1.1f,215f,158f)
        for(row in 0..1){
            val y=if(row==0)94f else 193f
            c.save();c.scale(1.2f,1.2f,215f,y+24f)
            text(c,if(row==0)"Intensity" else "Duration",215f,y-12f,18f,label)
            paint.color=if(light)color(when(tone){1->"#99335b78";2->"#99173d2c";else->"#99675478"}) else gray(0x66001629)
            c.drawRoundRect(RectF(71f,y,359f,y+48f),30f,30f,paint)
            val selected=if(row==0)value("IVI_FRAG_CONCERNTION_FB")-1 else listOf(1,2,0).indexOf(value("FCM_DURATION_CONTROL"))
            val position=motion.value("selection-$row",selected.toFloat(),spring=true)
            if(selected>=0){
                val clip=c.save();c.clipPath(Path().apply{addRoundRect(RectF(71f,y,359f,y+48),30f,30f,Path.Direction.CW)})
                paint.color=motion.color("power-$row",on,color("#87939b"),color("#389cff"));c.drawRoundRect(RectF(71f+position*96,y,167f+position*96,y+48),30f,30f,paint);c.restoreToCount(clip)
            }
            for(i in 0..2){
                text(c,(if(row==0)listOf("Low","Medium","High") else listOf("30 min","60 min","Unlimited"))[i],119f+i*96,y+30f,17f,if(on)Color.WHITE else 0xffc6cbd0.toInt())}
            c.restore()
        }
        c.restore()
        listOf(2,3,1).forEachIndexed{i,n->
            // Both scene layers share one control target during the background crossfade.
            val active=on&&scent==n
            val name=when(n){1->"shuigan";2->"zhuzi";else->"mudan"}
            val x=456f+i*82
            val rect=RectF(x,221f,x+90,311f)
            motion.content("scent-icon-$n",if(active)"on" else "off",c,rect){status->
                if(light&&status=="off")staticArtwork.draw(c,"fragrance_icon_${name}_off-light",rect)
                else image(c,"fragrance_icon_${name}_$status",rect)
            }
        }
        // Emission is the foreground layer, including above the active selector icon.
        if(on&&artReady){
            val frame=((pulse*28).toInt().coerceIn(0,27)*2).toString().padStart(2,'0')
            image(c,"fragrance_anim_$frame",RectF(469f,-28f,705f,304f),alpha=if(light)185 else 225)
        }
        c.restore()
    }
    override fun onTouchEvent(e:MotionEvent):Boolean{
        val x=(e.x*770/width-28.0464f)/.927152f;val y=e.y*280/height/.927152f
        when(e.actionMasked){
            MotionEvent.ACTION_DOWN->{downX=x;downY=y;return true}
            MotionEvent.ACTION_UP->{
                if(abs(x-downX)>24||abs(y-downY)>24)return true
                val controlX=215f+(x-215f)/1.1f
                val controlY=158f+(y-158f)/1.1f
                if(y in 237f..302f&&x in 469f..697f&&valid("IVI_FRAG_TASTE_FB",1..3)&&valid("FCM_SW_REQ_FB",1..2)){
                    val n=listOf(2,3,1)[((x-469)/82).toInt().coerceIn(0,2)];onCommand("scent",if(on&&scent==n)"0" else PassengerFragranceMapping.channel(n).toString())
                }else if(on&&controlX in 42.2f..387.8f){val i=((controlX-42.2f)/115.2f).toInt().coerceIn(0,2)
                    if(controlY in 89.2f..146.8f&&valid("IVI_FRAG_CONCERNTION_FB",1..3))onCommand("intensity",(i+1).toString())
                    if(controlY in 188.2f..245.8f&&valid("FCM_DURATION_CONTROL",0..2))onCommand("duration",listOf(1,2,0)[i].toString())
                }
                performClick();return true
            }
        };return true
    }
    override fun performClick():Boolean{super.performClick();return true}
}
