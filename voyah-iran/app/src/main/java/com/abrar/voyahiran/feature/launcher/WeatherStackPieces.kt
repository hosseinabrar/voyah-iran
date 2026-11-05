package com.abrar.voyahiran.feature.launcher

/** Weather icons use the original drawable resources, shared across all three pages. */
internal object WeatherStackPieces {
    val paths=listOf("weather-stack-drop","clock-stack-dot-active","clock-stack-dot-dark","clock-stack-dot-light",
        "clock-stack-separator-dark","clock-stack-separator-light").map { "passenger/baked/$it.png" }
}
