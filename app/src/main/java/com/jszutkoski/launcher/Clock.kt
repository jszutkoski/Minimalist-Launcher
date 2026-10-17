package com.jszutkoski.launcher

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** From the date's baseline to the top of the clock's digits under it. */
const val DATE_GAP_DP = 20

/** From the clock's baseline to the battery line's centre. */
const val BATTERY_GAP_DP = 26

enum class ClockDesign(val label: Int) {
    LINE(R.string.clock_line),
    STACKED(R.string.clock_stacked),
    ANALOG(R.string.clock_analog),
}

/** How heavy the digits are, or the hands of the analog clock. */
enum class ClockWeight(val label: Int, val weight: Int) {
    ULTRA_THIN(R.string.weight_ultra_thin, 100),
    THIN(R.string.weight_thin, 200),
    LIGHT(R.string.weight_light, 300),
    MEDIUM(R.string.weight_medium, 500),
    BOLD(R.string.weight_bold, 700),
}

enum class SizeStep(val label: Int, val scale: Float) {
    SMALL(R.string.text_small, 0.8f),
    DEFAULT(R.string.text_default, 1f),
    LARGE(R.string.text_large, 1.2f),
}

enum class BatteryDesign(val label: Int) {
    LINE(R.string.battery_line),
    RING(R.string.battery_ring),
}

/** How thick the battery's line or ring is drawn. */
enum class LineWeight(val label: Int, val dp: Float) {
    FINE(R.string.line_fine, 2f),
    MEDIUM(R.string.line_medium, 3f),
    THICK(R.string.line_thick, 5f),
}

/** The clock and battery as chosen in settings, shared by the home screen and the bedside clock. */
data class ClockLook(
    val design: ClockDesign = ClockDesign.LINE,
    val weight: ClockWeight = ClockWeight.THIN,
    val size: SizeStep = SizeStep.DEFAULT,
    val battery: BatteryDesign = BatteryDesign.LINE,
    val batteryWeight: LineWeight = LineWeight.MEDIUM,
    val batterySize: SizeStep = SizeStep.DEFAULT,
) {
    companion object {
        fun from(prefs: Prefs) = ClockLook(
            prefs.clockDesign,
            prefs.clockWeight,
            prefs.clockSize,
            prefs.batteryDesign,
            prefs.batteryWeight,
            prefs.batterySize,
        )
    }
}

/**
 * Roboto Flex, cut down to digits and Latin letters, with its whole weight axis: the phone's
 * own font may have only a few weights, or none at all past regular and bold.
 */
object ClockFont {
    private val cache = HashMap<Int, Typeface>()

    fun at(context: Context, weight: Int): Typeface = cache.getOrPut(weight) {
        // Anything wrong with the file: the phone's own sans-serif at the nearest weight.
        runCatching {
            Typeface.Builder(context.assets, "fonts/clock.ttf")
                .setFontVariationSettings("'wght' $weight")
                .setFallback("sans-serif")
                .setWeight(weight)
                .build()
        }.getOrNull() ?: weight(weight)
    }
}

/**
 * The time and the battery under it, in the chosen design: digits on one line, hours over
 * minutes, or an analog face; then the battery as a line as wide as the time, or a ring. The
 * date sits just above, outside it.
 */
class ClockFace(context: Context, pressable: Boolean) : LinearLayout(context) {
    private val color = if (pressable) pressable(Palette.TEXT) else ColorStateList.valueOf(Palette.TEXT)
    val digital = digits(context, "h:mm", "HH:mm")

    /** Hours over minutes: two lines of their own, so each is spaced from its own glyphs. */
    val stack = LinearLayout(context).apply {
        orientation = VERTICAL
        visibility = GONE
    }
    private val hours = digits(context, "hh", "HH").apply { isDuplicateParentStateEnabled = true }
    private val minutes = digits(context, "mm", "mm").apply { isDuplicateParentStateEnabled = true }
    val analog = AnalogClock(context, pressable).apply { visibility = GONE }

    // Room for the charging bolt from the start, so plugging in never shifts the clock.
    val battery = BatteryBar(context).apply { roomForMarker = true }

