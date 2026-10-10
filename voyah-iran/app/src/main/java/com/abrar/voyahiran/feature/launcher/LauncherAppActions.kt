package com.abrar.voyahiran.feature.launcher

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Uses the same CCC popup palette and rounded geometry as LauncherDrivePopup. */
internal class LauncherAppActions(private val host:FrameLayout, private val light:Boolean) {
    private var overlay:FrameLayout?=null
    private val oemFont by lazy {android.graphics.Typeface.createFromAsset(host.context.assets,"launcher/ccc/actions/semibold.ttf")}
    private fun darkCancel():android.graphics.drawable.StateListDrawable {
        fun image(state:String)=host.context.assets.open("launcher/ccc/actions/button_380_black_${state}_a.png").use {android.graphics.drawable.Drawable.createFromStream(it,null)!!}
        return android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed),image("pre"))
            addState(intArrayOf(),image("nor"))
        }
    }
    private fun pill(destructive:Boolean=false)=RippleDrawable(
        ColorStateList.valueOf(0x33ffffff),
        GradientDrawable().apply {
            cornerRadius=px(26).toFloat()
            setColor(if(destructive)removeColor else if(light)0xffcbd8e9.toInt() else 0xff3c4c65.toInt())
        },null)
    private val scale get()=(host.resources.displayMetrics.widthPixels/1920f).coerceAtLeast(.65f)
    private fun px(v:Int)=(v*scale).toInt()
    private val removeColor get()=if(light)0xffff3b30.toInt() else 0xffff453a.toInt()
    private fun actionIcon(remove:Boolean):android.graphics.drawable.Drawable = object:android.graphics.drawable.Drawable() {
        private val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color=if(remove)removeColor else textColor;strokeWidth=1.8f;strokeCap=android.graphics.Paint.Cap.ROUND;strokeJoin=android.graphics.Paint.Join.ROUND
        }
        override fun draw(canvas:android.graphics.Canvas) {
            canvas.save();canvas.translate(bounds.left.toFloat(),bounds.top.toFloat());canvas.scale(bounds.width()/24f,bounds.height()/24f)
            if(remove) {
                paint.style=android.graphics.Paint.Style.STROKE
                canvas.drawRoundRect(6f,7f,18f,21f,1.5f,1.5f,paint)
                canvas.drawLine(4f,5f,20f,5f,paint);canvas.drawLine(9f,2f,15f,2f,paint)
                canvas.drawLine(10f,10f,10f,18f,paint);canvas.drawLine(14f,10f,14f,18f,paint)
            } else {
                paint.style=android.graphics.Paint.Style.FILL
                canvas.drawRoundRect(5f,5f,19f,19f,2f,2f,paint)
            }
            canvas.restore()
        }
        override fun setAlpha(alpha:Int){paint.alpha=alpha;invalidateSelf()}
        override fun setColorFilter(filter:android.graphics.ColorFilter?){paint.colorFilter=filter;invalidateSelf()}
        @Suppress("DEPRECATION") override fun getOpacity()=android.graphics.PixelFormat.TRANSLUCENT
    }.apply {setBounds(0,0,px(24),px(24))}
    private val textColor get()=if(light)0xff303947.toInt() else 0xffeef3fb.toInt()
    private fun shape(button:Boolean=false)=GradientDrawable().apply {
        cornerRadius=px(if(button)14 else 28).toFloat()
        setColor(if(button) {if(light)0xffdce6f3.toInt() else 0xff34435e.toInt()} else {if(light)0xfcf2f6fc.toInt() else 0xfc131b24.toInt()})
    }
    private fun label(value:String)=TextView(host.context).apply {
        text=value;setTextColor(textColor);gravity=Gravity.CENTER
        typeface=oemFont;includeFontPadding=false; textDirection=View.TEXT_DIRECTION_LTR
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,26*scale)
    }
    private fun button(value:String,action:()->Unit)=label(value).apply {
        background=if(light)pill() else darkCancel()
        isFocusable=true;setOnClickListener {action()}
    }
    fun dismiss(){overlay?.let {host.removeView(it)};overlay=null}
    private fun layer():FrameLayout {
        dismiss()
        return FrameLayout(host.context).also { view ->
            overlay=view;view.setOnClickListener {dismiss()}
            host.addView(view,FrameLayout.LayoutParams(-1,-1))
        }
    }
    fun menu(anchor:View,stop:()->Unit,remove:()->Unit) {
        val cover=layer()
        val panel=LinearLayout(host.context).apply {orientation=LinearLayout.VERTICAL;background=shape();setPadding(px(12),px(10),px(12),px(10));isClickable=true}
        panel.addView(button("Force Stop") {dismiss();stop()}.apply {
            setCompoundDrawables(actionIcon(text.toString()=="Remove"),null,null,null);compoundDrawablePadding=px(14)
            background=RippleDrawable(ColorStateList.valueOf(0x33429bff),null,shape());gravity=Gravity.LEFT or Gravity.CENTER_VERTICAL;setPadding(px(24),0,0,0)
        },LinearLayout.LayoutParams(-1,px(68)))
        panel.addView(button("Remove") {dismiss();remove()}.apply {setTextColor(removeColor);setCompoundDrawables(actionIcon(text.toString()=="Remove"),null,null,null);compoundDrawablePadding=px(14)
            background=RippleDrawable(ColorStateList.valueOf(0x33429bff),null,shape());gravity=Gravity.LEFT or Gravity.CENTER_VERTICAL;setPadding(px(24),0,0,0)},LinearLayout.LayoutParams(-1,px(68)).apply {topMargin=px(6)})
        val a=IntArray(2);val h=IntArray(2);anchor.getLocationOnScreen(a);host.getLocationOnScreen(h)
        cover.addView(panel,FrameLayout.LayoutParams(px(300),px(162)).apply {
            leftMargin=(a[0]-h[0]).coerceIn(0,(host.width-px(300)).coerceAtLeast(0))
            topMargin=(a[1]-h[1]+anchor.height).coerceIn(0,(host.height-px(162)).coerceAtLeast(0))
        })
    }
    fun confirm(name:String,remove:()->Unit) {
        val cover=layer();cover.setBackgroundColor(0x55000000)
        val panel=LinearLayout(host.context).apply {orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;background=shape();setPadding(px(26),px(24),px(26),px(24));isClickable=true}
        panel.addView(label("Remove $name?").apply {maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END},LinearLayout.LayoutParams(-1,px(76)))
        val buttons=LinearLayout(host.context)
        buttons.addView(button("Cancel") {dismiss()},LinearLayout.LayoutParams(0,px(52),1f).apply {rightMargin=px(12)})
        buttons.addView(button("Remove") {dismiss();remove()}.apply {
            setTextColor(Color.WHITE)
            background=pill(true)
        },LinearLayout.LayoutParams(0,px(52),1f))
        panel.addView(buttons,LinearLayout.LayoutParams(-1,px(52)).apply {topMargin=px(20)})
        cover.addView(panel,FrameLayout.LayoutParams(px(560),-2,Gravity.CENTER))
    }
}
