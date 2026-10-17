package com.abrar.voyahaudiotest

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** All writes require a persisted rollback record. No production bridge/token/subscription changes. */
class ProbeClient(private val context: Context, private val report: (String) -> Unit) {
    private class ProbeFailure(message: String, val mayHaveChanged: Boolean) : IllegalStateException(message)
    private val directory = "/data/local/tmp/voyah-audio-test"
    private val prefs = context.getSharedPreferences("rollback", Context.MODE_PRIVATE)
    private val sessions = ConcurrentHashMap<String, Session>()
    private val targets = mapOf("oem" to "com.qinggan.audiopolicy.service", "bluetooth" to "com.android.bluetooth")
    @Volatile private var closing = false
    @Volatile var foreground = true
    @Volatile private var generation = 0
    private var recoveryRequired = false
    private val logFile = File(context.getExternalFilesDir(null) ?: context.filesDir, "audio-lab.log")

    @Synchronized fun log(message: String) {
        val line = "${java.time.Instant.now()} $message"
        if (logFile.length() > 2_000_000) {
            val previous = File(logFile.parentFile, "audio-lab.previous.log")
            previous.delete(); logFile.renameTo(previous)
        }
        logFile.appendText(line + "\n")
        android.util.Log.i("VoyahAudioLab", message.take(3000)); report(line)
    }
    fun logText() = logFile.takeIf { it.exists() }?.readText().orEmpty()
    private fun root(vararg command: String): String {
        val process = ProcessBuilder(listOf("su", "0") + command).redirectErrorStream(true).start()
        val output = StringBuilder()
        val reader = thread(isDaemon = true) { process.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { synchronized(output) { if (output.length < 2_000_000) output.append(it).append('\n') } }
        } }
        try {
            check(process.waitFor(15, TimeUnit.SECONDS)) { "Root command timeout: ${command.firstOrNull()}" }
            reader.join(1000)
            check(process.exitValue() == 0) { "Root command failed: $output" }
            return output.toString().trim()
        } finally { process.destroy(); process.inputStream.close() }
    }
    private fun deploy() {
        check(root("id", "-u") == "0") { "Root required" }
        check(root("getprop", "ro.product.cpu.abi") == "arm64-v8a") { "This probe runtime requires arm64" }
        root("mkdir", "-p", directory)
        for (name in listOf("frida-inject", "audio-probe.js")) {
            val source = File(context.cacheDir, name)
            context.assets.open(name).use { input -> source.outputStream().use { output -> input.copyTo(output) } }
            val hash = MessageDigest.getInstance("SHA-256").let { digest ->
                source.inputStream().use { input ->
                    val bytes = ByteArray(65536)
                    while (true) { val n = input.read(bytes); if (n < 0) break; digest.update(bytes, 0, n) }
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
            if (name == "frida-inject") check(hash == "5e5ad93e0f12538829b5f98fb65f1056a72da5f1eb380ada1ddf8a89716b5cc9") { "Injector hash mismatch" }
            root("cp", source.path, "$directory/$name")
            check(root("sha256sum", "$directory/$name").substringBefore(' ') == hash) { "Deployment hash mismatch" }
            source.delete()
        }
        root("chmod", "755", "$directory/frida-inject")
        root("chmod", "644", "$directory/audio-probe.js")
    }
    private inner class Session(val role: String, val process: Process) {
        val responses = ConcurrentHashMap<String, LinkedBlockingQueue<JSONObject>>()
        val ready = LinkedBlockingQueue<JSONObject>()
        val capabilities = java.util.concurrent.CopyOnWriteArraySet<String>()
        init {
            thread(name = "audio-$role-output", isDaemon = true) {
                try { process.inputStream.bufferedReader().useLines { lines -> lines.forEach { line ->
                    log("[$role] $line")
                    val offset = line.indexOf("AUDIO_LAB ")
                    if (offset >= 0) runCatching {
                        val json = JSONObject(line.substring(offset + 10))
                        if (json.optString("event") == "ready") ready.offer(json)
                        if (json.has("id")) responses[json.optString("id")]?.offer(json)
                        // Only clear durable rollback after explicit feedback confirmation.
                        if (json.optString("event") == "restored") clear(role, json.getString("key"))
                    }.onFailure { log("Parse failure: $it") }
                } } } catch (e: Exception) { log("[$role] Reader stopped: $e") }
            }
        }
        @Synchronized fun send(json: JSONObject) {
            check(process.isAlive) { "$role injector exited" }
            process.outputStream.write((json.toString() + "\n").toByteArray())
            process.outputStream.flush()
        }
        fun request(op: String, key: String = "", before: Any? = null, value: Int? = null): JSONObject {
            val id = UUID.randomUUID().toString()
            val queue = LinkedBlockingQueue<JSONObject>(); responses[id] = queue
            try {
                send(JSONObject().put("id", id).put("op", op).put("key", key).apply {
                    if (before != null) put("before", before)
                    if (value != null) put("value", value)
                })
                val response = queue.poll(10, TimeUnit.SECONDS) ?: error("$role $op timed out; rollback retained")
                if (!response.optBoolean("ok")) throw ProbeFailure(response.optString("error"), response.optBoolean("mayHaveChanged", true))
                return response.optJSONObject("result") ?: JSONObject()
            } finally { responses.remove(id) }
        }
    }
    @Synchronized private fun save(role: String, key: String, before: Any) {
        check(prefs.edit().putString("$role:$key", JSONObject().put("before", before).toString()).commit()) { "Cannot save rollback; write refused" }
    }
    @Synchronized private fun clear(role: String, key: String) {
        check(prefs.edit().remove("$role:$key").commit()) { "Cannot clear rollback journal" }
    }
    fun pending() = prefs.all.keys.sorted()
    fun available(key: String) = sessions[role(key)]?.capabilities?.contains(key) == true
    private fun role(key: String) = if (key in listOf("duck", "absolute")) "bluetooth" else "oem"
    fun connect() {
        check(sessions.isEmpty()) { "Already connected; stop/restore first" }
        check(!closing) { "Previous session is closing" }
        recoveryRequired = pending().isNotEmpty()
        val thisGeneration = ++generation
        deploy()
        for ((role, target) in targets) {
            try {
                val pid = root("pidof", target).split(Regex("\\s+")).single()
                check(pid.matches(Regex("[1-9][0-9]*")))
                check(root("cat", "/proc/$pid/cmdline").substringBefore('\u0000') == target) { "Process identity mismatch" }
                val process = ProcessBuilder("su", "0", "$directory/frida-inject", "-p", pid, "-s", "$directory/audio-probe.js", "-i")
                    .redirectErrorStream(true).start()
                val session = Session(role, process); sessions[role] = session
                val result = session.ready.poll(15, TimeUnit.SECONDS) ?: error("$target did not confirm attachment")
                check(result.getString("role") == role)
                val list = result.getJSONArray("capabilities")
                for (i in 0 until list.length()) session.capabilities.add(list.getString(i))
                log("$role confirmed: ${session.capabilities}")
            } catch (e: Exception) {
                sessions.remove(role)?.process?.destroy()
                log("UNAVAILABLE $role: $e")
            }
        }
        check(sessions.isNotEmpty()) { "No process attached. Root/SELinux/runtime error; see log. No restrictions were disabled." }
        // Recovery is allowed on a fresh session; experiments are blocked while any journal remains.
        thread(name = "audio-heartbeat", isDaemon = true) {
            while (!closing && foreground && generation == thisGeneration && sessions.isNotEmpty()) {
                sessions.values.forEach { runCatching { it.send(JSONObject().put("op", "ping")) } }
                Thread.sleep(2000)
            }
        }
    }
    fun apply(key: String, value: Int? = null) {
        check(foreground) { "App is not foreground; experiment refused" }
        check(!recoveryRequired || pending().isEmpty()) { "Restore the previous session before starting another experiment" }
        check(pending().none { it == "${role(key)}:$key" }) { "Restore this control before applying it again" }
        check(available(key)) { "Unsupported/unattached: $key" }
        val session = sessions.getValue(role(key))
        val result = session.request("prepare", key, value = value)
        val before = result.get("before")
        save(role(key), key, before)
        try { log("APPLIED $key ${session.request("apply", key, before)}") }
        catch (e: Exception) {
            if (e is ProbeFailure && !e.mayHaveChanged) clear(role(key), key)
            else runCatching { session.request("restore", key, before) }.onFailure { log("ROLLBACK REQUIRED $key: $it") }
            throw e
        }
    }
    fun restore(key: String) {
        val owner = role(key)
        val stored = prefs.getString("$owner:$key", null) ?: return
        val session = sessions[owner] ?: error("Reconnect $owner to restore $key")
        session.request("restore", key, JSONObject(stored).get("before"))
        clear(owner, key)
    }
    fun restoreAll() {
        val errors = mutableListOf<String>()
        for (id in pending()) runCatching { restore(id.substringAfter(':')) }.onFailure { errors.add("$id: $it") }
        check(errors.isEmpty()) { errors.joinToString("\n") }
    }
    fun close() {
        closing = true
        generation++
        try {
            runCatching { restoreAll() }.onFailure { log("RESTORE INCOMPLETE: $it") }
            sessions.values.forEach { session ->
                runCatching { session.request("stop") }.onFailure { log("Stop/restore not confirmed: $it") }
                session.process.destroy()
            }
            sessions.clear()
            log("Disconnected. Remaining rollback: ${pending()}")
        } finally { closing = false }
    }
    fun diagnostics() {
        for (service in listOf("audio", "car_service", "qg.audio_policy")) {
            runCatching { log("DUMP $service\n${root("dumpsys", service)}") }.onFailure { log("Dump failed: $it") }
        }
    }
}
