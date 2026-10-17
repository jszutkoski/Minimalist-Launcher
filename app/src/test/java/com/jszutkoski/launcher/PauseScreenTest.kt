package com.jszutkoski.launcher

import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PauseScreenTest {
    private val context = RuntimeEnvironment.getApplication()
    private var cancelled = false
    private var opened = false
    private val pause = PauseView(context) { cancelled = true }

    private fun show() = pause.show(testApp("Instagram"), null) { opened = true }
    private fun button(text: String) = pause.textViews().single { it.text == text }
    private val open get() = button(context.getString(R.string.pause_open, "Instagram"))

    @Test
    fun theWayInAppearsOnlyAfterTheBreath() {
        show()
        assertTrue(pause.isShowing)
        // Robolectric runs animations faster than real time, so only before and after are checked.
        assertEquals(View.INVISIBLE, open.visibility)
        idle(6_000)
        assertEquals(View.VISIBLE, open.visibility)
        assertTrue(pause.textViews().any { it.text == context.getString(R.string.pause_still) })
        open.performClick()
        assertTrue(opened)
    }

    @Test
    fun notNowIsThereFromTheStart() {
        show()
        button(context.getString(R.string.pause_not_now)).performClick()
        assertTrue(cancelled)
        assertFalse(opened)
    }

    @Test
    fun onceHiddenNothingOpens() {
        show()
        idle(6_000)
        pause.hide(animate = false)
        assertFalse(pause.isShowing)
        open.performClick()
        assertFalse(opened)
    }
}
