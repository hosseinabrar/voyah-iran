package com.abrar.voyahiran.feature.launcher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

internal data class PassengerMusicPresentation(val title:String="Music",val artist:String="",val cover:Bitmap?=null,
    val source:Drawable?=null,val playing:Boolean=false,val position:Long=0,val duration:Long=0,val seek:Boolean=false)

/** Actual passenger music surface; no sample metadata or cover is bundled into production. */
internal class PassengerMusicView(context:Context):View(context){
    var light=false
        set(v){if(field!=v){field=v;invalidate()}}
    var onCommand:(String,String)->Unit={_,_->}
    private var state=PassengerMusicPresentation()
    private val motion=LauncherMotion(this)
    private var stamp=0L
    private var volume=0
    private var drag=0
    private var previewVolume=0
    private var previewPosition=0L
    private var downX=0f;private var downY=0f
    private var oldCover:Bitmap?=null
    private var blend=1f
    private var fade:ValueAnimator?=null
    private var volumeQueued=false
    private val volumeSend=Runnable{volumeQueued=false;onCommand("volume",previewVolume.toString())}
    private val tick=object:Runnable{override fun run(){if(isAttachedToWindow&&isShown&&windowVisibility==VISIBLE&&state.playing&&state.duration>0){invalidate((163*width/556f).toInt(),(110*height/256f).toInt(),(544*width/556f).toInt(),(155*height/256f).toInt());postDelayed(this,1000)}}}
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val font=Typeface.createFromAsset(context.assets,"passenger/fonts/Roboto-Regular.ttf")
    private val bold=Typeface.createFromAsset(context.assets,"passenger/fonts/Roboto-Bold.ttf")
    private val persian=Typeface.createFromAsset(context.assets,"passenger/fonts/iransansx_regular.ttf")
    private fun asset(n:String)=context.assets.open("passenger/music/$n.png").use{BitmapFactory.decodeStream(it)!!}
    private val previous=asset("launcher_previous")
    private val next=asset("launcher_next")
    private val music=asset("volume_music")
    init{isClickable=true;contentDescription="Music playback and media volume"}
    fun update(s:PassengerMusicPresentation){
        if(s==state)return
        motion.updated()
        val trackChanged=s.title!=state.title||s.artist!=state.artist
        if(trackChanged)cancelGesture()
        val timingChanged=trackChanged||s.position!=state.position||s.playing!=state.playing||s.duration!=state.duration
        val changed=s.cover!==state.cover
        if(changed){fade?.cancel();oldCover=state.cover;blend=1f}
        state=s;if(timingChanged)stamp=SystemClock.elapsedRealtime()
        if(changed&&isAttachedToWindow&&isShown&&ValueAnimator.areAnimatorsEnabled()){
            fade=ValueAnimator.ofFloat(0f,1f).apply{duration=300;addUpdateListener{blend=it.animatedValue as Float;invalidate()};addListener(object:android.animation.AnimatorListenerAdapter(){override fun onAnimationEnd(animation:android.animation.Animator){oldCover=null}});start()}
        }else if(changed)oldCover=null
        contentDescription="${s.title}, ${s.artist}";schedule();invalidate()
    }
    fun updateVolume(value:Int){val bounded=value.coerceIn(0,30);if(volume!=bounded){motion.updated();volume=bounded;invalidate()}}
    private fun schedule(){removeCallbacks(tick);if(isAttachedToWindow&&isShown&&windowVisibility==VISIBLE&&state.playing&&state.duration>0)postDelayed(tick,1000)}
    override fun onAttachedToWindow(){super.onAttachedToWindow();schedule()}
    override fun onWindowVisibilityChanged(v:Int){super.onWindowVisibilityChanged(v);if(isAttachedToWindow){if(v!=VISIBLE)cancelGesture();schedule()}}
    override fun onDetachedFromWindow(){removeCallbacks(tick);cancelGesture();fade?.cancel();oldCover=null;blend=1f;super.onDetachedFromWindow()}
    private fun cancelGesture(){removeCallbacks(volumeSend);volumeQueued=false;drag=0}
    private fun position()=if(drag==2)previewPosition else (state.position+if(state.playing)(SystemClock.elapsedRealtime()-stamp).coerceAtLeast(0)else 0).coerceIn(0,state.duration.coerceAtLeast(0))
    private val fg get()=if(light)0xff293543.toInt()else 0xfff5f8ff.toInt()
    private val muted get()=if(light)0xff71839e.toInt()else 0xffbdc9e0.toInt()
    private fun text(c:Canvas,value:String,x:Float,y:Float,size:Float,color:Int,width:Float=Float.MAX_VALUE,strong:Boolean=false){
        paint.shader=null;paint.colorFilter=null;paint.color=color;paint.textSize=size;paint.typeface=if(value.any{it in '\u0600'..'\u06ff'})persian else if(strong)bold else font;paint.textAlign=Paint.Align.LEFT
        var s=value;if(paint.measureText(s)>width){while(s.isNotEmpty()&&paint.measureText(s+"…")>width)s=s.substring(0,s.offsetByCodePoints(s.length,-1));s+="…"};c.drawText(s,x,y,paint)
    }
    private fun bitmap(c:Canvas,b:Bitmap,r:RectF,tint:Int?=null,alpha:Int=255){paint.shader=null;paint.color=Color.WHITE;paint.alpha=alpha;paint.colorFilter=tint?.let{PorterDuffColorFilter(it,PorterDuff.Mode.SRC_IN)};c.drawBitmap(b,null,r,paint);paint.colorFilter=null;paint.alpha=255}
    private fun clock(ms:Long)="${ms.coerceAtLeast(0)/60000}:${(ms.coerceAtLeast(0)/1000%60).toString().padStart(2,'0')}"
    override fun onDraw(c:Canvas){
        super.onDraw(c);paint.color=Color.WHITE;paint.style=Paint.Style.FILL;paint.colorFilter=null;c.save();c.scale(width/556f,height/256f);c.clipPath(Path().apply{addRoundRect(RectF(0f,0f,556f,256f),16f,16f,Path.Direction.CW)})
        paint.shader=LinearGradient(0f,0f,556f,256f,if(light)intArrayOf(0xfff7faff.toInt(),0xffeef4fc.toInt())else intArrayOf(0xff35456e.toInt(),0xff1b233a.toInt()),null,Shader.TileMode.CLAMP);c.drawRect(0f,0f,556f,256f,paint);paint.shader=null
        c.save();c.translate(278f,266f);c.scale(1f,.12f);paint.shader=RadialGradient(0f,0f,417f,intArrayOf(if(light)0x356ba5ef else 0x706278b6,0x006278b6),null,Shader.TileMode.CLAMP);c.drawCircle(0f,0f,417f,paint);paint.shader=null;c.restore()
        val coverRect=RectF(20f,20f,154.4f,154.4f);c.save();c.clipPath(Path().apply{addRoundRect(coverRect,12f,12f,Path.Direction.CW)})
        paint.color=if(light)0xffdce5f2.toInt()else 0xff202c47.toInt();c.drawRect(coverRect,paint)
        fun cover(b:Bitmap?,alpha:Int){if(b==null){bitmap(c,music,RectF(63f,63f,111f,111f),muted,alpha)}else{val size=min(b.width,b.height);paint.color=Color.WHITE;paint.alpha=alpha;c.drawBitmap(b,Rect((b.width-size)/2,(b.height-size)/2,(b.width+size)/2,(b.height+size)/2),coverRect,paint);paint.alpha=255}}
        if(blend<1f)cover(oldCover,255);cover(state.cover,(blend*255).toInt());c.restore()
        state.source?.let{d->c.save();c.clipPath(Path().apply{addRoundRect(RectF(121.4f,121.4f,149.4f,149.4f),7f,7f,Path.Direction.CW)});val old=Rect(d.bounds);d.setBounds(121,121,149,149);d.draw(c);d.bounds=old;c.restore()}
        motion.text("title",state.title.ifBlank{"Music"},c,RectF(170f,37f,385f,74f)){text(c,it,173f,68f,28f,fg,210f,true)}
        motion.text("artist",state.artist,c,RectF(170f,77f,385f,109f)){text(c,it,173f,102f,21f,muted,210f)}
        bitmap(c,previous,RectF(397.6f,59.6f,426.4f,88.4f),if(light)fg else null)
        bitmap(c,next,RectF(505.6f,59.6f,534.4f,88.4f),if(light)fg else null)
        paint.shader=LinearGradient(438f,46f,494f,102f,if(light)0xff719eff.toInt()else 0xff536eb5.toInt(),if(light)0xff3868e5.toInt()else 0xff304986.toInt(),Shader.TileMode.CLAMP);paint.color=Color.WHITE;c.drawCircle(466f,74f,28f,paint);paint.shader=null;paint.color=0xffe5edff.toInt()
        motion.content("play",state.playing.toString(),c,RectF(450f,56f,484f,92f)){token->
            paint.color=0xffe5edff.toInt()
            if(token=="true"){c.drawRoundRect(RectF(456f,62f,463f,86f),1f,1f,paint);c.drawRoundRect(RectF(470f,62f,477f,86f),1f,1f,paint)}else c.drawPath(Path().apply{moveTo(459f,61f);lineTo(480f,74f);lineTo(459f,87f);close()},paint)
        }
        val elapsed=position();val progress=if(state.duration>0)elapsed.toFloat()/state.duration else 0f
        paint.strokeWidth=3f;paint.color=if(light)0xffcdd9e8.toInt()else 0x26ffffff;c.drawLine(173f,126f,534f,126f,paint)
        paint.color=if(light)0xff668cce.toInt()else 0xffc9d7f9.toInt();c.drawLine(173f,126f,173f+361*progress,126f,paint);c.drawCircle(173f+361*progress,126f,5f,paint)
        text(c,if(state.duration>0)clock(elapsed)else "—",173f,152f,17f,muted)
        val duration=if(state.duration>0)clock(state.duration)else "—";paint.textSize=17f;text(c,duration,534f-paint.measureText(duration),152f,17f,muted)
        val n=if(drag==1)previewVolume else volume
        val smoothVolume=motion.value("volume",n.toFloat())
        paint.color=if(light)0xffdce5f2.toInt()else 0xff202c47.toInt();c.drawRoundRect(RectF(17f,167.6f,90f,234f),20f,20f,paint);c.drawRoundRect(RectF(103f,167.6f,443f,234f),20f,20f,paint)
        c.save();c.clipPath(Path().apply{addRoundRect(RectF(103f,167.6f,443f,234f),20f,20f,Path.Direction.CW)});paint.color=0xff389cff.toInt();c.drawRect(103f,167.6f,103f+340*(if(drag==1)n.toFloat()else smoothVolume)/30f,234f,paint);c.restore()
        bitmap(c,music,RectF(256f,183.8f,290f,217.8f),if(light)0xff63788b.toInt()else 0xffeef5ff.toInt())
        motion.content("mute",(n==0).toString(),c,RectF(17f,167.6f,90f,234f)){speaker(c,it=="true")}
        text(c,n.toString(),460f,210f,27f,fg);text(c,"/ 30",495f,206f,15f,muted)
        c.restore()
    }
    private fun speaker(c:Canvas,off:Boolean){
        c.save();c.translate(32.5f,179.8f);c.scale(1.05f,1.05f);paint.color=if(light)0xff536780.toInt()else 0xffc9d6ed.toInt();paint.style=Paint.Style.STROKE;paint.strokeWidth=2.2f;paint.strokeCap=Paint.Cap.ROUND;paint.strokeJoin=Paint.Join.ROUND
        c.drawPath(Path().apply{moveTo(7f,15f);lineTo(13f,15f);lineTo(21f,8f);lineTo(21f,32f);lineTo(13f,25f);lineTo(7f,25f);close()},paint)
        if(off){c.drawLine(27f,16f,35f,24f,paint);c.drawLine(35f,16f,27f,24f,paint)}else{c.drawPath(Path().apply{moveTo(26f,14f);cubicTo(29f,17f,29f,23f,26f,26f);moveTo(30f,9f);cubicTo(36f,15f,36f,25f,30f,31f)},paint)}
        paint.style=Paint.Style.FILL;paint.strokeCap=Paint.Cap.BUTT;c.restore()
    }
    private fun slide(x:Float){if(drag==1){previewVolume=((x-103)/340*30).roundToInt().coerceIn(0,30);if(!volumeQueued){volumeQueued=true;postDelayed(volumeSend,80)}}else if(drag==2)previewPosition=(((x-173)/361).coerceIn(0f,1f)*state.duration).toLong();invalidate()}
    override fun onTouchEvent(e:MotionEvent):Boolean{
        val x=e.x*556/width;val y=e.y*256/height
        when(e.actionMasked){
            MotionEvent.ACTION_DOWN->{downX=x;downY=y;drag=if(x in 103f..443f&&y in 167.6f..234f)1 else if(state.seek&&state.duration>0&&x in 163f..544f&&y in 110f..154f)2 else 0;if(drag!=0){parent?.requestDisallowInterceptTouchEvent(true);slide(x)};return true}
            MotionEvent.ACTION_MOVE->{if(drag!=0)slide(x);return true}
            MotionEvent.ACTION_CANCEL->{cancelGesture();parent?.requestDisallowInterceptTouchEvent(false);invalidate();return true}
            MotionEvent.ACTION_UP->{
                if(drag!=0){slide(x);removeCallbacks(volumeSend);volumeQueued=false;if(drag==1)onCommand("volume",previewVolume.toString())else onCommand("seek",previewPosition.toString())}
                else if(hypot(x-downX,y-downY)<20){when{
                    x in 17f..90f&&y in 167.6f..234f->onCommand("mute","")
                    x in 20f..154.4f&&y in 20f..154.4f->onCommand("source","")
                    y in 46f..102f&&x in 392f..540f->onCommand("media",if(x<438)"previous"else if(x<494)"play"else "next")
                }};drag=0;parent?.requestDisallowInterceptTouchEvent(false);performClick();invalidate();return true
            }
        };return true
    }
    override fun performClick():Boolean{super.performClick();return true}
}
