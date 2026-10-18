package com.jszutkoski.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class StandbyBrightnessTest {
    @Test
    fun byTheTimeOfDayItIsBrightByDayAndFaintInTheSmallHours() {
        val levels = (0..23).map { StandbyBrightness.BY_HOUR.level(it) }
        assertEquals(0.1f, levels[3])
        assertEquals(0.3f, levels[7])
        assertEquals(0.6f, levels[12])
        assertEquals(0.3f, levels[21])
        assertEquals(0.1f, levels[23])
    }

    @Test
    fun aFixedLevelIsTheSameAllDay() {
        assertEquals(setOf(0.5f), (0..23).map { StandbyBrightness.HALF.level(it) }.toSet())
        assertEquals(50, StandbyBrightness.HALF.percent)
        assertEquals(null, StandbyBrightness.BY_HOUR.percent)
    }
}
