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
    private var timer=false
    @Synchronized fun command(action: String,id: String): CommandResult {
        if(action=="stop") { if(session==id)stop();return CommandResult(0,"Map released") }
        if(!BridgeSubscriptionSession.vipActive)return CommandResult(126,"VIP inactive")
        try {
            if(session!=id) {
                stop()
                val client=Api();api=client;session=id;hasFrame=false;presenting=true
                ClusterMusicBridge.start()
                client.call("syncReadyState",MEDIA,1)
                val intent=Intent().setClassName("com.abrar.voyahiran","com.abrar.voyahiran.feature.launcher.ClusterMapActivity")
                    .addCategory(MEDIA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                    .putExtra("session",id)
                client.type.getMethod("startClusterActivity",Intent::class.java).invoke(client.remote,intent)
                client.call("requestPresentation",SMALL,1)
                AppLog.info("ClusterMap","media presentation started session=$id")
            }
            renewed=SystemClock.elapsedRealtime()
            hasFrame=hasFrame || action=="ready"
            if(!timer) {
                timer=true
                Executors.newSingleThreadScheduledExecutor { r->Thread(r,"ClusterMapLease").apply {isDaemon=true} }
                    .scheduleWithFixedDelay(::tick,0,100,TimeUnit.MILLISECONDS)
            }
            return CommandResult(0,"Map lease renewed")
        } catch(e:Exception) {stop();return CommandResult(1,"Cluster map unavailable: ${e.cause ?: e}")}
    }
    @Synchronized private fun tick() {
        if(session==null)return
        if(!BridgeSubscriptionSession.vipActive || SystemClock.elapsedRealtime()-renewed>3500) {stop();return}
        try {
            val client=api ?: return
            if(hasFrame && client.requested) {
                client.requested=false
                client.type.getMethod("notifyReadyToShow",String::class.java).invoke(client.remote,SMALL)
            }
        } catch(e:Exception) {AppLog.error("ClusterMap","ready failed",e);stop()}
    }
    private fun stop() {
        val client=api;api=null;session=null;hasFrame=false;presenting=false
        if(client!=null) {
            runCatching {client.call("requestPresentation",SMALL,2)}
                .onFailure {AppLog.error("ClusterMap","release failed",it)}
            // MEDIA is shared, not a map-only presentation. Closing it does not restore
            // the OEM task displaced by startClusterActivity. Restore the stock owner;
            // its onResume/callback supplies syncReadyState and notifyReadyToShow.
            runCatching {
                val intent=Intent().setClassName("com.qinggan.instrumentcard","com.qinggan.instrumentcard.ScreenActivity")
                    .addCategory(MEDIA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                client.type.getMethod("startClusterActivity",Intent::class.java).invoke(client.remote,intent)
                client.call("requestPresentation",SMALL,1)
                AppLog.info("ClusterMap","map released; OEM media presentation restored")
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
            val receiver=object:Binder() {
                override fun onTransact(code:Int,data:Parcel,reply:Parcel?,flags:Int):Boolean {
                    if(code==INTERFACE_TRANSACTION){reply?.writeString(callbackType.name);return true}
                    data.enforceInterface(callbackType.name)
                    if(code==presentation) {data.readString();val category=data.readString();val state=data.readInt();if(category==SMALL)requested=state==1}
                    return true
                }
            }
            callback=Proxy.newProxyInstance(loader,arrayOf(callbackType)) {p,m,a->when(m.name) {
                "asBinder"->receiver;"hashCode"->System.identityHashCode(p);"equals"->p===a?.get(0);"toString"->"VoyahClusterMap";else->null
            }}
            type.getMethod("registerCallback",callbackType).invoke(remote,callback)
        }
        fun call(name:String,category:String,value:Int) {type.getMethod(name,String::class.java,Int::class.javaPrimitiveType).invoke(remote,category,value)}
    }
}
