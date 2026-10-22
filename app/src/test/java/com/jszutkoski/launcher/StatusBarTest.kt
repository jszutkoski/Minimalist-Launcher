package com.jszutkoski.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the status bar steps out of the way. */
class StatusBarTest {
    @Test
    fun aLowBatteryNotChargingIsTintedByAndroid() {
        assertTrue(BatteryState(0.05f, charging = false).tintsStatusBar)
        assertTrue(BatteryState(0.20f, charging = false).tintsStatusBar)
        assertFalse(BatteryState(0.21f, charging = false).tintsStatusBar)
        // Charging, Android draws it in the usual color again.
        assertFalse(BatteryState(0.05f, charging = true).tintsStatusBar)
    }

    @Test
    fun onlyTheDimmedBarGoesForAColoredBattery() {
        assertTrue(StatusBarMode.DIMMED.hides(batteryColored = true))
        assertFalse(StatusBarMode.DIMMED.hides(batteryColored = false))
        // Visible stays visible: whoever chose it wants to see the battery.
        assertFalse(StatusBarMode.VISIBLE.hides(batteryColored = true))
        assertTrue(StatusBarMode.HIDDEN.hides(batteryColored = false))
    }
}
