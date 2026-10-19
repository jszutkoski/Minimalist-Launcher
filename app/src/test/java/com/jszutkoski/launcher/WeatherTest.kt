package com.jszutkoski.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherTest {
    @Test
    fun waitsBetweenAttempts() {
        assertTrue(Weather.due(lastTry = 0L, now = 5_000L))
        assertFalse(Weather.due(lastTry = 5_000L, now = 5_000L + Weather.RETRY_MS - 1))
        assertTrue(Weather.due(lastTry = 5_000L, now = 5_000L + Weather.RETRY_MS))
    }

    @Test
    fun theSkyHasAGlyphForDayAndNight() {
        assertEquals(R.drawable.ic_weather_clear_day, Weather.icon(0, day = true))
        assertEquals(R.drawable.ic_weather_clear_night, Weather.icon(0, day = false))
        assertEquals(R.drawable.ic_weather_partly_night, Weather.icon(2, day = false))
        assertEquals(R.drawable.ic_weather_rain, Weather.icon(81, day = true))
        assertEquals(R.drawable.ic_weather_snow, Weather.icon(85, day = true))
        assertEquals(R.drawable.ic_weather_thunder, Weather.icon(95, day = false))
        // A code it doesn't know: just clouds.
        assertEquals(R.drawable.ic_weather_cloudy, Weather.icon(42, day = true))
    }
}
