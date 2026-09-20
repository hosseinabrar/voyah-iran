package com.abrar.voyahiran.feature.subscription

import android.content.Context
import android.content.Intent
import com.abrar.voyahiran.VoyahIranApplication
import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import com.abrar.voyahiran.feature.steering.service.StarBackService
import com.abrar.voyahiran.feature.drivememory.service.DriveMemoryService
import com.abrar.voyahiran.feature.voice.RahaService
import com.abrar.voyahiran.feature.voice.VoicePreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SubscriptionStatus { CHECKING, ACTIVE, OFFLINE, BLOCKED, VEHICLE_UNAVAILABLE }
class SubscriptionManager(private val app: VoyahIranApplication) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository = SubscriptionRepository(app)
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var started = false
    @Volatile private var grant: SubscriptionGrant? = null
    @Volatile private var denied = false
    private var announced = false
    private val mutable = MutableStateFlow(SubscriptionStatus.CHECKING)
    val state = mutable.asStateFlow()
    val allowed: Boolean get() = SubscriptionAccessPolicy.allowed(grant,repository.now(),denied)
    fun refresh() { start(); wake.trySend(Unit) }
    @Synchronized fun start() {
        if(started) return
        started = true
        scope.launch {
            var vin: String? = null
            var nextRefresh = 0L
            var retries = 0
            var wasAllowed = false
            while(isActive) {
                val nowElapsed = android.os.SystemClock.elapsedRealtime()
                try {
                    if(vin == null) {
                        vin = withContext(Dispatchers.IO) { repository.readVin() }
                        grant = withContext(Dispatchers.IO) { repository.cached(vin) }
                        denied = repository.blocked()
                    }
                    // A valid local grant unlocks features before waiting for the network.
                    if(allowed && !wasAllowed) {
                        updateRuntimeSession()
                        wasAllowed=startFeatures()
                        if(wasAllowed) { mutable.value=SubscriptionStatus.ACTIVE; announced=false }
                    }
                    if(!allowed && wasAllowed) nextRefresh=0
                    if(nowElapsed >= nextRefresh) {
                        if(!allowed) mutable.value = if(denied) SubscriptionStatus.BLOCKED else SubscriptionStatus.CHECKING
                        if(!allowed) announce()
                        try {
                            grant = withContext(Dispatchers.IO) { repository.renew(vin) }
                            denied = false; retries=0; nextRefresh=nowElapsed+6*60*60*1000L
                            AppLog.info("Subscription", "signed grant accepted expiresAt=${grant!!.expiresAt}")
                        } catch(e: Exception) {
                            if(e is CancellationException) throw e
                            denied = repository.blocked()
                            retries=(retries+1).coerceAtMost(5)
                            nextRefresh=nowElapsed+(if(allowed) 300000L else 30000L)*retries
                            mutable.value=if(denied) SubscriptionStatus.BLOCKED else if(allowed && wasAllowed) SubscriptionStatus.ACTIVE else SubscriptionStatus.OFFLINE
                            AppLog.info("Subscription", "refresh failed type=${e.javaClass.simpleName} active=$allowed blocked=$denied retry=$retries")
                        }
                    }
                    updateRuntimeSession()
                    if(allowed) {
                        if(!wasAllowed) wasAllowed=startFeatures()
                        if(wasAllowed) { mutable.value=SubscriptionStatus.ACTIVE; announced=false }
                        else mutable.value=SubscriptionStatus.VEHICLE_UNAVAILABLE
                        withContext(Dispatchers.IO) { repository.checkpoint() }
                    } else if(mutable.value==SubscriptionStatus.ACTIVE) mutable.value=SubscriptionStatus.OFFLINE
                } catch(e: Exception) {
                    if(e is CancellationException) throw e
                    mutable.value=SubscriptionStatus.VEHICLE_UNAVAILABLE
                    AppLog.error("Subscription", "VIN or runtime initialization failed", e)
                }
                if(!allowed) {
                    if(wasAllowed) { app.stopService(Intent(app,RahaService::class.java)); app.stopService(Intent(app,DriveMemoryService::class.java)); wasAllowed=false }
                    // Restore the star button even if the previous app process died with a recovery journal.
                    if(app.starBackup.read()!=null) StarBackService.request(app,StarBackService.RESUME)
                    announce()
                }
                val forced=withTimeoutOrNull(if(vin==null) 10000L else 30000L) { wake.receive(); true } == true
                if(forced)nextRefresh=0
            }
        }
    }
    private suspend fun announce() {
        if(announced) return
        val result=withContext(Dispatchers.IO) { ProcessCommandRunner().run(listOf("su","0","subscription-ui","open"),5) }
        announced=result.exitCode==0
    }
    private suspend fun updateRuntimeSession() {
        val seconds=if(allowed) (grant!!.expiresAt-repository.now()).coerceIn(0,90).toInt() else 0
        val result=withContext(Dispatchers.IO) { ProcessCommandRunner().run(listOf("su","0","subscription-session",seconds.toString()),3) }
        check(!result.timedOut && result.exitCode == 0) {
            "Runtime session was not accepted: ${result.output}"
        }
    }
    private suspend fun startFeatures(): Boolean = try {
        if(app.starBackup.desired || app.starBackup.read()!=null) StarBackService.request(app,StarBackService.RESUME)
        // A failed read before registration may have left this shared controller running.
        // Reset its connection so start() re-arms both saved restore targets.
        app.driveMemoryController.detach()
        DriveMemoryService.request(app,DriveMemoryService.RESUME)
        if(VoicePreferences(app).enabled) withContext(Dispatchers.IO) {
            ProcessCommandRunner().run(listOf("su","0","raha-service","start"),5)
        }
        try { com.abrar.voyahiran.feature.media.MediaControl.provision(app) }
        catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLog.error("SteeringMedia", "startup access failed", e)
        }
        AppLog.info("Subscription", "startup features dispatched after runtime session accepted")
        true
    } catch(e: Exception) {
        if(e is CancellationException) throw e
        AppLog.info("Subscription","Feature startup deferred: ${e.javaClass.simpleName}")
        false
    }
}
