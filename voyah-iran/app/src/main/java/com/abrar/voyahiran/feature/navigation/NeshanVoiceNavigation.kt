package com.abrar.voyahiran.feature.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.core.shell.CommandResult
import kotlinx.coroutines.*

/** All access is on the application's main thread (Raha + AccessibilityService). */
internal object NeshanVoiceNavigation {
    data class Destination(val name: String, val latitude: Double, val longitude: Double)
    // Coordinates from each dealer's map link in the official Soroush Motor dealer directory.
    val destinations = mapOf(
        "soroush_central" to Destination("نمایندگی مرکزی سروش موتور", 35.59486389160156, 51.29362487792969),
        "shahpary" to Destination("نمایندگی شاهپری", 35.7361246, 51.4933056)
    )
    class Request(val destination: Destination, var progress: NeshanRouteProgress, val cardRepair:Boolean=false) {
        val startup = NeshanStartupGate()
        var displayId: Int? = null
        var destinationSent = false
        var entered = false
        var completed = false
        var failure: String? = null
    }
    var pending: Request? = null; private set
    internal fun reserveCardRepair(request:Request):Boolean {
        if(pending!=null)return false
        pending=request;return true
    }
    internal fun releaseCardRepair(request:Request) {if(pending===request)pending=null}
    var serviceConnected = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var routeJob: Job? = null

