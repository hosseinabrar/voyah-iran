package com.abrar.voyahiran.core.bridge

import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import java.io.File
import java.security.MessageDigest
import kotlin.concurrent.thread

/** Process-local Java compatibility for Apple Music; no system-wide root hiding. */
internal class AppleMusicHookManager {
    private val runner=ProcessCommandRunner(useBridge=false)
    private val directory=File("/data/local/tmp/voyahiran-bridge/apple-music")
    private val executable=File(directory,"frida-inject")
    private var child:Process?=null
    private var target=""
    private var attempted=""
    private var attempts=0
    private var retryAt=0L
    @Volatile private var ready=false
    private var prepared=false
    private fun hash(file:File):String {
        val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input->val buffer=ByteArray(65536);while(true){val n=input.read(buffer);if(n<0)break;digest.update(buffer,0,n)} }
        return digest.digest().joinToString(""){"%02x".format(it)}
    }
    fun start() {
        // Clean up only this manager's injector left behind by a bridge restart.
        File("/proc").listFiles()?.filter{it.name.all(Char::isDigit)}?.forEach {
            if(runCatching{File(it,"cmdline").readText().substringBefore('\u0000')==executable.path}.getOrDefault(false))
                runner.run(listOf("/system/bin/kill","-TERM",it.name),2)
        }
        thread(name="voyah-apple-music-watch",isDaemon=true) {
            while(true) {
                try {tick()} catch(e:Exception){AppLog.error("AppleMusicCompat","hook unavailable",e)}
                Thread.sleep(1000)
            }
        }
    }
    private fun tick() {
        val pid=runner.run(listOf("/system/bin/pidof","com.apple.android.music"),2).output.trim().split(' ').firstOrNull().orEmpty()
        if(!pid.matches(Regex("[0-9]+"))) {child?.destroy();child=null;target="";attempted="";attempts=0;return}
        val now=android.os.SystemClock.elapsedRealtime()
        if(pid!=target){child?.destroy();child=null;target=pid;attempts=0;attempted="";retryAt=0;ready=false}
        if(pid==attempted && (ready && child?.isAlive==true || attempts>=3 || now<retryAt))return
        child?.destroy();child=null
        attempts++;attempted=pid;retryAt=now+10000;ready=false
        if(!prepared) {
            val source=File("/data/local/tmp/voyahiran-bridge/roa/frida-inject")
            check(hash(source)==File(source.path+".sha256").readText().trim()){"Injector checksum mismatch"}
            check(directory.isDirectory || directory.mkdirs())
            source.copyTo(executable,overwrite=true)
            File(directory,"root-compat.js").writeText(AppleMusicHookScript.source)
            check(runner.run(listOf("/system/bin/chmod","700",directory.path,executable.path),3).exitCode==0)
            prepared=true
        }
        val context=File("/proc/self/attr/current").readText().trim().trimEnd('\u0000')
        val prefix=if(android.os.Build.VERSION.SDK_INT==28 && context=="u:r:shell:s0") {
            val su=listOf("/system/xbin/su","/system/bin/su").firstOrNull{File(it).canExecute()} ?: error("OEM su missing")
            check(runner.run(listOf(su,"0","/system/bin/id","-Z"),3).output.trim()=="u:r:su:s0")
            listOf(su,"0")
        } else emptyList()
        val process=ProcessBuilder(prefix+listOf(executable.path,"-p",pid,"-s",File(directory,"root-compat.js").path,"-i")).redirectErrorStream(true).start()
        child=process;attempted=pid
        thread(name="voyah-apple-music-output",isDaemon=true) {
            runCatching { process.inputStream.bufferedReader().useLines { lines->lines.forEach { if(it.contains("VI_APPLE_ROOT_READY") && child===process)ready=true;AppLog.info("AppleMusicCompat",it.take(1200)) } } }
        }
    }
}
