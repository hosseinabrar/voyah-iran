package com.abrar.voyahvoicetest.speech

import android.content.Context
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.util.zip.ZipInputStream

/** All profiles use the same model; only scheduling and endpoint silence differ. */
enum class EngineChoice(val silence: Float, val streaming: Boolean) {
    BASELINE(0.9f, false), FAST_BATCH(0.45f, false), LIVE(0.45f, true)
}

class VoskBackend(context: Context) : AutoCloseable {
    private val model = Model(install(context).absolutePath)
    fun openRecognizer() = Recognizer(model, 16000f)

    fun transcribe(samples: FloatArray, cancelled: () -> Boolean): String {
        val pcm = ShortArray(samples.size) { (samples[it] * 32768).toInt().coerceIn(-32768, 32767).toShort() }
        return Recognizer(model, 16000f).use { recognizer ->
            val segments = mutableListOf<String>()
            var offset = 0
            while (offset < pcm.size && !cancelled()) {
                val frame = pcm.copyOfRange(offset, minOf(offset + 1600, pcm.size))
                if (recognizer.acceptWaveForm(frame, frame.size)) {
                    segments += JSONObject(recognizer.result).optString("text")
                }
                offset += frame.size
            }
            if (cancelled()) return@use ""
            segments += JSONObject(recognizer.finalResult).optString("text")
            segments.filter(String::isNotBlank).joinToString(" ").trim()
        }
    }
    override fun close() = model.close()
    private fun install(context: Context): File {
        val root = File(context.noBackupFilesDir, "speech-model")
        val name = "vosk-model-small-fa-0.42"
        val destination = File(root, name)
        val marker = File(root, "complete-$name")
        if (marker.exists() && File(destination, "am/final.mdl").isFile) return destination
        root.mkdirs()
        val prefix = root.canonicalPath + File.separator
        context.assets.open("persian-model.zip").use { raw ->
            ZipInputStream(raw.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val file = File(root, entry.name)
                    check(file.canonicalPath.startsWith(prefix)) { "Invalid model archive path" }
                    if (entry.isDirectory) file.mkdirs() else {
                        file.parentFile?.mkdirs()
                        file.outputStream().buffered().use { zip.copyTo(it) }
                    }
                    zip.closeEntry()
                }
            }
        }
        check(File(destination, "am/final.mdl").isFile)
        marker.writeText(name)
        return destination
    }
}
