package com.jszutkoski.launcher

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.text.NumberFormat

/** How the home screen's clock is laid out. */
enum class HomeLayout(val label: Int) {
    /** The clock as set up in Settings → Clock, the weather and screen time on lines under it. */
    CLASSIC(R.string.layout_classic),

    /** The date, the digits, and one quiet row: weather, screen time, battery. */
    PANEL(R.string.layout_panel),
}

/** Between the panel layout's date, digits and row, glyph to glyph: a little more air than the classic's. */
const val PANEL_GAP_DP = 28

/**
 * The panel under the clock: the sky, today's screen time and the battery, each a glyph and its
 * value, in the grey of the lines under the classic clock, parted by hairlines. A part with
 * nothing to show (weather or screen time off) leaves, and its hairline with it.
 */
class HomePanel(context: Context, onScreenTime: () -> Unit, onBattery: () -> Unit) : LinearLayout(context) {
    private class Part(val view: LinearLayout, val icon: ImageView, val value: TextView)

    private val batteryGlyph = BatteryGlyph(context)
    private val weather = part(null)
    private val screenTime = part(onScreenTime)
    private val battery = part(onBattery).apply { icon.setImageDrawable(batteryGlyph) }
    private val parts = listOf(weather, screenTime, battery)
    private val dividers = List(parts.size - 1) {
        View(context).apply { setBackgroundColor(Palette.HAIRLINE) }
    }
    private var scale = 1f

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        screenTime.icon.setImageResource(R.drawable.ic_screen_time)
        parts.forEachIndexed { i, part ->
            if (i > 0) addView(dividers[i - 1])
            addView(part.view)
        }
        weather.view.visibility = GONE
        screenTime.view.visibility = GONE
        setTextScale(1f)
    }

    private fun part(onClick: (() -> Unit)?): Part {
        val icon = ImageView(context).apply {
            imageTintList = ColorStateList.valueOf(Palette.SECONDARY)
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            isDuplicateParentStateEnabled = true
        }
        val value = context.text("", 17f, Palette.SECONDARY, 400).apply {
            letterSpacing = 0.02f
            includeFontPadding = false
            isDuplicateParentStateEnabled = true
        }
        val view = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(icon)
            addView(value)
            if (onClick != null) setOnClickListener { onClick() }
        }
        return Part(view, icon, value)
    }

    /** The values at the home screen's text size, the glyphs and spacing in step. */
    fun setTextScale(scale: Float) {
        this.scale = scale
        val icon = context.dp(ICON_DP * scale).toInt()
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        for (part in parts) {
            part.value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f * scale)
            part.icon.layoutParams = LayoutParams(icon, icon).apply { rightMargin = context.dp(8f * scale).toInt() }
            // A finger-sized row, the hairlines a little shorter than it.
            part.view.minimumHeight = context.dp(48)
            part.view.setPadding(context.dp(PADDING_DP), 0, context.dp(PADDING_DP), 0)
            part.value.layoutParams = LayoutParams(wrap, wrap)
        }
        for (divider in dividers) {
            divider.layoutParams = LayoutParams(context.dp(1), context.dp(28f * scale).toInt())
        }
        requestLayout()
    }

    /** "☁ 18°", or the part leaves without a reading. */
    fun showWeather(now: Weather.Now?) {
        if (now != null) {
            weather.icon.setImageResource(now.icon)
            weather.value.text = now.degrees
            weather.view.contentDescription = listOfNotNull(now.degrees, now.describe(context)).joinToString(" ")
        }
        show(weather, now != null)
    }

    /** "1 h 19 min" today, or the part leaves. */
    fun showScreenTime(label: String?) {
        screenTime.value.text = label.orEmpty()
        screenTime.view.contentDescription = label?.let { context.getString(R.string.screen_time_today, it) }
        show(screenTime, label != null)
    }

    fun showBattery(state: BatteryState) {
        batteryGlyph.update(state)
        battery.value.text = NumberFormat.getPercentInstance().format(state.fraction.toDouble())
    }

    private fun show(part: Part, shown: Boolean) {
        part.view.visibility = if (shown) VISIBLE else GONE
        // A hairline only between two parts that are there.
        var before = false
        parts.forEachIndexed { i, p ->
            val visible = p.view.visibility == VISIBLE
            if (i > 0) dividers[i - 1].visibility = if (visible && before) VISIBLE else GONE
            before = before || visible
        }
    }

    /** As an element of the home column: from the values' capitals to their baseline. */
    fun spaced(): Spaced {
        val text = spacedText(battery.value, this)
        val metrics = battery.value.paint.fontMetrics
        val textHeight = metrics.descent - metrics.ascent
        val inset = (maxOf(context.dp(48).toFloat(), textHeight) - textHeight) / 2f
        return Spaced(this, inset + text.top, inset + text.bottom)
    }

    private companion object {
        const val ICON_DP = 22f
        const val PADDING_DP = 16
    }
}

/**
 * The battery as a line glyph, its fill as full as the charge: grey like its value, red when
 * low, green when full on the charger.
 */
class BatteryGlyph(context: Context) : Drawable() {
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()
    private var level = 1f
    private var color: Int? = null
    private var tint = Palette.SECONDARY
    private val minSize = context.dp(24)

    fun update(state: BatteryState) {
        level = state.fraction.coerceIn(0f, 1f)
        color = when {
            state.low -> Palette.LOW
            state.charging && level >= 1f -> Palette.FULL
            else -> null
        }
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        val unit = bounds.width() / 24f
        canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(unit, unit)
        stroke.color = tint
        stroke.strokeWidth = 1.5f
        box.set(3f, 7.5f, 19f, 16.5f)
        canvas.drawRoundRect(box, 2f, 2f, stroke)
        canvas.drawLine(21.2f, 10.5f, 21.2f, 13.5f, stroke)
        fill.color = color ?: tint
        // Inside the outline, from the left, as far as the charge goes.
        box.set(5.6f, 10f, 5.6f + 8.8f * level, 14f)
        if (level > 0f) canvas.drawRect(box, fill)
        canvas.restore()
    }

    override fun setTintList(tint: ColorStateList?) {
        this.tint = tint?.getColorForState(state, tint.defaultColor) ?: Palette.SECONDARY
        invalidateSelf()
    }

    override fun getIntrinsicWidth() = minSize

    override fun getIntrinsicHeight() = minSize

    override fun setAlpha(alpha: Int) {
        stroke.alpha = alpha
        fill.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        stroke.colorFilter = colorFilter
        fill.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
