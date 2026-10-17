package com.jszutkoski.launcher

import android.app.Notification
import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Gives this app the list of media sessions (now playing) and tells which apps have
 * notifications waiting (dots). It never reads, changes or dismisses the notifications.
 */
class MediaListener : NotificationListenerService() {
    override fun onListenerConnected() = refresh()

    override fun onListenerDisconnected() = Notifications.update(emptySet())

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    private fun refresh() {
        val active = try {
            activeNotifications
        } catch (e: RuntimeException) {
            warn("Could not read the waiting notifications", e)
            null
        } ?: return
        Notifications.update(active.filter(::waiting).map { it.packageName to it.user }.toSet())
    }

    /** Something to read: not a running player, download or call, and not our own. */
    private fun waiting(sbn: StatusBarNotification) =
        sbn.isClearable && !sbn.isOngoing && sbn.packageName != packageName &&
            sbn.notification.flags and Notification.FLAG_FOREGROUND_SERVICE == 0
}

/**
 * Which apps have notifications waiting, for the dots, per user: the work copy of an app has
 * its own. Read and updated on the main thread.
 */
object Notifications {
    private val main = Handler(Looper.getMainLooper())
    private var packages: Set<Pair<String, UserHandle>> = emptySet()
    private val listeners = mutableListOf<() -> Unit>()

    fun has(packageName: String, user: UserHandle) = (packageName to user) in packages

    /** In any profile, for standby's glyphs. */
    fun hasAny(packageName: String) = packages.any { it.first == packageName }

    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    fun update(newPackages: Set<Pair<String, UserHandle>>) {
        main.post {
            if (newPackages == packages) return@post
            packages = newPackages
            listeners.toList().forEach { it() }
        }
    }
}

/**
 * A small filled circle. With [centerY] at the bounds' middle it sits beside a line of text;
 * on a dock icon it is drawn at the glyph's top-right corner instead.
 */
class Dot(context: Context, private val glyphInset: Int = 0) : Drawable() {
    private val radius = context.dp(3f)
    private val size = context.dp(6)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.TEXT }
    var shown = true
        set(value) {
            field = value
            invalidateSelf()
        }

    override fun draw(canvas: Canvas) {
        if (!shown) return
        if (glyphInset > 0) {
            canvas.drawCircle(bounds.right - glyphInset.toFloat(), bounds.top + glyphInset.toFloat(), radius, paint)
        } else {
            canvas.drawCircle(bounds.exactCenterX(), bounds.exactCenterY(), radius, paint)
        }
    }

    override fun getIntrinsicWidth() = if (glyphInset > 0) -1 else size

    override fun getIntrinsicHeight() = if (glyphInset > 0) -1 else size

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
