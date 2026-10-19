package com.jszutkoski.launcher

import android.content.Context
import android.content.res.ColorStateList
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * The two bedside screens, each laid out once and for all: the clock's centre at a set height,
 * then the weather and the notification icons at set gaps under it, glyph to glyph.
 */
enum class StandbyScreen(
    val label: Int,
    val look: ClockLook,
    val digitsSp: Float,
    val analogDp: Float,
    /** Where the middle of the clock sits, as a share of the screen's height from the top. */
    val clockCentre: Float,
    /** From the clock's glyphs to the weather's capitals. */
    val weatherGapDp: Int,
    /** From the weather's baseline to the notification icons (from the clock without weather). */
    val iconsGapDp: Int,
    val weatherSp: Float,
    /** The sky's glyph before the temperature, this big; none at 0. */
    val skyIconDp: Int,
    /** The notification glyphs' size. */
    val glyphDp: Int,
) {
    /** Large thin digits with no battery line, a little above the middle. */
    DIGITAL(
        R.string.clock_digital,
        ClockLook(design = ClockDesign.DIGITAL, weight = ClockWeight.ULTRA_THIN, date = DatePosition.HIDDEN),
        digitsSp = 112f,
        analogDp = 0f,
        clockCentre = 0.38f,
        weatherGapDp = 72,
        iconsGapDp = 72,
        weatherSp = 17f,
        skyIconDp = 34,
        glyphDp = 30,
    ),

    /** The outlined face with its battery in the ring, a little above the middle. */
    ANALOG(
        R.string.clock_analog,
        ClockLook(design = ClockDesign.ANALOG, weight = ClockWeight.THIN, date = DatePosition.HIDDEN),
        digitsSp = 0f,
        analogDp = 280f,
        clockCentre = 0.40f,
        weatherGapDp = 80,
        iconsGapDp = 70,
        weatherSp = 15f,
        skyIconDp = 30,
        glyphDp = 26,
    ),
}

/**
 * A bedside clock on black: the clock, the weather when it is on, and a glyph for each app with
 * notifications waiting, as [StandbyScreen] lays them out. The group drifts a few pixels every
 * minute so no pixel stays lit in the same place. A tap calls [onExit].
 */
