package com.abrar.voyahiran.feature.launcher

import android.content.*
import android.content.pm.ApplicationInfo
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.abrar.voyahiran.VoyahIranApplication
import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import kotlinx.coroutines.*
import org.json.JSONObject
import java.util.UUID

internal data class LauncherAppsEntry(val id:String,val label:String,val icon:Drawable?,val component:ComponentName?=null)

/** Process cache: one in-flight catalog shared by HOME warmup and the app drawer. */
internal object LauncherAppsCache {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var app:Context?=null
    private var generation=0
    private var theme=false
    private var work:Deferred<List<LauncherAppsEntry>>?=null
    private var cached:List<LauncherAppsEntry>?=null
    private val listeners=linkedSetOf<(List<LauncherAppsEntry>)->Unit>()
    /** Main-thread observers share one catalog load across both displays and favorites. */
    fun observe(context:Context,listener:(List<LauncherAppsEntry>)->Unit):()->Unit {
        listeners+=listener
        warm(context)
        cached?.let(listener)
        return { listeners-=listener }
    }
    private val pending=mutableMapOf<String,CompletableDeferred<JSONObject>>()
    private val chunks=mutableMapOf<String,Array<String?>>()
    private val receiver=object:BroadcastReceiver() {
        override fun onReceive(context:Context,intent:Intent) {
            if(intent.action=="com.abrar.voyahiran.DRAWER_RESULT") {
                val nonce=intent.getStringExtra("nonce") ?: return
                val waiting=pending[nonce] ?: return
                runCatching {
                    var text=intent.getStringExtra("payload").orEmpty()
                    if(intent.hasExtra("parts")) {
                        val count=intent.getIntExtra("parts",0)
                        val index=intent.getIntExtra("part",-1)
                        check(count in 1..44 && index in 0 until count && text.length<=8000)
                        val parts=chunks.getOrPut(nonce) {arrayOfNulls(count)}
                        check(parts.size==count)
                        parts[index]=text
                        if(parts.any {it==null})return
                        text=parts.joinToString("") {it.orEmpty()}
                    }
                    check(text.length<=350000)
                    JSONObject(text)
                }.fold(waiting::complete,waiting::completeExceptionally)
                chunks.remove(nonce);pending.remove(nonce)
            } else {
                if(intent.action==Intent.ACTION_PACKAGE_REMOVED && intent.getBooleanExtra(Intent.EXTRA_REPLACING,false))return
                if(intent.action==Intent.ACTION_PACKAGE_REMOVED) {
                    val pkg=intent.data?.schemeSpecificPart
                    cached?.filter {it.component?.packageName!=pkg}?.let {remaining->
                        listeners.toList().forEach { listener->runCatching {listener(remaining)} }
                    }
                }
                invalidate()
                warm(context)
            }
        }
    }
    private fun allowed(c:Context)=LauncherPreferences.enabled(c) && (c as VoyahIranApplication).subscriptions.vipAllowed
    fun warm(context:Context) {
        val c=context.applicationContext
        if(app==null) {
            app=c;theme=LauncherTheme.isLight()
            val replies=IntentFilter("com.abrar.voyahiran.DRAWER_RESULT").apply {addAction("theme_loaded")}
            val packages=IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED);addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED);addAction(Intent.ACTION_PACKAGE_REPLACED);addDataScheme("package")
            }
            if(android.os.Build.VERSION.SDK_INT>=33) {
                c.registerReceiver(receiver,replies,Context.RECEIVER_EXPORTED)
                c.registerReceiver(receiver,packages,Context.RECEIVER_EXPORTED)
            } else {
                c.registerReceiver(receiver,replies);c.registerReceiver(receiver,packages)
            }
            scope.launch {
                while(isActive) {
                    delay(1000)
                    if(theme!=LauncherTheme.isLight()) {invalidate();warm(c)}
                }
            }
        }
        if(theme!=LauncherTheme.isLight())invalidate()
        if(!allowed(c) || cached!=null || work?.isActive==true)return
        val revision=generation
        val selectedTheme=theme
        work=scope.async {
            try {
                val result=load(c,selectedTheme)
                check(revision==generation) {"Catalog invalidated"}
                cached=result
                LauncherAppsTiles.warm(c,result,selectedTheme)
                listeners.toList().forEach {listener->runCatching {listener(result)}.onFailure {AppLog.error("LauncherApps","Catalog observer failed",it)}}
                AppLog.info("LauncherApps","background catalog ready total="+result.size+" light="+selectedTheme)
                result
            } catch(e:Exception) {
                if(e !is CancellationException) {
                    AppLog.error("LauncherApps","background preparation failed",e)
                    scope.launch {delay(5000);warm(c)}
                }
                throw e
            }
        }
    }
    fun invalidate() {
        LauncherAppsTiles.invalidate()
        generation++;theme=LauncherTheme.isLight();cached=null;work?.cancel();work=null
    }
    suspend fun get(context:Context):List<LauncherAppsEntry> {
        while(true) {
            warm(context)
            cached?.let{return it}
            val current=work ?: error("Launcher unavailable")
            try {return current.await()}
            catch(e:CancellationException) {
                currentCoroutineContext().ensureActive()
                if(current===work)throw IllegalStateException("Catalog preparation timed out",e)
            }
        }
    }
    private suspend fun load(context:Context,light:Boolean):List<LauncherAppsEntry> {
        val nonce=UUID.randomUUID().toString().replace("-","")
        val response=CompletableDeferred<JSONObject>();pending[nonce]=response
        val catalog=try {
            val sent=withContext(Dispatchers.IO) {ProcessCommandRunner().run(listOf("su","0","launcher-drawer","catalog",nonce,"all"),5)}
            check(sent.exitCode==0 && !sent.timedOut){sent.output}
            withTimeout(8000){response.await()}.also {check(!it.has("error")){it.optString("error")}}
        } finally {pending.remove(nonce);chunks.remove(nonce)}
        return withContext(Dispatchers.IO) {
            val resources=context.resources
            val packageManager=context.packageManager
            val packageName=context.packageName
                    val oem=catalog.getJSONArray("items")
                    val rows=mutableListOf<LauncherAppsEntry>()
                    for(i in 0 until oem.length()) {
                        val item=oem.getJSONObject(i);val id=item.getString("subtype")
                        if(!LauncherAppsPolicy.visible(id))continue
                        val bytes=android.util.Base64.decode(item.optString("icon"),android.util.Base64.DEFAULT)
                        val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size)
                        rows+=LauncherAppsEntry(id,item.getString("label"),LauncherAppIcon.create(resources,bitmap?.let {BitmapDrawable(resources,it)}))
                    }
                    val installed=packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)
                        .filter { it.activityInfo.enabled && it.activityInfo.exported && it.activityInfo.applicationInfo.enabled &&
                            (it.activityInfo.applicationInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))==0 }
                        .distinctBy { ComponentName(it.activityInfo.packageName,it.activityInfo.name) }
                        .map {
                            val own=it.activityInfo.packageName==packageName
                            val icon=if(own && light)resources.getDrawable(com.abrar.voyahiran.R.drawable.launcher_voyah_icon_light,null) else it.loadIcon(packageManager)
                            LauncherAppsEntry(it.activityInfo.packageName+"/"+it.activityInfo.name,it.loadLabel(packageManager).toString(),LauncherAppIcon.create(resources,icon,rounded=own),ComponentName(it.activityInfo.packageName,it.activityInfo.name))
                        }
                        .sortedBy { it.label.lowercase() }
                    rows+installed
        }
    }
}
