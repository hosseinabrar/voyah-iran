package com.abrar.raha

import java.util.zip.GZIPInputStream

/** Loaded only on the IO worker while importing new contacts; never during audio decoding. */
class FinglishContactNames(private val open: (String)->java.io.InputStream) {
    // AAPT unpacks .gz assets and strips the suffix in the installed APK.
    private fun table(name:String):java.io.InputStream = try {
        GZIPInputStream(open("$name.gz"))
    } catch (_: java.io.FileNotFoundException) { open(name) }
    private val dictionary = table("names.tsv")
        .bufferedReader().useLines { lines -> lines.map { it.split('\t', limit=2) }
            .filter { it.size == 2 }.associate { it[0] to it[1] } }
    private val common by lazy {
        open("f2p-dict.txt").bufferedReader().useLines { lines ->
            lines.filter(String::isNotBlank).map { it.trim().split(' ',limit=2) }.filter { it.size==2 }.associate { it[0] to it[1] }
        }
    }
    private val frequencies by lazy {
        java.util.TreeMap<String,Int>().apply {
            table("frequency.tsv").bufferedReader().useLines { lines ->
                lines.forEach { val row=it.split('\t'); put(row[0],row[1].toInt()) }
            }
        }
    }
    private val converters by lazy {
        listOf("beginning","middle","ending").map { position ->
            open("f2p-$position.txt").bufferedReader().useLines { lines ->
                lines.filter(String::isNotBlank).map { it.trim().split(Regex("\\s+")) }
                    .associate { row -> row[0] to row.drop(1).map { if(it=="nothing") "" else it } }
            }
        }
    }
    private fun key(text: String) = text.lowercase(java.util.Locale.ROOT).replace(Regex("[^a-z]"), "")
    fun convert(name: String): String {
        val words = name.trim().split(Regex("[\\s_\\-]+"))
        val result = mutableListOf<String>()
        var index = 0
        while (index < words.size) {
            if (!words[index].matches(Regex("[A-Za-z']+"))) { result += words[index++]; continue }
            var consumed = false
            for (length in minOf(3, words.size-index) downTo 1) {
                val part = words.subList(index,index+length)
                if (part.any { !it.matches(Regex("[A-Za-z']+")) }) continue
                val value = dictionary[key(part.joinToString(""))] ?: continue
                result += value; index += length; consumed = true; break
            }
            if (!consumed) result += offline(words[index++])
        }
        return RahaContacts.normalize(result.joinToString(" ")).take(120)
    }
    /** Frequency-guided traversal, capped at 8,000 nodes per unknown word; no exponential product. */
    private fun offline(input:String):String {
        val word=input.lowercase(java.util.Locale.ROOT)
        common[word]?.let { return it }
        if(word.length>32) return fallback(input)
        var visits=0
        var best:String?=null
        var bestFrequency=0
        val pairs=mapOf("aa" to "A","ee" to "i","oo" to "u","ou" to "u","kh" to "kh","gh" to "gh","sh" to "sh","ch" to "ch","zh" to "zh","ck" to "ck","ei" to "ei")
        fun visit(offset:Int,prefix:String) {
            if(++visits>8000) return
            if(offset==word.length) {
                val score=frequencies[prefix]?:0
                if(score>bestFrequency) { bestFrequency=score; best=prefix }
                return
            }
            val options=mutableListOf<Pair<String,Int>>()
            if(offset+2<=word.length) pairs[word.substring(offset,offset+2)]?.let { options += it to 2 }
            if(offset+3<=word.length && word.substring(offset,offset+3)=="kha") options.add(0,"kha" to 3)
            if(offset+1<word.length && word[offset]==word[offset+1]) options += word[offset].toString() to 2
            options += word[offset].toString() to 1
            for((token,length) in options) {
                val table=converters[if(offset==0) 0 else if(offset+length==word.length) 2 else 1]
                for(value in table[token].orEmpty()) {
                    val next=prefix+value
                    if(frequencies.ceilingKey(next)?.startsWith(next)==true) visit(offset+length,next)
                }
            }
        }
        visit(0,"")
        return best ?: fallback(input)
    }
    // Deterministic bounded fallback for unknown Latin labels. Always editable by the owner.
    private fun fallback(input: String): String {
        val s = input.lowercase(java.util.Locale.ROOT)
        val digraphs = mapOf("kh" to "خ", "sh" to "ش", "ch" to "چ", "zh" to "ژ", "gh" to "غ", "aa" to "ا", "oo" to "و", "ou" to "و", "ee" to "ی")
        val consonants = mapOf('b' to "ب", 'p' to "پ", 't' to "ت", 'j' to "ج", 'd' to "د", 'r' to "ر", 'z' to "ز", 's' to "س", 'f' to "ف", 'q' to "ق", 'k' to "ک", 'g' to "گ", 'l' to "ل", 'm' to "م", 'n' to "ن", 'v' to "و", 'w' to "و", 'h' to "ه", 'y' to "ی", 'i' to "ی", 'u' to "و", 'x' to "کس", 'c' to "ک")
        return buildString {
            var i=0
            while (i<s.length) {
                val pair=if(i+1<s.length) digraphs[s.substring(i,i+2)] else null
                if(pair!=null) { append(pair); i+=2; continue }
                val c=s[i]
                append(consonants[c] ?: when(c) { 'a' -> if(i==0 || i==s.lastIndex) "ا" else ""; 'e' -> if(i==0) "ا" else if(i==s.lastIndex) "ه" else ""; 'o' -> if(i==0) "ا" else if(i==s.lastIndex) "و" else ""; else -> "" })
                i++
            }
        }.ifBlank { input }
    }
}
