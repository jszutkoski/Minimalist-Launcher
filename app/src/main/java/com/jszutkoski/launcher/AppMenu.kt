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
 * The long-press menu of an app on home or in the list: its shortcuts, then pinning,
 * renaming, the pause, hiding, app info and uninstalling. [render] redraws home afterwards.
 */
class AppMenu(
    private val activity: Activity,
    private val prefs: Prefs,
    private val repo: AppRepository,
    private val render: () -> Unit,
    private val openShortcut: (App, ShortcutInfo) -> Unit,
) {
    fun show(app: App) {
        val favoriteKeys = prefs.favorites
        val index = favoriteKeys.indexOf(app.key)
        val paused = app.key in prefs.pauseApps
        Sheet(activity, app.label).apply {
            addShortcuts(activity, app) { openShortcut(app, it) }
            if (index >= 0) {
                option(activity.getString(R.string.menu_remove_home)) { prefs.favorites = favoriteKeys - app.key; render() }
                if (index > 0) option(activity.getString(R.string.menu_move_up)) { moveFavorite(app.key, -1) }
                if (index < favoriteKeys.lastIndex) option(activity.getString(R.string.menu_move_down)) { moveFavorite(app.key, 1) }
            } else {
                option(activity.getString(R.string.menu_add_home)) { addFavorite(app) }
            }
            option(activity.getString(R.string.menu_rename)) { showRename(app) }
            option(activity.getString(if (paused) R.string.menu_open_without_pause else R.string.menu_pause_before)) {
                prefs.pauseApps = if (paused) prefs.pauseApps - app.key else prefs.pauseApps + app.key
            }
            if (index < 0) option(activity.getString(R.string.menu_hide)) { hide(app) }
            option(activity.getString(R.string.menu_app_info)) { SystemActions.openAppDetails(activity, app) }
            if (!app.isSystem) option(activity.getString(R.string.menu_uninstall), Palette.SECONDARY) { uninstall(app) }
            show()
        }
    }

    private fun addFavorite(app: App) {
        val current = prefs.favorites.filter { repo.find(it) != null }
        if (current.size >= MAX_FAVORITES) {
            Toast.makeText(activity, activity.getString(R.string.home_full, MAX_FAVORITES), Toast.LENGTH_SHORT).show()
            return
        }
        prefs.favorites = current + app.key
        render()
    }

    private fun moveFavorite(key: String, by: Int) {
        val list = prefs.favorites.toMutableList()
        val from = list.indexOf(key)
        val to = from + by
        if (from < 0 || to !in list.indices) return
        list.add(to, list.removeAt(from))
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
        SystemActions.start(activity, Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.component.packageName}")))
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
