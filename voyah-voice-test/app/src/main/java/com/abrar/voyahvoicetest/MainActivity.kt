package com.abrar.voyahvoicetest

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.ClipData
import android.widget.Toast
import androidx.core.content.FileProvider
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.abrar.voyahvoicetest.databinding.ActivityMainBinding
import com.abrar.raha.speech.EngineChoice
import com.abrar.raha.speech.SpeechLab
import com.abrar.raha.speech.SpeechResult
import com.abrar.raha.feedback.RahaFeedback
import com.abrar.raha.intent.RahaIntentModel
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import com.abrar.voicecommands.Recognition
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private lateinit var ui: ActivityMainBinding
    private lateinit var lab: SpeechLab
    private lateinit var visual: RahaFeedback
    private val handler = Handler(Looper.getMainLooper())
    private var watching=false
    private var wakeFailed=false
    private var manualPending=false
    private var wakePermission=false
    private var generation=0L
    private var busy=false
    private var loaded=false
    private var foreground=false
    private val commands by lazy { RahaIntentModel(this) }
    private var recognizing=false
    private lateinit var feedback: FeedbackStore
    private var unknownSentence:String?=null
    private var feedbackDialog:AlertDialog?=null
    private val clearResult=Runnable { ui.status.setText(R.string.ready) }
    private fun wakePhrases()=buildSet {
        if(ui.wakeHey.isChecked)add("هی رها")
        if(ui.wakeHello.isChecked)add("سلام رها")
        if(ui.wakeName.isChecked)add("رها")
    }
    private fun lamp(active:Boolean) {
        ui.wakeLamp.isActivated=active
        ui.wakeState.setText(if(active)R.string.wake_receiving else if(watching)R.string.wake_waiting else R.string.wake_idle)
    }
    private val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if(granted && foreground && loaded && !busy) { if(wakePermission)ensureWake() else start(); wakePermission=false }
        else if(!granted) {wakeFailed=true;wakePermission=false;ui.status.setText(R.string.permission)}
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ui=ActivityMainBinding.inflate(layoutInflater);setContentView(ui.root)
        feedback=FeedbackStore(applicationContext)
        unknownSentence=savedInstanceState?.getString("unknownSentence")
        updateSampleCount()
        ui.reviewUnknown.visibility=if(unknownSentence==null)View.GONE else View.VISIBLE
        ui.reviewUnknown.setOnClickListener { askToSave() }
        ui.shareSamples.setOnClickListener {
            AlertDialog.Builder(this).setMessage(R.string.share_instruction)
                .setPositiveButton(R.string.share_continue) { _,_->shareSamples() }
                .setNegativeButton(android.R.string.cancel,null).show()
        }
        ViewCompat.setOnApplyWindowInsetsListener(ui.root) { view,insets ->
            val bars=insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left,bars.top,bars.right,bars.bottom);insets
        }
        val prefs=getSharedPreferences("wake",MODE_PRIVATE)
        ui.showTranscript.isChecked=prefs.getBoolean("showTranscript",false)
        ui.showTranscript.setOnCheckedChangeListener { _,checked -> prefs.edit().putBoolean("showTranscript",checked).apply() }
        visual=RahaFeedback(this,::cancel,showTranscript={ui.showTranscript.isChecked},host=window.decorView as ViewGroup)
        lab=SpeechLab(applicationContext)
        lab.prepare(EngineChoice.CAR_LIVE) { error ->
            lifecycleScope.launch {
                val failure=if(error!=null)error else try { withContext(Dispatchers.IO){commands.prepare()};null }
                    catch(e:CancellationException){throw e}
                    catch(e:Exception){e}
                    catch(e:LinkageError){e}
                    catch(e:OutOfMemoryError){e}
                loaded=failure==null;renderButtons()
                ui.status.setText(if(loaded)R.string.ready else R.string.model_error)
                ui.metrics.text=failure?.javaClass?.simpleName.orEmpty();ensureWake()
            }
        }
        ui.listen.setOnClickListener {
            handler.removeCallbacks(clearResult)
            if(watching) { generation++;visual.dismiss();manualPending=true;lab.cancel() }
            else if(busy)cancel()
            else if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)start()
            else permission.launch(Manifest.permission.RECORD_AUDIO)
        }
        ui.wakeSensitivity.adapter=android.widget.ArrayAdapter.createFromResource(
            this,R.array.wake_sensitivity_options,R.layout.engine_item).also { it.setDropDownViewResource(R.layout.engine_dropdown_item) }
        ui.wakeSensitivity.setSelection(prefs.getInt("sensitivity",2).coerceIn(0,3))
        ui.wakeSensitivity.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent:android.widget.AdapterView<*>?) = Unit
            override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:View?,position:Int,id:Long) {
                if(prefs.getInt("sensitivity",2)==position)return
                prefs.edit().putInt("sensitivity",position).apply()
                wakeFailed=false
                if(watching) { generation++;visual.dismiss();lab.cancel() } else ensureWake()
            }
        }
        ui.wakeHey.isChecked=prefs.getBoolean("hey",false)
        ui.wakeHello.isChecked=prefs.getBoolean("hello",false)
        ui.wakeName.isChecked=prefs.getBoolean("name",false)
        for(box in listOf(ui.wakeHey,ui.wakeHello,ui.wakeName)) box.setOnCheckedChangeListener { _,_->
            prefs.edit().putBoolean("hey",ui.wakeHey.isChecked).putBoolean("hello",ui.wakeHello.isChecked)
                .putBoolean("name",ui.wakeName.isChecked).apply()
            wakeFailed=false
            if(watching) { generation++;visual.dismiss();lab.cancel() }
            else if(wakePhrases().isNotEmpty() && checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {
                wakePermission=true;permission.launch(Manifest.permission.RECORD_AUDIO)
            } else ensureWake()
        }
    }
    private fun renderButtons() {
        ui.listen.isEnabled=loaded && !recognizing
        ui.listen.setText(if(busy)R.string.cancel else R.string.start)
        ui.shareSamples.isEnabled=!busy && !watching
    }
    private fun ensureWake() {
        if(!foreground || !loaded || busy || recognizing || watching || wakeFailed || feedbackDialog!=null || wakePhrases().isEmpty())return
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)return
        watching=true;lamp(false);renderButtons()
        val epoch=++generation
        lab.watch(wakePhrases(),object:SpeechLab.WakeListener {
            private fun current()=foreground && epoch==generation
            override fun onCandidate(active:Boolean) {
                if(!current())return
                lamp(active)
                if(active)visual.begin(tentative=true) else if(visual.receiving)visual.dismiss()
            }
            override fun onState(active:Boolean) {
                if(!current())return
                lamp(active)
                if(active)visual.begin() else if(visual.receiving)visual.dismiss()
            }
            override fun onLevel(percent:Int) {if(current())visual.level(percent)}
            override fun onSpeech(active:Boolean) {if(current())visual.speech(active)}
            override fun onText(text:String) {if(current())visual.text(text)}
            override fun onCommand(text:String) {if(current())showCommand(text, silentUnknown=true)}
            override fun onStopped(error:Throwable?) {
                watching=false;lamp(false);renderButtons()
                if(error!=null && current()) {wakeFailed=true;ui.status.setText(R.string.error);ui.metrics.text=error.javaClass.simpleName;visual.result(false)}
                if(manualPending && foreground) {manualPending=false;start()} else ensureWake()
            }
        }, sensitivity=com.abrar.raha.WakeSensitivity.values()[ui.wakeSensitivity.selectedItemPosition.coerceIn(0,3)])
    }
    private fun start() {
        if(!loaded || busy || recognizing || watching || !foreground)return
        handler.removeCallbacks(clearResult)
        busy=true;lamp(true);renderButtons();clearCommand();ui.metrics.text=""
        ui.status.setText(R.string.listening)
        val epoch=++generation
        visual.begin(manual=true)
        lab.start(false,object:SpeechLab.Listener {
            private fun current()=foreground && epoch==generation
            override fun onPartial(text:String) {if(current())visual.text(text)}
            override fun onLevel(percent:Int) {if(current())visual.level(percent)}
            override fun onDecoding() {if(current()) {lamp(false);visual.processing();ui.status.setText(R.string.decoding)}}
            override fun onComplete(result:SpeechResult?) {
                busy=false;lamp(false)
                if(current()) {
                    if(result==null) {ui.status.setText(R.string.no_speech);visual.result(false)}
                    else {
                        showCommand(result.text)
                        ui.status.setText(if(result.text.isBlank())R.string.no_speech else R.string.result_status)
                        ui.metrics.text=getString(R.string.timing,result.audioMs/1000.0,result.decodeMs/1000.0)
                        handler.postDelayed(clearResult,5000)
                    }
                } else ui.status.setText(R.string.ready)
                renderButtons();ensureWake()
            }
            override fun onError(error:Throwable) {
                busy=false;lamp(false)
                if(current()) {ui.status.setText(R.string.error);ui.metrics.text=error.javaClass.simpleName;visual.result(false)}
                renderButtons();ensureWake()
            }
        })
    }
    private fun cancel() {
        generation++;manualPending=false;visual.dismiss();lab.cancel()
        handler.removeCallbacks(clearResult)
        ui.status.setText(if(busy || watching)R.string.cancelling else R.string.ready)
        // Stale callbacks cannot display text, play a cue or map a command after cancellation.
    }
    private fun clearCommand() {ui.commandSummary.setText(R.string.command_waiting);ui.commandDetails.text=""}
    private fun showCommand(text:String, silentUnknown:Boolean=false) {
        visual.text(text)
        if(recognizing)return
        recognizing=true
        val epoch=generation
        lifecycleScope.launch {
          try {
            val result=withContext(Dispatchers.Default){commands.recognize(text)}
            if(epoch!=generation || !foreground)return@launch
            when(result) {
            is Recognition.Matched -> {
                ui.commandSummary.text=result.command.description
                ui.commandDetails.text=result.command.action+"\n"+JSONObject(result.command.parameters)
                visual.result(true)
                android.util.Log.i("VoyahVoice","Raha action=${result.command.action}; preview only")
            }
            is Recognition.Unknown -> {
                ui.commandSummary.setText(R.string.command_unknown);ui.commandDetails.text=result.reason
                if(silentUnknown)visual.dismiss() else visual.result(false)
                if(text.isNotBlank()) {unknownSentence=text;ui.reviewUnknown.visibility=View.VISIBLE}
            }
            }
          } catch(e:CancellationException){throw e}
            catch(e:Exception){ui.status.setText(R.string.error);visual.result(false)}
            catch(e:LinkageError){ui.status.setText(R.string.error);visual.result(false)}
            catch(e:OutOfMemoryError){ui.status.setText(R.string.error);visual.result(false)}
          finally { recognizing=false;renderButtons();ensureWake() }
        }
    }
    private fun updateSampleCount() {
        try { ui.shareSamples.text = getString(R.string.share_samples, feedback.count()) }
        catch (error: Exception) { feedbackError(error) }
    }
    private fun feedbackError(error: Exception) {
        android.util.Log.e("VoyahVoice", "Feedback operation failed", error)
        Toast.makeText(this, R.string.feedback_error, Toast.LENGTH_LONG).show()
    }
    private fun askToSave() {
        val sentence = unknownSentence ?: return
        if(watching) { generation++; lab.cancel() }
        if (!foreground || feedbackDialog != null) return
        val dialog = AlertDialog.Builder(this).setTitle(R.string.feedback_title)
            .setMessage(getString(R.string.feedback_question, sentence))
            .setPositiveButton(R.string.feedback_yes, null)
            .setNegativeButton(R.string.feedback_no) { _, _ -> unknownSentence = null }
            .create()
        dialog.setOnCancelListener { unknownSentence = null }
        dialog.setOnDismissListener { feedbackDialog = null; ui.reviewUnknown.visibility=if(unknownSentence==null)View.GONE else View.VISIBLE; ensureWake() }
        feedbackDialog = dialog
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try {
                val added = feedback.add(sentence)
                unknownSentence = null
                updateSampleCount()
                Toast.makeText(this, if (added) R.string.feedback_saved else R.string.feedback_duplicate, Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            } catch (error: Exception) { feedbackError(error) }
        }
    }
    private fun shareSamples() {
        try {
            val file = feedback.export(this)
            if (file == null) {
                Toast.makeText(this, R.string.feedback_empty, Toast.LENGTH_SHORT).show()
                return
            }
            val uri = FileProvider.getUriForFile(this, "$packageName.feedback", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newUri(contentResolver, "Voice samples", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share_instruction)))
        } catch (error: Exception) { feedbackError(error) }
    }
    private fun confirmShare() {
        if (feedbackDialog != null) return
        try {
            if (!feedback.hasPending()) return
            val dialog = AlertDialog.Builder(this).setTitle(R.string.share_confirm)
                .setMessage(R.string.share_confirm_message)
                .setPositiveButton(R.string.share_done, null)
                .setNegativeButton(R.string.share_keep, null).create()
            dialog.setOnDismissListener { feedbackDialog = null; ui.reviewUnknown.visibility=if(unknownSentence==null)View.GONE else View.VISIBLE; ensureWake() }
            feedbackDialog = dialog
            dialog.show()
            fun finish(sent: Boolean) {
                try {
                    feedback.finishShare(sent)
                    updateSampleCount()
                    dialog.dismiss()
                } catch (error: Exception) { feedbackError(error) }
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { finish(true) }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { finish(false) }
        } catch (error: Exception) { feedbackError(error) }
    }
    override fun onStart() { super.onStart(); foreground = true }
    override fun onResume() {
        super.onResume()
        confirmShare()
        ensureWake()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("unknownSentence", unknownSentence)
        super.onSaveInstanceState(outState)
    }
    override fun onStop() {
        foreground = false
        visual.dismiss()
        generation++
        manualPending = false
        if(watching) { generation++; lab.cancel() }
        lamp(false)
        if (busy) cancel()
        handler.removeCallbacks(clearResult)
        super.onStop()
    }
    override fun onDestroy() {
        feedbackDialog?.setOnDismissListener(null)
        feedbackDialog?.dismiss()
        handler.removeCallbacksAndMessages(null)
        visual.close()
        lab.close()
        CoroutineScope(Dispatchers.IO).launch { commands.close() }
        super.onDestroy()
    }
}
