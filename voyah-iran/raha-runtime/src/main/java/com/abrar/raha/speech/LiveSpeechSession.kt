package com.abrar.raha.speech

interface LiveSpeechSession : AutoCloseable {
    fun accept(frame: FloatArray)
    fun finish(): String
}
