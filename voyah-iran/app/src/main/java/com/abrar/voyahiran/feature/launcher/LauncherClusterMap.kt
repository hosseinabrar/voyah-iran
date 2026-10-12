package com.abrar.voyahiran.feature.launcher

import android.content.Context
import android.view.Surface
import com.abrar.voyahiran.VoyahIranApplication
import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import kotlinx.coroutines.*

/** Shared-process owner. The bridge's short lease also releases MEDIA after an app crash. */
internal object LauncherClusterMap {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var renderer: MapFrameRelay? = null
    private var task: Job? = null
    @Volatile private var session: String? = null
    @Volatile private var ready = false
    @Volatile private var output: Surface? = null
    private var retryAt=0L
    fun allowed(c: Context) = LauncherPreferences.enabled(c) && LauncherPreferences.clusterMap(c) &&
        (c.applicationContext as VoyahIranApplication).subscriptions.vipAllowed
    fun show(c: Context, relay: MapFrameRelay) {
        if (!allowed(c)) { hide(relay); return }
        if (renderer === relay && task?.isActive == true) return
        if(android.os.SystemClock.elapsedRealtime()<retryAt)return
        hide()
        renderer = relay
        val id = java.util.UUID.randomUUID().toString()
        session = id
        task = scope.launch {
            val started=android.os.SystemClock.elapsedRealtime()
            try {
                while (isActive && renderer === relay && allowed(c)) {
                    check(ready || android.os.SystemClock.elapsedRealtime()-started<12000) {"No cluster output frame"}
                    val action = if (ready) "ready" else "show"
                    val result = withContext(Dispatchers.IO) {
                        ProcessCommandRunner().run(listOf("su", "0", "cluster-map", action, id), 3)
                    }
                    check(result.exitCode == 0 && !result.timedOut) { result.output }
                    delay(750)
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    retryAt=android.os.SystemClock.elapsedRealtime()+3000
                    AppLog.error("ClusterMap", "presentation failed", e)
                }
            } finally {
                if (session == id) { renderer?.cluster(null,0,0,null); renderer=null; session=null; ready=false; output=null }
                withContext(NonCancellable + Dispatchers.IO) {
                    runCatching { ProcessCommandRunner().run(listOf("su", "0", "cluster-map", "stop", id), 3) }
                }
            }
        }
    }
    fun hide(relay: MapFrameRelay? = null) {
        if (relay != null && renderer !== relay) return
        renderer?.cluster(null,0,0,null)
        renderer=null; ready=false; session=null; output=null
        task?.cancel();task=null
    }
    fun valid(c: Context, id: String?) = id != null && session == id && renderer != null && allowed(c)
    fun surface(id: String, surface: Surface?, w: Int, h: Int) {
        if (session != id) return
        output=surface;ready=false
        renderer?.cluster(surface,w,h,if(surface==null)null else Runnable { if(session==id && output===surface)ready=true })
    }
}
