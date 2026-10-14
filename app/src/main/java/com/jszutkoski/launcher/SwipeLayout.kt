package com.jszutkoski.launcher

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Root of the home screen. Recognises swipes anywhere, even when they start on
 * a favourite, and long-presses on empty space.
 */
class SwipeLayout(context: Context) : FrameLayout(context) {
    interface Listener {
        /** Upward drag in progress; 0 is closed, 1 is fully open. */
        fun onDrawerDrag(progress: Float)
        fun onDrawerRelease(open: Boolean)
        fun onSwipeDown()
        fun onDoubleTapEmpty()
        fun onPinchIn()
        fun onLongPressEmpty()
    }

    private enum class Mode { NONE, UP, DOWN, SIDE, PINCH }

    var listener: Listener? = null

    /** Checked when a touch starts, so a drag already under way is never cut off. */
    var gesturesEnabled = true

    private val config = ViewConfiguration.get(context)
    private val slop = config.scaledTouchSlop
    private val fling = context.dp(500f)
    private val dragDistance = context.dp(DRAG_DISTANCE_DP)
    private val swipeDistance = context.dp(80f)
    private val pullDistance = context.dp(32f)

    private var active = false
    private var mode = Mode.NONE
    private var pulled = false
    private var pinched = false
    private var pinchStart = 0f
    private var lastTapTime = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f
    private var downX = 0f
    private var downY = 0f
    private var originY = 0f
    private var velocity: VelocityTracker? = null

    private val longPress = Runnable {
        if (mode == Mode.NONE) {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            listener?.onLongPressEmpty()
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            active = gesturesEnabled
            mode = Mode.NONE
            downX = ev.x
            downY = ev.y
            velocity?.recycle()
            velocity = VelocityTracker.obtain()
        }
        velocity?.addMovement(ev)
        trackPinch(ev)
        return super.dispatchTouchEvent(ev)
    }

    /**
     * Two fingers drawing together, anywhere on the home screen. Watched here, before
     * children see the touch, so it also works when the first finger lands on an app name.
     */
    private fun trackPinch(ev: MotionEvent) {
        if (!active) return
        when (ev.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> if (ev.pointerCount == 2 && mode == Mode.NONE) {
                mode = Mode.PINCH
                pinchStart = span(ev)
                removeCallbacks(longPress)
            }
            MotionEvent.ACTION_MOVE -> if (mode == Mode.PINCH && !pinched && ev.pointerCount >= 2 &&
                span(ev) < pinchStart * PINCH_RATIO
            ) {
                pinched = true
                listener?.onPinchIn()
            }
        }
    }

    private fun span(ev: MotionEvent) = hypot(ev.getX(0) - ev.getX(1), ev.getY(0) - ev.getY(1))

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean =
        active && ev.actionMasked == MotionEvent.ACTION_MOVE && detect(ev)

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!active) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
            MotionEvent.ACTION_MOVE -> {
                if (mode == Mode.NONE) detect(ev)
                if (mode == Mode.UP) listener?.onDrawerDrag(((originY - ev.y) / dragDistance).coerceIn(0f, 1f))
                // Open the shade mid-gesture, so it comes down with the finger instead of after it.
                if (mode == Mode.DOWN && !pulled && ev.y - originY > pullDistance) {
                    pulled = true
                    listener?.onSwipeDown()
                }
            }
            MotionEvent.ACTION_UP -> release(ev)
            MotionEvent.ACTION_CANCEL -> {
                if (mode == Mode.UP) listener?.onDrawerRelease(false)
                finish()
            }
        }
        return true
    }

    private fun detect(ev: MotionEvent): Boolean {
        if (mode != Mode.NONE) return true
        val dx = ev.x - downX
        val dy = ev.y - downY
        if (abs(dx) < slop && abs(dy) < slop) return false
        mode = when {
            abs(dy) >= abs(dx) && dy < 0 -> Mode.UP
            abs(dy) >= abs(dx) -> Mode.DOWN
            else -> Mode.SIDE
        }
        originY = ev.y
        removeCallbacks(longPress)
        parent?.requestDisallowInterceptTouchEvent(true)
        return true
    }

    private fun release(ev: MotionEvent) {
        val tracker = velocity
        tracker?.computeCurrentVelocity(1000)
        val vy = tracker?.yVelocity ?: 0f
        val dy = ev.y - downY
        when (mode) {
            Mode.UP -> {
                val progress = (originY - ev.y) / dragDistance
                listener?.onDrawerRelease(vy < -fling || (vy < fling && progress > 0.3f))
            }
            Mode.DOWN -> if (!pulled && (dy > swipeDistance || vy > fling)) listener?.onSwipeDown()
            Mode.NONE -> detectDoubleTap(ev)
            // Sideways movement only cancels a long-press; it has no action of its own.
            Mode.SIDE, Mode.PINCH -> Unit
        }
        finish()
    }

    /** Two quick taps close together on empty space. A lone tap still does nothing. */
    private fun detectDoubleTap(ev: MotionEvent) {
        if (ev.eventTime - ev.downTime >= ViewConfiguration.getLongPressTimeout()) return
        val near = hypot(ev.x - lastTapX, ev.y - lastTapY) < config.scaledDoubleTapSlop
        if (ev.eventTime - lastTapTime <= ViewConfiguration.getDoubleTapTimeout() && near) {
            lastTapTime = 0L
            listener?.onDoubleTapEmpty()
        } else {
            lastTapTime = ev.eventTime
            lastTapX = ev.x
            lastTapY = ev.y
        }
    }

    private fun finish() {
        removeCallbacks(longPress)
        mode = Mode.NONE
        pulled = false
        pinched = false
        velocity?.recycle()
        velocity = null
    }

    private companion object {
        /** How far the fingers must close, relative to where they started. */
        const val PINCH_RATIO = 0.65f
    }
}
