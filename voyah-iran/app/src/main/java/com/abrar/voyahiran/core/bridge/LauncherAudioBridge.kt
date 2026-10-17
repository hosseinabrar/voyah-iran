package com.abrar.voyahiran.core.bridge

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import com.abrar.voyahiran.core.logging.AppLog
import dalvik.system.PathClassLoader
import org.json.JSONObject
import java.lang.reflect.Proxy

/** OEM media volume; callback-invalidated cache, independent of Android STREAM_MUSIC. */
internal object LauncherAudioBridge {
    private var session: Session? = null
    @Synchronized fun command(value: String): JSONObject {
        check(BridgeSubscriptionSession.vipActive) { "VIP inactive" }
        val s = session?.takeIf { it.binder.isBinderAlive } ?: Session().also { session = it }
        return try { s.command(value) } catch(e: Exception) {
            s.close(); session = null
            throw e
        }
    }
    private class Session {
        val loader = PathClassLoader("/system/priv-app/Setting-release-signed/Setting-release-signed.apk", javaClass.classLoader)
        val type = loader.loadClass("com.qinggan.audiopolicy.IAudioPolicyService")
        val binder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
            .invoke(null, "qg.audio_policy") as IBinder
        val remote = loader.loadClass(type.name + "\$Stub").getMethod("asInterface", IBinder::class.java).invoke(null, binder)
        val callbackType = loader.loadClass("com.qinggan.audiopolicy.IAudioPolicyInfoCallback")
        val stub = loader.loadClass(callbackType.name + "\$Stub")
        val codes = listOf("onStreamVolumeChange", "onStreamMuteStateChange", "onMuteStateChange", "onAudioPolicyInfoChange")
            .associateWith { stub.getDeclaredField("TRANSACTION_$it").apply { isAccessible = true }.getInt(null) }
        val generation = java.util.concurrent.atomic.AtomicLong(0)
        var readGeneration = -1L
        var snapshot = JSONObject()
        val callbackBinder = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                if(code == INTERFACE_TRANSACTION) { reply?.writeString(callbackType.name); return true }
                if(code !in codes.values) return super.onTransact(code,data,reply,flags)
                data.enforceInterface(callbackType.name)
                generation.incrementAndGet()
                LauncherVehicleBridge.audioChanged()
                reply?.writeNoException()
                return true
            }
        }
        val callback = Proxy.newProxyInstance(loader, arrayOf(callbackType)) { proxy, method, args ->
            when(method.name) {
                "asBinder" -> callbackBinder
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                "toString" -> "LauncherAudioCallback"
                else -> null
            }
        }
        val intType = Int::class.javaPrimitiveType!!
        val getter = try { type.getMethod("getStreamVolume",intType,String::class.java) }
            catch(_: NoSuchMethodException) { type.getMethod("getStreamVolume",intType) }
        init {
            check(binder.interfaceDescriptor == type.name)
            check(type.getMethod("registerAudioPolicyInfoCallback",callbackType,String::class.java)
                .invoke(remote,callback,"com.abrar.voyahiran") != false) { "Audio callback registration rejected" }
        }
        fun close() { runCatching { type.getMethod("unregisterAudioPolicyInfoCallback",callbackType).invoke(remote,callback) } }
        fun command(value: String): JSONObject {
            if(value != "read") {
                if(value == "mute") {
                    val muted = type.getMethod("isStreamMute",intType).invoke(remote,3) as Boolean
                    type.getMethod("setStreamMute",intType,Boolean::class.javaPrimitiveType).invoke(remote,3,!muted)
                } else {
                    val level = value.toInt().also { require(it in 0..30) }
                    val max = type.getMethod("getStreamMaxVolume",intType).invoke(remote,3) as Int
                    require(level <= max)
                    type.getMethod("setStreamVolume",intType,intType,intType,String::class.java)
                        .invoke(remote,3,level,0,"com.abrar.voyahiran")
                }
                generation.incrementAndGet()
            }
            val version = generation.get()
            if(version != readGeneration) {
                val volume = if(getter.parameterCount == 2) getter.invoke(remote,3,"com.abrar.voyahiran") as Int else getter.invoke(remote,3) as Int
                val max = type.getMethod("getStreamMaxVolume",intType).invoke(remote,3) as Int
                val muted = type.getMethod("isStreamMute",intType).invoke(remote,3) as Boolean
                check(max > 0 && volume in 0..max)
                snapshot = JSONObject().put("volume",volume).put("max",max).put("muted",muted)
                readGeneration = version
                AppLog.changed("LauncherAudio", "OEM media volume=$volume max=$max muted=$muted")
            }
            return snapshot
        }
    }
}
