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
import android.widget.LinearLayout
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

/** The bedside clock: when it may keep the screen lit, and what it shows over the lock screen. */
@RunWith(RobolectricTestRunner::class)
class StandbyScreenTest {
    private val app = RuntimeEnvironment.getApplication()
    private val keyguard = shadowOf(app.getSystemService(KeyguardManager::class.java))

    private val realLock = LockService.lockAction
    private var locks = 0

    @Before
    fun countLocks() {
        // The screen is on (Robolectric's default): this is someone at the phone.
        LockService.lockAction = { locks++; true }
    }

    @After
    fun tearDown() {
        LockService.lockAction = realLock
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

    /** WhatsApp's icon among the notification icons, or null when it isn't there. */
    private fun StandbyActivity.whatsApp() =
        content().views().filterIsInstance<ImageView>().singleOrNull { it.contentDescription == "WhatsApp" }

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
    fun overTheLockScreenTheShadeIsLeftToAndroid() {
        // Locking again would only turn the screen off; the lock screen's own settings decide.
        phone(secure = true, locked = true)
        battery(charging = true)
        shadeOver(seconds = 11)
        assertEquals(0, locks)
    }

    private fun serviceOn() {
        val me = android.content.ComponentName(app, LockService::class.java).flattenToString()
        android.provider.Settings.Secure.putString(app.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, me)
    }

    @Test
    fun unlockedOnTheChargerItLocksToStayLit() {
        serviceOn()
        phone(secure = true, locked = false)
        battery(charging = true)
        open()
        idle(9_000)
        assertEquals(0, locks)
        idle(2_000)
        assertEquals(1, locks)
    }

    @Test
    fun offTheChargerItNeverLocksOnItsOwn() {
        serviceOn()
        phone(secure = true, locked = false)
        battery(charging = false)
        open()
        idle(15_000)
        assertEquals(0, locks)
    }

    @Test
    fun alreadyLockedThereIsNothingToDo() {
        serviceOn()
        phone(secure = true, locked = true)
        battery(charging = true)
        open()
        idle(15_000)
        assertEquals(0, locks)
    }

    @Test
    fun withNoScreenLockThereIsNothingToLock() {
        phone(secure = false, locked = false)
        battery(charging = true)
        shadeOver(seconds = 11)
        assertEquals(0, locks)
    }

    /** An app on the phone, with a home screen entry, as Android would list it. */
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

    private fun waiting(vararg packages: String) {
        Notifications.update(packages.map { it to Process.myUserHandle() }.toSet())
        idle()
    }

    private fun whatsAppWaiting() {
        install("com.whatsapp", "WhatsApp")
        waiting("com.whatsapp")
    }

    /** The descriptions of the notification icons, left to right. */
    private fun StandbyActivity.icons() = content().views().filterIsInstance<ImageView>()
        .filter { it.parent is LinearLayout && (it.parent as View).visibility == View.VISIBLE }
        .map { it.contentDescription.toString() }

    @Test
    fun messageIconsShowOverTheLockScreenByDefault() {
        phone(secure = true, locked = true)
        whatsAppWaiting()
        assertEquals(View.VISIBLE, open().whatsApp()?.visibility)
    }

    @Test
    fun appsWithoutAnIconOfTheirOwnShareThreeDots() {
        install("com.whatsapp", "WhatsApp")
        install("org.example.notes", "Notes")
        install("org.example.shop", "Shop")
        waiting("org.example.notes", "com.whatsapp", "org.example.shop")
        assertEquals(listOf("WhatsApp", "Notes, Shop"), open().icons())
    }

    @Test
    fun aMutedAppShowsNoIcon() {
        install("com.whatsapp", "WhatsApp")
        install("org.example.notes", "Notes")
        Prefs(app).standbyMuted = setOf("com.whatsapp")
        waiting("com.whatsapp", "org.example.notes")
        assertEquals(listOf("Notes"), open().icons())
    }

    @Test
    fun theSystemsOwnPassingNotesShowNoIcon() {
        // Nothing to open from the home screen: a screenshot saved, a cable plugged in.
        waiting("com.android.systemui")
        assertEquals(emptyList<String>(), open().icons())
    }

    @Test
    fun messageIconsCanStayOffTheLockScreen() {
        Prefs(app).lockScreenGlyphs = false
        phone(secure = true, locked = true)
        whatsAppWaiting()
        val standby = open()
        assertNull(standby.whatsApp())
        // Unlocked, the phone is in your hands: the icons come back on the next wake.
        keyguard.setKeyguardLocked(false)
        app.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON))
        idle()
        assertEquals(View.VISIBLE, standby.whatsApp()?.visibility)
    }
}
