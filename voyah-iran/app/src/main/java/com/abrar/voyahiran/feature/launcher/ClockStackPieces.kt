package com.abrar.voyahiran.feature.launcher

/** Tight, native-size PNG pieces. Generated offline by design/clock-stack/pack.py. */
internal object ClockStackPieces {
    data class Piece(val name:String,val x:Float,val y:Float,val width:Float,val height:Float)
    val pages=mapOf(
        ("dark" to 1) to listOf(Piece("clock-stack-battery-dark",43f,98f,30f,47f), Piece("clock-stack-fuel-dark",34f,258f,48f,47f), Piece("clock-stack-track-dark",30f,205f,346f,10f), Piece("clock-stack-track-dark",30f,365f,346f,10f)),
        ("dark" to 2) to listOf(Piece("clock-stack-clock-dark",131f,168f,22f,22f), Piece("clock-stack-separator-dark",30f,209f,348f,2f), Piece("clock-stack-divider-dark",206f,263f,2f,65f)),
        ("dark" to 10) to listOf(Piece("clock-stack-dot-active",400f,166f,8f,8f), Piece("clock-stack-dot-dark",400f,185f,8f,8f), Piece("clock-stack-dot-dark",400f,204f,8f,8f)),
        ("dark" to 11) to listOf(Piece("clock-stack-dot-dark",400f,166f,8f,8f), Piece("clock-stack-dot-active",400f,185f,8f,8f), Piece("clock-stack-dot-dark",400f,204f,8f,8f)),
        ("dark" to 12) to listOf(Piece("clock-stack-dot-dark",400f,166f,8f,8f), Piece("clock-stack-dot-dark",400f,185f,8f,8f), Piece("clock-stack-dot-active",400f,204f,8f,8f)),
        ("light" to 1) to listOf(Piece("clock-stack-battery-light",43f,98f,30f,47f), Piece("clock-stack-fuel-light",34f,258f,48f,47f), Piece("clock-stack-track-light",30f,205f,346f,10f), Piece("clock-stack-track-light",30f,365f,346f,10f)),
        ("light" to 2) to listOf(Piece("clock-stack-clock-light",131f,168f,22f,22f), Piece("clock-stack-separator-light",30f,209f,348f,2f), Piece("clock-stack-divider-light",206f,263f,2f,65f)),
        ("light" to 10) to listOf(Piece("clock-stack-dot-active",400f,166f,8f,8f), Piece("clock-stack-dot-light",400f,185f,8f,8f), Piece("clock-stack-dot-light",400f,204f,8f,8f)),
        ("light" to 11) to listOf(Piece("clock-stack-dot-light",400f,166f,8f,8f), Piece("clock-stack-dot-active",400f,185f,8f,8f), Piece("clock-stack-dot-light",400f,204f,8f,8f)),
        ("light" to 12) to listOf(Piece("clock-stack-dot-light",400f,166f,8f,8f), Piece("clock-stack-dot-light",400f,185f,8f,8f), Piece("clock-stack-dot-active",400f,204f,8f,8f)),
    )
    val paths=pages.values.flatten().map { "passenger/baked/${it.name}.png" }.distinct()
}
