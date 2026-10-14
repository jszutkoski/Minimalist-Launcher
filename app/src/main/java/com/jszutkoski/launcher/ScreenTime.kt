package com.jszutkoski.launcher

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.util.Calendar

/** Today's time in apps, from the system's own usage events. Needs usage access. */
object ScreenTime {
    fun hasAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        // Deprecated in Android 16 but still the only check available back to Android 11.
        @Suppress("DEPRECATION")
        val mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Adds up every stretch an app's activity spent in the foreground since midnight.
     * Time on this home screen is left out: it is the screen you see when not using the phone.
     */
    fun today(context: Context): Long {
        val usage = context.getSystemService(UsageStatsManager::class.java) ?: return 0
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val events = usage.queryEvents(start, System.currentTimeMillis()) ?: return 0
        val event = UsageEvents.Event()
        val resumedAt = HashMap<String, Long>()
        var total = 0L
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName == context.packageName) continue
            val key = "${event.packageName}/${event.className}"
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> resumedAt[key] = event.timeStamp
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED ->
                    resumedAt.remove(key)?.let { total += event.timeStamp - it }
            }
        }
        return total
    }

    /** "2 h 14 min", "14 min" or "0 min". */
    fun format(millis: Long): String {
        val minutes = millis / 60_000
        val hours = minutes / 60
        return if (hours > 0) "$hours h ${minutes % 60} min" else "$minutes min"
    }
}
