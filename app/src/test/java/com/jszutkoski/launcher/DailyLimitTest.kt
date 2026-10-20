package com.jszutkoski.launcher

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Each app's daily limit, as kept in the settings. */
@RunWith(RobolectricTestRunner::class)
class DailyLimitTest {
    private val prefs = Prefs(RuntimeEnvironment.getApplication())

    @Test
    fun eachAppKeepsItsOwnLimit() {
        prefs.dailyLimits = mapOf("com.instagram.android/.Main 0" to 30, "com.google.android.youtube/.Main 0" to 60)
        assertEquals(30, prefs.dailyLimits["com.instagram.android/.Main 0"])
        assertEquals(60, prefs.dailyLimits["com.google.android.youtube/.Main 0"])
    }

    @Test
    fun removingOneLeavesTheOthers() {
        prefs.dailyLimits = mapOf("a" to 15, "b" to 45)
        prefs.dailyLimits = prefs.dailyLimits - "a"
        assertEquals(mapOf("b" to 45), prefs.dailyLimits)
    }
}
