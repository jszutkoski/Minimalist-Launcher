package com.jszutkoski.launcher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

/** Below this charge, and not charging, the battery shows in red. */
const val LOW_BATTERY = 0.15f

/**
 * A hairline under the clock, as wide as the time, filled to the battery level.
 * It breathes slowly while charging and turns red when the battery runs low.
 */
class BatteryBar(context: Context) : View(context) {
    private val thickness = context.dp(3f)
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.TRACK }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var level = 1f
    private var breathing: ValueAnimator? = null

    fun update(level: Float, charging: Boolean, low: Boolean) {
        this.level = level.coerceIn(0f, 1f)
        fillPaint.color = if (low) Palette.LOW else Palette.TEXT
        if (charging) breathe() else stopBreathing()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), thickness.toInt().coerceAtLeast(1))
    }

    override fun onDraw(canvas: Canvas) {
        val radius = height / 2f
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRoundRect(0f, 0f, w, h, radius, radius, trackPaint)
        if (level > 0f) canvas.drawRoundRect(0f, 0f, maxOf(w * level, h), h, radius, radius, fillPaint)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        breathing?.start()
    }

    override fun onDetachedFromWindow() {
        breathing?.cancel()
        super.onDetachedFromWindow()
    }

    private fun breathe() {
        if (breathing != null) return
        breathing = ValueAnimator.ofInt(255, 90).apply {
            duration = 1400
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                fillPaint.alpha = it.animatedValue as Int
                invalidate()
            }
            if (isAttachedToWindow) start()
        }
    }

    private fun stopBreathing() {
        breathing?.cancel()
        breathing = null
        fillPaint.alpha = 255
    }
}
