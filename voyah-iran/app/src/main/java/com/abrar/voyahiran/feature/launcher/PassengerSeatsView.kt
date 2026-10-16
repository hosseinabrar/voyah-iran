package com.abrar.voyahiran.feature.launcher

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import android.util.LruCache
import kotlinx.coroutines.*
import org.json.JSONObject
import kotlin.math.abs

/** OEM layers in the approved 556x228 composition. Feedback, not taps, drives all levels. */
internal class PassengerSeatsView(context:Context):View(context) {
    var light=false
        set(v){if(field!=v){field=v;invalidate()}}
    var onCommand:(String,String)->Unit={_,_->}
    private var state=JSONObject()
    private val motion=LauncherMotion(this)
    private val fadingArtwork=HashMap<String,Bitmap>()
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val font=Typeface.createFromAsset(context.assets,"passenger/fonts/Roboto-Regular.ttf")
    private val cache=object:LruCache<String,Bitmap>(12*1024*1024){override fun sizeOf(key:String,value:Bitmap)=value.allocationByteCount}
    // Animation churn must never evict the static seats or their controls.
    private val artwork=java.util.concurrent.ConcurrentHashMap<String,Bitmap>()
    private fun cached(name:String)=artwork[name]?:cache.get(name)
    private fun retain(name:String,b:Bitmap){if(name.lastOrNull()?.isDigit()==true)cache.put(name,b)else artwork[name]=b}
    private var scope:CoroutineScope?=null
    private var animation:Job?=null
    private var frame=10
    private var downX=0f;private var downY=0f
    private val bright=ColorMatrixColorFilter(ColorMatrix(floatArrayOf(1.1712f,0f,0f,0f,5.1f,0f,1.1712f,0f,0f,5.1f,0f,0f,1.1712f,0f,5.1f,0f,0f,0f,1f,0f)))
    private fun level(side:Int,feature:String)=PassengerSeatFeedback.level(state,side,feature)
    private fun valid(side:Int,feature:String)=state.optBoolean("fresh")&&level(side,feature) in if(feature=="mode")1..2 else 0..3
    private fun active(side:Int,feature:String)=valid(side,feature)&&level(side,feature)>0
    init {isClickable=true;contentDescription="Driver and passenger seat ventilation, heating and massage";load("seat_bg_high_level_97c")
        for(s in listOf("left","right"))for(f in listOf("vent","heat","massage")){control("seat_${s}_${f}_0_normal",false);control("seat_${s}_${f}_0_normal",true)}
    }
    private fun load(name:String):Bitmap {
        cached(name)?.let{return it}
        val b=context.assets.open("passenger/oem-x/$name.png").use{BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply{if(name.endsWith("bg_97c")||name=="seat_bg_high_level_97c")inSampleSize=2})!!}
        retain(name,b);return b
    }
    private fun control(name:String,day:Boolean):Bitmap {
        if(!day)return load(name)
        cached("light-$name")?.let{return it}
        val source=load(name);val b=source.copy(Bitmap.Config.ARGB_8888,true);val pixels=IntArray(b.width*b.height);b.getPixels(pixels,0,b.width,0,0,b.width,b.height)
        pixels.indices.forEach{i->val c=pixels[i];if(Color.red(c)+Color.green(c)+Color.blue(c)>740){
            val tint=if(i/b.width>=b.height*.78f)0xffdce5f2.toInt()else 0xff747970.toInt();pixels[i]=(c and -0x1000000)or(tint and 0xffffff)
        }}
        b.setPixels(pixels,0,b.width,0,0,b.width,b.height);retain("light-$name",b);return b
    }
    private fun names():List<String> = buildList {
        for(side in 0..1){val s=if(side==0)"left"else "right"
            if(active(side,"vent")||active(side,"heat")){val kind=if(active(side,"vent"))"vent"else "hot";add("seatfront_${kind}${s}_bg_97c");add("seat_${kind}_${s}_${frame%89}")}
            if(active(side,"massage"))add("seat_${if(side==0)"main"else "co"}_massage_${if(level(side,"mode")==2)"rolling"else "waving"}_${frame%91}")
        }
    }
    fun update(next:JSONObject){
        if(state.toString()==next.toString())return
        motion.updated()
        state=next
        for(side in 0..1)for(feature in listOf("vent","heat","massage")){
            val name="seat_${if(side==0)"left"else "right"}_${feature}_${if(valid(side,feature))level(side,feature)else 0}_normal"
            control(name,false);control(name,true)
        }
        names().forEach(::load);invalidate();animateIfNeeded()
    }
    private fun animateIfNeeded(){
        val moving=(0..1).any{s->listOf("vent","heat","massage").any{active(s,it)}}
        if(!isAttachedToWindow||!isShown||windowVisibility!=VISIBLE||!moving||!android.animation.ValueAnimator.areAnimatorsEnabled()){animation?.cancel();animation=null;return}
        if(animation?.isActive==true)return
        animation=scope?.launch {while(isActive){delay(50);val nextFrame=frame+1;val previous=frame;frame=nextFrame;val required=names();frame=previous
            withContext(Dispatchers.IO){required.forEach(::load)};frame=nextFrame;invalidate()
        }}
    }
    override fun onAttachedToWindow(){super.onAttachedToWindow();scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate);animateIfNeeded()}
    override fun onDetachedFromWindow(){scope?.cancel();scope=null;animation=null;super.onDetachedFromWindow()}
    override fun onWindowVisibilityChanged(visibility:Int){super.onWindowVisibilityChanged(visibility);if(isAttachedToWindow)animateIfNeeded()}
    override fun onVisibilityChanged(changedView:View,visibility:Int){super.onVisibilityChanged(changedView,visibility);if(isAttachedToWindow)animateIfNeeded()}
    private fun image(c:Canvas,name:String,r:RectF,filter:ColorFilter?=null){val b=cached(name)?:return;paint.shader=null;paint.color=Color.WHITE;paint.colorFilter=filter;c.drawBitmap(b,null,r,paint);paint.colorFilter=null}
    private fun overlay(c:Canvas,key:String,name:String?,r:RectF){
        if(name!=null)cached(name)?.let{fadingArtwork[key]=it}
        val alpha=motion.value(key,if(name!=null)1f else 0f)
        if(alpha<=0f){fadingArtwork.remove(key);return}
        fadingArtwork[key]?.let{b->paint.shader=null;paint.colorFilter=null;paint.color=Color.WHITE;paint.alpha=(alpha*255).toInt();c.drawBitmap(b,null,r,paint);paint.alpha=255}
    }
    override fun onDraw(c:Canvas){
        super.onDraw(c);paint.color=Color.WHITE;paint.style=Paint.Style.FILL;paint.colorFilter=null;c.save();c.scale(width/556f,height/228f);c.clipPath(Path().apply{addRoundRect(RectF(0f,0f,556f,228f),16f,16f,Path.Direction.CW)})
        paint.shader=LinearGradient(0f,0f,556f,228f,if(light)intArrayOf(0xfff7faff.toInt(),0xffeef4fc.toInt())else intArrayOf(0xff35456e.toInt(),0xff1b233a.toInt()),null,Shader.TileMode.CLAMP);c.drawRect(0f,0f,556f,228f,paint);paint.shader=null
        c.save();c.translate(278f,237f);c.scale(1f,.12f);paint.shader=RadialGradient(0f,0f,417f,intArrayOf(if(light)0x356ba5ef else 0x706278b6,0x006278b6),null,Shader.TileMode.CLAMP);c.drawCircle(0f,0f,417f,paint);paint.shader=null;c.restore()
        for(side in 0..1){c.save();c.translate(side*278f+25.02f,0f);c.scale(.82f,.82f);seat(c,side);c.restore()}
        paint.color=if(light)0x2271839e else 0x22bfc9de;c.drawRect(277.5f,178f,278.5f,213f,paint);c.restore()
    }
    private fun seat(c:Canvas,side:Int){
        val s=if(side==0)"left"else "right"
        c.save();c.translate(75f,27f);c.clipRect(if(side==0)-75f else 0f,0f,188f,181f);c.scale(1.1f,1.1f);c.translate(if(side==0)-210f else -353f,-63f)
        c.save();if(side==0)c.clipRect(0f,0f,353.6f,237.6f)
        image(c,"seat_bg_high_level_97c",RectF(0f,0f,633.6f,237.6f),if(light)bright else null)
        for(feature in listOf("vent","heat")){val kind=if(feature=="vent")"vent"else "hot"
            overlay(c,"back-$side-$feature",if(active(side,feature))"seatfront_${kind}${s}_bg_97c"else null,RectF(0f,0f,633.6f,237.6f))
        };c.restore()
        for(feature in listOf("vent","heat")){val x=if(side==0)159.72f else 355.08f;val kind=if(feature=="vent")"vent"else "hot"
            overlay(c,"air-$side-$feature",if(active(side,feature))"seat_${kind}_${s}_${frame%89}"else null,RectF(x,70.95f,x+165f,235.95f))
        }
        for(mode in 1..2){val x=if(side==0)248.49f else 376.53f;c.save();c.rotate(if(side==0)5f else -2f,x+24.75f,138.6f)
            overlay(c,"massage-$side-$mode",if(active(side,"massage")&&level(side,"mode")==mode)"seat_${if(side==0)"main"else "co"}_massage_${if(mode==2)"rolling"else "waving"}_${frame%91}"else null,RectF(x,105.6f,x+49.5f,171.6f));c.restore()
        };c.restore()
        listOf("vent","heat","massage").forEachIndexed{i,f->
            val name="seat_${s}_${f}_${if(valid(side,f))level(side,f)else 0}_normal"
            val rect=RectF(40f+i*66,205f,106f+i*66,271f)
            motion.content("control-$side-$f",name,c,rect){image(c,if(light)"light-$it"else it,rect)}
        }
        val modeAlpha=motion.value("mode-visible-$side",if(active(side,"massage"))1f else 0f)
        if(modeAlpha>0){
        val modeLayer=if(modeAlpha<1f)c.saveLayerAlpha(0f,60f,280f,160f,(modeAlpha*255).toInt())else c.save()
        for(i in 0..1){val x=if(side==0)205f else 7f;val y=70f+i*43.9f;val selected=valid(side,"mode")&&level(side,"mode")==i+1
            paint.color=motion.color("mode-bg-$side-$i",selected,if(light)0xffe1e8f2.toInt()else 0xff222c40.toInt(),if(light)0x1aac4ed0 else 0xff3b3154.toInt());c.drawRoundRect(RectF(x,y,x+66,y+34.9f),18f,18f,paint)
            paint.color=motion.color("mode-outline-$side-$i",selected,0x00ac4ed0,0xffac4ed0.toInt());paint.style=Paint.Style.STROKE;paint.strokeWidth=1f;c.drawRoundRect(RectF(x,y,x+66,y+34.9f),18f,18f,paint);paint.style=Paint.Style.FILL
            paint.color=motion.color("mode-text-$side-$i",selected,if(light)0xff536780.toInt()else 0xffc4ccdc.toInt(),if(light)0xffa346cb.toInt()else 0xffdfb1f4.toInt());paint.typeface=font;paint.textSize=12f;paint.textAlign=Paint.Align.CENTER;c.drawText(if(i==0)"Waving"else "Rolling",x+33,y+22,paint)
        }
        c.restoreToCount(modeLayer)
        }
    }
    override fun onTouchEvent(e:MotionEvent):Boolean{
        val x=e.x*556/width;val y=e.y*228/height
        when(e.actionMasked){MotionEvent.ACTION_DOWN->{downX=x;downY=y;return true};MotionEvent.ACTION_UP->{
            if(abs(x-downX)>20||abs(y-downY)>20)return true
            val side=if(x<278)0 else 1;val localX=(x-side*278-25.02f)/.82f;val localY=y/.82f;val owner=if(side==0)"driver"else "passenger"
            if(localY in 205f..273f&&localX in 40f..238f){val f=listOf("vent","heat","massage")[((localX-40)/66).toInt().coerceIn(0,2)];if(valid(side,f))onCommand("$owner-$f",((level(side,f)+1)%4).toString())}
            val modeX=if(side==0)205f else 7f
            if(active(side,"massage")&&valid(side,"mode")&&localX in modeX..modeX+66){for(i in 0..1)if(localY in (70f+i*43.9f)..(104.9f+i*43.9f))onCommand("$owner-mode",(i+1).toString())}
            performClick();return true
        }};return true
    }
    override fun performClick():Boolean{super.performClick();return true}
}
