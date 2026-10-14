package com.jszutkoski.launcher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.graphics.Insets
import android.graphics.Rect
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
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
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import java.text.NumberFormat
import java.util.Locale
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
    private lateinit var favorites: LinearLayout
    private lateinit var setDefault: TextView
    private lateinit var drawer: AppDrawerView

    private var progress = 0f
    private var animator: ValueAnimator? = null
    private var backCallback: Any? = null
    private val emphasized = PathInterpolator(0.2f, 0f, 0f, 1f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        launcherApps = getSystemService(LauncherApps::class.java)
        edgeToEdge()

        buildViews()
        setContentView(root)

        repo = AppRepository(this, prefs)
        repo.onChanged = ::render
        repo.start()
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = updateBattery(intent)
    }

    override fun onStart() {
        super.onStart()
        // A sticky broadcast: registering returns the current state right away.
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let(::updateBattery)
    }

    override fun onResume() {
        super.onResume()
        // Settings may have changed text size, toggles or hidden apps.
        render()
        applyStatusBar()
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(batteryReceiver)
        // Coming back home should always land on the clock, never a stale search.
        closeDrawer(animate = false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed while already home.
        if (progress > 0f) closeDrawer(animate = true)
    }

    override fun onDestroy() {
        repo.stop()
        super.onDestroy()
    }

    @Deprecated("Only reached below Android 13; newer versions use OnBackInvokedCallback.")
    override fun onBackPressed() {
        // A home screen never finishes itself.
        if (progress > 0f) closeDrawer(animate = true)
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
        favorites = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        setDefault = text("Set as home app", 14f, Palette.SECONDARY, 400).apply {
            setPadding(0, dp(12), 0, dp(12))
            setOnClickListener { SystemActions.requestDefaultHome(this@MainActivity) }
        }

        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        // The clock widget: time, a battery hairline exactly as wide as the time,
        // then the date and the battery percentage on one quiet line.
        val clockBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        clockBlock.addView(clock, LinearLayout.LayoutParams(wrap, wrap))
        clockBlock.addView(
            batteryBar,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, wrap).apply {
                topMargin = dp(10)
                bottomMargin = dp(4)
            },
        )
        val dateRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(date)
            addView(batteryDot)
            addView(batteryText)
        }
        home.addView(clockBlock, LinearLayout.LayoutParams(wrap, wrap))
        home.addView(dateRow, LinearLayout.LayoutParams(wrap, wrap))
        home.addView(Space(this), LinearLayout.LayoutParams(0, 0, 1f))
        home.addView(favorites, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, wrap))
        home.addView(setDefault, LinearLayout.LayoutParams(wrap, wrap).apply { topMargin = dp(28) })
        root.addView(home)

        drawer = AppDrawerView(this, this).apply {
            visibility = View.INVISIBLE
            alpha = 0f
        }
        root.addView(drawer)

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
        val scale = prefs.textSize.scale
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 80f * scale)
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f * scale)
        batteryDot.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f * scale)
        batteryText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f * scale)
        renderFavorites(scale)
        setDefault.visibility = if (SystemActions.isDefaultHome(this)) View.GONE else View.VISIBLE

        drawer.textScale = scale
        drawer.autoLaunch = prefs.autoLaunch
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
        batteryBar.update(fraction, charging, low)
        batteryText.text = NumberFormat.getPercentInstance().format(fraction.toDouble())
        batteryText.setTextColor(pressable(if (low) Palette.LOW else Palette.SECONDARY))
    }

    private fun applyStatusBar() {
        val controller = window.insetsController ?: return
        if (prefs.hideStatusBar) {
            controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsets.Type.statusBars())
        } else {
            controller.show(WindowInsets.Type.statusBars())
        }
    }

    private fun renderFavorites(scale: Float) {
        favorites.removeAllViews()
        val apps = prefs.favorites.mapNotNull(repo::find)
        if (apps.isEmpty()) {
            if (repo.loaded) {
                favorites.addView(
                    text("Swipe up for your apps.\nLong-press one to keep it here.", 17f * scale, Palette.SECONDARY).apply {
                        isSingleLine = false
                        setLineSpacing(dp(6f), 1f)
                    },
                )
            }
            return
        }
        apps.forEach { app ->
            favorites.addView(
                text(app.label, 30f * scale).apply {
                    setPadding(0, dp(9), 0, dp(9))
                    setOnClickListener { launch(app, it) }
                    setOnLongClickListener {
                        showActions(app)
                        true
                    }
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
        }
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
        setBackEnabled(true)
        if (prefs.showKeyboard) drawer.showKeyboard()
        animateTo(1f)
    }

    private fun closeDrawer(animate: Boolean) {
        setBackEnabled(false)
        drawer.hideKeyboard()
        if (animate) {
            animateTo(0f) { drawer.reset() }
        } else {
            animator?.cancel()
            setProgress(0f)
            drawer.reset()
        }
    }

    private fun setBackEnabled(enabled: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val dispatcher = onBackInvokedDispatcher
        if (enabled && backCallback == null) {
            val callback = OnBackInvokedCallback { closeDrawer(animate = true) }
            dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
            backCallback = callback
        } else if (!enabled && backCallback != null) {
            dispatcher.unregisterOnBackInvokedCallback(backCallback as OnBackInvokedCallback)
            backCallback = null
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

    override fun onSwipeDown() {
        root.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
        SystemActions.expandNotifications(this)
    }

    override fun onSwipeLeft() {
        root.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
        SystemActions.openCamera(this)
    }

    override fun onSwipeRight() {
        root.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
        SystemActions.openDialer(this)
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

    private fun launch(app: App, source: View) {
        val bounds = Rect().also { source.getGlobalVisibleRect(it) }
        val options = ActivityOptions.makeScaleUpAnimation(source, 0, 0, source.width, source.height).toBundle()
        try {
            launcherApps.startMainActivity(app.component, app.user, bounds, options)
        } catch (_: RuntimeException) {
            // ActivityNotFoundException or SecurityException: the app went away under us.
            Toast.makeText(this, "Can't open ${app.label}", Toast.LENGTH_SHORT).show()
            repo.reload()
        }
    }

    private fun showActions(app: App) {
        val favoriteKeys = prefs.favorites
        val index = favoriteKeys.indexOf(app.key)
        Sheet(this, app.label).apply {
            if (index >= 0) {
                option("Remove from home") { prefs.favorites = favoriteKeys - app.key; render() }
                if (index > 0) option("Move up") { moveFavorite(app.key, -1) }
                if (index < favoriteKeys.lastIndex) option("Move down") { moveFavorite(app.key, 1) }
            } else {
                option("Add to home") { addFavorite(app) }
            }
            option("Rename") { showRename(app) }
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

    private companion object {
        const val MAX_FAVORITES = 8
        const val LOW_BATTERY = 0.15f
    }
}
