package com.jszutkoski.launcher

import android.app.KeyguardManager
import android.content.Intent
import android.os.BatteryManager
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController

/** Without the accessibility service, home itself hears the screen go off. */
@RunWith(RobolectricTestRunner::class)
class ScreenEventsTest {
    private val app = RuntimeEnvironment.getApplication()
    private var home: ActivityController<MainActivity>? = null

    @After
    fun tearDown() {
        home?.pause()?.stop()?.destroy()
        idle()
    }

    @Suppress("DEPRECATION")
    private fun offTheCharger() {
        app.sendStickyBroadcast(
            Intent(Intent.ACTION_BATTERY_CHANGED)
                .putExtra(BatteryManager.EXTRA_LEVEL, 80)
                .putExtra(BatteryManager.EXTRA_SCALE, 100)
                .putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_DISCHARGING),
        )
        idle()
    }

    /** The power button: the phone locked at once, and the screen off. */
    private fun powerButton(): Boolean {
        val keyguard = shadowOf(app.getSystemService(KeyguardManager::class.java))
        keyguard.setIsDeviceSecure(true)
        keyguard.setKeyguardLocked(true)
        app.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        idle()
        val started = generateSequence { shadowOf(app).nextStartedActivity }.toList()
        return started.any { it.component?.className in setOf(StandbyActivity::class.java.name, StandbyOverAppsActivity::class.java.name) }
    }

    @Test
    fun standbyComesWithThePowerButton() {
        Prefs(app).standbyOnWake = true
        offTheCharger()
        home = Robolectric.buildActivity(MainActivity::class.java).setup()
        assertTrue(powerButton())
    }

    @Test
    fun turnedOffInSettingsNothingComes() {
        Prefs(app).standbyOnWake = false
        offTheCharger()
        home = Robolectric.buildActivity(MainActivity::class.java).setup()
        assertFalse(powerButton())
    }
}
