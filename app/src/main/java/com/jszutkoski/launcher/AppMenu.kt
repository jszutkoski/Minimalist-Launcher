package com.jszutkoski.launcher

import android.app.Activity
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.net.Uri
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast

/**
 * The long-press menu of an app on home or in the list: its shortcuts, then pinning to home
 * or to the bottom icons, renaming, the pause and the daily limit, hiding, app info and
 * uninstalling. [render]
 * redraws home afterwards.
 */
class AppMenu(
    private val activity: Activity,
    private val prefs: Prefs,
    private val repo: AppRepository,
    private val dock: DockApps,
    private val render: () -> Unit,
    private val openShortcut: (App, ShortcutInfo) -> Unit,
    /** Opens the app straight away, past its pause. */
    private val openNow: (App) -> Unit,
) {
    fun show(app: App) {
        // Only the pinned apps that show: a locked private space or an app being updated keeps
        // its place in storage, but is not counted or moved past.
        val favoriteKeys = shownFavorites()
        val index = favoriteKeys.indexOf(app.key)
        val paused = app.key in prefs.pauseApps
        Sheet(activity, app.label).apply {
            addShortcuts(activity, app) { openShortcut(app, it) }
            if (index >= 0) {
                option(activity.getString(R.string.menu_remove_home)) { prefs.favorites = prefs.favorites - app.key; render() }
                if (index > 0) option(activity.getString(R.string.menu_move_up)) { moveFavorite(app.key, -1) }
                if (index < favoriteKeys.lastIndex) option(activity.getString(R.string.menu_move_down)) { moveFavorite(app.key, 1) }
            } else {
                option(activity.getString(R.string.menu_add_home)) { addFavorite(app) }
            }
            val docked = dock.shown().indexOf(app.key)
            if (docked >= 0) {
                option(activity.getString(R.string.dock_remove)) { dock.remove(docked); render() }
            } else {
                option(activity.getString(R.string.menu_add_dock)) { dock.offer(activity, app, render) }
            }
            option(activity.getString(R.string.menu_rename)) { showRename(app) }
            if (paused) {
                // Just this once; the pause stays for next time.
                option(activity.getString(R.string.menu_open_without_pause)) { openNow(app) }
                option(activity.getString(R.string.menu_remove_pause)) { prefs.pauseApps = prefs.pauseApps - app.key }
            } else {
                option(activity.getString(R.string.menu_pause_before)) { prefs.pauseApps = prefs.pauseApps + app.key }
            }
            val limit = prefs.dailyLimits[app.key]
            option(
                if (limit == null) activity.getString(R.string.menu_daily_limit) else activity.getString(R.string.menu_daily_limit_set, DailyLimit.format(limit)),
            ) { DailyLimit.choose(activity, prefs, app) }
            if (index < 0) option(activity.getString(R.string.menu_hide)) { hide(app) }
            option(activity.getString(R.string.menu_app_info)) { SystemActions.openAppDetails(activity, app) }
            if (!app.isSystem) option(activity.getString(R.string.menu_uninstall), Palette.SECONDARY) { uninstall(app) }
            show()
        }
    }

    private fun shownFavorites() = prefs.favorites.filter { repo.find(it) != null }

    private fun addFavorite(app: App) {
        if (shownFavorites().size >= MAX_FAVORITES) {
            Toast.makeText(activity, activity.getString(R.string.home_full, MAX_FAVORITES), Toast.LENGTH_SHORT).show()
            return
        }
        // Everything stored stays stored: the private space's apps come back when it unlocks.
        prefs.favorites = prefs.favorites + app.key
        render()
    }

    /** Swaps [key] with the shown app next to it, whatever is stored, unseen, in between. */
    private fun moveFavorite(key: String, by: Int) {
        val shown = shownFavorites()
        val neighbour = shown.getOrNull(shown.indexOf(key) + by) ?: return
        val list = prefs.favorites.toMutableList()
        val a = list.indexOf(key)
        val b = list.indexOf(neighbour)
        list[a] = neighbour
        list[b] = key
        prefs.favorites = list
        render()
    }

    private fun hide(app: App) {
        prefs.hidden = prefs.hidden + app.key
        prefs.favorites = prefs.favorites - app.key
        render()
        Toast.makeText(activity, activity.getString(R.string.app_hidden, app.label), Toast.LENGTH_SHORT).show()
    }

    private fun uninstall(app: App) {
        // The copy in this app's profile: the work profile's or private space's, not the personal one.
        SystemActions.start(
            activity,
            Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.component.packageName}")).putExtra(Intent.EXTRA_USER, app.user),
        )
    }

    private fun showRename(app: App) {
        val sheet = Sheet(activity, activity.getString(R.string.rename_title, app.originalLabel))
        val input = EditText(activity).apply {
            setText(app.label)
            selectAll()
            setTextColor(Palette.TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = weight(300)
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            imeOptions = EditorInfo.IME_ACTION_DONE
            backgroundTintList = pressable(Palette.TERTIARY)
        }
        fun save(name: String?) {
            prefs.setCustomName(app.key, name?.takeIf { it.isNotBlank() && it.trim() != app.originalLabel })
            sheet.dialog.dismiss()
            repo.rebuild()
        }
        input.setOnEditorActionListener { _, _, _ ->
            save(input.text.toString())
            true
        }
        sheet.column.addView(input)

        val buttons = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(8), 0, 0)
        }
        buttons.addView(
            activity.text(activity.getString(R.string.rename_reset), 17f, Palette.SECONDARY, 400).apply {
                setPadding(dp(16), dp(12), dp(16), dp(12))
                setOnClickListener { save(null) }
            },
        )
        buttons.addView(
            activity.text(activity.getString(R.string.rename_save), 17f, Palette.TEXT, 500).apply {
                setPadding(dp(16), dp(12), 0, dp(12))
                setOnClickListener { save(input.text.toString()) }
            },
        )
        sheet.column.addView(buttons)

        // Dialogs are not edge-to-edge, so resizing above the keyboard still applies.
        @Suppress("DEPRECATION")
        sheet.dialog.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
        )
        sheet.show()
        input.requestFocus()
    }

    private companion object {
        const val MAX_FAVORITES = 8
    }
}
