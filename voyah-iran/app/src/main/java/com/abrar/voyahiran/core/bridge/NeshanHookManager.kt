package com.abrar.voyahiran.core.bridge

import android.os.SystemClock
import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.core.shell.CommandResult
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import com.abrar.voyahiran.feature.navigation.ClusterLatinText
import org.json.JSONObject
import com.abrar.voyahiran.feature.navigation.NeshanConfig
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Private injector pipe; no exported receiver/socket, temporary ad UI suppression without preference writes. */
internal class NeshanHookManager {
    private val directory = File("/data/local/tmp/voyahiran-bridge/neshan")
    private val executable = File(directory, "frida-inject")
    private val script = File(directory, "direct-navigation.js")
    private val flag = File(directory, "enabled")
    private val runner = ProcessCommandRunner(useBridge = false)
    private val stateLock = Any()
    private data class RecenterReply(val process: Process, val signal: java.util.concurrent.CountDownLatch = java.util.concurrent.CountDownLatch(1), @Volatile var result: String? = null)
    private val recenterReplies = java.util.concurrent.ConcurrentHashMap<String, RecenterReply>()
    @Volatile private var child: Process? = null
    @Volatile private var ready = false
    @Volatile private var failure: String? = null
    private var pid = ""
    private var routeEnabled = false
    private var autoUntil = 0L
    private var retryAt = 0L
    private var filesReady = false
    private var injectorPrefix: List<String>? = null
    private val configFile = File(directory, NeshanConfig.FILE_NAME)
    private val incomingConfig = File("/data/user/0/com.abrar.voyahiran/files/" + NeshanConfig.FILE_NAME)
    private var catalog: NeshanConfig.Catalog? = null
    private var configStamp = ""
    private var configCheckAt = 0L
    private var apkIdentity = ""
    private var apkHash = ""
    @Volatile private var sample: Sample? = null
    private var showing = false
    private var pidCheckAt = 0L
    private var cachedPid = ""
    private var pingAt = 0L
    private var translatedSource: String? = null
    private var translatedRoad = ""
    private data class Sample(val road: String, val meters: Int, val icon: Int, val at: Long, val stepKey: String?)
    private fun now() = SystemClock.elapsedRealtime()

