package com.abrar.voyahiran.core.bridge

import java.io.DataInputStream
import java.security.MessageDigest

data class BridgeRequest(val id: String, val timeout: Long, val arguments: List<String>)

object BridgeProtocol {
    fun read(input: DataInputStream, token: String): BridgeRequest {
        require(input.readInt() == BridgePolicy.MAGIC) { "Protocol mismatch" }
        require(MessageDigest.isEqual(input.readUTF().toByteArray(), token.toByteArray())) { "Authentication failed" }
        val id = input.readUTF()
        require(id.matches(Regex("[a-f0-9-]{36}")))
        val timeout = input.readInt()
        require(timeout in 1..25)
        val count = input.readInt()
        require(count in 1..16)
        val arguments = List(count) { input.readUTF().also { require(it.length <= 256) } }
        return BridgeRequest(id, timeout.toLong(), arguments)
    }
}
