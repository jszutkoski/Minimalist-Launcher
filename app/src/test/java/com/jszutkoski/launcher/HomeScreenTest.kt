package com.jszutkoski.launcher

import android.content.Intent
import android.content.pm.ShortcutInfo
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.view.accessibility.AccessibilityManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class HomeScreenTest {
    private val app = RuntimeEnvironment.getApplication()

    private fun home() = Robolectric.buildActivity(MainActivity::class.java).setup()

    private fun MainActivity.pause() = content().views().filterIsInstance<PauseView>().single()

    /** Opens a shortcut the way the long-press menu does. */
    private fun MainActivity.openShortcut(target: App, shortcut: ShortcutInfo) {
        MainActivity::class.java
            .getDeclaredMethod("openShortcut", App::class.java, ShortcutInfo::class.java)
            .apply { isAccessible = true }
            .invoke(this, target, shortcut)
        idle()
    }

    private fun shortcut() = ShortcutInfo.Builder(app, "chat")
        .setShortLabel("Chat")
        .setIntent(Intent(Intent.ACTION_VIEW))
        .build()

    @Test
    fun aShortcutIntoAPausedAppGoesThroughThePause() {
        val instagram = testApp("Instagram")
        Prefs(app).pauseApps = setOf(instagram.key)
        val activity = home().get()
        activity.openShortcut(instagram, shortcut())
        assertTrue(activity.pause().isShowing)
    }

    @Test
    fun aShortcutIntoAnyOtherAppOpensStraightAway() {
        val activity = home().get()
        activity.openShortcut(testApp("Waze"), shortcut())
        assertFalse(activity.pause().isShowing)
    }

    @Test
    fun aMenuClosesWhenHomeIsLeft() {
        val controller = home()
        val sheet = Sheet(controller.get(), "Menu").apply { show() }
        controller.pause().stop()
        assertFalse(sheet.dialog.isShowing)
    }

    @Test
    fun aMenuClosesWhenHomeIsPressed() {
        val controller = home()
        val sheet = Sheet(controller.get(), "Menu").apply { show() }
        controller.newIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
        assertFalse(sheet.dialog.isShowing)
    }

    private fun MainActivity.search() = content().views().filterIsInstance<EditText>().single()

    private fun keyboardUp() = shadowOf(app.getSystemService(InputMethodManager::class.java)).isSoftInputVisible

    @Test
    fun theKeyboardComesUpWhenTheListOpens() {
        shadowOf(app.getSystemService(AccessibilityManager::class.java)).setTouchExplorationEnabled(true)
        val controller = home()
        controller.windowFocusChanged(true)
        val activity = controller.get()
        // Opened from a button the list is still hidden when asked: focus has to wait for it.
        activity.allAppsButton().performClick()
        idle(1_000)
        assertTrue(activity.search().isFocused)
        assertTrue(keyboardUp())
    }

    @Test
    fun theKeyboardWaitsForTheWindowToHaveFocus() {
        shadowOf(app.getSystemService(AccessibilityManager::class.java)).setTouchExplorationEnabled(true)
        val controller = home()
        // Just back home: the window isn't focused yet, and Android would drop the request.
        controller.windowFocusChanged(false)
        val activity = controller.get()
        activity.allAppsButton().performClick()
        idle(1_000)
        assertFalse(keyboardUp())
        controller.windowFocusChanged(true)
        idle()
        assertTrue(activity.search().isFocused)
        assertTrue(keyboardUp())
    }

    private fun MainActivity.allAppsButton() = content().textViews().single { it.text == getString(R.string.all_apps) }

    private fun MainActivity.allApps() = allAppsButton().parent as View

    @Test
    fun talkBackGetsButtonsForWhatTheSwipesDo() {
        shadowOf(app.getSystemService(AccessibilityManager::class.java)).setTouchExplorationEnabled(true)
        assertEquals(View.VISIBLE, home().get().allApps().visibility)
    }

    @Test
    fun withoutTalkBackThereAreNoExtraButtons() {
        shadowOf(app.getSystemService(AccessibilityManager::class.java)).setTouchExplorationEnabled(false)
        assertEquals(View.GONE, home().get().allApps().visibility)
    }
}
