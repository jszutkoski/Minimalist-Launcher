package com.jszutkoski.launcher

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.ScrollView

class SettingsActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var repo: AppRepository
    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        edgeToEdge()

        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply {
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
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onDestroy() {
        repo.stop()
        super.onDestroy()
    }

    private fun render() {
        content.removeAllViews()
        content.addView(
            text("Settings", 40f, fontWeight = 200).apply { setPadding(0, 0, 0, dp(32)) },
        )

        section("Home screen")
        row("Text size", prefs.textSize.label) {
            prefs.textSize = TextSize.entries[(prefs.textSize.ordinal + 1) % TextSize.entries.size]
        }
        row("Open single match", onOff(prefs.autoLaunch), "Launch an app as soon as the search finds only one") {
            prefs.autoLaunch = !prefs.autoLaunch
        }
        row("Keyboard with app list", onOff(prefs.showKeyboard), "Start typing as soon as the list opens") {
            prefs.showKeyboard = !prefs.showKeyboard
        }
        row("Layout", if (prefs.centerFavorites) "Centred" else "Left aligned", "Clock and pinned apps") {
            prefs.centerFavorites = !prefs.centerFavorites
        }
        row(
            "Status bar",
            prefs.statusBar.label,
            "Dimmed: dark icons on black, almost invisible and never jumps. " +
                "Hidden: gone, but Android slides it in and out with the notifications",
        ) {
            prefs.statusBar = StatusBarMode.entries[(prefs.statusBar.ordinal + 1) % StatusBarMode.entries.size]
        }
        section("Extras")
        row(
            "Standby while charging",
            onOff(prefs.standbyWhenCharging),
            "A dim bedside clock after 20 seconds on the home screen while charging. " +
                "Pinch the home screen with two fingers to open it any time; tap to leave",
        ) { prefs.standbyWhenCharging = !prefs.standbyWhenCharging }
        permissionRow(
            title = "Screen time",
            description = "Today's time in apps, under the date",
            enabled = prefs.screenTime,
            granted = ScreenTime.hasAccess(this),
            missing = "Tap to allow usage access",
            setEnabled = { prefs.screenTime = it },
            grant = { SystemActions.openUsageAccess(this) },
        )
        permissionRow(
            title = "Now playing",
            description = "The playing song and its controls, under the date",
            enabled = prefs.nowPlaying,
            granted = NowPlaying.hasAccess(this),
            missing = "Tap to allow notification access",
            setEnabled = { prefs.nowPlaying = it },
            grant = { SystemActions.openNotificationAccess(this) },
        )
        permissionRow(
            title = "Double-tap to lock",
            description = "Double-tap an empty part of the home screen to turn the screen off",
            enabled = prefs.doubleTapLock,
            granted = LockService.isEnabled(this),
            missing = "Tap to turn on Minimalist screen lock in Accessibility",
            setEnabled = { prefs.doubleTapLock = it },
            grant = { SystemActions.openAccessibility(this) },
        )
        row(
            "Restricted setting?",
            "Open app info",
            "If Android says \"App was denied access\" for usage access, notification access or " +
                "the screen lock: try once, then open app info, tap ⋮ and choose " +
                "\"Allow restricted settings\".",
        ) { SystemActions.openAppInfo(this) }

        section("Apps")
        val hiddenCount = prefs.hidden.size
        row("Hidden apps", if (hiddenCount == 0) "None" else "$hiddenCount hidden") { showHidden() }
        val isDefault = SystemActions.isDefaultHome(this)
        row("Home app", if (isDefault) "Minimalist is your home app" else "Not your home app yet") {
            if (isDefault) SystemActions.openHomeSettings(this) else SystemActions.requestDefaultHome(this)
        }

        content.addView(
            text(
                "Swipe up for apps · down for notifications\n" +
                    "Long-press an icon at the bottom to change its app\n" +
                    "Pinch for the bedside clock · double-tap to lock (if on)\n" +
                    "Long-press empty space for settings",
                14f,
                Palette.TERTIARY,
                400,
            ).apply {
                isSingleLine = false
                setLineSpacing(dp(6f), 1f)
                setPadding(0, dp(40), 0, 0)
            },
        )
    }

    private fun section(title: String) {
        content.addView(
            text(title.uppercase(), 12f, Palette.TERTIARY, 500).apply {
                letterSpacing = 0.12f
                setPadding(0, dp(28), 0, dp(4))
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
            !enabled -> "Off"
            !granted -> missing
            else -> "On"
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
        val sheet = Sheet(this, "Tap an app to show it again")
        val hidden = prefs.hidden
        if (hidden.isEmpty()) {
            sheet.column.addView(text("No hidden apps", 19f, Palette.TERTIARY).apply { setPadding(0, dp(13), 0, dp(13)) })
        }
        hidden
            .map { key -> key to (repo.find(key)?.label ?: "Uninstalled app") }
            .sortedBy { it.second.lowercase() }
            .forEach { (key, label) ->
                sheet.option(label) {
                    prefs.hidden = prefs.hidden - key
                    render()
                }
            }
        sheet.show()
    }

    private fun onOff(value: Boolean) = if (value) "On" else "Off"
}
