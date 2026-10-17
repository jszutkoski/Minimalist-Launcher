package com.jszutkoski.launcher

import android.os.SystemClock
import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** The home screen's own gestures, fed straight to its root layout. */
@RunWith(RobolectricTestRunner::class)
class SwipeLayoutTest {
    private val sideways = mutableListOf<Boolean>()
    private var swipesDown = 0
    private val layout = SwipeLayout(RuntimeEnvironment.getApplication()).apply {
        listener = object : SwipeLayout.Listener {
            override fun onDrawerDrag(progress: Float) = Unit
            override fun onDrawerRelease(open: Boolean) = Unit
            override fun onSwipeDown() {
                swipesDown++
            }
            override fun onDoubleTapEmpty() = Unit
            override fun onPinchIn() = Unit
            override fun onLongPressEmpty() = Unit
            override fun onSwipeSideways(toLeft: Boolean) {
                sideways += toLeft
            }
        }
    }

    /** One finger from (x0, y0) to (x1, y1) in ten steps over [millis]. */
    private fun drag(x0: Float, y0: Float, x1: Float, y1: Float, millis: Long = 200) {
        val start = SystemClock.uptimeMillis()
        fun event(action: Int, t: Long, x: Float, y: Float) =
            MotionEvent.obtain(start, start + t, action, x, y, 0).also {
                layout.dispatchTouchEvent(it)
                it.recycle()
            }
        event(MotionEvent.ACTION_DOWN, 0, x0, y0)
        for (step in 1..10) {
            event(MotionEvent.ACTION_MOVE, millis * step / 10, x0 + (x1 - x0) * step / 10, y0 + (y1 - y0) * step / 10)
        }
        event(MotionEvent.ACTION_UP, millis, x1, y1)
    }

    private fun dp(value: Float) = RuntimeEnvironment.getApplication().dp(value)

    @Test
    fun aSwipeToTheLeftOrRight() {
        drag(dp(300f), dp(400f), dp(100f), dp(410f))
        drag(dp(100f), dp(400f), dp(300f), dp(390f))
        assertEquals(listOf(true, false), sideways)
    }

    @Test
    fun aShortSlowNudgeSidewaysDoesNothing() {
        drag(dp(200f), dp(400f), dp(180f), dp(400f), millis = 1_000)
        assertEquals(emptyList<Boolean>(), sideways)
    }

    @Test
    fun aSwipeDownIsNotSideways() {
        drag(dp(200f), dp(300f), dp(230f), dp(500f))
        assertEquals(emptyList<Boolean>(), sideways)
        assertEquals(1, swipesDown)
    }
}
