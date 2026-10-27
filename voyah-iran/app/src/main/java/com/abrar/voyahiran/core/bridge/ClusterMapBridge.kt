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

/** Leases the map input of the OEM image compositor; never replaces the OEM window. */
internal object ClusterMapBridge {
    @Volatile var presenting=false
        private set
    @Volatile var layersActive=false
        private set
    @Volatile var blendFrom=0f
        private set
    @Volatile var blendStart=0L
        private set
    @Synchronized fun layerPacket()=org.json.JSONObject()
        .put("layers",layersActive).put("map",presenting).put("from",blendFrom).put("start",blendStart)
        .put("session",session ?: org.json.JSONObject.NULL)
    private fun target(map: Boolean) {
        if(presenting==map)return
        val t=((SystemClock.elapsedRealtime()-blendStart)/360f).coerceIn(0f,1f)
        val ease=t*t*(3-2*t)
        blendFrom += ((if(presenting)1f else 0f)-blendFrom)*ease
        presenting=map
        blendStart=SystemClock.elapsedRealtime()+300
        AppLog.info("ClusterMap","blend target=$map from=$blendFrom start=$blendStart")
    }
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
                layersActive=true;blendFrom=0f;blendStart=0L
                bootstrapped=false;launchAt=0L;launchRequested=false;startedAt=SystemClock.elapsedRealtime()
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
            if(action=="covered" && hasFrame && ClusterMusicBridge.musicReady)target(true)
            if(action=="music")target(false)
            if(!timer) {
                timer=true
                Executors.newSingleThreadScheduledExecutor { r->Thread(r,"ClusterMapLease").apply {isDaemon=true} }
                    .scheduleWithFixedDelay(::tick,0,100,TimeUnit.MILLISECONDS)
            }
            val theme=runCatching {api?.theme()}.getOrNull()
            return CommandResult(0,"Map lease renewed theme=${theme ?: -1} reveal=${if(revealed) 1 else 0} musicReady=${if(ClusterMusicBridge.musicReady) 1 else 0} frameReady=${if(hasFrame)1 else 0} target=${if(presenting)1 else 0} from=$blendFrom start=$blendStart")
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
                // The OEM window remains the sole display owner. Map frames arrive
                // in its ImageReader; no second activity/window is launched.
                val intent=Intent().setClassName("com.qinggan.instrumentcard","com.qinggan.instrumentcard.ScreenActivity")
                    .addCategory(MEDIA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    .putExtra("session",session)
                client.type.getMethod("startClusterActivity",Intent::class.java).invoke(client.remote,intent)
                launchRequested=true;launchAt=now
                // The vendor API returns void even on rejection. Only ready from
                // the actual rendered surface is confirmation, never this call.
                AppLog.info("ClusterMap","OEM image compositor requested session=$session")
            }
            // Register the persistent transparent output. Layer selection is a separate blend.
            if(hasFrame && client.requested) {
                client.requested=false
                client.type.getMethod("notifyReadyToShow",String::class.java).invoke(client.remote,SMALL)
                revealed=true
            }
        } catch(e:Exception) {AppLog.error("ClusterMap","ready failed",e);stop("ready_error error=${e.cause ?: e}")}
    }
    private fun stop(reason: String) {
        if(session != null) AppLog.info("ClusterMap", "stop reason=$reason session=$session leaseAgeMs=${SystemClock.elapsedRealtime()-renewed} hasFrame=$hasFrame revealed=$revealed launchRequested=$launchRequested")
        val client=api
        api=null;session=null;hasFrame=false;revealed=false;presenting=false;layersActive=false;blendFrom=0f;blendStart=0L
        if(client!=null) {
            // Nothing displaced the OEM task: releasing the input must NOT relaunch
            // it, hide its display or expose factory contents under our music image.
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
