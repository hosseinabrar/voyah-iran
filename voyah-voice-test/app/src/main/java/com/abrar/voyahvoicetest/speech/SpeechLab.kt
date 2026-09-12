package com.abrar.voyahvoicetest.speech

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.sqrt

data class SpeechResult(val text: String, val audioMs: Long, val decodeMs: Long, val peak: Float, val clipped: Boolean)

/** Shared input and endpoint detection make backend comparisons use the same recording path. */
class SpeechLab(context: Context) : AutoCloseable {
    interface Listener {
        fun onLevel(percent: Int)
        fun onPartial(text: String)
        fun onDecoding()
        fun onComplete(result: SpeechResult?) // null means cancelled
        fun onError(error: Throwable)
    }
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val cancelled = AtomicBoolean()
    @Volatile private var closed = false
    private var backend: VoskBackend? = null
    private var vad: Vad? = null
    private var choice = EngineChoice.BASELINE
    private var last: Recording? = null

    fun prepare(next: EngineChoice, callback: (Throwable?) -> Unit) {
        worker.execute {
            val failure = try {
                // Preserve the tested model across profile changes; only recreate the endpoint detector.
                vad?.release(); vad = null
                vad = Vad(app.assets, VadModelConfig(
                    sileroVadModelConfig = SileroVadModelConfig(
                        model = "silero_vad.onnx", minSilenceDuration = next.silence,
                        minSpeechDuration = 0.1f, maxSpeechDuration = 12f
                    ), sampleRate = RATE, numThreads = 1, debug = false
                ))
                choice = next
                if (backend == null) backend = VoskBackend(app)
                Log.i(TAG, "Ready engine=$choice")
                null
            } catch (e: Exception) { e } catch (e: LinkageError) { e } catch (e: OutOfMemoryError) { e }
            failure?.let { Log.e(TAG, "Model initialization failed engine=$next", it) }
            post { callback(failure) }
        }
    }

    fun start(reuseLast: Boolean, listener: Listener) {
        cancelled.set(false)
        worker.execute {
            var live: LiveVoskSession? = null
            var outcome: SpeechResult? = null
            var failure: Throwable? = null
            try {
                if (choice.streaming) live = LiveVoskSession(checkNotNull(backend), cancelled::get) { text ->
                    post { if (!cancelled.get()) listener.onPartial(text) }
                }
                val session = live
                val recording = if (reuseLast) checkNotNull(last) else record(listener) { session?.accept(it) }
                if (!cancelled.get()) {
                    if (!reuseLast) last = recording.takeIf { it.samples.isNotEmpty() }
                    post { if (!cancelled.get()) listener.onDecoding() }
                    val start = SystemClock.elapsedRealtime()
                    if (reuseLast && session != null) {
                        var offset = 0
                        while (offset < recording.samples.size && !cancelled.get()) {
                            val end = minOf(offset + 512, recording.samples.size)
                            session.accept(recording.samples.copyOfRange(offset, end))
                            offset = end
                        }
                    }
                    val decoded = if (session != null) session.finish()
                        else if (recording.samples.isEmpty()) ""
                        else checkNotNull(backend).transcribe(recording.samples, cancelled::get)
                    outcome = SpeechResult(if (recording.samples.isEmpty()) "" else decoded,
                        recording.samples.size * 1000L / RATE,
                        SystemClock.elapsedRealtime() - start, recording.peak, recording.clipped)
                    Log.i(TAG, "Completed profile=$choice reused=$reuseLast audioMs=${outcome.audioMs} afterCaptureMs=${outcome.decodeMs} peak=${outcome.peak} clipped=${outcome.clipped} cancelled=${cancelled.get()}")
                }
            } catch (e: Exception) { failure = e }
              catch (e: LinkageError) { failure = e }
              catch (e: OutOfMemoryError) { failure = e }
            finally {
                // Drain/join the decoder before another profile can access or free its model.
                runCatching { live?.close() }.onFailure { if (failure == null) failure = it }
            }
            failure?.let { Log.e(TAG, "Speech operation failed profile=$choice", it) }
            val error = failure
            val result = outcome
            post {
                if (cancelled.get()) listener.onComplete(null)
                else if (error != null) listener.onError(error)
                else listener.onComplete(result)
            }
        }
    }

    private data class Recording(val samples: FloatArray, val peak: Float, val clipped: Boolean)
    @SuppressLint("MissingPermission")
    private fun record(listener: Listener, onAudio: (FloatArray) -> Unit): Recording {
        val detector = checkNotNull(vad)
        detector.reset()
        val minimum = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minimum > 0) { "Unsupported audio format: $minimum" }
        val recorder = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum, 6400))
        try {
            check(recorder.state == AudioRecord.STATE_INITIALIZED) { "Microphone unavailable" }
            recorder.startRecording()
            check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING)
            Log.i(TAG, "Microphone started rate=$RATE source=VOICE_RECOGNITION")
            val all = FloatArray(RATE * 16)
            val pcm = ShortArray(512)
            var total = 0
            var speech = false
            var frames = 0
            var peak = 0f
            var clipped = false
            val deadline = SystemClock.elapsedRealtime() + 15_000
            while (!cancelled.get() && total + pcm.size <= all.size && SystemClock.elapsedRealtime() < deadline) {
                val count = recorder.read(pcm, 0, pcm.size, AudioRecord.READ_BLOCKING)
                check(count > 0) { "Microphone read failed: $count" }
                val frame = FloatArray(count) { pcm[it] / 32768f }
                var energy = 0.0
                for (sample in frame) {
                    peak = maxOf(peak, abs(sample)); energy += sample * sample
                    if (abs(sample) >= 0.999f) clipped = true
                }
                frame.copyInto(all, total); total += count
                onAudio(frame)
                detector.acceptWaveform(frame)
                speech = speech || detector.isSpeechDetected()
                if (++frames % 4 == 0) {
                    val level = (sqrt(energy / count) * 400).toInt().coerceIn(0, 100)
                    post { if (!cancelled.get()) listener.onLevel(level) }
                }
                if (!detector.empty()) break
                if (!speech && total >= RATE * 6) break
            }
            detector.flush()
            val detected = speech || !detector.empty()
            // Keep pre-roll and trailing silence; identical samples are reused across engines.
            return Recording(if (detected) all.copyOf(total) else FloatArray(0), peak, clipped)
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
            detector.reset()
            Log.i(TAG, "Microphone released before decoding")
        }
    }
    fun cancel() { cancelled.set(true) }
    private fun post(action: () -> Unit) { main.post { if (!closed) action() } }
    override fun close() {
        closed = true; cancel()
        worker.execute { backend?.close(); backend = null; vad?.release(); vad = null; last = null }
    }
    companion object {
        private const val RATE = 16000
        private const val TAG = "VoyahVoice"
        // Serialize across Activity recreation too: free the old native model before loading another.
        private val worker = Executors.newSingleThreadExecutor()
    }
}
