package com.abrar.raha.feedback

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.*
import android.widget.TextView
import com.abrar.raha.feedback.R
import android.util.Log

/** Full-display transparent touch surface, with the orb centered at 25% screen height. */
class RahaFeedback(private val context: Context, private val cancel: () -> Unit,
    private val showTranscript: () -> Boolean = { false }, private val host: ViewGroup? = null) : AutoCloseable {
    private var attachedWindows: WindowManager? = null
    private var attachedType = 0
    private val windows = context.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val policy = FeedbackStateMachine()
    private val sounds = RahaSounds(context)
    private val audioFocus = RahaAudioFocus(context) { dismiss(); cancel() }
    private var root: View? = null
    private var orb: RahaOrbView? = null
    private var transcriptView: TextView? = null
    private var transcript = ""
    private var closed = false
    private var focusAttempted = false
    private var voiceActive = false
    val receiving get() = policy.state in setOf(FeedbackState.WAITING, FeedbackState.LISTENING)
    private val tick = object : Runnable {
        override fun run() {
            if (closed) return
            if (policy.tick(SystemClock.elapsedRealtime())) sounds.play(RahaSounds.Cue.READY)
            render()
            if (policy.state != FeedbackState.HIDDEN) main.postDelayed(this,60)
        }
    }
    fun begin(manual: Boolean = false) {
        if(closed || !acquireAudio()) return
        if (!receiving) { transcript=""; sounds.stop(); policy.begin(SystemClock.elapsedRealtime(), !manual) }
        if(manual) sounds.play(RahaSounds.Cue.READY)
        refresh()
    }
    fun text(value: String) {
        if(closed || value.isBlank() || !acquireAudio()) return
        transcript=value.take(2000)
        policy.text(SystemClock.elapsedRealtime(),value); refresh()
    }
    fun level(percent: Int) {
        orb?.level = percent / 100f
    }
    fun speech(active: Boolean) { voiceActive=active; policy.speech(SystemClock.elapsedRealtime(),active) }
    fun processing() { if(closed || !acquireAudio()) return; policy.processing(); refresh() }
    fun result(success: Boolean) {
        if(closed || !acquireAudio()) return
        policy.result(SystemClock.elapsedRealtime(),success)
        sounds.play(if(success) RahaSounds.Cue.SUCCESS else RahaSounds.Cue.FAILURE)
        refresh()
    }
    fun dismiss() { policy.cancel(); transcript=""; voiceActive=false; sounds.stop(); main.removeCallbacks(tick); render() }
    private fun acquireAudio(): Boolean {
        if (!focusAttempted) {
            focusAttempted = true
            if (!audioFocus.acquire()) {
                Log.w("RahaFeedback", "audio focus denied; media attenuation unavailable for this interaction")
            }
        }
        return true
    }
    private fun refresh() { main.removeCallbacks(tick); tick.run() }
    private fun render() {
        if (policy.state == FeedbackState.HIDDEN) {
            audioFocus.release()
            focusAttempted = false
            root?.let { view -> runCatching { if(host != null) host.removeView(view) else attachedWindows?.removeViewImmediate(view) } }
            root = null; orb = null; transcriptView=null; attachedWindows=null; return
        }
        // A late service connection must promote the currently visible overlay as well.
        if(host == null && root != null && attachedType == WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY && RahaOverlayService.instance != null) {
            root?.let { runCatching { attachedWindows?.removeViewImmediate(it) } }
            root=null; orb=null; transcriptView=null; attachedWindows=null
            Log.i("RahaFeedback", "promoting overlay to accessibility host")
        }
        if(root == null && (host != null || RahaOverlayService.instance != null || Settings.canDrawOverlays(context))) {
            val view = LayoutInflater.from(context).inflate(R.layout.overlay_raha,null)
            view.setOnClickListener { Log.i("RahaFeedback","touch cancel"); cancel() }
            view.setOnTouchListener { touched, event ->
                if(event.actionMasked == MotionEvent.ACTION_DOWN) touched.performClick()
                true
            }
            val overlayHost = if(host == null) RahaOverlayService.instance else null
            if(host == null && overlayHost == null) {
                val enabled=Settings.Secure.getString(context.contentResolver,Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
                Log.w("RahaFeedback", "HIGH_OVERLAY_UNAVAILABLE configured=${enabled.contains(context.packageName+"/com.abrar.raha.feedback.RahaOverlayService")} fallback=2038")
            }
            val windowType = if(overlayHost != null) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            val manager = overlayHost?.getSystemService(WindowManager::class.java) ?: windows
            val params = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT,
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; setTitle("Raha") }
            try {
                if(host != null) host.addView(view,ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT))
                else { manager.addView(view,params); attachedWindows=manager; attachedType=windowType; Log.i("RahaFeedback", "overlay attached type=$windowType") }
                root=view; orb=view.findViewById(R.id.orb); transcriptView=view.findViewById(R.id.rahaTranscript)
            } catch(e:Exception) { Log.e("RahaFeedback","overlay failed",e) }
        }
        orb?.phase = if(policy.state == FeedbackState.WAITING && voiceActive) FeedbackState.LISTENING else policy.state
        transcriptView?.apply {
            val visible=showTranscript() && transcript.isNotBlank()
            visibility=if(visible) View.VISIBLE else View.GONE
            text=if(visible) transcript else ""
        }
    }
    override fun close() { dismiss(); closed=true; sounds.close() }
}
