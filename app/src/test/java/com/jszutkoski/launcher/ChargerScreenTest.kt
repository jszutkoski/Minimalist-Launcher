package com.jszutkoski.launcher

import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.BatteryManager
import android.os.Process
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController

/** The clock for the night on the charger: when it comes, how it shows, and when it goes. */
@RunWith(RobolectricTestRunner::class)
class ChargerScreenTest {
    private val app = RuntimeEnvironment.getApplication()
    private val keyguard = shadowOf(app.getSystemService(KeyguardManager::class.java))

    private val realLock = LockService.lockAction
    private var locks = 0
    private val opened = mutableListOf<ActivityController<ChargerActivity>>()

    @Before
    fun countLocks() {
        LockService.lockAction = {
            locks++
            keyguard.setKeyguardLocked(true)
            true
        }
    }

    @After
    fun tearDown() {
        LockService.lockAction = realLock
        Notifications.update(emptySet())
        idle(4_000)
        // Gone, so the next test starts with no clock in front.
        opened.forEach { if (!it.get().isDestroyed) it.pause().stop().destroy() }
        idle()
    }

    @Suppress("DEPRECATION")
    private fun battery(status: Int, plugged: Int = 0) {
        app.sendStickyBroadcast(
            Intent(Intent.ACTION_BATTERY_CHANGED)
                .putExtra(BatteryManager.EXTRA_LEVEL, 80)
                .putExtra(BatteryManager.EXTRA_SCALE, 100)
                .putExtra(BatteryManager.EXTRA_STATUS, status)
                .putExtra(BatteryManager.EXTRA_PLUGGED, plugged),
        )
        idle()
    }

    private fun charging() = battery(BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_PLUGGED_AC)

    private fun unplugged() = battery(BatteryManager.BATTERY_STATUS_DISCHARGING)

    private fun phone(secure: Boolean, locked: Boolean) {
        keyguard.setIsDeviceSecure(secure)
        keyguard.setKeyguardLocked(locked)
    }

    private fun open(): ChargerActivity {
        val controller = Robolectric.buildActivity(ChargerActivity::class.java).setup()
        opened += controller
        return controller.get()
    }

    private fun ChargerActivity.lit() =
        window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0

    /** What the notification glyphs say, left to right. */
    private fun ChargerActivity.icons() = content().views().filterIsInstance<ImageView>()
        .filter { it.contentDescription != null && (it.parent as View).visibility == View.VISIBLE }
        .map { it.contentDescription.toString() }

    private fun startedClock(): Intent? {
        // A few seconds after the screen goes off.
        idle(2_000)
        assertNull(shadowOf(app).nextStartedActivity)
        idle(1_500)
        return shadowOf(app).nextStartedActivity?.takeIf { it.component?.className == ChargerActivity::class.java.name }
    }

    @Test
    fun overTheLockScreenItStaysLitAndDim() {
        phone(secure = true, locked = true)
        charging()
        val clock = open()
        assertFalse(clock.isFinishing)
        assertTrue(clock.lit())
        assertEquals(ChargerActivity.BRIGHTNESS, clock.window.attributes.screenBrightness)
    }

    @Test
    fun overAnUnlockedPhoneItNeverShows() {
        // Lit all night there, anyone could pull down the notifications.
        phone(secure = true, locked = false)
        charging()
        assertTrue(open().isFinishing)
    }

    @Test
    fun withNoScreenLockThereIsNothingToGuard() {
        phone(secure = false, locked = false)
        charging()
        val clock = open()
        assertFalse(clock.isFinishing)
        assertTrue(clock.lit())
    }

    @Test
    fun pluggedInButHoldingTheChargeCountsAsOnTheCharger() {
        // A phone that stops at 80% to spare its battery says "not charging" all night.
        phone(secure = true, locked = true)
        battery(BatteryManager.BATTERY_STATUS_NOT_CHARGING, BatteryManager.BATTERY_PLUGGED_AC)
        assertTrue(open().lit())
    }

    @Test
    fun takenOffTheChargerItStepsAside() {
        phone(secure = true, locked = true)
        charging()
        val clock = open()
        unplugged()
        assertTrue(clock.isFinishing)
    }

    private fun install(packageName: String, label: String) {
        val pm = shadowOf(app.packageManager)
        pm.installPackage(
            PackageInfo().apply {
                this.packageName = packageName
                applicationInfo = ApplicationInfo().apply {
                    this.packageName = packageName
                    nonLocalizedLabel = label
                }
            },
        )
        val component = ComponentName(packageName, "$packageName.Main")
        pm.addActivityIfNotPresent(component)
        pm.addIntentFilterForActivity(
            component,
            IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addCategory(Intent.CATEGORY_LAUNCHER)
            },
        )
    }

    @Test
    fun anythingWaitingIsThreeDotsThatNameNoApp() {
        phone(secure = true, locked = true)
        charging()
        install("com.whatsapp", "WhatsApp")
        install("com.google.android.gm", "Gmail")
        val clock = open()
        assertEquals(emptyList<String>(), clock.icons())
        Notifications.update(setOf("com.whatsapp" to Process.myUserHandle(), "com.google.android.gm" to Process.myUserHandle()))
        idle()
        assertEquals(listOf(app.getString(R.string.charger_waiting)), clock.icons())
    }

    @Test
    fun everyMinuteItIsSomewhereElse() {
        phone(secure = true, locked = true)
        charging()
        val controller = Robolectric.buildActivity(ChargerActivity::class.java).setup().visible()
        opened += controller
        idle()
        val group = controller.get().content().views().filterIsInstance<StandbyView>().single().getChildAt(0)
        assertEquals(0f, group.translationY)
        idle(62_000)
        assertTrue(group.translationX != 0f || group.translationY != 0f)
        assertEquals(1f, group.alpha)
    }

    @Test
    fun lockedOnTheChargerTheClockComesUp() {
        // The screen went off in an app, with Android still waiting a few seconds to lock.
        Prefs(app).standbyWhenCharging = true
        phone(secure = true, locked = false)
        charging()
        ChargerActivity.startWhenLocked(app)
        assertEquals(1, locks)
        assertTrue(startedClock() != null)
    }

    @Test
    fun turnedOffInSettingsNothingComes() {
        Prefs(app).standbyWhenCharging = false
        phone(secure = true, locked = true)
        charging()
        ChargerActivity.startWhenLocked(app)
        assertNull(startedClock())
    }

    @Test
    fun powerOverTheClockJustTurnsTheScreenOff() {
        Prefs(app).standbyWhenCharging = true
        phone(secure = true, locked = true)
        charging()
        open()
        ChargerActivity.startWhenLocked(app)
        assertEquals(0, locks)
        assertNull(startedClock())
    }

    /** ActivityInfo's flag for android:turnScreenOn, hidden from the SDK. */
    private val TURN_SCREEN_ON = 0x1000000

    @Test
    fun theScreenComesOnAsTheyOpen() {
        // Only the manifest's request reaches Android in time to light a dark screen.
        for (screen in listOf(ChargerActivity::class.java, StandbyLitActivity::class.java)) {
            val info = app.packageManager.getActivityInfo(ComponentName(app, screen), 0)
            assertTrue(screen.simpleName, info.flags and TURN_SCREEN_ON != 0)
        }
        // Standby itself opens at screen off and must wait for the wake.
        val standby = app.packageManager.getActivityInfo(ComponentName(app, StandbyOverAppsActivity::class.java), 0)
        assertFalse(standby.flags and TURN_SCREEN_ON != 0)
    }
}
