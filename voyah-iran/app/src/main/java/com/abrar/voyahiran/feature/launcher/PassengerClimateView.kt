package com.abrar.voyahiran.feature.launcher

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import android.util.AttributeSet
import com.abrar.voyahiran.R
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Production and preview share this renderer; coordinates match the approved 770 × 370 card. */
internal class PassengerClimateView @JvmOverloads constructor(context: Context, attrs:AttributeSet?=null) : View(context,attrs) {
    var light=false
        set(value){if(field!=value){field=value;invalidate()}}
    var onCommand:(String,String)->Unit={_,_->}
    private var state=JSONObject()
    private val motion=LauncherMotion(this)
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val colors=android.animation.ArgbEvaluator()
    private val font=Typeface.createFromAsset(context.assets,"passenger/fonts/Roboto-Regular.ttf")
    private val images=HashMap<String,Bitmap>()
    private var downX=0f;private var downY=0f
    private val wheels=Array(2){side->PassengerTemperatureWheel(this){n->
        if(valid(if(side==0)"getAirLeftTemperature" else "getAirRightTemperature",155..305))
            onCommand(if(side==0)"driver-temp" else "passenger-temp",n.toString())
    }}
    private var touchedWheel:Int?=null
    private var airflow:ValueAnimator?=null
    private var airflowTime=0L
    private val blowing get()=valid("getAirSWStatus",0..1)&&value("getAirSWStatus")==1&&valid("getAirWindSpeed",1..7)&&valid("getAirSupplyStatus",0..6)
    private val buttons=listOf("blow_window","blow_face","blow_foot","ac","switch","auto","front_defrost","rear_defrost","cycle")
    init {
        isClickable=true;contentDescription="Driver and passenger climate controls"
        if(isInEditMode){
            light=attrs?.getAttributeValue("http://schemas.android.com/apk/res/android","tag")=="preview-light"
            state=JSONObject().put("fresh",true).put("getAirSWStatus",1).put("getAirWindSpeed",3)
                .put("getAirSupplyStatus",6).put("getAirLeftTemperature",220).put("getAirRightTemperature",230)
                .put("getAirACStatus",1).put("getAirDUALStatus",1)
            wheels[0].sync(220);wheels[1].sync(230);airflowTime=1400L
        }
    }
    fun update(value:JSONObject){if(state.toString()!=value.toString()){
        motion.updated();state=value
        wheels.forEachIndexed{i,w->val key=if(i==0)"getAirLeftTemperature" else "getAirRightTemperature"
            if(!valid(key,155..305))w.cancel() else w.sync(this.value(key))}
        animateAirflow();invalidate()
    }}
    private fun animateAirflow(){
        if(isInEditMode)return
        if(isAttachedToWindow&&isShown&&windowVisibility==VISIBLE&&blowing&&ValueAnimator.areAnimatorsEnabled()){
            if(airflow==null)airflow=ValueAnimator.ofInt(0,7499).apply{duration=7500;interpolator=LinearInterpolator();repeatCount=ValueAnimator.INFINITE
                addUpdateListener{val t=(it.animatedValue as Int).toLong();if(t/50!=airflowTime/50){airflowTime=t;invalidate()}};start()}
        }else{airflow?.cancel();airflow=null;airflowTime=0}
    }
    override fun onAttachedToWindow(){super.onAttachedToWindow();animateAirflow()}
    override fun onWindowVisibilityChanged(v:Int){super.onWindowVisibilityChanged(v);animateAirflow()}
    override fun onVisibilityChanged(v:View,visibility:Int){super.onVisibilityChanged(v,visibility);animateAirflow();if(!isShown)cancelWheels()}
    private fun cancelWheels(){wheels.forEachIndexed{i,w->w.cancel();val n=value(if(i==0)"getAirLeftTemperature" else "getAirRightTemperature");if(n in 155..305)w.sync(n)};touchedWheel=null}
    override fun onDetachedFromWindow(){airflow?.cancel();airflow=null;cancelWheels();super.onDetachedFromWindow()}
    override fun computeScroll(){super.computeScroll();wheels.forEach{it.advance()}}
    private fun value(key:String)=if(state.has(key)&&!state.isNull(key))state.optInt(key,-999) else -999
    private fun valid(key:String,range:IntRange)=state.optBoolean("fresh")&&value(key) in range
    private fun color(hex:String)=Color.parseColor(hex)
    private val foreground get()=color(if(light)"#303b4a" else "#ffffff")
    private val secondary get()=color(if(light)"#71839e" else "#bec9e2")
    private val lightBackground get()=color("#c7d7e6") // Silver-blue, deliberately distinct from white cards.
    private fun artwork(path:String):Bitmap?=images[path] ?: (
        if(isInEditMode)context.assets.open(path).use{BitmapFactory.decodeStream(it)}
        else PassengerArtworkCache.get(context,path,this)
    )?.also{images[path]=it}
    private fun bitmap(name:String):Bitmap?=artwork("passenger/oem-ac/$name.png")
    private fun image(c:Canvas,name:String,rect:RectF,tint:Int?=null,cover:Boolean=false){
        val b=bitmap(name) ?: return;val s=if(cover)max(rect.width()/b.width,rect.height()/b.height) else minOf(rect.width()/b.width,rect.height()/b.height)
        val dest=RectF(rect.centerX()-b.width*s/2,rect.centerY()-b.height*s/2,rect.centerX()+b.width*s/2,rect.centerY()+b.height*s/2)
        paint.color=Color.WHITE;paint.alpha=255;paint.shader=null;paint.colorFilter=tint?.let{PorterDuffColorFilter(it,PorterDuff.Mode.SRC_IN)}
        c.drawBitmap(b,null,dest,paint);paint.colorFilter=null
    }
    private fun text(c:Canvas,s:String,x:Float,y:Float,size:Float,tint:Int,center:Boolean=false){
        paint.shader=null;paint.colorFilter=null;paint.color=tint;paint.typeface=font;paint.textSize=size;paint.textAlign=if(center)Paint.Align.CENTER else Paint.Align.LEFT
        c.drawText(s,x,y,paint)
    }
    private fun pill(c:Canvas,r:RectF,tint:Int){paint.shader=null;paint.color=tint;c.drawRoundRect(r,30f,30f,paint)}
    private fun drawAirflow(c:Canvas){
        if(!blowing)return
        fun frame(name:String,rect:RectF){
            val path="passenger/airflow/$name.png"
            val b=artwork(path)?:return
            paint.shader=null;paint.color=Color.WHITE;paint.alpha=if(light)180 else 215
            paint.colorFilter=PorterDuffColorFilter(if(light)0xff479cd5.toInt() else 0xffb8e6ff.toInt(),PorterDuff.Mode.SRC_IN)
            c.drawBitmap(b,null,rect,paint);paint.colorFilter=null;paint.alpha=255
        }
        if(active("blow_face")){
            val n=(airflowTime/100%75).toInt()
            frame("face-left-$n",airRect(R.integer.air_face_left_x,R.integer.air_face_left_y,R.integer.air_face_left_width,R.integer.air_face_left_height))
            frame("face-center-$n",airRect(R.integer.air_face_center_x,R.integer.air_face_center_y,R.integer.air_face_center_width,R.integer.air_face_center_height))
            frame("face-right-$n",airRect(R.integer.air_face_right_x,R.integer.air_face_right_y,R.integer.air_face_right_width,R.integer.air_face_right_height))
        }
        if(active("blow_window"))frame("window-${airflowTime/50%30}",airRect(R.integer.air_window_x,R.integer.air_window_y,R.integer.air_window_width,R.integer.air_window_height))
        if(active("blow_foot")){
            frame("foot-left",airRect(R.integer.air_foot_left_x,R.integer.air_foot_left_y,R.integer.air_foot_left_width,R.integer.air_foot_left_height))
            frame("foot-right",airRect(R.integer.air_foot_right_x,R.integer.air_foot_right_y,R.integer.air_foot_right_width,R.integer.air_foot_right_height))
        }
    }
    private fun airRect(x:Int,y:Int,w:Int,h:Int):RectF {
        val left=resources.getInteger(x).toFloat();val top=resources.getInteger(y).toFloat()
        return RectF(left,top,left+resources.getInteger(w),top+resources.getInteger(h))
    }
    override fun onDraw(c:Canvas){
        super.onDraw(c);c.save();c.scale(width/770f,height/370f)
        c.clipPath(Path().apply{addRoundRect(RectF(0f,0f,770f,370f),16f,16f,Path.Direction.CW)})
        c.drawColor(if(light)lightBackground else color("#14191e"))
        // These are the approved, composed artworks. Do not replace a composed
        // light background with the raw OEM drawable of a different geometry.
        image(c,if(light)"cabin-day-fade-v2" else "air_bg_h97c",RectF(0f,0f,770f,370f),cover=true)
        if(light){
            val base=lightBackground
            fun fade(x0:Float,y0:Float,x1:Float,y1:Float,alphas:IntArray,stops:FloatArray){
                paint.shader=LinearGradient(x0,y0,x1,y1,alphas.map{(base and 0xffffff) or (it shl 24)}.toIntArray(),stops,Shader.TileMode.CLAMP)
                c.drawRect(0f,0f,770f,370f,paint);paint.shader=null
            }
            fade(0f,0f,770f,0f,intArrayOf(255,245,96,0,0,96,245,255),floatArrayOf(0f,.12f,.20f,.27f,.73f,.80f,.88f,1f))
            fade(0f,370f,0f,0f,intArrayOf(255,245,128,0),floatArrayOf(0f,.29f,.39f,.51f))
            fade(0f,0f,0f,370f,intArrayOf(179,51,0),floatArrayOf(0f,.20f,.36f))
        }
        drawAirflow(c)
        temperature(c,"Driver","getAirLeftTemperature",67f,96f)
        temperature(c,"Passenger","getAirRightTemperature",646f,674f)
        val sync=valid("getAirDUALStatus",0..1)&&value("getAirDUALStatus")==0
        val syncRect=RectF(55f,240f,166f,273.5f)
        if(light)pill(c,syncRect,motion.color("sync-bg",sync,color("#dce5f2"),color("#cee3fb")))
        else motion.content("sync",if(sync)"air_ic_sync_on_normal" else "air_ic_sync_off_normal",c,syncRect){image(c,it,syncRect)}
        text(c,"Front Sync",110.5f,263f,17.65f,motion.color("sync-text",sync,color(if(light)"#536780" else "#bfc9de"),color("#389cff")),true)
        val fan=if(valid("getAirWindSpeed",0..7))value("getAirWindSpeed") else 0
        val inactive=if(light)color("#718096") else Color.WHITE
        // Optical alignment with the fan bars (y=257), not the OEM PNG bounds.
        c.save();c.scale(1.1f,1.1f,375f,257f)
        image(c,"air_ic_wind_min",RectF(191f,228f,249f,286f),if(light)inactive else null)
        image(c,"air_ic_wind_max",RectF(501f,228f,559f,286f),if(light)inactive else null)
        for(i in 0..6){paint.color=motion.color("fan-$i",i<fan,inactive,color("#389cff"));c.drawRoundRect(RectF(267f+i*35.8f,249f,273f+i*35.8f,265f),3f,3f,paint)}
        c.restore()
        c.save();c.scale(1.1f,1.1f,384.5f,326f)
        for(group in 0..2){val r=RectF(51f+group*226f,301f,266f+group*226f,351f)
            if(light)pill(c,r,color("#dce5f2")) else bitmap("air_bg_operation")?.let{background->
                // The OEM 480x80 background must fill the control bounds, not fit
                // its aspect ratio (which left the orange indicators outside).
                paint.shader=null;paint.colorFilter=null;paint.color=Color.WHITE
                c.drawBitmap(background,null,r,paint)
            }
        }
        buttons.forEachIndexed{i,key->
            val active=active(key);val x=82f+(i/3)*226f+(i%3)*69f
            val name=if(key=="cycle")"air_cycle_${if(value("getAirCirculationMode")==1)"outer" else "inner"}_normal" else "air_ic_${key}_${if(active)"on" else "off"}_normal"
            val rect=RectF(x-25f,301f,x+25f,351f)
            motion.content("button-$key","$name|$active",c,rect){token->
                image(c,token.substringBefore('|'),rect,if(light&&token.endsWith("|false"))color("#637c98") else null)
            }
        }
        c.restore()
        c.restore()
    }
    private fun temperature(c:Canvas,label:String,key:String,x:Float,labelX:Float){
        text(c,label,labelX,55f,21.36f,secondary,true)
        val n=if(valid(key,155..305))value(key) else null
        fun number(v:Int?,baseline:Float,size:Float,tint:Int){
            val whole=when(v){null->"—";305->"Hi";155->"Lo";else->(v/10).toString()}
            text(c,whole,x,baseline,size,tint)
            if(v!=null&&v!=305&&v!=155){val width=paint.measureText(whole);text(c,".${v%10}",x+width,baseline,size*.46f,tint)}
        }
        val position=wheels[if(key=="getAirLeftTemperature")0 else 1].position
        c.save();c.clipRect(x-3,75f,x+76f,230f)
        if(n==null)number(null,171f,48.3f,foreground) else for(index in 0..30){
            val distance=position-index
            if(abs(distance)>1.6f)continue
            val emphasis=(1-abs(distance)).coerceIn(0f,1f)
            val dim=if(light)color("#718096") else color("#74777a")
            val tint=colors.evaluate(emphasis,dim,foreground) as Int
            number(155+index*5,171f+distance*56f,32f+16.3f*emphasis,tint)
        };c.restore()
        val unitOffset=if(n!=null&&n!=155&&n!=305)76f else 64f
        text(c,"℃",x+unitOffset,171f,24.8f,foreground)
    }
    private fun active(key:String):Boolean=when(key){
        "blow_face"->value("getAirSupplyStatus") in listOf(0,1,6)
        "blow_foot"->value("getAirSupplyStatus") in listOf(1,2,3,6)
        "blow_window"->value("getAirSupplyStatus") in listOf(3,4,6)
        "cycle"->value("getAirCirculationMode") in listOf(0,2)
        else->value(field(key))==1
    }
    private fun field(key:String)=when(key){"ac"->"getAirACStatus";"switch"->"getAirSWStatus";"auto"->"getAirMode";"front_defrost"->"getAirFrontWindowDefogger";"rear_defrost"->"getAirRearWindowDefogger";else->""}
    override fun onTouchEvent(e:MotionEvent):Boolean{
        val x=e.x*770/width;val y=e.y*370/height
        when(e.actionMasked){
            MotionEvent.ACTION_DOWN->{downX=x;downY=y;parent?.requestDisallowInterceptTouchEvent(true)
                touchedWheel=if(y in 65f..230f&&(x<170||x>600))if(x<170)0 else 1 else null
                touchedWheel?.let{i->if(valid(if(i==0)"getAirLeftTemperature" else "getAirRightTemperature",155..305))wheels[i].down(e,y) else touchedWheel=null};return true}
            MotionEvent.ACTION_MOVE->{touchedWheel?.let{wheels[it].move(e,y)};return true}
            MotionEvent.ACTION_CANCEL->{cancelWheels();invalidate();parent?.requestDisallowInterceptTouchEvent(false);return true}
            MotionEvent.ACTION_UP->{
                parent?.requestDisallowInterceptTouchEvent(false)
                touchedWheel?.let{i->touchedWheel=null;if(state.optBoolean("fresh"))wheels[i].up(e,y) else cancelWheels();performClick();return true}
                if(!state.optBoolean("fresh"))return true
                if(abs(x-downX)<25&&abs(y-downY)<25){
                    val fanX=375f+(x-375f)/1.1f;val fanY=257f+(y-257f)/1.1f
                    val buttonX=384.5f+(x-384.5f)/1.1f;val buttonY=326f+(y-326f)/1.1f
                    when{
                        y in 235f..280f&&x in 55f..166f&&valid("getAirDUALStatus",0..1)->onCommand("sync",if(value("getAirDUALStatus")==0)"0" else "1")
                        fanY in 228f..286f&&fanX in 191f..559f&&valid("getAirWindSpeed",0..7)->{
                            val n=if(fanX<249)value("getAirWindSpeed")-1 else if(fanX>501)value("getAirWindSpeed")+1 else ((fanX-252)/250*7).roundToInt()
                            onCommand("fan",n.coerceIn(0,7).toString())
                        }
                        buttonY in 301f..351f->{buttons.forEachIndexed{i,key->val center=82f+(i/3)*226f+(i%3)*69f
                            if(abs(buttonX-center)<30)when{
                                key.startsWith("blow_")&&valid("getAirSupplyStatus",0..6)->onCommand("airflow",when(key){"blow_face"->"face";"blow_foot"->"feet";else->"windshield"})
                                key=="cycle"&&valid("getAirCirculationMode",0..2)->onCommand("recirculation",((value("getAirCirculationMode")+1)%3).toString())
                                valid(field(key),0..1)->onCommand(if(key=="switch")"power" else key,if(active(key))"0" else "1")
                            }
                        }}
                    }
                }
                performClick();return true
            }
        }
        return true
    }
    override fun performClick():Boolean{super.performClick();return true}
}
