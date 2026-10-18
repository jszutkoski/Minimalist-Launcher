package com.jszutkoski.launcher

import android.app.Activity
import android.provider.Settings
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
class SettingsScreenTest {
    private val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()

    private fun Activity.title() = content().textViews().first().text.toString()

    /** Taps the row with this title (rows are the clickable parents of their texts), or a text that is a button itself. */
    private fun Activity.tap(title: Int) {
        val text = getString(title)
        val view = content().textViews().first { it.text == text }
        (if (view.hasOnClickListeners()) view else view.parent as View).performClick()
        idle()
    }

    @Test
    fun aGroupOpensAsItsOwnPage() {
        val activity = controller.get()
        assertEquals(activity.getString(R.string.settings), activity.title())
        activity.tap(R.string.page_search)
        assertEquals(activity.getString(R.string.page_search), activity.title())
    }

    @Test
    fun thePageSurvivesTheScreenBeingRebuilt() {
        controller.get().tap(R.string.page_standby)
        controller.recreate()
        idle()
        val activity = controller.get()
        assertEquals(activity.getString(R.string.page_standby), activity.title())
    }

    @Test
    fun backFromAPageReturnsToTheList() {
        val activity = controller.get()
        activity.tap(R.string.page_apps)
        @Suppress("DEPRECATION")
        activity.onBackPressed()
        idle()
        assertEquals(activity.getString(R.string.settings), activity.title())
        assertFalse(activity.isFinishing)
    }

    @Test
    fun messageIconsOnTheLockScreenCanBeTurnedOff() {
        val activity = controller.get()
        assertTrue(Prefs(activity).lockScreenGlyphs)
        activity.tap(R.string.page_standby)
        activity.tap(R.string.lock_glyphs)
        assertFalse(Prefs(activity).lockScreenGlyphs)
    }

    @Test
    fun theBedsideClockHasABrightnessOfItsOwn() {
        val activity = controller.get()
        activity.tap(R.string.page_standby)
        assertEquals(StandbyBrightness.SYSTEM, Prefs(activity).standbyBrightness)
        activity.tap(R.string.standby_brightness)
        assertEquals(StandbyBrightness.TEN, Prefs(activity).standbyBrightness)
        assertTrue(activity.content().textViews().any { it.text == "10%" })
    }

    @Test
    fun theAppCanSpeakPortugueseWhateverThePhoneSpeaks() {
        val activity = controller.get()
        assertEquals(null, AppLanguage.current(activity))
        activity.tap(R.string.language)
        assertEquals(AppLanguage.PORTUGUESE, AppLanguage.current(activity))
    }

    /** Taps this option of the sheet on top. */
    private fun Activity.choose(option: Int) {
        val sheet = ShadowDialog.getLatestDialog()
        assertTrue(sheet.isShowing)
        val text = getString(option)
        sheet.window!!.decorView.textViews().first { it.text == text }.performClick()
        idle()
    }

