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
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.abrar.voyahvoicetest.databinding.ActivityMainBinding
import com.abrar.voyahvoicetest.speech.EngineChoice
import com.abrar.voyahvoicetest.speech.SpeechLab
import com.abrar.voyahvoicetest.speech.SpeechResult
import com.abrar.voicecommands.PersianCommandRecognizer
import com.abrar.voicecommands.Recognition
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private lateinit var ui: ActivityMainBinding
    private lateinit var lab: SpeechLab
    private val handler = Handler(Looper.getMainLooper())
    private var busy = false
    private var loaded = false
    private var foreground = false
    private var lastAvailable = false
    private var selected = -1
    private var selectionVersion = 0
    private val commands = PersianCommandRecognizer()
    private lateinit var feedback: FeedbackStore
    private var unknownSentence: String? = null
    private var feedbackDialog: AlertDialog? = null
    private val clearResult = Runnable {
        ui.transcript.text = ""
        ui.status.setText(R.string.ready)
    }
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && foreground && loaded && !busy) start(false)
        else if (!granted) ui.status.setText(R.string.permission)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ui = ActivityMainBinding.inflate(layoutInflater)
        setContentView(ui.root)
        feedback = FeedbackStore(applicationContext)
        unknownSentence = savedInstanceState?.getString("unknownSentence")
        updateSampleCount()
        ui.shareSamples.setOnClickListener {
            AlertDialog.Builder(this).setMessage(R.string.share_instruction)
                .setPositiveButton(R.string.share_continue) { _, _ -> shareSamples() }
                .setNegativeButton(android.R.string.cancel, null).show()
        }
        ViewCompat.setOnApplyWindowInsetsListener(ui.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        lab = SpeechLab(applicationContext)
        ui.engine.adapter = ArrayAdapter.createFromResource(this, R.array.engines, R.layout.engine_item)
            .apply { setDropDownViewResource(R.layout.engine_dropdown_item) }
        ui.engine.setSelection(EngineChoice.LIVE.ordinal)
        ui.engine.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == selected || busy) return
                selected = position
                val version = ++selectionVersion
                handler.removeCallbacks(clearResult)
                loaded = false
                renderButtons()
                ui.engine.isEnabled = false
                ui.status.setText(R.string.loading)
                ui.transcript.text = ""
                clearCommand()
                lab.prepare(EngineChoice.entries[position]) { error ->
                    if (version == selectionVersion) {
                        loaded = error == null
                        renderButtons()
                        ui.status.setText(if (loaded) R.string.ready else R.string.model_error)
                        ui.metrics.text = error?.javaClass?.simpleName.orEmpty()
                    }
                }
            }
        }
        ui.listen.setOnClickListener {
            handler.removeCallbacks(clearResult)
            if (busy) cancel()
            else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start(false)
            else permission.launch(Manifest.permission.RECORD_AUDIO)
        }
        ui.repeat.setOnClickListener { start(true) }
    }

    private fun renderButtons() {
        ui.listen.isEnabled = loaded
        ui.listen.setText(if (busy) R.string.cancel else R.string.start)
        ui.engine.isEnabled = !busy
        ui.repeat.isEnabled = loaded && !busy && lastAvailable
        ui.shareSamples.isEnabled = !busy
    }
    private fun start(reuseLast: Boolean) {
        if (!loaded || busy) return
        handler.removeCallbacks(clearResult)
        busy = true
        renderButtons()
        ui.transcript.text = ""
        clearCommand()
        ui.metrics.text = ""
        ui.level.progress = 0
        ui.status.setText(if (reuseLast) R.string.decoding else R.string.listening)
        lab.start(reuseLast, object : SpeechLab.Listener {
            override fun onPartial(text: String) { ui.transcript.text = text }
            override fun onLevel(percent: Int) { ui.level.progress = percent }
            override fun onDecoding() {
                ui.status.setText(R.string.decoding)
                ui.level.progress = 0
            }
            override fun onComplete(result: SpeechResult?) {
                busy = false
                ui.level.progress = 0
                if (result == null) {
                    ui.status.setText(R.string.ready)
                    clearCommand()
                } else {
                    lastAvailable = result.audioMs > 0
                    ui.transcript.text = result.text
                    showCommand(result.text)
                    ui.status.setText(if (result.text.isBlank()) R.string.no_speech else R.string.result_status)
                    ui.metrics.text = getString(R.string.timing, result.audioMs / 1000.0, result.decodeMs / 1000.0) +
                        when {
                            result.clipped -> getString(R.string.clipped)
                            result.peak < 0.01f -> getString(R.string.quiet)
                            else -> ""
                        }
                    if (unknownSentence == null) handler.postDelayed(clearResult, 5000)
                }
                renderButtons()
            }
            override fun onError(error: Throwable) {
                busy = false
                ui.level.progress = 0
                ui.status.setText(R.string.error)
                clearCommand()
                ui.metrics.text = error.javaClass.simpleName
                renderButtons()
            }
        })
    }
    private fun cancel() {
        lab.cancel()
        ui.listen.isEnabled = false
        ui.status.setText(R.string.cancelling)
        // Native decoding cannot be interrupted safely. Keep controls locked until it returns.
    }
    private fun clearCommand() {
        ui.commandSummary.setText(R.string.command_waiting)
        ui.commandDetails.text = ""
    }
    private fun showCommand(finalTranscript: String) {
        when (val result = commands.recognize(finalTranscript)) {
            is Recognition.Matched -> {
                ui.commandSummary.text = result.command.description
                ui.commandDetails.text = result.command.action + "\n" + JSONObject(result.command.parameters).toString()
                android.util.Log.i("VoyahVoice", "Mapping matched id=${result.command.catalogId} action=${result.command.action}; preview only")
            }
            is Recognition.Unknown -> {
                ui.commandSummary.setText(R.string.command_unknown)
                ui.commandDetails.text = ""
                if (finalTranscript.isNotBlank()) {
                    unknownSentence = finalTranscript
                    askToSave()
                }
                android.util.Log.i("VoyahVoice", "Mapping unknown; preview only")
            }
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
        if (!foreground || feedbackDialog != null) return
        val dialog = AlertDialog.Builder(this).setTitle(R.string.feedback_title)
            .setMessage(getString(R.string.feedback_question, sentence))
            .setPositiveButton(R.string.feedback_yes, null)
            .setNegativeButton(R.string.feedback_no) { _, _ -> unknownSentence = null }
            .create()
        dialog.setOnCancelListener { unknownSentence = null }
        dialog.setOnDismissListener { feedbackDialog = null }
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
            dialog.setOnDismissListener { feedbackDialog = null }
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
        askToSave()
        confirmShare()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("unknownSentence", unknownSentence)
        super.onSaveInstanceState(outState)
    }
    override fun onStop() {
        foreground = false
        if (busy) cancel()
        handler.removeCallbacks(clearResult)
        super.onStop()
    }
    override fun onDestroy() {
        feedbackDialog?.setOnDismissListener(null)
        feedbackDialog?.dismiss()
        handler.removeCallbacksAndMessages(null)
        lab.close()
        super.onDestroy()
    }
}
