package com.jszutkoski.launcher

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
}
