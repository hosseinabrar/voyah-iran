package com.abrar.voyahiran.feature.voice

import android.app.AlertDialog
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

object RahaContactsDialog {
    fun show(activity:ComponentActivity) {
        val rows=RahaPhonebook.contacts()
        if(rows.isEmpty()) {
            AlertDialog.Builder(activity).setTitle("مخاطب‌ها").setMessage(RahaPhonebook.status)
                .setPositiveButton("تلاش مجدد") { _,_ -> activity.lifecycleScope.launch { RahaPhonebook.refresh(activity); show(activity) } }
                .setNegativeButton("بستن",null).show()
            return
        }
        fun dp(n:Int)=(n*activity.resources.displayMetrics.density).toInt()
        val edits=mutableMapOf<String,String>()
        val content=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL; layoutDirection=View.LAYOUT_DIRECTION_RTL; setPadding(dp(16),dp(8),dp(16),dp(8)) }
        val headings=LinearLayout(activity).apply {
            for(label in listOf("نام داخل گوشی","نام فارسی برای رها")) addView(TextView(activity).apply { text=label; setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, activity.resources.getDimension(com.abrar.voyahiran.R.dimen.text_secondary_size)); setPadding(dp(8),dp(8),dp(8),dp(8)) },LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
        }
        content.addView(headings)
        val list=ListView(activity).apply { itemsCanFocus=true; descendantFocusability=ViewGroup.FOCUS_AFTER_DESCENDANTS }
        class Holder(val row:LinearLayout,val original:TextView,val spoken:EditText) { var watcher:TextWatcher?=null }
        list.adapter=object:BaseAdapter() {
            override fun getCount()=rows.size
            override fun getItem(position:Int)=rows[position]
            override fun getItemId(position:Int)=position.toLong()
            fun createHolder():Holder {
                val row=LinearLayout(activity).apply { gravity=android.view.Gravity.CENTER_VERTICAL; setPadding(0,dp(4),0,dp(4)) }
                val original=TextView(activity).apply { setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, activity.resources.getDimension(com.abrar.voyahiran.R.dimen.text_secondary_size)); textDirection=View.TEXT_DIRECTION_FIRST_STRONG; setPadding(dp(8),0,dp(8),0) }
                val spoken=EditText(activity).apply { setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, activity.resources.getDimension(com.abrar.voyahiran.R.dimen.text_secondary_size)); setSingleLine(true); textDirection=View.TEXT_DIRECTION_RTL; filters=arrayOf(android.text.InputFilter.LengthFilter(120)); inputType=android.text.InputType.TYPE_CLASS_TEXT }
                row.addView(original,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
                row.addView(spoken,LinearLayout.LayoutParams(0,activity.resources.getDimensionPixelSize(com.abrar.voyahiran.R.dimen.touch_target),1f))
                return Holder(row,original,spoken)
            }
            override fun getView(position:Int,convertView:View?,parent:ViewGroup):View {
                val holder=(convertView?.tag as? Holder) ?: createHolder().also { it.row.tag=it }
                val contact=rows[position]
                holder.watcher?.let(holder.spoken::removeTextChangedListener)
                holder.original.text=if(contact.builtIn) "${contact.original} (${contact.number})" else contact.original
                holder.spoken.isEnabled=!contact.builtIn
                val displayed=edits[contact.id]?:contact.spoken
                if(holder.spoken.text.toString()!=displayed) holder.spoken.setText(displayed)
                holder.spoken.contentDescription="نام فارسی ${contact.original}"
                holder.watcher=object:TextWatcher {
                    override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
                    override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { edits[contact.id]=s.toString() }
                    override fun afterTextChanged(s:Editable?) {}
                }.also(holder.spoken::addTextChangedListener)
                return holder.row
            }
        }
        content.addView(list,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(340)))
        val dialog=AlertDialog.Builder(activity).setTitle("مخاطب‌ها").setView(content).setPositiveButton("ذخیره",null).setNegativeButton("انصراف",null).create()
        dialog.setOnShowListener {
            dialog.window?.setLayout(minOf(dp(900),activity.resources.displayMetrics.widthPixels-dp(48)),ViewGroup.LayoutParams.WRAP_CONTENT)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if(edits.values.any { it.isBlank() }) { Toast.makeText(activity,"نام فارسی نمی‌تواند خالی باشد",Toast.LENGTH_LONG).show(); return@setOnClickListener }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=false
                activity.lifecycleScope.launch {
                    try { RahaPhonebook.edit(activity,edits); dialog.dismiss() }
                    catch(_:Exception) { dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=true; Toast.makeText(activity,"ذخیره مخاطب‌ها ناموفق بود",Toast.LENGTH_LONG).show() }
                }
            }
        }
        dialog.show()
    }
}
