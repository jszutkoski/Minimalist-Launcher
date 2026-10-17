package com.jszutkoski.launcher

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import java.text.NumberFormat
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** From the date's baseline to the top of the clock under it. */
const val DATE_GAP_DP = 20

/** From the clock's baseline to the battery line's centre, and from there to the date below. */
const val BATTERY_GAP_DP = 26

enum class ClockDesign(val label: Int) {
    /** The time on one line, the battery as a line under it. */
    DIGITAL(R.string.clock_digital),

    /** A face with hands, the battery as an arc along its right-hand edge. */
    ANALOG(R.string.clock_analog),
}

/** How heavy the clock is drawn: the digits and the battery line, or the hands and marks. */
enum class ClockWeight(val label: Int, val weight: Int, val lineDp: Float) {
    ULTRA_THIN(R.string.weight_ultra_thin, 100, 2f),
    THIN(R.string.weight_thin, 200, 3f),
    LIGHT(R.string.weight_light, 300, 3.5f),
    MEDIUM(R.string.weight_medium, 500, 4.5f),
    BOLD(R.string.weight_bold, 700, 6f),
}

enum class SizeStep(val label: Int, val scale: Float) {
    SMALL(R.string.text_small, 0.8f),
    DEFAULT(R.string.text_default, 1f),
    LARGE(R.string.text_large, 1.2f),
}

enum class DatePosition(val label: Int) {
    ABOVE(R.string.date_above),
    BELOW(R.string.date_below),
    HIDDEN(R.string.date_hidden),
}

/** The two screens with a clock, each set up on its own. */
enum class ClockScreen(val label: Int, val prefix: String) {
    HOME(R.string.clock_home, ""),
    STANDBY(R.string.clock_standby, "standby_"),
}

/** The clock as chosen in settings for one screen. */
data class ClockLook(
    val design: ClockDesign = ClockDesign.DIGITAL,
    val weight: ClockWeight = ClockWeight.THIN,
    val size: SizeStep = SizeStep.DEFAULT,
    val date: DatePosition = DatePosition.ABOVE,
)

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
 * The time and the battery as one block, in the chosen design: digits with the battery line
 * under them, exactly as wide as the time, or an analog face with the battery set into it.
 * The date sits outside it, above or below, and is spaced from the glyphs.
 */
class ClockFace(context: Context, pressable: Boolean) : LinearLayout(context) {
    val digital = TextClock(context).apply {
        format12Hour = "h:mm"
        format24Hour = "HH:mm"
        includeFontPadding = false
        isSingleLine = true
        setTextColor(if (pressable) pressable(Palette.TEXT) else ColorStateList.valueOf(Palette.TEXT))
    }

    // Room for the charging bolt from the start, so plugging in never shifts the clock.
    val battery = BatteryBar(context).apply { roomForMarker = true }
    val analog = AnalogClock(context, pressable).apply { visibility = GONE }