    suspend fun navigate(context: Context, id: String, cancelled: () -> Boolean): CommandResult {
        while (com.abrar.voyahiran.feature.launcher.LauncherMapWakeSession.restarting) {
            check(!cancelled()) { "Navigation cancelled during map restart" }
            delay(100)
        }
        val prefs = NavigationPreferences(context)
        check(prefs.voiceNavigationEnabled) { "مسیریابی با رها در تنظیمات نشان فعال نیست" }
        val destination = destinations[id] ?: SavedPlacesStore(context).users().firstOrNull { it.id==id }
            ?.let { Destination(it.name,it.latitude,it.longitude) } ?: error("مکان انتخاب‌شده دیگر موجود نیست")

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:${destination.latitude},${destination.longitude}"))
            .setPackage(NavigationPreferences.NESHAN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        check(context.packageManager.resolveActivity(intent, 0) != null) { "نشان نصب نیست یا امکان اجرای مسیریابی ندارد" }
        val launchIntent = context.packageManager.getLaunchIntentForPackage(NavigationPreferences.NESHAN)
            ?: error("نشان قابل اجرا نیست")
        if (!serviceConnected) NavigationPreferences.provisionAccessibility(context)
        repeat(25) { if (!serviceConnected) delay(200) }
        check(serviceConnected) { "Neshan accessibility service is not connected" }
        check(!cancelled()) { "Navigation cancelled before launch" }
        routeJob?.cancelAndJoin() // A new spoken request replaces a stalled previous attempt.
        val app = context.applicationContext
        val active = Request(destination, NeshanRouteProgress(SystemClock.elapsedRealtime()))
        // A new explicit route supersedes any old-session cleanup still waiting for HOME.
        com.abrar.voyahiran.feature.launcher.LauncherMapWakeSession.handled =
            com.abrar.voyahiran.feature.launcher.LauncherMapWakeSession.generation
        pending = active // Reserve automation before any launch or success-animation delay.
        // Acceptance is the end of Raha's command. Route automation has its own lifetime
        // and must never signal feedback or observe cancellation of a subsequent utterance.
        routeJob = scope.launch {
            val request = active
            try {
                // Let Raha's 1300ms success indication finish before opening the map.
                delay(1450)
                check(NavigationPreferences(app).voiceNavigationEnabled &&
                    (app as com.abrar.voyahiran.VoyahIranApplication).subscriptions.vipAllowed) { "Navigation disabled" }
                check(serviceConnected) { "Neshan accessibility disconnected" }
                val preferCard = com.abrar.voyahiran.feature.launcher.LauncherPreferences.enabled(app) &&
                    NavigationPreferences(app).navigator == NavigationPreferences.NESHAN
                val preparedCard = if (preferCard) prepareLauncherCard(app) else null
                val status = bridgeRetry(listOf("launcher-map-status", NavigationPreferences.NESHAN, "0"), 4)
                check(status.exitCode == 0 && !status.timedOut) { "Cannot verify map display: ${status.output}" }
                AppLog.info("NeshanRoute", "map status ${status.output.trim()}")
                val visibleDisplay = if (status.exitCode == 0 && !status.timedOut)
                    Regex("MAP_DISPLAY=(\\d+) MAP_VISIBLE=1").find(status.output)?.groupValues?.get(1)?.toIntOrNull()
                    else null
                val card = com.abrar.voyahiran.feature.launcher.LauncherEmbeddedMapSurface.automationDisplayId
                val reuse = preparedCard ?: visibleDisplay?.takeIf { it == 0 || it == 1 ||
                    (it == card && com.abrar.voyahiran.feature.launcher.VoyahLauncherActivity.mapPageVisible) }
                active.displayId = reuse ?: 0
                val options = android.app.ActivityOptions.makeBasic().apply { launchDisplayId = active.displayId!! }.toBundle()
                if (reuse == null) app.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options)
                NeshanDrivingService.inspectForRoute()
                AppLog.info("NeshanRoute", "target display=${active.displayId} reuseVisible=${reuse != null}")
                val startupAt = SystemClock.elapsedRealtime()
                AppLog.info("NeshanRoute", "waiting for map UI destination=$id display=${active.displayId}")
                while (!active.startup.ready(SystemClock.elapsedRealtime())) {
                    check(serviceConnected && NavigationPreferences(app).voiceNavigationEnabled && app.subscriptions.vipAllowed) { "Navigation disabled during startup" }
                    active.failure?.let { error(it) }
                    check(SystemClock.elapsedRealtime() - startupAt < 15_000) { "Neshan map UI not ready after normal launch" }
                    delay(100)
                }
                active.failure?.let { error(it) }
                active.progress = NeshanRouteProgress(SystemClock.elapsedRealtime())
                if (active.displayId!! > 1) {
                    check(com.abrar.voyahiran.feature.launcher.VoyahLauncherActivity.mapPageVisible) { "Map card is no longer visible" }
                    val dispatched = bridgeRetry(listOf("launcher-map-route", NavigationPreferences.NESHAN,
                        active.displayId.toString(), destination.latitude.toString(), destination.longitude.toString()), 8)
                    check(dispatched.exitCode == 0 && !dispatched.timedOut &&
                        !dispatched.output.contains("Error:") && !dispatched.output.contains("Exception")) {
                        "Map destination dispatch failed: ${dispatched.output}"
                    }
                } else app.startActivity(intent, options)
                active.destinationSent = true
                AppLog.info("NeshanRoute", "map ready after ${SystemClock.elapsedRealtime()-startupAt}ms; destination dispatched=$id")
                while (SystemClock.elapsedRealtime() - active.progress.startedAt < 45_000) {
                    check(NavigationPreferences(app).voiceNavigationEnabled &&
                        app.subscriptions.vipAllowed) { "Navigation cancelled" }
                    active.failure?.let { error(it) }
                    if (active.completed) {
                        AppLog.info("NeshanRoute", "route guidance confirmed destination=$id")
                        return@launch
                    }
                    delay(200)
                }
                error("Neshan navigation timed out at ${active.progress.step}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.error("NeshanRoute", "accepted route failed destination=$id", e)
            } finally {
                if (pending === request) pending = null
            }
        }
        return CommandResult(0, "Neshan navigation accepted: $id")
    }
    private suspend fun prepareLauncherCard(app: Context): Int {
        app.startActivity(Intent(app, com.abrar.voyahiran.feature.launcher.VoyahLauncherActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            android.app.ActivityOptions.makeBasic().apply { launchDisplayId=0 }.toBundle())
        val deadline=SystemClock.elapsedRealtime()+15_000
        var claimedDisplay: Int?=null
        var stableDisplay: Int?=null
        var stable=0
        while(SystemClock.elapsedRealtime()<deadline) {
            check(serviceConnected && NavigationPreferences(app).voiceNavigationEnabled &&
                (app as com.abrar.voyahiran.VoyahIranApplication).subscriptions.vipAllowed &&
                com.abrar.voyahiran.feature.launcher.LauncherPreferences.enabled(app) &&
                NavigationPreferences(app).navigator==NavigationPreferences.NESHAN) { "Map card navigation disabled" }
            val card=com.abrar.voyahiran.feature.launcher.LauncherEmbeddedMapSurface.automationDisplayId
            if(card!=null && card>1 && com.abrar.voyahiran.feature.launcher.VoyahLauncherActivity.mapPageVisible) {
                val status=bridgeRetry(listOf("launcher-map-status",NavigationPreferences.NESHAN,card.toString()),4)
                val visible=status.exitCode==0 && !status.timedOut &&
                    status.output.contains("MAP_DISPLAY=$card MAP_VISIBLE=1")
                if(visible) {
                    // Let the card-sized layout settle before injecting the destination.
                    if(stableDisplay!=card) {stableDisplay=card;stable=0}
                    stable++
                    if(stable>=3) {
                        AppLog.info("NeshanRoute","launcher card ready display=$card")
                        return card
                    }
                } else {
                    stable=0
                    if(status.exitCode==0 && !status.timedOut && claimedDisplay!=card) {
                        val claim=bridgeRetry(listOf("launcher-map-claim",NavigationPreferences.NESHAN,card.toString()),8)
                        check(claim.exitCode==0 && !claim.timedOut && !claim.output.contains("Error:")) {
                            "Cannot prepare map card: ${claim.output}"
                        }
                        claimedDisplay=card
                    }
                }
            } else stable=0
            delay(200)
        }
        // Do not silently navigate fullscreen: that recreates the stale wide-layout offset.
        error("Launcher map card did not become ready; destination was not sent")
    }
    private suspend fun bridgeRetry(args: List<String>, timeout: Long): CommandResult {
        val until = SystemClock.elapsedRealtime() + 4000
        while (true) {
            val result = withContext(Dispatchers.IO) {
                com.abrar.voyahiran.core.shell.ProcessCommandRunner().run(listOf("su", "0") + args, timeout)
            }
            if (result.exitCode != 75 || SystemClock.elapsedRealtime() >= until) return result
            delay(150)
        }
    }

}
