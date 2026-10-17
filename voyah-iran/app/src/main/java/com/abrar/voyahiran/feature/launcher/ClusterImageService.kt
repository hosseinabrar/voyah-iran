package com.abrar.voyahiran.feature.launcher

import android.app.Service
import android.content.Intent
import android.os.*
import android.view.Surface
import com.abrar.voyahiran.core.logging.AppLog

/** Transfers a GPU producer Surface, never pixels over Binder. Only the OEM renderer may bind. */
class ClusterImageService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var surface: Surface? = null
    private var session: String? = null
    private val endpoint = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) { reply?.writeString(DESCRIPTOR); return true }
            if (code != FIRST_CALL_TRANSACTION) return super.onTransact(code, data, reply, flags)
            data.enforceInterface(DESCRIPTOR)
            val uid = getCallingUid()
            val oemUid = packageManager.getApplicationInfo("com.qinggan.instrumentcard", 0).uid
            check(uid == oemUid) { "Cluster Surface caller rejected uid=$uid" }
            val id = data.readString()
            val next = Surface.CREATOR.createFromParcel(data)
            main.post {
                if (!LauncherClusterMap.valid(this@ClusterImageService, id) || !next.isValid) {
                    next.release()
                    AppLog.info("ClusterImage", "Surface rejected: stale session=$id")
                } else {
                    val old = surface
                    surface = next; session = id
                    LauncherClusterMap.surface(id!!, next, 1920, 720)
                    old?.release()
                    AppLog.info("ClusterImage", "OEM ImageReader connected session=$id 1920x720")
                }
            }
            reply?.writeNoException()
            return true
        }
    }
    override fun onBind(intent: Intent): IBinder = endpoint
    override fun onUnbind(intent: Intent): Boolean { release(); return false }
    override fun onDestroy() { release(); super.onDestroy() }
    private fun release() {
        session?.let { LauncherClusterMap.surface(it, null, 0, 0) }
        surface?.release(); surface = null; session = null
    }
    companion object { private const val DESCRIPTOR = "com.abrar.voyahiran.ClusterImageSurface" }
}