    init {
        orientation = VERTICAL
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        addView(digital, LayoutParams(wrap, wrap))
        stack.addView(hours, LayoutParams(wrap, wrap))
        stack.addView(minutes, LayoutParams(wrap, wrap))
        addView(stack, LayoutParams(wrap, wrap))
        addView(analog, LayoutParams(wrap, wrap))
        addView(battery, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, wrap))
    }

    private fun digits(context: Context, format12: String, format24: String) = TextClock(context).apply {
        format12Hour = format12
        format24Hour = format24
        includeFontPadding = false
        isSingleLine = true
        setTextColor(color)
    }

    fun setOnTimeClick(action: () -> Unit) {
        digital.setOnClickListener { action() }
        stack.setOnClickListener { action() }
        analog.setOnClickListener { action() }
    }

    /**
     * Shows [look] with the digits at [digitsSp] (the analog face as tall as [analogDp]) and
     * the battery's number at [labelSp], lined up by [gravity], never wider than [maxWidth]
     * pixels. The clock's top hangs [dateGap] pixels under the baseline of the [date] above it,
     * and the battery sits [batteryGap] pixels under the clock: both measured from the glyphs,
     * not the text boxes, whose built-in ascent and descent would otherwise skew the spacing.
     */
    fun apply(
        look: ClockLook,
        digitsSp: Float,
        analogDp: Float,
        labelSp: Float,
        gravity: Int,
        maxWidth: Int,
        date: TextView,
        dateGap: Int,
        batteryGap: Int,
    ) {
        val face = ClockFont.at(context, look.weight.weight)
        digital.visibility = if (look.design == ClockDesign.LINE) VISIBLE else GONE
        stack.visibility = if (look.design == ClockDesign.STACKED) VISIBLE else GONE
        analog.visibility = if (look.design == ClockDesign.ANALOG) VISIBLE else GONE
        val sizeSp = digitsSp * look.size.scale
        // The view that starts the clock, the one whose glyphs are its top, and its last line.
        val (first, top, last) = when (look.design) {
            ClockDesign.LINE -> {
                showDigits(digital, face, sizeSp, "00:00", maxWidth)
                Triple(digital, digital, digital)
            }
            ClockDesign.STACKED -> {
                showDigits(hours, face, sizeSp, "00", maxWidth)
                showDigits(minutes, face, sizeSp, "00", maxWidth)
                // The minutes' digits an eighth of their height under the hours', not a whole
                // line's worth of empty ascent and descent.
                val natural = hours.paint.fontMetrics.descent + glyphInset(minutes)
                (minutes.layoutParams as LayoutParams).topMargin = (digitHeight(hours) * STACKED_GAP - natural).roundToInt()
                Triple(stack, hours, minutes)
            }
            ClockDesign.ANALOG -> {
                analog.diameter = minOf(context.dp(analogDp * look.size.scale).roundToInt(), maxWidth)
                analog.stroke = context.dp(AnalogClock.strokeDp(look.weight.weight))
                Triple(analog, analog, analog)
            }
        }
        for (view in listOf(digital, stack, hours, minutes, analog)) (view.layoutParams as LayoutParams).gravity = gravity

        // Under the date: from its baseline, past its descent and padding, to the clock's glyphs.
        val underDate = date.paint.fontMetrics.descent + date.paddingBottom
        for (view in listOf(digital, stack, analog)) (view.layoutParams as LayoutParams).topMargin = 0
        (first.layoutParams as LayoutParams).topMargin = (dateGap - underDate - glyphInset(top)).roundToInt()

        battery.design = look.battery
        battery.lineWidth = context.dp(look.batteryWeight.dp * if (look.battery == BatteryDesign.RING) RING_STROKE else 1f)
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        (battery.layoutParams as LayoutParams).apply {
            width = if (look.battery == BatteryDesign.RING) wrap else ViewGroup.LayoutParams.MATCH_PARENT
            this.gravity = gravity
        }
        if (look.battery == BatteryDesign.RING) {
            val ring = context.dp(RING_DP * labelSp / LABEL_SP * look.batterySize.scale).roundToInt()
            battery.ringDiameter = ring
            battery.setLabelSizePx(ring * RING_LABEL)
        } else {
            battery.setLabelSize(labelSp * look.batterySize.scale)
        }
        // The last line's descent taken away, so the gap starts at the digits' baseline.
        (last.layoutParams as LayoutParams).bottomMargin =
            if (last is TextView) -(last.paint.fontMetrics.descent + last.paddingBottom).roundToInt() else 0
        // The line's centre [batteryGap] under the digits; a ring's top a little nearer.
        (battery.layoutParams as LayoutParams).topMargin = if (look.battery == BatteryDesign.RING) {
            (batteryGap * RING_GAP).roundToInt()
        } else {
            batteryGap - battery.preferredHeight / 2
        }
        requestLayout()
    }

    /** From the top of [view]'s box to the top of its digits; nothing for the analog face. */
    private fun glyphInset(view: View): Float {
        if (view !is TextView) return 0f
        val boxTop = if (view.includeFontPadding) view.paint.fontMetrics.top else view.paint.fontMetrics.ascent
        return view.paddingTop + (-digitHeight(view) - boxTop)
    }

    /** How tall [view]'s digits stand above the baseline. */
    private fun digitHeight(view: TextView) = -Rect().also { view.paint.getTextBounds("0", 0, 1, it) }.top.toFloat()

    /** [view]'s digits in [face] at [sizeSp], or smaller when [widest] would not fit in [maxWidth]. */
    private fun showDigits(view: TextView, face: Typeface, sizeSp: Float, widest: String, maxWidth: Int) {
        view.typeface = face
        view.letterSpacing = -0.02f
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        val width = view.paint.measureText(widest) * FIT_MARGIN + view.paddingLeft + view.paddingRight
        if (maxWidth > 0 && width > maxWidth) view.setTextSize(TypedValue.COMPLEX_UNIT_PX, view.textSize * maxWidth / width)
    }

    private companion object {
        /** A little room to spare, for digits that are not quite all one width. */
        const val FIT_MARGIN = 1.04f

        /** Between the hours and the minutes, against the digits' height. */
        const val STACKED_GAP = 0.12f

        /** The ring at the default text size; it grows with the battery's number. */
        const val RING_DP = 50f
        const val LABEL_SP = 13f * 1.1f

        /** A ring's top is nearer the digits than a line's centre would be. */
        const val RING_GAP = 0.7f

        /** The ring's stroke is drawn a little heavier than the line's, as it is shorter. */
        const val RING_STROKE = 1.3f

        /** The number inside, against the ring's diameter. */
        const val RING_LABEL = 0.3f
    }
}

