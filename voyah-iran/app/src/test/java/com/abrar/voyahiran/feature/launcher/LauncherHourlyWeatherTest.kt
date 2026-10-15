package com.abrar.voyahiran.feature.launcher

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LauncherHourlyWeatherTest {
    @Test fun nextFiveHoursExcludeTheCurrentHourAndCrossMidnight() {
        val start=1_700_000_000L
        val hourly=JSONObject().put("time",JSONArray((0..8).map {start+it*3600}))
            .put("temperature_2m",JSONArray((0..8).map {20-it}))
            .put("weather_code",JSONArray(List(9){2})).put("is_day",JSONArray(List(9){0}))
        val parsed=LauncherWeather.parseHours(JSONObject().put("hourly",hourly),(start+1800)*1000)
        assertEquals(5,parsed.size)
        assertEquals((start+3600)*1000,parsed.first().time)
        assertEquals(19.0,parsed.first().temperature,0.001)
        assertFalse(parsed.first().day)
        assertEquals((start+5*3600)*1000,parsed.last().time)
    }
    @Test fun oldSavedResponsesWithoutHourlyDataAreSafe() {
        assertTrue(LauncherWeather.parseHours(JSONObject(),0).isEmpty())
        assertTrue(LauncherWeather.parseHours(JSONObject().put("hourly",JSONObject().put("time",JSONArray().put(1))),0).isEmpty())
    }
}
