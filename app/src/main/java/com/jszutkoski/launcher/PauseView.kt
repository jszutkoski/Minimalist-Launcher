package com.jszutkoski.launcher

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout

/**
 * A breath before a distracting app: "Breathe in", "Breathe out", then the choice. The way in
 * only appears after the pause; "Not now" is there from the start. Shows today's time in the
 * app, and how many times it was opened, when usage access is on. Past the app's daily limit
 * the pause is three times as long, says so, and puts "Not now" first.
 */
class PauseView(context: Context, private val onCancel: () -> Unit) :
    FrameLayout(context) {
    private val title = context.text("", 40f, Palette.TEXT, 200)
    private val subtitle = context.text("", 17f, Palette.SECONDARY, 400)
    private val usage = context.text("", 15f, Palette.TERTIARY, 400)
    private val progress = BatteryBar(context)
    private val open = context.text("", 20f, Palette.TEXT, 400)
    private val notNow = context.text(context.getString(R.string.pause_not_now), 17f, Palette.SECONDARY, 400)
    /** What "Open" does once the pause is over: the app, or one of its shortcuts. */
    private var pending: (() -> Unit)? = null
    private var timer: ValueAnimator? = null
    private val column = LinearLayout(context)
    private var notNowFirst = false
    private var textScale = 1f
    /** Breaths taken so far in this pause, each half of [PAUSE_MS]. */
    private var breaths = 0
    private var length = PAUSE_MS

    // The pause itself runs on the clock, not on the animation: with animations off in Android's
    // settings an animator ends at once, and "Open" would be there straight away.
    private val clock = Handler(Looper.getMainLooper())
    // In, out, in, out: one breath each half of the short pause, for as long as the pause lasts.
    private val breathe: Runnable = object : Runnable {
        override fun run() {
            breaths++
            title.text = context.getString(if (breaths % 2 == 1) R.string.breathe_out else R.string.breathe_in)
            if ((breaths + 1) * BREATH_MS < length) clock.postDelayed(this, BREATH_MS)
        }
    }
    private val ready = Runnable {
        title.text = context.getString(R.string.pause_still)
        open.alpha = 0f
        open.visibility = VISIBLE
        open.animate().alpha(1f).setDuration(250).start()
    }

    val isShowing get() = visibility == VISIBLE

    init {
        setBackgroundColor(Palette.BACKGROUND)
        visibility = GONE
        alpha = 0f

        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        column.apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(title, LinearLayout.LayoutParams(wrap, wrap))
            addView(subtitle, LinearLayout.LayoutParams(wrap, wrap).apply { topMargin = dp(10) })
            addView(usage, LinearLayout.LayoutParams(wrap, wrap).apply { topMargin = dp(4) })
            addView(progress, LinearLayout.LayoutParams(dp(160), wrap).apply { topMargin = dp(36) })
        }
        placeChoices(notNowFirst = false)
        open.setPadding(dp(16), dp(12), dp(16), dp(12))
        notNow.setPadding(dp(16), dp(12), dp(16), dp(12))
        open.setOnClickListener { pending?.invoke() }
        notNow.setOnClickListener { onCancel() }
        // "Breathe in", "Breathe out" and the way in are read out as they come.
        title.accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
        addView(column, LayoutParams(wrap, wrap, Gravity.CENTER))
    }

    /**
     * The breath before [app]; [pastLimit], as "30 min", when the app is past its daily limit,
     * for the long pause.
     */
    fun show(app: App, usageToday: String?, pastLimit: String? = null, onOpen: () -> Unit) {
        pending = onOpen
        length = if (pastLimit != null) LONG_PAUSE_MS else PAUSE_MS
        breaths = 0
        title.text = context.getString(R.string.breathe_in)
        subtitle.text = if (pastLimit != null) {
            context.getString(R.string.pause_past_limit, pastLimit, app.label)
        } else {
            context.getString(R.string.pause_before, app.label)
        }
        setUsage(usageToday)
        open.text = context.getString(R.string.pause_open, app.label)
        open.visibility = INVISIBLE
        placeChoices(notNowFirst = pastLimit != null)
        progress.update(0f, charging = false, low = false)

        animate().cancel()
        visibility = VISIBLE
        alpha = 0f
        animate().alpha(1f).setDuration(250).start()

        hideBehind(true)
        clock.removeCallbacks(breathe)
        clock.removeCallbacks(ready)
        clock.postDelayed(breathe, BREATH_MS)
        clock.postDelayed(ready, length)
        // The line only shows how far along it is.
        timer?.cancel()
        timer = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = length
            interpolator = LinearInterpolator()
            addUpdateListener { progress.update(it.animatedFraction, charging = false, low = false) }
            start()
        }
    }

    /**
     * The way in and "Not now" under the line: the way in first and larger, or, past the daily
     * limit, "Not now" first and larger, the way in quieter after it.
     */
    private fun placeChoices(notNowFirst: Boolean) {
        column.removeView(open)
        column.removeView(notNow)
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        val (first, second) = if (notNowFirst) notNow to open else open to notNow
        column.addView(first, LinearLayout.LayoutParams(wrap, wrap).apply { topMargin = dp(40) })
        column.addView(second, LinearLayout.LayoutParams(wrap, wrap).apply { topMargin = dp(8) })
        this.notNowFirst = notNowFirst
        applyChoiceStyle()
    }

    private fun applyChoiceStyle() {
        val (main, other) = if (notNowFirst) notNow to open else open to notNow
        main.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f * textScale)
        main.setTextColor(pressable(Palette.TEXT))
        other.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f * textScale)
        other.setTextColor(pressable(Palette.SECONDARY))
    }

    /** While the pause shows, TalkBack can't wander to the home screen or the list behind it. */
    private fun hideBehind(hidden: Boolean) {
        val parent = parent as? ViewGroup ?: return
        for (i in 0 until parent.childCount) {
            val view = parent.getChildAt(i)
            if (view === this) continue
            view.importantForAccessibility =
                if (hidden) IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS else IMPORTANT_FOR_ACCESSIBILITY_AUTO
        }
    }

    /** "45 min today, opened 14 times", or nothing. */
    fun setUsage(usageToday: String?) {
        usage.text = usageToday.orEmpty()
        usage.visibility = if (usageToday != null) VISIBLE else GONE
    }

    fun setTextScale(scale: Float) {
        textScale = scale
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40f * scale)
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f * scale)
        usage.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f * scale)
        applyChoiceStyle()
    }

    fun hide(animate: Boolean) {
        pending = null
        timer?.cancel()
        clock.removeCallbacks(breathe)
        clock.removeCallbacks(ready)
        hideBehind(false)
        animate().cancel()
        if (!animate) {
            alpha = 0f
            visibility = GONE
            return
        }
        animate().alpha(0f).setDuration(200).withEndAction { visibility = GONE }.start()
    }

    /** Takes every touch, so nothing on the home screen underneath can be tapped; nothing to click. */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent) = true

    private companion object {
        const val PAUSE_MS = 5000L

        /** Past the app's daily limit. */
        const val LONG_PAUSE_MS = 15_000L

        /** "Breathe in" or "Breathe out", each. */
        const val BREATH_MS = PAUSE_MS / 2
    }
}
