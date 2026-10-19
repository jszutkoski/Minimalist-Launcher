package com.jszutkoski.launcher

import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.BatteryManager
import android.os.Process
import android.os.SystemClock
import android.view.MotionEvent
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

/** The bedside clock: when it locks the phone, and what it shows over the lock screen. */
@RunWith(RobolectricTestRunner::class)
class StandbyScreenTest {
    private val app = RuntimeEnvironment.getApplication()
    private val keyguard = shadowOf(app.getSystemService(KeyguardManager::class.java))

    private val realLock = LockService.lockAction
    private var locks = 0

    @Before
    fun countLocks() {
        OverLock.forget()
        // The screen is on (Robolectric's default): this is someone at the phone.
        LockService.lockAction = { locks++; true }
    }

    @After
    fun tearDown() {
        LockService.lockAction = realLock
        OverLock.forget()
        Notifications.update(emptySet())
        // A standby on its way back after locking lands before the next test.
        idle(1_000)
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
        serviceOn()
        phone(secure = true, locked = false)
        battery(charging = true)
        assertFalse(open().lit())
    }

    @Test
    fun theShadeOverAnUnlockedStandbyLocksThePhone() {
        serviceOn()
        phone(secure = true, locked = false)
        // Off the charger, so only the shade locks.
        battery(charging = false)
        shadeOver(seconds = 11)
        assertEquals(1, locks)
    }

    /** The standby started again, lit, after it locked the phone, or null. */
    private fun cameBack(): Intent? {
        idle(1_000)
        return shadowOf(app).nextStartedActivity?.takeIf { it.component?.className == StandbyLitActivity::class.java.name }
    }

    @Test
    fun afterTheShadeLocksThePhoneTheClockComesBackLit() {
        serviceOn()
        phone(secure = true, locked = false)
        battery(charging = false)
        shadeOver(seconds = 11)
        assertTrue(cameBack() != null)
    }

    @Test
    fun homeOnAnUnlockedStandbyLocksAndTheClockComesBack() {
        // Past the first seconds, off the charger: nothing has locked the phone yet.
        serviceOn()
        phone(secure = true, locked = false)
        battery(charging = false)
        val controller = Robolectric.buildActivity(StandbyActivity::class.java).setup()
        idle(11_000)
        controller.userLeaving()
        assertEquals(1, locks)
        // Over the lock screen again, rather than Android's lock screen alone.
        assertTrue(cameBack() != null)
    }

    @Test
    fun homeInTheFirstSecondsJustLeaves() {
        serviceOn()
        phone(secure = true, locked = false)
        battery(charging = false)
        val controller = Robolectric.buildActivity(StandbyActivity::class.java).setup()
        idle(3_000)
        controller.userLeaving()
        assertEquals(0, locks)
        assertTrue(controller.get().isFinishing)
        assertNull(cameBack())
    }

