package com.abrar.raha.feedback

import android.content.Context
import android.media.*
import com.abrar.raha.feedback.R
import android.util.Log

class RahaSounds(context: Context) : AutoCloseable {
    enum class Cue { READY, SUCCESS, FAILURE }
    private val audio = context.getSystemService(AudioManager::class.java)
    // OEM SceneIntentService maps voiceSystem (Sys) to stream 5; stream 1 is navigation.
    private val attributes = AudioAttributes.Builder().setLegacyStreamType(AudioManager.STREAM_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    private val pool = SoundPool.Builder().setMaxStreams(1).setAudioAttributes(attributes).build()
    private val loaded = mutableSetOf<Int>()
    private val ids: Map<Cue, Int>
    private var stream = 0
    init {
        pool.setOnLoadCompleteListener { _, id, status -> if(status == 0) loaded += id }
        ids = mapOf(Cue.READY to pool.load(context,R.raw.raha_ready,1),
            Cue.SUCCESS to pool.load(context,R.raw.raha_success,1), Cue.FAILURE to pool.load(context,R.raw.raha_failure,1))
    }
    fun play(cue: Cue) {
        val id = ids.getValue(cue)
        if (id !in loaded) { Log.i("RahaFeedback","cue=$cue skipped: not loaded"); return }
        stop()
        stream = pool.play(id,1f,1f,1,0,1f)
        Log.i("RahaFeedback","cue=$cue playbackId=$stream audioStream=5 sysVolume=${audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION)} sysMax=${audio.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)}")
    }
    fun stop() {
        if(stream != 0) pool.stop(stream)
        stream = 0
    }
    override fun close() { stop(); pool.release() }
}
