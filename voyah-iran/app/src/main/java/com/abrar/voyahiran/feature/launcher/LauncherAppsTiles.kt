package com.abrar.voyahiran.feature.launcher

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.widget.*
import com.abrar.voyahiran.R

/** Detached, listener-free tiles. Warm one per main-loop turn, never create Views on IO. */
internal object LauncherAppsTiles {
    data class Tile(val view:LinearLayout,val badge:TextView?)
    private data class Key(val display:Int,val width:Int,val light:Boolean,val entry:LauncherAppsEntry)
    private val prepared=mutableMapOf<Key,Tile>()
    private val handler=Handler(Looper.getMainLooper())
    private var generation=0
    fun invalidate(){generation++;prepared.clear()}
    fun warm(context:Context,entries:List<LauncherAppsEntry>,light:Boolean) {
        invalidate()
        val revision=generation
        val app=context.applicationContext
        val displays=app.getSystemService(DisplayManager::class.java)
        val tasks=listOf(0,1).flatMap {id->
            val display=displays?.getDisplay(id) ?: return@flatMap emptyList()
            val c=ContextThemeWrapper(app.createDisplayContext(display),R.style.Theme_VoyahLauncher)
            entries.map {Key(id,c.resources.displayMetrics.widthPixels,light,it) to c}
        }.iterator()
        fun next() {
            handler.post {
                if(revision!=generation)return@post
                if(!tasks.hasNext()) {
                    com.abrar.voyahiran.core.logging.AppLog.info("LauncherApps","detached tiles prepared total=${prepared.size} light=$light")
                    return@post
                }
                val (key,c)=tasks.next()
                runCatching {
                    create(c,key.entry,key.light).also {tile->
                        val w=(key.width*1775/1920/9).coerceAtLeast(1)
                        val h=((c.resources.displayMetrics.heightPixels-50-28)/3).coerceAtLeast(1)
                        tile.view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY))
                        tile.view.layout(0,0,w,h)
                    }
                }.onSuccess {prepared[key]=it}.onFailure {
                    com.abrar.voyahiran.core.logging.AppLog.error("LauncherApps","tile preparation failed display=${key.display} id=${key.entry.id}",it)
                }
                next()
            }
        }
        next()
    }
    fun take(context:Context,display:Int,entry:LauncherAppsEntry,light:Boolean):Tile =
        prepared.remove(Key(display,context.resources.displayMetrics.widthPixels,light,entry)) ?: create(context,entry,light)

    private fun create(c:Context,entry:LauncherAppsEntry,light:Boolean):Tile {
        val tile=LinearLayout(c).apply {
            orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;isFocusable=true;isClickable=true
            contentDescription=entry.label
            val value=android.util.TypedValue();c.theme.resolveAttribute(android.R.attr.selectableItemBackground,value,true)
            if(value.resourceId!=0)setBackgroundResource(value.resourceId)
        }
        val size=(112f*c.resources.displayMetrics.widthPixels/1920).toInt().coerceAtLeast(56)
        val box=FrameLayout(c).apply {clipChildren=false;clipToPadding=false}
        box.addView(ImageView(c).apply {
            setImageDrawable(entry.icon?.constantState?.newDrawable(c.resources)?.mutate() ?: entry.icon)
            scaleType=ImageView.ScaleType.FIT_CENTER
        },FrameLayout.LayoutParams(size,size).apply {leftMargin=22;topMargin=22})
        val badge=if(entry.component!=null && entry.component.packageName!=c.packageName)TextView(c).apply {
            text="⋯";gravity=Gravity.CENTER;contentDescription="Options for ${entry.label}"
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,28f)
            setTextColor(if(light)0xff303947.toInt() else 0xffeef3fb.toInt())
            background=android.graphics.drawable.GradientDrawable().apply {
                shape=android.graphics.drawable.GradientDrawable.OVAL
                setColor(if(light)0xfff2f6fc.toInt() else 0xff26364f.toInt());setStroke(1,0xff657995.toInt())
            }
            visibility=View.GONE;isFocusable=true
        }.also {box.addView(it,FrameLayout.LayoutParams(44,44,Gravity.TOP or Gravity.RIGHT))} else null
        tile.addView(box,LinearLayout.LayoutParams(size+44,size+22))
        tile.addView(TextView(c).apply {
            text=entry.label
            if(entry.label.any {it in '\u0600'..'\u06ff'})typeface=c.resources.getFont(com.abrar.raha.feedback.R.font.iransansx_regular)
            setTextColor(if(light)0xff303947.toInt() else 0xffeef3fb.toInt())
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,(25f*c.resources.displayMetrics.widthPixels/1920).coerceAtLeast(14f))
            setSingleLine(true);ellipsize=android.text.TextUtils.TruncateAt.END;gravity=Gravity.CENTER;setPadding(5,14,5,0)
        },LinearLayout.LayoutParams(-1,-2))
        return Tile(tile,badge)
    }
}
