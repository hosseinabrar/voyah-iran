package com.abrar.voyahiran.feature.launcher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

/** Five editable shortcuts and a fixed drawer entry; shared by production and native render tests. */
internal class PassengerFavoritesView(context:Context):View(context) {
    private val motion=LauncherMotion(this)
    private val previousEntries=HashMap<String,LauncherAppsEntry>()
    var light=false
        set(v){if(field!=v){field=v;invalidate()}}
    var onOpen:(String)->Unit={}
    var onAllApps:()->Unit={}
    var onPick:(Int)->Unit={}
    var onSave:(List<String>)->Unit={}
    private var catalog=emptyList<LauncherAppsEntry>()
    private var ids=List(5){""}
    var editing=false;private set
    private var wobble:ValueAnimator?=null
    private var angle=0f
    private var down=-1
    private var downX=0f;private var downY=0f
    private var dragX=0f;private var dragY=0f
    private var dragging=false
    private var held=false
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val staticArtwork=StaticLauncherArtwork(this)
    private val textPaint=TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val font=Typeface.createFromAsset(context.assets,"passenger/fonts/Roboto-Regular.ttf")
    private val persian=Typeface.createFromAsset(context.assets,"passenger/fonts/iransansx_regular.ttf")
    private val hold=Runnable{if(down in 0..4){held=true;beginEdit()}}
    init{isClickable=true;contentDescription="Favorite apps"}
    fun bind(apps:List<LauncherAppsEntry>,favorites:List<String>){
        motion.updated()
        previousEntries.clear();catalog.filter{it.id in ids}.forEach{previousEntries[it.id]=it}
        catalog=apps;ids=List(5){i->favorites.getOrNull(i)?.takeIf{id->apps.any{it.id==id}}.orEmpty()};invalidate()
    }
    fun selections()=ids.toList()
    fun availableApps()=catalog.filter{it.id !in ids}
    fun select(index:Int,id:String){if(index !in 0..4||id in ids||catalog.none{it.id==id})return;ids=ids.toMutableList().apply{set(index,id)};onSave(ids);invalidate()}
    fun beginEdit(){editing=true;updateWobble();invalidate()}
    fun dismissEdit(){editing=false;down=-1;dragging=false;removeCallbacks(hold);updateWobble();invalidate()}
    private fun updateWobble(){
        if(editing&&isAttachedToWindow&&isShown&&windowVisibility==VISIBLE&&ValueAnimator.areAnimatorsEnabled()){
            if(wobble==null)wobble=ValueAnimator.ofFloat(-1.4f,1.4f).apply{duration=150;repeatMode=ValueAnimator.REVERSE;repeatCount=ValueAnimator.INFINITE;addUpdateListener{angle=it.animatedValue as Float;invalidate()};start()}
        }else{wobble?.cancel();wobble=null;angle=0f}
    }
    override fun onDetachedFromWindow(){dismissEdit();super.onDetachedFromWindow()}
    override fun onWindowVisibilityChanged(visibility:Int){super.onWindowVisibilityChanged(visibility);if(visibility!=VISIBLE)dismissEdit() else updateWobble()}
    private fun cx(i:Int)=if(i%2==0)145f else 399f
    private fun cy(i:Int)=151f+(i/2)*191f
    private fun hit(x:Float,y:Float)=(0..5).firstOrNull{
        if(editing&&it<5) (abs(x-cx(it))<=58&&abs(y-cy(it))<=58) ||
            (ids[it].isNotEmpty()&&hypot(x-(cx(it)+57),y-(cy(it)-57))<=26)
        else abs(x-cx(it))<=100&&y in cy(it)-72..cy(it)+101
    }?:-1
    private fun label(c:Canvas,text:String,x:Float,y:Float,size:Float,max:Float){
        textPaint.typeface=if(text.any{it in '\u0600'..'\u06ff'})persian else font
        textPaint.textSize=size;textPaint.color=if(light)0xff293543.toInt() else 0xffe6edfa.toInt();textPaint.textAlign=Paint.Align.CENTER
        c.drawText(TextUtils.ellipsize(text,textPaint,max,TextUtils.TruncateAt.END).toString(),x,y,textPaint)
    }
    override fun onDraw(c:Canvas){
        super.onDraw(c);c.save();c.scale(width/546f,height/662f)
        c.clipPath(Path().apply{addRoundRect(RectF(0f,0f,546f,662f),16f,16f,Path.Direction.CW)})
        staticArtwork.draw(c,"favorites-${if(light)"light"else "dark"}",RectF(0f,0f,546f,662f))
        textPaint.typeface=font;textPaint.textSize=30f
        label(c,"Favorite apps",30f+textPaint.measureText("Favorite apps")/2,55f,30f,300f)
        for(i in 0..5)if(!(dragging&&i==down))tile(c,i,cx(i),cy(i))
        if(dragging&&down in 0..4)tile(c,down,dragX,dragY)
        c.restore()
    }
    private fun tile(c:Canvas,i:Int,x:Float,y:Float){
        motion.content("slot-$i",if(i==5)"all"else ids[i],c,RectF(x-112,y-80,x+112,y+103)){id->tileContent(c,i,id,x,y)}
    }
    private fun tileContent(c:Canvas,i:Int,id:String,x:Float,y:Float){
        c.save();if(editing&&i<5)c.rotate(if(i%2==0)angle else -angle,x,y)
        val app=if(i<5)catalog.firstOrNull{it.id==id}?:previousEntries[id]else null
        if(i==5){
            staticArtwork.draw(c,"all-apps-${if(light)"light"else "dark"}",RectF(x-43,y-43,x+43,y+43))
        }else if(app!=null){app.icon?.let{drawIcon(c,it,x,y)}}else{
            paint.color=if(light)0xff6e90bd.toInt()else 0xffb8cbed.toInt();paint.strokeWidth=5f;paint.strokeCap=Paint.Cap.ROUND
            c.drawLine(x-30,y,x+30,y,paint);c.drawLine(x,y-30,x,y+30,paint)
        }
        label(c,if(i==5)"All apps" else app?.label?:"Add",x,y+92,24f,220f)
        val editAlpha=motion.value("edit-$i",if(editing&&i<5)1f else 0f)
        if(editAlpha>0f&&app!=null){
            val layer=if(editAlpha<1f)c.saveLayerAlpha(x+37,y-77,x+77,y-37,(editAlpha*255).toInt())else c.save()
            val bx=x+57;val by=y-57;paint.color=if(light)0xfff2f6fc.toInt()else 0xff26364f.toInt();c.drawCircle(bx,by,18f,paint)
            paint.color=0xff657995.toInt();paint.style=Paint.Style.STROKE;paint.strokeWidth=1f;c.drawCircle(bx,by,18f,paint);paint.style=Paint.Style.FILL
            paint.color=if(light)0xff303947.toInt()else 0xffeef3fb.toInt();paint.strokeWidth=2.5f;paint.strokeCap=Paint.Cap.ROUND;c.drawLine(bx-5,by-5,bx+5,by+5,paint);c.drawLine(bx+5,by-5,bx-5,by+5,paint)
            c.restoreToCount(layer)
        };c.restore()
    }
    private fun drawIcon(c:Canvas,icon:Drawable,x:Float,y:Float){val old=Rect(icon.bounds);val w=icon.intrinsicWidth.coerceAtLeast(1);val h=icon.intrinsicHeight.coerceAtLeast(1);val scale=116f/max(w,h);c.save();c.clipPath(Path().apply{addRoundRect(RectF(x-58,y-58,x+58,y+58),24f,24f,Path.Direction.CW)});icon.setBounds((x-w*scale/2).roundToInt(),(y-h*scale/2).roundToInt(),(x+w*scale/2).roundToInt(),(y+h*scale/2).roundToInt());icon.draw(c);icon.bounds=old;c.restore()}
    override fun onTouchEvent(e:MotionEvent):Boolean{
        val x=e.x*546/width;val y=e.y*662/height
        when(e.actionMasked){
            MotionEvent.ACTION_DOWN->{down=hit(x,y);downX=x;downY=y;dragX=x;dragY=y;held=false;dragging=false
                if(down<0){dismissEdit();return true}
                if(down in 0..4)postDelayed(hold,550);return true}
            MotionEvent.ACTION_MOVE->{if(hypot(x-downX,y-downY)>12){removeCallbacks(hold);if(editing&&down in 0..4){dragging=true;dragX=x;dragY=y;invalidate()}};return true}
            MotionEvent.ACTION_CANCEL->{removeCallbacks(hold);down=-1;dragging=false;invalidate();return true}
            MotionEvent.ACTION_UP->{removeCallbacks(hold);val target=hit(x,y)
                if(dragging&&down in 0..4&&target in 0..4){ids=ids.toMutableList().apply{val a=get(down);set(down,get(target));set(target,a)};onSave(ids)}
                else if(!held&&hypot(x-downX,y-downY)<12&&target==down){
                    when{
                        down==5->{if(editing)dismissEdit() else onAllApps()}
                        down in 0..4->{val i=down
                            if(editing&&ids[i].isNotEmpty()&&hypot(x-(cx(i)+57),y-(cy(i)-57))<=26){ids=ids.toMutableList().apply{set(i,"")};onSave(ids)}
                            else if(editing)dismissEdit()
                            else if(ids[i].isEmpty())onPick(i) else onOpen(ids[i])
                        }
                    }
                };down=-1;dragging=false;performClick();invalidate();return true
            }
        };return true
    }
    override fun performClick():Boolean{super.performClick();return true}
}
