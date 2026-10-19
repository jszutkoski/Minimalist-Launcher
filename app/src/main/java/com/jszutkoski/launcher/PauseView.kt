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
 * app when usage access is on.
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

    // The pause itself runs on the clock, not on the animation: with animations off in Android's
    // settings an animator ends at once, and "Open" would be there straight away.
    private val clock = Handler(Looper.getMainLooper())
    private val breatheOut = Runnable { title.text = context.getString(R.string.breathe_out) }
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
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(title, LinearLayout.LayoutParams(wrap, wrap))
            addView(subtitle, LinearLayout.LayoutParams(wrap, wrap).apply { topMargin = dp(10) })
            addView(usage, LinearLayout.LayoutParams(wrap, wrap).apply { topMargin = dp(4) })
            addView(progress, LinearLayout.LayoutParams(dp(160), wrap).apply { topMargin = dp(36) })
            addView(open, LinearLayout.LayoutParams(wrap, wrap).apply { topMargin = dp(40) })
            addView(notNow, LinearLayout.LayoutParams(wrap, wrap).apply { topMargin = dp(8) })
        }
        open.setPadding(dp(16), dp(12), dp(16), dp(12))
        notNow.setPadding(dp(16), dp(12), dp(16), dp(12))
        open.setOnClickListener { pending?.invoke() }
        notNow.setOnClickListener { onCancel() }
        // "Breathe in", "Breathe out" and the way in are read out as they come.
        title.accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
        addView(column, LayoutParams(wrap, wrap, Gravity.CENTER))
    }

    fun show(app: App, minutesToday: String?, onOpen: () -> Unit) {
        pending = onOpen
        title.text = context.getString(R.string.breathe_in)
        subtitle.text = context.getString(R.string.pause_before, app.label)
        setUsage(minutesToday)
        open.text = context.getString(R.string.pause_open, app.label)
        open.visibility = INVISIBLE
        progress.update(0f, charging = false, low = false)

        animate().cancel()
        visibility = VISIBLE
        alpha = 0f
        animate().alpha(1f).setDuration(250).start()

        hideBehind(true)
        clock.removeCallbacks(breatheOut)
        clock.removeCallbacks(ready)
        clock.postDelayed(breatheOut, PAUSE_MS / 2)
        clock.postDelayed(ready, PAUSE_MS)
        // The line only shows how far along it is.
        timer?.cancel()
        timer = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = PAUSE_MS
            interpolator = LinearInterpolator()
            addUpdateListener { progress.update(it.animatedFraction, charging = false, low = false) }
            start()
        }
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

    fun setUsage(minutesToday: String?) {
        usage.text = minutesToday?.let { context.getString(R.string.pause_usage, it) }.orEmpty()
        usage.visibility = if (minutesToday != null) VISIBLE else GONE
    }

    fun setTextScale(scale: Float) {
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40f * scale)
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f * scale)
        open.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f * scale)
        usage.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f * scale)
        notNow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f * scale)
    }

    fun hide(animate: Boolean) {
        pending = null
        timer?.cancel()
        clock.removeCallbacks(breatheOut)
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
    }
}
