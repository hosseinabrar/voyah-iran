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
    @Test fun centralForecastIncludesCurrentHourAndHandlesMissingRainProbability() {
        val h=JSONObject().put("time",JSONArray(listOf(1000,4600,8200)))
            .put("temperature_2m",JSONArray(listOf(20,21,22)))
            .put("weather_code",JSONArray(listOf(2,3,0))).put("is_day",JSONArray(listOf(1,1,1)))
            .put("precipitation_probability",JSONArray().put(0).put(JSONObject.NULL).put(120))
        val result=LauncherWeather.parseHours(JSONObject().put("hourly",h),2_000_000,true)
        assertEquals(1_000_000,result.first().time)
        assertEquals(0,result.first().precipitation)
        assertNull(result[1].precipitation);assertNull(result[2].precipitation)
        assertEquals(4_600_000,LauncherWeather.parseHours(JSONObject().put("hourly",h),4_600_000,true).first().time)
    }
    @Test fun dailyForecastRejectsIncompleteRowsAndOldCachesRemainCompatible() {
        assertTrue(LauncherWeather.parseDays(JSONObject()).isEmpty())
        val d=JSONObject().put("time",JSONArray(listOf(1000,87400)))
            .put("temperature_2m_min",JSONArray(listOf(10,11))).put("temperature_2m_max",JSONArray(listOf(20,21)))
            .put("weather_code",JSONArray(listOf(2))).put("precipitation_probability_max",JSONArray(listOf(30)))
        val result=LauncherWeather.parseDays(JSONObject().put("daily",d))
        assertEquals(1,result.size);assertEquals(30,result.first().precipitation)
    }
}