/**
 * A plain analog face: a faint ring, twelve marks and two hands, redrawn on the minute.
 * The [stroke] follows the chosen weight.
 */
class AnalogClock(context: Context, private val pressable: Boolean) : View(context) {
    var diameter = context.dp(180)
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
            invalidate()
        }

    var stroke = context.dp(2f)
        set(value) {
            field = value
            invalidate()
        }

    private val dialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Palette.TRACK
    }
    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Palette.TERTIARY
    }
    private val handPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val tick = Runnable { onMinute() }
    private var shown = false

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) = setMeasuredDimension(diameter, diameter)

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(cx, cy) - stroke
        dialPaint.strokeWidth = stroke * 0.6f
        canvas.drawCircle(cx, cy, radius, dialPaint)
        markPaint.strokeWidth = stroke * 0.8f
        for (hour in 0 until 12) {
            val angle = Math.toRadians(hour * 30.0)
            // The quarters a little longer, as on a watch.
            val inner = radius * if (hour % 3 == 0) 0.82f else 0.88f
            val outer = radius * 0.93f
            canvas.drawLine(
                cx + inner * sin(angle).toFloat(),
                cy - inner * cos(angle).toFloat(),
                cx + outer * sin(angle).toFloat(),
                cy - outer * cos(angle).toFloat(),
                markPaint,
            )
        }
        val now = Calendar.getInstance()
        val minutes = now.get(Calendar.MINUTE)
        val hours = now.get(Calendar.HOUR) + minutes / 60f
        handPaint.color = if (pressable && isPressed) Palette.SECONDARY else Palette.TEXT
        handPaint.strokeWidth = stroke * 1.4f
        hand(canvas, cx, cy, hours * 30.0, radius * 0.5f)
        handPaint.strokeWidth = stroke
        hand(canvas, cx, cy, minutes * 6.0, radius * 0.76f)
    }

    private fun hand(canvas: Canvas, cx: Float, cy: Float, degrees: Double, length: Float) {
        val angle = Math.toRadians(degrees)
        canvas.drawLine(cx, cy, cx + length * sin(angle).toFloat(), cy - length * cos(angle).toFloat(), handPaint)
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        if (pressable) invalidate()
    }

    /** Redrawn, read out to TalkBack, and due again at the start of the next minute. */
    private fun onMinute() {
        contentDescription = DateFormat.getTimeFormat(context).format(Calendar.getInstance().time)
        invalidate()
        removeCallbacks(tick)
        if (shown) postDelayed(tick, MINUTE_MS - System.currentTimeMillis() % MINUTE_MS + 50)
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        shown = isVisible
        // Back in sight: the time may have moved on, or been changed, meanwhile.
        if (isVisible) onMinute() else removeCallbacks(tick)
    }

    override fun onDetachedFromWindow() {
        shown = false
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    companion object {
        private const val MINUTE_MS = 60_000L

        /** The minute hand's thickness for a font weight: 1dp at the thinnest, 6dp at bold. */
        fun strokeDp(weight: Int) = 1f + (weight - 100) / 600f * 5f
    }
}
