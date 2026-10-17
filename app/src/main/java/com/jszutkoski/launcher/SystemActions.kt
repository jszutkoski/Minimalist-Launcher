package com.jszutkoski.launcher

import android.annotation.SuppressLint
import android.app.Activity
import android.app.SearchManager
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Bundle
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
        } catch (e: Throwable) {
            warn("Could not open the notification shade", e)
        }
    }

    // Opening another app always uses the system's animation: Android ignores every kind of
    // launch animation (custom, scale-up, clip reveal) asked for by a third-party home screen.
    fun openCamera(activity: Activity) = start(activity, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))

    fun openUsageAccess(activity: Activity) =
        start(activity, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${activity.packageName}"))) ||
            start(activity, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))

    fun openNotificationAccess(activity: Activity) =
        start(
            activity,
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(activity, MediaListener::class.java).flattenToString(),
            ),
        ) || start(activity, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))

    /** Android's own choice of what the lock screen shows of notifications; its page moves between makers. */
    fun openLockScreenNotifications(activity: Activity) =
        start(activity, Intent("android.settings.LOCK_SCREEN_SETTINGS")) ||
            start(activity, Intent("android.settings.NOTIFICATION_SETTINGS")) ||
            start(activity, Intent(Settings.ACTION_SETTINGS))

    fun openAccessibility(activity: Activity) = start(activity, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    /** The privacy policy, published with the code. */
    fun openPrivacyPolicy(activity: Activity) = start(activity, Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)))

    /** Where Android hides "Allow restricted settings" for sideloaded apps. */
    fun openAppInfo(activity: Activity) =
        start(activity, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}")))

    /** Digital Wellbeing's dashboard; it usually has no launcher icon, so it is opened by name. */
    fun openDigitalWellbeing(activity: Activity): Boolean {
        val pkg = "com.google.android.apps.wellbeing"
        val launch = activity.packageManager.getLaunchIntentForPackage(pkg)
        return (launch != null && start(activity, launch)) ||
            start(activity, Intent().setClassName(pkg, "$pkg.settings.TopLevelSettingsActivity")) ||
            start(activity, Intent(Settings.ACTION_SETTINGS))
    }

    fun openBatteryUsage(activity: Activity) = start(activity, Intent(Intent.ACTION_POWER_USAGE_SUMMARY))

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

    /** The phone's search app, else Google in the browser. */
    fun webSearch(activity: Activity, query: String) =
        start(activity, Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)) ||
            start(activity, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")))

    /** App info for an app, which may have been uninstalled since its menu opened. */
    fun openAppDetails(activity: Activity, app: App) {
        try {
            activity.getSystemService(LauncherApps::class.java).startAppDetailsActivity(app.component, app.user, null, null)
        } catch (e: RuntimeException) {
            warn("No app info for ${app.component}", e)
            start(activity, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.component.packageName}")))
        }
    }

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

    fun start(activity: Activity, intent: Intent, options: Bundle? = null): Boolean = try {
        activity.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options)
        true
    } catch (e: ActivityNotFoundException) {
        warn("Nothing opens $intent", e)
        false
    } catch (e: SecurityException) {
        warn("Not allowed to open $intent", e)
        false
    }

    private const val PRIVACY_POLICY_URL = "https://github.com/jszutkoski/Minimalist-Launcher/blob/main/PRIVACY.md"
}
