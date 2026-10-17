package com.jszutkoski.launcher

import android.content.Intent
import android.os.BatteryManager

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

/** Packages the launcher recognises by name. */
object Packages {
    val WHATSAPP = listOf("com.whatsapp", "com.whatsapp.w4b")
    val EMAIL = listOf(
        "com.google.android.gm",
        "com.microsoft.office.outlook",
        "ch.protonmail.android",
        "com.yahoo.mobile.client.android.mail",
        "com.samsung.android.email.provider",
        "com.fsck.k9",
        "net.thunderbird.android",
        "com.fastmail.app",
    )
}
