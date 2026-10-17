package com.jszutkoski.launcher

import android.app.KeyguardManager
import android.content.Intent
import android.os.BatteryManager
import android.os.Process
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** The bedside clock: when it may keep the screen lit, and what it shows over the lock screen. */
@RunWith(RobolectricTestRunner::class)
class StandbyScreenTest {
    private val app = RuntimeEnvironment.getApplication()
    private val keyguard = shadowOf(app.getSystemService(KeyguardManager::class.java))

    private val realLock = LockService.lockAction
    private val realCloseShade = LockService.closeShadeAction
    private var locks = 0
    private var shadesClosed = 0

    @Before
    fun countLocks() {
        // The screen is on (Robolectric's default): this is someone at the phone.
        LockService.lockAction = { locks++; true }
        LockService.closeShadeAction = { shadesClosed++; true }
    }

    @After
    fun tearDown() {
        LockService.lockAction = realLock
        LockService.closeShadeAction = realCloseShade
        Notifications.update(emptySet())
        idle()
    }

    @Suppress("DEPRECATION")
    private fun battery(charging: Boolean) {
        val status = if (charging) BatteryManager.BATTERY_STATUS_CHARGING else BatteryManager.BATTERY_STATUS_DISCHARGING
        app.sendStickyBroadcast(
            Intent(Intent.ACTION_BATTERY_CHANGED)
                .putExtra(BatteryManager.EXTRA_LEVEL, 80)
                .putExtra(BatteryManager.EXTRA_SCALE, 100)
                .putExtra(BatteryManager.EXTRA_STATUS, status),
        )
        idle()
    }

    private fun phone(secure: Boolean, locked: Boolean) {
        keyguard.setIsDeviceSecure(secure)
        keyguard.setKeyguardLocked(locked)
    }

    private fun open() = Robolectric.buildActivity(StandbyActivity::class.java).setup().get()

    /** Standby open for [seconds], then something of the system's (the shade) coming over it. */
    private fun shadeOver(seconds: Long) {
        val controller = Robolectric.buildActivity(StandbyActivity::class.java).setup()
        idle(seconds * 1000)
        controller.windowFocusChanged(false)
        idle()
    }

    private fun StandbyActivity.lit() =
        window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0

    private fun StandbyActivity.whatsApp() =
        content().views().filterIsInstance<ImageView>().single { it.contentDescription == "WhatsApp" }

    @Test
    fun unlockedOnTheChargerItTimesOutLikeAnyScreen() {
        phone(secure = true, locked = false)
        battery(charging = true)
        assertFalse(open().lit())
    }

    @Test
    fun overTheLockScreenOnTheChargerItStaysLit() {
        phone(secure = true, locked = true)
        battery(charging = true)
        assertTrue(open().lit())
    }

    @Test
    fun onceAndroidLocksTheNextWakeStaysLit() {
        phone(secure = true, locked = false)
        battery(charging = true)
        val standby = open()
        assertFalse(standby.lit())
        keyguard.setKeyguardLocked(true)
        app.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON))
        idle()
        assertTrue(standby.lit())
    }

    @Test
    fun withNoScreenLockThereIsNothingToGuard() {
        phone(secure = false, locked = false)
        battery(charging = true)
        assertTrue(open().lit())
    }

    @Test
    fun offTheChargerItTimesOut() {
        phone(secure = true, locked = true)
        battery(charging = false)
        val standby = open()
        assertFalse(standby.lit())
        battery(charging = true)
        assertTrue(standby.lit())
        battery(charging = false)
        assertFalse(standby.lit())
    }

    @Test
    fun theShadeOverAnUnlockedStandbyLocksThePhone() {
        phone(secure = true, locked = false)
        battery(charging = true)
        shadeOver(seconds = 11)
        assertEquals(1, locks)
    }

    @Test
    fun inTheFirstSecondsTheShadeIsAsOpenAsATap() {
        phone(secure = true, locked = false)
        battery(charging = true)
        shadeOver(seconds = 3)
        assertEquals(0, locks)
    }

    @Test
    fun overTheLockScreenTheShadeIsClosedAgain() {
        phone(secure = true, locked = true)
        battery(charging = true)
        shadeOver(seconds = 1)
        assertEquals(1, shadesClosed)
        // Locking again would only turn the screen off.
        assertEquals(0, locks)
    }

    @Test
    fun theShadeCanBeLeftToAndroidsLockScreenSettings() {
        Prefs(app).closeShadeOnStandby = false
        phone(secure = true, locked = true)
        battery(charging = true)
        shadeOver(seconds = 1)
        assertEquals(0, shadesClosed)
    }

    @Test
    fun withNoScreenLockThereIsNothingToLock() {
        phone(secure = false, locked = false)
        battery(charging = true)
        shadeOver(seconds = 11)
        assertEquals(0, locks)
        assertEquals(0, shadesClosed)
    }

    private fun whatsAppWaiting() {
        Notifications.update(setOf("com.whatsapp" to Process.myUserHandle()))
        idle()
    }

    @Test
    fun messageIconsShowOverTheLockScreenByDefault() {
        phone(secure = true, locked = true)
        whatsAppWaiting()
        assertEquals(View.VISIBLE, open().whatsApp().visibility)
    }

    @Test
    fun messageIconsCanStayOffTheLockScreen() {
        Prefs(app).lockScreenGlyphs = false
        phone(secure = true, locked = true)
        whatsAppWaiting()
        val standby = open()
        assertEquals(View.GONE, standby.whatsApp().visibility)
        // Unlocked, the phone is in your hands: the icons come back on the next wake.
        keyguard.setKeyguardLocked(false)
        app.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON))
        idle()
        assertEquals(View.VISIBLE, standby.whatsApp().visibility)
    }
}