class StandbyView(context: Context, private val onExit: () -> Unit) : FrameLayout(context) {
    private val weather = WeatherRow(context)
    private val content = LinearLayout(context)
    private val waiting = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        visibility = GONE
    }
    private val drift = Runnable { moveContent() }
    private val face = ClockFace(context, pressable = false)
    private var screen = StandbyScreen.DIGITAL
    private var icons: List<AppGlyphs.Waiting> = emptyList()
    private var describe: (AppGlyphs.Waiting) -> String = { "" }

    init {
        setBackgroundColor(Palette.BACKGROUND)
        // Clickable, so TalkBack offers its double-tap to leave.
        isClickable = true
        visibility = GONE
        alpha = 0f

        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        content.apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(face, LinearLayout.LayoutParams(wrap, wrap))
            addView(weather, LinearLayout.LayoutParams(wrap, wrap))
            addView(waiting, LinearLayout.LayoutParams(wrap, wrap))
        }
        addView(content, LayoutParams(wrap, wrap))
        setScreen(screen)
    }

    /** One of the two screens. */
    fun setScreen(screen: StandbyScreen) {
        this.screen = screen
        face.apply(
            screen.look,
            digitsSp = screen.digitsSp,
            analogDp = screen.analogDp,
            labelSp = 13f,
            gravity = Gravity.CENTER_HORIZONTAL,
            maxWidth = resources.displayMetrics.widthPixels - 2 * dp(SIDE_MARGIN_DP),
            gap = dp(GAP_DP),
            batteryLine = false,
        )
        face.analog.outlined = true
        weather.style(screen.weatherSp, screen.skyIconDp)
        layoutIcons()
        space()
    }

    /** The gaps of the chosen screen, glyph to glyph, with the clock's glyphs at the top of the group. */
    private fun space() {
        val weatherShown = weather.visibility != GONE
        val iconsShown = waiting.visibility != GONE
        val clock = face.spaced()
        (face.layoutParams as LinearLayout.LayoutParams).apply {
            topMargin = -clock.top.roundToInt()
            // Trimmed to the glyphs only where something follows: the last line keeps its
            // descent (the tail of a y, the bottom of the sky's glyph), or the group cuts it off.
            bottomMargin = if (weatherShown || iconsShown) -clock.bottom.roundToInt() else 0
        }
        val sky = weather.spaced()
        (weather.layoutParams as LinearLayout.LayoutParams).apply {
            topMargin = (dp(screen.weatherGapDp) - sky.top).roundToInt()
            bottomMargin = if (iconsShown) -sky.bottom.roundToInt() else 0
        }
        (waiting.layoutParams as LinearLayout.LayoutParams).topMargin =
            dp(if (weatherShown) screen.iconsGapDp else screen.weatherGapDp) - glyphInset()
        content.requestLayout()
    }

    /** The time read again now, rather than at the clock's own next tick. */
    fun refreshClock() = face.refresh()

    /** Where the glyphs of the clock itself sit, from the top of [face]: their middle. */
    private fun clockMiddle(): Float {
        val clock = face.spaced()
        return (face.measuredHeight - clock.top - clock.bottom) / 2f
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        // The group hangs from the clock: its middle at the screen's chosen height, whatever
        // is shown under it, so the clock never moves when the weather or a message comes.
        val width = right - left
        val height = bottom - top
        val x = (width - content.measuredWidth) / 2
        val y = (height * screen.clockCentre - clockMiddle()).roundToInt()
        content.layout(x, y, x + content.measuredWidth, y + content.measuredHeight)
    }

    /**
     * A line glyph for each app, or kind of app, with notifications waiting, each described by
     * [describe]; the row goes when there is nothing.
     */
    fun showWaiting(icons: List<AppGlyphs.Waiting>, describe: (AppGlyphs.Waiting) -> String) {
        this.icons = icons
        this.describe = describe
        layoutIcons()
        space()
    }

    /** The empty edge inside a notification glyph, around its lines: an eighth of it. */
    private fun glyphInset() = dp(screen.glyphDp) / 8

    /**
     * The icons at an even gap, about twice their own width line to line, narrowed when there
     * are too many for the screen.
     */
    private fun layoutIcons() {
        waiting.removeAllViews()
        val room = resources.displayMetrics.widthPixels - 2 * dp(SIDE_MARGIN_DP)
        val glyph = dp(screen.glyphDp)
        val wanted = dp(ICON_GAP_DP) - 2 * glyphInset()
        val gap = if (icons.size < 2) 0 else minOf(wanted, (room - icons.size * glyph) / (icons.size - 1))
        icons.forEachIndexed { index, icon ->
            val view = ImageView(context).apply {
                setImageResource(icon.drawable)
                imageTintList = ColorStateList.valueOf(Palette.TEXT)
                contentDescription = describe(icon)
            }
            // The gap only between two glyphs: a lone one sits exactly under the centre.
            waiting.addView(view, LinearLayout.LayoutParams(glyph, glyph).apply { if (index > 0) leftMargin = gap.coerceAtLeast(0) })
        }
        waiting.visibility = if (icons.isEmpty()) GONE else VISIBLE
    }

    fun updateBattery(state: BatteryState) = face.showBattery(state, number = false)

    /** "18° Drizzle" under the clock, or nothing. */
    fun showWeather(now: Weather.Now?) {
        weather.show(now)
        space()
    }

    fun show() {
        if (visibility == VISIBLE) return
        animate().cancel()
        visibility = VISIBLE
        moveContent()
        animate().alpha(1f).setDuration(600).start()
    }

    /** Shown at once and kept still: a picture of the screen, for settings. */
    fun showStill() {
        visibility = VISIBLE
        alpha = 1f
    }

    /**
     * A few pixels from its place, somewhere new once a minute: enough that no pixel stays lit
     * all night, too little to ever look moved.
     */
    private fun moveContent() {
        val rangeX = dp(DRIFT_DP).toFloat()
        val rangeY = dp(DRIFT_DP).toFloat()
        content.animate()
            .translationX(Random.nextFloat() * 2 * rangeX - rangeX)
            .translationY(Random.nextFloat() * 2 * rangeY - rangeY)
            .setDuration(if (content.translationX == 0f && content.translationY == 0f) 0 else 1200)
            .start()
        removeCallbacks(drift)
        postDelayed(drift, 60_000)
    }

    /** Touches that began before this moment, on the uptime clock, are not taps. */
    private var ignoreBefore = 0L
    private var tapping = false

    /**
     * The next [ms] of touches are not taps: a double-tap that wakes the screen can land its
     * last touch here, which would go straight on to Android's unlock instead of showing standby.
     */
    fun ignoreTaps(ms: Long) {
        ignoreBefore = SystemClock.uptimeMillis() + ms
        tapping = false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> tapping = event.downTime >= ignoreBefore
            // Only a touch that began here: never the end of one that woke the screen.
            MotionEvent.ACTION_UP -> if (tapping) {
                tapping = false
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> tapping = false
        }
        return true
    }

    /** A tap anywhere, or TalkBack's double-tap: leave, through the unlock when it is needed. */
    override fun performClick(): Boolean {
        super.performClick()
        onExit()
        return true
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(drift)
        super.onDetachedFromWindow()
    }

    private companion object {
        const val DRIFT_DP = 6

        /** Between two notification glyphs, line to line. */
        const val ICON_GAP_DP = 48
    }
}