    @Test
    fun accessibilityIsExplainedBeforeAndroidsPageOpens() {
        val activity = controller.get()
        activity.tap(R.string.page_gestures)
        activity.tap(R.string.double_tap)
        assertNull(shadowOf(activity).nextStartedActivity)
        activity.choose(R.string.accessibility_agree)
        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, shadowOf(activity).nextStartedActivity.action)
        assertTrue(Prefs(activity).doubleTapLock)
    }

    @Test
    fun notAgreeingLeavesTheFeatureOff() {
        val activity = controller.get()
        activity.tap(R.string.page_standby)
        activity.tap(R.string.standby_wake)
        activity.choose(R.string.accessibility_decline)
        assertNull(shadowOf(activity).nextStartedActivity)
        assertFalse(Prefs(activity).standbyOnWake)
    }

    @Test
    fun theClockIsDigitalOrAnalog() {
        val activity = controller.get()
        activity.tap(R.string.page_clock)
        activity.tap(R.string.clock_design)
        assertEquals(ClockDesign.ANALOG, Prefs(activity).clockLook(ClockScreen.HOME).design)
        activity.tap(R.string.clock_design)
        assertEquals(ClockDesign.DIGITAL, Prefs(activity).clockLook(ClockScreen.HOME).design)
    }

    @Test
    fun thePreviewKeepsItsHeightSoTheRowsStayPut() {
        val activity = controller.get()
        activity.tap(R.string.page_clock)
        fun previewHeight() = activity.content().views().filterIsInstance<FrameLayout>().single { it.layoutParams.height > 0 }.layoutParams.height
        val before = previewHeight()
        activity.tap(R.string.clock_size)
        activity.tap(R.string.clock_design)
        activity.tap(R.string.date_position)
        assertEquals(before, previewHeight())
    }

    @Test
    fun theDateGoesAboveBelowOrNowhere() {
        val activity = controller.get()
        assertEquals(DatePosition.ABOVE, Prefs(activity).clockLook(ClockScreen.HOME).date)
        activity.tap(R.string.page_clock)
        activity.tap(R.string.date_position)
        assertEquals(DatePosition.BELOW, Prefs(activity).clockLook(ClockScreen.HOME).date)
        activity.tap(R.string.date_position)
        assertEquals(DatePosition.HIDDEN, Prefs(activity).clockLook(ClockScreen.HOME).date)
    }

    @Test
    fun theBedsideClockIsSetUpOnItsOwn() {
        val activity = controller.get()
        activity.tap(R.string.page_clock)
        activity.tap(R.string.clock_standby)
        activity.tap(R.string.clock_design)
        assertEquals(ClockDesign.ANALOG, Prefs(activity).clockLook(ClockScreen.STANDBY).design)
        assertEquals(ClockDesign.DIGITAL, Prefs(activity).clockLook(ClockScreen.HOME).design)
    }

    @Test
    fun weatherAndScreenTimeAreChosenPerScreen() {
        val activity = controller.get()
        activity.tap(R.string.page_clock)
        activity.tap(R.string.weather)
        activity.tap(R.string.clock_standby)
        activity.tap(R.string.screen_time)
        val prefs = Prefs(activity)
        assertTrue(prefs.weather(ClockScreen.HOME))
        assertFalse(prefs.weather(ClockScreen.STANDBY))
        assertTrue(prefs.screenTime(ClockScreen.STANDBY))
        assertFalse(prefs.screenTime(ClockScreen.HOME))
    }

    @Test
    fun aSidewaysSwipeCanBeLeftWithNoApp() {
        val activity = controller.get()
        Prefs(activity).setSwipeApp(toLeft = true, key = "pkg.Waze/.Main#0")
        activity.tap(R.string.page_gestures)
        activity.tap(R.string.swipe_left)
        activity.choose(R.string.off)
        assertNull(Prefs(activity).swipeApp(toLeft = true))
    }

    /** Waits for the app list, read off the main thread: changes to the bottom icons wait for it too. */
    private fun SettingsActivity.appsLoaded() {
        val repo = SettingsActivity::class.java.getDeclaredField("repo").apply { isAccessible = true }.get(this) as AppRepository
        repeat(200) {
            if (repo.loaded) return
            Thread.sleep(10)
            idle()
        }
    }

    /** Taps the sheet option reading [text]. */
    private fun chooseText(text: String) {
        ShadowDialog.getLatestDialog().window!!.decorView.textViews().first { it.text == text }.performClick()
        idle()
    }

    private fun Activity.rows(title: Int) = content().textViews().count { it.text.startsWith(getString(title, 0).dropLast(1)) }

    @Test
    fun aBottomIconCanGoAndComeBackWithTheNumber() {
        val activity = controller.get()
        activity.appsLoaded()
        activity.tap(R.string.page_dock)
        // With no apps installed, the camera is the one icon there is.
        assertEquals(1, activity.rows(R.string.dock_place))
        activity.content().textViews().first { it.text == activity.getString(R.string.dock_place, 1) }.let {
            (it.parent as View).performClick()
        }
        idle()
        activity.choose(R.string.dock_remove)
        assertEquals(0, Prefs(activity).dockCount)
        assertEquals(0, activity.rows(R.string.dock_place))
        activity.tap(R.string.dock_count)
        chooseText("1")
        assertEquals(1, Prefs(activity).dockCount)
        assertTrue(activity.content().textViews().any { it.text == activity.getString(R.string.dock_camera) })
    }

    @Test
    @Config(qualifiers = "pt-rBR")
    fun speaksPortugueseOnAPhoneInPortuguese() {
        val texts = controller.get().content().textViews().map { it.text.toString() }
        assertEquals("Configurações", texts.first())
        assertTrue("Tela inicial" in texts)
        assertTrue("Descanso" in texts)
    }
}
