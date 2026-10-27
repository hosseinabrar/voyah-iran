package com.abrar.raha.intent

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class RahaSealedCodecTest {
    private val key=ByteArray(32){it.toByte()}
    private val plain=ByteArray(150000){(it%251).toByte()}
    private fun seal():ByteArray = ByteArrayOutputStream().also {
        RahaSealedCodec.seal(ByteArrayInputStream(plain),it,plain.size,key,"model.onnx")
    }.toByteArray()
    private fun reject(bytes:ByteArray,k:ByteArray=key,name:String="model.onnx") {
        try { RahaSealedCodec.open(ByteArrayInputStream(bytes),k,name);fail("Accepted invalid asset") }
        catch(e:java.io.IOException) {} catch(e:java.security.GeneralSecurityException) {}
    }
    @Test fun roundTripAndRandomNonces() {
        val a=seal();val b=seal()
        assertFalse(a.contentEquals(b))
        assertArrayEquals(plain,RahaSealedCodec.open(ByteArrayInputStream(a),key,"model.onnx"))
    }
    @Test fun tamperingAndWrongIdentityFailClosed() {
        val a=seal()
        reject(a,ByteArray(32))
        reject(a,name="schema.json")
        reject(a.copyOf(a.size-1));reject(a+byteArrayOf(1))
        for(index in listOf(0,7,9,30,65580,a.lastIndex)) {
            val bad=a.copyOf();bad[index]=(bad[index].toInt() xor 1).toByte();reject(bad)
        }
    }
}
