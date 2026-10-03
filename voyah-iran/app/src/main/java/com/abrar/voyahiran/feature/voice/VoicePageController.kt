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
        ui.wakeVolume.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                ui.wakeVolumeLabel.text = "حداقل بلندی صدا برای فعال شدن رها: $progress از ۱۰۰"
                if (fromUser) prefs.minimumWakeLevel = progress
            }
            override fun onStartTrackingTouch(bar: android.widget.SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: android.widget.SeekBar?) = Unit
        })
        ui.contacts.setOnClickListener { RahaContactsDialog.show(activity) }
        ui.callsEnabled.setOnClickListener {
            prefs.callsEnabled=ui.callsEnabled.isChecked
            RahaPhonebook.sync(activity)
            render()
            if(prefs.callsEnabled) activity.lifecycleScope.launch {
                ui.contactsStatus.visibility=View.VISIBLE
                ui.contactsStatus.text="در حال بارگذاری مخاطب‌ها…"
                ui.contacts.isEnabled=false
                RahaPhonebook.refresh(activity)
                ui.contacts.isEnabled=true
                ui.contactsStatus.text=RahaPhonebook.status
            }
        }
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
        ui.wakeVolume.progress=prefs.minimumWakeLevel
        ui.wakeVolumeLabel.text="حداقل بلندی صدا برای فعال شدن رها: ${prefs.minimumWakeLevel} از ۱۰۰"
        ui.wakeVolume.isEnabled=enabled
        ui.wakeVolume.alpha=if(enabled) 1f else 0.4f
        ui.master.isChecked = enabled
        ui.callsEnabled.isChecked=prefs.callsEnabled
        ui.callsEnabled.isEnabled=enabled
        ui.callsEnabled.alpha=if(enabled) 1f else 0.4f
        ui.contacts.visibility=if(prefs.callsEnabled) View.VISIBLE else View.GONE
        ui.contactsStatus.visibility=if(prefs.callsEnabled) View.VISIBLE else View.GONE
        ui.contactsStatus.text=RahaPhonebook.status
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
