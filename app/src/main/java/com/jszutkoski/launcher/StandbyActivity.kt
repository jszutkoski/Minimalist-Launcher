package com.jszutkoski.launcher

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityOptions
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.telecom.TelecomManager
import android.view.WindowInsetsController
import android.window.OnBackInvokedDispatcher
import java.util.Calendar

/**
 * The bedside clock as its own screen, allowed to show over the lock screen. When the phone
 * locks while it is open, pressing power brings it back instead of the lock screen. It shows
 * the time, the weather and which messages are waiting, nothing that can be opened:
 * tapping asks for the PIN or fingerprint first, and unlocking by any means closes it. On the
 * charger, once the phone locks, [ChargerActivity] takes over for the night.
 */
open class StandbyActivity : Activity() {
    private val prefs by lazy { Prefs(this) }
    private val keyguard by lazy { getSystemService(KeyguardManager::class.java) }
    private val power by lazy { getSystemService(PowerManager::class.java) }
    private val screenOn get() = power?.isInteractive == true
    private val locked get() = keyguard?.isKeyguardLocked == true
    private val secure get() = keyguard?.isDeviceSecure == true

    private lateinit var view: StandbyView
    private var unlockAsked = false
    /** Our own fingerprint prompt while it is up, to take it down if standby closes first. */
    private var prompt: CancellationSignal? = null
    /** The fingerprint or PIN was given here, so leaving needs no lock behind it. */
    private var confirmed = false
    /** Home or recents came while our own prompt was up: handled once the prompt is gone. */
    private var leftWhileAsking = false
    private var stopped = false
    /** When the screen last came on with standby up, or standby opened on a lit screen. */
    private var awakeSince = SystemClock.elapsedRealtime()
    private val id = System.identityHashCode(this)

