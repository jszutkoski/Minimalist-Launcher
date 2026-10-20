package com.jszutkoski.launcher

import android.app.Activity
import android.content.Context

/**
 * A time a day for an app, past which its mindful pause is three times as long and puts
 * "Not now" first. Counted from the system's usage events, so it needs usage access.
 */
object DailyLimit {
    /** The limits offered, in minutes. */
    val CHOICES = listOf(15, 30, 45, 60, 120)

    /** "30 min", "1 h", "1 h 30 min". */
    fun format(minutes: Int): String =
        if (minutes % 60 == 0) "${minutes / 60} h" else ScreenTime.format(minutes * MINUTE_MS)

    fun isPast(day: ScreenTime.AppDay, minutes: Int) = day.time >= minutes * MINUTE_MS

    /** "45 min today, opened 14 times". */
    fun usageLine(context: Context, day: ScreenTime.AppDay): String {
        val opens = if (day.opens == 1) context.getString(R.string.opened_once) else context.getString(R.string.opened_times, day.opens)
        return context.getString(R.string.pause_usage, ScreenTime.format(day.time), opens)
    }

    /** The limits for [app], the current one bright; [done] once one is chosen. */
    fun choose(activity: Activity, prefs: Prefs, app: App, done: () -> Unit = {}) {
        val current = prefs.dailyLimits[app.key]
        Sheet(activity, activity.getString(R.string.limit_title, app.label)).apply {
            (listOf<Int?>(null) + CHOICES).forEach { minutes ->
                val label = minutes?.let(::format) ?: activity.getString(R.string.limit_none)
                option(label, if (minutes == current) Palette.TEXT else Palette.SECONDARY) {
                    prefs.dailyLimits = if (minutes == null) prefs.dailyLimits - app.key else prefs.dailyLimits + (app.key to minutes)
                    // Nothing to count it by without usage access: Android's page to allow it.
                    if (minutes != null && !ScreenTime.hasAccess(activity)) SystemActions.openUsageAccess(activity)
                    done()
                }
            }
            show()
        }
    }

    private const val MINUTE_MS = 60_000L
}