    init {
        orientation = VERTICAL
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        addView(digital, LayoutParams(wrap, wrap))
        addView(battery, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, wrap))
        addView(analog, LayoutParams(wrap, wrap))
    }

    fun setOnTimeClick(action: () -> Unit) {
        digital.setOnClickListener { action() }
        analog.setOnClickListener { action() }
    }

    /** The charge in both designs; [number] sets the percentage into the line as well. */
    fun showBattery(state: BatteryState, number: Boolean) {
        battery.update(state.fraction, state.charging, state.low)
        // Just the number: set into the battery line, it needs no percent sign.
        battery.label = if (number) NumberFormat.getIntegerInstance().format((state.fraction * 100).roundToInt()) else null
        battery.contentDescription = NumberFormat.getPercentInstance().format(state.fraction.toDouble())
        analog.updateBattery(state.fraction, state.charging, state.low)
    }

    /**
     * Shows [look] with the digits at [digitsSp] (the analog face [analogDp] across) and the
     * battery's number at [labelSp], lined up by [gravity], never wider than [maxWidth] pixels.
     * Above the clock, the [date]'s baseline sits [dateGap] pixels over the clock's top; below,
     * the battery line sits midway, [gap] pixels from the digits and from the date.
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
        gap: Int,
    ) {
        val digits = look.design == ClockDesign.DIGITAL
        digital.visibility = if (digits) VISIBLE else GONE
        battery.visibility = if (digits) VISIBLE else GONE
        analog.visibility = if (digits) GONE else VISIBLE
        for (view in listOf(digital, battery, analog)) {
            (view.layoutParams as LayoutParams).apply {
                this.gravity = gravity
                topMargin = 0
                bottomMargin = 0
            }
        }
        // From the date's baseline, past its descent and padding, to the top of the box under it.
        val underDate = date.paint.fontMetrics.descent + date.paddingBottom
        if (digits) {
            showDigits(look, digitsSp, maxWidth)
            battery.thickness = context.dp(look.weight.lineDp)
            battery.setLabelSize(labelSp * look.size.scale)
            val half = battery.preferredHeight / 2
            // The line's centre [gap] under the digits' baseline, not under their descent.
            (digital.layoutParams as LayoutParams).bottomMargin =
                -(digital.paint.fontMetrics.descent + digital.paddingBottom).roundToInt()
            (battery.layoutParams as LayoutParams).topMargin = gap - half
            when (look.date) {
                DatePosition.ABOVE -> (digital.layoutParams as LayoutParams).topMargin =
                    (dateGap - underDate - glyphInset(digital)).roundToInt()
                DatePosition.BELOW -> (battery.layoutParams as LayoutParams).bottomMargin =
                    (gap - half - capInset(date)).roundToInt()
                DatePosition.HIDDEN -> Unit
            }
        } else {
            // The line's widened touch area must not linger over the face once the line is gone.
            touchDelegate = null
            analog.diameter = minOf(context.dp(analogDp * look.size.scale).roundToInt(), maxWidth)
            analog.weight = look.weight.weight
            when (look.date) {
                DatePosition.ABOVE -> (analog.layoutParams as LayoutParams).topMargin = (dateGap - underDate).roundToInt()
                DatePosition.BELOW -> (analog.layoutParams as LayoutParams).bottomMargin = (gap - capInset(date)).roundToInt()
                DatePosition.HIDDEN -> Unit
            }
        }
        requestLayout()
    }

    /** The digits in the chosen weight and size, or smaller when they would not fit in [maxWidth]. */
    private fun showDigits(look: ClockLook, digitsSp: Float, maxWidth: Int) {
        digital.typeface = ClockFont.at(context, look.weight.weight)
        digital.letterSpacing = -0.02f
        digital.setTextSize(TypedValue.COMPLEX_UNIT_SP, digitsSp * look.size.scale)
        val width = digital.paint.measureText(WIDEST_TIME) * FIT_MARGIN + digital.paddingLeft + digital.paddingRight
        if (maxWidth > 0 && width > maxWidth) digital.setTextSize(TypedValue.COMPLEX_UNIT_PX, digital.textSize * maxWidth / width)
    }

    /** From the top of [view]'s box to the top of its digits. */
    private fun glyphInset(view: TextView) = view.paddingTop + (glyphTop(view, "0") - boxTop(view))

    /** From the top of [view]'s box to the top of its capitals. */
    private fun capInset(view: TextView) = view.paddingTop + (glyphTop(view, "H") - boxTop(view))

    private fun boxTop(view: TextView) =
        if (view.includeFontPadding) view.paint.fontMetrics.top else view.paint.fontMetrics.ascent

    private fun glyphTop(view: TextView, glyph: String) =
        Rect().also { view.paint.getTextBounds(glyph, 0, glyph.length, it) }.top.toFloat()

    private companion object {
        /** Digits are all one width in the clock's font, so any four stand for every time of day. */
        const val WIDEST_TIME = "00:00"

        /** A little room to spare. */
        const val FIT_MARGIN = 1.04f
    }
}

/**
 * An analog face: a dark dial, the 12, 3, 6 and 9, a mark for every hour and minute, three
 * hands, and the battery as a segmented arc along the right-hand edge, from the 1 to the 5.
 * The battery is green when full on the charger, red when low, and
 * breathes while charging, as the battery line does.
 */
