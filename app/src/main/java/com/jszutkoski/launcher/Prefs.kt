package com.jszutkoski.launcher

import android.content.Context

enum class TextSize(val label: String, val scale: Float) {
    SMALL("Small", 0.86f),
    DEFAULT("Default", 1f),
    LARGE("Large", 1.16f),
}

class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    private val names = context.getSharedPreferences("names", Context.MODE_PRIVATE)

    /** Keys of the apps pinned to the home screen, in display order. */
    var favorites: List<String>
        get() = prefs.getString(KEY_FAVORITES, null)?.split('\n')?.filter { it.isNotEmpty() }.orEmpty()
        set(value) = prefs.edit().putString(KEY_FAVORITES, value.joinToString("\n")).apply()

    var hidden: Set<String>
        get() = prefs.getStringSet(KEY_HIDDEN, null)?.toSet().orEmpty()
        set(value) = prefs.edit().putStringSet(KEY_HIDDEN, value).apply()

    var textSize: TextSize
        get() = TextSize.entries.getOrElse(prefs.getInt(KEY_TEXT_SIZE, TextSize.DEFAULT.ordinal)) { TextSize.DEFAULT }
        set(value) = prefs.edit().putInt(KEY_TEXT_SIZE, value.ordinal).apply()

    /** Open an app as soon as the search narrows down to it. */
    var autoLaunch: Boolean
        get() = prefs.getBoolean(KEY_AUTO_LAUNCH, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_LAUNCH, value).apply()

    /** Bring up the keyboard together with the app list. */
    var showKeyboard: Boolean
        get() = prefs.getBoolean(KEY_SHOW_KEYBOARD, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_KEYBOARD, value).apply()

    /** Hide the status bar on the home screen; a swipe from the top edge shows it briefly. */
    var hideStatusBar: Boolean
        get() = prefs.getBoolean(KEY_HIDE_STATUS_BAR, false)
        set(value) = prefs.edit().putBoolean(KEY_HIDE_STATUS_BAR, value).apply()

    fun customName(key: String): String? = names.getString(key, null)

    fun setCustomName(key: String, name: String?) {
        names.edit().apply {
            if (name.isNullOrBlank()) remove(key) else putString(key, name.trim())
        }.apply()
    }

    private companion object {
        const val KEY_FAVORITES = "favorites"
        const val KEY_HIDDEN = "hidden"
        const val KEY_TEXT_SIZE = "text_size"
        const val KEY_AUTO_LAUNCH = "auto_launch"
        const val KEY_SHOW_KEYBOARD = "show_keyboard"
        const val KEY_HIDE_STATUS_BAR = "hide_status_bar"
    }
}
