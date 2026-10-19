package com.jszutkoski.launcher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityOptions
import android.app.AlarmManager
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.database.ContentObserver
import android.graphics.Insets
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.ContactsContract
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.TouchDelegate
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.accessibility.AccessibilityManager
import android.view.animation.PathInterpolator
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs

class MainActivity : Activity(), SwipeLayout.Listener, AppDrawerView.Callbacks {
    private lateinit var prefs: Prefs
    private lateinit var repo: AppRepository
    private lateinit var launcherApps: LauncherApps

    private lateinit var root: SwipeLayout
    private lateinit var home: HomeColumn
    private lateinit var face: ClockFace
    private lateinit var date: TextClock
    private lateinit var alarmDot: TextView
    private lateinit var alarmText: TextView
    private lateinit var pause: PauseView
    private var favoriteViews: List<Pair<TextView, App>> = emptyList()
    private val dotsChanged: () -> Unit = { showDots() }
    private lateinit var favorites: LinearLayout
    private lateinit var setDefault: TextView
    private lateinit var dateRow: LinearLayout
    private lateinit var topSpace: Space
    private lateinit var bottomSpace: Space
    private lateinit var dock: Dock
    private lateinit var screenTimeText: TextView
    private lateinit var weatherText: TextView
    /** Apps and settings as plain buttons, shown only to TalkBack, which can't make the swipes. */
    private lateinit var accessibleRow: LinearLayout
    private val touchExploration = AccessibilityManager.TouchExplorationStateChangeListener { showAccessibleRow() }
    private var contactsLoaded = false
    private var contactsObserverOn = false
    private val contactsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            contactsLoaded = false
        }
    }
    private lateinit var appMenu: AppMenu
    private lateinit var searchActions: SearchActions
    private lateinit var player: PlayerView
    private lateinit var panel: HomePanel
    private lateinit var nowPlaying: NowPlaying
    private val background = Executors.newSingleThreadExecutor()
    private lateinit var drawer: AppDrawerView

    private var progress = 0f
    private var stale = true

    /** The date's row goes above the clock, as chosen; otherwise under it. */
    private var dateAbove = true
    private var panelLayout = false

    /** Today's screen time as last read, to show at once while it is read again. */
    private var lastScreenTime: Long? = null
    private var animator: ValueAnimator? = null
    private val emphasized = PathInterpolator(0.2f, 0f, 0f, 1f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        launcherApps = getSystemService(LauncherApps::class.java)
        edgeToEdge()

        buildViews()
        setContentView(root)
        interceptBack()

        repo = AppRepository(this, prefs)
        repo.onChanged = ::render
        prefs.onChange { stale = true }
        dock = Dock(this, prefs, repo, ::launch, ::openShortcut)
        appMenu = AppMenu(this, prefs, repo, dock.apps, ::render, ::openShortcut) { launchNow(it, root) }
        searchActions = SearchActions(this, repo, ::launch, ::throughPauseFor) { closeDrawer(animate = true) }
        addDock()
        repo.start()
        nowPlaying = NowPlaying(this, ::showTrack)
        Notifications.addListener(dotsChanged)
        Torch.start(this)
        getSystemService(AccessibilityManager::class.java)?.addTouchExplorationStateChangeListener(touchExploration)
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = updateBattery(intent)
    }

    private val alarmReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = updateAlarm()
    }
    private val alarmRefresh = Runnable { updateAlarm() }

    /** The screen coming on, and every minute: the clock catches up if its own timer ran late. */
    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refreshClock()
    }

    private fun refreshClock() {
        face.refresh()
        date.refreshTime()
    }

    override fun onStart() {
        super.onStart()
        // A sticky broadcast: registering returns the current state right away.
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let(::updateBattery)
        registerReceiver(alarmReceiver, IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED))
        registerReceiver(
            timeReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
        )
        refreshClock()
        updateAlarm()
    }

    override fun onResume() {
        super.onResume()
        inFront = true
        resumed = true
        refreshClock()
        // Only rebuilt when a setting, name or pinned app changed since the last time, so
        // coming back from an app costs next to nothing before home can draw. Back from
        // settings, the weather and screen time (as last read) are in place for that first
        // frame too, so the new layout shows whole at once rather than filling in after.
        if (stale) {
            render()
            updateWeather()
            showScreenTime(lastScreenTime)
        }
        setDefault.visibility = if (SystemActions.isDefaultHome(this)) View.GONE else View.VISIBLE
        showAccessibleRow()
        applyStatusBar()
        player.reserveSpace = prefs.nowPlaying
        // Lookups that can wait for the first frame.
        root.post {
            updateWeather()
            updateScreenTime()
            loadContacts()
            if (prefs.nowPlaying) nowPlaying.start() else nowPlaying.stop()
        }
    }

    override fun onStop() {
        super.onStop()
        resumed = false
        // Stopped because the screen went off: home is still what the user was looking at.
        inFront = getSystemService(PowerManager::class.java)?.isInteractive == false
        unregisterReceiver(batteryReceiver)
        unregisterReceiver(alarmReceiver)
        unregisterReceiver(timeReceiver)
        root.removeCallbacks(alarmRefresh)
        // Pressing home or the screen going off closes any menu left open.
        Sheet.dismissAll(this)
        nowPlaying.stop()
        pause.hide(animate = false)
        // Coming back home should always land on the clock, never a stale search.
        closeDrawer(animate = false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home swiped over a bedside clock on the lock screen: the clock again, asking for the
        // fingerprint or PIN, rather than Android's lock screen over this one.
        if (getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true) {
            StandbyLog.note(this, "home new intent, locked")
            if (OverLock.home(this)) return
        }
        // Home pressed while already home.
        Sheet.dismissAll(this)
        if (pause.isShowing) cancelPause()
        if (progress > 0f) closeDrawer(animate = true)
    }

    override fun onDestroy() {
        Sheet.dismissAll(this)
        repo.stop()
        Notifications.removeListener(dotsChanged)
        Torch.stop(this)
        getSystemService(AccessibilityManager::class.java)?.removeTouchExplorationStateChangeListener(touchExploration)
        if (contactsObserverOn) contentResolver.unregisterContentObserver(contactsObserver)
        background.shutdownNow()
        super.onDestroy()
    }

    // Android 11 and 12 only: from 13 on, back reaches the OnBackInvokedCallback instead.
    @SuppressLint("GestureBackNavigation")
    @Deprecated("Only reached below Android 13; newer versions use OnBackInvokedCallback.")
    override fun onBackPressed() {
        // A home screen never finishes itself.
        if (pause.isShowing) cancelPause() else if (progress > 0f) closeDrawer(animate = true)
    }

    private fun buildViews() {
        root = SwipeLayout(this).apply {
            setBackgroundColor(Palette.BACKGROUND)
            listener = this@MainActivity
        }

        home = HomeColumn(this)

        // The clock widget: the time with the battery, a hairline exactly as wide as the time
        // with the percentage set into it where the fill ends, or an analog face with the
        // battery set into it. The date goes above or below it, or nowhere.
        face = ClockFace(this, pressable = true).apply {
            setOnTimeClick { SystemActions.openAlarms(this@MainActivity) }
            battery.setOnClickListener { SystemActions.openBatteryUsage(this@MainActivity) }
        }
        val datePattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM")
        date = TextClock(this).apply {
            format12Hour = datePattern
            format24Hour = datePattern
            setTextColor(pressable(Palette.SECONDARY))
            typeface = weight(400)
            letterSpacing = 0.02f
            setPadding(0, dp(10), 0, dp(10))
            setOnClickListener { SystemActions.openCalendar(this@MainActivity) }
        }
        alarmDot = text("·", 15f, Palette.TERTIARY, 400).apply {
            setPadding(dp(10), dp(10), dp(10), dp(10))
            visibility = View.GONE
        }
        alarmText = text("", 15f, Palette.SECONDARY, 400).apply {
            letterSpacing = 0.02f
            setPadding(0, dp(10), dp(8), dp(10))
            visibility = View.GONE
            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_alarm, 0, 0, 0)
            compoundDrawablePadding = dp(5)
            compoundDrawableTintList = pressable(Palette.SECONDARY)
            setOnClickListener { SystemActions.openAlarms(this@MainActivity) }
        }
        favorites = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        home.names = favorites
        setDefault = text(getString(R.string.set_as_home), 14f, Palette.SECONDARY, 400).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(12))
            setOnClickListener { SystemActions.requestDefaultHome(this@MainActivity) }
        }
        weatherText = text("", 17f, Palette.SECONDARY, 400).apply {
            letterSpacing = 0.02f
            visibility = View.GONE
        }
        screenTimeText = text("", 17f, Palette.SECONDARY, 400).apply {
            letterSpacing = 0.02f
            visibility = View.GONE
            setPadding(0, 0, 0, dp(8))
            setOnClickListener { SystemActions.openDigitalWellbeing(this@MainActivity) }
        }
        player = PlayerView(this, ::openPlayer)
        panel = HomePanel(
            this,
            onScreenTime = { SystemActions.openDigitalWellbeing(this@MainActivity) },
            onBattery = { SystemActions.openBatteryUsage(this@MainActivity) },
        ).apply { visibility = View.GONE }

        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        dateRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(date)
            addView(alarmDot)
            addView(alarmText)
        }
        home.addView(dateRow, LinearLayout.LayoutParams(wrap, wrap))
        home.addView(face, LinearLayout.LayoutParams(wrap, wrap))
        // Optional, each on its own line under the clock: the weather, then today's screen time.
        home.addView(weatherText, LinearLayout.LayoutParams(wrap, wrap))
        home.addView(screenTimeText, LinearLayout.LayoutParams(wrap, wrap))
        // Or all of it in one row, with the battery, for the panel layout.
        home.addView(panel, LinearLayout.LayoutParams(wrap, wrap))
        topSpace = Space(this)
        bottomSpace = Space(this)
        home.addView(topSpace, LinearLayout.LayoutParams(0, 0, 1f))
        home.addView(favorites, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, wrap))
        home.addView(bottomSpace, LinearLayout.LayoutParams(0, dp(28)))

        home.addView(
            setDefault,
            LinearLayout.LayoutParams(wrap, wrap).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(8)
            },
        )
        accessibleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
            val buttons = listOf(R.string.all_apps to { openDrawer() }, R.string.settings to { onLongPressEmpty() })
            buttons.forEach { (label, action) ->
                addView(
                    text(getString(label), 17f, Palette.SECONDARY, 400).apply {
                        setPadding(dp(16), dp(12), dp(16), dp(12))
                        setOnClickListener { action() }
                    },
                )
            }
        }
        home.addView(
            accessibleRow,
            LinearLayout.LayoutParams(wrap, wrap).apply { gravity = Gravity.CENTER_HORIZONTAL },
        )
        root.addView(home)

        // The battery line is a few dp tall: its touch area reaches 48dp, like any button.
        face.battery.addOnLayoutChangeListener { bar, _, _, _, _, _, _, _, _ ->
            val area = Rect().also(bar::getHitRect)
            val extra = ((dp(48) - area.height()) / 2).coerceAtLeast(0)
            area.inset(0, -extra)
            face.touchDelegate = TouchDelegate(area, bar)
        }

        drawer = AppDrawerView(this, this).apply {
            visibility = View.INVISIBLE
            alpha = 0f
        }
        root.addView(drawer)

        // Hidden once home stops, so the app opens over the pause rather than over home.
        pause = PauseView(this, onCancel = ::cancelPause)
        root.addView(pause)

        root.setOnApplyWindowInsetsListener { _, insets ->
            // Ignoring visibility keeps the layout still when the status bar is hidden or peeks in.
            val bars = Insets.max(
                insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()),
                insets.getInsets(WindowInsets.Type.displayCutout()),
            )
            val ime = insets.getInsets(WindowInsets.Type.ime())
            if (insets.isVisible(WindowInsets.Type.ime())) drawer.onKeyboardShown()
            val side = dp(SIDE_MARGIN_DP)
            home.setPadding(bars.left + side, bars.top + dp(56), bars.right + side, bars.bottom + dp(36))
            drawer.applyInsets(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            WindowInsets.CONSUMED
        }
    }

    private fun render() {
        stale = false
        val scale = prefs.textSize.scale
        panelLayout = prefs.homeLayout == HomeLayout.PANEL
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f * scale)
        // The panel keeps the digits' weight and size, always digital and with the date above.
        val look = prefs.clockLook(ClockScreen.HOME).let {
            if (panelLayout) it.copy(design = ClockDesign.DIGITAL, date = DatePosition.ABOVE) else it
        }
        face.apply(
            look,
            digitsSp = 100f * scale,
            analogDp = 220f * scale,
            labelSp = 13f * scale,
            gravity = if (prefs.centerFavorites) Gravity.CENTER_HORIZONTAL else Gravity.START,
            maxWidth = resources.displayMetrics.widthPixels - 2 * dp(SIDE_MARGIN_DP),
            gap = dp(GAP_DP),
            // The battery is in the panel.
            batteryLine = !panelLayout,
        )
        panel.setTextScale(scale)
        panel.visibility = if (panelLayout) View.VISIBLE else View.GONE
        // The date's row above the clock or below it; hidden, only the next alarm stays there.
        dateAbove = look.date == DatePosition.ABOVE
        val rowParams = dateRow.layoutParams
        home.removeView(dateRow)
        home.addView(dateRow, home.indexOfChild(face) + if (dateAbove) 0 else 1, rowParams)
        date.visibility = if (look.date == DatePosition.HIDDEN) View.GONE else View.VISIBLE
        alarmDot.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f * scale)
        alarmText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f * scale)
        pause.setTextScale(scale)
        renderFavorites(scale)
        dock.render()
        showDots()
        screenTimeText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f * scale)
        weatherText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f * scale)
        // Spaces the column too, with every size set.
        updateAlarm()
        player.setTextScale(scale)
        setDefault.visibility = if (SystemActions.isDefaultHome(this)) View.GONE else View.VISIBLE

        drawer.textScale = scale
        drawer.autoLaunch = prefs.autoLaunch
        drawer.searchOnly = prefs.searchOnly
        drawer.whatsApp = searchActions.whatsAppPackage() != null
        drawer.setApps(repo.visibleApps())
    }

    private fun updateBattery(intent: Intent) {
        val battery = BatteryState.from(intent) ?: return
        face.showBattery(battery, number = true)
        panel.showBattery(battery)
    }

    // Standby ------------------------------------------------------------------

    private fun enterStandby() {
        // On an unlocked phone, standby needs the accessibility service to stay guarded.
        if (!StandbyActivity.canShow(this)) {
            Toast.makeText(this, R.string.standby_needs_service, Toast.LENGTH_LONG).show()
            return
        }
        StandbyActivity.start(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // The notification shade only takes focus away; it never pauses this activity, so
        // onResume doesn't run when it closes.
        if (hasFocus) applyStatusBar()
        // A keyboard asked for while the window had no focus yet is shown now.
        if (hasFocus && progress > 0f) drawer.onWindowFocus()
    }

    private fun showAccessibleRow() {
        val talkBack = getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true
        accessibleRow.visibility = if (talkBack) View.VISIBLE else View.GONE
    }

    private fun applyStatusBar() {
        val controller = window.insetsController ?: return
        val mode = prefs.statusBar
        val darkIcons = if (mode == StatusBarMode.DIMMED) WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS else 0
        controller.setSystemBarsAppearance(darkIcons, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
        if (mode == StatusBarMode.HIDDEN) {
            controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsets.Type.statusBars())
        } else {
            controller.show(WindowInsets.Type.statusBars())
        }
    }

    /** The player and the dock go last, below "Set as home app", once the app list exists. */
    private fun addDock() {
        // Away from the clock: the player sits just above the icons, and only while music plays.
        home.addView(
            player,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(20)
            },
        )
        home.addView(
            dock.view,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                // Pulled out by the icon padding, so the glyphs line up with the text margin.
                leftMargin = -dock.iconInset
                rightMargin = -dock.iconInset
            },
        )
    }

    private fun updateScreenTime() {
        if (!prefs.screenTime(ClockScreen.HOME) || !ScreenTime.hasAccess(this)) {
            showScreenTime(null)
            return
        }
        background.execute {
            val total = ScreenTime.today(this)
            runOnUiThread {
                lastScreenTime = total
                showScreenTime(total)
            }
        }
    }

    /** Today's [total] under the clock or in the panel, or nothing when it is off or not read yet. */
    private fun showScreenTime(total: Long?) {
        val time = total?.takeIf { prefs.screenTime(ClockScreen.HOME) && ScreenTime.hasAccess(this) }?.let(ScreenTime::format)
        if (time != null) screenTimeText.text = getString(R.string.screen_time_today, time)
        // In the panel, the time alone: its glyph says what it is.
        screenTimeText.visibility = if (time != null && !panelLayout) View.VISIBLE else View.GONE
        panel.showScreenTime(time)
        spaceHome()
    }

    /** The last reading, fetched again when it is over half an hour old. */
    private fun updateWeather() {
        if (!prefs.weather(ClockScreen.HOME) || !Weather.hasAccess(this)) {
            showWeather(null)
            return
        }
        showWeather(Weather.cached(this))
        if (Weather.isFresh(this)) return
        Weather.refresh(this) { now ->
            if (now != null) runOnUiThread { if (!isDestroyed) showWeather(now) }
        }
    }

    private fun showWeather(now: Weather.Now?) {
        weatherText.text = now?.label(this).orEmpty()
        weatherText.visibility = if (now == null || panelLayout) View.GONE else View.VISIBLE
        panel.showWeather(now)
        spaceHome()
    }

    /** The date (or the alarm alone), the clock, the weather and screen time: one gap between each. */
    private fun spaceHome() {
        val row = spacedText(if (date.visibility == View.VISIBLE) date else alarmText, dateRow)
        spaceGlyphs(
            buildList {
                if (dateAbove) add(row)
                add(face.spaced())
                if (!dateAbove) add(row)
                add(spacedText(weatherText))
                add(spacedText(screenTimeText))
                add(panel.spaced())
            },
            dp(if (panelLayout) PANEL_GAP_DP else GAP_DP),
        )
    }

    // Smart search -------------------------------------------------------------------

    /** Once, then again only after the address book changes. */
    private fun loadContacts() {
        if (!prefs.contactSearch || !ContactSearch.hasAccess(this)) {
            if (drawer.contacts.isNotEmpty()) drawer.contacts = emptyList()
            contactsLoaded = false
            return
        }
        if (!contactsObserverOn) {
            contactsObserverOn = true
            contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, contactsObserver)
        }
        if (contactsLoaded) return
        contactsLoaded = true
        background.execute {
            val contacts = ContactSearch.load(this)
            runOnUiThread { if (prefs.contactSearch) drawer.contacts = contacts }
        }
    }

    override fun onResult(result: SearchResult, source: View) = searchActions.open(result, source)

    override fun onWhatsApp(contact: Contact) = searchActions.whatsApp(contact)

    private fun showTrack(track: NowPlaying.Track?) = player.show(track)

    private fun openPlayer(packageName: String) {
        val app = repo.apps.firstOrNull { it.component.packageName == packageName } ?: return
        launch(app, player)
    }

    private fun renderFavorites(scale: Float) {
        favorites.removeAllViews()
        val center = prefs.centerFavorites
        val alignment = if (center) Gravity.CENTER_HORIZONTAL else Gravity.START
        favorites.gravity = alignment
        (screenTimeText.layoutParams as LinearLayout.LayoutParams).gravity = alignment
        (weatherText.layoutParams as LinearLayout.LayoutParams).gravity = alignment
        (panel.layoutParams as LinearLayout.LayoutParams).apply {
            gravity = alignment
            // Left aligned, the first glyph lines up with the text margin, not its padding.
            leftMargin = if (center) 0 else -dp(16)
        }
        player.setAlignment(alignment)
        // The clock widget follows the same alignment as the pinned apps.
        (face.layoutParams as LinearLayout.LayoutParams).gravity = alignment
        (dateRow.layoutParams as LinearLayout.LayoutParams).gravity = alignment
        face.requestLayout()
        // Centred: in the middle of the free space. Left aligned: down near the thumb.
        bottomSpace.layoutParams = (bottomSpace.layoutParams as LinearLayout.LayoutParams).apply {
            weight = if (center) 1f else 0f
            height = if (center) 0 else dp(28)
        }
        val apps = prefs.favorites.mapNotNull(repo::find)
        favoriteViews = emptyList()
        if (apps.isEmpty()) {
            if (repo.loaded) {
                favorites.addView(
                    text(getString(R.string.empty_home), 17f * scale, Palette.SECONDARY).apply {
                        gravity = alignment
                        isSingleLine = false
                        setLineSpacing(dp(6f), 1f)
                    },
                )
            }
            return
        }
        home.namesChanged(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 30f * scale, resources.displayMetrics))
        favoriteViews = apps.map { app ->
            val view = text(app.label, 30f * scale).apply {
                setPadding(0, dp(9), 0, dp(9))
                compoundDrawablePadding = dp(12)
                setOnClickListener { launch(app, it) }
                setOnLongClickListener {
                    appMenu.show(app)
                    true
                }
            }
            favorites.addView(
                view,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
            view to app
        }
    }

    // Notification dots ------------------------------------------------------------

    private fun showDots() {
        val enabled = prefs.notificationDots && NowPlaying.hasAccess(this)
        val centred = prefs.centerFavorites
        favoriteViews.forEach { (view, app) ->
            val dot = if (enabled && Notifications.has(app.component.packageName, app.user)) Dot(this) else null
            // Centred names get an invisible twin on the other side, so the dot never pulls them off centre.
            val twin = if (dot != null && centred) Dot(this).apply { shown = false } else null
            dot?.setBounds(0, 0, dot.intrinsicWidth, dot.intrinsicHeight)
            twin?.setBounds(0, 0, twin.intrinsicWidth, twin.intrinsicHeight)
            view.setCompoundDrawablesRelative(twin, null, dot, null)
        }
        dock.showDots(enabled)
    }

    // Next alarm -------------------------------------------------------------------

    /** "6:30" within a day, "Sat 6:30" further out, gone when no alarm is set. */
    private fun updateAlarm() {
        root.removeCallbacks(alarmRefresh)
        val next = getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime
        val visible = if (next != null) View.VISIBLE else View.GONE
        // The dot only parts the alarm from a date beside it.
        alarmDot.visibility = if (date.visibility == View.VISIBLE) visible else View.GONE
        alarmText.visibility = visible
        dateRow.visibility = if (date.visibility == View.VISIBLE || next != null) View.VISIBLE else View.GONE
        spaceHome()
        if (next == null) return
        val untilWithinDay = next - System.currentTimeMillis() - DAY_MS
        // Drop the weekday once the alarm is less than a day away, even if home stays open.
        if (untilWithinDay > 0) root.postDelayed(alarmRefresh, untilWithinDay + 1000)
        val skeleton = (if (untilWithinDay > 0) "EEE" else "") +
            (if (DateFormat.is24HourFormat(this)) "Hm" else "hm")
        alarmText.text = DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton), next)
    }

    // Mindful pause ------------------------------------------------------------------

    private fun showPause(app: App, open: () -> Unit) {
        root.gesturesEnabled = false
        // Typing must not go on underneath: the hidden search could open something else.
        drawer.hideKeyboard()
        pause.show(app, null, open)
        if (!prefs.screenTime(ClockScreen.HOME) || !ScreenTime.hasAccess(this)) return
        background.execute {
            val spent = ScreenTime.today(this, app.component.packageName)
            runOnUiThread { if (pause.isShowing) pause.setUsage(ScreenTime.format(spent)) }
        }
    }

    private fun cancelPause() {
        pause.hide(animate = true)
        root.gesturesEnabled = progress == 0f
    }

    // Drawer animation --------------------------------------------------------

    /** 0 shows home, 1 shows the app list; the two cross-fade with a short slide. */
    private fun setProgress(value: Float) {
        progress = value
        val shift = dp(48f)
        home.alpha = (1f - value * 1.6f).coerceIn(0f, 1f)
        home.translationY = -value * shift
        drawer.alpha = value
        drawer.translationY = (1f - value) * shift * 2f
        drawer.visibility = if (value > 0f) View.VISIBLE else View.INVISIBLE
        root.gesturesEnabled = value == 0f
    }

    private fun animateTo(target: Float, onEnd: (() -> Unit)? = null) {
        animator?.cancel()
        val distance = abs(target - progress)
        if (distance == 0f) {
            onEnd?.invoke()
            return
        }
        animator = ValueAnimator.ofFloat(progress, target).apply {
            duration = (140 + 200 * distance).toLong()
            interpolator = emphasized
            addUpdateListener { setProgress(it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (!cancelled) onEnd?.invoke()
                }
            })
            start()
        }
    }

    private fun openDrawer() {
        // An empty list with no keyboard would be a dead end.
        if (prefs.showKeyboard || prefs.searchOnly) drawer.showKeyboard()
        animateTo(1f)
    }

    private fun closeDrawer(animate: Boolean) {
        drawer.hideKeyboard()
        if (animate) {
            animateTo(0f) { drawer.reset() }
        } else {
            animator?.cancel()
            setProgress(0f)
            drawer.reset()
        }
    }

    /**
     * Back is always handled here, even with nothing to close. Otherwise Android 13+
     * plays its predictive-back animation on the home screen itself, sliding it away
     * as if it were an app that could be left.
     */
    private fun interceptBack() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
            if (pause.isShowing) cancelPause() else if (progress > 0f) closeDrawer(animate = true)
        }
    }

    // Home gestures -----------------------------------------------------------

    override fun onDrawerDrag(progress: Float) {
        animator?.cancel()
        setProgress(progress)
    }

    override fun onDrawerRelease(open: Boolean) {
        if (open) openDrawer() else animateTo(0f)
    }

    override fun onSwipeDown() = SystemActions.expandNotifications(this)

    override fun onPinchIn() = enterStandby()

    /** The app chosen for that side in settings, through the pause like any other launch. */
    override fun onSwipeSideways(toLeft: Boolean) {
        val app = prefs.swipeApp(toLeft)?.let(repo::find) ?: return
        launch(app, root)
    }

    override fun onDoubleTapEmpty() {
        if (!prefs.doubleTapLock) return
        if (!LockService.lock()) {
            Toast.makeText(this, getString(R.string.lock_needs_service), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onLongPressEmpty() {
        startActivity(
            Intent(this, SettingsActivity::class.java),
            ActivityOptions.makeCustomAnimation(this, R.anim.rise_in, R.anim.hold).toBundle(),
        )
    }

    // Drawer callbacks ----------------------------------------------------------

    override fun onLaunch(app: App, source: View) = launch(app, source)

    override fun onAppLongPress(app: App) = appMenu.show(app)

    override fun onCloseDrag(progress: Float) = onDrawerDrag(progress)

    override fun onCloseRelease(close: Boolean) {
        if (close) closeDrawer(animate = true) else animateTo(1f)
    }

    // Apps ----------------------------------------------------------------------

    /** Opens an app, through the breathing pause when it is on its list. */
    private fun launch(app: App, source: View) = throughPause(app) { launchNow(app, source) }

    /** One of an app's shortcuts: a way into the app like any other, so paused apps pause. */
    private fun openShortcut(app: App, shortcut: ShortcutInfo) =
        throughPause(app) { Shortcuts.start(this, shortcut, null) }

    /** Runs [open] now, or after the breathing pause for the apps on its list. */
    private fun throughPause(app: App, open: () -> Unit) {
        if (app.key in prefs.pauseApps) showPause(app, open) else open()
    }

    /**
     * The same for a way into an app known only by its package (a search in YouTube, a WhatsApp
     * chat, the browser): paused if any of that package's entries is.
     */
    private fun throughPauseFor(packageName: String?, open: () -> Unit) {
        val paused = packageName?.let { pkg ->
            repo.apps.firstOrNull { it.component.packageName == pkg && it.key in prefs.pauseApps }
        }
        if (paused != null) showPause(paused, open) else open()
    }

    private fun launchNow(app: App, source: View) {
        val bounds = Rect().also { source.getGlobalVisibleRect(it) }
        try {
            launcherApps.startMainActivity(app.component, app.user, bounds, null)
        } catch (e: RuntimeException) {
            // ActivityNotFoundException or SecurityException: the app went away under us.
            warn("Could not open ${app.component}", e)
            Toast.makeText(this, getString(R.string.cant_open, app.label), Toast.LENGTH_SHORT).show()
            repo.reload()
        }
    }

    companion object {
        /** Home was on screen, or was when the screen went off. */
        var inFront = false
            private set

        /** Home is in front right now, not just when the screen went off. */
        private var resumed = false

        /**
         * The phone was unlocked. Home counts as in front only if it is what shows: from the
         * lock screen, a notification or the camera may have taken the user straight into an
         * app, and the next standby must then cover that app, not bring home back over it.
         */
        fun onUnlocked() {
            if (!resumed) inFront = false
        }

        private const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
