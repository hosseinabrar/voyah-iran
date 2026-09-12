package com.abrar.voyahvoicetest

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Local feedback is separate from the reusable recognition library. */
class FeedbackStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "voice-feedback.json"))
    private fun read(): JSONObject = if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists())
        file.openRead().bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
        else JSONObject().put("sentences", JSONArray()).put("pending", JSONArray())
    private fun write(data: JSONObject) {
        val stream = file.startWrite()
        try {
            stream.write(data.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (error: Exception) { file.failWrite(stream); throw error }
    }
    private fun JSONObject.items(key: String): List<String> = optJSONArray(key)?.let { array ->
        (0 until array.length()).map { array.getString(it) }
    }.orEmpty()
    fun add(text: String): Boolean {
        val sentence = text.trim().replace(Regex("\\s+"), " ")
        val data = read()
        val list = data.items("sentences")
        if (sentence.isBlank() || sentence in list) return false
        write(data.put("sentences", JSONArray(list + sentence)))
        return true
    }
    fun count(): Int = read().items("sentences").size
    fun hasPending(): Boolean = read().items("pending").isNotEmpty()
    fun export(context: Context): File? {
        val data = read()
        val list = data.items("sentences")
        if (list.isEmpty()) return null
        val directory = File(context.cacheDir, "feedback").apply { mkdirs() }
        val target = File(directory, "voyah-voice-samples.txt")
        target.writeText(list.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
        write(data.put("pending", JSONArray(list)))
        return target
    }
    /** Remove only the exported snapshot, preserving anything collected afterwards. */
    fun finishShare(sent: Boolean) {
        val data = read()
        if (sent) data.put("sentences", JSONArray(data.items("sentences") - data.items("pending").toSet()))
        write(data.put("pending", JSONArray()))
    }
}
