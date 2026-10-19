package com.jszutkoski.launcher

import android.content.Context
import android.content.SharedPreferences
import android.view.WindowManager

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

/**
 * The bedside clock's screen brightness: the system's, one of its own, or one that follows the
 * time of day. Stored by position, so new ones go last.
 */
enum class StandbyBrightness(private val fixed: Float) {
    SYSTEM(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE),
    TEN(0.1f),
    QUARTER(0.25f),
    HALF(0.5f),
    THREE_QUARTERS(0.75f),
    FULL(1f),
    BY_HOUR(Float.NaN);

    /** The window brightness at [hour] (0 to 23), or the system's own for [SYSTEM]. */
    fun level(hour: Int): Float = if (this == BY_HOUR) byHour(hour) else fixed

    /** As a percentage, for those of a fixed level. */
    val percent: Int? get() = if (this == SYSTEM || this == BY_HOUR) null else Math.round(fixed * 100)

    private fun byHour(hour: Int): Float = when (hour) {
        in DAY_START until DUSK -> 0.6f
        in DAWN until DAY_START, in DUSK until NIGHT -> 0.3f
        else -> 0.1f
    }

    private companion object {
        // Bright through the day, softer at dawn and in the evening, faint in the small hours.
        const val DAWN = 6
        const val DAY_START = 8
        const val DUSK = 20
        const val NIGHT = 23
    }
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

    /** The clock and battery on [screen]; each screen has its own. */
    fun clockLook(screen: ClockScreen) = ClockLook(
        choice(screen.prefix + KEY_CLOCK_DESIGN, ClockDesign.DIGITAL),
        choice(screen.prefix + KEY_CLOCK_WEIGHT, ClockWeight.THIN),
        choice(screen.prefix + KEY_CLOCK_SIZE, SizeStep.DEFAULT),
        choice(screen.prefix + KEY_DATE_POSITION, DatePosition.ABOVE),
    )

    fun setClockLook(screen: ClockScreen, look: ClockLook) {
        prefs.edit()
            .putInt(screen.prefix + KEY_CLOCK_DESIGN, look.design.ordinal)
            .putInt(screen.prefix + KEY_CLOCK_WEIGHT, look.weight.ordinal)
            .putInt(screen.prefix + KEY_CLOCK_SIZE, look.size.ordinal)
            .putInt(screen.prefix + KEY_DATE_POSITION, look.date.ordinal)
            .apply()
    }

    /** Temperature and sky under the clock on [screen] (needs approximate location). */
    fun weather(screen: ClockScreen) = prefs.getBoolean(weatherKey(screen), false)

    fun setWeather(screen: ClockScreen, on: Boolean) = prefs.edit().putBoolean(weatherKey(screen), on).apply()

    /** Today's screen time under the clock on [screen] (needs usage access). */
    fun screenTime(screen: ClockScreen) = prefs.getBoolean(screenTimeKey(screen), false)

    fun setScreenTime(screen: ClockScreen, on: Boolean) = prefs.edit().putBoolean(screenTimeKey(screen), on).apply()

    // Weather was the bedside clock's alone, and screen time the home screen's: their keys stay.
    private fun weatherKey(screen: ClockScreen) = if (screen == ClockScreen.HOME) KEY_HOME_WEATHER else KEY_WEATHER

    private fun screenTimeKey(screen: ClockScreen) =
        if (screen == ClockScreen.HOME) KEY_SCREEN_TIME else KEY_STANDBY_SCREEN_TIME

    /** One of [default]'s values, stored by position; the default when unset or out of range. */
    private inline fun <reified T : Enum<T>> choice(key: String, default: T): T =
        enumValues<T>().getOrElse(prefs.getInt(key, default.ordinal)) { default }

    /** Clock and pinned apps centred on the home screen, or left aligned with the apps near the thumb. */
    var centerFavorites: Boolean
        get() = prefs.getBoolean(KEY_CENTER_FAVORITES, true)
        set(value) = prefs.edit().putBoolean(KEY_CENTER_FAVORITES, value).apply()

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

    var standbyBrightness: StandbyBrightness
        get() = choice(KEY_STANDBY_BRIGHTNESS, StandbyBrightness.SYSTEM)
        set(value) = prefs.edit().putInt(KEY_STANDBY_BRIGHTNESS, value.ordinal).apply()

    /** Which of the two bedside screens; at first, the design the bedside clock had before there were only two. */
    var standbyScreen: StandbyScreen
        get() = StandbyScreen.entries.getOrNull(prefs.getInt(KEY_STANDBY_SCREEN, -1))
            ?: if (clockLook(ClockScreen.STANDBY).design == ClockDesign.ANALOG) StandbyScreen.ANALOG else StandbyScreen.DIGITAL
        set(value) = prefs.edit().putInt(KEY_STANDBY_SCREEN, value.ordinal).apply()

