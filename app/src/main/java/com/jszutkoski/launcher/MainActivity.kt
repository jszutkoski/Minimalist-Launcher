package com.jszutkoski.launcher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.app.ActivityOptions
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.database.ContentObserver
import android.graphics.Insets
import android.graphics.Rect
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.ContactsContract
import android.text.InputType
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs

class MainActivity : Activity(), SwipeLayout.Listener, AppDrawerView.Callbacks {
    private lateinit var prefs: Prefs
    private lateinit var repo: AppRepository
    private lateinit var launcherApps: LauncherApps

    private lateinit var root: SwipeLayout
    private lateinit var home: LinearLayout
    private lateinit var clock: TextClock
    private lateinit var date: TextClock
    private lateinit var batteryBar: BatteryBar
    private lateinit var batteryText: TextView
    private lateinit var batteryDot: TextView
    private lateinit var alarmDot: TextView
    private lateinit var alarmText: TextView
    private lateinit var pause: PauseView
    private var favoriteViews: List<Pair<TextView, App>> = emptyList()
    private val dotsChanged: () -> Unit = { showDots() }
    private lateinit var favorites: LinearLayout
    private lateinit var setDefault: TextView
    private lateinit var clockBlock: LinearLayout
    private lateinit var dateRow: LinearLayout
    private lateinit var topSpace: Space
    private lateinit var bottomSpace: Space
    private lateinit var dock: Dock
    private lateinit var screenTimeText: TextView
    private lateinit var weatherText: TextView
    private lateinit var infoDot: TextView
    private lateinit var infoRow: LinearLayout
    private var weatherLoading = false
    private var contactsLoaded = false
    private val contactsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            contactsLoaded = false
        }
    }
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
        dock = Dock(this, prefs, repo, ::launch)
        addDock()
        repo.start()
        nowPlaying = NowPlaying(this, ::showTrack)
        Notifications.addListener(dotsChanged)
        Torch.start(this)
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = updateBattery(intent)
    }

    private val alarmReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = updateAlarm()
    }

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
        applyStatusBar()
        player.reserveSpace = prefs.nowPlaying
        // Lookups that can wait for the first frame.
        root.post {
            updateScreenTime()
            updateWeather()
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
        nowPlaying.stop()
        pause.hide(animate = false)
        // Coming back home should always land on the clock, never a stale search.
        closeDrawer(animate = false)
        root.removeCallbacks(idleStandby)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed while already home.
        if (pause.isShowing) cancelPause()
        if (progress > 0f) closeDrawer(animate = true)
    }

    override fun onDestroy() {
        repo.stop()
        Notifications.removeListener(dotsChanged)
        Torch.stop(this)
        if (contactsObserverOn) contentResolver.unregisterContentObserver(contactsObserver)
        background.shutdownNow()
        super.onDestroy()
    }

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

        home = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        clock = TextClock(this).apply {
            format12Hour = "h:mm"
            format24Hour = "HH:mm"
            setTextColor(pressable(Palette.TEXT))
            typeface = weight(200)
            letterSpacing = -0.02f
            includeFontPadding = false
            setOnClickListener { SystemActions.openAlarms(this@MainActivity) }
        }
        batteryBar = BatteryBar(this).apply {
            setOnClickListener { SystemActions.openBatteryUsage(this@MainActivity) }
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
        batteryDot = text("·", 15f, Palette.TERTIARY, 400).apply { setPadding(dp(10), dp(10), dp(10), dp(10)) }
        batteryText = text("", 15f, Palette.SECONDARY, 400).apply {
            letterSpacing = 0.02f
            setPadding(0, dp(10), dp(8), dp(10))
            setOnClickListener { SystemActions.openBatteryUsage(this@MainActivity) }
        }
        alarmDot = text("·", 15f, Palette.TERTIARY, 400).apply {
            setPadding(dp(2), dp(10), dp(10), dp(10))
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
        setDefault = text("Set as home app", 14f, Palette.SECONDARY, 400).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(12))
            setOnClickListener { SystemActions.requestDefaultHome(this@MainActivity) }
        }
        screenTimeText = text("", 17f, Palette.SECONDARY, 400).apply {
            letterSpacing = 0.02f
            visibility = View.GONE
            setPadding(0, dp(8), 0, dp(8))
            setOnClickListener { SystemActions.openDigitalWellbeing(this@MainActivity) }
        }
        weatherText = text("", 17f, Palette.SECONDARY, 400).apply {
            letterSpacing = 0.02f
            visibility = View.GONE
            setPadding(0, dp(8), 0, dp(8))
            setOnClickListener { SystemActions.webSearch(this@MainActivity, "weather") }
        }
        infoDot = text("·", 17f, Palette.TERTIARY, 400).apply {
            setPadding(dp(10), dp(8), dp(10), dp(8))
            visibility = View.GONE
        }
        player = PlayerView(this, ::openPlayer)

        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        // The clock widget: time, a battery hairline exactly as wide as the time,
        // then the date and the battery percentage on one quiet line.
        clockBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        clockBlock.addView(clock, LinearLayout.LayoutParams(wrap, wrap))
        clockBlock.addView(
            batteryBar,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, wrap).apply {
                topMargin = dp(16)
                bottomMargin = dp(14)
            },
        )
        dateRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(date)
            addView(batteryDot)
            addView(batteryText)
            addView(alarmDot)
            addView(alarmText)
        }
        home.addView(clockBlock, LinearLayout.LayoutParams(wrap, wrap))
        home.addView(dateRow, LinearLayout.LayoutParams(wrap, wrap))
        // Optional: the weather and today's screen time, a quiet line under the date.
        infoRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(weatherText)
            addView(infoDot)
            addView(screenTimeText)
        }
        home.addView(infoRow, LinearLayout.LayoutParams(wrap, wrap).apply { topMargin = dp(8) })
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
        root.addView(home)

        drawer = AppDrawerView(this, this).apply {
            visibility = View.INVISIBLE
            alpha = 0f
        }
        root.addView(drawer)

        pause = PauseView(this, onOpen = ::openAfterPause, onCancel = ::cancelPause)
        root.addView(pause)

        root.setOnApplyWindowInsetsListener { _, insets ->
            // Ignoring visibility keeps the layout still when the status bar is hidden or peeks in.
            val bars = Insets.max(
                insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()),
                insets.getInsets(WindowInsets.Type.displayCutout()),
            )
            val ime = insets.getInsets(WindowInsets.Type.ime())
            val side = dp(SIDE_MARGIN_DP)
            home.setPadding(bars.left + side, bars.top + dp(56), bars.right + side, bars.bottom + dp(36))
            drawer.applyInsets(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            WindowInsets.CONSUMED
        }
    }

    private fun render() {
        stale = false
        val scale = prefs.textSize.scale
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 92f * scale)
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f * scale)
        batteryDot.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f * scale)
        batteryText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f * scale)
        alarmDot.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f * scale)
        alarmText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f * scale)
        pause.setTextScale(scale)
        renderFavorites(scale)
        dock.render()
        showDots()
        screenTimeText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f * scale)
        weatherText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f * scale)
        infoDot.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f * scale)
        player.setTextScale(scale)
        setDefault.visibility = if (SystemActions.isDefaultHome(this)) View.GONE else View.VISIBLE

        drawer.textScale = scale
        drawer.autoLaunch = prefs.autoLaunch
        drawer.searchOnly = prefs.searchOnly
        drawer.whatsApp = whatsAppPackage() != null
        drawer.setApps(repo.visibleApps())
    }

    private fun updateBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0 || scale <= 0) return
        val fraction = level / scale.toFloat()
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val low = !charging && fraction <= LOW_BATTERY
        val label = NumberFormat.getPercentInstance().format(fraction.toDouble())
        batteryBar.update(fraction, charging, low)
        batteryText.text = label
        batteryText.setTextColor(pressable(if (low) Palette.LOW else Palette.SECONDARY))
        if (charging != this.charging) {
            this.charging = charging
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
            updateInfoDot()
            return
        }
        background.execute {
            val total = ScreenTime.today(this)
            runOnUiThread {
                screenTimeText.text = "${ScreenTime.format(total)} today"
                screenTimeText.visibility = View.VISIBLE
                updateInfoDot()
            }
        }
    }

    // Weather ------------------------------------------------------------------------

    /** The last reading right away, then a new one when it is over half an hour old. */
    private fun updateWeather() {
        if (!prefs.weather || !Weather.hasAccess(this)) {
            weatherText.visibility = View.GONE
            updateInfoDot()
            return
        }
        Weather.cached(this)?.let(::showWeather)
        if (weatherLoading || Weather.isFresh(this)) return
        weatherLoading = true
        Weather.refresh(this) { now ->
            runOnUiThread {
                weatherLoading = false
                if (now != null && prefs.weather) showWeather(now)
            }
        }
    }

    private fun showWeather(now: Weather.Now) {
        weatherText.text = now.label
        weatherText.visibility = View.VISIBLE
        updateInfoDot()
    }

    /** The dot only between two things. */
    private fun updateInfoDot() {
        val both = weatherText.visibility == View.VISIBLE && screenTimeText.visibility == View.VISIBLE
        infoDot.visibility = if (both) View.VISIBLE else View.GONE
    }

    // Smart search -------------------------------------------------------------------

    private var contactsObserverOn = false

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

    override fun onResult(result: SearchResult, source: View) {
        when (result) {
            is SearchResult.AppHit -> launch(result.app, source)
            is SearchResult.Answer -> copy(Calculator.plain(result.value))
            is SearchResult.ContactHit -> showContact(result.contact)
            is SearchResult.SettingHit -> openSetting(result.setting)
            is SearchResult.Web -> SystemActions.webSearch(this, result.query)
        }
    }

    override fun onWhatsApp(contact: Contact) {
        val pkg = whatsAppPackage() ?: return
        val number = contact.whatsApp ?: return
        SystemActions.start(this, Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number")).setPackage(pkg))
    }

    private fun whatsAppPackage(): String? =
        WHATSAPP.firstOrNull { pkg -> repo.apps.any { it.component.packageName == pkg } }

    private fun copy(value: String) {
        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Result", value))
        // Android 13+ confirms a copy on its own.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(this, "Copied $value", Toast.LENGTH_SHORT).show()
    }

    private fun showContact(contact: Contact) {
        Sheet(this, "${contact.name} · ${contact.phone}").apply {
            if (contact.whatsApp != null && whatsAppPackage() != null) option("WhatsApp") { onWhatsApp(contact) }
            option("Call") { SystemActions.start(this@MainActivity, Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", contact.phone, null))) }
            option("Message") { SystemActions.start(this@MainActivity, Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", contact.phone, null))) }
            option("Contact card") {
                SystemActions.start(
                    this@MainActivity,
                    Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.getLookupUri(contact.id, contact.lookupKey)),
                )
            }
            show()
        }
    }

    private fun openSetting(setting: SystemSetting) {
        if (setting.isTorch) {
            if (!Torch.toggle(this)) Toast.makeText(this, "The flashlight is unavailable", Toast.LENGTH_SHORT).show()
            closeDrawer(animate = true)
            return
        }
        setting.intents.any { SystemActions.start(this, it()) }
    }

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
        (infoRow.layoutParams as LinearLayout.LayoutParams).gravity = alignment
        player.setAlignment(alignment)
        // The clock widget follows the same alignment as the pinned apps.
        (clockBlock.layoutParams as LinearLayout.LayoutParams).gravity = alignment
        (dateRow.layoutParams as LinearLayout.LayoutParams).gravity = alignment
        clockBlock.requestLayout()
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
                    text("Swipe up for your apps.\nLong-press one to keep it here.", 17f * scale, Palette.SECONDARY).apply {
                        gravity = alignment
                        isSingleLine = false
                        setLineSpacing(dp(6f), 1f)
                    },
                )
            }
            return
        }
        favoriteViews = apps.map { app ->
            val view = text(app.label, 30f * scale).apply {
                setPadding(0, dp(9), 0, dp(9))
                compoundDrawablePadding = dp(12)
                setOnClickListener { launch(app, it) }
                setOnLongClickListener {
                    showActions(app)
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
            val dot = if (enabled && Notifications.has(app.component.packageName)) Dot(this) else null
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
        val next = getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime
        val visible = if (next != null) View.VISIBLE else View.GONE
        alarmDot.visibility = visible
        alarmText.visibility = visible
        if (next == null) return
        val skeleton = (if (next - System.currentTimeMillis() > DAY_MS) "EEE" else "") +
            (if (DateFormat.is24HourFormat(this)) "Hm" else "hm")
        alarmText.text = DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton), next)
    }

    // Mindful pause ------------------------------------------------------------------

    private fun showPause(app: App) {
        root.gesturesEnabled = false
        pause.show(app, null)
        if (!prefs.screenTime || !ScreenTime.hasAccess(this)) return
        background.execute {
            val spent = ScreenTime.today(this, app.component.packageName)
            runOnUiThread { if (pause.isShowing) pause.setUsage(ScreenTime.format(spent)) }
        }
    }

    private fun openAfterPause(app: App) {
        launchNow(app, pause)
        // Hidden once home stops, so the app opens over the pause rather than over home.
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

    override fun onDoubleTapEmpty() {
        if (!prefs.doubleTapLock) return
        if (!LockService.lock()) {
            Toast.makeText(this, "Turn on the screen lock in Minimalist settings", Toast.LENGTH_SHORT).show()
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

    override fun onAppLongPress(app: App) = showActions(app)

    override fun onCloseDrag(progress: Float) = onDrawerDrag(progress)

    override fun onCloseRelease(close: Boolean) {
        if (close) closeDrawer(animate = true) else animateTo(1f)
    }

    // Apps ----------------------------------------------------------------------

    /** Opens an app, through the breathing pause when it is on its list. */
    private fun launch(app: App, source: View) {
        if (app.key in prefs.pauseApps) showPause(app) else launchNow(app, source)
    }

    private fun launchNow(app: App, source: View) {
        val bounds = Rect().also { source.getGlobalVisibleRect(it) }
        try {
            launcherApps.startMainActivity(app.component, app.user, bounds, null)
        } catch (_: RuntimeException) {
            // ActivityNotFoundException or SecurityException: the app went away under us.
            Toast.makeText(this, "Can't open ${app.label}", Toast.LENGTH_SHORT).show()
            repo.reload()
        }
    }

    private fun showActions(app: App) {
        val favoriteKeys = prefs.favorites
        val index = favoriteKeys.indexOf(app.key)
        val paused = app.key in prefs.pauseApps
        Sheet(this, app.label).apply {
            addShortcuts(this@MainActivity, app)
            if (index >= 0) {
                option("Remove from home") { prefs.favorites = favoriteKeys - app.key; render() }
                if (index > 0) option("Move up") { moveFavorite(app.key, -1) }
                if (index < favoriteKeys.lastIndex) option("Move down") { moveFavorite(app.key, 1) }
            } else {
                option("Add to home") { addFavorite(app) }
            }
            option("Rename") { showRename(app) }
            option(if (paused) "Open without pause" else "Pause before opening") {
                prefs.pauseApps = if (paused) prefs.pauseApps - app.key else prefs.pauseApps + app.key
            }
            if (index < 0) option("Hide") { hide(app) }
            option("App info") { launcherApps.startAppDetailsActivity(app.component, app.user, null, null) }
            if (!app.isSystem) option("Uninstall", Palette.SECONDARY) { uninstall(app) }
            show()
        }
    }

    private fun addFavorite(app: App) {
        val current = prefs.favorites.filter { repo.find(it) != null }
        if (current.size >= MAX_FAVORITES) {
            Toast.makeText(this, "Home holds $MAX_FAVORITES apps. Remove one first.", Toast.LENGTH_SHORT).show()
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
        Toast.makeText(this, "${app.label} hidden. Unhide it in Settings.", Toast.LENGTH_SHORT).show()
    }

    private fun uninstall(app: App) {
        SystemActions.start(this, Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.component.packageName}")))
    }

    private fun showRename(app: App) {
        val sheet = Sheet(this, "Rename ${app.originalLabel}")
        val input = EditText(this).apply {
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

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(8), 0, 0)
        }
        buttons.addView(
            text("Reset", 17f, Palette.SECONDARY, 400).apply {
                setPadding(dp(16), dp(12), dp(16), dp(12))
                setOnClickListener { save(null) }
            },
        )
        buttons.addView(
            text("Save", 17f, Palette.TEXT, 500).apply {
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

    companion object {
        /** Home was on screen, or was when the screen went off. */
        var inFront = false
            private set

        private const val MAX_FAVORITES = 8
        private val WHATSAPP = listOf("com.whatsapp", "com.whatsapp.w4b")
        private const val STANDBY_DELAY_MS = 20_000L
        private const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
