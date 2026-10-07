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
    class Request(val destination: Destination, var progress: NeshanRouteProgress) {
        val startup = NeshanStartupGate()
        var destinationSent = false
        var entered = false
        var completed = false
        var failure: String? = null
    }
    var pending: Request? = null; private set
    var serviceConnected = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var routeJob: Job? = null

    suspend fun navigate(context: Context, id: String, cancelled: () -> Boolean): CommandResult {
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
                val options = android.app.ActivityOptions.makeBasic().apply { launchDisplayId = 0 }.toBundle()
                app.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options)
                val startupAt = SystemClock.elapsedRealtime()
                AppLog.info("NeshanRoute", "opening normal entry; waiting for map UI destination=$id")
                while (!active.startup.ready(SystemClock.elapsedRealtime())) {
                    check(serviceConnected && NavigationPreferences(app).voiceNavigationEnabled && app.subscriptions.vipAllowed) { "Navigation disabled during startup" }
                    active.failure?.let { error(it) }
                    check(SystemClock.elapsedRealtime() - startupAt < 15_000) { "Neshan map UI not ready after normal launch" }
                    delay(100)
                }
                active.failure?.let { error(it) }
                active.progress = NeshanRouteProgress(SystemClock.elapsedRealtime())
                app.startActivity(intent, options)
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
}