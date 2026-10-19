package com.jszutkoski.launcher

import android.annotation.SuppressLint
import android.app.Activity
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.WindowInsetsController
import android.view.WindowManager
import android.window.OnBackInvokedDispatcher

/**
 * The clock for the night on the charger: once the phone locks there, the screen comes on with
 * the bedside clock, the weather and, when anything is waiting, three dots that never say from
 * which app. Dim, lit for as long as the phone is plugged in, and somewhere new on the screen
 * every minute. Only over the lock screen (or on a phone with no screen lock): tapping goes to
 * Android's own unlock, unlocking by any means closes it, and so does unplugging.
 */
class ChargerActivity : Activity() {
    private val prefs by lazy { Prefs(this) }
    private val keyguard by lazy { getSystemService(KeyguardManager::class.java) }
    private val power by lazy { getSystemService(PowerManager::class.java) }
    private val screenOn get() = power?.isInteractive == true

    private lateinit var view: StandbyView
    private var unlockAsked = false
    /** Brought back after home swiped Android's unlock away: asks again once it shows. */
    private var askOnResume = false
    /** When Android's unlock last went away without the phone opening. */
    private var answeredAt = 0L
    /**
     * Closed by itself (unlocked, unplugged): not the same as Android closing it because home
     * came, which clears whatever is over the home screen.
     */
    private var closing = false
    private val locked get() = keyguard?.isKeyguardLocked == true

    /** A step for the log, with the phone's state at that moment. */
    private fun note(step: String) =
        StandbyLog.note(this, "ChargerActivity $step screenOn=$screenOn locked=$locked asking=$unlockAsked")
    /** Plugged in at the last reading: unplugging after that closes it. */
    private var plugged = false

    private val notificationsChanged: () -> Unit = { showWaiting() }

