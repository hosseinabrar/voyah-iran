package com.abrar.raha.speech

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineNeMoCtcModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors

/** Reads bundled INT8 models directly from assets; no network or second installed model copy. */
class ShenavaBackend(context: Context, val modelName: String) : AutoCloseable {
    val recognizer = OnlineRecognizer(context.assets, OnlineRecognizerConfig(
        featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
        modelConfig = OnlineModelConfig(
            neMoCtc = OnlineNeMoCtcModelConfig(model = "shenava/$modelName/model.int8.onnx"),
            tokens = "shenava/$modelName/tokens.txt", numThreads = 2, provider = "cpu"
        ),
        enableEndpoint = false, decodingMethod = "greedy_search"
    ))
    override fun close() { recognizer.release() }
}

/** Native decoding stays off the capture thread, with one stream per recording/replay. */
class LiveShenavaSession(
    backend: ShenavaBackend,
    private val cancelled: () -> Boolean,
    partial: (String) -> Unit
) : LiveSpeechSession {
    private val frames = ArrayBlockingQueue<FloatArray>(512)
    private val decoder = Executors.newSingleThreadExecutor()
    private var ended = false
    private val result = decoder.submit<String> {
        val recognizer = backend.recognizer
        val stream = recognizer.createStream()
        try {
            var previous = ""
            while (true) {
                val frame = frames.take()
                if (frame.isEmpty()) break
                if (cancelled()) continue
                stream.acceptWaveform(frame, 16000)
                while (!cancelled() && recognizer.isReady(stream)) recognizer.decode(stream)
                val text = recognizer.getResult(stream).text.trim()
                if (text != previous) { previous = text; partial(text) }
            }
            if (cancelled()) "" else {
                // Flush the final acoustic context without extending microphone recording.
                stream.acceptWaveform(FloatArray(8000), 16000)
                stream.inputFinished()
                while (!cancelled() && recognizer.isReady(stream)) recognizer.decode(stream)
                if (cancelled()) "" else recognizer.getResult(stream).text.trim()
            }
        } finally { stream.release() }
    }
    override fun accept(frame: FloatArray) {
        check(!ended && frame.isNotEmpty())
        check(frames.offer(frame)) { "Shenava decoder queue overflow" }
    }
    override fun finish(): String {
        if (!ended) {
            ended = true
            check(frames.offer(FloatArray(0))) { "Cannot finish Shenava decoder" }
        }
        return result.get()
    }
    override fun close() {
        try { finish() } finally { decoder.shutdown() }
    }
}
