package com.abrar.voyahiran.feature.remote

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.abrar.voyahiran.VoyahIranApplication
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

class RemotePage(private val activity: ComponentActivity, private val root: LinearLayout) {
    private var job: Job? = null
    private var qrDialog: AlertDialog? = null
    private var visible = false
    private val allowed get() = (activity.application as VoyahIranApplication).subscriptions.remoteAllowed
    fun close() { visible=false; qrDialog?.setOnDismissListener(null);qrDialog?.dismiss();qrDialog=null;job?.cancel(); job = null }
    fun open() { close(); visible=true; job = activity.lifecycleScope.launch { refresh() } }
    private suspend fun request(body: JSONObject) = withContext(Dispatchers.IO) { RemoteApi.call(body) }
    private fun text(value: String) = TextView(activity).apply { text=value; textSize=24f; setPadding(20,20,20,20) }
    private fun button(value: String, action: ()->Unit) = Button(activity).apply { text=value; setOnClickListener { action() } }
    private suspend fun refresh() {
        if (!allowed) { root.removeAllViews(); return }
        root.removeAllViews(); root.addView(text("دسترسی ریموت"))
        try {
            val devices = JSONArray(request(JSONObject().put("action","list")))
            val row=LinearLayout(activity).apply { orientation=LinearLayout.HORIZONTAL }
            root.addView(row)
            repeat(3) { index ->
                val slot=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL; setPadding(20,20,20,20) }
                row.addView(slot, LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f))
                if(index < devices.length()) {
                    val d=devices.getJSONObject(index)
                    slot.addView(text(d.getString("name")))
                    slot.addView(button("ویرایش") { name(d.getString("name")) { value -> mutate(JSONObject().put("action","rename").put("id",d.getString("id")).put("name",value)) } })
                    slot.addView(button("حذف") { AlertDialog.Builder(activity).setMessage("دسترسی این دستگاه حذف شود؟").setNegativeButton("انصراف",null).setPositiveButton("حذف") { _,_ -> mutate(JSONObject().put("action","delete").put("id",d.getString("id"))) }.show() })
                } else slot.addView(button("+\nافزودن دستگاه") { name("گوشی ${index+1}") { value -> invite(value) } })
            }
        } catch(e: CancellationException) { throw e }
        catch(e: Exception) { error(e); root.addView(button("تلاش دوباره") { open() }) }
    }
    private fun name(default: String, done: (String)->Unit) {
        val input=EditText(activity).apply { setText(default); filters=arrayOf(android.text.InputFilter.LengthFilter(40)); isSingleLine=true }
        val dialog=AlertDialog.Builder(activity).setTitle("نام دستگاه").setView(input).setNegativeButton("انصراف",null).setPositiveButton("تأیید",null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { val value=input.text.toString().trim(); if(value.isNotEmpty()) { dialog.dismiss(); done(value) } } }; dialog.show()
    }
    private fun mutate(body: JSONObject) { job?.cancel();job=activity.lifecycleScope.launch { try { if(allowed) request(body); refresh() } catch(e: CancellationException){throw e}catch(e:Exception){error(e)} } }
    private fun invite(name: String) { job?.cancel();job=activity.lifecycleScope.launch {
        try {
            if(!allowed)return@launch
            val before=JSONArray(request(JSONObject().put("action","list"))).length()
            val data=request(JSONObject().put("action","invite").put("name",name))
            // QR is data encoding, not decorative runtime artwork.
            val bitmap=withContext(Dispatchers.Default) {
                val matrix=com.google.zxing.qrcode.QRCodeWriter().encode(data,com.google.zxing.BarcodeFormat.QR_CODE,440,440,mapOf(com.google.zxing.EncodeHintType.CHARACTER_SET to "UTF-8"))
                val pixels=IntArray(440*440) { n -> if(matrix[n%440,n/440]) Color.BLACK else Color.WHITE }
                Bitmap.createBitmap(pixels,440,440,Bitmap.Config.ARGB_8888)
            }
            val image=ImageView(activity).apply { setImageBitmap(bitmap); setPadding(20,20,20,20) }
            val dialog=AlertDialog.Builder(activity).setTitle("افزودن دستگاه").setView(image).setMessage("کد را با VI Remote اسکن کنید. اعتبار کد: ۳ دقیقه").setNegativeButton("بستن",null).create()
            qrDialog=dialog; dialog.setOnDismissListener { qrDialog=null; if(visible) open() }; dialog.show()
            repeat(90) { delay(2000);if(!dialog.isShowing)return@launch;if(!allowed){dialog.dismiss();return@launch};if(JSONArray(request(JSONObject().put("action","list"))).length()>before){dialog.dismiss();return@launch} }
            dialog.dismiss()
        } catch(e: CancellationException){throw e}catch(e:Exception){error(e)}
    } }
    private fun error(e: Exception) { com.abrar.voyahiran.core.logging.AppLog.error("Remote","Remote operation failed",e); Toast.makeText(activity,"ارتباط با خودرو برقرار نشد؛ دوباره تلاش کنید.",Toast.LENGTH_LONG).show() }
}
