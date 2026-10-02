package com.abrar.voyahiran.feature.navigation

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.LocationManager
import androidx.core.os.CancellationSignal
import androidx.core.location.LocationManagerCompat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.abrar.raha.RahaSavedPlace
import com.abrar.voyahiran.BuildConfig
import org.json.JSONObject
import java.util.UUID

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
internal class NeshanPlacePicker(private val activity: ComponentActivity, place: RahaSavedPlace?) : LinearLayout(activity) {
    private val web=WebView(activity)
    private val handler=Handler(Looper.getMainLooper())
    private var closed=false
    private var signal:CancellationSignal?=null
    private val locate=Button(activity).apply { text="◎ موقعیت من";setTextColor(Color.BLACK);backgroundTintList=android.content.res.ColorStateList.valueOf(Color.WHITE) }
    private val permission=activity.activityResultRegistry.register("saved-place-location-"+UUID.randomUUID(),ActivityResultContracts.RequestMultiplePermissions()) {
        if(!closed) { if(hasPermission())locateNow() else message("برای نمایش موقعیت، دسترسی مکان را فعال کنید") }
    }
    private val timeout=Runnable { signal?.cancel();signal=null;locate.isEnabled=true;message("موقعیت دریافت نشد؛ در فضای باز دوباره امتحان کنید") }
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    private fun message(text:String) { if(!closed)Toast.makeText(activity,text,Toast.LENGTH_LONG).show() }
    private fun hasPermission()=activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED || activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
    init {
        orientation=VERTICAL
        if(BuildConfig.NESHAN_MAP_KEY.isBlank()) {
            addView(TextView(activity).apply {text="نقشه در این نسخه آماده نیست.";setTextColor(Color.WHITE)})
        } else {
            web.settings.javaScriptEnabled=true
            web.settings.domStorageEnabled=true
            web.settings.allowFileAccess=false
            web.settings.allowContentAccess=false
            web.webViewClient=object:WebViewClient() {override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest)=true}
            web.setOnTouchListener {view,event ->
                view.parent.requestDisallowInterceptTouchEvent(event.action!=MotionEvent.ACTION_UP && event.action!=MotionEvent.ACTION_CANCEL);false
            }
            val frame=FrameLayout(activity)
            frame.addView(web,FrameLayout.LayoutParams(-1,-1))
            frame.addView(locate,FrameLayout.LayoutParams(-2,dp(52),Gravity.BOTTOM or Gravity.RIGHT).apply {rightMargin=dp(12);bottomMargin=dp(26)})
            addView(frame,LayoutParams(-1,dp(360)).apply {gravity=Gravity.CENTER_HORIZONTAL})
            addOnLayoutChangeListener {_,l,_,r,_,_,_,_,_ ->
                val size=(r-l-paddingLeft-paddingRight).coerceAtMost(dp(460))
                if(size>0 && (frame.layoutParams.width!=size || frame.layoutParams.height!=size)) {
                    frame.layoutParams=frame.layoutParams.apply {width=size;height=size}
                    web.post {if(!closed)web.evaluateJavascript("if(map)map.invalidateSize()",null)}
                }
            }
            locate.setOnClickListener {
                if(hasPermission())locateNow() else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))
            }
            val config=JSONObject().put("key",BuildConfig.NESHAN_MAP_KEY).put("latitude",place?.latitude ?: 35.699756).put("longitude",place?.longitude ?: 51.338076).put("selected",place!=null)
            val html=activity.assets.open("neshan-place-picker.html").bufferedReader().use {it.readText()}.replace("__CONFIG__",config.toString().replace("<","\\u003c"))
            web.loadDataWithBaseURL("https://voyah-iran.ir/",html,"text/html","UTF-8",null)
        }
    }
    @SuppressLint("MissingPermission")
    private fun locateNow() {
        if(closed)return
        val manager=activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            val fine=activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
            val provider=when {
                fine && manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> {message("موقعیت‌یابی دستگاه در دسترس نیست؛ GPS را فعال کنید");return}
            }
            signal?.cancel();handler.removeCallbacks(timeout)
            val request=CancellationSignal();signal=request;locate.isEnabled=false
            message("در حال دریافت موقعیت…")
            handler.postDelayed(timeout,20000)
            LocationManagerCompat.getCurrentLocation(manager,provider,request,activity.mainExecutor) {location ->
                if(closed || signal!==request)return@getCurrentLocation
                handler.removeCallbacks(timeout);signal=null;locate.isEnabled=true
                if(location==null)message("موقعیت دریافت نشد؛ دوباره امتحان کنید")
                else if(location.latitude.isFinite() && location.longitude.isFinite()) {
                    web.evaluateJavascript("showPoint(${location.latitude},${location.longitude})") {result ->
                        if(result!="true")message("نقشه هنوز آماده نیست؛ دوباره امتحان کنید")
                    }
                }
            }
        } catch(e:Exception) {
            handler.removeCallbacks(timeout);signal?.cancel();signal=null;locate.isEnabled=true
            message("دریافت موقعیت ممکن نشد؛ دسترسی مکان و GPS را بررسی کنید")
        }
    }
    fun selected(callback:(Pair<Double,Double>?)->Unit) {
        if(BuildConfig.NESHAN_MAP_KEY.isBlank()){callback(null);return}
        web.evaluateJavascript("JSON.stringify(selection)") {raw ->
            if(!closed)callback(runCatching {
                val value=org.json.JSONTokener(raw).nextValue() as? String ?: return@runCatching null
                if(value=="null")null else JSONObject(value).let {it.getDouble("latitude") to it.getDouble("longitude")}
            }.getOrNull())
        }
    }
    fun close() {
        if(closed)return
        closed=true;handler.removeCallbacks(timeout);signal?.cancel();signal=null;permission.unregister();web.stopLoading();web.destroy()
    }
}
