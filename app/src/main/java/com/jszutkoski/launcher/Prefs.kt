package com.jszutkoski.launcher

import android.content.Context
import android.content.SharedPreferences

enum class TextSize(val label: String, val scale: Float) {
    SMALL("Small", 0.86f),
    DEFAULT("Default", 1f),
    LARGE("Large", 1.16f),
}

enum class StatusBarMode(val label: String) {
    VISIBLE("Visible"),

    /** Dark icons on the black background: nearly invisible, but never animates away and back. */
    DIMMED("Dimmed"),

    /** Gone; Android slides it back briefly with the notification shade or a swipe from the top. */
    HIDDEN("Hidden"),
}

class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    private val names = context.getSharedPreferences("names", Context.MODE_PRIVATE)

    // Kept here: SharedPreferences only holds its listeners weakly.
    private var changeListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    /** Calls [listener] after any setting, name or pinned app changes, in this or another screen. */
    fun onChange(listener: () -> Unit) {
        val wrapped = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> listener() }
        changeListener = wrapped
        prefs.registerOnSharedPreferenceChangeListener(wrapped)
        names.registerOnSharedPreferenceChangeListener(wrapped)
    }

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

    /** How the status bar looks on the home screen. */
    var statusBar: StatusBarMode
        get() {
            val stored = prefs.getInt(KEY_STATUS_BAR, -1)
            if (stored < 0) {
                // Carried over from the earlier on/off "hide status bar" switch.
                return if (prefs.getBoolean(KEY_HIDE_STATUS_BAR, false)) StatusBarMode.HIDDEN else StatusBarMode.VISIBLE
            }
            return StatusBarMode.entries.getOrElse(stored) { StatusBarMode.VISIBLE }
        }
        set(value) = prefs.edit().putInt(KEY_STATUS_BAR, value.ordinal).apply()

    /** Clock and pinned apps centred on the home screen, or left aligned with the apps near the thumb. */
    var centerFavorites: Boolean
        get() = prefs.getBoolean(KEY_CENTER_FAVORITES, true)
        set(value) = prefs.edit().putBoolean(KEY_CENTER_FAVORITES, value).apply()

    /** Show today's screen time under the date (needs usage access). */
    var screenTime: Boolean
        get() = prefs.getBoolean(KEY_SCREEN_TIME, false)
        set(value) = prefs.edit().putBoolean(KEY_SCREEN_TIME, value).apply()

    /** Show the playing track with controls under the date (needs notification access). */
    var nowPlaying: Boolean
        get() = prefs.getBoolean(KEY_NOW_PLAYING, false)
        set(value) = prefs.edit().putBoolean(KEY_NOW_PLAYING, value).apply()

    /** Lock the screen on a double-tap of empty space (needs the accessibility service). */
    var doubleTapLock: Boolean
        get() = prefs.getBoolean(KEY_DOUBLE_TAP_LOCK, false)
        set(value) = prefs.edit().putBoolean(KEY_DOUBLE_TAP_LOCK, value).apply()

    /** Bedside clock after a short idle on the home screen while charging. */
    var standbyWhenCharging: Boolean
        get() = prefs.getBoolean(KEY_STANDBY, true)
        set(value) = prefs.edit().putBoolean(KEY_STANDBY, value).apply()

    /** Put standby on top whenever the screen turns off, so it is the first thing on wake. */
    var standbyOnWake: Boolean
        get() = prefs.getBoolean(KEY_STANDBY_ON_WAKE, false)
        set(value) = prefs.edit().putBoolean(KEY_STANDBY_ON_WAKE, value).apply()

    /** App chosen for a bottom icon, or null for that slot's default. */
    fun dockApp(slot: Int): String? = prefs.getString("$KEY_DOCK$slot", null)

    fun setDockApp(slot: Int, key: String?) {
        prefs.edit().apply { if (key == null) remove("$KEY_DOCK$slot") else putString("$KEY_DOCK$slot", key) }.apply()
    }

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
        const val KEY_STATUS_BAR = "status_bar"
        const val KEY_CENTER_FAVORITES = "center_favorites"
        const val KEY_SCREEN_TIME = "screen_time"
        const val KEY_NOW_PLAYING = "now_playing"
        const val KEY_DOUBLE_TAP_LOCK = "double_tap_lock"
        const val KEY_DOCK = "dock_"
        const val KEY_STANDBY = "standby"
        const val KEY_STANDBY_ON_WAKE = "standby_on_wake"
    }
}
