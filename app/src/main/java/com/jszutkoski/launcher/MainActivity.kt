package com.jszutkoski.launcher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityOptions
import android.app.AlarmManager
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
    private lateinit var nowPlaying: NowPlaying
    private val background = Executors.newSingleThreadExecutor()
    private var charging = false
    // Focus is checked when the countdown ends: a menu or the shade may have opened since.
    private val idleStandby = Runnable { if (canAutoStandby() && hasWindowFocus()) enterStandby() }
    private lateinit var drawer: AppDrawerView

    private var progress = 0f
    private var stale = true
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
        appMenu = AppMenu(this, prefs, repo, ::render, ::openShortcut)
        searchActions = SearchActions(this, repo, ::launch) { closeDrawer(animate = true) }
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

    override fun onStart() {
        super.onStart()
        // A sticky broadcast: registering returns the current state right away.
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let(::updateBattery)
        registerReceiver(alarmReceiver, IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED))
        updateAlarm()
    }

    override fun onResume() {
        super.onResume()
        inFront = true
        // Only rebuilt when a setting, name or pinned app changed since the last time, so
        // coming back from an app costs next to nothing before home can draw.
        if (stale) render()
        setDefault.visibility = if (SystemActions.isDefaultHome(this)) View.GONE else View.VISIBLE
        showAccessibleRow()
        applyStatusBar()
        player.reserveSpace = prefs.nowPlaying
        // Lookups that can wait for the first frame.
        root.post {
            updateScreenTime()
            loadContacts()
            if (prefs.nowPlaying) nowPlaying.start() else nowPlaying.stop()
        }
        scheduleStandby()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        scheduleStandby()
    }

    override fun onStop() {
        super.onStop()
        // Stopped because the screen went off: home is still what the user was looking at.
        inFront = getSystemService(PowerManager::class.java)?.isInteractive == false
        unregisterReceiver(batteryReceiver)
        unregisterReceiver(alarmReceiver)
        root.removeCallbacks(alarmRefresh)
        // Pressing home or the screen going off closes any menu left open.
        Sheet.dismissAll(this)
        nowPlaying.stop()
        pause.hide(animate = false)
        // Coming back home should always land on the clock, never a stale search.
        closeDrawer(animate = false)
        root.removeCallbacks(idleStandby)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
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
        screenTimeText = text("", 17f, Palette.SECONDARY, 400).apply {
            letterSpacing = 0.02f
            visibility = View.GONE
            setPadding(0, dp(18), 0, dp(8))
            setOnClickListener { SystemActions.openDigitalWellbeing(this@MainActivity) }
        }
        player = PlayerView(this, ::openPlayer)

        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        dateRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(date)
            addView(alarmDot)
            addView(alarmText)
        }
        home.addView(dateRow, LinearLayout.LayoutParams(wrap, wrap))
        home.addView(face, LinearLayout.LayoutParams(wrap, wrap))
        // Optional: today's screen time, on its own line under the battery. The weather is for standby.
        home.addView(screenTimeText, LinearLayout.LayoutParams(wrap, wrap))
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
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f * scale)
        val look = ClockLook.from(prefs)
        face.apply(
            look,
            digitsSp = 100f * scale,
            analogDp = 220f * scale,
            labelSp = 13f * scale,
            gravity = if (prefs.centerFavorites) Gravity.CENTER_HORIZONTAL else Gravity.START,
            maxWidth = resources.displayMetrics.widthPixels - 2 * dp(SIDE_MARGIN_DP),
            date = date,
            dateGap = dp(DATE_GAP_DP),
            gap = dp(BATTERY_GAP_DP),
        )
        // The date's row above the clock or below it; hidden, only the next alarm stays there.
        val rowParams = dateRow.layoutParams
        home.removeView(dateRow)
        home.addView(dateRow, home.indexOfChild(face) + if (look.date == DatePosition.ABOVE) 0 else 1, rowParams)
        date.visibility = if (look.date == DatePosition.HIDDEN) View.GONE else View.VISIBLE
        updateAlarm()
        alarmDot.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f * scale)
        alarmText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f * scale)
        pause.setTextScale(scale)
        renderFavorites(scale)
        dock.render()
        showDots()
        screenTimeText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f * scale)
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
        if (battery.charging != charging) {
            charging = battery.charging
            scheduleStandby()
        }
    }

    // Standby ------------------------------------------------------------------

    /** Restarts the countdown to standby; called on every touch. */
    private fun scheduleStandby() {
        root.removeCallbacks(idleStandby)
        if (canAutoStandby()) root.postDelayed(idleStandby, STANDBY_DELAY_MS)
    }

    /** Charging, on the bare home screen, with the app list closed. */
    private fun canAutoStandby() =
        prefs.standbyWhenCharging && charging && progress == 0f

    private fun enterStandby() {
        root.removeCallbacks(idleStandby)
        StandbyActivity.start(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // The notification shade only takes focus away; it never pauses this activity, so
        // onResume doesn't run when it closes.
        if (hasFocus) applyStatusBar()
        if (hasFocus) scheduleStandby()
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
        if (!prefs.screenTime || !ScreenTime.hasAccess(this)) {
            screenTimeText.visibility = View.GONE
            return
        }
        background.execute {
            val total = ScreenTime.today(this)
            runOnUiThread {
                screenTimeText.text = getString(R.string.screen_time_today, ScreenTime.format(total))
                screenTimeText.visibility = View.VISIBLE
            }
        }
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
        pause.show(app, null, open)
        if (!prefs.screenTime || !ScreenTime.hasAccess(this)) return
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

        private const val STANDBY_DELAY_MS = 20_000L
        private const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
