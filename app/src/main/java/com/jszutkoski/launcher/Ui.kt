package com.jszutkoski.launcher

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Build
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import kotlin.math.roundToInt

/** The whole palette: black, white and two greys. */
object Palette {
    const val BACKGROUND = 0xFF000000.toInt()
    const val SURFACE = 0xFF141414.toInt()
    const val TEXT = 0xFFF2F2F2.toInt()
    const val SECONDARY = 0xFF8A8A8A.toInt()
    const val TERTIARY = 0xFF4D4D4D.toInt()
    const val HAIRLINE = 0xFF1F1F1F.toInt()
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
