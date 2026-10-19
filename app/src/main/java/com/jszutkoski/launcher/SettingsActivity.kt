package com.jszutkoski.launcher

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.roundToInt

class SettingsActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var repo: AppRepository
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        edgeToEdge()

        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll = ScrollView(this).apply {
            setBackgroundColor(Palette.BACKGROUND)
            isVerticalScrollBarEnabled = false
            addView(content)
            setOnApplyWindowInsetsListener { _, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                val side = dp(SIDE_MARGIN_DP)
                content.setPadding(bars.left + side, bars.top + dp(56), bars.right + side, bars.bottom + dp(40))
                WindowInsets.CONSUMED
            }
        }
        setContentView(scroll)

        // Leaves the way it came in: sinking back down over the home screen.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.hold, R.anim.sink_out)
        }

        repo = AppRepository(this, prefs)
        repo.onChanged = ::render
        repo.start()
        // Back on the same page after the screen is rebuilt (dark mode, font size).
        savedInstanceState?.getString(KEY_PAGE)?.let { name -> Page.entries.firstOrNull { it.name == name }?.let(::open) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        page?.let { outState.putString(KEY_PAGE, it.name) }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onDestroy() {
        Sheet.dismissAll(this)
        repo.stop()
        super.onDestroy()
    }

    /** The main list of groups, or one group's settings. */
    private enum class Page(val title: Int, val summary: Int) {
        HOME(R.string.page_home, R.string.page_home_summary),
        DOCK(R.string.page_dock, R.string.page_dock_summary),
        CLOCK(R.string.page_clock, R.string.page_clock_summary),
        SEARCH(R.string.page_search, R.string.page_search_summary),
        STANDBY(R.string.page_standby, R.string.page_standby_summary),
        APPS(R.string.page_apps, R.string.page_apps_summary),
        GESTURES(R.string.page_gestures, R.string.page_gestures_summary),
    }

    private var page: Page? = null
    private var backRegistered = false
    private val backToMain by lazy { OnBackInvokedCallback { open(null) } }

    /** Into a group, or back out to the main list with null. */
    private fun open(target: Page?) {
        page = target
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Back leaves a group first; only from the main list does it close settings.
            if (target != null && !backRegistered) {
                onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, backToMain)
                backRegistered = true
            } else if (target == null && backRegistered) {
                onBackInvokedDispatcher.unregisterOnBackInvokedCallback(backToMain)
                backRegistered = false
            }
        }
        scroll.scrollTo(0, 0)
        render()
        content.alpha = 0f
        content.animate().alpha(1f).setDuration(180).start()
    }

    // Android 11 and 12 only: from 13 on, back reaches the OnBackInvokedCallback instead.
    @SuppressLint("GestureBackNavigation")
    @Deprecated("Only reached below Android 13; newer versions use OnBackInvokedCallback.")
    override fun onBackPressed() {
        @Suppress("DEPRECATION")
        if (page != null) open(null) else super.onBackPressed()
    }

    private fun render() {
        content.removeAllViews()
        val current = page
        content.addView(
            text(getString(current?.title ?: R.string.settings), 40f, fontWeight = 200).apply { setPadding(0, 0, 0, dp(20)) },
        )
        when (current) {
            null -> renderMain()
            Page.HOME -> renderHome()
            Page.DOCK -> renderDock()
            Page.CLOCK -> renderClock()
            Page.SEARCH -> renderSearch()
            Page.STANDBY -> renderStandby()
            Page.APPS -> renderApps()
            Page.GESTURES -> renderGestures()
        }
    }

    private fun renderMain() {
        Page.entries.forEach { group -> row(getString(group.title), getString(group.summary)) { open(group) } }
        row(
            getString(R.string.restricted_title),
            getString(R.string.restricted_value),
            getString(R.string.restricted_desc),
        ) { SystemActions.openAppInfo(this) }
        // Android 13 and later keep a language for each app; before that, only the phone's.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val current = AppLanguage.current(this)
            row(getString(R.string.language), current?.label ?: getString(R.string.language_system)) {
                AppLanguage.set(this, AppLanguage.after(current))
            }
        }
        row(getString(R.string.privacy), getString(R.string.privacy_value)) { SystemActions.openPrivacyPolicy(this) }
    }

    private fun renderHome() {
        row(getString(R.string.text_size), getString(prefs.textSize.label)) {
            prefs.textSize = TextSize.entries[(prefs.textSize.ordinal + 1) % TextSize.entries.size]
        }
        row(
            getString(R.string.layout),
            getString(if (prefs.centerFavorites) R.string.layout_centred else R.string.layout_left),
            getString(R.string.layout_desc),
        ) {
            prefs.centerFavorites = !prefs.centerFavorites
        }
        row(
            getString(R.string.status_bar),
            getString(prefs.statusBar.label),
            getString(R.string.status_bar_desc),
        ) {
            prefs.statusBar = StatusBarMode.entries[(prefs.statusBar.ordinal + 1) % StatusBarMode.entries.size]
        }
        permissionRow(
            title = getString(R.string.dots),
            description = getString(R.string.dots_desc),
            enabled = prefs.notificationDots,
            granted = NowPlaying.hasAccess(this),
            missing = getString(R.string.grant_notifications),
            setEnabled = { prefs.notificationDots = it },
            grant = { SystemActions.openNotificationAccess(this) },
        )
        permissionRow(
            title = getString(R.string.now_playing),
            description = getString(R.string.now_playing_desc),
            enabled = prefs.nowPlaying,
            granted = NowPlaying.hasAccess(this),
            missing = getString(R.string.grant_notifications),
            setEnabled = { prefs.nowPlaying = it },
            grant = { SystemActions.openNotificationAccess(this) },
        )
    }

    /**
     * The bottom icons as they are, then how many there are, each one to change, move or remove,
     * and a new one while there is room.
     */
    private fun renderDock() {
        val dock = DockApps(this, prefs, repo)
        val shown = dock.shown()
        if (shown.isNotEmpty()) {
            val inset = dp(Dock.INSET_DP)
            content.addView(
                dockPreview(dock, shown, inset),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    // Out by the icon padding, as on the home screen, so the glyphs line up with the text.
                    leftMargin = -inset
                    rightMargin = -inset
                    bottomMargin = dp(16)
                },
            )
        }
        row(getString(R.string.dock_count), dockCount(shown.size)) { pickDockCount(dock) }
        shown.forEachIndexed { index, entry ->
            row(getString(R.string.dock_place, index + 1), dock.label(entry)) { dockOptions(dock, index, shown.size) }
        }
        if (!dock.isFull) {
            row(getString(R.string.dock_add), getString(R.string.dock_add_value)) {
                dock.choose(this, getString(R.string.dock_add)) { entry ->
                    dock.add(entry)
                    render()
                }
            }
        }
        content.addView(
            text(getString(R.string.dock_help), 14f, Palette.TERTIARY, 400).apply {
                isSingleLine = false
                setLineSpacing(dp(6f), 1f)
                setPadding(0, dp(28), 0, 0)
            },
        )
    }

    /** The icons spread as on the home screen, only to look at. */
    private fun dockPreview(dock: DockApps, entries: List<String>, inset: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        val glyphs = AppGlyphs.lookup(context)
        entries.forEachIndexed { index, entry ->
            if (index > 0) addView(Space(context), LinearLayout.LayoutParams(0, 0, 1f))
            val icon = ImageView(context).apply {
                val glyph = dock.glyph(entry, glyphs)
                if (glyph != null) setImageResource(glyph) else setImageDrawable(InitialGlyph(context, dock.label(entry)))
                imageTintList = ColorStateList.valueOf(Palette.TEXT)
                setPadding(inset, inset, inset, inset)
                contentDescription = dock.label(entry)
            }
            val size = dp(Dock.ICON_DP) + 2 * inset
            addView(icon, LinearLayout.LayoutParams(size, size))
        }
    }

    private fun dockCount(count: Int) = if (count == 0) getString(R.string.dock_count_none) else count.toString()

    private fun pickDockCount(dock: DockApps) {
        val sheet = Sheet(this, getString(R.string.dock_count))
        for (count in 0..Prefs.MAX_DOCK) sheet.option(dockCount(count)) { dock.setCount(count) }
        sheet.onDismiss = { render() }
        sheet.show()
    }

    private fun dockOptions(dock: DockApps, index: Int, count: Int) {
        val sheet = Sheet(this, dock.label(dock.shown()[index]))
        sheet.option(getString(R.string.dock_change_app)) {
            dock.choose(this, getString(R.string.choose_app)) { entry ->
                dock.replace(index, entry)
                render()
            }
        }
        if (index > 0) sheet.option(getString(R.string.dock_move_left)) { dock.move(index, -1) }
        if (index < count - 1) sheet.option(getString(R.string.dock_move_right)) { dock.move(index, 1) }
        sheet.option(getString(R.string.dock_remove)) { dock.remove(index) }
        sheet.onDismiss = { render() }
        sheet.show()
    }

    /**
     * The home screen's clock: a live preview, then each choice, going round its values on every
     * tap. The preview keeps the height of the tallest clock there is, with the date and both
     * extra lines, so the rows never move.
     */
    private fun renderClock() {
        val screen = ClockScreen.HOME
        val look = prefs.clockLook(screen)
        val layout = prefs.homeLayout

        val tallest = HomeLayout.entries.flatMap { candidateLayout ->
            ClockDesign.entries.flatMap { design ->
                listOf(DatePosition.ABOVE, DatePosition.BELOW).map { date ->
                    candidateLayout to look.copy(design = design, weight = ClockWeight.BOLD, size = SizeStep.entries.last(), date = date)
                }
            }
        }.maxOf { (candidateLayout, candidate) ->
            preview(candidateLayout, candidate, extras = true).run {
                measure(
                    View.MeasureSpec.makeMeasureSpec(previewWidth, View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                )
                measuredHeight
            }
        }
        val box = FrameLayout(this).apply {
            addView(
                preview(layout, look, extras = false),
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER_HORIZONTAL,
                ),
            )
        }
        content.addView(
            box,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, tallest).apply {
                topMargin = dp(20)
                bottomMargin = dp(28)
            },
        )

        fun set(change: ClockLook) = prefs.setClockLook(screen, change)
        row(getString(R.string.home_layout), getString(layout.label), getString(R.string.home_layout_desc)) {
            prefs.homeLayout = next(layout)
        }
        // The panel is always digital, with the date above.
        val panel = layout == HomeLayout.PANEL
        if (!panel) {
            row(getString(R.string.clock_design), getString(look.design.label)) { set(look.copy(design = next(look.design))) }
        }
        // Each weight's name is written in that weight.
        row(
            getString(R.string.clock_weight),
            getString(look.weight.label),
            valueFace = ClockFont.at(this, look.weight.weight),
        ) { set(look.copy(weight = next(look.weight))) }
        row(getString(R.string.clock_size), getString(look.size.label)) { set(look.copy(size = next(look.size))) }
        if (!panel) {
            row(getString(R.string.date_position), getString(look.date.label)) { set(look.copy(date = next(look.date))) }
        }
        weatherRow(screen)
        permissionRow(
            title = getString(R.string.screen_time),
            description = getString(R.string.screen_time_desc),
            enabled = prefs.screenTime(screen),
            granted = ScreenTime.hasAccess(this),
            missing = getString(R.string.grant_usage),
            setEnabled = { prefs.setScreenTime(screen, it) },
            grant = { SystemActions.openUsageAccess(this) },
        )
        content.addView(
            text(getString(R.string.clock_help), 14f, Palette.TERTIARY, 400).apply {
                isSingleLine = false
                setLineSpacing(dp(6f), 1f)
                setPadding(0, dp(28), 0, 0)
            },
        )
    }

    /** The weather under the clock on [screen], once location is allowed. */
    private fun weatherRow(screen: ClockScreen) = permissionRow(
        title = getString(R.string.weather),
        description = getString(R.string.weather_desc),
        enabled = prefs.weather(screen),
        granted = Weather.hasAccess(this),
        missing = getString(R.string.grant_location),
        setEnabled = { on ->
            prefs.setWeather(screen, on)
            // Off on both screens: the last place kept for it goes as well.
            if (!on && ClockScreen.entries.none(prefs::weather)) Weather.forget(this)
        },
        grant = { askPermission(Manifest.permission.ACCESS_COARSE_LOCATION) },
    )

    private val previewWidth get() = resources.displayMetrics.widthPixels - 2 * dp(SIDE_MARGIN_DP)

    /**
     * The date and [look] as the home screen shows them in [layout], at today's charge, with its
     * weather and screen time when they are on, or with both anyway when [extras] (to measure the
     * most room).
     */
    private fun preview(layout: HomeLayout, look: ClockLook, extras: Boolean): LinearLayout {
        val screen = ClockScreen.HOME
        val scale = prefs.textSize.scale
        val panelLayout = layout == HomeLayout.PANEL
        val shown = if (panelLayout) look.copy(design = ClockDesign.DIGITAL, date = DatePosition.ABOVE) else look
        fun line(value: String) = text(value, 17f * scale, Palette.SECONDARY, 400).apply { letterSpacing = 0.02f }
        val date = line(DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM"), Date()).toString())
        date.setPadding(0, dp(10), 0, dp(10))
        val face = ClockFace(this, pressable = false)
        face.apply(
            shown,
            digitsSp = 100f * scale,
            analogDp = 220f * scale,
            labelSp = 13f * scale,
            gravity = Gravity.CENTER_HORIZONTAL,
            maxWidth = previewWidth,
            gap = dp(GAP_DP),
            batteryLine = !panelLayout,
        )
        // The battery as it is now: a sticky broadcast answers at once, with no receiver kept.
        val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let(BatteryState::from)
            ?: BatteryState(1f, charging = false)
        face.showBattery(battery, number = true)
        val wantWeather = extras || prefs.weather(screen)
        val wantScreenTime = extras || prefs.screenTime(screen)
        val cached = Weather.cached(this)
        val lines = mutableListOf<View>()
        val spaced = mutableListOf<Spaced>()
        fun add(view: View, element: Spaced) {
            lines += view
            spaced += element
        }
        if (shown.date == DatePosition.ABOVE) add(date, spacedText(date))
        add(face, face.spaced())
        if (shown.date == DatePosition.BELOW) add(date, spacedText(date))
        if (panelLayout) {
            val row = HomePanel(this, onScreenTime = {}, onBattery = {}).apply {
                setTextScale(scale)
                showBattery(battery)
                if (wantWeather) showWeather(cached ?: Weather.Now(18, 3, System.currentTimeMillis()))
                if (wantScreenTime) showScreenTime(ScreenTime.format(0))
            }
            if (wantScreenTime && !extras && ScreenTime.hasAccess(this)) {
                thread(name = "screen-time") {
                    val label = ScreenTime.format(ScreenTime.today(this))
                    runOnUiThread { if (!isDestroyed) row.showScreenTime(label) }
                }
            }
            add(row, row.spaced())
        } else {
            if (wantWeather) line(cached?.label(this) ?: getString(R.string.weather)).let { add(it, spacedText(it)) }
            if (wantScreenTime) {
                val screenTime = line(getString(R.string.screen_time))
                if (!extras && ScreenTime.hasAccess(this)) {
                    thread(name = "screen-time") {
                        val label = getString(R.string.screen_time_today, ScreenTime.format(ScreenTime.today(this)))
                        runOnUiThread { if (!isDestroyed) screenTime.text = label }
                    }
                }
                add(screenTime, spacedText(screenTime))
            }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
            lines.forEach { addView(it, LinearLayout.LayoutParams(wrap, wrap)) }
            // The same gaps as on the screen itself, glyph to glyph.
            spaceGlyphs(spaced, dp(if (panelLayout) PANEL_GAP_DP else GAP_DP))
        }
    }

    /**
     * The two bedside screens side by side, each a small picture of the real thing with its name
     * under it, the chosen one outlined; a tap on either picks it.
     */
    private fun standbyPicker(): LinearLayout {
        val bounds = windowManager.currentWindowMetrics.bounds
        val gap = dp(16)
        val cardWidth = (previewWidth - gap) / 2
        val scale = cardWidth.toFloat() / bounds.width()
        val cardHeight = (bounds.height() * scale).roundToInt()
        val weather = Weather.cached(this)?.takeIf { prefs.weather(ClockScreen.STANDBY) && Weather.hasAccess(this) }
        val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let(BatteryState::from)
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            StandbyScreen.entries.forEachIndexed { index, screen ->
                val chosen = screen == prefs.standbyScreen
                val standby = StandbyView(context) {}.apply {
                    setScreen(screen)
                    showWeather(weather)
                    updateBattery(battery ?: BatteryState(1f, charging = false))
                    showStill()
                    pivotX = 0f
                    pivotY = 0f
                    scaleX = scale
                    scaleY = scale
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                }
                // The picture is only looked at: touches go to the card.
                val card = object : FrameLayout(context) {
                    override fun onInterceptTouchEvent(event: MotionEvent) = true
                }.apply {
                    addView(standby, FrameLayout.LayoutParams(bounds.width(), bounds.height()))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(14f)
                        setColor(Palette.BACKGROUND)
                        setStroke(dp(1), if (chosen) Palette.SECONDARY else Palette.HAIRLINE)
                    }
                    clipToOutline = true
                    isDuplicateParentStateEnabled = true
                }
                val option = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    isClickable = true
                    isSelected = chosen
                    contentDescription = getString(screen.label)
                    addView(card, LinearLayout.LayoutParams(cardWidth, cardHeight))
                    addView(
                        text(getString(screen.label), 17f, if (chosen) Palette.TEXT else Palette.TERTIARY, if (chosen) 500 else 400).apply {
                            isDuplicateParentStateEnabled = true
                            setPadding(0, dp(12), 0, dp(4))
                        },
                    )
                    setOnClickListener {
                        prefs.standbyScreen = screen
                        render()
                    }
                }
                addView(option, LinearLayout.LayoutParams(cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    if (index > 0) leftMargin = gap
                })
            }
        }
    }

    /** The value after [value], back to the first after the last. */
    private inline fun <reified T : Enum<T>> next(value: T): T = enumValues<T>().let { it[(value.ordinal + 1) % it.size] }

    private fun renderSearch() {
        row(getString(R.string.auto_launch), onOff(prefs.autoLaunch), getString(R.string.auto_launch_desc)) {
            prefs.autoLaunch = !prefs.autoLaunch
        }
        row(getString(R.string.search_only), onOff(prefs.searchOnly), getString(R.string.search_only_desc)) {
            prefs.searchOnly = !prefs.searchOnly
        }
        // Search only always brings up the keyboard.
        if (!prefs.searchOnly) {
            row(getString(R.string.keyboard), onOff(prefs.showKeyboard), getString(R.string.keyboard_desc)) {
                prefs.showKeyboard = !prefs.showKeyboard
            }
        }
        permissionRow(
            title = getString(R.string.contacts),
            description = getString(R.string.contacts_desc),
            enabled = prefs.contactSearch,
            granted = ContactSearch.hasAccess(this),
            missing = getString(R.string.grant_contacts),
            setEnabled = { prefs.contactSearch = it },
            grant = { askPermission(Manifest.permission.READ_CONTACTS) },
        )
    }

    private fun renderStandby() {
        content.addView(
            standbyPicker(),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(12)
                bottomMargin = dp(20)
            },
        )
        weatherRow(ClockScreen.STANDBY)
        // The service is what hears the phone lock on the charger, and lights the clock.
        permissionRow(
            title = getString(R.string.standby_charging),
            description = getString(R.string.standby_charging_desc),
            enabled = prefs.standbyWhenCharging,
            granted = LockService.isEnabled(this),
            missing = getString(R.string.grant_accessibility),
            setEnabled = { prefs.standbyWhenCharging = it },
            grant = { askForAccessibility { prefs.standbyWhenCharging = false } },
        )
        permissionRow(
            title = getString(R.string.standby_wake),
            description = getString(R.string.standby_wake_desc),
            enabled = prefs.standbyOnWake,
            granted = LockService.isEnabled(this),
            missing = getString(R.string.grant_accessibility),
            setEnabled = { prefs.standbyOnWake = it },
            grant = { askForAccessibility { prefs.standbyOnWake = false } },
        )
        row(
            getString(R.string.standby_brightness),
            brightness(prefs.standbyBrightness),
            getString(R.string.standby_brightness_desc),
        ) { prefs.standbyBrightness = next(prefs.standbyBrightness) }
        val muted = prefs.standbyMuted.count { pkg -> repo.apps.any { it.component.packageName == pkg } }
        row(
            getString(R.string.standby_icons),
            when {
                !NowPlaying.hasAccess(this) -> getString(R.string.grant_notifications)
                muted == 0 -> getString(R.string.standby_icons_all)
                else -> getString(R.string.standby_icons_but, muted)
            },
            getString(R.string.standby_icons_desc),
        ) {
            if (NowPlaying.hasAccess(this)) pickStandbyApps() else SystemActions.openNotificationAccess(this)
        }
        row(
            getString(R.string.lock_glyphs),
            onOff(prefs.lockScreenGlyphs),
            getString(R.string.lock_glyphs_desc),
        ) { prefs.lockScreenGlyphs = !prefs.lockScreenGlyphs }
        row(
            getString(R.string.lock_notifications),
            getString(R.string.lock_notifications_value),
            getString(R.string.lock_notifications_desc),
        ) { SystemActions.openLockScreenNotifications(this) }
        row(
            getString(R.string.standby_log),
            getString(R.string.standby_log_value),
            getString(R.string.standby_log_desc),
        ) {
            getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(ClipData.newPlainText(getString(R.string.standby_log), StandbyLog.read(this)))
            // Android 13 and later show their own note when something is copied.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                Toast.makeText(this, R.string.standby_log_copied, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun renderApps() {
        val paused = prefs.pauseApps.mapNotNull { repo.find(it)?.label }.sorted()
        row(
            getString(R.string.pause),
            if (paused.isEmpty()) getString(R.string.off) else paused.joinToString(", "),
            getString(R.string.pause_desc),
        ) { pickPauseApps() }
        // Uninstalled apps stay listed in the sheet, to be cleared, but aren't counted here.
        val hiddenCount = if (repo.loaded) prefs.hidden.count { repo.find(it) != null } else prefs.hidden.size
        row(
            getString(R.string.hidden_apps),
            if (hiddenCount == 0) getString(R.string.hidden_none) else getString(R.string.hidden_count, hiddenCount),
        ) { showHidden() }
        val isDefault = SystemActions.isDefaultHome(this)
        row(getString(R.string.home_app), getString(if (isDefault) R.string.home_app_yes else R.string.home_app_no)) {
            if (isDefault) SystemActions.openHomeSettings(this) else SystemActions.requestDefaultHome(this)
        }
    }

    private fun renderGestures() {
        listOf(true to R.string.swipe_left, false to R.string.swipe_right).forEach { (toLeft, title) ->
            val app = prefs.swipeApp(toLeft)?.let(repo::find)
            row(getString(title), app?.label ?: getString(R.string.off), getString(R.string.swipe_desc)) {
                pickSwipeApp(toLeft, getString(title))
            }
        }
        permissionRow(
            title = getString(R.string.double_tap),
            description = getString(R.string.double_tap_desc),
            enabled = prefs.doubleTapLock,
            granted = LockService.isEnabled(this),
            missing = getString(R.string.grant_accessibility),
            setEnabled = { prefs.doubleTapLock = it },
            grant = { askForAccessibility { prefs.doubleTapLock = false } },
        )
        content.addView(
            text(
                getString(R.string.gestures_help),
                14f,
                Palette.TERTIARY,
                400,
            ).apply {
                isSingleLine = false
                setLineSpacing(dp(6f), 1f)
                setPadding(0, dp(28), 0, 0)
            },
        )
    }

    /**
     * A switch for a feature that also needs a permission from Android. Turning it on
     * sends you to grant the permission when it is missing; while it stays missing the
     * row says so, and tapping goes back there instead of switching off.
     */
    private fun permissionRow(
        title: String,
        description: String,
        enabled: Boolean,
        granted: Boolean,
        missing: String,
        setEnabled: (Boolean) -> Unit,
        grant: () -> Unit,
    ) {
        val value = when {
            !enabled -> getString(R.string.off)
            !granted -> missing
            else -> getString(R.string.on)
        }
        row(title, value, description) {
            when {
                !enabled -> {
                    setEnabled(true)
                    if (!granted) grant()
                }
                !granted -> grant()
                else -> setEnabled(false)
            }
        }
    }

    /**
     * The accessibility service can do far more than this app asks of it, so before Android's
     * page opens, this says what it is used for and what it never does; only "agree" goes on.
     * Anything else turns the feature that asked back off.
     */
    private fun askForAccessibility(declined: () -> Unit) {
        val sheet = Sheet(this, getString(R.string.accessibility_title))
        sheet.column.addView(
            text(getString(R.string.accessibility_disclosure), 16f, Palette.SECONDARY, 400).apply {
                isSingleLine = false
                setLineSpacing(dp(4f), 1f)
                setPadding(0, dp(8), 0, dp(12))
            },
        )
        var agreed = false
        sheet.option(getString(R.string.accessibility_agree)) {
            agreed = true
            SystemActions.openAccessibility(this)
        }
        sheet.option(getString(R.string.accessibility_decline), Palette.SECONDARY) {}
        sheet.onDismiss = {
            if (!agreed) declined()
            render()
        }
        sheet.show()
    }

    /** Android's own prompt; once it has been refused for good, app info is the only way left. */
    private fun askPermission(permission: String) {
        if (prefs.askedFor(permission) && !shouldShowRequestPermissionRationale(permission)) {
            SystemActions.openAppInfo(this)
        } else {
            prefs.setAskedFor(permission)
            requestPermissions(arrayOf(permission), 0)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        render()
    }

    private fun row(
        title: String,
        value: String,
        description: String? = null,
        valueFace: Typeface? = null,
        onClick: () -> Unit,
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(14), 0, dp(14))
            isClickable = true
            // Read out with the row, and again when it changes.
            stateDescription = value
            setOnClickListener {
                onClick()
                render()
                // The page is built anew: TalkBack's focus goes back to this row, which reads
                // its new value, instead of falling to the top of the page.
                refocus(title)
            }
        }
        row.addView(text(title, 20f).apply { isDuplicateParentStateEnabled = true })
        row.addView(
            text(value, 15f, Palette.SECONDARY, 400).apply {
                isDuplicateParentStateEnabled = true
                isSingleLine = false
                setPadding(0, dp(4), 0, 0)
                if (valueFace != null) typeface = valueFace
            },
        )
        if (description != null) {
            row.addView(
                text(description, 13f, Palette.TERTIARY, 400).apply {
                    isDuplicateParentStateEnabled = true
                    isSingleLine = false
                    setPadding(0, dp(2), 0, 0)
                },
            )
        }
        content.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    /** Puts TalkBack's focus on the row titled [title], when TalkBack is on. */
    private fun refocus(title: String) {
        if (getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled != true) return
        val row = (0 until content.childCount).map { content.getChildAt(it) }.firstOrNull { view ->
            (view as? LinearLayout)?.getChildAt(0).let { it is TextView && it.text == title }
        } ?: return
        row.post { row.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null) }
    }

    private fun showHidden() {
        val sheet = Sheet(this, getString(R.string.hidden_sheet_title))
        val hidden = prefs.hidden
        if (hidden.isEmpty()) {
            sheet.column.addView(
                text(getString(R.string.hidden_empty), 19f, Palette.TERTIARY).apply { setPadding(0, dp(13), 0, dp(13)) },
            )
        }
        hidden
            .map { key -> key to (repo.find(key)?.label ?: getString(R.string.uninstalled_app)) }
            .sortedBy { it.second.lowercase() }
            .forEach { (key, label) ->
                sheet.option(label) {
                    prefs.hidden = prefs.hidden - key
                    render()
                }
            }
        sheet.show()
    }

    /** Nothing, or one of the visible apps, for a sideways swipe on the home screen. */
    private fun pickSwipeApp(toLeft: Boolean, title: String) {
        val sheet = Sheet(this, title)
        sheet.option(getString(R.string.off), Palette.SECONDARY) { prefs.setSwipeApp(toLeft, null) }
        sheet.divider()
        sheet.appList(repo.visibleApps()) { app ->
            prefs.setSwipeApp(toLeft, app.key)
            sheet.dialog.dismiss()
        }
        sheet.onDismiss = { render() }
        sheet.show()
    }

    /** Every visible app; tapping one adds or removes it. Chosen apps are bright, the rest dim. */
    private fun pickPauseApps() {
        val chosen = prefs.pauseApps.toMutableSet()
        val sheet = Sheet(this, getString(R.string.menu_pause_before))
        sheet.appList(repo.visibleApps(), chosen = { it.key in chosen }) { app ->
            if (!chosen.remove(app.key)) chosen += app.key
            prefs.pauseApps = chosen.toSet()
        }
        sheet.onDismiss = { render() }
        sheet.show()
    }

    /**
     * Every app, hidden ones too as they still notify; tapping one lets it show its icon on the
     * bedside clock, or not. Allowed apps are bright, the rest dim.
     */
    private fun pickStandbyApps() {
        val muted = prefs.standbyMuted.toMutableSet()
        val sheet = Sheet(this, getString(R.string.standby_icons_title))
        sheet.appList(repo.apps.distinctBy { it.component.packageName }, chosen = { it.component.packageName !in muted }) { app ->
            val pkg = app.component.packageName
            if (!muted.remove(pkg)) muted += pkg
            prefs.standbyMuted = muted.toSet()
        }
        sheet.onDismiss = { render() }
        sheet.show()
    }

    private fun brightness(value: StandbyBrightness) = when (value) {
        StandbyBrightness.SYSTEM -> getString(R.string.brightness_system)
        StandbyBrightness.BY_HOUR -> getString(R.string.brightness_by_hour)
        else -> "${value.percent}%"
    }

    private fun onOff(value: Boolean) = getString(if (value) R.string.on else R.string.off)

    private companion object {
        const val KEY_PAGE = "page"
    }
}
