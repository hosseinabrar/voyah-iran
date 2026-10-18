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
    private val showTranscript: () -> Boolean = { false }, private val host: ViewGroup? = null,
    private val lightTheme: () -> Boolean = { false }) : AutoCloseable {
    private var attachedWindows: WindowManager? = null
    private var attachedType = 0
    private var interactionStarted = 0L
    private var heartbeatStartedAt: Long? = null
    private fun playReady() {
        sounds.play(RahaSounds.Cue.READY)
        heartbeatStartedAt = SystemClock.uptimeMillis()
        orb?.heartbeatStartedAt = heartbeatStartedAt
    }
    private inline fun onMain(crossinline action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else main.post { action() }
    }
    companion object {
        // Retain ownership until WindowManager confirms removal, even after a failure.
        private val visibleOwners = mutableSetOf<RahaFeedback>()
        fun hostDisconnected() {
            Handler(Looper.getMainLooper()).post {
                visibleOwners.toList().forEach { it.dismissImmediately(); it.cancel() }
            }
        }
    }
    private fun detach(): Boolean {
        val view = root ?: return true
        try {
            if (host != null) host.removeView(view)
            else attachedWindows?.removeViewImmediate(view)
        } catch (e: Exception) {
            Log.e("RahaFeedback", "overlay removal failed; retaining ownership for retry", e)
            if (view.isAttachedToWindow) {
                // A failed removal must not leave an invisible full-screen touch blocker.
                view.visibility = View.GONE
                val params = view.layoutParams as? WindowManager.LayoutParams
                if (params != null) runCatching {
                    params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    attachedWindows?.updateViewLayout(view, params)
                }.onFailure { Log.e("RahaFeedback", "touch unblock failed", it) }
                main.removeCallbacks(retryDetach)
                main.postDelayed(retryDetach, 1000)
                return false
            }
        }
        main.removeCallbacks(retryDetach)
        root = null; orb = null; transcriptView = null; attachedWindows = null
        visibleOwners.remove(this)
        Log.i("RahaFeedback", "overlay removed")
        return true
    }
    private val retryDetach: Runnable = Runnable { detach() }

    private val windows = context.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val policy = FeedbackStateMachine()
    private val sounds = RahaSounds(context)
    private val audioFocus = RahaAudioFocus(context) { dismissImmediately(); cancel() }
    private var root: View? = null
    private var exiting = false
    // addView succeeds before the first traversal attaches the view to its window.
    val showing get() = root != null && root?.visibility == View.VISIBLE && policy.state != FeedbackState.HIDDEN
    private var orb: RahaOrbView? = null
    private var transcriptView: TextView? = null
    private var transcript = ""
    private var promptText = ""
    private var confirmation = false
    private var light = false
    private var themeCheckedAt = 0L
    private fun dp(n: Int) = (n * context.resources.displayMetrics.density).toInt()
    private fun panelBackground(color: Int, stroke: Int = 0) = android.graphics.drawable.GradientDrawable().apply {
        setColor(color); cornerRadius=dp(20).toFloat(); if(stroke!=0) setStroke(dp(1).coerceAtLeast(1),stroke)
    }
    private val textColor get() = if(light) 0xff243551.toInt() else 0xfff4f7ff.toInt()
    private fun applyTheme() {
        val bg=if(light) 0xfff8fbff.toInt() else 0xff253857.toInt()
        val border=if(light) 0xffe0ecfa.toInt() else 0xff466083.toInt()
        transcriptView?.apply { setTextColor(textColor); background=panelBackground(bg,border); typeface=context.resources.getFont(R.font.iransansx_bold) }
        root?.findViewById<View>(R.id.rahaPrompt)?.background=panelBackground(bg,border)
        orb?.light=light
    }
    private var promptChoices = emptyList<String>()
    private var promptSelect: ((Int)->Unit)? = null
    fun prompt(text: String, choices: List<String>, confirmation: Boolean = false, selected: (Int)->Unit): Unit = onMain {
        begin(tentative=true)
        this.confirmation=confirmation
        promptText=text; promptChoices=choices; promptSelect=selected
        renderPrompt(); refresh()
        if(showing) playReady()
    }
    private fun renderPrompt() {
        val panel=root?.findViewById<android.widget.LinearLayout>(R.id.rahaPrompt) ?: return
        if(panel.isLaidOut) android.transition.TransitionManager.beginDelayedTransition(panel,
            android.transition.Fade().apply { duration=180 })
        panel.removeAllViews()
        panel.orientation=android.widget.LinearLayout.VERTICAL
        panel.visibility=if(promptText.isBlank()) View.GONE else View.VISIBLE
        if(promptText.isBlank()) return
        fun dp(n:Int)=(n*context.resources.displayMetrics.density).toInt()
        val question=TextView(context).apply { typeface=context.resources.getFont(R.font.iransansx); text=promptText; textSize=22f; setTextColor(textColor); gravity=Gravity.CENTER; setPadding(dp(8),dp(8),dp(8),dp(8)) }
        if(confirmation && promptChoices.isNotEmpty()) {
            // Explicit LTR row places controls on the left and the RTL question on the right.
            val row=android.widget.LinearLayout(context).apply { orientation=android.widget.LinearLayout.HORIZONTAL; layoutDirection=View.LAYOUT_DIRECTION_LTR; gravity=Gravity.CENTER_VERTICAL }
            fun answer(label:String, positive:Boolean, action:()->Unit) = android.widget.Button(context).apply {
                text=label; textSize=22f; typeface=context.resources.getFont(R.font.iransansx_bold)
                setTextColor(if(positive) android.graphics.Color.WHITE else textColor)
                background=panelBackground(if(positive) { if(light) 0xff4d85ee.toInt() else 0xff5283d9.toInt() } else if(light) 0xffe2eaf6.toInt() else 0xff3b5072.toInt())
                minWidth=0; minimumWidth=0; setPadding(dp(8),0,dp(8),0)
                isEnabled=receiving
                setOnClickListener { if(receiving) action() }
            }
            row.addView(answer("خیر",false) { dismiss(); cancel() },android.widget.LinearLayout.LayoutParams(dp(80),dp(64)))
            row.addView(answer("بله",true) { promptSelect?.invoke(0) },android.widget.LinearLayout.LayoutParams(dp(88),dp(64)).apply { leftMargin=dp(10); rightMargin=dp(14) })
            question.gravity=Gravity.RIGHT or Gravity.CENTER_VERTICAL;question.textDirection=View.TEXT_DIRECTION_RTL
            row.addView(question,android.widget.LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
            panel.addView(row)
            return
        }
        panel.addView(question)
        if(promptChoices.isNotEmpty()) {
            // Wait for actual window/panel bounds, including the orb and wrapped question.
            // Relayout callbacks below also handle changes of display size or density.
            val width=panel.width-panel.paddingLeft-panel.paddingRight
            if(width<=0 || root?.height==0) return
            question.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
            val bottomInset=root?.rootWindowInsets?.stableInsetBottom ?: 0
            val available=(root?.height ?: 0)-panel.top-bottomInset-dp(16)
            val count=RahaPromptLayout.buttonCount(available,question.measuredHeight,
                panel.paddingTop+panel.paddingBottom,dp(72),dp(8)).coerceAtMost(promptChoices.size)
            promptChoices.take(count).forEachIndexed { index,label ->
                panel.addView(android.widget.Button(context).apply {
                    typeface=context.resources.getFont(R.font.iransansx)
                    text=label; textSize=22f; maxLines=2; ellipsize=android.text.TextUtils.TruncateAt.END
                    setTextColor(textColor); background=panelBackground(if(light) 0xffe2eaf6.toInt() else 0xff3b5072.toInt())
                    setOnClickListener { if(receiving) promptSelect?.invoke(index) }
                },android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(72)).apply { bottomMargin=dp(8) })
            }
        }
    }
    private var closed = false
    private var focusAttempted = false
    private var voiceActive = false
    val receiving get() = policy.state in setOf(FeedbackState.WAITING, FeedbackState.LISTENING)
    private val tick: Runnable = object : Runnable {
        override fun run() {
            if (closed) return
            if (policy.state != FeedbackState.HIDDEN &&
                SystemClock.elapsedRealtime() - interactionStarted > 30_000) {
                Log.w("RahaFeedback", "overlay watchdog expired")
                dismiss(); cancel(); return
            }
            if (policy.tick(SystemClock.elapsedRealtime())) playReady()
            render()
            if (policy.state != FeedbackState.HIDDEN) main.postDelayed(this,60)
        }
    }
    fun begin(manual: Boolean = false, tentative: Boolean = false): Unit = onMain {
        if(closed || !acquireAudio()) return@onMain
        visibleOwners.toList().filter { it !== this }.forEach { it.dismissImmediately() }
        // Keep the visual host across dialogue steps and interrupted exits.
        root?.animate()?.cancel()
        exiting=false
        root?.apply { visibility=View.VISIBLE; animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).start() }
        interactionStarted = SystemClock.elapsedRealtime()
        heartbeatStartedAt = null
        light=lightTheme(); themeCheckedAt=interactionStarted
        confirmation=false
        promptText=""; promptChoices=emptyList(); promptSelect=null; transcript=""; voiceActive=false; sounds.stop(); policy.cancel(); policy.begin(interactionStarted, !manual && !tentative)
        renderPrompt()
        Log.i("RahaFeedback", "new interaction; remaining overlay owners=${visibleOwners.size}")
        if(manual && !tentative) playReady()
        refresh()
    }
    fun text(value: String): Unit = onMain {
        if(closed || value.isBlank() || !acquireAudio()) return@onMain
        transcript=value.take(2000)
        policy.text(SystemClock.elapsedRealtime(),value); refresh()
    }
    fun level(percent: Int): Unit = onMain {
        orb?.level = percent / 100f
    }
    fun speech(active: Boolean): Unit = onMain { voiceActive=active; policy.speech(SystemClock.elapsedRealtime(),active) }
    fun processing(): Unit = onMain { if(closed || !acquireAudio()) return@onMain; policy.processing(); refresh() }
    fun result(success: Boolean): Unit = onMain {
        if(closed || !acquireAudio()) return@onMain
        policy.result(SystemClock.elapsedRealtime(),success)
        sounds.play(if(success) RahaSounds.Cue.SUCCESS else RahaSounds.Cue.FAILURE)
        refresh()
    }
    /** Release transient focus before media control, so later focus restoration cannot undo Pause. */
    fun yieldAudioForMedia(): Unit = onMain { audioFocus.release() }
    fun dismiss(): Unit = onMain { policy.cancel(); promptText=""; promptChoices=emptyList(); promptSelect=null; transcript=""; voiceActive=false; sounds.stop(); main.removeCallbacks(tick); render() }
    private fun dismissImmediately() {
        dismiss();root?.animate()?.cancel();detach();exiting=false
    }
    private fun acquireAudio(): Boolean {
        if (!focusAttempted) {
            interactionStarted = SystemClock.elapsedRealtime()
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
            val view=root
            if(view!=null && !exiting) {
                exiting=true
                view.animate().cancel()
                view.animate().alpha(0f).scaleX(.96f).scaleY(.96f).setDuration(180)
                    .withEndAction { if(exiting && root===view && policy.state==FeedbackState.HIDDEN) { detach();exiting=false } }.start()
            }
            return
        }
        if(exiting) {
            root?.animate()?.cancel();exiting=false
            root?.animate()?.alpha(1f)?.scaleX(1f)?.scaleY(1f)?.setDuration(220)?.start()
        }
        // A late service connection must promote the currently visible overlay as well.
        if(host == null && root != null && attachedType == WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY && RahaOverlayService.instance != null) {
            if (!detach()) return
            Log.i("RahaFeedback", "promoting overlay to accessibility host")
        }
        if(root == null && (host != null || RahaOverlayService.instance != null || Settings.canDrawOverlays(context))) {
            val view = LayoutInflater.from(context).inflate(R.layout.overlay_raha,null)
            view.setOnClickListener { Log.i("RahaFeedback","touch cancel"); dismiss(); cancel() }
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
                root=view; visibleOwners.add(this); orb=view.findViewById(R.id.orb); transcriptView=view.findViewById(R.id.rahaTranscript)
                view.alpha=0f;view.scaleX=.96f;view.scaleY=.96f
                view.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).start()
                view.addOnLayoutChangeListener { _,l,t,r,b,oldL,oldT,oldR,oldB ->
                    if(r-l!=oldR-oldL || b-t!=oldB-oldT) renderPrompt()
                }
                view.findViewById<View>(R.id.rahaPrompt).addOnLayoutChangeListener { _,l,t,r,_,oldL,oldT,oldR,_ ->
                    if(t!=oldT || r-l!=oldR-oldL) renderPrompt()
                }
                applyTheme(); renderPrompt()
            } catch(e:Exception) { Log.e("RahaFeedback","overlay failed",e) }
        }
        val now=SystemClock.elapsedRealtime()
        if(now-themeCheckedAt>=1000) {
            themeCheckedAt=now
            val updated=lightTheme()
            if(updated!=light) { light=updated;applyTheme();renderPrompt() }
        }
        orb?.heartbeatStartedAt = heartbeatStartedAt
        orb?.phase = if(receiving) { if(voiceActive) FeedbackState.LISTENING else FeedbackState.WAITING } else policy.state
        root?.findViewById<android.widget.LinearLayout>(R.id.rahaPrompt)?.let { panel ->
            fun enableButtons(group: ViewGroup) {
                for(i in 0 until group.childCount) {
                    val child=group.getChildAt(i)
                    if(child is android.widget.Button && child.isEnabled!=receiving) {
                        child.isEnabled=receiving
                        child.animate().alpha(if(receiving)1f else .45f).setDuration(180).start()
                    }
                    else if(child is ViewGroup) enableButtons(child)
                }
            }
            enableButtons(panel)
        }
        transcriptView?.apply {
            val visible=promptText.isBlank() && showTranscript() && transcript.isNotBlank()
            visibility=if(visible) View.VISIBLE else View.GONE
            text=if(visible) transcript else ""
        }
    }
    override fun close(): Unit = onMain { dismiss();root?.animate()?.cancel();detach();exiting=false;closed=true;sounds.close() }
}