class AnalogClock(context: Context, private val pressable: Boolean) : View(context) {
    var diameter = context.dp(180)
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
            invalidate()
        }

    /** The font weight chosen: the hands, marks and numbers follow it. */
    var weight = 200
        set(value) {
            if (field == value) return
            field = value
            numberPaint.typeface = ClockFont.at(context, value)
            invalidate()
        }

    private val dialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.SURFACE }
    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val handPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = ClockFont.at(context, 200)
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arcBounds = RectF()
    private val digitBounds = Rect()

    private var level = 1f
    private var charging = false
    private var full = false
    private var low = false
    private var breathStart = 0L
    private var shown = false
    private var lastMinute = -1
    private val frame = Runnable { onFrame() }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) = setMeasuredDimension(diameter, diameter)

    fun updateBattery(level: Float, charging: Boolean, low: Boolean) {
        this.level = level.coerceIn(0f, 1f)
        val full = charging && this.level >= 1f
        if (charging != this.charging || full != this.full) breathStart = SystemClock.uptimeMillis()
        this.charging = charging
        this.full = full
        this.low = low
        // Read out again with the new charge.
        lastMinute = -1
        onFrame()
    }

    /** The minute hand's thickness for a font weight: 1dp at the thinnest, 6dp at bold. */
    val stroke get() = context.dp(1f + (weight - 100) / 600f * 5f)

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(cx, cy)
        val s = stroke
        val ink = if (pressable && isPressed) Palette.SECONDARY else Palette.TEXT
        canvas.drawCircle(cx, cy, r, dialPaint)

        // Every minute a short faint mark, every hour a long one.
        for (minute in 0 until 60) {
            val hour = minute % 5 == 0
            markPaint.color = if (hour) ink else Palette.TERTIARY
            markPaint.strokeWidth = if (hour) s * 0.7f else s * 0.4f
            radial(canvas, cx, cy, minute * 6.0, r * if (hour) 0.72f else 0.83f, r * 0.87f, markPaint)
        }
        numberPaint.textSize = r * 0.2f
        numberPaint.color = ink
        for ((hour, label) in listOf(0 to "12", 3 to "3", 6 to "6", 9 to "9")) {
            val angle = Math.toRadians(hour * 30.0)
            numberPaint.getTextBounds(label, 0, label.length, digitBounds)
            val x = cx + r * 0.58f * sin(angle).toFloat()
            val y = cy - r * 0.58f * cos(angle).toFloat() - digitBounds.exactCenterY()
            canvas.drawText(label, x, y, numberPaint)
        }
        drawBattery(canvas, cx, cy, r, s)
        drawHands(canvas, cx, cy, r, s, ink)
    }

    /** Ten segments from the 1 to the 5, lit from the top down. */
    private fun drawBattery(canvas: Canvas, cx: Float, cy: Float, r: Float, s: Float) {
        val color = when {
            low -> Palette.LOW
            full -> Palette.FULL
            else -> Palette.TEXT
        }
        val alpha = if (charging) breathAlpha(breathStart, full) else 255
        val arcRadius = r * 0.94f
        arcPaint.strokeWidth = r * 0.025f + s * 0.5f
        arcBounds.set(cx - arcRadius, cy - arcRadius, cx + arcRadius, cy + arcRadius)
        val segment = ARC_DEGREES / SEGMENTS
        val lit = level * ARC_DEGREES
        for (i in 0 until SEGMENTS) {
            val from = i * segment
            val to = from + segment - SEGMENT_GAP
            // Canvas angles start at 3 o'clock; the clock's at 12.
            arcPaint.color = Palette.TRACK
            canvas.drawArc(arcBounds, ARC_START - 90f + from, to - from, false, arcPaint)
            val end = minOf(to, lit)
            if (end > from) {
                arcPaint.color = color
                arcPaint.alpha = alpha
                canvas.drawArc(arcBounds, ARC_START - 90f + from, end - from, false, arcPaint)
            }
        }
    }

    private fun drawHands(canvas: Canvas, cx: Float, cy: Float, r: Float, s: Float, ink: Int) {
        val now = Calendar.getInstance()
        val seconds = now.get(Calendar.SECOND)
        val minutes = now.get(Calendar.MINUTE) + seconds / 60f
        val hours = now.get(Calendar.HOUR) + minutes / 60f
        handPaint.color = ink
        handPaint.strokeWidth = maxOf(context.dp(2f), s * 1.8f)
        radial(canvas, cx, cy, hours * 30.0, 0f, r * 0.48f, handPaint)
        handPaint.strokeWidth = maxOf(context.dp(1.5f), s * 1.3f)
        radial(canvas, cx, cy, minutes * 6.0, 0f, r * 0.74f, handPaint)
        handPaint.strokeWidth = maxOf(context.dp(0.8f), s * 0.4f)
        // The second hand runs a little past the centre, as on a watch.
        radial(canvas, cx, cy, seconds * 6.0, -r * 0.12f, r * 0.82f, handPaint)
        fillPaint.alpha = 255
        fillPaint.color = Palette.TRACK
        canvas.drawCircle(cx, cy, context.dp(4f) + s, fillPaint)
        fillPaint.color = Palette.BACKGROUND
        canvas.drawCircle(cx, cy, context.dp(1f) + s * 0.3f, fillPaint)
    }

    /** A line along the clock angle [degrees], from [from] to [to] away from the centre. */
    private fun radial(canvas: Canvas, cx: Float, cy: Float, degrees: Double, from: Float, to: Float, paint: Paint) {
        val dx = sin(Math.toRadians(degrees)).toFloat()
        val dy = -cos(Math.toRadians(degrees)).toFloat()
        canvas.drawLine(cx + from * dx, cy + from * dy, cx + to * dx, cy + to * dy, paint)
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        if (pressable) invalidate()
    }

    /**
     * Redrawn once a second for the second hand, or at the breath's steps while charging, and
     * only while in sight. What TalkBack reads changes once a minute, or with the charge.
     */
    private fun onFrame() {
        invalidate()
        removeCallbacks(frame)
        if (!shown) return
        val minute = Calendar.getInstance().get(Calendar.MINUTE)
        if (minute != lastMinute) {
            lastMinute = minute
            // The time and the charge: the arc has no number of its own.
            contentDescription = DateFormat.getTimeFormat(context).format(Calendar.getInstance().time) + ", " +
                NumberFormat.getPercentInstance().format(level.toDouble())
        }
        val delay = if (charging) BREATH_FRAME_MS else SECOND_MS - System.currentTimeMillis() % SECOND_MS + 5
        postDelayed(frame, delay)
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        shown = isVisible
        onFrame()
    }

    override fun onDetachedFromWindow() {
        shown = false
        removeCallbacks(frame)
        super.onDetachedFromWindow()
    }

    private companion object {
        const val SECOND_MS = 1000L

        /** The battery arc: from the 1 to the 5, in ten segments with a sliver between them. */
        const val ARC_START = 30f
        const val ARC_DEGREES = 120f
        const val SEGMENTS = 10
        const val SEGMENT_GAP = 1.6f
    }
}
