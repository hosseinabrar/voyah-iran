package com.abrar.voyahiran.feature.voice

import android.Manifest
import android.content.pm.PackageManager
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import com.abrar.voyahiran.R
import com.abrar.voyahiran.databinding.PageVoiceBinding
import kotlinx.coroutines.launch

class VoicePageController(private val activity: ComponentActivity, private val ui: PageVoiceBinding) {
    private val prefs = VoicePreferences(activity)
    private val microphone = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        prefs.enabled = granted
        render()
        if (granted) RahaService.resume(activity)
        else { ui.error.setText(R.string.raha_mic_denied); ui.error.visibility = View.VISIBLE }
    }
    fun bind() {
        ui.showTranscript.setOnClickListener { prefs.showTranscript=ui.showTranscript.isChecked }
        ui.master.setOnClickListener {
            if (ui.master.isChecked && activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ui.master.isChecked = false
                microphone.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                prefs.enabled = ui.master.isChecked
                render()
                RahaService.sync(activity)
            }
        }
        val boxes = listOf(ui.wakeHey, ui.wakeHello, ui.wakeName)
        boxes.forEach { box -> box.setOnClickListener {
            prefs.phrases = boxes.zip(VoicePreferences.PHRASES).filter { it.first.isChecked }.map { it.second }.toSet()
            RahaService.sync(activity)
        } }
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) { render(); RahaService.resume(activity) }
        })
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                RahaService.error.collect { error ->
                    ui.error.visibility = if (error) View.VISIBLE else View.GONE
                    ui.error.setText(R.string.raha_error)
                }
            }
        }
        render()
    }
    private fun render() {
        val enabled = prefs.enabled
        ui.master.isChecked = enabled
        ui.showTranscript.isChecked=prefs.showTranscript
        ui.showTranscript.isEnabled=enabled
        ui.showTranscript.alpha=if(enabled) 1f else 0.4f
        ui.wakeOptions.alpha = if (enabled) 1f else 0.4f
        listOf(ui.wakeHey, ui.wakeHello, ui.wakeName).zip(VoicePreferences.PHRASES).forEach { (box, phrase) ->
            box.isEnabled = enabled
            box.isChecked = phrase in prefs.phrases
        }
    }
}
