package com.abrar.voyahiran.feature.navigation

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.abrar.raha.RahaSavedPlace
import com.abrar.raha.RahaSavedPlaces
import com.abrar.voyahiran.R
import java.util.UUID

internal class SavedPlacesDialog(private val activity:ComponentActivity) {
    private val store=SavedPlacesStore(activity)
    private val dialogs=mutableSetOf<AlertDialog>()
    private fun dp(n:Int)=(activity.resources.displayMetrics.density*n).toInt()
    private fun column()=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL;setPadding(dp(18),dp(12),dp(18),dp(12)) }
    private fun label(value:String)=TextView(activity).apply { text=value;setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, activity.resources.getDimension(com.abrar.voyahiran.R.dimen.text_secondary_size));setTextColor(activity.getColor(R.color.text_primary));setPadding(0,dp(8),0,dp(8)) }
    private fun button(value:String,red:Boolean=false)=Button(activity).apply {
        text=value;isAllCaps=false;setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, activity.resources.getDimension(com.abrar.voyahiran.R.dimen.text_secondary_size));setTextColor(Color.WHITE)
        backgroundTintList=android.content.res.ColorStateList.valueOf(activity.getColor(if(red)R.color.reboot_red else R.color.confirm_green))
        minHeight=activity.resources.getDimensionPixelSize(R.dimen.segment_height)
    }
    fun show() {
        val body=column()
        val list=column()
        val add=button("＋ افزودن مکان منتخب")
        body.addView(add)
        body.addView(ScrollView(activity).apply { addView(list) },LinearLayout.LayoutParams(-1,0,1f))
        fun render() {
            list.removeAllViews()
            val users=try {store.users()} catch(e:Exception) {list.addView(label("فهرست مکان‌ها قابل خواندن نیست"));add.isEnabled=false;return}
            add.isEnabled=users.size<18
            for((_,place) in NeshanVoiceNavigation.destinations) {
                list.addView(label(place.name+"  —  ثابت"))
            }
            for(place in users) {
                val row=LinearLayout(activity).apply { gravity=Gravity.CENTER_VERTICAL }
                row.addView(label(place.name),LinearLayout.LayoutParams(0,-2,1f))
                row.addView(button("ویرایش").apply { setOnClickListener { edit(place,::render) } })
                row.addView(button("حذف",true).apply {
                    layoutParams=LinearLayout.LayoutParams(-2,-2).apply { marginStart=dp(10) }
                    setOnClickListener {
                        val confirmation=AlertDialog.Builder(activity).setTitle("حذف مکان منتخب")
                            .setMessage("«${place.name}» حذف شود؟")
                            .setNegativeButton("انصراف",null).setPositiveButton("حذف") { _,_ ->
                                try {store.delete(place.id);render()}catch(e:Exception){Toast.makeText(activity,e.message,Toast.LENGTH_LONG).show()}
                            }.create()
                        showDialog(confirmation,false)
                    }
                })
                list.addView(row)
            }
        }
        add.setOnClickListener { edit(null,::render) }
        render()
        val dialog=AlertDialog.Builder(activity).setTitle("مکان‌های منتخب").setView(body).setNegativeButton("بستن",null).create()
        showDialog(dialog,true)
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_DESTROY)dialogs.toList().forEach { it.dismiss() } }
        activity.lifecycle.addObserver(observer)
        dialog.setOnDismissListener { dialogs.remove(dialog);activity.lifecycle.removeObserver(observer);dialogs.toList().forEach {it.dismiss()} }
    }
    @Suppress("DEPRECATION") // Car Android 11: keep the save button reachable above the IME.
    private fun edit(place:RahaSavedPlace?, changed:()->Unit) {
        val body=column()
        fun input(value:String)=EditText(activity).apply { setText(value);setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, activity.resources.getDimension(com.abrar.voyahiran.R.dimen.text_secondary_size));setTextColor(activity.getColor(R.color.text_primary));isSingleLine=true;filters=arrayOf(android.text.InputFilter.LengthFilter(300)) }
        body.addView(label("نام"))
        val name=input(place?.name.orEmpty()).apply { hint="مثال: خونه";setHintTextColor(activity.getColor(R.color.hint_text)) };body.addView(name)
        body.addView(label("نام‌های جایگزین (حداکثر ۴ نام، با کاما جدا کنید)"))
        val aliases=input(place?.aliases?.joinToString("، ").orEmpty()).apply { hint="مثال: خونه، خانه، منزل، منزل خودمون";setHintTextColor(activity.getColor(R.color.hint_text)) };body.addView(aliases)
        body.addView(label("موقعیت روی نقشه"))
        val picker=NeshanPlacePicker(activity,place);body.addView(picker)
        val error=label("").apply {setTextColor(activity.getColor(R.color.error));visibility=View.GONE};body.addView(error)
        val dialog=AlertDialog.Builder(activity).setTitle(if(place==null)"افزودن مکان منتخب" else "ویرایش مکان منتخب")
            .setView(ScrollView(activity).apply {addView(body)}).setNegativeButton("انصراف",null).setPositiveButton("ذخیره",null).create()
        showDialog(dialog,true)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.setOnDismissListener {dialogs.remove(dialog);picker.close()}
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            picker.selected { point ->
                if(!dialog.isShowing)return@selected
                try {
                    require(point!=null) { "محل مورد نظر را روی نقشه انتخاب کنید" }
                    val saved=RahaSavedPlace(place?.id ?: "user_"+UUID.randomUUID(),name.text.toString().trim(),RahaSavedPlaces.aliases(aliases.text.toString()),point.first,point.second)
                    store.save(saved);changed();dialog.dismiss()
                }catch(e:Exception) {error.text=e.message;error.visibility=View.VISIBLE;Toast.makeText(activity,e.message,Toast.LENGTH_LONG).show()}
            }
        }
    }
    private fun showDialog(dialog:AlertDialog,large:Boolean) {
        dialogs+=dialog;dialog.show()
        dialog.window?.setBackgroundDrawable(activity.getDrawable(R.drawable.bg_card))
        if(large) {
            val bounds=if(android.os.Build.VERSION.SDK_INT>=30) activity.windowManager.currentWindowMetrics.bounds
                else android.graphics.Rect().also { activity.window.decorView.getWindowVisibleDisplayFrame(it) }
            dialog.window?.setLayout((bounds.width()*0.88).toInt(),(bounds.height()*0.90).toInt())
        }
        dialog.setOnDismissListener { dialogs.remove(dialog) }
    }
}
