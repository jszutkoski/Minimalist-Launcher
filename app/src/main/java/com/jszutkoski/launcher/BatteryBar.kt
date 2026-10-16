package com.jszutkoski.launcher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.TypedValue
import android.view.View
import kotlin.math.ceil

/** Below this charge, and not charging, the battery shows in red. */
const val LOW_BATTERY = 0.15f

/**
 * A hairline under the clock, as wide as the time, filled to the battery level.
 * Where the fill ends sits a marker, the line parting around it: the [label] (the
 * percentage) and, while charging, a small bolt. Charging, it breathes slowly; full on
 * the charger, it turns green and pulses; low, it turns red.
 */
class BatteryBar(context: Context) : View(context) {
    private val thickness = context.dp(3f)
    private val gap = context.dp(7f)
    private val boltGap = context.dp(3f)
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.TRACK }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val boltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = weight(400)
        letterSpacing = 0.04f
        color = Palette.SECONDARY
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 13f, context.resources.displayMetrics)
    }
    private val bolt = Path()
    private var level = 1f
    private var charging = false
    private var full = false
    private var breathing: ValueAnimator? = null

    /** The percentage, drawn at the end of the fill; null for no number. */
    var label: String? = null
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
            invalidate()
        }

    /** Keep the marker's height even with nothing in it, so plugging in never moves anything. */
    var roomForMarker = false
        set(value) {
            field = value
            requestLayout()
        }

    fun setLabelSize(sp: Float) {
        labelPaint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)
        requestLayout()
        invalidate()
    }

    fun update(level: Float, charging: Boolean, low: Boolean) {
        this.level = level.coerceIn(0f, 1f)
        val full = charging && this.level >= 1f
        val changed = charging != this.charging || full != this.full
        this.charging = charging
        this.full = full
        fillPaint.color = when {
            low -> Palette.LOW
            full -> Palette.FULL
            else -> Palette.TEXT
        }
        labelPaint.color = when {
            low -> Palette.LOW
            full -> Palette.FULL
            else -> Palette.SECONDARY
        }
        boltPaint.color = if (full) Palette.FULL else Palette.TEXT
        if (changed) {
            stopBreathing()
            if (charging) breathe(slow = full)
            requestLayout()
        }
        invalidate()
    }

    private val markerHeight: Int
        get() {
            val text = ceil(labelPaint.fontMetrics.run { descent - ascent }).toInt()
            return maxOf(text, boltSize.toInt())
        }

    /** As tall as the digits' capitals. */
    private val boltSize get() = labelPaint.textSize * 0.82f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val line = thickness.toInt().coerceAtLeast(1)
        val marker = label != null || charging || roomForMarker
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), if (marker) maxOf(line, markerHeight) else line)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val text = label
        if (text == null && !charging) {
            segment(canvas, 0f, w, trackPaint)
            if (level > 0f) segment(canvas, 0f, maxOf(w * level, thickness), fillPaint)
            return
        }
        // Filled line up to the marker, the marker, then the empty line after it.
        val boltWidth = if (charging) boltSize * 0.62f else 0f
        val textWidth = text?.let(labelPaint::measureText) ?: 0f
        val spacing = if (charging && text != null) boltGap else 0f
        val half = (boltWidth + spacing + textWidth) / 2f
        val centre = (w * level).coerceIn(half, (w - half).coerceAtLeast(half))
        val fillEnd = centre - half - gap
        val trackStart = centre + half + gap
        if (fillEnd > 0f) segment(canvas, 0f, fillEnd, fillPaint)
        if (trackStart < w) segment(canvas, trackStart, w, trackPaint)

        var x = centre - half
        if (charging) {
            drawBolt(canvas, x, boltWidth)
            x += boltWidth + spacing
        }
        if (text != null) {
            val baseline = height / 2f - (labelPaint.descent() + labelPaint.ascent()) / 2f
            labelPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(text, x, baseline, labelPaint)
        }
    }

    /** A lightning bolt, [width] wide, centred on the line. */
    private fun drawBolt(canvas: Canvas, left: Float, width: Float) {
        val h = boltSize
        val top = (height - h) / 2f
        fun px(fx: Float) = left + fx * width
        fun py(fy: Float) = top + fy * h
        bolt.rewind()
        bolt.moveTo(px(0.62f), py(0f))
        bolt.lineTo(px(0f), py(0.56f))
        bolt.lineTo(px(0.45f), py(0.56f))
        bolt.lineTo(px(0.36f), py(1f))
        bolt.lineTo(px(1f), py(0.42f))
        bolt.lineTo(px(0.55f), py(0.42f))
        bolt.close()
        canvas.drawPath(bolt, boltPaint)
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

    /** Charging: a slow breath. Full: slower still, in green. */
    private fun breathe(slow: Boolean) {
        breathing = ValueAnimator.ofInt(255, if (slow) 110 else 90).apply {
            duration = if (slow) 2400 else 1400
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                val alpha = it.animatedValue as Int
                fillPaint.alpha = alpha
                labelPaint.alpha = alpha
                boltPaint.alpha = alpha
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
        boltPaint.alpha = 255
    }
}
