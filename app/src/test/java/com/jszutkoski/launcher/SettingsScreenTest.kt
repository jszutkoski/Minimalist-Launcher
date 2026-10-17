package com.jszutkoski.launcher

import android.app.Activity
import android.provider.Settings
import android.view.View
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

    /** Taps the row with this title; rows are the clickable parents of their texts. */
    private fun Activity.tap(title: Int) {
        val text = getString(title)
        (content().textViews().first { it.text == text }.parent as View).performClick()
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
    @Config(qualifiers = "pt-rBR")
    fun speaksPortugueseOnAPhoneInPortuguese() {
        val texts = controller.get().content().textViews().map { it.text.toString() }
        assertEquals("Configurações", texts.first())
        assertTrue("Tela inicial" in texts)
        assertTrue("Descanso" in texts)
    }
}