    private val screenOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            view.ignoreTaps(WAKE_TAP_MS)
            view.refreshClock()
            showWaiting()
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = updateBattery(intent)
    }

    private val minuteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = view.refreshClock()
    }

    /** Unlocked with the fingerprint or face: the phone is wanted, so step aside. */
    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The screen comes on as it opens: turnScreenOn in the manifest, which Android reads in
        // time, and only for an opening, so pressing power over it later just turns it off.
        setShowWhenLocked(true)
        edgeToEdge()
        view = StandbyView(this, ::leave).apply { wanders = true }
        setContentView(view)
        askOnResume = intent.getBooleanExtra(OverLock.EXTRA_ASK, false)
        Notifications.addListener(notificationsChanged)

        // Bars stay, with dark icons on black: nearly invisible.
        val dark = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        window.insetsController?.setSystemBarsAppearance(dark, dark)
        // Low, whatever the phone's brightness is set to: a glow on the bedside table.
        window.attributes = window.attributes.apply { screenBrightness = BRIGHTNESS }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { leave() }
        }

        // Sticky: registering returns the current charge right away.
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let(::updateBattery)
        registerReceiver(unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))
        registerReceiver(screenOnReceiver, IntentFilter(Intent.ACTION_SCREEN_ON))
        registerReceiver(minuteReceiver, IntentFilter(Intent.ACTION_TIME_TICK))
    }

    override fun onResume() {
        super.onResume()
        note("resumed ask=$askOnResume")
        // Never lit over an unlocked phone, where anyone could pull down the notifications.
        if (!canShow(this)) {
            finish()
            return
        }
        showing = true
        view.setScreen(prefs.standbyScreen)
        view.refreshClock()
        view.ignoreTaps(WAKE_TAP_MS)
        view.show()
        showWaiting()
        showWeather()
        if (askOnResume) {
            askOnResume = false
            // Once it is in front again, so Android's unlock opens over it.
            view.postDelayed({ if (!isFinishing && keyguard?.isKeyguardLocked == true) leave() }, ASK_DELAY_MS)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(OverLock.EXTRA_ASK, false)) askOnResume = true
        note("new intent ask=$askOnResume")
    }

    /** Swiped home over the lock screen, the unlock up or not: the clock comes back asking. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        note("user leaving")
        if (screenOn && (unlockAsked || locked)) OverLock.left(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        note("focus=$hasFocus")
    }

    override fun onPause() {
        super.onPause()
        note("paused")
    }

    override fun onStop() {
        super.onStop()
        note("stopped finishing=$isFinishing closing=$closing")
        // Gone from sight over the lock screen with the unlock up: the home swipe put Android's
        // lock screen in its place, so it comes back, asking again.
        if (screenOn && locked && !closing && !LockService.inCall(this)) OverLock.covered(this, unlockAsked, answeredAt)
        // Covered with the screen on (a call, an alarm): the next screen off brings it back.
        // Stopped by the screen going off, it is still what the wake will show.
        if (screenOn) showing = false
        view.hold()
    }

    override fun finish() {
        closing = true
        super.finish()
    }

    override fun onDestroy() {
        note("destroyed")
        Notifications.removeListener(notificationsChanged)
        showing = false
        unregisterReceiver(batteryReceiver)
        unregisterReceiver(unlockReceiver)
        unregisterReceiver(screenOnReceiver)
        unregisterReceiver(minuteReceiver)
        super.onDestroy()
    }

    // Android 11 and 12 only: from 13 on, back reaches the OnBackInvokedCallback instead.
    @SuppressLint("GestureBackNavigation")
    @Deprecated("Only reached below Android 13; newer versions use OnBackInvokedCallback.")
    override fun onBackPressed() = leave()

    /** A tap or back: Android's own unlock, and the phone once it is given. */
    private fun leave() {
        val keyguard = keyguard
        if (keyguard == null || !keyguard.isKeyguardLocked) {
            finish()
            return
        }
        if (unlockAsked) return
        unlockAsked = true
        note("unlock asked")
        keyguard.requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    note("unlocked")
                    finish()
                }

                // Back out of the PIN pad: the clock stays, and the next tap asks again.
                override fun onDismissCancelled() {
                    unlockAsked = false
                    answeredAt = SystemClock.elapsedRealtime()
                    note("unlock cancelled")
                    OverLock.cancelled(this@ChargerActivity)
                }

                override fun onDismissError() {
                    unlockAsked = false
                    answeredAt = SystemClock.elapsedRealtime()
                    note("unlock error")
                }
            },
        )
    }

    /** Three dots when anything is waiting, whatever it is and from whichever app. */
    private fun showWaiting() {
        val apps = StandbyActivity.waitingApps(this, prefs)
        val dots = if (apps.isEmpty()) emptyList() else listOf(AppGlyphs.Waiting(R.drawable.ic_more, apps))
        view.showWaiting(dots) { getString(R.string.charger_waiting) }
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
        // Charging or holding at a limit: either way, on the charger.
        if (battery.plugged) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            // Taken off the charger: the phone is picked up, so Android's lock screen is next.
            if (plugged) finish()
        }
        plugged = battery.plugged
    }

    companion object {
        /** The window's brightness: near the lowest the screen goes. */
        const val BRIGHTNESS = 0.02f

        /** Back in front after home, the moment before Android's unlock is asked for again. */
        private const val ASK_DELAY_MS = 300L

        /** After the screen comes on, how long a touch is taken for the end of the wake gesture. */
        private const val WAKE_TAP_MS = 700L

        /** In front, or was when the screen went off. */
        @Volatile
        var showing = false
            private set

        /** From the screen going off, or the phone locking, to the clock lighting it again. */
        private const val DELAY_MS = 3_000L

        /** On its way: the screen just went off, and the clock follows once the phone is locked. */
        private var pending = false

        private fun plugged(context: Context): Boolean =
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let(BatteryState::from)?.plugged == true

        /** Over the lock screen, or on a phone with no screen lock: nothing there to guard. */
        fun canShow(context: Context): Boolean {
            val keyguard = context.getSystemService(KeyguardManager::class.java) ?: return true
            return keyguard.isKeyguardLocked || !keyguard.isDeviceSecure
        }

        /** Turned on in settings, and on the charger now. */
        fun wanted(context: Context): Boolean = Prefs(context).standbyWhenCharging && plugged(context)

        /**
         * At screen off on the charger (Minimalist's accessibility service hears it): the phone
         * is locked at once if Android would wait a few seconds first, so the screen that comes
         * back on a few seconds later is over the lock screen, with this clock. Nothing to do if it is
         * already there: pressing power over it just turns the screen off.
         */
        fun startWhenLocked(context: Context) {
            if (showing || pending) return
            val keyguard = context.getSystemService(KeyguardManager::class.java)
            if (keyguard != null && keyguard.isDeviceSecure && !keyguard.isKeyguardLocked && !LockService.lock()) return
            pending = true
            val app = context.applicationContext
            Handler(Looper.getMainLooper()).postDelayed({
                pending = false
                if (showing || LockService.inCall(app) || !wanted(app) || !canShow(app)) return@postDelayed
                try {
                    app.startActivity(
                        Intent(app, ChargerActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    )
                } catch (e: RuntimeException) {
                    warn("The charger's clock could not open", e)
                }
            }, DELAY_MS)
        }

        /** Plugged in while locked: the clock for the night, without waiting for the screen to go off. */
        fun onPlugged(context: Context) {
            val keyguard = context.getSystemService(KeyguardManager::class.java) ?: return
            if (keyguard.isKeyguardLocked && Prefs(context).standbyWhenCharging && !LockService.inCall(context)) {
                startWhenLocked(context)
            }
        }
    }
}
