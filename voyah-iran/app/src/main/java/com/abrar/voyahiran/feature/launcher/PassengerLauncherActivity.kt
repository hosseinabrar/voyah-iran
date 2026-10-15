package com.abrar.voyahiran.feature.launcher

import android.app.ActivityOptions
import android.content.*
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.AudioManager
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.webkit.*
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import com.abrar.voyahiran.VoyahIranApplication
import com.abrar.voyahiran.core.bridge.BridgeClient
import com.abrar.voyahiran.core.bridge.PassengerComfortBridge
import com.abrar.voyahiran.core.logging.AppLog
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap

/** Offline presentation; no remote page can access the bounded native command interface. */
class PassengerLauncherActivity : ComponentActivity() {
    private lateinit var web:WebView
    private lateinit var climate:PassengerClimateView
    private lateinit var fragrance:PassengerFragranceView
    private lateinit var favoriteCard:PassengerFavoritesView
    private var appPicker:android.app.Dialog?=null
    private lateinit var surface:android.widget.FrameLayout
    private var ready=false
    private var visible=false
    private var light=false
    private var watch:Job?=null
    private var guard:Job?=null
    private var coverJob:Job?=null
    private var coverKey=""
    private var media=LauncherMediaState()
    private var vehicleAt=0L
    private var commandBusy=false
    private var lastVolume=11
    private var catalog=emptyList<LauncherAppsEntry>()
    private var unobserve:(()->Unit)?=null
    private val states=linkedMapOf<String,JSONObject>()
    private val lastSent=HashMap<String,String>()
    private val pictures=ConcurrentHashMap<String,ByteArray>()
    private val skin by lazy {PassengerSkinArtwork(applicationContext)}
    private val favorites by lazy {getSharedPreferences("passenger_favorites",MODE_PRIVATE)}
    private val audio by lazy {getSystemService(AudioManager::class.java)}
    private val weather by lazy {LauncherSharedWeather(this) {s->
        val hours=JSONArray();s.hours.forEach {h->hours.put(JSONObject().put("time",h.time).put("temperature",h.temperature).put("icon",weatherIcon(h.code,h.day)))}
        publish("weather",JSONObject().put("city",s.city).put("temperature",s.temperature).put("icon",weatherIcon(s.code,s.day)).put("notice",s.notice).put("hours",hours))
    }}
    private val mediaObserver by lazy {LauncherMediaObserver(this,lifecycleScope,::onMedia)}
    private val changes=object:BroadcastReceiver() {override fun onReceive(c:Context?,i:Intent?) {if(i?.action=="theme_loaded"){skin.clear();applyTheme(true)} else volume()}}
    @Suppress("DEPRECATION")
    private fun allowed()=visible && !isFinishing && !isDestroyed && LauncherPreferences.enabled(this) && (application as VoyahIranApplication).subscriptions.vipAllowed && (window.decorView.display?.displayId ?: windowManager.defaultDisplay.displayId)==1
    @Suppress("SetJavaScriptEnabled") override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        web=WebView(this)
        climate=PassengerClimateView(this).apply {onCommand={op,value->if(allowed())command("comfort",JSONObject().put("op",op).put("value",value).toString())}}
        fragrance=PassengerFragranceView(this).apply {onCommand={op,value->if(allowed())command("comfort",JSONObject().put("op",op).put("value",value).toString())}}
        favoriteCard=PassengerFavoritesView(this).apply {
            onOpen={if(allowed())command("open",it)}
            onAllApps={if(allowed())command("allApps","")}
            onSave={if(allowed())command("favorites",JSONArray(it).toString())}
            onPick={if(allowed())showAppPicker(it)}
        }
        web.settings.apply {javaScriptEnabled=true;allowFileAccess=false;allowContentAccess=false;domStorageEnabled=false;javaScriptCanOpenWindowsAutomatically=false;setSupportMultipleWindows(false);mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW}
        web.addJavascriptInterface(Commands(),"Passenger")
        web.webViewClient=object:WebViewClient() {
            override fun shouldOverrideUrlLoading(v:WebView,r:WebResourceRequest)=true
            override fun shouldInterceptRequest(v:WebView,r:WebResourceRequest):WebResourceResponse {
                val uri=r.url;val path=uri.path.orEmpty().removePrefix("/")
                if(uri.scheme!="https" || uri.host!="passenger.local" || path.contains(".."))return emptyResponse()
                return runCatching {
                    if(path.startsWith("image/"))return@runCatching WebResourceResponse("image/png",null,ByteArrayInputStream(pictures[path.removePrefix("image/")] ?: byteArrayOf()))
                    skin.get(path,light)?.let {return@runCatching WebResourceResponse("image/png",null,ByteArrayInputStream(it))}
                    val mime=when(path.substringAfterLast('.')) {"html"->"text/html";"js"->"application/javascript";"css"->"text/css";"ttf"->"font/ttf";"png"->"image/png";"jpg","jpeg"->"image/jpeg";else->"application/octet-stream"}
                    WebResourceResponse(mime,"UTF-8",assets.open("passenger/$path"))
                }.getOrElse {emptyResponse()}
            }
            override fun onPageFinished(v:WebView,url:String) {
                if(url!="https://passenger.local/${if(light)"light" else "dark"}.html")return
                ready=true;states.toMap().forEach {(k,s)->publish(k,s)};publishApps();web.evaluateJavascript("document.querySelector('.climate').style.display='none';document.querySelector('.fragrance').style.display='none';document.querySelector('.apps').style.display='none';passenger.visibility($visible)",null);web.animate().alpha(1f).setDuration(280).start()
            }
        }
        surface=android.widget.FrameLayout(this)
        surface.addView(web,android.widget.FrameLayout.LayoutParams(-1,-1))
        surface.addView(climate,android.widget.FrameLayout.LayoutParams(1,1))
        surface.addView(fragrance,android.widget.FrameLayout.LayoutParams(1,1))
        surface.addView(favoriteCard,android.widget.FrameLayout.LayoutParams(1,1))
        listOf(climate,fragrance).forEach {card->card.setOnTouchListener {_,event->
            if(event.actionMasked==android.view.MotionEvent.ACTION_DOWN)web.evaluateJavascript("window.passenger&&passenger.dismiss()",null)
            false
        }}
        setContentView(surface)
        web.addOnLayoutChangeListener {_,_,_,_,_,_,_,_,_->
            val point=android.graphics.Point()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealSize(point)
            val origin=IntArray(2);web.getLocationOnScreen(origin)
            val top=if(point.y>0)origin[1]*720.0/point.y else 0.0
            publish("frame",JSONObject().put("top",top))
            val sx=web.width/1920f;val sy=(web.height/(720.0-top)).toFloat()
            climate.layoutParams=android.widget.FrameLayout.LayoutParams((770*sx).toInt(),(370*sy).toInt()).apply {
                leftMargin=(5*sx).toInt();topMargin=((50-top)*sy).toInt()
            }
            fragrance.layoutParams=android.widget.FrameLayout.LayoutParams((770*sx).toInt(),(280*sy).toInt()).apply {
                leftMargin=(5*sx).toInt();topMargin=((432-top)*sy).toInt()
            }
            favoriteCard.layoutParams=android.widget.FrameLayout.LayoutParams((546*sx).toInt(),(662*sy).toInt()).apply {
                leftMargin=(793*sx).toInt();topMargin=((50-top)*sy).toInt()
            }
        }
        onBackPressedDispatcher.addCallback(this,object:OnBackPressedCallback(true){override fun handleOnBackPressed(){favoriteCard.dismissEdit();web.evaluateJavascript("window.passenger&&passenger.dismiss()",null)}})
        val filter=IntentFilter("theme_loaded").apply {addAction("android.media.VOLUME_CHANGED_ACTION")}
        if(android.os.Build.VERSION.SDK_INT>=33)registerReceiver(changes,filter,Context.RECEIVER_EXPORTED) else registerReceiver(changes,filter)
        applyTheme(true)
    }
    private fun emptyResponse()=WebResourceResponse("text/plain","UTF-8",ByteArrayInputStream(byteArrayOf()))
    override fun dispatchTouchEvent(event:android.view.MotionEvent):Boolean {
        if(event.actionMasked==android.view.MotionEvent.ACTION_DOWN&&::favoriteCard.isInitialized){
            val bounds=android.graphics.Rect();favoriteCard.getGlobalVisibleRect(bounds)
            if(!bounds.contains(event.rawX.toInt(),event.rawY.toInt()))favoriteCard.dismissEdit()
        }
        return super.dispatchTouchEvent(event)
    }
    private fun showAppPicker(slot:Int){
        appPicker?.dismiss()
        val scale=web.width/1920f
        val dialog=android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(PassengerAppPickerView(this,favoriteCard.availableApps(),light,scale){id->
            if(allowed())favoriteCard.select(slot,id)
            dialog.dismiss()
        })
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnDismissListener{if(appPicker===dialog)appPicker=null}
        appPicker=dialog;dialog.show()
        dialog.window?.apply{
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            setLayout((800*scale).toInt(),(540*scale).toInt())
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes=attributes.apply{dimAmount=.45f;windowAnimations=android.R.style.Animation_Dialog}
        }
    }
    private fun applyTheme(force:Boolean=false) {
        val next=LauncherTheme.isLight();if(!force && next==light)return
        val fading=ready
        light=next;climate.light=next;fragrance.light=next;favoriteCard.light=next;appPicker?.dismiss();ready=false;lastSent.clear();LauncherWindowTheme.apply(window,light);web.setBackgroundColor(LauncherWindowTheme.background(light))
        web.animate().cancel()
        val load={if(!isDestroyed && light==next)web.loadUrl("https://passenger.local/${if(next)"light" else "dark"}.html")}
        if(fading)web.animate().alpha(0f).setDuration(160).withEndAction {load()}.start()
        else {web.alpha=0f;load()}
    }
    private fun publish(kind:String,value:JSONObject) {
        if(kind=="vehicle")climate.update(value)
        if(kind=="vehicle")fragrance.update(value)
        states[kind]=value
        if(ready){val json=value.toString();if(lastSent[kind]==json)return;lastSent[kind]=json;web.evaluateJavascript("window.passenger&&passenger.update(${JSONObject.quote(kind)},$json)",null)}
    }
    override fun onResume() {
        super.onResume();visible=true;if(!allowed()){finish();return};applyTheme();web.onResume()
        web.evaluateJavascript("window.passenger&&passenger.visibility(true)",null)
        if(watch?.isActive==true)return
        vehicleAt=0L;publish("vehicle",JSONObject().put("fresh",false))
        weather.start();mediaObserver.start();volume();unobserve=LauncherAppsCache.observe(this){catalog=it;publishApps()}
        watch=lifecycleScope.launch {
            var revision=-1L
            while(isActive) {
                val r=withContext(Dispatchers.IO){BridgeClient.run(listOf("launcher-vehicle","watch",revision.toString()),4)}
                if(!allowed())break
                if(r.exitCode==0 && !r.timedOut)runCatching {JSONObject(r.output)}.onSuccess {s->
                    vehicleAt=SystemClock.elapsedRealtime();val next=s.optLong("revision",-1)
                    if(next!=revision || states["vehicle"]?.optBoolean("fresh")!=true){revision=next;publish("vehicle",s.put("fresh",true))}
                }
                if(SystemClock.elapsedRealtime()-vehicleAt>10000 && states["vehicle"]?.optBoolean("fresh")!=false)publish("vehicle",JSONObject().put("fresh",false))
                delay(if(r.exitCode==0)100 else 1000)
            }
        }
        guard=lifecycleScope.launch {while(isActive){delay(1000);if(!allowed()){finish();break};applyTheme()}}
    }
    override fun onStop(){favoriteCard.dismissEdit();appPicker?.dismiss();visible=false;watch?.cancel();watch=null;guard?.cancel();guard=null;unobserve?.invoke();unobserve=null;weather.stop();mediaObserver.stop();web.evaluateJavascript("window.passenger&&passenger.visibility(false)",null);web.onPause();super.onStop()}
    override fun onDestroy(){ready=false;visible=false;unobserve?.invoke();unobserve=null;coverJob?.cancel();unregisterReceiver(changes);web.removeJavascriptInterface("Passenger");web.destroy();super.onDestroy()}
    override fun onConfigurationChanged(config:android.content.res.Configuration){super.onConfigurationChanged(config);applyTheme()}
    private inner class Commands {
        @JavascriptInterface fun send(kind:String,value:String) {if(kind.length>40 || value.length>4096)return;runOnUiThread {if(allowed())runCatching {command(kind,value)}.onFailure {error(it)}}}
    }
    private fun command(kind:String,value:String) {
        when(kind) {
            "nativeOverlay" -> {
                val visibility=if(value=="true")android.view.View.INVISIBLE else android.view.View.VISIBLE
                climate.visibility=visibility;fragrance.visibility=visibility
            }
            "comfort" -> {
                val p=JSONObject(value);val op=p.getString("op");val v=p.getString("value")
                if(commandBusy || SystemClock.elapsedRealtime()-vehicleAt>10000 || !PassengerComfortBridge.valid(op,v))return
                commandBusy=true;lifecycleScope.launch {try {val r=withContext(Dispatchers.IO){BridgeClient.run(listOf("passenger-comfort",op,v),12)};check(r.exitCode==0 && !r.timedOut){r.output}}
                catch(e:CancellationException){throw e}catch(e:Exception){error(e)}finally{commandBusy=false}}
            }
            "allApps" -> startActivity(Intent(this,PassengerAppsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),ActivityOptions.makeBasic().apply {launchDisplayId=1}.toBundle())
            "open" -> catalog.firstOrNull {it.id==value}?.let(::openApp)
            "favorites" -> {
                val a=JSONArray(value);require(a.length()==5);val ids=(0..4).map {a.optString(it)}
                require(ids.filter {it.isNotBlank()}.distinct().size==ids.count {it.isNotBlank()});require(ids.all {id->id.isEmpty() || catalog.any {it.id==id}})
                favorites.edit().putString("ids",a.toString()).apply();publishApps()
            }
            "volume" -> {val n=value.toInt();require(n in 0..30);audio.setStreamVolume(AudioManager.STREAM_MUSIC,(n*audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)/30f).toInt(),0);volume()}
            "mute" -> {val n=audio.getStreamVolume(AudioManager.STREAM_MUSIC);if(n>0)lastVolume=n;audio.setStreamVolume(AudioManager.STREAM_MUSIC,if(n>0)0 else lastVolume.coerceIn(1,audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)),0);volume()}
            "seek" -> if(media.canSeek)media.controller?.transportControls?.seekTo(value.toLong().coerceIn(0,media.duration))
            "media" -> {
                if(value=="play" && !media.playing && (media.controller==null || media.title=="no music")) {
                    lifecycleScope.launch {try {com.abrar.voyahiran.feature.media.RahaMediaControl.playDefault(this@PassengerLauncherActivity,1)}catch(e:CancellationException){throw e}catch(e:Exception){error(e)}}
                    return
                }
                val key=when(value){"previous"->88;"next"->87;"play"->if(media.playing)127 else 126;else->return};val c=media.controller
                if(c!=null)when(key){88->c.transportControls.skipToPrevious();87->c.transportControls.skipToNext();127->c.transportControls.pause();else->c.transportControls.play()}
                else {audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,key));audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP,key))}
            }
            "source" -> LauncherSourceLaunch.open(this,media,1)
        }
    }
    private fun openApp(entry:LauncherAppsEntry) {
        if(entry.component!=null)startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(entry.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),ActivityOptions.makeBasic().apply {launchDisplayId=1}.toBundle())
        else lifecycleScope.launch {val nonce=java.util.UUID.randomUUID().toString().replace("-","");val r=withContext(Dispatchers.IO){BridgeClient.run(listOf("launcher-drawer","open",nonce,entry.id,"1"),5)};if(r.exitCode!=0 || r.timedOut)error(IllegalStateException(r.output))}
    }
    private var catalogJob:Job?=null
    private fun publishApps() {
        val saved=runCatching {JSONArray(favorites.getString("ids",null))}.getOrNull()
        val ids=if(saved!=null)(0..4).map {saved.optString(it).takeIf {id->catalog.any {e->e.id==id}}.orEmpty()} else catalog.take(5).map {it.id}.let {it+List(5-it.size){""}}
        if(saved==null&&catalog.isNotEmpty())favorites.edit().putString("ids",JSONArray(ids).toString()).apply()
        favoriteCard.bind(catalog,ids);appPicker?.dismiss()
        catalogJob?.cancel();val items=catalog
        catalogJob=lifecycleScope.launch {val rows=withContext(Dispatchers.Default){JSONArray().also {a->items.forEach {e->
            val key="app-"+e.id.hashCode();e.icon?.constantState?.newDrawable(resources)?.let {d->val b=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888);d.setBounds(0,0,128,128);d.draw(Canvas(b));pictures[key]=png(b);b.recycle()}
            a.put(JSONObject().put("id",e.id).put("label",e.label).put("icon","image/$key"))
        }}};publish("apps",JSONObject().put("items",rows).put("favorites",JSONArray(ids)))}
    }
    private fun onMedia(state:LauncherMediaState) {
        media=state;val key=listOf(state.sourcePackage,state.title,state.artist,state.coverUri).joinToString("|")
        if(key!=coverKey){coverKey=key;coverJob?.cancel();pictures.remove("cover");coverJob=lifecycleScope.launch {
            val image=withContext(Dispatchers.IO){state.cover ?: state.coverUri.takeIf {it.isNotBlank()}?.let {LauncherArtwork(this@PassengerLauncherActivity).load(it)}}
            if(coverKey==key && image!=null){pictures["cover"]=withContext(Dispatchers.Default){png(image)};mediaState()}
        }};mediaState()
    }
    private fun mediaState() {
        val s=media;s.sourceIcon?.constantState?.newDrawable(resources)?.let {d->val b=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);d.setBounds(0,0,64,64);d.draw(Canvas(b));pictures["source"]=png(b);b.recycle()}
        publish("media",JSONObject().put("title",s.title).put("artist",s.artist).put("playing",s.playing).put("position",s.position).put("duration",s.duration).put("seek",s.canSeek).put("cover",if(pictures.containsKey("cover"))"image/cover?v=${coverKey.hashCode()}" else "").put("source",if(s.sourceIcon!=null)"image/source?v=${s.sourcePackage.hashCode()}" else ""))
    }
    private fun png(b:Bitmap)=ByteArrayOutputStream().use {b.compress(Bitmap.CompressFormat.PNG,100,it);it.toByteArray()}
    private fun volume(){publish("volume",JSONObject().put("value",(audio.getStreamVolume(AudioManager.STREAM_MUSIC)*30f/audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)).toInt()))}
    private fun weatherIcon(code:Int,day:Boolean):String {val id=if(!day && code in 0..1)com.abrar.voyahiran.R.drawable.launcher_weather_moon else LauncherWeatherCode.icon(code);val key="weather-$id";if(!pictures.containsKey(key))pictures[key]=png(LauncherWeatherArtwork.decode(resources,id));return "image/$key"}
    private fun error(e:Throwable){AppLog.error("PassengerLauncher","Operation failed",e);if(visible)Toast.makeText(this,"عملیات انجام نشد؛ وضعیت خودرو را بررسی کنید",Toast.LENGTH_LONG).show()}
}
