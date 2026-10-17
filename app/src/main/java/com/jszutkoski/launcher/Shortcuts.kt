package com.jszutkoski.launcher

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.view.View
import android.widget.Toast

/**
 * The shortcuts an app offers, such as Waze's "Drive home" or WhatsApp's frequent chats.
 * Android shares them only with the current home app.
 */
object Shortcuts {
    private const val MAX = 4

    fun forApp(context: Context, app: App): List<ShortcutInfo> {
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return emptyList()
        return try {
            if (!launcherApps.hasShortcutHostPermission()) return emptyList()
            val query = LauncherApps.ShortcutQuery()
                .setPackage(app.component.packageName)
                .setActivity(app.component)
                .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC)
            launcherApps.getShortcuts(query, app.user).orEmpty()
                .filter { it.isEnabled && !it.shortLabel.isNullOrBlank() }
                // The app's fixed shortcuts first, then the changing ones, each in the app's own order.
                .sortedWith(compareBy({ !it.isDeclaredInManifest }, { it.rank }))
                .take(MAX)
        } catch (e: RuntimeException) {
            // The user is locked or the app went away.
            warn("No shortcuts for ${app.component}", e)
            emptyList()
        }
    }

    fun start(context: Context, shortcut: ShortcutInfo, source: View?) {
        val bounds = source?.let { Rect().also(it::getGlobalVisibleRect) }
        try {
            context.getSystemService(LauncherApps::class.java)?.startShortcut(shortcut, bounds, null)
        } catch (e: RuntimeException) {
            warn("Shortcut ${shortcut.id} failed", e)
            Toast.makeText(context, context.getString(R.string.cant_open, shortcut.shortLabel), Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * Adds an app's shortcuts at the top of a long-press sheet, set apart by a hairline. [open]
 * starts one, so that a shortcut into a paused app still goes through the pause.
 */
fun Sheet.addShortcuts(context: Context, app: App, open: (ShortcutInfo) -> Unit) {
    val shortcuts = Shortcuts.forApp(context, app)
    if (shortcuts.isEmpty()) return
    shortcuts.forEach { shortcut ->
        option(shortcut.shortLabel.toString()) { open(shortcut) }
    }
    divider()
}
