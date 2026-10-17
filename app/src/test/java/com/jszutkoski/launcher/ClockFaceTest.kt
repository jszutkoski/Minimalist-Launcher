package com.jszutkoski.launcher

import android.view.View
import android.view.View.MeasureSpec
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode

/** The clock widget in each design, measured with real text as on a phone. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClockFaceTest {
    private val context = RuntimeEnvironment.getApplication()
    private val face = ClockFace(context, pressable = false)
    private val date = TextView(context).apply { text = "Saturday, 17 October" }

    private fun show(look: ClockLook, maxWidth: Int = 1000) {
        face.apply(look, digitsSp = 100f, analogDp = 170f, labelSp = 13f, gravity = 0, maxWidth = maxWidth, below = date, gap = 26)
        face.measure(MeasureSpec.makeMeasureSpec(2000, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(4000, MeasureSpec.AT_MOST))
    }

    @Test
    fun stackedPutsTheHoursOverTheMinutes() {
        show(ClockLook(design = ClockDesign.STACKED))
        assertEquals(View.GONE, face.digital.visibility)
        assertEquals(View.VISIBLE, face.stack.visibility)
        // Two rows of digits, closer together than two plain lines of text would be.
        val rows = face.stack.measuredHeight
        val line = (face.stack.getChildAt(0) as TextView).measuredHeight
        assertTrue("$rows vs $line", rows < 2 * line)
    }

    @Test
    fun analogShowsTheFaceInsteadOfDigits() {
        show(ClockLook(design = ClockDesign.ANALOG))
        assertEquals(View.GONE, face.digital.visibility)
        assertEquals(View.VISIBLE, face.analog.visibility)
        assertEquals(face.analog.measuredWidth, face.analog.measuredHeight)
    }

    @Test
    fun aRingIsRoundAndTheLineAsWideAsTheTime() {
        show(ClockLook(battery = BatteryDesign.RING))
        assertEquals(face.battery.measuredWidth, face.battery.measuredHeight)
        show(ClockLook(battery = BatteryDesign.LINE))
        assertEquals(face.digital.measuredWidth, face.battery.measuredWidth)
    }

    @Test
    fun aClockTooWideForTheScreenShrinksToFit() {
        show(ClockLook(weight = ClockWeight.BOLD, size = SizeStep.LARGE), maxWidth = 300)
        assertTrue("${face.digital.measuredWidth}", face.digital.measuredWidth <= 300)
    }

    @Test
    fun thickerChoicesDrawThicker() {
        show(ClockLook(batteryWeight = LineWeight.FINE))
        val fine = face.battery.lineWidth
        show(ClockLook(batteryWeight = LineWeight.THICK))
        assertTrue(face.battery.lineWidth > fine)
        assertTrue(AnalogClock.strokeDp(ClockWeight.BOLD.weight) > AnalogClock.strokeDp(ClockWeight.THIN.weight))
    }
}
