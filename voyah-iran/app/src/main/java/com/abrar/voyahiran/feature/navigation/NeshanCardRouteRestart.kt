package com.abrar.voyahiran.feature.navigation

import android.content.Context
import android.os.SystemClock
import com.abrar.voyahiran.core.bridge.BridgeClient
import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.feature.launcher.LauncherEmbeddedMapSurface
import com.abrar.voyahiran.feature.launcher.LauncherPreferences
import com.abrar.voyahiran.feature.launcher.VoyahLauncherActivity
import kotlinx.coroutines.*
import org.json.JSONObject

/** Fail closed on unknown trip history. Moving a card-origin trip never starts a new trip. */
internal object NeshanCardRouteRestart {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var job:Job?=null
    private val attempted=linkedSetOf<String>()
    private var nextCheck=0L
    private fun card(c:Context,id:Int)=VoyahLauncherActivity.mapPageVisible &&
        LauncherEmbeddedMapSurface.automationDisplayId==id && LauncherPreferences.enabled(c) &&
        NavigationPreferences(c).navigator==NavigationPreferences.NESHAN &&
        (c.applicationContext as com.abrar.voyahiran.VoyahIranApplication).subscriptions.vipAllowed
    private suspend fun command(args:List<String>)=withContext(Dispatchers.IO) {BridgeClient.run(args,4)}
    private suspend fun snapshot():JSONObject? {
        val result=command(listOf("neshan-direct","trip-state"))
        if(result.exitCode!=0 || result.timedOut || !result.output.startsWith("NESHAN_TRIP="))return null
        return runCatching {JSONObject(result.output.removePrefix("NESHAN_TRIP="))}.getOrNull()
    }
    internal suspend fun confirmedIdle():Boolean = snapshot()?.let {it.has("active") && it.opt("active")==false}==true
    internal fun eligible(data:JSONObject):Boolean = data.optBoolean("valid") &&
        data.optString("origin")=="external" && data.optString("stage")=="active" &&
        !data.optBoolean("attempted") && data.optInt("stops",-1)==0 && data.optString("mode")=="CAR" &&
        data.optString("id").matches(Regex("[0-9]{1,20}-[0-9]{1,10}")) &&
        data.optDouble("lat",Double.NaN).let {it.isFinite() && it in -90.0..90.0} &&
        data.optDouble("lon",Double.NaN).let {it.isFinite() && it in -180.0..180.0}
    fun consider(context:Context,display:Int) {
        val app=context.applicationContext
        if(job?.isActive==true || NeshanVoiceNavigation.pending!=null || !card(app,display) || SystemClock.elapsedRealtime()<nextCheck)return
        nextCheck=SystemClock.elapsedRealtime()+1500
        job=scope.launch {
            var request:NeshanVoiceNavigation.Request?=null
            try {
                val state=snapshot() ?: return@launch
                if(!eligible(state) || state.getString("id") in attempted)return@launch
                val id=state.getString("id")
                if(!NeshanVoiceNavigation.serviceConnected) NavigationPreferences.provisionAccessibility(app)
                repeat(25) {if(!NeshanVoiceNavigation.serviceConnected)delay(200)}
                check(NeshanVoiceNavigation.serviceConnected && card(app,display)) {"Card automation unavailable"}
                val current=snapshot() ?: return@launch
                if(!eligible(current) || current.optString("id")!=id)return@launch
                val owned=NeshanVoiceNavigation.Request(NeshanVoiceNavigation.Destination("مسیر قبلی",current.getDouble("lat"),current.getDouble("lon")),
                    NeshanRouteProgress(SystemClock.elapsedRealtime()),cardRepair=true)
                val destinationIntent=android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("geo:${owned.destination.latitude},${owned.destination.longitude}"))
                    .setPackage(NavigationPreferences.NESHAN)
                check(app.packageManager.resolveActivity(destinationIntent,0)!=null) {"No destination handler; keep current trip"}
                owned.displayId=display
                if(!NeshanVoiceNavigation.reserveCardRepair(owned))return@launch
                request=owned
                attempted.add(id);while(attempted.size>64)attempted.remove(attempted.first())
                val stop=command(listOf("neshan-direct","trip-cancel",id,display.toString()))
                check(stop.exitCode==0 && !stop.timedOut) {"Trip cancel not accepted"}
                val stopped=withTimeoutOrNull(6000) {
                    while(true) {
                        check(card(app,display) && NeshanVoiceNavigation.serviceConnected && NeshanVoiceNavigation.pending===owned) {"Card left during trip restart"}
                        val stateNow=snapshot()
                        if(stateNow!=null) {
                            check(stateNow.optString("id")==id) {"Trip changed; do not overwrite it"}
                            check(stateNow.optString("stage")!="failed") {"Stock stop action failed"}
                            if(!stateNow.optBoolean("valid") && stateNow.optString("stage")=="stopped")return@withTimeoutOrNull true
                        }
                        delay(150)
                    }
                    @Suppress("UNREACHABLE_CODE") false
                }==true
                check(stopped && card(app,display)) {"Trip stop was not confirmed"}
                val beforeDispatch=checkNotNull(snapshot())
                check(NeshanVoiceNavigation.pending===owned && beforeDispatch.optString("id")==id &&
                    !beforeDispatch.optBoolean("valid") && beforeDispatch.optString("stage")=="stopped") {"Trip changed before destination dispatch"}
                owned.progress=NeshanRouteProgress(SystemClock.elapsedRealtime())
                val sent=command(listOf("launcher-map-route",NavigationPreferences.NESHAN,display.toString(),
                    owned.destination.latitude.toString(),owned.destination.longitude.toString()))
                check(sent.exitCode==0 && !sent.timedOut && !sent.output.contains("Error:") && !sent.output.contains("Exception")) {"Destination dispatch failed"}
                owned.destinationSent=true
                NeshanDrivingService.inspectForRoute()
                withTimeout(45000) {
                    while(!owned.completed) {
                        check(card(app,display) && NeshanVoiceNavigation.serviceConnected && NeshanVoiceNavigation.pending===owned) {"Card route interrupted"}
                        owned.failure?.let {error(it)}
                        delay(200)
                    }
                }
                AppLog.info("NeshanCardRoute","external-origin trip restarted in card")
            } catch(e:CancellationException) {throw e}
            catch(e:Exception) {AppLog.error("NeshanCardRoute","restart not completed; no automatic retry",e)}
            finally {request?.let {NeshanVoiceNavigation.releaseCardRepair(it)}}
        }
    }
}
