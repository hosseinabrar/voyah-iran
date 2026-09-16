package com.abrar.raha.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log

/** One transient focus lease per interaction, including its final sound. Main thread only. */
internal class RahaAudioFocus(context: Context, private val interrupted: () -> Unit) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var generation = 0L
    private var request: AudioFocusRequest? = null

    fun acquire(): Boolean {
        if (request != null) return true
        val epoch = ++generation
        val next = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener({ change ->
                Log.i("RahaAudio", "focus change=$change active=${request != null}")
                if (epoch == generation && request != null && change < 0) {
                    release()
                    interrupted()
                }
            }, main).build()
        val result = try { audio.requestAudioFocus(next) }
        catch (error: Exception) {
            Log.e("RahaAudio", "focus request failed", error)
            AudioManager.AUDIOFOCUS_REQUEST_FAILED
        }
        Log.i("RahaAudio", "transient assistant focus result=$result")
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            audio.abandonAudioFocusRequest(next)
            return false
        }
        request = next
        return true
    }

    fun release() {
        val old = request ?: return
        request = null
        generation++
        val result = audio.abandonAudioFocusRequest(old)
        Log.i("RahaAudio", "focus released result=$result; previous player may resume")
    }
}