    fun start() {
        // Only our own copied injector, never the camera injector or another tool's Frida.
        File("/proc").listFiles()?.filter { it.name.all(Char::isDigit) }?.forEach {
            if (runCatching { File(it, "cmdline").readText().substringBefore('\u0000') == executable.path }.getOrDefault(false))
                runner.run(listOf("/system/bin/kill", "-TERM", it.name), 2)
        }
        thread(name = "voyah-neshan-watch", isDaemon = true) {
            while (true) {
                val start = now()
                try { synchronized(this) { tick() } }
                catch (e: Exception) { failure = e.message; stop(); retryAt = now() + 5000; AppLog.error("NeshanDirect", "watch failed", e) }
                Thread.sleep((200 - (now() - start)).coerceAtLeast(20))
            }
        }
    }
    @Synchronized fun command(action: String, display: Int? = null): CommandResult { return try {
        when (action) {
            "enable" -> {
                prepareFiles()
                File(directory, "enabled.new").writeText("1\n")
                check(File(directory, "enabled.new").renameTo(flag))
                retryAt = 0; failure = null
            }
            "disable" -> { check(!flag.exists() || flag.delete()); clearDisplay(); if(now() >= autoUntil) stop(); failure = null }
            "auto-state" -> {
                check(BridgeSubscriptionSession.vipActive) { "VIP session required" }
                // Lease requests route data only, never OEM guidance display or saved settings.
                autoUntil = now() + 20_000
                val value = sample?.takeIf { routeEnabled && ready && now() - it.at in 0..5000 && it.stepKey != null }
                val data = JSONObject().put("valid", value != null)
                    .put("reason", when {
                        !routeEnabled -> "route_disabled"
                        !ready -> "hook_not_ready"
                        sample == null -> "sample_missing"
                        sample?.stepKey == null -> "step_missing"
                        value == null -> "sample_stale"
                        else -> "valid"
                    })
                if (value != null) data.put("step", value.stepKey).put("meters", value.meters).put("sampledAt", value.at)
                    .put("speedKmh", LauncherVehicleBridge.autoMapSpeed() ?: JSONObject.NULL)
                return CommandResult(0, "NESHAN_AUTO=" + data.toString())
            }
            "status" -> Unit
            "recenter" -> {
                check(BridgeSubscriptionSession.vipActive && display != null && display in 2..10000)
                if (!ready || child?.isAlive != true) return CommandResult(75, "Neshan hook not ready")
                val process = child ?: return CommandResult(75, "hook_missing")
                val token = java.util.UUID.randomUUID().toString()
                val reply = RecenterReply(process)
                recenterReplies[token] = reply
                try {
                    process.outputStream.apply { write("recenter $display $token ${System.currentTimeMillis()+10000}\n".toByteArray()); flush() }
                    reply.signal.await(1500, TimeUnit.MILLISECONDS)
                    val result = reply.result ?: "ack_timeout"
                    AppLog.info("NeshanRecenter", "request=$token display=$display result=$result")
                    return CommandResult(if(result == "applied" || result == "accepted") 0 else 75, "RECENTER_RESULT=$result request=$token")
                } finally { recenterReplies.remove(token) }
            }
            else -> error("Unsupported Neshan command")
        }
        CommandResult(0, "NESHAN_DIRECT=" + when {
            !flag.exists() -> "DISABLED"
            failure != null -> "ERROR: $failure"
            ready && child?.isAlive == true -> "ATTACHED"
            else -> "WAITING"
        })
    } catch (e: Exception) { CommandResult(1, "Neshan direct setup failed: ${e.message}") } }
    private fun prepareFiles() {
        if (filesReady) return
        val source = File("/data/local/tmp/voyahiran-bridge/roa/frida-inject")
        val expected = File(source.path + ".sha256").readText().trim()
        val digest = MessageDigest.getInstance("SHA-256")
        source.inputStream().use { stream ->
            val buffer = ByteArray(65536)
            while (true) { val n = stream.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        }
        check(expected == digest.digest().joinToString("") { "%02x".format(it) }) { "Injector hash mismatch; reinstall" }
        check(directory.isDirectory || directory.mkdirs())
        source.copyTo(File(directory, "frida-inject.new"), overwrite = true)
        check(File(directory, "frida-inject.new").renameTo(executable))
        val chmod = runner.run(listOf("/system/bin/chmod", "700", directory.path, executable.path), 3)
        check(chmod.exitCode == 0 && !chmod.timedOut) { "Cannot secure Neshan runtime" }
        filesReady = true
    }
    private fun injectorCommand(): List<String> {
        injectorPrefix?.let { return it }
        val context = File("/proc/self/attr/current").readText().trim().trimEnd('\u0000')
        if (android.os.Build.VERSION.SDK_INT != 28 || context != "u:r:shell:s0")
            return emptyList<String>().also { injectorPrefix = it }
        val su = listOf("/system/xbin/su", "/system/bin/su").firstOrNull { File(it).canExecute() }
            ?: error("Android 9 injector needs OEM su; no executable found")
        val prefix = listOf(su, "0")
        val probe = runner.run(prefix + listOf("/system/bin/id", "-Z"), 3)
        val domain = probe.output.trim()
        AppLog.info("NeshanDirect", "injector context=$context su=$su probeExit=${probe.exitCode} targetContext=$domain")
        check(probe.exitCode == 0 && !probe.timedOut && domain == "u:r:su:s0") {
            "OEM su did not provide the required injector context: $domain"
        }
        return prefix.also { injectorPrefix = it }
    }

    private fun targetPid(): String {
        val result = runner.run(listOf("/system/bin/pidof", PACKAGE), 2)
        return result.output.trim().split(Regex("\\s+")).filter { it.matches(Regex("[1-9][0-9]*")) }
            .singleOrNull { p -> runCatching { File("/proc/$p/cmdline").readText().substringBefore('\u0000') == PACKAGE }.getOrDefault(false) }.orEmpty()
    }
    private fun refreshConfig() {
        if (now() < configCheckAt) return
        configCheckAt = now() + 5000
        if (catalog == null && configFile.isFile) {
            catalog = runCatching { NeshanConfig.verify(NeshanConfig.read(configFile)) }.getOrNull()
        }
        if (!incomingConfig.isFile) return
        val stamp = "${incomingConfig.lastModified()}:${incomingConfig.length()}"
        if (stamp == configStamp) return
        configStamp = stamp
        try {
            val text = NeshanConfig.read(incomingConfig)
            val next = NeshanConfig.verify(text)
            next.acceptAfter(catalog)
            if (next.digest != catalog?.digest) {
                NeshanConfig.save(configFile, text)
                stop(); retryAt = 0
                catalog = next
                AppLog.info("NeshanConfig", "bridge accepted revision=${next.revision} profiles=${next.profiles.size}")
            }
        } catch (e: Exception) { AppLog.info("NeshanConfig", "bridge rejected config, keeping cache: ${e.message}") }
    }
    private fun chooseProfile(): NeshanConfig.Profile? {
        val current = catalog ?: return null
        val paths = runner.run(listOf("/system/bin/pm", "path", "--user", "0", PACKAGE), 5)
        check(paths.exitCode == 0 && !paths.timedOut) { "Cannot resolve Neshan APK" }
        val path = paths.output.lineSequence().map { it.trim() }.singleOrNull { it.endsWith("/base.apk") }
            ?.removePrefix("package:") ?: error("Cannot resolve Neshan base APK")
        require(path.startsWith("/data/app/") || path.startsWith("/system/"))
        val apk = File(path)
        val identity = "${apk.path}:${apk.length()}:${apk.lastModified()}"
        if (identity != apkIdentity) { apkHash = NeshanConfig.fileHash(apk); apkIdentity = identity }
        return current.profiles.singleOrNull { it.apkSha256 == apkHash }
    }
    private fun tick() {
        refreshConfig()
        if (!BridgeSubscriptionSession.vipActive) { autoUntil = 0; stop(); return }
        val wantsRoute = flag.exists() || now() < autoUntil
        if (child != null && routeEnabled != wantsRoute) { stop(); retryAt = 0 }
        if (now() >= pidCheckAt) {
            cachedPid = targetPid(); pidCheckAt = now() + 1000
        }
        val target = cachedPid
        if (child != null && (!child!!.isAlive || target != pid)) { stop(); retryAt = now() + 5000 }
        if (target.isEmpty()) return
        if (child == null && now() >= retryAt) {
            val profile = chooseProfile()
            if (profile == null) {
                failure = "Waiting for signed config matching installed Neshan"
                AppLog.changed("NeshanConfig", failure!!)
                retryAt = now() + 5000
                return
            }
            prepareFiles(); pid = target; ready = false; failure = null; routeEnabled = wantsRoute
            NeshanConfig.save(script, "const NESHAN_ROUTE_ENABLED = $routeEnabled;\nconst NESHAN_PROFILE = " + profile.json.toString() + ";\n" + NeshanRecenterScript.source + "\n" + NeshanHookScript.source)
            val process = ProcessBuilder(injectorCommand() + listOf(executable.path, "-p", target, "-s", script.path, "-i")).redirectErrorStream(true).start()
            child = process
            thread(name = "voyah-neshan-output", isDaemon = true) {
                try { process.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines -> lines.forEach { line ->
                    accept(process, line)
                } } } catch (e: Exception) { AppLog.info("NeshanDirect", "pipe closed ${e.message}") }
            }
            val deadline = now() + 10000
            while (process.isAlive && !ready && failure == null && now() < deadline) Thread.sleep(50)
            if (!ready || failure != null) { val error = failure ?: "No hook-ready acknowledgement"; stop(); retryAt = now() + 5000; failure = error; return }
            AppLog.info("NeshanDirect", "attached pid=$pid, direct route model; background updates require vehicle test")
        }
        if (now() >= pingAt) {
            child?.let { it.outputStream.write("ping\n".toByteArray()); it.outputStream.flush() }
            pingAt = now() + 1000
        }
        val value = sample?.takeIf { routeEnabled && ready && now() - it.at in 0..5000 }
        if (value != null && flag.exists()) {
            if (translatedSource != value.road) {
                translatedRoad = ClusterLatinText.convert(value.road)
                translatedSource = value.road
            }
            val road = translatedRoad
            AppLog.changed("NeshanDirectText", "unicode=${value.road} latin=$road")
            val text = road.ifBlank { "-" }
            OemClusterNavigation.command(listOf("cluster-navigation", "show", text.take(180), value.icon.toString(), value.meters.toString()))
            showing = true
        } else clearDisplay()
    }
    private fun accept(process: Process, line: String) = synchronized(stateLock) {
        if (child !== process) return@synchronized
        when {
            line.startsWith("VI_NESHAN_RECENTER_ERROR ") ||
            line.startsWith("VI_NESHAN_RECENTER_STACK ") ||
            line.startsWith("VI_NESHAN_RECENTER_PHASE ") ||
            line.startsWith("VI_NESHAN_RECENTER_TIMING ") ||
            line.startsWith("VI_NESHAN_RECENTER_BUTTON ") -> AppLog.info("NeshanRecenter", line.take(2000))
            line.startsWith("VI_NESHAN_RECENTER_RESULT ") -> {
                val parts = line.split(' ', limit=3)
                if(parts.size==3) recenterReplies[parts[1]]?.takeIf { it.process === process }?.let {
                    it.result=parts[2];it.signal.countDown()
                }
                AppLog.info("NeshanRecenter",line.take(700))
            }
            line.startsWith("VI_NESHAN_READY ") -> { ready = true; AppLog.info("NeshanDirect", line) }
            line == "VI_NESHAN_CLEAR" -> sample = null
            line == "VI_NESHAN_DISABLED" -> { ready = false; sample = null }
            line.startsWith("VI_NESHAN_ERROR") -> { failure = line.take(500); ready = false; sample = null; AppLog.info("NeshanDirect", line.take(500)) }
            line.startsWith("VI_NESHAN_STATE_ERROR ") -> AppLog.info("NeshanDirectState", line.take(1200))
            line.startsWith("VI_NESHAN_STATUS ") -> AppLog.info("NeshanDirectStatus", line.removePrefix("VI_NESHAN_STATUS "))
            line.startsWith("VI_NESHAN_SAMPLE_ERROR ") -> { sample = null; AppLog.changed("NeshanDirect", line.take(500)) }
            line.startsWith("VI_NESHAN_DATA ") -> try {
                val data = JSONObject(line.removePrefix("VI_NESHAN_DATA "))
                val age = data.getLong("ageMs")
                val meters = (data.opt("meters") as? Number)?.toDouble()?.takeIf { it.isFinite() && it in 0.0..1_000_000.0 }?.toInt() ?: -1
                val rawRoad = (data.opt("road") as? String).orEmpty().trim().take(200)
                require(age in 0..5000)
                val modifier = data.optString("modifier", "UNKNOWN"); val type = data.optString("type", "UNKNOWN")
                val road = NeshanManeuver.roadLabel(type, rawRoad)
                val turnIndex = (data.opt("turnIndex") as? Number)?.toInt()
                val icon = NeshanManeuver.availableIcon(type, modifier, turnIndex)
                val step = (data.opt("step") as? Number)?.toInt()?.takeIf { it >= 0 }
                val epoch = (data.opt("epoch") as? Number)?.toLong()
                val key = if (step == null || epoch == null) null else "$pid:$epoch:$step:$type:$modifier:$turnIndex"
                sample = Sample(road, meters, icon, now() - age, key)
                AppLog.changed("NeshanDirectData", "step=${data.optInt("step")} type=$type modifier=$modifier meters=$meters road=$road turnIndex=$turnIndex clusterIcon=$icon")
            } catch (e: Exception) { sample = null; AppLog.changed("NeshanDirect", "invalid sample: ${e.message}") }
            else -> AppLog.changed("NeshanDirectRuntime", line.take(500))
        }
    }
    private fun clearDisplay() {
        if (showing) { OemClusterNavigation.command(listOf("cluster-navigation", "clear")); showing = false }
    }
    private fun stop() {
        val process = child
        synchronized(stateLock) { child = null; ready = false; sample = null; pid = "" }
        clearDisplay()
        if (process != null) {
            runCatching { process.outputStream.write("disable\n".toByteArray()); process.outputStream.flush() }
            process.destroy()
            if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly()
        }
    }
    companion object { private const val PACKAGE = "org.rajman.neshan.traffic.tehran.navigator" }
}
