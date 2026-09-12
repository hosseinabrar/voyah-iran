package com.abrar.voyahvoicetest.speech

import org.json.JSONObject
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors

/** Audio capture never waits for decoding. The bounded queue holds at most one 16-second take. */
class LiveVoskSession(
    backend: VoskBackend,
    private val cancelled: () -> Boolean,
    partial: (String) -> Unit
) : AutoCloseable {
    private val frames = ArrayBlockingQueue<FloatArray>(512)
    private val decoder = Executors.newSingleThreadExecutor()
    private var ended = false
    private val result = decoder.submit<String> {
        backend.openRecognizer().use { recognizer ->
            val segments = mutableListOf<String>()
            var previous = ""
            while (true) {
                val frame = frames.take()
                if (frame.isEmpty()) break
                if (cancelled()) continue
                val pcm = ShortArray(frame.size) { (frame[it] * 32768).toInt().coerceIn(-32768, 32767).toShort() }
                val tail = if (recognizer.acceptWaveForm(pcm, pcm.size)) {
                    // Vosk's internal endpoint only splits text; it never stops the microphone.
                    segments += JSONObject(recognizer.result).optString("text")
                    ""
                } else JSONObject(recognizer.partialResult).optString("partial")
                val text = (segments + tail).filter(String::isNotBlank).joinToString(" ")
                if (text != previous) { previous = text; partial(text) }
            }
            if (cancelled()) "" else {
                segments += JSONObject(recognizer.finalResult).optString("text")
                segments.filter(String::isNotBlank).joinToString(" ").trim()
            }
        }
    }

    fun accept(frame: FloatArray) {
        check(!ended && frame.isNotEmpty())
        check(frames.offer(frame)) { "Live decoder queue overflow" }
    }
    fun finish(): String {
        if (!ended) {
            ended = true
            // Capture is limited to 15 seconds (469 full frames); one slot remains for the sentinel.
            check(frames.offer(FloatArray(0))) { "Cannot finish live decoder" }
        }
        return result.get()
    }
    override fun close() {
        try { finish() } finally { decoder.shutdown() }
    }
}
