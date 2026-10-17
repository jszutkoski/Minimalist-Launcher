package com.jszutkoski.launcher

import android.content.Intent
import android.os.BatteryManager
import android.os.SystemClock
import kotlin.math.PI
import kotlin.math.cos

/** Below this charge, and not charging, the battery shows in red. */
const val LOW_BATTERY = 0.15f

/** The charge as the battery broadcast reports it, shared by home and standby. */
class BatteryState(val fraction: Float, val charging: Boolean) {
    val low get() = !charging && fraction <= LOW_BATTERY

    companion object {
        /** Null when the broadcast carries no usable level. */
        fun from(intent: Intent): BatteryState? {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (level < 0 || scale <= 0) return null
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            return BatteryState(level / scale.toFloat(), charging)
        }
    }
}

/** Charging: one breath in or out. Full on the charger: slower still, in green. */
private const val BREATH_MS = 1400
private const val SLOW_MS = 2400

/**
 * A breath drawn at these steps rather than every frame: the battery is lit all night on the
 * charger, and redrawing it at 120 Hz would keep the panel and GPU busy for nothing. About 16
 * steps a second: smooth enough for a slow fade.
 */
const val BREATH_FRAME_MS = 60L

/** The charging battery's alpha now, fading in and out since [start] (uptime). */
fun breathAlpha(start: Long, full: Boolean): Int {
    val period = if (full) SLOW_MS else BREATH_MS
    val low = if (full) 110 else 90
    val phase = ((SystemClock.uptimeMillis() - start) % (2 * period)) / period.toDouble()
    return (low + (255 - low) * (0.5 + 0.5 * cos(PI * phase))).toInt()
}
