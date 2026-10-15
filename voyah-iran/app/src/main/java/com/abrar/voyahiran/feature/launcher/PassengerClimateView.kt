package com.abrar.voyahiran.feature.launcher

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Production and preview share this renderer; coordinates match the approved 770 × 370 card. */
internal class PassengerClimateView(context: Context) : View(context) {
    var light=false
        set(value){if(field!=value){field=value;invalidate()}}
    var onCommand:(String,String)->Unit={_,_->}
    private var state=JSONObject()
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val font=Typeface.createFromAsset(context.assets,"passenger/fonts/Roboto-Regular.ttf")
    private val images=HashMap<String,Bitmap>()
    private var downX=0f;private var downY=0f
    private val buttons=listOf("blow_window","blow_face","blow_foot","ac","switch","auto","front_defrost","rear_defrost","cycle")
    init {isClickable=true;contentDescription="Driver and passenger climate controls"}
    fun update(value:JSONObject){if(state.toString()!=value.toString()){state=value;invalidate()}}
    private fun value(key:String)=if(state.has(key)&&!state.isNull(key))state.optInt(key,-999) else -999
    private fun valid(key:String,range:IntRange)=state.optBoolean("fresh")&&value(key) in range
    private fun color(hex:String)=Color.parseColor(hex)
    private val foreground get()=color(if(light)"#303b4a" else "#ffffff")
    private val secondary get()=color(if(light)"#71839e" else "#bec9e2")
    private fun bitmap(name:String)=images.getOrPut(name){context.assets.open("passenger/oem-ac/$name.png").use{BitmapFactory.decodeStream(it)}}
    private fun image(c:Canvas,name:String,rect:RectF,tint:Int?=null,cover:Boolean=false){
        val b=bitmap(name);val s=if(cover)max(rect.width()/b.width,rect.height()/b.height) else minOf(rect.width()/b.width,rect.height()/b.height)
        val dest=RectF(rect.centerX()-b.width*s/2,rect.centerY()-b.height*s/2,rect.centerX()+b.width*s/2,rect.centerY()+b.height*s/2)
        paint.color=Color.WHITE;paint.alpha=255;paint.shader=null;paint.colorFilter=tint?.let{PorterDuffColorFilter(it,PorterDuff.Mode.SRC_IN)}
        c.drawBitmap(b,null,dest,paint);paint.colorFilter=null
    }
    private fun text(c:Canvas,s:String,x:Float,y:Float,size:Float,tint:Int,center:Boolean=false){
        paint.shader=null;paint.colorFilter=null;paint.color=tint;paint.typeface=font;paint.textSize=size;paint.textAlign=if(center)Paint.Align.CENTER else Paint.Align.LEFT
        c.drawText(s,x,y,paint)
    }
    private fun pill(c:Canvas,r:RectF,tint:Int){paint.shader=null;paint.color=tint;c.drawRoundRect(r,30f,30f,paint)}
    override fun onDraw(c:Canvas){
        super.onDraw(c);c.save();c.scale(width/770f,height/370f)
        c.clipPath(Path().apply{addRoundRect(RectF(0f,0f,770f,370f),16f,16f,Path.Direction.CW)})
        c.drawColor(color(if(light)"#d6e1ed" else "#14191e"))
        // These are the approved, composed artworks. Do not replace a composed
        // light background with the raw OEM drawable of a different geometry.
        image(c,if(light)"cabin-day-fade-v2" else "air_bg_h97c",RectF(0f,0f,770f,370f),cover=true)
        if(light){
            val base=color("#d6e1ed")
            fun fade(x0:Float,y0:Float,x1:Float,y1:Float,alphas:IntArray,stops:FloatArray){
                paint.shader=LinearGradient(x0,y0,x1,y1,alphas.map{(base and 0xffffff) or (it shl 24)}.toIntArray(),stops,Shader.TileMode.CLAMP)
                c.drawRect(0f,0f,770f,370f,paint);paint.shader=null
            }
            fade(0f,0f,770f,0f,intArrayOf(255,245,96,0,0,96,245,255),floatArrayOf(0f,.12f,.20f,.27f,.73f,.80f,.88f,1f))
            fade(0f,370f,0f,0f,intArrayOf(255,245,128,0),floatArrayOf(0f,.29f,.39f,.51f))
            fade(0f,0f,0f,370f,intArrayOf(179,51,0),floatArrayOf(0f,.20f,.36f))
        }
        temperature(c,"Driver","getAirLeftTemperature",67f,96f)
        temperature(c,"Passenger","getAirRightTemperature",646f,674f)
        val sync=valid("getAirDUALStatus",0..1)&&value("getAirDUALStatus")==0
        val syncRect=RectF(55f,240f,166f,273.5f)
        if(light)pill(c,syncRect,color(if(sync)"#cee3fb" else "#dce5f2"))
        else image(c,if(sync)"air_ic_sync_on_normal" else "air_ic_sync_off_normal",syncRect)
        text(c,"SYNC",110.5f,263f,17.65f,if(sync)color("#389cff") else color(if(light)"#536780" else "#bfc9de"),true)
        val fan=if(valid("getAirWindSpeed",0..7))value("getAirWindSpeed") else 0
        val inactive=if(light)color("#718096") else Color.WHITE
        image(c,"air_ic_wind_min",RectF(191f,232f,249f,290f),if(light)inactive else null)
        image(c,"air_ic_wind_max",RectF(501f,232f,559f,290f),if(light)inactive else null)
        for(i in 0..6){paint.color=if(i<fan)color("#389cff") else inactive;c.drawRoundRect(RectF(267f+i*35.8f,249f,273f+i*35.8f,265f),3f,3f,paint)}
        for(group in 0..2){val r=RectF(51f+group*226f,301f,266f+group*226f,351f)
            if(light)pill(c,r,color("#dce5f2")) else image(c,"air_bg_operation",r)
        }
        buttons.forEachIndexed{i,key->
            val active=active(key);val x=82f+(i/3)*226f+(i%3)*69f
            val name=if(key=="cycle")"air_cycle_${if(value("getAirCirculationMode")==1)"outer" else "inner"}_normal" else "air_ic_${key}_${if(active)"on" else "off"}_normal"
            image(c,name,RectF(x-25f,301f,x+25f,351f),if(light&&!active)color("#637c98") else null)
        }
        c.restore()
    }
    private fun temperature(c:Canvas,label:String,key:String,x:Float,labelX:Float){
        text(c,label,labelX,55f,21.36f,secondary,true)
        val n=if(valid(key,155..305))value(key) else null
        fun number(v:Int?,baseline:Float,size:Float,tint:Int){
            val whole=when(v){null->"—";305->"Hi";155->"Lo";else->(v/10).toString()}
            text(c,whole,x,baseline,size,tint)
            if(v!=null&&v%10==5&&v!=305&&v!=155){val width=paint.measureText(whole);text(c,".5",x+width,baseline,size*.46f,tint)}
        }
        number(n?.plus(5)?.takeIf{it<=305},107f,32f,if(light)color("#929eae") else color("#74777a"))
        number(n,171f,48.3f,foreground)
        val unitOffset=if(n!=null&&n%10==5&&n!=155&&n!=305)84f else 64f
        text(c,"℃",x+unitOffset,162f,24.8f,if(light)color("#47566c") else color("#dddddd"))
        number(n?.minus(5)?.takeIf{it>=155},219f,32f,if(light)color("#718096") else color("#74777a"))
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
            MotionEvent.ACTION_DOWN->{downX=x;downY=y;parent?.requestDisallowInterceptTouchEvent(true);return true}
            MotionEvent.ACTION_CANCEL->{parent?.requestDisallowInterceptTouchEvent(false);return true}
            MotionEvent.ACTION_UP->{
                parent?.requestDisallowInterceptTouchEvent(false)
                if(!state.optBoolean("fresh"))return true
                if(downY in 65f..230f && (downX<170 || downX>600)){
                    val key=if(downX<170)"getAirLeftTemperature" else "getAirRightTemperature"
                    if(valid(key,155..305)){
                        val steps=if(abs(y-downY)>15)((y-downY)/55).roundToInt() else if(y<130)1 else if(y>185)-1 else 0
                        if(steps!=0)onCommand(if(downX<170)"driver-temp" else "passenger-temp",(value(key)+steps*5).coerceIn(155,305).toString())
                    }
                }else if(abs(x-downX)<25&&abs(y-downY)<25){
                    when{
                        y in 235f..280f&&x in 55f..166f&&valid("getAirDUALStatus",0..1)->onCommand("sync",if(value("getAirDUALStatus")==0)"0" else "1")
                        y in 232f..290f&&x in 191f..559f&&valid("getAirWindSpeed",0..7)->{
                            val n=if(x<249)value("getAirWindSpeed")-1 else if(x>501)value("getAirWindSpeed")+1 else ((x-252)/250*7).roundToInt()
                            onCommand("fan",n.coerceIn(0,7).toString())
                        }
                        y in 301f..351f->{buttons.forEachIndexed{i,key->val center=82f+(i/3)*226f+(i%3)*69f
                            if(abs(x-center)<30)when{
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
