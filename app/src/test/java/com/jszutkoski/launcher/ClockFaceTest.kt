package com.jszutkoski.launcher

import android.view.View
import android.view.View.MeasureSpec
import android.widget.LinearLayout
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode

/** The clock block in each design, measured with real text as on a phone. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClockFaceTest {
    private val context = RuntimeEnvironment.getApplication()
    private val face = ClockFace(context, pressable = false)
    private val date = TextView(context).apply { text = "Saturday, 17 October" }

    private fun show(look: ClockLook, maxWidth: Int = 1000) {
        face.apply(
            look,
            digitsSp = 100f,
            analogDp = 220f,
            labelSp = 13f,
            gravity = 0,
            maxWidth = maxWidth,
            date = date,
            dateGap = 20,
            gap = 26,
        )
        face.measure(MeasureSpec.makeMeasureSpec(2000, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(4000, MeasureSpec.AT_MOST))
    }

    private val View.margins get() = layoutParams as LinearLayout.LayoutParams

    @Test
    fun digitalHasTheBatteryLineAsWideAsTheTime() {
        show(ClockLook(design = ClockDesign.DIGITAL))
        assertEquals(View.VISIBLE, face.battery.visibility)
        assertEquals(View.GONE, face.analog.visibility)
        assertEquals(face.digital.measuredWidth, face.battery.measuredWidth)
    }

    @Test
    fun analogCarriesItsOwnBattery() {
        show(ClockLook(design = ClockDesign.ANALOG))
        assertEquals(View.GONE, face.digital.visibility)
        assertEquals(View.GONE, face.battery.visibility)
        assertEquals(face.analog.measuredWidth, face.analog.measuredHeight)
    }

    @Test
    fun theDateAboveOrBelowMovesTheSpacing() {
        show(ClockLook(date = DatePosition.ABOVE))
        assertEquals(0, face.battery.margins.bottomMargin)
        show(ClockLook(date = DatePosition.BELOW))
        assertTrue(face.battery.margins.bottomMargin != 0)
        assertEquals(0, face.digital.margins.topMargin)
        show(ClockLook(date = DatePosition.HIDDEN))
        assertEquals(0, face.battery.margins.bottomMargin)
        assertEquals(0, face.digital.margins.topMargin)
    }

    @Test
    fun aClockTooWideForTheScreenShrinksToFit() {
        show(ClockLook(weight = ClockWeight.BOLD, size = SizeStep.LARGE), maxWidth = 300)
        assertTrue("${face.digital.measuredWidth}", face.digital.measuredWidth <= 300)
        // Wider than the room left: the face is cut down to it.
        val wide = RuntimeEnvironment.getApplication().dp(220f * SizeStep.LARGE.scale).toInt()
        show(ClockLook(design = ClockDesign.ANALOG, size = SizeStep.LARGE), maxWidth = wide / 2)
        assertEquals(wide / 2, face.analog.measuredWidth)
    }

    @Test
    fun thicknessIsForTheWholeBlock() {
        show(ClockLook(weight = ClockWeight.ULTRA_THIN))
        val thin = face.battery.thickness
        show(ClockLook(weight = ClockWeight.BOLD))
        assertTrue(face.battery.thickness > thin)
        show(ClockLook(design = ClockDesign.ANALOG, weight = ClockWeight.ULTRA_THIN))
        val hands = face.analog.stroke
        show(ClockLook(design = ClockDesign.ANALOG, weight = ClockWeight.BOLD))
        assertTrue(face.analog.stroke > hands)
    }
}
