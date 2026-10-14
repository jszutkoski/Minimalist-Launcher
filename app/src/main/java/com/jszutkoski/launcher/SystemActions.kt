package com.jszutkoski.launcher

import android.annotation.SuppressLint
import android.app.Activity
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings

object SystemActions {
    /** There is no public API for this; every third-party launcher uses the same call. */
    @SuppressLint("WrongConstant", "PrivateApi")
    fun expandNotifications(context: Context) {
        try {
            val service = context.getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(service)
        } catch (_: Throwable) {
        }
    }

    fun openCamera(activity: Activity) = start(activity, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))

    fun openDialer(activity: Activity) = start(activity, Intent(Intent.ACTION_DIAL))

    fun openAlarms(activity: Activity) = start(activity, Intent(AlarmClock.ACTION_SHOW_ALARMS))

    fun openCalendar(activity: Activity) = start(
        activity,
        Intent(
            Intent.ACTION_VIEW,
            CalendarContract.CONTENT_URI.buildUpon()
                .appendPath("time")
                .appendPath(System.currentTimeMillis().toString())
                .build(),
        ),
    )

    fun openHomeSettings(activity: Activity) = start(activity, Intent(Settings.ACTION_HOME_SETTINGS))

    fun isDefaultHome(context: Context): Boolean =
        context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_HOME) == true

    /** Shows the system "use as home app" prompt, or the home settings page if it is unavailable. */
    @Suppress("DEPRECATION")
    fun requestDefaultHome(activity: Activity) {
        val roles = activity.getSystemService(RoleManager::class.java)
        if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) {
            try {
                activity.startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME), 0)
                return
            } catch (_: ActivityNotFoundException) {
            }
        }
        openHomeSettings(activity)
    }

    fun start(activity: Activity, intent: Intent): Boolean = try {
        activity.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
