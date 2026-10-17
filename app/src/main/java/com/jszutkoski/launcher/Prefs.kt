package com.jszutkoski.launcher

import android.content.Context
import android.content.SharedPreferences

enum class TextSize(val label: Int, val scale: Float) {
    SMALL(R.string.text_small, 0.95f),
    DEFAULT(R.string.text_default, 1.1f),
    LARGE(R.string.text_large, 1.26f),
}

enum class StatusBarMode(val label: Int) {
    VISIBLE(R.string.bar_visible),

    /** Dark icons on the black background: nearly invisible, but never animates away and back. */
    DIMMED(R.string.bar_dimmed),

    /** Gone; Android slides it back briefly with the notification shade or a swipe from the top. */
    HIDDEN(R.string.bar_hidden),
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

    /** The app list stays empty until something is typed: no list to scroll through idly. */
    var searchOnly: Boolean
        get() = prefs.getBoolean(KEY_SEARCH_ONLY, false)
        set(value) = prefs.edit().putBoolean(KEY_SEARCH_ONLY, value).apply()

    /** Contacts in the search, to call, text or open WhatsApp (needs the contacts permission). */
    var contactSearch: Boolean
        get() = prefs.getBoolean(KEY_CONTACT_SEARCH, false)
        set(value) = prefs.edit().putBoolean(KEY_CONTACT_SEARCH, value).apply()

    /** Temperature and sky under the date (needs approximate location). */
    var weather: Boolean
        get() = prefs.getBoolean(KEY_WEATHER, false)
        set(value) = prefs.edit().putBoolean(KEY_WEATHER, value).apply()

    /** Android asks for a permission only twice; after that, only app info can grant it. */
    fun askedFor(permission: String) = prefs.getBoolean("$KEY_ASKED$permission", false)

    fun setAskedFor(permission: String) = prefs.edit().putBoolean("$KEY_ASKED$permission", true).apply()

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

    // The clock and the battery with it, on the home screen and the bedside clock.
    var clockDesign: ClockDesign
        get() = choice(KEY_CLOCK_DESIGN, ClockDesign.DIGITAL)
        set(value) = prefs.edit().putInt(KEY_CLOCK_DESIGN, value.ordinal).apply()

    var clockWeight: ClockWeight
        get() = choice(KEY_CLOCK_WEIGHT, ClockWeight.THIN)
        set(value) = prefs.edit().putInt(KEY_CLOCK_WEIGHT, value.ordinal).apply()

    /** On top of the text size. */
    var clockSize: SizeStep
        get() = choice(KEY_CLOCK_SIZE, SizeStep.DEFAULT)
        set(value) = prefs.edit().putInt(KEY_CLOCK_SIZE, value.ordinal).apply()

    var datePosition: DatePosition
        get() = choice(KEY_DATE_POSITION, DatePosition.ABOVE)
        set(value) = prefs.edit().putInt(KEY_DATE_POSITION, value.ordinal).apply()

    /** One of [default]'s values, stored by position; the default when unset or out of range. */
    private inline fun <reified T : Enum<T>> choice(key: String, default: T): T =
        enumValues<T>().getOrElse(prefs.getInt(key, default.ordinal)) { default }

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

    /** The WhatsApp and email glyphs on standby also over the lock screen, not only once unlocked. */
    var lockScreenGlyphs: Boolean
        get() = prefs.getBoolean(KEY_LOCK_SCREEN_GLYPHS, true)
        set(value) = prefs.edit().putBoolean(KEY_LOCK_SCREEN_GLYPHS, value).apply()

    /** Small dots beside apps with notifications waiting (needs notification access). */
    var notificationDots: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATION_DOTS, false)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATION_DOTS, value).apply()

    /** Apps that open only after a short breathing pause. */
    var pauseApps: Set<String>
        get() = prefs.getStringSet(KEY_PAUSE_APPS, null)?.toSet().orEmpty()
        set(value) = prefs.edit().putStringSet(KEY_PAUSE_APPS, value).apply()

    /** App chosen for a bottom icon, or null for that slot's default. */
    fun dockApp(slot: Int): String? = prefs.getString("$KEY_DOCK$slot", null)

    fun setDockApp(slot: Int, key: String?) {
        prefs.edit().apply { if (key == null) remove("$KEY_DOCK$slot") else putString("$KEY_DOCK$slot", key) }.apply()
    }

    /** The app a sideways swipe on the home screen opens, or null for none. */
    fun swipeApp(toLeft: Boolean): String? = prefs.getString(swipeKey(toLeft), null)

    fun setSwipeApp(toLeft: Boolean, key: String?) {
        prefs.edit().apply { if (key == null) remove(swipeKey(toLeft)) else putString(swipeKey(toLeft), key) }.apply()
    }

    private fun swipeKey(toLeft: Boolean) = if (toLeft) KEY_SWIPE_LEFT else KEY_SWIPE_RIGHT

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
        const val KEY_SEARCH_ONLY = "search_only"
        const val KEY_CONTACT_SEARCH = "contact_search"
        const val KEY_WEATHER = "weather"
        const val KEY_ASKED = "asked_"
        const val KEY_HIDE_STATUS_BAR = "hide_status_bar"
        const val KEY_STATUS_BAR = "status_bar"
        const val KEY_CENTER_FAVORITES = "center_favorites"
        const val KEY_SCREEN_TIME = "screen_time"
        const val KEY_NOW_PLAYING = "now_playing"
        const val KEY_DOUBLE_TAP_LOCK = "double_tap_lock"
        const val KEY_DOCK = "dock_"
        const val KEY_STANDBY = "standby"
        const val KEY_STANDBY_ON_WAKE = "standby_on_wake"
        const val KEY_NOTIFICATION_DOTS = "notification_dots"
        const val KEY_LOCK_SCREEN_GLYPHS = "lock_screen_glyphs"
        const val KEY_PAUSE_APPS = "pause_apps"
        // Not "clock_design": that held three designs, of which two are gone.
        const val KEY_CLOCK_DESIGN = "clock_layout"
        const val KEY_CLOCK_WEIGHT = "clock_weight"
        const val KEY_CLOCK_SIZE = "clock_size"
        const val KEY_DATE_POSITION = "date_position"
        const val KEY_SWIPE_LEFT = "swipe_left_app"
        const val KEY_SWIPE_RIGHT = "swipe_right_app"
    }
}
