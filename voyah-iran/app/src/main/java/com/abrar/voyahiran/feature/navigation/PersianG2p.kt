package com.abrar.voyahiran.feature.navigation

import java.io.DataInputStream
import kotlin.math.exp
import kotlin.math.tanh

/** Bounded, offline GRU inference ported from PersianG2P (see resources/voi-g2p).
 * Used only after curated pronunciations and explicit diacritics. No Android/Python dependency. */
internal object PersianG2p {
    private const val H = 128
    private const val E = 64
    private val graphemes = " ًٌٍَُِآئابتثجحخدذرزسشصضطظعغفقلمنهوپچژکگی"
    private val phones = "?ACSZ^_abdefghijklmnopqrstuvxyz"
    private val cache = object : LinkedHashMap<String, String?>(256, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String?>) = size > 512
    }
    private val model: Model? by lazy {
        try {
            val stream = PersianG2p::class.java.getResourceAsStream("/voi-g2p/weights.bin")
                ?: error("Missing Persian G2P weights")
            DataInputStream(stream.buffered()).use { Model(it) }
        } catch (e: Exception) {
            System.err.println("VoyahG2p: model unavailable; using letter fallback: ${e.message}")
            null
        }
    }

    @Synchronized
    fun predict(word: String): String? {
        // No mixed Latin/numbers/punctuation, overlong input or caller-supplied diacritics.
        if (word.length !in 2..32 || word.any { it !in graphemes || it in 'ً'..'ْ' || it == ' ' }) return null
        if (cache.containsKey(word)) return cache[word]
        val result = model?.predict(word)
        cache[word] = result
        return result
    }

    private class Model(input: DataInputStream) {
        init { val magic = ByteArray(8); input.readFully(magic); require(String(magic, Charsets.US_ASCII) == "VIG2P001") }
        private fun read(input: DataInputStream, size: Int): FloatArray {
            require(input.readInt() == size)
            return FloatArray(size) { input.readFloat().also { require(it.isFinite()) } }
        }
        private val enc = read(input, (graphemes.length + 3) * E)
        private val dec = read(input, (phones.length + 4) * E)
        private val fc = read(input, (phones.length + 4) * H)
        private val bias = read(input, phones.length + 4)
        private val encoder = Cell(input)
        private val decoder = Cell(input)
        init { require(input.read() == -1) }
        private inner class Cell(input: DataInputStream) {
            val wi = read(input, 3 * H * E)
            val wh = read(input, 3 * H * H)
            val bi = read(input, 3 * H)
            val bh = read(input, 3 * H)
            fun step(embedding: FloatArray, offset: Int, hidden: FloatArray) {
                val a = FloatArray(3 * H)
                val b = FloatArray(3 * H)
                for (r in a.indices) {
                    var x = 0f; var y = 0f
                    for (j in 0 until E) x += embedding[offset + j] * wi[r * E + j]
                    for (j in 0 until H) y += hidden[j] * wh[r * H + j]
                    a[r] = x + bi[r]; b[r] = y + bh[r]
                }
                for (j in 0 until H) {
                    val reset = sigmoid(a[j] + b[j])
                    val update = sigmoid(a[H + j] + b[H + j])
                    val candidate = tanh(a[2 * H + j] + reset * b[2 * H + j])
                    hidden[j] = (1 - update) * candidate + update * hidden[j]
                }
            }
            private fun sigmoid(x: Float) = (1.0 / (1.0 + exp(-x.toDouble()))).toFloat()
        }
        fun predict(word: String): String? {
            val hidden = FloatArray(H)
            for (c in word) encoder.step(enc, (graphemes.indexOf(c) + 3) * E, hidden)
            encoder.step(enc, 2 * E, hidden) // encoder EOS
            var previous = 2 // decoder BOS
            val result = StringBuilder()
            repeat(20) {
                decoder.step(dec, previous * E, hidden)
                var best = 0; var score = Float.NEGATIVE_INFINITY
                for (p in bias.indices) {
                    var value = 0f
                    for (j in 0 until H) value += hidden[j] * fc[p * H + j]
                    value += bias[p]
                    if (value > score) { score = value; best = p }
                }
                if (best == 3) return result.toString().takeIf { it.isNotEmpty() }
                if (best < 4) return null
                result.append(when (val c = phones[best - 4]) {
                    'A' -> "a"; 'S' -> "sh"; 'Z' -> "zh"; 'C' -> "ch"
                    'x' -> "kh"; 'q' -> "gh"; 'u' -> "oo"
                    '?', '^', '_' -> ""; else -> c.toString()
                })
                previous = best
            }
            return null // incomplete decoding must not be presented as a complete road name
        }
    }
}
