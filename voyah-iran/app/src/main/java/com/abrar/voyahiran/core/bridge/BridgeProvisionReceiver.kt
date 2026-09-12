package com.abrar.voyahiran.core.bridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.AtomicFile
import java.io.File

/** Exported only to callers holding the platform INSTALL_PACKAGES permission. */
class BridgeProvisionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val token = intent.getStringExtra("token") ?: return
        if (!token.matches(Regex("[a-f0-9]{64}"))) return
        val file = AtomicFile(File(context.filesDir, "bridge.token"))
        val stream = file.startWrite()
        try {
            stream.write(token.toByteArray(Charsets.US_ASCII))
            file.finishWrite(stream)
            File(context.filesDir, "bridge.token").apply {
                setReadable(false, false); setWritable(false, false)
                setReadable(true, true); setWritable(true, true)
            }
            resultCode = 200
        } catch (error: Exception) {
            file.failWrite(stream)
            resultCode = 500
        }
    }
}
