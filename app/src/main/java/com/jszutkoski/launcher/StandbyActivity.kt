package com.jszutkoski.launcher

import android.app.Activity
import android.app.ActivityOptions
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowInsetsController
import android.view.WindowManager
import android.window.OnBackInvokedDispatcher
import java.text.NumberFormat

/**
 * The bedside clock as its own screen, allowed to show over the lock screen. When the phone
 * locks while it is open, pressing power brings it back instead of the lock screen. It shows
 * only the time, date and battery: tapping asks for the PIN or fingerprint before anything
 * else, and unlocking by any means closes it.
 */
open class StandbyActivity : Activity() {
    private lateinit var view: StandbyView
    private var charging = false
    private var unlockAsked = false

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

        // No brightness override: the screen would first light up at the system level and
        // then visibly ramp down to it. The half-strength text on black keeps it dim enough.
        // Bars stay, with dark icons on black: nearly invisible. Hidden bars flash in for a
        // moment on every wake before Android applies the request again.
        val dark = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        window.insetsController?.setSystemBarsAppearance(dark, dark)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { finish() }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.hold, R.anim.fade_out)
        }

        // Sticky: registering returns the current charge right away.
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let(::updateBattery)
        registerReceiver(unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))
    }

    override fun onResume() {
        super.onResume()
        inFront = true
        view.show()
    }

    override fun onDestroy() {
        inFront = false
        unregisterReceiver(batteryReceiver)
        unregisterReceiver(unlockReceiver)
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
        if (getSystemService(PowerManager::class.java)?.isInteractive == false) return
        inFront = false
        if (this !is StandbyOverAppsActivity) finish()
    }

    @Deprecated("Only reached below Android 13; newer versions use OnBackInvokedCallback.")
    override fun onBackPressed() = finish()

    /** A tap: straight home when unlocked, otherwise through the PIN or fingerprint first. */
    private fun leave() {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard?.isKeyguardLocked == true) {
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
        } else {
            finish()
        }
    }

    private fun updateBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0 || scale <= 0) return
        val fraction = level / scale.toFloat()
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val low = !charging && fraction <= LOW_BATTERY
        view.updateBattery(fraction, charging, low, NumberFormat.getPercentInstance().format(fraction.toDouble()))
        // Awake all night on the charger; on battery the screen times out as usual.
        if (charging) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    companion object {
        /** Standby screens alive right now, of either kind. */
        /** A standby screen is showing, or was when the screen went off. */
        var inFront = false
            private set

        /**
         * From the screen-off receiver: in a task of its own above whatever app was open, so
         * that app returns after unlocking. Nothing to do if standby is already up.
         */
        fun startOnTop(context: Context) {
            try {
                context.startActivity(
                    Intent(context, StandbyOverAppsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (_: RuntimeException) {
                // No longer allowed to open screens from the background.
            }
        }

        /** From the screen-off receiver when home was showing: into the home task, on top of home. */
        fun startOverHome(context: Context) {
            try {
                context.startActivity(Intent(context, StandbyActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: RuntimeException) {
                // No longer allowed to open screens from the background.
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
