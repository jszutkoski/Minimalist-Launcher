package com.jszutkoski.launcher

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import kotlin.math.roundToInt

/** The whole palette: black, white and two greys. */
object Palette {
    const val BACKGROUND = 0xFF000000.toInt()
    const val SURFACE = 0xFF141414.toInt()
    const val TEXT = 0xFFF2F2F2.toInt()
    const val SECONDARY = 0xFF8A8A8A.toInt()
    /** The quietest text that still reads: 4.5:1 on black, the WCAG minimum for small text. */
    const val TERTIARY = 0xFF757575.toInt()
    const val HAIRLINE = 0xFF1F1F1F.toInt()
    const val TRACK = 0xFF262626.toInt()
    /** Only used to warn about a low battery. */
    const val LOW = 0xFFE5484D.toInt()
    /** Only used for a battery full on the charger. */
    const val FULL = 0xFF3DD68C.toInt()
}

/** Distance a finger travels to fully open or close the app list. */
const val DRAG_DISTANCE_DP = 240f

/** Horizontal margin shared by every screen. */
const val SIDE_MARGIN_DP = 36

fun Context.dp(value: Float): Float = value * resources.displayMetrics.density

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

fun View.dp(value: Int): Int = context.dp(value)

fun weight(weight: Int): Typeface = Typeface.create(Typeface.SANS_SERIF, weight, false)

/** Text that dims while pressed instead of drawing a highlight behind it. */
fun pressable(color: Int): ColorStateList {
    val pressed = if (color == Palette.TEXT) Palette.SECONDARY else Palette.TERTIARY
    return ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf()),
        intArrayOf(pressed, color),
    )
}

fun Context.text(
    value: CharSequence,
    sizeSp: Float,
    color: Int = Palette.TEXT,
    fontWeight: Int = 300,
): TextView = TextView(this).apply {
    text = value
    setTextColor(pressable(color))
    setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    typeface = weight(fontWeight)
    isSingleLine = true
    ellipsize = TextUtils.TruncateAt.END
}

/**
 * Margins that put [line] optically midway between two texts: [gap] from the baseline of the
 * digits in [above] to the line's centre, and [gap] again from there to the tops of the
 * capitals in [below]. Measured from the glyphs, not the text boxes, whose built-in space
 * (descent under the clock, ascent above the date) would otherwise push the line off centre.
 * [above] and [line] must share a vertical LinearLayout; [below] may sit in the next one.
 */
fun spaceEvenly(above: TextView, line: View, lineHeight: Int, below: TextView, gap: Int) {
    val descent = above.paint.fontMetrics.descent + above.paddingBottom
    val metrics = below.paint.fontMetrics
    val boxTop = if (below.includeFontPadding) metrics.top else metrics.ascent
    val caps = Rect().also { below.paint.getTextBounds("H", 0, 1, it) }
    val capInset = below.paddingTop + (caps.top - boxTop)
    (above.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = -descent.roundToInt()
    (line.layoutParams as ViewGroup.MarginLayoutParams).apply {
        topMargin = gap - lineHeight / 2
        bottomMargin = (gap - lineHeight / 2 - capInset).roundToInt()
    }
    above.requestLayout()
}

/** Draws behind the status and navigation bars. Android 15+ does this for every app already. */
fun Activity.edgeToEdge() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        @Suppress("DEPRECATION")
        window.setDecorFitsSystemWindows(false)
    }
}
