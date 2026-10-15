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
 * A bedside clock: time, battery line, date and charge, dimmed on black. The group drifts
 * to a new spot every minute so no pixel stays lit in the same place. A tap calls [onExit].
 */
class StandbyView(context: Context, private val onExit: () -> Unit) : FrameLayout(context) {
    private val battery = BatteryBar(context)
    private val percent: TextView = context.text("", 17f, Palette.SECONDARY, 400)
    private val content = LinearLayout(context)
    private val chat = glyph(R.drawable.ic_chat, "WhatsApp")
    private val mail = glyph(R.drawable.ic_mail, "Email")
    private val waiting = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        visibility = GONE
    }
    private val drift = Runnable { moveContent() }

    init {
        setBackgroundColor(Palette.BACKGROUND)
        visibility = GONE
        alpha = 0f

        val clock = TextClock(context).apply {
            format12Hour = "h:mm"
            format24Hour = "HH:mm"
            setTextColor(Palette.TEXT)
            typeface = weight(200)
            textSize = 96f
            letterSpacing = -0.02f
            includeFontPadding = false
        }
        val datePattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM")
        val date = TextClock(context).apply {
            format12Hour = datePattern
            format24Hour = datePattern
            setTextColor(Palette.SECONDARY)
            typeface = weight(400)
            textSize = 17f
            letterSpacing = 0.02f
        }
        val dot = context.text("·", 17f, Palette.TERTIARY, 400).apply { setPadding(dp(10), 0, dp(10), 0) }
        percent.letterSpacing = 0.02f

        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        val clockBlock = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(clock, LinearLayout.LayoutParams(wrap, wrap))
            addView(
                battery,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, wrap).apply { topMargin = dp(14) },
            )
        }
        val dateRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(date)
            addView(dot)
            addView(percent)
        }
        content.apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            // Everything at half strength: readable in the dark, easy on the panel.
            alpha = 0.55f
            addView(clockBlock, LinearLayout.LayoutParams(wrap, wrap))
            addView(dateRow, LinearLayout.LayoutParams(wrap, wrap).apply { topMargin = dp(14) })
            addView(waiting, LinearLayout.LayoutParams(wrap, wrap).apply { topMargin = dp(22) })
        }
        waiting.addView(chat)
        waiting.addView(mail, LinearLayout.LayoutParams(wrap, wrap).apply { leftMargin = dp(22) })
        addView(content, LayoutParams(wrap, wrap, Gravity.CENTER))
    }

    /** A line glyph for each kind of message waiting; the row goes when there is nothing. */
    fun showWaiting(whatsApp: Boolean, email: Boolean) {
        chat.visibility = if (whatsApp) VISIBLE else GONE
        mail.visibility = if (email) VISIBLE else GONE
        waiting.visibility = if (whatsApp || email) VISIBLE else GONE
    }

    private fun glyph(drawable: Int, description: String) = ImageView(context).apply {
        setImageResource(drawable)
        imageTintList = ColorStateList.valueOf(Palette.TEXT)
        contentDescription = description
        visibility = GONE
    }

    fun updateBattery(fraction: Float, charging: Boolean, low: Boolean, label: CharSequence) {
        battery.update(fraction, charging, low)
        percent.text = label
        percent.setTextColor(if (low) Palette.LOW else Palette.SECONDARY)
    }

    fun show() {
        if (visibility == VISIBLE) return
        animate().cancel()
        visibility = VISIBLE
        moveContent()
        animate().alpha(1f).setDuration(600).start()
    }

    /** Somewhere new within the middle of the screen, once a minute. */
    private fun moveContent() {
        val rangeX = width * 0.12f
        val rangeY = height * 0.18f
        content.animate()
            .translationX(Random.nextFloat() * 2 * rangeX - rangeX)
            .translationY(Random.nextFloat() * 2 * rangeY - rangeY)
            .setDuration(if (content.translationX == 0f && content.translationY == 0f) 0 else 1200)
            .start()
        removeCallbacks(drift)
        postDelayed(drift, 60_000)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) onExit()
        return true
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(drift)
        super.onDetachedFromWindow()
    }
}
