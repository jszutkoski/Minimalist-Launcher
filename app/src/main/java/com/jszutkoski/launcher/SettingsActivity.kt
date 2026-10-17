package com.jszutkoski.launcher

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.ScrollView
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher

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
            title = getString(R.string.screen_time),
            description = getString(R.string.screen_time_desc),
            enabled = prefs.screenTime,
            granted = ScreenTime.hasAccess(this),
            missing = getString(R.string.grant_usage),
            setEnabled = { prefs.screenTime = it },
            grant = { SystemActions.openUsageAccess(this) },
        )
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
        row(
            getString(R.string.standby_charging),
            onOff(prefs.standbyWhenCharging),
            getString(R.string.standby_charging_desc),
        ) { prefs.standbyWhenCharging = !prefs.standbyWhenCharging }
        permissionRow(
            title = getString(R.string.standby_wake),
            description = getString(R.string.standby_wake_desc),
            enabled = prefs.standbyOnWake,
            granted = LockService.isEnabled(this),
            missing = getString(R.string.grant_accessibility),
            setEnabled = { prefs.standbyOnWake = it },
            grant = { SystemActions.openAccessibility(this) },
        )
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
        permissionRow(
            title = getString(R.string.weather),
            description = getString(R.string.weather_desc),
            enabled = prefs.weather,
            granted = Weather.hasAccess(this),
            missing = getString(R.string.grant_location),
            setEnabled = { prefs.weather = it },
            grant = { askPermission(Manifest.permission.ACCESS_COARSE_LOCATION) },
        )
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
        permissionRow(
            title = getString(R.string.double_tap),
            description = getString(R.string.double_tap_desc),
            enabled = prefs.doubleTapLock,
            granted = LockService.isEnabled(this),
            missing = getString(R.string.grant_accessibility),
            setEnabled = { prefs.doubleTapLock = it },
            grant = { SystemActions.openAccessibility(this) },
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

    private fun row(title: String, value: String, description: String? = null, onClick: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(14), 0, dp(14))
            isClickable = true
            setOnClickListener {
                onClick()
                render()
            }
        }
        row.addView(text(title, 20f).apply { isDuplicateParentStateEnabled = true })
        row.addView(
            text(value, 15f, Palette.SECONDARY, 400).apply {
                isDuplicateParentStateEnabled = true
                isSingleLine = false
                setPadding(0, dp(4), 0, 0)
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

    private fun showHidden() {
        val sheet = Sheet(this, getString(R.string.hidden_sheet_title))
        val hidden = prefs.hidden
        if (hidden.isEmpty()) {
            sheet.column.addView(text(getString(R.string.hidden_empty), 19f, Palette.TERTIARY).apply { setPadding(0, dp(13), 0, dp(13)) })
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

    private fun onOff(value: Boolean) = getString(if (value) R.string.on else R.string.off)

    private companion object {
        const val KEY_PAGE = "page"
    }
}
