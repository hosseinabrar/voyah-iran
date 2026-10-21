package com.abrar.voyahiran.feature.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import com.abrar.voyahiran.core.logging.AppLog

/** Read-only OEM ReverseManager contract (C/X); no gear inference or camera commands. */
internal class LauncherAvmVisibility(private val context: Context) {
    private var remote: IBinder? = null
    private var bound = false
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) { remote = service }
        override fun onServiceDisconnected(name: ComponentName) { remote = null }
        override fun onBindingDied(name: ComponentName) { remote = null }
    }
    fun start() {
        bound = runCatching {
            context.bindService(Intent("com.qinggan.systempolicy.SystemPolicyService")
                .setPackage("com.qinggan.systempolicy"), connection, Context.BIND_AUTO_CREATE)
        }.getOrDefault(false)
    }
    fun visible(): Boolean = runCatching {
        val binder = remote?.takeIf { it.isBinderAlive } ?: return false
        check(binder.interfaceDescriptor == "com.qinggan.system.ISystemPolicy")
        val request = Parcel.obtain(); val reply = Parcel.obtain()
        try {
            request.writeInterfaceToken("com.qinggan.system.ISystemPolicy")
            // getReverseState() is transaction 13 in both supplied C and X SDKs.
            // OEM state 1 also covers manually opened AVM, not just reverse gear.
            check(binder.transact(13, request, reply, 0))
            reply.readException()
            reply.readInt() == 1
        } finally { request.recycle(); reply.recycle() }
    }.getOrElse { AppLog.changed("LauncherAVM", "visibility unavailable: ${it.message}"); false }
    fun stop() {
        if (bound) runCatching { context.unbindService(connection) }
        bound = false; remote = null
    }
}