    @Test
    fun inTheFirstSecondsTheShadeIsAsOpenAsATap() {
        serviceOn()
        phone(secure = true, locked = false)
        battery(charging = false)
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
    fun onTheChargerItLeavesLockingToAndroid() {
        // The charger's own clock comes once the phone locks; standby doesn't lock it to stay lit.
        serviceOn()
        phone(secure = true, locked = false)
        battery(charging = true)
        open()
        idle(15_000)
        assertEquals(0, locks)
    }

    @Test
    fun offTheChargerOverTheLockScreenItGoesDarkAfterTenSeconds() {
        // Darkened by locking again, which only turns the screen off.
        phone(secure = true, locked = true)
        battery(charging = false)
        open()
        idle(9_000)
        assertEquals(0, locks)
        idle(2_000)
        assertEquals(1, locks)
    }

    @Test
    fun onTheChargerOverTheLockScreenItKeepsToTheScreenTimeout() {
        phone(secure = true, locked = true)
        battery(charging = true)
        open()
        idle(15_000)
        assertEquals(0, locks)
    }

    @Test
    fun homeOverAndroidsUnlockBringsStandbyBackAsking() {
        phone(secure = true, locked = true)
        battery(charging = false)
        val controller = Robolectric.buildActivity(StandbyActivity::class.java).setup()
        idle(1_000)
        // A tap: Android's unlock comes up. Then a swipe home instead of the fingerprint.
        controller.get().touch()
        controller.userLeaving()
        idle(1_000)
        val back = shadowOf(app).nextStartedActivity
        assertEquals(StandbyActivity::class.java.name, back?.component?.className)
        assertTrue(back!!.getBooleanExtra(OverLock.EXTRA_ASK, false))
        // With the unlock up, standby never goes dark under it.
        idle(15_000)
        assertEquals(0, locks)
    }

    @Test
    fun coveredByTheLockScreenWhileAskingItComesBackAsking() {
        // No word of home from Android: standby just goes out of sight under its unlock.
        phone(secure = true, locked = true)
        battery(charging = false)
        val controller = Robolectric.buildActivity(StandbyActivity::class.java).setup()
        idle(1_000)
        controller.get().touch()
        controller.pause().stop()
        idle(1_000)
        val back = shadowOf(app).nextStartedActivity
        assertEquals(StandbyActivity::class.java.name, back?.component?.className)
        assertTrue(back!!.getBooleanExtra(OverLock.EXTRA_ASK, false))
    }

    @Test
    fun coveredWithoutAskingItWaitsBehind() {
        // A call or an alarm over it, say: nothing to bring back.
        phone(secure = true, locked = true)
        battery(charging = false)
        val controller = Robolectric.buildActivity(StandbyActivity::class.java).setup()
        idle(1_000)
        controller.pause().stop()
        idle(1_000)
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test
    fun theHomeSwipeOverStandbyBringsItBackAsking() {
        // Out of sight over the lock screen, and home coming to Minimalist's home screen.
        phone(secure = true, locked = true)
        battery(charging = false)
        val controller = Robolectric.buildActivity(StandbyActivity::class.java).setup()
        idle(1_000)
        controller.pause().stop()
        assertTrue(OverLock.home(app))
        val back = shadowOf(app).nextStartedActivity
        assertEquals(StandbyActivity::class.java.name, back?.component?.className)
        assertTrue(back!!.getBooleanExtra(OverLock.EXTRA_ASK, false))
    }

    @Test
    fun homeLongAfterStandbyWasCoveredLeavesItBehind() {
        phone(secure = true, locked = true)
        battery(charging = false)
        val controller = Robolectric.buildActivity(StandbyActivity::class.java).setup()
        idle(1_000)
        controller.pause().stop()
        idle(3_000)
        assertFalse(OverLock.home(app))
        assertNull(shadowOf(app).nextStartedActivity)
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
    fun itKeepsToTheSystemsBrightnessUnlessToldOtherwise() {
        assertEquals(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE, open().window.attributes.screenBrightness)
        Prefs(app).standbyBrightness = StandbyBrightness.HALF
        assertEquals(0.5f, open().window.attributes.screenBrightness)
    }

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

    private fun StandbyActivity.touch() {
        val view = content().views().filterIsInstance<StandbyView>().single()
        val now = SystemClock.uptimeMillis()
        view.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 10f, 10f, 0))
        view.dispatchTouchEvent(MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, 10f, 10f, 0))
        idle()
    }

    @Test
    fun theTouchThatWokeTheScreenDoesNotLeave() {
        val standby = open()
        // The last touch of a double-tap that lit the screen.
        standby.touch()
        assertFalse(standby.isFinishing)
        // A moment later, a tap of its own.
        idle(1000)
        standby.touch()
        assertTrue(standby.isFinishing)
    }

    @Test
    fun withoutTheServiceItShowsOnlyOverTheLockScreen() {
        // Unlocked, nothing could lock the phone when someone leaves it: it doesn't stay.
        phone(secure = true, locked = false)
        assertTrue(open().isFinishing)
        phone(secure = true, locked = true)
        assertFalse(open().isFinishing)
    }

    @Test
    fun aCopyLeftBehindAnotherAppNeverLocksIt() {
        serviceOn()
        phone(secure = true, locked = false)
        battery(charging = true)
        val controller = Robolectric.buildActivity(StandbyOverAppsActivity::class.java).setup()
        // Home pressed within the first seconds: the copy over apps waits behind, alive.
        idle(3_000)
        controller.userLeaving().pause().stop()
        idle(15_000)
        battery(charging = true)
        idle(15_000)
        assertEquals(0, locks)
    }
}