    /** Put standby on top whenever the screen turns off, so it is the first thing on wake. */
    var standbyOnWake: Boolean
        get() = prefs.getBoolean(KEY_STANDBY_ON_WAKE, false)
        set(value) = prefs.edit().putBoolean(KEY_STANDBY_ON_WAKE, value).apply()

    /** The WhatsApp and email glyphs on standby also over the lock screen, not only once unlocked. */
    var lockScreenGlyphs: Boolean
        get() = prefs.getBoolean(KEY_LOCK_SCREEN_GLYPHS, true)
        set(value) = prefs.edit().putBoolean(KEY_LOCK_SCREEN_GLYPHS, value).apply()

    /** Apps, by package, whose notifications never show an icon on the bedside clock. */
    var standbyMuted: Set<String>
        get() = prefs.getStringSet(KEY_STANDBY_MUTED, null)?.toSet().orEmpty()
        set(value) = prefs.edit().putStringSet(KEY_STANDBY_MUTED, value).apply()

    /** Small dots beside apps with notifications waiting (needs notification access). */
    var notificationDots: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATION_DOTS, false)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATION_DOTS, value).apply()

    /** Apps that open only after a short breathing pause. */
    var pauseApps: Set<String>
        get() = prefs.getStringSet(KEY_PAUSE_APPS, null)?.toSet().orEmpty()
        set(value) = prefs.edit().putStringSet(KEY_PAUSE_APPS, value).apply()

    /**
     * The bottom icons, left to right, as app keys or [DOCK_CAMERA], including those past
     * [dockCount] kept for when it grows again; null until first changed.
     */
    var dockApps: List<String>?
        get() = prefs.getString(KEY_DOCK_APPS, null)?.split('\n')?.filter { it.isNotEmpty() }
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_DOCK_APPS) else putString(KEY_DOCK_APPS, value.joinToString("\n"))
        }.apply()

    /** How many bottom icons there are. */
    var dockCount: Int
        get() = prefs.getInt(KEY_DOCK_COUNT, DEFAULT_DOCK_COUNT).coerceIn(0, MAX_DOCK)
        set(value) = prefs.edit().putInt(KEY_DOCK_COUNT, value.coerceIn(0, MAX_DOCK)).apply()

    /** From before the bottom icons were a list: the app chosen for one of the five, or null. */
    fun oldDockApp(slot: Int): String? = prefs.getString("$KEY_DOCK$slot", null)

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

    companion object {
        /** In [dockApps], the camera: whichever app Android opens for a photo. */
        const val DOCK_CAMERA = "camera"
        const val MAX_DOCK = 6
        const val DEFAULT_DOCK_COUNT = 5

        private const val KEY_FAVORITES = "favorites"
        private const val KEY_HIDDEN = "hidden"
        private const val KEY_TEXT_SIZE = "text_size"
        private const val KEY_AUTO_LAUNCH = "auto_launch"
        private const val KEY_SHOW_KEYBOARD = "show_keyboard"
        private const val KEY_SEARCH_ONLY = "search_only"
        private const val KEY_CONTACT_SEARCH = "contact_search"
        private const val KEY_WEATHER = "weather"
        private const val KEY_ASKED = "asked_"
        private const val KEY_HIDE_STATUS_BAR = "hide_status_bar"
        private const val KEY_STATUS_BAR = "status_bar"
        private const val KEY_CENTER_FAVORITES = "center_favorites"
        private const val KEY_SCREEN_TIME = "screen_time"
        private const val KEY_NOW_PLAYING = "now_playing"
        private const val KEY_DOUBLE_TAP_LOCK = "double_tap_lock"
        private const val KEY_DOCK = "dock_"
        private const val KEY_STANDBY = "standby"
        private const val KEY_STANDBY_ON_WAKE = "standby_on_wake"
        private const val KEY_NOTIFICATION_DOTS = "notification_dots"
        private const val KEY_LOCK_SCREEN_GLYPHS = "lock_screen_glyphs"
        private const val KEY_PAUSE_APPS = "pause_apps"
        // Not "clock_design": that held three designs, of which two are gone.
        private const val KEY_CLOCK_DESIGN = "clock_layout"
        private const val KEY_CLOCK_WEIGHT = "clock_weight"
        private const val KEY_CLOCK_SIZE = "clock_size"
        private const val KEY_DATE_POSITION = "date_position"
        private const val KEY_HOME_WEATHER = "home_weather"
        private const val KEY_STANDBY_SCREEN_TIME = "standby_screen_time"
        private const val KEY_SWIPE_LEFT = "swipe_left_app"
        private const val KEY_SWIPE_RIGHT = "swipe_right_app"
        private const val KEY_DOCK_APPS = "dock_apps"
        private const val KEY_DOCK_COUNT = "dock_count"
        private const val KEY_STANDBY_MUTED = "standby_muted"
        private const val KEY_STANDBY_BRIGHTNESS = "standby_brightness"
        private const val KEY_STANDBY_SCREEN = "standby_screen"
    }
}
