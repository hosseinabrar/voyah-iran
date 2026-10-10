package com.abrar.voyahiran.feature.launcher

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.*

/** CCC popup_window_layout/mode_item proportions, scaled with the launcher canvas. */
internal object LauncherDrivePopup {
    fun show(anchor: View, state: LauncherVehicleState, lightTheme: Boolean = false, select: (Int) -> Unit): PopupWindow {
        val context=anchor.context
        val scale=minOf(anchor.width/1775f, anchor.height/660f)
        fun px(n:Int)=(n*scale).toInt().coerceAtLeast(1)
        val options=LauncherVehicleState.standardDriveModes.map { mode -> LauncherVehicleState.driveNames.entries.first { it.key == mode } }
        val names=mapOf(1 to "Eco.",2 to "Comf.",3 to "Sport",4 to "Outing",6 to "Snow",5 to "Indiv.")
        val icons=mapOf(1 to "eco_normal",2 to "comfort_normal",3 to "sport_img",4 to "outing_img",6 to "snow_normal",5 to "person_normal")
        val chosen=mapOf(1 to "eco_img",2 to "comfort_img",6 to "snow",5 to "person_img")
        fun icon(name:String):Drawable?=context.assets.open("launcher/ccc/$name.png").use { Drawable.createFromStream(it,name) }
        val list=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_LTR }
        val scroll=ScrollView(context).apply {
            isFillViewport=false;isVerticalScrollBarEnabled=false
            background=GradientDrawable().apply {setColor(if(lightTheme)0xfcf2f6fc.toInt() else 0xfc131b24.toInt());cornerRadius=28*scale}
            clipToOutline=true
            addView(list)
        }
        val popup=PopupWindow(scroll,px(340),px(minOf(options.size*100,400)),true).apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable=true
            elevation=0f
        }
        options.forEach { entry ->
            val selected=state.drive==entry.key
            val row=LinearLayout(context).apply {
                orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
                setPadding(px(30),0,px(30),0)
                contentDescription=names[entry.key]
                isSelected=selected
            }
            val image=ImageView(context).apply {
                setImageDrawable(icon(if(selected) chosen[entry.key] ?: icons.getValue(entry.key) else icons.getValue(entry.key)))
            }
            if(lightTheme && !selected) image.setColorFilter(0xff263651.toInt(),android.graphics.PorterDuff.Mode.SRC_IN)
            row.addView(image,LinearLayout.LayoutParams(px(40),px(40)))
            val label=TextView(context).apply {
                text=names[entry.key];setTextSize(TypedValue.COMPLEX_UNIT_PX,28*scale)
                typeface=Typeface.create("sans-serif",Typeface.NORMAL)
                setTextColor(if(selected)0xff47b4ff.toInt() else if(lightTheme)0xff263651.toInt() else Color.WHITE)
                maxLines=1;gravity=Gravity.CENTER_VERTICAL
            }
            row.addView(label,LinearLayout.LayoutParams(0,-1,1f).apply {leftMargin=px(15)})
            row.addView(ImageView(context).apply {
                setImageDrawable(icon("choice_img"));visibility=if(selected)View.VISIBLE else View.INVISIBLE
            },LinearLayout.LayoutParams(px(40),px(40)))
            row.setOnClickListener { popup.dismiss();select(entry.key) }
            list.addView(row,LinearLayout.LayoutParams(-1,px(100)))
        }
        val location=IntArray(2);anchor.getLocationInWindow(location)
        // Above the mode shortcut, inside the suspension card, as in the OEM screenshot.
        popup.showAtLocation(anchor,Gravity.TOP or Gravity.LEFT,location[0]+px(1214),location[1]+px(80))
        scroll.post { val index=options.indexOfFirst {it.key==state.drive};if(index>=4) scroll.scrollTo(0,px((index-3)*100)) }
        return popup
    }
}
