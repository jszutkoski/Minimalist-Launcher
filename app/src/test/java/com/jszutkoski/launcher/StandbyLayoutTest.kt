package com.jszutkoski.launcher

import android.view.View
import android.view.View.MeasureSpec
import android.widget.ImageView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode

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
    fun theDigitalClockHasNoBatteryLineAndTheSkyHasItsGlyph() {
        val standby = StandbyView(context) {}.apply { setScreen(StandbyScreen.DIGITAL) }
        standby.showWeather(Weather.Now(11, 3, System.currentTimeMillis()))
        standby.layOut()
        assertTrue(standby.views().filterIsInstance<BatteryBar>().all { it.visibility == View.GONE })
        val row = standby.views().filterIsInstance<WeatherRow>().single()
        assertEquals(View.VISIBLE, row.visibility)
        assertTrue(row.views().any { it is ImageView && it.visibility == View.VISIBLE })
    }

    @Test
    fun theAnalogWeatherIsWordsAlone() {
        val standby = StandbyView(context) {}.apply { setScreen(StandbyScreen.ANALOG) }
        standby.showWeather(Weather.Now(11, 3, System.currentTimeMillis()))
        standby.layOut()
        val row = standby.views().filterIsInstance<WeatherRow>().single()
        assertTrue(row.views().none { it is ImageView && it.visibility == View.VISIBLE })
    }

    private companion object {
        const val WIDTH = 1080
        const val HEIGHT = 2400
    }
}
