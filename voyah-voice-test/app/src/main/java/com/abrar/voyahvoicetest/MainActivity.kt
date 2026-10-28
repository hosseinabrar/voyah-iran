package com.abrar.voyahvoicetest

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.abrar.raha.speech.EngineChoice
import com.abrar.raha.speech.SpeechLab
import com.abrar.raha.speech.SpeechResult
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.concurrent.Executors

/** Bundled offline UI. No remote navigation, training endpoint, or vehicle execution. */
class MainActivity:ComponentActivity() {
    private lateinit var web:WebView
    private lateinit var lab:SpeechLab
    private lateinit var model:TesterModel
    private lateinit var store:TrainingStore
    private val worker=Executors.newSingleThreadExecutor()
    @Volatile private var destroyed=false
    @Volatile private var foreground=false
    @Volatile private var ready=false
    @Volatile private var listening=false
    @Volatile private var decoding=false
    @Volatile private var epoch=0L
    private var current:JSONObject?=null
    private val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted->
        if(granted&&foreground)listen() else speech("error","message" to "برای شنود، دسترسی میکروفون لازم است")
    }
    override fun onCreate(state:Bundle?) {
        super.onCreate(state);enableEdgeToEdge()
        store=TrainingStore(this);model=TesterModel(this);lab=SpeechLab(applicationContext)
        web=WebView(this)
        val root=android.widget.FrameLayout(this)
        val loading=android.widget.TextView(this).apply {text="در حال آماده‌سازی…";gravity=android.view.Gravity.CENTER;setTextColor(android.graphics.Color.DKGRAY)}
        root.setBackgroundColor(android.graphics.Color.rgb(243,246,250));root.addView(web);root.addView(loading,android.widget.FrameLayout.LayoutParams(-1,-1));setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(web){view,insets->
            val bars=insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left,bars.top,bars.right,bars.bottom);insets
        }
        web.setBackgroundColor(android.graphics.Color.rgb(243,246,250))
        web.settings.apply {javaScriptEnabled=true;allowFileAccess=false;allowContentAccess=false;domStorageEnabled=false;setSupportMultipleWindows(false);mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW}
        web.addJavascriptInterface(Bridge(),"AndroidTrainer")
        web.webViewClient=object:WebViewClient(){
            override fun onPageFinished(view:WebView,url:String){loading.visibility=android.view.View.GONE}
            override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest)=true
            override fun shouldInterceptRequest(view:WebView,request:WebResourceRequest):WebResourceResponse {
                val uri=request.url;val name=uri.path.orEmpty().removePrefix("/trainer/")
                val allowed=setOf("index.html","web/style.css","web/app.js","web/native.js","web/parameters.js","web/feedback-editor.js")
                if(uri.scheme!="https"||uri.host!="appassets.androidplatform.net"||!uri.path.orEmpty().startsWith("/trainer/")||name !in allowed)
                    return WebResourceResponse("text/plain","UTF-8",403,"Forbidden",emptyMap(),ByteArrayInputStream(byteArrayOf()))
                val mime=when {name.endsWith(".html")->"text/html";name.endsWith(".css")->"text/css";else->"text/javascript"}
                return WebResourceResponse(mime,"UTF-8",200,"OK",mapOf("Cache-Control" to "no-store","Content-Security-Policy" to "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; connect-src 'none'; frame-src 'none'; object-src 'none'"),assets.open("trainer/$name"))
            }
        }
        lab.prepare(EngineChoice.CAR_LIVE){error->
            if(destroyed)return@prepare
            worker.execute {
                try {
                    if(error!=null)throw error
                    model.prepare();ready=true
                    runOnUiThread {if(!destroyed)web.loadUrl("https://appassets.androidplatform.net/trainer/index.html")}
                }catch(e:Throwable){android.util.Log.e("VoiceTrainer","Preparation failed",e);runOnUiThread {if(!destroyed)loading.text="آماده‌سازی انجام نشد؛ برنامه را دوباره باز کن"}}
            }
        }
    }
    private fun js(script:String)=runOnUiThread {if(!destroyed)web.evaluateJavascript(script,null)}
    private fun speech(type:String,vararg values:Pair<String,Any>) {
        val event=JSONObject().put("type",type);values.forEach {event.put(it.first,it.second)}
        js("window.nativeSpeech && window.nativeSpeech($event)")
    }
    private fun reply(id:Int,value:Any?=null,error:String?=null)=js("window.nativeReply($id,${value?:"null"},${error?.let(JSONObject::quote)?:"null"})")
    inner class Bridge {
        @JavascriptInterface fun request(id:Int,path:String,raw:String) {
            if(destroyed)return
            if(raw.length>16000){reply(id,error="درخواست بیش از حد طولانی است");return}
            worker.execute {
                try {
                    check(ready){"هنوز آماده نیست"};val body=JSONObject(raw)
                    when(path){
                        "session"->{if(body.has("name"))store.name=body.getString("name");reply(id,JSONObject().put("name",store.name))}
                        "catalog"->reply(id,model.catalog)
                        "listen"->{check(store.name.isNotEmpty());runOnUiThread {if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)listen() else permission.launch(Manifest.permission.RECORD_AUDIO)};reply(id,JSONObject())}
                        "cancel"->{runOnUiThread {cancel()};reply(id,JSONObject())}
                        "feedback"->{
                            check(!listening&&!decoding){"صبر کن شنود تمام شود"}
                            val result=checkNotNull(current){"ابتدا یک فرمان بگو"}
                            check(body.getString("text")==result.getString("text")){"نتیجهٔ شنود تغییر کرده است"}
                            val action=body.getString("action");val params=body.getJSONObject("parameters");val kind=body.getString("kind")
                            require(model.validate(action,params)){"پارامترهای فرمان معتبر نیستند"}
                            store.add(result,action,params,kind)
                            val updated=model.prediction(result.getString("text"),action,params,1.0,true);current=updated;reply(id,updated)
                        }
                        "share"->{val file=store.export();runOnUiThread {
                            try {
                                val uri=FileProvider.getUriForFile(this@MainActivity,"$packageName.feedback",file)
                                val send=Intent(Intent.ACTION_SEND).apply {type="application/octet-stream";putExtra(Intent.EXTRA_STREAM,uri);clipData=ClipData.newUri(contentResolver,"آموزش‌های رها",uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)}
                                startActivity(Intent.createChooser(send,"اشتراک‌گذاری آموزش‌ها"));reply(id,JSONObject())
                            }catch(e:Exception){reply(id,error="اشتراک‌گذاری انجام نشد")}
                        }}
                        else->error("درخواست نامعتبر")
                    }
                }catch(e:Exception){android.util.Log.w("VoiceTrainer","Request failed",e);reply(id,error=e.message?:"درخواست انجام نشد")}
            }
        }
    }
    private fun listen() {
        if(!foreground||!ready||listening||decoding)return
        listening=true;val turn=++epoch;worker.execute {current=null};speech("start")
        lab.start(false,object:SpeechLab.Listener {
            private fun active()=foreground&&!destroyed&&turn==epoch
            override fun onPartial(text:String){if(active())speech("partial","text" to text)}
            override fun onLevel(percent:Int){if(active())speech("level","level" to percent.coerceIn(0,100))}
            override fun onDecoding(){if(active()){decoding=true;speech("decoding")}}
            override fun onComplete(result:SpeechResult?){
                listening=false
                if(!active()){decoding=false;return}
                val text=result?.text.orEmpty()
                if(text.isBlank()){decoding=false;speech("error","message" to "صدایی تشخیص داده نشد؛ دوباره امتحان کن");return}
                decoding=true;worker.execute {
                    try {
                        val prediction=model.predict(text);val override=store.override(prediction.getString("text"),model.version)
                        val output=if(override==null)prediction else model.prediction(prediction.getString("text"),override.getString("action"),override.getJSONObject("parameters"),1.0,true)
                        if(active()){current=output;speech("result","result" to output)}
                    }catch(e:Exception){if(active())speech("error","message" to "بررسی فرمان انجام نشد")}
                    finally{decoding=false}
                }
            }
            override fun onError(error:Throwable){listening=false;decoding=false;if(active())speech("error","message" to "شنود انجام نشد؛ دوباره امتحان کن")}
        })
    }
    private fun cancel(){epoch++;lab.cancel();speech("cancel")}
    override fun onStart(){super.onStart();foreground=true}
    override fun onStop(){foreground=false;if(listening||decoding)cancel();super.onStop()}
    override fun onDestroy(){destroyed=true;epoch++;lab.close();worker.execute {model.close()};worker.shutdown();web.removeJavascriptInterface("AndroidTrainer");web.destroy();super.onDestroy()}
}
