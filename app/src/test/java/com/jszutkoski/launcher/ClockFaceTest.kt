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
            gap = 24,
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
    fun everyGapIsTheSameGlyphToGlyph() {
        // A column of date, clock and weather, as the screens build it.
        show(ClockLook())
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val weather = TextView(context).apply { text = "18° Sunny" }
        val wrap = LinearLayout.LayoutParams.WRAP_CONTENT
        listOf(date, face, weather).forEach { column.addView(it, LinearLayout.LayoutParams(wrap, wrap)) }
        spaceGlyphs(listOf(spacedText(date), face.spaced(), spacedText(weather)), 24)
        column.measure(MeasureSpec.makeMeasureSpec(2000, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(4000, MeasureSpec.AT_MOST))
        column.layout(0, 0, column.measuredWidth, column.measuredHeight)
        val dateBaseline = date.bottom + date.margins.bottomMargin
        val clockTop = face.top + face.spaced().top
        val lineCentre = face.bottom - face.spaced().bottom
        val weatherCaps = weather.top + spacedText(weather).top
        assertEquals(24f, clockTop - dateBaseline, 1.5f)
        assertEquals(24f, weatherCaps - lineCentre, 1.5f)
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
