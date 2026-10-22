package com.jszutkoski.launcher

import android.view.View
import android.view.View.MeasureSpec
import android.widget.ImageView
import android.widget.TextClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/** The two bedside screens, laid out with real text as on a phone. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StandbyLayoutTest {
    private val context = RuntimeEnvironment.getApplication()

    private fun StandbyView.layOut() {
        measure(MeasureSpec.makeMeasureSpec(WIDTH, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(HEIGHT, MeasureSpec.EXACTLY))
        layout(0, 0, WIDTH, HEIGHT)
    }

    /** Where [view] is on the screen, from the top. */
    private fun View.top(): Int = top + ((parent as? View)?.top() ?: 0)

    private fun StandbyView.analog() = views().filterIsInstance<AnalogClock>().single()

    @Test
    fun theAnalogFaceSitsAtItsHeightWhateverComesUnderIt() {
        val standby = StandbyView(context) {}.apply { setScreen(StandbyScreen.ANALOG) }
        standby.layOut()
        val face = standby.analog()
        val middle = face.top() + face.height / 2
        assertEquals(HEIGHT * StandbyScreen.ANALOG.clockCentre, middle.toFloat(), 2f)
        standby.showWeather(Weather.Now(11, 1, System.currentTimeMillis()))
        standby.layOut()
        assertEquals(middle, face.top() + face.height / 2)
    }

    @Test
    fun theDigitalBatteryLineIsHalfwayToTheWeather() {
        val standby = StandbyView(context) {}.apply { setScreen(StandbyScreen.DIGITAL) }
        standby.updateBattery(BatteryState(0.6f, charging = false))
        standby.showWeather(Weather.Now(11, 3, System.currentTimeMillis()))
        standby.layOut()
        val line = standby.views().filterIsInstance<BatteryBar>().single()
        assertEquals(View.VISIBLE, line.visibility)
        // No number in it.
        assertEquals(null, line.label)
        val digits = standby.views().filterIsInstance<TextClock>().single()
        val row = standby.views().filterIsInstance<WeatherRow>().single()
        val baseline = digits.top() + digits.baseline
        val centre = line.top() + line.height / 2
        val capitals = row.top() + row.spaced().top.toInt()
        assertTrue("$baseline, $centre, $capitals", abs((centre - baseline) - (capitals - centre)) <= 3)
    }

    @Test
    fun theDigitalSkyHasItsGlyph() {
        val standby = StandbyView(context) {}.apply { setScreen(StandbyScreen.DIGITAL) }
        standby.showWeather(Weather.Now(11, 3, System.currentTimeMillis()))
        standby.layOut()
        val row = standby.views().filterIsInstance<WeatherRow>().single()
        assertEquals(View.VISIBLE, row.visibility)
        assertTrue(row.views().any { it is ImageView && it.visibility == View.VISIBLE })
    }

    @Test
    fun theAnalogWeatherHasTheSkysGlyphToo() {
        val standby = StandbyView(context) {}.apply { setScreen(StandbyScreen.ANALOG) }
        standby.showWeather(Weather.Now(11, 3, System.currentTimeMillis()))
        standby.layOut()
        val row = standby.views().filterIsInstance<WeatherRow>().single()
        assertTrue(row.views().any { it is ImageView && it.visibility == View.VISIBLE })
    }

    @Test
    fun theLastLineKeepsItsTail() {
        val standby = StandbyView(context) {}.apply { setScreen(StandbyScreen.DIGITAL) }
        standby.showWeather(Weather.Now(11, 3, System.currentTimeMillis()))
        standby.layOut()
        // No notifications: the weather is the last line, and nothing of it is cut off.
        val row = standby.views().filterIsInstance<WeatherRow>().single()
        val group = row.parent as View
        assertTrue("${row.bottom} > ${group.height}", row.bottom <= group.height)
    }

    private companion object {
        const val WIDTH = 1080
        const val HEIGHT = 2400
    }
}
