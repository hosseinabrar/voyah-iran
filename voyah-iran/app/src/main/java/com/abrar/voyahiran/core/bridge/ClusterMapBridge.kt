package com.abrar.voyahiran.core.bridge

import android.content.Intent
import android.os.*
import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.core.shell.CommandResult
import dalvik.system.PathClassLoader
import java.io.File
import java.lang.reflect.Proxy
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Owns only a temporary MEDIA presentation, never navigation or persistent cluster settings. */
internal object ClusterMapBridge {
    @Volatile var presenting=false
        private set
    private const val MEDIA="qg.car.cluster.MEDIA"
    private const val SMALL="qg.car.cluster.MEDIA_SMALL"
    private var api: Api?=null
    private var session: String?=null
    private var renewed=0L
    private var hasFrame=false
    private var revealed=false
    private var bootstrapped=false
    private var launchAt=0L
    private var launchRequested=false
    private var startedAt=0L
    private var timer=false
    @Synchronized fun command(action: String,id: String): CommandResult {
        if(action=="stop") { if(session==id)stop("client_stop");return CommandResult(0,"Map released") }
        if(!BridgeSubscriptionSession.vipActive)return CommandResult(126,"VIP inactive")
        try {
            if(session!=id) {
                stop("session_replaced new=$id")
                val client=Api();api=client;session=id;hasFrame=false;revealed=false
                bootstrapped=false;launchAt=0L;launchRequested=false;startedAt=SystemClock.elapsedRealtime()
                ClusterMusicBridge.beginMapTransition()
                ClusterMusicBridge.start()
                client.call("syncReadyState",MEDIA,1)
                client.call("requestPresentation",SMALL,1)
                AppLog.info("ClusterMap","waiting for media display session=$id")
            }
            renewed=SystemClock.elapsedRealtime()
            if(action=="ready" && !hasFrame) {
                hasFrame=true
                AppLog.info("ClusterMap","map frame confirmed session=$id")
            }
            if(action=="covered" && hasFrame) presenting=true
            if(action=="music" && presenting) {
                ClusterMusicBridge.beginMusicTransition()
                presenting=false
            }
            if(!timer) {
                timer=true
                Executors.newSingleThreadScheduledExecutor { r->Thread(r,"ClusterMapLease").apply {isDaemon=true} }
                    .scheduleWithFixedDelay(::tick,0,100,TimeUnit.MILLISECONDS)
            }
            val theme=runCatching {api?.theme()}.getOrNull()
            return CommandResult(0,"Map lease renewed theme=${theme ?: -1} reveal=${if(revealed) 1 else 0} musicReady=${if(ClusterMusicBridge.musicReady) 1 else 0}")
        } catch(e:Exception) {stop("command_error action=$action error=${e.cause ?: e}");return CommandResult(1,"Cluster map unavailable: ${e.cause ?: e}")}
    }
    @Synchronized private fun tick() {
        if(session==null)return
        if(!BridgeSubscriptionSession.vipActive) {stop("vip_inactive");return}
        if(SystemClock.elapsedRealtime()-renewed>3500) {stop("lease_expired");return}
        try {
            val client=api ?: return
            val now=SystemClock.elapsedRealtime()
            if(!hasFrame && now-startedAt>12_000) {
                AppLog.info("ClusterMap","map startup timed out mediaReady=${client.mediaReady} launchRequested=$launchRequested")
                stop("startup_timeout");return
            }
            // OEM creates the MEDIA virtual display from notifyReadyToShow, not
            // startClusterActivity. Bootstrap with music still visible; waiting
            // for a map frame here would deadlock when MEDIA does not exist yet.
            if(!client.mediaReady && client.requested && !bootstrapped) {
                bootstrapped=true
                client.type.getMethod("notifyReadyToShow",String::class.java).invoke(client.remote,SMALL)
            }
            if(client.mediaReady && !hasFrame && (!launchRequested || now-launchAt>=1000)) {
                val intent=Intent().setClassName("com.abrar.voyahiran","com.abrar.voyahiran.feature.launcher.ClusterMapActivity")
                    .addCategory(MEDIA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    .putExtra("session",session)
                client.type.getMethod("startClusterActivity",Intent::class.java).invoke(client.remote,intent)
                launchRequested=true;launchAt=now
                // The vendor API returns void even on rejection. Only ready from
                // the actual rendered surface is confirmation, never this call.
                AppLog.info("ClusterMap","map launch requested session=$session")
            }
            // Reveal over the still-visible music; hide music only after the client
            // confirms that the final opaque map frame has been swapped.
            if(hasFrame && client.requested) {
                client.requested=false
                client.type.getMethod("notifyReadyToShow",String::class.java).invoke(client.remote,SMALL)
                revealed=true
            }
        } catch(e:Exception) {AppLog.error("ClusterMap","ready failed",e);stop("ready_error error=${e.cause ?: e}")}
    }
    private fun stop(reason: String) {
        if(session != null) AppLog.info("ClusterMap", "stop reason=$reason session=$session leaseAgeMs=${SystemClock.elapsedRealtime()-renewed} hasFrame=$hasFrame revealed=$revealed launchRequested=$launchRequested")
        val client=api;val restore=launchRequested
        api=null;session=null;hasFrame=false;revealed=false;presenting=false
        if(client!=null) {
            // MEDIA is shared, not a map-only presentation. Closing it does not restore
            // the OEM task displaced by startClusterActivity. Restore the stock owner;
            // its onResume/callback supplies syncReadyState and notifyReadyToShow.
            // Do not close the shared MEDIA display before restoring its owner:
            // that can invalidate the very category needed by the restore call.
            if(restore && client.mediaReady) runCatching {
                val intent=Intent().setClassName("com.qinggan.instrumentcard","com.qinggan.instrumentcard.ScreenActivity")
                    .addCategory(MEDIA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                client.type.getMethod("startClusterActivity",Intent::class.java).invoke(client.remote,intent)
                client.call("requestPresentation",SMALL,1)
                AppLog.info("ClusterMap","map released; OEM media restore requested")
            }.onFailure {AppLog.error("ClusterMap","OEM media restore failed",it)}
            runCatching {client.type.getMethod("unregisterCallback",client.callbackType).invoke(client.remote,client.callback)}
        }
    }
    private class Api {
        val type: Class<*>
        val remote: Any
        val callbackType: Class<*>
        val callback: Any
        @Volatile var requested=false
        @Volatile var mediaReady=false
        init {
            val path="/system/priv-app/VehicleSetting-release-signed/VehicleSetting-release-signed.apk"
            check(File(path).isFile) {"Vehicle SDK missing"}
            val loader=object:PathClassLoader(path,"/system/lib64:/system/lib",javaClass.classLoader) {
                override fun loadClass(name:String,resolve:Boolean):Class<*> {
                    if(!name.startsWith("com.qinggan.cluster."))return super.loadClass(name,resolve)
                    synchronized(this) {val c=findLoadedClass(name) ?: findClass(name);if(resolve)resolveClass(c);return c}
                }
            }
            type=loader.loadClass("com.qinggan.cluster.IInstrumentClusterManagerService")
            val binder=Class.forName("android.os.ServiceManager").getMethod("getService",String::class.java).invoke(null,"qg.cluster.service") as? IBinder ?: error("Cluster unavailable")
            check(binder.interfaceDescriptor==type.name)
            remote=checkNotNull(loader.loadClass(type.name+"\$Stub").getMethod("asInterface",IBinder::class.java).invoke(null,binder))
            callbackType=loader.loadClass("com.qinggan.cluster.IInstrumentClusterManagerCallback")
            val stub=loader.loadClass(callbackType.name+"\$Stub")
            val presentation=stub.getDeclaredField("TRANSACTION_setPresentationState").apply {isAccessible=true}.getInt(null)
            val activityState=stub.getDeclaredField("TRANSACTION_setClusterActivityState").apply {isAccessible=true}.getInt(null)
            val receiver=object:Binder() {
                override fun onTransact(code:Int,data:Parcel,reply:Parcel?,flags:Int):Boolean {
                    if(code==INTERFACE_TRANSACTION){reply?.writeString(callbackType.name);return true}
                    data.enforceInterface(callbackType.name)
                    if(code==presentation) {data.readString();val category=data.readString();val state=data.readInt();if(category==SMALL)requested=state==1}
                    if(code==activityState) {
                        data.readString();val category=data.readString()
                        val state=if(data.readInt()!=0) Bundle.CREATOR.createFromParcel(data) else null
                        // visible=false still means launch options exist: OEM
                        // publishes this callback after registering the display.
                        if(category==MEDIA) mediaReady=state!=null
                    }
                    return true
                }
            }
            callback=Proxy.newProxyInstance(loader,arrayOf(callbackType)) {p,m,a->when(m.name) {
                "asBinder"->receiver;"hashCode"->System.identityHashCode(p);"equals"->p===a?.get(0);"toString"->"VoyahClusterMap";else->null
            }}
            type.getMethod("registerCallback",callbackType).invoke(remote,callback)
        }
        fun call(name:String,category:String,value:Int) {type.getMethod(name,String::class.java,Int::class.javaPrimitiveType).invoke(remote,category,value)}
        fun theme(): Int {
            val state=type.classLoader!!.loadClass("com.qinggan.cluster.ClusterState")
            return type.getMethod("getClusterState",state).invoke(remote,state.getField("THEM_MODE").get(null)) as Int
        }
    }
}
