package com.jszutkoski.launcher

import com.jszutkoski.launcher.ScreenTime.Change
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenTimeTest {
    private val midnight = 1_000_000L
    private val now = midnight + 10_000

    private fun on(activity: String, time: Long) = Change(activity, true, time)
    private fun off(activity: String, time: Long) = Change(activity, false, time)

    @Test
    fun addsSeparateStretches() {
        val changes = listOf(on("a", midnight + 100), off("a", midnight + 300), on("b", midnight + 500), off("b", midnight + 600))
        assertEquals(300L, ScreenTime.foreground(changes, midnight, now))
    }

    @Test
    fun anAppOpenBeforeMidnightCountsFromMidnight() {
        val changes = listOf(on("a", midnight - 5_000), off("a", midnight + 200))
        assertEquals(200L, ScreenTime.foreground(changes, midnight, now))
    }

    @Test
    fun twoAppsSharingTheScreenCountOnce() {
        val changes = listOf(on("a", midnight + 100), on("b", midnight + 200), off("a", midnight + 300), off("b", midnight + 400))
        assertEquals(300L, ScreenTime.foreground(changes, midnight, now))
    }

    @Test
    fun pausedThenStoppedCountsOnce() {
        val changes = listOf(on("a", midnight + 100), off("a", midnight + 200), off("a", midnight + 250))
        assertEquals(100L, ScreenTime.foreground(changes, midnight, now))
    }

    @Test
    fun homeComingBackClosesWhateverWasLeftOpen() {
        val changes = listOf(on("a", midnight + 100), Change("", false, midnight + 400, home = true))
        assertEquals(300L, ScreenTime.foreground(changes, midnight, now))
    }

    @Test
    fun stillOpenCountsUntilNow() {
        assertEquals(1_000L, ScreenTime.foreground(listOf(on("a", now - 1_000)), midnight, now))
    }

    @Test
    fun format() {
        assertEquals("2 h 14 min", ScreenTime.format(2 * 3_600_000L + 14 * 60_000L))
        assertEquals("14 min", ScreenTime.format(14 * 60_000L))
        assertEquals("0 min", ScreenTime.format(59_000L))
    }
}
