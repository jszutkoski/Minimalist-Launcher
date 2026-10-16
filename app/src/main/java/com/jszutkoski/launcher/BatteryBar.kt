package com.jszutkoski.launcher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.TypedValue
import android.view.View
import kotlin.math.ceil

/** Below this charge, and not charging, the battery shows in red. */
const val LOW_BATTERY = 0.15f

/**
 * A hairline under the clock, as wide as the time, filled to the battery level.
 * It breathes slowly while charging and turns red when the battery runs low.
 * With a [label], the number sits right where the fill ends, the line parting around it.
 */
class BatteryBar(context: Context) : View(context) {
    private val thickness = context.dp(3f)
    private val gap = context.dp(7f)
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.TRACK }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = weight(400)
        letterSpacing = 0.04f
        color = Palette.SECONDARY
    }
    private var level = 1f
    private var breathing: ValueAnimator? = null

    /** The percentage, drawn at the end of the fill; null for a plain line. */
    var label: String? = null
        set(value) {
            if (field == value) return
            val resize = (field == null) != (value == null)
            field = value
            if (resize) requestLayout()
            invalidate()
        }

    fun setLabelSize(sp: Float) {
        labelPaint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)
        requestLayout()
        invalidate()
    }

    fun update(level: Float, charging: Boolean, low: Boolean) {
        this.level = level.coerceIn(0f, 1f)
        fillPaint.color = if (low) Palette.LOW else Palette.TEXT
        labelPaint.color = if (low) Palette.LOW else Palette.SECONDARY
        if (charging) breathe() else stopBreathing()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val line = thickness.toInt().coerceAtLeast(1)
        val height = if (label == null) line else maxOf(line, ceil(labelPaint.fontMetrics.run { descent - ascent }).toInt())
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val text = label
        if (text == null) {
            segment(canvas, 0f, w, trackPaint)
            if (level > 0f) segment(canvas, 0f, maxOf(w * level, thickness), fillPaint)
            return
        }
        // Filled line up to the number, the number, then the empty line after it.
        val half = labelPaint.measureText(text) / 2f
        val centre = (w * level).coerceIn(half, (w - half).coerceAtLeast(half))
        val fillEnd = centre - half - gap
        val trackStart = centre + half + gap
        if (fillEnd > 0f) segment(canvas, 0f, fillEnd, fillPaint)
        if (trackStart < w) segment(canvas, trackStart, w, trackPaint)
        val baseline = height / 2f - (labelPaint.descent() + labelPaint.ascent()) / 2f
        labelPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(text, centre, baseline, labelPaint)
    }

    /** A rounded stretch of line, centred vertically. */
    private fun segment(canvas: Canvas, from: Float, to: Float, paint: Paint) {
        if (to <= from) return
        val top = (height - thickness) / 2f
        val radius = thickness / 2f
        canvas.drawRoundRect(from, top, maxOf(to, from + thickness), top + thickness, radius, radius, paint)
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
                labelPaint.alpha = it.animatedValue as Int
                invalidate()
            }
            if (isAttachedToWindow) start()
        }
    }

    private fun stopBreathing() {
        breathing?.cancel()
        breathing = null
        fillPaint.alpha = 255
        labelPaint.alpha = 255
    }
}
