package com.abrar.voyahiran.core.bridge

import android.os.IBinder
import android.os.SystemClock
import com.abrar.raha.RahaAction
import com.abrar.voyahiran.core.logging.AppLog
import dalvik.system.PathClassLoader
import java.io.File

/** Uses the same qg.audio_policy service as SoundPresenter, without starting Settings. */
internal object OemAudioBinder {
    fun execute(action: RahaAction, p: Map<String, Any>) {
        try { executeInstalled(action, p) }
        catch (e: LinkageError) { throw IllegalStateException("Installed audio SDK could not be loaded", e) }
    }
    private fun executeInstalled(action: RahaAction, p: Map<String, Any>) {
        val apk = File("/system/priv-app/Setting-release-signed/Setting-release-signed.apk")
        check(apk.isFile) { "Installed audio SDK unavailable" }
        val loader = object : PathClassLoader(apk.path, javaClass.classLoader) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                if (!name.startsWith("com.qinggan.audiopolicy.")) return super.loadClass(name, resolve)
                synchronized(this) {
                    val type = findLoadedClass(name) ?: findClass(name)
                    if (resolve) resolveClass(type)
                    return type
                }
            }
        }
        val type = loader.loadClass("com.qinggan.audiopolicy.IAudioPolicyService")
        val binder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
            .invoke(null, "qg.audio_policy") as? IBinder ?: error("OEM audio policy unavailable")
        check(binder.isBinderAlive && binder.interfaceDescriptor == type.name)
        val remote = loader.loadClass(type.name + "\$Stub").getMethod("asInterface", IBinder::class.java).invoke(null, binder)
        val intType = Int::class.javaPrimitiveType!!
        val channel = mapOf("media" to 3, "call" to 6, "navigation" to 1, "voice" to 22, "system" to 5).getValue(p["channel"] as String)
        val caller = "com.abrar.voyahiran"
        fun volume() = type.getMethod("getStreamVolume", intType, String::class.java).invoke(remote, channel, caller) as Int
        fun mute() = type.getMethod("isStreamMute", intType).invoke(remote, channel) as Boolean
        val expected: Any
        val read: () -> Any
        if (action == RahaAction.SET_AUDIO_MUTE) {
            expected = p.getValue("enabled") as Boolean
            read = ::mute
            type.getMethod("setStreamMute", intType, Boolean::class.javaPrimitiveType).invoke(remote, channel, expected)
        } else {
            val max = type.getMethod("getStreamMaxVolume", intType).invoke(remote, channel) as Int
            val current = volume()
            check(max > 0 && current in 0..max) { "OEM audio range unavailable" }
            expected = if (action == RahaAction.ADJUST_AUDIO_VOLUME) (current + (p.getValue("delta") as Number).toInt()).coerceIn(0, max)
                else when (p["level"]) { "max" -> max; "min" -> 0; else -> (p.getValue("level") as Number).toInt() }
            require(expected in 0..max) { "Volume outside OEM range" }
            read = ::volume
            // 0: no FLAG_SHOW_UI and no extra system tone.
            type.getMethod("setStreamVolume", intType, intType, intType, String::class.java).invoke(remote, channel, expected, 0, caller)
        }
        val deadline = SystemClock.elapsedRealtime() + 2000
        var actual = read()
        while (actual != expected && SystemClock.elapsedRealtime() < deadline) { Thread.sleep(50); actual = read() }
        AppLog.info("RahaAudio", "route=binder action=$action channel=$channel expected=$expected actual=$actual")
        check(actual == expected) { "Audio feedback not confirmed: expected=$expected actual=$actual" }
    }
}
