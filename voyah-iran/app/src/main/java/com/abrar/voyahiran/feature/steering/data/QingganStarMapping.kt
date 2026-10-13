package com.abrar.voyahiran.feature.steering.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import com.abrar.voyahiran.feature.steering.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.Executors

/** Wire protocol recovered from the supplied Qinggan 1.0.0 firmware. */
class QingganStarMapping(context: Context) : StarMapping {
    private val context = context.applicationContext
    // H97A Android 9 QGKeyEvent parcels contain key, action, Bundle only.
    private val keyCodec = QingganKeyCodec(android.os.Build.VERSION.SDK_INT >= 30)
    private val operations = Mutex()
    @Volatile private var voice: (() -> Unit)? = null
    @Volatile private var media: ((Int, Int, Int) -> Boolean)? = null
    @Volatile private var screenSwap: ((Int, Int) -> Boolean)? = null
    private val voicePress = com.abrar.voyahiran.feature.voice.VoiceButtonPolicy()
    val voiceListening: Boolean get() = voice != null && registered && remote?.isBinderAlive == true
    private val events = Executors.newSingleThreadExecutor()
    @Volatile private var connection: ServiceConnection? = null
    @Volatile private var remote: IBinder? = null
    @Volatile private var policy: StarEventPolicy? = null
    @Volatile private var registered = false
    override val listening: Boolean get() = remote?.isBinderAlive == true && registered && policy != null
    private var token = Binder()
    private val generation = java.util.concurrent.atomic.AtomicLong()
    private val callback = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) { reply?.writeString(CALLBACK); return true }
            if (code != 1 && code != 2) return super.onTransact(code, data, reply, flags)
            data.enforceInterface(CALLBACK)
            var consumed = false
            if (data.readInt() != 0) {
                val event = keyCodec.read({ data.readInt() }, { data.readBundle(javaClass.classLoader) })
                val key = event.code
                val action = event.action
                val keyFlags = event.flags
                val extras = event.payload
                val display = event.display
                if (code == 2) { data.readString(); data.readInt() }
                val epoch = generation.get()
                if (voice != null && key == 130) {
                    consumed = true
                    if (voicePress.trigger(action)) voice?.invoke()
                }
                try { if (screenSwap?.invoke(key, action) == true) consumed = true }
                catch (e: Exception) { com.abrar.voyahiran.core.logging.AppLog.error("ScreenSwap", "key handler failed", e) }
                try { if (media?.invoke(key, action, display) == true) consumed = true }
                catch (e: Exception) { com.abrar.voyahiran.core.logging.AppLog.error("SteeringMedia", "key handler failed", e) }
                val routed = policy?.route(key, action, keyFlags, display, OemPayload(extras))
                com.abrar.voyahiran.core.logging.AppLog.info("StarEvent", "key=$key action=$action flags=$keyFlags display=$display consumed=${consumed || routed?.consume == true} starConsumed=${routed?.consume} emitted=${routed?.events?.size}")
                if (routed?.consume == true) {
                    // Reply promptly: synchronous injection here could deadlock the OEM engine.
                    events.execute {
                        runCatching { routed.events.forEach { if (generation.get() == epoch) inject(it) } }.onFailure {
                            com.abrar.voyahiran.core.logging.AppLog.error("StarEvent", "event-injection-failed", it)
                        }
                            .onFailure { if (generation.get() == epoch) policy = null }
                    }
                    consumed = true
                }
            }
            reply?.writeNoException()
            reply?.writeInt(if (consumed) 1 else 0)
            return true
        }
    }

    override suspend fun read(): Int = offMain {
        connect()
        return@offMain call(9, { writeInt(SteeringKeys.STAR); writeString(context.packageName) }) { readInt() }
    }

    override suspend fun write(target: Int) = offMain {
        connect()
        call(8, { writeInt(SteeringKeys.STAR); writeInt(target); writeString(context.packageName) }) { Unit }
    }

    override suspend fun intercept(previous: Int) = offMain {
        connect()
        // Replaying a target that itself maps elsewhere could recurse or change the OEM action.
        for (target in StarBackTransport.replayTargets(android.os.Build.VERSION.SDK_INT, previous)) {
            val mapped = call(9, { writeInt(target); writeString(context.packageName) }) { readInt() }
            if (mapped != target) throw SteeringException(SteeringFailure.UNAVAILABLE)
        }
        generation.incrementAndGet()
        policy = StarEventPolicy(previous)
        registerKeys()
    }

    override suspend fun release() = offMain {
        generation.incrementAndGet()
        policy = null
        if (voice != null || media != null || screenSwap != null) { connect(); registerKeys() }
        else { unregisterKeys(); disconnect() }
    }

    /** OEM indexes interceptors by package, so star and voice MUST share this registration. */
    suspend fun setVoiceListener(listener: (() -> Unit)?) = offMain {
        if (listener != null && voiceListening) { voice = listener; return@offMain }
        voice = listener
        voicePress.reset()
        if (listener != null || policy != null || media != null || screenSwap != null) {
            connect()
            registerKeys()
        } else { unregisterKeys(); disconnect() }
    }

    suspend fun setMediaListener(listener: ((Int, Int, Int) -> Boolean)?) = offMain {
        if (listener != null && media != null && registered && remote?.isBinderAlive == true) {
            media = listener; return@offMain
        }
        if (listener == null && media == null) return@offMain
        media = listener
        if (listener != null || voice != null || policy != null || screenSwap != null) { connect(); registerKeys() }
        else { unregisterKeys(); disconnect() }
    }

    suspend fun setScreenSwapListener(listener: ((Int, Int) -> Boolean)?) = offMain {
        if (listener != null && screenSwap != null && registered && remote?.isBinderAlive == true) {
            screenSwap = listener; return@offMain
        }
        if (listener == null && screenSwap == null) return@offMain
        screenSwap = listener
        if (listener != null || voice != null || media != null || policy != null) { connect(); registerKeys() }
        else { unregisterKeys(); disconnect() }
    }

    // Binder registration may wait for OEM startup. Never block activity creation/drawing.
    // Keep the mutex held across suspension so listeners still share one registration.
    private suspend fun <T> offMain(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        operations.withLock { block() }
    }

    private fun unregisterKeys() {
        if (registered && remote?.isBinderAlive == true)
            call(4, { writeStrongBinder(callback); writeString(context.packageName) }) { Unit }
        registered = false
    }

    private fun registerKeys() {
        unregisterKeys()
        token = Binder()
        registered = true // Release even when acknowledgment is ambiguous.
        val keys = buildList {
            if (policy != null) add(SteeringKeys.STAR)
            if (media != null) addAll(com.abrar.voyahiran.feature.media.MediaRoutingPolicy.keys)
            if (screenSwap != null) add(3115)
            if (voice != null) add(130) // Qinglink Constant.Button.KeyCode.KEYCODE_IVOKA
        }.toIntArray()
        val result = call(3, {
            writeStrongBinder(token); writeStrongBinder(callback); writeIntArray(keys)
            writeString(context.packageName); writeInt(0); writeInt(100)
        }) { readInt() }
        if (result != 0) {
            registered = false
            throw SteeringException(SteeringFailure.CONNECTION)
        }
        com.abrar.voyahiran.core.logging.AppLog.info("Steering", "shared interception keys=${keys.joinToString()}")
    }

    private data class OemPayload(val extras: Bundle?) : KeyPayload

    private fun inject(event: RoutedKey) {
        com.abrar.voyahiran.core.logging.AppLog.info("StarEvent", "inject code=${event.code} action=${event.action} display=${event.display}")
        if (StarBackTransport.directCenter(android.os.Build.VERSION.SDK_INT, event)) {
            val result = com.abrar.voyahiran.core.bridge.BridgeClient.run(listOf("steering-back", "center"), 3)
            if (result.exitCode != 0 || result.timedOut) throw SteeringException(SteeringFailure.CONNECTION)
            return
        }
        call(7, {
            writeInt(1)
            keyCodec.write(QingganKeyCodec.Event(event.code, event.action, event.flags,
                (event.payload as? OemPayload)?.extras, event.display), { writeInt(it) }, { writeBundle(it) })
            writeString(context.packageName)
        }) { Unit }
    }

    private suspend fun connect() {
        if (remote?.isBinderAlive == true) return
        disconnect()
        val ready = CompletableDeferred<IBinder>()
        val pending = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) { if (connection === this) ready.complete(binder) }
            override fun onServiceDisconnected(name: ComponentName) {
                if (connection !== this) return
                remote = null; policy = null; registered = false
                ready.completeExceptionally(SteeringException(SteeringFailure.CONNECTION))
            }
            override fun onNullBinding(name: ComponentName) { ready.completeExceptionally(SteeringException(SteeringFailure.UNAVAILABLE)) }
            override fun onBindingDied(name: ComponentName) {
                if (connection !== this) return
                remote = null; policy = null; registered = false
                ready.completeExceptionally(SteeringException(SteeringFailure.CONNECTION))
            }
        }
        try {
            val info = context.packageManager.getPackageInfo(PACKAGE, 0)
            if (info.versionName != "1.0.0") throw SteeringException(SteeringFailure.UNAVAILABLE)
            connection = pending
            if (!context.bindService(Intent().setComponent(ComponentName(PACKAGE, "$PACKAGE.KeyManagerService")), pending, Context.BIND_AUTO_CREATE)) {
                throw SteeringException(SteeringFailure.UNAVAILABLE)
            }
            val binder = withTimeout(3000) { ready.await() }
            if (binder.interfaceDescriptor != DESCRIPTOR) throw SteeringException(SteeringFailure.UNAVAILABLE)
            remote = binder
        } catch (error: Exception) {
            disconnect()
            if (error is CancellationException && error !is TimeoutCancellationException) throw error
            if (error is SteeringException) throw error
            if (error is android.content.pm.PackageManager.NameNotFoundException) throw SteeringException(SteeringFailure.UNAVAILABLE, error)
            throw SteeringException(SteeringFailure.CONNECTION, error)
        }
    }

    private fun <T> call(code: Int, write: Parcel.() -> Unit, read: Parcel.() -> T): T {
        val request = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            request.writeInterfaceToken(DESCRIPTOR)
            request.write()
            if (remote?.transact(code, request, reply, 0) != true) throw SteeringException(SteeringFailure.CONNECTION)
            reply.readException()
            return reply.read()
        } finally { reply.recycle(); request.recycle() }
    }

    private fun disconnect() {
        connection?.let { runCatching { context.unbindService(it) } }
        registered = false
        policy = null
        connection = null
        remote = null
    }

    private companion object {
        const val PACKAGE = "com.qinggan.keymanager.service"
        const val DESCRIPTOR = "com.qinggan.system.IKeyManagerService"
        const val CALLBACK = "com.qinggan.system.IKeyManagerCallback"
    }
}



