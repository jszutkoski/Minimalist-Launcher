package com.jszutkoski.launcher

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityOptions
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.PowerManager
import android.os.SystemClock
import android.view.WindowInsetsController
import android.view.WindowManager
import android.window.OnBackInvokedDispatcher

/**
 * The bedside clock as its own screen, allowed to show over the lock screen. When the phone
 * locks while it is open, pressing power brings it back instead of the lock screen. It shows
 * the time, battery, date, weather and which messages are waiting, nothing that can be opened:
 * tapping asks for the PIN or fingerprint first, and unlocking by any means closes it.
 */
open class StandbyActivity : Activity() {
    private val prefs by lazy { Prefs(this) }
    private val keyguard by lazy { getSystemService(KeyguardManager::class.java) }
    private val power by lazy { getSystemService(PowerManager::class.java) }
    private val screenOn get() = power?.isInteractive == true
    private val locked get() = keyguard?.isKeyguardLocked == true
    private val secure get() = keyguard?.isDeviceSecure == true

    private lateinit var view: StandbyView
    private var charging = false
    private var unlockAsked = false
    /** The fingerprint or PIN was given here, so leaving needs no lock behind it. */
    private var confirmed = false
    /** When the screen last came on with standby up, or standby opened on a lit screen. */
    private var awakeSince = SystemClock.elapsedRealtime()
    private val id = System.identityHashCode(this)

    private val screenOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            awakeSince = SystemClock.elapsedRealtime()
            // Android may have locked while the screen was off: now standby may stay lit.
            keepScreenOn()
            showWaiting()
        }
    }
    private val notificationsChanged: () -> Unit = { showWaiting() }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = updateBattery(intent)
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
        Notifications.addListener(notificationsChanged)
        showWaiting()

        // No brightness override: the screen would first light up at the system level and
        // then visibly ramp down to it. The half-strength text on black keeps it dim enough.
        // Bars stay, with dark icons on black: nearly invisible. Hidden bars flash in for a
        // moment on every wake before Android applies the request again.
        val dark = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        window.insetsController?.setSystemBarsAppearance(dark, dark)
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
    }

    override fun onResume() {
        super.onResume()
        shown += id
        view.show()
        keepScreenOn()
        showWaiting()
        showWeather()
    }

    override fun onStop() {
        super.onStop()
        // Covered by something else with the screen on (a call, an alarm, an app opened from a
        // notification): no longer in front, so the next screen off must bring it back on top.
        // Stopped by the screen going off, it is still what the wake will show.
        if (screenOn) leaveFront()
    }

    override fun onDestroy() {
        Notifications.removeListener(notificationsChanged)
        leaveFront()
        unregisterReceiver(batteryReceiver)
        unregisterReceiver(unlockReceiver)
        unregisterReceiver(screenOnReceiver)
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
        // The PIN pad of our own prompt opening over standby.
        if (unlockAsked) return
        leaveFront()
        // Home or recents on a phone that was never locked (the charger keeps the screen on,
        // so it never locks by itself): lock it now, or that swipe would get straight in.
        if (!locked && needsUnlock()) LockService.lock()
        if (this !is StandbyOverAppsActivity) finish()
    }

    /**
     * Something of the system's came over standby: the notification shade, quick settings or the
     * power menu (a call or an alarm too, but those are left alone). On a phone that is still
     * unlocked, past the grace period, the shade would hand anyone the messages and their reply
     * boxes; so the phone locks there and then, as pressing home does. Over the lock screen the
     * shade is Android's own, as it is without standby, and shows what Android's lock-screen
     * settings allow.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Our own fingerprint prompt or PIN pad, standby closing, or the screen going off.
        if (hasFocus || unlockAsked || isFinishing || !screenOn || LockService.inCall(this)) return
        if (!locked && needsUnlock()) LockService.lock()
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
    }

    /**
     * The phone has a PIN, pattern or password, nobody has given it yet, and the grace period is
     * over. For the first seconds after the screen comes on (or standby opens), a phone Android
     * has not locked yet goes straight in, as it would without standby: a screen that timed out
     * a moment ago should not need the fingerprint. Kept awake longer, by the charger or by
     * just sitting there, standby guards the phone itself.
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
        BiometricPrompt.Builder(this)
            .setTitle(getString(R.string.unlock))
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
            .build()
            .authenticate(
                CancellationSignal(),
                mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        unlockAsked = false
                        confirmed = true
                        finish()
                    }

                    // Cancelled or too many tries: standby stays, and the next tap asks again.
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        unlockAsked = false
                    }
                },
            )
    }

    /**
     * WhatsApp or email waiting: a glyph each under the date, from the notification listener.
     * Over the lock screen only if allowed, as they tell anyone looking that messages came in.
     */
    private fun showWaiting() {
        val allowed = !(locked && secure) || prefs.lockScreenGlyphs
        view.showWaiting(
            whatsApp = allowed && Packages.WHATSAPP.any(Notifications::hasAny),
            email = allowed && Packages.EMAIL.any(Notifications::hasAny),
        )
    }

    /** The last reading under the date, fetched again when it is over half an hour old. */
    private fun showWeather() {
        if (!prefs.weather || !Weather.hasAccess(this)) {
            view.showWeather(null)
            return
        }
        view.showWeather(Weather.cached(this)?.label(this))
        if (Weather.isFresh(this)) return
        Weather.refresh(this) { now ->
            if (now != null) runOnUiThread { if (!isDestroyed) view.showWeather(now.label(this)) }
        }
    }

    private fun updateBattery(intent: Intent) {
        val battery = BatteryState.from(intent) ?: return
        charging = battery.charging
        view.updateBattery(battery.fraction, battery.charging, battery.low)
        keepScreenOn()
    }

    /**
     * Awake all night on the charger, but only over the lock screen (or on a phone with no
     * screen lock). Kept lit while still unlocked, anyone could pull down the notification
     * shade and read or answer messages; so an unlocked standby times out like any screen,
     * Android locks, and from the next wake on it stays lit over the lock screen.
     */
    private fun keepScreenOn() {
        if (charging && (locked || !secure)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    companion object {
        /** How long after the screen comes on a still-unlocked phone opens without the fingerprint. */
        private const val GRACE_MS = 10_000L

        /** The standby screens in front (or in front when the screen went off), by instance. */
        private val shown = mutableSetOf<Int>()

        /** A standby screen is showing, or was when the screen went off. */
        val inFront get() = shown.isNotEmpty()

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
                // No longer allowed to open screens from the background.
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
                // No longer allowed to open screens from the background.
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
