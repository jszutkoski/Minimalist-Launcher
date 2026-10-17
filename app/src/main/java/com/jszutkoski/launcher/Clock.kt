package com.jszutkoski.launcher

import android.graphics.Typeface
import android.util.TypedValue
import android.widget.TextView

/** The faces the clock can wear, on the home screen and the bedside clock alike. Android's own fonts. */
enum class ClockStyle(val label: Int, private val family: String, private val weight: Int, val spacing: Float) {
    THIN(R.string.clock_thin, "sans-serif", 200, -0.02f),
    BOLD(R.string.clock_bold, "sans-serif", 600, -0.03f),
    NARROW(R.string.clock_narrow, "sans-serif-condensed", 300, 0f),
    SERIF(R.string.clock_serif, "serif", 300, -0.01f),
    MONO(R.string.clock_mono, "monospace", 400, -0.06f),
    ;

    val typeface: Typeface get() = Typeface.create(Typeface.create(family, Typeface.NORMAL), weight, false)
}

enum class ClockSize(val label: Int, val scale: Float) {
    SMALL(R.string.text_small, 0.8f),
    DEFAULT(R.string.text_default, 1f),
    LARGE(R.string.text_large, 1.2f),
}

/**
 * The time in [style] at [sizeSp], or smaller when the widest time would not fit in [maxWidth]
 * pixels: a bold or monospaced face at a large size is wider than the screen.
 */
fun TextView.styleClock(style: ClockStyle, sizeSp: Float, maxWidth: Int) {
    typeface = style.typeface
    letterSpacing = style.spacing
    setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    val widest = paint.measureText(WIDEST_TIME) * FIT_MARGIN + paddingLeft + paddingRight
    if (maxWidth > 0 && widest > maxWidth) setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize * maxWidth / widest)
}

/** Digits are all one width in Android's fonts, so any four stand for every time of day. */
private const val WIDEST_TIME = "00:00"

/** A little room to spare, for faces whose digits are not quite all one width. */
private const val FIT_MARGIN = 1.04f
