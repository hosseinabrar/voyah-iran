package com.abrar.voyahiran.feature.launcher

import android.content.Context
import android.view.Surface
import com.abrar.voyahiran.VoyahIranApplication
import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import kotlinx.coroutines.*

/** Keeps the presentation across fullscreen trips; card presence only selects the layer. */
internal object LauncherClusterMap {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var renderer:MapFrameRelay?=null
    private var task:Job?=null
    private var leaving:Job?=null
    @Volatile private var session:String?=null
    @Volatile private var ready=false
    private val readiness=ClusterMapReadiness()
    @Volatile private var output:Surface?=null
    private var retryAt=0L
    private var retryJob:Job?=null
    private var retryRelay:MapFrameRelay?=null
    private val retry=ClusterMapRetry()
    private var lastBlend=""
    private var mapInCard=false
    @Volatile private var mediaHasContent: Boolean? = null
    // Same observation that supplies the cluster's "open multimedia" frame.
    fun mediaContent(value: Boolean?) { mediaHasContent = value }
    private var surfaceDeadline=0L
    fun cardPresence(relay:MapFrameRelay,inCard:Boolean) {
        if(!inCard && retryRelay===relay){retryJob?.cancel();retryJob=null;retryRelay=null}
        if(renderer!==relay || mapInCard==inCard)return
        mapInCard=inCard
        AppLog.info("ClusterMap","card presence=$inCard keeping session=$session")
    }
    val autoPolicy=ClusterMapAutoPolicy {AppLog.info("ClusterMapAuto",it)}
    fun available(c:Context)=LauncherPreferences.enabled(c) && LauncherPreferences.clusterMap(c) &&
        (c.applicationContext as VoyahIranApplication).subscriptions.vipAllowed
    fun allowed(c:Context)=available(c) && LauncherPreferences.clusterMapMode(c).showsMap(
        autoPolicy.show(android.os.SystemClock.elapsedRealtime()), mediaHasContent)
    private suspend fun command(action:String,id:String)=withContext(Dispatchers.IO) {
        ProcessCommandRunner().run(listOf("su","0","cluster-map",action,id),3)
    }
    fun show(c:Context,relay:MapFrameRelay) {
        if(!available(c)){hide(relay);return}
        if(renderer===relay && task?.isActive==true){cardPresence(relay,true);leaving?.cancel();leaving=null;return}
        if(android.os.SystemClock.elapsedRealtime()<retryAt){
            if(retryJob?.isActive!=true) {
                retryRelay=relay
                retryJob=scope.launch {
                    delay((retryAt-android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0))
                    retryJob=null;retryRelay=null
                    show(c,relay)
                }
            }
            return
        }
        release()
        renderer=relay;mapInCard=true
        val id=java.util.UUID.randomUUID().toString();session=id
        surfaceDeadline=android.os.SystemClock.elapsedRealtime()+12000
        task=scope.launch {
            var failed=false
            try {
                while(isActive && session==id) {
                    // Keep the last map buffer alive until the single-image fade
                    // reaches music, even when the map checkbox itself is disabled.
                    if(!available(c))hide(relay,"feature_unavailable")
                    check(!ready || output?.isValid==true){"OEM image Surface lost"}
                    check(ready || android.os.SystemClock.elapsedRealtime()<surfaceDeadline){"OEM image Surface not ready"}
                    val sentGeneration=readiness.generation
                    val action=if(leaving!=null) "music" else if(!ready) "show" else if(!readiness.acknowledged) "ready" else if(mapInCard && allowed(c)) "covered" else "music"
                    val result=command(action,id)
                    check(result.exitCode==0 && !result.timedOut){result.output}
                    fun field(key:String)=Regex("(?:^| )$key=([^ ]+)").find(result.output)?.groupValues?.get(1)
                    // The bridge may restart with the same lease ID. Its explicit
                    // acknowledgement, not a successful command exit, is authoritative.
                    readiness.reply(sentGeneration,ready,field("frameReady")=="1")
                    if(action=="ready" && readiness.acknowledged)retry.reset()
                    field("theme")?.toIntOrNull()?.takeIf{it in setOf(1,2,4)}?.let{ClusterMapCalibration.themeId=it}
                    val target=field("target")?.toFloatOrNull()
                    val from=field("from")?.toFloatOrNull()
                    val start=field("start")?.toLongOrNull()
                    val blend="$target/$from/$start"
                    if(ready && target!=null && from!=null && start!=null && blend!=lastBlend){
                        // The single OEM ImageView blends both live inputs. Never
                        // fade the producer too (that would apply alpha twice).
                        lastBlend=blend
                        AppLog.info("ClusterMap","layers blend=$blend session=$id")
                    }
                    delay(200)
                }
            } catch(e:Exception){
                if(e !is CancellationException){failed=true;retryAt=android.os.SystemClock.elapsedRealtime()+retry.nextDelay();AppLog.error("ClusterMap","layers failed",e)}
            } finally {
                val recover=failed && session==id && mapInCard && leaving==null && available(c)
                if(session==id){renderer?.cluster(null,0,0,null);renderer=null;session=null;ready=false;output=null}
                if(recover) {
                    retryRelay=relay
                    retryJob=scope.launch {
                        delay((retryAt-android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0))
                        if(retryRelay===relay && available(c)) {
                            retryJob=null;retryRelay=null
                            AppLog.info("ClusterMap","retrying failed image session")
                            show(c,relay)
                        }
                    }
                }
                withContext(NonCancellable){runCatching{command("stop",id)}}
            }
        }
    }
    fun hide(relay:MapFrameRelay?=null,reason:String="map_left_card") {
        if(relay==null || retryRelay===relay){retryJob?.cancel();retryJob=null;retryRelay=null}
        if(relay!=null && renderer!==relay)return
        if(leaving!=null)return
        val id=session ?: return
        AppLog.info("ClusterMap","release layers reason=$reason session=$id")
        leaving=scope.launch {
            val result=command("music",id)
            val from=Regex("from=([^ ]+)").find(result.output)?.groupValues?.get(1)?.toFloatOrNull()
            val start=Regex("start=(\\d+)").find(result.output)?.groupValues?.get(1)?.toLongOrNull()
            if(session==id && from!=null && start!=null){
                delay((start+400-android.os.SystemClock.elapsedRealtime()).coerceIn(0L,800L))
            }
            if(session==id)release()
        }
    }
    private fun release(){
        readiness.invalidate()
        retryJob?.cancel();retryJob=null;retryRelay=null
        leaving?.cancel();leaving=null
        task?.cancel();task=null
        renderer?.cluster(null,0,0,null);renderer=null;session=null;ready=false;output=null;lastBlend="";mapInCard=false
    }
    fun valid(c:Context,id:String?)=id!=null && session==id && renderer!=null && available(c)
    fun surface(id:String,surface:Surface?,w:Int,h:Int){
        if(session!=id)return
        readiness.invalidate()
        output=surface;ready=false;lastBlend=""
        val generation=readiness.generation
        surfaceDeadline=android.os.SystemClock.elapsedRealtime()+12000
        renderer?.cluster(surface,w,h,if(surface==null)null else Runnable{if(session==id && output===surface && readiness.generation==generation)ready=true})
    }
}
