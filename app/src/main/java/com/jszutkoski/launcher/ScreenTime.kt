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
     * One activity coming to the foreground ([resumed]) or leaving it. [home] marks this
     * launcher coming back, which means nothing else is in front any more, whatever events
     * went missing.
     */
    class Change(val activity: String, val resumed: Boolean, val time: Long, val home: Boolean = false)

    /** Activities resumed before this much ahead of midnight are still counted from midnight. */
    private const val LOOKBACK_MS = 6 * 60 * 60 * 1000L
    private const val CACHE_MS = 60 * 1000L

    /** Back in the same app within this, after a moment with none of its screens: the same visit. */
    private const val REOPEN_MS = 3_000L

    @Volatile
    private var cached: Pair<Long, Long>? = null // when it was worked out, and the total

    /**
     * Time in the foreground since midnight, for one app or, with [only] null, all of them. Time
     * on this home screen is left out: it is the screen you see when not using the phone. The
     * total for all apps is reused for a minute, as home asks for it each time it comes back.
     */
    fun today(context: Context, only: String? = null): Long {
        val now = System.currentTimeMillis()
        val midnight = midnight()
        if (only == null) cached?.let { (at, total) -> if (now - at < CACHE_MS && at >= midnight) return total }
        val total = foreground(changes(context, midnight, now, only) ?: return 0, midnight, now)
        if (only == null) cached = now to total
        return total
    }

    /** One app's day so far: its time in the foreground, and how many times it was opened. */
    class AppDay(val time: Long, val opens: Int)

    fun appToday(context: Context, packageName: String): AppDay {
        val now = System.currentTimeMillis()
        val midnight = midnight()
        val changes = changes(context, midnight, now, packageName) ?: return AppDay(0, 0)
        return AppDay(foreground(changes, midnight, now), opens(changes, midnight))
    }

    private fun midnight() = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Every activity coming and going since a while before [midnight], for [only] one app or all. */
    private fun changes(context: Context, midnight: Long, now: Long, only: String?): List<Change>? {
        val usage = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val events = usage.queryEvents(midnight - LOOKBACK_MS, now) ?: return null
        val event = UsageEvents.Event()
        val changes = ArrayList<Change>()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName == context.packageName) {
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) changes += Change("", false, event.timeStamp, home = true)
                continue
            }
            if (only != null && event.packageName != only) continue
            val resumed = when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> true
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> false
                else -> continue
            }
            changes += Change("${event.packageName}/${event.className}", resumed, event.timeStamp)
        }
        return changes
    }

    /**
     * How many times, since [from], the app was opened: none of its activities in the
     * foreground, then one. Moving from screen to screen inside it leaves a moment with none,
     * so a return within [REOPEN_MS] is the same visit, unless home came in between.
     */
    fun opens(changes: List<Change>, from: Long): Int {
        val open = HashSet<String>()
        var closedAt = Long.MIN_VALUE
        var count = 0
        for (change in changes.sortedBy { it.time }) {
            if (change.home) {
                open.clear()
                closedAt = Long.MIN_VALUE
            } else if (change.resumed) {
                val returning = closedAt != Long.MIN_VALUE && change.time - closedAt < REOPEN_MS
                if (open.isEmpty() && !returning && change.time >= from) count++
                open += change.activity
            } else if (open.remove(change.activity) && open.isEmpty()) {
                closedAt = change.time
            }
        }
        return count
    }

    /**
     * How long, between [from] and [until], at least one of the activities was in the
     * foreground. Counted once when two share the screen, and from [from] for one opened
     * before it; one still open at the end counts until [until].
     */
    fun foreground(changes: List<Change>, from: Long, until: Long): Long {
        val open = HashSet<String>()
        var since = 0L
        var total = 0L
        for (change in changes.sortedBy { it.time }) {
            if (change.home) {
                if (open.isNotEmpty()) total += span(since, change.time, from, until)
                open.clear()
            } else if (change.resumed) {
                if (open.isEmpty()) since = change.time
                open += change.activity
            } else if (open.remove(change.activity) && open.isEmpty()) {
                total += span(since, change.time, from, until)
            }
        }
        if (open.isNotEmpty()) total += span(since, until, from, until)
        return total
    }

    private fun span(start: Long, end: Long, from: Long, until: Long) =
        (minOf(end, until) - maxOf(start, from)).coerceAtLeast(0)

    /** "2 h 14 min", "14 min" or "0 min". */
    fun format(millis: Long): String {
        val minutes = millis / 60_000
        val hours = minutes / 60
        return if (hours > 0) "$hours h ${minutes % 60} min" else "$minutes min"
    }
}