    private val screenOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            awakeSince = SystemClock.elapsedRealtime()
            view.ignoreTaps(WAKE_TAP_MS)
            view.refreshClock()
            showWaiting()
        }
    }

    /** Standby locked the phone and comes back: the next return to the front turns the screen on. */
    private var relighting = false
    private val notificationsChanged: () -> Unit = { showWaiting() }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = updateBattery(intent)
    }

    /** Each minute: the time caught up if its own timer runs late, and brightness that follows the time of day. */
    private val minuteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            view.refreshClock()
            applyBrightness()
        }
    }

    /** Unlocked with the fingerprint or face while showing: the phone is wanted, so step aside. */
    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        edgeToEdge()
        view = StandbyView(this, ::leave)
        setContentView(view)
        if (intent.getBooleanExtra(EXTRA_RELIGHT, false)) relightOnResume()
        Notifications.addListener(notificationsChanged)
        showWaiting()

        // Set before the first frame, so the screen lights up as near the chosen level as Android
        // allows. Left to the system, nothing is overridden. Bars stay, with dark icons on black: nearly invisible. Hidden bars flash in for a
        // moment on every wake before Android applies the request again.
        val dark = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        window.insetsController?.setSystemBarsAppearance(dark, dark)
        applyBrightness()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { leave() }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.hold, R.anim.fade_out)
        }

        // Sticky: registering returns the current charge right away.
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let(::updateBattery)
        registerReceiver(unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))
        registerReceiver(screenOnReceiver, IntentFilter(Intent.ACTION_SCREEN_ON))
        registerReceiver(minuteReceiver, IntentFilter(Intent.ACTION_TIME_TICK))
    }

    override fun onStart() {
        super.onStart()
        stopped = false
    }

    override fun onResume() {
        super.onResume()
        // Back in front: whatever came over it (our own PIN pad) was not someone leaving.
        leftWhileAsking = false
        // Unlocked, only Minimalist's accessibility service can keep the way in guarded (by
        // locking at home or the shade): without it, standby shows over the lock screen alone.
        if (!canShow(this)) {
            finish()
            return
        }
        if (relighting) {
            relighting = false
            setTurnScreenOn(false)
        }
        shown += id
        // Kept behind other apps for days, so the screen chosen meanwhile is applied on each return.
        view.setScreen(prefs.standbyScreen)
        view.refreshClock()
        view.ignoreTaps(WAKE_TAP_MS)
        applyBrightness()
        view.show()
        showWaiting()
        showWeather()
    }

    /** The brightness chosen for standby, for its own window only: the phone's setting stays as it is. */
    private fun applyBrightness() {
        val level = prefs.standbyBrightness.level(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
        if (window.attributes.screenBrightness == level) return
        window.attributes = window.attributes.apply { screenBrightness = level }
    }

    /** The next return to the front turns the screen on, over the lock screen. */
    private fun relightOnResume() {
        relighting = true
        setTurnScreenOn(true)
    }

    override fun onStop() {
        super.onStop()
        // Covered by something else with the screen on (a call, an alarm, an app opened from a
        // notification): no longer in front, so the next screen off must bring it back on top.
        // Stopped by the screen going off, it is still what the wake will show.
        if (screenOn) leaveFront()
        stopped = true
        // Home or recents while our prompt was up, and the prompt already gone without the
        // fingerprint or PIN: left like any other way.
        if (leftWhileAsking && !unlockAsked && screenOn) left()
        // Nobody sees it drift; it starts again on the way back.
        view.hold()
    }

    override fun onDestroy() {
        // Closed under its own prompt (home clears the home task): left like any other way.
        if (leftWhileAsking) left()
        prompt?.cancel()
        Notifications.removeListener(notificationsChanged)
        leaveFront()
        unregisterReceiver(batteryReceiver)
        unregisterReceiver(unlockReceiver)
        unregisterReceiver(screenOnReceiver)
        unregisterReceiver(minuteReceiver)
        super.onDestroy()
    }

    /**
     * Android calls this when the user leaves for home, but also right after standby is put
     * on top at screen off. The copy over other apps must survive that, or it closes before
     * the screen ever wakes; it simply waits behind until the next screen off brings it back.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // With the screen off this is just the launch at screen off, not the user leaving.
        if (!screenOn) return
        // Our own prompt is up, and the PIN pad opening over standby lands here too. Only if
        // the prompt then goes without the fingerprint or PIN, with standby out of sight, was
        // this home or recents.
        if (unlockAsked) {
            leftWhileAsking = true
            return
        }
        left()
    }

    /**
     * Home or recents. On a phone that was never locked (the charger keeps the screen on, so it
     * never locks by itself), past the grace period, the phone locks now, or that swipe would
     * get straight in; and standby comes straight back over the lock screen, so the swipe lands
     * on the clock again rather than on Android's lock screen.
     */
    private fun left() {
        leftWhileAsking = false
        leaveFront()
        if (!locked && needsUnlock()) lockAndReturn(this)
        if (this !is StandbyOverAppsActivity) finish()
    }

    /**
     * Something of the system's came over standby: the notification shade, quick settings or the
     * power menu (a call or an alarm too, but those are left alone). On a phone that is still
     * unlocked, past the grace period, the shade would hand anyone the messages and their reply
     * boxes; so the phone locks there and then, as pressing home does, and standby lights up again
     * over the lock screen. Over the lock screen the
     * shade is Android's own, as it is without standby, and shows what Android's lock-screen
     * settings allow.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Our own fingerprint prompt or PIN pad, standby closing, or the screen going off.
        if (hasFocus || unlockAsked || isFinishing || !screenOn || LockService.inCall(this)) return
        if (!locked && needsUnlock()) lockAndReturn(this)
    }

    private fun leaveFront() {
        shown -= id
    }

    // Android 11 and 12 only: from 13 on, back reaches the OnBackInvokedCallback instead.
    @SuppressLint("GestureBackNavigation")
    @Deprecated("Only reached below Android 13; newer versions use OnBackInvokedCallback.")
    override fun onBackPressed() = leave()

    /** Put on top again at a later screen off: a fresh look, so a fresh grace period. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        awakeSince = SystemClock.elapsedRealtime()
        if (intent.getBooleanExtra(EXTRA_RELIGHT, false)) relightOnResume()
    }

    /**
     * The phone has a PIN, pattern or password, nobody has given it yet, and the grace period is
     * over. For the first seconds after the screen comes on (or standby opens), a phone Android
     * has not locked yet goes straight in, as it would without standby: a screen that timed out
     * a moment ago should not need the fingerprint. Kept awake longer, standby guards the phone
     * itself.
     */
    private fun needsUnlock(): Boolean {
        if (confirmed || !secure) return false
        return SystemClock.elapsedRealtime() - awakeSince > GRACE_MS
    }

    /**
     * A tap or back. Over the lock screen, Android's own unlock. On a phone that is still
     * unlocked: straight in during the grace period, the fingerprint or PIN after it, so a
     * standby kept awake by the charger still guards the phone.
     */
    private fun leave() {
        val keyguard = keyguard
        if (keyguard == null || !locked) {
            if (needsUnlock()) confirmIdentity() else finish()
            return
        }
        if (unlockAsked) return
        unlockAsked = true
        keyguard.requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = finish()

                // Back out of the PIN pad: standby stays, and the next tap asks again.
                override fun onDismissCancelled() {
                    unlockAsked = false
                }

                override fun onDismissError() {
                    unlockAsked = false
                }
            },
        )
    }

    private fun confirmIdentity() {
        if (unlockAsked) return
        unlockAsked = true
        val signal = CancellationSignal().also { prompt = it }
        BiometricPrompt.Builder(this)
            .setTitle(getString(R.string.unlock))
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
            .build()
            .authenticate(
                signal,
                mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        unlockAsked = false
                        prompt = null
                        confirmed = true
                        finish()
                    }

                    // Cancelled or too many tries: standby stays, and the next tap asks again.
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        unlockAsked = false
                        prompt = null
                        // Taken away by home or recents, with standby out of sight now.
                        if (leftWhileAsking && stopped) left()
                    }
                },
            )
    }

    /**
     * A glyph for each app with notifications waiting, from the notification listener, leaving
     * out the apps muted in settings. Over the lock screen only if allowed, as they tell anyone
     * looking that messages came in.
     */
    private fun showWaiting() {
        val allowed = !(locked && secure) || prefs.lockScreenGlyphs
        if (!allowed) {
            view.showWaiting(emptyList()) { "" }
            return
        }
        val dialer = getSystemService(TelecomManager::class.java)?.defaultDialerPackage
        val apps = waitingApps(this, prefs)
        view.showWaiting(AppGlyphs.waiting(apps, AppGlyphs.lookup(this))) { icon ->
            icon.packages.joinToString(", ") { label(if (it in Packages.CALLS) dialer ?: it else it) }
        }
    }

    private fun label(packageName: String): String = try {
        packageManager.getApplicationInfo(packageName, 0).loadLabel(packageManager).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        packageName
    }

    /** The last reading under the clock, fetched again when it is over half an hour old. */
    private fun showWeather() {
        if (!prefs.weather(ClockScreen.STANDBY) || !Weather.hasAccess(this)) {
            view.showWeather(null)
            return
        }
        view.showWeather(Weather.cached(this))
        if (Weather.isFresh(this)) return
        Weather.refresh(this) { now ->
            if (now != null) runOnUiThread { if (!isDestroyed) view.showWeather(now) }
        }
    }

    private fun updateBattery(intent: Intent) {
        val battery = BatteryState.from(intent) ?: return
        view.updateBattery(battery)
    }

    companion object {
        /** How long after the screen comes on a still-unlocked phone opens without the fingerprint. */
        private const val GRACE_MS = 10_000L

        /** After the screen comes on, how long a touch is taken for the end of the wake gesture. */
        private const val WAKE_TAP_MS = 700L

        /** After locking, the moment for the lock screen to settle before lighting up again. */
        const val RELIGHT_DELAY_MS = 600L

        /**
         * The apps with notifications waiting that may show on the bedside clock, from the
         * notification listener, leaving out the apps muted in settings.
         */
        fun waitingApps(context: Context, prefs: Prefs): List<String> {
            val muted = prefs.standbyMuted
            val dialer = context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
            return Notifications.waitingApps().filter { app ->
                // Missed calls come from Android's call service: they belong to the phone app.
                val call = app in Packages.CALLS
                val owner = if (call) dialer ?: app else app
                // Only apps with a home screen entry: not the system's own passing notes.
                owner !in muted && (call || context.packageManager.getLaunchIntentForPackage(app) != null)
            }
        }

        /** Brought back to the front with the screen lit, after standby locked the phone. */
        private const val EXTRA_RELIGHT = "relight"

        /** The standby screens in front (or in front when the screen went off), by instance. */
        private val shown = mutableSetOf<Int>()

        /** A standby screen is showing, or was when the screen went off. */
        val inFront get() = shown.isNotEmpty()

        /** Standby locked the phone and is on its way back: the screen off is its own. */
        var returning = false
            private set

        /**
         * Someone left an unlocked standby without the fingerprint or PIN (home, recents, the
         * shade): the phone locks, and the same standby comes straight back, lit, over the lock
         * screen. Started from here rather than from the screen itself, which home may have
         * closed by then.
         */
        private fun lockAndReturn(standby: StandbyActivity) {
            if (returning || !LockService.lock()) return
            returning = true
            val context = standby.applicationContext
            val screen = standby.javaClass
            Handler(Looper.getMainLooper()).postDelayed({
                returning = false
                if (LockService.inCall(context)) return@postDelayed
                // On the charger the night's clock is what comes over the lock screen.
                if (ChargerActivity.wanted(context)) {
                    ChargerActivity.startWhenLocked(context)
                    return@postDelayed
                }
                try {
                    // Into its own task again: the home task for the one over home, where the
                    // screen still showing is brought forward rather than opened twice.
                    context.startActivity(
                        Intent(context, screen)
                            .putExtra(EXTRA_RELIGHT, true)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    )
                } catch (e: RuntimeException) {
                    warn("Standby could not come back over the lock screen", e)
                }
            }, RELIGHT_DELAY_MS)
        }

        /**
         * Standby may show: over the lock screen, on a phone with no screen lock, or with
         * Minimalist's accessibility service on, which is what locks the phone when someone
         * leaves standby on an unlocked phone without the fingerprint or PIN.
         */
        fun canShow(context: Context): Boolean {
            val keyguard = context.getSystemService(KeyguardManager::class.java) ?: return true
            return keyguard.isKeyguardLocked || !keyguard.isDeviceSecure || LockService.isEnabled(context)
        }

        /**
         * From the screen-off receiver: in a task of its own above whatever app was open, so
         * that app returns after unlocking. Nothing to do if standby is already up.
         */
        fun startOnTop(context: Context) {
            try {
                context.startActivity(
                    Intent(context, StandbyOverAppsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (e: RuntimeException) {
                // A launch Android refuses from the background is dropped without a word; this
                // is only for the screen itself failing to start.
                warn("Standby could not open over the app", e)
            }
        }

        /** From the screen-off receiver when home was showing: into the home task, on top of home. */
        fun startOverHome(context: Context) {
            try {
                context.startActivity(
                    Intent(context, StandbyActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (e: RuntimeException) {
                // As above: only a screen that fails to start lands here.
                warn("Standby could not open over home", e)
            }
        }

        /**
         * From the home screen: in the home task, so it fades in. A separate task would open
         * with the system's sideways app animation instead.
         */
        fun start(activity: Activity) {
            activity.startActivity(
                Intent(activity, StandbyActivity::class.java),
                ActivityOptions.makeCustomAnimation(activity, R.anim.fade_in, R.anim.hold).toBundle(),
            )
        }
    }
}

/** The same bedside clock in its own task, for when it covers another app at screen off. */
class StandbyOverAppsActivity : StandbyActivity()
