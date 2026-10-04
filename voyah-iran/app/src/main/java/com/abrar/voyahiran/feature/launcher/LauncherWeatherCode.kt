package com.abrar.voyahiran.feature.launcher

import com.abrar.voyahiran.R

internal object LauncherWeatherCode {
    fun description(code: Int): String = when(code) {
        0 -> "Clear"; 1 -> "Mostly clear"; 2 -> "Partly cloudy"; 3 -> "Overcast"
        45 -> "Fog"; 48 -> "Freezing fog"
        51 -> "Light drizzle"; 53 -> "Drizzle"; 55 -> "Heavy drizzle"
        56,57 -> "Freezing drizzle"
        61 -> "Light rain"; 63 -> "Rain"; 65 -> "Heavy rain"
        66,67 -> "Freezing rain"
        71 -> "Light snow"; 73 -> "Snow"; 75 -> "Heavy snow"; 77 -> "Snow grains"
        80 -> "Light showers"; 81 -> "Showers"; 82 -> "Heavy showers"
        85 -> "Snow showers"; 86 -> "Heavy snow showers"
        95 -> "Thunderstorm"; 97 -> "Heavy thunderstorm"; 96,99 -> "Thunder and hail"
        else -> "Unknown"
    }
    fun icon(code: Int): Int = when(code) {
        0 -> R.drawable.launcher_weather_clearly
        1 -> R.drawable.launcher_weather_mostly_sunny
        2 -> R.drawable.launcher_weather_cloudy
        3 -> R.drawable.launcher_weather_overcast
        45 -> R.drawable.launcher_weather_foggy
        48 -> R.drawable.launcher_weather_ice_foggy
        51,53,55,61 -> R.drawable.launcher_weather_light_rain
        56,57,66,67 -> R.drawable.launcher_weather_ice_rain
        63 -> R.drawable.launcher_weather_moderate_rain
        65 -> R.drawable.launcher_weather_heavy_rain
        71 -> R.drawable.launcher_weather_light_snow
        73 -> R.drawable.launcher_weather_moderate_snow
        75 -> R.drawable.launcher_weather_heavy_snow
        77 -> R.drawable.launcher_weather_needle_ice
        80 -> R.drawable.launcher_weather_light_shower
        81 -> R.drawable.launcher_weather_shower
        82 -> R.drawable.launcher_weather_heavy_shower
        85 -> R.drawable.launcher_weather_light_snow_shower
        86 -> R.drawable.launcher_weather_snow_flurry
        95,97 -> R.drawable.launcher_weather_thunderstorm
        96,99 -> R.drawable.launcher_weather_thunder_shower_with_hail
        else -> R.drawable.launcher_weather_unknown
    }
}
