package com.abrar.voyahiran.feature.launcher

import android.content.Context
import android.view.SurfaceView
import android.view.SurfaceHolder
import android.view.View
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Read-only monitoring; passenger ownership changes only on an explicit button press. */
class LauncherMapPreview(private val context: Context, private val scope: CoroutineScope, private val onMessage: (String) -> Unit, private val view:SurfaceView) : SurfaceHolder.Callback {
    private var mapInCard=false
    private var recenteredDisplay: Int? = null
    private var cardSeenAt = 0L
    // The framework applies launcher theme when the map resolves its configuration
    // on our owned display, and app theme again when it returns to a physical one.
    private var autoMonitor: Job? = null
    private fun wantsAuto() = !closed && mapInCard &&
        packageName == com.abrar.voyahiran.feature.navigation.NavigationPreferences.NESHAN &&
        LauncherPreferences.autoClusterRequested(context)
    fun refreshCluster() {
        if (!wantsAuto()) {
            autoMonitor?.cancel(); autoMonitor = null
            LauncherClusterMap.autoPolicy.reset("auto_not_requested closed=$closed mapInCard=$mapInCard package=$packageName")
        } else if (autoMonitor?.isActive != true) {
            LauncherClusterMap.autoPolicy.reset("monitor_started")
            autoMonitor = scope.launch {
                while (isActive && wantsAuto()) {
                    try {
                        val result = withContext(Dispatchers.IO) {
                            ProcessCommandRunner().run(listOf("su", "0", "neshan-direct", "auto-state"), 3)
                        }
                        // An in-flight response must not override a new button/map/subscription state.
                        if (!wantsAuto()) break
                        val json = result.output.lineSequence().firstOrNull { it.startsWith("NESHAN_AUTO=") }
                        val data = if (result.exitCode == 0 && !result.timedOut && json != null)
                            org.json.JSONObject(json.removePrefix("NESHAN_AUTO=")) else null
                        if (data?.optBoolean("valid") == true) {
                            LauncherClusterMap.autoPolicy.update(data.getString("step"), data.getInt("meters"),
                                data.getLong("sampledAt"), android.os.SystemClock.elapsedRealtime(),
                                (data.opt("speedKmh") as? Number)?.toInt())
                        } else {
                            LauncherClusterMap.autoPolicy.unavailable(android.os.SystemClock.elapsedRealtime())
                            com.abrar.voyahiran.core.logging.AppLog.changed("ClusterMapAuto", "sample unavailable exit=${result.exitCode} timedOut=${result.timedOut} reason=${data?.optString("reason") ?: "missing_response"}; retaining unexpired sample")
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        LauncherClusterMap.autoPolicy.unavailable(android.os.SystemClock.elapsedRealtime())
                        com.abrar.voyahiran.core.logging.AppLog.changed("ClusterMapAuto", "navigation unavailable: ${e.message}")
                    }
                    embedded.cluster(mapInCard)
                    delay(500)
                }
            }
        }
        embedded.cluster(mapInCard)
    }
    private var clusterMonitor: Job?=null
    private fun error(message: String) {
        mapInCard=message.isEmpty() && !fullscreen && !closed
        refreshCluster()
        if(mapInCard && LauncherPreferences.clusterMap(context) && clusterMonitor?.isActive!=true) clusterMonitor=scope.launch {
            while(isActive && !closed && mapInCard) {
                delay(750)
                if(active || busy)continue
                val id=embedded.displayId ?: break
                val status=withContext(Dispatchers.IO) {
                    ProcessCommandRunner().run(listOf("su","0","launcher-map-status",packageName,id.toString()),4)
                }
                if(!mapInCard || closed)break
                if(status.exitCode==0 && !status.timedOut) {
                    mapInCard=isVisibleHere(status.output,id)
                    if(!mapInCard){recenteredDisplay=null;cardSeenAt=0L}
                    refreshCluster()
                } else if(status.exitCode!=75) {mapInCard=false;refreshCluster()}
            }
        }
        onMessage(message)
    }
    private var departingUntil = 0L
    private val transferObserver = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            runCatching {
                val data = org.json.JSONObject(android.provider.Settings.Global.getString(context.contentResolver, "voyah_iran_map_transfer") ?: "{}")
                val age = System.currentTimeMillis() - data.optLong("time")
                if (!closed && data.optString("pkg") == packageName && age in 0..12000) {
                    recenteredDisplay=null;cardSeenAt=0L
                    departingUntil = android.os.SystemClock.elapsedRealtime() + if(data.optString("state")=="start") 12000 else 1000
                    error("نقشه روی نمایشگر شاگرد است")
                }
            }
        }
    }
    init {
        LauncherMapWakeSession.observe(context)
        context.contentResolver.registerContentObserver(android.provider.Settings.Global.getUriFor("voyah_iran_map_transfer"), false, transferObserver)
    }
    private val embedded=LauncherEmbeddedMapSurface(context,view)
    private var holder:SurfaceHolder?=null
    private var surfaceWidth=0
    private var surfaceHeight=0
    private var active=false
    private var job: Job? = null
    private var monitor: Job? = null
    private var packageName = ""
    private var closed = false
    private var initialAttempted=false
    private var busy=false
    private val moves=Mutex()
    // Handoff must survive Activity destruction; canceling it can strand the live task.
    private val handoffScope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var handoff:Job?=null
    var fullscreen = false
        private set
    fun packageName(value: String) { packageName = value; currentPreview=java.lang.ref.WeakReference(this) }
    override fun surfaceCreated(h:SurfaceHolder) {
        holder=h
        surfaceWidth=view.width;surfaceHeight=view.height
        if(surfaceWidth>0 && surfaceHeight>0)attachSurface()
    }
    override fun surfaceChanged(h:SurfaceHolder,format:Int,w:Int,hgt:Int) {
        holder=h;surfaceWidth=w;surfaceHeight=hgt
        attachSurface()
    }
    private fun attachSurface() {
        if(ensureDisplay() && active && !initialAttempted) {
            initialAttempted=true
            move(embedded.displayId!!,false)
        }
    }
    private fun ensureDisplay(): Boolean {
        if(closed || packageName.isBlank()) {error("نقشهٔ پیش‌فرض را در تنظیمات انتخاب کنید");return false}
        val h=holder ?: return false
        return try {
            embedded.attach(h,surfaceWidth,surfaceHeight)
            embedded.active(active)
            com.abrar.voyahiran.core.logging.AppLog.info("LauncherMap","native surface attached display=${embedded.displayId} size=${surfaceWidth}x$surfaceHeight")
            true
        } catch(e:Exception) {
            embedded.detach()
            error("نقشه در دسترس نیست")
            com.abrar.voyahiran.core.logging.AppLog.error("LauncherMap","native surface attachment failed",e)
            false
        }
    }
    private fun move(id: Int, claim: Boolean, done: (() -> Unit)? = null) {
        if(closed || busy)return
        recenteredDisplay=null;cardSeenAt=0L
        busy=true
        mapInCard=false;embedded.cluster(false)
        departingUntil=0L
        error(LOADING)
        job=scope.launch {
            var mask: LauncherTransferMask? = null
            var centralReady = false
            try {
                moves.withLock {
                    if(closed || ((fullscreen || !active) && id!=0))return@withLock
                    if (id == 0) {
                        mask = LauncherTransferMask(context,packageName)
                        mask?.show()
                    }
                    val clean = !claim && id != 0 && packageName.isNotBlank() &&
                        LauncherMapWakeSession.pending &&
                        (context.applicationContext as com.abrar.voyahiran.VoyahIranApplication).subscriptions.vipAllowed &&
                        com.abrar.voyahiran.feature.navigation.NeshanVoiceNavigation.pending == null
                    val generation = LauncherMapWakeSession.generation
                    if (clean) LauncherMapWakeSession.restarting = true
                    val command=if(clean) "launcher-map-clean" else if(claim) "launcher-map-claim" else "launcher-map"
                    val r=try { transferWithBusyRetry(command,id) }
                        finally { if (clean) LauncherMapWakeSession.restarting = false }
                    if (clean) {
                        // One attempt per wake, including unsupported/failed force-stop, avoids a restart loop.
                        LauncherMapWakeSession.handled = generation
                        com.abrar.voyahiran.core.logging.AppLog.info("LauncherMap", "clean session exit=${r.exitCode} ${r.output}")
                    }
                    if(r.exitCode!=0 || r.timedOut || r.output.contains("Error:")) {
                        error(if(r.output.contains("passenger display")) "نقشه روی نمایشگر شاگرد است" else "نقشه در دسترس نیست")
                        com.abrar.voyahiran.core.logging.AppLog.info("LauncherMap",r.output)
                    } else if(id==0) {
                        fullscreen=true;initialAttempted=false;error("");done?.invoke()
                        centralReady = withTimeoutOrNull(8000) {
                            var stable = 0
                            while (!closed) {
                                val status = withContext(Dispatchers.IO) {
                                    ProcessCommandRunner().run(listOf("su","0","launcher-map-status",packageName,"0"),4)
                                }
                                stable = if (status.exitCode == 0 && !status.timedOut && isVisibleHere(status.output,0) &&
                                    status.output.contains("MAP_FITTED=1")) stable + 1 else 0
                                if (stable >= 3) return@withTimeoutOrNull true
                                delay(120)
                            }
                            false
                        } == true
                        com.abrar.voyahiran.core.logging.AppLog.info("LauncherMap", "central transfer fitted=$centralReady")
                    }
                    else {
                        // Keep the opaque card above SurfaceView until both ownership and output are ready.
                        check(awaitCardReady(id)) { "Map transfer did not produce a ready card surface" }
                        if(!closed && active && embedded.displayId == id) error("")
                    }
                }
            } catch(e:CancellationException) {throw e}
            catch(e:Exception) {error("نقشه در دسترس نیست");com.abrar.voyahiran.core.logging.AppLog.error("LauncherMap","move failed",e)}
            finally {mask?.finish(centralReady);busy=false}
        }
    }
    private suspend fun transferWithBusyRetry(command:String,id:Int):com.abrar.voyahiran.core.shell.CommandResult {
        val started=android.os.SystemClock.elapsedRealtime()
        var attempt=0
        while(true) {
            currentCoroutineContext().ensureActive()
            if(closed || (id!=0 && (!active || fullscreen || embedded.displayId!=id)))
                throw CancellationException("Map transfer owner changed")
            attempt++
            // Finish each dispatched request even if the view stops, but never
            // shield the retry delay from cancellation or resend an unknown result.
            val result=withContext(Dispatchers.IO + NonCancellable) {
                ProcessCommandRunner().run(listOf("su","0",command,packageName,id.toString()),15)
            }
            val elapsed=android.os.SystemClock.elapsedRealtime()-started
            val retry=MapTransferRetryPolicy.shouldRetry(result.exitCode,result.timedOut,result.output,attempt,elapsed)
            com.abrar.voyahiran.core.logging.AppLog.info("LauncherMap",
                "transfer command=$command display=$id attempt=$attempt elapsedMs=$elapsed exit=${result.exitCode} timedOut=${result.timedOut} retry=$retry")
            if(!retry)return result
            delay(150)
        }
    }
    private suspend fun awaitCardReady(id: Int): Boolean {
        return withTimeoutOrNull(12_000L) {
            var confirmations = 0
            while (!closed && active && !fullscreen && embedded.displayId == id) {
                val status = withContext(Dispatchers.IO) {
                    ProcessCommandRunner().run(listOf("su", "0", "launcher-map-status", packageName, id.toString()), 4)
                }
                val ready = status.exitCode == 0 && !status.timedOut && isVisibleHere(status.output, id) &&
                    embedded.attached && outputReady()
                confirmations = if (ready) confirmations + 1 else 0
                if (confirmations >= 2) return@withTimeoutOrNull true
                delay(150)
            }
            false
        } ?: false
    }
    /** PixelCopy reports whether the live SurfaceView has a buffer; never expose the anchor alone. */
    private suspend fun outputReady(): Boolean {
        val bitmap = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
        val result = CompletableDeferred<Boolean>()
        try {
            android.view.PixelCopy.request(view, bitmap, { code ->
                bitmap.recycle()
                result.complete(code == android.view.PixelCopy.SUCCESS)
            }, android.os.Handler(android.os.Looper.getMainLooper()))
        } catch (_: IllegalArgumentException) {
            bitmap.recycle()
            return false
        }
        // The callback owns the bitmap even if the transfer is cancelled while copying.
        return withTimeoutOrNull(1000) { result.await() } ?: false
    }
    fun showInCard() {
        if(ensureDisplay()) move(embedded.displayId!!,true)
    }
    fun open(done: () -> Unit) { if(packageName.isNotBlank())move(0,false,done) }
    fun startMonitoring() {
        if(closed)return
        // A page change must not reattach/resize an already live virtual display.
        if(active && !fullscreen && monitor?.isActive==true)return
        active=true
        fullscreen=false
        view.visibility=View.VISIBLE
        runCatching {embedded.active(true)}.onFailure {error("نقشه در دسترس نیست")}
        if(holder!=null)attachSurface()
        if(monitor?.isActive==true)return
        monitor=scope.launch {
            while(isActive && !closed && !fullscreen) {
                val id=embedded.displayId
                if(id!=null && embedded.attached && !busy) {
                    try { moves.withLock {
                        if(busy || closed || fullscreen)return@withLock
                        val r=withContext(Dispatchers.IO) {
                            ProcessCommandRunner().run(listOf("su","0","launcher-map-status",packageName,id.toString()),4)
                        }
                        if(busy || !active || !embedded.attached || embedded.displayId!=id || android.os.SystemClock.elapsedRealtime()<departingUntil)return@withLock
                        if(r.exitCode==0 && !r.timedOut) {
                            if (isVisibleHere(r.output,id) && LauncherMapWakeSession.pending &&
                                packageName.isNotBlank() &&
                                com.abrar.voyahiran.feature.launcher.VoyahLauncherActivity.mapPageVisible &&
                                com.abrar.voyahiran.feature.navigation.NeshanVoiceNavigation.pending == null &&
                                (context.applicationContext as com.abrar.voyahiran.VoyahIranApplication).subscriptions.vipAllowed) {
                                move(id, false)
                                return@withLock
                            }
                            val inCard=isVisibleHere(r.output,id)
                            error(if(inCard) "" else if(r.output.contains("MAP_DISPLAY=1 ")) "نقشه روی نمایشگر شاگرد است" else "نقشه در دسترس نیست")
                            if(!inCard) {recenteredDisplay=null;cardSeenAt=0L}
                            else if(packageName==com.abrar.voyahiran.feature.navigation.NavigationPreferences.NESHAN && recenteredDisplay!=id) {
                                val now=android.os.SystemClock.elapsedRealtime()
                                if(cardSeenAt==0L)cardSeenAt=now
                                // Wait for the resized card layout, once per arrival, not each heartbeat.
                                if(now-cardSeenAt>=500) {
                                    val arrival=cardSeenAt
                                    val camera=withContext(Dispatchers.IO) {
                                        com.abrar.voyahiran.core.bridge.BridgeClient.run(listOf("neshan-direct","recenter",id.toString()),3)
                                    }
                                    // A reply from a previous arrival must not complete the current one.
                                    if(closed || fullscreen || !active || !mapInCard || embedded.displayId!=id || cardSeenAt!=arrival)return@withLock
                                    val confirmed=camera.exitCode==0 && !camera.timedOut && camera.output.startsWith("RECENTER_RESULT=applied ")
                                    val accepted=camera.exitCode==0 && !camera.timedOut && camera.output.startsWith("RECENTER_RESULT=accepted ")
                                    val exhausted=android.os.SystemClock.elapsedRealtime()-arrival>=5000
                                    com.abrar.voyahiran.core.logging.AppLog.info("NeshanRecenter",
                                        "display=$id arrival=$arrival accepted=$accepted confirmed=$confirmed exhausted=$exhausted exit=${camera.exitCode} timedOut=${camera.timedOut} result=${camera.output.take(700)}")
                                    if(accepted || confirmed || exhausted)recenteredDisplay=id
                                }
                            }
                        } else if(r.exitCode!=75) error("نقشه در دسترس نیست")
                    } } catch(e:CancellationException) {throw e}
                    catch(e:Exception) {error("نقشه در دسترس نیست")}
                }
                delay(if(LauncherPreferences.clusterMap(context)) 750 else 2000)
            }
        }
    }
    fun stopMonitoring() {
        if(fullscreen || closed) {mapInCard=false;refreshCluster()}
        active=false;monitor?.cancel();monitor=null
        runCatching {embedded.active(false)}
        view.visibility=View.INVISIBLE
    }
    fun hostStopped() {
        // Opening a task may briefly pause/stop HOME while the system reparents it.
        // Do not turn the source display off or start a competing handoff mid-transfer.
        if(busy && !closed)return
        stopMonitoring()
        // Covering HOME must not move its map or request another launch on resume.
        // Only final disposal releases ownership of the virtual display.
        if(!closed)return
        initialAttempted=false
        if(handoff?.isActive==true)return
        val id=embedded.displayId ?: run { if(closed)handoffScope.cancel();return }
        val pkg=packageName
        handoff=handoffScope.launch {
            moves.withLock {
                val result=withContext(Dispatchers.IO) {
                    ProcessCommandRunner().run(listOf("su","0","launcher-map-release",pkg,id.toString()),10)
                }
                com.abrar.voyahiran.core.logging.AppLog.info("LauncherMap","host handoff display=$id exit=${result.exitCode} ${result.output}")
                if(closed) {embedded.close();handoffScope.cancel()}
            }
        }
    }
    override fun surfaceDestroyed(h:SurfaceHolder) {
        holder=null
        if(closed) embedded.detach() else runCatching { embedded.park() }.onFailure {
            embedded.detach()
            com.abrar.voyahiran.core.logging.AppLog.error("LauncherMap","background output failed",it)
        }
        // Losing a window surface is not loss of the map task. Preserve card state.
    }
    fun close() {
        autoMonitor?.cancel();autoMonitor=null
        LauncherClusterMap.autoPolicy.reset()
        clusterMonitor?.cancel();clusterMonitor=null
        embedded.cluster(false)
        if(currentPreview?.get() === this) currentPreview=null
        context.contentResolver.unregisterContentObserver(transferObserver)
        closed=true;hostStopped()
        view.holder.removeCallback(this)
        holder=null
    }
    companion object {
        private var currentPreview: java.lang.ref.WeakReference<LauncherMapPreview>? = null
        internal suspend fun openHostedMapOnPassenger(context:Context,pkg:String,launch:()->Unit):Boolean {
            val preview=currentPreview?.get() ?: return false
            if(preview.closed || pkg.isBlank() || preview.packageName!=pkg)return false
            return preview.moves.withLock {
                if(preview.busy)return@withLock true
                val id=preview.embedded.displayId ?: return@withLock false
                val status=withContext(Dispatchers.IO) {
                    ProcessCommandRunner().run(listOf("su","0","launcher-map-status",pkg,"0"),4)
                }
                if(status.exitCode!=0 || status.timedOut || !isVisibleHere(status.output,id))return@withLock false
                val mask=LauncherTransferMask(context,pkg,1)
                var ready=false
                preview.busy=true
                preview.departingUntil=android.os.SystemClock.elapsedRealtime()+12_000
                preview.recenteredDisplay=null;preview.cardSeenAt=0L
                preview.mapInCard=false;preview.embedded.cluster(false)
                try {
                    mask.show()
                    launch()
                    ready=withTimeoutOrNull(8000) {
                        var stable=0
                        while(true) {
                            val result=withContext(Dispatchers.IO) {
                                ProcessCommandRunner().run(listOf("su","0","launcher-map-status",pkg,"0"),4)
                            }
                            stable=if(result.exitCode==0 && !result.timedOut &&
                                isVisibleHere(result.output,1) && result.output.contains("MAP_FITTED=1"))stable+1 else 0
                            if(stable>=3)break
                            delay(120)
                        }
                        true
                    }==true
                    com.abrar.voyahiran.core.logging.AppLog.info("LauncherMap","passenger transfer pkg=$pkg fitted=$ready")
                    true
                } finally {
                    mask.finish(ready)
                    preview.busy=false
                    preview.departingUntil=android.os.SystemClock.elapsedRealtime()+1000
                }
            }
        }
        /** Drawer launches of the hosted map must use the same handoff as tapping its card. */
        internal fun openHostedMap(pkg: String): Boolean {
            val preview=currentPreview?.get() ?: return false
            if(preview.closed || pkg.isBlank() || preview.packageName!=pkg) return false
            if(!preview.busy) {
                com.abrar.voyahiran.core.logging.AppLog.info("LauncherMap", "drawer uses card transfer pkg=$pkg")
                preview.open { preview.stopMonitoring() }
            }
            // Do not issue a competing normal startActivity while an existing handoff is busy.
            return true
        }

        internal const val LOADING = "در حال نمایش نقشه…"
        internal fun isVisibleHere(output: String, displayId: Int): Boolean =
            Regex("(?:^|\\s)MAP_DISPLAY=$displayId MAP_VISIBLE=1(?:$|\\s)").containsMatchIn(output)
    }
}
