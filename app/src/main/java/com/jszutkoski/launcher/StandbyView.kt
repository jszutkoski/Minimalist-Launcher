package com.jszutkoski.launcher

import android.content.Context
import android.content.res.ColorStateList
import android.text.format.DateFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import java.util.Locale
import kotlin.random.Random

/**
 * A bedside clock: time, battery, date, and the weather and screen time if chosen, dimmed on black. The group drifts
 * to a new spot every minute so no pixel stays lit in the same place. A tap calls [onExit].
 */
class StandbyView(context: Context, private val onExit: () -> Unit) : FrameLayout(context) {
    private val screenTime: TextView = context.text("", 17f, Palette.SECONDARY, 400).apply {
        letterSpacing = 0.02f
        visibility = GONE
    }
    private val weather: TextView = context.text("", 17f, Palette.SECONDARY, 400).apply {
        letterSpacing = 0.02f
        visibility = GONE
    }
    private val content = LinearLayout(context)
    private val waiting = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        visibility = GONE
    }
    private val drift = Runnable { moveContent() }
    private val face = ClockFace(context, pressable = false)
    private val date: TextClock

    init {
        setBackgroundColor(Palette.BACKGROUND)
        // Clickable, so TalkBack offers its double-tap to leave.
        isClickable = true
        visibility = GONE
        alpha = 0f

        val datePattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM")
        date = TextClock(context).apply {
            format12Hour = datePattern
            format24Hour = datePattern
            setTextColor(Palette.SECONDARY)
            typeface = weight(400)
            textSize = 17f
            letterSpacing = 0.02f
        }

        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        content.apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            // Everything at half strength: readable in the dark, easy on the panel.
            alpha = 0.55f
            addView(date, LinearLayout.LayoutParams(wrap, wrap))
            addView(face, LinearLayout.LayoutParams(wrap, wrap))
            addView(weather, LinearLayout.LayoutParams(wrap, wrap))
            addView(screenTime, LinearLayout.LayoutParams(wrap, wrap))
            addView(waiting, LinearLayout.LayoutParams(wrap, wrap))
        }
        setLook(ClockLook())
        addView(content, LayoutParams(wrap, wrap, Gravity.CENTER))
    }

    /** The clock and battery chosen in settings, kept clear of the screen's sides as it drifts. */
    fun setLook(look: ClockLook) {
        face.apply(
            look,
            digitsSp = 106f,
            analogDp = 240f,
            labelSp = 13f,
            gravity = Gravity.CENTER_HORIZONTAL,
            maxWidth = resources.displayMetrics.widthPixels - 2 * dp(SIDE_MARGIN_DP),
            gap = dp(GAP_DP),
        )
        content.removeView(date)
        content.addView(date, content.indexOfChild(face) + if (look.date == DatePosition.ABOVE) 0 else 1)
        date.visibility = if (look.date == DatePosition.HIDDEN) GONE else VISIBLE
        space()
    }

    /**
     * One gap between each shown line, glyph to glyph, and none at the ends: the glyphs
     * themselves are what sits in the middle of the screen.
     */
    private fun space() {
        val lines = (0 until content.childCount).map { i ->
            when (val view = content.getChildAt(i)) {
                face -> face.spaced()
                is TextView -> spacedText(view)
                else -> Spaced(view, 0f, 0f)
            }
        }
        spaceGlyphs(lines, dp(GAP_DP), trimEnds = true)
    }

    /**
     * A line glyph for each app, or kind of app, with notifications waiting, each described by
     * [describe]; the row goes when there is nothing.
     */
    fun showWaiting(icons: List<AppGlyphs.Waiting>, describe: (AppGlyphs.Waiting) -> String) {
        waiting.removeAllViews()
        icons.forEachIndexed { index, icon ->
            val glyph = ImageView(context).apply {
                setImageResource(icon.drawable)
                imageTintList = ColorStateList.valueOf(Palette.TEXT)
                contentDescription = describe(icon)
            }
            // The gap only between two glyphs: a lone one sits exactly under the centre.
            val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
            waiting.addView(glyph, LinearLayout.LayoutParams(wrap, wrap).apply { if (index > 0) leftMargin = dp(22) })
        }
        waiting.visibility = if (icons.isEmpty()) GONE else VISIBLE
        space()
    }

    fun updateBattery(state: BatteryState) = face.showBattery(state, number = false)

    /** "18° Drizzle" under the clock, or nothing. */
    fun showWeather(label: String?) {
        weather.text = label.orEmpty()
        weather.visibility = if (label == null) GONE else VISIBLE
        space()
    }

    /** "2 h 5 min today" under the weather, or nothing. */
    fun showScreenTime(label: String?) {
        screenTime.text = label.orEmpty()
        screenTime.visibility = if (label == null) GONE else VISIBLE
        space()
    }

    fun show() {
        if (visibility == VISIBLE) return
        animate().cancel()
        visibility = VISIBLE
        moveContent()
        animate().alpha(1f).setDuration(600).start()
    }

    /**
     * A few pixels from the centre, somewhere new once a minute: enough that no pixel stays lit
     * all night, too little to ever look off centre.
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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
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
    }
}
