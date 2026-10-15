package com.abrar.voyahiran.feature.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.widget.*

/** Only entries not already pinned are supplied; no search, close or done button. */
internal class PassengerAppPickerView(context:Context,apps:List<LauncherAppsEntry>,light:Boolean,scale:Float,onSelect:(String)->Unit):LinearLayout(context){
    init {
        fun px(n:Int)=(n*scale).toInt()
        orientation=VERTICAL;setPadding(px(30),px(24),px(30),px(24))
        background=GradientDrawable().apply{cornerRadius=px(20).toFloat();setColor(if(light)0xfff4f8fe.toInt()else 0xff273653.toInt())}
        val foreground=if(light)0xff293543.toInt()else Color.WHITE
        val font=Typeface.createFromAsset(context.assets,"passenger/fonts/Roboto-Regular.ttf")
        addView(TextView(context).apply{text="Choose an app";typeface=font;setTextColor(foreground);setTextSize(TypedValue.COMPLEX_UNIT_PX,28*scale)},LayoutParams(-1,px(60)))
        val grid=LinearLayout(context).apply{orientation=VERTICAL}
        apps.chunked(3).forEach{row->
            val line=LinearLayout(context)
            row.forEach{app->line.addView(object:android.view.View(context){
                private val icon=app.icon?.constantState?.newDrawable(resources)?:app.icon
                private val labelPaint=android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply{
                    typeface=if(app.label.any{it in '\u0600'..'\u06ff'})Typeface.createFromAsset(context.assets,"passenger/fonts/iransansx_regular.ttf")else font
                    color=foreground;textSize=21*scale;textAlign=android.graphics.Paint.Align.CENTER
                }
                init{isFocusable=true;contentDescription=app.label;setOnClickListener{onSelect(app.id)}}
                override fun onDraw(canvas:android.graphics.Canvas){
                    super.onDraw(canvas)
                    val center=width/2f;canvas.save()
                    canvas.clipPath(android.graphics.Path().apply{addRoundRect(android.graphics.RectF(center-px(42),px(12).toFloat(),center+px(42),px(96).toFloat()),px(18).toFloat(),px(18).toFloat(),android.graphics.Path.Direction.CW)})
                    icon?.let{val old=android.graphics.Rect(it.bounds);val ratio=84*scale/maxOf(it.intrinsicWidth.coerceAtLeast(1),it.intrinsicHeight.coerceAtLeast(1));val w=it.intrinsicWidth.coerceAtLeast(1)*ratio;val h=it.intrinsicHeight.coerceAtLeast(1)*ratio;it.setBounds((center-w/2).toInt(),(54*scale-h/2).toInt(),(center+w/2).toInt(),(54*scale+h/2).toInt());it.draw(canvas);it.bounds=old};canvas.restore()
                    canvas.drawText(TextUtils.ellipsize(app.label,labelPaint,(width-px(16)).toFloat(),TextUtils.TruncateAt.END).toString(),center,130*scale,labelPaint)
                }
            },LayoutParams(0,px(150),1f))}
            repeat(3-row.size){line.addView(Space(context),LayoutParams(0,px(150),1f))}
            grid.addView(line)
        }
        if(apps.isEmpty())grid.addView(TextView(context).apply{text="No more apps";setTextColor(foreground);gravity=Gravity.CENTER;setTextSize(TypedValue.COMPLEX_UNIT_PX,22*scale)})
        addView(ScrollView(context).apply{isFillViewport=true;addView(grid)},LayoutParams(-1,0,1f))
    }
}
