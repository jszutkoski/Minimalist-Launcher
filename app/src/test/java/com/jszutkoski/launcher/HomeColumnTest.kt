package com.jszutkoski.launcher

import android.util.TypedValue
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

/** Real text measurement, so the names' height follows their size as on a phone. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeColumnTest {
    private val context = RuntimeEnvironment.getApplication()
    private val column = HomeColumn(context)
    private val names = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    init {
        // The clock, date and dock: a fixed 1000px.
        column.addView(View(context), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1000))
        repeat(8) {
            names.addView(TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_PX, 60f)
                text = "App $it"
            })
        }
        column.addView(names)
        column.names = names
        column.namesChanged(60f)
    }

    private fun measure(height: Int) = column.measure(
        MeasureSpec.makeMeasureSpec(1080, MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
    )

    private fun sizes() = (0 until names.childCount).map { (names.getChildAt(it) as TextView).textSize }

    @Test
    fun namesShrinkJustEnoughToFit() {
        measure(1400)
        val size = sizes().first()
        assertTrue("shrunk to $size", size < 60f && size >= 36f)
        assertTrue(sizes().all { it == size })
        assertTrue("fits: ${names.measuredHeight}", 1000 + names.measuredHeight <= 1400 + 8)
    }

    @Test
    fun namesGrowBackWhenThereIsRoomAgain() {
        measure(1400)
        measure(5000)
        assertEquals(60f, sizes().first(), 0.01f)
    }

    @Test
    fun withRoomToSpareNothingChanges() {
        measure(5000)
        assertEquals(60f, sizes().first(), 0.01f)
    }
}
