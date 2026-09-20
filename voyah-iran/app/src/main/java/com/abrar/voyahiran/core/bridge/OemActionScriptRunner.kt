package com.abrar.voyahiran.core.bridge

import com.abrar.voyahiran.core.logging.AppLog
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Bounded, one-shot dispatch into an initialized OEM process. Never retries a timed-out action. */
internal object OemActionScriptRunner {
    @Synchronized fun run(packageName: String, source: String) {
        val runtime=File("/data/local/tmp/voyahiran-bridge/roa/frida-inject")
        val expected=File(runtime.path+".sha256").readText().trim()
        val hash=java.security.MessageDigest.getInstance("SHA-256")
        runtime.inputStream().use { input ->
            val buffer=ByteArray(65536)
            while(true) { val n=input.read(buffer); if(n<0)break; hash.update(buffer,0,n) }
        }
        check(hash.digest().joinToString("") { "%02x".format(it) }==expected) { "OEM action runtime hash mismatch" }
        val pid=File("/proc").listFiles().orEmpty().filter { it.name.all(Char::isDigit) }.singleOrNull {
            runCatching { File(it,"cmdline").readText().substringBefore('\u0000')==packageName }.getOrDefault(false)
        }?.name ?: error("OEM vehicle UI process unavailable")
        val script=File.createTempFile("raha-action-", ".js",runtime.parentFile)
        var process: Process?=null
        try {
            script.writeText(source)
            val child=ProcessBuilder(runtime.path,"-p",pid,"-s",script.path,"-i").redirectErrorStream(true).start()
            process=child
            AppLog.info("RahaOemAction", "OEM action attach started pid=$pid script=${script.name}")
            val done=CountDownLatch(1)
            val stopping=java.util.concurrent.atomic.AtomicBoolean(false)
            val failure=java.util.concurrent.atomic.AtomicReference<String?>(null)
            val ok=java.util.concurrent.atomic.AtomicBoolean(false)
            val reader=thread(name="raha-action-output",isDaemon=true) {
                try { child.inputStream.bufferedReader().useLines { lines -> lines.forEach { line ->
                    AppLog.info("RahaOemAction",line.take(1500))
                    if(line.contains("RAHA_ACTION_OK")) { ok.set(true); done.countDown() }
                    if(line.contains("RAHA_ACTION_ERROR")) { failure.set(line.take(1500)); done.countDown() }
                } } } catch(e:Exception) {
                    // Closing/killing frida can interrupt readLine. Never let this escape the
                    // worker: Android's uncaught-exception handler terminates the whole bridge.
                    if(!stopping.get() && !ok.get()) {
                        failure.compareAndSet(null,"Output read failed: ${e.javaClass.simpleName}: ${e.message}")
                        AppLog.error("RahaOemAction","OEM action output reader failed",e)
                    }
                } finally { done.countDown() }
            }
            try {
                val completed=done.await(8,TimeUnit.SECONDS)
                val exit=runCatching { child.exitValue().toString() }.getOrDefault("running")
                AppLog.info("RahaOemAction","OEM action attach completed=$completed confirmed=${ok.get()} process=$exit failure=${failure.get()}")
                check(completed && ok.get()) { failure.get() ?: "OEM action not confirmed: completed=$completed process=$exit" }
            } finally {
                stopping.set(true)
                child.destroy()
                if(!child.waitFor(1,TimeUnit.SECONDS)) child.destroyForcibly()
                reader.join(500)
            }
        } finally {
            process?.destroy()
            if(process?.waitFor(1,TimeUnit.SECONDS)==false) process.destroyForcibly()
            script.delete()
        }
    }
}
