package com.abrar.raha.intent

import android.content.Context
import java.io.File

/** No plaintext asset fallback: tampered/missing ciphertext fails closed. */
class RahaProtectedAssets(private val context: Context) {
    fun read(name: String): ByteArray {
        require(name in setOf("model.onnx","manifest.json","schema.json","vocab.txt","contact-rules.json"))
        val key = RahaAssetKey.value()
        return try {
            context.assets.open("raha-intent/$name.sealed").buffered().use {
                RahaSealedCodec.open(it,key,name)
            }
        } finally { key.fill(0) }
    }
    fun text(name: String): String {
        val bytes=read(name)
        return try { bytes.toString(Charsets.UTF_8) } finally { bytes.fill(0) }
    }
    fun removeLegacyPlaintextCache() {
        val directory=File(context.noBackupFilesDir,"raha-intent")
        directory.listFiles()?.filter {
            it.isFile && (it.name.matches(Regex("[a-f0-9]{64}\\.onnx")) || it.name.matches(Regex("install-.*\\.tmp")))
        }?.forEach { check(it.delete()) { "Cannot remove legacy model cache" } }
    }
}
