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
    private var fadeOut: Job? = null
    private var revealed = false
    @Volatile private var session: String? = null
    @Volatile private var ready = false
    @Volatile private var output: Surface? = null
    private var retryAt=0L
    val autoPolicy = ClusterMapAutoPolicy()
    fun allowed(c: Context) = LauncherPreferences.enabled(c) && LauncherPreferences.clusterMap(c) &&
        when (LauncherPreferences.clusterMapMode(c)) {
            ClusterMapMode.OFF -> false
            ClusterMapMode.ON -> true
            ClusterMapMode.AUTO -> autoPolicy.show(android.os.SystemClock.elapsedRealtime())
        } &&
        (c.applicationContext as VoyahIranApplication).subscriptions.vipAllowed
    fun show(c: Context, relay: MapFrameRelay) {
        if (!allowed(c)) { hide(relay); return }
        if (renderer === relay && task?.isActive == true) {
            if (fadeOut != null) {
                fadeOut?.cancel(); fadeOut=null
                if (revealed) relay.fadeCluster(1f,360,null)
            }
            return
        }
        if(android.os.SystemClock.elapsedRealtime()<retryAt)return
        release()
        renderer = relay
        val id = java.util.UUID.randomUUID().toString()
        session = id
        task = scope.launch {
            val started=android.os.SystemClock.elapsedRealtime()
            try {
                while (isActive && renderer === relay) {
                    if (!allowed(c)) hide(relay)
                    if (session != id) break
                    check(revealed || android.os.SystemClock.elapsedRealtime()-started<12000) {"Cluster transition did not become ready"}
                    val action = if (ready) "ready" else "show"
                    val result = withContext(Dispatchers.IO) {
                        ProcessCommandRunner().run(listOf("su", "0", "cluster-map", action, id), 3)
                    }
                    check(result.exitCode == 0 && !result.timedOut) { result.output }
                    if (session == id && ready && !revealed && fadeOut == null && result.output.contains("reveal=1")) {
                        revealed=true
                        relay.fadeCluster(1f,360,null)
                    }
                    Regex("theme=(\\d+)").find(result.output)?.groupValues?.get(1)?.toIntOrNull()?.let {
                        if(it in setOf(1,2,4)) ClusterMapCalibration.themeId=it
                    }
                    delay(if (revealed) 750 else 100)
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    retryAt=android.os.SystemClock.elapsedRealtime()+3000
                    AppLog.error("ClusterMap", "presentation failed", e)
                }
            } finally {
                if (session == id) {
                    fadeOut?.cancel();fadeOut=null
                    renderer?.cluster(null,0,0,null); renderer=null; session=null; ready=false; output=null;revealed=false
                }
                withContext(NonCancellable + Dispatchers.IO) {
                    runCatching { ProcessCommandRunner().run(listOf("su", "0", "cluster-map", "stop", id), 3) }
                }
            }
        }
    }
    fun hide(relay: MapFrameRelay? = null) {
        if (relay != null && renderer !== relay) return
        if (fadeOut != null) return
        val current=renderer
        val id=session
        if (current == null || id == null || !revealed) { release(); return }
        fadeOut=scope.launch {
            // Keep the Activity, surface and bridge lease alive until the last
            // transparent buffer has been swapped. Quick re-entry cancels this job.
            withTimeoutOrNull(700) {
                suspendCancellableCoroutine<Unit> { continuation ->
                    current.fadeCluster(0f,240,Runnable {
                        if (continuation.isActive) continuation.resumeWith(Result.success(Unit))
                    })
                }
            }
            if (session == id && renderer === current) release()
        }
    }
    private fun release() {
        fadeOut?.cancel();fadeOut=null
        renderer?.cluster(null,0,0,null)
        renderer=null; ready=false; session=null; output=null;revealed=false
        task?.cancel();task=null
    }
    // OFF/AUTO changes request a fade; do not let the Activity's 100ms watchdog
    // destroy its SurfaceView halfway through that transition.
    fun valid(c: Context, id: String?) = id != null && session == id && renderer != null &&
        (c.applicationContext as VoyahIranApplication).subscriptions.vipAllowed
    fun surface(id: String, surface: Surface?, w: Int, h: Int) {
        if (session != id) return
        output=surface;ready=false;revealed=false
        renderer?.cluster(surface,w,h,if(surface==null)null else Runnable { if(session==id && output===surface)ready=true })
    }
}
