package com.jszutkoski.launcher

import android.content.Context
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/**
 * The home screen's column. When everything at full size would not fit (large text, eight
 * pinned apps, music and screen time, a large system font), the pinned apps' names shrink just
 * enough, down to [MIN_FIT], instead of the bottom icons being squeezed off the screen.
 */
class HomeColumn(context: Context) : LinearLayout(context) {
    /** The pinned apps, whose names give way. */
    var names: LinearLayout? = null
    private var fullSize = 0f
    private var fit = 1f

    init {
        orientation = VERTICAL
    }

    /** The names were rebuilt at [sizePx]: start again from full size. */
    fun namesChanged(sizePx: Float) {
        fullSize = sizePx
        fit = 1f
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val column = names
        if (column != null && column.childCount > 0 && fullSize > 0f &&
            MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED
        ) {
            // Everything at the names' current size, without the space it actually has.
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            val letters = (0 until column.childCount)
                .map { column.getChildAt(it) }
                .filterIsInstance<TextView>()
                .sumOf { it.measuredHeight - it.paddingTop - it.paddingBottom }
            if (letters > 0) {
                val rest = measuredHeight - letters
                val room = MeasureSpec.getSize(heightMeasureSpec) - rest
                // The letters' height follows the text size, so scale it by the room left for them.
                val wanted = (fit * room / letters).coerceIn(MIN_FIT, 1f)
                if (abs(wanted - fit) > 0.01f) {
                    fit = wanted
                    for (i in 0 until column.childCount) {
                        (column.getChildAt(i) as? TextView)?.setTextSize(TypedValue.COMPLEX_UNIT_PX, fullSize * fit)
                    }
                }
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private companion object {
        const val MIN_FIT = 0.6f
    }
}
