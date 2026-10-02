package com.abrar.voyahiran.feature.voice

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import com.abrar.raha.feedback.RahaFeedback
import com.abrar.raha.RahaCommandRecognizer
import com.abrar.voicecommands.Recognition
import com.abrar.voyahiran.R
import com.abrar.voyahiran.VoyahIranApplication
import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import com.abrar.voyahiran.ui.MainActivity
import com.abrar.raha.speech.EngineChoice
import com.abrar.raha.speech.SpeechLab
import com.abrar.raha.speech.SpeechResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject

/** Owns capture independently of the screen. Never opens a second recorder during handover. */
class RahaService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val main = Handler(Looper.getMainLooper())
    private val prefs by lazy { VoicePreferences(this) }
    private val keys get() = (application as VoyahIranApplication).steeringKeys
    private val recognizer = RahaCommandRecognizer()
    private lateinit var speech: SpeechLab
    private lateinit var feedback: RahaFeedback
    private var captureEpoch = 0L
    private var cancelledEpoch = 0L
    private var overlayRetryAt = 0L
    private var ready = false
    private var preparing = false
    private var recording = false
    private var manualPending = false
    private var executing = false
    private var closed = false
    private var watchPhrases = emptySet<String>()
    private var manual = false
    private var navigationRevision = -1L

    override fun onCreate() {
        super.onCreate()
        try { com.abrar.voyahiran.feature.navigation.SavedPlacesStore(this).sync() }
        catch(e: Exception) { AppLog.error("Raha", "saved destinations unavailable", e) }
        navigationRevision=com.abrar.raha.RahaNavigationRecognizer.revision
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.raha_tab), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 43, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        startForeground(43, Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_app)
            .setContentTitle(getString(R.string.raha_notification)).setContentIntent(open).setOngoing(true).build())
        speech = SpeechLab(this)
        feedback = RahaFeedback(this, ::cancelInteraction, showTranscript = { prefs.showTranscript })
        scope.launch {
            while (isActive) {
                if (!prefs.enabled || !(application as VoyahIranApplication).subscriptions.allowed) { stopSelf(); break }
                try {
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                        error("Microphone permission unavailable")
                    if ((!Settings.canDrawOverlays(this@RahaService) || !highOverlayConfigured()) && SystemClock.elapsedRealtime() >= overlayRetryAt) {
                        overlayRetryAt = SystemClock.elapsedRealtime() + 10000
                        val grant = withContext(Dispatchers.IO) {
                            ProcessCommandRunner().run(listOf("su", "0", "raha-overlay", "enable"), 8)
                        }
                        AppLog.info("RahaFeedback", "overlay permission exit=${grant.exitCode} allowed=${Settings.canDrawOverlays(this@RahaService)} highConfigured=${highOverlayConfigured()} highConnected=${com.abrar.raha.feedback.RahaOverlayService.instance != null} detail=${grant.output}")
                    }
                    if (!ready && !preparing) prepare()
                    if (ready) {
                        val revision=com.abrar.raha.RahaNavigationRecognizer.revision
                        if(revision!=navigationRevision && !manual && !manualPending && !executing && !feedback.receiving) {
                            navigationRevision=revision
                            if(recording) cancelInteraction()
                        }
                        keys.setVoiceListener { main.post { button() } }
                        reconcile()
                    }
                } catch (e: Exception) { AppLog.error("Raha", "readiness failed; retry", e); error.value = true }
                delay(1000)
            }
        }
    }

    private fun highOverlayConfigured(): Boolean {
        val services = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val component = packageName + "/com.abrar.raha.feedback.RahaOverlayService"
        return component in services.split(':') &&
            Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
    }

    private fun prepare() {
        preparing = true
        speech.prepare(EngineChoice.CAR_LIVE) { failure ->
            preparing = false
            ready = failure == null
            error.value = failure != null
            if (failure != null) AppLog.error("Raha", "model initialization failed", failure)
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!prefs.enabled) {
            manualPending = false
            cancelInteraction()
            scope.launch { runCatching { keys.setVoiceListener(null) }; stopSelf() }
            return START_NOT_STICKY
        }
        if (recording && !manual && watchPhrases != prefs.phrases) cancelInteraction()
        return START_STICKY
    }
    private fun button() {
        if (closed || !prefs.enabled || !(application as VoyahIranApplication).subscriptions.allowed) return
        if (manual || manualPending || feedback.receiving || com.abrar.voyahiran.feature.navigation.NeshanVoiceNavigation.pending != null) {
            AppLog.info("Raha", "steering voice button cancels active listening")
            cancelInteraction()
            return
        }
        if (!ready || executing) return
        AppLog.info("Raha", "steering voice trigger")
        manualPending = true
        captureEpoch++
        feedback.dismiss()
        if (recording) speech.cancel() else reconcile()
    }
    private fun cancelInteraction() {
        cancelledEpoch++
        captureEpoch++ // Invalidate queued transcripts from this recording before cancelling it.
        manualPending = false
        feedback.dismiss()
        if (recording) speech.cancel()
        AppLog.info("Raha", "interaction cancelled; dispatched vehicle requests are not reversed")
    }
    private fun reconcile() {
        if (closed || !prefs.enabled || !(application as VoyahIranApplication).subscriptions.allowed || !ready || recording || executing) return
        if (manualPending) {
            manualPending = false; recording = true; manual = true
            val epoch = ++captureEpoch
            feedback.begin(manual = true)
            speech.start(false, object : SpeechLab.Listener {
                override fun onLevel(percent: Int) { if(epoch == captureEpoch) feedback.level(percent) }
                override fun onPartial(text: String) { if(epoch == captureEpoch) feedback.text(text) }
                override fun onDecoding() { if(epoch == captureEpoch) feedback.processing() }
                override fun onComplete(result: SpeechResult?) {
                    recording = false; manual = false
                    if (epoch == captureEpoch) {
                        if (result != null) command(result.text) else feedback.dismiss()
                    }
                    reconcile()
                }
                override fun onError(error: Throwable) {
                    recording = false; manual = false
                    AppLog.error("Raha", "manual capture failed", error)
                    RahaService.error.value = true
                    if(epoch == captureEpoch) feedback.result(false)
                }
            })
        } else if (prefs.phrases.isNotEmpty()) {
            watchPhrases = prefs.phrases
            recording = true; manual = false
            val epoch = ++captureEpoch
            speech.watch(watchPhrases, object : SpeechLab.WakeListener {
                override fun onSpeech(active: Boolean) { if(epoch == captureEpoch) feedback.speech(active) }
                override fun onLevel(percent: Int) { if(epoch == captureEpoch) feedback.level(percent) }
                override fun onState(active: Boolean) {
                    AppLog.info("Raha", "wake receiving=$active")
                    if(epoch != captureEpoch || executing) return
                    if(active) feedback.begin() else if(feedback.receiving) feedback.dismiss()
                }
                override fun onText(text: String) { if(epoch == captureEpoch && !executing) feedback.text(text) }
                override fun onCommand(text: String) { if(epoch == captureEpoch) command(text) }
                override fun onStopped(error: Throwable?) {
                    recording = false
                    if (error != null) {
                        AppLog.error("Raha", "wake capture failed", error)
                        RahaService.error.value = true
                        if(epoch == captureEpoch) feedback.result(false)
                    } else reconcile()
                }
            })
        }
    }
    private fun command(text: String) {
        if (closed || !prefs.enabled || executing) return
        AppLog.info("Raha", "final transcript=$text")
        feedback.text(text)
        when (val result = recognizer.recognize(text)) {
            is Recognition.Unknown -> { AppLog.info("Raha", "unrecognized reason=${result.reason}"); feedback.dismiss() }
            is Recognition.Matched -> {
                executing = true
                val command = result.command
                val resultEpoch = cancelledEpoch
                feedback.processing()
                scope.launch {
                    try {
                        check((application as VoyahIranApplication).subscriptions.allowed) { "Subscription inactive" }
                        AppLog.info("Raha", "execute action=${command.action} parameters=${command.parameters}")
                        if (command.action in setOf("PLAY_MEDIA", "PAUSE_MEDIA", "NEXT_MEDIA", "PREVIOUS_MEDIA")) {
                            feedback.yieldAudioForMedia()
                            delay(200)
                            check(resultEpoch == cancelledEpoch) { "Media command cancelled before dispatch" }
                        }
                        val outcome = if (command.action in com.abrar.voyahiran.feature.media.RahaMediaControl.actions) {
                            com.abrar.voyahiran.feature.media.RahaMediaControl.execute(this@RahaService, command.action)
                        } else if (command.action in setOf("OPEN_DEFAULT_MAP", "OPEN_DEFAULT_MUSIC", "OPEN_OEM_RADIO")) {
                            com.abrar.voyahiran.feature.navigation.DefaultAppLaunch.execute(
                                this@RahaService, command.action, command.parameters["requiredApp"] as? String)
                        } else if (command.action == "NAVIGATE_TO_SAVED_PLACE") {
                            com.abrar.voyahiran.feature.navigation.NeshanVoiceNavigation.navigate(
                                this@RahaService, command.parameters["destination"] as? String ?: error("Missing destination")) {
                                resultEpoch != cancelledEpoch || !(application as VoyahIranApplication).subscriptions.allowed || !prefs.enabled
                            }
                        } else if (command.action in setOf("SAVE_SEAT_POSITION", "RECALL_SEAT_POSITION")) {
                            OemSeatMemory.execute(this@RahaService, command)
                        } else withContext(Dispatchers.IO) {
                            ProcessCommandRunner().run(listOf("su", "0", "raha-command", command.action, JSONObject(command.parameters).toString()), 20)
                        }
                        error.value = outcome.exitCode != 0
                        if(resultEpoch == cancelledEpoch) feedback.result(outcome.exitCode == 0 && !outcome.timedOut)
                        AppLog.info("Raha", "result action=${command.action} exit=${outcome.exitCode} timeout=${outcome.timedOut} detail=${outcome.output}")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        error.value = true
                        if(resultEpoch == cancelledEpoch) feedback.result(false)
                        AppLog.error("Raha", "command execution failed: ${command.action}", e)
                    } finally { executing = false; reconcile() }
                }
            }
        }
    }
    override fun onDestroy() {
        closed = true
        manualPending = false
        feedback.close()
        speech.close()
        scope.cancel()
        // OEM restoration must complete even though the service's own jobs have been cancelled.
        CoroutineScope(Dispatchers.Main.immediate).launch {
            runCatching { keys.setVoiceListener(null) }.onFailure { AppLog.error("Raha", "voice hook release failed", it) }
        }
        AppLog.info("Raha", "stopped; microphone and voice interception released")
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object {
        private const val CHANNEL = "raha"
        val error = MutableStateFlow(false)
        fun resume(context: Context) {
            if (VoicePreferences(context).enabled) sync(context)
        }
        fun sync(context: Context) {
            if (VoicePreferences(context).enabled) context.startForegroundService(Intent(context, RahaService::class.java))
            else context.stopService(Intent(context, RahaService::class.java))
        }
    }
}
