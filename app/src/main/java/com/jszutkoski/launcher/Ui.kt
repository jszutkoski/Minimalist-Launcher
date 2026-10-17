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

/** Draws behind the status and navigation bars. Android 15+ does this for every app already. */
fun Activity.edgeToEdge() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        @Suppress("DEPRECATION")
        window.setDecorFitsSystemWindows(false)
    }
}

/** One element of a column spaced glyph to glyph: [view] is laid out, its glyphs [top] and [bottom] inset in it. */
class Spaced(val view: View, val top: Float, val bottom: Float)

/**
 * A line of text as a [Spaced] element, from the top of its capitals to its baseline, measured
 * on [text] but laid out as [view] (the row holding it, when there is one).
 */
fun spacedText(text: TextView, view: View = text): Spaced {
    val metrics = text.paint.fontMetrics
    val boxTop = if (text.includeFontPadding) metrics.top else metrics.ascent
    val capTop = Rect().also { text.paint.getTextBounds("H", 0, 1, it) }.top
    return Spaced(view, text.paddingTop + (capTop - boxTop), metrics.descent + text.paddingBottom)
}

/**
 * Margins that put every shown element of a vertical LinearLayout exactly [gap] pixels from
 * the next, from the glyphs of one to the glyphs of the other, whatever the empty space in
 * their boxes. With [trimEnds], the first and last also lose that space at the column's edges,
 * so the glyphs themselves are what is centred.
 */
fun spaceGlyphs(elements: List<Spaced>, gap: Int, trimEnds: Boolean = false) {
    val shown = elements.filter { it.view.visibility != View.GONE }
    shown.forEachIndexed { i, element ->
        (element.view.layoutParams as ViewGroup.MarginLayoutParams).apply {
            topMargin = when {
                i > 0 -> (gap - element.top).roundToInt()
                trimEnds -> -element.top.roundToInt()
                else -> 0
            }
            bottomMargin = if (i < shown.lastIndex || trimEnds) -element.bottom.roundToInt() else 0
        }
        element.view.requestLayout()
    }
}
