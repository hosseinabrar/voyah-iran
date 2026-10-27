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
import com.abrar.raha.intent.RahaIntentModel
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
    private val recognizer by lazy { RahaIntentModel(this) { AppLog.info("Raha",it) } }
    private lateinit var speech: SpeechLab
    private lateinit var feedback: RahaFeedback
    private var captureEpoch = 0L
    @Volatile private var cancelledEpoch = 0L
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
    private var contactsRevision = -1L
    private val calls = com.abrar.raha.RahaCallDialog(SystemClock::elapsedRealtime)
    private var callSnapshot = emptyList<RahaContact>()
    private var callTimeout: Job? = null
    private var callListenPending = false

    override fun onCreate() {
        super.onCreate()
        RahaPhonebook.sync(this)
        try { com.abrar.voyahiran.feature.navigation.SavedPlacesStore(this).sync() }
        catch(e: Exception) { AppLog.error("Raha", "saved destinations unavailable", e) }
        navigationRevision=com.abrar.raha.RahaPlaces.revision
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.raha_tab), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 43, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        startForeground(43, Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_app)
            .setContentTitle(getString(R.string.raha_notification)).setContentIntent(open).setOngoing(true).build())
        speech = SpeechLab(this)
        feedback = RahaFeedback(this, ::cancelInteraction, showTranscript = { prefs.showTranscript }, lightTheme = { com.abrar.voyahiran.feature.launcher.LauncherTheme.isLight() })
        scope.launch {
            while (isActive) {
                if (!prefs.enabled || !(application as VoyahIranApplication).subscriptions.vipAllowed) { stopSelf(); break }
                try {
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                        error("Microphone permission unavailable")
                    // Model loading is asynchronous; do not serialize it behind bridge setup.
                    if (!ready && !preparing) prepare()
                    if ((!Settings.canDrawOverlays(this@RahaService) || !highOverlayConfigured()) && SystemClock.elapsedRealtime() >= overlayRetryAt) {
                        overlayRetryAt = SystemClock.elapsedRealtime() + 1000
                        val grant = withContext(Dispatchers.IO) {
                            ProcessCommandRunner().run(listOf("su", "0", "raha-overlay", "enable"), 8)
                        }
                        AppLog.info("RahaFeedback", "overlay permission exit=${grant.exitCode} allowed=${Settings.canDrawOverlays(this@RahaService)} highConfigured=${highOverlayConfigured()} highConnected=${com.abrar.raha.feedback.RahaOverlayService.instance != null} detail=${grant.output}")
                    }
                    if (calls.waiting && !prefs.callsEnabled) cancelInteraction()
                    if (ready) {
                        val contactsNow=com.abrar.raha.RahaContacts.revision
                        if(contactsNow!=contactsRevision && !manual && !manualPending && !executing && !feedback.receiving && !calls.waiting) {
                            contactsRevision=contactsNow
                            if(recording) cancelInteraction()
                        }
                        val revision=com.abrar.raha.RahaPlaces.revision
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
        val startedAt = SystemClock.elapsedRealtime()
        AppLog.info("Raha", "model initialization started")
        scope.launch {
            try {
                withContext(Dispatchers.IO) { recognizer.prepare() }
                if(closed)return@launch
                speech.prepare(EngineChoice.CAR_LIVE) { failure ->
                    if(!closed) {
                        preparing = false
                        ready = failure == null
                        error.value = failure != null
                        AppLog.info("Raha", "model initialization finished ready=$ready elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
                        if (failure != null) AppLog.error("Raha", "model initialization failed", failure)
                    }
                }
            } catch(e:CancellationException) { throw e }
            catch(e:Exception) { preparing=false;error.value=true;AppLog.error("Raha","intent model initialization failed",e) }
            catch(e:LinkageError) { preparing=false;error.value=true;AppLog.error("Raha","intent runtime unavailable",e) }
            catch(e:OutOfMemoryError) { preparing=false;error.value=true;AppLog.error("Raha","intent model memory unavailable",e) }
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!prefs.enabled) {
            manualPending = false
            cancelInteraction()
            scope.launch { runCatching { keys.setVoiceListener(null) }; stopSelf() }
            return START_NOT_STICKY
        }
        if (!prefs.callsEnabled && calls.waiting) cancelInteraction()
        if (recording && !manual && watchPhrases != prefs.phrases) cancelInteraction()
        return START_STICKY
    }
    private fun button() {
        if (closed || !prefs.enabled || !(application as VoyahIranApplication).subscriptions.vipAllowed) return
        if (calls.waiting || manual || manualPending || feedback.receiving || com.abrar.voyahiran.feature.navigation.NeshanVoiceNavigation.pending != null) {
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
        calls.cancel(); callSnapshot=emptyList(); callListenPending=false
        callTimeout?.cancel(); callTimeout=null
        cancelledEpoch++
        captureEpoch++ // Invalidate queued transcripts from this recording before cancelling it.
        manualPending = false
        feedback.dismiss()
        if (recording) speech.cancel()
        AppLog.info("Raha", "interaction cancelled; dispatched vehicle requests are not reversed")
    }
    private fun reconcile() {
        if (closed || !prefs.enabled || !(application as VoyahIranApplication).subscriptions.vipAllowed || !ready || recording || executing) return
        if (callListenPending && calls.waiting) {
            callListenPending=false; recording=true; manual=true
            val epoch=++captureEpoch
            val confirming=calls.step is com.abrar.raha.RahaCallDialog.Step.Confirm
            speech.start(false, object:SpeechLab.Listener {
                override fun onSpeech(active:Boolean) { if(epoch==captureEpoch) feedback.speech(active) }
                override fun onLevel(percent:Int) { if(epoch==captureEpoch) feedback.level(percent) }
                override fun onPartial(text:String) {} // Keep the question visible until a final answer.
                override fun onDecoding() { if(epoch==captureEpoch) feedback.processing() }
                override fun onComplete(result:SpeechResult?) {
                    recording=false; manual=false
                    if(epoch==captureEpoch) advanceCall(if(result==null) calls.cancel() else calls.speak(result.text))
                    reconcile()
                }
                override fun onError(error:Throwable) { recording=false; manual=false; if(epoch==captureEpoch) cancelInteraction(); reconcile() }
            }, carModeOverride=if(confirming) false else true, initialSilenceMs=10_000)
        } else if (manualPending) {
            manualPending = false; recording = true; manual = true
            val epoch = ++captureEpoch
            feedback.begin(manual = true)
            speech.start(false, object : SpeechLab.Listener {
                override fun onSpeech(active:Boolean) { if(epoch==captureEpoch) feedback.speech(active) }
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
            }, minimumWakeLevel = { prefs.minimumWakeLevel })
        }
    }
    private fun command(text: String) {
        if (closed || !prefs.enabled || executing) return
        executing=true
        val epoch=cancelledEpoch
        feedback.text(text)
        feedback.processing()
        scope.launch {
            try {
                val result=withContext(Dispatchers.Default) { recognizer.recognize(text) }
                executing=false
                if(closed || epoch!=cancelledEpoch || !prefs.enabled || !(application as VoyahIranApplication).subscriptions.vipAllowed)return@launch
                dispatchRecognition(text,result)
            } catch(e:CancellationException) { throw e }
            catch(e:Exception) { executing=false;error.value=true;if(epoch==cancelledEpoch)feedback.result(false);AppLog.error("Raha","intent inference failed",e) }
            catch(e:LinkageError) { executing=false;error.value=true;if(epoch==cancelledEpoch)feedback.result(false);AppLog.error("Raha","intent runtime failed",e) }
            catch(e:OutOfMemoryError) { executing=false;error.value=true;if(epoch==cancelledEpoch)feedback.result(false);AppLog.error("Raha","intent inference memory unavailable",e) }
            finally { reconcile() }
        }
    }
    private fun dispatchRecognition(text:String,result:Recognition) {
        if(result is Recognition.Matched && result.command.action=="CALL_CONTACT") {
            val target=result.command.parameters["contact_query"] as? String
            if(prefs.callsEnabled && !target.isNullOrBlank()) {
                val lookupStarted=android.os.SystemClock.elapsedRealtime()
                val snapshot=RahaPhonebook.contacts()
                val step=calls.begin(target,snapshot.map { com.abrar.raha.RahaCallDialog.Candidate(it.id,it.spoken) })
                AppLog.info("RahaContacts", "lookup count=${snapshot.size} elapsedMs=${android.os.SystemClock.elapsedRealtime()-lookupStarted}")
                if(step !is com.abrar.raha.RahaCallDialog.Step.Cancelled) {
                    callSnapshot=snapshot
                    advanceCall(step)
                    return
                }
            }
            feedback.result(false)
            return
        }
        AppLog.info("Raha", "final transcript=$text")
        feedback.text(text)
        when (result) {
            is Recognition.Unknown -> {
                AppLog.info("Raha", "unrecognized reason=${result.reason}")
                if(com.abrar.raha.RahaIgnoredSpeech.matches(text))feedback.dismiss()
                else feedback.unrecognized()
            }
            is Recognition.Matched -> {
                executing = true
                val command = result.command
                val resultEpoch = cancelledEpoch
                feedback.processing()
                scope.launch {
                    try {
                        check((application as VoyahIranApplication).subscriptions.vipAllowed) { "Subscription inactive" }
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
                                resultEpoch != cancelledEpoch || !(application as VoyahIranApplication).subscriptions.vipAllowed || !prefs.enabled
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
    private fun advanceCall(step:com.abrar.raha.RahaCallDialog.Step) {
        callTimeout?.cancel(); callTimeout=null
        captureEpoch++
        if(recording) speech.cancel()
        callListenPending=false
        when(step) {
            is com.abrar.raha.RahaCallDialog.Step.Cancelled -> cancelInteraction()
            is com.abrar.raha.RahaCallDialog.Step.Dial -> {
                val selected=callSnapshot.singleOrNull { it.id==step.contact.id }
                calls.cancel(); callSnapshot=emptyList()
                if(selected==null || !prefs.callsEnabled || !feedback.showing) { cancelInteraction(); return }
                executing=true
                val epoch=cancelledEpoch
                feedback.processing()
                scope.launch {
                    try {
                        val ok=withContext(Dispatchers.IO) {
                            if(epoch!=cancelledEpoch) false else RahaPhonebook.dial(this@RahaService,selected) { epoch!=cancelledEpoch }
                        }
                        if(epoch==cancelledEpoch) feedback.result(ok)
                    } catch(e:CancellationException) { throw e }
                    catch(_:Exception) { if(epoch==cancelledEpoch) feedback.result(false) }
                    finally { executing=false; reconcile() }
                }
            }
            else -> {
                val choices=when(step) {
                    is com.abrar.raha.RahaCallDialog.Step.Choose -> step.candidates
                    is com.abrar.raha.RahaCallDialog.Step.Confirm -> listOf(step.contact)
                    else -> emptyList()
                }
                val question=when(step) {
                    is com.abrar.raha.RahaCallDialog.Step.Choose -> "کدوم ${step.query}؟"
                    is com.abrar.raha.RahaCallDialog.Step.Confirm -> "با ${step.contact.name} تماس بگیرم؟"
                    else -> ""
                }
                val labels=choices.map { candidate ->
                    val suffix=if(choices.count { it.name==candidate.name }>1) callSnapshot.firstOrNull { it.id==candidate.id }?.number?.takeLast(4)?.let { " ($it)" }.orEmpty() else ""
                    "تماس با ${candidate.name}$suffix"
                }
                feedback.prompt(question,labels, confirmation = step is com.abrar.raha.RahaCallDialog.Step.Confirm) { index ->
                    choices.getOrNull(index)?.let { advanceCall(calls.select(it.id)) }
                }
                if(!feedback.showing) { cancelInteraction(); return }
                callListenPending=true
                callTimeout=scope.launch { delay(10_000); cancelInteraction(); reconcile() }
                reconcile()
            }
        }
    }
    override fun onDestroy() {
        closed = true
        manualPending = false
        feedback.close()
        speech.close()
        scope.cancel()
        CoroutineScope(Dispatchers.IO).launch { recognizer.close() }
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
            if ((context.applicationContext as VoyahIranApplication).subscriptions.vipAllowed && VoicePreferences(context).enabled) context.startForegroundService(Intent(context, RahaService::class.java))
            else context.stopService(Intent(context, RahaService::class.java))
        }
    }
}
