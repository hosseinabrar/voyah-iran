package com.abrar.voyahiran.feature.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.core.shell.CommandResult
import kotlinx.coroutines.delay

/** All access is on the application's main thread (Raha + AccessibilityService). */
internal object NeshanVoiceNavigation {
    data class Destination(val name: String, val latitude: Double, val longitude: Double)
    // Coordinates from each dealer's map link in the official Soroush Motor dealer directory.
    val destinations = mapOf(
        "soroush_central" to Destination("نمایندگی مرکزی سروش موتور", 35.59486389160156, 51.29362487792969),
        "shahpary" to Destination("نمایندگی شاهپری", 35.7361246, 51.4933056)
    )
    class Request(val destination: Destination, val progress: NeshanRouteProgress) {
        var entered = false
        var completed = false
        var failure: String? = null
    }
    var pending: Request? = null; private set
    var serviceConnected = false

    suspend fun navigate(context: Context, id: String, cancelled: () -> Boolean): CommandResult {
        val prefs = NavigationPreferences(context)
        check(prefs.voiceNavigationEnabled) { "مسیریابی با رها در تنظیمات نشان فعال نیست" }
        val destination = destinations[id] ?: SavedPlacesStore(context).users().firstOrNull { it.id==id }
            ?.let { Destination(it.name,it.latitude,it.longitude) } ?: error("مکان انتخاب‌شده دیگر موجود نیست")
        check(pending == null) { "Navigation request already active" }
        NavigationPreferences.provisionAccessibility(context)
        repeat(25) { if (!serviceConnected) delay(200) }
        check(serviceConnected) { "Neshan accessibility service is not connected" }
        check(!cancelled()) { "Navigation cancelled before launch" }
        val request = Request(destination, NeshanRouteProgress(SystemClock.elapsedRealtime()))
        pending = request
        try {
            // Explicit package bypasses Android's app chooser, including when other maps are installed.
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:${destination.latitude},${destination.longitude}"))
                .setPackage(NavigationPreferences.NESHAN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            AppLog.info("NeshanRoute", "destination=$id lat=${destination.latitude} lon=${destination.longitude} explicit package launched")
            while (SystemClock.elapsedRealtime() - request.progress.startedAt < 45_000) {
                check(!cancelled() && NavigationPreferences(context).voiceNavigationEnabled) { "Navigation cancelled" }
                request.failure?.let { error(it) }
                if (request.completed) return CommandResult(0, "Neshan route guidance confirmed: $id")
                delay(200)
            }
            error("Neshan navigation timed out at ${request.progress.step}; check NeshanRoute/NeshanUi logs")
        } finally { if (pending === request) pending = null }
    }
}
