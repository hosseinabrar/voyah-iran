package com.abrar.voyahiran.feature.media

import android.content.Context
import android.provider.Settings
import com.abrar.voyahiran.VoyahIranApplication
import com.abrar.voyahiran.core.bridge.BridgeClient
import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.feature.drivememory.data.CanDriveGateway
import com.abrar.voyahiran.feature.drivememory.data.CanField
import com.abrar.voyahiran.feature.navigation.NavigationPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.json.JSONObject

internal class StartupVolumeAutomation(private val context:Context,private val scope:CoroutineScope) {
    private val prefs=NavigationPreferences(context)
    private val history=context.getSharedPreferences("startup_volume_history",Context.MODE_PRIVATE)
    private val events=Channel<Int>(Channel.UNLIMITED)
    private val gateway=CanDriveGateway(context) { if(it.field==CanField.ACC) events.trySend(it.value) }
    private var job:Job?=null
    private fun allowed()=(context.applicationContext as VoyahIranApplication).subscriptions.allowed && prefs.startupVolumeEnabled
    fun tick() {
        if(!allowed()) {job?.cancel();return}
        if(job?.isCompleted==false)return
        job=scope.launch {
            try {
                var acc:Int?=null
                while(isActive && allowed()) {
                    try {
                        if(acc==null)acc=withContext(Dispatchers.IO){gateway.readPowerState()}
                        val boot=Settings.Global.getInt(context.contentResolver,Settings.Global.BOOT_COUNT,-1)
                        if(acc==0) history.edit().putBoolean("armed",true).commit()
                        if(shouldApply(acc,boot,history.getInt("boot",-2),history.getBoolean("armed",false))) {
                            // Claim once per power cycle; listener reconnects must not reset the user's volume.
                            check(history.edit().putInt("boot",boot).putBoolean("armed",false).commit())
                            applyVolume()
                        }
                        // CAN callbacks are primary; slow read recovers missed events/reconnections.
                        acc=withTimeoutOrNull(30_000){events.receive()}
                    } catch(e:CancellationException){throw e}
                    catch(e:Exception){AppLog.changed("StartupVolume","waiting for vehicle: ${e.message}");acc=null;delay(2000)}
                }
            } finally {withContext(NonCancellable+Dispatchers.IO){gateway.close()}}
        }
    }
    private suspend fun applyVolume() {
        val level=prefs.startupVolume
        repeat(6) { attempt ->
            if(!allowed() || prefs.startupVolume!=level)return
            try {
                val acc=withContext(Dispatchers.IO){gateway.readPowerState()}
                if(acc!=2)return
                val result=withContext(Dispatchers.IO){BridgeClient.run(listOf("music-startup-volume",level.toString()),4)}
                val actual=runCatching {JSONObject(result.output).getInt("volume")}.getOrNull()
                val confirmed=result.exitCode==0 && !result.timedOut && actual==level
                AppLog.info("StartupVolume","acc=2 target=$level attempt=${attempt+1} confirmed=$confirmed exit=${result.exitCode} actual=$actual detail=${result.output}")
                if(confirmed)return
            } catch(e:CancellationException){throw e}
            catch(e:Exception){AppLog.info("StartupVolume","attempt=${attempt+1} unavailable: ${e.message}")}
            delay(1500)
        }
        AppLog.info("StartupVolume","retry limit reached; leave user volume alone until next startup")
    }
    fun close(){job?.cancel()}
    companion object {
        internal fun shouldApply(acc:Int?,boot:Int,handledBoot:Int,armed:Boolean)=acc==2 && boot>=0 && (boot!=handledBoot || armed)
        fun deferUntilNextStartup(context:Context) {
            val boot=Settings.Global.getInt(context.contentResolver,Settings.Global.BOOT_COUNT,-1)
            check(context.getSharedPreferences("startup_volume_history",Context.MODE_PRIVATE).edit().putInt("boot",boot).putBoolean("armed",false).commit())
        }
    }
}