/**
 * "☁ 11° Mostly clear": the sky's glyph, the temperature bright and the
 * sky in words dimmer, on one line; gone when there is no reading.
 */
class WeatherRow(context: Context) : LinearLayout(context) {
    private val icon = ImageView(context).apply {
        imageTintList = ColorStateList.valueOf(Palette.TEXT)
        visibility = GONE
        // The temperature and the words say it all.
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val degrees: TextView = context.text("", 16f, Palette.TEXT, 500).apply { includeFontPadding = false }
    private val sky: TextView = context.text("", 16f, Palette.SECONDARY, 400).apply {
        includeFontPadding = false
        letterSpacing = 0.02f
    }
    private var iconSize = 0

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        visibility = GONE
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        addView(icon, LayoutParams(0, 0))
        addView(degrees, LayoutParams(wrap, wrap))
        addView(sky, LayoutParams(wrap, wrap))
    }

    /** Text at [sizeSp], and the sky's glyph [iconDp] across before it, or none at 0. */
    fun style(sizeSp: Float, iconDp: Int) {
        degrees.textSize = sizeSp
        sky.textSize = sizeSp
        iconSize = context.dp(iconDp)
        (icon.layoutParams as LayoutParams).apply {
            width = iconSize
            height = iconSize
            // The glyph's lines end a little inside its box.
            rightMargin = context.dp(sizeSp * 1.1f).roundToInt() - iconSize / 16
        }
        // About a space and a half between the number and the words.
        (sky.layoutParams as LayoutParams).leftMargin = context.dp(sizeSp * 0.75f).roundToInt()
        icon.visibility = if (iconSize > 0 && degrees.text.isNotEmpty()) VISIBLE else GONE
        requestLayout()
    }

    fun show(now: Weather.Now?) {
        if (now == null) {
            degrees.text = ""
            icon.visibility = GONE
            visibility = GONE
            return
        }
        degrees.text = now.degrees
        val words = now.describe(context)
        sky.text = words.orEmpty()
        sky.visibility = if (words == null) GONE else VISIBLE
        icon.setImageResource(now.icon)
        icon.visibility = if (iconSize > 0) VISIBLE else GONE
        visibility = VISIBLE
    }

    /**
     * The row as a [Spaced] element, from the temperature's capitals to its baseline: the text
     * box sits in the middle of the row when the glyph is taller.
     */
    fun spaced(): Spaced {
        val text = spacedText(degrees, this)
        val metrics = degrees.paint.fontMetrics
        val textHeight = metrics.descent - metrics.ascent
        val rowHeight = maxOf(textHeight, if (icon.visibility == VISIBLE) iconSize.toFloat() else 0f)
        val inset = (rowHeight - textHeight) / 2f
        return Spaced(this, inset + text.top, inset + text.bottom)
    }
}
