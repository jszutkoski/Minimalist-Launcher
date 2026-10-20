package com.jszutkoski.launcher

import android.animation.ValueAnimator
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

    /** Android's "Remove animations": every animator ends the moment it starts. */
    private fun animatorScale(scale: Float) {
        ValueAnimator::class.java.getDeclaredMethod("setDurationScale", Float::class.javaPrimitiveType)
            .apply { isAccessible = true }
            .invoke(null, scale)
    }

    @Test
    fun withAnimationsOffThePauseStillLasts() {
        animatorScale(0f)
        try {
            show()
            idle(100)
            assertEquals(View.INVISIBLE, open.visibility)
            idle(4_000)
            assertEquals(View.INVISIBLE, open.visibility)
            idle(1_000)
            assertEquals(View.VISIBLE, open.visibility)
        } finally {
            animatorScale(1f)
        }
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

    @Test
    fun pastTheDailyLimitThePauseIsLongerAndNotNowComesFirst() {
        pause.show(testApp("Instagram"), "45 min", pastLimit = "30 min") { opened = true }
        assertTrue(pause.textViews().any { it.text == context.getString(R.string.pause_past_limit, "30 min", "Instagram") })
        val notNow = button(context.getString(R.string.pause_not_now))
        val choices = pause.textViews().filter { it === notNow || it === open }
        assertTrue(choices.first() === notNow)
        for (second in 1..14) {
            idle(1_000)
            assertEquals("at ${second}s: ${shown()}", View.INVISIBLE, open.visibility)
        }
        idle(1_500)
        assertEquals(View.VISIBLE, open.visibility)
    }

    /** What the pause says, for a failing check to tell. */
    private fun shown() = pause.textViews().filter { it.visibility == View.VISIBLE }.joinToString(" | ") { it.text }

    @Test
    fun theNextOrdinaryPauseIsShortAgain() {
        pause.show(testApp("Instagram"), null, pastLimit = "30 min") { opened = true }
        pause.hide(animate = false)
        show()
        val notNow = button(context.getString(R.string.pause_not_now))
        assertTrue(pause.textViews().filter { it === notNow || it === open }.first() === open)
        idle(6_000)
        assertEquals(View.VISIBLE, open.visibility)
    }
}
